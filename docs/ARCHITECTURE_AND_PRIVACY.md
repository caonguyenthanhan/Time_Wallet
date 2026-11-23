# Kiến trúc, Quyền riêng tư & Kiểm chứng kỹ thuật — Time Wallet `v2.2.0`

Tài liệu kỹ thuật chuyên sâu cho **Time Wallet (`Wally`)** `v2.2.0` (`versionCode = 23`), tách khỏi `README.md` để trang chính giữ vai trò giới thiệu sản phẩm.

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
│   └── usecase/       # AiTodoEngine, WallyHubSnapshotEngine, ScheduleGridEngine,
│                      # LedgerAnalyticsCalculator, AdaptiveContextRouter, AppUpdateContract
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

## 4. Kết quả kiểm chứng bản `v2.2.0` (`versionCode 23`)

| Hạng mục | Kết quả | Chi tiết đo trực tiếp |
|---|---|---|
| Unit & Contract Tests (`:app:testDebugUnitTest`) | **PASS (676 / 676 chạy offline)** | `76` test suites, `680` test cases (`676 passed, 0 failures, 0 errors, 4 skipped` — 4 suite Live Eval bỏ qua khi không có API key live). |
| Android Lint (`:app:lintDebug` & `:app:lintRelease`) | **PASS (0 errors)** | `lintDebug`: `0 errors, 109 warnings` · `lintRelease`: `0 errors, 99 warnings`. |
| Release APK & chữ ký (`apksigner`) | **PASS** | `TimeWallet-MVP-v2.2.0-release.apk`: **`9,375,715` bytes** · `SHA-256`: `CB243B229D85F9BAE39E65D9E06E159722489BF41CACF0A79BEF2D603FC59E6A` · ký v2 bằng chứng thư `39f5712d…`. |
| Debug APK | **PASS** | `TimeWallet-MVP-v2.2.0-debug.apk`: **`14,496,991` bytes** · `SHA-256`: `9477BDC879D8253B0EC996AD7F136459569868D5920FE1F1B24C567C6EBE1209`. |
| QA giao diện trên `WallyQA` (`index 1`, Android 9) | **PASS (`NO_FATAL_EXCEPTION`)** | Kiểm chứng AI To-do, Focus Pause/Resume, Trợ lý nổi (bong bóng + bảng nhanh + chat nhanh + `Tắt` đồng bộ), Lịch trình & Thống kê 24h đồng bộ, và `dumpsys appwidget` xác nhận đăng ký `WallyHomeWidgetProvider`. |
| Phạm vi chưa chạy trên phần cứng thật (`NOT_TESTED`) | — | Xem trước ghim Home Widget trực tiếp trên launcher chuẩn Google/Samsung (giả lập LDPlayer lược bỏ `AppWidgetHost` pin drawer), gọi Cloud AI live cho tuyến To-do mới trên bản `v2.2.0`, AVD Android 16 (tạm dừng theo quy tắc ổn định phần cứng) và máy thật. |

---

## 5. Build từ mã nguồn

```bash
# Chạy toàn bộ unit & contract test
./gradlew :app:testDebugUnitTest --no-daemon

# Kiểm tra lint và build APK debug
./gradlew :app:lintDebug :app:assembleDebug --no-daemon
```

> Keystore phát hành lưu ngoài kho mã (`%USERPROFILE%\.timewallet\keystore.properties` hoặc biến môi trường `TIMEWALLET_KEYSTORE_*`); không commit khóa ký hay API key vào Git.
