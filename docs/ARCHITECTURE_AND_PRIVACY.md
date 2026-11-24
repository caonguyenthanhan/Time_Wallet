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
| Unit & Contract Tests (`:app:testDebugUnitTest`) | **PASS (690 / 690 chạy offline)** | `694` test cases (`690 passed, 0 failures, 0 errors, 4 skipped` — 4 test Live AI / live-eval bỏ qua khi không có API key live). |
| Compose Screenshot Validation (`:app:validateDebugScreenshotTest`) | **PASS (15 / 15)** | `15 passed, 0 failures, 0 errors, 0 skipped` (gồm các preview `Thống kê`, `Lịch`, `Dòng thời gian`, `Tập trung` và thẻ `Tasks 320dp` Light & Dark). |
| Android Lint (`:app:lintDebug` & `:app:lintRelease`) | **PASS (0 errors)** | `lintDebug`: `0 errors, 117 warnings` · `lintRelease`: `0 errors, 105 warnings`. |
| Release APK & chữ ký (`apksigner`) | **PASS** | `TimeWallet-MVP-v2.2.1-release.apk`: **`9,474,023` bytes** · `SHA-256`: `1E9A0508AE21BD80E6B2342FD4A9AC9787579BC4FAD3B1E92B551C4DCD784F36` · ký bằng chứng thư `39f5712d…`. |
| Debug APK | **PASS** | `TimeWallet-MVP-v2.2.1-debug.apk`: **`15,731,025` bytes** · `SHA-256`: `936FE940C21297BC351B8A9F58CD33B2F8893C93CFEB78C3081DCBA766AF2783`. |
| QA giao diện trên `WallyQA` (`index 1`, Android 9) | **PASS (`NO_FATAL_EXCEPTION`)** | Kiểm tra nâng cấp `v2.2.0 → v2.2.1` trên `WallyQA` giữ lại dữ liệu To-do; xác minh **Wally kể từ dữ liệu** (ẩn lời kể khi tuần rỗng `0` bản ghi/`0` mục tiêu), đồng bộ mốc giờ Dòng thời gian & Lịch ngày (`13:03–13:04`, `13:04–13:05` `Đã đo`), kéo dời lịch giữ `taskId` + `Hạn` trọn vẹn ở `320dp`, banner **`⚠ 1 quá hạn`** (tách bạch quá hạn/gần hạn), chặn kéo vùng trống không phủ phiên đã đo + kéo mép trên giữ thanh `+`/`✕` ở `320dp` + tạo bản ghi `Có giờ`, Bảng nổi `⚠ Quá hạn` `320dp`, Chat nổi gọn (`maxHeight = 286dp`) nhập/gửi & hiển thị tin nhắn rõ nét. |
| Phạm vi chưa chạy trên phần cứng thật (`NOT_TESTED`) | — | Nâng cấp qua toàn bộ dải phiên bản `v2.0.0–v2.1.0` và kiểm thử giữ API key sau nâng cấp; xem trước ghim Home Widget trực tiếp trên launcher chuẩn Google/Samsung (`isRequestPinAppWidgetSupported == false` trên launcher LDPlayer); bàn phím ảo vẽ đồ họa đầy đủ ở `320dp` (trên `WallyQA`, `com.android.inputmethod.pinyin/.InputService` tạo cửa sổ `InputMethod` kích thước `Requested w=640 h=0`); gọi Cloud AI live trên bản `v2.2.1`; AVD Android 16 và thiết bị thật. |

---

## 5. Build từ mã nguồn

```bash
# Chạy toàn bộ unit & contract test
./gradlew :app:testDebugUnitTest --no-daemon

# Kiểm tra lint và build APK debug
./gradlew :app:lintDebug :app:assembleDebug --no-daemon
```

> Keystore phát hành lưu ngoài kho mã (`%USERPROFILE%\.timewallet\keystore.properties` hoặc biến môi trường `TIMEWALLET_KEYSTORE_*`); không commit khóa ký hay API key vào Git.
