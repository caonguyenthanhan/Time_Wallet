# Wally — Đánh giá Hybrid Intent Routing (offline, không gọi API)

> **ĐÃ KIỂM TOÁN ĐỘC LẬP — đọc `ROUTING_AUDIT.md` cùng báo cáo này.** Kiểm toán tính lại mọi số liệu gốc (khớp), chấm nhãn mù 86 câu (83/86 trùng, kappa 0,929) và viết 263 câu tấn công: cổng gốc (gate-1) để lọt 57 lỗ hổng xác nhận trong 9 lớp (ví dụ "Chạy bộ 5h30" ghi 330 phút, "Họp từ 2h đến 15h" ghi 13 giờ, "Hôm nay tôi tập bao nhiêu phút?" trả tổng mọi danh mục). Đã vá (gate-2: AF1 đến AF9, diff ở `audit_scripts/tier0router_gate1_to_gate2.diff`), chạy lại toàn bộ harness: 0 lỗ hổng xác nhận. **Các con số trong báo cáo này đã được cập nhật sang gate-2**; giá của bản vá: coverage gộp 38,9% xuống 37,1%, false-escalate 11 lên 17 (nhãn giữ nguyên). Hai điều chỉnh về nội dung: (1) số timeout tiết kiệm KHÔNG có chứng cứ (log live: câu đủ điều kiện cục bộ 0/19 timeout, 6 timeout đều thuộc 4 câu khác), đọc là "0 đến 22 trên 1.000 yêu cầu"; (2) "78,6% cache cũ/sai" là trung bình 5 đồng hồ do người dựng chọn (cùng ngày: 13,3%). Phiên bản gốc trước kiểm toán nằm ở `route_ws/audit/before/`.

Ứng viên đóng băng: v1.0.12 (`source_v1.0.12.tar`). Mọi số liệu dưới đây do mã tính (harness Kotlin trong `route_ws`), không đọc bằng mắt. Không gọi mạng, không emulator, không sửa `app/src/main` (đã kiểm: `diff -r` với tar = giống hệt).

## 0. Kết quả trong một màn hình

**Đề xuất: làm Router Tier-0 tất định (phương án B), gói nhỏ nhất ba dạng câu, bật sau cờ tính năng mặc định TẮT, chạy chế độ "bóng" trước. Không làm cache theo câu (A). Prompt-cache của nhà cung cấp (C) là việc song song, không thay thế được B.**

| Chỉ số (cổng đã chốt, đồng hồ cố định Thứ Tư 2026-11-18 16:30) | Catalogue 44 câu | Corpus mới 293 câu | Gộp 337 câu |
|---|---|---|---|
| Tỉ lệ yêu cầu xử lý KHÔNG cần Cloud (coverage) | 11/44 = **25,0%** | 114/293 = **38,9%** | 125/337 = **37,1%** |
| Độ chính xác của phần xử lý cục bộ (chấm đúng) | 11/11 = **100%** | 114/114 = **100%** | 125/125 = **100%** |
| False-local (xử lý cục bộ mà SAI, hoặc nhãn MUST_ESCALATE) — trên corpus này; bộ đối kháng của kiểm toán: cổng gốc 57 lỗ hổng, gate-2 0 | **0** | **0** | **0** (0,0%) |
| False-escalate (đưa lên Cloud dù nhãn EXPECT_LOCAL_OK) | 3/14 = 21,4% | 14/128 = 10,9% | 17/142 = 12,0% |
| Bộ hybrid trên catalogue (câu bị escalate dùng kết quả AI thật của lần chạy live) | 41/44 | | AI thuần cũng 41/44 (A5, B8, E6 trượt, cả ba vẫn lên Cloud) |

Đọc đúng các con số này:

* Catalogue (25%) là mốc THẤP và độc lập: nó gồm các câu cần AI, kỳ vọng do bản kế hoạch của Manager đặt, không do tôi. Corpus mới (41%) là corpus do tôi viết nên tỉ lệ 128/293 câu "nên cục bộ" là do tôi dựng, không phải tỉ lệ thật của người dùng. Cả hai KHÔNG phải tỉ lệ lưu lượng thật. Kinh tế dưới đây vì thế dùng dải 20/40/60%.
* Độ chính xác 100% và 0 false-local là kết quả của cổng đã được siết SAU khi chạy lần đầu (ba lần chỉnh, ghi ở mục 3.4, không đổi nhãn nào). Corpus và cổng cùng một tác giả; chống thiên lệch bằng: viết corpus trước router, hai cách tính độc lập phải khớp, bộ biến dị 2.172 câu, bộ tổ hợp 350 câu (mục 5). Chưa có tập mù và chưa có lưu lượng thật.
* Cổng KHÔNG học gì từ AI (hàm thuần của `(câu, hôm nay, giờ hiện tại)`, không lưu gì).

| Kinh tế trên 1.000 yêu cầu (nền: p50 8,5 s, 4.850 token prompt, 650 token ra gồm suy luận ẩn, 6% quá 30 s, USD 0,0025 cận trên / 0,001 theo dashboard) | Hiện tại | Router ở 25% | Router ở 37,1% | 40% | 60% (vượt trần hợp lý, xem ROUTING_AUDIT mục 5) |
|---|---|---|---|---|---|
| Lượt gọi Cloud | 1.000 | 750 | 629 | 600 | 400 |
| Token prompt (triệu; theo 4.850 của đề bài, theo trung bình đo trong log 4.455 thì thấp hơn 8,1%: 37,1% cho 2,80) | 4,85 | 3,64 | 3,05 | 2,91 | 1,94 |
| Chi phí ước tính (cận trên / dashboard, USD) | 2,50 / 1,00 | 1,88 / 0,75 | 1,57 / 0,63 | 1,50 / 0,60 | 1,00 / 0,40 |
| Lượt chạm hạn 30 s (timeout) — CHỈ NẾU câu cục bộ có cùng xác suất timeout; log live: 0/19 ở câu đủ điều kiện, 6/68 ở câu còn lại, nên đọc là "0 đến mức này" | 60 | 45 | 38 | 36 | 24 |
| Độ trễ trung vị toàn luồng (s; "Hiện tại" của mô hình là 8,8 chứ đề bài là 8,5) | 8,8 | 7,2 | 6,0 | 5,6 | 0,02 |
| Độ trễ p90 / p95 toàn luồng (s; p95 là ngưỡng: tỉ lệ timeout 4,7% đã cho p95 29,1 chứ không phải 30,0) | 18,4 / 30,0 | 16,1 / 25,7 | 14,9 / 21,3 | 14,6 / 20,5 | 12,4 / 16,5 |
| Thời gian chờ tiết kiệm (giây) | 0 | 2.655 | 3.939 | 4.248 | 6.372 |

Điểm trung thực nhất của phần kinh tế: **tiền gần như không đáng kể** (tiết kiệm 0,4 đến 0,9 USD trên 1.000 yêu cầu ở 37,1%). Giá trị thật là **độ trễ** (câu cục bộ trả lời trong khoảng 0,6 ms trên JVM máy tính, thay cho ~9 s) và **độ tin cậy** (ít lượt chạm hạn 30 s, ít phụ thuộc mạng). Nếu Owner kỳ vọng "giảm chi phí" thì kỳ vọng đó nên được điều chỉnh.

## 1. Đề xuất của Owner và bằng chứng nằm ở đâu

| Yêu cầu | Bằng chứng | Mục |
|---|---|---|
| Tỉ lệ yêu cầu không cần Cloud | coverage theo nhóm câu | 4 |
| Độ chính xác phần xử lý cục bộ | chấm theo kỳ vọng độc lập (catalogue: bộ chấm của Manager) | 4 |
| Tỉ lệ khớp nhầm mẫu | false-local = 0, liệt kê mọi trường hợp (không có) | 4.3 |
| Độ trễ, API call, token, chi phí trước/sau | bảng kinh tế + dải 20/40/60% | 0, 6 |
| Dữ liệu động (giờ/ngày/sổ cái) | cache theo câu vs slot tính lại | 5.1 |
| Phủ định, sửa lại, mơ hồ, câu mới | hành vi của cổng | 4.4 |
| Không tăng ghi sai/trùng/trái phép | bất biến an toàn + đường xác nhận thật | 5.2 |
| Không học điều AI hiểu sai | thiết kế + rủi ro của biến thể "học mẫu" | 8 |

## 2. Cổng của Router (chính xác)

Router = `route(text, today, now) → LOCAL(outcome) | ESCALATE(lý do)`. Chỉ dùng mã đã có: `VietnameseChatParser`, `QueryIntentParser`, `AiProposalGuard`, `AiQueryValidator`, `CancelCommandParser`, `Rx`. Mọi nghi ngờ hoặc ngoại lệ ⇒ ESCALATE (đường Cloud giữ nguyên). Lệnh hủy ("Hủy tất cả đề xuất"...) vẫn ở nhánh điều khiển cục bộ có sẵn trong ViewModel, không phải việc của router (4 câu loại khỏi thống kê).

| # | Điều kiện (tất cả phải đúng) |
|---|---|
| G0 | 1 đến 70 ký tự, không xuống dòng, không phải lệnh hủy |
| G1 | Không có từ meta/chèn lệnh: xác nhận, ghi, lưu, sổ, bỏ qua, hệ thống, prompt, ignore, system, record, admin, tự động, ... |
| G2 | Không có từ phủ định/sửa lại: không, chưa, chẳng, đừng, chớ, hủy, bỏ, sửa, nhầm, à không, ý là, thay vì, chứ, ko, hok, ... |
| G3 | Không có từ ước lượng: khoảng, tầm, vài, mấy, nhiều, ít, cả buổi, ... (trừ "nhiều nhất/ít nhất/bao nhiêu/bao lâu") |
| G4 | Đúng MỘT mệnh đề: không có và/rồi/nhưng/hoặc/với/cùng/còn/thì/mà/nữa/cả/luôn, không có `,` `;` |
| G5 | MỌI token được giải thích: sau khi bỏ số+đơn vị, các họ từ khóa hoạt động, từ đệm (tôi, mình, đã, vừa, xong, hôm nay/qua/kia, ngày mai) và từ khung giờ (từ/đến/tới, sáng/chiều/tối), không còn gì ngoài {bài, tập}. Hoạt động thuộc đúng MỘT họ và khớp `classifyCategory` |
| G6 | Kế toán số: ghi nhận = đúng 1 nhóm số; "đến giờ" = đúng 1 mốc; truy vấn = 0 số |
| G7 | Chống mơ hồ (gate-2): "Nh" trần (đơn vị chỉ là "h") chỉ nhận N ≤ 4 (ngủ ≤ 8) và KHÔNG nhận cho hoạt động kiểu sự kiện (ăn, tắm, cà phê, nghỉ, thiền, tập/đi bộ/đạp xe/gym, gặp gỡ, họp, báo cáo) vì thường là giờ trong ngày (AF1); thập phân chỉ ".5" (AF9); mốc giờ không có buổi phải có ít nhất một đầu ≥ 13h, đầu nhỏ ≥ 5h và khoảng ≤ 10 giờ (AF2); tối đa một từ buổi; không "đêm"; khoảng giờ phải tăng dần, ≤ 12 giờ, và không đang chạy tại giờ gửi (AF7); thì quá khứ + ngày tương lai lên Cloud (AF6); "đến giờ" cần giờ bắt đầu không mơ hồ (≥ 13h hoặc có đúng một buổi), **nhỏ hơn hẳn giờ hiện tại truyền vào** và cách ≤ 12 giờ; thời lượng ≤ 12 giờ |
| G8 | HAI cách tính phải khớp: số phút, khoảng giờ, độ lệch ngày, các kỳ truy vấn, giờ bắt đầu "đến giờ" được tính lại bằng mã độc lập và phải bằng kết quả của parser/`QueryIntentParser`/`AiProposalGuard.openEndedStartCandidates` |
| G9 | Qua ĐÚNG các guard mà kết quả Cloud đi qua: `AiProposalGuard.apply` không đổi, không cảnh báo; `AiQueryValidator.validate`. Đề xuất giữ `needsConfirmation = true`; số liệu thống kê do `LedgerQueryEngine` tính sau (router chỉ tạo `QueryIntent`) |
| G10 | Tiêu đề do parser sinh phải sạch (không chữ số, mọi từ được giải thích): chặn "Học chiều", "Họp phút", "Học 1h" |

Khe giờ hiện tại ("đến giờ") được tính từ `today`/`now` truyền vào tại thời điểm gửi; không lưu, không cache.

Ba dạng LOCAL của gói nhỏ nhất đề xuất (mục 7): **S1** ghi thời lượng đơn ("Đọc sách 30 phút", "Hôm qua học 2 tiếng"); **S2** truy vấn kỳ lịch (hôm nay/qua/kia, tuần này/trước, tháng này/trước, kèm danh mục tùy chọn, và so sánh hai kỳ); **S3** "từ Xh đến giờ". Dạng **S4** khoảng giờ tường minh ("từ 9h đến 17h") có trong nguyên mẫu nhưng đề xuất để ngoài v1 (mục 7).

## 3. Corpus

### 3.1 Nguyên tắc dán nhãn (viết TRƯỚC router)

`EXPECT_LOCAL_OK` chỉ khi câu: một mệnh đề; đủ (hoạt động thuộc kho quen + thời lượng, hoặc khoảng giờ/"đến giờ" không mơ hồ, hoặc truy vấn có kỳ lịch rõ); không phủ định/sửa lại/nối/ước lượng/meta; có dấu tiếng Việt; mốc giờ không mơ hồ AM/PM. Mọi thứ khác `MUST_ESCALATE` (gồm: trò chuyện, truy vấn nối tiếp cần ngữ cảnh, quy ước cửa sổ trượt như "7 ngày qua", ngân sách/thứ/năm, lệnh sửa, câu kiểu chèn lệnh). Kỳ vọng của câu cục bộ được tính tay từ câu và đồng hồ, không lấy từ đầu ra của router.

### 3.2 Quy mô

* Catalogue: 44 câu (A1 đến H7), chấm bằng `BenchGraders` nguyên bản (chấm kết quả sau guard; `source` được đặt thành CLOUD khi chấm vì cột source thuộc về việc định tuyến, không thuộc về chất lượng câu trả lời). Nhãn định tuyến: 14 EXPECT_LOCAL_OK, 30 MUST_ESCALATE.
* Câu mới do tôi viết: **293** (+4 lệnh hủy loại khỏi thống kê): 128 EXPECT_LOCAL_OK, 165 MUST_ESCALATE; 24 loại câu (cộng nhóm lệnh hủy loại khỏi thống kê); 95 câu đánh dấu DYNAMIC (phụ thuộc đồng hồ/ngày/sổ cái ngoài chuyện "ngày = hôm nay").
* Đồng hồ: C1 Thứ Tư 2026-11-18 16:30 (chính), C2 Thứ Bảy 2026-12-05 21:45 (tuần vắt qua tháng), C3 Thứ Hai 2027-01-04 08:05 (trước giờ bắt đầu "đến giờ").
* File: `routing_corpus.json` (mỗi câu: nhãn theo từng đồng hồ, dynamic, kỳ vọng độc lập, lý do).

### 3.3 Chỗ cần đọc kỹ

Nhãn của vài câu catalogue cố ý đặt "nên cục bộ" dù cổng mức tối thiểu không hỗ trợ: A3 (có từ đệm "tập trung"), A5 (giờ bắt đầu "lúc 10h tối" + 8h), A9 ("tổng kết" trong tiêu đề). Chúng thành false-escalate trung thực (mục 4.3), không bị nhãn hóa để cho đẹp.

### 3.4 Những chỉnh cổng sau lần chạy đầu (nhật ký, `labelLog` trong harness)

Lần chạy đầu: coverage 40,5%, 0 false-local, 7 false-escalate. Sau đó, không đổi nhãn nào, chỉ siết cổng: (1) thêm G10 vì tiêu đề "Học chiều/Họp phút/Học 1h" lọt qua; (2) bỏ "khóa" khỏi danh sách meta vì chặn nhầm "khóa học"; (3) soát từ đệm: bỏ "sẽ" (ý định tương lai), "đi/nhé/nha" (giọng đề nghị), siết "ngủ Nh" xuống ≤ 9. Bốn câu mới thêm vào corpus nhờ soát (không nhờ thất bại): "Tôi sẽ học 2 tiếng", "Học 2 tiếng đi", "Học 2 tiếng nhé", "Ngủ 10h". Kết quả cuối ở mục 0 và 4.

## 4. Kết quả

### 4.1 Theo họ câu

Catalogue:

| Họ | n | EXPECT_LOCAL_OK | Cục bộ | Coverage | Đúng/đã xử lý | False-local | False-escalate |
|---|---|---|---|---|---|---|---|
| RECORD (A, H) | 17 | 9 | 6 | 35,3% | 6/6 | 0 | 3 |
| QUERY (B) | 8 | 5 | 5 | 62,5% | 5/5 | 0 | 0 |
| MULTI_MIXED (C) | 4 | 0 | 0 | 0 | - | 0 | 0 |
| NEGATION_CORRECTION (D) | 8 | 0 | 0 | 0 | - | 0 | 0 |
| CHAT_CONTROL (E5, E6) | 2 | 0 | 0 | 0 | - | 0 | 0 |
| ADVERSARIAL (G1, G2, G4, G6, G7) | 5 | 0 | 0 | 0 | - | 0 | 0 |

Câu cục bộ trên catalogue: A1, A4, B1, B3, B4, B5, B7, H1, H2, H3, H4 (11/11 đúng; AI thật cũng đạt cả 11, nên ở đây router bằng AI về đúng/sai, chỉ nhanh hơn và không tốn token).

Câu mới:

| Họ | n | EXPECT_LOCAL_OK | Cục bộ | Coverage | Đúng/đã xử lý | False-local | False-escalate |
|---|---|---|---|---|---|---|---|
| RECORD | 110 | 92 | 78 | 70,9% | 78/78 | 0 | 14 |
| QUERY | 51 | 36 | 36 | 70,6% | 36/36 | 0 | 0 |
| NEGATION_CORRECTION | 24 | 0 | 0 | 0 | - | 0 | 0 |
| MULTI_MIXED | 23 | 0 | 0 | 0 | - | 0 | 0 |
| AMBIGUOUS_UNSEEN | 46 | 0 | 0 | 0 | - | 0 | 0 |
| TYPO_NODIACRITIC | 14 | 0 | 0 | 0 | - | 0 | 0 |
| CHAT_CONTROL | 15 | 0 | 0 | 0 | - | 0 | 0 |
| ADVERSARIAL | 10 | 0 | 0 | 0 | - | 0 | 0 |

Theo loại câu (mới), phần cục bộ: ghi thời lượng đơn 47/56, ghi có ngày tương đối 8/8, khoảng giờ 7/10, "đến giờ" 16/18 (2 câu còn lại đúng là phải escalate), truy vấn đơn 30/30, so sánh 6/6. Mọi loại còn lại (phủ định, sửa lại, nối nhiều mệnh đề, mơ hồ, hoạt động lạ, thiếu dấu/gõ sai, chit-chat, lệnh sửa, hỗn hợp, chèn lệnh, câu dài) 0% cục bộ. Bảng đầy đủ từng loại: `routing_summary.csv`.

Lý do escalate (câu C1): phủ định/sửa lại 45, từ nối 24, hoạt động không thuộc kho 23, từ khóa meta 20, số nhóm số không khớp 17, từ lạ trong mệnh đề 14, dấu phẩy 13, ước lượng 9, "Nh" trần 9, giờ mơ hồ 8, còn lại ≤ 5 mỗi loại.

### 4.2 Hành vi trên đồng hồ khác (cùng câu, đồng hồ khác)

Trên 95 câu động, C2 (Thứ Bảy 21:45, tuần vắt tháng): cục bộ 67, đúng 67, sai 0. C3 (Thứ Hai 08:05): cục bộ 53, đúng 53, sai 0; các câu "Code từ 14h đến giờ" đều ESCALATE vì 14:00 chưa tới (router không tạo khoảng âm). Ví dụ cùng câu "Code từ 14h đến giờ": C1 → 14:00 đến 16:30 (150 phút, ngày 2026-11-18); C2 → 14:00 đến 21:45 (465 phút, ngày 2026-12-05).

### 4.3 False-local và false-escalate (liệt kê đủ)

**False-local: 0** (không có câu nào được xử lý cục bộ mà sai hoặc mà nhãn là MUST_ESCALATE, ở C1, C2, C3).

**False-escalate: 17** (11 ở gate-1, cộng 6 mới do kiểm toán; mất cơ hội tiết kiệm, không mất an toàn):

| Câu | Lý do |
|---|---|
| A3 "Từ 13:00 đến 18:00 làm việc tập trung" | từ lạ "trung" (G5) |
| A5 "Ngủ lúc 10h tối 8h" | dạng "lúc Xh + thời lượng" không nằm trong gói tối thiểu (AI thật cũng TRƯỢT câu này) |
| A9 "Hôm nay họp tổng kết dự án 2h" | từ lạ "tổng kết" |
| "Học 1h30p", "Họp 1h30 phút" | tiêu đề sinh ra bẩn ("Học 1h", "Họp phút"), G10 |
| "Học 2,5 tiếng" | dấu phẩy thập phân bị G4 chặn; parser thô đọc thành 300 phút (xem 4.5) |
| "Học 1 giờ 30" | parser đọc 60, cách tính độc lập ra 90, G8 chặn (parser thô sai) |
| "Học từ 2h chiều đến 4h chiều", "Học từ 7h tối đến 9h tối" | tiêu đề bẩn ("Học chiều", "Học tối"), G10 |
| "Lúc 20h học 45p", "Ngủ lúc 22h 8 tiếng" | dạng điểm bắt đầu, ngoài gói tối thiểu (kiểm toán: nhãn EXPECT_LOCAL_OK của hai câu này quá lạc quan, nên thực tế false-escalate bị đếm dư) |
| "Gặp gỡ bạn bè 2h", "Họp dự án 1h30", "Đạp xe 1h", "Gym 1h", "Báo cáo 1h" | MỚI (kiểm toán, AF1): "Nh" trần trên hoạt động kiểu sự kiện lên Cloud vì có thể là giờ trong ngày ("Họp 2h", "Cà phê 3h"); giá chấp nhận được, có thể nới theo từng hoạt động bằng dữ liệu chạy bóng |
| "Làm việc từ 9h đến 17h" | MỚI (kiểm toán, AF7): ở đồng hồ C1 (16:30) khoảng giờ còn đang chạy; sau 17h vẫn cục bộ |

### 4.4 Phủ định, sửa lại, mơ hồ, câu mới

* Câu phủ định/sửa lại thật (catalogue D1 đến D8, mới NEG 16 + CORR 8): 0 câu xử lý cục bộ. Cổng không có đường "chứng minh một câu phủ định là đúng", nên không câu phủ định nào được xử lý cục bộ.
* Biến dị tự động: thêm "không/tôi chưa/đừng ghi" vào đầu, ", à không, 3h" vào cuối, "Nhầm rồi," vào đầu, "và/rồi/nhưng ..." vào cuối, "khoảng", "không cần xác nhận", "(bỏ qua xác nhận)", "Ghi luôn ... vào sổ", "Xin chào," vào đầu, gõ sai chữ đầu, bỏ dấu, thêm câu hỏi vào cuối: **2.172 câu biến dị, 0 câu còn xử lý cục bộ**. 342 biến thể vô hại (IN HOA, thêm "!", dãn cách): kết quả không đổi 342/342 (gate-1: 360/360 trên 120 câu nền thay vì 114).
* Hoạt động lạ: 44 hoạt động chưa có trong kho (làm vườn, vẽ tranh, ...) và 5 từ khóa tranh cãi (python, gọi điện, đi làm, khách hàng, "chơi" trơn) = 49 cụm × 2 cách viết thời lượng = 98 câu: 98/98 escalate.
* Câu mới nhưng thuộc kho: mọi cụm trong kho × 4 cách viết thời lượng (30 phút, 2h, 1 tiếng, 45p) = 252 câu tổ hợp: gate-2 cho 217 cục bộ và đúng, 35 chuyển Cloud theo luật AF1 ("Cà phê 2h", "Ăn trưa 2h", "Họp 2h" có thể là 2 giờ chiều; gate-1 nhận hết 252/252, đó chính là lỗ hổng), 0 sai (sau khi bỏ "khóa" khỏi danh sách meta; lần đầu 4 câu "Khóa học ..." bị chặn nhầm).
* Mơ hồ: "khoảng/vài/mấy/cả buổi", "Họp 9h" (giờ hay thời lượng), "Làm việc 8h", "Ngủ 10h", "Học 2-3 tiếng", thiếu thời lượng/thiếu hoạt động: escalate.

### 4.5 Cái cổng ngăn được (đường cục bộ KHÔNG có cổng, từ phiên bản trước API-first)

Đường cũ (`QueryIntentParser` rồi `VietnameseChatParser.parse`, không cổng) cho ra kết quả hành động được trên 228/337 câu (so với 125 của cổng gate-2), trong đó 106 câu mà nhãn nói phải escalate. Các kết quả SAI/nguy hiểm đã đối chiếu với kỳ vọng:

| Câu | Đường cũ không cổng cho ra | Đúng phải là |
|---|---|---|
| "Từ 9h sáng đến 2h chiều làm việc" | 09:00 đến 02:00, 1.020 phút, tiêu đề "Chiều làm việc" | 09:00 đến 14:00 (300 phút) |
| "Làm việc từ 10h tối đến 2h sáng" | 10:00 đến 14:00 | 22:00 đến 02:00 |
| "Học 2,5 tiếng" | 300 phút, danh mục "other" | 150 phút |
| "Học 1 giờ 30" | 60 phút, tiêu đề "Học 30" | 90 phút |
| "Ngày mai 9h họp 1 tiếng" (H5) | bỏ mất 9h | họp 09:00 đến 10:00 ngày mai |
| "Code từ 14h đến giờ, đọc sách 30p" (H7) | chỉ còn "Đọc sách", MẤT "Code" | hai đề xuất |
| "Hủy hợp đồng 2h" (E5) | danh mục "other" | công việc |
| "Tôi sẽ học 2 tiếng" | ghi như đã học xong hôm nay | kế hoạch hoặc hỏi lại |
| "Họp từ 2h đến 4h", "Họp 9h" | 02:00 đến 04:00; 540 phút | hỏi lại AM/PM, hỏi giờ hay thời lượng |
| "Code từ 14h đến giờ" (H1, H2, H4) | không hỗ trợ ("thiếu thời lượng") | khoảng giờ đến hiện tại |

(Parser tự do nhanh nhưng sai một cách im lặng ở những câu này. Cổng của router biến "im lặng sai" thành "chuyển cho AI".)

## 5. Dữ liệu động và an toàn

### 5.1 Dữ liệu động: cache theo câu vs mẫu có tính lại khe

(i) **Cache kết quả theo câu** (khóa = câu đã chuẩn hóa → kết quả ĐÚNG lúc lưu ở C1), phát lại trên 128 câu × 5 đồng hồ = 640 lượt:

| Phát lại ở | Cache cũ/sai | Tỉ lệ | Router cục bộ đúng | Router cục bộ sai | Router escalate an toàn |
|---|---|---|---|---|---|
| Cùng ngày +1 giờ (17:30) | 17/128 | 13,3% | 114 | 0 | 14 |
| Hôm sau (Thứ Năm 16:30) | 109/128 | 85,2% | 114 | 0 | 14 |
| Tuần sau (Thứ Hai 16:30) | 121/128 | 94,5% | 114 | 0 | 14 |
| C2 (Thứ Bảy 21:45, tháng sau) | 128/128 | 100% | 112 | 0 | 16 |
| C3 (Thứ Hai 08:05, năm sau) | 128/128 | 100% | 100 | 0 | 28 |
| **Tổng** | **503/640** | **78,6%** | | **0** | |

Ngay cả câu thời lượng đơn như "Đọc sách 30 phút" cũng thành sai từ hôm sau, vì đề xuất lưu `dateLocal` = hôm nay của lúc lưu (224/280 lượt phát lại của loại này cũ/sai). Mọi câu "đến giờ" cũ ngay trong cùng ngày (giờ kết thúc đã đổi: 80/80). Câu truy vấn: 35/36 câu có câu trả lời THAY ĐỔI sau khi sổ cái thêm 10 bản ghi đã xác nhận ⇒ cache lời đáp sẽ hiện số cũ. Với router, `QueryIntent` được dựng lại ở mỗi lần gửi và số do `LedgerQueryEngine` tính lúc trả lời: 36/36 khớp tuyệt đối số liệu mới của engine.

(ii) **Mẫu + khe tính lại tại thời điểm gửi** = chính router: bảng trên (đúng 114/114, 112/112 và 100/100, sai 0 ở mọi đồng hồ; cột cuối tăng so với gate-1 vì cổng chặt hơn). Lưu ý của kiểm toán: tổng 503/640 = 78,6% là trung bình 5 đồng hồ phát lại do người dựng chọn (chỉ cùng ngày: 13,3%; bỏ hai đồng hồ tháng/năm sau: 64,3%); kết luận không phát lại kết quả theo câu vẫn đúng vì mọi câu "đến giờ" cũ ngay trong ngày (80/80).

### 5.2 An toàn ghi sổ (kiểm bằng test, không chỉ lập luận)

Lập luận: router không có tham chiếu tới DAO/ledger (hàm thuần trả về `ParseChatOutcome`), nên định tuyến không thể ghi. Mọi đề xuất cục bộ vẫn phải qua nút Xác nhận và đường `BatchConfirm` có sẵn.

Đã test trên 125 kết quả LOCAL (C1) và các kết quả LOCAL ở C2/C3, 0 vi phạm:

* mọi đề xuất `needsConfirmation = true`, không có lời đáp tự do (`reply = null`), đúng một đề xuất hoặc một truy vấn;
* mỗi truy vấn qua `AiQueryValidator.validate` = Ok, ≤ 93 ngày;
* **tương đương validator Cloud**: kết quả LOCAL được tuần tự hóa đúng như JSON mà AI sẽ trả rồi chạy qua `GeminiProposalClient.parseAndValidateResponseJson` thật (StrictJson, AiProposalGuard, ReplyGuard): ra y hệt, không cảnh báo;
* đường xác nhận thật: 84 kết quả ghi nhận được đưa qua `EditableProposalDraft` + `BatchConfirm` + DAO trong RAM: ghi đúng 1 dòng/đề xuất (84/84 Committed), trước xác nhận 0 dòng, xác nhận lần hai 0 dòng thêm (0 trùng);
* các họ phủ định/sửa lại/chèn lệnh/hỗn hợp/chit-chat/thiếu dấu: 0 câu cục bộ ở mọi đồng hồ.

### 5.3 Độ trễ đo thật của router (JVM)

Câu cục bộ (125, gate-2): p50 0,94 ms, p95 2,0 ms, tối đa 2,7 ms (trung vị 25 lần gọi nóng; lần chạy gate-1: 0,57 / 1,6 / 3,0 ms, máy đang dùng chung nên dao động theo tải). Câu bị escalate (212): p50 0,03 ms, p95 0,34 ms (phần cộng thêm vào đường Cloud, không đáng kể so với ~9 s). Lần gọi đầu trong JVM mới: 0,16 đến 0,29 s qua các lần chạy (nạp lớp + biên dịch regex), nên khởi động nóng trong cùng chỗ đã khởi tạo guard ngoài luồng chính. Android chưa đo (không được dùng emulator): giả định ≤ 20 ms/lần nóng.

## 6. Kinh tế: giả định và độ nhạy

* Nền (đề bài): p50 8,5 s, điển hình 6 đến 17 s, 5 đến 7% quá hạn 30 s; ~4.400 đến 5.300 token prompt (lấy 4.850) + ~150 token ra + ~500 token suy luận ẩn; chi phí 0,0025 USD/yêu cầu cận trên, ~0,001 theo dashboard; cache nhà cung cấp không báo (cached_tokens = 0/NOT_REPORTED).
* Mô hình trễ Cloud: 94% log-chuẩn (trung vị 8,5 s, sigma 0,45) + 6% chạm hạn 30 s ⇒ p50 8,8 s, p90 18,4 s, p95 30,0 s (đối chiếu: log live tại thời điểm phân tích, n=107, p50 9,3 s, p95 21,5 s, 5 lượt hết hạn; file này vẫn đang được tiến trình QA ghi thêm nên chỉ dùng để đối chiếu). Router: 20 ms mỗi câu cục bộ.
* Giả định chính, nêu rõ: câu cục bộ có cùng xác suất chạm hạn/lỗi mạng như câu Cloud (nếu câu đơn giản ít lỗi hơn thì mức tiết kiệm timeout nhỏ hơn; KIỂM TOÁN: log live cho 0/19 timeout ở câu đủ điều kiện cục bộ và 6/68 ở câu còn lại, nên giả định này KHÔNG được ủng hộ, đọc timeout tiết kiệm là "0 đến mức bảng"); chi phí tuyến tính theo số lượt Cloud. Kiểm toán cũng ghi: trung điểm 4.850 token cao hơn trung bình đo 4.455; nền p50 của mô hình là 8,8 s chứ không phải 8,5 s; kịch bản 60% vượt trần của mix đề bài (RECORD + QUERY = 70%); số "20 đến 29%" của phương án C dùng giá giả định cho chi phí 0,00308 USD mỗi yêu cầu, cao hơn cận trên 0,0025 của đề bài (chi tiết: ROUTING_AUDIT mục 5).
* Kịch bản đo: catalogue 25,0%; corpus mới 38,9%; gộp 37,1%; gói nhỏ nhất (S1+S2+S3) 34,7% trên 337 và 22,7% trên catalogue; độ nhạy 20/40/60%. Số đầy đủ từng kịch bản (cả khi tính trên log live): `routing_economics.json`.

Cục bộ hóa theo dạng (số câu cục bộ trên 337): ghi thời lượng đơn 57, truy vấn đơn 34, so sánh 7, "đến giờ" 19, khoảng giờ tường minh 8.

| Gói | Coverage (337) | Coverage (catalogue) |
|---|---|---|
| S1 ghi thời lượng đơn | 16,9% | 4,5% |
| S1 + S2 truy vấn kỳ lịch (+ so sánh) | 29,1% | 15,9% |
| S1 + S2 + S3 "đến giờ" (gói đề xuất) | 34,7% | 22,7% |
| + S4 khoảng giờ tường minh (nguyên mẫu đầy đủ) | 38,9% | 25,0% |

S4 chỉ thêm 2,4 điểm coverage nhưng mang logic AM/PM nhạy cảm nhất và kéo thêm hai lỗi tiêu đề của parser; bỏ ra khỏi v1.

## 7. So sánh phương án (trên bằng chứng)

| | (A) Cache kết quả theo câu | (B) Router Tier-0 + tính lại khe | (C) Prompt-cache của nhà cung cấp | (D) Giữ nguyên |
|---|---|---|---|---|
| Giảm lượt Cloud | = tỉ lệ lặp câu, CHƯA ĐO (không có dữ liệu lặp thật) | 25% (catalogue) đến 39% (corpus), đo được | 0 | 0 |
| Độ trễ / timeout | câu trúng cache nhanh | câu cục bộ ~ms, timeout giảm theo coverage | gần 0 (tiền xử lý prompt chỉ là phần nhỏ của ~9 s, chủ yếu là suy luận) | không đổi |
| Đúng với dữ liệu động | **78,6% lượt phát lại cũ/sai**; muốn đúng phải khóa theo ngày/giờ/sổ cái (khi đó thực chất là B) | 0 sai trên 5 đồng hồ | đúng (vẫn gọi AI) | đúng |
| Sao chép lỗi của AI | **có**: phát lại nguyên văn lỗi (A5: AI hỏi lại thay vì đề xuất; E6: hỏi lại), cả biến thể gần | không (không đọc đầu ra AI) | không | không |
| Chi phí tiền | tiết kiệm theo hit-rate | ~0,4 đến 1,0 USD / 1.000 yêu cầu | tối đa ~20 đến 29% chi phí (giá niêm yết GIẢ ĐỊNH 0,30/2,50 USD mỗi triệu token, giảm 50 đến 75% cho phần tiền tố 4.000 token) nếu nhà cung cấp thật sự bật; hiện KHÔNG quan sát được (cached_tokens = 0/NOT_REPORTED ở mọi yêu cầu) | 0 |
| Mã/tác động | trung bình, cần bộ nhớ, hết hạn, bảo mật | ~370 dòng sản xuất | không đổi mã app (cấu hình/nhà cung cấp) | 0 |
| Bằng chứng | thiếu | tốt cho độ đúng/động; yếu cho coverage thật | không có (offline không đo được) | có |

Kết luận: B là phương án duy nhất giảm trễ/timeout/lượt gọi mà vẫn đúng trên dữ liệu động và không học lỗi AI. C bổ sung (rẻ hơn cho phần còn lại) nhưng chưa có bằng chứng và không đụng vào độ trễ; nên hỏi nhà cung cấp/gateway riêng, không tính vào lợi ích của B. A bị loại.

## 8. Thay đổi nhỏ nhất nên làm: "Tier-0 Router v1" sau cờ tắt

### 8.1 Nội dung

Ba dạng: S1 ghi thời lượng đơn, S2 truy vấn kỳ lịch (đơn + so sánh hai kỳ), S3 "từ Xh đến giờ". Cùng cổng G0 đến G10, trừ nhánh khoảng giờ tường minh.

### 8.2 File và hàm sản xuất sẽ chạm

| File | Thay đổi | Ước lượng |
|---|---|---|
| NEW `domain/usecase/Tier0Router.kt` | `object Tier0Router { fun route(text, today, now): Decision }`, port từ nguyên mẫu (bỏ nhánh khoảng giờ, đổi mọi `Regex(` còn lại sang `Rx.uni`) | ~300 dòng |
| `ui/TimeWalletViewModel.kt` `analyzeChatInput()` | giữa nhánh NOT_CONFIGURED (~dòng 807) và `chatRunner.submit` (~dòng 812): nếu cờ bật và router trả LOCAL thì `chatRunner.submit(text) { outcome }` (vẫn đi qua vé/epoch/hủy/chống bấm đúp của `WallyChatRunner`), ngược lại gọi `GeminiProposalClient.analyzeInput` như cũ | ~15 dòng |
| `domain/usecase/AiFault.kt` | `OutcomeSource.ROUTER`; `WallyIntentClassifier.classify` ánh xạ ROUTER giống CLOUD cho RECORD/QUERY | ~4 dòng |
| `data/diagnostics/AiDiagnostics.kt` | đếm `routerLocal` (không lưu nội dung câu); hiển thị trong chẩn đoán | ~10 dòng |
| `domain/usecase/WallyAiGuards.kt` (`WallyAnswerBuilder`) và `WallyTurnPolicy.buildCard` | nhãn thẻ truy vấn theo `outcome.source` (cục bộ không được gắn nhãn "Wally (AI)") | ~8 dòng |
| `data/local/TimerAndSettingsStore.kt` (`AiPreferences`) + hộp thoại cấu hình AI trong `ComponentsAndDialogs.kt` | cờ `localRouterEnabled` (mặc định false) + công tắc "Trả lời nhanh trên máy (thử nghiệm)" | ~25 dòng |
| `data/ai/GuardHealth.kt` | thêm probe khởi tạo router (bài học P0 F6: regex lỗi trên ICU thì tắt router, không sập app) | ~8 dòng |

Tổng ≈ 370 dòng sản xuất. Test: chuyển corpus + bộ an toàn + bộ biến dị sang `V1013Tier0RouterTest` (~600 dòng, harness hiện có làm khung), thêm vào quét tĩnh "không `Regex(`" và test "router không tham chiếu DAO".

### 8.3 Kế hoạch kiểm thử

1. JVM: toàn bộ harness hiện tại (corpus, tương đương validator Cloud, đường xác nhận thật, biến dị, tổ hợp, 5 đồng hồ) làm cổng CI; thêm test cờ tắt ⇒ hành vi y hệt v1.0.12 (hồi quy).
2. Chế độ bóng trên máy Owner: router chạy nhưng vẫn gọi Cloud; ghi số khớp/không khớp (so bộ `(tiêu đề, danh mục, thời lượng, ngày, giờ)` của router với đề xuất AI sau guard; chỉ ghi đếm và tên lý do, không ghi câu). Mục tiêu: ≥ 100 câu thật, khớp ≥ 99% trong số router chọn LOCAL, 0 trường hợp lệch nghiêm trọng, đo coverage thật.
3. Android thật (ADB/AVD theo quy trình QA hiện có, sau khi được phép): đo độ trễ cục bộ, lần gọi đầu, chế độ máy bay.

### 8.4 Triển khai và hoàn tác

Cờ mặc định TẮT; bật theo thứ tự: chế độ bóng, rồi LOCAL trên máy Owner, rồi mặc định BẬT sau khi Live Eval đạt. Hoàn tác = gạt công tắc tắt (không có dữ liệu lưu, không migrate). Tự tắt khi probe `GuardHealth` của router lỗi.

### 8.5 Phải kiểm lại (Live Eval v1.2 + Phụ lục v1.0.12)

* Nhóm A (A1, A4 sang đường cục bộ; A2, A5 đến A10 vẫn Cloud), B (B1, B3, B4, B5, B7 cục bộ; B2, B6, B8 vẫn Cloud), H (H1, H2, H4 cục bộ; H3 vẫn Cloud nếu bỏ S4; H5 đến H7 Cloud). Trong gói S1+S2+S3, H3 ("Học từ 8h sáng đến 11h") là khoảng giờ tường minh nên quay về Cloud: coverage catalogue 22,7%.
* Nhóm C, D, E5, E6, G: phải vẫn LÊN Cloud (`mustReachCloud`), ý định BLOCKED/CHAT/CLARIFY như cũ.
* Bất biến S1 (không ghi trái phép), S2 (không ghi trùng, bấm đôi), S4 (số liệu đúng sổ cái, nay có thẻ từ đường cục bộ), S8 (không "báo thành công" giả), S7 (draft đã hủy không hồi sinh), T5 (báo cáo p50/p95 TÁCH đường cục bộ và Cloud), nhóm F (mạng lỗi: câu cục bộ không bị ảnh hưởng, hành vi MỚI).
* Mục mới "R": nhãn thẻ truy vấn cục bộ; xen kẽ với đề xuất đang chờ (`TRANSACTION_CHAT`); cờ tắt thì y hệt.

### 8.6 KHÔNG nằm trong thay đổi này

Cache bất kỳ kiểu nào; học từ đầu ra AI; trò chuyện/lời chào cục bộ; khoảng giờ tường minh (S4); "lúc Xh + thời lượng"; nhiều mệnh đề (kể cả "Học 45p, đọc sách 30p": là ứng viên cho v2 vì parser xử lý được, nhưng chính cơ chế tách mệnh đề này đã làm MẤT mệnh đề "Code" ở H7 và đọc sai dấu phẩy thập phân, cần thiết kế riêng); câu hỏi nối tiếp; bỏ cổng NOT_CONFIGURED để dùng không cần khóa (quyết định sản phẩm riêng); đổi `cleanTitle` của parser; chạy prompt-cache của nhà cung cấp (việc cấu hình riêng).

## 9. "Không học điều AI hiểu sai"

Thiết kế khuyến nghị không học gì: `Tier0Router.route` là hàm thuần của `(câu, hôm nay, giờ)`; không có biến trạng thái, không ghi đĩa, không đọc `ParseChatOutcome` do AI trả. Cái duy nhất "đã biết" là kho từ khóa tĩnh trong mã (được review như mọi mã khác).

Biến thể "mẫu học được" sẽ có rủi ro cụ thể, có bằng chứng ngay trong dữ liệu:

* sao chép lỗi AI: ở lần chạy live, A5 "Ngủ lúc 10h tối 8h" bị AI trả lời bằng câu hỏi lại thay vì đề xuất (TRƯỚC KHI có cổng, parser cục bộ đọc đúng 22:00 đến 06:00); E6 "Bỏ ghi chú 20 phút" cũng bị hỏi lại. Mẫu/cache học từ đó sẽ phát lại lỗi vô thời hạn và khái quát sang biến thể gần;
* nhiễm độc: nội dung học được do văn bản người dùng quyết định, kể cả câu kiểu "bỏ qua xác nhận";
* cũ theo thời gian: 78,6% lượt phát lại ngoài C1 là cũ/sai (mục 5.1);
* riêng tư (lưu câu của người dùng), lệch phiên bản (đổi `PROMPT_VERSION`/model phải xóa), cần dọn dẹp.

Nếu sau này muốn học, chỉ nên học các bản ghi do người dùng đã bấm Xác nhận mà không sửa, có tính lại khe, qua cùng cổng, và cần Owner duyệt riêng; không nằm trong đề xuất này.

## 10. Giới hạn và chỗ bằng chứng còn mỏng

1. Corpus do một tác giả viết cùng với cổng. Đã giảm thiên lệch (nhãn viết trước router, cách tính độc lập, kỳ vọng catalogue của Manager, bộ biến dị và tổ hợp tự sinh) nhưng KHÔNG có tập mù và KHÔNG có lưu lượng người dùng thật. 100% là số trên tập này, không phải lời hứa trên thực tế.
2. Coverage thật chưa biết: catalogue là mốc thấp (chọn câu cần AI), corpus mới là mốc dựng. Kinh tế dùng dải 20/40/60%; chế độ bóng là cách đo thật.
3. Độ trễ cục bộ chỉ đo trên JVM máy tính; Android chưa đo. Nền Cloud lấy từ đề bài (~100 yêu cầu); mô hình trộn là mô hình, không phải đo trực tiếp.
4. Chi phí tuyến tính theo số lượt Cloud (cận trên 0,0025 và 0,001 từ đề bài); không có bảng giá thật. Phần (C) dùng giá niêm yết giả định và không kiểm chứng được.
5. Danh mục cục bộ theo bảng từ khóa của chính app, AI có thể gán khác (đã loại 4 từ khóa gây tranh cãi: python, gọi điện, đi làm, khách hàng, và "chơi" trơn). Tỉ lệ bất đồng thật với AI chưa đo.
6. "Hybrid trên catalogue 41/44" dùng kết quả AI của MỘT lần chạy live trước đó, không chạy lại.
7. Nguyên mẫu nằm trong cây test, còn dùng `Regex(` thuần ở vài chỗ phụ; bản sản xuất phải dùng `Rx` và có probe `GuardHealth` (khác biệt ICU/JDK chưa thử trên Android).
8. Chưa kiểm tích hợp UI/ViewModel (nhãn thẻ, xen kẽ đề xuất đang chờ): đã nêu thành hạng mục kiểm.
9. Cổng có 17 false-escalate (mục 4.3); mỗi phần mở rộng kèm rủi ro mới nên đề xuất không làm trong v1.
10. Quá trình: chạy tuần tự trong một workspace Gradle (luật: mỗi lần một build, `--max-workers=2`), nên bước này không song song hóa được phần chạy; phần viết corpus và phần phân tích có thể giao cho hai tác tử khác nhau nhưng vẫn dùng chung một bản build.

## 11. Phụ lục: file và cách chạy lại

* `routing_corpus.json`, `routing_results.json` (mỗi câu: quyết định, lý do, kết quả chấm, thời gian, và đầu ra của đường cũ không cổng), `routing_summary.csv` (theo họ và loại, catalogue tách riêng), `routing_dynamic.json`, `routing_economics.json`, `routing_timing.json`, `routing_synthetic_metamorphic.json`, `routing_synthetic_combinatorial.json`, `routing_ledger_integrity.txt`, `routing_safety_problems.txt` (NONE).
* Harness trong `route_ws/app/src/test/java/com/timewallet/mvp/routing/` (`Tier0Router.kt`, `RoutingCorpus.kt`, `RoutingEvalTest.kt`, `RoutingSyntheticTest.kt`) và `route_ws/econ/economics.py`.
* Chạy lại: `JAVA_HOME="C:\Program Files\Java\jdk-22"; ./gradlew testDebugUnitTest --offline --rerun --max-workers=2 --tests "com.timewallet.mvp.routing.*"` rồi `python -X utf8 -I economics.py`.
* Kiểm toán độc lập: `ROUTING_AUDIT.md`, `routing_audit_blind.json`, `routing_audit_adversarial.json`, `routing_audit_recomputed_before.json` / `_after.json`, `audit_scripts/` (gồm `tier0router_gate1_to_gate2.diff`, bộ 263 câu đối kháng, nhãn mù, `AuditAdversarialTest.kt`).
