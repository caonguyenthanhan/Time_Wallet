# WALLY v1.0.9 — Gói xác minh tập trung cho Reviewer (5 điểm theo MANAGER DECISION)

Phạm vi theo Manager: **không build v1.0.10, không review kiến trúc mới, Owner không cần đợi Reviewer để cài.** Gói này không đổi mã nguồn, không đổi APK; mọi kiểm tra bổ sung chạy trên **bản sao tạm** của `source_v1.0.9.tar` (cây thật vẫn 110/110 theo manifest). Nguồn của các kiểm tra: `out/wally_evidence_v109/reviewer_pack/` (có `scratch_sources/` để chạy lại).

**Binary:** `out/wally_candidate_v109/TimeWallet-MVP-v1.0.9-WALLY-HOTFIX-candidate-debug.apk` · SHA-256 `0f5f23d9b7eb718367d3d87a26bb3cbbc7fb2884e124163fc5b980bd116ca4e2`.

## 0. Đính chính một diễn đạt trong dossier (không đổi kết luận, không đổi APK)

`WALLY_V109_DOSSIER.md` §6 viết "146 mẫu đưa vào ICU4C". Con số 146 là **số dòng trong tệp dump**, không phải 146 mẫu khác nhau: dump gồm 108 mẫu lấy lúc chạy (JVM chạy cả bộ test nên nhiều nhánh lười đã được thực thi) + 38 literal tĩnh, và 38 dòng này được đưa vào với cờ ignore-case **luôn tắt** dù ngoài sản xuất nhiều chỗ bật. Chỉ có **109 chuỗi mẫu khác nhau**. Phát hiện này đã được xử lý bằng một lượt phát lại bổ sung với **đúng cờ thật** (§1c), kết quả sạch. Dossier đã được ghi chú tương ứng.

## 1. `Rx` bao phủ toàn bộ production Regex, giữ ngữ nghĩa, fail-closed

**(a) Bao phủ: 114 điểm dựng = 49 `Rx.uni` + 65 `Rx.ascii`.**

| Phân loại (`reviewer_pack/site_scope.txt`, `reconcile_counts.txt`) | Số điểm | Cách được bao phủ |
|---|---|---|
| Có nội suy `$…`, **tất cả là khởi tạo thuộc tính** của `object` (không có điểm nào trong thân hàm) | 24 | dựng lúc khởi tạo lớp ⇒ canary `GuardHealth` + registry lúc chạy |
| Literal tĩnh, thuộc tính | 43 | như trên |
| Literal tĩnh, trong thân hàm (nhánh lười) | 47 | trích từ mã nguồn → dịch → biên dịch + phát lại ICU (§1c) |
| **Tổng** | **114** | |

Quy tắc "mọi Regex phải qua `Rx`" được giữ bằng test quét mã nguồn (`no production file builds a Regex or Pattern outside Rx`: cấm `Regex(`, `.toRegex(`, `Pattern.compile`, `java.util.regex.Pattern`, `(?U)` ngoài `Rx.kt`). `grep -R "(?U)" app/src/main` chỉ còn 5 dòng chú thích trong `Rx.kt`; dex v1.0.9 có 0 chuỗi `(?U)` (v1.0.8: 46).

**(b) Ngữ nghĩa JDK ↔ ICU.**
- JDK: mẫu dịch ≡ mẫu gốc (`(?U)…` hoặc mặc định) trên 146 dòng × 2.336 câu (> 100.000 phép so, gồm HOA, NBSP, tab, CR/LF/CRLF, NEL, VT, FF, LS/PS, NFD): 0 lệch.
- ICU4C thật: 0 lệch JVM↔ICU, 0 lỗi chạy (§1c). Cấu trúc ngoài tập con bị `Rx` từ chối (test `constructs outside the portable subset are rejected…`).
- Khác biệt đã biết, nói thẳng: `\b` Unicode xấp xỉ "Alphabetic" bằng `\p{L}\p{M}\p{Nd}\p{Nl}\p{Pc}`+ZWNJ/ZWJ (không phủ chữ cái ký hiệu hiếm như Ⓐ); `\b` ASCII không sao chép quy tắc dấu-kết-hợp của JDK (chỉ văn bản NFD; không thấy lệch trong corpus). ICU thử là 68.2 của Windows, **không phải Android**.

**(c) Phát lại ICU với đúng cờ thật.** Hợp của mọi tổ hợp (chuỗi mẫu, ignore-case) có thật ngoài sản xuất = **109**: 100 (registry lúc chạy khi chạy riêng một test) + 9 literal tĩnh chưa được thực thi lúc chạy. 100 mẫu kia nằm trong dump chính (có cờ thật); 9 mẫu còn lại phát lại bổ sung: **9/9 biên dịch được, 21.024 cặp, 0 lệch, 0 lỗi chạy** (`reviewer_pack/icu_replay_report_SUPPLEMENTARY_static_true_flags.txt`).

**(d) Fail-closed khi lỗi — chạy thật, trên bản sao tạm có CỐ Ý một lỗi:** `WallyTurnPolicy.recordVerb` bị chèn `\w` (ngoài tập con di động), `reviewer_pack/failclosed_report.txt`:

| Quan sát | Kết quả |
|---|---|
| `GuardHealth.check()` | `ok=false guard=WallyTurnPolicy errorClass=ExceptionInInitializerError`; chuỗi nguyên nhân `ExceptionInInitializerError ← IllegalArgumentException "Rx: escape \w is outside the portable subset…"`; **không ném** |
| `GeminiProposalClient.analyzeInput` | ném `GuardUnavailableException`; **số kết nối mở = 0** |
| `WallyChatRunner` | `chatFault=GUARD_UNAVAILABLE`, phase `ERROR`, **0 đề xuất**, không thẻ trả lời, **input giữ nguyên**, `isAnalyzingChat=false` |
| Ghi thủ công (`LedgerValidator`), đồng hồ (`TimerSegments`) | vẫn hoạt động (`Valid`; 1 đoạn) |
| Nguồn Manual/Timer/Budget/Ledger/Dashboard/Journal/DerivedStats | **không tham chiếu** `GuardHealth`, guard nào, hay `Rx` |

## 2. Đối chiếu RED/GREEN trên ICU; giải thích 98 / 146 / 114

| Con số | Nghĩa chính xác |
|---|---|
| **114** | số **điểm dựng** `Regex` trong mã nguồn (49 `(?U)` + 65 không) — số lần gọi, không phải số mẫu khác nhau |
| 90 / 24 | trong 114: 90 literal tĩnh (84 bộ (chế độ, văn bản, cờ) khác nhau), 24 có nội suy |
| **98** (RED) | số mẫu **khác nhau mà v1.0.8 thực sự dựng** trong lượt chạy RED (đo bằng spy, đã giải nội suy; 46 trong đó mở đầu `(?U)`). Thấp hơn 114 vì: nhiều điểm dùng chung một chuỗi; nhánh lười không được chạm thì không đo được |
| **100** (GREEN, chạy riêng) | cùng cách đo trên v1.0.9: **98 mẫu RED ↔ 98 mẫu GREEN khớp 1-1 sau khi dịch (RED-only = 0)** + 2 mẫu mới (`countFigure`, `digitsOnly`: tôi nâng từ nhánh lười lên trường, nên v1.0.9 dựng chúng sớm còn v1.0.8 thì không chạm tới trong lượt RED) |
| **108** | cùng cách đo nhưng JVM chạy cả bộ 383 test (các test khác thực thi thêm nhánh lười) |
| **146** | số dòng trong dump chính = 108 + 38 literal tĩnh (cờ ignore-case bị tắt) — **chỉ 109 chuỗi khác nhau** (§0) |
| **109** | số tổ hợp (mẫu, cờ thật) khác nhau trong sản xuất = 100 + 9 |

RED trên ICU4C thật: 46/98 mẫu lỗi `U_REGEX_RULE_SYNTAX` tại offset 3; 52 mẫu biên dịch được thì có 567 lệch JVM↔ICU (121.472 cặp). GREEN: 0 lỗi, 0 lệch. Cùng một corpus (SHA-256 `ec6d162a…2c45` trùng ở hai bên). **49 điểm `(?U)` là 49 chuỗi khác nhau** (47 được thực thi khi chạy riêng v1.0.9, cộng 2 nhánh lười chỉ có trong phát lại tĩnh). RED thực thi 46: ba điểm còn lại là `countFigure` (nằm trong nhánh lười của v1.0.8, v1.0.9 đã nâng lên trường) và 2 nhánh lười mà corpus không chạm tới. Cả ba dùng cùng cờ `(?U)` nên theo cùng cơ chế sẽ bị ICU từ chối, nhưng **không được đo riêng** (không có phát lại RED bổ sung cho chúng).

## 3. Full SHA-256 APK và manifest nguồn

```powershell
(Get-FileHash "out\wally_candidate_v109\TimeWallet-MVP-v1.0.9-WALLY-HOTFIX-candidate-debug.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
# 0f5f23d9b7eb718367d3d87a26bb3cbbc7fb2884e124163fc5b980bd116ca4e2
(Get-FileHash "out\wally_candidate_v109\source_v1.0.9.tar" -Algorithm SHA256).Hash.ToLowerInvariant()
# add321024c47d8b39bde95fdb752e2a0af45ee97780d6c4846ecee8b2a7b37bf
```
Manifest `out/wally_candidate_v109/MANIFEST_SHA256_after.txt`: 110 dòng; `sha256sum -c` (sau `tr -d '\r'`) → 110 OK / 0 lỗi, kiểm lại lần cuối sau khi viết xong các tài liệu này. APK: versionCode 10, versionName 1.0.9, cert `402f1d41…530d` (trùng v1.0.8), chỉ quyền INTERNET. Các APK cũ nguyên vẹn (`ALL_APK_SHA256.txt`; không tệp nào trong `out/wally_candidate_v106..v108` đổi sau mốc snapshot).

## 4. V106-N1 (False Success) — đã xử lý ở v1.0.8, không hồi quy ở v1.0.9

Kiến trúc v1.0.8 (`WallyTurnPolicy`: trạng thái giao dịch do **ứng dụng** nói; lời tự do của AI bị bỏ ở RECORD/MIXED/chat liên quan ghi nhận) **không bị chạm** ngoài việc chuyển `Regex(` → `Rx.uni(` trong `WallyTurnPolicy.kt` (4 mẫu, bỏ `(?U)`). Bằng chứng không hồi quy trên binary này:
- `V108TransactionStatusTest` **không bị sửa** (không nằm trong `CHANGED_FILES_vs_v1.0.8.txt`) và **16/16 PASS**, gồm 24 mẫu false-success (14 mẫu Reviewer khôi phục nguyên văn + 6 vòng 2 + 4 mẫu V107) qua `ReplyGuard` và cả đường RECORD/MIXED/CHAT/CLARIFY; mẫu "lách" mà không regex nào bắt (chỉ kiến trúc giữ được); thẻ trạng thái cũ bị xóa khi draft đổi.
- Cùng các suite liên quan, không sửa, PASS: `V107RemediationTest` 12, `V107Round2Test` 7, `V106ReviewRound2Test` 13, `V106ResponseContractTest` 20, `WallyApiFirstGuardsTest` 13, `Ai01FeedbackAndGuardTest` 11.
- Mẫu `WallyTurnPolicy`/`ReplyGuard` dịch ≡ gốc trên JDK (§1b) và 0 lệch trên ICU, nên bộ chọn "câu trạng thái/ghi nhận" hành xử như trước.
- Việc Reviewer nên làm (đã nêu từ v1.0.8, vẫn chưa làm được ở đây): chạy lại **probe `ADV_REPLYGUARD` gốc** của Reviewer; tệp đó không có trong repo.

## 5. Failure containment không bypass guard và không ảnh hưởng Manual/Timer/Budget/Ledger

- **Không có đường bỏ qua guard:** không `try/catch` quanh guard để rơi về đề xuất chưa kiểm. Ba lớp cùng chặn (ViewModel cổng trước lệnh gọi guard đầu tiên; `analyzeInput` câu lệnh đầu `GuardHealth.require()` trước mọi xử lý/kết nối; `WallyChatRunner` bắt `Throwable` trừ OOM thật và kết quả chưa từng được áp). Test `ViewModel checks guard health before the first guard call…` ghim thứ tự trong mã; `V109GuardContainmentTest` (16) chạy 5 loại lỗi qua runner thật.
- **Không ảnh hưởng nghiệp vụ khác:** §1d (kèm kiểm tĩnh: các nguồn Manual/Timer/Budget/Ledger không tham chiếu guard hay `Rx`). `GuardHealth` chỉ được gọi từ ViewModel (`analyzeChatInput`, làm nóng), `GeminiProposalClient.analyzeInput`, `ChatScreen` (vị từ không ném).
- **Hành vi an toàn có chủ đích:** nếu canary ngữ nghĩa (2 câu) lệch trên máy thật thì Wally bị chặn kèm lỗi rõ thay vì chạy với guard không đáng tin.
- **Không chứng minh được ở đây:** hành vi trên ART/ICU của Android. Điều kiện FIX_VERIFIED của chữ ký này vẫn là Owner gửi lại đúng câu đã crash trên máy thật.

## 6. Việc tiếp theo (không thêm Wave/Gate)

1. Owner cài đè ngay theo `WALLY_OWNER_CHECKLIST_V109.md` (không đợi Reviewer).
2. Owner xác nhận bước 4 (gửi lại đúng câu đã crash) → F6 chữ ký này `FIX_VERIFIED`.
3. Nếu qua: chạy **Live Eval v1.2 trên đúng SHA-256 `0f5f23d9…ca4e2`** (`WALLY_LIVE_EVAL_PLAN_v1.2.md`, hash `0b5144c8…17ea0`, không đổi); ghi vào `WALLY_V109_ACCEPTANCE_RECORD.md` §4.
4. Không emulator/ADB; không gửi khóa API qua chat.
