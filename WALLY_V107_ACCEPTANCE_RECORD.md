# WALLY v1.0.7 — Hồ sơ liên kết kết quả kiểm thử với SHA-256 candidate (hồ sơ riêng)

Mục đích: mọi kết quả kiểm thử (Reviewer, Owner thiết bị, Live Eval v1.2) chỉ có giá trị cho **đúng binary** ghi ở đây. Không chuyển kết quả giữa các binary, kể cả cùng versionCode.

## 1. Binary hiện hành

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v107/TimeWallet-MVP-v1.0.7-WALLY-HOTFIX-candidate-debug.apk` |
| **SHA-256** | `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61` |
| Package / version | `com.timewallet.mvp` · versionCode **8** · versionName **1.0.7** |
| Cert ký (debug) | `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (trùng v1.0.6, v1.0.5) |
| Nguồn | `source_v1.0.7.tar` `8f733b0e73fb336603337a4452c411978840be134d8e5ff2354b42159326dde0`; manifest 103 dòng |
| Hồ sơ kỹ thuật | `WALLY_V107_DOSSIER.md` |
| Live Eval | `WALLY_LIVE_EVAL_PLAN_v1.2.md` SHA-256 `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0` (giữ nguyên, expected không đổi) |

## 2. Binary KHÔNG được dùng làm bằng chứng cho v1.0.7

| Binary | SHA-256 | Lý do |
|---|---|---|
| v1.0.7 vòng 1 (`superseded_round1/`) | `a8f6727f42992a3b0afa43b52a3644ef2ab936f527a80424d3ce05974143b11f` | Đã thay thế; khác code ở `WallyAiGuards.kt`, `WallyChatReducer.kt` |
| v1.0.6 | `0d4e722bd385e88309c3658c85e7be3dd2a233017045f247c144bcf7968bc09f` | Binary khác; kể cả kết quả thiết bị/Live Eval nếu đã chạy |
| v1.0.5 | `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` | Binary khác |

Một dòng kết quả có SHA-256 khác `3df0fee7…c61` là **vô hiệu**, phải chạy lại.

## 3. Cổng nghiệm thu (trạng thái hiện tại, đều chưa có kết quả)

| Cổng | Trạng thái | Ghi chú |
|---|---|---|
| JVM (unit test) | **PASS** trên binary này | 341/341 trong cây, 343/343 kèm 2 test ngoài cây; golden 66/66; holdout 46/46 (`out/wally_evidence_v107/final*/`) |
| Reviewer re-check mục tiêu | NOT_TESTED | gói tái kiểm: `WALLY_V107_DOSSIER.md` mục 6 |
| DEVICE_PASS | NOT_TESTED | `WALLY_OWNER_CHECKLIST_V107.md` |
| AI_LIVE_PASS | NOT_TESTED | chỉ Owner tự nhập khóa; `WALLY_LIVE_EVAL_PLAN_v1.2.md` |
| SPEC_ACCEPTED | NO | |
| F6 | UNRESOLVED | chưa có bằng chứng nguyên nhân |

## 4. Mẫu ghi kết quả (điền khi chạy; mỗi lần chạy phải ghi SHA-256 thực của APK đã dùng)

### 4.1 Reviewer re-check mục tiêu

| Trường | Giá trị |
|---|---|
| Ngày / Reviewer | |
| SHA-256 APK đã kiểm (`sha256sum`) | `________________` (phải = `3df0fee7…c61`) |
| Kết quả `F-V105-03` | PASS / FAIL — ghi câu thử |
| Kết quả `V106-N1` (probe gốc 14 mẫu `ADV_REPLYGUARD`) | PASS / FAIL — số mẫu lọt: `__/14` (prop0 / prop1 / chỉ-AI có dòng trạng thái) |
| Kết quả `V106-N2` (BOM, tổng thời lượng) | PASS / FAIL |
| Phát hiện mới | |

### 4.2 Owner — thiết bị (`WALLY_OWNER_CHECKLIST_V107.md`)

| Trường | Giá trị |
|---|---|
| Ngày / thiết bị / Android | |
| SHA-256 APK đã cài (kiểm trên máy hoặc trước khi chép sang) | `________________` |
| `versionName` hiển thị ở ⓘ | `Time Wallet v1.0.7` |
| Kết quả từng mục | theo checklist |
| F6 | UNRESOLVED / NOT_REPRODUCED (ghi số lần thử) / log đính kèm |

### 4.3 Live Eval v1.2 (nếu Owner chạy)

| Trường | Giá trị |
|---|---|
| Ngày bắt đầu / múi giờ máy | |
| SHA-256 APK đã cài | `________________` |
| SHA-256 `WALLY_LIVE_EVAL_PLAN_v1.2.md` đã dùng | `________________` (phải = `0b5144c8…17ea0`) |
| Lượt 1: số câu đạt / 40 | `__/40` (cần ≥ 38) |
| Nhóm G (T7) | `__/7` (cần 7/7) |
| Bất biến S1–S9 | vi phạm: `__` (cần 0) |
| Case SC trượt phần an toàn ở bất kỳ lượt nào | `__` (cần 0) |
| Latency p50 / p95; token; cache | |
| Kết luận | ĐẠT / KHÔNG ĐẠT (chưa phải AI_LIVE_PASS cho tới khi Owner nghiệm thu) |
