package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.ui.HistoryFilter
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    onAddManual: () -> Unit = {}
) {
    var recordPendingDelete by remember { mutableStateOf<LedgerRecord?>(null) }
    val nowMs = System.currentTimeMillis()

    val filteredRecords = remember(records, selectedFilter, nowMs, todayDateLocal) {
        records.filter { record ->
            val resolved = record.resolveLayerAt(nowMs, todayDateLocal)
            when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.ACTUAL_TIMED ->
                    resolved == TimeLayer.MEASURED || resolved == TimeLayer.REPORTED_TIMED
                HistoryFilter.UNTIMED ->
                    resolved == TimeLayer.REPORTED_UNTIMED
                HistoryFilter.ACTIVE_OR_PLANNED ->
                    resolved == TimeLayer.ACTIVE || resolved == TimeLayer.PLANNED
            }
        }
    }

    val groupedByDate = remember(filteredRecords) {
        filteredRecords
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Nhật ký")

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
                val dayTotalSeconds = JournalDayTotal.seconds(dayRecords, nowMs)
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
                    // Timeline: trục dọc + chấm màu danh mục, mỗi bản ghi là một thẻ.
                    dayRecords.forEachIndexed { itemIdx, record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .timelineRail(
                                    color = Color(record.category.colorHex),
                                    isFirst = itemIdx == 0,
                                    isLast = itemIdx == dayRecords.lastIndex
                                )
                        ) {
                            Spacer(modifier = Modifier.width(28.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
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
