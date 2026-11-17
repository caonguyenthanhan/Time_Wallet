# REVIEW — WALLY v1.0.11 (UI Polish) — Independent Product Review

- **Phương pháp:** Reviewer **chỉ đọc** (không sửa mã nguồn production, không chạy gradle/adb/emulator, không chạm LDPlayer); xem ảnh chụp runtime thật trên AVD `TimeWallet_QA`, so sánh với ảnh baseline v1.0.10, `diff -r` với `source_v1.0.10.tar`, đọc logcat và bằng chứng test. Mọi phát hiện được trả cho Master (Single Writer) sửa; không yêu cầu Owner duyệt từng vòng.
- **Ghi chú trung thực:** bản báo cáo dưới đây là tóm tắt của Master từ báo cáo của Reviewer (agent độc lập, cùng phiên làm việc nhưng không nhìn thấy kết luận của Master). Nó **không** thay thế nghiệm thu của Owner trên điện thoại thật.

## Vòng 1 — verdict `FIX_REQUIRED`
Guardrail và nghiệp vụ nguyên vẹn (chỉ file UI/greeting/versionCode đổi; không test cũ nào bị sửa; không crash/`PatternSyntaxException`/ANR của app (8 dòng ANR còn lại là của tiến trình hệ thống lúc emulator khởi động); cài đè giữ dữ liệu). Quan điểm sản phẩm: Q1/Q2/Q4/Q6 = PARTIAL.

| ID | Mức | Phát hiện | Xử lý |
|---|---|---|---|
| F1 | P1 | Bong bóng tin nhắn người dùng ở Wally chỉ là `remember` cục bộ: mất khi đổi tab, còn trơ trọi sau khi xác nhận, lệnh hủy ghi đè tin gốc | **Đã sửa**: trạng thái do `MainActivity` giữ (`rememberSaveable`), tự xóa khi không còn gì hiển thị, lệnh hủy không ghi đè; xác minh runtime (`final_extra_wally_*`) |
| F2 | P1 (bằng chứng) | Ảnh 320dp "cuộn" giống hệt ảnh gốc: script đọc sai kích thước màn hình khi có `wm size` override | **Đã sửa công cụ**, chụp lại toàn bộ ma trận; ảnh `_a`/`_b` nay khác nhau |
| F3 | P2 | "Gợi ý nhìn lại" ~50 từ hiện thường trực, kể cả trạng thái rỗng | **Đã sửa**: chỉ hiện khi có dữ liệu, thu gọn 2 dòng, chạm để xem đủ |
| F4 | P2 | Tổng quan vẫn là chuỗi 7 khối; hai số "chưa ghi" khác nghĩa | **Đã sửa trong phạm vi**: hero chỉ còn một số lớn; số phụ chuyển về thẻ 24 giờ; ngân sách tối đa 2. Thứ tự khối giữ nguyên (một test cũ ghim thứ tự) |
| F5 | P2 | Chip danh mục do xúc xắc chọn có thể nằm ngoài vùng nhìn | **Đã sửa** (`BringIntoViewRequester`); bằng chứng runtime với chip ngoài màn hình: `final_extra_timer_dice_offscreen_chip_in_view` |
| F6 | P3 | Toast che nút "Chạy ngay" | **Đã sửa**: toast ở đầu màn hình |
| F7 | P3 | Hàng bản ghi chưa gắn giờ nói "Chưa gắn giờ" hai lần | **Đã sửa** |
| F8 | P3 | Nút `＋` nằm trên nút `×` khi cuộn ở 320dp | Chấp nhận (hàng cuối được chừa 88dp) |
| F9 | P3 | Thẻ đề xuất: ký tự chữ ✎/×, dòng phụ bị bóp cạnh "Bỏ hết" | **Đã sửa**: icon vector, dòng phụ dưới tiêu đề, chip tóm tắt trong `FlowRow` |
| F10 | P3 | Nhãn "7 ngày · Có mốc giờ" xuống dòng ở 320dp | **Đã sửa** ("7 ngày · Có giờ") |
| F11 | P3 | Chip AI hiện tên máy chủ | **Đã sửa** ("✦ AI đã bật · ⚙") |
| F12 | P3 | Lời hộp thoại xóa nói quá (ngân sách thực ra được giữ) | **Đã sửa lời** ("Xóa toàn bộ bản ghi · giữ lại ngân sách") |

## Vòng 2 — verdict `FIX_REQUIRED` (gần đạt; 11/12 đã sửa, F9 một phần)
| ID | Mức | Phát hiện | Xử lý |
|---|---|---|---|
| N1 | P1 | Ô thao tác nhanh "Thêm giờ" bị cắt chữ ở 320dp × cỡ chữ 1.3 (`giờ` → `giò`) do `maxLines=1, softWrap=false` | **Đã sửa**: nhãn tối đa 2 dòng, không ép một dòng; thêm test hợp đồng |
| N2 | P2 | Chip tóm tắt của thẻ đề xuất nằm trong `Row` không xuống dòng → tràn ở 320dp | **Đã sửa**: `FlowRow`. Khi chụp lại bằng harness ở 320dp còn lộ thêm lỗi **hàng đề xuất bị bóp còn một ký tự/dòng** (do lề trái 38dp + hai nút 48dp trong cùng một hàng) → **đã tái cấu trúc hàng thành 2 tầng** (tầng 1: biểu tượng + tên + thời lượng; tầng 2: chi tiết + sửa/bỏ) và bỏ lề trái; chụp lại ở 411dp ×1.0 và 320dp (×1.0 và ×1.3) |
| N3 | P2 | Bằng chứng unit test cũ hơn các sửa | **Đã làm mới** (số liệu cuối trong `WALLY_V1011_ACCEPTANCE_RECORD.md`) |
| N4 | P3 | Hộp thoại Thông tin / Cấu hình AI còn thuật ngữ kỹ thuật | **Chấp nhận, ghi vào giới hạn**: không thuộc 5 màn hình, không đổi so với v1.0.10 |
| N5 | P3 | Sau khi đóng lỗi, ô nhập vẫn giữ chữ đã gõ | **Chấp nhận**: đây là hành vi "giữ nội dung đã nhập" có chủ đích của Wally |

## Guardrail / regression (cả hai vòng): PASS
Đề xuất vẫn cần xác nhận và không tự lưu; đường hủy khi đang phân tích còn nguyên (`enabled = !isAnalyzing || GuardHealth.isCancelCommand(...)`, `onClick = onCancelAnalysis`); `Rx`/`GuardHealth`/`Diagnostics`/`TimeWalletApp`/ViewModel/Room/Keystore/đồng hồ không đổi; không `Log`/`println`; xác nhận xóa bản ghi/ngân sách/xóa toàn bộ còn nguyên; không test cũ nào bị sửa; logcat không `FATAL`/`PatternSyntaxException`/ANR của app (ANR còn lại là của tiến trình hệ thống lúc emulator khởi động); cài đè giữ dữ liệu.

## Vòng 3 — kiểm toán đa góc nhìn (workflow, 164 agent chỉ-đọc)
Năm góc nhìn độc lập (hình ảnh 411dp · màn hẹp/chữ lớn · luồng & hồi quy · mã nguồn & guardrail · đóng gói & tài liệu), mỗi phát hiện phải qua 3 người phản biện cố bác bỏ (cần ≥2/3 xác nhận). Kết quả: **46 phát hiện đã xác nhận — 0 P0/P1, 2 P2, 44 P3** (4 phát hiện bị bác bỏ). Tất cả guardrail vẫn PASS (chỉ file UI/greeting/versionCode đổi; không test cũ nào bị sửa; không `Log`/`println`; `Rx`/`GuardHealth`/ViewModel/Room/Keystore/đồng hồ/phân tích không đổi).

| Nhóm phát hiện (đã xác nhận) | Xử lý |
|---|---|
| **P2** "Dự kiến" bị cắt trong thẻ đề xuất ở 320dp | **Đã sửa**: dòng chi tiết tối đa 3 dòng; hàng đề xuất 2 tầng; chip tóm tắt trong `FlowRow`; chụp lại ở 411dp ×1.0 và 320dp (×1.0, ×1.3) |
| **P2** nhãn trạng thái dài của Nhật ký có thể bị cắt ở 320dp×1.3 | **Đã sửa**: nhãn tự xuống dòng (tối đa 2 dòng) |
| Hai nút sau khi lắc xúc xắc nằm nửa dưới thanh tab, không tự cuộn; nhãn "Chạy ngay" có thể bị cắt | **Đã sửa**: tự cuộn vào vùng nhìn sau khi lắc; nhãn tự xuống dòng; chứng minh runtime với chip danh mục nằm ngoài màn hình (`final_extra_timer_dice_offscreen_chip_in_view`) |
| Nút `▶ Bắt đầu đo` bị che ở 320dp×1.3 | **Đã sửa**: vòng đồng hồ thu nhỏ trên màn thấp/chữ lớn |
| Logo màn chào Wally bị cắt ở 320dp×1.3 | **Đã sửa**: màn chào không tự cuộn xuống đáy |
| Nhãn đỉnh cột ≥10 giờ bị cắt ("10h 3") | **Đã sửa**: nhãn ngắn (`2h15`, `10h`, `45p`) + test hành vi `V1011UiLogicTest` |
| Gợi ý thu gọn lặp số liệu rồi cắt giữa câu; hiện cả khi rỗng | **Đã sửa**: thu gọn chỉ còn câu hỏi, chỉ hiện khi có dữ liệu (test hành vi) |
| Timeline đứt đoạn, quá nhạt | **Đã sửa**: trục liên tục, màu rõ hơn |
| "Thử lại" không cập nhật bong bóng tin nhắn | **Đã sửa** (cùng điều kiện với nút gửi; test siết chặt) |
| Chú giải donut Phân tích không báo "+N"; khung biểu đồ rỗng không có thông báo; nút gửi trông như đang bật khi ô trống; gợi ý "bấm ＋" ở Tổng quan; Tổng quan 2 ngân sách vs hộp sửa hứa 3; tiêu đề Wally sát chip ở 320dp | **Đã sửa** |
| Nhãn nhỏ dùng cỡ cố định theo dp, không theo cỡ chữ hệ thống | **Đã giảm nhẹ**: dùng cỡ chữ có giới hạn phóng (tối đa 1.1–1.15×, `cappedSp`) cho nhãn tab, mốc giờ, nhãn biểu đồ; đồng hồ lớn vẫn cố định theo dp |
| Test hợp đồng khớp chuỗi quá lỏng / tên test nói quá | **Đã siết** (điều kiện đầy đủ, nối dây ở MainActivity) và **thêm 3 test hành vi** |
| `upgrade_test.json` không chứng minh dữ liệu do v1.0.10 tạo sống sót; log tự mâu thuẫn | **Đã làm lại đúng nghĩa** (gỡ app → v1.0.10 tạo dữ liệu có dấu mốc `MARKER-UPG-37` + ngân sách → `adb install -r`), ghi một kết luận duy nhất |
| Luồng ngân sách/bản ghi/hẹn giờ/xuất JSON/xoay màn hình/cài mới chưa có bằng chứng trên APK cuối | **Đã chạy lại trên APK cuối** (`chain_log_part2.txt` mục 6–10, chạy lại trên cùng APK; lần đầu ở `chain_log.txt`) |
| Ảnh cuộn 320dp thiếu cho Đồng hồ; ảnh harness a/b trùng nhau | **Đã chụp lại** (ảnh `_b` của Đồng hồ; harness `_b` cuộn lên; thêm `_c` và ảnh xúc xắc ở 4 cỡ) |
| Patch nguồn không áp được; file hash dùng CRLF; đường dẫn harness sai trong dossier; câu chữ logcat/ANR/"bàn phím thật"/checklist (không có cách xác nhận bản cài, bước 10) | **Đã sửa** (`diff -ruN` đã kiểm áp thật → byte-giống; hash LF; đường dẫn; ghi rõ "của app", "Gboard trên emulator"; thêm bước kiểm tiêu đề `Time Wallet v1.0.11`, bước 10 chỉ khi có log) |

**Chấp nhận, không sửa (ghi vào giới hạn):** màn Daily Review, hộp Cấu hình AI / Thông tin còn giao diện–thuật ngữ cũ (ngoài 5 màn hình); Wally chỉ hiển thị một lượt hỏi–đáp (ViewModel không lưu lịch sử); lời báo lỗi AI lần đầu và nội dung toast (ngày dạng ISO) do tầng nghiệp vụ tạo; chênh lệch nhãn giữa các màn, thẻ ngân sách lặp con số, kiểu icon emoji/vector, chip cuộn ngang cắt ở lề trang, nhãn kỹ thuật trong menu/hộp dữ liệu (P3 thẩm mỹ).

## Vòng 4 — kiểm toán xác nhận (workflow, 55 agent chỉ-đọc)
Bốn góc nhìn (xác minh sửa lỗi bằng ảnh · hồi quy mã nguồn · tính trung thực của bằng chứng luồng · đóng gói & tài liệu), mỗi phát hiện qua 3 người phản biện. Kết quả: **12 phát hiện đã xác nhận = 10 vấn đề riêng biệt (hai vấn đề được hai góc nhìn cùng thấy) — 0 P0/P1, 2 P2 riêng biệt, còn lại P3** (5 phát hiện bị bác bỏ). Phần lớn mục "đã sửa" của vòng 3 được xác nhận đúng bằng ảnh/mã; ngoại lệ là các mục liệt kê dưới đây, trong đó có **một mục tài liệu ghi sai**: nhãn tab "Tổng quan" vẫn bị cắt ở 320dp×1.3.

| Phát hiện (đã xác nhận) | Mức | Xử lý |
|---|---|---|
| Wally xoay ngang: vùng hội thoại còn ~45dp, thẻ trả lời/đề xuất không đọc được; log nói "bong bóng có mặt" vì chỉ khớp chữ trong ô nhập (2 góc nhìn cùng thấy) | P2 | **Đã sửa**: màn thấp (<480dp) ẩn đầu trang và gợi ý; kiểm tra xoay màn hình viết lại (khối trả lời phải cao ≥40px, ô nhập còn, về dọc thì bong bóng là nút UI thật phía trên ô nhập); ảnh `final_flow_rotation_*` |
| Thẻ trả lời của câu hỏi trước nằm dưới tin nhắn mới trong lúc phân tích/sau lỗi | P2 | **Đã sửa**: hỏi câu mới (và Thử lại) đóng câu trả lời cũ; thẻ đề xuất đang chờ vẫn giữ |
| Nhãn tab "Tổng quan" bị cắt ở 320dp×1.3 (dossier/checklist ghi là đã sửa) | P3 | **Đã sửa thật**: nhãn tab giữ 11dp trên màn <360dp; ảnh chứng minh; dossier mô tả đúng |
| Nhãn cột "2h5" dễ đọc nhầm 2h50 | P3 | **Đã sửa** ("2h05") + test hành vi |
| Quay lại tab Đồng hồ tự cuộn tới nút xúc xắc, đẩy đồng hồ khỏi màn đầu | P3 | **Đã sửa**: chỉ cuộn ngay sau khi lượt lắc kết thúc trong lần hiển thị này |
| "Chốt thực tế" 9sp là hành động chính của hàng Nhật ký | P3 | **Đã sửa** (cỡ chữ ≥12sp, vùng chạm 48dp giữ nguyên) |
| Bằng chứng: ma trận thiếu ảnh cuộn lần 2/ảnh xúc xắc ở 320dp; Wally không có `_b`; một số cặp a/b ở 411dp giống nhau | P3 | **Đã bổ sung** `_c` và `timer_dice` ở 4 cỡ; ghi rõ trong dossier Wally không có `_b` và vì sao có cặp giống nhau |
| Tài liệu: câu chữ ANR (vòng 1–2), phạm vi ảnh harness (411dp ×1.3 không có), `patch` cần `--binary` | P3 | **Đã sửa** câu chữ; `PATCH_VERIFY.txt` ghi đúng lệnh tái tạo |

**Chấp nhận, không sửa:** các mục thẩm mỹ P3 đã chấp nhận ở vòng 3 và mục "nút hành động nằm sát mép thanh tab sau khi lắc" (bị bác bỏ 1/3: vẫn thấy đủ và bấm được).

## Vòng 5 — cổng cuối (workflow, 27 agent chỉ-đọc) và giới hạn của kiểm toán độc lập
Kết quả: **0 P0/P1**. Đã xác nhận 1 P2 — nhãn nút "Lắc xúc xắc" chạm viền nút ở 320dp×1.3 (mã: `contentPadding` không có lề ngang) — và các P3 về nhãn cột biểu đồ (làm tròn như các nơi khác), gợi ý phản tư khi chưa đặt ngân sách (chỉ giữ câu cuối), và ghi chú tài liệu/bằng chứng (kích thước APK trong digest, mô tả ảnh `_b`/`_c`, đường dẫn file DEX/badging, ảnh thẻ ngân sách trước/sau cài đè). Do hết hạn mức, 11 người phản biện không chạy được; các phát hiện chưa được phản biện đủ được tôi kiểm tra trực tiếp trên mã nguồn và ảnh rồi xử lý.

**Đã sửa sau vòng 5:** nút xúc xắc thêm lề ngang + căn giữa + tối đa 2 dòng; `compactBarLabel` làm tròn đến phút gần nhất ("2h05", "1p", "10h") + test hành vi; `reflectionQuestion` không có mốc thì chỉ giữ câu cuối + test; tài liệu; bằng chứng bổ sung (ảnh thẻ ngân sách trước/sau cài đè; luồng đồng hồ → lưu → Nhật ký).

**Giới hạn trung thực:** Manager quyết định **không mở vòng 6 và không chờ hạn mức**. Vì vậy các sửa sau vòng 5 **chưa được reviewer độc lập xác nhận lại**; chúng chỉ được tự kiểm chứng bằng chuỗi runtime trên AVD (`chain_log.txt`: 407/409 test xanh, ma trận 411dp/320dp × 1.0/1.3, các luồng, cài đè đã chạy lại sạch, logcat) và các test hồi quy tương ứng. Không có phát hiện P0/P1 mở.

## Không thể xác minh (trong mọi vòng)
Điện thoại thật, bàn phím trên máy thật (`KBD-01`), phản hồi AI thật (thẻ đề xuất chỉ xem được bằng dữ liệu giả lập qua bản harness tạm, không phải candidate), chế độ tối, TalkBack, trạng thái chip AI khi đã cấu hình khóa.
