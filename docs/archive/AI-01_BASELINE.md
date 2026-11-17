# AI-01 BASELINE — Time Wallet MVP v1.0.1

Phạm vi tài liệu: chỉ đọc source, không sửa code, không gọi API thật, không chạy build/test/emulator.
Áp dụng: `ai-architecture-handbook` v1.0, mode REVIEW + DESIGN.
Ngày: 2026-11-12. Đường dẫn gốc: `app/src/main/java/com/timewallet/mvp/` (viết tắt `…/`).

## 0. Nhãn bằng chứng

| Nhãn | Nghĩa trong tài liệu này |
|---|---|
| [ĐÃ XÁC MINH] | Đọc trực tiếp source trong phiên này (file + symbol nêu kèm) |
| [BÁO CÁO] | Owner báo cáo, chưa tái xác minh trong phiên này (25/25 PASS, BUILD_PASS) |
| [CHƯA ĐO] | Chưa có phép đo: latency, chất lượng parse, hiệu năng với dữ liệu lớn |
| [GIẢ ĐỊNH] | Suy luận thiết kế |

Số `@Test` trong `app/src/test/.../TimeWalletDomainTest.kt` = 25 [ĐÃ XÁC MINH bằng đếm]. Khớp con số 25/25 của Owner, nhưng kết quả PASS vẫn là [BÁO CÁO]. DEVICE_NOT_TESTED nghĩa là chưa có bằng chứng chạy trên thiết bị.

## 1. Kết luận ngắn

- Luồng **ghi nhận** (chat → đề xuất → xác nhận → ghi atomic) đã đầy đủ và an toàn: AI chỉ đề xuất, mọi ghi đều qua `LedgerValidator` và DAO transactional.
- Luồng **truy vấn / hỏi đáp / so sánh kỳ** chưa tồn tại. Chat hiện chỉ hiểu câu "ghi nhận". Một câu hỏi như "tuần này tôi học bao nhiêu?" đi vào parser ghi nhận và bị bỏ qua với cảnh báo "thiếu thời lượng".
- Phần tính toán số liệu (`LedgerAnalyticsCalculator`) đã tách bạch timed / untimed / planned, khử trùng khoảng giờ. AI-01 nên **tái sử dụng** phần này, không viết lại.
- Đề xuất: slice nhỏ nhất là **bộ trả lời truy vấn tất định (deterministic) trong tab Chat**, không đổi schema, không gọi LLM ở đường đọc. LLM chỉ là phương án bổ sung sau khi có số đo.

## 2. Năng lực thực tế theo thành phần [ĐÃ XÁC MINH]

| Thành phần | File / symbol | Làm được |
|---|---|---|
| AI Client | `data/ai/GeminiProposalClient.analyzeInput`, `buildSystemPrompt`, `parseAndValidateResponseJson` | OpenAI-compatible `/chat/completions`. Chỉ trích xuất `proposals` (≤15). Ép HTTPS, kiểm host tin cậy, fallback về parser cục bộ. Không có chế độ trả lời câu hỏi. |
| Chat (parser) | `domain/usecase/VietnameseChatParser.parse`, `classifyCategory`, `extractTimeRange`, `extractDurationMinutes` | Tách đa hoạt động theo `, ; \n và rồi sau đó tiếp theo`. Ngày: hôm nay / hôm qua / đêm qua / hôm kia / mai. Giờ: khoảng "13:00-18:00", "lúc 8h" + thời lượng. |
| Ledger | `domain/model/LedgerRecord`, `TimeLayer`, `TimeCategory` | 9 danh mục, layer MEASURED / REPORTED_TIMED / REPORTED_UNTIMED / ACTIVE / PLANNED. |
| Validator | `domain/usecase/LedgerValidator.validateAndBuildRecord`, `validateBatchAtomic` | Kiểm tra danh mục, ngày, trùng giờ, lệch thời lượng so với khoảng giờ (dòng 163), batch all-or-nothing. |
| Analytics | `domain/usecase/LedgerAnalyticsCalculator.computeDayStats`, `computeWeeklyInsights`, `computeBudgetProgress`, `resolveCurrentWeekBounds`; `DailyReviewAssistant.buildSnapshotForDate`, `generateFactualSummary` | Thống kê ngày theo danh mục; 7 ngày gần nhất; tiến độ ngân sách tuần (Thứ Hai–Chủ Nhật) hoặc ngày; tóm tắt sự thật trong ngày. |
| DAO | `data/local/LedgerDao` (`observeAllRecords`, `getAllRecordsSnapshot`, `commitRecordTransactionally`, `commitBatchRecordsTransactionally`, `findOverlappingEntity`) | Chỉ có "lấy tất cả" và ghi transactional. Không có truy vấn theo khoảng ngày hoặc danh mục. Room `version = 3`. |
| UI | `ui/screens/ChatScreen`, `ui/TimeWalletViewModel.analyzeChatInput`, `confirmProposalsList`, `TimeWalletUiState` | 5 tab (`AppTab`). Chat = ô nhập + chip ví dụ + thẻ đề xuất hàng loạt. Không có danh sách tin nhắn, không có vùng hiển thị câu trả lời. |

## 3. Capability matrix

| Năng lực | Trạng thái | Evidence | Ghi chú / gap |
|---|---|---|---|
| Multi-entry (nhiều hoạt động / câu) | **EXISTING** | Parser: `clauseConnectorRegex`, tách `[,;\n]`, giới hạn 15. Client: `minOf(arr.length(), 15)`. Test `refinement01 - single sentence with 5 activities…` | Comment đầu parser ghi "12", code cho 15 (lệch tài liệu, không ảnh hưởng chức năng). |
| Date parsing | **PARTIAL** | Parser xử lý hôm nay/qua/kia/mai, chỉ quét tiền tố 25 ký tự cho ngày toàn câu. Không có: thứ trong tuần, `dd/MM`, "tuần trước", "sáng/chiều/tối" (AM/PM). | `grep "thứ"` = 0 kết quả. "lúc 2h chiều" sẽ thành 02:00 [suy ra từ đọc regex, CHƯA ĐO]. Nhánh LLM có `today` trong prompt nhưng không có quy tắc ngày tương đối. |
| Category mapping | **PARTIAL** | Parser: `categoryRules`, match đầu tiên theo thứ tự, mặc định `other`. Client: `fromKeyOrDefault` đưa danh mục lạ về OTHER. Validator: từ chối key lạ. | Heuristic từ khóa (vd "chơi" → fun). Chưa có đo độ chính xác. Chưa có ánh xạ tùy chỉnh theo người dùng. |
| Write confirmation | **EXISTING** | `ActivityProposal.needsConfirmation = true`; `confirmProposalsList` → `validateBatchAtomic` → `commitBatchRecordsTransactionally`; idempotencyKey; `LedgerConcurrencyGate`. Test `refinement02 - batch validation…atomic`, `refinement03 - concurrent batch writes…` | Cần giữ nguyên, không xây lại. |
| Query (hỏi đáp dữ liệu) | **MISSING** | Không có intent parser nào; grep "bao nhiêu / so sánh / tuần trước" = 0 kết quả. | Câu hỏi hiện bị coi là câu ghi nhận lỗi. |
| Aggregation | **PARTIAL** | Có: theo ngày (`computeDayStats`), 7 ngày gần nhất (`computeWeeklyInsights`), ngân sách tuần/ngày. Thiếu: khoảng tùy ý, tuần trước, tháng, lọc theo danh mục trên kỳ bất kỳ. | Các hàm hiện có đủ làm nền; cần lớp cộng gộp mỏng theo khoảng ngày. |
| Period comparison | **MISSING** | `computeBudgetProgress` chỉ so với mục tiêu. Không có so sánh kỳ này với kỳ trước. | Có thể suy ra từ hai lần tổng hợp theo khoảng. |
| Report (văn bản) | **PARTIAL** | `DailyReviewAssistant.generateFactualSummary` (ngày), `buildNeutralBudgetReflection`, màn Insights/Daily Review. | Chưa có báo cáo văn bản theo yêu cầu trong chat (tuần/kỳ). |

## 4. Phần tái sử dụng, và so sánh deterministic với LLM

Tái sử dụng nguyên trạng [ĐÃ XÁC MINH]:

- `computeDayStats` và `DayLedgerStats`: nguồn sự thật cho timed / untimed / planned / theo danh mục.
- `computeWeeklyInsights`, `resolveCurrentWeekBounds`, `computeBudgetProgress`: khung tuần, ngân sách.
- `DailyReviewAssistant.generateFactualSummary`: câu tóm tắt trung tính từ dữ liệu thật.
- `LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds` / `formatCompactDurationSeconds`: định dạng.
- `TimeWalletUiState.records`: dữ liệu đã nằm sẵn trong bộ nhớ, không cần DAO mới.
- Toàn bộ luồng ghi: parser, `GeminiProposalClient`, validator, DAO, thẻ xác nhận.

| Tiêu chí | Deterministic (từ khóa + regex + hàm có sẵn) | LLM phân loại intent |
|---|---|---|
| Phủ câu hỏi | Tập đóng: tổng giờ, theo danh mục, so sánh hai kỳ, còn lại ngân sách, tóm tắt. Đủ cho Rolly-style cơ bản | Linh hoạt hơn với diễn đạt tự do [GIẢ ĐỊNH] |
| Độ đúng số liệu | Số lấy từ `LedgerAnalyticsCalculator`, tái lập được | Số vẫn phải tính cục bộ; LLM chỉ được chọn intent. Cho LLM tự tính thì sai số có thể xảy ra |
| Quyền riêng tư | Không gửi dữ liệu ra ngoài | Gửi câu hỏi tới host bên thứ ba (đã có cơ chế opt-in + HTTPS) |
| Chi phí / độ trễ | Không đáng kể, offline | [CHƯA ĐO] |
| Kiểm thử | Golden set tất định | Cần eval riêng, chưa có hạ tầng |

Quyết định (QUY TẮC THIẾT KẾ): deterministic trước theo nguyên tắc Non-AI First. LLM chỉ xem xét ở slice sau, khi golden set cho thấy tỉ lệ câu hỏi không nhận diện được vượt ngưỡng do Manager đặt [CHƯA ĐO].

## 5. Data flow

### 5.1 Hiện tại — ghi (đã có)

```
Chat input ─▶ GeminiProposalClient.analyzeInput
              ├─ opt-in + HTTPS + host tin cậy ─▶ LLM (proposals JSON) ─▶ parseAndValidateResponseJson
              └─ ngược lại / lỗi ─▶ VietnameseChatParser.parse
        ─▶ ActivityProposal (needsConfirmation=true) ─▶ EditableProposalDraft (người dùng sửa)
        ─▶ [Xác nhận] ─▶ LedgerValidator.validateBatchAtomic ─▶ LedgerDao.commitBatchRecordsTransactionally ─▶ Room v3
```

### 5.2 Hiện tại — đọc

```
Room ─▶ observeAllRecords ─▶ state.records ─▶ computeDayStats / computeWeeklyInsights / computeBudgetProgress
     ─▶ Overview / Insights / Daily Review (hiển thị, không hỏi đáp)
```

### 5.3 Đề xuất AI-01 — đọc, hỏi đáp (mới)

```
Chat input ─▶ [Router tất định] có thời lượng/khoảng giờ? ── có ─▶ luồng ghi 5.1 (không đổi)
                    │ không
                    ▼
        QueryIntentParser (mới, thuần Kotlin)
          intent = {metric, period, category?, compareTo?}
                    │ nhận diện được            │ không nhận diện được
                    ▼                           ▼
        LedgerQueryEngine (mới, mỏng)       Giữ hành vi cũ: cảnh báo
          cộng gộp computeDayStats          + gợi ý câu hỏi mẫu
          theo khoảng ngày trên state.records
                    ▼
        QueryAnswer(text, số liệu, ghi chú timed/untimed/planned)
                    ▼
        state.chatAnswer (tạm thời, không lưu Room) ─▶ thẻ trả lời trong ChatScreen
```

Ranh giới: đường đọc không ghi DB, không gọi mạng. Số liệu chỉ đến từ `LedgerAnalyticsCalculator`.

## 6. Implementation slice nhỏ nhất đề xuất cho AI-01

**AI-01-A "Hỏi đáp số liệu tất định"** gồm:

1. `QueryIntentParser` (file mới, `domain/usecase/`): nhận diện `metric` (tổng đã dùng / theo danh mục / chưa gắn giờ / dự kiến / còn lại ngân sách / tóm tắt), `period` (hôm nay, hôm qua, tuần này Thứ Hai–Chủ Nhật, tuần trước, 7 ngày gần nhất, ngày cụ thể), `category` tái dùng `VietnameseChatParser.classifyCategory`, `compareTo` ("so với tuần trước").
2. `LedgerQueryEngine` (file mới): cộng gộp `computeDayStats` theo khoảng ngày; so sánh kỳ = hai lần cộng gộp và hiệu số.
3. Hook trong `TimeWalletViewModel.analyzeChatInput`: nếu parser ghi nhận không ra đề xuất nào và `QueryIntentParser` nhận ra intent thì trả lời; ngược lại giữ hành vi hiện tại. Thêm `chatAnswer` vào `TimeWalletUiState` (tạm thời).
4. `ChatScreen`: một thẻ hiển thị câu trả lời và chip ví dụ cho câu hỏi.

Cải thiện nhỏ cho luồng ghi (tùy chọn, tách PR riêng, ưu tiên theo mức rủi ro):

- Sáng/chiều/tối (AM/PM) trong "lúc 2h chiều": ảnh hưởng đúng sai của dữ liệu ghi. Ưu tiên cao nhất trong nhóm này.
- Ngày theo thứ trong tuần và `dd/MM`.
- Bổ sung quy tắc ngày tương đối vào `buildSystemPrompt`.

Không đề xuất: đổi Room schema, thêm tab, thêm lịch sử chat, xây lại parser hay validator, multi-agent, RAG, semantic cache, gọi LLM ở đường đọc.

Tầng 8-layer liên quan: 02 Prompt (chỉ phần ghi), 06 Guardrails (đường đọc không có side effect), 08 Evals (golden set). Các tầng 03, 04, 05, 07 không áp dụng cho slice này (dữ liệu cục bộ một người dùng, không có RAG hay tool).

## 7. Độ phủ test hiện tại

Tất cả nằm trong `app/src/test/java/com/timewallet/mvp/TimeWalletDomainTest.kt`, 25 test [ĐÃ XÁC MINH bằng đọc tên và đếm]; kết quả PASS là [BÁO CÁO].

| Nhóm | Test (tên rút gọn) |
|---|---|
| Parser | duration-only không bịa giờ; 5 hoạt động / câu; 2am + 5h |
| Ledger / layer | ACTIVE 14:35 chỉ tính 1h35; PLANNED không tính; chống tính trùng; ACTIVE không tự hoàn tất |
| Analytics | 24h + 7 ngày tách untimed; untimed tách khỏi timed; ngân sách tuần 10h; tuần theo múi giờ |
| Persistence | Room migration 1→2, 1→2→3; batch atomic; ghi đồng thời |
| Khác | Timer + clock skew; dice engine; JSON export; Daily Review; patch F1–F5 (vault AES-GCM, HTTPS, reboot, control chars, mismatch) |

Không có: test ViewModel, test UI/Compose, thư mục `androidTest`, test cho ngày tương đối (hôm qua / mai), AM/PM, hay câu hỏi truy vấn. Không có đo chất lượng parser trên tập câu thực tế [CHƯA ĐO].

## 8. Gap

| # | Gap | Mức |
|---|---|---|
| G1 | Không có luồng truy vấn / so sánh / báo cáo trong chat | Mục tiêu AI-01 |
| G2 | Parser không xử lý AM/PM, thứ trong tuần, `dd/MM` | Ảnh hưởng đúng/sai dữ liệu ghi |
| G3 | Không có golden set / eval cho parser và danh mục | Chưa đo được chất lượng |
| G4 | Không có truy vấn DAO theo khoảng ngày; mọi phép tính chạy trên toàn bộ `state.records` | [CHƯA ĐO] ở quy mô lớn; chấp nhận được với MVP |
| G5 | Thư mục không phải git repo: không có cơ chế rollback sẵn có | Cần xử lý trước khi sửa code (xem mục 10) |
| G6 | Test chưa được chạy lại trong phiên này; chưa chạy trên thiết bị | [BÁO CÁO], DEVICE_NOT_TESTED |
| G7 | Tên `GeminiProposalClient` gây hiểu nhầm (client là OpenAI-compatible) | Thấp, không đổi trong AI-01 |

## 9. Definition of Done cho AI-01-A

Mỗi dòng PASS / FAIL / CHƯA ĐO cần có artifact kèm theo.

1. Golden set tối thiểu 30 câu hỏi tiếng Việt, gồm cả câu phủ định / mơ hồ (không được nhận nhầm thành intent). Tỉ lệ nhận diện đúng intent và kỳ đạt ngưỡng do Manager đặt (ngưỡng: CHƯA ĐẶT).
2. Đối soát: câu trả lời "hôm nay" khớp `computeDayStats(...).timedSeconds`; "tuần này" khớp tổng các ngày Thứ Hai–Chủ Nhật. PLANNED và untimed không cộng vào số "đã dùng"; hiển thị tách riêng.
3. So sánh kỳ: kỳ trước rỗng hiển thị "chưa có dữ liệu", không chia cho 0, không phần trăm vô nghĩa.
4. Hồi quy: 25 test hiện có vẫn PASS khi Owner / Master chạy; luồng ghi cho câu ghi nhận cho kết quả không đổi (thêm test snapshot các câu ghi nhận mẫu).
5. Đường đọc không ghi DB và không gọi mạng (kiểm chứng bằng test hoặc review code).
6. Không đổi: `versionName 1.0.1`, Room `version = 3`, 5 tab, không thêm migration.
7. Câu không nhận diện được: hành vi cũ cộng gợi ý câu hỏi mẫu, không hiển thị số bịa.
8. Kiểm thử thiết bị: giữ nhãn DEVICE_NOT_TESTED cho đến khi có bằng chứng.

## 10. Rollback

- Chỉ thêm 2 file mới (`QueryIntentParser`, `LedgerQueryEngine`) và 3 điểm chỉnh nhỏ: `TimeWalletViewModel.analyzeChatInput`, `TimeWalletUiState`, `ChatScreen`. Không migration, không đổi dữ liệu người dùng.
- Hoàn tác = xóa 2 file mới và khôi phục 3 file đã sửa. Hook ViewModel đặt sau một hằng `QUERY_ENABLED` để tắt nhanh mà không cần xóa.
- Vì thư mục không phải git repo (G5), **trước khi sửa** cần sao lưu 3 file sẽ chạm vào và giữ APK snapshot hiện có làm bản chuẩn. Việc này thuộc quyền của Master / Owner, tài liệu này không thực hiện.
- Dữ liệu người dùng không bị ảnh hưởng vì đường đọc không ghi.

## 11. Điều kiện xem xét lại

- Nếu golden set cho thấy deterministic không đạt ngưỡng: mở slice AI-01-B (LLM chỉ phân loại intent, số vẫn tính cục bộ, tái dùng cơ chế opt-in / HTTPS / host tin cậy của F1–F2).
- Nếu số bản ghi tăng đến mức độ trễ cộng gộp trong bộ nhớ đáng kể (cần đo): thêm truy vấn DAO theo khoảng ngày.
- Nếu Owner ưu tiên độ đúng khi ghi giờ: tách PR sửa AM/PM trước slice truy vấn.
