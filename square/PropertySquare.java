package ProjectOop.square;

import ProjectOop.player.*;
import java.util.List;

public class PropertySquare extends AssetSquare {
    private static final int[] RENT_MULTIPLIERS = {1, 5, 15, 36, 44, 52}; // bảng giá nhân giá theo cấp (nên cân bằng lại)

    private int baseRent; // Tiền thuê gốc (chưa xây nhà)
    private int houseLevel; // 0: đất trống, 1-4: số nhà, 5: khách sạn
    private int houseCost; // Giá nâng cấp mỗi cấp nhà/khách sạn
    private ColorGroup colorGroup; // Nhóm màu của ô đất

    // 1. Constructor
    public PropertySquare(int position, String name, int price, int baseRent, int houseCost, ColorGroup colorGroup) {
        super(position, name, price);
        this.baseRent = baseRent;
        this.houseCost = houseCost;
        this.houseLevel = 0;
        this.colorGroup = colorGroup;
    }

    public PropertySquare(int position, String name, int price, int baseRent, int houseCost) {
        this(position, name, price, baseRent, houseCost, null);
    }

    // 2. Tính tiền thuê nhà
    @Override
    public int calculateRent(Player visitor) {
        // Đất đang thế chấp không được thu tiền thuê
        if (isMortgaged()) {
            return 0;
        }

        if (houseLevel < 0 || houseLevel >= RENT_MULTIPLIERS.length) {
            return baseRent;
        } // Tránh lỗi truy cập ngoài mảng (nếu có)

        // Đất trống (chưa xây nhà): nhân đôi tiền thuê nếu chủ sở hữu có trọn bộ màu
        if (houseLevel == 0) {
            Player owner = getOwner();
            if (owner != null && colorGroup != null && owner.hasFullColorGroup(colorGroup)) {
                return baseRent * 2;
            }
            return baseRent;
        }

        return baseRent * RENT_MULTIPLIERS[houseLevel];
    }

    // 3. Các hàm xử lý cho lựa chọn của người chơi
    // Logic cho tương tác cài sau khi có UI

    // Kiểm tra có thể thế chấp
    @Override
    public boolean canMortgage() {
        if (!super.canMortgage()) {
            return false;
        }

        // Không được thế chấp nếu ô này đang có nhà
        if (houseLevel > 0) {
            return false;
        }

        // Không được thế chấp nếu các ô khác cùng bộ màu đang có nhà
        Player owner = getOwner();
        if (owner != null && colorGroup != null) {
            List<PropertySquare> groupProperties = owner.getOwnedPropertiesByColorGroup(colorGroup);
            for (PropertySquare prop : groupProperties) {
                if (prop.getHouseLevel() > 0) {
                    return false;
                }
            }
        }

        return true;
    }

    // Kiểm tra có thể nâng cấp
    public boolean canUpgrade(Player p) {
        // Phải là chủ sở hữu, chưa đạt cấp tối đa (5: khách sạn) và có đủ tiền
        if (getOwner() != p || houseLevel >= 5 || p.getBalance() < houseCost) {
            return false;
        }

        // Không thể nâng cấp nếu bản thân ô này đang bị thế chấp
        if (isMortgaged()) {
            return false;
        }

        // Phải sở hữu đủ các ô cùng ColorGroup
        if (colorGroup == null || !p.hasFullColorGroup(colorGroup)) {
            return false;
        }

        List<PropertySquare> groupProperties = p.getOwnedPropertiesByColorGroup(colorGroup);
        for (PropertySquare prop : groupProperties) {
            // Không được nâng cấp nếu có ô cùng bộ màu đang bị thế chấp
            if (prop.isMortgaged()) {
                return false;
            }
            // Quy tắc xây đều tay: không được nâng cấp nếu có ô khác cùng bộ màu có số nhà thấp hơn ô này
            if (prop.getHouseLevel() < this.houseLevel) {
                return false;
            }
        }

        return true;
    }

    // Thực hiện nâng cấp nhà/khách sạn (tạm thời nâng 1 cấp 1 lần)
    public boolean upgrade(Player p) {
        if (canUpgrade(p)) {
            p.deductMoney(houseCost);
            houseLevel++;
            return true;
        }
        return false;
    }

    // 4. GETTER và SETTER
    public int getBaseRent() { return baseRent; }
    public int getHouseLevel() { return houseLevel; }
    public void setHouseLevel(int houseLevel) { this.houseLevel = houseLevel; }
    public int getHouseCost() { return houseCost; }
    public ColorGroup getColorGroup() { return colorGroup; }
    public void setColorGroup(ColorGroup colorGroup) { this.colorGroup = colorGroup; }

}
