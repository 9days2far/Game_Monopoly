package ProjectOop.square;

import ProjectOop.card.CardDeck;
import ProjectOop.card.CardType;

/**
 * Lớp ChanceSquare kế thừa từ CardSquare nhằm đảm bảo tính tương thích ngược.
 * Khuyến khích sử dụng CardSquare trực tiếp kết hợp với CardDeck.
 */
public class ChanceSquare extends CardSquare {

    public ChanceSquare(int position, String name, CardDeck deck) {
        super(position, name, CardType.CHANCE, deck);
    }

    public ChanceSquare(int position, String name) {
        super(position, name, CardType.CHANCE, null);
    }
}
