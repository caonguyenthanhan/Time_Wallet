# CONTEXT_MANIFEST.md
Cập nhật lần cuối: 2026-11-25T00:35:00+07:00

## Core (luôn nạp vào context)
- `.agents/` — Quy tắc dự án (`AGENTS.md`, `rules/link-ecosystem.md`)
- `.gitignore`, `.repomixignore` — Bộ lọc tệp build, output QA, `reference/`, media promo, design vector nguồn lớn, `docs/archive` & tệp tạm
- `README.md` — Trang giới thiệu sản phẩm trực quan cho `Wally — Time Wallet v2.2.2` (`versionCode 25`)
- `docs/ARCHITECTURE_AND_PRIVACY.md` — Kiến trúc 4 lớp, Room v8, bảo mật Keystore/Permissions, kết quả kiểm chứng `v2.2.2` & hướng dẫn build
- `promo-video/` — Mã nguồn bố cục HyperFrames (`index.html`, `compositions/*.html`, `SPEC.md`, `STORYLINES.md`, `REVIEW.md`)
- `Time_Wallet_MVP/` — Đặc tả MVP & HTML Prototype gốc
- `ai_test/` — Script kiểm thử gateway (`test_foza_ai.ts`, đọc `FOZA_API_KEY` từ biến môi trường; KHÔNG xóa dù có chuỗi `_test`)
- `app/` — Mã nguồn Kotlin/Compose `v2.2.2` (`versionCode 25`): `WallyDataStoryEngine`, `LedgerAnalyticsCalculator`, `TaskHabitEngine`, `ConfirmationEngine`, `TimelineFeedEngine`, `WallyHomeWidgetProvider` & `WallyHomeWidgetService`, `WallyFloatingService`, `AiTodoEngine`, `WallyHubSnapshotEngine`, `WallyActionController`, `WallyTodoAiPlanner`, `AiProviderPreset`, `AppUpdateContract`, `GitHubReleaseUpdateChannel`, Room schema `v8` (`MIGRATION_7_8`), `src/screenshotTest` (`15/15` pass) & bộ test JVM (`704` cases: `700` pass, `4` skipped)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradle.properties`
- Tài liệu đặc tả & phát hành khác: `SPEC_TIME_WALLET_WALLY.md`, `SPEC_WALLY_UI_UX-v221.md`, `WALLY_TECHNICAL_SPEC.md`, `WALLY_V210_CANDIDATE.md`, `WALLY_V200_CANDIDATE.md`, `WALLY_V120_PRODUCTION_CANDIDATE.md`, `WALLY_V110_PLAN.md`, `WALLY_V110_CLOSURE_DIGEST.md`, `WALLY_OWNER_CHECKLIST_V110.md`

## Vendored / operational (KHÔNG nạp vào context, KHÔNG di chuyển)
- `.git/`, `.gradle/`, `.kotlin/`, `.idea/`, `.vscode/`, `build/`, `app/build/`
- `reference/` — Ảnh UI tham chiếu Rolly & HTML nháp ngoài scope mã nguồn
- `Wally_Vector*.svg`, `TimeWallet_Wally_Icon_Style_Guide*.svg`, `Robot vui vẻ với biểu tượng lịch và đồng hồ.png`, `Biểu tượng ứng dụng Ví Thời Gian.png` — Tệp thiết kế nguồn dung lượng lớn (bản tối ưu đã nằm trong `docs/brand/`)
- `promo-video/.hyperframes/`, `promo-video/.media/`, `promo-video/.thumbnails/`, `promo-video/.waveform-cache/`, `promo-video/renders/`
- `out/` — Lưu candidate APK & chứng từ build (`wally_candidate_v109..v222`, `haiku_eval`, `wally_evidence_*`, ignored khỏi Git & Repomix)
- `docs/screenshots/` — Thư viện ảnh chụp UI thật (`ui-preview/` gồm `v2.2.0` + `v2.0.0`, và baseline `ldplayer-android9/`)
- `local.properties`, `gradlew`, `gradlew.bat`

## Đã archive & vệ sinh
- `docs/archive/` — Lưu trữ hồ sơ, dossier, biên bản nghiệm thu, review & live-eval lịch sử (`v1.0.5`–`v1.0.13`, `AI-01_*`, `REVIEW_*`, `MVP_V1_HANDOFF.md`)
- Các tệp trung gian quét cây (`tree_scan_result.*`, `classify_result.json`, `repomix-context.xml`) bị loại trừ khỏi Git/Repomix và dọn sạch sau khi hoàn tất cập nhật manifest.

> Lần scan sau: cấu trúc đã gọn theo `context-hygiene.md`, `.gitignore` và `.repomixignore`; không nạp `docs/archive/`, `docs/screenshots/`, `reference/`, `out/`, `build/`, `.gradle/` vào context.
