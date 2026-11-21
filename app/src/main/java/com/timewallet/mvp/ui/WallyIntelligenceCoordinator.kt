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
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.WallyContextEngine
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
 * - Kết nối kết quả AI Coach đã kiểm chứng với Chat và kiểm tra xung đột lịch trước khi đề xuất cho người dùng.
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
        val startedAtEpochMs: Long
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
        activeTimer: ActiveTimerSession?
    ): AdaptiveChatPlan {
        val nowMs = nowEpochProvider()
        val convBefore = currentConversationSnapshot(perms)
        val route = AdaptiveContextRouter.decide(
            userText = userText,
            conversationState = convBefore,
            perms = perms,
            today = today,
            nowTime = nowTime,
            nowEpochMs = nowMs
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
                    conversationState = convBefore,
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
            effectiveLastQuery = convBefore?.lastQuery ?: lastQueryContext,
            startedAtEpochMs = nowMs
        )
    }

    fun finalizeChatTurn(
        userText: String,
        plan: AdaptiveChatPlan,
        outcome: ParseChatOutcome,
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        zone: ZoneId
    ): ParseChatOutcome {
        val adaptiveCtx = plan.adaptiveContext
        val conflictWarnings = if (outcome.fault == null && outcome.proposals.isNotEmpty() && adaptiveCtx != null) {
            WallyContextEngine.detectScheduleConflictWarnings(
                proposals = outcome.proposals,
                perms = perms,
                records = records,
                activeTimer = activeTimer,
                nowEpochMs = nowEpochProvider(),
                zone = zone
            )
        } else {
            emptyList()
        }

        if (outcome.fault == null) {
            updateConversationAfterSuccess(
                userText = userText,
                route = plan.route,
                adaptiveCtx = adaptiveCtx,
                outcome = outcome,
                perms = perms,
                nowMs = nowEpochProvider()
            )
        }

        return if (conflictWarnings.isEmpty()) outcome
        else outcome.copy(warnings = (outcome.warnings + conflictWarnings).distinct())
    }

    private fun updateConversationAfterSuccess(
        userText: String,
        route: com.timewallet.mvp.domain.usecase.ContextRouteDecision,
        adaptiveCtx: AdaptivePromptContext?,
        outcome: ParseChatOutcome,
        perms: AiReadPermissions,
        nowMs: Long
    ) {
        synchronized(lock) {
            val base = if (conversationState.isValidFor(nowMs, perms)) conversationState else ConversationState.EMPTY
            val newLastQuery = outcome.queries.lastOrNull()?.let { QueryContext(it.metric, it.period, it.category) } ?: base.lastQuery
            val inferredCategory = route.focusCategory
                ?: outcome.proposals.firstOrNull()?.category?.let { TimeCategory.fromKeyOrDefault(it) }
                ?: outcome.queries.lastOrNull()?.category
                ?: base.focusCategory
            val topic = when {
                adaptiveCtx != null -> adaptiveCtx.goalDeficits.firstOrNull()?.goalLabel
                    ?: inferredCategory?.displayName
                    ?: userText.trim().take(50)
                outcome.proposals.isNotEmpty() -> outcome.proposals.first().title
                outcome.queries.isNotEmpty() -> outcome.queries.last().category?.displayName ?: base.topicLabel
                else -> base.topicLabel
            }
            val nextAdvisory = route.mode == ContextRouteMode.ADAPTIVE
            val nextDate = adaptiveCtx?.targetDate ?: route.targetDate ?: base.targetDate
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
                permissionsFingerprint = perms.fingerprint()
            )
        }
    }
}
