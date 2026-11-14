# BÁO CÁO REVIEW ĐỘC LẬP KIẾN TRÚC AI — TIME WALLET (AI-01)

> **Tài liệu:** Báo cáo Đánh giá Kiến trúc AI & An toàn Dữ liệu (Mode: `REVIEW`)  
> **Phiên bản ứng dụng kiểm tra:** Time Wallet v1.0.1 (`versionCode = 2`, `versionName = "1.0.1"`, Room DB `version = 3`)  
> **Chuẩn áp dụng:** `SKILL.md` (AI Architecture Handbook v1.0 — Tư duy kỹ sư & Production)  
> **Trạng thái kiểm tra:** Mã nguồn cục bộ (READ-ONLY) — `25/25 UNIT_TEST_PASS`, `BUILD_PASS`, `DEVICE_NOT_TESTED`  
> **Ngày thực hiện:** 2026-11-12

---

## 1. KẾT LUẬN TỔNG QUAN & EXECUTIVE SUMMARY

- **Đánh giá chung:** Hệ thống AI của Time Wallet v1.0.1 tuân thủ tốt nguyên tắc **Non-AI First**, **Minimum Architecture** và **Privacy by Design**. Toàn bộ logic tính toán sổ cái, ngân sách, analytics và báo cáo phản tư đều được xây dựng bằng mã nguồn Kotlin tất định (deterministic). AI chỉ đóng vai trò bộ trích xuất dữ liệu bán cấu trúc (information extraction) từ câu thoại tiếng Việt, hoàn toàn không có quyền can thiệp hay ghi trực tiếp vào cơ sở dữ liệu.
- **Ranh giới tin cậy (Trust Boundary):** Được thiết lập rất chặt chẽ giữa tầng AI Client không tin cậy (`GeminiProposalClient`) và tầng Sổ cái lõi (`LedgerValidator` + `LedgerDao`). Không có rò rỉ dữ liệu cá nhân từ cơ sở dữ liệu Room sang AI endpoint (`[ĐÃ XÁC MINH]`).
- **Các điểm nghẽn & rủi ro phát hiện (`Findings`):**
  1. **[Finding A - Medium]** *Cảnh báo Fallback và mục bị hủy bị ẩn ngầm (False Success & Masked Warnings):* Khi AI gọi mạng thất bại và chuyển sang bộ phân tích cục bộ, nếu bộ phân tích cục bộ trích xuất được `>= 1` hoạt động thì thông báo lỗi/fallback bị nuốt chửng hoàn toàn. Tương tự, nếu một hoạt động trong lô bị loại bỏ do sai mốc giờ, người dùng không được cảnh báo về hoạt động đã mất.
  2. **[Finding B - Medium-High]** *Thiếu chốt chặn Re-entrancy / Debounce tại `analyzeChatInput`:* Nhấp đúp hoặc bấm liên tiếp nút "Phân tích" sẽ kích hoạt nhiều coroutine gọi API đồng thời, gây nhân bản đề xuất trùng lặp lên màn hình UI (`_uiState.proposals`).
  3. **[Finding C - Medium]** *Nguy cơ cắt cụt ngữ cảnh ngày tương đối trong `VietnameseChatParser`:* Hàm `trimmed.take(25)` có thể cắt ngang cụm từ "hôm qua" nếu câu có tiền tố dài, dẫn đến việc đoạn đầu nhận ngày hôm qua còn các đoạn sau bị gán nhầm sang ngày hôm nay.
  4. **[Finding D - Medium]** *Chưa lọc mệnh đề tổng hợp ("tổng cộng 5 tiếng"):* Cả Prompt của AI và Regex parser đều chưa có cơ chế loại trừ các câu chốt tổng, dẫn đến việc tạo thêm một bản ghi thời gian tổng cộng gây tính trùng (double-counting).
  5. **[Finding E - Low-Medium]** *JSON Parser dễ bị lỗi nếu Model kèm lời chào/văn bản trò chuyện:* Payload không cấu hình `response_format: {"type": "json_object"}` và Regex bóc tách JSON chỉ xử lý markdown ở đầu/cuối chuỗi, dễ gây crash parse và ép fallback ngầm.
  6. **[Finding F - Low]** *Khoảng trễ đồng bộ `activeTimer` giữa RAM và SQLite khi commit batch:* `commitBatchRecordsTransactionally` khóa mutex và kiểm tra overlap trong SQLite nhưng không kiểm tra lại `activeTimer` lưu trong SharedPreferences tại thời điểm commit.

---

## 2. SƠ ĐỒ ĐƯỜNG DỮ LIỆU & RANH GIỚI QUYỀN (TRUST BOUNDARY)

```
[Người dùng nhập text] 
       │
       ▼
[ChatScreen UI] ──(bấm Phân tích)──► [TimeWalletViewModel.analyzeChatInput()]
                                                │
                 ┌──────────────────────────────┴──────────────────────────────┐
                 ▼ (Opt-In OFF hoặc Blank API Key)                              ▼ (Opt-In ON + Valid API Key)
       [VietnameseChatParser] (Offline, Regex)                    [GeminiProposalClient] (HTTPS /v1/chat/completions)
                 │                                                             │ (chỉ gửi: userText, today, nowTime, tz)
                 │                                                             │ (fallback an toàn về VietnameseChatParser nếu lỗi)
                 └──────────────────────────────┬──────────────────────────────┘
                                                ▼
                                   [ParseChatOutcome (proposals)]
                                                │
                                                ▼ (CHỈ LƯU TRÊN RAM)
                             [UiState.proposals: List<EditableProposalDraft>]
                                                │
                                    (NGƯỜI DÙNG CHỈNH SỬA / XEM)
                                                │
                                                ▼ (Người dùng bấm: Xác nhận toàn bộ / Xác nhận đơn)
                             [TimeWalletViewModel.confirmProposalsList()]
                                                │
                                                ▼ (Tầng Kiểm soát Nghiệp vụ tất định)
                             [LedgerValidator.validateBatchAtomic()]
                                    │ (Kiểm tra: thời lượng 1-1440p, start..end, overlap với DB & activeTimer)
                                    ├─► [INVALID] ──► Hủy toàn bộ lô (All-or-Nothing), Toast cảnh báo
                                    │
                                    ▼ [VALID]
                             [LedgerDao.commitBatchRecordsTransactionally()]
                                    │ (LedgerConcurrencyGate.mutex.withLock + Room @Transaction)
                                    │ (Kiểm tra: cấu trúc, trùng lặp idempotencyKey, overlap nội bộ, overlap DB)
                                    │
                                    ▼ [DB COMMIT THÀNH CÔNG]
                           [Room SQLite Database v3 (ledger_records)]
                                                │
                                                ▼ (Room Flow Trigger phản ứng)
                             [TimeWalletViewModel.observeAllRecords()]
                                                │
                                                ▼ (Tất định 100% bằng code Kotlin)
                             [LedgerAnalyticsCalculator.computeDayStats()]
                             [LedgerAnalyticsCalculator.computeWeeklyInsights()]
                             [DailyReviewAssistant.buildSnapshotForDate()]
                                                │
                                                ▼
                             [TimeWalletUiState] ──► [UI Screens: Dashboard / Nhật ký / Phân tích / Review]
```

### Bảng Phân Định Ranh Giới Quyền (Read / Propose / Execute)

| Quyền hạn | Hiện trạng trong Code | Đánh giá & Ranh giới Bảo mật | Nhãn xác minh |
| :--- | :--- | :--- | :---: |
| **AI ĐƯỢC ĐỌC GÌ** | Chỉ đọc: `userText` (câu chat vừa nhập), `today` (ngày hiện tại), `currentTimeHm` (giờ phút), `timezoneId` (múi giờ). | **TUYỆT ĐỐI AN TOÀN:** AI không được cấp quyền đọc Sổ cái, không đọc Timer, không đọc Ngân sách, không đọc Ghi chú cá nhân/Feynman. | **`[ĐÃ XÁC MINH]`** |
| **AI ĐƯỢC ĐỀ XUẤT GÌ** | Chỉ đề xuất danh sách `ActivityProposal` dạng cấu trúc: tên, danh mục (1 trong 8), thời lượng, khoảng giờ (nếu có), trạng thái tương lai (`isFuturePlanned`). | **ĐÚNG THIẾT KẾ:** Không được đề xuất thay đổi cấu hình, không can thiệp cài đặt ngân sách hay xóa dữ liệu. | **`[ĐÃ XÁC MINH]`** |
| **AI ĐƯỢC THỰC THI GÌ** | **HOÀN TOÀN KHÔNG CÓ QUYỀN THỰC THI (ZERO WRITE PRIVILEGE).** | **CHUẨN KIẾN TRÚC:** Mọi đề xuất của AI chỉ là bản nháp trên RAM (`EditableProposalDraft`). Bắt buộc người dùng bấm xác nhận mới gọi lệnh ghi. | **`[ĐÃ XÁC MINH]`** |
| **NGUỒN SỰ THẬT (SSOT)** | Nguồn sự thật duy nhất là **SQLite Room Database v3** (`ledger_records`, `category_budgets`, `daily_reviews`, `activity_review_notes`). | AI không phải là nguồn sự thật. Mọi số liệu báo cáo, tổng kết và câu hỏi phản tư đều do Kotlin tính từ SQLite. | **`[ĐÃ XÁC MINH]`** |

---

## 3. RÀ SOÁT KIẾN TRÚC AI 8 TẦNG (THEO SKILL.MD)

### Tầng 01: Model
- **Hiện trạng:** Sử dụng endpoint OpenAI-compatible (`https://api.foza.ai/v1`), model mặc định `cinb/gemini-3.8-flash`. `temperature = 0.1`, timeouts: connect 15s, read 25s (`[ĐÃ XÁC MINH]`).
- **Đánh giá:** Nhiệt độ 0.1 phù hợp cho tác vụ trích xuất cấu trúc. Model flash có độ trễ thấp và chi phí tối ưu.
- **Khoảng trống:** Chưa hỗ trợ retry có giới hạn (bounded retry) với backoff khi gặp mã lỗi 429 hoặc 503; hiện tại lỗi mạng lập tức rơi vào fallback offline.

### Tầng 02: Prompt
- **Hiện trạng:** System prompt được tạo động trong `GeminiProposalClient.buildSystemPrompt`, quy định rõ ràng: (1) Cấm tự bịa mốc giờ khi chỉ có thời lượng; (2) Chỉ điền mốc giờ khi có khoảng tường minh; (3) Khóa cứng 8 danh mục hợp lệ; (4) `needsConfirmation: true` (`[ĐÃ XÁC MINH]`).
- **Khoảng trống:**
  - Chưa có quy tắc hướng dẫn xử lý ngày tương đối ("hôm qua", "hôm kia", "ngày mai"). Mẫu trả về cố định `"dateLocal": "$today"`, khiến model có xu hướng ép mọi hoạt động về ngày hôm nay dù người dùng nói "hôm qua".
  - Chưa hướng dẫn model cách nhận diện và bỏ qua các mệnh đề tổng kết/chốt tổng ("tổng cộng 5h", "cả ngày hết 8h").

### Tầng 03: RAG
- **Hiện trạng:** **Không sử dụng RAG (Zero RAG).** Không truy xuất dữ liệu quá khứ (`[ĐÃ XÁC MINH]`).
- **Đánh giá:** Quyết định kiến trúc chuẩn xác theo nguyên tắc Minimum Architecture. Bài toán nhập liệu nhanh không cần RAG.
- **Khuyến nghị cho đợt nâng cấp Rolly:** Khi bổ sung AI hội thoại phân tích lịch sử, **tuyệt đối không gửi raw ledger records sang proxy bên thứ ba**. Phải thực hiện tổng hợp cục bộ bằng SQL/Room trước, chỉ gửi số liệu đã ẩn danh và thu gọn (Aggregated Context).

### Tầng 04: Tools / MCP
- **Hiện trạng:** Không dùng Tool calling / Function calling / MCP runtime (`[ĐÃ XÁC MINH]`).
- **Cơ chế Idempotency:** Mỗi `EditableProposalDraft` được cấp một `idempotencyKey = UUID.randomUUID()`. Khi commit batch, `LedgerDao` lọc qua `seenIdempotencyKeys` và `countByIdempotencyKey`, ngăn chặn ghi đúp nếu gửi trùng payload (`[ĐÃ XÁC MINH]`).

### Tầng 05: Authorization & Trust Boundary
- **Hiện trạng:** Ranh giới quyền rõ ràng. AI Client chạy ở vùng không tin cậy (Untrusted Sandbox). Toàn bộ dữ liệu do AI trả về phải được chuyển đổi thành DTO trung gian và chỉ được ghi xuống DB khi có hành động tường minh từ con người (`[ĐÃ XÁC MINH]`).

### Tầng 06: Guardrails
- **Hiện trạng:** Bộ lọc nghiệp vụ `LedgerValidator.validateBatchAtomic` đóng vai trò Guardrail cứng:
  - Kiểm tra thời lượng hợp lệ trong khoảng `1..1440` phút (`[ĐÃ XÁC MINH]`).
  - Kiểm tra tính tương thích chéo giữa `explicit_range` và `durationMinutes` (sai số `<= 120s`) (`[ĐÃ XÁC MINH]`).
  - Ngăn chặn triệt để chồng lấn thời gian thực tế (`overlap`) trong nội bộ batch và với các bản ghi đã có trong SQLite (`[ĐÃ XÁC MINH]`).
- **Khoảng trống:** Tầng UI ViewModel thiếu guardrail chống re-entrancy (bấm nút liên tục).

### Tầng 07: Cache / Router
- **Hiện trạng:** Router 2 nhánh:
  - Nếu `!optInEnabled || apiKey.isBlank()` → Route sang `VietnameseChatParser` (100% Offline).
  - Nếu bật Opt-in → Route sang Cloud API, tự động fallback về Offline khi có lỗi mạng hoặc ngoại lệ (`[ĐÃ XÁC MINH]`).
- **Đánh giá:** Kiến trúc đơn giản, hiệu quả, đảm bảo tính sẵn sàng cao của ứng dụng ngay cả khi mất mạng.

### Tầng 08: Observability / Evals
- **Hiện trạng:** 25 unit tests kiểm tra domain logic, SQLite migration, encryption và validator (`[ĐÃ XÁC MINH]`).
- **Khoảng trống:** Thiếu test case kiểm định chuỗi truyền tin cảnh báo (Warning propagation) từ Client lên ViewModel, và thiếu test case kiểm tra hành vi UI khi bấm phân tích đồng thời.

---

## 4. BẢNG CHI TIẾT CÁC PHÁT HIỆN ĐỘC LẬP (FINDINGS)

### Finding A: Cảnh báo Fallback & Hoạt động bị loại bỏ bị ẩn ngầm (False Success)
- **Mức độ nghiêm trọng:** `MEDIUM`
- **Vị trí bằng chứng:** [TimeWalletViewModel.kt:648-654](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt#L648-L654)
- **Hiện trạng:**
  ```kotlin
  if (newDrafts.isEmpty()) {
      val warn = outcome.warnings.firstOrNull() ?: "..."
      showToast(warn)
  } else {
      showToast("Đã tách ${newDrafts.size} hoạt động. Bấm \"Xác nhận toàn bộ\" để lưu hàng loạt.")
  }
  ```
- **Hậu quả:**
  1. Khi Cloud API gặp lỗi (ví dụ API key sai, 401 Unauthorized, rớt mạng), hệ thống fallback sang parser offline. Nếu parser offline nhận diện được `>= 1` hoạt động, toast hiển thị thành công bình thường. Người dùng hoàn toàn không biết rằng API key của họ không hoạt động và họ đang chạy bản offline.
  2. Nếu người dùng nhập 3 hoạt động nhưng 1 hoạt động bị loại do sai mốc giờ (`abs(rangeSeconds - durationSeconds) > 120L`), cảnh báo loại bỏ nằm trong `outcome.warnings` bị bỏ qua. Người dùng tưởng rằng hệ thống đã nhận đủ 3 hoạt động.
- **Hành vi kỳ vọng:** Hiển thị cảnh báo kết hợp nếu có hoạt động bị loại hoặc nếu có fallback (ví dụ: *"Đã tách 2 hoạt động (Offline). Lưu ý: 1 hoạt động bị bỏ qua do lệch mốc giờ"*).

---

### Finding B: Thiếu chốt chặn Re-entrancy / Debounce tại `analyzeChatInput`
- **Mức độ nghiêm trọng:** `MEDIUM-HIGH`
- **Vị trí bằng chứng:** [TimeWalletViewModel.kt:614-625](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt#L614-L625)
- **Hiện trạng:**
  Trong hàm `analyzeChatInput()`, không có câu lệnh kiểm tra trạng thái đang phân tích:
  ```kotlin
  fun analyzeChatInput() {
      val text = _uiState.value.chatInputText.trim()
      if (text.isEmpty()) { ... return }
      // THIẾU: if (_uiState.value.isAnalyzingChat) return
      _uiState.update { it.copy(isAnalyzingChat = true) }
      viewModelScope.launch { ... }
  ```
- **Hậu quả:** Nếu người dùng nhấp đúp (double-tap) hoặc bấm nhiều lần vào nút "Phân tích" khi mạng chậm, nhiều coroutine sẽ chạy song song. Khi hoàn thành, lệnh `_uiState.update { state -> (state.proposals + newDrafts).take(15) }` sẽ cộng dồn danh sách kết quả nhiều lần, làm xuất hiện các đề xuất trùng lặp trên giao diện. Các hoạt động chưa gắn giờ (`REPORTED_UNTIMED`) nếu được xác nhận toàn bộ sẽ bị ghi đúp vào sổ cái.
- **Hành vi kỳ vọng:** Kiểm tra ngay đầu hàm: `if (_uiState.value.isAnalyzingChat) return`, hoặc disable nút gửi trên UI khi `isAnalyzingChat == true`.

---

### Finding C: Nguy cơ cắt cụt ngữ cảnh ngày tương đối trong `VietnameseChatParser`
- **Mức độ nghiêm trọng:** `MEDIUM`
- **Vị trí bằng chứng:** [VietnameseChatParser.kt:161-164](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/usecase/VietnameseChatParser.kt#L161-L164)
- **Hiện trạng:**
  ```kotlin
  val globalTomorrow = Regex("""(?U)(?:^|\s)(?:ngày mai|mai)(?=\s|$|:|,)""", ...).containsMatchIn(trimmed.take(25))
  val globalYesterday = Regex("""(?U)(?:^|\s)(?:hôm qua|đêm qua|yesterday)(?=\s|$|:|,)""", ...).containsMatchIn(trimmed.take(25))
  ```
- **Hậu quả:** Giới hạn cứng `trimmed.take(25)` kiểm tra ngữ cảnh toàn câu. Nếu người dùng nhập câu có tiền tố dài hơn 25 ký tự, ví dụ: `"Nhật ký công việc của hôm qua: họp 1h, viết báo cáo 2h"` (độ dài 37 ký tự), từ "hôm qua" nằm ngoài phạm vi 25 ký tự đầu tiên nên `globalYesterday = false`. Đoạn 1 chứa "hôm qua" nên nhận ngày hôm qua, nhưng Đoạn 2 ("viết báo cáo 2h") không chứa từ "hôm qua" nên bị gán nhầm sang ngày hôm nay (`today`).
- **Hành vi kỳ vọng:** Quét ngữ cảnh toàn câu trên phần tiền tố trước dấu hai chấm đầu tiên (hoặc toàn bộ câu) thay vì cắt cứng ở 25 ký tự.

---

### Finding D: Nguy cơ tính trùng thời lượng do mệnh đề tổng hợp ("tổng cộng 5h")
- **Mức độ nghiêm trọng:** `MEDIUM`
- **Vị trí bằng chứng:** [GeminiProposalClient.kt:111-127](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L111-L127) và [VietnameseChatParser.kt:225-240](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/domain/usecase/VietnameseChatParser.kt#L225-L240)
- **Hiện trạng:** Người dùng nói: *"Học 2h, làm việc 3h, tổng cộng 5h"*. Cả AI System Prompt và Regex Parser đều xem "tổng cộng 5h" là một hoạt động thứ ba độc lập với thời lượng 300 phút.
- **Hậu quả:** Hệ thống tạo ra 3 đề xuất với tổng thời lượng 10 giờ thay vì 5 giờ. Vì là hoạt động chưa gắn mốc giờ (`REPORTED_UNTIMED`), khi người dùng bấm xác nhận toàn bộ, thuật toán cộng dồn sẽ ghi nhận cả 10 giờ vào sổ cái.
- **Hành vi kỳ vọng:**
  - Trong Prompt: Bổ sung quy tắc: *"Bỏ qua các mệnh đề tổng kết, chốt tổng thời gian (ví dụ: 'tổng cộng 5 tiếng', 'cả ngày hết 8h'). Không tạo proposal cho mệnh đề tổng hợp."*
  - Trong `VietnameseChatParser`: Bỏ qua các segment bắt đầu hoặc chứa các từ khóa tổng hợp (`tổng cộng`, `tổng thời gian`, `tất cả`).

---

### Finding E: Rủi ro Parse JSON khi Model kèm văn bản trò chuyện / Thiếu `response_format`
- **Mức độ nghiêm trọng:** `LOW-MEDIUM`
- **Vị trí bằng chứng:** [GeminiProposalClient.kt:148-165](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L148-L165) và [GeminiProposalClient.kt:219-224](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt#L219-L224)
- **Hiện trạng:** Payload gửi lên OpenAI-compatible endpoint không kích hoạt `response_format: {"type": "json_object"}`. Bộ bóc tách chuỗi JSON chỉ dùng `replace` để bỏ markdown ở đầu và cuối chuỗi (`^```json` và ````$`).
- **Hậu quả:** Nếu một số model (hoặc proxy) sinh thêm lời dẫn ở đầu câu (ví dụ: *"Dưới đây là danh sách hoạt động: ```json {...} ```"*), lệnh `JSONObject(cleanedJson)` sẽ quăng `JSONException`, dẫn đến việc tự động fallback ngầm về parser offline mà không tận dụng được kết quả của AI.
- **Hành vi kỳ vọng:** Thêm `response_format` vào payload và dùng Regex trích xuất khối JSON bao bọc (`Regex("""\{[\s\S]*\}""")`) trước khi đưa vào `JSONObject`.

---

### Finding F: Khoảng cách đồng bộ `activeTimer` giữa RAM và SQLite khi commit batch
- **Mức độ nghiêm trọng:** `LOW`
- **Vị trí bằng chứng:** [LedgerDao.kt:193-207](file:///d:/desktop/Time_Wallet/app/src/main/java/com/timewallet/mvp/data/local/LedgerDao.kt#L193-L207)
- **Hiện trạng:** `activeTimer` được lưu trữ trong `SharedPreferences` (`TimerAndSettingsStore`). Khi người dùng bấm lưu lô đề xuất, `LedgerValidator.validateBatchAtomic` kiểm tra overlap với `activeTimer` lấy từ RAM. Tuy nhiên, khi bước vào `commitBatchRecordsTransactionally` bên trong khóa Mutex của `LedgerDao`, hàm này chỉ đối chiếu overlap với các dòng trong bảng `ledger_records` của SQLite mà không đối chiếu lại trạng thái Timer.
- **Hậu quả:** Nếu người dùng bật một phiên timer mới trên một luồng khác ngay giữa thời điểm validate và thời điểm commit DB, bản ghi trong batch có thể ghi đè lên khung giờ của timer đang chạy. (Rủi ro thấp vì ứng dụng xử lý tuần tự trên Main thread).

---

## 5. RÀ SOÁT LẠI CÁC BẢN VÁ F1–F5 (REGRESSION CHECK)

| Bản vá | Nội dung đã vá | Kết quả rà soát độc lập trên Source v1.0.1 | Trạng thái |
| :--- | :--- | :--- | :---: |
| **F1** | Mã hóa API Key bằng Android Keystore AES-GCM; loại trừ backup `sharedpref` và `database`. | `AesGcmKeystoreVault` chuẩn hóa AES/GCM 256-bit. `AndroidManifest.xml` đặt `allowBackup="false"`, `backup_rules.xml` và `data_extraction_rules.xml` loại trừ đúng toàn bộ domain nhạy cảm. | **PASS** (`[ĐÃ XÁC MINH]`) |
| **F2** | Công bố rõ host nhận dữ liệu, bắt buộc HTTPS, xóa API key khi đổi host. | `AiSettingsDialog` hiển thị đầy đủ host đích, dữ liệu gửi và chế độ offline mặc định. `extractValidatedHttpsHost` chặn URL không an toàn. Khi đổi URL sang host mới, API key cũ bị xóa trống. | **PASS** (`[ĐÃ XÁC MINH]`) |
| **F3** | Sửa lỗi thiếu giờ Timer sau reboot thiết bị bằng `BOOT_COUNT`. | `computeElapsedSeconds` phân biệt chính xác reboot dựa trên `startedBootCount != currentBootCount` hoặc `nowElapsed < startedElapsed`. Fallback đồng hồ tường an toàn. | **PASS** (`[ĐÃ XÁC MINH]`) |
| **F4** | Chuẩn hóa JSON Exporter bằng `JSONObject.quote`. | Hàm `jsonQuote` thủ công đã được thay thế 100% bằng `org.json.JSONObject.quote`. Không còn rủi ro với ký tự điều khiển `U+0000`–`U+001F`. | **PASS** (`[ĐÃ XÁC MINH]`) |
| **F5** | Đóng HTTP connection trong `finally`, bọc lỗi ngoại lệ, kiểm tra chéo `explicit_range` vs `duration`. | `connection?.disconnect()` nằm trong `finally`. Lỗi thô được thay bằng `SAFE_FALLBACK_WARNING_VI`. `explicit_range` được kiểm tra chéo cả ở AI parser và `validateBatchAtomic` (dòng 360). | **PASS (Kèm Finding A)** (`[ĐÃ XÁC MINH]`) |

---

## 6. KHUYẾN NGHỊ TEST CASES TỐI THIỂU CHO ĐỢT AI-01

Nhằm phục vụ nâng cấp AI conversational input và analytics theo trải nghiệm Rolly, khuyến nghị bổ sung 6 bài kiểm thử tự động sau vào bộ test domain:

1. **Test AI-01-T1 (Warning Propagation on Silent Fallback):**
   - *Kịch bản:* Giả lập `GeminiProposalClient` ném `IOException` hoặc `HTTP 401`. Người dùng nhập `"Học 45p"`.
   - *Kỳ vọng:* `outcome.warnings` chứa `SAFE_FALLBACK_WARNING_VI` và cảnh báo này phải được hiển thị lên UI/Toast, không bị ghi đè bởi thông báo thành công của parser offline.
2. **Test AI-01-T2 (Re-entrancy & Debounce Guard):**
   - *Kịch bản:* Gọi `analyzeChatInput()` 2 lần liên tiếp khi coroutine thứ nhất đang chạy (`isAnalyzingChat == true`).
   - *Kỳ vọng:* Lần gọi thứ hai bị từ chối ngay lập tức; `_uiState.proposals` chỉ chứa đúng 1 bộ đề xuất, không bị nhân đôi.
3. **Test AI-01-T3 (Long-Prefix Relative Date Propagation):**
   - *Kịch bản:* Nhập câu: `"Nhật ký các hoạt động của ngày hôm qua: họp 1h, viết tài liệu 2h, đọc sách 30p"`.
   - *Kỳ vọng:* Toàn bộ 3 đề xuất đều được gán `dateLocal = today - 1`, không đề xuất nào bị rơi về `today`.
4. **Test AI-01-T4 (Aggregate Sentence Deduplication):**
   - *Kịch bản:* Nhập câu: `"Sáng học 2h, chiều làm việc 3h, tổng cộng hôm nay được 5h"`.
   - *Kỳ vọng:* Chỉ tạo 2 đề xuất (Học 2h và Làm việc 3h), tổng thời lượng là 5h. Mệnh đề "tổng cộng hôm nay được 5h" bị bỏ qua.
5. **Test AI-01-T5 (Conversational JSON Wrapper Resilience):**
   - *Kịch bản:* Giả lập AI trả về chuỗi có lời dẫn: `"Chào bạn, đây là nhật ký: ```json {"proposals":[{"title":"Code","category":"work","durationSeconds":3600,"dateLocal":"2026-11-12","timeEvidence":"duration_only","needsConfirmation":true}]} ``` Chúc bạn làm việc tốt!"`.
   - *Kỳ vọng:* Bộ bóc tách trích xuất thành công đối tượng JSON, tạo đề xuất "Code" 1h bình thường mà không bị crash rơi vào fallback.
6. **Test AI-01-T6 (Batch Confirmation Cross-talk with Active Timer):**
   - *Kịch bản:* Bộ đếm Timer đang chạy từ 10:00 (hiện tại 10:30). Người dùng xác nhận một lô đề xuất trong đó có hoạt động từ 10:15 đến 11:00.
   - *Kỳ vọng:* `LedgerValidator.validateBatchAtomic` từ chối toàn bộ lô vì trùng mốc giờ với `activeTimer`.

---

## 7. CHECKLIST KIỂM TRA RUNTIME TRÊN THIẾT BỊ THẬT (DÀNH CHO OWNER)

Theo quy định tại `AGENTS.md`, AI không được khởi chạy Emulator hoặc cài đặt APK lên thiết bị. Các điều kiện runtime sau đây cần được Owner trực tiếp kiểm chứng trên máy thật:

- [ ] **1. Kiểm tra Android Keystore qua các lần khởi động lại app:** Bật Opt-in, nhập API key thật, đóng hẳn app (vuốt khỏi Task Manager), mở lại app và kiểm tra xem API key có tự giải mã và giữ nguyên cấu hình không.
- [ ] **2. Kiểm tra Reboot máy khi Timer đang chạy:** Khởi động bộ đếm Timer, khởi động lại điện thoại (Reboot), mở lại ứng dụng: kiểm tra xem số giây đo có bù đủ thời gian máy tắt dựa trên đồng hồ tường không.
- [ ] **3. Kiểm tra Gọi Cloud API thật qua mạng di động/WiFi:** Bật Opt-in với endpoint `https://api.foza.ai/v1`, nhập câu phức tạp và kiểm tra độ trễ (TTFT), tính chính xác của đề xuất và định dạng trên màn hình 390px.
- [ ] **4. Kiểm tra Chế độ Máy bay (Offline Fallback):** Bật chế độ máy bay, nhấn phân tích: kiểm tra xem hệ thống có tự chuyển mượt mà sang bộ phân tích cục bộ mà không bị đơ hoặc văng ứng dụng (ANR/Crash) không.
- [ ] **5. Kiểm tra Nâng cấp Room DB từ máy đã có dữ liệu cũ:** Cài đè bản APK v1.0.1 lên bản v1.0.0 đã có dữ liệu để kiểm chứng tính toàn vẹn của dữ liệu cũ sau migration.

