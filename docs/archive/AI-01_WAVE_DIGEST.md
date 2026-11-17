# AI-01 — WAVE DIGEST (Slice A → B)

Trạng thái: **CANDIDATE sẵn sàng để Reviewer kiểm định.** Chưa phải bản phát hành. **DEVICE_NOT_TESTED** (không cài APK, không chạy emulator, không gọi API thật).

Nhãn bằng chứng: [ĐÃ CHẠY] = tôi chạy trong phiên này và có file kết quả; [ĐỌC CODE] = xác minh bằng đọc; [CHƯA ĐO] = chưa có phép đo.

## 00. CLOSURE PATCH: vòng đời proposal (F1–F4)

Candidate v3: `out/ai01_candidate_v3_closure/TimeWallet-MVP-v1.0.1-AI01-CLOSURE-candidate-debug.apk`, SHA256 `730dc2699dc5df110bd2adfd7c7edee29b858ae26855646ac7a8859afde2752d`. v2 (`500e1585…4936`), candidate đầu (`a753da75…daa2`) và APK v1.0.1 (`bfe04960…1c03`) giữ nguyên, đã đối chiếu lại bằng `sha256sum` (`ALL_APK_SHA256.txt`). DEVICE_NOT_TESTED: không cài APK, không emulator/ADB, không gọi API thật.

| Finding | Kết quả | Cách sửa |
|---|---|---|
| F1 "Hủy ghi nhận 30 phút đọc sách" tạo proposal | **FIXED** | `CancelCommandParser` (file mới `ProposalLifecycle.kt`): lệnh hủy phải đứng đầu câu và theo sau là đối tượng thuộc về đề xuất (ghi nhận/đề xuất/nháp/tất cả...). Parser và đường cloud đều không tạo proposal cho lệnh hủy. "Hủy hợp đồng 2h", "Bỏ cuộc họp 1h", "Bỏ ghi chú 20 phút" vẫn là hoạt động. |
| F2 hủy proposal đang chờ | **FIXED** | ViewModel xử lý lệnh hủy trước router: 0 draft -> thông báo không có gì để hủy; 1 draft -> hủy đúng draft; nhiều draft -> hủy theo tham chiếu rõ (mô tả, thời lượng, "số N"), mơ hồ hoặc không khớp -> hỏi lại, không hủy gì; "hủy tất cả đề xuất" -> chỉ xóa draft chưa lưu. |
| F3 "Làm việc không nghỉ 3 tiếng" bị xếp rest | **FIXED** | Phân loại danh mục bỏ cụm "không nghỉ/ngừng..." khi phía trước đã có hoạt động. "Nghỉ ngơi 20 phút", "Nghỉ 1 tiếng", "Hôm nay tôi không nghỉ 2h" giữ nguyên nghĩa. |
| F4 "Không học 2h nhưng code 1h" | **FIXED (giữ đúng code 1h)** | Khi hai mệnh đề tương phản đều có thời lượng thì tách riêng: vế phủ định bị bỏ kèm cảnh báo, vế hợp lệ giữ. "Học 2h nhưng code 1h" nay ra 2 proposal (trước đây ra 1 proposal tiêu đề lẫn). Nếu vế hợp lệ không có thời lượng ("Không học 2h nhưng code") thì không ghi gì và có cảnh báo. |

**Bảo vệ state:**
- Câu hỏi số liệu/hỏi lại không đụng draft (nhánh route không chứa `proposals`, có test tĩnh).
- Hủy trong lúc phân tích: mỗi lượt phân tích mang một số thế hệ; hủy xác định làm tăng số thế hệ ngay trong cùng bước cập nhật state, kết quả về muộn bị bỏ, không tái tạo draft đã hủy. Hủy mơ hồ (hỏi lại) thì không bỏ lượt đang chạy. Cập nhật và gộp kết quả cùng nằm trong `_uiState.update` nên không có khoảng hở giữa hai bước.
- Nút "Tách đề xuất" trước đây bị khóa khi đang phân tích nên lệnh hủy không gửi được; nay nút chỉ mở khi nội dung là lệnh hủy (thay đổi UI duy nhất, `ChatScreen.kt`).
- Không đụng Ledger: nhánh hủy và `ProposalLifecycle.kt` không chứa `ledgerDao`, `commit`, `upsert`, `delete`, `settingsStore`, Room, mạng (test tĩnh). Không có chức năng xóa bản ghi đã lưu bằng chat; thông báo luôn nói "Bản ghi đã lưu không bị thay đổi".

**Evidence** (`out/ai01_evidence/closure/`):
| Bước | Kết quả |
|---|---|
| RED: `Ai01ClosureParserTest` (9 test) trên hành vi cũ (stub tạm F1/F3/F4) | **5 FAIL, 4 PASS** (4 PASS là test bảo toàn câu hợp lệ). `closure_parser_RED_before_fix.xml` |
| RED lifecycle | Không có RED chạy được: các hàm hủy/epoch là API mới, trước đó không tồn tại (test không biên dịch trên code cũ). Đây là điểm khác với F1/F3/F4. |
| GREEN toàn bộ | 93/93 PASS gồm holdout: 25 cũ + 32 Slice A/B + 10 negation + 9 parser closure + 15 lifecycle + 2 holdout. 0 lỗi, 0 skip. Golden 66/66 (39 câu hỏi, 0 sai), holdout 46/46. Không sửa expected cũ. `after_main/` |
| Build | `assembleDebug` OK. Room v3, 5 tab, version 1.0.1/2, gradle và manifest không đổi (hash khớp snapshot). Không thêm thư viện. |
| Diff | `SOURCE_DIFF_vs_v1.0.1.patch`: 17 file, +2641/−34 so với v1.0.1. So với v2 chỉ đổi 6 file: `ProposalLifecycle.kt` (mới), `VietnameseChatParser.kt`, `TimeWalletViewModel.kt`, `ChatScreen.kt`, 2 file test mới (`MANIFEST_DELTA_vs_v2.txt`). `GeminiProposalClient.kt` không đổi. |

**Giới hạn trung thực:**
- Phần nối ViewModel và UI chỉ được kiểm bằng biên dịch và kiểm tra tĩnh; test lifecycle chạy chính các hàm thuần mà ViewModel gọi, trên MutableStateFlow, không phải ViewModel thật. Chưa có test Compose/ViewModel, chưa chạy trên thiết bị.
- Hủy trong lúc phân tích luôn bỏ cả kết quả đang chạy khi hủy xác định: kết quả hợp lệ không liên quan đang về cùng lúc cũng bị bỏ (người dùng nhập lại). Chọn an toàn: không có draft cũ sau khi hủy.
- Nhận diện lệnh hủy là quy tắc theo vị trí và danh sách từ đối tượng; cách nói khác ("thôi đừng lưu cái đó") không được nhận là lệnh hủy.
- F3 chỉ sửa đường offline. Danh mục do AI cloud trả về vẫn do AI quyết định.
- Test do tôi viết; Reviewer nên đưa thêm câu hủy/phủ định riêng.

**Rollback v3:** `out/ai01_candidate_v3_closure/NEW_FILES_vs_v1.0.1.txt` liệt kê 10 file mới (mục 6 bên dưới có 7 file là danh sách của v2, dùng danh sách v3 này). Làm theo Cách A hoặc B ở mục 6 với danh sách file mới của v3. Quay về v2 bằng cách khôi phục 4 file đổi theo `MANIFEST_DELTA_vs_v2.txt` hoặc cài lại APK v2.

## 0. FINAL FIX — Ngữ nghĩa phủ định (bổ sung sau Independent Verification)

Candidate mới: `out/ai01_candidate_v2_negation/TimeWallet-MVP-v1.0.1-AI01-FINALFIX-candidate-debug.apk`, SHA256 `500e1585e7e845214c695378e37d743a1b25893c43038c429b1cebdfb5b64936`. Candidate cũ (`a753da75…daa2`) và APK v1.0.1 (`bfe04960…1c03`) giữ nguyên, đã đối chiếu lại bằng `sha256sum`. DEVICE_NOT_TESTED.

**Lỗi:** câu phủ định có thời lượng tạo proposal ngược nghĩa ("Hôm nay tôi không học 2 tiếng" -> "Không học", 120p). Không tự ghi DB (vẫn cần người dùng xác nhận) nhưng toast báo tách thành công.

**Sửa tối thiểu** (2 file production, không đổi kiến trúc, không thêm thư viện):
- `VietnameseChatParser`: một đoạn bị bỏ qua khi từ phủ định (không/chưa/chẳng/đừng/chớ) đứng **trước** thời lượng trong cùng mệnh đề. Có cảnh báo kèm theo nên toast không báo "Đã tách". Mệnh đề tách bằng "nhưng / tuy nhiên / song"; "Không học nhưng code 2h" chỉ lấy vế "code 2h" làm tiêu đề và danh mục (trước đây tiêu đề mang vế phủ định, danh mục sai thành `learn`).
- Không chặn theo từ khóa toàn câu: phủ định đứng **sau** thời lượng ("Học 2h không nghỉ", "Học 2h chưa ghi vào sổ", "Họp 2h không ghi biên bản") giữ nguyên. Cụm "không nghỉ / không ngừng / không ngắt quãng" chỉ được coi là cách làm liên tục khi phía trước đã có hoạt động: "Làm việc không nghỉ 3h", "Hôm nay tôi học không nghỉ 2h" vẫn tạo proposal; "Hôm nay tôi không nghỉ 2h" (không nghỉ 2 tiếng) bị bỏ qua.
- Câu sửa lại thời lượng ("Không phải 2 tiếng, học 3 tiếng", "Học 2h, à không, 3h", "Sửa lại thành học 3h", "Học 2h chứ không phải 3h") -> hỏi lại, không tạo proposal.
- `GeminiProposalClient`: câu phủ định/sửa lại **không gửi lên cloud** (xử lý offline ngay đầu `analyzeInput`); `parseAndValidateResponseJson` cũng bỏ toàn bộ đề xuất của AI cho câu đó (phòng thủ chiều sâu). Mọi nhánh fallback (opt-in tắt, URL không HTTPS, host không khớp, lỗi mạng, JSON rỗng) đều đi qua `VietnameseChatParser.parse` đã có guard, nên không bỏ qua guard.

**Evidence RED -> GREEN** (`out/ai01_evidence/final_fix/`):
| Bước | Kết quả | File |
|---|---|---|
| RED: `Ai01NegationFinalFixTest` (10 test) chạy trên hành vi cũ | **8 FAIL / 2 PASS**. 2 PASS là hai test bảo toàn (câu hợp lệ không bị chặn), đúng kỳ vọng | `negation_RED_before_fix.xml` |
| GREEN: toàn bộ test | 67/67 PASS (25 cũ + 32 Slice A/B + 10 mới), 0 lỗi, 0 skip. Với holdout: 69 test PASS | `after_main/` |
| GREEN: thêm holdout 46 của Reviewer | `Ai01IndependentVerificationHarnessTest` PASS, intent 46/46 = 100% | `after_main/`, `green_with_holdout_console.txt` |
| Golden 66 | PASS, intent 66/66, 39 câu hỏi 0 sai. **Không sửa expected cũ.** | `after_main/` |

Cách chạy RED: tôi vô hiệu hóa tạm ba hàm guard trong parser bằng stub, chạy test, rồi khôi phục bản đã sửa (đã kiểm tra không còn dấu `RED-STUB`). Đây là mô phỏng hành vi cũ, không phải bản build cũ nguyên văn; hai thay đổi tách khỏi guard (tiêu đề theo mệnh đề, cloud short-circuit) cũng bị tắt trong RED vì chúng phụ thuộc cùng guard.

Holdout 46 chỉ được copy tạm vào cây test để chạy rồi gỡ ra; bản gốc nằm ở `out/ai01_evidence/independent_verification/`. Cây nguồn của candidate không chứa file đó.

**Giới hạn còn lại (trung thực):**
- Phạm vi phủ định là heuristic theo vị trí (từ phủ định đứng trước thời lượng trong cùng mệnh đề), chưa phải phân tích cú pháp. Cách nói lạ có thể bị bỏ qua kèm cảnh báo hoặc lọt qua; chọn lệch về an toàn vì proposal vẫn cần người dùng xác nhận.
- Câu có từ như "ý là", "nhầm" cùng thời lượng sẽ bị hỏi lại dù không phải sửa lại; cũng lệch về an toàn.
- "Không học 2h và code 1h" tách theo "và": vế học bị bỏ, vế code vẫn tạo proposal để người dùng xem trước và xác nhận.
- Chưa có test ViewModel/Compose; toast kiểm bằng `ChatAnalysisFeedback`.
- Tập kiểm của test mới do tôi viết; Reviewer nên bổ sung câu phủ định riêng.

## 1. Kết quả kiểm thử của candidate đầu [ĐÃ CHẠY, `./gradlew testDebugUnitTest --offline`]

| Thời điểm | Kết quả | File |
|---|---|---|
| Trước khi sửa (baseline) | 25/25 PASS | `out/ai01_evidence/baseline_tests_before.xml` |
| Test tái hiện A–D, AM/PM, F trên code CHƯA sửa | 10 test, **6 FAIL** (C×2, D, AM/PM×3), F-unguarded PASS = bằng chứng race | `out/ai01_evidence/repro_RED_before_fix.xml` |
| Sau Slice A | 46/46 PASS (25 cũ + 21 mới) | `out/ai01_evidence/sliceA_after/` |
| Cuối (A + B) | **57/57 PASS**, 0 lỗi, 0 skip | `out/ai01_evidence/final_after/` |

Chi tiết 57: `TimeWalletDomainTest` 25 (cũ, không sửa), `Ai01IntegrityReproTest` 10, `Ai01FeedbackAndGuardTest` 11, `Ai01ConversationalAnalyticsTest` 11 (32 test mới).

Golden set (`Ai01ConversationalAnalyticsTest`), in ra trong stdout của file XML:

- 66 câu: 39 câu hỏi được chấp nhận (gồm 5 câu nối tiếp và 6 câu so sánh kỳ), 12 câu phải hỏi lại, 15 câu ghi nhận / phủ định phải đi vào luồng ghi.
- Intent đúng: **66/66 = 100%** (ngưỡng ≥95%).
- Câu hỏi được chấp nhận: 39, **sai kỳ/danh mục/số liệu: 0** (ngưỡng 100%). Số liệu kỳ vọng được tính tay từ fixture, không lấy từ engine.

## 2. Slice A — Data Integrity Hardening

| Finding | Kết luận | Sửa | Test |
|---|---|---|---|
| A cảnh báo bị nuốt | Xác nhận [ĐỌC CODE + test] | `ChatAnalysisFeedback.toastMessage`, VM dùng cho mọi nhánh | 4 |
| B re-entrancy | **Xác nhận một phần**: ViewModel không chặn; nút UI đã `enabled = !isAnalyzing` nên rủi ro thực tế thấp hơn báo cáo review | `ChatAnalysisGuard` (AtomicBoolean) + `finally` nhả khóa | 2 (gồm 50 caller đồng thời) |
| C cắt cụt `take(25)` | Xác nhận [test RED] | Tiền tố ngữ cảnh = phần trước dấu `:` đầu tiên (bỏ qua `:` trong giờ), không có thì dùng 25 ký tự như cũ | 3 |
| D mệnh đề tổng hợp | Xác nhận [test RED] | Bỏ qua "tổng cộng / tổng thời gian / cộng lại / cả ngày hết…" (parser + lọc nhánh AI + prompt); KHÔNG chặn "họp tổng kết" | 4 |
| AM/PM | Xác nhận [test RED]: "lúc 2h chiều" → 02:00 | `shiftHourForPeriod` cho sáng/trưa/chiều/tối/đêm; "đêm qua" vẫn là mốc ngày | 4 |
| F race timer | **Xác nhận ở tầng DAO** [test]: lô đã validate vẫn ghi được đè timer bật sau đó | `commitBatchRecordsGuarded` chạy guard trong mutex, đọc lại Timer từ `settingsStore` ngay trước khi ghi; VM `confirmProposalsList` dùng nó | 3 |
| E JSON bọc lời dẫn | **DEFER** theo chỉ đạo | Không đổi | — |

Hạn chế F: Timer bắt đầu không đi qua mutex ledger, nên vẫn còn khoảng hở cực ngắn giữa guard và lệnh ghi. Đã thu hẹp từ "cả quãng validate→commit" xuống "ngay trước ghi", chưa xóa hoàn toàn. Hậu quả còn lại thấp: `stopAndSaveTimer` vẫn từ chối khi trùng.

## 3. Slice B — Conversational Analytics

- `QueryIntentParser` + `ChatRouter` (`domain/usecase/QueryIntentParser.kt`): quy tắc tất định, định tuyến **trước** parser ghi nhận.
- `LedgerQueryEngine` (`domain/usecase/LedgerQueryEngine.kt`): cộng `LedgerAnalyticsCalculator.computeDayStats` theo từng ngày của kỳ; không viết lại thuật toán tính.
- Hỗ trợ: hôm nay/qua/kia, tuần này/trước, tháng này/trước, N ngày qua (≤93), ngày cụ thể (dd/MM, ISO); danh mục; chỉ số tổng, chưa gắn giờ, dự kiến, nhiều/ít nhất, tổng kết theo danh mục; so sánh kỳ; câu hỏi nối tiếp bằng `lastQueryContext` (RAM).
- Câu mơ hồ, câu hỗn hợp (vừa ghi vừa hỏi), kỳ >93 ngày, ngày sai, hỏi theo thứ / năm / ngân sách: **hỏi lại**, trả `Clarify`, không rơi vào luồng ghi, không ghi gì.
- UI: thẻ `ChatAnswerCard` trong tab Chat (vẫn 5 tab), 2 chip câu hỏi mẫu. `chatAnswer` và `lastQueryContext` chỉ ở `TimeWalletUiState`, không lưu Room.
- Công tắc: `ChatRouter.QUERY_ENABLED` (`const val`). `false` => mọi câu đi vào luồng ghi như v1.0.1 (có test).
- Offline / không ghi DB: test tĩnh `query code has zero network or database dependencies` quét hai file nguồn tìm `java.net`, `HttpURLConnection`, `GeminiProposalClient`, `data.local`, `androidx.room`, `LedgerDao`, `commit*`, `upsert`…; và kiểm tra trong ViewModel, nhánh định tuyến nằm trước `tryAcquire()`/`GeminiProposalClient.analyzeInput` và không chứa `ledgerDao`/`commit`. Đây là kiểm tra cấu trúc, không phải theo dõi runtime.

## 4. Bất biến [ĐÃ CHẠY / ĐỌC CODE]

`versionName 1.0.1` / `versionCode 2`, Room `version = 3`, không migration mới, `AndroidManifest.xml`, `app/build.gradle.kts`, `libs.versions.toml` không đổi, `AppTab` vẫn 5 mục. Không thêm dependency.

## 5. Artifact và diff

| Mục | Giá trị |
|---|---|
| APK v1.0.1 gốc `TimeWallet-MVP-v1.0.1-debug.apk` | SHA256 `bfe0496959a027af32993d37d382e7c80a06c3c3c0311a208fcfcf3a762e1c03`, **không đổi** (so với manifest trước khi sửa) |
| APK candidate `out/ai01_candidate/TimeWallet-MVP-v1.0.1-AI01-candidate-debug.apk` | SHA256 `a753da75002db309a09ffaf27f8104e4602499fe12324bd0170bf353c040daa2`, `assembleDebug` BUILD OK |
| Source snapshot trước | `out/ai01_snapshot_pre/source_v1.0.1_pre_AI01.tar` + `MANIFEST_SHA256.txt` (46 dòng) |
| Source sau | `out/ai01_candidate/MANIFEST_SHA256_after.txt` |
| Diff candidate đầu | `out/ai01_candidate/SOURCE_DIFF.patch`: 13 file, +1734 / −18 |
| Diff candidate FINALFIX | `out/ai01_candidate_v2_negation/SOURCE_DIFF_vs_v1.0.1.patch`: 14 file, +2017 / −24 so với v1.0.1; `MANIFEST_SHA256_after.txt`, `MANIFEST_DELTA_vs_candidate1.txt` (chỉ 3 file đổi so với candidate đầu: `VietnameseChatParser.kt`, `GeminiProposalClient.kt`, `Ai01NegationFinalFixTest.kt` mới), `APK_SHA256.txt`, `ALL_APK_SHA256.txt` |

Lưu ý quy trình: lần đầu tôi ghi file bằng script Python trên Windows làm 7 file chuyển từ LF sang CRLF, nên diff phình lên 7.030 dòng. Đã chuẩn hóa lại LF, chạy lại toàn bộ test và build lại trước khi lấy hash/diff ở trên.

## 6. Rollback

Nguồn gốc v1.0.1 nằm ở `out/ai01_snapshot_pre/source_v1.0.1_pre_AI01.tar` (nguồn `app/src`, file gradle và `proguard-rules.pro` của v1.0.1; **không** chứa file mới của AI-01), kèm `MANIFEST_SHA256.txt`. Chỉ `tar -xf` đè lên cây hiện tại là **không đủ**: nó khôi phục file đã sửa nhưng để sót các file mới (xem `NEW_FILES_vs_v1.0.1.txt` của candidate tương ứng: 7 file ở v2, 10 file ở v3), nên cây thành trạng thái lai.

**Cách A (khuyến nghị, không đụng cây hiện tại): restore vào thư mục sạch**
1. `mkdir ../TimeWallet_v1.0.1_restore && tar -xf out/ai01_snapshot_pre/source_v1.0.1_pre_AI01.tar -C ../TimeWallet_v1.0.1_restore`
2. Đối chiếu: `cd ../TimeWallet_v1.0.1_restore && sha256sum -c <đường dẫn>/MANIFEST_SHA256.txt` (hai dòng APK trong manifest sẽ báo thiếu file vì APK không nằm trong tar; đó là bình thường).
Thư mục restore chứa nguồn và file gradle của v1.0.1 (không có `gradlew`/`local.properties`; lấy từ cây hiện tại nếu cần build). Tôi đã chạy bước 1 và 2 trên thư mục tạm: 44 file nguồn OK, 0 file AI-01 xuất hiện, 2 mục APK báo thiếu như dự kiến.

**Cách B (rollback tại chỗ): xóa file mới theo manifest rồi restore**
1. Xóa đúng các file trong `NEW_FILES_vs_v1.0.1.txt` của candidate đang dùng (danh sách v2): `ChatAnalysisSupport.kt`, `LedgerQueryEngine.kt`, `QueryIntentParser.kt`, `Ai01ConversationalAnalyticsTest.kt`, `Ai01FeedbackAndGuardTest.kt`, `Ai01IntegrityReproTest.kt`, `Ai01NegationFinalFixTest.kt`. Với v3 thêm `ProposalLifecycle.kt`, `Ai01ClosureParserTest.kt`, `Ai01ClosureLifecycleTest.kt`.
2. `tar -xf out/ai01_snapshot_pre/source_v1.0.1_pre_AI01.tar` tại thư mục gốc dự án.
3. `sha256sum -c out/ai01_snapshot_pre/MANIFEST_SHA256.txt` và xác nhận không còn file nào trong danh sách ở bước 1.

**Rollback từng phần:** tắt Slice B bằng `QUERY_ENABLED = false` trong `QueryIntentParser.kt`. Guard phủ định không có công tắc riêng; gỡ nó bằng diff `MANIFEST_DELTA_vs_candidate1.txt` (3 file) hoặc quay về candidate đầu.

**Artifact:** APK v1.0.1 và candidate đầu còn nguyên, cài lại được. Không có migration, không đụng DB, nên rollback không ảnh hưởng dữ liệu người dùng.

## 7. Giới hạn trung thực để Reviewer kiểm tra

- **Golden set do chính tôi viết cùng lúc với parser**, nên 100% không chứng minh độ phủ trên cách diễn đạt ngoài tập. Tôi thử thêm 18 câu chưa dùng khi chỉnh: 13 ra intent hợp lý, 3 hỏi lại đúng, 1 ("cho mình xem tuần này") lọt vào luồng ghi, đã sửa và thêm vào golden. Các câu đó không nằm trong tập chấm điểm độc lập. **Đề nghị Reviewer đưa bộ câu riêng.**
- Câu như "hôm nay làm được gì?" được trả như tổng theo kỳ, chưa liệt kê từng hoạt động.
- "Còn tuần trước thì sao?" khi chưa có câu hỏi trước được trả lời như tổng tuần trước (không hỏi lại).
- Hiệu năng cộng gộp với dữ liệu lớn [CHƯA ĐO]. Mỗi kỳ ≤93 ngày gọi `computeDayStats` từng ngày trên toàn bộ danh sách.
- Hành vi trên thiết bị thật, giao diện thẻ trả lời trên màn 390px, kiểm tra tương tác chạm: [CHƯA ĐO], DEVICE_NOT_TESTED.
- Không có test Compose/ViewModel; phần nối ViewModel và UI chỉ được kiểm bằng biên dịch + kiểm tra tĩnh.
- Số liệu "đã dùng" tuân theo quy tắc hiện có của `computeDayStats` (bản ghi ACTIVE quá giờ chưa xác nhận không được cộng).
