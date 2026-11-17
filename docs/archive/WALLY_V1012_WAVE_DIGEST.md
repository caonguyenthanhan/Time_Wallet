# WAVE DIGEST — v1.0.12 (gửi Manager): sửa P1-AI và P1-NAV

**Ngày:** 2026-11-15 · **Single Writer:** AI-Master · **Quyền:** Manager Directive WALLY AI RELIABILITY / WALLY PRODUCT RELIABILITY

## Kết quả
| Mục | Giá trị |
|---|---|
| Candidate | `out/wally_candidate_v1012/TimeWallet-MVP-v1.0.12-WALLY-RELIABILITY-candidate-debug.apk` (11,867,366 byte) |
| SHA-256 | `1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8` |
| Phiên bản / ký | `versionName 1.0.12`, `versionCode 13`; cùng chứng chỉ debug với v1.0.11; DEX không có `(?U)` |
| Baseline | v1.0.11 `af50d6cc…079ed` và toàn bộ bằng chứng v1.0.11 **không bị sửa** (đã kiểm hash); v1.0.10 `1d834a83…536b` nguyên |
| **P1-AI** | Nguyên nhân (có bằng chứng): khóa đã lưu có **ký tự điều khiển** (xuống dòng/CR…) mà header HTTP không chuyển được (ĐÍNH CHÍNH: đo trên Android 9 và 16 cho thấy ký tự ngoài ASCII KHÔNG gây lỗi cục bộ, xem `WALLY_V1012_P1AI_REAL_STACK_ADDENDUM.md`) ⇒ lớp mạng ném lỗi cục bộ vài ms, không mã HTTP ⇒ bị gán nhãn `MALFORMED`. Đó là con đường khớp (độ trễ 4–37 ms loại trừ mọi vòng mạng; gateway trả 401 có mã HTTP sau 0,5–1,3 s) cả 3 số đo của Owner (5/5 MALFORMED, không mã HTTP, 4–37 ms). Tái hiện hai cây: v1.0.11 **3/3 ĐỎ** → v1.0.12 **3/3 XANH**. Sửa gốc (làm sạch khóa, `KEY_INVALID`, phân loại lỗi trung thực, chẩn đoán có mã HTTP + tên lớp lỗi), không chỉ đổi thông báo. **Chưa xác nhận bằng chính khóa của Owner** (không được đọc khóa): chẩn đoán mới tự nhận diện lần sau |
| **P1-NAV** | Số đo (không giả định): Android 9 `isImeVisible=true` ở mọi tab, `imeBottomPx=0`, `mInputShown=false` ⇒ thanh tab bị ẩn oan. Sửa: chỉ ẩn khi có ime inset thật. **PASS trên Android 9 (WallyQA) và Android 16 (AVD, bàn phím thật)**: mở Wally, gõ, đóng bàn phím, Back, chuyển tab |
| Câu của Owner | "Code từ lúc 14h đến giờ" là regression bắt buộc (`V1012OwnerRegressionTest`, `V1012OwnerSentenceTest`). Phát hiện và sửa thêm 3 lỗi thật ở phần kiểm tra của app (mốc giờ bị đọc nhầm là thời lượng làm đề xuất đúng của AI bị bỏ). Công bố độ phủ và khoảng trống: `WALLY_NL_COVERAGE_V1012.md` |
| Test | **443 test / 44 suite, 0 lỗi** (407 cũ + 36 mới); **445 / 45** khi thêm holdout ngoài. Sửa 2 test cũ có lý do (ghim lên `isImeVisible`; phiên bản prompt) |
| Luồng đầu–cuối | AI giả lập (harness trên WallyQA, tiếng Việt có dấu thật): câu Owner → thẻ "Công việc · 14:00–giờ hiện tại" → Nhật ký trống trước xác nhận → Xác nhận → đúng 1 dòng. **HARNESS ONLY, không phải candidate, không phải AI Live** |
| Hồi quy | Cài đè v1.0.11 → v1.0.12 giữ dữ liệu (mức giao diện) PASS; ngân sách/bản ghi/đồng hồ/xuất JSON/xoay màn hình/nhập thủ công/xóa toàn bộ/cài mới PASS; đồng hồ → lưu → Nhật ký PASS; logcat 0 `FATAL`/ANR của app; 5 tab × 411dp/320dp × cỡ chữ 1.0/1.3 chụp lại |
| Môi trường | AVD tắt bằng `emu kill`; WallyQA chỉ mở khi không có instance của Owner, tắt bằng `ldconsole quit --index 1`; không đụng LDPlayer của Owner, không dừng tiến trình dùng chung, không khóa/dữ liệu Owner, không gọi API thật |
| Ảnh giao | `out/wally_candidate_v1012/screenshots/` (5 ảnh 1080×2400 + `_contact_sheet_5_screens.jpg`); ảnh điều hướng Android 9/16 và luồng AI giả lập ở `out/wally_evidence_v1012/screens/` |

## BLOCKER — AI Live (cần Owner/Manager, đúng yêu cầu)
Live Eval v1.2 (≥ 38/40, critical 100%, nhóm G 7/7) **chưa chạy**. Cần: (1) **Owner tự nhập khóa API** trong ⚙ trên thiết bị của Owner (không qua chat; AI-Master không có khóa); (2) **quyền gọi API thật** cho phiên đánh giá; (3) cài đè đúng APK v1.0.12 (kiểm SHA). Kế hoạch v1.2 giữ nguyên (`0b5144c8…17ea0`); các case H cho câu của Owner ở `WALLY_LIVE_EVAL_ADDENDUM_V1012.md`. Các phần độc lập không bị chặn đã hoàn tất.

## Giới hạn / chưa kiểm chứng (công khai)
- Chưa `AI_LIVE_PASS`; luồng đề xuất → xác nhận → sổ với AI **thật** trên candidate: NOT_TESTED. Mức harness/đơn vị không thay thế.
- Chưa đo trên điện thoại thật, Android 10–14, `KBD-01`, F6 smoke thật; bàn phím thật trên máy không chuyển inset: thanh tab vẫn hiện (an toàn, không nhường chỗ).
- Khoảng trống NL còn hở: chỉ nêu giờ bắt đầu, qua nửa đêm, gõ không dấu cho "đến giờ" (`WALLY_NL_COVERAGE_V1012.md` G6–G9).
- Build không tái lập từng byte: mọi bằng chứng gắn với đúng một SHA ở trên (các lần build thử khác ở `candidate_build/`, đã loại; bằng chứng của bản thử đầu ở `superseded_rebuild1/`).
- Không có Independent Review độc lập cho đợt này: các sửa chỉ được tự kiểm chứng (test + runtime).

## Chưa tuyên bố (không đổi)
`DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED`. Không merge `main`, không push thêm (nhánh git `wally-v1.0.12-ai-nav-fix` chỉ có ở máy, thay đổi chưa commit).

## Việc tiếp theo (Owner)
`WALLY_OWNER_CHECKLIST_V1012.md` (10 bước) trên điện thoại thật với đúng SHA-256 ở trên; sau đó Live Eval v1.2 + phụ lục.
