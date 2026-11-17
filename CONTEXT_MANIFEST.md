# CONTEXT_MANIFEST.md
Cập nhật lần cuối: 2026-11-17T19:28:00+07:00

## Core (luôn nạp vào context)
- `.agents/` — Quy tắc dự án (`AGENTS.md`, `rules/link-ecosystem.md`)
- `.gitignore`, `.repomixignore` — Bộ lọc tệp build, output QA, archive & tệp tạm
- `README.md` — Trang giới thiệu tổng quan GitHub `v1.2.0`, điểm nổi bật kỹ thuật, cấu trúc & hướng dẫn build/test
- `Time_Wallet_MVP/` — Đặc tả MVP & HTML Prototype gốc
- `ai_test/` — Script kiểm thử gateway (`test_foza_ai.ts`, đọc `FOZA_API_KEY` từ biến môi trường)
- `app/` — Mã nguồn Kotlin/Compose, bộ test JVM (`app/src/test/`) & Room schemas (`app/schemas/`)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradle.properties`
- Đặc tả & hồ sơ hiện hành tại root: `SPEC_TIME_WALLET_WALLY.md`, `WALLY_TECHNICAL_SPEC.md`, `WALLY_V110_PLAN.md`, `WALLY_V110_CLOSURE_DIGEST.md`, `WALLY_OWNER_CHECKLIST_V110.md`, `WALLY_V120_PRODUCTION_CANDIDATE.md`

## Vendored / operational (KHÔNG nạp vào context, KHÔNG di chuyển)
- `.git/`, `.gradle/`, `.kotlin/`, `.idea/`, `.vscode/`
- `build/`, `app/build/`
- `out/` — Chỉ giữ các candidate APK & bằng chứng chính `v1.0.9`–`v1.1.0` (`wally_candidate_v109..v110`, `wally_evidence_v109..v1013`, `wally_live_v1012/evidence`, `wally_live_v1013`, `wally_live_v110`; đã ignore trong `.gitignore` & `.repomixignore`)
- `local.properties`, `gradlew`, `gradlew.bat`, `Biểu tượng ứng dụng Ví Thời Gian.png`
- `docs/screenshots/` — Ảnh chụp UI minh họa (đã loại khỏi text context trong `.repomixignore`)
- Tệp trung gian pipeline (nếu sinh khi quét): `tree_scan_result.json`, `tree_scan_result.txt`, `classify_result.json`, `repomix-context.xml`

## Đã archive
- `docs/archive/` — Lưu trữ `61` hồ sơ, dossier, biên bản nghiệm thu, review & live-eval lịch sử (`v1.0.5`–`v1.0.13`, `AI-01_*`, `REVIEW_*`, `MVP_V1_HANDOFF.md`) để giữ root gọn sạch (loại khỏi context mặc định qua `.repomixignore`).
- Đã dọn rác root (`text.md`, 2 APK `v1.0.1` cũ, 2 tệp `.lnk`) và `37` thư mục tạm/`*_snapshot_pre`/`*_ws` cũ trong `out/`.

> Lần scan sau: cấu trúc đã gọn theo `context-hygiene.md` và `.repomixignore`; không nạp `docs/archive/`, `docs/screenshots/`, `out/`, `build/`, `.gradle/` vào context.
