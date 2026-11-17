# WAVE DIGEST — UI-POLISH v1.0.11 (gửi Manager)

**Ngày:** 2026-11-15 · **Single Writer:** AI-Master · **Quyền:** Owner Authorization "v1.0.11 UI POLISH" + Owner Directive "Agent Work / Product Quality First"

## Kết quả
| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v1011/TimeWallet-MVP-v1.0.11-WALLY-UIPOLISH-candidate-debug.apk` (11,867,366 byte) |
| SHA-256 | `af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed` |
| Phiên bản / ký | `versionName 1.0.11`, `versionCode 12`; cùng chứng chỉ debug với v1.0.10 (`402f1d41…2530d`); DEX không có `(?U)` |
| Rollback | APK v1.0.10 `1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b` giữ nguyên (đã kiểm hash) |
| Phạm vi | Chỉ giao diện 5 màn hình. 11 file sửa, 4 file mới (`UiKit.kt`, `DataManagementDialog.kt`, test `V1011UiPolishContractTest.kt`, test `V1011UiLogicTest.kt`), 0 xóa. `Rx`/`GuardHealth`/ViewModel/Room/Keystore/đồng hồ/phân tích **không đổi**; không test cũ nào bị sửa |
| Test | **407 test / 39 suite, 0 lỗi** (390 cũ + 17 mới); **409 / 40 suite** khi thêm bộ holdout ngoài |
| Runtime | AVD `TimeWallet_QA` (Android 16, `emulator-5580`): 5 tab × (cài mới, có dữ liệu, sau xóa toàn bộ), ma trận 411dp/320dp × cỡ chữ 1.0/1.3 (có ảnh cuộn), các luồng thêm giờ / đồng hồ chạy-hủy / xúc xắc / ngân sách (sửa, xóa có xác nhận) / sửa-xóa bản ghi / Xuất JSON / xóa toàn bộ / menu / xoay màn hình / bàn phím Gboard trên emulator; 112 ảnh `final_*` + 5 ảnh giao `delivery_*` (chụp thật từ emulator) |
| Cài đè | PASS mức giao diện: gỡ app → cài v1.0.10 → tạo dữ liệu bằng chính v1.0.10 (3 dòng mẫu, bản ghi `MARKER-UPG-37`, ngân sách 5h/tuần) → `adb install -r` v1.0.11 → còn nguyên (`upgrade_test.json`); chưa phủ khóa AI/Keystore, dòng dự kiến/đang chạy, so sánh mức CSDL. Lần chạy đầu trong chuỗi cuối báo `False` là lỗi của harness (bước nạp dữ liệu mẫu/chuyển tab bị bỏ vì chạm khi app còn khởi động lạnh 4–6 s trên AVD; đo A/B cho thấy v1.0.10 và v1.0.11 khởi động lạnh ngang nhau: `startup_ab.json`); đã sửa harness chờ khung hình đầu rồi chạy lại: mọi kiểm tra `True`, thẻ ngân sách trước/sau giống hệt (30%, còn 3h30p). |
| Đồng hồ → lưu → Nhật ký | **PASS (runtime AVD)**: chạy >1 phút → "Kết thúc & lưu" → Nhật ký +1 dòng `0h 01` "Đã đo" → đồng hồ về chờ (`final_timer_save.json`) |
| Wally đề xuất → Xác nhận → sổ cái (giao diện) | **NOT_TESTED** — cần phản hồi AI thật; không chuyển PASS từ harness. Chỉ có phủ gián tiếp: test đơn vị `WallyApiFirstGuardsTest` trên `BatchConfirm.run` thật + cùng đường ghi chạy ở runtime qua thêm giờ thủ công / lưu đồng hồ |
| AVD | `TimeWallet_QA` (`emulator-5580`): lần 1 tắt bằng `adb -s emulator-5580 emu kill` (không còn tiến trình AVD, `adb devices` rỗng); sau đó khởi động lại để chụp lại bằng chứng (chuỗi phần 2). Lần 2 **không** được tắt có kiểm soát bằng `emu kill`: khi phiên làm việc được tiếp tục thì emulator đã không còn chạy (bị kết thúc cùng phiên trước). Đã kiểm sau đó: không còn tiến trình `emulator`/`qemu`/`crashpad`, `ldconsole runninglist` rỗng, và đã xóa file khóa cũ (`hardware-qemu.ini.lock`, `multiinstance.lock`) của đúng tiến trình AVD đã chết. Máy chủ adb (adb của LDPlayer) không còn chạy lúc đó và được `adb devices` tự khởi động lại — không dùng `kill-server`. Không đụng LDPlayer của Owner, không dừng/khởi động lại tiến trình dùng chung |
| Logcat | 0 `FATAL EXCEPTION`, 0 ANR của app, 0 `PatternSyntaxException`/`ExceptionInInitializerError`/`GUARD_UNAVAILABLE` (ANR còn lại là của tiến trình hệ thống lúc emulator khởi động, không thuộc app: lần chạy đầu 8 dòng (`chain_log.txt`), lần chạy lại 4 dòng (`logcat_avd_full.txt`, chỉ phủ phiên AVD khởi động lại)) |
| Hai lần chạy chuỗi bằng chứng | Chuỗi bằng chứng đã chạy **hai lần trên cùng một APK** (không build lại): lần đầu `chain_log.txt` (cũng là nơi build + 407/409 test) báo cài đè `False` do lỗi harness (chạm khi app còn khởi động lạnh 4–6 s trên AVD nên bước nạp dữ liệu mẫu bị bỏ) và vì vậy ảnh ma trận/dữ liệu của lần đó ở trạng thái dữ liệu thưa; đã sửa harness rồi chạy lại bước 3–11 trên AVD khởi động lại: `chain_log_part2.txt`. Các ảnh `final_*`/`upgrade_*`/`delivery_*` hiện có và `logcat_avd_full.txt` là của lần chạy lại |
| Independent Product Review (chỉ-đọc) | vòng 1–2 `FIX_REQUIRED` → đã sửa; vòng 3 (workflow 5 góc nhìn + phản biện, 164 agent): 0 P0/P1, 2 P2, 44 P3 → đã xử lý; vòng 4 (kiểm toán xác nhận, 55 agent): 12 phát hiện (10 vấn đề riêng biệt), 0 P0/P1, 2 P2 riêng biệt → đã sửa; vòng 5 (cổng cuối, 27 agent chỉ-đọc, 3 người phản biện cho mỗi phát hiện; một phần bị gián đoạn do hết hạn mức nên 11 người phản biện không chạy được): 0 P0/P1; đã xác nhận 1 P2 (nhãn nút "Lắc xúc xắc" chạm viền ở 320dp×1.3) và các P3 (nhãn biểu đồ, gợi ý phản tư khi chưa có ngân sách, ghi chú tài liệu/bằng chứng) → đã sửa; các phát hiện chưa kịp phản biện được tôi kiểm tra trực tiếp trên mã nguồn/ảnh. **Manager quyết định không mở vòng 6: các sửa cuối (nút xúc xắc, làm tròn nhãn cột, gợi ý phản tư, tài liệu) chỉ được tự kiểm chứng bằng runtime AVD + test, chưa được reviewer độc lập xác nhận lại** — `REVIEW_WALLY_V1011.md` |

## Ảnh chụp (đường dẫn)
**5 ảnh giao (411dp, cỡ chữ 1.0; cài mới + dữ liệu mẫu + ngân sách 5h/tuần):** `out/wally_candidate_v1011/screenshots/` — `1_overview`, `2_timer`, `3_wally`, `4_journal`, `5_insights` (PNG 1080×2400) + `_contact_sheet_5_screens.jpg`; bản gốc `screens/delivery_*`. Đều là ảnh chụp màn hình thật từ emulator Android 16, **không** phải điện thoại thật.

`out/wally_evidence_v1011/screens/` — `before_v1010_*` (trước), `final_data_*` (sau, có dữ liệu), `final_fresh_*` (sau, cài mới không dữ liệu), `final_empty_*` (sau xóa toàn bộ, còn ngân sách), `final_mx_*` (ma trận hiển thị), `final_flow_*` / `final_extra_*` (luồng bổ sung), `upgrade_*` (cài đè), `harness2_cards_*` (thẻ đề xuất với **dữ liệu giả lập**, không phải candidate).

## Thay đổi hành vi cần biết
Nút `＋` nổi chỉ ở Nhật ký (Tổng quan có ô "Thêm giờ"); quản lý dữ liệu chuyển sang menu `⋮` → "Dữ liệu & sao lưu" ("Xóa toàn bộ bản ghi" giữ ngân sách, nay ghi rõ); toast hiện ở đầu màn hình; đồng hồ `MM:SS` dưới 1 giờ; nút "Tách đề xuất" thành nút gửi.

## Lỗi/giới hạn còn tồn tại (trung thực)
- **Chưa kiểm trên điện thoại thật**; `KBD-01` (Gboard/Samsung/Laban Key trên máy thật) chưa kiểm.
- **Chưa kiểm với AI thật** (không gọi Cloud API, không dùng khóa cá nhân): bong bóng trả lời và thẻ đề xuất chỉ thấy bằng dữ liệu giả lập qua bản harness tạm (không phải candidate; đã hoàn nguyên và kiểm hash). Luồng Wally đề xuất → Xác nhận → sổ cái trên candidate: **NOT_TESTED**.
- **Reviewer độc lập chưa xác nhận các sửa cuối** (không mở vòng 6 theo quyết định của Manager); các sửa đó chỉ có bằng chứng runtime + test của chính tác giả.
- Hộp thoại Thông tin / Cấu hình AI giữ văn bản kỹ thuật cũ (không thuộc 5 màn hình).
- Chưa kiểm chế độ tối và TalkBack.
- **Phát hiện mới sau khi đóng gói (chụp LDPlayer `WallyQA`, Android 9, 2026-11-15, chưa sửa):** trên instance này thanh tab dưới cùng **biến mất ngay khi mở tab Wally** dù không có bàn phím (`mInputShown=false`); phím Back thoát hẳn ra launcher nên chỉ rời Wally bằng cách mở lại app. Nguyên nhân khả dĩ: điều kiện ẩn thanh tab khi `WindowInsets.isImeVisible` (mới ở v1.0.11) trả về đúng sai trên môi trường này; trên AVD Android 16 không xảy ra. Chưa kiểm trên điện thoại thật. APK `af50d6cc…` không đổi; cần quyết định của Owner/Manager có sửa và build lại hay không. Ảnh: `docs/screenshots/ldplayer-android9/`.
- Emulator Android dùng AEHD: nếu Owner mở LDPlayer khi AVD đang chạy có thể xung đột — AVD sẽ được tắt khi xong.

## Chưa tuyên bố (Owner chưa nghiệm thu)
`DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED` — **không** được cấp. Live Eval v1.2 giữ nguyên (SHA `0b5144c8…17ea0`); không chuyển PASS giữa các APK khác SHA.

## Việc tiếp theo (Owner)
Làm theo `WALLY_OWNER_CHECKLIST_V1011.md` (10 bước) trên điện thoại thật với đúng SHA-256 ở trên.
