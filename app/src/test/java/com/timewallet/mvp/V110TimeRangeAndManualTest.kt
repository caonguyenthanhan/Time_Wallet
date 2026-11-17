package com.timewallet.mvp

import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ManualEntryValidation
import com.timewallet.mvp.domain.usecase.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v1.1.0 (A, L2): nhập theo khoảng giờ (14:00–15:12 = 72 phút), kết thúc "Bây giờ", chỉ-thời-lượng không bị ép mốc giờ, và đi qua đúng bộ kiểm tra của sổ cái.
 */
class V110TimeRangeAndManualTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 18)
    private val nowMs = LocalDateTime.of(2026, 11, 18, 16, 30).atZone(zone).toInstant().toEpochMilli()

    // ---------- TimeRange ----------
    @Test
    fun `reading from 14h00 to 15h12 is exactly 72 minutes`() {
        val r = TimeRange.compute("14:00", TimeRange.End.At("15:12"), nowHm = "16:30", isToday = true)
        r as TimeRange.Result.Ok
        assertEquals(72, r.minutes)
        assertEquals("14:00", r.startHm); assertEquals("15:12", r.endHm)
        assertEquals("1h 12p", TimeRange.formatDuration(r.minutes))
    }

    @Test
    fun `end now takes the current clock and needs no retyping`() {
        val r = TimeRange.compute("14:00", TimeRange.End.Now, nowHm = "16:30", isToday = true) as TimeRange.Result.Ok
        assertEquals(150, r.minutes); assertEquals("16:30", r.endHm)
    }

    @Test
    fun `end now is refused for a past day and when the start is still in the future`() {
        assertTrue(TimeRange.compute("14:00", TimeRange.End.Now, "16:30", isToday = false) is TimeRange.Result.Invalid)
        assertTrue(TimeRange.compute("17:00", TimeRange.End.Now, "16:30", isToday = true) is TimeRange.Result.Invalid)
        assertTrue("chưa đủ 1 phút", TimeRange.compute("16:30", TimeRange.End.Now, "16:30", isToday = true) is TimeRange.Result.Invalid)
    }

    @Test
    fun `crossing midnight is computed and equal times are invalid not 24 hours`() {
        val r = TimeRange.compute("23:30", TimeRange.End.At("00:15"), "16:30", isToday = false) as TimeRange.Result.Ok
        assertEquals(45, r.minutes); assertTrue(r.crossesMidnight)
        assertTrue(TimeRange.compute("10:00", TimeRange.End.At("10:00"), "16:30", isToday = false) is TimeRange.Result.Invalid)
    }

    @Test
    fun `clock text formats are parsed strictly`() {
        assertEquals(14 * 60, TimeRange.parseMinuteOfDay("14:00"))
        assertEquals(9 * 60 + 5, TimeRange.parseMinuteOfDay("9:05"))
        assertEquals(14 * 60 + 30, TimeRange.parseMinuteOfDay("14h30"))
        assertEquals(14 * 60, TimeRange.parseMinuteOfDay("14h"))
        assertNull(TimeRange.parseMinuteOfDay("24:00")); assertNull(TimeRange.parseMinuteOfDay("12:60")); assertNull(TimeRange.parseMinuteOfDay("abc")); assertNull(TimeRange.parseMinuteOfDay(""))
        assertNull(TimeRange.parseMinuteOfDay("1:2:3"))
    }

    @Test
    fun `digits only input is read as typed on the field without a colon`() {
        assertEquals(14 * 60, TimeRange.parseMinuteOfDay("1400"))
        assertEquals(15 * 60 + 12, TimeRange.parseMinuteOfDay("1512"))
        assertEquals(9 * 60 + 30, TimeRange.parseMinuteOfDay("930"))
        assertEquals(14 * 60, TimeRange.parseMinuteOfDay("14"))
        assertEquals(9 * 60, TimeRange.parseMinuteOfDay("9"))
        assertNull("giờ hai chữ số thiếu phút", TimeRange.parseMinuteOfDay("141"))
        assertNull(TimeRange.parseMinuteOfDay("2460")); assertNull(TimeRange.parseMinuteOfDay("2400")); assertNull(TimeRange.parseMinuteOfDay("12345"))
        val r = TimeRange.compute("1400", TimeRange.End.At("1512"), "16:30", true) as TimeRange.Result.Ok
        assertEquals(72, r.minutes); assertEquals("14:00", r.startHm); assertEquals("15:12", r.endHm)
    }

    // ---------- ManualEntry.validateRange ----------
    private fun range(start: String, end: String, endNow: Boolean = false, date: String = today.toString(), note: String = "", tagId: String? = null, tagName: String? = null, cat: String? = "learn") =
        ManualEntry.validateRange(cat, tagId, tagName, start, end, endNow, date, note, today, nowHm = "16:30")

    @Test
    fun `range entry builds a timed record input with computed minutes and the tag`() {
        val v = range("14:00", "15:12", tagId = "tag_1", tagName = "Đọc sách") as ManualEntryValidation.Valid
        assertEquals(72, v.durationMinutes); assertEquals("14:00", v.startHm); assertEquals("15:12", v.endHm)
        assertEquals("Đọc sách", v.title); assertEquals("tag_1", v.tagId)
        val input = ManualEntry.toBatchInput(v, "s1")
        assertEquals("14:00", input.startHm); assertEquals("15:12", input.endHm); assertEquals(72, input.durationMinutes); assertEquals("tag_1", input.tagId)
    }

    @Test
    fun `range entry through the ledger validator is a REPORTED_TIMED record of exactly 4320 seconds`() {
        val v = range("14:00", "15:12") as ManualEntryValidation.Valid
        val res = LedgerValidator.validateBatchAtomic(listOf(ManualEntry.toBatchInput(v, "s2")), emptyList(), null, nowMs, zone)
        res as BatchValidationResult.Valid
        val r = res.records.single()
        assertEquals(TimeLayer.REPORTED_TIMED, r.layer); assertEquals(4320L, r.durationSeconds); assertNotNull(r.startedAt); assertNotNull(r.endedAt)
        assertEquals("CONFIRMED", r.status)
    }

    @Test
    fun `end now through the ledger validator is a completed timed record and not an active one`() {
        val v = range("14:00", "", endNow = true) as ManualEntryValidation.Valid
        val r = (LedgerValidator.validateBatchAtomic(listOf(ManualEntry.toBatchInput(v, "s3")), emptyList(), null, nowMs, zone) as BatchValidationResult.Valid).records.single()
        assertEquals(TimeLayer.REPORTED_TIMED, r.layer); assertEquals(150 * 60L, r.durationSeconds)
    }

    @Test
    fun `duration only entry is still possible and never invents start or end`() {
        val v = ManualEntry.validate("learn", "0", "30", today.toString(), "", today, tagId = "t", tagName = "Đọc sách") as ManualEntryValidation.Valid
        assertNull(v.startHm); assertNull(v.endHm)
        val r = (LedgerValidator.validateBatchAtomic(listOf(ManualEntry.toBatchInput(v, "s4")), emptyList(), null, nowMs, zone) as BatchValidationResult.Valid).records.single()
        assertEquals(TimeLayer.REPORTED_UNTIMED, r.layer); assertNull(r.startedAt); assertNull(r.endedAt); assertEquals("t", r.tagId)
        assertEquals("Đọc sách", r.title)
    }

    @Test
    fun `range entry accepts future day as PLANNED and ongoing interval as DOING while validating inputs`() {
        val futureDay = range("14:00", "15:00", date = today.plusDays(1).toString()) as ManualEntryValidation.Valid
        assertTrue(futureDay.saveAsPlanned)
        val futureRec = (LedgerValidator.validateBatchAtomic(listOf(ManualEntry.toBatchInput(futureDay, "f1")), emptyList(), null, nowMs, zone) as BatchValidationResult.Valid).records.single()
        assertEquals(TimeLayer.PLANNED, futureRec.layer)

        val ongoing = range("15:00", "17:00") as ManualEntryValidation.Valid
        val ongoingRec = (LedgerValidator.validateBatchAtomic(listOf(ManualEntry.toBatchInput(ongoing, "o1")), emptyList(), null, nowMs, zone) as BatchValidationResult.Valid).records.single()
        assertEquals(TimeLayer.ACTIVE, ongoingRec.layer)

        assertTrue(range("14:00", "15:00", date = today.minusDays(1).toString()) is ManualEntryValidation.Valid)
        assertTrue("chưa chọn nhóm", range("14:00", "15:00", cat = null) is ManualEntryValidation.Invalid)
    }

    @Test
    fun `an overlapping range is rejected by the ledger like any timed record`() {
        val first = range("14:00", "15:12") as ManualEntryValidation.Valid
        val rec = (LedgerValidator.validateBatchAtomic(listOf(ManualEntry.toBatchInput(first, "a")), emptyList(), null, nowMs, zone) as BatchValidationResult.Valid).records.single()
        val second = range("14:30", "15:00") as ManualEntryValidation.Valid
        val res = LedgerValidator.validateBatchAtomic(listOf(ManualEntry.toBatchInput(second, "b")), listOf(rec), null, nowMs, zone)
        assertTrue(res is BatchValidationResult.Invalid)
    }
}
