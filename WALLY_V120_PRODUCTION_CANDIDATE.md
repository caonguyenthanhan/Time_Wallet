# Time Wallet `v1.2.0` (`versionCode 16`) — Production Candidate & Owner Acceptance Pack

**Ngày lập:** 2026-11-17  
**Mục tiêu:** Chốt **1 APK Release ký số dùng cá nhân** (`isMinifyEnabled = false`), bảo toàn dữ liệu khi chuyển từ Debug sang Release, xử lý tuyến AI mặc định không ổn định, và báo cáo trung thực mọi hạng mục **PASS** vs **NOT_TESTED**.

---

## 1. Tệp bàn giao chính

| Thành phần | Giá trị / Đường dẫn |
|---|---|
| APK Production Candidate | `out/wally_candidate_v120/TimeWallet-MVP-v1.2.0-PRODUCTION-CANDIDATE-release.apk` |
| Dung lượng | `8,457,132` bytes |
| SHA-256 | `FA58218B83FD787D110BD0C4FB7CC6C572B4A650DFF7BEB70E9791F359D9D638` |
| SHA-256 file | `out/wally_candidate_v120/TimeWallet-MVP-v1.2.0-PRODUCTION-CANDIDATE-release.apk.sha256` |
| Package / Version | `com.timewallet.mvp` · `versionName = "1.2.0"` · `versionCode = 16` (`minSdk 26`, `targetSdk 35`) |
| Chứng thư ký số Release | `CN=Time Wallet Personal Release, OU=Mobile, O=TimeWallet, L=Hanoi, ST=HN, C=VN` (RSA 2048-bit, APK Signature Scheme v2, digest `39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0`) |
| Keystore & `keystore.properties` | Lưu **ngoài repo Git** tại `%USERPROFILE%\.timewallet\` (không chứa trong repo; `.gitignore`, `.repomixignore` và `WallySecurityStaticTest` chặn lộ) |
| Live AI QA logs (synthetic) | `out/wally_candidate_v120/chat_live_eval_v120.jsonl`, `out/wally_candidate_v120/coach_live_eval_v120.jsonl` |
| Dữ liệu & ảnh UI 1 tuần | `out/wally_candidate_v120/mock_1week_ai_showcase.json`, `docs/screenshots/ui-preview/` |

---

## 2. Các hạng mục đã hoàn thiện (P0 & P1)

1. **P0 — Lossless Export & Import JSON (`Debug -> Release` không mất dữ liệu):**
   - Mở rộng `LedgerJsonExporter.exportToJsonString` ([TimerAndSettingsStore.kt](app/src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt)) để xuất đầy đủ `idempotencyKey`, `plannedStartedAt`, `plannedEndedAt`, `overlapWithTitle`, `activityNoteUpdatedAt`, `budgets.priorityOrder`, `tags.nameKey`, `tags.updatedAt`, `goals.createdAt`, `goals.updatedAt` (giữ `schemaVersion = 1` tương thích ngược, tuyệt đối không xuất API key hay cấu hình Keystore).
   - Bổ sung [LedgerJsonBackup.kt](app/src/main/java/com/timewallet/mvp/data/local/LedgerJsonBackup.kt): `LedgerJsonBackupParser` + `LedgerBackupRestorer` chạy trong `LedgerConcurrencyGate.mutex` và Room transaction (`MERGE_SAFE`: bỏ qua trùng `id`/`idempotencyKey`, bỏ qua chồng lấn giờ, tự ánh xạ Tag trùng `nameKey` sang `Tag.id` hiện có cho cả `LedgerRecord` và `Goal(TAG)`; `REPLACE_ALL`: kiểm tra hợp lệ & chống chồng lấn trái phép trước khi xóa/nạp lại 6 bảng, vẫn bảo toàn các nhóm `PARALLEL_CONFIRMED` cùng `relationGroupId`).
   - Nối UI qua [DataManagementDialog.kt](app/src/main/java/com/timewallet/mvp/ui/screens/DataManagementDialog.kt) (`"Nhập / khôi phục JSON"`), `ImportJsonDialog` ([ComponentsAndDialogs.kt](app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt)), `OpenDocument` SAF picker ([MainActivity.kt](app/src/main/java/com/timewallet/mvp/MainActivity.kt)), và `TimeWalletViewModel.confirmImportJson` ([TimeWalletViewModel.kt](app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt)) tự đồng bộ lại lịch `AlarmManager` sau khi khôi phục.
2. **P0 — Release Signing & Bảo vệ Keystore:**
   - Cấu hình `signingConfigs.release` đọc từ `TIMEWALLET_KEYSTORE_*` hoặc `keystore.properties` tại root hoặc `%USERPROFILE%\.timewallet\keystore.properties` trong [app/build.gradle.kts](app/build.gradle.kts); chặn fail-closed nếu thiếu credential khi đóng gói Release; giữ `isMinifyEnabled = false`.
   - Khóa toàn bộ `*.jks`, `*.keystore`, `*.p12`, `*.pkcs12`, `keystore.properties`, `signing/` trong [.gitignore](.gitignore), [.repomixignore](.repomixignore) và test tĩnh [WallySecurityStaticTest.kt](app/src/test/java/com/timewallet/mvp/WallySecurityStaticTest.kt).
3. **P0 — Khôi phục nhắc lịch sau reboot / cập nhật / đổi múi giờ & kiểm chứng nâng cấp DB:**
   - Thêm `CompletionBootReceiver` và `CompletionReminderPolicy.selectMissedRecentPending` trong [CompletionReminder.kt](app/src/main/java/com/timewallet/mvp/notifications/CompletionReminder.kt), khai báo `RECEIVE_BOOT_COMPLETED` và receiver cho `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET` trong [AndroidManifest.xml](app/src/main/AndroidManifest.xml).
   - Bổ sung kiểm tra `POST_NOTIFICATIONS` trực tiếp và `catch SecurityException` vượt qua Android `lintRelease`.
   - Ràng buộc schema Room `1 -> 5` (`time_wallet_ledger.db`, không `fallbackToDestructiveMigration`) được đối chiếu 100% với `app/schemas/.../5.json` qua `V120MigrationSchemaTest`.
4. **P1 — Tuyến AI mặc định ổn định & UX lỗi:**
   - Chuyển `AiPreferences.DEFAULT_AI_MODEL` sang `"cinb/claude-sonnet-4-6"` ([TimerAndSettingsStore.kt](app/src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt)), thêm 2 chip chọn nhanh model + gợi ý chuyển model trong `AiSettingsDialog`, hiển thị nút `"Mở cấu hình ⚙"` cho `AiFault.SERVER` / `AiFault.NOT_FOUND` trong [ChatScreen.kt](app/src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt) và chi tiết lỗi rõ ràng trong [GeminiProposalClient.kt](app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt).
5. **Tích hợp 5 thành phần giao diện gốc Jetpack Compose (`v1.2.0` UI Integration):**
   - **Timeline dọc theo trình tự thời gian ([HistoryScreen.kt](app/src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt), [UiKit.kt](app/src/main/java/com/timewallet/mvp/ui/screens/UiKit.kt)):** Sắp xếp sự kiện trong ngày tăng dần từ sáng đến tối (`sortTimelineDayRecordsChronological`), cột giờ đối diện bên trái (`HH:mm` / `→ HH:mm`), chấm đặc cho sự kiện đã xong, vòng xoay động cho hoạt động `DOING`, đường nối dừng gọn ở chấm cuối, kèm tay cầm chỉnh nhanh thời lượng `−15p`/`+15p` cho bản ghi `PLANNED` (`ConfirmationEngine.resizePlannedRecord`).
   - **Hội thoại Chat ([ChatScreen.kt](app/src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt)):** Vùng hội thoại có `LiveRegionMode.Polite`, bong bóng bo rộng có đuôi góc hướng người nói (`bottomStart = 5.dp` / `bottomEnd = 5.dp`), giới hạn độ rộng tối đa ~75% cột, dòng metadata giờ gửi + trạng thái (`Đã gửi ✓` / `Wally đã xem ✓✓`) dưới tin nhắn mới nhất của người dùng, và chỉ báo 3 chấm nảy tuần tự (`WallyTypingDots`) khi đang phân tích.
   - **Donut 2D tương tác ([UiKit.kt](app/src/main/java/com/timewallet/mvp/ui/screens/UiKit.kt), [OverviewScreen.kt](app/src/main/java/com/timewallet/mvp/ui/screens/OverviewScreen.kt), [InsightsScreen.kt](app/src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt)):** Giới hạn tối đa 6 lát (`capDonutSlices`), khe hở đều giữa các lát, chạm lát hoặc dòng chú giải đẩy lát nổi nhẹ theo hướng 2D phẳng (không 3D) và cập nhật số liệu ở lỗ tâm.
   - **Nhóm FAB + Speed Dial + Chat + Back-to-Top ([MainActivity.kt](app/src/main/java/com/timewallet/mvp/MainActivity.kt)):** `FloatingActionButton` tròn màu chủ đạo với dấu `+` trắng cố định góc dưới phải, mở Speed Dial 3 hành động (`Ghi giờ thủ công`, `Bắt đầu đồng hồ`, `Nhìn lại ngày hôm nay`), nút mở nhanh Wally Chat, và nút Back-to-Top chỉ hiện khi cuộn `> 400 px` (`shouldShowBackToTop`), kèm đệm đáy tránh che nội dung cần chạm.
   - **Kéo-thả sắp xếp thứ tự ngân sách ưu tiên ([InsightsScreen.kt](app/src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt), [ComponentsAndDialogs.kt](app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt), [BudgetDao.kt](app/src/main/java/com/timewallet/mvp/data/local/BudgetDao.kt), [BudgetPolicy.kt](app/src/main/java/com/timewallet/mvp/domain/usecase/BudgetPolicy.kt)):** Tay cầm `⋮⋮` (`detectDragGestures`) kèm bản xem trước nổi, vạch chèn `BudgetInsertionLine` trước khi thả, nút trợ năng `↑ Lên`/`↓ Xuống` giữ nguyên phân nhóm ưu tiên, và công bố qua `liveRegion`.

---

## 3. Bảng kết quả kiểm chứng (PASS vs NOT_TESTED)

| Hạng mục | Trạng thái | Bằng chứng / Ghi chú |
|---|---|---|
| Bộ test JVM & tĩnh (`testDebugUnitTest --rerun-tasks`) | **PASS (618/620)** | Chạy sau sửa cuối (`task-3180`): `total=620, passed=618, failures=0, errors=0, skipped=2` (2 test skipped khi chạy offline là 2 bài Live API có điều kiện env var). Bao gồm [V121UiIntegrationTest.kt](app/src/test/java/com/timewallet/mvp/V121UiIntegrationTest.kt), [V121UiScreenHelpersTest.kt](app/src/test/java/com/timewallet/mvp/ui/screens/V121UiScreenHelpersTest.kt), [V120ProductionReadinessTest.kt](app/src/test/java/com/timewallet/mvp/V120ProductionReadinessTest.kt), [V120MigrationSchemaTest.kt](app/src/test/java/com/timewallet/mvp/V120MigrationSchemaTest.kt), `WallySecurityStaticTest`, `V106UiContractTest`, `V109PortableRegexTest`, `V1011UiPolishContractTest`, `WallyUiStaticTest`. |
| Android Lint Release (`lintRelease`) | **PASS (0 errors)** | Chạy sau sửa cuối (`task-3180`), báo cáo `Lint Report: 46 warnings` và **0 errors**. |
| Build & Ký số Release APK (`assembleRelease` + `apksigner` + `aapt2`) | **PASS** | `TimeWallet-MVP-v1.2.0-PRODUCTION-CANDIDATE-release.apk` (`8,457,132` bytes), xác thực chữ ký v2 thành công (`apksigner verify`), `versionCode=16`, `versionName="1.2.0"`, `targetSdk=35`. |
| Live AI QA với Foza thật (`V110CoachLiveEvalTest` trên `wally-coach-5` + lịch sử `V120LiveAiEvalTest`) | **PASS (8/8 HTTP OK trên Coach mới)** | Đã chạy trực tiếp `V110CoachLiveEvalTest` (`COACH_EVAL_REPEAT=1`, `cinb/claude-sonnet-4-6`, dữ liệu synthetic): **8/8** cuộc gọi không fault (`S2`–`S9`) đều qua **`CoachGuard` (`OK`, `0` lỗi rubric)**, độ dài còn **`105`–`168` ký tự** kèm `headline`, `highlights` và `1`–`2` Action Items; riêng `S1` gặp `TIMEOUT` 45s từ upstream. Vòng Live Chat `UC-15` trước đó đạt HTTP 200, không suy đoán câu mơ hồ và tách lô 2 đề xuất. |
| Emulator Android 9 (`WallyQA`) trên bản nền trước tích hợp UI (`3B144E36…`) | **Lịch sử** | Đã cài APK Release `3B144E36...`, xác minh Export/Import JSON `MERGE_SAFE` + `REPLACE_ALL`, `dumpsys alarm`, Live AI, và chụp ảnh xem tại `docs/screenshots/ui-preview/`. |
| Emulator Android 9 (`WallyQA`) trên **APK mới nhất (`FA58218B…`)** | **NOT_TESTED (Host unavailable)** | Lệnh `ldconsole launch --index 1` không đưa `WallyQA` online (`1,WallyQA,0`, không có `adb devices`); dừng phiên QA theo Rule 7–8 `AGENTS.md` (không chạm tiến trình/service Windows dùng chung). |
| Android 13–16 runtime (`POST_NOTIFICATIONS` prompt & Work/Alarm trên Android 13+) | **NOT_TESTED (Blocked)** | Bị chặn theo **Rule 14 `AGENTS.md`** (AVD Android 16 AEHD gây sập nguồn máy chủ Windows 3 lần trước đó). |
| Khởi động lại lạnh (`BOOT_COMPLETED` / `CompletionBootReceiver`) & nghiệm thu máy thật Owner | **NOT_TESTED (Pending Owner)** | Chờ Owner cài đặt, kiểm tra trực quan 5 thành phần giao diện mới + thẻ Wally AI Coach có Action Items và nghiệm thu trên điện thoại cá nhân. |

---

## 4. Rủi ro còn lại cần Owner lưu ý

1. **Chữ ký Debug ≠ Release:** Chứng thư Debug (`402f1d41...`) khác chứng thư Release (`39f5712d...`). Android sẽ **từ chối cài đè** bản Release lên bản Debug đang có. Bắt buộc **Xuất JSON trước → gỡ bản Debug → cài bản Release → Nhập / khôi phục JSON (`Khôi phục toàn bộ`)**.
2. **Bảo quản Keystore cá nhân:** Keystore đang nằm ở `%USERPROFILE%\.timewallet\timewallet-release.jks` + `keystore.properties` (chỉ có Owner + Windows user hiện tại). Nếu mất keystore này, các bản cập nhật Release tương lai sẽ không thể cài đè mà phải gỡ cài lại (Export/Import JSON sẽ vẫn cứu được dữ liệu). Hãy sao lưu riêng thư mục `%USERPROFILE%\.timewallet\` vào nơi an toàn (không đưa lên Git công khai).
3. **Runtime notification & reboot sau cài Release:** `CompletionBootReceiver` đã được kiểm tra ở mức JVM (`CompletionReminderPolicy`), Manifest bytecode (`aapt2`) và `lintRelease`, nhưng kích hoạt `ACTION_BOOT_COMPLETED` sau khi khởi động lại máy chưa được xác minh runtime (do `Ld9BoxSVC` giữ sau phiên thoát trên `WallyQA`). Android 13+ có cơ chế `POST_NOTIFICATIONS` runtime và một số hãng (Xiaomi/Oppo/Samsung OneUI) có thể hạn chế báo thức nền nếu bật tối ưu pin cực đoan — cần Owner kiểm chứng nhanh trên điện thoại cá nhân ở bước dưới.

---

## 5. Hướng dẫn Owner cài đặt & tự kiểm thu trên điện thoại cá nhân

1. **Sao lưu dữ liệu từ bản đang cài (nếu đang dùng bản Debug cũ):** Mở app hiện tại → menu `⋮` góc trên phải → **Dữ liệu & sao lưu** → **Xuất JSON** → **Lưu tệp .JSON** vào thư mục `Downloads` trên máy. Mở thử hoặc kiểm tra dung lượng tệp > 0 bytes.
2. **Gỡ bản Debug cũ** (chỉ sau khi đã thấy tệp `.json` an toàn trong `Downloads`).
3. **Cài APK Production Candidate:** Chép `out/wally_candidate_v120/TimeWallet-MVP-v1.2.0-PRODUCTION-CANDIDATE-release.apk` (đối chiếu đúng SHA-256 `FA58218B83FD787D110BD0C4FB7CC6C572B4A650DFF7BEB70E9791F359D9D638`) sang điện thoại và cài đặt.
4. **Khôi phục toàn bộ dữ liệu:** Mở app mới → menu `⋮` → **Dữ liệu & sao lưu** → **Nhập / khôi phục JSON** → **Chọn tệp .JSON từ thiết bị** → chọn đúng tệp vừa xuất → kiểm tra dòng xanh đếm đủ bản ghi / tag / mục tiêu / ngân sách / phản tư → chọn chế độ **`Khôi phục toàn bộ`** → xác nhận. Đối chiếu lại màn Tổng quan, Nhật ký, Phân tích và Tag.
5. **Thiết lập & kiểm tra AI:** Vào biểu tượng `⚙` → dán API Key cá nhân → kiểm tra model mặc định đã là **`cinb/claude-sonnet-4-6`** → bấm **Kiểm tra kết nối** (phải báo `✓ Kết nối thành công tới api.foza.ai (HTTP 200)`) → bấm **Xác nhận & Lưu**. Thử nhắn một câu ở tab Wally (ví dụ `"Sáng 8h đến 9h đọc sách"`).
6. **Kiểm tra thông báo & Reboot (Android 13+):** Thêm 1 hoạt động **Dự kiến** kết thúc sau hiện tại ~2–3 phút → cấp quyền thông báo khi Android hỏi → khóa màn hình chờ tới giờ kết thúc xem có notification và bấm vào notification có mở đúng hộp thoại xác nhận không; tùy chọn: đặt 1 hoạt động dự kiến khác xa hơn, khởi động lại điện thoại rồi kiểm tra thông báo vẫn đổ đúng giờ khi tới hạn.
