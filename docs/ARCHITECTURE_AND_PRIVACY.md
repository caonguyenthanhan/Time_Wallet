# Kiến trúc, Quyền riêng tư & Kiểm chứng kỹ thuật — Time Wallet `v2.2.2`

Tài liệu kỹ thuật chuyên sâu cho **Time Wallet (`Wally`)** `v2.2.2` (`versionCode = 25`), tách khỏi `README.md` để trang chính giữ vai trò giới thiệu sản phẩm.

---

## 1. Bất biến dữ liệu thời gian (4 lớp tách biệt)

Time Wallet lưu trữ dữ liệu theo nguyên tắc trung thực, không tự bịa mốc giờ hay làm sai lệch quá khứ:

| Lớp dữ liệu | Ý nghĩa | Nguồn tạo | Ảnh hưởng lên biểu đồ 24h & thống kê |
|---|---|---|---|
| `MEASURED` | Thời gian đo thực tế bằng bấm giờ / Focus | `Focus`, `WallyFloatingService`, `WallyHomeWidgetProvider` | Cộng vào cột 24h theo đúng khung giờ thực tế |
| `REPORTED_TIMED` | Tự khai báo kèm khung giờ bắt đầu – kết thúc | Ghi thủ công, kéo thả trên lịch, AI sau khi bạn duyệt | Cộng vào cột 24h, kiểm tra không chồng lấn (`overlap`) |
| `REPORTED_UNPLACED` | Tự ghi chỉ có thời lượng (`"Đọc sách 30p"`) | Ghi nhanh không nêu giờ bắt đầu | Không đưa vào mốc giờ trên trục 24h; tách riêng thẻ tổng kết |
| `PLANNED` | Dự định / To-do được đưa lên lịch tương lai | Công việc To-do, lịch dự kiến | Tách biệt hoàn toàn khỏi thời gian đã thực hiện |

---

## 2. Kiến trúc mã nguồn (`app/src/main/java/com/timewallet/mvp/`)

```text
app/src/main/java/com/timewallet/mvp/
├── domain/
│   ├── model/         # LedgerRecord, TaskItem, Habit, Goal, Proposal, UpdateContract
│   └── usecase/       # WallyDataStoryEngine, LedgerAnalyticsCalculator, TaskHabitEngine,
│                      # ConfirmationEngine, TimelineFeedEngine, AiTodoEngine,
│                      # WallyHubSnapshotEngine, ScheduleGridEngine, AdaptiveContextRouter, AppUpdateContract
├── data/
│   ├── local/         # Room Database v8 (MIGRATION_4_5..7_8), DAO, Android Keystore prefs,
│   │                  # WallyActionController (SSOT cho App + Widget + Floating Assistant)
│   ├── ai/            # GeminiProposalClient, WallyTodoAiPlanner, AiProviderPreset
│   └── update/        # GitHubReleaseUpdateChannel (HTTPS allowlist, SHA-256, cert check)
├── widget/            # WallyHomeWidgetProvider & WallyHomeWidgetService (2×2 / 4×2 / 4×3+)
├── overlay/           # WallyFloatingService (Foreground specialUse + SYSTEM_ALERT_WINDOW)
├── notifications/     # Focus pinned notification (id=910301) & CompletionBootReceiver
└── ui/                # Jetpack Compose Material 3 (Lịch trình, Công việc, Tập trung, Mở rộng, Chat)
```

### Đồng bộ trạng thái chung (Single Source of Truth)
- `WallyActionController` là điểm ghi/điều khiển thống nhất giữa **App chính**, **Home Widget** và **Trợ lý nổi**: mọi thao tác bắt đầu, tạm dừng, tiếp tục, lưu phiên hay hoàn thành To-do đi qua cùng DAO Room (`v8`) và phát sự kiện cập nhật đồng bộ.
- **Tạm dừng & Nối phiên (`Pause / Resume`)**: khi người dùng bấm tạm dừng, đoạn thời gian vừa chạy được chốt sạch qua `TimerCommit` thành bản ghi `MEASURED` thực tế (không cộng gộp hay suy đoán khoảng nghỉ); bấm `Tiếp tục` mở một đoạn đo mới gắn lại tiêu đề/nhóm/công việc trước đó.

---

## 3. Quyền riêng tư, Bảo mật & Hỗ trợ đa nhà cung cấp AI

1. **Khóa API do bạn kiểm soát:** ứng dụng không nhúng API key sẵn; khóa người dùng nhập được mã hóa bằng **Android Keystore AES-256-GCM** qua `EncryptedSharedPreferences`.
2. **Hỗ trợ đa cấu hình AI:** người dùng chọn preset hoặc tự điền Base URL tương thích OpenAI / Gemini / Anthropic / DeepSeek / Groq / Ollama cục bộ và danh sách model (`/models`) trong **Hồ sơ & Cài đặt › Cấu hình Wally AI**.
3. **Phân quyền đọc từng mảng (`AiReadPermissions`):** có thể bật/tắt độc lập quyền AI đọc *Sổ cái hôm nay*, *Công việc & Inbox*, *Thói quen* và *Mục tiêu*. Khi một quyền tắt, ngữ cảnh tương ứng bị chặn hoàn toàn ở tầng cục bộ trước khi tạo prompt (`WallyTodoAiPlanner`, `WallyContextEngine`).
4. **Không tự ghi dữ liệu & không đọc màn hình:**
   - Wally luôn trả về `Proposal` kèm `needsConfirmation = true`; dữ liệu chỉ ghi khi người dùng xác nhận.
   - Trợ lý nổi chỉ dùng `SYSTEM_ALERT_WINDOW` và `FOREGROUND_SERVICE_SPECIAL_USE`; **không** khai báo `AccessibilityService`, **không** xin `PACKAGE_USAGE_STATS`, không đọc nội dung ứng dụng khác.
5. **Tự động cập nhật an toàn 3 lớp (`GitHubReleaseUpdateChannel`):**
   - Chỉ đọc metadata từ `https://api.github.com/repos/caonguyenthanhan/Time_Wallet/releases/latest` và chặn chuyển hướng ngoài miền GitHub.
   - Tải `.apk` + `.sha256`, đối chiếu checksum `SHA-256` trước khi mở trình cài đặt Android.
   - Xác minh APK trùng `applicationId` (`com.timewallet.mvp`), `versionCode` mới hơn và chữ ký phát hành `39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0`.

---

## 4. Kết quả kiểm chứng bản `v2.2.2` (`versionCode 25`)

| Hạng mục | Kết quả | Chi tiết đo trực tiếp |
|---|---|---|
| Unit & Contract Tests (`:app:testDebugUnitTest`) | **PASS (700 / 700 chạy offline)** | `704` test cases (`700 passed, 0 failures, 0 errors, 4 skipped` — 4 test Live AI / live-eval bỏ qua khi không có API key live; gồm test mới trong `TimelineFeedEngineTest` cho `LinkedTaskDisplay` / dịch khung giờ / đổi thời lượng & sao chép xóa phân biệt việc vs lịch dự kiến). |
| Compose Screenshot Validation (`:app:validateDebugScreenshotTest`) | **PASS (15 / 15)** | `15 passed, 0 failures, 0 errors, 0 skipped` (gồm các preview `Thống kê`, `Lịch`, `Dòng thời gian` 320dp/390dp, `Tập trung` và thẻ `Tasks 320dp` Light & Dark). |
| Android Lint (`:app:lintDebug` & `:app:lintRelease`) | **PASS (0 errors)** | `lintDebug`: `0 errors, 117 warnings` · `lintRelease`: `0 errors, 105 warnings`. |
| Release APK & chữ ký (`apksigner`) | **PASS** | `TimeWallet-MVP-v2.2.2-release.apk`: **`9,523,168` bytes** · `SHA-256`: `36A46F2E72019C1EA0D855FA7E031A1302A86B8B6A5309C9F5D797D87CED5297` · ký bằng chứng thư `39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0`. |
| Debug APK | **PASS** | `TimeWallet-MVP-v2.2.2-debug.apk`: **`14,261,295` bytes** · `SHA-256`: `8DF370C9A480BD2C9AAA2D0BE4353950F0A1B021200DF881D40E1418774E2877`. |
| QA tiền nhiệm ở `320dp` (`WallyQA`, APK `751B65AE…`) | **PASS một phần (trước khi vá `LinkedTaskDisplay`)** | Xác minh trên `WallyQA` (`320dp`, `out/wally_evidence_v221/crud_delete_overlay_320dp_run1/`): tạo/mở lại/xóa lịch dự kiến giữ nguyên `2p` thực tế, Hủy xóa giữ nguyên dữ liệu, hộp thoại xóa To-do nêu tên việc & `2` bước con, 1 lần gửi từ Bảng nổi tạo đúng 1 To-do, quay về từ Chat nổi chạm được ngay tab `Công việc`, thứ tự Dòng thời gian `Sắp diễn ra` / `Bây giờ` / `Hôm nay` (mới trước). |
| QA giao diện trên `WallyQA` (`index 1`, Android 9, APK `36A46F2E…`) | **PASS (`NO_FATAL_EXCEPTION_OR_ANR`, 228 tệp PNG/XML)** | Xác minh đầy đủ 4 ca trên `36A46F2E…` (`out/wally_evidence_v222/timeline_phase_review/`): (1) mở lại lịch xếp từ To-do hiện `"Thuộc công việc: On bai kiem tra"` + `"Hạn 11-26 17:00"` + `Dự kiến`; (2) dịch `+15p` (`09:15–09:45`, `30p`) vs đổi thời lượng `+15p` (`09:00–09:45`, `45p`) khớp Lịch ngày, Dòng thời gian & thẻ To-do sau `am force-stop`; (3) thứ tự `Sắp diễn ra` / `● Bây giờ` (`Phan tich log QA`) / `Đã diễn ra` (`Mới nhất trước`) qua `Hôm nay` & `Hôm qua`, phiên qua đêm `23:00–00:30` tách `23:00–24:00 · 1h (+1 ngày)` ở `Hôm qua` và `Trong ngày 36p · +30p từ hoạt động qua đêm = Đã ghi 1h 06` ở `Hôm nay`; (4) ở `320dp` (`640×1136`) có bóng Wally nổi (`Trợ lý nổi` bật), bấm được `✓ Xác nhận`, `Điều chỉnh`, 4 bộ lọc 1 dòng, menu `⋮` thẻ cuối và `Hủy`/`Xóa lịch dự kiến` giữ nguyên giờ thực tế (`390dp` đầu trang đọc trọn `2` thẻ `148dp`; `320dp` sau cuộn qua Ví 24h đọc trọn `1` thẻ `150dp`). Giữ song song bằng chứng `320dp` tiền nhiệm `751B65AE…` (`out/wally_evidence_v221/crud_delete_overlay_320dp_run1/`) cho xóa To-do 2 bước con, 1 lần gửi Bảng nổi và quay về từ Chat nổi. |
| Phạm vi chưa chạy trên phần cứng thật (`NOT_TESTED`) | — | Ghim Home Widget trực tiếp trên launcher chuẩn Google/Samsung (`isRequestPinAppWidgetSupported == false` trên launcher LDPlayer); bàn phím ảo vẽ đồ họa đầy đủ ở `320dp` (trên `WallyQA`, `com.android.inputmethod.pinyin/.InputService` tạo cửa sổ `InputMethod` kích thước `Requested w=640 h=0`); gọi Cloud AI live trên bản `v2.2.2`; AVD Android 16 (tắt theo Rule 14) và thiết bị thật; cũng không có ảnh trước-sửa cùng dữ liệu để đối chiếu nhận xét *"gọn hơn bản cũ"*. |

---

## 5. Build từ mã nguồn

```bash
# Chạy toàn bộ unit & contract test
./gradlew :app:testDebugUnitTest --no-daemon

# Kiểm tra lint và build APK debug
./gradlew :app:lintDebug :app:assembleDebug --no-daemon
```

> Keystore phát hành lưu ngoài kho mã (`%USERPROFILE%\.timewallet\keystore.properties` hoặc biến môi trường `TIMEWALLET_KEYSTORE_*`); không commit khóa ký hay API key vào Git.
