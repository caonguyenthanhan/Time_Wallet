# Ảnh chụp giao diện chuẩn (`ui-preview`) — Time Wallet `v1.2.0` Production Candidate

Bộ ảnh chụp màn hình mới được thực hiện trực tiếp trên **APK Release ký số Production Candidate (`v1.2.0`, `versionCode 16`)** cài trên môi trường QA tách biệt **`WallyQA`** với bộ dữ liệu mock synthetic **1 tuần (11/11 → 17/11/2026)** và kết nối AI thật (dữ liệu tổng hợp + khóa QA tạm, đã xóa khóa khỏi thiết bị ngay sau khi chụp).

- **Thiết bị & môi trường QA:** LDPlayer 9 `WallyQA` (`index 1`, `emulator-5556`), Android 9, độ phân giải `780×1688 @320dpi` (`~390dp`), múi giờ `Asia/Ho_Chi_Minh`.
- **APK lúc chụp bộ ảnh cập nhật (`01*`, `02`, `03*`, `04*`, `05*`):** Release `TimeWallet-MVP-v1.2.0-PRODUCTION-CANDIDATE-release.apk` (`8,457,131` bytes, SHA-256 `1CCA6C8AA13B196997DD32DBC0A178ABB688D255AA5ABDF6F2D2681BC7AFDE2D`, `versionCode=16`), đã kiểm chứng trực quan 5 thành phần UI Compose + nâng cấp `Wally AI Coach` (`wally-coach-5` + Action Items) trên `WallyQA`. *(Chi tiết bảng PASS / `NOT_TESTED` tại [WALLY_V120_PRODUCTION_CANDIDATE.md](../../../WALLY_V120_PRODUCTION_CANDIDATE.md).)*
- **Dữ liệu mock 1 tuần:** Nạp từ bản JSON synthetic `7` ngày (`41` bản ghi `11/11–17/11/2026`, `5` Tag, `2` Mục tiêu, `3` Ngân sách ưu tiên, `1` Daily Review, kèm xuyên đêm `UC-15`, khoảng nghỉ `BREAK_CONFIRMED`, hoạt động song song `PARALLEL_CONFIRMED`, `DOING` và `PLANNED`) qua chức năng **`Nhập / khôi phục JSON`** (`Khôi phục toàn bộ`). Toàn bộ là dữ liệu synthetic QA; không chạm instance cá nhân của Owner (`index 0`). *(Thư mục `out/` được loại trừ khỏi Git theo `.gitignore`.)*
- **Logcat & live AI sau phiên chụp:** `FATAL EXCEPTION = 0`; live AI `cinb/claude-sonnet-4-6` chạy 3 yêu cầu (`RECORD`, `QUERY`, `COACH`) đều trả HTTP `200`, `0` lỗi; đã xóa khóa QA và tắt Opt-in trước khi tắt `WallyQA`.
- **Trạng thái chưa kiểm thử runtime (`NOT_TESTED`):** Khởi động lại lạnh (`BOOT_COMPLETED`) trên `WallyQA`, Android 13–16 AVD (Rule 14 AEHD), và điện thoại thật của Owner.
- **Thư mục lịch sử:** [../ldplayer-android9/README.md](../ldplayer-android9/README.md) giữ nguyên bộ ảnh baseline `v1.0.11` (`versionCode 12`, commit `195a60c`).

## Ảnh tổng hợp 5 màn hình nổi bật (UI Mới + Live AI)

![Tổng hợp 5 màn hình nổi bật Time Wallet v1.2.0](_tong-hop_5-man-hinh.jpg)

## Danh sách ảnh chi tiết

| Tệp ảnh | Màn hình / Luồng nghiệp vụ | Điểm nhấn giao diện |
|---|---|---|
| [`01_tong-quan.png`](01_tong-quan.png) | **Tổng quan** | Ví thời gian hôm nay (`15h 36` đã ghi nhận + `Dự kiến 1h 54 · chưa tính vào đã ghi nhận`), 3 nút nhanh, Donut 2D đủ tên `Công việc`, `Ngủ`, `Nghỉ ngơi`. |
| [`01_tong-quan_donut_pop.png`](01_tong-quan_donut_pop.png) | **Donut 2D tương tác** | Chạm `💼 Công việc` đẩy lát xanh nổi 2D, tô viền dòng chú giải tương ứng và hiện `6h 53 / Công việc · 45%` ở tâm. |
| [`01b_wally-coach-ai.png`](01b_wally-coach-ai.png) | **Wally Coach AI (`7 ngày qua`)** | Live `cinb/claude-sonnet-4-6` (`wally-coach-5`): thanh nhấn + headline 1 câu, bullet highlight, hộp `Lưu ý:`, 2 Action Items (`Gắn Tag →`, `Lên lịch →`), gợi ý mục tiêu và cụm FAB nổi (`↑`, `✦`, `+`). |
| [`01c_speed-dial.png`](01c_speed-dial.png) | **Speed Dial thao tác nhanh** | Mở từ FAB `+` góc dưới phải với 3 nhãn rõ ràng (`Nhìn lại ngày hôm nay`, `Bắt đầu đồng hồ`, `Ghi giờ thủ công`). |
| [`02_dong-ho.png`](02_dong-ho.png) | **Đồng hồ** | Đếm giờ thời gian thực, chọn danh mục/Tag (`Học AI`, `Deep Work`, `Đọc sách`), `▶ Bắt đầu đo` và thẻ `Dopamine Time Dice`. |
| [`03_wally.png`](03_wally.png) | **Wally Chat & Trả lời `✦`** | Bong bóng gửi canh phải có đuôi + `"20:28 · Wally đã xem ✓✓"`, thẻ trả lời `✦ Wally (AI)` canh trái có vạch nhấn + danh sách số liệu trong ngày. |
| [`03a_wally-typing.png`](03a_wally-typing.png) | **Chỉ báo đang soạn (`3 chấm`)** | Trạng thái `"20:28 · Đã gửi ✓"` và bong bóng nhận `Wally đang soạn...` với 3 chấm xanh nảy tuần tự + nút `Hủy`. |
| [`03b_wally-proposals.png`](03b_wally-proposals.png) | **Wally AI (Đa đề xuất)** | Trạng thái `✦ AI đã bật · ⚙`, bóc tách câu tiếng Việt thành **5 thẻ đề xuất** kèm gợi ý Tag `🏷` và nút `✓ Xác nhận toàn bộ (5 hoạt động)`. |
| [`04_nhat-ky.png`](04_nhat-ky.png) | **Nhật ký (Sáng → Chiều)** | Timeline `Hôm nay, 17/11` (`15h 37`, `+6h 30` xuyên đêm), cột giờ trái `07:00 → 14:30`, thẻ `Đã đo` / `Có giờ` / `Khoảng nghỉ`. |
| [`04b_nhat-ky-doing-planned.png`](04b_nhat-ky-doing-planned.png) | **Timeline `DOING` / `PLANNED` & `±15p`** | Chấm đặc (`15:00`), vòng xoay tiến độ động (`19:45 Đang diễn ra`), chấm rỗng kết thúc đường nối dọc (`21:15 Dự kiến`), tay cầm `↔ Kéo giãn nhanh ±15p`. |
| [`05_phan-tich.png`](05_phan-tich.png) | **Phân tích (`7 ngày gần nhất`)** | Tổng tuần `106h 54` (`7 ngày · Có giờ`), khử trùng song song `1h 00`, đủ **7 cột `Th 4` → `Th 3`**. |
| [`05b_phan-tich-budgets-dnd.png`](05b_phan-tich-budgets-dnd.png) | **Donut 6 danh mục & Ngân sách Kéo-Thả** | Donut 6 lát (`106h 54 · 7 ngày`, đủ tên `Ngủ` → `Khác` không cắt chữ) + thẻ `Ngân sách tuần` có tay cầm `⋮⋮` và nhãn `★ Ưu tiên`. |
| [`06_nhap-gio_manual.png`](06_nhap-gio_manual.png) | **Thêm giờ (`ManualEntrySheet`)** | Nhập khoảng giờ tự tính, cảnh báo trạng thái đang diễn ra + nhắc xác nhận, footer ghim cố định (`Clean · Xóa nhập liệu`, `Thoát`, `Lưu`). *(ảnh preview v1.2.0)* |
| [`07_xu-ly-chong-lan.png`](07_xu-ly-chong-lan.png) | **Xử lý hoạt động xen giữa (`OverlapResolutionDialog`)** | Phát hiện chồng lấn với các lựa chọn tách khoảng nghỉ, chạy song song, chỉnh giờ hoặc để sau. *(ảnh preview v1.2.0)* |
| [`08_xac-nhan-trang-thai.png`](08_xac-nhan-trang-thai.png) | **Xác nhận trạng thái (`ActivityConfirmationDialog`)** | Điều chỉnh giờ / kết quả thực tế, tiếp tục thêm thời gian, hủy hoạt động dự kiến hoặc để sau. *(ảnh preview v1.2.0)* |
| [`09_ban-nhap_tong-quan.png`](09_ban-nhap_tong-quan.png) | **Bản nháp đang nhập dở trên Tổng quan** | Thẻ khôi phục bản nháp tự lưu trên Tổng quan kèm nút `Clean · Xóa nháp`. *(ảnh preview v1.2.0)* |
| [`10_nhap-json_release.png`](10_nhap-json_release.png) | **Nhập & khôi phục dữ liệu (JSON)** | Hộp thoại `ImportJsonDialog` trên bản Release chọn chế độ `Gộp an toàn` / `Khôi phục toàn bộ`, không ghi đè khóa AI. |
