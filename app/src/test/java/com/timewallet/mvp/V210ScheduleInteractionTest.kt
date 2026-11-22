package com.timewallet.mvp

import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ManualEntryValidation
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Kiểm chứng toàn bộ 6 bất biến tương tác Lịch trình & hợp đồng luồng thống nhất:
 * 1) 18:00-21:00 = 180 phút.
 * 2) Dời sang 19:00-22:00 giữ nguyên 180 phút.
 * 3) Kéo mép kết thúc 21:00 -> 20:30 cập nhật thành 150 phút.
 * 4) 21:00-24:00 xử lý đúng ranh giới ngày (180 phút, đúng ngày gốc, không rò ngày kế tiếp).
 * 5) Chặn trùng giờ thực tế & không tự biến Dự kiến thành Đã thực hiện.
 * 6) Kéo/chọn dùng trạng thái nháp preview + có thể Hủy trước khi ghi; khay Chưa gắn giờ tách biệt & luôn hiện trên đầu lưới.
 */
class V210ScheduleInteractionTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 22)
    private val todayStr = today.toString()

    private fun ms(date: LocalDate, h: Int, m: Int = 0): Long =
        date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `invariant 1 2 3 move preserves 180m and top or bottom edge resize updates exact duration`() {
        val nowMs = ms(today, 23, 0)
        val rec18to21 = LedgerRecord(
            id = "rec_18_21",
            source = "manual",
            layer = TimeLayer.REPORTED_TIMED,
            createdAt = ms(today, 21, 0),
            userConfirmedAt = ms(today, 21, 0),
            category = TimeCategory.WORK,
            title = "Code Lịch trình",
            date = todayStr,
            durationSeconds = 180 * 60L,
            startedAt = ms(today, 18, 0),
            endedAt = ms(today, 21, 0),
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "k_18_21",
            confirmationStateRaw = ConfirmationState.COMPLETED.name
        )

        // 1. 18:00–21:00 luôn là 180 phút
        assertEquals(180, rec18to21.durationMinutes)
        val day1 = ScheduleGridEngine.buildDayScheduleModel(today, listOf(rec18to21), nowEpochMs = nowMs, zone = zone)
        assertEquals(180, day1.timedBlocks.single().totalDurationMinutes)
        assertEquals(1080, day1.timedBlocks.single().startMinute)
        assertEquals(1260, day1.timedBlocks.single().endMinute)

        // 2. Dời sang 19:00–22:00 giữ nguyên 180 phút
        val (movedStart, movedEnd) = ScheduleGridEngine.snapMoveRange(18 * 60, 21 * 60, deltaMinutes = 60)
        assertEquals(19 * 60, movedStart)
        assertEquals(22 * 60, movedEnd)
        assertEquals(180, movedEnd - movedStart)

        val moveRes = ConfirmationEngine.moveOrResizeScheduleBlock(
            existing = rec18to21,
            newDateLocal = todayStr,
            newStartHm = TimeRange.formatHm(movedStart),
            newEndHm = ScheduleGridEngine.formatHmEnd(movedEnd),
            existingRecords = listOf(rec18to21),
            nowEpochMs = nowMs,
            zoneId = zone
        ) as ConfirmationActionResult.Applied
        val movedRec = moveRes.updatedRecords.single()
        assertEquals(180, movedRec.durationMinutes)
        assertEquals(ms(today, 19, 0), movedRec.startedAt)
        assertEquals(ms(today, 22, 0), movedRec.endedAt)

        // 3. Kéo mép kết thúc từ 21:00 xuống 20:30 -> 150 phút
        val (resStart, resEnd) = ScheduleGridEngine.snapResizeRange(18 * 60, 21 * 60, deltaEndMinutes = -30)
        assertEquals(18 * 60, resStart)
        assertEquals(20 * 60 + 30, resEnd)
        assertEquals(150, resEnd - resStart)

        val resizeRes = ConfirmationEngine.moveOrResizeScheduleBlock(
            existing = rec18to21,
            newDateLocal = todayStr,
            newStartHm = TimeRange.formatHm(resStart),
            newEndHm = ScheduleGridEngine.formatHmEnd(resEnd),
            existingRecords = listOf(rec18to21),
            nowEpochMs = nowMs,
            zoneId = zone
        ) as ConfirmationActionResult.Applied
        assertEquals(150, resizeRes.updatedRecords.single().durationMinutes)

        // Bonus: kéo mép TRÊN từ 18:00 xuống 18:30 giữ nguyên 21:00 -> 150 phút
        val (topStart, topEnd) = ScheduleGridEngine.snapResizeStartRange(18 * 60, 21 * 60, deltaStartMinutes = 30)
        assertEquals(18 * 60 + 30, topStart)
        assertEquals(21 * 60, topEnd)
        assertEquals(150, topEnd - topStart)
    }

    @Test
    fun `invariant 4 activity 21h00 to 24h00 handles day boundary cleanly without wrong day or duration`() {
        // Hỗ trợ cả "24:00" ở đầu vào form lẫn "24:00"/"00:00" ở validator; start="24:00" vẫn bị từ chối
        assertNull(TimeRange.parseMinuteOfDay("24:00"))
        assertEquals(1440, TimeRange.parseEndMinuteOfDay("24:00"))
        assertEquals(1440, TimeRange.parseEndMinuteOfDay("2400"))

        val tr = TimeRange.compute("21:00", TimeRange.End.At("24:00"), nowHm = "23:55", isToday = true) as TimeRange.Result.Ok
        assertEquals(180, tr.minutes)
        assertEquals("21:00", tr.startHm)
        assertEquals("24:00", tr.endHm)
        assertFalse(tr.crossesMidnight)

        val parsed = LedgerValidator.parseTimeRangeToEpochMillis(todayStr, "21:00", "24:00", zone)!!
        assertEquals(ms(today, 21, 0), parsed.first)
        assertEquals(ms(today.plusDays(1), 0, 0), parsed.second)
        assertEquals(180 * 60_000L, parsed.second - parsed.first)

        val validManual = ManualEntry.validateRange(
            categoryKey = TimeCategory.LEARN.key,
            tagId = null,
            tagName = null,
            startText = "2100",
            endText = "2400",
            endIsNow = false,
            dateLocal = todayStr,
            note = "Đọc sách tối muộn",
            today = today,
            nowHm = "23:55",
            saveAsPlanned = false
        ) as ManualEntryValidation.Valid
        assertEquals(180, validManual.durationMinutes)

        val batch = LedgerValidator.validateBatchAtomic(
            listOf(ManualEntry.toBatchInput(validManual, "s_2400")),
            existingRecords = emptyList(),
            activeTimer = null,
            nowEpochMs = ms(today.plusDays(1), 0, 5),
            zoneId = zone
        ) as BatchValidationResult.Valid
        val saved = batch.records.single()
        assertEquals(todayStr, saved.date)
        assertEquals(10_800L, saved.durationSeconds)
        assertEquals(ms(today.plusDays(1), 0, 0), saved.endedAt)

        // Lưới hôm nay hiện 21:00..24:00 = 180p và không tràn khối ma sang ngày mai
        val todayGrid = ScheduleGridEngine.buildDayScheduleModel(today, listOf(saved), nowEpochMs = ms(today.plusDays(1), 0, 5), zone = zone)
        assertEquals(1, todayGrid.timedBlocks.size)
        assertEquals(1260, todayGrid.timedBlocks.single().startMinute)
        assertEquals(1440, todayGrid.timedBlocks.single().endMinute)
        assertFalse(todayGrid.timedBlocks.single().crossesMidnightToNext)

        val tomorrowGrid = ScheduleGridEngine.buildDayScheduleModel(today.plusDays(1), listOf(saved), nowEpochMs = ms(today.plusDays(1), 0, 5), zone = zone)
        assertTrue(tomorrowGrid.timedBlocks.isEmpty())
    }

    @Test
    fun `invariant 5 conflict detection and plan vs actual separation never overwrite or auto-complete`() {
        val nowMs = ms(today, 12, 0)
        val existingActual = LedgerRecord(
            id = "act_morning",
            source = "manual",
            layer = TimeLayer.REPORTED_TIMED,
            createdAt = ms(today, 9, 0),
            userConfirmedAt = ms(today, 9, 0),
            category = TimeCategory.WORK,
            title = "Deep Work",
            date = todayStr,
            durationSeconds = 3600L,
            startedAt = ms(today, 9, 0),
            endedAt = ms(today, 10, 0),
            timezone = zone.id,
            status = "CONFIRMED",
            idempotencyKey = "k_act_m"
        )
        val otherActual = existingActual.copy(
            id = "act_11",
            title = "Họp nhóm",
            startedAt = ms(today, 11, 0),
            endedAt = ms(today, 12, 0),
            idempotencyKey = "k_act_11"
        )
        val plannedFuture = existingActual.copy(
            id = "plan_evening",
            layer = TimeLayer.PLANNED,
            title = "Chạy bộ (KH)",
            startedAt = ms(today, 17, 0),
            endedAt = ms(today, 18, 0),
            status = "PLANNED",
            confirmationStateRaw = ConfirmationState.PLANNED.name,
            idempotencyKey = "k_plan_ev"
        )

        val dayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = today,
            records = listOf(existingActual, otherActual, plannedFuture),
            nowEpochMs = nowMs,
            zone = zone
        )

        // Kéo bản ghi thực tế sang vùng trùng với otherActual (10:30–11:30) -> phát hiện hard conflict
        val gridCheck = ScheduleGridEngine.findConflictingBlocks(
            candidateStartMin = 10 * 60 + 30,
            candidateEndMin = 11 * 60 + 30,
            excludeRecordId = existingActual.id,
            isPlannedCandidate = false,
            dayBlocks = dayModel.timedBlocks
        )
        assertTrue(gridCheck.hasBlockingOrWarningConflict)
        assertEquals("Họp nhóm", gridCheck.hardConflicts.single().title)

        // ConfirmationEngine cũng chặn/yêu cầu đối soát, tuyệt đối không ghi đè im lặng
        val moveConflict = ConfirmationEngine.moveOrResizeScheduleBlock(
            existing = existingActual,
            newDateLocal = todayStr,
            newStartHm = "10:30",
            newEndHm = "11:30",
            existingRecords = listOf(existingActual, otherActual, plannedFuture),
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertTrue(moveConflict is ConfirmationActionResult.Overlap)

        // Dời hoạt động thực tế vào giờ tương lai (15:00-16:00) không được tự biến thành Dự kiến hay vượt quyền
        val moveIntoFuture = ConfirmationEngine.moveOrResizeScheduleBlock(
            existing = existingActual,
            newDateLocal = todayStr,
            newStartHm = "15:00",
            newEndHm = "16:00",
            existingRecords = listOf(existingActual, otherActual, plannedFuture),
            nowEpochMs = nowMs,
            zoneId = zone
        )
        assertTrue(moveIntoFuture is ConfirmationActionResult.Invalid)

        // Dời bản ghi Dự kiến sang quá khứ (08:00-09:00) không tự biến thành Đã thực hiện
        val movePlanEarlier = ConfirmationEngine.moveOrResizeScheduleBlock(
            existing = plannedFuture,
            newDateLocal = todayStr,
            newStartHm = "08:00",
            newEndHm = "09:00",
            existingRecords = listOf(existingActual, otherActual, plannedFuture),
            nowEpochMs = nowMs,
            zoneId = zone
        ) as ConfirmationActionResult.Applied
        assertEquals(TimeLayer.PLANNED, movePlanEarlier.updatedRecords.single().layer)
        assertEquals("PLANNED", movePlanEarlier.updatedRecords.single().status)
    }

    @Test
    fun `drag empty range, untimed placement window, pinned tray and cancelable draft wiring hold across UI`() {
        val (dragS, dragE) = ScheduleGridEngine.snapEmptyDragRange(14 * 60 + 10, 15 * 60 + 40)
        assertEquals(14 * 60, dragS)
        assertEquals(15 * 60 + 45, dragE)

        val (uS, uE) = ScheduleGridEngine.buildUntimedPlacementWindow(22 * 60 + 45, durationMinutes = 90)
        assertEquals(22 * 60 + 30, uS)
        assertEquals(24 * 60, uE)

        val scheduleSrc = File("src/main/java/com/timewallet/mvp/ui/screens/ScheduleCalendarView.kt").readText()
        // Khay Chưa gắn giờ được ghim ở phần trên cùng trước lưới lịch (không phụ thuộc cuộn xuống đáy)
        assertTrue(scheduleSrc.indexOf("PinnedScheduleUtilityStrip(") in 1 until scheduleSrc.indexOf("DayTimelineGrid("))
        assertTrue(scheduleSrc.indexOf("PinnedScheduleUtilityStrip(") in 1 until scheduleSrc.indexOf("MultiDayTimelineGrid("))
        // Có đầy đủ trạng thái bản nháp trước khi lưu + nút Hủy + tay nắm mép trên/dưới + đặt chưa gắn giờ
        assertTrue(scheduleSrc.contains("ScheduleInteractionState.BlockDraftTransform"))
        assertTrue(scheduleSrc.contains("DraftTransformActionBar("))
        assertTrue(scheduleSrc.contains("UntimedPlacementActionBar("))
        assertTrue(scheduleSrc.contains("snapResizeStartRange("))
        assertTrue(scheduleSrc.contains("onPlaceUntimedOnSlot"))
        assertTrue(scheduleSrc.contains("Đặt vào lịch"))

        val vmSrc = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        assertTrue(vmSrc.contains("fun placeUntimedRecordOnSlot("))
    }
}
