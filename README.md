# Wally — Time Wallet (`v2.1.1`)

**Trung tâm Quản lý Thời gian & Năng suất Cá nhân · Riêng tư tại máy · AI đề xuất có kiểm soát**  
*Your time. Your choices. Your life.*

<p align="left">
  <img src="https://img.shields.io/badge/Version-v2.1.1%20(code%2020)-1F8066?style=flat-square" alt="Version v2.1.1" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.0.21" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Android-API%2026%E2%80%9335-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android API 26-35" />
  <img src="https://img.shields.io/badge/Room%20SQLite-Schema%20v7-00838F?style=flat-square" alt="Room Schema v7" />
  <img src="https://img.shields.io/badge/JVM%20Tests-665%2F669%20PASS-2E7D32?style=flat-square" alt="Unit Tests" />
  <img src="https://img.shields.io/badge/Lint%20Release-0%20errors-2E7D32?style=flat-square" alt="Lint Release" />
</p>

<table>
  <tr>
    <td width="136" align="center" valign="middle">
      <img src="docs/brand/wally-hero.webp" alt="Wally — Personal Time Companion" width="116" />
    </td>
    <td valign="middle">
      <p><b>Ai cũng chỉ có 24 giờ mỗi ngày — điều quan trọng không phải là nhồi nhét cho kín lịch, mà là nhìn rõ thời gian thực sự đi đâu để sống chủ động hơn.</b></p>
      <p><b>Time Wallet (Wally 2.0)</b> kết nối lịch trình, công việc, thói quen, mục tiêu và các phiên tập trung vào một không gian Android bản địa — gọn gàng, chạy mượt khi ngoại tuyến, tôn trọng sự nghỉ ngơi và luôn giữ dữ liệu thuộc toàn quyền sở hữu của bạn.</p>
      <p><i><b>English summary:</b> Wally 2.0 is a local-first Android productivity &amp; time hub unifying Calendar, Tasks &amp; Subtasks, Habits, Goals, Focus Pin, 24h analytics, and permission-scoped AI assistance — built for clarity without guilt, fake productivity scores, or lock-in.</i></p>
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

| Vấn đề thường gặp ở app truyền thống | Cách Wally `v2.1.1` giải quyết |
|---|---|
| Bắt ghi quá nhiều nơi: app lịch riêng, to-do riêng, bấm giờ riêng → ngại mở hoặc nhập trùng | **Một vòng khép kín tự nhiên:** từ *Inbox / Việc cần làm* đẩy lên *Lịch*, bấm `▶` đo ngay kèm checklist *Subtasks*, và tự khớp hoàn thành *Thói quen* nếu đúng Tag & đủ phút. |
| Gộp chung dự kiến với thực tế, hoặc tự đoán khung giờ cho ghi nhận rời rạc | **Trung thực tuyệt đối với dữ liệu 24h:** tách rõ *Đã đo (`MEASURED`)*, *Có mốc giờ (`REPORTED_TIMED`)*, *Chỉ thời lượng (`Chưa gắn giờ`)* và *Kế hoạch (`PLANNED`)*. |
| Chấm điểm năng suất gây áp lực khi nghỉ ngơi | **Nhìn rõ không phán xét:** phân 4 tầng *Mục tiêu · Phi mục tiêu · Lãng phí (chỉ khi bạn tự đánh dấu) · Chưa rõ*, tách nghỉ ngơi hợp lý khỏi lãng phí. |
| Phụ thuộc cloud, đòi nhiều quyền hệ thống, khó biết AI đang đọc gì | **Local-first & Privacy-first:** chỉ xin 4 quyền Android tối thiểu (`INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_INSTALL_PACKAGES` cho cài đặt bản cập nhật có xác thực từ GitHub Releases), bật/tắt chi tiết **4 phạm vi đọc của AI**, khóa API mã hóa AES-GCM trong Android Keystore và không xuất ra sao lưu. |

---

## Key Capabilities in `v2.1.1`

- **Lịch trình là trung tâm (`Lịch trình · Ngày / 3 ngày / Tuần / Tháng`):** Thiết kế *Timeline-first* gom thống kê 24h (`1h 30 / 24h ▾`) vào hộp thoại bật nhanh, giữ thanh **`Chưa giờ (N)` & `+` ghim cố định ở hàng điều khiển**, thanh công cụ ngữ cảnh neo nổi đối diện khối/vùng đang chọn & chừa vùng trống cho bóng Wally (không đẩy/co lưới), chạm thẻ kéo dời trực tiếp hoặc kéo mép trên/dưới qua bản nháp **`✓ Lưu / Hủy`** kèm cảnh báo trùng giờ và chuẩn `21:00–24:00`; giữ đối chiếu **Kế hoạch vs Thực tế**, xử lý vắt nửa đêm (`UC-15`) và bộ lọc/chú giải trong menu `⋮`.
- **Việc cần làm, Subtasks, Thói quen & Mục tiêu hợp nhất:** Chụp nhanh ý tưởng vào *Inbox*, quản lý *Priority (`P1–P3`)*, mở rộng *Subtasks* ngay trên thẻ, theo dõi chuỗi *Thói quen* 7 ngày (`T2–CN`) tự khớp từ sổ cái (`⚡`), và gộp *Mục tiêu tối thiểu (`MIN`)* cùng *Ngân sách giới hạn (`MAX`)* theo chu kỳ. Bật thêm *Kanban* hoặc *Ma trận Eisenhower* trong Cài đặt nếu cần.
- **Tập trung thực chiến & Ghim thông báo (`Focus Pin`):** Chuyển đổi nhanh giữa *Đếm xuôi*, *Pomodoro 25p* và *Sâu 45p*; đo từ một *Task* sẽ mang theo mục tiêu phút và checklist *Subtasks* xuống ngay dưới đồng hồ. Bật **Ghim thông báo tập trung (`id = 910301`)** để giữ phiên đang đo, kế hoạch hiện tại hoặc gợi ý theo lịch sử (`tuần trước / hôm qua / 3 ngày gần đây`) ngay trên thanh thông báo kèm nút `Bỏ ghim`.
- **Trợ lý `✦ Wally` (`Wally Pro / Wally Flash`), Pop-on & Ngữ cảnh thích ứng (Method A):** Mở nhanh từ mọi tab qua **bóng chat Pop-on thu gọn (`48.dp`) kéo thả tự hít mép** hoặc nút `✦ Wally` trên thanh trên cùng (`WallyAiOverlay`). Chọn trực tiếp giữa **`Wally Pro`** (`Claude Sonnet 4.6`, mặc định ưu tiên chất lượng `95.8%`) và **`Wally Flash`** (`Claude Haiku 4.5`, tùy chọn phản hồi nhanh `~2.35s`) với tên model thực tế hiển thị rõ bên dưới (`Auto` tạm khóa chờ đạt ngưỡng router). `AdaptiveContextRouter` & `WallyContextEngine` chỉ đính kèm mục tiêu thiếu hụt, lịch trống và hội thoại nối tiếp trên các câu hỏi tư vấn kế hoạch (tuân thủ nghiêm `AiReadPermissions` và luôn chờ xác nhận trước khi lưu).
- **Thống kê 3 lớp & Wally kể từ dữ liệu (`Mở rộng › Thống kê`):** Mặc định xem biểu đồ 24h × 7 ngày theo **5 nhóm hoạt động thực tế** (`Ngủ, nghỉ · Công việc · Học tập · Sức khỏe · Giải trí & Khác`) tách bạch với `Chưa ghi nhận` và `Chưa gắn giờ`, kèm chuyển nhanh góc nhìn **4 tầng Mục tiêu** (`Hoạt động | Mục tiêu`), chạm ngày xem chi tiết Tag/hoạt động + `% so với tuần trước`, giải thích tự động khi mục tiêu `0p` do thiếu gắn Tag, và thẻ **Wally kể từ dữ liệu** 4 bước (`Điều gì xảy ra → Đáng chú ý → Ảnh hưởng mục tiêu → Có thể làm tiếp`) có ghi chú thận trọng khi dữ liệu chưa đủ.
- **Cập nhật ứng dụng qua GitHub Releases & Tùy biến giao diện:** Hỗ trợ kiểm tra bản phát hành mới trực tiếp từ GitHub Releases (kênh phân phối chính khi chưa phát hành Google Play), xác thực 3 lớp (HTTPS origin chính thức + `SHA-256` + chữ ký APK) trước khi mở trình cài đặt mà không làm mất dữ liệu; chuyển nhanh chủ đề *Theo máy / Sáng / Tối*, **Cỡ chữ hiển thị (`Nhỏ / Vừa / To`)**, bật tắt từng module, quy tắc phân loại offline, xuất/nhập **JSON `schemaVersion = 2`** và xuất **Markdown** Obsidian.

---

## UI Preview (`v2.0.0` · Light & Dark Mode)

<p align="center">
  <img src="docs/screenshots/ui-preview/_tong-hop_wally-2.0.jpg" alt="Time Wallet v2.0.0 — Light & Dark 5-Screen Overview" width="960" />
</p>

<p align="center">
  <sub><i>Gallery covers 36 screenshots (plus 5-panel overview banner) captured on the isolated <code>WallyQA</code> emulator (Android 9, <code>780×1688 @320dpi</code>) using synthetic QA data: refreshed Day Schedule, 3-Layer Activity/Wally Story Stats &amp; Wally Overlay captures (<code>01</code>, <code>15</code>, <code>15b</code>, <code>24</code>, <code>26</code>, <code>26b</code>, banner) come directly from the latest signed Release APK <code>9CEA64A0C9C1…</code> (<code>v2.0.0</code> · <code>versionCode 18</code>), alongside unchanged screens from prior <code>BEDEE1904C58…</code> / <code>50EBDFFF…</code> baseline checks. Owner personal instance (<code>index 0</code>), live AI eval on <code>v2.0.0</code> / Adaptive Context, Android 13–16 AEHD, cold-boot, and physical device remain untouched / <code>NOT_TESTED</code>.</i></sub>
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

## Verification & Quality Status (`v2.1.1` · `9D61CE31…`)

- **JVM & Contract Tests:** **`665/669` PASS** (`0` failures, `0` errors across `75` files; 4 Live AI suites `V110CoachLiveEvalTest`, `V120LiveAiEvalTest`, `V210HaikuVsSonnetLiveEvalTest`, `V210HybridRoutingEvalTest` skipped offline), including `V210ScheduleInteractionTest` (`5/5`), `V210AutoUpdateTest` (`5/5`), `V210WallyModelModeTest` (`3/3`), `WallyContextEngineTest` (`6/6`), `WallyOriginBindingTest` (`11/11`), `WallySecurityStaticTest` (`5/5`), and `V106UiContractTest`.
- **Static Analysis & Packaging:** `lintRelease` reports **0 errors** (`64` warnings) and `lintDebug` reports **0 errors** (`74` warnings); Release APK (`9,190,548` bytes, SHA-256 `9D61CE313DAF755AF175B005C267DA871CA01DE317477370A897656896C769DA`) and Debug APK (`15,154,928` bytes, SHA-256 `575A538291FE08C1D0CB94FC817575C9F1AEE611D5C85DA0811F9848A4590F17`) verified with `apksigner` (`39f5712d…`).
- **Controlled AI Benchmark (`24×2 = 48` Live Foza Calls):** `Wally Pro` (`cinb/claude-sonnet-4-6`) reached **`23/24` (`95.8%`, `6.78s` avg, `0` safety violations)** vs `Wally Flash` (`hoang/claude-haiku-4.5`) **`19/24` (`79.2%`, `2.35s` avg, `0` safety violations)** vs `Hybrid` **`20/24` (`83.3%`, `p95 = 10.67s`)** — establishing `Pro` as default, `Flash` as user-selectable speed mode, and `Auto` locked pending router quality.
- **QA Emulator Validation (`WallyQA`):** Prior `v2.0.0` baseline (`9CEA64A0…` / `BEDEE190…`) captured all `36` Light/Dark gallery screens cleanly. On final `v2.1.1` Release APK `9D61CE31…` installed over `v2.1.0`, synthetic QA verified all Timeline-first views: Day view open & `0px` jump selection, direct drag draft, compact 2-line `Tuần` (`T6 · 20/11 | T7 · 21/11 | CN · 22/11`) at `40.dp/hr` (`10+` hours visible without edge clipping or bottom-bar truncation), crisp 3-column `3 ngày` view (`T7 · 21/11 | CN · 22/11 | T2 · 23/11`) with top-docked overlay below header strip, and bottom floating bars clear of `BottomBar` & Wally bubble (`WallyQA` cleanly shut down afterward). `Tháng` & prolonged touch-edge drag/auto-scroll captures on `9D61CE31…`, full E2E in-app download/install from GitHub Releases `v2.1.1`, cold-boot `BOOT_COMPLETED`, Android 13–16 AEHD per Rule 14, and Owner physical device remain `NOT_TESTED`.

---

## Technical Architecture & Repository Structure

- **Platform:** Android Native · Kotlin (`2.0.21`) · Jetpack Compose M3 · Room SQLite (`v7`, `MIGRATION_1_7`)
- **Current Release (`Latest`):** Wally `v2.1.1` (`versionCode 20`, multi-day timeline polish) · baseline feature handoff [WALLY_V210_CANDIDATE.md](WALLY_V210_CANDIDATE.md) (`v2.1.0`, `versionCode 19`)
- **Previous Baselines:** [WALLY_V200_CANDIDATE.md](WALLY_V200_CANDIDATE.md) · [WALLY_V120_PRODUCTION_CANDIDATE.md](WALLY_V120_PRODUCTION_CANDIDATE.md)

<details>
<summary>Expand architecture layers, Room v7 schema, and directory tree</summary>

### Layer Map

| Layer | Key Modules | Responsibility |
|---|---|---|
| **UI (Compose M3)** | `MainActivity`, `ScheduleCalendarView`, `TasksHabitsScreen`, `TimerScreen`, `ChatScreen`, `InsightsScreen`, `ProfileSettingsScreen`, `AppUpdateDialog`, `OverviewScreen`, `HistoryScreen`, `ManualEntrySheet`, `DailyReviewScreen`, `ComponentsAndDialogs`, `DataManagementDialog`, `ConfirmationAndBreakDialogs`, `UiKit`, `Theme` | 4-tab calendar-first shell, `✦ Wally` pop-on bubble & overlay with `Wally Pro / Flash` selector, Light/Dark tokens + `Nhỏ / Vừa / To` font scaling, inline subtasks, Kanban/Eisenhower opt-in views, 3-layer 24h analytics & in-app GitHub Release update dialog |
| **State & Coordinators** | `TimeWalletViewModel`, `AppUpdateCoordinator`, `WallyIntelligenceCoordinator`, `Wally20Coordinator`, `WallyChatRunner`, `WallyChatReducer`, `ManualEntryDraftCoordinator` | `TimerTickState` 1s slicing, GitHub release check/download state machine, adaptive context planning/conflict checking & Coach→Chat continuity, permission-aware AI digests & Inbox guards, draft persistence, Focus/Task linking |
| **Domain (Pure Kotlin)** | `AppUpdateContract`, `AdaptiveContextRouter`, `WallyContextEngine`, `TaskHabitEngine`, `FocusPinPolicy`, `MarkdownSummaryExporter`, `ScheduleGridEngine`, `ManualEntry`, `LedgerValidator`, `LedgerAnalyticsCalculator`, `LedgerQueryEngine`, `ConfirmationEngine`, `ActivityBreakEngine`, `BatchProposalPlanner`, `BudgetPolicy`, `TagRules`, `GoalRules`, `VietnameseChatParser`, `WallyAiGuards`, `CoachDigestBuilder`, `CoachGuard`, `Rx` | Pure JVM business rules: `AppUpdateChannel` + 3-layer GitHub release trust policy, Method A adaptive routing, bounded goal-deficit/free-slot context, 3-layer activity buckets + week-over-week delta + 4-step Wally story, task/habit aggregation, Focus Pin policy, Obsidian Markdown, calendar 15-min math, `UC-15` handling, deterministic AI guards |
| **Data & System** | `TimeWalletDatabase` (Room v7, `MIGRATION_1_7`), `GitHubAppUpdater`, `TaskHabitEntities`, `LedgerDao`, `BudgetDao`, `DailyReviewDao`, `TagGoalEntities`, `LedgerJsonBackup` (v1 & v2), `TimerAndSettingsStore`, `GeminiProposalClient`, `CompletionReminder` | Atomic SQLite transactions, GitHub REST release/SHA-256 download + APK cert verification + `FileProvider` install launcher, lossless JSON backup (`MERGE_SAFE` / `REPLACE_ALL`), AES-GCM Keystore API vault + `WallyModelPreset`, `AlarmManager`, Focus Pin `NotificationManager` |

### Repository Structure

```text
Time_Wallet/
├── .agents/                        # Project rules & QA environment safeguards
├── Time_Wallet_MVP/                # MVP specification & original HTML prototype
├── ai_test/                        # Gateway test script (reads FOZA_API_KEY env var)
├── docs/
│   ├── archive/                    # Historical dossiers, reviews & QA records (v1.0.5–v1.0.13)
│   ├── brand/                      # Icon Design System v3 SVG & Wally WebP showcase
│   └── screenshots/                # v2.0.0 Light/Dark UI previews & v1.0.11 baseline
├── promo-video/                    # HyperFrames product intro video project
├── app/
│   ├── build.gradle.kts            # Kotlin, Compose, Room schema export, external Release signing
│   ├── schemas/                    # Exported Room DB schemas (v4, v5, v6, v7)
│   └── src/
│       ├── main/java/com/timewallet/mvp/
│       │   ├── MainActivity.kt
│       │   ├── data/               # AI client, GitHub Updater, Diagnostics, Room DAO/Entities (v7), Backup JSON v2, Settings
│       │   ├── domain/             # Pure Kotlin models & engines (UpdateContract, Task/Habit, FocusPin, Calendar, Analytics, Guards)
│       │   ├── notifications/      # Focus Pin & AlarmManager CompletionReminder + CompletionBootReceiver
│       │   └── ui/                 # TimeWalletViewModel, AppUpdateCoordinator, WallyIntelligenceCoordinator, Screens, Theme, UiKit
│       └── test/java/com/timewallet/mvp/  # JVM unit & static contract test suites (669 test cases)
├── WALLY_TECHNICAL_SPEC.md         # Detailed technical spec & Mermaid diagrams
├── WALLY_V210_CANDIDATE.md         # v2.1.0 Candidate handoff, SHA-256, Benchmark & Product Backlog
├── WALLY_V200_CANDIDATE.md         # v2.0.0 Release handoff baseline
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
   - [Candidate `v2.1.0` Handoff, Auto-Update, Pro/Flash Benchmark & Backlog](WALLY_V210_CANDIDATE.md)
   - [Baseline `v2.0.0` Handoff & Acceptance Matrix](WALLY_V200_CANDIDATE.md)
   - [Historical `v1.2.0` Production Candidate](WALLY_V120_PRODUCTION_CANDIDATE.md)
   - [Technical Specification](WALLY_TECHNICAL_SPEC.md)
   - [MVP Specification](Time_Wallet_MVP/Time_Wallet_MVP_Spec.md)
   - [Owner Device Self-Check Checklist](WALLY_OWNER_CHECKLIST_V110.md)
