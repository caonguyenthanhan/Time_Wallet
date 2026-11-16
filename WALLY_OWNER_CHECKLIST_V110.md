# WALLY v1.1.0 — Danh sách kiểm tra trên điện thoại thật của Owner (~15 phút)

- **APK:** `out/wally_candidate_v110/TimeWallet-MVP-v1.1.0-PERSONAL-COACH-candidate-debug.apk`
- **SHA-256 phải khớp:** `373aeb7d645e4bf85e03bf9a197bec643bba8bcf83880a7c6ac178f7ce700f71`
- **Phiên bản:** `com.timewallet.mvp`, `versionName = 1.1.0`, `versionCode = 15`, DB schema `v4` (`MIGRATION_3_4`)
- **Bản quay lui (nếu cần):** `v1.0.13` (`bfc00ae9e444d5aaeee9d11ff58f1471bda16d8dbcf35cce5e96d731a9788a2b`) / `v1.0.12` (`1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8`)
- **Lưu ý trạng thái:** `DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED` **chưa** được cấp trước khi Owner kiểm thực tế.
- **Lưu ý cấu hình AI cho bản Preview:** Tuyến mặc định `cinb/gemini-3.8-flash` trên Foza hiện vẫn trả `HTTP 503` (upstream `[antigravity/gemini-3.8-flash-low] 403 TOS_VIOLATION` ghi nhận lúc `2026-11-16 23:57` ICT; app báo `"Máy chủ AI đang gặp lỗi (5xx)."` và không crash). Để trải nghiệm Chat & Coach trong khi tuyến Gemini chưa mở lại, vào `⚙ Cài đặt AI` và đổi **`Tên model`** sang **`cinb/claude-sonnet-4-6`**.

## Các bước kiểm tra nhanh trên điện thoại thật

1. **Cài đè giữ dữ liệu (`MIGRATION_3_4`):** Kiểm SHA-256 khớp mục trên, cài đè (không gỡ app). Mở app → `⋮` → `Thông tin & nhật ký lỗi` phải ghi `Time Wallet v1.1.0`; các bản ghi và ngân sách cũ vẫn còn nguyên.
2. **Tạo & quản lý Tag (`⋮ → Quản lý Tag` hoặc nút trên Tổng quan):** Tạo Tag mới (ví dụ `Chạy bộ`, `Đọc sách`, `Chơi game`, `Gym`). Thử tạo trùng tên viết hoa/không dấu (`chay bo`) → app phải báo trùng và không tạo đè. Đổi tên hoặc lưu trữ một Tag → các bản ghi cũ ở Nhật ký vẫn giữ nguyên dữ liệu lịch sử.
3. **Nhập thủ công (Khoảng giờ & `"Bây giờ"`):** Mở `Thêm giờ` → chọn Tag `Đọc sách` → chế độ `Khoảng giờ`, nhập `14:00` → `15:12`: app tự hiện **72 phút** (`1h 12p`) trước khi Lưu. Thử nhập giờ bắt đầu trước hiện tại và bấm **`Kết thúc: Bây giờ`** → lưu sang Nhật ký (`Có giờ`).
4. **Mục tiêu (`Mục tiêu của bạn → Quản lý`):** Tạo mục tiêu **Tối thiểu `30 phút/ngày` cho Tag `Chạy bộ`**. Ghi thử `30p` cho Tag `Gym` (cùng nhóm Vận động) → tiến độ mục tiêu `Chạy bộ` **không** được tăng. Sau đó ghi `30p` cho Tag `Chạy bộ` → thẻ mục tiêu cập nhật đạt `30p / 30p ✓`.
5. **Đồng hồ + Tag:** Mở tab **Đồng hồ** → chạm chip Tag `Chạy bộ` → `▶ Bắt đầu đo` → chạy vài giây → `■ Kết thúc & lưu` → Nhật ký có bản ghi `Đã đo` gắn đúng Tag `Chạy bộ`.
6. **AI Chat + Tag & hồi quy v1.0.13 (nhập khóa QA/cá nhân trong `⚙`, đặt `Tên model = cinb/claude-sonnet-4-6` nếu `cinb/gemini-3.8-flash` đang lỗi `5xx`):** Thử gửi `"Code từ lúc 14h đến giờ"`, `"Học 45p"` hoặc `"Đọc sách 25p"` → kiểm đề xuất (khoảng giờ/thời lượng), kiểm gắn Tag/danh mục đúng và **chưa** tự lưu cho tới khi bấm `Xác nhận toàn bộ`. Kiểm thêm bàn phím mềm tiếng Việt (`KBD-01`), bấm đúp `Gửi`, và câu làm rõ (`"Không phải 2 tiếng, học 3 tiếng"`).
7. **Wally Coach (`Tổng quan → Wally Coach`):** Bấm **`Hôm nay`** hoặc **`7 ngày qua`**. Kiểm tra số liệu app tự tính tách riêng các Tag cùng danh mục (vd `Đọc sách` vs `Chơi game`) và phần `(chưa gắn Tag)`; lời kể `Wally (AI)` không phán xét, không đoán cảm xúc/lý do và không bịa số. Nếu có thẻ **`Gợi ý mục tiêu (chưa tạo)`**, kiểm tra mục tiêu chỉ được thêm sau khi bấm **`Tạo mục tiêu`**.
8. **Dữ liệu & log:** `⋮ → Dữ liệu & sao lưu → Xuất JSON` xem trước để xác nhận không có khóa API xuất hiện trong bản xuất.
