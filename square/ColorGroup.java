package ProjectOop.square;

public enum ColorGroup {
    BROWN("Nâu", 2),
    LIGHT_BLUE("Xanh da trời", 3),
    PINK("Hồng", 3),
    ORANGE("Cam", 3),
    RED("Đỏ", 3),
    YELLOW("Vàng", 3),
    GREEN("Xanh lá", 3),
    DARK_BLUE("Xanh dương", 2);

    private final String displayName;
    private final int totalProperties;

    ColorGroup(String displayName, int totalProperties) {
        this.displayName = displayName;
        this.totalProperties = totalProperties;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTotalProperties() {
        return totalProperties;
    }

    public int getPropertyCount() {
        return totalProperties;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
