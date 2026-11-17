# WALLY v1.0.12 — DOSSIER (sửa hai lỗi P1 của v1.0.11)

**Ngày:** 2026-11-15 · **Quyền:** MANAGER DIRECTIVE — WALLY AI RELIABILITY / WALLY PRODUCT RELIABILITY · **Single Writer:** AI-Master
**Phạm vi:** P1-AI (Cloud MALFORMED 5/5), P1-NAV (mất thanh tab ở Wally trên Android 9), độ chính xác câu "Code từ lúc 14h đến giờ". Không thiết kế lại UI, không thêm tính năng.

| Mục | Giá trị |
|---|---|
| Candidate | `out/wally_candidate_v1012/TimeWallet-MVP-v1.0.12-WALLY-RELIABILITY-candidate-debug.apk` (11,867,366 byte; trùng kích thước tệp với v1.0.11 do làm tròn nén, **SHA khác** và DEX khác 23.652 byte, đã kiểm có mã mới trong DEX) |
| SHA-256 | `1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8` |
| Phiên bản | `versionName 1.0.12`, `versionCode 13` (cùng `applicationId`, cùng chứng chỉ debug với v1.0.11) |
| Baseline giữ nguyên | APK v1.0.11 `af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed` + `out/wally_candidate_v1011/` + `out/wally_evidence_v1011/` (đã kiểm hash, không ghi vào); v1.0.10 `1d834a83…536b` cũng nguyên |
| Source snapshot | `out/wally_candidate_v1012/source_v1.0.12.tar`, `SOURCE_DIFF_vs_v1.0.11.patch` (đã kiểm áp thật → byte-giống), `MANIFEST_SHA256_after.txt` |
| Lưu ý build | Build **không** tái lập từng byte (hai lần build cùng mã cho hai SHA khác nhau). Vì vậy mọi bằng chứng chạy trên **đúng một APK** ở dòng SHA trên; các APK build thử khác nằm ở `out/wally_evidence_v1012/candidate_build/` và **không** phải candidate |

## 1. Nguyên nhân và cách sửa
Chi tiết, bảng suy luận theo từng con đường `MALFORMED`, tái hiện RED/GREEN và phần **chưa chứng minh**: `WALLY_V1012_ROOT_CAUSE_P1.md`.

| Lỗi | Nguyên nhân (có bằng chứng) | Sửa |
|---|---|---|
| **P1-AI** Cloud `MALFORMED` 5/5, không mã HTTP, 4–37 ms | Một con đường khớp chữ ký đó (ĐÍNH CHÍNH 2026-11-15, xem `WALLY_V1012_P1AI_REAL_STACK_ADDENDUM.md`): lớp mạng của nền tảng từ chối header xác thực vì khóa đã lưu có **ký tự điều khiển** (xuống dòng/CR…; đo trên Android 9 và 16: ký tự ngoài ASCII KHÔNG gây lỗi cục bộ) ⇒ `classify()` gán `MALFORMED` ("AI trả dữ liệu hỏng"), lỗi cục bộ còn bị đếm là "yêu cầu cloud". Tái hiện: v1.0.11 → 3/3 ĐỎ; v1.0.12 → 3/3 XANH | `ApiKeyHygiene` (làm sạch khi lưu/đọc/gửi; kiểm tra + `KEY_INVALID` + báo trong hộp thoại và chặn lưu khóa hỏng); `classify()` không gán lỗi cục bộ là MALFORMED; MALFORMED chỉ sau HTTP 2xx và ghi **mã 200 + tên lớp lỗi**; thân `{"error":…}` được phân loại; chẩn đoán có `err=` |
| **P1-NAV** Android 9 mất thanh tab khi mở Wally dù không có bàn phím | Đo trên APK chẩn đoán (Android 9): `isImeVisible=true` ở mọi tab trong khi `imeBottomPx=0` và `mInputShown=false`: cờ đứng yên ở giá trị khởi tạo vì cửa sổ không chuyển inset cho Compose. Không giả định trước — số đo ở `nav_probe_ldq_android9_v1011_diag.json` | Chỉ ẩn thanh tab khi **ime inset thật** > 0 và đang ở Wally (`shouldShowBottomBar`); máy không chuyển inset ⇒ thanh tab luôn hiện |
| **NL** "Code từ lúc 14h đến giờ" | (a) "đến giờ" không có xử lý; (b) guard thời lượng đọc **mốc giờ** (`từ 14h30`, `8h sáng đến 11h`, `9h họp 1 tiếng`) như thời lượng và **bỏ đề xuất đúng** của AI; (c) hoạt động khác trong cùng câu bị loại/chỉnh nhầm | Prompt `wally-prompt-5` (quy tắc 7b); guard "đến giờ" tất định (giờ kết thúc = giờ hiện tại, liên kết theo giờ bắt đầu, dung sai 2 phút); parser: từ/đến/tới + giờ là mốc, từ chỉ buổi giữa hai mốc, cụm có đơn vị được ưu tiên. Chi tiết: `WALLY_NL_COVERAGE_V1012.md` |

## 2. Thay đổi mã (11 tệp sửa (10 mã nguồn + `build.gradle.kts`), 1 tệp mới ở `app/src/main`)
`MainActivity.kt` (điều kiện thanh tab), `GeminiProposalClient.kt` (khóa, phân loại lỗi, trạng thái HTTP, thân lỗi, prompt 5, truyền ngày/giờ cho guard), `ApiKeyHygiene.kt` (mới), `AiFault.kt` (`KEY_INVALID`), `AiDiagnostics.kt` (`errorClass`), `WallyAiGuards.kt` (guard "đến giờ"), `VietnameseChatParser.kt` (mốc giờ so với thời lượng), `TimerAndSettingsStore.kt` (chuẩn hóa khóa khi đọc/lưu), `TimeWalletViewModel.kt` (kiểm tra khi lưu), `ChatScreen.kt` (nút Mở cấu hình cho `KEY_INVALID`), `ComponentsAndDialogs.kt` (báo lỗi khóa trong hộp thoại), `app/build.gradle.kts` (13 / 1.0.12). Test: 6 tệp mới, **2 test cũ sửa có lý do** (`V1011UiPolishContractTest`: từng ghim lên `isImeVisible`; `WallyEightLayerTest`: phiên bản prompt 4→5).

## 3. Giữ nguyên (kiểm bằng test + bằng chứng chạy)
AI chỉ đề xuất, chỉ ghi sau xác nhận (không đổi); `Rx`/`GuardHealth` (F6) và DEX không có `(?U)`; hợp đồng phản hồi chặt v1.0.6 (R03: `durationSeconds` chuỗi, `content` mảng, `proposals` null **vẫn bị từ chối**); Keystore, Room (không đổi schema), vòng đời đồng hồ, công thức phân tích; toàn bộ giao diện v1.0.11 (ngoài hai điểm nêu trên).

## 4. Kiểm chứng
| Hạng mục | Kết quả | Bằng chứng |
|---|---|---|
| Unit test trong cây mã | **443 test / 44 suite, 0 lỗi** (407 cũ + 36 mới) | `out/wally_evidence_v1012/unit_final/` |
| Cộng holdout ngoài | **45 suite, 445 test, 0 lỗi** | `unit_final_with_external/` |
| Hồi quy hai cây cho câu của Owner | v1.0.11 **3/3 ĐỎ** → v1.0.12 **3/3 XANH** | `red_green/`, `red_on_v1011_console.txt` |
| Điều hướng Android 9 (WallyQA, 780×1688) | **PASS**: mở Wally, gõ, đóng bàn phím (Back), 7 lượt chuyển tab, Back ở Tổng quan thoát app (như baseline) | `nav_verify_ldq_android9_v1012.json`, `screens/nav_ldq_android9_v1012_*` |
| Điều hướng Android 16 (AVD, bàn phím mềm thật) | **PASS**: bàn phím thật hiện ⇒ thanh tab ẩn; gõ; `Back` đóng bàn phím ⇒ thanh tab hiện lại và vẫn ở Wally; 7 lượt chuyển tab đúng nội dung; Back ở Tổng quan thoát app (như baseline) | `nav_verify_avd_android16_v1012.json`, `screens/nav_avd_android16_v1012_*` |
| Luồng đầu–cuối với **AI giả lập** (harness; WallyQA; tiếng Việt có dấu thật) | câu của Owner → thẻ "Công việc · 14:00–giờ hiện tại" → chưa có gì trong Nhật ký → Xác nhận → đúng 1 dòng 14:00–giờ hiện tại; thông báo khóa hỏng hiện trong hộp thoại | `aiflow_wallyqa_harness.json`, `screens/aiflow_*` — **bản harness, KHÔNG phải candidate, KHÔNG phải AI Live** |
| Cài đè v1.0.11 → v1.0.12 | **PASS (mức giao diện)**: gỡ app → cài v1.0.11 đóng băng → tạo dữ liệu bằng chính v1.0.11 (3 dòng mẫu, bản ghi `MARKER-UPG-1012`, ngân sách 5h/tuần) → `adb install -r` v1.0.12 → còn nguyên. Chưa phủ khóa AI/Keystore, dòng dự kiến/đang chạy, so sánh mức CSDL | `upgrade_test.json`, `screens/upgrade_*` |
| Ma trận 411dp/320dp × cỡ chữ 1.0/1.3, trạng thái cài mới/có dữ liệu/sau xóa | CAPTURED | `screens/final_*` |
| Luồng chức năng | **PASS** (tự động qua ADB/UI Automator, từng bước `True`): sửa/xóa ngân sách và bản ghi có xác nhận + Hủy giữ nguyên; đồng hồ chạy → hủy → chờ; Xuất JSON; xoay ngang/dọc ở Wally; thêm giờ thủ công; đồng hồ bắt đầu → kết thúc & lưu → Nhật ký `0h 01` "Đã đo" | `chain_log.txt`, `screens/final_flow_*` |
| Logcat | 0 `FATAL EXCEPTION` / `PatternSyntaxException` / `ExceptionInInitializerError` / `GUARD_UNAVAILABLE`, **0 ANR của app** (AVD: 4 dòng ANR của tiến trình hệ thống lúc emulator khởi động; WallyQA: 0) | `logcat_avd_full.txt`, `logcat_aiflow_wallyqa.txt` |
| DEX / chữ ký | không có `(?U)`; cùng chứng chỉ debug | `apk_dex_check_v1.0.12.txt`, `SIGNING_CERT_COMPARE.txt` |

## 5. Giới hạn và rủi ro còn lại (trung thực)
- **AI Live chưa chạy** (không có khóa/quyền): chưa chứng minh model thật hiểu đúng; chưa `AI_LIVE_PASS`, `SPEC_ACCEPTED`. Yêu cầu cụ thể ở `WALLY_LIVE_EVAL_ADDENDUM_V1012.md`.
- **Nguyên nhân P1-AI chưa xác nhận bằng chính khóa của Owner** (không được đọc khóa): đó là nguyên nhân khớp mọi số đo (đính chính 2026-11-15: cơ chế = ký tự điều khiển, tái hiện trên Android 9 thật; nhãn = MITIGATED). Chẩn đoán mới tự nhận diện lần sau (`KEY_INVALID` hoặc mã HTTP + tên lớp lỗi).
- P1-NAV đo trên Android 9 (LDPlayer) và Android 16; **chưa** đo trên điện thoại thật, Android 10–14, hay khi bàn phím thật hiện trên máy không chuyển inset (khi đó thanh tab vẫn hiện — an toàn nhưng không nhường chỗ).
- Câu chỉ có giờ bắt đầu ("Code từ 14h"): chỉ có quy tắc prompt bắt AI hỏi lại, không có kiểm tra tất định; qua nửa đêm và gõ không dấu cho "đến giờ" chưa xử lý (xem `WALLY_NL_COVERAGE_V1012.md`).
- Luồng đề xuất → xác nhận → sổ trên **candidate** với AI thật: **NOT_TESTED**. Mức harness (AI giả lập) PASS và mức đơn vị PASS không thay thế.
- Chưa kiểm điện thoại thật, `KBD-01`, F6 smoke thật, chế độ tối, TalkBack.
- Lỗi thẩm mỹ nhỏ còn lại từ v1.0.11 (chú giải biểu đồ cắt tên danh mục ở 390dp).
- Bản harness chẩn đoán/AI giả lập nằm ở `out/wally_evidence_v1012/harness/` chỉ để làm bằng chứng; mã nguồn đã hoàn nguyên và kiểm hash ngay sau khi build harness (APK candidate đã được build và lưu riêng trước đó; tar nguồn và manifest của candidate lấy từ cây đã hoàn nguyên, `HarnessAiConnection` không có trong `app/src`).
