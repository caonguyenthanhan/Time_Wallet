# WALLY QA REPORT — Origin binding & redirect (v1.0.5)

Ngày: 2026-11-13 · Theo chỉ thị Manager sau v1.0.4 · Verdict: **CANDIDATE_UNVERIFIED** (F6 UNRESOLVED; chưa có AI thật, chưa có thiết bị).

## 0. Kết luận ngắn

| Câu hỏi | Kết quả |
|---|---|
| Khóa API có bị gửi sang origin khác qua **redirect** không? | **Không.** Kiểm bằng HttpURLConnection thật của JDK và hai máy chủ HTTP cục bộ (hai cổng = hai origin): 301, 302, 303, 307, 308 đều không được theo, origin thứ hai nhận 0 yêu cầu. Chạy trên **v1.0.4 chưa sửa gì** cũng PASS. Có kiểm đột biến: bật `instanceFollowRedirects = true` thì test FAIL (origin thứ hai nhận yêu cầu), nên test có độ nhạy. |
| Có lỗ hổng thực tế nào không? | **Có một lỗ hổng, đã sửa**: khóa chỉ được gắn với **host**, không gắn với **cổng**. Đổi `https://host/v1` thành `https://host:8443/v1` không bị phát hiện, khóa vẫn được gửi tới origin khác (cổng khác = máy chủ/dịch vụ khác). |
| Mức độ | Thấp-trung bình: cần người dùng tự đổi Base URL trong ⚙ (hoặc sửa cấu hình lưu trên máy). Không có đường tấn công từ xa. Cấu hình mặc định (`api.foza.ai`, cổng 443) không bị ảnh hưởng. |

## 1. Artifact

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_origin/TimeWallet-MVP-v1.0.5-WALLY-ORIGIN-candidate-debug.apk` |
| SHA256 | `e80f752248dcce6ccfc7c0b6adf6b2b8e2d49ac1c3772f5c28738c8ca16d2e1d` (đọc từ `APK_SHA256.txt`) |
| Package/version | `com.timewallet.mvp` · versionCode **6** · versionName **1.0.5** (`aapt dump badging`); chữ ký debug, chứng chỉ SHA-256 `402f1d41…2530d` trùng các bản trước ⇒ cài đè được |
| v1.0.4 | Không đổi: `338ddc3426beff52a54f9d656cdf7bdb6df349deb04d88b7912dfe4d64611600` (đã `sha256sum -c`: OK). Evidence v1.0.4 không đụng |
| Diff | 3 file đổi + 1 file test mới: `SOURCE_DIFF_vs_v1.0.4.patch` (+307/−5). Đổi: `TimerAndSettingsStore.kt`, `GeminiProposalClient.kt`, `app/build.gradle.kts` |
| Rollback | `out/wally_origin_snapshot_pre/source_v1.0.4_pre_ORIGIN.tar` + manifest (khớp manifest v1.0.4) |
| Test | **224/224 PASS** (213 + 11 mới), 0 lỗi. Golden 66/66, holdout 46/46. Không sửa test nào đã có (`CHANGED_FILES_vs_v1.0.4.txt`) |

## 2. Audit

| Hạng mục | Phát hiện | Evidence |
|---|---|---|
| Redirect (mọi mã 3xx, cả đường kiểm tra kết nối) | An toàn: `instanceFollowRedirects = false` đặt trước khi đặt header `Authorization`; 3xx trả lỗi, không có đề xuất | `WallyOriginBindingTest` 4 test redirect (socket thật); `out/wally_evidence_origin/origin_BEFORE_fix_on_v1.0.4.xml` (PASS trên v1.0.4); `mutation_redirects_ENABLED.xml` (8/9 FAIL khi bật redirect) |
| Nội dung chat gửi sang origin khác | Không: body chỉ tới origin đã cấu hình | test "request body … only ever reaches the configured origin" |
| **Gắn khóa với origin** | **Lỗ hổng**: so khớp chỉ theo host. RED trên v1.0.4: 4/4 test liên quan FAIL (`origin_BEFORE_fix_on_v1.0.4.xml`) | Sau sửa: 9/9 PASS (`origin_AFTER_fix.xml`) |
| Sai lệch bộ đọc URI (lúc xác thực) và URL (lúc kết nối) | Chưa tìm được đầu vào gây lệch, nhưng trước đây không có chốt kiểm. Đã thêm chốt: URL sắp mở phải có đúng origin đã xác thực, nếu không ném `CONFIG` và **không mở kết nối** | Test bảng 8 đầu vào (hoa/thường, dấu chấm cuối, `:443`, cổng khác, IPv6, IP) cho URI và URL cùng origin |
| Điểm thoát mạng duy nhất | Chỉ `GeminiProposalClient` mở kết nối và đặt `Authorization` (1 chỗ) | test tĩnh quét toàn bộ nguồn chính; thứ tự: chặn redirect → kiểm origin → mở kết nối |
| Quyền | Chỉ `INTERNET`; không `usesCleartextTraffic`, không `networkSecurityConfig` nới lỏng | test `WallySecurityStaticTest` (cũ) + `aapt` |
| Userinfo / query / fragment / không https | Bị từ chối (từ v1.0.4) | `WallyEightLayerTest` (cũ) |

## 3. Sửa gì

1. **Origin = scheme + host + cổng.** `AiPreferences.extractValidatedHttpsHost` nay trả `host` (cổng 443 hoặc không nêu) hoặc `host:cổng`; cổng ngoài 1..65535 bị từ chối. Mọi nơi so khớp (khi gửi, khi lưu, khi nạp, trong hộp thoại ⚙, ViewModel) đều đi qua hàm này nên nhất quán. Khóa đã lưu bằng cổng mặc định giữ nguyên khớp (không phải nhập lại).
2. **Chốt origin trước khi mở kết nối** (`AiPreferences.originOf(url)` so với origin đã xác thực) trong đường gửi và đường kiểm tra kết nối.

## 4. Thay đổi hành vi cần Owner biết

- Cấu hình mặc định và mọi URL cổng 443: **không đổi**.
- Nếu trước đây bạn đã lưu Base URL có **cổng riêng** (ví dụ `https://host:8443/v1`), sau khi cài đè khóa sẽ bị giữ lại và Wally báo "nhập lại khóa"; hãy vào ⚙ nhập lại khóa. Đây là hành vi an toàn có chủ đích (không mất dữ liệu sổ cái).
- Ở ⚙, host hiển thị có thể kèm `:cổng`.

## 5. Giới hạn trung thực

- Redirect được kiểm bằng HttpURLConnection của JDK trên máy phát triển; Android dùng cài đặt HttpURLConnection khác (OkHttp bên trong). Cờ `instanceFollowRedirects = false` là API chuẩn cho cả hai, nhưng hành vi trên Android thật chưa được quan sát: **NOT_TESTED trên thiết bị**.
- Proxy hệ thống và chứng chỉ do người dùng cài (MITM chủ động của chính máy) nằm ngoài phạm vi; TLS dùng kho tin cậy mặc định, không ghim chứng chỉ.
- Tên miền giống hệt về chữ nhưng khác ký tự (IDN/homograph) không được chuẩn hóa thêm; người dùng tự nhập Base URL.
- Vẫn chưa có kết quả AI thật, thiết bị thật; F6 UNRESOLVED. Không có thay đổi nào khác trong đợt này (không Redis, vector DB, MCP, data cache, multi-round).

## 6. Đính chính cho báo cáo 8 tầng (v1.0.4)

Bộ đếm "lệnh cục bộ" và nhãn ý định `CONTROL` trong chẩn đoán chỉ tăng khi gọi **trực tiếp** `GeminiProposalClient` (như trong test). Trong ứng dụng thật, ViewModel xử lý lệnh hủy draft **trước** khi gọi client nên lệnh hủy không để lại dòng chẩn đoán nào. Điều này không ảnh hưởng an toàn (lệnh hủy vẫn không gọi mạng, không đụng sổ cái), chỉ làm số đếm đó luôn bằng 0 trên máy thật. Không sửa trong đợt này vì không cần thiết.

## 7. Đánh giá chất lượng Wally API-first

Golden 66 và holdout 46 chỉ chứng minh bộ phân tích cục bộ, không chứng minh Wally API-first. Bảng chấm cho ghi nhận, truy vấn, hỗn hợp, phủ định, hủy và lỗi mạng khi có API thật: `WALLY_LIVE_EVAL_PLAN.md`. Chưa chạy (không có credential): AI_LIVE_PASS NOT_TESTED.
