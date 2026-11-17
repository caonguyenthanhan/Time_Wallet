# WALLY v1.0.12 — Nguyên nhân gốc hai lỗi P1 của v1.0.11

**Ngày:** 2026-11-15 · **Single Writer:** AI-Master · **Quyền:** MANAGER DIRECTIVE — WALLY AI RELIABILITY / WALLY PRODUCT RELIABILITY
**Baseline giữ nguyên:** APK v1.0.11 `af50d6cc568633d6f5befad74fce675fc64f17805dc71ab1f042396e1a5079ed` và `out/wally_candidate_v1011/`, `out/wally_evidence_v1011/` không bị sửa (đã kiểm hash).

---

> **ĐÍNH CHÍNH 2026-11-15:** các câu dưới đây về "ký tự ngoài ASCII (dấu gạch dài, NBSP, chữ có dấu…) làm lớp mạng ném lỗi" và "con đường duy nhất" **không đúng** khi đo trên lớp HTTP thật: trên Android 9 và 16 chỉ **ký tự điều khiển** (xuống dòng LF/CR, U+0001–U+001F trừ Tab, DEL) bị từ chối; ký tự ngoài ASCII đã đo đều đi qua; gateway trả **401 có mã HTTP** cho khóa sai. Xem `WALLY_V1012_P1AI_REAL_STACK_ADDENDUM.md`. Nhãn P1-AI = **MITIGATED** (cơ chế xác nhận trên Android 9 thật; nguyên nhân trên máy Owner chưa xác nhận).

## P1-AI — "Cloud MALFORMED 5/5" khi Owner dùng Wally ("Code từ lúc 14h đến giờ")

### Dữ liệu quan sát (do Owner/Manager cung cấp)
5 yêu cầu Cloud · 5 lần `MALFORMED` · **không có mã HTTP** · độ trễ **4–37 ms**.

### Suy luận từ mã nguồn (đã kiểm từng con đường)
Mọi nơi trong v1.0.11 có thể cho ra `MALFORMED` (`GeminiProposalClient.kt`, `StrictJson.kt`) và chữ ký mà mỗi nơi để lại:

| # | Con đường tới `MALFORMED` | Xảy ra khi nào | Độ trễ điển hình | Có mã HTTP trong chẩn đoán v1.0.11? | Khớp quan sát? |
|---|---|---|---|---|---|
| 1 | `classify()` gán `IllegalArgumentException / IllegalStateException / JSONException` thành `MALFORMED` | **Lỗi cục bộ TRƯỚC khi có kết nối**, ví dụ lớp mạng của nền tảng từ chối giá trị header `Authorization` | vài ms (37 ms lần đầu do nạp lớp, ~4 ms các lần sau) | Không (ngoại lệ không mang mã) | **CÓ** |
| 2 | `parseAndValidateResponseJson` / `StrictJson` ném `MALFORMED` | **Sau** một phản hồi HTTP 200 (thân không phải JSON, sai kiểu, thiếu `choices`…) | ≥ thời gian mạng + thời gian model (hàng trăm ms trở lên) | Không (v1.0.11 ném không kèm mã 200) | Không: độ trễ 4 ms không thể có khi đã nhận phản hồi LLM |
| 3 | Thân phản hồi > 512 KB | Sau khi đọc dài | lớn | Có (mã 200) | Không |
| 4 | `StackOverflowError` | Sau phản hồi | lớn | Không | Không |

Chỉ **con đường 1** khớp cả ba đặc điểm (MALFORMED + không mã HTTP + vài ms). Trong con đường 1, đầu vào duy nhất thay đổi giữa các máy là **khóa API** (`Authorization: Bearer <khóa>`; các header khác là hằng số). Vì vậy: **khóa đã lưu của Owner chứa ít nhất một ký tự mà header HTTP không chuyển được** (~~dấu gạch dài, khoảng trắng không ngắt, ký tự rộng-bằng-không, ngoặc kép cong~~ — **đã bị bác bỏ bằng số đo**; còn lại: **ký tự điều khiển như xuống dòng LF/CR khi dán khóa**). Lớp mạng của Android ném `IllegalArgumentException` ngay khi đặt header → `classify()` cũ gán nhãn "AI trả về dữ liệu không hợp lệ" → thông báo sai bản chất, người dùng không có cách tự sửa. Thêm một lỗi chẩn đoán: `failure()` mặc định `source = CLOUD`, nên lỗi cục bộ còn bị đếm là "yêu cầu cloud" (lý do Owner thấy "5 Cloud requests").

### Tái hiện TRƯỚC khi sửa → THẤT BẠI; SAU khi sửa → THÀNH CÔNG
- **Trước (mã v1.0.11, thăm dò):** `out/wally_evidence_v1012/probe_before_fix.txt` — khóa có ký tự rộng-bằng-không / xuống dòng / chữ có dấu (**trên lớp kết nối MÔ PHỎNG; Android thật chỉ từ chối ký tự điều khiển**): `fault=MALFORMED`, `status=null`, độ trễ **2–11 ms**, đúng chữ ký của Owner; khóa sạch thì thành công.
- **Hồi quy hai cây** (`V1012OwnerRegressionTest`, chỉ dùng API đã có ở v1.0.11):
  - trên bản trích của v1.0.11 → **3/3 ĐỎ** (`khóa 21 ký tự phải dùng được expected null, but was MALFORMED`; `Values should be different. Actual: MALFORMED`; `expected 16:30 but was 17:00`) — `out/wally_evidence_v1012/red_green/red_v1011/`, `red_on_v1011_console.txt`;
  - trên v1.0.12 → **3/3 XANH** — `red_green/green_v1012/`.

### Cái gì được chứng minh, cái gì KHÔNG
- **Chứng minh:** (a) ~~con đường duy nhất trong mã khớp quan sát~~ → đính chính: một con đường khớp; độ trễ 4–37 ms loại trừ mọi vòng mạng (gateway trả 401 sau 0,5–1,3 s); (b) tái hiện được đúng chữ ký bằng lớp kết nối mô phỏng nền tảng; (c) mã mới xử lý các ký tự rác phổ biến và báo đúng bản chất khi không sửa được.
- **KHÔNG chứng minh được:** ký tự cụ thể trong khóa của Owner (không được và không cần đọc khóa). Đây là nguyên nhân *khớp mọi số đo*, chưa phải xác nhận bằng chính máy của Owner. Từ v1.0.12 chẩn đoán **tự nhận diện**: cùng lỗi sẽ hiện `KEY_INVALID` kèm vị trí và loại ký tự; nếu suy luận sai, bản ghi sẽ có **mã HTTP** và **tên lớp ngoại lệ** (`err=…`) thay cho "-". Không cần Owner gửi khóa.

### Sửa (không chỉ đổi thông báo)
1. `ApiKeyHygiene.normalize`: bỏ khoảng trắng/xuống dòng/ký tự vô hình, ngoặc bao quanh, tiền tố `Bearer`; đổi các loại dấu gạch Unicode về `-`. Áp dụng khi **lưu**, khi **đọc khóa đã lưu** (khóa hỏng sẵn trên máy Owner được sửa ngay, không cần nhập lại nếu chỉ là ký tự rác) và ngay trước khi gửi.
2. `ApiKeyHygiene.check` + `AiFault.KEY_INVALID`: nếu vẫn còn ký tự không hợp lệ → báo rõ vị trí/loại, **không mở kết nối**, không tính là yêu cầu cloud, nút "Mở cấu hình ⚙" hiện ra; lưu khóa như vậy bị từ chối ngay ở màn cấu hình.
3. Nếu nền tảng vẫn từ chối header vì lý do khác → `KEY_INVALID` kèm tên lớp ngoại lệ (không còn MALFORMED).
4. `classify()` không còn gán lỗi cục bộ là `MALFORMED`; lỗi **đọc phản hồi** được bọc riêng thành `MALFORMED` kèm **mã HTTP 200** và tên lớp lỗi. Thân `{"error":…}` trong HTTP 200 được phân loại (AUTH / RATE_LIMIT / NOT_FOUND / UNKNOWN).
5. `AiEvent.errorClass` (chỉ tên lớp, không có thông điệp/nội dung/khóa) và `err=` trong chẩn đoán.

---

## P1-NAV — Android 9 mất thanh điều hướng khi mở Wally dù bàn phím không hiện

### Bằng chứng (không giả định trước)
APK chẩn đoán dùng một lần (`out/wally_evidence_v1012/harness/NAVDIAG_ONLY_NOT_A_CANDIDATE.apk`, mã nguồn đã hoàn nguyên, hash `67e84215…` kiểm lại) ghi `WALLYDIAG` trên WallyQA (Android 9):

```
tab=OVERVIEW isImeVisible=true imeBottomPx=0 navBottomPx=0 systemBarsBottomPx=0 statusTopPx=0 showBar=true
tab=CHAT     isImeVisible=true imeBottomPx=0 navBottomPx=0 systemBarsBottomPx=0 statusTopPx=0 showBar=false   (mInputShown=false)
```
(`nav_probe_ldq_android9_v1011_diag.json`, ảnh `screens/nav_ldq_android9_v1011_diag_wally_open*`.)

- `WindowInsets.isImeVisible` là `true` **ngay cả ở tab Tổng quan** và khi không có bàn phím; **ime inset ở đáy = 0**. Đó không phải suy đoán về bàn phím: cờ này đứng yên ở giá trị khởi tạo. Cùng lúc cửa sổ không chuyển inset nào cho Compose (`statusTopPx=0` dù cửa sổ có thanh trạng thái 48px).
- Cơ chế (suy luận từ số đo): app đặt `targetSdk 35` nhưng chỉ Android 15+ ép giao diện tràn viền; trên máy cũ hơn cửa sổ tự "fit" các thanh hệ thống và không chuyển inset cho Compose, nên `isImeVisible` không bao giờ được cập nhật khỏi `true`. v1.0.11 ẩn thanh tab khi `isImeVisible && tab == Wally` ⇒ thanh tab biến mất ngay khi mở Wally.
- **Phạm vi:** đã đo chắc trên Android 9 (LDPlayer). Có thể ảnh hưởng mọi máy không chuyển inset (suy luận, chưa đo trên điện thoại thật hay Android 10–14).

### Sửa
`MainActivity.kt`: thanh tab chỉ ẩn khi **có ime inset THẬT ở đáy** (`WindowInsets.ime.getBottom(density) > 0`) và đang ở Wally; quyết định tách thành hàm thuần `shouldShowBottomBar(tab, imeBottomPx)` có test. Không còn đọc `isImeVisible`. Máy không chuyển inset ⇒ thanh tab luôn hiện (không bao giờ kẹt trong Wally).

### Xác minh trên candidate (`nav_verify_*`)
| Môi trường | Mở Wally | Nhập liệu | Đóng bàn phím | Back | Chuyển tab |
|---|---|---|---|---|---|
| Android 9 (WallyQA, bàn phím chủ LDPlayer báo "đang hiện" nhưng không có inset) | 5 tab hiện | 5 tab hiện | `Back` đóng bàn phím, vẫn ở trong app, 5 tab | `Back` ở Tổng quan thoát app (như baseline) | 7 lượt chuyển Wally ↔ các tab đều đúng nội dung |
| Android 16 (AVD, bàn phím mềm thật) | 5 tab hiện | bàn phím hiện ⇒ thanh tab **ẩn** (đúng thiết kế), chữ gõ vào ô | `Back` đóng bàn phím ⇒ 5 tab **hiện lại**, vẫn ở Wally | `Back` ở Tổng quan thoát app (như baseline) | 7 lượt chuyển tab đúng nội dung (`nav_verify_avd_android16_v1012.json`) |

Cả hai môi trường chạy trên đúng APK candidate (SHA ở `WALLY_V1012_ACCEPTANCE_RECORD.md`). Ảnh: `screens/nav_ldq_android9_v1012_*`, `screens/nav_avd_android16_v1012_*`.
