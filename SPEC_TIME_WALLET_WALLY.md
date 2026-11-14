# SPEC — TIME WALLET / WALLY EXPERIENCE & AI RELIABILITY

**Version:** 1.0 | **Date:** 2026-11-13 | **Audience:** AI-Master / coding agent

## 0. Mandate — tự làm, tự test, tự sửa

Đọc toàn bộ SPEC và source hiện tại, xác định baseline, triển khai trực tiếp, chạy test, sửa lỗi phát hiện, chạy lại test và build APK. Tự quyết HOW trong phạm vi này; không dừng để xin duyệt từng bước, không chỉ trả kế hoạch. Không mở Wave/Gate mới, không viết lại app, không push/publish/release. Không thay đổi signing key/package ID tùy tiện. Nếu thiếu thiết bị hoặc credential, tiếp tục mọi phần có thể kiểm chứng; ghi rõ phần chưa kiểm chứng, không giả PASS. Không xóa dữ liệu người dùng để xử lý lỗi. Chỉ dùng dữ liệu synthetic cho test; không gửi nhật ký thật lên cloud để debug.

**Thứ tự:** baseline → crash/AI reliability → manual entry → visual UX → regression → APK/evidence. Có thể làm song song phần độc lập. Mỗi finding phải có reproduction hoặc evidence, fix và test tương ứng. Không giới hạn số vòng sửa-test cần thiết trong scope.

## 1. Bối cảnh và mục tiêu

Time Wallet Android là ví thời gian, Wally là tên trợ lý AI hiện có (không tạo chatbot thứ hai). Tham khảo Rolly: icon danh mục, nút thêm nhanh, nhật ký dạng giao dịch, donut chart, progress ngân sách; không sao chép mascot, thương hiệu hay bố cục dài dòng. Wally có nhận diện riêng, không sao chép WALL·E. Giữ xanh lá và light theme hiện có; dark theme là P2, không chặn bản ổn định.

Baseline theo báo cáo trước: candidate v3, 93/93 test PASS, golden 66/66 (39 câu hỏi), holdout 46/46; DEVICE_NOT_TESTED. Các con số có thể thuộc các bộ đếm khác nhau, không cộng cơ học; Master phải chạy lại và ghi tên suite/count thực tế. F1–F4 đã được báo cáo sửa, cần bảo toàn. F5: preset 20h/tuần xuống dòng từng ký tự trong Budget Dialog (ảnh Owner). F6: Android báo Time Wallet tiếp tục dừng (ảnh Owner); chưa có stack trace, chưa biết thao tác kích hoạt. Owner báo lỗi khi gọi AI; không mặc định F6 do AI hoặc Budget. Owner không thấy cách thêm giờ thủ công; kiểm tra source trước khi bổ sung hoặc đưa luồng có sẵn ra UI.

### Bất biến sản phẩm

- Tracking: ghi nhận thời gian đã sử dụng. Accounting: dữ liệu chính xác, không trùng. Budgeting: phân bổ theo mục tiêu. Reflection: hiểu giá trị thời gian đầu tư; giữ Daily Review/Feynman hiện có.
- Thời gian dự kiến không phải thời gian đã sử dụng. Thời lượng không có mốc giờ không được cộng vào chỉ số có trục thời gian; có thể cộng tổng theo ngày nếu có ngày hợp lệ.
- 24h là mốc ngày thông thường, không phải thời gian rảnh; 24h trừ tổng ghi nhận là phần chưa ghi nhận theo quy ước, không phải thời gian còn lại thực tế. Xử lý ngày dài/ngắn theo timezone nếu logic hiện có hỗ trợ; không tạo số âm gây hiểu nhầm. Không tự suy diễn khoảng thời gian trống hoặc thời điểm bắt đầu.
- Timer, manual, AI dùng chung nghiệp vụ và nguồn dữ liệu; chỉ bản ghi hợp lệ đã commit mới xuất hiện trong tổng số. AI không phải điều kiện để sử dụng app.

## 2. Kiến trúc AI 8 tầng — trách nhiệm, không phải 8 dịch vụ

| Tầng | Áp dụng Time Wallet | Điều kiện đạt |
|---|---|---|
| 01 Model & Model Serving | Giữ provider/model hiện có; adapter rõ ràng, timeout, mapping lỗi, kiểm tra kết nối. | Mock success/error; model thực được kiểm chứng riêng nếu có quyền/credential; không hardcode bí mật. |
| 02 System Prompt & Prompt Management | Prompt có version, intent rõ, output schema, tiếng Việt ngắn; phân biệt ghi nhận/hủy/truy vấn. | Golden/holdout không regression; schema đúng chưa đủ, validator nghiệp vụ độc lập. |
| 03 RAG & Knowledge Lifecycle | Truy vấn ledger qua repository có phạm vi ngày/danh mục; chỉ gửi context tối thiểu khi cần. Không bắt buộc vector DB/RAG mới. | Không bịa số liệu; truy vấn đúng phạm vi, dữ liệu mới sau commit; không có dữ liệu thì nói rõ. |
| 04 Tools, MCP & Agent Orchestration | AI chỉ tạo proposal hoặc đọc dữ liệu qua interface giới hạn; commit qua use case hiện có. Không cần MCP nếu không có nhu cầu. | Không side effect trước xác nhận; retry/double tap không ghi trùng; hủy không commit. |
| 05 Identity, RBAC/ABAC & Authorization | Quyền thực thi bằng code, giới hạn dữ liệu local; không coi prompt là quyền. API key Keystore-backed, loại khỏi backup/log/export. | Test từ chối thao tác chưa xác nhận; không lộ key; không mở quyền hệ thống thừa. |
| 06 Guardrails & AI Security | Deterministic validation ngày, duration, category, intent, schema; chống prompt injection và dữ liệu không tin cậy. | JSON sai hoặc yêu cầu bỏ qua xác nhận không ghi DB; lệnh hủy không tạo proposal; không tự sửa/xóa dữ liệu. |
| 07 Cache, Router & Cost Optimization | Local parser trước cho intent đủ chắc chắn; cloud cho yêu cầu cần model; lời chào local. Không cache câu trả lời ledger nếu không có invalidation đúng. | Offline manual/local hoạt động; không gọi cloud thừa; response cũ bị loại; không tái sử dụng số liệu stale. |
| 08 Observability, Evaluation & Release Gates | Request ID, error class, latency, test evidence, APK provenance; log tối thiểu không chứa bí mật/nội dung riêng tư. | Có baseline, regression, fault injection, device evidence hoặc NOT_TESTED; không tự tuyên bố production-ready. |

**Ranh giới:** JSON hợp lệ ≠ nghiệp vụ đúng ≠ quyền thực thi. Prompt/LLM không cấp quyền. Deterministic policy chạy trước side effect. Chỉ mở tầng cần thiết; không thêm vector DB, MCP, server, RBAC enterprise hay LLM local chỉ để đủ 8 tầng. Release gates ở đây là tiêu chí nghiệm thu candidate, không mở Gate quản trị mới.

## 3. P0 — Runtime crash và AI reliability

### 3.1 Điều tra F6

Xác định package/build, Android version, bước tái hiện, lấy logcat FATAL EXCEPTION/Caused by nếu có thiết bị hoặc emulator. Kiểm tra AI client, ViewModel/coroutine, Budget save/refresh, Room migration, Compose state/navigation, timer lifecycle. Không sửa mò hoặc bắt mọi exception để che crash. Nếu chưa tái hiện được, ghi F6 UNRESOLVED/NOT_REPRODUCED, liệt kê evidence và cách Owner tái hiện; không đánh dấu FIXED. Nếu có root cause, test phải fail trước fix và pass sau fix (hoặc reproduction có bằng chứng tương đương).

### 3.2 AI pipeline

Input → local intent parser → local result hoặc cloud adapter → schema + business validation → answer/proposal → explicit confirmation → atomic repository commit. Giữ contract F1–F4; lệnh hủy có ưu tiên đúng ngữ cảnh, không biến hoạt động như “Hủy hợp đồng 2h” thành lệnh hủy draft. Không sửa expected cũ để làm test xanh.

State machine: IDLE → SENDING → VALIDATING → ANSWER_READY/PROPOSAL_READY → CONFIRMING → SAVED; lỗi → ERROR → RETRY/IDLE; cancel → CANCELLED. Mỗi trạng thái có đường thoát. Giữ input khi lỗi; proposal đã hủy không hồi sinh. Request ID/generation token và lifecycle-aware coroutine; không network trên main thread; propagate CancellationException; response muộn không ghi đè state mới. Chống double-submit cả UI lẫn repository bằng idempotency/transaction phù hợp; không dedupe chỉ vì hai hoạt động có cùng duration/category.

| Fault | Hành vi |
|---|---|
| Offline/DNS/TLS | Không crash; lỗi ngắn; manual/local vẫn dùng được; không bỏ qua TLS validation. |
| 401/403 | Hướng dẫn kiểm tra key/quyền; không retry tự động. |
| 404/model sai | Hướng dẫn kiểm tra endpoint/model; không tự đổi model. |
| 429 | Tôn trọng Retry-After nếu hợp lệ; không retry dồn dập; cho thử lại. |
| 5xx/timeout | Thoát loading, giữ input; retry có giới hạn. |
| JSON malformed/schema sai/intent sai | Từ chối proposal, không commit; thông báo dễ hiểu. |
| Cancel/tab/background/process recreation | Không crash; không commit ngầm; state nhất quán; request cũ không hồi sinh. |
| Duplicate send/confirm | Một request active theo luồng; một commit cho một proposal. |

Timeout toàn request mặc định đề xuất 30s (bao gồm retry; điều chỉnh có lý do theo provider); tối đa một retry tự động cho lỗi tạm thời phù hợp, không retry khi cancel hoặc lỗi auth/schema. Không loading vô hạn. UI hiển thị trạng thái gửi, nút hủy/thử lại, và đường ghi thủ công nếu phù hợp. Nếu local parser hiểu chắc chắn, không cần gọi cloud. Không giả AI thành công khi fallback; phân biệt câu trả lời local/cloud trong diagnostics.

### 3.3 Cấu hình và bảo mật

Cài đặt AI: provider/model/key che mặc định, test connection bằng payload synthetic tối thiểu, thông báo lỗi cụ thể. Audit finding SharedPreferences plaintext + allowBackup=true; nếu còn tồn tại, chuyển key sang lưu trữ mã hóa Keystore-backed, migration an toàn, loại key khỏi backup, không log/export. Không làm mất ledger; nếu key không giải mã được, yêu cầu nhập lại, manual vẫn hoạt động. Không gọi live API nếu chưa được cấp quyền/credential; test mock không thay thế live evidence. Không đưa key vào APK/test fixture/report.

## 4. P1 — Manual Time Entry

FAB + trên Dashboard và Nhật ký, không che nội dung hoặc navigation. Bottom sheet: 9 danh mục hiện có bằng icon, giờ/phút chính xác, preset 15m/30m/1h/2h, ngày mặc định hôm nay (cho ngày quá khứ), ghi chú tùy chọn, lưu. Không bắt buộc AI/timer. Có thể chọn category + preset + lưu trong tối đa 4 thao tác từ Dashboard (không tính nhập tùy chỉnh). Không tự gán start/end khi chỉ nhập duration. Validation dùng chung: duration dương, phút 0–59, ngày/category hợp lệ, giới hạn theo nghiệp vụ; nếu vượt giới hạn hoặc overlap có mốc giờ, xử lý theo policy hiện có và báo rõ, không âm thầm cắt số liệu. Nếu có kế hoạch tương lai, tách khỏi ledger thực tế.

Sau commit, Dashboard/Journal/Analytics/Budget cập nhật từ cùng nguồn dữ liệu. Sửa/xóa bản ghi có xác nhận phù hợp; chống double tap; lưu lỗi giữ form và cho thử lại. Không tạo bản ghi khi cancel. Offline và chưa cấu hình AI vẫn dùng được. Test persistence sau restart và upgrade.

## 5. P1 — Visual-first UI/UX

### Dashboard

- Hero card: ví thời gian ngày, đã ghi nhận/chưa ghi nhận, progress; số lớn, nhãn ngắn, không gọi phần chưa ghi nhận là thời gian rảnh.
- Quick actions: Đồng hồ, Thêm giờ, Hỏi Wally. FAB là đường thêm nhanh nhất; tránh CTA trùng gây rối.
- Donut phân bổ theo danh mục, legend icon/màu/số giờ, tỷ lệ tính trên tổng đã ghi nhận; zero state rõ, không NaN, không chart giả. Màu nhất quán với Journal/Budget; không chỉ dùng màu để truyền nghĩa.
- Tối đa 3 ngân sách ưu tiên, thanh tiến độ, số đã dùng/mục tiêu, còn/vượt; không duplicate UI do lỗi query/key. Có đường xem tất cả.
- Hoạt động gần đây ngắn gọn; phần giải thích chuyển vào chi tiết. Wally nhỏ, lời chào local một câu; không gọi API cho trang trí. Mascot original nếu có asset hợp lệ, nếu chưa có dùng icon/vector đơn giản; không chặn candidate.
- Giữ 5 tab Tổng quan/Đồng hồ/Chat AI (Wally)/Nhật ký/Phân tích; không đổi route hoặc bỏ tính năng hiện có. Settings vẫn truy cập được.

### Journal / Analytics / Budget

Journal nhóm theo ngày, tổng ngày, icon trái, tên/ghi chú ngắn giữa, duration phải; sửa/xóa dễ tìm. Analytics ưu tiên chart và số, trục không wrap từng ký tự; dữ liệu không có timestamp không xuất hiện trên biểu đồ theo giờ. Budget F5: label Chọn nhanh riêng, FlowRow/adaptive, preset 5h/10h/15h/20h không lặp /tuần, giữ chu kỳ ngày/tuần, giờ/phút, priority toggle. Dialog scroll, keyboard insets, nút lưu/hủy tiếp cận được. Không thay đổi logic ngân sách ngoài fix có chứng cứ.

### Visual acceptance

320/360/390/412dp, font scale 1.0 và 1.3, bàn phím mở/đóng, portrait, system bars. Không clipping/overflow, text wrap từng ký tự, nút che nội dung; touch target tối thiểu 48dp cho thao tác chính; contrast đọc được, accessibility label cho icon. Dashboard first viewport có hero và đường thêm giờ (ở font scale chuẩn); không ép tất cả chart vào một màn hình. Light theme bắt buộc ổn định; dark theme P2 nếu thực hiện phải test tương đương. Không thêm banner quảng cáo, premium, animation nặng, tính năng tài chính. Screenshot before/after cùng viewport; review trực quan, không chỉ build PASS.

## 6. Acceptance matrix — điều kiện đạt bắt buộc

| ID | Test | PASS khi |
|---|---|---|
| A01 | Baseline F1–F4 + golden/holdout | Không regression; giữ expected cũ; báo count từng suite. |
| A02 | F6 reproduction | Root cause xác định, fail-before/pass-after hoặc evidence tương đương; luồng thực tế không crash. |
| A03 | AI success local/cloud | Intent đúng, answer/proposal hợp lệ; chỉ commit sau xác nhận. |
| A04 | Offline/DNS/TLS/timeout | Không crash, loading kết thúc trong budget, input giữ, manual hoạt động. |
| A05 | 401/403/404/429/5xx | Mapping đúng, retry policy đúng, không ghi DB. |
| A06 | Malformed JSON/schema/injection | Không commit, không bypass confirmation, không lộ bí mật. |
| A07 | Cancel/stale response/tab/background | Không hồi sinh proposal, không crash, không ghi ngầm. |
| A08 | Duplicate send/confirm/retry | Một commit cho một proposal; hai hoạt động hợp lệ giống nhau vẫn được ghi riêng. |
| A09 | Manual 30m, custom duration, ngày quá khứ, đủ 9 category | Lưu đúng, offline, không cần key; cancel/invalid không ghi. |
| A10 | Manual edit/delete/restart | Ledger và mọi tổng cập nhật đúng; persistence đúng. |
| A11 | Budget ngày/tuần, preset, priority | Tính đúng, tối đa 3 ưu tiên, không duplicate, F5 hết. |
| A12 | Dashboard/chart empty/mixed/no timestamp | Số đúng, không NaN, không suy diễn thời gian rảnh; không đưa duration-only vào trục giờ. |
| A13 | UI viewport/font/keyboard | Đạt toàn bộ visual acceptance mục 5, có screenshot. |
| A14 | Upgrade từ APK cũ có dữ liệu synthetic | Cài đè tương thích, giữ ledger/budget/settings; migration không destructive. |
| A15 | Key security | Không plaintext key trong storage mới/log/export/backup; test migration và key unavailable. |
| A16 | Build APK | Build thành công, artifact tồn tại, SHA256, package/version/signing compatibility ghi rõ. |
| A17 | Real Android device | Cài/upgrade, cold start, manual, budget, AI success/error, background/restart; không crash trong các luồng test. |
| A18 | Live provider (nếu được phép) | Request synthetic thực thành công, model/provider đúng; không lộ key hoặc gửi dữ liệu thật. |

Test bằng unit/integration (mock HTTP), UI/instrumentation/emulator nếu có, real device nếu có quyền và thiết bị. Fault injection phải thực sự đi qua adapter/ViewModel boundary, không chỉ test helper. Không dùng mock để tuyên bố live PASS; emulator không thay thế real-device PASS. Ghi rõ môi trường và command. Nếu không có thiết bị, tạo checklist Owner chạy, không giả screenshot/device result. Không bỏ qua test đỏ; sửa trong scope, chạy lại. Không giảm coverage hoặc đổi expected để che regression.

## 7. Definition of Done & trạng thái nghiệm thu

**IMPLEMENTATION_PASS:** A01, A03–A16 đạt ở môi trường phù hợp; A02 phải có fix được chứng minh nếu muốn gọi F6 FIXED. Không lỗi regression, không known P0/P1 chưa xử lý trong phần đã test. APK có thể cài đặt, evidence đầy đủ. Nếu F6 chưa tái hiện hoặc A02 chưa đạt, chỉ ghi CANDIDATE_UNVERIFIED, không IMPLEMENTATION_PASS cho toàn SPEC.

**DEVICE_PASS:** A17 đạt trên Android thực, F6 được xác minh lại theo luồng tái hiện. **AI_LIVE_PASS:** A18 đạt; nếu không có quyền/credential ghi NOT_TESTED. **SPEC_ACCEPTED:** toàn bộ A01–A18 bắt buộc đạt, Owner nghiệm thu; P2 dark mode/mascot nâng cao không bắt buộc. Không tự tuyên bố accepted hoặc production-ready. Nếu thiếu device/live credential, vẫn giao candidate và báo rõ các điều kiện chưa đạt, không mở rộng scope để bù.

## 8. Deliverables — tự tạo trong repo hiện tại

1. Source/test changes đúng scope; danh sách file thay đổi và lý do. Không tự đặt tên repo/path nếu chưa đọc source.
2. APK candidate mới (không overwrite bản authoritative nếu có), đường dẫn thực, SHA256, versionCode/versionName, package ID, signing compatibility.
3. `WALLY_QA_REPORT.md`: baseline, root cause F5/F6, AI lỗi thực tế đã tìm thấy, fix, command/test counts, matrix A01–A18 PASS/FAIL/NOT_TESTED kèm evidence path; log đã redact; giới hạn và known issues.
4. Screenshot Dashboard, Manual Entry, Journal, Budget Dialog, Wally success/error/loading; viewport/font scale; before/after nếu có. Không dùng mockup thay screenshot runtime.
5. Checklist Owner tối đa 10 thao tác kiểm tra trên điện thoại, hướng dẫn cài đè không mất dữ liệu nếu tương thích; không yêu cầu gỡ app.

**Báo cáo cuối ngắn:** verdict từng mức, APK path/hash, test summary, findings còn mở, việc Owner cần làm. Dừng khi đã giao candidate/evidence và các blocker thực sự được phân loại; không tự mở feature mới. Nếu còn lỗi sửa được trong scope, tiếp tục fix-test trước khi dừng.
