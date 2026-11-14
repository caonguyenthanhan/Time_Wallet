package com.timewallet.mvp

import android.content.SharedPreferences
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.ActivityReviewNoteEntity
import com.timewallet.mvp.data.local.AesGcmKeystoreVault
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.CategoryBudgetEntity
import com.timewallet.mvp.data.local.DailyReviewEntity
import com.timewallet.mvp.data.local.LedgerDao
import com.timewallet.mvp.data.local.LedgerJsonExporter
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.data.local.TimeWalletDatabase
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import org.json.JSONObject
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.DailyReviewSnapshot
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.CognitiveState
import com.timewallet.mvp.domain.usecase.DailyReviewAssistant
import com.timewallet.mvp.domain.usecase.DiceBehavioralContext
import com.timewallet.mvp.domain.usecase.DopamineTimeDiceEngine
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import androidx.sqlite.db.SupportSQLiteDatabase

class TimeWalletDomainTest {

    private val testZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val testDate = LocalDate.of(2026, 11, 11) // Wednesday
    private val testDateStr = "2026-11-11"
    // 2026-11-11T18:00:00+07:00
    private val nowEpochMs = testDate.atTime(18, 0).atZone(testZone).toInstant().toEpochMilli()

    @Test
    fun `chat parser keeps duration-only input untimed without fabricating start or end time`() {
        val outcome = VietnameseChatParser.parse("Hôm nay học kỹ thuật Feynman 45 phút", testDate)
        assertEquals(1, outcome.proposals.size)
        val proposal = outcome.proposals.first()
        assertEquals("learn", proposal.category)
        assertEquals(2700L, proposal.durationSeconds)
        assertNull(proposal.startLocal)
        assertNull(proposal.endLocal)
        assertEquals("duration_only", proposal.timeEvidence)
        assertTrue(proposal.missingFields.containsAll(listOf("startLocal", "endLocal")))

        val validation = LedgerValidator.validateAndBuildRecord(
            source = "chat",
            rawTitle = proposal.title,
            rawCategory = proposal.category,
            dateLocal = proposal.dateLocal,
            inputDurationMinutes = proposal.durationMinutes,
            startedAt = null,
            endedAt = null,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        )
        assertTrue(validation is ValidationResult.Valid)
        val record = (validation as ValidationResult.Valid).record
        assertEquals(TimeLayer.REPORTED_UNTIMED, record.layer)
        assertNull(record.startedAt)
        assertNull(record.endedAt)
    }

    @Test
    fun `refinement01 - at 14_35 activity 13_00 to 18_00 is ACTIVE and only counts 1h35m actual not 5h`() {
        val now1435 = testDate.atTime(14, 35).atZone(testZone).toInstant().toEpochMilli()
        val range13to18 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "13:00", "18:00", testZone)!!

        val validation = LedgerValidator.validateAndBuildRecord(
            source = "manual",
            rawTitle = "Làm việc sâu chiều",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 300,
            startedAt = range13to18.first,
            endedAt = range13to18.second,
            existingRecords = emptyList(),
            nowEpochMs = now1435,
            zoneId = testZone
        )
        assertTrue(validation is ValidationResult.Valid)
        val activeRecord = (validation as ValidationResult.Valid).record
        assertEquals(TimeLayer.ACTIVE, activeRecord.layer)

        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = testDateStr,
            records = listOf(activeRecord),
            zoneId = testZone,
            nowEpochMs = now1435
        )

        assertEquals(5_700L, dayStats.timedSeconds)
        assertEquals(12_300L, dayStats.plannedSeconds)
        assertEquals(52_500L, dayStats.elapsedDaySeconds)
        assertEquals(33_900L, dayStats.remainingDaySeconds)
        assertEquals(46_800L, dayStats.untrackedPastSeconds)
    }

    @Test
    fun `refinement01 - future records are PLANNED and do not affect used time statistics until confirmed after occurring`() {
        val now1435 = testDate.atTime(14, 35).atZone(testZone).toInstant().toEpochMilli()
        val futureRange19to21 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "19:00", "21:00", testZone)!!

        val validation = LedgerValidator.validateAndBuildRecord(
            source = "manual",
            rawTitle = "Học ngoại ngữ buổi tối",
            rawCategory = "learn",
            dateLocal = testDateStr,
            inputDurationMinutes = 120,
            startedAt = futureRange19to21.first,
            endedAt = futureRange19to21.second,
            existingRecords = emptyList(),
            nowEpochMs = now1435,
            zoneId = testZone
        )
        assertTrue(validation is ValidationResult.Valid)
        val plannedRecord = (validation as ValidationResult.Valid).record
        assertEquals(TimeLayer.PLANNED, plannedRecord.layer)

        val dayStatsAt1435 = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = testDateStr,
            records = listOf(plannedRecord),
            zoneId = testZone,
            nowEpochMs = now1435
        )
        assertEquals(0L, dayStatsAt1435.timedSeconds)
        assertEquals(0L, dayStatsAt1435.untimedSeconds)
        assertEquals(7_200L, dayStatsAt1435.plannedSeconds)

        val weeklyAt1435 = LedgerAnalyticsCalculator.computeWeeklyInsights(
            endDateLocal = testDateStr,
            records = listOf(plannedRecord),
            zoneId = testZone,
            nowEpochMs = now1435
        )
        assertEquals(0L, weeklyAt1435.totalTimedSeconds)
        assertEquals(0L, weeklyAt1435.totalUnplacedSeconds)

        val prematureConfirm = LedgerValidator.confirmPlannedOrActiveToActual(
            record = plannedRecord,
            existingRecords = listOf(plannedRecord),
            nowEpochMs = now1435,
            zoneId = testZone
        )
        assertTrue(prematureConfirm is ValidationResult.Invalid)

        val now2130 = testDate.atTime(21, 30).atZone(testZone).toInstant().toEpochMilli()
        val validConfirm = LedgerValidator.confirmPlannedOrActiveToActual(
            record = plannedRecord,
            existingRecords = listOf(plannedRecord),
            nowEpochMs = now2130,
            zoneId = testZone
        )
        assertTrue(validConfirm is ValidationResult.Valid)
        val confirmedRecord = (validConfirm as ValidationResult.Valid).record
        assertEquals(TimeLayer.REPORTED_TIMED, confirmedRecord.layer)
        assertEquals(7_200L, confirmedRecord.durationSeconds)
    }

    @Test
    fun `refinement01 - prevents double counting of overlapping actual intervals in validator and analytics`() {
        val range1 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "09:00", "10:30", testZone)!!
        val range2 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "10:00", "11:00", testZone)!!

        val rec1 = (LedgerValidator.validateAndBuildRecord(
            source = "chat",
            rawTitle = "Code module A",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 90,
            startedAt = range1.first,
            endedAt = range1.second,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        val overlapAttempt = LedgerValidator.validateAndBuildRecord(
            source = "chat",
            rawTitle = "Họp nhóm",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 60,
            startedAt = range2.first,
            endedAt = range2.second,
            existingRecords = listOf(rec1),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        )
        assertTrue(overlapAttempt is ValidationResult.Invalid)

        val forcedOverlappingRec2 = rec1.copy(
            id = "rec-overlap-2",
            title = "Họp nhóm",
            startedAt = range2.first,
            endedAt = range2.second,
            durationSeconds = 3600L
        )
        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = testDateStr,
            records = listOf(rec1, forcedOverlappingRec2),
            zoneId = testZone,
            nowEpochMs = nowEpochMs
        )
        assertEquals(7_200L, dayStats.timedSeconds)
    }

    @Test
    fun `refinement01 - single sentence with 5 activities parses into batch of 5 proposals without fabricating times`() {
        val outcome = VietnameseChatParser.parse(
            "Từ 8h đến 9h30 làm dự án, đọc sách 45 phút, chạy bộ 30 phút, ăn trưa 30 phút, nghỉ ngơi 20 phút",
            testDate
        )
        assertEquals(5, outcome.proposals.size)

        assertEquals("08:00", outcome.proposals[0].startLocal)
        assertEquals("09:30", outcome.proposals[0].endLocal)
        assertEquals(5_400L, outcome.proposals[0].durationSeconds)

        for (i in 1..4) {
            assertNull(outcome.proposals[i].startLocal)
            assertNull(outcome.proposals[i].endLocal)
            assertEquals("duration_only", outcome.proposals[i].timeEvidence)
        }
        assertEquals(2_700L, outcome.proposals[1].durationSeconds)
        assertEquals(1_800L, outcome.proposals[2].durationSeconds)
        assertEquals(1_800L, outcome.proposals[3].durationSeconds)
        assertEquals(1_200L, outcome.proposals[4].durationSeconds)
    }

    @Test
    fun `refinement01 - legacy REPORTED_UNPLACED data in Room DB is preserved and mapped to REPORTED_UNTIMED`() {
        assertEquals(TimeLayer.REPORTED_UNTIMED, TimeLayer.fromStorageString("REPORTED_UNPLACED", null, null))
        assertEquals(TimeLayer.REPORTED_UNTIMED, TimeLayer.fromStorageString("REPORTED_UNTIMED", null, null))

        val legacyEntity = LedgerRecordEntity(
            id = "legacy-001",
            source = "chat",
            layer = "REPORTED_UNPLACED",
            createdAt = nowEpochMs,
            userConfirmedAt = nowEpochMs,
            category = "learn",
            title = "Đọc tài liệu cũ",
            date = testDateStr,
            durationSeconds = 1800L,
            startedAt = null,
            endedAt = null,
            timezone = testZone.id,
            status = "CONFIRMED",
            idempotencyKey = "idem-legacy-001"
        )
        val domainRecord = legacyEntity.toDomain()
        assertEquals(TimeLayer.REPORTED_UNTIMED, domainRecord.layer)
        assertEquals(1800L, domainRecord.durationSeconds)
        assertEquals("Đọc tài liệu cũ", domainRecord.title)
    }

    @Test
    fun `24h day stats and 7-day insights do not add untimed duration into timed total`() {
        val timedRange = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "09:00", "10:00", testZone)!!
        val timedRecord = (LedgerValidator.validateAndBuildRecord(
            source = "timer",
            rawTitle = "Coding",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = null,
            startedAt = timedRange.first,
            endedAt = timedRange.second,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        val untimedRecord = (LedgerValidator.validateAndBuildRecord(
            source = "chat",
            rawTitle = "Học từ vựng",
            rawCategory = "learn",
            dateLocal = testDateStr,
            inputDurationMinutes = 45,
            startedAt = null,
            endedAt = null,
            existingRecords = listOf(timedRecord),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = testDateStr,
            records = listOf(timedRecord, untimedRecord),
            zoneId = testZone,
            nowEpochMs = nowEpochMs
        )

        assertEquals(3600L, dayStats.timedSeconds)
        assertEquals(2700L, dayStats.untimedSeconds)
        assertEquals(64_800L - 3600L, dayStats.untrackedPastSeconds)

        val weekly = LedgerAnalyticsCalculator.computeWeeklyInsights(
            endDateLocal = testDateStr,
            records = listOf(timedRecord, untimedRecord),
            zoneId = testZone,
            nowEpochMs = nowEpochMs
        )
        assertEquals(3600L, weekly.totalTimedSeconds)
        assertEquals(2700L, weekly.totalUnplacedSeconds)
    }

    @Test
    fun `active timer session recovers elapsed seconds and detects system clock skew`() {
        val session = ActiveTimerSession(
            title = "Deep Work",
            categoryKey = "work",
            startedAtEpochMs = 1_000_000L,
            startedElapsedRealtimeMs = 500_000L,
            timezone = testZone.id
        )

        val normalSnap = session.computeElapsedSeconds(
            nowEpochMs = 1_300_000L,
            nowElapsedRealtimeMs = 800_000L
        )
        assertEquals(300L, normalSnap.elapsedSeconds)
        assertEquals(false, normalSnap.clockSkewDetected)

        val skewedSnap = session.computeElapsedSeconds(
            nowEpochMs = 900_000L,
            nowElapsedRealtimeMs = 1_100_000L
        )
        assertEquals(600L, skewedSnap.elapsedSeconds)
        assertEquals(true, skewedSnap.clockSkewDetected)
    }

    @Test
    fun `explicit start time at 2am with duration 5h computes 02_00 to 07_00 while missing start time stays untimed`() {
        val twoAm = LocalTime.of(2, 0)

        val unanchoredOutcome = VietnameseChatParser.parse(
            inputText = "tôi sẽ ngủ 5h tiếng",
            today = testDate,
            nowTime = twoAm
        )
        val unanchored = unanchoredOutcome.proposals.first()
        assertEquals("sleep", unanchored.category)
        assertEquals(18_000L, unanchored.durationSeconds)
        assertNull(unanchored.startLocal)
        assertNull(unanchored.endLocal)

        val explicitStartOutcome = VietnameseChatParser.parse(
            inputText = "lúc 2h sáng ngủ 5 tiếng",
            today = testDate,
            nowTime = twoAm
        )
        val anchored = explicitStartOutcome.proposals.first()
        assertEquals("02:00", anchored.startLocal)
        assertEquals("07:00", anchored.endLocal)
        assertEquals(18_000L, anchored.durationSeconds)
    }

    @Test
    fun `dopamine and pomodoro dice engine adapts weights to cognitive fatigue and guards against dopamine traps`() {
        val faces = DopamineTimeDiceEngine.buildFaces(TimeCategory.WORK)
        assertEquals(6, faces.size)
        assertEquals(120, faces[1].durationMinutes)
        assertEquals(TimeCategory.WORK, faces[1].category)
        assertEquals(30, faces[2].durationMinutes)
        assertEquals(TimeCategory.REST, faces[2].category)

        val fatiguedCtx = DiceBehavioralContext(
            todayFocusMinutes = 160,
            todayRestAndFunMinutes = 20,
            recentContinuousFocusMinutes = 100,
            consecutiveBreakRolls = 0
        )
        val (fatigueState, fatigueProbs) = DopamineTimeDiceEngine.computeProbabilities(fatiguedCtx)
        assertEquals(CognitiveState.FATIGUE_RECOVERY_NEEDED, fatigueState)
        assertEquals(5, fatigueProbs.first { it.face.faceNumber == 2 }.probabilityPercent)
        assertEquals(32, fatigueProbs.first { it.face.faceNumber == 3 }.probabilityPercent)

        val bingeBreakCtx = DiceBehavioralContext(
            todayFocusMinutes = 30,
            todayRestAndFunMinutes = 60,
            recentContinuousFocusMinutes = 0,
            consecutiveBreakRolls = 2
        )
        val (guardState, guardProbs) = DopamineTimeDiceEngine.computeProbabilities(bingeBreakCtx)
        assertEquals(CognitiveState.DOPAMINE_RESET_GUARD, guardState)
        val focusTotalPercent = guardProbs
            .filter { it.face.faceNumber in listOf(1, 2, 4) }
            .sumOf { it.probabilityPercent }
        assertEquals(80, focusTotalPercent)
    }

    @Test
    fun `json exporter produces valid schema with records`() {
        val json = LedgerJsonExporter.exportToJsonString(
            records = emptyList(),
            activeTimer = null,
            exportedAtIso = "2026-11-11T10:00:00Z"
        )
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"items\": ["))
    }

    // =========================================================================
    // REFINEMENT 02 TESTS (YÊU CẦU 1..7 & NGHIỆM THU)
    // =========================================================================

    @Test
    fun `refinement02 - self-reported ACTIVE record does NOT auto-complete when past end time and requires explicit confirmation`() {
        val now1435 = testDate.atTime(14, 35).atZone(testZone).toInstant().toEpochMilli()
        val now1900 = testDate.atTime(19, 0).atZone(testZone).toInstant().toEpochMilli()
        val range13to18 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "13:00", "18:00", testZone)!!

        // User self-reports 13:00-18:00 at 14:35 -> saved as ACTIVE
        val activeRecord = (LedgerValidator.validateAndBuildRecord(
            source = "manual",
            rawTitle = "Làm việc chiều",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 300,
            startedAt = range13to18.first,
            endedAt = range13to18.second,
            existingRecords = emptyList(),
            nowEpochMs = now1435,
            zoneId = testZone
        ) as ValidationResult.Valid).record
        assertEquals(TimeLayer.ACTIVE, activeRecord.layer)
        assertEquals("ACTIVE", activeRecord.status)

        // When clock passes 18:00 (at 19:00) WITHOUT user confirmation:
        // 1) Must NOT auto-transition to REPORTED_TIMED; stays ACTIVE and flags requiresCompletionConfirmationAt = true
        assertEquals(TimeLayer.ACTIVE, activeRecord.resolveLayerAt(now1900, testDateStr))
        assertTrue(activeRecord.requiresCompletionConfirmationAt(now1900))

        // 2) Day stats at 19:00 must NOT auto-count the unconfirmed 14:35-18:00 tail as used time
        val statsAt1900BeforeConfirm = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = testDateStr,
            records = listOf(activeRecord),
            zoneId = testZone,
            nowEpochMs = now1900
        )
        assertEquals(5_700L, statsAt1900BeforeConfirm.timedSeconds) // Still 1h 35m confirmed
        assertEquals(12_300L, statsAt1900BeforeConfirm.plannedSeconds) // 3h 25m unconfirmed

        // 3) User explicitly confirms actual completion at 19:00 (e.g. finished at 17:30 -> 4h 30m = 16,200s)
        val actualEnd1730 = testDate.atTime(17, 30).atZone(testZone).toInstant().toEpochMilli()
        val confirmResult = LedgerValidator.confirmPlannedOrActiveToActual(
            record = activeRecord,
            existingRecords = listOf(activeRecord),
            nowEpochMs = now1900,
            zoneId = testZone,
            actualEndedAtEpochMs = actualEnd1730
        )
        assertTrue(confirmResult is ValidationResult.Valid)
        val confirmedRecord = (confirmResult as ValidationResult.Valid).record
        assertEquals(TimeLayer.REPORTED_TIMED, confirmedRecord.layer)
        assertEquals("CONFIRMED", confirmedRecord.status)
        assertFalse(confirmedRecord.requiresCompletionConfirmationAt(now1900))
        assertEquals(16_200L, confirmedRecord.durationSeconds)

        val statsAfterConfirm = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = testDateStr,
            records = listOf(confirmedRecord),
            zoneId = testZone,
            nowEpochMs = now1900
        )
        assertEquals(16_200L, statsAfterConfirm.timedSeconds)
        assertEquals(0L, statsAfterConfirm.plannedSeconds)
    }

    @Test
    fun `refinement02 - untimed duration is strictly separated from timed category totals without misleading combined sum`() {
        val range08to10 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "08:00", "10:00", testZone)!!
        val timedLearn = (LedgerValidator.validateAndBuildRecord(
            source = "manual",
            rawTitle = "Học thuật toán",
            rawCategory = "learn",
            dateLocal = testDateStr,
            inputDurationMinutes = 120,
            startedAt = range08to10.first,
            endedAt = range08to10.second,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        val untimedLearn = (LedgerValidator.validateAndBuildRecord(
            source = "chat",
            rawTitle = "Ôn flashcard",
            rawCategory = "learn",
            dateLocal = testDateStr,
            inputDurationMinutes = 45,
            startedAt = null,
            endedAt = null,
            existingRecords = listOf(timedLearn),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = testDateStr,
            records = listOf(timedLearn, untimedLearn),
            zoneId = testZone,
            nowEpochMs = nowEpochMs
        )
        // Timed = 2h (7200s), Untimed = 45m (2700s) -> strictly separate in breakdown lists
        assertEquals(7_200L, dayStats.timedSeconds)
        assertEquals(2_700L, dayStats.untimedSeconds)
        assertEquals(7_200L, dayStats.categoryBreakdown.single { it.category == TimeCategory.LEARN }.durationSeconds)
        assertEquals(2_700L, dayStats.untimedCategoryBreakdown.single { it.category == TimeCategory.LEARN }.durationSeconds)

        val weekly = LedgerAnalyticsCalculator.computeWeeklyInsights(
            endDateLocal = testDateStr,
            records = listOf(timedLearn, untimedLearn),
            zoneId = testZone,
            nowEpochMs = nowEpochMs
        )
        assertEquals(7_200L, weekly.totalTimedSeconds)
        assertEquals(2_700L, weekly.totalUntimedSeconds)
        assertEquals(7_200L, weekly.categoryTotals.single { it.category == TimeCategory.LEARN }.durationSeconds)
        assertEquals(2_700L, weekly.untimedCategoryTotals.single { it.category == TimeCategory.LEARN }.durationSeconds)
    }

    @Test
    fun `refinement02 - Room MIGRATION_1_2 upgrades v1 SQLite database preserving old records and creating category_budgets`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { stmt ->
                // 1. Create exact Room v1 schema
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `ledger_records` (
                        `id` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `layer` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `userConfirmedAt` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `startedAt` INTEGER,
                        `endedAt` INTEGER,
                        `timezone` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                // Insert legacy v1 data including REPORTED_UNPLACED and MEASURED
                stmt.execute(
                    """
                    INSERT INTO `ledger_records` VALUES
                    ('v1-rec-1', 'chat', 'REPORTED_UNPLACED', 1700000000000, 1700000000000, 'learn', 'Đọc sách v1', '2026-11-10', 2700, NULL, NULL, 'Asia/Ho_Chi_Minh', 'CONFIRMED', 'idem-v1-1'),
                    ('v1-rec-2', 'timer', 'MEASURED', 1700000000000, 1700000000000, 'work', 'Code v1', '2026-11-10', 3600, 1700000000000, 1700003600000, 'Asia/Ho_Chi_Minh', 'CONFIRMED', 'idem-v1-2')
                    """.trimIndent()
                )
            }

            // 2. Execute Room MIGRATION_1_2 against the SQLite connection via SupportSQLiteDatabase proxy
            val supportDb = createSupportSQLiteDatabaseOverJdbc(conn)
            TimeWalletDatabase.MIGRATION_1_2.migrate(supportDb)

            // 3. Verify all v1 records are preserved and REPORTED_UNPLACED was migrated to REPORTED_UNTIMED
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT id, layer, title, durationSeconds FROM ledger_records ORDER BY id ASC")
                assertTrue(rs.next())
                assertEquals("v1-rec-1", rs.getString("id"))
                assertEquals("REPORTED_UNTIMED", rs.getString("layer"))
                assertEquals("Đọc sách v1", rs.getString("title"))
                assertEquals(2700L, rs.getLong("durationSeconds"))

                assertTrue(rs.next())
                assertEquals("v1-rec-2", rs.getString("id"))
                assertEquals("MEASURED", rs.getString("layer"))
                assertEquals("Code v1", rs.getString("title"))
                assertEquals(3600L, rs.getLong("durationSeconds"))
                assertFalse(rs.next())
            }

            // 4. Verify new category_budgets table exists in v2 and accepts CategoryBudgetEntity data
            val budgetEntity = CategoryBudgetEntity.fromDomain(
                CategoryTimeBudget(
                    category = TimeCategory.WORK,
                    period = BudgetPeriod.WEEKLY,
                    targetSeconds = 36_000L,
                    isPriority = true,
                    priorityOrder = 0,
                    updatedAt = nowEpochMs
                )
            )
            conn.prepareStatement(
                "INSERT INTO category_budgets (categoryKey, period, targetSeconds, isPriority, priorityOrder, updatedAt) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, budgetEntity.categoryKey)
                ps.setString(2, budgetEntity.period)
                ps.setLong(3, budgetEntity.targetSeconds)
                ps.setInt(4, budgetEntity.isPriority)
                ps.setInt(5, budgetEntity.priorityOrder)
                ps.setLong(6, budgetEntity.updatedAt)
                ps.executeUpdate()
            }

            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT categoryKey, period, targetSeconds, isPriority FROM category_budgets")
                assertTrue(rs.next())
                assertEquals("work", rs.getString("categoryKey"))
                assertEquals("WEEKLY", rs.getString("period"))
                assertEquals(36_000L, rs.getLong("targetSeconds"))
                assertEquals(1, rs.getInt("isPriority"))
            }
        }
    }

    @Test
    fun `refinement02 - batch validation and DAO batch transaction are atomic when any item is invalid or overlapping`() = runBlocking {
        // Part A: Domain-level atomic batch validation rejects the whole batch if 1 item is invalid
        val mixedInputs = listOf(
            BatchRecordInput(
                rawTitle = "Làm dự án",
                rawCategory = "work",
                dateLocal = testDateStr,
                durationMinutes = 90,
                startHm = "08:00",
                endHm = "09:30"
            ),
            BatchRecordInput(
                rawTitle = "Đọc sách",
                rawCategory = "learn",
                dateLocal = testDateStr,
                durationMinutes = 45
            ),
            BatchRecordInput(
                rawTitle = "Mục lỗi thời lượng âm",
                rawCategory = "rest",
                dateLocal = testDateStr,
                durationMinutes = 0 // Invalid!
            )
        )
        val batchCheck = LedgerValidator.validateBatchAtomic(
            inputs = mixedInputs,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        )
        assertTrue(batchCheck is BatchValidationResult.Invalid)
        assertEquals(2, (batchCheck as BatchValidationResult.Invalid).failedIndex)

        // Part B: DAO-level atomic batch transaction rolls back / aborts all writes if any entity conflicts or is invalid
        val fakeDao = InMemoryTransactionalLedgerDao()
        val r1Range = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "08:00", "09:00", testZone)!!
        val r2Range = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "08:30", "09:30", testZone)!! // Overlaps r1!

        val e1 = LedgerRecordEntity(
            id = "b-1",
            source = "chat",
            layer = "REPORTED_TIMED",
            createdAt = nowEpochMs,
            userConfirmedAt = nowEpochMs,
            category = "work",
            title = "Việc 1 hợp lệ",
            date = testDateStr,
            durationSeconds = 3600L,
            startedAt = r1Range.first,
            endedAt = r1Range.second,
            timezone = testZone.id,
            status = "CONFIRMED",
            idempotencyKey = "idem-b-1"
        )
        val e2Overlapping = e1.copy(
            id = "b-2",
            title = "Việc 2 trùng giờ với Việc 1",
            startedAt = r2Range.first,
            endedAt = r2Range.second,
            idempotencyKey = "idem-b-2"
        )
        val e3ValidUntimed = e1.copy(
            id = "b-3",
            layer = "REPORTED_UNTIMED",
            title = "Việc 3 chưa gắn giờ",
            durationSeconds = 1800L,
            startedAt = null,
            endedAt = null,
            idempotencyKey = "idem-b-3"
        )

        val failedOutcome = fakeDao.commitBatchRecordsTransactionally(listOf(e1, e2Overlapping, e3ValidUntimed))
        assertTrue(failedOutcome.abortedAtomically)
        assertEquals(0, failedOutcome.savedCount)
        // Crucial atomicity invariant: e1 and e3 were NOT saved because e2 failed!
        assertTrue(fakeDao.getAllRecordsSnapshot().isEmpty())

        // Also test structural invalidity inside DAO batch (e.g., durationSeconds = -10)
        val eInvalidDuration = e3ValidUntimed.copy(id = "b-bad", durationSeconds = -10L, idempotencyKey = "idem-bad")
        val invalidOutcome = fakeDao.commitBatchRecordsTransactionally(listOf(e1, eInvalidDuration))
        assertTrue(invalidOutcome.abortedAtomically)
        assertEquals(0, invalidOutcome.savedCount)
        assertTrue(fakeDao.getAllRecordsSnapshot().isEmpty())

        // Now commit a valid batch -> all items saved atomically
        val validOutcome = fakeDao.commitBatchRecordsTransactionally(listOf(e1, e3ValidUntimed))
        assertFalse(validOutcome.abortedAtomically)
        assertEquals(2, validOutcome.savedCount)
        assertEquals(2, fakeDao.getAllRecordsSnapshot().size)
    }

    @Test
    fun `refinement02 - weekly budget 10h with 6h valid timed records displays 4h remaining and excludes PLANNED and untimed`() {
        // Acceptance Criterion: "Ngân sách 10h/tuần, đã ghi nhận hợp lệ 6h, hiển thị còn 4h. PLANNED không được tính là đã sử dụng."
        val budget10h = CategoryTimeBudget(
            category = TimeCategory.WORK,
            period = BudgetPeriod.WEEKLY,
            targetSeconds = 10 * 3600L, // 10h = 36,000s
            isPriority = true
        )

        // Tuesday 2026-11-10: 3h valid timed work (09:00-12:00)
        val tueStr = "2026-11-10"
        val tueRange = LedgerValidator.parseTimeRangeToEpochMillis(tueStr, "09:00", "12:00", testZone)!!
        val recTue3h = (LedgerValidator.validateAndBuildRecord(
            source = "timer",
            rawTitle = "Làm việc sáng thứ Ba",
            rawCategory = "work",
            dateLocal = tueStr,
            inputDurationMinutes = null,
            startedAt = tueRange.first,
            endedAt = tueRange.second,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        // Wednesday 2026-11-11: 3h valid timed work (14:00-17:00)
        val wedRange = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "14:00", "17:00", testZone)!!
        val recWed3h = (LedgerValidator.validateAndBuildRecord(
            source = "manual",
            rawTitle = "Làm việc chiều thứ Tư",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 180,
            startedAt = wedRange.first,
            endedAt = wedRange.second,
            existingRecords = listOf(recTue3h),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        // Wednesday 2026-11-11: 2h PLANNED future work (19:00-21:00) -> must NOT count as used
        val plannedRange = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "19:00", "21:00", testZone)!!
        val recPlanned2h = (LedgerValidator.validateAndBuildRecord(
            source = "manual",
            rawTitle = "Dự kiến làm tối",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 120,
            startedAt = plannedRange.first,
            endedAt = plannedRange.second,
            existingRecords = listOf(recTue3h, recWed3h),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record
        assertEquals(TimeLayer.PLANNED, recPlanned2h.layer)

        // Wednesday 2026-11-11: 1h REPORTED_UNTIMED work -> kept separate, not added into timed usedSeconds
        val recUntimed1h = (LedgerValidator.validateAndBuildRecord(
            source = "chat",
            rawTitle = "Trả lời email",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 60,
            startedAt = null,
            endedAt = null,
            existingRecords = listOf(recTue3h, recWed3h, recPlanned2h),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        val progressList = LedgerAnalyticsCalculator.computeBudgetProgress(
            budgets = listOf(budget10h),
            records = listOf(recTue3h, recWed3h, recPlanned2h, recUntimed1h),
            zoneId = testZone,
            nowEpochMs = nowEpochMs
        )
        assertEquals(1, progressList.size)
        val workProgress = progressList.single()

        assertEquals(36_000L, workProgress.targetSeconds)      // 10h target
        assertEquals(21_600L, workProgress.usedSeconds)        // 6h valid timed used
        assertEquals(14_400L, workProgress.remainingSeconds)   // 4h remaining
        assertEquals(0L, workProgress.exceededSeconds)
        assertEquals("Còn 4h", workProgress.differenceLabelVi)
        assertEquals(7_200L, workProgress.plannedSeconds)      // 2h PLANNED excluded
        assertEquals(3_600L, workProgress.untimedNoteSeconds)  // 1h untimed separated
    }

    @Test
    fun `refinement02 - week rollover respects device timezone and AI reflection uses real budgets without judging`() {
        // Acceptance Criterion: "Đổi tuần đúng múi giờ thiết bị."
        // Instant: 2026-11-15T20:30:00Z
        // - In UTC: Sunday 2026-11-15 20:30 -> still in week [2026-11-09 .. 2026-11-15]
        // - In Asia/Ho_Chi_Minh (UTC+7): Monday 2026-11-16 03:30 -> rolled over to NEW week [2026-11-16 .. 2026-11-22]!
        val boundaryInstantMs = Instant.parse("2026-11-15T20:30:00Z").toEpochMilli()
        val utcZone = ZoneId.of("UTC")
        val vnZone = ZoneId.of("Asia/Ho_Chi_Minh")

        val budget10h = CategoryTimeBudget(
            category = TimeCategory.LEARN,
            period = BudgetPeriod.WEEKLY,
            targetSeconds = 10 * 3600L
        )

        // 6h record on 2026-11-12 (Thursday of the 2026-11-09..2026-11-15 week)
        val thuRange = LedgerValidator.parseTimeRangeToEpochMillis("2026-11-12", "08:00", "14:00", vnZone)!!
        val thuRecord6h = (LedgerValidator.validateAndBuildRecord(
            source = "timer",
            rawTitle = "Nghiên cứu chuyên sâu",
            rawCategory = "learn",
            dateLocal = "2026-11-12",
            inputDurationMinutes = null,
            startedAt = thuRange.first,
            endedAt = thuRange.second,
            existingRecords = emptyList(),
            nowEpochMs = boundaryInstantMs,
            zoneId = vnZone
        ) as ValidationResult.Valid).record

        // 1. In UTC, it is still Sunday 2026-11-15 -> week is 2026-11-09..2026-11-15 -> 6h used, 4h remaining
        val utcProgress = LedgerAnalyticsCalculator.computeBudgetProgress(
            budgets = listOf(budget10h),
            records = listOf(thuRecord6h),
            zoneId = utcZone,
            nowEpochMs = boundaryInstantMs
        ).single()
        assertEquals("2026-11-09", utcProgress.periodStartDateLocal)
        assertEquals("2026-11-15", utcProgress.periodEndDateLocal)
        assertEquals(21_600L, utcProgress.usedSeconds)
        assertEquals(14_400L, utcProgress.remainingSeconds)
        assertEquals("Còn 4h", utcProgress.differenceLabelVi)

        // 2. In Asia/Ho_Chi_Minh (UTC+7), it is already Monday 2026-11-16 03:30 -> NEW week 2026-11-16..2026-11-22 -> 0h used, 10h remaining!
        val vnProgress = LedgerAnalyticsCalculator.computeBudgetProgress(
            budgets = listOf(budget10h),
            records = listOf(thuRecord6h),
            zoneId = vnZone,
            nowEpochMs = boundaryInstantMs
        ).single()
        assertEquals("2026-11-16", vnProgress.periodStartDateLocal)
        assertEquals("2026-11-22", vnProgress.periodEndDateLocal)
        assertEquals(0L, vnProgress.usedSeconds)
        assertEquals(36_000L, vnProgress.remainingSeconds)
        assertEquals("Còn 10h", vnProgress.differenceLabelVi)

        // 3. Neutral AI Reflection check (Yêu cầu 7): references real budget and never modifies targetSeconds
        val weeklyInUtc = LedgerAnalyticsCalculator.computeWeeklyInsights(
            endDateLocal = "2026-11-15",
            records = listOf(thuRecord6h),
            zoneId = utcZone,
            nowEpochMs = boundaryInstantMs,
            budgets = listOf(budget10h)
        )
        assertTrue(weeklyInUtc.reflectionPrompt.contains("Học tập (10h/tuần)"))
        assertTrue(weeklyInUtc.reflectionPrompt.contains("6h"))
        assertTrue(weeklyInUtc.reflectionPrompt.contains("còn 4h"))
        assertEquals(36_000L, budget10h.targetSeconds)
    }

    // ------------------- REFINEMENT 03: DAILY REVIEW & RUNTIME PERSISTENCE TESTS -------------------

    @Test
    fun `refinement03 - chained Room migration v1 to v2 to v3 preserves data and persists review notes across file database restart`() {
        val tempDbFile = File.createTempFile("timewallet_migration_v1_v2_v3_", ".db")
        try {
            val jdbcUrl = "jdbc:sqlite:${tempDbFile.absolutePath}"

            // Phase 1: Create v1 database on disk, populate v1 data, migrate v1 -> v2 -> v3, and write v2 + v3 records
            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `ledger_records` (
                            `id` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `layer` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `userConfirmedAt` INTEGER NOT NULL,
                            `category` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `durationSeconds` INTEGER NOT NULL,
                            `startedAt` INTEGER,
                            `endedAt` INTEGER,
                            `timezone` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `idempotencyKey` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    stmt.execute(
                        """
                        INSERT INTO `ledger_records` VALUES
                        ('v1-learn-1', 'timer', 'MEASURED', 1762850000000, 1762853600000, 'learn', 'Học thuật toán đồ thị', '2026-11-11', 3600, 1762850000000, 1762853600000, 'Asia/Ho_Chi_Minh', 'CONFIRMED', 'idem-v1-learn-1'),
                        ('v1-legacy-untimed', 'chat', 'REPORTED_UNPLACED', 1762854000000, 1762854000000, 'work', 'Viết tài liệu thiết kế', '2026-11-11', 1800, NULL, NULL, 'Asia/Ho_Chi_Minh', 'CONFIRMED', 'idem-v1-legacy-untimed')
                        """.trimIndent()
                    )
                }

                val supportDb = createSupportSQLiteDatabaseOverJdbc(conn)
                // Execute real Room migrations v1 -> v2 -> v3
                TimeWalletDatabase.MIGRATION_1_2.migrate(supportDb)
                TimeWalletDatabase.MIGRATION_2_3.migrate(supportDb)

                // Insert a v2 budget and v3 DailyReview + ActivityReviewNote
                val reviewEntity = DailyReviewEntity.fromDomain(
                    DailyReviewRecord(
                        dateLocal = "2026-11-11",
                        summaryText = "Ngày 2026-11-11: Đã ghi nhận có mốc giờ 1h; Thời lượng chưa gắn giờ 30p.",
                        question1 = "Hoạt động Học tập 'Học thuật toán đồ thị' đã mang lại hiểu biết cốt lõi nào hôm nay?",
                        question2 = "Khoảng 30p chưa gắn giờ diễn ra vào buổi nào?",
                        answer1 = "Hiểu rõ cách chứng minh định lý đường đi ngắn nhất Dijkstra.",
                        answer2 = "Diễn ra vào đầu giờ chiều.",
                        generalNote = "Tập trung rất tốt vào buổi sáng.",
                        updatedAt = nowEpochMs
                    )
                )
                conn.prepareStatement(
                    "INSERT INTO daily_reviews (dateLocal, summaryText, question1, question2, answer1, answer2, generalNote, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                ).use { ps ->
                    ps.setString(1, reviewEntity.dateLocal)
                    ps.setString(2, reviewEntity.summaryText)
                    ps.setString(3, reviewEntity.question1)
                    ps.setString(4, reviewEntity.question2)
                    ps.setString(5, reviewEntity.answer1)
                    ps.setString(6, reviewEntity.answer2)
                    ps.setString(7, reviewEntity.generalNote)
                    ps.setLong(8, reviewEntity.updatedAt)
                    ps.executeUpdate()
                }

                val noteEntity = ActivityReviewNoteEntity.fromDomain(
                    ActivityReviewNote(
                        recordId = "v1-learn-1",
                        dateLocal = "2026-11-11",
                        outcomeNote = "Hoàn thành cài đặt hàng đợi ưu tiên.",
                        feynmanExplanation = "Hãy tưởng tượng bạn lan tỏa nước từ điểm xuất phát qua các ống dẫn có độ dài khác nhau...",
                        updatedAt = nowEpochMs
                    )
                )
                conn.prepareStatement(
                    "INSERT INTO activity_review_notes (recordId, dateLocal, outcomeNote, feynmanExplanation, updatedAt) VALUES (?, ?, ?, ?, ?)"
                ).use { ps ->
                    ps.setString(1, noteEntity.recordId)
                    ps.setString(2, noteEntity.dateLocal)
                    ps.setString(3, noteEntity.outcomeNote)
                    ps.setString(4, noteEntity.feynmanExplanation)
                    ps.setLong(5, noteEntity.updatedAt)
                    ps.executeUpdate()
                }
            }

            // Phase 2: Reopen SQLite file from disk (simulating full app/process restart) and verify persistence
            DriverManager.getConnection(jdbcUrl).use { reopenedConn ->
                reopenedConn.createStatement().use { stmt ->
                    val rsLedger = stmt.executeQuery("SELECT id, layer, title FROM ledger_records ORDER BY id ASC")
                    assertTrue(rsLedger.next())
                    assertEquals("v1-learn-1", rsLedger.getString("id"))
                    assertEquals("MEASURED", rsLedger.getString("layer"))

                    assertTrue(rsLedger.next())
                    assertEquals("v1-legacy-untimed", rsLedger.getString("id"))
                    assertEquals("REPORTED_UNTIMED", rsLedger.getString("layer"))
                    assertFalse(rsLedger.next())
                }

                reopenedConn.createStatement().use { stmt ->
                    val rsReview = stmt.executeQuery("SELECT dateLocal, answer1, generalNote FROM daily_reviews WHERE dateLocal = '2026-11-11'")
                    assertTrue(rsReview.next())
                    assertEquals("Hiểu rõ cách chứng minh định lý đường đi ngắn nhất Dijkstra.", rsReview.getString("answer1"))
                    assertEquals("Tập trung rất tốt vào buổi sáng.", rsReview.getString("generalNote"))
                }

                reopenedConn.createStatement().use { stmt ->
                    val rsNote = stmt.executeQuery("SELECT recordId, outcomeNote, feynmanExplanation FROM activity_review_notes WHERE recordId = 'v1-learn-1'")
                    assertTrue(rsNote.next())
                    assertEquals("Hoàn thành cài đặt hàng đợi ưu tiên.", rsNote.getString("outcomeNote"))
                    assertTrue(rsNote.getString("feynmanExplanation").contains("lan tỏa nước"))
                }
            }
        } finally {
            tempDbFile.delete()
        }
    }

    @Test
    fun `refinement03 - timer survives screen lock backgrounding and process restart via TimerAndSettingsStore without UI lifecycle drift`() {
        val backingPrefs = InMemorySharedPreferences()
        val storeBeforeRestart = TimerAndSettingsStore(backingPrefs)

        val startWallMs = 1_762_848_000_000L // 15:00:00
        val startMonotonicMs = 500_000L
        val originalSession = ActiveTimerSession(
            title = "Đọc sách Hệ thống phân tán",
            categoryKey = TimeCategory.LEARN.key,
            startedAtEpochMs = startWallMs,
            startedElapsedRealtimeMs = startMonotonicMs,
            timezone = testZone.id,
            targetDurationSeconds = 2700L // 45m
        )

        // 1. User starts timer and locks screen / backgrounds app
        storeBeforeRestart.saveActiveTimer(originalSession)

        // 2. Process is killed by Android OS while in background; user reopens app 45 minutes (2,700,000 ms) later
        val storeAfterProcessRestart = TimerAndSettingsStore(backingPrefs)
        val restoredSession = storeAfterProcessRestart.loadActiveTimer()
        assertNotNull(restoredSession)
        assertEquals("Đọc sách Hệ thống phân tán", restoredSession!!.title)
        assertEquals(TimeCategory.LEARN.key, restoredSession.categoryKey)
        assertEquals(2700L, restoredSession.targetDurationSeconds)

        // Case A: Same device boot (monotonic clock advanced by 45m = 2,700,000ms), even if wall clock was tampered (+3h)
        val snapshotDuringSameBoot = restoredSession.computeElapsedSeconds(
            nowEpochMs = startWallMs + 3 * 3_600_000L, // wall clock shifted +3h
            nowElapsedRealtimeMs = startMonotonicMs + 2_700_000L // monotonic clock accurately says 45m
        )
        assertEquals(2700L, snapshotDuringSameBoot.elapsedSeconds)
        assertTrue(snapshotDuringSameBoot.clockSkewDetected)
        assertEquals(startWallMs + 2_700_000L, snapshotDuringSameBoot.effectiveEndEpochMs)

        // Case B: Process restart after monotonic reference reset (nowElapsedRealtimeMs < startedElapsedRealtimeMs), falls back cleanly to wall clock delta
        val snapshotAfterRebootOrReset = restoredSession.computeElapsedSeconds(
            nowEpochMs = startWallMs + 2_700_000L, // 45m later on wall clock
            nowElapsedRealtimeMs = 120_000L // monotonic clock reset below 500_000L
        )
        assertEquals(2700L, snapshotAfterRebootOrReset.elapsedSeconds)
        assertEquals(startWallMs + 2_700_000L, snapshotAfterRebootOrReset.effectiveEndEpochMs)

        // 3. Clearing timer removes persisted state across restarts
        storeAfterProcessRestart.saveActiveTimer(null)
        assertNull(TimerAndSettingsStore(backingPrefs).loadActiveTimer())
    }

    @Test
    fun `refinement03 - concurrent batch writes are serialized by LedgerConcurrencyGate and prevent race-condition overlap`() = runBlocking {
        val dao = InMemoryTransactionalLedgerDao()
        val slot1 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "09:00", "10:00", testZone)!!
        val slot2 = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "10:00", "11:00", testZone)!!

        // Launch 10 concurrent coroutines on Dispatchers.Default all trying to commit overlapping batches [09:00-10:00, 10:00-11:00]
        val outcomes = coroutineScope {
            (1..10).map { workerIdx ->
                async(Dispatchers.Default) {
                    val batch = listOf(
                        LedgerRecordEntity(
                            id = "race-$workerIdx-a",
                            source = "chat",
                            layer = "REPORTED_TIMED",
                            createdAt = nowEpochMs,
                            userConfirmedAt = nowEpochMs,
                            category = "work",
                            title = "Tác vụ song song #$workerIdx A",
                            date = testDateStr,
                            durationSeconds = 3600L,
                            startedAt = slot1.first,
                            endedAt = slot1.second,
                            timezone = testZone.id,
                            status = "CONFIRMED",
                            idempotencyKey = "idem-race-$workerIdx-a"
                        ),
                        LedgerRecordEntity(
                            id = "race-$workerIdx-b",
                            source = "chat",
                            layer = "REPORTED_TIMED",
                            createdAt = nowEpochMs,
                            userConfirmedAt = nowEpochMs,
                            category = "learn",
                            title = "Tác vụ song song #$workerIdx B",
                            date = testDateStr,
                            durationSeconds = 3600L,
                            startedAt = slot2.first,
                            endedAt = slot2.second,
                            timezone = testZone.id,
                            status = "CONFIRMED",
                            idempotencyKey = "idem-race-$workerIdx-b"
                        )
                    )
                    dao.commitBatchRecordsTransactionally(batch)
                }
            }.awaitAll()
        }

        val succeeded = outcomes.filter { !it.abortedAtomically && it.savedCount == 2 }
        val rejected = outcomes.filter { it.abortedAtomically && it.savedCount == 0 }

        // Exactly 1 batch must win the race; the other 9 concurrent batches must be rejected atomically!
        assertEquals(1, succeeded.size)
        assertEquals(9, rejected.size)

        val finalRows = dao.getAllRecordsSnapshot()
        assertEquals(2, finalRows.size)
    }

    @Test
    fun `refinement03 - DailyReviewAssistant reflects real ledger excludes PLANNED enforces max 2 questions includes Feynman prompt for LEARN and exports JSON`() {
        val workRange = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "08:00", "10:00", testZone)!!
        val learnRange = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "10:00", "11:00", testZone)!!
        val plannedRange = LedgerValidator.parseTimeRangeToEpochMillis(testDateStr, "20:00", "22:00", testZone)!!

        // 1. 2h MEASURED work (08:00-10:00)
        val recWork2h = (LedgerValidator.validateAndBuildRecord(
            id = "rec-work-2h",
            source = "timer",
            rawTitle = "Tối ưu hóa truy vấn Room",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = null,
            startedAt = workRange.first,
            endedAt = workRange.second,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        // 2. 1h REPORTED_TIMED learn (10:00-11:00)
        val recLearn1h = (LedgerValidator.validateAndBuildRecord(
            id = "rec-learn-1h",
            source = "manual",
            rawTitle = "Nghiên cứu cơ chế Coroutine Mutex",
            rawCategory = "learn",
            dateLocal = testDateStr,
            inputDurationMinutes = 60,
            startedAt = learnRange.first,
            endedAt = learnRange.second,
            existingRecords = listOf(recWork2h),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        // 3. 45m REPORTED_UNTIMED rest
        val recUntimed45m = (LedgerValidator.validateAndBuildRecord(
            id = "rec-rest-45m",
            source = "chat",
            rawTitle = "Nghe nhạc thư giãn",
            rawCategory = "rest",
            dateLocal = testDateStr,
            inputDurationMinutes = 45,
            startedAt = null,
            endedAt = null,
            existingRecords = listOf(recWork2h, recLearn1h),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        // 4. 2h PLANNED future work (20:00-22:00) -> must NOT be counted in actual used time or completedActivities
        val recPlanned2h = (LedgerValidator.validateAndBuildRecord(
            id = "rec-planned-2h",
            source = "manual",
            rawTitle = "Dự kiến chuẩn bị slide",
            rawCategory = "work",
            dateLocal = testDateStr,
            inputDurationMinutes = 120,
            startedAt = plannedRange.first,
            endedAt = plannedRange.second,
            existingRecords = listOf(recWork2h, recLearn1h, recUntimed45m),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record
        assertEquals(TimeLayer.PLANNED, recPlanned2h.layer)

        val workBudget = CategoryTimeBudget(
            category = TimeCategory.WORK,
            period = BudgetPeriod.DAILY,
            targetSeconds = 4 * 3600L // 4h/ngày
        )

        val learnNote = ActivityReviewNote(
            recordId = recLearn1h.id,
            dateLocal = testDateStr,
            outcomeNote = "Đã nắm vững cách tránh race condition trong suspend transaction.",
            feynmanExplanation = "Mutex giống như chiếc chìa khóa phòng họp duy nhất: ai giữ khóa mới được vào ghi sổ, người đến sau xếp hàng chờ."
        )

        val snapshot = DailyReviewAssistant.buildSnapshotForDate(
            dateLocal = testDateStr,
            records = listOf(recWork2h, recLearn1h, recUntimed45m, recPlanned2h),
            budgets = listOf(workBudget),
            savedReview = null,
            activityNotes = listOf(learnNote),
            zoneId = testZone,
            nowEpochMs = nowEpochMs
        )

        // Verify Requirement 2 & 3: Accurate Ledger reflection & PLANNED exclusion
        // At 18:00 (18h elapsed), 3h timed (2h work + 1h learn) -> 15h untracked past, 45m untimed, 2h planned
        assertEquals(3 * 3600L, snapshot.dayStats.timedSeconds)
        assertEquals(45 * 60L, snapshot.dayStats.untimedSeconds)
        assertEquals(15 * 3600L, snapshot.dayStats.untrackedPastSeconds)
        assertEquals(2 * 3600L, snapshot.dayStats.plannedSeconds)

        // Budget progress excludes PLANNED (2h used out of 4h daily target -> 2h remaining)
        assertEquals(1, snapshot.budgetProgressList.size)
        val workProg = snapshot.budgetProgressList.single()
        assertEquals(2 * 3600L, workProg.usedSeconds)
        assertEquals(2 * 3600L, workProg.remainingSeconds)

        // Completed activities include the 3 actual records and exclude PLANNED
        assertEquals(3, snapshot.completedActivities.size)
        assertFalse(snapshot.completedActivities.any { it.layer == TimeLayer.PLANNED })

        // Verify Requirement 5: AI factual summary & <= 2 reflection questions strictly from real Ledger
        assertTrue(snapshot.generatedSummary.contains("Đã ghi nhận 3h có mốc giờ"))
        assertTrue(snapshot.generatedSummary.contains("45p chưa gắn giờ (tách riêng)"))
        assertTrue(snapshot.generatedSummary.contains("15h chưa ghi nhận trong quá khứ"))
        assertTrue(snapshot.generatedSummary.contains("không cộng vào thực tế"))
        assertTrue(snapshot.generatedQuestions.size in 1..2)
        assertTrue(snapshot.effectiveQuestions.size <= 2)

        // Verify Requirement 6: Exact Feynman question for LEARN activity
        assertEquals(
            "Bạn sẽ giải thích điều vừa học cho người chưa biết như thế nào?",
            DailyReviewSnapshot.FEYNMAN_QUESTION_VI
        )
        assertEquals(1, snapshot.learningActivities.size)
        assertEquals(recLearn1h.id, snapshot.learningActivities.single().id)
        assertEquals(
            learnNote.feynmanExplanation,
            snapshot.activityNotesMap[recLearn1h.id]?.feynmanExplanation
        )

        // Verify Requirement 7: JSON export includes records (with outcomeNote & feynmanExplanation), budgets, and dailyReviews
        val savedReview = DailyReviewRecord(
            dateLocal = testDateStr,
            summaryText = snapshot.generatedSummary,
            question1 = snapshot.effectiveQuestions.getOrElse(0) { "" },
            question2 = snapshot.effectiveQuestions.getOrElse(1) { "" },
            answer1 = "Tối ưu hóa xong tầng DAO.",
            answer2 = "Chiều có khoảng nghỉ chưa ghi nhận.",
            generalNote = "Hoàn thành mục tiêu kỹ thuật.",
            updatedAt = nowEpochMs
        )
        val exportedJson = LedgerJsonExporter.exportToJsonString(
            records = listOf(recWork2h, recLearn1h, recUntimed45m, recPlanned2h),
            activeTimer = null,
            budgets = listOf(workBudget),
            dailyReviews = listOf(savedReview),
            activityNotes = listOf(learnNote)
        )
        assertTrue(exportedJson.contains("\"schemaVersion\": 1"))
        assertTrue(exportedJson.contains("\"budgets\": ["))
        assertTrue(exportedJson.contains("\"dailyReviews\": ["))
        assertTrue(exportedJson.contains("\"feynmanPrompt\": \"Bạn sẽ giải thích điều vừa học cho người chưa biết như thế nào?\""))
        assertTrue(exportedJson.contains("Mutex giống như chiếc chìa khóa phòng họp duy nhất"))
        assertTrue(exportedJson.contains("Tối ưu hóa xong tầng DAO."))
    }

    // ------------------- PATCH v1.0.1: SECURITY & DATA INTEGRITY TESTS (F1–F5) -------------------

    @Test
    fun `patch_v1_0_1_F1_and_F2 - AES_GCM vault encrypts API key migrates legacy plaintext enforces HTTPS and blocks sending token to unconfirmed host`() = runBlocking {
        val prefs = InMemorySharedPreferences()
        val vault = InMemoryApiKeyVault()
        val store = TimerAndSettingsStore(prefs, vault)

        // 1. Simulate legacy v1.0.0 plaintext API key stored in SharedPreferences
        val syntheticToken = "sk-synthetic-mock-token-for-unit-test-only"
        prefs.edit()
            .putBoolean("ai_opt_in_enabled", true)
            .putString(TimerAndSettingsStore.KEY_AI_BASE_URL, "https://api.foza.ai/v1")
            .putString(TimerAndSettingsStore.KEY_AI_API_KEY_LEGACY, syntheticToken)
            .commit()

        // 2. Loading preferences must migrate plaintext -> AES-GCM ciphertext and delete the plaintext key
        val migratedPrefs = store.loadAiPreferences()
        assertEquals(syntheticToken, migratedPrefs.apiKey)
        assertEquals("api.foza.ai", migratedPrefs.trustedHost)
        assertNull(prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_LEGACY, null))
        val storedCiphertext = prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, null)
        assertNotNull(storedCiphertext)
        assertTrue(storedCiphertext!!.startsWith(AesGcmKeystoreVault.PAYLOAD_PREFIX))
        assertFalse(storedCiphertext.contains(syntheticToken))

        // Tampering with ciphertext must fail GCM authentication tag check and return null
        val tamperedCiphertext = storedCiphertext.dropLast(2) + "AA"
        assertNull(vault.decrypt(tamperedCiphertext))

        // 3. F2: Enforce HTTPS only
        assertNull(AiPreferences.extractValidatedHttpsHost("http://api.foza.ai/v1"))
        assertNull(AiPreferences.extractValidatedHttpsHost("ftp://api.foza.ai/v1"))
        assertNull(AiPreferences.extractValidatedHttpsHost("https://user:pass@api.foza.ai/v1"))
        assertEquals("api.foza.ai", AiPreferences.extractValidatedHttpsHost("https://api.foza.ai/v1"))

        val insecureOutcome = GeminiProposalClient.analyzeInput(
            userText = "Đọc sách 30 phút",
            today = testDate,
            nowTime = LocalTime.of(14, 0),
            optInEnabled = true,
            baseUrl = "http://api.foza.ai/v1",
            apiKey = syntheticToken,
            trustedHost = "api.foza.ai"
        )
        // WALLY API-FIRST (thay đổi có chủ đích): không còn phân tích cục bộ thay thế. Thuộc tính bảo mật F2 vẫn giữ:
        // URL không https => KHÔNG kết nối (không có connectionFactory nào được gọi) và báo lỗi cấu hình, không có đề xuất.
        assertEquals(com.timewallet.mvp.domain.usecase.AiFault.CONFIG, insecureOutcome.fault)
        assertTrue(insecureOutcome.proposals.isEmpty())
        assertTrue(insecureOutcome.warnings.any { it.contains("https") })

        // 4. F2: Prevent sending credential to a new unconfirmed host
        val mismatchedHostOutcome = GeminiProposalClient.analyzeInput(
            userText = "Đọc sách 30 phút",
            today = testDate,
            nowTime = LocalTime.of(14, 0),
            optInEnabled = true,
            baseUrl = "https://untrusted-proxy.example.com/v1",
            apiKey = syntheticToken,
            trustedHost = "api.foza.ai"
        )
        // WALLY API-FIRST (thay đổi có chủ đích): host lạ => không gửi khóa, lỗi cấu hình, không phân tích cục bộ thay thế.
        assertEquals(com.timewallet.mvp.domain.usecase.AiFault.CONFIG, mismatchedHostOutcome.fault)
        assertTrue(mismatchedHostOutcome.proposals.isEmpty())
        assertTrue(mismatchedHostOutcome.warnings.any { it.contains("khớp host của khóa") })

        // Also verify store.loadAiPreferences() withholds the key if KEY_AI_BASE_URL was changed to another host without updating KEY_AI_TRUSTED_HOST
        prefs.edit().putString(TimerAndSettingsStore.KEY_AI_BASE_URL, "https://another-host.example.org/v1").commit()
        val afterTamperedUrl = store.loadAiPreferences()
        assertEquals("", afterTamperedUrl.apiKey)
        assertFalse(afterTamperedUrl.optInEnabled)
    }

    @Test
    fun `patch_v1_0_1_F3 - timer reboot with new uptime greater than start uptime does NOT undercount while same-boot clock skew and legacy sessions work`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, InMemoryApiKeyVault())

        val startWallMs = 1_762_848_000_000L
        // Timer started 1 minute after boot #10 (uptime = 60,000 ms)
        val sessionBoot10 = ActiveTimerSession(
            title = "Phiên làm việc qua lúc reboot",
            categoryKey = TimeCategory.WORK.key,
            startedAtEpochMs = startWallMs,
            startedElapsedRealtimeMs = 60_000L,
            timezone = testZone.id,
            targetDurationSeconds = 1800L,
            startedBootCount = 10
        )
        store.saveActiveTimer(sessionBoot10)
        val restored = store.loadActiveTimer()
        assertNotNull(restored)
        assertEquals(10, restored!!.startedBootCount)

        // Scenario A (Review Issue #3): Device reboots (currentBootCount = 11).
        // 30 minutes (1800s) have elapsed on the wall clock, and the phone's new uptime since reboot is 10 minutes (600,000 ms > 60,000 ms).
        // Before fix: monotonicDeltaMs = 540,000ms (9m), skew check tripped, and timer undercounted 9m instead of 30m!
        // With fix: bootCount mismatch (10 != 11) forces wall-clock fallback -> exact 1800s (30m) and clockSkewDetected = false.
        val afterRebootSnap = restored.computeElapsedSeconds(
            nowEpochMs = startWallMs + 30 * 60_000L,
            nowElapsedRealtimeMs = 600_000L,
            currentBootCount = 11
        )
        assertEquals(1800L, afterRebootSnap.elapsedSeconds)
        assertFalse(afterRebootSnap.clockSkewDetected)
        assertTrue(afterRebootSnap.rebootFallbackUsed)
        assertEquals(startWallMs + 30 * 60_000L, afterRebootSnap.effectiveEndEpochMs)

        // Scenario B: Same boot (currentBootCount = 10), user manually shifts wall clock +2 hours while 10m (600s) actually elapsed on monotonic clock.
        val sameBootSkewSnap = restored.computeElapsedSeconds(
            nowEpochMs = startWallMs + 2 * 3_600_000L,
            nowElapsedRealtimeMs = 60_000L + 600_000L,
            currentBootCount = 10
        )
        assertEquals(600L, sameBootSkewSnap.elapsedSeconds)
        assertTrue(sameBootSkewSnap.clockSkewDetected)
        assertFalse(sameBootSkewSnap.rebootFallbackUsed)

        // Scenario C: Legacy session upgraded from v1.0.0 without startedBootCount (startedBootCount = null, currentBootCount = 11)
        val legacySession = restored.copy(startedBootCount = null)
        val legacyRebootSnap = legacySession.computeElapsedSeconds(
            nowEpochMs = startWallMs + 30 * 60_000L,
            nowElapsedRealtimeMs = 600_000L,
            currentBootCount = 11
        )
        assertEquals(1800L, legacyRebootSnap.elapsedSeconds)
        assertFalse(legacyRebootSnap.clockSkewDetected)

        // Scenario D: Legacy session where wall clock was rolled backward before start (wallDeltaMs < 0)
        val legacyNegativeWallSnap = legacySession.computeElapsedSeconds(
            nowEpochMs = startWallMs - 300_000L,
            nowElapsedRealtimeMs = 60_000L + 420_000L, // 7 minutes monotonic
            currentBootCount = 11
        )
        assertEquals(420L, legacyNegativeWallSnap.elapsedSeconds)
        assertTrue(legacyNegativeWallSnap.clockSkewDetected)
    }

    @Test
    fun `patch_v1_0_1_F4 - JSON exporter serializes all control characters U0000 to U001F and Vietnamese Unicode with valid round-trip JSONObject parse`() {
        val allControlChars = (0x00..0x1F).map { it.toChar() }.joinToString("")
        val complexVietnameseWithControls =
            "Tổng kết tiếng Việt: Đọc \"Deep Work\" \\ Chương 1 $allControlChars Kết thúc."

        val rec = (LedgerValidator.validateAndBuildRecord(
            id = "rec-ctrl-test",
            source = "chat",
            rawTitle = complexVietnameseWithControls,
            rawCategory = "learn",
            dateLocal = testDateStr,
            inputDurationMinutes = 45,
            startedAt = null,
            endedAt = null,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        ) as ValidationResult.Valid).record

        val note = ActivityReviewNote(
            recordId = rec.id,
            dateLocal = testDateStr,
            outcomeNote = "Ghi chú kết quả có control chars: $allControlChars",
            feynmanExplanation = "Giải thích Feynman tiếng Việt: $allControlChars"
        )
        val review = DailyReviewRecord(
            dateLocal = testDateStr,
            summaryText = "Tóm tắt: $allControlChars",
            question1 = "Câu hỏi 1?",
            question2 = "Câu hỏi 2?",
            answer1 = "Trả lời 1: $allControlChars",
            answer2 = "Trả lời 2: Tiếng Việt chuẩn",
            generalNote = "Ghi chú chung: $allControlChars",
            updatedAt = nowEpochMs
        )

        val exportedJson = LedgerJsonExporter.exportToJsonString(
            records = listOf(rec),
            activeTimer = null,
            budgets = emptyList(),
            dailyReviews = listOf(review),
            activityNotes = listOf(note)
        )

        // Round-trip parse with standard org.json.JSONObject must succeed without JSONException
        val parsedRoot = JSONObject(exportedJson)
        assertEquals(1, parsedRoot.getInt("schemaVersion"))

        val parsedItem = parsedRoot.getJSONArray("items").getJSONObject(0)
        assertEquals(complexVietnameseWithControls.trim(), parsedItem.getString("title"))
        assertEquals(note.outcomeNote, parsedItem.getString("outcomeNote"))
        assertEquals(note.feynmanExplanation, parsedItem.getString("feynmanExplanation"))

        val parsedReview = parsedRoot.getJSONArray("dailyReviews").getJSONObject(0)
        assertEquals(review.answer1, parsedReview.getString("answer1"))
        assertEquals(review.generalNote, parsedReview.getString("generalNote"))
    }

    @Test
    fun `patch_v1_0_1_F5 - GeminiProposalClient and LedgerValidator reject explicit_range duration mismatch and sanitize fallback errors`() {
        // 1. AI returns explicit_range 08:00-09:00 (60m = 3600s), but claims durationSeconds = 7200 (120m) -> must be rejected!
        val mismatchedAiResponse = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"proposals\":[{\"title\":\"Làm việc lệch giờ\",\"category\":\"work\",\"durationSeconds\":7200,\"dateLocal\":\"2026-11-11\",\"startLocal\":\"08:00\",\"endLocal\":\"09:00\",\"timeEvidence\":\"explicit_range\",\"missingFields\":[],\"needsConfirmation\":true},{\"title\":\"Học hợp lệ\",\"category\":\"learn\",\"durationSeconds\":3600,\"dateLocal\":\"2026-11-11\",\"startLocal\":\"10:00\",\"endLocal\":\"11:00\",\"timeEvidence\":\"explicit_range\",\"missingFields\":[],\"needsConfirmation\":true}]}"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsedOutcome = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = mismatchedAiResponse,
            userText = "Làm việc và học tập",
            today = testDate,
            nowTime = LocalTime.of(14, 0)
        )
        assertEquals(1, parsedOutcome.proposals.size)
        assertEquals("Học hợp lệ", parsedOutcome.proposals.single().title)
        assertEquals(3600L, parsedOutcome.proposals.single().durationSeconds)
        assertTrue(parsedOutcome.warnings.any { it.contains("không khớp với khoảng giờ 08:00–09:00") })

        // 2. LedgerValidator.validateBatchAtomic must also reject a batch item whose durationMinutes (120m) mismatches startHm..endHm (08:00..09:00 = 60m)
        val batchWithMismatch = listOf(
            BatchRecordInput(
                rawTitle = "Mục lệch thời lượng và mốc giờ",
                rawCategory = "work",
                dateLocal = testDateStr,
                durationMinutes = 120,
                startHm = "08:00",
                endHm = "09:00"
            )
        )
        val batchCheck = LedgerValidator.validateBatchAtomic(
            inputs = batchWithMismatch,
            existingRecords = emptyList(),
            nowEpochMs = nowEpochMs,
            zoneId = testZone
        )
        assertTrue(batchCheck is BatchValidationResult.Invalid)
        assertTrue((batchCheck as BatchValidationResult.Invalid).message.contains("không khớp"))
    }

    // --- Helper for testing Room Migration & DAO Transaction in JVM Unit Tests ---

    private fun createSupportSQLiteDatabaseOverJdbc(conn: Connection): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    val sql = args[0] as String
                    conn.createStatement().use { it.execute(sql) }
                    null
                }
                "isOpen" -> !conn.isClosed
                "close" -> {
                    conn.close()
                    null
                }
                else -> throw UnsupportedOperationException("Method ${method.name} not mocked")
            }
        } as SupportSQLiteDatabase
    }

    private class InMemoryTransactionalLedgerDao : LedgerDao {
        private val rows = mutableListOf<LedgerRecordEntity>()

        override fun observeAllRecords(): Flow<List<LedgerRecordEntity>> = emptyFlow()

        override suspend fun getAllRecordsSnapshot(): List<LedgerRecordEntity> = rows.toList()

        override suspend fun countByIdempotencyKey(idempotencyKey: String): Int =
            rows.count { it.idempotencyKey == idempotencyKey }

        override suspend fun findOverlappingEntity(
            startMs: Long,
            endMs: Long,
            excludeId: String
        ): LedgerRecordEntity? {
            return rows.firstOrNull { r ->
                (r.layer == "MEASURED" || r.layer == "REPORTED_TIMED" || r.layer == "ACTIVE") &&
                    r.status != "PLANNED" &&
                    r.startedAt != null &&
                    r.endedAt != null &&
                    r.id != excludeId &&
                    startMs < r.endedAt &&
                    endMs > r.startedAt
            }
        }

        override suspend fun upsertInternal(entity: LedgerRecordEntity) {
            rows.removeAll { it.id == entity.id }
            rows.add(entity)
        }

        override suspend fun deleteById(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val staged = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { staged[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { staged[key] = values }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { staged[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { staged[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { staged[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { staged[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            override fun commit(): Boolean {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                staged.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
