package com.timewallet.mvp.domain.model

import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import kotlin.math.max

/**
 * Chu kỳ ngân sách thời gian do người dùng tự đặt (Theo ngày hoặc Theo tuần).
 */
enum class BudgetPeriod(
    val storageKey: String,
    val labelVi: String,
    val shortSuffixVi: String,
    val maxTargetMinutes: Int
) {
    DAILY(
        storageKey = "DAILY",
        labelVi = "Theo ngày",
        shortSuffixVi = "/ngày",
        maxTargetMinutes = 24 * 60
    ),
    WEEKLY(
        storageKey = "WEEKLY",
        labelVi = "Theo tuần",
        shortSuffixVi = "/tuần",
        maxTargetMinutes = 7 * 24 * 60
    );

    companion object {
        fun fromStorageString(raw: String?): BudgetPeriod {
            return when (raw?.trim()?.uppercase()) {
                "DAILY" -> DAILY
                else -> WEEKLY
            }
        }
    }
}

/**
 * Cấu hình ngân sách thời gian cho 1 danh mục cụ thể.
 * Hệ thống KHÔNG tự đặt mục tiêu thay người dùng; chỉ lưu khi người dùng chủ động tạo/sửa.
 */
data class CategoryTimeBudget(
    val category: TimeCategory,
    val period: BudgetPeriod,
    val targetSeconds: Long,
    val isPriority: Boolean = true,
    val priorityOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val targetMinutes: Int
        get() = ((targetSeconds + 30L) / 60L).toInt().coerceAtLeast(1)
}

/**
 * Kết quả đối chiếu mức sử dụng thực tế với ngân sách mục tiêu.
 *
 * WALLY v1.0.6 (F-V105-02) — MỘT định nghĩa duy nhất cho mọi màn hình (Tổng quan, Insights, phản tư, Nhật ký ngày, Wally):
 *   **Đã ghi nhận = thời gian thực tế có mốc giờ + chưa gắn giờ (không gồm PLANNED)** = [combinedUsedSeconds].
 *   Mọi chỗ hiển thị mức ngân sách phải dùng nhóm `combined*` / [recordedSummaryVi].
 *
 * - usedSeconds: CHỈ phần có mốc giờ (MEASURED, REPORTED_TIMED, phần đã xác nhận của ACTIVE). Giữ nguyên cho trục giờ 24h và
 *   các test cũ; KHÔNG dùng để nói "đã ghi nhận" hay "còn lại" với người dùng.
 * - untimedNoteSeconds: thời lượng tự khai báo chưa gắn giờ (REPORTED_UNTIMED); đã nằm trong "đã ghi nhận".
 * - plannedSeconds: thời lượng dự kiến (PLANNED + phần chưa xác nhận của ACTIVE), luôn tách riêng, không tính.
 */
data class TimeBudgetProgress(
    val budget: CategoryTimeBudget,
    val periodStartDateLocal: String,
    val periodEndDateLocal: String,
    val usedSeconds: Long,
    val targetSeconds: Long,
    val remainingSeconds: Long,
    val exceededSeconds: Long,
    val deltaSeconds: Long,
    val untimedNoteSeconds: Long = 0L,
    val plannedSeconds: Long = 0L
) {
    val category: TimeCategory
        get() = budget.category

    val period: BudgetPeriod
        get() = budget.period

    val isExceeded: Boolean
        get() = usedSeconds > targetSeconds

    val progressFraction: Float
        get() = (usedSeconds.toFloat() / max(1L, targetSeconds).toFloat()).coerceIn(0f, 1f)

    // WALLY: tiến độ ngân sách GỒM thời lượng chưa gắn giờ (nhập thủ công / chat). `usedSeconds` giữ nguyên
    // (chỉ bản ghi có mốc giờ) để các chỉ số trục giờ và test cũ không đổi.
    val combinedUsedSeconds: Long
        get() = usedSeconds + untimedNoteSeconds

    val combinedRemainingSeconds: Long
        get() = (targetSeconds - combinedUsedSeconds).coerceAtLeast(0L)

    val combinedExceededSeconds: Long
        get() = (combinedUsedSeconds - targetSeconds).coerceAtLeast(0L)

    val combinedIsExceeded: Boolean
        get() = combinedUsedSeconds > targetSeconds

    val combinedProgressFraction: Float
        get() = (combinedUsedSeconds.toFloat() / max(1L, targetSeconds).toFloat()).coerceIn(0f, 1f)

    val combinedUsedFormatted: String
        get() = if (combinedUsedSeconds == 0L) "0p" else LedgerAnalyticsCalculator.formatCompactDurationSeconds(combinedUsedSeconds)

    val combinedDifferenceLabelVi: String
        get() = when {
            combinedRemainingSeconds > 0L -> "Còn ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(combinedRemainingSeconds)}"
            combinedExceededSeconds > 0L -> "Vượt ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(combinedExceededSeconds)}"
            else -> "Còn 0p (Đủ mục tiêu)"
        }

    val usedFormatted: String
        get() = if (usedSeconds == 0L) "0p" else LedgerAnalyticsCalculator.formatCompactDurationSeconds(usedSeconds)

    /** Chuỗi dùng chung cho mọi nơi nêu mức đã ghi nhận: "1h30" hoặc "1h30 (1h có mốc giờ + 30p chưa gắn giờ)". */
    val recordedSummaryVi: String
        get() = if (untimedNoteSeconds > 0L) {
            "$combinedUsedFormatted ($usedFormatted có mốc giờ + ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(untimedNoteSeconds)} chưa gắn giờ)"
        } else combinedUsedFormatted

    val targetFormatted: String
        get() = "${LedgerAnalyticsCalculator.formatCompactDurationSeconds(targetSeconds)}${budget.period.shortSuffixVi}"

    val differenceLabelVi: String
        get() = when {
            remainingSeconds > 0L -> "Còn ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(remainingSeconds)}"
            exceededSeconds > 0L -> "Vượt ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(exceededSeconds)}"
            else -> "Còn 0p (Đủ mục tiêu)"
        }
}

