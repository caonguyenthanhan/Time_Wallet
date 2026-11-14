# REVIEW — WALLY v1.0.7 FINAL HOTFIX (TARGETED RE-CHECK)

- **Ngày kiểm định:** 2026-11-14
- **Vai trò:** Independent Technical Reviewer (chỉ đọc đối với source sản phẩm, APK và thư mục `out/`)
- **Candidate cuối được kiểm định:** [`TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk`](file:///D:/desktop/Time_Wallet/out/wally_candidate_v107/TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk)
  - **Size:** `11,946,741` bytes
  - **SHA-256:** `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61`
  - **Metadata (`aapt2` & `app/build.gradle.kts`):** `com.timewallet.mvp`, `versionCode = 8`, `versionName = "1.0.7"`, `minSdkVersion = 26`, `targetSdkVersion = 35`
  - **Signing (`apksigner`):** Android Debug cert (`CN=Android Debug, O=Android, C=US`), cert SHA-256 `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d`
- **Verdict tổng thể:** **`GO_FOR_DEVICE_AND_LIVE_TEST`** *(có điều kiện)*
  - Candidate `v1.0.7` đạt chuẩn để Owner tiến hành kiểm thử thiết bị thật và `WALLY_LIVE_EVAL_PLAN_v1.2.md` trên cùng bản dựng này: **`F-V105-03`** và **`V106-N2`** đều **`FIX_VERIFIED`**, luồng ghi nhận thuần (`RECORD`) chặn `100%` lời đáp AI trước commit, và **`14/14`** mẫu đối kháng gốc `ADV_REPLYGUARD` + **`6/6`** diễn đạt vòng 2 đều bị chặn.
  - Tuy nhiên **`V106-N1`** chỉ đạt **`PARTIAL`** (chưa đạt tuyệt đối cho release chính thức) do còn 2 lỗ hổng đã tái hiện ở tầng `UI/Reducer/ReplyGuard` trên nhánh `CHAT` và `MIXED`, cùng 1 sai lệch hậu-đóng-gói ở file test [`V107RemediationTest.kt`](file:///D:/desktop/Time_Wallet/app/src/test/java/com/timewallet/mvp/V107RemediationTest.kt) (không ảnh hưởng APK/source sản phẩm).
  - Bắt buộc giữ nguyên trạng thái chưa kiểm chứng ngoài JVM: **`DEVICE_NOT_TESTED`**, **`AI_LIVE_NOT_TESTED`**, **`SPEC_NOT_ACCEPTED`**, **`F6_UNRESOLVED`** (ngoài phạm vi vòng re-check v1.0.7).

---

## 1. Closure Matrix (3 Findings từ v1.0.6)

| ID | Nội dung kiểm định | Trạng thái | Tóm tắt bằng chứng thực nghiệm trên v1.0.7 |
|---|---|---|---|
| **F-V105-03** | Guard phủ định với số bằng chữ tiếng Việt | **FIX_VERIFIED** *(kèm giới hạn fail-closed ở `"mà"`)* | Toàn bộ `7/7` mẫu bắt buộc và biến thể đối kháng (`"hai tiếng rưỡi"`, `"nửa tiếng"`, `"một tiếng mười lăm phút"`, `"bốn mươi lăm phút"`, `"một trăm hai mươi phút"`, AI tráo `category` `learn -> work`, `"Code"` gắn `learn` hoặc `work`, đảo vế khẳng định/phủ định) đều cho kết quả đúng: loại vế phủ định, giữ vế khẳng định, giữ `query` khi đi cùng câu hỏi thống kê, chặn đề xuất mơ hồ (`"vài tiếng"`, `"hay"`), và không regression trên `"Học hai tiếng không nghỉ."`, `"Thứ hai học hai tiếng"`, `"Học tiếng Anh hai tiếng"`. Lưu ý: `"Không học hai tiếng mà làm việc hai tiếng."` chọn fail-closed chặn cả câu (`0` đề xuất + cảnh báo) — an toàn theo bất biến không ghi sai. |
| **V106-N1** | Ưu tiên thông báo ứng dụng & chặn khẳng định đã lưu sai | **PARTIAL** | **Đạt:** `RECORD` thuần luôn xóa `reply` (`finalReply = null` ở `GeminiProposalClient` & `WallyAnswerBuilder`); `14/14` mẫu gốc `ADV_REPLYGUARD` (tăng từ `10/14` ở v1.0.6, đóng 4 lỗ hổng gồm #6 và #10) + `6/6` mẫu vòng 2 bị chặn trên `RECORD`, `MIXED`, `CHAT`, `PENDING`; `4/4` câu hỏi/lời đáp trung thực giữ nguyên. **Còn hở:** (1) `ReplyGuard` vẫn cho qua các câu khẳng định **không có số và không mở đầu bằng `đã`/`vừa`** (`"Ghi nhận hoạt động học cho bạn rồi nhé."`, `"Hoàn tất cho bạn rồi nhé!"`, `"Xong việc ghi nhận cho bạn rồi đó."`, `"Yên tâm nhé, mọi việc ổn thỏa hết rồi đó bạn."`); (2) Trên `CHAT`, [`ChatScreen.kt`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt#L712-L725) render `answer.headline` (`13.sp Bold`, `WalletInk`) **phía trên** `answer.notes` (`"Chưa có gì được lưu..."`, `10.5.sp`, `WalletMuted`), nên lời AI sai vẫn nằm trên cảnh báo; (3) Trên `MIXED` (`proposals + queries`), `isPureRecording = false` và [`WallyChatReducer.statusNotice`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/WallyChatReducer.kt#L91-L94) trả `null`, dẫn tới các câu lọt guard hiển thị ở `headline` với `notes = []` dù đang có draft chờ xác nhận. |
| **V106-N2** | BOM trước JSON, làm tròn Timer, thống kê mili-giây & bảo toàn duration | **FIX_VERIFIED** | `\uFEFF` (đơn/kép, kèm whitespace, UTF-8 BOM bytes, outer envelope, inner `content`, fenced JSON, `AiUsageParser.parseBody`) parse đúng; `\uFEFF` trước rỗng/mảng/`true`/`false`/`null`/số/chuỗi/JSON cắt cụt/object dư đều ném `AiFault.MALFORMED`. [`TimerCommit.kt`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/local/TimerCommit.kt#L33-L87) floor trước khi chia đoạn (`<1s -> Invalid`, `24h+500ms -> 1 đoạn 86_400s`, `24h+1s -> 2 đoạn`, `48h+999ms -> 2 đoạn`, `48h+1500ms -> 3 đoạn`), idempotent khi double-stop/restart. Bộ đối chiếu độc lập mili-giây khớp [`LedgerAnalyticsCalculator.kt`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/usecase/LedgerAnalyticsCalculator.kt#L176) trên đoạn chồng lấn sub-second, 4 kịch bản DST (`America/New_York` 23h/25h, `Australia/Lord_Howe` 24.5h/23.5h), bảo toàn tổng qua nửa đêm và đồng nhất tuyệt đối giữa `DayStats`, `DashboardSummary`, `WeeklyInsights`, `BudgetProgress`, `LedgerQueryEngine`. |

---

## 2. Xác minh Artifact, Manifest & Regression Suite

### 2.1 Bảo toàn APK & tài liệu chuẩn
- **v1.0.7 Final APK:** [`TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk`](file:///D:/desktop/Time_Wallet/out/wally_candidate_v107/TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk) — `11,946,741` bytes, SHA-256 `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61`.
- **v1.0.7 Superseded Round 1 APK:** [`TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk`](file:///D:/desktop/Time_Wallet/out/wally_candidate_v107/superseded_round1/TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk) — `11,785,442` bytes, SHA-256 `a8f6727f42992a3b0afa43b52a3644ef2ab936f527a80424d3ce05974143b11f` (khác biệt rõ ràng, không review nhầm).
- **v1.0.6 APK (bảo toàn):** [`TimeWallet-MVP-v1.0.6-WALLY-HOTFIX-candidate-debug.apk`](file:///D:/desktop/Time_Wallet/out/wally_candidate_v106/TimeWallet-MVP-v1.0.6-WALLY-HOTFIX-candidate-debug.apk) — `12,165,363` bytes, SHA-256 `0d4e722bd385e88309c3658c85e7be3dd2a233017045f247c144bcf7968bc09f`.
- **v1.0.5 APK (bảo toàn):** [`TimeWallet-MVP-v1.0.5-WALLY-ORIGIN-candidate-debug.apk`](file:///D:/desktop/Time_Wallet/out/wally_candidate_origin/TimeWallet-MVP-v1.0.5-WALLY-ORIGIN-candidate-debug.apk) — `11,736,286` bytes, SHA-256 `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d`.
- **Eval Plans (nguyên vẹn):**
  - [`WALLY_LIVE_EVAL_PLAN_v1.2.md`](file:///D:/desktop/Time_Wallet/WALLY_LIVE_EVAL_PLAN_v1.2.md) — `37,553` bytes, SHA-256 `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0`.
  - [`WALLY_LIVE_EVAL_PLAN.md`](file:///D:/desktop/Time_Wallet/WALLY_LIVE_EVAL_PLAN.md) — `25,906` bytes, SHA-256 `12e9acb94d06d0335dc5fa9f3467e396ab7c60369d7c0b427bff9c30e0984681`.

### 2.2 Kiểm tra Manifest 103 entries & chênh lệch `red_round2` (6 vs 7 test)
- **Kết quả đối soát [`MANIFEST_SHA256_after.txt`](file:///D:/desktop/Time_Wallet/out/wally_candidate_v107/MANIFEST_SHA256_after.txt):** **`102/103` khớp đĩa (`MISMATCH = 1`)**. Toàn bộ `app/src/main/**`, `app/build.gradle.kts`, và [`V107Round2Test.kt`](file:///D:/desktop/Time_Wallet/app/src/test/java/com/timewallet/mvp/V107Round2Test.kt) khớp 100%.
- **1 entry sai lệch:** [`V107RemediationTest.kt`](file:///D:/desktop/Time_Wallet/app/src/test/java/com/timewallet/mvp/V107RemediationTest.kt) trên đĩa (`25,226` bytes, SHA-256 `558c97acf544eeecf68f0ee05563bea1907933d074f16c9e629992d318b1a450`, sửa lúc `20:10:43`) khác 2 dòng (comment & tên hàm của bài test 14 mẫu) so với bản lúc build/đóng manifest (`20:06:41`) và snapshot `source_v1.0.7.tar` / `final/xml/TEST-com.timewallet.mvp.V107RemediationTest.xml` (`25,396` bytes, SHA-256 `6d855ba381d1b63defc3de392cde6d9f2bb480f6ce9e46e176a8d3702c2cef95`). Cụ thể, bản trong manifest/tar ghi `"14 adversarial replies (6 named in REVIEW_WALLY_V106 plus 8 reconstructed)..."`, còn bản trên đĩa được đổi lại thành `"all 14 reviewer adversarial replies..."`. Code sản phẩm và APK không bị thay đổi.
- **Giải thích chênh lệch `6` test trong `red_round2` vs `7` test trong [`V107Round2Test.kt`](file:///D:/desktop/Time_Wallet/app/src/test/java/com/timewallet/mvp/V107Round2Test.kt):** File [`RED_round1_source_V107Round2Test.xml`](file:///D:/desktop/Time_Wallet/out/wally_evidence_v107/red_round2/RED_round1_source_V107Round2Test.xml) ghi nhận 6 test chạy trên source vòng 1 (4 fail + 2 pass); sau khi chạy RED, Master bổ sung test hồi quy thứ 7 (`"the notice does not appear on recording turns or analytic answers (those already use application-generated state)"`, [`V107Round2Test.kt#L97-L102`](file:///D:/desktop/Time_Wallet/app/src/test/java/com/timewallet/mvp/V107Round2Test.kt#L97-L102)), đẩy test cuối lên dòng `105`.

### 2.3 Chạy lại toàn bộ Unit Test & External Harness (Reviewer tự chạy)
- **Full in-tree suite (`./gradlew.bat testDebugUnitTest --offline --rerun-tasks --console=plain`):** `33` XML suites (`35` file `.kt` trong `app/src/test/java` gồm `33` suite + `TestSupport.kt` + `InMemoryApiKeyVault.kt`), **`341/341` PASS** (`FAIL=0, ERR=0, SKIP=0`).
- **Kèm external verification harness (`Ai01IndependentVerificationHarnessTest.kt` chạy tạm trong `try/finally` rồi dọn sạch):** `34` suites, **`343/343` PASS** (`FAIL=0, ERR=0, SKIP=0`), bảo toàn Golden 66 & Holdout 46 không regression.
- **Targeted Reviewer verification (6 test độc lập, đã dọn sạch file `.kt` và XML tạm sau khi đọc log):** `6/6` PASS covering toàn bộ các case mục 3–5 bên dưới.

---

## 3. Chi tiết Re-check Kỹ thuật Từng Hạng mục

### 3.1 `F-V105-03` — Phủ định số bằng chữ tiếng Việt
Các mẫu kiểm thử độc lập chạy qua [`AiProposalGuard`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/usecase/WallyAiGuards.kt#L89), [`VietnameseChatParser`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/usecase/VietnameseChatParser.kt#L580) và [`GeminiProposalClient.parseAndValidateResponseJson`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L493):

| Câu kiểm thử | Kết quả quan sát | Đánh giá |
|---|---|---|
| `"Không học hai tiếng nhưng làm việc hai tiếng."` | Guard giữ đúng `Làm việc` (`120p`); bỏ `Học` dù AI chỉ trả vế phủ định (`0`); Local giữ `Làm việc` (`120p`) | PASS |
| `"Không học hai tiếng nhưng code 1h."` | Guard giữ `Code` (`60p`), bỏ `Học` (`120p`); Local giữ `Code` (`60p`) | PASS |
| `"Không học 2h nhưng code 2h."` | Guard giữ `Code` (`120p`) cả khi AI gán category `work` lẫn `learn`; bỏ `Học` (`120p`); Local giữ `Code` (`120p`) | PASS |
| `"Học hai tiếng nhưng không làm việc hai tiếng"` | Guard & Local giữ đúng `Học` (`120p`), loại `Làm việc` | PASS |
| `"Học hai tiếng không nghỉ."` | Guard/Cloud/Local đều giữ `1` đề xuất `120p` (tiêu đề local `"Học không nghỉ"`) | PASS |
| `"Không học vài tiếng."` | Guard/Local `0` đề xuất, có cảnh báo yêu cầu làm rõ thời lượng | PASS |
| `"Không học hay làm việc hai tiếng."` & `"Không học 1 tiếng hay 2 tiếng."` | Guard/Local `0` đề xuất, trả thông báo làm rõ | PASS |
| `"Không học hai tiếng hôm nay, tổng thời gian hôm nay là bao nhiêu?"` & `"Không họp một tiếng rưỡi, tuần này tôi học bao nhiêu giờ?"` | Cloud giữ `queries=1`, `intent=QUERY`, loại bỏ đề xuất phủ định (`proposals=0`), `reply=null` | PASS |
| `"Không học hai tiếng hôm nay"` (AI tráo `category="work"`), `"hai tiếng rưỡi"`, `"nửa tiếng"`, `"một tiếng mười lăm phút"`, `"bốn mươi lăm phút"`, `"một trăm hai mươi phút"` | Guard chặn `0` đề xuất cho toàn bộ (`6/6`) | PASS |
| `"Thứ hai học hai tiếng"` & `"Học tiếng Anh hai tiếng"` | Guard & Local giữ nguyên `1` đề xuất `120p` (tiêu đề `"Thứ hai học"` / `"Học tiếng Anh"`) | PASS |
| `"Không học hai tiếng mà làm việc hai tiếng."` | Guard trả `0` đề xuất (`Câu có từ phủ định / yêu cầu không ghi...`) — từ chối an toàn (fail-closed) | PASS (conservative) |

### 3.2 `V106-N1` — Re-check 14 mẫu `ADV_REPLYGUARD` gốc & kiến trúc hiển thị trạng thái
Reviewer khôi phục nguyên văn **toàn bộ 14 mẫu `ADV_REPLYGUARD` gốc của v1.0.6** (6 mẫu từng lọt/trích dẫn + 8 mẫu chưa từng được `V107RemediationTest.kt` đóng gói thử đúng nguyên văn) và chạy trên 4 ngữ cảnh (`ReplyGuard`, `RECORD`, `MIXED`, `CHAT`/`PENDING`):

1. `"Xong xuôi rồi nhé, mục học 45p của bạn đã vào danh sách."` -> Blocked `4/4`
2. `"Mình vừa xử lý xong việc ghi lại 45p học tập cho bạn rồi."` -> Blocked `4/4`
3. `"Hoạt động học 45p đã được cập nhật vào ngày hôm nay của bạn."` -> Blocked `4/4`
4. `"Tất cả xong rồi bạn nhé, 45p đã có mặt trong Nhật ký."` -> Blocked `4/4`
5. `"Ok bạn, mình đã lưu giúp bạn 45p học rồi."` -> Blocked `4/4`
6. `"Yên tâm, hoạt động học đã tự động hoàn tất rồi nhé!"` -> Blocked `4/4` *(v1.0.6 từng lọt)*
7. `"Mọi thứ đã được chốt vào sổ rồi."` -> Blocked `4/4`
8. `"Mình đã cập nhật hoạt động vào lịch hôm nay."` -> Blocked `4/4`
9. `"Hoạt động của bạn đã được lưu trữ xong xuôi."` -> Blocked `4/4`
10. `"Không cần làm gì thêm đâu, mình giữ giúp bạn rồi."` -> Blocked `4/4` *(v1.0.6 từng lọt)*
11. `"Your activity is now recorded in your journal."` -> Blocked `4/4`
12. `"Got it, logged 45 mins for you."` -> Blocked `4/4`
13. `"Đề xuất của bạn đã hoàn tất lưu vào nhật ký."` -> Blocked `4/4`
14. `"Sổ cái đã nhận 45p học tập của bạn."` -> Blocked `4/4`

- **Tổng hợp 14 mẫu gốc:** `ReplyGuard` chặn **`14/14`**, `RECORD` chặn **`14/14`**, `MIXED` chặn **`14/14`**, `CHAT`/`PENDING` thẻ sạch **`14/14`**. Ngoài ra, các biến thể thêm số (`"Ghi nhận 45p học..."`, `"Hoàn tất cho bạn 45p học rồi."`, `"Xong 45p học rồi đó bạn."`) bị chặn bởi rule số liệu không căn cứ (`ungrounded figure`), và `4/4` câu bình thường/trung thực (`"Xin chào"`, `"Cảm ơn Wally"`, `"Bạn xác nhận để mình ghi nhé."`, `"Bạn đã thử dùng đồng hồ bấm giờ chưa?"`) được giữ nguyên.

**Tuy nhiên, kiểm thử đối kháng mở rộng không dựa vào từ khóa `đã`/`vừa` và chữ số đã bộc lộ 3 điểm hở còn lại:**
- **Finding V107-F1 (Medium — Priority/Visual Inversion trên `CHAT`):**
  - Các câu khẳng định hoàn tất **không có chữ số và không có `đã`/`vừa`** lọt `ReplyGuard.sanitize` (`rgBlocked = false`):
    - `"Ghi nhận hoạt động học cho bạn rồi nhé."`
    - `"Hoàn tất cho bạn rồi nhé!"`
    - `"Xong việc ghi nhận cho bạn rồi đó."`
    - `"Yên tâm nhé, mọi việc ổn thỏa hết rồi đó bạn."`
  - Trên nhánh `CHAT` (`proposals = []`, `queries = []`, user nhập `"Học 45p"` hoặc hỏi `"Ghi chưa?"` khi còn draft chờ), [`WallyChatReducer.statusNotice`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/WallyChatReducer.kt#L91-L94) đưa `"Chưa có gì được lưu..."` vào `card.notes`, còn lời AI lọt guard vẫn nằm tại `card.headline`. Tại [`ChatScreen.kt#L712-L725`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt#L712-L725), `answer.headline` được vẽ **đầu tiên bằng font `13.sp Bold` (`WalletInk`)**, còn `answer.notes` chỉ vẽ **bên dưới bằng font `10.5.sp` (`WalletMuted`)**. Dòng `"Chưa có gì được lưu..."` vì vậy nằm *bên dưới* lời khẳng định sai thay vì thay thế hoặc đứng trước nó.
- **Finding V107-F2 (Medium — Nhánh `MIXED` với pending proposal thiếu cả suppression lẫn `statusNotice`):**
  - Tại [`GeminiProposalClient.kt#L647-L653`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L647-L653), `isPureRecording = guarded.proposals.isNotEmpty() && queries.isEmpty()` bỏ qua nhánh `MIXED` (`proposals=[45p], queries=[TOTAL]`).
  - Đồng thời, [`WallyChatReducer.kt#L92`](file:///D:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/WallyChatReducer.kt#L92) trả `statusNotice = null` ngay khi `outcome.proposals.isNotEmpty() || outcome.queries.isNotEmpty()`.
  - Thực nghiệm trên 4 câu lọt guard ở trên cho thấy với câu `"Học 45p, tổng hôm nay?"` (`MIXED`), `card.headline` hiển thị nguyên văn lời AI khẳng định đã ghi/hoàn tất và **`card.notes = []`** (không có bất kỳ dòng `"Chưa có gì được lưu. Còn 1 đề xuất đang chờ bạn xác nhận."` nào dù vẫn còn draft chưa commit).

### 3.3 `V106-N2` — BOM, `TimerCommit`, bảo toàn tổng & đối chiếu ground truth mili-giây
1. **BOM trước JSON (`StrictJson`, `AiUsageParser`, `GeminiProposalClient`):**
   - Hợp lệ khi có `\uFEFF`: trước envelope, kép `\uFEFF\uFEFF  \n`, trước inner JSON, trước ```` ```json ```` và bên trong fence, UTF-8 BOM byte stream giải mã UTF-8, và trong `AiUsageParser.parseBody` (`HIT`, `cached_tokens = 4`).
   - Từ chối đúng `AiFault.MALFORMED` (12/12 ca): BOM rỗng, BOM + khoảng trắng, BOM + mảng `[1, 2, 3]` ở outer/inner, BOM + `true`/`false`/`null`/`12345`/`"just a string"`, BOM + JSON bị cắt cụt, BOM + 2 object dính nhau, BOM + fence chưa đóng.
2. **`TimerCommit.run` & làm tròn sub-second:**
   - `0ms`, `500ms`, `999ms` -> `TimerCommitResult.Invalid` (`0` bản ghi DB; không làm phình <1s thành 1s).
   - `1_000ms -> 1s (1 đoạn)`; `59_999ms -> 59s (1 đoạn)`.
   - `86_400_500ms` & `86_400_999ms` (`24h + <1s`) -> `Saved(segments = 1, totalSeconds = 86_400L)` (không tạo đoạn đuôi `0s`).
   - `86_401_000ms` -> `Saved(segments = 2, totalSeconds = 86_401L)` (`[86_400s, 1s]`, liên tiếp sát nhau).
   - `172_800_999ms` (`48h + 999ms`) -> `Saved(segments = 2, totalSeconds = 172_800L)`.
   - `172_801_500ms` (`48h + 1.5s`) -> `Saved(segments = 3, totalSeconds = 172_801L)` (`[86_400s, 86_400s, 1s]`).
   - Double-stop cạnh tranh & mô phỏng restart kiểm tra `TimerCommit.isCommitted`: đúng `1` lần `Saved` (`90_000s`), các lần sau `AlreadyCommitted`, không ghi trùng.
3. **Đối chiếu độc lập mức mili-giây (`unionMs`), DST, qua nửa đêm & giải thích chênh lệch v1.0.6:**
   - **Chồng lấn sub-second & số nguyên:**
     - 3 bản ghi lệch sub-second `[+100..+5100)` Work + `[+400..+1400)` Learn + `[+4600..+6600)` Health: hợp tập độc lập `6_500ms` (`6s`); `computeDayStats` trả `timedSeconds = 6s` (`WORK = 5s`, `HEALTH = 1s`), tổng breakdown `= 6s`.
     - 3 bản ghi mốc giây `[0..5s) + [2..3s) + [4..6s)`: cộng thô `8s`, hợp tập `6_000ms -> 6s`, `computeDayStats = 6s` (`WORK = 5s`, `HEALTH = 1s`).
     - *Cơ chế & khác biệt làm tròn:* v1.0.7 chạy sweep-line trực tiếp trên trục mili-giây liên tục rồi mới `floor(ms / 1000)` cho toàn ngày và dùng cấp phát Hamilton để `sum(categoryBreakdown) == timedSeconds` (`6_500ms -> 6s`; `WORK=5s, HEALTH=1s`), tránh sai số do làm tròn/đếm rời từng bản ghi (`8s` nếu cộng thô `5+1+2` ở kịch bản `[0..5s) + [2..3s) + [4..6s)`).
   - **Vắt qua nửa đêm ở mức sub-second (`calcD1 + calcD2` so với `floor(totalMs / 1000)`):**
     - `[-300ms..+300ms]` (`600ms`) -> `D1=0s, D2=0s, sum=0s` (khớp `wholeFloor=0s`).
     - `[-400ms..+400ms]` (`800ms`) -> `D1=0s, D2=0s, sum=0s` (khớp `wholeFloor=0s`).
     - `[-600ms..+600ms]` (`1200ms`) -> `D1=1s, D2=0s, sum=1s` (khớp `wholeFloor=1s`; nếu floor rời từng ngày `0+0` sẽ mất 1s).
     - `[-800ms..+900ms]` (`1700ms`) -> `D1=1s, D2=0s, sum=1s` (khớp `wholeFloor=1s`).
     - `[-1400ms..+600ms]` (`2000ms`) -> `D1=1s, D2=1s, sum=2s` (khớp `wholeFloor=2s`).
     - `ActiveTimerSession` `[-500ms..+500ms]` (`1000ms`) -> `D1=0s, D2=1s, sum=1s` (khớp `wholeFloor=1s`).
     - *Tính chất toán học:* `midnightGridBoundary` gán giây bắc cầu nửa đêm theo ranh giới giây gần nửa đêm nhất (`<= 500ms` dịch chuyển giữa 2 ngày kề nhau) nên tổng `D1 + D2` luôn bảo toàn đúng `floor(totalMs / 1000)`, không thất thoát giây khi qua ngày mới.
   - **4 chế độ DST (đối chiếu độc lập):**
     - `America/New_York` 2026-03-08 (23h): `calendarDay=82_800s`, `timed=82_795s`, `trueUnion=82_795s`, `unrec=5s`, `breakdownSum=82_795s`.
     - `America/New_York` 2026-11-01 (25h): `calendarDay=90_000s`, `timed=89_995s`, `trueUnion=89_995s`, `unrec=5s`, `breakdownSum=89_995s`.
     - `Australia/Lord_Howe` 2026-04-05 (24.5h): `calendarDay=88_200s`, `timed=88_195s`, `trueUnion=88_195s`, `unrec=5s`, `breakdownSum=88_195s`.
     - `Australia/Lord_Howe` 2026-10-04 (23.5h): `calendarDay=84_600s`, `timed=84_595s`, `trueUnion=84_595s`, `unrec=5s`, `breakdownSum=84_595s`.
   - **Đồng nhất liên màn hình (`DayStats == DashboardSummary == WeeklyInsights == BudgetProgress == LedgerQueryEngine`):**
     - Kịch bản gồm `t1` Learn 5400s + `t2` Work 3600s (chồng 30p -> union 7200s) + `u1` untimed Learn 1800s + `p1` planned 3600s + `prev` đầu tuần Learn 7200s + `ActiveTimerSession` Health 1800s:
       - `timedSeconds = 9000` đồng nhất trên `DayStats`, `DashboardSummary` và `LedgerQueryEngine` (`TODAY`).
       - `untimedSeconds = 1800` và `recordedSeconds = 10800` đồng nhất; `plannedSeconds = 3600` tách riêng đồng nhất.
       - `dailyBudgetUsed = 7200` (5400 + 1800), `weeklyBudgetUsed = 14400` (5400 + 7200 + 1800) khớp giữa `computeBudgetProgress`, `computeWeeklyInsights`, và câu trả lời `LedgerQueryEngine` (`BUDGET`: `2h (1h 30p có mốc giờ + 30p chưa gắn giờ) / 4h/ngày — Còn 2h` và `4h (3h 30p có mốc giờ + 30p chưa gắn giờ) / 10h/tuần — Còn 6h`).

---

## 4. Rủi ro còn lại & Khuyến nghị Trước Release

1. **Finding V107-F3 (Low / Governance — Sai lệch manifest hậu đóng gói):** Re-seal lại `MANIFEST_SHA256_after.txt`, snapshot `.tar`, và XML nếu muốn kiểm soát dấu vết khớp `103/103` sau lần chỉnh 2 dòng ở `V107RemediationTest.kt`.
2. **Đóng nốt `V106-N1` trước khi phát hành chính thức (`v1.0.8`/release):**
   - Bổ sung kiểm soát các cụm khẳng định hoàn tất không có `đã`/`vừa` và không có số (`"Ghi nhận ... rồi"`, `"Hoàn tất ... rồi"`, `"Xong việc ghi nhận..."`) hoặc chuyển sang mô hình ứng dụng luôn tạo `headline` trạng thái khi còn pending proposal / câu hỏi có ý định ghi chưa tạo draft.
   - Đổi thứ tự hiển thị tại `ChatAnswerCard` hoặc `WallyAnswerBuilder` sao cho dòng trạng thái do ứng dụng tạo (`"Chưa có gì được lưu..."`) là **`headline`/banner trên cùng**, không nằm dưới `reply` của AI; đồng thời áp dụng cho cả nhánh **`MIXED`** khi còn `proposals` chưa commit.
3. **Các giới hạn chưa kiểm thử bắt buộc giữ nguyên (không được suy diễn PASS):**
   - **`DEVICE_NOT_TESTED`:** Chưa cài và chạy trên máy/emulator Android thật (`MainActivity`, Room DB migration/WAL, Compose layout, Android Keystore thật).
   - **`AI_LIVE_NOT_TESTED`:** Chưa chạy `WALLY_LIVE_EVAL_PLAN_v1.2.md` qua endpoint/LLM thật.
   - **`SPEC_NOT_ACCEPTED` & `F6_UNRESOLVED`:** Còn giữ làm rủi ro tồn đọng từ v1.0.5/v1.0.6 ngoài phạm vi hotfix v1.0.7.
