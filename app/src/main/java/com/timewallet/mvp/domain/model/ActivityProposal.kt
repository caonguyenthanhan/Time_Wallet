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
    val isFuturePlanned: Boolean = false
) {
    val durationMinutes: Int
        get() = ((durationSeconds + 30L) / 60L).toInt().coerceAtLeast(1)
}

