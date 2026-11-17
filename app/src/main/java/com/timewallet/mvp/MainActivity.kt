package com.timewallet.mvp

import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timewallet.mvp.notifications.CompletionNotificationHelper
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.TimeWalletViewModel
import com.timewallet.mvp.ui.screens.ActivityConfirmationDialog
import com.timewallet.mvp.ui.screens.AiSettingsDialog
import com.timewallet.mvp.ui.screens.OverlapResolutionDialog
import com.timewallet.mvp.ui.screens.BudgetDeleteConfirmDialog
import com.timewallet.mvp.ui.screens.BudgetEditorDraft
import com.timewallet.mvp.ui.screens.ChatScreen
import com.timewallet.mvp.ui.screens.DailyReviewScreen
import com.timewallet.mvp.ui.screens.DataManagementDialog
import com.timewallet.mvp.ui.screens.EditRecordDialog
import com.timewallet.mvp.ui.screens.ExportJsonDialog
import com.timewallet.mvp.ui.screens.HistoryScreen
import com.timewallet.mvp.ui.screens.ImportJsonDialog
import com.timewallet.mvp.ui.screens.InsightsScreen
import com.timewallet.mvp.ui.screens.ManualEntrySheet
import com.timewallet.mvp.ui.screens.OverviewScreen
import com.timewallet.mvp.ui.screens.GoalEditorDialog
import com.timewallet.mvp.ui.screens.GoalsDialog
import com.timewallet.mvp.ui.screens.PersonalHubCard
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

class MainActivity : ComponentActivity() {
    private val pendingNotificationRecordId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationRecordId.value = intent?.getStringExtra(CompletionNotificationHelper.EXTRA_CONFIRM_RECORD_ID)
        setContent {
            TimeWalletTheme {
                TimeWalletAppRoot(
                    pendingNotificationRecordId = pendingNotificationRecordId.value,
                    onNotificationHandled = { pendingNotificationRecordId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationRecordId.value = intent.getStringExtra(CompletionNotificationHelper.EXTRA_CONFIRM_RECORD_ID)
    }
}

/**
 * v1.0.12 (P1-NAV): thanh tab chỉ ẩn khi bàn phím THẬT (ime inset ở đáy > 0) đang hiện ở Wally. Mọi trường hợp khác — kể cả khi hệ thống
 * không chuyển inset cho Compose (Android trước 15, LDPlayer Android 9) — thanh tab luôn hiện để người dùng không bị kẹt trong Wally.
 */
internal fun shouldShowBottomBar(currentTab: AppTab, imeBottomPx: Int): Boolean =
    !(imeBottomPx > 0 && currentTab == AppTab.CHAT)

/** Nút Lên đầu trang chỉ hiện khi cuộn vượt quá 400 px. */
internal fun shouldShowBackToTop(scrollPx: Int): Boolean = scrollPx > 400

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeWalletAppRoot(
    viewModel: TimeWalletViewModel = viewModel(),
    pendingNotificationRecordId: String? = null,
    onNotificationHandled: () -> Unit = {}
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
    ) { }

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

    val coroutineScope = rememberCoroutineScope()
    var speedDialExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentTab, state.isShowingDailyReview) {
        speedDialExpanded = false
    }

    BackHandler(enabled = speedDialExpanded) {
        speedDialExpanded = false
    }

    // Nhóm nút nổi góc dưới phải: ẩn ở Wally (đã có ô nhập ghim đáy) và màn Nhìn lại toàn trang; nội dung cuộn có khoảng đệm đáy tránh bị che.
    val showFloatingControls = state.currentTab != AppTab.CHAT && !state.isShowingDailyReview
    val showBackTop = showFloatingControls && shouldShowBackToTop(scrollState.value)
    // Ở Tổng quan lúc đầu trang đã có ô "Hỏi Wally" lớn, nên chỉ hiện nút nổi "Hỏi nhanh Wally" khi cuộn qua hoặc ở các tab khác để không che chú giải Donut.
    val showFloatingChatLauncher = showFloatingControls && (state.currentTab != AppTab.OVERVIEW || scrollState.value > 250)

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
                    onTimerBadgeClick = { viewModel.selectTab(AppTab.TIMER) },
                    onDataClick = { showDataDialog = true },
                    onAiSettingsClick = { viewModel.toggleAiSettingsDialog(true) },
                    onInfoClick = { viewModel.toggleProductInfoDialog(true) }
                )
            },
            floatingActionButton = {
                if (showFloatingControls) {
                    FloatingActionGroup(
                        showBackTop = showBackTop,
                        showChatLauncher = showFloatingChatLauncher,
                        speedDialExpanded = speedDialExpanded,
                        onBackToTop = {
                            speedDialExpanded = false
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        },
                        onOpenWallyChat = {
                            speedDialExpanded = false
                            viewModel.selectTab(AppTab.CHAT)
                        },
                        onToggleSpeedDial = { speedDialExpanded = !speedDialExpanded },
                        onManualEntry = {
                            speedDialExpanded = false
                            viewModel.openManualEntry()
                        },
                        onOpenTimer = {
                            speedDialExpanded = false
                            viewModel.selectTab(AppTab.TIMER)
                        },
                        onOpenDailyReview = {
                            speedDialExpanded = false
                            viewModel.openDailyReview(state.todayDateLocal)
                        }
                    )
                }
            },
            bottomBar = {
                // Khi bàn phím THẬT đang hiện ở Wally, ẩn thanh tab để nhường chỗ cho hội thoại; mọi trường hợp khác thanh tab luôn hiện.
                if (shouldShowBottomBar(state.currentTab, imeBottomPx)) {
                    TimeWalletBottomBar(
                        currentTab = state.currentTab,
                        isTimerRunning = state.activeTimer != null,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            // v1.0.11: Wally là màn hình hội thoại với ô nhập ghim đáy nên không nằm trong khối cuộn chung.
            val isChatTab = state.currentTab == AppTab.CHAT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .then(if (isChatTab) Modifier else Modifier.verticalScroll(scrollState))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                when (state.currentTab) {
                    AppTab.OVERVIEW -> {
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
                            val hasSavedTodayReview = state.dailyReviews.any { it.dateLocal == state.todayDateLocal }
                            OverviewScreen(
                                todayDateLocal = state.todayDateLocal,
                                todayStats = state.todayStats,
                                recentRecords = state.records,
                                priorityBudgets = state.dashboardPriorityBudgets,
                                hasSavedTodayReview = hasSavedTodayReview,
                                onOpenDailyReview = { viewModel.openDailyReview(state.todayDateLocal) },
                                onOpenBudgetEditor = viewModel::openBudgetEditor,
                                onAddManual = { viewModel.openManualEntry() },
                                onConfirmOrResolveRecord = viewModel::confirmPlannedOrActiveRecord,
                                pendingManualDraft = state.pendingManualDraft,
                                onResumeManualDraft = { viewModel.openManualEntry() },
                                onCleanManualDraft = viewModel::discardPendingManualDraft,
                                onNavigateToTab = { viewModel.selectTab(it) }
                            )
                            PersonalHubCard(
                                goalSummaries = goalSummaries,
                                coach = state.coach,
                                aiEnabled = state.aiPreferences.optInEnabled && state.aiPreferences.apiKey.isNotBlank(),
                                onOpenGoals = viewModel::openGoals,
                                onOpenTags = viewModel::openTagManager,
                                onAskCoach = viewModel::requestCoach,
                                onDismissCoach = viewModel::dismissCoach,
                                onConfirmSuggestion = viewModel::confirmCoachSuggestion,
                                onDismissSuggestion = viewModel::dismissCoachSuggestion,
                                onTriggerAction = viewModel::triggerCoachAction
                            )
                        }
                    }

                    AppTab.TIMER -> TimerScreen(
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
                        onCleanDraft = viewModel::cleanTimerDraft
                    )

                    AppTab.CHAT -> ChatScreen(
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
                        onCleanChatInput = viewModel::cleanChatInput
                    )

                    AppTab.HISTORY -> HistoryScreen(
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
                        onMoveOrResizeBlock = viewModel::moveOrResizeScheduleRecord,
                        onNavigateToTimer = { viewModel.selectTab(AppTab.TIMER) }
                    )

                    AppTab.INSIGHTS -> InsightsScreen(
                        weeklyInsights = state.weeklyInsights,
                        onOpenBudgetEditor = viewModel::openBudgetEditor,
                        onDeleteBudget = { pendingBudgetDelete = it },
                        onReorderBudgetPriority = viewModel::reorderBudgetPriority
                    )
                }
                if (!isChatTab) Spacer(modifier = Modifier.height(if (showFloatingControls) 176.dp else 16.dp))
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
                    onEditRecord = viewModel::openEditRecord,
                    onConfirmPlannedOrActiveRecord = viewModel::confirmPlannedOrActiveRecord,
                    onMoveOrResizeBlock = viewModel::moveOrResizeScheduleRecord,
                    onNavigateToTimer = {
                        viewModel.closeScheduleWorkspace()
                        viewModel.selectTab(AppTab.TIMER)
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
            onSave = { title, catKey, dateLocal, durationMinsText, startHm, endHm, allowPlanned ->
                viewModel.saveEditedRecord(
                    existing = record,
                    newTitle = title,
                    newCategoryKey = catKey,
                    newDateLocal = dateLocal,
                    newDurationMinutesText = durationMinsText,
                    newStartHm = startHm,
                    newEndHm = endHm,
                    allowFutureAsPlanned = allowPlanned
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
}

/**
 * Nhóm nút nổi cố định ở góc dưới phải trên phần inset đáy:
 * Back-to-Top (chỉ hiện khi cuộn > 400 px), nút mở nhanh Wally Chat, và FAB tròn mở Speed Dial 3 hành động.
 */
@Composable
private fun FloatingActionGroup(
    showBackTop: Boolean,
    showChatLauncher: Boolean,
    speedDialExpanded: Boolean,
    onBackToTop: () -> Unit,
    onOpenWallyChat: () -> Unit,
    onToggleSpeedDial: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenDailyReview: () -> Unit
) {
    val plusRotation by animateFloatAsState(
        targetValue = if (speedDialExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "fabPlusRotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = showBackTop && !speedDialExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SmallFloatingActionButton(
                onClick = onBackToTop,
                shape = CircleShape,
                containerColor = Color.White,
                contentColor = WalletInk,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                modifier = Modifier.semantics { contentDescription = "Lên đầu trang" }
            ) {
                GlyphIcon(WalletGlyph.ARROW_UP, color = WalletInk, size = 18.dp)
            }
        }

        AnimatedVisibility(
            visible = showChatLauncher && !speedDialExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SmallFloatingActionButton(
                onClick = onOpenWallyChat,
                shape = CircleShape,
                containerColor = WalletMintTint,
                contentColor = WalletMint,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                modifier = Modifier.semantics { contentDescription = "Hỏi nhanh Wally" }
            ) {
                GlyphIcon(WalletGlyph.WALLY_BOT, color = WalletMint, size = 20.dp, filled = true)
            }
        }

        AnimatedVisibility(
            visible = speedDialExpanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedDialActionItem(
                    label = "Nhìn lại ngày hôm nay",
                    glyph = WalletGlyph.BOOK,
                    onClick = onOpenDailyReview
                )
                SpeedDialActionItem(
                    label = "Bắt đầu đồng hồ",
                    glyph = WalletGlyph.CLOCK,
                    onClick = onOpenTimer
                )
                SpeedDialActionItem(
                    label = "Ghi giờ thủ công",
                    glyph = WalletGlyph.EDIT,
                    iconModifier = Modifier.semantics { contentDescription = "Thêm giờ thủ công" },
                    onClick = onManualEntry
                )
            }
        }

        FloatingActionButton(
            onClick = onToggleSpeedDial,
            shape = CircleShape,
            containerColor = WalletMint,
            contentColor = Color.White,
            modifier = Modifier.semantics {
                contentDescription = if (speedDialExpanded) "Đóng bảng thao tác nhanh" else "Mở thao tác nhanh"
            }
        ) {
            GlyphIcon(
                glyph = WalletGlyph.PLUS,
                color = Color.White,
                size = 24.dp,
                modifier = Modifier.rotate(plusRotation)
            )
        }
    }
}

@Composable
private fun SpeedDialActionItem(
    label: String,
    glyph: WalletGlyph,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier.semantics { contentDescription = label }
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, WalletStroke),
            modifier = Modifier.walletClickable(onClickLabel = label, onClick = onClick)
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = WalletInk,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = Color.White,
            contentColor = WalletMint,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
            modifier = iconModifier
        ) {
            GlyphIcon(glyph = glyph, color = WalletMint, size = 18.dp)
        }
    }
}
