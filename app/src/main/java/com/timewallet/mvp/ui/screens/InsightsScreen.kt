package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DonutSlice
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.WeeklyInsightsSummary
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletRing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Nhãn đỉnh cột: ngắn để vừa cột hẹp ("2h15", "10h", "45p"); 0 = "–". */
internal fun compactBarLabel(seconds: Long): String {
    if (seconds <= 0L) return "–"
    // làm tròn đến phút gần nhất, đúng như formatHoursMinutesFromSeconds ở mọi chỗ khác (cùng một ngày không được ra hai số khác nhau)
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
    // không có mốc (chưa đặt ngân sách): chỉ giữ câu cuối (lời gợi ý); số liệu đã có ở các thẻ phía trên
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

@Composable
fun InsightsScreen(
    weeklyInsights: WeeklyInsightsSummary,
    onOpenBudgetEditor: (TimeCategory?) -> Unit = {},
    onDeleteBudget: (String) -> Unit = {},
    onReorderBudgetPriority: (String, String) -> Unit = { _, _ -> }
) {
    val fmt = LedgerAnalyticsCalculator::formatHoursMinutesFromSeconds
    val maxDaySeconds = remember(weeklyInsights.days) {
        weeklyInsights.days.maxOfOrNull { it.timedSeconds }?.coerceAtLeast(60L) ?: 60L
    }
    // Donut phân bổ: chỉ danh mục có mốc giờ; tỷ lệ tính trên tổng có mốc giờ (chưa gắn giờ tách riêng, không cộng gộp).
    val timedTotal = remember(weeklyInsights.categoryTotals) { weeklyInsights.categoryTotals.sumOf { it.durationSeconds } }
    val slices = remember(weeklyInsights.categoryTotals, timedTotal) {
        if (timedTotal <= 0L) emptyList() else weeklyInsights.categoryTotals
            .filter { it.durationSeconds > 0L }
            .sortedByDescending { it.durationSeconds }
            .map { DonutSlice(it.category, it.durationSeconds, it.durationSeconds.toFloat() / timedTotal.toFloat()) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenTitle("Phân tích")

        // Hai số của 7 ngày — KHÔNG bao giờ cộng gộp thành một tổng.
        SoftCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "7 ngày · Có giờ", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = WalletMuted)
                    Text(
                        text = fmt(weeklyInsights.totalTimedSeconds),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletMint,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(WalletRing)
                )
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(text = "Chưa gắn giờ", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = WalletMuted)
                    Text(
                        text = fmt(weeklyInsights.totalUntimedSeconds),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            val totalParallelOverlapSeconds = remember(weeklyInsights.days) {
                weeklyInsights.days.sumOf { it.parallelOverlapSeconds }
            }
            if (totalParallelOverlapSeconds > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Có hoạt động song song trong tuần (${fmt(totalParallelOverlapSeconds)} trùng giờ, quỹ ngày đã tự khử trùng).",
                    fontSize = 11.5.sp,
                    color = WalletMuted
                )
            }
        }

        // 7 ngày: biểu đồ cột (chỉ giờ có mốc xác định).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("7 ngày gần nhất")
            SoftCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyInsights.days.forEachIndexed { idx, day ->
                        val isToday = idx == weeklyInsights.days.lastIndex
                        val fillFraction = (day.timedSeconds.toFloat() / maxDaySeconds.toFloat()).coerceIn(0f, 1f)
                        val shortDayLabel = remember(day.dateLocal) {
                            try {
                                LocalDate.parse(day.dateLocal).format(DateTimeFormatter.ofPattern("EEE", Locale("vi", "VN")))
                            } catch (_: Exception) {
                                day.dateLocal.takeLast(2)
                            }
                        }
                        val topValueLabel = compactBarLabel(day.timedSeconds)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = topValueLabel,
                                fontSize = cappedSp(10f),
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) WalletMint else Color(0xFF4F6A5C),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(0.78f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEDF2ED))
                                    .then(
                                        if (isToday) Modifier.border(2.dp, Color(0xFF74BC91), RoundedCornerShape(10.dp)) else Modifier
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (fillFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(fillFraction.coerceAtLeast(0.06f))
                                            .background(
                                                brush = Brush.verticalGradient(listOf(Color(0xFF6FD0A6), Color(0xFF1F8066)))
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = shortDayLabel,
                                fontSize = cappedSp(12f),
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) WalletInk else Color(0xFF5F7168),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
                if (weeklyInsights.days.all { it.timedSeconds == 0L }) {
                    Text(
                        text = "Chưa có giờ nào trong 7 ngày qua.",
                        fontSize = 12.5.sp,
                        color = WalletMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        // Phân bổ theo danh mục (donut 2D tương tác + chú giải đồng bộ).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Phân bổ thời gian")
            SoftCard {
                if (slices.isEmpty()) {
                    EmptyState(
                        glyph = WalletGlyph.BARS,
                        title = "Chưa có dữ liệu có mốc giờ",
                        hint = "Ghi giờ cho hoạt động để xem biểu đồ."
                    )
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
                                label = "${slice.category.emoji} ${slice.category.displayName}",
                                value = fmt(slice.seconds),
                                dot = Color(slice.category.colorHex),
                                percentText = "${slice.percent}%",
                                isSelected = selectedSliceIndex == idx,
                                onClick = {
                                    selectedSliceIndex = if (selectedSliceIndex == idx) null else idx
                                }
                            )
                        }
                    }
                }
                if (weeklyInsights.untimedCategoryTotals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Chưa gắn giờ (tách riêng)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        weeklyInsights.untimedCategoryTotals.forEach { uStat ->
                            LegendRow(
                                label = "${uStat.category.emoji} ${uStat.category.displayName}",
                                value = fmt(uStat.durationSeconds),
                                dot = Color(0xFFC5CFC9)
                            )
                        }
                    }
                }
            }
        }

        // Ngân sách tuần: ở SAU biểu đồ. Hỗ trợ kéo-thả tay cầm ⋮⋮ và nút ↑/↓ để xếp thứ tự ưu tiên (live region).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                text = "Ngân sách tuần",
                actionText = "+ Đặt ngân sách",
                onAction = { onOpenBudgetEditor(null) }
            )
            if (weeklyInsights.budgetProgressList.isEmpty()) {
                SoftCard {
                    EmptyState(
                        glyph = WalletGlyph.BARS,
                        title = "Đặt mục tiêu đầu tiên",
                        hint = "Bạn muốn dành thời gian cho điều gì?",
                        actionLabel = "+ Tạo ngân sách",
                        onAction = { onOpenBudgetEditor(null) }
                    )
                }
            } else {
                val budgets = weeklyInsights.budgetProgressList
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

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            compactMode = false,
                            onEditClick = { cat -> onOpenBudgetEditor(cat) },
                            onDeleteClick = { cat -> onDeleteBudget(cat.key) },
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
                            dragHandleContent = if (groupIndices.size > 1) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isDragged) WalletMintTint else Color(0xFFF3F6F4))
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
                                                            "Đã nhấc ${item.category.displayName}. Kéo lên hoặc xuống trong nhóm ${if (isPriorityGroup) "ưu tiên" else "thường"}."
                                                    },
                                                    onDragCancel = {
                                                        draggingKey = null
                                                        dragOffsetY = 0f
                                                        dropTargetIndex = null
                                                        reorderLiveMessage = "Đã hủy sắp xếp."
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
                                                            reorderLiveMessage =
                                                                "Đã đặt ${item.category.displayName} tại vị trí ${targetIdx - groupMinIdx + 1} trong nhóm."
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
                                                            val destPos = nextTarget - groupMinIdx + 1
                                                            reorderLiveMessage = "Vị trí thả dự kiến: thứ $destPos (${budgets[nextTarget].category.displayName})."
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
                            extraFooterContent = if (groupIndices.size > 1) {
                                {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
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
                                                        val targetKey = budgets[idx - 1].category.key
                                                        onReorderBudgetPriority(key, targetKey)
                                                        reorderLiveMessage = "Đã đưa ${item.category.displayName} lên trước."
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
                                                        val targetKey = budgets[idx + 1].category.key
                                                        onReorderBudgetPriority(key, targetKey)
                                                        reorderLiveMessage = "Đã đưa ${item.category.displayName} xuống sau."
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

        // Gợi ý nhìn lại (dựa trên ngân sách thật; không phán xét, không tự đổi mục tiêu).
        // F3: gợi ý chỉ hiện khi đã có dữ liệu, thu gọn 2 dòng, chạm để xem đủ.
        if (weeklyInsights.totalTimedSeconds + weeklyInsights.totalUntimedSeconds > 0L) {
            var reflectionOpen by remember { mutableStateOf(false) }
            SoftCard(tint = WalletMintTint) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .walletClickable(onClickLabel = if (reflectionOpen) "Thu gọn gợi ý" else "Xem thêm gợi ý") { reflectionOpen = !reflectionOpen },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gợi ý nhìn lại",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = if (reflectionOpen) "▴" else "▾", fontSize = 14.sp, color = WalletMuted)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (reflectionOpen) weeklyInsights.reflectionPrompt else reflectionQuestion(weeklyInsights.reflectionPrompt),
                    fontSize = 13.5.sp,
                    color = Color(0xFF2E6147),
                    lineHeight = 20.sp,
                    maxLines = if (reflectionOpen) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Cách tính: ẩn mặc định.
        val weekRange = if (weeklyInsights.currentWeekStartLocal.isNotBlank() && weeklyInsights.currentWeekEndLocal.isNotBlank()) {
            "Tuần hiện tại: ${weeklyInsights.currentWeekStartLocal} → ${weeklyInsights.currentWeekEndLocal} (${ZoneId.systemDefault().id})."
        } else {
            "Theo múi giờ thiết bị (${ZoneId.systemDefault().id})."
        }
        InfoToggle(label = "Cách tính") {
            Text(
                text = "$weekRange Biểu đồ cột và phân bổ chỉ gồm thời gian có mốc giờ (đã khử trùng); thời lượng chưa gắn giờ được tách riêng, không cộng gộp. " +
                    "Mức đã ghi nhận của ngân sách gồm thời gian có mốc giờ và phần chưa gắn giờ; hoạt động Dự kiến không được tính. " +
                    "Mục tiêu do bạn tự quyết định; không chấm điểm ngủ, nghỉ ngơi hay giải trí.",
                fontSize = 12.sp,
                color = WalletMuted,
                lineHeight = 17.sp
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
