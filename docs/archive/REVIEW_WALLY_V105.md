# INDEPENDENT TECHNICAL REVIEW — TIME WALLET WALLY v1.0.5

- **Vai trò:** Independent Technical Reviewer (độc lập với AI-Master)
- **Ngày kiểm toán:** 2026-11-13
- **Candidate mục tiêu:** `out/wally_candidate_origin/TimeWallet-MVP-v1.0.5-WALLY-ORIGIN-candidate-debug.apk`
- **APK SHA-256 (đã kiểm lại):** `E80F752248DCCE6CCFC7C0B6ADF6B2B8E2D49AC1C3772F5C28738C8CA16D2E1D` (11,736,286 bytes)
- **Package / Signing:** `com.timewallet.mvp` · `versionCode=6` · `versionName=1.0.5` · `compileSdkVersion=35` · `minSdkVersion=26` · `targetSdkVersion=35` · Debug cert SHA-256 `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (trùng các bản trước)
- **Ràng buộc thực thi:** Không sửa source sản phẩm, không build APK mới, không dùng emulator/ADB, không gọi API thật. Chỉ chạy `.\gradlew.bat testDebugUnitTest --offline` trên codebase hiện trạng và chạy kiểm thử tái hiện độc lập tạm thời trên JVM (đã xóa ngay sau khi thu XML/console).

---

## 1. Executive Verdict

- **Verdict kiểm toán:** **`FAIL_FOR_DEVICE_TEST`**
- **Khuyến nghị cuối cùng:** **`FIX`**

### Phân tách trạng thái theo `SPEC_TIME_WALLET_WALLY.md §7`

| Mức đánh giá | Trạng thái | Căn cứ xác minh |
|---|---|---|
| `IMPLEMENTATION_PASS` | **`NO` (`CANDIDATE_UNVERIFIED`)** | SPEC §7 bắt buộc giữ `CANDIDATE_UNVERIFIED` khi `A02` (F6) chưa tái hiện/chứng minh fix. Ngoài ra, kiểm toán độc lập tái hiện được **4 lỗi logic/nhất quán xác nhận được (`REPRO2`–`REPRO5`)**, **3 khiếm khuyết code (`F-V105-04`, `F-V105-06`, `F-V105-07`)**, và các lỗ hổng bổ sung ở **Phụ lục Addendum R01–R04** (Timer > 24h mất dữ liệu đo, thiếu truy vấn Budget, 5 khe hở fallback JSON/plaintext & `ReplyGuard`). |
| `DEVICE_PASS` | **`NOT_TESTED`** | Không chạy emulator, ADB hoặc thiết bị Android thật (`.agents/AGENTS.md` và quyết định Owner). |
| `AI_LIVE_PASS` | **`NOT_TESTED`** | Không có credential/quyền gọi nhà cung cấp thật; mọi bằng chứng AI hiện có chỉ là mock HTTP. |
| `SPEC_ACCEPTED` | **`NO`** | Chưa đóng các lỗi xác nhận được ở mức P1/P2 và còn thiếu toàn bộ `A02`, `A13`, `A14`, `A17`, `A18`. |

### Kiểm chứng Provenance & Build Artifact

- **APK & Snapshot:** `out/wally_candidate_origin/TimeWallet-MVP-v1.0.5-WALLY-ORIGIN-candidate-debug.apk` khớp SHA-256 `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d`; snapshot rollback `out/wally_origin_snapshot_pre/source_v1.0.4_pre_ORIGIN.tar` có SHA-256 `6221B1B86E280F8F096B8BF40823B5650ADCA607A972AB361B63EFB14B0BCBDC`.
- **Nguồn vs Manifest:** Nguồn trong workspace khớp 85/85 mục trong `out/wally_candidate_origin/MANIFEST_SHA256_after.txt`. Lưu ý `WALLY_V105_DOSSIER.md:15` ghi `81/81` là không khớp với số dòng thực tế (`85`) của manifest.
- **Unit Test thực chạy độc lập:** `.\gradlew.bat testDebugUnitTest --offline --rerun-tasks` cho kết quả **222/222 PASS** (21 tệp XML trong cây, `0 failures`, `0 errors`, `0 skipped`). Khi copy tạm `out/ai01_evidence/independent_verification/Ai01IndependentVerificationHarnessTest.kt` (`2` test) vào cây test và chạy lại riêng suite này, cả `2/2` PASS (`46/46` holdout trên `ChatRouter` cũ, `1` boundary spy trên `ChatRouter`), khớp tổng `224/224` của Master.
- **Bằng chứng log bị rỗng:** Ba tệp console trong `out/wally_evidence_origin/` gồm `after_fix_console.txt`, `final/assemble_console.txt`, và `final/console.txt` đều có kích thước **0 byte**.

---

## 2. Findings (Phân tách rạch ròi `CONFIRMED BUG` · `RISK` · `NOT_TESTED`)

### `F-V105-01` — `P1` · `CONFIRMED BUG`: `policyBlocked` xóa sạch `queries` hợp lệ ở câu MIXED kèm phủ định và để sót lời đáp mồ côi

- **Vị trí:** `app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt` · `GeminiProposalClient.finalizeOutcome` (dòng `660–665`).
- **Expected:** Với câu ý định hỗn hợp có vế phủ định + vế truy vấn (ví dụ: `"Hôm nay tôi không học 2h nhưng hôm nay tôi đã dùng bao nhiêu giờ?"`), guard chặn proposal sai nhưng **giữ nguyên `queries` hợp lệ** để `LedgerQueryEngine` trả số liệu thật cho người dùng (`WALLY_QA_REPORT_APIFIRST.md` mục 3: *"không phần nào bị bỏ"*).
- **Actual:** Dòng `663` cố giữ `cleanReply` khi `queries.isNotEmpty()` (`if (policyBlocked && cleanReply != null && queries.isEmpty()) null else cleanReply`), nhưng dòng `664` lại xóa toàn bộ query (`val finalQueries = if (policyBlocked) emptyList() else queries`). Hệ quả kép: (1) mất hoàn toàn kết quả truy vấn hợp lệ (`queries=0`), (2) `finalReply` của AI vẫn được giữ lại dù phần số liệu bên dưới đã bị xóa.
- **Reproduction / Evidence:** Kiểm thử tái hiện độc lập trên `GeminiProposalClient.parseAndValidateResponseJson` (đầu vào JSON hợp lệ có `"durationSeconds": 7200`, `"metric": "TOTAL"`, `reply="Hôm nay bạn đã ghi nhận thời gian như bên dưới."`):
  - Output quan sát được: `REPRO2 proposals=0 queries=0 reply='Hôm nay bạn đã ghi nhận thời gian như bên dưới.' intent=BLOCKED warnings=[Câu có từ phủ định / yêu cầu không ghi nên không tạo bản ghi nào.]`.
- **Tác động người dùng:** Câu hỏi số liệu hợp lệ đi kèm phủ định biến mất hoàn toàn kết quả truy vấn, thậm chí hiển thị câu dẫn mồ côi trỏ tới số liệu không tồn tại.
- **Đề xuất sửa tối thiểu:** Chỉ xóa `queries` khi chính query không hợp lệ (`AiQueryValidator`); đổi dòng `664` thành `val finalQueries = queries`, và đồng bộ điều kiện ẩn `reply` tại `663` theo `finalQueries.isEmpty()` cùng thông số `guardBlocked` trong `WallyIntentClassifier.classify`.

---

### `F-V105-02` — `P1` · `CONFIRMED BUG`: Mâu thuẫn dữ liệu ngân sách giữa Card UI, chú thích màn hình Phân tích và câu gợi ý phản tư

- **Vị trí:**
  - `app/src/main/java/com/timewallet/mvp/domain/model/TimeBudget.kt` · `TimeBudgetProgress` (dòng `86–124`)
  - `app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt` · `TimeBudgetItemCard` (dòng `1250, 1264, 1336–1339`)
  - `app/src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt` (dòng `163–167`, `383–389`)
  - `app/src/main/java/com/timewallet/mvp/domain/usecase/LedgerAnalyticsCalculator.kt` · `buildNeutralBudgetReflection` (dòng `436–446`)
- **Expected:** Số liệu hiển thị trên thẻ ngân sách, chú thích giải thích bên dưới thẻ và thẻ phản tư AI/hệ thống trên cùng màn hình Phân tích phải thống nhất một quy ước tính.
- **Actual:** `TimeBudgetItemCard` hiển thị `progress.combinedUsedFormatted` (`usedSeconds + untimedNoteSeconds`) và `progress.combinedDifferenceLabelVi`. Tuy nhiên ngay bên dưới danh sách thẻ, `InsightsScreen.kt:164` vẫn ghi chú: *"Chỉ thời gian thực tế hợp lệ có mốc giờ mới được tính vào mức đã sử dụng..."*, và bên dưới nữa `buildNeutralBudgetReflection` (`LedgerAnalyticsCalculator.kt:443`) vẫn in `item.usedFormatted` / `item.differenceLabelVi` (chỉ tính bản ghi có mốc giờ).
- **Reproduction / Evidence:** Tái hiện trên `LedgerAnalyticsCalculator.computeWeeklyInsights` với `1h REPORTED_UNTIMED` (Học tập) và ngân sách `DAILY 1h` (Học tập):
  - Output quan sát được: `REPRO4 combined='1h/Còn 0p (Đủ mục tiêu)' timedOnly='0p/Còn 1h' reflection='Đối chiếu ngân sách thực tế của bạn: Học tập (1h/ngày): đã ghi nhận có giờ 0p, còn 1h...'`.
- **Tác động người dùng:** Trên cùng một màn hình Phân tích, card báo `"1h / 1h/ngày"` (`Còn 0p (Đủ mục tiêu)`) trong khi chú thích phủ nhận bản ghi không giờ và hộp phản tư phía dưới báo `"0p, còn 1h"`. Người dùng nhập tay / dùng chat Wally sẽ mất tin cậy vào số liệu ngân sách.
- **Đề xuất sửa tối thiểu:** Thống nhất một quy ước theo quyết định Owner (nếu giữ gộp `untimed` vào ngân sách): cập nhật chú thích tại `InsightsScreen.kt:164` (nêu rõ gồm cả thời lượng chưa gắn giờ, vẫn loại `PLANNED`) và cập nhật `buildNeutralBudgetReflection` dùng `combinedUsedFormatted` + `combinedDifferenceLabelVi` (hoặc tách rõ cả hai phần giống card).

---

### `F-V105-03` — `P1` · `CONFIRMED BUG`: `AiProposalGuard` bỏ lọt đề xuất phủ định khi AI gán danh mục khác parser cục bộ

- **Vị trí:** `app/src/main/java/com/timewallet/mvp/domain/usecase/WallyAiGuards.kt` · `AiProposalGuard.apply` (dòng `112–123`).
- **Expected:** Mệnh đề phủ định (như `"Không học 2h nhưng code 1h"`) không được tạo đề xuất cho phần phủ định (`2h`), bất kể Cloud AI gán tiêu đề hay danh mục gần nghĩa (`"work"` thay vì `"learn"`).
- **Actual:** Dòng `118–120` lọc bằng điều kiện `kept.filterNot { p -> negated.any { n -> n.category == p.category && n.minutes == p.durationMinutes } }`. Chỉ cần AI gán danh mục khác cho vế phủ định, bộ lọc `n.category == p.category` trượt và giữ lại luôn cả đề xuất bị phủ định.
- **Reproduction / Evidence:** Tái hiện với `AiProposalGuard.apply("Không học 2h nhưng code 1h", [Proposal("Ôn tập dự án", category="work", 120m), Proposal("Code", category="work", 60m)])`:
  - Output quan sát được: `REPRO3 keptCount=2 titles=[Ôn tập dự án, Code]` — cả hai đề xuất đều vượt qua guard.
- **Tác động người dùng:** Trong câu hỗn hợp phủ định + khẳng định, đề xuất bị cấm vẫn xuất hiện trên UI nếu AI phân loại hơi lệch (ví dụ `"ôn tập"`, `"đọc tài liệu dự án"` vào `work` thay vì `learn`).
- **Đề xuất sửa tối thiểu:** Khi câu có `negated.isNotEmpty()`, đối chiếu theo danh sách thời lượng hợp lệ của phần khẳng định (`validLocal`) hoặc loại đề xuất có thời lượng khớp `negated` mà không khóa cứng `n.category == p.category` khi `validLocal` không có mục cùng thời lượng đó.

---

### `F-V105-04` — `P1` · `CONFIRMED BUG` (Static + State/Concurrency Analysis): Lỗi đua khi bấm `Hủy ✕` rồi gửi lại ngay hoặc coroutine cũ kết thúc muộn

- **Vị trí:** `app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt` · `analyzeChatInput()` & `cancelAnalysis()` (dòng `754–764`, `820–839`).
- **Expected (SPEC §3.2 / A07):** Sau khi bấm `Hủy ✕`, giao diện thoát trạng thái chờ một cách nhất quán; nếu người dùng bấm gửi câu mới ngay, yêu cầu mới phải chạy độc lập và không bao giờ bị coroutine cũ ghi đè cờ `isAnalyzingChat` hay giải phóng nhầm `chatAnalysisGuard`.
- **Actual:**
  1. `cancelAnalysis()` (dòng `832–837`) đặt `_uiState.isAnalyzingChat = false` **ngay lập tức** (nút Gửi trên UI bật lại), nhưng **không** giải phóng `chatAnalysisGuard` — guard chỉ được giải phóng trong khối `finally` của `analysisJob` (dòng `822`) sau khi coroutine unwind. Nếu người dùng bấm Gửi lại trước khi coroutine cũ thoát khỏi I/O/finally, `analyzeChatInput()` tại dòng `754` gặp `chatAnalysisGuard.tryAcquire() == false` và `return` âm thầm (mất cú bấm, không toast).
  2. Khối `finally` (dòng `821`) gọi `_uiState.update { it.copy(isAnalyzingChat = false) }` không kiểm tra `epochAtStart == analysisEpoch.current()` và `chatAnalysisGuard.release()` không phân biệt token sở hữu.
- **Mức độ kiểm chứng:** Xác nhận trực tiếp trong code ViewModel (`CONCURRENCY DEFECT`); chưa chạy được Android `viewModelScope` coroutine trên JVM thuần (`Main` dispatcher & Room).
- **Đề xuất sửa tối thiểu:** Gắn token/thế hệ vào `ChatAnalysisGuard` (`tryAcquire(): Long?` / `release(token: Long)` hoặc `AtomicReference<Job?>`), chỉ cập nhật `isAnalyzingChat = false` trong `finally` nếu còn giữ đúng token/epoch hiện hành, và thông báo rõ nếu kết nối cũ vẫn đang đóng thay vì im lặng `return`.

---

### `F-V105-05` — `P1` · `CONFIRMED CODE DEFECT / DATA LOSS RISK`: `AesGcmKeystoreVault` tự fallback sang khóa RAM tạm khiến mất khóa sau khi khởi động lại app

- **Vị trí:** `app/src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt` · `AesGcmKeystoreVault` (dòng `100–137`) và `TimerAndSettingsStore.saveAiPreferences` (dòng `378–399`).
- **Expected (SPEC §3.3 / A15):** Không ghi cấu hình AI hoặc báo thành công giả nếu `AndroidKeyStore` thật trên thiết bị không mã hóa được; mọi bản lưu thành công phải giải mã lại được sau khi tắt app bật lại.
- **Actual:** `AesGcmKeystoreVault.resolveSecretKey()` bắt **mọi** `Throwable` (dòng `133`) và trả `volatileFallbackKey` (sinh ngẫu nhiên trong RAM theo từng process tại dòng `104–106`). `encrypt()` vẫn thành công, `saveAiPreferences` trả `true` và bật `ai_opt_in_enabled`, nhưng sang lần mở app tiếp theo khóa `volatileFallbackKey` mới không thể giải mã payload cũ ⇒ khóa mất sạch (`keyUnavailable = true`).
- **Reproduction / Evidence:** Tái hiện bằng `REPRO5` trên JVM với hai instance `AesGcmKeystoreVault()` kế tiếp (mô phỏng restart tiến trình khi `AndroidKeyStore` ném lỗi):
  - Output quan sát được: `saved=true`, sau đó lần nạp thứ 2 cho `REPRO5 reloaded keyLen=0 keyUnavailable=true optIn=false`.
- **Tác động người dùng:** Nếu `AndroidKeyStore` trên một máy Android thật gặp lỗi tạm thời hoặc không tạo được khóa phần cứng, app âm thầm báo đã lưu nhưng người dùng bị văng mất khóa ở mọi lần mở app sau. (Ngoài ra, hành vi thực tế của `AndroidKeyStore` trên máy thật vẫn là `NOT_TESTED`.)
- **Đề xuất sửa tối thiểu:** Tách cờ test/inject `ApiKeyVault` cho JVM test; ở production trên Android, không dùng `volatileFallbackKey` trong `catch` của `AndroidKeyStore` mà để `encrypt()` ném `GeneralSecurityException` cho `saveAiPreferences` bắt tại dòng `381` và trả `false` + báo lỗi cho người dùng.

---

### `F-V105-06` — `P2` · `CONFIRMED CODE DEFECT`: `AiSettingsDialog` và `ProductInfoDialog` không hỗ trợ cuộn dọc

- **Vị trí:** `app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt` · `AiSettingsDialog` (dòng `928`) và `ProductInfoDialog` (dòng `1093`).
- **Expected (`SPEC §5`):** Mọi dialog có form nhập liệu hoặc văn bản dài phải cuộn được và tiếp cận được nút Lưu/Hủy trên màn hình `320dp`, `fontScale 1.3`, và khi mở bàn phím (tương tự `TimeBudgetEditorDialog:1411` đã dùng `.verticalScroll(rememberScrollState())`).
- **Actual:** Cả hai dialog dùng `Column(...)` không có `.verticalScroll(rememberScrollState())`. `AiSettingsDialog` chứa hộp giải thích 3 gạch đầu dòng, công tắc Opt-in, 3 `OutlinedTextField` (Base URL, API key, Model), thông báo test kết nối và khu vực chẩn đoán; `ProductInfoDialog` chứa giới thiệu 5 trạng thái sổ cái và có thể kèm `crashReport`.
- **Tác động người dùng:** Trên màn hình nhỏ / cỡ chữ `1.3x` / bàn phím mở, nội dung và nút hành động có nguy cơ bị cắt hoặc đẩy ra ngoài viewport (chưa quan sát runtime trên máy: `NOT_TESTED`).
- **Đề xuất sửa tối thiểu:** Thêm `.verticalScroll(rememberScrollState())` vào `Column` nội dung của `AiSettingsDialog` và `ProductInfoDialog`.

---

### `F-V105-07` — `P2` · `CONFIRMED CODE DEFECT`: Một số điều khiển phụ thiếu vùng chạm tối thiểu `48dp` trong code

- **Vị trí:**
  - `app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt` · badge đồng hồ top bar (`dòng 150–156`: không có `minimumInteractiveComponentSize()`, chỉ `padding(horizontal = 9.dp, vertical = 5.dp)`)
  - `app/src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt` · nút `"Bỏ hết"` (`dòng 348–357`), nút mở rộng/xóa từng draft `"✎"` / `"×"` (`dòng 480–500`), nút `"Đóng ✕"` trên thẻ trả lời (`dòng 681–687`).
- **Expected (`SPEC §5`):** Vùng chạm đủ lớn (`>= 48dp` cho thao tác chính và tránh chạm nhầm khi thao tác ngón tay trên nút xóa `"×"` / `"Bỏ hết"`).
- **Actual:** Chỉ áp dụng `.padding(...)` nhỏ trên `Text` có `.clickable(...)`, chưa đặt `minimumInteractiveComponentSize()` / `heightIn(min = 48.dp)` (khác với bottom bar và `TimeBudgetEditorDialog` chips đã áp dụng). Kích thước hiển thị vật lý trên thiết bị thật hiện là `NOT_TESTED`.
- **Đề xuất sửa tối thiểu:** Dùng `minimumInteractiveComponentSize()` hoặc `IconButton` chuẩn cho các nút thao tác rời rạc trên.

---

### `F-V105-08` — `P2` · `RISK` (JVM `PASS`, Android ART `NOT_TESTED`): Đọc `optString("content")` thiếu kiểm tra `isNull("content")`

- **Vị trí:** `app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt` · dòng `497–501` và `526–531`.
- **Expected:** Nếu API trả `choices[0].message.content = null` (thường gặp khi model gọi tool hoặc bị lọc nội dung), phải ném `AiFault.MALFORMED` (hoặc xử lý rõ), không bao giờ hiển thị chuỗi `"null"` cho người dùng.
- **Actual & Evidence:** Khác với dòng `567, 573, 636, 648` trong cùng lớp đã tự kiểm `isNull(...)`, dòng `499` gọi thẳng `?.optString("content")`. Khi kiểm chứng trên JVM với `org.json:json:20231013`, `{"choices":[{"message":{"role":"assistant","content":null}}]}` trả `""` và ném `AiHttpException: MALFORMED` tại dòng `530` (đã xác nhận bằng thực nghiệm). Tuy nhiên trên Android runtime (`org.json` của AOSP, `NOT_TESTED`), `JSONObject.optString` với `JSONObject.NULL` có rủi ro lịch sử trả về chuỗi `"null"`, khi đó điều kiện dòng `526` (`cleaned.isNotEmpty()`) sẽ nhận `"null"` làm lời đáp hợp lệ.
- **Đề xuất sửa tối thiểu:** Bổ sung `val msg = choices.getJSONObject(0).optJSONObject("message"); if (msg == null || msg.isNull("content")) throw AiHttpException(AiFault.MALFORMED)`.

---

### `F-V105-09` — `P2` · `RISK`: Ticker 1 giây chạy vô điều kiện, `collectAsState()` không theo vòng đời và `WallyChatReducer` tính lại truy vấn trên Main thread

- **Vị trí:**
  - `app/src/main/java/com/timewallet/mvp/MainActivity.kt` (dòng `83`, kèm warning compiler ở dòng `85` về `LocalLifecycleOwner` deprecated)
  - `app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt` (dòng `310–320` và `781–811`)
  - `app/src/main/java/com/timewallet/mvp/ui/WallyChatReducer.kt` (dòng `65–68`)
  - `app/src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt` (dòng `145–189` — Master đã ghi nhận tại `WALLY_QA_REPORT.md:156`)
- **Mức độ chứng minh:** Đọc code trực tiếp (`RISK`); độ trễ khung hình thật trên Android `NOT_TESTED`.
- **Chi tiết kỹ thuật:**
  1. `TimeWalletAppRoot` dùng `collectAsState()` dù project đã khai báo dependency `androidx.lifecycle.runtime.compose` (`app/build.gradle.kts:50`).
  2. Vòng lặp `while (isActive)` trong `ViewModel.init` (`310–320`) đánh thức coroutine mỗi 1s và chạy `recomputeStats` (đồng bộ trên Main) mỗi 15s kể cả khi không có đồng hồ chạy hoặc app xuống nền.
  3. Khi sổ cái/timer đổi trong lúc đợi mạng AI, `WallyChatReducer.applyResult` (`WallyChatReducer.kt:67`) chạy `compute(state.records, state.activeTimer)` (`LedgerQueryEngine.execute`) ngay bên trong `_uiState.update` trên Main thread. Theo benchmark `out/wally_evidence_8layer/query_benchmark.txt`, kỳ 93 ngày đạt `p95 = 179 ms` ở 3.650 bản ghi và `718 ms` ở 21.900 bản ghi trên JVM máy dev (điện thoại thật sẽ chậm hơn).
- **Đề xuất sửa tối thiểu:** Dùng `collectAsStateWithLifecycle()`, chỉ tick 1s khi có `activeTimer != null` hoặc đang foreground, và nếu cần tính lại `answers` thì đẩy sang `Dispatchers.Default` trước khi áp state.

---

### `F-V105-10` — `P2` · `UNRESOLVED / NOT_TESTED` + Lỗ hổng hồ sơ kiểm chứng: F6 chưa xác định nguyên nhân và bằng chứng log origin bị rỗng

- **Vị trí:** `SPEC_TIME_WALLET_WALLY.md §3.1`, `out/wally_evidence_origin/`, `WALLY_OWNER_CHECKLIST_APIFIRST.md`, `WALLY_V105_DOSSIER.md`.
- **Chi tiết:**
  - **F6 ("Time Wallet tiếp tục dừng"):** Vẫn `UNRESOLVED / NOT_REPRODUCED`. Việc thêm `safeHandler` (`TimeWalletViewModel.kt:239`) và `CrashRecorder` là biện pháp thu log và phòng ngừa coroutine, chưa phải chứng minh nguyên nhân gốc trên máy Owner.
  - **Log origin bị rỗng:** Ba file `out/wally_evidence_origin/after_fix_console.txt`, `final/assemble_console.txt`, và `final/console.txt` đều `0 bytes`. Số test `224/224` đạt được nhờ copy tạm `Ai01IndependentVerificationHarnessTest.kt` từ ngoài cây (`222` test nằm trong `app/src/test`).
  - **Tài liệu Owner lệch bản:** `WALLY_V105_DOSSIER.md:30` dẫn Owner tới `WALLY_OWNER_CHECKLIST_APIFIRST.md`, nhưng file checklist này vẫn ghi tiêu đề bản `1.0.3` / `versionCode 4`, thiếu kiểm tra prompt cache line (`WALLY_QA_REPORT_8LAYER.md:127`) và hành vi reset khóa custom-port `K3`.

---

## 3. Kiểm toán chi tiết 5 mảng bắt buộc (A–E) & Ma trận 8 tầng AI

### A. Product & UI/UX

| Hạng mục kiểm tra | Trạng thái | Bằng chứng mã nguồn & nhận xét |
|---|---|---|
| Đường tiếp cận Manual Entry từ Dashboard & Nhật ký | **IMPLEMENTED (code)** / UI `NOT_TESTED` | `MainActivity.kt:128-160` hiển thị `FAB +` (`contentDescription = "Thêm giờ thủ công"`) trên `OVERVIEW` (khi không mở Daily Review) và `HISTORY`; `OverviewScreen.kt:232` có `QuickActionTile("＋", "Thêm giờ")`. `ManualEntrySheet.kt:69-174` cho phép chọn 9 danh mục, 4 preset, ngày (hôm nay/hôm qua/DatePicker) và lưu không cần key/mạng. |
| Thêm / sửa / xóa bản ghi cập nhật nhất quán | **PASS (JVM)** / Room runtime `NOT_TESTED` | `BatchConfirm.run` kiểm tra validate chung; `deleteRecord` (`TimeWalletViewModel.kt:1082-1091`) và `clearAllData` (`1093-1106`) dùng `database.withTransaction`; các luồng Room Flow đồng bộ lại `recomputeStats`. |
| Giảm chữ, tăng icon, biểu đồ và trực quan hóa | **IMPLEMENTED (code)** / Visual `NOT_TESTED` | `OverviewScreen.kt` đặt Hero card ví thời gian (số lớn, không gọi phần chưa ghi nhận là giờ rảnh) → 3 Quick actions → Donut theo danh mục (legend kèm emoji, tên, giờ, %) → Trục 24h → Gần đây. |
| Budget Dialog F5 (`FlowRow` responsive) | **IMPLEMENTED (static)** / Visual `NOT_TESTED` | `TimeBudgetEditorDialog` (`ComponentsAndDialogs.kt:1468-1503`) tách nhãn `"Chọn nhanh (...)"` riêng dòng, dùng `FlowRow` cho chip `5h/10h/15h/20h` (`softWrap = false`, `minimumInteractiveComponentSize()`), và bọc `verticalScroll`. |
| Mất chức năng cũ / lỗi giao diện phát hiện | **CONFIRMED BUGS (code/logic)** | Không mất 5 tab hay chức năng cũ (Timer/Daily Review/Feynman/Export/Budget giữ nguyên), **nhưng** dính `F-V105-02` (mâu thuẫn Budget UI vs Insights footnote/reflection), `F-V105-06` (`AiSettingsDialog`/`ProductInfoDialog` thiếu scroll), và `F-V105-07` (vùng chạm <48dp ở nút phụ Chat/TopBar). Chưa có kiểm thử trực quan 320/360/390/412dp & font 1.3 (`NOT_TESTED`). |

### B. Ma trận kiến trúc AI 8 tầng (`SPEC §2`)

| Tầng | Tên tầng | Trạng thái | Bằng chứng & Nhận xét độc lập |
|---|---|---|---|
| **01** | Model & Model Serving | **IMPLEMENTED (mock)** · Live `NOT_TESTED` | `GeminiProposalClient` (`analyzeInput`, timeout 15s/25s, tổng hạn 30s, tối đa 1 retry cho lỗi tạm thời, `Retry-After` tối đa 10s, hủy đóng socket qua `CoroutineStart.UNDISPATCHED`, `MAX_BODY_CHARS=512KB`, `AiFault` 10 lớp, `testConnection` gửi `"ping"`). Live API `NOT_TESTED`. |
| **02** | System Prompt & Prompt Management | **PARTIAL** | Có `PROMPT_VERSION = "wally-prompt-3"`, tiền tố tĩnh cố định, schema JSON (`reply`, `proposals`, `queries`), validator độc lập. **Thiếu:** Chưa có eval với mô hình AI thật; golden 66 và holdout 46 chỉ chạy trên parser cục bộ cũ (`ChatRouter`), không chứng minh được prompt v3. |
| **03** | RAG & Knowledge Lifecycle | **PARTIAL** | `LedgerQueryEngine` tính số liệu cục bộ từ sổ cái trong khoảng ≤93 ngày, không gửi sổ cái lên cloud. **Lỗ hổng xác nhận:** `buildNeutralBudgetReflection` trên màn hình Phân tích (`F-V105-02`) dùng `usedSeconds` thay vì khớp với thẻ `combinedUsedSeconds`. |
| **04** | Tools, MCP & Agent Orchestration | **PARTIAL** | AI chỉ trả JSON đề xuất/truy vấn (`needsConfirmation = true`); ghi DB chỉ qua `BatchConfirm.run` sau khi người dùng xác nhận; có `ProposalLifecycle`, `confirmGuard`, `manualSaveGuard`. **Lỗ hổng xác nhận:** `F-V105-01` xóa `queries` ở MIXED+phủ định; `F-V105-04` race khi `Cancel`→`Resend`; coroutine/Compose VM chưa chạy runtime test. |
| **05** | Identity, RBAC/ABAC & Authorization | **PARTIAL** | Quyền thực thi bằng code; chỉ xin `android.permission.INTERNET`; `allowBackup="false"` + `backup_rules.xml`/`data_extraction_rules.xml`; `AiPreferences.extractValidatedHttpsHost` + `originOf` gắn khóa theo `scheme + host + port`. **Lỗ hổng xác nhận:** `F-V105-05` fallback `volatileFallbackKey` khi Keystore lỗi gây mất khóa khi restart; Keystore thật `NOT_TESTED`. |
| **06** | Guardrails & AI Security | **PARTIAL** | `AiQueryValidator`, `CancelCommandParser`, `ReplyGuard` (chỉ kiểm đơn vị thời gian), từ chối schema/JSON hỏng và ép `needsConfirmation=true` (`WallyAiFaultInjectionTest` 34/34). **Lỗ hổng xác nhận:** `F-V105-03` lọt phủ định khác category; `F-V105-08` rủi ro `optString("content")` trên AOSP `org.json`; injection trên model thật `NOT_TESTED`. |
| **07** | Cache, Router & Cost Optimization | **PARTIAL** | Router **API-first** theo đúng quyết định Owner mới (mọi câu ngôn ngữ tự nhiên đi cloud nếu đã cấu hình, lệnh hủy xử lý cục bộ trước client). Prompt prefix tĩnh đã verify byte-identical (`WallyPromptPrefixContractTest`). Tuy nhiên hỗ trợ cache phía `api.foza.ai` = `UNVERIFIED`, cache hit thực tế = `NOT_TESTED`, cost routing = `NOT_IMPLEMENTED`, data/response cache = `NOT_IMPLEMENTED` có chủ đích (kèm benchmark `query_benchmark.txt`). |
| **08** | Observability, Evaluation & Release Gates | **PARTIAL** | `AiDiagnostics` (không lưu text chat/khóa), `AiUsageParser`, `CrashRecorder`, APK/manifest/snapshot đầy đủ. Tuy nhiên `K2` làm bộ đếm `CONTROL` trên app luôn 0, console log trong `out/wally_evidence_origin/final/` rỗng 0 byte, `WALLY_V105_DOSSIER.md` ghi lệch `81/81` vs `85`, Device & Live eval gates = `NOT_TESTED`. |

### C. Security & Data Integrity

- **Điểm đạt (JVM / Static):**
  - `WallyOriginBindingTest` (11/11) kiểm chứng bằng `HttpURLConnection` JDK thật + 2 local HTTP sockets: mọi mã `301/302/303/307/308` đều không bị theo dõi (`instanceFollowRedirects = false` đặt trước header `Authorization`), `extractValidatedHttpsHost` và `AiPreferences.originOf` khớp cùng chuẩn `host` (443) vs `host:port` (1..65535), chấp nhận IPv6/case/trailing-dot nhất quán và từ chối `http`, `userinfo`, query, fragment.
  - Quét tĩnh xác nhận chỉ `GeminiProposalClient` mở kết nối và đặt header `Authorization` (1 điểm duy nhất); `WallySecurityStaticTest` xác nhận không lộ API key plaintext trong `SharedPreferences`, `AiPreferences.toString()`, `LedgerJsonExporter`, `AiDiagnostics`, hay mã nguồn; `allowBackup="false"` kèm `backup_rules.xml` & `data_extraction_rules.xml`.
  - Atomic Room delete & clear-all sử dụng `database.withTransaction` (`TimeWalletViewModel.kt:1085, 1099`); `MIGRATION_1_2` & `MIGRATION_2_3` bảo toàn dữ liệu cũ (chưa đổi schema v3).
- **Lỗ hổng & phần chưa kiểm chứng:**
  - `F-V105-05` (`CONFIRMED`): `AesGcmKeystoreVault` bắt mọi `Throwable` dùng khóa RAM tạm làm `saveAiPreferences` báo thành công giả rồi mất khóa khi restart.
  - Redirect trên Android `OkHttp`-backed `HttpURLConnection` thật, behavior `AndroidKeyStore` thật, và Room migration/transaction trên SQLite Android thật đều `NOT_TESTED` (test dùng `InMemoryLedgerDao`, `InMemorySharedPreferences`, và JDK socket). Không có certificate pinning (chấp nhận mọi CA tin cậy hệ thống); tên miền IDN/homograph ngoài phạm vi chuẩn hóa.

### D. Runtime & Reliability

- **Xử lý lỗi mạng & parser (mock):** Các ca offline/DNS, TLS, timeout (15s connect, 25s read, 30s deadline, tối đa 1 retry), `401/403/404/429 (Retry-After)/5xx`, response > 512KB và JSON hỏng được kiểm kỹ qua adapter `GeminiProposalClient` trong `WallyAiFaultInjectionTest` (34/34).
- **Vấn đề runtime còn mở:**
  1. `F-V105-04`: Race giữa `cancelAnalysis()` (mở lại nút Gửi ngay) và `chatAnalysisGuard.release()` nằm cuối `analysisJob` `finally`, cộng `isAnalyzingChat = false` không check epoch.
  2. `F-V105-08`: Rủi ro `"null"` literal trên Android AOSP `org.json` nếu `choices[0].message.content` là JSON `null`.
  3. `F-V105-09`: Ticker 1s chạy vô điều kiện trong ViewModel + `collectAsState()` chưa lifecycle-aware + `computeAnswers` chạy trên Main thread trong `WallyChatReducer` khi sổ cái đổi mid-flight + `HistoryScreen` không dùng lazy list.
  4. `F6`: Vẫn `UNRESOLVED / NOT_REPRODUCED`.

### E. Chất lượng Test & đánh giá `WALLY_LIVE_EVAL_PLAN.md`

- **Đối soát test thực tế (`222` in-tree + `2` holdout ngoài cây):**
  - Toàn bộ **21 suite / 222 test** trong `app/src/test` PASS (`TimeWalletDomainTest` 25, `Ai01ClosureLifecycleTest` 15, `Ai01ClosureParserTest` 9, `Ai01ConversationalAnalyticsTest` 11, `Ai01FeedbackAndGuardTest` 11, `Ai01IntegrityReproTest` 10, `Ai01NegationFinalFixTest` 10, `WallyAiFaultInjectionTest` 34, `WallyApiFirstContractTest` 5, `WallyApiFirstGuardsTest` 13, `WallyBudgetTest` 8, `WallyChatLifecycleTest` 8, `WallyDashboardSummaryTest` 6, `WallyDiagnosticsTest` 4, `WallyEightLayerTest` 16, `WallyKeystoreFaultTest` 4, `WallyManualEntryTest` 11, `WallyOriginBindingTest` 11, `WallyPromptPrefixContractTest` 1, `WallySecurityStaticTest` 4, `WallyUiStaticTest` 6).
  - **Khoảng trống chất lượng test:**
    1. `app/build.gradle.kts:42` bật `unitTests.isReturnDefaultValues = true` nhưng không có Compose UI test (`createComposeRule`), Robolectric, test `TimeWalletViewModel` coroutine thật, hay test `Room` migration/index trên SQLite.
    2. Nhiều test trong `WallyUiStaticTest`, `WallySecurityStaticTest`, `WallyChatLifecycleTest`, `WallyBudgetTest` kiểm tra bằng cách đọc chuỗi file `.kt` (static grep) nên không phát hiện lỗi UI/runtime như `AiSettingsDialog` thiếu scroll (`F-V105-06`) hay race ở `cancelAnalysis` (`F-V105-04`).
    3. Golden `66/66` và Holdout `46/46` (`Ai01IndependentVerificationHarnessTest`) chạy trên `ChatRouter`/`VietnameseChatParser` cũ, **không** kiểm tra pipeline API-first (`GeminiProposalClient` + `TimeWalletViewModel`).
- **Đánh giá `WALLY_LIVE_EVAL_PLAN.md`:**
  - **Đủ tốt cho mảng Chat AI thật:** Tổ chức tốt 6 nhóm A–F (Ghi nhận, Truy vấn, Hỗn hợp, Phủ định/Sửa lại, Hủy cục bộ, Lỗi mạng), tách rõ dữ liệu thử sạch, quy trình chẩn đoán cache/token/latency (`HIT/MISS/KHÔNG_BÁO`), tiêu chí bất biến 100% và `intent=CONTROL/BLOCKED/MIXED`. Thậm chí nếu chạy bài `D4`/`C1` kết hợp (`"Không học 2h nhưng hôm nay tôi dùng bao nhiêu?"`), kế hoạch này đủ sức phát hiện bug nếu dùng câu hỗn hợp phủ định+truy vấn.
  - **Chưa đủ làm hồ sơ nghiệm thu trọn vẹn v1.0.5:** Mới đo phần Wally AI chat; thiếu hẳn kịch bản nghiệm thu trực tiếp cho Manual Entry (thêm 4 thao tác, sửa/xóa, persistence sau restart), kiểm chứng mâu thuẫn Budget UI vs Insights footnote/reflection, upgrade dữ liệu từ v1.0.4 → v1.0.5 (bao gồm case K3 đổi cổng), và check viewport `320dp` / `fontScale 1.3`. File checklist đi kèm `WALLY_OWNER_CHECKLIST_APIFIRST.md` vẫn ghi nhãn v1.0.3 / `versionCode 4`.

---

## 4. Acceptance Audit (`A01–A18` theo đúng `SPEC_TIME_WALLET_WALLY.md §6`)

| ID | Điều kiện PASS theo SPEC | Trạng thái | Bằng chứng & Chênh lệch độc lập |
|---|---|---|---|
| **A01** | Không regression baseline F1–F4 + golden/holdout; giữ expected cũ; báo count từng suite | **PASS (JVM)** | 222/222 in-tree + 2/2 external holdout (`46/46`, `ChatRouter`) PASS; golden `66/66`. 3 sửa đổi API-first được ghi minh bạch ở `OLD_TESTS_CHANGED_INTENTIONALLY.patch`. (Lưu ý log console ở `out/wally_evidence_origin/final/console.txt` bị 0 byte.) |
| **A02** | F6 reproduction: root cause xác định, fail-before/pass-after hoặc evidence tương đương; luồng thực tế không crash | **NOT_TESTED** | F6 vẫn `UNRESOLVED / NOT_REPRODUCED`. `CrashRecorder` & `safeHandler` chỉ là phòng vệ và công cụ ghi log lần sau. |
| **A03** | AI success local/cloud: intent đúng, answer/proposal hợp lệ; chỉ commit sau xác nhận | **PARTIAL** | Mock HTTP pass cho câu đơn/hỗn hợp thông thường và commit qua `BatchConfirm`. **FAIL** ở câu MIXED bị `policyBlocked` xóa sạch `queries` hợp lệ (`F-V105-01` / `REPRO2`) và lọt phủ định lệch category (`F-V105-03` / `REPRO3`). Live AI: `NOT_TESTED`. |
| **A04** | Offline/DNS/TLS/timeout: không crash, loading kết thúc trong budget, input giữ, manual hoạt động | **PARTIAL** | Adapter HTTP + mock pass (`WallyAiFaultInjectionTest` 34/34, deadline 30s, reducer giữ input). UI & Android runtime: `NOT_TESTED`. |
| **A05** | 401/403/404/429/5xx: mapping đúng, retry policy đúng, không ghi DB | **PARTIAL** | Pass trên mock HTTP tại `GeminiProposalClient` (`WallyAiFaultInjectionTest`), client tách khỏi DAO. Thiết bị & UI thật: `NOT_TESTED`. |
| **A06** | Malformed JSON/schema/injection: không commit, không bypass confirmation, không lộ bí mật | **PARTIAL** | Pass schema hỏng & injection cơ bản trên mock; **còn lỗ hổng** `F-V105-03` (phủ định lệch category) và `F-V105-08` (rủi ro `optString("content")` với JSON `null` trên Android `org.json`). Chưa test trên LLM thật (`NOT_TESTED`). |
| **A07** | Cancel/stale response/tab/background: không hồi sinh proposal, không crash, không ghi ngầm | **FAIL (code race) / NOT_TESTED (UI lifecycle)** | `ChatPhaseMachine`, `AnalysisEpoch` và đóng socket pass mức unit/mock, nhưng tồn tại **bug race `F-V105-04`** giữa `cancelAnalysis()` và `analyzeChatInput()` (`chatAnalysisGuard` + `finally` thiếu check epoch). Đổi tab/background/process recreation: `NOT_TESTED`. |
| **A08** | Duplicate send/confirm/retry: một commit cho một proposal; hai hoạt động hợp lệ giống nhau vẫn ghi riêng | **PASS (JVM)** / Room thật `NOT_TESTED` | `confirmGuard`, `manualSaveGuard`, `idempotencyKey` kiểm qua `BatchConfirm.run` trên `InMemoryLedgerDao` (`WallyEightLayerTest`, `WallyManualEntryTest`). Room SQLite thật: `NOT_TESTED`. |
| **A09** | Manual 30m, custom duration, ngày quá khứ, đủ 9 category: lưu đúng, offline, không cần key; cancel/invalid không ghi | **PASS (JVM)** / UI `NOT_TESTED` | `WallyManualEntryTest` (11/11) pass cho logic và `BatchConfirm`; `ManualEntrySheet` trên Android thật: `NOT_TESTED`. |
| **A10** | Manual edit/delete/restart: Ledger và mọi tổng cập nhật đúng; persistence đúng | **PASS (domain)** / Restart thật `NOT_TESTED` | `deleteRecord` chạy trong `withTransaction`; tổng & truy vấn phản ánh sau thêm/sửa/xóa (`WallyEightLayerTest`). Restart SQLite trên máy thật: `NOT_TESTED`. |
| **A11** | Budget ngày/tuần, preset, priority: tính đúng, tối đa 3 ưu tiên, không duplicate, F5 hết | **FAIL (nhất quán phản tư & chú thích)** / F5 visual `NOT_TESTED` | Logic `computeBudgetProgress` & `BudgetPolicy` (3 ưu tiên) pass `WallyBudgetTest` (8/8), F5 `FlowRow` đạt kiểm tĩnh. Nhưng `InsightsScreen.kt:164` & `buildNeutralBudgetReflection` **mâu thuẫn trực tiếp** với card UI (`F-V105-02` / `REPRO4`). |
| **A12** | Dashboard/chart empty/mixed/no timestamp: số đúng, không NaN, không suy diễn thời gian rảnh; không đưa duration-only vào trục giờ | **PASS (JVM)** | `WallyDashboardSummaryTest` (6/6), `WallyUiStaticTest` (`JournalDayTotal`), `TimeWalletDomainTest` (25/25) đều pass, tách đúng `untimed` & `planned` khỏi trục giờ. |
| **A13** | UI viewport/font/keyboard đạt visual acceptance mục 5, có screenshot | **FAIL (thiếu scroll & 48dp ở code)** + **`NOT_TESTED` (visual runtime)** | Không có screenshot runtime. Kiểm tĩnh phát hiện `AiSettingsDialog` & `ProductInfoDialog` thiếu `.verticalScroll()` (`F-V105-06`) và một số nút phụ thiếu 48dp (`F-V105-07`). |
| **A14** | Upgrade từ APK cũ có dữ liệu synthetic: cài đè tương thích, giữ ledger/budget/settings; migration không destructive | **NOT_TESTED** | Signing cert trùng `402f1d41...`, `versionCode 6 > 5`, `MIGRATION_1_2` & `MIGRATION_2_3` tồn tại; chưa chạy cài đè thật hoặc Room migration test. |
| **A15** | Key security: không plaintext key trong storage mới/log/export/backup; test migration và key unavailable | **FAIL (`F-V105-05` fallback RAM)** / Keystore thật `NOT_TESTED` | Tĩnh & origin-binding trên JVM pass (`WallySecurityStaticTest` 4/4, `WallyOriginBindingTest` 11/11). Tuy nhiên `AesGcmKeystoreVault` tự fallback sang khóa RAM tạm khi `AndroidKeyStore` lỗi làm lưu thành công giả rồi mất khóa sau restart (`REPRO5`). |
| **A16** | Build APK thành công, artifact tồn tại, SHA256, package/version/signing compatibility ghi rõ | **PARTIAL** | APK tồn tại, SHA-256 đúng (`e80f7522...`), signing/versionCode hợp lệ, source khớp 85/85 dòng manifest; nhưng log build/test gốc (`final/assemble_console.txt`, `final/console.txt`) rỗng 0 byte và `WALLY_V105_DOSSIER.md` ghi lệch `81/81`. |
| **A17** | Real Android device: cài/upgrade, cold start, manual, budget, AI success/error, background/restart | **NOT_TESTED** | Không có chạy trên thiết bị thật / emulator. |
| **A18** | Live provider: request synthetic thực thành công, model/provider đúng; không lộ key/dữ liệu thật | **NOT_TESTED** | Không có API key thật / chưa chạy `WALLY_LIVE_EVAL_PLAN.md`. |

---

## 5. Khuyến nghị cuối cùng: **`FIX`**

Cần sửa gọn trong phạm vi v1.0.5 (không mở tính năng v1.0.6) trước khi đưa bản mới tới Owner nghiệm thu thiết bị và AI thật:

1. **Sửa `GeminiProposalClient.kt:660–665` (`F-V105-01`)** để `policyBlocked` không làm rơi `queries` hợp lệ trong câu ý định hỗn hợp và đồng bộ ẩn/hiện `reply`.
2. **Thống nhất UI/Reflection Ngân sách (`F-V105-02`)** giữa `ComponentsAndDialogs.kt`, `InsightsScreen.kt:164` và `LedgerAnalyticsCalculator.buildNeutralBudgetReflection`.
3. **Gỡ ràng buộc cứng `n.category == p.category` ở `AiProposalGuard` (`F-V105-03`)** khi loại bỏ mệnh đề phủ định.
4. **Sửa race `cancelAnalysis()` + `chatAnalysisGuard` + epoch check ở `finally` (`F-V105-04`)** trong `TimeWalletViewModel.kt`.
5. **Chặn silent RAM fallback trong `AesGcmKeystoreVault` ở runtime production (`F-V105-05`)**, chỉ giữ inject/test vault cho JVM test; đồng thời thêm `msg.isNull("content")` ở `GeminiProposalClient.kt:499` (`F-V105-08`).
6. **Bổ sung `.verticalScroll(rememberScrollState())` cho `AiSettingsDialog` & `ProductInfoDialog` (`F-V105-06`)**, đảm bảo touch target `48dp` cho nút phụ (`F-V105-07`), lưu đầy đủ console log build/test thay cho tệp 0 byte, và cập nhật checklist Owner đúng phiên bản kèm kiểm tra Manual Entry / Budget reflection / upgrade.
7. **Khắc phục mất dữ liệu Timer > 24h (`R01`, ưu tiên dữ liệu):** Thêm bộ tách segment theo ranh giới ngày/`<= 86_400s` với khóa `timer_${startMs}_seg_$i`, lưu giao dịch nguyên tử và UI khôi phục khi chạm overlap thay vì chỉ cho phép `Hủy phiên` làm mất trắng thời gian `MEASURED`.
8. **Siết parser/guard fallback AI (`R03`):** Trả `AiFault.MALFORMED` khi field `proposals`/`queries`/`reply` sai kiểu JSON hoặc cắt đuôi JSON rớt mảng, chặn plaintext False Success tuyên bố đã lưu khi `proposals.isEmpty()`, và mở rộng `ReplyGuard` cho `%`/số chữ/câu trả lời tự khẳng định dựa trên số từ `userText`.
9. **Làm rõ phạm vi Budget Q&A (`R02`) & ngữ nghĩa tổng ngày > 24h (`R04`):** Bổ sung `QueryMetric.BUDGET` hoặc khai báo giới hạn rõ ở UI/Technical Spec; thống nhất thuật ngữ `"đã ghi nhận"` vs `"có mốc giờ"` và bổ sung cảnh báo khả năng trùng lặp khi nhập thêm `REPORTED_UNTIMED`.


---

## Phụ lục Kiểm định Addendum (2026-11-13) — R01–R04 & Đối chiếu `WALLY_TECHNICAL_SPEC.md`

Phần này bổ sung kết quả xác minh độc lập theo **Review Addendum (2026-11-13)** dựa trên đọc trực tiếp source/SPEC/Technical Spec và chạy kiểm chứng JUnit thực nghiệm trên mã nguồn production (`ReviewerAddendumReproTest`, **4/4 PASS**, đã dọn sạch file tạm sau khi chạy).

### 1. Xác minh chi tiết R01–R04

| ID | Hạng mục | Phân loại | Bằng chứng source & thực nghiệm | Tác động thực tế |
|---|---|---|---|---|
| **R01** | **Timer > 24h mất toàn bộ thời gian đo** | **CONFIRMED BUG (Ưu tiên dữ liệu / High)** | `TimeWalletViewModel.kt:599-666` (`stopAndSaveTimer`), `LedgerValidator.kt:175-178` (`> 86_400L`), `LedgerDao.kt:75-77`, `TimeWalletViewModel.kt:668-680` (`cancelRunningTimer`), `TimerScreen.kt:235-293`. Thực nghiệm: `25h` và `86_401s` đều trả `ValidationResult.Invalid("Một bản ghi đơn lẻ không vượt quá 24 giờ.")`; phiên `3h` xuyên nửa đêm (`22:00`→`01:00`, `10_800s`) lưu thành công. | Khi đồng hồ chạy quá `86_400s`, nút `"■ Kết thúc & lưu"` chỉ báo toast lỗi và giữ timer kẹt trạng thái đang chạy (chặn bắt đầu timer mới và gây xung đột overlap). Người dùng chỉ còn lựa chọn `"Hủy phiên"` → mất 100% thời gian `MEASURED`. UI `TimerScreen.kt` không có cảnh báo hay hướng dẫn khôi phục. |
| **R02** | **Truy vấn mục tiêu / ngân sách còn thiếu qua Wally** | **DESIGN LIMITATION + CONFIRMED BUG (UX khi hỏi ngân sách)** | `QueryIntentParser.kt:19` chỉ có `TOTAL, UNTIMED, PLANNED, TOP_CATEGORY, LEAST_CATEGORY, BREAKDOWN`; `GeminiProposalClient.kt:284, 320` không cấp `budgets` vào prompt; `LedgerQueryEngine.kt:39-45` không nhận `budgets` (trong khi `TimeWalletViewModel.kt:781-790` không truyền `_uiState.value.budgets`). Thực nghiệm với câu `"Tuần này tôi học bao nhiêu giờ, còn thiếu bao nhiêu so với mục tiêu?"`: `computeBudgetProgress` tính đúng `remainingSeconds = 28_800s` (còn thiếu 8h/10h), nhưng `LedgerQueryEngine.execute(TOTAL)` chỉ trả `"09/11–15/11 — Học tập: 2h có mốc giờ."` (`lines=[]`). Nếu AI tự bịa `"metric":"BUDGET"` + reply `"còn thiếu 8h"`, `AiQueryValidator` từ chối (`"Loại số liệu không hợp lệ."`) và `ReplyGuard` xóa sạch reply → `queries=0, reply=null, intent=CLARIFY` kèm 2 cảnh báo kỹ thuật. | Wally không thể trả lời mục tiêu và thời lượng còn thiếu của người dùng dù dữ liệu và `computeBudgetProgress` đã có sẵn ở tầng domain. `WALLY_TECHNICAL_SPEC.md §11` cũng không khai báo giới hạn này. |
| **R03** | **JSON/plaintext fallback, schema sai kiểu & `ReplyGuard`** | **CONFIRMED BUGS (5 lỗ hổng) + Guard cơ bản hoạt động trên cả 2 nhánh** | `GeminiProposalClient.kt:515-537, 633, 648, 653-677`, `WallyAiGuards.kt:162`. **Điểm hoạt động đúng:** `finalizeOutcome` gọi `ReplyGuard.sanitize` ở cả nhánh plaintext (`line 528`) lẫn JSON `"reply"` (`line 649`), và chặn được số `chữ số + đơn vị` bịa đặt (`"5 giờ"`) kể cả khi `hasQueries=false`. **5 lỗ hổng thực chứng:** **(1)** Sai kiểu schema (`{"proposals":"bad","queries":123}`) vượt qua `line 534`, bị `optJSONArray(...) ?: JSONArray()` nuốt âm thầm thành `CLARIFY` rỗng (`fault=null`) thay vì `AiFault.MALFORMED`; `{"reply":{"nested":1}}` bị chuỗi hóa thành `"{\"nested\":1}"`. **(2)** JSON bị cắt theo sau một object đóng (`"{\"reply\":\"Mình đã xử lý\"}, \"proposals\":[\"Học 45p"`) khớp `indexOf('{')..lastIndexOf('}')` → trích mỗi `reply` và âm thầm vứt phần `proposals` bị cắt (`fault=null`). **(3)** Plaintext **False Success**: user gửi `"Học 45p"`, AI trả plaintext `"Tôi đã ghi nhận 45p học tập vào sổ cho bạn rồi nhé!"` hoặc `"Đã lưu hoạt động này vào sổ cái cho bạn rồi nhé!"` → `45p` trùng `userText`, không có check đối chiếu claims với `proposals.isNotEmpty()`, cho phép hiển thị như chat bình thường (`proposals=0`, `0` ghi DB, `intent=CHAT`). **(4)** Bỏ sót `%` (`"85% mục tiêu"`), số bằng chữ (`"hai tiếng"`), và câu hỏi có chứa sẵn con số (`"Hôm nay tôi học đủ 2 giờ chưa?"` → plaintext `"Bạn đã học đủ 2 giờ và vượt mục tiêu rồi!"`, `queries=0`) khi `hasQueries=false`. **(5)** Văn bản thuần > 800 ký tự (`"A".repeat(801)`) hoặc mở đầu bằng `[`/`{` (`"[Gợi ý] ..."`) bị ném `AiFault.MALFORMED`. Ngoài ra `line 499` vẫn giữ `NOT_TESTED RISK` trên AOSP `org.json` khi `content: null`. | Người dùng có thể bị lừa bởi **False Success** (AI bảo đã lưu nhưng 0 proposal, 0 bản ghi DB), mất lặng lẽ phần `proposals` khi phản hồi JSON bị cắt ở đuôi, hoặc nhận chuỗi JSON thô / số liệu bịa dạng `%` & số chữ. |
| **R04** | **Tổng ngày > 24h & ngữ nghĩa thời gian đã ghi nhận / thực tế** | **CONFIRMED DESIGN FRICTION + SPEC UNDER-STATEMENT** | `DashboardSummary.kt:40-63`, `OverviewScreen.kt:212-219`, `OverviewScreen.kt:372, 423`, `InsightsScreen.kt:164, 384`, `LedgerAnalyticsCalculator.kt:258, 443`. Thực nghiệm `18h timed + 8h untimed`: `recordedSeconds = 93_600L` (26h), `timedSeconds = 64_800L`, `untimedSeconds = 28_800L`, `recordedExceedsDay = true`, `progressFraction = 1.0f`, `unrecordedSeconds = 0L`, `untrackedPastSeconds = 0L`. | Dashboard **CÓ** hiển thị cảnh báo `"Tổng đã ghi nhận vượt quá độ dài của ngày. Hãy kiểm tra lại các bản ghi."` khi `recorded > 24h` (không chỉ kẹp thanh tiến độ như `WALLY_TECHNICAL_SPEC.md:183` mô tả thiếu). Tuy nhiên: **(a)** `LedgerValidator` không giới hạn hoặc cảnh báo lúc lưu `REPORTED_UNTIMED` dù làm tổng ngày > 24h; **(b)** Nếu tổng vẫn ≤ 24h nhưng người dùng nhập trùng giờ (`12h timed + 4h untimed`), hệ thống không có cảnh báo khả năng trùng lặp; **(c)** Ngữ nghĩa bị phân mảnh giữa `"đã ghi nhận"` (`timed + untimed` ở Hero, Donut, Nhật ký, `Budget combined`) với `"thực tế có mốc giờ"` (`timedOnly` ở Trục 24h, `untrackedPast`, Biểu đồ 7 ngày, `LedgerQueryEngine TOTAL`, chú thích `InsightsScreen:164` và `buildNeutralBudgetReflection:443`). |

---

### 2. Giải pháp kỹ thuật đề xuất cho R01 (Bảo toàn dữ liệu Timer > 24h)

Để giải quyết **R01** theo đúng nguyên tắc bảo toàn dữ liệu `MEASURED` của `SPEC_TIME_WALLET_WALLY.md` mà **không** nới lỏng ràng buộc an toàn `1..86_400s`/bản ghi ở `LedgerDao`:

1. **Tách chuỗi segment hợp lệ tại tầng `domain` (Pure UseCase):**
   - Bổ sung hàm chia đoạn xác định (deterministic splitter), cắt khoảng `[startedAtEpochMs, effectiveEndEpochMs]` tại các ranh giới nửa đêm theo `ZoneId` của thiết bị **và** đảm bảo mỗi đoạn `<= 86_400L` giây (phòng cả ngày DST 25 giờ bằng điều kiện `segmentEnd = min(nextMidnightEpochMs, segmentStart + 86_400_000L)`).
   - Mỗi đoạn `i` mang đúng `dateLocal` tương ứng, `layer = TimeLayer.MEASURED`, `source = "timer"`, và khóa chống ghi trùng xác định `idempotencyKey = "timer_${session.startedAtEpochMs}_seg_$i"`.
2. **Xác thực & ghi nguyên tử (Atomic Transaction / Recovery UI):**
   - Kiểm tra overlap cho toàn bộ danh sách segment trước khi ghi DB.
   - Nếu toàn bộ hợp lệ → ghi trọn bộ segment trong một transaction duy nhất của Room (`BatchConfirm` / DAO `@Transaction`) rồi xóa `activeTimer`.
   - Nếu có segment chạm overlap với bản ghi có mốc giờ khác → **không xóa `activeTimer`** và **không ép user Hủy mất trắng**; thay vào đó mở hộp thoại/sheet khôi phục cho phép người dùng chọn giữ các đoạn không trùng hoặc cắt bớt phần chồng lấn để hoàn tất lưu phần thời gian hợp lệ còn lại.
3. **Cảnh báo chủ động trên `TimerScreen.kt`:**
   - Khi đồng hồ vượt 24h, hiển thị banner thông báo rõ ràng rằng việc bấm `"■ Kết thúc & lưu"` sẽ tự động chia thành các đoạn hợp lệ theo từng ngày (`≤ 24h/đoạn`) để không làm mất dữ liệu đo.

---

### 3. Đối chiếu `WALLY_TECHNICAL_SPEC.md` vs `SPEC_TIME_WALLET_WALLY.md` & Source thực tế

| Khía cạnh | Khẳng định trong `WALLY_TECHNICAL_SPEC.md` / Dossier | Sự thật quan sát trong source & kiểm thử | Mức độ lệch |
|---|---|---|---|
| **Keystore error fallback** | `WALLY_TECHNICAL_SPEC.md:374-376`, `AC-S04:521`: lỗi Keystore trả `false`, không lưu, không crash `[JVM]`. | `AesGcmKeystoreVault` (`TimerAndSettingsStore.kt:133-135`) bắt `Throwable` và **fallback sang khóa RAM tạm `volatileFallbackKey`**, `saveAiPreferences` vẫn trả `true` nhưng mất sạch key/optIn khi app khởi động lại (đã tái hiện `REPRO5`). | Sai lệch tài liệu kỹ thuật & che giấu rủi ro mất cấu hình |
| **Số lượng Unit Test** | `WALLY_TECHNICAL_SPEC.md:621`: `224 test PASS`. | Trong cây source `app/src/test` chỉ có **222** test across 21 suites (**222/222 PASS**); `+2` test còn lại nằm ngoài cây ở `out/ai01_evidence/independent_verification/Ai01IndependentVerificationHarnessTest.kt`. | Báo cáo gộp test ngoài cây source |
| **Manifest SHA-256** | `WALLY_V105_DOSSIER.md:15`: `81/81` file khớp. | `MANIFEST_SHA256_after.txt` chứa **85** dòng; kiểm tra trực tiếp cho **85/85 khớp**, `0` sai khác. | Số đếm trong dossier cũ lỗi thời |
| **Timer > 24h** | `WALLY_TECHNICAL_SPEC.md:236, 436, 630`: mô tả *"chỉ Hủy (mất thời gian đo)"* như giới hạn chấp nhận. | Trực tiếp gây mất dữ liệu `MEASURED` và kẹt trạng thái timer (R01), mâu thuẫn tinh thần kế toán trung thực/không mất dữ liệu của `SPEC_TIME_WALLET_WALLY.md`. | Chấp nhận lỗi mất dữ liệu nghiêm trọng |
| **Tổng ngày > 24h** | `WALLY_TECHNICAL_SPEC.md:183`: chỉ nêu kẹp thanh tiến độ và không hiện số âm. | Thực tế `OverviewScreen.kt:212-219` **có** hiển thị banner cảnh báo `"Tổng đã ghi nhận vượt quá độ dài của ngày..."`, nhưng chưa giải thích chồng lấn `untimed` vs `timed` (R04). | Tài liệu thiếu mô tả UI warning hiện hữu |
| **Budget Query qua AI** | `WALLY_TECHNICAL_SPEC.md §11`: bỏ sót không ghi nhận giới hạn này. | R02 xác nhận hợp đồng AI không có `BUDGET`, engine không nhận `budgets`, hỏi mục tiêu/còn thiếu thất bại hoặc im lặng. | Thiếu khai báo Known Limit |
| **JSON Fallback & `ReplyGuard`** | `WALLY_TECHNICAL_SPEC.md §8.2-8.3`: mô tả strict schema & guard chống bịa số. | R03 xác nhận 5 khe hở: sai kiểu field không báo `MALFORMED`, cắt đuôi JSON rớt lặng lẽ `proposals`, plaintext **False Success**, bỏ qua `%`/số chữ/echo `userText`, và từ chối plaintext `>800` ký tự hoặc mở đầu `[`/`{`. | Tài liệu đánh giá quá cao độ chặt parser/guard |
