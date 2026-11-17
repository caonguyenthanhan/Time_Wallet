# Checklist nghiệm thu Owner — candidate WALLY v1.0.8 (`versionCode 9`)

> **Quy tắc:** kết quả của v1.0.7, v1.0.6 không chuyển sang binary này. Ghi kết quả kèm SHA-256 thực của APK đã cài ở `WALLY_V108_ACCEPTANCE_RECORD.md`. Chỉ dùng dữ liệu thử; không gỡ app; **không gửi khóa API qua chat**; không emulator/ADB.

## 1. Xác nhận binary (làm trước)

- APK: `out/wally_candidate_v108/TimeWallet-MVP-v1.0.8-WALLY-HOTFIX-candidate-debug.apk`
- SHA-256 phải là: `e13c35f49ae474a57c79c5be269307cb8e5f7d3a410e75528a9e553683cd62a5`
```powershell
(Get-FileHash "out\wally_candidate_v108\TimeWallet-MVP-v1.0.8-WALLY-HOTFIX-candidate-debug.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
```
- [ ] SHA-256 khớp; sau khi cài đè, ⓘ ghi `Time Wallet v1.0.8`; số bản ghi và ngân sách y như trước khi cài.

## 2. Trạng thái giao dịch do ứng dụng nói (V106-N1) — cần khóa AI của Owner

Sau mỗi câu: **không bấm Xác nhận**; kiểm Nhật ký chưa tăng; cuối mỗi mục bấm "Bỏ hết".
- [ ] **Ghi nhận ("Học 45p"):** thẻ đề xuất ghi **"1 hoạt động đề xuất · chờ bạn xác nhận"** và "Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận."; **không** có câu chat nào của Wally nói đã lưu/xong.
- [ ] **Vừa ghi vừa hỏi ("Học 45p và hôm nay tôi có bao nhiêu giờ chưa gắn giờ?"):** có thẻ đề xuất **và** thẻ số liệu; dòng đầu thẻ số liệu nói số liệu chỉ gồm bản ghi đã lưu; số **không** cộng 45p đang chờ; không có lời AI khẳng định kết quả.
- [ ] **Hỏi trạng thái khi còn đề xuất chờ ("Ghi chưa?", "Đã lưu chưa?"):** thẻ có **tiêu đề do ứng dụng** "Chưa có gì được lưu. Còn N đề xuất đang chờ bạn xác nhận." (N đúng số thẻ đang chờ); lời AI không hiện.
- [ ] **Hỏi trạng thái khi không còn đề xuất:** tiêu đề "Chưa có gì được lưu: …"; dòng nói "Hiện không có đề xuất nào đang chờ…".
- [ ] **Câu ghi nhận mà AI không tạo đề xuất (thử "Ghi giúp mình việc học", không nêu thời lượng):** thẻ báo **chưa lưu** và yêu cầu nêu rõ hoạt động/thời lượng hoặc dùng Ghi thủ công; không báo thành công.
- [ ] **Sau khi bấm Xác nhận:** chỉ toast "Đã lưu…" của ứng dụng; thẻ trạng thái cũ ("Còn N đề xuất…") **biến mất**, không còn số cũ.
- [ ] **Hội thoại tự nhiên:** "Xin chào", "Cảm ơn Wally" (khi không có đề xuất chờ) vẫn nhận lời đáp tự nhiên của Wally.
- [ ] Chụp mọi thẻ Wally có vẻ báo đã lưu mà **không** do ứng dụng tạo, kèm câu đã gõ.

## 3. Không hồi quy (chạy nhanh)

- [ ] Phủ định: "Không học hai tiếng nhưng làm việc hai tiếng" chỉ còn Làm việc 2h; "Làm việc không nghỉ ba tiếng" còn Công việc 3h.
- [ ] Đồng hồ: dừng sau vài giây lưu đúng; Timer > 24h theo `WALLY_OWNER_CHECKLIST_V106.md` bước 8; ngân sách nhất quán (bước 3 của V106).
- [ ] Hủy → Gửi lại ngay (≤ 1 giây) được nhận; khóa API còn dùng được sau khi đóng và mở lại app.
- [ ] **Không khóa, không mạng:** (chế độ máy bay, xóa khóa trong ⚙) mở app, ghi thủ công, đồng hồ, Nhật ký, Ngân sách dùng bình thường; ở tab Wally chỉ hiện hướng dẫn cấu hình, không lỗi treo.
- [ ] **F6 giữ `UNRESOLVED`:** nếu app dừng đột ngột, mở lại, ⓘ → "Nhật ký lỗi gần nhất" → Sao chép và gửi kèm thao tác trước đó.

## 4. Kết luận (điền)

Thiết bị / Android: `______` · SHA-256 APK đã cài: `______` (= `e13c35f4…62a5`) · Reviewer re-check: `[ ] PASS / [ ] REJECT` · Owner: `[ ] PASS / [ ] FAIL (kèm bằng chứng)` · F6: `UNRESOLVED` · Ghi chú: `______`
