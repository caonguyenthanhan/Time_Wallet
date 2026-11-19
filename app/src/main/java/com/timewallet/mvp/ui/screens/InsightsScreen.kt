package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.CoachActionItem
import com.timewallet.mvp.domain.usecase.DonutSlice
import com.timewallet.mvp.domain.usecase.EfficiencyTier
import com.timewallet.mvp.domain.usecase.FocusPinPolicy
import com.timewallet.mvp.domain.usecase.FocusPinState
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.PeakFocusWindow
import com.timewallet.mvp.domain.usecase.PlanAccuracySummary
import com.timewallet.mvp.domain.usecase.WeeklyEfficiencySummary
import com.timewallet.mvp.domain.usecase.WeeklyInsightsSummary
import com.timewallet.mvp.ui.CoachUiState
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletRing
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EfficiencyGoalMint = Color(0xFF33A378)
private val EfficiencyNeutralBlue = Color(0xFF7894CF)
private val EfficiencyWasteCoral = Color(0xFFDE846F)
private val EfficiencyUnknownGray = Color(0xFFE0DFDB)

/** Nhãn đỉnh cột: ngắn để vừa cột hẹp ("2h15", "10h", "45p"); 0 = "–". */
internal fun compactBarLabel(seconds: Long): String {
    if (seconds <= 0L) return "–"
    val total = ((seconds + 30L) / 60L).coerceAtLeast(1L)
    val h = total / 60L
    val m = total % 60L
    return when {
        h >= 10L -> "${h}h"
        h > 0L && m > 0L -> "${h}h${m.toString().padStart(2, '0')}"
        h > 0L -> "${h}h"
        else -> "${m}p"
    }
}

/** Khi thu gọn chỉ hiện câu hỏi gợi ý (số liệu đã có ở thẻ ngân sách); không có mốc thì chỉ giữ câu cuối. */
internal fun reflectionQuestion(prompt: String): String {
    val question = prompt.substringAfter("Gợi ý phản tư:", "").trim()
    if (question.isNotEmpty()) return question
    return prompt.trim().substringAfterLast(". ", prompt)
}

/** Tính vị trí thả mục tiêu khi kéo thẻ ngân sách theo trục Y trong phạm vi [minIndex]..[maxIndex]. */
internal fun computeDragTargetIndex(
    currentIndex: Int,
    dragOffsetY: Float,
    itemHeightsPx: List<Float>,
    spacingPx: Float,
    minIndex: Int,
    maxIndex: Int
): Int {
    if (currentIndex !in itemHeightsPx.indices || minIndex > maxIndex) return currentIndex
    var target = currentIndex
    if (dragOffsetY > 0f) {
        var remaining = dragOffsetY
        while (target < maxIndex) {
            val nextH = itemHeightsPx.getOrElse(target + 1) { 180f } + spacingPx
            val threshold = (nextH * 0.52f).coerceAtLeast(36f)
            if (remaining >= threshold) {
                remaining -= nextH
                target++
            } else {
                break
            }
        }
    } else if (dragOffsetY < 0f) {
        var remaining = -dragOffsetY
        while (target > minIndex) {
            val prevH = itemHeightsPx.getOrElse(target - 1) { 180f } + spacingPx
            val threshold = (prevH * 0.52f).coerceAtLeast(36f)
            if (remaining >= threshold) {
                remaining -= prevH
                target--
            } else {
                break
            }
        }
    }
    return target.coerceIn(minIndex, maxIndex)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    weeklyInsights: WeeklyInsightsSummary,
    weeklyEfficiency: WeeklyEfficiencySummary = WeeklyEfficiencySummary(
        days = emptyList(),
        totalGoalSeconds = 0L,
        totalNeutralSeconds = 0L,
        totalWasteSeconds = 0L,
        totalUnknownSeconds = 0L,
        totalUntimedSeconds = 0L,
        goalItems = emptyList(),
        actionableInsight = ""
    ),
    goalsCount: Int = 0,
    tagsCount: Int = 0,
    coach: CoachUiState = CoachUiState(),
    aiEnabled: Boolean = false,
    hasSavedTodayReview: Boolean = false,
    onOpenBudgetEditor: (TimeCategory?) -> Unit = {},
    onDeleteBudget: (String) -> Unit = {},
    onReorderBudgetPriority: (String, String) -> Unit = { _, _ -> },
    onOpenGoals: () -> Unit = {},
    onOpenTags: () -> Unit = {},
    onOpenDailyReview: () -> Unit = {},
    onOpenTimer: () -> Unit = {},
    onOpenDataDialog: () -> Unit = {},
    onAskCoach: (Int) -> Unit = {},
    onDismissCoach: () -> Unit = {},
    onConfirmSuggestion: (Int) -> Unit = {},
    onDismissSuggestion: (Int) -> Unit = {},
    onTriggerAction: (CoachActionItem) -> Unit = {},
    focusPinEnabled: Boolean = false,
    focusPinPreview: FocusPinState = FocusPinPolicy.resolve(emptyList(), null, System.currentTimeMillis()),
    onToggleFocusPin: (Boolean) -> Unit = {},
    peakFocusWindow: PeakFocusWindow? = null,
    planAccuracySummary: PlanAccuracySummary? = null,
    onOpenMarkdownExport: () -> Unit = {},
    onOpenWallyChat: () -> Unit = {},
    onOpenProfileSettings: () -> Unit = {}
) {
    var showToolsSegment by rememberSaveable { mutableStateOf(false) }
    var selectedDayIndex by remember(weeklyEfficiency.days) {
        mutableIntStateOf((weeklyEfficiency.days.size - 1).coerceAtLeast(0))
    }
    var activeTierFilter by remember { mutableStateOf<EfficiencyTier?>(null) }
    var showEfficiencyInfoSheet by remember { mutableStateOf(false) }
    var showBudgetsSheet by remember { mutableStateOf(false) }
    var showCoachSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Đầu trang Mở rộng với chuyển đổi 2 ngăn Thống kê | Công cụ.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Mở rộng",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(WalletStroke.copy(alpha = 0.45f))
                    .border(1.dp, WalletStroke, RoundedCornerShape(100.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(false to "Thống kê", true to "Công cụ").forEach { (isTools, label) ->
                    val isSel = showToolsSegment == isTools
                    WalletPill(
                        onClickLabel = "Chuyển sang $label",
                        containerColor = if (isSel) WalletMintDark else Color.Transparent,
                        selected = isSel,
                        role = Role.Tab,
                        contentDescription = "Ngăn $label",
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        onClick = { showToolsSegment = isTools }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else WalletMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        if (!showToolsSegment) {
            EfficiencyAnalyticsSegment(
                weeklyInsights = weeklyInsights,
                weeklyEfficiency = weeklyEfficiency,
                selectedDayIndex = selectedDayIndex,
                onSelectDay = { selectedDayIndex = it },
                activeTierFilter = activeTierFilter,
                onToggleTierFilter = { clicked ->
                    activeTierFilter = if (activeTierFilter == clicked) null else clicked
                },
                onOpenInfo = { showEfficiencyInfoSheet = true },
                onEditGoals = onOpenGoals,
                peakFocusWindow = peakFocusWindow,
                planAccuracySummary = planAccuracySummary
            )
        } else {
            ToolsHubSegment(
                goalsCount = goalsCount,
                budgetsCount = weeklyInsights.budgetProgressList.size,
                tagsCount = tagsCount,
                hasSavedTodayReview = hasSavedTodayReview,
                aiEnabled = aiEnabled,
                focusPinEnabled = focusPinEnabled,
                focusPinPreview = focusPinPreview,
                onToggleFocusPin = onToggleFocusPin,
                onOpenGoals = onOpenGoals,
                onOpenBudgets = { showBudgetsSheet = true },
                onOpenTags = onOpenTags,
                onOpenDailyReview = onOpenDailyReview,
                onOpenCoach = { showCoachSheet = true },
                onOpenTimer = onOpenTimer,
                onOpenDataDialog = onOpenDataDialog,
                onOpenMarkdownExport = onOpenMarkdownExport,
                onOpenWallyChat = onOpenWallyChat,
                onOpenProfileSettings = onOpenProfileSettings
            )
        }
    }

    if (showEfficiencyInfoSheet) {
        val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEfficiencyInfoSheet = false },
            sheetState = infoSheetState,
            containerColor = WalletCardBg
        ) {
            EfficiencyInfoSheetContent()
        }
    }

    if (showBudgetsSheet) {
        val budgetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBudgetsSheet = false },
            sheetState = budgetSheetState,
            containerColor = WalletBg
        ) {
            BudgetsManagerSheetContent(
                budgets = weeklyInsights.budgetProgressList,
                onOpenBudgetEditor = onOpenBudgetEditor,
                onDeleteBudget = onDeleteBudget,
                onReorderBudgetPriority = onReorderBudgetPriority
            )
        }
    }

    if (showCoachSheet) {
        val coachSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCoachSheet = false },
            sheetState = coachSheetState,
            containerColor = WalletCardBg
        ) {
            CoachSheetContent(
                coach = coach,
                aiEnabled = aiEnabled,
                onAskCoach = onAskCoach,
                onDismissCoach = onDismissCoach,
                onConfirmSuggestion = onConfirmSuggestion,
                onDismissSuggestion = onDismissSuggestion,
                onTriggerAction = { action ->
                    showCoachSheet = false
                    onTriggerAction(action)
                }
            )
        }
    }
}

@Composable
private fun EfficiencyAnalyticsSegment(
    weeklyInsights: WeeklyInsightsSummary,
    weeklyEfficiency: WeeklyEfficiencySummary,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    activeTierFilter: EfficiencyTier?,
    onToggleTierFilter: (EfficiencyTier) -> Unit,
    onOpenInfo: () -> Unit,
    onEditGoals: () -> Unit,
    peakFocusWindow: PeakFocusWindow? = null,
    planAccuracySummary: PlanAccuracySummary? = null
) {
    val fmt = LedgerAnalyticsCalculator::formatHoursMinutesFromSeconds
    val days = weeklyEfficiency.days
    val safeDayIdx = selectedDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val selectedDay = days.getOrNull(safeDayIdx)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tách riêng tổng chưa gắn giờ (nếu có) theo bất biến sản phẩm, không trộn vào cột 24h.
        if (weeklyInsights.totalUntimedSeconds > 0L) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = WalletCardBg,
                border = BorderStroke(1.dp, WalletStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chưa gắn giờ (7 ngày · tách riêng)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = fmt(weeklyInsights.totalUntimedSeconds),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk
                    )
                }
            }
        }

        // Thẻ 1: Biểu đồ 24h x 7 ngày (Mục tiêu / Phi mục tiêu / Lãng phí / Chưa rõ).
        SoftCard(padding = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phân bổ 24 giờ · 7 ngày",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk
                    )
                    Text(
                        text = "Chạm ngày xem chi tiết · chạm màu để lọc tầng",
                        fontSize = 11.5.sp,
                        color = WalletMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(WalletMintTint)
                        .border(1.dp, WalletStroke, CircleShape)
                        .semantics { contentDescription = "Giải thích 4 tầng hiệu quả 24h" }
                        .walletClickable(onClickLabel = "Giải thích 4 tầng thời gian", onClick = onOpenInfo),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ⓘ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WalletMintAccent)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(228.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { idx, day ->
                    val isSelectedDay = idx == safeDayIdx
                    val totalDaySecs = day.calendarDaySeconds.coerceAtLeast(1L).toFloat()
                    val topLabelSecs = day.secondsFor(activeTierFilter)
                    val shortDayLabel = remember(day.dateLocal) {
                        try {
                            LocalDate.parse(day.dateLocal).format(DateTimeFormatter.ofPattern("EEE", Locale("vi", "VN")))
                        } catch (_: Exception) {
                            day.dateLocal.takeLast(2)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .walletClickable(onClickLabel = "Xem chi tiết ngày ${day.dateLocal}") { onSelectDay(idx) }
                            .padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = compactBarLabel(topLabelSecs),
                            fontSize = cappedSp(10.5f),
                            fontWeight = FontWeight.Bold,
                            color = if (isSelectedDay) MaterialTheme.colorScheme.primary else WalletMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Cột luôn biểu diễn trọn 24h: xếp từ dưới lên (Mục tiêu -> Phi mục tiêu -> Lãng phí -> Chưa rõ).
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.84f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSelectedDay) 2.dp else 1.dp,
                                    color = if (isSelectedDay) MaterialTheme.colorScheme.primary else WalletStroke,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            val tiersBottomUp = listOf(
                                EfficiencyTier.UNKNOWN to day.unknownSeconds,
                                EfficiencyTier.WASTE to day.wasteSeconds,
                                EfficiencyTier.NEUTRAL to day.neutralSeconds,
                                EfficiencyTier.GOAL to day.goalSeconds
                            )
                            tiersBottomUp.forEach { (tier, secs) ->
                                if (secs > 0L) {
                                    val w = (secs.toFloat() / totalDaySecs).coerceAtLeast(0.008f)
                                    val dimmed = activeTierFilter != null && activeTierFilter != tier
                                    val segColor = when (tier) {
                                        EfficiencyTier.GOAL -> EfficiencyGoalMint
                                        EfficiencyTier.NEUTRAL -> EfficiencyNeutralBlue
                                        EfficiencyTier.WASTE -> EfficiencyWasteCoral
                                        EfficiencyTier.UNKNOWN -> EfficiencyUnknownGray
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(w)
                                            .alpha(if (dimmed) 0.25f else 1f)
                                            .background(segColor)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = shortDayLabel,
                            fontSize = cappedSp(12f),
                            fontWeight = if (isSelectedDay) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelectedDay) WalletInk else WalletMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }

            if (selectedDay != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val dayTitle = remember(selectedDay.dateLocal) {
                    runCatching {
                        LocalDate.parse(selectedDay.dateLocal).format(DateTimeFormatter.ofPattern("EEE, dd/MM", Locale("vi", "VN")))
                    }.getOrDefault(selectedDay.dateLocal)
                }
                val tierName = activeTierFilter?.labelVi ?: "Đã dùng"
                val shownSecs = selectedDay.secondsFor(activeTierFilter)
                val shownLabel = if (shownSecs > 0L) LedgerAnalyticsCalculator.formatCompactDurationSeconds(shownSecs) else "0p"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WalletBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "$dayTitle · $tierName: $shownLabel / 24h",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chú giải 2x2 tương tác lọc theo từng tầng (tổng cả 7 ngày).
            Text(
                text = "Tổng 7 ngày theo tầng · Chạm để lọc",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WalletMuted,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            val legendItems = listOf(
                Triple(EfficiencyTier.GOAL, EfficiencyGoalMint, weeklyEfficiency.totalGoalSeconds),
                Triple(EfficiencyTier.NEUTRAL, EfficiencyNeutralBlue, weeklyEfficiency.totalNeutralSeconds),
                Triple(EfficiencyTier.WASTE, EfficiencyWasteCoral, weeklyEfficiency.totalWasteSeconds),
                Triple(EfficiencyTier.UNKNOWN, EfficiencyUnknownGray, weeklyEfficiency.totalUnknownSeconds)
            )
            legendItems.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { (tier, color, weekSecs) ->
                        val isActive = activeTierFilter == tier
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isActive) WalletMintTint else WalletBg)
                                .border(1.dp, if (isActive) WalletMint else WalletStroke, RoundedCornerShape(11.dp))
                                .semantics { this.selected = isActive }
                                .walletClickable(onClickLabel = "Lọc tầng ${tier.labelVi}") { onToggleTierFilter(tier) }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
                            Text(
                                text = tier.labelVi,
                                fontSize = 11.5.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = WalletInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (weekSecs > 0L) LedgerAnalyticsCalculator.formatCompactDurationSeconds(weekSecs) else "0p",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletMuted,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Thẻ 2: Tiến độ mục tiêu trực quan (không văn bản giải thích dài dòng).
        SoftCard(padding = 14.dp) {
            SectionTitle(
                text = "Tiến độ mục tiêu",
                actionText = "Sửa ›",
                onAction = onEditGoals
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (weeklyEfficiency.goalItems.isEmpty()) {
                Text(
                    text = "Chưa có mục tiêu nào. Chạm 'Sửa ›' để đặt mục tiêu đầu tiên.",
                    fontSize = 12.5.sp,
                    color = WalletMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    weeklyEfficiency.goalItems.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = item.progressText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (item.met) MaterialTheme.colorScheme.primary else WalletMuted
                                )
                            }
                            LinearProgressIndicator(
                                progress = { item.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(item.accentHex),
                                trackColor = WalletRing
                            )
                        }
                    }
                }
            }
        }

        // Thẻ 3: Một nhận định ngắn, có giá trị hành động từ Wally (không chú thích mô phỏng, không nút đặt giờ).
        SoftCard(padding = 14.dp, tint = WalletMintTint) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(WalletMintDark)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text("✦ Wally", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
                }
                Text(
                    text = weeklyEfficiency.actionableInsight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WalletInk,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Thẻ Intelligence 2.0: Giờ vàng tập trung & Độ sát kế hoạch (có nguồn dữ liệu minh bạch).
        if (peakFocusWindow != null || (planAccuracySummary != null && planAccuracySummary.sampleCount > 0)) {
            SoftCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nhịp tập trung & Độ sát kế hoạch",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk
                        )
                        Text(
                            text = "ⓘ Sổ cái cục bộ",
                            fontSize = 11.5.sp,
                            color = WalletMuted
                        )
                    }
                    if (peakFocusWindow != null) {
                        Text(
                            text = "Giờ vàng: ${peakFocusWindow.windowLabel} (${peakFocusWindow.goalMinutes}p · ${peakFocusWindow.sharePercent}% giờ mục tiêu 14 ngày)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (planAccuracySummary != null && planAccuracySummary.sampleCount > 0) {
                        Text(
                            text = planAccuracySummary.summaryText,
                            fontSize = 13.sp,
                            color = WalletInk
                        )
                    }
                }
            }
        }

        // Phân bổ theo danh mục (thu gọn mặc định để Thống kê thoáng gọn).
        val timedTotal = remember(weeklyInsights.categoryTotals) { weeklyInsights.categoryTotals.sumOf { it.durationSeconds } }
        val slices = remember(weeklyInsights.categoryTotals, timedTotal) {
            if (timedTotal <= 0L) emptyList() else weeklyInsights.categoryTotals
                .filter { it.durationSeconds > 0L }
                .sortedByDescending { it.durationSeconds }
                .map { DonutSlice(it.category, it.durationSeconds, it.durationSeconds.toFloat() / timedTotal.toFloat()) }
        }
        InfoToggle(label = "Chi tiết Donut 7 ngày theo danh mục") {
            if (slices.isEmpty()) {
                Text("Chưa có dữ liệu có mốc giờ trong 7 ngày qua.", fontSize = 12.sp, color = WalletMuted)
            } else {
                val displaySlices = remember(slices) { capDonutSlices(slices, maxSlices = 6) }
                var selectedSliceIndex by remember(displaySlices) { mutableStateOf<Int?>(null) }
                val selectedSlice = selectedSliceIndex?.let { displaySlices.getOrNull(it) }
                val description = displaySlices.joinToString(", ") { "${it.category.displayName} ${it.percent}%" }
                DonutWithLegend(
                    slices = displaySlices,
                    centerTop = selectedSlice?.let { fmt(it.seconds) } ?: fmt(timedTotal),
                    centerBottom = selectedSlice?.let { "${it.category.displayName} · ${it.percent}%" } ?: "7 ngày",
                    description = "Phân bổ thời gian có mốc giờ trong 7 ngày: $description",
                    selectedIndex = selectedSliceIndex,
                    onSelectSlice = { selectedSliceIndex = it }
                ) {
                    displaySlices.forEachIndexed { idx, slice ->
                        LegendRow(
                            label = slice.category.displayName,
                            value = fmt(slice.seconds),
                            dot = Color(slice.category.colorHex),
                            category = slice.category,
                            percentText = "${slice.percent}%",
                            isSelected = selectedSliceIndex == idx,
                            onClick = {
                                selectedSliceIndex = if (selectedSliceIndex == idx) null else idx
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsHubSegment(
    goalsCount: Int,
    budgetsCount: Int,
    tagsCount: Int,
    hasSavedTodayReview: Boolean,
    aiEnabled: Boolean,
    focusPinEnabled: Boolean,
    focusPinPreview: FocusPinState,
    onToggleFocusPin: (Boolean) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenDailyReview: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenDataDialog: () -> Unit,
    onOpenMarkdownExport: () -> Unit,
    onOpenWallyChat: () -> Unit,
    onOpenProfileSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Thẻ Ghim thông báo tập trung nổi bật ở đầu trang Công cụ
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = WalletCardBg,
            border = BorderStroke(if (focusPinEnabled) 1.5.dp else 1.dp, if (focusPinEnabled) WalletMint else WalletStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WalletMintTint),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(glyph = WalletGlyph.NOTIFY, color = WalletMintAccent, size = 20.dp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Ghim thông báo tập trung", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                    Text(
                        text = if (focusPinEnabled) focusPinPreview.headline else "Hiện việc theo lịch hoặc gợi ý từ lịch sử",
                        fontSize = 12.sp,
                        color = WalletMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = focusPinEnabled,
                    onCheckedChange = onToggleFocusPin,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = WalletMint
                    )
                )
            }
        }

        // 2. Nhóm Quản lý & Theo dõi (Lưới 2x2 ngay ngắn, cùng chiều cao mỗi hàng)
        Text(
            text = "KẾ HOẠCH & MỤC TIÊU",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = WalletMuted,
            modifier = Modifier.padding(start = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolGridTile(
                    glyph = WalletGlyph.GOAL,
                    title = "Mục tiêu",
                    meta = if (goalsCount > 0) "$goalsCount đang theo dõi" else "Đặt MIN / MAX",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenGoals
                )
                ToolGridTile(
                    glyph = WalletGlyph.BARS,
                    title = "Ngân sách thời gian",
                    meta = if (budgetsCount > 0) "$budgetsCount hạn mức tuần" else "Giới hạn danh mục",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenBudgets
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolGridTile(
                    glyph = WalletGlyph.SPARK,
                    title = "Tag & danh mục",
                    meta = if (tagsCount > 0) "$tagsCount tag hoạt động" else "Nhãn cá nhân",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTags
                )
                ToolGridTile(
                    glyph = WalletGlyph.BOOK,
                    title = "Nhìn lại ngày",
                    meta = if (hasSavedTodayReview) "✓ Đã lưu hôm nay" else "Phản tư cuối ngày",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDailyReview
                )
            }
        }

        // 3. Nhóm Trợ lý & Tập trung (Lưới 2 cột + Nút mở nhanh Wally Chat)
        Text(
            text = "TRỢ LÝ & TẬP TRUNG",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = WalletMuted,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolGridTile(
                    glyph = WalletGlyph.WALLY_BOT,
                    title = "Wally AI Coach",
                    meta = if (aiEnabled) "Tóm tắt 5 giây · Đã bật" else "Tổng kết & gợi ý",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCoach
                )
                ToolGridTile(
                    glyph = WalletGlyph.DICE,
                    title = "Xúc xắc thời gian",
                    meta = "Gợi ý phiên ngẫu nhiên",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTimer
                )
            }
            ToolRowCard(
                glyph = WalletGlyph.WALLY_BOT,
                title = "Chat với Wally AI",
                subtitle = "Ghi nhiều hoạt động tự nhiên, hỏi dữ liệu & duyệt đề xuất",
                onClick = onOpenWallyChat
            )
        }

        // 4. Nhóm Dữ liệu & Hệ thống gộp gọn trong một thẻ có vạch phân tách mảnh
        Text(
            text = "DỮ LIỆU & CÀI ĐẶT",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = WalletMuted,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = WalletCardBg,
            border = BorderStroke(1.dp, WalletStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GroupedToolRow(
                    glyph = WalletGlyph.SETTINGS,
                    title = "Dữ liệu & sao lưu",
                    subtitle = "Xuất / nhập JSON an toàn, nạp mẫu hoặc dọn dữ liệu",
                    onClick = onOpenDataDialog
                )
                HorizontalDivider(color = WalletStroke, modifier = Modifier.padding(horizontal = 14.dp))
                GroupedToolRow(
                    glyph = WalletGlyph.BOOK,
                    title = "Xuất nhật ký Markdown (Obsidian)",
                    subtitle = "Tóm tắt ngày cục bộ sẵn sàng dán vào kho ghi chú",
                    onClick = onOpenMarkdownExport
                )
                HorizontalDivider(color = WalletStroke, modifier = Modifier.padding(horizontal = 14.dp))
                GroupedToolRow(
                    glyph = WalletGlyph.SETTINGS,
                    title = "Giao diện, Module & Quyền AI",
                    subtitle = "Chủ đề Sáng/Tối, module hiển thị, quyền đọc AI và quy tắc tự động",
                    onClick = onOpenProfileSettings
                )
            }
        }
    }
}

@Composable
private fun ToolGridTile(
    glyph: WalletGlyph,
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WalletCardBg,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = modifier
            .heightIn(min = 98.dp)
            .walletClickable(onClickLabel = "Mở $title", onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(WalletMintTint),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(glyph = glyph, color = WalletMint, size = 18.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    fontSize = 11.5.sp,
                    color = WalletMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GroupedToolRow(
    glyph: WalletGlyph,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .walletClickable(onClickLabel = "Mở $title", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WalletMintTint),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph = glyph, color = WalletMint, size = 18.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WalletInk)
            Text(text = subtitle, fontSize = 12.sp, color = WalletMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        GlyphIcon(glyph = WalletGlyph.CHEVRON, color = WalletMuted, size = 18.dp)
    }
}

@Composable
private fun ToolRowCard(
    glyph: WalletGlyph,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WalletCardBg,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = Modifier
            .fillMaxWidth()
            .walletClickable(onClickLabel = "Mở $title", onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WalletMintTint),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(glyph = glyph, color = WalletMint, size = 20.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                Text(text = subtitle, fontSize = 12.sp, color = WalletMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            GlyphIcon(glyph = WalletGlyph.CHEVRON, color = WalletMuted, size = 18.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EfficiencyInfoSheetContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Phân loại 24 giờ hiệu quả",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WalletInk
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Mục tiêu" to EfficiencyGoalMint,
                "Phi mục tiêu" to EfficiencyNeutralBlue,
                "Lãng phí" to EfficiencyWasteCoral,
                "Chưa rõ" to EfficiencyUnknownGray
            ).forEach { (label, c) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WalletBg)
                        .border(1.dp, WalletStroke, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(c))
                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                }
            }
        }
        Text(
            text = "• Phi mục tiêu gồm giấc ngủ, ăn uống sinh hoạt, nghỉ ngơi phục hồi và giải trí vừa đủ — hoàn toàn không bị đánh đồng là lãng phí.\n" +
                "• Chỉ tính Lãng phí khi chính bạn chủ động đánh dấu ở biểu mẫu hoạt động.\n" +
                "• Chưa rõ là khoảng giờ chưa có dữ liệu chi tiết; mục chưa gắn mốc giờ luôn tách riêng, không cộng trùng.",
            fontSize = 12.5.sp,
            color = WalletInk,
            lineHeight = 18.sp
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = WalletBg,
            border = BorderStroke(1.dp, WalletStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Cơ sở khoa học của tầng Phi mục tiêu",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Giấc ngủ (AASM & SRS Consensus, Watson et al., 2015): người trưởng thành cần từ 7 giờ ngủ/ngày trở lên để duy trì nhận thức, miễn dịch và hạn chế sai sót.\n" +
                        "• Nghỉ phục hồi (Albulescu et al., 2022, PLOS ONE): khoảng nghỉ giữa phiên giúp tăng sinh lực (d = 0.36) và giảm mệt mỏi (d = 0.35).\n" +
                        "• Giải trí vừa đủ (Sonnentag, Effort-Recovery & Detachment): ngắt khỏi áp lực công việc giúp tái tạo tài nguyên chú ý và nâng hiệu suất hôm sau.",
                    fontSize = 11.5.sp,
                    color = WalletMuted,
                    lineHeight = 16.5.sp
                )
            }
        }
    }
}

@Composable
private fun BudgetsManagerSheetContent(
    budgets: List<TimeBudgetProgress>,
    onOpenBudgetEditor: (TimeCategory?) -> Unit,
    onDeleteBudget: (String) -> Unit,
    onReorderBudgetPriority: (String, String) -> Unit
) {
    var editSortMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Ngân sách tuần",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (budgets.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { editSortMode = !editSortMode },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (editSortMode) "Xong" else "Sắp xếp / Sửa",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Button(
                    onClick = { onOpenBudgetEditor(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+ Đặt", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (budgets.isEmpty()) {
            SoftCard {
                EmptyState(
                    glyph = WalletGlyph.BARS,
                    title = "Chưa có ngân sách tuần",
                    hint = "Đặt hạn mức để đối chiếu thực tế cho từng danh mục.",
                    actionLabel = "+ Tạo ngân sách",
                    onAction = { onOpenBudgetEditor(null) }
                )
            }
        } else {
            val itemHeightsPx = remember { mutableStateMapOf<String, Float>() }
            var draggingKey by remember { mutableStateOf<String?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
            var reorderLiveMessage by remember { mutableStateOf("") }
            val spacingPx = with(LocalDensity.current) { 10.dp.toPx() }
            val draggingIndex = budgets.indexOfFirst { it.category.key == draggingKey }

            if (reorderLiveMessage.isNotBlank()) {
                Text(
                    text = reorderLiveMessage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletMint,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            budgets.forEachIndexed { idx, item ->
                val key = item.category.key
                val isPriorityGroup = item.budget.isPriority
                val groupIndices = budgets.indices.filter { budgets[it].budget.isPriority == isPriorityGroup }
                val groupMinIdx = groupIndices.firstOrNull() ?: idx
                val groupMaxIdx = groupIndices.lastOrNull() ?: idx
                val canMoveUp = idx > groupMinIdx
                val canMoveDown = idx < groupMaxIdx
                val isDragged = draggingKey == key

                if (draggingKey != null && dropTargetIndex == idx && draggingIndex >= 0 && dropTargetIndex!! < draggingIndex) {
                    BudgetInsertionLine()
                }

                val shiftPx = when {
                    isDragged -> dragOffsetY
                    draggingKey == null || draggingIndex < 0 || dropTargetIndex == null -> 0f
                    idx in (draggingIndex + 1)..dropTargetIndex!! ->
                        -(itemHeightsPx[draggingKey] ?: 180f) - spacingPx
                    idx in dropTargetIndex!! until draggingIndex ->
                        (itemHeightsPx[draggingKey] ?: 180f) + spacingPx
                    else -> 0f
                }

                TimeBudgetItemCard(
                    progress = item,
                    compactMode = !editSortMode,
                    onEditClick = { cat -> onOpenBudgetEditor(cat) },
                    onDeleteClick = if (editSortMode) { cat -> onDeleteBudget(cat.key) } else null,
                    isBeingDragged = isDragged,
                    modifier = Modifier
                        .zIndex(if (isDragged) 10f else 0f)
                        .graphicsLayer {
                            translationY = shiftPx
                            if (isDragged) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                                alpha = 0.94f
                            }
                        }
                        .onGloballyPositioned { coords ->
                            itemHeightsPx[key] = coords.size.height.toFloat()
                        },
                    dragHandleContent = if (editSortMode && groupIndices.size > 1) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDragged) WalletMintTint else WalletSoftNeutral)
                                    .semantics {
                                        contentDescription = "Tay kéo sắp xếp ngân sách ${item.category.displayName}"
                                    }
                                    .pointerInput(key, budgets, groupMinIdx, groupMaxIdx) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingKey = key
                                                dragOffsetY = 0f
                                                dropTargetIndex = idx
                                                reorderLiveMessage =
                                                    "Đã nhấc ${item.category.displayName}."
                                            },
                                            onDragCancel = {
                                                draggingKey = null
                                                dragOffsetY = 0f
                                                dropTargetIndex = null
                                            },
                                            onDragEnd = {
                                                val targetIdx = dropTargetIndex
                                                val fromKey = draggingKey
                                                draggingKey = null
                                                dragOffsetY = 0f
                                                dropTargetIndex = null
                                                if (fromKey != null && targetIdx != null && targetIdx != idx && targetIdx in budgets.indices) {
                                                    val targetCat = budgets[targetIdx].category
                                                    onReorderBudgetPriority(fromKey, targetCat.key)
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val heights = budgets.map { itemHeightsPx[it.category.key] ?: 180f }
                                                val nextTarget = computeDragTargetIndex(
                                                    currentIndex = idx,
                                                    dragOffsetY = dragOffsetY,
                                                    itemHeightsPx = heights,
                                                    spacingPx = spacingPx,
                                                    minIndex = groupMinIdx,
                                                    maxIndex = groupMaxIdx
                                                )
                                                if (nextTarget != dropTargetIndex) {
                                                    dropTargetIndex = nextTarget
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(WalletGlyph.DRAG, color = if (isDragged) WalletMint else WalletMuted, size = 20.dp)
                            }
                        }
                    } else null,
                    extraFooterContent = if (editSortMode && groupIndices.size > 1) {
                        {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (canMoveUp) {
                                    Text(
                                        text = "↑ Lên",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletMint,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .walletClickable(onClickLabel = "Đưa ${item.category.displayName} lên trước") {
                                                onReorderBudgetPriority(key, budgets[idx - 1].category.key)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (canMoveDown) {
                                    Text(
                                        text = "↓ Xuống",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletMint,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .walletClickable(onClickLabel = "Đưa ${item.category.displayName} xuống sau") {
                                                onReorderBudgetPriority(key, budgets[idx + 1].category.key)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else null
                )

                if (draggingKey != null && dropTargetIndex == idx && draggingIndex >= 0 && dropTargetIndex!! > draggingIndex) {
                    BudgetInsertionLine()
                }
            }
        }
    }
}

@Composable
private fun CoachSheetContent(
    coach: CoachUiState,
    aiEnabled: Boolean,
    onAskCoach: (Int) -> Unit,
    onDismissCoach: () -> Unit,
    onConfirmSuggestion: (Int) -> Unit,
    onDismissSuggestion: (Int) -> Unit,
    onTriggerAction: (CoachActionItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WallyMascotAvatar(size = 40.dp, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("Wally AI Coach", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                Text(
                    text = if (aiEnabled) "Tóm tắt 5 giây · Điểm sáng · Action items" else "Bật AI trong Hồ sơ & Cài đặt để nhận tóm tắt cá nhân hóa",
                    fontSize = 12.sp,
                    color = WalletMuted
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onAskCoach(1) },
                enabled = !coach.loading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("✦ Hôm nay", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { onAskCoach(7) },
                enabled = !coach.loading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("✦ 7 ngày qua", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        }
        if (coach.requested || coach.loading) {
            CoachPanel(
                coach = coach,
                onDismiss = onDismissCoach,
                onConfirmSuggestion = onConfirmSuggestion,
                onDismissSuggestion = onDismissSuggestion,
                onTriggerAction = onTriggerAction
            )
        }
    }
}

/** Đường vạch vị trí chèn mục tiêu khi đang kéo thẻ ngân sách. */
@Composable
private fun BudgetInsertionLine() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(WalletMint)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(WalletMint)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(WalletMint)
        )
    }
}
