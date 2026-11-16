# WALLY v1.0.12 — So sánh chất lượng 5 model LLM trên cùng bộ đánh giá

**Ngày:** 2026-11-16 · **Single Writer:** AI-Master · **Quyền:** Manager Directive FINAL LIVE EVAL & MODEL BENCHMARK + Owner Authorization WALLY LIVE API QA
**Nhà cung cấp:** `https://api.foza.ai/v1` (cùng một khóa QA, cùng gateway). **Dữ liệu:** synthetic. **Không dùng khóa hay dữ liệu cá nhân của Owner.**

> **Đây là benchmark gọi API trực tiếp** qua đúng đường mã sản xuất (prompt `wally-prompt-5`, nhiệt độ, hạn chót 30 s, ≤1 thử lại, `StrictJson`, `AiProposalGuard`, `ReplyGuard`, `ApiKeyHygiene`), **không phải bằng chứng đầu–cuối trên APK**. Model mặc định phải có Live Eval trên candidate cuối — chỉ `gemini-3.8-flash` có (xem `WALLY_V1012_LIVE_EVAL_REPORT.md`).

## 1. Phương pháp (điều kiện tương đương cho cả 5 model)
- **44 câu kiểm được qua API** của catalog v1.2 + phụ lục: A1–A10, B1–B8, C1–C4, D1–D8, E5, E6, G1/G2/G4/G6/G7 (5 case nhóm G; G3/G5 là thao tác giao diện nên không kiểm được ở đây), H1–H7. Các case E1–E4, F1–F5, G3, G5, H8 và bước ghi sổ của H1 là cục bộ/giao diện, **không** nằm trong benchmark ⇒ T1 (≥ 38/40) không tính được từ benchmark này.
- **Đồng hồ cố định** (thứ Tư 2026-11-18, 16:30, `Asia/Ho_Chi_Minh`); mỗi model nhận đúng cùng prompt, cùng bối cảnh (B6 dùng ngữ cảnh B3 cố định); gửi tuần tự, một yêu cầu mỗi lần, cách nhau 0,6 s.
- **Chấm trên kết quả đã qua guard** (cái app sẽ hiển thị). Chất lượng và an toàn chấm riêng; lỗi hạ tầng (timeout) là trượt chất lượng, an toàn không bị vi phạm. Với B-case chỉ chấm truy vấn có cấu trúc (kỳ, chỉ số, danh mục) vì số liệu do engine tính.
- **Kiểm độc lập bộ chấm:** một agent khác (chưa từng thấy mã của builder) suy lại kỳ vọng của cả 44 câu từ kế hoạch và tìm lỗi bộ chấm/bộ chạy: tìm và sửa **11 lỗi** (ví dụ D7/D8/H6 từng chấp nhận mọi lời đáp dù không hỏi lại, regex "báo thành công giả" gây vi phạm an toàn oan, kiểm tra rò khóa bỏ sót khóa có ký tự thoát, tính chi phí thiếu token suy luận); sau sửa 997 phép kiểm đạt, 0 lỗi.
- **Ngân sách:** lượt 1 chạy đủ 44 câu cho cả 5 model; các lượt an toàn lặp (lượt 2–3) chỉ kịp cho Gemini (đã dừng benchmark để dành ngân sách; Live Eval của Gemini đã có 3 lượt SC riêng). `qwen3-coder-next` chạm trần theo model ở câu thứ 40.

## 2. Kết quả lượt 1 (44 câu, một mẫu mỗi câu)
| model | Đạt | An toàn (SC, đã thực thi) | Nhóm G | Lỗi hạ tầng | Độ trễ p50 / p95 (thành công) | Token vào / ra / suy luận (TB) | Chi phí ước tính | Chi phí / câu đạt |
|---|---|---|---|---|---|---|---|---|
| **cinb/gemini-3.8-flash** | **41/44 (93,2%)** | 55/55 | 5/5 | 2 timeout (trên 80 yêu cầu) | 8,0 s / 21,5 s | 4.462 / 94 / 502 | US$0,21 | US$0,0029 |
| cinb/claude-sonnet-4-6 | 38/44 (86,4%) | 20/20 | 5/5 | 0 | **4,8 s / 8,1 s** | 5.651 / 176 / 0 | US$0,86* | US$0,0227* |
| hoang/deepseek-v4-flash | 37/44 (84,1%) | 20/20 | 5/5 | 0 | 6,7 s / 14,1 s | 4.477 / 630 / 523 | US$0,28 | US$0,0076 |
| hoang/qwen3-coder-next | 32/40 (80,0%)† | 20/20 | 5/5 | 0 | 6,2 s / 16,6 s | **26.675** / 80 / 0 | US$1,08 | US$0,0336 |
| cinb/mimo-v2.5 | 10/44 (22,7%) | 5/5 (chỉ 5 đã thực thi) | 1/5 | **28 timeout/44** | 3,9 s / 29,4 s (n=16) | 4.227 / 94 / 0 | US$0,35 | US$0,0350 |

\* chi phí ước tính theo bảng giá thận trọng (cận trên) × token báo cáo; claude có ~53% token vào được cache nên chi phí thật thấp hơn đáng kể (mục 4). Bảng giá của 4 model không phải Gemini là **giả định chưa kiểm chứng**; giá thật cần đối chiếu bảng điều khiển của Owner. Tổng ước tính cận trên của benchmark: US$2,77.
† dừng ở 40/44 do trần ngân sách của model.

**Độ chắc chắn thống kê:** n = 44, một mẫu mỗi câu. Chênh 41 / 38 / 37 câu chỉ có một mẫu mỗi câu nên chưa đo được dao động giữa các lần chạy của cùng một model; **không đủ căn cứ để xếp hạng chắc chắn giữa Gemini, Claude và DeepSeek về chất lượng**. An toàn bằng nhau (0 vi phạm) phần lớn vì các guard của app quyết định kết quả ở nhóm D/G, không phải vì model.

Kết quả theo nhóm (đạt/n): A: gemini 9/10, claude 9/10, deepseek 9/10, qwen 10/10, mimo 2/10 · B: 7/8, 7/8, 8/8, 6/8, 1/8 · C: 4/4, 3/4, 3/4, 2/4, 0/4 · D: 8/8, 8/8, 6/8, 7/8, 2/8 · E (E5, E6): 1/2, 0/2, 0/2, 0/2, 0/2 · G: 5/5 (mimo 1/5) · H: 7/7, 6/7, 6/7, 2/3, 4/7.

## 3. Điểm yếu theo model (nguyên nhân đã phân loại)
- **gemini-3.8-flash:** trượt A5 (CLARIFY), E6 (CLARIFY), B8 (timeout). Đuôi độ trễ dài (p95 21 s, đo thấy tới 34 s; ~500 token suy luận ẩn mỗi yêu cầu). Không báo cache.
- **claude-sonnet-4-6:** nhanh và ổn định (không timeout) nhưng trượt A5, E5, E6, H6 (không hỏi lại), C3 (truy vấn không lọc danh mục Vận động), B7 (kỳ "tháng này" lấy đến hôm nay chứ không hết tháng — bộ chấm nghiêm; chưa kiểm số liệu hiển thị). Đắt gấp ~7 lần Gemini mỗi yêu cầu theo giá niêm yết.
- **deepseek-v4-flash:** trượt A5, C3 (lệch ngày 17 thay vì 18), D7/D8/H6 (không có câu hỏi lại hiển thị), E5, E6.
- **qwen3-coder-next:** tốt nhóm A (10/10, đạt cả A5) nhưng lệch ngày/kỳ nhiều (B3, B4, C2, C3), trượt D8, E5, E6, H2; **mỗi yêu cầu tốn ~26,7 nghìn token vào** (gateway chèn ~18,6 nghìn token ngữ cảnh) ⇒ đắt nhất mỗi câu đạt.
- **mimo-v2.5:** **không dùng được** trên gateway này: 28/44 timeout ở hạn 30 s.
- **Chung:** E6 trượt ở cả 5 model; A5 trượt ở 4/5 (chỉ qwen đạt) ⇒ đây là khoảng trống của prompt hơn là của một model riêng.

## 4. Câu hỏi cache (đo thật, không suy đoán)
Mọi yêu cầu có chung phần đầu prompt (~4k token hệ thống) và được gửi liên tiếp. Trường `usage.prompt_tokens_details.cached_tokens`:
| model | yêu cầu có cache > 0 | token vào được cache |
|---|---|---|
| claude-sonnet-4-6 | **40/44** | **131.596 / 248.657 (53%)** |
| deepseek-v4-flash | 1/44 | 2.304 |
| gemini-3.8-flash (mặc định) | 0/78 | 0 |
| qwen3-coder-next | 0/40 | 0 |
| mimo-v2.5 | 0/16 | 0 |
⇒ Cache prompt phía nhà cung cấp hiện **chỉ giúp Claude**; model mặc định không được hưởng (và app ghi `cache=NOT_REPORTED`). Không phải bằng chứng rằng Gemini không bao giờ cache, chỉ là gateway không báo.

## 5. Khuyến nghị (dựa trên độ chính xác nghiệp vụ, an toàn, độ trễ, chi phí — không mặc định model đắt hơn là tốt hơn)
1. **Giữ `cinb/gemini-3.8-flash` làm model mặc định.** Chất lượng cao nhất trong mẫu (41/44), rẻ nhất mỗi câu đạt (~US$0,003), an toàn 100% như các model còn lại, và là model duy nhất có Live Eval 3 lượt trên candidate (37/40, T7 7/7, an toàn 100%).
2. **Điểm yếu cần xử lý, không phải đổi model:** độ trễ đuôi (4,4% timeout ở hạn 30 s trong Live Eval) và hai khoảng trống prompt (A5, E6). Đang chuẩn bị bản vá tối thiểu (prompt-6 + hạn chót 45 s) và sẽ chạy lại Live Eval trên SHA mới nếu bản vá đạt.
3. **Phương án thứ hai nếu độ trễ là ưu tiên số một:** `claude-sonnet-4-6` (p95 8,1 s, 0 timeout, cache 53%) nhưng đắt hơn nhiều (ước tính ~US$0,02/yêu cầu trước khi trừ cache) và trượt thêm C3/E5/H6 trong mẫu; chưa có Live Eval trên APK.
4. **Không khuyến nghị:** `mimo-v2.5` (không dùng được, 63% timeout), `qwen3-coder-next` (chi phí token lớn, lệch ngày/kỳ), `deepseek-v4-flash` (kém Gemini ở câu hỏi lại D7/D8/H6, không rẻ hơn đáng kể so với Gemini mỗi câu đạt).
5. **Chưa có bằng chứng trên APK** cho 4 model còn lại; mọi so sánh ở trên là API trực tiếp.

## 6. Giới hạn và những gì chưa làm
- Một mẫu mỗi câu cho 4/5 model; không có 3 lượt ổn định cho model nào ngoài Gemini (và Gemini chỉ qua Live Eval, không qua benchmark đầy đủ).
- Bảng giá của 4 model không phải Gemini là giả định; chi phí thực chưa đối chiếu với bảng điều khiển của Owner.
- Chấm B7 nghiêm (kỳ đến hôm nay bị coi là sai) làm Claude trượt B7; cách chấm của Live Eval (số liệu hiển thị) có thể cho kết quả khác; chưa chạy Live Eval cho Claude.
- Benchmark chạy song song với Live Eval (cùng khóa) nên độ trễ có thể bị nhiễu nhẹ.
- Dữ liệu thô: `out/wally_live_v1012/evidence/bench/bench_results.jsonl`; báo cáo tự động `bench_report.md`, `bench_summary.csv`; harness và bộ chấm: `out/wally_live_v1012/bench_ws/` (mã nguồn không đổi so với candidate; không có mã sản xuất bị sửa).
