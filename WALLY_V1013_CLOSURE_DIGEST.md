# WALLY v1.0.13 — CLOSURE DIGEST (một bản duy nhất)

**Ngày:** 2026-11-16 · **Single Writer:** AI-Master · **Quyền:** Manager Directive WALLY v1.0.13 PRODUCT COMPLETION (phương án B) + Owner Authorization FINAL VALIDATION / GO EXECUTE (khóa QA mới tại `<OWNER_QA_KEY_FILE>`, trần US$5,00)

## 0. Kết luận
**Trạng thái: `BLOCKED_EXTERNAL` (Manager Directive WALLY v1.0.13 EXTERNAL BLOCKER). CHƯA đủ điều kiện Owner Acceptance.** Blocker bên ngoài là nhà cung cấp: từ khoảng 11:30 (giờ máy) tuyến `cinb/gemini-3.8-flash` của gateway Foza trả `HTTP 503` với lỗi upstream `[antigravity/gemini-3.8-flash-low] [403] This service has been disabled in this account for violation of Terms of Service`. **Cửa sổ thăm dò 50 phút đã kết thúc (17/17 lần 503, 11:41–12:30, nhật ký `evidence\live\provider_poll.log`); đã dừng gọi API, không gia hạn, không tìm cách vượt hạn chế upstream.** Môi trường QA đã khôi phục sạch và writer lease đã bàn giao (`WALLY_V1013_WRITER_HANDOFF.md`). Khóa QA vẫn hợp lệ (`GET /v1/models` = 200 và thấy model). Sáng nay (≤ 09:19) cùng tuyến chạy bình thường. Mình **không đổi model/provider** (kết luận Live Eval là cho cấu hình mặc định `cinb/gemini-3.8-flash`; chỉ thị cấm đổi) nên **Live Eval lượt đầu và holdout trên APK cuối `bfc00ae9…` CHƯA chạy**.

Đã làm trong lúc chờ (không gọi API ngoài các phép thử nhỏ):
1. **Live Eval lượt 1 trên APK trước (`aeae3def…`, prompt-6) đã chạy xong** và phát hiện một lỗi thật: **D7/D8 không hiện câu hỏi cụ thể của AI** (xem mục 4). Đã sửa (prompt-7), kiểm bằng mẫu phát triển trực tiếp qua API (19/24 → 24/24), build APK mới **`bfc00ae9…`** và chạy lại các kiểm tra thiết bị không-API.
2. Mọi kết quả Live Eval của `aeae3def…` chỉ là bằng chứng của bản bị thay thế; **không chuyển sang `bfc00ae9…`** (khác prompt).

| Candidate | Giá trị |
|---|---|
| **APK cuối (chờ Live Eval)** | `out\wally_candidate_v1013\TimeWallet-MVP-v1.0.13-WALLY-COMPLETION-candidate-debug.apk` · 11.992.808 byte (byte-giống tệp đã kiểm `out\wally_evidence_v1013\candidate_build\app-debug_v1.0.13_final.apk`) |
| SHA-256 | `bfc00ae9e444d5aaeee9d11ff58f1471bda16d8dbcf35cce5e96d731a9788a2b` |
| Phiên bản | `versionName 1.0.13`, `versionCode 14` (không đổi so với bản bị thay thế; phân biệt bằng SHA), **`PROMPT_VERSION wally-prompt-7`**, model mặc định `cinb/gemini-3.8-flash` (không đổi), hạn chót 45 s |
| Khác bản bị thay thế | chỉ văn bản prompt (quy tắc 8 và ngoại lệ ở quy tắc 11): `classes7.dex` là mục duy nhất đổi kích thước trong 122 mục APK |
| Nguồn gốc | `source_v1.0.13.tar` (sha256 `2cda1e76…a55bf`), manifest 130/130 OK, `SOURCE_DIFF_vs_v1.0.12.patch` (đã kiểm bằng cách áp lên bản trích sạch v1.0.12: exit 0, cây sau vá giống hệt v1.0.13, 0 `.orig`/`.rej`); 20 tệp sửa / 8 tệp mới / 0 xóa |
| Baseline | v1.0.12 `1a034e02…`, v1.0.11 `af50d6cc…`, v1.0.10 `1d834a83…` **không đổi** (hash kiểm lại) |
| Bị thay thế (giữ nguyên làm bằng chứng) | `aeae3def…` (prompt-6): `out\wally_candidate_v1013_SUPERSEDED_final1_aeae3def\`, bằng chứng `out\wally_evidence_v1013\superseded_final1\` và `out\wally_live_v1013\evidence\superseded_final1\`; `986da3d3…` (build đầu): `superseded_build1\` |

## 1. Findings đã sửa và bằng chứng
| # | Finding | Sửa | Bằng chứng |
|---|---|---|---|
| 1 | A5 "Ngủ lúc 10h tối 8h", E6 "Bỏ ghi chú 20 phút" (lỗi hiểu) | prompt-6: quy tắc **tổng quát** 7c (giờ bắt đầu rồi độ dài) và 7d (động từ nghe như lệnh nhưng là tên việc); không câu catalog nào trong prompt (test ghim) | trực tiếp qua API: catalog 43/44 (trước 41/44). **Trên APK `aeae3def…` (Live Eval lượt 1): A5 PASS, E6 PASS** |
| 2 | B8 timeout 30 s | hạn chót 45 s, read-timeout không vượt phần còn lại | test 40 s nhận, 46 s TIMEOUT. Trên APK: B8 trả lời sau **43,3 s** (PASS, sát hạn); H3 **TIMEOUT 45 s** ở lượt đầu (xem mục 5). Tăng hạn chót không làm giảm độ trễ |
| 3 | D7/D8: người dùng chỉ thấy ghi chú chung, không thấy câu hỏi cụ thể của AI | (a) `ReplyGuard` theo từng câu + thẻ "Cần hỏi lại" lấy câu hỏi của AI làm tiêu đề (đợt trước); (b) **mới, từ Live Eval thật: `wally-prompt-7`** — quy tắc 8: lời hỏi lại là ĐÚNG MỘT câu cụ thể **kết thúc bằng "?"**, được nhắc lại đúng số người dùng vừa viết; quy tắc 11 có ngoại lệ | Live Eval `aeae3def…`: D7/D8 **FAIL** (thẻ chung). Nguyên nhân (đo trực tiếp): prompt-6 bảo model hỏi lại *không nhắc số* ⇒ câu trả lời dạng mệnh lệnh không có "?" ("…nhắn lại giúp mình nhé."), app chỉ hiện câu có "?". Mẫu phát triển (6 câu × 4, API trực tiếp, `evidence\live\diag_askback_*`): prompt-6 **19/24** hiện câu hỏi (câu sửa số: 3/8) → prompt-7 **24/24** (câu sửa số 8/8, ví dụ "Bạn muốn ghi học 2h hay 3h?"), 0 đề xuất, 0 số bịa. **Chưa kiểm trên APK `bfc00ae9…`** (bị chặn bởi nhà cung cấp) |
| 4 | F4: bấm đúp Gửi → hủy nhầm | nút Hủy chỉ có tác dụng sau 700 ms; đang chạy thì lần gửi thứ hai bị bỏ qua | 10 test và trên thiết bị (mục 3); Live Eval `aeae3def…` F4 PASS (1 yêu cầu, không CANCELLED, 1 thẻ) |
| 5 | testConnection phản ánh sai lỗi | thành công chỉ khi 200 là phản hồi chat-completions thật; nhãn `CONNECTION_TEST`, đếm riêng | 13 test; **thấy trên APK `bfc00ae9…` với API thật**: lỗi 503 hiện đúng "Máy chủ AI đang gặp lỗi (5xx). (HTTP 503)", `intent=CONNECTION_TEST`, "kiểm tra kết nối=1 (lỗi=1)", không tính là yêu cầu chat |
| 6 | Thời gian tương lai và PLANNED/ACTIVE | 3 lỗi thật sửa (đề xuất ngày quá khứ không "Dự kiến"; xúc xắc chỉ tính phần đã trôi của bản ghi ĐANG diễn ra; khoảng giờ bằng nhau không thành 24 giờ) | 10 test; Live Eval `aeae3def…`: H1–H7 đúng giờ (H3 timeout) |
| 7 | Tài liệu `ApiKeyHygiene` sai về HTTP stack | KDoc/test double đúng số đo thật | 10 test |
| 8 | Lớp lỗi phía app do holdout mù phát hiện (lớp 2) | (C1)(C2)(C3) | 23 test; holdout lớp 2 (câu trả lời mẫu qua app): 64/96 → 83 → 93/96 |
| 9 | **P0** credential hardcode | xem mục 8 | — |
| 10 | **Lỗi bộ chấm/driver của mình** (không phải lỗi app, báo để minh bạch) | (a) bộ chấm coi chip tĩnh "Hôm nay dùng bao nhiêu?" là câu hỏi của AI ⇒ D7/D8 bị chấm PASS sai; sửa `ai_questions` (loại 3 chip), D7/D8 thành FAIL; (b) A1: đọc chẩn đoán lỗi nên thẻ A1 còn trong khay, A2 hiện 3 thẻ và nhận yêu cầu của A1 ⇒ **lỗi driver**, A1/A2 chạy lại (cả hai lần đều giữ trong `live_raw.jsonl`, có dấu `invalidate`), thêm cổng kiểm "khay rỗng, không hộp thoại" trước mỗi ca; (c) vòng chờ "Kiểm tra kết nối" thoát sớm ⇒ sửa điều kiện | `superseded_final1\live\live_raw.jsonl`, `live_verdict_overrides.json` |

## 2. Test
- Đơn vị trên cây cuối (prompt-7): **537 test, 52 suite, 0 lỗi, 0 lỗi biên dịch/bỏ qua** (`out\wally_evidence_v1013\unit_final3\`). Số lượng không đổi so với bản trước: test prompt được đổi tên và đổi khẳng định (quy tắc 8 mới), hai ghim phiên bản 6 → 7.
- **Đây không phải kết quả nghiệm thu APK** (chỉ thị).

## 3. Thiết bị (đúng APK `bfc00ae9…`, không gọi API thật)
| Kiểm | Android 9 (LDPlayer `WallyQA`) | Android 16 (AVD `TimeWallet_QA`) |
|---|---|---|
| Điều hướng (mở Wally, gõ, đóng bàn phím, Back, đổi tab) | **PASS** (lần 1: 1 lần trượt kiểm nội dung tab "Đồng hồ" do định thời của công cụ, đã lưu; chạy lại PASS) | **PASS** (2 lần chạy, bàn phím thật) |
| F4: bấm đúp Gửi | 1 yêu cầu, không bị hủy | — (chỉ ở bản trước) |
| Hủy sau cửa sổ 0,7 s / hủy rồi gửi lại / chạm Hủy sớm 0,3 s | `CANCELLED` ô nhập giữ; `CANCELLED` + yêu cầu mới; bị bỏ qua | — |
| Thông báo "Kiểm tra kết nối" khi lỗi | đúng lỗi, `CONNECTION_TEST` | — |
| Cài đè v1.0.12 → v1.0.13 | — | dữ liệu giữ nguyên (2 lần chạy) |
| Ngân sách/bản ghi sửa-xóa có xác nhận, đồng hồ bắt đầu/hủy, xuất JSON, xoay màn hình | — | PASS (lượt chạy 1 và 3) |
| Đồng hồ → dừng → lưu → Nhật ký; nhập thủ công; Wally bàn phím/đổi tab/hộp thoại/toast | — | PASS (lượt chạy 1) |
| Chip xúc xắc, **cài mới trên AVD, quét logcat** | — | **NOT_TESTED trên SHA này** (xem dưới) |

**Sự cố hạ tầng (quan trọng):** máy chủ Windows của Owner **tự khởi động lại cứng 2 lần hôm nay (09:53 và 10:55; lần thứ 3 hôm qua 16:05)**, mỗi lần chỉ vài giây sau khi AVD Android 16 đang chạy (`Kernel-Power 41`, `WHEA-Logger` Machine Check; phân tích do Owner cung cấp). Chuỗi AVD của mình bị cắt đúng các thời điểm đó (nhật ký `chain_avd_log_run1_interrupted_at_step6.txt`, `chain_avd_log.txt`). **Mình đã dừng chạy AVD** và sẽ không bật lại khi chưa có đồng ý của Owner. Android 16 trên SHA này vì vậy chỉ đạt **một phần**; ba bước còn thiếu chỉ có bằng chứng ở bản trước (`aeae3def…`, khác nhau duy nhất ở văn bản prompt). Các tệp của lượt chạy hỏng do tiến trình bị giết/`uiautomator` mồ côi (lượt 2, mọi bước False) được giữ lại và đặt tên `*_run2_harness_failed*`.
Phương pháp không-API: khóa **bịa** + mạng của app bị `iptables` từ chối (Android 9, root chỉ trên instance QA).

## 4. Live Eval v1.2
### 4.1 Trên APK `bfc00ae9…` (cuối): **NOT RUN (blocked bởi nhà cung cấp)**
Duy nhất đã chạy: cài mới (SHA kiểm), nhập khóa QA vào ô che (51 ký tự), "Kiểm tra kết nối" ⇒ HTTP 503 (xem mục 0). Driver, quy tắc chấm đăng ký trước, holdout (đồng bộ nguồn: 69/69 tệp giống cây chính) đã sẵn sàng. Khi tuyến hồi phục cần ~4 giờ (Live Eval ≈ 3,5 giờ + holdout ≈ 30 phút), chi phí ≈ US$0,3 + 0,75 (cận trên).
### 4.2 Tham chiếu: lượt 1 trên APK bị thay thế `aeae3def…` (prompt-6), thật, 2026-11-16 07:38–09:05
| Chỉ tiêu | Kết quả |
|---|---|
| **T1 (40 câu gốc)** | **38/40** (đạt ngưỡng ≥ 38 sát ngưỡng) — **D7, D8 TRƯỢT** (xem mục 1 #3). Sau khi sửa bộ chấm (#10a) và chạy lại A1/A2 (#10b); B8 chấm **PASS** theo kết quả (c) của kế hoạch (trả lời cho khoảng ≤ 93 ngày, thấy rõ "15/08–15/11") |
| Nhóm G | **7/7** |
| An toàn SC (lượt 1) | 30 bản ghi, **0 vi phạm**; không ghi trái phép/trùng/sai; không lộ khóa (S3) |
| Owner sentence "Code từ lúc 14h đến giờ" (H1) | **PASS**, đúng **1** dòng sổ sau xác nhận (14:00–15:37, 97 phút) |
| A5 / E6 / F4 / F1 / F2 / F5 / G5 / G7 | PASS |
| H3 "…từ 8h đến 11h" | **TIMEOUT 45 s** ở lượt đầu (tính là trượt; thử lại phụ ghi riêng) |
| Chưa chạy trên bản này | lượt 2 và 3 của 30 ca SC, s2/h8/s3, holdout |
**Lượt 1 không phải verdict cuối**: APK đã đổi (SHA mới), nên kết quả Live Eval của `bfc00ae9…` phải là lượt chạy mới đầu tiên.

## 5. Hiệu năng và chi phí
- Độ trễ lượt 1 trên `aeae3def…` (yêu cầu thành công, n = 59): **p50 7,7 s, p95 17,9 s, tối đa 43,3 s** (B8). Quá 30 s: 1/59 (B8); TIMEOUT 45 s: 1 (H3). Lượt đầu / thử lại / timeout / lỗi driver báo riêng: lượt đầu mọi ca; thử lại phụ: H3, F1 (theo thiết kế); timeout: H3 (tính trượt); **lỗi driver: 2 (A1/A2, đã chạy lại, ghi trong #10)**; lỗi sản phẩm: D7, D8; lỗi model/nhà cung cấp: H3 timeout, 503/403 hiện tại.
- Token ≈ 5.015 vào + 148 ra mỗi yêu cầu; cache không được báo (Gemini).
- **Chi phí phiên xác nhận cuối (cận trên, bảng giá thận trọng 0,5/3,0 US$ mỗi triệu token): ≈ US$0,34** (Live Eval lượt 1: ≈ 0,19 cho 64 yêu cầu; chẩn đoán/mẫu phát triển/thăm dò ≈ 0,15). Cộng mốc bảng điều khiển của Owner (US$0,641423) ≈ **US$0,98**; còn ≈ **US$4,02** dưới trần US$5,00. Chạy lại đầy đủ (Live Eval + holdout) dự kiến ≤ US$1,1 (cận trên). Số thật trên bảng điều khiển thấp hơn cận trên khoảng một nửa với Gemini.

## 6. Đề xuất Router (Hybrid Intent Routing) — quyết định ngắn cho Owner
Chi tiết: `WALLY_OWNER_DECISION_HYBRID_ROUTING.md`. Không triển khai; chính sách API-First giữ nguyên chờ Owner chọn 1/2/3. Không thay đổi so với bản trước.

## 7. Giới hạn / NOT_TESTED
- Live Eval và holdout trên APK `bfc00ae9…`; độ trễ/timeout/chi phí của cấu hình cuối; D7/D8 trên APK (mới chỉ có bằng chứng API trực tiếp, **không** thay cho Live Eval).
- Android 16 trên SHA này: chip xúc xắc, cài mới, quét logcat; chưa kiểm điện thoại thật, Android 10–15, `KBD-01`, F6 smoke máy thật, xoay màn hình trong E1 (LDPlayer không đổi hướng), giao diện khi chờ tới 45 s, regex trên ICU thật của Android.
- Giới hạn đã biết: câu phủ định **không dấu** chưa được `AiProposalGuard` nhận diện (chỉ dựa model; mọi đề xuất vẫn cần xác nhận); 3/20 câu làm rõ nhóm S-C của holdout vẫn bị bỏ vì chứa số guard không suy ra được; "Xóa hết email cũ" không thời lượng vẫn bị coi là lệnh xóa-hết nháp; A5 lúc 16:30 bị đánh dấu "Dự kiến" cho 22:00 hôm nay; chờ tối đa 45 s trước khi báo lỗi; **đuôi độ trễ của gateway (43 s ở B8, timeout 45 s ở H3) là rủi ro nhà cung cấp còn nguyên**; câu hỏi lại của AI có thể vẫn bị bỏ nếu chứa số người dùng không viết.
- Quy trình: chưa có Reviewer độc lập cho v1.0.13; holdout "mù" chỉ mù với phần model; prompt-7 được chỉnh dựa trên D7/D8 của Live Eval (không dựa holdout) và mẫu phát triển riêng; holdout vẫn chưa thấy kết quả API.

## 8. Bảo mật và Git
- **P0 (đã xử lý ở mức cây làm việc):** `ai_test/test_foza_ai.ts` từng chứa khóa cứng (có trong 3 commit; nhánh đã đẩy chứa commit gốc). Mình **không đọc, in, sao chép hay dùng** nó. Tệp trong cây làm việc đã thay bằng bản đọc `FOZA_API_KEY`. **Owner xác nhận đã thu hồi khóa cũ và cấp khóa QA mới** (`<OWNER_QA_KEY_FILE>`; mình chỉ dùng khóa này, không in ra chat/log/evidence; mọi bằng chứng đã được quét: không có khóa đầy đủ, 6 đầu, 4 cuối). **Quan sát phải ghi:** thời điểm sửa (mtime) của tệp khóa mới **trùng giây** với tệp khóa cũ (có thể chỉ là di chuyển tệp); mình **không** so nội dung. Khóa hoạt động (`/v1/models` = 200) nên không kết luận gì thêm; Owner nên xác nhận trên bảng điều khiển rằng khóa đang dùng khác khóa đã thu hồi.
- Git (phương án A, sửa tiếp về phía trước): nhánh local `wally-v1.0.12-ai-nav-fix`, **chưa commit/push/merge**; không force-push, không viết lại lịch sử, không publish. Lịch sử git vẫn còn khóa cũ (đã thu hồi). Bàn giao cho Reviewer/Git Controller sau khi xong: dọn nguồn, PII, tệp sao lưu (`*.before_navdiag`…), quét bí mật, đồng bộ tài liệu.
- **Môi trường QA đã khôi phục (12:35):** ứng dụng đã gỡ khỏi `WallyQA` (không còn khóa QA trong guest), múi giờ `Asia/Ho_Chi_Minh` + tự động múi giờ bật, không còn rule `iptables`, `WallyQA` đã `quit` rồi `modify --root 0`, không còn `RUN_LIVE.flag`, không tiến trình emulator/QA, `adb devices` rỗng; AVD không chạy; không đụng instance Owner (index 0), tiến trình dùng chung hay điện thoại thật. Tệp khóa `<OWNER_QA_KEY_FILE>` thuộc Owner, ngoài kho mã. Writer lease: `WALLY_V1013_WRITER_HANDOFF.md` (cho Reviewer/Git Controller; không sửa `app/src`).

## 9. Quyết định cần Owner
1. **Xử lý tuyến `cinb/gemini-3.8-flash` bên Foza/CIN_B** (upstream `antigravity/gemini-3.8-flash-low` báo 403 ToS). Khi tuyến trả 200 lại và có người giao lại quyền, AI-Master chạy tiếp trên đúng APK `bfc00ae9…`: phase0 → Live Eval lượt 1 → lượt 2–3 → holdout (≈ 4 giờ, ≤ US$1,1). Polling không tự gia hạn.
2. **Duyệt (hoặc từ chối) cấu hình thay thế Claude Sonnet 4.6** (Manager khuyến nghị dựa benchmark cũ: 38/44, p95 8 s, 0 lỗi, cache 53% token vào). Không thực hiện khi chưa có quyết định Owner; nếu duyệt: Live Eval phải gắn đúng model + prompt-7 + APK SHA + cấu hình, **không dùng để tuyên bố Gemini PASS**, không benchmark lại 5 model; cần đối chiếu chi phí thực tế trên bảng điều khiển (giá Claude cao hơn Gemini) trước khi chạy.
3. Android 16: **không** boot lại AVD trên máy này; không đổi BIOS, power plan, hypervisor hay driver (Manager). Ba bước thiếu trên SHA cuối giữ `NOT_TESTED`; không dùng bằng chứng của `aeae3def…` để tuyên bố `bfc00ae9…` đã vượt toàn bộ Android 16.
4. Nếu Live Eval trên `bfc00ae9…` đạt (T1 ≥ 38/40, G 7/7, SC 100%, A5/B8/E6/D7/D8/H1 đạt, 0 ghi trái phép/trùng/sai/lộ khóa): giao đúng tệp APK ở mục 0 (không build lại) rồi dùng `WALLY_OWNER_CHECKLIST_V1013.md` trên điện thoại thật; `AI_LIVE_PASS` do Owner/Manager ghi nhận. Nếu trượt: sửa trong phạm vi hiện tại ⇒ SHA mới ⇒ chạy lại.

*Bằng chứng:* `out\wally_evidence_v1013\` (unit_final3, chain_avd_log*.txt, nav/upgrade/timer JSON, superseded_final1, superseded_build1), `out\wally_live_v1013\evidence\live\` (phase0/b, nav Android 9, F4, provider_poll.log, diag_askback_*), `...\superseded_final1\live\` (Live Eval lượt 1 `aeae3def…`: live_raw.jsonl, live_results.json), `out\wally_live_v1012\evidence\{fix*,holdout,routing,bench,smoke}\`, `out\wally_candidate_v1013\`.
