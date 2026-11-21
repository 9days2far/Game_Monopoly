package ProjectOop.square;

import ProjectOop.board.Board;
import ProjectOop.card.Card;
import ProjectOop.card.CardDeck;
import ProjectOop.card.CardType;
import ProjectOop.player.Player;

import java.util.List;

/**
 * Ô rút thẻ bài (dùng chung cho cả ô Cơ Hội và ô Khí Vận).
 * Quản lý một tham chiếu đến CardDeck tương ứng.
 *
 * <h3>QUY ƯỚC DỰ ÁN</h3>
 * Class này KHÔNG in ra console (System.out). Mọi việc hiển thị thông tin
 * là trách nhiệm của GameController.
 *
 * <h3>HƯỚNG DẪN TƯƠNG TÁC CHO GAMECONTROLLER</h3>
 * Khi người chơi dừng chân tại ô CardSquare, GameController cần:
 * <ol>
 *   <li>Gọi {@code applyEffect(player, allPlayers, board)} để rút bài và kích hoạt hiệu ứng.</li>
 *   <li>Kiểm tra kết quả trả về (Card):
 *     <ul>
 *       <li>{@code null} → không có bộ bài hoặc bộ bài đã hết, nên thông báo cho người chơi.</li>
 *       <li>Có Card → dùng {@code card.getTitle()}, {@code card.getDescription()},
 *           {@code card.getId()} để in thông tin lá bài ra console.</li>
 *     </ul>
 *   </li>
 *   <li>Kiểm tra {@code card.isKeptByPlayer()}:
 *     <ul>
 *       <li>{@code true} → người chơi giữ lại lá bài (VD: thẻ ra tù miễn phí),
 *           nên thông báo "{player} đã giữ lại lá bài: {card.getTitle()}".</li>
 *       <li>{@code false} → lá bài đã tự động được trả về đáy xấp bài.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>VÍ DỤ SỬ DỤNG (tham khảo TestGame)</h3>
 * <pre>{@code
 * if (currentSquare instanceof CardSquare) {
 *     CardSquare cardSquare = (CardSquare) currentSquare;
 *     Card drawnCard = cardSquare.applyEffect(player, allPlayers, board);
 *     if (drawnCard != null) {
 *         System.out.println(player.getName() + " đã rút ra lá bài: " + drawnCard.getTitle());
 *         System.out.println("Hiệu ứng: " + drawnCard.getDescription());
 *         if (drawnCard.isKeptByPlayer()) {
 *             System.out.println(player.getName() + " đã giữ lại lá bài: " + drawnCard.getTitle());
 *         }
 *     } else {
 *         System.out.println("Không có lá bài nào để rút!");
 *     }
 * }
 * }</pre>
 */
public class CardSquare extends Square {
    private CardDeck deck;
    private CardType cardType;
    private Card lastDrawnCard;

    public CardSquare(int position, String name, CardType cardType, CardDeck deck) {
        super(position, name);
        this.cardType = cardType;
        this.deck = deck;
    }

    public CardSquare(int position, String name, CardDeck deck) {
        this(position, name, deck != null ? deck.getType() : null, deck);
    }

    public CardSquare(int position, String name) {
        this(position, name, null, null);
    }

    /**
     * Kích hoạt hiệu ứng cơ bản khi người chơi dừng chân tại ô này.
     * Gọi tới {@link #applyEffect(Player, List, Board)} với allPlayers và board là null.
     */
    @Override
    public void applyEffect(Player p) {
        applyEffect(p, null, null);
    }

    /**
     * Kích hoạt hiệu ứng đầy đủ khi có ngữ cảnh danh sách người chơi và bàn cờ.
     *
     * <p>Luồng xử lý bên trong:</p>
     * <ol>
     *   <li>Rút lá bài trên cùng từ bộ bài ({@code deck.drawCard()}).</li>
     *   <li>Lưu lá bài vào {@code lastDrawnCard} để truy vấn sau nếu cần.</li>
     *   <li>Kích hoạt hiệu ứng lá bài ({@code card.applyEffect(p, allPlayers, board)})
     *       — hiệu ứng có thể di chuyển, cộng/trừ tiền, đưa vào tù, v.v.</li>
     *   <li>Nếu lá bài KHÔNG được giữ lại ({@code !card.isKeptByPlayer()}),
     *       tự động trả về đáy xấp bài ({@code deck.returnCard(card)}).</li>
     *   <li>Nếu lá bài ĐƯỢC giữ lại (VD: thẻ ra tù miễn phí), lá bài rời khỏi bộ bài
     *       và thuộc về người chơi cho đến khi sử dụng.</li>
     * </ol>
     *
     * @param p          Người chơi dừng chân tại ô này
     * @param allPlayers Danh sách tất cả người chơi (cần cho các thẻ tương tác nhiều người)
     * @param board      Bàn cờ (cần cho các thẻ di chuyển đến ô cụ thể)
     * @return Lá bài vừa rút được, hoặc {@code null} nếu không có bộ bài / bộ bài rỗng.
     *         GameController dùng kết quả này để in thông tin ra console.
     */
    public Card applyEffect(Player p, List<Player> allPlayers, Board board) {
        if (deck == null) {
            return null;
        }

        Card card = deck.drawCard();
        if (card != null) {
            this.lastDrawnCard = card;

            // Kích hoạt hiệu ứng thẻ bài (di chuyển, cộng/trừ tiền, vào tù, v.v.)
            card.applyEffect(p, allPlayers, board);

            // Thẻ bình thường: trả về đáy xấp bài sau khi dùng
            // Thẻ giữ lại (VD: thẻ ra tù miễn phí): người chơi giữ, không trả lại
            if (!card.isKeptByPlayer()) {
                deck.returnCard(card);
            }
            return card;
        }

        return null;
    }

    // ==========================================
    // GETTER VÀ SETTER
    // ==========================================

    public CardDeck getDeck() {
        return deck;
    }

    public void setDeck(CardDeck deck) {
        this.deck = deck;
        if (deck != null && this.cardType == null) {
            this.cardType = deck.getType();
        }
    }

    public CardType getCardType() {
        return cardType;
    }

    public void setCardType(CardType cardType) {
        this.cardType = cardType;
    }

    /**
     * Lấy lá bài cuối cùng được rút tại ô này.
     * Hữu ích khi GameController cần truy vấn lại thông tin lá bài sau khi applyEffect.
     */
    public Card getLastDrawnCard() {
        return lastDrawnCard;
    }
}
