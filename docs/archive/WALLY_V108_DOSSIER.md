# WALLY v1.0.8 — Hồ sơ candidate (`versionCode 9`): đóng V106-N1 theo kiến trúc + toàn vẹn hồ sơ V107-F3

**Trạng thái:** CANDIDATE_UNVERIFIED — chờ Reviewer re-check **chỉ** `V106-N1` và toàn vẹn artifact, rồi chuyển Owner nghiệm thu.
**Không tuyên bố:** DEVICE_PASS, AI_LIVE_PASS, SPEC_ACCEPTED. **F6: UNRESOLVED.**
**Phạm vi:** `MANAGER DIRECTIVE — FINAL TRANSACTION STATUS FIX`. Giữ API-first, 8 tầng, origin binding, chính sách riêng tư. Không thêm tính năng, không emulator/ADB, không gọi API thật, không xin credential qua chat. Live Eval v1.2 giữ nguyên.

## 1. Nhận diện nhị phân và bảo toàn hiện vật

| Hiện vật | SHA-256 | Trạng thái |
|---|---|---|
| **v1.0.8 (candidate này)** — `out/wally_candidate_v108/TimeWallet-MVP-v1.0.8-WALLY-HOTFIX-candidate-debug.apk` | `e13c35f49ae474a57c79c5be269307cb8e5f7d3a410e75528a9e553683cd62a5` | **Hiện hành.** 11.785.442 byte; versionCode **9**, versionName **1.0.8**, package `com.timewallet.mvp` |
| v1.0.7 (vòng 2) | `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61` | Nguyên vẹn. Folder `out/wally_candidate_v107/` và `out/wally_evidence_v107/` **không bị sửa** |
| v1.0.7 vòng 1 | `a8f6727f42992a3b0afa43b52a3644ef2ab936f527a80424d3ce05974143b11f` | Đã thay thế trước đó; giữ ở `superseded_round1/` |
| v1.0.6 / v1.0.5 | `0d4e722b…bc09f` / `e80f7522…d2e1d` | Nguyên vẹn |

Lưu ý nhận diện: kích thước v1.0.8 trùng với v1.0.7 **vòng 1** (11.785.442 byte) nhưng đó là **trùng ngẫu nhiên**: SHA-256 khác hoàn toàn và dex của v1.0.8 chứa `WallyTurnPolicy`, `isTransactionStatus`, "Trạng thái ghi nhận", "chờ bạn xác nhận" mà hai bản v1.0.7 không có (quét dex, tệp `apk_scan_v1.0.8.txt` và lệnh đối chiếu trong báo cáo).

| Mục | Giá trị |
|---|---|
| Chữ ký | Debug keystore, cert SHA-256 `402f1d417186bdb1f73975f57f4ceb7976e2e683fa26adf896e1590af222530d`, **trùng v1.0.7/v1.0.6** (`SIGNING_CERT_COMPARE.txt`, `apksigner`) |
| Nguồn | `source_v1.0.8.tar` SHA-256 `91e6730160bf6b9d55b97059c2e747e967b92197ab13f3eb7b6d1a4a4b17be20`; manifest `MANIFEST_SHA256_after.txt` **105 dòng**, `sha256sum -c` = 105 OK / 0 sai |
| Snapshot **trước khi sửa** | `out/wally_v108_snapshot_pre/source_tree_as_found_pre_V108.tar` (`79d0ee20…91ab`) + `MANIFEST_SHA256_as_found.txt` (103 dòng, lệch 1 dòng so với manifest v1.0.7: xem addendum) |
| Diff | `SOURCE_DIFF_vs_v1.0.7_packaged.patch`; `SOURCE_DIFF_vs_tree_as_found.patch`; `CHANGED_FILES_vs_v1.0.7.txt` (12), `NEW_FILES_vs_v1.0.7.txt` (2), `REMOVED_FILES_vs_v1.0.7.txt` (0, rỗng có chủ đích = không tệp nào bị xóa) |
| Log build | `assemble_console.txt` (2.710 byte), `assemble_exitcode.txt` = `exit=0` |
| Live Eval | `WALLY_LIVE_EVAL_PLAN_v1.2.md` `0b5144c8…17ea0` và v1.1 `12e9acb9…4681` khớp sidecar |
| Toàn vẹn hồ sơ v1.0.7 (V107-F3) | `WALLY_V107_ADDENDUM_F3.md` |

## 2. V106-N1 đóng theo kiến trúc (không thêm regex làm bằng chứng chính)

Nguyên tắc: **AI không bao giờ là nguồn của câu "đã lưu / chưa lưu / đang chờ".** Trạng thái đó chỉ lấy từ ứng dụng. Mỗi lượt được `WallyTurnPolicy` xếp loại, loại quyết định nội dung thẻ:

| Loại lượt | Điều kiện | Người dùng thấy gì | Lời AI |
|---|---|---|---|
| **RECORD** | có đề xuất, không truy vấn | thẻ đề xuất (nay ghi rõ **"N hoạt động đề xuất · chờ bạn xác nhận"** và "Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận.") + toast do ứng dụng | **bỏ hoàn toàn** |
| **MIXED** (V107-F2) | có đề xuất **và** truy vấn | headline + số liệu do `LedgerQueryEngine` tính, dòng đầu: "Số liệu bên dưới tính từ các bản ghi đã lưu tại thời điểm hỏi; các đề xuất đang chờ xác nhận chưa được tính và chưa được lưu." + thẻ đề xuất | **bỏ hoàn toàn**; mọi truy vấn hợp lệ được giữ |
| **TRANSACTION_CHAT** (V107-F1) | không đề xuất, không truy vấn, và câu liên quan ghi nhận / hỏi "đã lưu chưa" / **đang còn đề xuất chờ** | thẻ **do ứng dụng tạo, là headline**: "Chưa có gì được lưu. Còn N đề xuất đang chờ bạn xác nhận." hoặc "Chưa có gì được lưu: Wally chưa tạo được đề xuất ghi nhận nào từ câu này." + hướng dẫn (nêu rõ hoạt động và thời lượng / Ghi thủ công) | **bỏ hoàn toàn** (không đứng cạnh, không bị "chú thích" bên dưới) |
| **QUERY** | chỉ truy vấn | số liệu do engine; lời AI chỉ là phần mở đầu và bị bỏ nếu có cách nói hoàn tất khi còn đề xuất chờ | giữ nếu không mâu thuẫn |
| **PLAIN_CHAT** | không liên quan giao dịch, không có đề xuất chờ | lời AI tự nhiên | giữ (đã qua `ReplyGuard`) |

Các điểm khác:
- `GeminiProposalClient.finalizeOutcome`: hễ có đề xuất (RECORD **và MIXED**) ⇒ `reply = null`.
- **Chỉ báo "Đã lưu" sau commit thành công:** các thông báo "Đã lưu…" duy nhất nằm ở `ConfirmFeedback` sau `ConfirmResult.Committed`; `WallyTurnPolicy` không có đường nào tự nói "đã lưu" (test tĩnh).
- **Thẻ trạng thái không cũ đi:** `ChatAnswer.isTransactionStatus`; ViewModel xóa thẻ khi Xác nhận / Bỏ / Bỏ hết (số "đang chờ" không còn đúng sau khi đổi).
- `ReplyGuard` (heuristic, **lớp phụ**): thêm 3 mẫu cho 4 câu mới của Reviewer (không có "đã/vừa", không có số). Không còn là bảo đảm duy nhất: câu lách mà không regex nào bắt (ví dụ "Cứ yên chí đi, phần việc đó mình lo trọn gói rồi nhé.") vẫn **không hiện** ở các lượt giao dịch vì thẻ do ứng dụng thay thế (test riêng).

Tệp sản phẩm đổi: `WallyTurnPolicy.kt` (mới), `WallyChatReducer.kt`, `WallyAiGuards.kt`, `GeminiProposalClient.kt`, `LedgerQueryEngine.kt` (`ChatAnswer` thêm 1 trường mặc định), `TimeWalletViewModel.kt`, `ChatScreen.kt` (tiêu đề thẻ đề xuất). Không đổi dependency, manifest, tài nguyên, Room, origin, Keystore, timer, ngân sách.

## 3. Evidence hồi quy

| Giai đoạn | Thư mục | Kết quả |
|---|---|---|
| **RED** (test mới chạy trên **nguồn v1.0.7 đóng gói**) | `out/wally_evidence_v108/red_vs_v107/` | `V108TransactionStatusTest` (biến thể bỏ 1 test chỉ-API-mới để biên dịch được): 15 test, **10 FAIL** — RECORD/MIXED/CHAT/CLARIFY với pending, hỏi trạng thái, 24 mẫu, tiêu đề thẻ, xóa thẻ cũ. 5 test còn lại là chốt hồi quy (PASS cả hai bản) |
| **Final, trong cây** | `out/wally_evidence_v108/final/` (`console.txt`, `exitcode.txt` = 0, `xml/`, `per_suite_counts.txt`) | 34 tệp XML / **357/357 PASS** (341 của v1.0.7 + 16 mới); 0 fail, 0 error, 0 skip |
| **Final + 2 test ngoài cây** | `out/wally_evidence_v108/final_with_external/` (+ `golden_holdout_extract.txt`) | 35 tệp / **359/359 PASS**: toàn bộ **343 baseline v1.0.7 (341 + 2 harness)** + 16 mới; **golden 66/66**, **holdout 46/46** |
| Build | `out/wally_candidate_v108/assemble_console.txt` | `exit=0` |

**Các test của v1.0.7 có assertion bị cập nhật vì hành vi cũ chính là điều chỉ thị yêu cầu đổi** (không xóa, không bỏ test; tổng 343 baseline vẫn đủ và PASS):

| Test | Thay đổi |
|---|---|
| `V107RemediationTest › … safe pending confirmation phrasing and mixed analytical query…` | lượt MIXED nay `reply == null` (trước đây giữ lời AI); đề xuất và truy vấn vẫn được giữ |
| `V107RemediationTest › … 14 adversarial replies …` | **đổi tên và chú thích cho đúng nguồn gốc** (6 mẫu có tên + 8 tái dựng, không phải 14 mẫu nguyên bản của Reviewer) |
| `V107Round2Test` (4 test viết lại, vẫn 7 test) | kiểm headline do ứng dụng tạo thay cho "ghi chú nhỏ"; câu lách mới (v1.0.8 đã chặn mẫu cũ bằng regex nên đổi sang mẫu không regex nào bắt) |
| `WallyAiFaultInjectionTest › … a mixed record plus question sentence…` | MIXED `reply == null` |
| `WallyEightLayerTest › reducer - a mixed result yields drafts and an answer card together…` | headline là kết quả engine; dòng đầu là dòng của ứng dụng |
| `WallyApiFirstGuardsTest › no input is lost on failure…` (tĩnh) | mốc cắt chuỗi đổi vì thẻ nay dựng ở `WallyTurnPolicy.buildCard` |

**Điều kiện đạt của chỉ thị — đối chiếu:**
- *Không còn lời khẳng định đã lưu giả trong case đối kháng:* mẫu `reviewerOriginal14` (14 mẫu Reviewer khôi phục, REVIEW_WALLY_V107.md §3.2) + 6 mẫu vòng 2 + 4 mẫu mới = **24 mẫu**, kiểm trên ReplyGuard và trên pipeline sản xuất RECORD, MIXED, CHAT, CLARIFY, có và không có đề xuất đang chờ. Thêm 1 câu lách không regex nào bắt.
- *Không mất đề xuất đúng hay truy vấn hợp lệ:* test RECORD (đề xuất 45 phút giữ nguyên), MIXED (1 đề xuất + 1 truy vấn, engine trả số).
- *Số liệu không gồm draft chưa commit:* test MIXED với sổ cái 30p + 2 draft chờ ⇒ engine báo đúng 30p.
- *Không regression phủ định, timer, budget, origin, Keystore, cancellation:* các suite tương ứng của v1.0.7 vẫn PASS nguyên (`WallyOriginBindingTest`, `V106KeystoreTest`, `V106TimerLongSessionTest`, `V106BudgetQueryTest`, `V106ChatRunnerTest`, `V107RemediationTest` ca phủ định…).

## 4. Còn lại, không sửa

- `ReplyGuard` vẫn là heuristic; bảo đảm cấu trúc là bảng ở mục 2. Lượt **QUERY** vẫn hiển thị lời AI khi không mâu thuẫn (có thẻ số liệu của ứng dụng đi kèm).
- Lượt hội thoại không liên quan giao dịch **và** không có đề xuất chờ: nếu AI tự khẳng định đã lưu bằng cách nói mà regex chưa biết, lời đó vẫn có thể hiện (không có gì để mâu thuẫn). Đây là đánh đổi có chủ đích để giữ hội thoại tự nhiên.
- Bộ phân loại "liên quan ghi nhận" là heuristic văn bản (thời lượng, động từ ghi, câu hỏi "đã lưu chưa"); câu hỏi thông tin có giờ ("3 giờ chiều tôi nên làm gì?") được loại trừ. Câu hiếm có thể bị xếp sai theo hướng an toàn (hiện thẻ trạng thái thay vì lời AI).
- F6 UNRESOLVED; thiết bị và AI thật NOT_TESTED. Kiểm tra nhanh "không AI local / hiệu năng": `WALLY_AUDIT_NO_LOCAL_AI_PERFORMANCE.md` (đã quét cả APK v1.0.8).

## 5. Gói tái kiểm cho Reviewer (chỉ V106-N1 và toàn vẹn artifact)

| Việc | Cách làm |
|---|---|
| Xác minh binary | `sha256sum` APK = `e13c35f4…62a5`; `aapt2 dump badging` = versionCode 9 / 1.0.8; `apksigner verify --print-certs` = `402f1d41…530d`; `sha256sum -c` manifest (bỏ CR nếu mở bằng PowerShell) = 105 OK |
| Toàn vẹn v1.0.7 | `sha256sum` APK v1.0.7 = `3df0fee7…c61`, tar `8f733b0e…326dde0`; `WALLY_V107_ADDENDUM_F3.md` mô tả sai lệch 1 tệp test |
| Chạy lại | `JAVA_HOME=<JDK 22> ./gradlew testDebugUnitTest --offline --rerun-tasks` ⇒ 357; chép tạm `Ai01IndependentVerificationHarnessTest.kt` ⇒ 359 |
| V106-N1 | `V108TransactionStatusTest` (16 test); **chạy lại probe `ADV_REPLYGUARD` của Reviewer** trên `ReplyGuard` + `parseAndValidateResponseJson` + `WallyChatReducer.applyResult` (RECORD, MIXED, CHAT có/không draft chờ, CLARIFY); kỳ vọng: không câu AI nào xuất hiện ở lượt giao dịch; MIXED giữ query; thẻ chat giao dịch có headline "Chưa có gì được lưu…" |
| Giao diện | thẻ đề xuất: "N hoạt động đề xuất · chờ bạn xác nhận" + "Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận." (kiểm tĩnh; chưa thấy trên máy) |

Kết quả Reviewer/Owner phải ghi cùng SHA-256 APK thực tế ở `WALLY_V108_ACCEPTANCE_RECORD.md`. Không dùng kết quả v1.0.7 hay v1.0.6 cho binary này.
