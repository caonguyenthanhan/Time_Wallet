# WALLY QA REPORT — API-FIRST (thay thế quyết định Local-first)

Ngày: 2026-11-13 · Căn cứ: "OWNER DECISION UPDATE — WALLY API-FIRST" · Bản này **bổ sung và thay thế** các phần Local-first của `WALLY_QA_REPORT.md` (candidate v1.0.2, giữ nguyên để đối chiếu).

## 0. Kết luận

| Mức | Trạng thái |
|---|---|
| Toàn SPEC | **CANDIDATE_UNVERIFIED** (F6 vẫn chưa tái hiện; không có thiết bị) |
| API-FIRST: điều kiện bổ sung ở mức mock HTTP + JVM | **PASS** (chi tiết §4) |
| DEVICE_PASS | **NOT_TESTED** (quy tắc dự án cấm emulator/adb; không có thiết bị) |
| AI_LIVE_PASS | **NOT_TESTED** (không có credential/quyền gọi nhà cung cấp thật) |
| IMPLEMENTATION_PASS / SPEC_ACCEPTED | Không tuyên bố |

## 1. Artifact

| Mục | Giá trị |
|---|---|
| APK mới | `out/wally_candidate_apifirst/TimeWallet-MVP-v1.0.3-WALLY-APIFIRST-candidate-debug.apk` |
| SHA256 | `fd058632ec39a8f9184a07ce9c6a6a7808c17a7e5fe70e09787ad55dd598dcb2` |
| Package / version | `com.timewallet.mvp` · versionCode **4** · versionName **1.0.3** · targetSdk 35 (`aapt dump badging`) |
| Chữ ký | Debug keystore, chứng chỉ SHA-256 `402f1d41…2530d`, **trùng** v1.0.2 và các bản trước ⇒ cài đè được; versionCode 4 > 3. |
| Candidate v1.0.2 | Không đổi: `49a83f85…9e8c` (xem `ALL_APK_SHA256.txt`) |
| Room / schema | Không đổi (version 3, không migration) |
| Diff | `SOURCE_DIFF_vs_v1.0.2.patch`: 18 file, +1119/−234; `CHANGED_FILES_vs_v1.0.2.txt` (16), `NEW_FILES_vs_v1.0.2.txt` (3), `MANIFEST_SHA256_after.txt` |
| Snapshot rollback | `out/wally_apifirst_snapshot_pre/source_v1.0.2_pre_APIFIRST.tar` + `MANIFEST_SHA256.txt` (nguồn v1.0.2 trước khi sửa) |

Rollback: giải nén tar vào thư mục sạch rồi `sha256sum -c MANIFEST_SHA256.txt`, hoặc xóa 3 file trong `NEW_FILES_vs_v1.0.2.txt` rồi giải nén tar tại gốc dự án. Hạ cấp về APK v1.0.2 đòi hỏi gỡ app (versionCode thấp hơn): **xuất JSON trước**.

## 2. Hành vi mới (đối chiếu với từng yêu cầu của Owner)

| # | Yêu cầu | Thực hiện |
|---|---|---|
| 1 | Mọi tin nhắn NL đi qua Cloud AI, kể cả "Học 45p" | Bỏ hoàn toàn đường tắt cục bộ: `localFirst`, `ChatRouter`, trả lời lời chào cục bộ, fallback parser đã bị gỡ khỏi `analyzeInput` và ViewModel. |
| 2 | Giữ parser/validator cục bộ cho guardrail | Giữ ở vai trò kiểm tra: `AiProposalGuard`, `ReplyGuard`, `AiQueryValidator`, `CancelCommandParser`, `LedgerValidator`, `LedgerQueryEngine` (tính số). Không dùng để dựng câu trả lời thay AI. |
| 3 | Lỗi/offline: thông báo ngắn, giữ input, Thử lại hoặc Ghi thủ công; không chuyển âm thầm sang chatbot cục bộ | Mọi lỗi trả `fault` + **không đề xuất, không lời đáp**. Banner: "Wally chưa trả lời được", Thử lại (chỉ lỗi tạm thời), Mở cấu hình ⚙ (lỗi khóa/cấu hình), Ghi thủ công. Input chỉ bị xóa khi có đề xuất hoặc thẻ trả lời. |
| 4 | Manual/Timer/Journal/Budget/Dashboard offline | Không phụ thuộc khóa API (test tĩnh kiểm các hàm ViewModel tương ứng). |
| 5 | AI chỉ tạo proposal/trả lời; ghi phải qua validation + xác nhận | `GeminiProposalClient` không có đường tới DAO/Room (test tĩnh); `needsConfirmation` luôn true; test tích hợp: phân tích không ghi gì, xác nhận hai lần = một lần ghi. |
| 6 | Khóa do người dùng nhập, Keystore, không hardcode/log | Giữ nguyên từ bản trước; test quét không có khóa thật trong nguồn/test/gradle, không `Log.*`, diagnostics không chứa khóa. |
| 7 | Giữ tên tab Wally | Giữ. |
| 8 | F5, F6, integration tests | F5 đã sửa (bản trước, không đổi). F6 vẫn UNRESOLVED (§6). Thêm integration test mới. |

**Chưa có khóa / chưa bật AI:** tin nhắn bị chặn trước khi tới mạng; Wally hiện "Wally chưa được cấu hình AI" + nút Mở cấu hình ⚙ + Ghi thủ công. Không có phản hồi giả; chip chế độ ghi "Chưa cấu hình AI" (không còn "Cục bộ (100% Offline)").

**Lệnh điều khiển còn xử lý cục bộ (đúng yêu cầu 2):** "Hủy tất cả đề xuất", "Hủy ghi nhận 30 phút đọc sách"… (thao tác tất định trên draft chưa lưu; không gửi lên cloud, hoạt động cả khi chưa có khóa).

## 3. Thiết kế đầu ra của AI (prompt `wally-prompt-2`)

AI trả một JSON: `reply` (≤2 câu), `proposals`, `queries`.
- **Ghi hoạt động** → `proposals` (quy tắc cũ: không suy diễn giờ, 9 danh mục, ngày tương đối, 12h, bỏ mệnh đề tổng hợp, phủ định/hủy/sửa lại không tạo đề xuất).
- **Câu hỏi số liệu / so sánh** → `queries` (metric, khoảng ngày, danh mục, kỳ so sánh). **Ứng dụng tự tính số** bằng `LedgerQueryEngine` từ sổ cái; AI không được nêu số liệu. Lý do: tầng 03 của SPEC ("không bịa số liệu") và không gửi sổ cái lên cloud.
- **Ý định hỗn hợp** ("Học 45p và hôm nay tôi dùng bao nhiêu giờ?") → cả `proposals` lẫn `queries`; cả hai phần được xử lý, thẻ trả lời và đề xuất cùng hiện; không phần nào bị bỏ.
- **Câu nối tiếp** ("còn tuần trước thì sao?") → ViewModel truyền `QueryContext` của câu hỏi trước vào prompt.
- **Trò chuyện/lời chào** → `reply`. AI trả văn bản thường (không JSON) cũng được coi là lời đáp, không bao giờ là đề xuất. JSON bọc lời dẫn được bóc (đóng Finding E).

Guard tất định chạy sau AI: schema; ngày/khoảng ≤93 ngày/danh mục/metric; tối đa 3 query; lệnh hủy và câu sửa lại ⇒ không đề xuất; phủ định theo từng mệnh đề ("Không học 2h nhưng code 1h" giữ code 1h); đối chiếu thời lượng với câu người dùng (trừ khi câu dùng số bằng chữ); lời đáp có số liệu thời gian không có căn cứ bị bỏ.

## 4. Điều kiện đạt bổ sung

| Điều kiện | Trạng thái | Evidence |
|---|---|---|
| "Học 45p" thực sự gọi API, có request count | **PASS** (mock HTTP) | `WallyApiFirstContractTest."Hoc 45p really calls…"`: đúng 1 request, đề xuất là của AI (title/category do AI quyết). `WallyAiFaultInjectionTest."API-FIRST a trivially simple sentence…"` kiểm thêm nội dung gửi đi. **RED:** cùng test hợp đồng chạy trên snapshot v1.0.2: **5/5 FAIL** (`apifirst_contract_RED_on_v1.0.2.xml`); trên bản mới 5/5 PASS. |
| Câu hỏi phân tích + ý định hỗn hợp được AI xử lý đủ | **PASS** (mock) / live NOT_TESTED | AI trả proposal + query ⇒ cả hai tới được người dùng; số tính bởi engine, đối chiếu với `computeDayStats`; query sai bị bỏ có cảnh báo. **Không chứng minh** mô hình thật sinh query đúng. |
| Timeout/offline/401/429 không crash, không bản ghi sai | **PASS** (mock) | 34 test fault qua adapter thật: offline/DNS, TLS, timeout, 401/403/404, 429 (+Retry-After), 5xx, JSON/schema hỏng, body quá lớn, chuyển hướng; mọi lỗi cho kết quả không có đề xuất/lời đáp. |
| Không có khóa: hướng dẫn cấu hình, không giả AI | **PASS** (client + tĩnh) / UI NOT_TESTED | 0 kết nối, `NOT_CONFIGURED`, không đề xuất, thông báo nhắc khóa API. Hiển thị banner chưa quan sát trên thiết bị. |
| Ghi thủ công chạy khi không có khóa | **PASS** (JVM) | `WallyManualEntryTest` + test tĩnh `saveManualEntry` không chạm `aiPreferences`/`GeminiProposalClient`. |
| Không regression: hủy request, response muộn, đề xuất trùng, xác nhận | **PASS** (JVM) | Hủy giữa chừng đóng socket (25 vòng); epoch bỏ kết quả muộn; hai hoạt động giống nhau từ AI được ghi riêng, xác nhận hai lần = 2 dòng; `Ai01ClosureLifecycleTest` giữ nguyên trừ 1 test (xem §5). |
| Test cũ Local-first được cập nhật có chủ đích, không sửa tùy tiện test nghiệp vụ khác | **PASS** | §5. |
| AI_LIVE_PASS | **NOT_TESTED** | Cần credential Owner cho phép. |

## 5. Test

Cuối: **196/196 PASS** (0 lỗi, 0 skip). `out/wally_evidence_apifirst/final/xml/`. Golden 66/66, holdout 46/46 (tập holdout copy tạm). Trước đó (v1.0.2): 164.

| Suite | Số | Ghi chú |
|---|---|---|
| TimeWalletDomainTest | 25 | **sửa 2 assertion** (xem dưới) |
| Ai01IntegrityReproTest / FeedbackAndGuard / Negation / ClosureParser | 10 / 11 / 10 / 9 | không sửa |
| Ai01ConversationalAnalyticsTest | 11 | **sửa phần kiểm tra tĩnh ViewModel của 1 test**; golden 66 không đổi |
| Ai01ClosureLifecycleTest | 15 | **sửa 1 test** |
| Holdout harness | 2 | không sửa (46/46) |
| WallyApiFirstContractTest | 5 | MỚI, có RED trên v1.0.2 |
| WallyApiFirstGuardsTest | 13 | MỚI: guard, validator, answer builder, tích hợp AI→xác nhận→commit |
| WallyAiFaultInjectionTest | 34 | viết lại phần Local-first; thêm 13 test API-first |
| WallyChatLifecycleTest | 8 | sửa test lời chào |
| WallyBudgetTest 8, WallyDashboardSummaryTest 6, WallyDiagnosticsTest 4, WallyKeystoreFaultTest 4, WallyManualEntryTest 11, WallySecurityStaticTest 4, WallyUiStaticTest 6 | | không đổi |

**Test cũ bị sửa có chủ đích** (`OLD_TESTS_CHANGED_INTENTIONALLY.patch`, 3 file, +25/−19). Chỉ những khẳng định gắn với hành vi Local-first:
1. `TimeWalletDomainTest` F1/F2 (khóa Keystore): trước kỳ vọng URL không https / host lạ ⇒ *fallback cục bộ vẫn ra 1 đề xuất*. Nay: không kết nối + lỗi `CONFIG` + không đề xuất. **Thuộc tính bảo mật F2 (không gửi khóa sang host chưa xác nhận, bắt buộc https) giữ nguyên.**
2. `Ai01ConversationalAnalyticsTest` "zero network or database dependencies": phần quét hai file query giữ nguyên; phần kiểm tra "ViewModel định tuyến bằng ChatRouter trước `analyzeInput`" đổi thành "ViewModel không dùng ChatRouter; engine chỉ chạy sau khi AI trả kết quả".
3. `Ai01ClosureLifecycleTest` "query and clarify routing never touch the pending drafts": bất biến (câu trả lời không xóa draft) giữ, kiểm bằng cách mới (chỉ `mergeIfCurrent`, nhánh chưa cấu hình không đụng `proposals`).
Golden/holdout (bộ phân tích truy vấn) **không sửa expected**. Test tôi viết ở vòng trước (`Wally*`) được cập nhật vì chúng mô tả Local-first.

## 6. F5 / F6 / bảo mật

- **F5:** như báo cáo trước, giữ nguyên (đã sửa; kiểm tra tĩnh; chưa có ảnh chụp).
- **F6: UNRESOLVED / NOT_REPRODUCED.** Không đổi: vẫn chưa có stack trace. Đã có sẵn cơ chế ghi stack trace đã che nội dung (ⓘ → Nhật ký lỗi gần nhất). Hành vi API-first không chứng minh/loại trừ nguyên nhân F6. Thay đổi này **thêm** một nguồn lỗi có thể gặp ở thiết bị (mọi tin nhắn đi mạng) nhưng mọi đường lỗi mạng đã được test không sập.
- **Bảo mật:** khóa Keystore/`allowBackup=false`/che khóa như trước. **Thay đổi rủi ro riêng tư cần Owner biết:** khi bật AI, *mọi* câu nhập ở tab Wally (kể cả câu đơn giản) được gửi tới nhà cung cấp (mặc định `api.foza.ai`, bên thứ ba), kèm ngày/giờ/múi giờ và, với câu nối tiếp, mô tả (không phải số liệu) của câu hỏi trước. Sổ cái và số liệu thống kê không được gửi.

## 7. Giới hạn trung thực

- **Không có kiểm chứng với AI thật.** Toàn bộ là mock HTTP. Chất lượng suy luận (nhận ý định, sinh `queries` đúng, tuân thủ phủ định, phân loại "làm việc không nghỉ" là `work`) phụ thuộc mô hình và **chỉ được bảo vệ bằng prompt + guard**, không được chứng minh. F3 ("Làm việc không nghỉ 3 tiếng" → work) và F4 trên đường AI chỉ được guard một phần (guard bắt phủ định có thời lượng; danh mục là do AI).
- **Guard đối chiếu thời lượng có thể từ chối đề xuất hợp lệ** (ví dụ AI gộp hai mốc, hoặc câu dùng cách nói mà bộ đọc số cục bộ hiểu khác). Chọn thiên về an toàn; thấy lỗi dương tính trên điện thoại thì báo để chỉnh.
- **Một vòng AI:** AI yêu cầu số liệu nhưng không nhìn thấy kết quả để nhận xét thêm (không gửi sổ cái lên cloud). Câu như "tuần này tôi học nhiều hay ít hơn bình thường?" chỉ nhận số liệu và so sánh kỳ do engine tính, không có nhận định của AI.
- **Mọi tin nhắn tốn một lần gọi mạng:** độ trễ tối đa 30 giây (có nút Hủy), chi phí API, và phụ thuộc mạng. Không cache.
- `ChatRouter`/`QueryIntentParser` còn trong mã nguồn nhưng **không được ViewModel gọi**; giữ vì golden/holdout vẫn chạy trên nó. Có thể xóa khi Owner đồng ý (kèm golden/holdout).
- Giao diện mới (banner cấu hình, thẻ Wally (AI)) chưa được quan sát trên máy; chưa có test ViewModel/Compose thật.
- Mặc định Opt-in vẫn tắt: người dùng phải nhập khóa **và** bật công tắc. Chưa có khóa mặc định (không hardcode).
- Lời đáp của AI không có số liệu được kiểm chứng ngữ nghĩa ngoài ReplyGuard (chỉ bắt số có đơn vị thời gian, không bắt mọi khẳng định sai).
- Các hạn chế về thiết bị/screenshot/F6/Room thật của báo cáo trước vẫn giữ.
