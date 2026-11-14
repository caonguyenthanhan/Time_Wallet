package com.timewallet.mvp.ui.screens

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.ui.HistoryFilter
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    todayDateLocal: String,
    records: List<LedgerRecord>,
    selectedFilter: HistoryFilter,
    onSelectFilter: (HistoryFilter) -> Unit,
    onConfirmPlannedOrActiveRecord: (LedgerRecord) -> Unit,
    onEditRecord: (LedgerRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onLoadSampleData: () -> Unit,
    onExportJsonClick: () -> Unit,
    onClearAllData: () -> Unit
) {
    var recordPendingDelete by remember { mutableStateOf<LedgerRecord?>(null) }
    var showConfirmClearAll by remember { mutableStateOf(false) }
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

    Column(modifier = Modifier.fillMaxWidth()) {
        PageIntroHeader(
            eyebrow = "SỔ GIAO DỊCH THỜI GIAN",
            title = "Nhật ký hoạt động.",
            subtitle = "Phân biệt rõ hoạt động Thực tế (đã đo / có giờ / chưa gắn giờ), Đang diễn ra và Dự kiến."
        )

        // Filter Tabs (Việt hóa 100%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            HistoryFilter.entries.forEach { filter ->
                val selected = filter == selectedFilter
                val bg = if (selected) Color(0xFF205F4B) else Color.White
                val textColor = if (selected) Color.White else Color(0xFF657A6C)
                val borderColor = if (selected) Color(0xFF205F4B) else Color(0xFFE2E9E2)

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .semantics { role = Role.Button }
                        .clickable { onSelectFilter(filter) }
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.label,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        WalletSurfaceCard(paddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            if (groupedByDate.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Chưa có hoạt động trong nhóm bộ lọc này.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF7D9086),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                groupedByDate.entries.forEachIndexed { groupIdx, (dateStr, dayRecords) ->
                    if (groupIdx > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    val dayTotalSeconds = JournalDayTotal.seconds(dayRecords, nowMs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatFriendlyDayHeader(dateStr, todayDateLocal),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F6A5C),
                            modifier = Modifier.weight(1f)
                        )
                        if (dayTotalSeconds > 0L) {
                            Text(
                                text = "Tổng ${LedgerAnalyticsCalculator.formatHoursMinutesFromSeconds(dayTotalSeconds)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletInk,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    dayRecords.forEachIndexed { itemIdx, record ->
                        ActivityRecordRow(
                            record = record,
                            nowEpochMs = nowMs,
                            todayDateLocal = todayDateLocal,
                            showEditAndDelete = true,
                            onConfirmActualClick = onConfirmPlannedOrActiveRecord,
                            onEditClick = onEditRecord,
                            onDeleteClick = { recordPendingDelete = it }
                        )
                        if (itemIdx < dayRecords.lastIndex) {
                            HorizontalDivider(color = Color(0xFFEFF2EE))
                        }
                    }
                }
            }
        }

        SectionHeader(title = "Quản lý dữ liệu")

        WalletSurfaceCard {
            Text(
                text = "Dữ liệu lưu cục bộ qua Room SQLite và được bảo toàn khi nâng cấp. Khoảng giờ dự kiến chỉ chuyển sang thực tế khi bạn bấm \"✓ Chốt thực tế\".",
                fontSize = 10.5.sp,
                color = Color(0xFF7D8E83),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLoadSampleData,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFDCE8DF)),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "Nạp dữ liệu mẫu",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF27644D)
                    )
                }

                OutlinedButton(
                    onClick = onExportJsonClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFDCE8DF)),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "Xuất JSON",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF27644D)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            DangerOutlineButton(
                text = "Xóa toàn bộ dữ liệu",
                modifier = Modifier.fillMaxWidth(),
                onClick = { showConfirmClearAll = true }
            )
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

    if (showConfirmClearAll) {
        AlertDialog(
            onDismissRequest = { showConfirmClearAll = false },
            containerColor = Color.White,
            title = {
                Text("Xóa toàn bộ dữ liệu?", fontWeight = FontWeight.ExtraBold, color = WalletInk)
            },
            text = {
                Text(
                    "Xóa toàn bộ dữ liệu Time Wallet trên thiết bị này? Thao tác này không thể hoàn tác.",
                    fontSize = 12.sp,
                    color = WalletMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClearAll = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletDangerText)
                ) {
                    Text("Xóa toàn bộ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearAll = false }) {
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
        when (date) {
            today -> "Hôm nay ($dateStr)"
            today.minusDays(1) -> "Hôm qua ($dateStr)"
            today.plusDays(1) -> "Ngày mai · Dự kiến ($dateStr)"
            else -> {
                val fmt = DateTimeFormatter.ofPattern("dd MMMM, yyyy", Locale("vi", "VN"))
                date.format(fmt)
            }
        }
    } catch (_: Exception) {
        dateStr
    }
}
