# INDEPENDENT REVIEW — TIME WALLET WALLY v1.0.6

- **Reviewer:** Independent Technical Reviewer (độc lập với AI-Master)
- **Ngày kiểm toán:** 2026-11-14
- **Candidate:** `out/wally_candidate_v106/TimeWallet-MVP-v1.0.6-WALLY-HOTFIX-candidate-debug.apk`
- **Tài liệu đối chiếu:** `SPEC_TIME_WALLET_WALLY.md`, `REVIEW_WALLY_V105.md`, `WALLY_V106_DOSSIER.md`, `WALLY_TECHNICAL_SPEC.md`, `WALLY_LIVE_EVAL_PLAN.md` (v1.1), `WALLY_LIVE_EVAL_PLAN_v1.2.md`, `WALLY_OWNER_CHECKLIST_V106.md`, source code, tests và `out/wally_evidence_v106/`.
- **Quyết định Owner áp dụng:** Wally API-first (không chatbot local), giữ custom endpoint, không chạy emulator/ADB trong phạm vi Review trước thiết bị.

---

## 1. Executive Verdict

**Verdict: `GO_FOR_DEVICE_TEST`**

Candidate v1.0.6 (`versionCode 7`, `versionName 1.0.6`) đã khắc phục có kiểm chứng các lỗi P1 về dữ liệu và logic cốt lõi của v1.0.5: bảo toàn truy vấn ở câu MIXED kèm phủ định (`F-V105-01`), thống nhất công thức và thuật ngữ ngân sách (`F-V105-02`), nối clause + `"không nghỉ"` + phủ định khác danh mục (`F-V105-03` trừ giới hạn số viết bằng chữ đã công bố), khử race Hủy/gửi đè (`F-V105-04`), loại bỏ `volatileFallbackKey` (`F-V105-05`), tách đoạn nguyên tử cho Timer > 24h và giữ phiên khi trùng giờ (`R01`), hỗ trợ `QueryMetric.BUDGET` (`R02`), cảnh báo > 24h (`R04`), hủy theo tiêu đề (`K1`), và chuẩn hóa bằng chứng log không rỗng (`F-V105-10`). Toàn bộ **322/322** unit test trong repo và **324/324** khi gắn harness ngoài (gồm Golden `66/66`, Holdout `46/46`) đều PASS trong lần chạy lại độc lập của Reviewer.

**Điều kiện và phạm vi cần lưu ý trước khi lên bản phát hành cuối:**
1. Chạy `WALLY_OWNER_CHECKLIST_V106.md` trên máy thật để đóng các mục **`NOT_TESTED` / `UNRESOLVED`** (`F6`, TalkBack, scroll dialog trên màn hình nhỏ/font lớn, `AndroidKeyStore` sau kill/restart, hiệu năng UI).
2. Chạy `WALLY_LIVE_EVAL_PLAN_v1.2.md` để đo thực tế với AI thật (đặc biệt **Group G7** cho phủ định số bằng chữ và **False Success gián tiếp**).
3. **Finding mới `V106-N1` (P2):** `ReplyGuard` (tầng chặn regex phụ sau LLM) vẫn để lọt **6/14** cách diễn đạt False Success gián tiếp bằng tiếng Việt khi có 1 pending proposal (`2/14` khi `0` proposal). Không có ghi DB tự động xảy ra (`S1` giữ nguyên qua xác nhận thẻ Draft), nhưng nên siết thêm mẫu regex trong bản cập nhật cuối trước khi chốt phát hành.

---

## 2. Artifact Provenance & Incident Check

### 2.1 Xác minh Binary, Signing & Manifest

| Artifact | Kích thước (bytes) | SHA-256 / Thuộc tính xác minh | Trạng thái |
|---|---:|---|---|
| **Final Candidate v1.0.6** (`out/wally_candidate_v106/TimeWallet-MVP-v1.0.6-WALLY-HOTFIX-candidate-debug.apk`) | `12,165,363` | `0d4e722bd385e88309c3658c85e7be3dd2a233017045f247c144bcf7968bc09f` | **VERIFIED** |
| **Package / Badging (`aapt2`)** | — | `package='com.timewallet.mvp' versionCode='7' versionName='1.0.6' compileSdkVersion='35' minSdkVersion='26' targetSdkVersion='35'` | **VERIFIED** |
| **Signing Cert (`apksigner`)** | — | `C=US, O=Android, CN=Android Debug`, cert SHA-256 `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (khớp chứng chỉ v1.0.5) | **VERIFIED** |
| **Superseded Round 1** (`out/wally_candidate_v106/superseded_round1/...`) | `11,769,054` | `87e362f7589f96c00db1415a821f74f5e35de63c221a139cd3affba61e96bbb7` (khác hoàn toàn candidate cuối) | **VERIFIED** |
| **Preserved v1.0.5 Origin** (`out/wally_candidate_origin/TimeWallet-MVP-v1.0.5-WALLY-ORIGIN-candidate-debug.apk`) | `11,736,286` | `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` (khớp `V105_APK_SHA256.txt` và `ALL_APK_SHA256.txt`) | **VERIFIED** |
| **Source Manifest v1.0.6** (`MANIFEST_SHA256_after.txt`) | — | **101** entries, `MISMATCHES=0` (khớp so với snapshot tiền v1.0.6 `MANIFEST_SHA256.txt` **85** entries) | **VERIFIED** |
| **Source Snapshots (`.tar`)** | `2,344,960` / `2,519,040` | `source_v1.0.5_pre_V106.tar` (`85` files nguồn, SHA-256 `15b6b3293dd470bf6485358e7c2d9e561058861e507b9f19d328a133c90dc780`) và `source_v1.0.6.tar` (SHA-256 `0878eaa3d0e6c451abd4d9a292106dd26403fe159dbf3727ca25a0a892caa8fe`) | **VERIFIED** |
| **Eval Plan v1.1** (`WALLY_LIVE_EVAL_PLAN.md`) | `25,906` | `12e9acb94d06d0335dc5fa9f3467e396ab7c60369d7c0b427bff9c30e0984681` (được giữ nguyên) | **VERIFIED** |
| **Eval Plan v1.2** (`WALLY_LIVE_EVAL_PLAN_v1.2.md`) | `37,553` | `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0` | **VERIFIED** |

### 2.2 Kiểm tra sự cố `rm -f` ở thư mục gốc (Incident Check)

Kiểm tra trực tiếp hệ thống tệp hiện tại tại `D:\desktop\Time_Wallet`:
- Ở thư mục gốc chỉ tồn tại 2 tệp APK: `TimeWallet-MVP-debug.apk` và `TimeWallet-MVP-v1.0.1-debug.apk` (cùng kích thước `11,877,812` bytes, SHA-256 `730DC2699DC5DF110BD2ADFD7C7EDEE29B858AE26855646AC7A8859AFDE2752D`).
- Không còn tệp `*v1.0.5*` nào ở thư mục gốc (khớp báo cáo Master đã chạy `rm -f` nhầm tên tệp v1.0.5 ở root; Reviewer không phục hồi hay suy đoán lịch sử xóa ngoài filesystem hiện tại).
- Toàn bộ artifact gốc v1.0.5 trong `out/wally_candidate_origin/`, `out/wally_v106_snapshot_pre/` và `out/wally_evidence_origin/` còn nguyên vẹn và khớp đúng SHA-256 v1.0.5 gốc (`e80f7522...`).

---

## 3. Closure Matrix (Các findings v1.0.5: `F-V105-01..10`, `R01–R04`, `K1–K3`)

| ID | Nội dung gốc (v1.0.5) | Production Wiring & Bằng chứng kiểm tra | Trạng thái | Ghi chú & Giới hạn còn lại |
|---|---|---|---|---|
| **`F-V105-01`** | `policyBlocked` xóa sạch `queries` hợp lệ ở MIXED + phủ định và để sót `reply` | `GeminiProposalClient.kt:644-661` (`finalizeOutcome`): giữ `queries`, tính `surviving = !policyBlocked \|\| queries.isNotEmpty()`, chỉ xóa `finalReply` khi `policyBlocked`. Probe `ADV_REPRO2`: `proposals=0, queries=1, intent=QUERY, reply=null`. | **`FIX_VERIFIED`** | — |
| **`F-V105-02`** | Mâu thuẫn số liệu/từ ngữ Ngân sách giữa Card UI, chú thích Insights và phản tư | `DerivedStats.kt`, `ComponentsAndDialogs.kt`, `InsightsScreen.kt:172`, `LedgerQueryEngine.kt`. Probe `ADV_BUDGET_MET` và `V106BudgetConsistencyTest` (4 tests) khớp cùng tổng `đã ghi nhận = có mốc giờ + chưa gắn giờ` (trừ `PLANNED`). | **`FIX_VERIFIED`** | — |
| **`F-V105-03`** | `AiProposalGuard` bỏ lọt đề xuất phủ định khi AI đổi danh mục; case nhiều mệnh đề / `"không nghỉ"` | `WallyAiGuards.kt` (`AiProposalGuard`): ghép theo thời lượng + giao từ khóa tiêu đề, danh mục chỉ làm tie-breaker. Direct RED (`red_A1_A2` `13` fail) -> `0` fail. Probe: `ADV_REPRO3`, `ADV_MULTI_SAME` (`kept=[Code dự án:120, Chạy bộ:60]`), `ADV_NOBREAK_1/2` (`kept=[Làm việc]/[Học]`), `ADV_NEGREST` (`kept=[]`). | **`PARTIAL`** | Probe `ADV_SPELLED_LIMIT` xác nhận câu số bằng chữ (`"Không học hai tiếng hôm nay, làm việc ba giờ"`) lọt guard cục bộ (`kept=[Học, Làm việc]`); đã được công bố tại `WALLY_TECHNICAL_SPEC.md` §11 (`ACCEPTED_LIMITATION` ở guard tĩnh, kiểm ở Live Eval G7). |
| **`F-V105-04`** | Race condition khi `Hủy ✕` -> gửi lại hoặc kết quả AI cũ đến muộn; query tính trên Main thread | `WallyChatRunner.kt`, `TimeWalletViewModel.kt`, `ChatRequestOwner`. Production wiring kiểm trực tiếp; `ADV_REVERSED_ORDER` (exception + completion đảo thứ tự) `PASS`; `V106ChatRunnerTest` (reconstructed RED). | **`FIX_VERIFIED`** | Không có file test RED chạy trên binary v1.0.5 gốc (RED tái dựng trong `V106ChatRunnerTest`). |
| **`F-V105-05`** | `AesGcmKeystoreVault` tự fallback sang `volatileFallbackKey` RAM; reset khóa origin | Đã gỡ `volatileFallbackKey` khỏi source; thay bằng `KeyVaultUnavailableException`. `AiPreferences` giữ khóa cũ chỉ khi cùng exact origin (`keepStoredKeyIfBlank`); HTTP client chặn redirect (`instanceFollowRedirects = false`). `red_A4_keystore` (`2` fail) -> `V106KeystoreTest` PASS. | **`PARTIAL`** | Logic & hợp đồng `InMemoryApiKeyVault` đã `FIX_VERIFIED`; **`AndroidKeyStore` thật và persist sau kill app là `NOT_TESTED`** (chỉ xác minh được trên máy thật). |
| **`F-V105-06`** | `AiSettingsDialog` & `ProductInfoDialog` thiếu `verticalScroll` | `ComponentsAndDialogs.kt` thêm `verticalScroll` + `imePadding`. `V106UiStaticRedProbe` (`2` fail) -> `V106UiContractTest` (`8` fail-safe checks PASS). | **`PARTIAL`** | Code tĩnh **`FIX_VERIFIED`**; giao diện thực tế `320/360/390/412dp` & font `1.3` là **`NOT_TESTED`**. |
| **`F-V105-07`** | Một số nút phụ thiếu vùng chạm `48dp` & vấn đề TalkBack | `ComponentsAndDialogs.kt`: `minimumInteractiveComponentSize()`/`48.dp`; nút có chữ dùng `onClickLabel` + `Role.Button`, nút chỉ có icon giữ `contentDescription` (tránh TalkBack đọc đè chữ). | **`PARTIAL`** | Code tĩnh **`FIX_VERIFIED`**; TalkBack trên Android **`NOT_TESTED`**. |
| **`F-V105-08`** | `optString("content")` thiếu check `null`; JSON cắt / sai kiểu | `StrictJson.kt`, `GeminiProposalClient.kt` (`opt("content") as? String`, `maxOutputTokens=2048`). Probe `ADV_STRICTJSON` chặn toàn bộ wrong-types, `content: null`, nested reply, unclosed fence, trailing ZWSP/prose/object. | **`FIX_VERIFIED`** | Phát hiện quirk nhỏ (`V106-N2a`): BOM `\uFEFF` đặt trước `[1, 2]` hoặc `null` lọt thành plaintext reply (`\uFEFFnull`), không tạo proposal. |
| **`F-V105-09`** | Ticker chạy nền, `collectAsState()`, query tính trên Main thread | `MainActivity.kt` + `TimeWalletViewModel.kt`: `setAppForeground`, `collectAsStateWithLifecycle()`, `DerivedStatsCalculator.compute` + `computeAnswers` chạy trên `Dispatchers.Default`. | **`FIX_VERIFIED`** | Hiệu năng cuộn / frame pacing trên thiết bị thật **`NOT_TESTED`**; danh sách Lịch sử không lazy nằm ngoài phạm vi chặn theo chỉ thị. |
| **`F-V105-10`** | Bằng chứng log v1.0.5 có tệp rỗng và trộn test / thiếu audit baseline | `out/wally_evidence_v106/`: **0** tệp 0-byte; tách rõ baseline (`224`), 9 test sửa trên 8 file (đã kiểm: giữ nguyên bất biến SPEC), final in-tree (`322`), external (`324`). `WALLY_LIVE_EVAL_PLAN.md` (v1.1) giữ nguyên, bổ sung `v1.2`. | **`FIX_VERIFIED`** | — |
| **`R01`** | Timer > 24h chỉ Hủy được; thiếu nguyên tử & phục hồi trùng giờ | `TimerSegments.kt` + `TimerCommit.kt` (`commitBatchRecordsGuarded` nguyên tử 1 transaction) + `AlreadyCommitted` idempotency (`timer_<start>_i`) + `TimeWalletViewModel` giữ `activeTimer` khi overlap + `TimerScreen.kt:264`. `V106TimerLongSessionTest`, `ADV_NY_TZ`, `ADV2_NY_STATS` (`3600+86400+7200=97200s`), `ADV_RECOVERY_*` PASS. | **`FIX_VERIFIED`** | Ghi nhận 2 đặc tính kỹ thuật ở `V106-N2b`/`V106-N2c`: (i) cắt theo khối 24h cố định (`86_400_000ms`), đuôi `<1s` làm tròn lên `1s`; (ii) sửa bản ghi trùng *bên trong* `[startedAt, now]` vẫn bị từ chối khi timer đang mở (cần dời ra ngoài, chuyển chưa gắn giờ, hoặc xóa). |
| **`R02`** | AI contract thiếu metric truy vấn Ngân sách | Thêm `QueryMetric.BUDGET` trong `QueryIntentParser.kt`, `GeminiProposalClient.kt`, `LedgerQueryEngine.kt`. `V106BudgetQueryTest` + `ADV2_BUDGET` xác nhận trả lời đúng tiến độ & phần còn thiếu từ sổ cái, `ReplyGuard` xóa số `"99 giờ"` bịa. | **`FIX_VERIFIED`** | Thực thi với Gemini thật chờ Live Eval. |
| **`R03`** | Lỗ hổng fallback JSON/plaintext & `ReplyGuard` khẳng định đã lưu | JSON cắt/sai kiểu/số liệu khi có query/khẳng định trực tiếp (`Đã ghi...`, `vào sổ`, `tự động xác nhận`) bị chặn (`V106LabelsAndPlaintextTest`, `ADV2_BUDGET`). Tuy nhiên probe `ADV_REPLYGUARD` cho thấy **6/14** mẫu False Success gián tiếp lọt `ReplyGuard` khi có proposal (xem `V106-N1`). | **`PARTIAL`** | AI thật không thể tự ghi DB (`needsConfirmation=true`), nhưng tầng lọc câu trả lời gián tiếp chưa phủ hết ngữ nghĩa tiếng Việt tự nhiên (`V106-N1`). |
| **`R04`** | Tổng ngày > 24h chưa báo khả năng trùng; lệch bộ nhãn | `DashboardSummary.kt:63` (`recordedExceedsDay`), `OverviewScreen.kt:213-220` hiển thị banner cảnh báo trùng giữa phần có mốc giờ & chưa gắn giờ; thống nhất nhãn `đã ghi nhận / có mốc giờ / chưa gắn giờ` khắp UI và Query (`red_K1_R04` -> PASS, `ADV2_R04` `93600 > 86400 => true`). | **`FIX_VERIFIED`** | Chỉ cảnh báo trên Dashboard sau khi lưu (không chặn lúc lưu, khớp nguyên tắc không chặn cứng bản ghi chưa gắn giờ). |
| **`K1`** | Lệnh hủy ưu tiên theo ngữ cảnh/tiêu đề chưa chuẩn | `ProposalLifecycle.kt`: khớp theo `title` trước `rawInput`, 1 khớp thì hủy, nhiều khớp thì hỏi lại, không sửa sổ cái. `V106CancelByTitleTest` (`red_K1_R04`: `2` fail -> `7` PASS). | **`FIX_VERIFIED`** | Với câu diễn đạt tự nhiên ngoài parser cục bộ kiểm tiếp ở Live Eval G5. |
| **`K2`** | Bộ đếm `CONTROL` / nhãn cục bộ luôn `0` trên máy thật | `WALLY_V106_DOSSIER.md:70`: giữ ở Backlog theo chỉ thị (chủ yếu ảnh hưởng thống kê chẩn đoán nội bộ ở chế độ API-first). | **`ACCEPTED_LIMITATION`** | Không ảnh hưởng hành vi nghiệp vụ cốt lõi. |
| **`K3`** | Khóa cũ gắn với cổng riêng bị giữ lại sau khi cài đè | `WALLY_V106_DOSSIER.md:71`: giữ nguyên có chủ đích để bảo toàn migration từ bản cũ không lưu cổng. | **`ACCEPTED_LIMITATION`** | Đã khóa origin cho các cấu hình mới theo `scheme + host + port`. |

---

## 4. New Findings & Technical Observations

### `V106-N1` (`P2`) — `ReplyGuard` còn để lọt 6/14 cách diễn đạt False Success gián tiếp khi có Pending Proposal
- **Vị trí code:** `app/src/main/java/com/timewallet/mvp/domain/usecase/WallyAiGuards.kt:239-246` (`ReplyGuard.claims`).
- **Tái hiện (`ADV_REPLYGUARD`):** Khi gọi trực tiếp `ReplyGuard.sanitize(reply, userText, proposals, hasQueries = false)` trên 14 câu khẳng định gián tiếp đã hoàn tất ghi nhận:
  - Với `proposals = emptyList()` (`prop0`), **2/14** câu lọt qua:
    1. `"Yên tâm, hoạt động học đã tự động hoàn tất rồi nhé!"`
    2. `"Không cần làm gì thêm đâu, mình giữ giúp bạn rồi."`
  - Với 1 pending proposal (`45p học tập`, `needsConfirmation = true`, **chưa hề ghi DB**, `prop1`), **6/14** câu lọt qua:
    3. `"Xong xuôi rồi nhé, mục học 45p của bạn đã vào danh sách."`
    4. `"Mình vừa xử lý xong việc ghi lại 45p học tập cho bạn rồi."` *(lọt trực tiếp do khoảng cách giữa `"vừa"` và `"ghi"` là 4 từ `"xử lý xong việc"`, vượt ngưỡng `{0,3}` của regex)*
    5. `"Tất cả xong rồi bạn nhé, 45p đã có mặt trong Nhật ký."` *(regex dòng 243 chỉ bắt `(?:nằm\s+trong|vào)\s+sổ`, bỏ sót `"Nhật ký"`, `"danh sách"`)*
    6. `"Sổ cái đã nhận 45p học tập của bạn."`
    *(cùng 2 câu ở `prop0`; 8 câu còn lại bị chặn đúng ở cả hai trường hợp).*
- **Đánh giá tác động:** Không gây ghi DB sai (DB chỉ ghi khi người dùng bấm Xác nhận trên Draft Card). Tuy nhiên nếu model thật phát câu trả lời kiểu trên kèm đề xuất chờ duyệt, bong bóng chat sẽ hiển thị nội dung gây hiểu lầm là đã lưu xong. Nên mở rộng từ khóa domain (`nhật\s*ký|danh\s*sách|sổ\s*cái`), cụm `"tự\s+động\s+hoàn\s+tất"`, `"không\s+cần\s+làm\s+gì\s+thêm"` và nới cửa sổ từ sau `"đã|vừa"` lên `{0,6}` trước khi phát hành chính thức.

### `V106-N2` (`P3`) — Các edge-case kỹ thuật nhỏ đã kiểm chứng (không chặn device test)
1. **`V106-N2a` (`StrictJson` BOM prefix trước literal không phải object):** `ADV_STRICTJSON` cho thấy BOM `\uFEFF` đứng trước object (`\uFEFF{"reply":"Chào bạn"}`) được strip đúng và parse thành `"Chào bạn"`, còn ngoài thân message (`outer_bom_body`), sai kiểu, `content: null`, fence chưa đóng, ký tự ZWSP/chữ dư sau JSON đều bị ném `MALFORMED`. Tuy nhiên nếu AI trả nguyên văn `\uFEFF[1, 2]` hoặc `\uFEFFnull`, luồng fallback coi đây là văn bản thường (`reply=\uFEFF[1, 2]` / `\uFEFFnull`, `proposals=0`). Mức rủi ro rất thấp.
2. **`V106-N2b` (`TimerSegments` làm tròn +1s ở phần dư `< 1s` & cắt theo khối 24h cố định):**
   - `ADV2_SUBSECOND`: phiên dài `24h + 500ms` (`86,400,500ms`) tách thành 2 đoạn (`86,400,000ms` và `500ms`). Vì mỗi đoạn dùng `.coerceAtLeast(1L)`, tổng thời lượng lưu là `86,401s` trong khi `floor((end - start) / 1000) = 86,400s` (lệch tối đa `+1s`).
   - `ADV_NY_TZ` & `ADV2_NY_STATS`: `TimerSegments.split` chia theo khối `86_400_000ms` liên tiếp từ `startedAt` chứ không cắt theo nửa đêm lịch. Dù `date` trên bản ghi đoạn 1 giữ ngày bắt đầu, `LedgerAnalyticsCalculator.computeDayStats` cắt chuẩn theo ranh giới ngày UTC/múi giờ `[dayStartMs, dayEndMs)` nên tổng 3 ngày ở múi giờ `America/New_York` (`2026-12-31 23:00` -> `2027-01-02 02:00`) đạt chính xác `3600s + 86400s + 7200s = 97200s`.
3. **`V106-N2c` (`R01` UX khi xử lý Overlap lúc Timer đang chạy):** `ADV_RECOVERY_EDIT_INSIDE` xác nhận khi bấm sửa bản ghi cũ để rút ngắn nhưng vẫn nằm bên trong khoảng `[timerStartedAt, now]`, `saveEditedRecord` báo trùng với `"Bộ đếm đang chạy (...)"`. Người dùng phục hồi thành công (`ADV_RECOVERY_EDIT_OUTSIDE`, `ADV_RECOVERY_EDIT_UNTIMED`) bằng cách dời bản ghi cũ ra ngoài `[startedAt, now]`, xóa mốc giờ (chuyển sang chưa gắn giờ), hoặc xóa bản ghi trùng; `activeTimer` vẫn được bảo toàn đến khi `Stop` thành công hoặc chủ động `Hủy`.

---

## 5. Test Integrity, Evidence Audit & Eval Plan v1.2

### 5.1 Kết quả chạy lại trực tiếp của Reviewer (Offline Gradle)

| Lần chạy | Kết quả | Chi tiết xác minh |
|---|---:|---|
| **Baseline trong repo** (`gradlew.bat testDebugUnitTest --offline --rerun-tasks --console=plain`) | **322 / 322 PASS** | `31 suites`, `0` fail, `0` error, `0` skipped (`GRADLE_EXIT=0`). Có 2 compiler warnings có sẵn: `MainActivity.kt:88:26` (`LocalLifecycleOwner` deprecated), `QueryIntentParser.kt:185:27` (`Condition is always 'true'`). |
| **Gắn harness ngoài tạm** (`Ai01IndependentVerificationHarnessTest`, sau đó dọn sạch) | **324 / 324 PASS** | `32 suites`, `0` fail (`HARNESS_EXIT=0`, `FULL_324_EXIT=0`). Golden `66/66` (`100.0%`, `accepted=39, wrong=0`); Holdout `46/46` (`100.0%` intent, `Query: 32 \| Clarify: 9 \| Write: 5`, `32/32` figure accuracy). **Lưu ý:** Golden 66 & Holdout 46 chỉ kiểm parser/query cục bộ, **không** kiểm đường gọi Gemini API-first. |
| **Probe đối kháng của Reviewer** (`ReviewerV106AdversarialTest` & `ReviewerV106AdversarialPart2Test`, dọn sạch sau khi chạy) | **Đã chạy & xác minh** | Xác nhận các case `ADV_*` và `ADV2_*` (xem mục 3 & 4). Ở Part 1 chỉ có test `06_timer_new_year` tự fail ở assertion viết nhầm của Reviewer (kỳ vọng cắt theo nửa đêm thay vì khối 24h), sau đó được xác nhận lại chuẩn xác ở Part 2 (`ADV2_NY_STATS`: `3600 + 86400 + 7200 = 97200s`, `ADV2_EXIT=0`). |

### 5.2 Kiểm tra Evidence RED→GREEN & 9 Baseline Tests thay đổi

- **Tính nguyên vẹn log (`out/wally_evidence_v106/`):** **0** tệp rỗng (0 bytes). Khớp XML: `baseline` (`22` XMLs, `224` tests, `0` fail) -> `final_in_tree` (`31` XMLs, `322` tests) -> `final_with_external` (`32` XMLs, `324` tests). Thư mục `superseded_round1` (`61` XMLs, `620` tests tổng cộng từ vòng 1) được tách riêng, không trộn vào con số cuối.
- **Phân biệt RED trực tiếp vs RED tái dựng:**
  - **RED trực tiếp trên nền mã trước sửa:** `red_A1_A2` (`20` tests, `13` fail -> `0`), `red_A4_keystore` (`6` tests, `2` fail -> `0`), `red_B1_budget` (`6` tests, `4` fail -> `0`), `red_C_ui_static` (`V106UiStaticRedProbe`: `2` fail -> `V106UiContractTest`: `8` PASS), `red_K1_R04` (`CancelByTitle`: `2` fail -> `7` PASS; `LabelsAndPlaintext`: `4` fail -> `6` PASS).
  - **RED tái dựng (`green_only`, 5 suites / 55 tests):** `V106ChatRunnerTest` (cho `F-V105-04`) và `V106TimerLongSessionTest` (phần ghi một bản ghi đơn cho `R01`) là kiểm thử mới tái dựng hành vi lỗi v1.0.5 sau khi đã viết logic sửa, **không** phải kết quả chạy trực tiếp trên bản gốc v1.0.5; việc tách riêng thư mục `green_only` trong evidence là trung thực.
- **Audit các test baseline đổi trên 8 file (`WALLY_V106_DOSSIER.md` §3.1):** Đều là cập nhật có chủ đích theo kiến trúc v1.0.6, không xóa test hay nới lỏng bất biến SPEC:
  - Thay lời đáp False Success `"Đã ghi 45 phút học."` bằng câu chờ xác nhận (`WallyApiFirstGuardsTest › reply guard...`) và cập nhật kiểm tĩnh nhánh lỗi từ `WallyChatReducer.build` sang `applyResultOrNull` (`WallyApiFirstGuardsTest › no input is lost...`).
  - Cập nhật kiểm tra tĩnh kiến trúc sang `WallyChatRunner` (`Ai01ClosureLifecycleTest`, `Ai01ConversationalAnalyticsTest`) và hash prompt `wally-prompt-4` (`WallyEightLayerTest`).
  - Thay `AesGcmKeystoreVault()` mặc định (từng dựa vào `volatileFallbackKey` trên JVM) bằng `InMemoryApiKeyVault` qua DI ở 4 tệp test (`TimeWalletDomainTest`, `WallyDiagnosticsTest`, `WallyOriginBindingTest`, `WallySecurityStaticTest`), giữ nguyên hợp đồng kiểm tra bản mã/origin/migration.
  - Ngoài ra ở vòng rà soát nội bộ v1.0.6, test mới `V106UiContractTest` được chỉnh từ `contentDescription` sang `onClickLabel` + `Role.Button` cho nút có chữ (chỉ giữ `contentDescription` cho nút chỉ có icon).

### 5.3 Kiểm toán `WALLY_LIVE_EVAL_PLAN_v1.2.md` (so với v1.1)

- `WALLY_LIVE_EVAL_PLAN.md` (v1.1) giữ nguyên hash gốc; `WALLY_LIVE_EVAL_PLAN_v1.2.md` được tạo tách biệt.
- Đáp ứng các yêu cầu rà soát: giữ chuẩn đậu `>= 38/40`, `100%` an toàn (0 sai phạm ghi/khẳng định sai), `SC x3`; bảng đối chiếu chân lý độc lập ở §3.0 tách khỏi suy đoán mô hình; bắt buộc **7/7 Group G** (G1–G7, gồm kiểm tra rule prompt/model G4, mapping F5 = G3, thứ tự test B3 -> B6, kiểm tra hủy F3 yêu cầu `fault=CANCELLED` và đợi tối thiểu `35s`); công bố trung thực giới hạn phủ định dùng số bằng chữ ở §3.2 / Group G7 thay vì giấu dưới câu số thông thường.

---

## 6. Residual Risks, Handoff Checklist & Kết luận cho Owner

### Các hạng mục `NOT_TESTED` / `UNRESOLVED` (Chờ kiểm trên thiết bị & Live Eval)
1. **Android UI & Accessibility thật (`F-V105-06`, `F-V105-07`, `F-V105-09`):** Bố cục 320–412dp, font 1.3, cuộn ngang/dọc + bàn phím IME trong `AiSettingsDialog`/`ProductInfoDialog`, TalkBack trên nhãn tiếng Việt, và độ mượt cuộn.
2. **Android Keystore thật (`F-V105-05`):** Lưu khóa mã hóa thật, hành vi khi tắt mở lại app (kill & relaunch), và thông báo lỗi khi Keystore thiết bị gặp trục trặc.
3. **Live AI End-to-End (`WALLY_LIVE_EVAL_PLAN_v1.2.md`):** Chưa gọi Gemini/OpenAI-compatible endpoint thật qua mạng (tỷ lệ thành công thực tế của prompt `wally-prompt-4`, G5 hủy theo tiêu đề, G7 phủ định số bằng chữ, R02 truy vấn ngân sách, prompt caching của provider nếu có).
4. **Lỗi `F6` từ v1.0.5 (`UNRESOLVED`):** Vẫn chưa tái hiện/chốt nguyên nhân trong môi trường không có thiết bị/ADB; cần Owner kiểm tra trực tiếp theo bước trong `WALLY_OWNER_CHECKLIST_V106.md`.

### Kết luận bàn giao Owner
1. **Cài bản mới để thử nghiệm thiết bị:** Dùng duy nhất file `out/wally_candidate_v106/TimeWallet-MVP-v1.0.6-WALLY-HOTFIX-candidate-debug.apk` (`SHA256: 0d4e722b...bc09f`, không dùng file trong `superseded_round1/`).
2. **Thực hiện đủ 2 bước nghiệm thu trực tiếp:**
   - Hoàn thành `WALLY_OWNER_CHECKLIST_V106.md` trên điện thoại (gồm kiểm tra `F6`, Keystore qua restart, Timer > 24h & overlap recovery, TalkBack/Dialog scroll).
   - Chạy bộ test AI thật `WALLY_LIVE_EVAL_PLAN_v1.2.md` (đạt `>= 38/40`, `0` lỗi an toàn, `7/7` Group G).
3. **Trước khi chốt bản phát hành cuối (Production Sign-off):** Bổ sung siết regex `ReplyGuard` cho `V106-N1` (6 mẫu False Success gián tiếp quanh `"Nhật ký"`, `"danh sách"`, `"Sổ cái đã nhận"`, `"tự động hoàn tất"`, cửa sổ `{0,6}` từ sau `"đã|vừa"`) cùng mọi phát hiện từ phiên đo máy thật / `F6` (nếu tái hiện).
