package com.timewallet.mvp

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.CustomizationSettings
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiTodoAnalysisReport
import com.timewallet.mvp.domain.usecase.AiTodoProposal
import com.timewallet.mvp.domain.usecase.AiTodoProposalKind
import com.timewallet.mvp.domain.usecase.CognitiveState
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.ScheduleViewMode
import com.timewallet.mvp.domain.usecase.TaskSmartFilter
import com.timewallet.mvp.domain.usecase.TodoRiskAlert
import com.timewallet.mvp.domain.usecase.TodoRiskKind
import com.timewallet.mvp.ui.HistoryFilter
import com.timewallet.mvp.ui.screens.HistoryScreen
import com.timewallet.mvp.ui.screens.InsightsScreen
import com.timewallet.mvp.ui.screens.ScheduleCalendarView
import com.timewallet.mvp.ui.screens.TasksHabitsScreen
import com.timewallet.mvp.ui.screens.TimerScreen
import com.timewallet.mvp.ui.theme.TimeWalletTheme
import com.timewallet.mvp.ui.theme.WalletBg
import java.time.ZoneId
import java.time.ZonedDateTime

private object PreviewFixtures {
    const val TODAY = "2026-11-23"
    val zone: ZoneId = ZoneId.systemDefault()

    fun ms(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 11, 23, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    val fixedNowMs: Long = ms(17, 20)

    val sampleRecords: List<LedgerRecord> = listOf(
        LedgerRecord(
            id = "prev_sleep",
            source = "manual",
            layer = TimeLayer.REPORTED_TIMED,
            createdAt = ms(7, 10),
            userConfirmedAt = ms(7, 10),
            category = TimeCategory.SLEEP,
            title = "Ngủ đêm",
            date = TODAY,
            durationSeconds = 7 * 3600L,
            startedAt = ms(0, 0),
            endedAt = ms(7, 0),
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "prev_sleep"
        ),
        LedgerRecord(
            id = "prev_deep",
            source = "timer",
            layer = TimeLayer.MEASURED,
            createdAt = ms(10, 30),
            userConfirmedAt = ms(10, 30),
            category = TimeCategory.WORK,
            title = "Thiết kế UI Time Wallet",
            date = TODAY,
            durationSeconds = 90 * 60L,
            startedAt = ms(9, 0),
            endedAt = ms(10, 30),
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "prev_deep"
        ),
        LedgerRecord(
            id = "prev_planned",
            source = "todo_ai_plan",
            layer = TimeLayer.PLANNED,
            createdAt = ms(17, 15),
            userConfirmedAt = ms(17, 15),
            category = TimeCategory.LEARN,
            title = "Ôn thi hệ thống phân tán",
            date = TODAY,
            durationSeconds = 45 * 60L,
            startedAt = ms(19, 30),
            endedAt = ms(20, 15),
            timezone = zone.id,
            status = "PLANNED",
            idempotencyKey = "prev_planned",
            taskId = "prev_t1"
        )
    )

    val sampleTasks: List<TaskItem> = listOf(
        TaskItem(
            id = "prev_t1",
            title = "Ôn thi hệ thống phân tán",
            category = TimeCategory.LEARN,
            priority = TaskPriority.P1,
            estimatedMinutes = 45,
            dueDateLocal = TODAY,
            dueTimeLocal = "20:30",
            status = TaskStatus.TODO
        ),
        TaskItem(
            id = "prev_t2",
            title = "Nộp báo cáo tuần nghiên cứu",
            category = TimeCategory.WORK,
            priority = TaskPriority.P2,
            estimatedMinutes = 30,
            dueDateLocal = TODAY,
            status = TaskStatus.INBOX
        )
    )

    val sampleRisks: List<TodoRiskAlert> = listOf(
        TodoRiskAlert(
            taskId = "prev_t1",
            taskTitle = "Ôn thi hệ thống phân tán",
            kind = TodoRiskKind.DUE_TODAY_SOON,
            severityHigh = false,
            detailVi = "Sắp tới mốc 20:30 (còn 45p)."
        )
    )

    val sampleReport = AiTodoAnalysisReport(
        generatedAtEpochMs = ms(17, 20),
        dateLocal = TODAY,
        fromCloudAi = false,
        summaryVi = "Tổng hợp cục bộ (2 việc mở, 1 lưu ý).",
        orderedTaskIds = listOf("prev_t1", "prev_t2"),
        risks = sampleRisks,
        proposals = listOf(
            AiTodoProposal(
                id = "prev_prop_1",
                kind = AiTodoProposalKind.SCHEDULE_INTO_GAP,
                taskId = "prev_t2",
                taskTitle = "Nộp báo cáo tuần nghiên cứu",
                reasonVi = "Phù hợp khung trống 20:30–21:00 hôm nay (P2)",
                targetDateLocal = TODAY,
                startHm = "20:30",
                endHm = "21:00",
                categoryKey = TimeCategory.WORK.key,
                needsConfirmation = true
            )
        )
    )

    val weeklyInsights by lazy {
        LedgerAnalyticsCalculator.computeWeeklyInsights(
            endDateLocal = TODAY,
            records = sampleRecords,
            zoneId = zone,
            nowEpochMs = ms(17, 20)
        )
    }

    val weeklyEfficiency by lazy {
        LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = TODAY,
            records = sampleRecords,
            zoneId = zone,
            nowEpochMs = ms(17, 20)
        )
    }
}

@PreviewTest
@Preview(name = "Schedule Day Light", showBackground = true, widthDp = 411, heightDp = 860)
@Composable
fun ScheduleDayLightPreview() {
    TimeWalletTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ScheduleCalendarView(
                todayDateLocal = PreviewFixtures.TODAY,
                records = PreviewFixtures.sampleRecords,
                selectedDateLocal = PreviewFixtures.TODAY,
                viewMode = ScheduleViewMode.DAY,
                onSelectDate = {},
                onSelectViewMode = {},
                onAddForSlot = { _, _, _, _ -> },
                onEditRecord = {},
                onConfirmPlannedOrActiveRecord = {},
                onMoveOrResizeBlock = { _, _, _, _ -> },
                embeddedInScrollContainer = false,
                recordedSummaryBadge = "8h 30 / 24h ▾",
                nowEpochMs = PreviewFixtures.fixedNowMs
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "Schedule Day Dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 860,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ScheduleDayDarkPreview() {
    TimeWalletTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ScheduleCalendarView(
                todayDateLocal = PreviewFixtures.TODAY,
                records = PreviewFixtures.sampleRecords,
                selectedDateLocal = PreviewFixtures.TODAY,
                viewMode = ScheduleViewMode.DAY,
                onSelectDate = {},
                onSelectViewMode = {},
                onAddForSlot = { _, _, _, _ -> },
                onEditRecord = {},
                onConfirmPlannedOrActiveRecord = {},
                onMoveOrResizeBlock = { _, _, _, _ -> },
                embeddedInScrollContainer = false,
                recordedSummaryBadge = "8h 30 / 24h ▾",
                nowEpochMs = PreviewFixtures.fixedNowMs
            )
        }
    }
}

@PreviewTest
@Preview(name = "Schedule Week Light", showBackground = true, widthDp = 411, heightDp = 860)
@Composable
fun ScheduleWeekLightPreview() {
    TimeWalletTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ScheduleCalendarView(
                todayDateLocal = PreviewFixtures.TODAY,
                records = PreviewFixtures.sampleRecords,
                selectedDateLocal = PreviewFixtures.TODAY,
                viewMode = ScheduleViewMode.WEEK,
                onSelectDate = {},
                onSelectViewMode = {},
                onAddForSlot = { _, _, _, _ -> },
                onEditRecord = {},
                onConfirmPlannedOrActiveRecord = {},
                onMoveOrResizeBlock = { _, _, _, _ -> },
                embeddedInScrollContainer = false,
                recordedSummaryBadge = "8h 30 / 24h ▾",
                nowEpochMs = PreviewFixtures.fixedNowMs
            )
        }
    }
}

@PreviewTest
@Preview(name = "Schedule Day 320dp", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
fun ScheduleDay320dpPreview() {
    TimeWalletTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            ScheduleCalendarView(
                todayDateLocal = PreviewFixtures.TODAY,
                records = PreviewFixtures.sampleRecords,
                selectedDateLocal = PreviewFixtures.TODAY,
                viewMode = ScheduleViewMode.DAY,
                onSelectDate = {},
                onSelectViewMode = {},
                onAddForSlot = { _, _, _, _ -> },
                onEditRecord = {},
                onConfirmPlannedOrActiveRecord = {},
                onMoveOrResizeBlock = { _, _, _, _ -> },
                embeddedInScrollContainer = true,
                recordedSummaryBadge = "8h 30 / 24h ▾",
                nowEpochMs = PreviewFixtures.fixedNowMs
            )
        }
    }
}

@PreviewTest
@Preview(name = "Timeline Light 390dp", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TimelineLightPreview() {
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            HistoryScreen(
                todayDateLocal = PreviewFixtures.TODAY,
                records = PreviewFixtures.sampleRecords,
                selectedFilter = HistoryFilter.ALL,
                onSelectFilter = {},
                onConfirmPlannedOrActiveRecord = {},
                onEditRecord = {},
                onDeleteRecord = {},
                showCalendarMode = false,
                todayStats = LedgerAnalyticsCalculator.computeDayStats(
                    dateLocal = PreviewFixtures.TODAY,
                    records = PreviewFixtures.sampleRecords,
                    zoneId = PreviewFixtures.zone,
                    nowEpochMs = PreviewFixtures.fixedNowMs
                ),
                inboxTasks = PreviewFixtures.sampleTasks,
                nowEpochMs = PreviewFixtures.fixedNowMs
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "Timeline Dark 320dp",
    showBackground = true,
    widthDp = 320,
    heightDp = 568,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun TimelineDark320dpPreview() {
    TimeWalletTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            HistoryScreen(
                todayDateLocal = PreviewFixtures.TODAY,
                records = PreviewFixtures.sampleRecords,
                selectedFilter = HistoryFilter.ALL,
                onSelectFilter = {},
                onConfirmPlannedOrActiveRecord = {},
                onEditRecord = {},
                onDeleteRecord = {},
                showCalendarMode = false,
                nowEpochMs = PreviewFixtures.fixedNowMs
            )
        }
    }
}

@PreviewTest
@Preview(name = "Tasks Applied Card Light", showBackground = true, widthDp = 411, heightDp = 860)
@Composable
fun TasksAppliedCardLightPreview() {
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            TasksHabitsScreen(
                todayDateLocal = PreviewFixtures.TODAY,
                tasks = PreviewFixtures.sampleTasks,
                taskFilter = TaskSmartFilter.TODAY,
                onSelectTaskFilter = {},
                habits = emptyList(),
                habitLogs = emptyList(),
                records = PreviewFixtures.sampleRecords,
                tags = emptyList(),
                unifiedGoals = emptyList(),
                customization = CustomizationSettings(),
                onCreateTask = { _, _, _, _, _, _, _, _ -> },
                onToggleTaskDone = {},
                onUpdateTaskStatus = { _, _ -> },
                onSetTaskEisenhower = { _, _ -> },
                onAddSubtask = { _, _ -> },
                onToggleSubtaskDone = {},
                onDeleteTask = {},
                onStartFocusOnTask = {},
                onScheduleTaskOnCalendar = {},
                onBreakDownTaskWithWally = {},
                onRequestWallyReplanInbox = {},
                onCreateHabit = { _, _, _, _, _ -> },
                onToggleHabitForToday = {},
                onOpenGoalEditor = {},
                onPauseGoal = {},
                onResumeGoal = {},
                onEndGoal = {},
                onOpenCustomization = {},
                todoRiskAlerts = PreviewFixtures.sampleRisks,
                todoAiReport = PreviewFixtures.sampleReport,
                lastTodoApprovalReceipt = "Bạn đã duyệt đề xuất → Đã xếp \"Ôn thi hệ thống phân tán\" vào Lịch dự kiến (2026-11-23 · 19:30–20:15). Thời gian Thực tế giữ nguyên 0p."
            )
        }
    }
}

@PreviewTest
@Preview(name = "Tasks Applied Card 320dp", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
fun TasksAppliedCard320dpPreview() {
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            TasksHabitsScreen(
                todayDateLocal = PreviewFixtures.TODAY,
                tasks = PreviewFixtures.sampleTasks,
                taskFilter = TaskSmartFilter.TODAY,
                onSelectTaskFilter = {},
                habits = emptyList(),
                habitLogs = emptyList(),
                records = PreviewFixtures.sampleRecords,
                tags = emptyList(),
                unifiedGoals = emptyList(),
                customization = CustomizationSettings(),
                onCreateTask = { _, _, _, _, _, _, _, _ -> },
                onToggleTaskDone = {},
                onUpdateTaskStatus = { _, _ -> },
                onSetTaskEisenhower = { _, _ -> },
                onAddSubtask = { _, _ -> },
                onToggleSubtaskDone = {},
                onDeleteTask = {},
                onStartFocusOnTask = {},
                onScheduleTaskOnCalendar = {},
                onBreakDownTaskWithWally = {},
                onRequestWallyReplanInbox = {},
                onCreateHabit = { _, _, _, _, _ -> },
                onToggleHabitForToday = {},
                onOpenGoalEditor = {},
                onPauseGoal = {},
                onResumeGoal = {},
                onEndGoal = {},
                onOpenCustomization = {},
                todoRiskAlerts = PreviewFixtures.sampleRisks,
                lastTodoApprovalReceipt = "Bạn đã duyệt đề xuất → Đã xếp vào Lịch dự kiến (19:30–20:15)."
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "Tasks Dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 860,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun TasksDarkPreview() {
    TimeWalletTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            TasksHabitsScreen(
                todayDateLocal = PreviewFixtures.TODAY,
                tasks = PreviewFixtures.sampleTasks,
                taskFilter = TaskSmartFilter.TODAY,
                onSelectTaskFilter = {},
                habits = emptyList(),
                habitLogs = emptyList(),
                records = PreviewFixtures.sampleRecords,
                tags = emptyList(),
                unifiedGoals = emptyList(),
                customization = CustomizationSettings(),
                onCreateTask = { _, _, _, _, _, _, _, _ -> },
                onToggleTaskDone = {},
                onUpdateTaskStatus = { _, _ -> },
                onSetTaskEisenhower = { _, _ -> },
                onAddSubtask = { _, _ -> },
                onToggleSubtaskDone = {},
                onDeleteTask = {},
                onStartFocusOnTask = {},
                onScheduleTaskOnCalendar = {},
                onBreakDownTaskWithWally = {},
                onRequestWallyReplanInbox = {},
                onCreateHabit = { _, _, _, _, _ -> },
                onToggleHabitForToday = {},
                onOpenGoalEditor = {},
                onPauseGoal = {},
                onResumeGoal = {},
                onEndGoal = {},
                onOpenCustomization = {},
                todoRiskAlerts = PreviewFixtures.sampleRisks
            )
        }
    }
}

@PreviewTest
@Preview(name = "Timer Active Light", showBackground = true, widthDp = 411, heightDp = 860)
@Composable
fun TimerActiveLightPreview() {
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            TimerScreen(
                activeTimer = ActiveTimerSession(
                    title = "Ôn thi hệ thống phân tán",
                    categoryKey = TimeCategory.LEARN.key,
                    startedAtEpochMs = 1_795_420_000_000L,
                    startedElapsedRealtimeMs = 1000L,
                    timezone = PreviewFixtures.zone.id,
                    targetDurationSeconds = 2700L
                ),
                elapsedSeconds = 865L,
                clockSkewDetected = false,
                inputTitle = "Ôn thi hệ thống phân tán",
                inputCategoryKey = TimeCategory.LEARN.key,
                diceCognitiveState = CognitiveState.HIGH_ENERGY_BUILDUP,
                diceFaceProbabilities = emptyList(),
                isRollingDice = false,
                rollingPreviewFaceNumber = 1,
                lastDiceRoll = null,
                onTitleChange = {},
                onCategoryChange = {},
                onRollDice = {},
                onApplyDiceAndStartTimer = {},
                onStartTimer = {},
                onStopAndSaveTimer = {},
                onCancelTimer = {},
                idleQuickStartTasks = PreviewFixtures.sampleTasks
            )
        }
    }
}

@PreviewTest
@Preview(name = "Timer Idle QuickStart 390dp", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TimerIdleQuickStartLightPreview() {
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            TimerScreen(
                activeTimer = null,
                elapsedSeconds = 0L,
                clockSkewDetected = false,
                inputTitle = "",
                inputCategoryKey = TimeCategory.WORK.key,
                diceCognitiveState = CognitiveState.HIGH_ENERGY_BUILDUP,
                diceFaceProbabilities = emptyList(),
                isRollingDice = false,
                rollingPreviewFaceNumber = 1,
                lastDiceRoll = null,
                onTitleChange = {},
                onCategoryChange = {},
                onRollDice = {},
                onApplyDiceAndStartTimer = {},
                onStartTimer = {},
                onStopAndSaveTimer = {},
                onCancelTimer = {},
                idleQuickStartTasks = PreviewFixtures.sampleTasks
            )
        }
    }
}

@PreviewTest
@Preview(name = "Timer Idle 320dp", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
fun TimerIdle320dpPreview() {
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            TimerScreen(
                activeTimer = null,
                elapsedSeconds = 0L,
                clockSkewDetected = false,
                inputTitle = "",
                inputCategoryKey = TimeCategory.WORK.key,
                diceCognitiveState = CognitiveState.HIGH_ENERGY_BUILDUP,
                diceFaceProbabilities = emptyList(),
                isRollingDice = false,
                rollingPreviewFaceNumber = 1,
                lastDiceRoll = null,
                onTitleChange = {},
                onCategoryChange = {},
                onRollDice = {},
                onApplyDiceAndStartTimer = {},
                onStartTimer = {},
                onStopAndSaveTimer = {},
                onCancelTimer = {},
                idleQuickStartTasks = PreviewFixtures.sampleTasks
            )
        }
    }
}

@PreviewTest
@Preview(name = "Insights Light", showBackground = true, widthDp = 411, heightDp = 860)
@Composable
fun InsightsLightPreview() {
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            InsightsScreen(
                weeklyInsights = PreviewFixtures.weeklyInsights,
                weeklyEfficiency = PreviewFixtures.weeklyEfficiency
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "Insights Dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 860,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun InsightsDarkPreview() {
    TimeWalletTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            InsightsScreen(
                weeklyInsights = PreviewFixtures.weeklyInsights,
                weeklyEfficiency = PreviewFixtures.weeklyEfficiency
            )
        }
    }
}

@PreviewTest
@Preview(name = "Insights Empty Adaptive 390dp", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun InsightsEmptyAdaptivePreview() {
    val emptyInsights = LedgerAnalyticsCalculator.computeWeeklyInsights(
        endDateLocal = PreviewFixtures.TODAY,
        records = emptyList(),
        zoneId = PreviewFixtures.zone,
        nowEpochMs = PreviewFixtures.fixedNowMs
    )
    val emptyEff = LedgerAnalyticsCalculator.computeWeeklyEfficiency(
        endDateLocal = PreviewFixtures.TODAY,
        records = emptyList(),
        zoneId = PreviewFixtures.zone,
        nowEpochMs = PreviewFixtures.fixedNowMs
    )
    TimeWalletTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            InsightsScreen(
                weeklyInsights = emptyInsights,
                weeklyEfficiency = emptyEff
            )
        }
    }
}
