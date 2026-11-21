package ProjectOop.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Quản lý các kết nối Server-Sent Events (SSE) cho người chơi và người xem online.
 * <p>
 * Mỗi kết nối chạy trên một Virtual Thread (Java 21) và dùng khóa ghi riêng
 * trên từng kênh, tuyệt đối không giữ lock của phòng chơi khi ghi I/O mạng.
 * </p>
 */
public class SseHub {

    public static final class ClientChannel {
        public final String token;
        public final String role;
        public final String playerId;
        private final HttpExchange exchange;
        private final OutputStream out;
        private final Thread ownerThread;
        private final Object writeLock = new Object();
        private volatile boolean open = true;

        ClientChannel(String token, String role, String playerId,
                      HttpExchange exchange, OutputStream out, Thread ownerThread) {
            this.token = token;
            this.role = role;
            this.playerId = playerId;
            this.exchange = exchange;
            this.out = out;
            this.ownerThread = ownerThread;
        }

        public boolean isOpen() {
            return open;
        }

        public boolean sendEvent(String eventName, String data) {
            if (!open) return false;
            StringBuilder frame = new StringBuilder(data.length() + 32);
            if (eventName != null && !eventName.isEmpty()) {
                frame.append("event: ").append(eventName).append('\n');
            }
            String[] lines = data.split("\n", -1);
            for (String line : lines) {
                frame.append("data: ").append(line).append('\n');
            }
            frame.append('\n');
            return writeRaw(frame.toString());
        }

        public boolean sendPing() {
            if (!open) return false;
            return writeRaw(": ping\n\n");
        }

        private boolean writeRaw(String payload) {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            synchronized (writeLock) {
                if (!open) return false;
                try {
                    out.write(bytes);
                    out.flush();
                    return true;
                } catch (IOException e) {
                    close();
                    return false;
                }
            }
        }

        public void close() {
            if (!open) return;
            open = false;
            try {
                out.close();
            } catch (IOException ignored) {}
            try {
                exchange.close();
            } catch (Exception ignored) {}
            ownerThread.interrupt();
        }
    }

    private final List<ClientChannel> channels = new CopyOnWriteArrayList<>();

    public void subscribe(String token,
                          String role,
                          String playerId,
                          HttpExchange exchange,
                          String initialSnapshotJson,
                          Consumer<String> onDisconnect) {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/event-stream; charset=UTF-8");
            headers.set("Cache-Control", "no-cache, no-transform");
            headers.set("Connection", "keep-alive");
            headers.set("X-Accel-Buffering", "no");
            headers.set("Access-Control-Allow-Origin", "*");

            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();

            // Đóng kết nối SSE cũ của cùng token (nếu người chơi F5 nhanh)
            for (ClientChannel existing : channels) {
                if (existing.token.equals(token)) {
                    channels.remove(existing);
                    existing.close();
                }
            }

            ClientChannel channel = new ClientChannel(
                    token, role, playerId, exchange, out, Thread.currentThread());
            channels.add(channel);

            if (!channel.sendEvent("state", initialSnapshotJson)) {
                cleanupChannel(channel, onDisconnect);
                return;
            }

            while (channel.isOpen() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(15_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (!channel.sendPing()) {
                    break;
                }
            }

            cleanupChannel(channel, onDisconnect);
        } catch (IOException e) {
            try {
                exchange.close();
            } catch (Exception ignored) {}
            if (onDisconnect != null && !hasActiveChannel(token)) {
                onDisconnect.accept(token);
            }
        }
    }

    public void broadcast(Function<ClientChannel, String> snapshotSupplier) {
        for (ClientChannel ch : channels) {
            if (!ch.isOpen()) {
                channels.remove(ch);
                continue;
            }
            try {
                String json = snapshotSupplier.apply(ch);
                if (json != null && !ch.sendEvent("state", json)) {
                    channels.remove(ch);
                }
            } catch (Exception e) {
                channels.remove(ch);
                ch.close();
            }
        }
    }

    public boolean hasActiveChannel(String token) {
        if (token == null) return false;
        for (ClientChannel ch : channels) {
            if (ch.token.equals(token) && ch.isOpen()) {
                return true;
            }
        }
        return false;
    }

    public int getActiveCount() {
        int count = 0;
        for (ClientChannel ch : channels) {
            if (ch.isOpen()) count++;
        }
        return count;
    }

    public void closeAll() {
        for (ClientChannel ch : channels) {
            channels.remove(ch);
            ch.close();
        }
    }

    private void cleanupChannel(ClientChannel channel, Consumer<String> onDisconnect) {
        channels.remove(channel);
        channel.close();
        if (onDisconnect != null && !hasActiveChannel(channel.token)) {
            onDisconnect.accept(channel.token);
        }
    }
}
