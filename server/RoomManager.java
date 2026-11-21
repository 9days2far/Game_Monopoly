package ProjectOop.web;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý danh sách phòng chơi Cờ Tỷ Phú Online đang hoạt động trên Server.
 */
public class RoomManager {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long IDLE_TIMEOUT_MS = 60L * 60L * 1000L; // 60 phút

    private final ConcurrentHashMap<String, OnlineGameRoom> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MonopolyRoomCleaner");
        t.setDaemon(true);
        return t;
    });

    public RoomManager() {
        cleaner.scheduleAtFixedRate(this::cleanupIdleRooms, 5, 5, TimeUnit.MINUTES);
    }

    public OnlineGameRoom createRoom(String roomName, boolean isPublic, int maxPlayers, int startingBalance) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = generateRoomCode(5);
            OnlineGameRoom created = new OnlineGameRoom(code, roomName, isPublic, maxPlayers, startingBalance);
            if (rooms.putIfAbsent(code, created) == null) {
                return created;
            }
        }
        throw new IllegalStateException("Không thể tạo mã phòng mới, vui lòng thử lại.");
    }

    public OnlineGameRoom getRoom(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return rooms.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public List<Map<String, Object>> listPublicRooms() {
        List<OnlineGameRoom> snapshot = new ArrayList<>();
        for (OnlineGameRoom room : rooms.values()) {
            if (room.isPublic) {
                snapshot.add(room);
            }
        }
        snapshot.sort(Comparator.comparingLong((OnlineGameRoom r) -> r.getLastActivityAt()).reversed());

        List<Map<String, Object>> result = new ArrayList<>(snapshot.size());
        for (OnlineGameRoom room : snapshot) {
            result.add(room.toSummaryMap());
        }
        return result;
    }

    public void removeIfEmptyAndStale(String code) {
        OnlineGameRoom room = getRoom(code);
        if (room != null && room.isEmptyAndInactive(5_000L)) {
            if (rooms.remove(room.roomCode, room)) {
                room.getSseHub().closeAll();
            }
        }
    }

    private void cleanupIdleRooms() {
        for (Map.Entry<String, OnlineGameRoom> entry : rooms.entrySet()) {
            OnlineGameRoom room = entry.getValue();
            if (room.isEmptyAndInactive(IDLE_TIMEOUT_MS)) {
                if (rooms.remove(entry.getKey(), room)) {
                    room.getSseHub().closeAll();
                }
            }
        }
    }

    private String generateRoomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
