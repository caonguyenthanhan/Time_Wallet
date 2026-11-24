package com.timewallet.mvp

import com.timewallet.mvp.data.ai.WallyTodoAiPlanner
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiTodoEngine
import com.timewallet.mvp.domain.usecase.AiTodoProposalKind
import com.timewallet.mvp.domain.usecase.ScheduleBlockVisualState
import com.timewallet.mvp.domain.usecase.ScheduleGridBlock
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.TaskHabitEngine
import com.timewallet.mvp.domain.usecase.TodoRiskAlert
import com.timewallet.mvp.domain.usecase.TodoRiskKind
import com.timewallet.mvp.ui.screens.buildTodoSummaryBannerState
import com.timewallet.mvp.ui.screens.computeEmbeddedGridHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class V221UiUxSpecTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun ts(date: LocalDate, h: Int, m: Int): Long =
        date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `natural task parser cleans Soan bao cao truoc 15h mai can 45 phut P1 cleanly`() {
        val today = LocalDate.of(2026, 11, 23)
        val parsed = AiTodoEngine.parseNaturalTaskInput(
            rawInput = "Soạn báo cáo trước 15h mai, cần 45 phút, P1",
            today = today
        )
        assertNotNull(parsed)
        assertEquals("Soạn báo cáo", parsed!!.title)
        assertEquals("2026-11-24", parsed.dueDateLocal)
        assertEquals("15:00", parsed.dueTimeLocal)
        assertEquals(45, parsed.estimatedMinutes)
        assertEquals(TaskPriority.P1, parsed.priority)

        val afternoon = AiTodoEngine.parseNaturalTaskInput(
            rawInput = "Họp nhóm 3h chiều mai 30p P2",
            today = today
        )
        assertNotNull(afternoon)
        assertEquals("15:00", afternoon!!.dueTimeLocal)
        assertEquals(30, afternoon.estimatedMinutes)
    }

    @Test
    fun `schedule proposal after task deadline explains overdue status and post-deadline slot clearly`() {
        val today = "2026-11-23"
        val nowMs = ZonedDateTime.of(2026, 11, 23, 21, 30, 0, 0, zone).toInstant().toEpochMilli()
        val task = TaskItem(
            id = "task_exam",
            title = "Ôn thi hệ thống",
            category = TimeCategory.LEARN,
            priority = TaskPriority.P1,
            estimatedMinutes = 45,
            dueDateLocal = today,
            dueTimeLocal = "20:00",
            status = TaskStatus.TODO
        )

        val note = AiTodoEngine.buildScheduleDeadlineNoteVi(
            task = task,
            targetDateLocal = today,
            startHm = "21:45",
            endHm = "22:30",
            todayLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertNotNull(note)
        assertTrue(note!!.contains("20:00"))
        assertTrue(note.contains("đã quá hạn"))
        assertTrue(note.contains("21:45–22:30"))
        assertTrue(note.contains("sau hạn"))

        val props = AiTodoEngine.buildDeterministicTodoProposals(
            tasks = listOf(task),
            records = emptyList(),
            activeTimer = null,
            todayLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone
        )
        val sched = props.first { it.kind == AiTodoProposalKind.SCHEDULE_INTO_GAP }
        assertNotNull(sched.deadlineWarningVi)
        assertTrue(sched.deadlineWarningVi!!.contains("20:00"))
        assertTrue(sched.deadlineWarningVi!!.contains("đã quá hạn"))

        // Cloud JSON parsing runs revalidateTodoProposals so deadlineWarningVi is populated there too.
        val cloudJson = """
            {"choices":[{"message":{"content":"{\"summary\":\"Đề xuất tối nay\",\"orderedTaskIds\":[\"task_exam\"],\"proposals\":[{\"kind\":\"SCHEDULE_INTO_GAP\",\"taskId\":\"task_exam\",\"reason\":\"Khung trống tối\",\"targetDateLocal\":\"2026-11-23\",\"startHm\":\"21:45\",\"endHm\":\"22:30\"}]}"}}]}
        """.trimIndent()
        val cloudReport = WallyTodoAiPlanner.parseTodoPlanJson(
            responseBody = cloudJson,
            openTasks = listOf(task),
            records = emptyList(),
            activeTimer = null,
            todayLocal = today,
            nowEpochMs = nowMs,
            zoneId = zone,
            fallbackRisks = emptyList(),
            permsFingerprint = "ok"
        )
        val cloudSched = cloudReport.proposals.first()
        assertNotNull(cloudSched.deadlineWarningVi)
        assertTrue(cloudSched.deadlineWarningVi!!.contains("20:00"))
    }

    @Test
    fun `planned block plus two 1m measured segments computes exact 2m actual logged time`() {
        val today = "2026-11-23"
        val taskId = "task_exam"
        val planStart = ZonedDateTime.of(2026, 11, 23, 21, 45, 0, 0, zone).toInstant().toEpochMilli()
        val planEnd = ZonedDateTime.of(2026, 11, 23, 22, 30, 0, 0, zone).toInstant().toEpochMilli()
        val seg1Start = ZonedDateTime.of(2026, 11, 23, 21, 5, 0, 0, zone).toInstant().toEpochMilli()
        val seg2Start = ZonedDateTime.of(2026, 11, 23, 21, 7, 0, 0, zone).toInstant().toEpochMilli()

        val records = listOf(
            LedgerRecord(
                id = "plan_45m",
                source = "todo_ai_plan",
                layer = TimeLayer.PLANNED,
                createdAt = planStart - 60_000L,
                userConfirmedAt = planStart - 60_000L,
                category = TimeCategory.LEARN,
                title = "Ôn thi hệ thống",
                date = today,
                durationSeconds = 45 * 60L,
                startedAt = planStart,
                endedAt = planEnd,
                timezone = zone.id,
                status = "PLANNED",
                idempotencyKey = "k_plan",
                taskId = taskId
            ),
            LedgerRecord(
                id = "meas_1",
                source = "timer",
                layer = TimeLayer.MEASURED,
                createdAt = seg1Start + 60_000L,
                userConfirmedAt = seg1Start + 60_000L,
                category = TimeCategory.LEARN,
                title = "Ôn thi hệ thống",
                date = today,
                durationSeconds = 60L,
                startedAt = seg1Start,
                endedAt = seg1Start + 60_000L,
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "k_m1",
                taskId = taskId
            ),
            LedgerRecord(
                id = "meas_2",
                source = "timer",
                layer = TimeLayer.MEASURED,
                createdAt = seg2Start + 60_000L,
                userConfirmedAt = seg2Start + 60_000L,
                category = TimeCategory.LEARN,
                title = "Ôn thi hệ thống",
                date = today,
                durationSeconds = 60L,
                startedAt = seg2Start,
                endedAt = seg2Start + 60_000L,
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "k_m2",
                taskId = taskId
            )
        )

        val actualSeconds = TaskHabitEngine.computeTaskLoggedSeconds(taskId, records)
        assertEquals("Chỉ cộng 2 phút thực đo, không cộng 45 phút dự kiến", 120L, actualSeconds)
    }

    @Test
    fun `ui and viewmodel uphold v2_2_1 spec contracts`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val applyBlock = vm.substring(vm.indexOf("AiTodoProposalKind.SCHEDULE_INTO_GAP ->"), vm.indexOf("is com.timewallet.mvp.data.local.ConfirmResult.Aborted"))
        assertFalse("SCHEDULE_INTO_GAP không được ghi đè dueTimeLocal/dueDateLocal gốc", applyBlock.contains("dueTimeLocal = startHm"))
        assertTrue(vm.contains("modelName = ai.modelName"))

        val main = File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText()
        assertTrue(main.contains("Alignment.BottomCenter"))
        assertTrue(main.contains("isTimerRunning = state.activeTimer != null && state.currentTab != AppTab.FOCUS"))

        val history = File("src/main/java/com/timewallet/mvp/ui/screens/HistoryScreen.kt").readText()
        assertTrue(history.contains("text = \"Dòng thời gian\""))

        val sched = File("src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        assertTrue(sched.contains("staticGridCoords"))
        assertFalse(sched.contains("text = \"Trống\""))

        val comp = File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        assertTrue(comp.contains("Text(\"Ghi nhanh\""))
        assertFalse(comp.contains("Thu thập nhanh (Offline)"))
        assertTrue(comp.contains("Cài đặt nâng cao"))

        val profile = File("src/main/java/com/timewallet/mvp/ui/screens/ProfileSettingsScreen.kt").readText()
        assertFalse(profile.contains("AASM/SRS"))
        assertFalse(profile.contains("Albulescu"))
        assertFalse(profile.contains("Sonnentag"))
        assertTrue(profile.contains("Bắt đầu ngay"))

        val floating = File("src/main/java/com/timewallet/mvp/overlay/WallyFloatingService.kt").readText()
        assertTrue(floating.contains("modelName = aiPrefs.modelName"))
        assertTrue(floating.contains("Thu gọn") && floating.contains("Tắt nổi"))

        val theme = File("src/main/java/com/timewallet/mvp/ui/theme/Theme.kt").readText()
        assertTrue(theme.contains("ANIMATOR_DURATION_SCALE"))
        assertTrue(theme.contains("WalletReduceMotion"))
    }

    @Test
    fun `summary-only week data hides Wally narrative without category ranking or tag nagging`() {
        val monday = LocalDate.of(2026, 11, 16)
        val sunday = monday.plusDays(6)
        val nowMs = ZonedDateTime.of(sunday, java.time.LocalTime.of(14, 0), zone).toInstant().toEpochMilli()

        val records = (0L..6L).flatMap { offset ->
            val d = monday.plusDays(offset)
            val sleepStart = ZonedDateTime.of(d, java.time.LocalTime.of(0, 0), zone).toInstant().toEpochMilli()
            val sleepEnd = ZonedDateTime.of(d, java.time.LocalTime.of(7, 30), zone).toInstant().toEpochMilli()
            val workStart = ZonedDateTime.of(d, java.time.LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
            val workEnd = ZonedDateTime.of(d, java.time.LocalTime.of(16, 0), zone).toInstant().toEpochMilli()
            listOf(
                LedgerRecord(
                    id = "s_$offset", source = "manual", layer = TimeLayer.REPORTED_TIMED,
                    createdAt = sleepEnd, userConfirmedAt = sleepEnd, category = TimeCategory.SLEEP,
                    title = "Ngủ", date = d.toString(), durationSeconds = 7 * 3600L + 1800L,
                    startedAt = sleepStart, endedAt = sleepEnd, timezone = zone.id,
                    status = "CONFIRMED", idempotencyKey = "ks_$offset"
                ),
                LedgerRecord(
                    id = "w_$offset", source = "manual", layer = TimeLayer.REPORTED_TIMED,
                    createdAt = workEnd, userConfirmedAt = workEnd, category = TimeCategory.WORK,
                    title = "Công việc", date = d.toString(), durationSeconds = 7 * 3600L,
                    startedAt = workStart, endedAt = workEnd, timezone = zone.id,
                    status = "CONFIRMED", idempotencyKey = "kw_$offset"
                )
            )
        }

        val summary = com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = sunday.toString(),
            records = records,
            goals = emptyList(),
            tags = emptyList(),
            zoneId = zone,
            nowEpochMs = nowMs
        )

        assertTrue("Biểu đồ vẫn có đủ tổng giờ Ngủ và Công việc", summary.activityTotals.any { it.durationSeconds >= 49 * 3600L })
        assertEquals("Chỉ có tổng giờ tuần -> ẩn phần lời kể Wally", null, summary.wallyStory)
        assertFalse(summary.actionableInsight.contains("chiếm nhiều nhất", ignoreCase = true))
        assertFalse(summary.actionableInsight.contains("chưa gắn Tag", ignoreCase = true))
    }

    @Test
    fun `repeating missed evening workout with work overlap proposes Wed 12_15-12_35 and adapts on conflict or logging`() {
        // Giả định hôm nay là Thứ Ba 2026-11-24 08:00 (trong tuần 23/11–29/11).
        val tuesday = LocalDate.of(2026, 11, 24)
        val monday = tuesday.minusDays(1)
        val wednesday = tuesday.plusDays(1)
        val nowMs = ZonedDateTime.of(tuesday, java.time.LocalTime.of(8, 0), zone).toInstant().toEpochMilli()

        val exerciseGoal = com.timewallet.mvp.domain.model.Goal(
            id = "g_health",
            groupId = "gg_health",
            targetType = com.timewallet.mvp.domain.model.GoalTargetType.CATEGORY,
            targetKey = TimeCategory.HEALTH.key,
            kind = com.timewallet.mvp.domain.model.GoalKind.MIN,
            period = com.timewallet.mvp.domain.model.GoalPeriod.WEEK,
            targetSeconds = 3600L,
            status = com.timewallet.mvp.domain.model.GoalVersionStatus.ACTIVE,
            validFrom = monday.minusDays(14).toString(),
            validTo = null,
            createdAt = nowMs - 86_400_000L * 14,
            updatedAt = nowMs - 86_400_000L * 14
        )

        fun ts(d: LocalDate, h: Int, m: Int) =
            ZonedDateTime.of(d, java.time.LocalTime.of(h, m), zone).toInstant().toEpochMilli()

        val prevMon = monday.minusWeeks(1)
        val prevWed = wednesday.minusWeeks(1)
        val prevFri = prevMon.plusDays(4)

        val baseRecords = mutableListOf(
            // Tuần trước: 2 buổi tập trưa 20p lúc 12:15–12:35
            LedgerRecord(
                id = "prev_h1", source = "timer", layer = TimeLayer.MEASURED,
                createdAt = ts(prevMon, 12, 35), userConfirmedAt = ts(prevMon, 12, 35),
                category = TimeCategory.HEALTH, title = "Vận động trưa", date = prevMon.toString(),
                durationSeconds = 20 * 60L, startedAt = ts(prevMon, 12, 15), endedAt = ts(prevMon, 12, 35),
                timezone = zone.id, status = "CONFIRMED", idempotencyKey = "kh1"
            ),
            LedgerRecord(
                id = "prev_h2", source = "timer", layer = TimeLayer.MEASURED,
                createdAt = ts(prevFri, 12, 35), userConfirmedAt = ts(prevFri, 12, 35),
                category = TimeCategory.HEALTH, title = "Vận động trưa", date = prevFri.toString(),
                durationSeconds = 20 * 60L, startedAt = ts(prevFri, 12, 15), endedAt = ts(prevFri, 12, 35),
                timezone = zone.id, status = "CONFIRMED", idempotencyKey = "kh2"
            ),
            // 3 buổi dự kiến tối trong tuần / kỳ gần đây (19:00–19:30), 2 buổi trùng công việc kéo dài 18:00–20:30, đều chưa ghi nhận
            LedgerRecord(
                id = "p_ev1", source = "manual", layer = TimeLayer.PLANNED,
                createdAt = ts(monday, 10, 0), userConfirmedAt = ts(monday, 10, 0),
                category = TimeCategory.HEALTH, title = "Tập tối 1", date = monday.toString(),
                durationSeconds = 30 * 60L, startedAt = ts(monday, 19, 0), endedAt = ts(monday, 19, 30),
                timezone = zone.id, status = "PLANNED", idempotencyKey = "pe1"
            ),
            LedgerRecord(
                id = "p_ev2", source = "manual", layer = TimeLayer.PLANNED,
                createdAt = ts(monday, 10, 0), userConfirmedAt = ts(monday, 10, 0),
                category = TimeCategory.HEALTH, title = "Tập tối 2", date = monday.toString(),
                durationSeconds = 30 * 60L, startedAt = ts(monday, 20, 0), endedAt = ts(monday, 20, 30),
                timezone = zone.id, status = "PLANNED", idempotencyKey = "pe2"
            ),
            LedgerRecord(
                id = "p_ev3", source = "manual", layer = TimeLayer.PLANNED,
                createdAt = ts(monday, 10, 0), userConfirmedAt = ts(monday, 10, 0),
                category = TimeCategory.HEALTH, title = "Tập tối 3", date = monday.toString(),
                durationSeconds = 30 * 60L, startedAt = ts(monday, 21, 0), endedAt = ts(monday, 21, 30),
                timezone = zone.id, status = "PLANNED", idempotencyKey = "pe3"
            ),
            LedgerRecord(
                id = "w_overtime", source = "manual", layer = TimeLayer.REPORTED_TIMED,
                createdAt = ts(monday, 20, 45), userConfirmedAt = ts(monday, 20, 45),
                category = TimeCategory.WORK, title = "Công việc kéo dài", date = monday.toString(),
                durationSeconds = 165 * 60L, startedAt = ts(monday, 18, 0), endedAt = ts(monday, 20, 45),
                timezone = zone.id, status = "CONFIRMED", idempotencyKey = "kwo",
                plannedDurationSeconds = 90 * 60L
            ),
            // Thứ Ba 12:00–13:00 bận họp để thứ Tư 12:15–12:35 là ô trưa trống đầu tiên
            LedgerRecord(
                id = "tue_busy", source = "manual", layer = TimeLayer.PLANNED,
                createdAt = ts(tuesday, 8, 0), userConfirmedAt = ts(tuesday, 8, 0),
                category = TimeCategory.WORK, title = "Họp trưa", date = tuesday.toString(),
                durationSeconds = 60 * 60L, startedAt = ts(tuesday, 12, 0), endedAt = ts(tuesday, 13, 0),
                timezone = zone.id, status = "PLANNED", idempotencyKey = "ktb"
            )
        )

        val res1 = com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = tuesday.toString(),
            records = baseRecords,
            goals = listOf(exerciseGoal),
            zoneId = zone,
            nowEpochMs = nowMs
        )
        val story1 = res1.wallyStory
        assertNotNull(story1)
        assertTrue("Phát hiện nêu rõ 2/3 và chưa ghi nhận: ${story1!!.mainFinding}", story1.mainFinding.contains("2/3") && story1.mainFinding.contains("chưa ghi nhận"))
        assertFalse("Không lặp từ buổi buổi", (story1.findingDetail ?: "").contains("buổi buổi") || story1.rationaleVi.contains("buổi buổi"))
        assertFalse("Không phán xét bỏ tập", story1.mainFinding.contains("bỏ tập", ignoreCase = true))
        assertEquals("Xếp thứ Tư 12:15–12:35", story1.actionLabel)
        val slot1 = (story1.action as com.timewallet.mvp.domain.usecase.WallyStoryAction.ScheduleSlot).slot
        assertEquals(wednesday.toString(), slot1.dateLocal)
        assertEquals(12 * 60 + 15, slot1.startMinute)
        assertEquals(12 * 60 + 35, slot1.endMinute)
        assertEquals(20, slot1.durationMinutes)
        assertTrue("Có căn cứ ngày/phiên cho Phân tích AI", story1.evidenceItems.size >= 4)

        // Khi khung thứ Tư 12:15–12:35 bị lấp kín -> gợi ý không còn chọn thứ Tư 12:15–12:35
        val blockedRecords = baseRecords + LedgerRecord(
            id = "wed_busy", source = "manual", layer = TimeLayer.PLANNED,
            createdAt = ts(tuesday, 8, 0), userConfirmedAt = ts(tuesday, 8, 0),
            category = TimeCategory.WORK, title = "Họp đột xuất", date = wednesday.toString(),
            durationSeconds = 60 * 60L, startedAt = ts(wednesday, 12, 0), endedAt = ts(wednesday, 13, 0),
            timezone = zone.id, status = "PLANNED", idempotencyKey = "kwb"
        )
        val resBlocked = com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = tuesday.toString(),
            records = blockedRecords,
            goals = listOf(exerciseGoal),
            zoneId = zone,
            nowEpochMs = nowMs
        )
        assertFalse(resBlocked.wallyStory?.actionLabel == "Xếp thứ Tư 12:15–12:35")

        // Khi ghi bổ sung 1 buổi tập 60p -> mục tiêu đạt và nội dung đổi tương ứng
        val loggedRecords = baseRecords + LedgerRecord(
            id = "actual_60", source = "timer", layer = TimeLayer.MEASURED,
            createdAt = ts(tuesday, 7, 30), userConfirmedAt = ts(tuesday, 7, 30),
            category = TimeCategory.HEALTH, title = "Chạy sáng", date = tuesday.toString(),
            durationSeconds = 3600L, startedAt = ts(tuesday, 6, 30), endedAt = ts(tuesday, 7, 30),
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "ka60"
        )
        val resLogged = com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = tuesday.toString(),
            records = loggedRecords,
            goals = listOf(exerciseGoal),
            zoneId = zone,
            nowEpochMs = nowMs
        )
        assertTrue(resLogged.goalItems.single().met)
        assertFalse(resLogged.wallyStory?.actionLabel == "Xếp thứ Tư 12:15–12:35")
    }

    @Test
    fun `02_50 AM never suggests 03_00 slot and sleep or outlier plans never cause +48938 pct drift`() {
        val today = LocalDate.of(2026, 11, 24)
        val now0250 = ZonedDateTime.of(today, java.time.LocalTime.of(2, 50), zone).toInstant().toEpochMilli()
        val monday = today.minusDays(1)

        val exerciseGoal = com.timewallet.mvp.domain.model.Goal(
            id = "g_ex", groupId = "gg_ex",
            targetType = com.timewallet.mvp.domain.model.GoalTargetType.CATEGORY,
            targetKey = TimeCategory.HEALTH.key,
            kind = com.timewallet.mvp.domain.model.GoalKind.MIN,
            period = com.timewallet.mvp.domain.model.GoalPeriod.WEEK,
            targetSeconds = 3600L,
            status = com.timewallet.mvp.domain.model.GoalVersionStatus.ACTIVE,
            validFrom = monday.toString(), validTo = null,
            createdAt = now0250 - 86_400_000L, updatedAt = now0250 - 86_400_000L
        )

        fun ts(d: LocalDate, h: Int, m: Int) =
            ZonedDateTime.of(d, java.time.LocalTime.of(h, m), zone).toInstant().toEpochMilli()

        // Lịch hôm nay có ngủ 00:00–07:00 và làm việc sáng/chiều -> khoảng trống trưa ban ngày (12:00–13:30), không phải 03:00
        // Kèm 1 bản ghi Ngủ kế hoạch 1p thực tế 8h10p (+48938%) và 1 bản ghi Vận động 1p -> 1p/60p còn 59p
        val records = listOf(
            LedgerRecord(
                id = "sleep_outlier", source = "manual", layer = TimeLayer.REPORTED_TIMED,
                createdAt = ts(monday, 7, 10), userConfirmedAt = ts(monday, 7, 10),
                category = TimeCategory.SLEEP, title = "Ngủ", date = monday.toString(),
                durationSeconds = 29423L, startedAt = ts(monday, 0, 0), endedAt = ts(monday, 8, 10),
                timezone = zone.id, status = "CONFIRMED", idempotencyKey = "kso",
                confirmationStateRaw = "ADJUSTED", plannedDurationSeconds = 60L
            ),
            LedgerRecord(
                id = "ex_1m", source = "timer", layer = TimeLayer.MEASURED,
                createdAt = ts(monday, 18, 1), userConfirmedAt = ts(monday, 18, 1),
                category = TimeCategory.HEALTH, title = "Khởi động", date = monday.toString(),
                durationSeconds = 60L, startedAt = ts(monday, 18, 0), endedAt = ts(monday, 18, 1),
                timezone = zone.id, status = "CONFIRMED", idempotencyKey = "kex1"
            ),
            LedgerRecord(
                id = "work_am", source = "manual", layer = TimeLayer.PLANNED,
                createdAt = ts(monday, 20, 0), userConfirmedAt = ts(monday, 20, 0),
                category = TimeCategory.WORK, title = "Việc sáng", date = today.toString(),
                durationSeconds = 3 * 3600L, startedAt = ts(today, 9, 0), endedAt = ts(today, 12, 0),
                timezone = zone.id, status = "PLANNED", idempotencyKey = "kwam"
            ),
            LedgerRecord(
                id = "work_pm", source = "manual", layer = TimeLayer.PLANNED,
                createdAt = ts(monday, 20, 0), userConfirmedAt = ts(monday, 20, 0),
                category = TimeCategory.WORK, title = "Việc chiều", date = today.toString(),
                durationSeconds = 4 * 3600L, startedAt = ts(today, 13, 30), endedAt = ts(today, 17, 30),
                timezone = zone.id, status = "PLANNED", idempotencyKey = "kwpm"
            )
        )

        val eff = com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = today.toString(),
            records = records,
            goals = listOf(exerciseGoal),
            zoneId = zone,
            nowEpochMs = now0250
        )

        // 1) Đồng bộ 1p / 60p -> còn 59p trên thanh tiến độ, lời kể và phân tích
        assertEquals("1p / 60p", eff.goalItems.single().progressText)
        val story = eff.wallyStory!!
        assertTrue("Phải nói còn 59p: ${story.mainFinding}", story.mainFinding.contains("1p/60p") && story.mainFinding.contains("còn 59p"))
        assertFalse("Không gợi ý 03:00", story.mainFinding.contains("03:00") || (story.actionLabel?.contains("03:00") == true))
        val slot = (story.action as com.timewallet.mvp.domain.usecase.WallyStoryAction.ScheduleSlot).slot
        assertTrue("Khung gợi ý phải thuộc ban ngày 06:00–22:00", slot.startMinute >= 360 && slot.endMinute <= 1320)
        assertTrue("Có căn cứ chọn khung", story.rationaleVi.isNotBlank())

        // 2) Loại Ngủ/nghỉ khỏi chỉ số tập trung -> sampleCount == 0 (ẩn chỉ số thay vì +48938%)
        val accOnlySleep = TaskHabitEngine.computePlanVsActualDelta(records)
        assertEquals(0, accOnlySleep.sampleCount)
        assertFalse(accOnlySleep.summaryText.contains("48938"))

        // 3) Khi có phiên WORK kế hoạch 15p nhưng thực tế 120p (> 300%) -> dùng phút, không dùng +700%
        val extremeWork = LedgerRecord(
            id = "w_extreme", source = "manual", layer = TimeLayer.REPORTED_TIMED,
            createdAt = ts(monday, 11, 0), userConfirmedAt = ts(monday, 11, 0),
            category = TimeCategory.WORK, title = "Sprint đột xuất", date = monday.toString(),
            durationSeconds = 120 * 60L, startedAt = ts(monday, 9, 0), endedAt = ts(monday, 11, 0),
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "kwe",
            confirmationStateRaw = "ADJUSTED", plannedDurationSeconds = 15 * 60L
        )
        val accExtreme = TaskHabitEngine.computePlanVsActualDelta(records + extremeWork)
        assertEquals(1, accExtreme.sampleCount)
        assertTrue(accExtreme.usedMinutesDisplayOnly)
        assertFalse(accExtreme.summaryText.contains("%)"))
        assertTrue(accExtreme.summaryText.contains("15p") && accExtreme.summaryText.contains("2h"))
        assertTrue(accExtreme.summaryText.contains("1h 45p"))

        // 4) Phiên 09:00–16:30 (450p) trải đều theo từng giờ, không cộng dồn 450p vào 1 giờ bắt đầu
        val longBlock = LedgerRecord(
            id = "w_long", source = "manual", layer = TimeLayer.REPORTED_TIMED,
            createdAt = ts(today, 16, 30), userConfirmedAt = ts(today, 16, 30),
            category = TimeCategory.WORK, title = "Ca dài", date = today.toString(),
            durationSeconds = 450 * 60L, startedAt = ts(today, 9, 0), endedAt = ts(today, 16, 30),
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "kwl"
        )
        val peak = TaskHabitEngine.computePeakFocusWindow(listOf(longBlock), today.toString(), zone)
        assertNotNull(peak)
        assertEquals("09:00–11:00", peak!!.windowLabel)
        assertEquals(120L, peak.goalMinutes)
        assertEquals(27, peak.sharePercent)
    }

    @Test
    fun `r5 regression dragging or resizing planned task block preserves taskId and habitId`() {
        val monday = LocalDate.of(2026, 11, 23)
        val nowMs = ts(monday, 2, 0)
        val linkedPlan = LedgerRecord(
            id = "plan_soan_bc",
            source = "todo_ai_plan",
            layer = TimeLayer.PLANNED,
            createdAt = nowMs,
            userConfirmedAt = nowMs,
            category = TimeCategory.WORK,
            title = "Soạn báo cáo",
            date = monday.toString(),
            durationSeconds = 45 * 60L,
            startedAt = ts(monday, 3, 35),
            endedAt = ts(monday, 4, 20),
            timezone = zone.id,
            status = "PLANNED",
            idempotencyKey = "k_plan_bc",
            confirmationStateRaw = ConfirmationState.PLANNED.name,
            plannedStartedAt = ts(monday, 3, 35),
            plannedEndedAt = ts(monday, 4, 20),
            plannedDurationSeconds = 45 * 60L,
            taskId = "task_soan_bc",
            habitId = "hab_1"
        )

        val dragged = com.timewallet.mvp.domain.usecase.ConfirmationEngine.moveOrResizeScheduleBlock(
            existing = linkedPlan,
            newDateLocal = monday.toString(),
            newStartHm = "03:35",
            newEndHm = "05:00",
            existingRecords = listOf(linkedPlan),
            nowEpochMs = nowMs,
            zoneId = zone
        ) as com.timewallet.mvp.domain.usecase.ConfirmationActionResult.Applied
        val draggedRec = dragged.updatedRecords.single()
        assertEquals("task_soan_bc", draggedRec.taskId)
        assertEquals("hab_1", draggedRec.habitId)
        assertEquals(TimeLayer.PLANNED, draggedRec.resolveLayerAt(nowMs, monday.toString()))
        assertEquals(ts(monday, 5, 0), draggedRec.endedAt)

        val nudged = com.timewallet.mvp.domain.usecase.ConfirmationEngine.resizePlannedRecord(
            existing = draggedRec,
            deltaMinutes = -15,
            nowEpochMs = nowMs,
            zoneId = zone
        ) as com.timewallet.mvp.domain.usecase.ConfirmationActionResult.Applied
        assertEquals("task_soan_bc", nudged.updatedRecords.single().taskId)
        assertEquals("hab_1", nudged.updatedRecords.single().habitId)

        val edited = com.timewallet.mvp.domain.usecase.ConfirmationEngine.adjustRecord(
            existing = draggedRec,
            newTitle = "Soạn báo cáo",
            newCategoryKey = TimeCategory.WORK.key,
            newDateLocal = monday.toString(),
            newDurationMinutes = null,
            newStartHm = "03:40",
            newEndHm = "05:10",
            allowFutureAsPlanned = true,
            existingRecords = listOf(draggedRec),
            nowEpochMs = nowMs,
            zoneId = zone
        ) as com.timewallet.mvp.domain.usecase.ConfirmationActionResult.Applied
        assertEquals("task_soan_bc", edited.updatedRecords.single().taskId)
        assertEquals("hab_1", edited.updatedRecords.single().habitId)
    }

    @Test
    fun `r5 regression consecutive 1m measured segments match timeline clock format and use parallel lanes`() {
        val day = LocalDate.of(2026, 11, 24)
        val seg1Start = day.atTime(2, 35, 40).atZone(zone).toInstant().toEpochMilli()
        val seg1End = day.atTime(2, 36, 40).atZone(zone).toInstant().toEpochMilli()
        val seg2Start = day.atTime(2, 36, 45).atZone(zone).toInstant().toEpochMilli()
        val seg2End = day.atTime(2, 37, 45).atZone(zone).toInstant().toEpochMilli()
        val nowMs = day.atTime(2, 38, 0).atZone(zone).toInstant().toEpochMilli()

        val r1 = LedgerRecord(
            id = "t_seg1", source = "timer", layer = TimeLayer.MEASURED,
            createdAt = seg1End, userConfirmedAt = seg1End,
            category = TimeCategory.LEARN, title = "Ôn thi hệ thống", date = day.toString(),
            durationSeconds = 60L, startedAt = seg1Start, endedAt = seg1End,
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "ks1",
            confirmationStateRaw = ConfirmationState.COMPLETED.name
        )
        val r2 = LedgerRecord(
            id = "t_seg2", source = "timer", layer = TimeLayer.MEASURED,
            createdAt = seg2End, userConfirmedAt = seg2End,
            category = TimeCategory.LEARN, title = "Ôn thi hệ thống", date = day.toString(),
            durationSeconds = 60L, startedAt = seg2Start, endedAt = seg2End,
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "ks2",
            confirmationStateRaw = ConfirmationState.COMPLETED.name
        )

        val model = com.timewallet.mvp.domain.usecase.ScheduleGridEngine.buildDayScheduleModel(
            date = day,
            records = listOf(r1, r2),
            nowEpochMs = nowMs,
            zone = zone
        )
        assertEquals(2, model.timedBlocks.size)
        val b1 = model.timedBlocks.first { it.recordId == "t_seg1" }
        val b2 = model.timedBlocks.first { it.recordId == "t_seg2" }
        assertEquals("02:35", b1.displayStartHm)
        assertEquals("02:36", b1.displayEndHm)
        assertEquals("02:36", b2.displayStartHm)
        assertEquals("02:37", b2.displayEndHm)
        assertEquals(2, b1.laneCount)
        assertEquals(2, b2.laneCount)
        assertTrue(b1.laneIndex != b2.laneIndex)
    }

    @Test
    fun `r5 regression empty week with zero records and zero goals hides wally story`() {
        val today = LocalDate.of(2026, 11, 24)
        val eff = com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = today.toString(),
            records = emptyList(),
            goals = emptyList(),
            zoneId = zone,
            nowEpochMs = ts(today, 9, 0)
        )
        assertEquals(null, eff.wallyStory)
    }

    @Test
    fun `r5 closure empty gap drag and bottom resize stop at measured blocks instead of overlapping`() {
        val day = LocalDate.of(2026, 11, 24)
        val occupied = listOf(
            ScheduleGridBlock(
                recordId = "r5_seg1",
                record = null,
                isActiveTimer = false,
                dateLocal = day.toString(),
                startMinute = 281, // 04:41
                endMinute = 282,   // 04:42
                totalDurationMinutes = 1,
                title = "Ôn thi hệ thống",
                category = TimeCategory.LEARN,
                visualState = ScheduleBlockVisualState.COMPLETED
            ),
            ScheduleGridBlock(
                recordId = "r5_seg2",
                record = null,
                isActiveTimer = false,
                dateLocal = day.toString(),
                startMinute = 282, // 04:42
                endMinute = 283,   // 04:43
                totalDurationMinutes = 1,
                title = "Ôn thi hệ thống",
                category = TimeCategory.LEARN,
                visualState = ScheduleBlockVisualState.COMPLETED
            )
        )

        // Kéo xuống từ 03:45 (225) qua 05:30 (330): phải dừng tại 04:41 (281), không phủ 04:41–04:43
        val dragDown = ScheduleGridEngine.snapEmptyDragRangeInGaps(
            anchorMinute = 225,
            currentMinute = 330,
            occupiedBlocks = occupied
        )
        assertEquals(225 to 281, dragDown)

        // Kéo mép dưới của vùng đã chọn 03:45–04:41 xuống thêm 60p: giữ trần tại 04:41 (281)
        val resizeDown = ScheduleGridEngine.snapEmptyResizeEndInGap(
            origStartMin = 225,
            origEndMin = 281,
            deltaEndMinutes = 60,
            occupiedBlocks = occupied
        )
        assertEquals(225 to 281, resizeDown)

        // Kéo mép trên lên mép trên 00:00: mở rộng mượt về 00:00–04:41
        val resizeUp = ScheduleGridEngine.snapEmptyResizeStartInGap(
            origStartMin = 225,
            origEndMin = 281,
            deltaStartMinutes = -240,
            occupiedBlocks = occupied
        )
        assertEquals(0 to 281, resizeUp)

        // Nếu phía trên có khối 01:00–01:30, kéo mép trên lên mạnh phải dừng tại 01:30 (90)
        val earlierBlock = ScheduleGridBlock(
            recordId = "earlier",
            record = null,
            isActiveTimer = false,
            dateLocal = day.toString(),
            startMinute = 60,
            endMinute = 90,
            totalDurationMinutes = 30,
            title = "Khối trước đó",
            category = TimeCategory.WORK,
            visualState = ScheduleBlockVisualState.COMPLETED
        )
        val resizeUpBlocked = ScheduleGridEngine.snapEmptyResizeStartInGap(
            origStartMin = 225,
            origEndMin = 281,
            deltaStartMinutes = -200,
            occupiedBlocks = listOf(earlierBlock) + occupied
        )
        assertEquals(90 to 281, resizeUpBlocked)
    }

    @Test
    fun `r5 closure empty slot defaults future windows to planned and past today slots to reported timed`() {
        val today = "2026-11-24"
        val nowMin = 4 * 60 + 48 // 04:48
        assertFalse(ScheduleGridEngine.shouldDefaultEmptySlotAsPlanned(today, 3 * 60 + 45, today, nowMin))
        assertTrue(ScheduleGridEngine.shouldDefaultEmptySlotAsPlanned(today, 5 * 60 + 50, today, nowMin))
        assertTrue(ScheduleGridEngine.shouldDefaultEmptySlotAsPlanned("2026-11-25", 0, today, nowMin))
    }

    @Test
    fun `r5 closure tasks summary banner separates overdue alerts from due soon`() {
        val overdueOnly = buildTodoSummaryBannerState(
            todoRiskAlerts = listOf(
                TodoRiskAlert(
                    taskId = "t1",
                    taskTitle = "Ôn thi hệ thống",
                    kind = TodoRiskKind.OVERDUE,
                    severityHigh = true,
                    detailVi = "Quá hạn 00:10"
                )
            ),
            proposalCount = 0,
            inboxCount = 0
        )
        assertEquals("⚠ 1 quá hạn (Ôn thi hệ thống)", overdueOnly.label)
        assertTrue(overdueOnly.hasOverdue)

        val mixed = buildTodoSummaryBannerState(
            todoRiskAlerts = listOf(
                TodoRiskAlert("t1", "Ôn thi hệ thống", TodoRiskKind.OVERDUE, true, "Quá hạn"),
                TodoRiskAlert("t2", "Soạn báo cáo", TodoRiskKind.DUE_TODAY_SOON, true, "Gần hạn")
            ),
            proposalCount = 1,
            inboxCount = 2
        )
        assertEquals("✦ 1 gợi ý xếp lịch · ⚠ 1 quá hạn · 1 gần hạn · 2 Inbox", mixed.label)
        assertTrue(mixed.hasOverdue)
    }

    @Test
    fun `r5 closure embedded schedule grid height fits 320dp viewport cleanly above bottom nav`() {
        assertEquals(248, computeEmbeddedGridHeightDp(screenHeightDp = 568, chromeReserveDp = 320))
        assertEquals(524, computeEmbeddedGridHeightDp(screenHeightDp = 844, chromeReserveDp = 320))
        assertEquals(322, computeEmbeddedGridHeightDp(screenHeightDp = 568, chromeReserveDp = 246))
    }
}
