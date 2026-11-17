package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.local.DbCommitOutcome
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ActivityBreakEngine
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.BreakDecision
import com.timewallet.mvp.domain.usecase.CoachCheck
import com.timewallet.mvp.domain.usecase.CoachDigestBuilder
import com.timewallet.mvp.domain.usecase.CoachGuard
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ManualEntryValidation
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.notifications.CompletionReminderPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Kiểm thử toàn diện CASE — Time Wallet: Future Time, Activity Break, Confirmation States,
 * UC-15 cross-midnight sleep, và liên kết mục tiêu AI Time Coach.
 */
class V120FutureBreakConfirmationTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 17)
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    private fun ms(date: LocalDate, hour: Int, min: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, min)).atZone(zone).toInstant().toEpochMilli()

    private fun buildRecord(
        id: String,
        title: String,
        cat: String,
        date: LocalDate,
        startHm: String,
        endHm: String,
        nowMs: Long,
        existing: List<LedgerRecord> = emptyList(),
        saveAsPlanned: Boolean = false
    ): LedgerRecord {
        val (s, e) = LedgerValidator.parseTimeRangeToEpochMillis(date.toString(), startHm, endHm, zone)!!
        val res = LedgerValidator.validateAndBuildRecord(
            id = id,
            source = "manual",
            rawTitle = title,
            rawCategory = cat,
            dateLocal = date.toString(),
            inputDurationMinutes = null,
            startedAt = s,
            endedAt = e,
            existingRecords = existing,
            nowEpochMs = nowMs,
            zoneId = zone,
            convertFutureToPlanned = saveAsPlanned
        )
        return (res as ValidationResult.Valid).record
    }

    // ---------- 1. Ghi nhận thời gian tương lai & vắt qua hiện tại + UC-15 ----------

    @Test
    fun `criterion 1 - at 10h10 saving 09h00-18h00 saves DOING with 1h10m elapsed and 0s confirmed future`() {
        val now1010 = ms(today, 10, 10)
        val valid = ManualEntry.validateRange(
            categoryKey = "work",
            tagId = null,
            tagName = null,
            startText = "09:00",
            endText = "18:00",
            endIsNow = false,
            dateLocal = today.toString(),
            note = "Làm việc",
            today = today,
            nowHm = "10:10"
        ) as ManualEntryValidation.Valid

        assertEquals(540, valid.durationMinutes)

        val dao = InMemoryLedgerDao()
        val commit = runBlocking {
            BatchConfirm.run(dao, listOf(ManualEntry.toBatchInput(valid, "s_work")), null, { null }, now1010, zone) { now1010 }
        }
        assertTrue(commit is ConfirmResult.Committed)

        val saved = runBlocking { dao.getAllRecordsSnapshot() }.single().toDomain()
        assertEquals(TimeLayer.ACTIVE, saved.layer)
        assertEquals(ConfirmationState.DOING, saved.resolveConfirmationStateAt(now1010))
        assertEquals(32_400L, saved.durationSeconds)
        assertEquals(ms(today, 18, 0), saved.plannedEndedAt)

        val stats1010 = LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(saved), zone, now1010)
        assertEquals("Chỉ tính 09:00-10:10 đã trôi qua", 4_200L, stats1010.timedSeconds)
        assertEquals("Chưa xác nhận hoàn thành", 0L, stats1010.confirmedCompletedSeconds)
        assertEquals(4_200L, stats1010.doingElapsedSeconds)
    }

    @Test
    fun `criterion 1 & UC-15 - future intervals count 0s actual and overnight sleep splits cleanly across yesterday and today`() {
        val now0700 = ms(today, 7, 0)
        val tomorrowWork = buildRecord("p1", "Họp chiến lược", "work", tomorrow, "09:00", "11:00", now0700, saveAsPlanned = true)
        assertEquals(TimeLayer.PLANNED, tomorrowWork.layer)
        assertEquals(ConfirmationState.PLANNED, tomorrowWork.resolveConfirmationStateAt(now0700))
        assertEquals(0L, JournalDayTotal.seconds(listOf(tomorrowWork), now0700))

        // UC-15: Ngủ từ 20:00 hôm qua đến 07:00 hôm nay (11 giờ) qua "Bây giờ"
        val sleepValid = ManualEntry.validateRange(
            categoryKey = "sleep",
            tagId = null,
            tagName = null,
            startText = "20:00",
            endText = "",
            endIsNow = true,
            dateLocal = yesterday.toString(),
            note = "Ngủ qua đêm",
            today = today,
            nowHm = "07:00"
        ) as ManualEntryValidation.Valid
        assertEquals(660, sleepValid.durationMinutes)
        assertEquals("07:00", sleepValid.endHm)

        val sleepRec = (LedgerValidator.validateBatchAtomic(
            listOf(ManualEntry.toBatchInput(sleepValid, "s_sleep")),
            emptyList(),
            null,
            now0700,
            zone
        ) as BatchValidationResult.Valid).records.single()

        assertEquals(TimeLayer.REPORTED_TIMED, sleepRec.layer)
        val yStats = LedgerAnalyticsCalculator.computeDayStats(yesterday.toString(), listOf(sleepRec), zone, now0700)
        val tStats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(sleepRec), zone, now0700)
        assertEquals("4h tối hôm qua", 4 * 3_600L, yStats.timedSeconds)
        assertEquals("7h sáng hôm nay", 7 * 3_600L, tStats.timedSeconds)
    }

    // ---------- 2 & 3. Máy trạng thái xác nhận: không tự complete + COMPLETED / ADJUSTED / INTERRUPTED / CONTINUE / CANCELLED ----------

    @Test
    fun `criteria 2 & 3 - passing 18h00 transitions PLANNED and DOING to PENDING_CONFIRMATION, never auto-COMPLETED`() {
        val now1010 = ms(today, 10, 10)
        val doingWork = buildRecord("w1", "Làm việc", "work", today, "09:00", "18:00", now1010)
        val eveningPlan = buildRecord("p1", "Chạy bộ tối", "health", today, "19:00", "20:00", now1010)

        val now1805 = ms(today, 18, 5)
        assertEquals(ConfirmationState.PENDING_CONFIRMATION, doingWork.resolveConfirmationStateAt(now1805))
        assertTrue(doingWork.requiresCompletionConfirmationAt(now1805))
        // Tới 21:00 chưa phản hồi thông báo -> vẫn PENDING_CONFIRMATION (kể cả kế hoạch 19:00-20:00)
        val now2100 = ms(today, 21, 0)
        assertEquals(ConfirmationState.PENDING_CONFIRMATION, doingWork.resolveConfirmationStateAt(now2100))
        assertEquals(ConfirmationState.PENDING_CONFIRMATION, eveningPlan.resolveConfirmationStateAt(now2100))
        // Kế hoạch chưa xác nhận KHÔNG biến thành COMPLETED hay thời gian thực tế
        assertEquals(0L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(doingWork), zone, now2100).confirmedCompletedSeconds)
        assertEquals(0L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(eveningPlan), zone, now2100).timedSeconds)

        // (a) Xác nhận COMPLETED -> CONFIRMED + thống kê đầy đủ
        val completed = (ConfirmationEngine.confirmCompleted(doingWork, emptyList(), null, now1805, zone) as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(ConfirmationState.COMPLETED, completed.resolveConfirmationStateAt(now1805))
        assertEquals(32_400L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(completed), zone, now1805).confirmedCompletedSeconds)

        // (b) ADJUSTED -> giữ planned gốc (09:00-18:00 = 9h), thực tế = 09:00-17:30 (8.5h) và GIỮ trạng thái hoàn thành
        val adjusted = (ConfirmationEngine.adjustRecord(
            existing = doingWork,
            newTitle = "Làm việc",
            newCategoryKey = "work",
            newDateLocal = today.toString(),
            newDurationMinutes = null,
            newStartHm = "09:00",
            newEndHm = "17:30",
            allowFutureAsPlanned = false,
            existingRecords = emptyList(),
            nowEpochMs = now1805,
            zoneId = zone
        ) as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(ConfirmationState.ADJUSTED, adjusted.resolveConfirmationStateAt(now1805))
        assertEquals(32_400L, adjusted.plannedDurationSeconds)
        assertEquals(30_600L, adjusted.durationSeconds)
        assertEquals(30_600L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(adjusted), zone, now1805).confirmedCompletedSeconds)

        // (c) INTERRUPTED -> kết thúc sớm lúc 11:30 = 2.5h thực tế, vẫn giữ planned 9h
        val now1140 = ms(today, 11, 40)
        val interrupted = (ConfirmationEngine.interruptRecord(doingWork, ms(today, 11, 30), emptyList(), null, now1140, zone) as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(ConfirmationState.INTERRUPTED, interrupted.resolveConfirmationStateAt(now1140))
        assertEquals(9_000L, interrupted.durationSeconds)
        assertEquals(32_400L, interrupted.plannedDurationSeconds)

        // (d) CONTINUE -> kéo dài tới 20:00 -> vẫn DOING
        val continued = (ConfirmationEngine.continueRecord(doingWork, ms(today, 20, 0), emptyList(), null, now1805, zone) as ConfirmationActionResult.Applied).updatedRecords.single()
        assertEquals(ConfirmationState.DOING, continued.resolveConfirmationStateAt(now1805))
        assertEquals(TimeLayer.ACTIVE, continued.layer)
        assertEquals(39_600L, continued.durationSeconds)

        // (e) CANCELLED -> 0s thực tế, giữ lại để Coach đọc, không gây overlap với việc khác cùng khung giờ
        val cancelled = (ConfirmationEngine.cancelRecord(eveningPlan, now2100) as ConfirmationActionResult.Applied).updatedRecords.single()
        assertTrue(cancelled.isCancelled)
        assertEquals(ConfirmationState.CANCELLED, cancelled.resolveConfirmationStateAt(now2100))
        assertEquals(0L, JournalDayTotal.seconds(listOf(cancelled), now2100))
        assertTrue(ActivityBreakEngine.findAllOverlaps(ms(today, 19, 0), ms(today, 20, 0), listOf(cancelled), null, now2100).isEmpty())
    }

    // ---------- 4 & 5. Activity Break & Overlap: BREAK_CONFIRMED (9h), PARALLEL_CONFIRMED (chống tính trùng 9h), PENDING / BREAK_REJECTED ----------

    @Test
    fun `criteria 4 & 5 - BREAK_CONFIRMED splits 09-18 into 09-14 + 15-18 (8h) and 14-15 reading (1h) totaling 9h cleanly in DAO`() {
        val now1830 = ms(today, 18, 30)
        val work = buildRecord("w_full", "Làm việc", "work", today, "09:00", "18:00", now1830)
        val reading = buildRecord("r_read", "Đọc sách", "learn", today, "14:00", "15:00", now1830, existing = emptyList())

        val overlaps = ActivityBreakEngine.findAllOverlaps(
            startMs = reading.startedAt!!,
            endMs = reading.endedAt!!,
            existingRecords = listOf(work),
            nowEpochMs = now1830
        )
        assertEquals(listOf("w_full"), overlaps.map { it.id })

        val plan = ActivityBreakEngine.resolveBreak(reading, overlaps, BreakDecision.BREAK_CONFIRMED, now1830, zone).getOrThrow()
        assertEquals(3, plan.updatedRecords.size)

        val dao = InMemoryLedgerDao()
        runBlocking {
            assertEquals(DbCommitOutcome.Saved, dao.commitRecordTransactionally(LedgerRecordEntity.fromDomain(work)))
            assertEquals(DbCommitOutcome.Saved, dao.applyOverlapResolutionTransactionally(plan.updatedRecords.map { LedgerRecordEntity.fromDomain(it) }, plan.deletedIds))
        }

        val all = runBlocking { dao.getAllRecordsSnapshot() }.map { it.toDomain() }
        val stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), all, zone, now1830)
        val workCat = stats.categoryBreakdown.first { it.category == TimeCategory.WORK }.durationSeconds
        val learnCat = stats.categoryBreakdown.first { it.category == TimeCategory.LEARN }.durationSeconds

        assertEquals("Làm việc = 8 giờ", 8 * 3_600L, workCat)
        assertEquals("Đọc sách = 1 giờ", 1 * 3_600L, learnCat)
        assertEquals("Tổng thực tế = 9 giờ", 9 * 3_600L, stats.timedSeconds)
        assertEquals(9 * 3_600L, stats.confirmedCompletedSeconds)
        assertEquals(0L, stats.parallelOverlapSeconds)
    }

    @Test
    fun `criteria 4 & 5 - PARALLEL_CONFIRMED distinguishes individual durations (9h vs 1h) without double counting day fund (9h total)`() {
        val now1830 = ms(today, 18, 30)
        val work = buildRecord("w_full", "Làm việc", "work", today, "09:00", "18:00", now1830)
        val reading = buildRecord("r_read", "Đọc sách", "learn", today, "14:00", "15:00", now1830, existing = emptyList())
        val plan = ActivityBreakEngine.resolveBreak(reading, listOf(work), BreakDecision.PARALLEL_CONFIRMED, now1830, zone).getOrThrow()

        val dao = InMemoryLedgerDao()
        runBlocking {
            assertEquals(DbCommitOutcome.Saved, dao.commitRecordTransactionally(LedgerRecordEntity.fromDomain(work)))
            assertEquals(DbCommitOutcome.Saved, dao.applyOverlapResolutionTransactionally(plan.updatedRecords.map { LedgerRecordEntity.fromDomain(it) }))
        }

        val all = runBlocking { dao.getAllRecordsSnapshot() }.map { it.toDomain() }
        val stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), all, zone, now1830)
        val workCat = stats.standaloneCategorySeconds.getValue(TimeCategory.WORK)
        val learnCat = stats.standaloneCategorySeconds.getValue(TimeCategory.LEARN)

        assertEquals("Làm việc vẫn giữ thời lượng riêng 9h", 9 * 3_600L, workCat)
        assertEquals("Đọc sách vẫn giữ thời lượng riêng 1h", 1 * 3_600L, learnCat)
        assertEquals("Tổng quỹ ngày thực tế sau khử trùng = 9h (KHÔNG thành 10h!)", 9 * 3_600L, stats.timedSeconds)
        assertEquals("Thời lượng trùng giờ được theo dõi riêng = 1h", 1 * 3_600L, stats.parallelOverlapSeconds)

        // Vẫn chặn bản ghi thứ 3 không phải nhóm song song trùng giờ
        val clash = buildRecord("c3", "Chơi game", "fun", today, "14:15", "14:45", now1830, existing = emptyList())
        val daoOutcome = runBlocking { dao.commitRecordTransactionally(LedgerRecordEntity.fromDomain(clash)) }
        assertTrue(daoOutcome is DbCommitOutcome.OverlapRejected)
    }

    @Test
    fun `criterion 5 - PENDING and BREAK_REJECTED stay excluded from confirmed stats and goals`() {
        val now1830 = ms(today, 18, 30)
        val work = buildRecord("w1", "Làm việc", "work", today, "09:00", "18:00", now1830)
        val disputed = buildRecord("r2", "Đọc sách", "learn", today, "14:00", "15:00", now1830, existing = emptyList())
        val pendingPlan = ActivityBreakEngine.resolveBreak(disputed, listOf(work), BreakDecision.PENDING, now1830, zone).getOrThrow()
        val rejectedPlan = ActivityBreakEngine.resolveBreak(disputed.copy(id = "r3"), listOf(work), BreakDecision.BREAK_REJECTED, now1830, zone).getOrThrow()

        val all = listOf(work) + pendingPlan.updatedRecords + rejectedPlan.updatedRecords
        val stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), all, zone, now1830)
        assertEquals(2, stats.reconciliationPendingCount)
        assertEquals("Chỉ tính work 9h, không cộng thêm bất kỳ bản ghi PENDING/BREAK_REJECTED", 9 * 3_600L, stats.timedSeconds)
    }

    // ---------- 6. AI Time Coach & Goal Link ----------

    @Test
    fun `criterion 6 - Goal and Coach distinguish planned vs pending confirmation vs confirmed 8h focus and treat reading rest neutrally`() {
        val now1900 = ms(today, 19, 0)
        val now1010 = ms(today, 10, 10)
        val unconfirmedWork = buildRecord("w_doing", "Làm việc", "work", today, "09:00", "18:00", now1010)
        val cancelledPlan = (ConfirmationEngine.cancelRecord(
            buildRecord("p_cancel", "Họp tối", "work", today, "20:00", "21:00", now1900),
            now1900
        ) as ConfirmationActionResult.Applied).updatedRecords.single()

        val goal = GoalRules.create(
            targetType = GoalTargetType.CATEGORY,
            targetKey = "work",
            kind = GoalKind.MIN,
            period = GoalPeriod.DAY,
            targetSeconds = 8 * 3_600L,
            today = today,
            nowEpochMs = now1010,
            id = "g8h",
            groupId = "gg1"
        )

        // Khi Làm việc 09:00-18:00 vẫn chưa được người dùng xác nhận (PENDING_CONFIRMATION tại 19:00) -> không tính đạt mục tiêu!
        val unconfirmedUsed = GoalRules.confirmedUsedSeconds(
            GoalTargetType.CATEGORY,
            "work",
            listOf(unconfirmedWork, cancelledPlan),
            today,
            today,
            now1900,
            zone
        )
        assertEquals(0L, unconfirmedUsed)

        val digestBefore = CoachDigestBuilder.build(listOf(unconfirmedWork, cancelledPlan), emptyList(), listOf(goal), 1, now1900, zone)
        assertFalse(digestBefore.hasAnyData)
        assertTrue(digestBefore.hasPlanOrStateHistory)
        assertTrue(digestBefore.text.contains("CHỜ XÁC NHẬN"))
        assertTrue(digestBefore.text.contains("ĐÃ HỦY"))
        assertTrue(
            "CoachGuard chặn AI tự tuyên bố đạt mục tiêu khi chưa có xác nhận thực tế",
            CoachGuard.check("Bạn đã đạt mục tiêu 8 giờ hôm nay.", digestBefore) is CoachCheck.Rejected
        )

        // Chính sách nhắc hẹn thông báo kết thúc lúc 18:00 / tương lai
        val futurePlanned = buildRecord("fp", "Họp tối", "work", today, "20:00", "21:00", now1010)
        val completedSample = (ConfirmationEngine.confirmCompleted(unconfirmedWork, emptyList(), null, now1900, zone) as ConfirmationActionResult.Applied).updatedRecords.single()
        val targetsAt1010 = CompletionReminderPolicy.selectTargets(
            listOf(unconfirmedWork, futurePlanned, cancelledPlan, completedSample),
            now1010
        ).map { it.id }.toSet()
        assertEquals(setOf("w_doing", "fp"), targetsAt1010)
        assertTrue("Khi đã xác nhận COMPLETED hoặc CANCELLED thì hủy nhắc hẹn", CompletionReminderPolicy.selectTargets(listOf(cancelledPlan, completedSample), now1010).isEmpty())

        // Sau khi xác nhận BREAK_CONFIRMED (8h làm việc + 1h đọc sách nghỉ có chủ đích), tất cả đều COMPLETED
        val completedWork1 = buildRecord("w_a", "Làm việc", "work", today, "09:00", "14:00", now1900)
        val completedWork2 = buildRecord("w_b", "Làm việc", "work", today, "15:00", "18:00", now1900, existing = listOf(completedWork1))
        val breakRead = buildRecord("r_b", "Đọc sách", "learn", today, "14:00", "15:00", now1900, existing = listOf(completedWork1, completedWork2))
            .copy(overlapResolutionRaw = OverlapResolution.BREAK_CONFIRMED.name)

        val recordsAfter = listOf(completedWork1, completedWork2, breakRead)
        val summary = GoalRules.summaries(listOf(goal), recordsAfter, { null }, today, now1900, zone).single()
        assertEquals(8 * 3_600L, summary.progress!!.usedSeconds)
        assertTrue("Đạt đúng 8 giờ mục tiêu tập trung sau khi trừ 1h nghỉ đọc sách", summary.progress!!.met)

        val digestAfter = CoachDigestBuilder.build(recordsAfter, emptyList(), listOf(goal), 1, now1900, zone)
        assertTrue(digestAfter.hasAnyData)
        assertTrue(digestAfter.text.contains("8 giờ") || digestAfter.text.contains("480 phút"))
        assertTrue(digestAfter.text.contains("Khoảng nghỉ xen giữa", ignoreCase = true))

        // CoachGuard chấp nhận nhận xét khách quan về khoảng nghỉ đọc sách & đạt mục tiêu 8 giờ
        val okStory = CoachGuard.check(
            "Hôm nay bạn đã hoàn thành 8 giờ làm việc và dành 1 giờ đọc sách xen giữa, đạt mục tiêu 8 giờ đã đặt.",
            digestAfter
        )
        assertEquals(CoachCheck.Ok, okStory)

        val p = GeminiProposalClient.coachSystemPrompt
        assertTrue(p.contains("nghỉ ngơi có chủ đích", ignoreCase = true))
        assertTrue(p.contains("CHỜ XÁC NHẬN", ignoreCase = true))
        assertNotNull(LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(9 * 3_600L))
    }
}
