package ProjectOop.trade;

/**
 * Kết quả kiểm tra tính hợp lệ của một lời đề nghị giao dịch.
 */
public class TradeValidationResult {
    private final boolean valid;
    private final String errorMessage;

    private TradeValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public static TradeValidationResult success() {
        return new TradeValidationResult(true, "");
    }

    public static TradeValidationResult fail(String errorMessage) {
        return new TradeValidationResult(false, errorMessage);
    }

    public boolean isValid() {
        return valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
