package com.timewallet.mvp

import com.timewallet.mvp.data.ai.WallyTodoAiPlanner
import com.timewallet.mvp.data.local.TaskEntity
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.PausedTimerContinuation
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiTodoAnalysisReport
import com.timewallet.mvp.domain.usecase.AiTodoEngine
import com.timewallet.mvp.domain.usecase.AiTodoProposalKind
import com.timewallet.mvp.domain.usecase.CurrentWorkMode
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.domain.usecase.DerivedStatsCalculator
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.domain.usecase.TodoRiskKind
import com.timewallet.mvp.domain.usecase.WallyHubSnapshotEngine
import com.timewallet.mvp.domain.usecase.fingerprint
import kotlinx.coroutines.runBlocking
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
import java.time.ZonedDateTime

class V220FloatingWidgetAndAiTodoTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun projectFile(pathFromRoot: String): File {
        val direct = File(pathFromRoot)
        if (direct.exists()) return direct
        val withoutApp = File(pathFromRoot.removePrefix("app/"))
        if (withoutApp.exists()) return withoutApp
        return direct
    }

    private fun sampleRecord(
        id: String,
        title: String,
        category: TimeCategory,
        layer: TimeLayer,
        date: String,
        durationSeconds: Long,
        startedAt: Long,
        endedAt: Long,
        status: String = if (layer == TimeLayer.PLANNED) "PLANNED" else "CONFIRMED"
    ): LedgerRecord = LedgerRecord(
        id = id,
        source = "manual",
        layer = layer,
        createdAt = startedAt,
        userConfirmedAt = startedAt,
        category = category,
        title = title,
        date = date,
        durationSeconds = durationSeconds,
        startedAt = startedAt,
        endedAt = endedAt,
        timezone = zone.id,
        status = status,
        idempotencyKey = "k_$id"
    )

    @Test
    fun naturalTaskParser_extractsDueDateTimeDurationAndPriority() {
        val today = LocalDate.of(2026, 11, 23)
        val parsed = AiTodoEngine.parseNaturalTaskInput(
            rawInput = "Nộp báo cáo quý mai 15h 45p uu tien cao",
            today = today
        )
        assertNotNull(parsed)
        assertEquals("2026-11-24", parsed!!.dueDateLocal)
        assertEquals("15:00", parsed.dueTimeLocal)
        assertEquals(45, parsed.estimatedMinutes)
        assertEquals(TaskPriority.P1, parsed.priority)
        assertTrue(parsed.title.isNotBlank())
    }

    @Test
    fun workloadAndRiskEngine_detectsOverdueConflictPostponeAndOverflow() {
        val today = "2026-11-23"
        val nowMs = ZonedDateTime.of(2026, 11, 23, 19, 30, 0, 0, zone).toInstant().toEpochMilli()

        val overdueTask = TaskItem(
            id = "t_overdue",
            title = "Gửi hóa đơn tháng 10",
            category = TimeCategory.WORK,
            priority = TaskPriority.P1,
            dueDateLocal = "2026-11-21",
            estimatedMinutes = 30
        )
        val conflictTask = TaskItem(
            id = "t_conflict",
            title = "Họp đồng bộ sprint",
            category = TimeCategory.WORK,
            priority = TaskPriority.P1,
            dueDateLocal = today,
            dueTimeLocal = "20:00",
            estimatedMinutes = 60,
            postponedCount = 3
        )
        val heavyTask = TaskItem(
            id = "t_heavy",
            title = "Ôn thi hệ thống",
            category = TimeCategory.LEARN,
            priority = TaskPriority.P1,
            dueDateLocal = today,
            estimatedMinutes = 240
        )

        val meetingStart = ZonedDateTime.of(2026, 11, 23, 20, 15, 0, 0, zone).toInstant().toEpochMilli()
        val meetingEnd = ZonedDateTime.of(2026, 11, 23, 21, 0, 0, 0, zone).toInstant().toEpochMilli()
        val existingBlock = sampleRecord(
            id = "rec_planned",
            title = "Khối làm việc sâu",
            category = TimeCategory.WORK,
            layer = TimeLayer.PLANNED,
            date = today,
            durationSeconds = 2700L,
            startedAt = meetingStart,
            endedAt = meetingEnd
        )

        val alerts = AiTodoEngine.analyzeWorkloadAndRisks(
            tasks = listOf(overdueTask, conflictTask, heavyTask),
            records = listOf(existingBlock),
            activeTimer = null,
            todayLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )

        val kinds = alerts.map { it.kind }.toSet()
        assertTrue(TodoRiskKind.OVERDUE in kinds)
        assertTrue(TodoRiskKind.SCHEDULE_CONFLICT in kinds)
        assertTrue(TodoRiskKind.REPEATED_POSTPONE in kinds)
        assertTrue(TodoRiskKind.WORKLOAD_OVERFLOW in kinds)
    }

    @Test
    fun todoProposals_alwaysRequireConfirmation_andRevalidateOnScheduleChange() {
        val today = "2026-11-23"
        val nowMs = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val task = TaskItem(
            id = "t_sched",
            title = "Thiết kế kiến trúc Widget",
            category = TimeCategory.WORK,
            priority = TaskPriority.P1,
            estimatedMinutes = 60,
            dueDateLocal = today,
            postponedCount = 2
        )

        val proposals = AiTodoEngine.buildDeterministicTodoProposals(
            tasks = listOf(task),
            records = emptyList(),
            activeTimer = null,
            todayLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertTrue(proposals.isNotEmpty())
        assertTrue(proposals.all { it.needsConfirmation })

        val scheduleProp = proposals.firstOrNull { it.kind == AiTodoProposalKind.SCHEDULE_INTO_GAP }
        assertNotNull(scheduleProp)
        val startParts = scheduleProp!!.startHm!!.split(":")
        val endParts = scheduleProp.endHm!!.split(":")
        val sMs = ZonedDateTime.of(2026, 11, 23, startParts[0].toInt(), startParts[1].toInt(), 0, 0, zone).toInstant().toEpochMilli()
        val eMs = ZonedDateTime.of(2026, 11, 23, endParts[0].toInt(), endParts[1].toInt(), 0, 0, zone).toInstant().toEpochMilli()

        val blockingRecord = sampleRecord(
            id = "rec_block",
            title = "Khối họp đột xuất",
            category = TimeCategory.WORK,
            layer = TimeLayer.PLANNED,
            date = today,
            durationSeconds = (eMs - sMs) / 1000L,
            startedAt = sMs,
            endedAt = eMs
        )

        val revalidated = AiTodoEngine.revalidateTodoProposals(
            proposals = proposals,
            tasks = listOf(task),
            records = listOf(blockingRecord),
            activeTimer = null,
            todayLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        val updatedSched = revalidated.first { it.id == scheduleProp.id }
        assertNotNull(updatedSched.conflictWarningVi)
        assertTrue(updatedSched.needsConfirmation)
    }

    @Test
    fun hubSnapshotEngine_sharesSsotAndExcludesPlannedFromActual() {
        val today = "2026-11-23"
        val nowMs = ZonedDateTime.of(2026, 11, 23, 10, 30, 0, 0, zone).toInstant().toEpochMilli()
        val recActual = sampleRecord(
            id = "r_actual",
            title = "Code Domain",
            category = TimeCategory.WORK,
            layer = TimeLayer.MEASURED,
            date = today,
            durationSeconds = 3600L,
            startedAt = ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
            endedAt = ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        )
        val recPlanned = sampleRecord(
            id = "r_planned",
            title = "Review chiều",
            category = TimeCategory.WORK,
            layer = TimeLayer.PLANNED,
            date = today,
            durationSeconds = 7200L,
            startedAt = ZonedDateTime.of(2026, 11, 23, 14, 0, 0, 0, zone).toInstant().toEpochMilli(),
            endedAt = ZonedDateTime.of(2026, 11, 23, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
        )
        val paused = PausedTimerContinuation(
            title = "Viết spec Widget",
            categoryKey = TimeCategory.WORK.key,
            pausedAtEpochMs = nowMs - 60_000L,
            accumulatedSeconds = 1200L
        )
        val todo = TaskItem(
            id = "todo_1",
            title = "Kiểm tra APK release",
            category = TimeCategory.WORK,
            priority = TaskPriority.P1,
            dueDateLocal = today
        )

        val snap = WallyHubSnapshotEngine.build(
            records = listOf(recActual, recPlanned),
            tasks = listOf(todo),
            activeTimer = null,
            pausedTimer = paused,
            nowEpochMs = nowMs,
            zoneId = zone,
            aiReadPermissions = AiReadPermissions(),
            cachedAiReport = null,
            aiEnabled = false
        )

        assertEquals(CurrentWorkMode.PAUSED_TIMER, snap.current?.mode)
        assertEquals("Viết spec Widget", snap.current?.title)
        assertEquals(60, snap.actualRecordedMinutes)
        assertTrue(snap.plannedRemainingMinutes >= 120)
        assertEquals("Review chiều", snap.upcoming?.title)
        assertFalse(snap.isCloudAiInsight)
        assertEquals(1, snap.openTodoCount)

        val active = ActiveTimerSession(
            title = "Chạy đo thực tế",
            categoryKey = TimeCategory.WORK.key,
            startedAtEpochMs = nowMs - 120_000L,
            startedElapsedRealtimeMs = 10_000L,
            timezone = zone.id
        )
        val snapActive = WallyHubSnapshotEngine.build(
            records = listOf(recActual, recPlanned),
            tasks = listOf(todo),
            activeTimer = active,
            pausedTimer = paused,
            nowEpochMs = nowMs,
            nowElapsedRealtimeMs = 130_000L,
            zoneId = zone,
            aiReadPermissions = AiReadPermissions(),
            cachedAiReport = AiTodoAnalysisReport(
                generatedAtEpochMs = nowMs,
                dateLocal = today,
                fromCloudAi = true,
                summaryVi = "Ưu tiên hoàn thành Kiểm tra APK release trước 14:00.",
                orderedTaskIds = listOf("todo_1"),
                risks = emptyList(),
                proposals = emptyList(),
                permissionsFingerprint = AiReadPermissions().fingerprint()
            ),
            aiEnabled = true
        )
        assertEquals(CurrentWorkMode.ACTIVE_TIMER, snapActive.current?.mode)
        assertTrue(snapActive.isCloudAiInsight)
    }

    @Test
    fun geminiTodoPlanner_respectsZeroLeakPermissions_andParsesConfirmedProposals() = runBlocking {
        val blocked = WallyTodoAiPlanner.analyzeTodoPlan(
            tasks = emptyList(),
            records = emptyList(),
            activeTimer = null,
            todayLocal = "2026-11-23",
            nowTime = LocalTime.of(10, 0),
            zoneId = zone,
            aiReadPermissions = AiReadPermissions(readTasks = false),
            optInEnabled = true,
            apiKey = "sk-test-1234"
        )
        assertTrue(blocked is WallyTodoAiPlanner.TodoPlanOutcome.Blocked)

        val task = TaskItem(
            id = "task_ai_1",
            title = "Chuẩn bị tài liệu phát hành",
            category = TimeCategory.WORK,
            priority = TaskPriority.P1,
            dueDateLocal = "2026-11-23",
            estimatedMinutes = 45
        )
        val nowMs = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val mockOpenAiJson = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"summary\":\"Nên xử lý tài liệu lúc 15:00 khi lịch còn trống.\",\"orderedTaskIds\":[\"task_ai_1\"],\"proposals\":[{\"kind\":\"SCHEDULE_INTO_GAP\",\"taskId\":\"task_ai_1\",\"reason\":\"Khung trống 45p buổi chiều\",\"targetDateLocal\":\"2026-11-23\",\"startHm\":\"15:00\",\"endHm\":\"15:45\",\"suggestedPriority\":\"P1\",\"suggestedSubtasks\":[]}]}"
                  }
                }
              ]
            }
        """.trimIndent()

        val report = WallyTodoAiPlanner.parseTodoPlanJson(
            responseBody = mockOpenAiJson,
            openTasks = listOf(task),
            records = emptyList(),
            activeTimer = null,
            todayLocal = "2026-11-23",
            nowEpochMs = nowMs,
            zoneId = zone,
            fallbackRisks = emptyList(),
            permsFingerprint = AiReadPermissions().fingerprint()
        )
        assertTrue(report.fromCloudAi)
        assertEquals(1, report.proposals.size)
        assertTrue(report.proposals.all { it.needsConfirmation })
        assertEquals("15:00", report.proposals.first().startHm)
        assertEquals("15:45", report.proposals.first().endHm)
    }

    @Test
    fun reportSerialization_and_taskPostponedCountRoundTrip() {
        val original = AiTodoAnalysisReport(
            generatedAtEpochMs = 1700000000000L,
            dateLocal = "2026-11-23",
            fromCloudAi = true,
            summaryVi = "Tóm tắt",
            orderedTaskIds = listOf("t1"),
            risks = emptyList(),
            proposals = emptyList(),
            permissionsFingerprint = "fp1"
        )
        val roundTripped = AiTodoEngine.deserializeReport(AiTodoEngine.serializeReport(original))
        assertNotNull(roundTripped)
        assertEquals(original.summaryVi, roundTripped!!.summaryVi)
        assertEquals(listOf("t1"), roundTripped.orderedTaskIds)

        val t = TaskItem(
            id = "t_postpone",
            title = "Gửi báo cáo",
            category = TimeCategory.WORK,
            postponedCount = 2
        )
        val back = TaskEntity.fromDomain(t).toDomain()
        assertEquals(2, back.postponedCount)
        assertTrue(projectFile("app/schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/8.json").exists())
    }

    @Test
    fun v220ManifestAndResources_containWidgetAndFloatingServiceWithoutInvasivePermissions() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("WallyHomeWidgetProvider"))
        assertTrue(manifest.contains("WallyHomeWidgetService"))
        assertTrue(manifest.contains("WallyFloatingService"))
        assertTrue(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertFalse(manifest.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"))
        assertFalse(manifest.contains("PACKAGE_USAGE_STATS"))

        assertTrue(projectFile("app/src/main/res/layout/wally_widget_small.xml").exists())
        assertTrue(projectFile("app/src/main/res/layout/wally_widget_medium.xml").exists())
        assertTrue(projectFile("app/src/main/res/layout/wally_widget_large.xml").exists())
        assertTrue(projectFile("app/src/main/res/xml/wally_home_widget_info.xml").exists())

        // UX P1 hierarchy: QuickPanel ưu tiên Đang làm -> Sắp làm -> Việc cần xử lý; AI To-do phân tách Đề xuất -> Duyệt -> Dự kiến vs Thực tế
        val floatingSrc = projectFile("app/src/main/java/com/timewallet/mvp/overlay/WallyFloatingService.kt").readText()
        assertTrue(floatingSrc.contains("QuickPanelDrawer"))
        assertTrue(floatingSrc.contains("Việc cần xử lý"))
        assertTrue(floatingSrc.contains("LocalConfiguration.current"))
        val tasksSrc = projectFile("app/src/main/java/com/timewallet/mvp/ui/screens/TasksHabitsScreen.kt").readText()
        assertTrue(tasksSrc.contains("var aiPanelExpanded by rememberSaveable"))
        assertTrue(tasksSrc.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(tasksSrc.contains("Chờ bạn duyệt"))
        assertTrue(tasksSrc.contains("Đã duyệt AI → Lịch dự kiến"))
        val schedSrc = projectFile("app/src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        assertTrue(schedSrc.contains("Về giờ hiện tại"))
        assertTrue(schedSrc.contains("Tới hoạt động kế tiếp"))
    }

    @Test
    fun `overnight 2300 to 0030 splits 60m yesterday and 30m today consistently across 24h wallet, floating panel and widget`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val yesterday = LocalDate.of(2026, 11, 24)
        val today = LocalDate.of(2026, 11, 25)
        val startMs = ZonedDateTime.of(yesterday, LocalTime.of(23, 0), zone).toInstant().toEpochMilli()
        val endMs = ZonedDateTime.of(today, LocalTime.of(0, 30), zone).toInstant().toEpochMilli()
        val checkTodayMs = ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant().toEpochMilli()

        val overnightRecord = LedgerRecord(
            id = "ov_2300_0030",
            source = "timer",
            layer = TimeLayer.MEASURED,
            createdAt = endMs,
            userConfirmedAt = endMs,
            category = TimeCategory.WORK,
            title = "Phiên xuyên đêm",
            date = yesterday.toString(),
            durationSeconds = 5400L,
            startedAt = startMs,
            endedAt = endMs,
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "ov_2300_0030"
        )

        val yesterdayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = yesterday.toString(),
            records = listOf(overnightRecord),
            zoneId = zone,
            nowEpochMs = checkTodayMs,
            activeTimer = null
        )
        val todayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = today.toString(),
            records = listOf(overnightRecord),
            zoneId = zone,
            nowEpochMs = checkTodayMs,
            activeTimer = null
        )
        val todaySnapshot = WallyHubSnapshotEngine.build(
            records = listOf(overnightRecord),
            tasks = emptyList(),
            activeTimer = null,
            pausedTimer = null,
            nowEpochMs = checkTodayMs,
            zoneId = zone
        )

        assertEquals(3600L, DashboardSummary.from(yesterdayStats).recordedSeconds)
        assertEquals(1800L, DashboardSummary.from(todayStats).recordedSeconds)
        assertEquals("1h 00", LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(3600L))
        assertEquals("0h 30", LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(1800L))
        assertEquals("1h", LedgerAnalyticsCalculator.formatCompactDurationSeconds(3600L))
        assertEquals("30p", LedgerAnalyticsCalculator.formatCompactDurationSeconds(1800L))
        assertEquals(30, todaySnapshot.actualRecordedMinutes)
        assertEquals("30p", TimeRange.formatDuration(todaySnapshot.actualRecordedMinutes))

        // Đồng hồ đang chạy xuyên qua 00:00 (23:00 hôm trước -> 00:30 hôm sau):
        val runningTimer = ActiveTimerSession(
            title = "Đo xuyên đêm",
            categoryKey = TimeCategory.WORK.key,
            startedAtEpochMs = startMs,
            startedElapsedRealtimeMs = 1_000L,
            timezone = zone.id
        )
        val runningYesterdayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = yesterday.toString(),
            records = emptyList(),
            zoneId = zone,
            nowEpochMs = endMs,
            activeTimer = runningTimer
        )
        val runningTodayStats = DerivedStatsCalculator.compute(
            records = emptyList(),
            budgets = emptyList(),
            dailyReviews = emptyList(),
            activityNotes = emptyList(),
            reviewDateOverride = null,
            selectedReviewDate = today.toString(),
            activeTimer = runningTimer,
            preferredFocusCategory = TimeCategory.WORK,
            consecutiveBreakRolls = 0,
            zone = zone,
            nowMs = endMs
        ).dayStats
        val runningSnapshot = WallyHubSnapshotEngine.build(
            records = emptyList(),
            tasks = emptyList(),
            activeTimer = runningTimer,
            pausedTimer = null,
            nowEpochMs = endMs,
            nowElapsedRealtimeMs = 1_000L + 5_400_000L,
            zoneId = zone
        )

        assertEquals(3600L, DashboardSummary.from(runningYesterdayStats).recordedSeconds)
        assertEquals(1800L, DashboardSummary.from(runningTodayStats).recordedSeconds)
        assertEquals(30, runningSnapshot.actualRecordedMinutes)
    }

    @Test
    fun `active timer synchronizes exact total minutes across 24h wallet, floating panel and widget data`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val today = LocalDate.of(2026, 11, 25)
        val morningStart = ZonedDateTime.of(today, LocalTime.of(8, 0), zone).toInstant().toEpochMilli()
        val morningEnd = ZonedDateTime.of(today, LocalTime.of(8, 20), zone).toInstant().toEpochMilli()
        val timerStart = ZonedDateTime.of(today, LocalTime.of(9, 0, 0), zone).toInstant().toEpochMilli()
        val nowMs = ZonedDateTime.of(today, LocalTime.of(9, 15, 35), zone).toInstant().toEpochMilli()
        val planStart = ZonedDateTime.of(today, LocalTime.of(14, 0), zone).toInstant().toEpochMilli()
        val planEnd = ZonedDateTime.of(today, LocalTime.of(15, 0), zone).toInstant().toEpochMilli()

        val morningRecord = LedgerRecord(
            id = "morning_20p",
            source = "manual",
            layer = TimeLayer.MEASURED,
            createdAt = morningEnd,
            userConfirmedAt = morningEnd,
            category = TimeCategory.WORK,
            title = "Ghi buổi sáng",
            date = today.toString(),
            durationSeconds = 1200L,
            startedAt = morningStart,
            endedAt = morningEnd,
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "morning_20p"
        )
        val futurePlan = LedgerRecord(
            id = "future_plan",
            source = "manual",
            layer = TimeLayer.PLANNED,
            createdAt = nowMs - 10_000L,
            userConfirmedAt = nowMs - 10_000L,
            category = TimeCategory.WORK,
            title = "Lịch chiều",
            date = today.toString(),
            durationSeconds = 3600L,
            startedAt = planStart,
            endedAt = planEnd,
            timezone = zone.id,
            status = "PLANNED",
            idempotencyKey = "future_plan",
            confirmationStateRaw = "PLANNED",
            plannedStartedAt = planStart,
            plannedEndedAt = planEnd,
            plannedDurationSeconds = 3600L
        )
        val activeTimer = ActiveTimerSession(
            title = "Đo trực tiếp",
            categoryKey = TimeCategory.WORK.key,
            startedAtEpochMs = timerStart,
            startedElapsedRealtimeMs = 5_000L,
            timezone = zone.id
        )

        val walletStats = DerivedStatsCalculator.compute(
            records = listOf(morningRecord, futurePlan),
            budgets = emptyList(),
            dailyReviews = emptyList(),
            activityNotes = emptyList(),
            reviewDateOverride = null,
            selectedReviewDate = today.toString(),
            activeTimer = activeTimer,
            preferredFocusCategory = TimeCategory.WORK,
            consecutiveBreakRolls = 0,
            zone = zone,
            nowMs = nowMs
        ).dayStats
        val walletRecordedSeconds = DashboardSummary.from(walletStats).recordedSeconds
        val walletFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(walletRecordedSeconds)
        val walletCompact = LedgerAnalyticsCalculator.formatCompactDurationSeconds(walletRecordedSeconds)
        val snapshot = WallyHubSnapshotEngine.build(
            records = listOf(morningRecord, futurePlan),
            tasks = emptyList(),
            activeTimer = activeTimer,
            pausedTimer = null,
            nowEpochMs = nowMs,
            nowElapsedRealtimeMs = 5_000L + (nowMs - timerStart),
            zoneId = zone
        )

        // 20m + 15m35s = 35m35s (2135s) -> làm tròn chung thành 36p (0h 36 / 36p), không cộng PLANNED
        assertEquals(2135L, walletRecordedSeconds)
        assertEquals("0h 36", walletFormatted)
        assertEquals("36p", walletCompact)
        assertEquals(36, snapshot.actualRecordedMinutes)
        assertEquals(walletCompact, TimeRange.formatDuration(snapshot.actualRecordedMinutes))

        val floatingSrc = projectFile("app/src/main/java/com/timewallet/mvp/overlay/WallyFloatingService.kt").readText()
        assertTrue(floatingSrc.contains("Ghi \${TimeRange.formatDuration(snapshot.actualRecordedMinutes)}"))
        val vmSrc = projectFile("app/src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        assertTrue(vmSrc.contains("val fastDayStats = LedgerAnalyticsCalculator.computeDayStats("))
        val widgetSrc = projectFile("app/src/main/java/com/timewallet/mvp/widget/WallyHomeWidgetProvider.kt").readText()
        assertTrue(widgetSrc.contains("snapshot.actualRecordedMinutes"))
    }

    @Test
    fun `completing linked todo hides it from floating panel and widget upcoming while preserving planned block on calendar`() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val today = LocalDate.of(2026, 11, 25)
        val todayStr = today.toString()
        val nowMorningMs = ZonedDateTime.of(today, LocalTime.of(10, 0), zone).toInstant().toEpochMilli()
        val p1Start = ZonedDateTime.of(today, LocalTime.of(11, 0), zone).toInstant().toEpochMilli()
        val p1End = ZonedDateTime.of(today, LocalTime.of(12, 0), zone).toInstant().toEpochMilli()
        val p2Start = ZonedDateTime.of(today, LocalTime.of(14, 0), zone).toInstant().toEpochMilli()
        val p2End = ZonedDateTime.of(today, LocalTime.of(15, 0), zone).toInstant().toEpochMilli()
        val duringP1Ms = ZonedDateTime.of(today, LocalTime.of(11, 15), zone).toInstant().toEpochMilli()

        val todoTask = TaskItem(
            id = "task_scheduled_done",
            title = "Hoàn thiện tài liệu",
            category = TimeCategory.WORK,
            priority = TaskPriority.P1,
            dueDateLocal = todayStr,
            dueTimeLocal = "11:00",
            estimatedMinutes = 60,
            status = TaskStatus.TODO
        )
        val plannedForTask = LedgerRecord(
            id = "plan_for_done_task",
            source = "manual",
            layer = TimeLayer.PLANNED,
            createdAt = nowMorningMs - 60_000L,
            userConfirmedAt = nowMorningMs - 60_000L,
            category = TimeCategory.WORK,
            title = "Hoàn thiện tài liệu",
            date = todayStr,
            durationSeconds = 3600L,
            startedAt = p1Start,
            endedAt = p1End,
            timezone = zone.id,
            status = "PLANNED",
            idempotencyKey = "plan_for_done_task",
            confirmationStateRaw = "PLANNED",
            plannedStartedAt = p1Start,
            plannedEndedAt = p1End,
            plannedDurationSeconds = 3600L,
            taskId = todoTask.id
        )
        val laterOpenPlan = LedgerRecord(
            id = "plan_open_later",
            source = "manual",
            layer = TimeLayer.PLANNED,
            createdAt = nowMorningMs - 30_000L,
            userConfirmedAt = nowMorningMs - 30_000L,
            category = TimeCategory.LEARN,
            title = "Review kiến trúc",
            date = todayStr,
            durationSeconds = 3600L,
            startedAt = p2Start,
            endedAt = p2End,
            timezone = zone.id,
            status = "PLANNED",
            idempotencyKey = "plan_open_later",
            confirmationStateRaw = "PLANNED",
            plannedStartedAt = p2Start,
            plannedEndedAt = p2End,
            plannedDurationSeconds = 3600L,
            taskId = null
        )

        val beforeDone = WallyHubSnapshotEngine.build(
            records = listOf(plannedForTask),
            tasks = listOf(todoTask),
            activeTimer = null,
            pausedTimer = null,
            nowEpochMs = nowMorningMs,
            zoneId = zone
        )
        assertEquals("Hoàn thiện tài liệu", beforeDone.upcoming?.title)

        val doneTask = todoTask.copy(status = TaskStatus.DONE, completedAt = nowMorningMs)
        val afterDoneSingle = WallyHubSnapshotEngine.build(
            records = listOf(plannedForTask),
            tasks = listOf(doneTask),
            activeTimer = null,
            pausedTimer = null,
            nowEpochMs = nowMorningMs,
            zoneId = zone
        )
        assertNull("Việc đã xong không được hiện ở Sắp làm trên Bảng nổi/Widget", afterDoneSingle.upcoming)
        assertEquals(0, afterDoneSingle.plannedRemainingMinutes)

        val afterDoneDuringSlot = WallyHubSnapshotEngine.build(
            records = listOf(plannedForTask),
            tasks = listOf(doneTask),
            activeTimer = null,
            pausedTimer = null,
            nowEpochMs = duringP1Ms,
            zoneId = zone
        )
        assertNull("Việc đã xong không hiển thị thẻ Theo lịch ở Đang làm", afterDoneDuringSlot.current)

        val afterDoneWithLater = WallyHubSnapshotEngine.build(
            records = listOf(plannedForTask, laterOpenPlan),
            tasks = listOf(doneTask),
            activeTimer = null,
            pausedTimer = null,
            nowEpochMs = nowMorningMs,
            zoneId = zone
        )
        assertEquals("Review kiến trúc", afterDoneWithLater.upcoming?.title)
        assertEquals("14:00", afterDoneWithLater.upcoming?.startHm)

        // Trên Lịch, khung 11:00–12:00 vẫn giữ nguyên
        val calendarDay = ScheduleGridEngine.buildDayScheduleModel(
            date = today,
            records = listOf(plannedForTask, laterOpenPlan),
            activeTimer = null,
            nowEpochMs = nowMorningMs,
            zone = zone
        )
        assertTrue(
            "Khung đã xếp trên Lịch phải còn nguyên sau khi tích hoàn thành To-do",
            calendarDay.timedBlocks.any { it.recordId == plannedForTask.id }
        )
    }
}
