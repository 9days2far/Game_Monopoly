package ProjectOop.card;

import ProjectOop.board.Board;
import ProjectOop.player.Player;
import ProjectOop.square.AssetSquare;
import ProjectOop.square.PropertySquare;
import ProjectOop.square.RailroadSquare;
import ProjectOop.square.Square;
import ProjectOop.square.UtilitySquare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Quản lý bộ bài (xấp bài) cho Cơ Hội hoặc Khí Vận.
 * Hỗ trợ rút bài (ở đỉnh), hoàn trả thẻ (xuống đáy), xáo bài và khôi phục bộ bài.
 */
public class CardDeck {
    private final CardType type;
    private final List<Card> initialCards;
    private final LinkedList<Card> activeCards;

    public CardDeck(CardType type) {
        this.type = type;
        this.initialCards = new ArrayList<>();
        this.activeCards = new LinkedList<>();
    }

    /**
     * Thêm một lá bài vào danh sách bài gốc của bộ bài.
     */
    public void addCard(Card card) {
        if (card != null) {
            initialCards.add(card);
            activeCards.add(card);
        }
    }

    /**
     * Rút một lá bài ở trên cùng (đỉnh) của bộ bài.
     * @return Lá bài được rút, hoặc null nếu bộ bài rỗng.
     */
    public Card drawCard() {
        if (activeCards.isEmpty()) {
            return null;
        }
        return activeCards.pollFirst();
    }

    /**
     * Hoàn trả một lá bài về dưới đáy của bộ bài (áp dụng sau khi dùng xong thẻ bài).
     */
    public void returnCard(Card card) {
        if (card != null && !activeCards.contains(card)) {
            activeCards.addLast(card);
        }
    }

    /**
     * Xáo trộn ngẫu nhiên các lá bài đang có trong bộ bài.
     */
    public void shuffle() {
        Collections.shuffle(activeCards);
    }

    /**
     * Khôi phục toàn bộ các lá bài về trạng thái ban đầu và xáo bài.
     */
    public void reset() {
        activeCards.clear();
        activeCards.addAll(initialCards);
        shuffle();
    }

    public CardType getType() {
        return type;
    }

    public int getRemainingCount() {
        return activeCards.size();
    }

    public int getTotalInitialCount() {
        return initialCards.size();
    }

    public List<Card> getInitialCards() {
        return Collections.unmodifiableList(initialCards);
    }

    // ==========================================
    // CÁC HÀM TIỆN ÍCH NGHIỆP VỤ (HELPER METHODS)
    // ==========================================

    /**
     * Tìm vị trí ô gần nhất phía trước mặt người chơi (theo chiều tiến kim đồng hồ).
     */
    public static int findNearestSquare(int currentPos, int[] candidatePositions) {
        int minDistance = 40;
        int target = candidatePositions[0];
        for (int pos : candidatePositions) {
            int distance = (pos - currentPos + 40) % 40;
            if (distance == 0) distance = 40; // Nếu đang đứng đúng ô đó, đi tiếp 1 vòng
            if (distance < minDistance) {
                minDistance = distance;
                target = pos;
            }
        }
        return target;
    }

    /**
     * Tính chi phí sửa chữa tài sản dựa trên số nhà và khách sạn của người chơi.
     */
    public static int calculateRepairCost(Player player, int houseCost, int hotelCost) {
        if (player == null || player.getOwnedAssets() == null) return 0;
        int houses = 0;
        int hotels = 0;

        for (AssetSquare asset : player.getOwnedAssets()) {
            if (asset instanceof PropertySquare) {
                PropertySquare prop = (PropertySquare) asset;
                int level = prop.getHouseLevel();
                if (level == 5) {
                    hotels++;
                } else if (level > 0 && level <= 4) {
                    houses += level;
                }
            }
        }
        return (houses * houseCost) + (hotels * hotelCost);
    }

    // ==========================================
    // FACTORY METHODS KHỞI TẠO BỘ BÀI CHUẨN GỐC
    // ==========================================

    /**
     * Khởi tạo bộ bài Cơ Hội (Chance) 16 lá chuẩn Monopoly gốc.
     */
    public static CardDeck createChanceDeck() {
        CardDeck deck = new CardDeck(CardType.CHANCE);

        // CH_01: Tiến tới ô GO (nhận $200)
        deck.addCard(new ActionCard("CH_01", CardType.CHANCE, "Tiến tới ô GO",
                "Tiến thẳng tới ô Khởi hành (GO). Nhận ngay $200.",
                (p, all, b) -> p.moveTo(0, true)));

        // CH_02: Tiến tới Nha Trang (ô 24)
        deck.addCard(new ActionCard("CH_02", CardType.CHANCE, "Tiến tới Nha Trang",
                "Tiến thẳng tới Nha Trang (ô 24). Nếu đi qua GO, nhận $200.",
                (p, all, b) -> p.moveTo(24, true)));

        // CH_03: Tiến tới Hải Phòng (ô 11)
        deck.addCard(new ActionCard("CH_03", CardType.CHANCE, "Tiến tới Hải Phòng",
                "Tiến thẳng tới Hải Phòng (ô 11). Nếu đi qua GO, nhận $200.",
                (p, all, b) -> p.moveTo(11, true)));

        // CH_04: Tiến tới Tiện ích gần nhất (ô 12 hoặc 28)
        deck.addCard(new ActionCard("CH_04", CardType.CHANCE, "Tiến tới Tiện ích gần nhất",
                "Đi tới Công ty Điện hoặc Nước gần nhất. Nếu đã có chủ, tung xúc xắc và trả gấp 10 lần số điểm vừa tung.",
                (p, all, b) -> {
                    int[] utilities = {12, 28};
                    int target = findNearestSquare(p.getPosition(), utilities);
                    p.moveTo(target, true);
                    if (b != null) {
                        Square sq = b.getSquare(target);
                        if (sq instanceof UtilitySquare) {
                            UtilitySquare util = (UtilitySquare) sq;
                            Player owner = util.getOwner();
                            if (owner != null && owner != p && !util.isMortgaged()) {
                                int roll = p.getLastDiceRoll() > 0 ? p.getLastDiceRoll() : 7;
                                int rent = roll * 10;
                                p.deductMoney(rent);
                                owner.addMoney(rent);
                            }
                        }
                    }
                }));

        // CH_05: Tiến tới Ga tàu gần nhất (lá 1)
        deck.addCard(new ActionCard("CH_05", CardType.CHANCE, "Tiến tới Ga tàu gần nhất (1)",
                "Đi tới Ga tàu gần nhất. Nếu đã có chủ, trả gấp đôi tiền thuê thông thường.",
                (p, all, b) -> {
                    int[] railroads = {5, 15, 25, 35};
                    int target = findNearestSquare(p.getPosition(), railroads);
                    p.moveTo(target, true);
                    if (b != null) {
                        Square sq = b.getSquare(target);
                        if (sq instanceof RailroadSquare) {
                            RailroadSquare rr = (RailroadSquare) sq;
                            Player owner = rr.getOwner();
                            if (owner != null && owner != p && !rr.isMortgaged()) {
                                int rent = rr.calculateRent(p) * 2;
                                p.deductMoney(rent);
                                owner.addMoney(rent);
                            }
                        }
                    }
                }));

        // CH_06: Tiến tới Ga tàu gần nhất (lá 2)
        deck.addCard(new ActionCard("CH_06", CardType.CHANCE, "Tiến tới Ga tàu gần nhất (2)",
                "Đi tới Ga tàu gần nhất. Nếu đã có chủ, trả gấp đôi tiền thuê thông thường.",
                (p, all, b) -> {
                    int[] railroads = {5, 15, 25, 35};
                    int target = findNearestSquare(p.getPosition(), railroads);
                    p.moveTo(target, true);
                    if (b != null) {
                        Square sq = b.getSquare(target);
                        if (sq instanceof RailroadSquare) {
                            RailroadSquare rr = (RailroadSquare) sq;
                            Player owner = rr.getOwner();
                            if (owner != null && owner != p && !rr.isMortgaged()) {
                                int rent = rr.calculateRent(p) * 2;
                                p.deductMoney(rent);
                                owner.addMoney(rent);
                            }
                        }
                    }
                }));

        // CH_07: Đi đến Ga Lào Cai (ô 5)
        deck.addCard(new ActionCard("CH_07", CardType.CHANCE, "Chuyến đi đến Ga Lào Cai",
                "Đi thẳng đến Ga Lào Cai (ô 5). Nếu đi qua GO, nhận $200.",
                (p, all, b) -> p.moveTo(5, true)));

        // CH_08: Tiến tới Hà Nội (ô 39)
        deck.addCard(new ActionCard("CH_08", CardType.CHANCE, "Tiến tới Hà Nội",
                "Tiến thẳng tới ô đắt giá nhất: Hà Nội (ô 39).",
                (p, all, b) -> p.moveTo(39, false)));

        // CH_09: Đi lùi 3 ô
        deck.addCard(new ActionCard("CH_09", CardType.CHANCE, "Đi lùi 3 ô",
                "Lùi lại 3 ô và kích hoạt hiệu ứng của ô dừng chân.",
                (p, all, b) -> {
                    p.move(-3);
                    if (b != null) {
                        Square sq = b.getSquare(p.getPosition());
                        if (sq != null) {
                            sq.applyEffect(p);
                        }
                    }
                }));

        // CH_10: Vào tù ngay lập tức
        deck.addCard(new ActionCard("CH_10", CardType.CHANCE, "Vào tù ngay lập tức",
                "Vào tù ngay lập tức. Không đi qua ô GO, không nhận $200.",
                (p, all, b) -> p.sendToJail()));

        // CH_11: Thẻ ra tù miễn phí (Giữ lại)
        deck.addCard(new ActionCard("CH_11", CardType.CHANCE, "Thẻ ra tù miễn phí",
                "Thẻ này được giữ lại cho đến khi cần dùng hoặc bán lại.",
                (p, all, b) -> p.addOutOfJailTicket(), true));

        // CH_12: Sửa chữa bất động sản ($25/nhà, $100/khách sạn)
        deck.addCard(new ActionCard("CH_12", CardType.CHANCE, "Sửa chữa bất động sản",
                "Bảo trì toàn bộ tài sản: Trả $25 cho mỗi căn nhà, $100 cho mỗi khách sạn đang sở hữu.",
                (p, all, b) -> {
                    int cost = calculateRepairCost(p, 25, 100);
                    p.deductMoney(cost);
                }));

        // CH_13: Phạt vi phạm giao thông ($15)
        deck.addCard(new ActionCard("CH_13", CardType.CHANCE, "Phạt vi phạm giao thông",
                "Chạy quá tốc độ: Nộp phạt $15 cho Ngân hàng.",
                (p, all, b) -> p.deductMoney(15)));

        // CH_14: Ngân hàng chia cổ tức ($50)
        deck.addCard(new ActionCard("CH_14", CardType.CHANCE, "Ngân hàng chia cổ tức",
                "Ngân hàng chia cổ tức: Nhận $50 từ Ngân hàng.",
                (p, all, b) -> p.addMoney(50)));

        // CH_15: Đáo hạn khoản vay ($150)
        deck.addCard(new ActionCard("CH_15", CardType.CHANCE, "Đáo hạn khoản vay",
                "Khoản vay xây dựng của bạn đáo hạn: Nhận $150.",
                (p, all, b) -> p.addMoney(150)));

        // CH_16: Chủ tịch HĐQT (trả mỗi người $50)
        deck.addCard(new ActionCard("CH_16", CardType.CHANCE, "Bầu làm Chủ tịch HĐQT",
                "Bạn được bầu làm Chủ tịch HĐQT: Trả cho mỗi người chơi khác $50.",
                (p, all, b) -> {
                    if (all != null) {
                        for (Player other : all) {
                            if (other != p && !other.isBankrupt()) {
                                if (p.deductMoney(50)) {
                                    other.addMoney(50);
                                }
                            }
                        }
                    } else {
                        p.deductMoney(50);
                    }
                }));

        return deck;
    }

    /**
     * Khởi tạo bộ bài Khí Vận (Community Chest) 16 lá chuẩn Monopoly gốc.
     */
    public static CardDeck createCommunityChestDeck() {
        CardDeck deck = new CardDeck(CardType.COMMUNITY_CHEST);

        // CC_01: Tiến tới ô GO (nhận $200)
        deck.addCard(new ActionCard("CC_01", CardType.COMMUNITY_CHEST, "Tiến tới ô GO",
                "Tiến thẳng tới ô Khởi hành (GO). Nhận ngay $200.",
                (p, all, b) -> p.moveTo(0, true)));

        // CC_02: Ngân hàng chuyển nhầm tiền ($200)
        deck.addCard(new ActionCard("CC_02", CardType.COMMUNITY_CHEST, "Ngân hàng chuyển nhầm tiền",
                "Ngân hàng hạch toán nhầm có lợi cho bạn: Nhận $200.",
                (p, all, b) -> p.addMoney(200)));

        // CC_03: Viện phí bác sĩ ($50)
        deck.addCard(new ActionCard("CC_03", CardType.COMMUNITY_CHEST, "Viện phí bác sĩ",
                "Chi phí khám bệnh bác sĩ: Trả $50.",
                (p, all, b) -> p.deductMoney(50)));

        // CC_04: Bán cổ phiếu ($50)
        deck.addCard(new ActionCard("CC_04", CardType.COMMUNITY_CHEST, "Bán cổ phiếu",
                "Thu lợi nhuận từ việc bán cổ phiếu: Nhận $50.",
                (p, all, b) -> p.addMoney(50)));

        // CC_05: Thẻ ra tù miễn phí (Giữ lại)
        deck.addCard(new ActionCard("CC_05", CardType.COMMUNITY_CHEST, "Thẻ ra tù miễn phí",
                "Thẻ này được giữ lại cho đến khi cần dùng hoặc bán lại.",
                (p, all, b) -> p.addOutOfJailTicket(), true));

        // CC_06: Vào tù ngay lập tức
        deck.addCard(new ActionCard("CC_06", CardType.COMMUNITY_CHEST, "Vào tù ngay lập tức",
                "Đi thẳng vào tù. Không đi qua ô GO, không nhận $200.",
                (p, all, b) -> p.sendToJail()));

        // CC_07: Quỹ tiết kiệm nghỉ lễ ($100)
        deck.addCard(new ActionCard("CC_07", CardType.COMMUNITY_CHEST, "Quỹ tiết kiệm nghỉ lễ",
                "Quỹ tiết kiệm nghỉ lễ của bạn đáo hạn: Nhận $100.",
                (p, all, b) -> p.addMoney(100)));

        // CC_08: Hoàn thuế thu nhập ($20)
        deck.addCard(new ActionCard("CC_08", CardType.COMMUNITY_CHEST, "Hoàn thuế thu nhập",
                "Được hoàn trả thuế thu nhập cá nhân: Nhận $20.",
                (p, all, b) -> p.addMoney(20)));

        // CC_09: Chúc mừng sinh nhật (thu mỗi người $10)
        deck.addCard(new ActionCard("CC_09", CardType.COMMUNITY_CHEST, "Chúc mừng sinh nhật",
                "Hôm nay là sinh nhật bạn: Thu của mỗi người chơi khác $10.",
                (p, all, b) -> {
                    if (all != null) {
                        for (Player other : all) {
                            if (other != p && !other.isBankrupt()) {
                                if (other.deductMoney(10)) {
                                    p.addMoney(10);
                                }
                            }
                        }
                    } else {
                        p.addMoney(10);
                    }
                }));

        // CC_10: Bảo hiểm nhân thọ đáo hạn ($100)
        deck.addCard(new ActionCard("CC_10", CardType.COMMUNITY_CHEST, "Bảo hiểm nhân thọ đáo hạn",
                "Hợp đồng bảo hiểm nhân thọ đáo hạn: Nhận $100.",
                (p, all, b) -> p.addMoney(100)));

        // CC_11: Nộp viện phí bệnh viện ($100)
        deck.addCard(new ActionCard("CC_11", CardType.COMMUNITY_CHEST, "Nộp viện phí bệnh viện",
                "Viện phí bệnh viện: Trả $100 cho Ngân hàng.",
                (p, all, b) -> p.deductMoney(100)));

        // CC_12: Nộp học phí ($50)
        deck.addCard(new ActionCard("CC_12", CardType.COMMUNITY_CHEST, "Nộp học phí",
                "Thanh toán học phí: Trả $50 cho Ngân hàng.",
                (p, all, b) -> p.deductMoney(50)));

        // CC_13: Thù lao tư vấn ($25)
        deck.addCard(new ActionCard("CC_13", CardType.COMMUNITY_CHEST, "Thù lao tư vấn",
                "Nhận thù lao từ dịch vụ tư vấn: Nhận $25.",
                (p, all, b) -> p.addMoney(25)));

        // CC_14: Thuế bảo trì đường phố ($40/nhà, $115/khách sạn)
        deck.addCard(new ActionCard("CC_14", CardType.COMMUNITY_CHEST, "Thuế bảo trì đường phố",
                "Đánh thuế tu sửa đường sá: Trả $40 cho mỗi căn nhà và $115 cho mỗi khách sạn.",
                (p, all, b) -> {
                    int cost = calculateRepairCost(p, 40, 115);
                    p.deductMoney(cost);
                }));

        // CC_15: Giải nhì sắc đẹp ($10)
        deck.addCard(new ActionCard("CC_15", CardType.COMMUNITY_CHEST, "Giải nhì sắc đẹp",
                "Đạt giải nhì trong cuộc thi sắc đẹp: Nhận $10.",
                (p, all, b) -> p.addMoney(10)));

        // CC_16: Tiền thừa kế ($100)
        deck.addCard(new ActionCard("CC_16", CardType.COMMUNITY_CHEST, "Tiền thừa kế",
                "Bạn được nhận tiền thừa kế: Nhận $100.",
                (p, all, b) -> p.addMoney(100)));

        return deck;
    }
}
