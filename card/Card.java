package ProjectOop.card;

import ProjectOop.board.Board;
import ProjectOop.player.Player;
import java.util.List;

/**
 * Lớp trừu tượng đại diện cho một lá bài trong Monopoly.
 */
public abstract class Card {
    protected String id;
    protected CardType cardType;
    protected String title;
    protected String description;

    public Card(String id, CardType cardType, String title, String description) {
        this.id = id;
        this.cardType = cardType;
        this.title = title;
        this.description = description;
    }

    /**
     * Kích hoạt hiệu ứng của lá bài.
     * @param player Người chơi bốc phải lá bài
     * @param allPlayers Danh sách tất cả người chơi trong ván đấu
     * @param board Bàn cờ
     */
    public abstract void applyEffect(Player player, List<Player> allPlayers, Board board);

    /**
     * Quá tải phương thức khi chỉ cần tác động lên người chơi hiện tại.
     */
    public void applyEffect(Player player) {
        applyEffect(player, null, null);
    }

    /**
     * Xác định xem người chơi có giữ lại lá bài này hay không (ví dụ thẻ Ra tù miễn phí).
     * Mặc định là false (lá bài sẽ được hoàn trả về đáy xấp bài sau khi dùng).
     */
    public boolean isKeptByPlayer() {
        return false;
    }

    // GETTER
    public String getId() {
        return id;
    }

    public CardType getCardType() {
        return cardType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "[" + cardType.getDisplayName() + "] " + id + " - " + title + ": " + description;
    }
}
