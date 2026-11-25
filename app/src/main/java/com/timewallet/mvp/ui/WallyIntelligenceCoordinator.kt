package com.timewallet.mvp.ui

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AdaptiveContextRouter
import com.timewallet.mvp.domain.usecase.AdaptivePromptContext
import com.timewallet.mvp.domain.usecase.CoachDigest
import com.timewallet.mvp.domain.usecase.CoachGuard
import com.timewallet.mvp.domain.usecase.ContextRouteMode
import com.timewallet.mvp.domain.usecase.ConversationState
import com.timewallet.mvp.domain.usecase.OpenChatDraftSnapshot
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.PendingProposalContext
import com.timewallet.mvp.domain.usecase.PersistedChatConversation
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.ReconciledChatOutcome
import com.timewallet.mvp.domain.usecase.TagAssignmentResolution
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.WallyContextEngine
import com.timewallet.mvp.domain.usecase.WallyConversationEngine
import com.timewallet.mvp.domain.usecase.WallyTagAssignmentEngine
import com.timewallet.mvp.domain.usecase.fingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Điều phối Wally Intelligence tách khỏi God-ViewModel (`TimeWalletViewModel`):
 * - Quyết định adaptive context qua [AdaptiveContextRouter] (không tốn thêm request model).
 * - Dựng ngữ cảnh giới hạn kích thước & tuân thủ nghiêm ngặt [AiReadPermissions] qua [WallyContextEngine].
 * - Quản lý [ConversationState] (hội thoại nối tiếp có TTL + tự vô hiệu hóa khi thu hồi quyền AI).
 * - Kết nối kết quả AI Coach đã kiểm chứng với Chat, điều phối làm rõ lịch nhiều lượt và phát hiện xung đột.
 */
class WallyIntelligenceCoordinator(
    private val nowEpochProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()

    @Volatile
    private var conversationState: ConversationState = ConversationState.EMPTY

    fun currentConversationSnapshot(perms: AiReadPermissions): ConversationState? {
        val now = nowEpochProvider()
        return synchronized(lock) {
            val s = conversationState
            if (s.isValidFor(now, perms)) s else null
        }
    }

    fun onPermissionsChanged(newPerms: AiReadPermissions) {
        synchronized(lock) {
            if (conversationState.permissionsFingerprint != newPerms.fingerprint()) {
                conversationState = ConversationState.EMPTY
            }
        }
    }

    fun onChatCleared() {
        synchronized(lock) {
            conversationState = ConversationState.EMPTY
        }
    }

    fun exportPersistedSnapshot(
        perms: AiReadPermissions,
        clarificationQuestion: String? = null,
        clarificationSuggestions: List<String> = emptyList()
    ): PersistedChatConversation? {
        val snap = currentConversationSnapshot(perms) ?: return null
        return PersistedChatConversation(
            pendingProposal = snap.pendingProposal,
            lastQuery = snap.lastQuery,
            focusTagId = snap.focusTag?.id,
            topicLabel = snap.topicLabel,
            focusCategoryKey = snap.focusCategory?.key,
            targetDateLocal = snap.targetDate?.toString(),
            isAdvisoryTopic = snap.isAdvisoryTopic,
            updatedAtEpochMs = snap.updatedAtEpochMs,
            permissionsFingerprint = snap.permissionsFingerprint,
            clarificationQuestion = clarificationQuestion,
            clarificationSuggestions = clarificationSuggestions
        )
    }

    fun restorePersistedSnapshot(
        persisted: PersistedChatConversation?,
        tags: List<Tag>,
        perms: AiReadPermissions
    ) {
        val now = nowEpochProvider()
        synchronized(lock) {
            if (persisted == null || persisted.permissionsFingerprint != perms.fingerprint()) {
                return
            }
            if (persisted.updatedAtEpochMs <= 0L || now - persisted.updatedAtEpochMs > ConversationState.DEFAULT_TTL_MS) {
                return
            }
            val current = conversationState
            if (current.isValidFor(now, perms) && current.updatedAtEpochMs > persisted.updatedAtEpochMs) {
                return
            }
            val restoredTag = persisted.focusTagId?.let { id -> tags.firstOrNull { !it.isArchived && it.id == id } }
            conversationState = ConversationState(
                isAdvisoryTopic = persisted.isAdvisoryTopic,
                topicLabel = persisted.topicLabel,
                focusCategory = persisted.focusCategoryKey?.let { TimeCategory.fromKeyOrNull(it) },
                targetDate = persisted.targetDateLocal?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                lastQuery = persisted.lastQuery,
                updatedAtEpochMs = persisted.updatedAtEpochMs,
                permissionsFingerprint = persisted.permissionsFingerprint,
                focusTag = restoredTag,
                pendingProposal = persisted.pendingProposal
            )
        }
    }

    /**
     * Đồng bộ trạng thái đề xuất đang mở (sửa Tag tay, đóng thẻ, hoặc sau khi xác nhận ghi sổ).
     * Khi chỉ có yêu cầu làm rõ chưa thành thẻ (`draftId == null`), giữ nguyên nếu danh sách thẻ hiện tại trống.
     */
    fun syncOpenDrafts(
        drafts: List<OpenChatDraftSnapshot>,
        perms: AiReadPermissions,
        tags: List<Tag> = emptyList(),
        nowMs: Long = nowEpochProvider()
    ) {
        synchronized(lock) {
            val base = if (conversationState.isValidFor(nowMs, perms)) conversationState else ConversationState.EMPTY
            val nextPending = when {
                drafts.size == 1 -> {
                    val d = drafts.first()
                    val tagObj = d.tagId?.let { id -> tags.firstOrNull { !it.isArchived && it.id == id } }
                    PendingProposalContext(
                        draftId = d.id,
                        title = d.title,
                        categoryKey = d.categoryKey,
                        tagId = d.tagId,
                        tagName = tagObj?.name,
                        dateLocal = d.dateLocal,
                        startLocal = d.startLocal,
                        endLocal = d.endLocal,
                        durationMinutes = d.durationMinutes,
                        isFuturePlanned = d.isFuturePlanned,
                        taskId = d.taskId,
                        explicitTagCleared = d.tagId == null
                    )
                }
                drafts.isEmpty() -> if (base.pendingProposal?.draftId != null) null else base.pendingProposal
                else -> null
            }
            val nextFocusTag = if (drafts.size == 1) {
                val id = drafts.first().tagId
                if (id != null) tags.firstOrNull { !it.isArchived && it.id == id } ?: base.focusTag else null
            } else {
                base.focusTag
            }
            conversationState = base.copy(
                pendingProposal = nextPending,
                focusTag = nextFocusTag,
                updatedAtEpochMs = if (base == ConversationState.EMPTY && nextPending == null) 0L else nowMs,
                permissionsFingerprint = perms.fingerprint()
            )
        }
    }

    fun buildCoachDigest(
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        goals: List<Goal>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        days: Int,
        nowMs: Long,
        zone: ZoneId
    ): CoachDigest = WallyContextEngine.buildFilteredCoachDigest(
        perms = perms,
        records = records,
        tags = tags,
        goals = goals,
        tasks = tasks,
        habits = habits,
        habitLogs = habitLogs,
        days = days,
        nowMs = nowMs,
        zone = zone
    )

    fun recordVerifiedCoachSummary(
        story: String,
        perms: AiReadPermissions,
        nowMs: Long = nowEpochProvider()
    ) {
        val compact = story.trim()
            .split(' ', '\n', '\r', '\t')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .take(180)
        if (compact.isEmpty()) return
        synchronized(lock) {
            val base = if (conversationState.isValidFor(nowMs, perms)) conversationState else ConversationState.EMPTY
            conversationState = base.copy(
                lastCoachSummary = compact,
                updatedAtEpochMs = nowMs,
                permissionsFingerprint = perms.fingerprint()
            )
        }
    }

    data class AdaptiveChatPlan(
        val route: com.timewallet.mvp.domain.usecase.ContextRouteDecision,
        val adaptiveContext: AdaptivePromptContext?,
        val effectiveLastQuery: QueryContext?,
        val startedAtEpochMs: Long,
        val today: LocalDate = LocalDate.now(),
        val nowTime: LocalTime = LocalTime.now()
    )

    suspend fun prepareChatTurn(
        userText: String,
        today: LocalDate,
        nowTime: LocalTime,
        zone: ZoneId,
        perms: AiReadPermissions,
        lastQueryContext: QueryContext?,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        goals: List<Goal>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        activeTimer: ActiveTimerSession?,
        openDrafts: List<OpenChatDraftSnapshot> = emptyList()
    ): AdaptiveChatPlan {
        val nowMs = nowEpochProvider()
        val convBefore = currentConversationSnapshot(perms)
        val singleDraft = openDrafts.singleOrNull()
        val effectiveConv = if (convBefore?.pendingProposal == null && singleDraft != null) {
            val draftTag = singleDraft.tagId?.let { id -> tags.firstOrNull { !it.isArchived && it.id == id } }
            val fallbackPending = PendingProposalContext(
                draftId = singleDraft.id,
                title = singleDraft.title,
                categoryKey = singleDraft.categoryKey,
                tagId = singleDraft.tagId,
                tagName = draftTag?.name,
                dateLocal = singleDraft.dateLocal,
                startLocal = singleDraft.startLocal,
                endLocal = singleDraft.endLocal,
                durationMinutes = singleDraft.durationMinutes,
                isFuturePlanned = singleDraft.isFuturePlanned,
                taskId = singleDraft.taskId
            )
            (convBefore ?: ConversationState.EMPTY).copy(
                focusTag = draftTag ?: convBefore?.focusTag,
                pendingProposal = fallbackPending,
                updatedAtEpochMs = nowMs,
                permissionsFingerprint = perms.fingerprint()
            )
        } else {
            convBefore
        }

        val route = AdaptiveContextRouter.decide(
            userText = userText,
            conversationState = effectiveConv,
            perms = perms,
            today = today,
            nowTime = nowTime,
            nowEpochMs = nowMs,
            tags = tags
        )

        val adaptiveCtx: AdaptivePromptContext? = if (route.mode == ContextRouteMode.ADAPTIVE) {
            withContext(Dispatchers.Default) {
                WallyContextEngine.buildAdaptiveChatContext(
                    route = route,
                    perms = perms,
                    records = records,
                    tags = tags,
                    goals = goals,
                    tasks = tasks,
                    habits = habits,
                    habitLogs = habitLogs,
                    activeTimer = activeTimer,
                    conversationState = effectiveConv,
                    today = today,
                    nowEpochMs = nowMs,
                    zone = zone
                )
            }
        } else {
            null
        }

        return AdaptiveChatPlan(
            route = route,
            adaptiveContext = adaptiveCtx,
            effectiveLastQuery = effectiveConv?.lastQuery ?: lastQueryContext,
            startedAtEpochMs = nowMs,
            today = today,
            nowTime = nowTime
        )
    }

    fun finalizeChatTurn(
        userText: String,
        plan: AdaptiveChatPlan,
        outcome: ParseChatOutcome,
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        zone: ZoneId,
        tags: List<Tag> = emptyList(),
        tasks: List<TaskItem> = emptyList(),
        openDrafts: List<OpenChatDraftSnapshot> = emptyList()
    ): ParseChatOutcome {
        if (outcome.fault != null) return outcome

        when (
            val tagAssignRes = WallyTagAssignmentEngine.resolve(
                userText = userText,
                rawIntents = outcome.rawTagAssignments,
                perms = perms,
                records = records,
                tags = tags,
                today = plan.today,
                zone = zone
            )
        ) {
            is TagAssignmentResolution.Single -> {
                return outcome.copy(
                    proposals = emptyList(),
                    tagAssignmentProposals = listOf(tagAssignRes.proposal),
                    reply = null,
                    pendingClarification = null,
                    clarificationSuggestions = emptyList()
                )
            }
            is TagAssignmentResolution.Ambiguous -> {
                return outcome.copy(
                    proposals = emptyList(),
                    tagAssignmentProposals = emptyList(),
                    reply = tagAssignRes.question,
                    pendingClarification = null,
                    clarificationSuggestions = tagAssignRes.suggestions
                )
            }
            is TagAssignmentResolution.NoChange -> {
                return outcome.copy(
                    proposals = emptyList(),
                    tagAssignmentProposals = emptyList(),
                    reply = tagAssignRes.message,
                    pendingClarification = null,
                    clarificationSuggestions = emptyList()
                )
            }
            is TagAssignmentResolution.NotFound -> {
                return outcome.copy(
                    proposals = emptyList(),
                    tagAssignmentProposals = emptyList(),
                    reply = tagAssignRes.message,
                    pendingClarification = null,
                    clarificationSuggestions = emptyList()
                )
            }
            is TagAssignmentResolution.PermissionDenied -> {
                return outcome.copy(
                    proposals = emptyList(),
                    tagAssignmentProposals = emptyList(),
                    reply = tagAssignRes.message,
                    pendingClarification = null,
                    clarificationSuggestions = emptyList()
                )
            }
            TagAssignmentResolution.NotApplicable -> Unit
        }

        val convSnap = currentConversationSnapshot(perms)
        val reconciled = WallyConversationEngine.reconcileTurnOutcome(
            userText = userText,
            rawOutcome = outcome,
            route = plan.route,
            adaptiveContext = plan.adaptiveContext,
            conversationState = convSnap,
            openDrafts = openDrafts,
            tags = tags,
            tasks = tasks,
            today = plan.today,
            nowTime = plan.nowTime
        )

        val adaptiveCtx = plan.adaptiveContext
        val conflictWarnings = if (reconciled.outcome.proposals.isNotEmpty() && adaptiveCtx != null) {
            WallyContextEngine.detectScheduleConflictWarnings(
                proposals = reconciled.outcome.proposals,
                perms = perms,
                records = records,
                activeTimer = activeTimer,
                nowEpochMs = nowEpochProvider(),
                zone = zone
            )
        } else {
            emptyList()
        }

        updateConversationAfterSuccess(
            userText = userText,
            route = plan.route,
            adaptiveCtx = adaptiveCtx,
            reconciled = reconciled,
            tags = tags,
            perms = perms,
            nowMs = nowEpochProvider()
        )

        val enriched = reconciled.outcome.copy(
            pendingClarification = reconciled.pendingClarification,
            clarificationSuggestions = reconciled.suggestionChips,
            replacesDraftId = reconciled.replacesDraftId
        )
        return if (conflictWarnings.isEmpty()) enriched
        else enriched.copy(warnings = (enriched.warnings + conflictWarnings).distinct())
    }

    private fun updateConversationAfterSuccess(
        userText: String,
        route: com.timewallet.mvp.domain.usecase.ContextRouteDecision,
        adaptiveCtx: AdaptivePromptContext?,
        reconciled: ReconciledChatOutcome,
        tags: List<Tag>,
        perms: AiReadPermissions,
        nowMs: Long
    ) {
        synchronized(lock) {
            val outcome = reconciled.outcome
            val base = if (conversationState.isValidFor(nowMs, perms)) conversationState else ConversationState.EMPTY
            val newLastQuery = outcome.queries.lastOrNull()?.let {
                QueryContext(it.metric, it.period, it.category, it.tagId, it.tagName)
            } ?: base.lastQuery
            val firstProposal = outcome.proposals.firstOrNull()
            val proposalTag = firstProposal?.tagId?.let { id -> tags.firstOrNull { !it.isArchived && it.id == id } }
            val queryTag = outcome.queries.lastOrNull()?.tagId?.let { id -> tags.firstOrNull { !it.isArchived && it.id == id } }
            val nextFocusTag = when {
                reconciled.explicitTagCleared -> null
                proposalTag != null -> proposalTag
                reconciled.pendingClarification?.tagId != null -> tags.firstOrNull {
                    !it.isArchived && it.id == reconciled.pendingClarification.tagId
                } ?: route.focusTag ?: base.focusTag
                route.focusTag != null -> route.focusTag
                queryTag != null -> queryTag
                else -> base.focusTag
            }
            val inferredCategory = nextFocusTag?.category
                ?: route.focusCategory
                ?: firstProposal?.category?.let { TimeCategory.fromKeyOrDefault(it) }
                ?: outcome.queries.lastOrNull()?.category
                ?: base.focusCategory
            val topic = when {
                firstProposal != null -> firstProposal.title
                reconciled.pendingClarification != null -> reconciled.pendingClarification.title
                adaptiveCtx != null -> adaptiveCtx.goalDeficits.firstOrNull()?.goalLabel
                    ?: nextFocusTag?.name
                    ?: inferredCategory?.displayName
                    ?: userText.trim().take(50)
                outcome.queries.isNotEmpty() -> queryTag?.name ?: outcome.queries.last().category?.displayName ?: base.topicLabel
                else -> base.topicLabel
            }
            val nextPending = when {
                reconciled.pendingClarification != null -> reconciled.pendingClarification
                outcome.proposals.size == 1 && firstProposal != null -> PendingProposalContext(
                    draftId = firstProposal.id,
                    title = firstProposal.title,
                    categoryKey = firstProposal.category,
                    tagId = firstProposal.tagId,
                    tagName = firstProposal.tagName,
                    dateLocal = firstProposal.dateLocal,
                    startLocal = firstProposal.startLocal,
                    endLocal = firstProposal.endLocal,
                    durationMinutes = firstProposal.durationMinutes,
                    isFuturePlanned = firstProposal.isFuturePlanned,
                    taskId = firstProposal.taskId,
                    explicitTagCleared = reconciled.explicitTagCleared
                )
                outcome.queries.isNotEmpty() && outcome.proposals.isEmpty() -> base.pendingProposal
                else -> null
            }
            val nextAdvisory = route.mode == ContextRouteMode.ADAPTIVE || nextPending != null
            val nextDate = nextPending?.dateLocal?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: adaptiveCtx?.targetDate
                ?: route.targetDate
                ?: base.targetDate
            val nextWindow = adaptiveCtx?.dayWindow ?: if (route.mode == ContextRouteMode.ADAPTIVE) route.dayWindow else base.dayWindow
            val nextSlots = adaptiveCtx?.feasibleSlots ?: base.candidateSlots
            val nextHeadline = outcome.reply?.take(140) ?: base.lastRecommendationHeadline

            conversationState = base.copy(
                isAdvisoryTopic = nextAdvisory,
                topicLabel = topic,
                focusCategory = inferredCategory,
                targetDate = nextDate,
                dayWindow = nextWindow,
                lastQuery = newLastQuery,
                lastRecommendationHeadline = nextHeadline,
                candidateSlots = nextSlots,
                updatedAtEpochMs = nowMs,
                permissionsFingerprint = perms.fingerprint(),
                focusTag = nextFocusTag,
                pendingProposal = nextPending
            )
        }
    }
}
