# Time Wallet v2.2.1 · Review nghiệm thu R5

**Ngày review 24/11/2026. Khuyến nghị nghiệm thu từng phần và giữ mở nghiệm thu toàn bộ.** R5 thể hiện được việc giữ hạn khi duyệt lịch và ghi đúng tổng thời gian của phiên đo. Vẫn có sai khác giữa các màn về mốc giờ thực tế, thiếu giờ dự kiến trên thẻ việc sau khi sửa lịch và lỗi dễ đọc ở 320dp.

## Phạm vi bằng chứng

Đã đối chiếu 62 cặp PNG/XML trong `qa_v221_verify_r5.zip` với các mục 2–7 của `SPEC_WALLY_UI_UX.md`. Màn Cài đặt ghi Time Wallet v2.2.1. Gói có ảnh 780×1688 và 640×1136, các ca được đặt tên 390dp và 320dp. Trong 62 PNG có 54 ảnh khác nhau theo nội dung. Gói chỉ có PNG/XML. Kết luận về hành vi dựa trên các trạng thái nhìn thấy được. Các thao tác ngoài khung hình, giá trị lưu nội bộ và danh tính bản build chính xác chưa được xác minh độc lập.

Phần “Wally kể” được đánh giá riêng ở cuối báo cáo. Ngay với phạm vi UI/UX ban đầu, R5 vẫn còn mục chưa đạt.

## Kết quả theo spec

| Phạm vi | Kết quả | Dẫn chứng |
|---|---|---|
| Hạn gốc khi duyệt lịch · mục 2 | Đạt ở luồng duyệt được chụp | `04e → 05 → 06`: việc “Ôn thi hệ thống” hạn 00:10, Wally nêu rõ 02:45–03:30 nằm sau hạn. Duyệt xong thẻ vẫn báo quá hạn 00:10. Cặp được đánh dấu sau mở lại `10b` có cùng thông tin. Đây là ca tương đương về thứ tự các mốc với ví dụ 20:00 trong spec. |
| Đo, tạm dừng, tiếp tục, lưu · mục 2 và 5 | Đạt về thời lượng trong ca đã chụp | `09a–09e`: tạm dừng lưu 1 phút, nút Tiếp tục nêu đúng tên việc. Dòng thời gian có hai đoạn Đã đo, mỗi đoạn 1 phút, và một mục Dự kiến 45 phút cho cùng việc. Thẻ việc ghi Thực tế 2p. |
| Hoàn thành từ bảng nổi · mục 2 và 5 | Đạt về danh sách việc mở và tổng thời gian của ca này | `18a → 18b`: số việc cần xử lý giảm 2 xuống 1. `19a` còn “Soạn báo cáo” trong app. Tổng ngày trước và sau vẫn 2 phút. Bảng nổi sau hoàn thành vẫn ghi “Kế: 02:45 Ôn thi hệ thống”. Cần phân biệt một lịch hẹn còn tồn tại với lời gợi ý làm tiếp một việc đã xong. |
| To-do, bộ lọc, đề xuất · mục 3–4 | Đạt một phần | `04b → 04c` nhận được tên việc, hạn 15:00 ngày mai, 45 phút và P1 từ câu nhập không dấu. `05` ghi nguồn gợi ý trên máy và có Áp dụng/Bỏ qua, nhưng lặp nguyên cảnh báo sau hạn. `06` đã bỏ banner xác nhận lặp giữa màn. `07` và `08` thực sự mở đúng Thói quen và Mục tiêu. |
| Thao tác chính 320dp · mục 3 | Đạt về khả năng nhìn thấy ở trạng thái chụp | `19a/20a` thấy tên việc đầu tiên, nút đo và điều khiển hoàn thành. `20b` thấy Bắt đầu đo ngay. `04c/06/09b` cho thấy thông báo nằm gần thanh điều hướng dưới. Tại `20b`, hàng Ghim thông báo còn khuất một phần ở đáy; ảnh không xác nhận được thao tác sau khi cuộn. |
| Chọn và dời lịch · mục 3 và 5 | Đạt thao tác đổi giờ kết thúc trên sự kiện hiện hữu | `11a–11c` chọn “Soạn báo cáo”, kéo từ 03:35–04:20 thành 03:35–05:00, xem trước 1h25 và lưu đúng 05:00. Chuỗi này không bao quát chọn khoảng trống, kéo sát mép trên/dưới của vùng cuộn hoặc dời cả khối. |
| Lịch và Dòng thời gian · mục 3 và 5 | Còn lỗi ở lịch ngày | `02b/09d` thống nhất tên Dòng thời gian. `14` bỏ chữ “Trống” lặp trong các ngày rỗng. Ở `10a/20c`, mốc hiện tại và khối dự kiến che tên của phiên đo ngắn. Hai màn còn hiển thị khoảng giờ thực tế khác nhau, chi tiết bên dưới. |
| Bảng nổi và chat nổi · mục 5 | Đạt giao diện tối, còn lỗi và thiếu ca bàn phím | `18a–18d` xác nhận giao diện tối, nút Thu gọn và Tắt nổi dễ phân biệt. Nhãn Quá hạn vẫn bị cắt. Ảnh `18d` chỉ cho thấy ô chat nhận focus; toàn bộ vùng dưới vẫn là màn hình chính Android, chưa có bàn phím ảo. |
| Ví 24 giờ, thống kê, cài đặt, chat, onboarding · mục 6 | Đạt một phần | `02c` cho thấy đã ghi 0, chưa ghi 2h34, còn 21h26. `03a/21a` phân biệt tuần rỗng với ngày có 2 phút. `15b/15c` cho thấy model thực và trường URL/model ID trong mục nâng cao. Hộp thường vẫn dùng lời giải thích kỹ thuật “AES-GCM gắn theo host”. `21b` ghi “Ghi nhanh”. `01b` có nút “Bắt đầu ngay”, nhưng bốn bước vẫn dùng cùng hình mascot. `22` là ảnh tĩnh nên chưa xác nhận giảm chuyển động. |

## Ba sai khác ảnh hưởng trực tiếp đến nghiệm thu

1. **Phiên đo có hai cách hiển thị giờ.** `09d` ghi 02:35–02:36 và 02:36–02:37. XML của lịch ngày `10a/10c` lại ghi 02:36–02:38 cho khối cùng tên; trên PNG, chữ bị che. Tổng 2 phút khớp. Cần đối chiếu mốc đầu/cuối với cùng bản ghi và bảo đảm người dùng đọc được chúng trên lịch.
2. **Thẻ việc thiếu lịch đã chỉnh.** “Soạn báo cáo” có nhãn dự kiến 03:35–04:20 tại `06`. Sau khi kéo, `11c` và `17` hiện 03:35–05:00 trên lịch. Tại `19a/20a`, cả PNG và XML thẻ việc đều thiếu khung dự kiến. Ảnh còn cắt hạn thành “Hạn 11-2…”. Bằng chứng xác định lỗi hiển thị trong chuỗi này; nguyên nhân có thể nằm ở chế độ hẹp hoặc ở cập nhật sau khi sửa lịch.
3. **Trạng thái hạn khó đọc hoặc dễ hiểu sai.** Tại `18a`, XML chứa “⚠ Quá hạn” nhưng người dùng chỉ nhìn thấy “⚠…”. Tại `04e/06/09e`, dòng tổng báo “1 gần hạn” trong khi thẻ Ôn thi báo “Quá hạn”. Việc còn lại có hạn 15:00 hôm sau. Dòng tổng chưa cho biết việc nào đang bị tính vào cảnh báo, còn việc đã quá hạn chưa được nêu rõ ở phần tổng.

## Yêu cầu riêng “Wally kể từ dữ liệu”

**Chưa đạt với ca rỗng có bằng chứng trực tiếp.** Trong `03b`, cả tuần có 0 bản ghi và chưa đặt mục tiêu. Wally vẫn dựng toàn bộ bốn mục kể, nhắc lại chưa có dữ liệu nhiều lần rồi đưa nút “Hỏi Wally”. Spec yêu cầu giữ thống kê gọn khi chưa có phát hiện đáng kể. `21a` có ví dụ thống kê thưa với 2 phút nhưng không chụp nội dung thẻ kể tại trạng thái đó.

**Chưa kiểm chứng các ca có dữ liệu.** R5 thiếu đầu ra cho hai tuần có phiên đủ so sánh, thao tác mở căn cứ trong Phân tích AI, mâu thuẫn 0p/1p, tỷ lệ +48938% và quyết định gợi ý của chính thẻ lúc 02:50. `05` có gợi ý ban đêm 02:45–03:30 và 03:35–04:20 ở **AI To-do**. Đây là màn khác; không dùng ảnh đó để khẳng định thẻ kể đã đạt hoặc vi phạm quy tắc giờ nghỉ. Ảnh 02:50 gửi riêng trước đó không định danh bản R5.

## Bằng chứng còn cần để chốt

- Đối chiếu lại cùng một việc trên Dòng thời gian, Lịch ngày, thẻ To-do và bảng nổi. Cần thấy mốc đo, hạn gốc, khung đã sửa và phút thực tế, bao gồm sau khi mở lại.
- Bổ sung đúng các nhánh còn thiếu của luồng gốc: Bỏ qua đề xuất, xếp lịch cho việc chưa đặt hạn, hoàn thành việc chưa từng đo; cùng ca thẻ 320dp có đủ cả hạn, quá hạn và khung dự kiến trong khung nhìn đầu.
- Với lịch, cần thấy chọn khoảng trống, hiện thao tác ngay, kéo tới hai mép vùng hiển thị và kết quả sau lưu. Với bảng nổi, cần thấy bàn phím thực sự mở, nhập/gửi được và việc thêm từ bảng nổi xuất hiện đúng một lần.
- Với Wally kể, cần ca rỗng đã thu gọn và một phát hiện từ dữ liệu có lịch sử, mở được các phiên đối chiếu; thêm kết quả ở 02:50 và khi số liệu bất thường.

Đối chiếu theo nội dung thực của ảnh. Ví dụ `01_onboarding_1.png` là bước 1/4 nhưng XML cùng tên là bước 4/4. `09e` và `10b` có PNG giống hệt nhau, phù hợp với giao diện ổn định sau mở lại nhưng tự chúng không xác nhận thao tác khởi động lại hoặc giá trị lưu bên dưới. Có thể giữ các kết quả đã đạt khi xem lại những mục còn mở.
