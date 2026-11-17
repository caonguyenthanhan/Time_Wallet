package com.timewallet.mvp.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.GuardHealth
import com.timewallet.mvp.data.local.ActivityReviewNoteEntity
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.BackupParseResult
import com.timewallet.mvp.data.local.BackupRestoreMode
import com.timewallet.mvp.data.local.BatchOverlapStep
import com.timewallet.mvp.data.local.BatchProposalPlanner
import com.timewallet.mvp.data.local.CategoryBudgetEntity
import com.timewallet.mvp.data.local.DailyReviewEntity
import com.timewallet.mvp.data.local.DbCommitOutcome
import com.timewallet.mvp.data.local.LedgerBackupRestorer
import com.timewallet.mvp.data.local.LedgerJsonBackupParser
import com.timewallet.mvp.data.local.LedgerJsonExporter
import com.timewallet.mvp.data.local.ParsedBackupPayload
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.data.local.ManualEntryDraft
import com.timewallet.mvp.data.local.GoalEntity
import com.timewallet.mvp.data.local.TagEntity
import com.timewallet.mvp.data.local.TimeWalletDatabase
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.local.TimerInputDraft
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.usecase.ActivityBreakEngine
import com.timewallet.mvp.domain.usecase.BreakDecision
import com.timewallet.mvp.domain.usecase.CoachActionItem
import com.timewallet.mvp.domain.usecase.CoachActionKind
import com.timewallet.mvp.domain.usecase.CoachBrief
import com.timewallet.mvp.domain.usecase.CoachCheck
import com.timewallet.mvp.domain.usecase.CoachDigestBuilder
import com.timewallet.mvp.domain.usecase.CoachGuard
import com.timewallet.mvp.domain.usecase.CoachStructuredCheck
import com.timewallet.mvp.domain.usecase.CoachSuggestion
import com.timewallet.mvp.domain.usecase.ConfirmationActionResult
import com.timewallet.mvp.domain.usecase.ConfirmationEngine
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.domain.usecase.TimerSegments
import com.timewallet.mvp.data.local.TimerCommit
import com.timewallet.mvp.data.local.TimerCommitResult
import com.timewallet.mvp.notifications.AndroidCompletionReminderScheduler
import com.timewallet.mvp.notifications.ReminderScheduler
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.DailyReviewSnapshot
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.CognitiveState
import com.timewallet.mvp.domain.usecase.DailyReviewAssistant
import com.timewallet.mvp.domain.usecase.DayLedgerStats
import com.timewallet.mvp.domain.usecase.DiceActionKind
import com.timewallet.mvp.domain.usecase.DiceRollResult
import com.timewallet.mvp.domain.usecase.DopamineTimeDiceEngine
import com.timewallet.mvp.domain.usecase.FaceProbabilityItem
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.ChatAnalysisFeedback
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.ChatEvent
import com.timewallet.mvp.domain.usecase.ChatPhase
import com.timewallet.mvp.domain.usecase.ChatPhaseMachine
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.BudgetPolicy
import com.timewallet.mvp.domain.usecase.DerivedStatsCalculator
import com.timewallet.mvp.domain.usecase.CancelArming
import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.CancelOutcome
import com.timewallet.mvp.domain.usecase.DraftRef
import com.timewallet.mvp.domain.usecase.ProposalLifecycle
import com.timewallet.mvp.domain.usecase.ChatAnswer
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.WallyAnswerBuilder
import com.timewallet.mvp.domain.usecase.QueryAnswer
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.ChatAnalysisGuard
import com.timewallet.mvp.domain.usecase.ConfirmFeedback
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ManualEntryValidation
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.domain.usecase.WeeklyInsightsSummary
import androidx.room.withTransaction
import com.timewallet.mvp.data.diagnostics.AiDiagnostics
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.diagnostics.CrashRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class AppTab(val routeKey: String, val label: String, val iconSymbol: String) {
    OVERVIEW("overview", "Tổng quan", "⌂"),
    TIMER("timer", "Đồng hồ", "◴"),
    CHAT("chat", "Wally", "✦"),
    HISTORY("history", "Nhật ký", "☷"),
    INSIGHTS("insights", "Phân tích", "▥")
}

enum class HistoryFilter(val label: String) {
    ALL("Tất cả"),
    ACTUAL_TIMED("Thực tế có giờ"),
    UNTIMED("Chưa gắn giờ"),
    ACTIVE_OR_PLANNED("Đang diễn ra / Dự kiến")
}

data class EditableProposalDraft(
    val id: String,
    val rawInput: String,
    val title: String,
    val categoryKey: String,
    val durationMinutesText: String,
    val dateLocal: String,
    val startLocal: String,
    val endLocal: String,
    val hasInitialTimeEvidence: Boolean,
    val isFuturePlanned: Boolean,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    /** v1.1.0: Tag của người dùng khớp TIÊU ĐỀ đề xuất (khớp xác định, không dùng AI). null = không gắn. */
    val tagId: String? = null
) {
    /** Gắn Tag khớp tiêu đề; Tag là cách phân loại của người dùng nên Category theo Tag (người dùng vẫn sửa được ở khung sửa). */
    fun withTagMatch(tags: List<Tag>): EditableProposalDraft {
        val t = TagRules.matchTitle(title, tags) ?: return this
        return copy(tagId = t.id, categoryKey = t.category.key)
    }

    val durationMinutes: Int
        get() = durationMinutesText.trim().toIntOrNull()?.coerceIn(1, 1440) ?: 1

    companion object {
        fun fromProposal(p: ActivityProposal): EditableProposalDraft {
            return EditableProposalDraft(
                id = p.id,
                rawInput = p.rawInput,
                title = p.title,
                categoryKey = p.category,
                durationMinutesText = p.durationMinutes.toString(),
                dateLocal = p.dateLocal,
                startLocal = p.startLocal ?: "",
                endLocal = p.endLocal ?: "",
                hasInitialTimeEvidence = p.timeEvidence == "explicit_range",
                isFuturePlanned = p.isFuturePlanned
            )
        }
    }
}

/**
 * WALLY P1: trạng thái form nhập giờ thủ công. `sessionId` sinh MỘT lần khi mở form và dùng làm id + idempotencyKey,
 * nên bấm Lưu nhiều lần chỉ ghi một bản ghi; mở form lần khác là bản ghi mới (hai hoạt động giống nhau vẫn ghi riêng).
 */
data class ManualEntryState(
    val sessionId: String = UUID.randomUUID().toString(),
    val categoryKey: String? = null,
    val hoursText: String = "",
    val minutesText: String = "",
    val dateLocal: String = LocalDate.now().toString(),
    val note: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    // v1.1.0 (A): hai cách nhập — Khoảng giờ (tự tính phút, có "Bây giờ") và Thời lượng; Tag cá nhân; tìm/tạo Tag ngay trong form.
    val mode: ManualMode = ManualMode.DURATION,
    val tagId: String? = null,
    val tagQuery: String = "",
    val startText: String = "",
    val endText: String = "",
    val endIsNow: Boolean = false,
    val creatingTag: Boolean = false,
    val newTagName: String = "",
    val saveAsPlanned: Boolean = false
) {
    fun hasUserData(todayLocal: String = LocalDate.now().toString()): Boolean =
        categoryKey != null ||
            tagId != null ||
            tagQuery.isNotBlank() ||
            hoursText.isNotBlank() ||
            minutesText.isNotBlank() ||
            startText.isNotBlank() ||
            endText.isNotBlank() ||
            endIsNow ||
            saveAsPlanned ||
            note.isNotBlank() ||
            dateLocal != todayLocal

    fun isPersistable(todayLocal: String = LocalDate.now().toString()): Boolean =
        hasUserData(todayLocal) || mode != ManualMode.DURATION

    fun toDraft(nowMs: Long = System.currentTimeMillis()): ManualEntryDraft = ManualEntryDraft(
        sessionId = sessionId,
        categoryKey = categoryKey,
        hoursText = hoursText,
        minutesText = minutesText,
        dateLocal = dateLocal,
        note = note,
        modeName = mode.name,
        tagId = tagId,
        tagQuery = tagQuery,
        startText = startText,
        endText = endText,
        endIsNow = endIsNow,
        saveAsPlanned = saveAsPlanned,
        updatedAtEpochMs = nowMs
    )
}

enum class ManualMode { RANGE, DURATION }

data class PendingOverlapConflict(
    val incoming: LedgerRecord,
    val conflicts: List<LedgerRecord>,
    val clearedDraftIds: List<String> = emptyList(),
    val remainingChatDraftIds: List<String> = emptyList(),
    val priorSavedCount: Int = 0,
    val priorSkippedDuplicates: Int = 0,
    val closeManualOnResolve: Boolean = false,
    val closeEditOnResolve: Boolean = false,
    val finishTimerOnResolve: Boolean = false
)

/** v1.1.0 (D): trạng thái thẻ AI Time Coach. `facts` do ỨNG DỤNG viết (luôn có); `story` chỉ có khi AI trả lời và qua [CoachGuard]. */
data class CoachUiState(
    val loading: Boolean = false,
    val rangeDays: Int = 1,
    val facts: List<String> = emptyList(),
    val story: String? = null,
    val brief: CoachBrief? = null,
    val suggestions: List<CoachSuggestion> = emptyList(),
    val note: String? = null,
    val requested: Boolean = false
)

/** v1.1.0 (B): màn Quản lý Tag. */
data class TagManagerState(
    val newName: String = "",
    val newCategoryKey: String? = null,
    val renamingId: String? = null,
    val renameText: String = "",
    val recategorizingId: String? = null,
    val showArchived: Boolean = false,
    val error: String? = null
)

/** v1.1.0 (C): trình soạn mục tiêu. `editingGroupId` = null khi tạo mới. */
data class GoalEditorState(
    val editingGroupId: String? = null,
    val targetType: GoalTargetType = GoalTargetType.TAG,
    val targetKey: String? = null,
    val kind: GoalKind = GoalKind.MIN,
    val period: GoalPeriod = GoalPeriod.DAY,
    val hoursText: String = "",
    val minutesText: String = "30",
    val error: String? = null
)

data class ImportBackupUiState(
    val rawJson: String = "",
    val parsedPayload: ParsedBackupPayload? = null,
    val errorMessage: String? = null,
    val isRestoring: Boolean = false
)

data class TimeWalletUiState(
    val currentTab: AppTab = AppTab.OVERVIEW,
    val todayDateLocal: String = LocalDate.now().toString(),
    val records: List<LedgerRecord> = emptyList(),
    val todayStats: DayLedgerStats = LedgerAnalyticsCalculator.computeDayStats(
        LocalDate.now().toString(),
        emptyList()
    ),
    val weeklyInsights: WeeklyInsightsSummary = LedgerAnalyticsCalculator.computeWeeklyInsights(
        LocalDate.now().toString(),
        emptyList()
    ),
    val activeTimer: ActiveTimerSession? = null,
    val timerElapsedSeconds: Long = 0L,
    val timerClockSkewDetected: Boolean = false,
    val timerInputTitle: String = "Làm việc tập trung",
    val timerInputCategoryKey: String = TimeCategory.WORK.key,
    /** v1.1.0: Tag đang chọn cho phiên đo sắp bắt đầu (null = không gắn). */
    val timerInputTagId: String? = null,
    val diceCognitiveState: CognitiveState = CognitiveState.HIGH_ENERGY_BUILDUP,
    val diceFaceProbabilities: List<FaceProbabilityItem> = DopamineTimeDiceEngine
        .computeProbabilities(
            DopamineTimeDiceEngine.deriveContextFromRecords(
                todayDateLocal = LocalDate.now().toString(),
                records = emptyList()
            )
        ).second,
    val isRollingDice: Boolean = false,
    val rollingPreviewFaceNumber: Int = 1,
    val lastDiceRoll: DiceRollResult? = null,
    val consecutiveBreakRolls: Int = 0,
    val chatInputText: String = "",
    val isAnalyzingChat: Boolean = false,
    val chatPhase: ChatPhase = ChatPhase.IDLE,
    val chatFault: AiFault? = null,
    val lastAnalysisSource: OutcomeSource? = null,
    val lastRequestId: String? = null,
    val chatAnswer: ChatAnswer? = null,
    val lastQueryContext: QueryContext? = null,
    val proposals: List<EditableProposalDraft> = emptyList(),
    val expandedProposalId: String? = null,
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val aiPreferences: AiPreferences = AiPreferences(),
    val budgets: List<CategoryTimeBudget> = emptyList(),
    val budgetProgressList: List<TimeBudgetProgress> = emptyList(),
    val dashboardPriorityBudgets: List<TimeBudgetProgress> = emptyList(),
    val isShowingDailyReview: Boolean = false,
    val selectedReviewDateLocal: String = LocalDate.now().toString(),
    val dailyReviews: List<DailyReviewRecord> = emptyList(),
    val activityNotes: List<ActivityReviewNote> = emptyList(),
    val currentDailyReviewSnapshot: DailyReviewSnapshot = DailyReviewAssistant.buildSnapshotForDate(
        dateLocal = LocalDate.now().toString(),
        records = emptyList(),
        budgets = emptyList(),
        savedReview = null,
        activityNotes = emptyList()
    ),
    val showBudgetDialog: Boolean = false,
    val editingBudgetCategory: TimeCategory? = null,
    val editingRecord: LedgerRecord? = null,
    val exportedJsonContent: String? = null,
    val importBackup: ImportBackupUiState? = null,
    val showProductInfoDialog: Boolean = false,
    val showAiSettingsDialog: Boolean = false,
    val manualEntry: ManualEntryState? = null,
    val pendingManualDraft: ManualEntryDraft? = null,
    // v1.1.0: Tag cá nhân, Mục tiêu (mọi phiên bản), Quản lý Tag, Mục tiêu, AI Coach.
    val tags: List<Tag> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val tagManager: TagManagerState? = null,
    val showGoals: Boolean = false,
    val goalEditor: GoalEditorState? = null,
    val coach: CoachUiState = CoachUiState(),
    val confirmingRecord: LedgerRecord? = null,
    val pendingOverlapConflict: PendingOverlapConflict? = null,
    val requestNotificationPermissionNonce: Int = 0,
    val lastCrashReport: String? = null,
    val aiConnectionTestMessage: String? = null,
    val aiConnectionTesting: Boolean = false,
    val aiDiagnosticsText: String = "",
    val toastMessage: String? = null
)

class TimeWalletViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TimeWalletDatabase.getInstance(application)
    private val ledgerDao = database.ledgerDao()
    private val budgetDao = database.budgetDao()
    private val dailyReviewDao = database.dailyReviewDao()
    private val tagDao = database.tagDao()
    private val goalDao = database.goalDao()
    private val settingsStore = TimerAndSettingsStore(application)
    private val reminderScheduler: ReminderScheduler = AndroidCompletionReminderScheduler(application)

    private val confirmGuard = ChatAnalysisGuard()
    private val manualSaveGuard = ChatAnalysisGuard()
    private val timerStopGuard = ChatAnalysisGuard()

    private val _uiState = MutableStateFlow(TimeWalletUiState())
    val uiState: StateFlow<TimeWalletUiState> = _uiState.asStateFlow()

    private var toastJob: Job? = null

    // WALLY F6: ngoại lệ chưa bắt trong coroutine (Room/SQLite...) không được giết tiến trình âm thầm:
    // ghi báo cáo đã che nội dung, báo người dùng, KHÔNG nuốt im lặng. Hủy coroutine không phải lỗi.
    private val safeHandler = CoroutineExceptionHandler { _, error ->
        if (error is CancellationException) return@CoroutineExceptionHandler
        CrashRecorder.record(error)
        _uiState.update { it.copy(lastCrashReport = CrashRecorder.readLast()) }
        showToast("Có lỗi không mong muốn. Dữ liệu đã lưu không bị ảnh hưởng; xem \"Nhật ký lỗi\" trong mục Thông tin.")
    }

    // v1.0.6 (F-V105-04): lượt phân tích chat theo vé sở hữu; tính số liệu ngoài luồng chính.
    private val chatRunner = WallyChatRunner(
        scope = viewModelScope,
        state = _uiState,
        computeAnswers = { basis, intents ->
            intents.map {
                LedgerQueryEngine.execute(
                    intent = it,
                    records = basis.records,
                    zoneId = ZoneId.systemDefault(),
                    nowEpochMs = System.currentTimeMillis(),
                    activeTimer = basis.timer,
                    budgets = basis.budgets
                )
            }
        },
        toast = { showToast(it) },
        onUnexpected = { e ->
            CrashRecorder.record(e)
            _uiState.update { it.copy(lastCrashReport = CrashRecorder.readLast()) }
            showToast("Có lỗi không mong muốn. Dữ liệu đã lưu không bị ảnh hưởng; xem \"Nhật ký lỗi\" trong mục Thông tin.")
        },
        launchContext = safeHandler,
        // v1.0.13 (F4): nút Hủy chỉ có hiệu lực sau CancelArming.DEFAULT_ARM_MS kể từ lúc gửi (bấm đúp Gửi không tự hủy).
        cancelArming = CancelArming(),
        onChatInputConsumed = { settingsStore.saveChatInputDraft("") }
    )

    // v1.0.9: khởi tạo guard NGOÀI luồng chính ngay khi mở app (không bao giờ ném); lần gửi đầu không phải biên dịch regex trên Main.
    init {
        viewModelScope.launch(safeHandler) { kotlinx.coroutines.withContext(Dispatchers.Default) { GuardHealth.check() } }
    }

    init {
        val restoredTimer = try {
            settingsStore.loadActiveTimer()
        } catch (e: Exception) {
            CrashRecorder.record(e)
            null
        }
        val aiPrefs = try {
            settingsStore.loadAiPreferences()
        } catch (e: Exception) {
            CrashRecorder.record(e)
            AiPreferences(keyUnavailable = true)
        }
        val restoredManualDraft = runCatching { settingsStore.loadManualEntryDraft() }.getOrNull()
        val restoredChatDraft = runCatching { settingsStore.loadChatInputDraft() }.getOrDefault("")
        val restoredTimerDraft = runCatching { settingsStore.loadTimerInputDraft() }.getOrDefault(TimerInputDraft())
        _uiState.update { state ->
            state.copy(
                lastCrashReport = CrashRecorder.readLast(),
                activeTimer = restoredTimer,
                timerInputTitle = restoredTimer?.title ?: restoredTimerDraft.title,
                timerInputCategoryKey = restoredTimer?.categoryKey ?: restoredTimerDraft.categoryKey,
                timerInputTagId = if (restoredTimer == null) restoredTimerDraft.tagId else restoredTimer.tagId,
                chatInputText = restoredChatDraft,
                pendingManualDraft = restoredManualDraft,
                aiPreferences = aiPrefs
            )
        }

        // v1.0.6 (R01): app có thể bị tắt SAU khi phiên đo đã được ghi nhưng TRƯỚC khi phiên bị xóa. Khi mở lại, nếu sổ cái đã có
        // phiên này (cùng khóa chống ghi trùng) thì chỉ dọn phiên, không bắt người dùng lưu lần nữa và không ghi trùng.
        restoredTimer?.let { t ->
            viewModelScope.launch(safeHandler) {
                if (TimerCommit.isCommitted(ledgerDao, t.startedAtEpochMs)) {
                    finishTimerAfterCommit()
                    showToast("Phiên đo trước đã được ghi vào sổ cái từ lần mở trước.")
                }
            }
        }

        // v1.2.0: tự động đồng bộ mọi thay đổi của chatInputText (gõ tay, áp gợi ý, lệnh hủy hoặc AI tiêu thụ) xuống SharedPreferences.
        viewModelScope.launch(safeHandler) {
            _uiState.map { it.chatInputText }.distinctUntilChanged().drop(1).collect { text ->
                settingsStore.saveChatInputDraft(text)
            }
        }

        // Quan sát toàn bộ sổ cái từ Room (bảo toàn dữ liệu cũ)
        viewModelScope.launch(safeHandler) {
            ledgerDao.observeAllRecords().collect { entities ->
                val domainRecords = entities.map { it.toDomain() }
                reminderScheduler.syncReminders(domainRecords, System.currentTimeMillis())
                recomputeStats(records = domainRecords)
            }
        }

        // Quan sát toàn bộ ngân sách danh mục từ Room (không tự tạo mục tiêu mặc định)
        viewModelScope.launch(safeHandler) {
            budgetDao.observeAllBudgets().collect { budgetEntities ->
                val domainBudgets = budgetEntities.map { it.toDomain() }
                recomputeStats(records = _uiState.value.records, budgets = domainBudgets)
            }
        }

        // Quan sát toàn bộ Daily Review từ Room
        viewModelScope.launch(safeHandler) {
            dailyReviewDao.observeAllDailyReviews().collect { reviewEntities ->
                val domainReviews = reviewEntities.map { it.toDomain() }
                recomputeStats(
                    records = _uiState.value.records,
                    budgets = _uiState.value.budgets,
                    dailyReviews = domainReviews
                )
            }
        }

        // Quan sát toàn bộ ghi chú kết quả & giải thích Feynman theo hoạt động từ Room
        viewModelScope.launch(safeHandler) {
            dailyReviewDao.observeAllActivityNotes().collect { noteEntities ->
                val domainNotes = noteEntities.map { it.toDomain() }
                recomputeStats(
                    records = _uiState.value.records,
                    budgets = _uiState.value.budgets,
                    activityNotes = domainNotes
                )
            }
        }

        // v1.1.0: Tag cá nhân và mục tiêu (mọi phiên bản) từ Room.
        viewModelScope.launch(safeHandler) {
            tagDao.observeAll().collect { entities -> _uiState.update { it.copy(tags = entities.map(TagEntity::toDomain)) } }
        }
        viewModelScope.launch(safeHandler) {
            goalDao.observeAll().collect { entities -> _uiState.update { it.copy(goals = entities.map(GoalEntity::toDomain)) } }
        }

        // v1.0.6: vòng cập nhật đồng hồ KHÔNG chạy ngầm; chỉ chạy khi màn hình đang hiện (onAppResumed ... onAppPaused).
    }

    private var tickerJob: Job? = null

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch(safeHandler) {
            var tickCounter = 0
            while (isActive) {
                refreshTimerTick()
                tickCounter++
                if (tickCounter % 15 == 0) {
                    recomputeStats(_uiState.value.records)
                }
                delay(1000L)
            }
        }
    }

    /** Ngừng vòng cập nhật khi ứng dụng không còn hiển thị và đồng bộ ngay các bản nháp đang nhập xuống bộ nhớ bền vững. */
    fun onAppPaused() {
        tickerJob?.cancel()
        tickerJob = null
        val s = _uiState.value
        s.manualEntry?.let { syncManualDraftPersistence(it, immediate = true) }
        settingsStore.saveChatInputDraft(s.chatInputText, immediate = true)
        if (s.activeTimer == null) {
            settingsStore.saveTimerInputDraft(
                TimerInputDraft(s.timerInputTitle, s.timerInputCategoryKey, s.timerInputTagId),
                immediate = true
            )
        }
    }

    private var recomputeJob: Job? = null
    private val recomputeSeq = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * v1.0.6 (nhóm C): đầu vào (sổ cái, ngân sách, nhật ký, ghi chú) được công bố NGAY; phần số liệu dẫn xuất (ngày, tuần, Nhật ký
     * ngày, xúc xắc) tính ngoài luồng chính trên ảnh chụp bất biến, lượt mới nhất thắng (lượt cũ bị bỏ khi về muộn).
     */
    private fun recomputeStats(
        records: List<LedgerRecord>,
        budgets: List<CategoryTimeBudget> = _uiState.value.budgets,
        dailyReviews: List<DailyReviewRecord> = _uiState.value.dailyReviews,
        activityNotes: List<ActivityReviewNote> = _uiState.value.activityNotes,
        reviewDateOverride: String? = null
    ) {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val todayStr = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
        val current = _uiState.value
        val targetReviewDate = reviewDateOverride ?: current.selectedReviewDateLocal.ifBlank { todayStr }
        _uiState.update {
            it.copy(
                todayDateLocal = todayStr,
                records = records,
                budgets = budgets,
                dailyReviews = dailyReviews,
                activityNotes = activityNotes,
                selectedReviewDateLocal = targetReviewDate
            )
        }
        val seq = recomputeSeq.incrementAndGet()
        val activeTimer = _uiState.value.activeTimer
        val preferredCat = TimeCategory.fromKeyOrDefault(current.timerInputCategoryKey)
        val breakRolls = current.consecutiveBreakRolls
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch(safeHandler) {
            val d = withContext(Dispatchers.Default) {
                DerivedStatsCalculator.compute(
                    records = records, budgets = budgets, dailyReviews = dailyReviews, activityNotes = activityNotes,
                    reviewDateOverride = targetReviewDate, selectedReviewDate = targetReviewDate,
                    activeTimer = activeTimer, preferredFocusCategory = preferredCat, consecutiveBreakRolls = breakRolls,
                    zone = zone, nowMs = nowMs
                )
            }
            if (seq != recomputeSeq.get()) return@launch
            _uiState.update { state ->
                state.copy(
                    todayDateLocal = d.todayDateLocal,
                    budgetProgressList = d.budgetProgress,
                    dashboardPriorityBudgets = d.priorityBudgets,
                    currentDailyReviewSnapshot = d.reviewSnapshot,
                    todayStats = d.dayStats,
                    weeklyInsights = d.weekly,
                    diceCognitiveState = d.cognitiveState,
                    diceFaceProbabilities = d.faceProbabilities
                )
            }
        }
    }

    /**
     * Đồng bộ ngay lập tức khi ứng dụng quay lại từ khóa màn hình / chạy nền.
     */
    fun onAppResumed() {
        val persistedTimer = settingsStore.loadActiveTimer()
        if (_uiState.value.activeTimer == null && persistedTimer != null) {
            _uiState.update { it.copy(activeTimer = persistedTimer) }
        }
        reminderScheduler.syncReminders(_uiState.value.records, System.currentTimeMillis())
        refreshTimerTick()
        recomputeStats(_uiState.value.records)
        startTicker()
    }

    private fun refreshTimerTick() {
        val session = _uiState.value.activeTimer
        if (session == null) {
            if (_uiState.value.timerElapsedSeconds != 0L || _uiState.value.timerClockSkewDetected) {
                _uiState.update { it.copy(timerElapsedSeconds = 0L, timerClockSkewDetected = false) }
            }
            return
        }
        val currentBootCount = TimerAndSettingsStore.readDeviceBootCount(getApplication())
        val snapshot = session.computeElapsedSeconds(
            nowEpochMs = System.currentTimeMillis(),
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            currentBootCount = currentBootCount
        )
        _uiState.update {
            it.copy(
                timerElapsedSeconds = snapshot.elapsedSeconds,
                timerClockSkewDetected = snapshot.clockSkewDetected
            )
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update {
            it.copy(
                currentTab = tab,
                isShowingDailyReview = false
            )
        }
        recomputeStats(_uiState.value.records)
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch(safeHandler) {
            delay(3600L)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = null) }
    }

    // ------------------- LUỒNG A: TIMER & XÚC XẮC -------------------

    fun onTimerTitleChange(newTitle: String) {
        if (_uiState.value.activeTimer != null) return
        val capped = newTitle.take(110)
        _uiState.update { it.copy(timerInputTitle = capped) }
        persistPreTimerDraft()
    }

    fun onTimerCategoryChange(categoryKey: String) {
        if (_uiState.value.activeTimer != null) return
        _uiState.update { state ->
            val preferredCat = TimeCategory.fromKeyOrDefault(categoryKey)
            val diceCtx = DopamineTimeDiceEngine.deriveContextFromRecords(
                todayDateLocal = state.todayDateLocal,
                records = state.records,
                nowEpochMs = System.currentTimeMillis(),
                consecutiveBreakRolls = state.consecutiveBreakRolls,
                preferredFocusCategory = preferredCat
            )
            val (cogState, probItems) = DopamineTimeDiceEngine.computeProbabilities(diceCtx)
            state.copy(
                timerInputCategoryKey = categoryKey,
                diceCognitiveState = cogState,
                diceFaceProbabilities = probItems
            )
        }
        persistPreTimerDraft()
    }

    fun cleanTimerDraft() {
        if (_uiState.value.activeTimer != null) return
        _uiState.update { it.copy(timerInputTitle = "", timerInputCategoryKey = "work", timerInputTagId = null) }
        settingsStore.saveTimerInputDraft(null, immediate = true)
    }

    private fun persistPreTimerDraft(immediate: Boolean = false) {
        val s = _uiState.value
        if (s.activeTimer != null) return
        settingsStore.saveTimerInputDraft(
            TimerInputDraft(s.timerInputTitle, s.timerInputCategoryKey, s.timerInputTagId),
            immediate = immediate
        )
    }

    fun rollTimeDice() {
        val current = _uiState.value
        if (current.isRollingDice) return

        viewModelScope.launch(safeHandler) {
            _uiState.update { it.copy(isRollingDice = true) }
            for (step in 1..6) {
                _uiState.update { it.copy(rollingPreviewFaceNumber = (step % 6) + 1) }
                delay(70L)
            }

            val stateSnap = _uiState.value
            val preferredCat = TimeCategory.fromKeyOrDefault(stateSnap.timerInputCategoryKey)
            val diceCtx = DopamineTimeDiceEngine.deriveContextFromRecords(
                todayDateLocal = stateSnap.todayDateLocal,
                records = stateSnap.records,
                nowEpochMs = System.currentTimeMillis(),
                consecutiveBreakRolls = stateSnap.consecutiveBreakRolls,
                preferredFocusCategory = preferredCat
            )

            val result = DopamineTimeDiceEngine.roll(diceCtx)
            val newConsecutiveBreaks = if (result.selectedFace.actionKind == DiceActionKind.WORK_FOCUS) {
                0
            } else {
                stateSnap.consecutiveBreakRolls + 1
            }

            val nextCtx = diceCtx.copy(consecutiveBreakRolls = newConsecutiveBreaks)
            val (nextCogState, nextProbs) = DopamineTimeDiceEngine.computeProbabilities(nextCtx)

            _uiState.update { state ->
                state.copy(
                    isRollingDice = false,
                    rollingPreviewFaceNumber = result.selectedFace.faceNumber,
                    lastDiceRoll = result,
                    consecutiveBreakRolls = newConsecutiveBreaks,
                    diceCognitiveState = nextCogState,
                    diceFaceProbabilities = nextProbs,
                    timerInputTitle = if (state.activeTimer == null) result.selectedFace.activityTitle else state.timerInputTitle,
                    timerInputCategoryKey = if (state.activeTimer == null) result.selectedFace.category.key else state.timerInputCategoryKey
                )
            }

            showToast("🎲 ${result.selectedFace.headline}")
        }
    }

    fun applyDiceRollAndStartTimer() {
        val current = _uiState.value
        if (current.activeTimer != null) {
            showToast("Đang có bộ đếm chạy. Hãy kết thúc phiên hiện tại trước.")
            return
        }
        val roll = current.lastDiceRoll
        if (roll == null) {
            rollTimeDice()
            return
        }

        val face = roll.selectedFace
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentBootCount = TimerAndSettingsStore.readDeviceBootCount(getApplication())
        val session = ActiveTimerSession(
            title = face.activityTitle,
            categoryKey = face.category.key,
            startedAtEpochMs = nowEpoch,
            startedElapsedRealtimeMs = nowElapsed,
            timezone = ZoneId.systemDefault().id,
            targetDurationSeconds = face.durationSeconds,
            startedBootCount = currentBootCount
        )
        settingsStore.saveActiveTimer(session)
        settingsStore.saveTimerInputDraft(null, immediate = true)
        _uiState.update {
            it.copy(
                activeTimer = session,
                timerInputTitle = face.activityTitle,
                timerInputCategoryKey = face.category.key,
                timerElapsedSeconds = 0L,
                timerClockSkewDetected = false,
                currentTab = AppTab.TIMER
            )
        }
        recomputeStats(_uiState.value.records)
        showToast("▶ Đã bắt đầu: ${face.activityTitle} (${face.durationMinutes}p).")
    }

    fun startTimer() {
        val current = _uiState.value
        if (current.activeTimer != null) return
        val title = current.timerInputTitle.trim()
        if (title.isEmpty()) {
            showToast("Hãy nhập tên hoạt động trước khi bấm Bắt đầu.")
            return
        }
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val currentBootCount = TimerAndSettingsStore.readDeviceBootCount(getApplication())
        val session = ActiveTimerSession(
            title = title,
            categoryKey = current.timerInputCategoryKey,
            startedAtEpochMs = nowEpoch,
            startedElapsedRealtimeMs = nowElapsed,
            timezone = ZoneId.systemDefault().id,
            startedBootCount = currentBootCount,
            tagId = current.timerInputTagId
        )
        settingsStore.saveActiveTimer(session)
        settingsStore.saveTimerInputDraft(null, immediate = true)
        _uiState.update {
            it.copy(
                activeTimer = session,
                timerElapsedSeconds = 0L,
                timerClockSkewDetected = false
            )
        }
        recomputeStats(_uiState.value.records)
        showToast("Đồng hồ đã bắt đầu đo.")
    }

    fun stopAndSaveTimer() {
        val session = _uiState.value.activeTimer ?: return
        val currentBootCount = TimerAndSettingsStore.readDeviceBootCount(getApplication())
        val snapshot = session.computeElapsedSeconds(
            nowEpochMs = System.currentTimeMillis(),
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            currentBootCount = currentBootCount
        )
        if (snapshot.elapsedSeconds < 1L) {
            showToast("Đồng hồ cần chạy ít nhất 1 giây.")
            return
        }

        // v1.0.6 (R01): phiên > 24h được cắt thành các đoạn <= 24h và ghi MỘT giao dịch; không còn "không lưu được, chỉ còn Hủy".
        val zone = runCatching { ZoneId.of(session.timezone) }.getOrDefault(ZoneId.systemDefault())
        if (!timerStopGuard.tryAcquire()) return // bấm đúp: chỉ một lượt ghi tại một thời điểm
        viewModelScope.launch(safeHandler) {
            try {
                handleTimerCommit(session, snapshot.effectiveEndEpochMs, zone)
            } finally {
                timerStopGuard.release()
            }
        }
    }

    private suspend fun handleTimerCommit(session: ActiveTimerSession, endEpochMs: Long, zone: ZoneId) {
        run {
            when (val result = TimerCommit.run(
                dao = ledgerDao,
                title = session.title,
                categoryKey = session.categoryKey,
                startedAtEpochMs = session.startedAtEpochMs,
                endEpochMs = endEpochMs,
                zone = zone,
                tagId = session.tagId
            )) {
                is TimerCommitResult.Overlap -> {
                    val durationMs = endEpochMs - session.startedAtEpochMs
                    if (durationMs in 1L..86_400_000L) {
                        val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
                        val dateLocal = Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zone).toLocalDate().toString()
                        val conflicts = ActivityBreakEngine.findAllOverlaps(
                            startMs = session.startedAtEpochMs,
                            endMs = endEpochMs,
                            existingRecords = currentRecords,
                            activeTimer = null,
                            nowEpochMs = endEpochMs
                        )
                        val built = LedgerValidator.validateAndBuildRecord(
                            source = "timer",
                            rawTitle = session.title,
                            rawCategory = session.categoryKey,
                            dateLocal = dateLocal,
                            inputDurationMinutes = null,
                            startedAt = session.startedAtEpochMs,
                            endedAt = endEpochMs,
                            existingRecords = emptyList(),
                            activeTimer = null,
                            nowEpochMs = endEpochMs,
                            zoneId = zone,
                            idempotencyKey = TimerSegments.idempotencyKey(session.startedAtEpochMs, 0),
                            tagId = session.tagId
                        )
                        if (conflicts.isNotEmpty() && built is ValidationResult.Valid) {
                            _uiState.update {
                                it.copy(
                                    pendingOverlapConflict = PendingOverlapConflict(
                                        incoming = built.record,
                                        conflicts = conflicts,
                                        finishTimerOnResolve = true
                                    )
                                )
                            }
                            return
                        }
                    }
                    showToast(
                        result.message + " Phiên đo vẫn được giữ: hãy sửa hoặc xóa bản ghi trùng rồi bấm Dừng & Lưu lại."
                    )
                }
                is TimerCommitResult.Invalid -> showToast(result.message)
                is TimerCommitResult.Saved -> {
                    finishTimerAfterCommit()
                    showToast(
                        if (result.segments > 1) "Đã ghi phiên đo dài vào sổ cái thành ${result.segments} đoạn liên tiếp (mỗi đoạn tối đa 24 giờ)."
                        else "Đã ghi phiên đo vào sổ cái thời gian."
                    )
                }
                TimerCommitResult.AlreadyCommitted -> {
                    finishTimerAfterCommit()
                    showToast("Phiên đo này đã được ghi từ trước nên không ghi lại.")
                }
            }
        }
    }

    private fun finishTimerAfterCommit() {
        settingsStore.saveActiveTimer(null)
        _uiState.update {
            it.copy(
                activeTimer = null,
                timerElapsedSeconds = 0L,
                timerClockSkewDetected = false,
                currentTab = AppTab.OVERVIEW
            )
        }
        // Phiên đã rời khỏi trạng thái "đang chạy": số liệu dẫn xuất không được còn tính cả phiên đang chạy lẫn bản ghi vừa ghi.
        recomputeStats(_uiState.value.records)
    }

    fun cancelRunningTimer() {
        if (_uiState.value.activeTimer == null) return
        settingsStore.saveActiveTimer(null)
        _uiState.update {
            it.copy(
                activeTimer = null,
                timerElapsedSeconds = 0L,
                timerClockSkewDetected = false
            )
        }
        recomputeStats(_uiState.value.records)
        showToast("Đã hủy phiên đo hiện tại.")
    }

    // ------------------- LUỒNG B: AI CHAT — COMPACT BATCH CARD -------------------

    fun onChatInputChange(text: String) {
        _uiState.update { it.copy(chatInputText = text) }
        settingsStore.saveChatInputDraft(text)
    }

    fun applyChatExample(example: String) {
        _uiState.update { it.copy(chatInputText = example) }
        settingsStore.saveChatInputDraft(example)
    }

    fun cleanChatInput() {
        _uiState.update { it.copy(chatInputText = "") }
        settingsStore.saveChatInputDraft("", immediate = true)
    }

    fun analyzeChatInput() {
        val text = _uiState.value.chatInputText.trim()
        if (text.isEmpty()) {
            showToast("Hãy nhập hoạt động và thời lượng.")
            return
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowTime = LocalTime.now(zone)

        // v1.0.9 (P0 F6): guard không khả dụng ⇒ giữ input, báo lỗi rõ ràng, KHÔNG gọi AI, KHÔNG tạo/xác nhận đề xuất.
        // Không có đường nào bỏ qua guard để vẫn ghi dữ liệu.
        val guardStatus = GuardHealth.check()
        if (!guardStatus.ok) {
            guardStatus.cause?.let { if (GuardHealth.shouldReportOnce()) CrashRecorder.record(it) }
            _uiState.update {
                it.copy(
                    chatFault = AiFault.GUARD_UNAVAILABLE,
                    chatPhase = ChatPhaseMachine.next(ChatPhase.SENDING, ChatEvent.Result(0, AiFault.GUARD_UNAVAILABLE)),
                    lastCrashReport = CrashRecorder.readLast()
                )
            }
            return
        }

        // AI-01 CLOSURE cancel branch begin
        // Lệnh hủy chỉ tác động draft chưa lưu trong RAM (không Ledger/DB) và hủy cả kết quả phân tích đang chạy khi cần.
        // v1.0.13 (C1): "xóa X <thời lượng>" với X ngoài ứng dụng chỉ là lệnh khi X khớp một đề xuất đang chờ; còn lại đi lên cloud như mô tả hoạt động.
        val toDraftRef = { p: EditableProposalDraft -> DraftRef(p.id, p.title, p.rawInput, p.durationMinutes) }
        val cancelCommand = CancelCommandParser.parse(text) ?: CancelCommandParser.parseDraftReference(text, _uiState.value.proposals.map(toDraftRef))
        if (cancelCommand != null) {
            var outcome: CancelOutcome? = null
            _uiState.update { state ->
                val (o, remaining) = ProposalLifecycle.applyCancel(
                    drafts = state.proposals,
                    toRef = toDraftRef,
                    command = cancelCommand,
                    analysisInFlight = state.isAnalyzingChat,
                    epoch = chatRunner.epoch
                )
                outcome = o
                state.copy(
                    proposals = remaining,
                    expandedProposalId = if (remaining.any { it.id == state.expandedProposalId }) state.expandedProposalId else null,
                    chatInputText = if (o.askBack) state.chatInputText else "",
                    chatAnswer = if (o.askBack) ChatAnswer.clarification(text, o.message, emptyList()) else null,
                    isAnalyzingChat = state.isAnalyzingChat && !o.invalidateInFlight,
                    chatPhase = ChatPhaseMachine.next(
                        state.chatPhase,
                        if (o.invalidateInFlight) ChatEvent.Cancelled else ChatEvent.DraftsChanged(remaining.size)
                    )
                )
            }
            val done = outcome
            if (done != null && done.invalidateInFlight) chatRunner.cancelStaleJob()
            if (done != null && !done.askBack) showToast(done.message)
            return
        }
        // AI-01 CLOSURE cancel branch end

        // WALLY API-FIRST begin
        // Mọi câu ngôn ngữ tự nhiên đi qua Cloud AI (kể cả "Học 45p"). Không định tuyến cục bộ, không chatbot cục bộ.
        // Chưa có khóa / chưa bật AI: hướng dẫn cấu hình, giữ input, KHÔNG gọi mạng và KHÔNG tự phân tích cục bộ.
        val aiPrefs = _uiState.value.aiPreferences
        if (!aiPrefs.optInEnabled || aiPrefs.apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    chatFault = AiFault.NOT_CONFIGURED,
                    lastAnalysisSource = OutcomeSource.NOT_CONFIGURED,
                    chatPhase = ChatPhaseMachine.next(
                        ChatPhase.SENDING, ChatEvent.Result(0, AiFault.NOT_CONFIGURED)
                    )
                )
            }
            return
        }
        // WALLY API-FIRST end

        // v1.0.6 (F-V105-04): chống bấm đúp bằng quyền sở hữu theo vé; sau "Hủy" lượt mới chạy ngay, lượt cũ không ghi gì.
        val lastQuery = _uiState.value.lastQueryContext
        chatRunner.submit(text) {
            GeminiProposalClient.analyzeInput(
                userText = text,
                today = today,
                nowTime = nowTime,
                timezoneId = zone.id,
                optInEnabled = aiPrefs.optInEnabled,
                baseUrl = aiPrefs.baseUrl,
                apiKey = aiPrefs.apiKey,
                modelName = aiPrefs.modelName,
                trustedHost = aiPrefs.trustedHost,
                lastQuery = lastQuery
            )
        }
    }

    /** Nút "Hủy": ngắt yêu cầu đang chạy, bỏ kết quả muộn, giữ nguyên nội dung đã nhập. */
    fun cancelAnalysis() {
        if (!chatRunner.cancel()) return
        showToast("Đã hủy phân tích. Nội dung bạn nhập được giữ lại.")
    }

    fun dismissChatAnswer() {
        _uiState.update {
            it.copy(chatAnswer = null, chatPhase = ChatPhaseMachine.next(it.chatPhase, ChatEvent.Dismissed))
        }
    }

    fun dismissChatError() {
        _uiState.update {
            it.copy(chatFault = null, chatPhase = ChatPhaseMachine.next(it.chatPhase, ChatEvent.Dismissed))
        }
    }

    fun toggleExpandProposal(proposalId: String) {
        _uiState.update { state ->
            val nextId = if (state.expandedProposalId == proposalId) null else proposalId
            state.copy(expandedProposalId = nextId)
        }
    }

    fun updateProposalDraft(
        proposalId: String,
        transform: (EditableProposalDraft) -> EditableProposalDraft
    ) {
        _uiState.update { state ->
            state.copy(
                proposals = state.proposals.map { if (it.id == proposalId) transform(it) else it }
            )
        }
    }

    fun discardProposal(proposalId: String) {
        _uiState.update { state ->
            state.copy(
                proposals = state.proposals.filterNot { it.id == proposalId },
                expandedProposalId = if (state.expandedProposalId == proposalId) null else state.expandedProposalId,
                chatAnswer = state.chatAnswer?.takeUnless { it.isTransactionStatus }
            )
        }
    }

    fun clearAllProposals() {
        _uiState.update { it.copy(proposals = emptyList(), expandedProposalId = null, chatAnswer = it.chatAnswer?.takeUnless { a -> a.isTransactionStatus }) }
    }

    fun confirmSingleProposal(proposalId: String) {
        val draft = _uiState.value.proposals.firstOrNull { it.id == proposalId } ?: return
        confirmProposalsList(listOf(draft))
    }

    /**
     * Xác nhận toàn bộ danh sách đề xuất trong Compact Batch Card (hỗ trợ 5+ hoạt động cùng lúc).
     */
    fun confirmAllProposals() {
        val drafts = _uiState.value.proposals
        if (drafts.isEmpty()) return
        confirmProposalsList(drafts)
    }

    private fun confirmProposalsList(
        drafts: List<EditableProposalDraft>,
        priorSavedCount: Int = 0,
        priorSkippedDuplicates: Int = 0
    ) {
        val zone = ZoneId.systemDefault()

        // WALLY A08: một lượt xác nhận tại một thời điểm; bấm lặp không tạo lượt ghi thứ hai.
        if (!confirmGuard.tryAcquire()) {
            showToast("Đang lưu, vui lòng chờ giây lát.")
            return
        }
        val job = viewModelScope.launch(safeHandler) {
            val nowMs = System.currentTimeMillis()
            val existing = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            when (
                val step = BatchProposalPlanner.plan(
                    inputs = drafts.map { it.toBatchInput() },
                    existingRecords = existing,
                    activeTimer = _uiState.value.activeTimer,
                    nowEpochMs = nowMs,
                    zone = zone
                )
            ) {
                is BatchOverlapStep.Invalid -> {
                    val prefix = if (priorSavedCount > 0) "Đã lưu $priorSavedCount bản ghi trước đó; mục kế tiếp không hợp lệ: " else "Đã hủy lưu lô để bảo toàn dữ liệu: "
                    showToast(prefix + step.message)
                }

                is BatchOverlapStep.ReadyAll -> {
                    val result = if (step.inputs.isEmpty()) {
                        ConfirmResult.Committed(0, step.skippedDuplicates)
                    } else {
                        BatchConfirm.run(
                            dao = ledgerDao,
                            inputs = step.inputs,
                            activeTimer = _uiState.value.activeTimer,
                            readActiveTimer = { settingsStore.loadActiveTimer() },
                            nowEpochMs = nowMs,
                            zone = zone
                        )
                    }
                    when (result) {
                        is ConfirmResult.Invalid -> showToast("Đã hủy lưu lô để bảo toàn dữ liệu: ${result.message}")
                        is ConfirmResult.Aborted -> showToast("Đã hủy lưu lô: ${result.message}")
                        is ConfirmResult.Committed -> {
                            val totalSaved = priorSavedCount + result.savedCount
                            val totalSkipped = priorSkippedDuplicates + result.skippedDuplicates +
                                (if (step.inputs.isEmpty()) 0 else step.skippedDuplicates)
                            clearConfirmedDraftsAndNotify(drafts, result.savedCount > 0, zone, nowMs)
                            showToast(ConfirmFeedback.message(totalSaved, totalSkipped))
                        }
                    }
                }

                is BatchOverlapStep.ConflictAt -> {
                    var runningSaved = priorSavedCount
                    var runningSkipped = priorSkippedDuplicates + step.skippedDuplicates
                    if (step.safePrefix.isNotEmpty()) {
                        val prefixRes = BatchConfirm.run(
                            dao = ledgerDao,
                            inputs = step.safePrefix,
                            activeTimer = _uiState.value.activeTimer,
                            readActiveTimer = { settingsStore.loadActiveTimer() },
                            nowEpochMs = nowMs,
                            zone = zone
                        )
                        when (prefixRes) {
                            is ConfirmResult.Invalid -> {
                                showToast("Đã hủy lưu lô để bảo toàn dữ liệu: ${prefixRes.message}")
                                return@launch
                            }
                            is ConfirmResult.Aborted -> {
                                showToast("Đã hủy lưu lô: ${prefixRes.message}")
                                return@launch
                            }
                            is ConfirmResult.Committed -> {
                                runningSaved += prefixRes.savedCount
                                runningSkipped += prefixRes.skippedDuplicates
                                val safeIds = step.safePrefix.mapNotNull { it.id }.toSet()
                                val safeDrafts = drafts.filter { it.id in safeIds }
                                clearConfirmedDraftsAndNotify(safeDrafts, prefixRes.savedCount > 0, zone, nowMs)
                            }
                        }
                    }
                    val latestRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
                    val candidate = step.conflictCandidate
                    val freshConflicts = if (candidate.startedAt != null && candidate.endedAt != null) {
                        ActivityBreakEngine.findAllOverlaps(
                            startMs = candidate.startedAt,
                            endMs = candidate.endedAt,
                            existingRecords = latestRecords,
                            activeTimer = _uiState.value.activeTimer,
                            nowEpochMs = nowMs
                        )
                    } else emptyList()
                    _uiState.update { state ->
                        state.copy(
                            pendingOverlapConflict = PendingOverlapConflict(
                                incoming = candidate,
                                conflicts = freshConflicts.ifEmpty { step.conflicts },
                                clearedDraftIds = listOf(step.conflictInputId),
                                remainingChatDraftIds = step.remainingInputIds,
                                priorSavedCount = runningSaved,
                                priorSkippedDuplicates = runningSkipped
                            )
                        )
                    }
                }
            }
        }
        job.invokeOnCompletion { confirmGuard.release() }
    }

    private fun clearConfirmedDraftsAndNotify(
        confirmedDrafts: List<EditableProposalDraft>,
        didSaveNewRows: Boolean,
        zone: ZoneId,
        nowMs: Long
    ) {
        if (confirmedDrafts.isEmpty()) return
        val confirmedIds = confirmedDrafts.map { it.id }.toSet()
        val needsReminderPermission = didSaveNewRows && confirmedDrafts.any { d ->
            if (d.isFuturePlanned) return@any true
            val r = if (d.startLocal.isNotBlank() && d.endLocal.isNotBlank()) {
                LedgerValidator.parseTimeRangeToEpochMillis(d.dateLocal, d.startLocal, d.endLocal, zone)
            } else null
            r != null && r.second > nowMs
        }
        _uiState.update { state ->
            val remaining = state.proposals.filterNot { confirmedIds.contains(it.id) }
            state.copy(
                proposals = remaining,
                expandedProposalId = state.expandedProposalId?.takeIf { id -> remaining.any { it.id == id } },
                // Thẻ trạng thái ("còn N đề xuất đang chờ") sẽ cũ ngay sau khi commit: xóa; chỉ toast do ứng dụng báo "Đã lưu" sau commit.
                chatAnswer = state.chatAnswer?.takeUnless { it.isTransactionStatus },
                requestNotificationPermissionNonce = if (needsReminderPermission) {
                    state.requestNotificationPermissionNonce + 1
                } else {
                    state.requestNotificationPermissionNonce
                }
            )
        }
    }

    // ------------------- LUỒNG C: HISTORY, CHUYỂN DỰ KIẾN -> THỰC TẾ, SỬA & XÓA -------------------

    fun setHistoryFilter(filter: HistoryFilter) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun openEditRecord(record: LedgerRecord) {
        _uiState.update { it.copy(editingRecord = record) }
    }

    fun closeEditRecord() {
        _uiState.update { it.copy(editingRecord = null) }
    }

    /**
     * Mở hộp thoại xác nhận trạng thái thực tế cho bản ghi PLANNED / DOING / PENDING_CONFIRMATION,
     * hoặc mở hộp thoại đối soát chồng lấn nếu bản ghi đang ở trạng thái PENDING / BREAK_REJECTED.
     */
    fun confirmPlannedOrActiveRecord(record: LedgerRecord) {
        if (record.isNeedsReconciliation) {
            openReconciliationDialog(record)
        } else {
            openConfirmationDialog(record)
        }
    }

    fun openConfirmationDialog(record: LedgerRecord) {
        _uiState.update { it.copy(confirmingRecord = record) }
    }

    fun closeConfirmationDialog() {
        _uiState.update { it.copy(confirmingRecord = null) }
    }

    fun openActivityConfirmationFromNotification(recordId: String) {
        viewModelScope.launch(safeHandler) {
            val target = _uiState.value.records.firstOrNull { it.id == recordId }
                ?: ledgerDao.getAllRecordsSnapshot().firstOrNull { it.id == recordId }?.toDomain()
            if (target == null) {
                showToast("Hoạt động này không còn tồn tại trong sổ cái.")
                return@launch
            }
            if (target.isCancelled) {
                showToast("Hoạt động \"${target.title}\" đã được đánh dấu hủy trước đó.")
                return@launch
            }
            _uiState.update { it.copy(isShowingDailyReview = false) }
            if (target.isNeedsReconciliation) {
                openReconciliationDialog(target)
            } else {
                _uiState.update { it.copy(confirmingRecord = target) }
            }
        }
    }

    fun openReconciliationDialog(record: LedgerRecord) {
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            val start = record.startedAt
            val end = record.endedAt
            val conflicts = if (start != null && end != null) {
                ActivityBreakEngine.findAllOverlaps(
                    startMs = start,
                    endMs = end,
                    existingRecords = currentRecords,
                    activeTimer = _uiState.value.activeTimer,
                    nowEpochMs = nowMs,
                    excludeRecordIds = setOf(record.id)
                )
            } else emptyList()
            if (conflicts.isNotEmpty()) {
                _uiState.update {
                    it.copy(pendingOverlapConflict = PendingOverlapConflict(incoming = record, conflicts = conflicts))
                }
            } else {
                _uiState.update { it.copy(confirmingRecord = record) }
            }
        }
    }

    private suspend fun applyConfirmationAction(
        result: ConfirmationActionResult,
        closeConfirmOnSuccess: Boolean = false,
        closeEditOnSuccess: Boolean = false
    ) {
        when (result) {
            is ConfirmationActionResult.Invalid -> showToast(result.messageVi)
            is ConfirmationActionResult.Overlap -> {
                _uiState.update {
                    it.copy(
                        confirmingRecord = null,
                        pendingOverlapConflict = PendingOverlapConflict(
                            incoming = result.candidate,
                            conflicts = result.conflicts,
                            closeEditOnResolve = closeEditOnSuccess
                        )
                    )
                }
            }
            is ConfirmationActionResult.Applied -> {
                val entities = result.updatedRecords.map { LedgerRecordEntity.fromDomain(it) }
                when (val commit = ledgerDao.applyOverlapResolutionTransactionally(entities)) {
                    is DbCommitOutcome.OverlapRejected ->
                        showToast("Bị trùng mốc giờ thực tế với \"${commit.conflictingTitle}\".")
                    is DbCommitOutcome.InvalidEntityRejected ->
                        showToast(commit.reason)
                    DbCommitOutcome.Saved, DbCommitOutcome.DuplicateIgnored -> {
                        val needsReminder = result.updatedRecords.any {
                            it.layer == TimeLayer.PLANNED || it.layer == TimeLayer.ACTIVE
                        }
                        result.updatedRecords.forEach {
                            if (it.isCancelled || (it.layer != TimeLayer.PLANNED && it.layer != TimeLayer.ACTIVE)) {
                                reminderScheduler.cancelReminder(it.id)
                            }
                        }
                        _uiState.update { s ->
                            s.copy(
                                confirmingRecord = if (closeConfirmOnSuccess) null else s.confirmingRecord,
                                editingRecord = if (closeEditOnSuccess) null else s.editingRecord,
                                requestNotificationPermissionNonce = if (needsReminder) {
                                    s.requestNotificationPermissionNonce + 1
                                } else {
                                    s.requestNotificationPermissionNonce
                                }
                            )
                        }
                        showToast(result.messageVi)
                    }
                }
            }
        }
    }

    fun confirmStartDoing() {
        val record = _uiState.value.confirmingRecord ?: return
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            applyConfirmationAction(
                result = ConfirmationEngine.startDoingNow(record, currentRecords, _uiState.value.activeTimer, nowMs, zone),
                closeConfirmOnSuccess = true
            )
        }
    }

    fun confirmCompleted() {
        val record = _uiState.value.confirmingRecord ?: return
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            applyConfirmationAction(
                result = ConfirmationEngine.confirmCompleted(record, currentRecords, _uiState.value.activeTimer, nowMs, zone),
                closeConfirmOnSuccess = true
            )
        }
    }

    fun confirmAdjusted(title: String, startHm: String, endHm: String) {
        val record = _uiState.value.confirmingRecord ?: return
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val mins = (record.durationSeconds / 60L).toInt().coerceIn(1, 1440)
        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            applyConfirmationAction(
                result = ConfirmationEngine.adjustRecord(
                    existing = record,
                    newTitle = title,
                    newCategoryKey = record.category.key,
                    newDateLocal = record.date,
                    newDurationMinutes = mins,
                    newStartHm = startHm,
                    newEndHm = endHm,
                    allowFutureAsPlanned = false,
                    existingRecords = currentRecords,
                    activeTimer = _uiState.value.activeTimer,
                    nowEpochMs = nowMs,
                    zoneId = zone
                ),
                closeConfirmOnSuccess = true
            )
        }
    }

    fun confirmInterrupted(actualEndHm: String) {
        val record = _uiState.value.confirmingRecord ?: return
        val startMs = record.startedAt
        if (startMs == null) {
            showToast("Chỉ hoạt động có mốc giờ mới ghi nhận điểm gián đoạn theo giờ.")
            return
        }
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val hmFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val startZoned = Instant.ofEpochMilli(startMs).atZone(zone)
        val parsed = LedgerValidator.parseTimeRangeToEpochMillis(
            dateLocal = startZoned.toLocalDate().toString(),
            startHm = startZoned.format(hmFmt),
            endHm = actualEndHm.trim(),
            zoneId = zone
        )
        if (parsed == null) {
            showToast("Giờ kết thúc gián đoạn không hợp lệ (HH:mm) hoặc trùng giờ bắt đầu.")
            return
        }
        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            applyConfirmationAction(
                result = ConfirmationEngine.interruptRecord(
                    existing = record,
                    actualEndedAtMs = parsed.second,
                    existingRecords = currentRecords,
                    activeTimer = _uiState.value.activeTimer,
                    nowEpochMs = nowMs,
                    zoneId = zone
                ),
                closeConfirmOnSuccess = true
            )
        }
    }

    fun confirmContinued(newEndHm: String) {
        val record = _uiState.value.confirmingRecord ?: return
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val startMs = record.startedAt ?: nowMs
        val hmFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val startZoned = Instant.ofEpochMilli(startMs).atZone(zone)
        val parsed = LedgerValidator.parseTimeRangeToEpochMillis(
            dateLocal = startZoned.toLocalDate().toString(),
            startHm = startZoned.format(hmFmt),
            endHm = newEndHm.trim(),
            zoneId = zone
        )
        if (parsed == null) {
            showToast("Giờ kết thúc mới không hợp lệ (HH:mm).")
            return
        }
        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            applyConfirmationAction(
                result = ConfirmationEngine.continueRecord(
                    existing = record,
                    newEndedAtMs = parsed.second,
                    existingRecords = currentRecords,
                    activeTimer = _uiState.value.activeTimer,
                    nowEpochMs = nowMs,
                    zoneId = zone
                ),
                closeConfirmOnSuccess = true
            )
        }
    }

    fun confirmCancelled() {
        val record = _uiState.value.confirmingRecord ?: return
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch(safeHandler) {
            applyConfirmationAction(
                result = ConfirmationEngine.cancelRecord(existing = record, nowEpochMs = nowMs),
                closeConfirmOnSuccess = true
            )
        }
    }

    fun dismissOverlapConflict() {
        _uiState.update { it.copy(pendingOverlapConflict = null) }
    }

    fun resolveOverlapConflict(
        decision: BreakDecision,
        adjustedStartHm: String? = null,
        adjustedEndHm: String? = null
    ) {
        val conflict = _uiState.value.pendingOverlapConflict ?: return
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            if (decision == BreakDecision.TIME_ADJUSTED) {
                val range = LedgerValidator.parseTimeRangeToEpochMillis(
                    dateLocal = conflict.incoming.date,
                    startHm = adjustedStartHm.orEmpty(),
                    endHm = adjustedEndHm.orEmpty(),
                    zoneId = zone
                )
                if (range == null) {
                    showToast("Giờ bắt đầu/kết thúc mới không hợp lệ (định dạng HH:mm).")
                    return@launch
                }
                val alreadyExists = currentRecords.any { it.id == conflict.incoming.id }
                val activeTimerForCheck = if (conflict.finishTimerOnResolve) null else _uiState.value.activeTimer
                val newConflicts = ActivityBreakEngine.findAllOverlaps(
                    startMs = range.first,
                    endMs = range.second,
                    existingRecords = currentRecords,
                    activeTimer = activeTimerForCheck,
                    nowEpochMs = nowMs,
                    excludeRecordIds = setOf(conflict.incoming.id)
                )
                val built = LedgerValidator.validateAndBuildRecord(
                    id = conflict.incoming.id,
                    source = conflict.incoming.source,
                    rawTitle = conflict.incoming.title,
                    rawCategory = conflict.incoming.category.key,
                    dateLocal = conflict.incoming.date,
                    inputDurationMinutes = null,
                    startedAt = range.first,
                    endedAt = range.second,
                    existingRecords = currentRecords.filterNot { c -> newConflicts.any { it.id == c.id } },
                    activeTimer = null,
                    nowEpochMs = nowMs,
                    zoneId = zone,
                    idempotencyKey = conflict.incoming.idempotencyKey,
                    excludeRecordId = conflict.incoming.id,
                    convertFutureToPlanned = false,
                    tagId = conflict.incoming.tagId,
                    confirmationStateOverride = if (alreadyExists && range.second <= nowMs) {
                        ConfirmationState.ADJUSTED.name
                    } else null,
                    plannedStartedAt = conflict.incoming.plannedStartedAt ?: conflict.incoming.startedAt,
                    plannedEndedAt = conflict.incoming.plannedEndedAt ?: conflict.incoming.endedAt,
                    plannedDurationSeconds = conflict.incoming.plannedDurationSeconds ?: conflict.incoming.durationSeconds
                )
                when (built) {
                    is ValidationResult.Invalid -> showToast(built.message)
                    is ValidationResult.Valid -> {
                        val candidate = built.record.copy(createdAt = conflict.incoming.createdAt)
                        if (newConflicts.isNotEmpty()) {
                            _uiState.update {
                                it.copy(
                                    pendingOverlapConflict = conflict.copy(
                                        incoming = candidate,
                                        conflicts = newConflicts
                                    )
                                )
                            }
                            showToast("Khoảng giờ vừa chỉnh vẫn trùng với hoạt động khác.")
                        } else {
                            when (val commit = ledgerDao.applyOverlapResolutionTransactionally(
                                updatedEntities = listOf(LedgerRecordEntity.fromDomain(candidate))
                            )) {
                                is DbCommitOutcome.OverlapRejected -> showToast("Vẫn trùng mốc giờ với \"${commit.conflictingTitle}\".")
                                is DbCommitOutcome.InvalidEntityRejected -> showToast(commit.reason)
                                DbCommitOutcome.Saved, DbCommitOutcome.DuplicateIgnored -> {
                                    finalizeOverlapResolved(conflict, listOf(candidate), "Đã điều chỉnh giờ cho \"${candidate.title}\".")
                                }
                            }
                        }
                    }
                }
                return@launch
            }

            val plan = ActivityBreakEngine.resolveBreak(
                incoming = conflict.incoming,
                conflicts = conflict.conflicts,
                decision = decision,
                nowEpochMs = nowMs,
                zoneId = zone
            ).getOrElse { err ->
                showToast(err.message ?: "Không thể xử lý chồng lấn thời gian.")
                return@launch
            }

            val commit = ledgerDao.applyOverlapResolutionTransactionally(
                updatedEntities = plan.updatedRecords.map { LedgerRecordEntity.fromDomain(it) },
                deletedIds = plan.deletedIds
            )
            when (commit) {
                is DbCommitOutcome.OverlapRejected ->
                    showToast("Bị trùng mốc giờ thực tế với \"${commit.conflictingTitle}\".")
                is DbCommitOutcome.InvalidEntityRejected ->
                    showToast(commit.reason)
                DbCommitOutcome.Saved, DbCommitOutcome.DuplicateIgnored -> {
                    finalizeOverlapResolved(conflict, plan.updatedRecords, plan.summaryVi)
                }
            }
        }
    }

    private fun finalizeOverlapResolved(
        conflict: PendingOverlapConflict,
        savedRecords: List<LedgerRecord>,
        messageVi: String
    ) {
        if (conflict.finishTimerOnResolve) {
            finishTimerAfterCommit()
        }
        if (conflict.closeManualOnResolve) {
            ManualEntryDraftCoordinator.clearOnCommitSuccess(settingsStore)
        }
        val cleared = conflict.clearedDraftIds.toSet()
        val needsReminder = savedRecords.any { it.layer == TimeLayer.PLANNED || it.layer == TimeLayer.ACTIVE }
        val nextQueuedDrafts = if (conflict.remainingChatDraftIds.isEmpty()) {
            emptyList()
        } else {
            val byId = _uiState.value.proposals.filterNot { it.id in cleared }.associateBy { it.id }
            conflict.remainingChatDraftIds.mapNotNull { byId[it] }
        }
        _uiState.update { state ->
            val remainingProposals = if (cleared.isEmpty()) state.proposals else state.proposals.filterNot { it.id in cleared }
            state.copy(
                pendingOverlapConflict = null,
                manualEntry = if (conflict.closeManualOnResolve) null else state.manualEntry,
                pendingManualDraft = if (conflict.closeManualOnResolve) null else state.pendingManualDraft,
                editingRecord = if (conflict.closeEditOnResolve) null else state.editingRecord,
                proposals = remainingProposals,
                expandedProposalId = state.expandedProposalId?.takeIf { id -> remainingProposals.any { it.id == id } },
                chatAnswer = if (cleared.isNotEmpty() && nextQueuedDrafts.isEmpty()) state.chatAnswer?.takeUnless { it.isTransactionStatus } else state.chatAnswer,
                requestNotificationPermissionNonce = if (needsReminder) {
                    state.requestNotificationPermissionNonce + 1
                } else {
                    state.requestNotificationPermissionNonce
                }
            )
        }
        showToast(messageVi)
        if (nextQueuedDrafts.isNotEmpty()) {
            confirmProposalsList(
                drafts = nextQueuedDrafts,
                priorSavedCount = conflict.priorSavedCount + 1,
                priorSkippedDuplicates = conflict.priorSkippedDuplicates
            )
        }
    }

    fun saveEditedRecord(
        existing: LedgerRecord,
        newTitle: String,
        newCategoryKey: String,
        newDateLocal: String,
        newDurationMinutesText: String,
        newStartHm: String,
        newEndHm: String,
        allowFutureAsPlanned: Boolean
    ) {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val hasRange = newStartHm.trim().isNotEmpty() || newEndHm.trim().isNotEmpty()
        val mins = if (hasRange) null else newDurationMinutesText.trim().toIntOrNull()

        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            val result = ConfirmationEngine.adjustRecord(
                existing = existing,
                newTitle = newTitle,
                newCategoryKey = newCategoryKey,
                newDateLocal = newDateLocal,
                newDurationMinutes = mins,
                newStartHm = newStartHm,
                newEndHm = newEndHm,
                allowFutureAsPlanned = allowFutureAsPlanned,
                existingRecords = currentRecords,
                activeTimer = _uiState.value.activeTimer,
                nowEpochMs = nowMs,
                zoneId = zone
            )
            applyConfirmationAction(result = result, closeEditOnSuccess = true)
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch(safeHandler) {
            reminderScheduler.cancelReminder(recordId)
            // WALLY: bản ghi và ghi chú liên quan cùng bị xóa hoặc cùng hoàn tác (một giao dịch Room).
            database.withTransaction {
                ledgerDao.deleteById(recordId)
                dailyReviewDao.deleteActivityNoteByRecordId(recordId)
            }
            showToast("Đã xóa bản ghi.")
        }
    }

    fun resizePlannedRecord(recordId: String, deltaMinutes: Int) {
        if (deltaMinutes == 0) return
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch(safeHandler) {
            val existing = ledgerDao.getAllRecordsSnapshot()
                .firstOrNull { it.id == recordId }
                ?.toDomain()
                ?: return@launch
            val result = ConfirmationEngine.resizePlannedRecord(
                existing = existing,
                deltaMinutes = deltaMinutes,
                nowEpochMs = nowMs,
                zoneId = zone
            )
            applyConfirmationAction(result = result, closeEditOnSuccess = false)
        }
    }

    fun clearAllData() {
        if (_uiState.value.activeTimer != null) {
            showToast("Hãy kết thúc đồng hồ đang chạy trước khi xóa toàn bộ dữ liệu.")
            return
        }
        viewModelScope.launch(safeHandler) {
            _uiState.value.records.forEach { reminderScheduler.cancelReminder(it.id) }
            database.withTransaction {
                ledgerDao.deleteAll()
                dailyReviewDao.deleteAllDailyReviews()
                dailyReviewDao.deleteAllActivityNotes()
            }
            showToast("Đã xóa toàn bộ bản ghi trong ví thời gian.")
        }
    }

    fun loadSampleData() {
        viewModelScope.launch(safeHandler) {
            val zone = ZoneId.systemDefault()
            val nowMs = System.currentTimeMillis()
            val today = LocalDate.now(zone)
            val todayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()

            val targetDate = if (nowMs - todayStartMs < 4 * 3_600_000L) {
                today.minusDays(1)
            } else {
                today
            }
            val baseDayStartMs = targetDate.atStartOfDay(zone).toInstant().toEpochMilli()

            data class DemoSpec(val hourOffset: Int, val durationMins: Int, val title: String, val cat: String)

            val specs = if (targetDate == today && nowMs - baseDayStartMs < 13 * 3_600_000L) {
                val startHour = (((nowMs - baseDayStartMs) / 3_600_000L).toInt() - 3).coerceAtLeast(0)
                listOf(
                    DemoSpec(startHour, 60, "Làm việc dự án", "work"),
                    DemoSpec(startHour + 1, 45, "Học kỹ thuật Feynman", "learn"),
                    DemoSpec(startHour + 2, 30, "Đi bộ thư giãn", "health")
                )
            } else {
                listOf(
                    DemoSpec(8, 60, "Làm việc dự án", "work"),
                    DemoSpec(10, 45, "Học kỹ thuật Feynman", "learn"),
                    DemoSpec(12, 30, "Đi bộ thư giãn", "health")
                )
            }

            val existing = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }.toMutableList()
            var addedCount = 0

            for ((idx, spec) in specs.withIndex()) {
                val startMs = baseDayStartMs + spec.hourOffset * 3_600_000L
                val endMs = startMs + spec.durationMins * 60_000L
                if (endMs > nowMs) continue

                val validation = LedgerValidator.validateAndBuildRecord(
                    id = "demo_${nowMs}_$idx",
                    source = "demo",
                    rawTitle = spec.title,
                    rawCategory = spec.cat,
                    dateLocal = targetDate.toString(),
                    inputDurationMinutes = spec.durationMins,
                    startedAt = startMs,
                    endedAt = endMs,
                    existingRecords = existing,
                    activeTimer = _uiState.value.activeTimer,
                    nowEpochMs = nowMs,
                    zoneId = zone,
                    idempotencyKey = "demo_${targetDate}_${spec.title}"
                )
                if (validation is ValidationResult.Valid) {
                    val outcome = ledgerDao.commitRecordTransactionally(
                        LedgerRecordEntity.fromDomain(validation.record)
                    )
                    if (outcome is DbCommitOutcome.Saved) {
                        existing.add(validation.record)
                        addedCount++
                    }
                }
            }

            showToast("Đã thêm $addedCount hoạt động mẫu vào sổ cái.")
        }
    }

    // ------------------- LUỒNG D: QUẢN LÝ NGÂN SÁCH THỜI GIAN (TIME BUDGET) -------------------

    fun openBudgetEditor(initialCategory: TimeCategory? = null) {
        _uiState.update {
            it.copy(
                showBudgetDialog = true,
                editingBudgetCategory = initialCategory
            )
        }
    }

    fun closeBudgetEditor() {
        _uiState.update {
            it.copy(
                showBudgetDialog = false,
                editingBudgetCategory = null
            )
        }
    }

    fun saveCategoryBudget(
        categoryKey: String,
        period: BudgetPeriod,
        targetHoursText: String,
        targetMinutesText: String,
        isPriority: Boolean
    ) {
        val category = TimeCategory.fromKeyOrNull(categoryKey)
        if (category == null) {
            showToast("Danh mục ngân sách không hợp lệ.")
            return
        }

        val hours = targetHoursText.trim().ifEmpty { "0" }.toIntOrNull()
        val mins = targetMinutesText.trim().ifEmpty { "0" }.toIntOrNull()
        if (hours == null || mins == null || hours < 0 || mins < 0 || mins >= 60) {
            showToast("Hãy nhập số giờ (>= 0) và số phút (0..59) hợp lệ.")
            return
        }

        val totalMinutes = hours * 60 + mins
        if (totalMinutes < 1 || totalMinutes > period.maxTargetMinutes) {
            val maxHours = period.maxTargetMinutes / 60
            showToast("Mục tiêu ${period.labelVi.lowercase()} phải từ 1 phút đến $maxHours giờ.")
            return
        }

        val nowMs = System.currentTimeMillis()
        val existingBudgets = _uiState.value.budgets
        BudgetPolicy.priorityViolation(existingBudgets, category, isPriority)?.let {
            showToast(it)
            return
        }
        val existingOrder = existingBudgets.firstOrNull { it.category == category }?.priorityOrder
            ?: existingBudgets.size

        val budget = CategoryTimeBudget(
            category = category,
            period = period,
            targetSeconds = totalMinutes * 60L,
            isPriority = isPriority,
            priorityOrder = existingOrder,
            updatedAt = nowMs
        )

        viewModelScope.launch(safeHandler) {
            budgetDao.upsertBudget(CategoryBudgetEntity.fromDomain(budget))
            _uiState.update {
                it.copy(
                    showBudgetDialog = false,
                    editingBudgetCategory = null
                )
            }
            showToast(
                "Đã lưu ngân sách ${category.displayName}: ${
                    LedgerAnalyticsCalculator.formatCompactDurationSeconds(budget.targetSeconds)
                }${period.shortSuffixVi}."
            )
        }
    }

    fun deleteCategoryBudget(categoryKey: String) {
        val category = TimeCategory.fromKeyOrDefault(categoryKey)
        viewModelScope.launch(safeHandler) {
            budgetDao.deleteBudgetByCategory(categoryKey)
            _uiState.update {
                it.copy(
                    showBudgetDialog = false,
                    editingBudgetCategory = null
                )
            }
            showToast("Đã xóa ngân sách danh mục ${category.displayName}.")
        }
    }

    fun reorderBudgetPriority(fromCategoryKey: String, toCategoryKey: String) {
        val updated = BudgetPolicy.reorderBudgetsPreservingGroup(
            existing = _uiState.value.budgets,
            fromCategoryKey = fromCategoryKey,
            toCategoryKey = toCategoryKey,
            nowMs = System.currentTimeMillis()
        )
        if (updated.isEmpty()) return
        viewModelScope.launch(safeHandler) {
            budgetDao.upsertBudgetsTransactionally(updated.map { CategoryBudgetEntity.fromDomain(it) })
        }
    }

    // ------------------- LUỒNG E: DAILY REVIEW & PHẢN TƯ FEYNMAN -------------------

    fun openDailyReview(dateLocal: String? = null) {
        val targetDate = dateLocal ?: _uiState.value.todayDateLocal
        _uiState.update {
            it.copy(
                currentTab = AppTab.OVERVIEW,
                isShowingDailyReview = true,
                selectedReviewDateLocal = targetDate
            )
        }
        recomputeStats(
            records = _uiState.value.records,
            reviewDateOverride = targetDate
        )
    }

    fun closeDailyReview() {
        _uiState.update { it.copy(isShowingDailyReview = false) }
    }

    fun selectPreviousReviewDay() {
        val currentStr = _uiState.value.selectedReviewDateLocal.ifBlank { _uiState.value.todayDateLocal }
        val prevStr = try {
            LocalDate.parse(currentStr).minusDays(1).toString()
        } catch (_: Exception) {
            _uiState.value.todayDateLocal
        }
        _uiState.update { it.copy(selectedReviewDateLocal = prevStr) }
        recomputeStats(
            records = _uiState.value.records,
            reviewDateOverride = prevStr
        )
    }

    fun selectNextReviewDay() {
        val currentStr = _uiState.value.selectedReviewDateLocal.ifBlank { _uiState.value.todayDateLocal }
        val nextStr = try {
            LocalDate.parse(currentStr).plusDays(1).toString()
        } catch (_: Exception) {
            _uiState.value.todayDateLocal
        }
        _uiState.update { it.copy(selectedReviewDateLocal = nextStr) }
        recomputeStats(
            records = _uiState.value.records,
            reviewDateOverride = nextStr
        )
    }

    fun selectTodayReviewDay() {
        val todayStr = _uiState.value.todayDateLocal
        _uiState.update { it.copy(selectedReviewDateLocal = todayStr) }
        recomputeStats(
            records = _uiState.value.records,
            reviewDateOverride = todayStr
        )
    }

    fun saveActivityReviewNote(
        recordId: String,
        dateLocal: String,
        outcomeNote: String,
        feynmanExplanation: String
    ) {
        val note = ActivityReviewNote(
            recordId = recordId,
            dateLocal = dateLocal,
            outcomeNote = outcomeNote.trim(),
            feynmanExplanation = feynmanExplanation.trim(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch(safeHandler) {
            dailyReviewDao.upsertActivityNote(ActivityReviewNoteEntity.fromDomain(note))
            showToast("Đã lưu ghi chú kết quả hoạt động.")
        }
    }

    fun saveFullDailyReview(
        dateLocal: String,
        summaryText: String,
        question1: String,
        question2: String,
        answer1: String,
        answer2: String,
        generalNote: String,
        pendingActivityNotes: List<ActivityReviewNote>
    ) {
        val nowMs = System.currentTimeMillis()
        val review = DailyReviewRecord(
            dateLocal = dateLocal,
            summaryText = summaryText.trim(),
            question1 = question1.trim(),
            question2 = question2.trim(),
            answer1 = answer1.trim(),
            answer2 = answer2.trim(),
            generalNote = generalNote.trim(),
            updatedAt = nowMs
        )
        viewModelScope.launch(safeHandler) {
            if (pendingActivityNotes.isNotEmpty()) {
                dailyReviewDao.upsertActivityNotes(
                    pendingActivityNotes.map { ActivityReviewNoteEntity.fromDomain(it.copy(updatedAt = nowMs)) }
                )
            }
            dailyReviewDao.upsertDailyReview(DailyReviewEntity.fromDomain(review))
            showToast("Đã lưu bản phản tư Daily Review ($dateLocal) vào bộ nhớ cục bộ.")
        }
    }

    fun prepareExportJson() {
        val state = _uiState.value
        val json = LedgerJsonExporter.exportToJsonString(
            records = state.records,
            activeTimer = state.activeTimer,
            budgets = state.budgets,
            dailyReviews = state.dailyReviews,
            activityNotes = state.activityNotes,
            tags = state.tags,
            goals = state.goals
        )
        _uiState.update { it.copy(exportedJsonContent = json) }
    }

    fun closeExportJsonDialog() {
        _uiState.update { it.copy(exportedJsonContent = null) }
    }

    fun openImportJsonDialog() {
        _uiState.update { it.copy(importBackup = ImportBackupUiState()) }
    }

    fun updateImportJsonText(rawJson: String) {
        if (rawJson.isBlank()) {
            _uiState.update {
                it.copy(
                    importBackup = (it.importBackup ?: ImportBackupUiState()).copy(
                        rawJson = rawJson,
                        parsedPayload = null,
                        errorMessage = null
                    )
                )
            }
            return
        }
        when (val result = LedgerJsonBackupParser.parse(rawJson)) {
            is BackupParseResult.Valid -> {
                _uiState.update {
                    it.copy(
                        importBackup = (it.importBackup ?: ImportBackupUiState()).copy(
                            rawJson = rawJson,
                            parsedPayload = result.payload,
                            errorMessage = null
                        )
                    )
                }
            }
            is BackupParseResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        importBackup = (it.importBackup ?: ImportBackupUiState()).copy(
                            rawJson = rawJson,
                            parsedPayload = null,
                            errorMessage = result.reasonVi
                        )
                    )
                }
            }
        }
    }

    fun closeImportJsonDialog() {
        _uiState.update { it.copy(importBackup = null) }
    }

    fun confirmImportJson(mode: BackupRestoreMode) {
        val currentImport = _uiState.value.importBackup ?: return
        if (currentImport.isRestoring) return
        val parseResult = LedgerJsonBackupParser.parse(currentImport.rawJson)
        if (parseResult is BackupParseResult.Invalid) {
            _uiState.update {
                it.copy(
                    importBackup = currentImport.copy(
                        parsedPayload = null,
                        errorMessage = parseResult.reasonVi
                    )
                )
            }
            return
        }
        val payload = (parseResult as BackupParseResult.Valid).payload
        val previousRecordIds = _uiState.value.records.map { it.id }
        _uiState.update {
            it.copy(importBackup = currentImport.copy(parsedPayload = payload, errorMessage = null, isRestoring = true))
        }
        viewModelScope.launch(safeHandler) {
            val outcome = runCatching {
                LedgerBackupRestorer.restore(
                    database = database,
                    payload = payload,
                    mode = mode
                )
            }
            outcome.onSuccess { summary ->
                val nowMs = System.currentTimeMillis()
                if (mode == BackupRestoreMode.REPLACE_ALL) {
                    previousRecordIds.forEach { reminderScheduler.cancelReminder(it) }
                }
                val restoredDomain = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
                reminderScheduler.syncReminders(restoredDomain, nowMs)
                _uiState.update { it.copy(importBackup = null) }
                showToast(summary.toUserMessage())
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        importBackup = state.importBackup?.copy(
                            isRestoring = false,
                            errorMessage = err.message ?: "Khôi phục thất bại. Dữ liệu hiện tại được giữ nguyên."
                        )
                    )
                }
            }
        }
    }

    // ------------------- WALLY: NHẬP GIỜ THỦ CÔNG -------------------

    private fun syncManualDraftPersistence(form: ManualEntryState, immediate: Boolean = false) {
        val draft = ManualEntryDraftCoordinator.syncDraft(
            store = settingsStore,
            form = form,
            todayLocal = LocalDate.now().toString(),
            nowEpochMs = System.currentTimeMillis(),
            immediate = immediate
        )
        _uiState.update { it.copy(pendingManualDraft = draft) }
    }

    fun openManualEntry(categoryKey: String? = null) {
        if (_uiState.value.manualEntry != null) return
        val draft = _uiState.value.pendingManualDraft
            ?: runCatching { settingsStore.loadManualEntryDraft() }.getOrNull()
        val (restored, activeDraft) = ManualEntryDraftCoordinator.openOrRestore(
            storedDraft = draft,
            requestedCategoryKey = categoryKey,
            tags = _uiState.value.tags,
            todayLocal = LocalDate.now().toString()
        )
        _uiState.update {
            it.copy(
                manualEntry = restored,
                pendingManualDraft = activeDraft
            )
        }
    }

    /** v1.1.0: chọn Tag trong form — Category đi theo Tag (người dùng vẫn đổi được), tên Tag là tiêu đề mặc định. Chọn lại Tag đang chọn = bỏ chọn. */
    fun selectManualTag(tagId: String?) {
        updateManualEntry { f ->
            if (tagId == null || f.tagId == tagId) f.copy(tagId = null)
            else {
                val tag = _uiState.value.tags.firstOrNull { it.id == tagId && !it.isArchived } ?: return@updateManualEntry f
                f.copy(tagId = tag.id, categoryKey = tag.category.key)
            }
        }
    }

    /** Tạo Tag ngay trong form ghi nhận (Category = nhóm đang chọn) rồi chọn luôn Tag đó. */
    fun createTagFromManualEntry() {
        val form = _uiState.value.manualEntry ?: return
        val category = TimeCategory.fromKeyOrNull(form.categoryKey)
        if (category == null) {
            _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(error = "Hãy chọn nhóm cho Tag mới trước.")) }
            return
        }
        viewModelScope.launch(safeHandler) {
            when (val r = createTagInternal(form.newTagName, category)) {
                is TagActionResult.Created -> {
                    var updatedForm: ManualEntryState? = null
                    _uiState.update {
                        val next = it.manualEntry?.copy(
                            tagId = r.tag.id,
                            categoryKey = r.tag.category.key,
                            creatingTag = false,
                            newTagName = "",
                            error = null
                        )
                        updatedForm = next
                        it.copy(manualEntry = next)
                    }
                    updatedForm?.let { syncManualDraftPersistence(it) }
                }
                is TagActionResult.Failed -> _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(error = r.message)) }
            }
        }
    }

    fun updateManualEntry(transform: (ManualEntryState) -> ManualEntryState) {
        var nextSnapshot: ManualEntryState? = null
        _uiState.update { state ->
            val current = state.manualEntry ?: return@update state
            if (current.saving) return@update state
            val next = transform(current).copy(error = null)
            nextSnapshot = next
            state.copy(manualEntry = next)
        }
        nextSnapshot?.let { syncManualDraftPersistence(it) }
    }

    /** Thoát khỏi form KHÔNG xóa bản nháp; giữ nguyên để khôi phục khi quay lại hoặc mở lại ứng dụng. */
    fun closeManualEntry() {
        _uiState.value.manualEntry?.let { syncManualDraftPersistence(it, immediate = true) }
        _uiState.update { it.copy(manualEntry = null) }
    }

    /** Chủ động làm sạch biểu mẫu đang mở (nút Clean) và cấp sessionId mới cho lần nhập sạch. */
    fun cleanManualEntry() {
        _uiState.update { state ->
            val resetForm = ManualEntryDraftCoordinator.cleanAndReset(
                store = settingsStore,
                keepSheetOpen = state.manualEntry != null,
                todayLocal = LocalDate.now().toString()
            )
            state.copy(
                manualEntry = resetForm,
                pendingManualDraft = null
            )
        }
    }

    fun discardPendingManualDraft() {
        ManualEntryDraftCoordinator.clearOnCommitSuccess(settingsStore)
        _uiState.update { it.copy(pendingManualDraft = null) }
    }

    fun saveManualEntry() {
        val form = _uiState.value.manualEntry ?: return
        if (form.saving) return
        val zone = ZoneId.systemDefault()
        val tagName = form.tagId?.let { id -> _uiState.value.tags.firstOrNull { it.id == id }?.name }
        val validation = if (form.mode == ManualMode.RANGE) {
            ManualEntry.validateRange(
                categoryKey = form.categoryKey,
                tagId = form.tagId,
                tagName = tagName,
                startText = form.startText,
                endText = form.endText,
                endIsNow = form.endIsNow,
                dateLocal = form.dateLocal,
                note = form.note,
                today = LocalDate.now(zone),
                nowHm = TimeRange.formatHm(LocalTime.now(zone).hour * 60 + LocalTime.now(zone).minute),
                saveAsPlanned = form.saveAsPlanned
            )
        } else {
            ManualEntry.validate(
                categoryKey = form.categoryKey,
                hoursText = form.hoursText,
                minutesText = form.minutesText,
                dateLocal = form.dateLocal,
                note = form.note,
                today = LocalDate.now(zone),
                tagId = form.tagId,
                tagName = tagName,
                saveAsPlanned = form.saveAsPlanned
            )
        }
        if (validation is ManualEntryValidation.Invalid) {
            _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(error = validation.message)) }
            return
        }
        validation as ManualEntryValidation.Valid
        if (!manualSaveGuard.tryAcquire()) return
        _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(saving = true, error = null)) }

        val job = viewModelScope.launch(safeHandler) {
            val nowMs = System.currentTimeMillis()
            val batchInput = ManualEntry.toBatchInput(validation, form.sessionId)
            val range = if (!batchInput.startHm.isNullOrBlank() && !batchInput.endHm.isNullOrBlank()) {
                LedgerValidator.parseTimeRangeToEpochMillis(batchInput.dateLocal, batchInput.startHm, batchInput.endHm, zone)
            } else null

            if (range != null) {
                val existing = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
                val conflicts = ActivityBreakEngine.findAllOverlaps(
                    startMs = range.first,
                    endMs = range.second,
                    existingRecords = existing,
                    activeTimer = _uiState.value.activeTimer,
                    nowEpochMs = nowMs
                )
                if (conflicts.isNotEmpty()) {
                    val built = LedgerValidator.validateAndBuildRecord(
                        id = batchInput.id!!,
                        source = batchInput.source,
                        rawTitle = batchInput.rawTitle,
                        rawCategory = batchInput.rawCategory,
                        dateLocal = batchInput.dateLocal,
                        inputDurationMinutes = batchInput.durationMinutes,
                        startedAt = range.first,
                        endedAt = range.second,
                        existingRecords = existing.filterNot { c -> conflicts.any { it.id == c.id } },
                        activeTimer = null,
                        nowEpochMs = nowMs,
                        zoneId = zone,
                        idempotencyKey = batchInput.idempotencyKey!!,
                        convertFutureToPlanned = batchInput.convertFutureToPlanned,
                        tagId = batchInput.tagId
                    )
                    if (built is ValidationResult.Valid) {
                        _uiState.update {
                            it.copy(
                                manualEntry = it.manualEntry?.copy(saving = false),
                                pendingOverlapConflict = PendingOverlapConflict(
                                    incoming = built.record,
                                    conflicts = conflicts,
                                    closeManualOnResolve = true
                                )
                            )
                        }
                        return@launch
                    }
                }
            }

            val result = BatchConfirm.run(
                dao = ledgerDao,
                inputs = listOf(batchInput),
                activeTimer = _uiState.value.activeTimer,
                readActiveTimer = { settingsStore.loadActiveTimer() },
                nowEpochMs = nowMs,
                zone = zone
            )
            when (result) {
                is ConfirmResult.Invalid ->
                    _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(saving = false, error = result.message)) }
                is ConfirmResult.Aborted ->
                    _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(saving = false, error = result.message)) }
                is ConfirmResult.Committed -> {
                    ManualEntryDraftCoordinator.clearOnCommitSuccess(settingsStore)
                    val needsReminderPermission = validation.saveAsPlanned || (range != null && range.second > nowMs)
                    _uiState.update {
                        it.copy(
                            manualEntry = null,
                            pendingManualDraft = null,
                            requestNotificationPermissionNonce = if (needsReminderPermission && result.savedCount > 0) {
                                it.requestNotificationPermissionNonce + 1
                            } else {
                                it.requestNotificationPermissionNonce
                            }
                        )
                    }
                    showToast(
                        if (result.savedCount > 0) {
                            val label = if (validation.saveAsPlanned) "Dự kiến" else validation.category.displayName
                            "Đã ghi ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(validation.durationMinutes * 60L)} $label vào ${validation.dateLocal}."
                        } else {
                            ConfirmFeedback.message(0, result.skippedDuplicates)
                        }
                    )
                }
            }
        }
        job.invokeOnCompletion {
            manualSaveGuard.release()
            // Nếu coroutine chết vì lỗi, mở lại form để người dùng thử lại (không mất dữ liệu đã nhập).
            if (it != null) _uiState.update { s -> s.copy(manualEntry = s.manualEntry?.copy(saving = false)) }
        }
    }

    // ------------------- v1.1.0: TAG CÁ NHÂN -------------------

    sealed class TagActionResult {
        data class Created(val tag: Tag) : TagActionResult()
        data class Failed(val message: String) : TagActionResult()
    }

    /** Tạo (hoặc khôi phục nếu đã lưu trữ cùng tên) một Tag. Không bao giờ tạo trùng tên; không đụng bản ghi nào. */
    private suspend fun createTagInternal(rawName: String, category: TimeCategory): TagActionResult {
        val now = System.currentTimeMillis()
        val existing = tagDao.getAllSnapshot().map { it.toDomain() }
        return when (val check = TagRules.checkName(rawName, existing)) {
            is TagRules.NameCheck.Invalid -> TagActionResult.Failed(check.message)
            is TagRules.NameCheck.Duplicate -> {
                if (check.existing.isArchived) {
                    val restored = TagRules.restore(check.existing, now)
                    tagDao.update(TagEntity.fromDomain(restored))
                    showToast("Đã khôi phục Tag \"${restored.name}\" (đã có từ trước).")
                    TagActionResult.Created(restored)
                } else TagActionResult.Failed("Đã có Tag \"${check.existing.name}\".")
            }
            is TagRules.NameCheck.Ok -> {
                val tag = TagRules.newTag(check.cleanName, check.nameKey, category, now)
                try {
                    tagDao.insert(TagEntity.fromDomain(tag))
                    TagActionResult.Created(tag)
                } catch (e: android.database.sqlite.SQLiteConstraintException) {
                    TagActionResult.Failed("Đã có Tag trùng tên.")
                }
            }
        }
    }

    fun openTagManager() { _uiState.update { it.copy(tagManager = TagManagerState()) } }
    fun closeTagManager() { _uiState.update { it.copy(tagManager = null) } }
    fun updateTagManager(transform: (TagManagerState) -> TagManagerState) {
        _uiState.update { s -> s.tagManager?.let { s.copy(tagManager = transform(it).copy(error = null)) } ?: s }
    }

    fun createTagFromManager() {
        val m = _uiState.value.tagManager ?: return
        val category = TimeCategory.fromKeyOrNull(m.newCategoryKey)
        if (category == null) {
            _uiState.update { it.copy(tagManager = it.tagManager?.copy(error = "Chọn nhóm cho Tag trước khi tạo.")) }
            return
        }
        viewModelScope.launch(safeHandler) {
            when (val r = createTagInternal(m.newName, category)) {
                is TagActionResult.Created -> {
                    _uiState.update { it.copy(tagManager = it.tagManager?.copy(newName = "", error = null)) }
                    showToast("Đã tạo Tag \"${r.tag.name}\".")
                }
                is TagActionResult.Failed -> _uiState.update { it.copy(tagManager = it.tagManager?.copy(error = r.message)) }
            }
        }
    }

    fun renameTag(tagId: String) {
        val m = _uiState.value.tagManager ?: return
        viewModelScope.launch(safeHandler) {
            val all = tagDao.getAllSnapshot().map { it.toDomain() }
            val tag = all.firstOrNull { it.id == tagId } ?: return@launch
            when (val check = TagRules.checkName(m.renameText, all, excludeId = tagId)) {
                is TagRules.NameCheck.Invalid -> _uiState.update { it.copy(tagManager = it.tagManager?.copy(error = check.message)) }
                is TagRules.NameCheck.Duplicate -> _uiState.update { it.copy(tagManager = it.tagManager?.copy(error = "Đã có Tag \"${check.existing.name}\".")) }
                is TagRules.NameCheck.Ok -> {
                    tagDao.update(TagEntity.fromDomain(TagRules.rename(tag, check.cleanName, check.nameKey, System.currentTimeMillis())))
                    _uiState.update { it.copy(tagManager = it.tagManager?.copy(renamingId = null, renameText = "", error = null)) }
                    showToast("Đã đổi tên Tag. Lịch sử không thay đổi.")
                }
            }
        }
    }

    fun setTagCategory(tagId: String, categoryKey: String) {
        val category = TimeCategory.fromKeyOrNull(categoryKey) ?: return
        viewModelScope.launch(safeHandler) {
            val tag = tagDao.getAllSnapshot().map { it.toDomain() }.firstOrNull { it.id == tagId } ?: return@launch
            tagDao.update(TagEntity.fromDomain(TagRules.recategorize(tag, category, System.currentTimeMillis())))
            _uiState.update { it.copy(tagManager = it.tagManager?.copy(recategorizingId = null)) }
            showToast("Đã đổi nhóm của Tag. Bản ghi cũ giữ nhóm đã ghi nhận.")
        }
    }

    fun archiveTag(tagId: String) {
        viewModelScope.launch(safeHandler) {
            val tag = tagDao.getAllSnapshot().map { it.toDomain() }.firstOrNull { it.id == tagId } ?: return@launch
            tagDao.update(TagEntity.fromDomain(TagRules.archive(tag, System.currentTimeMillis())))
            showToast("Đã lưu trữ Tag \"${tag.name}\". Lịch sử và tiến độ được giữ nguyên.")
        }
    }

    fun restoreTag(tagId: String) {
        viewModelScope.launch(safeHandler) {
            val tag = tagDao.getAllSnapshot().map { it.toDomain() }.firstOrNull { it.id == tagId } ?: return@launch
            tagDao.update(TagEntity.fromDomain(TagRules.restore(tag, System.currentTimeMillis())))
            showToast("Đã khôi phục Tag \"${tag.name}\".")
        }
    }

    /** Chọn Tag cho phiên đồng hồ sắp bắt đầu: Category và tên hoạt động đi theo Tag (sửa lại được). Chọn lại Tag đang chọn = bỏ chọn. */
    fun selectTimerTag(tagId: String?) {
        _uiState.update { s ->
            if (s.activeTimer != null) return@update s
            if (tagId == null || s.timerInputTagId == tagId) s.copy(timerInputTagId = null)
            else {
                val tag = s.tags.firstOrNull { it.id == tagId && !it.isArchived } ?: return@update s
                s.copy(timerInputTagId = tag.id, timerInputCategoryKey = tag.category.key, timerInputTitle = tag.name)
            }
        }
        persistPreTimerDraft()
    }

    // ------------------- v1.1.0: MỤC TIÊU -------------------

    fun openGoals() { _uiState.update { it.copy(showGoals = true) } }
    fun closeGoals() { _uiState.update { it.copy(showGoals = false, goalEditor = null) } }

    fun openGoalEditor(groupId: String? = null) {
        val s = _uiState.value
        val editing = groupId?.let { g -> GoalRules.latest(s.goals.filter { it.groupId == g }) }
        _uiState.update {
            it.copy(
                goalEditor = if (editing == null) GoalEditorState()
                else {
                    val m = (editing.targetSeconds / 60L).toInt()
                    GoalEditorState(editing.groupId, editing.targetType, editing.targetKey, editing.kind, editing.period,
                        if (m >= 60) (m / 60).toString() else "", (m % 60).toString())
                }
            )
        }
    }

    fun updateGoalEditor(transform: (GoalEditorState) -> GoalEditorState) {
        _uiState.update { s -> s.goalEditor?.let { s.copy(goalEditor = transform(it).copy(error = null)) } ?: s }
    }

    fun closeGoalEditor() { _uiState.update { it.copy(goalEditor = null) } }

    private fun persistGoalRows(rows: List<Goal>) {
        viewModelScope.launch(safeHandler) {
            database.withTransaction { rows.forEach { goalDao.upsert(GoalEntity.fromDomain(it)) } }
        }
    }

    fun saveGoalEditor() {
        val s = _uiState.value
        val ed = s.goalEditor ?: return
        val h = ed.hoursText.trim().ifEmpty { "0" }.toIntOrNull()
        val m = ed.minutesText.trim().ifEmpty { "0" }.toIntOrNull()
        if (h == null || m == null || h < 0 || m !in 0..59) {
            _uiState.update { it.copy(goalEditor = it.goalEditor?.copy(error = "Hãy nhập giờ và phút hợp lệ.")) }
            return
        }
        val seconds = (h * 60L + m) * 60L
        (GoalRules.checkTarget(ed.period, seconds) as? GoalRules.Check.Invalid)?.let { bad ->
            _uiState.update { it.copy(goalEditor = it.goalEditor?.copy(error = bad.message)) }
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        if (ed.editingGroupId != null) {
            val open = GoalRules.latest(s.goals.filter { it.groupId == ed.editingGroupId })
            if (open == null || open.validTo != null) {
                _uiState.update { it.copy(goalEditor = it.goalEditor?.copy(error = "Mục tiêu này đã kết thúc.")) }
                return
            }
            persistGoalRows(GoalRules.change(open, today, now, newTargetSeconds = seconds, newKind = ed.kind, newPeriod = ed.period))
            _uiState.update { it.copy(goalEditor = null) }
            showToast("Đã cập nhật mục tiêu từ hôm nay. Dữ liệu quá khứ không đổi.")
            return
        }
        val key = ed.targetKey
        if (key == null) {
            _uiState.update { it.copy(goalEditor = it.goalEditor?.copy(error = "Hãy chọn hoạt động (Tag) hoặc nhóm cho mục tiêu.")) }
            return
        }
        val dup = s.goals.groupBy { it.groupId }.values.any { vs ->
            val last = GoalRules.latest(vs)
            last != null && last.validTo == null && last.targetType == ed.targetType && last.targetKey == key && last.kind == ed.kind && last.period == ed.period
        }
        if (dup) {
            _uiState.update { it.copy(goalEditor = it.goalEditor?.copy(error = "Đã có mục tiêu giống vậy.")) }
            return
        }
        persistGoalRows(listOf(GoalRules.create(ed.targetType, key, ed.kind, ed.period, seconds, today, now)))
        _uiState.update { it.copy(goalEditor = null) }
        showToast("Đã tạo mục tiêu.")
    }

    private fun openVersionOf(groupId: String): Goal? = GoalRules.latest(_uiState.value.goals.filter { it.groupId == groupId })?.takeIf { it.validTo == null }

    fun pauseGoal(groupId: String) {
        val open = openVersionOf(groupId) ?: return
        if (open.status == GoalVersionStatus.PAUSED) return
        persistGoalRows(GoalRules.change(open, LocalDate.now(), System.currentTimeMillis(), newStatus = GoalVersionStatus.PAUSED))
        showToast("Đã tạm dừng mục tiêu. Tiến độ trước đây được giữ.")
    }

    fun resumeGoal(groupId: String) {
        val open = openVersionOf(groupId) ?: return
        if (open.status == GoalVersionStatus.ACTIVE) return
        persistGoalRows(GoalRules.change(open, LocalDate.now(), System.currentTimeMillis(), newStatus = GoalVersionStatus.ACTIVE))
        showToast("Đã tiếp tục mục tiêu từ hôm nay.")
    }

    fun endGoal(groupId: String) {
        val open = openVersionOf(groupId) ?: return
        persistGoalRows(listOf(GoalRules.end(open, LocalDate.now(), System.currentTimeMillis())))
        showToast("Đã kết thúc mục tiêu. Lịch sử được giữ nguyên.")
    }

    // ------------------- v1.1.0: AI TIME COACH -------------------

    private var coachJob: Job? = null

    /** Số liệu do ứng dụng tính hiện ngay; AI chỉ kể chuyện (nếu đã bật). Câu chuyện chỉ hiện khi qua [CoachGuard]. */
    fun requestCoach(days: Int) {
        val s = _uiState.value
        if (s.coach.loading) return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val digest = CoachDigestBuilder.build(s.records, s.tags, s.goals, days, now, zone)
        val prefs = s.aiPreferences
        if (!digest.hasAnyData && !digest.hasPlanOrStateHistory) {
            _uiState.update { it.copy(coach = CoachUiState(rangeDays = days, facts = digest.facts, requested = true,
                note = "Chưa có dữ liệu trong kỳ này nên Wally chưa có gì để kể. Hãy ghi nhận vài hoạt động trước.")) }
            return
        }
        if (!prefs.optInEnabled || prefs.apiKey.isBlank()) {
            _uiState.update { it.copy(coach = CoachUiState(rangeDays = days, facts = digest.facts, requested = true,
                note = "Bật AI trong Cấu hình AI để Wally kể câu chuyện từ số liệu. Số liệu bên trên do ứng dụng tính, không cần AI.")) }
            return
        }
        _uiState.update { it.copy(coach = CoachUiState(loading = true, rangeDays = days, facts = digest.facts, requested = true)) }
        coachJob?.cancel()
        coachJob = viewModelScope.launch(safeHandler) {
            val raw = GeminiProposalClient.requestCoach(
                digestText = digest.text, optInEnabled = prefs.optInEnabled, baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey, modelName = prefs.modelName, trustedHost = prefs.trustedHost
            )
            val fresh = _uiState.value
            val coach = when {
                raw.fault != null -> CoachUiState(rangeDays = days, facts = digest.facts, requested = true,
                    note = (raw.fault.messageVi) + " Số liệu bên trên vẫn do ứng dụng tính.")
                else -> when (CoachGuard.check(raw.story, digest)) {
                    CoachCheck.Ok -> when (
                        val structured = CoachGuard.checkStructured(
                            rawStory = raw.story,
                            rawHeadline = raw.headline,
                            rawHighlights = raw.highlights,
                            rawWatchout = raw.watchout,
                            rawActions = raw.actionItems,
                            digest = digest,
                            tags = fresh.tags
                        )
                    ) {
                        is CoachStructuredCheck.Ok -> CoachUiState(
                            rangeDays = days,
                            facts = digest.facts,
                            requested = true,
                            story = structured.story,
                            brief = structured.brief,
                            suggestions = CoachGuard.validSuggestions(raw.suggestions, fresh.tags, fresh.goals)
                        )
                        is CoachStructuredCheck.Rejected -> CoachUiState(
                            rangeDays = days,
                            facts = digest.facts,
                            requested = true,
                            note = "Wally chưa kể được câu chuyện đủ chắc chắn từ dữ liệu này, nên chỉ hiện số liệu do ứng dụng tính."
                        )
                    }
                    is CoachCheck.Rejected -> CoachUiState(rangeDays = days, facts = digest.facts, requested = true,
                        note = "Wally chưa kể được câu chuyện đủ chắc chắn từ dữ liệu này, nên chỉ hiện số liệu do ứng dụng tính.")
                }
            }
            _uiState.update { it.copy(coach = coach) }
        }
    }

    fun dismissCoach() { coachJob?.cancel(); _uiState.update { it.copy(coach = CoachUiState()) } }

    /** Đề xuất của Coach chỉ thành mục tiêu khi người dùng bấm Xác nhận. */
    fun confirmCoachSuggestion(index: Int) {
        val s = _uiState.value
        val sug = s.coach.suggestions.getOrNull(index) ?: return
        val dup = s.goals.groupBy { it.groupId }.values.any { vs ->
            val last = GoalRules.latest(vs)
            last != null && last.validTo == null && last.targetType == sug.targetType && last.targetKey == sug.targetKey && last.kind == sug.kind && last.period == sug.period
        }
        if (!dup) persistGoalRows(listOf(GoalRules.create(sug.targetType, sug.targetKey, sug.kind, sug.period, sug.targetSeconds, LocalDate.now(), System.currentTimeMillis())))
        _uiState.update { it.copy(coach = it.coach.copy(suggestions = it.coach.suggestions.filterIndexed { i, _ -> i != index })) }
        showToast(if (dup) "Mục tiêu này đã có." else "Đã tạo mục tiêu từ gợi ý của Wally.")
    }

    fun dismissCoachSuggestion(index: Int) {
        _uiState.update { it.copy(coach = it.coach.copy(suggestions = it.coach.suggestions.filterIndexed { i, _ -> i != index })) }
    }

    /** Action Item của Coach chỉ mở biểu mẫu/màn hình tương ứng (hoặc điền sẵn bản nháp mới), không tự ghi vào sổ cái. */
    fun triggerCoachAction(action: CoachActionItem) {
        val current = _uiState.value
        when (action.kind) {
            CoachActionKind.PLAN -> {
                if (current.pendingManualDraft != null) {
                    openManualEntry(action.targetCategoryKey)
                    showToast("Đang có bản nháp nhập dở; hoàn tất hoặc Clean trước khi lên lịch mới.")
                    return
                }
                val mins = action.durationMinutes ?: 30
                val draftForm = ManualEntryState(
                    categoryKey = action.targetCategoryKey,
                    hoursText = (mins / 60).takeIf { it > 0 }?.toString().orEmpty(),
                    minutesText = (mins % 60).takeIf { it > 0 || mins < 60 }?.toString().orEmpty(),
                    dateLocal = current.todayDateLocal,
                    note = action.targetLabel ?: action.title,
                    mode = ManualMode.DURATION,
                    tagId = action.targetTagId,
                    saveAsPlanned = true
                )
                _uiState.update { it.copy(manualEntry = draftForm) }
            }
            CoachActionKind.TIMER -> {
                if (current.activeTimer != null) {
                    _uiState.update { it.copy(currentTab = AppTab.TIMER) }
                    showToast("Đồng hồ đang đo phiên hiện tại.")
                    return
                }
                _uiState.update {
                    it.copy(
                        currentTab = AppTab.TIMER,
                        timerInputTitle = action.targetLabel ?: action.title,
                        timerInputCategoryKey = action.targetCategoryKey ?: it.timerInputCategoryKey,
                        timerInputTagId = action.targetTagId ?: it.timerInputTagId
                    )
                }
            }
            CoachActionKind.TAG -> openTagManager()
            CoachActionKind.REFLECT -> openDailyReview(current.todayDateLocal)
        }
    }

    fun clearCrashReport() {
        CrashRecorder.clear()
        _uiState.update { it.copy(lastCrashReport = null) }
    }

    fun toggleProductInfoDialog(show: Boolean) {
        _uiState.update { it.copy(showProductInfoDialog = show) }
    }

    // v1.0.13 (U2): kết quả của một lần kiểm tra thuộc về đúng phiên hộp thoại/khóa/URL đã bấm; đóng hộp thoại thì hủy lượt đang chạy để kết quả muộn không hiện vào phiên sau.
    private var aiTestJob: Job? = null

    fun toggleAiSettingsDialog(show: Boolean) {
        aiTestJob?.cancel()
        _uiState.update {
            it.copy(
                showAiSettingsDialog = show,
                aiConnectionTestMessage = null,
                aiConnectionTesting = false,
                aiDiagnosticsText = if (show) aiDiagnosticsReport() else ""
            )
        }
    }

    /** Tổng hợp số liệu + các yêu cầu gần nhất. Không chứa khóa, prompt, câu chat hay dữ liệu sổ cái. */
    private fun aiDiagnosticsReport(): String =
        AiDiagnostics.summaryText(GeminiProposalClient.PROMPT_VERSION, GeminiProposalClient.staticPromptHash) +
            "\n\n" + AiDiagnostics.asText()

    /** Kiểm tra kết nối bằng payload tổng hợp; hiển thị lỗi cụ thể, không lưu gì. */
    fun testAiConnection(baseUrl: String, apiKey: String, modelName: String) {
        if (_uiState.value.aiConnectionTesting) return
        _uiState.update { it.copy(aiConnectionTesting = true, aiConnectionTestMessage = null) }
        aiTestJob = viewModelScope.launch(safeHandler) {
            val result = try {
                GeminiProposalClient.testConnection(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName)
            } finally {
                _uiState.update { it.copy(aiConnectionTesting = false) }
            }
            _uiState.update {
                it.copy(
                    aiConnectionTestMessage = (if (result.ok) "✓ " else "✗ ") + result.detailVi +
                        (result.httpStatus?.let { s -> " (HTTP $s)" } ?: "") + " · mã ${result.requestId}",
                    aiDiagnosticsText = aiDiagnosticsReport()
                )
            }
        }
    }

    fun saveAiPreferences(optInEnabled: Boolean, baseUrl: String, apiKey: String, modelName: String) {
        val cleanUrl = baseUrl.trim().ifEmpty { AiPreferences.DEFAULT_AI_BASE_URL }
        val validatedHost = AiPreferences.extractValidatedHttpsHost(cleanUrl)
        if (validatedHost == null) {
            showToast("Base URL bắt buộc phải dùng giao thức https:// hợp lệ.")
            return
        }

        val currentPrefs = _uiState.value.aiPreferences
        val isHostChanged = currentPrefs.trustedHost.isNotBlank() &&
            !validatedHost.equals(currentPrefs.trustedHost, ignoreCase = true)

        // v1.0.12 (P1-AI): chuẩn hóa khóa dán vào và báo rõ nếu còn ký tự ngoài ASCII in được (quy tắc sản phẩm: khóa nhà cung cấp là ASCII; không lưu khóa hỏng).
        val cleanKey = ApiKeyHygiene.normalize(apiKey)
        ApiKeyHygiene.check(cleanKey)?.let { p ->
            showToast("Khóa API có ký tự không hợp lệ ở vị trí ${p.position} (${p.kindVi}). Hãy dán lại khóa.")
            return
        }
        if (isHostChanged && cleanKey.isNotEmpty() && cleanKey == currentPrefs.apiKey) {
            showToast("Bạn vừa đổi sang host mới ($validatedHost). Hãy nhập lại khóa API dành cho host này.")
            return
        }

        val candidate = AiPreferences(
            optInEnabled = optInEnabled,
            baseUrl = cleanUrl,
            apiKey = cleanKey,
            modelName = modelName.trim().ifEmpty { AiPreferences.DEFAULT_AI_MODEL },
            trustedHost = validatedHost
        )
        if (!settingsStore.saveAiPreferences(candidate, hostChangeConfirmed = true, keepStoredKeyIfBlank = currentPrefs.keyUnavailable)) {
            showToast("Không lưu được khóa API an toàn trên thiết bị này. Hãy thử lại; sổ cái và nhập thủ công không bị ảnh hưởng.")
            return
        }
        val reloaded = settingsStore.loadAiPreferences()
        _uiState.update {
            it.copy(
                aiPreferences = reloaded,
                showAiSettingsDialog = false
            )
        }
        showToast(
            if (reloaded.optInEnabled && reloaded.apiKey.isNotEmpty()) {
                "Đã bật AI qua host ${reloaded.trustedHost} (${reloaded.modelName}). Mọi đề xuất luôn cần bạn xác nhận."
            } else {
                "Wally chưa có AI: hãy nhập khóa API và bật AI. Ghi thủ công và các tab khác vẫn dùng bình thường."
            }
        )
    }
}
