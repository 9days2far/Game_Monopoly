package ProjectOop.square;

import ProjectOop.player.*;

public class UtilitySquare extends AssetSquare {
    public static final int UTILITY_PRICE = 150; // Giá mua của công ty tiện ích
    private static final int[] RENT_MULTIPLIERS_BY_COUNT = {0, 4, 10}; // Bảng giá thuê nhân theo số tiện ích sở hữu

    public UtilitySquare(int position, String name, int price) {
        super(position, name, price);
    }

    public UtilitySquare(int position, String name) {
        this(position, name, UTILITY_PRICE);
    }

    // 1. Hàm tính tiền thuê dựa trên số lượng công ty tiện ích mà chủ sở hữu sở hữu
    @Override
    public int calculateRent(Player visitor) {
        // Đang thế chấp thì không thu tiền thuê
        if (isMortgaged()) {
            return 0;
        }

        int utilityCount = countOwnedUtilities();
        int diceRoll = visitor.getLastDiceRoll();

        // Dùng bảng tra cứu để tính tiền thuê nhà
        int index = Math.min(utilityCount, RENT_MULTIPLIERS_BY_COUNT.length - 1); // Chặn chỉ số an toàn, tối đa lấy phần tử cuối cùng của mảng
        int multiplier = RENT_MULTIPLIERS_BY_COUNT[index];
        return diceRoll * multiplier;
    }

    // 2. Hàm phụ trợ nội bộ để đếm số tiện ích chủ sở hữu đang có
    private int countOwnedUtilities() {
        Player owner = getOwner();
        if (owner == null) return 0;

        int count = 0;
        for (AssetSquare asset : owner.getOwnedAssets()) {
            if (asset instanceof UtilitySquare) {
                count++;
            }
        }
        return count;
    }

}
