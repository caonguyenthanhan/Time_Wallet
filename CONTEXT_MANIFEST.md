# CONTEXT_MANIFEST.md
Cập nhật lần cuối: 2026-11-22T05:00:00+07:00

## Core (luôn nạp vào context)
- `.agents/` — Quy tắc dự án (`AGENTS.md`, `rules/link-ecosystem.md`)
- `.gitignore`, `.repomixignore` — Bộ lọc tệp build, output QA, `reference/`, media promo, design vector nguồn lớn, `docs/archive` & tệp tạm
- `README.md` — Trang giới thiệu tổng quan `v2.1.0` (`versionCode 19`), thư viện ảnh Light/Dark, kiến trúc Room v7, Adaptive Context & Thống kê 3 lớp
- `promo-video/` — Mã nguồn bố cục HyperFrames (`index.html`, `compositions/*.html`, `SPEC.md`, `STORYLINES.md`, `REVIEW.md`)
- `Time_Wallet_MVP/` — Đặc tả MVP & HTML Prototype gốc
- `ai_test/` — Script kiểm thử gateway (`test_foza_ai.ts`, đọc `FOZA_API_KEY` từ biến môi trường; KHÔNG xóa dù có chuỗi `_test`)
- `app/` — Mã nguồn Kotlin/Compose `v2.1.0` (`AdaptiveContextRouter`, `WallyContextEngine`, `WallyIntelligenceCoordinator`, `LedgerAnalyticsCalculator`, `InsightsScreen`), bộ test JVM (`655` cases) & Room schemas (`v4..v7`)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradle.properties`
- Tài liệu chính ở root: `WALLY_V210_CANDIDATE.md`, `WALLY_V200_CANDIDATE.md`, `WALLY_V120_PRODUCTION_CANDIDATE.md`, `WALLY_TECHNICAL_SPEC.md`, `SPEC_TIME_WALLET_WALLY.md`, `WALLY_V110_PLAN.md`, `WALLY_V110_CLOSURE_DIGEST.md`, `WALLY_OWNER_CHECKLIST_V110.md`

## Vendored / operational (KHÔNG nạp vào context, KHÔNG di chuyển)
- `.git/`, `.gradle/`, `.kotlin/`, `.idea/`, `.vscode/`, `build/`, `app/build/`
- `reference/` — Ảnh UI tham chiếu Rolly & HTML nháp ngoài scope mã nguồn
- `Wally_Vector*.svg`, `TimeWallet_Wally_Icon_Style_Guide*.svg`, `Robot vui vẻ với biểu tượng lịch và đồng hồ.png`, `Biểu tượng ứng dụng Ví Thời Gian.png` — Tệp thiết kế nguồn dung lượng lớn (bản tối ưu đã nằm trong `docs/brand/`)
- `promo-video/.hyperframes/`, `promo-video/.media/`, `promo-video/.thumbnails/`, `promo-video/.waveform-cache/`, `promo-video/renders/`
- `out/` — Lưu candidate APK & chứng từ build (`wally_candidate_v109..v200`, `wally_evidence_*`, ignored khỏi Git & Repomix)
- `docs/screenshots/` — Thư viện ảnh chụp UI `v2.0.0` (`ui-preview/`) và baseline `v1.0.11` (`ldplayer-android9/`)
- `local.properties`, `gradlew`, `gradlew.bat`

## Đã archive & vệ sinh
- `docs/archive/` — Lưu trữ hồ sơ, dossier, biên bản nghiệm thu, review & live-eval lịch sử (`v1.0.5`–`v1.0.13`, `AI-01_*`, `REVIEW_*`, `MVP_V1_HANDOFF.md`)
- Các tệp trung gian quét cây (`tree_scan_result.*`, `classify_result.json`, `repomix-context.xml`) bị loại trừ khỏi Git/Repomix và dọn sạch sau khi hoàn tất cập nhật manifest.

> Lần scan sau: cấu trúc đã gọn theo `context-hygiene.md`, `.gitignore` và `.repomixignore`; không nạp `docs/archive/`, `docs/screenshots/`, `reference/`, `out/`, `build/`, `.gradle/` vào context.
