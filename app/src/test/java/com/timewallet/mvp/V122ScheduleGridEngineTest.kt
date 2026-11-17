package com.timewallet.mvp

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ScheduleBlockVisualState
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class V122ScheduleGridEngineTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 18)
    private val todayStr = today.toString()

    private fun ms(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `plan vs actual shows both 60m planned and 72m actual side by side without auto-completing plan`() {
        val nowMs = ms(today, 16, 0)
        val plannedRead = LedgerRecord(
            id = "p_read", source = "manual", layer = TimeLayer.PLANNED, createdAt = ms(today, 8, 0),
            userConfirmedAt = ms(today, 8, 0), category = TimeCategory.LEARN, title = "Đọc sách (KH)",
            date = todayStr, durationSeconds = 3600L, startedAt = ms(today, 14, 0), endedAt = ms(today, 15, 0),
            timezone = zone.id, status = "PLANNED", idempotencyKey = "k_p_read",
            plannedStartedAt = ms(today, 14, 0), plannedEndedAt = ms(today, 15, 0), plannedDurationSeconds = 3600L
        )
        val actualRead = LedgerRecord(
            id = "a_read", source = "timer", layer = TimeLayer.MEASURED, createdAt = ms(today, 14, 0),
            userConfirmedAt = ms(today, 15, 12), category = TimeCategory.LEARN, title = "Đọc sách",
            date = todayStr, durationSeconds = 72 * 60L, startedAt = ms(today, 14, 0), endedAt = ms(today, 15, 12),
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k_a_read",
            plannedStartedAt = ms(today, 14, 0), plannedEndedAt = ms(today, 15, 0), plannedDurationSeconds = 3600L
        )

        val dayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = today,
            records = listOf(plannedRead, actualRead),
            nowEpochMs = nowMs,
            zone = zone
        )

        assertEquals(2, dayModel.timedBlocks.size)
        val planBlock = dayModel.timedBlocks.first { it.recordId == "p_read" }
        val actualBlock = dayModel.timedBlocks.first { it.recordId == "a_read" }

        // Vì planned end (15:00) đã qua lúc 16:00 và chưa xác nhận -> UNCONFIRMED; actual -> COMPLETED
        assertEquals(ScheduleBlockVisualState.UNCONFIRMED, planBlock.visualState)
        assertEquals(ScheduleBlockVisualState.COMPLETED, actualBlock.visualState)
        assertEquals(60, planBlock.totalDurationMinutes)
        assertEquals(72, actualBlock.totalDurationMinutes)
        assertEquals("KH 1h → TT 1h 12p (+12p)", actualBlock.planComparisonText)

        // Xếp 2 cột song song để hiện rõ cả hai trên cùng trục giờ 14:00–15:12
        assertEquals(2, planBlock.laneCount)
        assertEquals(2, actualBlock.laneCount)
        assertTrue(planBlock.laneIndex != actualBlock.laneIndex)
        assertEquals(72, dayModel.actualMinutesTotal)
        assertEquals(60, dayModel.plannedMinutesTotal)
        assertEquals(1, dayModel.unconfirmedCount)
    }

    @Test
    fun `overnight activity splits cleanly across yesterday and today and untimed stays off timeline`() {
        val yesterday = today.minusDays(1)
        val nowMs = ms(today, 10, 0)
        val overnightSleep = LedgerRecord(
            id = "sleep_1", source = "manual", layer = TimeLayer.REPORTED_TIMED, createdAt = ms(today, 7, 0),
            userConfirmedAt = ms(today, 7, 0), category = TimeCategory.SLEEP, title = "Ngủ",
            date = yesterday.toString(), durationSeconds = 8 * 3600L,
            startedAt = ms(yesterday, 22, 0), endedAt = ms(today, 6, 0),
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k_sleep_1"
        )
        val untimedRead = LedgerRecord(
            id = "u_read", source = "manual", layer = TimeLayer.REPORTED_UNTIMED, createdAt = ms(today, 8, 30),
            userConfirmedAt = ms(today, 8, 30), category = TimeCategory.LEARN, title = "Đọc sách 30p",
            date = todayStr, durationSeconds = 1800L, startedAt = null, endedAt = null,
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k_u_read"
        )
        val futurePlan = LedgerRecord(
            id = "fut_1", source = "manual", layer = TimeLayer.PLANNED, createdAt = ms(today, 9, 0),
            userConfirmedAt = ms(today, 9, 0), category = TimeCategory.WORK, title = "Review PR",
            date = todayStr, durationSeconds = 3600L, startedAt = ms(today, 15, 0), endedAt = ms(today, 16, 0),
            timezone = zone.id, status = "PLANNED", idempotencyKey = "k_fut_1"
        )
        val activeTimer = ActiveTimerSession(
            title = "Code Lịch trình",
            categoryKey = "work",
            startedAtEpochMs = ms(today, 9, 0),
            startedElapsedRealtimeMs = 0L,
            timezone = zone.id
        )

        val todayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = today,
            records = listOf(overnightSleep, untimedRead, futurePlan),
            activeTimer = activeTimer,
            nowEpochMs = nowMs,
            zone = zone
        )

        // Lưới giờ chỉ chứa Sleep morning slice (00:00-06:00), Active Timer (09:00-10:00), Future Plan (15:00-16:00)
        assertEquals(3, todayModel.timedBlocks.size)
        val sleepToday = todayModel.timedBlocks.first { it.recordId == "sleep_1" }
        assertEquals(0, sleepToday.startMinute)
        assertEquals(360, sleepToday.endMinute)
        assertTrue(sleepToday.crossesMidnightFromPrev)
        assertFalse(sleepToday.crossesMidnightToNext)

        val timerBlock = todayModel.timedBlocks.first { it.isActiveTimer }
        assertEquals(ScheduleBlockVisualState.ACTIVE, timerBlock.visualState)
        assertEquals(9 * 60, timerBlock.startMinute)
        assertEquals(10 * 60, timerBlock.endMinute)

        val planBlock = todayModel.timedBlocks.first { it.recordId == "fut_1" }
        assertEquals(ScheduleBlockVisualState.PLANNED, planBlock.visualState)

        // Bản ghi chỉ có thời lượng KHÔNG được tự gán mốc giờ lên timeline
        assertEquals(1, todayModel.untimedRecords.size)
        assertEquals("u_read", todayModel.untimedRecords.single().id)
        assertTrue(todayModel.freeGaps.any { it.startMinute == 360 && it.endMinute == 540 }) // 06:00 - 09:00 trống

        // Tổng thực tế gồm: 6h ngủ hôm nay + 1h active timer + 30p chưa gắn giờ = 450p; Kế hoạch = 60p
        assertEquals(450, todayModel.actualMinutesTotal)
        assertEquals(60, todayModel.plannedMinutesTotal)
    }

    @Test
    fun `drag move and edge resize update planned record without altering actual history`() {
        val nowMs = ms(today, 10, 0)
        val planned = LedgerRecord(
            id = "plan_x", source = "manual", layer = TimeLayer.PLANNED, createdAt = nowMs, userConfirmedAt = nowMs,
            category = TimeCategory.WORK, title = "Thiết kế UI", date = todayStr, durationSeconds = 3600L,
            startedAt = ms(today, 14, 0), endedAt = ms(today, 15, 0), timezone = zone.id, status = "PLANNED",
            idempotencyKey = "k_plan_x"
        )
        val actual = LedgerRecord(
            id = "act_1", source = "manual", layer = TimeLayer.REPORTED_TIMED, createdAt = nowMs, userConfirmedAt = nowMs,
            category = TimeCategory.HEALTH, title = "Chạy bộ", date = todayStr, durationSeconds = 1800L,
            startedAt = ms(today, 7, 0), endedAt = ms(today, 7, 30), timezone = zone.id, status = "CONFIRMED",
            idempotencyKey = "k_act_1"
        )

        val (movedStart, movedEnd) = ScheduleGridEngine.snapMoveRange(14 * 60, 15 * 60, deltaMinutes = 40)
        assertEquals(14 * 60 + 45, movedStart)
        assertEquals(15 * 60 + 45, movedEnd)

        val (resizedStart, resizedEnd) = ScheduleGridEngine.snapResizeRange(movedStart, movedEnd, deltaEndMinutes = 30)
        assertEquals(14 * 60 + 45, resizedStart)
        assertEquals(16 * 60 + 15, resizedEnd)

        val res = ConfirmationEngine.moveOrResizeScheduleBlock(
            existing = planned,
            newDateLocal = todayStr,
            newStartHm = "14:45",
            newEndHm = "16:15",
            existingRecords = listOf(planned, actual),
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertTrue(res is ConfirmationActionResult.Applied)
        val updated = (res as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(TimeLayer.PLANNED, updated.layer)
        assertEquals("PLANNED", updated.status)
        assertEquals(90 * 60L, updated.durationSeconds)
        assertEquals(ms(today, 14, 45), updated.startedAt)
        assertEquals(ms(today, 16, 15), updated.endedAt)
    }

    @Test
    fun `month grid marks free days and parses quick duration input without inventing bounds`() {
        val nowMs = ms(today, 10, 0)
        val rec = LedgerRecord(
            id = "r1", source = "manual", layer = TimeLayer.REPORTED_TIMED, createdAt = nowMs, userConfirmedAt = nowMs,
            category = TimeCategory.WORK, title = "Sprint", date = todayStr, durationSeconds = 7200L,
            startedAt = ms(today, 8, 0), endedAt = ms(today, 10, 0), timezone = zone.id, status = "CONFIRMED",
            idempotencyKey = "k_r1"
        )
        val monthCells = ScheduleGridEngine.buildMonthGrid(YearMonth.of(2026, 11), listOf(rec), nowEpochMs = nowMs, zone = zone)
        assertEquals(42, monthCells.size)
        val todayCell = monthCells.first { it.dateLocal == todayStr }
        assertTrue(todayCell.isToday)
        assertFalse(todayCell.isFreeDay)
        assertEquals(120, todayCell.actualMinutes)

        val tomorrowCell = monthCells.first { it.dateLocal == today.plusDays(1).toString() }
        assertTrue(tomorrowCell.isFreeDay)

        val quick = ManualEntry.parseQuickInput("Đọc sách 30 phút", today = today)
        assertNotNull(quick)
        assertEquals("Đọc sách", quick!!.cleanTitle)
        assertEquals("learn", quick.categoryKey)
        assertEquals(30, quick.durationMinutes)
        assertNull("Không tự gán giờ bắt đầu cho câu chỉ có thời lượng", quick.startHm)
        assertNull("Không tự gán giờ kết thúc cho câu chỉ có thời lượng", quick.endHm)

        val readingTag = Tag(
            id = "tag_doc_sach",
            name = "Đọc sách",
            nameKey = "doc sach",
            category = TimeCategory.LEARN,
            archivedAt = null,
            createdAt = nowMs,
            updatedAt = nowMs
        )
        val unaccentedQuick = ManualEntry.parseQuickInput(
            "Doc sach 30 phut",
            tags = listOf(readingTag),
            today = today
        )
        assertNotNull(unaccentedQuick)
        assertEquals(30, unaccentedQuick!!.durationMinutes)
        assertEquals("learn", unaccentedQuick.categoryKey)
        assertNull("Không tự gán giờ bắt đầu cho câu không dấu chỉ có thời lượng", unaccentedQuick.startHm)
    }
}
