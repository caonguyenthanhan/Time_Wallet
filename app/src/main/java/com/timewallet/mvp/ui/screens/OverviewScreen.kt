package com.timewallet.mvp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletClarificationBg
import com.timewallet.mvp.ui.theme.WalletDangerSolid
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintBright
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintMid
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletUntrackedBar
import com.timewallet.mvp.ui.theme.WalletWarningText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Compact24hWalletCard(
    todayDateLocal: String,
    todayStats: DayLedgerStats,
    inboxTaskCount: Int = 0,
    onOpenDailyReview: () -> Unit = {},
    onOpenTasksInbox: () -> Unit = {}
) {
    val summary = remember(todayStats) { DashboardSummary.from(todayStats) }
    val fmt = LedgerAnalyticsCalculator::formatHoursMinutesFromSeconds
    val recordedFormatted = fmt(summary.recordedSeconds)
    val totalDayHoursLabel = "${todayStats.calendarDaySeconds / 3600L}h"
    val totalDaySecs = todayStats.calendarDaySeconds.coerceAtLeast(1L).toFloat()
    var expanded by rememberSaveable(todayDateLocal) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WalletMintTint)
            .border(1.dp, WalletStroke, RoundedCornerShape(14.dp))
            .walletClickable(
                onClickLabel = if (expanded) "Thu gọn ví 24 giờ" else "Mở rộng chi tiết ví 24 giờ"
            ) { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = recordedFormatted,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletMintAccent,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "/ $totalDayHoursLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletMuted,
                    modifier = Modifier.padding(bottom = 0.5.dp),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WalletUntrackedBar)
                    .describe("Ví 24h hôm nay ($todayDateLocal): đã ghi nhận $recordedFormatted"),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (todayStats.timedSeconds > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((todayStats.timedSeconds.toFloat() / totalDaySecs).coerceIn(0.03f, 1f))
                            .fillMaxHeight()
                            .background(WalletMint)
                    )
                }
                if (todayStats.untrackedPastSeconds > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((todayStats.untrackedPastSeconds.toFloat() / totalDaySecs).coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(WalletMuted.copy(alpha = 0.38f))
                    )
                }
                if (todayStats.remainingDaySeconds > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((todayStats.remainingDaySeconds.toFloat() / totalDaySecs).coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(WalletSoftNeutral)
                    )
                }
            }
            if (inboxTaskCount > 0) {
                WalletPill(
                    onClickLabel = "Mở hộp việc Inbox",
                    containerColor = WalletMintSoft,
                    borderColor = WalletStroke,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    onClick = onOpenTasksInbox
                ) {
                    Text(
                        text = "Inbox ($inboxTaskCount)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMintAccent,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Text(
                text = if (expanded) "▴" else "▾",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WalletMintAccent
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Có giờ ${fmt(todayStats.timedSeconds)}", fontSize = 12.sp, color = WalletInk, maxLines = 1, softWrap = false)
                    Text("Chưa ghi ${fmt(todayStats.untrackedPastSeconds)}", fontSize = 12.sp, color = WalletMuted, maxLines = 1, softWrap = false)
                    Text("Còn lại ${fmt(todayStats.remainingDaySeconds)}", fontSize = 12.sp, color = WalletMintAccent, maxLines = 1, softWrap = false)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    WalletPill(
                        onClickLabel = "Mở phản tư cuối ngày",
                        containerColor = WalletMintDark,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        onClick = onOpenDailyReview
                    ) {
                        Text(
                            text = "Phản tư cuối ngày",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (summary.recordedExceedsDay) {
                    Text(
                        text = "Tổng đã ghi nhận vượt quá độ dài của ngày; các hoạt động có giờ và chưa gắn giờ có thể trùng nhau.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletWarningText
                    )
                }
            }
        }
    }
}

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
    onOpenProfileSettings: () -> Unit = {},
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Lời chào gọn 1 dòng + mở nhanh Hồ sơ/Giới thiệu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WallyMascotAvatar(
                size = 40.dp,
                contentDescription = "Mở hồ sơ và giới thiệu app",
                modifier = Modifier.walletClickable(onClickLabel = "Mở hồ sơ và giới thiệu app", onClick = onOpenProfileSettings)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WallyGreeting.dashboardLine(greetingHour),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    maxLines = 1
                )
                Text(text = dateHeader, fontSize = 12.sp, color = WalletMuted)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White)
                    .border(1.dp, com.timewallet.mvp.ui.theme.WalletStroke, RoundedCornerShape(100.dp))
                    .walletClickable(onClickLabel = "Thêm giờ thủ công", onClick = onAddManual)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Ghi giờ",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletMintDark,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Hero nén gọn: tổng đã ghi nhận + trục 24h tích hợp ngay trong thẻ (không chiếm 2 thẻ tách biệt).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(WalletMintDark, WalletMintMid, WalletMintBright)))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "VÍ THỜI GIAN HÔM NAY",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFFCDEBDD)
                    )
                    Text(
                        text = "Quỹ $totalDayHoursLabel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCDEBDD)
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = recordedFormatted,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "đã ghi nhận",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.86f),
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                // Thanh trục 24h tích hợp ngay trong Hero (chỉ thời gian có mốc giờ + chưa ghi trong quá khứ + phần còn lại)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .describe("Đã ghi nhận $recordedFormatted trên $totalDayHoursLabel của ngày"),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val totalDaySecs = todayStats.calendarDaySeconds.coerceAtLeast(1L).toFloat()
                    if (todayStats.timedSeconds > 0L) {
                        Box(
                            modifier = Modifier
                                .weight((todayStats.timedSeconds.toFloat() / totalDaySecs).coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                    if (todayStats.untrackedPastSeconds > 0L) {
                        Box(
                            modifier = Modifier
                                .weight((todayStats.untrackedPastSeconds.toFloat() / totalDaySecs).coerceIn(0.01f, 1f))
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.35f))
                        )
                    }
                    if (todayStats.remainingDaySeconds > 0L) {
                        Box(
                            modifier = Modifier
                                .weight((todayStats.remainingDaySeconds.toFloat() / totalDaySecs).coerceIn(0.01f, 1f))
                                .fillMaxHeight()
                                .background(Color.Transparent)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Có giờ ${fmt(todayStats.timedSeconds)}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.92f))
                    Text("Chưa ghi ${fmt(todayStats.untrackedPastSeconds)}", fontSize = 11.sp, color = Color(0xFFCDEBDD))
                    Text("Còn lại ${fmt(todayStats.remainingDaySeconds)}", fontSize = 11.sp, color = Color(0xFFCDEBDD))
                }
                if (summary.untimedSeconds > 0L || summary.plannedSeconds > 0L) {
                    val extras = buildList {
                        if (summary.untimedSeconds > 0L) add("Chưa gắn giờ ${fmt(summary.untimedSeconds)}")
                        if (summary.plannedSeconds > 0L) add("Dự kiến ${fmt(summary.plannedSeconds)}")
                    }.joinToString(" · ")
                    Text(text = extras, fontSize = 11.sp, color = Color(0xFFD9F3E6))
                }
                if (summary.recordedExceedsDay) {
                    Text(
                        text = "Tổng đã ghi nhận vượt quá độ dài của ngày; các hoạt động có giờ và chưa gắn giờ có thể trùng nhau.",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFE9B8)
                    )
                }
            }
        }

        val pendingActionRecords = remember(recentRecords) {
            val now = System.currentTimeMillis()
            recentRecords.filter { !it.isCancelled && (it.isNeedsReconciliation || it.requiresCompletionConfirmationAt(now)) }
        }
        if (pendingActionRecords.isNotEmpty()) {
            SoftCard(padding = 12.dp, tint = WalletClarificationBg) {
                val now = System.currentTimeMillis()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cần xác nhận (${pendingActionRecords.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk
                    )
                    Text(
                        text = "Xem ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMintAccent,
                        modifier = Modifier.walletClickable(onClickLabel = "Xem Lịch trình") { onNavigateToTab(AppTab.SCHEDULE) }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                pendingActionRecords.take(2).forEachIndexed { idx, record ->
                    ActivityRecordRow(
                        record = record,
                        nowEpochMs = now,
                        todayDateLocal = todayDateLocal,
                        showEditAndDelete = true,
                        onConfirmActualClick = onConfirmOrResolveRecord,
                        onEditClick = null,
                        onDeleteClick = null
                    )
                    if (idx < pendingActionRecords.take(2).lastIndex) HorizontalDivider(color = WalletDivider)
                }
            }
        }

        if (pendingManualDraft != null) {
            var confirmCleanOverviewDraft by remember { mutableStateOf(false) }
            val catLabel = pendingManualDraft.categoryKey?.let { TimeCategory.fromKeyOrNull(it)?.displayName } ?: "Chưa chọn nhóm"
            val summaryTitle = pendingManualDraft.note.ifBlank { catLabel }
            SoftCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Bản nháp đang nhập dở: $summaryTitle · ${pendingManualDraft.dateLocal}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                    Text(
                        text = "Chưa ghi vào sổ cái và chưa tính vào thống kê.",
                        fontSize = 11.5.sp,
                        color = WalletMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { confirmCleanOverviewDraft = true },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Text("Clean · Xóa nháp", color = WalletDangerText, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onResumeManualDraft,
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Text("Tiếp tục nhập", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (confirmCleanOverviewDraft) {
                AlertDialog(
                    onDismissRequest = { confirmCleanOverviewDraft = false },
                    containerColor = WalletCardBg,
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
                            colors = ButtonDefaults.buttonColors(containerColor = WalletDangerSolid, contentColor = Color.White)
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

        // Donut phân bổ theo danh mục (chặt chẽ, không khoảng trống thừa).
        SoftCard(padding = 14.dp) {
            SectionTitle(
                text = "Phân bổ theo danh mục",
                actionText = "Thống kê ›",
                onAction = { onNavigateToTab(AppTab.MORE) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (summary.isEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DonutChart(
                        slices = emptyList(),
                        centerTop = "0h",
                        modifier = Modifier.size(96.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Chưa có giờ nào được ghi hôm nay.",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk
                        )
                        Text(text = "Bấm '+ Ghi giờ', bật Đồng hồ hoặc hỏi Wally.", fontSize = 12.sp, color = WalletMuted)
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

        // Hoạt động gần đây (gọn 3 mục + mở Lịch trình).
        SoftCard(padding = 12.dp) {
            SectionTitle(
                text = "Hoạt động gần đây",
                actionText = "Xem Lịch trình →",
                onAction = { onNavigateToTab(AppTab.SCHEDULE) }
            )
            Spacer(modifier = Modifier.height(6.dp))
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
}
