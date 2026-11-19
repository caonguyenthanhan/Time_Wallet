# Ảnh chụp giao diện chuẩn (`ui-preview`) — Time Wallet `v2.0.0` (`versionCode 18`)

Toàn bộ `34` ảnh PNG (`01`–`33`, gồm `10b`) và banner [`_tong-hop_wally-2.0.jpg`](_tong-hop_wally-2.0.jpg) được chụp trực tiếp trên **APK Release ký số Production Candidate (`v2.0.0`, `versionCode 18`, SHA-256 `BEDEE1904C5805E2C7DEB9D83E8B0AD0A30C385BDD044FDA5B36EB8C610219CA`, `9,009,774` bytes)** trên môi trường QA tách biệt **`WallyQA`** (`index 1`, `emulator-5556`, Android 9, `780×1688 @320dpi`, múi giờ `Asia/Ho_Chi_Minh`) với dữ liệu synthetic 1 tuần không chồng lấn. Không chạm instance cá nhân của Owner (`index 0`).

- **Điểm kiểm chứng trên `BEDEE190…`:** Bóng chat Wally Pop-on kéo thả tự hít mép có huy hiệu `✦`, mở khóa cuộn trang & thu hẹp lề ngang màn Lịch, cột 3 ngày/Tuần cuộn ngang mượt giữ cố định trục giờ trái, thẻ chi tiết Tháng/Sổ cái phóng to, biểu đồ 24h × 7 ngày cao `228.dp`, `FlowRow` không cắt nút lọc/gợi ý, TopBar giữ trọn `"time wallet."` khi đồng hồ Focus đang chạy, và ngăn Công cụ chia 2 cột kèm thẻ cài đặt gộp nhóm. *(Đối chiếu chi tiết xem [WALLY_V200_CANDIDATE.md](../../../WALLY_V200_CANDIDATE.md).)*
- **Dọn môi trường QA:** Đã xác minh `FATAL EXCEPTION = 0` trong `logcat`, khôi phục `persist.sys.timezone = Asia/Ho_Chi_Minh`, `pm clear com.timewallet.mvp`, xóa tệp QA trên emulator và tắt `WallyQA` (`ldconsole quit --index 1`).
- **Trạng thái chưa kiểm thử runtime (`NOT_TESTED`):** Live AI gọi API thật trên `v2.0.0` (chỉ kiểm chứng khối lọc/chặn offline), khởi động lại lạnh `BOOT_COMPLETED`, Android 13–16 AVD (Rule 14 AEHD), và thiết bị thật của Owner. *(Kiểm thử nâng cấp tại chỗ `v1.2.0 → v2.0.0` đã chạy trước đó trên baseline `50EBDFFF…` với `FATAL EXCEPTION = 0`, schema không đổi sang `BEDEE190…`; ảnh baseline `v1.0.11` giữ tại [../ldplayer-android9/README.md](../ldplayer-android9/README.md).)*

## Ảnh tổng hợp 5 màn hình nổi bật (`Wally 2.0` Light + Dark)

![Tổng hợp Time Wallet v2.0.0 Light và Dark Mode](_tong-hop_wally-2.0.jpg)

<sub>5 màn hình ghép trực tiếp từ ảnh thật: `01` Lịch trình Ngày (Light + Pop-on) · `16` Công cụ 2 cột gọn gàng (Light + Pop-on) · `22` Tập trung gắn Task & TopBar không cắt chữ (Dark) · `24` Wally Pop-on Overlay với FlowRow gợi ý (Dark) · `26` Thống kê 24h × 7 ngày phóng to (Dark).</sub>

## Danh sách 34 ảnh chi tiết (`BEDEE190…`)

| Tệp ảnh | Bản chụp | Màn hình / Luồng nghiệp vụ | Điểm nhấn đã kiểm chứng |
|---|---|---|---|
| [`01_lich-trinh-hom-nay_light.png`](01_lich-trinh-hom-nay_light.png) | `BEDEE190` | **Lịch trình Ngày (Hôm nay · `19/11` · Light)** | Thanh Ví 24h thu gọn 1 dòng, Timeline phóng to rõ chữ, bóng chat Wally Pop-on nổi góc phải. |
| [`02_lich-trinh-ngay_light.png`](02_lich-trinh-ngay_light.png) | `BEDEE190` | **Lịch trình Ngày (`Hôm qua · 18/11` · `Hôm nay` pill)** | Nút pill `Hôm nay` rõ nét cạnh ngày, bản ghi xuyên đêm `Ngủ xuyên đêm (UC-15)` + Pop-on bubble. |
| [`03_lich-trinh-3ngay_light.png`](03_lich-trinh-3ngay_light.png) | `BEDEE190` | **Lịch trình 3 ngày (`19/11–21/11`)** | 3 cột rộng `112.dp`/ngày cuộn ngang giữ cố định trục giờ trái, kèm tổng TT & KH từng ngày. |
| [`04_lich-trinh-tuan_light.png`](04_lich-trinh-tuan_light.png) | `BEDEE190` | **Lịch trình Tuần (`16/11–22/11`)** | Cột tuần rộng `98.dp`/ngày dễ đọc tiêu đề block 2 dòng, cuộn ngang mượt giữ trục giờ trái. |
| [`05_lich-trinh-thang_light.png`](05_lich-trinh-thang_light.png) | `BEDEE190` | **Lịch trình Tháng (`11/2026`)** | Tổng quan tháng (`Trống`, `KH 3h30p`, chấm cam chờ xác nhận) kèm thẻ chi tiết `Hôm nay · 19/11` phóng to phía dưới. |
| [`06_lich-trinh-menu_light.png`](06_lich-trinh-menu_light.png) | `BEDEE190` | **Menu `⋮` điều khiển Lịch** | Gom `＋ Ghi mới hoạt động`, `Tìm & lọc danh mục`, `Hiện chú giải 4 trạng thái` gọn vào menu ngữ cảnh. |
| [`07_cong-viec_light.png`](07_cong-viec_light.png) | `BEDEE190` | **Công việc & Inbox (Light)** | Hàng tab module (`5` chip) & bộ lọc (`5` chip) dùng `FlowRow` không bị cắt mép, thẻ Task 2 dòng dễ quét + Pop-on. |
| [`08_thoi-quen_light.png`](08_thoi-quen_light.png) | `BEDEE190` | **Thói quen (`Habits` · Light)** | Tóm tắt `1/1`, tự khớp từ Sổ cái (`⚡`), nhãn `T6..T5` dưới 7 chấm và gợi ý thói quen lành mạnh. |
| [`09_muc-tieu_light.png`](09_muc-tieu_light.png) | `BEDEE190` | **Mục tiêu hợp nhất (`MIN`/`MAX`)** | Hợp nhất Goal tối thiểu theo ngày & Ngân sách tuần tối đa trong 1 danh sách thống nhất. |
| [`10_kanban_light.png`](10_kanban_light.png) | `BEDEE190` | **Bảng Kanban (`Cần làm / Đang làm`)** | Cột Kanban rộng `74%` chiều ngang, chuyển cột & bấm `▶ Đo` trực tiếp từ thẻ. |
| [`10b_ma-tran-eisenhower_light.png`](10b_ma-tran-eisenhower_light.png) | `BEDEE190` | **Ma trận Eisenhower (`Q1–Q4`)** | Phân nhóm theo mức Gấp & Quan trọng với thao tác đo nhanh từng việc. |
| [`11_tap-trung_light.png`](11_tap-trung_light.png) | `BEDEE190` | **Tập trung (`Stopwatch / Pomodoro` · Light)** | Bộ đếm kép, xúc xắc gợi ý, công tắc `Ghim tập trung` + nút `Chọn`, bóng Pop-on không che nút chính. |
| [`12_ghim-focus-cho.png`](12_ghim-focus-cho.png) | `BEDEE190` | **Ghim Focus Notification (Chờ)** | Kiểm chứng độc lập notification `id=910301` gợi ý `Giờ này tuần trước: Deep Work — Thiết kế CSDL`. |
| [`13_ghim-focus-dang-do.png`](13_ghim-focus-dang-do.png) | `BEDEE190` | **Ghim Focus Notification (Đang đo)** | Cập nhật tiêu đề trực tiếp `Đang tập trung: Deep Work — Thiết kế CSDL · Bắt đầu lúc 09:07`. |
| [`14_ghi-nhanh-offline_light.png`](14_ghi-nhanh-offline_light.png) | `BEDEE190` | **Thu thập nhanh (`+ Ghi` Offline)** | Nhận diện `"Doc sach 20 phut"` thành `Ghi thời lượng (20p) · Học tập`, `FlowRow` nút chọn chế độ không tràn mép. |
| [`15_thong-ke-24h_light.png`](15_thong-ke-24h_light.png) | `BEDEE190` | **Thống kê 24h (`7 ngày · 4 tầng`)** | Biểu đồ `228.dp` cao thoáng 4 tầng `Mục tiêu · Phi mục tiêu · Lãng phí · Chưa rõ`, tách riêng `Chưa gắn giờ`. |
| [`16_cong-cu_light.png`](16_cong-cu_light.png) | `BEDEE190` | **Mở rộng › Công cụ (2 cột gọn gàng)** | Bố cục mới: thẻ nổi bật Ghim Focus, 2 cụm lưới 2 cột (`KẾ HOẠCH & MỤC TIÊU`, `TRỢ LÝ & TẬP TRUNG`) và thẻ `DỮ LIỆU & CÀI ĐẶT` gộp. |
| [`17_cai-dat_light.png`](17_cai-dat_light.png) | `BEDEE190` | **Hồ sơ & Cài đặt (Light)** | Chọn chủ đề `Theo máy / Sáng / Tối`, bật tắt module Kanban/Eisenhower/Habits/Pomodoro/Dice, quyền AI. |
| [`18_xuat-markdown_obsidian.png`](18_xuat-markdown_obsidian.png) | `BEDEE190` | **Xuất Markdown (Obsidian)** | Sinh báo cáo thuần cục bộ theo ngày `2026-11-19` (`11` hoạt động + `Chưa gắn giờ 30p`) để dán sang Obsidian. |
| [`19_lich-trinh-tuan_dark.png`](19_lich-trinh-tuan_dark.png) | `BEDEE190` | **Lịch trình Tuần (Dark Mode)** | Tiêu đề `Tuần 16/11–22/11` & cột tuần rộng dễ đọc trên nền tối `#0F1A15`, hiện Pop-on bubble. |
| [`20_chon-gio-trong_dark.png`](20_chon-gio-trong_dark.png) | `BEDEE190` | **Chọn khung giờ trống (Dark Mode)** | Khối chọn `✓ Đã chọn 17:00–18:00` rõ nét cạnh các block thực tế tiếng Việt chuẩn dấu trên nền tối. |
| [`21_cong-viec-subtasks_dark.png`](21_cong-viec-subtasks_dark.png) | `BEDEE190` | **Công việc & Checklist Subtasks (Dark Mode)** | Tab & chip lọc `FlowRow` đủ chữ 100%, chạm thẻ mở nhanh checklist con (`1/2 bước`) và ô thêm bước. |
| [`22_tap-trung-task_dark.png`](22_tap-trung-task_dark.png) | `BEDEE190` | **Tập trung gắn Task & Subtasks (Dark Mode)** | TopBar giữ nguyên chữ `"time wallet."` + `● 00:02` không bị cắt, đồng bộ mục tiêu `45p` & Subtasks dưới đồng hồ. |
| [`23_da-xong-viec-dialog_dark.png`](23_da-xong-viec-dialog_dark.png) | `BEDEE190` | **Hộp thoại `"Đã xong công việc!"` (Dark)** | Surface tối `#16241E` tương phản cao kèm các lựa chọn ghi `~45p vào Sổ` hoặc `Chỉ đánh dấu xong`. |
| [`24_wally-overlay_dark.png`](24_wally-overlay_dark.png) | `BEDEE190` | **Wally Pop-on Overlay (Dark Mode)** | Mở từ bóng chat Wally nổi; `4` chip gợi ý (`Ghi hoạt động`, `Hôm nay dùng bao nhiêu?`, `So với tuần trước`, `+ Ghi thủ công`) bọc 2 dòng bằng `FlowRow` không cắt chữ. |
| [`25_wally-chan-quyen-ai_dark.png`](25_wally-chan-quyen-ai_dark.png) | `BEDEE190` | **Chặn Quyền đọc Công việc AI (Dark Mode)** | Khi tắt quyền `Công việc & Inbox`, bấm `Xếp Inbox (1)` bị chặn cục bộ tức thì, 0 dữ liệu việc rò rỉ. |
| [`26_thong-ke-24h_dark.png`](26_thong-ke-24h_dark.png) | `BEDEE190` | **Thống kê 24h & Tiến độ mục tiêu (Dark)** | Biểu đồ `228.dp` 7 ngày sắc nét, bảng tổng hợp 4 tầng tương phản cao trên nền tối + Pop-on bubble. |
| [`27_cai-dat_dark.png`](27_cai-dat_dark.png) | `BEDEE190` | **Hồ sơ & Cài đặt (Dark Mode)** | Chuyển nhanh chủ đề `Theo máy / Sáng / Tối` và bật tắt các module mở rộng trên nền tối đồng nhất. |
| [`28_nhap-json_dark.png`](28_nhap-json_dark.png) | `BEDEE190` | **Hộp thoại Nhập & khôi phục JSON (Dark)** | Chữ, khung nhập và 2 chế độ `Gộp an toàn / Khôi phục toàn bộ` hiển thị rõ nét trên nền tối. |
| [`29_xac-nhan-gio-dialog_dark.png`](29_xac-nhan-gio-dialog_dark.png) | `BEDEE190` | **Đối soát kết thúc giờ (Dark Mode)** | Màn `Đã tới giờ xác nhận` (`Thực hành AI Time Coach & Wally`) với đủ 5 nhánh đối soát giờ thực tế. |
| [`30_thoi-quen_dark.png`](30_thoi-quen_dark.png) | `BEDEE190` | **Thói quen (Dark Mode)** | Chuỗi 7 ngày (`1/1`) và chip gợi ý cân đối trên nền tối. |
| [`31_muc-tieu_dark.png`](31_muc-tieu_dark.png) | `BEDEE190` | **Mục tiêu hợp nhất (Dark Mode)** | Thanh tiến độ mục tiêu tối thiểu ngày & hạn mức tuần rõ nét trên nền tối. |
| [`32_kanban_dark.png`](32_kanban_dark.png) | `BEDEE190` | **Bảng Kanban (Dark Mode)** | Cột rộng thoáng dễ quét, nút chuyển cột & `▶ Đo` giữ nguyên độ tương phản trong chế độ tối. |
| [`33_ma-tran-eisenhower_dark.png`](33_ma-tran-eisenhower_dark.png) | `BEDEE190` | **Ma trận Eisenhower (Dark Mode)** | Phân chia 4 góc phần tư `Q1–Q4` rõ viền và dễ đọc trên nền tối. |
