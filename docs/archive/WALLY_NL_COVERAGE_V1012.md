# WALLY — Độ phủ kiểm thử ngôn ngữ tự nhiên (v1.0.12)

**Ngày:** 2026-11-15 · công khai phạm vi test hiện có, khoảng trống quan trọng, phần đã bổ sung, và phần CHƯA được chứng minh.

## 1. Cái mà JVM test đo được và không đo được
Kiến trúc là **API-first**: AI (Cloud) hiểu tiếng Việt; ứng dụng chỉ **kiểm tra** đầu ra (schema, thời lượng, khoảng giờ, phủ định/sửa/hủy), tính số từ sổ cái và **chỉ ghi sau xác nhận**. Vì vậy:
- Test trong cây mã kiểm tra **phần của ứng dụng** với phản hồi AI *giả lập* (đúng hoặc sai có chủ đích) đi qua đúng adapter `GeminiProposalClient.analyzeInput` (kết nối giả, không mạng).
- **Không test nào trong cây mã chứng minh model thật hiểu đúng.** Việc đó chỉ Live Eval (khóa API của Owner) chứng minh được — xem `WALLY_LIVE_EVAL_ADDENDUM_V1012.md`. Trạng thái: **CHƯA CHẠY (chờ Owner)**.
- Bộ phân tích cục bộ `VietnameseChatParser` vẫn tồn tại như *tham chiếu để đối chiếu* đầu ra AI (guard), không phải chatbot thay thế.

## 2. Test hiện có trước v1.0.12 (kiểm kê, số `@Test` đếm bằng `grep -c`)
| Mảng | Lớp test (số test) | Kiểm tra gì |
|---|---|---|
| Phân tích câu: thời lượng, nhiều hoạt động, số bằng chữ, mốc giờ | `Ai01ClosureParserTest` (9), `V107Round2Test` (7), `Ai01IntegrityReproTest` (10) | thời lượng `45p/2h/1 tiếng rưỡi`, khoảng `13:00–18:00`, "lúc 8h … 30p", tách nhiều mệnh đề, mệnh đề tổng hợp |
| Phủ định / sửa lại | `Ai01NegationFinalFixTest` (10), `V107RemediationTest` (12), `V109GuardContainmentTest` (16) | "không học 2h", "không nghỉ 3 tiếng", "không phải 2 tiếng, 3 tiếng", phủ định số bằng chữ |
| Hủy | `V106CancelByTitleTest` (7), `Ai01ClosureLifecycleTest` (15) | "Hủy tất cả đề xuất", hủy theo tiêu đề, không tạo bản ghi |
| Truy vấn / ngân sách | `Ai01ConversationalAnalyticsTest` (11), `V106BudgetQueryTest` (9), `WallyApiFirstGuardsTest` (13) | số liệu từ sổ cái, so sánh kỳ, ngân sách |
| Hợp đồng phản hồi AI | `V106ResponseContractTest` (20), `V106ReviewRound2Test` (13) | JSON chặt, kiểu trường, lời đáp giả "đã lưu" |
| Vòng đời đề xuất / xác nhận / ghi trùng | `WallyApiFirstGuardsTest`, `WallyChatLifecycleTest` (8), `V108TransactionStatusTest` (16) | chỉ ghi sau xác nhận, xác nhận lặp không ghi trùng |
| Lỗi mạng / adapter | `WallyAiFaultInjectionTest` (34), `WallyEightLayerTest` (16) | offline/TLS/timeout/401/404/429/5xx/hủy giữa chừng, cache prompt |
Cộng lại là **407 test trong cây mã trước v1.0.12** (golden 66, holdout 46 nằm trong số này).

## 3. Khoảng trống quan trọng đã tìm ra (và trạng thái)
| # | Khoảng trống | Bằng chứng | Trạng thái v1.0.12 |
|---|---|---|---|
| G1 | **Mốc giờ bị nhầm thành thời lượng** trong guard thời lượng: `từ 14h30`, `từ 8h sáng đến 11h`, `Ngày mai 9h họp 1 tiếng` bị đọc là 14,5 giờ / 8 giờ / 9 giờ ⇒ đề xuất ĐÚNG của AI bị bỏ ("không khớp với câu bạn nhập") | `probe_nl_clock_vs_duration.txt` (3 trong 16 câu hỏng trước khi sửa) + `Ngày mai 9h họp 1 tiếng` phát hiện qua test mới (đề xuất bị bỏ trước khi sửa) | **Đã sửa** (parser: từ/đến/tới + giờ là mốc; từ chỉ buổi giữa hai mốc; cụm có đơn vị được ưu tiên) + test |
| G2 | "đến giờ / đến bây giờ / đến nay" không có xử lý nào (prompt và ứng dụng) ⇒ phụ thuộc may rủi vào AI; guard còn loại đề xuất vì lý do G1 | câu của Owner | **Đã sửa**: prompt `wally-prompt-5` (quy tắc 7b) + guard tất định: giờ kết thúc = giờ hiện tại (lệch > 2 phút thì chỉnh; liên kết theo giờ bắt đầu, không lan sang hoạt động khác) |
| G3 | Câu nhiều hoạt động có "đến giờ": hoạt động còn lại bị loại hoặc bị chỉnh nhầm | `probe` (câu 10) | **Đã sửa** + test |
| G4 | Không có test nào chạy ngôn ngữ tự nhiên → đề xuất → xác nhận → sổ cái cho **khoảng giờ có mốc** qua đúng đường ghi | — | **Đã thêm** `V1012OwnerSentenceTest` (ghi 14:00–16:30 một lần, xác nhận lần hai không ghi thêm) |
| G5 | Lỗi khóa API bị gọi là "AI trả dữ liệu hỏng" | xem `WALLY_V1012_ROOT_CAUSE_P1.md` | **Đã sửa** |
| G6 | Chỉ nêu giờ bắt đầu ("Code từ 14h"): ứng dụng không có cách tất định để biết AI có tự bịa thời lượng hay không (văn bản không có thời lượng để đối chiếu) | — | **Còn hở:** chỉ có quy tắc prompt 7b bắt AI hỏi lại; ứng dụng vẫn bắt người dùng xác nhận đề xuất. Cần Live Eval H6 |
| G7 | Qua nửa đêm ("từ 23h đến giờ" lúc 01:00): đề xuất thuộc ngày hôm qua nên guard "đến giờ" không chỉnh | — | **Còn hở (ghi nhận):** để AI + người dùng xác nhận |
| G8 | Gõ không dấu ("den gio"), số bằng chữ trong khoảng giờ | — | **Còn hở:** guard "đến giờ" cần dấu; ghi nhận |
| G9 | Hiểu đúng của model thật (ý định, ngày tương đối, 12 giờ sáng/chiều, nhiều ý định) | — | **CHƯA ĐO:** thuộc Live Eval v1.2 + phụ lục |

## 4. Đã bổ sung ở v1.0.12 (theo ý định và dữ liệu nghiệp vụ, không chỉ câu trả lời hội thoại)
- `V1012NaturalLanguageTest` (9): khoảng giờ ở 8 cách viết, "đến giờ" ở 6 cách viết (AI đúng được giữ; AI lệch được chỉnh), không lan sang hoạt động khác, thời lượng đơn/nhiều/"rồi", thời lượng sai và mệnh đề tổng hợp bị loại, ngày tương đối và kế hoạch ngày mai (`isFuturePlanned`), phủ định/sửa lại/hủy không thành bản ghi (hủy không mở kết nối), chỉ thị ẩn không tắt được xác nhận, truy vấn/trò chuyện/làm rõ phân loại đúng ý định.
- `V1012OwnerSentenceTest` (10): **câu của Owner** (đề xuất đúng một bản ghi 14:00–16:30, vẫn cần xác nhận; ngữ cảnh gửi đi có giờ/ngày hiện tại và không có khóa; AI lệch giờ → đặt theo giờ hiện tại; dung sai 2 phút; mốc bắt đầu chưa tới → cảnh báo; hai khoảng giờ không bị chỉnh nhầm; chỉ nêu giờ bắt đầu → hỏi lại; **xác nhận → sổ cái đúng giờ bắt đầu/kết thúc/thời lượng, một lần**; prompt có quy tắc).
- `V1012OwnerRegressionTest` (3, chạy được trên cả hai cây): hồi quy bắt buộc cho câu của Owner — xem `WALLY_V1012_ROOT_CAUSE_P1.md`.
- `V1012AiReliabilityTest` (10) và `V1012NavigationContractTest` (4): độ tin cậy luồng AI và điều hướng.

## 5. Kết luận trung thực
Độ phủ phần ứng dụng cho các tình huống tiếng Việt thực tế (thời lượng, khoảng giờ, mốc tương đối "đến giờ", ngày, nhiều hoạt động, phủ định, sửa, hủy, truy vấn, kế hoạch) đã được mở rộng và sửa ba lỗi thật. **Độ hiểu của AI thật chưa được chứng minh**; không có tuyên bố `AI_LIVE_PASS`.
