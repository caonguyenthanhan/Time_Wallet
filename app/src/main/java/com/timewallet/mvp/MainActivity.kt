package com.timewallet.mvp

import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import android.net.Uri
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.TimeWalletViewModel
import com.timewallet.mvp.ui.screens.AiSettingsDialog
import com.timewallet.mvp.ui.screens.BudgetDeleteConfirmDialog
import com.timewallet.mvp.ui.screens.BudgetEditorDraft
import com.timewallet.mvp.ui.screens.ChatScreen
import com.timewallet.mvp.ui.screens.DailyReviewScreen
import com.timewallet.mvp.ui.screens.DataManagementDialog
import com.timewallet.mvp.ui.screens.EditRecordDialog
import com.timewallet.mvp.ui.screens.ExportJsonDialog
import com.timewallet.mvp.ui.screens.HistoryScreen
import com.timewallet.mvp.ui.screens.InsightsScreen
import com.timewallet.mvp.ui.screens.ManualEntrySheet
import com.timewallet.mvp.ui.screens.OverviewScreen
import com.timewallet.mvp.ui.screens.ProductInfoDialog
import com.timewallet.mvp.ui.screens.TimeBudgetEditorDialog
import com.timewallet.mvp.ui.screens.TimeWalletBottomBar
import com.timewallet.mvp.ui.screens.TimeWalletTopBar
import com.timewallet.mvp.ui.screens.TimerScreen
import com.timewallet.mvp.ui.theme.TimeWalletTheme
import com.timewallet.mvp.ui.theme.WalletBg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeWalletTheme {
                TimeWalletAppRoot()
            }
        }
    }
}

/**
 * v1.0.12 (P1-NAV): thanh tab chỉ ẩn khi bàn phím THẬT (ime inset ở đáy > 0) đang hiện ở Wally. Mọi trường hợp khác — kể cả khi hệ thống
 * không chuyển inset cho Compose (Android trước 15, LDPlayer Android 9) — thanh tab luôn hiện để người dùng không bị kẹt trong Wally.
 */
internal fun shouldShowBottomBar(currentTab: AppTab, imeBottomPx: Int): Boolean =
    !(imeBottomPx > 0 && currentTab == AppTab.CHAT)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeWalletAppRoot(viewModel: TimeWalletViewModel = viewModel()) {
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

    LaunchedEffect(state.currentTab, state.isShowingDailyReview, state.selectedReviewDateLocal) {
        scrollState.scrollTo(0)
    }

    // v1.0.11: nút ＋ nổi chỉ ở Nhật ký; Tổng quan đã có ô "Thêm giờ" ở màn hình đầu nên không để nút nổi che nội dung.
    val showFab = state.currentTab == AppTab.HISTORY

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletBg)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
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
                if (showFab) {
                    FloatingActionButton(
                        onClick = { viewModel.openManualEntry() },
                        containerColor = com.timewallet.mvp.ui.theme.WalletMint,
                        contentColor = Color.White,
                        modifier = Modifier.semantics { contentDescription = "Thêm giờ thủ công" }
                    ) {
                        Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
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
                                onNavigateToTab = { viewModel.selectTab(it) }
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
                        onCancelTimer = viewModel::cancelRunningTimer
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
                        onLastUserMessageChange = { lastChatUserMessage = it }
                    )

                    AppTab.HISTORY -> HistoryScreen(
                        todayDateLocal = state.todayDateLocal,
                        records = state.records,
                        selectedFilter = state.historyFilter,
                        onSelectFilter = viewModel::setHistoryFilter,
                        onConfirmPlannedOrActiveRecord = viewModel::confirmPlannedOrActiveRecord,
                        onEditRecord = viewModel::openEditRecord,
                        onDeleteRecord = viewModel::deleteRecord,
                        onAddManual = { viewModel.openManualEntry() }
                    )

                    AppTab.INSIGHTS -> InsightsScreen(
                        weeklyInsights = state.weeklyInsights,
                        onOpenBudgetEditor = viewModel::openBudgetEditor,
                        onDeleteBudget = { pendingBudgetDelete = it }
                    )
                }
                if (!isChatTab) Spacer(modifier = Modifier.height(if (showFab) 88.dp else 16.dp))
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
            }
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
            onChange = viewModel::updateManualEntry,
            onSave = viewModel::saveManualEntry,
            onDismiss = viewModel::closeManualEntry
        )
    }

    if (showDataDialog) {
        DataManagementDialog(
            onDismiss = { showDataDialog = false },
            onExportJson = { viewModel.prepareExportJson() },
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
