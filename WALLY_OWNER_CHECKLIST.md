# Checklist cho Owner — bản 1.0.2 (WALLY candidate)

APK: `out/wally_candidate/TimeWallet-MVP-v1.0.2-WALLY-candidate-debug.apk`
SHA256: `49a83f858dcbaf47130c244c847e7d69809c8ab09a44a8f21163917134a89e8c`

Chỉ dùng dữ liệu thử. **Không gỡ app** (gỡ sẽ xóa dữ liệu). Cài đè bằng Android Studio (Run) hoặc `adb install -r <apk>` từ máy của bạn; bản này cùng khóa ký debug và versionCode 3 lớn hơn bản 2 nên cài đè được **nếu** bản đang cài được build trên cùng máy/khóa. Nếu Android báo "xung đột chữ ký" thì **dừng**, đừng gỡ app; báo lại để xuất dữ liệu trước (nút ⇩).

1. **Cài đè và phiên bản.** Cài đè. Mở app, bấm ⓘ: tiêu đề phải ghi `Time Wallet v1.0.2`.
2. **Dữ liệu cũ còn nguyên.** Xem tab Nhật ký và Phân tích: bản ghi và ngân sách đã có trước đó còn đủ.
3. **Khởi động lạnh (F6).** Vuốt tắt app rồi mở lại 3 lần. Nếu app dừng lại: mở lại, bấm ⓘ, đọc mục "Nhật ký lỗi gần nhất", bấm **Sao chép** và gửi lại. Ghi rõ vừa làm gì trước khi dừng.
4. **Thêm giờ thủ công.** Ở Tổng quan bấm nút **＋** → chọn "Học tập" → chọn **30p** → **Lưu** (4 thao tác). Kiểm tra: số lớn ở thẻ đầu và biểu đồ tròn cập nhật; tab Nhật ký có dòng mới và có "Tổng …" của ngày. Thử thêm một mục cho "Hôm qua".
5. **Bấm Lưu hai lần thật nhanh.** Chỉ được 1 bản ghi.
6. **Ngân sách (F5).** Tab Phân tích → đặt ngân sách tuần: các nút `5h 10h 15h 20h` phải nằm gọn, không xuống dòng từng chữ. Thử cỡ chữ lớn (Cài đặt → Hiển thị → Cỡ chữ) và khi bàn phím đang mở. Đặt ngân sách ưu tiên thứ 4: phải bị từ chối kèm thông báo.
7. **Wally khi không có mạng.** Bật chế độ máy bay. Gõ "Học 45p" → ra đề xuất ngay (cục bộ). Nếu đã bật AI: gõ "Chiều nay tôi học bài" → phải báo lỗi ngắn, có **Thử lại / Ghi thủ công / Đóng**, không treo; nội dung đã gõ còn nguyên. Bấm "Hủy ✕" khi đang gửi phải dừng ngay.
8. **AI thật (chỉ nếu bạn muốn và có khóa).** Ở tab Wally bấm ⚙ → nhập khóa → **Kiểm tra kết nối** (chỉ gửi chữ "ping"). Ghi lại kết quả và mã `req-…`. Gửi một câu thử và xác nhận rằng chỉ khi bấm **Xác nhận** mới có bản ghi. Mục "Chẩn đoán gần đây" không được chứa khóa hay nội dung bạn gõ.
9. **Nền và khởi động lại.** Bắt đầu một phân tích, rồi chuyển sang app khác / xoay màn hình / vuốt tắt app. Mở lại: không được sập, không có đề xuất "ma" xuất hiện, dữ liệu đã lưu còn nguyên.
10. **Chụp màn hình.** Chụp Tổng quan, form Thêm giờ, Nhật ký, hộp thoại Ngân sách và tab Wally (thành công, lỗi, đang gửi) ở cỡ chữ mặc định và cỡ chữ lớn, gửi lại kèm model máy và phiên bản Android. Báo mọi chỗ bị cắt chữ, chồng nút hoặc nút khó bấm.

Ghi kết quả từng bước PASS/FAIL. Kết quả các bước 3, 7, 8, 9 quyết định F6 và A17/A18; hiện tất cả đang là **NOT_TESTED**.
