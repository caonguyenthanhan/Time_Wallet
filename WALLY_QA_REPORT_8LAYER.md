# WALLY QA REPORT — Kiến trúc AI 8 tầng & hồ sơ kiểm tra trước khi Owner thử trên máy

Ngày: 2026-11-13 · Candidate mới: **v1.0.4** (v1.0.3, v1.0.2 giữ nguyên) · Verdict: **CANDIDATE_UNVERIFIED** (F6 chưa xác định, chưa có thiết bị, chưa có AI thật).

Nhãn: **IMPLEMENTED** = có mã; **VERIFIED(JVM/mock)** = có test chạy; **NOT_TESTED** = chưa chạy trên thiết bị hoặc API thật. Mock không thay cho live.

## 0. Artifact

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_8layer/TimeWallet-MVP-v1.0.4-WALLY-8LAYER-candidate-debug.apk` |
| SHA256 | `338ddc3426beff52a54f9d656cdf7bdb6df349deb04d88b7912dfe4d64611600` (đọc từ `APK_SHA256.txt`) |
| Package/version | `com.timewallet.mvp` · versionCode **5** · versionName **1.0.4** · targetSdk 35 (`aapt dump badging`) |
| Chữ ký | Debug keystore, chứng chỉ SHA-256 `402f1d41…2530d`, trùng v1.0.3 ⇒ cài đè được |
| Bản trước | v1.0.3 `fd058632…dcb2` và v1.0.2 `49a83f85…9e8c` không đổi (`ALL_APK_SHA256.txt`) |
| Room/schema | Không đổi (v3, không migration) |
| Diff | `SOURCE_DIFF_vs_v1.0.3.patch`: 14 file, +1027/−154 · `CHANGED_FILES_vs_v1.0.3.txt` (10) · `NEW_FILES_vs_v1.0.3.txt` (5) |
| Rollback | `out/wally_8layer_snapshot_pre/source_v1.0.3_pre_8LAYER.tar` + manifest. Nguồn v1.0.3 được dựng lại từ snapshot v1.0.2 + `SOURCE_DIFF_vs_v1.0.2.patch` và **khớp 74/74 file** với manifest v1.0.3 (tôi quên chụp tar trước khi sửa nên dựng lại; đã kiểm bằng hash). |

## 1. Test

**213/213 PASS** (0 lỗi, 0 skip) · `out/wally_evidence_8layer/final/xml/`. Baseline 196 giữ nguyên + 17 mới. Golden **66/66** (39 câu hỏi, 0 sai), holdout **46/46**.

Test cũ bị đổi trong đợt này: **chỉ 2 file do tôi viết ở vòng Wally trước** (`WallyApiFirstGuardsTest`, `WallyChatLifecycleTest`): hai kiểm tra *tĩnh* đọc chữ trong ViewModel nay đọc `WallyChatReducer.kt` vì logic đã được trích ra. Không đụng `Ai01*`, `TimeWalletDomainTest`, golden hay holdout (hash không đổi, `CHANGED_FILES_vs_v1.0.3.txt`).

| Suite mới | Số | Nội dung |
|---|---|---|
| `WallyEightLayerTest` | 16 | tiền tố prompt, usage/cache status, metrics, phân loại ý định, endpoint/model/khóa, reducer, độ mới số liệu, BatchConfirm, xóa nguyên tử |
| `WallyPromptPrefixContractTest` | 1 | chỉ dùng API có ở cả v1.0.3 và bản mới: **RED trên v1.0.3 (1 FAIL), GREEN trên bản mới** (`prompt_prefix_RED_on_v1.0.3.xml`) |

## 2. Hồ sơ kiểm tra theo chỉ thị Manager (mục 4 và 5)

| Yêu cầu | Trước đợt này | Xử lý | Trạng thái |
|---|---|---|---|
| **ViewModel integration** | THIẾU: ViewModel không dựng được trên JVM (cần Application, Room thật); chỉ có kiểm tra tĩnh | Trích hai khối logic thành hàm thuần dùng **chung** với ViewModel: `WallyChatReducer.applyResult` (áp kết quả AI lên state) và `BatchConfirm.run` (validate → commit, dùng cho cả Xác nhận và Nhập thủ công). Test chạy chính các hàm đó với `TimeWalletUiState` thật, kết quả `analyzeInput` thật (mock HTTP), `InMemoryLedgerDao`. | **VERIFIED(JVM)** cho state, reducer, commit. Vòng đời coroutine (`launch`, `cancel`, `invokeOnCompletion`) và Compose vẫn chỉ kiểm tĩnh: **NOT_TESTED** |
| **Atomic delete** | THIẾU: xóa bản ghi và xóa ghi chú là hai lệnh DAO rời | `deleteRecord` và `clearAllData` chạy trong `database.withTransaction { … }` (room-ktx đã có sẵn). | Mã **IMPLEMENTED**; test chỉ kiểm tĩnh (cả hai lệnh nằm trong giao dịch). Hành vi hoàn tác của Room: **NOT_TESTED** (không có Room thật trên JVM) |
| **Ý định hỗn hợp** | Có ở mức client (`WallyAiFaultInjectionTest`) | Thêm test reducer: kết quả MIXED cho cả draft lẫn thẻ trả lời, xóa input, đặt `lastQueryContext`; và phân loại `MIXED`/`RECORD`/`QUERY`/… | **VERIFIED(mock)**; AI thật: NOT_TESTED |
| **Endpoint/model** | Có ở mức nhỏ | Test: URL gửi đi = `https://host/…/chat/completions` (không nối đôi nếu đã có suffix), `model` trong payload = cấu hình, `Authorization: Bearer` chỉ ở header, không trong body, không redirect | **VERIFIED(mock)** |
| **Host allowlist** | Không phải danh sách cố định: Base URL do người dùng nhập, **khóa gắn với host** (`trustedHost`) | Giữ nguyên cơ chế; **siết thêm**: `extractValidatedHttpsHost` nay từ chối URL có query hoặc fragment (trước đây `…/v1?x=1` làm hỏng URL sau khi nối `/chat/completions`). Test: http/ftp/userinfo/query/fragment/chuỗi rác ⇒ lỗi `CONFIG`, **0 kết nối**; host khác host của khóa ⇒ 0 kết nối, không gửi khóa; so khớp host không phân biệt hoa thường. | **VERIFIED(JVM)**. Không có allowlist cố định ⇒ nếu Owner muốn chỉ cho phép đúng `api.foza.ai` thì cần quyết định thêm |
| **Bảo vệ khóa** | Keystore AES-GCM, `allowBackup=false`, quy tắc loại sharedpref/database, che `toString`, `keyUnavailable` | Không đổi; test bảo mật (`WallySecurityStaticTest` 4, `WallyKeystoreFaultTest` 4, `WallyDiagnosticsTest` 4) vẫn PASS; chẩn đoán mới không chứa khóa | **VERIFIED(JVM)**; Keystore thật: **NOT_TESTED** |
| Không gọi API thật | | Không gọi; mọi HTTP là `HttpURLConnection` giả | — |

## 3. Bảng 8 tầng

| Tầng | Mã thực thi | Evidence | Trạng thái |
|---|---|---|---|
| **01 Model & Serving** | `GeminiProposalClient`: `analyzeInput`, `postJson`, `testConnection`, `classify`, `httpFault`; `AiPreferences` (provider/model/baseUrl/trustedHost). Timeout 15s/25s, hạn tổng 30s, ≤1 retry, `Retry-After`, hủy đóng socket, trần 512KB, không redirect | `WallyAiFaultInjectionTest` 34, `WallyApiFirstContractTest` 5, endpoint/model trong `WallyEightLayerTest` | **IMPLEMENTED, VERIFIED(mock)**. Live NOT_TESTED. Chưa có streaming (không cần) |
| **02 Prompt & schema** | `staticSystemPrompt` (có phiên bản `wally-prompt-3`, băm tiền tố `b2c337d447b6`), `buildDynamicContext`, `buildUserMessage`; schema JSON `reply/proposals/queries`; `parseAndValidateResponseJson`; `AiQueryValidator` | Test tiền tố, hash, schema hỏng/injection (`WallyAiFaultInjectionTest`) | **PARTIAL**. Có phiên bản + schema + validator nghiệp vụ độc lập. **Chưa có bộ đánh giá prompt với mô hình thật**: golden 66/holdout 46 kiểm bộ phân tích cục bộ, KHÔNG kiểm prompt |
| **03 RAG & Knowledge** | `LedgerQueryEngine.execute` trên sổ cái trong RAM; AI chỉ yêu cầu truy vấn có phạm vi (`AiQueryValidator`: ≤93 ngày, danh mục, metric). Context gửi đi tối thiểu: ngày/giờ/múi giờ + mô tả (không phải số liệu) của câu hỏi trước. Không gửi sổ cái | `WallyApiFirstGuardsTest` (engine khớp `computeDayStats`, không dữ liệu ⇒ nói rõ), reducer + độ mới trong `WallyEightLayerTest` | **IMPLEMENTED, VERIFIED(JVM)**. Cố ý không có vector DB/RAG |
| **04 Tools & Orchestration** | AI chỉ ra `proposals`/`queries`. Ghi chỉ qua `BatchConfirm.run` (`validateBatchAtomic` → `commitBatchRecordsGuarded`) sau xác nhận. `ProposalLifecycle` (hủy, epoch), `confirmGuard`, `manualSaveGuard`, `WallyChatReducer` | `Ai01ClosureLifecycleTest` 15, `WallyEightLayerTest` (confirm, replay, hai hoạt động giống nhau, timer chặn) | **IMPLEMENTED, VERIFIED(JVM)**. Wiring coroutine/Compose: NOT_TESTED. Vòng công cụ hai lượt: **không triển khai** (§6) |
| **05 Identity & Authorization** | `AesGcmKeystoreVault`, `TimerAndSettingsStore` (khóa gắn host, `keyUnavailable`), `extractValidatedHttpsHost`, `allowBackup=false` + quy tắc loại sao lưu, chỉ quyền INTERNET; quyền ghi do code (cần xác nhận), không do prompt | `WallySecurityStaticTest`, `WallyKeystoreFaultTest`, `WallyEightLayerTest` (endpoint) | **IMPLEMENTED, VERIFIED(JVM)**. Keystore thật NOT_TESTED. Không có RBAC (không cần, ứng dụng một người dùng) |
| **06 Guardrails** | `AiProposalGuard` (hủy/sửa lại/phủ định/lệch thời lượng), `ReplyGuard`, `AiQueryValidator`, `LedgerValidator`, `CancelCommandParser`, `needsConfirmation` luôn true, chống injection ở vai trò: câu người dùng là *dữ liệu* | `WallyApiFirstGuardsTest` 13, injection/malformed trong `WallyAiFaultInjectionTest` | **IMPLEMENTED, VERIFIED(mock)**. Hiệu quả với mô hình thật NOT_TESTED |
| **07 Cache, Router, Cost** | Xem §4 và §5 | `WallyEightLayerTest`, `WallyPromptPrefixContractTest` | **PARTIAL** (xem bảng con) |
| **08 Observability & Release gates** | `AiDiagnostics` (sự kiện + số liệu cộng dồn), `AiUsageParser`, `CrashRecorder`, `requestId`; hộp thoại ⚙ hiển thị tổng hợp + sự kiện gần nhất, ⓘ hiển thị nhật ký lỗi. Gate = test + hash APK + manifest + diff + RED/GREEN | `WallyEightLayerTest` (metrics, riêng tư), `WallyDiagnosticsTest` | **IMPLEMENTED, VERIFIED(mock)**. Chỉ cục bộ (không telemetry từ xa, cố ý). Số liệu thật: NOT_TESTED |

### Tầng 07 chi tiết

| Thành phần | Trạng thái | Ghi chú |
|---|---|---|
| Router AI-first | **IMPLEMENTED** | Mọi câu NL gọi API (test xác nhận 1 request cho "Học 45p", câu hỏi, câu hỗn hợp, phủ định, chào hỏi). Lệnh điều khiển (hủy draft) cục bộ tất định, 0 kết nối. Chưa cấu hình ⇒ 0 kết nối. |
| Phân loại ý định | **IMPLEMENTED** | `WallyIntentClassifier`: RECORD, QUERY, MIXED, CHAT, CLARIFY, BLOCKED, CONTROL, NOT_CONFIGURED, ERROR (suy từ đầu ra đã qua guard; dùng cho số liệu quan sát, không dùng để bỏ qua API). |
| Định tuyến theo chi phí (model rẻ/đắt) | **NOT_IMPLEMENTED** | Chỉ có một model cấu hình; không có cơ sở đo để tách. |
| Prompt cache: cấu trúc tiền tố | **IMPLEMENTED, VERIFIED(JVM)** | Tiền tố tĩnh (luật, schema, ví dụ; 4.074 ký tự ≈ 5.043 byte) đứng đầu, **giống hệt từng byte** giữa mọi yêu cầu; ngày/giờ/múi giờ/câu hỏi trước/câu người dùng nằm ở tin nhắn user phía sau. Trước đợt này ngày và giờ nằm *đầu* system prompt nên tiền tố đổi mỗi phút (RED chứng minh). |
| Prompt cache: nhà cung cấp hỗ trợ | **UNVERIFIED** (không phải UNSUPPORTED) | Xem §4. |
| Prompt cache: cache hit thực tế | **NOT_TESTED** | Cần credential; không giả. |
| Data cache (kết quả truy vấn) | **NOT_IMPLEMENTED có chủ đích** | §5: không có lợi ích đo được. |
| Response cache | **Không có, có chủ đích** | Không dùng response cache để bỏ qua API; không replay response cũ. |

## 4. Prompt cache: hỗ trợ của nhà cung cấp

- **Nhà cung cấp/model cấu hình mặc định:** `https://api.foza.ai/v1`, model `cinb/gemini-3.8-flash`. Đây là cổng của bên thứ ba.
- **Tìm tài liệu công khai:** tôi tìm tài liệu của `api.foza.ai` về prompt caching: **không có kết quả nào nhắc tới tên miền này**. Do đó **không xác nhận được** cổng này có cache hay không, và không dám suy ra từ chuẩn tương thích.
- **Điều tài liệu bên ngoài cho biết (chỉ để tham khảo, không áp cho Foza):** API kiểu OpenAI báo cache hit ở `usage.prompt_tokens_details.cached_tokens`; Gemini gốc có *implicit caching* tự động theo tiền tố, báo ở `cachedContentTokenCount`, có ngưỡng token tối thiểu; một số cổng chuyển đổi trường này, một số cổng làm mất nó (cached_tokens luôn 0 dù có cache) hoặc không hưởng chiết khấu khi gọi qua định dạng tương thích. ⇒ Cổng tương thích **không** đồng nghĩa có cache.
- **Quyết định thiết kế:**
  1. Không gửi trường điều khiển cache riêng của nhà cung cấp nào (không biết cổng chấp nhận gì; trường lạ có thể gây lỗi 400). Test khẳng định payload chỉ có `model`, `temperature`, `messages`.
  2. Chỉ làm điều chắc chắn có lợi nếu cache tự động tồn tại: giữ tiền tố tĩnh, ổn định, có phiên bản và băm.
  3. **Phát hiện lúc chạy, không giả định:** `AiUsageParser` đọc `usage` của từng phản hồi. `HIT`/`MISS` chỉ khi nhà cung cấp *thật sự* trả token đệm; không có trường thì ghi `NOT_REPORTED` (không chứng minh là không hỗ trợ), không có `usage` thì `NO_USAGE`. Test: nhà cung cấp không báo usage ⇒ 0 HIT.
  4. Kích thước tiền tố ước lượng khoảng 1.600–2.700 token [GIẢ ĐỊNH, tiếng Việt có dấu thường 1,5–2,5 ký tự/token], nên có thể vượt ngưỡng tối thiểu thường gặp (khoảng 1.024); chưa đo bằng tokenizer thật.
- **Trạng thái cuối:** cấu trúc cache-friendly = **IMPLEMENTED/VERIFIED(JVM)**; hỗ trợ của nhà cung cấp = **UNVERIFIED**; cache hit = **NOT_TESTED**. Cách xác định: Owner chạy 2–3 yêu cầu liên tiếp, mở ⚙ → "Chẩn đoán" và đọc dòng `bộ nhớ đệm prompt: HIT/MISS/KHÔNG_BÁO` và `Token: … đệm=`.

## 5. Data cache: có lợi ích đo được không?

Đo độ trễ `LedgerQueryEngine.execute` (JVM máy phát triển, 30 lần/ô, `out/wally_evidence_8layer/query_benchmark.txt`; điện thoại sẽ chậm hơn):

| Quy mô | Kỳ 7 ngày + so sánh (p50 / p95) | Kỳ 93 ngày + so sánh (p50 / p95) |
|---|---|---|
| 279 bản ghi (điển hình: 3/ngày × 93 ngày) | 1,8 / 4,9 ms | 11,5 / 21,8 ms |
| 3.650 bản ghi (nặng: 10/ngày × 1 năm) | 5,8 / 9,9 ms | 95 / 179 ms |
| 21.900 bản ghi (cực đoan) | 73 / 133 ms | 447 / 718 ms |

**Quyết định: KHÔNG triển khai cache dữ liệu.** Lý do: (1) trường hợp điển hình ≤ 22 ms, không đáng; (2) câu hỏi do AI sinh nên hiếm khi lặp lại y hệt; (3) kết quả phụ thuộc "bây giờ" (bản ghi đang chạy/tương lai), timer và sổ cái nên khóa cache phải gồm phút hiện tại + phiên bản sổ cái + timer, tỷ lệ trúng gần 0; (4) cache sai là rủi ro số liệu cũ. Thiết kế dự phòng nếu sau này cần: khóa = (metric, khoảng ngày, danh mục, so sánh, múi giờ, phút hiện tại, phiên bản sổ cái, phiên bản ngân sách), vô hiệu khi `observeAllRecords`/ngân sách phát ra, không lưu câu chat hay khóa.

**Vấn đề thật được đo ra và đã sửa:** truy vấn nặng chạy trên luồng chính (180 ms trở lên trên dữ liệu lớn). Nay số liệu được tính trên `Dispatchers.Default` (ngoài luồng chính) và **tính lại ngay nếu sổ cái hoặc timer đã đổi** trong lúc chờ (so sánh tham chiếu danh sách). Test `reducer - numbers are recomputed from the CURRENT ledger…` và `fresh numbers after add edit and delete` chứng minh: thêm ⇒ số tăng, sửa ⇒ số đổi, xóa ⇒ số giảm, không bao giờ trả số cũ. Hiệu năng thật trên điện thoại: **NOT_TESTED**.

Không có cache ⇒ không có chuyện replay đề xuất đã hủy/đã xác nhận/phản hồi request cũ qua cache. Phần đó do epoch (`AnalysisEpoch`) và `idempotencyKey` bảo đảm và đã có test.

## 6. Vòng công cụ hai lượt: đánh giá, không triển khai

Hiện: một lượt (AI yêu cầu `queries`, ứng dụng tính số). Hai lượt nghĩa là gửi *kết quả số liệu* (tổng theo ngày/danh mục) lên cloud để AI nhận xét. **Không làm** vì: (1) vi phạm chính sách riêng tư hiện hành (sổ cái và số liệu không rời máy; hộp thoại ⚙ cam kết điều này); (2) nhân đôi độ trễ và chi phí cho mọi câu hỏi; (3) chưa có test/bằng chứng cho thấy câu hỏi nào của người dùng không giải quyết được bằng truy vấn + so sánh kỳ hiện có; (4) chỉ thị Manager: không phát triển AI multi-round trước khi kiểm tra candidate. Giới hạn đã biết: Wally không đưa nhận định phân tích (chỉ số liệu và so sánh do engine tính).

## 7. Metrics đo được (và chưa đo được)

`AiDiagnostics` ghi, chỉ trong RAM từ khi mở app, **không** có văn bản, prompt, khóa, phản hồi, sổ cái hay host: số yêu cầu cloud (mỗi lượt Wally = 1, thử lại tính riêng), lỗi theo lớp, số lần thử lại, độ trễ trung bình/tối đa, lệnh cục bộ, chưa cấu hình, token vào/ra/đệm **nếu** nhà cung cấp trả `usage`, trạng thái đệm (HIT/MISS/KHÔNG_BÁO/KHÔNG_USAGE), phân loại ý định, phiên bản prompt và băm tiền tố. Mỗi yêu cầu gần nhất có một dòng.

- **Đo được ở mức mock** (`WallyEightLayerTest`, dữ liệu tổng hợp): 3 lượt cloud, 1 lỗi AUTH, 1 lần thử lại, token vào 4.100/ra 60/đệm 1.800, 1 HIT, 1 KHÔNG_BÁO, 5 loại ý định, 1 lệnh cục bộ, 1 chưa cấu hình; chuỗi chẩn đoán không chứa khóa hay nội dung.
- **Đo thật (JVM):** độ trễ truy vấn (§5).
- **NOT_TESTED:** độ trễ/tokens/cache của nhà cung cấp thật, số liệu trên điện thoại.

## 8. Điều kiện đạt của Owner

| # | Điều kiện | Kết quả |
|---|---|---|
| 1 | 8 tầng có mapping + evidence | **PASS** (§3); tầng 02 PARTIAL, tầng 07 PARTIAL |
| 2 | "Học 45p" vẫn gọi API | **PASS** (mock): 1 request; test hợp đồng RED 5/5 trên v1.0.2 |
| 3 | Prompt cache có bằng chứng nhà cung cấp hỗ trợ và dùng, hoặc báo UNSUPPORTED | **Không thể đạt, báo UNVERIFIED**: không tìm thấy tài liệu của nhà cung cấp, không có credential. Không giả PASS |
| 4 | Sau thêm/sửa/xóa, truy vấn trả số mới | **PASS** (JVM) |
| 5 | Không duplicate proposal / replay | **PASS** (JVM): xác nhận lặp ⇒ 0 bản ghi thêm; hai hoạt động giống nhau từ hai đề xuất ⇒ ghi riêng |
| 6 | Test invalidation, lifecycle, privacy, regression tương ứng | **PASS** cho phần đã triển khai. Không có cache dữ liệu ⇒ không có test invalidation cache; thay bằng test độ mới |
| 7 | 196 test baseline, golden 66/66, holdout 46/46, không sửa expected | **PASS** (213 test, `Ai01*` và `TimeWalletDomainTest` không đổi) |
| 8 | Thiếu credential ⇒ AI_LIVE_PASS và cache hit NOT_TESTED | **NOT_TESTED** như yêu cầu |
| 9 | F6 giữ UNRESOLVED nếu chưa rõ nguyên nhân | **UNRESOLVED** |
| 10 | Build candidate riêng, giữ v1.0.3 | **PASS** (v1.0.4) |

## 9. Blocker còn lại và việc Owner cần làm

- **Credential cho AI thật** để có AI_LIVE_PASS, kiểm chứng prompt cache và số liệu độ trễ/token.
- **Thiết bị thật** (A13/A14/A17), F5 trên giao diện, F6: dùng `WALLY_OWNER_CHECKLIST_APIFIRST.md` và bổ sung: sau 2–3 câu liên tiếp mở ⚙ → "Chẩn đoán gần đây", chụp các dòng `Tổng hợp từ khi mở app`, `Token`, `bộ nhớ đệm prompt`, `Prompt: wally-prompt-3 (prefix=b2c337d447b6)`; dùng khóa thử và dữ liệu thử.
- **Quyết định của Owner:** có muốn giới hạn endpoint cố định (chỉ `api.foza.ai`) thay vì cho nhập host tùy ý (hiện khóa gắn với host đã lưu).
- **Giới hạn trung thực:** prompt chưa có bộ đánh giá với mô hình thật; xóa nguyên tử mới kiểm tĩnh; wiring coroutine/Compose chưa kiểm động; tokenizer thật chưa đo kích thước tiền tố; đường tin nhắn tới mạng nay luôn gọi API nên mỗi câu tốn một lượt mạng.
