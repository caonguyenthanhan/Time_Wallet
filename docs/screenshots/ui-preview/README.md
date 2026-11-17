# Ảnh chụp giao diện chuẩn (`ui-preview`) — Time Wallet `v1.2.0` Production Candidate

Bộ ảnh chụp màn hình mới được thực hiện trực tiếp trên **APK Release ký số Production Candidate (`v1.2.0`, `versionCode 16`)** cài trên môi trường QA tách biệt **`WallyQA`** với bộ dữ liệu mock synthetic **1 tuần (11/11 → 17/11/2026)** và kết nối AI thật (dữ liệu tổng hợp + khóa QA tạm, đã xóa khóa khỏi thiết bị ngay sau khi chụp).

- **Thiết bị & môi trường QA:** LDPlayer 9 `WallyQA` (`index 1`, `emulator-5556`), Android 9, độ phân giải `780×1688 @320dpi` (`~390dp`), múi giờ `Asia/Ho_Chi_Minh`.
- **APK lúc chụp bộ ảnh này (`01`–`05`, `01b`, `10`):** bản nền Release `TimeWallet-MVP-v1.2.0-PRODUCTION-CANDIDATE-release.apk` (`8,407,976` bytes, SHA-256 `3B144E36EF3054A6AB0ACB1AF2815E5945CE54C207E1E01C8A9E312880BBB8AE`, trước đợt tích hợp 5 thành phần UI Jetpack Compose mới nhất trên APK `D500D410...` — kiểm tra runtime trên bản `D500D410...` hiện là `NOT_TESTED`, chi tiết trong [WALLY_V120_PRODUCTION_CANDIDATE.md](../../../WALLY_V120_PRODUCTION_CANDIDATE.md)).
- **Dữ liệu mock 1 tuần:** Nạp từ tệp cục bộ `out/wally_candidate_v120/mock_1week_ai_showcase.json` qua chức năng **`Nhập / khôi phục JSON`** (`Khôi phục toàn bộ`: `41` bản ghi trải đều 7 ngày `11/11–17/11`, `5` Tag, `2` Mục tiêu, `3` Ngân sách ưu tiên, `1` Daily Review, kèm xuyên đêm `UC-15`, khoảng nghỉ `BREAK_CONFIRMED`, hoạt động song song `PARALLEL_CONFIRMED`, `DOING` và `PLANNED`). Toàn bộ là dữ liệu synthetic QA; không chạm instance cá nhân của Owner (`index 0`). *(Thư mục `out/` được loại trừ khỏi Git theo `.gitignore`.)*
- **Logcat sau phiên chụp:** `FATAL EXCEPTION = 0`.
- **Trạng thái chưa kiểm thử runtime (`NOT_TESTED`):** Khởi động lại lạnh (`BOOT_COMPLETED`) trên `WallyQA` (dừng theo Rule 8 `AGENTS.md` vì `Ld9BoxSVC` còn giữ sau lần thoát phiên), Android 13–16 AVD (Rule 14 AEHD), và điện thoại thật của Owner.
- **Thư mục lịch sử:** [../ldplayer-android9/README.md](../ldplayer-android9/README.md) giữ nguyên bộ ảnh baseline `v1.0.11` (`versionCode 12`, commit `195a60c`).

## Ảnh tổng hợp 5 màn hình nổi bật (1 tuần + AI)

![Tổng hợp 5 màn hình nổi bật Time Wallet v1.2.0](_tong-hop_5-man-hinh.jpg)

## Danh sách ảnh chi tiết

| Tệp ảnh | Màn hình / Luồng nghiệp vụ | Điểm nhấn giao diện |
|---|---|---|
| [`01_tong-quan.png`](01_tong-quan.png) | **Tổng quan** | Ví thời gian hôm nay (`15h 13` đã ghi nhận + `Dự kiến 2h 32 · chưa tính vào đã ghi nhận`), nút thao tác nhanh, biểu đồ tròn 5 danh mục. |
| [`01b_wally-coach-ai.png`](01b_wally-coach-ai.png) | **Wally Coach AI (`7 ngày qua`)** | Số liệu tất định của ứng dụng kết hợp bài phân tích `Wally (AI)` trực tiếp trên 7/7 ngày (`cinb/claude-sonnet-4-6`), tách rõ `chốt xác nhận` / `khoảng nghỉ xen giữa` / `dự kiến`. |
| [`02_dong-ho.png`](02_dong-ho.png) | **Đồng hồ** | Chọn Tag `✓ 📘 Học AI`, nút `Clean · Xóa nhập liệu`, nút `Bắt đầu đo` và thẻ gợi ý phiên `Dopamine Time Dice`. |
| [`03_wally.png`](03_wally.png) | **Wally AI (Đa đề xuất)** | Trạng thái `✦ AI đã bật · ⚙`, bóc tách câu tiếng Việt thành **5 thẻ đề xuất** kèm gợi ý Tag `🏷` và nút `✓ Xác nhận toàn bộ (5 hoạt động)`. |
| [`04_nhat-ky.png`](04_nhat-ky.png) | **Nhật ký** | Timeline `Hôm nay, 17/11` (`Gồm +6h 30 chuyển tiếp từ hoạt động qua đêm`), thẻ `Dự kiến`, `Đang diễn ra`, `Khoảng nghỉ` và nút `⚙ Xác nhận / Điều chỉnh`. |
| [`05_phan-tich.png`](05_phan-tich.png) | **Phân tích (7 ngày)** | Tổng tuần `106h 40` (`7 ngày · Có giờ`), thông báo tự khử trùng `1h 00` hoạt động song song, đủ **7 cột `Th 4` → `Th 3`** và donut 7 ngày. |
| [`06_nhap-gio_manual.png`](06_nhap-gio_manual.png) | **Thêm giờ (`ManualEntrySheet`)** | Nhập khoảng giờ tự tính, cảnh báo trạng thái đang diễn ra + nhắc xác nhận, footer ghim cố định (`Clean · Xóa nhập liệu`, `Thoát`, `Lưu`). *(ảnh preview v1.2.0)* |
| [`07_xu-ly-chong-lan.png`](07_xu-ly-chong-lan.png) | **Xử lý hoạt động xen giữa (`OverlapResolutionDialog`)** | Phát hiện chồng lấn với các lựa chọn tách khoảng nghỉ, chạy song song, chỉnh giờ hoặc để sau. *(ảnh preview v1.2.0)* |
| [`08_xac-nhan-trang-thai.png`](08_xac-nhan-trang-thai.png) | **Xác nhận trạng thái (`ActivityConfirmationDialog`)** | Điều chỉnh giờ / kết quả thực tế, tiếp tục thêm thời gian, hủy hoạt động dự kiến hoặc để sau. *(ảnh preview v1.2.0)* |
| [`09_ban-nhap_tong-quan.png`](09_ban-nhap_tong-quan.png) | **Bản nháp đang nhập dở trên Tổng quan** | Thẻ khôi phục bản nháp tự lưu trên Tổng quan kèm nút `Clean · Xóa nháp`. *(ảnh preview v1.2.0)* |
| [`10_nhap-json_release.png`](10_nhap-json_release.png) | **Nhập & khôi phục dữ liệu (JSON)** | Hộp thoại `ImportJsonDialog` trên bản Release chọn chế độ `Gộp an toàn` / `Khôi phục toàn bộ`, không ghi đè khóa AI. |
