# WALLY v1.0.7 — Hồ sơ candidate hotfix (`versionCode 8`) và gói tái kiểm mục tiêu cho Reviewer

**Trạng thái:** CANDIDATE_UNVERIFIED — chờ Reviewer tái kiểm **đúng phần thay đổi** (mục 6), rồi chuyển Owner nghiệm thu chính thức.
**Không tuyên bố:** DEVICE_PASS, AI_LIVE_PASS, SPEC_ACCEPTED. **F6: UNRESOLVED** (chưa có bằng chứng nguyên nhân).
**Phạm vi:** chỉ 3 finding còn mở của `MANAGER DIRECTIVE — V106 FINAL REMEDIATION`: `F-V105-03`, `V106-N1`, `V106-N2`. Giữ API-first, 8 tầng, origin binding, chính sách riêng tư. Không thêm tính năng. Không emulator/ADB, không gọi API thật, không xin credential qua chat.

## 0. Lịch sử bản dựng v1.0.7 (đọc trước khi dùng bất kỳ SHA nào)

| Bản dựng | SHA-256 APK | Trạng thái |
|---|---|---|
| **v1.0.7 vòng 2 (bản này)** | `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61` | **Candidate hiện hành** |
| v1.0.7 vòng 1 | `a8f6727f42992a3b0afa43b52a3644ef2ab936f527a80424d3ce05974143b11f` | **ĐÃ THAY THẾ**, giữ ở `out/wally_candidate_v107/superseded_round1/` (cùng versionCode 8). Không dùng để nghiệm thu |
| v1.0.6 | `0d4e722bd385e88309c3658c85e7be3dd2a233017045f247c144bcf7968bc09f` | Nguyên vẹn, **khác binary** |
| v1.0.5 | `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` | Nguyên vẹn |

**Vì sao có vòng 2:** sau khi vòng 1 đã đóng gói và trước khi bàn giao, Master kiểm độc lập vòng 1 bằng probe riêng (xem mục 4) và thấy `V106-N1` chưa được đóng hết: một số lời đáp **chỉ-AI** (không kèm đề xuất) khẳng định đã lưu bằng cách diễn đạt chưa có trong regex — ví dụ "Ok, mình đã chép vào sổ giúp bạn." — vẫn hiển thị nguyên văn. Đây đúng là điều chỉ thị yêu cầu không chỉ vá regex. Vòng 2 chỉ đổi `WallyAiGuards.kt` và `WallyChatReducer.kt` (+ test); ba finding còn lại không đổi so với vòng 1.
**Mọi kết quả kiểm thử của vòng 1 và của v1.0.6 KHÔNG được chuyển sang binary này.**

## 1. Nhận diện nhị phân

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v107/TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk` (11.946.741 byte) |
| SHA-256 | `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61` (`APK_SHA256.txt`; trùng `app-debug.apk` vừa build) |
| Package / version | `com.timewallet.mvp` · versionCode **8** · versionName **1.0.7** (`aapt2 dump badging`, `output-metadata.json`) |
| Chữ ký | Debug keystore, chứng chỉ SHA-256 `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d`, **trùng v1.0.6** ⇒ cài đè được (`SIGNING_CERT_COMPARE.txt`) |
| Nguồn | `source_v1.0.7.tar` SHA-256 `8f733b0e73fb336603337a4452c411978840be134d8e5ff2354b42159326dde0`; `MANIFEST_SHA256_after.txt` **103 dòng**, `sha256sum -c` = 103 OK / 0 sai (`MANIFEST_VERIFY.txt`) |
| Snapshot trước khi sửa | `out/wally_v107_snapshot_pre/source_v1.0.6_pre_V107.tar` (`155d2048…3529`, trước vòng 1) và `out/wally_v107_snapshot_pre_round2/source_v1.0.7_round1_pre_round2.tar` (`d02c4775…ef550`, trước vòng 2) |
| Diff | `SOURCE_DIFF_vs_v1.0.6.patch` (9 tệp đổi, 2 tệp mới, 0 xóa); `SOURCE_DIFF_round2_vs_round1.patch` (chỉ phần vòng 2) |
| Log build | `assemble_console.txt` (không rỗng), `assemble_exitcode.txt` = `exit=0` |
| Live Eval | `WALLY_LIVE_EVAL_PLAN_v1.2.md` giữ nguyên, SHA-256 `0b5144c8…17ea0` khớp `.sha256`; v1.1 cũng khớp. Expected không đổi |

## 2. Thay đổi theo finding (so với v1.0.6)

### 2.1 `F-V105-03` — phủ định với số bằng chữ tiếng Việt
`VietnameseChatParser.normalizeSpelledDurations` chuẩn hóa số bằng chữ thành chữ số **trước** khi tách mệnh đề: 1–999 ("hai mươi lăm phút", "một trăm hai mươi phút"), "nửa tiếng", "một tiếng rưỡi", "hai phẩy năm giờ", phút đọc chữ sau mốc giờ ("14 giờ hai mươi lăm"). Có chốt chặn số thứ tự/ngày ("thứ hai", "tháng năm", "lần", "tầng"…) và "tiếng Anh/Việt…". Hai trường hợp **không phân biệt được** thì **không tạo đề xuất và yêu cầu làm rõ**: (a) phủ định đứng trước thời lượng mơ hồ ("vài tiếng", "mấy giờ", "dăm phút"…); (b) một từ phủ định nối chung thời lượng bằng "và/hay/hoặc" ("không học và đọc sách hai tiếng"). Cùng thời lượng ("không học hai tiếng nhưng làm việc hai tiếng"), câu hỗn hợp và "không nghỉ" giữ đúng phần khẳng định.
Tệp: `VietnameseChatParser.kt`, `WallyAiGuards.kt` (`AiProposalGuard.apply`).

### 2.2 `V106-N1` — không cho lời đáp AI tuyên bố đã lưu trước commit
Ba lớp, **lớp cấu trúc đứng trước regex**:
1. **Luồng ghi nhận thuần** (có đề xuất chờ xác nhận, không có truy vấn): `finalizeOutcome` bỏ hẳn lời đáp tự do của AI; `WallyAnswerBuilder` không dựng thẻ lời đáp. Người dùng chỉ thấy thẻ đề xuất và toast trạng thái do ứng dụng tạo (`GeminiProposalClient.kt`, `WallyAiGuards.kt`).
2. **Lời đáp chỉ-AI** (không đề xuất, không truy vấn; **mới ở vòng 2**): `WallyChatReducer.statusNotice` luôn gắn vào thẻ một dòng do **ứng dụng** tạo: "Chưa có gì được lưu. Còn N đề xuất đang chờ bạn xác nhận." (khi còn draft chờ) hoặc "Chưa có gì được lưu: Wally chưa tạo đề xuất ghi nhận nào từ câu này…" (khi câu người dùng có thời lượng). Nhờ đó dù AI dùng cách nói nào mà regex chưa biết, người dùng vẫn thấy trạng thái giao dịch thật — kể cả khi đang có pending proposal.
3. **`ReplyGuard` (regex, heuristic)** mở rộng: cách nói gián tiếp ("đã tự động hoàn tất", "giữ giúp bạn rồi", "đã vào danh sách", "có mặt trong Nhật ký", "Sổ cái đã nhận", "xử lý xong việc ghi lại"…) và, ở vòng 2, "đã chép vào sổ", "note lại rồi", "sẵn sàng trong sổ", "hoạt động/bản ghi đã được tạo / đã sẵn có", "hoàn thành việc nhập liệu". Vẫn giữ các câu trung thực ("Bạn xác nhận để mình ghi nhé", "Mình chưa ghi gì cả", "Mình sẽ không tự động xác nhận thay bạn").

**Về "14 mẫu đối kháng của Reviewer":** `REVIEW_WALLY_V106.md` chỉ nêu tên **6** câu (2 lọt ở 0 đề xuất, 6 lọt khi có đề xuất, chồng nhau) và nói 8 câu còn lại bị chặn đúng mà không liệt kê; tệp probe gốc `ADV_REPLYGUARD` không có trong repo. Vì vậy bộ 14 câu trong `V107RemediationTest` gồm **6 câu có tên + 8 câu do Master tái dựng**, không phải 14 câu nguyên văn của Reviewer. **Đề nghị Reviewer chạy lại probe gốc của mình trên APK này.**

### 2.3 `V106-N2` — BOM và bảo toàn thời lượng
- **BOM:** `﻿` dẫn đầu được bỏ trước mọi kiểm tra JSON (envelope, `content`, khối fence, `usage`). `﻿[1, 2]`, `﻿null`, `﻿true`, `﻿123`, `﻿"x"` ⇒ `MALFORMED`; `﻿{…}` và `﻿` + văn bản thường ⇒ phân tích bình thường. BOM đứng **sau** object ⇒ `MALFORMED` (phần dư). (`StrictJson.kt`, `AiUsageParser.kt`)
- **Timer:** `TimerCommit.run` quy phiên về `floor((end−start)/1000)` giây, từ chối phiên dưới 1 giây (không phình thành 1 giây), cắt các đoạn trên mốc đã căn nên tổng các đoạn **đúng bằng** số giây đó, đoạn nào cũng ≤ 24h và liền nhau (`TimerCommit.kt`).
- **Thống kê ngày:** `computeDayStats` cộng theo mili-giây (sweep-line, phần PLANNED cũng vậy) rồi mới quy đổi sang giây; phần dư theo danh mục được phân bổ để tổng danh mục = tổng `timedSeconds`; bản ghi vắt qua nửa đêm có mili-giây lẻ không mất giây giữa hai ngày (`LedgerAnalyticsCalculator.kt`). Đây là phần thay đổi lớn nhất về dòng mã; xem kiểm chứng đối chiếu ở mục 4.3.

## 3. Bằng chứng kiểm thử

| Giai đoạn | Thư mục | Kết quả |
|---|---|---|
| Baseline trước vòng 1 (v1.0.6) | `out/wally_evidence_v107/superseded_round1/baseline/` | 31 tệp XML / **322** test, 0 lỗi |
| RED vòng 1 | `out/wally_evidence_v107/red_v107.txt` | `V107RemediationTest` trên **mã v1.0.6**: 12 test, **11 FAIL** |
| GREEN vòng 1 (đã thay thế) | `out/wally_evidence_v107/superseded_round1/final*/` | 334 / 336 test PASS |
| RED vòng 2 | `out/wally_evidence_v107/red_round2/` | `V107Round2Test` trên **mã vòng 1**: 6 test, **4 FAIL** (2 test còn lại là chốt hồi quy) |
| **Final, trong cây (vòng 2)** | `out/wally_evidence_v107/final/` (`console.txt`, `exitcode.txt` = 0, `xml/`, `per_suite_counts.txt`) | 33 tệp XML / **341/341 PASS** = 322 + 12 (`V107RemediationTest`) + 7 (`V107Round2Test`); 0 fail, 0 error, 0 skip |
| **Final, kèm 2 test ngoài cây** | `out/wally_evidence_v107/final_with_external/` (+ `golden_holdout_extract.txt`) | 34 tệp / **343/343 PASS**; **golden 66/66**, **holdout 46/46** (harness chép tạm vào cây rồi **đã xóa**) |
| Build APK | `out/wally_candidate_v107/assemble_console.txt` | `exit=0` |

Nhãn trung thực: golden/holdout chỉ chứng minh bộ phân tích cục bộ cũ, không thay cho đánh giá AI thật. Không test nào bị xóa hay nới lỏng; tên test 14 mẫu được sửa cho đúng bản chất (xem 2.2).

## 4. Kiểm chứng độc lập của Master (probe cách ly, không nằm trong cây của candidate)

Probe chạy trên bản sao cách ly của mã nguồn; không đổi cây làm việc. Kết quả tóm tắt:

1. **Phủ định số bằng chữ (30 câu, vòng 1):** pure negation ⇒ chặn; cùng thời lượng, hỗn hợp, "không nghỉ", "tiếng Anh", "thứ hai", "nửa tiếng", "hai mươi lăm phút", nhiều mệnh đề ⇒ đúng kỳ vọng; "vài tiếng", "không học hay làm việc hai tiếng" ⇒ chặn kèm yêu cầu làm rõ. Ghi chú: "Không học hai tiếng **mà** làm việc hai tiếng" bị chặn cả câu (an toàn nhưng mất phần khẳng định) và thông báo là "phủ định/không ghi", chưa phải lời yêu cầu làm rõ.
2. **Lời đáp (18 cách diễn đạt mới + 3 câu trung thực):** sau vòng 1, 6 câu chỉ-AI vẫn lọt regex ⇒ dẫn tới vòng 2. Sau vòng 2 cả 6 bị chặn và `V107Round2Test` ghim chúng; riêng câu hoàn toàn lách (ví dụ "Yên tâm nhé, mọi việc ổn thỏa hết rồi đó bạn.") vẫn hiển thị nhưng **luôn kèm dòng trạng thái của ứng dụng** (test cấu trúc).
3. **Bảo toàn thời lượng:** (a) 600 phiên timer ngẫu nhiên, ba múi giờ (có DST, Lord Howe): tổng giây các đoạn = `floor(len/1000)`, mỗi đoạn ≤ 86.400 s, liền nhau, bắt đầu/kết thúc đúng; phiên < 1 s bị từ chối. (b) `computeDayStats` v1.0.6 ↔ v1.0.7 trên **cùng 1.599 kịch bản ngẫu nhiên có hạt giống** (tối đa 6 bản ghi, 2 múi giờ, có PLANNED, chưa gắn giờ và timer đang chạy): **dữ liệu tròn giây** khác nhau ở 38/799 kịch bản, mọi chênh lệch đúng **1 s** theo ngày (do `now` có phần mili-giây: cộng mili-giây trước khi cắt giây); **dữ liệu có mili-giây** khác ở 638/800 kịch bản, chênh lệch theo ngày tối đa **5 s**, theo kịch bản (tổng `timed`) tối đa **6 s**; ở **0/1.599** kịch bản tổng `timed` mới thấp hơn cũ. Một số ngày có −1 s (và ngày kề bên +1 s) khi giây vắt qua nửa đêm được gán cho ngày chứa điểm giữa của giây đó. Đây là thay đổi **có chủ đích về độ chính xác**, nhưng là thay đổi hiển thị ở mức giây trên thống kê; Reviewer nên xem xét có chấp nhận không.
4. **BOM (20 biến thể):** như mục 2.3; ZWSP/NBSP **đứng đầu** được bỏ (an toàn), phần dư sau object ⇒ `MALFORMED`.

## 5. Còn lại / không sửa (giữ minh bạch)

- **F6 UNRESOLVED.** Mọi mục thiết bị / AI thật vẫn **NOT_TESTED**.
- `ReplyGuard` là heuristic: không bảo đảm bắt mọi cách diễn đạt; bảo đảm cấu trúc là (1) bỏ lời đáp ở luồng ghi nhận và (2) dòng trạng thái của ứng dụng ở lời đáp chỉ-AI. Lời đáp **truy vấn** (có thẻ số liệu) vẫn qua regex, không có dòng trạng thái vì đã có thẻ do ứng dụng tính.
- Số bằng chữ hiếm/phương ngữ chưa chuẩn hóa được ⇒ đề xuất vẫn phải qua bước xác nhận của người dùng.
- "Một giờ rưỡi chiều" đứng một mình được hiểu là 90 phút (không phải 13:30); "14 giờ hai mươi lăm" thành "14 giờ 25 phút" rồi đi qua cùng quy tắc mơ hồ giờ/thời lượng sẵn có với chữ số. Không phải hồi quy.
- Nhật ký chưa lazy, K2 backlog, K3 không đổi (theo chỉ thị trước).

## 6. Gói tái kiểm mục tiêu cho Reviewer

**Chỉ các tệp sau đổi so với v1.0.6** (`CHANGED_FILES_vs_v1.0.6.txt`): `AiUsageParser.kt`, `GeminiProposalClient.kt`, `StrictJson.kt`, `TimerCommit.kt`, `LedgerAnalyticsCalculator.kt`, `VietnameseChatParser.kt`, `WallyAiGuards.kt`, `WallyChatReducer.kt`, `app/build.gradle.kts`; mới: `V107RemediationTest.kt`, `V107Round2Test.kt`. Phần **riêng của vòng 2**: `WallyAiGuards.kt`, `WallyChatReducer.kt` (`SOURCE_DIFF_round2_vs_round1.patch`).

| Việc | Cách làm |
|---|---|
| Xác minh binary | `sha256sum` APK = `3df0fee7…c61`; `aapt2 dump badging` = versionCode 8 / 1.0.7; `apksigner verify --print-certs` = cert `402f1d41…530d`; `sha256sum -c` manifest (bỏ CR nếu mở bằng PowerShell) = 103 OK |
| Chạy lại toàn bộ | `JAVA_HOME=<JDK 22> ./gradlew testDebugUnitTest --offline --rerun-tasks` ⇒ 341 test, 0 fail; chép `Ai01IndependentVerificationHarnessTest.kt` tạm vào cây ⇒ 343 (xóa lại sau khi chạy) |
| `F-V105-03` | `V107RemediationTest` ca 1–5; thêm các câu của riêng Reviewer (mẫu probe: "Không học hai tiếng nhưng làm việc hai tiếng", "Làm việc không nghỉ ba tiếng", "Không học vài tiếng", "Không học hay làm việc hai tiếng", "Tôi không học tiếng Anh hai tiếng") qua `AiProposalGuard.apply` |
| `V106-N1` | **Chạy lại probe gốc `ADV_REPLYGUARD` của Reviewer** (14 câu) trên `ReplyGuard.sanitize` (prop0/prop1) **và** qua `parseAndValidateResponseJson` + `WallyChatReducer.applyResult`; kỳ vọng: luồng có đề xuất ⇒ `reply == null`; chỉ-AI ⇒ hoặc bị chặn, hoặc thẻ có dòng "Chưa có gì được lưu…". `V107RemediationTest` ca 6–8, `V107Round2Test` |
| `V106-N2a` | `V107RemediationTest` ca 9–10: BOM trước `[1, 2]`/`null`/object/fence/usage |
| `V106-N2b/c` | `V107RemediationTest` ca 11–12; phiên 24h+500 ms ⇒ tổng 86.400 s (không còn 86.401 s); 48h+999 ms, 48h+1.500 ms |
| Không regression | 322 test baseline v1.0.6 vẫn PASS nguyên vẹn; golden 66/66, holdout 46/46 |

Kết quả kiểm thử trên **thiết bị/AI thật** phải được ghi cùng SHA-256 thực của APK đã cài trong `WALLY_V107_ACCEPTANCE_RECORD.md` (hồ sơ riêng), không ghi vào hồ sơ v1.0.6.
