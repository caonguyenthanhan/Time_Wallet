package com.timewallet.mvp.ui

import com.timewallet.mvp.data.local.SubtaskEntity
import com.timewallet.mvp.data.local.TaskEntity
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.LocalCategoryRule
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.WallyContextEngine
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.MarkdownSummaryExporter
import com.timewallet.mvp.domain.usecase.PeakFocusWindow
import com.timewallet.mvp.domain.usecase.PlanAccuracySummary
import com.timewallet.mvp.domain.usecase.ScheduleFreeGap
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.TaskHabitEngine
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.domain.usecase.WeeklyEfficiencySummary
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Tách logic tổng hợp của Wally 2.0 ra khỏi God-ViewModel (`TimeWalletViewModel`):
 * - Lắp Task + Subtasks, tính PeakFocusWindow & Plan vs Actual Accuracy.
 * - Hợp nhất hiển thị Goal + CategoryBudget thành danh sách mục tiêu thống nhất.
 * - Thực thi lọc dữ liệu theo `AiReadPermissions` trước khi gửi AI.
 * - Hỗ trợ xuất Markdown cho hệ sinh thái ghi chú cá nhân.
 */
object Wally20Coordinator {

    fun assembleTasks(
        taskEntities: List<TaskEntity>,
        subtaskEntities: List<SubtaskEntity>
    ): List<TaskItem> {
        val byTask = subtaskEntities
            .map { it.toDomain() }
            .groupBy { it.taskId }
            .mapValues { (_, list) -> list.sortedBy { it.sortOrder } }
        return taskEntities.map { t -> t.toDomain(byTask[t.id].orEmpty()) }
    }

    /**
     * Hợp nhất `Goal` và `CategoryBudget` cũ thành danh sách thống nhất để hiển thị chung ở ngăn Mục tiêu,
     * tránh trùng lặp các ngân sách đã di trú từ `category_budgets` sang `goals` trong `MIGRATION_6_7`.
     */
    fun buildUnifiedGoalSummaries(
        goals: List<Goal>,
        budgets: List<CategoryTimeBudget>,
        records: List<LedgerRecord>,
        tagNameOf: (String) -> String?,
        today: LocalDate,
        nowEpochMs: Long,
        zone: ZoneId
    ): List<GoalRules.Summary> {
        val goalSummaries = GoalRules.summaries(
            goals = goals,
            records = records,
            tagNameOf = tagNameOf,
            today = today,
            nowEpochMs = nowEpochMs,
            zone = zone
        )
        val coveredWeeklyMaxCategories = goalSummaries
            .filter { it.latest.targetType == GoalTargetType.CATEGORY && it.latest.kind == GoalKind.MAX }
            .mapTo(HashSet()) { it.latest.targetKey }

        val legacyBudgetFallbacks = budgets
            .filter { b -> b.category.key !in coveredWeeklyMaxCategories }
            .map { b ->
                val syntheticGoal = Goal(
                    id = "budget_view_${b.category.key}",
                    groupId = "grp_budget_${b.category.key}",
                    targetType = GoalTargetType.CATEGORY,
                    targetKey = b.category.key,
                    kind = GoalKind.MAX,
                    period = GoalPeriod.WEEK,
                    targetSeconds = b.targetSeconds,
                    status = GoalVersionStatus.ACTIVE,
                    validFrom = "1970-01-01",
                    validTo = null,
                    createdAt = b.updatedAt,
                    updatedAt = b.updatedAt,
                    isPriority = b.isPriority,
                    priorityOrder = b.priorityOrder
                )
                val prog = GoalRules.progress(syntheticGoal, records, today, nowEpochMs, zone)
                GoalRules.Summary(
                    groupId = syntheticGoal.groupId,
                    name = "${b.category.displayName} (Ngân sách tuần)",
                    state = com.timewallet.mvp.domain.model.GoalGroupState.ACTIVE,
                    latest = syntheticGoal,
                    progress = prog
                )
            }
        return (goalSummaries + legacyBudgetFallbacks)
            .sortedWith(compareByDescending<GoalRules.Summary> { it.latest.isPriority }.thenBy { it.latest.priorityOrder })
    }

    fun computeAnalyticsExtras(
        records: List<LedgerRecord>,
        todayDateLocal: String,
        zone: ZoneId
    ): Pair<PeakFocusWindow?, PlanAccuracySummary> {
        val peak = TaskHabitEngine.computePeakFocusWindow(records, todayDateLocal, zone)
        val acc = TaskHabitEngine.computePlanVsActualDelta(records)
        return peak to acc
    }

    fun buildFilteredCoachDigest(
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        goals: List<Goal>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        days: Int,
        nowMs: Long,
        zone: ZoneId
    ) = WallyContextEngine.buildFilteredCoachDigest(
        perms = perms,
        records = records,
        tags = tags,
        goals = goals,
        tasks = tasks,
        habits = habits,
        habitLogs = habitLogs,
        days = days,
        nowMs = nowMs,
        zone = zone
    )

    fun proposeSubtasksOffline(task: TaskItem): List<String> {
        val clean = task.title.trim()
        val est = task.estimatedMinutes ?: 45
        val stepMins = (est / 3).coerceAtLeast(10)
        return listOf(
            "Xác định mục tiêu đầu ra cho \"$clean\" (~${stepMins}p)",
            "Thực hiện phần lõi không xao nhãng (~${stepMins}p)",
            "Kiểm tra & chốt kết quả (~${(est - 2 * stepMins).coerceAtLeast(10)}p)"
        )
    }

    fun buildInboxReplanDrafts(
        tasks: List<TaskItem>,
        records: List<LedgerRecord>,
        todayLocal: String,
        nowEpochMs: Long,
        zone: ZoneId
    ): List<EditableProposalDraft> {
        val unscheduled = tasks
            .filter { !it.isDone && it.status != TaskStatus.ARCHIVED && it.dueDateLocal == null && it.dueTimeLocal == null }
            .sortedBy { it.priority.sortWeight }
            .take(3)
        if (unscheduled.isEmpty()) return emptyList()

        val targetDate = runCatching { LocalDate.parse(todayLocal) }.getOrElse { LocalDate.now(zone) }
        val dayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = targetDate,
            records = records,
            activeTimer = null,
            nowEpochMs = nowEpochMs,
            zone = zone
        )
        val nowMinute = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(zone).let { it.hour * 60 + it.minute }
        val openGaps: MutableList<ScheduleFreeGap> = dayModel.freeGaps
            .filter { g -> g.endMinute > nowMinute + 15 }
            .toMutableList()
        if (openGaps.isEmpty()) return emptyList()

        val drafts = mutableListOf<EditableProposalDraft>()
        for (task in unscheduled) {
            val needMins = (task.estimatedMinutes ?: 30).coerceIn(15, 180)
            val gapIdx = openGaps.indexOfFirst { g -> (g.endMinute - maxOf(g.startMinute, nowMinute + 10)) >= needMins }
            if (gapIdx < 0) continue
            val gap = openGaps[gapIdx]
            val startMin = maxOf(gap.startMinute, ((nowMinute + 14) / 15) * 15)
            val endMin = (startMin + needMins).coerceAtMost(gap.endMinute)
            if (endMin - startMin < 15) continue
            drafts += EditableProposalDraft(
                id = "replan_${task.id}_${UUID.randomUUID().toString().take(6)}",
                rawInput = "Wally xếp lịch từ Inbox: ${task.title}",
                title = task.title,
                categoryKey = task.category.key,
                durationMinutesText = (endMin - startMin).toString(),
                dateLocal = todayLocal,
                startLocal = TimeRange.formatHm(startMin),
                endLocal = TimeRange.formatHm(endMin),
                hasInitialTimeEvidence = true,
                isFuturePlanned = true,
                tagId = task.tagId,
                taskId = task.id
            )
            if (endMin < gap.endMinute) {
                openGaps[gapIdx] = gap.copy(startMinute = endMin + 5)
            } else {
                openGaps.removeAt(gapIdx)
            }
        }
        return drafts
    }

    fun exportDailyMarkdown(
        dateLocal: String,
        records: List<LedgerRecord>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        dailyReviews: List<DailyReviewRecord>,
        weeklyEfficiency: WeeklyEfficiencySummary,
        zone: ZoneId
    ): String {
        val dayEff = weeklyEfficiency.days.firstOrNull { it.dateLocal == dateLocal }
        val dr = dailyReviews.firstOrNull { it.dateLocal == dateLocal }
        return MarkdownSummaryExporter.exportDailyMarkdown(
            dateLocal = dateLocal,
            records = records,
            tasks = tasks,
            habits = habits,
            habitLogs = habitLogs,
            dailyReview = dr,
            efficiency = dayEff,
            zoneId = zone
        )
    }

    fun matchLocalRuleCategory(
        title: String,
        rules: List<LocalCategoryRule>
    ): LocalCategoryRule? {
        val norm = com.timewallet.mvp.domain.usecase.TagRules.nameKey(title)
        if (norm.isBlank()) return null
        return rules.firstOrNull { r ->
            r.enabled && r.keyword.trim().isNotEmpty() && norm.contains(com.timewallet.mvp.domain.usecase.TagRules.nameKey(r.keyword))
        }
    }
}
