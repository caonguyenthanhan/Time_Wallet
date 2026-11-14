# TIME WALLET — MVP v1.0 / v1.0.1 HANDOFF & O&M PATCH DOCUMENT

- **Dự án:** Time Wallet (Ví Thời Gian — Android Native MVP)
- **Phiên bản hiện hành:** **v1.0.1 AI-01 Final Release (`versionCode = 2`)** — Bản nâng cấp Conversational Analytics, Chống Race Condition Timer & Sửa triệt để vòng đời proposal (F1–F4)
- **Ngày cập nhật:** `2026-11-13T01:50:00+07:00`
- **Trạng thái vận hành:** **RELEASE READY — Technical Sign-Off Completed (93/93 Tests PASS, DEVICE_NOT_TESTED)**

---

## 1. Định danh Bản Bàn giao Chính thức (`v1.0.1` AI-01 Final Release)

### 1.1. Bản Phát hành Hiện hành tại Root (`TimeWallet-MVP-debug.apk`)

| Thuộc tính | Giá trị xác thực (`v1.0.1 AI-01 Closure`) |
| :--- | :--- |
| **Vị trí APK chính (Root)** | [TimeWallet-MVP-debug.apk](file:///d:/desktop/Time_Wallet/TimeWallet-MVP-debug.apk) |
| **Vị trí APK theo phiên bản** | [TimeWallet-MVP-v1.0.1-debug.apk](file:///d:/desktop/Time_Wallet/TimeWallet-MVP-v1.0.1-debug.apk) |
| **Vị trí APK Candidate v3** | [TimeWallet-MVP-v1.0.1-AI01-CLOSURE-candidate-debug.apk](file:///d:/desktop/Time_Wallet/out/ai01_candidate_v3_closure/TimeWallet-MVP-v1.0.1-AI01-CLOSURE-candidate-debug.apk) |
| **Application ID** | `com.timewallet.mvp` |
| **Version Name / Version Code** | `1.0.1` (`versionCode = 2`) |
| **Room Database Version** | `version = 3` (Bảo toàn 100% schema và dữ liệu hiện có) |
| **Kích thước chính xác** | `11,877,812 bytes` (`11.33 MiB` / `11.88 MB`) |
| **Thời điểm đóng gói** | `2026-11-13` |
| **SHA-256 Checksum** | `730DC2699DC5DF110BD2ADFD7C7EDEE29B858AE26855646AC7A8859AFDE2752D` |
| **Số lượng Unit Test đạt chuẩn** | **93 / 93 PASS** (Bao gồm Golden 66/66 và Holdout 46/46) |

### 1.2. Các bản lưu trữ an toàn trước khi nâng cấp

Toàn bộ các bản phát hành và snapshot trước đó được lưu trữ nguyên vẹn để phục vụ rollback khi cần:
- **Baseline v1.0.1 Pre-AI01 (Bảo mật & Keystore):** `out/ai01_snapshot_pre/TimeWallet-MVP-v1.0.1-debug.apk` (SHA-256: `BFE0496959A027AF32993D37D382E7C80A06C3C3C0311A208FCFCF3A762E1C03`, `11,538,935 bytes`).
- **Source Snapshot v1.0.1 Pre-AI01:** `out/ai01_snapshot_pre/source_v1.0.1_pre_AI01.tar` (44 files sạch, khớp manifest 100%).
- **Snapshot v1.0.0 ban đầu:** `out/v1.0.0_candidate/TimeWallet-MVP-v1.0.0-debug.apk` (SHA-256: `9E4FD1DFC3F029A60BDC717E3F9BF19A593E7E8B2B2E3DADEC45DC1E6320F235`).

<details>
<summary><strong>Bảng băm SHA-256 chi tiết cho toàn bộ 42 tệp mã nguồn & cấu hình của bản v1.0.1</strong></summary>

```text
6461DDD479D39E8339EC96984376A87144636B55E70B2EEC4170DB29FFB568BA  app/build.gradle.kts
47C4953155BEDB220F12ECB54CD62851099B6EB261843A6CA16FEE4AF6952997  app/src/main/AndroidManifest.xml
6FFF771250A1E0176761C05AF8E1D2F4B66B24AD10531617A07463C196E66895  app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt
D5DE21AE592332784DC973A5FB1A069B19BF0BA4D8DB0B146C43C094C4D993C3  app/src/main/java/com/timewallet/mvp/data/local/BudgetDao.kt
EDFBF70FE6D7E1105532CB0EC22E00FD68637983174172677302DE403FA4A7C6  app/src/main/java/com/timewallet/mvp/data/local/CategoryBudgetEntity.kt
49DFB9DB914CD0F2EAD561EBC3070115774CBB0C39A7FE5EC6EB619A7ED5D0BF  app/src/main/java/com/timewallet/mvp/data/local/DailyReviewDao.kt
16002E181AF4B57924731822861D9F48149CC646B6211AD1F3E7D670ABC2566E  app/src/main/java/com/timewallet/mvp/data/local/DailyReviewEntity.kt
968DD32890DEAE6DDCD649D86C37680CA234ACF905FFE5B510501EE3D79B004D  app/src/main/java/com/timewallet/mvp/data/local/LedgerDao.kt
2BB6674A2B694F0B886B164F9B56077F375E5A3C0BCDB7DDD361CC5A2498313F  app/src/main/java/com/timewallet/mvp/data/local/LedgerRecordEntity.kt
6B5BA4F87E4F2B2472AD212029FBCD2A7B0E810B0BD6ED113F25B8C3159F3852  app/src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt
C529F1E892863D284D87319445496E26B7383DC77B95622E7239655D471865D6  app/src/main/java/com/timewallet/mvp/data/local/TimeWalletDatabase.kt
B90985E21017AAABB826A6696FDD34EEDB14B61C986EA1CB22F6CB2C91BE2C51  app/src/main/java/com/timewallet/mvp/domain/model/ActiveTimerSession.kt
24CF75C923160647C816843A42EFF01E59B408A4A1C2BDE5CD0A36E5788B418B  app/src/main/java/com/timewallet/mvp/domain/model/ActivityProposal.kt
E8E3DD39EB87AF692917CA2CEFE6B4C3F8719823F0522C19BAC24B0A376F4CE8  app/src/main/java/com/timewallet/mvp/domain/model/DailyReview.kt
AA7AD9AB8B7B14DC8B506C9933ED4BDEC586FB9293DCCBC8EEE2D2E88AAE268F  app/src/main/java/com/timewallet/mvp/domain/model/LedgerRecord.kt
86B13EC6141D10AF257C99ADC505E0DC680C8410C23E5738683E917179BC9F37  app/src/main/java/com/timewallet/mvp/domain/model/TimeBudget.kt
E48317CDAEAA1C723ACE5C3C9802DD26404E653F21C898DA51DD5CF2B7B7F91E  app/src/main/java/com/timewallet/mvp/domain/model/TimeCategory.kt
253AB9642EA52D38E1906B9D3DBF02EBD99B8C343A068D6C13404EAAF44BFC07  app/src/main/java/com/timewallet/mvp/domain/model/TimeLayer.kt
B5EA4E8EEF30797F343E8B76131E497E4D1FCDAF50C9D501E19C4D8872575AF8  app/src/main/java/com/timewallet/mvp/domain/usecase/DailyReviewAssistant.kt
57F2E737D024C15EFB6DEA7419ADA90B56B3835812599649080E82E99E3BA322  app/src/main/java/com/timewallet/mvp/domain/usecase/DopamineTimeDiceEngine.kt
5957A89C0E93C06EAC8C768BCB8C333B7266E8E3FAEE2AA4B9217BE7471308F4  app/src/main/java/com/timewallet/mvp/domain/usecase/LedgerAnalyticsCalculator.kt
AA56A08D58A808622FDBC8784C3A18B4404B31D87373EF549A1C9E66683072AA  app/src/main/java/com/timewallet/mvp/domain/usecase/LedgerValidator.kt
E9039F2C5A08916DB1F18EF0451D44E11277D66096EF85CA8F98162E57547E51  app/src/main/java/com/timewallet/mvp/domain/usecase/VietnameseChatParser.kt
572CFE9FFE204D98E7D42351F5206DA1347B0C4B03C4D09135063B47EB467978  app/src/main/java/com/timewallet/mvp/MainActivity.kt
781AA45FBA1310D40E54AC7BCBCBA31583E662C550A5FC548593A001C3B77C4E  app/src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt
44ED1EA5E53A7B8B8AFF6E504106901B110B10D796AA80F49689E150D99F9576  app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt
A3D6EAB48EAF4F81C722B48A11A4456721CA5E0F4E63CB11BF7AA35A9B605DBF  app/src/main/java/com/timewallet/mvp/ui/screens/DailyReviewScreen.kt
64FB1ED268BF2E49E3211E810D0D0A7433502028639307F5932B7F4A7FBFB4BD  app/src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt
FAF2AAE99ED8FA5A9DC72CC1AB11AF2E62C551D1723A7E3715BF616D44BEA334  app/src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt
F94D67A6791707C6E107FF58B61F0C8DA5243D298D912843ED23AA6D10853FFC  app/src/main/java/com/timewallet/mvp/ui/screens/OverviewScreen.kt
6D4D351DAA48F274A8F8E273105F0353D64BB534C2B004B944087473B3FB6CD2  app/src/main/java/com/timewallet/mvp/ui/screens/TimerScreen.kt
80B52FEEBC77B2945BD4589DF934017A10A516C45C14A0C7A16591B54258DB93  app/src/main/java/com/timewallet/mvp/ui/theme/Theme.kt
802DD5D92D616629B3E962CE25BF69795AADB8009DE2D8BBE9AD9A726E344FCB  app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt
0D858D267B354BD9043AE4EA066DC67E9366F18E70A91EE03EC4CDDAA3AC8558  app/src/main/res/drawable/ic_launcher_wallet.xml
2DD965DDA479FCAB6EB7F7983DF90AE31BD62BB4253ED123D9161190A9BE31B8  app/src/main/res/drawable-nodpi/app_logo.png
72A19D9C41C1D457B5E5506DEF941AB045288F851F04407FF11AE3F54571E7B9  app/src/main/res/values/strings.xml
2FF282F95454708959F2F039842A46370ABDC2C4AF96993EAAB5D6EEB11983B6  app/src/main/res/values/themes.xml
D1ADF359966D84A26B7C9B749E2761FF4DEEDB8B775FB4C577C0E6169242BE3C  app/src/main/res/xml/backup_rules.xml
82A34EAEC6535A574D355092004CB0FB1F2DC747790CE1078DC72E64CBA43870  app/src/main/res/xml/data_extraction_rules.xml
D65EE93D7D01CA093AAC193FA0A0C2F2CE9D109BED8AE5B7A9B2387A778FFFB3  app/src/test/java/com/timewallet/mvp/TimeWalletDomainTest.kt
C901F69220924F95494B81ADDA7D17F362BF72E7D3F5D4FE8467D13A8A9C7CAD  build.gradle.kts
8D78B9D51E39271FB2357B7B6434E109E07626D2205F5C5C6F717132EFAEA198  settings.gradle.kts
```
</details>

---

## 2. Danh sách Phát hiện từ Independent Code Review (`FIXED` / `DEFERRED` / `NOT_REPRODUCED`)

| Mã | Phát hiện từ Code Review | Trạng thái | Giải pháp Kỹ thuật & Tệp Đã Sửa |
| :--- | :--- | :---: | :--- |
| **F1** | **Rò rỉ API key và dữ liệu nhạy cảm qua Backup + Lưu API key dạng plaintext** | **`FIXED`** | 1. Triển khai `AesGcmKeystoreVault` (`AES/GCM/NoPadding` 256-bit, 128-bit GCM tag qua `AndroidKeyStore`) trong [TimerAndSettingsStore.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt#L58-L148).<br>2. Tự động migrate khóa plaintext cũ (`ai_gemini_api_key`) sang bản mã `ai_api_key_aes_gcm_v1` và xóa ngay bản ghi plaintext khỏi `SharedPreferences`.<br>3. Đặt `android:allowBackup="false"` trong [AndroidManifest.xml](file:///d:/desktop/Time_Wallet/app/src/main/AndroidManifest.xml#L7-L11) kèm quy tắc loại trừ `sharedpref` và `database` tại [backup_rules.xml](file:///d:/desktop/Time_Wallet/app/src/main/res/xml/backup_rules.xml) và [data_extraction_rules.xml](file:///d:/desktop/Time_Wallet/app/src/main/res/xml/data_extraction_rules.xml). Không ảnh hưởng dữ liệu Room v3 hiện có. |
| **F2** | **Gửi văn bản nhật ký sang bên thứ ba khi Opt-in; thiếu cảnh báo host đích, kiểm tra HTTPS và ràng buộc token theo host** | **`FIXED`** | 1. Bổ sung `AiPreferences.extractValidatedHttpsHost()` ([TimerAndSettingsStore.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt#L38-L51)) bắt buộc giao thức `https://` hợp lệ.<br>2. Ràng buộc khóa API với `trustedHost`: trong [GeminiProposalClient.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L68-L75) và [TimeWalletViewModel.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt#L1217-L1254), không bao giờ gửi Bearer token sang host mới nếu người dùng chưa nhập lại/xác nhận cấu hình cho host đó.<br>3. Cập nhật hộp thoại `AiSettingsDialog` ([ComponentsAndDialogs.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt#L853-L977)) nêu rõ: mặc định 100% Offline, tên host đích nhận dữ liệu, các trường dữ liệu gửi đi và tự động xóa khóa cũ khi đổi host. |
| **F3** | **Timer tính thiếu giờ (undercount) sau khi thiết bị reboot nếu uptime mới lớn hơn uptime lúc bắt đầu** | **`FIXED`** | 1. Bổ sung `startedBootCount: Int?` (đọc từ `Settings.Global.BOOT_COUNT`) vào [ActiveTimerSession.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/model/ActiveTimerSession.kt#L20-L82) và lưu xuống `SharedPreferences` (`timer_boot_count`).<br>2. Khi `startedBootCount != currentBootCount` (hoặc `nowElapsedRealtimeMs < startedElapsedRealtimeMs`), bỏ qua hoàn toàn đồng hồ đơn điệu và fallback an toàn sang hiệu số đồng hồ tường (`nowEpochMs - startedAtEpochMs`).<br>3. Xử lý an toàn phiên cũ nâng cấp từ v1.0.0 thiếu `startedBootCount` (`startedBootCount == null` trên thiết bị có `currentBootCount != null`) và giữ nguyên bảo vệ chống chỉnh lệch giờ (`clockSkewDetected`) trong cùng chu kỳ boot. |
| **F4** | **Hàm `jsonQuote` thủ công không escape hết các ký tự điều khiển `< 0x20` (`\b`, `\f`, `U+0000`–`U+001F`)** | **`FIXED`** | Thay thế vòng lặp escape thủ công bằng serializer chuẩn `org.json.JSONObject.quote(value)` (RFC 8259) trong [TimerAndSettingsStore.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt#L455-L485). Kiểm thử đầy đủ dải `U+0000`–`U+001F` và tiếng Việt có dấu qua round-trip `JSONObject(exportedJson)`. |
| **F5.a** | **Kết nối `HttpURLConnection` không gọi `disconnect()` trong khối `finally`** | **`FIXED`** | Bọc toàn bộ vòng đời `HttpURLConnection` trong `try { ... } finally { connection?.disconnect() }` tại [GeminiProposalClient.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L162-L192). |
| **F5.b** | **Hiển thị raw exception / nội dung lỗi máy chủ ra cảnh báo người dùng** | **`FIXED`** | Chuẩn hóa thông báo fallback tĩnh `SAFE_FALLBACK_WARNING_VI` trong [GeminiProposalClient.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L39-L95) và [MainActivity.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/MainActivity.kt#L112-L114), tuyệt đối không nối `e.message` hay body lỗi HTTP ra UI. |
| **F5.c** | **Kiểm tra chéo `durationSeconds` với `endLocal - startLocal` khi AI trả về `explicit_range` và trong `LedgerValidator`** | **`FIXED`** | 1. Trong [GeminiProposalClient.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L251-L273): đối chiếu `abs(rangeSeconds - durationSeconds) > 120L`, từ chối đề xuất lệch giờ và đồng bộ `effectiveDurationSeconds = rangeSeconds`.<br>2. Trong [LedgerValidator.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/usecase/LedgerValidator.kt#L159-L166) & [#L360](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/usecase/LedgerValidator.kt#L360): `validateAndBuildRecord` đã có kiểm tra lệch > 2 phút, nhưng `validateBatchAtomic` trước đó truyền `null` khi có `startMs/endMs`; đã sửa dòng 360 để truyền `item.durationMinutes` giúp chặn lệch giờ ngay cả khi lưu theo lô. |
| **F5.d** | **Đọc lại `activeTimer` bên trong coroutine tại `confirmProposalsList`** | **`FIXED`** | Chuyển lệnh đọc `val activeTimer = _uiState.value.activeTimer` vào bên trong `viewModelScope.launch` ngay sau `ledgerDao.getAllRecordsSnapshot()` tại [TimeWalletViewModel.kt](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt#L700-L704). |
| **F5.e** | **`isMinifyEnabled = false` cho bản `release` trong `app/build.gradle.kts`** | **`DEFERRED`** | Giữ nguyên `isMinifyEnabled = false` theo đúng Yêu cầu 6 của bản vá O&M (*"Không bật minification hoặc refactor ngoài phạm vi"*). Sẽ cấu hình R8/ProGuard khi chuẩn bị bản phát hành Production. |

---

## 3. Phân định Trạng thái Kiểm thử (`UNIT_TEST_PASS` / `BUILD_PASS` / `DEVICE_NOT_TESTED`)

| Hạng mục Xác nhận | Trạng thái | Chi tiết Bằng chứng |
| :--- | :---: | :--- |
| **1. Kiểm thử Đơn vị & Tích hợp SQLite (`testDebugUnitTest`)** | **`UNIT_TEST_PASS`** | **25/25 bài kiểm thử PASS** (`21` regression tests + `4` bài test bảo mật & toàn vẹn dữ liệu `patch_v1_0_1`, `0 failures`, `0 errors`, thời gian chạy `0.561s`). Báo cáo XML tại [TEST-com.timewallet.mvp.TimeWalletDomainTest.xml](file:///d:/desktop/Time_Wallet/app/build/test-results/testDebugUnitTest/TEST-com.timewallet.mvp.TimeWalletDomainTest.xml). |
| **2. Biên dịch & Đóng gói APK (`assembleDebug`)** | **`BUILD_PASS`** | `BUILD SUCCESSFUL`, tạo gói [TimeWallet-MVP-v1.0.1-debug.apk](file:///d:/desktop/Time_Wallet/TimeWallet-MVP-v1.0.1-debug.apk) (`SHA256: BFE0496959A027AF32993D37D382E7C80A06C3C3C0311A208FCFCF3A762E1C03`). |
| **3. Chạy thực tế trên Thiết bị Android / Emulator** | **`DEVICE_NOT_TESTED`** | Tuân thủ quy tắc tại [AGENTS.md](file:///d:/desktop/Time_Wallet/.agents/AGENTS.md): không tự chạy Emulator hoặc `adb install` từ terminal AI. Không tuyên bố nghiệm thu Android runtime khi chưa chạy trên điện thoại thực tế. |

### Bằng chứng 4 Bài Kiểm thử Mới trong `v1.0.1` ([TimeWalletDomainTest.kt](file:///d:/desktop/Time_Wallet/app/src/test/java/com/timewallet/mvp/TimeWalletDomainTest.kt#L1286-L1522))

1. **`patch_v1_0_1_F1_and_F2 - AES_GCM vault encrypts API key migrates legacy plaintext enforces HTTPS and blocks sending token to unconfirmed host` (`PASS`):**
   - Xác minh khóa plaintext cũ được mã hóa tự động sang `enc:v1:<iv>:<ciphertext>`, bản ghi plaintext bị xóa khỏi `SharedPreferences`, sửa đổi bản mã bị từ chối bởi GCM authentication tag, URL `http://` bị chặn, và khóa API không bao giờ được gửi khi `baseUrl` đổi sang host chưa xác nhận.
2. **`patch_v1_0_1_F3 - timer reboot with new uptime greater than start uptime does NOT undercount while same-boot clock skew and legacy sessions work` (`PASS`):**
   - Mô phỏng chính xác tình huống: Timer bắt đầu lúc uptime `1 phút` (`60,000 ms`, `bootCount = 10`), máy reboot sang `bootCount = 11`, đồng hồ tường trôi qua `30 phút` (`1800s`) và uptime mới đạt `10 phút` (`600,000 ms > 60,000 ms`). Kết quả trả về chính xác `1800s` (`30 phút`), `clockSkewDetected = false`, `rebootFallbackUsed = true`.
3. **`patch_v1_0_1_F4 - JSON exporter serializes all control characters U0000 to U001F and Vietnamese Unicode with valid round-trip JSONObject parse` (`PASS`):**
   - Xuất dữ liệu chứa toàn bộ 32 ký tự điều khiển `U+0000`..`U+001F` kết hợp tiếng Việt có dấu, dấu ngoặc kép và gạch chéo ngược; parse ngược bằng `org.json.JSONObject(exportedJson)` khớp tuyệt đối từng ký tự.
4. **`patch_v1_0_1_F5 - GeminiProposalClient and LedgerValidator reject explicit_range duration mismatch and sanitize fallback errors` (`PASS`):**
   - Xác minh cả `GeminiProposalClient.parseAndValidateResponseJson` và `LedgerValidator.validateBatchAtomic` đều từ chối mục có `explicit_range` `08:00–09:00` nhưng khai báo thời lượng `120 phút` (`7200s`).

---

## 4. Wave Digest — Patch v1.0.1 (`PASS` / `FAIL` / `NOT_TESTED`)

| Hạng mục Nghiệm thu Patch v1.0.1 | Trạng thái | Bằng chứng Xác thực |
| :--- | :---: | :--- |
| **1. Snapshot `v1.0.0` (APK & Source) trước khi sửa** | **`PASS`** | Lưu trữ nguyên vẹn tại `out/v1.0.0_candidate/` (`APK SHA256: 9E4FD1DF...`, `Source ZIP SHA256: 9AAFEE1B...`). |
| **2. F1: Mã hóa API key AES-GCM + Migrate plaintext + Backup exclusion** | **`PASS`** | `AesGcmKeystoreVault` + `allowBackup="false"` + `backup_rules.xml` & `data_extraction_rules.xml`. |
| **3. F2: Minh bạch host Opt-in, bắt buộc HTTPS & Chặn gửi token nhầm host** | **`PASS`** | Kiểm tra `extractValidatedHttpsHost` + đối chiếu `trustedHost` trong `TimerAndSettingsStore`, `GeminiProposalClient` và `AiSettingsDialog`. |
| **4. F3: Reboot với uptime mới > uptime lúc start không undercount** | **`PASS`** | Kiểm tra `startedBootCount != currentBootCount` chuyển sang `wallDeltaMs`, giữ nguyên độ chính xác same-boot và tương thích phiên cũ thiếu boot ID. |
| **5. F4: JSON parse hợp lệ mọi control character `U+0000`–`U+001F` & tiếng Việt** | **`PASS`** | Sử dụng `org.json.JSONObject.quote` + kiểm thử round-trip `JSONObject(exportedJson)`. |
| **6. F5: Disconnect HTTP trong `finally`, ẩn raw error, chặn lệch `explicit_range`/`duration`, đọc lại `activeTimer`** | **`PASS`** | Đã sửa trong `GeminiProposalClient.kt`, `LedgerValidator.kt`, `TimeWalletViewModel.kt`, `MainActivity.kt`. |
| **7. Bảo toàn Room v3, UI/navigation và 21/21 regression tests + 4 test mới** | **`PASS`** | `25/25 PASS` trong `testDebugUnitTest` (`0.561s`). |
| **8. Kiểm thử Runtime trực tiếp trên Thiết bị Android vật lý** | **`DEVICE_NOT_TESTED`** | Tuân thủ quy tắc không chạy Emulator/ADB từ terminal AI; nghiệm thu trên điện thoại theo Checklist 7 ngày bên dưới. |

---

## 5. Checklist Sử dụng Thực tế 7 Ngày (Cập nhật cho `v1.0.1`)

- **Ngày 1 (Timer & Lock Screen):** Bật đồng hồ bấm giờ, khóa màn hình 15–30 phút, mở lại và lưu vào Sổ cái (`MEASURED`).
- **Ngày 2 (AI Chat, HTTPS & Host Binding):** Thử chế độ Offline với câu 5 hoạt động trên Compact Batch Card; mở cài đặt AI (`⚙`), kiểm tra thông báo tên host đích (`api.foza.ai`), thử nhập URL `http://` để xác nhận nút Lưu bị khóa.
- **Ngày 3 (Overlap, Active Clipping & Explicit Range Validation):** Kiểm tra chặn trùng giờ, kiểm tra cắt thời gian tại `nowEpochMs` cho hoạt động đang diễn ra, và xác nhận thủ công bản ghi `ACTIVE`/`PLANNED`.
- **Ngày 4 (Time Budget & Dopamine Dice):** Đặt 3 ngân sách ưu tiên ngày/tuần, kiểm tra `PLANNED` và `UNTIMED` không cộng vào thanh tiến độ đã dùng.
- **Ngày 5 (Daily Review & Feynman):** Mở Daily Review từ Dashboard, ghi kết quả hoạt động, trả lời câu hỏi Feynman cho mục Học tập và tối đa 2 câu hỏi phản tư AI.
- **Ngày 6 (Reboot Thiết bị khi Timer đang chạy):** Bật đồng hồ khi máy vừa khởi động xong (uptime thấp), sau đó **Reboot** lại điện thoại và để máy chạy quá mức uptime lúc bắt đầu rồi mới bấm Kết thúc: xác nhận đồng hồ tính đúng tổng thời gian thực tế theo đồng hồ tường, không bị undercount.
- **Ngày 7 (Chuyển tuần theo Múi giờ, Xuất JSON & Nâng cấp APK):** Xuất tệp JSON và cài đè APK để xác nhận bảo toàn 100% dữ liệu Room v3 và khóa API đã mã hóa.

---

**STOP — Trở về chế độ Operations & Maintenance (O&M):** Bản vá `v1.0.1` đã hoàn tất, toàn bộ 25/25 bài kiểm thử đạt `UNIT_TEST_PASS`, gói APK `v1.0.1` và bản snapshot `v1.0.0` đều được định danh bằng mã băm SHA-256. Không mở thêm tính năng hoặc wave mới.
