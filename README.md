# Cờ Tỷ Phú Việt Nam Online (Web Multiplayer + Spectator)

Phiên bản **Web Game trực tuyến thời gian thực** được xây dựng trên nền tảng **Core OOP Java** của dự án Cờ Tỷ Phú Việt Nam, cho phép nhiều người chơi cùng lúc trên trình duyệt (PC/điện thoại), đấu với **Bot AI**, trò chuyện trực tuyến và **gửi link cho bạn bè xem trực tiếp (Spectator Mode)**.

---

## Tính năng nổi bật

- **Chơi Online nhiều người (2–6 người/phòng):** Tạo phòng công khai hoặc phòng riêng tư bằng mã 5 ký tự, bốc thăm thứ tự lượt đi ngẫu nhiên, hỗ trợ kết nối lại (reconnect) khi lỡ F5 hoặc rớt mạng.
- **Chế độ Xem Trực Tiếp (Live Spectator):** Gửi link xem (`?room=MÃ&watch=1`) cho không giới hạn người xem theo dõi bàn cờ, xúc xắc, thẻ bài vừa rút và nhắn chat cổ vũ theo thời gian thực.
- **Bot AI tự động:** Chủ phòng có thể thêm/xóa Bot AI ngay trong phòng chờ để chơi thử 1 mình hoặc bù người.
- **Đầy đủ luật Cờ Tỷ Phú chuẩn từ Core OOP:**
  - Bản đồ Việt Nam 40 ô (`cells_40` & `cells_40_upright` độ nét cao), 2 bộ bài Cơ Hội & Khí Vận (32 lá).
  - Đổ đôi đi tiếp, vi phạm tốc độ (3 lần đôi liên tiếp vào tù), 3 lựa chọn ra tù (nộp $50, dùng thẻ ra tù, thử đổ đôi).
  - Mua tài sản, xây nhà đều tay (1–4 nhà + Khách sạn cấp 5), thế chấp 50% & chuộc lại (+10% phí).
  - Thương lượng giao dịch 2 chiều (`TradeValidator` & `TradeExecutor`) đổi tiền mặt, đất và thẻ ra tù.
  - Thế chấp cưỡng chế khi thiếu nợ và xử lý phá sản chuyển giao về chủ nợ hoặc Ngân hàng.
- **Zero-Dependency Server:** Chạy thuần trên **Java 21** (`HttpServer` + Virtual Threads + Server-Sent Events + HTML/CSS/JS thuần), không cần cài Maven, Gradle hay Node.js.

---

## Cấu trúc thư mục

```text
📦 Game_Monopoly
┣ 📂 board/      # Bàn cờ 40 ô bản đồ Việt Nam & bộ luật RULES.md
┣ 📂 card/       # Hệ thống 32 thẻ Cơ Hội & Khí Vận + ảnh thẻ
┣ 📂 dice/       # Bộ xúc xắc & luật đổ đôi
┣ 📂 player/     # Thực thể người chơi & token 6 màu
┣ 📂 square/     # 40 ô đa hình + bộ ảnh cells_40 & cells_40_upright
┣ 📂 trade/      # Xác thực & thực thi giao dịch nguyên tử
┣ 📂 server/     # Java 21 Online Web Server (HttpServer, SseHub, OnlineGameRoom, RoomManager, JsonUtil)
┣ 📂 public/     # Giao diện Web (index.html, style.css, app.js)
┣ 📜 run_web_server.bat / .sh
┣ 📜 Dockerfile
┗ 📜 render.yaml
```

---

## Hướng dẫn chạy nhanh trên máy (Chơi qua LAN hoặc Internet)

**Yêu cầu:** JDK 21 trở lên (`java` và `javac`).

### 1. Chạy trên Windows
Nhấp đúp hoặc chạy từ terminal:
```bat
run_web_server.bat
```
Hoặc chạy thủ công:
```powershell
mkdir out -Force
javac -encoding UTF-8 -d out board/*.java card/*.java dice/*.java player/*.java square/*.java trade/*.java server/*.java
java -cp out ProjectOop.web.WebServerMain
```
Sau khi khởi động, server sẽ in ra:
- Link mở trên máy: `http://localhost:8080`
- Link gửi bạn bè cùng mạng Wi-Fi / LAN: `http://<LAN-IP>:8080`

### 2. Chơi online qua Internet với bạn bè ngay (không cần thuê Server)
Sau khi chạy server ở cổng `8080`, mở thêm 1 terminal chạy **Cloudflare Tunnel**:
```powershell
cloudflared tunnel --url http://localhost:8080
```
Lấy đường link `https://...trycloudflare.com` gửi bạn bè để vào chơi hoặc bấm nút **Copy Link Mời Chơi** / **Copy Link Xem Online** ngay trong phòng.

---

## Deploy lên Cloud để chạy 24/7 (Render / Railway / Fly.io)

Dự án đã có sẵn [`Dockerfile`](Dockerfile) và [`render.yaml`](render.yaml) (tự động đọc biến môi trường `PORT`):

1. Truy cập **Render.com** → **New +** → **Web Service** (hoặc **Blueprint**).
2. Kết nối repository GitHub `9days2far/Game_Monopoly`.
3. Render tự nhận `Dockerfile` (hoặc `render.yaml`), build image Java 21 và mở dịch vụ online 24/7.
4. Chia sẻ đường link web công khai cho mọi người cùng vào tạo phòng, chơi và xem trực tiếp.
