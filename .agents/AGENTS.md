# Project Rules — Time Wallet (Android)

## Liên kết Ecosystem (SSOT)
- Kho rule & skill trung tâm: `D:\An-tool-ecosystem`
- Skill SSOT: `D:\An-tool-ecosystem\.trae\skills`
- Context Hygiene: `D:\An-tool-ecosystem\rules\context-hygiene.md`

## Quy tắc dự án
1. **Android Development**:
   - Mở và chạy dự án bằng **Android Studio**.
   - **Quy tắc nền (lịch sử & bảo vệ thiết bị/dữ liệu Owner):** KHÔNG BAO GIỜ kết nối/chạm điện thoại thật, và KHÔNG BAO GIỜ khởi chạy, dừng, đọc/copy prefs/ciphertext/key hoặc thao tác lên instance cá nhân của Owner (`index 0`, tên `LDPlayer`, serial `emulator-5554`). Xem thêm sự cố và phụ lục bắt buộc tại `WALLY_V109_INCIDENT_ADDENDUM_OWNER_INSTANCE.md`. Không yêu cầu Owner phục hồi instance cá nhân để tiếp tục QA.
   - **Ngoại lệ QA giới hạn hẹp (chỉ áp dụng cho `WallyQA`):** Chỉ được dùng emulator/ADB đối với instance QA riêng `WallyQA` (`index 1`, serial `emulator-5556`) với các điều kiện bắt buộc:
     1. Xác minh tên (`WallyQA`), `index` (`1`) và ADB serial (`emulator-5556`) qua `ldconsole list2` / `ldconsole runninglist` / `adb devices` trước mỗi nhóm thao tác (nếu `index 0` đang chạy, dừng ngay và báo cáo).
     2. Chỉ dùng dữ liệu synthetic trong `WallyQA`; tuyệt đối không đọc/copy prefs, ciphertext, key hoặc dữ liệu cá nhân của Owner.
     3. Không `adb kill-server`, không thay đổi cấu hình mạng Windows, không dùng `svc wifi`/`svc data`.
     4. Không dùng chuột/bàn phím Windows để điều khiển UI.
     5. Không kết nối hoặc thao tác lên điện thoại thật.
     6. **(Manager Closure v1.0.10)** Không chạm instance của Owner (`index 0`) dưới bất kỳ hình thức nào.
     7. **(Manager Closure v1.0.10)** Không stop/kill/restart tiến trình hoặc service LDPlayer dùng chung trên Windows, bao gồm `Ld9BoxSVC` (và `Ld9BoxHeadless` không thuộc `WallyQA`). Không `adb kill-server` toàn cục.
     8. **(Manager Closure v1.0.10)** Nếu host service LDPlayer bị treo: báo Owner và dừng riêng phiên QA bị ảnh hưởng. Không tự xử lý tiến trình dùng chung.
     9. Ngoại lệ này chỉ có hiệu lực cho `WallyQA`. Trạng thái: v1.0.10 vẫn FROZEN (không QA thêm cho v1.0.10). **Owner đã cấp phép (2026-11-15) Wave UI-POLISH v1.0.11**: được dùng `WallyQA` để kiểm tra giao diện v1.0.11, với MỌI giới hạn 1–8 ở trên giữ nguyên. Hết Wave thì quay lại "không emulator QA" trừ khi Owner/Manager cấp lại.
   - **Single Writer (Manager Closure v1.0.10):** tại một thời điểm chỉ MỘT agent được quyền sửa working tree. Reviewer không sửa production source. Chuyển quyền writer phải có handoff bằng văn bản (mẫu trong `WALLY_V1010_MANAGER_CLOSURE_RECORD.md`) ghi rõ: writer cũ → writer mới, thời điểm, trạng thái cây mã (hash manifest/tar), việc đang dở, và xác nhận writer cũ ngừng ghi.
   - **Writer hiện tại (Owner Authorization 2026-11-15, Wave UI-POLISH v1.0.11):** AI-Master của phiên này là Single Writer duy nhất cho Wave; được sửa `app/src`, chạy test, build APK v1.0.11. APK v1.0.10 (`1d834a83…536b`) và `out/wally_candidate_v1010/` là baseline rollback, KHÔNG ghi đè. Phạm vi: chỉ UI/UX Polish; giữ nghiệp vụ, dữ liệu, timer lifecycle, analytics, guardrails Wally, F6 fix, không đổi schema.
   - **Baseline có thẩm quyền:** manifest + tar `v1.0.9`/`v1.0.10` (`out/wally_candidate_v109/`, `out/wally_candidate_v1010/`) là baseline để đối chiếu candidate hiện tại. Không rewrite lịch sử Git để tạo provenance giả.
   - Logic nghiệp vụ (Time Ledger, Overlap Detection, Natural Language Parser, DST/Timezone Math) phải tách biệt trong tầng `domain` và có Unit Test đầy đủ.
2. **Bất biến sản phẩm (Time Wallet MVP Spec v1.0)**:
   - Ba lớp dữ liệu thực tế: `MEASURED`, `REPORTED_TIMED`, `REPORTED_UNPLACED` và lớp tách biệt `PLANNED`.
   - Không tự suy đoán giờ bắt đầu/kết thúc từ dữ liệu chỉ có thời lượng.
   - Không cộng `REPORTED_UNPLACED` vào biểu đồ 24h hoặc biểu đồ cột 7 ngày có mốc giờ.
   - Chặn chồng lấn thời gian (`overlap`) giữa các bản ghi có mốc giờ.
   - AI chỉ tạo đề xuất (`Proposal`), không tự ý ghi vào cơ sở dữ liệu khi chưa có xác nhận của người dùng.

