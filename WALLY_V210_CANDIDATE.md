# Time Wallet `v2.1.0` (`versionCode 19`) — Adaptive Wally (`Pro / Flash`), 3-Layer Insights, GitHub Auto-Update & UI Polish Candidate

**Ngày lập / Chốt phiên bản:** 2026-11-22  
**Mục tiêu:** Nâng chính thức từ `v2.0.0` (`versionCode 18`) lên **`v2.1.0` (`versionCode 19`)** với các năng lực cốt lõi đã triển khai:
1. **Wally Intelligence Chặng 1 (Phương án A · Adaptive Context) & State Slicing:** tách điều phối ngữ cảnh ra khỏi `TimeWalletViewModel` (`WallyIntelligenceCoordinator`, `AdaptiveContextRouter`, `WallyContextEngine`), gắn ngữ cảnh có kiểm soát quyền đọc (`AiReadPermissions`) chỉ cho câu hỏi tư vấn kế hoạch & hội thoại nối tiếp có TTL 30 phút, và tách nhịp tick 1 giây ra `timerTickState`.
2. **Hai chế độ AI rõ ràng: `Wally Pro` (Mặc định) & `Wally Flash` (Tùy chọn):** ánh xạ trực tiếp hai model Claude hiện hành (`Wally Pro` → **`Claude Sonnet 4.6`** `cinb/claude-sonnet-4-6`; `Wally Flash` → **`Claude Haiku 4.5`** `hoang/claude-haiku-4.5`), luôn hiển thị tên model Claude thực tế bên dưới nút chọn trong Chat/Overlay/Cài đặt, tránh nhầm lẫn với Gemini Flash; giữ chế độ `Auto` tạm khóa do benchmark đối đầu (`24×2 = 48` lượt thật) cho thấy Hybrid chưa đạt ngưỡng tin cậy.
3. **Thống kê 3 lớp (`Mở rộng › Thống kê`) & Wally kể từ dữ liệu:** chuyển mặc định sang góc nhìn **Hoạt động thực tế** (`Ngủ, nghỉ · Công việc · Học tập · Sức khỏe · Giải trí & Khác` vs `Chưa ghi nhận`), giữ nút chuyển nhanh `Hoạt động | Mục tiêu`, đổi nhãn sang **`Đã ghi nhận: X / 24h`**, thêm phân tích Tag/hoạt động chi tiết + **`% so với tuần trước`**, giải thích tự động mục tiêu `0p` khi thiếu gắn Tag, và hiển thị câu chuyện 4 bước **`Wally kể từ dữ liệu`** kèm ghi chú thận trọng khi dữ liệu chưa đầy đủ.
4. **Tự động cập nhật qua GitHub Releases (`Phương án A` tách kênh & 3 lớp xác thực):** kiểm tra phát hành nền không chặn giao diện, hiển thị phiên bản & changelog, tải APK trực tiếp trong app, xác thực 3 lớp (HTTPS origin chính thức + đối chiếu `SHA-256` + khớp chứng thư ký số APK `39f5712d…`) trước khi mở trình cài đặt hệ thống và giữ nguyên 100% dữ liệu Room/Keystore khi cài đè.
5. **Thống nhất luồng tương tác Lịch trình & tùy chọn cỡ chữ (`Nhỏ / Vừa / To`):** hợp nhất mô hình tương tác trên `Ngày / 3 ngày / Tuần / Tháng` với khay `Chưa gắn giờ` ghim cố định ở đầu lưới (mở sheet `Đặt vào lịch` tách biệt), chạm/kéo chọn khoảng trống điền sẵn giờ vào biểu mẫu, chạm/kéo thẻ hoạt động với bản nháp **Lưu thay đổi / Hủy** (dời giữ nguyên thời lượng, kéo mép trên/dưới, cảnh báo chồng lấn & bảo toàn tách biệt `PLANNED` vs Thực tế, xử lý chuẩn `21:00–24:00`), bổ sung `FontScalePreference` (`0.86x / 0.93x / 1.00x`), thu gọn Pop-on (`48.dp`, đệm đáy `76.dp`) và tinh chỉnh 5 màn hình.

---

## 1. Tệp bàn giao & Định danh bản build (`v2.1.0`)

| Thành phần | Giá trị / Đường dẫn |
|---|---|
| APK Production Candidate (Release) | `out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk` |
| Dung lượng Release | `9,174,162` bytes |
| SHA-256 Release | `8C6EB5C79678627D2EEF6D93F5A2FC0E90C4FA2708C914A2CDFF9C1DDA8E20CB` |
| Tệp `.sha256` Release | `out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk.sha256` |
| APK Debug (dành cho nhánh Debug) | `out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-candidate-debug.apk` (`15,297,401` bytes, SHA-256 `1738AC69A208ACA7F104CEAB27AD47E4B8AA52B914BE83AFA5DEC2A9E4DECBAA`) |
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
2. **Chế độ `Wally Pro` / `Wally Flash` (`TimerAndSettingsStore.kt`, `ChatScreen.kt`, `ComponentsAndDialogs.kt`, `ProfileSettingsScreen.kt`, `InsightsScreen.kt`, `TimeWalletViewModel.kt`):**
   - Định nghĩa `WallyModelPreset` với hai chế độ trực tiếp trên 2 model Claude: **`PRO`** (`cinb/claude-sonnet-4-6` — *Claude Sonnet 4.6*, mặc định) và **`FLASH`** (`hoang/claude-haiku-4.5` — *Claude Haiku 4.5*, tùy chọn ưu tiên tốc độ).
   - Dùng chung component `WallyModelModeSelector` tại Chat, Wally Overlay, Hồ sơ & Cài đặt và Hộp thoại Cấu hình AI, luôn hiển thị rõ tên model thực tế bên dưới nút để không nhầm Flash với Gemini Flash; `TimerAndSettingsStore.saveAiModelName` chỉ cập nhật `KEY_AI_MODEL` mà không chạm vào ciphertext khóa API.
   - Bỏ chip gợi ý legacy `cinb/gemini-3.8-flash` trong `AiSettingsDialog`; khóa nút/luồng `Auto` cho tới khi bộ định tuyến đạt chuẩn chất lượng.
3. **Thống kê 3 lớp & Wally kể từ dữ liệu (`LedgerAnalyticsCalculator.kt`, `InsightsScreen.kt`):**
   - **Lớp 1 — Thời gian thực tế:** Góc nhìn mặc định **`Hoạt động`** nhóm 24h × 7 ngày theo `ActivityBucket` (`Ngủ, nghỉ`, `Công việc`, `Học tập`, `Sức khỏe & Sinh hoạt`, `Giải trí & Khác`) kèm bảo toàn tổng giây đúng bằng `recordedTimedSeconds` trên từng cột 24h, tách bạch `Chưa ghi nhận` và `Chưa gắn giờ`, giữ nút chuyển nhanh `Hoạt động | Mục tiêu`, đổi nhãn thành `"Đã ghi nhận: X / 24h"`, hiện danh sách Tag/hoạt động cụ thể và `% so với tuần trước`.
   - **Lớp 2 — Mức độ phù hợp mục tiêu:** Hiện tiến độ `MIN`/`MAX` (`0p / 1h` rõ ràng khi `0s`), chẩn đoán khi mục tiêu `TAG` đang `0p` nhưng cùng danh mục có bản ghi chưa gắn Tag (`hintVi`), đồng thời khẳng định vai trò phục hồi của `Ngủ, nghỉ / Sức khỏe / Phi mục tiêu`.
   - **Lớp 3 — `Wally kể từ dữ liệu`:** Tổng hợp câu chuyện 4 bước (`Điều gì xảy ra` → `Đáng chú ý` → `Ảnh hưởng mục tiêu` → `Có thể làm tiếp`) dựa trên dữ liệu thực & khoảng trống từ `ScheduleGridEngine`, kèm ghi chú thận trọng (`uncertaintyNote`) khi thiếu ngày hoặc ngày hiện tại chưa kết thúc.
4. **Tự động cập nhật ứng dụng qua GitHub Releases (`AppUpdateContract.kt`, `GitHubAppUpdater.kt`, `AppUpdateCoordinator.kt`, `AppUpdateDialog.kt`, `AndroidManifest.xml`, `file_provider_paths.xml`):**
   - Thiết kế theo **Phương án A** với interface `AppUpdateChannel` tách kênh phân phối (`GitHubReleaseUpdateChannel` cho bản cài trực tiếp từ GitHub Releases hiện nay; `PlayStoreUpdateChannel` sẵn sàng cho Google Play In-App Updates khi phát hành lên Store sau này).
   - Kiểm tra metadata phát hành từ GitHub REST API (chặn redirect ra origin lạ), đối chiếu `versionCode`, tải APK và `.sha256` về cache nội bộ, kiểm tra 3 lớp (origin tin cậy + `SHA-256` tệp tải về + chữ ký APK khớp chứng thư đang chạy `39f5712d…`) và chỉ mở Android PackageInstaller (`FileProvider`) khi có thao tác đồng ý của người dùng. Mọi lỗi mạng/timeout/403/404 đều rơi về trạng thái an toàn để app hoạt động bình thường.
5. **Thống nhất luồng tương tác Lịch trình, cỡ chữ & UI Polish (`ScheduleCalendarView.kt`, `ScheduleGridEngine.kt`, `TimeRange.kt`, `LedgerValidator.kt`, `ConfirmationEngine.kt`, `ComponentsAndDialogs.kt`, `TimeWalletViewModel.kt`, `HistoryScreen.kt`, `MainActivity.kt`, `TaskAndHabitModels.kt`, `Theme.kt`, `ProfileSettingsScreen.kt`, `InsightsScreen.kt`, `ChatScreen.kt`):**
   - Thống nhất state tương tác (`ScheduleInteractionState`: `Idle`, `EmptySlotsSelected`, `BlockSelected`, `BlockDraftTransform`, `UntimedPlacement`) với khay `PinnedScheduleUtilityStrip` (`Chưa gắn giờ (N)` + `+ Ghi mới`) ghim cố định ở đầu lưới; mọi thao tác chạm/kéo chọn ô trống điền sẵn giờ vào biểu mẫu, mọi thao tác dời/kéo cạnh trên-dưới hoặc `Đặt vào lịch` đều qua bản nháp **Lưu thay đổi / Hủy** kèm kiểm tra trùng giờ trực tiếp (`findConflictingBlocks`) và chặn tự chuyển bản ghi Thực tế sang `PLANNED`; chuẩn hóa mốc cuối ngày `24:00` (`parseEndMinuteOfDay`, `1440` phút) trên cùng ngày gốc không tràn khối ma sang ngày kế tiếp.
   - Bổ sung `FontScalePreference` (`SMALL 0.86f`, `MEDIUM 0.93f`, `LARGE 1.00f` mặc định) áp dụng qua `LocalDensity`, thu gọn khoảng đệm Lịch trình & Thống kê, thu gọn `WallyPopOnBubble` (`48.dp`) + chừa đáy cuộn `76.dp`, và tối ưu composer trong Chat.

---

## 3. Bảng kết quả kiểm chứng (`v2.1.0` · `8C6EB5C7…`)

| Hạng mục kiểm chứng | Trạng thái | Bằng chứng đo trực tiếp |
|---|---|---|
| Bộ test JVM & Contract (`:app:testDebugUnitTest`) | **PASS (665/669)** | Chạy sau chỉnh sửa cuối (`task-9944`, offline): `total=669, passed=665, failures=0, errors=0, skipped=4` across `75` files (4 ca live AI bỏ qua an toàn khi không đặt biến khóa: `V110CoachLiveEvalTest`, `V120LiveAiEvalTest`, `V210HaikuVsSonnetLiveEvalTest`, `V210HybridRoutingEvalTest`; bao gồm `V210ScheduleInteractionTest` `5/5`, `V210AutoUpdateTest` `5/5`, `V210WallyModelModeTest` `3/3`, `WallyContextEngineTest` `6/6`, `WallyOriginBindingTest` `11/11`, `WallySecurityStaticTest` `5/5`). |
| Android Lint (`:app:lintRelease` & `:app:lintDebug`) | **PASS (0 errors)** | `lintRelease`: `0 errors`, `64 warnings` · `lintDebug`: `0 errors`, `74 warnings` trên cây nguồn `8C6EB5C7…`. |
| Đóng gói & Ký số Release + Debug (`apksigner`) | **PASS** | Release `8C6EB5C7…` (`9,174,162` bytes, `versionCode 19`) & Debug `1738AC69…` (`15,297,401` bytes, `versionCode 19`) xác thực chữ ký v2 hợp lệ (`39f5712d…`). |
| Benchmark đối đầu Live AI (`Foza` thật · `24×2 = 48` lượt) | **MEASURED (`0` SafeViol)** | Từ `out/haiku_eval/hybrid_routing_24x2.jsonl` (`23` lượt Cloud/model, `S07` chặn cục bộ `2 ms`): **Pro (`Claude Sonnet 4.6`)** đạt **`23/24` (`95.8%`)**, `avgMs=6784`, ước tính 24 req `\$0.109464`; **Flash (`Claude Haiku 4.5`)** đạt **`19/24` (`79.2%`)** (trượt `3/12` ca đơn giản), `avgMs=2351`, `\$0.091200`; **Hybrid** đạt `20/24` (`83.3%`), `p95=10670 ms`, `\$0.097288`. Cơ sở chốt: **Pro mặc định, Flash tùy chọn, Auto tạm khóa**. |
| GitHub Auto-Update API thật (`v2.0.0` `/releases/latest`) | **PASS (Metadata + SHA)** | API thật trả tag `v2.0.0`, asset `9,009,774` bytes + `.sha256` (`BEDEE190…`), khớp parser & kiểm tra hợp đồng offline (`V210AutoUpdateTest`). Luồng tải & cài đè APK trong app từ GitHub thật chưa chạy cuối-tới-cuối trên thiết bị (`NOT_TESTED`). |
| Kiểm thử tương tác Lịch trình & Overlay trên `WallyQA` (`8C6EB5C7…`) | **PASS (Synthetic QA · Day/Sheet/Wally)** | Đã cài đè sạch `8C6EB5C7…` (`versionCode 19`) lên `WallyQA` (`index 1`, Android 9, dữ liệu synthetic, bảo toàn dữ liệu Room); kiểm chứng trực quan: (1) thanh `Chưa gắn giờ (1)` + `+ Ghi mới` giữ nguyên trên đầu khi cuộn tới `17:00–21:00`; (2) chạm ô trống chọn khoảng & mở `ManualEntrySheet` điền sẵn `14:15–15:15` (không tự bật khay chưa gắn giờ); (3) chạm thẻ `Code Lich` chọn cả khối (`Sửa / ▲ Sớm 15p / ▼ Muộn 15p / Xóa`); (4) bấm `▼ Muộn 15p` hiện bản nháp `Code Lich (Chưa lưu)` `14:15–15:15 → 14:30–15:30` kèm bóng mờ gốc + nút `✓ Lưu thay đổi / Hủy` và bấm `Hủy`; (5) mở sheet `Chưa gắn giờ → Đặt vào lịch` cho `"Doc QA" (30p)`; (6) mở `✦ Wally` xác nhận selector `Wally Pro (Claude Sonnet 4.6)` & `Wally Flash (Claude Haiku 4.5)`. Chế độ `3 ngày / Tuần / Tháng` & kéo cạnh vật lý trên màn cảm ứng chưa chụp riêng trên `8C6EB5C7…` (bao phủ qua JVM `V210ScheduleInteractionTest`). Đã tắt `WallyQA` sạch (`runninglist` rỗng). *(Thư viện `36` ảnh chuẩn Light/Dark của baseline `v2.0.0` `9CEA64A0…` xem `docs/screenshots/ui-preview/`.)* |
| Android 13–16 AVD (`TimeWallet_QA`), Cold-boot & Máy thật Owner | **NOT_TESTED** | Tạm dừng AVD AEHD theo Rule 14; chờ Owner cài APK `8C6EB5C7…` kiểm thử thực tế trên thiết bị cá nhân. |

---

## 4. Hướng dẫn cài đặt / nâng cấp cho Owner

1. **Nếu máy đang dùng Release `v2.0.0` (`versionCode 18`, cùng chứng thư `39f5712d…`):** Cài đè trực tiếp [TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk](out/wally_candidate_v210/TimeWallet-MVP-v2.1.0-PRODUCTION-CANDIDATE-release.apk) (`8C6EB5C7…`) — toàn bộ Sổ cái, Mục tiêu, Task, Habit, tùy chọn giao diện/cỡ chữ và khóa AI trong Keystore được giữ nguyên. *(Khuyến nghị Xuất JSON dự phòng trước mọi lần nâng cấp.)*
2. **Nếu máy đang cài bản Debug / từ Android Studio:** Xuất JSON sao lưu → gỡ bản Debug → cài sạch APK Release `v2.1.0` → vào **⚙ Hồ sơ & Cài đặt › Dữ liệu & Sao lưu JSON › Nhập / khôi phục JSON (`Khôi phục toàn bộ`)**.

---

## 5. Product Backlog sau `v2.1.0` (KHÔNG triển khai vào `v2.1.0`)

> **Quyết định phạm vi của Owner:** Toàn bộ các ý tưởng dưới đây được ghi nhận ở **mức backlog sản phẩm**, không đưa ồ ạt vào mã nguồn `v2.1.0`. Trọng tâm tiếp theo ưu tiên ba năng lực cốt lõi: **(1) ghi nhận nhanh**, **(2) hiểu dữ liệu chính xác**, và **(3) chuyển lời khuyên thành hành động được xác nhận**.

| Mức ưu tiên đề xuất | Hạng mục | Mô tả & giá trị kỳ vọng | Ghi chú ràng buộc sản phẩm |
|---|---|---|---|
| **P0** | **1. Smart Timeline** | Lịch hoạt động kiểu calendar trực quan hơn, hỗ trợ kéo thả, chỉnh sửa thời lượng và thêm nhanh hoạt động vào khoảng trống. | Phần mở rộng của `ScheduleCalendarView` đã có; giữ tách biệt `PLANNED` vs thực tế. |
| **P0** | **2. Quick Capture — Ghi nhận siêu nhanh** | Nhập một câu hoặc giọng nói (`"Đọc sách từ 14h đến 15h12"`) → Wally tạo bản nháp và cho xác nhận, hạn chế tối đa mở nhiều biểu mẫu. | Mở rộng luồng Quick Capture / Chat hiện có; luôn qua bước người dùng duyệt. |
| **P0** | **3. Goal Intelligence — Trí tuệ mục tiêu** | Không chỉ báo `"chạy bộ đạt 4/7 ngày"`, mà tìm nguyên nhân từ dữ liệu, đề xuất khung giờ phù hợp và theo dõi hiệu quả sau điều chỉnh. | Chỉ giải thích dựa trên số liệu thực trong sổ cái, không suy đoán cảm xúc/nguyên nhân bịa đặt. |
| **P0** | **10. Data Trust — Dữ liệu đáng tin cậy** | Phân biệt thời gian đo thực tế, tự khai báo và kế hoạch; phát hiện trùng lịch; sao lưu/khôi phục an toàn; AI không biến suy luận thành sự thật. | Kế thừa & củng cố 4 tầng bất biến MVP v1.0–v2.1. |
| **P1** *(Ý tưởng giá trị nhất)* | **4. Wally Action — Chat thành hành động** | Từ câu tự nhiên (`"Sắp xếp cho tôi 3 buổi học AI tuần sau"`), Wally tìm khoảng trống theo lịch đã ghi, đề xuất lịch cụ thể và chỉ ghi khi người dùng bấm duyệt. | Bước phát triển trọng tâm ngoài model: hiểu dữ liệu → phát hiện vấn đề → đề xuất hành động xác nhận. |
| **P1** | **6. Personal Insights — Câu chuyện từ dữ liệu** | Phát hiện xu hướng theo tuần/tháng: tiến bộ mục tiêu, biến động nhịp sinh hoạt, các yếu tố liên quan gắn với số liệu thật. | Mở rộng lớp `Wally kể từ dữ liệu` trên phạm vi đa tuần / tháng. |
| **P1** | **5. Daily Closure — Chốt ngày thông minh** | Cuối ngày nhận diện khoảng thời gian chưa ghi, gợi ý bổ sung nếu cần và tạo bản tổng kết ngắn. | Tuyệt đối không tự coi khoảng trống là thời gian lãng phí. |
| **P1** | **8. Wally Widget & Quick Actions** | Bắt đầu/dừng bộ đếm, ghi nhanh hoạt động và xem mục tiêu hôm nay ngay từ màn hình chính Android. | Giảm số lần mở app cho thao tác đo giờ hằng ngày. |
| **P2** | **7. Time Simulation — Thử trước khi đổi lịch** | Mô phỏng câu hỏi giả định (`"Nếu học thêm 30 phút mỗi tối thì sao?"`) tác động lên mục tiêu & lịch hiện tại. | Không tự coi phần `Chưa ghi nhận` là giờ rảnh. |
| **P2** | **9. Adaptive Reminder — Nhắc nhở có ngữ cảnh** | Nhắc trước hoạt động dự kiến hoặc khi có nguy cơ trượt mục tiêu; hỗ trợ giờ yên lặng, giới hạn tần suất và tắt hoàn toàn. | Tuân thủ giới hạn quyền tối thiểu Android và chống làm phiền. |
| **P2** *(Router gate)* | **Wally Auto Mode** | Chế độ tự chuyển giữa `Pro` và `Flash` theo độ phức tạp câu hỏi. | Chỉ kích hoạt khi bộ định tuyến vượt ngưỡng độ chính xác & tối ưu chi phí thực đo (hiện chưa mở). |
