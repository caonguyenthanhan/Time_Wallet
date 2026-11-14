package com.timewallet.mvp.ui.screens

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.BorderStroke
import com.timewallet.mvp.domain.usecase.WallyGreeting
import com.timewallet.mvp.domain.usecase.DonutSlice
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DayLedgerStats
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintBright
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintMid
import com.timewallet.mvp.ui.theme.WalletMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    onNavigateToTab: (AppTab) -> Unit
) {
    val formattedTodayHeader = remember(todayDateLocal) {
        try {
            val d = LocalDate.parse(todayDateLocal)
            val fmt = DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale("vi", "VN"))
            d.format(fmt).uppercase(Locale("vi", "VN"))
        } catch (_: Exception) {
            "HÔM NAY"
        }
    }

    val remainingFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(todayStats.remainingDaySeconds)
    val elapsedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(todayStats.elapsedDaySeconds)
    val recordedTimedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(todayStats.timedSeconds)
    val untrackedPastFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(todayStats.untrackedPastSeconds)
    val untimedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(todayStats.untimedSeconds)
    val plannedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(todayStats.plannedSeconds)
    val totalDayHoursLabel = "${(todayStats.calendarDaySeconds / 3600L)}h"

    val summary = remember(todayStats) { DashboardSummary.from(todayStats) }
    val greetingHour = remember(todayDateLocal) { java.time.LocalTime.now().hour }
    val recordedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(summary.recordedSeconds)
    val unrecordedFormatted = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(summary.unrecordedSeconds)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Wally: lời chào cục bộ một câu (không gọi API).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_wally),
                contentDescription = "Wally, trợ lý thời gian",
                modifier = Modifier.size(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WallyGreeting.dashboardLine(greetingHour),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk,
                    lineHeight = 17.sp
                )
                Text(text = formattedTodayHeader, fontSize = 10.sp, color = WalletMuted)
            }
        }

        // Hero: ví thời gian của ngày. Số lớn = đã ghi nhận; "chưa ghi nhận" là phần còn lại của 24h theo quy ước, không phải giờ rảnh.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(WalletMintDark, WalletMintMid, WalletMintBright)
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "VÍ THỜI GIAN HÔM NAY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        color = Color(0xFFD3EDE2)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)) {
                                append(recordedFormatted)
                            }
                            withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))) {
                                append(" đã ghi nhận / $totalDayHoursLabel")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .semantics {
                                contentDescription = "Đã ghi nhận $recordedFormatted trên $totalDayHoursLabel của ngày"
                            }
                    ) {
                        if (summary.progressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(summary.progressFraction.coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = unrecordedFormatted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(text = "Chưa ghi nhận", fontSize = 10.5.sp, color = Color(0xFFD3EDE2))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(summary.untimedSeconds),
                                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                            )
                            Text(text = "Trong đó chưa gắn giờ", fontSize = 10.5.sp, color = Color(0xFFD3EDE2))
                        }
                    }
                    if (summary.plannedSeconds > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Dự kiến ${LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(summary.plannedSeconds)} (chưa tính vào đã ghi nhận)",
                            fontSize = 10.sp,
                            color = Color(0xFFD3EDE2)
                        )
                    }
                    if (summary.recordedExceedsDay) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tổng đã ghi nhận (có mốc giờ + chưa gắn giờ) vượt quá độ dài của ngày. Phần chưa gắn giờ có thể trùng với phần có mốc giờ; hãy kiểm tra lại các bản ghi.",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFE9B8)
                        )
                    }
                }
            }
        }

        // Thao tác nhanh: Đồng hồ / Thêm giờ / Hỏi Wally. (FAB là đường thêm giờ nhanh nhất.)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionTile("◴", "Đồng hồ", "Mở tab Đồng hồ", Modifier.weight(1f)) { onNavigateToTab(AppTab.TIMER) }
            QuickActionTile("＋", "Thêm giờ", "Nhập giờ thủ công", Modifier.weight(1f), onAddManual)
            QuickActionTile("✦", "Hỏi Wally", "Mở tab Wally", Modifier.weight(1f)) { onNavigateToTab(AppTab.CHAT) }
        }

        // Donut phân bổ theo danh mục (tỷ lệ trên tổng đã ghi nhận).
        SectionHeader(title = "Phân bổ theo danh mục", subtitleOrRightText = "Tỷ lệ trên tổng đã ghi nhận")
        WalletSurfaceCard {
            if (summary.isEmpty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Chưa có giờ nào được ghi hôm nay.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                    Text(
                        text = "Bật đồng hồ, bấm ＋ để thêm giờ, hoặc hỏi Wally.",
                        fontSize = 10.5.sp,
                        color = WalletMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val donutDescription = summary.slices.joinToString(", ") { "${it.category.displayName} ${it.percent}%" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DonutChart(
                        slices = summary.slices,
                        centerLabel = recordedFormatted,
                        modifier = Modifier
                            .size(112.dp)
                            .semantics { contentDescription = "Biểu đồ phân bổ theo danh mục: $donutDescription" }
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        summary.slices.forEach { slice ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(slice.category.colorHex)))
                                    Text(
                                        text = "${slice.category.emoji} ${slice.category.displayName}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WalletInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(slice.seconds)} · ${slice.percent}%",
                                    fontSize = 10.5.sp,
                                    color = WalletMuted,
                                    modifier = Modifier.padding(start = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 24-hour Allocation Card (Tách 3 miền: Đã ghi nhận | Chưa ghi nhận quá khứ | Quỹ còn lại)
        SectionHeader(
            title = "Trục 24 giờ hôm nay",
            subtitleOrRightText = "Quá khứ đã qua vs Quỹ còn lại"
        )

        WalletSurfaceCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                    val untrackedPastWeight =
                        (todayStats.untrackedPastSeconds.toFloat() / totalDaySecs).coerceAtLeast(0.015f)
                    Box(
                        modifier = Modifier
                            .weight(untrackedPastWeight)
                            .fillMaxHeight()
                            .background(Color(0xFFC5CFC9))
                    )
                }
                if (todayStats.remainingDaySeconds > 0L) {
                    val remainingWeight =
                        (todayStats.remainingDaySeconds.toFloat() / totalDaySecs).coerceAtLeast(0.015f)
                    Box(
                        modifier = Modifier
                            .weight(remainingWeight)
                            .fillMaxHeight()
                            .background(Color(0xFFDFF0E6))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val legendItems = buildList {
                todayStats.categoryBreakdown.forEach { stat ->
                    add(
                        Triple(
                            Color(stat.category.colorHex),
                            stat.category.displayName,
                            LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(stat.durationSeconds)
                        )
                    )
                }
                add(
                    Triple(
                        Color(0xFFC5CFC9),
                        "Chưa ghi nhận (quá khứ)",
                        untrackedPastFormatted
                    )
                )
                add(
                    Triple(
                        Color(0xFF96CEB0),
                        "Quỹ còn lại hôm nay",
                        remainingFormatted
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                legendItems.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { (dotColor, label, valueText) ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF5D6D66),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = valueText,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletInk
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFEAF0E9))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Chưa ghi nhận (quá khứ) là khoảng đã trôi qua chưa có bản ghi; tách biệt với Quỹ thời gian còn lại trong ngày.",
                fontSize = 10.5.sp,
                color = Color(0xFF738479),
                lineHeight = 15.sp
            )
        }

        // Priority Time Budgets on Dashboard (Max 3, Editable)
        val budgetsArePriority = priorityBudgets.any { it.budget.isPriority }
        SectionHeader(
            title = if (budgetsArePriority) "Ngân sách ưu tiên (${priorityBudgets.take(3).size}/3)" else "Ngân sách (${priorityBudgets.take(3).size})",
            actionText = if (priorityBudgets.isEmpty()) "Đặt ngân sách ✎" else "Xem tất cả →",
            onActionClick = {
                if (priorityBudgets.isEmpty()) onOpenBudgetEditor(null) else onNavigateToTab(AppTab.INSIGHTS)
            }
        )

        WalletSurfaceCard {
            if (priorityBudgets.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Button }
                        .heightIn(min = 48.dp)
                        .clickable { onOpenBudgetEditor(null) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Chưa có ngân sách thời gian nào được đặt.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Hệ thống không tự đặt mục tiêu thay bạn. Bấm vào đây để tự đặt số giờ/ngày hoặc tuần cho danh mục ưu tiên.",
                        fontSize = 10.5.sp,
                        color = WalletMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFFE6F4EC))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ Đặt ngân sách ưu tiên",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMint
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // Daily Review Entry Card on Dashboard (Refinement 03)
        SectionHeader(
            title = "Phản tư cuối ngày (Daily Review)",
            actionText = if (hasSavedTodayReview) "Xem / Sửa lại →" else "Mở Daily Review →",
            onActionClick = onOpenDailyReview
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { role = Role.Button }
                .heightIn(min = 48.dp)
                .clickable(onClick = onOpenDailyReview),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F9F5)),
            border = BorderStroke(1.dp, Color(0xFFCDE5D7)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(WalletMintDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✦",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Nhìn lại trong ngày & Phản tư Feynman",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk
                        )
                        if (hasSavedTodayReview) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFFDCF2E5))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "Đã lưu",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMint
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Xem theo ngày từ Ledger thật, ghi kết quả từng việc, trả lời tối đa 2 câu hỏi phản tư và giải thích bài học Feynman.",
                        fontSize = 10.sp,
                        color = WalletMuted,
                        lineHeight = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(WalletMint)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Mở →",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Recent Activities
        SectionHeader(
            title = "Hoạt động gần đây",
            actionText = "Xem tất cả →",
            onActionClick = { onNavigateToTab(AppTab.HISTORY) }
        )

        WalletSurfaceCard(paddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            if (recentRecords.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Chưa có hoạt động nào. Bật đồng hồ, bấm ＋ để thêm giờ, hoặc hỏi Wally.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF7D9086),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            } else {
                val nowMs = System.currentTimeMillis()
                recentRecords.take(3).forEachIndexed { idx, record ->
                    ActivityRecordRow(
                        record = record,
                        nowEpochMs = nowMs,
                        todayDateLocal = todayDateLocal,
                        showEditAndDelete = false
                    )
                    if (idx < minOf(recentRecords.size, 3) - 1) {
                        HorizontalDivider(color = Color(0xFFEFF2EE))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDFEAE3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WalletMint)
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = WalletInk,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Donut vẽ bằng Canvas; không có dữ liệu thì không vẽ phần nào (không chart giả). */
@Composable
private fun DonutChart(slices: List<DonutSlice>, centerLabel: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 16.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = Color(0xFFEDF0ED), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = strokePx)
            )
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.fraction
                drawArc(
                    color = Color(slice.category.colorHex), startAngle = start,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = strokePx)
                )
                start += sweep
            }
        }
        Text(
            text = centerLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WalletInk,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
