package com.timewallet.mvp.domain.model

/**
 * v1.1.0 — Tag cá nhân và Mục tiêu.
 *
 * Category (9 nhóm mặc định) là nhóm tổng quát; Tag là hoạt động cụ thể của người dùng ("Chạy bộ", "Đọc sách", "Chơi game").
 * Bản ghi trỏ tới Tag bằng `tagId` nên đổi tên / đổi Category / lưu trữ Tag không làm mất lịch sử.
 * Bản ghi luôn giữ `category` của thời điểm ghi nhận (ảnh chụp): đổi Category của Tag chỉ áp cho bản ghi MỚI.
 */
data class Tag(
    val id: String,
    val name: String,
    /** Tên chuẩn hóa (bỏ dấu, thường, gọn khoảng trắng) để chống trùng và tìm kiếm. */
    val nameKey: String,
    val category: TimeCategory,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isArchived: Boolean get() = archivedAt != null
}

enum class GoalTargetType(val storageKey: String) {
    CATEGORY("CATEGORY"),
    TAG("TAG");

    companion object {
        fun fromStorage(raw: String?): GoalTargetType = entries.firstOrNull { it.storageKey == raw?.trim()?.uppercase() } ?: CATEGORY
    }
}

/** MIN = "ít nhất" (mục tiêu cần đạt), MAX = "tối đa" (giới hạn). */
enum class GoalKind(val storageKey: String) {
    MIN("MIN"),
    MAX("MAX");

    companion object {
        fun fromStorage(raw: String?): GoalKind = entries.firstOrNull { it.storageKey == raw?.trim()?.uppercase() } ?: MIN
    }
}

enum class GoalPeriod(val storageKey: String) {
    DAY("DAY"),
    WEEK("WEEK");

    companion object {
        fun fromStorage(raw: String?): GoalPeriod = entries.firstOrNull { it.storageKey == raw?.trim()?.uppercase() } ?: DAY
    }
}

/** Trạng thái đánh giá của MỘT phiên bản mục tiêu. Trạng thái "đã kết thúc" của cả mục tiêu được suy ra (xem [GoalRules.groupState]). */
enum class GoalVersionStatus(val storageKey: String) {
    ACTIVE("ACTIVE"),
    PAUSED("PAUSED");

    companion object {
        fun fromStorage(raw: String?): GoalVersionStatus = entries.firstOrNull { it.storageKey == raw?.trim()?.uppercase() } ?: ACTIVE
    }
}

enum class GoalGroupState { ACTIVE, PAUSED, ENDED }

/**
 * Một PHIÊN BẢN của mục tiêu. Sửa / tạm dừng / tiếp tục / kết thúc = đóng phiên bản đang mở ([validTo]) và (trừ khi kết thúc) mở phiên bản mới
 * cùng [groupId], nên tiến độ quá khứ luôn được tính theo mục tiêu có hiệu lực lúc đó.
 * [validFrom]/[validTo] là ngày địa phương YYYY-MM-DD, đều bao gồm hai đầu; [validTo] = null nghĩa là đang mở.
 */
data class Goal(
    val id: String,
    val groupId: String,
    val targetType: GoalTargetType,
    /** `TimeCategory.key` khi [targetType] = CATEGORY; `Tag.id` khi TAG. */
    val targetKey: String,
    val kind: GoalKind,
    val period: GoalPeriod,
    val targetSeconds: Long,
    val status: GoalVersionStatus,
    val validFrom: String,
    val validTo: String?,
    val createdAt: Long,
    val updatedAt: Long
)
