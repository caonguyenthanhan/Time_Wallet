# Wally — Time Wallet (`v2.1.0`)

**Trung tâm Quản lý Thời gian & Năng suất Cá nhân · Riêng tư tại máy · AI đề xuất có kiểm soát**  
*Your time. Your choices. Your life.*

<p align="left">
  <img src="https://img.shields.io/badge/Version-v2.1.0%20(code%2019)-1F8066?style=flat-square" alt="Version v2.1.0" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.0.21" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Android-API%2026%E2%80%9335-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android API 26-35" />
  <img src="https://img.shields.io/badge/Room%20SQLite-Schema%20v7-00838F?style=flat-square" alt="Room Schema v7" />
  <img src="https://img.shields.io/badge/JVM%20Tests-653%2F655%20PASS-2E7D32?style=flat-square" alt="Unit Tests" />
  <img src="https://img.shields.io/badge/Lint%20Release-0%20errors-2E7D32?style=flat-square" alt="Lint Release" />
</p>

<table>
  <tr>
    <td width="136" align="center" valign="middle">
      <img src="docs/brand/wally-hero.webp" alt="Wally — Personal Time Companion" width="116" />
    </td>
    <td valign="middle">
      <p><b>Ai cũng chỉ có 24 giờ mỗi ngày — điều quan trọng không phải là nhồi nhét cho kín lịch, mà là nhìn rõ thời gian thực sự đi đâu để sống chủ động hơn.</b></p>
      <p><b>Time Wallet (Wally v2.1)</b> kết nối lịch trình, công việc, thói quen, mục tiêu, phân tích hoạt động 3 lớp và trợ lý AI thích ứng vào một không gian Android bản địa — gọn gàng, chạy mượt khi ngoại tuyến, tôn trọng sự nghỉ ngơi và luôn giữ dữ liệu thuộc toàn quyền sở hữu của bạn.</p>
      <p><i><b>English summary:</b> Time Wallet v2.1 builds on Wally 2.0's local-first Android productivity &amp; time hub (Calendar, Tasks &amp; Subtasks, Habits, Goals, Focus Pin) with Method A Adaptive AI Context, 3-layer 24h activity/goal analytics, and grounded data storytelling — clarity without guilt, fake productivity scores, or lock-in.</i></p>
    </td>
  </tr>
</table>

> **Tuyên ngôn sản phẩm · Product Manifesto**  
> Không phải phút nào trong ngày cũng phải "tối đa năng suất". Thời gian cho công việc, học tập, giấc ngủ hồi phục hay những khoảnh khắc nghỉ ngơi đều xứng đáng được nhìn nhận trung thực — tách bạch rõ giữa **điều bạn dự kiến làm** và **điều thực sự đã diễn ra**, không bịa giờ cho ghi chú chỉ có thời lượng, và không bao giờ để AI tự ý thay đổi sổ cái khi bạn chưa bấm đồng ý.  
> *Truthful tracking over fake hustle: plans stay distinct from actuals, rest is respected alongside deep work, your records stay local-first on your device, and Wally only proposes changes — you always decide.*

---

## Product Demo

<!-- YOUTUBE_EMBED -->

---

## Why Time Wallet? · Vì sao chọn Time Wallet?

| Vấn đề thường gặp ở app truyền thống | Cách Wally `v2.1.0` giải quyết |
|---|---|
| Bắt ghi quá nhiều nơi: app lịch riêng, to-do riêng, bấm giờ riêng → ngại mở hoặc nhập trùng | **Một vòng khép kín tự nhiên:** từ *Inbox / Việc cần làm* đẩy lên *Lịch*, bấm `▶` đo ngay kèm checklist *Subtasks*, và tự khớp hoàn thành *Thói quen* nếu đúng Tag & đủ phút. |
| Gộp chung dự kiến với thực tế, hoặc tự đoán khung giờ cho ghi nhận rời rạc | **Trung thực tuyệt đối với dữ liệu 24h:** tách rõ *Đã đo (`MEASURED`)*, *Có mốc giờ (`REPORTED_TIMED`)*, *Chỉ thời lượng (`Chưa gắn giờ`)* và *Kế hoạch (`PLANNED`)*. |
| Chấm điểm năng suất gây áp lực khi nghỉ ngơi | **Nhìn rõ không phán xét:** mặc định biểu đồ **5 nhóm hoạt động thực tế** đối chiếu với `Chưa ghi nhận`, tách nghỉ ngơi hợp lý khỏi lãng phí, và chỉ đánh dấu lãng phí khi bạn chọn. |
| Phụ thuộc cloud, đòi nhiều quyền hệ thống, khó biết AI đang đọc gì | **Local-first & Privacy-first:** chỉ dùng **3 quyền Android tối thiểu** (`INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`), bật/tắt chi tiết **4 phạm vi đọc của AI**, `AdaptiveContextRouter` chỉ gửi ngữ cảnh giới hạn khi cần, khóa API mã hóa AES-GCM trong Android Keystore và không xuất ra sao lưu. |

---

## Key Capabilities in `v2.1.0`

- **Lịch trình là trung tâm (`Lịch trình · Ngày / 3 ngày / Tuần / Tháng`):** Xem ngay thanh **Ví 24h thu gọn 1 dòng** ở đầu trang, đối chiếu song song **Kế hoạch vs Thực tế**, xử lý vắt nửa đêm (`UC-15`), chọn ô giờ trống tạo nhanh kế hoạch, cuộn ngang lịch Tuần với cột giờ cố định, phóng to thẻ chi tiết ngày/hoạt động và thu gọn bộ lọc/chú giải vào menu `⋮`.
- **Việc cần làm, Subtasks, Thói quen & Mục tiêu hợp nhất:** Chụp nhanh ý tưởng vào *Inbox*, quản lý *Priority (`P1–P3`)*, mở rộng *Subtasks* ngay trên thẻ, theo dõi chuỗi *Thói quen* 7 ngày (`T2–CN`) tự khớp từ sổ cái (`⚡`), và gộp *Mục tiêu tối thiểu (`MIN`)* cùng *Ngân sách giới hạn (`MAX`)* theo chu kỳ. Bật thêm *Kanban* hoặc *Ma trận Eisenhower* trong Cài đặt nếu cần.
- **Tập trung thực chiến & Ghim thông báo (`Focus Pin`):** Chuyển đổi nhanh giữa *Đếm xuôi*, *Pomodoro 25p* và *Sâu 45p*; đo từ một *Task* sẽ mang theo mục tiêu phút và checklist *Subtasks* xuống ngay dưới đồng hồ. Bật **Ghim thông báo tập trung (`id = 910301`)** để giữ phiên đang đo, kế hoạch hiện tại hoặc gợi ý theo lịch sử (`tuần trước / hôm qua / 3 ngày gần đây`) ngay trên thanh thông báo kèm nút `Bỏ ghim`.
- **Trợ lý `✦ Wally` xuyên suốt, Bóng chat Pop-on & Ngữ cảnh thích ứng (Method A):** Mở nhanh từ mọi tab qua **bóng chat Pop-on kéo thả tự hít mép** hoặc nút `✦ Wally` trên thanh trên cùng (`WallyAiOverlay`); `AdaptiveContextRouter` & `WallyContextEngine` chỉ đính kèm mục tiêu thiếu hụt, lịch trống và hội thoại nối tiếp trên các câu hỏi tư vấn kế hoạch (giữ payload tối giản cho ghi nhận nhanh & câu hỏi số liệu, tuân thủ nghiêm `AiReadPermissions` và luôn chờ xác nhận trước khi lưu).
- **Thống kê 3 lớp & Wally kể từ dữ liệu (`Mở rộng › Thống kê`):** Mặc định xem biểu đồ 24h × 7 ngày theo **5 nhóm hoạt động thực tế** (`Ngủ, nghỉ · Công việc · Học tập · Sức khỏe · Giải trí & Khác`) tách bạch với `Chưa ghi nhận` và `Chưa gắn giờ`, kèm chuyển nhanh góc nhìn **4 tầng Mục tiêu** (`Hoạt động | Mục tiêu`), chạm ngày xem chi tiết Tag/hoạt động + `% so với tuần trước`, giải thích tự động khi mục tiêu `0p` do thiếu gắn Tag, và thẻ **Wally kể từ dữ liệu** 4 bước (`Điều gì xảy ra → Đáng chú ý → Ảnh hưởng mục tiêu → Có thể làm tiếp`) có ghi chú thận trọng khi dữ liệu chưa đủ.
- **Tùy biến linh hoạt, Dark Mode đồng bộ & Sở hữu dữ liệu mở:** Chuyển nhanh giao diện *Theo máy / Sáng / Tối*, bật tắt từng module nâng cao (*Habits, Pomodoro, Kanban, Eisenhower, Xúc xắc thời gian, Wally AI*), đặt quy tắc phân loại từ khóa offline, xuất/nhập **JSON `schemaVersion = 2`** (tương thích bản `1.x`) và xuất **nhật ký Markdown** cho Obsidian.

---

## UI Preview (`v2.1.0` / `v2.0` · Light & Dark Mode)

<p align="center">
  <img src="docs/screenshots/ui-preview/_tong-hop_wally-2.0.jpg" alt="Time Wallet v2.1.0 — Light & Dark 5-Screen Overview" width="960" />
</p>

<p align="center">
  <sub><i>Gallery covers 36 screenshots (plus 5-panel overview banner) captured on the isolated <code>WallyQA</code> emulator (Android 9, <code>780×1688 @320dpi</code>) using synthetic QA data: refreshed Day Schedule, 3-Layer Activity/Wally Story Stats &amp; Wally Overlay captures (<code>01</code>, <code>15</code>, <code>15b</code>, <code>24</code>, <code>26</code>, <code>26b</code>, banner) were captured from Release APK <code>9CEA64A0C9C1…</code> (identical UI source to <code>v2.1.0</code> <code>005378EF…</code> right before the <code>versionCode 18 → 19</code> bump), alongside unchanged screens from prior <code>BEDEE1904C58…</code> / <code>50EBDFFF…</code> <code>v2.0.0</code> baseline checks. Owner personal instance (<code>index 0</code>), live AI eval on Adaptive Context, Android 13–16 AEHD, cold-boot, and physical device remain untouched / <code>NOT_TESTED</code>.</i></sub>
</p>

<details>
<summary>Browse all 36 Light &amp; Dark screen captures (<code>docs/screenshots/ui-preview/</code>)</summary>

| Screen / Flow | Capture | Highlights |
|---|---|---|
| Day Schedule (Today · Light) | [01_lich-trinh-hom-nay_light.png](docs/screenshots/ui-preview/01_lich-trinh-hom-nay_light.png) | Collapsible 1-line 24h Wallet strip (`22/11`), timeline with sleep/planned blocks, draggable Wally Pop-on bubble |
| Day Schedule (`18/11` · `Hôm nay` Pill) | [02_lich-trinh-ngay_light.png](docs/screenshots/ui-preview/02_lich-trinh-ngay_light.png) | Clean `Hôm nay` shortcut pill, `UC-15` overnight blocks, Pop-on floating bubble |
| 3-Day / Week / Month Schedule | [03_lich-trinh-3ngay_light.png](docs/screenshots/ui-preview/03_lich-trinh-3ngay_light.png) · [04_lich-trinh-tuan_light.png](docs/screenshots/ui-preview/04_lich-trinh-tuan_light.png) · [05_lich-trinh-thang_light.png](docs/screenshots/ui-preview/05_lich-trinh-thang_light.png) | Enlarged horizontally-scrollable 3-day & `T2–CN` week grids with pinned hour axis, plus enlarged monthly detail cards |
| Calendar `⋮` Menu | [06_lich-trinh-menu_light.png](docs/screenshots/ui-preview/06_lich-trinh-menu_light.png) | Secondary controls (`＋ Ghi mới hoạt động`, category filter, 4-state legend) tucked cleanly into `⋮` |
| Compact Tasks & Habits (Light) | [07_cong-viec_light.png](docs/screenshots/ui-preview/07_cong-viec_light.png) · [08_thoi-quen_light.png](docs/screenshots/ui-preview/08_thoi-quen_light.png) | Unclipped `FlowRow` module/filter pills, 1-line Inbox bar, compact rows (`▶` Focus + `⋯`), daily habits |
| Unified Goals, Kanban & Eisenhower (Light) | [09_muc-tieu_light.png](docs/screenshots/ui-preview/09_muc-tieu_light.png) · [10_kanban_light.png](docs/screenshots/ui-preview/10_kanban_light.png) · [10b_ma-tran-eisenhower_light.png](docs/screenshots/ui-preview/10b_ma-tran-eisenhower_light.png) | `MIN`/`MAX` unified goals, wide opt-in Kanban columns, and `Q1–Q4` Eisenhower matrix |
| Focus Timer & Standalone Focus Pin (`id=910301`) | [11_tap-trung_light.png](docs/screenshots/ui-preview/11_tap-trung_light.png) · [12_ghim-focus-cho.png](docs/screenshots/ui-preview/12_ghim-focus-cho.png) · [13_ghim-focus-dang-do.png](docs/screenshots/ui-preview/13_ghim-focus-dang-do.png) | Dual Stopwatch/Pomodoro timer & independent ongoing Focus notification (idle & active) |
| Offline Quick Capture & Obsidian Export | [14_ghi-nhanh-offline_light.png](docs/screenshots/ui-preview/14_ghi-nhanh-offline_light.png) · [18_xuat-markdown_obsidian.png](docs/screenshots/ui-preview/18_xuat-markdown_obsidian.png) | Accentless duration parsing (`"Doc sach 20 phut"`) & local-only Obsidian Markdown export |
| 3-Layer 24h Activity Stats, Wally Data Story & Tools (Light) | [15_thong-ke-24h_light.png](docs/screenshots/ui-preview/15_thong-ke-24h_light.png) · [15b_thong-ke-wally-story_light.png](docs/screenshots/ui-preview/15b_thong-ke-wally-story_light.png) · [16_cong-cu_light.png](docs/screenshots/ui-preview/16_cong-cu_light.png) · [17_cai-dat_light.png](docs/screenshots/ui-preview/17_cai-dat_light.png) | Activity/Goal-tier stacked 24h bars, `"Đã ghi nhận: X / 24h"`, Tag & week-over-week breakdown, 4-step `Wally kể từ dữ liệu` + partial-week note, neat Tools hub & Settings |
| Week Schedule & Empty-Slot Selection (Dark) | [19_lich-trinh-tuan_dark.png](docs/screenshots/ui-preview/19_lich-trinh-tuan_dark.png) · [20_chon-gio-trong_dark.png](docs/screenshots/ui-preview/20_chon-gio-trong_dark.png) | Enlarged dark `T2–CN` columns & high-contrast selected slot action card (`Đã chọn 17:00–18:00`) |
| Task Subtasks → Live Focus & Task Done Dialog (Dark) | [21_cong-viec-subtasks_dark.png](docs/screenshots/ui-preview/21_cong-viec-subtasks_dark.png) · [22_tap-trung-task_dark.png](docs/screenshots/ui-preview/22_tap-trung-task_dark.png) · [23_da-xong-viec-dialog_dark.png](docs/screenshots/ui-preview/23_da-xong-viec-dialog_dark.png) | Inline subtasks, live Focus session with unclipped TopBar `time wallet. ● 00:02`, and dark AlertDialogs |
| Wally Pop-on Overlay & Offline Task Privacy Block (Dark) | [24_wally-overlay_dark.png](docs/screenshots/ui-preview/24_wally-overlay_dark.png) · [25_wally-chan-quyen-ai_dark.png](docs/screenshots/ui-preview/25_wally-chan-quyen-ai_dark.png) | Pop-on chat opened via floating bubble with wrapped `FlowRow` prompt pills & deterministic `Xếp Inbox` privacy block |
| Dark 3-Layer Stats, Wally Story, Settings, Dialogs & Opt-in Views | [26_thong-ke-24h_dark.png](docs/screenshots/ui-preview/26_thong-ke-24h_dark.png) · [26b_thong-ke-wally-story_dark.png](docs/screenshots/ui-preview/26b_thong-ke-wally-story_dark.png) · [27_cai-dat_dark.png](docs/screenshots/ui-preview/27_cai-dat_dark.png) · [28_nhap-json_dark.png](docs/screenshots/ui-preview/28_nhap-json_dark.png) · [29_xac-nhan-gio-dialog_dark.png](docs/screenshots/ui-preview/29_xac-nhan-gio-dialog_dark.png) · [30_thoi-quen_dark.png](docs/screenshots/ui-preview/30_thoi-quen_dark.png) · [31_muc-tieu_dark.png](docs/screenshots/ui-preview/31_muc-tieu_dark.png) · [32_kanban_dark.png](docs/screenshots/ui-preview/32_kanban_dark.png) · [33_ma-tran-eisenhower_dark.png](docs/screenshots/ui-preview/33_ma-tran-eisenhower_dark.png) | Complete Dark Mode token coverage across 3-Layer Statistics & Wally Data Story, Settings, Backup/Reconciliation dialogs, Habits, Goals, Kanban, and Eisenhower |

See capture details in [docs/screenshots/ui-preview/README.md](docs/screenshots/ui-preview/README.md) (baseline `v1.0.11` preserved in [docs/screenshots/ldplayer-android9/README.md](docs/screenshots/ldplayer-android9/README.md)).

</details>

---

## Verification & Quality Status (`v2.1.0` · `005378EF…`)

- **JVM & Contract Tests:** `653/655` PASS (`0` failed, `0` errors; 2 Live API suites `V110CoachLiveEvalTest#coachLiveEval` and `V120LiveAiEvalTest#liveChatAndRouteEval` skipped offline), including `WallyContextEngineTest` (Adaptive Context routing, permission zero-leak, TTL invalidation, 24h activity bucket conservation & 3-layer Stats contract).
- **Static Analysis & Packaging:** `lintRelease` reports **0 errors** (`60` warnings); Release APK (`9,058,921` bytes, SHA-256 `005378EFD99484A82BB3D22C8510BBD6B3D9285FD9843F748BBA74BAA4AB51D0`) and Debug APK (`13,452,888` bytes, SHA-256 `78A3455E6F0049777C0AC08B5CD7B53C471A3C98CEC97A5D03D9C7B68857CDDF`) signed and verified with `apksigner` (`39f5712d…`).
- **QA Emulator Validation (`WallyQA`):** Visually verified `9CEA64A0…` (identical UI source right prior to the `versionCode 18 → 19` bump to `v2.1.0`) on `WallyQA` (`390×844dp` @320dpi, causal `2026-11-22` synthetic dataset) for Day Schedule, 3-Layer `Thống kê` (Light + Dark, `Hoạt động | Mục tiêu` switch, `"Đã ghi nhận: X / 24h"`, Tag & `% vs tuần trước`, `0p / 1h` untagged hint, 4-step `Wally kể từ dữ liệu` + partial confidence note), draggable Pop-on bubble & `Trợ lý Wally` overlay + offline API-First guard with `0` crashes in `logcat`, alongside prior `v2.0.0` (`BEDEE190…` / `50EBDFFF…`) checks (`v1.2.0 → v2.0.0` upgrade, standalone Focus Pin `id=910301`, Week/Month scroll, `Task → Subtasks → Focus`). *(Live AI eval on Adaptive Context, cold-boot `BOOT_COMPLETED`, Android 13–16 AEHD, and Owner physical device remain `NOT_TESTED`.)*

---

## Technical Architecture & Repository Structure

- **Platform:** Android Native · Kotlin (`2.0.21`) · Jetpack Compose M3 · Room SQLite (`v7`, `MIGRATION_1_7`)
- **Current Release Candidate:** Wally `v2.1.0` (`versionCode 19`) — [WALLY_V210_CANDIDATE.md](WALLY_V210_CANDIDATE.md)
- **Previous Baselines:** [WALLY_V200_CANDIDATE.md](WALLY_V200_CANDIDATE.md) (`v2.0.0`) · [WALLY_V120_PRODUCTION_CANDIDATE.md](WALLY_V120_PRODUCTION_CANDIDATE.md) (`v1.2.0`)

<details>
<summary>Expand architecture layers, Room v7 schema, and directory tree</summary>

### Layer Map

| Layer | Key Modules | Responsibility |
|---|---|---|
| **UI (Compose M3)** | `MainActivity`, `ScheduleCalendarView`, `TasksHabitsScreen`, `TimerScreen`, `ChatScreen`, `InsightsScreen`, `ProfileSettingsScreen`, `OverviewScreen`, `HistoryScreen`, `ManualEntrySheet`, `DailyReviewScreen`, `ComponentsAndDialogs`, `DataManagementDialog`, `ConfirmationAndBreakDialogs`, `UiKit`, `Theme` | 4-tab calendar-first shell, `✦ Wally` pop-on bubble & overlay, Light/Dark `WalletPalette` tokens, inline task subtasks, Kanban/Eisenhower opt-in views, 3-layer 24h activity/goal analytics & Wally data story |
| **State & Coordinators** | `TimeWalletViewModel`, `WallyIntelligenceCoordinator`, `Wally20Coordinator`, `WallyChatRunner`, `WallyChatReducer`, `ManualEntryDraftCoordinator` | `TimerTickState` 1s slicing, adaptive context planning/conflict checking & Coach→Chat continuity, permission-aware AI digests & Inbox guards, draft persistence, Focus/Task linking, Room & notification coordination |
| **Domain (Pure Kotlin)** | `AdaptiveContextRouter`, `WallyContextEngine`, `TaskHabitEngine`, `FocusPinPolicy`, `MarkdownSummaryExporter`, `ScheduleGridEngine`, `ManualEntry`, `LedgerValidator`, `LedgerAnalyticsCalculator`, `LedgerQueryEngine`, `ConfirmationEngine`, `ActivityBreakEngine`, `BatchProposalPlanner`, `BudgetPolicy`, `TagRules`, `GoalRules`, `VietnameseChatParser`, `WallyAiGuards`, `CoachDigestBuilder`, `CoachGuard`, `Rx` | Pure JVM business rules: Method A adaptive routing, bounded goal-deficit/free-slot context, 3-layer activity buckets + week-over-week delta + 4-step Wally story, task/habit aggregation, Focus Pin policy, Obsidian Markdown, calendar 15-min math, `UC-15` handling, deterministic AI guards |
| **Data & System** | `TimeWalletDatabase` (Room v7, `MIGRATION_1_7`), `TaskHabitEntities`, `LedgerDao`, `BudgetDao`, `DailyReviewDao`, `TagGoalEntities`, `LedgerJsonBackup` (v1 & v2), `TimerAndSettingsStore`, `GeminiProposalClient`, `CompletionReminder` | Atomic SQLite transactions, lossless JSON backup (`MERGE_SAFE` / `REPLACE_ALL`), AES-GCM Keystore API secret vault, `AlarmManager`, Focus Pin `NotificationManager` |

### Repository Structure

```text
Time_Wallet/
├── .agents/                        # Project rules & QA environment safeguards
├── Time_Wallet_MVP/                # MVP specification & original HTML prototype
├── ai_test/                        # Gateway test script (reads FOZA_API_KEY env var)
├── docs/
│   ├── archive/                    # Historical dossiers, reviews & QA records (v1.0.5–v1.0.13)
│   ├── brand/                      # Icon Design System v3 SVG & Wally WebP showcase
│   └── screenshots/                # v2.1 / v2.0 Light/Dark UI previews & v1.0.11 baseline
├── promo-video/                    # HyperFrames product intro video project
├── app/
│   ├── build.gradle.kts            # Kotlin, Compose, Room schema export, external Release signing
│   ├── schemas/                    # Exported Room DB schemas (v4, v5, v6, v7)
│   └── src/
│       ├── main/java/com/timewallet/mvp/
│       │   ├── MainActivity.kt
│       │   ├── data/               # AI client, Diagnostics, Room DAO/Entities (v7), Backup JSON v2, Settings
│       │   ├── domain/             # Pure Kotlin models & engines (AdaptiveContext, Analytics 3-layer, Task/Habit, Guards)
│       │   ├── notifications/      # Focus Pin & AlarmManager CompletionReminder + CompletionBootReceiver
│       │   └── ui/                 # TimeWalletViewModel, WallyIntelligenceCoordinator, Wally20Coordinator, Screens, Theme
│       └── test/java/com/timewallet/mvp/  # JVM unit & static contract test suites (655 test cases)
├── WALLY_TECHNICAL_SPEC.md         # Detailed technical spec & Mermaid diagrams
├── WALLY_V210_CANDIDATE.md         # v2.1.0 Candidate handoff, SHA-256 & Acceptance Checklist
├── WALLY_V200_CANDIDATE.md         # v2.0.0 baseline handoff & Acceptance Checklist
├── WALLY_V120_PRODUCTION_CANDIDATE.md  # v1.2.0 historical baseline handoff
└── CONTEXT_MANIFEST.md
```

</details>

---

## Build, Test & Documentation

1. **Android Studio:** Open `Time_Wallet`, wait for Gradle sync, and press **Run (`Shift + F10`)**.
2. **Run JVM unit tests, Android Lint, and build Debug APK:**
   ```powershell
   .\gradlew.bat testDebugUnitTest lintRelease assembleDebug
   ```
3. **Build personally signed Release APK** *(keystore config stays outside Git at `%USERPROFILE%\.timewallet\keystore.properties` or via `TIMEWALLET_KEYSTORE_*` env vars)*:
   ```powershell
   .\gradlew.bat assembleRelease
   ```
4. **Documentation:**
   - [Candidate `v2.1.0` Handoff & Acceptance Matrix](WALLY_V210_CANDIDATE.md)
   - [Baseline `v2.0.0` Candidate Handoff](WALLY_V200_CANDIDATE.md)
   - [Historical `v1.2.0` Production Candidate](WALLY_V120_PRODUCTION_CANDIDATE.md)
   - [Technical Specification](WALLY_TECHNICAL_SPEC.md)
   - [MVP Specification](Time_Wallet_MVP/Time_Wallet_MVP_Spec.md)
   - [Owner Device Self-Check Checklist](WALLY_OWNER_CHECKLIST_V110.md)
