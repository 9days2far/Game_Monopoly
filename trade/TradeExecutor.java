package ProjectOop.trade;

import ProjectOop.player.Player;
import ProjectOop.square.AssetSquare;

/**
 * Thực thi giao dịch nguyên tử (Atomic Transaction) giữa 2 người chơi.
 * <p>
 * Đảm bảo tính toàn vẹn dữ liệu: Chỉ hoán đổi khi toàn bộ các điều kiện nghiệp vụ
 * đã được xác thực hợp lệ. Nếu không hợp lệ, không có bất kỳ trạng thái nào bị thay đổi.
 * </p>
 */
public class TradeExecutor {

    /**
     * Thực hiện hoán đổi tài sản giữa Sender và Receiver theo gói đề nghị.
     *
     * @param offer gói đề nghị giao dịch đã được cả hai bên đồng thuận
     * @return true nếu thực thi thành công, false nếu vi phạm điều kiện
     */
    public static synchronized boolean executeTrade(TradeOffer offer) {
        if (offer == null) {
            return false;
        }

        // Xác thực lại lần cuối trước khi thay đổi trạng thái
        TradeValidationResult validation = TradeValidator.validate(offer);
        if (!validation.isValid()) {
            return false;
        }

        Player sender = offer.getSender();
        Player receiver = offer.getReceiver();

        // 1. Chuyển tiền mặt
        if (offer.getOfferedCash() > 0) {
            sender.deductMoney(offer.getOfferedCash());
            receiver.addMoney(offer.getOfferedCash());
        }
        if (offer.getRequestedCash() > 0) {
            receiver.deductMoney(offer.getRequestedCash());
            sender.addMoney(offer.getRequestedCash());
        }

        // 2. Chuyển thẻ ra tù miễn phí
        for (int i = 0; i < offer.getOfferedJailCards(); i++) {
            if (sender.useOutOfJailTicket()) {
                receiver.addOutOfJailTicket();
            }
        }
        for (int i = 0; i < offer.getRequestedJailCards(); i++) {
            if (receiver.useOutOfJailTicket()) {
                sender.addOutOfJailTicket();
            }
        }

        // 3. Chuyển giao bất động sản: Sender -> Receiver
        // Lưu ý: Giữ nguyên trạng thái thế chấp isMortgaged nếu có theo luật gốc
        for (AssetSquare asset : offer.getOfferedProperties()) {
            sender.removeAsset(asset);
            asset.setOwner(receiver);
            receiver.addAsset(asset);
        }

        // 4. Chuyển giao bất động sản: Receiver -> Sender
        for (AssetSquare asset : offer.getRequestedProperties()) {
            receiver.removeAsset(asset);
            asset.setOwner(sender);
            sender.addAsset(asset);
        }

        return true;
    }
}
