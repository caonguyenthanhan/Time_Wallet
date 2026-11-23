# Kiến trúc, Quyền riêng tư & Kiểm chứng kỹ thuật — Time Wallet `v2.2.1`

Tài liệu kỹ thuật chuyên sâu cho **Time Wallet (`Wally`)** `v2.2.1` (`versionCode = 24`), tách khỏi `README.md` để trang chính giữ vai trò giới thiệu sản phẩm.

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
│                      # ConfirmationEngine, AiTodoEngine, WallyHubSnapshotEngine,
│                      # ScheduleGridEngine, AdaptiveContextRouter, AppUpdateContract
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

## 4. Kết quả kiểm chứng bản `v2.2.1` (`versionCode 24`)

| Hạng mục | Kết quả | Chi tiết đo trực tiếp |
|---|---|---|
| Unit & Contract Tests (`:app:testDebugUnitTest`) | **PASS (686 / 686 chạy offline)** | `690` test cases (`686 passed, 0 failures, 0 errors, 4 skipped` — 4 test Live AI / live-eval bỏ qua khi không có API key live). |
| Compose Screenshot Validation (`:app:validateDebugScreenshotTest`) | **PASS (15 / 15)** | `15 passed, 0 failures, 0 errors, 0 skipped` (gồm các preview `Thống kê` / `Wally kể từ dữ liệu` / thẻ `Tasks 320dp` Light & Dark). |
| Android Lint (`:app:lintDebug` & `:app:lintRelease`) | **PASS (0 errors)** | `lintDebug`: `0 errors, 117 warnings` · `lintRelease`: `0 errors, 105 warnings`. |
| Release APK & chữ ký (`apksigner`) | **PASS** | `TimeWallet-MVP-v2.2.1-release.apk`: **`9,457,632` bytes** · `SHA-256`: `329E9A75CF02358933590C72A623066AC0DFF215A8F92D579CCDFBB9F61F4AF1` · ký bằng chứng thư `39f5712d…`. |
| Debug APK | **PASS** | `TimeWallet-MVP-v2.2.1-debug.apk`: **`15,728,565` bytes** · `SHA-256`: `C078352B29DD1310976445A1BD09E47E5FD12C953C6E2C96452C9F94936D00D1`. |
| QA giao diện trên `WallyQA` (`index 1`, Android 9) | **PASS (`NO_FATAL_EXCEPTION`)** | Cài đè `v2.2.0 → v2.2.1` giữ nguyên task & `v2.2.1`; xác minh 4 bộ dữ liệu + tuần rỗng 0 bản ghi của **Wally kể từ dữ liệu**, **Phân tích AI**, **Nhịp tập trung** (`22h 30p`, loại phiên `Ngủ`), đồng bộ mốc giờ giữa Dòng thời gian & Lịch ngày (làn song song, đường giờ hiện tại phía sau nền thẻ đục), giữ `taskId` khi kéo sửa lịch & nhãn `Hạn` trọn vẹn ở `320dp`, kéo vùng trống lịch, Bảng nổi `⚠ Quá hạn` `320dp`, Chat nổi tự cuộn + Focus `320dp`. |
| Phạm vi chưa chạy trên phần cứng thật (`NOT_TESTED`) | — | Xem trước ghim Home Widget trực tiếp trên launcher chuẩn Google/Samsung (`isRequestPinAppWidgetSupported == false` trên launcher LDPlayer), bàn phím ảo vẽ đầy đủ bởi IME ngoài (giả lập LDPlayer dùng `ldinputmethod` dù `InputMethodManager` báo `mInputShown=true`), gọi Cloud AI live trên bản `v2.2.1`, AVD Android 16 (tạm dừng theo quy tắc ổn định phần cứng) và thiết bị thật. |

---

## 5. Build từ mã nguồn

```bash
# Chạy toàn bộ unit & contract test
./gradlew :app:testDebugUnitTest --no-daemon

# Kiểm tra lint và build APK debug
./gradlew :app:lintDebug :app:assembleDebug --no-daemon
```

> Keystore phát hành lưu ngoài kho mã (`%USERPROFILE%\.timewallet\keystore.properties` hoặc biến môi trường `TIMEWALLET_KEYSTORE_*`); không commit khóa ký hay API key vào Git.
