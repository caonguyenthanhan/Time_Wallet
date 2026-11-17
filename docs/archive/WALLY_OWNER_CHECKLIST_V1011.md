# WALLY OWNER CHECKLIST — v1.0.11 (UI Polish) — nghiệm thu trên điện thoại thật

- **APK:** `out/wally_candidate_v1011/TimeWallet-MVP-v1.0.11-WALLY-UIPOLISH-candidate-debug.apk`
- **SHA-256 phải khớp:** `af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed`
- **Phiên bản:** `com.timewallet.mvp`, `versionName = 1.0.11`, `versionCode = 12`
- **Quay lui nếu cần:** APK `v1.0.10` (`1d834a83e4e87a7afdf2b99f5c71786dede9ab15cb7006ea18bf5fd4ba08536b`) giữ nguyên, cài đè lại được (`adb install -r -d`).
- Bản này chỉ đổi **giao diện**. Nghiệp vụ, dữ liệu, đồng hồ, phân tích, bảo vệ của Wally và bản sửa lỗi F6 giữ nguyên.
- Chưa có kết quả nào từ điện thoại thật: **chưa** có `DEVICE_PASS`, `F6_DEVICE_VERIFIED`, `KBD-01`, `AI_LIVE_PASS`, `SPEC_ACCEPTED`.

## 10 bước (khoảng 10 phút)

1. **Cài đè, không gỡ app.** Kiểm SHA-256 trước khi chuyển sang máy. Cài đè lên bản đang có → mở app → dữ liệu Nhật ký / Ngân sách / khóa AI cũ **vẫn còn**. Xác nhận đúng bản: `⋮` → `Thông tin & nhật ký lỗi` → tiêu đề hộp thoại phải là **`Time Wallet v1.0.11`**.
2. **Tổng quan.** Thấy ngay một thẻ xanh với số giờ **đã ghi nhận** thật lớn, ba nút (Đồng hồ / Thêm giờ / Hỏi Wally), biểu đồ vòng theo danh mục. Không còn đoạn giải thích dài. Nút `⋮` ở góc trên phải mở menu.
3. **Đồng hồ.** Vòng tròn đồng hồ và nút `▶ Bắt đầu đo` nằm ngay màn hình đầu, không cần cuộn. Bấm bắt đầu → kết thúc & lưu → bản ghi xuất hiện trong Nhật ký. Mục `Chưa biết làm gì?` (xúc xắc) đóng sẵn, bấm mới mở.
4. **Wally (bàn phím thật — KBD-01).** Gõ tiếng Việt có dấu bằng bàn phím bạn hay dùng (Gboard / Samsung / Laban Key): ô nhập luôn nằm trên bàn phím, không bị che; thanh tab tạm ẩn khi gõ để có chỗ cho hội thoại. Nút gửi là mũi tên tròn (không còn nút "Tách đề xuất").
5. **F6 smoke trên máy thật.** Cold start (thoát hẳn khỏi Recents rồi mở lại) → tab Wally → gửi lần lượt `Xin chào`, `Học 45p`, `Hủy tất cả đề xuất`. App không được crash/đơ. Nếu chưa cấu hình AI thì Wally báo trung thực, nội dung đã gõ được giữ nguyên.
6. **Nhật ký.** Dạng timeline theo ngày, mỗi hoạt động có biểu tượng danh mục + thời lượng + mốc giờ. Bộ lọc là một hàng chip ngắn (vuốt ngang được). Nút `＋` nổi chỉ có ở màn này; ở Tổng quan dùng ô `Thêm giờ`. Thử `✎` sửa và `×` xóa (có hộp xác nhận).
7. **Dữ liệu & sao lưu.** `⋮` → `Dữ liệu & sao lưu`: `Xuất JSON`, `Nạp dữ liệu mẫu`, `Xóa toàn bộ bản ghi` (có xác nhận riêng; giữ lại ngân sách). Các nút này **không còn** nằm trong màn Nhật ký.
8. **Phân tích.** Biểu đồ cột 7 ngày và biểu đồ vòng phân bổ nằm **trước**; ngân sách tuần nằm sau. `ⓘ Cách tính` ở cuối trang mở phần giải thích. Thử `+ Đặt ngân sách`, rồi xóa ngân sách: hộp xác nhận `Xóa ngân sách?` vẫn hoạt động như v1.0.10.
9. **Cỡ chữ lớn.** Đặt cỡ chữ hệ thống lớn (≈1.3×): đồng hồ, tiêu đề Wally, chú giải biểu đồ, nhãn tab không bị cắt hay thành cột một chữ.
10. **Nhật ký lỗi (chỉ khi có).** Mục này chỉ áp dụng nếu hộp `Thông tin & nhật ký lỗi` đang có khối nhật ký lỗi — **đừng cố tạo lỗi**. Nếu có log mới, dòng đầu có dạng `Thời điểm: … · Phiên bản khi lỗi: 1.0.11 (12)`. Log cũ không có phiên bản vẫn hiện `Phiên bản khi lỗi: Không xác định` (đã chấp nhận ở v1.0.10). Nếu không có khối nhật ký lỗi: ghi "không áp dụng".

## Ghi kết quả (để trống đến khi Owner làm)

| Mục | Kết quả | Ghi chú |
|---|---|---|
| Thiết bị / Android | | |
| 1 Cài đè giữ dữ liệu | | |
| 2–3, 6–8 Giao diện & thao tác | | |
| 4 KBD-01 bàn phím thật | | |
| 5 F6 smoke | | |
| 9 Cỡ chữ lớn | | |
| 10 Nhật ký lỗi (nếu có; nếu không: "không áp dụng") | | |
| Live Eval v1.2 (khóa riêng của Owner, ghi riêng cho SHA này) | | |

Live Eval v1.2 dùng kịch bản chuẩn `WALLY_LIVE_EVAL_PLAN_v1.2.md` (SHA-256 `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0`); kết quả của `v1.0.6`–`v1.0.10` **không** thay cho `v1.0.11`.
