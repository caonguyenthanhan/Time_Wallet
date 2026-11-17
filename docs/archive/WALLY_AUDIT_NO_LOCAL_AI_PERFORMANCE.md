# Kiểm tra nhanh — Time Wallet KHÔNG chạy/tải AI local; nguyên nhân có thể gây lag

**Ngày:** 2026-11-14 · **Phạm vi:** chỉ đọc (APK, dependency, manifest, mã nguồn). Không thêm tính năng, không đổi kiến trúc, **không build bản mới vì cuộc kiểm tra này** (không phát hiện lỗi thực tế). Không emulator/ADB, không gọi API thật.
**APK đã quét:** v1.0.7 `3df0fee71e5a71481f353beb010d6984f3be45c1737329126eaad45be0c1dc61` (bản dựng đã đóng gói gần nhất lúc nhận yêu cầu) **và** v1.0.8 `e13c35f49ae474a57c79c5be269307cb8e5f7d3a410e75528a9e553683cd62a5` (bản dựng ngay sau đó theo chỉ thị khác). Hai bản có cùng khối `dependencies`, không thêm thư viện hay tài nguyên.
Bằng chứng thô: `out/wally_evidence_audit_nolocalai/` (`apk_scan_v1.0.7.txt`, `apk_scan_v1.0.8.txt`, `gradle_debugRuntimeClasspath.txt`, `gradle_releaseRuntimeClasspath.txt`, `merged_manifest_debug_v1.0.8.xml`).

## Kết luận

| Câu hỏi | Kết luận | Căn cứ |
|---|---|---|
| Có model / runtime AI local trong APK? | **`LOCAL_MODEL_ABSENT`** | Mục 1 và 2 |
| Có tải model, khởi tạo model hoặc suy luận local? | **`MODEL_DOWNLOAD_ABSENT`** (không có tải model, không khởi tạo model, không suy luận local) | Mục 3 |
| Wally chỉ dùng Cloud API? | **Đúng.** Parser Kotlin chỉ là guardrail tất định | Mục 4 |
| Mở và dùng được khi không có khóa API và không có mạng? | **Đúng theo đọc mã và test JVM**; chưa kiểm trên máy thật | Mục 5 |
| Có thể gây lag? | Có một số rủi ro đo-bằng-đọc-mã, **chưa đo trên thiết bị** | Mục 6 |

Giới hạn: kết quả trên thiết bị thật **NOT_TESTED**. Test JVM không chứng minh hiệu năng hay hành vi trên máy.

## 1. Nội dung APK (quét từng mục trong tệp)

| Hạng mục | v1.0.7 | v1.0.8 |
|---|---|---|
| Số mục / dung lượng giải nén / nén | 122 / 27,89 MB / 11,73 MB | 122 / 27,90 MB / 11,73 MB |
| Tệp có đuôi mô hình (`.gguf .ggml .onnx .tflite .pt .pb .safetensors .task .litertlm .mnn .ncnn .bin .model`…) hoặc tên runtime AI (`llama, gguf, onnx, tflite, tensorflow, litert, mediapipe, mlkit, torch, whisper, ncnn, mnn, executorch, genai, gemma…`) | **0 mô hình.** Duy nhất khớp mẫu `.bin`: `DebugProbesKt.bin` (1.738 byte, đầu tệp `CAFEBABE` = **lớp Java** của kotlinx-coroutines debug probes, không phải mô hình) | như v1.0.7 |
| `assets/` | **không có** | không có |
| `res/raw/` | **không có** | không có |
| Thư viện native (`lib/**/*.so`) | chỉ `libandroidx.graphics.path.so` × 4 ABI (≈ 37 KB tổng): thư viện đường vẽ của Jetpack Compose | như v1.0.7 |
| Dex (9 tệp, ≈ 26,0 MB) — dấu vết `org/tensorflow`, `ai/onnxruntime`, `com/google/mlkit`, `com/google/mediapipe`, `com/google/ai/edge`, `org/pytorch`, `llama`, `ggml`, `gguf`, `firebase/ml`, `androidx/work`, `androidx/camera` | **không có** | không có |
| Dấu vết `DownloadManager` | 1 lần trong `classes9.dex`: là **mô tả kiểu của lớp khung Android** (nằm giữa `AlarmManager`, `Dialog`, `Fragment`…) do thư viện androidx tham chiếu; **không có đoạn mã nào của app** gọi (`grep` toàn bộ `app/src/main` = 0) | như v1.0.7 |
| Mục lớn nhất | `classes.dex` 18,1 MB, `classes9.dex` 5,9 MB (Compose/Material/Room, bản debug **không** thu nhỏ), `res/drawable-nodpi-v4/app_logo.png` 1,36 MB | như v1.0.7 |

## 2. Dependency (bằng chứng Gradle)

- Khai báo (`app/build.gradle.kts`, `gradle/libs.versions.toml`): chỉ **AndroidX** (core-ktx, lifecycle, activity-compose, Compose BOM + ui/graphics/material3, Room) + `ui-tooling` **chỉ cho debug**; test: junit, sqlite-jdbc, org.json.
- Classpath giải quyết đầy đủ (`gradlew :app:dependencies --configuration debugRuntimeClasspath`): **không có** tensorflow, onnxruntime, litert, mlkit, mediapipe, llama, pytorch, ggml, genai, firebase, play-services, work-runtime, camera, okhttp, retrofit, ktor, volley, coil, glide (`grep` = 0). Thư viện bên thứ ba duy nhất ngoài AndroidX: kotlin-stdlib, kotlinx-coroutines, `com.google.guava:listenablefuture`.
- `ui-tooling` (kèm `PreviewActivity`) chỉ có trong classpath debug; classpath release chỉ còn `ui-tooling-preview` (chú thích).

## 3. Điểm vào mạng, khởi động, tác vụ nền

- **Quyền:** chỉ `INTERNET` (và quyền nội bộ `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` do androidx sinh). Không `FOREGROUND_SERVICE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`.
- **Thành phần trong manifest gộp:** `MainActivity`; (debug) `PreviewActivity`; `androidx.startup.InitializationProvider` (EmojiCompat, ProcessLifecycle, ProfileInstaller); `ProfileInstallReceiver`; `room.MultiInstanceInvalidationService`. **Không có** Service/Receiver/Worker/Alarm của app. Tất cả thành phần còn lại là của thư viện chuẩn.
- **Điểm vào mạng duy nhất trong mã:** `GeminiProposalClient` (`HttpURLConnection`, `URL`). Chỉ có **hai** nơi gọi: `analyzeInput` (khi người dùng bấm gửi ở tab Wally) và `testConnection` (khi bấm "Kiểm tra kết nối"). `TimeWalletApp.onCreate` chỉ cài bộ ghi nhật ký lỗi; `ViewModel.init` chỉ đọc cấu hình, khôi phục timer và đăng ký 4 luồng Room. **Không có** gọi mạng, tải tệp, tải model hay khởi tạo model khi khởi động hoặc nền.
- `grep` toàn bộ `app/src/main`: `tflite|onnx|gguf|llama|ggml|loadModel|DownloadManager|WorkManager|AlarmManager|startForegroundService|Interpreter|assets.open` = **0**. Chuỗi "model" trong mã chỉ là **tên model của máy chủ AI** (`modelName`, mặc định `cinb/gemini-3.8-flash`).

## 4. Wally chỉ dùng Cloud API

- Luồng một câu: `analyzeChatInput` → (chốt khóa/opt-in) → `WallyChatRunner` → `GeminiProposalClient.analyzeInput` → máy chủ AI từ xa. Không có đường định tuyến cục bộ, không chatbot cục bộ, không fallback cục bộ khi lỗi (lỗi ⇒ báo lỗi, giữ input).
- Parser Kotlin chỉ làm **guardrail tất định**: `VietnameseChatParser.parse` chỉ được gọi **một chỗ** — bên trong `AiProposalGuard` để kiểm đề xuất do AI trả; `QueryIntentParser`/`ChatRouter` không được ViewModel gọi (chỉ có test hồi quy). `LedgerQueryEngine` chỉ cộng số từ sổ cái trên máy (không gửi sổ cái lên cloud).

## 5. Không có khóa API và không có mạng

- `analyzeChatInput`: nếu `!optInEnabled || apiKey.isBlank()` ⇒ báo "chưa cấu hình AI" và **return trước khi tạo bất kỳ kết nối nào**; không tự phân tích cục bộ.
- Nạp cấu hình khi chưa có khóa: không có bản mã ⇒ **không truy cập Keystore** (ngoại lệ duy nhất: di cư một lần khóa plaintext của bản v1.0.0 cũ nếu còn). APK không có `assets/` nên **không có baseline profile** của app.
- `saveManualEntry`, `startTimer`, `stopAndSaveTimer`, `saveCategoryBudget`: **0 tham chiếu** `aiPreferences/apiKey/GeminiProposalClient` (đọc mã + test tĩnh `offline features do not depend on the API key`). Timer, Nhật ký, Ngân sách, Tổng quan chỉ dùng Room/SharedPreferences.
- Không có tác vụ nền gọi API; khi mất mạng chỉ lần gửi ở tab Wally (do người dùng bấm) trả lỗi OFFLINE/TIMEOUT.
- **Chưa kiểm trên máy:** mở app không khóa, không mạng, chế độ máy bay → bước tương ứng trong checklist Owner.

## 6. Rủi ro gây lag (đọc mã; chưa đo)

| # | Rủi ro | Căn cứ | Mức |
|---|---|---|---|
| 1 | **Nhật ký không lazy:** không có `LazyColumn/LazyRow` ở bất kỳ màn hình nào; toàn bộ nội dung mỗi tab nằm trong một `Column(verticalScroll)`; `HistoryScreen` dựng **mọi** bản ghi đã lọc bằng `forEach` | `grep` Lazy = 0; `HistoryScreen.kt:152-182` | Cao khi sổ cái lớn (hàng nghìn bản ghi): mở tab Nhật ký dựng toàn bộ một lượt. Đã ghi là backlog; chưa có số đo |
| 2 | **Tải cả sổ cái vào RAM, đổi mỗi lần có thay đổi:** `observeAllRecords()` trả toàn bộ bảng; `entities.map { toDomain() }` chạy trong `viewModelScope` (**luồng chính**) ở mỗi lần phát | `TimeWalletViewModel.kt` (khối `init`) | Trung bình, O(n) trên Main mỗi lần ghi/sửa/xóa |
| 3 | **Khởi động nguội:** `init` đọc SharedPreferences và, **chỉ khi đã lưu khóa**, giải mã bằng AndroidKeyStore trên luồng chính (có thể chậm vài chục–vài trăm ms ở vài máy) | `loadAiPreferences()` trong `init` | Thấp–trung bình, một lần; không xảy ra khi chưa có khóa |
| 4 | **Vòng 1 giây khi màn hình hiện và đồng hồ đang chạy:** mỗi giây cập nhật `UiState`, gốc `TimeWalletAppRoot` đọc toàn bộ state ⇒ tái dựng gốc mỗi giây (hiệu quả phụ thuộc cơ chế bỏ qua của Compose) | `refreshTimerTick`, `MainActivity` | Thấp–trung bình; chỉ khi có phiên đo. Vòng **dừng khi app rời màn hình** (`onAppPaused`) |
| 5 | **Tính lại số liệu mỗi 15 giây khi màn hình hiện:** tính ngoài luồng chính (`Dispatchers.Default`, lượt mới nhất thắng, v1.0.6) nhưng O(7·n) trên toàn bộ sổ cái và chạy lặp | `recomputeStats`, `DerivedStatsCalculator` | Thấp với n nhỏ; tăng theo n |
| 6 | **Bản debug, không R8:** candidate là APK debug (kèm `ui-tooling`); bản release cũng đặt `isMinifyEnabled = false` ⇒ dex ≈ 26,0 MB không thu nhỏ, nạp lớp và biên dịch ART lần đầu chậm hơn | `app/build.gradle.kts` | Ảnh hưởng độ trễ mở app và bộ nhớ; **không nên đánh giá hiệu năng bằng APK debug** |
| 7 | `app_logo.png` 1254×1254 (1,36 MB) nằm trong `res/drawable-nodpi` nhưng **không được tham chiếu** ở mã hay XML | `grep app_logo` = 0 | Chỉ tăng dung lượng APK, không tốn bộ nhớ lúc chạy |
| 8 | Khởi tạo thư viện lúc mở app: EmojiCompat, ProcessLifecycle, ProfileInstaller (không có baseline profile của app) | `InitializationProvider` | Thấp, chuẩn của Compose |

**Không có** trong danh sách nguyên nhân: AI/model local, tải model, tác vụ nền định kỳ, dịch vụ nền, tải nặng trên Main do truy vấn thống kê (đã ra khỏi Main từ v1.0.6) hay vòng đồng hồ chạy khi app ở nền.

## 7. Điều chưa biết

Chưa có số đo thật: thời gian mở nguội, dropped frames, bộ nhớ, tiêu thụ pin, và thử với sổ cái lớn đều **NOT_TESTED**. Muốn xác minh lag trên máy cần: bản release thu nhỏ hoặc ít nhất bản debug đo riêng, sổ cái thử vài nghìn bản ghi, đo mở tab Nhật ký, và đo khi đồng hồ chạy 10 phút ở tab Nhật ký. Không có việc nào cần sửa mã chỉ vì cuộc kiểm tra này.
