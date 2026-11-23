# Đặc tả hoàn thiện UI/UX Time Wallet – Wally

**Phạm vi giao việc · 23/11/2026**  
Triển khai toàn bộ yêu cầu dưới đây trên ứng dụng Android Time Wallet hiện có. Bàn giao bản ứng dụng đã chỉnh sửa và kết quả kiểm chứng. P0, P1, P2 thể hiện mức ưu tiên khi xử lý xung đột. Cả ba mức đều thuộc cùng một phạm vi bàn giao.

## 1. Kết quả người dùng cần đạt

Người dùng có thể mở Wally, biết mình đang làm gì và việc nào sắp tới, thêm một việc bằng tiếng Việt, duyệt giờ làm do Wally đề xuất, bắt đầu đo và hoàn thành việc đó. Trong suốt quá trình, họ luôn phân biệt được **hạn phải xong**, **giờ dự kiến làm** và **thời gian thực tế đã ghi nhận**.

Tiếp tục từ giao diện mới trong bộ QA ngày 23/11. Màn chính hiện tại là **Lịch trình**. Giữ bố cục gọn và tạo nét nhận diện Wally qua những dấu hiệu có ý nghĩa khi sử dụng như mốc giờ hiện tại, đồng hồ đang chạy và việc sắp tới. Trên mỗi màn, thông tin hoặc hành động quan trọng nhất phải dễ nhận ra ở cả giao diện sáng và tối.

## 2. P0 · Hạn việc, lịch dự kiến và thời gian thực tế

**Tình huống tham chiếu.** Trong bộ QA, trước khi duyệt, việc “Ôn thi hệ thống” có hạn **20:00** và đã quá hạn. Wally tìm được khung **21:45–22:30**. Sau khi duyệt, thẻ việc lại hiện hạn **21:45** và chuyển thành **gần hạn**. Ảnh và XML xác nhận sự thay đổi trên giao diện. Dữ liệu lưu bên dưới cần được kiểm chứng trên ứng dụng.

| Thao tác | Kết quả bắt buộc với cùng một việc |
|---|---|
| Mở đề xuất lúc khoảng 21:29 | Thấy hạn 20:00, trạng thái quá hạn và khung trống 21:45–22:30. Wally nói rõ khung đề xuất nằm sau hạn. |
| Bỏ qua đề xuất | Việc vẫn có hạn 20:00. Lịch và thời gian thực tế giữ nguyên. |
| Duyệt đề xuất | Lịch dự kiến có đúng một mục 21:45–22:30. Khi việc còn mở, thẻ vẫn ghi **Hạn 20:00 · Quá hạn**. Người dùng đọc được giờ dự kiến riêng. Thực tế vẫn là 0 phút. |
| Xem từ bảng nổi, trở lại Công việc hoặc mở lại app | Cùng một hạn, một khung dự kiến và cùng trạng thái. Việc đã có lịch vẫn giữ hạn gốc. Việc chưa có hạn vẫn có thể được xếp lịch. |
| Bắt đầu đo, tạm dừng, tiếp tục và lưu | Thời gian đo phản ánh các đoạn thực sự đã chạy. Ví dụ, lên kế hoạch 45 phút và chỉ đo 2 phút thì phần đã đo là 2 phút. |
| Đánh dấu hoàn thành | Trạng thái hoàn thành nhất quán giữa Công việc và bảng nổi. Thao tác này tự nó không cộng thời lượng, kể cả với việc chưa từng đo. |

Chỉ thay đổi hạn khi người dùng chủ động sửa hạn hoặc chấp thuận một đề xuất **đổi hạn** có nêu rõ giờ cũ và giờ mới. Quy tắc này áp dụng cả với việc đã có từ trước. Giữ nguyên ý nghĩa của năm lớp dữ liệu hiện có gồm Đã đo, Có giờ, Chưa gắn giờ, Đang diễn ra và Dự kiến.

## 3. P0 · Thao tác phải nhìn thấy và dùng được

| Khu vực | Điều kiện đạt |
|---|---|
| Công việc ở 320dp | Với một việc như tình huống trên và bàn phím đóng, tên việc đầu tiên, nút Đo và điều khiển hoàn thành đều nằm trong khung nhìn đầu. Ba mục Công việc, Thói quen, Mục tiêu đều dễ tiếp cận. Mục tiêu và nút thêm không bị đẩy thành một hàng rời. |
| Tập trung ở 320dp | Khi chưa chạy và bàn phím đóng, người dùng thấy và bấm được **Bắt đầu đo** ngay khi mở màn. Khi tạm dừng, nhận ra ngay **Tiếp tục** sẽ nối lại việc nào. |
| Thông báo nhanh | Sau khi thêm việc, duyệt lịch hoặc tạm dừng đo, thông báo nằm gần thanh điều hướng dưới. Tiêu đề, tab, điều khiển chính và nội dung đang nhập vẫn dùng được. |
| Bong bóng Wally | Người dùng chạm được nút Đo, công tắc ghim thông báo, nút xóa trong Dòng thời gian và đọc được nhãn Dự kiến cùng số liệu cuối màn. Mục cuối trong danh sách cuộn có thể đưa trọn vào vùng thao tác. |
| Lịch ngày, 3 ngày và tuần | Mốc hiện tại, nút tới việc kế tiếp, tiêu đề ngày và tên các phiên ngắn đều đọc được khi ở gần nhau. Vị trí chạm hoặc kéo tương ứng đúng với giờ trên trục. |
| Chọn và dời lịch | Chạm khoảng trống làm hiện ngay vùng chọn cùng thao tác thêm lịch. Khi kéo đến mép trên hoặc dưới, vùng chọn vẫn liền khối và thao tác còn tiếp cận được. Giờ xem trước khớp giờ sau khi lưu hoặc dời. Cuộn vùng lịch giữ đúng tỷ lệ giờ. |

## 4. P1 · Công việc và AI To-do

- **Thêm nhanh.** Một lối thêm việc chính dùng được cả khi danh sách rỗng lẫn khi đã có việc. Ô nhập hỗ trợ câu tự nhiên có tên việc, hạn, thời lượng và ưu tiên, ví dụ “Soạn báo cáo trước 15h mai, cần 45 phút, P1”. Người dùng hiểu sự khác nhau giữa thêm việc, ghi thời gian và gọi Wally xếp lịch.
- **Đề xuất chờ duyệt.** Mỗi đề xuất có tên việc, khoảng giờ, thời lượng, lý do ngắn và hai lựa chọn **Áp dụng / Bỏ qua**. Đề xuất trông khác thẻ việc và chỉ xuất hiện khi có nội dung cần duyệt. Nếu gợi ý được tạo trên máy, nhãn phải phản ánh đúng nguồn.
- **Sau khi duyệt.** Trên thẻ việc, giữ một thông tin bền vững về khung đã xếp. Một phản hồi ngắn là đủ tại thời điểm duyệt. Giải phóng phần chiều cao hiện bị chiếm bởi banner lặp. Hạn, cảnh báo và giờ dự kiến đọc được trọn nghĩa. Chỉ hiện số phút thực tế một lần trên cùng thẻ.
- **Danh sách và bộ lọc.** Khi có việc, ưu tiên tên việc và thao tác đo/hoàn thành. Khi rỗng, cho biết đang xem bộ lọc nào và cách thêm việc. Người dùng nhận ra còn bộ lọc ngoài mép màn và tiếp cận được từng mục. Thói quen và Mục tiêu mở đúng nội dung riêng.

## 5. P1 · Wally nổi, lịch và tập trung

| Khu vực | Trải nghiệm cần bàn giao |
|---|---|
| Bong bóng ngoài app | Chạm mở Chat Wally. Nhấn giữ mở bảng nhanh. Người dùng phân biệt được **thu gọn**, **tắt trợ lý nổi** và **dừng phiên đo**. |
| Bảng nhanh | Trong cùng bảng, thấy phiên đang làm hoặc tạm dừng, việc kế tiếp và các To-do cần xử lý. Có thể xem lịch hôm nay và khoảng trống kế tiếp, thêm việc, bắt đầu đo hoặc đánh dấu xong ngay tại bảng. Lịch phân biệt mục thực tế với mục dự kiến. |
| Bảng hẹp và bàn phím | Ở 320dp, tên Wally và các nút còn đọc được. Nút hoàn thành đủ dễ chạm, khác rõ nút đo. Nhãn Quá hạn/Gần hạn đọc trọn. Khi nhập việc hoặc chat, ô nhập và nút gửi vẫn dùng được. Khi trở về, việc vừa thêm xuất hiện đúng một lần. |
| Giao diện nổi | Bảng nổi và chat nổi theo giao diện sáng/tối của app. Nút thu gọn và tắt dễ phân biệt. Ngăn gợi ý tập trung vào hành động phù hợp với đề xuất. Mục cuối của lịch đọc và chọn được. |
| Lịch trình và Dòng thời gian | Dành phần lớn vùng thao tác cho lịch và nội dung. Chỉ nhắc giờ hiện tại thêm một lần khi người dùng đã cuộn ra khỏi mốc đó và cần quay lại. Ở lịch tháng, để ngày rỗng dễ nhận biết mà không lặp chữ “Trống” trên từng ô. Tên Dòng thời gian thống nhất giữa bộ chuyển và tiêu đề. Tên hoạt động, giờ, thời lượng và thao tác xác nhận/điều chỉnh có đủ chỗ. |
| Tập trung | Phân biệt rõ sẵn sàng, đang chạy và tạm dừng. Khi đang chạy, ưu tiên đồng hồ cùng thao tác tạm dừng/lưu. Chỉ hiện tiến độ theo phần trăm ở chế độ có thời lượng đích. Gợi ý việc phù hợp xuất hiện trước khi bắt đầu. Tránh lặp đồng hồ ngay trên thanh đầu của chính màn Tập trung. |

## 6. P2 · Thống kê, nhận diện và các màn phụ

- **Mở rộng / Thống kê.** Phân biệt ngày chưa có dữ liệu, ngày có ít dữ liệu và ngày đã ghi nhận nhiều hoạt động. Với chỉ 1 phút trong tuần, người dùng đọc được đúng 1 phút và ngày tương ứng. Chỉ hiển thị chuỗi ngày liên tiếp, hiệu suất hoặc tỷ lệ khi có dữ liệu phù hợp.
- **Ví 24 giờ.** Khi đã ghi 0 phút, con số, chú giải và biểu đồ cùng diễn đạt được phần đã ghi, phần chưa ghi và phần còn lại. Người dùng nhận ra ngay hành động **Phản tư cuối ngày** và cách đóng.
- **Cài đặt và cấu hình AI.** Cho thấy Wally Pro và Wally Flash có vai trò gì, đang chọn chế độ nào và tên model thực tương ứng. Hai lựa chọn có cùng cấp thị giác. ID định tuyến, địa chỉ dịch vụ và trường tùy chỉnh thuộc phần nâng cao. Màn chính tránh các ghi chú dành cho lập trình viên và các hàng chọn model lặp.
- **Chat và Ghi nhanh.** Ưu tiên diện tích hội thoại và ô soạn. Hướng dẫn, ví dụ và chọn model vẫn dễ tìm. Khung chat bám đáy hợp lý cả khi bàn phím đóng và mở. Tiêu đề Ghi nhanh dùng tiếng Việt tự nhiên, chỉ nêu trạng thái kết nối khi thông tin đó ảnh hưởng tới thao tác.
- **Giới thiệu lần đầu.** Mỗi bước có thông điệp ngắn và biểu cảm hoặc minh họa phù hợp. Đưa dẫn chứng dài ra khỏi nội dung đọc chính. Nút cuối **Bắt đầu ngay** rõ ràng, cùng cách trình bày với nút chuyển bước.
- **Nhận diện và khả năng đọc.** Giữ sắc xanh Wally làm điểm nhấn có chọn lọc. Nút, biểu tượng điều khiển và mascot nhất quán giữa app và bảng nổi. Biểu tượng quay lại, xóa nội dung, tạm dừng và dừng thể hiện đúng hành động. Công tắc tắt vẫn dễ nhận ra. Ưu tiên chữ và dấu hiệu trạng thái rõ ở cả hai giao diện. Chuyển động phản ánh thay đổi trạng thái thật và tôn trọng tùy chọn giảm chuyển động.

## 7. Điều kiện hoàn tất và bàn giao

1. **Luồng đầu cuối chạy được trên app.** Thêm việc → xem hoặc bỏ qua đề xuất → duyệt lịch → mở từ bảng nổi → đo → tạm dừng/tiếp tục → lưu → hoàn thành. Đối chiếu hạn, lịch và số phút thực tế ngay sau thao tác và sau khi mở lại. Với tình huống 20:00, xác nhận được cả giá trị đã lưu lẫn giá trị người dùng nhìn thấy.
2. **Các màn đạt tiêu chí trong đúng điều kiện gây lỗi.** Có bằng chứng cho Công việc và Timer ở 320dp, vùng chọn sát mép lịch, cùng bảng nổi khi mở bàn phím và khi bật giao diện tối. Kiểm tra các màn liên quan ở 390dp để bảo toàn không gian thao tác trên màn rộng hơn. Ảnh minh chứng cho Thói quen phải thực sự hiển thị Thói quen.
3. **Bàn giao một kết quả tổng hợp.** Nêu phiên bản đã chạy, môi trường kiểm chứng, kết quả theo mục 2–6 và lỗi còn tái hiện nếu có. Dùng ảnh trước/sau cho vấn đề thị giác và kết quả thao tác thực cho thay đổi dữ liệu. Ghi **chưa kiểm chứng** cho trường hợp chưa chạy.

**Đối chiếu khi có bộ ảnh QA.** Trong `qa_screenshots_review (2).zip`, các cặp PNG/XML có cùng tên. Các mốc quan trọng gồm `05_tasks_ai_pending_light` và `06_tasks_applied_card_light` cho hạn việc, `37_tasks_320dp` và `38_timer_idle_320dp` cho màn nhỏ, `25_schedule_day_dark` cho các phiên ngắn, nhóm `27`–`34a` cho bảng nổi. Ảnh `07_tasks_habits_light` của bộ này vẫn đang mở Công việc.
