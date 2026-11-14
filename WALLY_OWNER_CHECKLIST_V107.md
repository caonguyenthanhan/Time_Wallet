# Checklist nghiệm thu Owner — candidate WALLY v1.0.7 (`versionCode 8`)

> **Quy tắc bắt buộc:** kết quả thử của v1.0.6 (`0d4e722b…`) và của bản dựng v1.0.7 vòng 1 (`a8f6727f…`) **không** được chuyển sang binary này. Ghi kết quả vào `WALLY_V107_ACCEPTANCE_RECORD.md` kèm SHA-256 thực của APK đã cài. Chỉ dùng dữ liệu thử; không gỡ app; **không gửi khóa API qua chat**; không emulator/ADB.

## 1. Xác nhận binary (làm trước)

- APK: `out/wally_candidate_v107/TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk`
- SHA-256 phải là: `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61`
- `versionName 1.0.7`, `versionCode 8`, package `com.timewallet.mvp`, cùng chữ ký v1.0.6 (cài đè được).

```powershell
(Get-FileHash "out\wally_candidate_v107\TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
# phải ra: 3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61
```

- [ ] SHA-256 khớp; sau khi cài, ⓘ ghi `Time Wallet v1.0.7`; số bản ghi và ngân sách y như trước khi cài đè.

## 2. Ba finding đã sửa

Mục 2.1 và 2.3 chạy **không cần AI** (nhập tay/đồng hồ); mục 2.1 (b) và 2.2 cần khóa AI của Owner.

### 2.1 `F-V105-03` — phủ định với số bằng chữ (cần AI để thấy đề xuất)
Gửi từng câu ở tab Wally, **không bấm Xác nhận**, sau mỗi câu bấm "Bỏ hết"; Nhật ký phải giữ nguyên số bản ghi.
- [ ] "Hôm nay tôi không học hai tiếng" ⇒ **0 đề xuất**, có lý do.
- [ ] "Không học hai tiếng nhưng làm việc hai tiếng" ⇒ chỉ còn **Làm việc 2h** (không có Học).
- [ ] "Không học hai tiếng hôm nay, làm việc ba giờ" ⇒ chỉ còn **Làm việc 3h**.
- [ ] "Không học vài tiếng" hoặc "Không học hay làm việc hai tiếng" ⇒ **0 đề xuất** kèm lời yêu cầu nêu rõ thời lượng / tách câu.
- [ ] Không hồi quy: "Làm việc không nghỉ ba tiếng" ⇒ Công việc 3h; "Học hai tiếng không nghỉ" ⇒ Học 2h; "Học một tiếng rưỡi" ⇒ Học 1h30; "Học tiếng Anh hai tiếng" ⇒ Học 2h.

### 2.2 `V106-N1` — AI không được báo "đã lưu" trước khi xác nhận
- [ ] Câu ghi hoạt động ("Học 45p"): chỉ hiện **thẻ đề xuất** và thông báo trạng thái do ứng dụng tạo (chờ xác nhận); **không** có câu chat của AI kiểu "đã lưu / xong xuôi / đã vào sổ". Nhật ký chưa tăng trước khi bấm Xác nhận.
- [ ] Gửi một câu **không phải ghi nhận** khi đang có đề xuất chờ (ví dụ "Cảm ơn nhé"): nếu thẻ trả lời của Wally hiện ra thì phải có dòng **"Chưa có gì được lưu. Còn N đề xuất đang chờ bạn xác nhận."**
- [ ] Gửi một câu có thời lượng mà AI chỉ trả lời văn bản (không tạo đề xuất): thẻ có dòng **"Chưa có gì được lưu: Wally chưa tạo đề xuất…"**.
- [ ] Câu vừa ghi vừa hỏi ("Học 45p và hôm nay tôi có bao nhiêu giờ chưa gắn giờ?") ⇒ vẫn có cả thẻ đề xuất và thẻ số liệu.
- [ ] Ghi lại mọi câu của Wally có vẻ báo đã lưu mà **không** kèm dòng trạng thái của ứng dụng (chụp màn hình).

### 2.3 `V106-N2` — BOM và bảo toàn thời lượng (không cần AI)
- [ ] Đồng hồ: bắt đầu rồi dừng sau vài giây; tổng đã lưu khớp số giây hiển thị (làm tròn **xuống**); đồng hồ chạy dưới 1 giây ⇒ báo "cần chạy ít nhất 1 giây" và không lưu.
- [ ] Ngày có nhiều bản ghi: tổng các lát donut ở Tổng quan khớp số "đã ghi nhận" (có mốc giờ + chưa gắn giờ) của ngày. Một phiên đồng hồ vắt qua 00:00 (nếu thử được): tổng hai ngày bằng thời lượng thật.
- (BOM trong phản hồi của máy chủ không thể tạo thủ công trên máy; phần này kiểm bằng unit test.)

## 3. Nguyên vẹn hệ thống và riêng tư

- [ ] Khóa API lưu mã hóa AES-GCM trong AndroidKeyStore (không lưu dạng rõ); đổi Base URL sang host khác ⇒ yêu cầu nhập lại khóa và báo rõ.
- [ ] Thời lượng chỉ có số phút (chưa gắn giờ) không bị suy đoán giờ bắt đầu/kết thúc và không cộng vào trục 24h; bản ghi có giờ trùng nhau bị từ chối.
- [ ] Ngân sách, Timer > 24h, hộp thoại cuộn: chạy lại các bước tương ứng của `WALLY_OWNER_CHECKLIST_V106.md` (không hồi quy).
- [ ] **F6 giữ `UNRESOLVED`:** nếu app dừng đột ngột, mở lại, ⓘ → "Nhật ký lỗi gần nhất" → Sao chép và gửi kèm thao tác trước đó; không tự đánh dấu đã sửa.

## 4. Kết luận (điền)

- Thiết bị / Android: `__________________`
- SHA-256 APK đã cài: `__________________` (phải = `3df0fee7…c61`)
- Reviewer re-check: `[ ] PASS` / `[ ] REJECT`
- Owner: `[ ] PASS` / `[ ] FAIL (kèm bằng chứng)`
- F6: `UNRESOLVED` (hoặc ghi log nếu có)
- Ghi chú: `__________________`
