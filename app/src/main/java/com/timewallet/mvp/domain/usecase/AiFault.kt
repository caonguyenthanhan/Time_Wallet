package com.timewallet.mvp.domain.usecase

/**
 * Nguồn của kết quả (WALLY API-FIRST):
 * - CLOUD: đã gọi Cloud AI (kể cả khi lỗi);
 * - LOCAL: chỉ lệnh điều khiển rõ ràng xử lý cục bộ (ví dụ "Hủy tất cả đề xuất"), không giả làm câu trả lời AI;
 * - NOT_CONFIGURED: chưa có khóa/chưa bật AI, không gọi mạng và KHÔNG tự phân tích cục bộ.
 */
enum class OutcomeSource { LOCAL, CLOUD, NOT_CONFIGURED }

/** Lớp lỗi khi gọi AI. Thông điệp ngắn, tiếng Việt, không chứa nội dung riêng tư hay khóa. */
enum class AiFault(val messageVi: String, val retryable: Boolean) {
    OFFLINE("Không kết nối được mạng hoặc máy chủ AI. Bạn vẫn có thể Ghi thủ công.", true),
    TLS("Kết nối bảo mật (TLS) với máy chủ AI thất bại. Kiểm tra giờ hệ thống và Base URL.", false),
    TIMEOUT("Máy chủ AI phản hồi quá chậm.", true),
    AUTH("Máy chủ từ chối khóa API (401/403). Kiểm tra khóa và quyền truy cập.", false),
    NOT_FOUND("Không tìm thấy endpoint hoặc model (404). Kiểm tra Base URL và tên model.", false),
    RATE_LIMIT("Máy chủ AI đang giới hạn tốc độ (429).", true),
    SERVER("Máy chủ AI đang gặp lỗi (5xx).", true),
    MALFORMED("AI trả về dữ liệu không hợp lệ nên không có đề xuất nào từ AI được dùng.", false),
    CANCELLED("Đã hủy phân tích.", false),
    UNKNOWN("Không thể kết nối máy chủ AI. Hãy thử lại hoặc Ghi thủ công.", false),
    NOT_CONFIGURED("Wally cần khóa API và bật AI để hiểu và trả lời. Mở ⚙ để nhập khóa. Bạn vẫn có thể Ghi thủ công.", false),
    CONFIG("Cấu hình AI chưa hợp lệ: Base URL phải bắt đầu bằng https:// và khớp host của khóa. Mở ⚙ để kiểm tra.", false),
    KEY_INVALID("Khóa API có ký tự không hợp lệ (thường do dán thừa dấu cách, xuống dòng hoặc dấu gạch lạ). Mở ⚙ và dán lại khóa.", false),
    GUARD_UNAVAILABLE("Bộ kiểm tra an toàn của Wally không khởi tạo được trên máy này, nên chưa gửi gì lên AI và chưa tạo đề xuất nào. Bạn vẫn có thể Ghi thủ công.", false)
}

/** Trạng thái bộ nhớ đệm prompt suy ra từ trường `usage` mà nhà cung cấp trả về. KHÔNG đoán khi nhà cung cấp không báo. */
enum class CacheStatus {
    /** Phản hồi không có đối tượng `usage`. */
    NO_USAGE,

    /** Có `usage` nhưng không có trường token đệm: nhà cung cấp không báo (không chứng minh là không hỗ trợ). */
    NOT_REPORTED,

    /** Nhà cung cấp báo 0 token đệm. */
    MISS,

    /** Nhà cung cấp báo > 0 token đệm. */
    HIT
}

/** Số token do nhà cung cấp báo (null = không báo). Không chứa nội dung. */
data class AiUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val cachedTokens: Int?,
    val cacheStatus: CacheStatus
)

/** Phân loại kết quả của một lượt Wally (dùng cho số liệu quan sát và giao diện). */
enum class WallyIntentKind { RECORD, QUERY, MIXED, CHAT, CLARIFY, BLOCKED, CONTROL, NOT_CONFIGURED, ERROR, COACH }

object WallyIntentClassifier {
    /**
     * @param guardBlocked true nếu AI có đề xuất nhưng chính sách (phủ định/hủy/sửa lại/lệch thời lượng) đã bỏ hết.
     */
    fun classify(
        source: OutcomeSource,
        fault: AiFault?,
        proposalCount: Int,
        queryCount: Int,
        hasReply: Boolean,
        guardBlocked: Boolean
    ): WallyIntentKind = when {
        source == OutcomeSource.LOCAL -> WallyIntentKind.CONTROL
        source == OutcomeSource.NOT_CONFIGURED -> WallyIntentKind.NOT_CONFIGURED
        fault != null -> WallyIntentKind.ERROR
        guardBlocked -> WallyIntentKind.BLOCKED
        proposalCount > 0 && queryCount > 0 -> WallyIntentKind.MIXED
        proposalCount > 0 -> WallyIntentKind.RECORD
        queryCount > 0 -> WallyIntentKind.QUERY
        hasReply -> WallyIntentKind.CHAT
        else -> WallyIntentKind.CLARIFY
    }
}
