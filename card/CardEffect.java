package ProjectOop.card;

import ProjectOop.board.Board;
import ProjectOop.player.Player;
import java.util.List;

/**
 * Functional Interface định nghĩa hiệu ứng thực thi của một lá bài lên người chơi,
 * các người chơi khác và bàn cờ.
 */
@FunctionalInterface
public interface CardEffect {
    void execute(Player player, List<Player> allPlayers, Board board);
}
