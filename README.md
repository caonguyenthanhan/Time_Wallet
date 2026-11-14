# Time Wallet — Mobile App (Android Kotlin + Jetpack Compose)

> **Tuyên ngôn:** Thời gian là tài sản hữu hạn và không thể tích lũy. Quản lý thời gian để hiểu và chủ động phân bổ, không để phán xét nghỉ ngơi hay tối đa hóa thời gian làm việc.

Ứng dụng di động **Time Wallet** được xây dựng theo đặc tả [Time_Wallet_MVP_Spec.md](Time_Wallet_MVP/Time_Wallet_MVP_Spec.md) và thiết kế giao diện mẫu [Time_Wallet_Prototype.html](Time_Wallet_MVP/Time_Wallet_Prototype.html).

## 1. Kiến trúc & Công nghệ

- **Nền tảng:** Android Native (`minSdk 26`, `targetSdk 35`, Kotlin `2.0.21`, Jetpack Compose Material 3).
- **Cơ sở dữ liệu cục bộ (Offline-first):** Room SQLite (`time_wallet_ledger.db`) với ràng buộc `@Transaction` chống chồng lấn mốc giờ (`overlap`) và khóa chống lưu trùng (`idempotencyKey`).
- **Tách tầng Domain rõ ràng:**
  - `domain/model/`: `LedgerRecord`, `TimeLayer` (`MEASURED`, `REPORTED_TIMED`, `REPORTED_UNPLACED`, `PLANNED`), `TimeCategory`, `ActivityProposal`, `ActiveTimerSession`.
  - `domain/usecase/`:
    - `LedgerValidator`: Kiểm tra đầy đủ các bất biến sổ cái (cặp `startedAt`/`endedAt`, chống chồng lấn thời gian, chặn ngày tương lai vào dữ liệu thực tế, kiểm tra danh mục hợp lệ).
    - `LedgerAnalyticsCalculator`: Thống kê ngày 24h và biểu đồ tuần 7 ngày theo múi giờ thực tế (hỗ trợ cả ngày chuyển DST), tách bạch phần có mốc giờ (`timedSeconds`), phần chỉ có thời lượng (`unplacedSeconds`) và phần chưa ghi nhận (`untrackedSeconds`).
    - `VietnameseChatParser`: Bộ phân tích ngôn ngữ tự nhiên tiếng Việt theo quy tắc (100% Offline), hỗ trợ tách nhiều hoạt động, nhận diện khung giờ và thời lượng mà không tự bịa mốc giờ còn thiếu.
  - `data/ai/GeminiProposalClient`: Tích hợp Gemini API theo cơ chế **Opt-in** (tùy chọn nhập API Key cá nhân, không gửi lịch sử nhật ký lên AI, tự động fallback về parser cục bộ khi offline).

## 2. Cấu trúc thư mục

```text
Time_Wallet/
├── .agents/                        # Rules dự án & liên kết SSOT An-tool-ecosystem
├── Time_Wallet_MVP/                # Tài liệu đặc tả MVP & HTML Prototype gốc
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/timewallet/mvp/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── ai/GeminiProposalClient.kt
│       │   │   │   └── local/ (Room Database, DAO, Entity, Preferences, JSON Exporter)
│       │   │   ├── domain/
│       │   │   │   ├── model/ (LedgerRecord, TimeLayer, TimeCategory, ActivityProposal...)
│       │   │   │   └── usecase/ (LedgerValidator, LedgerAnalyticsCalculator, VietnameseChatParser)
│       │   │   └── ui/
│       │   │       ├── TimeWalletViewModel.kt
│       │   │       ├── screens/ (Overview, Timer, Chat, History, Insights, Dialogs)
│       │   │       └── theme/Theme.kt
│       │   └── res/
│       └── test/java/com/timewallet/mvp/TimeWalletDomainTest.kt
├── build.gradle.kts
├── settings.gradle.kts
└── CONTEXT_MANIFEST.md
```

## 3. Hướng dẫn mở và chạy trên Android Studio

1. Mở **Android Studio** → Chọn **Open** → Trỏ tới thư mục gốc `d:\desktop\Time_Wallet`.
2. Đợi Gradle đồng bộ xong, chọn thiết bị thật hoặc máy ảo trong Android Studio và nhấn **Run (`Shift + F10`)**.
3. Để chạy bộ kiểm thử đơn vị (Unit Tests) cho tầng Domain:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```

