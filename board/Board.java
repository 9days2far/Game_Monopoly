package ProjectOop.board;

import ProjectOop.card.CardDeck;
import ProjectOop.card.CardType;
import ProjectOop.player.Player;
import ProjectOop.square.*;
import java.util.ArrayList;
import java.util.List;

public class Board {
    // Tạo mảng square
    private Square[] squares;
    private CardDeck chanceDeck;
    private CardDeck communityChestDeck;

    public Board() {
        squares = new Square[40];
        initializeBoard();
    }

    // Hàm khởi tạo các ô
    private void initializeBoard() {
        // Khoi tao bo bai Co hoi va Khi van
        chanceDeck = CardDeck.createChanceDeck();
        chanceDeck.shuffle();
        communityChestDeck = CardDeck.createCommunityChestDeck();
        communityChestDeck.shuffle();

        // Ô GO
        squares[0] = new GoSquare(0, "GO");
        // Ô ĐẤT NÂU
        squares[1] = new PropertySquare(1, "Tuyên Quang", 60, 2, 50, ColorGroup.BROWN);
        // Ô KHÍ VẬN
        squares[2] = new CardSquare(2, "Khí vận", CardType.COMMUNITY_CHEST, communityChestDeck);
        // Ô ĐẤT NÂU
        squares[3] = new PropertySquare(3, "Lạng Sơn", 60, 4, 50, ColorGroup.BROWN);
        // Ô THUẾ
        squares[4] = new TaxSquare(4, "Thuế Thu nhập", 200);
        // Ô TÀU HỎA
        squares[5] = new RailroadSquare(5, "Ga Lào Cai", 200);
        // Ô ĐẤT XANH DA TRỜI
        squares[6] = new PropertySquare(6, "Thái Nguyên", 100, 6, 50, ColorGroup.LIGHT_BLUE);
        // Ô CƠ HỘI
        squares[7] = new CardSquare(7, "Cơ hội", CardType.CHANCE, chanceDeck);
        // Ô ĐẤT XANH DA TRỜI
        squares[8] = new PropertySquare(8, "Bắc Ninh", 100, 6, 50, ColorGroup.LIGHT_BLUE);
        // Ô ĐẤT XANH DA TRỜI
        squares[9] = new PropertySquare(9, "Phú Thọ", 120, 8, 50, ColorGroup.LIGHT_BLUE);
        // Ô THĂM TÙ
        squares[10] = new JailSquare(10, "Nhà Tù", false);
        // Ô ĐẤT HỒNG
        squares[11] = new PropertySquare(11, "Hải Phòng", 140, 10, 100, ColorGroup.PINK);
        // Ô ĐIỆN 
        squares[12] = new UtilitySquare(12, "Điện lực EVN", 150);
        // Ô ĐẤT HỒNG
        squares[13] = new PropertySquare(13, "Hưng Yên", 140, 10, 100, ColorGroup.PINK);
        // Ô ĐẤT HỒNG
        squares[14] = new PropertySquare(14, "Ninh Bình", 160, 12, 100, ColorGroup.PINK);
        // Ô TÀU HỎA
        squares[15] = new RailroadSquare(15, "Ga Nam Định", 200);
        // Ô ĐẤT CAM
        squares[16] = new PropertySquare(16, "Thanh Hóa", 180, 14, 100, ColorGroup.ORANGE);
        // Ô KHÍ VẬN
        squares[17] = new CardSquare(17, "Khí vận", CardType.COMMUNITY_CHEST, communityChestDeck);
        // Ô ĐẤT CAM
        squares[18] = new PropertySquare(18, "Nghệ An", 180, 14, 100, ColorGroup.ORANGE);
        // Ô ĐẤT CAM
        squares[19] = new PropertySquare(19, "Hà Tĩnh", 200, 16, 100, ColorGroup.ORANGE);
        // Ô BÃI ĐỖ XE
        squares[20] = new FreeParkingSquare(20, "Bãi đỗ xe");
        // Ô ĐẤT ĐỎ
        squares[21] = new PropertySquare(21, "Huế", 220, 18, 150, ColorGroup.RED);
        // Ô CƠ HỘI
        squares[22] = new CardSquare(22, "Cơ hội", CardType.CHANCE, chanceDeck);
        // Ô ĐẤT ĐỎ
        squares[23] = new PropertySquare(23, "Đà Nẵng", 220, 18, 150, ColorGroup.RED);
        // Ô ĐẤT ĐỎ
        squares[24] = new PropertySquare(24, "Nha Trang", 240, 20, 150, ColorGroup.RED);
        // Ô TÀU HỎA
        squares[25] = new RailroadSquare(25, "Ga Quy Nhơn", 200);
        // Ô ĐẤT VÀNG
        squares[26] = new PropertySquare(26, "Đà Lạt", 260, 22, 150, ColorGroup.YELLOW);
        // Ô ĐẤT VÀNG
        squares[27] = new PropertySquare(27, "Lâm Đồng", 260, 22, 150, ColorGroup.YELLOW);
        // Ô NƯỚC
        squares[28] = new UtilitySquare(28, "Nước sạch Sài Gòn", 150);
        // Ô ĐẤT VÀNG
        squares[29] = new PropertySquare(29, "Gia Lai", 280, 24, 150, ColorGroup.YELLOW);
        // Ô VÀO TÙ
        squares[30] = new JailSquare(30, "Vào tù", true);
        // Ô ĐẤT XANH LÁ
        squares[31] = new PropertySquare(31, "Bình Dương", 300, 26, 200, ColorGroup.GREEN);
        // Ô ĐẤT XANH LÁ
        squares[32] = new PropertySquare(32, "Cần Thơ", 300, 26, 200, ColorGroup.GREEN);
        // Ô KHÍ VẬN
        squares[33] = new CardSquare(33, "Khí vận", CardType.COMMUNITY_CHEST, communityChestDeck);
        // Ô ĐẤT XANH LÁ
        squares[34] = new PropertySquare(34, "Vũng Tàu", 320, 28, 200, ColorGroup.GREEN);
        // Ô TÀU HỎA
        squares[35] = new RailroadSquare(35, "Ga Sài Gòn", 200);
        // Ô CƠ HỘI
        squares[36] = new CardSquare(36, "Cơ hội", CardType.CHANCE, chanceDeck);
        // Ô ĐẤT XANH DƯƠNG
        squares[37] = new PropertySquare(37, "TP.Hồ Chí Minh", 350, 35, 200, ColorGroup.DARK_BLUE);
        // Ô THUẾ
        squares[38] = new TaxSquare(38, "Thuế cao cấp", 100);
        // Ô ĐẤT XANH DƯƠNG
        squares[39] = new PropertySquare(39, "Hà Nội", 400, 50, 200, ColorGroup.DARK_BLUE);
    }

    public CardDeck getChanceDeck() {
        return chanceDeck;
    }

    public CardDeck getCommunityChestDeck() {
        return communityChestDeck;
    }

    // Các hàm tìm kiếm và truy xuất thông tin ô
    public Square getSquare(int position) {
        if (position < 0 || position >= squares.length) {
            return null;
        }
        return squares[position];
    }

    public Square[] getSquares() {
        return squares;
    }

    public List<PropertySquare> getPropertiesByColorGroup(ColorGroup colorGroup) {
        List<PropertySquare> result = new ArrayList<>();
        if (colorGroup == null) return result;
        for (Square sq : squares) {
            if (sq instanceof PropertySquare) {
                PropertySquare prop = (PropertySquare) sq;
                if (prop.getColorGroup() == colorGroup) {
                    result.add(prop);
                }
            }
        }
        return result;
    }

    public boolean hasColorGroup(Player player, ColorGroup colorGroup) {
        if (player == null || colorGroup == null) return false;
        List<PropertySquare> groupProps = getPropertiesByColorGroup(colorGroup);
        if (groupProps.isEmpty()) return false;
        for (PropertySquare prop : groupProps) {
            if (prop.getOwner() != player) {
                return false;
            }
        }
        return true;
    }
}
