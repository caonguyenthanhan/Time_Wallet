# Checklist cho Owner — bản 1.0.3 (WALLY API-FIRST)

APK: `out/wally_candidate_apifirst/TimeWallet-MVP-v1.0.3-WALLY-APIFIRST-candidate-debug.apk`
SHA256: `fd058632ec39a8f9184a07ce9c6a6a7808c17a7e5fe70e09787ad55dd598dcb2`

Thay thế `WALLY_OWNER_CHECKLIST.md` ở các bước về AI (bước 7 và 8 cũ mô tả Local-first). Chỉ dùng dữ liệu thử; **không gỡ app**. Nếu Android báo xung đột chữ ký, dừng và xuất JSON (nút ⇩) trước.

1. **Cài đè và phiên bản.** Cài đè bản 1.0.3 (versionCode 4). Bấm ⓘ: phải ghi `Time Wallet v1.0.3`. Dữ liệu cũ ở Nhật ký/Ngân sách còn đủ.
2. **Chưa có khóa.** (Nếu máy đang có khóa cũ, tắt công tắc AI trong ⚙.) Ở tab **Wally** gõ "Học 45p" rồi bấm gửi. Kỳ vọng: hiện "Wally chưa được cấu hình AI", có **Mở cấu hình ⚙** và **Ghi thủ công**, nội dung vẫn nằm trong ô nhập, **không** có đề xuất nào. Chip chế độ ghi "Chưa cấu hình AI".
3. **Ghi thủ công không cần khóa.** Ở Tổng quan bấm **＋** → danh mục → 30p → Lưu. Dashboard, Nhật ký, Ngân sách cập nhật; Đồng hồ vẫn chạy.
4. **Cấu hình AI.** Tab Wally → ⚙ → nhập khóa → bật công tắc → **Kiểm tra kết nối** (chỉ gửi chữ "ping"). Ghi lại kết quả và mã `req-…`. Lưu.
5. **"Học 45p" gọi API thật.** Gõ "Học 45p". Kỳ vọng: có chữ "Đang hỏi Wally… Hủy ✕" ở nút, sau đó hiện đề xuất (và có thể có lời đáp Wally). Mở ⚙ → "Chẩn đoán gần đây": phải có một dòng `source=CLOUD` cho yêu cầu này. Chưa có bản ghi nào cho tới khi bạn bấm **Xác nhận**.
6. **Câu hỏi và ý định hỗn hợp.** Gõ "Hôm nay tôi dùng bao nhiêu giờ?", rồi "Học 30p và tuần này tôi dùng bao nhiêu giờ?". Kỳ vọng: thẻ "Wally (AI)" với số liệu, và (câu thứ hai) cả đề xuất lẫn số liệu. So sánh con số với tab Phân tích. Ghi nhận mọi câu AI trả lời sai hoặc bỏ mất một phần.
7. **Phủ định và hủy.** Gõ "Hôm nay tôi không học 2 tiếng" (không được có đề xuất), "Không học 2h nhưng code 1h" (chỉ còn code 1h), rồi tạo một đề xuất và gõ "Hủy tất cả đề xuất" (xóa draft ngay, không gọi mạng, không đụng sổ cái).
8. **Lỗi mạng.** Bật chế độ máy bay, gửi "Học 45p". Kỳ vọng: lỗi ngắn, **Thử lại / Ghi thủ công / Đóng**, nội dung ô nhập còn nguyên, **không** có đề xuất giả. Tắt chế độ máy bay, bấm Thử lại. Thử thêm khóa sai (nhập khóa sai trong ⚙): phải báo lỗi khóa và có nút Mở cấu hình.
9. **Hủy và nền.** Gửi một câu rồi bấm **Hủy ✕** ngay; xoay màn hình hoặc chuyển sang app khác khi đang gửi. Không được sập, không có đề xuất "ma" hiện lên sau đó. Bấm Xác nhận hai lần thật nhanh: chỉ 1 lần ghi.
10. **Chụp màn hình và báo lại.** Chụp: Tổng quan, form Thêm giờ, hộp thoại Ngân sách, tab Wally ở các trạng thái (chưa cấu hình, đang gửi, thành công, lỗi) và ⚙ → Chẩn đoán. Nếu app dừng đột ngột: mở lại, ⓘ → "Nhật ký lỗi gần nhất" → Sao chép và gửi kèm thao tác trước đó (đây là bằng chứng để xử lý F6).

Lưu ý riêng tư: khi bật AI, mọi câu bạn gõ ở tab Wally được gửi tới nhà cung cấp đã chọn. Dùng dữ liệu thử.

Kết quả các bước 4–9 quyết định A17 (thiết bị) và A18 (AI thật). Hiện tất cả là **NOT_TESTED**.
