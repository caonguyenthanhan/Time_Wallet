package com.timewallet.mvp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timewallet.mvp.notifications.CompletionNotificationHelper
import com.timewallet.mvp.notifications.FocusPinNotificationManager
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.TimeWalletViewModel
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.ThemeModePreference
import com.timewallet.mvp.ui.screens.ActivityConfirmationDialog
import com.timewallet.mvp.ui.screens.AiSettingsDialog
import com.timewallet.mvp.ui.screens.OverlapResolutionDialog
import com.timewallet.mvp.ui.screens.BudgetDeleteConfirmDialog
import com.timewallet.mvp.ui.screens.BudgetEditorDraft
import com.timewallet.mvp.ui.screens.DailyReviewScreen
import com.timewallet.mvp.ui.screens.DataManagementDialog
import com.timewallet.mvp.ui.screens.EditRecordDialog
import com.timewallet.mvp.ui.screens.ExportJsonDialog
import com.timewallet.mvp.ui.screens.HistoryScreen
import com.timewallet.mvp.ui.screens.ImportJsonDialog
import com.timewallet.mvp.ui.screens.InsightsScreen
import com.timewallet.mvp.ui.screens.ManualEntrySheet
import com.timewallet.mvp.ui.screens.MarkdownExportDialog
import com.timewallet.mvp.ui.screens.TaskCompletionLogPromptDialog
import com.timewallet.mvp.ui.screens.TasksHabitsScreen
import com.timewallet.mvp.ui.screens.UniversalQuickCaptureDialog
import com.timewallet.mvp.ui.screens.WallyAiOverlay
import com.timewallet.mvp.ui.screens.AppIntroDialog
import com.timewallet.mvp.ui.screens.GoalEditorDialog
import com.timewallet.mvp.ui.screens.GoalsDialog
import com.timewallet.mvp.ui.screens.ProfileSettingsScreen
import com.timewallet.mvp.ui.screens.ScheduleCalendarView
import com.timewallet.mvp.ui.screens.TagManagerDialog
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.TagRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.timewallet.mvp.ui.screens.GlyphIcon
import com.timewallet.mvp.ui.screens.ProductInfoDialog
import com.timewallet.mvp.ui.screens.TimeBudgetEditorDialog
import com.timewallet.mvp.ui.screens.TimeWalletBottomBar
import com.timewallet.mvp.ui.screens.TimeWalletTopBar
import com.timewallet.mvp.ui.screens.TimerScreen
import com.timewallet.mvp.ui.screens.WalletGlyph
import com.timewallet.mvp.ui.screens.walletClickable
import com.timewallet.mvp.ui.theme.TimeWalletTheme
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke

data class PendingFocusLaunch(
    val title: String?,
    val categoryKey: String?,
    val tagId: String?
)

class MainActivity : ComponentActivity() {
    private val pendingNotificationRecordId = mutableStateOf<String?>(null)
    private val pendingFocusLaunch = mutableStateOf<PendingFocusLaunch?>(null)

    private fun captureNotificationIntent(targetIntent: Intent?) {
        pendingNotificationRecordId.value = targetIntent?.getStringExtra(CompletionNotificationHelper.EXTRA_CONFIRM_RECORD_ID)
        if (targetIntent?.action == FocusPinNotificationManager.ACTION_OPEN_FOCUS_PIN) {
            pendingFocusLaunch.value = PendingFocusLaunch(
                title = targetIntent.getStringExtra(FocusPinNotificationManager.EXTRA_FOCUS_TITLE),
                categoryKey = targetIntent.getStringExtra(FocusPinNotificationManager.EXTRA_FOCUS_CATEGORY_KEY),
                tagId = targetIntent.getStringExtra(FocusPinNotificationManager.EXTRA_FOCUS_TAG_ID)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNotificationIntent(intent)
        setContent {
            TimeWalletTheme {
                TimeWalletAppRoot(
                    pendingNotificationRecordId = pendingNotificationRecordId.value,
                    onNotificationHandled = { pendingNotificationRecordId.value = null },
                    pendingFocusLaunch = pendingFocusLaunch.value,
                    onFocusLaunchHandled = { pendingFocusLaunch.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationIntent(intent)
    }
}

/**
 * v1.0.12 (P1-NAV): thanh tab chỉ ẩn khi bàn phím THẬT (ime inset ở đáy > 0) đang hiện ở Wally. Mọi trường hợp khác — kể cả khi hệ thống
 * không chuyển inset cho Compose (Android trước 15, LDPlayer Android 9) — thanh tab luôn hiện để người dùng không bị kẹt trong Wally.
 */
internal fun shouldShowBottomBar(currentTab: AppTab, imeBottomPx: Int): Boolean =
    imeBottomPx >= 0 && AppTab.entries.contains(currentTab)

/** Nút Lên đầu trang chỉ hiện khi cuộn vượt quá 400 px. */
internal fun shouldShowBackToTop(scrollPx: Int): Boolean = scrollPx > 400

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeWalletAppRoot(
    viewModel: TimeWalletViewModel = viewModel(),
    pendingNotificationRecordId: String? = null,
    onNotificationHandled: () -> Unit = {},
    pendingFocusLaunch: PendingFocusLaunch? = null,
    onFocusLaunchHandled: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    // v1.0.12 (P1-NAV): KHÔNG dùng `WindowInsets.isImeVisible`. Đo trên Android 9 (WallyQA): isImeVisible = true ngay cả ở tab Tổng quan trong khi
    // ime inset = 0 và không có bàn phím (mInputShown=false): khi hệ thống không chuyển inset cho Compose (app chưa edge-to-edge trước Android 15)
    // cờ đó đứng yên ở giá trị khởi tạo `true`. Chỉ coi bàn phím là đang hiện khi có phần inset THẬT ở đáy.
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onAppPaused()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = state.showProfileSettings) {
        viewModel.closeProfileSettings()
    }

    BackHandler(enabled = state.showScheduleWorkspace) {
        viewModel.closeScheduleWorkspace()
    }

    BackHandler(enabled = state.isShowingDailyReview) {
        viewModel.closeDailyReview()
    }

    var pendingJsonToWrite by remember { mutableStateOf<String?>(null) }
    var pendingBudgetDelete by remember { mutableStateOf<String?>(null) }
    var showDataDialog by remember { mutableStateOf(false) }
    // Tin nhắn vừa gửi ở Wally (chỉ để hiển thị bong bóng): sống qua đổi tab và xoay màn hình.
    var lastChatUserMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var preservedBudgetDraft by remember { mutableStateOf<BudgetEditorDraft?>(null) }
    LaunchedEffect(state.showBudgetDialog) {
        if (!state.showBudgetDialog) {
            preservedBudgetDraft = null
        }
    }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val content = pendingJsonToWrite
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                viewModel.closeExportJsonDialog()
                viewModel.showToast("Đã lưu tệp JSON thành công.")
            } catch (_: Exception) {
                viewModel.showToast("Không thể ghi tệp JSON do lỗi lưu trữ thiết bị.")
            }
        }
        pendingJsonToWrite = null
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }
                if (content != null) {
                    viewModel.updateImportJsonText(content)
                } else {
                    viewModel.showToast("Không đọc được nội dung tệp JSON.")
                }
            } catch (_: Exception) {
                viewModel.showToast("Không thể mở tệp JSON từ bộ nhớ thiết bị.")
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onNotificationPermissionGranted()
        }
    }

    LaunchedEffect(state.requestNotificationPermissionNonce) {
        if (state.requestNotificationPermissionNonce > 0 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !CompletionNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingNotificationRecordId) {
        val targetId = pendingNotificationRecordId ?: return@LaunchedEffect
        viewModel.openActivityConfirmationFromNotification(targetId)
        onNotificationHandled()
    }

    LaunchedEffect(pendingFocusLaunch) {
        val launch = pendingFocusLaunch ?: return@LaunchedEffect
        viewModel.openFocusFromNotification(launch.title, launch.categoryKey, launch.tagId)
        onFocusLaunchHandled()
    }

    LaunchedEffect(state.currentTab, state.isShowingDailyReview, state.selectedReviewDateLocal) {
        scrollState.scrollTo(0)
    }

    // v1.1.0: tóm tắt mục tiêu (tiến độ do ứng dụng tính) và gợi ý Tag gần đây/thường dùng; tính lại khi sổ cái/Tag/mục tiêu đổi.
    val goalSummaries = remember(state.goals, state.records, state.tags, state.todayDateLocal) {
        val zone = ZoneId.systemDefault()
        GoalRules.summaries(
            goals = state.goals, records = state.records,
            tagNameOf = { id -> state.tags.firstOrNull { it.id == id }?.name },
            today = LocalDate.now(zone), nowEpochMs = System.currentTimeMillis(), zone = zone
        )
    }
    val activeTags = remember(state.tags) { state.tags.filter { !it.isArchived } }
    val tagSuggestions = remember(state.tags, state.records) {
        TagRules.suggestions(state.tags, state.records, System.currentTimeMillis(), ZoneId.systemDefault())
    }

    var journalCalendarMode by rememberSaveable { mutableStateOf(true) }
    val inboxTasks = remember(state.tasks) {
        state.tasks.filter { it.status == TaskStatus.INBOX && !it.isDone }
    }
    val activeTask = remember(state.activeTimer, state.tasks) {
        state.activeTimer?.taskId?.let { tid -> state.tasks.firstOrNull { it.id == tid } }
    }
    val isDark = when (state.customization.themeMode) {
        ThemeModePreference.DARK -> true
        ThemeModePreference.LIGHT -> false
        ThemeModePreference.SYSTEM -> isSystemInDarkTheme()
    }

    TimeWalletTheme(darkTheme = isDark) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletBg)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (!state.showScheduleWorkspace) {
        Scaffold(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize(),
            containerColor = WalletBg,
            topBar = {
                TimeWalletTopBar(
                    isTimerRunning = state.activeTimer != null,
                    timerSeconds = state.timerElapsedSeconds,
                    onTimerBadgeClick = { viewModel.selectTab(AppTab.FOCUS) },
                    onDataClick = { showDataDialog = true },
                    onAiSettingsClick = { viewModel.toggleAiSettingsDialog(true) },
                    onInfoClick = { viewModel.toggleProductInfoDialog(true) },
                    onOpenProfileSettings = viewModel::openProfileSettings,
                    onOpenWallyOverlay = if (state.customization.enableAiAssistant) {
                        { viewModel.openWallyOverlay() }
                    } else null,
                    onOpenQuickCapture = { viewModel.openQuickCaptureDialog() },
                    wallyHasDrafts = state.proposals.isNotEmpty()
                )
            },
            bottomBar = {
                if (shouldShowBottomBar(state.currentTab, imeBottomPx)) {
                    TimeWalletBottomBar(
                        currentTab = state.currentTab,
                        isTimerRunning = state.activeTimer != null,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            val lockOuterScroll = (state.currentTab == AppTab.SCHEDULE && journalCalendarMode && !state.isShowingDailyReview)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .then(if (lockOuterScroll) Modifier else Modifier.verticalScroll(scrollState))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                when (state.currentTab) {
                    AppTab.SCHEDULE -> {
                        if (state.isShowingDailyReview) {
                            DailyReviewScreen(
                                todayDateLocal = state.todayDateLocal,
                                snapshot = state.currentDailyReviewSnapshot,
                                onBackToDashboard = viewModel::closeDailyReview,
                                onSelectPreviousDay = viewModel::selectPreviousReviewDay,
                                onSelectNextDay = viewModel::selectNextReviewDay,
                                onSelectToday = viewModel::selectTodayReviewDay,
                                onOpenBudgetEditor = viewModel::openBudgetEditor,
                                onSaveActivityNote = viewModel::saveActivityReviewNote,
                                onSaveFullDailyReview = viewModel::saveFullDailyReview,
                                onExportJsonClick = viewModel::prepareExportJson
                            )
                        } else {
                            HistoryScreen(
                                todayDateLocal = state.todayDateLocal,
                                records = state.records,
                                selectedFilter = state.historyFilter,
                                onSelectFilter = viewModel::setHistoryFilter,
                                onConfirmPlannedOrActiveRecord = viewModel::confirmPlannedOrActiveRecord,
                                onEditRecord = viewModel::openEditRecord,
                                onDeleteRecord = viewModel::deleteRecord,
                                onAddManual = { viewModel.openManualEntry() },
                                onResizePlannedRecord = viewModel::resizePlannedRecord,
                                activeTimer = state.activeTimer,
                                selectedScheduleDate = state.scheduleFocusDateLocal ?: state.todayDateLocal,
                                scheduleViewMode = state.scheduleViewMode,
                                onSelectScheduleDate = viewModel::selectScheduleFocusDate,
                                onSelectScheduleViewMode = viewModel::selectScheduleViewMode,
                                onAddForSlot = { dateStr, startHm, endHm, savePlanned ->
                                    viewModel.openManualFormForSlot(dateStr, startHm, endHm, savePlanned)
                                },
                                onAddForSlots = viewModel::openManualFormForSlots,
                                onMoveOrResizeBlock = viewModel::moveOrResizeScheduleRecord,
                                onNavigateToTimer = { viewModel.selectTab(AppTab.FOCUS) },
                                showCalendarMode = journalCalendarMode,
                                onShowCalendarModeChange = { journalCalendarMode = it },
                                todayStats = state.todayStats,
                                inboxTasks = inboxTasks,
                                onScheduleTaskOnDate = { task, date -> viewModel.scheduleTaskOnCalendar(task, date) },
                                onOpenDailyReview = { viewModel.openDailyReview(state.todayDateLocal) },
                                onOpenTasksTab = { viewModel.selectTab(AppTab.TASKS) },
                                onRequestWallyReplan = viewModel::requestWallyReplanInbox
                            )
                        }
                    }

                    AppTab.FOCUS -> TimerScreen(
                        activeTimer = state.activeTimer,
                        elapsedSeconds = state.timerElapsedSeconds,
                        clockSkewDetected = state.timerClockSkewDetected,
                        inputTitle = state.timerInputTitle,
                        inputCategoryKey = state.timerInputCategoryKey,
                        diceCognitiveState = state.diceCognitiveState,
                        diceFaceProbabilities = state.diceFaceProbabilities,
                        isRollingDice = state.isRollingDice,
                        rollingPreviewFaceNumber = state.rollingPreviewFaceNumber,
                        lastDiceRoll = state.lastDiceRoll,
                        onTitleChange = viewModel::onTimerTitleChange,
                        onCategoryChange = viewModel::onTimerCategoryChange,
                        onRollDice = viewModel::rollTimeDice,
                        onApplyDiceAndStartTimer = viewModel::applyDiceRollAndStartTimer,
                        onStartTimer = viewModel::startTimer,
                        onStopAndSaveTimer = viewModel::stopAndSaveTimer,
                        onCancelTimer = viewModel::cancelRunningTimer,
                        tagSuggestions = tagSuggestions,
                        selectedTagId = state.timerInputTagId,
                        onSelectTag = viewModel::selectTimerTag,
                        onCleanDraft = viewModel::cleanTimerDraft,
                        focusPinEnabled = state.focusPinEnabled,
                        focusPinPreview = state.focusPinPreview,
                        onToggleFocusPin = viewModel::toggleFocusPin,
                        onApplyFocusSuggestion = viewModel::openFocusFromNotification,
                        pomodoroMode = state.pomodoroMode,
                        pomodoroTargetMinutes = state.pomodoroTargetMinutes,
                        enablePomodoro = state.customization.enablePomodoro,
                        enableDice = state.customization.enableDice,
                        onSetPomodoroMode = viewModel::setPomodoroMode,
                        activeTask = activeTask,
                        onToggleSubtaskDone = viewModel::toggleSubtaskDone,
                        focusBreakNudgeVisible = state.focusBreakNudgeVisible
                    )

                    AppTab.TASKS -> TasksHabitsScreen(
                        todayDateLocal = state.todayDateLocal,
                        tasks = state.tasks,
                        taskFilter = state.taskFilter,
                        onSelectTaskFilter = viewModel::selectTaskFilter,
                        habits = state.habits,
                        habitLogs = state.habitLogs,
                        records = state.records,
                        tags = activeTags,
                        unifiedGoals = state.unifiedGoals,
                        customization = state.customization,
                        onCreateTask = { title, catKey, prio, estMins, dueDate, tagId, quad, subs ->
                            viewModel.createTask(
                                title = title,
                                categoryKey = catKey,
                                priority = prio,
                                estimatedMinutes = estMins,
                                dueDateLocal = dueDate,
                                tagId = tagId,
                                eisenhowerQuad = quad,
                                subtasksRaw = subs
                            )
                        },
                        onToggleTaskDone = viewModel::toggleTaskDone,
                        onUpdateTaskStatus = viewModel::updateTaskStatus,
                        onSetTaskEisenhower = viewModel::setTaskEisenhowerQuad,
                        onAddSubtask = viewModel::addSubtask,
                        onToggleSubtaskDone = viewModel::toggleSubtaskDone,
                        onDeleteTask = viewModel::deleteTask,
                        onStartFocusOnTask = viewModel::startFocusOnTask,
                        onScheduleTaskOnCalendar = { t -> viewModel.scheduleTaskOnCalendar(t, state.scheduleFocusDateLocal ?: state.todayDateLocal) },
                        onBreakDownTaskWithWally = viewModel::breakDownTaskWithWally,
                        onRequestWallyReplanInbox = viewModel::requestWallyReplanInbox,
                        onCreateHabit = viewModel::createHabit,
                        onToggleHabitForToday = { h -> viewModel.toggleHabitForDate(h, state.todayDateLocal) },
                        onOpenGoalEditor = viewModel::openGoalEditor,
                        onPauseGoal = viewModel::pauseGoal,
                        onResumeGoal = viewModel::resumeGoal,
                        onEndGoal = viewModel::endGoal,
                        onOpenCustomization = viewModel::openProfileSettings
                    )

                    AppTab.MORE -> {
                        val hasSavedTodayReview = state.dailyReviews.any { it.dateLocal == state.todayDateLocal }
                        InsightsScreen(
                            weeklyInsights = state.weeklyInsights,
                            weeklyEfficiency = state.weeklyEfficiency,
                            goalsCount = goalSummaries.size,
                            tagsCount = activeTags.size,
                            coach = state.coach,
                            aiEnabled = state.aiPreferences.optInEnabled && state.aiPreferences.apiKey.isNotBlank(),
                            hasSavedTodayReview = hasSavedTodayReview,
                            onOpenBudgetEditor = viewModel::openBudgetEditor,
                            onDeleteBudget = { pendingBudgetDelete = it },
                            onReorderBudgetPriority = viewModel::reorderBudgetPriority,
                            onOpenGoals = viewModel::openGoals,
                            onOpenTags = viewModel::openTagManager,
                            onOpenDailyReview = { viewModel.openDailyReview(state.todayDateLocal) },
                            onOpenTimer = { viewModel.selectTab(AppTab.FOCUS) },
                            onOpenDataDialog = { showDataDialog = true },
                            onAskCoach = viewModel::requestCoach,
                            onDismissCoach = viewModel::dismissCoach,
                            onConfirmSuggestion = viewModel::confirmCoachSuggestion,
                            onDismissSuggestion = viewModel::dismissCoachSuggestion,
                            onTriggerAction = viewModel::triggerCoachAction,
                            focusPinEnabled = state.focusPinEnabled,
                            focusPinPreview = state.focusPinPreview,
                            onToggleFocusPin = viewModel::toggleFocusPin,
                            peakFocusWindow = state.peakFocusWindow,
                            planAccuracySummary = state.planAccuracySummary,
                            onOpenMarkdownExport = viewModel::prepareExportDailyMarkdown,
                            onOpenWallyChat = { viewModel.openWallyOverlay() },
                            onOpenProfileSettings = viewModel::openProfileSettings
                        )
                    }
                }
                if (!lockOuterScroll) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                ScheduleCalendarView(
                    todayDateLocal = state.todayDateLocal,
                    records = state.records,
                    activeTimer = state.activeTimer,
                    selectedDateLocal = state.scheduleFocusDateLocal ?: state.todayDateLocal,
                    viewMode = state.scheduleViewMode,
                    onSelectDate = viewModel::selectScheduleFocusDate,
                    onSelectViewMode = viewModel::selectScheduleViewMode,
                    onAddForSlot = { dateStr, startHm, endHm, savePlanned ->
                        viewModel.openManualFormForSlot(dateStr, startHm, endHm, savePlanned)
                    },
                    onAddForSlots = viewModel::openManualFormForSlots,
                    onEditRecord = viewModel::openEditRecord,
                    onConfirmPlannedOrActiveRecord = viewModel::confirmPlannedOrActiveRecord,
                    onMoveOrResizeBlock = viewModel::moveOrResizeScheduleRecord,
                    onNavigateToTimer = {
                        viewModel.closeScheduleWorkspace()
                        viewModel.selectTab(AppTab.FOCUS)
                    },
                    showBackButton = true,
                    onBackClick = viewModel::closeScheduleWorkspace,
                    embeddedInScrollContainer = false
                )
            }
        }

        // Toast Banner at Bottom
        AnimatedVisibility(
            visible = state.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            state.toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .widthIn(max = 460.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF233E33))
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = "Đóng thông báo") { viewModel.dismissToast() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    state.confirmingRecord?.let { record ->
        ActivityConfirmationDialog(
            record = record,
            onDismiss = viewModel::closeConfirmationDialog,
            onStartDoing = viewModel::confirmStartDoing,
            onConfirmCompleted = viewModel::confirmCompleted,
            onAdjust = viewModel::confirmAdjusted,
            onInterrupt = viewModel::confirmInterrupted,
            onContinue = viewModel::confirmContinued,
            onCancelActivity = viewModel::confirmCancelled
        )
    }

    state.pendingOverlapConflict?.let { conflict ->
        OverlapResolutionDialog(
            conflict = conflict,
            onDismissDiscard = viewModel::dismissOverlapConflict,
            onResolve = viewModel::resolveOverlapConflict
        )
    }

    // Dialogs
    state.editingRecord?.let { record ->
        EditRecordDialog(
            record = record,
            onDismiss = viewModel::closeEditRecord,
            onSave = { title, catKey, dateLocal, durationMinsText, startHm, endHm, allowPlanned, markedWaste ->
                viewModel.saveEditedRecord(
                    existing = record,
                    newTitle = title,
                    newCategoryKey = catKey,
                    newDateLocal = dateLocal,
                    newDurationMinutesText = durationMinsText,
                    newStartHm = startHm,
                    newEndHm = endHm,
                    allowFutureAsPlanned = allowPlanned,
                    newMarkedWaste = markedWaste
                )
            },
            onDelete = { viewModel.deleteRecord(record.id) },
            onOpenConfirmation = { viewModel.confirmPlannedOrActiveRecord(record) }
        )
    }

    state.exportedJsonContent?.let { json ->
        ExportJsonDialog(
            jsonContent = json,
            todayDateLocal = state.todayDateLocal,
            onDismiss = viewModel::closeExportJsonDialog,
            onSaveToFileClick = { fileName, content ->
                pendingJsonToWrite = content
                createDocumentLauncher.launch(fileName)
            },
            onCopiedToast = {
                viewModel.showToast("Đã sao chép JSON vào bộ nhớ tạm.")
            }
        )
    }

    state.importBackup?.let { importState ->
        ImportJsonDialog(
            state = importState,
            onJsonChange = viewModel::updateImportJsonText,
            onPickFileClick = {
                openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onConfirm = viewModel::confirmImportJson,
            onDismiss = viewModel::closeImportJsonDialog
        )
    }

    if (state.showAiSettingsDialog) {
        AiSettingsDialog(
            initialPreferences = state.aiPreferences,
            onDismiss = { viewModel.toggleAiSettingsDialog(false) },
            onSave = viewModel::saveAiPreferences,
            connectionTestMessage = state.aiConnectionTestMessage,
            connectionTesting = state.aiConnectionTesting,
            diagnosticsText = state.aiDiagnosticsText,
            onTestConnection = viewModel::testAiConnection
        )
    }

    state.manualEntry?.let { form ->
        ManualEntrySheet(
            state = form,
            suggestions = tagSuggestions,
            allActiveTags = activeTags,
            onChange = viewModel::updateManualEntry,
            onSelectTag = viewModel::selectManualTag,
            onCreateTag = viewModel::createTagFromManualEntry,
            onSave = viewModel::saveManualEntry,
            onDismiss = viewModel::closeManualEntry,
            onClean = viewModel::cleanManualEntry
        )
    }

    state.tagManager?.let { tm ->
        TagManagerDialog(
            state = tm,
            tags = state.tags,
            onChange = viewModel::updateTagManager,
            onCreate = viewModel::createTagFromManager,
            onRename = viewModel::renameTag,
            onSetCategory = viewModel::setTagCategory,
            onArchive = viewModel::archiveTag,
            onRestore = viewModel::restoreTag,
            onDismiss = viewModel::closeTagManager
        )
    }

    if (state.showGoals && state.goalEditor == null) {
        GoalsDialog(
            summaries = goalSummaries,
            onCreate = { viewModel.openGoalEditor(null) },
            onEdit = { viewModel.openGoalEditor(it) },
            onPause = viewModel::pauseGoal,
            onResume = viewModel::resumeGoal,
            onEnd = viewModel::endGoal,
            onDismiss = viewModel::closeGoals
        )
    }
    state.goalEditor?.let { ed ->
        GoalEditorDialog(
            state = ed,
            activeTags = activeTags,
            onChange = viewModel::updateGoalEditor,
            onSave = viewModel::saveGoalEditor,
            onDismiss = viewModel::closeGoalEditor
        )
    }

    if (showDataDialog) {
        DataManagementDialog(
            onDismiss = { showDataDialog = false },
            onExportJson = { viewModel.prepareExportJson() },
            onImportJson = { viewModel.openImportJsonDialog() },
            onLoadSampleData = viewModel::loadSampleData,
            onClearAllData = viewModel::clearAllData
        )
    }

    if (state.showProductInfoDialog) {
        ProductInfoDialog(
            onDismiss = { viewModel.toggleProductInfoDialog(false) },
            crashReport = state.lastCrashReport,
            onClearCrashReport = viewModel::clearCrashReport
        )
    }

    if (state.showProfileSettings) {
        ProfileSettingsScreen(
            aiEnabled = state.aiPreferences.optInEnabled && state.aiPreferences.apiKey.isNotBlank(),
            hasCrashReport = !state.lastCrashReport.isNullOrBlank(),
            tagsCount = activeTags.size,
            goalsCount = goalSummaries.size,
            onBack = viewModel::closeProfileSettings,
            onOpenIntro = viewModel::openAppIntro,
            onOpenInfo = { viewModel.toggleProductInfoDialog(true) },
            onOpenTags = viewModel::openTagManager,
            onOpenGoals = viewModel::openGoals,
            onOpenData = { showDataDialog = true },
            onOpenAiSettings = { viewModel.toggleAiSettingsDialog(true) },
            focusPinEnabled = state.focusPinEnabled,
            focusPinPreview = state.focusPinPreview,
            onToggleFocusPin = viewModel::toggleFocusPin,
            customization = state.customization,
            aiReadPermissions = state.aiReadPermissions,
            localRules = state.localRules,
            onUpdateCustomization = viewModel::updateCustomizationSettings,
            onUpdateAiPermissions = viewModel::updateAiReadPermissions,
            onAddLocalRule = { kw, catKey, markWaste -> viewModel.addLocalCategoryRule(kw, catKey, markWaste = markWaste) },
            onDeleteLocalRule = viewModel::deleteLocalCategoryRule
        )
    }

    if (state.showAppIntro) {
        AppIntroDialog(onDismiss = viewModel::dismissAppIntro)
    }

    // v1.0.10: chỉ một dialog ngân sách hiển thị tại một thời điểm; hủy xác nhận quay lại đúng trạng thái sửa Budget.
    val activeDeleteKey = pendingBudgetDelete
    if (activeDeleteKey != null) {
        BudgetDeleteConfirmDialog(
            categoryKey = activeDeleteKey,
            onConfirm = {
                viewModel.deleteCategoryBudget(activeDeleteKey)
                pendingBudgetDelete = null
                preservedBudgetDraft = null
            },
            onDismiss = {
                pendingBudgetDelete = null
            }
        )
    } else if (state.showBudgetDialog) {
        TimeBudgetEditorDialog(
            initialCategory = state.editingBudgetCategory,
            existingBudgets = state.budgets,
            preservedDraft = preservedBudgetDraft,
            onDismiss = {
                preservedBudgetDraft = null
                viewModel.closeBudgetEditor()
            },
            onSave = { categoryKey, period, hoursText, minutesText, isPriority ->
                preservedBudgetDraft = null
                viewModel.saveCategoryBudget(categoryKey, period, hoursText, minutesText, isPriority)
            },
            onDelete = { categoryKey ->
                pendingBudgetDelete = categoryKey
            },
            onDeleteWithDraft = { draft ->
                preservedBudgetDraft = draft
                pendingBudgetDelete = draft.categoryKey
            }
        )
    }

    if (state.showWallyOverlay) {
        WallyAiOverlay(
            aiReadPermissions = state.aiReadPermissions,
            overlayHint = state.wallyOverlayHint,
            inboxTaskCount = inboxTasks.size,
            onRequestReplanInbox = viewModel::requestWallyReplanInbox,
            onDismiss = viewModel::closeWallyOverlay,
            chatInputText = state.chatInputText,
            isAnalyzing = state.isAnalyzingChat,
            chatFault = state.chatFault,
            lastAnalysisSource = state.lastAnalysisSource,
            onCancelAnalysis = viewModel::cancelAnalysis,
            onDismissError = viewModel::dismissChatError,
            onManualEntry = { viewModel.openManualEntry() },
            chatAnswer = state.chatAnswer,
            onDismissAnswer = viewModel::dismissChatAnswer,
            proposals = state.proposals,
            expandedProposalId = state.expandedProposalId,
            aiPreferences = state.aiPreferences,
            onChatInputChange = viewModel::onChatInputChange,
            onApplyExample = viewModel::applyChatExample,
            onAnalyzeClick = viewModel::analyzeChatInput,
            onOpenAiSettings = { viewModel.toggleAiSettingsDialog(true) },
            onToggleExpandProposal = viewModel::toggleExpandProposal,
            onUpdateProposal = viewModel::updateProposalDraft,
            onDiscardProposal = viewModel::discardProposal,
            onClearAllProposals = viewModel::clearAllProposals,
            onConfirmSingleProposal = viewModel::confirmSingleProposal,
            onConfirmAllProposals = viewModel::confirmAllProposals,
            lastUserMessage = lastChatUserMessage,
            onLastUserMessageChange = { lastChatUserMessage = it },
            chatHistory = state.chatHistory,
            onClearChatHistory = viewModel::clearChatHistory,
            onCleanChatInput = viewModel::cleanChatInput,
            onCopyPromptToast = { viewModel.showToast("Đã sao chép prompt.") },
            onEditAndResubmitPrompt = viewModel::editAndResubmitChatPrompt,
            onOpenAppIntro = viewModel::openAppIntro
        )
    }

    if (state.showQuickCaptureDialog) {
        UniversalQuickCaptureDialog(
            tags = activeTags,
            localRules = state.localRules,
            onSubmit = viewModel::submitQuickCapture,
            onDismiss = viewModel::closeQuickCaptureDialog
        )
    }

    state.pendingTaskLogPrompt?.let { prompt ->
        TaskCompletionLogPromptDialog(
            prompt = prompt,
            onLogToLedger = { viewModel.openManualLogFromCompletedTask(prompt) },
            onSkip = viewModel::dismissTaskLogPrompt
        )
    }

    state.exportedMarkdownContent?.let { md ->
        MarkdownExportDialog(
            markdownText = md,
            onDismiss = viewModel::closeExportMarkdownDialog,
            onCopied = { viewModel.showToast("Đã sao chép nhật ký Markdown.") }
        )
    }
    }
}
