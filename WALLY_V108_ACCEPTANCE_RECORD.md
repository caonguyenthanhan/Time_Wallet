# WALLY v1.0.8 — Hồ sơ liên kết kết quả kiểm thử với SHA-256 candidate

Mọi kết quả (Reviewer, Owner thiết bị, Live Eval v1.2) chỉ có giá trị cho **đúng binary** dưới đây. Không chuyển kết quả giữa các binary, kể cả cùng versionCode hoặc cùng kích thước tệp.

## 1. Binary hiện hành

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v108/TimeWallet-MVP-v1.0.8-WALLY-HOTFIX-candidate-debug.apk` |
| **SHA-256** | `e13c35f49ae474a57c79c5be269307cb8e5f7d3a410e75528a9e553683cd62a5` |
| Package / version | `com.timewallet.mvp` · versionCode **9** · versionName **1.0.8** |
| Cert ký (debug) | `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (trùng v1.0.7/v1.0.6/v1.0.5) |
| Nguồn | `source_v1.0.8.tar` `91e6730160bf6b9d55b97059c2e747e967b92197ab13f3eb7b6d1a4a4b17be20`; manifest 105 dòng |
| Hồ sơ | `WALLY_V108_DOSSIER.md`; addendum `WALLY_V107_ADDENDUM_F3.md` |
| Live Eval | `WALLY_LIVE_EVAL_PLAN_v1.2.md` `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0` (giữ nguyên, expected không đổi) |

## 2. Binary KHÔNG dùng làm bằng chứng cho v1.0.8

| Binary | SHA-256 |
|---|---|
| v1.0.7 vòng 2 | `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61` |
| v1.0.7 vòng 1 (kích thước **trùng** v1.0.8: 11.785.442 byte; nội dung khác) | `a8f6727f42992a3b0afa43b52a3644ef2ab936f527a80424d3ce05974143b11f` |
| v1.0.6 | `0d4e722bd385e88309c3658c85e7be3dd2a233017045f247c144bcf7968bc09f` |
| v1.0.5 | `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` |

Dòng kết quả mang SHA-256 khác `e13c35f4…62a5` là **vô hiệu**.

## 3. Cổng nghiệm thu

| Cổng | Trạng thái | Ghi chú |
|---|---|---|
| JVM | **PASS** trên binary này | 357/357 trong cây; 359/359 kèm 2 test ngoài cây; golden 66/66; holdout 46/46 |
| Reviewer re-check (chỉ V106-N1 + toàn vẹn artifact) | NOT_TESTED | gói tái kiểm ở `WALLY_V108_DOSSIER.md` mục 5 |
| DEVICE_PASS | NOT_TESTED | `WALLY_OWNER_CHECKLIST_V108.md` |
| AI_LIVE_PASS | NOT_TESTED | Owner tự nhập khóa; Live Eval v1.2 |
| SPEC_ACCEPTED | NO | |
| F6 | UNRESOLVED | |

## 4. Mẫu ghi kết quả (ghi SHA-256 thực của APK đã dùng ở mỗi lần)

**Reviewer:** ngày/người: `____` · SHA-256 APK đã kiểm: `____` (= `e13c35f4…62a5`) · probe `ADV_REPLYGUARD` gốc, số câu còn hiện ở lượt giao dịch: `__/14` · MIXED giữ query: `có/không` · số liệu query không gồm draft: `có/không` · toàn vẹn artifact (APK, manifest 105/105, v1.0.7 nguyên vẹn): `đạt/không` · phát hiện mới: `____`.

**Owner thiết bị:** ngày / thiết bị / Android: `____` · SHA-256 APK đã cài: `____` · ⓘ ghi `Time Wallet v1.0.8`: `có/không` · kết quả theo `WALLY_OWNER_CHECKLIST_V108.md`: `____` · F6: `UNRESOLVED / NOT_REPRODUCED (số lần) / log`.

**Live Eval v1.2 (nếu chạy):** SHA-256 APK đã cài: `____` · SHA-256 plan đã dùng: `____` (= `0b5144c8…17ea0`) · T1 `__/40` (≥ 38) · T7 nhóm G `__/7` · vi phạm S1–S9 `__` · SC trượt phần an toàn `__` · p50/p95 · kết luận: `ĐẠT/KHÔNG ĐẠT`.
