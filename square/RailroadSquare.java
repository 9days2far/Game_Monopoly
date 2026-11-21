package ProjectOop.square;

import ProjectOop.player.Player;

public class RailroadSquare extends AssetSquare {
    public static final int RAILROAD_PRICE = 200; // Giá mua 1 ô nhà ga
    private static final int[] RENT_BY_COUNT = {0, 25, 50, 100, 200}; // Bảng giá thuê theo số lượng nhà ga đang sở hữu

    public RailroadSquare(int position, String name, int price) {
        super(position, name, price);
    }

    public RailroadSquare(int position, String name) {
        this(position, name, RAILROAD_PRICE);
    }

    @Override
    public int calculateRent(Player visitor) {
        // Đang thế chấp thì không thu tiền thuê
        if (isMortgaged()) {
            return 0;
        }

        int railroadCount = countOwnedRailroads();

        // Dùng bảng tra cứu để tính tiền thuê
        int index = Math.min(railroadCount, RENT_BY_COUNT.length - 1);
        return RENT_BY_COUNT[index];
    }

    private int countOwnedRailroads() {
        Player owner = getOwner();
        if (owner == null) return 0;

        int count = 0;
        for (AssetSquare asset : owner.getOwnedAssets()) {
            if (asset instanceof RailroadSquare) {
                count++;
            }
        }
        return count;
    }
}
