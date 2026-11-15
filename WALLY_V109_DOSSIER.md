# WALLY v1.0.9 — P0 F6: nguyên nhân gốc crash trên thiết bị thật và bản sửa

Ngày: 2026-11-14 · Trạng thái: **FIX_CANDIDATE — chờ Owner xác nhận trên máy thật.** F6 chỉ được đánh dấu FIXED cho **đúng chữ ký crash này** sau khi Owner cài đè và gửi lại đúng thao tác đã crash. F6 nói chung vẫn `UNRESOLVED`. Không có `DEVICE_PASS`, `AI_LIVE_PASS`, `SPEC_ACCEPTED`.

## 0. Binary

| Mục | Giá trị |
|---|---|
| APK | `out/wally_candidate_v109/TimeWallet-MVP-v1.0.9-WALLY-HOTFIX-candidate-debug.apk` (11.972.276 byte) |
| **SHA-256** | `0f5f23d9b7eb718367d3d87a26bb3cbbc7fb2884e124163fc5b980bd116ca4e2` |
| Package / version | `com.timewallet.mvp` · **versionCode 10** · versionName **1.0.9** (cao hơn v1.0.8 = 9, v1.0.7 = 8, v1.0.6 = 7 nên cài đè được lên bất kỳ bản nào Owner đang dùng) |
| Cert ký (debug) | `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d` (trùng v1.0.8; `SIGNING_CERT_COMPARE.txt`) |
| Quyền | chỉ `INTERNET` (+ quyền receiver nội bộ AndroidX), không đổi (`apk_badging_v1.0.9.txt`) |
| Nguồn | `source_v1.0.9.tar` `add321024c47d8b39bde95fdb752e2a0af45ee97780d6c4846ecee8b2a7b37bf`; manifest **110/110** khớp |
| Hồ sơ | `WALLY_V109_ACCEPTANCE_RECORD.md`, `WALLY_OWNER_CHECKLIST_V109.md` |

Một bản dựng trung gian (SHA `1955d6ef…`) đã bị hủy **trước khi giao**: kiểm dex phát hiện chuỗi thông báo lỗi của `Rx` còn chứa chữ của cờ cũ; đã đổi lời, thêm test, dựng lại. Chỉ một APK v1.0.9 được giao. Mọi APK và evidence cũ nguyên vẹn (không tệp nào trong `out/wally_candidate_v106..v108`, `out/wally_evidence_v106..v108` bị sửa sau mốc snapshot).

## 1. Binary nào khớp log của Owner

Đối chiếu số dòng của stack (`TimeWalletViewModel.kt:739`, `ProposalLifecycle.kt:29`, `MainActivity.kt:250`) với các nguồn đã đóng gói:

| Nguồn | VM:739 | PL:29 | MA:250 | Khớp |
|---|---|---|---|---|
| v1.0.5 | `if (!aiPrefs.optInEnabled …` | `Regex(` | `onClearAllProposals` | **không** |
| v1.0.6 (vc7) | `CancelCommandParser.parse(text)` | `Regex(` | `onAnalyzeClick` | có |
| v1.0.7 vòng 1 / vòng 2 (vc8) | như trên | như trên | như trên | có |
| v1.0.8 (vc9) | như trên | như trên | như trên | có |

Bốn bản v1.0.6–v1.0.8 đều khớp nên **không thể biết Owner cài bản nào**; cả bốn cùng lỗi. Cờ `(?U)` đã có từ trước chuỗi hotfix: baseline AI-01 v3 (`out/wally_snapshot_pre/source_v3_pre_WALLY.tar`) có 5 mẫu `(?U)` trong `ProposalLifecycle.kt` và 15 trong `VietnameseChatParser.kt`; v1.0.5 có 21 `(?U)`. Chưa dựng/chạy các bản cũ hơn.

## 2. Nguyên nhân gốc

**Đọc mẫu trước khi kết luận.** `ProposalLifecycle.kt:29-31` (`CancelCommandParser.commandRegex`, v1.0.8):

```
(?U)^(?:(?:xin|vui lòng|hãy|làm ơn|cho tôi|cho mình|tôi muốn|mình muốn|tôi|mình)\s+)*(?:hủy|huỷ|xóa|xoá|bỏ)(?=\s|$|[.!,])\s*(.*)$
```

Cả 5 `Regex` của `CancelCommandParser` (dòng 29, 32, 35, 36, 37) đều mở đầu bằng `(?U)`. `(?U)` là cờ nội tuyến của **JDK** (`UNICODE_CHARACTER_CLASS`). **Thành phần cú pháp ICU không hỗ trợ: chính ký tự `U` trong nhóm cờ `(?U)`.**

Bằng chứng, theo từng mắt xích:

1. **Android không chạy bộ phân tích regex của JDK.** Nguồn SDK `android-35` (`sources/android-35/java/util/regex/Pattern.java`): `compile()` (dòng 3520-3535) chỉ gọi `PatternNative.create(icuPattern, icuFlags)` với `icuPattern` là **chuỗi mẫu nguyên vẹn**; `icuFlags = flags & (CASE_INSENSITIVE | COMMENTS | MULTILINE | DOTALL | UNIX_LINES)`. Trình phân tích của JDK (nơi `(?U)` được hiểu) bị gỡ. Hằng `UNICODE_CHARACTER_CLASS` cũng bị cấm: dòng 1382-1383 ném `IllegalArgumentException("UNICODE_CHARACTER_CLASS flag not supported")`. Chú thích dòng 48-52: "Android always uses unicode character classes".
2. **ICU4C từ chối `(?U)`.** Chạy trên engine ICU4C thật (xem §6): `(?U)^abc` → `U_REGEX_RULE_SYNTAX`, offset 3 (ký tự ngay sau `(?U`). Các cờ hợp lệ của ICU (`i d m s w x`) biên dịch bình thường.
3. **Khớp chữ ký log.** `PatternNative.compileImpl` ném `PatternSyntaxException` → `kotlin.text.Regex.<init>` → `CancelCommandParser.<clinit>` (PL:29) → `ExceptionInInitializerError`. Mô phỏng trên mã v1.0.8 nguyên vẹn cho đúng chuỗi này (`red_on_v1.0.8/red_crash_signature.txt`).
4. **Vì sao crash ngay lần bấm gửi đầu tiên:** `analyzeChatInput()` gọi `CancelCommandParser.parse(text)` **đồng bộ trên luồng chính** (VM:739) trước mọi thứ khác; lần chạm đầu tiên kích hoạt `<clinit>`. `ExceptionInInitializerError` là `Error`, không phải `Exception`, nên không `catch (e: Exception)` nào bắt được. Lần chạm sau ném `NoClassDefFoundError`.
5. **Vì sao test JVM không thấy:** JDK hiểu `(?U)`. JVM PASS không nói gì về ICU.

Log của Owner **không** có thông điệp/offset của `PatternSyntaxException`, nên "chính xác `(?U)` tại offset 3" là kết luận suy ra từ: mẫu thật ở dòng 29 + nguồn Android + tái hiện trên ICU4C, không phải đọc trực tiếp từ log thiết bị.

## 3. Regex cũ → mới (dòng 29)

**Cũ (v1.0.8):** như §2.

**Mới (v1.0.9), nguồn:** `Rx.uni("""^(?:(?:xin|vui lòng|…|mình)\s+)*(?:hủy|huỷ|xóa|xoá|bỏ)(?=\s|$|[.!,])\s*(.*)$""")` — văn bản mẫu **giữ nguyên, chỉ bỏ `(?U)`** và đổi `Regex(` thành `Rx.uni(`.

**Mới, chuỗi cuối cùng đưa cho bộ máy (giống hệt trên JDK và ICU):**

```
^(?:(?:xin|vui lòng|hãy|làm ơn|cho tôi|cho mình|tôi muốn|mình muốn|tôi|mình)[\t\n\x{B}\f\r\x{85}\p{Z}]+)*(?:hủy|huỷ|xóa|xoá|bỏ)(?=[\t\n\x{B}\f\r\x{85}\p{Z}]|(?=(?:\r\n|(?<!\r)\n|[\r\x{85}\x{2028}\x{2029}])?\z)|[.!,])[\t\n\x{B}\f\r\x{85}\p{Z}]*([^\n\r\x{85}\x{2028}\x{2029}]*)(?=(?:\r\n|(?<!\r)\n|[\r\x{85}\x{2028}\x{2029}])?\z)
```

Không còn `(?U)`, `\s`, `\d`, `\b`, `$`, `.` trần: mọi thứ phụ thuộc cờ/định nghĩa của bộ máy được viết tường minh.

**Quyết định thiết kế:** không sửa dòng 29 riêng lẻ và không viết lại ~114 mẫu bằng tay. Một nhà máy trung tâm `Rx` (`domain/usecase/Rx.kt`) nhận mẫu viết theo "phương ngữ" nhỏ, dịch thành một chuỗi duy nhất chỉ gồm cấu trúc có nghĩa giống nhau trên hai bộ máy, và **từ chối** mọi cấu trúc ngoài tập con (cờ nội tuyến, `\w`, `&&`, nhóm đặt tên, `\p{Is..}`, lớp lồng nhau, `{` chưa thoát). `Rx.uni` = ngữ nghĩa Unicode (trước là tiền tố `(?U)`); `Rx.ascii` = ngữ nghĩa ASCII mặc định của JDK. Văn bản 114 mẫu không bị sửa tay → rủi ro gõ sai bằng 0; hành vi được chứng minh bằng đối chiếu (§6, §7).

| Cấu trúc nguồn | JDK (ngữ nghĩa cũ) | Android/ICU | Bản dịch (mode Unicode / ASCII) |
|---|---|---|---|
| `(?U)` đầu mẫu | UNICODE_CHARACTER_CLASS | **từ chối** `U_REGEX_RULE_SYNTAX` | bỏ; chọn `Rx.uni` |
| `\s` | ASCII (hoặc White_Space khi `(?U)`) | Unicode | `[\t\n\x{B}\f\r\x{85}\p{Z}]` / `[ \t\n\x{B}\f\r]` |
| `\d` | `[0-9]` (hoặc Nd khi `(?U)`) | Unicode (Nd) | `\p{Nd}` / `[0-9]` |
| `\b` | ASCII (hoặc ký tự từ Unicode khi `(?U)`) | Unicode | `(?:(?<=W)(?!W)\|(?<!W)(?=W))`, W = `[\p{L}\p{M}\p{Nd}\p{Nl}\p{Pc}\x{200C}\x{200D}]` / `[A-Za-z0-9_]` |
| `$` (không MULTILINE) | trước dấu xuống dòng cuối: `\n \r \r\n NEL LS PS` | **thêm** U+000B, U+000C | `(?=(?:\r\n\|(?<!\r)\n\|[\r\x{85}\x{2028}\x{2029}])?\z)` |
| `.` (không DOTALL) | trừ `\n \r NEL LS PS` | **thêm** U+000B, U+000C | `[^\n\r\x{85}\x{2028}\x{2029}]` |
| `[\s\S]` | mọi ký tự | mọi ký tự | `[\x{0}-\x{10FFFF}]` |
| `\p{L}`, `\p{N}`, lookahead/lookbehind đơn ký tự, `(?:)`, lượng từ | như nhau | như nhau | giữ nguyên (whitelist `\p{..}`) |

## 4. Kiểm toán tập trung (không chỉ dòng 29)

Toàn bộ `Regex(`/`toRegex`/`Pattern.compile`/`(?U)` trong mã sản xuất nằm ở **6 tệp** (grep toàn `app/src/main`; mọi nơi khác dùng `split(':')` ký tự hay `split(":", "h")` chuỗi, không phải regex):

| Tệp | Điểm dựng | `Rx.uni` (cũ `(?U)`) | `Rx.ascii` |
|---|---|---|---|
| `VietnameseChatParser.kt` (gồm chuẩn hóa số bằng chữ v1.0.7) | 47 | 26 | 21 |
| `QueryIntentParser.kt` | 31 | 0 | 31 |
| `WallyAiGuards.kt` (`AiProposalGuard`, `ReplyGuard`) | 23 | 14 | 9 |
| `ProposalLifecycle.kt` (`CancelCommandParser`) | 6 | 5 | 1 |
| `WallyTurnPolicy.kt` | 4 | 4 | 0 |
| `StrictJson.kt` | 3 | 0 | 3 |
| **Tổng** | **114** | **49** | **65** |

Đã làm bằng script cơ học (`Regex(` → `Rx.uni(`/`Rx.ascii(`, bỏ 49 `(?U)`, giữ nguyên xuống dòng của từng tệp); hai mẫu dựng trong hàm có nội suy (`WallyAiGuards` ~337) được nâng lên trường để dựng lúc khởi tạo. Test quét mã nguồn cấm mọi `Regex(`/`.toRegex(`/`Pattern.compile`/`java.util.regex.Pattern`/`(?U)` ngoài `Rx.kt`; `Rx.kt` cũng không để chữ của cờ cũ trong bất kỳ chuỗi nào (nên **dex v1.0.9 có 0 chuỗi `(?U)`; v1.0.8 có 46** — `apk_dex_check_v1.0.8_vs_v1.0.9.txt`).

## 5. Chứa lỗi: guard hỏng không được làm sập app, không được mở đường ghi thiếu kiểm tra

`GuardHealth` (`data/ai/GuardHealth.kt`) chạy một lần bộ canary qua 7 đối tượng có Regex (`CancelCommandParser`, `VietnameseChatParser`, `QueryIntentParser`, `AiProposalGuard`, `ReplyGuard`, `WallyTurnPolicy`, `StrictJson`): khởi tạo lớp + 2 kiểm tra ngữ nghĩa chắc chắn ("Hủy tất cả đề xuất" → `All`; "Học 45p" → 45). Bắt `Throwable` (kể cả `ExceptionInInitializerError`, `NoClassDefFoundError`), ghi nhớ kết quả tất định (OOM tạm thời không bị ghi nhớ).

| Điểm chặn | Hành vi khi guard không khả dụng |
|---|---|
| `TimeWalletViewModel.analyzeChatInput` (trước dòng gọi `CancelCommandParser` đầu tiên) | **giữ nguyên input**, hiện thẻ lỗi `AiFault.GUARD_UNAVAILABLE` ("… chưa gửi gì lên AI và chưa tạo đề xuất nào. Bạn vẫn có thể Ghi thủ công."), ghi Nhật ký lỗi một lần, `return`. Không gọi AI, không tạo/xác nhận đề xuất, không chạm sổ cái |
| `GeminiProposalClient.analyzeInput` (câu lệnh đầu tiên) | `GuardHealth.require()` ném `GuardUnavailableException` **trước mọi xử lý và mọi kết nối mạng** |
| `WallyChatRunner.run` | `catch (Throwable)` (OOM thật vẫn ném lại): lỗi guard → `chatFault = GUARD_UNAVAILABLE`, phase `ERROR`; kết quả chưa từng được áp nên không có đề xuất nào |
| `ChatScreen` (lúc compose, 2 chỗ) | gọi `GuardHealth.isCancelCommand` (không bao giờ ném) thay vì `CancelCommandParser.parse` trực tiếp — nếu không, `NoClassDefFoundError` ngay lúc vẽ giao diện vẫn làm sập app |
| Khởi động ViewModel | làm nóng guard ngoài luồng chính (`Dispatchers.Default`, qua `safeHandler`) để lần gửi đầu không biên dịch regex trên Main |

Không có đường nào bỏ qua guard: không `try/catch` quanh guard để rơi về đề xuất chưa kiểm. Ghi thủ công, đồng hồ, ngân sách, nhật ký, khóa API không phụ thuộc `GuardHealth` và không đổi.

## 6. Bằng chứng RED/GREEN thực sự đã chạy

**Công cụ ICU thật:** Windows 11 có ICU hệ thống (`C:\Windows\System32\icu.dll`, API C `uregex_*`) — cùng họ engine ICU4C mà Android dùng. Harness `out/wally_evidence_v109/tools/icu_replay.py` (ctypes) biên dịch từng mẫu bằng `uregex_open` rồi chạy từng câu mẫu bằng `uregex_findNext`, so từng cặp (đầu, cuối) của mọi nhóm với kết quả JVM. **ICU 68.2 / Unicode 13.0 — không phải bản ICU trong Android và không phải Android runtime.** Cùng một tập 2.336 câu (mẫu tiếng Việt + biến thể: HOA, NBSP, tab, CR/LF/CRLF, NEL, VT, FF, LS/PS, NFD, tiền/hậu tố) cho cả RED và GREEN (SHA-256 corpus trùng nhau: `ec6d162a…2c45`).

| | RED: v1.0.8 nguyên vẹn | GREEN: v1.0.9 |
|---|---|---|
| Mẫu đưa vào ICU4C | 98 (đo lúc chạy qua spy, đã giải nội suy) | 146 **dòng** (108 mẫu lúc chạy + 38 literal tĩnh; chỉ **109** chuỗi khác nhau — xem ghi chú dưới bảng và `WALLY_V109_REVIEWER_RECHECK_PACK.md` §0, §2) |
| **Biên dịch được trên ICU4C** | **52** | **146** |
| **Lỗi biên dịch** | **46, tất cả `U_REGEX_RULE_SYNTAX` tại offset 3** (`(?U`) | **0** |
| Cặp mẫu×câu so JVM↔ICU | 121.472 (52 mẫu biên dịch được) | 341.056 |
| Lệch vị trí/nhóm JVM↔ICU | **567** (vd. `\s` khớp NBSP/LS trên ICU, không khớp trên JDK) | **0** |
| Lỗi chạy của ICU | 0 | 0 |

Báo cáo: `red_on_v1.0.8/icu_replay_report_RED_v1.0.8.txt`, `icu_green/icu_replay_report_GREEN_v1.0.9.txt`; dữ liệu: các thư mục `dump/`.

**Ghi chú số liệu (bổ sung sau MANAGER DECISION; không đổi APK):** "146" là số dòng trong dump chính; 38 dòng literal tĩnh được nạp với cờ ignore-case tắt nên số chuỗi khác nhau là 109. Đã phát lại bổ sung trên ICU4C các literal tĩnh còn thiếu **với đúng cờ thật**: 9/9 biên dịch được, 21.024 cặp, 0 lệch (`out/wally_evidence_v109/reviewer_pack/`). Hợp mọi tổ hợp (mẫu, cờ thật) trong sản xuất = 109, tất cả đã được biên dịch và phát lại trên ICU4C. Đối chiếu 114 điểm dựng / 98 / 100 / 109 / 146: `WALLY_V109_REVIEWER_RECHECK_PACK.md` §2.

Lần đầu của GREEN còn 7 lệch (mẫu `CancelCommandParser.commandRegex` với câu kết thúc bằng U+000B): ICU coi VT/FF là kết thúc dòng, JDK thì không. `parse()` đã `trim()` trước nên ít khi chạm, nhưng chứng cứ đã thấy nên `$` và `.` được viết lại đúng ngữ nghĩa JDK (§3) rồi chạy lại: 0 lệch.

**RED mô phỏng trên mã v1.0.8 nguyên vẹn** (`red_on_v1.0.8/red_sources/PatternSpy*.kt`, `RedSimulatedIcuTest.kt`; spy từ chối `(?U)` đúng như ICU, **là mô phỏng, không phải Android**; mã sản xuất không bị sửa):
- `1 REPRO`: **PASS (xác nhận lỗi tái hiện)** — `ExceptionInInitializerError` ← `PatternSyntaxException: U_REGEX_RULE_SYNTAX near index 3` ← `CancelCommandParser.<clinit>(ProposalLifecycle.kt:29)`; lần gọi sau ném `NoClassDefFoundError` (`red_crash_signature.txt`).
- `2 CONTRACT`: **FAIL (RED)** — qua `WallyChatRunner` + `GeminiProposalClient.analyzeInput` thật của v1.0.8, `ExceptionInInitializerError` thoát khỏi runner (`escaped=[ExceptionInInitializerError]`) và `chatFault` vẫn `null` (không có lỗi nhìn thấy).

**GREEN (JVM, mô phỏng guard hỏng bằng probe ném đúng chuỗi lỗi của log):** `V109GuardContainmentTest` — runner chuyển 5 loại lỗi (EIIE←PatternSyntax, NoClassDefFoundError, StackOverflowError, `GuardUnavailableException`, `PatternSyntaxException`) thành thẻ `GUARD_UNAVAILABLE`, input giữ nguyên, 0 đề xuất, không có toast "Đã lưu"; `analyzeInput` không mở kết nối nào; runner không bị "nhiễm độc" sau lỗi.

## 7. Test đã chạy trên binary này

JDK 22, `--offline`, `--rerun-tasks`: **383/383 trong cây** (357 nền + 26 mới: `V109PortableRegexTest` 10, `V109GuardContainmentTest` 16); **385/385 kèm 2 test ngoài cây**; golden **66/66** (39 chấp nhận, 0 sai); holdout **46/46**; 0 lỗi, 0 bỏ qua (`out/wally_evidence_v109/final*`).

| Yêu cầu của directive | Test |
|---|---|
| `CancelCommandParser` khởi tạo + toàn bộ luồng hủy | `whole cancel flow…` (All/Unspecified/Targeted/số thứ tự; "Hủy hợp đồng 2h", "Bỏ cuộc họp 1h" **không** là lệnh; `applyCancel` xóa đúng draft và vô hiệu hóa phân tích đang chạy) |
| "Học 45p" | `Học 45p - duration parsed and RECORD turn…` (45 phút, 1 đề xuất chờ xác nhận, lời AI bị bỏ, input được xóa đúng) |
| "Xin chào" | `Xin chào - natural conversation still reaches the user` (CHAT, lời đáp giữ nguyên) |
| "Hủy tất cả đề xuất" | như hàng cancel |
| số bằng chữ + phủ định | `spelled-number negation keeps only the affirmative activity` ("Không học hai tiếng nhưng làm việc hai tiếng" → chỉ Làm việc) |
| MIXED | `MIXED keeps both the proposal and the query` |
| không đề xuất sai khi parser hỏng | `runner converts any guard Error…`, `analyzeInput refuses before any network…` |
| mẫu cuối cùng không còn cấu trúc phụ thuộc bộ máy; mọi `Regex` qua `Rx` | `no production file builds a Regex…`, `registry … only portable patterns`, `every static pattern literal…`, `Rx itself has no inline-flag text…` |
| mẫu dịch = mẫu gốc trên JDK | `translated pattern behaves exactly like the original…` (146 mẫu × 2.336 câu, > 100.000 phép so, gồm NFD/NBSP/CRLF/VT/FF) |
| guard không chậm/không tràn ngăn xếp | `explicit patterns stay fast…` (7,5k ký tự qua 6 guard, ngưỡng 4 s) |
| ledger, khóa API, ngân sách, đồng hồ | toàn bộ suite nền không đổi và PASS |

## 8. Tệp và dòng đã đổi (so với v1.0.8 đóng gói; `CHANGED_LINES_summary.txt`, `SOURCE_DIFF_vs_v1.0.8_packaged.patch`)

Mã sản phẩm: `domain/usecase/Rx.kt` (mới, 1-156), `data/ai/GuardHealth.kt` (mới, 1-104), `AiFault.kt` 21-28 (+`GUARD_UNAVAILABLE`, cuối enum), `ProposalLifecycle.kt` 26-41/54-60, `VietnameseChatParser.kt` (13 vùng, 34-702), `QueryIntentParser.kt` (4 vùng, 110-335), `WallyAiGuards.kt` (6 vùng, 175-355), `WallyTurnPolicy.kt` 22-47, `StrictJson.kt` 1-7/24-30/62-69, `GeminiProposalClient.kt` 107-114 (`GuardHealth.require()`), `TimeWalletViewModel.kt` 5-11/269-279/740-760 (cổng + làm nóng), `WallyChatRunner.kt` 3-10/165-196, `ChatScreen.kt` 47-53/184-190/197-203, `app/build.gradle.kts` 13-20 (versionCode 10, 1.0.9). 13 tệp đổi, 5 tệp mới, 0 tệp xóa. Không đổi dependency, `AndroidManifest.xml`, tài nguyên, Room, origin binding, Keystore, đồng hồ, kiến trúc API-first; không mô hình cục bộ, không tải model, không backend/cache.

**Test bị retarget có chủ đích (không xóa, không nới lỏng):** `Ai01ClosureLifecycleTest."cancelling during analysis is reachable from the UI"` ghim chuỗi mã cũ `CancelCommandParser.parse(chatInputText)`; nay ghim `GuardHealth.isCancelCommand(chatInputText)` (hành vi giống hệt, nút Hủy vẫn bật khi đang phân tích) **và** thêm khẳng định mạnh hơn: `ChatScreen` không được gọi `CancelCommandParser` trực tiếp. `WallyChatLifecycleTest."every coroutine in the ViewModel uses the safe handler"` giữ nguyên; mã mới (làm nóng guard) được viết để tuân thủ bất biến đó.

## 9. Chưa chứng minh / rủi ro còn lại (nói thẳng)

- **Android runtime: NOT_TESTED** (không emulator/ADB theo quy định). ICU4C đã chạy là bản 68.2 của Windows; Android 14/15 dùng ICU mới hơn. Tập cấu trúc cuối chỉ gồm tính năng ICU cổ điển (lớp ký tự, `\p{L}`, `\x{..}`, `\z`, tiền/hậu kiểm tra một ký tự, lượng từ) nên rủi ro thấp nhưng **không bằng 0**; chỉ máy thật chứng minh được.
- **Mô phỏng ≠ Android:** RED mô phỏng trên JVM dùng spy; GREEN containment dùng probe ném lỗi. Chúng chứng minh hợp đồng chứa lỗi và chữ ký lỗi, không chứng minh hành vi của ART.
- **Bản Owner đang dùng** (v1.0.6/7/8) không xác định được từ log; không ảnh hưởng kết luận (cả bốn cùng lỗi) nhưng Owner nên ghi dòng ⓘ phiên bản cũ.
- **Phạm vi F6:** bản sửa đóng đúng chữ ký `EIIE ← PatternSyntaxException ← CancelCommandParser.<clinit>` và cả họ lỗi cùng nguyên nhân (mọi `Regex` sản xuất). Nó **không** loại trừ nguyên nhân crash khác (Keystore, bộ nhớ…); vì vậy F6 chung vẫn `UNRESOLVED`.
- **Bộ so sánh dùng 2.336 câu**: bằng chứng là tương đương trên tập đó, không phải chứng minh hình thức. Khác biệt đã biết ngoài phạm vi: `\b` Unicode xấp xỉ "Alphabetic" bằng `\p{L}\p{M}\p{Nd}\p{Nl}\p{Pc}` + ZWNJ/ZWJ (khác với ký hiệu chữ hiếm như chữ cái trong vòng tròn Ⓐ; Wally không xử lý loại này); `\b` chế độ ASCII không sao chép quy tắc dấu kết hợp-sau-ký tự-gốc của JDK (chỉ tác động văn bản NFD, đã nằm trong corpus và không thấy lệch).
- Guard canary có thể chặn Wally nếu động cơ regex trên máy lệch ngữ nghĩa ở 2 câu mẫu; đây là hành vi an toàn có chủ đích (báo lỗi rõ, không ghi thiếu kiểm tra).
- Các rủi ro cũ (Journal không lazy, Keystore giải mã trong init VM, debug APK không R8, chưa đo trên máy) không đổi.

## 10. Gói tái kiểm cho Reviewer

```
# GREEN (cây hiện tại)
$env:JAVA_HOME="C:\Program Files\Java\jdk-22"; .\gradlew testDebugUnitTest --offline --rerun-tasks
python out\wally_evidence_v109\tools\icu_replay.py app\build\wally_icu <báo_cáo.txt> --label GREEN
# RED: giải nén out\wally_candidate_v108\source_v1.0.8.tar vào thư mục mới, chép thêm gradlew, gradle\wrapper, local.properties,
#      chép out\wally_evidence_v109\red_on_v1.0.8\red_sources\PatternSpy.kt -> ...\domain\usecase\, PatternSpyAi.kt -> ...\data\ai\,
#      Red*Test.kt và app\src\test\...\V109TestSupport.kt -> app\src\test\java\com\timewallet\mvp\;
#      lượt 1 (không biến môi trường): --tests *RedIcuDumpTest ; lượt 2: $env:WALLY_SIMULATE_ICU="true" rồi --tests *RedSimulatedIcuTest
```
Các script đóng gói chỉ nằm trong thư mục tạm của phiên; lệnh trên tái tạo được kết quả. Reviewer nên tự rà ba điểm: (1) `Rx.translate` so với bảng §3; (2) `grep -R "(?U)" app/src/main` chỉ ra chú thích; (3) chạy lại harness ICU trên ICU của Android nếu có.
