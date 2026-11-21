package ProjectOop.square;

import ProjectOop.player.*;

public abstract class AssetSquare extends Square {
    private final int price; // Giá mua của ô tài sản
    private Player owner; // Chủ sở hữu của ô tài sản
    private boolean isMortgaged; // Trạng thái thế chấp ô tài sản

    public AssetSquare(int position, String name, int price) {
        super(position, name);
        this.price = price;
        this.owner = null;
        this.isMortgaged = false;
    }

    // Hàm abstract tính tiền thuê nhà
    public abstract int calculateRent(Player visitor);

    @Override
    public void applyEffect(Player p) {
        if (owner != null && p != owner) {
            int rent = calculateRent(p); // Gọi hàm abstract ở đây

            if (rent > 0) {
                if (p.deductMoney(rent)) {
                    // Thanh toán đủ: chuyển tiền thuê cho chủ sở hữu
                    owner.addMoney(rent);
                } else {
                    // Không đủ tiền mặt: chuyển tạm toàn bộ số dư còn lại cho chủ sở hữu.
                    // GameController.checkBankruptcy() sẽ xử lý phần còn lại:
                    //   - Huy động tài sản (thế chấp) trước khi tuyên bố phá sản
                    //   - Chuyển tài sản cho chủ nợ nếu không thể thanh toán
                    //   - Gọi declareBankruptcy() và loại người chơi khỏi game
                    // → KHÔNG setBankrupt tại đây, để GameController kiểm soát hoàn toàn
                    owner.addMoney(p.getBalance());
                    p.deductMoney(p.getBalance());
                }
            }
        }
    }

    // Kiểm tra có thể mua ô này không
    public boolean canBuy(Player p) {
        return owner == null && p.getBalance() >= price;
    }

    public boolean buyAsset(Player p) {
        if (canBuy(p)) {
            p.deductMoney(price);
            this.owner = p;
            p.addAsset(this);
            return true;
        }
        return false;
    }

    // Cơ chế Thế chấp (Mortgage)
    // Giá trị thế chấp = 50% giá mua đất
    public int getMortgageValue() {
        return price / 2;
    }

    // Tiền chuộc = tiền thế chấp + 10% lãi
    public int getUnmortgageCost() {
        return (int) Math.round(getMortgageValue() * 1.1);
    }

    // Kiểm tra có thể thế chấp
    public boolean canMortgage() {
        return owner != null && !isMortgaged;
    }

    public boolean canMortgage(Player p) {
        return p != null && p == owner && canMortgage();
    }

    // Thực hiện thế chấp: nhận tiền từ Ngân hàng, đánh dấu đã thế chấp
    public boolean mortgage() {
        if (canMortgage()) {
            isMortgaged = true;
            owner.addMoney(getMortgageValue());
            return true;
        }
        return false;
    }

    public boolean mortgage(Player p) {
        if (canMortgage(p)) {
            return mortgage();
        }
        return false;
    }

    // Kiểm tra có thể chuộc lại đất (unmortgage)
    public boolean canUnmortgage() {
        return owner != null && isMortgaged && owner.getBalance() >= getUnmortgageCost();
    }

    public boolean canUnmortgage(Player p) {
        return p != null && p == owner && canUnmortgage();
    }

    // Thực hiện chuộc lại đất: trả tiền thế chấp + 10% lãi cho Ngân hàng
    public boolean unmortgage() {
        if (canUnmortgage()) {
            if (owner.deductMoney(getUnmortgageCost())) {
                isMortgaged = false;
                return true;
            }
        }
        return false;
    }

    public boolean unmortgage(Player p) {
        if (canUnmortgage(p)) {
            return unmortgage();
        }
        return false;
    }

    // GETTER và SETTER
    public Player getOwner() { return owner; }
    public void setOwner(Player owner) { this.owner = owner; }
    public int getPrice() { return price; }
    public boolean isMortgaged() { return isMortgaged; }
    public void setMortgaged(boolean mortgaged) { this.isMortgaged = mortgaged; }
}
