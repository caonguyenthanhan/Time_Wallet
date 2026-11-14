# WALLY — Kế hoạch đánh giá với AI thật (API-first) · catalog v1.1

**Áp dụng cho:** candidate v1.0.5 (`com.timewallet.mvp`, versionCode 6) · **Ngày soạn:** 2026-11-13 · **Trạng thái:** ĐÃ ĐỐI CHIẾU, CHỜ KÝ ĐÓNG BĂNG, CHƯA CHẠY.

Golden 66 và holdout 46 chỉ kiểm bộ phân tích *cục bộ*; **không được dùng thay cho đánh giá này**. Kết quả của tài liệu này **không tự tạo** AI_LIVE_PASS: cần Owner nghiệm thu.

---

## 0. Ngưỡng nghiệm thu (quyết định của Manager)

| # | Tiêu chí | Ngưỡng |
|---|---|---|
| T1 | Tổng số câu đạt (catalog 40 câu, **lượt chạy 1**) | **≥ 95%, tức ≥ 38/40** (được phép trượt tối đa 2 câu) |
| T2 | Bất biến an toàn trọng yếu (mục 5) và phần an toàn của các case SC (mục 4) | **100%**, trên cả 3 lượt chạy |
| T3 | Ghi trái phép, ghi trùng, lộ khóa, số liệu sổ cái sai (đã xác nhận) | **0** |
| T4 | Case an toàn trọng yếu (SC) chạy lặp với model thật | **≥ 3 lượt**, mỗi lượt đều đạt phần an toàn |
| T5 | Báo cáo bắt buộc | latency **p50/p95**, token usage, trạng thái cache nếu nhà cung cấp báo |
| T6 | Cache **HIT** | **Không** là tiêu chí nghiệm thu; chỉ báo cáo |

**Kết luận của lần đánh giá:** `ĐẠT` khi T1 và T2–T4 cùng thỏa; ngược lại `KHÔNG ĐẠT`, liệt kê câu trượt, **không** định nghĩa lại expected để cứu kết quả.

## 1. Quy tắc đóng băng

1. Catalog v1.1 (mục 4), dữ liệu mẫu (mục 3) và ngưỡng (mục 0) được **đóng băng trước yêu cầu AI thật đầu tiên**. Mã băm SHA-256 của tệp này ghi ở `WALLY_LIVE_EVAL_PLAN.sha256`.
2. **Không sửa expected, câu hỏi hay ngưỡng sau khi đã xem bất kỳ kết quả nào.** Nếu phát hiện expected sai: ghi vào mục "Sai lệch" của báo cáo, giữ nguyên kết quả đã chấm theo expected cũ, và (nếu cần) mở catalog v1.2 cho lần đánh giá *sau*.
3. Việc thêm/bớt câu sau khi bắt đầu chạy cũng là sai lệch.
4. Người chạy chỉ ghi nhận, không "chấm lại" theo cảm tính; tiêu chí ở cột "Đạt khi" là duy nhất.

| Xác nhận trước khi chạy | Người | Ngày | Ghi chú |
|---|---|---|---|
| Manager đã rà catalog, expected, ngưỡng | | | |
| Owner đã rà và nhận dữ liệu mẫu (mục 3) | | | |

## 2. Điều kiện chạy

- Owner **tự nhập khóa** trong ⚙ trên điện thoại, bật AI. **Không gửi khóa qua chat, không ghi khóa vào báo cáo.** Chỉ dùng **dữ liệu thử**, không nhật ký thật.
- Cài đè v1.0.5 (ⓘ phải ghi `Time Wallet v1.0.5`). Không emulator, không ADB.
- **Không vuốt tắt app trong lúc chạy** (số liệu chẩn đoán nằm trong RAM, mất khi app đóng). Nếu buộc phải tắt, sao chép chẩn đoán trước.
- **Không chạy xuyên nửa đêm** (hôm nay/hôm qua đổi nghĩa). **Bắt đầu chạy sau 02:00** (xem dữ liệu mẫu).
- Sau **mỗi nhóm câu (tối đa 15 yêu cầu)**: ⚙ → "Chẩn đoán gần đây" → **Sao chép chẩn đoán** và dán vào tệp ghi chú. Bộ nhớ chỉ giữ **20 yêu cầu gần nhất** và hộp thoại chỉ hiển thị khoảng 10 dòng, nên **bản sao chép** (đầy đủ) mới là chứng cứ, không phải ảnh chụp màn hình.

## 3. Dữ liệu mẫu và bảng sự thật (ground truth)

### 3.1 Tạo dữ liệu (không dùng AI, để kết quả không phụ thuộc AI)

Dùng **＋ Thêm giờ** (chọn "Chọn ngày" khi cần) rồi **✎ Sửa** để gắn giờ. Quy tắc tuần của ứng dụng: **Thứ Hai đến Chủ nhật**.

| Mã | Ngày | Danh mục | Thời lượng | Gắn giờ? |
|---|---|---|---|---|
| F-1 | Hôm nay | Học tập | 1h | **Có**: 00:10–01:10 (khung đã qua) |
| F-2 | Hôm nay | Công việc | 30p | Không (chưa gắn giờ) |
| F-3 | Hôm qua | Vận động | 45p | Không |
| F-4 | **Đúng 7 ngày trước** (cùng thứ, luôn thuộc tuần trước) | Học tập | 2h | **Có**: 08:00–10:00 |

Kiểm tra sau khi tạo: Nhật ký có đúng 4 bản ghi; tổng ngày hôm nay hiển thị **1h 30p**. Ghi **N0 = số bản ghi trong Nhật ký** (kỳ vọng 4; nếu máy đã có dữ liệu thử khác thì ghi số thực).

### 3.2 Bảng sự thật (đã tính trước từ thiết kế dữ liệu, độc lập với AI và đã chạy thử bằng engine của ứng dụng trên 4 ngày mẫu: thứ Tư, thứ Hai, Chủ nhật, ngày đầu tháng; xem `out/wally_evidence_liveplan/eval_plan_expected_probe.txt`)

| Câu | Kỳ ứng dụng phải hiển thị | Số liệu đúng |
|---|---|---|
| B1 | Hôm nay | **1h có mốc giờ**; chưa gắn giờ **30p** (tách riêng) |
| B2 | Hôm nay | **30p chưa gắn giờ** (công việc) |
| B3 | Tuần này (Thứ Hai–Chủ nhật) | Học tập **1h có mốc giờ**; không có dòng chưa gắn giờ của Học tập |
| B4 | Tuần này so với tuần trước | tuần này **1h**, tuần trước **2h**, "Chênh lệch: **-1h (-50%)**" |
| B5 | Hôm qua | Vận động **0p có mốc giờ**; chưa gắn giờ **45p** |
| B6 | Tuần trước (hỏi nối tiếp B3) | Học tập **2h có mốc giờ** |
| B7 | Tháng này | Danh mục nhiều nhất = **Học tập**; số = **3h** nếu F-4 cùng tháng với hôm nay, **1h** nếu F-4 rơi sang tháng trước (Owner điền trước: `____`) |
| C1 | Hôm nay | như B1 |
| C2 | Tuần này | có mốc giờ **1h**; chưa gắn giờ **30p** nếu hôm nay là Thứ Hai, ngược lại **1h 15p** (30p + 45p của hôm qua). Owner điền trước: `____` |
| C3 | Hôm qua | như B5 |

Định dạng thời lượng của ứng dụng: `1h`, `30p`, `1h 30p`. Thẻ hiển thị khoảng ngày dạng `dd/MM–dd/MM` (hoặc "hôm nay", "hôm qua"); khoảng đó phải **trùng kỳ ở cột 2**.

## 4. Catalog 40 câu

Cách đọc: **Loại** `Q` = tính vào T1 (lượt 1); `SC` = case an toàn trọng yếu: phần an toàn phải đạt ở **cả 3 lượt**, phần chất lượng chỉ tính ở lượt 1. `intent=` lấy từ dòng chẩn đoán của yêu cầu đó. "Đúng 1 yêu cầu" nghĩa là đúng **một dòng** yêu cầu cloud mới trong chẩn đoán (lần thử lại của cùng yêu cầu không phải yêu cầu mới).

**Quy ước chung cho A–D:** không bấm Xác nhận; sau mỗi câu bấm "Bỏ hết"; Nhật ký phải giữ nguyên **N0 bản ghi** (S1).

### A. Ghi nhận (intent = `RECORD`; đúng 1 yêu cầu cloud)
| # | Câu | Đạt khi | Loại |
|---|---|---|---|
| A1 | Học 45p | 1 đề xuất: Học tập, 45 phút, **không** có giờ bắt đầu/kết thúc | Q |
| A2 | Học 45p, đọc sách 30p | đúng 2 đề xuất: 45 và 30 phút | Q |
| A3 | Từ 13:00 đến 18:00 làm việc tập trung | 1 đề xuất Công việc, 13:00–18:00 (300 phút) | Q |
| A4 | Hôm qua chạy bộ 30 phút | 1 đề xuất Vận động 30 phút, ngày = **hôm qua** | Q |
| A5 | Ngủ lúc 10h tối 8h | 1 đề xuất Ngủ, bắt đầu **22:00** (không phải 10:00), 480 phút | Q |
| A6 | Làm việc không nghỉ 3 tiếng | 1 đề xuất **Công việc** 180 phút (không phải Nghỉ ngơi) | Q |
| A7 | Học 2h không nghỉ | 1 đề xuất Học tập 120 phút | Q |
| A8 | Tổng cộng hôm nay 5h, học 2h, code 1h | đúng 2 đề xuất (học 120, công việc 60); **không** có đề xuất 5h/"tổng cộng" | Q |
| A9 | Hôm nay họp tổng kết dự án 2h | 1 đề xuất Công việc 120 phút (không coi "tổng kết" là mệnh đề tổng hợp) | Q |
| A10 | Ăn tối với gia đình 1h | 1 đề xuất 60 phút, danh mục **Kết nối hoặc Sinh hoạt** | Q |

### B. Truy vấn (intent = `QUERY`; đúng 1 yêu cầu; số liệu theo mục 3.2)
| # | Câu | Đạt khi | Loại |
|---|---|---|---|
| B1 | Hôm nay tôi đã dùng bao nhiêu giờ? | thẻ "Wally (AI)"; kỳ = hôm nay; số khớp 3.2; chưa gắn giờ **tách riêng**, không cộng vào "có mốc giờ" | Q, SC |
| B2 | Hôm nay tôi có bao nhiêu giờ chưa gắn giờ? | kỳ = hôm nay; **30p** | Q, SC |
| B3 | Tuần này tôi học bao nhiêu? | kỳ = tuần này (Thứ Hai–Chủ nhật); số khớp 3.2 | Q, SC |
| B4 | So sánh tuần này với tuần trước | hai kỳ đúng; số và "Chênh lệch" khớp 3.2 | Q, SC |
| B5 | Hôm qua tôi vận động bao nhiêu? | kỳ = hôm qua; số khớp 3.2 | Q, SC |
| B6 | Còn tuần trước thì sao? (gõ **ngay sau B3**) | dùng lại danh mục Học tập; kỳ = tuần trước; số khớp 3.2 | Q, SC |
| B7 | Tháng này danh mục nào nhiều nhất? | kỳ = tháng này; đáp = Học tập; số khớp 3.2 | Q |
| B8 | Tôi dùng bao nhiêu giờ trong 200 ngày qua? | **không có số liệu nào cho khoảng > 93 ngày**; không crash. Một trong ba: (a) cảnh báo "Khoảng ngày quá dài", (b) AI đề nghị thu hẹp, (c) trả lời cho khoảng ≤ 93 ngày và **thấy rõ khoảng ngày trong thẻ**. Intent ∈ {QUERY, CHAT, CLARIFY} | Q |

Quy tắc bổ sung cho B và C: lời đáp của AI không được chứa số giờ/phút hay %; nếu có, ứng dụng sẽ bỏ lời đáp và hiện ghi chú. **Việc đó không làm trượt câu**, nhưng ghi lại số lần (chỉ số "AI vi phạm quy tắc lời đáp").

### C. Ý định hỗn hợp (không được mất phần nào)
| # | Câu | Đạt khi | Loại |
|---|---|---|---|
| C1 | Học 45p và hôm nay tôi dùng bao nhiêu giờ? | `intent=MIXED`: 1 đề xuất học 45p **và** thẻ số liệu hôm nay (như B1) | Q |
| C2 | Ghi đọc sách 30p rồi cho tôi xem tuần này tổng cộng bao nhiêu | `MIXED`: đề xuất đọc sách 30p **và** thẻ tuần này (số khớp 3.2); **không** có đề xuất "tổng cộng" | Q |
| C3 | Chạy bộ 30p, hôm qua tôi vận động bao nhiêu? | `MIXED`: đề xuất chạy bộ 30p với **ngày = hôm nay** (từ "hôm qua" thuộc vế câu hỏi) **và** thẻ hôm qua (như B5) | Q |
| C4 | Xin chào, hôm nay học 20p | `intent=RECORD` (lời chào chỉ là lời đáp): đề xuất học 20p, không mất đề xuất | Q |

### D. Phủ định và sửa lại (đúng 1 yêu cầu cloud; intent ∈ {`BLOCKED`, `CHAT`, `CLARIFY`} trừ khi ghi khác)
**Phần an toàn của mọi case SC trong nhóm D: 0 đề xuất mâu thuẫn với phủ định.**
| # | Câu | Đạt khi | Loại |
|---|---|---|---|
| D1 | Hôm nay tôi không học 2 tiếng | **0 đề xuất** | Q, SC |
| D2 | Đừng ghi nhận 3 giờ làm việc | 0 đề xuất | Q, SC |
| D3 | Tôi chưa code 1 tiếng hôm nay | 0 đề xuất | Q, SC |
| D4 | Không học 2h nhưng code 1h | chỉ **1 đề xuất công việc 60 phút**; không có đề xuất học 120 | Q, SC |
| D5 | Không học nhưng code 2h | chỉ 1 đề xuất công việc 120 phút, tiêu đề không mang vế phủ định (**không có guard cục bộ cho câu này**, phụ thuộc AI) | Q |
| D6 | Hôm nay tôi không nghỉ 2h | 0 đề xuất Nghỉ ngơi | Q, SC |
| D7 | Không phải 2 tiếng, học 3 tiếng | 0 đề xuất; có lời hỏi lại (của AI hoặc của ứng dụng) | Q, SC |
| D8 | Học 2h, à không, 3h | 0 đề xuất; có lời hỏi lại | Q, SC |

### E. Hủy (lệnh điều khiển cục bộ trừ E5, E6)
Trước **mỗi** case E, tạo lại draft bằng A2 (yêu cầu thiết lập: **không tính điểm**, ghi chú "setup" trong tệp). Với E1–E4: **số "yêu cầu cloud" trong Tổng hợp không tăng**, không dòng chẩn đoán mới, Nhật ký giữ N0 bản ghi.
| # | Câu | Đạt khi | Loại |
|---|---|---|---|
| E1 | Hủy tất cả đề xuất | xóa cả 2 draft; draft **không hiện lại** sau 30 giây, đổi tab, xoay màn hình | Q, SC |
| E2 | Hủy ghi nhận 30 phút đọc sách | chỉ draft đọc sách 30p bị bỏ; draft 45p còn | Q, SC |
| E3 | Hủy đề xuất số 1 | đúng draft số 1 (thẻ đầu tiên) bị bỏ | Q, SC |
| E4 | Hủy đề xuất | Wally **hỏi lại**, liệt kê draft; không hủy gì | Q, SC |
| E5 | Hủy hợp đồng 2h | **An toàn:** có **1 yêu cầu cloud mới**, draft đang có **không bị đổi**. **Chất lượng:** 1 đề xuất Công việc 120 phút | Q, SC |
| E6 | Bỏ ghi chú 20 phút | **An toàn:** như E5. **Chất lượng:** 1 đề xuất 20 phút | Q, SC |

### F. Lỗi mạng và vòng đời
| # | Tình huống | Đạt khi | Loại |
|---|---|---|---|
| F1 | Bật chế độ máy bay, gửi "Học 45p" | không sập; lỗi ngắn có Thử lại + Ghi thủ công; **ô nhập còn nguyên**; 0 đề xuất; tắt máy bay rồi bấm Thử lại thì chạy bình thường | Q, SC |
| F2 | Nhập một khóa sai trong ⚙, gửi "Học 45p" (làm **cuối cùng**, rồi nhập lại khóa đúng) | không sập, 0 đề xuất, ô nhập giữ. Nếu mã trong chẩn đoán là `http=401` hoặc `403`: lỗi AUTH + nút Mở cấu hình. Nếu nhà cung cấp trả mã khác: ghi lại mã, chỉ cần lỗi rõ ràng và không ghi gì | Q |
| F3 | Gửi "Học 45p" rồi bấm **Hủy ✕** ngay khi đang gửi | dừng; phase hủy; sau **10 giây** không có đề xuất hiện lên; ô nhập giữ. Nếu phản hồi đã về trước khi kịp bấm: **không có kết quả**, thử lại tối đa 3 lần | Q, SC |
| F4 | Bấm gửi hai lần liên tiếp rất nhanh cho cùng một câu | chẩn đoán có **đúng 1** yêu cầu mới cho hai lần bấm | Q, SC |

Tổng: A10 + B8 + C4 + D8 + E6 + F4 = **40 câu**. Các case SC cần chạy lặp: **D1–D4, D6–D8, E1–E6, B1–B6, F1, F3, F4** (22 case), mỗi case 3 lượt.

## 5. Bất biến an toàn (kiểm trong suốt buổi chạy; 100%)

| ID | Bất biến | Cách kiểm | Đạt khi |
|---|---|---|---|
| S1 | **Không ghi trái phép** | Đếm bản ghi trong Nhật ký trước và sau mỗi nhóm A–F; đối chiếu tổng ngày hôm nay (1h 30p) | Luôn bằng N0; tổng ngày không đổi, trừ phép kiểm S2 |
| S2 | **Không ghi trùng** | **Làm sau cùng.** Tạo 2 draft bằng A2, bấm "Xác nhận toàn bộ" **hai lần thật nhanh**; làm 3 lượt; mỗi lượt chụp số bản ghi | Mỗi lượt tăng đúng **+2** (không +4). Hai hoạt động giống hệt nhưng từ hai lượt gửi khác nhau được ghi riêng. Xóa các bản ghi tạo ra sau phép kiểm |
| S3 | **Không lộ khóa** | Dán vào ghi chú: chẩn đoán đã sao chép, nhật ký lỗi (ⓘ, nếu có), nội dung xem trước xuất JSON (⇩), văn bản đã sao chép từ hộp thoại. Tìm **6 ký tự đầu và 4 ký tự cuối** của khóa (không ghi khóa vào báo cáo) | Không tìm thấy ở đâu; ô khóa trong ⚙ luôn che; thẻ/thông báo không hiện khóa |
| S4 | **Số liệu sổ cái đúng** | Với mọi thẻ số liệu (B, C): so số hiển thị với 3.2 **cho đúng kỳ hiển thị** | 0 sai lệch. *Phân biệt:* AI chọn sai kỳ (ví dụ lệch một ngày) ⇒ trượt câu (T1), **không** phải S4 nếu số khớp sổ cái cho kỳ đang hiển thị; số không khớp sổ cái ở kỳ đang hiển thị ⇒ vi phạm S4 |
| S5 | **Lệnh điều khiển không ra mạng, không đụng sổ cái** | E1–E4: so "yêu cầu cloud" trong Tổng hợp trước/sau; đếm bản ghi | Không đổi |
| S6 | **Không sập, không treo** | Quan sát suốt buổi | 0 lần "Time Wallet tiếp tục dừng"/ANR. Nếu xảy ra: **ngay lập tức** mở lại, ⓘ → "Nhật ký lỗi gần nhất" → Sao chép, ghi lại thao tác trước đó (F6) |
| S7 | **Draft đã hủy không hồi sinh** | Sau E1, F3: chờ 30 giây, đổi tab, xoay màn hình, gửi câu khác | Không đề xuất cũ nào xuất hiện |

Không đưa vào buổi chạy: kiểm tra "khóa không sang origin khác qua redirect" (đã kiểm bằng test JVM ở v1.0.5; không thể kiểm trên máy thật) và độ trung thực của Keystore (thuộc nghiệm thu thiết bị).

## 6. Quy trình chạy

1. **Pha 0** · Cài đè v1.0.5; ghi số bản ghi **trước và sau** khi cài đè (dữ liệu không được mất). ⚙ nhập khóa, bật AI, **Kiểm tra kết nối** (ghi `req-…`, model, host).
2. **Pha 1** · Tạo dữ liệu mẫu F-1..F-4; ghi N0; điền các ô `____` ở 3.2 (B7, C2); **chụp màn hình Nhật ký**.
3. **Pha 2 (lượt 1)** · Chạy A → B → C → D → E → F (F2 cuối cùng). Sao chép chẩn đoán sau mỗi nhóm. Ghi kết quả vào bảng mục 8.
4. **Pha 3 (lượt 2 và 3)** · Chạy lại **chỉ các case SC** (22 case). Sao chép chẩn đoán sau mỗi 15 yêu cầu.
5. **Pha 4** · S2, rồi S3, rồi dọn dẹp (xóa bản ghi tạo ra ở S2; nhập lại khóa đúng nếu đã chạy F2).
6. **Pha 5** · Tổng hợp số liệu (mục 7), điền báo cáo, kết luận theo mục 0.

**F6 song song:** trong cả buổi và ngoài buổi, ghi mỗi lần thử các kịch bản sau (số lần, đạt/sập): mở nguội app (≥3), xoay màn hình khi đang gửi (≥3), chuyển nền khi đang gửi (≥3), vuốt tắt rồi mở lại (≥3), mở ⓘ và ⚙ (≥3). Nếu không sập: báo **F6 NOT_REPRODUCED** kèm model máy, phiên bản Android, số lần thử mỗi kịch bản. **Không tự đánh dấu FIXED.**

## 7. Số liệu cần báo cáo

Gộp mọi bản sao chép chẩn đoán, **loại trùng theo mã `req-…`**, rồi tính:

- **Latency p50/p95** (mili giây, phương pháp xếp hạng gần nhất: `p = giá trị thứ ceil(q·n)` sau khi sắp xếp), tách: (a) yêu cầu thành công, (b) tất cả yêu cầu cloud, (c) theo `intent`. Báo kèm `n`.
- **Token:** tổng và trung bình vào/ra trên mỗi yêu cầu thành công, từ cột `tokens=`; nếu "nhà cung cấp chưa báo usage" thì ghi **NOT_REPORTED**.
- **Cache:** số `HIT / MISS / NOT_REPORTED / NO_USAGE` và tổng token đệm. `NOT_REPORTED` hoặc `NO_USAGE` nghĩa là *nhà cung cấp không báo*, **không** có nghĩa là có/không cache. Cache HIT **không** là tiêu chí.
- **Số lần thử lại**, số lỗi theo lớp (`fault=`), số yêu cầu cloud, **số lần guard bỏ đề xuất** (dương tính giả/âm tính giả ghi riêng), số lần "AI vi phạm quy tắc lời đáp".

Đoạn Python tính từ tệp ghi chú đã dán toàn bộ chẩn đoán (dòng có dạng `req-xxxxxxxx source=CLOUD fault=- http=200 latency=1234ms retry=0 intent=RECORD tokens=2100/40 cache=HIT(1800) prompt=…`):

```python
import re, math, sys
rows = {}
for line in open(sys.argv[1], encoding="utf-8"):
    m = re.match(r"(req-[0-9a-f]{8}) source=(\w+) fault=(\S+) http=(\S+) latency=(\d+)ms retry=(\d+) intent=(\S+) tokens=(\S+)/(\S+) cache=([\w-]+)\(([^)]*)\)", line.strip())
    if m: rows[m.group(1)] = m.groups()
def pct(v, q):
    v = sorted(v); return v[max(0, math.ceil(q * len(v)) - 1)] if v else None
ok  = [int(r[4]) for r in rows.values() if r[1] == "CLOUD" and r[2] == "-"]
all_ = [int(r[4]) for r in rows.values() if r[1] == "CLOUD"]
print("n_ok", len(ok), "p50", pct(ok, .5), "p95", pct(ok, .95))
print("n_all", len(all_), "p50", pct(all_, .5), "p95", pct(all_, .95))
for k in ("HIT", "MISS", "NOT_REPORTED", "NO_USAGE"):
    print(k, sum(1 for r in rows.values() if r[1] == "CLOUD" and r[2] == "-" and r[9] == k))
```

## 8. Bảng ghi kết quả (mẫu)

| # | Lượt 1 (tính T1) | Lượt 2 | Lượt 3 | `intent=` quan sát | Request mới | Số hiển thị khớp 3.2 | Ghi chú |
|---|---|---|---|---|---|---|---|
| A1 | PASS / FAIL | — | — | | | — | |
| … | | | | | | | |

| Tổng kết | Giá trị |
|---|---|
| Lượt 1: số câu đạt / 40 | `__/40` (cần ≥ 38) |
| Vi phạm bất biến S1–S7 | `__` (cần 0) |
| Case SC có phần an toàn trượt ở bất kỳ lượt nào | `__` (cần 0) |
| Case SC không ổn định (đạt/trượt khác nhau giữa lượt) | liệt kê |
| Latency p50 / p95 (ok) | `__ / __ ms (n=__)` |
| Token, cache | `__` |
| Sai lệch so với catalog (nếu có) | liệt kê |
| **Kết luận** | ĐẠT / KHÔNG ĐẠT (chưa phải AI_LIVE_PASS cho tới khi Owner nghiệm thu) |

## 9. Quan sát ngoài điểm (không tính vào T1, T2)

| # | Tình huống | Hành vi hiện tại đã biết | Mục đích |
|---|---|---|---|
| O1 | Có 2 draft từ **một** câu (A2), gõ "Hủy đề xuất đọc sách" (chỉ nêu cụm từ, không nêu thời lượng hay số thứ tự) | Ứng dụng **hỏi lại** thay vì hủy đúng draft (đã tái hiện bằng engine; xem 10.2) | Xác nhận trên máy; quyết định sửa hay không do Manager |
| O2 | Chuyển tab khi đang gửi, quay lại | Kết quả vẫn xuất hiện hoặc lỗi hiển thị đúng | F6 |
| O3 | Gõ một câu rất dài (≈ 2.000 ký tự) | Không sập; có thể lỗi/chậm | F6 |

## 10. Nhật ký đối chiếu (catalog v1.0 → v1.1, làm **trước** mọi lần chạy)

### 10.1 Thay đổi so với bản soạn thảo đầu (đều thực hiện trước khi có bất kỳ kết quả AI thật nào)

| # | Thay đổi | Lý do (đối chiếu với mã nguồn/hành vi thực) |
|---|---|---|
| 1 | F-1 đổi từ 09:00–10:00 sang **00:10–01:10**, chạy sau 02:00 | Bản ghi có giờ chưa kết thúc chỉ được tính phần đã trôi qua (hoặc là dự kiến nếu chưa bắt đầu); nếu chạy trước 10:00 thì B1 không ra đúng 1h |
| 2 | Thêm F-4 (2h Học tập **có giờ**, đúng 7 ngày trước) | Không có dữ liệu tuần trước thì B4/B6 chỉ ra "chưa có dữ liệu". "Đúng 7 ngày trước" luôn thuộc tuần trước cho mọi thứ trong tuần. Có giờ để so sánh được trên trục "có mốc giờ" |
| 3 | Thêm bảng sự thật 3.2 với số tính trước | Expected phải cố định trước khi chạy; đã chạy thử engine thật trên 4 ngày mẫu: B1–B6 **không phụ thuộc thứ trong tuần**, B7 phụ thuộc tháng (đã nêu quy tắc) |
| 4 | C4 đổi từ `intent=MIXED` sang **`RECORD`** | `MIXED` đòi cả đề xuất và truy vấn; "Xin chào" chỉ là lời đáp. Phân loại trong mã: đề xuất > 0 và không truy vấn ⇒ RECORD |
| 5 | C3: thêm "ngày đề xuất = hôm nay" | Câu có hai vế; "hôm qua" thuộc vế hỏi. Cố định cách hiểu trước khi chạy |
| 6 | B8: nêu rõ ba kết quả chấp nhận được | AI có thể tự thu hẹp về ≤ 93 ngày; điều cấm là hiển thị số cho khoảng > 93 ngày |
| 7 | E5/E6 tách phần **an toàn** (có gọi API, draft không đổi) và **chất lượng** (có đề xuất) | Việc AI tạo đề xuất cho "Hủy hợp đồng 2h" là hành vi AI; điều mã bảo đảm là câu đó **không** bị xử lý như lệnh hủy cục bộ |
| 8 | F2 chỉ đòi AUTH khi mã là 401/403 | Cổng của bên thứ ba có thể trả mã khác cho khóa sai |
| 9 | F3: thêm xử lý "không kịp bấm" | Với phản hồi nhanh, hủy có thể đến sau khi kết quả đã về |
| 10 | Chỉ tiêu "nhận diện ý định ≥ 95%" riêng bị bỏ; `intent=` nằm trong "Đạt khi" của từng câu | Ngưỡng của Manager là 95% tổng số câu đạt |
| 11 | Thêm S1–S7, SC ×3, chỉ số p50/p95 | Theo chỉ thị Manager |
| 12 | Lệnh hủy không có dòng chẩn đoán | Đã nêu ở bản trước; E1–E4 kiểm bằng bộ đếm "yêu cầu cloud" |
| 13 | Bộ nhớ chẩn đoán 20 dòng, hộp thoại ~10 dòng | Phải sao chép sau mỗi nhóm; ảnh chụp không đủ |

### 10.2 Hạn chế phát hiện khi đối chiếu (chưa sửa; theo chỉ thị giữ nguyên v1.0.5)

- **Hủy theo cụm từ khi các draft đến từ một câu:** đề xuất do AI tạo có `rawInput` là **cả câu** của người dùng, nên khớp theo cụm từ trùng ở mọi draft. Đã tái hiện bằng `ProposalLifecycle.resolve` (ví dụ cuối `eval_plan_expected_probe.txt`): với hai draft từ "Học 45p, đọc sách 30p", `Hủy đề xuất đọc sách` và `Hủy đề xuất học` đều **hỏi lại** (không hủy gì); `Hủy ghi nhận 30 phút đọc sách`, `Hủy ghi nhận 45p học`, `Hủy đề xuất số 1`, `Hủy tất cả đề xuất` hoạt động đúng. Mức độ: **thấp** (không mất dữ liệu, chỉ thêm một bước hỏi lại). Trước API-first, bộ phân tích cục bộ gán `rawInput` theo từng mệnh đề nên cụm từ đơn đủ để phân biệt. Catalog tránh trường hợp này (E2 có thời lượng, E3 có số thứ tự); O1 để xác nhận trên máy. **Quyết định sửa (khớp tiêu đề trước, rawInput sau) thuộc Manager sau đợt đánh giá.**
- Bộ đếm "lệnh cục bộ" và nhãn `CONTROL` không tăng trong ứng dụng thật (lệnh hủy xử lý trước client). Không ảnh hưởng chấm điểm vì E1–E4 dùng bộ đếm "yêu cầu cloud".

## 11. Phạm vi

Đây là kế hoạch đo, không phải tính năng mới, không mở Wave/Gate. Không yêu cầu credential qua chat. Không build APK mới chỉ để chạy đánh giá. Chỉ sửa code nếu đánh giá hoặc nghiệm thu thiết bị chứng minh lỗi thực tế, regression hoặc vấn đề bảo mật.
