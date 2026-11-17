# WALLY v1.0.6 — Hồ sơ candidate hotfix, ma trận đóng lỗi và gói tái kiểm của Reviewer

**Trạng thái:** CANDIDATE_UNVERIFIED (hotfix sau `REVIEW_WALLY_V105.md`: `FIX_REQUIRED`) · **Ngày:** 2026-11-14 · **Giai đoạn tiếp theo:** Reviewer tái kiểm → Manager chuyển sang nghiệm thu thiết bị của Owner.
**Không tuyên bố:** DEVICE_PASS, AI_LIVE_PASS, SPEC_ACCEPTED. **F6: UNRESOLVED** (sửa Keystore không đồng nghĩa đã sửa crash F6). Không emulator/ADB, không gọi API thật, không xin khóa qua chat, không thêm backend/Redis/vector DB/MCP/data cache/AI nhiều vòng.

## 1. Artifact

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v106/TimeWallet-MVP-v1.0.6-WALLY-HOTFIX-candidate-debug.apk` |
| SHA256 | `0d4e722bd385e88309c3658c85e7be3dd2a233017045f247c144bcf7968bc09f` (`APK_SHA256.txt`, tính bằng `sha256sum`) |
| Package / version | `com.timewallet.mvp` · **versionCode 7** · **versionName 1.0.6** · targetSdk 35 (`output-metadata.json`) |
| Chữ ký | Debug keystore, chứng chỉ SHA-256 `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d`, **trùng v1.0.5** ⇒ cài đè được (`SIGNING_CERT_COMPARE.txt`, `apksigner verify`) |
| Room | v3, không migration mới |
| Manifest nguồn | `MANIFEST_SHA256_after.txt`: **101 dòng**, kiểm lại `sha256sum -c` = 101 OK / 0 sai (`MANIFEST_VERIFY.txt`). (v1.0.5: **85** dòng, không phải 81 như `WALLY_V105_DOSSIER.md` ghi) |
| Snapshot nguồn v1.0.6 | `source_v1.0.6.tar` + `source_v1.0.6.tar.sha256` |
| Diff / danh sách | `SOURCE_DIFF_vs_v1.0.5.patch`, `CHANGED_FILES_vs_v1.0.5.txt` (30 tệp), `NEW_FILES_vs_v1.0.5.txt` (16 tệp), `REMOVED_FILES_vs_v1.0.5.txt` (0) |
| Log build | `assemble_console.txt` (không rỗng), `assemble_exitcode.txt` (`exit=0`) |
| **Bảo toàn v1.0.5** | APK v1.0.5 `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` **không đổi** (kiểm lại trong `ALL_APK_SHA256.txt` cùng v1.0.4/1.0.3/1.0.2); snapshot trước khi sửa `out/wally_v106_snapshot_pre/` (`source_v1.0.5_pre_V106.tar`, `MANIFEST_SHA256.txt` 85 dòng, `V105_APK_SHA256.txt`); evidence v1.0.5 không bị ghi đè |

## 2. Số liệu kiểm chứng (JVM, `--offline`, không phải thiết bị)

| Chạy | Kết quả | Evidence |
|---|---|---|
| **Baseline trước khi sửa** (22 tệp XML) | **224/224 PASS** = 222 trong cây + 2 ngoài cây (`Ai01IndependentVerificationHarnessTest`, holdout) | `out/wally_evidence_v106/baseline/` (`console.txt` 2.396 byte, `exitcode.txt`, `xml/`) |
| **v1.0.6, trong cây** (31 tệp XML, `--rerun-tasks`) | **322/322 PASS**, 0 fail, 0 error, 0 skip = 222 baseline + **100 mới** | `final_in_tree/` (`console.txt`, `exitcode.txt` = 0, `xml/`, `per_suite_counts.txt`) |
| **v1.0.6, kèm 2 test ngoài cây** (32 tệp XML) | **324/324 PASS** (harness chép vào cây tạm thời rồi **đã xóa**) | `final_with_external/` (+ `golden_holdout_extract.txt`) |
| Golden / holdout | **golden 66/66** (`GOLDEN_INTENT total=66 correct=66`, `GOLDEN_ACCEPTED accepted=39 wrong=0`); **holdout 46/46** (`Intent Accuracy 100.0% (46/46)`, `Figure Accuracy 32/32`) — chỉ chứng minh bộ phân tích cục bộ cũ, **không** thay cho AI thật | `final_with_external/golden_holdout_extract.txt` |

100 test mới: `V106ResponseContractTest` 20 · `V106TimerLongSessionTest` 15 · `V106ReviewRound2Test` 13 · `V106ChatRunnerTest` 10 · `V106BudgetQueryTest` 9 · `V106UiContractTest` 8 · `V106CancelByTitleTest` 7 · `V106BudgetConsistencyTest` 6 · `V106KeystoreTest` 6 · `V106LabelsAndPlaintextTest` 6 (+ `InMemoryApiKeyVault`, kho giả chỉ trong source test).

### 2.1 RED trước / GREEN sau

RED = chạy **cùng tệp test** trên bản sao nguồn v1.0.5 (`out/wally_v106_snapshot_pre/source_v1.0.5_pre_V106.tar` giải nén ra thư mục riêng, không đụng cây làm việc); GREEN = XML của lần chạy cuối trên v1.0.6. Mỗi thư mục có `console_v105.txt`, `exitcode_v105.txt`, `RED_v105_*.xml`, `GREEN_v106_*.xml`.

| Thư mục | Test | Trên v1.0.5 (RED) | Trên v1.0.6 |
|---|---|---|---|
| `red_A1_A2/` | `V106ResponseContractTest` | **13/20 FAIL** (F01 query bị mất; F03 nhãn sai / cùng danh mục / thiếu bằng chứng; R03 null, sai kiểu, envelope, cắt/thừa JSON, False Success, số bằng chữ, %, số lặp từ câu hỏi) — 7 test còn lại là chốt hồi quy (PASS cả hai bản) | 20/20 PASS |
| `red_A4_keystore/` | `V106KeystoreTest` | **2/6 FAIL** (vault production vẫn "mã hóa" bằng khóa RAM; mã nguồn còn `volatileFallbackKey`) — các test "cập nhật hỏng giữ nguyên cấu hình cũ" PASS cả hai bản (chốt hồi quy) | 6/6 PASS |
| `red_B1_budget/` | `V106BudgetConsistencyTest` | **4/6 FAIL** (phản tư Insights và tổng kết/câu hỏi Nhật ký ngày nêu mức chỉ-có-giờ; chú thích Insights mâu thuẫn thẻ; validator từ chối `BUDGET`) | 6/6 PASS |
| `red_K1_R04/` | `V106CancelByTitleTest`, `V106LabelsAndPlaintextTest` | **6/13 FAIL** (K1: hủy theo tiêu đề hỏi lại; R03(5): văn bản "[Gợi ý]…" và văn bản dài bị coi là hỏng; R04: nhãn thống nhất + cảnh báo > 24h) | 13/13 PASS |
| `red_C_ui_static/` | `V106UiStaticRedProbe` (cùng phép kiểm tĩnh của `V106UiContractTest` cho F-V105-06/07) | **2/2 FAIL**: `AiSettingsDialog` thiếu `verticalScroll`; **24** điểm `clickable` thiếu 48dp/nhãn | 0 điểm vi phạm (`V106UiContractTest`) |
| `green_only/` | `V106ChatRunnerTest` (10), `V106TimerLongSessionTest` (15), `V106BudgetQueryTest` (9), `V106UiContractTest` (8), `V106ReviewRound2Test` (13) | **Không chạy được trên v1.0.5** vì kiểm lớp/hàm mới (`WallyChatRunner`, `TimerCommit`, `QueryMetric.BUDGET`, `DerivedStatsCalculator`) | PASS |

**Nhãn trung thực cho RED không chạy được trực tiếp** (ViewModel không dựng được trên JVM):
- **F-V105-04:** `V106ChatRunnerTest › RECONSTRUCTED v105 model…` dựng lại **đúng logic v1.0.5** (guard toàn cục, `finally` luôn tắt loading + nhả guard) và chứng minh trên mô hình đó: Gửi lại sau Hủy bị nuốt; nếu chỉ nhả guard khi Hủy thì lượt cũ tháo dỡ sẽ tắt loading và nhả guard của lượt mới. Đây **không** phải chạy ViewModel v1.0.5. Hành vi đúng của v1.0.6 được kiểm bằng `WallyChatRunner` thật với thứ tự hoàn thành điều khiển được.
- **R01:** `V106TimerLongSessionTest › v105 single-record path…` chạy đúng lời gọi mà `stopAndSaveTimer` v1.0.5 thực hiện (một `validateAndBuildRecord` cho cả phiên) và chứng minh phiên 25h bị từ chối; 14 test còn lại kiểm `TimerCommit` thật trên DAO trong RAM.

## 3. Ma trận đóng lỗi

Kết quả = trạng thái bằng chứng **ở mức JVM/mã nguồn**. "Thiết bị / AI thật" luôn là NOT_TESTED trừ khi ghi khác.

| ID | Việc đã làm (tệp chính) | Test | Kết quả | Rủi ro còn lại |
|---|---|---|---|---|
| **F-V105-01** | `finalizeOutcome` giữ `queries` hợp lệ khi đề xuất bị chặn; lời đáp đi kèm đề xuất bị chặn bị bỏ; intent `QUERY` (hoặc `BLOCKED` nếu không còn gì) — `GeminiProposalClient.kt` | `V106ResponseContractTest` F01 ×2 | RED→GREEN | AI thật chưa chạy (G1 trong kế hoạch v1.2) |
| **F-V105-02** | Một định nghĩa "đã ghi nhận" (`recordedSummaryVi`, `combined*`) cho thẻ, phản tư Insights, tổng kết/câu hỏi Nhật ký ngày, chú thích Insights, Wally — `TimeBudget.kt`, `LedgerAnalyticsCalculator.kt`, `DailyReviewAssistant.kt`, `InsightsScreen.kt`, `ComponentsAndDialogs.kt` | `V106BudgetConsistencyTest` | RED→GREEN | Hiển thị thực tế NOT_TESTED |
| **F-V105-03** | `AiProposalGuard` đối chiếu theo mệnh đề mang thời lượng + từ khóa, danh mục chỉ là tín hiệu phụ; không đủ bằng chứng ⇒ hỏi lại — `WallyAiGuards.kt`, `VietnameseChatParser.durationClauses` | `V106ResponseContractTest` F03 ×5 (gồm "cùng số phút, khẳng định + phủ định") | RED→GREEN | Số bằng **chữ** trong câu phủ định không được nhận ra cục bộ; vẫn phải qua bước xác nhận của người dùng |
| **F-V105-04** | Quyền sở hữu theo vé (`ChatRequestOwner`) + `WallyChatRunner`; lượt cũ không ghi trạng thái/không nhả quyền lượt mới; Hủy→Gửi lại được nhận ngay — `ChatAnalysisSupport.kt`, `WallyChatRunner.kt`, `TimeWalletViewModel.kt` | `V106ChatRunnerTest` (10, async, thứ tự điều khiển được) | GREEN; RED **dựng lại** (xem 2.1) | Vòng đời Android và mạng thật NOT_TESTED |
| **F-V105-05** | Bỏ khóa dự phòng RAM; `AesGcmKeystoreVault` ném `KeyVaultUnavailableException`; lưu hỏng ⇒ báo chưa lưu, giữ nguyên cấu hình cũ; kho giả chỉ qua DI trong source test — `TimerAndSettingsStore.kt` | `V106KeystoreTest` (+ 5 test cũ chuyển sang kho giả DI) | RED→GREEN | **Keystore thật trên thiết bị NOT_TESTED**; không đồng nghĩa đã sửa F6. Khóa đang không đọc được mà người dùng Lưu với ô khóa trống không còn ghi đè bản mã cũ (cùng origin). Di cư khóa plaintext cũ vẫn xóa plaintext khi Keystore lỗi (quyết định F6 cũ, không phải hồi quy) |
| **F-V105-06** | `verticalScroll` cho `AiSettingsDialog`, `ProductInfoDialog`; nút hành động nằm ngoài vùng cuộn — `ComponentsAndDialogs.kt` | `V106UiContractTest` | RED→GREEN (tĩnh) | Chưa có kiểm thử trực quan 320–412dp/chữ 1.3/bàn phím |
| **F-V105-07** | 24 điểm `clickable` được thêm `minimumInteractiveComponentSize`/`heightIn(48dp)` + `Role`/`contentDescription` — `ChatScreen.kt`, `ComponentsAndDialogs.kt`, `DailyReviewScreen.kt`, `HistoryScreen.kt`, `InsightsScreen.kt`, `OverviewScreen.kt`, `MainActivity.kt` | `V106UiContractTest` (quét toàn bộ nguồn) | RED→GREEN (tĩnh) | TalkBack/cảm giác chạm NOT_TESTED. Sau vòng đọc độc lập (mục 3.2): khung có chữ riêng dùng `onClickLabel` thay vì `contentDescription` (tránh thay chữ khi đọc) |
| **F-V105-08** | `StrictJson`: `content` phải là chuỗi, một object trọn vẹn, không phần dư, đúng kiểu; không phụ thuộc cách `org.json` JVM/AOSP xử lý phần dư/`null` — `StrictJson.kt`, `GeminiProposalClient.kt` | `V106ResponseContractTest` R03 | RED→GREEN trên JVM | **Phân tích kỹ thuật** cho Android ART: mã không còn dùng `optString("content")`; `content` được lấy bằng `opt(...) as? String` nên `null` cho `MALFORMED` bất kể bản `org.json`. Chạy trên ART NOT_TESTED |
| **F-V105-09** | Số liệu truy vấn tính ngoài luồng chính kể cả nhánh tính lại (`applyResultOrNull`); dẫn xuất Tổng quan/Insights/Nhật ký ngày/xúc xắc tính trên `Dispatchers.Default`, lượt mới nhất thắng (`DerivedStatsCalculator`); ticker chỉ chạy giữa `onAppResumed…onAppPaused`; `collectAsStateWithLifecycle` — `WallyChatReducer.kt`, `DerivedStats.kt`, `TimeWalletViewModel.kt`, `MainActivity.kt` | `V106ChatRunnerTest` (luồng tính), `V106UiContractTest`, kiểm tương đương `DerivedStatsCalculator` | GREEN | Nhật ký **chưa lazy** (backlog, chưa có bằng chứng cần sửa); hiệu năng thực tế NOT_TESTED |
| **F-V105-10** | Mọi log console/XML/exit code của đợt này đầy đủ, không rỗng, không ghi đè evidence cũ; F6 giữ UNRESOLVED | Mục 2 | Đóng phần hồ sơ | **F6 UNRESOLVED** (cần nhật ký lỗi từ máy thật) |
| **R01** | `TimerSegments` (cắt ≤ 24h theo thời gian thực) + `TimerCommit` (một giao dịch, khóa ổn định `timer_<start>[_i]`, trùng giờ ⇒ giữ phiên); đối soát khi mở lại; banner > 24h — `TimerSegments.kt`, `TimerCommit.kt`, `TimeWalletViewModel.kt`, `TimerScreen.kt` | `V106TimerLongSessionTest` (15: 24h, 24h+1s, 25h, nhiều ngày, nửa đêm, DST 25h/23h, trùng giờ nguyên tử, thử lại, khởi động lại giữa commit và xóa phiên, tương thích khóa v1.0.5) | GREEN; RED **dựng lại** | Đồng hồ nền/khởi động lại trên máy thật NOT_TESTED (bước 8 checklist). Bấm đúp "Kết thúc & lưu" được chặn ở ViewModel và `TimerCommit` báo `AlreadyCommitted` thay vì trùng giờ giả |
| **R02** | `QueryMetric.BUDGET` dùng `computeBudgetProgress` chung; không có ngân sách ⇒ nói rõ; không so sánh kỳ; không vòng AI thứ hai; không gửi sổ cái/ngân sách lên cloud; prompt `wally-prompt-4` — `QueryIntentParser.kt`, `LedgerQueryEngine.kt`, `GeminiProposalClient.kt` | `V106BudgetQueryTest` (9), `V106BudgetConsistencyTest` | GREEN; RED một phần (validator v1.0.5 từ chối `BUDGET`) | Chất lượng AI chọn đúng `BUDGET` NOT_TESTED (G4); câu hỏi ngân sách luôn là ngày/tuần **hiện tại** |
| **R03** | `ReplyGuard` v2: không báo đã ghi/sửa/xóa, không "không cần xác nhận"; số bằng chữ, %, số lặp từ câu hỏi bị bỏ; chỉ thời lượng của đề xuất đang chờ được nhắc; văn bản "[Gợi ý]…" và văn bản dài được xử lý như lời đáp — `WallyAiGuards.kt`, `StrictJson.kt` | `V106ResponseContractTest`, `V106LabelsAndPlaintextTest` | RED→GREEN | Có thể chặn nhầm lời đáp vô hại như lời khuyên chung có số ("thử Pomodoro 25 phút") hay "Top 3 hoạt động" (ưu tiên an toàn); là heuristic, không thể chặn mọi cách diễn đạt; AI thật NOT_TESTED |
| **R04** | Cảnh báo > 24h giữ và nói rõ khả năng trùng; **không chặn cứng** nhập chưa gắn giờ; một bộ nhãn *đã ghi nhận / có mốc giờ / chưa gắn giờ*; không gọi phần chưa ghi là "thời gian rảnh" — `OverviewScreen.kt`, `LedgerQueryEngine.kt`, `InsightsScreen.kt` | `V106LabelsAndPlaintextTest`, `V106BudgetConsistencyTest` | RED→GREEN | Không có cảnh báo riêng lúc **lưu** (chỉ banner sau khi lưu) |
| **K1** | Hủy theo **tiêu đề** trước, rawInput sau; một khớp ⇒ hủy, nhiều khớp ⇒ hỏi lại; sổ cái không đổi — `ProposalLifecycle.kt` | `V106CancelByTitleTest` (7) | RED→GREEN | AI thật (G5) |
| **K2** | Bộ đếm "lệnh cục bộ"/nhãn `CONTROL` luôn 0 trên máy thật | — | **Backlog** theo chỉ thị | Rất thấp |
| **K3** | Khóa cũ gắn với cổng riêng bị giữ lại sau khi cài đè | — | **Không đổi** theo chỉ thị | Thấp, chủ ý |

### 3.2 Vòng đọc độc lập thứ hai (agent đọc mã nguồn, chỉ đọc; **không** chạy test/thiết bị)

Sau khi build lần đầu (APK `87e362f7589f96c00db1415a821f74f5e35de63c221a139cd3affba61e96bbb7`, **đã thay thế**, giữ nguyên ở `out/wally_candidate_v106/superseded_round1/` cùng evidence cũ ở `out/wally_evidence_v106/superseded_round1/`), một agent độc lập đọc toàn bộ phần mã đã đổi. Kết quả và cách xử lý (đã xác minh bằng cách đọc mã, không nhận nguyên văn):

| Phát hiện | Xử lý | Test |
|---|---|---|
| Bấm đúp **Kết thúc & lưu**: lượt hai va vào chính các dòng vừa ghi nên báo "trùng giờ" giả; `stopAndSaveTimer` không có chốt chặn | Chốt `timerStopGuard` ở ViewModel; `TimerCommit` kiểm lại khóa sau khi bị từ chối/trùng và trả `AlreadyCommitted`; `finishTimerAfterCommit` làm mới số liệu dẫn xuất | `V106ReviewRound2Test` (mô phỏng cuộc đua bằng DAO "nói dối một lần") |
| `contentDescription` trên khung có chữ (thông báo, huy hiệu đồng hồ, dòng Nhật ký, nút có chữ) sẽ **thay** chữ hiển thị khi đọc bằng TalkBack (hồi quy trợ năng do chính F-V105-07 sinh ra; kết luận từ kiến thức nền tảng, chưa thử trên máy) | Khung có chữ dùng `onClickLabel` + `Role.Button`; chỉ nút chỉ có biểu tượng (×, ✎/▲) giữ `contentDescription` | `V106UiContractTest` (cập nhật) |
| `ReplyGuard` bị lách: dấu câu/ngoặc trước động từ, diễn đạt khác ("đã giúp bạn lưu", "đã được Wally ghi lại", "ghi lại rồi", "đã nằm trong sổ"), NFD, số bằng chữ thiếu (`lăm`, `mốt`), đơn vị/định dạng thiếu (`2:30`, `1g30`, `mins`, `hrs`), số tiếng Anh, `0 buổi` khi không có đề xuất | Biên từ bằng lookbehind, cho phép ≤ 3 từ chen giữa, chuẩn hóa NFC, bổ sung từ/đơn vị/định dạng, cộng giờ-phút (`2h30` = 150), số lượng chỉ được nhắc khi có đề xuất; giờ bắt đầu/kết thúc của chính đề xuất được phép nhắc lại | `V106ReviewRound2Test` |
| Ngược lại, `ReplyGuard` chặn nhầm: "mình sẽ không tự động xác nhận", "When you're…", lặp khoảng giờ của đề xuất | Sửa (phủ định đứng trước không còn bị coi là lời hứa; bỏ `done`; cho phép lặp giờ của đề xuất). Lời khuyên chung có số ("thử Pomodoro 25 phút", "Top 3 hoạt động") **vẫn bị bỏ có chủ đích** (ưu tiên an toàn) | `V106ReviewRound2Test` |
| Khóa đang `keyUnavailable` rồi Lưu với ô khóa trống ghi đè bản mã còn dùng được bằng chuỗi rỗng | `keepStoredKeyIfBlank` (chỉ khi cùng origin; đổi origin vẫn xóa; không tự bật AI) | `V106ReviewRound2Test` (3 test) |
| `finishTimerAfterCommit` không làm mới số liệu dẫn xuất (đường đối soát lúc khởi động) | Gọi `recomputeStats` | `V106ReviewRound2Test` (tĩnh) |
| Guard phủ định chỉ nhận thời lượng bằng **chữ số**: "Tôi không học **hai** tiếng nhưng làm việc **hai** tiếng" không được nhận ra cục bộ | **Không sửa trong v1.0.6** (ngoài phạm vi F-V105-03 yêu cầu: bỏ phụ thuộc danh mục + ca cùng số phút bằng chữ số). Ghi vào giới hạn đã biết; người dùng vẫn phải xác nhận từng đề xuất | — |
| `content` dạng mảng các phần (một số nhà cung cấp OpenAI-compatible) bị `MALFORMED`; văn bản thường mở đầu bằng `{` hoặc `[1]` vẫn bị `MALFORMED` | Chặt có chủ đích (R03); ghi vào giới hạn | — |
| Nhật ký (HistoryScreen) chưa lazy; vòng đồng hồ vẫn chạy mỗi giây khi màn hình hiện dù không có phiên | Theo chỉ thị: Nhật ký chưa lazy là **backlog** (chưa có bằng chứng); vòng 1 giây khi không có phiên chỉ gọi một hàm thoát ngay | — |
| Lõi `WallyChatRunner`/`ChatRequestOwner`, cắt đoạn Timer, `BUDGET`, hủy theo tiêu đề, `recomputeStats` latest-wins, Keystore không còn dự phòng | Agent không tìm thấy lỗi (ghi là đọc mã, chưa chạy) | — |

### 3.1 Test baseline được cập nhật CÓ CHỦ ĐÍCH (222 test vẫn đủ, 9 test đổi nội dung)

| Tệp › test | Lý do |
|---|---|
| `WallyApiFirstGuardsTest › reply guard keeps figures backed…` | Kỳ vọng cũ chấp nhận lời đáp "Đã ghi 45 phút học." — chính là False Success (F-V105-08/R03). Nay lời đáp nhắc lại thời lượng của đề xuất ("Mình thấy 45 phút học, bạn xác nhận nhé.") được giữ, còn "Đã ghi…" bị bỏ |
| `WallyApiFirstGuardsTest › no input is lost on failure…` | Nhánh lỗi nằm trong `WallyChatReducer.build` (đã tách `applyResultOrNull`); đổi chỗ cắt chuỗi kiểm tĩnh, bất biến giữ nguyên |
| `Ai01ClosureLifecycleTest › query and clarify routing never touch the pending drafts` | Logic áp kết quả chuyển từ ViewModel sang `WallyChatRunner`; kiểm tĩnh đọc tệp mới, bất biến "không xóa draft khi có câu trả lời" giữ nguyên |
| `Ai01ConversationalAnalyticsTest › query code has zero network or database dependencies` | Engine được ViewModel truyền vào runner; kiểm "engine chỉ chạy sau khi AI trả kết quả" chuyển sang `WallyChatRunner` |
| `WallyEightLayerTest › prompt cache layout…` | `PROMPT_VERSION` = `wally-prompt-4` (thêm `BUDGET`, quy tắc lời đáp); băm tiền tố vẫn kiểm tự nhất quán |
| `TimeWalletDomainTest › patch_v1_0_1_F1_and_F2…`, `› patch_v1_0_1_F3…`; `WallyDiagnosticsTest › healthy vault round trip…`; `WallyOriginBindingTest › the origin binding is symmetrical…`; `WallySecurityStaticTest › stored value is ciphertext…` | Dùng `AesGcmKeystoreVault()` mặc định (dựa vào khóa RAM dự phòng trên JVM) ⇒ chuyển sang `InMemoryApiKeyVault` qua DI (F-V105-05). Hợp đồng kiểm tra (bản mã, di cư, gắn origin) không đổi |

Không có test nào bị xóa hoặc làm lỏng mà không có lý do ở bảng trên (`CHANGED_FILES_vs_v1.0.5.txt` liệt kê đủ 8 tệp test đổi).

## 4. Tài liệu đi kèm

| Tài liệu | Nội dung |
|---|---|
| `WALLY_LIVE_EVAL_PLAN_v1.2.md` (+ `.sha256` = `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0`) | Kế hoạch AI thật **v1.2**, tạo trước mọi lượt chạy; 40 câu gốc + ngưỡng (≥ 38/40, an toàn 100%, SC ×3, p50/p95) không đổi; thêm nhóm G (7 case hồi quy, bắt buộc 100%), sửa ground truth độc lập sổ cái cũ, thứ tự B3→B6, F3 phải thấy `fault=CANCELLED` + chờ ≥ 35 giây. **`WALLY_LIVE_EVAL_PLAN.md` (v1.1) và `.sha256` giữ nguyên** (hash khớp `12e9acb9…4681`) |
| `WALLY_OWNER_CHECKLIST_V106.md` | Checklist thiết bị 10 bước cho candidate này (nâng cấp, CRUD thủ công, nhất quán ngân sách, hộp thoại/bàn phím, vùng chạm, Keystore qua khởi động lại, Hủy/Gửi lại, Timer > 24h, `BUDGET`/Hủy theo tên, F5, F6) |
| `WALLY_TECHNICAL_SPEC.md` | Đính chính các điểm sai/thiếu của bản v1.0.5 (Timer > 24h, Keystore, ngân sách, hợp đồng AI, cảnh báo > 24h, số test/manifest) |

## 5. Gói tái kiểm cho Independent Reviewer

1. **Chạy lại toàn bộ:** `JAVA_HOME=<JDK 22> ./gradlew testDebugUnitTest --offline --rerun-tasks` (kỳ vọng 322 test, 0 fail). Chép `out/ai01_evidence/independent_verification/Ai01IndependentVerificationHarnessTest.kt` vào `app/src/test/java/com/timewallet/mvp/` rồi chạy lại ⇒ 324 (holdout 46/46). **Nhớ xóa tệp chép tạm.**
2. **Tái hiện RED trên v1.0.5:** giải nén `out/wally_v106_snapshot_pre/source_v1.0.5_pre_V106.tar` ra thư mục mới, chép vào đó các tệp test sau từ `app/src/test/java/com/timewallet/mvp/` rồi chạy `--tests` tương ứng: `V106ResponseContractTest.kt`, `V106KeystoreTest.kt` + `InMemoryApiKeyVault.kt`, `V106BudgetConsistencyTest.kt`, `V106CancelByTitleTest.kt`, `V106LabelsAndPlaintextTest.kt`, và `out/wally_evidence_v106/red_C_ui_static/V106UiStaticRedProbe.kt.txt` (đổi đuôi `.kt`). Kỳ vọng số FAIL như bảng 2.1.
3. **Ca tái hiện của Review ↔ test:**

| Ca của Reviewer | Test tương ứng |
|---|---|
| REPRO2 (F-V105-01: câu hỏi hợp lệ + đề xuất bị chặn) | `F01 a valid query survives…` |
| REPRO3 (F-V105-03: phủ định lệch danh mục lọt) | `F03 a relabeled negated proposal…`, `F03 an affirmative proposal sharing…`, `F03 same minutes…` |
| REPRO4 (F-V105-02: card vs phản tư) | `V106BudgetConsistencyTest` (4 test) |
| REPRO5 (F-V105-05: lưu "thành công" rồi mất khóa) | `V106KeystoreTest › production vault without AndroidKeyStore refuses…`, `…a failed key update…` |
| R01 (Timer > 24h) | `V106TimerLongSessionTest` (25h, 86.401s, nhiều ngày, DST, trùng giờ, khởi động lại) |
| R02 (không có truy vấn ngân sách) | `V106BudgetQueryTest`, `R02 the validator accepts a BUDGET query…` |
| R03 (1)–(5): sai kiểu, JSON cắt, False Success, `%`/số chữ/số lặp, văn bản `[`/dài | `R03 wrong typed fields…`, `R03 truncated or trailing-garbage…`, `R03 an AI reply can never claim…`, `R03 spelled-out number words…`, `R03 percentages…`, `R03 numbers echoed…`, `plaintext that merely starts with a bracket tag…` |
| R04 | `V106LabelsAndPlaintextTest` |
| F-V105-04 | `V106ChatRunnerTest` (Hủy→Gửi lại, kết quả muộn, bấm đúp, huỷ bằng lệnh, tính ngoài luồng chính) |

4. **Điều Reviewer nên tự thử thêm** (ngoài test đã có): đảo thứ tự hoàn thành trong `V106ChatRunnerTest` bằng ví dụ riêng; thêm câu phủ định với nhiều cặp cùng số phút; thêm phiên Timer qua giao thừa múi giờ khác; phản hồi AI có phần dư Unicode/BOM.

## 6. Chưa kiểm chứng (NOT_TESTED) và giới hạn

- **Thiết bị:** nâng cấp cài đè (A14), bố cục 320–412dp/chữ 1.3/bàn phím (A13/A17), Keystore thật, đồng hồ nền > 24h, TalkBack, hiệu năng. → `WALLY_OWNER_CHECKLIST_V106.md`.
- **AI thật:** mọi chất lượng AI, hoàn toàn mock. → `WALLY_LIVE_EVAL_PLAN_v1.2.md`, cần Owner tự nhập khóa.
- **F6 UNRESOLVED.**
- **Khác:** Nhật ký chưa lazy (backlog); guard phủ định cục bộ chỉ nhận chữ số; truy vấn ngân sách chỉ cho ngày/tuần hiện tại; test UI là kiểm tĩnh cấu trúc.
- **Bản build:** chỉ `debug` ký bằng debug keystore (release chưa ký).

## 7. Quy tắc giai đoạn tiếp theo

Reviewer tái kiểm (mục 5) → Manager chuyển candidate sang nghiệm thu chính thức của Owner. Chỉ sửa code khi có lỗi thực tế được chứng minh; **không** tăng version thêm khi không có lỗi thật.
