package ProjectOop.card;

import ProjectOop.board.Board;
import ProjectOop.player.Player;
import java.util.List;

/**
 * Lớp thẻ bài cụ thể nhận một CardEffect (Strategy / Lambda) để xử lý logic đa dạng.
 */
public class ActionCard extends Card {
    private final CardEffect effect;
    private final boolean keepByPlayer;

    public ActionCard(String id, CardType cardType, String title, String description, CardEffect effect) {
        this(id, cardType, title, description, effect, false);
    }

    public ActionCard(String id, CardType cardType, String title, String description, CardEffect effect, boolean keepByPlayer) {
        super(id, cardType, title, description);
        this.effect = effect;
        this.keepByPlayer = keepByPlayer;
    }

    @Override
    public void applyEffect(Player player, List<Player> allPlayers, Board board) {
        if (effect != null) {
            effect.execute(player, allPlayers, board);
        }
    }

    @Override
    public boolean isKeptByPlayer() {
        return keepByPlayer;
    }
}
