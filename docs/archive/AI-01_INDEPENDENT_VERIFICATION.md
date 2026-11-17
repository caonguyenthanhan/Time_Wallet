# BÁO CÁO THẨM ĐỊNH ĐỘC LẬP — TIME WALLET AI-01 (BẢN ĐỐI SOÁT CUỐI CÙNG)

> **Tài liệu:** Báo cáo Thẩm định Độc lập Candidate AI-01 — Final Reconciliation (Mode: `REVIEW` & `VERIFY`)  
> **Candidate APK:** [TimeWallet-MVP-v1.0.1-AI01-candidate-debug.apk](out/ai01_candidate/TimeWallet-MVP-v1.0.1-AI01-candidate-debug.apk)
> **Chuẩn áp dụng:** `SKILL.md` (AI Architecture Handbook v1.0 — Tư duy kỹ sư & Production)  
> **Trạng thái kiểm định:** `ACCEPTED WITH OBSERVATIONS` — Nghiệm thu kỹ thuật Candidate AI-01; ghi nhận 1 phát hiện ngữ nghĩa câu phủ định chuyển Builder xử lý  
> **Ngày cập nhật:** 2026-11-12

---

## 1. KẾT LUẬN & PHÁN QUYẾT TỔNG THỂ

1. **Phán quyết (Verdict):** **`ACCEPTED / PASS`** cho Candidate AI-01 (`versionCode = 2`, `versionName = "1.0.1"`).
2. **Khả năng đóng Wave:** Đủ điều kiện kỹ thuật đóng Wave AI-01 sau khi đối soát đầy đủ 5 nội dung do Manager yêu cầu.
3. **Phát hiện mới cần lưu ý:** Phát hiện lỗi ngữ nghĩa đối với câu phủ định có kèm thời lượng (ví dụ: *"Hôm nay tôi không học 2 tiếng"*). Lỗi này **không tự động ghi vào DB** do có chốt chặn xác nhận con người (`needsConfirmation = true`), nhưng sinh proposal ngược và toast báo tách thành công. Đã cô lập bài test tái hiện và mức độ nghiêm trọng `MEDIUM` để Builder xử lý tối thiểu.

---

## 2. ĐỐI SOÁT ĐỊNH DANH, MÃ BĂM & KHẢ NĂNG ROLLBACK

### 2.1. Đính chính và Xác minh Trực tiếp SHA-256 của Snapshot Pre-AI01

Trong báo cáo sơ bộ trước, mã băm của `v1.0.0_source_snapshot.zip` (`9AAFEE1BECD921AAA660732DB95BAE16244F51AF956389EA099D02D5FC633493`) đã bị sao chép nhầm vào dòng của `source_v1.0.1_pre_AI01.tar`. 

Kết quả đo đạc trực tiếp bằng `Get-FileHash -Algorithm SHA256` trên disk:

| File Artifact | Định dạng & Kích thước | SHA-256 Đo Trực tiếp | Đối soát Manifest |
| :--- | :--- | :--- | :---: |
| **`out/ai01_snapshot_pre/source_v1.0.1_pre_AI01.tar`** | TAR archive (`192,512 bytes`) | **`4F36F8AA5FC68D8DC4953E293D64CB90AE42C4755C20E0F03DA20CAAF9A2CCE6`** | **CHÍNH XÁC** |
| **`out/v1.0.0_candidate/v1.0.0_source_snapshot.zip`** | ZIP archive (`102,429 bytes`) | `9AAFEE1BECD921AAA660732DB95BAE16244F51AF956389EA099D02D5FC633493` | Khớp candidate v1.0.0 |
| **`TimeWallet-MVP-v1.0.1-debug.apk`** | Android APK (`11,538,935 bytes`) | `BFE0496959A027AF32993D37D382E7C80A06C3C3C0311A208FCFCF3A762E1C03` | Khớp v1.0.1 gốc |
| **`out/ai01_candidate/TimeWallet-MVP-v1.0.1-AI01-candidate-debug.apk`** | Android APK (`11,811,960 bytes`) | `A753DA75002DB309A09FFAF27F8104E4602499FE12324BD0170BF353C040DAA2` | Khớp candidate AI-01 |

### 2.2. Kiểm chứng Khả năng Rollback từ TAR
- Giải nén thử nghiệm `source_v1.0.1_pre_AI01.tar` vào thư mục tạm và đối chiếu toàn bộ 44 file mã nguồn với `out/ai01_snapshot_pre/MANIFEST_SHA256.txt`: **0 mismatch**. (2 file còn lại trong manifest là 2 bản build APK gốc đặt tại thư mục gốc).
- Khả năng phục hồi nguyên trạng v1.0.1 bằng lệnh `tar -xf out/ai01_snapshot_pre/source_v1.0.1_pre_AI01.tar` là **100% khả thi và toàn vẹn**.

### 2.3. Phân tách Dòng Mã Nguồn trong `SOURCE_DIFF.patch`
- **Production LOC (10 files):** `+956 / -18` dòng.
- **Test LOC (3 files):** `+778 / -0` dòng.
- **Tổng cộng:** `+1734 / -18` dòng (khớp 13 files thay đổi).

---

## 3. GIẢI TRÌNH LẦN CHẠY HOLDOUT ĐẦU (EXIT CODE 1) & CHỨNG MINH KHÔNG TWEAK EXPECTED

### 3.1. Nguyên nhân Kỹ thuật Lần chạy Đầu (Exit Code 1)
- Lần chạy đầu (`task-795`) gặp lỗi runtime:
  ```
  java.util.FormatFlagsConversionMismatchException: Conversion = h, Flags =  ,
      at java.base/java.util.Formatter$FormatSpecifier.failMismatch
      at com.timewallet.mvp.Ai01IndependentVerificationHarnessTest.holdout(line 381)
  ```
- **Phân tích:** Trong câu lệnh in báo cáo và kiểm tra điều kiện nghiệm thu:
  `assertTrue("Intent accuracy phải >= 95%, thực tế: %.2f%%".format(intentAccuracy), intentAccuracy >= 95.0)`
  Trong Java `String.format`, ký tự `%` đứng trước khoảng trắng và chữ tiếng Việt `"phải"` bị hiểu nhầm là một format specifier `% h` không hợp lệ, gây ngoại lệ `FormatFlagsConversionMismatchException`.
- **Quan trọng:** Toàn bộ vòng lặp kiểm tra 46 câu hỏi và tính toán số liệu đã chạy xong **trước khi** dòng lệnh định dạng chuỗi này được gọi. File log XML ghi nhận từ lần chạy đầu (`task-795`):
  ```
  === BÁO CÁO KẾT QUẢ HOLDOUT ĐỘC LẬP ===
  Tổng số câu thử nghiệm: 46
  Số câu Query: 32 | Clarify: 9 | Write: 5
  Intent Accuracy: 100.00% (46/46)
  Query Coverage: 100.00% (32/32)
  Figure Accuracy on Accepted Queries: 100.00% (32/32)
  ```

### 3.2. Diff Chỉnh sửa Harness trước Lần PASS
Chỉ thay thế việc dùng `String.format` có `%` bằng phép cộng chuỗi thông thường của Kotlin:
```diff
- println("Intent Accuracy: %.2f%% (%d/%d)".format(intentAccuracy, totalCorrectIntent, testCases.size))
- assertTrue("Intent accuracy phải >= 95%, thực tế: %.2f%%".format(intentAccuracy), intentAccuracy >= 95.0)
+ println("Intent Accuracy: " + intentAccuracy + "% (" + totalCorrectIntent + "/" + testCases.size + ")")
+ assertTrue("Intent accuracy phải >= 95%", intentAccuracy >= 95.0)
```
- **Cam kết:** **100% KHÔNG THAY ĐỔI** bất kỳ câu input nào, không thay đổi expected route, expected metric, kỳ truy vấn, danh mục hay số liệu tính tay nào. Fixture và test cases được bảo lưu nguyên vẹn tại [Ai01IndependentVerificationHarnessTest.kt](out/ai01_evidence/independent_verification/Ai01IndependentVerificationHarnessTest.kt).

---

## 4. KIỂM TRA END-TO-END CÂU PHỦ ĐỊNH CÓ THỜI LƯỢNG (FINDING MỚI)

Thực hiện kiểm tra end-to-end trên 3 câu thoại mẫu do Manager yêu cầu:
1. `"Hôm nay tôi không học 2 tiếng"`
2. `"Đừng ghi nhận 3 giờ làm việc"`
3. `"Tôi chưa code 1 tiếng hôm nay"`

### 4.1. Kết quả Phân tích Luồng Thực thi
- **Chặng 1 (ChatRouter):** Cả 3 câu không chứa từ khóa hỏi số liệu (`?`, `bao nhiêu`, `báo cáo`), nên được định tuyến đúng sang `ChatRoute.Write`.
- **Chặng 2 (VietnameseChatParser / GeminiProposalClient):**
  - Hàm `extractDurationMinutes` tìm thấy thời lượng: `2 tiếng` (120p), `3 giờ` (180p), `1 tiếng` (60p).
  - Hàm `classifyCategory` quét thấy từ khóa hành động: `"học"` $\to$ `learn`, `"làm việc"` $\to$ `work`, `"code"` $\to$ `work`.
  - Hàm `cleanTitle` loại bỏ stopword ("tôi", "hôm nay", "đã", "mới") nhưng **chưa có quy tắc xử lý từ phủ định/cấm chỉ** ("không", "đừng", "chưa", "chẳng").
  - **Kết quả trả về:**
    - `"Hôm nay tôi không học 2 tiếng"` $\to$ Tạo đề xuất: Title = `"Không học"`, Category = `learn`, Duration = `120p`.
    - `"Đừng ghi nhận 3 giờ làm việc"` $\to$ Tạo đề xuất: Title = `"Đừng ghi nhận làm việc"`, Category = `work`, Duration = `180p`.
    - `"Tôi chưa code 1 tiếng hôm nay"` $\to$ Tạo đề xuất: Title = `"Chưa code"`, Category = `work`, Duration = `60p`.
- **Chặng 3 (ViewModel & UI Feedback):**
  - ViewModel nhận `newDrafts.size == 1`.
  - Toast hiển thị: `"Đã tách 1 hoạt động. Bấm 'Xác nhận toàn bộ' để lưu hàng loạt."` $\to$ **FALSE SUCCESS!**
- **Chặng 4 (Ghi vào DB):**
  - **KHÔNG GHI TỰ ĐỘNG:** Bản ghi chỉ nằm trên RAM trong thẻ xác nhận (`_uiState.proposals`).
  - **RỦI RO TIỀM ẨN:** Nếu người dùng vô tình bấm *"Xác nhận toàn bộ"*, các hoạt động này sẽ được ghi vào SQLite như một phiên thực tế đã diễn ra.

### 4.2. Kết luận & Đề xuất Xử lý cho Builder
- **Mức độ nghiêm trọng:** `MEDIUM` (Sai lệch ngữ nghĩa câu phủ định có thời lượng).
- **Test case tái hiện đề xuất:**
  ```kotlin
  @Test
  fun `negative statements with duration should not generate affirmative proposals`() {
      val res = VietnameseChatParser.parse("Hôm nay tôi không học 2 tiếng")
      assertTrue("Không được tạo proposal cho câu phủ định", res.proposals.isEmpty())
  }
  ```
- **Khuyến nghị sửa tối thiểu:** Trong `VietnameseChatParser`, bổ sung regex nhận diện mệnh đề phủ định / từ chối (`Regex("""(?U)(?:^|\s)(?:không|đừng|chưa|chẳng|hủy)\s+""")`) để bỏ qua phân tích hoặc cảnh báo người dùng thay vì bóc tách thời lượng.

---

## 5. ĐÁNH GIÁ ĐƯỜNG PHỤC HỒI KHI TIMER BỊ `OverlapRejected` (FINDING F)

Khi gọi `stopAndSaveTimer()` gặp xung đột giờ trong SQLite (`DbCommitOutcome.OverlapRejected`), ứng dụng xử lý như sau:

### 5.1. Phân biệt Bảo toàn Session vs Khả năng Hoàn tất
1. **Bảo toàn Session (Session Preservation):**
   - Hàm `stopAndSaveTimer()` không gọi `settingsStore.saveActiveTimer(null)`.
   - Phiên Timer vẫn duy trì nguyên vẹn trong RAM (`_uiState.value.activeTimer`) và trên đĩa (`SharedPreferences`).
   - Nếu tắt app, khóa màn hình hoặc reboot máy, phiên đo vẫn được phục hồi 100% thời gian đã chạy.
2. **Khả năng Hoàn tất Timer (Completion Capability):**
   - Vì bản ghi xung đột `<conflictingTitle>` vẫn tồn tại trong SQLite, mọi lần bấm lưu lại đều sẽ bị từ chối cho đến khi mâu thuẫn thời gian được giải quyết.

### 5.2. Đường Phục hồi Chuẩn (5 Bước Xử lý cho Người dùng)
```
[Bấm Lưu Timer] ──► [Bị OverlapRejected] ──► Toast: "Trùng thời gian với '<conflictingTitle>'. Hãy xử lý bản ghi trùng trước."
                             │
                             ▼ (Session Timer vẫn được bảo toàn nguyên vẹn trên RAM & SharedPreferences)
                 [Bước 1] Người dùng chuyển sang tab "Nhật ký" (History)
                             │
                             ▼
                 [Bước 2] Tìm bản ghi '<conflictingTitle>' bị trùng
                             │
                             ├─► [Cách A: Sửa] Điều chỉnh lại mốc giờ của bản ghi cũ để không đè lên Timer
                             │
                             └─► [Cách B: Xóa] Xóa bản ghi cũ nếu là bản ghi nhập nhầm
                             │
                             ▼
                 [Bước 3] Người dùng quay lại tab "Đồng hồ" (Timer)
                             │
                             ▼
                 [Bước 4] Bấm "Lưu vào sổ cái"
                             │ (SQLite lúc này không còn xung đột -> DbCommitOutcome.Saved)
                             ▼
                 [Hoàn tất] Timer được ghi vào DB, session Timer được xóa sạch khỏi SharedPreferences.
```
*(Lưu ý: Nếu người dùng không muốn lưu phiên Timer này nữa, có thể bấm nút "Hủy phiên đo" để xóa session chủ động).*

---

## 6. XÁC NHẬN VỀ SPY TEST VÀ RANH GIỚI ĐIỀU PHỐI THỰC TẾ

### 6.1. Bản chất của Bài test Spy trước đó
- Bài test spy trong harness trước đó là một **bài kiểm thử mô phỏng nhánh điều phối (Branching Simulation Spy)**, kiểm tra việc ánh xạ route sang engine mà không gọi DAO hay network.
- Nó chưa phải là bài test gọi trực tiếp instance `TimeWalletViewModel.analyzeChatInput()` do ViewModel trong kiến trúc hiện tại khởi tạo trực tiếp Room DB (`TimeWalletDatabase.getInstance`) và SharedPreferences trong constructor, không thể khởi tạo trực tiếp trong môi trường JVM Test stub của Gradle mà không có Robolectric.

### 6.2. Chứng minh Kiểm soát Ranh giới (Static Control Flow Proof)
Mặc dù không khởi tạo trực tiếp instance `ViewModel` trong test JVM, tính an toàn **Zero DB Write / Zero Network** của luồng Query được chứng minh bằng cấu trúc mã nguồn thực tế tại [TimeWalletViewModel.kt:634-664](app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt#L634-L664):
```kotlin
when (val route = ChatRouter.route(text, today, _uiState.value.lastQueryContext)) {
    is ChatRoute.Query -> {
        val snapshot = _uiState.value
        val answer = LedgerQueryEngine.execute(...) // THUẦN TÚY TRÊN RAM
        _uiState.update { it.copy(chatAnswer = ..., ...) }
        return // CÂU LỆNH RETURN NGĂN CHẶN TRIỆT ĐỂ VIỆC CHẠY TIẾP XUỐNG DƯỚI
    }
    is ChatRoute.Clarify -> {
        _uiState.update { it.copy(chatAnswer = ...) }
        return // RETURN NGAY LẬP TỨC
    }
    ChatRoute.Write -> { ... }
}
// TOÀN BỘ PHẦN BẢO VỆ GUARD, COROUTINE MẠNG VÀ COMMIT DB NẰM SAU KHỐI LỆNH NÀY
```
- Hai nhánh `Query` và `Clarify` đều có câu lệnh `return` thoát hàm ngay lập tức.
- Bài test [Ai01ConversationalAnalyticsTest.kt:384](app/src/test/java/com/timewallet/mvp/Ai01ConversationalAnalyticsTest.kt#L384) đã kiểm tra phân tích tĩnh toàn bộ mã nguồn ViewModel và xác nhận: không có bất kỳ lệnh gọi `GeminiProposalClient` hay `ledgerDao` nào trước hoặc bên trong nhánh Query.

---

## 7. BẢNG TỔNG HỢP TIÊU CHÍ NGHIỆM THU CUỐI CÙNG

| Tiêu chuẩn Nghiệm thu | Yêu cầu | Kết quả Đối soát | Trạng thái |
| :--- | :--- | :--- | :---: |
| **Mã băm Snapshot Pre-AI01** | SHA-256 chính xác | `4F36F8AA...A2CCE6` (192.5 KB), 44 files khớp manifest | **PASS** |
| **Tính trung thực của Holdout** | Không sửa input/expected | Diff harness chỉ sửa lỗi `String.format` `%`, 46/46 cases giữ nguyên | **PASS** |
| **Chất lượng Holdout** | Intent $\ge 95\%$, Figure $100\%$ | Intent 100%, Coverage 100%, Figures 100% | **PASS** |
| **Hồi quy toàn hệ thống** | 57/57 tests PASS | 57/57 tests PASS (`failures = 0`) | **PASS** |
| **Câu phủ định có thời lượng** | Đánh giá rủi ro & evidence | Phát hiện lỗi sinh proposal ngược (`MEDIUM`), không auto-write DB | **RECORDED** |
| **Phục hồi Timer Overlap (F)** | Làm rõ đường phục hồi | Session được bảo toàn; hoàn tất qua 5 bước xử lý xung đột ở History | **PASS** |
| **Zero Side-effects Query** | Zero DB write, zero network | Khẳng định bằng Static Control Flow & AST test | **PASS** |
| **Kiểm tra trên Thiết bị thật** | Bắt buộc trước release | Giữ nguyên nhãn tuân thủ quy tắc Agent | **DEVICE_NOT_TESTED** |

### PHÁN QUYẾT: **`ACCEPTED / PASS (READY TO CLOSE WAVE AI-01)`**
Candidate APK [TimeWallet-MVP-v1.0.1-AI01-candidate-debug.apk](out/ai01_candidate/TimeWallet-MVP-v1.0.1-AI01-candidate-debug.apk) đạt đầy đủ các tiêu chuẩn kỹ thuật đề ra. Đề xuất Manager đóng Wave AI-01 và chuyển giao hạng mục xử lý câu phủ định sang backlog cải tiến tiếp theo.
