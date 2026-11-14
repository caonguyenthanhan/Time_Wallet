package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.TimeCategory
import kotlin.math.roundToInt

/** Một phần của donut: tỷ lệ tính trên TỔNG ĐÃ GHI NHẬN (không phải trên 24h). */
data class DonutSlice(
    val category: TimeCategory,
    val seconds: Long,
    val fraction: Float
) {
    val percent: Int get() = (fraction * 100f).roundToInt()
}

/**
 * WALLY P1: số liệu hero + donut của Dashboard. Hàm thuần, kiểm thử được.
 * - recorded = có mốc giờ + chưa gắn giờ (tổng theo NGÀY có ngày hợp lệ). Dự kiến KHÔNG được tính.
 * - unrecorded = max(0, 24h của ngày − recorded): phần chưa ghi nhận theo quy ước, KHÔNG phải thời gian rảnh,
 *   không bao giờ âm; ngày 23h/25h (DST) dùng `calendarDaySeconds` có sẵn.
 * - Trục giờ (24h axis) vẫn chỉ dùng thời gian có mốc giờ; donut/hero không phải trục giờ nên được gộp chưa gắn giờ.
 */
data class DashboardSummary(
    val calendarDaySeconds: Long,
    val recordedSeconds: Long,
    val timedSeconds: Long,
    val untimedSeconds: Long,
    val plannedSeconds: Long,
    val unrecordedSeconds: Long,
    val progressFraction: Float,
    val recordedExceedsDay: Boolean,
    val slices: List<DonutSlice>
) {
    val isEmpty: Boolean get() = recordedSeconds <= 0L

    companion object {
        fun from(stats: DayLedgerStats): DashboardSummary {
            val day = stats.calendarDaySeconds.coerceAtLeast(1L)
            val timed = stats.timedSeconds.coerceAtLeast(0L)
            val untimed = stats.untimedSeconds.coerceAtLeast(0L)
            val recorded = timed + untimed

            val perCategory = linkedMapOf<TimeCategory, Long>()
            (stats.categoryBreakdown + stats.untimedCategoryBreakdown).forEach {
                if (it.durationSeconds > 0L) perCategory[it.category] = (perCategory[it.category] ?: 0L) + it.durationSeconds
            }
            val categoryTotal = perCategory.values.sum()
            val slices = if (categoryTotal <= 0L) {
                emptyList()
            } else {
                perCategory.entries
                    .sortedByDescending { it.value }
                    .map { DonutSlice(it.key, it.value, it.value.toFloat() / categoryTotal.toFloat()) }
            }

            return DashboardSummary(
                calendarDaySeconds = day,
                recordedSeconds = recorded,
                timedSeconds = timed,
                untimedSeconds = untimed,
                plannedSeconds = stats.plannedSeconds.coerceAtLeast(0L),
                unrecordedSeconds = (day - recorded).coerceAtLeast(0L),
                progressFraction = (recorded.toFloat() / day.toFloat()).coerceIn(0f, 1f),
                recordedExceedsDay = recorded > day,
                slices = slices
            )
        }
    }
}
