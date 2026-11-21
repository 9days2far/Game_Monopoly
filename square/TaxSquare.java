package ProjectOop.square;
import  ProjectOop.player.*;


public class TaxSquare extends Square {
    private int taxAmount;
    // Constructor
    public TaxSquare(int position, String name, int taxAmount) {
        super(position,name);
        this.taxAmount = taxAmount;
    }
    @Override
    public void applyEffect(Player p) {
        if (!p.deductMoney(taxAmount)) {
            // Không đủ tiền mặt nộp thuế: nộp tạm toàn bộ số dư còn lại cho Ngân hàng.
            // GameController.checkBankruptcy() sẽ xử lý phần còn lại:
            //   - Huy động tài sản (thế chấp) trước khi tuyên bố phá sản
            //   - Trả tài sản còn lại về Ngân hàng nếu vẫn không đủ
            //   - Gọi declareBankruptcy() và loại người chơi khỏi game
            // → KHÔNG setBankrupt tại đây, để GameController kiểm soát hoàn toàn
            p.deductMoney(p.getBalance());
        }
    }

    // Ham trả về tiền thuế
    public int getTaxAmount() {
        return taxAmount;
    }
}
