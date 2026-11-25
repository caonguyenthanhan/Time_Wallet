package com.timewallet.mvp.domain.model

/**
 * Đề xuất bản ghi từ AI/Rule-based Chat theo cấu trúc JSON mục 5 của Time_Wallet_MVP_Spec.md.
 * AI KHÔNG được cập nhật cơ sở dữ liệu trực tiếp mà chỉ tạo ActivityProposal chờ người dùng xác nhận.
 */
data class ActivityProposal(
    val id: String,
    val rawInput: String,
    val title: String,
    val category: String,
    val durationSeconds: Long,
    val dateLocal: String, // YYYY-MM-DD
    val startLocal: String?, // HH:mm or null
    val endLocal: String?,   // HH:mm or null
    val timeEvidence: String, // "explicit_range" | "duration_only"
    val missingFields: List<String>,
    val needsConfirmation: Boolean = true,
    val isFuturePlanned: Boolean = false,
    val tagId: String? = null,
    val tagName: String? = null,
    val taskId: String? = null
) {
    val durationMinutes: Int
        get() = ((durationSeconds + 30L) / 60L).toInt().coerceAtLeast(1)
}

/** Ý định thô từ Cloud AI khi người dùng muốn gắn/đổi/bỏ Tag cho một phiên cũ đã lưu. */
data class RawTagAssignmentIntent(
    val recordTitle: String? = null,
    val dateLocal: String? = null,
    val startHm: String? = null,
    val tagName: String? = null
)

/**
 * Đề xuất cập nhật Tag cho một bản ghi đã lưu — luôn chờ người dùng duyệt, không bao giờ tự ghi.
 */
data class TagAssignmentProposal(
    val id: String,
    val recordId: String,
    val recordTitle: String,
    val recordDateLocal: String,
    val recordTimeLabel: String,
    val recordDurationSeconds: Long,
    val previousTagId: String?,
    val previousTagName: String?,
    val previousCategoryKey: String,
    val newTagId: String?,
    val newTagName: String?,
    val newCategoryKey: String,
    val affectedPeriodStart: String,
    val affectedPeriodEnd: String,
    val affectedPeriodLabel: String,
    val needsConfirmation: Boolean = true
)

/** Token lưu trạng thái trước/sau để người dùng bấm Hoàn tác ngay trong Chat sau khi duyệt gắn Tag. */
data class TagAssignmentUndoToken(
    val actionId: String,
    val recordId: String,
    val recordTitle: String,
    val previousTagId: String?,
    val previousTagName: String?,
    val previousCategoryKey: String,
    val appliedTagId: String?,
    val appliedTagName: String?,
    val appliedCategoryKey: String,
    val affectedPeriodStart: String,
    val affectedPeriodEnd: String,
    val affectedPeriodLabel: String,
    val undone: Boolean = false
)

