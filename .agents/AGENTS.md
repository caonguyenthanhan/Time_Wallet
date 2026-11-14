# Project Rules — Time Wallet (Android)

## Liên kết Ecosystem (SSOT)
- Kho rule & skill trung tâm: `D:\An-tool-ecosystem`
- Skill SSOT: `D:\An-tool-ecosystem\.trae\skills`
- Context Hygiene: `D:\An-tool-ecosystem\rules\context-hygiene.md`

## Quy tắc dự án
1. **Android Development**:
   - Mở và chạy dự án bằng **Android Studio**.
   - **KHÔNG BAO GIỜ** khởi chạy Emulator hoặc chạy lệnh cài đặt/chạy app trực tiếp lên thiết bị từ terminal của AI.
   - Logic nghiệp vụ (Time Ledger, Overlap Detection, Natural Language Parser, DST/Timezone Math) phải tách biệt trong tầng `domain` và có Unit Test đầy đủ.
2. **Bất biến sản phẩm (Time Wallet MVP Spec v1.0)**:
   - Ba lớp dữ liệu thực tế: `MEASURED`, `REPORTED_TIMED`, `REPORTED_UNPLACED` và lớp tách biệt `PLANNED`.
   - Không tự suy đoán giờ bắt đầu/kết thúc từ dữ liệu chỉ có thời lượng.
   - Không cộng `REPORTED_UNPLACED` vào biểu đồ 24h hoặc biểu đồ cột 7 ngày có mốc giờ.
   - Chặn chồng lấn thời gian (`overlap`) giữa các bản ghi có mốc giờ.
   - AI chỉ tạo đề xuất (`Proposal`), không tự ý ghi vào cơ sở dữ liệu khi chưa có xác nhận của người dùng.

