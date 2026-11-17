# WALLY v1.0.13 — Danh sách kiểm tra trên điện thoại thật của Owner (≤ 12 bước, ~20 phút)

**Chỉ dùng khi** APK đã được xác nhận bằng SHA-256 (mục 0) và bộ Live Eval trên chính APK đó đã đạt (xem `WALLY_V1013_CLOSURE_DIGEST.md`). Dữ liệu thử, khóa **QA mới** (không dùng khóa cũ bị lộ).

0. **Kiểm APK:** `Get-FileHash <apk> -Algorithm SHA256` phải khớp `out\wally_candidate_v1013\APK_SHA256.txt`. Cài đè (không gỡ cài đặt). ⓘ phải ghi `Time Wallet v1.0.13`.
1. **Khóa API:** ⚙ → dán khóa QA mới (dán thừa dấu cách/xuống dòng được app tự làm sạch; ký tự điều khiển/chữ có dấu thì báo "ký tự không hợp lệ ở vị trí N") → **Kiểm tra kết nối**: phải báo thành công; thử khóa sai: phải nói rõ 401/403, không nói "AI trả dữ liệu hỏng".
2. **Câu của Owner:** gõ `Code từ lúc 14h đến giờ` (sau 14:30) → đúng 1 đề xuất Công việc 14:00 → giờ hiện tại, **chưa** vào Nhật ký → Xác nhận → đúng 1 dòng. Bấm Xác nhận hai lần nhanh: vẫn 1 dòng.
3. `Ngủ lúc 10h tối 8h` → đề xuất Ngủ bắt đầu 22:00, 8 giờ (đánh dấu Dự kiến nếu chưa đến 22:00).
4. `Bỏ ghi chú 20 phút` → đề xuất 20 phút (không bị coi là lệnh hủy).
5. **Câu hỏi làm rõ có ích:** `Không phải 2 tiếng, học 3 tiếng` và `Học 2h, à không, 3h` → Wally hỏi lại **một câu hỏi cụ thể** (không phải thông báo chung "Chưa có gì được lưu…"); không có đề xuất nào.
6. `Code từ 14h` (chỉ có giờ bắt đầu) → Wally hỏi làm rõ, không đoán.
7. **Bấm đúp Gửi:** chỉ gửi **một** yêu cầu, không bị hủy; chạm ✕ Hủy ngay lập tức sau khi gửi (trong ~0,7 s) không hủy; hủy sau đó (≥ 1 s) hủy ngay và ô nhập còn nguyên; hủy rồi gửi lại nhanh vẫn được nhận.
8. **Hạn chót 45 s:** bật chế độ máy bay → gửi `Học 45p` → lỗi ngắn, có Thử lại và Ghi thủ công, ô nhập còn; tắt máy bay → Thử lại chạy bình thường.
9. **Điều hướng:** mở Wally, gõ, đóng bàn phím, Back, chuyển 5 tab; Back ở Tổng quan thoát app như cũ.
10. **Ghi sổ:** đồng hồ → dừng → lưu → Nhật ký; nhập thủ công 30p; xóa có hỏi xác nhận.
11. **Dữ liệu:** xuất JSON (⇩) xem trước: không có khóa; dữ liệu cũ vẫn còn sau khi cài đè.
12. **Ghi lại:** mẫu/Android của máy, SHA đã kiểm, kết quả từng bước, bản sao **Chẩn đoán gần đây** (⚙) sau mỗi nhóm ≤ 15 yêu cầu. Không gửi khóa qua chat. Nếu bước 2 lỗi lần nữa: bản sao chẩn đoán đủ để xác định nguyên nhân (không cần khóa).

**Chưa tuyên bố cho tới khi Owner ghi nhận:** `DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED`.
