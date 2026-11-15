# WALLY OWNER CHECKLIST — v1.0.10 (Phone Acceptance & Live Eval v1.2)

- **Candidate APK cần cài trên điện thoại Owner:** `out/wally_candidate_v1010/TimeWallet-MVP-v1.0.10-WALLY-HOTFIX-candidate-debug.apk`
- **Full SHA-256 bắt buộc đối chiếu trước/sau khi cài:** `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b`
- **Package / Phiên bản:** `com.timewallet.mvp`, `versionName = 1.0.10`, `versionCode = 11`
- **Lưu ý quan trọng về LDPlayer:** **KHÔNG cần** và **KHÔNG yêu cầu** Owner phục hồi instance LDPlayer cá nhân (`index 0`). Mọi QA giả lập đã tách riêng sang `WallyQA` (`index 1`) chỉ dùng dữ liệu synthetic.

---

## 1. Kiểm tra cài đặt & bảo toàn dữ liệu trên máy thật

1. Kiểm tra SHA-256 file APK trước khi chuyển sang điện thoại (`1d834a83...`).
2. Cài đè `v1.0.10` lên bản `v1.0.9` đang có trên máy thật (không gỡ cài đặt).
3. Mở ứng dụng, kiểm tra dữ liệu Nhật ký / Ngân sách / Khóa AI đã lưu (nếu có) vẫn còn nguyên.
4. Bấm nút `(i)` ở góc trên bên phải:
   - Xác nhận tiêu đề hiển thị **`Time Wallet v1.0.10`**.
   - Nếu máy có log lỗi cũ từ bản trước chưa ghi `version=`: xác nhận phần header hiển thị đúng thời điểm lỗi và **`Phiên bản khi lỗi: Không xác định`** (tuyệt đối không gắn nhầm `v1.0.10`), nội dung stack trace bên dưới không bị mất.

## 2. Kiểm tra sửa lỗi UI trên điện thoại thật

1. **Tab Phân tích (`Phân tích`):**
   - Kiểm tra tiêu đề **`Ngân sách thời gian`** và nút **`+ Đặt ngân sách`** nằm gọn cùng hàng, không bị bóp nút xuống nhiều dòng; thử tăng cỡ chữ hệ thống lên lớn (~1.3x) nếu muốn kiểm chứng thêm.
   - Kiểm tra từng thẻ ngân sách: tên danh mục rõ ràng, hàng chip (`Theo tuần`/`Theo ngày`, `★ Ưu tiên`, `Còn ...`/`Vượt ...`) đầy đủ, câu `"Đã ghi nhận: ... / Mục tiêu: ..."` đọc hết trên 1–2 dòng rõ ràng không bị ép thành cột 1 chữ.
2. **Tab Nhật ký (`Nhật ký`):**
   - Kiểm tra các hoạt động có tên dài (ví dụ > 30 ký tự) hiển thị tối đa 2 dòng đọc được rõ tên hoạt động, không bị cắt mất nội dung hoặc ép chồng lên nút Sửa / Xóa.
3. **Hộp thoại xác nhận xóa Ngân sách (2 luồng):**
   - **Từ thẻ Ngân sách (nút `×`):** bấm `×` -> hiện hộp thoại `Xóa ngân sách?` -> bấm `Hủy` (thẻ còn nguyên) -> bấm lại `×` và bấm `Xóa` (chỉ xóa mục tiêu ngân sách, bản ghi lịch sử giữ nguyên).
   - **Từ bên trong hộp thoại Sửa Ngân sách:** mở sửa 1 ngân sách, đổi thử chu kỳ/số giờ (ví dụ `5h`), bấm `Xóa ngân sách danh mục này` -> xác nhận chỉ có duy nhất hộp thoại `Xóa ngân sách?` hiển thị (không đè 2 dialog lên nhau) -> bấm `Hủy` -> **xác nhận quay lại đúng hộp thoại Sửa Ngân sách với giá trị `5h` vừa chỉnh vẫn còn**.

## 3. Kiểm tra F6 & Bàn phím thật (`KBD-01`)

1. Sau cold start (thoát hẳn app từ Recents rồi mở lại), mở tab **Wally**:
   - Thử tương tác bàn phím mềm (`KBD-01`): gõ tiếng Việt có dấu trên Gboard/Samsung Keyboard/Laban Key, kiểm tra thanh điều hướng và nút **`Tách đề xuất ↗`** không bị che khuất hoặc kẹt cuộn.
2. Kiểm tra smoke F6:
   - Nhập `"Xin chào"` -> gửi.
   - Nhập `"Học 45p"` -> gửi.
   - Nhập `"Hủy tất cả đề xuất"` -> gửi.
   - Xác nhận app không crash, không đứng đơ, phản hồi trung thực với trạng thái bật/tắt AI và có/không key. (Nếu chẳng may app crash, mở lại vào `(i)` chụp ảnh log có gắn `version=1.0.10 (11)`).

## 4. Nghiệm thu Live Eval v1.2 (khi Owner bật AI bằng key riêng)

1. Giữ nguyên kịch bản chuẩn trong `WALLY_LIVE_EVAL_PLAN_v1.2.md` (SHA-256: `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0`).
2. Không sử dụng kết quả Live Eval của các bản `v1.0.6`–`v1.0.9` thay cho `v1.0.10`; ghi kết quả nghiệm thu `v1.0.10` vào `WALLY_V1010_ACCEPTANCE_RECORD.md`.
