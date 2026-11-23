package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.domain.usecase.DayLedgerStats
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.ScheduleViewMode
import com.timewallet.mvp.domain.usecase.SelectedSlotGroup
import com.timewallet.mvp.ui.HistoryFilter
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerSolid
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletUntrackedBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Sắp xếp các mục trong một ngày theo thứ tự thời gian tăng dần (từ sáng đến tối); mục chưa gắn giờ xếp sau theo thời điểm tạo. */
internal fun sortTimelineDayRecordsChronological(records: List<LedgerRecord>): List<LedgerRecord> =
    records.sortedWith(
        compareBy<LedgerRecord>(
            { it.startedAt == null },
            { it.startedAt ?: Long.MAX_VALUE },
            { it.createdAt }
        )
    )

/** Nhãn bộ lọc ngắn để không xuống dòng; nhãn đầy đủ vẫn nằm trong HistoryFilter.label (đọc cho trình đọc màn hình). */
private fun HistoryFilter.shortLabel(): String = when (this) {
    HistoryFilter.ALL -> "Tất cả"
    HistoryFilter.ACTUAL_TIMED -> "Có giờ"
    HistoryFilter.UNTIMED -> "Chưa gắn giờ"
    HistoryFilter.ACTIVE_OR_PLANNED -> "Đang & dự kiến"
}

@Composable
fun HistoryScreen(
    todayDateLocal: String,
    records: List<LedgerRecord>,
    selectedFilter: HistoryFilter,
    onSelectFilter: (HistoryFilter) -> Unit,
    onConfirmPlannedOrActiveRecord: (LedgerRecord) -> Unit,
    onEditRecord: (LedgerRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onAddManual: () -> Unit = {},
    onResizePlannedRecord: (String, Int) -> Unit = { _, _ -> },
    activeTimer: ActiveTimerSession? = null,
    selectedScheduleDate: String = todayDateLocal,
    scheduleViewMode: ScheduleViewMode = ScheduleViewMode.DAY,
    onSelectScheduleDate: (String) -> Unit = {},
    onSelectScheduleViewMode: (ScheduleViewMode) -> Unit = {},
    onAddForSlot: (dateLocal: String, startHm: String?, endHm: String?, saveAsPlanned: Boolean) -> Unit = { _, _, _, _ -> onAddManual() },
    onAddForSlots: (List<SelectedSlotGroup>) -> Unit = { groups ->
        groups.firstOrNull()?.let { g ->
            val todayCheck = runCatching { LocalDate.parse(todayDateLocal) }.getOrDefault(LocalDate.now())
            val slotDate = runCatching { LocalDate.parse(g.dateLocal) }.getOrDefault(todayCheck)
            onAddForSlot(g.dateLocal, g.startHm, g.endHm, slotDate.isAfter(todayCheck))
        }
    },
    onMoveOrResizeBlock: (recordId: String, dateLocal: String, startHm: String, endHm: String) -> Unit = { _, _, _, _ -> },
    onPlaceUntimedOnSlot: (recordId: String, dateLocal: String, startHm: String, endHm: String) -> Unit = onMoveOrResizeBlock,
    onNavigateToTimer: () -> Unit = {},
    showCalendarMode: Boolean? = null,
    onShowCalendarModeChange: ((Boolean) -> Unit)? = null,
    todayStats: DayLedgerStats? = null,
    inboxTasks: List<TaskItem> = emptyList(),
    onScheduleTaskOnDate: (TaskItem, String) -> Unit = { _, _ -> },
    onOpenDailyReview: () -> Unit = {},
    onOpenTasksTab: () -> Unit = {},
    onRequestWallyReplan: () -> Unit = {},
    nowEpochMs: Long = System.currentTimeMillis()
) {
    var recordPendingDelete by remember { mutableStateOf<LedgerRecord?>(null) }
    var selectedPlannedId by remember { mutableStateOf<String?>(null) }
    var showInboxScheduleSheet by remember { mutableStateOf(false) }
    var showWalletStatsModal by remember { mutableStateOf(false) }
    var liveStatusAnnouncement by remember { mutableStateOf("") }
    var internalCalendarMode by rememberSaveable { mutableStateOf(true) }
    val activeCalendarMode = showCalendarMode ?: internalCalendarMode
    val setCalendarMode: (Boolean) -> Unit = { mode ->
        internalCalendarMode = mode
        onShowCalendarModeChange?.invoke(mode)
    }
    val nowMs = nowEpochMs

    val filteredRecords = remember(records, selectedFilter, nowMs, todayDateLocal) {
        records.filter { record ->
            val resolved = record.resolveLayerAt(nowMs, todayDateLocal)
            when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.ACTUAL_TIMED ->
                    !record.isCancelled && !record.isNeedsReconciliation &&
                        (resolved == TimeLayer.MEASURED || resolved == TimeLayer.REPORTED_TIMED)
                HistoryFilter.UNTIMED ->
                    !record.isCancelled && resolved == TimeLayer.REPORTED_UNTIMED
                HistoryFilter.ACTIVE_OR_PLANNED ->
                    resolved == TimeLayer.ACTIVE || resolved == TimeLayer.PLANNED ||
                        record.isCancelled || record.isNeedsReconciliation
            }
        }
    }

    val zone = remember { ZoneId.systemDefault() }
    val carryoverTodaySeconds = remember(records, todayDateLocal, nowMs, selectedFilter) {
        if (selectedFilter == HistoryFilter.ALL || selectedFilter == HistoryFilter.ACTUAL_TIMED) {
            JournalDayTotal.overnightCarryoverSeconds(todayDateLocal, records, zone, nowMs)
        } else {
            0L
        }
    }
    val groupedByDate = remember(filteredRecords, carryoverTodaySeconds, todayDateLocal) {
        val map = filteredRecords
            .groupBy { it.date }
            .toMutableMap()
        if (carryoverTodaySeconds > 0L && todayDateLocal !in map) {
            map[todayDateLocal] = emptyList()
        }
        map.toSortedMap(compareByDescending { it })
    }

    val todaySummary24Text = remember(todayStats) {
        todayStats?.let { stats ->
            val summary24 = DashboardSummary.from(stats)
            val recStr = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(summary24.recordedSeconds)
            "$recStr / 24h"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Khi ở chế độ Lịch trình, toàn bộ tiêu đề + 24h + bộ chuyển chế độ nằm gọn trong panel xanh hợp nhất;
        // thanh trên chỉ hiện khi đang ở chế độ Dòng thời gian để chuyển về Lịch trình.
        if (!activeCalendarMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Dòng thời gian",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(WalletSoftNeutral)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(true to "Lịch trình", false to "Dòng thời gian").forEach { (isCal, label) ->
                        val selected = activeCalendarMode == isCal
                        WalletPill(
                            onClickLabel = "Chế độ $label",
                            containerColor = if (selected) WalletMint else Color.Transparent,
                            selected = selected,
                            role = Role.Tab,
                            contentDescription = "Chế độ $label",
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            onClick = { setCalendarMode(isCal) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else WalletMuted,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        if (!activeCalendarMode && todayStats != null) {
            Compact24hWalletCard(
                todayDateLocal = todayDateLocal,
                todayStats = todayStats,
                inboxTaskCount = inboxTasks.size,
                onOpenDailyReview = onOpenDailyReview,
                onOpenTasksInbox = { showInboxScheduleSheet = true }
            )
        }

        if (activeCalendarMode) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ScheduleCalendarView(
                    todayDateLocal = todayDateLocal,
                    records = records,
                    activeTimer = activeTimer,
                    selectedDateLocal = selectedScheduleDate.ifBlank { todayDateLocal },
                    viewMode = scheduleViewMode,
                    onSelectDate = onSelectScheduleDate,
                    onSelectViewMode = onSelectScheduleViewMode,
                    onAddForSlot = onAddForSlot,
                    onEditRecord = onEditRecord,
                    onConfirmPlannedOrActiveRecord = onConfirmPlannedOrActiveRecord,
                    onMoveOrResizeBlock = onMoveOrResizeBlock,
                    onDeleteRecord = onDeleteRecord,
                    onPlaceUntimedOnSlot = onPlaceUntimedOnSlot,
                    onNavigateToTimer = onNavigateToTimer,
                    showBackButton = false,
                    embeddedInScrollContainer = true,
                    onAddForSlots = onAddForSlots,
                    recordedSummaryBadge = todaySummary24Text,
                    onRecordedSummaryClick = if (todayStats != null) ({ showWalletStatsModal = true }) else null,
                    onSwitchToTimelineMode = { setCalendarMode(false) },
                    nowEpochMs = nowMs
                )
            }
        } else {

        // Bộ lọc gọn: một hàng cuộn ngang, phân tầng surface sạch không viền.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { filter ->
                val selected = filter == selectedFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) WalletMint else WalletCardBg)
                        .semantics { this.selected = selected }
                        .walletClickable(onClickLabel = "Lọc ${filter.label}", role = Role.Tab) { onSelectFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.shortLabel(),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else WalletMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (records.isEmpty()) {
            SoftCard {
                EmptyState(
                    glyph = WalletGlyph.LIST,
                    title = "Chưa có hoạt động nào",
                    hint = "Ghi lại hoạt động đầu tiên của bạn.",
                    actionLabel = "Ghi hoạt động đầu tiên",
                    onAction = onAddManual
                )
            }
        } else if (groupedByDate.isEmpty()) {
            SoftCard {
                EmptyState(glyph = WalletGlyph.LIST, title = "Không có hoạt động trong bộ lọc này")
            }
        } else {
            groupedByDate.entries.forEach { (dateStr, dayRecords) ->
                val dayTotalSeconds = if (selectedFilter == HistoryFilter.ALL) {
                    JournalDayTotal.secondsForDate(dateStr, records, zone, nowMs)
                } else {
                    JournalDayTotal.seconds(dayRecords, nowMs)
                }
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatFriendlyDayHeader(dateStr, todayDateLocal),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk,
                            modifier = Modifier.weight(1f)
                        )
                        if (dayTotalSeconds > 0L) {
                            Text(
                                text = LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(dayTotalSeconds),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletMint,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    if (dateStr == todayDateLocal && carryoverTodaySeconds > 0L) {
                        Text(
                            text = "Gồm +${LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(carryoverTodaySeconds)} chuyển tiếp từ hoạt động qua đêm.",
                            fontSize = 11.5.sp,
                            color = WalletMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    // Timeline: cột giờ đối diện bên trái, trục dọc (dừng ở chấm cuối) + thẻ nội dung sắp theo trình tự thời gian trong ngày.
                    val hmFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
                    val sortedDayRecords = remember(dayRecords) { sortTimelineDayRecordsChronological(dayRecords) }
                    if (liveStatusAnnouncement.isNotBlank()) {
                        Text(
                            text = liveStatusAnnouncement,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletMint,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    sortedDayRecords.forEachIndexed { itemIdx, record ->
                        val resolvedLayer = remember(record, nowMs, todayDateLocal) {
                            record.resolveLayerAt(nowMs, todayDateLocal)
                        }
                        val confState = remember(record, nowMs, todayDateLocal) {
                            record.resolveConfirmationStateAt(nowMs, todayDateLocal)
                        }
                        val isInProgress = !record.isCancelled &&
                            resolvedLayer == TimeLayer.ACTIVE &&
                            confState != ConfirmationState.PENDING_CONFIRMATION
                        val isPlanned = !record.isCancelled && resolvedLayer == TimeLayer.PLANNED
                        val isSelectedPlanned = isPlanned && selectedPlannedId == record.id
                        val oppositeSlot: Pair<String, String> = remember(record, zone) {
                            val recordZone = try { ZoneId.of(record.timezone) } catch (_: Exception) { zone }
                            if (record.startedAt != null && record.endedAt != null) {
                                val startStr = Instant.ofEpochMilli(record.startedAt).atZone(recordZone).format(hmFormatter)
                                val endStr = Instant.ofEpochMilli(record.endedAt).atZone(recordZone).format(hmFormatter)
                                startStr to "→ $endStr"
                            } else {
                                "Chưa" to "gắn giờ"
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .timelineRail(
                                    color = Color(record.category.colorHex),
                                    isFirst = itemIdx == 0,
                                    isLast = itemIdx == sortedDayRecords.lastIndex,
                                    isInProgress = isInProgress,
                                    isPlanned = isPlanned,
                                    railCenterX = 7.dp
                                )
                                .semantics {
                                    contentDescription = "Khung giờ ${oppositeSlot.first} ${oppositeSlot.second}"
                                },
                            verticalAlignment = Alignment.Top
                        ) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(WalletCardBg)
                                    .then(
                                        if (isSelectedPlanned) {
                                            Modifier.border(1.5.dp, WalletMint, RoundedCornerShape(18.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                ActivityRecordRow(
                                    record = record,
                                    nowEpochMs = nowMs,
                                    todayDateLocal = todayDateLocal,
                                    showEditAndDelete = true,
                                    onConfirmActualClick = onConfirmPlannedOrActiveRecord,
                                    onEditClick = onEditRecord,
                                    onDeleteClick = { recordPendingDelete = it },
                                    trailingMetaSlot = if (isPlanned && !isSelectedPlanned) {
                                        {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(WalletSoftNeutral)
                                                    .semantics { contentDescription = "Chọn để chỉnh nhanh thời lượng ${record.title}"; role = Role.Button }
                                                    .walletClickable(onClickLabel = "Chọn để chỉnh nhanh thời lượng ${record.title}") {
                                                        selectedPlannedId = record.id
                                                        liveStatusAnnouncement = "Đang chọn chỉnh thời lượng dự kiến \"${record.title}\"."
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "↔ ±15p",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = WalletMintAccent,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    } else null
                                )
                                if (isPlanned && isSelectedPlanned) {
                                    val minsNow = ((record.durationSeconds + 30L) / 60L).toInt()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(WalletMintTint)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "−15p",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (minsNow >= 20) WalletInk else WalletMuted,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(WalletCardBg)
                                                .walletClickable(
                                                    onClickLabel = "Giảm 15 phút cho ${record.title}",
                                                    enabled = minsNow >= 20
                                                ) {
                                                    onResizePlannedRecord(record.id, -15)
                                                    liveStatusAnnouncement = "Đã rút ngắn \"${record.title}\" 15 phút."
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                        Text(
                                            text = LedgerAnalyticsCalculator.formatCompactDurationSeconds(record.durationSeconds),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalletMintAccent,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "+15p",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = WalletInk,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(WalletCardBg)
                                                .walletClickable(onClickLabel = "Tăng 15 phút cho ${record.title}") {
                                                    onResizePlannedRecord(record.id, 15)
                                                    liveStatusAnnouncement = "Đã tăng thêm \"${record.title}\" 15 phút."
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                        Text(
                                            text = "Xong",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalletMuted,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .walletClickable(onClickLabel = "Đóng chỉnh nhanh ${record.title}") {
                                                    selectedPlannedId = null
                                                }
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    recordPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { recordPendingDelete = null },
            containerColor = WalletCardBg,
            title = {
                Text("Xóa bản ghi?", fontWeight = FontWeight.ExtraBold, color = WalletInk)
            },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa bản ghi \"${target.title}\" khỏi sổ cái thời gian?",
                    fontSize = 12.sp,
                    color = WalletMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecord(target.id)
                        recordPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletDangerSolid, contentColor = Color.White)
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordPendingDelete = null }) {
                    Text("Hủy", color = WalletMuted)
                }
            }
        )
    }

    if (showWalletStatsModal && todayStats != null) {
        val fmt24 = LedgerAnalyticsCalculator::formatHoursMinutesFromSeconds
        AlertDialog(
            onDismissRequest = { showWalletStatsModal = false },
            containerColor = WalletCardBg,
            title = {
                Text("Ví 24 giờ hôm nay", fontWeight = FontWeight.ExtraBold, color = WalletInk)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val totalDaySecs24 = todayStats.calendarDaySeconds.coerceAtLeast(1L).toFloat()
                    val recLabel24 = fmt24(DashboardSummary.from(todayStats).recordedSeconds)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$recLabel24 / ${todayStats.calendarDaySeconds / 3600L}h đã ghi nhận",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletMintAccent
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WalletSoftNeutral),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (todayStats.timedSeconds > 0L) {
                            Box(
                                modifier = Modifier
                                    .weight((todayStats.timedSeconds.toFloat() / totalDaySecs24).coerceIn(0.03f, 1f))
                                    .fillMaxHeight()
                                    .background(WalletMint)
                            )
                        }
                        if (todayStats.untrackedPastSeconds > 0L) {
                            Box(
                                modifier = Modifier
                                    .weight((todayStats.untrackedPastSeconds.toFloat() / totalDaySecs24).coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(WalletMuted.copy(alpha = 0.4f))
                            )
                        }
                        if (todayStats.remainingDaySeconds > 0L) {
                            Box(
                                modifier = Modifier
                                    .weight((todayStats.remainingDaySeconds.toFloat() / totalDaySecs24).coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(WalletUntrackedBar.copy(alpha = 0.55f))
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletBg)
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        ) {
                            Text("Có giờ", fontSize = 10.5.sp, color = WalletMuted)
                            Text(fmt24(todayStats.timedSeconds), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletBg)
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        ) {
                            Text("Chưa ghi", fontSize = 10.5.sp, color = WalletMuted)
                            Text(fmt24(todayStats.untrackedPastSeconds), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = WalletMuted)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletBg)
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        ) {
                            Text("Còn lại", fontSize = 10.5.sp, color = WalletMuted)
                            Text(fmt24(todayStats.remainingDaySeconds), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = WalletMintAccent)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WalletPill(
                            onClickLabel = "Phản tư cuối ngày",
                            containerColor = WalletMintDark,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            onClick = {
                                showWalletStatsModal = false
                                onOpenDailyReview()
                            }
                        ) {
                            Text("Phản tư cuối ngày", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                        }
                        if (inboxTasks.isNotEmpty()) {
                            WalletPill(
                                onClickLabel = "Xếp lịch Inbox",
                                containerColor = WalletMintSoft,
                                borderColor = WalletStroke,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                onClick = {
                                    showWalletStatsModal = false
                                    showInboxScheduleSheet = true
                                }
                            ) {
                                Text("Inbox (${inboxTasks.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = WalletMintAccent, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWalletStatsModal = false }) {
                    Text("Đóng", color = WalletMintDark, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showInboxScheduleSheet) {
        val targetDate = selectedScheduleDate.ifBlank { todayDateLocal }
        AlertDialog(
            onDismissRequest = { showInboxScheduleSheet = false },
            containerColor = WalletCardBg,
            title = {
                Text("Việc Inbox chờ xếp lịch (${inboxTasks.size})", fontWeight = FontWeight.ExtraBold, color = WalletInk)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (inboxTasks.isEmpty()) {
                        Text("Tất cả công việc Inbox đều đã có lịch hoặc hoàn thành.", fontSize = 12.sp, color = WalletMuted)
                    } else {
                        Text(
                            text = "Chạm \"Xếp vào $targetDate\" để gán khung giờ trống, hoặc nhờ Wally tự xếp nhiều việc.",
                            fontSize = 11.5.sp,
                            color = WalletMuted
                        )
                        inboxTasks.take(6).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WalletMintTint)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                                    Text(
                                        "${task.priority.name} · ${task.category.displayName} · ~${task.estimatedMinutes ?: 30}p",
                                        fontSize = 11.sp,
                                        color = WalletMuted
                                    )
                                }
                                Text(
                                    text = "Xếp lịch",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(WalletMintDark)
                                        .walletClickable(onClickLabel = "Xếp lịch ${task.title}") {
                                            showInboxScheduleSheet = false
                                            onScheduleTaskOnDate(task, targetDate)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (inboxTasks.isNotEmpty()) {
                    Button(
                        onClick = {
                            showInboxScheduleSheet = false
                            onRequestWallyReplan()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMintDark, contentColor = Color.White)
                    ) {
                        Text("✦ Wally gợi ý lịch", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showInboxScheduleSheet = false
                        onOpenTasksTab()
                    }) {
                        Text("Mở tab Việc", color = WalletMintAccent, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showInboxScheduleSheet = false }) {
                        Text("Đóng", color = WalletMuted)
                    }
                }
            }
        )
    }
}

private fun formatFriendlyDayHeader(dateStr: String, todayDateLocal: String): String {
    return try {
        val today = LocalDate.parse(todayDateLocal)
        val date = LocalDate.parse(dateStr)
        val short = date.format(DateTimeFormatter.ofPattern("dd/MM"))
        when (date) {
            today -> "Hôm nay, $short"
            today.minusDays(1) -> "Hôm qua, $short"
            today.plusDays(1) -> "Ngày mai, $short · Dự kiến"
            else -> {
                val fmt = DateTimeFormatter.ofPattern("dd MMMM, yyyy", Locale("vi", "VN"))
                date.format(fmt)
            }
        }
    } catch (_: Exception) {
        dateStr
    }
}
