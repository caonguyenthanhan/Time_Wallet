# Kiến trúc, Quyền riêng tư & Kiểm chứng kỹ thuật — Time Wallet `v2.2.3`

Tài liệu kỹ thuật chuyên sâu cho **Time Wallet (`Wally`)** `v2.2.3` (`versionCode = 26`, ứng viên luồng Tag & hội thoại nhiều lượt), tách khỏi `README.md` để trang chính giữ vai trò giới thiệu sản phẩm.

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
│   │   └── usecase/       # WallyConversationEngine, WallyTagAssignmentEngine, WallyDataStoryEngine, LedgerAnalyticsCalculator, TaskHabitEngine,
│   │                      # ConfirmationEngine, TimelineFeedEngine, AiTodoEngine,
│   │                      # WallyHubSnapshotEngine, ScheduleGridEngine, AdaptiveContextRouter, AppUpdateContract
│   ├── data/
│   │   ├── local/         # Room Database v8 (MIGRATION_4_5..7_8), DAO, Android Keystore prefs,
│   │   │                  # WallyActionController (SSOT cho App + Widget + Floating Assistant)
│   │   ├── ai/            # GeminiProposalClient, WallyTodoAiPlanner, AiProviderPreset
│   │   └── update/        # GitHubReleaseUpdateChannel (HTTPS allowlist, SHA-256, cert check)
│   ├── widget/            # WallyHomeWidgetProvider & WallyHomeWidgetService (2×2 / 4×2 / 4×3+)
│   ├── overlay/           # WallyFloatingService & WallyFloatingChatSession (Foreground specialUse + SYSTEM_ALERT_WINDOW)
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

## 4. Kết quả kiểm chứng ứng viên `v2.2.3` (`versionCode 26`)

| Hạng mục | Kết quả | Chi tiết đo trực tiếp |
|---|---|---|
| Unit & Contract Tests (`:app:testDebugUnitTest`) | **PASS (717 / 717 chạy offline)** | `721` test cases across `80` suites (`717 passed, 0 failures, 0 errors, 4 skipped` — 4 test Live AI bỏ qua khi `FOZA_API_KEY` chưa đặt; gồm `V223FinalClosureTest` `4/4`, `V223TagAndMultiTurnTest` `7/7`, `V108TransactionStatusTest` `16/16`, `WallyChatLifecycleTest` `8/8`, `V200ArchitectureAndFeatureTest` pass dùng phản hồi JSON AI giả lập + `InMemoryLedgerDao`, và hợp đồng Z-order đường chỉ báo thời gian hiện tại trong `V1011UiPolishContractTest`). |
| Compose Screenshot Validation (`:app:validateDebugScreenshotTest`) | **PASS (15 / 15)** | `15 passed, 0 failures, 0 errors, 0 skipped`. |
| Android Lint (`:app:lintDebug` & `:app:lintRelease`) | **PASS (0 errors)** | `lintDebug`: `0 errors, 119 warnings` · `lintRelease`: `0 errors, 107 warnings`. |
| Release APK & chữ ký (`apksigner`) | **PASS** | `out/wally_candidate_v223/TimeWallet-MVP-v2.2.3-release.apk`: **`9,637,853` bytes** · `SHA-256`: `9FA68E9E35429E95E9210DEA71232F4EBFB888A9190F885016B4204AF660312B` · ký bằng chứng thư `39f5712d139124e05d550458180e69a1919ade5a331d2eadbb9de47da79cdcd0`. |
| Debug APK | **PASS** | `out/wally_candidate_v223/TimeWallet-MVP-v2.2.3-debug.apk`: **`15,016,034` bytes** · `SHA-256`: `5E8AEDF3683F81AA29370031ACD1F98FFE678422FF0C86B552E86A1005EAB85F` · chứng thư debug `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d`. |
| QA `WallyQA` trên bản dựng tiền vá `da7ee78` (`87F20208…`, `out/wally_evidence_v223/`) | **PASS phần đã chụp (0 crash, `LOGCAT_FATAL_COUNT=0`)** | Thực hiện trên APK `v2.2.3` trước vòng vá review (`87F20208…`): (1) Đường chỉ báo thời gian hiện tại (`zIndex = 15f`) hiển thị liên tục trên thẻ `On IELTS qua gio` (`11:53–13:23`), chữ rõ ở `Ngày` (`s01`–`s03`), `3 ngày` (`s05`–`s06`) và `Tuần` (`s08`–`s09`); chạm đúng giao điểm mở chọn thẻ + thanh thao tác; kéo mép trên ở `Ngày` tạo preview `12:00–13:23 · 1h 23p` + `✓ Lưu`. (2) `Thống kê` (`Mở rộng -> Thống kê`) đồng bộ live timer `Tag: IELTS`: từ `60p / 5h` (`g02`) lên `61p / 5h` & `1h 31p / 24h` (`01:28`, `g04`) và `1h 32p / 24h` (`c04`), không cộng `PLANNED` `90p`. (3) Thông báo `Wally đang đồng hành` & bóng pop-on hiển thị sạch (`s09`, `c01_shade`). *(Các bản vá review trong APK `9FA68E9E…` không đổi `ScheduleCalendarView` nhưng chưa chạy lại trên emulator vì `Ld9BoxSVC` treo `vbox_pid = -1`.)* |
| Phạm vi chưa chạy trên thiết bị / AI live (`NOT_TESTED` & nghẽn dịch vụ host) | — | Cài & chạy trực tiếp APK sau vá review (`9FA68E9E…`) trên `WallyQA` (dịch vụ host `Ld9BoxSVC` đang treo `vbox_pid = -1`, đã dừng theo Rule 8); gọi model Cloud AI thật (`LiveEval` 4 suite skip); bấm `✓ Lưu` kéo giãn / kéo thân thẻ trên lịch `Ngày`; mở màn `Công việc › Mục tiêu` (`GoalsDialog`) đối chiếu trực tiếp; thao tác UI Chat nổi (`↶ Hoàn tác`) / `✦ Wally` / khôi phục nháp `am kill` trên APK; ghim Home Widget vật lý trên launcher; và ghi chú mở từ `v2.2.2` (`04:51` vs `04:59`). |

---

## 5. Hồ sơ kiểm chứng baseline tiền nhiệm `v2.2.2` (`versionCode 25`, tag `6d59f61`)

| Hạng mục | Kết quả | Chi tiết đo trực tiếp |
|---|---|---|
| Unit & Contract Tests (`:app:testDebugUnitTest`) | **PASS (705 / 705 chạy offline)** | `709` test cases (`705 passed, 0 failures, 0 errors, 4 skipped`). |
| Release APK & chữ ký (`apksigner`) | **PASS** | `TimeWallet-MVP-v2.2.2-release.apk`: **`9,523,164` bytes** · `SHA-256`: `66628ED44634559B070AC9E01B56E51BFB842E182243B80BDF69F19D12BF3732`. |
| QA `320dp` sau `Xác nhận` trên `WallyQA` (APK `66628ED4…`) | **PASS (khép khoảng trống `320dp`)** | Bộ `out/wally_evidence_v222/confirm_planned_320dp/` (`99` tệp PNG/XML/text, `NO_FATAL_EXCEPTION_OR_ANR`): trước xác nhận `0h 15 / 24h` + thẻ `Chờ xác nhận` `On tap thi thu` (`c01`, `c03c`) → hộp thoại `Xác nhận hoàn thành` (`c04`) → sau xác nhận tăng lên `0h 45 / 24h`, chuyển vào `Đã diễn ra` (`02:33–03:03 · Đã ghi 30p`, `c05`, `c05b`), `Thống kê` `45p / 24h` (`c07`), và giữ nguyên sau `am force-stop` (`c08`, `c08b`, `c09` `Thực tế 30p`). *(Ghi chú: ảnh `c06` ở vẫn ở tab Dòng thời gian do thanh `Lịch ngày` đã cuộn lên trên; tổng giờ & persistence đã xác nhận qua `c05`/`c05b`/`c07`/`c08`/`c08b`/`c09`.)* |
| QA 3 bản vá trên `WallyQA` (`APK 66628ED4…`) & JVM | **PASS** | Bộ `out/wally_evidence_v222/fixes_v222_qa/` (`120` tệp, `NO_FATAL_EXCEPTION_OR_ANR`): (1) phiên `23:00–00:30` tách `1h 00` ở `Hôm qua` (`f02`) & `0h 30 / 24h` ở `Hôm nay` (`f01`), `Bảng nổi` `Ghi 30p` (`f03`); khi timer chạy (`f04` `01:07`, `f05` `● 01:10`), `Bảng nổi` và `Ví 24h` cùng báo `31p` (`0h 31 / 24h`); (2) trước khi tích hoàn thành, `f06` ghi cùng lúc `Kế: 04:59 Kiem tra Sap lam` trên `Bảng nổi` và khối `04:59–05:29` trên Lịch bên dưới; sau khi bấm `○`, `f07` xóa mục khỏi `Kế:` trong khi `f07`, `f08` và `f08b` vẫn giữ nguyên đúng khung `04:59–05:29` trên Lịch ngày và Dòng thời gian; (3) xếp lịch `Draft Link Check`, bấm Home + `am kill com.timewallet.mvp`, mở lại `+ Ghi` giữ nguyên `"Thuộc công việc: Draft Link Check"` (`f10`, `03:45–04:15`) và sau khi lưu hiện lại `"Thuộc công việc: Draft Link Check · Hạn 11-25"` (`f12`). Nhánh Widget provider, nháp đề xuất chat (`SavedProposalDraft.taskId`) và tương thích ngược JSON nháp cũ được kiểm chứng qua JVM unit tests (`V220FloatingWidgetAndAiTodoTest`, `V120DraftPersistenceTest`). |
| QA baseline tiền nhiệm trên `WallyQA` (`36A46F2E…` & `751B65AE…`) | **Lưu hồ sơ đối chiếu** | `out/wally_evidence_v222/timeline_phase_review/` (`228` tệp, Ca 1–3 đạt đầy đủ trên `36A46F2E…`) và `out/wally_evidence_v221/crud_delete_overlay_320dp_run1/` (`164` tệp trên `751B65AE…`). Các điểm hiển thị từng ghi nhận ở `320dp` (`bóng nổi`, `"Dịch khung gi…"`, `"Wa…"`) giữ nguyên phạm vi theo chốt Owner. |
| Phạm vi chưa chạy trên phần cứng thật (`NOT_TESTED`) | — | Ghim Home Widget vật lý trên launcher Google/Samsung (`isRequestPinAppWidgetSupported == false` trên launcher LDPlayer); bàn phím ảo vẽ đồ họa đầy đủ ở `320dp`; gọi Cloud AI live trên bản `v2.2.2`; AVD Android 16 (tắt theo Rule 14); thiết bị thật; và luồng khôi phục `SavedProposalDraft.taskId` trên thiết bị khi có Cloud AI live (đã kiểm chứng bằng JVM unit test). |

---

## 6. Build từ mã nguồn

```bash
# Chạy toàn bộ unit & contract test
./gradlew :app:testDebugUnitTest --no-daemon

# Kiểm tra lint và build APK debug
./gradlew :app:lintDebug :app:assembleDebug --no-daemon
```

> Keystore phát hành lưu ngoài kho mã (`%USERPROFILE%\.timewallet\keystore.properties` hoặc biến môi trường `TIMEWALLET_KEYSTORE_*`); không commit khóa ký hay API key vào Git.
