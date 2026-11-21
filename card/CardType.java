package ProjectOop.card;

/**
 * Phân loại loại thẻ bài trong trò chơi Monopoly:
 * - CHANCE: Thẻ Cơ Hội
 * - COMMUNITY_CHEST: Thẻ Khí Vận (Quỹ cộng đồng)
 */
public enum CardType {
    CHANCE("Cơ hội"),
    COMMUNITY_CHEST("Khí vận");

    private final String displayName;

    CardType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
