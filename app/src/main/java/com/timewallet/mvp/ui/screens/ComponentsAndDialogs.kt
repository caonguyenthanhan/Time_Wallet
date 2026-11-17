package com.timewallet.mvp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.data.diagnostics.CrashRecorder
import com.timewallet.mvp.data.diagnostics.CrashReportInfo
import com.timewallet.mvp.R
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.BackupRestoreMode
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ApiKeyHygiene
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.ui.AppTab
import com.timewallet.mvp.ui.ImportBackupUiState
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerBg
import com.timewallet.mvp.ui.theme.WalletDangerBorder
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMicroBg
import com.timewallet.mvp.ui.theme.WalletMicroText
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimeWalletTopBar(
    isTimerRunning: Boolean,
    timerSeconds: Long,
    onTimerBadgeClick: () -> Unit,
    onDataClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F7F3))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // v1.0.11: header gọn một dòng (bỏ dòng khẩu hiệu).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Icon-only vector (không chứa chữ nhỏ bên trong logo)
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_wallet),
                contentDescription = "Biểu tượng Time Wallet",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "time wallet",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk
                )
                Text(
                    text = ".",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletMint
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isTimerRunning) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WalletMintSoft)
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = "Mở tab Đồng hồ đang chạy") { onTimerBadgeClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val m = (timerSeconds / 60).toString().padStart(2, '0')
                    val s = (timerSeconds % 60).toString().padStart(2, '0')
                    Text(
                        text = "● $m:$s",
                        color = WalletMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Menu phụ: dữ liệu & sao lưu, cấu hình AI, thông tin (không còn khối quản trị dữ liệu trong Nhật ký).
            Box {
                TopBarCircleButton(glyph = WalletGlyph.MORE, contentDescription = "Menu", onClick = { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Dữ liệu & sao lưu") },
                        onClick = { menuOpen = false; onDataClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Cấu hình AI") },
                        onClick = { menuOpen = false; onAiSettingsClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Thông tin & nhật ký lỗi") },
                        onClick = { menuOpen = false; onInfoClick() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarCircleButton(
    glyph: WalletGlyph,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, WalletStroke, RoundedCornerShape(12.dp))
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph = glyph, color = WalletInk, size = 20.dp)
    }
}

@Composable
fun TimeWalletBottomBar(
    currentTab: AppTab,
    isTimerRunning: Boolean,
    onSelectTab: (AppTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.dp, WalletStroke, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEach { tab ->
            val selected = tab == currentTab
            val tint = if (selected) WalletMint else Color(0xFF7F9087)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .semantics {
                        contentDescription = "Tab ${tab.label}"
                        role = Role.Tab
                        this.selected = selected
                    }
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Viên thuốc nền cho tab đang chọn; biểu tượng cùng kiểu nét, cùng kích thước.
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (selected) WalletMintSoft else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(glyph = tab.glyph(), color = tint, size = 22.dp, filled = selected)
                    if (tab == AppTab.TIMER && isTimerRunning) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 3.dp, end = 12.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(WalletDangerText)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    // trên màn hẹp (<360dp) nhãn tab giữ đúng 11dp để "Tổng quan" không bị cắt dù cỡ chữ hệ thống lớn
                    fontSize = cappedSp(11f, if (LocalConfiguration.current.screenWidthDp < 360) 1.0f else 1.1f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) tint else Color(0xFF5F7168),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitleOrRightText: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 13.dp, bottom = 7.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = WalletInk,
            // v1.0.10: tiêu đề nhường chỗ (xuống dòng) thay vì bị đo trước rồi ép nút/ghi chú bên phải thành cột 1 chữ.
            modifier = if ((actionText != null && onActionClick != null) || subtitleOrRightText != null) {
                Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            } else {
                Modifier
            }
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = WalletMint,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { role = Role.Button }
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        } else if (subtitleOrRightText != null) {
            Text(
                text = subtitleOrRightText,
                fontSize = 10.5.sp,
                color = WalletMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
    }
}

@Composable
fun PageIntroHeader(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp, start = 2.dp, end = 2.dp)
    ) {
        Text(
            text = eyebrow.uppercase(),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.3.sp,
            color = Color(0xFF668577)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WalletInk
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 11.5.sp,
            color = WalletMuted
        )
    }
}

@Composable
fun WalletSurfaceCard(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WalletCardBg),
        border = BorderStroke(1.dp, WalletStroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(paddingValues)) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityRecordRow(
    record: LedgerRecord,
    nowEpochMs: Long = System.currentTimeMillis(),
    todayDateLocal: String? = null,
    showEditAndDelete: Boolean = false,
    onConfirmActualClick: ((LedgerRecord) -> Unit)? = null,
    onEditClick: ((LedgerRecord) -> Unit)? = null,
    onDeleteClick: ((LedgerRecord) -> Unit)? = null
) {
    val categoryColor = Color(record.category.colorHex)
    val resolvedLayer = remember(record, nowEpochMs, todayDateLocal) {
        record.resolveLayerAt(nowEpochMs, todayDateLocal)
    }
    val confState = remember(record, nowEpochMs, todayDateLocal) {
        record.resolveConfirmationStateAt(nowEpochMs, todayDateLocal)
    }
    val overlapRes = remember(record) { record.overlapResolution }
    val timeDescription = remember(record, resolvedLayer, nowEpochMs) {
        formatRecordWhenLabelVi(record, resolvedLayer, nowEpochMs)
    }
    val durationText = remember(record, resolvedLayer, confState, nowEpochMs) {
        when {
            record.isCancelled -> "0p (đã hủy)"
            record.isNeedsReconciliation -> "Chưa tính · ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(record.durationSeconds)}"
            resolvedLayer == TimeLayer.ACTIVE && confState != ConfirmationState.PENDING_CONFIRMATION -> {
                val actualSecs = record.actualElapsedSecondsAt(nowEpochMs, todayDateLocal)
                "${LedgerAnalyticsCalculator.formatCompactDurationSeconds(actualSecs)} / ${
                    LedgerAnalyticsCalculator.formatCompactDurationSeconds(record.durationSeconds)
                }"
            }
            else -> LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(record.durationSeconds)
        }
    }

    val needsCompletionConfirm = remember(record, nowEpochMs) {
        record.requiresCompletionConfirmationAt(nowEpochMs)
    }
    val badgeLabel = when {
        confState == ConfirmationState.CANCELLED -> "Đã hủy"
        overlapRes == OverlapResolution.PENDING -> "Chờ đối soát"
        overlapRes == OverlapResolution.BREAK_REJECTED -> "Cần làm rõ"
        needsCompletionConfirm -> "Chờ xác nhận"
        overlapRes == OverlapResolution.PARALLEL_CONFIRMED -> "Song song"
        resolvedLayer == TimeLayer.ACTIVE -> resolvedLayer.badgeShortVi
        overlapRes == OverlapResolution.BREAK_CONFIRMED && resolvedLayer != TimeLayer.PLANNED -> "Khoảng nghỉ"
        confState == ConfirmationState.INTERRUPTED -> "Gián đoạn"
        confState == ConfirmationState.ADJUSTED -> "Đã điều chỉnh"
        else -> resolvedLayer.badgeShortVi
    }

    val (badgeBg, badgeTextColor) = when {
        confState == ConfirmationState.CANCELLED -> Color(0xFFF0F2F1) to Color(0xFF63736B)
        overlapRes == OverlapResolution.PENDING -> Color(0xFFFFF0D9) to Color(0xFF9C540B)
        overlapRes == OverlapResolution.BREAK_REJECTED -> Color(0xFFFDECEC) to Color(0xFFB42318)
        needsCompletionConfirm -> Color(0xFFFFECCC) to Color(0xFF9C540B)
        overlapRes == OverlapResolution.PARALLEL_CONFIRMED -> Color(0xFFE6EEFF) to Color(0xFF1D4ED8)
        confState == ConfirmationState.INTERRUPTED -> Color(0xFFFFF3E0) to Color(0xFFA15C07)
        resolvedLayer == TimeLayer.MEASURED -> Color(0xFFDDF3E8) to Color(0xFF175C47)
        resolvedLayer == TimeLayer.REPORTED_TIMED -> Color(0xFFE7F4EA) to Color(0xFF256B48)
        resolvedLayer == TimeLayer.REPORTED_UNTIMED -> Color(0xFFEDF1EE) to Color(0xFF5A6B62)
        resolvedLayer == TimeLayer.ACTIVE -> Color(0xFFE1F0FF) to Color(0xFF185FA5)
        else -> Color(0xFFFFF3DF) to Color(0xFF9C5D16)
    }

    val canConfirmToActual = showEditAndDelete &&
        onConfirmActualClick != null &&
        !record.isCancelled &&
        (record.isNeedsReconciliation || resolvedLayer == TimeLayer.PLANNED || resolvedLayer == TimeLayer.ACTIVE)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showEditAndDelete && onEditClick != null) {
                    Modifier
                        .semantics { role = Role.Button }
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Sửa bản ghi ${record.title}") { onEditClick(record) }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        CategoryBadge(category = record.category, size = 36.dp)

        Column(modifier = Modifier.weight(1f)) {
            // Dòng 1 (v1.0.10): TÊN chiếm toàn bộ chiều rộng còn lại (tối đa 2 dòng), thời lượng nằm bên phải.
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = record.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = durationText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (resolvedLayer == TimeLayer.PLANNED) WalletMuted else WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
            }
            // Dòng 2: nhãn trạng thái + mô tả thời gian bên trái; sửa/xóa (>= 48dp) bên phải.
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // v1.0.11: nhãn trạng thái và mốc giờ nằm cùng một dòng (tự xuống dòng khi hẹp); nhãn đã nêu tầng dữ liệu nên bỏ phần lặp.
                val rangeOnly = !record.isNeedsReconciliation &&
                    !record.isCancelled &&
                    (resolvedLayer == TimeLayer.MEASURED || resolvedLayer == TimeLayer.REPORTED_TIMED)
                val shownWhen = when {
                    rangeOnly -> timeDescription.substringBefore(" · ")
                    resolvedLayer == TimeLayer.REPORTED_UNTIMED && timeDescription.startsWith("Chưa gắn giờ · ") ->
                        timeDescription.removePrefix("Chưa gắn giờ · ")
                    else -> timeDescription
                }
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(badgeBg)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            maxLines = 2
                        )
                    }
                    Text(
                        text = shownWhen,
                        // khoảng giờ giữ cỡ cố định theo dp (không phình theo cỡ chữ hệ thống) để luôn vừa một dòng cạnh nút sửa/xóa
                        fontSize = if (rangeOnly) cappedSp(12f) else 12.sp,
                        color = WalletMuted,
                        // khoảng giờ là một khối không xuống dòng giữa chừng: nếu không vừa thì cả khối xuống dòng dưới nhãn
                        maxLines = if (rangeOnly) 1 else 3,
                        softWrap = !rangeOnly,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (showEditAndDelete) {
                    if (onEditClick != null) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .semantics { contentDescription = "Sửa ${record.title}"; role = Role.Button }
                                .clickable { onEditClick(record) },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(WalletGlyph.EDIT, color = WalletMint, size = 20.dp)
                        }
                    }
                    if (onDeleteClick != null) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .semantics { contentDescription = "Xóa ${record.title}"; role = Role.Button }
                                .clickable { onDeleteClick(record) },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(WalletGlyph.DELETE, color = WalletDangerText, size = 19.dp)
                        }
                    }
                }
            }
            // Dòng 3 (hiếm): nút xác nhận / điều chỉnh trạng thái hoặc đối soát chồng lấn.
            if (canConfirmToActual) {
                val actionBg = if (record.isNeedsReconciliation) Color(0xFFA24C18) else WalletMint
                val (actionGlyph, actionLabel) = when {
                    record.isNeedsReconciliation -> WalletGlyph.WARNING to "Đối soát chồng lấn"
                    needsCompletionConfirm -> WalletGlyph.CONFIRM to "Xác nhận kết quả"
                    else -> WalletGlyph.SETTINGS to "Xác nhận / Điều chỉnh"
                }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(actionBg)
                        .semantics { contentDescription = "$actionLabel ${record.title}"; role = Role.Button }
                        .clickable { onConfirmActualClick?.invoke(record) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        GlyphIcon(actionGlyph, color = Color.White, size = 13.dp)
                        Text(
                            text = actionLabel,
                            fontSize = cappedSp(12f),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

fun formatRecordWhenLabelVi(
    record: LedgerRecord,
    resolvedLayer: TimeLayer,
    nowEpochMs: Long
): String {
    val zone = try {
        ZoneId.of(record.timezone)
    } catch (_: Exception) {
        ZoneId.systemDefault()
    }
    val hmFormatter = DateTimeFormatter.ofPattern("HH:mm")

    if (record.startedAt != null && record.endedAt != null) {
        val startZoned = Instant.ofEpochMilli(record.startedAt).atZone(zone)
        val endZoned = Instant.ofEpochMilli(record.endedAt).atZone(zone)
        val crossDay = startZoned.toLocalDate() != endZoned.toLocalDate()
        val rangeStr = "${startZoned.format(hmFormatter)}–${endZoned.format(hmFormatter)}${if (crossDay) " (+1 ngày)" else ""}"

        val confState = record.resolveConfirmationStateAt(nowEpochMs)
        val overlapSuffix = when (record.overlapResolution) {
            OverlapResolution.PENDING -> " · Chờ đối soát (${record.overlapWithTitle ?: "trùng giờ"})"
            OverlapResolution.BREAK_REJECTED -> " · Cần làm rõ (${record.overlapWithTitle ?: "không phải nghỉ"})"
            OverlapResolution.PARALLEL_CONFIRMED -> " · Hoạt động song song"
            OverlapResolution.BREAK_CONFIRMED -> " · Khoảng nghỉ xác nhận"
            OverlapResolution.NONE -> ""
        }
        if (confState == ConfirmationState.CANCELLED) {
            return "$rangeStr · Đã hủy (không diễn ra)"
        }
        if (record.isNeedsReconciliation) {
            return "$rangeStr$overlapSuffix"
        }

        return when (resolvedLayer) {
            TimeLayer.MEASURED -> "$rangeStr · Đã đo đồng hồ$overlapSuffix"
            TimeLayer.REPORTED_TIMED -> {
                val stateLabel = when (confState) {
                    ConfirmationState.ADJUSTED -> "Đã điều chỉnh & xác nhận"
                    ConfirmationState.INTERRUPTED -> "Gián đoạn / kết thúc sớm"
                    else -> "Thực tế có giờ"
                }
                "$rangeStr · $stateLabel$overlapSuffix"
            }
            TimeLayer.ACTIVE -> {
                if (record.requiresCompletionConfirmationAt(nowEpochMs)) {
                    "$rangeStr · Đã tới giờ dự kiến, chờ bạn xác nhận$overlapSuffix"
                } else {
                    val nowHm = Instant.ofEpochMilli(nowEpochMs).atZone(zone).format(hmFormatter)
                    "$rangeStr · Đang diễn ra (tính tới $nowHm)$overlapSuffix"
                }
            }
            TimeLayer.PLANNED -> "$rangeStr · Dự kiến (chưa tính vào đã dùng)"
            TimeLayer.REPORTED_UNTIMED -> "$rangeStr · Chưa gắn giờ"
        }
    }

    return when (resolvedLayer) {
        TimeLayer.PLANNED -> "Chưa gắn giờ · Dự kiến"
        else -> "Chưa gắn giờ · Tự khai báo thời lượng"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelectorGrid(
    selectedKey: String,
    enabled: Boolean = true,
    onSelectCategory: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TimeCategory.entries.forEach { category ->
            val selected = category.key == selectedKey
            val bg = if (selected) WalletMint else Color(0xFFF3F6F3)
            val textColor = if (selected) Color.White else WalletInk
            val borderColor = if (selected) WalletMint else WalletStroke

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .semantics {
                        contentDescription = "Danh mục ${category.displayName}"
                        role = Role.RadioButton
                        this.selected = selected
                    }
                    .clickable(enabled = enabled) { onSelectCategory(category.key) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CategoryIcon(
                    category = category,
                    size = 14.dp,
                    tint = if (selected) Color.White else WalletMint
                )
                Text(
                    text = category.displayName,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun EditRecordDialog(
    record: LedgerRecord,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        categoryKey: String,
        dateLocal: String,
        durationMinutesText: String,
        startHm: String,
        endHm: String,
        allowFutureAsPlanned: Boolean
    ) -> Unit
) {
    val zone = remember(record.timezone) {
        try {
            ZoneId.of(record.timezone)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
    }
    val hmFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val initialStart = remember(record.startedAt) {
        record.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFormatter) } ?: ""
    }
    val initialEnd = remember(record.endedAt) {
        record.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(hmFormatter) } ?: ""
    }

    var title by remember(record.id) { mutableStateOf(record.title) }
    var categoryKey by remember(record.id) { mutableStateOf(record.category.key) }
    var dateLocal by remember(record.id) { mutableStateOf(record.date) }
    var durationMinutesText by remember(record.id) { mutableStateOf(record.durationMinutes.toString()) }
    var startHm by remember(record.id) { mutableStateOf(initialStart) }
    var endHm by remember(record.id) { mutableStateOf(initialEnd) }
    var allowPlanned by remember(record.id) { mutableStateOf(record.layer == TimeLayer.PLANNED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = "Chỉnh sửa bản ghi",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(WalletMicroBg)
                        .padding(9.dp)
                ) {
                    Text(
                        text = if (record.layer == TimeLayer.REPORTED_UNTIMED) {
                            "Bản ghi này chưa gắn mốc giờ. Bổ sung Giờ bắt đầu & Kết thúc để chuyển thành hoạt động Thực tế có giờ."
                        } else {
                            "Trạng thái hiện tại: ${record.layer.labelVi}. Hệ thống sẽ tự kiểm tra va chạm mốc giờ."
                        },
                        fontSize = 10.5.sp,
                        color = WalletMicroText,
                        lineHeight = 15.sp
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(110) },
                    label = { Text("Tên hoạt động") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Danh mục",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMuted
                )
                CategorySelectorGrid(
                    selectedKey = categoryKey,
                    onSelectCategory = { categoryKey = it }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateLocal,
                        onValueChange = { dateLocal = it.trim() },
                        label = { Text("Ngày (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = durationMinutesText,
                        onValueChange = { durationMinutesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Số phút") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startHm,
                        onValueChange = { startHm = it.trim() },
                        label = { Text("Bắt đầu (HH:mm)") },
                        placeholder = { Text("13:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endHm,
                        onValueChange = { endHm = it.trim() },
                        label = { Text("Kết thúc (HH:mm)") },
                        placeholder = { Text("14:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lưu ở trạng thái Dự kiến",
                        fontSize = 11.5.sp,
                        color = WalletInk,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowPlanned,
                        onCheckedChange = { allowPlanned = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title,
                        categoryKey,
                        dateLocal,
                        durationMinutesText,
                        startHm,
                        endHm,
                        allowPlanned
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint)
            ) {
                Text("Lưu thay đổi", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = WalletMuted)
            }
        }
    )
}

@Composable
fun ExportJsonDialog(
    jsonContent: String,
    todayDateLocal: String,
    onDismiss: () -> Unit,
    onSaveToFileClick: (fileName: String, content: String) -> Unit,
    onCopiedToast: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = "Xuất dữ liệu Time Wallet (JSON)",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Lưu thành tệp .json vào thiết bị, chia sẻ hoặc sao chép trực tiếp.",
                    fontSize = 11.sp,
                    color = WalletMuted,
                    lineHeight = 15.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF3F6F3))
                        .border(1.dp, WalletStroke, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        // WALLY F6 giả thuyết 4: chỉ xem trước phần đầu; toàn bộ nội dung vẫn dùng để lưu/sao chép.
                        text = if (jsonContent.length > 20_000) jsonContent.take(20_000) + "\n… (đã rút gọn phần xem trước; tệp lưu có đầy đủ)" else jsonContent,
                        fontSize = 10.sp,
                        color = WalletInk
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("time-wallet-json", jsonContent))
                                onCopiedToast()
                            } catch (_: Exception) {
                                // Dữ liệu quá lớn cho clipboard: hãy dùng "Lưu thành tệp".
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sao chép", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "time-wallet-$todayDateLocal.json")
                                putExtra(Intent.EXTRA_TEXT, jsonContent)
                            }
                            try {
                                context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ dữ liệu JSON"))
                            } catch (_: Exception) {
                                // Quá lớn để chia sẻ qua Intent (TransactionTooLarge): hãy dùng "Lưu thành tệp".
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Chia sẻ", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveToFileClick("time-wallet-$todayDateLocal.json", jsonContent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint)
            ) {
                Text("Lưu tệp .JSON", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = WalletMuted)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportJsonDialog(
    state: ImportBackupUiState,
    onJsonChange: (String) -> Unit,
    onPickFileClick: () -> Unit,
    onConfirm: (BackupRestoreMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(BackupRestoreMode.MERGE_SAFE) }
    val parsed = state.parsedPayload

    AlertDialog(
        onDismissRequest = { if (!state.isRestoring) onDismiss() },
        containerColor = Color.White,
        title = {
            Text(
                text = "Nhập & khôi phục dữ liệu (JSON)",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(WalletMicroBg)
                        .padding(9.dp)
                ) {
                    Text(
                        text = "• Chọn tệp .json đã xuất (hoặc dán nội dung JSON) để chuyển dữ liệu sang bản Release mà không mất bản ghi, tag, mục tiêu, ngân sách hay phản tư.\n" +
                            "• Khóa API AI & cấu hình bảo mật được giữ riêng trên thiết bị, không bị ghi đè.",
                        fontSize = 10.5.sp,
                        color = WalletMicroText,
                        lineHeight = 14.5.sp
                    )
                }

                OutlinedButton(
                    onClick = onPickFileClick,
                    enabled = !state.isRestoring,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Chọn tệp .JSON từ thiết bị", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                if (state.rawJson.length > 20_000) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF3F6F3))
                            .border(1.dp, WalletStroke, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Đã nạp tệp JSON (${state.rawJson.length} ký tự). Xem trước 500 ký tự đầu:\n" +
                                    state.rawJson.take(500) + "…",
                                fontSize = 10.sp,
                                color = WalletInk,
                                lineHeight = 14.sp
                            )
                            TextButton(
                                onClick = { onJsonChange("") },
                                enabled = !state.isRestoring,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Xóa nội dung đã nạp", fontSize = 11.5.sp)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = state.rawJson,
                        onValueChange = onJsonChange,
                        enabled = !state.isRestoring,
                        label = { Text("Nội dung sao lưu JSON") },
                        placeholder = { Text("{\"schemaVersion\": 1, \"items\": [...]}") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(WalletDangerBg)
                            .border(1.dp, WalletDangerBorder, RoundedCornerShape(9.dp))
                            .padding(9.dp)
                    ) {
                        Text(
                            text = state.errorMessage,
                            fontSize = 11.sp,
                            color = WalletDangerText,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (parsed != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(WalletMintSoft)
                            .border(1.dp, WalletMint.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                            .padding(9.dp)
                    ) {
                        Text(
                            text = "Hợp lệ · Phát hiện ${parsed.records.size} bản ghi, ${parsed.tags.size} tag, " +
                                "${parsed.goals.size} mục tiêu, ${parsed.budgets.size} ngân sách, " +
                                "${parsed.dailyReviews.size} phản tư ngày.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletInk,
                            lineHeight = 15.sp
                        )
                    }
                }

                Text(
                    text = "Chế độ khôi phục",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BackupRestoreMode.entries.forEach { mode ->
                        val active = selectedMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) (if (mode == BackupRestoreMode.REPLACE_ALL) WalletDangerBg else WalletMintSoft) else Color(0xFFF3F6F3))
                                .border(
                                    1.dp,
                                    if (active) (if (mode == BackupRestoreMode.REPLACE_ALL) WalletDangerText else WalletMint) else WalletStroke,
                                    RoundedCornerShape(10.dp)
                                )
                                .walletClickable(
                                    onClickLabel = mode.labelVi,
                                    enabled = !state.isRestoring,
                                    onClick = { selectedMode = mode }
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = mode.labelVi,
                                fontSize = 11.5.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active && mode == BackupRestoreMode.REPLACE_ALL) WalletDangerText else WalletInk
                            )
                        }
                    }
                }
                Text(
                    text = if (selectedMode == BackupRestoreMode.MERGE_SAFE) {
                        "Gộp an toàn: Giữ dữ liệu đang có, chỉ thêm bản ghi chưa trùng mã/khóa và không chồng lấn giờ."
                    } else {
                        "Khôi phục toàn bộ: Thay thế toàn bộ sổ cái, tag, mục tiêu, ngân sách và phản tư hiện tại bằng tệp sao lưu (nên dùng khi chuyển máy/Debug → Release)."
                    },
                    fontSize = 10.5.sp,
                    color = if (selectedMode == BackupRestoreMode.REPLACE_ALL) WalletDangerText else WalletMuted,
                    lineHeight = 14.5.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMode) },
                enabled = parsed != null && state.errorMessage == null && !state.isRestoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMode == BackupRestoreMode.REPLACE_ALL) WalletDangerText else WalletMint
                )
            ) {
                Text(
                    text = if (state.isRestoring) "Đang khôi phục…" else selectedMode.labelVi,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isRestoring) {
                Text("Hủy", color = WalletMuted)
            }
        }
    )
}

@Composable
fun AiSettingsDialog(
    initialPreferences: AiPreferences,
    onDismiss: () -> Unit,
    onSave: (optIn: Boolean, baseUrl: String, apiKey: String, modelName: String) -> Unit,
    connectionTestMessage: String? = null,
    connectionTesting: Boolean = false,
    diagnosticsText: String = "",
    onTestConnection: (baseUrl: String, apiKey: String, modelName: String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var optIn by remember { mutableStateOf(initialPreferences.optInEnabled) }
    var baseUrl by remember { mutableStateOf(initialPreferences.baseUrl) }
    var apiKey by remember { mutableStateOf(initialPreferences.apiKey) }
    var modelName by remember { mutableStateOf(initialPreferences.modelName) }

    val initialHost = remember(initialPreferences.baseUrl) {
        AiPreferences.extractValidatedHttpsHost(initialPreferences.baseUrl) ?: initialPreferences.trustedHost
    }
    val currentValidatedHost = remember(baseUrl) {
        AiPreferences.extractValidatedHttpsHost(baseUrl)
    }
    // v1.0.12 (P1-AI): báo NGAY trong hộp thoại (thông báo dạng toast nằm sau hộp thoại nên người dùng không thấy) và không cho lưu/kiểm tra khóa hỏng.
    val keyProblem = remember(apiKey) { ApiKeyHygiene.check(ApiKeyHygiene.normalize(apiKey)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlyphIcon(WalletGlyph.SETTINGS, color = WalletMint, size = 20.dp)
                Text(
                    text = "Cấu hình AI đề xuất (Opt-in)",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(WalletMicroBg)
                        .padding(9.dp)
                ) {
                    val hostDisplay = currentValidatedHost ?: "URL chưa hợp lệ (bắt buộc https://)"
                    Text(
                        text = "• Wally dùng AI trên máy chủ để hiểu và trả lời. Khi chưa có khóa hoặc đang tắt, Wally không phân tích thay; bạn vẫn dùng Ghi thủ công, Đồng hồ, Nhật ký, Ngân sách và Tổng quan bình thường (offline).\n" +
                            "• Khi bật: mỗi câu bạn nhập ở tab Wally kèm ngày, giờ hiện tại và múi giờ được gửi tới $hostDisplay (/v1/chat/completions). Số liệu thống kê được tính trên máy bạn, không gửi sổ cái lên máy chủ.\n" +
                            "• Bảo mật: Bắt buộc HTTPS. Khóa API do bạn nhập, được mã hóa AES-GCM trong Android Keystore, gắn với host đích ($hostDisplay), không nằm trong ứng dụng hay bản sao lưu.",
                        fontSize = 10.sp,
                        color = WalletMicroText,
                        lineHeight = 14.5.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (currentValidatedHost != null) {
                            "Gửi câu Chat tới $currentValidatedHost (Opt-in)"
                        } else {
                            "Bật AI API (Opt-in)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = optIn, onCheckedChange = { optIn = it })
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { nextUrl ->
                        val nextHost = AiPreferences.extractValidatedHttpsHost(nextUrl)
                        if (nextHost != null && !nextHost.equals(initialHost, ignoreCase = true) && apiKey == initialPreferences.apiKey) {
                            // Không tự động giữ lại khóa API cũ khi người dùng đổi sang host mới
                            apiKey = ""
                        }
                        baseUrl = nextUrl
                    },
                    label = { Text("Base URL (Bắt buộc https://)") },
                    placeholder = { Text("https://api.foza.ai/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            if (currentValidatedHost != null && !currentValidatedHost.equals(initialHost, ignoreCase = true)) {
                                "Nhập lại API Key cho $currentValidatedHost"
                            } else {
                                "API Key (Bearer Token)"
                            }
                        )
                    },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
                if (keyProblem != null) {
                    Text(
                        text = "Khóa API có ký tự không hợp lệ ở vị trí ${keyProblem.position} (${keyProblem.kindVi}). Hãy dán lại khóa.",
                        fontSize = 11.sp, color = Color(0xFFB3261E), lineHeight = 15.sp,
                        modifier = Modifier.semantics { contentDescription = "Lỗi khóa API" }
                    )
                }
                if (initialPreferences.keyUnavailable && apiKey.isEmpty()) {
                    Text(
                        text = "Khóa đã lưu không đọc được trên thiết bị này. Hãy nhập lại. Sổ cái và nhập thủ công không bị ảnh hưởng.",
                        fontSize = 10.5.sp, color = Color(0xFF9C540B), lineHeight = 15.sp
                    )
                }

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Tên model") },
                    placeholder = { Text(AiPreferences.DEFAULT_AI_MODEL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        AiPreferences.DEFAULT_AI_MODEL to "Mặc định ổn định",
                        "cinb/gemini-3.8-flash" to "Gemini (khi rảnh tải)"
                    ).forEach { (preset, tagLabel) ->
                        val selectedPreset = modelName.trim() == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedPreset) WalletMintSoft else Color(0xFFF3F6F3))
                                .border(1.dp, if (selectedPreset) WalletMint else WalletStroke, RoundedCornerShape(8.dp))
                                .walletClickable(onClickLabel = "Chọn model $preset") { modelName = preset }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(preset, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WalletInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tagLabel, fontSize = 8.5.sp, color = WalletMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // WALLY: kiểm tra kết nối bằng payload tổng hợp "ping"; không gửi nhật ký của bạn.
                OutlinedButton(
                    onClick = { onTestConnection(baseUrl, apiKey, modelName) },
                    enabled = !connectionTesting && currentValidatedHost != null && apiKey.isNotBlank() && keyProblem == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(if (connectionTesting) "Đang kiểm tra…" else "Kiểm tra kết nối", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (!connectionTestMessage.isNullOrBlank()) {
                    Text(text = connectionTestMessage, fontSize = 11.sp, color = WalletInk, lineHeight = 15.sp)
                    if (connectionTestMessage.startsWith("✗") && (connectionTestMessage.contains("503") || connectionTestMessage.contains("5xx") || connectionTestMessage.contains("403") || connectionTestMessage.contains("404"))) {
                        Text(
                            text = "Gợi ý: Nếu tuyến đang chọn lỗi 503/403 hoặc 404, hãy chạm chọn \"${AiPreferences.DEFAULT_AI_MODEL}\" ở trên rồi Kiểm tra kết nối lại.",
                            fontSize = 10.sp,
                            color = Color(0xFF9C540B),
                            lineHeight = 14.sp
                        )
                    }
                }
                if (diagnosticsText.isNotBlank()) {
                    Text("Chẩn đoán gần đây (không chứa khóa hay nội dung nhật ký)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
                    Text(text = diagnosticsText.take(1800), fontSize = 8.5.sp, color = WalletMicroText, lineHeight = 11.sp)
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("time-wallet-ai-diagnostics", diagnosticsText))
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sao chép chẩn đoán", fontSize = 11.sp) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(optIn, baseUrl, apiKey, modelName) },
                enabled = (!optIn || currentValidatedHost != null) && keyProblem == null,
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint)
            ) {
                Text("Xác nhận & Lưu", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = WalletMuted)
            }
        }
    )
}

@Composable
fun ProductInfoDialog(
    onDismiss: () -> Unit,
    crashReport: String? = null,
    onClearCrashReport: () -> Unit = {}
) {
    val context = LocalContext.current
    // Hiển thị phiên bản thật của bản đang cài để Owner đối chiếu với APK candidate.
    val versionLabel = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_wallet),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Text(
                    text = if (versionLabel.isBlank()) "Time Wallet" else "Time Wallet v$versionLabel",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\"Thời gian là tài sản hữu hạn và không thể tích lũy. Quản lý thời gian để hiểu và chủ động phân bổ, không để phán xét nghỉ ngơi hay tối đa hóa thời gian làm việc.\"",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WalletMicroText,
                    lineHeight = 16.sp
                )
                HorizontalDivider(color = WalletStroke)
                Text(
                    text = "• 5 trạng thái sổ cái:\n" +
                        "  1. Đã đo đồng hồ (MEASURED)\n" +
                        "  2. Thực tế có giờ (REPORTED_TIMED)\n" +
                        "  3. Thực tế chưa gắn giờ (REPORTED_UNTIMED)\n" +
                        "  4. Đang diễn ra (ACTIVE — chỉ tính phần đã qua)\n" +
                        "  5. Dự kiến (PLANNED — không tính vào thời gian đã dùng)\n" +
                        "• Dashboard tách rõ Quỹ còn lại trong ngày và Thời gian chưa ghi nhận trong quá khứ.",
                    fontSize = 11.sp,
                    color = WalletInk,
                    lineHeight = 16.sp
                )
                if (!crashReport.isNullOrBlank()) {
                    // v1.0.10: hiện THỜI ĐIỂM và PHIÊN BẢN thực sự phát sinh lỗi. Bản ghi cũ chưa có phiên bản => "không xác định",
                    // tuyệt đối không gán cho bản đang cài. Văn bản thô bên dưới giữ nguyên; không có gì bị xóa hay sửa tự động.
                    val latestHeader = remember(crashReport) { CrashReportInfo.parse(crashReport).header() }
                    val previousReport = remember(crashReport) { CrashRecorder.readPrevious() }
                    HorizontalDivider(color = WalletStroke)
                    Text(
                        text = "Nhật ký lỗi gần nhất (đã che nội dung riêng tư, không chứa khóa API hay nhật ký của bạn)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                    Text(
                        text = latestHeader,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletInk,
                        lineHeight = 14.sp
                    )
                    Text(
                        text = crashReport.take(900),
                        fontSize = 9.sp,
                        color = WalletMicroText,
                        lineHeight = 12.sp
                    )
                    if (!previousReport.isNullOrBlank()) {
                        Text(
                            text = "Bản ghi lỗi trước đó (được giữ lại, không bị ghi đè)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk
                        )
                        Text(
                            text = CrashReportInfo.parse(previousReport).header(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletInk,
                            lineHeight = 14.sp
                        )
                        Text(
                            text = previousReport.take(600),
                            fontSize = 9.sp,
                            color = WalletMicroText,
                            lineHeight = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val copied = latestHeader + "\n" + crashReport + if (!previousReport.isNullOrBlank()) {
                                "\n\n--- Bản ghi lỗi trước đó ---\n" + CrashReportInfo.parse(previousReport).header() + "\n" + previousReport
                            } else {
                                ""
                            }
                            cm.setPrimaryClip(ClipData.newPlainText("time-wallet-error-log", copied))
                        }) { Text("Sao chép", fontSize = 12.sp) }
                        TextButton(onClick = onClearCrashReport) { Text("Xóa nhật ký", fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint)
            ) {
                Text("Đã hiểu", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun DangerOutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WalletDangerBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = WalletDangerBg,
            contentColor = WalletDangerText
        ),
        contentPadding = PaddingValues(vertical = 11.dp, horizontal = 12.dp)
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeBudgetItemCard(
    progress: TimeBudgetProgress,
    compactMode: Boolean = false,
    onEditClick: (TimeCategory) -> Unit,
    onDeleteClick: ((TimeCategory) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isBeingDragged: Boolean = false,
    dragHandleContent: (@Composable () -> Unit)? = null,
    extraFooterContent: (@Composable () -> Unit)? = null
) {
    val cat = progress.category
    val catColor = Color(cat.colorHex)
    val barColor = if (progress.combinedIsExceeded) Color(0xFFD97706) else catColor
    val diffBadgeBg = if (progress.combinedIsExceeded) Color(0xFFFFF3DF) else Color(0xFFE5F5EC)
    val diffBadgeText = if (progress.combinedIsExceeded) Color(0xFF9C540B) else Color(0xFF1F6A4A)
    val percentInt = (progress.combinedProgressFraction * 100f).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isBeingDragged) Color(0xFFF6FBF8) else Color.White)
            .border(
                width = if (isBeingDragged) 2.dp else 1.dp,
                color = if (isBeingDragged) WalletMint else Color(0xFFE8EEE9),
                shape = RoundedCornerShape(20.dp)
            )
            .semantics { role = Role.Button }
            .heightIn(min = 48.dp)
            .clickable { onEditClick(cat) }
            .padding(if (compactMode) 14.dp else 16.dp)
    ) {
        // v1.0.10 - bố cục XẾP CHỒNG (không còn một hàng ngang ép chữ):
        // hàng 1 = (tay kéo) + biểu tượng + TÊN (tối đa 2 dòng) + sửa/xóa; hàng 2 = chip kỳ hạn/ưu tiên + nhãn còn/vượt (tự xuống dòng nguyên khối);
        // hàng 3 = câu tiến độ ở TOÀN BỘ chiều rộng thẻ; sau đó là thanh tiến độ như cũ.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dragHandleContent?.invoke()
            CategoryBadge(category = cat, size = if (compactMode) 38.dp else 42.dp)

            Text(
                text = cat.displayName,
                fontSize = if (compactMode) 15.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = "Sửa ngân sách ${cat.displayName}"; role = Role.Button }
                    .clickable { onEditClick(cat) },
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(WalletGlyph.EDIT, color = WalletMint, size = 20.dp)
            }

            if (!compactMode && onDeleteClick != null) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .semantics { contentDescription = "Xóa ngân sách ${cat.displayName}"; role = Role.Button }
                        .clickable { onDeleteClick(cat) },
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(WalletGlyph.DELETE, color = WalletDangerText, size = 19.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEDF2EE))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = progress.period.labelVi,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (!compactMode && progress.budget.isPriority) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE7F5ED))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "★ Ưu tiên",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletMint,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(diffBadgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = progress.combinedDifferenceLabelVi,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = diffBadgeText,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Đã ghi nhận: ${progress.combinedUsedFormatted} / Mục tiêu: ${progress.targetFormatted}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = WalletMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE6EDE8))
            ) {
                if (progress.combinedProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.combinedProgressFraction.coerceIn(0.03f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(barColor)
                    )
                }
            }
            Text(
                text = "$percentInt%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WalletMuted,
                maxLines = 1,
                softWrap = false
            )
        }

        if (progress.untimedNoteSeconds > 0L || progress.plannedSeconds > 0L) {
            Spacer(modifier = Modifier.height(5.dp))
            val extraParts = buildList {
                if (progress.untimedNoteSeconds > 0L) {
                    add(
                        "Đã gồm chưa gắn giờ: ${
                            LedgerAnalyticsCalculator.formatCompactDurationSeconds(progress.untimedNoteSeconds)
                        }"
                    )
                }
                if (progress.plannedSeconds > 0L) {
                    add(
                        "Dự kiến: ${
                            LedgerAnalyticsCalculator.formatCompactDurationSeconds(progress.plannedSeconds)
                        }"
                    )
                }
            }.joinToString(" · ")
            Text(
                text = extraParts + if (progress.plannedSeconds > 0L) " (dự kiến không tính vào đã ghi nhận)" else "",
                fontSize = 11.5.sp,
                color = Color(0xFF7B8C81)
            )
        }
        extraFooterContent?.invoke()
    }
}

data class BudgetEditorDraft(
    val categoryKey: String,
    val period: BudgetPeriod,
    val hoursText: String,
    val minutesText: String,
    val isPriority: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeBudgetEditorDialog(
    initialCategory: TimeCategory?,
    existingBudgets: List<CategoryTimeBudget>,
    onDismiss: () -> Unit,
    onSave: (
        categoryKey: String,
        period: BudgetPeriod,
        targetHoursText: String,
        targetMinutesText: String,
        isPriority: Boolean
    ) -> Unit,
    onDelete: (categoryKey: String) -> Unit,
    preservedDraft: BudgetEditorDraft? = null,
    onDeleteWithDraft: ((BudgetEditorDraft) -> Unit)? = null
) {
    val startCategory = initialCategory ?: existingBudgets.firstOrNull()?.category ?: TimeCategory.WORK
    val seedCategoryKey = preservedDraft?.categoryKey ?: startCategory.key
    var selectedCategoryKey by remember { mutableStateOf(seedCategoryKey) }

    val matchedBudget = remember(selectedCategoryKey, existingBudgets) {
        existingBudgets.firstOrNull { it.category.key == selectedCategoryKey }
    }
    val usePreserved = preservedDraft != null && preservedDraft.categoryKey == seedCategoryKey

    var selectedPeriod by remember {
        mutableStateOf(
            if (usePreserved) preservedDraft!!.period
            else matchedBudget?.period ?: BudgetPeriod.WEEKLY
        )
    }
    var hoursText by remember {
        mutableStateOf(
            if (usePreserved) preservedDraft!!.hoursText
            else ((matchedBudget?.targetMinutes ?: 600) / 60).toString()
        )
    }
    var minutesText by remember {
        mutableStateOf(
            if (usePreserved) preservedDraft!!.minutesText
            else ((matchedBudget?.targetMinutes ?: 0) % 60).toString()
        )
    }
    var isPriority by remember {
        mutableStateOf(
            if (usePreserved) preservedDraft!!.isPriority
            else matchedBudget?.isPriority ?: true
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = if (matchedBudget != null) "Chỉnh sửa ngân sách thời gian" else "Đặt ngân sách thời gian",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Chọn danh mục",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMuted
                )
                CategorySelectorGrid(
                    selectedKey = selectedCategoryKey,
                    onSelectCategory = { nextKey ->
                        if (nextKey != selectedCategoryKey) {
                            selectedCategoryKey = nextKey
                            val nextBudget = existingBudgets.firstOrNull { it.category.key == nextKey }
                            selectedPeriod = nextBudget?.period ?: BudgetPeriod.WEEKLY
                            hoursText = ((nextBudget?.targetMinutes ?: 600) / 60).toString()
                            minutesText = ((nextBudget?.targetMinutes ?: 0) % 60).toString()
                            isPriority = nextBudget?.isPriority ?: true
                        }
                    }
                )

                Text(
                    text = "Chu kỳ ngân sách",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BudgetPeriod.entries.forEach { period ->
                        val selected = period == selectedPeriod
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) WalletMint else Color(0xFFF3F6F3))
                                .border(
                                    1.dp,
                                    if (selected) WalletMint else WalletStroke,
                                    RoundedCornerShape(10.dp)
                                )
                                .semantics { role = Role.Button }
                                .clickable { selectedPeriod = period }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period.labelVi,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else WalletInk
                            )
                        }
                    }
                }

                // Quick preset chips
                val presetsHours = if (selectedPeriod == BudgetPeriod.WEEKLY) {
                    listOf(5, 10, 15, 20)
                } else {
                    listOf(1, 2, 4, 8)
                }
                // WALLY F5: nhãn riêng một dòng + FlowRow; chip không lặp "/tuần" và không xuống dòng từng ký tự.
                Text(
                    text = "Chọn nhanh (${selectedPeriod.labelVi.lowercase()})",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletMuted
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    presetsHours.forEach { presetH ->
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEDF4EF))
                                .semantics { contentDescription = "Đặt ${presetH} giờ ${selectedPeriod.labelVi.lowercase()}"; role = Role.Button }
                                .clickable {
                                    hoursText = presetH.toString()
                                    minutesText = "0"
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${presetH}h",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletMint,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Số giờ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Số phút (0–59)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF5F8F6))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ưu tiên trên Dashboard",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk
                        )
                        Text(
                            text = "Dashboard hiển thị tối đa 3 ngân sách ưu tiên.",
                            fontSize = 9.5.sp,
                            color = WalletMuted
                        )
                    }
                    Switch(
                        checked = isPriority,
                        onCheckedChange = { isPriority = it }
                    )
                }

                if (matchedBudget != null) {
                    DangerOutlineButton(
                        text = "Xóa ngân sách danh mục này",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val draft = BudgetEditorDraft(
                                categoryKey = selectedCategoryKey,
                                period = selectedPeriod,
                                hoursText = hoursText,
                                minutesText = minutesText,
                                isPriority = isPriority
                            )
                            if (onDeleteWithDraft != null) {
                                onDeleteWithDraft(draft)
                            } else {
                                onDelete(selectedCategoryKey)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedCategoryKey,
                        selectedPeriod,
                        hoursText,
                        minutesText,
                        isPriority
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WalletMint)
            ) {
                Text("Lưu mục tiêu", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = WalletMuted)
            }
        }
    )
}

/** v1.0.10: xác nhận ngắn trước khi xóa MỤC TIÊU ngân sách (từ nút × ở Phân tích và từ hộp thoại sửa). Không đụng bản ghi thời gian. */
@Composable
fun BudgetDeleteConfirmDialog(
    categoryKey: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = TimeCategory.fromKeyOrDefault(categoryKey).displayName
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Xóa ngân sách?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
        text = {
            Text(
                text = "Xóa mục tiêu ngân sách \"$name\"? Các bản ghi thời gian đã lưu không bị ảnh hưởng.",
                fontSize = 12.sp,
                color = WalletMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = WalletDangerText)
            ) {
                Text("Xóa", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy", color = WalletMuted) }
        }
    )
}
