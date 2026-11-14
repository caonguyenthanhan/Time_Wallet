package com.timewallet.mvp

import com.timewallet.mvp.data.local.TimerCommit
import com.timewallet.mvp.data.local.TimerCommitResult
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.TimerSegments
import com.timewallet.mvp.domain.usecase.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WALLY v1.0.6 — R01: phiên Đồng hồ > 24 giờ phải lưu được, không mất thời gian, không ghi trùng.
 * Chạy NGUYÊN đường ghi thật (TimerCommit -> LedgerValidator -> commitBatchRecordsGuarded) trên DAO trong RAM.
 *
 * NHÃN TRUNG THỰC cho RED: ViewModel v1.0.5 không dựng được trên JVM. Mã v1.0.5 của stopAndSaveTimer là MỘT lần gọi
 * validateAndBuildRecord cho cả phiên; test `v105 single-record path` bên dưới chạy đúng lời gọi đó (và PASS trên cả hai bản)
 * để chứng minh nguyên nhân: phiên > 24h bị từ chối, người dùng chỉ còn đường Hủy => mất toàn bộ thời gian.
 */
class V106TimerLongSessionTest {

    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private val ny = ZoneId.of("America/New_York")

    private fun ms(z: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int = 0, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(z).toInstant().toEpochMilli()

    private fun commit(dao: InMemoryLedgerDao, start: Long, end: Long, zone: ZoneId = vn) =
        runBlocking { TimerCommit.run(dao, "Làm việc tập trung", TimeCategory.WORK.key, start, end, zone) }

    private fun rows(dao: InMemoryLedgerDao) = runBlocking { dao.getAllRecordsSnapshot() }.sortedBy { it.startedAt }

    private fun assertContiguousAndTotal(dao: InMemoryLedgerDao, start: Long, end: Long, segments: Int) {
        val r = rows(dao)
        assertEquals(segments, r.size)
        assertEquals(start, r.first().startedAt)
        assertEquals(end, r.last().endedAt)
        r.zipWithNext().forEach { (a, b) -> assertEquals("các đoạn phải sát nhau, không hở không chồng", a.endedAt, b.startedAt) }
        r.forEach { assertTrue("mỗi đoạn <= 24h: ${it.durationSeconds}", it.durationSeconds in 1L..86_400L) }
        assertEquals("tổng các đoạn = cả phiên", (end - start) / 1000L, r.sumOf { it.durationSeconds })
    }

    // ---------------------------------------------------------------- v1.0.5 gốc rễ
    @Test
    fun `v105 single-record path rejects a session longer than 24h, which left only Cancel`() {
        val start = ms(vn, 2026, 11, 10, 8)
        val end = start + 25 * 3_600_000L
        val v = LedgerValidator.validateAndBuildRecord(
            source = "timer", rawTitle = "x", rawCategory = "work", dateLocal = "2026-11-10", inputDurationMinutes = null,
            startedAt = start, endedAt = end, existingRecords = emptyList(), nowEpochMs = end, zoneId = vn,
            idempotencyKey = "timer_$start"
        )
        assertTrue(v is ValidationResult.Invalid)
    }

    // ---------------------------------------------------------------- các mốc biên
    @Test
    fun `exactly 24h is one record`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        val r = commit(dao, start, start + 86_400_000L)
        assertTrue(r is TimerCommitResult.Saved && r.segments == 1)
        assertContiguousAndTotal(dao, start, start + 86_400_000L, 1)
    }

    @Test
    fun `24h plus 1s becomes two segments and loses no second`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        val r = commit(dao, start, start + 86_401_000L)
        assertTrue(r is TimerCommitResult.Saved && r.segments == 2 && r.totalSeconds == 86_401L)
        assertContiguousAndTotal(dao, start, start + 86_401_000L, 2)
        assertEquals(listOf(86_400L, 1L), rows(dao).map { it.durationSeconds })
    }

    @Test
    fun `25 hours`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        commit(dao, start, start + 25 * 3_600_000L)
        assertContiguousAndTotal(dao, start, start + 25 * 3_600_000L, 2)
        assertEquals(listOf(86_400L, 3_600L), rows(dao).map { it.durationSeconds })
    }

    @Test
    fun `multi day session of 50h30m is split into three and keeps title category source and timezone`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 22, 15, 30)
        val end = start + (50 * 3600L + 30 * 60L) * 1000L
        val r = commit(dao, start, end)
        assertTrue(r is TimerCommitResult.Saved && r.segments == 3)
        assertContiguousAndTotal(dao, start, end, 3)
        rows(dao).forEach {
            assertEquals("Làm việc tập trung", it.title)
            assertEquals("work", it.category)
            assertEquals("timer", it.source)
            assertEquals("Asia/Ho_Chi_Minh", it.timezone)
            assertEquals("MEASURED", it.layer)
        }
        assertEquals(listOf("2026-11-10", "2026-11-11", "2026-11-12"), rows(dao).map { it.date })
        assertEquals(setOf("timer_$start", "timer_${start}_1", "timer_${start}_2"), rows(dao).map { it.idempotencyKey }.toSet())
    }

    @Test
    fun `a session crossing midnight but within 24h stays one record exactly as in v105`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 23, 30)
        val end = ms(vn, 2026, 11, 11, 0, 30)
        commit(dao, start, end)
        assertContiguousAndTotal(dao, start, end, 1)
        assertEquals("timer_$start", rows(dao).single().idempotencyKey)
    }

    @Test
    fun `daily statistics over the whole span add up to the full session (no loss no double count)`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 22)
        val end = start + 30 * 3_600_000L // tới 04:00 ngày 12
        commit(dao, start, end)
        val recs = rows(dao).map { it.toDomain() }
        var sum = 0L
        for (d in listOf("2026-11-10", "2026-11-11", "2026-11-12")) {
            sum += LedgerAnalyticsCalculator.computeDayStats(d, recs, vn, end).timedSeconds
        }
        assertEquals(30 * 3600L, sum)
    }

    // ---------------------------------------------------------------- DST
    @Test
    fun `fall back DST (25 hour local day) never produces a segment over 24h and keeps the total`() {
        val dao = InMemoryLedgerDao()
        val start = ms(ny, 2026, 10, 31, 20)           // EDT
        val end = ms(ny, 2026, 11, 2, 0)               // EST: 29h thực
        assertEquals(29 * 3_600_000L, end - start)
        val r = commit(dao, start, end, ny)
        assertTrue(r is TimerCommitResult.Saved && r.segments == 2)
        assertContiguousAndTotal(dao, start, end, 2)
        rows(dao).forEach { assertEquals("America/New_York", it.timezone) }
    }

    @Test
    fun `spring forward DST`() {
        val dao = InMemoryLedgerDao()
        val start = ms(ny, 2026, 3, 7, 12)
        val end = ms(ny, 2026, 3, 9, 12)               // 47h thực
        assertEquals(47 * 3_600_000L, end - start)
        commit(dao, start, end, ny)
        assertContiguousAndTotal(dao, start, end, 2)
    }

    // ---------------------------------------------------------------- trùng giờ: giữ phiên, không ghi dở
    private fun timedRecord(dao: InMemoryLedgerDao, startMs: Long, endMs: Long, title: String = "Họp") = runBlocking {
        val v = LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = title, rawCategory = "work", dateLocal = "2026-11-11", inputDurationMinutes = null,
            startedAt = startMs, endedAt = endMs, existingRecords = emptyList(), nowEpochMs = endMs + 10_000_000_000L, zoneId = vn
        ) as ValidationResult.Valid
        dao.upsertInternal(com.timewallet.mvp.data.local.LedgerRecordEntity.fromDomain(v.record))
        v.record
    }

    @Test
    fun `overlap inside a later segment aborts everything atomically and keeps the session recoverable`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        val end = start + 30 * 3_600_000L
        val conflict = timedRecord(dao, start + 26 * 3_600_000L, start + 27 * 3_600_000L)
        val r = commit(dao, start, end)
        assertTrue("phải là Overlap: $r", r is TimerCommitResult.Overlap)
        assertEquals("không ghi dở đoạn nào", listOf(conflict.id), rows(dao).map { it.id })
        assertFalse(runBlocking { TimerCommit.isCommitted(dao, start) })

        // Người dùng xử lý bản ghi trùng rồi bấm Dừng & Lưu lại: phiên vẫn còn nên lưu được toàn bộ.
        runBlocking { dao.deleteById(conflict.id) }
        val again = commit(dao, start, end)
        assertTrue(again is TimerCommitResult.Saved && again.segments == 2)
        assertContiguousAndTotal(dao, start, end, 2)
    }

    @Test
    fun `overlap with a short session is also rejected without writing anything`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        timedRecord(dao, start + 3_600_000L, start + 2 * 3_600_000L)
        val r = commit(dao, start, start + 3 * 3_600_000L)
        assertTrue(r is TimerCommitResult.Overlap)
        assertEquals(1, rows(dao).size)
    }

    // ---------------------------------------------------------------- thử lại / khởi động lại
    @Test
    fun `retry after success and restart between commit and timer clear never duplicate or lose time`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        val end = start + 49 * 3_600_000L
        assertTrue(commit(dao, start, end) is TimerCommitResult.Saved)
        // Giả lập: app bị tắt sau khi commit nhưng trước khi xóa phiên. Khi mở lại, phiên vẫn "đang chạy" nhưng đã được ghi.
        assertTrue(runBlocking { TimerCommit.isCommitted(dao, start) })
        // Dừng lần nữa ở một thời điểm MUỘN HƠN (đồng hồ vẫn chạy) không được ghi thêm hay nhân đôi.
        val later = commit(dao, start, end + 5 * 3_600_000L)
        assertEquals(TimerCommitResult.AlreadyCommitted, later)
        assertContiguousAndTotal(dao, start, end, 3)
    }

    @Test
    fun `a record saved by v105 for a short session is recognised as already committed`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        val end = start + 3_600_000L
        val v = LedgerValidator.validateAndBuildRecord(
            source = "timer", rawTitle = "Làm việc tập trung", rawCategory = "work", dateLocal = "2026-11-10",
            inputDurationMinutes = null, startedAt = start, endedAt = end, existingRecords = emptyList(), nowEpochMs = end,
            zoneId = vn, idempotencyKey = "timer_$start"
        ) as ValidationResult.Valid
        runBlocking { dao.upsertInternal(com.timewallet.mvp.data.local.LedgerRecordEntity.fromDomain(v.record)) }
        assertEquals(TimerCommitResult.AlreadyCommitted, commit(dao, start, end))
        assertEquals(1, rows(dao).size)
    }

    @Test
    fun `a session shorter than one second is invalid`() {
        val dao = InMemoryLedgerDao()
        val start = ms(vn, 2026, 11, 10, 8)
        assertTrue(commit(dao, start, start) is TimerCommitResult.Invalid)
        assertTrue(rows(dao).isEmpty())
    }

    @Test
    fun `segment splitting is exact and keys are stable`() {
        val s = TimerSegments.split(1_000L, 1_000L + 2 * TimerSegments.MAX_SEGMENT_MS + 5L)
        assertEquals(3, s.size)
        assertEquals(5L, s.last().durationMs)
        assertEquals("timer_1000", TimerSegments.idempotencyKey(1_000L, 0))
        assertEquals("timer_1000_2", TimerSegments.idempotencyKey(1_000L, 2))
        assertTrue(TimerSegments.split(10L, 10L).isEmpty())
    }
}
