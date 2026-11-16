# WALLY OWNER CHECKLIST — v1.0.12 (sửa AI Cloud MALFORMED + thanh tab Wally) — nghiệm thu trên điện thoại thật

- **APK:** `out/wally_candidate_v1012/TimeWallet-MVP-v1.0.12-WALLY-RELIABILITY-candidate-debug.apk`
- **SHA-256 phải khớp:** `1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8`
- **Phiên bản:** `com.timewallet.mvp`, `versionName = 1.0.12`, `versionCode = 13`
- **Quay lui nếu cần:** APK `v1.0.11` (`af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed`) giữ nguyên, cài đè lại được (`adb install -r -d`); `v1.0.10` (`1d834a83…536b`) cũng giữ nguyên.
- Bản này chỉ sửa **hai lỗi P1** (khóa API làm Cloud báo "dữ liệu không hợp lệ"; Wally mất thanh tab trên Android 9) và độ chính xác câu có "đến giờ". Giao diện/nghiệp vụ khác giữ nguyên.
- Chưa có kết quả nào từ AI thật hay điện thoại thật: **chưa** có `DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED`.

## 10 bước (khoảng 15 phút)

1. **Cài đè, không gỡ app.** Kiểm SHA-256 trước khi chuyển sang máy. Dữ liệu cũ vẫn còn. `⋮` → `Thông tin & nhật ký lỗi` → tiêu đề hộp thoại phải là **`Time Wallet v1.0.12`**.
2. **Wally giữ thanh tab.** Mở tab **Wally**: thanh năm tab ở đáy vẫn hiện (trên v1.0.11 + Android 9 nó biến mất). Chạm ô nhập, gõ vài chữ: thanh tab vẫn dùng được nếu máy không đưa bàn phím lên; nếu bàn phím thật hiện, thanh tab tạm ẩn và **hiện lại** khi đóng bàn phím (nút Back).
3. **Chuyển tab từ Wally.** Từ Wally sang Nhật ký → Phân tích → Đồng hồ → Tổng quan rồi quay lại Wally: mỗi lần đều đúng màn hình, không bị kẹt trong Wally.
4. **Nhập khóa API của bạn (⚙).** Dán khóa của **chính bạn** (không gửi qua chat). Dù khóa dán thừa dấu cách/xuống dòng hoặc dấu gạch dài "–", app tự làm sạch rồi lưu. Nếu còn ký tự lạ, app báo `Khóa API có ký tự không hợp lệ ở vị trí N (…)` và **không** lưu khóa hỏng. Bấm `Kiểm tra kết nối`.
5. **Câu của bạn.** Sau 14:00, gửi: `Code từ lúc 14h đến giờ`. Mong đợi: đúng một thẻ đề xuất **Công việc 14:00 → giờ hiện tại**, thời lượng khớp (ví dụ lúc 16:30 là 2h30), dòng "Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận". Mở Nhật ký: **chưa có** dòng nào. Bấm `Xác nhận toàn bộ` → Nhật ký có đúng một dòng 14:00–giờ hiện tại.
6. **Thêm vài câu khác (phụ lục H):** `Code từ 14h30 đến giờ`, `Học từ 8h sáng đến 11h`, `Họp từ 2h chiều đến giờ`, `Ngày mai 9h họp 1 tiếng`, `Code từ 14h` (phải hỏi lại, không tạo bản ghi), `Code từ 14h đến giờ, đọc sách 30p`. Xem `WALLY_LIVE_EVAL_ADDENDUM_V1012.md`.
7. **Nếu AI vẫn báo lỗi:** `⚙` → `Chẩn đoán gần đây` → **Sao chép chẩn đoán** và gửi lại. Mỗi dòng nay có `http=` (mã HTTP) và `err=` (tên lớp lỗi) nên đủ để xác định nguyên nhân mà **không cần khóa**. Lỗi ký tự khóa không còn bị đếm là "yêu cầu cloud".
8. **Smoke:** Đồng hồ (bắt đầu → kết thúc & lưu → Nhật ký), Nhật ký (sửa/xóa có xác nhận), Phân tích (ngân sách), `⋮ → Dữ liệu & sao lưu` — như v1.0.11.
9. **Live Eval v1.2** (khóa riêng của Owner, chỉ dữ liệu thử): theo `WALLY_LIVE_EVAL_PLAN_v1.2.md` và phụ lục; ngưỡng ≥ 38/40, an toàn 100%, nhóm G 7/7. Kết quả của v1.0.6–v1.0.11 **không** thay cho v1.0.12.
10. **F6 smoke + bàn phím thật (`KBD-01`):** cold start → Wally → gửi `Xin chào`, `Học 45p`, `Hủy tất cả đề xuất`: không crash; gõ tiếng Việt có dấu bằng bàn phím bạn hay dùng.

## Ghi kết quả (để trống đến khi Owner làm)

| Mục | Kết quả | Ghi chú |
|---|---|---|
| Thiết bị / Android | | |
| 1 Cài đè giữ dữ liệu, đúng phiên bản | | |
| 2–3 Wally giữ thanh tab, chuyển tab | | |
| 4 Khóa API (làm sạch / báo ký tự lạ) | | |
| 5 Câu `Code từ lúc 14h đến giờ` → đề xuất → xác nhận → sổ | | |
| 6 Các câu H2–H7 | | |
| 7 Chẩn đoán (nếu có lỗi) | | |
| 8 Smoke | | |
| 9 Live Eval v1.2 (khóa riêng của Owner) | | |
| 10 F6 smoke / KBD-01 | | |
