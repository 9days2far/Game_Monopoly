package ProjectOop.web;

import ProjectOop.board.Board;
import ProjectOop.card.Card;
import ProjectOop.card.CardDeck;
import ProjectOop.card.CardType;
import ProjectOop.dice.Dice;
import ProjectOop.player.Player;
import ProjectOop.square.*;
import ProjectOop.trade.TradeExecutor;
import ProjectOop.trade.TradeOffer;
import ProjectOop.trade.TradeValidationResult;
import ProjectOop.trade.TradeValidator;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Phòng chơi Cờ Tỷ Phú Online (State Machine + đồng bộ Core OOP).
 * <p>
 * Toàn bộ nghiệp vụ bàn cờ, tài sản, thẻ bài, giao dịch và phá sản đều gọi trực tiếp
 * vào các lớp Core OOP trong {@code board}, {@code square}, {@code player}, {@code dice},
 * {@code card} và {@code trade}.
 * </p>
 */
public class OnlineGameRoom {

    public enum RoomPhase {
        LOBBY,
        IN_GAME,
        GAME_OVER
    }

    public enum TurnState {
        WAIT_JAIL_CHOICE,
        WAIT_ROLL,
        WAIT_BUY_DECISION,
        WAIT_END_TURN,
        WAIT_TRADE_RESPONSE
    }

    public static final class PlayerSeat {
        public final String id;
        public final String token;
        public String name;
        public final int avatarIndex;
        public final String colorHex;
        public final boolean isBot;
        public volatile boolean connected;
        public Player domainPlayer;

        PlayerSeat(String id, String token, String name, int avatarIndex, String colorHex, boolean isBot) {
            this.id = id;
            this.token = token;
            this.name = name;
            this.avatarIndex = avatarIndex;
            this.colorHex = colorHex;
            this.isBot = isBot;
            this.connected = true;
        }
    }

    public static final class SpectatorSeat {
        public final String token;
        public String name;
        public final long joinedAt;

        SpectatorSeat(String token, String name) {
            this.token = token;
            this.name = name;
            this.joinedAt = System.currentTimeMillis();
        }
    }

    private static final String[] PLAYER_COLORS = {
            "#38bdf8", // 1. Xanh dương
            "#22c55e", // 2. Xanh lá
            "#ef4444", // 3. Đỏ
            "#eab308", // 4. Vàng
            "#a855f7", // 5. Tím
            "#f97316"  // 6. Cam
    };

    private static final String[] PLAYER_AVATAR_FILES = {
            "assets/player/player_1_blue.png",
            "assets/player/player_2_green.png",
            "assets/player/player_3_red.png",
            "assets/player/player_4_yellow.png",
            "assets/player/player_5_purple.png",
            "assets/player/player_6_orange.png"
    };

    private static final String[] CELL_IMAGE_FILES = {
            "o_00_go.png",
            "o_01_tuyen_quang.png",
            "o_02_khi_van.png",
            "o_03_lang_son.png",
            "o_04_thue_thu_nhap.png",
            "o_05_ga_lao_cai.png",
            "o_06_thai_nguyen.png",
            "o_07_co_hoi.png",
            "o_08_bac_ninh.png",
            "o_09_phu_tho.png",
            "o_10_nha_tu.png",
            "o_11_hai_phong.png",
            "o_12_dien_luc_evn.png",
            "o_13_hung_yen.png",
            "o_14_ninh_binh.png",
            "o_15_ga_nam_dinh.png",
            "o_16_thanh_hoa.png",
            "o_17_khi_van.png",
            "o_18_nghe_an.png",
            "o_19_ha_tinh.png",
            "o_20_bai_do_xe.png",
            "o_21_hue.png",
            "o_22_co_hoi.png",
            "o_23_da_nang.png",
            "o_24_nha_trang.png",
            "o_25_ga_quy_nhon.png",
            "o_26_da_lat.png",
            "o_27_lam_dong.png",
            "o_28_nuoc_sach_sai_gon.png",
            "o_29_gia_lai.png",
            "o_30_vao_tu.png",
            "o_31_binh_duong.png",
            "o_32_can_tho.png",
            "o_33_khi_van.png",
            "o_34_vung_tau.png",
            "o_35_ga_sai_gon.png",
            "o_36_co_hoi.png",
            "o_37_tp_ho_chi_minh.png",
            "o_38_thue_cao_cap.png",
            "o_39_ha_noi.png"
    };

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ScheduledExecutorService BOT_SCHEDULER =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "MonopolyBotRunner");
                t.setDaemon(true);
                return t;
            });

    public final String roomCode;
    public final String roomName;
    public final boolean isPublic;
    public final int maxPlayers;
    public final int startingBalance;
    public final long createdAt;
    private volatile long lastActivityAt;

    private final SseHub sseHub = new SseHub();
    private final List<PlayerSeat> seats = new ArrayList<>();
    private final Map<String, SpectatorSeat> spectators = new LinkedHashMap<>();
    private String hostToken;

    private RoomPhase phase = RoomPhase.LOBBY;
    private TurnState turnState = TurnState.WAIT_ROLL;
    private TurnState returnTurnState = TurnState.WAIT_ROLL;

    private Board board;
    private Dice dice;
    private int currentSeatIndex = 0;
    private int turnNumber = 1;
    private boolean allowExtraTurnOnDouble = true;
    private boolean rolledDoubleThisRoll = false;
    private int pendingBuySquarePos = -1;

    private TradeOffer pendingTradeOffer = null;
    private String pendingTradeSenderId = null;
    private String pendingTradeReceiverId = null;

    private final List<Card> keptJailCards = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private final List<Map<String, Object>> chats = new ArrayList<>();

    private long stateVersion = 1;
    private long rollSeq = 0;
    private long cardSeq = 0;
    private long moveSeq = 0;
    private String lastMovedPlayerId = null;
    private int lastMoveFrom = 0;
    private int lastMoveTo = 0;

    private String lastBanner = "Đang chờ người chơi vào phòng...";
    private Map<String, Object> lastCardPayload = null;
    private String winnerPlayerId = null;
    private int botCounter = 0;
    private int seatCounter = 0;

    public OnlineGameRoom(String roomCode, String roomName, boolean isPublic, int maxPlayers, int startingBalance) {
        this.roomCode = roomCode;
        this.roomName = (roomName == null || roomName.isBlank()) ? ("Bàn cờ #" + roomCode) : roomName.trim();
        this.isPublic = isPublic;
        this.maxPlayers = Math.max(2, Math.min(6, maxPlayers));
        this.startingBalance = Math.max(500, Math.min(10_000, startingBalance));
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = this.createdAt;
    }

    public SseHub getSseHub() {
        return sseHub;
    }

    public long getLastActivityAt() {
        return lastActivityAt;
    }

    public synchronized boolean isEmptyAndInactive(long timeoutMs) {
        boolean anyConnectedHuman = false;
        for (PlayerSeat seat : seats) {
            if (!seat.isBot && seat.connected) {
                anyConnectedHuman = true;
                break;
            }
        }
        return !anyConnectedHuman
                && sseHub.getActiveCount() == 0
                && (System.currentTimeMillis() - lastActivityAt) > timeoutMs;
    }

    public synchronized Map<String, Object> toSummaryMap() {
        int humans = 0;
        int bots = 0;
        for (PlayerSeat s : seats) {
            if (s.isBot) bots++;
            else humans++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomCode", roomCode);
        m.put("roomName", roomName);
        m.put("phase", phase.name());
        m.put("playerCount", seats.size());
        m.put("humanCount", humans);
        m.put("botCount", bots);
        m.put("maxPlayers", maxPlayers);
        m.put("spectatorCount", spectators.size());
        m.put("startingBalance", startingBalance);
        m.put("turnNumber", turnNumber);
        return m;
    }

    // =========================================================================
    // THAM GIA / RỜI PHÒNG / KẾT NỐI LẠI
    // =========================================================================

    public Map<String, Object> join(String rawName, String requestedRole, String existingToken) {
        Map<String, Object> response = new LinkedHashMap<>();
        synchronized (this) {
            touch();
            String cleanName = sanitizeName(rawName);
            String role = "SPECTATOR".equalsIgnoreCase(requestedRole) ? "SPECTATOR" : "PLAYER";

            // 1. Reconnect nếu token cũ hợp lệ trong phòng
            if (existingToken != null && !existingToken.isBlank()) {
                PlayerSeat existingSeat = findSeatByToken(existingToken);
                if (existingSeat != null && "PLAYER".equals(role)) {
                    if (rawName != null && !rawName.isBlank()) {
                        existingSeat.name = cleanName;
                        if (existingSeat.domainPlayer != null) {
                            existingSeat.domainPlayer.setName(cleanName);
                        }
                    }
                    existingSeat.connected = true;
                    response.put("role", "PLAYER");
                    response.put("token", existingSeat.token);
                    response.put("playerId", existingSeat.id);
                    response.put("roomCode", roomCode);
                    addLog("🔌 " + existingSeat.name + " đã kết nối lại phòng.");
                    bumpState();
                } else if (spectators.containsKey(existingToken) && "SPECTATOR".equals(role)) {
                    SpectatorSeat spec = spectators.get(existingToken);
                    if (rawName != null && !rawName.isBlank()) {
                        spec.name = cleanName;
                    }
                    response.put("role", "SPECTATOR");
                    response.put("token", spec.token);
                    response.put("playerId", null);
                    response.put("roomCode", roomCode);
                    bumpState();
                }
            }

            if (response.isEmpty()) {
                if ("PLAYER".equals(role)) {
                    if (phase != RoomPhase.LOBBY) {
                        throw new IllegalStateException("Ván đấu đang diễn ra. Hãy chọn 'Xem trực tiếp' hoặc tham gia bàn khác!");
                    }
                    if (seats.size() >= maxPlayers) {
                        throw new IllegalStateException("Phòng đã đủ " + maxPlayers + " người chơi. Bạn có thể vào xem trực tiếp!");
                    }

                    String uniqueName = ensureUniquePlayerName(cleanName);
                    int avatarIdx = pickNextAvailableAvatarIndex();
                    seatCounter++;
                    String seatId = "p" + seatCounter;
                    String token = newToken();
                    PlayerSeat seat = new PlayerSeat(
                            seatId, token, uniqueName, avatarIdx, PLAYER_COLORS[avatarIdx], false);
                    seats.add(seat);
                    if (hostToken == null) {
                        hostToken = token;
                    }
                    addLog("🙋 " + uniqueName + " đã vào phòng chơi.");
                    lastBanner = uniqueName + " đã gia nhập phòng chờ!";
                    bumpState();

                    response.put("role", "PLAYER");
                    response.put("token", token);
                    response.put("playerId", seatId);
                    response.put("roomCode", roomCode);
                } else {
                    String token = newToken();
                    String specName = cleanName.isBlank() ? ("Khán giả #" + (spectators.size() + 1)) : cleanName;
                    spectators.put(token, new SpectatorSeat(token, specName));
                    addLog("👁️ " + specName + " đang theo dõi trực tiếp.");
                    bumpState();

                    response.put("role", "SPECTATOR");
                    response.put("token", token);
                    response.put("playerId", null);
                    response.put("roomCode", roomCode);
                }
            }

            response.put("snapshot", buildSnapshotMap(String.valueOf(response.get("token"))));
        }

        broadcastState();
        return response;
    }

    public void leave(String token) {
        boolean changed = false;
        synchronized (this) {
            touch();
            if (spectators.remove(token) != null) {
                changed = true;
                bumpState();
            } else {
                PlayerSeat seat = findSeatByToken(token);
                if (seat != null) {
                    if (phase == RoomPhase.LOBBY) {
                        seats.remove(seat);
                        addLog("👋 " + seat.name + " đã rời phòng chờ.");
                        if (token.equals(hostToken)) {
                            promoteNextHost();
                        }
                        if (countHumanSeats() == 0) {
                            seats.clear();
                        }
                    } else {
                        seat.connected = false;
                        addLog("⚠️ " + seat.name + " tạm mất kết nối (có thể vào lại).");
                    }
                    changed = true;
                    bumpState();
                }
            }
        }
        if (changed) {
            broadcastState();
        }
    }

    public void handleSseDisconnect(String token) {
        boolean changed = false;
        synchronized (this) {
            PlayerSeat seat = findSeatByToken(token);
            if (seat != null && seat.connected) {
                seat.connected = false;
                changed = true;
                bumpState();
            }
        }
        if (changed) {
            broadcastState();
        }
    }

    public void handleSseConnect(String token) {
        boolean changed = false;
        synchronized (this) {
            PlayerSeat seat = findSeatByToken(token);
            if (seat != null && !seat.connected) {
                seat.connected = true;
                changed = true;
                bumpState();
            }
        }
        if (changed) {
            broadcastState();
        }
    }

    // =========================================================================
    // CHAT TRỰC TUYẾN (NGƯỜI CHƠI + NGƯỜI XEM)
    // =========================================================================

    public void addChat(String token, String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung chat không được để trống.");
        }
        String text = message.trim();
        if (text.length() > 240) {
            text = text.substring(0, 240);
        }

        synchronized (this) {
            touch();
            String senderName;
            String role;
            String colorHex = "#94a3b8";

            PlayerSeat seat = findSeatByToken(token);
            if (seat != null) {
                senderName = seat.name;
                role = "PLAYER";
                colorHex = seat.colorHex;
            } else if (spectators.containsKey(token)) {
                senderName = spectators.get(token).name;
                role = "SPECTATOR";
            } else {
                throw new IllegalStateException("Bạn chưa tham gia phòng này.");
            }

            Map<String, Object> chatEntry = new LinkedHashMap<>();
            chatEntry.put("sender", senderName);
            chatEntry.put("role", role);
            chatEntry.put("colorHex", colorHex);
            chatEntry.put("message", text);
            chatEntry.put("time", TIME_FMT.format(Instant.now()));
            chats.add(chatEntry);
            if (chats.size() > 80) {
                chats.remove(0);
            }
            bumpState();
        }
        broadcastState();
    }

    // =========================================================================
    // XỬ LÝ HÀNH ĐỘNG GAME (COMMAND DISPATCHER)
    // =========================================================================

    public Map<String, Object> handleAction(String token, Map<String, Object> payload) {
        String type = JsonUtil.getString(payload, "type", "").toUpperCase(Locale.ROOT);
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Thiếu loại hành động (type).");
        }

        Map<String, Object> snapshot;
        boolean scheduleBot = false;

        synchronized (this) {
            touch();
            PlayerSeat actor = findSeatByToken(token);
            if (actor == null) {
                if (spectators.containsKey(token)) {
                    throw new IllegalStateException("Người xem (Spectator) không thể thực hiện thao tác chơi game.");
                }
                throw new IllegalStateException("Phiên người chơi không hợp lệ.");
            }
            actor.connected = true;

            switch (type) {
                case "ADD_BOT" -> handleAddBot(actor);
                case "REMOVE_BOT" -> handleRemoveBot(actor, JsonUtil.getString(payload, "playerId", ""));
                case "START_GAME" -> handleStartGame(actor, JsonUtil.getBoolean(payload, "randomizeOrder", true));
                case "ROLL_DICE" -> handleRollDice(actor);
                case "JAIL_PAY_BAIL" -> handleJailPayBail(actor);
                case "JAIL_USE_CARD" -> handleJailUseCard(actor);
                case "JAIL_ROLL" -> handleJailRoll(actor);
                case "BUY_ASSET" -> handleBuyDecision(actor, true);
                case "SKIP_BUY" -> handleBuyDecision(actor, false);
                case "UPGRADE_PROPERTY" -> handleUpgradeProperty(actor, JsonUtil.getInt(payload, "squarePos", -1));
                case "MORTGAGE_ASSET" -> handleMortgageAsset(actor, JsonUtil.getInt(payload, "squarePos", -1));
                case "UNMORTGAGE_ASSET" -> handleUnmortgageAsset(actor, JsonUtil.getInt(payload, "squarePos", -1));
                case "PROPOSE_TRADE" -> handleProposeTrade(actor, payload);
                case "RESPOND_TRADE" -> handleRespondTrade(actor, JsonUtil.getBoolean(payload, "accept", false));
                case "CANCEL_TRADE" -> handleCancelTrade(actor);
                case "END_TURN" -> handleEndTurn(actor);
                case "SURRENDER" -> handleSurrender(actor);
                case "REMATCH" -> handleRematch(actor);
                default -> throw new IllegalArgumentException("Hành động không được hỗ trợ: " + type);
            }

            bumpState();
            snapshot = buildSnapshotMap(token);
            scheduleBot = shouldScheduleBot();
        }

        broadcastState();
        if (scheduleBot) {
            scheduleBotStep();
        }
        return snapshot;
    }

    // -------------------------------------------------------------------------
    // LOBBY HANDLERS
    // -------------------------------------------------------------------------

    private void handleAddBot(PlayerSeat actor) {
        requireHost(actor);
        requirePhase(RoomPhase.LOBBY);
        if (seats.size() >= maxPlayers) {
            throw new IllegalStateException("Phòng đã đủ " + maxPlayers + " người chơi.");
        }
        botCounter++;
        seatCounter++;
        int avatarIdx = pickNextAvailableAvatarIndex();
        String botName = ensureUniquePlayerName("Bot AI " + botCounter);
        PlayerSeat bot = new PlayerSeat(
                "p" + seatCounter, "bot_" + newToken(), botName, avatarIdx, PLAYER_COLORS[avatarIdx], true);
        seats.add(bot);
        addLog("🤖 Chủ phòng đã thêm " + botName + ".");
        lastBanner = botName + " đã tham gia bàn chơi!";
    }

    private void handleRemoveBot(PlayerSeat actor, String targetId) {
        requireHost(actor);
        requirePhase(RoomPhase.LOBBY);
        PlayerSeat target = findSeatById(targetId);
        if (target == null || !target.isBot) {
            throw new IllegalArgumentException("Không tìm thấy Bot hợp lệ để xóa.");
        }
        seats.remove(target);
        addLog("🗑️ Đã xóa " + target.name + " khỏi phòng chờ.");
    }

    private void handleStartGame(PlayerSeat actor, boolean randomizeOrder) {
        requireHost(actor);
        requirePhase(RoomPhase.LOBBY);
        if (seats.size() < 2) {
            throw new IllegalStateException("Cần ít nhất 2 người chơi (hoặc thêm Bot) để bắt đầu!");
        }
        if (randomizeOrder) {
            Collections.shuffle(seats, RANDOM);
        }
        startNewMatchInternal();
    }

    private void handleRematch(PlayerSeat actor) {
        requireHost(actor);
        if (phase != RoomPhase.GAME_OVER) {
            throw new IllegalStateException("Chỉ có thể bắt đầu ván mới sau khi ván hiện tại kết thúc.");
        }
        startNewMatchInternal();
    }

    private void startNewMatchInternal() {
        this.board = new Board();
        this.dice = new Dice();
        this.keptJailCards.clear();
        this.pendingBuySquarePos = -1;
        this.pendingTradeOffer = null;
        this.pendingTradeSenderId = null;
        this.pendingTradeReceiverId = null;
        this.lastCardPayload = null;
        this.winnerPlayerId = null;
        this.lastMovedPlayerId = null;
        this.turnNumber = 1;
        this.currentSeatIndex = 0;

        for (PlayerSeat seat : seats) {
            seat.domainPlayer = new Player(seat.name, startingBalance);
        }

        this.phase = RoomPhase.IN_GAME;
        this.turnState = TurnState.WAIT_ROLL;
        this.returnTurnState = TurnState.WAIT_ROLL;
        this.allowExtraTurnOnDouble = true;
        this.rolledDoubleThisRoll = false;
        this.dice.resetConsecutiveDoubles();

        PlayerSeat first = currentSeat();
        addLog("🎮 Ván mới bắt đầu (" + seats.size() + " người chơi, vốn $" + startingBalance + ")!");
        addLog("🎯 Lượt #1 thuộc về: " + first.name);
        lastBanner = "Ván mới bắt đầu! Lượt đầu tiên của " + first.name;
    }

    // -------------------------------------------------------------------------
    // LƯỢT CHƠI BÌNH THƯỜNG & TÙ
    // -------------------------------------------------------------------------

    private void handleRollDice(PlayerSeat actor) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        if (turnState != TurnState.WAIT_ROLL) {
            throw new IllegalStateException("Hiện tại chưa thể tung xúc xắc (trạng thái: " + turnState + ").");
        }

        Player p = actor.domainPlayer;
        if (p.isInJail()) {
            turnState = TurnState.WAIT_JAIL_CHOICE;
            throw new IllegalStateException("Bạn đang ở tù — vui lòng chọn cách ra tù.");
        }

        dice.roll();
        rollSeq++;
        rolledDoubleThisRoll = dice.isDouble();
        boolean speeding = p.handleDiceRoll(dice);

        String rollText = "🎲 " + p.getName() + " đổ được "
                + dice.getDie1() + " + " + dice.getDie2() + " = " + dice.getTotal()
                + (dice.isDouble() ? " [ĐÔI!]" : "");
        addLog(rollText);

        if (speeding) {
            lastMovedPlayerId = actor.id;
            lastMoveFrom = p.getPosition();
            lastMoveTo = Player.JAIL_POSITION;
            moveSeq++;
            allowExtraTurnOnDouble = false;
            rolledDoubleThisRoll = false;
            turnState = TurnState.WAIT_END_TURN;
            addLog("🚔 " + p.getName() + " đổ đôi 3 lần liên tiếp (Speeding) → Bị tống vào Nhà Tù!");
            lastBanner = p.getName() + " đổ đôi 3 lần liên tiếp và phải vào Tù!";
            return;
        }

        int posBefore = p.getPosition();
        int balBefore = p.getBalance();
        p.move(dice.getTotal());
        int posAfter = p.getPosition();

        lastMovedPlayerId = actor.id;
        lastMoveFrom = posBefore;
        lastMoveTo = posAfter;
        moveSeq++;

        if (p.getBalance() > balBefore) {
            addLog("⭐ " + p.getName() + " đi qua ô GO và nhận +$" + Player.PASS_GO_REWARD + "!");
        }

        Square landingSq = board.getSquare(posAfter);
        addLog("📍 " + p.getName() + " tiến tới Ô #" + posAfter + ": " + landingSq.getName());
        resolveSquareLanding(actor, landingSq, 0);
    }

    private void handleJailPayBail(PlayerSeat actor) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        if (turnState != TurnState.WAIT_JAIL_CHOICE) {
            throw new IllegalStateException("Bạn không ở trạng thái chọn cách ra tù.");
        }
        Player p = actor.domainPlayer;
        JailSquare jailSq = (JailSquare) board.getSquare(JailSquare.JAIL_POSITION);
        if (!jailSq.payBail(p)) {
            throw new IllegalStateException("Bạn không đủ $" + JailSquare.BAIL_AMOUNT + " để nộp bảo lãnh.");
        }
        allowExtraTurnOnDouble = true;
        turnState = TurnState.WAIT_ROLL;
        addLog("💸 " + p.getName() + " nộp $" + JailSquare.BAIL_AMOUNT + " bảo lãnh ra tù. Hãy tung xúc xắc!");
        lastBanner = p.getName() + " đã nộp $" + JailSquare.BAIL_AMOUNT + " để ra tù!";
    }

    private void handleJailUseCard(PlayerSeat actor) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        if (turnState != TurnState.WAIT_JAIL_CHOICE) {
            throw new IllegalStateException("Bạn không ở trạng thái chọn cách ra tù.");
        }
        Player p = actor.domainPlayer;
        JailSquare jailSq = (JailSquare) board.getSquare(JailSquare.JAIL_POSITION);
        if (!jailSq.useTicketToFree(p)) {
            throw new IllegalStateException("Bạn không có Thẻ Ra Tù Miễn Phí.");
        }
        returnUsedJailCardToDeck();
        allowExtraTurnOnDouble = true;
        turnState = TurnState.WAIT_ROLL;
        addLog("🎫 " + p.getName() + " dùng Thẻ Ra Tù Miễn Phí và được tự do. Hãy tung xúc xắc!");
        lastBanner = p.getName() + " đã dùng Thẻ Ra Tù Miễn Phí!";
    }

    private void handleJailRoll(PlayerSeat actor) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        if (turnState != TurnState.WAIT_JAIL_CHOICE) {
            throw new IllegalStateException("Bạn không ở trạng thái chọn cách ra tù.");
        }

        Player p = actor.domainPlayer;
        JailSquare jailSq = (JailSquare) board.getSquare(JailSquare.JAIL_POSITION);
        dice.roll();
        rollSeq++;
        p.setLastDiceRoll(dice.getTotal());
        p.setTurnsInJail(p.getTurnsInJail() + 1);

        addLog("🎲 " + p.getName() + " thử vận may ra tù: "
                + dice.getDie1() + " + " + dice.getDie2() + " = " + dice.getTotal()
                + (dice.isDouble() ? " [ĐÔI!]" : ""));

        if (dice.isDouble()) {
            p.releaseFromJail();
            allowExtraTurnOnDouble = false; // Luật: đổ đôi thoát tù không được đổ thêm lượt
            rolledDoubleThisRoll = false;
            int posBefore = p.getPosition();
            p.move(dice.getTotal());
            lastMovedPlayerId = actor.id;
            lastMoveFrom = posBefore;
            lastMoveTo = p.getPosition();
            moveSeq++;
            Square landingSq = board.getSquare(p.getPosition());
            addLog("🔓 " + p.getName() + " đổ đôi thoát tù thành công → Ô #" + p.getPosition() + ": " + landingSq.getName());
            resolveSquareLanding(actor, landingSq, 0);
            return;
        }

        if (p.getTurnsInJail() >= 3) {
            addLog("⌛ " + p.getName() + " hết 3 lượt ở tù — bắt buộc nộp $" + JailSquare.BAIL_AMOUNT + ".");
            int balBefore = p.getBalance();
            if (!jailSq.payBail(p)) {
                p.deductMoney(balBefore);
                int shortage = JailSquare.BAIL_AMOUNT - balBefore;
                boolean bankrupt = resolveDebtShortage(actor, shortage, null);
                if (bankrupt) {
                    finishOrAdvanceAfterBankruptcy();
                    return;
                }
                p.releaseFromJail();
            }
            allowExtraTurnOnDouble = false;
            rolledDoubleThisRoll = false;
            int posBefore = p.getPosition();
            p.move(dice.getTotal());
            lastMovedPlayerId = actor.id;
            lastMoveFrom = posBefore;
            lastMoveTo = p.getPosition();
            moveSeq++;
            Square landingSq = board.getSquare(p.getPosition());
            addLog("📍 " + p.getName() + " ra tù và đi tới Ô #" + p.getPosition() + ": " + landingSq.getName());
            resolveSquareLanding(actor, landingSq, 0);
        } else {
            allowExtraTurnOnDouble = false;
            rolledDoubleThisRoll = false;
            turnState = TurnState.WAIT_END_TURN;
            lastBanner = p.getName() + " chưa đổ được đôi — tiếp tục ở tù (" + p.getTurnsInJail() + "/3).";
            addLog("🔒 " + lastBanner);
        }
    }

    // -------------------------------------------------------------------------
    // XỬ LÝ Ô ĐÁP XUỐNG (POLYMORPHIC SQUARES)
    // -------------------------------------------------------------------------

    private void resolveSquareLanding(PlayerSeat actor, Square sq, int redirectDepth) {
        Player p = actor.domainPlayer;

        if (sq instanceof AssetSquare asset) {
            if (asset.getOwner() == null) {
                if (asset.canBuy(p)) {
                    pendingBuySquarePos = asset.getPosition();
                    turnState = TurnState.WAIT_BUY_DECISION;
                    lastBanner = p.getName() + " có muốn mua " + asset.getName() + " ($" + asset.getPrice() + ")?";
                    return;
                } else {
                    addLog("❌ " + p.getName() + " không đủ $" + asset.getPrice() + " để mua " + asset.getName() + ".");
                    lastBanner = p.getName() + " không đủ tiền mua " + asset.getName() + ".";
                    advanceAfterSquareAction(actor);
                    return;
                }
            } else if (asset.getOwner() == p) {
                lastBanner = p.getName() + " ghé thăm tài sản của mình: " + asset.getName() + ".";
                advanceAfterSquareAction(actor);
                return;
            } else {
                payRentToOwner(actor, asset, 1);
                return;
            }
        }

        if (sq instanceof TaxSquare taxSq) {
            int tax = taxSq.getTaxAmount();
            int balBefore = p.getBalance();
            taxSq.applyEffect(p);
            int paid = balBefore - p.getBalance();
            int shortage = Math.max(0, tax - paid);
            addLog("🏛️ " + p.getName() + " nộp thuế " + taxSq.getName() + " ($" + tax + ").");
            if (shortage > 0) {
                boolean bankrupt = resolveDebtShortage(actor, shortage, null);
                if (bankrupt) {
                    finishOrAdvanceAfterBankruptcy();
                    return;
                }
            }
            lastBanner = p.getName() + " đã nộp $" + tax + " tiền thuế.";
            advanceAfterSquareAction(actor);
            return;
        }

        if (sq instanceof JailSquare jailSq) {
            jailSq.applyEffect(p);
            if (jailSq.isGoToJail()) {
                lastMovedPlayerId = actor.id;
                lastMoveFrom = jailSq.getPosition();
                lastMoveTo = JailSquare.JAIL_POSITION;
                moveSeq++;
                allowExtraTurnOnDouble = false;
                rolledDoubleThisRoll = false;
                turnState = TurnState.WAIT_END_TURN;
                addLog("🚔 " + p.getName() + " rơi vào ô Vào Tù → bị đưa tới Nhà Tù (#10)!");
                lastBanner = p.getName() + " bị bắt vào Nhà Tù!";
            } else {
                lastBanner = p.getName() + " chỉ ghé thăm Nhà Tù.";
                advanceAfterSquareAction(actor);
            }
            return;
        }

        if (sq instanceof CardSquare cardSq) {
            handleCardSquareLanding(actor, cardSq, redirectDepth);
            return;
        }

        // GoSquare hoặc FreeParkingSquare
        sq.applyEffect(p);
        if (sq instanceof GoSquare) {
            lastBanner = p.getName() + " dừng chân tại ô GO!";
        } else {
            lastBanner = p.getName() + " nghỉ ngơi miễn phí tại " + sq.getName() + ".";
        }
        advanceAfterSquareAction(actor);
    }

    private void payRentToOwner(PlayerSeat visitorSeat, AssetSquare asset, int multiplier) {
        Player visitor = visitorSeat.domainPlayer;
        Player owner = asset.getOwner();
        PlayerSeat ownerSeat = findSeatByDomainPlayer(owner);

        if (asset.isMortgaged()) {
            addLog("🏚️ " + asset.getName() + " của " + owner.getName() + " đang thế chấp — miễn tiền thuê!");
            lastBanner = asset.getName() + " đang thế chấp, miễn tiền thuê!";
            advanceAfterSquareAction(visitorSeat);
            return;
        }

        int rent;
        if (multiplier == 1) {
            rent = asset.calculateRent(visitor);
        } else {
            // Hệ số đặc biệt từ thẻ Cơ Hội (CH_04 x10 xúc xắc tiện ích, CH_05/06 x2 ga tàu)
            if (asset instanceof UtilitySquare) {
                int roll = visitor.getLastDiceRoll() > 0 ? visitor.getLastDiceRoll() : 7;
                rent = roll * 10;
            } else {
                rent = asset.calculateRent(visitor) * multiplier;
            }
        }

        int paid = Math.min(visitor.getBalance(), rent);
        if (paid > 0) {
            visitor.deductMoney(paid);
            owner.addMoney(paid);
        }
        int shortage = Math.max(0, rent - paid);
        addLog("💸 " + visitor.getName() + " trả $" + rent + " tiền thuê " + asset.getName()
                + " cho " + owner.getName() + ".");

        if (shortage > 0) {
            boolean bankrupt = resolveDebtShortage(visitorSeat, shortage, ownerSeat);
            if (bankrupt) {
                finishOrAdvanceAfterBankruptcy();
                return;
            }
        }
        lastBanner = visitor.getName() + " trả $" + rent + " tiền thuê cho " + owner.getName() + ".";
        advanceAfterSquareAction(visitorSeat);
    }

    private void handleCardSquareLanding(PlayerSeat actor, CardSquare cardSq, int redirectDepth) {
        Player p = actor.domainPlayer;
        List<Player> activePlayers = getActiveDomainPlayers();

        int posBefore = p.getPosition();
        int balBefore = p.getBalance();
        Map<PlayerSeat, Integer> opponentBalancesBefore = new LinkedHashMap<>();
        for (PlayerSeat s : seats) {
            if (s != actor && s.domainPlayer != null && !s.domainPlayer.isBankrupt()) {
                opponentBalancesBefore.put(s, s.domainPlayer.getBalance());
            }
        }

        // Truyền board = null để tự kiểm soát đầy đủ thanh toán nợ và tránh double-effect
        Card drawn = cardSq.applyEffect(p, activePlayers, null);
        if (drawn == null) {
            lastBanner = "Bộ bài đã hết lá!";
            advanceAfterSquareAction(actor);
            return;
        }

        if (drawn.isKeptByPlayer()) {
            keptJailCards.add(drawn);
        }

        cardSeq++;
        Map<String, Object> cardInfo = new LinkedHashMap<>();
        cardInfo.put("seq", cardSeq);
        cardInfo.put("id", drawn.getId());
        cardInfo.put("cardType", drawn.getCardType().name());
        cardInfo.put("cardTypeLabel", drawn.getCardType().getDisplayName());
        cardInfo.put("title", drawn.getTitle());
        cardInfo.put("description", drawn.getDescription());
        cardInfo.put("imagePath", "assets/card/" + drawn.getId() + ".png");
        cardInfo.put("drawnByPlayerId", actor.id);
        cardInfo.put("drawnByName", p.getName());
        cardInfo.put("keptByPlayer", drawn.isKeptByPlayer());
        this.lastCardPayload = cardInfo;

        addLog("🃏 " + p.getName() + " rút thẻ [" + drawn.getCardType().getDisplayName() + "]: "
                + drawn.getTitle() + " — " + drawn.getDescription());

        // Kiểm tra trường hợp thẻ phạt tiền Ngân hàng nhưng người chơi không đủ tiền
        Integer bankPenalty = expectedBankPenaltyForCard(drawn.getId(), p);
        if (bankPenalty != null && bankPenalty > 0) {
            int actualPaid = balBefore - p.getBalance();
            if (actualPaid < bankPenalty) {
                int extraPaid = p.getBalance();
                p.deductMoney(extraPaid);
                int shortage = bankPenalty - (actualPaid + extraPaid);
                if (shortage > 0 && resolveDebtShortage(actor, shortage, null)) {
                    finishOrAdvanceAfterBankruptcy();
                    return;
                }
            }
        }

        // Thẻ CH_16: Chủ tịch HĐQT trả mỗi người khác $50
        if ("CH_16".equals(drawn.getId())) {
            for (Map.Entry<PlayerSeat, Integer> entry : opponentBalancesBefore.entrySet()) {
                PlayerSeat opp = entry.getKey();
                int oppGain = opp.domainPlayer.getBalance() - entry.getValue();
                if (oppGain < 50 && !p.isBankrupt()) {
                    int owe = 50 - oppGain;
                    int cashPart = Math.min(p.getBalance(), owe);
                    if (cashPart > 0) {
                        p.deductMoney(cashPart);
                        opp.domainPlayer.addMoney(cashPart);
                    }
                    int shortage = owe - cashPart;
                    if (shortage > 0 && resolveDebtShortage(actor, shortage, opp)) {
                        finishOrAdvanceAfterBankruptcy();
                        return;
                    }
                }
            }
        }

        // Thẻ CC_09: Chúc mừng sinh nhật thu mỗi người khác $10
        if ("CC_09".equals(drawn.getId())) {
            for (Map.Entry<PlayerSeat, Integer> entry : opponentBalancesBefore.entrySet()) {
                PlayerSeat opp = entry.getKey();
                int oppPaid = entry.getValue() - opp.domainPlayer.getBalance();
                if (oppPaid < 10 && !opp.domainPlayer.isBankrupt()) {
                    int owe = 10 - oppPaid;
                    int cashPart = Math.min(opp.domainPlayer.getBalance(), owe);
                    if (cashPart > 0) {
                        opp.domainPlayer.deductMoney(cashPart);
                        p.addMoney(cashPart);
                    }
                    int shortage = owe - cashPart;
                    if (shortage > 0) {
                        resolveDebtShortage(opp, shortage, actor);
                    }
                }
            }
            if (countAliveSeats() <= 1) {
                finishOrAdvanceAfterBankruptcy();
                return;
            }
        }

        // Nếu thẻ đưa vào tù ngay lập tức
        if (p.isInJail()) {
            lastMovedPlayerId = actor.id;
            lastMoveFrom = posBefore;
            lastMoveTo = Player.JAIL_POSITION;
            moveSeq++;
            allowExtraTurnOnDouble = false;
            rolledDoubleThisRoll = false;
            turnState = TurnState.WAIT_END_TURN;
            lastBanner = p.getName() + " rút trúng thẻ Vào Tù!";
            return;
        }

        // Nếu thẻ làm người chơi di chuyển tới ô khác
        if (p.getPosition() != posBefore) {
            lastMovedPlayerId = actor.id;
            lastMoveFrom = posBefore;
            lastMoveTo = p.getPosition();
            moveSeq++;

            Square dest = board.getSquare(p.getPosition());
            addLog("📍 Hiệu ứng thẻ đưa " + p.getName() + " tới Ô #" + dest.getPosition() + ": " + dest.getName());

            if ("CH_04".equals(drawn.getId()) && dest instanceof UtilitySquare util) {
                if (util.getOwner() == null) {
                    if (util.canBuy(p)) {
                        pendingBuySquarePos = util.getPosition();
                        turnState = TurnState.WAIT_BUY_DECISION;
                        lastBanner = p.getName() + " tới " + util.getName() + " — có muốn mua ($" + util.getPrice() + ")?";
                        return;
                    }
                    lastBanner = p.getName() + " tới " + util.getName() + " (không đủ tiền mua).";
                    advanceAfterSquareAction(actor);
                    return;
                } else if (util.getOwner() != p) {
                    payRentToOwner(actor, util, 10);
                    return;
                }
            }

            if (("CH_05".equals(drawn.getId()) || "CH_06".equals(drawn.getId()))
                    && dest instanceof RailroadSquare rr) {
                if (rr.getOwner() == null) {
                    if (rr.canBuy(p)) {
                        pendingBuySquarePos = rr.getPosition();
                        turnState = TurnState.WAIT_BUY_DECISION;
                        lastBanner = p.getName() + " tới " + rr.getName() + " — có muốn mua ($" + rr.getPrice() + ")?";
                        return;
                    }
                    lastBanner = p.getName() + " tới " + rr.getName() + " (không đủ tiền mua).";
                    advanceAfterSquareAction(actor);
                    return;
                } else if (rr.getOwner() != p) {
                    payRentToOwner(actor, rr, 2);
                    return;
                }
            }

            if (redirectDepth < 2) {
                resolveSquareLanding(actor, dest, redirectDepth + 1);
                return;
            }
        }

        lastBanner = p.getName() + " rút thẻ: " + drawn.getTitle();
        advanceAfterSquareAction(actor);
    }

    private Integer expectedBankPenaltyForCard(String cardId, Player p) {
        return switch (cardId) {
            case "CH_12" -> CardDeck.calculateRepairCost(p, 25, 100);
            case "CH_13" -> 15;
            case "CC_03", "CC_12" -> 50;
            case "CC_11" -> 100;
            case "CC_14" -> CardDeck.calculateRepairCost(p, 40, 115);
            default -> null;
        };
    }

    private void handleBuyDecision(PlayerSeat actor, boolean buy) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        if (turnState != TurnState.WAIT_BUY_DECISION || pendingBuySquarePos < 0) {
            throw new IllegalStateException("Hiện không có ô đất nào chờ quyết định mua.");
        }

        Square sq = board.getSquare(pendingBuySquarePos);
        pendingBuySquarePos = -1;
        if (!(sq instanceof AssetSquare asset)) {
            advanceAfterSquareAction(actor);
            return;
        }

        Player p = actor.domainPlayer;
        if (buy) {
            if (!asset.buyAsset(p)) {
                throw new IllegalStateException("Không thể mua ô " + asset.getName() + ".");
            }
            addLog("✅ " + p.getName() + " đã mua " + asset.getName() + " với giá $" + asset.getPrice() + ".");
            lastBanner = p.getName() + " đã sở hữu " + asset.getName() + "!";
        } else {
            addLog("⏭️ " + p.getName() + " bỏ qua không mua " + asset.getName() + ".");
            lastBanner = p.getName() + " không mua " + asset.getName() + ".";
        }
        advanceAfterSquareAction(actor);
    }

    private void advanceAfterSquareAction(PlayerSeat actor) {
        Player p = actor.domainPlayer;
        if (p.isBankrupt()) {
            finishOrAdvanceAfterBankruptcy();
            return;
        }
        if (rolledDoubleThisRoll && allowExtraTurnOnDouble && !p.isInJail()) {
            rolledDoubleThisRoll = false;
            turnState = TurnState.WAIT_ROLL;
            addLog("✨ " + p.getName() + " đổ đôi nên được đổ thêm lượt!");
        } else {
            rolledDoubleThisRoll = false;
            turnState = TurnState.WAIT_END_TURN;
        }
    }

    // -------------------------------------------------------------------------
    // QUẢN LÝ TÀI SẢN CHỦ ĐỘNG & GIAO DỊCH (TRADE)
    // -------------------------------------------------------------------------

    private void handleUpgradeProperty(PlayerSeat actor, int squarePos) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        requireManageActionAllowed();

        Square sq = board.getSquare(squarePos);
        if (!(sq instanceof PropertySquare prop)) {
            throw new IllegalArgumentException("Ô được chọn không phải đất màu có thể xây nhà.");
        }
        Player p = actor.domainPlayer;
        if (!prop.canUpgrade(p)) {
            throw new IllegalStateException("Chưa đủ điều kiện nâng cấp ô " + prop.getName()
                    + " (cần đủ bộ màu, không thế chấp, xây đều tay và đủ $" + prop.getHouseCost() + ").");
        }
        prop.upgrade(p);
        String capText = prop.getHouseLevel() == 5 ? "Khách Sạn (Cấp 5)" : ("Nhà cấp " + prop.getHouseLevel());
        addLog("🏗️ " + p.getName() + " nâng cấp " + prop.getName() + " lên " + capText + " (-$" + prop.getHouseCost() + ").");
        lastBanner = p.getName() + " nâng cấp " + prop.getName() + " lên " + capText + "!";
    }

    private void handleMortgageAsset(PlayerSeat actor, int squarePos) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        requireManageActionAllowed();

        Square sq = board.getSquare(squarePos);
        if (!(sq instanceof AssetSquare asset)) {
            throw new IllegalArgumentException("Ô được chọn không phải tài sản.");
        }
        Player p = actor.domainPlayer;
        if (!asset.canMortgage(p)) {
            throw new IllegalStateException("Không thể thế chấp " + asset.getName()
                    + " (hãy đảm bảo ô thuộc sở hữu của bạn và cả nhóm màu không có nhà).");
        }
        asset.mortgage(p);
        addLog("🏦 " + p.getName() + " thế chấp " + asset.getName() + " nhận +$" + asset.getMortgageValue() + ".");
        lastBanner = p.getName() + " đã thế chấp " + asset.getName() + ".";
    }

    private void handleUnmortgageAsset(PlayerSeat actor, int squarePos) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        requireManageActionAllowed();

        Square sq = board.getSquare(squarePos);
        if (!(sq instanceof AssetSquare asset)) {
            throw new IllegalArgumentException("Ô được chọn không phải tài sản.");
        }
        Player p = actor.domainPlayer;
        if (!asset.canUnmortgage(p)) {
            throw new IllegalStateException("Không thể chuộc " + asset.getName()
                    + " (cần $" + asset.getUnmortgageCost() + ").");
        }
        asset.unmortgage(p);
        addLog("🔓 " + p.getName() + " chuộc lại " + asset.getName() + " (-$" + asset.getUnmortgageCost() + ").");
        lastBanner = p.getName() + " đã chuộc lại " + asset.getName() + "!";
    }

    private void handleProposeTrade(PlayerSeat actor, Map<String, Object> payload) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        requireManageActionAllowed();

        String receiverId = JsonUtil.getString(payload, "receiverId", "");
        PlayerSeat receiverSeat = findSeatById(receiverId);
        if (receiverSeat == null || receiverSeat == actor || receiverSeat.domainPlayer == null) {
            throw new IllegalArgumentException("Đối tác giao dịch không hợp lệ.");
        }

        int offeredCash = JsonUtil.getInt(payload, "offeredCash", 0);
        int requestedCash = JsonUtil.getInt(payload, "requestedCash", 0);
        int offeredJailCards = JsonUtil.getInt(payload, "offeredJailCards", 0);
        int requestedJailCards = JsonUtil.getInt(payload, "requestedJailCards", 0);

        List<AssetSquare> offeredProps = resolveAssets(JsonUtil.getIntList(payload, "offeredAssets"));
        List<AssetSquare> requestedProps = resolveAssets(JsonUtil.getIntList(payload, "requestedAssets"));

        TradeOffer offer = new TradeOffer(
                actor.domainPlayer,
                receiverSeat.domainPlayer,
                offeredCash,
                offeredProps,
                offeredJailCards,
                requestedCash,
                requestedProps,
                requestedJailCards
        );

        TradeValidationResult result = TradeValidator.validate(offer);
        if (!result.isValid()) {
            throw new IllegalStateException(result.getErrorMessage());
        }

        this.returnTurnState = this.turnState;
        this.pendingTradeOffer = offer;
        this.pendingTradeSenderId = actor.id;
        this.pendingTradeReceiverId = receiverSeat.id;
        this.turnState = TurnState.WAIT_TRADE_RESPONSE;

        addLog("🤝 " + actor.name + " gửi đề nghị giao dịch tới " + receiverSeat.name + ".");
        lastBanner = actor.name + " mời " + receiverSeat.name + " thương lượng giao dịch!";
    }

    private void handleRespondTrade(PlayerSeat actor, boolean accept) {
        requirePhase(RoomPhase.IN_GAME);
        if (turnState != TurnState.WAIT_TRADE_RESPONSE || pendingTradeOffer == null) {
            throw new IllegalStateException("Không có đề nghị giao dịch nào đang chờ phản hồi.");
        }
        if (!actor.id.equals(pendingTradeReceiverId)) {
            throw new IllegalStateException("Chỉ người nhận đề nghị mới có quyền đồng ý hoặc từ chối.");
        }

        TradeOffer offer = this.pendingTradeOffer;
        String senderName = offer.getSender().getName();
        String receiverName = offer.getReceiver().getName();

        this.pendingTradeOffer = null;
        this.pendingTradeSenderId = null;
        this.pendingTradeReceiverId = null;
        this.turnState = this.returnTurnState;

        if (accept) {
            if (TradeExecutor.executeTrade(offer)) {
                addLog("🎉 Giao dịch giữa " + senderName + " và " + receiverName + " thành công!");
                lastBanner = senderName + " & " + receiverName + " đã chốt giao dịch!";
            } else {
                addLog("❌ Giao dịch giữa " + senderName + " và " + receiverName + " không còn hợp lệ.");
                lastBanner = "Giao dịch thất bại do điều kiện thay đổi.";
            }
        } else {
            addLog("👋 " + receiverName + " đã từ chối đề nghị giao dịch của " + senderName + ".");
            lastBanner = receiverName + " đã từ chối giao dịch.";
        }
    }

    private void handleCancelTrade(PlayerSeat actor) {
        requirePhase(RoomPhase.IN_GAME);
        if (turnState != TurnState.WAIT_TRADE_RESPONSE || pendingTradeOffer == null) {
            throw new IllegalStateException("Không có giao dịch nào đang chờ để hủy.");
        }
        if (!actor.id.equals(pendingTradeSenderId)) {
            throw new IllegalStateException("Chỉ người gửi đề nghị mới có quyền hủy.");
        }
        this.pendingTradeOffer = null;
        this.pendingTradeSenderId = null;
        this.pendingTradeReceiverId = null;
        this.turnState = this.returnTurnState;
        addLog("🚫 " + actor.name + " đã hủy lời đề nghị giao dịch.");
        lastBanner = actor.name + " đã hủy giao dịch.";
    }

    private void handleEndTurn(PlayerSeat actor) {
        requirePhase(RoomPhase.IN_GAME);
        requireActivePlayer(actor);
        if (turnState != TurnState.WAIT_END_TURN) {
            throw new IllegalStateException("Chưa thể kết thúc lượt (trạng thái hiện tại: " + turnState + ").");
        }
        advanceToNextAlivePlayer();
    }

    private void handleSurrender(PlayerSeat actor) {
        requirePhase(RoomPhase.IN_GAME);
        if (actor.domainPlayer == null || actor.domainPlayer.isBankrupt()) {
            throw new IllegalStateException("Bạn đã rời khỏi cuộc đua trước đó.");
        }

        if (pendingTradeOffer != null
                && (actor.id.equals(pendingTradeSenderId) || actor.id.equals(pendingTradeReceiverId))) {
            pendingTradeOffer = null;
            pendingTradeSenderId = null;
            pendingTradeReceiverId = null;
            turnState = returnTurnState;
        }

        boolean wasCurrent = (actor == currentSeat());
        addLog("🏳️ " + actor.name + " đã đầu hàng và rút khỏi ván chơi.");
        declareBankruptcy(actor, null);

        if (countAliveSeats() <= 1) {
            finishGame();
        } else if (wasCurrent) {
            pendingBuySquarePos = -1;
            advanceToNextAlivePlayer();
        }
    }

    // -------------------------------------------------------------------------
    // PHÁ SẢN & CHUYỂN LƯỢT (BANKRUPTCY)
    // -------------------------------------------------------------------------

    private boolean resolveDebtShortage(PlayerSeat debtorSeat, int shortage, PlayerSeat creditorSeat) {
        if (shortage <= 0) return false;
        Player debtor = debtorSeat.domainPlayer;
        Player creditor = creditorSeat != null ? creditorSeat.domainPlayer : null;

        // 1. Tự động thế chấp các tài sản hợp lệ (canMortgage)
        for (AssetSquare asset : new ArrayList<>(debtor.getOwnedAssets())) {
            if (debtor.getBalance() >= shortage) break;
            if (asset.canMortgage(debtor)) {
                asset.mortgage(debtor);
                addLog("🏦 " + debtor.getName() + " tự động thế chấp " + asset.getName()
                        + " (+" + "$" + asset.getMortgageValue() + ").");
            }
        }

        // 2. Kiểm tra lại khả năng thanh toán
        if (debtor.getBalance() >= shortage) {
            debtor.deductMoney(shortage);
            if (creditor != null) {
                creditor.addMoney(shortage);
            }
            addLog("✅ " + debtor.getName() + " đã xoay sở đủ tiền trả nợ ($" + shortage + ").");
            return false;
        }

        // 3. Tuyên bố phá sản
        declareBankruptcy(debtorSeat, creditorSeat);
        return true;
    }

    private void declareBankruptcy(PlayerSeat debtorSeat, PlayerSeat creditorSeat) {
        Player debtor = debtorSeat.domainPlayer;
        Player creditor = creditorSeat != null ? creditorSeat.domainPlayer : null;

        addLog("💥 " + debtor.getName() + " CHÍNH THỨC PHÁ SẢN!");
        lastBanner = debtor.getName() + " đã PHÁ SẢN!";

        if (creditor == null) {
            for (AssetSquare asset : new ArrayList<>(debtor.getOwnedAssets())) {
                asset.setOwner(null);
                asset.setMortgaged(false);
                if (asset instanceof PropertySquare prop) {
                    prop.setHouseLevel(0);
                }
                debtor.removeAsset(asset);
            }
        } else {
            creditor.addMoney(debtor.getBalance());
            for (AssetSquare asset : new ArrayList<>(debtor.getOwnedAssets())) {
                asset.setOwner(creditor);
                creditor.addAsset(asset);
                debtor.removeAsset(asset);
            }
            while (debtor.useOutOfJailTicket()) {
                creditor.addOutOfJailTicket();
            }
        }

        debtor.deductMoney(debtor.getBalance());
        debtor.setBankrupt(true);
    }

    private void finishOrAdvanceAfterBankruptcy() {
        if (countAliveSeats() <= 1) {
            finishGame();
        } else {
            advanceToNextAlivePlayer();
        }
    }

    private void finishGame() {
        this.phase = RoomPhase.GAME_OVER;
        this.pendingBuySquarePos = -1;
        this.pendingTradeOffer = null;
        PlayerSeat winner = null;
        for (PlayerSeat s : seats) {
            if (s.domainPlayer != null && !s.domainPlayer.isBankrupt()) {
                winner = s;
                break;
            }
        }
        if (winner != null) {
            this.winnerPlayerId = winner.id;
            addLog("🏆 CHÚC MỪNG " + winner.name.toUpperCase() + " ĐÃ CHIẾN THẮNG!");
            lastBanner = "🏆 " + winner.name + " giành chiến thắng chung cuộc!";
        } else {
            this.winnerPlayerId = null;
            addLog("💀 Toàn bộ người chơi đã phá sản — ván đấu kết thúc hòa.");
            lastBanner = "Trò chơi kết thúc hòa!";
        }
    }

    private void advanceToNextAlivePlayer() {
        int total = seats.size();
        for (int offset = 1; offset <= total; offset++) {
            int idx = (currentSeatIndex + offset) % total;
            PlayerSeat candidate = seats.get(idx);
            if (candidate.domainPlayer != null && !candidate.domainPlayer.isBankrupt()) {
                currentSeatIndex = idx;
                turnNumber++;
                dice.resetConsecutiveDoubles();
                candidate.domainPlayer.resetConsecutiveDoubles();
                allowExtraTurnOnDouble = true;
                rolledDoubleThisRoll = false;
                pendingBuySquarePos = -1;
                turnState = candidate.domainPlayer.isInJail()
                        ? TurnState.WAIT_JAIL_CHOICE
                        : TurnState.WAIT_ROLL;
                addLog("🎯 Lượt #" + turnNumber + ": " + candidate.name);
                lastBanner = "Đến lượt của " + candidate.name + "!";
                return;
            }
        }
        finishGame();
    }

    // =========================================================================
    // BOT AI TỰ ĐỘNG (CHẠY NHỊP NHÀNG ĐỂ NGƯỜI CHƠI & NGƯỜI XEM QUAN SÁT)
    // =========================================================================

    private boolean shouldScheduleBot() {
        if (phase != RoomPhase.IN_GAME || seats.isEmpty()) return false;
        if (turnState == TurnState.WAIT_TRADE_RESPONSE) {
            PlayerSeat receiver = findSeatById(pendingTradeReceiverId);
            return receiver != null && receiver.isBot;
        }
        PlayerSeat current = currentSeat();
        return current != null && current.isBot && current.domainPlayer != null && !current.domainPlayer.isBankrupt();
    }

    private void scheduleBotStep() {
        final long expectedVersion;
        synchronized (this) {
            expectedVersion = this.stateVersion;
        }
        BOT_SCHEDULER.schedule(() -> executeBotStep(expectedVersion), 900L, TimeUnit.MILLISECONDS);
    }

    private void executeBotStep(long expectedVersion) {
        boolean scheduleMore = false;
        synchronized (this) {
            if (phase != RoomPhase.IN_GAME || stateVersion != expectedVersion) {
                return;
            }
            touch();

            if (turnState == TurnState.WAIT_TRADE_RESPONSE && pendingTradeOffer != null) {
                PlayerSeat receiver = findSeatById(pendingTradeReceiverId);
                if (receiver == null || !receiver.isBot) return;
                boolean accept = evaluateTradeForBot(pendingTradeOffer);
                handleRespondTrade(receiver, accept);
                bumpState();
                scheduleMore = shouldScheduleBot();
            } else {
                PlayerSeat botSeat = currentSeat();
                if (botSeat == null || !botSeat.isBot || botSeat.domainPlayer == null || botSeat.domainPlayer.isBankrupt()) {
                    return;
                }
                Player bot = botSeat.domainPlayer;
                switch (turnState) {
                    case WAIT_JAIL_CHOICE -> {
                        if (bot.getOutOfJailTicket() > 0) {
                            handleJailUseCard(botSeat);
                        } else if (bot.getBalance() >= 250) {
                            handleJailPayBail(botSeat);
                        } else {
                            handleJailRoll(botSeat);
                        }
                    }
                    case WAIT_ROLL -> handleRollDice(botSeat);
                    case WAIT_BUY_DECISION -> {
                        Square sq = board.getSquare(pendingBuySquarePos);
                        boolean shouldBuy = (sq instanceof AssetSquare asset)
                                && asset.canBuy(bot)
                                && (bot.getBalance() - asset.getPrice() >= 180 || asset.getPrice() <= 140);
                        handleBuyDecision(botSeat, shouldBuy);
                    }
                    case WAIT_END_TURN -> {
                        // Thử nâng cấp 1 nhà nếu dư dả, rồi mới kết thúc lượt
                        boolean didUpgrade = tryBotUpgradeOneProperty(botSeat);
                        if (!didUpgrade) {
                            tryBotUnmortgageOneAsset(botSeat);
                            handleEndTurn(botSeat);
                        }
                    }
                    default -> {
                        return;
                    }
                }
                bumpState();
                scheduleMore = shouldScheduleBot();
            }
        }

        broadcastState();
        if (scheduleMore) {
            scheduleBotStep();
        }
    }

    private boolean tryBotUpgradeOneProperty(PlayerSeat botSeat) {
        Player bot = botSeat.domainPlayer;
        for (AssetSquare asset : new ArrayList<>(bot.getOwnedAssets())) {
            if (asset instanceof PropertySquare prop && prop.canUpgrade(bot)
                    && bot.getBalance() - prop.getHouseCost() >= 260) {
                handleUpgradeProperty(botSeat, prop.getPosition());
                return true;
            }
        }
        return false;
    }

    private void tryBotUnmortgageOneAsset(PlayerSeat botSeat) {
        Player bot = botSeat.domainPlayer;
        for (AssetSquare asset : new ArrayList<>(bot.getOwnedAssets())) {
            if (asset.canUnmortgage(bot) && bot.getBalance() - asset.getUnmortgageCost() >= 320) {
                handleUnmortgageAsset(botSeat, asset.getPosition());
                return;
            }
        }
    }

    private boolean evaluateTradeForBot(TradeOffer offer) {
        int receiveValue = offer.getOfferedCash() + offer.getOfferedJailCards() * 45;
        for (AssetSquare a : offer.getOfferedProperties()) {
            receiveValue += a.isMortgaged() ? a.getMortgageValue() : a.getPrice();
        }
        int giveValue = offer.getRequestedCash() + offer.getRequestedJailCards() * 45;
        for (AssetSquare a : offer.getRequestedProperties()) {
            giveValue += a.isMortgaged() ? a.getMortgageValue() : a.getPrice();
        }
        return receiveValue >= giveValue * 0.95;
    }

    // =========================================================================
    // SNAPSHOT DTO PHẲNG (AN TOÀN TUYỆT ĐỐI, KHÔNG THAM CHIẾU VÒNG)
    // =========================================================================

    public void broadcastState() {
        sseHub.broadcast(ch -> {
            synchronized (OnlineGameRoom.this) {
                return JsonUtil.toJson(buildSnapshotMap(ch.token));
            }
        });
    }

    public synchronized String buildSnapshotJson(String viewerToken) {
        return JsonUtil.toJson(buildSnapshotMap(viewerToken));
    }

    public synchronized Map<String, Object> buildSnapshotMap(String viewerToken) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("roomCode", roomCode);
        root.put("roomName", roomName);
        root.put("isPublic", isPublic);
        root.put("maxPlayers", maxPlayers);
        root.put("startingBalance", startingBalance);
        root.put("phase", phase.name());
        root.put("turnState", turnState.name());
        root.put("turnNumber", turnNumber);
        root.put("stateVersion", stateVersion);
        root.put("rollSeq", rollSeq);
        root.put("cardSeq", cardSeq);
        root.put("moveSeq", moveSeq);
        root.put("lastMovedPlayerId", lastMovedPlayerId);
        root.put("lastMoveFrom", lastMoveFrom);
        root.put("lastMoveTo", lastMoveTo);
        root.put("lastBanner", lastBanner);
        root.put("lastCard", lastCardPayload);
        root.put("winnerPlayerId", winnerPlayerId);
        root.put("spectatorCount", spectators.size());
        root.put("pendingBuySquarePos", pendingBuySquarePos);

        // Thông tin người xem hiện tại
        PlayerSeat viewerSeat = findSeatByToken(viewerToken);
        SpectatorSeat specSeat = viewerToken != null ? spectators.get(viewerToken) : null;
        Map<String, Object> me = new LinkedHashMap<>();
        if (viewerSeat != null) {
            me.put("role", "PLAYER");
            me.put("playerId", viewerSeat.id);
            me.put("name", viewerSeat.name);
            me.put("isHost", viewerSeat.token.equals(hostToken));
        } else if (specSeat != null) {
            me.put("role", "SPECTATOR");
            me.put("playerId", null);
            me.put("name", specSeat.name);
            me.put("isHost", false);
        } else {
            me.put("role", "GUEST");
            me.put("playerId", null);
            me.put("name", "Khách");
            me.put("isHost", false);
        }
        root.put("me", me);

        PlayerSeat current = currentSeat();
        root.put("currentPlayerId", (phase == RoomPhase.IN_GAME && current != null) ? current.id : null);

        // Xúc xắc
        Map<String, Object> diceDto = new LinkedHashMap<>();
        diceDto.put("die1", dice != null ? dice.getDie1() : 0);
        diceDto.put("die2", dice != null ? dice.getDie2() : 0);
        diceDto.put("total", dice != null ? dice.getTotal() : 0);
        diceDto.put("isDouble", dice != null && dice.isDouble());
        diceDto.put("consecutiveDoubles", dice != null ? dice.getConsecutiveDoubles() : 0);
        root.put("dice", diceDto);

        // Danh sách người chơi (ghế)
        List<Map<String, Object>> playerDtos = new ArrayList<>();
        for (PlayerSeat seat : seats) {
            playerDtos.add(buildPlayerDto(seat));
        }
        root.put("players", playerDtos);

        // Danh sách người xem
        List<String> spectatorNames = new ArrayList<>();
        for (SpectatorSeat s : spectators.values()) {
            spectatorNames.add(s.name);
        }
        root.put("spectators", spectatorNames);

        // 40 ô cờ
        List<Map<String, Object>> squareDtos = new ArrayList<>(40);
        if (board != null) {
            Player viewerDomain = viewerSeat != null ? viewerSeat.domainPlayer : null;
            for (int i = 0; i < 40; i++) {
                squareDtos.add(buildSquareDto(board.getSquare(i), viewerDomain));
            }
        }
        root.put("squares", squareDtos);

        // Giao dịch đang chờ
        if (pendingTradeOffer != null) {
            Map<String, Object> tradeDto = new LinkedHashMap<>();
            tradeDto.put("senderId", pendingTradeSenderId);
            tradeDto.put("senderName", pendingTradeOffer.getSender().getName());
            tradeDto.put("receiverId", pendingTradeReceiverId);
            tradeDto.put("receiverName", pendingTradeOffer.getReceiver().getName());
            tradeDto.put("offeredCash", pendingTradeOffer.getOfferedCash());
            tradeDto.put("offeredJailCards", pendingTradeOffer.getOfferedJailCards());
            tradeDto.put("offeredAssets", toAssetBriefList(pendingTradeOffer.getOfferedProperties()));
            tradeDto.put("requestedCash", pendingTradeOffer.getRequestedCash());
            tradeDto.put("requestedJailCards", pendingTradeOffer.getRequestedJailCards());
            tradeDto.put("requestedAssets", toAssetBriefList(pendingTradeOffer.getRequestedProperties()));
            root.put("pendingTrade", tradeDto);
        } else {
            root.put("pendingTrade", null);
        }

        // Nhật ký & Chat (mới nhất trước cho log, theo thứ tự thời gian cho chat)
        root.put("logs", new ArrayList<>(logs));
        root.put("chats", new ArrayList<>(chats));
        return root;
    }

    private Map<String, Object> buildPlayerDto(PlayerSeat seat) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", seat.id);
        dto.put("name", seat.name);
        dto.put("avatarIndex", seat.avatarIndex);
        dto.put("avatarUrl", PLAYER_AVATAR_FILES[seat.avatarIndex]);
        dto.put("colorHex", seat.colorHex);
        dto.put("isBot", seat.isBot);
        dto.put("connected", seat.connected);
        dto.put("isHost", seat.token.equals(hostToken));

        Player p = seat.domainPlayer;
        if (p != null) {
            dto.put("balance", p.getBalance());
            dto.put("position", p.getPosition());
            dto.put("inJail", p.isInJail());
            dto.put("turnsInJail", p.getTurnsInJail());
            dto.put("bankrupt", p.isBankrupt());
            dto.put("outOfJailTickets", p.getOutOfJailTicket());
            dto.put("assetCount", p.getOwnedAssets().size());
            dto.put("netWorth", p.getNetWorth());
            dto.put("totalAssetValue", computeTotalAssetValue(p));
        } else {
            dto.put("balance", startingBalance);
            dto.put("position", 0);
            dto.put("inJail", false);
            dto.put("turnsInJail", 0);
            dto.put("bankrupt", false);
            dto.put("outOfJailTickets", 0);
            dto.put("assetCount", 0);
            dto.put("netWorth", startingBalance);
            dto.put("totalAssetValue", startingBalance);
        }
        return dto;
    }

    private Map<String, Object> buildSquareDto(Square sq, Player viewerDomain) {
        Map<String, Object> dto = new LinkedHashMap<>();
        int pos = sq.getPosition();
        dto.put("position", pos);
        dto.put("name", sq.getName());
        dto.put("cellImg", "assets/square/cells_40/" + CELL_IMAGE_FILES[pos] + "?v=2");
        dto.put("uprightImg", "assets/square/cells_40_upright/" + CELL_IMAGE_FILES[pos] + "?v=2");

        if (sq instanceof PropertySquare prop) {
            dto.put("kind", "PROPERTY");
            fillAssetCommon(dto, prop, viewerDomain);
            dto.put("baseRent", prop.getBaseRent());
            dto.put("houseLevel", prop.getHouseLevel());
            dto.put("houseCost", prop.getHouseCost());
            dto.put("colorGroup", prop.getColorGroup() != null ? prop.getColorGroup().name() : null);
            dto.put("colorGroupLabel", prop.getColorGroup() != null ? prop.getColorGroup().getDisplayName() : null);
            dto.put("currentRent", prop.calculateRent(null));
            dto.put("rentTiers", List.of(
                    prop.getBaseRent(),
                    prop.getBaseRent() * 2,
                    prop.getBaseRent() * 5,
                    prop.getBaseRent() * 15,
                    prop.getBaseRent() * 36,
                    prop.getBaseRent() * 44,
                    prop.getBaseRent() * 52
            ));
            dto.put("viewerCanUpgrade", viewerDomain != null && prop.canUpgrade(viewerDomain));
        } else if (sq instanceof RailroadSquare rr) {
            dto.put("kind", "RAILROAD");
            fillAssetCommon(dto, rr, viewerDomain);
            dto.put("currentRent", rr.calculateRent(null));
            dto.put("rentTiers", List.of(25, 50, 100, 200));
            dto.put("viewerCanUpgrade", false);
        } else if (sq instanceof UtilitySquare util) {
            dto.put("kind", "UTILITY");
            fillAssetCommon(dto, util, viewerDomain);
            dto.put("currentRent", 0);
            dto.put("rentNote", "4x xúc xắc (1 công ty) / 10x xúc xắc (2 công ty)");
            dto.put("viewerCanUpgrade", false);
        } else if (sq instanceof TaxSquare tax) {
            dto.put("kind", "TAX");
            dto.put("taxAmount", tax.getTaxAmount());
        } else if (sq instanceof CardSquare cardSq) {
            dto.put("kind", cardSq.getCardType() == CardType.CHANCE ? "CHANCE" : "COMMUNITY_CHEST");
        } else if (sq instanceof JailSquare jail) {
            dto.put("kind", jail.isGoToJail() ? "GO_TO_JAIL" : "JAIL");
        } else if (sq instanceof GoSquare) {
            dto.put("kind", "GO");
        } else {
            dto.put("kind", "FREE_PARKING");
        }
        return dto;
    }

    private void fillAssetCommon(Map<String, Object> dto, AssetSquare asset, Player viewerDomain) {
        Player owner = asset.getOwner();
        PlayerSeat ownerSeat = findSeatByDomainPlayer(owner);
        dto.put("price", asset.getPrice());
        dto.put("mortgageValue", asset.getMortgageValue());
        dto.put("unmortgageCost", asset.getUnmortgageCost());
        dto.put("mortgaged", asset.isMortgaged());
        dto.put("ownerId", ownerSeat != null ? ownerSeat.id : null);
        dto.put("ownerName", ownerSeat != null ? ownerSeat.name : null);
        dto.put("ownerColor", ownerSeat != null ? ownerSeat.colorHex : null);
        dto.put("viewerCanMortgage", viewerDomain != null && asset.canMortgage(viewerDomain));
        dto.put("viewerCanUnmortgage", viewerDomain != null && asset.canUnmortgage(viewerDomain));
        dto.put("viewerIsTradable", viewerDomain != null && TradeValidator.isAssetTradable(viewerDomain, asset));
    }

    private List<Map<String, Object>> toAssetBriefList(List<AssetSquare> assets) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AssetSquare a : assets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("position", a.getPosition());
            m.put("name", a.getName());
            m.put("price", a.getPrice());
            m.put("mortgaged", a.isMortgaged());
            list.add(m);
        }
        return list;
    }

    private int computeTotalAssetValue(Player p) {
        int total = p.getBalance();
        for (AssetSquare asset : p.getOwnedAssets()) {
            total += asset.isMortgaged() ? asset.getMortgageValue() : asset.getPrice();
            if (asset instanceof PropertySquare prop && prop.getHouseLevel() > 0) {
                total += prop.getHouseLevel() * prop.getHouseCost();
            }
        }
        return total;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void returnUsedJailCardToDeck() {
        if (!keptJailCards.isEmpty()) {
            Card used = keptJailCards.remove(0);
            if (used.getCardType() == CardType.COMMUNITY_CHEST) {
                board.getCommunityChestDeck().returnCard(used);
            } else {
                board.getChanceDeck().returnCard(used);
            }
        }
    }

    private List<AssetSquare> resolveAssets(List<Integer> positions) {
        List<AssetSquare> list = new ArrayList<>();
        for (Integer pos : positions) {
            Square sq = board.getSquare(pos);
            if (sq instanceof AssetSquare asset) {
                list.add(asset);
            }
        }
        return list;
    }

    private List<Player> getActiveDomainPlayers() {
        List<Player> list = new ArrayList<>();
        for (PlayerSeat s : seats) {
            if (s.domainPlayer != null && !s.domainPlayer.isBankrupt()) {
                list.add(s.domainPlayer);
            }
        }
        return list;
    }

    private int countAliveSeats() {
        int c = 0;
        for (PlayerSeat s : seats) {
            if (s.domainPlayer != null && !s.domainPlayer.isBankrupt()) c++;
        }
        return c;
    }

    private int countHumanSeats() {
        int c = 0;
        for (PlayerSeat s : seats) {
            if (!s.isBot) c++;
        }
        return c;
    }

    private PlayerSeat currentSeat() {
        if (seats.isEmpty() || currentSeatIndex < 0 || currentSeatIndex >= seats.size()) {
            return null;
        }
        return seats.get(currentSeatIndex);
    }

    private PlayerSeat findSeatByToken(String token) {
        if (token == null) return null;
        for (PlayerSeat s : seats) {
            if (s.token.equals(token)) return s;
        }
        return null;
    }

    private PlayerSeat findSeatById(String id) {
        if (id == null) return null;
        for (PlayerSeat s : seats) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    private PlayerSeat findSeatByDomainPlayer(Player p) {
        if (p == null) return null;
        for (PlayerSeat s : seats) {
            if (s.domainPlayer == p) return s;
        }
        return null;
    }

    private void promoteNextHost() {
        hostToken = null;
        for (PlayerSeat s : seats) {
            if (!s.isBot) {
                hostToken = s.token;
                return;
            }
        }
    }

    private int pickNextAvailableAvatarIndex() {
        boolean[] used = new boolean[6];
        for (PlayerSeat s : seats) {
            if (s.avatarIndex >= 0 && s.avatarIndex < 6) {
                used[s.avatarIndex] = true;
            }
        }
        for (int i = 0; i < 6; i++) {
            if (!used[i]) return i;
        }
        return seats.size() % 6;
    }

    private String ensureUniquePlayerName(String base) {
        String candidate = base;
        int suffix = 2;
        while (nameExists(candidate)) {
            candidate = base + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private boolean nameExists(String name) {
        for (PlayerSeat s : seats) {
            if (s.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) return "Người chơi";
        String s = raw.trim();
        return s.length() > 20 ? s.substring(0, 20) : s;
    }

    private void requireHost(PlayerSeat actor) {
        if (!actor.token.equals(hostToken)) {
            throw new IllegalStateException("Chỉ chủ phòng mới có quyền thực hiện thao tác này.");
        }
    }

    private void requirePhase(RoomPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Thao tác không hợp lệ ở giai đoạn " + phase + ".");
        }
    }

    private void requireActivePlayer(PlayerSeat actor) {
        if (actor != currentSeat()) {
            throw new IllegalStateException("Chưa tới lượt của bạn!");
        }
        if (actor.domainPlayer == null || actor.domainPlayer.isBankrupt()) {
            throw new IllegalStateException("Bạn đã phá sản.");
        }
    }

    private void requireManageActionAllowed() {
        if (turnState != TurnState.WAIT_ROLL
                && turnState != TurnState.WAIT_END_TURN
                && turnState != TurnState.WAIT_JAIL_CHOICE) {
            throw new IllegalStateException("Hãy hoàn tất quyết định hiện tại trước khi quản lý tài sản hoặc giao dịch.");
        }
    }

    private void addLog(String message) {
        logs.add(0, "[" + TIME_FMT.format(Instant.now()) + "] " + message);
        if (logs.size() > 100) {
            logs.remove(logs.size() - 1);
        }
    }

    private void bumpState() {
        stateVersion++;
    }

    private void touch() {
        lastActivityAt = System.currentTimeMillis();
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
