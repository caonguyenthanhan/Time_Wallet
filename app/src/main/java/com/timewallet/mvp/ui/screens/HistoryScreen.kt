package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.ScheduleViewMode
import com.timewallet.mvp.ui.HistoryFilter
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
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
    onMoveOrResizeBlock: (recordId: String, dateLocal: String, startHm: String, endHm: String) -> Unit = { _, _, _, _ -> },
    onNavigateToTimer: () -> Unit = {}
) {
    var recordPendingDelete by remember { mutableStateOf<LedgerRecord?>(null) }
    var selectedPlannedId by remember { mutableStateOf<String?>(null) }
    var liveStatusAnnouncement by remember { mutableStateOf("") }
    var showCalendarMode by rememberSaveable { mutableStateOf(true) }
    val nowMs = System.currentTimeMillis()

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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Nhật ký",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletInk,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFFE9F0EC))
                    .border(1.dp, WalletStroke, RoundedCornerShape(100.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(true to "Lịch trình", false to "Dòng thời gian").forEach { (isCal, label) ->
                    val selected = showCalendarMode == isCal
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) WalletMintDark else Color.Transparent)
                            .semantics {
                                contentDescription = "Chế độ $label"
                                this.selected = selected
                            }
                            .walletClickable(onClickLabel = "Chế độ $label", role = Role.Tab) {
                                showCalendarMode = isCal
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
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

        if (showCalendarMode) {
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
                onNavigateToTimer = onNavigateToTimer,
                showBackButton = false,
                embeddedInScrollContainer = true
            )
        } else {

        // Bộ lọc gọn: một hàng cuộn ngang, nhãn không xuống dòng.
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
                        .background(if (selected) Color(0xFF205F4B) else Color.White)
                        .border(1.dp, if (selected) Color(0xFF205F4B) else WalletStroke, RoundedCornerShape(100.dp))
                        .semantics { this.selected = selected }
                        .walletClickable(onClickLabel = "Lọc ${filter.label}", role = Role.Tab) { onSelectFilter(filter) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.shortLabel(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else Color(0xFF657A6C),
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
                                    railCenterX = 70.dp
                                ),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(58.dp)
                                    .padding(top = 12.dp, end = 2.dp)
                                    .semantics {
                                        contentDescription = "Khung giờ ${oppositeSlot.first} ${oppositeSlot.second}"
                                    },
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = oppositeSlot.first,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isInProgress) WalletMint else WalletInk,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = oppositeSlot.second,
                                    fontSize = 10.5.sp,
                                    color = WalletMuted,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .then(
                                        if (isSelectedPlanned) {
                                            Modifier.border(1.5.dp, WalletMint, RoundedCornerShape(20.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                ActivityRecordRow(
                                    record = record,
                                    nowEpochMs = nowMs,
                                    todayDateLocal = todayDateLocal,
                                    showEditAndDelete = true,
                                    onConfirmActualClick = onConfirmPlannedOrActiveRecord,
                                    onEditClick = onEditRecord,
                                    onDeleteClick = { recordPendingDelete = it }
                                )
                                if (isPlanned) {
                                    if (isSelectedPlanned) {
                                        val minsNow = ((record.durationSeconds + 30L) / 60L).toInt()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
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
                                                    .background(Color.White)
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
                                                color = WalletMint,
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
                                                    .background(Color.White)
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
                                    } else {
                                        Text(
                                            text = "↔ Kéo giãn nhanh ±15p",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalletMint,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .walletClickable(onClickLabel = "Chọn để chỉnh nhanh thời lượng ${record.title}") {
                                                    selectedPlannedId = record.id
                                                    liveStatusAnnouncement = "Đang chọn chỉnh thời lượng dự kiến \"${record.title}\"."
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
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
            containerColor = Color.White,
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
                    colors = ButtonDefaults.buttonColors(containerColor = WalletDangerText)
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
