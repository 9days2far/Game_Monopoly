package ProjectOop.trade;

import ProjectOop.player.Player;
import ProjectOop.square.AssetSquare;
import ProjectOop.square.PropertySquare;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra tính hợp lệ của giao dịch theo quy chuẩn luật Cờ Tỷ Phú (Monopoly).
 * <p>
 * Đảm bảo:
 * <ul>
 *   <li>Không giao dịch tài sản không thuộc sở hữu.</li>
 *   <li>Không giao dịch đất thuộc nhóm màu đang có công trình (nhà/khách sạn).</li>
 *   <li>Số dư tiền mặt và số vé ra tù của 2 bên đủ đáp ứng đề nghị.</li>
 *   <li>Không chấp nhận giao dịch rỗng hoặc người chơi đã phá sản.</li>
 * </ul>
 * </p>
 */
public class TradeValidator {

    /**
     * Xác thực toàn diện một lời đề nghị giao dịch.
     *
     * @param offer gói đề nghị giao dịch
     * @return kết quả kiểm tra kèm thông báo lỗi (nếu có)
     */
    public static TradeValidationResult validate(TradeOffer offer) {
        if (offer == null) {
            return TradeValidationResult.fail("Giao dịch không tồn tại.");
        }

        Player sender = offer.getSender();
        Player receiver = offer.getReceiver();

        if (sender == null || receiver == null) {
            return TradeValidationResult.fail("Người tham gia giao dịch không hợp lệ.");
        }

        if (sender == receiver) {
            return TradeValidationResult.fail("Không thể tự giao dịch với chính mình.");
        }

        if (sender.isBankrupt() || receiver.isBankrupt()) {
            return TradeValidationResult.fail("Người chơi đã phá sản không thể tham gia giao dịch.");
        }

        if (offer.isEmpty()) {
            return TradeValidationResult.fail("Giao dịch rỗng: Phải có ít nhất một tài sản (tiền, đất, thẻ tù) được trao đổi.");
        }

        // 1. Kiểm tra tiền mặt
        if (sender.getBalance() < offer.getOfferedCash()) {
            return TradeValidationResult.fail(sender.getName() + " không đủ tiền mặt (hiện có: $"
                    + sender.getBalance() + ", đề nghị: $" + offer.getOfferedCash() + ").");
        }
        if (receiver.getBalance() < offer.getRequestedCash()) {
            return TradeValidationResult.fail(receiver.getName() + " không đủ tiền mặt (hiện có: $"
                    + receiver.getBalance() + ", yêu cầu: $" + offer.getRequestedCash() + ").");
        }

        // 2. Kiểm tra thẻ ra tù
        if (sender.getOutOfJailTicket() < offer.getOfferedJailCards()) {
            return TradeValidationResult.fail(sender.getName() + " không đủ vé ra tù (hiện có: "
                    + sender.getOutOfJailTicket() + ", đề nghị: " + offer.getOfferedJailCards() + ").");
        }
        if (receiver.getOutOfJailTicket() < offer.getRequestedJailCards()) {
            return TradeValidationResult.fail(receiver.getName() + " không đủ vé ra tù (hiện có: "
                    + receiver.getOutOfJailTicket() + ", yêu cầu: " + offer.getRequestedJailCards() + ").");
        }

        // 3. Kiểm tra trùng lặp tài sản
        Set<AssetSquare> offeredSet = new HashSet<>(offer.getOfferedProperties());
        if (offeredSet.size() != offer.getOfferedProperties().size()) {
            return TradeValidationResult.fail("Danh sách tài sản đưa ra bị trùng lặp.");
        }

        Set<AssetSquare> requestedSet = new HashSet<>(offer.getRequestedProperties());
        if (requestedSet.size() != offer.getRequestedProperties().size()) {
            return TradeValidationResult.fail("Danh sách tài sản yêu cầu bị trùng lặp.");
        }

        for (AssetSquare asset : offeredSet) {
            if (requestedSet.contains(asset)) {
                return TradeValidationResult.fail("Tài sản \"" + asset.getName() + "\" xuất hiện ở cả hai bên trao đổi.");
            }
        }

        // 4. Kiểm tra quyền sở hữu & luật nhà của bên Sender
        String senderPropErr = checkPropertiesTradable(sender, offer.getOfferedProperties());
        if (senderPropErr != null) {
            return TradeValidationResult.fail(senderPropErr);
        }

        // 5. Kiểm tra quyền sở hữu & luật nhà của bên Receiver
        String receiverPropErr = checkPropertiesTradable(receiver, offer.getRequestedProperties());
        if (receiverPropErr != null) {
            return TradeValidationResult.fail(receiverPropErr);
        }

        return TradeValidationResult.success();
    }

    /**
     * Kiểm tra một tài sản cụ thể có đủ điều kiện để giao dịch hay không.
     */
    public static boolean isAssetTradable(Player owner, AssetSquare asset) {
        if (owner == null || asset == null) {
            return false;
        }
        if (asset.getOwner() != owner) {
            return false;
        }
        if (asset instanceof PropertySquare prop) {
            if (prop.getColorGroup() != null && owner.hasHousesInColorGroup(prop.getColorGroup())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Kiểm tra danh sách bất động sản của người chơi:
     * - Phải thực sự thuộc quyền sở hữu của người đó.
     * - Nếu là PropertySquare, không được có nhà trên bất kỳ ô nào thuộc cùng bộ màu.
     */
    private static String checkPropertiesTradable(Player owner, List<AssetSquare> properties) {
        for (AssetSquare asset : properties) {
            if (asset.getOwner() != owner) {
                return owner.getName() + " không sở hữu ô \"" + asset.getName() + "\"!";
            }
            if (asset instanceof PropertySquare prop) {
                if (prop.getColorGroup() != null && owner.hasHousesInColorGroup(prop.getColorGroup())) {
                    return "Không thể giao dịch ô \"" + prop.getName()
                            + "\" vì nhóm màu " + prop.getColorGroup().getDisplayName()
                            + " đang có nhà/khách sạn! (Bắt buộc phải bán hết nhà về Ngân hàng trước).";
                }
            }
        }
        return null;
    }
}
