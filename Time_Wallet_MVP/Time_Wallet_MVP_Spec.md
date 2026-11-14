# TIME WALLET — Product & Technical Design v1.0

> **Tuyên ngôn:** Thời gian là tài sản hữu hạn và không thể tích lũy. Quản lý thời gian để hiểu và chủ động phân bổ, không để phán xét nghỉ ngơi hay tối đa hóa thời gian làm việc.

## 1. Vấn đề và giá trị

**Vấn đề:** Người dùng thường biết đã làm gì, nhưng không có bức tranh nhất quán về *khi nào*, *bao lâu*, và *mức độ phù hợp với ưu tiên cá nhân*.

**Giải pháp:** Kết hợp đo thời gian thực (timer) với nhật ký ngôn ngữ tự nhiên (AI chat), duy trì một sổ cái duy nhất và thống kê minh bạch theo độ tin cậy dữ liệu.

**Chỉ số thành công giai đoạn MVP:**
- Thêm một hoạt động sau khi kết thúc bằng ≤ 3 thao tác chính nếu dùng timer.
- Tạo đề xuất từ một câu chat và xác nhận trước khi ghi sổ.
- Không phát sinh hai bản ghi có mốc giờ trùng nhau trong cùng một tài khoản ở chế độ hoạt động loại trừ nhau.
- Thống kê ngày/tuần phản ánh đúng phạm vi dữ liệu, không cộng thời lượng chưa có mốc giờ vào phần thời gian xác định.
- Người dùng có thể xuất, xóa dữ liệu của mình.

## 2. Mô hình sổ cái

Mỗi ngày có 24 giờ theo múi giờ người dùng, nhưng tại các ngày chuyển DST, độ dài ngày lịch có thể khác 24 giờ; dữ liệu phải dựa trên mốc thời gian thực và ranh giới ngày trong timezone.

**Ba lớp dữ liệu thực tế:**
1. `MEASURED`: timer ghi được `startedAt`, `endedAt`, độ dài suy ra bằng phép trừ.
2. `REPORTED_TIMED`: người dùng khai báo khoảng thời gian cụ thể; có giờ nhưng không phải dữ liệu được đo.
3. `REPORTED_UNPLACED`: người dùng chỉ khai báo thời lượng; chưa có vị trí trên trục thời gian.

**Lớp tách biệt:** `PLANNED`: lịch dự kiến, *không* tính là thời gian đã sử dụng.

- Mọi bản ghi lưu `source`, `createdAt`, `userConfirmedAt`, `category`, `title`, `date`, `durationSeconds`, `startedAt?`, `endedAt?`, `timezone`, `status`.
- Không suy luận giờ bắt đầu hoặc kết thúc từ dữ liệu chỉ có thời lượng; AI có thể đề xuất cách nhập bổ sung, không ghi âm thầm.
- Thời lượng không có mốc giờ không được trừ vào biểu đồ 24 giờ. Phần còn lại hiển thị "chưa ghi nhận", không phải "rảnh" hoặc "lãng phí".
- Với bản ghi cùng kiểu loại trừ nhau, giao nhau về thời gian là lỗi cần giải quyết trước khi lưu. Bản production có thể cho phép các lớp hoạt động chồng nhau (ví dụ nghe podcast khi di chuyển) nhưng phải áp dụng quy tắc thống kê rõ ràng; MVP v1 ngăn chồng lấn.
- Phân tích tuần nên có hai con số riêng: *giờ có vị trí chính xác* và *giờ chỉ khai báo thời lượng*.

## 3. Luồng người dùng

### A. Timer
1. Chọn danh mục/tên việc → **Bắt đầu**.
2. Hệ thống ghi thời điểm bắt đầu, chạy đồng hồ ngay cả khi giao diện chuyển màn hình.
3. **Kết thúc & lưu** → kiểm tra va chạm → ghi vào Time Ledger.
4. Nếu mất kết nối, phiên được giữ cục bộ; đồng bộ server ở giai đoạn sau.

### B. AI Chat
1. Nhập: "Hôm nay học Feynman 45 phút, xem YouTube 30 phút".
2. AI trả về 2 **đề xuất**: nhãn, danh mục, thời lượng, mức độ chắc chắn, phần dữ liệu chưa rõ.
3. Người dùng sửa dữ liệu tùy ý, thêm khoảng giờ nếu biết.
4. Ấn **Xác nhận & lưu** từng đề xuất; hệ thống đối chiếu schema/độ dài/va chạm thời gian.
5. Không có giờ → lưu `REPORTED_UNPLACED`. Có giờ được xác nhận → `REPORTED_TIMED`.

### C. Insights
- Dashboard: số phút có mốc giờ trong ngày; thời lượng chưa xếp giờ; khoảng chưa ghi nhận.
- Nhật ký: phân loại theo ngày, bộ lọc có mốc giờ / chưa xếp giờ; quyền xóa và chỉnh sửa.
- Insights: tổng 7 ngày, xu hướng, phân bổ theo danh mục, phản tư theo mục tiêu của cá nhân.
- Không đánh giá ngủ/giải trí là kém giá trị một cách mặc định.

## 4. Màn hình chính

1. **Overview** — ví thời gian hôm nay, thanh phân bổ, nhật ký gần đây, 2 phím tắt.
2. **Timer** — một công việc hiện hành, đồng hồ lớn, start/stop.
3. **Chat** — nhập câu tự nhiên, gợi ý bản ghi cần xác nhận.
4. **History** — tra cứu, sửa/xóa, phát hiện thiếu mốc giờ.
5. **Insights** — biểu đồ tuần, thống kê danh mục và câu hỏi phản tư.

## 5. Đặc tả API AI đề xuất (production)

AI **không** được cập nhật cơ sở dữ liệu trực tiếp. AI chỉ phân tích thành đề xuất có cấu trúc; backend kiểm tra rồi mới ghi khi có thao tác xác nhận của người dùng.

```json
{
  "proposals": [
    {
      "title": "Học kỹ thuật Feynman",
      "category": "learn",
      "durationSeconds": 2700,
      "dateLocal": "YYYY-MM-DD",
      "startLocal": null,
      "endLocal": null,
      "timeEvidence": "duration_only",
      "missingFields": ["startLocal", "endLocal"],
      "needsConfirmation": true
    }
  ]
}
```

**Các bất biến backend:**
- `startAt`, `endAt` phải có cùng lúc hoặc cùng null; `endAt > startAt`.
- Nếu có hai mốc giờ, `durationSeconds = endAt - startAt` (giới hạn sai số làm tròn).
- Ngày tương lai không thể ghi là **thực tế đã diễn ra**. Phải chuyển sang planned.
- Chỉ nhận danh mục trong danh sách cho phép; không tin dữ liệu từ LLM nếu chưa validate.
- Check overlap cả trước khi hiển thị và trong transaction commit để chống race condition.
- Idempotency key cho thao tác confirm để tránh lưu trùng khi người dùng bấm nhiều lần.
- Không gửi nhật ký cá nhân đầy đủ cho AI nếu không cần thiết; hỗ trợ tắt AI và xóa dữ liệu.

## 6. Đề xuất công nghệ

**Prototype:** HTML/CSS/JS một file, localStorage. Không có máy chủ, không có LLM; phân tích chat hiện tại là rule-based để kiểm tra UX.

**Production giai đoạn 1:** React + TypeScript PWA, IndexedDB (lưu offline), kiến trúc domain tách UI/timer/ledger/AI suggestions, schema validation (ví dụ Zod), test unit và end-to-end.

**Production giai đoạn 2 (nếu cần đồng bộ):** Backend API, database với ràng buộc overlap, xác thực, đồng bộ có quy tắc conflict resolution, encrypted transport; tính năng AI được xử lý qua backend và được bật theo opt-in. Không nhúng API key của LLM trong frontend.

**Lưu ý kỹ thuật timer:** Khi chạy nền, dùng persisted timestamps để tính lại thay vì tin vào `setInterval`; cần xử lý chỉnh giờ hệ thống, timezone, DST, chuyển ngày và ngủ/khởi động lại thiết bị.

## 7. Ngoài phạm vi MVP

- Tự động theo dõi mọi hoạt động ứng dụng/hệ điều hành.
- Tích hợp lịch và đồng bộ nhiều thiết bị.
- Tính "giá tiền" cho mỗi giờ / chấm điểm đạo đức cho hoạt động.
- AI tự ra quyết định hoặc tự tạo mốc thời gian không có bằng chứng.
- Phân tích năng suất qua camera/microphone.

## 8. Nghiệm thu MVP (DoD)

- [ ] Start/stop timer ghi được thời lượng thực, sau reload vẫn khôi phục được phiên.
- [ ] Chat đề xuất tách được một hoặc nhiều hoạt động và **không tự ghi vào sổ**.
- [ ] Câu "học 45 phút" trở thành `REPORTED_UNPLACED` nếu không có giờ.
- [ ] Câu "14:00 đến 15:00" có thể ghi `REPORTED_TIMED` sau khi xác nhận.
- [ ] Bản ghi 14:30–15:30 bị chặn nếu đã có 14:00–15:00.
- [ ] Biểu đồ 24h không cộng thời lượng unplaced; 7-day chart dựa trên dated intervals.
- [ ] Có thể xuất JSON và xóa dữ liệu.
- [ ] UI sử dụng tốt trên viewport 390px; không bắt người dùng đọc đoạn chat dài.

## 9. Phát triển sau MVP

- Sửa bản ghi đã lưu / chuyển từ unplaced sang timed.
- Mục tiêu phân bổ theo tuần và loại tài sản thời gian (sức khỏe, học tập, gia đình, công việc).
- Chế độ Feynman sau phiên học: "Giải thích lại điều vừa học trong 3 câu"; lịch ôn tập cách quãng có đồng ý.
- Tích hợp calendar read-only và trợ lý tổng kết ngày dựa trên dữ liệu đủ chắc chắn.
