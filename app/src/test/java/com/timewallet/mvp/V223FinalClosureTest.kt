package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.StrictJson
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TagAssignmentUndoToken
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.data.local.BatchCommitOutcome
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.BatchOverlapStep
import com.timewallet.mvp.data.local.BatchProposalPlanner
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.local.LedgerDao
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.PersistedChatConversation
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.WallyTagAssignmentEngine
import com.timewallet.mvp.overlay.FloatingChatSnapshotData
import com.timewallet.mvp.overlay.WallyFloatingChatSession
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.Wally20Coordinator
import com.timewallet.mvp.ui.WallyIntelligenceCoordinator
import com.timewallet.mvp.ui.toBatchInput
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Bộ test nghiệm thu chốt v2.2.3:
 * 1. Đồng bộ một đề xuất xuyên suốt Chat nổi <-> Chat trong app, giữ nguyên khi lỗi lưu & chỉ ghi 1 lần khi thử lại.
 * 2. Duyệt gắn Tag cho phiên cũ cụ thể, tính lại tổng ngay trong chat và cho phép Hoàn tác chính xác.
 * 3. Đồng nhất phép tính Goal & CategoryTimeBudget (gồm timer đang chạy) giữa Chat, Thống kê và Mục tiêu.
 */
class V223FinalClosureTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 25)
    private val tomorrow = today.plusDays(1)
    private val nowMs = ZonedDateTime.of(today, LocalTime.of(11, 30), zone).toInstant().toEpochMilli()

    private val ieltsTag = Tag(
        id = "tag_ielts",
        name = "IELTS",
        nameKey = TagRules.nameKey("IELTS"),
        category = TimeCategory.LEARN,
        archivedAt = null,
        createdAt = nowMs - 86_400_000L,
        updatedAt = nowMs - 86_400_000L
    )

    private fun cloudJsonBody(contentJson: String): String = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", contentJson))))
        .toString()

    private fun timedRec(
        id: String,
        date: LocalDate,
        startHm: String,
        endHm: String,
        cat: TimeCategory,
        title: String,
        tagId: String? = null,
        layer: TimeLayer = TimeLayer.REPORTED_TIMED
    ): LedgerRecord {
        val st = LocalTime.parse(startHm)
        val en = LocalTime.parse(endHm)
        val sMs = ZonedDateTime.of(date, st, zone).toInstant().toEpochMilli()
        val eMs = ZonedDateTime.of(date, if (en.isAfter(st)) en else st.plusMinutes(30), zone).toInstant().toEpochMilli()
        return LedgerRecord(
            id = id,
            source = "manual",
            layer = layer,
            createdAt = sMs,
            userConfirmedAt = if (layer == TimeLayer.PLANNED) 0L else eMs,
            category = cat,
            title = title,
            date = date.toString(),
            durationSeconds = (eMs - sMs) / 1000L,
            startedAt = sMs,
            endedAt = eMs,
            timezone = zone.id,
            status = if (layer == TimeLayer.PLANNED) "PLANNED" else "CONFIRMED",
            idempotencyKey = "key_$id",
            tagId = tagId
        )
    }

    @Test
    fun `floating turn hands off pending proposal across coordinators and retry after save conflict keeps single draft with idempotency`() = runBlocking {
        val perms = AiReadPermissions()
        val data = FloatingChatSnapshotData(
            records = emptyList(),
            tags = listOf(ieltsTag),
            goals = emptyList(),
            tasks = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            budgets = emptyList(),
            activeTimer = null,
            perms = perms,
            aiPrefs = com.timewallet.mvp.data.local.AiPreferences()
        )

        // Lượt 1 ở Chat nổi: user nói thiếu giờ
        val floatingSession = WallyFloatingChatSession(WallyIntelligenceCoordinator { nowMs })
        val turn1 = floatingSession.processTurn(
            userText = "Xếp buổi ôn IELTS ngày mai",
            currentDrafts = emptyList(),
            data = data,
            today = today,
            nowTime = LocalTime.of(11, 30),
            zone = zone,
            nowEpochMs = nowMs
        ) { _, _, _, _, _ ->
            ParseChatOutcome(
                proposals = emptyList(),
                warnings = emptyList(),
                source = OutcomeSource.CLOUD,
                reply = "Bạn muốn ôn IELTS ngày mai lúc mấy giờ và trong bao lâu?"
            )
        }
        assertTrue(turn1.updatedDrafts.isEmpty())
        val exported: PersistedChatConversation = floatingSession.coordinator.exportPersistedSnapshot(
            perms = perms,
            clarificationQuestion = turn1.replyMessage?.text,
            clarificationSuggestions = turn1.replyMessage?.suggestions.orEmpty()
        )!!

        // Chuyển sang Chat trong app: phục hồi đúng câu đang dở & tiếp tục trả lời "20h, học 45 phút nhé"
        val appCoord = WallyIntelligenceCoordinator { nowMs + 5_000L }
        appCoord.restorePersistedSnapshot(exported, listOf(ieltsTag), perms)

        val plan2 = appCoord.prepareChatTurn(
            userText = "20h, học 45 phút nhé",
            today = today,
            nowTime = LocalTime.of(11, 30),
            zone = zone,
            perms = perms,
            lastQueryContext = null,
            records = emptyList(),
            tags = listOf(ieltsTag),
            goals = emptyList(),
            tasks = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            activeTimer = null
        )
        val out2 = appCoord.finalizeChatTurn(
            userText = "20h, học 45 phút nhé",
            plan = plan2,
            outcome = ParseChatOutcome(
                proposals = listOf(
                    com.timewallet.mvp.domain.model.ActivityProposal(
                        id = "p_ai_2",
                        rawInput = "20h, học 45 phút nhé",
                        title = "Học tập",
                        category = "learn",
                        durationSeconds = 45 * 60L,
                        dateLocal = today.toString(),
                        startLocal = "20:00",
                        endLocal = "20:45",
                        timeEvidence = "explicit_range",
                        missingFields = emptyList(),
                        isFuturePlanned = true
                    )
                ),
                warnings = emptyList(),
                source = OutcomeSource.CLOUD
            ),
            perms = perms,
            records = emptyList(),
            activeTimer = null,
            zone = zone,
            tags = listOf(ieltsTag)
        )
        val draft = EditableProposalDraft.fromProposal(out2.proposals.single())
        assertEquals(tomorrow.toString(), draft.dateLocal)
        assertEquals("20:00", draft.startLocal)
        assertEquals("20:45", draft.endLocal)
        assertEquals(ieltsTag.id, draft.tagId)
        assertTrue(draft.title.contains("IELTS", ignoreCase = true))

        // Mô phỏng lỗi lưu:
        // (a) Xung đột mốc giờ thực tế trong BatchProposalPlanner -> ConflictAt, giữ nguyên draft để thử lại
        val blocking = timedRec("blk", today, "10:00", "11:00", TimeCategory.WORK, "Họp sáng", layer = TimeLayer.REPORTED_TIMED)
        val actualConflictDraft = draft.copy(
            dateLocal = today.toString(),
            startLocal = "10:00",
            endLocal = "10:45",
            isFuturePlanned = false
        )
        val failedPlan = BatchProposalPlanner.plan(
            inputs = listOf(actualConflictDraft.toBatchInput()),
            existingRecords = listOf(blocking),
            activeTimer = null,
            nowEpochMs = nowMs,
            zone = zone
        )
        assertTrue(failedPlan is BatchOverlapStep.ConflictAt)

        // (b) Lỗi ghi / từ chối từ DAO khi xác nhận bản kế hoạch -> ConfirmResult.Aborted, chưa ghi DB, thử lại giữ nguyên idempotencyKey + Tag & chỉ lưu ĐÚNG 1 lần
        val realDao = InMemoryLedgerDao()
        val abortOnceDao = object : LedgerDao by realDao {
            override suspend fun commitBatchRecordsGuarded(
                entities: List<com.timewallet.mvp.data.local.LedgerRecordEntity>,
                preCommitGuard: suspend () -> String?
            ): BatchCommitOutcome = BatchCommitOutcome(
                savedCount = 0,
                skippedDuplicates = 0,
                rejectedConflicts = emptyList(),
                abortedAtomically = true,
                errorMessage = "Lỗi lưu tạm thời"
            )
        }
        val aborted = BatchConfirm.run(
            dao = abortOnceDao,
            inputs = listOf(draft.toBatchInput()),
            activeTimer = null,
            readActiveTimer = { null },
            nowEpochMs = nowMs,
            zone = zone,
            readNowMs = { nowMs }
        )
        assertTrue(aborted is ConfirmResult.Aborted)
        assertEquals(0, realDao.getAllRecordsSnapshot().size)

        // Thử lại với CÙNG draft (giữ nguyên tên, ngày, giờ, Tag và idempotencyKey) -> lưu thành công 1 lần duy nhất
        val firstSave = BatchConfirm.run(
            dao = realDao,
            inputs = listOf(draft.toBatchInput()),
            activeTimer = null,
            readActiveTimer = { null },
            nowEpochMs = nowMs,
            zone = zone,
            readNowMs = { nowMs }
        )
        assertEquals(ConfirmResult.Committed(savedCount = 1, skippedDuplicates = 0), firstSave)
        val savedEntity = realDao.getAllRecordsSnapshot().single()
        assertEquals(tomorrow.toString(), savedEntity.date)
        assertEquals(ieltsTag.id, savedEntity.tagId)
        assertEquals(draft.idempotencyKey, savedEntity.idempotencyKey)

        // Thử lại lần hai với cùng idempotencyKey (bấm lặp / mạng chập chờn) -> không ghi trùng
        val duplicateRetry = BatchConfirm.run(
            dao = realDao,
            inputs = listOf(draft.toBatchInput()),
            activeTimer = null,
            readActiveTimer = { null },
            nowEpochMs = nowMs,
            zone = zone,
            readNowMs = { nowMs }
        )
        assertEquals(ConfirmResult.Committed(savedCount = 0, skippedDuplicates = 1), duplicateRetry)
        assertEquals(1, realDao.getAllRecordsSnapshot().size)

        // Kiểm tra tĩnh FloatingService không xóa draft khi ConfirmResult là Aborted/Invalid
        val floatingSrc = File("src/main/java/com/timewallet/mvp/overlay/WallyFloatingService.kt").readText()
        val confirmStart = floatingSrc.indexOf("is ConfirmResult.Committed -> {")
        val abortedStart = floatingSrc.indexOf("is ConfirmResult.Aborted ->", confirmStart)
        val committedBlock = floatingSrc.substring(confirmStart, abortedStart)
        val abortedBlock = floatingSrc.substring(abortedStart, floatingSrc.indexOf("messages = (messages + noticeMsg)", abortedStart))
        assertTrue("Committed phải xóa draft", committedBlock.contains("session.removeDraft("))
        assertFalse("Aborted/Invalid tuyệt đối không được xóa draft", abortedBlock.contains("session.removeDraft("))
    }

    @Test
    fun `retroactive tag assignment parses schema, clarifies duplicates, recalculates totals in chat, and cleanly supports Undo`() {
        val yesterday = today.minusDays(1)
        val rTagged = timedRec("r1", yesterday, "08:00", "09:00", TimeCategory.LEARN, "Luyện nghe IELTS", tagId = ieltsTag.id)
        val rUntagged1 = timedRec("r2", yesterday, "14:00", "15:30", TimeCategory.LEARN, "Đọc tài liệu", tagId = null)
        val rUntagged2 = timedRec("r3", yesterday, "19:00", "19:30", TimeCategory.LEARN, "Đọc tài liệu", tagId = null)

        // 1. Parse JSON "tagAssignments" theo hợp đồng schema chặt
        val aiBody = cloudJsonBody("""{"reply":null,"proposals":[],"tagAssignments":[{"recordTitle":"Đọc tài liệu","dateLocal":"$yesterday","startHm":"14:00","tagName":"IELTS"}],"queries":[]}""")
        val parsed = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = aiBody,
            userText = "Gắn tag IELTS cho phiên Đọc tài liệu lúc 14:00 hôm qua",
            today = today,
            nowTime = LocalTime.of(11, 30),
            activeTags = listOf(ieltsTag)
        )
        assertEquals(1, parsed.rawTagAssignments.size)
        assertEquals("Đọc tài liệu", parsed.rawTagAssignments.single().recordTitle)

        // 2. Nhiều phiên trùng tên nếu thiếu giờ -> hỏi lại bằng chip phân biệt, KHÔNG đoán bừa
        val coord = WallyIntelligenceCoordinator { nowMs }
        val perms = AiReadPermissions()
        val ambigPlan = runBlocking {
            coord.prepareChatTurn(
                userText = "Gắn tag IELTS cho phiên Đọc tài liệu hôm qua",
                today = today,
                nowTime = LocalTime.of(11, 30),
                zone = zone,
                perms = perms,
                lastQueryContext = null,
                records = listOf(rTagged, rUntagged1, rUntagged2),
                tags = listOf(ieltsTag),
                goals = emptyList(),
                tasks = emptyList(),
                habits = emptyList(),
                habitLogs = emptyList(),
                activeTimer = null
            )
        }
        val ambigOutcome = coord.finalizeChatTurn(
            userText = "Gắn tag IELTS cho phiên Đọc tài liệu hôm qua",
            plan = ambigPlan,
            outcome = ParseChatOutcome(emptyList(), emptyList(), source = OutcomeSource.CLOUD),
            perms = perms,
            records = listOf(rTagged, rUntagged1, rUntagged2),
            activeTimer = null,
            zone = zone,
            tags = listOf(ieltsTag)
        )
        assertTrue(ambigOutcome.tagAssignmentProposals.isEmpty())
        assertTrue(ambigOutcome.clarificationSuggestions.size >= 2)
        assertTrue(ambigOutcome.reply?.endsWith("?") == true)

        // 3. Khi nêu rõ 14:00 hôm qua -> ra đúng 1 TagAssignmentProposal (r2), chưa đổi sổ cái
        val exactOutcome = coord.finalizeChatTurn(
            userText = "Gắn tag IELTS cho phiên Đọc tài liệu lúc 14:00 hôm qua",
            plan = ambigPlan,
            outcome = parsed,
            perms = perms,
            records = listOf(rTagged, rUntagged1, rUntagged2),
            activeTimer = null,
            zone = zone,
            tags = listOf(ieltsTag)
        )
        val proposal = exactOutcome.tagAssignmentProposals.single()
        assertEquals("r2", proposal.recordId)
        assertNull(proposal.previousTagId)
        assertEquals(ieltsTag.id, proposal.newTagId)

        // 4. Duyệt gắn Tag -> cập nhật đúng phiên r2 & tính lại tổng ngay trong chat (1h -> 2h 30p) kèm UndoToken
        val updatedList = listOf(rTagged, rUntagged1.copy(tagId = ieltsTag.id), rUntagged2)
        val token = TagAssignmentUndoToken(
            actionId = "undo_1",
            recordId = proposal.recordId,
            recordTitle = proposal.recordTitle,
            previousTagId = proposal.previousTagId,
            previousTagName = proposal.previousTagName,
            previousCategoryKey = proposal.previousCategoryKey,
            appliedTagId = proposal.newTagId,
            appliedTagName = proposal.newTagName,
            appliedCategoryKey = proposal.newCategoryKey,
            affectedPeriodStart = proposal.affectedPeriodStart,
            affectedPeriodEnd = proposal.affectedPeriodEnd,
            affectedPeriodLabel = proposal.affectedPeriodLabel
        )
        val appliedCard = WallyTagAssignmentEngine.buildRecalculatedChatAnswer(
            token = token,
            updatedRecords = updatedList,
            tags = listOf(ieltsTag),
            budgets = emptyList(),
            goals = emptyList(),
            activeTimer = null,
            zone = zone,
            nowEpochMs = nowMs,
            wasUndone = false
        )
        val appliedText = (listOf(appliedCard.headline) + appliedCard.lines).joinToString("\n")
        assertTrue("Phải tính lại IELTS thành 2h 30p ngay trong chat: $appliedText", appliedText.contains("2h 30p"))
        assertNotNull(appliedCard.undoToken)
        assertFalse(appliedCard.undoToken!!.undone)

        // 5. Hoàn tác -> trả r2 về tagId = null & tính lại tổng ngay (về 1h IELTS, 2h chưa gắn tag)
        val revertedList = listOf(rTagged, rUntagged1, rUntagged2)
        val undoCard = WallyTagAssignmentEngine.buildRecalculatedChatAnswer(
            token = token,
            updatedRecords = revertedList,
            tags = listOf(ieltsTag),
            budgets = emptyList(),
            goals = emptyList(),
            activeTimer = null,
            zone = zone,
            nowEpochMs = nowMs,
            wasUndone = true
        )
        val undoText = (listOf(undoCard.headline) + undoCard.lines).joinToString("\n")
        assertTrue("Hoàn tác phải trả IELTS về 1h: $undoText", undoText.contains("1h"))
        assertTrue("Phải khôi phục phần chưa gắn tag (2h): $undoText", undoText.contains("2h"))
        assertTrue(undoCard.undoToken?.undone == true)
    }

    @Test
    fun `budget and goal progress match identically across Chat, Stats, and Goals hub including live running timer`() {
        val ieltsGoal = Goal(
            id = "g_ielts",
            groupId = "grp_ielts",
            targetType = GoalTargetType.TAG,
            targetKey = ieltsTag.id,
            kind = GoalKind.MIN,
            period = GoalPeriod.WEEK,
            targetSeconds = 5 * 3600L,
            status = GoalVersionStatus.ACTIVE,
            validFrom = today.minusDays(2).toString(),
            validTo = null,
            createdAt = nowMs - 86_400_000L,
            updatedAt = nowMs - 86_400_000L
        )
        val funBudget = CategoryTimeBudget(
            category = TimeCategory.FUN,
            period = BudgetPeriod.WEEKLY,
            targetSeconds = 3 * 3600L,
            isPriority = true,
            priorityOrder = 0,
            updatedAt = nowMs - 86_400_000L
        )
        val loggedIelts = timedRec("l1", today, "08:00", "09:30", TimeCategory.LEARN, "IELTS", tagId = ieltsTag.id) // 90p
        val loggedFun = timedRec("f1", today, "10:00", "10:30", TimeCategory.FUN, "Xem phim") // 30p
        val plannedIelts = timedRec("p1", today, "20:00", "21:00", TimeCategory.LEARN, "IELTS tối", tagId = ieltsTag.id, layer = TimeLayer.PLANNED) // KHÔNG cộng
        val runningTimer = ActiveTimerSession(
            title = "IELTS Listening live",
            categoryKey = TimeCategory.LEARN.key,
            startedAtEpochMs = nowMs - 15 * 60_000L, // 15p đang chạy
            startedElapsedRealtimeMs = 1_000L,
            timezone = zone.id,
            tagId = ieltsTag.id
        )
        val records = listOf(loggedIelts, loggedFun, plannedIelts)

        // 1. Thống kê (computeWeeklyEfficiency)
        val weeklyEff = LedgerAnalyticsCalculator.computeWeeklyEfficiency(
            endDateLocal = today.toString(),
            records = records,
            goals = listOf(ieltsGoal),
            budgets = listOf(funBudget),
            tags = listOf(ieltsTag),
            zoneId = zone,
            nowEpochMs = nowMs,
            activeTimer = runningTimer
        )
        val statsIelts = weeklyEff.goalItems.first { it.title == "IELTS" }
        val statsFun = weeklyEff.goalItems.first { it.title == "Giải trí" }
        assertEquals("1h 45p / 5h", statsIelts.progressText) // 90p + 15p live, không cộng 60p planned
        assertEquals("30p / 3h", statsFun.progressText)

        // 2. Ngăn Mục tiêu (Wally20Coordinator.buildUnifiedGoalSummaries)
        val unified = Wally20Coordinator.buildUnifiedGoalSummaries(
            goals = listOf(ieltsGoal),
            budgets = listOf(funBudget),
            records = records,
            tagNameOf = { id -> if (id == ieltsTag.id) ieltsTag.name else null },
            today = today,
            nowEpochMs = nowMs,
            zone = zone,
            activeTimer = runningTimer
        )
        assertEquals(90 * 60L + 15 * 60L, unified.first { it.name == "IELTS" }.progress?.usedSeconds)
        assertEquals(30 * 60L, unified.first { it.name.contains("Giải trí") }.progress?.usedSeconds)

        // 3. Chat (LedgerQueryEngine BUDGET)
        val intentAll = (AiQueryValidator.validate("BUDGET", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent
        val chatAns = LedgerQueryEngine.execute(
            intent = intentAll,
            records = records,
            zoneId = zone,
            nowEpochMs = nowMs,
            activeTimer = runningTimer,
            budgets = listOf(funBudget),
            tags = listOf(ieltsTag),
            goals = listOf(ieltsGoal)
        )
        val chatAllText = (listOf(chatAns.headline) + chatAns.lines).joinToString("\n")
        assertTrue("Chat phải khớp 1h 45p / ≥ 5h của Thống kê: $chatAllText", chatAllText.contains("1h 45p"))
        assertTrue("Chat phải khớp 30p / 3h ngân sách Giải trí: $chatAllText", chatAllText.contains("30p / 3h"))

        // Hỏi riêng mục tiêu Tag IELTS trong Chat -> khớp 1h 45p còn thiếu 3h 15p
        val tagIntent = intentAll.copy(tagId = ieltsTag.id, tagName = ieltsTag.name, category = TimeCategory.LEARN)
        val tagGoalAns = LedgerQueryEngine.execute(
            intent = tagIntent,
            records = records,
            zoneId = zone,
            nowEpochMs = nowMs,
            activeTimer = runningTimer,
            budgets = listOf(funBudget),
            tags = listOf(ieltsTag),
            goals = listOf(ieltsGoal)
        )
        assertTrue(tagGoalAns.headline, tagGoalAns.headline.contains("1h 45p") && tagGoalAns.headline.contains("3h 15p"))

        // StrictJson chấp nhận object chỉ có tagAssignments
        val strictParsed = StrictJson.extract("""{"tagAssignments":[{"recordTitle":"X","dateLocal":"$today","startHm":null,"tagName":"IELTS"}]}""")
        assertTrue(strictParsed is StrictJson.Extracted.Obj)
        StrictJson.requireShape((strictParsed as StrictJson.Extracted.Obj).json)
    }
}
