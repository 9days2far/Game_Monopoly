# TÀI LIỆU LUẬT CHƠI VÀ THÔNG SỐ CỜ TỶ PHÚ (MONOPOLY)

Tài liệu quy chuẩn logic trò chơi dành cho việc phát triển các package: `board`, `square`, `player`, `dice`, `card`, `trade`, `ui` và `GameController`.

---

## 1. Cấu Trúc Bàn Cờ (Board Layout)
Bàn cờ chuẩn gồm **40 ô** (đánh chỉ số từ `0` đến `39`, đi theo chiều kim đồng hồ):
* **Ô 0:** Khởi hành (GO).
* **Ô 10:** Ô Tù / Thăm tù (Jail / Just Visiting).
* **Ô 20:** Đỗ xe miễn phí (Free Parking).
* **Ô 30:** Vào tù (Go To Jail).
* **22 ô Đất màu (Properties):** Chia thành 8 nhóm màu (ánh xạ bản đồ Việt Nam trong `Board.java`: Tuyên Quang → Hà Nội).
* **4 ô Bến xe / Nhà ga (Railroads):** Ô 5 (Ga Lào Cai), 15 (Ga Nam Định), 25 (Ga Quy Nhơn), 35 (Ga Sài Gòn).
* **2 ô Tiện ích (Utilities):** Điện lực EVN (ô 12) và Nước sạch Sài Gòn (ô 28).
* **3 ô Cơ hội (Chance - ô 7, 22, 36) & 3 ô Khí vận (Community Chest - ô 2, 17, 33).**
* **2 ô Thuế (Tax):** Thuế Thu nhập (ô 4) và Thuế cao cấp (ô 38).

---

## 2. Di Chuyển & Xúc Xắc (Movement & Dice)
* **Đổ xúc xắc:** Lượt đi dùng 2 viên xúc xắc 6 mặt (tổng điểm từ 2 đến 12).
* **Đổ đôi (Doubles):**
    * Người chơi được đi tiếp một lượt nữa sau khi hoàn thành lượt hiện tại.
    * **Luật vi phạm tốc độ (Speeding):** Nếu đổ đôi **3 lần liên tiếp** trong cùng một vòng đi, người chơi bị tống thẳng vào tù ngay lập tức và mất lượt tiếp theo.
* **Qua ô GO (Ô 0):** Mỗi khi người chơi đi ngang qua hoặc dừng lại chính xác tại ô GO, ngân hàng cộng ngay **$200** vào tài khoản.

---

## 3. Quy Tắc Các Loại Ô (Square Types)

### 3.1. Ô Đất Màu (PropertySquare)
* **Mua đất:** Khi dừng chân tại ô đất vô chủ:
    * Người chơi có quyền chọn mua với giá niêm yết (`price`).
    * Nếu không mua: Ngân hàng mở phiên đấu giá cho toàn bộ người chơi còn lại (bắt đầu từ mức giá bất kỳ). *(Ghi chú tiến độ: Cơ chế đấu giá chưa triển khai trong phiên bản hiện tại, dự kiến bổ sung ở giai đoạn mở rộng).*
* **Xây nhà & Khách sạn (`houseLevel` từ 0 đến 5):**
    * Chỉ được phép xây nhà khi người chơi **sở hữu trọn bộ màu** của khu vực đó (và không có ô nào cùng bộ màu đang bị thế chấp).
    * Phải xây đều tay: Không được xây 2 nhà trên một ô nếu các ô khác cùng bộ màu chưa có 1 nhà.
    * Cấp tối đa: 4 nhà thường -> Nâng cấp tiếp thành 1 Khách sạn (tương đương cấp 5).
* **Tính tiền thuê (`Rent`):**
    * Đất trống lẻ: Thu `baseRent`.
    * Đất trống nhưng **đã đủ bộ màu** (chưa xây nhà): Tiền thuê **gấp đôi** `baseRent * 2`.
    * Có nhà/khách sạn: Thu theo bảng nhân cấp độ `RENT_MULTIPLIERS = {1, 5, 15, 36, 44, 52}`.
* **Thế chấp (Mortgage):**
    * Khi cần tiền gấp, người chơi có thể thế chấp đất cho Ngân hàng để nhận lại **50% giá mua đất**.
    * Điều kiện: Phải bán hết toàn bộ nhà trên tất cả các ô thuộc bộ màu đó (theo luật chuẩn là bán lại cho Ngân hàng với giá 50% `houseCost`; *lưu ý: thao tác bán nhà chủ động `sellHouse()` dự kiến bổ sung ở giai đoạn mở rộng*).
    * Đất đang thế chấp **không được thu tiền thuê** nếu người khác đi vào.
    * Chuộc lại đất: Trả đủ tiền thế chấp + **10% lãi**.

---

### 3.2. Ô Nhà Ga & Tiện Ích (Railroad & Utility)
* **Nhà ga (4 ô):** Tiền thuê tính theo số lượng ga người đó sở hữu:
    * 1 ga: $25
    * 2 ga: $50
    * 3 ga: $100
    * 4 ga: $200
* **Tiện ích (Điện lực / Nước):** Tiền thuê dựa trên tổng điểm xúc xắc của người vừa bước vào:
    * Sở hữu 1 tiện ích: Tiền thuê = `Điểm xúc xắc * 4`.
    * Sở hữu cả 2 tiện ích: Tiền thuê = `Điểm xúc xắc * 10`.

---

### 3.3. Ô Tù (JailSquare)
* **Dừng vào ô 10 (Thăm tù):** Không có hiệu ứng gì, người chơi chỉ là khách ghé thăm (`isGoToJail = false`).
* **Bị tống vào tù khi:**
    1. Dừng chân tại ô 30 (Go To Jail).
    2. Bốc phải thẻ bài "Go To Jail" từ ô Cơ hội / Khí vận.
    3. Đổ đôi 3 lần liên tiếp.
       *(Khi bị vào tù, lập tức dịch chuyển về ô 10, không được nhận $200 dù có lướt qua ô GO).*
* **Quy tắc khi ở trong tù:**
    * **Vẫn được:** Thu tiền thuê nhà, tham gia đấu giá, mua bán trao đổi đất.
    * **Cách ra tù (chọn 1 trong 3):**
        1. Trả phí bảo lãnh **$50** trước khi đổ xúc xắc ở lượt của mình.
        2. Sử dụng thẻ "Get Out of Jail Free" (Thẻ miễn phí ra tù).
        3. Đổ xúc xắc: Nếu ra số đôi thì được thả tự do ngay lập tức và đi tiếp số ô vừa đổ (không được đổ thêm lượt nữa).
    * **Hết hạn 3 lượt:** Nếu sau 3 lượt cố đổ đôi không thành công, ở lượt thứ 3 bắt buộc phải trả $50 (hoặc dùng thẻ) và đi tiếp theo điểm xúc xắc vừa đổ.

---

### 3.4. Các Ô Đặc Biệt Khác
* **Ô Thuế (Tax):** Trả tiền thẳng cho Ngân hàng khi bước vào.
    * Thuế thu nhập (ô 4): Trả cố định $200.
    * Thuế cao cấp / Thuế xa xỉ (ô 38): Trong bản đồ hiện tại áp dụng cố định **$100** (luật gốc $100 hoặc $75 tùy phiên bản).
* **Đỗ xe miễn phí (Free Parking - ô 20):** Ô an toàn, không có sự kiện gì xảy ra (một số luật chơi phụ cho phép người dừng chân nhặt toàn bộ tiền thuế/phạt tích lũy).
* **Cơ hội (Chance) & Khí vận (Community Chest):** Bốc ngẫu nhiên 1 lá bài:
    * Thưởng tiền / Phạt tiền.
    * Di chuyển tới ô chỉ định (ô GO, ga gần nhất, vào tù...).
    * Sửa chữa nhà/khách sạn (phạt tiền theo số lượng nhà đang sở hữu).
    * Thẻ miễn phí ra tù (giữ lại dùng hoặc bán lại cho người khác).

---

## 4. Giao Dịch, Phá Sản & Kết Thúc (Game Over)
* **Trao đổi / Giao dịch (Trading):**
    * Các người chơi có thể tự do thương lượng đổi đất, tiền mặt hoặc thẻ ra tù với nhau (trong lượt của mình hoặc giữa các lượt đi).
    * Không được giao dịch ô đất nếu trên bộ màu đó vẫn còn nhà (phải bán nhà trước).
* **Phá sản (Bankruptcy):**
    * Xảy ra khi một người chơi phải trả nợ (tiền thuê hoặc thuế) mà tổng tiền mặt + giá trị huy động tài sản (bán nhà / thế chấp đất) không đủ bù.
    * **Nợ người chơi khác:** Toàn bộ tiền mặt, thẻ ra tù và tài sản (giữ nguyên trạng thái đang thế chấp) được chuyển giao hết cho chủ nợ. Người chơi bị loại.
    * **Nợ Ngân hàng:** Theo luật gốc, toàn bộ tài sản bị tịch thu, gỡ bỏ thế chấp và đem đấu giá công khai cho những người còn lại *(trong phiên bản hiện tại, tài sản được thu hồi về trạng thái vô chủ, gỡ thế chấp và reset `houseLevel = 0`; phần đấu giá sẽ bổ sung ở giai đoạn sau)*.
* **Điều kiện thắng:** Người duy nhất còn trụ lại sau khi toàn bộ đối thủ đã phá sản.

---

## 5. Phân Tách Trách Nhiệm Thiết Kế Hướng Đối Tượng (OOP Mapping)
* **`square`:**
    * `Square` (abstract): Quản lý vị trí, tên, hàm `applyEffect(Player p)`.
    * `AssetSquare` (abstract): Lớp cha của các ô có thể sở hữu, quản lý giá mua, chủ sở hữu, trạng thái thế chấp (`canBuy`, `buyAsset`, `mortgage`, `unmortgage`, `calculateRent`).
    * `PropertySquare`: Quản lý giá nhà, cấp nhà, nhóm màu (`ColorGroup`), kiểm tra nâng cấp đều tay (`canUpgrade`, `upgrade`) và hệ số tiền thuê.
    * `RailroadSquare`: Ô nhà ga, tính tiền thuê lũy tiến theo số ga chủ sở hữu nắm giữ (`$25 / $50 / $100 / $200`).
    * `UtilitySquare`: Ô tài sản có tiền thuê phụ thuộc vào điểm xúc xắc và số tiện ích chủ sở hữu nắm giữ (`4x` / `10x`).
    * `JailSquare`: Phân loại ô 10 hay ô 30 (`isGoToJail`), logic nộp bảo lãnh (`payBail`), dùng thẻ ra tù (`useTicketToFree`).
    * `TaxSquare`, `CardSquare` (cùng `ChanceSquare` kế thừa tương thích), `GoSquare`, `FreeParkingSquare`: Các ô sự kiện, thuế, rút bài và ô góc đặc biệt.
    * `ColorGroup`: Enum định nghĩa 8 nhóm màu và số lượng ô mỗi nhóm.
* **`board`:**
    * Khởi tạo mảng `Square[] squares = new Square[40];` với đầy đủ thông số 40 ô bản đồ Việt Nam cùng 2 bộ bài `chanceDeck` và `communityChestDeck`.
    * Cung cấp các hàm tìm kiếm ô (`getSquare`), truy xuất theo nhóm màu (`getPropertiesByColorGroup`) và kiểm tra sở hữu đủ bộ màu (`hasColorGroup`).
* **`card`:**
    * `Card`, `ActionCard`, `CardEffect`, `CardType`, `CardDeck`: Quản lý 16 lá Cơ Hội và 16 lá Khí Vận theo chu kỳ rút/trả bài.
* **`dice`:**
    * `Dice`: Đổ 2 viên xúc xắc (`roll`), kiểm tra đổ đôi (`isDouble`) và đếm 3 lần đổ đôi liên tiếp (`isSpeeding`).
* **`player`:**
    * `Player`: Quản lý tiền tệ, vị trí hiện tại (`move`, `moveTo`), danh sách tài sản (`List<AssetSquare>`), kiểm tra nhà theo nhóm màu (`hasHousesInColorGroup`), trạng thái tù (`inJail`, `turnsInJail`), thẻ ra tù và tài sản ròng có thể huy động (`getNetWorth`).
* **`trade`:**
    * `TradeOffer`, `TradeValidator`, `TradeValidationResult`, `TradeExecutor`: Đóng gói, kiểm tra hợp lệ theo luật Monopoly và hoán đổi tài sản nguyên tử giữa 2 người chơi.
* **`GameController` & `ui.GameUI`:**
    * `GameController`: Vòng lặp trò chơi Console (`startGame`), quản lý lượt đi, điều phối tương tác mua/nâng cấp/thế chấp/giao dịch và xử lý phá sản.
    * `ui.GameUI`: Giao diện đồ họa Java Swing 60 FPS kết nối trực tiếp vào Core Domain, đầy đủ chọn người chơi, tung xúc xắc phân lượt, bàn cờ, quản lý tài sản, giao dịch và màn hình kết thúc.