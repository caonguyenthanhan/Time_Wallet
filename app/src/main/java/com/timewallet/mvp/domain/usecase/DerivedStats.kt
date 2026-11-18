package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.DailyReviewSnapshot
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.Instant
import java.time.ZoneId

/**
 * WALLY v1.0.6 (nhóm C): toàn bộ số liệu dẫn xuất của Tổng quan/Insights/Nhật ký ngày/xúc xắc, tính MỘT lần từ ảnh chụp bất biến.
 * Hàm thuần, không đụng trạng thái giao diện, nên ViewModel chạy được trên Dispatchers.Default thay vì luồng chính
 * (trước đây tính lại cả tuần + nhật ký ngày + xúc xắc ngay trên luồng chính mỗi khi dữ liệu đổi và mỗi 15 giây).
 */
class DerivedStats(
    val todayDateLocal: String,
    val targetReviewDate: String,
    val dayStats: DayLedgerStats,
    val weekly: WeeklyInsightsSummary,
    val weeklyEfficiency: WeeklyEfficiencySummary,
    val budgetProgress: List<TimeBudgetProgress>,
    val priorityBudgets: List<TimeBudgetProgress>,
    val reviewSnapshot: DailyReviewSnapshot,
    val cognitiveState: CognitiveState,
    val faceProbabilities: List<FaceProbabilityItem>
)

object DerivedStatsCalculator {

    fun compute(
        records: List<LedgerRecord>,
        budgets: List<CategoryTimeBudget>,
        dailyReviews: List<DailyReviewRecord>,
        activityNotes: List<ActivityReviewNote>,
        reviewDateOverride: String?,
        selectedReviewDate: String,
        activeTimer: ActiveTimerSession?,
        preferredFocusCategory: TimeCategory,
        consecutiveBreakRolls: Int,
        zone: ZoneId,
        nowMs: Long,
        goals: List<Goal> = emptyList(),
        tags: List<Tag> = emptyList()
    ): DerivedStats {
        val todayStr = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = todayStr, records = records, zoneId = zone, nowEpochMs = nowMs, activeTimer = activeTimer
        )
        val weekly = LedgerAnalyticsCalculator.computeWeeklyInsights(
            endDateLocal = todayStr, records = records, zoneId = zone, nowEpochMs = nowMs,
            activeTimer = activeTimer, budgets = budgets
        )
        val weeklyEfficiency = LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = todayStr,
            records = records,
            goals = goals,
            budgets = budgets,
            tags = tags,
            zoneId = zone,
            nowEpochMs = nowMs,
            activeTimer = activeTimer,
            weeklyInsights = weekly
        )
        val targetReviewDate = reviewDateOverride ?: selectedReviewDate.ifBlank { todayStr }
        val reviewSnapshot = DailyReviewAssistant.buildSnapshotForDate(
            dateLocal = targetReviewDate,
            records = records,
            budgets = budgets,
            savedReview = dailyReviews.firstOrNull { it.dateLocal == targetReviewDate },
            activityNotes = activityNotes.filter { it.dateLocal == targetReviewDate },
            zoneId = zone,
            nowEpochMs = nowMs,
            activeTimer = activeTimer
        )
        val diceCtx = DopamineTimeDiceEngine.deriveContextFromRecords(
            todayDateLocal = todayStr,
            records = records,
            nowEpochMs = nowMs,
            consecutiveBreakRolls = consecutiveBreakRolls,
            preferredFocusCategory = preferredFocusCategory
        )
        val (cog, probs) = DopamineTimeDiceEngine.computeProbabilities(diceCtx)
        return DerivedStats(
            todayDateLocal = todayStr,
            targetReviewDate = targetReviewDate,
            dayStats = dayStats,
            weekly = weekly,
            weeklyEfficiency = weeklyEfficiency,
            budgetProgress = weekly.budgetProgressList,
            priorityBudgets = BudgetPolicy.dashboardBudgets(weekly.budgetProgressList),
            reviewSnapshot = reviewSnapshot,
            cognitiveState = cog,
            faceProbabilities = probs
        )
    }
}
