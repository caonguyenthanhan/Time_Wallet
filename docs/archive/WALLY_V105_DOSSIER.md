# WALLY v1.0.5 — Hồ sơ candidate (chỉ mục)

**Trạng thái:** CANDIDATE_UNVERIFIED · **Ngày:** 2026-11-13 · **Giai đoạn tiếp theo:** nghiệm thu thiết bị và đánh giá AI thật (không lập thêm kế hoạch phát triển).
**Không tuyên bố:** DEVICE_PASS, AI_LIVE_PASS, SPEC_ACCEPTED. **F6: UNRESOLVED.**

## 1. Artifact (không đổi trong giai đoạn này)

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_origin/TimeWallet-MVP-v1.0.5-WALLY-ORIGIN-candidate-debug.apk` |
| SHA256 | `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` |
| Package / version | `com.timewallet.mvp` · versionCode 6 · versionName 1.0.5 · targetSdk 35 |
| Chữ ký | Debug keystore, chứng chỉ SHA-256 `402f1d41…2530d`, trùng các bản trước ⇒ cài đè được |
| Room | v3, không migration mới |
| Nguồn | Khớp 81/81 file với `out/wally_candidate_origin/MANIFEST_SHA256_after.txt` (kiểm lại 2026-11-13) |
| Rollback | `out/wally_origin_snapshot_pre/source_v1.0.4_pre_ORIGIN.tar` |

Các candidate trước giữ nguyên để đối chiếu: v1.0.4 `338ddc3426beff52a54f9d656cdf7bdb6df349deb04d88b7912dfe4d64611600`, v1.0.3 `fd058632ec39a8f9184a07ce9c6a6a7808c17a7e5fe70e09787ad55dd598dcb2`, v1.0.2 `49a83f858dcbaf47130c244c847e7d69809c8ab09a44a8f21163917134a89e8c`.

## 2. Tài liệu

| Tài liệu | Nội dung |
|---|---|
| `WALLY_QA_REPORT_ORIGIN.md` | Audit origin và redirect; lỗ hổng gắn khóa theo cổng đã sửa (RED/GREEN); đính chính báo cáo 8 tầng |
| `WALLY_QA_REPORT_8LAYER.md` | 8 tầng, prompt cache, metrics, quyết định không cache dữ liệu, ViewModel/BatchConfirm/xóa nguyên tử |
| `WALLY_QA_REPORT_APIFIRST.md` | Chuyển sang API-first, guard, hợp đồng AI |
| `WALLY_QA_REPORT.md` | Báo cáo v1.0.2 (một phần đã bị thay thế, giữ để đối chiếu) |
| `WALLY_TECHNICAL_SPEC.md` | Đặc tả kỹ thuật: sơ đồ, use case, action case |
| `WALLY_LIVE_EVAL_PLAN.md` (+ `.sha256`) | **Kế hoạch đánh giá AI thật, catalog v1.1 đã đối chiếu, ngưỡng của Manager, đóng băng** |
| `WALLY_OWNER_CHECKLIST_APIFIRST.md` | Checklist thiết bị 10 bước (A13, A14, A17, F5, F6) |

Evidence: `out/wally_evidence_origin/` (RED trên v1.0.4, GREEN, đột biến, 224 test), `out/wally_evidence_8layer/`, `out/wally_evidence_liveplan/` (kiểm chứng expected của kế hoạch đánh giá).

## 3. Kết quả kiểm chứng đã có (JVM và mock; không phải thiết bị)

- **224/224 test PASS**, 0 lỗi, 0 skip. Golden 66/66, holdout 46/46 (chỉ chứng minh bộ phân tích cục bộ, **không** thay cho đánh giá AI thật).
- Origin: khóa gắn với scheme+host+cổng; redirect không được theo (kiểm bằng HttpURLConnection thật và hai máy chủ cục bộ); chốt kiểm origin trước khi mở kết nối; một điểm thoát mạng duy nhất.
- API-first: "Học 45p" gọi API (1 request); lỗi không có fallback cục bộ; mọi ghi qua `BatchConfirm` sau xác nhận.
- Prompt cache: tiền tố tĩnh ổn định; nhà cung cấp **UNVERIFIED**; cache hit NOT_TESTED.

## 4. Còn thiếu để nghiệm thu (cần Owner)

| Hạng mục | Tài liệu hướng dẫn | Trạng thái |
|---|---|---|
| Cài đè không mất dữ liệu (A14), F5 trên giao diện thật, Manual Entry và các màn hình chính, giao diện không overflow (A13, A17) | `WALLY_OWNER_CHECKLIST_APIFIRST.md` | NOT_TESTED |
| Đánh giá AI thật: ≥ 38/40, bất biến 100%, SC ×3, p50/p95, token, cache | `WALLY_LIVE_EVAL_PLAN.md` | NOT_TESTED (cần Owner tự nhập khóa trên điện thoại) |
| F6: điều tra bằng evidence | Nhật ký lỗi trong ⓘ + bảng F6 trong kế hoạch đánh giá | UNRESOLVED; nếu không tái hiện: báo NOT_REPRODUCED kèm điều kiện và số lần thử |

## 5. Vấn đề đã biết (không chặn thử nghiệm, chưa sửa theo chỉ thị giữ nguyên v1.0.5)

| # | Mô tả | Mức | Ghi chú |
|---|---|---|---|
| K1 | Hủy đề xuất chỉ bằng **cụm từ** (không có thời lượng hay số thứ tự) khi các draft đến từ **một** câu thì bị **hỏi lại** thay vì hủy đúng draft, vì `rawInput` của đề xuất AI là cả câu | Thấp (không mất dữ liệu) | Tái hiện bằng `ProposalLifecycle.resolve` (`out/wally_evidence_liveplan/eval_plan_expected_probe.txt`). Dạng có thời lượng ("Hủy ghi nhận 30 phút đọc sách"), số thứ tự, "Hủy tất cả" đều đúng. Quan sát O1 trong kế hoạch đánh giá. **Quyết định sửa thuộc Manager sau đợt đánh giá** |
| K2 | Bộ đếm "lệnh cục bộ" và nhãn `CONTROL` trong chẩn đoán luôn bằng 0 trên máy thật | Rất thấp | Lệnh hủy xử lý trước client; không ảnh hưởng an toàn |
| K3 | Khóa đã lưu cùng Base URL có cổng riêng sẽ bị giữ lại sau khi cài đè; phải nhập lại khóa | Thấp, chủ ý | Cấu hình mặc định (cổng 443) không ảnh hưởng |

Giới hạn khác (không có thiết bị/AI thật, Keystore thật, hoàn tác giao dịch Room, redirect trên Android, tokenizer thật) nằm trong từng báo cáo ở mục 2.

## 6. Quy tắc giai đoạn tiếp theo

Chỉ sửa code khi có lỗi thực tế, regression hoặc vấn đề bảo mật được chứng minh bằng evidence từ thiết bị hoặc đánh giá AI thật. Không build APK mới chỉ để tăng số test. Không mở Wave/Gate mới. Không xin credential qua chat. Không emulator/ADB.
