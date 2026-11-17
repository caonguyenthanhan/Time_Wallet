# Checklist nghiệm thu Owner — candidate WALLY v1.0.9 (`versionCode 10`) — P0 F6

> **Quy tắc:** kết quả của v1.0.8 trở về trước không chuyển sang binary này. Ghi kết quả kèm SHA-256 thực của APK đã cài ở `WALLY_V109_ACCEPTANCE_RECORD.md`. Chỉ dùng dữ liệu thử; **không gỡ app** (gỡ sẽ xóa sổ giờ); **không gửi khóa API qua chat**; không emulator/ADB.
>
> **Điều kiện dừng của P0:** cài đè bản này rồi gửi lại **đúng câu đã làm app dừng đột ngột**. F6 chỉ được đánh dấu FIXED (cho đúng lỗi này) khi bước 4 qua trên máy thật.

## 1. Xác nhận binary (làm trước)

- APK: `out/wally_candidate_v109/TimeWallet-MVP-v1.0.9-WALLY-HOTFIX-candidate-debug.apk`
- SHA-256 phải là: `0f5f23d9b7eb718367d3d87a26bb3cbbc7fb2884e124163fc5b980bd116ca4e2`
```powershell
(Get-FileHash "out\wally_candidate_v109\TimeWallet-MVP-v1.0.9-WALLY-HOTFIX-candidate-debug.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
```
- [ ] SHA-256 khớp (nếu không khớp: dừng, không cài).

## 2. Ghi lại trước khi cài (để so sau cài)

- [ ] ⓘ đang ghi phiên bản: `Time Wallet v______` (v1.0.6 / v1.0.7 / v1.0.8)
- [ ] Số bản ghi trong Nhật ký: `____` · số ngân sách: `____` · khóa API đã nhập: `có/không` (không gửi khóa)
- [ ] Câu bạn đã gõ khi app dừng đột ngột (nếu nhớ): `__________` (không nhớ thì dùng "Học 45p")

## 3. Cài đè (không gỡ app)

- [ ] Chép APK sang điện thoại, mở tệp, chọn **Cài đặt/Cập nhật** (cùng khóa ký debug nên cài đè được lên v1.0.6–v1.0.8; versionCode 10 cao hơn tất cả).
- [ ] Nếu Android báo xung đột/không cài được: **không gỡ app**; chụp màn hình thông báo và dừng.
- [ ] Mở app: ⓘ ghi **`Time Wallet v1.0.9`**; số bản ghi và số ngân sách **y như bước 2**; cấu hình AI còn nguyên.

## 3b. Dọn nhật ký lỗi cũ (QA LDPlayer phát hiện: nhật ký lỗi **còn lại sau khi nâng cấp**)

- [ ] Mở ⓘ → **Nhật ký lỗi gần nhất**. Nếu thấy một bản ghi có dòng `time=` **trước** lúc bạn cài v1.0.9 (ví dụ crash `ExceptionInInitializerError … analyzeChatInput`) thì đó là bản ghi **cũ** của bản trước, không phải lỗi mới. Bấm **Xóa nhật ký** rồi **Đã hiểu** trước khi làm bước 4, để mọi bản ghi xuất hiện sau đó là thật.

## 4. Bước quyết định — lặp lại thao tác đã crash

- [ ] Vào tab **Wally**, gõ **đúng câu ở bước 2** (hoặc "Học 45p") rồi bấm **Tách đề xuất** (gửi). Làm **3 lần** (đóng/mở lại app giữa các lần nếu muốn).
  - **Kỳ vọng:** app **không dừng đột ngột**. Có khóa AI: hiện thẻ đề xuất ghi "chờ bạn xác nhận" (**không bấm Xác nhận**) hoặc thẻ trả lời; chưa có khóa: thẻ "Wally cần khóa API và bật AI…" và input được giữ.
  - Ghi: `app có dừng không: __ / __ / __`.
- [ ] Kết thúc bằng **Bỏ hết** các đề xuất; Nhật ký **không tăng**.

## 5. Kiểm nhanh các câu bắt buộc (cần khóa AI của Owner)

- [ ] **"Hủy tất cả đề xuất"** (khi đang có đề xuất chờ): đề xuất biến mất, app không dừng; khi không có đề xuất: thông báo không có gì để hủy.
- [ ] **"Học 45p"**: 1 đề xuất 45 phút, chờ xác nhận; không có câu chat nào của Wally nói đã lưu.
- [ ] **"Xin chào"**: Wally đáp tự nhiên, không đề xuất.
- [ ] **"Không học hai tiếng nhưng làm việc hai tiếng"**: chỉ còn **Làm việc 2h** (không có "Học"); **không bấm Xác nhận** → Bỏ hết.
- [ ] **"Học 45p và hôm nay tôi có bao nhiêu giờ chưa gắn giờ?"**: có thẻ đề xuất **và** thẻ số liệu; số không cộng 45p đang chờ.

## 6. Không hồi quy (chạy nhanh)

- [ ] Ghi thủ công 1 bản ghi nhỏ rồi xóa nó; Đồng hồ: bấm giờ vài giây rồi dừng, lưu đúng; Nhật ký và Ngân sách mở bình thường.
- [ ] **Không khóa, không mạng:** (chế độ máy bay, xóa khóa trong ⚙) mở app, ghi thủ công, đồng hồ, Nhật ký, Ngân sách dùng bình thường; ở tab Wally chỉ hiện hướng dẫn cấu hình, không lỗi treo.
- [ ] Khóa API còn dùng được sau khi đóng và mở lại app.

## 7. Nếu có sự cố

- **Thấy thẻ "Bộ kiểm tra an toàn của Wally không khởi tạo được trên máy này…"**: đây là cơ chế chứa lỗi mới (không sập, không gửi AI, không tạo đề xuất; input được giữ). Nghĩa là trên máy này còn một nguyên nhân khác. Mở ⓘ → **Nhật ký lỗi gần nhất** → **Sao chép** → gửi kèm câu đã gõ và SHA-256. Vẫn dùng được Ghi thủ công/Đồng hồ/Ngân sách.
- **App vẫn dừng đột ngột:** mở lại, ⓘ → Nhật ký lỗi gần nhất → Sao chép, gửi kèm thao tác trước đó, phiên bản ⓘ và SHA-256. Đừng cố thử thêm.

## 8. Kết luận (điền)

Thiết bị / Android: `______` · SHA-256 APK đã cài: `______` (= `0f5f23d9…ca4e2`) · gửi lại đúng câu đã crash 3 lần, app dừng: `[ ] không / [ ] có (đính kèm Nhật ký lỗi)` · Reviewer re-check: `[ ] PASS / [ ] REJECT` · Owner: `[ ] PASS / [ ] FAIL (kèm bằng chứng)` · F6 (chữ ký `EIIE ← PatternSyntaxException ← CancelCommandParser.<clinit>`): `[ ] FIXED / [ ] NOT_FIXED` · F6 nói chung: `UNRESOLVED` · Ghi chú: `______`
