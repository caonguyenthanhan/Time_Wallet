# WALLY — Phụ lục Live Eval cho v1.0.12 (câu của Owner) — CHƯA CHẠY

**Quan hệ với kế hoạch gốc:** `WALLY_LIVE_EVAL_PLAN_v1.2.md` (SHA-256 `0b5144c80ceda1ba05ba19a82aab34a9445ab7b8ea072dc25bc5f9d8b9417ea0`) **không bị sửa** — catalog 40 câu, nhóm G (7 case), ngưỡng và quy tắc đóng băng giữ nguyên. Phụ lục này chỉ **thêm** các case H sau khi đã chạy xong catalog, **không tính vào mẫu số của T1**, và không đổi expected của câu gốc.

## Ngưỡng nghiệm thu của Manager (nhắc lại, không đổi)
- T1: ≥ **38/40** ở lượt 1 · T2/T3: an toàn **100%**, ghi trái phép/ghi trùng/lộ khóa/số sai = **0** · T7: nhóm G **7/7** · critical cases **100%**.
- Kết quả gắn với **đúng candidate** (SHA-256 ghi trong `WALLY_V1012_ACCEPTANCE_RECORD.md`) và đúng cấu hình provider/model đã dùng (ghi host + tên model + `PROMPT_VERSION` từ ⓘ/⚙).

## Case bổ sung H (chạy sau catalog; đồng hồ máy phải sau 14:30 và không qua nửa đêm)
Mỗi câu gửi **một** yêu cầu; ghi giờ máy lúc gửi `T`. "Đạt khi" là tiêu chí duy nhất.

| # | Câu | Đạt khi |
|---|---|---|
| H1 | Code từ lúc 14h đến giờ | đúng 1 đề xuất Công việc, bắt đầu **14:00**, kết thúc = `T` (lệch ≤ 2 phút), thời lượng = `T − 14:00`; vẫn chờ xác nhận; sau **Xác nhận**: đúng 1 dòng sổ 14:00–`T`, bấm lại không ghi thêm |
| H2 | Code từ 14h30 đến giờ | 1 đề xuất, 14:30 → `T` |
| H3 | Học từ 8h sáng đến 11h | 1 đề xuất Học tập, 08:00–11:00, 180 phút |
| H4 | Họp từ 2h chiều đến giờ | 1 đề xuất Công việc, **14:00** → `T` |
| H5 | Ngày mai 9h họp 1 tiếng | 1 đề xuất kế hoạch (Dự kiến) ngày mai 09:00–10:00 |
| H6 | Code từ 14h | **không** có đề xuất; Wally hỏi lại (xong lúc mấy giờ / đang làm tiếp) |
| H7 | Code từ 14h đến giờ, đọc sách 30p | đúng 2 đề xuất: Code 14:00 → `T`, Đọc sách 30 phút không giờ |
| H8 | (khóa) dán khóa API có xuống dòng/khoảng trắng thừa hoặc dấu gạch dài | lưu được và dùng được (đã chuẩn hóa) **hoặc** báo `Khóa API có ký tự không hợp lệ ở vị trí N`, không bao giờ báo "AI trả dữ liệu không hợp lệ" |

## Điều kiện cần Owner (BLOCKER của AI Live — nêu đúng yêu cầu)
1. **Khóa API do Owner tự nhập** trong ⚙ trên thiết bị của Owner (hoặc hồ sơ thử), bật AI. Không gửi khóa qua chat; không ghi khóa vào báo cáo. AI-Master không có và không được dùng khóa này.
2. **Cho phép gọi API thật** cho phiên Live Eval (theo quy tắc: chỉ khi Owner/Manager cấp). Chỉ dữ liệu thử.
3. Cài đè đúng APK v1.0.12 (kiểm SHA-256, ⓘ phải ghi `Time Wallet v1.0.12`).
4. Chạy theo `WALLY_LIVE_EVAL_PLAN_v1.2.md` mục 2 & 6 (sao chép **Chẩn đoán gần đây** sau mỗi nhóm ≤ 15 yêu cầu; không vuốt tắt app giữa chừng).
   *Lưu ý:* mục 2 của kế hoạch gốc nói "Không emulator, không ADB" và "Cài đè v1.0.6": với v1.0.12 hiểu là "cài đè v1.0.12"; việc chạy trên điện thoại thật của Owner vẫn là cách chuẩn.
5. Kết quả gắn nhãn `AI_LIVE_PASS` / không đạt do **Owner/Manager** ghi; AI-Master không tự cấp.

## Chẩn đoán mới giúp đọc kết quả
Từ v1.0.12 mỗi dòng chẩn đoán có thêm `err=<tên lớp ngoại lệ>` khi lỗi; lỗi sau phản hồi HTTP 2xx hiện `http=200`; `KEY_INVALID` không còn bị đếm là yêu cầu cloud. Nếu H1 vẫn lỗi trên máy Owner, bản sao chẩn đoán là đủ để xác định nguyên nhân mà không cần khóa.
