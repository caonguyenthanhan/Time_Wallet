# QUYẾT ĐỊNH CHO OWNER — Hybrid Intent Routing (một trang)

**Ngày:** 2026-11-16 · **Nguồn:** `WALLY_HYBRID_INTENT_ROUTING_EVAL.md` + `WALLY_HYBRID_INTENT_ROUTING_AUDIT.md` (kiểm toán độc lập; mọi số do mã tính, không gọi API, không đụng thiết bị).
**Trạng thái:** KHÔNG triển khai gì. Chính sách API-First giữ nguyên cho tới khi Owner duyệt.

## Kết quả đo (cổng đã được kiểm toán và vá, đồng hồ cố định)
| Chỉ số | Giá trị |
|---|---|
| Tỉ lệ yêu cầu xử lý được **không cần Cloud** | catalog 44 câu: **25%** · bộ câu mới 293 câu: **38,9%** · gộp: **37,1%** (gói nhỏ nhất S1+S2+S3: **34,7%**) |
| Độ chính xác phần xử lý cục bộ | **125/125** trên bộ câu do nhóm tự viết; **chưa có bộ kiểm mù và chưa có dữ liệu người dùng thật** |
| Nhận diện nhầm (false-local) | 0 trên bộ câu đã chốt. **Nhưng** bản cổng đầu để lọt **57 lỗ hổng** khi kiểm toán viên tấn công (ví dụ "Chạy bộ 5h30" bị ghi 330 phút, "Họp từ 2h đến 15h" ghi 13 giờ, "Hôm nay tôi tập bao nhiêu phút?" trả tổng mọi danh mục); đã vá hết, nhưng cho thấy quy tắc rất mong manh nếu mở rộng |
| Bỏ sót (câu đáng lẽ xử lý cục bộ nhưng chuyển lên AI) | khoảng 12% (chủ ý thận trọng) |
| Cache kết quả theo câu | **bác bỏ**: phát lại ở thời điểm khác cho kết quả sai/lỗi thời 78,6% (cùng ngày 13%, hôm sau 85%, tuần sau 94,5%) và sẽ lặp lại cả các câu AI hiểu sai (A5, E6) |
| Chi phí | tiết kiệm chỉ khoảng **US$0,4–0,9 mỗi 1.000 yêu cầu** (số cuộc gọi Cloud giảm ~35%): không phải lý do để làm |
| Độ trễ | câu xử lý cục bộ ~1 ms (đo trên JVM; Android chưa đo) so với p50 ≈ 8,9 s của Cloud: **đây là giá trị thật** |
| Timeout 30 s | **không có bằng chứng** việc định tuyến giảm timeout: trong log thật, 0/19 câu đủ điều kiện cục bộ bị timeout; 6 timeout thuộc 4 câu khác. Việc tăng hạn chót lên 45 s là biện pháp khác, **không** làm giảm độ trễ |
| An toàn | định tuyến không có trạng thái, không đọc kết quả AI, không chạm CSDL; đề xuất cục bộ vẫn là đề xuất cần xác nhận và đi qua đúng các guard/đường ghi sổ hiện có; số liệu truy vấn do engine sổ tính tại thời điểm trả lời |

## Đề xuất (thay đổi nhỏ nhất tạo giá trị)
Một bộ định tuyến tất định, không trạng thái, **tắt mặc định sau cờ tính năng**, chạy trước ở **chế độ "bóng"** (vẫn gọi Cloud, chỉ ghi lại việc bộ định tuyến có đồng ý hay không) để đo tỉ lệ phủ và tỉ lệ nhận diện nhầm trên dữ liệu thật trước khi bật.
- **Bước 1 (rủi ro thấp nhất):** chỉ câu hỏi số liệu theo kỳ lịch (hôm nay/hôm qua/tuần này/tuần trước/tháng này, so sánh hai kỳ): ~12% yêu cầu, **không** đi vào đường ghi sổ.
- **Bước 2:** ghi nhận một mệnh đề "việc + thời lượng" và "từ Xh đến giờ" (giờ hiện tại tính lúc gửi, không lưu).
- Khối lượng: khoảng 370 dòng mã sản xuất + khoảng 600 dòng test chuyển từ nguyên mẫu; tắt = lật cờ, không có dữ liệu cần dọn.
- **Không bao gồm:** cache, học từ kết quả AI, nhận dạng khoảng giờ tường minh, câu nhiều mệnh đề, chạy khi chưa có khóa API, bất kỳ động cơ AI/LLM cục bộ nào.

## Cần Owner quyết định (chọn một)
1. **Duyệt Bước 1 ở chế độ bóng** để đưa vào bản sau v1.0.13 (khuyến nghị).
2. Duyệt cả Bước 1 + 2 ở chế độ bóng.
3. Hoãn: giữ API-First thuần cho tới khi có thêm dữ liệu.

Điều mình **không** khuyến nghị: bật định tuyến thật ngay (chưa có dữ liệu người dùng thật) hoặc dùng cache theo câu.
