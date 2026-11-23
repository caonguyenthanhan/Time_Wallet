package com.timewallet.mvp

import com.timewallet.mvp.data.ai.WallyTodoAiPlanner
import com.timewallet.mvp.data.local.TaskEntity
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.PausedTimerContinuation
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiTodoAnalysisReport
import com.timewallet.mvp.domain.usecase.AiTodoEngine
import com.timewallet.mvp.domain.usecase.AiTodoProposalKind
import com.timewallet.mvp.domain.usecase.CurrentWorkMode
import com.timewallet.mvp.domain.usecase.TodoRiskKind
import com.timewallet.mvp.domain.usecase.WallyHubSnapshotEngine
import com.timewallet.mvp.domain.usecase.fingerprint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
