package com.timewallet.mvp.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.GuardHealth
import com.timewallet.mvp.data.local.ActivityReviewNoteEntity
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.CategoryBudgetEntity
import com.timewallet.mvp.data.local.DailyReviewEntity
import com.timewallet.mvp.data.local.DbCommitOutcome
import com.timewallet.mvp.data.local.LedgerJsonExporter
import com.timewallet.mvp.data.local.LedgerRecordEntity
import com.timewallet.mvp.data.local.TimeWalletDatabase
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.local.TimerCommit
import com.timewallet.mvp.data.local.TimerCommitResult
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
    val idempotencyKey: String = UUID.randomUUID().toString()
) {
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
    val error: String? = null
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
    val showProductInfoDialog: Boolean = false,
    val showAiSettingsDialog: Boolean = false,
    val manualEntry: ManualEntryState? = null,
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
    private val settingsStore = TimerAndSettingsStore(application)

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
        cancelArming = CancelArming()
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
        _uiState.update { state ->
            state.copy(
                lastCrashReport = CrashRecorder.readLast(),
                activeTimer = restoredTimer,
                timerInputTitle = restoredTimer?.title ?: state.timerInputTitle,
                timerInputCategoryKey = restoredTimer?.categoryKey ?: state.timerInputCategoryKey,
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

        // Quan sát toàn bộ sổ cái từ Room (bảo toàn dữ liệu cũ)
        viewModelScope.launch(safeHandler) {
            ledgerDao.observeAllRecords().collect { entities ->
                val domainRecords = entities.map { it.toDomain() }
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

    /** Ngừng vòng cập nhật khi ứng dụng không còn hiển thị (đồng hồ vẫn đúng vì tính từ mốc thời gian, không từ số lần tick). */
    fun onAppPaused() {
        tickerJob?.cancel()
        tickerJob = null
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
        _uiState.update { it.copy(timerInputTitle = newTitle.take(110)) }
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
            startedBootCount = currentBootCount
        )
        settingsStore.saveActiveTimer(session)
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
                zone = zone
            )) {
                is TimerCommitResult.Overlap -> showToast(
                    result.message + " Phiên đo vẫn được giữ: hãy sửa hoặc xóa bản ghi trùng rồi bấm Dừng & Lưu lại."
                )
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
    }

    fun applyChatExample(example: String) {
        _uiState.update { it.copy(chatInputText = example) }
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

    private fun confirmProposalsList(drafts: List<EditableProposalDraft>) {
        val zone = ZoneId.systemDefault()

        // WALLY A08: một lượt xác nhận tại một thời điểm; bấm lặp không tạo lượt ghi thứ hai.
        if (!confirmGuard.tryAcquire()) {
            showToast("Đang lưu, vui lòng chờ giây lát.")
            return
        }
        val job = viewModelScope.launch(safeHandler) {
            val result = BatchConfirm.run(
                dao = ledgerDao,
                inputs = drafts.map { it.toBatchInput() },
                activeTimer = _uiState.value.activeTimer,
                readActiveTimer = { settingsStore.loadActiveTimer() },
                nowEpochMs = System.currentTimeMillis(),
                zone = zone
            )
            when (result) {
                is ConfirmResult.Invalid -> {
                    showToast("Đã hủy lưu lô để bảo toàn dữ liệu: ${result.message}")
                    return@launch
                }
                is ConfirmResult.Aborted -> {
                    showToast("Đã hủy lưu lô: ${result.message}")
                    return@launch
                }
                is ConfirmResult.Committed -> {
                    val confirmedIds = drafts.map { it.id }.toSet()
                    _uiState.update { state ->
                        val remaining = state.proposals.filterNot { confirmedIds.contains(it.id) }
                        state.copy(
                            proposals = remaining,
                            expandedProposalId = state.expandedProposalId?.takeIf { id -> remaining.any { it.id == id } },
                            // Thẻ trạng thái ("còn N đề xuất đang chờ") sẽ cũ ngay sau khi commit: xóa; chỉ toast do ứng dụng báo "Đã lưu" sau commit.
                            chatAnswer = state.chatAnswer?.takeUnless { it.isTransactionStatus }
                        )
                    }
                    showToast(ConfirmFeedback.message(result.savedCount, result.skippedDuplicates))
                }
            }
        }
        job.invokeOnCompletion { confirmGuard.release() }
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
     * Xác nhận chuyển bản ghi Dự kiến (PLANNED) hoặc Đang diễn ra (ACTIVE) thành Thực tế đã sử dụng.
     */
    fun confirmPlannedOrActiveRecord(record: LedgerRecord) {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()

        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            val validation = LedgerValidator.confirmPlannedOrActiveToActual(
                record = record,
                existingRecords = currentRecords,
                activeTimer = _uiState.value.activeTimer,
                nowEpochMs = nowMs,
                zoneId = zone
            )
            when (validation) {
                is ValidationResult.Invalid -> showToast(validation.message)
                is ValidationResult.Valid -> {
                    val commit = ledgerDao.commitRecordTransactionally(
                        entity = LedgerRecordEntity.fromDomain(validation.record),
                        isUpdateExisting = true
                    )
                    when (commit) {
                        is DbCommitOutcome.OverlapRejected -> {
                            showToast("Bị trùng mốc giờ thực tế với \"${commit.conflictingTitle}\".")
                        }
                        is DbCommitOutcome.InvalidEntityRejected -> {
                            showToast(commit.reason)
                        }
                        DbCommitOutcome.Saved, DbCommitOutcome.DuplicateIgnored -> {
                            showToast("Đã xác nhận \"${record.title}\" thành thời gian thực tế.")
                        }
                    }
                }
            }
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
        val st = newStartHm.trim()
        val en = newEndHm.trim()
        if ((st.isNotEmpty() && en.isEmpty()) || (st.isEmpty() && en.isNotEmpty())) {
            showToast("Hãy nhập cả giờ bắt đầu và giờ kết thúc, hoặc bỏ trống cả hai.")
            return
        }

        val zone = ZoneId.systemDefault()
        var startMs: Long? = null
        var endMs: Long? = null
        if (st.isNotEmpty() && en.isNotEmpty()) {
            val parsed = LedgerValidator.parseTimeRangeToEpochMillis(newDateLocal, st, en, zone)
            if (parsed == null) {
                showToast("Giờ bắt đầu/kết thúc không hợp lệ (định dạng HH:mm).")
                return
            }
            startMs = parsed.first
            endMs = parsed.second
        }

        val mins = if (startMs != null && endMs != null) {
            null
        } else {
            newDurationMinutesText.trim().toIntOrNull()
        }

        viewModelScope.launch(safeHandler) {
            val currentRecords = ledgerDao.getAllRecordsSnapshot().map { it.toDomain() }
            val effectiveSource = if (existing.source == "timer" && startMs != null &&
                startMs == existing.startedAt && endMs == existing.endedAt
            ) {
                "timer"
            } else {
                "manual"
            }

            val validation = LedgerValidator.validateAndBuildRecord(
                id = existing.id,
                source = effectiveSource,
                rawTitle = newTitle,
                rawCategory = newCategoryKey,
                dateLocal = newDateLocal,
                inputDurationMinutes = mins,
                startedAt = startMs,
                endedAt = endMs,
                existingRecords = currentRecords,
                activeTimer = _uiState.value.activeTimer,
                nowEpochMs = System.currentTimeMillis(),
                zoneId = zone,
                idempotencyKey = existing.idempotencyKey,
                excludeRecordId = existing.id,
                convertFutureToPlanned = allowFutureAsPlanned
            )

            when (validation) {
                is ValidationResult.Invalid -> showToast(validation.message)
                is ValidationResult.Valid -> {
                    val updated = validation.record.copy(createdAt = existing.createdAt)
                    val commit = ledgerDao.commitRecordTransactionally(
                        entity = LedgerRecordEntity.fromDomain(updated),
                        isUpdateExisting = true
                    )
                    when (commit) {
                        is DbCommitOutcome.OverlapRejected -> {
                            showToast("Bị trùng mốc giờ với \"${commit.conflictingTitle}\". Chưa lưu.")
                        }
                        is DbCommitOutcome.InvalidEntityRejected -> {
                            showToast(commit.reason)
                        }
                        DbCommitOutcome.Saved, DbCommitOutcome.DuplicateIgnored -> {
                            _uiState.update { it.copy(editingRecord = null) }
                            showToast("Đã cập nhật bản ghi.")
                        }
                    }
                }
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch(safeHandler) {
            // WALLY: bản ghi và ghi chú liên quan cùng bị xóa hoặc cùng hoàn tác (một giao dịch Room).
            database.withTransaction {
                ledgerDao.deleteById(recordId)
                dailyReviewDao.deleteActivityNoteByRecordId(recordId)
            }
            showToast("Đã xóa bản ghi.")
        }
    }

    fun clearAllData() {
        if (_uiState.value.activeTimer != null) {
            showToast("Hãy kết thúc đồng hồ đang chạy trước khi xóa toàn bộ dữ liệu.")
            return
        }
        viewModelScope.launch(safeHandler) {
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
            activityNotes = state.activityNotes
        )
        _uiState.update { it.copy(exportedJsonContent = json) }
    }

    fun closeExportJsonDialog() {
        _uiState.update { it.copy(exportedJsonContent = null) }
    }

    // ------------------- WALLY: NHẬP GIỜ THỦ CÔNG -------------------

    fun openManualEntry(categoryKey: String? = null) {
        _uiState.update {
            it.copy(manualEntry = ManualEntryState(categoryKey = categoryKey, dateLocal = LocalDate.now().toString()))
        }
    }

    fun updateManualEntry(transform: (ManualEntryState) -> ManualEntryState) {
        _uiState.update { state ->
            val current = state.manualEntry ?: return@update state
            if (current.saving) state else state.copy(manualEntry = transform(current).copy(error = null))
        }
    }

    fun closeManualEntry() {
        _uiState.update { it.copy(manualEntry = null) }
    }

    fun saveManualEntry() {
        val form = _uiState.value.manualEntry ?: return
        if (form.saving) return
        val zone = ZoneId.systemDefault()
        val validation = ManualEntry.validate(
            categoryKey = form.categoryKey,
            hoursText = form.hoursText,
            minutesText = form.minutesText,
            dateLocal = form.dateLocal,
            note = form.note,
            today = LocalDate.now(zone)
        )
        if (validation is ManualEntryValidation.Invalid) {
            _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(error = validation.message)) }
            return
        }
        validation as ManualEntryValidation.Valid
        if (!manualSaveGuard.tryAcquire()) return
        _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(saving = true, error = null)) }

        val job = viewModelScope.launch(safeHandler) {
            val result = BatchConfirm.run(
                dao = ledgerDao,
                inputs = listOf(ManualEntry.toBatchInput(validation, form.sessionId)),
                activeTimer = _uiState.value.activeTimer,
                readActiveTimer = { settingsStore.loadActiveTimer() },
                nowEpochMs = System.currentTimeMillis(),
                zone = zone
            )
            when (result) {
                is ConfirmResult.Invalid ->
                    _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(saving = false, error = result.message)) }
                is ConfirmResult.Aborted ->
                    _uiState.update { it.copy(manualEntry = it.manualEntry?.copy(saving = false, error = result.message)) }
                is ConfirmResult.Committed -> {
                    _uiState.update { it.copy(manualEntry = null) }
                    showToast(
                        if (result.savedCount > 0) {
                            "Đã ghi ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(validation.durationMinutes * 60L)} ${validation.category.displayName} vào ${validation.dateLocal}."
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
