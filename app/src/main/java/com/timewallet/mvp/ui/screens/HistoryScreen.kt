package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.DashboardSummary
import com.timewallet.mvp.domain.usecase.DayLedgerStats
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.ScheduleViewMode
import com.timewallet.mvp.domain.usecase.SelectedSlotGroup
import com.timewallet.mvp.domain.usecase.TimelineConnector
import com.timewallet.mvp.domain.usecase.TimelineConnectorKind
import com.timewallet.mvp.domain.usecase.TimelineFeedEngine
import com.timewallet.mvp.domain.usecase.TimelineFeedFilter
import com.timewallet.mvp.domain.usecase.TimelineFeedItem
import com.timewallet.mvp.domain.usecase.TimelineGapSlot
import com.timewallet.mvp.domain.usecase.TimelineHorizon
import com.timewallet.mvp.ui.HistoryFilter
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerSolid
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletIsDark
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletUntrackedBar
import com.timewallet.mvp.ui.theme.WalletWarningBg
import com.timewallet.mvp.ui.theme.WalletWarningBorder
import com.timewallet.mvp.ui.theme.WalletWarningText
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
    HistoryFilter.UNTIMED -> "Chưa giờ"
    HistoryFilter.ACTIVE_OR_PLANNED -> "Sắp tới"
}

private fun HistoryFilter.toDomainFeedFilter(): TimelineFeedFilter = when (this) {
    HistoryFilter.ALL -> TimelineFeedFilter.ALL
    HistoryFilter.ACTUAL_TIMED -> TimelineFeedFilter.ACTUAL_TIMED
    HistoryFilter.UNTIMED -> TimelineFeedFilter.UNTIMED
    HistoryFilter.ACTIVE_OR_PLANNED -> TimelineFeedFilter.UPCOMING_OR_ACTIVE
}

private val timelineFilterDisplayOrder: List<HistoryFilter> = listOf(
    HistoryFilter.ALL,
    HistoryFilter.ACTIVE_OR_PLANNED,
    HistoryFilter.ACTUAL_TIMED,
    HistoryFilter.UNTIMED
)

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
    onShiftPlannedRecord: (String, Int) -> Unit = { _, _ -> },
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
    tasks: List<TaskItem> = emptyList(),
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
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }

    val zone = remember { ZoneId.systemDefault() }
    val timelineFeed = remember(
        records,
        activeTimer,
        todayStats,
        selectedFilter,
        todayDateLocal,
        nowMs,
        zone
    ) {
        TimelineFeedEngine.build(
            records = records,
            activeTimer = activeTimer,
            todayStats = todayStats,
            filter = selectedFilter.toDomainFeedFilter(),
            todayDateLocal = todayDateLocal,
            nowEpochMs = nowMs,
            zoneId = zone
        )
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Dòng thời gian",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(WalletSoftNeutral)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(true to "Lịch ngày", false to "Dòng").forEach { (isCal, label) ->
                        val selected = activeCalendarMode == isCal
                        val fullLabel = if (isCal) "Lịch ngày" else "Dòng thời gian"
                        WalletPill(
                            onClickLabel = "Chế độ $fullLabel",
                            containerColor = if (selected) WalletMint else Color.Transparent,
                            selected = selected,
                            role = Role.Tab,
                            contentDescription = "Chế độ $fullLabel",
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                            onClick = { setCalendarMode(isCal) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
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
                overnightCarryoverSeconds = timelineFeed.todayOvernightCarryoverSeconds,
                plannedTodaySeconds = timelineFeed.todayPlannedSeconds,
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
                    embeddedGridChromeReserveDp = 320,
                    onAddForSlots = onAddForSlots,
                    tasks = tasks,
                    recordedSummaryBadge = todaySummary24Text,
                    onRecordedSummaryClick = if (todayStats != null) ({ showWalletStatsModal = true }) else null,
                    onSwitchToTimelineMode = { setCalendarMode(false) },
                    nowEpochMs = nowMs
                )
            }
        } else {

        // Bộ lọc gọn 320dp-safe: nhãn ngắn gọn, cuộn ngang dự phòng, vẫn giữ contentDescription đầy đủ.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            timelineFilterDisplayOrder.forEach { filter ->
                val selected = filter == selectedFilter
                WalletPill(
                    onClickLabel = "Lọc ${filter.label}",
                    containerColor = if (selected) WalletMint else WalletCardBg,
                    borderColor = if (selected) WalletMint else WalletStroke,
                    selected = selected,
                    role = Role.Tab,
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                    onClick = { onSelectFilter(filter) }
                ) {
                    Text(
                        text = filter.shortLabel(),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else WalletMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (liveStatusAnnouncement.isNotBlank()) {
            Text(
                text = liveStatusAnnouncement,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = WalletMint,
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 2.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        val hasAnyVisibleContent = timelineFeed.upcomingDays.isNotEmpty() ||
            timelineFeed.current.items.isNotEmpty() ||
            timelineFeed.current.hasActiveTimer ||
            timelineFeed.pastDays.isNotEmpty()

        if (records.isEmpty() && activeTimer == null) {
            SoftCard {
                EmptyState(
                    glyph = WalletGlyph.LIST,
                    title = "Chưa có hoạt động nào",
                    hint = "Ghi lại hoạt động đầu tiên của bạn.",
                    actionLabel = "Ghi hoạt động đầu tiên",
                    onAction = onAddManual
                )
            }
        } else if (!hasAnyVisibleContent) {
            SoftCard {
                EmptyState(glyph = WalletGlyph.LIST, title = "Không có hoạt động trong bộ lọc này")
            }
        } else {
            val hmFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
            val fmtCompact = LedgerAnalyticsCalculator::formatCompactDurationSeconds
            val fmtHours = LedgerAnalyticsCalculator::formatHoursMinutesFromSeconds

            // 1. SẮP DIỄN RA (Tương lai tách biệt: từ gần hiện tại -> xa hơn; mặc định hiện tối đa 2 mục gần nhất để "Bây giờ" và phiên đang chạy luôn thấy ngay)
            if (timelineFeed.upcomingDays.isNotEmpty()) {
                var showAllUpcoming by rememberSaveable { mutableStateOf(false) }
                val totalUpcoming = timelineFeed.upcomingCount
                val upcomingCap = if (totalUpcoming > 2 && !showAllUpcoming) 2 else Int.MAX_VALUE
                var renderedUpcoming = 0

                TimelineHorizonHeader(
                    title = "Sắp diễn ra",
                    badgeText = "Dự kiến ${fmtCompact(timelineFeed.upcomingTotalPlannedSeconds)}",
                    isUpcoming = true
                )
                for (upcomingGroup in timelineFeed.upcomingDays) {
                    if (renderedUpcoming >= upcomingCap) break
                    val orderedTimedRecords = remember(upcomingGroup) {
                        sortTimelineDayRecordsChronological(upcomingGroup.timedItems.map { it.record })
                    }
                    val itemById = remember(upcomingGroup) {
                        upcomingGroup.timedItems.associateBy { it.record.id }
                    }
                    val allOrderedTimed = remember(orderedTimedRecords, itemById) {
                        orderedTimedRecords.mapNotNull { itemById[it.id] }
                    }
                    val remainingCap = (upcomingCap - renderedUpcoming).coerceAtLeast(0)
                    val visibleTimed = allOrderedTimed.take(remainingCap)
                    val visibleUntimed = upcomingGroup.untimedItems.take((remainingCap - visibleTimed.size).coerceAtLeast(0))
                    if (visibleTimed.isEmpty() && visibleUntimed.isEmpty()) break

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (upcomingGroup.dateLocal == todayDateLocal) {
                                "Hôm nay · phần còn lại"
                            } else {
                                formatFriendlyDayHeader(upcomingGroup.dateLocal, todayDateLocal)
                            },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = fmtHours(upcomingGroup.totalPlannedSeconds),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    visibleTimed.forEachIndexed { idx, feedItem ->
                        renderedUpcoming++
                        val canShowConnectorBelow = idx < visibleTimed.lastIndex
                        val connector = if (canShowConnectorBelow) upcomingGroup.connectorsAfterTimedItem[feedItem.record.id] else null
                        TimelineEntryRow(
                            item = feedItem, linkedTask = feedItem.record.taskId?.let(tasksById::get),
                            isFirst = idx == 0,
                            isLast = idx == visibleTimed.lastIndex && visibleUntimed.isEmpty() && connector == null,
                            hasGapBelow = connector?.isActionableGap == true,
                            zone = zone,
                            hmFormatter = hmFormatter,
                            selectedPlannedId = selectedPlannedId,
                            onSelectPlannedAdjust = { id ->
                                selectedPlannedId = if (selectedPlannedId == id) null else id
                                if (selectedPlannedId == id) {
                                    liveStatusAnnouncement = "Mở điều chỉnh khung giờ và thời lượng cho \"${feedItem.record.title}\"."
                                }
                            },
                            onConfirmRecord = onConfirmPlannedOrActiveRecord,
                            onEditRecord = onEditRecord,
                            onDeleteRecord = { recordPendingDelete = it },
                            onShiftPlannedRecord = { id, delta ->
                                onShiftPlannedRecord(id, delta)
                                liveStatusAnnouncement = "Đã dịch khung giờ \"${feedItem.record.title}\" ${if (delta > 0) "+" else ""}${delta}p."
                            },
                            onResizePlannedRecord = { id, delta ->
                                onResizePlannedRecord(id, delta)
                                liveStatusAnnouncement = "Đã đổi thời lượng \"${feedItem.record.title}\" ${if (delta > 0) "+" else ""}${delta}p."
                            }
                        )
                        if (connector != null) {
                            TimelineConnectorRow(
                                connector = connector,
                                dateLocal = upcomingGroup.dateLocal,
                                zone = zone,
                                hmFormatter = hmFormatter,
                                isFutureHorizon = true,
                                onAddForSlot = onAddForSlot
                            )
                        }
                    }

                    visibleUntimed.forEachIndexed { uIdx, feedItem ->
                        renderedUpcoming++
                        TimelineEntryRow(
                            item = feedItem, linkedTask = feedItem.record.taskId?.let(tasksById::get),
                            isFirst = visibleTimed.isEmpty() && uIdx == 0,
                            isLast = uIdx == visibleUntimed.lastIndex,
                            hasGapBelow = false,
                            zone = zone,
                            hmFormatter = hmFormatter,
                            selectedPlannedId = selectedPlannedId,
                            onSelectPlannedAdjust = { id ->
                                selectedPlannedId = if (selectedPlannedId == id) null else id
                            },
                            onConfirmRecord = onConfirmPlannedOrActiveRecord,
                            onEditRecord = onEditRecord,
                            onDeleteRecord = { recordPendingDelete = it },
                            onShiftPlannedRecord = onShiftPlannedRecord,
                            onResizePlannedRecord = onResizePlannedRecord
                        )
                    }
                }

                if (totalUpcoming > 2) {
                    val hiddenUpcoming = (totalUpcoming - renderedUpcoming).coerceAtLeast(0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        WalletPill(
                            onClickLabel = if (showAllUpcoming) "Thu gọn danh sách việc sắp tới" else "Mở rộng xem thêm $hiddenUpcoming việc sắp tới",
                            containerColor = WalletMintSoft,
                            borderColor = WalletStroke,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            onClick = { showAllUpcoming = !showAllUpcoming }
                        ) {
                            Text(
                                text = if (showAllUpcoming) "Thu gọn việc sắp tới ▴" else "Còn $hiddenUpcoming việc sắp tới · Mở rộng ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletMintAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 2. HIỆN TẠI: Neo "● Bây giờ HH:mm" + phiên đang chạy / việc chờ xác nhận / đối soát
            val showCurrentBlock = timelineFeed.current.hasActiveTimer ||
                timelineFeed.current.items.isNotEmpty() ||
                timelineFeed.upcomingDays.isNotEmpty() ||
                timelineFeed.pastDays.isNotEmpty()
            if (showCurrentBlock) {
                TimelineNowAnchorBar(
                    nowTimeLabel = timelineFeed.nowTimeLabel,
                    pendingActionCount = timelineFeed.current.pendingActionCount,
                    hasLiveTimer = timelineFeed.current.hasActiveTimer || timelineFeed.current.items.any { it.isRunningNow }
                )
            }

            if (timelineFeed.current.hasActiveTimer && activeTimer != null) {
                TimelineLiveTimerCard(
                    activeTimer = activeTimer,
                    nowEpochMs = nowMs,
                    zone = zone,
                    hmFormatter = hmFormatter,
                    onNavigateToTimer = onNavigateToTimer
                )
            }

            timelineFeed.current.items.forEachIndexed { idx, feedItem ->
                TimelineEntryRow(
                    item = feedItem, linkedTask = feedItem.record.taskId?.let(tasksById::get),
                    isFirst = idx == 0 && !timelineFeed.current.hasActiveTimer,
                    isLast = idx == timelineFeed.current.items.lastIndex,
                    hasGapBelow = false,
                    zone = zone,
                    hmFormatter = hmFormatter,
                    selectedPlannedId = selectedPlannedId,
                    onSelectPlannedAdjust = { id ->
                        selectedPlannedId = if (selectedPlannedId == id) null else id
                    },
                    onConfirmRecord = onConfirmPlannedOrActiveRecord,
                    onEditRecord = onEditRecord,
                    onDeleteRecord = { recordPendingDelete = it },
                    onShiftPlannedRecord = { id, delta ->
                        onShiftPlannedRecord(id, delta)
                        liveStatusAnnouncement = "Đã dịch khung giờ \"${feedItem.record.title}\" ${if (delta > 0) "+" else ""}${delta}p."
                    },
                    onResizePlannedRecord = { id, delta ->
                        onResizePlannedRecord(id, delta)
                        liveStatusAnnouncement = "Đã đổi thời lượng \"${feedItem.record.title}\" ${if (delta > 0) "+" else ""}${delta}p."
                    }
                )
            }

            // 3. ĐÃ DIỄN RA (Nhật ký thực tế: ngày mới nhất lên đầu, trong ngày mới nhất lên đầu)
            if (timelineFeed.pastDays.isNotEmpty()) {
                TimelineHorizonHeader(
                    title = "Đã diễn ra",
                    badgeText = "Mới nhất trước",
                    isUpcoming = false
                )
                timelineFeed.pastDays.forEach { pastDay ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatFriendlyDayHeader(pastDay.dateLocal, todayDateLocal),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (pastDay.totalRecordedSeconds > 0L) {
                                Text(
                                    text = "Đã ghi ${fmtHours(pastDay.totalRecordedSeconds)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WalletMint,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (pastDay.overnightCarryoverSeconds > 0L) {
                            Text(
                                text = "Trong ngày ${fmtCompact(pastDay.inDayRecordedSeconds)} · +${fmtCompact(pastDay.overnightCarryoverSeconds)} từ hoạt động qua đêm",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = WalletMuted,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        pastDay.trailingGapToNow?.let { gap ->
                            TimelineGapActionRow(
                                gapSlot = gap,
                                zone = zone,
                                hmFormatter = hmFormatter,
                                onAddForSlot = onAddForSlot
                            )
                        }

                        pastDay.timedItems.forEachIndexed { idx, feedItem ->
                            val connector = pastDay.connectorsAfterTimedItem[feedItem.record.id]
                            TimelineEntryRow(
                                item = feedItem, linkedTask = feedItem.record.taskId?.let(tasksById::get),
                                isFirst = idx == 0,
                                isLast = idx == pastDay.timedItems.lastIndex && pastDay.untimedItems.isEmpty() && connector == null,
                                hasGapBelow = connector?.isActionableGap == true,
                                zone = zone,
                                hmFormatter = hmFormatter,
                                selectedPlannedId = selectedPlannedId,
                                onSelectPlannedAdjust = { id ->
                                    selectedPlannedId = if (selectedPlannedId == id) null else id
                                },
                                onConfirmRecord = onConfirmPlannedOrActiveRecord,
                                onEditRecord = onEditRecord,
                                onDeleteRecord = { recordPendingDelete = it },
                                onShiftPlannedRecord = onShiftPlannedRecord,
                                onResizePlannedRecord = onResizePlannedRecord
                            )
                            if (connector != null) {
                                TimelineConnectorRow(
                                    connector = connector,
                                    dateLocal = pastDay.dateLocal,
                                    zone = zone,
                                    hmFormatter = hmFormatter,
                                    isFutureHorizon = false,
                                    onAddForSlot = onAddForSlot
                                )
                            }
                        }

                        if (pastDay.untimedItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chưa gắn giờ",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = fmtCompact(pastDay.untimedRecordedSeconds),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            pastDay.untimedItems.forEachIndexed { uIdx, feedItem ->
                                TimelineEntryRow(
                                    item = feedItem, linkedTask = feedItem.record.taskId?.let(tasksById::get),
                                    isFirst = pastDay.timedItems.isEmpty() && uIdx == 0,
                                    isLast = uIdx == pastDay.untimedItems.lastIndex,
                                    hasGapBelow = false,
                                    zone = zone,
                                    hmFormatter = hmFormatter,
                                    selectedPlannedId = selectedPlannedId,
                                    onSelectPlannedAdjust = {},
                                    onConfirmRecord = onConfirmPlannedOrActiveRecord,
                                    onEditRecord = onEditRecord,
                                    onDeleteRecord = { recordPendingDelete = it },
                                    onShiftPlannedRecord = onShiftPlannedRecord,
                                    onResizePlannedRecord = onResizePlannedRecord
                                )
                            }
                        }
                    }
                }
            }

            // Khoảng đệm cuối trang để thẻ cuối luôn cuộn lên trên nút nổi Wally & thanh điều hướng.
            Spacer(modifier = Modifier.height(20.dp))
        }
        }
    }

    recordPendingDelete?.let { target ->
        RecordDeleteConfirmationDialog(
            record = target,
            todayDateLocal = todayDateLocal,
            nowEpochMs = nowMs,
            onConfirmDelete = {
                onDeleteRecord(target.id)
                recordPendingDelete = null
            },
            onDismiss = { recordPendingDelete = null }
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

@Composable
private fun TimelineHorizonHeader(
    title: String,
    badgeText: String,
    isUpcoming: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isUpcoming) WalletMintAccent else WalletMuted)
            )
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isUpcoming) WalletMintAccent else WalletMuted,
                maxLines = 1,
                softWrap = false
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (isUpcoming) WalletMintSoft else WalletSoftNeutral)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = badgeText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUpcoming) WalletMintAccent else WalletMuted,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun TimelineNowAnchorBar(
    nowTimeLabel: String,
    pendingActionCount: Int,
    hasLiveTimer: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(WalletMint)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "● Bây giờ · $nowTimeLabel",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = WalletMint.copy(alpha = 0.45f)
        )
        val statusText = when {
            pendingActionCount > 0 -> "$pendingActionCount cần xử lý"
            hasLiveTimer -> "Đang đo"
            else -> "Hiện tại"
        }
        Text(
            text = statusText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (pendingActionCount > 0) WalletWarningText else WalletMintAccent,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineLiveTimerCard(
    activeTimer: ActiveTimerSession,
    nowEpochMs: Long,
    zone: ZoneId,
    hmFormatter: DateTimeFormatter,
    onNavigateToTimer: () -> Unit
) {
    val startHm = remember(activeTimer.startedAtEpochMs, zone) {
        Instant.ofEpochMilli(activeTimer.startedAtEpochMs).atZone(zone).format(hmFormatter)
    }
    val elapsedSecs = ((nowEpochMs - activeTimer.startedAtEpochMs) / 1000L).coerceAtLeast(0L)
    val category = remember(activeTimer.categoryKey) { TimeCategory.fromKeyOrDefault(activeTimer.categoryKey) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .timelineRail(
                color = Color(category.colorHex),
                isFirst = true,
                isLast = true,
                isInProgress = true,
                isPlanned = false,
                railCenterX = 7.dp,
                dotTopDp = 22.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(WalletMintTint)
                .border(1.2.dp, WalletMint, RoundedCornerShape(14.dp))
                .walletClickable(onClickLabel = "Mở bộ đếm ${activeTimer.title}") { onNavigateToTimer() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryBadge(category = category, size = 24.dp)
                Text(
                    text = activeTimer.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = LedgerAnalyticsCalculator.formatCompactDurationSeconds(elapsedSecs.coerceAtLeast(60L)),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletMintAccent,
                    maxLines = 1,
                    softWrap = false
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$startHm–nay · Đang đo trực tiếp",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletMintAccent,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "Mở bộ đếm ›",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMint,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineEntryRow(
    item: TimelineFeedItem,
    linkedTask: TaskItem? = null,
    isFirst: Boolean,
    isLast: Boolean,
    hasGapBelow: Boolean,
    zone: ZoneId,
    hmFormatter: DateTimeFormatter,
    selectedPlannedId: String?,
    onSelectPlannedAdjust: (String) -> Unit,
    onConfirmRecord: (LedgerRecord) -> Unit,
    onEditRecord: (LedgerRecord) -> Unit,
    onDeleteRecord: (LedgerRecord) -> Unit,
    onShiftPlannedRecord: (String, Int) -> Unit,
    onResizePlannedRecord: (String, Int) -> Unit
) {
    val record = item.record
    val isInProgress = item.isRunningNow
    val isPlanned = item.resolvedLayer == TimeLayer.PLANNED && !record.isCancelled
    val isSelectedPlanned = isPlanned && selectedPlannedId == record.id
    var menuExpanded by remember(record.id) { mutableStateOf(false) }

    val timeLabel = remember(record, zone) {
        val recordZone = try { ZoneId.of(record.timezone) } catch (_: Exception) { zone }
        if (record.startedAt != null && record.endedAt != null) {
            val s = Instant.ofEpochMilli(record.startedAt).atZone(recordZone).format(hmFormatter)
            val e = Instant.ofEpochMilli(record.endedAt).atZone(recordZone).format(hmFormatter)
            "$s–$e"
        } else {
            "Chưa gắn giờ"
        }
    }

    val statusText = when {
        record.isNeedsReconciliation -> "Cần đối soát"
        item.confirmationState == ConfirmationState.PENDING_CONFIRMATION -> "Chờ xác nhận"
        isInProgress -> "Đang diễn ra"
        isPlanned -> "Dự kiến"
        item.confirmationState == ConfirmationState.INTERRUPTED -> "Gián đoạn"
        item.confirmationState == ConfirmationState.ADJUSTED -> "Đã điều chỉnh"
        item.resolvedLayer == TimeLayer.MEASURED -> "Đã đo"
        item.resolvedLayer == TimeLayer.REPORTED_UNTIMED -> "Cả ngày"
        else -> "Đã ghi"
    }

    val cardBg = when {
        item.needsUserAction -> WalletWarningBg
        isInProgress -> WalletMintTint
        else -> WalletCardBg
    }
    val cardBorder = when {
        isSelectedPlanned || isInProgress -> WalletMint
        item.needsUserAction -> WalletWarningBorder
        else -> WalletStroke
    }
    val statusColor = when {
        item.needsUserAction -> WalletWarningText
        isInProgress || isPlanned -> WalletMintAccent
        else -> WalletMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .timelineRail(
                color = Color(record.category.colorHex),
                isFirst = isFirst,
                isLast = isLast,
                isInProgress = isInProgress,
                isPlanned = isPlanned,
                railCenterX = 7.dp,
                dotTopDp = 22.dp,
                hasGapBelow = hasGapBelow
            )
            .semantics {
                contentDescription = "${record.title}, $timeLabel, $statusText"
            },
        verticalAlignment = Alignment.Top
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(if (isSelectedPlanned || isInProgress) 1.4.dp else 1.dp, cardBorder, RoundedCornerShape(14.dp))
                .walletClickable(onClickLabel = "Mở chi tiết ${record.title}") { onEditRecord(record) }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Hàng 1: Badge gọn + Tên hoạt động + Thời lượng
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                CategoryBadge(category = record.category, size = 24.dp)
                Text(
                    text = record.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = LedgerAnalyticsCalculator.formatCompactDurationSeconds(record.durationSeconds),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPlanned || isInProgress) WalletMintAccent else WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Hàng 2: Cụm Giờ · Trạng thái · Gợi ý + nút ⋮ (tác vụ phụ gọn vào menu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = timeLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalletMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "· $statusText",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (!item.relativeHintVi.isNullOrBlank()) {
                        Text(
                            text = "· ${item.relativeHintVi}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WalletMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (linkedTask != null && !linkedTask.title.equals(record.title, ignoreCase = true)) {
                        Text(
                            text = "· Việc: ${linkedTask.title}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletMintAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    WalletPill(
                        onClickLabel = "Tùy chọn khác cho ${record.title}",
                        shape = RoundedCornerShape(8.dp),
                        containerColor = Color.Transparent,
                        contentDescription = "Tùy chọn khác cho ${record.title}",
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        onClick = { menuExpanded = true }
                    ) {
                        Text(
                            text = "⋮",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = WalletCardBg
                    ) {
                        if (isPlanned && record.hasTimeBounds) {
                            DropdownMenuItem(
                                text = { Text("Dịch giờ / đổi thời lượng", fontSize = 13.sp, color = WalletInk) },
                                onClick = {
                                    menuExpanded = false
                                    onSelectPlannedAdjust(record.id)
                                }
                            )
                        }
                        if (!record.hasTimeBounds && !isPlanned) {
                            DropdownMenuItem(
                                text = { Text("Gắn mốc giờ cụ thể", fontSize = 13.sp, color = WalletInk) },
                                onClick = {
                                    menuExpanded = false
                                    onEditRecord(record)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Sửa chi tiết", fontSize = 13.sp, color = WalletInk) },
                            onClick = {
                                menuExpanded = false
                                onEditRecord(record)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa bản ghi", fontSize = 13.sp, color = WalletDangerText) },
                            onClick = {
                                menuExpanded = false
                                onDeleteRecord(record)
                            }
                        )
                    }
                }
            }

            // Hàng thao tác ngắn gọn chỉ hiện với hoạt động Dự kiến / Đang làm / Chờ xác nhận
            if (isPlanned || isInProgress || item.needsUserAction) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WalletPill(
                        onClickLabel = "Xác nhận ${record.title}",
                        shape = RoundedCornerShape(9.dp),
                        containerColor = if (item.needsUserAction) WalletMint else WalletMintSoft,
                        borderColor = if (item.needsUserAction) WalletMint else WalletStroke,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        onClick = { onConfirmRecord(record) }
                    ) {
                        Text(
                            text = when {
                                item.needsUserAction -> "✓ Xác nhận"
                                isInProgress -> "Hoàn tất"
                                else -> "✓ Xác nhận"
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.needsUserAction) Color.White else WalletMintAccent,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    WalletPill(
                        onClickLabel = "Điều chỉnh ${record.title}",
                        shape = RoundedCornerShape(9.dp),
                        containerColor = if (isSelectedPlanned) WalletMintSoft else WalletSoftNeutral,
                        borderColor = WalletStroke,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        onClick = {
                            if (isPlanned && record.hasTimeBounds) {
                                onSelectPlannedAdjust(record.id)
                            } else {
                                onEditRecord(record)
                            }
                        }
                    ) {
                        Text(
                            text = if (isSelectedPlanned) "Thu gọn" else "Điều chỉnh",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Bảng điều chỉnh nhanh phân tách rõ 2 nút từng gây khó hiểu: Dịch khung giờ vs Đổi thời lượng
            if (isPlanned && isSelectedPlanned) {
                val minsNow = ((record.durationSeconds + 30L) / 60L).toInt()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WalletMintTint)
                        .border(1.dp, WalletStroke, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Dịch khung giờ",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "« Sớm 15p",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(WalletCardBg)
                                .walletClickable(onClickLabel = "Dịch sớm 15 phút cho ${record.title}") {
                                    onShiftPlannedRecord(record.id, -15)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Muộn 15p »",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(WalletCardBg)
                                .walletClickable(onClickLabel = "Dịch muộn 15 phút cho ${record.title}") {
                                    onShiftPlannedRecord(record.id, 15)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Đổi thời lượng (${LedgerAnalyticsCalculator.formatCompactDurationSeconds(record.durationSeconds)})",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "−15p",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (minsNow >= 20) WalletInk else WalletMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(WalletCardBg)
                                .walletClickable(
                                    onClickLabel = "Giảm 15 phút cho ${record.title}",
                                    enabled = minsNow >= 20
                                ) {
                                    onResizePlannedRecord(record.id, -15)
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "+15p",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(WalletCardBg)
                                .walletClickable(onClickLabel = "Tăng 15 phút cho ${record.title}") {
                                    onResizePlannedRecord(record.id, 15)
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sửa giờ cụ thể…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .walletClickable(onClickLabel = "Sửa chi tiết ${record.title}") {
                                    onEditRecord(record)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Xong",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .walletClickable(onClickLabel = "Đóng chỉnh nhanh ${record.title}") {
                                    onSelectPlannedAdjust(record.id)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineConnectorRow(
    connector: TimelineConnector,
    dateLocal: String,
    zone: ZoneId,
    hmFormatter: DateTimeFormatter,
    isFutureHorizon: Boolean,
    onAddForSlot: (dateLocal: String, startHm: String?, endHm: String?, saveAsPlanned: Boolean) -> Unit
) {
    val isDark = WalletIsDark
    val railColor = if (isDark) Color(0xFF33493F) else Color(0xFFCADDD0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val cx = 7.dp.toPx()
                val lineW = 2.dp.toPx()
                if (connector.isActionableGap) {
                    var y = 0f
                    val dash = 4.dp.toPx()
                    val step = 7.5.dp.toPx()
                    while (y < size.height) {
                        drawLine(
                            railColor,
                            Offset(cx, y),
                            Offset(cx, (y + dash).coerceAtMost(size.height)),
                            strokeWidth = lineW,
                            cap = StrokeCap.Round
                        )
                        y += step
                    }
                } else {
                    drawLine(railColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = lineW)
                }
            }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (connector.isActionableGap) {
            val startHm = remember(connector.startEpochMs, zone) {
                Instant.ofEpochMilli(connector.startEpochMs).atZone(zone).format(hmFormatter)
            }
            val endHm = remember(connector.endEpochMs, zone) {
                Instant.ofEpochMilli(connector.endEpochMs).atZone(zone).format(hmFormatter)
            }
            WalletPill(
                onClickLabel = "${connector.labelVi} — bấm để thêm",
                containerColor = if (isFutureHorizon) WalletMintTint else WalletSoftNeutral,
                borderColor = WalletStroke,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                onClick = { onAddForSlot(dateLocal, startHm, endHm, isFutureHorizon) }
            ) {
                Text(
                    text = "${connector.labelVi} · ${if (isFutureHorizon) "+ Xếp" else "+ Ghi"}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFutureHorizon) WalletMintAccent else WalletMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            val chipColor = if (connector.kind == TimelineConnectorKind.PARALLEL_OVERLAP) {
                WalletWarningText
            } else {
                WalletMuted
            }
            Text(
                text = connector.labelVi,
                fontSize = 10.5.sp,
                fontWeight = if (connector.kind == TimelineConnectorKind.PARALLEL_OVERLAP) FontWeight.SemiBold else FontWeight.Normal,
                color = chipColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun TimelineGapActionRow(
    gapSlot: TimelineGapSlot,
    zone: ZoneId,
    hmFormatter: DateTimeFormatter,
    onAddForSlot: (dateLocal: String, startHm: String?, endHm: String?, saveAsPlanned: Boolean) -> Unit
) {
    val startHm = remember(gapSlot.startEpochMs, zone) {
        Instant.ofEpochMilli(gapSlot.startEpochMs).atZone(zone).format(hmFormatter)
    }
    val endHm = remember(gapSlot.endEpochMs, zone) {
        Instant.ofEpochMilli(gapSlot.endEpochMs).atZone(zone).format(hmFormatter)
    }
    val isDark = WalletIsDark
    val railColor = if (isDark) Color(0xFF33493F) else Color(0xFFCADDD0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val cx = 7.dp.toPx()
                val lineW = 2.dp.toPx()
                var y = 0f
                val dash = 4.dp.toPx()
                val step = 7.5.dp.toPx()
                while (y < size.height) {
                    drawLine(
                        railColor,
                        Offset(cx, y),
                        Offset(cx, (y + dash).coerceAtMost(size.height)),
                        strokeWidth = lineW,
                        cap = StrokeCap.Round
                    )
                    y += step
                }
            }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WalletPill(
            onClickLabel = "${gapSlot.labelVi} — bấm để ghi bù",
            containerColor = WalletSoftNeutral,
            borderColor = WalletStroke,
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
            onClick = { onAddForSlot(gapSlot.dateLocal, startHm, endHm, false) }
        ) {
            Text(
                text = "${gapSlot.labelVi} · + Ghi",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = WalletMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
