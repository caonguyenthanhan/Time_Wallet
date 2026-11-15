# WALLY V1.0.10 DOSSIER — WIP Completion, UI Layout Fixes & Diagnostics Integrity

## 1. Thông tin Candidate Artifact

- **Ứng dụng:** `com.timewallet.mvp` (`Time Wallet`)
- **Phiên bản:** `versionName = "1.0.10"`, `versionCode = 11` (`minSdk = 26`, `targetSdk = 35`)
- **APK Candidate (`v1.0.10`):**
  - Đường dẫn: `out/wally_candidate_v1010/TimeWallet-MVP-v1.0.10-WALLY-HOTFIX-candidate-debug.apk`
  - Kích thước: `11,818,214` bytes
  - **Full SHA-256:** `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b`
  - Signing Cert (Signer #1 SHA-256): `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (trùng khớp 100% chữ ký `v1.0.9`, verified v2 scheme).
  - DEX static flag check (`(?U)` embedded flag): `DEX_EMBEDDED_FLAG_U_HITS=[]` (`out/wally_evidence_v1010/apk_dex_check_v1.0.10.txt`).
- **Bảo toàn baseline & artifact chuẩn:**
  - `out/wally_candidate_v109/TimeWallet-MVP-v1.0.9-WALLY-HOTFIX-candidate-debug.apk`: **nguyên vẹn**, size `11,972,276` bytes, SHA-256 `0f5f23d9b7eb718367d3d87a26bb3cbbc7fb2884e124163fc5b980bd116ca4e2`.
  - `WALLY_LIVE_EVAL_PLAN_v1.2.md`: **nguyên vẹn**, size `37,553` bytes, SHA-256 `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0`.
- **Manifest & Source Snapshot `v1.0.10` (111 tệp mã nguồn/test/Gradle so với 110 tệp `v1.0.9`):**
  - `out/wally_candidate_v1010/MANIFEST_SHA256_after.txt` & `MANIFEST_VERIFY.txt` (111/111 OK).
  - `out/wally_candidate_v1010/source_v1.0.10.tar` — SHA-256: `e895753d4c81fbf23fb3cbde7cbead49f3a43a616d5df9d4a3b2cf5f6cb27f65`.
  - Diff đối chiếu `v1.0.9 -> v1.0.10`: `5` tệp sửa, `1` tệp test mới, `0` tệp xóa (`out/wally_candidate_v1010/CHANGED_FILES_vs_v1.0.9.txt`, `NEW_FILES_vs_v1.0.9.txt`, `SOURCE_DIFF_vs_v1.0.9_packaged.patch`). Ngoài 111 tệp build/source, đã cập nhật `.agents/AGENTS.md` để đồng bộ quy tắc QA trên `WallyQA`.

---

## 2. Phạm vi thay đổi kỹ thuật đã hoàn tất (trong đúng scope v1.0.10)

Không thay đổi Dashboard layout, AI pipeline, Regex/Parser, Room schema, công thức Analytics, hay thêm tính năng mới.

1. **`Diagnostics.kt` & `TimeWalletApp.kt` (Khôi phục build & log lỗi trung thực):**
   - Hoàn thiện `CrashReportInfo` (`timeIso`, `version`, `exception`), `CrashReportInfo.parse(raw)` và `CrashReportInfo.header(zone)` với `UNKNOWN_VERSION_LABEL = "Không xác định"`.
   - Log cũ chưa có trường `version=` hiển thị đúng `"Thời điểm: <dd/MM/yyyy HH:mm:ss> (<iso>) · Phiên bản khi lỗi: Không xác định"`, tuyệt đối không gắn nhầm `v1.0.10`.
   - `CrashReport.redact(t, now, appVersion)` ghi thêm `version=...` khi có, vẫn giữ nguyên redaction exception message, chuỗi `caused_by=`, và stack trace giới hạn.
   - `CrashRecorder.install(target, appVersion)` nhận `installedVersion` (`"$versionName ($versionCode)"`) từ `TimeWalletApp`; `CrashRecorder.record()` lưu bản ghi trước sang `<file>.prev` trước khi ghi lỗi mới; `CrashRecorder.readPrevious()` đọc `.prev`; `CrashRecorder.clear()` xóa cả tệp chính và `.prev`.
2. **`ComponentsAndDialogs.kt` & `MainActivity.kt` (Sửa lỗi UI & xác nhận xóa ngân sách):**
   - `SectionHeader`: dùng `Modifier.weight(1f, fill = false)` cho tiêu đề khi có nút/ghi chú bên phải; nút hành động dùng `maxLines = 1, softWrap = false` + `minimumInteractiveComponentSize()`, không bị ép cột hoặc rớt dòng.
   - `TimeBudgetItemCard`: chuyển sang bố cục nhiều tầng (`hàng 1`: icon + tên danh mục tối đa 2 dòng + sửa/xóa; `hàng 2`: `FlowRow` cho chip chu kỳ, `★ Ưu tiên`, nhãn còn/vượt `softWrap = false`; `hàng 3`: dòng tiến độ toàn chiều rộng thẻ + thanh tiến độ).
   - `ActivityRecordRow`: tách rõ hàng 1 (tên tối đa 2 dòng `.weight(1f)` + thời lượng 1 dòng bên phải), hàng 2 (nhãn trạng thái + chi tiết mốc giờ bên trái, nút sửa/xóa bên phải), hàng 3 (`✓ Chốt thực tế` khi có).
   - `BudgetDeleteConfirmDialog` & `BudgetEditorDraft`: trong `MainActivity.kt`, quản lý trạng thái `pendingBudgetDelete` + `preservedBudgetDraft` loại trừ lẫn nhau (`if (activeDeleteKey != null) BudgetDeleteConfirmDialog(...) else if (state.showBudgetDialog) TimeBudgetEditorDialog(...)`), đảm bảo chỉ 1 dialog hiển thị tại một thời điểm. Xác nhận xóa từ cả card (`onDeleteBudget`) và bên trong editor (`onDeleteWithDraft`); bấm `"Hủy"` từ editor trả lại `TimeBudgetEditorDialog` giữ nguyên chu kỳ, giờ, phút, ưu tiên đang sửa.
3. **`V1010DiagnosticsAndUiContractTest.kt` (7 unit test mới):**
   - Kiểm chứng parser log cũ (`Không xác định`, không giả mạo `v1.0.10`), timestamp rỗng, redaction + version + cause chain, `.prev` backup & `clear()`, `TimeWalletApp` truyền `installedVersion`, mutual exclusion dialog trong `MainActivity`, và cấu trúc UI trong `ComponentsAndDialogs.kt`.
4. **`.agents/AGENTS.md` (Đồng bộ quy tắc LDPlayer QA):**
   - Giữ nguyên quy định nền bảo vệ thiết bị thật và instance cá nhân của Owner (`index 0`, `LDPlayer`, `emulator-5554`), dẫn chiếu `WALLY_V109_INCIDENT_ADDENDUM_OWNER_INSTANCE.md`, khẳng định không yêu cầu Owner phục hồi instance cá nhân để tiếp tục QA; bổ sung ngoại lệ giới hạn hẹp chỉ áp dụng cho `WallyQA` (`index 1`, `emulator-5556`) với 5 điều kiện bắt buộc.

---

## 3. Bằng chứng Compile, Unit Regression & LDPlayer QA

- **Compile:** `compileDebugKotlin` & `compileDebugUnitTestKotlin` -> Exit code `0` (`out/wally_evidence_v1010/compile_step1.txt`, `compile_step1_exitcode.txt`).
- **Assemble Debug APK:** `.\gradlew.bat assembleDebug --offline --console=plain` -> Exit code `0` (`out/wally_candidate_v1010/assemble_console.txt`, `assemble_exitcode.txt`).
- **In-tree Unit Tests:** `37` suites, **`390` tests** (`383` baseline + `7` mới), `0` failures, `0` errors, `0` skipped (`out/wally_evidence_v1010/unit_final/per_suite_counts.txt`).
- **With External Holdout Tests (`Ai01IndependentVerificationHarnessTest.kt` tạm thời rồi xóa trong `finally`):** `38` suites, **`392` tests** (`390` + `2`), `0` failures, `0` errors, `0` skipped (`out/wally_evidence_v1010/unit_final_with_external/per_suite_counts.txt`).
- **LDPlayer QA (`WallyQA` — chi tiết tại `out/wally_evidence_v1010/ldplayer_qa/LDPLAYER_QA_REPORT.md`):**
  - Kiểm toán lại 8/24 dòng `budgetFound=false` trong `ui_metrics_red_v1.0.9.json` cũ là **invalid/incomplete capture** (không xóa file cũ).
  - Chụp lại đủ fixed-harness RED (`ui_metrics_red_v1.0.9_fixed_complete.json`, 24 ảnh `red109_fixed_*`) chứng minh `sectionHeaderFound=true`, `budgetSectionLocated=true` 6/6 cấu hình và lỗi ép chữ/rớt nút thực tế trên `v1.0.9`.
  - Chụp GREEN matrix `v1.0.10` (`ui_metrics_green_v1.0.10.json`, 24 ảnh `green110_*`): **6/6 cấu hình** `sectionHeaderFound=true`, `sectionActionWrapped=false`, `budgetFound=true`, **`crushed=0`**, **`overflowX=0`**.
  - Kiểm tra chức năng (`v1010_functional_results.json`): `CRASH_LOG_LEGACY`, `F6_COLD_START`, `BUDGET_DELETE_CONFIRM`, `MANUAL_TIMER_JOURNAL_PERSIST` đạt **4/4 PASS**, `0` fatal crash trong `logcat_v1010.txt` (`3352` dòng).

---

## 4. Trạng thái Finding & Rủi ro còn lại (Residual Risks)

| Finding / Hạng mục | Trạng thái sau v1.0.10 | Ghi chú kiểm chứng |
|---|---|---|
| `LAY-02` (`SectionHeader` action rớt dòng/ép chữ) | **VERIFIED_ON_WALLYQA** | 6/6 cấu hình GREEN `sectionActionWrapped=false`. |
| `LAY-03` (`TimeBudgetItemCard` chip biến mất/cột 0px) | **VERIFIED_ON_WALLYQA** | 6/6 cấu hình GREEN hiển thị đủ chip + dòng tiến độ toàn rộng, `crushed=0`. |
| `LAY-04` (`ActivityRecordRow` tên bị cắt ngắn bất thường) | **VERIFIED_ON_WALLYQA** | Tiêu đề 2 dòng đọc rõ ở cả `320dp/fs1.3` và `390dp/fs1.0`, `overflowX=0`. |
| `LAY-01`, `LAY-05`, `OBS-02`, `RED-01`, `BUD-04`, `JRN-01` | **VERIFIED_ON_WALLYQA** | Cài đè `v1.0.9 -> v1.0.10` giữ dữ liệu, xác nhận xóa Budget từ card & editor + draft restore đều PASS. |
| `Diagnostics` (log cũ + version + `.prev`) | **VERIFIED_ON_WALLYQA (legacy header) + UNIT_VERIFIED (`.prev`)** | `last_crash.txt` cũ hiển thị `"Không xác định"` đúng timestamp trên `WallyQA`; `.prev` rotation & `clear()` kiểm chứng qua `V1010DiagnosticsAndUiContractTest`. Chưa fault-inject crash thứ 2 trực tiếp trên emulator. |
| `F6-ROOT` / `F6` trên thiết bị thật | **SMOKE_PASSED_OFFLINE / CHƯA TÁI HIỆN TRÊN DEVICE OWNER** | `F6_COLD_START` qua bình thường trên `WallyQA` không key; nguyên nhân lịch sử F6 trên máy Owner (nếu có) sẽ có dữ liệu `version=` thực tế nhờ `v1.0.10` nếu phát sinh lại. |
| `KBD-01` (Bàn phím mềm trên máy thật) | **NOT_RUN_ON_REAL_DEVICE** | `WallyQA` nhập chữ qua ADB, không bao phủ IME/bàn phím thật của điện thoại Owner. |
| `AI-01` & Live Eval v1.2 (với API key thật) | **NOT_RUN_BY_CONSTRAINT** | Chỉ thị cấm gọi API thật trong bước này; giữ nguyên `WALLY_LIVE_EVAL_PLAN_v1.2.md` để Owner nghiệm thu trên máy thật. |

---

## 5. Bước tiếp theo

Chuyển Reviewer tái kiểm phần thay đổi cuối (`MainActivity.kt`, `TimeWalletApp.kt`, `Diagnostics.kt`, `ComponentsAndDialogs.kt`, `V1010DiagnosticsAndUiContractTest.kt`, `.agents/AGENTS.md`, và bằng chứng `WallyQA`) trước khi Owner thực hiện checklist trên điện thoại thật và Live Eval v1.2 theo `WALLY_OWNER_CHECKLIST_V1010.md`.
