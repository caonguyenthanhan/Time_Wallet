package com.timewallet.mvp

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** WALLY A12: Dashboard rỗng / hỗn hợp / không mốc giờ — số đúng, không NaN, không suy diễn thời gian rảnh. */
class WallyDashboardSummaryTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = LocalDate.of(2026, 11, 13)
    private val nowMs = day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun untimed(cat: String, minutes: Int, existing: List<LedgerRecord> = emptyList()) =
        (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "t", rawCategory = cat, dateLocal = day.toString(), inputDurationMinutes = minutes,
            startedAt = null, endedAt = null, existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record

    private fun timed(cat: String, from: String, to: String, existing: List<LedgerRecord> = emptyList()): LedgerRecord {
        val r = LedgerValidator.parseTimeRangeToEpochMillis(day.toString(), from, to, zone)!!
        return (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "t", rawCategory = cat, dateLocal = day.toString(), inputDurationMinutes = null,
            startedAt = r.first, endedAt = r.second, existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record
    }

    private fun summary(records: List<LedgerRecord>, date: LocalDate = day, now: Long = nowMs): DashboardSummary =
        DashboardSummary.from(LedgerAnalyticsCalculator.computeDayStats(date.toString(), records, zone, now))

    @Test
    fun `empty day gives a clear zero state with no slices and no NaN`() {
        val s = summary(emptyList())
        assertTrue(s.isEmpty)
        assertEquals(0L, s.recordedSeconds)
        assertTrue(s.slices.isEmpty())
        assertEquals(0f, s.progressFraction, 0f)
        assertFalse(s.progressFraction.isNaN())
        assertEquals(s.calendarDaySeconds, s.unrecordedSeconds)
        assertEquals(86_400L, s.calendarDaySeconds)
    }

    @Test
    fun `untimed manual hours count as recorded for the day and form the donut but never enter timed seconds`() {
        val s = summary(listOf(untimed("learn", 90)))
        assertEquals(5400L, s.recordedSeconds)
        assertEquals(0L, s.timedSeconds)
        assertEquals(5400L, s.untimedSeconds)
        assertEquals(86_400L - 5400L, s.unrecordedSeconds)
        assertEquals(1, s.slices.size)
        assertEquals(TimeCategory.LEARN, s.slices[0].category)
        assertEquals(1f, s.slices[0].fraction, 0.0001f)
        assertEquals(100, s.slices[0].percent)
    }

    @Test
    fun `mixed timed and untimed merge by category and fractions are shares of the recorded total`() {
        val t = timed("work", "09:00", "11:00")                       // 2h work có giờ
        val u1 = untimed("work", 60, listOf(t))                       // 1h work chưa gắn giờ
        val u2 = untimed("learn", 60, listOf(t, u1))                  // 1h learn chưa gắn giờ
        val s = summary(listOf(t, u1, u2))
        assertEquals(4 * 3600L, s.recordedSeconds)
        assertEquals(2, s.slices.size)
        assertEquals(TimeCategory.WORK, s.slices[0].category)
        assertEquals(3 * 3600L, s.slices[0].seconds)
        assertEquals(0.75f, s.slices[0].fraction, 0.0001f)
        assertEquals(0.25f, s.slices[1].fraction, 0.0001f)
        assertEquals(1f, s.slices.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
        assertEquals("trục giờ chỉ có phần có mốc giờ", 2 * 3600L, s.timedSeconds)
        assertEquals(86_400L - 4 * 3600L, s.unrecordedSeconds)
        assertEquals(4f / 24f, s.progressFraction, 0.0001f)
    }

    @Test
    fun `planned time is never counted as recorded`() {
        val future = timed("work", "20:00", "22:00") // sau 18:00 => dự kiến
        val s = summary(listOf(future))
        assertEquals(0L, s.recordedSeconds)
        assertEquals(7200L, s.plannedSeconds)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `recorded above 24 hours clamps the bar and never produces a negative unrecorded value`() {
        var recs = emptyList<LedgerRecord>()
        repeat(3) { i -> recs = recs + untimed(if (i == 0) "work" else "learn", 600, recs) }  // 30h chưa gắn giờ
        val s = summary(recs)
        assertEquals(30 * 3600L, s.recordedSeconds)
        assertTrue(s.recordedExceedsDay)
        assertEquals(0L, s.unrecordedSeconds)
        assertEquals(1f, s.progressFraction, 0f)
    }

    @Test
    fun `a daylight saving day uses its real length instead of a fixed 24 hours`() {
        val ny = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 3, 8) // 23 giờ
        val nowNy = dstDay.atTime(20, 0).atZone(ny).toInstant().toEpochMilli()
        val stats = LedgerAnalyticsCalculator.computeDayStats(dstDay.toString(), emptyList(), ny, nowNy)
        val s = DashboardSummary.from(stats)
        assertEquals(23 * 3600L, s.calendarDaySeconds)
        assertEquals(23 * 3600L, s.unrecordedSeconds)
    }
}
