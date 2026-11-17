# HANDOFF — Single Writer (WALLY v1.0.13, trạng thái BLOCKED_EXTERNAL)

```
HANDOFF — Single Writer
Từ (writer cũ):            AI-Master
Sang (writer mới):         Reviewer / Git Controller (theo Owner Authorization FINAL VALIDATION v1.0.13, Git phương án A)
Thời điểm:                 2026-11-16 12:36 (UTC+7)
Người chỉ định:            Owner (Git phương án A, bàn giao sau khi xong) + Manager (Directive WALLY v1.0.13 EXTERNAL BLOCKER: "khôi phục WallyQA và bàn giao writer lease nếu polling kết thúc mà route vẫn lỗi")
Phạm vi được phép sửa:     chỉ hồ sơ/tài liệu và vệ sinh Git: dọn nguồn, PII, tệp sao lưu, quét bí mật, đồng bộ tài liệu.
                           KHÔNG sửa `app/src`, `app/build.gradle.kts`, test (candidate đã cố định tại SHA bên dưới; đổi mã = SHA mới = chạy lại toàn bộ Live Eval).
                           KHÔNG push, merge `main`, force-push, rewrite history hay publish nếu chưa có thẩm quyền của Owner.
Trạng thái cây mã bàn giao: manifest nguồn `out\wally_candidate_v1013\MANIFEST_SHA256_after.txt` (130 tệp, kiểm lại 130/130 OK lúc bàn giao) có SHA-256 f441d01dd3eb27bb2225603943ee9e6c816fce493581f3e41e241c07e190d26b;
                           tar nguồn `source_v1.0.13.tar` SHA-256 2cda1e764626d043c02e94a28ebc0abbc998abbeb58a3bc680a41ffb991a55bf;
                           APK ứng viên SHA-256 bfc00ae9e444d5aaeee9d11ff58f1471bda16d8dbcf35cce5e96d731a9788a2b (versionCode 14 / 1.0.13, wally-prompt-7).
                           Git: nhánh `wally-v1.0.12-ai-nav-fix`, HEAD 195a60c; 22 tệp đã theo dõi bị sửa, 31 tệp chưa theo dõi; KHÔNG commit/push/merge nào do AI-Master thực hiện.
Việc đang dở / không được tiếp tục:
  - Live Eval v1.2 + holdout 96 câu trên đúng APK `bfc00ae9…` CHƯA chạy: BLOCKED_EXTERNAL (tuyến `cinb/gemini-3.8-flash` trả 503 / upstream 403 ToS, 17/17 lần thăm dò 11:41–12:30). Polling đã kết thúc, KHÔNG gia hạn.
  - Tiếp tục khi (a) Owner/Foza báo tuyến Gemini hoạt động lại, hoặc (b) Owner duyệt cấu hình thay thế (Manager khuyến nghị Claude Sonnet 4.6; kết quả phải gắn đúng model/prompt/SHA và không dùng để tuyên bố Gemini PASS). Việc đó chỉ ghi vào `out\wally_live_v1013\evidence\`, không đụng `app/src`; cần người điều hành giao lại quyền cho AI-Master.
  - Android 16: không boot lại AVD (máy chủ tự khởi động lại cứng 3 lần khi AVD chạy). Các bước chưa làm trên SHA cuối: chip xúc xắc, cài mới trên AVD, quét logcat (NOT_TESTED).
  - Cờ KHÔNG được cấp: AI_LIVE_PASS, DEVICE_PASS, F6_DEVICE_VERIFIED, KBD-01, SPEC_ACCEPTED.
Việc Git Controller nên làm (đề xuất, chưa làm):
  - Quét bí mật và PII trên cây + lịch sử: lịch sử git VẪN chứa khóa Foza cũ trong `ai_test/test_foza_ai.ts` (3 commit; đã được Owner thu hồi; phương án A = không rewrite). Cây làm việc dùng bản đọc từ `FOZA_API_KEY`. AI-Master đã quét 1.778 tệp (evidence, tài liệu, nguồn, gói candidate): 0 lần xuất hiện khóa QA mới (đủ, 6 đầu, 4 cuối). Chưa quét PII (tên người dùng Windows trong đường dẫn, tác giả git).
  - Rà tệp sao lưu/rác trong cây (`*.orig`, `*.bak`, `*.before_navdiag`…): lúc bàn giao không có tệp nào như vậy trong `app/` hay git status; thư mục `out/` bị gitignore.
  - Đồng bộ tài liệu: `WALLY_V1013_CLOSURE_DIGEST.md` (bản duy nhất), `WALLY_V1013_LIVE_EVAL_RUNBOOK.md`, `WALLY_OWNER_CHECKLIST_V1013.md`, `.agents/AGENTS.md` (mục 11–14). Các tài liệu của v1.0.12 giữ nguyên.
Môi trường QA lúc bàn giao (đã khôi phục): WallyQA đã `quit`, root TẮT, múi giờ `Asia/Ho_Chi_Minh` + tự động múi giờ BẬT, ứng dụng đã gỡ (không còn khóa QA trong guest), không còn rule iptables, không còn `RUN_LIVE.flag`, không có tiến trình emulator/QA nào, `adb devices` rỗng; AVD không chạy. Instance Owner (index 0), tiến trình LDPlayer dùng chung và điện thoại thật không bị chạm. Tệp khóa QA `<OWNER_QA_KEY_FILE>` thuộc Owner, nằm ngoài kho mã.
Ngân sách: ước tính cận trên phiên này ≈ US$0,34 (≈ US$0,98 gồm mốc US$0,641423 của Owner); còn ≈ US$4,02 dưới trần US$5,00. Đối chiếu bảng điều khiển trước khi chạy cấu hình thay thế.
Xác nhận writer cũ ngừng ghi: CÓ — AI-Master không sửa cây mã/tài liệu nào sau 2026-11-16 12:36 (UTC+7), trừ khi được giao lại quyền bằng văn bản.
```

## Lease trả lại cho AI-Master (2026-11-16, theo OWNER AUTHORIZATION — WALLY CLAUDE ALTERNATIVE VALIDATION)

```
HANDOFF — Single Writer (chiều ngược)
Từ (writer hiện tại):      Reviewer / Git Controller (không ghi gì vào cây từ 12:36; kiểm lại lúc nhận: manifest nguồn 130/130 OK, `git status` 54 dòng như lúc bàn giao, không có commit/reflog mới, APK `bfc00ae9…` nguyên vẹn)
Sang (writer mới):         AI-Master
Người chỉ định:            Owner ("Nhận lại writer lease từ Reviewer/Git Controller theo quy trình Single Writer")
Phạm vi được phép sửa:     bằng chứng và tài liệu (`out\`, tài liệu gốc repo). KHÔNG sửa `app/src`, `app/build.gradle.kts`, test trừ khi Claude phát hiện lỗi trong phạm vi reliability hiện hành; khi đó phải build candidate mới và xác minh lại theo phạm vi ảnh hưởng.
Trạng thái cây mã nhận về: giống mục trên (manifest f441d01d…, tar 2cda1e76…, APK bfc00ae9…)
Việc được làm: kiểm chứng cấu hình Claude (`cinb/claude-sonnet-4-6`, đổi model bằng cấu hình runtime trong ứng dụng, giữ nguyên APK); không benchmark lại 5 model; không Hybrid Routing; không AVD.
Xác nhận writer cũ ngừng ghi: theo ghi nhận lúc nhận (không có thay đổi cây mã).
```
