package com.timewallet.mvp.overlay

import android.content.Context
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.DbCommitOutcome
import com.timewallet.mvp.data.local.TimeWalletDatabase
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TagAssignmentProposal
import com.timewallet.mvp.domain.model.TagAssignmentUndoToken
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.usecase.AdaptivePromptContext
import com.timewallet.mvp.domain.usecase.AnalysisEpoch
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.ChatHistoryEntry
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.OpenChatDraftSnapshot
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.PendingProposalContext
import com.timewallet.mvp.domain.usecase.ProposalLifecycle
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.WallyConversationEngine
import com.timewallet.mvp.domain.usecase.WallyTagAssignmentEngine
import com.timewallet.mvp.domain.usecase.WallyTurnPolicy
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.WallyIntelligenceCoordinator
import com.timewallet.mvp.ui.toBatchInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class FloatingChatMsg(
    val isUser: Boolean,
    val text: String,
    val badge: String? = null,
    val isClarification: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val undoToken: TagAssignmentUndoToken? = null
)

data class FloatingChatSnapshotData(
    val records: List<LedgerRecord>,
    val tags: List<Tag>,
    val goals: List<Goal>,
    val tasks: List<TaskItem>,
    val habits: List<Habit>,
    val habitLogs: List<HabitLog>,
    val budgets: List<CategoryTimeBudget>,
    val activeTimer: ActiveTimerSession?,
    val perms: AiReadPermissions,
    val aiPrefs: AiPreferences
)

data class FloatingChatTurnResult(
    val replyMessage: FloatingChatMsg?,
    val updatedDrafts: List<EditableProposalDraft>,
    val outcome: ParseChatOutcome,
    val tagAssignmentProposals: List<TagAssignmentProposal> = outcome.tagAssignmentProposals
)

/**
 * Phiên chat dùng chung cho Bong bóng nổi (`WallyFloatingService`) và kiểm thử JVM:
 * dùng đúng [WallyIntelligenceCoordinator], [GeminiProposalClient], [LedgerQueryEngine],
 * [WallyConversationEngine] và [EditableProposalDraft] như Chat trong app —
 * không làm rơi Tag, hỗ trợ hội thoại nhiều lượt và truyền đủ `tagId`/`taskId` khi xác nhận.
 */
class WallyFloatingChatSession(
    val coordinator: WallyIntelligenceCoordinator = WallyIntelligenceCoordinator()
) {
    private val epoch = AnalysisEpoch()
    var lastQueryContext: QueryContext? = null
        private set

    suspend fun loadContextData(context: Context): FloatingChatSnapshotData = withContext(Dispatchers.IO) {
        val db = TimeWalletDatabase.getInstance(context)
        val store = TimerAndSettingsStore(context)
        FloatingChatSnapshotData(
            records = db.ledgerDao().getAllRecordsSnapshot().map { it.toDomain() },
            tags = db.tagDao().getAllSnapshot().map { it.toDomain() },
            goals = db.goalDao().getAllSnapshot().map { it.toDomain() },
            tasks = db.taskDao().getAllTasksSnapshot().map { it.toDomain() },
            habits = db.habitDao().getAllHabitsSnapshot().map { it.toDomain() },
            habitLogs = db.habitDao().getAllLogsSnapshot().map { it.toDomain() },
            budgets = db.budgetDao().getAllBudgetsSnapshot().map { it.toDomain() },
            activeTimer = store.loadActiveTimer(),
            perms = store.loadAiReadPermissions(),
            aiPrefs = store.loadAiPreferences()
        )
    }

    suspend fun processTurn(
        userText: String,
        currentDrafts: List<EditableProposalDraft>,
        data: FloatingChatSnapshotData,
        today: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        analyzer: suspend (
            userText: String,
            lastQuery: QueryContext?,
            adaptiveContext: AdaptivePromptContext?,
            visibleCloudTags: List<Tag>,
            pendingDraft: PendingProposalContext?
        ) -> ParseChatOutcome = { q, lq, ac, visTags, pending ->
            GeminiProposalClient.analyzeInput(
                userText = q,
                today = today,
                nowTime = nowTime,
                timezoneId = zone.id,
                optInEnabled = data.aiPrefs.optInEnabled,
                baseUrl = data.aiPrefs.baseUrl,
                apiKey = data.aiPrefs.apiKey,
                modelName = data.aiPrefs.modelName,
                trustedHost = data.aiPrefs.trustedHost,
                lastQuery = lq,
                adaptiveContext = ac,
                activeTags = visTags,
                pendingDraft = pending
            )
        }
    ): FloatingChatTurnResult {
        val openSnapshots = currentDrafts.map { it.toOpenSnapshot() }
        val capturedEpoch = epoch.current()
        val plan = coordinator.prepareChatTurn(
            userText = userText,
            today = today,
            nowTime = nowTime,
            zone = zone,
            perms = data.perms,
            lastQueryContext = lastQueryContext,
            records = data.records,
            tags = data.tags,
            goals = data.goals,
            tasks = data.tasks,
            habits = data.habits,
            habitLogs = data.habitLogs,
            activeTimer = data.activeTimer,
            openDrafts = openSnapshots
        )
        val pendingDraft = coordinator.currentConversationSnapshot(data.perms)?.pendingProposal
        val visibleCloudTags = if (data.perms.readLedger || data.perms.readGoals || data.perms.readTasks) {
            data.tags
        } else {
            emptyList()
        }

        val rawOutcome = analyzer(userText, plan.effectiveLastQuery, plan.adaptiveContext, visibleCloudTags, pendingDraft)
        val outcome = coordinator.finalizeChatTurn(
            userText = userText,
            plan = plan,
            outcome = rawOutcome,
            perms = data.perms,
            records = data.records,
            activeTimer = data.activeTimer,
            zone = zone,
            tags = data.tags,
            tasks = data.tasks,
            openDrafts = openSnapshots
        )

        if (outcome.fault != null) {
            val errMsg = FloatingChatMsg(
                isUser = false,
                text = if (outcome.source == OutcomeSource.NOT_CONFIGURED) {
                    "Chưa bật/cấu hình API key Wally Cloud AI trong Cài đặt. Bạn có thể mở app để nhập API key hoặc dùng To-do nhanh ở Bảng điều khiển."
                } else {
                    outcome.fault.messageVi
                }
            )
            return FloatingChatTurnResult(errMsg, currentDrafts, outcome)
        }

        val clearTagExplicit = WallyConversationEngine.containsExplicitClearTagCommand(userText)
        val newDrafts = outcome.proposals.map {
            EditableProposalDraft.fromProposal(it).withTagMatch(data.tags, explicitClear = clearTagExplicit)
        }
        val mergedDrafts = ProposalLifecycle.mergeOrReplaceIfCurrent(
            current = currentDrafts,
            incoming = newDrafts,
            replacesDraftId = outcome.replacesDraftId,
            idOf = { it.id },
            capturedEpoch = capturedEpoch,
            epoch = epoch
        ) ?: currentDrafts

        val answers = if (outcome.queries.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                outcome.queries.map { q ->
                    LedgerQueryEngine.execute(
                        intent = q,
                        records = data.records,
                        zoneId = zone,
                        nowEpochMs = nowEpochMs,
                        activeTimer = data.activeTimer,
                        budgets = data.budgets,
                        tags = data.tags,
                        goals = data.goals
                    )
                }
            }
        } else {
            emptyList()
        }

        outcome.queries.lastOrNull()?.let { q ->
            lastQueryContext = QueryContext(q.metric, q.period, q.category, q.tagId, q.tagName)
        }

        val card = WallyTurnPolicy.buildCard(
            question = userText,
            outcome = outcome,
            answers = answers,
            pendingDrafts = mergedDrafts.size,
            allowAdvisoryCard = outcome.usedAdaptiveContext
        )

        val replyText = when {
            card != null -> buildString {
                append(card.headline)
                card.lines.forEach { append('\n').append("• ").append(it) }
                card.notes.forEach { append('\n').append(it) }
            }
            !outcome.reply.isNullOrBlank() -> outcome.reply
            outcome.tagAssignmentProposals.isNotEmpty() ->
                "Wally đã đối chiếu phiên cần cập nhật Tag bên dưới — kiểm tra rồi bấm ✓ Duyệt gắn Tag."
            newDrafts.isNotEmpty() ->
                "Wally đã chuẩn bị ${newDrafts.size} hoạt động bên dưới — kiểm tra Tag/giờ và bấm ✓ Xác nhận."
            outcome.warnings.isNotEmpty() -> outcome.warnings.first()
            else -> GeminiProposalClient.SAFE_FALLBACK_WARNING_VI
        }

        val msg = FloatingChatMsg(
            isUser = false,
            text = replyText,
            badge = card?.label ?: if (outcome.source == OutcomeSource.CLOUD) "Wally AI" else null,
            isClarification = card?.isClarification == true,
            suggestions = card?.suggestions.orEmpty()
        )
        return FloatingChatTurnResult(msg, mergedDrafts, outcome)
    }

    fun updateDraftTag(
        drafts: List<EditableProposalDraft>,
        draftId: String,
        newTagId: String?,
        tags: List<Tag>,
        perms: AiReadPermissions
    ): List<EditableProposalDraft> {
        val active = tags.filter { !it.isArchived }
        val chosenTag = newTagId?.let { id -> active.firstOrNull { it.id == id } }
        val next = drafts.map { d ->
            if (d.id != draftId) d
            else d.copy(
                tagId = chosenTag?.id,
                categoryKey = chosenTag?.category?.key ?: d.categoryKey
            )
        }
        coordinator.syncOpenDrafts(next.map { it.toOpenSnapshot() }, perms, active)
        return next
    }

    fun removeDraft(
        drafts: List<EditableProposalDraft>,
        draftId: String,
        tags: List<Tag>,
        perms: AiReadPermissions
    ): List<EditableProposalDraft> {
        val next = drafts.filterNot { it.id == draftId }
        coordinator.syncOpenDrafts(next.map { it.toOpenSnapshot() }, perms, tags)
        return next
    }

    fun buildConfirmInput(draft: EditableProposalDraft): BatchRecordInput = draft.toBatchInput()

    fun persistSharedState(
        context: Context,
        drafts: List<EditableProposalDraft>,
        pendingTagAssignments: List<TagAssignmentProposal>,
        perms: AiReadPermissions,
        clarificationQuestion: String? = null,
        clarificationSuggestions: List<String> = emptyList(),
        newHistoryEntries: List<ChatHistoryEntry> = emptyList()
    ) {
        val store = TimerAndSettingsStore(context.applicationContext)
        store.saveChatProposalsDraft(
            drafts.map { d ->
                com.timewallet.mvp.data.local.SavedProposalDraft(
                    id = d.id,
                    rawInput = d.rawInput,
                    title = d.title,
                    categoryKey = d.categoryKey,
                    durationMinutesText = d.durationMinutesText,
                    dateLocal = d.dateLocal,
                    startLocal = d.startLocal,
                    endLocal = d.endLocal,
                    hasInitialTimeEvidence = d.hasInitialTimeEvidence,
                    isFuturePlanned = d.isFuturePlanned,
                    idempotencyKey = d.idempotencyKey,
                    tagId = d.tagId,
                    isMarkedWaste = d.isMarkedWaste,
                    taskId = d.taskId
                )
            },
            immediate = true
        )
        store.savePendingTagAssignments(pendingTagAssignments, immediate = true)
        store.saveChatConversationSnapshot(
            coordinator.exportPersistedSnapshot(perms, clarificationQuestion, clarificationSuggestions),
            immediate = true
        )
        if (newHistoryEntries.isNotEmpty()) {
            val mergedHistory = (store.loadChatHistory() + newHistoryEntries).takeLast(60)
            store.saveChatHistory(mergedHistory, immediate = true)
        }
        com.timewallet.mvp.data.local.WallyActionController.notifyAllWidgets(context.applicationContext)
    }

    fun loadPersistedDrafts(context: Context): List<EditableProposalDraft> {
        val store = TimerAndSettingsStore(context.applicationContext)
        return store.loadChatProposalsDraft().map { s ->
            EditableProposalDraft(
                id = s.id,
                rawInput = s.rawInput,
                title = s.title,
                categoryKey = s.categoryKey,
                durationMinutesText = s.durationMinutesText,
                dateLocal = s.dateLocal,
                startLocal = s.startLocal,
                endLocal = s.endLocal,
                hasInitialTimeEvidence = s.hasInitialTimeEvidence,
                isFuturePlanned = s.isFuturePlanned,
                idempotencyKey = s.idempotencyKey,
                tagId = s.tagId,
                isMarkedWaste = s.isMarkedWaste,
                taskId = s.taskId
            )
        }
    }

    suspend fun approveTagAssignment(
        context: Context,
        proposal: TagAssignmentProposal,
        zone: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): FloatingChatMsg {
        val appContext = context.applicationContext
        val db = TimeWalletDatabase.getInstance(appContext)
        val res = db.ledgerDao().updateRecordTagTransactionally(
            recordId = proposal.recordId,
            newTagId = proposal.newTagId,
            newCategoryKey = proposal.newCategoryKey
        )
        if (res != DbCommitOutcome.Saved) {
            val reason = (res as? DbCommitOutcome.InvalidEntityRejected)?.reason
                ?: "Không thể cập nhật Tag cho phiên này."
            return FloatingChatMsg(isUser = false, text = reason)
        }
        val freshData = loadContextData(appContext)
        val undoToken = TagAssignmentUndoToken(
            actionId = "undo_${proposal.id}",
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
        val ans = WallyTagAssignmentEngine.buildRecalculatedChatAnswer(
            token = undoToken,
            updatedRecords = freshData.records,
            tags = freshData.tags,
            budgets = freshData.budgets,
            goals = freshData.goals,
            activeTimer = freshData.activeTimer,
            zone = zone,
            nowEpochMs = nowEpochMs,
            wasUndone = false
        )
        val summaryText = buildString {
            append(ans.headline)
            ans.lines.forEach { append('\n').append("• ").append(it) }
        }
        return FloatingChatMsg(
            isUser = false,
            text = summaryText,
            badge = ans.label,
            undoToken = ans.undoToken
        )
    }

    suspend fun undoTagAssignment(
        context: Context,
        token: TagAssignmentUndoToken,
        zone: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): FloatingChatMsg {
        val appContext = context.applicationContext
        val db = TimeWalletDatabase.getInstance(appContext)
        val res = db.ledgerDao().updateRecordTagTransactionally(
            recordId = token.recordId,
            newTagId = token.previousTagId,
            newCategoryKey = token.previousCategoryKey
        )
        if (res != DbCommitOutcome.Saved) {
            val reason = (res as? DbCommitOutcome.InvalidEntityRejected)?.reason
                ?: "Không thể hoàn tác Tag."
            return FloatingChatMsg(isUser = false, text = reason)
        }
        val freshData = loadContextData(appContext)
        val ans = WallyTagAssignmentEngine.buildRecalculatedChatAnswer(
            token = token,
            updatedRecords = freshData.records,
            tags = freshData.tags,
            budgets = freshData.budgets,
            goals = freshData.goals,
            activeTimer = freshData.activeTimer,
            zone = zone,
            nowEpochMs = nowEpochMs,
            wasUndone = true
        )
        val summaryText = buildString {
            append(ans.headline)
            ans.lines.forEach { append('\n').append("• ").append(it) }
        }
        return FloatingChatMsg(
            isUser = false,
            text = summaryText,
            badge = ans.label,
            undoToken = ans.undoToken
        )
    }

    private fun EditableProposalDraft.toOpenSnapshot(): OpenChatDraftSnapshot = OpenChatDraftSnapshot(
        id = id,
        title = title,
        categoryKey = categoryKey,
        tagId = tagId,
        dateLocal = dateLocal,
        startLocal = startLocal.ifBlank { null },
        endLocal = endLocal.ifBlank { null },
        durationMinutes = durationMinutesText.trim().toIntOrNull(),
        isFuturePlanned = isFuturePlanned,
        taskId = taskId
    )

    companion object {
        /** Giữ lại token Hoàn tác còn hiệu lực khi nạp lại lịch sử chia sẻ sau persistSharedState. */
        fun mergeSyncedHistoryWithUndoTokens(
            mapped: List<FloatingChatMsg>,
            existing: List<FloatingChatMsg>
        ): List<FloatingChatMsg> {
            val activeUndoByText = existing
                .filter { it.undoToken != null && !it.undoToken.undone }
                .associateBy { it.text }
            if (activeUndoByText.isEmpty()) return mapped.takeLast(14)
            val attached = mapped.map { item ->
                val tok = activeUndoByText[item.text]?.undoToken
                if (tok != null) item.copy(undoToken = tok) else item
            }
            val attachedTexts = attached.mapTo(HashSet()) { it.text }
            val extra = existing.filter {
                it.undoToken != null && !it.undoToken.undone && it.text !in attachedTexts
            }
            return (attached + extra).takeLast(14)
        }
    }
}
