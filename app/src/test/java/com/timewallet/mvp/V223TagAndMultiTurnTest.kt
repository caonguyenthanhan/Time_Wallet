package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.WallyTodoAiPlanner
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.AiTodoAnalysisReport
import com.timewallet.mvp.domain.usecase.AiTodoEngine
import com.timewallet.mvp.domain.usecase.AiTodoProposal
import com.timewallet.mvp.domain.usecase.AiTodoProposalKind
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.ContextRouteMode
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.OpenChatDraftSnapshot
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.QueryIntent
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.QueryPeriod
import com.timewallet.mvp.domain.usecase.QueryPeriodKind
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.WallyContextEngine
import com.timewallet.mvp.domain.usecase.WallyConversationEngine
import com.timewallet.mvp.overlay.FloatingChatSnapshotData
import com.timewallet.mvp.overlay.WallyFloatingChatSession
import com.timewallet.mvp.ui.TimeWalletUiState
import com.timewallet.mvp.ui.WallyChatRunner
import com.timewallet.mvp.ui.WallyIntelligenceCoordinator
import com.timewallet.mvp.ui.toBatchInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class V223TagAndMultiTurnTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 25)
    private val tomorrow = today.plusDays(1)
    private val nowTime = LocalTime.of(10, 0)
    private val nowMs = LocalDateTime.of(today, nowTime).atZone(zone).toInstant().toEpochMilli()

    private fun makeTag(name: String, cat: TimeCategory = TimeCategory.LEARN, id: String, createdAt: Long = 100L): Tag =
        TagRules.newTag(name, TagRules.nameKey(name), cat, createdAt, id = id)

    private val ieltsTag = makeTag("IELTS", TimeCategory.LEARN, "tag_ielts", createdAt = 10L)
    private val toeicTag = makeTag("TOEIC", TimeCategory.LEARN, "tag_toeic", createdAt = 20L)

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
        val s = date.atTime(LocalTime.parse(startHm)).atZone(zone).toInstant().toEpochMilli()
        val e = date.atTime(LocalTime.parse(endHm)).atZone(zone).toInstant().toEpochMilli()
        val status = if (layer == TimeLayer.PLANNED) "PLANNED" else "CONFIRMED"
        return LedgerRecord(
            id = id,
            source = "manual",
            layer = layer,
            createdAt = s,
            userConfirmedAt = if (layer == TimeLayer.PLANNED) 0L else e,
            category = cat,
            title = title,
            date = date.toString(),
            durationSeconds = (e - s) / 1000L,
            startedAt = s,
            endedAt = e,
            timezone = zone.id,
            status = status,
            idempotencyKey = "k_$id",
            tagId = tagId,
            confirmationStateRaw = if (layer == TimeLayer.PLANNED) "PLANNED" else "COMPLETED"
        )
    }

    private class FakeConn(url: URL, private val reply: () -> Pair<Int, String>) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() {}
        override fun setRequestProperty(k: String?, v: String?) {}
        override fun getOutputStream(): OutputStream = sent
        private val r by lazy { reply() }
        override fun getResponseCode(): Int = r.first
        override fun getInputStream(): InputStream = ByteArrayInputStream(r.second.toByteArray(Charsets.UTF_8))
        override fun getErrorStream(): InputStream? = null
    }

    private class FakeNet(private vararg val replies: Pair<Int, String>) {
        private var idx = 0
        val opener: (URL) -> HttpURLConnection = { url ->
            val r = replies[minOf(idx++, replies.size - 1)]
            FakeConn(url) { r }
        }
    }

    private fun cloudJsonBody(content: String): String =
        JSONObject().put(
            "choices",
            JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", content))
            )
        ).toString()

    @Test
    fun `flagship multi-turn IELTS scheduling works end to end in App Chat and counts only after confirm`() = runBlocking {
        // Tạo >8 tag để IELTS là tag ít dùng (ngoài top-8 gợi ý gần nhất), vẫn phải chọn được khi gọi tên rõ.
        val otherTags = (1..9).map { makeTag("Tag$it", TimeCategory.WORK, "tag_w$it", createdAt = 1000L + it) }
        val allTags = otherTags + ieltsTag
        val baselineRecords = otherTags.mapIndexed { idx, t ->
            timedRec("base_$idx", today.minusDays(1), "%02d:00".format(8 + idx), "%02d:30".format(8 + idx), t.category, t.name, tagId = t.id)
        } + timedRec("tmr_busy", tomorrow, "18:00", "18:45", TimeCategory.WORK, "Họp tối")

        val ieltsGoal = GoalRules.create(
            targetType = GoalTargetType.TAG,
            targetKey = ieltsTag.id,
            kind = GoalKind.MIN,
            period = GoalPeriod.DAY,
            targetSeconds = 45 * 60L,
            today = tomorrow,
            nowEpochMs = nowMs
        )

        val top8 = TagRules.suggestions(allTags, baselineRecords, nowMs, zone, limit = 8)
        assertFalse("IELTS là tag ít dùng nên ngoài top-8", top8.any { it.id == ieltsTag.id })

        val dao = InMemoryLedgerDao()
        val uiState = MutableStateFlow(TimeWalletUiState(tags = allTags, records = baselineRecords))
        val coordinator = WallyIntelligenceCoordinator(nowEpochProvider = { nowMs })
        val runner = WallyChatRunner(
            scope = this,
            state = uiState,
            computeAnswers = { basis, intents ->
                intents.map { LedgerQueryEngine.execute(it, basis.records, zone, nowMs, basis.timer, basis.budgets, allTags) }
            },
            toast = {},
            onUnexpected = { e -> throw AssertionError("Unexpected exception in WallyChatRunner", e) },
            computeContext = Dispatchers.Unconfined,
            launchContext = Dispatchers.Unconfined
        )

        val turn1Net = FakeNet(
            200 to cloudJsonBody("""{"reply":"Bạn muốn học lúc 19h hay 20h?","proposals":[],"queries":[]}""")
        )

        // Lượt 1: Người dùng yêu cầu "Xếp buổi ôn IELTS ngày mai" (chưa nêu giờ)
        runner.submit("Xếp buổi ôn IELTS ngày mai") {
            val s = uiState.value
            val plan = coordinator.prepareChatTurn(
                userText = "Xếp buổi ôn IELTS ngày mai",
                today = today,
                nowTime = nowTime,
                zone = zone,
                perms = s.aiReadPermissions,
                lastQueryContext = s.lastQueryContext,
                records = s.records,
                tags = s.tags,
                goals = s.goals,
                tasks = s.tasks,
                habits = s.habits,
                habitLogs = s.habitLogs,
                activeTimer = s.activeTimer
            )
            assertEquals(ContextRouteMode.ADAPTIVE, plan.route.mode)
            assertEquals(ieltsTag.id, plan.route.focusTag?.id)
            assertEquals(tomorrow, plan.route.targetDate)
            val raw = GeminiProposalClient.analyzeInput(
                userText = "Xếp buổi ôn IELTS ngày mai",
                today = today,
                nowTime = nowTime,
                timezoneId = zone.id,
                optInEnabled = true,
                apiKey = "sk-synthetic-v223",
                connectionFactory = turn1Net.opener,
                sleeper = {},
                lastQuery = plan.effectiveLastQuery,
                adaptiveContext = plan.adaptiveContext,
                activeTags = s.tags,
                pendingDraft = coordinator.currentConversationSnapshot(s.aiReadPermissions)?.pendingProposal
            )
            coordinator.finalizeChatTurn(
                userText = "Xếp buổi ôn IELTS ngày mai",
                plan = plan,
                outcome = raw,
                perms = s.aiReadPermissions,
                records = s.records,
                activeTimer = s.activeTimer,
                zone = zone,
                tags = s.tags,
                tasks = s.tasks
            )
        }
        runner.currentJob?.join()

        assertTrue("Lượt 1 chưa tạo thẻ lưu nháp", uiState.value.proposals.isEmpty())
        assertTrue("Lượt 1 hiển thị thẻ hỏi lại", uiState.value.chatAnswer?.isClarification == true)
        assertTrue("Gợi ý chip có giờ khả dụng", uiState.value.chatAnswer?.suggestions?.isNotEmpty() == true)
        val snapAfterTurn1 = coordinator.currentConversationSnapshot(uiState.value.aiReadPermissions)
        assertEquals(ieltsTag.id, snapAfterTurn1?.pendingProposal?.tagId)
        assertEquals(tomorrow.toString(), snapAfterTurn1?.pendingProposal?.dateLocal)

        // Lượt 2: Người dùng trả lời "20h, học 45 phút nhé" (AI có thể quên ngày/tagName -> Coordinator & ConversationEngine vẫn giữ đủ)
        val turn2Net = FakeNet(
            200 to cloudJsonBody(
                """{"reply":null,"proposals":[{"title":"Ôn IELTS","category":"learn","tagName":null,"durationSeconds":2700,"dateLocal":"$today","startLocal":"20:00","endLocal":"20:45","timeEvidence":"explicit_range","needsConfirmation":true}],"queries":[]}"""
            )
        )
        assertTrue("turn2 submit must be accepted", runner.submit("20h, học 45 phút nhé") {
            val s = uiState.value
            val plan = coordinator.prepareChatTurn(
                userText = "20h, học 45 phút nhé",
                today = today,
                nowTime = nowTime,
                zone = zone,
                perms = s.aiReadPermissions,
                lastQueryContext = s.lastQueryContext,
                records = s.records,
                tags = s.tags,
                goals = s.goals,
                tasks = s.tasks,
                habits = s.habits,
                habitLogs = s.habitLogs,
                activeTimer = s.activeTimer
            )
            assertEquals(ContextRouteMode.ADAPTIVE, plan.route.mode)
            assertEquals(tomorrow, plan.route.targetDate)
            val pending = coordinator.currentConversationSnapshot(s.aiReadPermissions)?.pendingProposal
            assertNotNull(pending)
            val raw = GeminiProposalClient.analyzeInput(
                userText = "20h, học 45 phút nhé",
                today = today,
                nowTime = nowTime,
                timezoneId = zone.id,
                optInEnabled = true,
                apiKey = "sk-synthetic-v223",
                connectionFactory = turn2Net.opener,
                sleeper = {},
                lastQuery = plan.effectiveLastQuery,
                adaptiveContext = plan.adaptiveContext,
                activeTags = s.tags,
                pendingDraft = pending
            )
            val finalized = coordinator.finalizeChatTurn(
                userText = "20h, học 45 phút nhé",
                plan = plan,
                outcome = raw,
                perms = s.aiReadPermissions,
                records = s.records,
                activeTimer = s.activeTimer,
                zone = zone,
                tags = s.tags,
                tasks = s.tasks
            )
            assertEquals("raw=${raw.proposals} fault=${raw.fault} warn=${raw.warnings} reply=${raw.reply} fin=${finalized.proposals}", 1, finalized.proposals.size)
            finalized
        })
        runner.currentJob?.join()

        assertNull("chatFault=${uiState.value.chatFault}, answer=${uiState.value.chatAnswer}", uiState.value.chatFault)
        assertEquals("answer=${uiState.value.chatAnswer}", 1, uiState.value.proposals.size)
        val createdDraft = uiState.value.proposals.single()
        assertEquals(tomorrow.toString(), createdDraft.dateLocal)
        assertEquals("20:00", createdDraft.startLocal)
        assertEquals("20:45", createdDraft.endLocal)
        assertEquals(45, createdDraft.durationMinutes)
        assertEquals(ieltsTag.id, createdDraft.tagId)
        assertTrue(createdDraft.isFuturePlanned)

        // Lượt 3 (trước khi bấm xác nhận): đổi giờ sang 19h30 và bỏ Tag trên đúng thẻ đang mở -> không nhân đôi thẻ và không tự gắn lại IELTS
        val turn3Net = FakeNet(
            200 to cloudJsonBody("""{"reply":null,"proposals":[],"queries":[]}""")
        )
        runner.submit("đổi sang 19h30, bỏ tag") {
            val s = uiState.value
            val openSnaps = s.proposals.map {
                OpenChatDraftSnapshot(
                    id = it.id,
                    title = it.title,
                    categoryKey = it.categoryKey,
                    tagId = it.tagId,
                    dateLocal = it.dateLocal,
                    startLocal = it.startLocal.ifBlank { null },
                    endLocal = it.endLocal.ifBlank { null },
                    durationMinutes = it.durationMinutesText.trim().toIntOrNull(),
                    isFuturePlanned = it.isFuturePlanned,
                    taskId = it.taskId
                )
            }
            val plan = coordinator.prepareChatTurn(
                userText = "đổi sang 19h30, bỏ tag",
                today = today,
                nowTime = nowTime,
                zone = zone,
                perms = s.aiReadPermissions,
                lastQueryContext = s.lastQueryContext,
                records = s.records,
                tags = s.tags,
                goals = s.goals,
                tasks = s.tasks,
                habits = s.habits,
                habitLogs = s.habitLogs,
                activeTimer = s.activeTimer,
                openDrafts = openSnaps
            )
            val raw = GeminiProposalClient.analyzeInput(
                userText = "đổi sang 19h30, bỏ tag",
                today = today,
                nowTime = nowTime,
                timezoneId = zone.id,
                optInEnabled = true,
                apiKey = "sk-synthetic-v223",
                connectionFactory = turn3Net.opener,
                sleeper = {},
                lastQuery = plan.effectiveLastQuery,
                adaptiveContext = plan.adaptiveContext,
                activeTags = s.tags,
                pendingDraft = coordinator.currentConversationSnapshot(s.aiReadPermissions)?.pendingProposal
            )
            coordinator.finalizeChatTurn(
                userText = "đổi sang 19h30, bỏ tag",
                plan = plan,
                outcome = raw,
                perms = s.aiReadPermissions,
                records = s.records,
                activeTimer = s.activeTimer,
                zone = zone,
                tags = s.tags,
                tasks = s.tasks,
                openDrafts = openSnaps
            )
        }
        runner.currentJob?.join()
        assertEquals(1, uiState.value.proposals.size)
        val updatedOpenDraft = uiState.value.proposals.single()
        assertEquals("19:30", updatedOpenDraft.startLocal)
        assertEquals("20:15", updatedOpenDraft.endLocal)
        assertNull("Đã yêu cầu bỏ tag nên phải gỡ hẳn Tag", updatedOpenDraft.tagId)

        // Chọn lại IELTS rồi xác nhận lưu lịch dự kiến -> PLANNED không tính vào thống kê/mục tiêu IELTS thực tế (0p)
        val draftToConfirm = updatedOpenDraft.copy(tagId = ieltsTag.id, categoryKey = TimeCategory.LEARN.key)
        val confirmPlan = BatchConfirm.run(
            dao, listOf(draftToConfirm.toBatchInput()), null, { null }, nowMs, zone
        ) { nowMs } as ConfirmResult.Committed
        assertEquals(1, confirmPlan.savedCount)
        val plannedDomain = dao.getAllRecordsSnapshot().single().toDomain()
        assertEquals(TimeLayer.PLANNED, plannedDomain.layer)
        assertEquals(ieltsTag.id, plannedDomain.tagId)

        val goalBeforeConfirmActual = GoalRules.progress(
            ieltsGoal, listOf(plannedDomain), tomorrow, tomorrow.atTime(19, 0).atZone(zone).toInstant().toEpochMilli(), zone
        )
        assertEquals("Khi mới xếp dự kiến, IELTS thực tế vẫn phải là 0p", 0L, goalBeforeConfirmActual.usedSeconds)

        // Xác nhận đã học xong (sau 20:15 ngày mai) -> mới cộng 45p vào mục tiêu và thống kê IELTS
        val afterStudyMs = tomorrow.atTime(20, 30).atZone(zone).toInstant().toEpochMilli()
        val confirmActual = ConfirmationEngine.confirmCompleted(
            record = plannedDomain,
            existingRecords = listOf(plannedDomain),
            activeTimer = null,
            nowEpochMs = afterStudyMs,
            zoneId = zone,
            overrideTagId = plannedDomain.tagId,
            overrideCategoryKey = TimeCategory.LEARN.key
        ) as ConfirmationActionResult.Applied
        val completedRecord = confirmActual.updatedRecords.single()
        assertEquals(TimeLayer.REPORTED_TIMED, completedRecord.layer)
        assertEquals(ieltsTag.id, completedRecord.tagId)
        assertEquals(45 * 60L, completedRecord.durationSeconds)

        val goalAfterConfirm = GoalRules.progress(ieltsGoal, listOf(completedRecord), tomorrow, afterStudyMs, zone)
        assertEquals(45 * 60L, goalAfterConfirm.usedSeconds)
        assertTrue(goalAfterConfirm.met)
    }

    @Test
    fun `floating chat session supports multi-turn IELTS scheduling Tag edit or clear and confirm input`() = runBlocking {
        val session = WallyFloatingChatSession()
        val perms = AiReadPermissions()
        val data = FloatingChatSnapshotData(
            records = emptyList(),
            tags = listOf(ieltsTag, toeicTag),
            goals = emptyList(),
            tasks = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            budgets = emptyList(),
            activeTimer = null,
            perms = perms,
            aiPrefs = AiPreferences(optInEnabled = true, apiKey = "sk-synthetic-v223")
        )

        // Lượt 1: "Xếp buổi ôn IELTS ngày mai"
        val t1 = session.processTurn(
            userText = "Xếp buổi ôn IELTS ngày mai",
            currentDrafts = emptyList(),
            data = data,
            today = today,
            nowTime = nowTime,
            zone = zone,
            nowEpochMs = nowMs
        ) { _, _, _, _, _ ->
            ParseChatOutcome(
                proposals = emptyList(),
                warnings = emptyList(),
                source = OutcomeSource.CLOUD,
                reply = "Bạn muốn học lúc 19h hay 20h?"
            )
        }
        assertTrue(t1.updatedDrafts.isEmpty())
        assertTrue(t1.replyMessage?.isClarification == true)

        // Lượt 2: "20h, học 45 phút nhé" -> Floating Chat cập nhật ra 1 draft ngày mai 20:00-20:45 + tag IELTS
        val t2 = session.processTurn(
            userText = "20h, học 45 phút nhé",
            currentDrafts = t1.updatedDrafts,
            data = data,
            today = today,
            nowTime = nowTime,
            zone = zone,
            nowEpochMs = nowMs
        ) { _, _, _, _, pending ->
            assertEquals(tomorrow.toString(), pending?.dateLocal)
            assertEquals(ieltsTag.id, pending?.tagId)
            val json = """{"reply":"Đã tạo đề xuất.","proposals":[{"title":"Ôn IELTS","category":"learn","tagName":"IELTS","durationSeconds":2700,"dateLocal":"$tomorrow","startLocal":"20:00","endLocal":"20:45","timeEvidence":"explicit_range","needsConfirmation":true}],"queries":[]}"""
            GeminiProposalClient.parseAndValidateResponseJson(json, "20h, học 45 phút nhé", today, nowTime, activeTags = data.tags)
        }
        assertEquals(1, t2.updatedDrafts.size)
        val draft = t2.updatedDrafts.single()
        assertEquals(tomorrow.toString(), draft.dateLocal)
        assertEquals("20:00", draft.startLocal)
        assertEquals("20:45", draft.endLocal)
        assertEquals(ieltsTag.id, draft.tagId)

        // Người dùng chỉnh lại thành 19h30 ngay trên thẻ đang mở -> cập nhật đúng thẻ đó thay vì nhân đôi
        val t3 = session.processTurn(
            userText = "đổi sang 19h30",
            currentDrafts = t2.updatedDrafts,
            data = data,
            today = today,
            nowTime = nowTime,
            zone = zone,
            nowEpochMs = nowMs
        ) { _, _, _, _, pending ->
            assertEquals(draft.id, pending?.draftId)
            ParseChatOutcome(proposals = emptyList(), warnings = emptyList(), source = OutcomeSource.CLOUD, reply = null)
        }
        assertEquals(1, t3.updatedDrafts.size)
        assertEquals("19:30", t3.updatedDrafts.single().startLocal)
        assertEquals("20:15", t3.updatedDrafts.single().endLocal)
        assertEquals(ieltsTag.id, t3.updatedDrafts.single().tagId)

        // Đổi / bỏ Tag trước khi xác nhận -> BatchRecordInput giữ lựa chọn cuối
        val swapped = session.updateDraftTag(t3.updatedDrafts, t3.updatedDrafts.single().id, toeicTag.id, data.tags, perms)
        assertEquals(toeicTag.id, session.buildConfirmInput(swapped.single()).tagId)
        val cleared = session.updateDraftTag(swapped, swapped.single().id, null, data.tags, perms)
        assertNull(session.buildConfirmInput(cleared.single()).tagId)
    }

    @Test
    fun `shortened title keeps explicit IELTS tag while ambiguous English input rejects hallucinated IELTS`() {
        val shortenedJson = """
            {"reply":null,"proposals":[{"title":"Luyện nghe","category":"learn","tagName":"IELTS","durationSeconds":3600,"dateLocal":"$tomorrow","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","needsConfirmation":true}],"queries":[]}
        """.trimIndent()
        val parsedShortened = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = cloudJsonBody(shortenedJson),
            userText = "xếp 1 giờ luyện nghe IELTS",
            today = today,
            nowTime = nowTime,
            activeTags = listOf(ieltsTag)
        )
        assertEquals(ieltsTag.id, parsedShortened.proposals.single().tagId)
        assertEquals("IELTS", parsedShortened.proposals.single().tagName)

        // Trường hợp AI rút gọn tiêu đề VÀ thậm chí quên "tagName": TagRules vẫn giữ đúng IELTS từ câu gốc
        val noTagJson = """
            {"reply":null,"proposals":[{"title":"Luyện nghe","category":"learn","tagName":null,"durationSeconds":3600,"dateLocal":"$tomorrow","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","needsConfirmation":true}],"queries":[]}
        """.trimIndent()
        val parsedFallbackExplicit = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = cloudJsonBody(noTagJson),
            userText = "xếp 1 giờ luyện nghe IELTS",
            today = today,
            nowTime = nowTime,
            activeTags = listOf(ieltsTag)
        )
        assertEquals(ieltsTag.id, parsedFallbackExplicit.proposals.single().tagId)

        // Trường hợp câu mơ hồ "mai học tiếng Anh": dù AI cố đoán "IELTS", bộ máy vẫn chặn không gắn nhầm Tag IELTS
        val hallucinatedJson = """
            {"reply":null,"proposals":[{"title":"Học tiếng Anh","category":"learn","tagName":"IELTS","durationSeconds":1800,"dateLocal":"$tomorrow","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","needsConfirmation":true}],"queries":[]}
        """.trimIndent()
        val parsedAmbiguous = GeminiProposalClient.parseAndValidateResponseJson(
            responseBody = cloudJsonBody(hallucinatedJson),
            userText = "mai học tiếng Anh 30 phút",
            today = today,
            nowTime = nowTime,
            activeTags = listOf(ieltsTag)
        )
        assertEquals(TimeCategory.LEARN.key, parsedAmbiguous.proposals.single().category)
        assertNull("Mơ hồ không được tự suy đoán thành IELTS", parsedAmbiguous.proposals.single().tagId)
        assertFalse(WallyConversationEngine.containsExplicitClearTagCommand("học IELTS"))
    }

    @Test
    fun `reopening edit and confirm dialogs lets user change or clear tag and keeps final choice`() {
        val initial = timedRec("rec_1", today, "08:00", "08:45", TimeCategory.LEARN, "Ôn IELTS", tagId = ieltsTag.id, layer = TimeLayer.PLANNED)
        val afterEdit = ConfirmationEngine.adjustRecord(
            existing = initial,
            newTitle = "Ôn TOEIC",
            newCategoryKey = TimeCategory.LEARN.key,
            newDateLocal = today.toString(),
            newDurationMinutes = 45,
            newStartHm = "08:00",
            newEndHm = "08:45",
            allowFutureAsPlanned = true,
            existingRecords = listOf(initial),
            activeTimer = null,
            nowEpochMs = nowMs,
            zoneId = zone,
            newTagId = toeicTag.id
        ) as ConfirmationActionResult.Applied
        val editedRecord = afterEdit.updatedRecords.single()
        assertEquals(toeicTag.id, editedRecord.tagId)

        // Mở xác nhận hoàn thành và bỏ Tag -> bản ghi thực tế cuối không còn Tag
        val afterConfirmMs = LocalDateTime.of(today, LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        val confirmedClear = ConfirmationEngine.confirmCompleted(
            record = editedRecord,
            existingRecords = listOf(editedRecord),
            activeTimer = null,
            nowEpochMs = afterConfirmMs,
            zoneId = zone,
            overrideTagId = null,
            overrideCategoryKey = TimeCategory.LEARN.key
        ) as ConfirmationActionResult.Applied
        val finalRec = confirmedClear.updatedRecords.single()
        assertEquals(TimeLayer.REPORTED_TIMED, finalRec.layer)
        assertNull(finalRec.tagId)
    }

    @Test
    fun `zero-tag users can query total study and typical window without Tag nagging in Coach or Insights`() {
        val studyRecords = listOf(
            timedRec("s1", today.minusDays(1), "19:30", "21:30", TimeCategory.LEARN, "Học ngữ pháp"),
            timedRec("s2", today, "08:00", "09:00", TimeCategory.LEARN, "Đọc tài liệu")
        )
        val endTodayMs = LocalDateTime.of(today, LocalTime.of(22, 0)).atZone(zone).toInstant().toEpochMilli()

        // 1. CoachDigest không được chê "chưa gắn Tag" khi người dùng chưa tạo Tag nào
        val digest = WallyContextEngine.buildFilteredCoachDigest(
            perms = AiReadPermissions(),
            records = studyRecords,
            tags = emptyList(),
            goals = emptyList(),
            tasks = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            days = 7,
            nowMs = endTodayMs,
            zone = zone
        )
        assertFalse(digest.text.contains("chưa gắn Tag", ignoreCase = true))

        // 2. Truy vấn tổng giờ học & khung giờ thường học hoạt động khi không có Tag
        val totalLearn = LedgerQueryEngine.execute(
            intent = QueryIntent(QueryMetric.TOTAL, QueryPeriod(today.minusDays(1), today, "2 ngày qua", QueryPeriodKind.ROLLING), category = TimeCategory.LEARN),
            records = studyRecords,
            zoneId = zone,
            nowEpochMs = endTodayMs,
            tags = emptyList()
        )
        assertTrue(totalLearn.headline.contains("3 giờ") || totalLearn.headline.contains("3h"))

        val typicalWindow = LedgerQueryEngine.execute(
            intent = QueryIntent(QueryMetric.TYPICAL_WINDOW, QueryPeriod(today.minusDays(6), today, "7 ngày qua", QueryPeriodKind.ROLLING), category = TimeCategory.LEARN),
            records = studyRecords,
            zoneId = zone,
            nowEpochMs = endTodayMs,
            tags = emptyList()
        )
        assertTrue(typicalWindow.headline.contains("Khung giờ") || typicalWindow.headline.contains("Học"))
        assertTrue(typicalWindow.lines.any { it.contains("19:") || it.contains("Buổi tối") })

        // 3. Kiểm tra nguồn Coach prompt và InsightsScreen không nhắc "chưa gắn Tag" khi chưa có Tag
        assertTrue(GeminiProposalClient.coachSystemPrompt.contains("nếu người dùng chưa tạo Tag nào"))
        val insightsSrc = File("src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt").readText()
        assertTrue(insightsSrc.contains("val untaggedPrefix = if (hasTags)"))
    }

    @Test
    fun `analytics distinguishes 4 hours Learn vs 90m IELTS and never counts planned`() {
        val studyDate = today.minusDays(1)
        val records = listOf(
            timedRec("r1", studyDate, "08:00", "09:30", TimeCategory.LEARN, "IELTS Listening", tagId = ieltsTag.id),
            timedRec("r2", studyDate, "14:00", "16:30", TimeCategory.LEARN, "Học tài liệu khác", tagId = null),
            timedRec("p1", today, "20:00", "20:45", TimeCategory.LEARN, "Ôn IELTS", tagId = ieltsTag.id, layer = TimeLayer.PLANNED)
        )
        val ans = LedgerQueryEngine.execute(
            intent = QueryIntent(
                metric = QueryMetric.TOTAL,
                period = QueryPeriod(studyDate, today, "2 ngày", QueryPeriodKind.ROLLING),
                category = TimeCategory.LEARN,
                tagId = ieltsTag.id,
                tagName = ieltsTag.name
            ),
            records = records,
            zoneId = zone,
            nowEpochMs = nowMs,
            tags = listOf(ieltsTag)
        )
        assertTrue("Phải hiện 90p (1h 30p) IELTS trong câu trả lời: ${ans.headline}", ans.headline.contains("1h 30p"))
        val joinedLines = ans.lines.joinToString("\n")
        assertTrue("Phải hiện tổng 4 giờ Học tập: $joinedLines", joinedLines.contains("4 giờ") || joinedLines.contains("4h"))
        assertTrue("Phải phân biệt phần chưa gắn tag (2h 30p): $joinedLines", joinedLines.contains("2h 30p"))
        assertTrue("Phải tách riêng 45p dự kiến: $joinedLines / ${ans.notes}", joinedLines.contains("45p") || ans.notes.joinToString().contains("45p"))
    }

    @Test
    fun `todo proposals preserve linked task tagId through AI planner and report serialization`() {
        val task = TaskItem(
            id = "task_ielts",
            title = "Luyện IELTS Speaking",
            category = TimeCategory.LEARN,
            priority = TaskPriority.P1,
            estimatedMinutes = 45,
            dueDateLocal = tomorrow.toString(),
            status = TaskStatus.TODO,
            tagId = ieltsTag.id
        )
        val aiJson = """
            {"summary":"Kế hoạch tập trung IELTS.","orderedTaskIds":["task_ielts"],"proposals":[{"taskId":"task_ielts","kind":"SCHEDULE_INTO_GAP","reason":"Khung tối rảnh phù hợp","targetDateLocal":"$tomorrow","startHm":"20:00","endHm":"20:45","suggestedPriority":null,"suggestedSubtasks":[]}]}
        """.trimIndent()
        val parsed = WallyTodoAiPlanner.parseTodoPlanJson(
            responseBody = cloudJsonBody(aiJson),
            openTasks = listOf(task),
            records = emptyList(),
            activeTimer = null,
            todayLocal = today.toString(),
            nowEpochMs = nowMs,
            zoneId = zone,
            fallbackRisks = emptyList(),
            permsFingerprint = "fp_v223"
        )
        val parsedProp = parsed.proposals.single()
        assertEquals(ieltsTag.id, parsedProp.tagId)

        val report = AiTodoAnalysisReport(
            generatedAtEpochMs = nowMs,
            dateLocal = tomorrow.toString(),
            fromCloudAi = true,
            summaryVi = "Tóm tắt",
            orderedTaskIds = listOf(task.id),
            risks = emptyList(),
            proposals = listOf(
                AiTodoProposal(
                    id = "todo_p1",
                    kind = AiTodoProposalKind.SCHEDULE_INTO_GAP,
                    taskId = task.id,
                    taskTitle = task.title,
                    reasonVi = "Ôn buổi tối",
                    targetDateLocal = tomorrow.toString(),
                    startHm = "20:00",
                    endHm = "20:45",
                    categoryKey = task.category.key,
                    tagId = ieltsTag.id
                )
            )
        )
        val restored = AiTodoEngine.deserializeReport(AiTodoEngine.serializeReport(report))
        assertEquals(ieltsTag.id, restored?.proposals?.single()?.tagId)
    }
}
