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
| Unit & Contract Tests (`:app:testDebugUnitTest`) | **PASS (705 / 705 chạy offline)** | `709` test cases (`705 passed, 0 failures, 0 errors, 4 skipped` — 4 test Live AI / live-eval bỏ qua khi không có API key live; gồm 3 test mới trong `V220FloatingWidgetAndAiTodoTest` và 2 test mới trong `V120DraftPersistenceTest` cho đồng bộ tổng phút qua đêm/timer, lọc việc đã xong khỏi `Sắp làm`, và giữ `taskId` nháp + tương thích ngược JSON cũ). |
| Compose Screenshot Validation (`:app:validateDebugScreenshotTest`) | **PASS (15 / 15)** | `15 passed, 0 failures, 0 errors, 0 skipped` (gồm các preview `Thống kê`, `Lịch`, `Dòng thời gian` 320dp/390dp, `Tập trung` và thẻ `Tasks 320dp` Light & Dark). |
| Android Lint (`:app:lintDebug` & `:app:lintRelease`) | **PASS (0 errors)** | `lintDebug`: `0 errors, 117 warnings` · `lintRelease`: `0 errors, 105 warnings`. |
| Release APK & chữ ký (`apksigner`) | **PASS** | `TimeWallet-MVP-v2.2.2-release.apk`: **`9,523,164` bytes** · `SHA-256`: `66628ED44634559B070AC9E01B56E51BFB842E182243B80BDF69F19D12BF3732` · ký bằng chứng thư `39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0`. |
| Debug APK | **PASS** | `TimeWallet-MVP-v2.2.2-debug.apk`: **`15,958,845` bytes** · `SHA-256`: `FD3E21D016187A02065EBE254028985CCCD62E2DD612FA4DF2885618F3588EAE`. |
| QA `320dp` sau `Xác nhận` trên `WallyQA` (APK `66628ED4…`) | **PASS (khép khoảng trống `320dp`)** | Bộ `out/wally_evidence_v222/confirm_planned_320dp/` (`99` tệp PNG/XML/text, `NO_FATAL_EXCEPTION_OR_ANR`): trước xác nhận `0h 15 / 24h` + thẻ `Chờ xác nhận` `On tap thi thu` (`c01`, `c03c`) → hộp thoại `Xác nhận hoàn thành` (`c04`) → sau xác nhận tăng lên `0h 45 / 24h`, chuyển vào `Đã diễn ra` (`02:33–03:03 · Đã ghi 30p`, `c05`, `c05b`), `Thống kê` `45p / 24h` (`c07`), và giữ nguyên sau `am force-stop` (`c08`, `c08b`, `c09` `Thực tế 30p`). *(Ghi chú: ảnh `c06` ở vẫn ở tab Dòng thời gian do thanh `Lịch ngày` đã cuộn lên trên; tổng giờ & persistence đã xác nhận qua `c05`/`c05b`/`c07`/`c08`/`c08b`/`c09`.)* |
| QA 3 bản vá trên `WallyQA` (`APK 66628ED4…`) & JVM | **PASS** | Bộ `out/wally_evidence_v222/fixes_v222_qa/` (`120` tệp, `NO_FATAL_EXCEPTION_OR_ANR`): (1) phiên `23:00–00:30` tách `1h 00` ở `Hôm qua` (`f02`) & `0h 30 / 24h` ở `Hôm nay` (`f01`), `Bảng nổi` `Ghi 30p` (`f03`); khi timer chạy `01:07–01:12`, `Bảng nổi` (`f04`) và `Ví 24h` (`f05`) cùng báo `31p` (`0h 31 / 24h`); (2) bấm `○` xong `Kiem tra Sap lam` ẩn mục khỏi `Kế:` trên `Bảng nổi` (`f06`→`f07`) nhưng giữ nguyên khối `04:59–05:29` trên Lịch (`f07`, `f08`); (3) xếp lịch `Draft Link Check`, bấm Home + `am kill com.timewallet.mvp`, mở lại `+ Ghi` giữ nguyên `"Thuộc công việc: Draft Link Check"` (`f10`) và sau khi lưu hiện lại `"Thuộc công việc: Draft Link Check · Hạn 11-25"` (`f12`). Nhánh Widget provider, nháp đề xuất chat (`SavedProposalDraft.taskId`) và tương thích ngược JSON nháp cũ được kiểm chứng qua JVM unit tests (`V220FloatingWidgetAndAiTodoTest`, `V120DraftPersistenceTest`). |
| QA baseline tiền nhiệm trên `WallyQA` (`36A46F2E…` & `751B65AE…`) | **Lưu hồ sơ đối chiếu** | `out/wally_evidence_v222/timeline_phase_review/` (`228` tệp, Ca 1–3 đạt đầy đủ trên `36A46F2E…`) và `out/wally_evidence_v221/crud_delete_overlay_320dp_run1/` (`164` tệp trên `751B65AE…`). Các điểm hiển thị từng ghi nhận ở `320dp` (`bóng nổi`, `"Dịch khung gi…"`, `"Wa…"`) giữ nguyên phạm vi theo chốt Owner. |
| Phạm vi chưa chạy trên phần cứng thật (`NOT_TESTED`) | — | Ghim Home Widget vật lý trên launcher Google/Samsung (`isRequestPinAppWidgetSupported == false` trên launcher LDPlayer); bàn phím ảo vẽ đồ họa đầy đủ ở `320dp`; gọi Cloud AI live trên bản `v2.2.2`; AVD Android 16 (tắt theo Rule 14); thiết bị thật; và luồng khôi phục `SavedProposalDraft.taskId` trên thiết bị khi có Cloud AI live (đã kiểm chứng bằng JVM unit test). |

---

## 5. Build từ mã nguồn

```bash
# Chạy toàn bộ unit & contract test
./gradlew :app:testDebugUnitTest --no-daemon

# Kiểm tra lint và build APK debug
./gradlew :app:lintDebug :app:assembleDebug --no-daemon
```

> Keystore phát hành lưu ngoài kho mã (`%USERPROFILE%\.timewallet\keystore.properties` hoặc biến môi trường `TIMEWALLET_KEYSTORE_*`); không commit khóa ký hay API key vào Git.
