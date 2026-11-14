package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.LedgerDao
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.ChatAnalysisFeedback
import com.timewallet.mvp.domain.usecase.ChatAnalysisGuard
import com.timewallet.mvp.domain.usecase.LedgerValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

/** AI-01 Slice A — Findings A, B, D (nhánh AI) và F (nhánh sửa lỗi). */
class Ai01FeedbackAndGuardTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 12)
    private val day = today.toString()

    // ---- Finding A ----
    @Test
    fun `A - fallback warning is surfaced even when local parser returns drafts`() {
        val msg = ChatAnalysisFeedback.toastMessage(
            draftCount = 1,
            warnings = listOf(GeminiProposalClient.SAFE_FALLBACK_WARNING_VI)
        )
        assertTrue(msg.contains("Đã tách 1 hoạt động"))
        assertTrue(msg.contains(GeminiProposalClient.SAFE_FALLBACK_WARNING_VI))
    }

    @Test
    fun `A - dropped item warning is surfaced alongside kept items and extra warnings are counted`() {
        val msg = ChatAnalysisFeedback.toastMessage(2, listOf("w1", "w2", "w3", "w1"))
        assertTrue(msg.contains("w1") && msg.contains("w2"))
        assertFalse(msg.contains("w3"))
        assertTrue(msg.contains("+1 lưu ý khác"))
    }

    @Test
    fun `A - no warnings keeps the original success message and empty result shows first warning`() {
        assertEquals(
            "Đã tách 3 hoạt động. Bấm \"Xác nhận toàn bộ\" để lưu hàng loạt.",
            ChatAnalysisFeedback.toastMessage(3, emptyList())
        )
        assertEquals("cảnh báo đầu", ChatAnalysisFeedback.toastMessage(0, listOf("cảnh báo đầu", "x")))
        assertTrue(ChatAnalysisFeedback.toastMessage(0, emptyList()).contains("Chưa nhận diện"))
    }

    @Test
    fun `A - AI response with a mismatched item reports the dropped item through warnings into the toast`() {
        val body = """{"proposals":[
          {"title":"Lệch giờ","category":"work","durationSeconds":7200,"dateLocal":"2026-11-12","startLocal":"08:00","endLocal":"09:00","timeEvidence":"explicit_range"},
          {"title":"Học","category":"learn","durationSeconds":3600,"dateLocal":"2026-11-12","timeEvidence":"duration_only"}]}"""
        val outcome = GeminiProposalClient.parseAndValidateResponseJson(body, "x", today, LocalTime.of(14, 0))
        assertEquals(1, outcome.proposals.size)
        val msg = ChatAnalysisFeedback.toastMessage(outcome.proposals.size, outcome.warnings)
        assertTrue(msg.contains("Lệch giờ"))
    }

    // ---- Finding B ----
    @Test
    fun `B - second analyze call is rejected while first is running and allowed again after release`() {
        val guard = ChatAnalysisGuard()
        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
        guard.release()
        assertTrue(guard.tryAcquire())
    }

    @Test
    fun `B - under 50 concurrent callers exactly one acquires`() = runBlocking {
        val guard = ChatAnalysisGuard()
        val results = coroutineScope {
            (1..50).map { async(Dispatchers.Default) { guard.tryAcquire() } }.awaitAll()
        }
        assertEquals(1, results.count { it })
    }

    // ---- Finding D (nhánh AI) ----
    @Test
    fun `D - AI aggregate proposal is dropped with a warning`() {
        val body = """{"proposals":[
          {"title":"Học","category":"learn","durationSeconds":7200,"dateLocal":"2026-11-12","timeEvidence":"duration_only"},
          {"title":"Tổng cộng hôm nay","category":"other","durationSeconds":18000,"dateLocal":"2026-11-12","timeEvidence":"duration_only"}]}"""
        val outcome = GeminiProposalClient.parseAndValidateResponseJson(body, "x", today, LocalTime.of(14, 0))
        assertEquals(listOf("Học"), outcome.proposals.map { it.title })
        assertTrue(outcome.warnings.any { it.contains("tổng hợp") })
    }

    @Test
    fun `D - system prompt carries relative date, aggregate and 12h rules`() {
        val p = GeminiProposalClient.buildSystemPrompt(today, "14:00", zone.id)
        assertTrue(p.contains("hôm qua") && p.contains("MỆNH ĐỀ TỔNG HỢP") && p.contains("2h chiều"))
    }

    // ---- Finding F (fix) ----
    private fun activeEntity(): Pair<LedgerRecordEntity, Long> {
        val range = LedgerValidator.parseTimeRangeToEpochMillis(day, "13:00", "18:00", zone)!!
        val nowMs = LedgerValidator.parseTimeRangeToEpochMillis(day, "14:35", "14:36", zone)!!.first
        return LedgerRecordEntity(
            id = "f-1", source = "chat", layer = "ACTIVE", createdAt = nowMs, userConfirmedAt = nowMs,
            category = "work", title = "Làm việc 13-18", date = day, durationSeconds = 5 * 3600L,
            startedAt = range.first, endedAt = range.second, timezone = zone.id,
            status = "ACTIVE", idempotencyKey = "idem-f-1"
        ) to nowMs
    }

    @Test
    fun `F - guarded commit aborts atomically when a timer started after validation overlaps the batch`() = runBlocking {
        val dao = InMemoryDao()
        val (entity, nowMs) = activeEntity()

        // t0: validate với timer = null -> hợp lệ
        val validation = LedgerValidator.validateBatchAtomic(
            inputs = listOf(
                BatchRecordInput(
                    id = "f-1", rawTitle = "Làm việc 13-18", rawCategory = "work", dateLocal = day,
                    durationMinutes = null, startHm = "13:00", endHm = "18:00", idempotencyKey = "idem-f-1"
                )
            ),
            existingRecords = emptyList(), activeTimer = null, nowEpochMs = nowMs, zoneId = zone
        )
        assertTrue(validation is BatchValidationResult.Valid)

        // t1: timer bắt đầu 14:35 (qua SharedPreferences, không qua mutex ledger)
        val liveTimer = ActiveTimerSession(
            title = "Đồng hồ", categoryKey = "work", startedAtEpochMs = nowMs,
            startedElapsedRealtimeMs = null, timezone = zone.id
        )

        // t2: commit có guard đọc timer mới nhất bên trong khóa
        val outcome = dao.commitBatchRecordsGuarded(listOf(entity)) {
            LedgerValidator.findActiveTimerConflict(
                records = (validation as BatchValidationResult.Valid).records,
                activeTimer = liveTimer,
                nowEpochMs = nowMs + 1_000L
            )
        }
        assertTrue(outcome.abortedAtomically)
        assertEquals(0, outcome.savedCount)
        assertTrue(outcome.errorMessage!!.contains("Bộ đếm đang chạy"))
        assertEquals(0, dao.getAllRecordsSnapshot().size)
    }

    @Test
    fun `F - guard passes through when no timer runs and untimed items are not blocked`() = runBlocking {
        val dao = InMemoryDao()
        val (entity, nowMs) = activeEntity()
        val ok = dao.commitBatchRecordsGuarded(listOf(entity)) { null }
        assertFalse(ok.abortedAtomically)
        assertEquals(1, ok.savedCount)

        val timer = ActiveTimerSession("t", "work", nowMs, null, zone.id)
        val untimed = BatchRecordInput(
            rawTitle = "Đọc", rawCategory = "learn", dateLocal = day, durationMinutes = 30, idempotencyKey = "u1"
        )
        val v = LedgerValidator.validateBatchAtomic(listOf(untimed), emptyList(), timer, nowMs, zone)
        assertTrue(v is BatchValidationResult.Valid)
        assertEquals(null, LedgerValidator.findActiveTimerConflict((v as BatchValidationResult.Valid).records, timer, nowMs))
    }

    @Test
    fun `F - guard and batch commit stay serialized under concurrent callers`() = runBlocking {
        val dao = InMemoryDao()
        val (entity, _) = activeEntity()
        val outcomes = coroutineScope {
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    dao.commitBatchRecordsGuarded(listOf(entity.copy(id = "f-$i", idempotencyKey = "idem-$i"))) { null }
                }
            }.awaitAll()
        }
        // cùng khung 13:00-18:00 => đúng 1 bản ghi thắng
        assertEquals(1, outcomes.count { !it.abortedAtomically && it.savedCount == 1 })
        assertEquals(1, dao.getAllRecordsSnapshot().size)
    }

    private class InMemoryDao : LedgerDao {
        private val rows = mutableListOf<LedgerRecordEntity>()
        override fun observeAllRecords(): Flow<List<LedgerRecordEntity>> = emptyFlow()
        override suspend fun getAllRecordsSnapshot(): List<LedgerRecordEntity> = rows.toList()
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
