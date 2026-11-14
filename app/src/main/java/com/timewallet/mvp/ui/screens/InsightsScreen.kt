package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.WeeklyInsightsSummary
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMuted
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InsightsScreen(
    weeklyInsights: WeeklyInsightsSummary,
    onOpenBudgetEditor: (TimeCategory?) -> Unit = {},
    onDeleteBudget: (String) -> Unit = {}
) {
    val weekTimedFormatted =
        LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(weeklyInsights.totalTimedSeconds)
    val weekUntimedFormatted =
        LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(weeklyInsights.totalUntimedSeconds)

    val maxDaySeconds = remember(weeklyInsights.days) {
        weeklyInsights.days.maxOfOrNull { it.timedSeconds }?.coerceAtLeast(60L) ?: 60L
    }

    val currentWeekRangeText = remember(weeklyInsights.currentWeekStartLocal, weeklyInsights.currentWeekEndLocal) {
        if (weeklyInsights.currentWeekStartLocal.isNotBlank() && weeklyInsights.currentWeekEndLocal.isNotBlank()) {
            "Tuần hiện tại: ${weeklyInsights.currentWeekStartLocal} → ${weeklyInsights.currentWeekEndLocal} (${ZoneId.systemDefault().id})"
        } else {
            "Theo múi giờ thiết bị (${ZoneId.systemDefault().id})"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PageIntroHeader(
            eyebrow = "PHÂN TÍCH & NGÂN SÁCH",
            title = "Nhìn rõ thời gian.",
            subtitle = "Tách bạch hoàn toàn thời gian có mốc giờ và thời lượng chưa gắn giờ; đối chiếu với ngân sách bạn tự đặt."
        )

        // Two separate weekly summary metrics (NEVER added together into a single combined total)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricSummaryCard(
                label = "7 ngày · Có mốc giờ",
                valueText = weekTimedFormatted,
                subNote = "Trên trục 24h (đã khử trùng)",
                modifier = Modifier.weight(1f)
            )
            MetricSummaryCard(
                label = "7 ngày · Chưa gắn giờ",
                valueText = weekUntimedFormatted,
                subNote = "Tách riêng (không cộng gộp)",
                modifier = Modifier.weight(1f)
            )
        }

        // TIME BUDGET SECTION (Yêu cầu 4 & 5)
        SectionHeader(
            title = "Ngân sách thời gian (Time Budget)",
            actionText = "+ Đặt ngân sách",
            onActionClick = { onOpenBudgetEditor(null) }
        )

        WalletSurfaceCard {
            Text(
                text = currentWeekRangeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WalletMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (weeklyInsights.budgetProgressList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Button }
                        .heightIn(min = 48.dp)
                        .clickable { onOpenBudgetEditor(null) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bạn chưa đặt ngân sách cho danh mục nào.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chọn một danh mục (Công việc, Học tập, Sức khỏe...) và đặt mục tiêu số giờ/ngày hoặc tuần để theo dõi mức đã ghi nhận và phần chênh lệch.",
                        fontSize = 10.5.sp,
                        color = WalletMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFFE6F4EC))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ Tạo ngân sách đầu tiên",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMint
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    weeklyInsights.budgetProgressList.forEach { item ->
                        TimeBudgetItemCard(
                            progress = item,
                            compactMode = false,
                            onEditClick = { cat -> onOpenBudgetEditor(cat) },
                            onDeleteClick = { cat -> onDeleteBudget(cat.key) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFEAF0E9))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mức đã ghi nhận gồm thời gian thực tế có mốc giờ và phần chưa gắn giờ. Các hoạt động Dự kiến (PLANNED) không được tính.",
                fontSize = 10.sp,
                color = Color(0xFF7D8E83),
                lineHeight = 14.5.sp
            )
        }

        // 7-day Timed Bar Chart
        SectionHeader(
            title = "7 ngày gần nhất",
            subtitleOrRightText = "Chỉ gồm giờ có mốc xác định"
        )

        WalletSurfaceCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyInsights.days.forEachIndexed { idx, day ->
                    val isToday = idx == weeklyInsights.days.lastIndex
                    val fillFraction =
                        (day.timedSeconds.toFloat() / maxDaySeconds.toFloat()).coerceIn(0f, 1f)
                    val shortDayLabel = remember(day.dateLocal) {
                        try {
                            val d = LocalDate.parse(day.dateLocal)
                            d.format(DateTimeFormatter.ofPattern("EEE", Locale("vi", "VN")))
                        } catch (_: Exception) {
                            day.dateLocal.takeLast(2)
                        }
                    }
                    val topValueLabel = if (day.timedSeconds > 0L) {
                        LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(day.timedSeconds)
                    } else {
                        "–"
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = topValueLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F6A5C),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.72f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEDF2ED))
                                .then(
                                    if (isToday) {
                                        Modifier.border(
                                            2.dp,
                                            Color(0xFF74BC91),
                                            RoundedCornerShape(8.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (fillFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fillFraction.coerceAtLeast(0.06f))
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color(0xFF80BBA0), Color(0xFF20765D))
                                            )
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = shortDayLabel,
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) WalletInk else Color(0xFF5F7168),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Biểu đồ chỉ hiển thị thời gian thực tế có mốc giờ xác định. Thời lượng chưa gắn giờ được tách riêng bên dưới để tránh cộng trùng.",
                fontSize = 10.5.sp,
                color = Color(0xFF7D8E83),
                lineHeight = 15.sp
            )
        }

        // Timed Category Distribution in 7 Days
        SectionHeader(
            title = "Danh mục có mốc giờ (7 ngày)",
            subtitleOrRightText = "Đã khử trùng lặp"
        )

        WalletSurfaceCard {
            if (weeklyInsights.categoryTotals.isEmpty()) {
                Text(
                    text = "Chưa có dữ liệu thực tế có mốc giờ trong 7 ngày qua.",
                    fontSize = 11.5.sp,
                    color = Color(0xFF7D9086),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    weeklyInsights.categoryTotals.forEach { stat ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${stat.category.emoji} ${stat.category.displayName}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletInk
                                )
                                Text(
                                    text = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(stat.durationSeconds),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WalletMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEDF2EE))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(stat.fractionOfDayOrTotal.coerceIn(0.03f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(stat.category.colorHex))
                                )
                            }
                        }
                    }
                }
            }

            if (weeklyInsights.untimedCategoryTotals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFEAF0E9))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Thời lượng chưa gắn giờ theo danh mục (tách riêng, không cộng gộp):",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    weeklyInsights.untimedCategoryTotals.forEach { uStat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uStat.category.emoji} ${uStat.category.displayName} (chưa gắn giờ)",
                                fontSize = 11.sp,
                                color = Color(0xFF5D6E64)
                            )
                            Text(
                                text = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(uStat.durationSeconds),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk
                            )
                        }
                    }
                }
            }
        }

        // Personal Reflection Card based on Real Budgets (Yêu cầu 7)
        SectionHeader(
            title = "AI Phản tư từ ngân sách thật",
            subtitleOrRightText = "Không phán xét · Không tự đổi mục tiêu"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFEEF7EF))
                .border(1.dp, Color(0xFFDCE8DE), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text(
                text = weeklyInsights.reflectionPrompt,
                fontSize = 11.5.sp,
                color = Color(0xFF2E6147),
                lineHeight = 17.5.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Không chấm điểm đạo đức cho ngủ, nghỉ ngơi hay giải trí. Mục tiêu hoàn toàn do bạn tự quyết định.",
            fontSize = 10.sp,
            color = Color(0xFF9BA69E),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetricSummaryCard(
    label: String,
    valueText: String,
    subNote: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE8ECE6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7D72)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = valueText,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subNote,
                fontSize = 9.5.sp,
                color = Color(0xFF8B9B91)
            )
        }
    }
}
