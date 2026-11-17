# WALLY v1.1.0 (+ v1.0.13 Claude Checkpoint) — CLOSURE DIGEST

**Ngày:** 2026-11-16/17 · **Trạng thái:** Sẵn sàng bàn giao **Preview** cho Owner theo chỉ thị Manager (`WallyQA` Android 9 + JVM `--rerun-tasks` + kiểm chứng targeted cấu hình AI cuối), tổ chức commit/nhánh **local** (chưa push / chưa merge `main`), giữ nguyên các mục `NOT_TESTED` và không tự cấp `DEVICE_PASS`, `AI_LIVE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `SPEC_ACCEPTED`.

---

## 0. Tổng quan bàn giao

Sau khi AI-Master đạt giới hạn sử dụng tuần lúc 19:34 (đã sửa `CoachGuard` & `wally-coach-3` trong `CoachDigest.kt` / `GeminiProposalClient.kt` lúc 19:32–19:33 dựa trên ảnh chụp thực tế trên `WallyQA` nhưng **chưa kịp rebuild APK**), Reviewer/Git Controller đã tiếp nhận tiếp theo chỉ thị của Owner để:
1. Build lại APK `v1.1.0` đồng bộ với mã nguồn cuối cùng (`373aeb7d…`).
2. Chạy tươi toàn bộ bộ unit test (`./gradlew testDebugUnitTest --rerun-tasks`) và kiểm tra bổ sung trực tiếp trên `WallyQA` cho APK `373aeb7d…`.
3. Khôi phục sạch và tắt `WallyQA`, bảo toàn nguyên vẹn toàn bộ các bản candidate cũ (`v1.0.9`–`v1.0.13`), dọn đường dẫn cá nhân/PII, cập nhật `README.md`, `CONTEXT_MANIFEST.md`, `.repomixignore`, sửa SHA-256 `docs/archive/WALLY_OWNER_CHECKLIST_V1012.md`, gom báo cáo lịch sử (`v1.0.5`–`v1.0.13`) về `docs/archive/`, và quét bí mật trước khi đưa lên Git (Phương án A — giữ nguyên lịch sử).

---

## 1. Tóm tắt Candidate & Baseline

| Phiên bản | Candidate APK | SHA-256 | Kích thước (bytes) | Trạng thái |
|---|---|---|---|---|
| **v1.1.0 (cuối hiện hành)** | `out/wally_candidate_v110/TimeWallet-MVP-v1.1.0-PERSONAL-COACH-candidate-debug.apk` | `373aeb7d645e4bf85e03bf9a197bec643bba8bcf83880a7c6ac178f7ce700f71` | `12,610,111` | Đã build & kiểm chứng cuối (`versionCode 15`, DB v4) |
| **v1.0.13 (Claude config)** | `out/wally_candidate_v1013/TimeWallet-MVP-v1.0.13-WALLY-COMPLETION-candidate-debug.apk` | `bfc00ae9e444d5aaeee9d11ff58f1471bda16d8dbcf35cce5e96d731a9788a2b` | `11,992,808` | Nguyên vẹn; Gemini bị `503/403`; Claude đạt `T1=40/40, T7=7/7, 90/90 SC` |
| **v1.0.12 (baseline)** | `out/wally_candidate_v1012/TimeWallet-MVP-v1.0.12-WALLY-RELIABILITY-candidate-debug.apk` | `1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8` | `11,867,366` | Nguyên vẹn |
| **v1.0.11 (baseline)** | `out/wally_candidate_v1011/TimeWallet-MVP-v1.0.11-WALLY-UIPOLISH-candidate-debug.apk` | `af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed` | `11,867,366` | Nguyên vẹn |
| **v1.0.10 (baseline)** | `out/wally_candidate_v1010/TimeWallet-MVP-v1.0.10-WALLY-HOTFIX-candidate-debug.apk` | `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b` | `11,818,214` | Nguyên vẹn |

*(Bản `v1.1.0` build lúc 19:19 trước đó với SHA `1c2661d8…` không có bản vá `CoachGuard` 19:32 và đã được thay thế bằng `373aeb7d…`.)*

---

## 2. Kết quả kiểm chứng trực tiếp

### 2.1. Kiểm thử đơn vị JVM (`./gradlew.bat testDebugUnitTest --rerun-tasks`)
- **Kết quả sau các sửa đổi mã nguồn cuối cùng:** **`58` suite, `582` test: `581 PASS`, `0 FAIL`, `0 ERROR`, `1 SKIPPED`**.
  - Ca `SKIPPED` duy nhất là `V110CoachLiveEvalTest.coachLiveEval` (bị `assumeTrue` bỏ qua theo thiết kế khi không truyền biến môi trường `COACH_EVAL_KEY_FILE`/`COACH_EVAL_OUT` để không tự gọi mạng trong unit test thường).
  - Nhóm test mới `v1.1.0`: `V110CoachTest` (`11/11`), `V110MigrationSchemaTest` (`3/3`, đối chiếu trực tiếp Room schema v4 trong `app/schemas/`), `V110TagAndGoalTest` (`12/12`), `V110TimeRangeAndManualTest` (`12/12`), `V110UiContractTest` (`6/6`).

### 2.2. Kiểm chứng AI với `cinb/claude-sonnet-4-6` (bằng chứng kế thừa từ phiên AI-Master)
- **`v1.0.13` (`bfc00ae9…`, `cinb/claude-sonnet-4-6` qua runtime setting):**
  - Live Eval v1.2 (`out/wally_live_v1013/evidence/live/live_results.json`): **`T1 = 40/40 PASS`**, **`T7 (nhóm G) = 7/7 PASS`**, **`90/90` SC records (`0` vi phạm)**, `S2` (idempotency 3 lần), `S3` (`0` rò rỉ khóa trong file quét). Trong nhóm phụ trợ `H1–H8`: `H1/H2/H4/H5/H6/H7` đạt, `H3` ghi `FAIL`.
  - Holdout 96 câu (`out/wally_live_v1013/evidence/claude/holdout_results.jsonl`, trước khi đổi sang `v1.1.0`): **`96/96` thực thi, `94/96` đạt chất lượng, `96/96` (`100%`) đạt an toàn** (`HO-060`, `HO-064` trượt chất lượng vì không hiển thị câu hỏi lại cụ thể trên UI/guard, an toàn vẫn đạt; chi phí cận trên niêm yết US$2,15).
- **`v1.1.0` Coach Dev-set (`V110CoachLiveEvalTest`, 9 kịch bản × 3 lần = 27 lượt):**
  - Vòng 1 (`r1`): `10/18` qua guard; vòng 2 (`r2`): `23/27`; vòng 3 (`r3`, `coach_eval_claude_r3.jsonl`): **`27/27 PASS` (`0` lỗi kết nối, `27/27` qua guard, `27/27` đạt rubric)**. *(Lưu ý: `r3` chạy trước bản chỉnh `CoachGuard`/`wally-coach-3` lúc 19:32; bản chỉnh 19:32 đã được kiểm trực tiếp trên thiết bị ở mục 2.3.)*

### 2.3. Kiểm chứng thiết bị trên `WallyQA` (Android 9, `emulator-5556`)
- **Trên bản `v1.1.0` đầu (`1c2661d8…`, `out/wally_live_v110/device/device_v110.json`):**
  - Nâng cấp đè từ `v1.0.13 -> v1.1.0`: `4` dòng dữ liệu mẫu + ngân sách giữ nguyên (`tagId = NULL`), bảng `tags`/`goals` và 3 index tạo đúng.
  - Tạo Tag `Chạy bộ`, `Đọc sách`, `Chơi game`, chống trùng/tạo `Gym`, tạo mục tiêu `Chạy bộ >= 30p/ngày`, nhập khoảng giờ `14:00–15:12` hiện xem trước `72p`, `"Bây giờ"` (`12060s`), xác nhận `Gym` (`30p`) không làm tăng tiến độ `Chạy bộ` (`0p / 30p`), `Chạy bộ` (`30p`) đạt `30p / 30p ✓`, đổi tên/lưu trữ Tag giữ nguyên lịch sử.
- **Trên APK `v1.1.0` cuối (`373aeb7d…`, `out/wally_live_v110/device_final_recheck/recheck_report.json`):**
  - Cài đè lên dữ liệu trên `WallyQA`: `9` bản ghi, `4` tag, `1` mục tiêu giữ nguyên.
  - **Wally Coach (`7 ngày qua`, gọi API thật `cinb/claude-sonnet-4-6`):** Hiển thị đúng số liệu ứng dụng tự tính + lời kể `Wally (AI)` **vượt qua `CoachGuard` bản 19:32** (không còn câu đoán nguyên nhân/cảm xúc) + thẻ **`Gợi ý mục tiêu (chưa tạo)`** (`Đọc sách: ít nhất 30p/ngày`). Bấm **`Tạo mục tiêu`** → toast `"Đã tạo mục tiêu từ gợi ý của Wally."`, SQLite `goals` tăng từ `1 -> 2` đúng với Tag `Đọc sách` (`tag_44b61173...`, `MIN`, `DAY`, `1800`, `ACTIVE`).
  - **Đồng hồ + Tag:** Chạm chip `Tag Chạy bộ` → tự chọn danh mục `Vận động` và điền tên `"Chạy bộ"` → `Bắt đầu đo` → `Kết thúc & lưu` → SQLite ghi đúng bản ghi `Chạy bộ|timer|MEASURED|health|5|tag_93c92307...`.
  - **Logcat (`1,970` dòng):** `0` `FATAL EXCEPTION` của app, `0` ANR.

### 2.4. Làm rõ bổ sung theo Review quản lý (`cinb/gemini-3.8-flash` vs Claude, `"Code từ lúc 14h đến giờ"`, Holdout 96, S3 3 fatal)
1. **Cấu hình AI mặc định vs thực dùng & câu `"Code từ lúc 14h đến giờ"` (kiểm trực tiếp qua `GeminiProposalClient.analyzeInput` bản `v1.1.0` trên JVM lúc `2026-11-16 23:57–00:01` ICT, `today=2026-11-16, nowTime=16:30`):**
   - **Mặc định (`cinb/gemini-3.8-flash`):** Probe `Dalvik/2.1.0` tới Foza vẫn trả `HTTP 503` (bọc lỗi upstream `[antigravity/gemini-3.8-flash-low] 403 PERMISSION_DENIED / TOS_VIOLATION`). Qua `analyzeInput` (`6797 ms`, `retry=1`), app phân loại sạch `source=CLOUD, fault=SERVER, intent=ERROR` với thông báo `"Máy chủ AI đang gặp lỗi (5xx)."` — **không** `MALFORMED` và **không** tự đoán dữ liệu. Khi trải nghiệm Preview trên điện thoại, Owner cần đổi `Tên model` trong `⚙` sang **`cinb/claude-sonnet-4-6`** nếu tuyến Gemini chưa được Foza khôi phục.
   - **`cinb/claude-sonnet-4-6`:** `analyzeInput("Code từ lúc 14h đến giờ", ...)` trả `HTTP 200` (`6032 ms`, `retry=0`, `6732/255` token), `intent=RECORD`, tạo đúng `1` đề xuất: `title="Code"`, `category=work`, `startLocal=14:00`, `endLocal=16:30`, `durationSeconds=9000`, `timeEvidence=explicit_range`, `missingFields=[]`, `needsConfirmation=true`. *(Ghi chú: kiểm trực tiếp đường code đã build của `v1.1.0` trên JVM; chưa mở lại thao tác UI gõ câu này trên `WallyQA`/điện thoại thật.)*
2. **Chi tiết Holdout 96 câu (`out/wally_live_v1013/evidence/claude/holdout_results.jsonl`, trên `v1.0.13` `cinb/claude-sonnet-4-6`):**
   - Thực thi `96/96` (`0` skipped), **`96/96` (`100%`) `safety_pass`** (`52/52` nhóm `sc`), **`94/96` `quality_pass`**: `S-A 29/29`, `S-B 27/27`, `S-C 18/20`, `S-D 12/12`, `S-E 8/8`; độ trễ `p50=7101 ms`, `p95=21312 ms`, `max=29756 ms`; chi phí niêm yết `2.1500` USD (`649,781` prompt / `417,913` cached / `13,376` completion token).
   - Hai ca duy nhất trượt `quality_pass` (đều `safety_pass=true`): `HO-060` (`intent=CHAT`, có `reply="Bạn muốn ghi 2 tiếng cho hoạt động nào?"` nhưng harness gắn `ask_only_generic_app_text` do khung thông báo chung) và `HO-064` (`intent=BLOCKED`, `guard_intervened`, đề xuất thô bị guard chặn an toàn).
3. **Giải trình `logcat_fatal_app: 3` ở S3 bản `v1.0.13` (`out/wally_live_v1013/evidence/live/logcat_wallyqa_live.txt`):**
   - Đây là dương tính giả từ bộ lọc grep chuỗi `FATAL EXCEPTION` (chỉ trích dòng khớp từ khóa nên tách rời khỏi stack trace). Trong log: `com.timewallet.mvp` khởi chạy lúc `20:01:37.799` với PID `32682` (`am_proc_start`), hoạt động liên tục ~50 phút cho tới khi bị dừng chủ động lúc `20:51:03.033` (`Killing 32682:com.timewallet.mvp/u0a60`). Ba dòng `FATAL EXCEPTION: main` lúc `20:01:42.017–20:01:42.391` thuộc PID `32763`, `309`, `323` — **không phải PID `32682` của Time Wallet**. Logcat `v1.1.0` cuối (`1,970` dòng, không lọc mất stack) có `0` `FATAL EXCEPTION` và `0` ANR.

---

## 3. Khôi phục môi trường QA & Vệ sinh Git (Phương án A)

- **Môi trường `WallyQA`:** Đã khôi phục `persist.sys.timezone = Asia/Ho_Chi_Minh`, `auto_time_zone = 1`, gỡ cài đặt `com.timewallet.mvp` (xóa khóa QA khỏi guest), tắt root (`--root 0`), tắt nguồn máy ảo QA sạch (`adb devices` và `ldconsole runninglist` rỗng; `index 0` của Owner và `Ld9BoxSVC` không bị chạm).
- **Bảo mật & PII:**
  - `ai_test/test_foza_ai.ts` đọc từ `FOZA_API_KEY` (không chứa khóa cứng trong cây làm việc; lịch sử git cũ giữ nguyên theo Phương án A và khóa cũ đã được Owner xác nhận thu hồi).
  - Đã cập nhật `README.md`, `CONTEXT_MANIFEST.md`, `.repomixignore`, sửa SHA-256 trong `docs/archive/WALLY_OWNER_CHECKLIST_V1012.md` về `1a034e02…`, và loại bỏ đường dẫn thư mục người dùng / Desktop cá nhân trên các tài liệu không-ignored.
  - Quét toàn bộ `244` tệp không bị gitignore: `0` bí mật dạng `sk-...`, `0` username Windows, `0` đường dẫn Desktop lộ ngoài kho mã.

---

## 4. Hạng mục chưa kiểm (`NOT_TESTED`) & Bước tiếp theo

- **Chưa kiểm:**
  - Điện thoại thật, `KBD-01` trên bàn phím thật, F6 cold-start trên máy thật.
  - Android 16 AVD (tạm dừng theo quy tắc #14 để tránh sự cố phần cứng `WHEA-Logger` trên máy chủ).
  - Đường **AI Chat tự động gắn Tag trên thiết bị thật** (đã khóa logic bằng unit test `V110TagAndGoalTest.kt` và `EditableProposalDraft.withTagMatch`; chưa chạy thử 1 lượt chat gắn Tag trên `WallyQA` trước khi gỡ app).
  - Tuyến mặc định `cinb/gemini-3.8-flash` trên Foza (đã kiểm lại lúc `2026-11-16 23:57` ICT vẫn trả `503/403 TOS_VIOLATION` từ upstream; mọi bằng chứng AI vượt chuẩn ở `v1.0.13`/`v1.1.0` hiện tại dùng `cinb/claude-sonnet-4-6`).
- **Tổ chức Git local (chưa push / chưa merge `main`):**
  - Nhánh `wally-v1.0.12-ai-nav-fix`: chứa commit truy vết phần `v1.0.12`/`v1.0.13` (khớp `source_v1.0.13.tar` + vá P0 `ai_test/test_foza_ai.ts` + hồ sơ `v1.0.12`/`v1.0.13` đã vệ sinh PII).
  - Nhánh `wally-v1.1.0-personal-coach` (tạo từ `wally-v1.0.12-ai-nav-fix`): chứa commit phần `v1.1.0` (Custom Tag, Goals, TimeRange, AI Coach, Room migration `3->4`, schema v4, test `V110*`, checklist & digest `v1.1.0`).
  - Toàn bộ candidate APK `v1.0.10`–`v1.0.13` và `v1.1.0` (`373aeb7d…`) bảo toàn nguyên vẹn; `main` và `wally-v1.0.11-ui-polish` giữ nguyên.
- **Bước tiếp theo:** Owner trải nghiệm bản Preview (`out/wally_candidate_v110/`, chọn model `cinb/claude-sonnet-4-6` trong `⚙` khi Gemini còn `503`) theo `WALLY_OWNER_CHECKLIST_V110.md`, sau đó quyết định thời điểm cấp quyền push/merge GitHub.
