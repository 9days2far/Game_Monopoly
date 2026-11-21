package ProjectOop.player;

import ProjectOop.dice.Dice;
import ProjectOop.square.*;
import java.util.ArrayList;
import java.util.List;

public class Player {
    public static final int PASS_GO_REWARD = 200; // Tiền thưởng khi qua hoặc dừng tại ô GO
    public static final int JAIL_POSITION = 10;   // Vị trí ô tù trên bàn cờ
    public static final int MAX_CONSECUTIVE_DOUBLES = 3; // Số lần đổ đôi tối đa trước khi bị phạt vào tù
    public static final int DEFAULT_STARTING_BALANCE = 1500; // Tiền khởi điểm mặc định

    //1.Thông tin ban đầu
    private String name;
    private int balance;
    private int position;
    private boolean isBankrupt;
    private int lastDiceRoll;

    //2.Tài sản
    private List<AssetSquare> ownedAssets;
    private int outOfJailFreeTickets;

    //3.Đi tù & Đổ đôi
    private boolean inJail;
    private int turnsInJail;
    private int consecutiveDoubles; // Số lần đổ đôi liên tiếp trong lượt đi

    //4.Constructor của 1 player (khởi tạo đầy đủ)
    public Player(String name, int startingBalance) {
        this.name = name;
        this.position = 0;
        this.balance = startingBalance;
        this.isBankrupt = false;
        this.lastDiceRoll = 0;

        this.inJail = false;
        this.turnsInJail = 0;
        this.consecutiveDoubles = 0;

        this.ownedAssets = new ArrayList<>();
        this.outOfJailFreeTickets = 0;
    }

    public Player(String name) {
        this(name, DEFAULT_STARTING_BALANCE);
    }

    //5.Di chuyển
    public void move(int step) {
        if (inJail) {
            // Đang ở trong tù thì không thể di chuyển tự do trừ khi được thả
            return;
        }

        int newPosition = this.position + step;
        // Đi qua hoặc dừng đúng ô GO (ô 0) theo chiều tiến (step > 0)
        if (step > 0 && newPosition >= 40) {
            passGo();
        }
        this.position = ((newPosition % 40) + 40) % 40;
    }

    public void passGo() {
        addMoney(PASS_GO_REWARD);
    }

    // Di chuyển tới ô chỉ định (dành cho thẻ Cơ hội / Khí vận)
    public void moveTo(int targetPosition, boolean canPassGo) {
        targetPosition = ((targetPosition % 40) + 40) % 40;
        // Nếu di chuyển tiến vượt qua ô GO thì được nhận tiền thưởng
        if (canPassGo && targetPosition < this.position) {
            passGo();
        }
        this.position = targetPosition;
    }

    // Bị tống vào tù (do ô 30, rút thẻ bài, hoặc đổ đôi 3 lần liên tiếp)
    // Dịch chuyển thẳng tới ô tù (10) và TUYỆT ĐỐI KHÔNG được tính qua ô GO (+200)
    public void sendToJail() {
        this.position = JAIL_POSITION;
        this.inJail = true;
        this.turnsInJail = 0;
        this.consecutiveDoubles = 0;
    }

    // Được thả tự do khỏi tù
    public void releaseFromJail() {
        this.inJail = false;
        this.turnsInJail = 0;
        this.consecutiveDoubles = 0;
    }

    // Xử lý kết quả xúc xắc & luật 3 lần double liên tiếp đi tù (Speeding rule)
    public boolean handleDiceRoll(Dice dice) {
        this.lastDiceRoll = dice.getTotal();

        if (dice.isDouble()) {
            this.consecutiveDoubles++;
            if (this.consecutiveDoubles >= MAX_CONSECUTIVE_DOUBLES) {
                sendToJail();
                return true; // Bị tống vào tù do vi phạm tốc độ (speeding)
            }
        } else {
            this.consecutiveDoubles = 0;
        }

        return false;
    }

    //6.Tài chính
    public void addMoney(int amount) {
        this.balance += amount;
    }

    public boolean deductMoney(int amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    //7.GETTER và SETTER
    public String getName() { return name; }
    public int getBalance() { return balance; }
    public int getPosition() { return position; }
    public boolean isBankrupt() { return isBankrupt; }
    public int getLastDiceRoll() { return lastDiceRoll; }
    public void setLastDiceRoll(int lastDiceRoll) { this.lastDiceRoll = lastDiceRoll; }

    public void setName(String name) { this.name = name; }
    public void setPosition(int position) { this.position = position; }

    //Các phương thức cho ô Jail
    public boolean isInJail() { return inJail; }
    public void setInJail(boolean inJail) { this.inJail = inJail; }
    public int getTurnsInJail() { return turnsInJail; }
    public void setTurnsInJail(int turnsInJail) { this.turnsInJail = turnsInJail; }

    //Quản lý số lần đổ đôi liên tiếp
    public int getConsecutiveDoubles() { return consecutiveDoubles; }
    public void setConsecutiveDoubles(int consecutiveDoubles) { this.consecutiveDoubles = consecutiveDoubles; }
    public void resetConsecutiveDoubles() { this.consecutiveDoubles = 0; }

    //Các phương thức tài sản
    public void setBankrupt(boolean isBankrupt) { this.isBankrupt = isBankrupt; }
    public List<AssetSquare> getOwnedAssets() { return ownedAssets; }
    public void addAsset(AssetSquare asset) {
        this.ownedAssets.add(asset);
    }
    public boolean removeAsset(AssetSquare asset) {
        return this.ownedAssets.remove(asset);
    }

    // Quản lý ô đất theo nhóm màu
    public List<PropertySquare> getOwnedPropertiesByColorGroup(ColorGroup colorGroup) {
        List<PropertySquare> result = new ArrayList<>();
        if (colorGroup == null) return result;
        for (AssetSquare asset : ownedAssets) {
            if (asset instanceof PropertySquare) {
                PropertySquare prop = (PropertySquare) asset;
                if (prop.getColorGroup() == colorGroup) {
                    result.add(prop);
                }
            }
        }
        return result;
    }

    public boolean hasFullColorGroup(ColorGroup colorGroup) {
        if (colorGroup == null) return false;
        return getOwnedPropertiesByColorGroup(colorGroup).size() == colorGroup.getTotalProperties();
    }

    /**
     * Kiểm tra xem trong nhóm màu chỉ định có ô nào đang có nhà/khách sạn hay không.
     * Theo luật Monopoly, nếu có nhà thì không được thế chấp hay giao dịch bất kỳ ô nào trong nhóm màu.
     */
    public boolean hasHousesInColorGroup(ColorGroup colorGroup) {
        if (colorGroup == null) return false;
        for (PropertySquare prop : getOwnedPropertiesByColorGroup(colorGroup)) {
            if (prop.getHouseLevel() > 0) {
                return true;
            }
        }
        return false;
    }

    // Quản lý tài sản thế chấp
    public List<AssetSquare> getMortgagedAssets() {
        List<AssetSquare> result = new ArrayList<>();
        for (AssetSquare asset : ownedAssets) {
            if (asset.isMortgaged()) {
                result.add(asset);
            }
        }
        return result;
    }

    //Quản lý thẻ ra tù
    public int getOutOfJailTicket() { return outOfJailFreeTickets; }
    public void addOutOfJailTicket() { this.outOfJailFreeTickets++; }
    public boolean useOutOfJailTicket() {
        if (this.outOfJailFreeTickets > 0) {
            this.outOfJailFreeTickets--;
            return true;
        }
        return false;
    }

    /**
     * Tổng tài sản có thể huy động (tiền mặt + giá trị thế chấp tối đa các tài sản hợp lệ).
     * Dùng để kiểm tra khả năng trả nợ trước khi tuyên bố phá sản.
     */
    public int getNetWorth() {
        int total = this.balance;
        for (AssetSquare asset : ownedAssets) {
            if (asset.canMortgage(this)) {
                total += asset.getMortgageValue();
            }
        }
        return total;
    }
}
