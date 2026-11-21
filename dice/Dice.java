package ProjectOop.dice;

import java.util.Random;

public class Dice {
    private int die1; // Giá trị viên xúc xắc 1 (1 - 6)
    private int die2; // Giá trị viên xúc xắc 2 (1 - 6)
    private int consecutiveDoubles; // Số lần đổ đôi liên tiếp
    private final Random random;

    public Dice() {
        this.random = new Random();
        this.die1 = 0;
        this.die2 = 0;
        this.consecutiveDoubles = 0;
    }

    public Dice(Random random) {
        this.random = random != null ? random : new Random();
        this.die1 = 0;
        this.die2 = 0;
        this.consecutiveDoubles = 0;
    }

    // Đổ 2 viên xúc xắc và trả về tổng điểm
    public int roll() {
        this.die1 = random.nextInt(6) + 1;
        this.die2 = random.nextInt(6) + 1;
        if (isDouble()) {
            this.consecutiveDoubles++;
        } else {
            this.consecutiveDoubles = 0;
        }
        return getTotal();
    }

    // Kiểm tra xem có đổ đôi (2 mặt giống nhau) hay không
    public boolean isDouble() {
        return die1 > 0 && die1 == die2;
    }

    // Kiểm tra có bị phạt vi phạm tốc độ (đổ đôi 3 lần liên tiếp) hay không
    public boolean isSpeeding() {
        return consecutiveDoubles >= 3;
    }

    // Tổng điểm của 2 viên xúc xắc
    public int getTotal() {
        return die1 + die2;
    }

    // GETTER và SETTER
    public int getDie1() {
        return die1;
    }

    public int getDie2() {
        return die2;
    }

    public int getConsecutiveDoubles() {
        return consecutiveDoubles;
    }

    public void resetConsecutiveDoubles() {
        this.consecutiveDoubles = 0;
    }

    public void setConsecutiveDoubles(int consecutiveDoubles) {
        this.consecutiveDoubles = consecutiveDoubles;
    }

    // Hỗ trợ thiết lập giá trị thủ công (hữu ích cho unit test / mock kịch bản)
    public void setDice(int die1, int die2) {
        this.die1 = die1;
        this.die2 = die2;
        if (isDouble()) {
            this.consecutiveDoubles++;
        } else {
            this.consecutiveDoubles = 0;
        }
    }

    @Override
    public String toString() {
        return "Dice{" + "die1=" + die1 + ", die2=" + die2 + ", total=" + getTotal() + ", isDouble=" + isDouble() + ", consecutiveDoubles=" + consecutiveDoubles + '}';
    }
}
