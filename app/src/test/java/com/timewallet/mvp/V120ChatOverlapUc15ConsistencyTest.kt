package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.BatchOverlapStep
import com.timewallet.mvp.data.local.BatchProposalPlanner
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.local.DbCommitOutcome
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ActivityBreakEngine
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BreakDecision
import com.timewallet.mvp.domain.usecase.CoachCheck
import com.timewallet.mvp.domain.usecase.CoachDigestBuilder
import com.timewallet.mvp.domain.usecase.CoachGuard
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import com.timewallet.mvp.notifications.CompletionNotificationHelper
import com.timewallet.mvp.notifications.CompletionReminderPolicy
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Bộ test cho v1.2.0:
 * 1) UC-15 qua Chat (xuyên ngày/qua nửa đêm + không tự suy đoán khi mốc giờ mơ hồ).
 * 2) Xử lý chồng lấn cho lô nhiều đề xuất Chat (tự động tách tiền tố an toàn, xử lý nối tiếp, bảo toàn dữ liệu đã xác nhận).
 * 3) Tính nhất quán giữa Ledger -> Overview -> Journal -> Analytics -> Goals -> AI Coach.
 * 4) Hợp đồng nhắc hẹn & mở ứng dụng từ notification + version v1.2.0.
 */
class V120ChatOverlapUc15ConsistencyTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 17)
    private val yesterday = today.minusDays(1)
    private val now0730 = LocalDateTime.of(today, LocalTime.of(7, 30)).atZone(zone).toInstant().toEpochMilli()

    private fun chatJson(reply: String, vararg proposals: JSONObject): String =
        JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put(
                            "content",
                            JSONObject()
                                .put("reply", reply)
                                .put("proposals", JSONArray(proposals.toList()))
                                .put("queries", JSONArray())
                                .toString()
                        )
                    )
                )
            )
            .toString()

    // ---------- 1. UC-15 qua Chat + không tự suy đoán khi thời điểm mơ hồ ----------

    @Test
    fun `UC-15 chat parses clear cross-midnight range yesterday evening to today morning and anchors start date to yesterday`() {
        val outLocal = VietnameseChatParser.parse(
            "Tôi ngủ từ 20h tối hôm qua đến 7h sáng hôm nay",
            today = today,
            nowTime = LocalTime.of(7, 30)
        )
        assertEquals(1, outLocal.proposals.size)
        val pLocal = outLocal.proposals.single()
        assertEquals(yesterday.toString(), pLocal.dateLocal)
        assertEquals("20:00", pLocal.startLocal)
        assertEquals("07:00", pLocal.endLocal)
        assertEquals(39_600L, pLocal.durationSeconds)
        assertEquals("sleep", pLocal.category)
        assertFalse(pLocal.isFuturePlanned)

        // Kể cả khi Cloud AI nhầm dateLocal = today cho ca qua đêm từ hôm qua, AiProposalGuard neo lại dateLocal = hôm qua.
        val bodyFromWrongDateAi = chatJson(
            "Mình đã tách hoạt động ngủ, bạn xác nhận nhé.",
            JSONObject()
                .put("title", "Ngủ")
                .put("category", "sleep")
                .put("durationSeconds", 39_600)
                .put("dateLocal", today.toString())
                .put("startLocal", "20:00")
                .put("endLocal", "07:00")
                .put("timeEvidence", "explicit_range")
                .put("missingFields", JSONArray())
                .put("needsConfirmation", true)
        )
        val outCloud = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = bodyFromWrongDateAi,
            userText = "Tôi ngủ từ 20h tối hôm qua đến 7h sáng hôm nay",
            today = today,
            nowTime = LocalTime.of(7, 30)
        )
        assertEquals(1, outCloud.proposals.size)
        val pCloud = outCloud.proposals.single()
        assertEquals(yesterday.toString(), pCloud.dateLocal)
        assertEquals("20:00", pCloud.startLocal)
        assertEquals("07:00", pCloud.endLocal)
        assertEquals(39_600L, pCloud.durationSeconds)
        assertFalse(pCloud.isFuturePlanned)
    }

    @Test
    fun `UC-15 chat refuses to guess ambiguous morning vs evening cross-day clock and allows clarifying question`() {
        val ambiguousSentence = "Tôi ngủ từ 8h ngày hôm qua đến 7h sáng hôm nay"

        // Đường local parser: không tạo đề xuất, báo rõ mốc giờ mơ hồ.
        val outLocal = VietnameseChatParser.parse(ambiguousSentence, today = today, nowTime = LocalTime.of(7, 30))
        assertTrue("Không tự suy đoán 08:00 hay 20:00", outLocal.proposals.isEmpty())
        assertTrue(outLocal.warnings.any { it.contains("8h") && it.contains("sáng hay tối") })

        // Đường Cloud AI: nếu AI tự đoán 20:00-07:00 -> guard bác đề xuất ngay.
        val guessedBody = chatJson(
            "Bạn xác nhận giúp nhé.",
            JSONObject()
                .put("title", "Ngủ")
                .put("category", "sleep")
                .put("durationSeconds", 39_600)
                .put("dateLocal", yesterday.toString())
                .put("startLocal", "20:00")
                .put("endLocal", "07:00")
                .put("timeEvidence", "explicit_range")
                .put("missingFields", JSONArray())
                .put("needsConfirmation", true)
        )
        val blockedOut = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = guessedBody,
            userText = ambiguousSentence,
            today = today,
            nowTime = LocalTime.of(7, 30)
        )
        assertTrue("Guard phải chặn đề xuất đoán từ giờ mơ hồ", blockedOut.proposals.isEmpty())
        assertTrue(blockedOut.warnings.any { it.contains("8h") })

        // Nếu AI tuân thủ rule 5b hỏi lại "8h sáng hay 8h tối hôm qua?" -> ReplyGuard giữ nguyên câu hỏi cho người dùng!
        val clarifyBody = chatJson("Bạn bắt đầu ngủ lúc 8h sáng hay 8h tối hôm qua?")
        val clarifyOut = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = clarifyBody,
            userText = ambiguousSentence,
            today = today,
            nowTime = LocalTime.of(7, 30)
        )
        assertTrue(clarifyOut.proposals.isEmpty())
        assertEquals("Bạn bắt đầu ngủ lúc 8h sáng hay 8h tối hôm qua?", clarifyOut.reply)

        // Trường hợp đầu kết thúc mơ hồ ("từ 20h tối hôm qua đến 7h hôm nay") cũng không được tự đoán.
        val ambiguousEnd = VietnameseChatParser.parse(
            "Ngủ từ 20h tối hôm qua đến 7h hôm nay",
            today = today,
            nowTime = LocalTime.of(9, 0)
        )
        assertTrue(ambiguousEnd.proposals.isEmpty())
    }

    @Test
    fun `UC-15 open-ended den gio starting yesterday computes cross-midnight duration cleanly`() {
        val openBody = chatJson(
            "Xác nhận giúp mình nhé.",
            JSONObject()
                .put("title", "Code")
                .put("category", "work")
                .put("durationSeconds", 10_800) // AI lệch giờ kết thúc (22:00-01:00 = 3h) -> guard tự tính lại theo now 02:30 hôm sau = 4h30
                .put("dateLocal", today.toString())
                .put("startLocal", "22:00")
                .put("endLocal", "01:00")
                .put("timeEvidence", "explicit_range")
                .put("missingFields", JSONArray())
                .put("needsConfirmation", true)
        )
        val out = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = openBody,
            userText = "Code từ 22h tối hôm qua đến giờ",
            today = today,
            nowTime = LocalTime.of(2, 30)
        )
        assertEquals(1, out.proposals.size)
        val p = out.proposals.single()
        assertEquals(yesterday.toString(), p.dateLocal)
        assertEquals("22:00", p.startLocal)
        assertEquals("02:30", p.endLocal)
        assertEquals(270L * 60L, p.durationSeconds)
        assertFalse(p.isFuturePlanned)
    }

    // ---------- 2. Xử lý chồng lấn cho lô nhiều đề xuất Chat ----------

    @Test
    fun `multi-proposal chat batch plans safe prefix cleanly and handles in-batch plus existing DB overlaps safely`() {
        val now1900 = LocalDateTime.of(today, LocalTime.of(19, 0)).atZone(zone).toInstant().toEpochMilli()
        val existingWorkRange = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "09:00", "18:00", zone)!!
        val existingWork = (LedgerValidator.validateAndBuildRecord(
            id = "db_work",
            source = "manual",
            rawTitle = "Làm việc",
            rawCategory = "work",
            dateLocal = today.toString(),
            inputDurationMinutes = null,
            startedAt = existingWorkRange.first,
            endedAt = existingWorkRange.second,
            existingRecords = emptyList(),
            nowEpochMs = now1900,
            zoneId = zone
        ) as ValidationResult.Valid).record

        val dao = InMemoryLedgerDao()
        runBlocking {
            assertEquals(DbCommitOutcome.Saved, dao.commitRecordTransactionally(LedgerRecordEntity.fromDomain(existingWork)))
        }

        // Lô Chat gồm 3 đề xuất:
        //  1) Ăn sáng 07:30-08:00 (không trùng -> vào safePrefix)
        //  2) Đọc sách 14:00-15:00 (trùng với db_work đã COMPLETED 09:00-18:00 -> mở ConflictAt!)
        //  3) Chạy bộ 18:30-19:00 (nằm trong remainingInputIds sau khi giải quyết (2))
        val pBreakfast = BatchRecordInput("c1", "chat", "Ăn sáng", "life", today.toString(), null, "07:30", "08:00", idempotencyKey = "k_c1")
        val pRead = BatchRecordInput("c2", "chat", "Đọc sách", "learn", today.toString(), null, "14:00", "15:00", idempotencyKey = "k_c2")
        val pRun = BatchRecordInput("c3", "chat", "Chạy bộ", "health", today.toString(), null, "18:30", "19:00", idempotencyKey = "k_c3")

        val step1 = BatchProposalPlanner.plan(
            inputs = listOf(pBreakfast, pRead, pRun),
            existingRecords = listOf(existingWork),
            activeTimer = null,
            nowEpochMs = now1900,
            zone = zone
        ) as BatchOverlapStep.ConflictAt

        assertEquals(listOf("c1"), step1.safePrefix.map { it.id })
        assertEquals("c2", step1.conflictInputId)
        assertEquals(listOf("c3"), step1.remainingInputIds)
        assertEquals(listOf("db_work"), step1.conflicts.map { it.id })

        // Nếu trong lô có 1 mục sai cú pháp/quá 24h -> Invalid trước khi ghi bất kỳ mục nào!
        val invalidItem = BatchRecordInput("bad", "chat", "Sai", "work", today.toString(), 2000, null, null, idempotencyKey = "k_bad")
        val badPlan = BatchProposalPlanner.plan(listOf(pBreakfast, invalidItem), listOf(existingWork), null, now1900, zone)
        assertTrue(badPlan is BatchOverlapStep.Invalid)

        // Lô tự trùng nội bộ trên DB trống: ["Làm việc 09:00-18:00", "Đọc sách 14:00-15:00"] -> tách cWork vào safePrefix và ConflictAt cBreak trùng cWork
        val cWork = BatchRecordInput("cw", "chat", "Làm việc", "work", today.toString(), null, "09:00", "18:00", idempotencyKey = "k_cw")
        val cBreak = BatchRecordInput("cb", "chat", "Đọc sách", "learn", today.toString(), null, "14:00", "15:00", idempotencyKey = "k_cb")
        val selfStep = BatchProposalPlanner.plan(listOf(cWork, cBreak), emptyList(), null, now1900, zone) as BatchOverlapStep.ConflictAt
        assertEquals(listOf("cw"), selfStep.safePrefix.map { it.id })
        assertEquals("cb", selfStep.conflictInputId)
        assertEquals(listOf("cw"), selfStep.conflicts.map { it.id })

        // Commit safePrefix + áp BREAK_CONFIRMED cho c2 + commit tiếp queue c3
        runBlocking {
            val resPrefix = BatchConfirm.run(dao, step1.safePrefix, null, { null }, now1900, zone) { now1900 }
            assertEquals(ConfirmResult.Committed(1, 0), resPrefix)

            val breakPlan = ActivityBreakEngine.resolveBreak(
                step1.conflictCandidate,
                step1.conflicts,
                BreakDecision.BREAK_CONFIRMED,
                now1900,
                zone
            ).getOrThrow()
            assertEquals(
                DbCommitOutcome.Saved,
                dao.applyOverlapResolutionTransactionally(breakPlan.updatedRecords.map { LedgerRecordEntity.fromDomain(it) }, breakPlan.deletedIds)
            )

            // Bước kế tiếp: lập kế hoạch cho remaining [pRun] trên DB đã cập nhật
            val snapshotNow = dao.getAllRecordsSnapshot().map { it.toDomain() }
            val step2 = BatchProposalPlanner.plan(listOf(pRun), snapshotNow, null, now1900, zone)
            assertTrue(step2 is BatchOverlapStep.ReadyAll)
            val resTail = BatchConfirm.run(dao, (step2 as BatchOverlapStep.ReadyAll).inputs, null, { null }, now1900, zone) { now1900 }
            assertEquals(ConfirmResult.Committed(1, 0), resTail)

            // Không bao giờ ghi trùng nếu gửi lại lô cũ có cùng idempotencyKey
            val snapshotAfter = dao.getAllRecordsSnapshot().map { it.toDomain() }
            val replayStep = BatchProposalPlanner.plan(listOf(pBreakfast, pRead, pRun), snapshotAfter, null, now1900, zone)
            assertTrue(replayStep is BatchOverlapStep.ReadyAll)
            assertEquals(3, (replayStep as BatchOverlapStep.ReadyAll).skippedDuplicates)
            assertTrue(replayStep.inputs.isEmpty())
        }

        // Bảo toàn dữ liệu đã xác nhận: work 09-14 + 15-18 = 8h, đọc sách 1h, ăn sáng 30p, chạy bộ 30p => đúng 10h thực tế!
        val finalRecords = runBlocking { dao.getAllRecordsSnapshot().map { it.toDomain() } }
        val stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), finalRecords, zone, now1900)
        assertEquals(10 * 3_600L, stats.timedSeconds)
        assertEquals(10 * 3_600L, stats.confirmedCompletedSeconds)
        assertEquals(10 * 3_600L, JournalDayTotal.secondsForDate(today.toString(), finalRecords, zone, now1900))
    }

    // ---------- 3. Tính nhất quán Ledger - Overview - Journal - Analytics - Goals - AI Coach ----------

    @Test
    fun `cross-midnight sleep and parallel breaks stay 100pct consistent across Ledger, Overview, Journal, Analytics, Goals and Coach`() {
        val sleepRange = LedgerValidator.parseTimeRangeToEpochMillis(yesterday.toString(), "20:00", "07:00", zone)!!
        val sleep = (LedgerValidator.validateAndBuildRecord(
            id = "rec_sleep",
            source = "chat",
            rawTitle = "Ngủ",
            rawCategory = "sleep",
            dateLocal = yesterday.toString(),
            inputDurationMinutes = null,
            startedAt = sleepRange.first,
            endedAt = sleepRange.second,
            existingRecords = emptyList(),
            nowEpochMs = now0730,
            zoneId = zone
        ) as ValidationResult.Valid).record

        val records = listOf(sleep)

        // 1) Analytics + Overview
        val yStats = LedgerAnalyticsCalculator.computeDayStats(yesterday.toString(), records, zone, now0730)
        val tStats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), records, zone, now0730)
        val ySummary = DashboardSummary.from(yStats)
        val tSummary = DashboardSummary.from(tStats)
        assertEquals(4 * 3_600L, yStats.timedSeconds)
        assertEquals(4 * 3_600L, ySummary.recordedSeconds)
        assertEquals(7 * 3_600L, tStats.timedSeconds)
        assertEquals(7 * 3_600L, tSummary.recordedSeconds)

        // 2) Journal (secondsForDate + overnightCarryoverSeconds) khớp đúng 4h hôm qua & 7h hôm nay
        assertEquals(4 * 3_600L, JournalDayTotal.secondsForDate(yesterday.toString(), records, zone, now0730))
        assertEquals(7 * 3_600L, JournalDayTotal.secondsForDate(today.toString(), records, zone, now0730))
        assertEquals(7 * 3_600L, JournalDayTotal.overnightCarryoverSeconds(today.toString(), records, zone, now0730))

        // 3) Weekly insights (7 ngày) khớp tổng 11h
        val weekly = LedgerAnalyticsCalculator.computeWeeklyInsights(today.toString(), records, zone, now0730)
        assertEquals(11 * 3_600L, weekly.totalTimedSeconds)

        // 4) GoalRules (mục tiêu ngủ tối đa 8h/ngày): hôm qua 4h (đạt), hôm nay 7h (đạt)!
        val sleepGoal = GoalRules.create(
            GoalTargetType.CATEGORY, TimeCategory.SLEEP.key, GoalKind.MAX, GoalPeriod.DAY,
            targetSeconds = 8 * 3_600L, today = yesterday, nowEpochMs = now0730, id = "g_sleep", groupId = "grp_sleep"
        )
        assertEquals(4 * 3_600L, GoalRules.progress(sleepGoal, records, yesterday, now0730, zone).usedSeconds)
        assertEquals(7 * 3_600L, GoalRules.progress(sleepGoal, records, today, now0730, zone).usedSeconds)

        // 5) AI Time Coach ("Hôm nay", days = 1): thấy đúng 420 phút (7h) của hôm nay & cho phép câu kể 7 giờ!
        val coachToday = CoachDigestBuilder.build(records, emptyList(), listOf(sleepGoal), days = 1, nowEpochMs = now0730, zone = zone)
        assertTrue(coachToday.hasAnyData)
        assertTrue("Coach hôm nay phải chứa 420 phút", coachToday.allowedMinutes.contains(420))
        assertTrue(coachToday.text.contains("420"))
        assertEquals(
            CoachCheck.Ok,
            CoachGuard.check("Hôm nay bạn đã ghi nhận 7 giờ ngủ, vẫn nằm trong giới hạn 8 giờ.", coachToday)
        )

        // Coach 7 ngày: tách riêng 240 phút hôm qua + 420 phút hôm nay!
        val coachWeek = CoachDigestBuilder.build(records, emptyList(), listOf(sleepGoal), days = 7, nowEpochMs = now0730, zone = zone)
        assertTrue(coachWeek.allowedMinutes.contains(240) && coachWeek.allowedMinutes.contains(420))
    }

    @Test
    fun `JournalDayTotal never double counts PARALLEL_CONFIRMED overlapping intervals`() {
        val now1900 = LocalDateTime.of(today, LocalTime.of(19, 0)).atZone(zone).toInstant().toEpochMilli()
        val rWork = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "09:00", "18:00", zone)!!
        val rBook = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "14:00", "15:00", zone)!!
        val work = (LedgerValidator.validateAndBuildRecord("w", "manual", "Làm việc", "work", today.toString(), null, rWork.first, rWork.second, emptyList(), activeTimer = null, nowEpochMs = now1900, zoneId = zone) as ValidationResult.Valid).record
        val book = (LedgerValidator.validateAndBuildRecord("b", "manual", "Đọc sách", "learn", today.toString(), null, rBook.first, rBook.second, emptyList(), activeTimer = null, nowEpochMs = now1900, zoneId = zone) as ValidationResult.Valid).record
        val par = ActivityBreakEngine.resolveBreak(book, listOf(work), BreakDecision.PARALLEL_CONFIRMED, now1900, zone).getOrThrow().updatedRecords

        assertEquals(9 * 3_600L, JournalDayTotal.seconds(par, now1900))
        assertEquals(9 * 3_600L, JournalDayTotal.secondsForDate(today.toString(), par, zone, now1900))
        assertEquals(9 * 3_600L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), par, zone, now1900).timedSeconds)
    }

    // ---------- 4. Notification launch contract & v1.2.0 ----------

    @Test
    fun `completion notification policy, receiver, MainActivity intent wiring and v1_2_0 manifest match contract`() {
        val now1010 = LocalDateTime.of(today, LocalTime.of(10, 10)).atZone(zone).toInstant().toEpochMilli()
        val r = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "09:00", "18:00", zone)!!
        val doing = (LedgerValidator.validateAndBuildRecord("doing_1", "manual", "Làm sâu", "work", today.toString(), null, r.first, r.second, emptyList(), activeTimer = null, nowEpochMs = now1010, zoneId = zone) as ValidationResult.Valid).record
        assertEquals(listOf("doing_1"), CompletionReminderPolicy.selectTargets(listOf(doing), now1010).map { it.id })

        val done = (ConfirmationEngine.confirmCompleted(doing, emptyList(), null, now1010 + 8 * 3_600_000L, zone) as ConfirmationActionResult.Applied).updatedRecords.single()
        assertTrue(CompletionReminderPolicy.selectTargets(listOf(done), now1010 + 8 * 3_600_000L).isEmpty())

        val mainSrc = File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText()
        assertTrue(mainSrc.contains("EXTRA_CONFIRM_RECORD_ID"))
        assertTrue(mainSrc.contains("onNewIntent(intent: Intent)"))
        assertTrue(mainSrc.contains("openActivityConfirmationFromNotification(targetId)"))

        val remSrc = File("src/main/java/com/timewallet/mvp/notifications/CompletionReminder.kt").readText()
        assertTrue(remSrc.contains("ACTION_CONFIRM_REMINDER"))
        assertTrue(remSrc.contains("timewallet://confirm/"))
        assertNotNull(CompletionNotificationHelper.CHANNEL_ID)

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("CompletionReminderReceiver"))
        assertTrue(manifest.contains("android:launchMode=\"singleTop\""))
    }
}
