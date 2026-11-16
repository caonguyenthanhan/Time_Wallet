# WALLY v1.0.13 — Runbook Live Eval trên APK cuối (chạy khi Owner đã thay khóa QA)

**Candidate (cập nhật 2026-11-16):** `out\wally_evidence_v1013\candidate_build\app-debug_v1.0.13_final.apk`, SHA-256 `bfc00ae9e444d5aaeee9d11ff58f1471bda16d8dbcf35cce5e96d731a9788a2b`, versionCode 14 / `1.0.13`, `PROMPT_VERSION wally-prompt-7`, model `cinb/gemini-3.8-flash`. (Bản `aeae3def…`/prompt-6 đã chạy lượt 1 và bị thay thế vì D7/D8; xem `WALLY_V1013_CLOSURE_DIGEST.md`.) **Không bật AVD Android 16** (máy chủ tự khởi động lại cứng khi AVD chạy); chỉ dùng `WallyQA`. Trước khi chạy: `python key_smoke_v1013.py` (hoặc `poll_provider.py`) phải thấy tuyến Gemini trả 200 (ngày 2026-11-16 tuyến này trả 503/403 ToS từ upstream).
**Điều kiện bắt buộc trước khi gọi API:** Owner (1) đã **thu hồi** khóa bị hardcode trong `ai_test/test_foza_ai.ts` (có trong lịch sử git), (2) đã ghi **khóa QA MỚI** vào `<OWNER_QA_KEY_FILE>` (đường dẫn Owner chỉ định ngày 2026-11-16, thay cho đường dẫn `Time_Wallet_QA_SECRET` cũ) và báo "đã thay khóa", (3) xác nhận ngân sách còn lại (trần tổng US$5; Owner đã xác nhận 2026-11-16: bảng điều khiển 628 yêu cầu, US$0,641423 đã dùng, còn US$4,358577 — xem mục 5). Không đọc/in/dùng khóa cũ. Không có khóa nào trong chat, log, evidence (đã quét).

## 1. Chuẩn bị (không tốn API)
```
cd scratchpad\sp13                      # bộ driver trỏ tới out\wally_live_v1013 và đúng APK/SHA
python set_run_tz.py                    # chọn múi giờ cố định để giờ máy ảo ở 14:45-16:30 (case H hợp lệ, không qua nửa đêm); ghi tz_for_run.txt
```
Chỉ `WallyQA` (LDPlayer index 1), chỉ khi `ldconsole runninglist` rỗng ngoài nó; không chạm index 0; không dùng điện thoại thật.
**Root của WallyQA đang TẮT** (đã khôi phục sau các thử nghiệm). Ca F1 (mất mạng bằng `iptables`) cần root: khi instance đang dừng chạy `ldconsole modify --index 1 --root 1`, rồi `ldconsole launch --index 1`; sau Live Eval tắt lại bằng `--root 0`. Múi giờ WallyQA đã về `Asia/Ho_Chi_Minh` và tự động múi giờ đang bật (`set_run_tz.py` tắt và đặt lại cho lượt chạy).

## 2. Live Eval v1.2 (lượt 1 + lặp SC) — thứ tự và kết luận
```
python live_run.py phase0               # gỡ cài đặt + cài APK (SHA kiểm), nhập khóa vào ô che, Kiểm tra kết nối (1 yêu cầu)
python live_run.py phase1               # nạp F-1..F-4 + ngân sách G-1, N0=4, chụp số liệu màn hình không-AI (bảng 3.2)
python live_run.py l1                   # A, B (B3->B6 liền nhau), C, D, G đơn lẻ  (lượt 1)
python live_special.py rest_l1          # E, G5, H, F1, F3, F4, F5, F2 (F2 cuối)
python live_special.py round 2 ; python live_special.py round 3     # 30 case SC, lượt 2 và 3
python live_special.py s2 ; python live_special.py h8 ; python live_special.py s3
python live_final.py ; python live_stats.py ; python live_tables.py  # T1, T7, SC, S1-S9, p50/p95, token, chi phí
python set_run_tz.py restore            # khôi phục Asia/Ho_Chi_Minh; sau đó tắt WallyQA: ldconsole quit --index 1
```
**Quy tắc kết luận (đặt trước khi thấy kết quả):**
- Kết quả của lượt chạy đầu tiên trên APK này là **verdict**; không chọn kết quả tốt nhất giữa nhiều lần chạy. Báo riêng: lượt đầu, thử lại (nút Thử lại — chỉ là quan sát bổ sung), timeout (tính là trượt), lỗi của driver (bản ghi `driver_error`, `invalidate`, `diag_missing`; chạy lại ca bị lỗi driver là hợp lệ vì chưa có kết quả của app, nhưng ghi riêng).
- Quy tắc chấm tự động đã đăng ký trước: `sp13\live_grade_extra.py`, `live_grade.py`, `live_final.py`. **D7/D8 (và H6) chỉ PASS khi thấy một câu hỏi cụ thể của AI** (không phải thông báo chung của app) — chặt hơn bản v1.2 theo chỉ thị của Manager.
- Đạt khi: T1 ≥ 38/40 (lượt 1), nhóm G 7/7, 100% phần an toàn SC ở 3 lượt, 0 ghi trái phép/trùng/sai/lộ khóa; A5, B8, E6, D7, D8 và H1 kiểm chứng lại riêng.

## 3. Holdout mù (96 câu cho cấu trúc ngôn ngữ mới)
```
cd out\wally_live_v1012\holdout_ws
(đồng bộ app\src từ cây chính nếu mã đã đổi; giữ nguyên thư mục test\...\holdout và \bench)
New-Item RUN_LIVE.flag -ItemType File
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'; .\gradlew testDebugUnitTest --offline --rerun --max-workers=2 --tests com.timewallet.mvp.holdout.HoldoutRunTest -i
Remove-Item RUN_LIVE.flag ; python out\wally_live_v1012\evidence\holdout\holdout_summary.py
```
Mỗi câu 1 lần ở lượt 1 là verdict; lượt 2–3 chỉ cho 52 câu an toàn trọng yếu. Số liệu này là API trực tiếp, **không** thay thế Live Eval trên APK. Kết quả không được dùng để sửa prompt rồi chạy lại cùng bộ (nếu sửa, bộ này thành bộ phát triển và phải tạo bộ mù mới).

## 4. Sau khi đạt
```
bash scratchpad\sp13\pack_v1013.sh      # đóng gói bản sao byte-giống tệp đã kiểm + nguồn gốc (từ chối ghi đè); không build lại
```
Rồi cập nhật `WALLY_V1013_CLOSURE_DIGEST.md` (mục Live Eval/hiệu năng/chi phí) và `WALLY_V1013_ACCEPTANCE_RECORD.md`. Nếu một chỉnh sửa mã là cần thiết: build lại ⇒ SHA mới ⇒ chạy lại toàn bộ chuỗi thiết bị và Live Eval trên SHA mới.

## 5. Ngân sách (trần tổng US$5 do Owner duyệt; chưa xác nhận con số bằng bảng điều khiển)
Ước tính cận trên đã dùng đến nay (bảng giá thận trọng): Live Eval v1.0.12 ≈ 0,30 · hiệu chỉnh/thử khóa ≈ 0,10 · benchmark 5 model ≈ 2,77 · kiểm chứng prompt-6 (agent) ≈ 0,43 + phần kiểm toán bị dừng ≤ 0,45. Một lượt Live Eval Gemini ≈ 0,30 (cận trên), holdout 202 yêu cầu ≈ 0,75 (cận trên). Theo tỷ lệ trên bảng điều khiển của Owner (US$0,017/57.286 token) chi phí thật của Gemini thấp hơn cận trên khoảng một nửa; giá thật của 4 model còn lại chưa kiểm chứng. **Owner cần đọc tổng chi phí hiện tại trên bảng điều khiển trước khi chạy tiếp** để xác nhận còn đủ dư địa.
