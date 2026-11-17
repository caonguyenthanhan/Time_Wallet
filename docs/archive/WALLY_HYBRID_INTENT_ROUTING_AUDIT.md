# Kiểm toán độc lập đánh giá Hybrid Intent Routing (gate-1 của người dựng, gate-2 sau kiểm toán)

Người kiểm toán không viết đánh giá gốc. Không gọi mạng, không emulator/adb, không đụng `app/src/main` (đã kiểm: `diff -rq` với `source_v1.0.12.tar` = giống hệt; `git status` của repo không đổi). Mọi sửa chữa nằm trong cây test của `route_ws` (prototype) và thư mục `evidence/routing`. Bản gốc trước khi sửa được giữ ở `route_ws/audit/before/`.

## 0. Kết luận

1. **Số liệu gốc tính lại độc lập: KHỚP** (mục 3). Không có sai số học nào trong coverage, độ chính xác, false-escalate, 503/640 cache cũ, bảng kinh tế.
2. **Nhãn mù: 83/86 = 96,5% trùng, kappa 0,929** (mục 2). Ba bất đồng, không cái nào ảnh hưởng an toàn. Trên 86 câu mù, router gốc không có false-local.
3. **Nhưng "false-local = 0, độ chính xác 100%" là kết quả của một corpus do người dựng viết, không phải bằng chứng cổng kín.** Bộ 263 câu tấn công do kiểm toán viết (189 câu phải lên Cloud) làm cổng gốc để lọt **68 câu, trong đó 57 là lỗ hổng xác nhận** (mục 4): "Chạy bộ 5h30" ghi 330 phút, "Ăn sáng 6h30" ghi 390 phút, "Họp từ 2h đến 15h" ghi 13 giờ, "Hôm nay tôi tập bao nhiêu phút?" trả TỔNG mọi danh mục, "Học trong 2 tiếng" ghi như đã học. Đã vá 9 lớp (AF1 đến AF9), chạy lại: **0 lỗ hổng xác nhận**. Giá của bản vá: coverage 38,9% xuống 37,1% (131 xuống 125 câu cục bộ), false-escalate 11 lên 17 (nhãn giữ nguyên).
4. **Kinh tế: phép tính đúng, nhưng 5 giả định không có chứng cứ hoặc mâu thuẫn với đề bài** (mục 5). Đáng kể nhất: nhật ký live cho thấy 0/19 yêu cầu của câu "đủ điều kiện cục bộ" bị timeout, 6 timeout đều thuộc 4 câu khác (B6, B8, E5, G7). Con số "60 xuống 37 timeout trên 1.000 yêu cầu" không được dữ liệu ủng hộ; trung thực là "0 đến 22".
5. **Khuyến nghị B vẫn là thay đổi nhỏ nhất và lập luận an toàn đứng vững** (mục 6), với điều kiện: dùng gate-2, cờ mặc định TẮT, chạy bóng trước, bỏ lời hứa giảm timeout, thêm 3 hạng mục tích hợp mà harness chưa phủ (nguồn `ROUTER` thay vì `LOCAL`, chạy ngoài luồng Main, nhãn thẻ).

## 1. Trước và sau (cùng harness, cùng corpus 337 câu; nhãn KHÔNG đổi)

| Chỉ số | Gate-1 (người dựng) | Gate-2 (sau kiểm toán) |
|---|---|---|
| Coverage catalogue 44 | 11/44 = 25,0% | 11/44 = 25,0% |
| Coverage corpus mới 293 | 120/293 = 41,0% | 114/293 = 38,9% |
| Coverage gộp 337 | 131/337 = 38,9% | 125/337 = 37,1% |
| Gói nhỏ nhất S1+S2+S3 trên 337 | 122/337 = 36,2% | 117/337 = 34,7% (catalogue vẫn 22,7%) |
| Độ chính xác phần cục bộ (corpus) | 131/131 | 125/125 |
| False-local trên corpus gốc | 0 | 0 |
| False-escalate (nhãn EXPECT_LOCAL_OK) | 11/142 = 7,7% | 17/142 = 12,0% |
| Đồng hồ C2 / C3 (95 câu động): cục bộ, đúng | 70, 70 / 54, 54 | 67, 67 / 53, 53 |
| Đối chiếu độc lập của kiểm toán (mọi câu LOCAL, 3 đồng hồ) | không sai | 125 / 67 / 53 câu, 0 sai |
| Biến dị (phải lên Cloud): còn cục bộ | 0/2.172 | 0/2.172 |
| Biến dị vô hại giữ nguyên kết quả | 360/360 | 342/342 |
| Tổ hợp kho từ khóa x 4 cách viết giờ | 252/252 cục bộ, đúng | 217 cục bộ, đúng + 35 ("Cà phê 2h"...) chuyển Cloud theo luật mới, 0 sai |
| Hoạt động lạ / từ khóa tranh cãi lên Cloud | 98/98 | 98/98 |
| Bộ đối kháng 263 câu (189 phải lên Cloud): lọt cục bộ (bất kỳ đồng hồ) | 68 (57 lỗ hổng xác nhận) | 13 (0 lỗ hổng xác nhận) |
| Câu đối kháng "chấp nhận cục bộ" vẫn cục bộ ở C1 | 70/74 | 65/74 |
| Ghi sổ qua đường xác nhận thật | 90/90, 0 trùng | 84/84, 0 trùng, 0 ghi trước xác nhận |
| Hybrid trên catalogue (AI thật cho câu escalate) | 41/44 = AI thuần 41/44 | 41/44 = AI thuần 41/44 |

## 2. Bước 1: nhãn mù

Mẫu 86 câu từ 293 câu mới (phân tầng theo `type`, tối thiểu 2 câu mỗi loại, hạt giống 20261116), kiểm toán viết nhãn LOCAL/ESC từ đặc tả (mục 3.1 của báo cáo gốc) khi chưa thấy nhãn, loại, lý do của người dựng và quyết định của router. Sau đó mới đối chiếu (`routing_audit_blind.json`).

| | Số câu |
|---|---|
| Trùng nhãn | 83/86 (96,5%), kappa 0,929 |
| Router gốc: LOCAL mà nhãn của kiểm toán là phải-lên-Cloud | 0 |
| Router gốc: ESCALATE mà nhãn của kiểm toán là cục bộ-được (false-escalate theo nhãn kiểm toán) | 5 (nhãn của người dựng cho 6 trong mẫu này; khác nhau ở 2 câu "lúc Xh" và câu "rồi") |

Ba câu bất đồng và phán quyết:

| Câu | Người dựng | Kiểm toán | Ai đúng |
|---|---|---|---|
| "Tuần này tôi học bao nhiêu giờ rồi?" | MUST_ESCALATE (vì "rồi" là từ nối của cổng) | cục bộ | **Kiểm toán.** Nhãn bị rò từ cổng (lý do ghi trong corpus là "connector marker in the minimal gate"); đây là nhãn duy nhất trong 165 nhãn MUST_ESCALATE được biện minh bằng chính cổng. Hậu quả: false-escalate bị đếm thiếu 1, không ảnh hưởng an toàn. |
| "Lúc 20h học 45p" | EXPECT_LOCAL_OK | thận trọng (ESC) | **Kiểm toán, thận trọng.** 20h chưa tới lúc 16:30: ghi đã làm hay kế hoạch? Nhãn người dựng làm phồng false-escalate. |
| "Ngủ lúc 22h 8 tiếng" | EXPECT_LOCAL_OK | thận trọng (ESC) | **Kiểm toán, thận trọng.** Vắt qua nửa đêm + đã làm/kế hoạch không rõ; chính A5 cùng họ câu này AI thật còn trượt. |

Giới hạn: mẫu lấy từ chính corpus của người dựng nên chỉ chứng minh nhãn nhất quán với đặc tả, không chứng minh phủ hết cách người dùng viết. Phần đó do bước 3 đảm nhiệm.

## 3. Bước 2: tính lại độc lập (`audit_scripts/recompute.py`, đọc `routing_results.json` + corpus)

Không dùng lại hàm nào của người dựng. Mỗi câu LOCAL ở cả ba đồng hồ còn được chấm lại bằng một oracle regex Python viết riêng (phút, ngày, kỳ truy vấn, giờ bắt đầu theo giờ gửi, cờ planned).

| Số của báo cáo gốc | Tính lại | Kết quả |
|---|---|---|
| 337 câu, 44 + 293, 4 lệnh hủy ngoài thống kê | 337, 44, 293, 4 | khớp |
| Coverage 25,0% / 41,0% / 38,9% | 11/44, 120/293, 131/337 | khớp |
| False-escalate 3/14, 8/128, 11/142 | cùng số, cùng 11 id | khớp |
| False-local 0; độ chính xác 100% | 0; oracle độc lập không bất đồng với `graded_correct` ở câu nào | khớp |
| Theo dạng: 62 / 34 / 7 / 19 / 9 | cùng | khớp |
| Lý do escalate (tổng = 206) | tổng khớp | khớp |
| Cache theo câu cũ/sai: 503/640 = 78,6% (17, 109, 121, 128, 128) | cùng | khớp |
| Kinh tế 1.000 yêu cầu: 611 lượt, 1,53/0,61 USD, 37 timeout, p50 5,8, p95 20,9 | 611,3; 1,528/0,611; 36,7; 5,76; 20,83 (hàm phân vị đóng, không lấy mẫu) | khớp |
| Hybrid 41/44 | 41/44 | khớp |

Sai lệch DUY NHẤT trong cách trình bày, không phải số học: "78,6% cũ/sai" là trung bình của 5 đồng hồ phát lại do người dựng chọn (1 trong cùng ngày, 4 từ ngày hôm sau trở đi). Chỉ tính phát lại cùng ngày là 13,3%; bỏ hai đồng hồ tháng/năm sau là 64,3%. Kết luận (không thể phát lại kết quả theo câu) vẫn đúng vì mọi câu "đến giờ" cũ ngay trong ngày (80/80), nhưng con số 78,6% không nên được trích như tỉ lệ thực.

## 4. Bước 3: bộ đối kháng (`routing_audit_adversarial.json`, `audit_scripts/make_adv.py`)

263 câu (189 phải lên Cloud, 74 chấp nhận cục bộ), chạy qua router ở 6 đồng hồ (C1 đến C3 của báo cáo + 00:20, 12:10, Chủ nhật 23:50). Phủ: phủ định giấu trong mệnh đề, "không" trong danh từ, đừng/chưa/chẳng/chả/hổng/k/hem/đâu, số viết chữ và "rưỡi", nhầm ngôi, từ ngày đổi nghĩa, truy vấn thiếu danh mục hoặc kỳ, câu giống ghi nhưng là sửa lại, "hôm qua" và "ngày mai", "đến giờ" không có điểm bắt đầu, "đến giờ họp" (đến giờ + danh từ), chữ số Ả Rập/toàn góc, NBSP, ký tự điều khiển.

**Cổng giữ vững (không lỗ hổng):** phủ định/sửa lại/meta (49 câu nhóm N, C, I, X, P: 0 lọt), mọi biến thể "đến giờ + danh từ" (14 câu như "Code từ 14h đến giờ họp", "Họp từ 14h đến giờ họp xong": cổng chặn bằng `OPEN_DISAGREES` vì hàm của guard không đọc được điểm bắt đầu), nhầm ngôi (trừ "Mai" trần), số Ả Rập/toàn góc, chuỗi dài, xuống dòng. Đây là phần mạnh thật của thiết kế "mọi token phải được giải thích".

**Lỗ hổng xác nhận ở cổng gốc (57 câu, 9 lớp) và bản vá:**

| Lớp | Ví dụ lọt cục bộ (cổng gốc) | Vì sao sai | Vá (gate-2) | Câu |
|---|---|---|---|---|
| AF1 | "Chạy bộ 5h30" (330 phút), "Ăn sáng 6h30" (390), "Ăn tối 6h" (360), "Họp 3h", "Cà phê 3h", "Gym 5h", "Ngủ 9h" | "Nh" trần nhận N ≤ 6 (ngủ ≤ 9) bất kể hoạt động; với bữa ăn, tập, cà phê, họp, thiền, tắm thì thường là GIỜ TRONG NGÀY | "Nh" trần: hoạt động thuộc life/rest/health/social hoặc họp/báo cáo luôn lên Cloud; còn lại N ≤ 4 (ngủ ≤ 8); đơn vị "giờ/tiếng/phút/p" không đổi | 22 |
| AF2 | "Họp từ 2h đến 15h" (13 giờ), "Học từ 3h đến 15h", "Làm việc từ 5h đến 18h" | quy tắc "một đầu ≥ 13h" chọn đầu nhỏ là SÁNG; thường là 12h/24h lẫn lộn | đầu nhỏ không có buổi phải ≥ 5h và khoảng ≤ 10 giờ; trần mọi khoảng 12 giờ | 10 |
| AF3 | "Hôm nay tôi tập bao nhiêu phút?", "Tuần này tôi tập bao nhiêu giờ?", "Hôm nay tôi học bao nhiêu bài?" | "tập" và "bài" được bỏ qua như từ đệm, danh mục rơi mất: câu trả lời là TỔNG mọi danh mục (số sai âm thầm) | "tập/bài" chỉ bỏ qua khi đã có từ danh mục thật; "bao nhiêu bài/tập" lên Cloud | 6 |
| AF4 | "Hôm nay tôi hết bao nhiêu giờ?", "Hôm nay tôi mất bao nhiêu giờ?", "Hôm nay là bao nhiêu giờ?", "Tuần này tôi tốn bao nhiêu giờ?" | "hết/mất/tốn/là" là từ đệm truy vấn nhưng đổi nghĩa (hết = còn lại, mất = đánh mất) | bỏ 4 từ khỏi từ đệm | 6 |
| AF5 | "Học trong 2 tiếng", "Họp trong 2 tiếng", "Họp xong trong 2 tiếng" | "trong" là từ đệm: "trong 2 tiếng" = sẽ xong trong 2 tiếng (kế hoạch) hoặc đã học 2 tiếng | bỏ "trong" khỏi từ đệm | 4 |
| AF6 | "Ngày mai tôi đã học 2 tiếng" | thì quá khứ + ngày tương lai tự mâu thuẫn, bị ghi thành kế hoạch | dấu quá khứ (đã, vừa, xong, mới) cùng ngày tương lai thì lên Cloud | 2 |
| AF7 | "Làm việc từ 14h đến 18h" lúc 16:30; "Code từ 1h sáng đến giờ" lúc 16:30 (15,5 giờ) | khoảng đang chạy được ghi như đã xong; "đến giờ" 15,5 giờ liền | khoảng đang chạy tại giờ gửi (bắt đầu qua, kết thúc chưa tới) và "đến giờ" > 12 giờ lên Cloud | 2 |
| AF8 | "So sánh hôm nay với tuần này", "Tuần này tôi học nhiều nhất bao nhiêu giờ?" | so ngày với tuần vô nghĩa; "nhiều nhất" kèm danh mục tự mâu thuẫn | hai kỳ phải cùng loại; TOP/LEAST_CATEGORY kèm danh mục lên Cloud | 2 |
| AF9 | "Học 1.30h" (78 phút, có thể là 1h30), "Học 15 tiếng", "Ngủ 14 tiếng" | thập phân khác .5 mơ hồ; 14 đến 15 giờ vô lý | chỉ chấp nhận thập phân ".5"; trần thời lượng 12 giờ (trước là 16) | 3 |

Còn lọt cục bộ sau vá (13 câu): 7 là kỳ vọng của kiểm toán sai hoặc vô hại (S16 "Học mười hai tiếng" = 720 phút rõ đơn vị, B22 "Ngủ 5h", O13, M22, Q40, NBSP, câu kiểm soát), 4 là phán đoán không thuộc router ("Mai" trần = ngày mai hay tên người: D18, P07; "Hôm nay cà phê bao nhiêu?" giá hay thời gian: Q12; "Hôm nay tôi ngủ bao nhiêu tiếng?": giấc ngủ được tính theo ngày bắt đầu nên "hôm nay" chỉ ra giấc ngủ ngắn, đây là quy ước sản phẩm: Q21), 2 chỉ cục bộ ở đồng hồ hợp lý (R11 khi khoảng nằm hoàn toàn trong tương lai/quá khứ; O12 lúc 08:05 và 12:10).

**Giá của bản vá, nói thẳng:** mất 6 câu cục bộ trong corpus gốc: "Gặp gỡ bạn bè 2h", "Họp dự án 1h30", "Đạp xe 1h", "Gym 1h", "Báo cáo 1h" (AF1, quá tay với N = 1 hoặc 2 nhưng thu hẹp theo N lại làm "Họp 2h", "Cà phê 2h", "Ăn trưa 1h" lọt về) và "Làm việc từ 9h đến 17h" lúc 16:30 (AF7, đúng thiết kế; sau 17h vẫn cục bộ). Cách tốt hơn là nới theo từng hoạt động bằng dữ liệu chạy bóng, không phải đoán thêm.

Lưu ý quy mô: tỉ lệ lỗ hổng trên bộ đối kháng (57/189) KHÔNG phải tỉ lệ trên lưu lượng thật; nó chỉ chứng minh cổng gốc có lỗ hổng cụ thể, và ngăn việc trích "false-local = 0" như bảo đảm.

## 5. Bước 4: giả định kinh tế

| # | Giả định của báo cáo gốc | So với đề bài / dữ liệu | Đánh giá |
|---|---|---|---|
| E1 | Timeout giảm theo coverage (60 xuống 37 mỗi 1.000) | Nhật ký live (127 yêu cầu Cloud duy nhất): câu đủ điều kiện cục bộ 0/19 timeout, p50 8,2 s; câu còn lại 6/68 timeout, p50 8,8 s. Cả 6 timeout thuộc 4 câu (B6, B8, E5, G7), E5 và B8 lặp lại ở nhiều vòng, nghĩa là timeout gắn với câu khó. 0/19 chưa đủ để kết luận (cận trên 95% khoảng 15%) | **Không có chứng cứ.** Báo "0 đến 22 timeout tiết kiệm mỗi 1.000 yêu cầu, ước lượng điểm theo log ≈ 0". Giá trị thật của B là độ trễ của câu đơn (khoảng 8 s xuống dưới 50 ms) chứ chưa phải độ tin cậy |
| E2 | Nền p50 8,5 s | Mô hình 94% lognormal(8,5 s) + 6% ở 30 s cho p50 = 8,81 s: bảng "Hiện tại" ghi 8,8 chứ không phải 8,5 | Lệch 0,3 s, mọi hàng "sau" lệch theo. Nhỏ, nhưng không khớp đề bài |
| E3 | p95 baseline 30,0 s và "30,0 xuống 20,9" | p95 nằm đúng trên khối timeout: tỉ lệ timeout 3% cho p95 = 21,3 s; 4% = 24,1; 4,7% (log) = 29,1; từ 5% = 30,0 | p95 là ngưỡng, không bền. Nên báo p50 và tỉ lệ > 10 s; log live cho p95 21,5 s (n=107) đến 22,9 s (n=118) |
| E4 | Token prompt 4.850 (trung điểm đề bài) | Trung bình đo trong log 4.455 (n=111) | Số token "sau" cao hơn thực 8,9% (gate-2: 3,05 triệu theo 4.850, 2,80 triệu theo 4.455 trên 1.000 yêu cầu) |
| E5 | Kịch bản 60% | Mix của đề bài: RECORD 45% + QUERY 25% = 70% trần. Với tỉ lệ cục bộ của corpus (RECORD 70,9%, QUERY 70,6%) coverage = 49,6%; với tỉ lệ của catalogue = 31,5%. Bản thân mix đề bài đến từ lưu lượng QA, không phải người dùng thật | 60% vượt mọi ước lượng hợp lý; vùng đáng tin 30 đến 50% (khớp 37,1% của gate-2) |
| E6 | Phương án C (cache prompt nhà cung cấp) "tối đa 20 đến 29%" | Giá GIẢ ĐỊNH 0,30/2,50 USD mỗi triệu token cho chi phí mô hình 0,00308 USD mỗi yêu cầu, cao hơn cận trên 0,0025 của đề bài 23% | Không dùng làm bằng chứng; ghi "không đo được" như `cached_tokens = 0/NOT_REPORTED` |
| E7 | Độ trễ cục bộ 20 ms trên Android | JVM nóng 0,9 ms (lần chạy 1: 0,57 ms), gọi đầu 0,16 đến 0,29 s. Trường `cold_first_call_us` của harness phụ thuộc thứ tự test (lần chạy gate-2 ra 1,4 ms vì lớp đã nạp); đã sửa JSON giữ số fresh-JVM và ghi chú | Hợp lý nhưng chưa đo trên Android; lần gọi đầu có thể 0,3 đến 1 giây nếu chạy trên Main |
| E8 | Tiền: cận trên 0,0025 và 0,001/yêu cầu, tuyến tính | Đúng như đề bài | Tiết kiệm 0,37 đến 0,93 USD mỗi 1.000 yêu cầu ở 37,1%: không đủ làm lý do. Nên nói "độ trễ", không nói "chi phí" |

Gate-2 trong bảng kinh tế (mỗi 1.000 yêu cầu, coverage 37,1%): 629 lượt Cloud, 3,05 triệu token prompt (2,80 theo log), 1,57 / 0,63 USD, p50 6,0 s, p90 14,9 s, p95 21,3 s, chờ tiết kiệm 3.939 s; timeout 38 NẾU giả định E1 (không chứng minh).

## 6. Bước 5: khuyến nghị có phải thay đổi nhỏ nhất, lập luận an toàn có đứng vững

**Nhỏ nhất?** Đúng so với các phương án A, C, D và so với giữ S4. Hai điểm có thể nhỏ hơn: (a) bỏ công tắc giao diện ở v1, dùng cờ build/remote (khoảng 25 dòng); (b) làm S2 (truy vấn kỳ lịch, 41/337 = 12,2%) trước: không đi qua `AiProposalGuard` và đường xác nhận, nên rủi ro thấp nhất, nhưng không phủ câu ghi của Owner. Ba dạng S1+S2+S3 phủ đúng 4 câu mẫu của Owner nên giữ nguyên. Ước tính "khoảng 300 dòng cho `Tier0Router.kt`" cần cộng thêm khoảng 30 dòng của AF1 đến AF9 (xem `audit_scripts/tier0router_gate1_to_gate2.diff`); AF2 và phần khoảng-giờ của AF7 bỏ được nếu S4 không vào v1.

**Lập luận an toàn: đã kiểm, đứng vững ở những điểm sau.** Router là hàm thuần của `(câu, hôm nay, giờ)`, không biến trạng thái, không tham chiếu DAO, không đọc đầu ra AI (kiểm bằng grep). Đề xuất vẫn `needsConfirmation = true` và đi qua `WallyChatRunner.run` rồi `ProposalLifecycle.mergeIfCurrent` rồi nút Xác nhận như đường Cloud; số liệu truy vấn do `LedgerQueryEngine.execute` tính ở thời điểm trả lời với `System.currentTimeMillis()`; `lastQueryContext` được đặt từ `outcome.queries` bất kể nguồn nên câu nối tiếp ("Còn tuần trước thì sao?") vẫn đi đúng lên Cloud với ngữ cảnh. Dữ liệu động được kiểm ở 6 đồng hồ.

**Chưa đứng vững hoặc harness chưa phủ (phải nằm trong kế hoạch triển khai):**

1. `WallyIntentClassifier.classify` ánh xạ `source == LOCAL` thành `CONTROL`, và `AiDiagnostics` đếm LOCAL là `localControl`. Prototype đặt `intent` trực tiếp nên đường này KHÔNG được chạy trong harness. Cần `OutcomeSource.ROUTER` và một test: RECORD/QUERY từ router không bị phân loại CONTROL (đề xuất gốc đã có, nhưng chưa được kiểm).
2. `analyze` của `chatRunner.submit` chạy trên `viewModelScope` (Main). `route()` lần đầu biên dịch khoảng 25 regex: phải bọc `withContext(Dispatchers.Default)` và khởi động nóng cùng chỗ `GuardHealth.check()` đang chạy ở `init`.
3. Nhãn thẻ "Wally (AI)" (`WallyAiGuards.LABEL_AI`) phải theo nguồn, nếu không câu trả lời cục bộ tự nhận là AI.
4. Chạy bóng phải đo đúng thứ mà cổng khẳng định: tỉ lệ khớp của các câu router chọn LOCAL với đề xuất AI sau guard, tách theo lớp AF1 đến AF9; mục tiêu ≥ 99% khớp trên ≥ 100 câu thật như báo cáo gốc đề ra, nhưng thêm điều kiện 0 lệch nghiêm trọng về phút/ngày/danh mục.
5. Router đặt sau cổng NOT_CONFIGURED nên không có khóa API thì không có đường cục bộ: đúng ý đề bài (API-first), nhưng giảm giá trị khi mất mạng nếu khóa vẫn còn.
6. "Không học từ AI": đúng theo thiết kế; chạy bóng chỉ được ghi đếm và tên lý do, không ghi câu (đã nêu trong báo cáo gốc).

## 7. Rủi ro còn lại

* Bộ đối kháng và corpus cùng một nhóm tác giả "người", dù kiểm toán không đọc corpus trước khi viết nhãn mù; chưa có lưu lượng thật. Chạy bóng là bài kiểm duy nhất cho tỉ lệ thật.
* Vá AF1 theo nhóm hoạt động là thô (mất 6 câu hợp lệ); mới kiểm trên JVM, chưa trên Android (ICU regex).
* Kiểm toán không có AI trong tay: không biết AI thật phân biệt "Chạy bộ 5h30" tốt hơn router hay không. Bằng chứng là "router không được tự quyết câu mơ hồ", không phải "AI luôn đúng".
* Ghi sổ: không phát hiện lối vòng ở cấp harness; tích hợp ViewModel/UI chưa test (mục 6).

## 8. Về tốc độ và chạy song song (câu hỏi của người dùng)

Phần chạy Gradle phải tuần tự (một build một lúc, `--max-workers=2`, máy đang có tiến trình QA thiết bị): 5 lần chạy cho kiểm toán này, mỗi lần 0,7 đến 1,5 phút. Phần tốn thời gian thật là soạn bộ câu và đọc kết quả. Ba việc độc lập không cần Gradle và có thể chạy song song bởi ba tác tử: (1) nhãn mù, (2) tính lại số liệu và kinh tế từ JSON, (3) soạn bộ đối kháng. Chỉ bước chạy router và chạy lại sau vá phải xếp hàng.

## 9. Chạy lại

* Bộ đối kháng: `python audit_scripts/make_adv.py` (ghi `adv_in.json`), chạy test `com.timewallet.mvp.routing.AuditAdversarialTest` (ghi `adv_out.json`), rồi `python audit_scripts/build_audit_artifacts.py`.
* Tính lại độc lập: `python -X utf8 audit_scripts/recompute.py` (ghi `routing_audit_recomputed_*.json`).
* Toàn bộ harness: `./gradlew testDebugUnitTest --offline --rerun --max-workers=2 --tests "com.timewallet.mvp.routing.*"` với `JAVA_HOME=C:\Program Files\Java\jdk-22`, rồi `python -X utf8 -I econ/economics.py`.
* File: `routing_audit_blind.json`, `routing_audit_adversarial.json`, `routing_audit_recomputed_before.json`, `routing_audit_recomputed_after.json`, `audit_scripts/` (mã, câu vào, nhãn mù, diff gate-1 sang gate-2).
