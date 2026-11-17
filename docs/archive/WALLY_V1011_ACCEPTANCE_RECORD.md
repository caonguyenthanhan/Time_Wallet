# WALLY V1.0.11 ACCEPTANCE RECORD

- **Candidate:** `TimeWallet-MVP-v1.0.11-WALLY-UIPOLISH-candidate-debug.apk` — `out/wally_candidate_v1011/`
- **Full SHA-256:** `af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed`
- **Application ID / Version:** `com.timewallet.mvp`, `versionName = 1.0.11`, `versionCode = 12`
- **Baseline rollback (không bị ghi đè):** `v1.0.10` — `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b`
- **Live Eval Plan v1.2 SHA-256:** `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0` (không thay đổi)
- **Quyền / phạm vi:** Owner Authorization "v1.0.11 UI POLISH" + Owner Directive "Agent Work / Product Quality First"; chỉ UI/UX 5 màn hình; Single Writer = AI-Master.

## Cổng kiểm tra trước khi giao Owner

| Cổng / hạng mục | Trạng thái | Chi tiết |
|---|---|---|
| Compile + `assembleDebug` (sạch, `--rerun-tasks`) | **PASS** | `out/wally_evidence_v1011/unit_final/console.txt`, `chain_log.txt` (bước 1–2) |
| Unit test (trong cây mã) | **PASS** | `39` suite, `407` test, `0` lỗi (390 cũ + 17: 14 `V1011UiPolishContractTest` + 3 `V1011UiLogicTest`; không sửa test cũ nào) — `unit_final/` |
| Unit test + holdout ngoài | **PASS** | `40` suite, `409` test, `0` lỗi — `unit_final_with_external/` |
| Chữ ký / tương thích cài đè | **PASS** | cùng chứng chỉ debug với v1.0.10 (`SIGNING_CERT_COMPARE.txt`); `versionCode 11 → 12` cài đè thành công |
| DEX không chứa `(?U)` (F6) | **PASS** | `apk_dex_check_v1.0.11.txt` |
| Cài đè giữ dữ liệu (dữ liệu do chính v1.0.10 tạo) | **PASS (mức giao diện)** | `upgrade_test.json`: gỡ app → cài v1.0.10 → 3 dòng mẫu + bản ghi thủ công `MARKER-UPG-37` + ngân sách 5h/tuần tạo trong v1.0.10 → `adb install -r` v1.0.11 → toàn bộ còn nguyên. Chưa phủ: dòng dự kiến/đang chạy, khóa AI/Keystore, so sánh mức CSDL. Lần chạy đầu trong chuỗi cuối báo `False` là lỗi của harness (bước nạp dữ liệu mẫu/chuyển tab bị bỏ vì chạm khi app còn khởi động lạnh 4–6 s trên AVD; đo A/B cho thấy v1.0.10 và v1.0.11 khởi động lạnh ngang nhau: `startup_ab.json`); đã sửa harness chờ khung hình đầu rồi chạy lại: mọi kiểm tra `True`, thẻ ngân sách trước/sau giống hệt (30%, còn 3h30p). |
| Runtime AVD `TimeWallet_QA` — 5 tab × (cài mới, có dữ liệu, sau xóa toàn bộ) | **CAPTURED** | `screens/final_fresh_*`, `final_data_*`, `final_empty_*` |
| Ma trận 411dp/320dp × cỡ chữ 1.0/1.3 (có ảnh cuộn) | **CAPTURED** | `screens/final_mx_*` |
| Luồng chức năng trên APK cuối | **PASS (tự động qua ADB/UI Automator; từng bước có dòng True trong log)** | `chain_log_part2.txt` mục 6–8 (cùng kết quả ở `chain_log.txt`), `screens/final_flow_*`, `final_extra_*`: sửa/xóa ngân sách (xác nhận + Hủy giữ nguyên), sửa/xóa bản ghi (xác nhận + Hủy giữ nguyên), đồng hồ chạy → hủy → chờ, Xuất JSON qua menu, thêm giờ thủ công, bong bóng Wally qua đổi tab và xoay màn hình (landscape đọc được, về dọc còn bong bóng; kiểm bằng nút UI thật), hộp Cấu hình AI / Thông tin, nhật ký hai ngày + tiêu đề dài |
| Đồng hồ: bắt đầu → chạy >1 phút → "Kết thúc & lưu" → Nhật ký (APK cuối) | **PASS (runtime AVD)** | `final_timer_save.json`, `screens/final_timer_save_*`: tiêu đề duy nhất `TIMER-SAVE-VERIFY`, hiện nút Hủy phiên / Kết thúc & lưu khi chạy, toast xác nhận, Nhật ký thêm đúng 1 dòng `0h 01` nhãn "Đã đo" kèm mốc giờ, đồng hồ về trạng thái chờ |
| Wally: đề xuất → Xác nhận → sổ cái (giao diện trên candidate) | **NOT_TESTED** | Cần phản hồi AI thật; candidate không có đường giả lập và bản harness không nối ViewModel nên **không** được tính PASS. Phủ gián tiếp, không thay thế: test đơn vị `WallyApiFirstGuardsTest` (A03/A08: đề xuất không ghi gì khi chưa xác nhận, xác nhận hai lần chỉ ghi một lần) chạy trên `BatchConfirm.run` thật; cùng đường ghi `BatchConfirm.run` được chạy ở runtime bởi "Thêm giờ thủ công" và lưu đồng hồ |
| Logcat AVD | **PASS (của app)** | 0 `FATAL EXCEPTION` / `PatternSyntaxException` / `ExceptionInInitializerError` / `GUARD_UNAVAILABLE`, 0 ANR của app (cả hai lần chạy); ANR còn lại là của tiến trình hệ thống lúc emulator khởi động: lần chạy đầu 8 dòng (`chain_log.txt`), lần chạy lại 4 dòng (`logcat_avd_full.txt`, chỉ phủ phiên AVD khởi động lại) |
| LDPlayer `WallyQA` (Android 9, 390dp): ảnh UI thật của APK cuối | **CAPTURED, kèm 1 phát hiện chưa sửa** | `docs/screenshots/ldplayer-android9/`, `out/wally_evidence_v1011/ldplayer_wallyqa/`. trên instance này thanh tab dưới cùng **biến mất ngay khi mở tab Wally** dù không có bàn phím (`mInputShown=false`); phím Back thoát hẳn ra launcher nên chỉ rời Wally bằng cách mở lại app. Nguyên nhân khả dĩ: điều kiện ẩn thanh tab khi `WindowInsets.isImeVisible` (mới ở v1.0.11) trả về đúng sai trên môi trường này; trên AVD Android 16 không xảy ra. Chưa kiểm trên điện thoại thật. APK `af50d6cc…` không đổi; cần quyết định của Owner/Manager có sửa và build lại hay không. Ảnh: `docs/screenshots/ldplayer-android9/` |
| Hai lần chạy chuỗi bằng chứng | **Ghi chú trung thực** | Chuỗi bằng chứng đã chạy **hai lần trên cùng một APK** (không build lại): lần đầu `chain_log.txt` (cũng là nơi build + 407/409 test) báo cài đè `False` do lỗi harness (chạm khi app còn khởi động lạnh 4–6 s trên AVD nên bước nạp dữ liệu mẫu bị bỏ) và vì vậy ảnh ma trận/dữ liệu của lần đó ở trạng thái dữ liệu thưa; đã sửa harness rồi chạy lại bước 3–11 trên AVD khởi động lại: `chain_log_part2.txt`. Các ảnh `final_*`/`upgrade_*`/`delivery_*` hiện có và `logcat_avd_full.txt` là của lần chạy lại |
| Thẻ đề xuất Wally | **Chỉ dữ liệu giả lập** | `screens/harness2_cards_*` (bản harness tạm, không phải candidate); chưa có ảnh với phản hồi AI thật |
| Independent Product Review (chỉ-đọc) | **Vòng 1, 2: `FIX_REQUIRED` → đã sửa. Vòng 3 (workflow 5 góc nhìn + phản biện): 0 P0/P1, 2 P2, 44 P3 → đã xử lý. Vòng 4 (kiểm toán xác nhận, 4 góc nhìn): 12 phát hiện (10 vấn đề riêng biệt), 0 P0/P1, 2 P2 riêng biệt → đã sửa. Vòng 5 (cổng cuối, 27 agent chỉ-đọc, 3 người phản biện cho mỗi phát hiện; một phần bị gián đoạn do hết hạn mức nên 11 người phản biện không chạy được): 0 P0/P1; đã xác nhận 1 P2 (nhãn nút "Lắc xúc xắc" chạm viền ở 320dp×1.3) và các P3 (nhãn biểu đồ, gợi ý phản tư khi chưa có ngân sách, ghi chú tài liệu/bằng chứng) → đã sửa; các phát hiện chưa kịp phản biện được tôi kiểm tra trực tiếp trên mã nguồn/ảnh. **Manager quyết định không mở vòng 6: các sửa cuối (nút xúc xắc, làm tròn nhãn cột, gợi ý phản tư, tài liệu) chỉ được tự kiểm chứng bằng runtime AVD + test, chưa được reviewer độc lập xác nhận lại**** | `REVIEW_WALLY_V1011.md` |
| Owner điện thoại thật + `KBD-01` + F6 smoke + Live Eval v1.2 | **PENDING_OWNER** | theo `WALLY_OWNER_CHECKLIST_V1011.md` |

## Chưa được tuyên bố (không đổi so với trước)
`DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED` — cần điện thoại thật / khóa riêng của Owner. Emulator Android 16 và bản harness dữ liệu giả lập **không** thay thế điều đó.

## Ký nhận
- **Implementation & AVD verification:** hoàn tất (`WALLY_V1011_DOSSIER.md`).
- **Independent Reviewer:** xem `REVIEW_WALLY_V1011.md` (Reviewer không sửa mã nguồn production).
- **Owner Physical Phone & Live Eval v1.2:** `PENDING`
  - Thiết bị test: `...`
  - Kết quả giao diện / KBD-01 / F6 smoke: `...`
  - Kết quả Live Eval v1.2: `...`
