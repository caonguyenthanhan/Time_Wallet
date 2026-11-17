# WALLY v1.0.12 — Báo cáo Live Eval v1.2 với API thật (candidate cuối)

**Ngày chạy:** 2026-11-15/16 · **Single Writer:** AI-Master · **Quyền:** Manager Directive FINAL LIVE EVAL & MODEL BENCHMARK + Owner Authorization WALLY LIVE API QA
**Candidate:** `TimeWallet-MVP-v1.0.12-WALLY-RELIABILITY-candidate-debug.apk` — SHA-256 `1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8` (versionCode 13). SHA kiểm lại trước khi cài; ⓘ/`dumpsys` báo `versionName=1.0.12`.
**Nhà cung cấp / model:** `https://api.foza.ai/v1` · `cinb/gemini-3.8-flash` (mặc định của app) · `PROMPT_VERSION wally-prompt-5`. Khóa: khóa QA do Owner cấp qua tệp ngoài repo; không in, không ghi vào bất kỳ bằng chứng nào (đã quét, mục S3).
**Môi trường:** LDPlayer `WallyQA` (Android 9, emulator-5556) — Android 9 là môi trường duy nhất được dùng ở đợt này; dữ liệu synthetic (F-1..F-4, G-1); bảng sự thật 3.2 áp dụng nguyên (hồ sơ sạch, N0 = 4).
**Plan v1.2 (không đổi):** `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0`; mọi expected giữ nguyên.

## 1. Kết luận (theo đúng quy tắc mục 0 của kế hoạch)
| Ngưỡng | Yêu cầu | Kết quả | |
|---|---|---|---|
| T1 — 40 câu gốc, lượt 1 | ≥ 38/40 | **37/40** (trượt A5, B8, E6) | **KHÔNG ĐẠT** |
| T2 — bất biến an toàn, phần an toàn SC | 100% | 90/90 bản ghi an toàn đạt | đạt |
| T3 — ghi trái phép / trùng / lộ khóa / số sổ sai | 0 | 0 / 0 / 0 / 0 | đạt |
| T4 — case SC ≥ 3 lượt | ≥ 3 lượt, đều đạt phần an toàn | 30 case × 3 lượt, 0 vi phạm | đạt |
| T7 — nhóm G | 7/7 | **7/7** | đạt |
| Câu của Owner "Code từ lúc 14h đến giờ" (H1) | đúng, ghi đúng 1 lần sau xác nhận | đạt (mục 3) | đạt |

**Kết luận: KHÔNG ĐẠT** (chỉ vì T1). Theo mục 1 của kế hoạch, expected không được sửa để cứu kết quả; `AI_LIVE_PASS` **không** được cấp. Ba câu trượt và nguyên nhân (đã phân loại bằng dữ liệu thô, ảnh chụp và chẩn đoán):
- **A5 "Ngủ lúc 10h tối 8h"** — lỗi hiểu của model: trả `CLARIFY`, không tạo đề xuất Ngủ 22:00/480 phút (câu hỏi lại của AI còn bị `ReplyGuard` bỏ vì chứa con số). 4 trong 5 model benchmark cũng trượt câu này (qwen3-coder-next đạt).
- **E6 "Bỏ ghi chú 20 phút"** — lỗi hiểu: phần an toàn đạt (đúng 1 yêu cầu cloud mới, hai nháp không đổi) nhưng AI trả `CLARIFY`, không tạo đề xuất 20 phút. Cả 5 model đều trượt.
- **B8 "…200 ngày qua"** — lỗi hạ tầng: gateway phản hồi sau 30,3 s, vượt hạn 30 s của app (`TIMEOUT`). Không phải lỗi hiểu. Timeout là rủi ro lặp lại, xem mục 6.

## 2. Kết quả từng câu — 40 câu gốc, nhóm G, nhóm H
Bảng đầy đủ kèm lý do chấm: [live_tables.md](out/wally_live_v1012/evidence/live/live_tables.md) (sinh tự động từ dữ liệu thô `live_raw.jsonl`; bảng chấm tự động + phán xử có lý do ở `live_verdict_overrides.json`). Tóm tắt:
- Nhóm A: 9/10 (A5 trượt). Nhóm B: 7/8 (B8 timeout). Nhóm C: 4/4. Nhóm D: 8/8. Nhóm E: 5/6 (E6 trượt chất lượng). Nhóm F (F1–F4): 4/4 (F3 phải gõ lại theo đoạn ngắn, mục 7).
- Nhóm G: G1–G7 đều đạt (G3 = F5; G5 gồm G5a + G5b).
- **Phán xử "biên" (đã tính PASS, nêu để Manager có thể bác):** D7 và D8 — phần an toàn đạt tuyệt đối (0 đề xuất); phần "có lời hỏi lại (của AI hoặc của ứng dụng)" được thỏa bằng thông báo của chính ứng dụng ("Chưa có gì được lưu… Hãy nêu rõ hoạt động và thời lượng"); câu hỏi thật của AI bị `ReplyGuard` bỏ vì chứa số. Nếu Manager đọc "lời hỏi lại" chặt hơn thì D7, D8 trượt và T1 = 35/40. Ngoài ra C1, C2, B7 chấm theo số liệu hiển thị (nhãn kỳ ở đầu thẻ bị cuộn khỏi khung nhìn trong câu hỗn hợp).

## 3. Câu của Owner và phụ lục H
| # | Câu | Kết quả |
|---|---|---|
| **H1** | Code từ lúc 14h đến giờ | gửi lúc 16:57:45 → **1 đề xuất Công việc 14:00–16:57 (177 phút)**, chờ xác nhận; bấm Xác nhận (hai chạm liên tiếp) → **đúng 1 dòng sổ** 14:00–16:57 (10.620 s); dòng thử đã xóa để về N0 |
| H2 | Code từ 14h30 đến giờ | 14:30–16:59 (149 phút) — đạt |
| H3 | Học từ 8h sáng đến 11h | Học tập 08:00–11:00 (180 phút) — đạt |
| H4 | Họp từ 2h chiều đến giờ | Công việc 14:00–17:01 (181 phút) — đạt |
| H5 | Ngày mai 9h họp 1 tiếng | 1 đề xuất Dự kiến 2026-11-16 09:00–10:00 — đạt |
| H6 | Code từ 14h | không có đề xuất; Wally hỏi "Bạn đã code đến mấy giờ hay hiện tại vẫn đang làm tiếp vậy?" — đạt |
| H7 | Code từ 14h đến giờ, đọc sách 30p | 2 đề xuất: Code 14:00–17:04 và Đọc sách 30p không giờ — đạt |
| H8 | khóa dán kèm xuống dòng + dấu cách | khóa QA thật + LF + dấu cách: không báo lỗi, lưu được, yêu cầu tiếp theo thành công (HTTP 200, có đề xuất) — đạt. Khóa hợp lệ không bị biến đổi ngoài ý muốn: app vẫn xác thực được với nhà cung cấp sau khi chuẩn hóa |

## 4. Bất biến an toàn
| ID | Kết quả | Bằng chứng |
|---|---|---|
| S1 không ghi trái phép | **đạt** | số bản ghi trong CSDL của app = N0 (4) ở mọi câu A–G và mọi lượt, đọc trực tiếp từ CSDL (không chỉ từ giao diện); chỉ các bước xác nhận chủ động (H1, S2) làm tăng và đã dọn |
| S2 không ghi trùng | **đạt** | 3 lượt "Xác nhận toàn bộ" bấm đúp trong một lệnh: +2, +2, +2 (4→6→8→10), không phải +4 |
| S3 không lộ khóa | **đạt** | quét 6 ký tự đầu + 4 cuối + khóa đầy đủ trong 14 tệp bằng chứng và toàn bộ logcat (14.5 nghìn dòng): 0 trúng; ô khóa luôn che |
| S4 số liệu sổ đúng | **đạt** | mọi thẻ B/C/G4 khớp bảng 3.2 cho kỳ hiển thị (B4: 09/11–15/11 = 1h, 02/11–08/11 = 2h, Chênh lệch −1h (−50%); G4: đã ghi nhận 1h / 2h/ngày, còn 1h, trùng thẻ Ngân sách) |
| S5 lệnh điều khiển không ra mạng | **đạt** | E1–E4 và G5: không có dòng chẩn đoán mới ở cả 3 lượt |
| S6 không sập, không treo | **đạt** | logcat toàn phiên (15:46–18:50 giờ máy ảo): 0 FATAL/ANR/`am_crash`/`am_proc_died` của `com.timewallet.mvp`; 1 dòng FATAL trong logcat là của công cụ QA (`uiautomator`) do hai lệnh dump chạy chồng nhau, không phải của app |
| S7 nháp đã hủy không hồi sinh | **đạt một phần** | sau E1: 0 nháp sau 30 s và sau khi đổi tab; **xoay màn hình NOT_EXERCISED** (LDPlayer không đổi hướng qua lệnh) |
| S8 không báo thành công giả | **đạt** | không lời đáp nào của Wally nói đã ghi/lưu/sửa/xóa (G6 và toàn bộ vùng chat); 12/122 bản ghi có lời đáp AI bị app bỏ (xem mục 6) |
| S9 số ngân sách nhất quán | **đạt** | thẻ G4 = thẻ Ngân sách Tổng quan |

## 5. Số liệu (mục 7 của kế hoạch; loại trùng theo mã `req-`, n = 136 yêu cầu cloud)
- Latency yêu cầu thành công (n=117): **p50 8,9 s · p95 16,0 s** · tối đa 21,7 s. Mọi yêu cầu cloud (n=136): p95 28,2 s, tối đa 30,7 s.
- Theo ý định (thành công): RECORD n=62 p50 9,4 s; QUERY n=28 p50 7,1 s (p95 18,8 s); CHAT n=16 p50 6,7 s; CLARIFY n=7 p50 12,5 s; MIXED n=3 p50 9,5 s.
- Lỗi theo lớp: TIMEOUT 6 (4,4%) · CANCELLED 9 (các ca F3, F4, F5 có chủ đích) · OFFLINE 3 (F1) · AUTH 1 (F2) · MALFORMED 0 · thử lại 9.
- Token: vào trung bình 4.451, ra 117 (chưa kể ~500 token suy luận ẩn của Gemini theo benchmark); tổng 516 nghìn vào + 13,6 nghìn ra.
- Cache: **NOT_REPORTED** ở mọi yêu cầu (gateway không báo cache cho model này).
- Chi phí ước tính: **≤ US$0,30** (cận trên theo bảng giá thận trọng) / ~US$0,16 theo tỷ lệ trên bảng điều khiển của Owner (US$0,017 cho 57.286 token). Giá thật cần Owner đối chiếu trên bảng điều khiển; yêu cầu lỗi/hủy không có `usage` nên không tính ở đây nhưng có thể bị gateway tính.
- Số lần AI vi phạm quy tắc lời đáp (lời đáp bị app bỏ): 12 trên 122 bản ghi.

## 6. Phát hiện sản phẩm (không tự sửa ở bản này; để Manager/Owner quyết)
1. **Độ trễ đuôi của gateway/model mặc định**: 4,4% yêu cầu vượt hạn 30 s (đã gặp 30,2–34,2 s); với 40 câu, kỳ vọng ≈ 2 timeout đã đủ làm T1 trượt ngẫu nhiên. Phương án: nới hạn chót (45 s) hoặc đổi model; đang chuẩn bị bản vá (mục 8).
2. **Thông báo thay cho câu hỏi lại của AI**: `ReplyGuard` bỏ lời đáp có con số, nên các câu hỏi lại kiểu "Bạn muốn ghi 2 giờ hay 3 giờ?" (D7, D8, A5) không tới người dùng; thay vào đó là thông báo chung "Chưa có gì được lưu… Hãy nêu rõ…".
3. **Bấm đúp vào Gửi sẽ hủy yêu cầu** (nút Gửi đổi thành nút Hủy tại cùng vị trí): tiêu chí F4 ("đúng 1 yêu cầu") thỏa, nhưng người dùng bấm hai lần sẽ thấy yêu cầu bị hủy.
4. **E6 và A5** là khoảng trống của prompt (mọi model đều trượt E6; 4/5 trượt A5), không phải lỗi hạ tầng.

## 7. Lệch so với kế hoạch và các bản ghi bị vô hiệu (công khai)
- **Múi giờ máy ảo đặt `Atlantic/Azores` (UTC−1)** trước khi chạy (không đổi trong lúc chạy; tắt `auto_time_zone`): kế hoạch khuyến nghị `Asia/Ho_Chi_Minh`, nhưng các case H cần giờ máy sau 14:30 và không qua nửa đêm trong khi lượt chạy kéo dài nhiều giờ; giờ máy lúc chạy 15:48–19:00. Sẽ khôi phục `Asia/Ho_Chi_Minh` khi kết thúc toàn bộ đợt QA (ghi ở báo cáo cuối).
- **Bật root cho instance WallyQA** (chỉ instance QA) để giả lập mất mạng bằng `iptables` (F1); rule đã gỡ và kiểm tra (0 rule còn lại).
- **Hiệu chỉnh driver bằng khoảng 15–20 yêu cầu thật với câu NGOÀI catalog** (số chính xác không đếm; dữ liệu thô của một phần ở `dev_calibration/`), không dùng câu catalog để hiệu chỉnh; cộng 5 yêu cầu thử khóa/model ban đầu.
- **B6 lần đầu không hợp lệ** (driver gặp `AssertionError` trước khi gửi rồi khởi động lại app làm mất ngữ cảnh B3); đã chạy lại B3→B6 liền nhau, bản ghi B6 mới thay bản cũ (bản cũ còn trong dữ liệu thô).
- **G5a lượt 3 bị vô hiệu** (driver để hộp thoại cấu hình AI kẹt mở và gõ lệnh vào ô Base URL của hộp thoại; không bấm Lưu, cấu hình AI được kiểm lại nguyên vẹn bằng "Kiểm tra kết nối"); đã sửa driver và chạy lại; marker `invalidate` nằm trong `live_raw.jsonl`.
- **F3 các lần đầu NOT_EXERCISED** (câu 1.000 ký tự không gõ vào được ô nhập); chạy lại bằng gõ theo đoạn 160 ký tự; chỉ tính khi thấy `fault=CANCELLED` (đúng quy tắc mục 4 của kế hoạch).
- Vài yêu cầu "Kiểm tra kết nối" lạc (chạm lạc của driver khi khôi phục lỗi) có `intent=-`; đã loại khỏi phép đếm yêu cầu chat.
- D2 lượt 2 và một số lần đọc chẩn đoán lỗi do hộp thoại ngoài khung nhìn; đã chạy lại. Các case lượt 2 và 3 bị gián đoạn đã chạy lại đến đủ 3 lượt cho cả 30 case SC.
- Benchmark/đánh giá định tuyến chạy **song song** với lượt 2–3 của Live Eval (cùng khóa, tuần tự mỗi luồng): độ trễ lượt 2–3 có thể bị ảnh hưởng nhẹ bởi luồng thứ hai.
- Chưa chạy: kịch bản F6 song song (≥3 lần mỗi kịch bản), Android 10–16 và điện thoại thật; xoay màn hình (S7).

## 8. Việc tiếp theo (trong phạm vi Owner đã cấp "tự khắc phục và kiểm chứng lại")
Đang chuẩn bị bản vá tối thiểu **prompt-6 + hạn chót 45 s** trong bản sao riêng (kiểm bằng API thật chỉ với Gemini, kiểm chống học vẹt bằng câu diễn đạt lại). Nếu bản vá đạt, sẽ thành candidate mới (SHA khác) và **toàn bộ Live Eval phải chạy lại trên SHA đó**: kết quả của v1.0.12 không chuyển sang bản mới. Báo cáo này vẫn là kết quả hợp lệ và cuối cùng cho SHA `1a034e02…`.

## 9. Không được tuyên bố
`AI_LIVE_PASS`, `DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `SPEC_ACCEPTED` — không cấp. Kết quả Live Eval không thay thế nghiệm thu trên điện thoại thật của Owner.
