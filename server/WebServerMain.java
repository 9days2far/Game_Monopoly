package ProjectOop.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * HTTP + SSE Server chạy Cờ Tỷ Phú Việt Nam Online (Java 21 + Virtual Threads).
 */
public class WebServerMain {

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Set<String> ALLOWED_ASSET_PACKAGES =
            Set.of("board", "card", "dice", "player", "square");

    private final Path projectRoot;
    private final Path publicRoot;
    private final RoomManager roomManager;

    public WebServerMain(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.publicRoot = this.projectRoot.resolve("public").toAbsolutePath().normalize();
        this.roomManager = new RoomManager();
    }

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);
        Path root = resolveProjectRoot();
        WebServerMain app = new WebServerMain(root);
        app.start(port);
    }

    private static int resolvePort(String[] args) {
        if (args != null && args.length > 0) {
            try {
                return Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {}
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_PORT;
    }

    private static Path resolveProjectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("public")) && Files.isDirectory(cwd.resolve("square/assets"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("public"))) {
            return parent;
        }
        return cwd;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/api/", this::handleApi);
        server.createContext("/assets/", this::handleAssets);
        server.createContext("/", this::handleStatic);

        server.start();

        System.out.println("============================================================");
        System.out.println("  CỜ TỶ PHÚ VIỆT NAM ONLINE — WEB SERVER ĐÃ KHỞI ĐỘNG!");
        System.out.println("  Thư mục gốc : " + projectRoot);
        System.out.println("  Local URL   : http://localhost:" + port);
        for (String lanIp : getLanIpv4Addresses()) {
            System.out.println("  LAN URL     : http://" + lanIp + ":" + port);
        }
        System.out.println("============================================================");
    }

    // =========================================================================
    // API ROUTER
    // =========================================================================

    private void handleApi(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(method)) {
            addCorsHeaders(exchange.getResponseHeaders());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        try {
            if ("GET".equals(method) && "/api/health".equals(path)) {
                sendJson(exchange, 200, Map.of("ok", true, "status", "UP"));
                return;
            }

            if ("GET".equals(method) && "/api/rooms".equals(path)) {
                sendJson(exchange, 200, Map.of("rooms", roomManager.listPublicRooms()));
                return;
            }

            if ("POST".equals(method) && "/api/rooms/create".equals(path)) {
                Map<String, Object> body = readJsonBody(exchange);
                String roomName = JsonUtil.getString(body, "roomName", "");
                String hostName = JsonUtil.getString(body, "hostName", "Chủ phòng");
                boolean isPublic = JsonUtil.getBoolean(body, "isPublic", true);
                int maxPlayers = JsonUtil.getInt(body, "maxPlayers", 4);
                int startingBalance = JsonUtil.getInt(body, "startingBalance", 1500);

                OnlineGameRoom room = roomManager.createRoom(roomName, isPublic, maxPlayers, startingBalance);
                Map<String, Object> joined = room.join(hostName, "PLAYER", null);
                sendJson(exchange, 200, joined);
                return;
            }

            // Các endpoint dạng /api/rooms/{code}/{action}
            if (path.startsWith("/api/rooms/")) {
                String rest = path.substring("/api/rooms/".length());
                String[] parts = rest.split("/", -1);
                if (parts.length == 2 && !parts[0].isBlank()) {
                    String roomCode = parts[0];
                    String subAction = parts[1];
                    OnlineGameRoom room = roomManager.getRoom(roomCode);
                    if (room == null) {
                        sendJson(exchange, 404, Map.of("error", "Không tìm thấy phòng có mã: " + roomCode));
                        return;
                    }

                    switch (subAction) {
                        case "join" -> {
                            requireMethod(method, "POST");
                            Map<String, Object> body = readJsonBody(exchange);
                            String name = JsonUtil.getString(body, "name", "Người chơi");
                            String role = JsonUtil.getString(body, "role", "PLAYER");
                            String existingToken = JsonUtil.getString(body, "token", null);
                            sendJson(exchange, 200, room.join(name, role, existingToken));
                            return;
                        }
                        case "state" -> {
                            requireMethod(method, "GET");
                            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                            String token = query.getOrDefault("token", "");
                            sendJsonString(exchange, 200, room.buildSnapshotJson(token));
                            return;
                        }
                        case "events" -> {
                            requireMethod(method, "GET");
                            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                            String token = query.getOrDefault("token", "");
                            room.handleSseConnect(token);
                            String initialJson = room.buildSnapshotJson(token);
                            room.getSseHub().subscribe(
                                    token,
                                    "",
                                    null,
                                    exchange,
                                    initialJson,
                                    room::handleSseDisconnect
                            );
                            return;
                        }
                        case "action" -> {
                            requireMethod(method, "POST");
                            Map<String, Object> body = readJsonBody(exchange);
                            String token = JsonUtil.getString(body, "token", "");
                            Map<String, Object> snapshot = room.handleAction(token, body);
                            Map<String, Object> res = new LinkedHashMap<>();
                            res.put("ok", true);
                            res.put("snapshot", snapshot);
                            sendJson(exchange, 200, res);
                            return;
                        }
                        case "chat" -> {
                            requireMethod(method, "POST");
                            Map<String, Object> body = readJsonBody(exchange);
                            String token = JsonUtil.getString(body, "token", "");
                            String msg = JsonUtil.getString(body, "message", "");
                            room.addChat(token, msg);
                            sendJson(exchange, 200, Map.of("ok", true));
                            return;
                        }
                        case "leave" -> {
                            requireMethod(method, "POST");
                            Map<String, Object> body = readJsonBody(exchange);
                            String token = JsonUtil.getString(body, "token", "");
                            room.leave(token);
                            roomManager.removeIfEmptyAndStale(roomCode);
                            sendJson(exchange, 200, Map.of("ok", true));
                            return;
                        }
                        default -> {
                            sendJson(exchange, 404, Map.of("error", "API không tồn tại."));
                            return;
                        }
                    }
                }
            }

            sendJson(exchange, 404, Map.of("error", "API endpoint không hợp lệ."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage() != null ? e.getMessage() : "Yêu cầu không hợp lệ."));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "Lỗi máy chủ nội bộ: " + e.getMessage()));
        }
    }

    // =========================================================================
    // STATIC ASSETS (/assets/{pkg}/{file...}) & PUBLIC WEB FILES
    // =========================================================================

    private void handleAssets(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String rawPath = exchange.getRequestURI().getPath();
        String rest = rawPath.substring("/assets/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash >= rest.length() - 1) {
            sendNotFound(exchange);
            return;
        }

        String pkg = rest.substring(0, slash);
        String relFile = rest.substring(slash + 1);
        if (!ALLOWED_ASSET_PACKAGES.contains(pkg) || relFile.contains("\\")) {
            sendForbidden(exchange);
            return;
        }

        Path assetsRoot = projectRoot.resolve(pkg).resolve("assets").toAbsolutePath().normalize();
        Path target = assetsRoot.resolve(relFile).toAbsolutePath().normalize();
        if (!target.startsWith(assetsRoot)) {
            sendForbidden(exchange);
            return;
        }
        if (!Files.isRegularFile(target)) {
            sendNotFound(exchange);
            return;
        }

        serveFile(exchange, target, "public, max-age=86400");
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String rawPath = exchange.getRequestURI().getPath();
        if (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath)) {
            rawPath = "/index.html";
        }

        String rel = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        if (rel.contains("\\")) {
            sendForbidden(exchange);
            return;
        }

        Path target = publicRoot.resolve(rel).toAbsolutePath().normalize();
        if (!target.startsWith(publicRoot)) {
            sendForbidden(exchange);
            return;
        }
        if (!Files.isRegularFile(target)) {
            // Fallback SPA: nếu truy cập đường dẫn lạ không có phần mở rộng, trả về index.html
            if (!rel.contains(".")) {
                target = publicRoot.resolve("index.html");
            }
            if (!Files.isRegularFile(target)) {
                sendNotFound(exchange);
                return;
            }
        }

        serveFile(exchange, target, "no-cache");
    }

    private void serveFile(HttpExchange exchange, Path file, String cacheControl) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", detectContentType(file.getFileName().toString()));
        headers.set("Cache-Control", cacheControl);
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        byte[] buf;
        try (InputStream is = exchange.getRequestBody()) {
            buf = is.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (buf.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Payload quá lớn.");
        }
        String text = new String(buf, StandardCharsets.UTF_8);
        return JsonUtil.parseObject(text);
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        sendJsonString(exchange, status, JsonUtil.toJson(payload));
    }

    private void sendJsonString(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        addCorsHeaders(headers);
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        byte[] msg = "Not Found".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(404, msg.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg);
        }
    }

    private void sendForbidden(HttpExchange exchange) throws IOException {
        byte[] msg = "Forbidden".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(403, msg.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg);
        }
    }

    private static void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void requireMethod(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IllegalArgumentException("Phương thức HTTP phải là " + expected + ".");
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static String detectContentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static List<String> getLanIpv4Addresses() {
        List<String> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        list.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}
