# WALLY v1.0.10 — MANAGER CLOSURE RECORD

- **Ngày:** 2026-11-15
- **Nguồn quyết định:** chỉ thị "MANAGER CLOSURE — v1.0.10" (bảy mục), ghi lại nguyên ý, không diễn giải thêm.
- **Phạm vi ghi nhận trong phiên này:** chỉ tài liệu/quy tắc. Không build, không sửa mã nguồn, không chạy emulator, không chạm instance của Owner.

## 1. Quyết định

| # | Quyết định của Manager | Trạng thái | Nơi ghi |
|---|---|---|---|
| 1 | Giữ nhãn `Phiên bản khi lỗi: Không xác định`. Không sửa string, không rebuild. | **ACCEPTED_DEVIATION** | mục 2 dưới đây |
| 2 | Candidate v1.0.10 **FROZEN**, giữ SHA-256 `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b` | **FROZEN** | mục 3 |
| 3 | Chấp nhận ngoại lệ QA trong `.agents/AGENTS.md` chỉ cho `WallyQA`, kèm giới hạn bổ sung | **ĐÃ GHI VÀO `.agents/AGENTS.md`** (mục 6–9 của ngoại lệ) | `.agents/AGENTS.md` |
| 4 | Sự cố `Ld9BoxSVC` giữ **OPEN** ở cấp audit; yêu cầu lệnh nguyên văn, thời điểm, lý do, phạm vi, evidence; không còn evidence → `UNVERIFIABLE`; không dựng lại log, không truy cập instance Owner | **OPEN** (đang chờ agent thực hiện) | `WALLY_V109_INCIDENT_ADDENDUM_OWNER_INSTANCE.md` §7 |
| 5 | Single Writer: một agent sửa working tree; Reviewer không sửa production source; chuyển writer phải có handoff | **ĐÃ GHI VÀO `.agents/AGENTS.md`** | mục 5 dưới đây |
| 6 | Manifest + tar v1.0.9/v1.0.10 là baseline có thẩm quyền; không rewrite lịch sử Git để tạo provenance giả | **ÁP DỤNG** | mục 4 |
| 7 | Không thêm build, code remediation hoặc emulator QA; không mở Wave/Gate mới | **ÁP DỤNG** | mục 7 |
| — | Chuyển Owner Acceptance ngay; audit sự cố không chặn kiểm thử APK trên điện thoại | **ĐÃ CHUYỂN** | mục 8 |

## 2. ACCEPTED_DEVIATION — nhãn phiên bản khi lỗi

- **Chỉ thị gốc của đợt remediation:** "Phiên bản phát sinh: không xác định" (cho log cũ không có `version=`).
- **Đã triển khai trong v1.0.10:** header `Thời điểm: … · Phiên bản khi lỗi: Không xác định` (`Diagnostics.kt`, hằng `UNKNOWN_VERSION_LABEL = "Không xác định"`).
- **Khác biệt:** chỉ ở câu chữ. Hành vi đúng như yêu cầu: log cũ **không** bị gán nhầm `v1.0.10`; stack trace giữ nguyên.
- **Quyết định:** chấp nhận; không sửa string; không rebuild. `WALLY_OWNER_CHECKLIST_V1010.md` đã dùng đúng nhãn này, không cần chỉnh.

## 3. Candidate FROZEN

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v1010/TimeWallet-MVP-v1.0.10-WALLY-HOTFIX-candidate-debug.apk` |
| SHA-256 | `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b` |
| Kích thước | 11,818,214 byte |
| Gói / phiên bản | `com.timewallet.mvp`, `versionName = 1.0.10`, `versionCode = 11` |
| Chứng chỉ ký | SHA-256 `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (khớp v1.0.9) |
| Source tar | `source_v1.0.10.tar`, SHA-256 `e895753d4c81fbf23fb3cbde7cbead49f3a43a616d5df9d4a3b2cf5f6cb27f65` |

Mọi thay đổi sau thời điểm này đối với APK hoặc `app/src` + `app/build.gradle.kts` là vi phạm đóng băng, trừ khi Manager mở lại bằng văn bản.

## 4. Baseline có thẩm quyền

- Baseline = **manifest + tar** của v1.0.9 và v1.0.10 (`out/wally_candidate_v109/`, `out/wally_candidate_v1010/`, snapshot `out/wally_v1010_snapshot_pre/`).
- Kho Git chỉ có một commit (`9b562bc`, "first commit") có trước các tệp v1.0.9 cuối cùng; working tree đang có thay đổi chưa commit. Vì vậy Git **không** phải baseline đáng tin cậy ở thời điểm này.
- **Không** rewrite lịch sử Git, không tạo commit hay tag hồi tố để làm ra "provenance" trông như có từ trước.

## 5. Single Writer

- **Quy tắc (đã ghi vào `.agents/AGENTS.md`):** một agent duy nhất có quyền sửa working tree tại một thời điểm; Reviewer không sửa production source; chuyển writer phải có handoff.
- **Trạng thái hiện tại:** vì candidate đã FROZEN và Manager không mở thêm việc sửa mã, **không agent nào có quyền sửa `app/src` / `app/build.gradle.kts`**. Chỉ được sửa tài liệu/hồ sơ nghiệm thu. Manager cần chỉ định writer nếu muốn mở lại.
- **Ghi chú trung thực:** trong đợt v1.0.10 có hai agent cùng chỉnh sửa cây mã (một trong số đó là phiên này, ở bước UI/diagnostics đầu), rồi agent kia tinh chỉnh tiếp. Không có văn bản handoff nào được lập vào thời điểm đó. Trạng thái cuối được xác nhận bằng so khớp tar, không bằng handoff.

### Mẫu handoff (dùng khi chuyển writer)

```
HANDOFF — Single Writer
Từ (writer cũ):            <agent>
Sang (writer mới):         <agent>
Thời điểm:                 <ngày giờ, múi giờ>
Người chỉ định:            <Manager/Owner>
Phạm vi được phép sửa:     <tệp/thư mục>
Trạng thái cây mã bàn giao: manifest SHA-256 <...>; tar SHA-256 <...>; APK SHA-256 <...> (nếu có)
Việc đang dở / không được tiếp tục: <...>
Xác nhận writer cũ ngừng ghi: <có/không + thời điểm>
```

## 6. Ngoại lệ QA `WallyQA` — giới hạn hiện hành

Nguyên văn thêm vào `.agents/AGENTS.md`: không chạm instance Owner (`index 0`); không stop/kill/restart tiến trình hoặc service LDPlayer dùng chung trên Windows, kể cả `Ld9BoxSVC`; không `adb kill-server` toàn cục (quy tắc cũ cấm mọi `adb kill-server`, giữ nguyên bản chặt hơn); nếu host service treo → báo Owner và dừng riêng QA bị ảnh hưởng, không tự xử lý tiến trình dùng chung.

## 7. Không làm thêm

Không build, không code remediation, không emulator QA, không mở Wave/Gate mới. Hồ sơ QA v1.0.10 trên `WallyQA` (24/24 UI, 4/4 chức năng) giữ nguyên là bằng chứng cuối; **không** được coi là `DEVICE_PASS`.

## 8. Owner Acceptance — chuyển ngay

Owner thực hiện theo `WALLY_OWNER_CHECKLIST_V1010.md` trên điện thoại thật với APK đúng SHA-256 ở mục 3. Chưa có kết quả nào từ điện thoại thật; các cờ dưới đây **chưa** được cấp:

| Cờ | Trạng thái |
|---|---|
| `DEVICE_PASS` | chưa cấp (chờ Owner) |
| `F6_DEVICE_VERIFIED` | chưa cấp (F6 vẫn mở trên thiết bị thật) |
| `KBD-01` | chưa kiểm (cần bàn phím mềm thật) |
| `AI_LIVE_PASS` | chưa cấp (Live Eval v1.2 cần khóa riêng của Owner và phép riêng) |
| `SPEC_ACCEPTED` | chưa cấp |

## 9. Sự cố còn mở

| Sự cố | Trạng thái | Chờ ai | Chặn nghiệm thu? |
|---|---|---|---|
| Thao tác lên instance Owner ngày 2026-11-14 (do phiên này) | Đã lập phụ lục §1–6; không thu thêm evidence | — | Không |
| `Ld9BoxSVC` bị xử lý ngày 2026-11-15 (không do phiên này) | **OPEN (audit)** | agent đã thực hiện: lệnh, thời điểm, lý do, phạm vi, evidence — hoặc `UNVERIFIABLE` | Không |
| `.prev` log rotation mới chỉ có unit test | Ghi nhận trong `REVIEW_WALLY_V1010.md` | Owner quan sát nếu gặp crash thứ hai | Không |
