# WALLY v1.1.0 — Personal Time & AI Coach: kế hoạch HOW (AI-Master)

**Nguồn:** `OWNER WHAT — WALLY v1.1.0` (2026-11-16). Một version, một candidate tích hợp, một Release Gate cuối. Không Wave. Kiểm thử theo Level L1–L5 và theo phạm vi ảnh hưởng thật.
**Baseline:** v1.0.13 (`bfc00ae9…`, prompt-7). Dữ liệu người dùng và các bất biến TimeLayer phải được bảo toàn.

## 1. Quyết định thiết kế (đã chốt, không cần Owner duyệt từng bước)

### Dữ liệu (L3)
- **DB v3 → v4** bằng `MIGRATION_3_4` thêm-không-phá-hủy:
  - `ledger_records.tagId TEXT NULL` (+ index `index_ledger_records_tagId`). Bản ghi cũ: `tagId = NULL` ("chưa gắn Tag") — **không suy diễn lại, không sửa `category`, `layer`, thời lượng**.
  - Bảng `tags(id, name, nameKey, categoryKey, archivedAt NULL, createdAt, updatedAt)`: `nameKey` = tên chuẩn hóa (bỏ dấu, thường) để chống trùng và tìm kiếm. Lưu trữ = đặt `archivedAt` (không xóa dòng, lịch sử giữ nguyên); đổi tên/đổi Category chỉ đổi dòng `tags`, bản ghi trỏ theo `tagId` nên lịch sử không mất. Bản ghi giữ `category` của thời điểm ghi nhận (ảnh chụp); Tag đổi Category chỉ áp cho bản ghi MỚI (mục tiêu theo Tag không đổi theo Category).
  - Bảng `goals(id, groupId, targetType 'CATEGORY'|'TAG', targetKey, kind 'MIN'|'MAX', period 'DAY'|'WEEK', targetSeconds, status 'ACTIVE'|'PAUSED'|'ENDED', validFrom, validTo NULL, createdAt, updatedAt)`. **Sửa mục tiêu = đóng phiên bản cũ (`validTo`) và tạo phiên bản mới (cùng `groupId`)** nên tiến độ quá khứ vẫn tính theo mục tiêu hiệu lực lúc đó.
  - Giữ nguyên `category_budgets` (Budget không đổi; Goals là năng lực mới, không thay thế).
- Mọi ghi vào sổ vẫn đi qua `LedgerValidator` + đường commit hiện có; `idempotencyKey`, `userConfirmedAt` giữ nguyên.

### Logic thời gian & mục tiêu (L2)
- `TimeRange.durationSeconds(start, end)` thuần: qua nửa đêm, bằng nhau = không hợp lệ (không 24 giờ), tối đa 24 giờ; "Bây giờ" = đồng hồ hiện tại tại lúc bấm Lưu và hiển thị trước khi lưu.
- `GoalProgress` thuần: tính theo ngày/tuần (T2–CN), đếm **chỉ** bản ghi đúng đối tượng (Tag ⇒ `tagId`; Category ⇒ `category`), chỉ phần đã diễn ra (PLANNED không tính), MIN ⇒ còn thiếu, MAX ⇒ còn dư/vượt; tạm dừng/kết thúc không tạo "thất bại".
- Tag gợi ý: gần đây (`lastUsed`) + thường dùng (số lần 30 ngày) + tìm theo `nameKey`; tối đa ~8 chip trước khi tìm.

### Ghi nhận (L1/L2)
- Màn nhập thủ công thiết kế lại: ưu tiên "bạn đã làm gì" (Tag/gợi ý) → 3 chế độ bằng một bộ chọn gọn: **Khoảng giờ** (bắt đầu, kết thúc hoặc "Bây giờ", tự tính phút), **Thời lượng** (preset + phút), **Bấm giờ** (chuyển sang tab Đồng hồ với Tag đã chọn). AI Chat giữ nguyên. Mặc định: hôm nay, Category suy từ Tag (đổi được), ghi chú tùy chọn.
- Timer lưu có Tag (cùng danh sách chip). `TimeLayer` không đổi: khoảng giờ ⇒ REPORTED_TIMED (có `startedAt/endedAt`), thời lượng ⇒ REPORTED_UNTIMED, bấm giờ ⇒ MEASURED như hiện tại.

### AI Chat gắn Tag (L4 nhẹ)
- Prompt nhận danh sách Tag của người dùng (tên + Category, tối đa ~30 gần đây/thường dùng) trong phần NGỮ CẢNH (phần động, không đổi tiền tố tĩnh cache). Đề xuất có thể mang `tag` (tên khớp một Tag có sẵn; tên mới ⇒ app hỏi tạo Tag khi xác nhận). Mọi đề xuất vẫn chỉ là đề xuất.

### AI Time Coach (L4)
- **Ứng dụng tính số, AI kể chuyện.** App dựng *digest* xác định từ sổ + mục tiêu (N ngày gần nhất: phút theo Tag/Category theo ngày, tiến độ mục tiêu, số ngày có dữ liệu, phần chưa gắn giờ tách riêng). Digest gửi kèm yêu cầu Coach; model chỉ được trả JSON `{story, suggestions[]}`.
- **Guard ở app (không tin model):** (1) mọi con số trong `story` phải thuộc tập số của digest (tái dùng cơ chế "số có căn cứ" của `ReplyGuard`); (2) cấm khẳng định đã ghi/lưu/đổi/xóa; (3) cấm từ phán xét ("lãng phí", "vô ích", "lười"…) và khẳng định nhân quả không có căn cứ (danh sách có kiểm thử); (4) "chưa có bản ghi" ≠ "không làm": câu chữ phải nói "chưa ghi nhận"; (5) dữ liệu mỏng (< 3 ngày) ⇒ nhận xét thận trọng + nói rõ mức đầy đủ; (6) `suggestions` chỉ là thẻ đề xuất mục tiêu/kế hoạch có nút Xác nhận/Bỏ, **không bao giờ tự tạo**.
- Điểm vào: thẻ Coach ở Tổng quan/Mục tiêu ("Phân tích hôm nay / tuần này") và câu hỏi tự nhiên trong chat (định tuyến sang Coach khi hỏi về tiến độ mục tiêu/so sánh nhiều ngày).

### Giao diện
- Tab hiện có giữ nguyên. Thêm: **Quản lý Tag** (trong menu ⋮ và từ màn nhập), **Mục tiêu** (thẻ ở Tổng quan + màn danh sách/chỉnh sửa trong Phân tích hoặc màn riêng), thẻ **Coach**.

## 2. Truy vết WHAT → HOW → bằng chứng

| Nghiệm thu | Thực hiện | Level / bằng chứng |
|---|---|---|
| 1. 14:00–15:12 = 72 phút | `TimeRange` + màn khoảng giờ | L2 unit biên; L1 UI |
| 2. Kết thúc "Bây giờ" | `TimeRange.now` + hiển thị trước lưu | L2 |
| 3. Chỉ biết thời lượng | chế độ Thời lượng | L2 |
| 4. Tag dùng ở thủ công, timer, AI Chat | `tags`, `tagId`, prompt + `ProposalDraft.tag` | L2/L3, L4 (dev-set) |
| 5. Đổi tên/lưu trữ Tag giữ lịch sử | tham chiếu `tagId`, `archivedAt` | L3 integration (SQLite thật) |
| 6. Mục tiêu chạy bộ 30 phút/ngày | `goals` | L2 |
| 7. Gym không tăng tiến độ chạy bộ | đếm theo `tagId` | L2 (ca bắt buộc) |
| 8. AI phân biệt đọc sách / chơi game cùng Category | digest theo Tag | L4 |
| 9. Đề xuất theo mục tiêu, không phán xét/không tự đổi dữ liệu | guard Coach + thẻ xác nhận | L2 guard + L4 |
| 10. So sánh nhiều ngày, hợp mức đầy đủ dữ liệu | digest N ngày + `coverage` | L2 + L4 |
| 11. Nâng cấp giữ dữ liệu | `MIGRATION_3_4` | L3 migration + trên máy QA nâng cấp từ v1.0.13 |
| 12. Không regression | toàn bộ suite hiện có (537) giữ xanh | L5 |

## 3. Chính sách kiểm thử (L1–L5, theo rủi ro)
- **L1** UI/layout: build + test hợp đồng tĩnh; chụp màn hình trên WallyQA (Android 9) một lần cho candidate cuối.
- **L2** logic: unit/boundary tự động (JVM).
- **L3** DB/migration: kiểm trên SQLite thật (sqlite-jdbc đã có trong dự án), gồm nâng cấp 3→4 với dữ liệu mẫu + bất biến; trên thiết bị: một lần nâng cấp từ APK v1.0.13.
- **L4** AI: golden/dev-set + Live Eval **chỉ phần bị ảnh hưởng** (Coach, Tag trong chat) với cấu hình Claude đã được Owner duyệt (không đụng Gemini); an toàn 100%.
- **L5** tích hợp cuối: toàn bộ unit + một lượt smoke trên WallyQA. Không chạy lại ma trận nhiều Android; Android 16 giữ NOT_TESTED. Mỗi thay đổi nhỏ trong quá trình làm chỉ chạy Level bị ảnh hưởng.

## 4. Giới hạn đã biết khi lập kế hoạch
- Không có Android 16; chỉ Android 9 qua LDPlayer. Không điện thoại thật.
- Gemini đang bị chặn bởi nhà cung cấp; Coach được kiểm với Claude Sonnet 4.6 (đúng cấu hình Owner duyệt).
