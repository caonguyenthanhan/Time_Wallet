# Time Wallet — Mobile App (Android Kotlin + Jetpack Compose)

> **Tuyên ngôn:** Thời gian là tài sản hữu hạn và không thể tích lũy. Quản lý thời gian để hiểu và chủ động phân bổ, không để phán xét nghỉ ngơi hay tối đa hóa thời gian làm việc.

Ứng dụng di động **Time Wallet** được xây dựng theo đặc tả [Time_Wallet_MVP_Spec.md](Time_Wallet_MVP/Time_Wallet_MVP_Spec.md) và thiết kế giao diện mẫu [Time_Wallet_Prototype.html](Time_Wallet_MVP/Time_Wallet_Prototype.html).

## 1. Kiến trúc & Công nghệ

- **Nền tảng:** Android Native (`minSdk 26`, `targetSdk 35`, Kotlin `2.0.21`, Jetpack Compose Material 3).
- **Cơ sở dữ liệu cục bộ (Offline-first):** Room SQLite (`time_wallet_ledger.db`, schema v4 kèm `MIGRATION_3_4` và `app/schemas/`) với ràng buộc `@Transaction` chống chồng lấn mốc giờ (`overlap`), khóa chống lưu trùng (`idempotencyKey`), cùng bảng `tags` và `goals` (theo phiên bản `validFrom`/`validTo` bảo toàn lịch sử).
- **Tách tầng Domain rõ ràng:**
  - `domain/model/`: `LedgerRecord`, `TimeLayer` (`MEASURED`, `REPORTED_TIMED`, `REPORTED_UNPLACED`, `ACTIVE`, `PLANNED`), `TimeCategory`, `ActivityProposal`, `ActiveTimerSession`, `Tag`, `Goal`.
  - `domain/usecase/`:
    - `LedgerValidator`: Kiểm tra đầy đủ các bất biến sổ cái (cặp `startedAt`/`endedAt`, chống chồng lấn thời gian, chặn ngày tương lai vào dữ liệu thực tế, kiểm tra danh mục hợp lệ).
    - `LedgerAnalyticsCalculator`: Thống kê ngày 24h và biểu đồ tuần 7 ngày theo múi giờ thực tế (hỗ trợ cả ngày chuyển DST), tách bạch phần có mốc giờ (`timedSeconds`), phần chỉ có thời lượng (`unplacedSeconds`) và phần chưa ghi nhận (`untrackedSeconds`).
    - `VietnameseChatParser` & `WallyAiGuards` (`ReplyGuard`, `AiProposalGuard`, `UntilNowGuard`, `BudgetClaimGuard`, `FutureStartGuard`): Bộ kiểm chứng tất định đầu ra AI, đối chiếu thời lượng/khung giờ/phủ định/câu hỏi lại và tính số liệu từ sổ cái cục bộ.
    - `TagRules`, `GoalRules`, `TimeRange`, `CoachDigestBuilder`, `CoachGuard` (`v1.1.0`): Quản lý Tag cá nhân không đè lịch sử, mục tiêu theo phiên bản, tính khoảng giờ kết thúc `"Bây giờ"`, dựng digest xác định cho AI Time Coach và chặn số bịa/phán xét/suy đoán nguyên nhân.
    - `ApiKeyHygiene`: Chuẩn hóa ký tự dán thừa và chặn khóa chứa ký tự điều khiển hoặc ngoài ASCII trước khi mở kết nối.
  - `data/ai/GeminiProposalClient`: Kiến trúc **API-First** kết nối OpenAI-compatible Chat Completions gateway theo cơ chế **Opt-in** (tự nhập API Key cá nhân, mã hóa Keystore; AI chỉ tạo đề xuất chờ người dùng xác nhận, không tự ghi dữ liệu; tính số liệu và kiểm tra an toàn do ứng dụng thực hiện).
  - `data/diagnostics/AiDiagnostics`: Nhật ký chẩn đoán an toàn (không chứa API key, prompt, câu chat hay dữ liệu sổ cái).

## 2. Cấu trúc thư mục

```text
Time_Wallet/
├── .agents/                        # Quy tắc dự án & liên kết hệ sinh thái
├── Time_Wallet_MVP/                # Tài liệu đặc tả MVP & HTML Prototype gốc
├── ai_test/                        # Script kiểm thử gateway (đọc từ biến môi trường FOZA_API_KEY)
├── docs/
│   ├── archive/                    # Hồ sơ, review & biên bản QA lịch sử (v1.0.5–v1.0.13, AI-01)
│   └── screenshots/                # Ảnh chụp giao diện minh họa trên WallyQA
├── app/
│   ├── build.gradle.kts
│   ├── schemas/                    # Lược đồ Room DB xuất tự động (bao gồm v4)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/timewallet/mvp/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── ai/GeminiProposalClient.kt
│       │   │   │   ├── diagnostics/AiDiagnostics.kt
│       │   │   │   └── local/ (Room Database, DAO, Entity, TagGoalEntities, Store, Exporter)
│       │   │   ├── domain/
│       │   │   │   ├── model/ (LedgerRecord, TimeLayer, TimeCategory, Tag, Goal...)
│       │   │   │   └── usecase/ (LedgerValidator, WallyAiGuards, TagRules, GoalRules, CoachDigest...)
│       │   │   └── ui/
│       │   │       ├── TimeWalletViewModel.kt
│       │       │   ├── WallyChatReducer.kt / WallyChatRunner.kt
│       │       │   ├── screens/ (Overview, Timer, Chat, History, Insights, PersonalScreens, Dialogs)
│       │       │   └── theme/Theme.kt
│       │   └── res/
│       └── test/java/com/timewallet/mvp/  # Bộ kiểm thử đơn vị JVM (domain, guards, migration, UI contract)
├── build.gradle.kts
├── settings.gradle.kts
├── WALLY_OWNER_CHECKLIST_V110.md   # Danh sách tự kiểm v1.1.0 Preview trên điện thoại thật
├── WALLY_V110_CLOSURE_DIGEST.md    # Tóm tắt bàn giao v1.1.0 Preview
└── CONTEXT_MANIFEST.md
```

## 3. Hướng dẫn mở và chạy trên Android Studio

1. Mở **Android Studio** → Chọn **Open** → Trỏ tới thư mục gốc của dự án `Time_Wallet`.
2. Đợi Gradle đồng bộ xong, chọn thiết bị thật hoặc máy ảo trong Android Studio và nhấn **Run (`Shift + F10`)**.
3. Để chạy bộ kiểm thử đơn vị (Unit Tests):
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```

