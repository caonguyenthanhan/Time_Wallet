# Time Wallet (Ví Thời Gian) — Android Native + AI Time Coach

<p align="left">
  <img src="https://img.shields.io/badge/Version-v1.2.0%20(code%2016)-1F8066?style=flat-square" alt="Version v1.2.0" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.0.21" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Android-API%2026%E2%80%9335-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android API 26-35" />
  <img src="https://img.shields.io/badge/Room%20SQLite-Schema%20v5-00838F?style=flat-square" alt="Room Schema v5" />
  <img src="https://img.shields.io/badge/JVM%20Tests-625%2F627%20PASS-2E7D32?style=flat-square" alt="Unit Tests" />
  <img src="https://img.shields.io/badge/Lint%20Release-0%20errors-2E7D32?style=flat-square" alt="Lint Release" />
</p>

> **Tuyên ngôn sản phẩm:** *Thời gian là tài sản hữu hạn và không thể tích lũy. Time Wallet giúp bạn hiểu và chủ động phân bổ 24 giờ mỗi ngày một cách trung thực — không phán xét nghỉ ngơi và không chạy theo điểm số năng suất giả.*

<table>
  <tr>
    <td width="132" align="center" valign="middle">
      <img src="docs/brand/wally-hero.webp" alt="Wally — Linh vật Trợ lý Thời gian Time Wallet" width="112" />
    </td>
    <td valign="middle">
      <b>Wally — Trợ lý AI Thời gian cá nhân:</b> Kết hợp <b>Sổ cái Thời gian 5 lớp bất biến (Offline-first Room v5)</b> với <b>Wally AI (API-First Opt-in)</b> kiểm chứng tất định, tách rạch ròi <i>thực tế có mốc giờ / chưa gắn giờ / dự kiến</i>, linh vật <b>Wally chi tiết nền trong suốt</b> (<a href="docs/brand/wally-hero.webp">wally-hero.webp</a>) cùng bộ icon vector 24dp & Launcher Monochrome đồng nhất (<a href="docs/brand/TimeWallet_Wally_Icon_Style_Guide_v3.svg">Icon Design System v3</a>).
    </td>
  </tr>
</table>

Ứng dụng Android Native **Time Wallet (`com.timewallet.mvp`)** là sổ cái cá nhân kèm hồ sơ đặc tả [Time_Wallet_MVP_Spec.md](Time_Wallet_MVP/Time_Wallet_MVP_Spec.md) và bàn giao phát hành [WALLY_V120_PRODUCTION_CANDIDATE.md](WALLY_V120_PRODUCTION_CANDIDATE.md).

---

## Trải nghiệm giao diện (`v1.2.0`)

<p align="center">
  <img src="docs/screenshots/ui-preview/_tong-hop_5-man-hinh.jpg" alt="Tổng hợp 5 màn hình Time Wallet v1.2.0" width="960" />
</p>

<p align="center">
  <sub><i>Ảnh tổng hợp chụp trực tiếp trên APK Release <code>7D703385…</code> (<code>v1.2.0</code> · <code>versionCode 16</code>) và bộ showcase synthetic <code>AC224463…</code> trên emulator QA tách biệt <code>WallyQA</code> (Android 9, <code>780×1688 @320dpi</code>) với dữ liệu mock synthetic 1 tuần + hệ thống Lịch trình &amp; Ghi nhận hợp nhất, tự dọn ô chat &amp; lưu lịch sử multi-turn và kết nối live AI (khóa QA đã xóa sạch khỏi máy ảo ngay sau khi chụp; hiển thị trên thiết bị thật Owner: <code>NOT_TESTED</code>).</i></sub>
</p>

---

## Điểm nổi bật kỹ thuật

- **Sổ cái Thời gian 5 lớp bất biến & Chống chồng lấn nguyên tử (`domain/` + Room v5):**
  - Tách biệt rõ 5 tầng `MEASURED`, `REPORTED_TIMED`, `REPORTED_UNTIMED`, `ACTIVE` và `PLANNED` (`ConfirmationState`: `PLANNED`, `DOING`, `PENDING_CONFIRMATION`, `COMPLETED`, `ADJUSTED`, `INTERRUPTED`, `CANCELLED`).
  - Không bao giờ tự suy đoán mốc giờ bắt đầu/kết thúc cho dữ liệu chỉ có thời lượng; không cộng gộp `REPORTED_UNTIMED` hay `PLANNED` vào trục 24h / cột 7 ngày có mốc giờ; hỗ trợ hoạt động vắt qua nửa đêm **`UC-15`** và tách đoạn nghỉ xen giữa / hoạt động song song trong Room `@Transaction` kết hợp `LedgerConcurrencyGate.mutex`.
- **AI-First Opt-in + Kiểm chứng tất định (Style riêng, Siêu ngắn 5 giây & Interactive Action Items):**
  - `Wally Chat` và `Wally AI Coach` (`wally-coach-5`) kết nối OpenAI-compatible Chat Completions gateway bằng API Key riêng mã hóa AES-GCM Android Keystore (mặc định `cinb/claude-sonnet-4-6`).
  - Thẻ **Wally AI Spotlight** trình bày có cấu trúc gọn (`headline` 1 câu, `1–2` điểm sáng `●`, badge `Lưu ý:` màu ấm, và **Action Items** có ô đánh dấu + nút 1 chạm **`Lên lịch →` / `Bấm giờ →` / `Gắn Tag →` / `Nhìn lại →`** chỉ mở sẵn form cho người dùng quyết định, không tự ghi DB).
  - Mọi đầu ra qua chuỗi guard tất định (`ReplyGuard`, `AiProposalGuard`, `CoachGuard.checkStructured`, `Rx.kt`) chặn số ngoài dữ liệu, phán xét hay suy đoán cảm xúc.
- **5 thành phần Jetpack Compose tương tác gốc vừa tích hợp (`v1.2.0`):**
  1. **Timeline dọc theo giờ & Chỉnh nhanh `±15p` (`HistoryScreen`, `UiKit`):** sắp tăng dần từ sáng đến tối, cột giờ đối diện bên trái, chấm đặc / vòng xoay động cho phiên `DOING` / đường nối dừng ở chấm cuối, hỗ trợ tay cầm `−15p`/`+15p` cho thẻ `PLANNED` (bị khóa với bản ghi đã ghi nhận).
  2. **Hội thoại Chat chuẩn mực (`ChatScreen`):** `LiveRegionMode.Polite`, bong bóng bo góc kèm đuôi hướng người nói, giới hạn ~75% chiều rộng cột, metadata giờ gửi + `Đã gửi ✓` / `Wally đã xem ✓✓`, chỉ báo 3 chấm nảy tuần tự (`WallyTypingDots`) và thẻ trả lời `✦` kèm pill `◆ BƯỚC TIẾP THEO`.
  3. **Donut 2D tương tác (`OverviewScreen`, `InsightsScreen`, `UiKit`):** tối đa 6 lát cắt (`capDonutSlices`), khe hở đều, chạm lát hoặc dòng chú giải đẩy lát nổi nhẹ 2D (không 3D) và cập nhật chi tiết ở lỗ giữa.
  4. **Nhóm Floating Action + Speed Dial + Back-to-Top (`MainActivity`):** FAB tròn mở Speed Dial 3 hành động (`Ghi giờ thủ công`, `Bắt đầu đồng hồ`, `Nhìn lại ngày hôm nay`), nút mở nhanh Wally Chat, và nút Back-to-Top chỉ hiện khi cuộn `> 400 px`.
  5. **Kéo-thả sắp xếp thứ tự ngân sách (`InsightsScreen`, `BudgetDao`, `BudgetPolicy`):** tay cầm `⋮⋮`, bản xem trước nổi, vạch chèn `BudgetInsertionLine` trước khi thả, nút trợ năng `↑`/`↓` giữ nguyên phân nhóm ưu tiên và công bố qua `liveRegion`.
- **Lịch trình trực quan & Ghi nhận hoạt động hợp nhất (`ScheduleCalendarView` + `ScheduleGridEngine`, `v1.2.0`):**
  - Thiết kế lại luồng ghi thủ công thành lịch quản lý 4 chế độ **Ngày / 3 ngày / Tuần / Tháng** chuẩn tương tác Google Calendar nhưng phân tách chặt chẽ **Kế hoạch (`Dự kiến` / `Chưa xác nhận`) vs Thực tế (`Đã thực hiện` / `Đang diễn ra`)**, đối chiếu song song 60p Kế hoạch vs 72p Thực tế (`KH 1h -> TT 1h 12p (+12p)`) mà không tự động đóng kế hoạch và không âm thầm sửa lịch sử thực tế.
  - Chạm trực tiếp khoảng trống để điền sẵn khung giờ `+ Ghi mới`, kéo đổi giờ / kéo cạnh dưới chỉnh thời lượng bước 15 phút (`ConfirmationEngine.moveOrResizeScheduleBlock`), khay riêng **`Chưa gắn giờ · Chỉ thời lượng`** (tuyệt đối không gán giờ giả), và ô **Ghi nhanh không cần mốc giờ** (`"Đọc sách 30 phút"`, `ManualEntry.parseQuickInput`) trong `ManualEntrySheet`.
  - Tích hợp cả từ `"Thêm giờ"` và trong tab **Nhật ký** (`Lịch trình | Dòng thời gian`).
- **Độ tin cậy Wally Chat & Lưu bền vững (`VietnameseChatParser`, `WallyAiGuards`, `TimerAndSettingsStore`):**
  - Hỗ trợ khoảng giờ có hoạt động xen giữa (`"9h làm việc đến 20h"` = 11h) và đuôi `"đến giờ"` gắn hoạt động (`"21h đến giờ review code"`).
  - Tự động dọn chữ đã gửi trong ô nhập khi đang gửi (`"Wally đang soạn…"`), giữ nháp khôi phục khi Hủy/Lỗi, và lưu bền vững lịch sử chat đa lượt (`ChatHistoryEntry`) cùng đề xuất chờ xác nhận (`SavedProposalDraft`) qua `force-stop`/khởi động lại kèm nút `Xóa lịch sử`.
- **Sẵn sàng Production dùng cá nhân (`v1.2.0` · `versionCode 16`):**
  - **Sao lưu / Khôi phục JSON lossless** (`MERGE_SAFE` và `REPLACE_ALL`, an toàn khi chuyển `Debug → Release`, không ghi đè khóa AI), cô lập Release Keystore ngoài Git, `CompletionBootReceiver` khôi phục lịch nhắc `AlarmManager` sau khởi động lại / cập nhật app / đổi múi giờ.
  - **Hệ thống nhận diện & Icon Design System v3 (`docs/brand/`):** 38 VectorDrawable đồng nhất (`ic_nav_*`, `ic_nav_timer_pressed` giữ kim trắng sắc nét trên nền Mint, 9 `CategoryBadge` danh mục thay emoji rời rạc, tách biệt rõ ràng `Warning` / `Error` / `Eraser` / `Delete` / `Notify`), Launcher Adaptive + Monochrome (`@mipmap/ic_launcher`), avatar 2D đồng bộ cho Coach/Header/Bubble (`32–48dp`), và linh vật toàn thân nền trong suốt (`ic_wally.webp` `512×512` xuất từ `Wally_Vector_Detailed.svg`) loại bỏ hoàn toàn ô nền xanh lệch màu trên `ChatScreen`.
  - **Kiểm chứng hiện hành (`7D703385…`):** `625/627` JVM & contract tests PASS (`0 failed`, `0 errors`, `2` bài Live API `V110CoachLiveEvalTest`/`V120LiveAiEvalTest` skipped khi chạy offline), `lintRelease` đạt **0 errors** (`53 warnings`), APK Release ký số Scheme v2 hợp lệ (`7D703385…`, `8,682,089` bytes); kiểm thử trực quan + tương tác Lịch trình 4 chế độ + Ghi nhanh không dấu + Chat live & bền vững qua `force-stop` đạt **PASS** (`FATAL EXCEPTION = 0`) trên emulator QA `WallyQA`. *(Cold-boot `BOOT_COMPLETED`, Android 13–16 AEHD và thiết bị thật của Owner giữ trạng thái `NOT_TESTED`.)*

---

## Kiến trúc hệ thống

| Tầng | Thành phần chính | Trách nhiệm |
|---|---|---|
| **UI (Compose M3)** | `MainActivity`, `OverviewScreen`, `TimerScreen`, `ChatScreen`, `HistoryScreen`, `ScheduleCalendarView`, `ManualEntrySheet`, `InsightsScreen`, `PersonalScreens`, `DailyReviewScreen`, `UiKit` | Hiển thị, Lịch trình xanh 4 chế độ (`Ngày`/`3 ngày`/`Tuần`/`Tháng`), tương tác 48dp + semantics/liveRegion, Speed Dial, Donut 2D, Timeline, Drag & Drop ngân sách, không chứa nghiệp vụ |
| **State & Coordinator** | `TimeWalletViewModel`, `WallyChatRunner`, `WallyChatReducer`, `ManualEntryDraftCoordinator` | Điều phối luồng bất đồng bộ theo `AnalysisEpoch`, khôi phục nháp + lịch sử chat đa lượt, `Clean`, nối DAO/Scheduler |
| **Domain (Kotlin thuần)** | `ScheduleGridEngine`, `ManualEntry`, `LedgerValidator`, `LedgerAnalyticsCalculator`, `LedgerQueryEngine`, `ConfirmationEngine`, `ActivityBreakEngine`, `BatchProposalPlanner`, `BudgetPolicy`, `TagRules`, `GoalRules`, `VietnameseChatParser`, `WallyAiGuards`, `CoachDigestBuilder`, `CoachGuard`, `Rx` | Bố cục lịch & đối chiếu Kế hoạch/Thực tế, ghi nhanh bảo toàn bản ghi không mốc giờ, kiểm chứng bất biến, tính 24h / 7 ngày / `UC-15`, phân giải break/song song, kiểm soát AI & Regex đa nền tảng |
| **Data & System** | `TimeWalletDatabase` (Room v5), `LedgerDao`, `BudgetDao`, `DailyReviewDao`, `TagGoalDao`, `LedgerJsonBackup`, `TimerAndSettingsStore`, `GeminiProposalClient`, `CompletionReminder` | Lưu trữ SQLite nguyên tử, Export/Import JSON lossless, AES-GCM Keystore cho API key, `AlarmManager` + `CompletionBootReceiver` |

---

## Bộ ảnh chi tiết các màn hình (`docs/screenshots/ui-preview/`)

<details>
<summary>Mở xem bộ ảnh chụp màn hình chi tiết trên APK Release 7D703385… / showcase AC224463… &amp; mô tả luồng nghiệp vụ</summary>

| Màn hình / Luồng chính | Ảnh chụp | Mô tả nhanh |
|---|---|---|
| Tổng quan | [01_tong-quan.png](docs/screenshots/ui-preview/01_tong-quan.png) | Ví thời gian hôm nay (`14h 50` đã ghi nhận + `Dự kiến 2h 40`), avatar 2D Wally, icon vector `Hỏi Wally` và Donut 2D dùng bộ icon danh mục v3 |
| Donut 2D tương tác | [01_tong-quan_donut_pop.png](docs/screenshots/ui-preview/01_tong-quan_donut_pop.png) | Chạm dòng `Ngủ` đẩy nổi lát 2D (`6h 30 · 44%`) và cập nhật chi tiết ở tâm |
| Wally AI Coach (`7 ngày qua`) | [01b_wally-coach-ai.png](docs/screenshots/ui-preview/01b_wally-coach-ai.png) | Thẻ `✦ WALLY AI · 7 NGÀY QUA` ngắn gọn (`cinb/claude-sonnet-4-6`), avatar 2D mới, hộp `Lưu ý:`, Action Item (`Lên lịch →`) và nhóm nút nổi (`↑`, robot mini, `+`) |
| Speed Dial thao tác nhanh | [01c_speed-dial.png](docs/screenshots/ui-preview/01c_speed-dial.png) | FAB tròn góc dưới phải mở 3 hành động nhanh (`Nhìn lại ngày hôm nay`, `Bắt đầu đồng hồ`, `Ghi giờ thủ công`) |
| Đồng hồ | [02_dong-ho.png](docs/screenshots/ui-preview/02_dong-ho.png) | Tab đang chọn dùng `ic_nav_timer_pressed` giữ kim trắng sắc nét trên nền Mint, chip danh mục/Tag vector (`Học AI`, `Deep Work`, `Đọc sách`), thẻ `Dopamine Time Dice` |
| Wally Chat & câu trả lời `✦` | [03_wally.png](docs/screenshots/ui-preview/03_wally.png) | Bong bóng chat có đuôi, metadata `"23:28 · Wally đã xem ✓✓"`, thẻ trả lời `✦ Wally (AI)` có gạch nhấn và danh sách rõ ràng |
| Chỉ báo 3 chấm đang soạn & dọn ô nhập | [03a_wally-typing.png](docs/screenshots/ui-preview/03a_wally-typing.png) | Tự dọn chữ đã gửi về `"Kể tiếp ngày của bạn..."` trong khi bong bóng `Wally đang soạn...` hiển thị + nút `Hủy` |
| Wally AI (Đa đề xuất) | [03b_wally-proposals.png](docs/screenshots/ui-preview/03b_wally-proposals.png) | `✦ AI đã bật`, bóc tách câu tiếng Việt thành 5 thẻ đề xuất kèm 5 `CategoryBadge` vector và nút `✓ Xác nhận toàn bộ (5 hoạt động)` |
| Lịch sử chat bền vững (`force-stop`) | [03d_wally-history-persisted.png](docs/screenshots/ui-preview/03d_wally-history-persisted.png) | Giữ nguyên tin nhắn + đề xuất qua `am force-stop`, kèm nút `Xóa lịch sử` an toàn (chỉ xóa hội thoại) |
| Wally Welcome (Mascot chi tiết) | [03c_wally-welcome.png](docs/screenshots/ui-preview/03c_wally-welcome.png) | Màn chào `ChatScreen` hiển thị `ic_wally.webp` (`512×512` xuất từ `Wally_Vector_Detailed`) nền trong suốt hoàn toàn, hòa tự nhiên với nền sáng không lệch màu |
| Nhật ký (Tổng quan ngày) | [04_nhat-ky.png](docs/screenshots/ui-preview/04_nhat-ky.png) | Timeline từ sáng `07:00 → 14:30`, `CategoryBadge` vector, icon thùng rác `Delete` đỏ tách biệt, `+6h 30` chuyển tiếp xuyên đêm (`UC-15`) |
| Timeline `DOING` / `PLANNED` & `±15p` | [04b_nhat-ky-doing-planned.png](docs/screenshots/ui-preview/04b_nhat-ky-doing-planned.png) | Thẻ chờ xác nhận (`19:45`, `21:15`), nút `✓ Xác nhận kết quả` và tay cầm `↔ Kéo giãn nhanh ±15p` |
| Nhật ký tích hợp Lịch trình / Dòng thời gian | [04c_nhat-ky-lich-trinh.png](docs/screenshots/ui-preview/04c_nhat-ky-lich-trinh.png) | Thanh chuyển đổi `Lịch trình \| Dòng thời gian` ngay đầu tab Nhật ký |
| Sửa hoạt động & tách Kế hoạch / Thực tế | [04d_sua-hoat-dong.png](docs/screenshots/ui-preview/04d_sua-hoat-dong.png) | Nút Xóa hoạt động góc trên phải, ghi chú bảo toàn tách bạch Kế hoạch/Thực tế và nút `Xác nhận kết quả thực tế` |
| Phân tích (`7 ngày gần nhất`) | [05_phan-tich.png](docs/screenshots/ui-preview/05_phan-tich.png) | Đủ **7 cột `Th 4–Th 3`** (`106h 15` tuần), khử trùng song song `1h 00`, icon vector danh mục |
| Donut 6 danh mục & Ngân sách Kéo-Thả | [05b_phan-tich-budgets-dnd.png](docs/screenshots/ui-preview/05b_phan-tich-budgets-dnd.png) | Thẻ `Ngân sách tuần` dùng `CategoryBadge` vector + thùng rác `Delete` + tay cầm `⋮⋮` sắp xếp ưu tiên |
| Lịch trình Ngày (Kế hoạch vs Thực tế) | [06a_lich-trinh-ngay.png](docs/screenshots/ui-preview/06a_lich-trinh-ngay.png) | Header xanh 4 chế độ, nút `Hôm nay`, đối chiếu song song `Đọc sách (KH)` 60p vs Thực tế `72p (+12p)`, `+ Trống 15:12–19:30`, khay `Chưa gắn giờ` |
| Lịch trình 3 ngày | [06b_lich-trinh-3ngay.png](docs/screenshots/ui-preview/06b_lich-trinh-3ngay.png) | So sánh Hôm qua (`UC-15`), Hôm nay (vạch giờ đỏ hiện tại + thẻ `Chưa xác nhận`), Ngày mai |
| Lịch trình Tuần (`7 cột`) | [06c_lich-trinh-tuan.png](docs/screenshots/ui-preview/06c_lich-trinh-tuan.png) | Phân bổ toàn tuần `T2–CN`, tóm tắt giờ mỗi ngày, vạch giờ đỏ hôm nay |
| Lịch trình Tháng (`Ngày trống` & `KH`) | [06d_lich-trinh-thang.png](docs/screenshots/ui-preview/06d_lich-trinh-thang.png) | Tổng quan tháng, đánh dấu ngày `Trống`, ngày có `KH 3h30p`, chấm chờ xác nhận |
| Thêm giờ (`+ Ghi mới` từ khoảng trống) | [06_nhap-gio_manual.png](docs/screenshots/ui-preview/06_nhap-gio_manual.png) | Tự điền sẵn `04:00–05:00` khi chạm ô trống trên lịch, ô ghi nhanh ở đầu form, Tag thu gọn |
| Ghi nhanh `"Doc sach 30 phut"` -> `Áp nhanh` | [06e_ghi-nhanh-khong-gio.png](docs/screenshots/ui-preview/06e_ghi-nhanh-khong-gio.png) | Tự nhận Tag `Đọc sách` + nhóm `Học tập` + `Thời lượng 30p`, giữ nguyên chế độ không gắn mốc giờ |
| Xử lý hoạt động xen giữa | [07_xu-ly-chong-lan.png](docs/screenshots/ui-preview/07_xu-ly-chong-lan.png) | Hộp thoại chọn khoảng nghỉ xen giữa / chạy song song / chỉnh giờ |
| Xác nhận trạng thái | [08_xac-nhan-trang-thai.png](docs/screenshots/ui-preview/08_xac-nhan-trang-thai.png) | Đối soát hoàn thành, điều chỉnh, dừng sớm hoặc hủy hoạt động dự kiến |
| Bản nháp đang nhập dở | [09_ban-nhap_tong-quan.png](docs/screenshots/ui-preview/09_ban-nhap_tong-quan.png) | Thẻ khôi phục bản nháp tự lưu trên Tổng quan kèm nút `Clean · Xóa nháp` |
| Nhập & khôi phục JSON | [10_nhap-json_release.png](docs/screenshots/ui-preview/10_nhap-json_release.png) | Chọn tệp `.json` chuyển dữ liệu `Debug -> Release` an toàn (`Gộp an toàn` / `Khôi phục toàn bộ`) |

Xem chi tiết điều kiện chụp tại [docs/screenshots/ui-preview/README.md](docs/screenshots/ui-preview/README.md) (bộ ảnh baseline `v1.0.11` giữ riêng tại [docs/screenshots/ldplayer-android9/README.md](docs/screenshots/ldplayer-android9/README.md)).

</details>

---

## Cấu trúc thư mục

```text
Time_Wallet/
├── .agents/                        # Quy tắc dự án & bảo vệ môi trường QA
├── Time_Wallet_MVP/                # Đặc tả MVP & HTML Prototype gốc
├── ai_test/                        # Script kiểm thử gateway (đọc biến môi trường FOZA_API_KEY)
├── docs/
│   ├── archive/                    # Hồ sơ, review & biên bản QA lịch sử (v1.0.5–v1.0.13)
│   ├── brand/                      # Icon Design System v3 SVG & Wally WebP showcase
│   └── screenshots/                # Bộ ảnh UI v1.2.0 Preview & baseline v1.0.11
├── app/
│   ├── build.gradle.kts            # Cấu hình Kotlin, Compose, Room schema export, Release signing ngoài Git
│   ├── schemas/                    # Lược đồ Room DB xuất tự động (v3, v4, v5)
│   └── src/
│       ├── main/java/com/timewallet/mvp/
│       │   ├── MainActivity.kt
│       │   ├── data/               # AI client, Diagnostics, Room DAO/Entities, Backup JSON, Settings
│       │   ├── domain/             # Models, Validators, Analytics, Confirmation/Break Engine, AI Guards
│       │   ├── notifications/      # AlarmManager CompletionReminder & CompletionBootReceiver
│       │   └── ui/                 # TimeWalletViewModel, Screens, Dialogs, UiKit
│       └── test/java/com/timewallet/mvp/  # 67 bộ kiểm thử JVM (627 test cases)
├── WALLY_TECHNICAL_SPEC.md         # Đặc tả kỹ thuật chi tiết & Mermaid diagrams
├── WALLY_V120_PRODUCTION_CANDIDATE.md  # Hồ sơ Production Candidate v1.2.0 & hướng dẫn Owner cài máy thật
└── CONTEXT_MANIFEST.md
```

---

## Xây dựng, Kiểm thử & Phát hành nội bộ

1. **Android Studio:** Mở thư mục `Time_Wallet`, chờ Gradle đồng bộ và nhấn **Run (`Shift + F10`)**.
2. **Chạy toàn bộ kiểm thử JVM, Android Lint và đóng gói APK:**
   ```powershell
   .\gradlew.bat testDebugUnitTest lintRelease assembleDebug
   ```
3. **Đóng gói APK Release ký số cá nhân** *(cấu hình keystore nằm hoàn toàn ngoài Git tại `%USERPROFILE%\.timewallet\keystore.properties` hoặc biến môi trường `TIMEWALLET_KEYSTORE_*`)*:
   ```powershell
   .\gradlew.bat assembleRelease
   ```
4. **Tài liệu liên quan:**
   - [Hồ sơ bàn giao Production Candidate `v1.2.0`](WALLY_V120_PRODUCTION_CANDIDATE.md) (SHA-256, bảng PASS vs `NOT_TESTED`, hướng dẫn Debug → Release)
   - [Đặc tả kỹ thuật hệ thống](WALLY_TECHNICAL_SPEC.md)
   - [Danh sách tự kiểm trên máy thật](WALLY_OWNER_CHECKLIST_V110.md)
