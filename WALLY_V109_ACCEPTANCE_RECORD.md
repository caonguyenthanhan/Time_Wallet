# WALLY v1.0.9 — Hồ sơ liên kết kết quả kiểm thử với SHA-256 candidate (P0 F6)

Mọi kết quả (Reviewer, Owner thiết bị, Live Eval v1.2) chỉ có giá trị cho **đúng binary** dưới đây. Không chuyển kết quả giữa các binary, kể cả cùng versionCode hoặc cùng kích thước tệp.

## 1. Binary hiện hành

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v109/TimeWallet-MVP-v1.0.9-WALLY-HOTFIX-candidate-debug.apk` |
| **SHA-256** | `0f5f23d9b7eb718367d3d87a26bb3cbbc7fb2884e124163fc5b980bd116ca4e2` |
| Package / version | `com.timewallet.mvp` · versionCode **10** · versionName **1.0.9** |
| Cert ký (debug) | `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (trùng v1.0.8/v1.0.7/v1.0.6/v1.0.5) |
| Nguồn | `source_v1.0.9.tar` `add321024c47d8b39bde95fdb752e2a0af45ee97780d6c4846ecee8b2a7b37bf`; manifest 110 dòng, 110/110 khớp |
| Hồ sơ | `WALLY_V109_DOSSIER.md`; gói Reviewer 5 điểm `WALLY_V109_REVIEWER_RECHECK_PACK.md` (MANAGER DECISION: Owner cài ngay, không đợi Reviewer) |
| Live Eval | `WALLY_LIVE_EVAL_PLAN_v1.2.md` `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0` (giữ nguyên, expected không đổi) |

## 2. Binary KHÔNG dùng làm bằng chứng cho v1.0.9

| Binary | SHA-256 | Ghi chú |
|---|---|---|
| v1.0.8 | `e13c35f49ae474a57c79c5be269307cb8e5f7d3a410e75528a9e553683cd62a5` | **có lỗi crash `(?U)`** (46 chuỗi `(?U)` trong dex) |
| v1.0.7 vòng 2 | `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61` | cùng lỗi |
| v1.0.7 vòng 1 | `a8f6727f42992a3b0afa43b52a3644ef2ab936f527a80424d3ce05974143b11f` | cùng lỗi |
| v1.0.6 | `0d4e722bd385e88309c3658c85e7be3dd2a233017045f247c144bcf7968bc09f` | cùng lỗi |
| v1.0.5 | `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` | không khớp số dòng log; cũng có `(?U)` |

Dòng kết quả mang SHA-256 khác `0f5f23d9…ca4e2` là **vô hiệu**.

## 3. Cổng nghiệm thu

| Cổng | Trạng thái | Ghi chú |
|---|---|---|
| JVM | **PASS** trên binary này | 383/383 trong cây; 385/385 kèm 2 test ngoài cây; golden 66/66; holdout 46/46; 0 lỗi/bỏ qua |
| ICU4C thật (Windows ICU 68.2, không phải Android) | **PASS** | 146/146 mẫu biên dịch; 341.056 cặp mẫu×câu, 0 lệch JVM↔ICU, 0 lỗi chạy. RED v1.0.8: 46/98 mẫu lỗi `U_REGEX_RULE_SYNTAX` tại offset 3 |
| Mô phỏng tái hiện crash trên v1.0.8 | **TÁI HIỆN** | chữ ký khớp log (`red_crash_signature.txt`); là mô phỏng, không phải Android |
| Binary | **PASS** | dex v1.0.9: 0 chuỗi `(?U)` (v1.0.8: 46); quyền chỉ INTERNET; quét runtime AI không đổi (không mô hình cục bộ, không tải model) |
| Reviewer re-check (§10 của dossier) | NOT_TESTED | |
| **Emulator (LDPlayer Android 9, QA instance, ADB/ldconsole/UI Automator)** | **PARTIAL — `F6_REGEX_EMULATOR_VERIFIED`** | `out/wally_evidence_v109/ldplayer_qa/LDPLAYER_QA_REPORT.md`. v1.0.8 sập đúng chữ ký log Owner (`PatternSyntaxException … near index 3`, `(?U)`, `CancelCommandParser.<clinit>` PL:29) trên Android 9 thật; v1.0.9 (SHA `0f5f23d9…ca4e2`) không còn, qua 2 vòng khởi động nguội + vòng chặn mạng. Nhập thủ công/đồng hồ/ngân sách/Nhật ký/Dashboard PASS. **Lỗi giao diện:** hàng Ngân sách ở màn Phân tích vỡ ở 390dp (nặng hơn ở chữ 1.3); tên Nhật ký bị cắt. Bàn phím mềm và mọi luồng cần khóa AI: NOT_TESTED. Đây là emulator, không phải điện thoại thật |
| **Android thật / DEVICE_PASS** | **NOT_TESTED** | `WALLY_OWNER_CHECKLIST_V109.md` (điện thoại thật của Owner) |
| AI_LIVE_PASS | NOT_TESTED | Owner tự nhập khóa; Live Eval v1.2 |
| SPEC_ACCEPTED | NO | |
| **F6 — chữ ký `EIIE ← PatternSyntaxException ← CancelCommandParser.<clinit>`** | **FIX_CANDIDATE** (trước đó: DEVICE_REPRODUCED trên v1.0.6–v1.0.8) | chỉ chuyển FIXED khi Owner xác nhận ở thiết bị (checklist bước 5) |
| F6 nói chung | UNRESOLVED | không loại trừ nguyên nhân crash khác |

## 4. Mẫu ghi kết quả (ghi SHA-256 thực của APK đã dùng ở mỗi lần)

**Reviewer:** ngày/người: `____` · SHA-256 APK đã kiểm: `____` (= `0f5f23d9…ca4e2`) · `grep -R "(?U)" app/src/main` chỉ còn chú thích: `có/không` · chạy lại ICU harness (GREEN `0` lệch / RED `46` lỗi): `____` · toàn vẹn artifact (APK, manifest 110/110, v1.0.5–v1.0.8 nguyên vẹn): `đạt/không` · phát hiện mới: `____`.

**Owner thiết bị:** ngày / thiết bị / Android: `____` · phiên bản ⓘ **trước** khi cài: `____` · SHA-256 APK đã cài: `____` · ⓘ ghi `Time Wallet v1.0.9`: `có/không` · câu đã làm app dừng trước đây: `____` · gửi lại đúng câu đó (số lần / có dừng không): `____` · kết quả theo `WALLY_OWNER_CHECKLIST_V109.md`: `____` · F6 (chữ ký này): `FIXED / NOT_FIXED (kèm Nhật ký lỗi)`.

**Live Eval v1.2 (nếu chạy):** SHA-256 APK đã cài: `____` · SHA-256 plan đã dùng: `____` (= `0b5144c8…17ea0`) · T1 `__/40` (≥ 38) · T7 nhóm G `__/7` · vi phạm S1–S9 `__` · SC trượt phần an toàn `__` · p50/p95 · kết luận: `ĐẠT/KHÔNG ĐẠT`.
