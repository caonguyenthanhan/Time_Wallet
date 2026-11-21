# Time Wallet `v2.0.0` (`versionCode 18`) — Private Personal Productivity Hub Candidate

**Ngày lập:** 2026-11-19  
**Mục tiêu:** Đưa **Wally 2.0** từ lộ trình thiết kế Phase 0–4 thành **Private Release Candidate (`versionName = "2.0.0"`, `versionCode = 18`)** ký số cá nhân: hợp nhất 4 trụ cột (`Lịch trình · Việc & Thói quen · Tập trung · Mở rộng`) + **Wally AI Pop-on Overlay**, bảo toàn 100% dữ liệu Room từ `v1.x -> v7` (`MIGRATION_6_7`), hoàn thiện hệ thống Dark Mode, kiểm chứng notification **Ghim tập trung** độc lập, và rà soát ma trận quyền đọc AI + xuất dữ liệu an toàn.

---

## 1. Tệp bàn giao & Định danh bản build

| Thành phần | Giá trị / Đường dẫn |
|---|---|
| APK Production Candidate (Release) | `out/wally_candidate_v200/TimeWallet-MVP-v2.0.0-PRODUCTION-CANDIDATE-release.apk` |
| Dung lượng Release | `9,058,923` bytes |
| SHA-256 Release | `9CEA64A0C9C1954A6C09109B406E825A3EC906AE7AB39E4CC19DB826990F5865` |
| Tệp `.sha256` Release | `out/wally_candidate_v200/TimeWallet-MVP-v2.0.0-PRODUCTION-CANDIDATE-release.apk.sha256` |
| APK Debug (dành cho cài đè nhánh Debug) | `out/wally_candidate_v200/TimeWallet-MVP-v2.0.0-candidate-debug.apk` (`14,656,226` bytes, SHA-256 `45E35EC455415FD7E9F432466265FE1D74AC298C80450640A997EE9EE8627113`) |
| Package / Version / SDK | `com.timewallet.mvp` · `versionName = "2.0.0"` · `versionCode = 18` (`minSdk 26`, `targetSdk 35`) |
| Chứng thư Release | `CN=Time Wallet Personal Release, OU=Mobile, O=TimeWallet, L=Hanoi, ST=HN, C=VN` (`SHA-256: 39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0`) |
| Keystore cá nhân | Lưu **ngoài Git** tại `%USERPROFILE%\.timewallet\` (chặn rò rỉ qua `.gitignore`, `.repomixignore` và `WallySecurityStaticTest`) |
| Dữ liệu QA & Ảnh chụp | `out/wally_candidate_v200/mock_1week_v200_qa.json`, `docs/screenshots/ui-preview/` (36 ảnh + banner tổng hợp) |

---

## 2. Kiến trúc & Những thay đổi chính lên `v2.0.0`

1. **Điều hướng 4 Tab trọng tâm Calendar + Wally AI Overlay (`MainActivity.kt`, `ComponentsAndDialogs.kt`, `ChatScreen.kt`):**
   - Hợp nhất **Ví 24h thu gọn 1 dòng** trực tiếp lên đầu Tab **`Lịch trình`**, loại bỏ tab `Tổng quan` trùng lặp và bỏ nút chat nổi che nội dung; lối mở nhanh `✦ Wally` nằm cố định trên `TimeWalletTopBar` + lối tắt trong `Mở rộng › Công cụ`.
   - Trong header Lịch trình, gộp bộ lọc, chú giải 4 trạng thái và `＋ Ghi mới hoạt động` vào menu ngữ cảnh `⋮` để tối đa hóa không gian Timeline (`Ngày · 3 ngày · Tuần · Tháng`), giữ nút pill `Hôm nay` rõ nét không chồng lấn.
2. **Tasks · Subtasks · Habits · Unified Goals (`TasksHabitsScreen.kt`, `TaskHabitEngine.kt`, `TaskHabitEntities.kt`):**
   - Smart Filters (`Hôm nay · Inbox · Ưu tiên cao · Chưa xong · Đã xong`), thẻ Task rút gọn có nút `▶` Focus 1 chạm, menu `⋯` (`Bắt đầu tập trung · Xếp lịch · Chia nhỏ với Wally AI · Mở bước con · Xóa`), chạm thẻ mở checklist Subtasks tại chỗ.
   - **Habits:** Thanh tóm tắt ngày, tự động đối chiếu hoàn thành (`⚡`) từ Sổ cái cùng Tag đạt ngưỡng phút, nhãn thứ `T2–CN` dưới chuỗi 7 chấm và chip gợi ý khi danh sách thói quen còn ít.
   - **Mục tiêu hợp nhất (`MIN`/`MAX`):** Gộp `CategoryBudget` (hạn mức tuần `MAX`) và `Goal` (`MIN`) vào bảng `goals` (`MIGRATION_6_7`) với quản lý phiên bản `validFrom..validTo`.
   - **Anti-bloat:** `Kanban` (`Cần làm · Đang làm · Đã xong`) và `Ma trận Eisenhower` (`Q1–Q4`) ẩn mặc định, bật/tắt linh hoạt trong `Hồ sơ & Cài đặt`.
3. **Focus Timer & Ghim thông báo tập trung (`TimerScreen.kt`, `FocusPinPolicy.kt`, `CompletionReminder.kt`):**
   - Hỗ trợ `Đếm xuôi`, `Pomodoro 25p`, `Sâu 45p`; khi bấm `▶` từ Task/Kanban tự nạp tiêu đề, nhóm/Tag và checklist Subtasks ngay dưới đồng hồ.
   - **Focus Pin (`id = 910301`, channel `time_wallet_focus_pin`):** Giữ nguyên đúng 3 quyền Android (`INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`); ưu tiên hiển thị phiên đang đo → kế hoạch hiện tại → gợi ý theo lịch sử (`tuần trước / hôm qua / 3 ngày gần đây`) kèm nút `BỎ GHIM`.
4. **Hệ thống Theme Sáng / Tối & Phân tách Cài đặt (`Theme.kt`, `ProfileSettingsScreen.kt`, `InsightsScreen.kt`):**
   - Bổ sung bảng token ngữ nghĩa `WalletPalette` (`WalletCardBg`, `WalletInk`, `WalletMuted`, `WalletMintAccent`, `WalletDangerSolid`, `surfaceContainer*`) và rà soát toàn bộ `AlertDialog` / `ModalBottomSheet` / `DropdownMenu` chống lỗi chữ sáng trên nền trắng ở Dark Mode.
   - Khôi phục tab `Mở rộng` gồm `Thống kê` & `Công cụ`, chuyển toàn bộ `Giao diện & Module`, `Trợ lý AI & Quyền dữ liệu` (`AiReadPermissions`), `Quy tắc phân loại cục bộ (Offline)` và `Dữ liệu & Sao lưu JSON` sang `Hồ sơ & Cài đặt` (`⚙`).
5. **Quyền đọc AI, Sao lưu JSON v2 & Xuất Markdown (`Wally20Coordinator.kt`, `LedgerJsonBackup.kt`, `MarkdownSummaryExporter.kt`):**
   - Thực thi chặn `AiReadPermissions.readTasks` ngay tại `requestWallyReplanInbox` và `breakDownTaskWithWally` cũng như bộ lắp ngữ cảnh `buildFilteredCoachDigest`; giữ nguyên bất biến AI chỉ tạo `Proposal` chờ xác nhận.
   - Hỗ trợ Backup JSON `schemaVersion = 2` (tương thích ngược bản `1.x`, tuyệt đối không xuất khóa API/bí mật) và xuất báo cáo Markdown cho hệ sinh thái ghi chú cá nhân (Obsidian).

---

## 3. Bảng kết quả kiểm chứng (`WALLY 2.0` Acceptance Checklist)

| Hạng mục kiểm chứng | Trạng thái | Bằng chứng đo trực tiếp |
|---|---|---|
| Bộ test JVM & Contract (`testDebugUnitTest`) | **PASS (653/655)** | Chạy sau sửa cuối (`task-8910`): `total=655, passed=653, failures=0, errors=0, skipped=2` (2 test live AI skipped offline: `V110CoachLiveEvalTest#coachLiveEval`, `V120LiveAiEvalTest#liveChatAndRouteEval`). |
| Android Lint Release (`lintRelease`) | **PASS (0 errors)** | `0 errors`, `60 warnings` trên build `9CEA64A0…`. |
| Đóng gói & Ký số Release + Debug (`apksigner`) | **PASS** | Release `9CEA64A0…` (`9,058,923` bytes) & Debug `45E35EC4…` (`14,656,226` bytes) xác thực chữ ký v2 hợp lệ. |
| Nâng cấp tại chỗ `v1.2.0 -> v2.0.0` (Room `v5 -> v7`) | **PASS (Emulator QA · baseline `50EBDFFF…`)** | Cài `v1.2.0` (`7D703385…`, nạp `2h 15p` mẫu ngày `Th 4`) rồi `adb install -r` đè lên `v2.0.0` Release trên `WallyQA`: giữ nguyên `2h 15p`, `FATAL EXCEPTION = 0`, `SQLiteException = 0`. *(Đường migrate `v5 -> v7` & schema không đổi ở bản `9CEA64A0…`.)* |
| Focus Pin Notification (`id=910301`) độc lập | **PASS (Emulator QA · baseline `BEDEE190…`)** | Xác minh riêng bằng `dumpsys notification --noredact` + ảnh khay thông báo ở cả trạng thái chờ (`Chưa có kế hoạch giờ này` + `BỎ GHIM`) lẫn khi bấm giờ (`QA_Focus`), tách biệt khỏi thông báo OS `Configure physical keyboard`. |
| Adaptive Context, Thống kê 3 lớp & Bóng Wally (`9CEA64A0…`) | **PASS (Emulator QA)** | Kiểm chứng trực quan trên `WallyQA` (`390×844dp`, dữ liệu synthetic đến `2026-11-22`): chuyển góc nhìn `Hoạt động | Mục tiêu`, `"Đã ghi nhận: X / 24h"`, Tag/hoạt động chi tiết + `% so với tuần trước`, giải thích `0p` khi thiếu gắn Tag (`Học AI: 0p / 1h`), thẻ **Wally kể từ dữ liệu** 4 bước kèm ghi chú độ tin cậy một phần (Light + Dark), kéo thả bóng chat `WallyPopOnBubble`, mở `Trợ lý Wally` và xác minh gửi câu offline vẫn giữ nguyên nháp & `logcat` `0` `FATAL EXCEPTION`. *(Bố cục Tuần/Tháng/Kanban/Công cụ giữ nguyên từ đợt `BEDEE190…`.)* |
| Live AI API trên `v2.0.0` & Adaptive Context (Foza thật) | **NOT_TESTED (Chưa cấu hình Live Eval đợt v2.0.0)** | Chủ ý giữ offline trong đợt hoàn thiện UX/UI 2.0 để tránh tốn token ngoài phạm vi; tuyến Adaptive Context mới được kiểm bằng mock HTTP + guard tests trên JVM. |
| Android 13–16 AVD (`TimeWallet_QA`) | **NOT_TESTED (Blocked)** | Tạm dừng theo Rule 14 `.agents/AGENTS.md` (lỗi AEHD `Kernel-Power 41` trên máy chủ Windows). |
| Khởi động lại lạnh (`BOOT_COMPLETED`) & Máy thật Owner | **NOT_TESTED (Pending Owner)** | Chờ Owner cài APK `9CEA64A0…` và dùng thực tế ở giai đoạn **Phase 5 — Real-life Validation**. |

---

## 4. Hướng dẫn Owner cài đặt & lưu ý nâng cấp

1. **Nếu máy đang cài bản Debug / từ Android Studio (`402f1d41…`):** Xuất JSON sao lưu trước → gỡ bản Debug → cài sạch [TimeWallet-MVP-v2.0.0-PRODUCTION-CANDIDATE-release.apk](out/wally_candidate_v200/TimeWallet-MVP-v2.0.0-PRODUCTION-CANDIDATE-release.apk) (`9CEA64A0…`) → vào **⚙ Hồ sơ & Cài đặt › Dữ liệu & Sao lưu JSON › Nhập / khôi phục JSON (`Khôi phục toàn bộ`)**. *(Cài đè khác chữ ký sẽ báo `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / LDPlayer `Error code:5`.)*
2. **Nếu máy đang cài sẵn bản Release `v1.2.0` (`39f5712d…`):** Có thể cài đè trực tiếp bản Release `v2.0.0` (vẫn khuyến nghị Xuất JSON dự phòng trước khi nâng cấp).
