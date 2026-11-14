# WALLY QA REPORT — Time Wallet / Wally Experience & AI Reliability

> **Cập nhật:** Owner đã thay Local-first bằng **API-first**. Các phần mô tả bộ phân tích cục bộ chạy trước/fallback cục bộ trong báo cáo này (candidate v1.0.2) đã bị thay thế bởi `WALLY_QA_REPORT_APIFIRST.md` (candidate v1.0.3). Báo cáo này được giữ nguyên để đối chiếu.

Ngày: 2026-11-13 · Spec: `SPEC_TIME_WALLET_WALLY.md` v1.0 · Người thực hiện: AI coding agent (Claude)

## 0. Kết luận

| Mức | Trạng thái | Lý do |
|---|---|---|
| **Toàn SPEC** | **CANDIDATE_UNVERIFIED** | F6 chưa tái hiện được và A02 chưa đạt, nên không được ghi IMPLEMENTATION_PASS (SPEC §7). |
| IMPLEMENTATION_PASS | Không tuyên bố | Phụ thuộc A02. |
| DEVICE_PASS | **NOT_TESTED** | Không chạy thiết bị/emulator (quy tắc dự án `.agents/AGENTS.md:11`, Owner xác nhận giữ quy tắc). |
| AI_LIVE_PASS | **NOT_TESTED** | Không có credential/quyền gọi nhà cung cấp thật; chỉ có mock HTTP. |
| SPEC_ACCEPTED | Không tuyên bố | Cần Owner nghiệm thu. |

Phần đã làm xong ở mức JVM/mock: manual entry, F5, hạ tầng AI (lỗi phân loại, retry, hủy, chẩn đoán), bảo vệ khóa API, Dashboard/Journal/Budget logic, a11y mức mã nguồn. Phần **chưa chứng minh bằng chạy thật**: mọi thứ liên quan hiển thị, thiết bị, Keystore thật, Room thật, nhà cung cấp AI thật.

## 1. Artifact

| Mục | Giá trị |
|---|---|
| APK candidate | `out/wally_candidate/TimeWallet-MVP-v1.0.2-WALLY-candidate-debug.apk` (11.703.518 byte) |
| SHA256 | `49a83f858dcbaf47130c244c847e7d69809c8ab09a44a8f21163917134a89e8c` |
| Package / version | `com.timewallet.mvp` · versionCode **3** · versionName **1.0.2** · minSdk 26 · targetSdk 35 (đọc bằng `aapt dump badging`) |
| Chữ ký | Debug keystore, chứng chỉ SHA-256 `402f1d41…2530d`, **trùng** với APK v3 và v1.0.1 (`apksigner verify --print-certs`) ⇒ cài đè được nếu cùng máy build; versionCode 3 > 2 nên không bị coi là hạ cấp. Bản release chưa ký (không có signingConfig), không đổi. |
| Room / schema | Vẫn `version = 3`, `exportSchema=false`; `TimeWalletDatabase.kt`, `LedgerRecordEntity.kt`, DAO không đổi ⇒ **không có migration mới**. |
| APK trước đó | Không bị ghi đè; `ALL_APK_SHA256.txt` liệt kê v3 `730dc269…752d`, v2 `500e1585…4936`, candidate đầu `a753da75…daa2`. File gốc ở thư mục gốc `TimeWallet-MVP-v1.0.1-debug.apk` đang trùng byte với v3 (từ phiên AI-01), bản v1.0.1 nguyên bản `bfe04960…1c03` nằm ở `out/ai01_snapshot_pre/`. |
| Diff | `out/wally_candidate/SOURCE_DIFF_vs_v3.patch`: 37 file, +3202/−335 (15 file sửa + 22 file mới trong `app/src`; `app/build.gradle.kts` đổi version). `CHANGED_FILES_vs_v3.txt`, `NEW_FILES_vs_v3.txt`, `MANIFEST_SHA256_after.txt`. |
| Snapshot rollback | `out/wally_snapshot_pre/source_v3_pre_WALLY.tar` + `MANIFEST_SHA256.txt` (nguồn v3 trước khi sửa). |

**Rollback:** (A) giải nén tar vào thư mục sạch rồi `sha256sum -c MANIFEST_SHA256.txt`; (B) tại chỗ: xóa 22 file trong `NEW_FILES_vs_v3.txt`, rồi `tar -xf` tại gốc dự án và đối chiếu manifest; hoặc cài lại APK v3 (v3 có versionCode 2 thấp hơn bản này nên Android không cho hạ cấp đè lên; cần gỡ app, nên **xuất JSON trước**).

## 2. Môi trường và lệnh

- JDK 22 (`JAVA_HOME=C:\Program Files\Java\jdk-22`; `java` trên PATH là 1.8 nên không dùng). Gradle wrapper, chạy `--offline`.
- Test: `./gradlew testDebugUnitTest --offline` · Build: `./gradlew assembleDebug --offline`.
- Holdout 46 của Reviewer nằm ngoài cây test (`out/ai01_evidence/independent_verification/`); được copy tạm vào cây test để chạy rồi gỡ ra.
- Không emulator, không adb, không cài app, không gọi API thật.

## 3. Test: tên suite và số đếm thực tế

Baseline trước khi sửa (`out/wally_evidence/baseline/`): **93/93 PASS** = 91 trong cây + 2 holdout harness. Golden 66/66 (39 câu hỏi, 0 sai), holdout 46/46.

Cuối (`out/wally_evidence/final/xml/`): **164/164 PASS**, 0 lỗi, 0 skip.

| Suite | Số test | Ghi chú |
|---|---|---|
| TimeWalletDomainTest | 25 | không sửa (hash trùng snapshot) |
| Ai01IntegrityReproTest | 10 | không sửa |
| Ai01FeedbackAndGuardTest | 11 | không sửa |
| Ai01ConversationalAnalyticsTest | 11 | không sửa; chứa golden 66 |
| Ai01NegationFinalFixTest | 10 | không sửa |
| Ai01ClosureParserTest | 9 | không sửa; F1, F3, F4 |
| Ai01ClosureLifecycleTest | 15 | không sửa; F2 + stale |
| Ai01IndependentVerificationHarnessTest (holdout) | 2 | copy tạm; intent 46/46 |
| **Tổng cũ** | **93** | |
| WallyAiFaultInjectionTest | 21 | MỚI, fault qua adapter thật |
| WallyManualEntryTest | 11 | MỚI |
| WallyBudgetTest | 8 | MỚI (gồm kiểm tra tĩnh F5) |
| WallyChatLifecycleTest | 7 | MỚI (gồm kiểm tra tĩnh VM) |
| WallyDashboardSummaryTest | 6 | MỚI |
| WallyUiStaticTest | 6 | MỚI, kiểm tra tĩnh giao diện |
| WallyKeystoreFaultTest | 4 | MỚI, F6 giả thuyết 1 |
| WallyDiagnosticsTest | 4 | MỚI |
| WallySecurityStaticTest | 4 | MỚI |
| **Tổng mới** | **71** | |

(`TestSupport.kt` chứa `InMemorySharedPreferences` và `InMemoryLedgerDao` dùng chung, không có test.)

Các con số 93, 66 và 46 là các bộ đếm khác nhau; không cộng cơ học.

## 4. Root cause và findings

### F5 — preset "20h/tuần" xuống dòng từng ký tự [ĐỌC CODE + test tĩnh]
`ComponentsAndDialogs.kt` (đoạn Chọn nhanh của hộp thoại ngân sách): một `Row` không cho phép xuống dòng chứa nhãn "Chọn nhanh:" và 4 chip; mỗi chip hiển thị `"20h/tuần"`. Trong hộp thoại chỉ rộng khoảng 230–260dp, Row cấp cho chip cuối gần 0dp nên chữ bị tách từng ký tự.
**Sửa:** nhãn riêng một dòng ("Chọn nhanh (tuần)"), chip trong `FlowRow`, chữ `"20h"` (không lặp /tuần), `maxLines=1`, `softWrap=false`, vùng chạm 48dp. Giữ chu kỳ ngày/tuần, giờ/phút, công tắc ưu tiên.
**Chứng cứ:** `WallyBudgetTest."F5 …"` là kiểm tra **tĩnh** trên mã nguồn. Chưa có ảnh chụp runtime (A13 NOT_TESTED).

### F6 — "Time Wallet tiếp tục dừng": **UNRESOLVED / NOT_REPRODUCED**
Không có stack trace, không có thiết bị, không tái hiện được. Không đánh dấu FIXED.
Đã rà tĩnh (không sửa mò) và xếp hạng giả thuyết:
1. `loadAiPreferences()` gọi trong `init` của ViewModel (trước mọi UI); migration khóa v1.0.0 plaintext gọi `vault.encrypt` **không bắt lỗi**. Nếu Keystore ném ngoại lệ, app sập ở mọi lần mở. **Tái hiện ở mức JVM:** `WallyKeystoreFaultTest` chạy trên code cũ cho **4/4 FAIL** (`out/wally_evidence/wp1/keystore_RED_before_fix.xml`), sau sửa 4/4 PASS. Đây chứng minh *đường lỗi tồn tại*, không chứng minh nó là nguyên nhân F6 trên máy Owner.
2. 21 điểm `viewModelScope.launch` trong ViewModel không có bắt lỗi (`SQLiteException` từ Room làm chết tiến trình).
3. `OutOfMemoryError`/`StackOverflowError` đường AI (đã chặn bằng giới hạn kích thước 512KB và bắt `StackOverflowError`).
4. Xuất JSON lớn qua clipboard/Intent (đã bọc `try/catch`, xem trước tối đa 20.000 ký tự).
5. Nhật ký không lazy (ghi nhận, chưa sửa: xem mục 8).

**Biện pháp để lần sau có bằng chứng:** `TimeWalletApp` cài `UncaughtExceptionHandler` ghi *tên lớp và khung stack* (không ghi message, không văn bản nhật ký, không khóa) vào `files/last_crash.txt`; hộp thoại ⓘ hiển thị, cho Sao chép và Xóa. Coroutine lỗi đi qua `safeHandler`: ghi báo cáo đó, báo người dùng bằng một câu, **không nuốt im lặng**.
**Cách Owner tái hiện/lấy bằng chứng:** xem `WALLY_OWNER_CHECKLIST.md` mục 3 và 9.

### Lỗi AI thực tế tìm thấy (qua đọc code, được xác nhận bằng test mock)
| # | Vấn đề trước khi sửa | Sửa |
|---|---|---|
| 1 | Mọi lỗi (DNS, TLS, 401/403/404/429/5xx, JSON hỏng) gộp thành một cảnh báo chung, mất mã trạng thái | `AiFault` 10 lớp + thông điệp ngắn tiếng Việt |
| 2 | Không có hạn tổng, không retry, không đọc `Retry-After` | Hạn tổng 30s; tối đa 1 lần thử lại cho OFFLINE/TIMEOUT/5xx/429 nếu còn thời gian; không retry 401/403/404/MALFORMED/hủy |
| 3 | Gọi chặn không hủy được; hủy không đóng socket | Coroutine canh gác `disconnect()` khi bị hủy. **Phát hiện lỗi đua bằng test lặp 25 vòng:** canh gác khởi chạy chậm có thể bỏ lỡ `disconnect()`; sửa bằng `CoroutineStart.UNDISPATCHED` (RED: `out/wally_evidence/wp2/cancel_race_RED_before_fix.xml`, thất bại ở vòng 0 khi bỏ sửa) |
| 4 | Theo chuyển hướng HTTP (có thể gửi lại nhật ký sang host khác); không giới hạn kích thước phản hồi | `instanceFollowRedirects=false`; trần 512KB |
| 5 | Schema thiếu mảng `proposals` bị coi như "AI không có gì" | Ném `MALFORMED` |
| 6 | Không có request id, không phân biệt local/cloud/fallback | `requestId`, `OutcomeSource`, `AiDiagnostics` (không chứa văn bản/khóa) |
| 7 | Không có nút hủy/thử lại; nút khóa khi đang phân tích | Nút "Hủy", banner lỗi có "Thử lại", "Ghi thủ công", "Đóng"; nội dung nhập được giữ |
| 8 | Bấm Xác nhận hai lần: toast "Đã lưu nguyên tử 0 hoạt động" | `confirmGuard` + `ConfirmFeedback` ("đã có, không ghi trùng") |
| 9 | `AiPreferences.toString()` in cả khóa | Che khóa |
| 10 | Khóa không giải mã được ⇒ rỗng, vẫn bật Opt-in, không báo | `keyUnavailable`, tắt Opt-in hiệu dụng, yêu cầu nhập lại, banner |

**Thay đổi hành vi cần Owner biết:** bộ phân tích cục bộ chạy *trước*; nếu hiểu chắc chắn (có đề xuất, không cảnh báo) thì **không gọi cloud** (SPEC tầng 07). Câu như "Học 45p" nay luôn ra kết quả cục bộ ngay cả khi bật AI. Chỉ câu local không hiểu mới đi cloud. Tham số `localFirst=false` tắt hành vi này.

## 5. Ma trận nghiệm thu A01–A18

PASS ở đây nghĩa là "đạt ở môi trường ghi bên cạnh". Mọi mục ghi "JVM" **không** là bằng chứng thiết bị.

| ID | Trạng thái | Môi trường · evidence |
|---|---|---|
| A01 | **PASS** | JVM. 93/93 cũ không đổi (7 file test cũ trùng hash snapshot), golden 66/66, holdout 46/46, F1–F4 giữ nguyên. `out/wally_evidence/final/` |
| A02 | **NOT_TESTED / UNRESOLVED** | F6 không tái hiện; xem §4. Chưa có fail-before/pass-after cho F6 thực tế. |
| A03 | **PASS** (mock) | `WallyAiFaultInjectionTest`: success CLOUD, local-first, mọi đề xuất `needsConfirmation=true`; commit chỉ qua `confirmProposalsList`. Live: NOT_TESTED (A18). |
| A04 | **PASS** (mock) / UI NOT_TESTED | Offline/DNS, TLS, timeout, hạn tổng 30s, fallback cục bộ giữ đề xuất, manual vẫn dùng. Vòng loading của ViewModel kiểm bằng `finally` + test tĩnh, không chạy ViewModel thật. |
| A05 | **PASS** (mock) | 401/403/404/429(+Retry-After ngắn/dài)/5xx; `GeminiProposalClient` không import DAO/Room (grep) nên không thể ghi DB. |
| A06 | **PASS** (mock) | JSON hỏng, schema sai, body quá lớn, injection ("IGNORE PREVIOUS…", `needsConfirmation:false`, `commit:true`, `durationSeconds` vô lý) → bị loại/không vượt xác nhận; diagnostics và crash report không có khóa/văn bản (test tĩnh). |
| A07 | **PASS** (JVM) + **NOT_TESTED** (thiết bị) | Hủy: socket đóng, 25 vòng; epoch bỏ kết quả muộn (`Ai01ClosureLifecycleTest`); máy trạng thái chat. Tab/background/tái tạo tiến trình: chưa chạy. |
| A08 | **PASS** (JVM) / Room thật NOT_TESTED | Lưu thủ công hai lần cùng form = 1 bản ghi; hai form giống nhau = 2 bản ghi; chốt `confirmGuard`/`manualSaveGuard` (test tĩnh). Test dùng DAO RAM (không mô phỏng chỉ mục duy nhất của Room). |
| A09 | **PASS** (JVM) / UI NOT_TESTED | 30p, tùy chỉnh 1h45, ngày quá khứ, đủ 9 danh mục, hủy/không hợp lệ không ghi, không cần khóa, không tự gán giờ. Giao diện bottom sheet: biên dịch + kiểm tra tĩnh. |
| A10 | **PASS** (domain) / **NOT_TESTED** (khởi động lại thiết bị, UI sửa) | Tổng cập nhật sau lưu/xóa, chưa gắn giờ không vào `timedSeconds`. Luồng sửa/xóa có sẵn, không đổi. |
| A11 | **PASS** (JVM) | Ngân sách ngày/tuần, số gộp mới `combined*` (giữ `usedSeconds` và test cũ), tối đa 3 ưu tiên (từ chối mục thứ 4), không trùng, preset; F5 kiểm tra tĩnh. |
| A12 | **PASS** (JVM) | `DashboardSummary`: rỗng, hỗn hợp, không mốc giờ, vượt 24h (kẹp), dự kiến không tính, ngày DST 23h; không NaN; trục giờ chỉ dùng thời gian có mốc giờ. |
| A13 | **NOT_TESTED** | Không có screenshot runtime; chỉ kiểm tra tĩnh (thứ tự bố cục, 48dp, `softWrap`, `imePadding`). Ước lượng first viewport chưa xác nhận. |
| A14 | **NOT_TESTED** (thiết bị) | Schema/entity/DAO không đổi; test migration v1→v2→v3 cũ vẫn PASS; versionCode 3 + cùng chữ ký. Chưa cài đè trên máy có dữ liệu. |
| A15 | **PASS** (JVM/tĩnh) | Khóa lưu dạng bản mã AES-GCM (đã có từ v1.0.1), không plaintext, `allowBackup=false` + quy tắc loại sharedpref/database, exporter không đụng khóa, không `Log.*` trong main, lỗi Keystore không làm app dừng, khóa không giải mã được → nhập lại. Keystore thật trên máy: NOT_TESTED. |
| A16 | **PASS** | Build thành công; SHA256, package, version, chữ ký ghi ở §1. |
| A17 | **NOT_TESTED** | Không có thiết bị thật. |
| A18 | **NOT_TESTED** | Không có credential/quyền; mock không thay thế live. |

## 6. Danh sách file thay đổi và lý do

**Mới (production):** `TimeWalletApp.kt` (ghi crash); `data/diagnostics/{Diagnostics,AiDiagnostics}.kt`; `domain/usecase/{AiFault,ChatPhase,WallyGreeting,ManualEntry,BudgetPolicy,DashboardSummary,JournalDayTotal}.kt`; `ui/screens/ManualEntrySheet.kt`; `res/drawable/ic_wally.xml` (biểu tượng gốc, không sao chép nhân vật nào).
**Sửa:** `GeminiProposalClient.kt` (AiFault, retry, deadline, hủy, seam `connectionFactory`, testConnection); `TimerAndSettingsStore.kt` (Keystore an toàn, che khóa); `TimeWalletViewModel.kt` (safeHandler, chat phase, hủy, nhập thủ công, guard xác nhận, test kết nối, chính sách ngân sách); `MainActivity.kt` (FAB, sheet, nối dây); `ChatScreen.kt`, `OverviewScreen.kt`, `HistoryScreen.kt`, `InsightsScreen.kt`, `TimerScreen.kt`, `ComponentsAndDialogs.kt` (UI, F5, a11y); `TimeBudget.kt` (số gộp); `VietnameseChatParser.kt` (`ParseChatOutcome` thêm trường mặc định); `ChatAnalysisSupport.kt` (`ConfirmFeedback`); `LedgerQueryEngine.kt` (`ChatAnswer.label`); `AndroidManifest.xml` (`android:name`); `app/build.gradle.kts` (versionCode 3 / 1.0.2).
**Test mới:** 9 file + `TestSupport.kt`.
Nhãn tab "Chat AI" đổi thành "Wally" (vẫn 5 tab, route không đổi). Hộp thoại ⓘ hiển thị phiên bản thật thay cho "v1.1" gắn cứng.

## 7. Evidence

```
out/wally_evidence/baseline/        XML baseline (93/93)
out/wally_evidence/wp1/             Keystore RED (4/4 FAIL) → GREEN, security
out/wally_evidence/wp2/             cancel_race_RED_before_fix.xml, console
out/wally_evidence/wp3|wp4|wp5/     console từng bước
out/wally_evidence/final/xml/       XML cuối (164/164) + console + assemble_console
out/wally_candidate/                APK, hash, manifest, diff
```
Log là kết quả Gradle/JUnit, dữ liệu tổng hợp; không có khóa thật. **RED chỉ có cho:** Keystore (F6 giả thuyết 1) và lỗi đua hủy. Các test cho API mới (manual, AiFault, DashboardSummary…) không thể chạy trên code cũ nên không có RED; chứng cứ là hành vi sau sửa.

## 8. Giới hạn và known issues

- **Không có ảnh chụp, không có chạy thiết bị.** Giao diện mới (FAB, bottom sheet, donut, banner lỗi) mới được biên dịch và kiểm tra tĩnh; chưa ai nhìn thấy nó chạy. Rủi ro bố cục ở 320dp/font 1.3 chưa được loại trừ.
- Ba đường nối ViewModel (hủy phân tích, nhập thủ công, xác nhận) chỉ kiểm bằng biên dịch + hàm thuần + kiểm tra tĩnh; chưa có test ViewModel/Compose.
- Nhật ký (History) vẫn dựng toàn bộ trong một cột cuộn (không lazy); sẽ chậm khi có hàng trăm bản ghi.
- Bộ đếm 1 giây của ViewModel vẫn chạy khi app ở nền; `collectAsState` chưa theo vòng đời.
- Đồng hồ chạy quá 24h không lưu được (validator từ chối) — chỉ hủy được; chưa sửa (ngoài phạm vi).
- Xóa bản ghi và xóa ghi chú liên quan là hai lệnh DAO riêng, chưa gói trong một giao dịch.
- Thẻ "Trục 24 giờ" vẫn còn nhãn "Quỹ còn lại hôm nay" cho phần chưa trôi qua của ngày (ý nghĩa cũ, khác "chưa ghi nhận"); hero không còn dùng cụm đó.
- Dark theme (P2) không làm. Mascot là biểu tượng vector đơn giản.
- Mặc định `api.foza.ai` là dịch vụ bên thứ ba; Opt-in vẫn tắt theo mặc định.
- Bản release chưa ký, chưa bật minify.
- Quyết định chờ Owner: bật/tắt `localFirst` (mặc định bật); có muốn nhãn tab "Wally" hay "Chat AI (Wally)".
