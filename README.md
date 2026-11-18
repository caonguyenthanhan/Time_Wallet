# Wally — Time Wallet (`v2.0.0`)

*Your time. Your choices. Your life.*

<p align="left">
  <img src="https://img.shields.io/badge/Version-v2.0.0%20(code%2018)-1F8066?style=flat-square" alt="Version v2.0.0" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.0.21" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Android-API%2026%E2%80%9335-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android API 26-35" />
  <img src="https://img.shields.io/badge/Room%20SQLite-Schema%20v7-00838F?style=flat-square" alt="Room Schema v7" />
  <img src="https://img.shields.io/badge/JVM%20Tests-646%2F648%20PASS-2E7D32?style=flat-square" alt="Unit Tests" />
  <img src="https://img.shields.io/badge/Lint%20Release-0%20errors-2E7D32?style=flat-square" alt="Lint Release" />
</p>

<table>
  <tr>
    <td width="136" align="center" valign="middle">
      <img src="docs/brand/wally-hero.webp" alt="Wally — Personal Time Companion" width="116" />
    </td>
    <td valign="middle">
      <p><b>Private Personal Productivity Hub — built around local-first privacy, clarity, and intentional living.</b></p>
      <p>Time is more than a number. We all have 24 hours a day — what matters is connecting your daily schedule, tasks, habits, and focus sessions with what genuinely matters to you, without guilt or fake productivity scores.</p>
      <p><b>Wally 2.0</b> brings Calendar, Tasks &amp; Subtasks, Habits, Unified Goals, Focus Pin, 24h Intelligence, Light &amp; Dark theme tokens, and a permission-filtered <code>✦ Wally</code> AI Overlay into one cohesive 4-tab Android experience.</p>
    </td>
  </tr>
</table>

---

## Product Intro

<!-- YOUTUBE_EMBED -->

---

## What's New in Wally `v2.0.0`

- **Calendar-First 4-Tab Shell (`Lịch trình · Việc & Thói quen · Tập trung · Mở rộng`):** Integrates a collapsible 1-line 24h Wallet strip into Schedule, moves secondary calendar filters/legends and quick-add into `⋮`, and replaces obstructing floating buttons with a compact `✦ Wally` TopBar overlay trigger.
- **Tasks, Subtasks, Habits & Unified Goals (`Room v7`):** Manage Inbox, priority tasks (`P1–P3`), inline checklist subtasks, Habit auto-matching from confirmed Ledger entries (`⚡`), and unified `MIN`/`MAX` Goals (lossless `MIGRATION_6_7` from `1.x` `CategoryBudget`), plus opt-in **Kanban** and **Eisenhower Matrix** modules.
- **Standalone Focus Pin Notification (`id = 910301`):** Keeps only the 3 minimal Android permissions (`INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`) and pins live session progress or historical activity suggestions directly to the notification shade with a one-tap `Bỏ ghim` action.
- **Full Light & Dark Theme Tokenization + Clean Settings Split:** Centralized `WalletPalette` semantic tokens across screens and dialogs, cleanly separating **Mở rộng › Công cụ** from **⚙ Hồ sơ & Cài đặt** (Theme, Module toggles, Offline Classification Rules, AI Read Permission Matrix, JSON Backup).
- **Zero-Leak AI Permissions, JSON v2 & Obsidian Markdown Export:** Independent read toggles (`Sổ cái · Công việc · Thói quen · Mục tiêu`) enforced on Coach digest, Inbox replanning, and Task breakdown; lossless `schemaVersion = 2` JSON backup & Obsidian Markdown daily export without ever leaking API keys.

---

## Architecture Highlights

- **Platform:** Android Native · Kotlin · Jetpack Compose M3 (`com.timewallet.mvp`)
- **Current Release Candidate:** Wally `v2.0.0` (`versionCode 18`) — see [WALLY_V200_CANDIDATE.md](WALLY_V200_CANDIDATE.md)
- **Previous `1.x` Baseline:** [WALLY_V120_PRODUCTION_CANDIDATE.md](WALLY_V120_PRODUCTION_CANDIDATE.md)

---

## UI Preview (`v2.0.0` · Light & Dark Mode)

<p align="center">
  <img src="docs/screenshots/ui-preview/_tong-hop_wally-2.0.jpg" alt="Time Wallet v2.0.0 — Light & Dark 5-Screen Overview" width="960" />
</p>

<p align="center">
  <sub><i>Captured directly from Release APK <code>50EBDFFF…</code> (<code>v2.0.0</code> · <code>versionCode 18</code>) and preceding <code>v2.0.0</code> UX-wave builds on the isolated <code>WallyQA</code> emulator (Android 9, <code>780×1688 @320dpi</code>) using 1-week synthetic mock data, <code>v1.2.0 → v2.0.0</code> in-place migration, Focus Pin <code>id=910301</code>, and offline AI privacy checks (Owner personal instance <code>index 0</code> untouched; live AI API eval on <code>v2.0.0</code>, Android 13–16 AEHD, cold-boot, and physical device remain <code>NOT_TESTED</code>).</i></sub>
</p>

<details>
<summary>Browse all 34 Light &amp; Dark screen captures (<code>docs/screenshots/ui-preview/</code>)</summary>

| Screen / Flow | Capture | Highlights |
|---|---|---|
| Day Schedule (Today · Light) | [01_lich-trinh-hom-nay_light.png](docs/screenshots/ui-preview/01_lich-trinh-hom-nay_light.png) | Collapsible 1-line 24h Wallet strip, full unclipped date header, timeline-first layout |
| Day Schedule (`18/11` · `Hôm nay` Pill) | [02_lich-trinh-ngay_light.png](docs/screenshots/ui-preview/02_lich-trinh-ngay_light.png) | Clean `Hôm nay` shortcut pill, `UC-15` overnight blocks, separated `Chưa gắn giờ` tray |
| 3-Day / Week / Month Schedule | [03_lich-trinh-3ngay_light.png](docs/screenshots/ui-preview/03_lich-trinh-3ngay_light.png) · [04_lich-trinh-tuan_light.png](docs/screenshots/ui-preview/04_lich-trinh-tuan_light.png) · [05_lich-trinh-thang_light.png](docs/screenshots/ui-preview/05_lich-trinh-thang_light.png) | Side-by-side 3-day grid, 7-column `T2–CN` week grid, and monthly dot/planned overview |
| Calendar `⋮` Menu | [06_lich-trinh-menu_light.png](docs/screenshots/ui-preview/06_lich-trinh-menu_light.png) | Secondary controls (`＋ Ghi mới hoạt động`, category filter, 4-state legend) tucked cleanly into `⋮` |
| Compact Tasks & Habits (Light) | [07_cong-viec_light.png](docs/screenshots/ui-preview/07_cong-viec_light.png) · [08_thoi-quen_light.png](docs/screenshots/ui-preview/08_thoi-quen_light.png) | 1-line Inbox bar, compact rows (`▶` Focus + `⋯`), daily habit summary, `T5–T4` dots & suggestions |
| Unified Goals, Kanban & Eisenhower (Light) | [09_muc-tieu_light.png](docs/screenshots/ui-preview/09_muc-tieu_light.png) · [10_kanban_light.png](docs/screenshots/ui-preview/10_kanban_light.png) · [10b_ma-tran-eisenhower_light.png](docs/screenshots/ui-preview/10b_ma-tran-eisenhower_light.png) | `MIN`/`MAX` unified goals, opt-in Kanban columns, and `Q1–Q4` Eisenhower matrix |
| Focus Timer & Standalone Focus Pin (`id=910301`) | [11_tap-trung_light.png](docs/screenshots/ui-preview/11_tap-trung_light.png) · [12_ghim-focus-cho.png](docs/screenshots/ui-preview/12_ghim-focus-cho.png) · [13_ghim-focus-dang-do.png](docs/screenshots/ui-preview/13_ghim-focus-dang-do.png) | Dual Stopwatch/Pomodoro timer & independent ongoing Focus notification (idle & active) |
| Offline Quick Capture & Obsidian Export | [14_ghi-nhanh-offline_light.png](docs/screenshots/ui-preview/14_ghi-nhanh-offline_light.png) · [18_xuat-markdown_obsidian.png](docs/screenshots/ui-preview/18_xuat-markdown_obsidian.png) | Accentless duration parsing (`"Doc sach 20 phut"`) & local-only Obsidian Markdown export |
| 24h Stats, Tools & Profile Settings (Light) | [15_thong-ke-24h_light.png](docs/screenshots/ui-preview/15_thong-ke-24h_light.png) · [16_cong-cu_light.png](docs/screenshots/ui-preview/16_cong-cu_light.png) · [17_cai-dat_light.png](docs/screenshots/ui-preview/17_cai-dat_light.png) | 4-tier 24h stacked bars, Tools hub separated from Settings (`Theme`, `AI Scope`, `Local Rules`) |
| Week Schedule & Empty-Slot Selection (Dark) | [19_lich-trinh-tuan_dark.png](docs/screenshots/ui-preview/19_lich-trinh-tuan_dark.png) · [20_chon-gio-trong_dark.png](docs/screenshots/ui-preview/20_chon-gio-trong_dark.png) | Crisp dark surfaces & high-contrast selected slot action card (`Bỏ` / `+ Tạo sự kiện`) |
| Task Subtasks → Live Focus & Task Done Dialog (Dark) | [21_cong-viec-subtasks_dark.png](docs/screenshots/ui-preview/21_cong-viec-subtasks_dark.png) · [22_tap-trung-task_dark.png](docs/screenshots/ui-preview/22_tap-trung-task_dark.png) · [23_da-xong-viec-dialog_dark.png](docs/screenshots/ui-preview/23_da-xong-viec-dialog_dark.png) | Inline subtasks, live Focus session synced with Task subtasks, and dark AlertDialogs |
| `✦ Wally` Overlay & Offline Task Privacy Block (Dark) | [24_wally-overlay_dark.png](docs/screenshots/ui-preview/24_wally-overlay_dark.png) · [25_wally-chan-quyen-ai_dark.png](docs/screenshots/ui-preview/25_wally-chan-quyen-ai_dark.png) | Non-duplicated prompt bar & deterministic `Xếp Inbox` block when `Công việc & Inbox` scope is OFF |
| Dark Stats, Settings, Dialogs & Opt-in Views | [26_thong-ke-24h_dark.png](docs/screenshots/ui-preview/26_thong-ke-24h_dark.png) · [27_cai-dat_dark.png](docs/screenshots/ui-preview/27_cai-dat_dark.png) · [28_nhap-json_dark.png](docs/screenshots/ui-preview/28_nhap-json_dark.png) · [29_xac-nhan-gio-dialog_dark.png](docs/screenshots/ui-preview/29_xac-nhan-gio-dialog_dark.png) · [30_thoi-quen_dark.png](docs/screenshots/ui-preview/30_thoi-quen_dark.png) · [31_muc-tieu_dark.png](docs/screenshots/ui-preview/31_muc-tieu_dark.png) · [32_kanban_dark.png](docs/screenshots/ui-preview/32_kanban_dark.png) · [33_ma-tran-eisenhower_dark.png](docs/screenshots/ui-preview/33_ma-tran-eisenhower_dark.png) | Complete Dark Mode token coverage across Statistics, Settings, Backup/Reconciliation dialogs, Habits, Goals, Kanban, and Eisenhower |

See capture details in [docs/screenshots/ui-preview/README.md](docs/screenshots/ui-preview/README.md) (baseline `v1.0.11` preserved in [docs/screenshots/ldplayer-android9/README.md](docs/screenshots/ldplayer-android9/README.md)).

</details>

---

## Verification & Quality Status (`v2.0.0` · `50EBDFFF…`)

- **JVM & Contract Tests:** `646/648` PASS (`0` failed, `0` errors; 2 Live API suites `V110CoachLiveEvalTest#coachLiveEval` and `V120LiveAiEvalTest#liveChatAndRouteEval` skipped offline).
- **Static Analysis & Packaging:** `lintRelease` reports **0 errors** (`60` warnings); Release APK (`8,993,385` bytes, SHA-256 `50EBDFFF…`) and Debug APK (`14,541,978` bytes, SHA-256 `D7FC3B60…`) signed and verified with `apksigner`.
- **QA Emulator Validation (`WallyQA`):** Tested in-place `v1.2.0 → v2.0.0` upgrade preserving `2h 15p` sample data with zero crashes, standalone Focus Pin notification (`id=910301`), Dark Mode dialog/surface contrast, AI Task read-permission enforcement, `Task → Subtasks → Focus`, and `schemaVersion = 2` JSON export with zero secret leakage (`FATAL EXCEPTION = 0`). *(Live API eval on `v2.0.0`, cold-boot `BOOT_COMPLETED`, Android 13–16 AEHD, and Owner physical device remain `NOT_TESTED`.)*

---

## Technical Architecture & Repository Structure

<details>
<summary>Expand architecture layers, Room v7 schema, and directory tree</summary>

### Layer Map

| Layer | Key Modules | Responsibility |
|---|---|---|
| **UI (Compose M3)** | `MainActivity`, `ScheduleCalendarView`, `TasksHabitsScreen`, `TimerScreen`, `ChatScreen`, `InsightsScreen`, `ProfileSettingsScreen`, `OverviewScreen`, `HistoryScreen`, `ManualEntrySheet`, `DailyReviewScreen`, `ComponentsAndDialogs`, `DataManagementDialog`, `ConfirmationAndBreakDialogs`, `UiKit`, `Theme` | 4-tab calendar-first shell, `✦ Wally` pop-on overlay, Light/Dark `WalletPalette` tokens, inline task subtasks, Kanban/Eisenhower opt-in views, 24h 4-tier analytics |
| **State & Coordinators** | `TimeWalletViewModel`, `Wally20Coordinator`, `WallyChatRunner`, `WallyChatReducer`, `ManualEntryDraftCoordinator` | Permission-aware AI digests & Inbox guards, draft persistence, Focus/Task linking, Room & notification coordination |
| **Domain (Pure Kotlin)** | `TaskHabitEngine`, `FocusPinPolicy`, `MarkdownSummaryExporter`, `ScheduleGridEngine`, `ManualEntry`, `LedgerValidator`, `LedgerAnalyticsCalculator`, `LedgerQueryEngine`, `ConfirmationEngine`, `ActivityBreakEngine`, `BatchProposalPlanner`, `BudgetPolicy`, `TagRules`, `GoalRules`, `VietnameseChatParser`, `WallyAiGuards`, `CoachDigestBuilder`, `CoachGuard`, `Rx` | Pure JVM business rules: task/habit aggregation, Focus Pin policy, Obsidian Markdown formatting, calendar 15-min math, `UC-15` handling, deterministic AI guards |
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
│   └── screenshots/                # v2.0.0 Light/Dark UI previews & v1.0.11 baseline
├── promo-video/                    # HyperFrames product intro video project
├── app/
│   ├── build.gradle.kts            # Kotlin, Compose, Room schema export, external Release signing
│   ├── schemas/                    # Exported Room DB schemas (v4, v5, v6, v7)
│   └── src/
│       ├── main/java/com/timewallet/mvp/
│       │   ├── MainActivity.kt
│       │   ├── data/               # AI client, Diagnostics, Room DAO/Entities (v7), Backup JSON v2, Settings
│       │   ├── domain/             # Pure Kotlin models & engines (Task/Habit, FocusPin, Calendar, Analytics, Guards)
│       │   ├── notifications/      # Focus Pin & AlarmManager CompletionReminder + CompletionBootReceiver
│       │   └── ui/                 # TimeWalletViewModel, Wally20Coordinator, Screens, Theme, UiKit
│       └── test/java/com/timewallet/mvp/  # JVM unit & static contract test suites (648 test cases)
├── WALLY_TECHNICAL_SPEC.md         # Detailed technical spec & Mermaid diagrams
├── WALLY_V200_CANDIDATE.md         # v2.0.0 Candidate handoff, SHA-256 & Acceptance Checklist
├── WALLY_V120_PRODUCTION_CANDIDATE.md  # v1.2.0 historical baseline handoff
└── CONTEXT_MANIFEST.md
```

</details>

---

## Build, Test & Local Release

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
   - [Candidate `v2.0.0` Handoff & Acceptance Matrix](WALLY_V200_CANDIDATE.md)
   - [Historical `v1.2.0` Production Candidate](WALLY_V120_PRODUCTION_CANDIDATE.md)
   - [Technical Specification](WALLY_TECHNICAL_SPEC.md)
   - [MVP Specification](Time_Wallet_MVP/Time_Wallet_MVP_Spec.md)
   - [Owner Device Self-Check Checklist](WALLY_OWNER_CHECKLIST_V110.md)
