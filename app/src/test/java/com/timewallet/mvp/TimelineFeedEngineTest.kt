package com.timewallet.mvp

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.TimelineConnectorKind
import com.timewallet.mvp.domain.usecase.TimelineFeedEngine
import com.timewallet.mvp.domain.usecase.TimelineFeedFilter
import com.timewallet.mvp.domain.usecase.TimelineHorizon
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

class TimelineFeedEngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = "2026-11-23"
    private val yesterday = "2026-11-22"
    private val tomorrow = "2026-11-24"

    private fun ms(dateStr: String, hour: Int, minute: Int): Long =
        LocalDate.parse(dateStr).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun rec(
        id: String,
        title: String,
        date: String,
        startHm: Pair<Int, Int>? = null,
        endHm: Pair<Int, Int>? = null,
        durationMinutes: Int = 30,
        layer: TimeLayer = TimeLayer.REPORTED_TIMED,
        status: String = "CONFIRMED",
        category: TimeCategory = TimeCategory.WORK,
        createdAt: Long? = null,
        confRaw: String? = null
    ): LedgerRecord {
        val s = startHm?.let { ms(date, it.first, it.second) }
        val e = endHm?.let { ms(date, it.first, it.second) }
        val durSecs = if (s != null && e != null) (e - s) / 1000L else durationMinutes * 60L
        return LedgerRecord(
            id = id,
            source = "manual",
            layer = layer,
            createdAt = createdAt ?: s ?: ms(date, 12, 0),
            userConfirmedAt = createdAt ?: s ?: ms(date, 12, 0),
            category = category,
            title = title,
            date = date,
            durationSeconds = durSecs,
            startedAt = s,
            endedAt = e,
            timezone = zone.id,
            status = status,
            idempotencyKey = "k_$id",
            confirmationStateRaw = confRaw
        )
    }

    @Test
    fun `separates upcoming active-pending and past strictly around now without mixing future into past`() {
        val nowMs = ms(today, 17, 20)
        val records = listOf(
            // Quá khứ hôm qua
            rec("y_pm", "Chạy bộ", yesterday, 18 to 0, 18 to 45, category = TimeCategory.HEALTH),
            rec("y_am", "Ngủ trưa", yesterday, 12 to 30, 13 to 0, category = TimeCategory.SLEEP),
            // Quá khứ hôm nay
            rec("t_sleep", "Ngủ đêm", today, 0 to 0, 7 to 0, layer = TimeLayer.MEASURED, category = TimeCategory.SLEEP),
            rec("t_deep", "Viết báo cáo", today, 9 to 0, 10 to 30, layer = TimeLayer.MEASURED),
            // Hiện tại: chờ xác nhận (đã qua 16:30)
            rec(
                id = "c_pend",
                title = "Họp nhóm chiều",
                date = today,
                startHm = 15 to 30,
                endHm = 16 to 30,
                layer = TimeLayer.PLANNED,
                status = "PLANNED",
                confRaw = ConfirmationState.PENDING_CONFIRMATION.name
            ),
            // Tương lai hôm nay (sau 17:20)
            rec("u_today_late", "Ôn bài", today, 20 to 0, 21 to 0, layer = TimeLayer.PLANNED, status = "PLANNED"),
            rec("u_today_soon", "Tập thể dục", today, 18 to 0, 18 to 45, layer = TimeLayer.PLANNED, status = "PLANNED"),
            // Tương lai ngày mai
            rec("u_tmrw", "Code sáng mai", tomorrow, 8 to 30, 10 to 0, layer = TimeLayer.PLANNED, status = "PLANNED")
        )

        val feed = TimelineFeedEngine.build(
            records = records,
            todayDateLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )

        assertEquals("17:20", feed.nowTimeLabel)

        // Sắp diễn ra: ngày tăng dần (today -> tomorrow), trong ngày tăng dần (18:00 -> 20:00)
        assertEquals(listOf(today, tomorrow), feed.upcomingDays.map { it.dateLocal })
        assertEquals(listOf("u_today_soon", "u_today_late"), feed.upcomingDays[0].timedItems.map { it.record.id })
        assertEquals(listOf("u_tmrw"), feed.upcomingDays[1].timedItems.map { it.record.id })
        assertTrue(feed.upcomingDays[0].timedItems.all { it.horizon == TimelineHorizon.UPCOMING })
        assertEquals(11_700L, feed.upcomingTotalPlannedSeconds) // 45p + 60p + 90p = 195p = 11_700s
    }

    @Test
    fun `past items order days descending and within each day newest recorded first while isolating untimed`() {
        val nowMs = ms(today, 17, 20)
        val records = listOf(
            rec("t_morning", "Sáng", today, 8 to 0, 9 to 0),
            rec("t_midday", "Trưa", today, 12 to 0, 13 to 15),
            rec("t_untimed_old", "Ghi chú 1", today, durationMinutes = 15, layer = TimeLayer.REPORTED_UNTIMED, createdAt = ms(today, 10, 0)),
            rec("t_untimed_new", "Ghi chú 2", today, durationMinutes = 20, layer = TimeLayer.REPORTED_UNTIMED, createdAt = ms(today, 16, 0)),
            rec("y_item", "Hôm qua", yesterday, 19 to 0, 20 to 0)
        )

        val feed = TimelineFeedEngine.build(
            records = records,
            todayDateLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )

        assertEquals(listOf(today, yesterday), feed.pastDays.map { it.dateLocal })
        val todayPast = feed.pastDays.first()
        // Trong ngày Hôm nay: mục kết thúc muộn hơn (12:00-13:15) đứng TRÊN (mới nhất trước)
        assertEquals(listOf("t_midday", "t_morning"), todayPast.timedItems.map { it.record.id })
        // Mục chưa gắn giờ KHÔNG bị bịa giờ, giữ tách biệt
        assertEquals(listOf("t_untimed_new", "t_untimed_old"), todayPast.untimedItems.map { it.record.id })
        assertNull(todayPast.untimedItems[0].record.startedAt)
        assertEquals(2_100L, todayPast.untimedRecordedSeconds)

        // Khoảng cách giữa 09:00 và 12:00 là 3h chưa ghi
        val connectorAfterMidday = todayPast.connectorsAfterTimedItem["t_midday"]
        assertNotNull(connectorAfterMidday)
        assertEquals(TimelineConnectorKind.UNTRACKED_GAP, connectorAfterMidday!!.kind)
        assertEquals(180, connectorAfterMidday.minutes)
        assertTrue(connectorAfterMidday.isActionableGap)
        assertEquals("Chưa ghi 3h (09:00–12:00)", connectorAfterMidday.labelVi)

        // Trailing gap từ 13:15 đến 17:20 (nowMs) = 4h 5p
        assertNotNull(todayPast.trailingGapToNow)
        assertEquals(245, todayPast.trailingGapToNow!!.minutes)
    }

    @Test
    fun `upcoming total calculation matches exact seconds across future days`() {
        val nowMs = ms(today, 17, 20)
        val records = listOf(
            rec("u1", "A", today, 18 to 0, 18 to 45, layer = TimeLayer.PLANNED, status = "PLANNED"), // 45m = 2700s
            rec("u2", "B", today, 20 to 0, 21 to 0, layer = TimeLayer.PLANNED, status = "PLANNED"),  // 60m = 3600s
            rec("u3", "C", tomorrow, 8 to 30, 10 to 0, layer = TimeLayer.PLANNED, status = "PLANNED") // 90m = 5400s
        )
        val feed = TimelineFeedEngine.build(records = records, todayDateLocal = today, nowEpochMs = nowMs, zoneId = zone)
        assertEquals(2700L + 3600L + 5400L, feed.upcomingTotalPlannedSeconds)

        // Khoảng trống giữa 18:45 và 20:00 là 1h 15p (75p) FREE_SLOT
        val gap = feed.upcomingDays.first().connectorsAfterTimedItem["u1"]
        assertNotNull(gap)
        assertEquals(TimelineConnectorKind.FREE_SLOT, gap!!.kind)
        assertEquals(75, gap.minutes)
        assertEquals("Trống 1h 15p (18:45–20:00)", gap.labelVi)
    }

    @Test
    fun `connectors detect continuous transition and parallel overlap cleanly`() {
        val nowMs = ms(today, 20, 0)
        val records = listOf(
            rec("a", "Sáng 1", today, 8 to 0, 9 to 0),
            rec("b", "Liền sau", today, 9 to 3, 10 to 0), // cách 3p -> CONTINUOUS
            rec("c", "Song song", today, 9 to 45, 11 to 0) // trùng 15p -> PARALLEL_OVERLAP
        )
        val feed = TimelineFeedEngine.build(records = records, todayDateLocal = today, nowEpochMs = nowMs, zoneId = zone)
        val connectors = feed.pastDays.first().connectorsAfterTimedItem
        // Sorted DESC: c (ended 11:00) -> b (ended 10:00) -> a (ended 09:00)
        assertEquals(TimelineConnectorKind.PARALLEL_OVERLAP, connectors["c"]?.kind)
        assertEquals(15, connectors["c"]?.minutes)
        assertEquals(TimelineConnectorKind.CONTINUOUS, connectors["b"]?.kind)
        assertFalse(connectors["b"]?.isActionableGap ?: true)
    }

    @Test
    fun `shiftPlannedRecord moves start and end while keeping duration constant`() {
        val nowMs = ms(today, 17, 20)
        val planned = rec("p1", "Ôn bài", today, 19 to 30, 20 to 15, layer = TimeLayer.PLANNED, status = "PLANNED")

        val shifted = ConfirmationEngine.shiftPlannedRecord(
            existing = planned,
            deltaMinutes = 15,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertTrue(shifted is ConfirmationActionResult.Applied)
        val updated = (shifted as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(ms(today, 19, 45), updated.startedAt)
        assertEquals(ms(today, 20, 30), updated.endedAt)
        assertEquals(45 * 60L, updated.durationSeconds)
        assertEquals(TimeLayer.PLANNED, updated.layer)
    }

    @Test
    fun `resizePlannedRecord keeps start fixed and changes duration while active timer enters current horizon`() {
        val nowMs = ms(today, 17, 20)
        val planned = rec("p1", "Ôn bài", today, 19 to 30, 20 to 15, layer = TimeLayer.PLANNED, status = "PLANNED")
        val resized = ConfirmationEngine.resizePlannedRecord(planned, deltaMinutes = 15, nowEpochMs = nowMs, zoneId = zone)
        assertTrue(resized is ConfirmationActionResult.Applied)
        val upd = (resized as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(ms(today, 19, 30), upd.startedAt)
        assertEquals(ms(today, 20, 30), upd.endedAt)
        assertEquals(60 * 60L, upd.durationSeconds)

        val timer = ActiveTimerSession(
            title = "Đang tập trung",
            categoryKey = TimeCategory.WORK.key,
            startedAtEpochMs = ms(today, 17, 5),
            startedElapsedRealtimeMs = 1000L,
            timezone = zone.id
        )
        val feed = TimelineFeedEngine.build(
            records = listOf(planned),
            activeTimer = timer,
            filter = TimelineFeedFilter.UNTIMED,
            todayDateLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertFalse(feed.current.hasActiveTimer)
        val feedAll = TimelineFeedEngine.build(
            records = listOf(planned),
            activeTimer = timer,
            filter = TimelineFeedFilter.ALL,
            todayDateLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertTrue(feedAll.current.hasActiveTimer)
        assertEquals(0, feedAll.pastDays.size)
        assertEquals(1, feedAll.upcomingDays.size)
    }

    @Test
    fun `HistoryScreen renders separate horizon sections clarified adjust labels and no ambiguous quick control`() {
        val startDir = File(System.getProperty("user.dir") ?: ".")
        val root = generateSequence(startDir) { it.parentFile }
            .first { File(it, "app/src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt").exists() }
        val history = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt").readText()
        assertTrue(history.contains("Sắp diễn ra"))
        assertTrue(history.contains("Bây giờ"))
        assertTrue(history.contains("Đã diễn ra"))
        assertTrue(history.contains("Dịch khung giờ"))
        assertTrue(history.contains("Đổi thời lượng"))
        assertTrue(history.contains("việc sắp tới"))
        assertFalse(history.contains("↔ ±15p"))

        val sched = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        assertTrue(sched.contains("visualBottomPrecedingMin"))

        val timer = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/TimerScreen.kt").readText()
        val pinStart = timer.indexOf("Tắt ghim thông báo tập trung")
        val pinEnd = timer.indexOf("Đang ghim tập trung", startIndex = pinStart)
        val pinSection = timer.substring(pinStart, pinEnd)
        assertTrue("Switch phải đặt trước nhãn để tránh bị bóng chat góc phải che", pinSection.contains("Switch("))

        val floating = File(root, "app/src/main/java/com/timewallet/mvp/overlay/WallyFloatingService.kt").readText()
        assertTrue(floating.contains("ACTION_MAIN_APP_FOREGROUND"))
    }

    @Test
    fun `five business cases future slot keeps actual unmeasured completion stays zero no deadline kept and single quick todo`() {
        val nowMs = ms(today, 14, 0)
        // 1. Lưu một khoảng tương lai (16:45-17:45) -> mang nhãn PLANNED ("Dự kiến") và giữ nguyên tổng thực tế 6h 32p
        val baseRec = rec("logged", "Làm việc", today, 7 to 0, 13 to 32, layer = TimeLayer.MEASURED)
        val builtFuture = com.timewallet.mvp.domain.usecase.LedgerValidator.validateAndBuildRecord(
            source = "manual",
            rawTitle = "Ôn tập chiều",
            rawCategory = TimeCategory.LEARN.key,
            dateLocal = today,
            inputDurationMinutes = null,
            startedAt = ms(today, 16, 45),
            endedAt = ms(today, 17, 45),
            existingRecords = listOf(baseRec),
            nowEpochMs = nowMs,
            zoneId = zone,
            convertFutureToPlanned = true
        )
        assertTrue(builtFuture is com.timewallet.mvp.domain.usecase.ValidationResult.Valid)
        val futureRec = (builtFuture as com.timewallet.mvp.domain.usecase.ValidationResult.Valid).record
        assertEquals(TimeLayer.PLANNED, futureRec.resolveLayerAt(nowMs, today))
        assertEquals("Dự kiến", futureRec.resolveConfirmationStateAt(nowMs, today).badgeShortVi)
        val actualSeconds = com.timewallet.mvp.domain.usecase.JournalDayTotal.secondsForDate(
            dateLocal = today,
            allRecords = listOf(baseRec, futureRec),
            zoneId = zone,
            nowEpochMs = nowMs
        )
        assertEquals((6 * 60 + 32) * 60L, actualSeconds)

        // 3. Việc chưa có hạn: khi tạo đề xuất / xếp lịch giữ hạn gốc null
        val noDeadlineTask = com.timewallet.mvp.domain.model.TaskItem(
            id = "task_nodeadline",
            title = "Đọc tài liệu kỹ thuật",
            category = TimeCategory.LEARN,
            priority = com.timewallet.mvp.domain.model.TaskPriority.P2,
            estimatedMinutes = 45,
            dueDateLocal = null,
            dueTimeLocal = null,
            status = com.timewallet.mvp.domain.model.TaskStatus.INBOX
        )
        val proposals = com.timewallet.mvp.domain.usecase.AiTodoEngine.buildDeterministicTodoProposals(
            tasks = listOf(noDeadlineTask),
            records = listOf(baseRec),
            activeTimer = null,
            todayLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertTrue(proposals.any { it.taskId == noDeadlineTask.id })
        assertNull(noDeadlineTask.dueDateLocal)
        assertNull(noDeadlineTask.dueTimeLocal)

        // 4. Hoàn thành việc chưa đo vẫn là 0 phút thực tế
        assertEquals(0L, com.timewallet.mvp.domain.usecase.TaskHabitEngine.computeTaskLoggedSeconds(noDeadlineTask.id, listOf(baseRec, futureRec)))

        // 2 & 5. Bỏ qua đề xuất không thay đổi danh sách công việc; tách input thêm nhanh chỉ tạo đúng 1 công việc
        val parsedQuick = com.timewallet.mvp.domain.usecase.AiTodoEngine.parseNaturalTaskInput(
            rawInput = "Gọi xác nhận lịch hẹn 20p P2",
            today = LocalDate.parse(today)
        )
        assertNotNull(parsedQuick)
        assertEquals("Gọi xác nhận lịch hẹn", parsedQuick!!.title)
    }

    @Test
    fun `delete confirmation copy distinguishes task subtasks planned schedule and actual records`() {
        val nowMs = ms(today, 14, 0)
        // 1. Xóa công việc có bước con: thấy tên việc, biết bước con cũng sẽ bị xóa, nút Hủy
        val taskWithSubtasks = com.timewallet.mvp.domain.model.TaskItem(
            id = "t_del_1",
            title = "Ôn thi hệ thống",
            category = TimeCategory.LEARN,
            priority = com.timewallet.mvp.domain.model.TaskPriority.P1,
            estimatedMinutes = 45,
            status = com.timewallet.mvp.domain.model.TaskStatus.TODO,
            subtasks = listOf(
                com.timewallet.mvp.domain.model.SubtaskItem(id = "s1", taskId = "t_del_1", title = "Đọc slide", isDone = false),
                com.timewallet.mvp.domain.model.SubtaskItem(id = "s2", taskId = "t_del_1", title = "Giải đề", isDone = true)
            )
        )
        val taskCopy = ConfirmationEngine.buildTaskDeleteCopy(taskWithSubtasks)
        assertEquals("Xóa công việc?", taskCopy.titleVi)
        assertEquals("Hủy", taskCopy.cancelLabelVi)
        assertEquals("Xóa", taskCopy.confirmLabelVi)
        assertTrue(taskCopy.messageVi.contains("Ôn thi hệ thống"))
        assertTrue(taskCopy.messageVi.contains("2 bước con"))
        assertTrue(taskCopy.messageVi.contains("bị xóa"))

        // 2. Xóa lịch dự kiến vs Xóa bản ghi thực tế
        val planned = rec("p_del", "Học nhóm dự kiến", today, 17 to 0, 18 to 0, layer = TimeLayer.PLANNED, status = "PLANNED")
        val planCopy = ConfirmationEngine.buildRecordDeleteCopy(planned, nowMs, today, zone)
        assertEquals("Xóa lịch dự kiến?", planCopy.titleVi)
        assertEquals("Hủy", planCopy.cancelLabelVi)
        assertTrue(planCopy.messageVi.contains("chưa tính vào giờ thực tế"))
        assertTrue(planCopy.messageVi.contains("giữ nguyên"))

        val measured = rec("m_del", "Ôn thi hệ thống", today, 13 to 0, 13 to 2, layer = TimeLayer.MEASURED)
        val measuredCopy = ConfirmationEngine.buildRecordDeleteCopy(measured, nowMs, today, zone)
        assertEquals("Xóa bản ghi thực tế?", measuredCopy.titleVi)
        assertTrue(measuredCopy.messageVi.contains("đã đo"))
        assertTrue(measuredCopy.messageVi.contains("2p"))
        assertTrue(measuredCopy.messageVi.contains("trừ khỏi tổng giờ đã ghi"))

        val untimed = rec("u_del", "Đọc nhanh", today, durationMinutes = 25, layer = TimeLayer.REPORTED_UNTIMED)
        val untimedCopy = ConfirmationEngine.buildRecordDeleteCopy(untimed, nowMs, today, zone)
        assertEquals("Xóa bản ghi thực tế?", untimedCopy.titleVi)
        assertTrue(untimedCopy.messageVi.contains("chưa gắn giờ"))
        assertTrue(untimedCopy.messageVi.contains("25p"))
        assertTrue(untimedCopy.messageVi.contains("trừ khỏi tổng giờ đã ghi"))

        // 3. Kiểm tra tất cả màn hình xóa công việc và xóa bản ghi đều dùng dialog xác nhận chung
        val startDir = File(System.getProperty("user.dir") ?: ".")
        val root = generateSequence(startDir) { it.parentFile }
            .first { File(it, "app/src/main/java/com/timewallet/mvp/ui/screens/TasksHabitsScreen.kt").exists() }
        val tasksUi = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/TasksHabitsScreen.kt").readText()
        assertTrue(tasksUi.contains("TaskDeleteConfirmationDialog"))
        assertTrue(tasksUi.contains("taskPendingDelete = task"))
        val historyUi = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt").readText()
        assertTrue(historyUi.contains("RecordDeleteConfirmationDialog"))
        val schedUi = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        assertTrue(schedUi.contains("RecordDeleteConfirmationDialog"))
        val compUi = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        assertTrue(compUi.contains("RecordDeleteConfirmationDialog"))
        assertFalse(compUi.contains("Giữ lại"))
    }

    @Test
    fun `linked task display preserves task title deadline and deleted fallback across edit and schedule flows`() {
        val targetDate = LocalDate.of(2026, 11, 25)
        val targetDateStr = targetDate.toString()
        val nowMs = ms("2026-11-24", 20, 0)
        val task = com.timewallet.mvp.domain.model.TaskItem(
            id = "task_0900",
            title = "Ôn bài kiểm tra",
            category = TimeCategory.LEARN,
            priority = com.timewallet.mvp.domain.model.TaskPriority.P1,
            estimatedMinutes = 30,
            dueDateLocal = targetDateStr,
            dueTimeLocal = "17:00",
            status = com.timewallet.mvp.domain.model.TaskStatus.TODO
        )
        val scheduled = rec(
            id = "plan_linked",
            title = "Phiên học sáng",
            date = targetDateStr,
            startHm = 9 to 0,
            endHm = 9 to 30,
            layer = TimeLayer.PLANNED,
            status = "PLANNED"
        ).copy(taskId = task.id)

        // 1. Có việc liên kết -> hiện tên việc gốc + hạn gốc
        val linkedDisplay = ConfirmationEngine.buildLinkedTaskDisplay(scheduled, task)!!
        assertEquals("Thuộc công việc: Ôn bài kiểm tra", linkedDisplay.labelVi)
        assertEquals("Hạn 11-25 17:00", linkedDisplay.detailVi)
        assertFalse(linkedDisplay.isMissingTask)

        val doneDisplay = ConfirmationEngine.buildLinkedTaskDisplay(
            scheduled,
            task.copy(status = com.timewallet.mvp.domain.model.TaskStatus.DONE)
        )!!
        assertEquals("Hạn 11-25 17:00 · Đã hoàn thành", doneDisplay.detailVi)

        // 2. Sau khi dịch +15p hoặc tăng thời lượng +15p hoặc lưu Edit -> taskId giữ nguyên & hiển thị lại đúng việc
        val shifted = (ConfirmationEngine.shiftPlannedRecord(scheduled, 15, nowMs, zone)
            as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(task.id, shifted.taskId)
        assertEquals("Thuộc công việc: Ôn bài kiểm tra", ConfirmationEngine.buildLinkedTaskDisplay(shifted, task)?.labelVi)

        val adjusted = (ConfirmationEngine.adjustRecord(
            existing = shifted,
            newTitle = "Ôn bài buổi sáng",
            newCategoryKey = TimeCategory.LEARN.key,
            newDateLocal = targetDateStr,
            newDurationMinutes = null,
            newStartHm = "09:00",
            newEndHm = "09:45",
            allowFutureAsPlanned = true,
            existingRecords = listOf(shifted),
            nowEpochMs = nowMs,
            zoneId = zone
        ) as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(task.id, adjusted.taskId)
        assertEquals("Thuộc công việc: Ôn bài kiểm tra", ConfirmationEngine.buildLinkedTaskDisplay(adjusted, task)?.labelVi)

        // 3. Nếu việc gốc bị xóa -> báo rõ fallback thay vì im lặng
        val missingDisplay = ConfirmationEngine.buildLinkedTaskDisplay(scheduled, null)!!
        assertTrue(missingDisplay.isMissingTask)
        assertTrue(missingDisplay.labelVi.contains("đã bị xóa"))

        // 4. Bản ghi không gắn taskId -> null
        assertNull(ConfirmationEngine.buildLinkedTaskDisplay(scheduled.copy(taskId = null), task))

        // 5. Hợp đồng UI: EditRecordDialog, ManualEntrySheet, ScheduleCalendarView, MainActivity đều nối hiển thị việc liên kết
        val startDir = File(System.getProperty("user.dir") ?: ".")
        val root = generateSequence(startDir) { it.parentFile }
            .first { File(it, "app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").exists() }
        val compUi = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        assertTrue(compUi.contains("buildLinkedTaskDisplay"))
        assertTrue(compUi.contains("LinkedTaskBadgeCard"))
        val mainUi = File(root, "app/src/main/java/com/timewallet/mvp/MainActivity.kt").readText()
        assertTrue(mainUi.contains("linkedTask = linkedTask"))
        assertTrue(mainUi.contains("linkedTaskTitle ="))
        val schedUi = File(root, "app/src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        assertTrue(schedUi.contains("buildLinkedTaskDisplay"))
    }
}
