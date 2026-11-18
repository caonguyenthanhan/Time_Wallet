package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TaskItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Biên giới tích hợp hệ sinh thái cá nhân (Phase 3 Contract):
 * xuất tóm tắt Markdown chuẩn cho Obsidian / công cụ ghi chú cá nhân qua thao tác tường minh của người dùng,
 * tuyệt đối không đọc/xuất API key hay cấu hình bí mật.
 */
object MarkdownSummaryExporter {

    private val hmFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun exportDailyMarkdown(
        dateLocal: String,
        records: List<LedgerRecord>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        dailyReview: DailyReviewRecord?,
        efficiency: DayEfficiencyBreakdown?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val dayRecords = records
            .filter { it.date == dateLocal && !it.isCancelled && !it.isNeedsReconciliation }
            .sortedBy { it.startedAt ?: Long.MAX_VALUE }

        val sb = StringBuilder()
        sb.append("# Time Wallet · Nhật ký ngày ").append(dateLocal).append("\n\n")

        if (efficiency != null) {
            sb.append("## Phân bổ 24 giờ\n")
            sb.append("- **Mục tiêu:** ").append(LedgerAnalyticsCalculator.formatCompactDurationSeconds(efficiency.goalSeconds)).append("\n")
            sb.append("- **Phi mục tiêu:** ").append(LedgerAnalyticsCalculator.formatCompactDurationSeconds(efficiency.neutralSeconds)).append("\n")
            sb.append("- **Lãng phí (tự đánh dấu):** ").append(LedgerAnalyticsCalculator.formatCompactDurationSeconds(efficiency.wasteSeconds)).append("\n")
            sb.append("- **Chưa rõ:** ").append(LedgerAnalyticsCalculator.formatCompactDurationSeconds(efficiency.unknownSeconds)).append("\n")
            if (efficiency.untimedSeconds > 0L) {
                sb.append("- **Chưa gắn giờ (tách riêng):** ").append(LedgerAnalyticsCalculator.formatCompactDurationSeconds(efficiency.untimedSeconds)).append("\n")
            }
            sb.append("\n")
        }

        sb.append("## Hoạt động đã ghi nhận (").append(dayRecords.size).append(")\n")
        if (dayRecords.isEmpty()) {
            sb.append("- Chưa có hoạt động trong ngày.\n")
        } else {
            for (r in dayRecords) {
                val timePrefix = if (r.startedAt != null && r.endedAt != null) {
                    val s = Instant.ofEpochMilli(r.startedAt).atZone(zoneId).format(hmFmt)
                    val e = Instant.ofEpochMilli(r.endedAt).atZone(zoneId).format(hmFmt)
                    "$s–$e"
                } else {
                    "Chưa gắn giờ"
                }
                val dur = LedgerAnalyticsCalculator.formatCompactDurationSeconds(r.durationSeconds)
                val wasteTag = if (r.isMarkedWaste) " · [Lãng phí]" else ""
                sb.append("- `").append(timePrefix).append("` **").append(r.title).append("** (")
                    .append(r.category.displayName).append(" · ").append(dur).append(wasteTag).append(")\n")
            }
        }
        sb.append("\n")

        val doneToday = tasks.filter { it.isDone }
        if (doneToday.isNotEmpty()) {
            sb.append("## Công việc đã hoàn thành\n")
            for (t in doneToday) {
                sb.append("- [x] ").append(t.title).append(" (").append(t.category.displayName).append(")\n")
            }
            sb.append("\n")
        }

        val activeHabits = habits.filter { !it.isArchived }
        if (activeHabits.isNotEmpty()) {
            sb.append("## Thói quen ngày ").append(dateLocal).append("\n")
            for (h in activeHabits) {
                val done = TaskHabitEngine.isHabitCompletedOnDate(h, dateLocal, habitLogs, records)
                sb.append("- ").append(if (done) "[x] " else "[ ] ").append(h.title).append("\n")
            }
            sb.append("\n")
        }

        if (dailyReview != null && (dailyReview.summaryText.isNotBlank() || dailyReview.generalNote.isNotBlank())) {
            sb.append("## Nhìn lại ngày\n")
            if (dailyReview.summaryText.isNotBlank()) sb.append(dailyReview.summaryText.trim()).append("\n")
            if (dailyReview.generalNote.isNotBlank()) sb.append("\n> ").append(dailyReview.generalNote.trim()).append("\n")
        }
        return sb.toString()
    }
}
