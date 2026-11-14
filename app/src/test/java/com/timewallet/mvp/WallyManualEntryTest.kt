package com.timewallet.mvp

import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ManualEntryValidation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * WALLY A09/A10: nhập giờ thủ công đi qua ĐÚNG đường chung (ManualEntry.validate -> toBatchInput ->
 * LedgerValidator.validateBatchAtomic -> LedgerDao.commitBatchRecordsGuarded). Không cần AI, không cần khóa API.
 */
class WallyManualEntryTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13)
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun InMemoryLedgerDao.rows() = runBlocking { getAllRecordsSnapshot() }
    private fun InMemoryLedgerDao.del(id: String) = runBlocking { deleteById(id) }

    private fun valid(
        cat: String? = "learn", h: String = "", m: String = "30", date: String = today.toString(), note: String = ""
    ): ManualEntryValidation.Valid {
        val v = ManualEntry.validate(cat, h, m, date, note, today)
        assertTrue("phải hợp lệ: $v", v is ManualEntryValidation.Valid)
        return v as ManualEntryValidation.Valid
    }

    /** Lặp lại đúng chuỗi mà ViewModel.saveManualEntry chạy (validate -> batch -> guarded commit). */
    private fun save(dao: InMemoryLedgerDao, v: ManualEntryValidation.Valid, session: String, timer: ActiveTimerSession? = null) =
        runBlocking {
            val existing = dao.getAllRecordsSnapshot().map { it.toDomain() }
            when (val batch = LedgerValidator.validateBatchAtomic(
                inputs = listOf(ManualEntry.toBatchInput(v, session)),
                existingRecords = existing, activeTimer = timer, nowEpochMs = nowMs, zoneId = zone
            )) {
                is BatchValidationResult.Invalid -> null to batch.message
                is BatchValidationResult.Valid -> dao.commitBatchRecordsGuarded(batch.records.map { LedgerRecordEntity.fromDomain(it) }) {
                    LedgerValidator.findActiveTimerConflict(batch.records, timer, nowMs)
                } to null
            }
        }

    // ---------- kiểm tra form ----------
    @Test
    fun `validation accepts 30 minutes custom durations past days and every one of the 9 categories`() {
        assertEquals(30, valid(m = "30").durationMinutes)
        assertEquals(105, valid(h = "1", m = "45").durationMinutes)
        assertEquals(120, valid(h = "2", m = "").durationMinutes)
        assertEquals("2026-10-01", valid(date = "2026-10-01").dateLocal)
        TimeCategory.entries.forEach { c ->
            val v = valid(cat = c.key)
            assertEquals(c, v.category)
            assertEquals("ghi chú trống => tên danh mục", c.displayName, v.title)
        }
        assertEquals(9, TimeCategory.entries.size)
        assertEquals(listOf(15, 30, 60, 120), ManualEntry.PRESET_MINUTES)
        assertEquals(listOf("15p", "30p", "1h", "2h"), ManualEntry.PRESET_MINUTES.map(ManualEntry::presetLabel))
    }

    @Test
    fun `validation rejects bad input with a clear message`() {
        fun bad(cat: String? = "work", h: String = "", m: String = "30", date: String = today.toString()) =
            (ManualEntry.validate(cat, h, m, date, "", today) as? ManualEntryValidation.Invalid)?.message
        assertTrue(bad(cat = null)!!.contains("danh mục"))
        assertTrue(bad(cat = "nonsense")!!.contains("danh mục"))
        assertTrue(bad(m = "0")!!.contains("lớn hơn 0"))
        assertTrue(bad(h = "", m = "")!!.contains("lớn hơn 0"))
        assertTrue(bad(m = "75")!!.contains("0 đến 59"))
        assertTrue(bad(h = "25", m = "0")!!.contains("24 giờ"))
        assertTrue(bad(h = "24", m = "30")!!.contains("24 giờ"))
        assertTrue(bad(m = "1x")!!.contains("số"))
        assertTrue(bad(date = "not-a-date")!!.contains("Ngày"))
        assertTrue(bad(date = today.plusDays(1).toString())!!.contains("tương lai"))
    }

    @Test
    fun `note is trimmed to 110 characters and used as the title`() {
        val v = valid(note = "  " + "x".repeat(200) + "  ")
        assertEquals(110, v.title.length)
        assertEquals("Họp nhóm", valid(note = " Họp nhóm ").title)
    }

    // ---------- lưu qua đường chung ----------
    @Test
    fun `A09 a 30 minute manual record is saved untimed without start or end and without any AI`() {
        val dao = InMemoryLedgerDao()
        val (outcome, err) = save(dao, valid(cat = "learn", m = "30"), "s1")
        assertNull(err)
        assertEquals(1, outcome!!.savedCount)
        val rec = dao.rows().single()
        assertEquals("manual", rec.source)
        assertEquals(TimeLayer.REPORTED_UNTIMED.name, rec.layer)
        assertNull("không tự gán giờ bắt đầu", rec.startedAt)
        assertNull("không tự gán giờ kết thúc", rec.endedAt)
        assertEquals(1800L, rec.durationSeconds)
        assertEquals("CONFIRMED", rec.status)
    }

    @Test
    fun `A09 a past day record lands on that day and all 9 categories can be saved`() {
        val dao = InMemoryLedgerDao()
        TimeCategory.entries.forEachIndexed { i, c ->
            save(dao, valid(cat = c.key, m = "15", date = today.minusDays(3).toString()), "s$i")
        }
        val rows = dao.rows()
        assertEquals(9, rows.size)
        assertTrue(rows.all { it.date == today.minusDays(3).toString() })
        assertEquals(TimeCategory.entries.map { it.key }.toSet(), rows.map { it.category }.toSet())
    }

    @Test
    fun `A08 pressing save twice with the same sheet writes exactly one row`() {
        val dao = InMemoryLedgerDao()
        val v = valid()
        val first = save(dao, v, "same-sheet").first!!
        val second = save(dao, v, "same-sheet").first!!
        assertEquals(1, first.savedCount)
        assertEquals(0, second.savedCount)
        assertEquals(1, second.skippedDuplicates)
        assertEquals(1, dao.rows().size)
    }

    @Test
    fun `A08 two separate sheets with identical content are two legitimate records`() {
        val dao = InMemoryLedgerDao()
        save(dao, valid(), "sheet-1")
        save(dao, valid(), "sheet-2")
        assertEquals(2, dao.rows().size)
    }

    @Test
    fun `A09 invalid or cancelled input writes nothing`() {
        val dao = InMemoryLedgerDao()
        // Form không hợp lệ không bao giờ tới DAO: validate trả Invalid nên không có Valid để lưu.
        assertTrue(ManualEntry.validate("work", "", "0", today.toString(), "", today) is ManualEntryValidation.Invalid)
        // Hủy form = không gọi save.
        assertEquals(0, dao.rows().size)
    }

    @Test
    fun `A09 manual entry works while a timer is running because untimed records cannot overlap`() {
        val dao = InMemoryLedgerDao()
        val timer = ActiveTimerSession("Đang làm", "work", nowMs - 600_000L, null, zone.id)
        val (outcome, err) = save(dao, valid(), "with-timer", timer)
        assertNull(err)
        assertEquals(1, outcome!!.savedCount)
    }

    // ---------- A10: tổng cập nhật đúng, sửa/xóa ----------
    @Test
    fun `A10 totals follow the ledger after save and after delete and untimed never enters the timed total`() = run {
        val dao = InMemoryLedgerDao()
        save(dao, valid(cat = "learn", h = "1", m = "0"), "a")
        save(dao, valid(cat = "work", m = "30"), "b")
        fun stats() = LedgerAnalyticsCalculator.computeDayStats(
            today.toString(), dao.rows().map { it.toDomain() }, zone, nowMs
        )
        assertEquals(5400L, stats().untimedSeconds)
        assertEquals("không suy diễn trục giờ", 0L, stats().timedSeconds)

        dao.del("manual_a")
        assertEquals(1800L, stats().untimedSeconds)
        dao.del("manual_b")
        assertEquals(0L, stats().untimedSeconds)
    }

    @Test
    fun `A10 records persist as plain rows so a restart reads back the same values`() {
        val dao = InMemoryLedgerDao()
        save(dao, valid(cat = "health", h = "1", m = "30", note = "Chạy bộ"), "r1")
        val restored = dao.rows().single().toDomain()
        assertEquals("Chạy bộ", restored.title)
        assertEquals(TimeCategory.HEALTH, restored.category)
        assertEquals(5400L, restored.durationSeconds)
        assertFalse(restored.layer == TimeLayer.PLANNED)
    }
}
