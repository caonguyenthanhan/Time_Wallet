# Time Wallet `v2.1.0` (`versionCode 19`) — Adaptive Wally Context & 3-Layer Insights Candidate

**Ngày lập:** 2026-11-22  
**Mục tiêu:** Nâng từ `v2.0.0` (`versionCode 18`) lên **`v2.1.0` (`versionCode 19`)** với hai trọng tâm thực chất:
1. **Wally Intelligence — Chặng 1 (Phương án A · Adaptive Context) & State Slicing:** tách điều phối ngữ cảnh ra khỏi `TimeWalletViewModel` (`WallyIntelligenceCoordinator`, `AdaptiveContextRouter`, `WallyContextEngine`), gắn ngữ cảnh có kiểm soát quyền đọc (`AiReadPermissions`) chỉ cho câu hỏi tư vấn kế hoạch & hội thoại nối tiếp có TTL 30 phút, và tách nhịp tick 1 giây ra `timerTickState`.
2. **Màn hình Thống kê 3 lớp (`Mở rộng › Thống kê`) & Wally kể từ dữ liệu:** chuyển mặc định sang góc nhìn **Hoạt động thực tế** (`Ngủ, nghỉ · Công việc · Học tập · Sức khỏe · Giải trí & Khác` vs `Chưa ghi nhận`), giữ nút chuyển nhanh `Hoạt động | Mục tiêu`, đổi nhãn sang **`Đã ghi nhận: X / 24h`**, thêm phân tích Tag/hoạt động chi tiết + **`% so với tuần trước`**, giải thích tự động mục tiêu `0p` khi thiếu gắn Tag, và hiển thị câu chuyện 4 bước **`Wally kể từ dữ liệu`** kèm ghi chú thận trọng khi dữ liệu chưa đầy đủ.

---

## 1. Tệp bàn giao & Định danh bản build (`v2.1.0`)

| Thành phần | Giá trị / Đường dẫn |
|---|---|
| APK Production Candidate (Release) | `out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk` |
| Dung lượng Release | `9,058,921` bytes |
| SHA-256 Release | `005378EFD99484A82BB3D22C8510BBD6B3D9285FD9843F748BBA74BAA4AB51D0` |
| Tệp `.sha256` Release | `out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk.sha256` |
| APK Debug (dành cho nhánh Debug) | `out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-candidate-debug.apk` (`13,452,888` bytes, SHA-256 `78A3455E6F0049777C0AC08B5CD7B53C471A3C98CEC97A5D03D9C7B68857CDDF`) |
| Package / Version / SDK | `com.timewallet.mvp` · `versionName = "2.1.0"` · `versionCode = 19` (`minSdk 26`, `targetSdk 35`) |
| Chứng thư Release | `CN=Time Wallet Personal Release, OU=Mobile, O=TimeWallet, L=Hanoi, ST=HN, C=VN` (`SHA-256: 39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0`) |
| Keystore cá nhân | Lưu **ngoài Git** tại `%USERPROFILE%\.timewallet\` (chặn rò rỉ qua `.gitignore`, `.repomixignore` và `WallySecurityStaticTest`) |
| Baseline trước (`v2.0.0`) | [WALLY_V200_CANDIDATE.md](WALLY_V200_CANDIDATE.md) (`BEDEE190…` / `9CEA64A0…`, `versionCode 18`) |
| Thư viện ảnh & Dữ liệu QA | [docs/screenshots/ui-preview/README.md](docs/screenshots/ui-preview/README.md), `out/wally_candidate_v200/mock_1week_v200_qa.json` |

---

## 2. Thay đổi kiến trúc & sản phẩm chính trong `v2.1.0`

1. **Adaptive Context (Phương án A) & State Slicing (`AdaptiveContextRouter.kt`, `WallyContextEngine.kt`, `WallyIntelligenceCoordinator.kt`, `TimeWalletViewModel.kt`, `MainActivity.kt`):**
   - `AdaptiveContextRouter.decide(...)` định tuyến hoàn toàn cục bộ (0 tốn thêm request AI): giữ `ContextRouteMode.NONE` cho ghi nhận đơn lẻ (`"Đọc sách 30 phút"`), hỏi số liệu (`"Hôm nay học bao nhiêu?"`), lệnh hủy và follow-up chỉ hỏi số liệu; chỉ bật `ADAPTIVE` cho câu tư vấn/kế hoạch hoặc follow-up nối tiếp chủ đề tư vấn đang mở.
   - `WallyContextEngine` xây ngữ cảnh giới hạn kích thước (`MAX_CONTEXT_CHARS`), kiểm soát nghiêm `AiReadPermissions`, vô hiệu hóa `ConversationState` khi hết TTL (`30m`) hoặc thay đổi quyền, tính mục tiêu thiếu hụt (`computeGoalDeficits`), khung giờ trống thực tế (`computeFeasibleSlots` qua `ScheduleGridEngine`) và cảnh báo giao cắt lịch (`detectScheduleConflictWarnings`) cả trên bản ghi thực tế lẫn `PLANNED`.
   - Chia sẻ tóm tắt AI Coach đã qua kiểm chứng (`recordVerifiedCoachSummary`) sang ngữ cảnh Chat, giữ nguyên bất biến V108 (không lộ free-text kèm đề xuất chưa xác nhận) và `staticSystemPrompt` (`wally-prompt-8`).
   - Tách `TimerTickState` (`viewModel.timerTickState`) khỏi `TimeWalletUiState`, giảm recomposition toàn màn hình mỗi giây và bỏ tính lại thống kê dư thừa.
2. **Thống kê 3 lớp & Wally kể từ dữ liệu (`LedgerAnalyticsCalculator.kt`, `InsightsScreen.kt`):**
   - **Lớp 1 — Thời gian thực tế:** Góc nhìn mặc định **`Hoạt động`** nhóm 24h × 7 ngày theo `ActivityBucket` (`Ngủ, nghỉ`, `Công việc`, `Học tập`, `Sức khỏe & Sinh hoạt`, `Giải trí & Khác`) kèm bảo toàn tổng giây đúng bằng `recordedTimedSeconds` trên từng cột 24h, tách bạch `Chưa ghi nhận` và `Chưa gắn giờ`, giữ nút chuyển nhanh `Hoạt động | Mục tiêu`, đổi nhãn thành `"Đã ghi nhận: X / 24h"`, hiện danh sách Tag/hoạt động cụ thể và `% so với tuần trước`.
   - **Lớp 2 — Mức độ phù hợp mục tiêu:** Hiện tiến độ `MIN`/`MAX` (`0p / 1h` rõ ràng khi `0s`), chẩn đoán khi mục tiêu `TAG` đang `0p` nhưng cùng danh mục có bản ghi chưa gắn Tag (`hintVi`), đồng thời khẳng định vai trò phục hồi của `Ngủ, nghỉ / Sức khỏe / Phi mục tiêu`.
   - **Lớp 3 — `Wally kể từ dữ liệu`:** Tổng hợp câu chuyện 4 bước (`Điều gì xảy ra` → `Đáng chú ý` → `Ảnh hưởng mục tiêu` → `Có thể làm tiếp`) dựa trên dữ liệu thực & khoảng trống từ `ScheduleGridEngine`, kèm ghi chú thận trọng (`uncertaintyNote`) khi thiếu ngày hoặc ngày hiện tại chưa kết thúc.

---

## 3. Bảng kết quả kiểm chứng (`v2.1.0` · `005378EF…`)

| Hạng mục kiểm chứng | Trạng thái | Bằng chứng đo trực tiếp |
|---|---|---|
| Bộ test JVM & Contract (`testDebugUnitTest`) | **PASS (653/655)** | Chạy sau nâng cấp `v2.1.0` (`task-8978`): `total=655, passed=653, failures=0, errors=0, skipped=2` (2 test live AI skipped offline: `V110CoachLiveEvalTest#coachLiveEval`, `V120LiveAiEvalTest#liveChatAndRouteEval`). |
| Android Lint Release (`lintRelease`) | **PASS (0 errors)** | `0 errors`, `60 warnings` trên build `005378EF…`. |
| Đóng gói & Ký số Release + Debug (`apksigner`) | **PASS** | Release `005378EF…` (`9,058,921` bytes) & Debug `78A3455E…` (`13,452,888` bytes) xác thực chữ ký v2 hợp lệ (`39f5712d…`). |
| Kiểm tra trực quan Thống kê 3 lớp & Wally (`WallyQA`) | **PASS (UI nguồn giống hệt · `9CEA64A0…`)** | Đã cài & chụp trực tiếp trên `WallyQA` (`390×844dp`, Android 9, dữ liệu synthetic đến `2026-11-22`) ngay trước bước nâng `versionCode 18 → 19`: kiểm chứng `Hoạt động | Mục tiêu`, `"Đã ghi nhận: X / 24h"`, `% vs tuần trước`, `0p / 1h` giải thích thiếu Tag, `Wally kể từ dữ liệu` 4 bước (Light + Dark), bóng `WallyPopOnBubble`, `Trợ lý Wally` overlay + offline config guard (`FATAL EXCEPTION = 0`). |
| Nâng cấp tại chỗ `v1.2.0 → v2.0.0` & Focus Pin (`id=910301`) | **PASS (Baseline `50EBDFFF…` / `BEDEE190…`)** | Room schema v7 (`MIGRATION_1_7`) giữ nguyên qua `v2.1.0`; cài đè từ Release `v2.0.0` (`code 18`, cùng khóa `39f5712d…`) sang `v2.1.0` (`code 19`) tương thích trực tiếp. |
| Live AI API với Adaptive Context (Foza thật) | **NOT_TESTED (Chưa cấu hình Live Eval đợt v2.1.0)** | Chủ ý giữ offline; tuyến Adaptive Context được kiểm bằng mock HTTP + guard tests trên JVM. |
| Android 13–16 AVD (`TimeWallet_QA`), Cold-boot & Máy thật Owner | **NOT_TESTED** | Tạm dừng AVD AEHD theo Rule 14; chờ Owner cài APK `005378EF…` kiểm thử thực tế. |

---

## 4. Hướng dẫn cài đặt / nâng cấp cho Owner

1. **Nếu máy đang dùng Release `v2.0.0` (`versionCode 18`, cùng chứng thư `39f5712d…`):** Cài đè trực tiếp [TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk](out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk) (`005378EF…`) — toàn bộ Sổ cái, Mục tiêu, Task, Habit và khóa AI trong Keystore được giữ nguyên. *(Khuyến nghị Xuất JSON dự phòng trước mọi lần nâng cấp.)*
2. **Nếu máy đang cài bản Debug / từ Android Studio:** Xuất JSON sao lưu → gỡ bản Debug → cài sạch APK Release `v2.1.0` → vào **⚙ Hồ sơ & Cài đặt › Dữ liệu & Sao lưu JSON › Nhập / khôi phục JSON (`Khôi phục toàn bộ`)**.
