package com.timewallet.mvp

import com.timewallet.mvp.data.local.BackupParseResult
import com.timewallet.mvp.data.local.LedgerJsonBackupParser
import com.timewallet.mvp.data.local.LedgerJsonExporter
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.CustomizationSettings
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitFrequency
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.LocalCategoryRule
import com.timewallet.mvp.domain.model.Project
import com.timewallet.mvp.domain.model.SubtaskItem
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.CaptureIntentKind
import com.timewallet.mvp.domain.usecase.MarkdownSummaryExporter
import com.timewallet.mvp.domain.usecase.TaskHabitEngine
import com.timewallet.mvp.domain.usecase.TaskSmartFilter
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.Wally20Coordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class V200ArchitectureAndFeatureTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `TaskHabitEngine auto completes habit from matching tag ledger session and excludes planned or waste`() {
        val tagRun = Tag(
            id = "tag_run",
            name = "Chạy bộ",
            nameKey = "chay bo",
            category = TimeCategory.HEALTH,
            archivedAt = null,
            createdAt = 1L,
            updatedAt = 1L
        )
        val habit = Habit(
            id = "hab_1",
            title = "Chạy mỗi sáng",
            tagId = tagRun.id,
            category = TimeCategory.HEALTH,
            frequency = HabitFrequency.DAILY,
            minSessionMinutes = 20
        )
        val dateStr = "2026-11-18"
        val dayStart = LocalDate.parse(dateStr).atStartOfDay(zone).toInstant().toEpochMilli()

        // Planned session or waste session should NOT auto-complete habit
        val planned = LedgerRecord(
            id = "r_plan",
            source = "manual",
            layer = TimeLayer.PLANNED,
            createdAt = dayStart,
            userConfirmedAt = 0L,
            category = TimeCategory.HEALTH,
            title = "Chạy bộ dự kiến",
            date = dateStr,
            durationSeconds = 1800L,
            startedAt = dayStart + 6 * 3600_000L,
            endedAt = dayStart + 6 * 3600_000L + 1800_000L,
            timezone = zone.id,
            status = "PLANNED",
            idempotencyKey = "k_p",
            tagId = tagRun.id
        )
        assertNull(TaskHabitEngine.findAutoFulfillingRecord(habit, dateStr, listOf(planned)))
        assertFalse(TaskHabitEngine.isHabitCompletedOnDate(habit, dateStr, emptyList(), listOf(planned)))

        // Confirmed MEASURED session >= 20m auto-completes habit
        val actual = LedgerRecord(
            id = "r_act",
            source = "timer",
            layer = TimeLayer.MEASURED,
            createdAt = dayStart,
            userConfirmedAt = dayStart + 7 * 3600_000L,
            category = TimeCategory.HEALTH,
            title = "Chạy công viên",
            date = dateStr,
            durationSeconds = 1500L,
            startedAt = dayStart + 6 * 3600_000L,
            endedAt = dayStart + 6 * 3600_000L + 1500_000L,
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "k_a",
            tagId = tagRun.id
        )
        assertNotNull(TaskHabitEngine.findAutoFulfillingRecord(habit, dateStr, listOf(planned, actual)))
        assertTrue(TaskHabitEngine.isHabitCompletedOnDate(habit, dateStr, emptyList(), listOf(planned, actual)))

        val dots = TaskHabitEngine.habitRecentDots(habit, dateStr, 7, emptyList(), listOf(actual))
        assertEquals(7, dots.size)
        assertTrue(dots.last())
        assertFalse(dots.dropLast(1).any { it })
    }

    @Test
    fun `TaskHabitEngine computeTaskLoggedSeconds excludes planned and cancelled records`() {
        val tId = "task_spec"
        val rOk = LedgerRecord(
            id = "r1", source = "timer", layer = TimeLayer.MEASURED, createdAt = 100L, userConfirmedAt = 200L,
            category = TimeCategory.WORK, title = "Spec", date = "2026-11-18", durationSeconds = 1800L,
            startedAt = 100L, endedAt = 1800_100L, timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k1", taskId = tId
        )
        val rPlan = rOk.copy(id = "r2", layer = TimeLayer.PLANNED, status = "PLANNED", durationSeconds = 3600L, idempotencyKey = "k2")
        val rCancel = rOk.copy(id = "r3", status = "CANCELLED", durationSeconds = 900L, idempotencyKey = "k3")
        assertEquals(1800L, TaskHabitEngine.computeTaskLoggedSeconds(tId, listOf(rOk, rPlan, rCancel)))
    }

    @Test
    fun `parseQuickCaptureInput routes Inbox vs Timed Event vs Duration and applies LocalCategoryRule offline`() {
        val rule = LocalCategoryRule(
            id = "rule_1",
            keyword = "tiktok",
            category = TimeCategory.FUN,
            markWasteByDefault = true
        )

        val taskRes = TaskHabitEngine.parseQuickCaptureInput("Hoàn thiện blueprint P1", rules = listOf(rule))
        assertEquals(CaptureIntentKind.TASK_INBOX, taskRes.kind)
        assertEquals(TaskPriority.P1, taskRes.priority)

        val timedRes = TaskHabitEngine.parseQuickCaptureInput("Lướt tiktok 20:00-20:45", rules = listOf(rule))
        assertEquals(CaptureIntentKind.TIMED_EVENT, timedRes.kind)
        assertEquals(TimeCategory.FUN, timedRes.category)
        assertTrue(timedRes.markWaste)
        assertEquals("20:00", timedRes.startHm)
        assertEquals("20:45", timedRes.endHm)

        val durRes = TaskHabitEngine.parseQuickCaptureInput("Đọc sách 45p", rules = listOf(rule))
        assertEquals(CaptureIntentKind.DURATION_ONLY, durRes.kind)
        assertEquals(45, durRes.durationMinutes)
    }

    @Test
    fun `computePeakFocusWindow and PlanAccuracy compute real 14d peak and drift metrics`() {
        val dateStr = "2026-11-18"
        val dayStart = LocalDate.parse(dateStr).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        val rMorning = LedgerRecord(
            id = "r_am", source = "timer", layer = TimeLayer.MEASURED, createdAt = dayStart, userConfirmedAt = dayStart + 5400_000L,
            category = TimeCategory.WORK, title = "Deep Work", date = dateStr, durationSeconds = 5400L,
            startedAt = dayStart, endedAt = dayStart + 5400_000L, timezone = zone.id, status = "CONFIRMED",
            idempotencyKey = "k_am", plannedDurationSeconds = 3600L
        )
        val (peak, acc) = Wally20Coordinator.computeAnalyticsExtras(listOf(rMorning), dateStr, zone)
        assertNotNull(peak)
        assertTrue(peak!!.goalMinutes >= 90L)
        assertEquals(1, acc.sampleCount)
        assertTrue(acc.summaryText.contains("kế hoạch") || acc.summaryText.contains("vượt") || acc.summaryText.contains("%"))
    }

    @Test
    fun `AiReadPermissions blocks private scopes from AI coach digest when toggled off`() {
        val dateStr = "2026-11-18"
        val task = TaskItem(id = "t_secret", title = "SecretTaskAlpha", category = TimeCategory.WORK, priority = TaskPriority.P1)
        val habit = Habit(id = "h_secret", title = "SecretHabitBeta", category = TimeCategory.HEALTH)
        val goal = Goal(
            id = "g1", groupId = "gg1", targetType = GoalTargetType.CATEGORY, targetKey = "learn",
            kind = GoalKind.MIN, period = GoalPeriod.DAY, targetSeconds = 3600L,
            status = GoalVersionStatus.ACTIVE, validFrom = "2026-11-01", validTo = null, createdAt = 1L, updatedAt = 1L
        )
        val rec = LedgerRecord(
            id = "r1", source = "timer", layer = TimeLayer.MEASURED, createdAt = 100L, userConfirmedAt = 200L,
            category = TimeCategory.WORK, title = "SecretLedgerGamma", date = dateStr, durationSeconds = 3600L,
            startedAt = LocalDate.parse(dateStr).atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
            endedAt = LocalDate.parse(dateStr).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k_sec"
        )

        val restricted = AiReadPermissions(readLedger = false, readTasks = false, readHabits = false, readGoals = false)
        val digestOff = Wally20Coordinator.buildFilteredCoachDigest(
            perms = restricted, records = listOf(rec), tags = emptyList(), goals = listOf(goal),
            tasks = listOf(task), habits = listOf(habit), habitLogs = emptyList(),
            days = 1, nowMs = LocalDate.parse(dateStr).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), zone = zone
        )
        assertFalse(digestOff.text.contains("SecretTaskAlpha"))
        assertFalse(digestOff.text.contains("TASKS_OPEN="))
        assertFalse(digestOff.text.contains("HABITS_TODAY="))
        assertFalse(digestOff.text.contains("SecretLedgerGamma"))

        val allowed = AiReadPermissions(readLedger = true, readTasks = true, readHabits = true, readGoals = true)
        val digestOn = Wally20Coordinator.buildFilteredCoachDigest(
            perms = allowed, records = listOf(rec), tags = emptyList(), goals = listOf(goal),
            tasks = listOf(task), habits = listOf(habit), habitLogs = emptyList(),
            days = 1, nowMs = LocalDate.parse(dateStr).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), zone = zone
        )
        assertTrue(digestOn.text.contains("SecretTaskAlpha"))
        assertTrue(digestOn.text.contains("TASKS_OPEN=1"))
        assertTrue(digestOn.text.contains("HABITS_TODAY=0/1"))
    }

    @Test
    fun `Wally20Coordinator unifies Goal and legacy CategoryTimeBudget without duplicating migrated MAX category`() {
        val goalWorkMax = Goal(
            id = "g_work", groupId = "gg_work", targetType = GoalTargetType.CATEGORY, targetKey = "work",
            kind = GoalKind.MAX, period = GoalPeriod.WEEK, targetSeconds = 144000L,
            status = GoalVersionStatus.ACTIVE, validFrom = "2026-11-01", validTo = null, createdAt = 1L, updatedAt = 1L
        )
        val budgetWork = CategoryTimeBudget(TimeCategory.WORK, BudgetPeriod.WEEKLY, 144000L, true, 0, 1L)
        val budgetFunOnly = CategoryTimeBudget(TimeCategory.FUN, BudgetPeriod.WEEKLY, 18000L, false, 1, 1L)

        val unified = Wally20Coordinator.buildUnifiedGoalSummaries(
            goals = listOf(goalWorkMax),
            budgets = listOf(budgetWork, budgetFunOnly),
            records = emptyList(),
            tagNameOf = { null },
            today = LocalDate.of(2026, 11, 18),
            nowEpochMs = System.currentTimeMillis(),
            zone = zone
        )
        assertEquals(2, unified.size)
        assertTrue(unified.any { it.latest.targetKey == "work" && it.latest.id == "g_work" })
        assertTrue(unified.any { it.latest.targetKey == "fun" && it.latest.id == "budget_view_fun" })
    }

    @Test
    fun `LedgerJsonExporter v2 roundtrips tasks habits projects and MarkdownSummaryExporter has no secrets`() {
        val project = Project(id = "p1", name = "Wally 2.0", colorHex = TimeCategory.WORK.colorHex, createdAt = 10L, updatedAt = 10L)
        val task = TaskItem(
            id = "t1", title = "Review PR", projectId = project.id, category = TimeCategory.WORK,
            priority = TaskPriority.P1, estimatedMinutes = 40, status = TaskStatus.TODO,
            subtasks = listOf(SubtaskItem(id = "s1", taskId = "t1", title = "Check tests", isDone = true, sortOrder = 0)),
            createdAt = 10L, updatedAt = 10L
        )
        val habit = Habit(id = "h1", title = "Thiền", category = TimeCategory.REST, createdAt = 10L, updatedAt = 10L)
        val hLog = HabitLog(id = "hl1", habitId = "h1", dateLocal = "2026-11-18", completed = true, updatedAt = 10L)

        val json = LedgerJsonExporter.exportToJsonString(
            records = emptyList(),
            activeTimer = null,
            projects = listOf(project),
            tasks = listOf(task),
            habits = listOf(habit),
            habitLogs = listOf(hLog)
        )
        assertTrue(json.contains("\"schemaVersion\": 2"))
        val parsed = LedgerJsonBackupParser.parse(json)
        assertTrue(parsed is BackupParseResult.Valid)
        val payload = (parsed as BackupParseResult.Valid).payload
        assertEquals(1, payload.projects.size)
        assertEquals(1, payload.tasks.size)
        assertEquals(1, payload.tasks.first().subtasks.size)
        assertEquals(1, payload.habits.size)
        assertEquals(1, payload.habitLogs.size)

        val md = MarkdownSummaryExporter.exportDailyMarkdown(
            dateLocal = "2026-11-18",
            records = emptyList(),
            tasks = listOf(task.copy(status = TaskStatus.DONE)),
            habits = listOf(habit),
            habitLogs = listOf(hLog),
            dailyReview = null,
            efficiency = null,
            zoneId = zone
        )
        assertTrue(md.contains("2026-11-18"))
        assertTrue(md.contains("Review PR"))
        assertTrue(md.contains("Thiền"))
        assertFalse(md.contains("apiKey"))
        assertFalse(md.contains("sk-"))
    }

    @Test
    fun `CustomizationSettings defaults keep Kanban and Eisenhower off to prevent bloat`() {
        val defaults = CustomizationSettings()
        assertFalse(defaults.enableKanban)
        assertFalse(defaults.enableEisenhower)
        assertTrue(defaults.enableHabits)
        assertTrue(defaults.enablePomodoro)
        assertTrue(defaults.enableAiAssistant)
    }

    @Test
    fun `AndroidManifest retains scoped permissions without invasive accessibility or usage stats and 4 tabs exist`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertFalse(manifest.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"))
        assertFalse(manifest.contains("PACKAGE_USAGE_STATS"))
        assertEquals(listOf(AppTab.SCHEDULE, AppTab.TASKS, AppTab.FOCUS, AppTab.MORE), AppTab.entries)
    }

    @Test
    fun `Wally Chat overlay is exposed via TopBar, Tools hub without floating FAB, and Settings separated cleanly`() {
        val main = File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText()
        val topBar = File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        val insights = File("src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt").readText()
        val profile = File("src/main/java/com/timewallet/mvp/ui/screens/ProfileSettingsScreen.kt").readText()

        assertFalse(main.contains("Mở nhanh Chat với Wally"))
        assertFalse(main.contains("floatingActionButton ="))
        assertTrue(main.contains("WallyAiOverlay("))
        assertTrue(topBar.contains("Mở trợ lý Wally AI"))
        assertTrue(topBar.contains(".weight(1f, fill = false)"))
        assertTrue(insights.contains("Chat với Wally AI"))
        assertFalse(insights.contains("Quyền đọc dữ liệu của AI"))
        assertTrue(profile.contains("Chủ đề giao diện"))
        assertTrue(profile.contains("Quyền đọc dữ liệu của AI"))
        assertTrue(profile.contains("Quy tắc phân loại cục bộ"))
    }

    @Test
    fun `Wally Inbox replanning and Task breakdown enforce AI readTasks permission and Schedule header preserves Hôm nay pill`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val sched = File("src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()

        assertTrue(vm.contains("if (!s.aiReadPermissions.readTasks)"))
        assertTrue(vm.contains("if (!_uiState.value.aiReadPermissions.readTasks)"))
        assertTrue(sched.contains("onClickLabel = \"Về hôm nay\""))
        assertTrue(sched.contains("＋ Ghi mới hoạt động"))

        val comp = File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        assertFalse(comp.contains("containerColor = Color.White"))
    }

    @Test
    fun `Schedule, Tasks, Overlay, PopOn Bubble and Tools layout enforce clean sizing and UX`() {
        val main = File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText()
        val history = File("src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt").readText()
        val sched = File("src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        val tasks = File("src/main/java/com/timewallet/mvp/ui/screens/TasksHabitsScreen.kt").readText()
        val chat = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        val insights = File("src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt").readText()

        assertTrue(main.contains("val lockOuterScroll = false"))
        assertTrue(main.contains("WallyPopOnBubble(") && main.contains("Bóng chat Wally Pop-on"))
        assertTrue(history.contains("embeddedInScrollContainer = true"))
        assertFalse(history.contains("Modifier.fillMaxWidth().weight(1f)"))
        assertTrue(sched.contains("val minColWidth = if (compactColumns) 98.dp else 112.dp"))
        assertTrue(sched.contains("Modifier.horizontalScroll(hScrollState)"))
        assertTrue(tasks.contains("FlowRow(") && tasks.contains(".verticalScroll(rememberScrollState())"))
        assertFalse(tasks.contains("width(248.dp)"))
        assertTrue(chat.contains(".imePadding()"))
        assertTrue(insights.contains("ToolGridTile(") && insights.contains("GroupedToolRow("))
    }
}
