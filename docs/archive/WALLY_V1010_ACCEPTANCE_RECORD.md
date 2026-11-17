# WALLY V1.0.10 ACCEPTANCE RECORD

- **Candidate:** `TimeWallet-MVP-v1.0.10-WALLY-HOTFIX-candidate-debug.apk`
- **Đường dẫn:** `out/wally_candidate_v1010/TimeWallet-MVP-v1.0.10-WALLY-HOTFIX-candidate-debug.apk`
- **Application ID / Version:** `com.timewallet.mvp`, `versionName = 1.0.10`, `versionCode = 11`
- **Candidate Full SHA-256:** `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b`
- **Previous Preserved APK (`v1.0.9`):** `0f5f23d9b7eb718367d3d87a26bb3cbbc7fb2884e124163fc5b980bd116ca4e2` (không bị thay đổi)
- **Live Eval Plan v1.2 SHA-256:** `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0` (không bị thay đổi)
- **Source Snapshot (`source_v1.0.10.tar`):** `e895753d4c81fbf23fb3cbde7cbead49f3a43a616d5df9d4a3b2cf5f6cb27f65` (`111` tệp: `5` sửa, `1` mới, `0` xóa so với `v1.0.9`)

---

## Cổng kiểm tra trước khi bàn giao nghiệm thu

| Cổng / Hạng mục | Trạng thái | Chi tiết xác minh |
|---|---|---|
| Compile Kotlin (`Debug` + `DebugUnitTest`) | **PASS** | Exit `0` (`out/wally_evidence_v1010/compile_step1.txt`) |
| Unit Regression (In-tree) | **PASS** | `37` suites, `390` tests (`383` + `7`), `0` fail/error/skip (`out/wally_evidence_v1010/unit_final/`) |
| Unit Regression (With External Holdout) | **PASS** | `38` suites, `392` tests (`390` + `2`), `0` fail/error/skip (`out/wally_evidence_v1010/unit_final_with_external/`) |
| Assemble APK & Cert Check | **PASS** | Exit `0`, cert SHA-256 `402f1d41...` khớp `v1.0.9`, `DEX_EMBEDDED_FLAG_U_HITS=[]` |
| RED Audit + Fixed-Harness RED (`v1.0.9`) | **COMPLETE** | `8` dòng cũ được đánh dấu invalid capture (giữ nguyên `ui_metrics_red_v1.0.9.json`); fixed-harness chụp đủ 6/6 viewport (`ui_metrics_red_v1.0.9_fixed_complete.json`, `red109_fixed_*`) |
| GREEN UI Matrix (`v1.0.10` trên `WallyQA`) | **PASS** | `24/24` lượt chụp `320/390/412dp × 1.0/1.3`: `sectionActionWrapped=false`, `crushed=0`, `overflowX=0` (`ui_metrics_green_v1.0.10.json`, `green110_*`) |
| Functional Smoke (`WallyQA` `v1.0.10`) | **PASS** | `4/4` PASS (`CRASH_LOG_LEGACY`, `F6_COLD_START`, `BUDGET_DELETE_CONFIRM`, `MANUAL_TIMER_JOURNAL_PERSIST`), `logcat_v1010.txt` = `0` fatal crash |
| Reviewer Independent Re-check | **GO_FOR_OWNER_ACCEPTANCE** | Xem `REVIEW_WALLY_V1010.md` (Reviewer không sửa production source) |
| Manager Closure (2026-11-15) | **CLOSED_FOR_CANDIDATE** | Candidate **FROZEN**; nhãn crash-log là `ACCEPTED_DEVIATION`; xem `WALLY_V1010_MANAGER_CLOSURE_RECORD.md` |
| Owner Physical Phone + `KBD-01` + Live Eval v1.2 | **PENDING_OWNER** | Thực hiện theo `WALLY_OWNER_CHECKLIST_V1010.md`; **không** yêu cầu phục hồi LDPlayer `index 0` |

---

## Ký nhận các vai trò

- **Implementation & `WallyQA` Verification:** Hoàn tất build xanh `v1.0.10` (`1d834a83...`), 390+2 unit tests, 24 RED-fixed + 24 GREEN UI matrix + 4/4 kiểm tra chức năng trên `WallyQA` (`emulator-5556`).
- **Independent Reviewer:** `GO_FOR_OWNER_ACCEPTANCE` (`REVIEW_WALLY_V1010.md`)
- **Manager:** candidate `v1.0.10` **FROZEN** (SHA-256 `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b`); nhãn `Phiên bản khi lỗi: Không xác định` = `ACCEPTED_DEVIATION` (không sửa string, không rebuild); không thêm build/QA/Wave/Gate. Sự cố `Ld9BoxSVC` giữ `OPEN` ở cấp audit, không chặn nghiệm thu điện thoại.
- **Owner Physical Phone & Live Eval v1.2:** `PENDING`
  - Thiết bị test: `...`
  - Kết quả UI / Budget Confirm / Crash Header / KBD-01 / F6 Smoke: `...`
  - Kết quả Live Eval v1.2 (`... / ...` ca): `...`
