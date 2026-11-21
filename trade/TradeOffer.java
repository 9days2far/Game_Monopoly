package ProjectOop.trade;

import ProjectOop.player.Player;
import ProjectOop.square.AssetSquare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đóng gói toàn bộ thông tin của một lời đề nghị giao dịch (Trade Offer) giữa hai người chơi.
 * <p>
 * Bao gồm tài sản do Sender đưa ra và tài sản do Sender yêu cầu từ Receiver:
 * tiền mặt, danh sách bất động sản và thẻ ra tù miễn phí.
 * </p>
 */
public class TradeOffer {
    private final Player sender;
    private final Player receiver;

    // Phía Sender đưa ra
    private final int offeredCash;
    private final List<AssetSquare> offeredProperties;
    private final int offeredJailCards;

    // Phía Sender yêu cầu Receiver trao lại
    private final int requestedCash;
    private final List<AssetSquare> requestedProperties;
    private final int requestedJailCards;

    public TradeOffer(Player sender, Player receiver,
                      int offeredCash, List<AssetSquare> offeredProperties, int offeredJailCards,
                      int requestedCash, List<AssetSquare> requestedProperties, int requestedJailCards) {
        this.sender = sender;
        this.receiver = receiver;
        this.offeredCash = Math.max(0, offeredCash);
        this.offeredProperties = offeredProperties != null ? new ArrayList<>(offeredProperties) : new ArrayList<>();
        this.offeredJailCards = Math.max(0, offeredJailCards);

        this.requestedCash = Math.max(0, requestedCash);
        this.requestedProperties = requestedProperties != null ? new ArrayList<>(requestedProperties) : new ArrayList<>();
        this.requestedJailCards = Math.max(0, requestedJailCards);
    }

    public Player getSender() {
        return sender;
    }

    public Player getReceiver() {
        return receiver;
    }

    public int getOfferedCash() {
        return offeredCash;
    }

    public List<AssetSquare> getOfferedProperties() {
        return Collections.unmodifiableList(offeredProperties);
    }

    public int getOfferedJailCards() {
        return offeredJailCards;
    }

    public int getRequestedCash() {
        return requestedCash;
    }

    public List<AssetSquare> getRequestedProperties() {
        return Collections.unmodifiableList(requestedProperties);
    }

    public int getRequestedJailCards() {
        return requestedJailCards;
    }

    /**
     * Kiểm tra xem gói giao dịch có rỗng ở cả 2 chiều hay không.
     */
    public boolean isEmpty() {
        return offeredCash == 0 && offeredProperties.isEmpty() && offeredJailCards == 0
                && requestedCash == 0 && requestedProperties.isEmpty() && requestedJailCards == 0;
    }
}
