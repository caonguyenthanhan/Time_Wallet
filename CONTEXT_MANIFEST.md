# CONTEXT_MANIFEST.md
Cập nhật lần cuối: 2026-11-16T19:44:00+07:00

## Core (luôn nạp vào context)
- `.agents/` — Quy tắc dự án (`AGENTS.md`, `rules/link-ecosystem.md`)
- `.gitignore`, `.repomixignore` — Bộ lọc tệp build, output QA, tệp tạm
- `README.md` — Tổng quan kiến trúc API-First, cấu trúc `v1.0.13` / `v1.1.0` & hướng dẫn build/test
- `Time_Wallet_MVP/` — Đặc tả MVP & HTML Prototype gốc
- `ai_test/` — Script kiểm thử gateway (`test_foza_ai.ts`, đọc `FOZA_API_KEY` từ biến môi trường)
- `app/` — Mã nguồn Kotlin/Compose, bộ test JVM (`app/src/test/`) & Room schemas (`app/schemas/`)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradle.properties`
- Tài liệu kỹ thuật, kiểm thử, checklist & bàn giao tại root (`SPEC_TIME_WALLET_WALLY.md`, `WALLY_*`, `REVIEW_*`, `AI-01_*`, `MVP_V1_HANDOFF.md`)

## Vendored / operational (KHÔNG nạp vào context, KHÔNG di chuyển)
- `.git/`, `.gradle/`, `.kotlin/`, `.idea/`
- `build/`, `app/build/`
- `out/` — Candidate APKs & bằng chứng QA/Live Eval (đã ignore trong `.gitignore` & `.repomixignore`)
- `local.properties`, `gradlew`, `gradlew.bat`, `Biểu tượng ứng dụng Ví Thời Gian.png`
- Tệp trung gian pipeline (nếu sinh khi quét): `tree_scan_result.json`, `tree_scan_result.txt`, `classify_result.json`, `repomix-context.xml`

## Đã archive
- Không có mục nào cần tự động chuyển sang `Archived/` (`ai_test/` là thư mục kiểm thử đang dùng, giữ lại; `text.md` không còn cần cho repo chính nếu chưa dọn).

> Lần scan sau: cấu trúc top-level đã được đồng bộ với `context-hygiene.md` và `.repomixignore`; không nạp `out/`, `build/`, `.gradle/` vào context.
