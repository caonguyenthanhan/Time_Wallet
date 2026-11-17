package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.data.local.ManualEntryDraft
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.domain.usecase.DayLedgerStats
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.WallyGreeting
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintBright
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintMid
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    todayDateLocal: String,
    todayStats: DayLedgerStats,
    recentRecords: List<LedgerRecord>,
    priorityBudgets: List<TimeBudgetProgress> = emptyList(),
    hasSavedTodayReview: Boolean = false,
    onOpenDailyReview: () -> Unit = {},
    onOpenBudgetEditor: (TimeCategory?) -> Unit = {},
    onAddManual: () -> Unit = {},
    onConfirmOrResolveRecord: (LedgerRecord) -> Unit = {},
    pendingManualDraft: ManualEntryDraft? = null,
    onResumeManualDraft: () -> Unit = {},
    onCleanManualDraft: () -> Unit = {},
    onNavigateToTab: (AppTab) -> Unit
) {
    val dateHeader = remember(todayDateLocal) {
        try {
            LocalDate.parse(todayDateLocal)
                .format(DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale("vi", "VN")))
                .replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            "Hôm nay"
        }
    }
    val summary = remember(todayStats) { DashboardSummary.from(todayStats) }
    val greetingHour = remember(todayDateLocal) { LocalTime.now().hour }
    val fmt = LedgerAnalyticsCalculator::formatHoursMinutesFromSeconds
    val recordedFormatted = fmt(summary.recordedSeconds)
    val totalDayHoursLabel = "${todayStats.calendarDaySeconds / 3600L}h"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Lời chào một câu (cục bộ, không gọi API) + ngày.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WallyMascotAvatar(
                size = 46.dp,
                contentDescription = "Wally, trợ lý thời gian"
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WallyGreeting.dashboardLine(greetingHour),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk
                )
                Text(text = dateHeader, fontSize = 13.sp, color = WalletMuted)
            }
        }

        // Hero: ví thời gian của ngày. Số lớn = đã ghi nhận; "chưa ghi nhận" là phần còn lại của 24h theo quy ước, không phải giờ rảnh.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(WalletMintDark, WalletMintMid, WalletMintBright)))
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = "VÍ THỜI GIAN HÔM NAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                    color = Color(0xFFCDEBDD)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = recordedFormatted,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
                Text(text = "đã ghi nhận", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .describe("Đã ghi nhận $recordedFormatted trên $totalDayHoursLabel của ngày")
                ) {
                    if (summary.progressFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(summary.progressFraction.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "trên $totalDayHoursLabel của ngày", fontSize = 12.sp, color = Color(0xFFCDEBDD))
                if (summary.untimedSeconds > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Trong đó chưa gắn giờ ${fmt(summary.untimedSeconds)}",
                        fontSize = 13.sp,
                        color = Color(0xFFCDEBDD)
                    )
                }
                if (summary.plannedSeconds > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Dự kiến ${fmt(summary.plannedSeconds)} · chưa tính vào đã ghi nhận",
                        fontSize = 12.sp,
                        color = Color(0xFFCDEBDD)
                    )
                }
                if (summary.recordedExceedsDay) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tổng đã ghi nhận vượt quá độ dài của ngày. Phần chưa gắn giờ có thể trùng phần có mốc giờ; hãy kiểm tra lại các bản ghi.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFE9B8)
                    )
                }
            }
        }

        // Thao tác nhanh: Đồng hồ / Thêm giờ / Hỏi Wally.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionTile(WalletGlyph.CLOCK, "Đồng hồ", "Mở tab Đồng hồ", Modifier.weight(1f)) { onNavigateToTab(AppTab.TIMER) }
            QuickActionTile(WalletGlyph.PLUS, "Thêm giờ", "Nhập giờ thủ công", Modifier.weight(1f), onAddManual)
            QuickActionTile(WalletGlyph.WALLY_BOT, "Hỏi Wally", "Mở tab Wally", Modifier.weight(1f)) { onNavigateToTab(AppTab.CHAT) }
        }

        // Donut phân bổ theo danh mục (tỷ lệ trên tổng đã ghi nhận).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Phân bổ theo danh mục")
            SoftCard {
                if (summary.isEmpty) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DonutChart(
                            slices = emptyList(),
                            centerTop = "0h",
                            modifier = Modifier.size(104.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Chưa có giờ nào được ghi hôm nay.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletInk
                            )
                            Text(text = "Bật đồng hồ, bấm Thêm giờ hoặc hỏi Wally.", fontSize = 12.5.sp, color = WalletMuted)
                        }
                    }
                } else {
                    val displaySlices = remember(summary.slices) { capDonutSlices(summary.slices, maxSlices = 6) }
                    var selectedSliceIndex by remember(displaySlices) { mutableStateOf<Int?>(null) }
                    val selectedSlice = selectedSliceIndex?.let { displaySlices.getOrNull(it) }
                    val donutDescription = displaySlices.joinToString(", ") { "${it.category.displayName} ${it.percent}%" }
                    DonutWithLegend(
                        slices = displaySlices,
                        centerTop = selectedSlice?.let { fmt(it.seconds) } ?: recordedFormatted,
                        centerBottom = selectedSlice?.let { "${it.category.displayName} · ${it.percent}%" } ?: "đã ghi nhận",
                        description = "Biểu đồ phân bổ theo danh mục: $donutDescription",
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

        // Trục 24 giờ: chỉ thời gian có mốc giờ (đúng quy ước), cộng phần đã qua chưa ghi và phần còn lại trong ngày.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Trục 24 giờ hôm nay")
            SoftCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFFEDF0ED)),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    val totalDaySecs = todayStats.calendarDaySeconds.coerceAtLeast(1L).toFloat()
                    todayStats.categoryBreakdown.forEach { catStat ->
                        val weight = (catStat.durationSeconds.toFloat() / totalDaySecs).coerceIn(0.01f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(Color(catStat.category.colorHex))
                        )
                    }
                    if (todayStats.untrackedPastSeconds > 0L) {
                        Box(
                            modifier = Modifier
                                .weight((todayStats.untrackedPastSeconds.toFloat() / totalDaySecs).coerceAtLeast(0.015f))
                                .fillMaxHeight()
                                .background(Color(0xFFC5CFC9))
                        )
                    }
                    if (todayStats.remainingDaySeconds > 0L) {
                        Box(
                            modifier = Modifier
                                .weight((todayStats.remainingDaySeconds.toFloat() / totalDaySecs).coerceAtLeast(0.015f))
                                .fillMaxHeight()
                                .background(Color(0xFFDFF0E6))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AxisKey(WalletMint, "Có mốc giờ", fmt(todayStats.timedSeconds))
                    AxisKey(Color(0xFFC5CFC9), "Đã qua, chưa ghi", fmt(todayStats.untrackedPastSeconds))
                    AxisKey(Color(0xFF96CEB0), "Còn lại", fmt(todayStats.remainingDaySeconds))
                }
            }
        }

        // Ngân sách ưu tiên (tối đa 3).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                text = "Ngân sách",
                actionText = if (priorityBudgets.isEmpty()) null else "Xem tất cả",
                onAction = { onNavigateToTab(AppTab.INSIGHTS) }
            )
            if (priorityBudgets.isEmpty()) {
                SoftCard(padding = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .walletClickable(onClickLabel = "Đặt ngân sách đầu tiên") { onOpenBudgetEditor(null) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconDisc(WalletGlyph.BARS)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Đặt mục tiêu đầu tiên", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                            Text(text = "Bạn muốn dành thời gian cho điều gì?", fontSize = 12.5.sp, color = WalletMuted)
                        }
                        GlyphIcon(WalletGlyph.CHEVRON, color = WalletMuted, size = 20.dp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    priorityBudgets.take(3).forEach { progress ->
                        TimeBudgetItemCard(
                            progress = progress,
                            compactMode = true,
                            onEditClick = { cat -> onOpenBudgetEditor(cat) }
                        )
                    }
                }
            }
        }

        // Nhìn lại cuối ngày: một dòng, không đoạn mô tả.
        SoftCard(padding = 4.dp, tint = WalletMintTint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .walletClickable(onClickLabel = "Mở nhìn lại cuối ngày", onClick = onOpenDailyReview)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconDisc(WalletGlyph.BOOK, solid = true)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Nhìn lại ngày hôm nay", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                    Text(
                        text = if (hasSavedTodayReview) "Đã lưu · chạm để xem hoặc sửa" else "Ghi lại điều đáng nhớ",
                        fontSize = 12.5.sp,
                        color = WalletMuted
                    )
                }
                GlyphIcon(WalletGlyph.CHEVRON, color = WalletMuted, size = 20.dp)
            }
        }

        // Hoạt động gần đây.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                text = "Hoạt động gần đây",
                actionText = "Xem tất cả",
                onAction = { onNavigateToTab(AppTab.HISTORY) }
            )
            SoftCard(padding = 14.dp) {
                if (recentRecords.isEmpty()) {
                    EmptyState(
                        glyph = WalletGlyph.LIST,
                        title = "Chưa có hoạt động nào",
                        hint = "Hoạt động bạn ghi sẽ hiện ở đây."
                    )
                } else {
                    val nowMs = System.currentTimeMillis()
                    val shown = recentRecords.take(3)
                    shown.forEachIndexed { idx, record ->
                        ActivityRecordRow(
                            record = record,
                            nowEpochMs = nowMs,
                            todayDateLocal = todayDateLocal,
                            showEditAndDelete = false
                        )
                        if (idx < shown.lastIndex) HorizontalDivider(color = Color(0xFFEFF2EE))
                    }
                }
            }
        }

        val pendingActionRecords = remember(recentRecords) {
            val now = System.currentTimeMillis()
            recentRecords.filter { !it.isCancelled && (it.isNeedsReconciliation || it.requiresCompletionConfirmationAt(now)) }
        }
        if (pendingActionRecords.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(
                    text = "Cần xác nhận & đối soát",
                    actionText = "Xem tất cả",
                    onAction = { onNavigateToTab(AppTab.HISTORY) }
                )
                SoftCard(padding = 14.dp) {
                    val now = System.currentTimeMillis()
                    val shownPending = pendingActionRecords.take(3)
                    shownPending.forEachIndexed { idx, record ->
                        ActivityRecordRow(
                            record = record,
                            nowEpochMs = now,
                            todayDateLocal = todayDateLocal,
                            showEditAndDelete = true,
                            onConfirmActualClick = onConfirmOrResolveRecord,
                            onEditClick = null,
                            onDeleteClick = null
                        )
                        if (idx < shownPending.lastIndex) HorizontalDivider(color = Color(0xFFEFF2EE))
                    }
                }
            }
        }

        if (pendingManualDraft != null) {
            var confirmCleanOverviewDraft by remember { mutableStateOf(false) }
            val catLabel = pendingManualDraft.categoryKey?.let { TimeCategory.fromKeyOrNull(it)?.displayName } ?: "Chưa chọn nhóm"
            val summaryTitle = pendingManualDraft.note.ifBlank { catLabel }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Bản nháp đang nhập dở")
                SoftCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "$summaryTitle · ${pendingManualDraft.dateLocal}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk
                        )
                        Text(
                            text = "Chưa ghi vào sổ cái và chưa tính vào thống kê.",
                            fontSize = 12.sp,
                            color = WalletMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { confirmCleanOverviewDraft = true },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text("Clean · Xóa nháp", color = Color(0xFFB3261E), fontSize = 12.5.sp)
                            }
                            Button(
                                onClick = onResumeManualDraft,
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text("Tiếp tục nhập", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
            if (confirmCleanOverviewDraft) {
                AlertDialog(
                    onDismissRequest = { confirmCleanOverviewDraft = false },
                    containerColor = Color.White,
                    title = { Text("Xóa bản nháp đang nhập dở?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
                    text = {
                        Text(
                            "Nội dung nháp \"$summaryTitle\" sẽ bị xóa sạch để bắt đầu ghi nhận mới.",
                            fontSize = 12.5.sp,
                            color = WalletMuted
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                confirmCleanOverviewDraft = false
                                onCleanManualDraft()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                        ) { Text("Xóa nháp", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCleanOverviewDraft = false }) {
                            Text("Giữ lại", color = WalletMuted)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AxisKey(dot: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(text = label, fontSize = 12.5.sp, color = WalletMuted)
        Text(text = value, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
    }
}

@Composable
private fun IconDisc(glyph: WalletGlyph, solid: Boolean = false) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (solid) WalletMintDark else WalletMintTint),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, color = if (solid) Color.White else WalletMint, size = 22.dp)
    }
}

@Composable
private fun QuickActionTile(
    glyph: WalletGlyph,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .describe(description)
            .walletClickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconDisc(glyph)
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = WalletInk,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}
