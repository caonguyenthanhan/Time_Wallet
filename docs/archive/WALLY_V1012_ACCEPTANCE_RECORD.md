# WALLY V1.0.12 ACCEPTANCE RECORD

- **Candidate:** `TimeWallet-MVP-v1.0.12-WALLY-RELIABILITY-candidate-debug.apk` — `out/wally_candidate_v1012/`
- **Full SHA-256:** `1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8`
- **Application ID / Version:** `com.timewallet.mvp`, `versionName = 1.0.12`, `versionCode = 13`
- **Baseline (không bị ghi đè):** `v1.0.11` — `af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed`; `v1.0.10` — `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b`
- **Live Eval Plan v1.2 SHA-256:** `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0` (không thay đổi; phụ lục riêng `WALLY_LIVE_EVAL_ADDENDUM_V1012.md`)
- **Quyền / phạm vi:** Manager Directive WALLY AI RELIABILITY + WALLY PRODUCT RELIABILITY; Single Writer = AI-Master; QA cô lập (AVD `TimeWallet_QA`, LDPlayer `WallyQA`), không khóa/dữ liệu Owner, không gọi API thật.

## Cổng kiểm tra

| Cổng / hạng mục | Trạng thái | Chi tiết |
|---|---|---|
| Compile + `assembleDebug` sạch (`--rerun-tasks`) | **PASS** | `unit_final/console.txt` |
| Unit test trong cây mã | **PASS** | `44` suite, `443` test, `0` lỗi (407 cũ + 36 mới); `unit_final/` |
| Unit test + holdout ngoài | **PASS** | 45 suite, 445 test, 0 lỗi |
| Hồi quy câu của Owner, hai cây | **PASS** | v1.0.11 3/3 ĐỎ → v1.0.12 3/3 XANH (`red_green/`) |
| **P1-AI** nguyên nhân + sửa | **MITIGATED (đính chính 2026-11-15: cơ chế = ký tự điều khiển trong khóa, tái hiện trên Android 9 thật; ký tự ngoài ASCII bị bác bỏ; chưa xác nhận bằng khóa/máy của Owner)** | `WALLY_V1012_ROOT_CAUSE_P1.md`, `WALLY_V1012_P1AI_REAL_STACK_ADDENDUM.md` |
| **P1-NAV** nguyên nhân (có số đo) + sửa | **FIXED + VERIFIED trên Android 9 và Android 16** | số đo `nav_probe_ldq_android9_v1011_diag.json`; xác minh `nav_verify_*` |
| Điều hướng Android 9 (mở Wally, nhập, đóng bàn phím, Back, chuyển tab) | **PASS** | `nav_verify_ldq_android9_v1012.json` |
| Điều hướng Android 16 với bàn phím thật | **PASS** | `nav_verify_avd_android16_v1012.json` |
| Cài đè v1.0.11 → v1.0.12 giữ dữ liệu | **PASS (mức giao diện)** | `upgrade_test.json` (dữ liệu do chính v1.0.11 tạo; mức giao diện) |
| Luồng chức năng + đồng hồ → lưu → Nhật ký | **PASS** | `chain_log.txt`, `final_timer_save.json` |
| Runtime AVD: ma trận 411dp/320dp × 1.0/1.3, cài mới, có dữ liệu, sau xóa | **CAPTURED** | `screens/final_*`, `delivery_*` |
| Logcat | **PASS (của app)**: 0 `FATAL EXCEPTION` / `PatternSyntaxException` / `ExceptionInInitializerError` / `GUARD_UNAVAILABLE`, 0 ANR của app (AVD: 4 dòng ANR hệ thống lúc khởi động; WallyQA: 0) | `logcat_avd_full.txt`, `logcat_aiflow_wallyqa.txt` |
| DEX không `(?U)` (F6) / chữ ký | **PASS** | `apk_dex_check_v1.0.12.txt`, `SIGNING_CERT_COMPARE.txt` |
| Câu Owner → đề xuất → xác nhận → sổ, **AI giả lập** (harness trên WallyQA) | **HARNESS ONLY — PASS ở mức harness** | `aiflow_wallyqa_harness.json`, `screens/aiflow_*`; không phải candidate, không phải AI Live |
| Câu Owner → đề xuất → xác nhận → sổ trên candidate với **AI thật** | **NOT_TESTED / BLOCKED** | cần khóa do Owner nhập + quyền gọi API thật |
| Live Eval v1.2 (≥ 38/40, critical 100%, nhóm G 7/7) trên v1.0.12 | **NOT_RUN — BLOCKED** | Owner nhập khóa trên thiết bị + cho phép gọi API thật; `WALLY_LIVE_EVAL_ADDENDUM_V1012.md` |
| Độ phủ test ngôn ngữ tự nhiên | **CÔNG BỐ** | `WALLY_NL_COVERAGE_V1012.md` (3 lỗi mốc-giờ/thời-lượng đã sửa; khoảng trống G6–G9 còn hở) |
| Independent Product Review | **KHÔNG CHẠY** | không có yêu cầu vòng review cho đợt này; các sửa chỉ được tự kiểm chứng (test + runtime), chưa có reviewer độc lập |
| Điện thoại thật + `KBD-01` + F6 smoke + Live Eval | **PENDING_OWNER** | `WALLY_OWNER_CHECKLIST_V1012.md` |

## Chưa được tuyên bố (không đổi)
`DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED` — cần điện thoại thật / khóa riêng của Owner. Emulator, LDPlayer và bản harness AI giả lập **không** thay thế điều đó. Kết quả của v1.0.6–v1.0.11 **không** chuyển sang v1.0.12 (SHA khác).

## Quản trị môi trường (đã kiểm)
- WallyQA chỉ mở khi **không** có instance LDPlayer của Owner đang chạy (đã kiểm `ldconsole runninglist` trước mỗi lần; lần đầu thấy `index 0` đang chạy ở yêu cầu trước nên dừng và hỏi Owner). `ldconsole quit --index 1` chỉ cho WallyQA; không `kill-server`, không dừng/kill tiến trình dùng chung.
- AVD tắt bằng `adb -s emulator-5580 emu kill`; không còn tiến trình `emulator`/`qemu`/`crashpad`, `adb devices` rỗng, `ldconsole runninglist` rỗng; không đụng LDPlayer của Owner; máy chủ adb dùng chung giữ nguyên (không `kill-server`).

## Ký nhận
- **Implementation & QA cô lập:** hoàn tất (`WALLY_V1012_DOSSIER.md`).
- **Owner Physical Phone & Live Eval v1.2:** `PENDING`
  - Thiết bị test: `...`
  - Kết quả điều hướng / khóa API / câu Owner: `...`
  - Kết quả Live Eval v1.2: `...`
