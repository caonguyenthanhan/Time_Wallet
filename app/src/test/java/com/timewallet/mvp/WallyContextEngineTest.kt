package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitFrequency
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AdaptiveContextRouter
import com.timewallet.mvp.domain.usecase.ContextRouteMode
import com.timewallet.mvp.domain.usecase.ConversationState
import com.timewallet.mvp.domain.usecase.DayTimeWindow
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.WallyContextEngine
import com.timewallet.mvp.domain.usecase.WallyTurnPolicy
import com.timewallet.mvp.domain.usecase.fingerprint
import com.timewallet.mvp.ui.WallyIntelligenceCoordinator
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class WallyContextEngineTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 22)
    private val nowTime = LocalTime.of(18, 0)
    private val nowMs = LocalDateTime.of(today, nowTime).atZone(zone).toInstant().toEpochMilli()

    private fun ms(date: LocalDate, h: Int, m: Int): Long =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val learnTag = Tag(
        id = "tag-ai",
        name = "Học AI",
        nameKey = "hoc ai",
        category = TimeCategory.LEARN,
        archivedAt = null,
        createdAt = nowMs - 86_400_000L,
        updatedAt = nowMs - 86_400_000L
    )

    private val aiGoal = Goal(
        id = "goal-ai",
        groupId = "grp-ai",
        targetType = GoalTargetType.TAG,
        targetKey = "tag-ai",
        kind = GoalKind.MIN,
        period = GoalPeriod.DAY,
        targetSeconds = 90 * 60L,
        status = GoalVersionStatus.ACTIVE,
        validFrom = today.minusDays(2).toString(),
        validTo = null,
        createdAt = nowMs - 86_400_000L,
        updatedAt = nowMs - 86_400_000L
    )

    private val records = listOf(
        LedgerRecord(
            id = "rec-ai-30m",
            source = "timer",
            layer = TimeLayer.MEASURED,
            createdAt = ms(today, 9, 0),
            userConfirmedAt = ms(today, 9, 30),
            category = TimeCategory.LEARN,
            title = "Học AI buổi sáng",
            date = today.toString(),
            durationSeconds = 30 * 60L,
            startedAt = ms(today, 9, 0),
            endedAt = ms(today, 9, 30),
            timezone = zone.id,
            status = ConfirmationState.COMPLETED.name,
            idempotencyKey = "k1",
            confirmationStateRaw = ConfirmationState.COMPLETED.name,
            tagId = "tag-ai"
        ),
        LedgerRecord(
            id = "rec-evening-busy",
            source = "manual",
            layer = TimeLayer.PLANNED,
            createdAt = ms(today, 12, 0),
            userConfirmedAt = ms(today, 12, 0),
            category = TimeCategory.WORK,
            title = "Họp nhóm buổi tối SECRET_MEETING",
            date = today.toString(),
            durationSeconds = 60 * 60L,
            startedAt = ms(today, 19, 0),
            endedAt = ms(today, 20, 0),
            timezone = zone.id,
            status = ConfirmationState.PLANNED.name,
            idempotencyKey = "k2",
            confirmationStateRaw = ConfirmationState.PLANNED.name
        )
    )

    private val tasks = listOf(
        TaskItem(
            id = "task-1",
            title = "Hoàn thiện tài liệu SECRET_TASK",
            category = TimeCategory.LEARN,
            priority = TaskPriority.P1,
            estimatedMinutes = 45,
            status = TaskStatus.TODO
        )
    )

    private val habits = listOf(
        Habit(
            id = "hab-1",
            title = "Đọc sách tối SECRET_HABIT",
            category = TimeCategory.LEARN,
            frequency = HabitFrequency.DAILY,
            minSessionMinutes = 20,
            createdAt = nowMs,
            updatedAt = nowMs
        )
    )

    @Test
    fun `AdaptiveContextRouter follows Method A across benchmark inputs`() {
        val allOn = AiReadPermissions()

        val rLog = AdaptiveContextRouter.decide("Đọc sách 30 phút", null, allOn, today, nowTime, nowMs)
        assertEquals(ContextRouteMode.NONE, rLog.mode)

        val rMetric = AdaptiveContextRouter.decide("Hôm nay học bao nhiêu?", null, allOn, today, nowTime, nowMs)
        assertEquals(ContextRouteMode.NONE, rMetric.mode)

        val rCancel = AdaptiveContextRouter.decide("Hủy tất cả đề xuất", null, allOn, today, nowTime, nowMs)
        assertEquals(ContextRouteMode.NONE, rCancel.mode)

        val rAdvice = AdaptiveContextRouter.decide("Tối nay nên học gì?", null, allOn, today, nowTime, nowMs)
        assertEquals(ContextRouteMode.ADAPTIVE, rAdvice.mode)
        assertEquals(today, rAdvice.targetDate)
        assertEquals(DayTimeWindow.EVENING, rAdvice.dayWindow)
        assertEquals(TimeCategory.LEARN, rAdvice.focusCategory)
        assertTrue(rAdvice.includeGoals && rAdvice.includeScheduleGaps)

        // Follow-up WITHOUT prior advisory conversation stays NONE
        val rFollowNoConv = AdaptiveContextRouter.decide("Vậy tối mai thì sao?", null, allOn, today, nowTime, nowMs)
        assertEquals(ContextRouteMode.NONE, rFollowNoConv.mode)

        // Follow-up WITH active advisory ConversationState becomes ADAPTIVE targeting tomorrow evening
        val activeConv = ConversationState(
            isAdvisoryTopic = true,
            topicLabel = "Học AI",
            focusCategory = TimeCategory.LEARN,
            targetDate = today,
            dayWindow = DayTimeWindow.EVENING,
            updatedAtEpochMs = nowMs - 60_000L,
            permissionsFingerprint = allOn.fingerprint()
        )
        val rFollowActive = AdaptiveContextRouter.decide("Vậy tối mai thì sao?", activeConv, allOn, today, nowTime, nowMs)
        assertEquals(ContextRouteMode.ADAPTIVE, rFollowActive.mode)
        assertEquals(today.plusDays(1), rFollowActive.targetDate)
        assertEquals(DayTimeWindow.EVENING, rFollowActive.dayWindow)
        assertEquals(TimeCategory.LEARN, rFollowActive.focusCategory)
        assertTrue(rFollowActive.includeContinuity)

        // Metric-only follow-up ("Còn tuần trước thì sao?") never enters heavy advisory context
        val metricOnlyConv = ConversationState(
            isAdvisoryTopic = false,
            topicLabel = "Học tập",
            focusCategory = TimeCategory.LEARN,
            updatedAtEpochMs = nowMs - 60_000L,
            permissionsFingerprint = allOn.fingerprint()
        )
        val rMetricFollow = AdaptiveContextRouter.decide("Còn tuần trước thì sao?", metricOnlyConv, allOn, today, nowTime, nowMs)
        assertEquals(ContextRouteMode.NONE, rMetricFollow.mode)
    }

    @Test
    fun `WallyContextEngine enforces AiReadPermissions zero-leak and TTL invalidation`() {
        val route = ContextRouteDecisionBuilder.eveningLearn(today)
        val allOff = AiReadPermissions(readLedger = false, readTasks = false, readHabits = false, readGoals = false)
        assertEquals(
            ContextRouteMode.NONE,
            AdaptiveContextRouter.decide("Tối nay nên học gì?", null, allOff, today, nowTime, nowMs).mode
        )

        val noLedgerOrGoals = AiReadPermissions(readLedger = false, readTasks = true, readHabits = false, readGoals = false)
        val ctx = WallyContextEngine.buildAdaptiveChatContext(
            route = route.copy(includeGoals = false, includeScheduleGaps = true, includeTasks = true, includeHabits = true),
            perms = noLedgerOrGoals,
            records = records,
            tags = listOf(learnTag),
            goals = listOf(aiGoal),
            tasks = tasks,
            habits = habits,
            habitLogs = emptyList(),
            activeTimer = null,
            conversationState = ConversationState(
                isAdvisoryTopic = true,
                lastCoachSummary = "Coach secret",
                updatedAtEpochMs = nowMs - 10_000L,
                permissionsFingerprint = AiReadPermissions().fingerprint() // mismatched!
            ),
            today = today,
            nowEpochMs = nowMs,
            zone = zone
        )
        assertTrue(ctx.goalDeficits.isEmpty())
        assertTrue(ctx.feasibleSlots.isEmpty())
        assertFalse(ctx.formattedBlock.contains("SECRET_MEETING"))
        assertFalse(ctx.formattedBlock.contains("SECRET_HABIT"))
        assertFalse(ctx.formattedBlock.contains("Coach secret"))
        assertTrue(ctx.formattedBlock.contains("SECRET_TASK"))

        val expired = ConversationState(
            isAdvisoryTopic = true,
            topicLabel = "Học AI",
            updatedAtEpochMs = nowMs - ConversationState.DEFAULT_TTL_MS - 1_000L,
            permissionsFingerprint = AiReadPermissions().fingerprint()
        )
        assertFalse(expired.isValidFor(nowMs, AiReadPermissions()))
    }

    @Test
    fun `Decision engine computes goal deficit, non-overlapping slots, and enforces size cap`() {
        val perms = AiReadPermissions()
        val route = AdaptiveContextRouter.decide("Tối nay nên học gì?", null, perms, today, nowTime, nowMs)
        val manyGoals = (1..12).map { idx ->
            aiGoal.copy(id = "g-$idx", groupId = "grp-$idx")
        }
        val ctx = WallyContextEngine.buildAdaptiveChatContext(
            route = route,
            perms = perms,
            records = records,
            tags = listOf(learnTag),
            goals = manyGoals,
            tasks = tasks,
            habits = habits,
            habitLogs = emptyList(),
            activeTimer = null,
            conversationState = null,
            today = today,
            nowEpochMs = nowMs,
            zone = zone
        )
        assertTrue(ctx.formattedBlock.length <= WallyContextEngine.MAX_CONTEXT_CHARS)
        val deficit = ctx.goalDeficits.first()
        assertEquals(90, deficit.targetMinutes)
        assertEquals(30, deficit.usedMinutes)
        assertEquals(60, deficit.remainingMinutes)

        // 19:00-20:00 is blocked by rec-evening-busy; feasible evening slots should cover 18:00-19:00 and 20:00-00:00
        assertTrue(ctx.feasibleSlots.any { it.startHm == "18:00" && it.endHm == "19:00" })
        assertTrue(ctx.feasibleSlots.any { it.startHm == "20:00" && it.endHm == "00:00" })
        assertFalse(ctx.feasibleSlots.any { it.startMinute < 20 * 60 && it.endMinute > 19 * 60 })

        // Verify conflict checker flags overlapping candidate
        val conflictProp = com.timewallet.mvp.domain.model.ActivityProposal(
            id = "p-overlap",
            rawInput = "Tối nay",
            title = "Học AI",
            category = TimeCategory.LEARN.key,
            durationSeconds = 3600L,
            dateLocal = today.toString(),
            startLocal = "19:30",
            endLocal = "20:30",
            timeEvidence = "explicit_range",
            missingFields = emptyList(),
            needsConfirmation = true,
            isFuturePlanned = true
        )
        val warnings = WallyContextEngine.detectScheduleConflictWarnings(
            proposals = listOf(conflictProp),
            perms = perms,
            records = records,
            activeTimer = null,
            nowEpochMs = nowMs,
            zone = zone
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("SECRET_MEETING"))
    }

    @Test
    fun `GeminiProposalClient keeps plain log payload minimal and grounds adaptive reply and proposal`() = runBlocking {
        val capturedPayloads = mutableListOf<JSONObject>()
        fun fakeOpener(replyJson: String): (URL) -> HttpURLConnection = { url ->
            object : HttpURLConnection(url) {
                private val out = ByteArrayOutputStream()
                override fun disconnect() {}
                override fun usingProxy(): Boolean = false
                override fun connect() {}
                override fun getOutputStream() = out
                override fun getResponseCode(): Int {
                    capturedPayloads += JSONObject(out.toString(Charsets.UTF_8.name()))
                    return 200
                }
                override fun getInputStream(): ByteArrayInputStream {
                    val body = JSONObject().put(
                        "choices",
                        JSONArray().put(
                            JSONObject().put("message", JSONObject().put("content", replyJson))
                        )
                    ).toString()
                    return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                }
            }
        }

        // 1) Plain activity log -> NO adaptive block sent
        val plainOut = GeminiProposalClient.analyzeInput(
            userText = "Đọc sách 30 phút",
            today = today,
            nowTime = nowTime,
            timezoneId = zone.id,
            optInEnabled = true,
            baseUrl = AiPreferences.DEFAULT_AI_BASE_URL,
            apiKey = "sk-test-1234",
            connectionFactory = fakeOpener("""{"reply":"Mình tách 1 hoạt động.","proposals":[{"title":"Đọc sách","category":"learn","durationSeconds":1800,"dateLocal":"$today","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","missingFields":["startLocal","endLocal"],"needsConfirmation":true}],"queries":[]}"""),
            adaptiveContext = null
        )
        val plainUserMsg = capturedPayloads[0].getJSONArray("messages").getJSONObject(1).getString("content")
        assertFalse(plainUserMsg.contains("[NGỮ CẢNH THÔNG MINH WALLY]"))
        assertFalse(plainOut.usedAdaptiveContext)
        assertNull("Standard RECORD turn still hides freeform reply before commit", plainOut.reply)

        // 2) Adaptive advisory reply without proposal -> grounded reply (60 phút) survives ReplyGuard & builds advisory Card;
        //    Adaptive proposal turn STILL respects V108 (reply hidden whenever proposals are present; unbacked 420p stripped!)
        val route = AdaptiveContextRouter.decide("Tối nay nên học gì?", null, AiReadPermissions(), today, nowTime, nowMs)
        val adaptiveCtx = WallyContextEngine.buildAdaptiveChatContext(
            route = route,
            perms = AiReadPermissions(),
            records = records,
            tags = listOf(learnTag),
            goals = listOf(aiGoal),
            tasks = tasks,
            habits = habits,
            habitLogs = emptyList(),
            activeTimer = null,
            conversationState = null,
            today = today,
            nowEpochMs = nowMs,
            zone = zone
        )
        val advisoryJson = """{"reply":"Mục tiêu Học AI hôm nay còn thiếu 60 phút. Bạn rảnh lúc 20:00-00:00, có thể học khung này nhé.","proposals":[],"queries":[]}"""
        val advisoryOut = GeminiProposalClient.analyzeInput(
            userText = "Tối nay nên học gì?",
            today = today,
            nowTime = nowTime,
            timezoneId = zone.id,
            optInEnabled = true,
            baseUrl = AiPreferences.DEFAULT_AI_BASE_URL,
            apiKey = "sk-test-1234",
            connectionFactory = fakeOpener(advisoryJson),
            adaptiveContext = adaptiveCtx
        )
        val adaptiveUserMsg = capturedPayloads[1].getJSONArray("messages").getJSONObject(1).getString("content")
        assertTrue(adaptiveUserMsg.contains("[NGỮ CẢNH THÔNG MINH WALLY]"))
        assertEquals(GeminiProposalClient.staticSystemPrompt, capturedPayloads[1].getJSONArray("messages").getJSONObject(0).getString("content"))
        assertTrue(advisoryOut.usedAdaptiveContext)
        assertNotNull(advisoryOut.reply)
        val card = WallyTurnPolicy.buildCard("Tối nay nên học gì?", advisoryOut, emptyList(), pendingDrafts = 0, allowAdvisoryCard = true)
        assertNotNull(card)
        assertTrue(card!!.headline.contains("60 phút"))

        val plannedJson = """{"reply":"Đã thêm lịch học nhé.","proposals":[{"title":"Học AI","category":"learn","durationSeconds":3600,"dateLocal":"$today","startLocal":"20:00","endLocal":"21:00","timeEvidence":"explicit_range","missingFields":[],"needsConfirmation":true}],"queries":[]}"""
        val plannedOut = GeminiProposalClient.analyzeInput(
            userText = "Tối nay nên học gì?",
            today = today,
            nowTime = nowTime,
            timezoneId = zone.id,
            optInEnabled = true,
            baseUrl = AiPreferences.DEFAULT_AI_BASE_URL,
            apiKey = "sk-test-1234",
            connectionFactory = fakeOpener(plannedJson),
            adaptiveContext = adaptiveCtx
        )
        assertEquals(1, plannedOut.proposals.size)
        assertTrue(plannedOut.proposals.first().needsConfirmation)
        assertNull("V108 invariant: never expose free text alongside pending proposal", plannedOut.reply)

        // Hallucinated number (420 phút) is STILL stripped even with adaptiveCtx!
        val hallucinated = ReplyGuard.sanitize(
            reply = "Bạn thiếu 420 phút hôm nay.",
            userText = "Tối nay nên học gì?",
            proposals = emptyList(),
            hasQueries = false,
            nowTime = nowTime,
            contextBackedMinutes = adaptiveCtx.backedMinutes
        )
        assertNull(hallucinated.first)
    }

    @Test
    fun `WallyIntelligenceCoordinator links Coach summary to Chat and invalidates on permission revocation`() {
        val coord = WallyIntelligenceCoordinator(nowEpochProvider = { nowMs })
        val allOn = AiReadPermissions()
        coord.recordVerifiedCoachSummary("Học AI đã đạt 30 phút, còn thiếu 60 phút hôm nay.", allOn, nowMs)
        assertNotNull(coord.currentConversationSnapshot(allOn)?.lastCoachSummary)

        val revoked = allOn.copy(readGoals = false)
        coord.onPermissionsChanged(revoked)
        assertNull(coord.currentConversationSnapshot(revoked))
        assertNull(coord.currentConversationSnapshot(allOn))
    }

    @Test
    fun `Insights 3-layer breakdown conserves 24h buckets, separates untimed vs unrecorded, and explains 0p tag gaps honestly`() {
        val yesterday = today.minusDays(1)
        val lastWeekDay = today.minusDays(8)
        val threeLayerRecords = listOf(
            // Previous week LEARN 60m + WORK 60m
            LedgerRecord(
                id = "prev-learn",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = ms(lastWeekDay, 10, 0),
                userConfirmedAt = ms(lastWeekDay, 10, 0),
                category = TimeCategory.LEARN,
                title = "Học tuần trước",
                date = lastWeekDay.toString(),
                durationSeconds = 3600L,
                startedAt = ms(lastWeekDay, 9, 0),
                endedAt = ms(lastWeekDay, 10, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "pl1"
            ),
            LedgerRecord(
                id = "prev-work",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = ms(lastWeekDay, 15, 0),
                userConfirmedAt = ms(lastWeekDay, 15, 0),
                category = TimeCategory.WORK,
                title = "Làm việc tuần trước",
                date = lastWeekDay.toString(),
                durationSeconds = 3600L,
                startedAt = ms(lastWeekDay, 14, 0),
                endedAt = ms(lastWeekDay, 15, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "pw1"
            ),
            // Current week: SLEEP 7h + WORK 2h + LEARN untagged 1h (missing tag-ai!) + REST untimed 45m
            LedgerRecord(
                id = "cur-sleep",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = ms(yesterday, 7, 5),
                userConfirmedAt = ms(yesterday, 7, 5),
                category = TimeCategory.SLEEP,
                title = "Ngủ đêm",
                date = yesterday.toString(),
                durationSeconds = 7 * 3600L,
                startedAt = ms(yesterday, 0, 0),
                endedAt = ms(yesterday, 7, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "cs1"
            ),
            LedgerRecord(
                id = "cur-work",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = ms(yesterday, 11, 5),
                userConfirmedAt = ms(yesterday, 11, 5),
                category = TimeCategory.WORK,
                title = "Xây tính năng mới",
                date = yesterday.toString(),
                durationSeconds = 2 * 3600L,
                startedAt = ms(yesterday, 9, 0),
                endedAt = ms(yesterday, 11, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "cw1"
            ),
            LedgerRecord(
                id = "cur-learn-untagged",
                source = "manual",
                layer = TimeLayer.REPORTED_TIMED,
                createdAt = ms(yesterday, 21, 5),
                userConfirmedAt = ms(yesterday, 21, 5),
                category = TimeCategory.LEARN,
                title = "Đọc paper AI",
                date = yesterday.toString(),
                durationSeconds = 3600L,
                startedAt = ms(yesterday, 20, 0),
                endedAt = ms(yesterday, 21, 0),
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "cl1",
                tagId = null
            ),
            LedgerRecord(
                id = "cur-rest-untimed",
                source = "manual",
                layer = TimeLayer.REPORTED_UNTIMED,
                createdAt = ms(yesterday, 13, 0),
                userConfirmedAt = ms(yesterday, 13, 0),
                category = TimeCategory.REST,
                title = "Nghỉ trưa",
                date = yesterday.toString(),
                durationSeconds = 2700L,
                startedAt = null,
                endedAt = null,
                timezone = zone.id,
                status = "CONFIRMED",
                idempotencyKey = "cr1"
            )
        )

        val summary = com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = today.toString(),
            records = threeLayerRecords,
            goals = listOf(aiGoal.copy(validFrom = today.minusDays(6).toString())),
            tags = listOf(learnTag),
            zoneId = zone,
            nowEpochMs = nowMs
        )

        assertEquals(7, summary.activityDays.size)
        summary.activityDays.forEach { d ->
            assertEquals(d.recordedTimedSeconds, d.bucketSeconds.values.sum())
            assertEquals(d.calendarDaySeconds, d.recordedTimedSeconds + d.unrecordedSeconds)
        }

        val yDay = summary.activityDays.first { it.dateLocal == yesterday.toString() }
        assertEquals(10 * 3600L, yDay.recordedTimedSeconds)
        assertEquals(2700L, yDay.untimedSeconds) // Untimed isolated outside 24h bar
        assertEquals(7 * 3600L, yDay.bucketSeconds[com.timewallet.mvp.domain.usecase.ActivityBucket.SLEEP_REST])
        assertEquals(2 * 3600L, yDay.bucketSeconds[com.timewallet.mvp.domain.usecase.ActivityBucket.WORK])
        assertEquals(3600L, yDay.bucketSeconds[com.timewallet.mvp.domain.usecase.ActivityBucket.LEARN])

        val workTotal = summary.activityTotals.first { it.bucket == com.timewallet.mvp.domain.usecase.ActivityBucket.WORK }
        assertEquals(2 * 3600L, workTotal.durationSeconds)
        assertEquals(3600L, workTotal.prevWeekDurationSeconds)
        assertEquals(100, workTotal.deltaPercentVsPrevWeek)

        // Goal target uses tag-ai ("Học AI") -> 0p because cur-learn-untagged missed tagId, so hintVi must explain!
        val goalItem = summary.goalItems.single()
        assertFalse(goalItem.met)
        assertTrue(goalItem.progressText.startsWith("0p /"))
        assertTrue(goalItem.hintVi?.contains("Học tập") == true && goalItem.hintVi?.contains("Học AI") == true)

        val story = summary.wallyStory
        assertNotNull(story)
        assertTrue(story!!.mainFinding.isNotBlank() && !story.actionLabel.isNullOrBlank())
        assertTrue(story.uncertaintyNote != null) // missing Tag goal nuance -> honest conditional note
        assertFalse(summary.actionableInsight.contains("Đặt giờ trên lịch"))
        assertFalse(summary.actionableInsight.contains("mô phỏng", ignoreCase = true))

        val insightsSrc = java.io.File("src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt").readText()
        assertFalse(insightsSrc.contains("Đã dùng"))
        assertFalse(insightsSrc.contains("Giờ vàng:"))
        listOf(
            "Đã ghi nhận",
            "\$filterLabel: \$shownLabel / 24h",
            "Hoạt động",
            "Chưa ghi nhận",
            "Wally kể từ dữ liệu",
            "Phân tích AI ›",
            "Khung ghi nhận nhiều nhất",
            "Đã ẩn lời kể"
        ).forEach { expected ->
            assertTrue("InsightsScreen thiếu \"$expected\"", insightsSrc.contains(expected))
        }
    }

    private object ContextRouteDecisionBuilder {
        fun eveningLearn(today: LocalDate) = com.timewallet.mvp.domain.usecase.ContextRouteDecision(
            mode = ContextRouteMode.ADAPTIVE,
            targetDate = today,
            dayWindow = DayTimeWindow.EVENING,
            focusCategory = TimeCategory.LEARN,
            includeGoals = true,
            includeScheduleGaps = true,
            includeTasks = true,
            includeHabits = true,
            includeCoachSummary = true,
            includeContinuity = true,
            reason = "test"
        )
    }
}
