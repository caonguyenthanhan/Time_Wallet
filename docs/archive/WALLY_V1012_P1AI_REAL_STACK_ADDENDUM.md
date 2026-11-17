# WALLY v1.0.12 — Phụ lục ĐÍNH CHÍNH nguyên nhân P1-AI (đo trên lớp HTTP thật)

**Ngày:** 2026-11-15 · **Single Writer:** AI-Master · **Quyền:** Manager Directive WALLY v1.0.12 LIVE VALIDATION (mục "xác nhận hoặc bác bỏ giả thuyết root cause") + Owner Authorization WALLY LIVE API QA
**Candidate không đổi:** `1a034e02f0cc3a0bce23b15add8a815f11d9ef68dddb6eefe857804bdd3cb5a8` (versionCode 13). Không có thay đổi mã nguồn trong phụ lục này.
**Không gọi API bằng khóa thật; không dùng khóa/dữ liệu của Owner.** Mọi khóa dưới đây là chuỗi do AI-Master tự bịa.

## 1. Đính chính (sai ở hồ sơ trước)
`WALLY_V1012_ROOT_CAUSE_P1.md`, `WALLY_V1012_WAVE_DIGEST.md`, `WALLY_V1012_ACCEPTANCE_RECORD.md` viết rằng:
1. khóa chứa **ký tự ngoài ASCII** (dấu gạch dài "–", NBSP, ký tự rộng-bằng-không, chữ có dấu) làm lớp mạng của Android ném lỗi cục bộ; và
2. đó là **"con đường duy nhất"** khớp bộ ba (MALFORMED + không mã HTTP + vài ms).

Cả hai đều **không đúng như đã viết**. Ở v1.0.12 hai điều đó dựa trên lớp kết nối mô phỏng trong test JVM (`AndroidLikeConnection`), vốn từ chối mọi ký tự > 0x7E. Mô hình đó **sai** so với Android thật. Các test v1.0.12 vẫn đúng theo hợp đồng của ứng dụng (khóa lạ bị chuẩn hóa/chặn) nhưng **không phải bằng chứng về hành vi nền tảng**.

## 2. Số đo trên lớp HTTP thật (`HttpURLConnection` của hệ thống)
Lớp Java thăm dò `HttpProbe` chạy bằng `app_process` trong emulator: với từng ký tự `ch`, đặt `Authorization: Bearer sk-A<ch>B` rồi chạy đúng chuỗi lệnh của app (`setRequestProperty` → `getOutputStream().write` → `getResponseCode`) tới `https://localhost:1/` (loopback; không có lưu lượng ra ngoài).

| Ký tự trong giá trị header | Android 9 (API 28, LDPlayer `WallyQA`) | Android 16 (API 36, AVD `TimeWallet_QA`) |
|---|---|---|
| U+0000–U+0008, **U+000A (LF)–U+001F (gồm CR)**, U+007F | `IllegalArgumentException` ngay tại `setRequestProperty` | giống hệt |
| U+0009 (Tab), U+0020–U+007E | đi tiếp tới kết nối (`ConnectException`) | giống hệt |
| **Ký tự ≥ U+0080 đã đo** (U+0080–U+00FF đầy đủ, gồm NBSP U+00A0 và `é`; cộng 23 điểm mã đại diện: `–` U+2013 và U+2010–2015, U+2018/2019/201C/201D, U+200B–200D, U+2060, U+FEFF, U+3000, U+2212, U+FF0D, chữ Việt U+1EA0/1EA1, CJK U+4E2D, Thái U+0E01) | **được chấp nhận** (đi tới kết nối) | giống hệt |

Không đo riêng `•` (U+2022) trên Android; chỉ suy ra cùng nhóm "ngoài ASCII" từ các điểm mã đã đo. Không có điểm mã ≥ U+0080 nào trong tập đo bị từ chối.

Bằng chứng: `out/wally_live_v1012/evidence/mechanism/httpprobe_android9.txt`, `httpprobe_android16.txt`.
**Kết luận (trong tập ký tự đã đo, hai bản Android):** một khóa chỉ làm hỏng yêu cầu **cục bộ** khi chứa **ký tự điều khiển** (xuống dòng LF/CR, U+0001–U+001F trừ Tab, DEL). Dấu gạch dài, NBSP, chữ có dấu… **không** gây ra lỗi cục bộ. Chưa đo Android 10–15 và điện thoại thật của Owner: hành vi của lớp HTTP có thể khác theo bản/nhà sản xuất.

## 3. Tái hiện trong chính ứng dụng, Android 9 thật (`mech_*.json`)
Base URL = `https://localhost:1/v1` (không ra mạng), khóa giả, câu gửi: "Code từ lúc 14h đến giờ". Mỗi ca = một lần lưu khóa + một yêu cầu, ngay sau khởi động lạnh.

| Bản | Khóa | Kết quả (diagnostics trong ứng dụng) |
|---|---|---|
| v1.0.11 `af50d6cc…` | chỉ ASCII | `fault=OFFLINE http=- latency=1111ms retry=1` (có kết nối, bị từ chối, thử lại) |
| v1.0.11 | có U+0001 | **`fault=MALFORMED http=- latency=93ms retry=0`**; thẻ: "AI trả về dữ liệu không hợp lệ…" |
| v1.0.11 | có LF (U+000A) | **`fault=MALFORMED http=- latency=217ms retry=0`** |
| v1.0.11 | có `–` (U+2013) | thẻ: "Không kết nối được mạng…" (không phải MALFORMED); chẩn đoán của ca này bị mất do script khởi động lại app — chỉ có ảnh/văn bản màn hình |
| v1.0.12 `1a034e02…` | chỉ ASCII | `OFFLINE … err=ConnectException` |
| v1.0.12 | có LF | **chuẩn hóa → dùng được**: `OFFLINE … err=ConnectException` |
| v1.0.12 | có `–`, NBSP | chuẩn hóa → dùng được: `OFFLINE … err=ConnectException` |
| v1.0.12 | có U+0001 | hộp thoại chặn: "Khóa API có ký tự không hợp lệ ở vị trí 8 (ký tự điều khiển)"; không lưu, không gửi |
| v1.0.12 | có `é` | hộp thoại chặn: "…vị trí 5 (ký tự ngoài ASCII…)"; không lưu, không gửi (chủ ý của v1.0.12: khóa có chữ có dấu không bao giờ hợp lệ với nhà cung cấp) |

Ghi chú trung thực: độ trễ MALFORMED của mình (93/217 ms) cao hơn của Owner (4–37 ms) vì mỗi ca của mình là yêu cầu đầu tiên sau khởi động lạnh trên emulator phần mềm; điểm phân biệt là `MALFORMED` + `http=-` + `retry=0` + không có `ConnectException`, tương phản với khóa sạch (`retry=1`, ~1,1 s).

## 4. Gateway trả lời thế nào với khóa SAI (để loại giả thuyết khác)
Hai yêu cầu tối giản tới `api.foza.ai/v1/chat/completions` bằng **khóa bịa** (ASCII sai; khóa có `•` kiểu giá trị bị che của bảng điều khiển). Evidence: `gateway_invalid_key_probe.json`.
- Cả hai: **HTTP 401**, `application/json`, thân `{"error":{"message":"Invalid API key","type":"invalid_request_error","param":null,"code":"401"}}`, độ trễ đo từ máy QA **566 ms** và **1311 ms** (sau Cloudflare).
- v1.0.11 ánh xạ 401/403 thành `AUTH` **kèm mã HTTP** (`GeminiProposalClient.kt` của commit `b2c8dba`, dòng 269). Vậy một khóa sai/bị che mà tới được gateway cho `fault=AUTH http=401` và trễ cỡ nửa giây — **không thể** là "MALFORMED, không mã HTTP, 4–37 ms".

## 5. Các giả thuyết sau khi đo
| Giả thuyết | Trạng thái | Lý do |
|---|---|---|
| **H-A**: khóa đã lưu trên máy Owner chứa **ký tự điều khiển** (thường là xuống dòng/CR khi dán) ⇒ lớp HTTP ném `IllegalArgumentException` trước khi có kết nối ⇒ v1.0.11 gán `MALFORMED` | **Được ủng hộ mạnh; CHƯA xác nhận bằng máy/khóa của Owner** | Tái hiện đúng chữ ký trong app trên lớp HTTP thật; 4–37 ms loại trừ mọi vòng mạng tới gateway (đo ≥ 0,5 s ngay cả với phản hồi 401); trường nhập khóa nhận được LF (ca `lf`) |
| H-A′: ký tự ngoài ASCII (gạch dài, NBSP, chữ có dấu đã đo; `•` suy ra) | **BÁC BỎ làm nguyên nhân** của triệu chứng này (trên Android 9 và 16) | hai bản đó chấp nhận; nếu yêu cầu tới được gateway thì là 401 `AUTH` có mã HTTP, không phải MALFORMED |
| H-B: gateway trả lỗi bọc trong HTTP 200 cho khóa sai | **BÁC BỎ cho khóa sai** | gateway trả 401 thật (mục 4). Chưa loại cho các lỗi khác (hết hạn mức, model không đăng ký): v1.0.12 vẫn phân loại `{"error":…}` trong 200 |
| H-C: can thiệp mạng cục bộ trả 200 nội dung lạ | Ít khả năng | TLS sẽ lỗi nếu bị chen; 4 ms không thể có khi qua mạng thật |

## 6. Việc đóng phần còn lại của P1-AI (đúng yêu cầu tối thiểu)
Chỉ máy của Owner xác nhận được khóa **của Owner** có ký tự điều khiển hay không, và AI-Master không được đọc khóa đó. Một lần chạy xác nhận hoặc bác bỏ: **cài v1.0.12, gửi "Code từ lúc 14h đến giờ", ⚙ → "Sao chép chẩn đoán".**
- Thành công ngay (khóa tự chuẩn hóa khi đọc) ⇒ H-A **xác nhận** bằng hiệu ứng (khóa là thứ duy nhất đổi hành vi cục bộ).
- `KEY_INVALID` kèm vị trí ⇒ H-A xác nhận (còn ký tự lạ ngoài khả năng chuẩn hóa).
- Dòng có `err=<lớp>` và/hoặc `http=<mã>` khác ⇒ H-A **bác bỏ**; dòng đó chứa nguyên nhân mới.
Bước bổ sung không cần Owner: bảng điều khiển nhà cung cấp có ghi 5 yêu cầu tại thời điểm sự cố hay không (H-A ⇒ không có yêu cầu nào đến gateway).

## 7. Tác động lên kết luận đã giao
- **Candidate v1.0.12 giữ nguyên.** Sửa của v1.0.12 xử lý đúng cơ chế đã đo (khóa có LF/CR được chuẩn hóa; ký tự điều khiển còn lại bị chặn kèm vị trí; lỗi cục bộ không còn bị gọi là MALFORMED; lỗi gateway có `http=`/`err=`), nên không cần đổi mã.
- Nhãn trong hồ sơ cũ "FIXED" cho P1-AI phải đọc là **"MITIGATED — cơ chế xác nhận trên Android 9 thật; nguyên nhân trên máy Owner chưa xác nhận"**.
- Lớp mô phỏng `AndroidLikeConnection` trong test JVM nên được sửa cho đúng hành vi đã đo (chỉ từ chối ký tự điều khiển) ở lần chạm mã tiếp theo; việc này **không** làm thay đổi hành vi sản phẩm và chưa làm trong đợt này để giữ nguyên candidate.
