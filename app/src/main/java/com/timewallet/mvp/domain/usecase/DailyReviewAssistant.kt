package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.DailyReviewSnapshot
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Bộ tổng hợp & trợ lý AI phản tư cho Daily Review (Refinement 03):
 * - Chỉ sử dụng dữ liệu thật từ Sổ cái (Ledger) và Ngân sách (Time Budget) của ngày được chọn.
 * - Tuyệt đối không cộng PLANNED vào thời gian thực tế.
 * - Không tự chấm điểm năng suất, không tự bịa kết quả hoạt động, không tự đổi ngân sách.
 * - Tạo bản tổng kết ngắn và tối đa 2 câu hỏi phản tư từ dữ liệu thật.
 * - Với hoạt động Học tập (LEARN), cung cấp câu hỏi Feynman chuẩn:
 *   “Bạn sẽ giải thích điều vừa học cho người chưa biết như thế nào?”.
 */
object DailyReviewAssistant {

    const val FEYNMAN_QUESTION_VI = DailyReviewSnapshot.FEYNMAN_QUESTION_VI

    /**
     * Tính tiến độ ngân sách tại ngày [targetDateLocal] (ngân sách ngày của ngày đó và ngân sách tuần
     * của tuần chứa ngày đó theo múi giờ [zoneId]).
     */
    fun computeBudgetProgressForDate(
        targetDateLocal: String,
        budgets: List<CategoryTimeBudget>,
        records: List<LedgerRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null
    ): List<TimeBudgetProgress> {
        if (budgets.isEmpty()) return emptyList()

        val targetDate = LocalDate.parse(targetDateLocal)
        val weekMonday = targetDate.with(DayOfWeek.MONDAY)
        val weekSunday = weekMonday.plusDays(6)

        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = targetDateLocal,
            records = records,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer
        )

        val weekStats = (0..6).map { offset ->
            val dStr = weekMonday.plusDays(offset.toLong()).toString()
            if (dStr == targetDateLocal) {
                dayStats
            } else {
                LedgerAnalyticsCalculator.computeDayStats(
                    dateLocal = dStr,
                    records = records,
                    zoneId = zoneId,
                    nowEpochMs = nowEpochMs,
                    activeTimer = activeTimer
                )
            }
        }

        return budgets
            .sortedWith(
                compareByDescending<CategoryTimeBudget> { it.isPriority }
                    .thenBy { it.priorityOrder }
                    .thenBy { it.category.ordinal }
            )
            .map { budget ->
                val cat = budget.category
                when (budget.period) {
                    BudgetPeriod.DAILY -> {
                        val usedTimed = dayStats.categoryBreakdown
                            .firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        val untimed = dayStats.untimedCategoryBreakdown
                            .firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        val planned = dayStats.plannedCategoryBreakdown
                            .firstOrNull { it.category == cat }?.durationSeconds ?: 0L

                        TimeBudgetProgress(
                            budget = budget,
                            periodStartDateLocal = targetDateLocal,
                            periodEndDateLocal = targetDateLocal,
                            usedSeconds = usedTimed,
                            targetSeconds = budget.targetSeconds,
                            remainingSeconds = max(0L, budget.targetSeconds - usedTimed),
                            exceededSeconds = max(0L, usedTimed - budget.targetSeconds),
                            deltaSeconds = budget.targetSeconds - usedTimed,
                            untimedNoteSeconds = untimed,
                            plannedSeconds = planned
                        )
                    }

                    BudgetPeriod.WEEKLY -> {
                        val usedTimed = weekStats.sumOf { d ->
                            d.categoryBreakdown.firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        }
                        val untimed = weekStats.sumOf { d ->
                            d.untimedCategoryBreakdown.firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        }
                        val planned = weekStats.sumOf { d ->
                            d.plannedCategoryBreakdown.firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        }

                        TimeBudgetProgress(
                            budget = budget,
                            periodStartDateLocal = weekMonday.toString(),
                            periodEndDateLocal = weekSunday.toString(),
                            usedSeconds = usedTimed,
                            targetSeconds = budget.targetSeconds,
                            remainingSeconds = max(0L, budget.targetSeconds - usedTimed),
                            exceededSeconds = max(0L, usedTimed - budget.targetSeconds),
                            deltaSeconds = budget.targetSeconds - usedTimed,
                            untimedNoteSeconds = untimed,
                            plannedSeconds = planned
                        )
                    }
                }
            }
    }

    /**
     * Xây dựng DailyReviewSnapshot đầy đủ cho ngày [dateLocal] từ dữ liệu thật trong Ledger.
     */
    fun buildSnapshotForDate(
        dateLocal: String,
        records: List<LedgerRecord>,
        budgets: List<CategoryTimeBudget>,
        activityNotes: List<ActivityReviewNote>,
        savedReview: DailyReviewRecord?,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null
    ): DailyReviewSnapshot {
        val todayLocalStr = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate().toString()
        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = dateLocal,
            records = records,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer
        )
        val budgetProgressList = computeBudgetProgressForDate(
            targetDateLocal = dateLocal,
            budgets = budgets,
            records = records,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer
        )

        val dayRecords = records
            .filter { it.date == dateLocal }
            .sortedBy { it.startedAt ?: it.createdAt }

        val completedActivities = mutableListOf<LedgerRecord>()
        val plannedOrActiveActivities = mutableListOf<LedgerRecord>()

        for (rec in dayRecords) {
            val resolved = rec.resolveLayerAt(nowEpochMs, todayLocalStr)
            if (resolved == TimeLayer.MEASURED ||
                resolved == TimeLayer.REPORTED_TIMED ||
                resolved == TimeLayer.REPORTED_UNTIMED
            ) {
                completedActivities.add(rec)
            } else {
                plannedOrActiveActivities.add(rec)
            }
        }

        val notesMap = activityNotes
            .filter { it.dateLocal == dateLocal || completedActivities.any { r -> r.id == it.recordId } }
            .associateBy { it.recordId }

        val summary = generateFactualSummary(
            dateLocal = dateLocal,
            dayStats = dayStats,
            completedActivities = completedActivities,
            budgetProgressList = budgetProgressList,
            notesMap = notesMap
        )

        val questions = generateReflectionQuestions(
            dateLocal = dateLocal,
            dayStats = dayStats,
            completedActivities = completedActivities,
            budgetProgressList = budgetProgressList
        )

        return DailyReviewSnapshot(
            dateLocal = dateLocal,
            dayStats = dayStats,
            budgetProgressList = budgetProgressList,
            completedActivities = completedActivities,
            plannedOrActiveActivities = plannedOrActiveActivities,
            activityNotesMap = notesMap,
            generatedSummary = summary,
            generatedQuestions = questions,
            savedReview = savedReview
        )
    }

    /**
     * Tạo bản tổng kết ngắn chỉ dựa trên dữ liệu có thật trong Ledger (không tự bịa kết quả, không chấm điểm).
     */
    fun generateFactualSummary(
        dateLocal: String,
        dayStats: DayLedgerStats,
        completedActivities: List<LedgerRecord>,
        budgetProgressList: List<TimeBudgetProgress>,
        notesMap: Map<String, ActivityReviewNote> = emptyMap()
    ): String {
        val timedStr = LedgerAnalyticsCalculator.formatCompactDurationSeconds(dayStats.timedSeconds)
        val untimedStr = LedgerAnalyticsCalculator.formatCompactDurationSeconds(dayStats.untimedSeconds)
        val untrackedStr = LedgerAnalyticsCalculator.formatCompactDurationSeconds(dayStats.untrackedPastSeconds)

        if (completedActivities.isEmpty() && dayStats.timedSeconds == 0L && dayStats.untimedSeconds == 0L) {
            val plannedNote = if (dayStats.plannedSeconds > 0L) {
                " Có ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(dayStats.plannedSeconds)} dự kiến (không tính vào thực tế)."
            } else {
                ""
            }
            return "Ngày $dateLocal: Chưa có hoạt động hoàn thành nào được ghi nhận trong Sổ cái (0h có mốc giờ, 0h chưa gắn giờ; chưa ghi nhận quá khứ: $untrackedStr).$plannedNote"
        }

        val titlesPreview = completedActivities
            .map { it.title }
            .distinct()
            .take(4)
            .joinToString(", ")

        val notedCount = completedActivities.count { rec ->
            val n = notesMap[rec.id]
            n != null && (n.outcomeNote.isNotBlank() || n.feynmanExplanation.isNotBlank())
        }

        return buildString {
            append("Ngày $dateLocal: Đã ghi nhận $timedStr có mốc giờ, $untimedStr chưa gắn giờ (tách riêng) và $untrackedStr chưa ghi nhận trong quá khứ.")
            if (titlesPreview.isNotBlank()) {
                append(" Hoạt động đã hoàn thành (${completedActivities.size}): $titlesPreview.")
            }
            if (dayStats.plannedSeconds > 0L) {
                append(" Khoảng dự kiến ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(dayStats.plannedSeconds)} được giữ riêng, không cộng vào thực tế.")
            }
            if (budgetProgressList.isNotEmpty()) {
                val topBudget = budgetProgressList.first()
                append(" Ngân sách ${topBudget.category.displayName}: đã ghi nhận ${topBudget.recordedSummaryVi}/${topBudget.targetFormatted} (${topBudget.combinedDifferenceLabelVi.lowercase()}).")
            }
            if (notedCount > 0) {
                append(" Bạn đã lưu ghi chú kết quả cho $notedCount/${completedActivities.size} hoạt động.")
            }
        }
    }

    /**
     * Sinh tối đa 2 câu hỏi phản tư trung lập, bám sát dữ liệu Ledger thật của ngày [dateLocal].
     */
    fun generateReflectionQuestions(
        dateLocal: String,
        dayStats: DayLedgerStats,
        completedActivities: List<LedgerRecord>,
        budgetProgressList: List<TimeBudgetProgress>
    ): List<String> {
        val questions = mutableListOf<String>()

        // Câu hỏi 1: Dựa trên hoạt động/danh mục thực tế đã ghi nhận trong ngày
        val topTimedCat = dayStats.categoryBreakdown.firstOrNull()
        val firstCompleted = completedActivities.firstOrNull()
        when {
            topTimedCat != null -> {
                val catDur = LedgerAnalyticsCalculator.formatCompactDurationSeconds(topTimedCat.durationSeconds)
                questions.add(
                    "Trong $catDur có mốc giờ dành cho ${topTimedCat.category.displayName} ngày $dateLocal, hoạt động nào mang lại kết quả hoặc ý nghĩa rõ rệt nhất với bạn?"
                )
            }
            firstCompleted != null -> {
                val recDur = LedgerAnalyticsCalculator.formatCompactDurationSeconds(firstCompleted.durationSeconds)
                questions.add(
                    "Với hoạt động \"${firstCompleted.title}\" ($recDur chưa gắn giờ) ngày $dateLocal, bạn có muốn gắn khung giờ cụ thể hoặc ghi lại kết quả chính đã đạt được không?"
                )
            }
            else -> {
                val untrackedDur = LedgerAnalyticsCalculator.formatCompactDurationSeconds(dayStats.untrackedPastSeconds.coerceAtLeast(60L))
                questions.add(
                    "Ngày $dateLocal hiện có $untrackedDur quá khứ chưa ghi nhận; đâu là hoạt động chính đã diễn ra mà bạn muốn lưu lại vào Sổ cái?"
                )
            }
        }

        // Câu hỏi 2: Dựa trên ngân sách thật (nếu có) hoặc phần thời gian chưa ghi nhận / cân bằng trong ngày
        val priorityBudget = budgetProgressList.firstOrNull()
        if (priorityBudget != null) {
            questions.add(
                "Ngân sách ${priorityBudget.category.displayName} hiện đã ghi nhận ${priorityBudget.recordedSummaryVi} / ${priorityBudget.targetFormatted} (${priorityBudget.combinedDifferenceLabelVi.lowercase()}); bạn muốn giữ nguyên phân bổ hiện tại hay điều chỉnh kế hoạch cho ngày tiếp theo?"
            )
        } else if (dayStats.untrackedPastSeconds >= 3600L) {
            val untrackedDur = LedgerAnalyticsCalculator.formatCompactDurationSeconds(dayStats.untrackedPastSeconds)
            questions.add(
                "Trong khoảng $untrackedDur chưa ghi nhận của ngày $dateLocal, phần nào là thời gian nghỉ ngơi tự nhiên và phần nào bạn muốn chủ động theo dõi thêm?"
            )
        } else {
            questions.add(
                "Nhìn lại các hoạt động ngày $dateLocal, điều gì bạn muốn duy trì tương tự vào ngày mai?"
            )
        }

        return questions.take(2)
    }
}

