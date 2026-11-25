package com.timewallet.mvp

import com.timewallet.mvp.data.local.LedgerDao
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * AI-01 Slice A — test tái hiện Findings C, D, F và lỗi AM/PM (viết TRƯỚC khi sửa).
 * Findings A, B có test riêng ở Ai01FeedbackAndGuardTest (cần API mới).
 */
class Ai01IntegrityReproTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 12)
    private val now = LocalTime.of(14, 0)

    private fun parse(text: String) = VietnameseChatParser.parse(text, today, now)

    // ---- Finding C ----
    @Test
    fun `C - long prefix before colon still propagates yesterday to every segment`() {
        val out = parse("Nhật ký các hoạt động của ngày hôm qua: họp 1h, viết tài liệu 2h, đọc sách 30p")
        assertEquals(3, out.proposals.size)
        out.proposals.forEach { assertEquals(today.minusDays(1).toString(), it.dateLocal) }
    }

    @Test
    fun `C - tomorrow header propagates and time colon is not mistaken for header colon`() {
        val out = parse("Kế hoạch cho ngày mai nhé: học 1h, đọc sách 30p")
        assertEquals(2, out.proposals.size)
        out.proposals.forEach { assertEquals(today.plusDays(1).toString(), it.dateLocal) }

        // Dấu ':' trong giờ 13:00 không phải dấu kết thúc tiền tố
        val timed = parse("13:00-14:00 làm việc, học 30p")
        assertEquals(2, timed.proposals.size)
        timed.proposals.forEach { assertEquals(today.toString(), it.dateLocal) }
    }

    @Test
    fun `C - regression - leading yesterday marker without colon still applies to every segment`() {
        val out = parse("Hôm qua học 1h, đọc sách 30p")
        assertEquals(2, out.proposals.size)
        out.proposals.forEach { assertEquals(today.minusDays(1).toString(), it.dateLocal) }
    }

    // ---- Finding D ----
    @Test
    fun `D - aggregate total clause is not turned into a third proposal`() {
        val out = parse("Sáng học 2h, chiều làm việc 3h, tổng cộng hôm nay được 5h")
        assertEquals(2, out.proposals.size)
        assertEquals(5 * 3600L, out.proposals.sumOf { it.durationSeconds })
        assertTrue(out.warnings.any { it.contains("tổng", ignoreCase = true) })
    }

    @Test
    fun `D - activities that merely contain the word tong are kept`() {
        val out = parse("Họp tổng kết dự án 1h, học 30p")
        assertEquals(2, out.proposals.size)
        assertEquals(3600L, out.proposals[0].durationSeconds)
    }

    // ---- AM/PM ----
    @Test
    fun `AMPM - lúc 2h chiều is 14_00 not 02_00`() {
        val p = parse("Họp lúc 2h chiều 1h").proposals.single()
        assertEquals("14:00", p.startLocal)
        assertEquals("15:00", p.endLocal)
        assertEquals("explicit_range", p.timeEvidence)
    }

    @Test
    fun `AMPM - range 2h-4h chiều is 14_00-16_00 and 10h-2h chiều is 10_00-14_00`() {
        val a = parse("Làm việc 2h-4h chiều").proposals.single()
        assertEquals("14:00", a.startLocal); assertEquals("16:00", a.endLocal)
        assertEquals(120L * 60, a.durationSeconds)

        val b = parse("Làm việc 10h-2h chiều").proposals.single()
        assertEquals("10:00", b.startLocal); assertEquals("14:00", b.endLocal)
        assertEquals(240L * 60, b.durationSeconds)
    }

    @Test
    fun `AMPM - toi trua sang and 24h forms`() {
        assertEquals("22:00", parse("Ngủ lúc 10h tối 8h").proposals.single().startLocal)
        assertEquals("13:00", parse("Họp lúc 1h trưa 1h").proposals.single().startLocal)
        assertEquals("12:00", parse("Ăn trưa lúc 12h trưa 1h").proposals.single().startLocal)
        assertEquals("06:00", parse("Chạy bộ lúc 6h sáng 30p").proposals.single().startLocal)
        assertEquals("14:00", parse("Họp 1h lúc 14h").proposals.single().startLocal)
        assertEquals("08:00", parse("Học 45p lúc 8h").proposals.single().startLocal)
    }

    @Test
    fun `AMPM - dem qua stays a yesterday marker and is not read as night period`() {
        val p = parse("Đêm qua lúc 9h học 1h").proposals.single()
        assertEquals(today.minusDays(1).toString(), p.dateLocal)
        assertEquals("09:00", p.startLocal)
    }

    // ---- Finding F (interleaving) ----
    @Test
    fun `F - interleaving - batch validated before timer start is still committed over the running timer when DAO has no guard`() = runBlocking {
        val dao = InMemoryDao()
        val day = today.toString()
        val start = LedgerValidator.parseTimeRangeToEpochMillis(day, "13:00", "18:00", zone)!!
        val nowMs = LedgerValidator.parseTimeRangeToEpochMillis(day, "14:35", "14:36", zone)!!.first

        // t0: validate với timer = null -> hợp lệ
        // t1: người dùng bấm Bắt đầu timer lúc 14:35 (không đi qua mutex của ledger)
        // t2: commit lô đã validate -> bản ghi ACTIVE 13:00-18:00 giao với timer
        val entity = LedgerRecordEntity(
            id = "f-1", source = "chat", layer = "ACTIVE", createdAt = nowMs, userConfirmedAt = nowMs,
            category = "work", title = "Làm việc 13-18", date = day, durationSeconds = 5 * 3600L,
            startedAt = start.first, endedAt = start.second, timezone = zone.id,
            status = "ACTIVE", idempotencyKey = "idem-f-1"
        )
        val outcome = dao.commitBatchRecordsTransactionally(listOf(entity))
        // Hành vi HIỆN TẠI: DAO không biết timer -> lưu. (Đây là bằng chứng race tồn tại ở tầng DAO.)
        assertFalse(outcome.abortedAtomically)
        assertEquals(1, dao.getAllRecordsSnapshot().size)
    }

    private class InMemoryDao : LedgerDao {
        private val rows = mutableListOf<LedgerRecordEntity>()
        override fun observeAllRecords(): Flow<List<LedgerRecordEntity>> = emptyFlow()
        override suspend fun getAllRecordsSnapshot(): List<LedgerRecordEntity> = rows.toList()
        override suspend fun findById(id: String): LedgerRecordEntity? = rows.firstOrNull { it.id == id }
        override suspend fun countByIdempotencyKey(idempotencyKey: String): Int =
            rows.count { it.idempotencyKey == idempotencyKey }
        override suspend fun findOverlappingEntity(startMs: Long, endMs: Long, excludeId: String): LedgerRecordEntity? =
            rows.firstOrNull { r ->
                (r.layer == "MEASURED" || r.layer == "REPORTED_TIMED" || r.layer == "ACTIVE") &&
                    r.status != "PLANNED" && r.startedAt != null && r.endedAt != null &&
                    r.id != excludeId && startMs < r.endedAt && endMs > r.startedAt
            }
        override suspend fun upsertInternal(entity: LedgerRecordEntity) {
            rows.removeAll { it.id == entity.id }; rows.add(entity)
        }
        override suspend fun deleteById(id: String) { rows.removeAll { it.id == id } }
        override suspend fun deleteAll() { rows.clear() }
    }
}
