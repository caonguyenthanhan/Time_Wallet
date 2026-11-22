package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.DayScheduleModel
import com.timewallet.mvp.domain.usecase.MonthDayCell
import com.timewallet.mvp.domain.usecase.ScheduleBlockVisualState
import com.timewallet.mvp.domain.usecase.ScheduleConflictCheck
import com.timewallet.mvp.domain.usecase.ScheduleGridBlock
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.ScheduleViewMode
import com.timewallet.mvp.domain.usecase.SelectedSlotGroup
import com.timewallet.mvp.domain.usecase.SelectedTimeSlot
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerBg
import com.timewallet.mvp.ui.theme.WalletDangerSolid
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInfoBg
import com.timewallet.mvp.ui.theme.WalletInfoText
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
import com.timewallet.mvp.ui.theme.WalletWarningBg
import com.timewallet.mvp.ui.theme.WalletWarningText
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val NowIndicatorCoral = Color(0xFFE53935)
private val PlannedAccentBlue = Color(0xFF3563E9)
private val ActiveAccentCyan = Color(0xFF0E9F6E)
private val UnconfirmedAmber = Color(0xFFD97706)

/**
 * Trạng thái tương tác thống nhất cho cả 4 chế độ xem Lịch trình (Ngày / 3 ngày / Tuần / Tháng):
 * - Chọn/kéo khoảng trống -> hiện ngay thao tác thêm trong ngữ cảnh vùng chọn.
 * - Chạm hoạt động -> chọn toàn bộ khối, hiện Sửa / Dời / Giãn / Xóa.
 * - Kéo thân / mép trên / mép dưới -> giữ bản nháp preview + kiểm tra xung đột + cho phép Lưu hoặc Hủy trước khi ghi.
 * - Đặt bản ghi Chưa gắn giờ -> khay riêng ở đầu màn hình, tách bạch với tạo mới trên khoảng trống.
 */
private sealed interface ScheduleInteractionState {
    data object Idle : ScheduleInteractionState
    data class EmptySlotsSelected(val slots: List<SelectedTimeSlot>) : ScheduleInteractionState
    data class BlockSelected(val block: ScheduleGridBlock) : ScheduleInteractionState
    data class BlockDraftTransform(
        val original: ScheduleGridBlock,
        val targetDateLocal: String,
        val draftStartMin: Int,
        val draftEndMin: Int
    ) : ScheduleInteractionState

    data class UntimedPlacement(
        val record: LedgerRecord,
        val targetDateLocal: String? = null,
        val startMinute: Int? = null,
        val endMinute: Int? = null
    ) : ScheduleInteractionState
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCalendarView(
    todayDateLocal: String,
    records: List<LedgerRecord>,
    activeTimer: ActiveTimerSession? = null,
    selectedDateLocal: String,
    viewMode: ScheduleViewMode,
    onSelectDate: (String) -> Unit,
    onSelectViewMode: (ScheduleViewMode) -> Unit,
    onAddForSlot: (dateLocal: String, startHm: String?, endHm: String?, saveAsPlanned: Boolean) -> Unit,
    onEditRecord: (LedgerRecord) -> Unit,
    onConfirmPlannedOrActiveRecord: (LedgerRecord) -> Unit,
    onMoveOrResizeBlock: (recordId: String, dateLocal: String, startHm: String, endHm: String) -> Unit,
    onNavigateToTimer: () -> Unit = {},
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    embeddedInScrollContainer: Boolean = false,
    onAddForSlots: (List<SelectedSlotGroup>) -> Unit = { groups ->
        groups.firstOrNull()?.let { g ->
            val todayCheck = runCatching { LocalDate.parse(todayDateLocal) }.getOrDefault(LocalDate.now())
            val slotDate = runCatching { LocalDate.parse(g.dateLocal) }.getOrDefault(todayCheck)
            onAddForSlot(g.dateLocal, g.startHm, g.endHm, slotDate.isAfter(todayCheck))
        }
    },
    onDeleteRecord: (String) -> Unit = {},
    onPlaceUntimedOnSlot: (recordId: String, dateLocal: String, startHm: String, endHm: String) -> Unit = onMoveOrResizeBlock
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(todayDateLocal) { runCatching { LocalDate.parse(todayDateLocal) }.getOrDefault(LocalDate.now(zone)) }
    val selectedDate = remember(selectedDateLocal, today) { runCatching { LocalDate.parse(selectedDateLocal) }.getOrDefault(today) }
    val nowMs = System.currentTimeMillis()
    val nowLocalTime = remember(nowMs, zone) { Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime() }
    val nowMinute = nowLocalTime.hour * 60 + nowLocalTime.minute

    var showSearchAndFilter by rememberSaveable { mutableStateOf(false) }
    var showLegend by rememberSaveable { mutableStateOf(false) }
    var showCalendarMenu by remember { mutableStateOf(false) }
    var showUntimedSheet by remember { mutableStateOf(false) }
    var blockPendingDelete by remember { mutableStateOf<LedgerRecord?>(null) }
    var interactionState by remember { mutableStateOf<ScheduleInteractionState>(ScheduleInteractionState.Idle) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val multiDayModels = remember(selectedDate, viewMode, records, activeTimer, nowMs / 30_000L, categoryFilter, searchQuery) {
        when (viewMode) {
            ScheduleViewMode.DAY -> listOf(
                ScheduleGridEngine.buildDayScheduleModel(
                    date = selectedDate,
                    records = records,
                    activeTimer = activeTimer,
                    nowEpochMs = nowMs,
                    zone = zone,
                    categoryFilter = categoryFilter,
                    searchQuery = searchQuery
                )
            )
            ScheduleViewMode.THREE_DAYS -> {
                val start3 = if (selectedDate == today) today.minusDays(1) else selectedDate
                ScheduleGridEngine.buildMultiDayScheduleModels(
                    startDate = start3,
                    dayCount = 3,
                    records = records,
                    activeTimer = activeTimer,
                    nowEpochMs = nowMs,
                    zone = zone,
                    categoryFilter = categoryFilter,
                    searchQuery = searchQuery
                )
            }
            ScheduleViewMode.WEEK -> {
                val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ScheduleGridEngine.buildMultiDayScheduleModels(
                    startDate = monday,
                    dayCount = 7,
                    records = records,
                    activeTimer = activeTimer,
                    nowEpochMs = nowMs,
                    zone = zone,
                    categoryFilter = categoryFilter,
                    searchQuery = searchQuery
                )
            }
            ScheduleViewMode.MONTH -> emptyList()
        }
    }

    val monthCells = remember(selectedDate, viewMode, records, activeTimer, nowMs / 30_000L, categoryFilter, searchQuery) {
        if (viewMode == ScheduleViewMode.MONTH) {
            ScheduleGridEngine.buildMonthGrid(
                yearMonth = YearMonth.from(selectedDate),
                records = records,
                activeTimer = activeTimer,
                nowEpochMs = nowMs,
                zone = zone,
                categoryFilter = categoryFilter,
                searchQuery = searchQuery
            )
        } else emptyList()
    }

    val selectedDayModel = remember(selectedDate, records, activeTimer, nowMs / 30_000L, categoryFilter, searchQuery) {
        ScheduleGridEngine.buildDayScheduleModel(
            date = selectedDate,
            records = records,
            activeTimer = activeTimer,
            nowEpochMs = nowMs,
            zone = zone,
            categoryFilter = categoryFilter,
            searchQuery = searchQuery
        )
    }

    val blocksByDate = remember(multiDayModels, selectedDayModel) {
        buildMap {
            multiDayModels.forEach { put(it.dateLocal, it.timedBlocks) }
            if (selectedDayModel.dateLocal !in keys) {
                put(selectedDayModel.dateLocal, selectedDayModel.timedBlocks)
            }
        }
    }

    // Đồng bộ interactionState nếu bản ghi nền thay đổi hoặc bị xóa
    LaunchedEffect(records) {
        when (val cur = interactionState) {
            is ScheduleInteractionState.EmptySlotsSelected -> {
                val filtered = cur.slots.filter { slot ->
                    val dayOcc = blocksByDate[slot.dateLocal].orEmpty()
                    dayOcc.none { b -> b.startMinute <= slot.startMinute && b.endMinute >= slot.endMinute }
                }
                if (filtered.isEmpty()) interactionState = ScheduleInteractionState.Idle
                else if (filtered.size != cur.slots.size) interactionState = ScheduleInteractionState.EmptySlotsSelected(filtered)
            }
            is ScheduleInteractionState.BlockSelected -> {
                val refreshed = blocksByDate.values.flatten().firstOrNull {
                    it.recordId == cur.block.recordId && it.dateLocal == cur.block.dateLocal
                }
                interactionState = if (refreshed != null) {
                    ScheduleInteractionState.BlockSelected(refreshed)
                } else {
                    ScheduleInteractionState.Idle
                }
            }
            is ScheduleInteractionState.BlockDraftTransform -> {
                val stillExists = records.any { it.id == cur.original.recordId }
                if (!stillExists) interactionState = ScheduleInteractionState.Idle
            }
            is ScheduleInteractionState.UntimedPlacement -> {
                val updated = records.firstOrNull { it.id == cur.record.id }
                if (updated == null || updated.startedAt != null) {
                    interactionState = ScheduleInteractionState.Idle
                }
            }
            ScheduleInteractionState.Idle -> Unit
        }
    }

    val selectedEmptySlots = (interactionState as? ScheduleInteractionState.EmptySlotsSelected)?.slots.orEmpty()
    val mergedSelectedGroups = remember(selectedEmptySlots) {
        ScheduleGridEngine.mergeSelectedEmptySlots(selectedEmptySlots)
    }

    val untimedItems = remember(multiDayModels, selectedDayModel, viewMode) {
        if (viewMode == ScheduleViewMode.MONTH) {
            selectedDayModel.untimedRecords
        } else {
            multiDayModels.flatMap { it.untimedRecords }
        }
    }

    val headerActualMinutes = if (viewMode == ScheduleViewMode.MONTH) {
        monthCells.filter { it.inCurrentMonth }.sumOf { it.actualMinutes }
    } else {
        multiDayModels.sumOf { it.actualMinutesTotal }
    }
    val headerPlannedMinutes = if (viewMode == ScheduleViewMode.MONTH) {
        monthCells.filter { it.inCurrentMonth }.sumOf { it.plannedMinutes }
    } else {
        multiDayModels.sumOf { it.plannedMinutesTotal }
    }
    val headerUnconfirmed = if (viewMode == ScheduleViewMode.MONTH) {
        monthCells.filter { it.inCurrentMonth }.sumOf { it.unconfirmedCount }
    } else {
        multiDayModels.sumOf { it.unconfirmedCount }
    }

    fun selectOrToggleEmptyRange(dateLocal: String, startMin: Int, endMin: Int, replaceIfCrossed: Boolean = false) {
        val candidate = SelectedTimeSlot(dateLocal, startMin.coerceIn(0, 1439), endMin.coerceIn(startMin + 1, 1440))
        val cur = (interactionState as? ScheduleInteractionState.EmptySlotsSelected)?.slots.orEmpty()
        val next = if (replaceIfCrossed) {
            listOf(candidate)
        } else {
            ScheduleGridEngine.toggleSelectedSlot(cur, candidate)
        }
        interactionState = if (next.isEmpty()) ScheduleInteractionState.Idle else ScheduleInteractionState.EmptySlotsSelected(next)
    }

    fun updateEmptySlotRangeExact(dateLocal: String, startMin: Int, endMin: Int) {
        val cleanStart = startMin.coerceIn(0, 1425)
        val cleanEnd = endMin.coerceIn(cleanStart + 15, 1440)
        interactionState = ScheduleInteractionState.EmptySlotsSelected(
            listOf(SelectedTimeSlot(dateLocal, cleanStart, cleanEnd))
        )
    }

    fun openCreateForEmptyGroups(groups: List<SelectedSlotGroup>) {
        interactionState = ScheduleInteractionState.Idle
        onAddForSlots(groups)
    }

    fun handleTapEmptyPosition(dayModel: DayScheduleModel, minuteOfDay: Int) {
        val cur = interactionState
        if (cur is ScheduleInteractionState.UntimedPlacement) {
            val (s, e) = ScheduleGridEngine.buildUntimedPlacementWindow(minuteOfDay, cur.record.durationMinutes)
            interactionState = cur.copy(targetDateLocal = dayModel.dateLocal, startMinute = s, endMinute = e)
            return
        }
        // Nếu người dùng đang chọn đúng slot này -> bỏ chọn; ngược lại chọn khoảng trống 15p-snapped tại vị trí chạm
        val curSlots = (cur as? ScheduleInteractionState.EmptySlotsSelected)?.slots.orEmpty()
        val existingHit = curSlots.firstOrNull {
            it.dateLocal == dayModel.dateLocal && minuteOfDay in it.startMinute until it.endMinute
        }
        if (existingHit != null) {
            val remaining = curSlots.filterNot { it == existingHit }
            interactionState = if (remaining.isEmpty()) {
                ScheduleInteractionState.Idle
            } else {
                ScheduleInteractionState.EmptySlotsSelected(remaining)
            }
            return
        }
        val (sMin, eMin) = ScheduleGridEngine.prefillGapRange(minuteOfDay, dayModel.timedBlocks, defaultDurationMin = 60)
        selectOrToggleEmptyRange(dayModel.dateLocal, sMin, eMin)
    }

    fun handleTapBlock(block: ScheduleGridBlock) {
        if (interactionState is ScheduleInteractionState.UntimedPlacement) return
        val cur = interactionState
        if (cur is ScheduleInteractionState.BlockSelected && cur.block.recordId == block.recordId && cur.block.dateLocal == block.dateLocal) {
            when {
                block.isActiveTimer -> onNavigateToTimer()
                block.record != null -> onEditRecord(block.record)
            }
        } else {
            interactionState = ScheduleInteractionState.BlockSelected(block)
        }
    }

    fun shiftBlockIntoDraft(block: ScheduleGridBlock, deltaMinutes: Int) {
        if (!block.canDragMoveOrResize || block.crossesMidnightFromPrev || block.crossesMidnightToNext) return
        val baseStart = (interactionState as? ScheduleInteractionState.BlockDraftTransform)?.takeIf {
            it.original.recordId == block.recordId
        }?.draftStartMin ?: block.startMinute
        val baseEnd = (interactionState as? ScheduleInteractionState.BlockDraftTransform)?.takeIf {
            it.original.recordId == block.recordId
        }?.draftEndMin ?: block.endMinute
        val (s, e) = ScheduleGridEngine.snapMoveRange(baseStart, baseEnd, deltaMinutes)
        interactionState = ScheduleInteractionState.BlockDraftTransform(
            original = block,
            targetDateLocal = block.dateLocal,
            draftStartMin = s,
            draftEndMin = e
        )
    }

    Column(
        modifier = if (embeddedInScrollContainer) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxSize()
        },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. Header xanh đặc trưng gọn tối đa
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (showBackButton) 18.dp else 16.dp))
                .background(Brush.linearGradient(listOf(WalletMintDark, WalletMintMid, WalletMintBright)))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showBackButton) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .semantics { contentDescription = "Đóng lịch trình" }
                            .walletClickable(onClickLabel = "Đóng lịch trình", onClick = onBackClick)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(WalletGlyph.BACK, color = Color.White, size = 16.dp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .semantics { contentDescription = "Lùi thời gian" }
                        .walletClickable(onClickLabel = "Lùi thời gian") {
                            onSelectDate(stepScheduleDate(selectedDate, viewMode, -1).toString())
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = formatScheduleRangeHeading(selectedDate, today, viewMode),
                            fontSize = if (viewMode == ScheduleViewMode.WEEK) 12.5.sp else 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (selectedDate != today) {
                            WalletPill(
                                onClickLabel = "Về hôm nay",
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                onClick = { onSelectDate(today.toString()) }
                            ) {
                                Text(
                                    text = "Hôm nay",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            append("TT ${TimeRange.formatDuration(headerActualMinutes)}")
                            append(" · KH ${TimeRange.formatDuration(headerPlannedMinutes)}")
                            if (headerUnconfirmed > 0) append(" · Chờ $headerUnconfirmed")
                        },
                        fontSize = 10.5.sp,
                        color = Color(0xFFD6EFE3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .semantics { contentDescription = "Tiến thời gian" }
                        .walletClickable(onClickLabel = "Tiến thời gian") {
                            onSelectDate(stepScheduleDate(selectedDate, viewMode, +1).toString())
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("›", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Box {
                    val hasActiveFilter = showSearchAndFilter || !categoryFilter.isNullOrBlank() || searchQuery.isNotBlank() || showLegend
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (hasActiveFilter) Color.White else Color.White.copy(alpha = 0.16f))
                            .semantics { contentDescription = "Tùy chọn lịch trình" }
                            .walletClickable(onClickLabel = "Tùy chọn lịch trình") {
                                showCalendarMenu = true
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!categoryFilter.isNullOrBlank() || searchQuery.isNotBlank()) "⋮●" else "⋮",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (hasActiveFilter) WalletMintDark else Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    DropdownMenu(
                        expanded = showCalendarMenu,
                        onDismissRequest = { showCalendarMenu = false },
                        containerColor = WalletCardBg,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "＋ Ghi mới hoạt động",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMintAccent
                                )
                            },
                            onClick = {
                                showCalendarMenu = false
                                onAddForSlot(selectedDate.toString(), null, null, selectedDate.isAfter(today))
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (showSearchAndFilter) "✓ Ẩn tìm & lọc" else "Tìm & lọc danh mục",
                                    fontSize = 13.sp,
                                    color = WalletInk
                                )
                            },
                            onClick = {
                                showSearchAndFilter = !showSearchAndFilter
                                showCalendarMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (showLegend) "✓ Ẩn chú giải màu" else "Hiện chú giải 4 trạng thái",
                                    fontSize = 13.sp,
                                    color = WalletInk
                                )
                            },
                            onClick = {
                                showLegend = !showLegend
                                showCalendarMenu = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ScheduleViewMode.entries.forEach { mode ->
                    val isSelected = mode == viewMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .semantics { this.selected = isSelected }
                            .walletClickable(onClickLabel = "Chế độ ${mode.label}", role = Role.Tab) {
                                onSelectViewMode(mode)
                            }
                            .padding(vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) WalletMintDark else Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 2. Khay truy cập "Chưa gắn giờ" cố định ngay trên đầu khu vực lịch (không phụ thuộc vị trí cuộn!)
        PinnedScheduleUtilityStrip(
            untimedCount = untimedItems.size,
            isPlacingUntimed = interactionState is ScheduleInteractionState.UntimedPlacement,
            onOpenUntimedTray = { showUntimedSheet = true },
            onQuickCreateNew = {
                onAddForSlot(selectedDate.toString(), null, null, selectedDate.isAfter(today))
            }
        )

        if (showSearchAndFilter) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WalletCardBg,
                border = BorderStroke(1.dp, WalletStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm tên hoạt động hoặc danh mục…", fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WalletMint,
                            unfocusedBorderColor = WalletStroke
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterBadgePill(
                            label = "Tất cả",
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null }
                        )
                        TimeCategory.entries.forEach { cat ->
                            FilterBadgePill(
                                label = cat.displayName,
                                category = cat,
                                selected = categoryFilter == cat.key,
                                onClick = {
                                    categoryFilter = if (categoryFilter == cat.key) null else cat.key
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showLegend) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendStateChip(color = WalletMintAccent, label = "Đã thực hiện", solid = true)
                LegendStateChip(color = PlannedAccentBlue, label = "Dự kiến", solid = false)
                LegendStateChip(color = ActiveAccentCyan, label = "Đang diễn ra", solid = true, pulse = true)
                LegendStateChip(color = UnconfirmedAmber, label = "Chưa xác nhận", solid = false)
            }
        }

        // 3. Thanh thao tác ngữ cảnh luôn nằm trong tầm nhìn khi chọn khoảng trống / hoạt động / bản nháp dời-giãn / đặt giờ
        when (val active = interactionState) {
            ScheduleInteractionState.Idle -> Unit
            is ScheduleInteractionState.EmptySlotsSelected -> {
                if (mergedSelectedGroups.isNotEmpty() && viewMode != ScheduleViewMode.MONTH) {
                    SelectedSlotsActionBar(
                        slotCount = active.slots.size,
                        summaryText = ScheduleGridEngine.summarizeSelectedSlotGroups(mergedSelectedGroups),
                        onClear = { interactionState = ScheduleInteractionState.Idle },
                        onCreate = { openCreateForEmptyGroups(mergedSelectedGroups) }
                    )
                }
            }
            is ScheduleInteractionState.UntimedPlacement -> {
                val targetDate = active.targetDateLocal
                val sMin = active.startMinute
                val eMin = active.endMinute
                val conflicts = if (targetDate != null && sMin != null && eMin != null) {
                    ScheduleGridEngine.findConflictingBlocks(
                        candidateStartMin = sMin,
                        candidateEndMin = eMin,
                        excludeRecordId = active.record.id,
                        isPlannedCandidate = targetDate > todayDateLocal,
                        dayBlocks = blocksByDate[targetDate].orEmpty()
                    )
                } else null
                UntimedPlacementActionBar(
                    record = active.record,
                    targetDateLocal = targetDate,
                    startMinute = sMin,
                    endMinute = eMin,
                    conflicts = conflicts,
                    onCancel = { interactionState = ScheduleInteractionState.Idle },
                    onConfirmPlace = {
                        if (targetDate != null && sMin != null && eMin != null) {
                            val recId = active.record.id
                            val st = TimeRange.formatHm(sMin)
                            val en = ScheduleGridEngine.formatHmEnd(eMin)
                            interactionState = ScheduleInteractionState.Idle
                            onPlaceUntimedOnSlot(recId, targetDate, st, en)
                        }
                    }
                )
            }
            is ScheduleInteractionState.BlockSelected -> {
                val blk = active.block
                SelectedBlockActionBar(
                    block = blk,
                    onEdit = {
                        interactionState = ScheduleInteractionState.Idle
                        if (blk.isActiveTimer) onNavigateToTimer()
                        else blk.record?.let(onEditRecord)
                    },
                    onNudgeEarlier = { shiftBlockIntoDraft(blk, -15) },
                    onNudgeLater = { shiftBlockIntoDraft(blk, +15) },
                    onConfirm = blk.record?.let { rec ->
                        if (blk.visualState == ScheduleBlockVisualState.UNCONFIRMED) {
                            {
                                interactionState = ScheduleInteractionState.Idle
                                onConfirmPlannedOrActiveRecord(rec)
                            }
                        } else null
                    },
                    onDelete = blk.record?.let { rec ->
                        { blockPendingDelete = rec }
                    },
                    onDismiss = { interactionState = ScheduleInteractionState.Idle }
                )
            }
            is ScheduleInteractionState.BlockDraftTransform -> {
                val conflictCheck = remember(active, blocksByDate) {
                    ScheduleGridEngine.findConflictingBlocks(
                        candidateStartMin = active.draftStartMin,
                        candidateEndMin = active.draftEndMin,
                        excludeRecordId = active.original.recordId,
                        isPlannedCandidate = active.original.visualState == ScheduleBlockVisualState.PLANNED,
                        dayBlocks = blocksByDate[active.targetDateLocal].orEmpty()
                    )
                }
                DraftTransformActionBar(
                    draft = active,
                    conflicts = conflictCheck,
                    onNudgeStart = { delta ->
                        val (s, e) = ScheduleGridEngine.snapResizeStartRange(active.draftStartMin, active.draftEndMin, delta)
                        interactionState = active.copy(draftStartMin = s, draftEndMin = e)
                    },
                    onNudgeEnd = { delta ->
                        val (s, e) = ScheduleGridEngine.snapResizeRange(active.draftStartMin, active.draftEndMin, delta)
                        interactionState = active.copy(draftStartMin = s, draftEndMin = e)
                    },
                    onCancel = { interactionState = ScheduleInteractionState.BlockSelected(active.original) },
                    onCommit = {
                        val orig = active.original
                        val d = active.targetDateLocal
                        val st = TimeRange.formatHm(active.draftStartMin)
                        val en = ScheduleGridEngine.formatHmEnd(active.draftEndMin)
                        interactionState = ScheduleInteractionState.Idle
                        onMoveOrResizeBlock(orig.recordId, d, st, en)
                    }
                )
            }
        }

        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val embeddedGridHeight = (screenHeightDp - 230).coerceIn(430, 590).dp
        val gridModifier = if (embeddedInScrollContainer) {
            Modifier.fillMaxWidth().height(embeddedGridHeight)
        } else {
            Modifier.fillMaxWidth().weight(1f)
        }

        when (viewMode) {
            ScheduleViewMode.DAY -> {
                val dayModel = multiDayModels.first()
                DayTimelineGrid(
                    dayModel = dayModel,
                    isToday = selectedDate == today,
                    nowMinute = nowMinute,
                    interactionState = interactionState,
                    modifier = gridModifier,
                    onTapEmptyAtMinute = { minute -> handleTapEmptyPosition(dayModel, minute) },
                    onDragSelectEmptyRange = { sMin, eMin ->
                        if (interactionState is ScheduleInteractionState.UntimedPlacement) {
                            val cur = interactionState as ScheduleInteractionState.UntimedPlacement
                            val (s, e) = ScheduleGridEngine.buildUntimedPlacementWindow(sMin, cur.record.durationMinutes)
                            interactionState = cur.copy(targetDateLocal = dayModel.dateLocal, startMinute = s, endMinute = e)
                        } else {
                            updateEmptySlotRangeExact(dayModel.dateLocal, sMin, eMin)
                        }
                    },
                    onResizeSelectedEmptySlot = { newStart, newEnd ->
                        updateEmptySlotRangeExact(dayModel.dateLocal, newStart, newEnd)
                    },
                    onQuickAddSelectedGroup = { g -> openCreateForEmptyGroups(listOf(g)) },
                    onClearSelection = { interactionState = ScheduleInteractionState.Idle },
                    onTapBlock = ::handleTapBlock,
                    onConfirmUnconfirmed = { record -> onConfirmPlannedOrActiveRecord(record) },
                    onDraftBlockChanged = { block, newStart, newEnd ->
                        interactionState = ScheduleInteractionState.BlockDraftTransform(
                            original = block,
                            targetDateLocal = block.dateLocal,
                            draftStartMin = newStart,
                            draftEndMin = newEnd
                        )
                    }
                )
            }
            ScheduleViewMode.THREE_DAYS -> {
                MultiDayTimelineGrid(
                    days = multiDayModels,
                    todayStr = today.toString(),
                    nowMinute = nowMinute,
                    hourHeight = 66.dp,
                    interactionState = interactionState,
                    modifier = gridModifier,
                    onSelectDay = { dateStr ->
                        onSelectDate(dateStr)
                        onSelectViewMode(ScheduleViewMode.DAY)
                    },
                    onTapCellAtMinute = { dayModel, minute -> handleTapEmptyPosition(dayModel, minute) },
                    onDragSelectEmptyRange = { dayModel, sMin, eMin ->
                        if (interactionState is ScheduleInteractionState.UntimedPlacement) {
                            val cur = interactionState as ScheduleInteractionState.UntimedPlacement
                            val (s, e) = ScheduleGridEngine.buildUntimedPlacementWindow(sMin, cur.record.durationMinutes)
                            interactionState = cur.copy(targetDateLocal = dayModel.dateLocal, startMinute = s, endMinute = e)
                        } else {
                            updateEmptySlotRangeExact(dayModel.dateLocal, sMin, eMin)
                        }
                    },
                    onTapBlock = ::handleTapBlock,
                    onDraftBlockMoved = { block, newStart, newEnd ->
                        interactionState = ScheduleInteractionState.BlockDraftTransform(
                            original = block,
                            targetDateLocal = block.dateLocal,
                            draftStartMin = newStart,
                            draftEndMin = newEnd
                        )
                    }
                )
            }
            ScheduleViewMode.WEEK -> {
                MultiDayTimelineGrid(
                    days = multiDayModels,
                    todayStr = today.toString(),
                    nowMinute = nowMinute,
                    hourHeight = 58.dp,
                    compactColumns = true,
                    interactionState = interactionState,
                    modifier = gridModifier,
                    onSelectDay = { dateStr ->
                        onSelectDate(dateStr)
                        onSelectViewMode(ScheduleViewMode.DAY)
                    },
                    onTapCellAtMinute = { dayModel, minute -> handleTapEmptyPosition(dayModel, minute) },
                    onDragSelectEmptyRange = { dayModel, sMin, eMin ->
                        if (interactionState is ScheduleInteractionState.UntimedPlacement) {
                            val cur = interactionState as ScheduleInteractionState.UntimedPlacement
                            val (s, e) = ScheduleGridEngine.buildUntimedPlacementWindow(sMin, cur.record.durationMinutes)
                            interactionState = cur.copy(targetDateLocal = dayModel.dateLocal, startMinute = s, endMinute = e)
                        } else {
                            updateEmptySlotRangeExact(dayModel.dateLocal, sMin, eMin)
                        }
                    },
                    onTapBlock = ::handleTapBlock,
                    onDraftBlockMoved = { block, newStart, newEnd ->
                        interactionState = ScheduleInteractionState.BlockDraftTransform(
                            original = block,
                            targetDateLocal = block.dateLocal,
                            draftStartMin = newStart,
                            draftEndMin = newEnd
                        )
                    }
                )
            }
            ScheduleViewMode.MONTH -> {
                MonthOverviewGrid(
                    cells = monthCells,
                    selectedDateLocal = selectedDate.toString(),
                    selectedDayModel = selectedDayModel,
                    today = today,
                    selectedBlockId = (interactionState as? ScheduleInteractionState.BlockSelected)?.block?.recordId,
                    modifier = if (embeddedInScrollContainer) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
                    scrollVertically = !embeddedInScrollContainer,
                    onSelectDate = { dStr -> onSelectDate(dStr) },
                    onOpenDayTimeline = { dStr ->
                        onSelectDate(dStr)
                        onSelectViewMode(ScheduleViewMode.DAY)
                    },
                    onAddOnDate = { dStr ->
                        val target = runCatching { LocalDate.parse(dStr) }.getOrDefault(today)
                        onAddForSlot(dStr, null, null, target.isAfter(today))
                    },
                    onSelectBlock = ::handleTapBlock
                )
            }
        }
    }

    if (showUntimedSheet) {
        UntimedRecordsSheet(
            records = untimedItems,
            onDismiss = { showUntimedSheet = false },
            onPlaceOnSchedule = { rec ->
                showUntimedSheet = false
                if (viewMode == ScheduleViewMode.MONTH) {
                    onSelectDate(rec.date)
                    onSelectViewMode(ScheduleViewMode.DAY)
                }
                interactionState = ScheduleInteractionState.UntimedPlacement(record = rec)
            },
            onEditRecord = { rec ->
                showUntimedSheet = false
                onEditRecord(rec)
            },
            onQuickLogDuration = {
                showUntimedSheet = false
                onAddForSlot(selectedDate.toString(), null, null, selectedDate.isAfter(today))
            }
        )
    }

    blockPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { blockPendingDelete = null },
            containerColor = WalletCardBg,
            title = {
                Text("Xóa hoạt động?", fontWeight = FontWeight.ExtraBold, color = WalletInk)
            },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa \"${target.title}\" khỏi sổ cái thời gian?",
                    fontSize = 12.5.sp,
                    color = WalletMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        blockPendingDelete = null
                        interactionState = ScheduleInteractionState.Idle
                        onDeleteRecord(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletDangerSolid, contentColor = Color.White)
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockPendingDelete = null }) {
                    Text("Hủy", color = WalletMuted)
                }
            }
        )
    }
}

@Composable
private fun PinnedScheduleUtilityStrip(
    untimedCount: Int,
    isPlacingUntimed: Boolean,
    onOpenUntimedTray: () -> Unit,
    onQuickCreateNew: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPlacingUntimed) WalletMintSoft else WalletCardBg)
                .border(1.dp, if (isPlacingUntimed) WalletMint else WalletStroke, RoundedCornerShape(12.dp))
                .walletClickable(onClickLabel = "Mở khay hoạt động chưa gắn giờ") { onOpenUntimedTray() }
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (untimedCount == 0) WalletMuted.copy(alpha = 0.4f) else WalletMint)
            )
            Text(
                text = "Chưa gắn giờ ($untimedCount)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WalletInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Đặt giờ ›",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WalletMintAccent,
                maxLines = 1,
                softWrap = false
            )
        }

        Box(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(WalletMintTint)
                .border(1.dp, WalletMint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .walletClickable(onClickLabel = "Ghi mới hoạt động vào ngày đang xem", onClick = onQuickCreateNew)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ Ghi mới",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WalletMintDark,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SelectedSlotsActionBar(
    slotCount: Int,
    summaryText: String,
    onClear: () -> Unit,
    onCreate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WalletMintTint,
        border = BorderStroke(1.5.dp, WalletMint),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (slotCount <= 1) "Khoảng trống đã chọn" else "Đã chọn $slotCount khối giờ trống",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletMintAccent,
                    maxLines = 1
                )
                Text(
                    text = summaryText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WalletInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(WalletCardBg)
                    .border(1.dp, WalletStroke, RoundedCornerShape(10.dp))
                    .walletClickable(onClickLabel = "Hủy chọn khoảng trống", onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Hủy", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(WalletMint)
                    .walletClickable(onClickLabel = "Thêm hoạt động vào khung giờ đã chọn", onClick = onCreate)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Thêm giờ này", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedBlockActionBar(
    block: ScheduleGridBlock,
    onEdit: () -> Unit,
    onNudgeEarlier: () -> Unit,
    onNudgeLater: () -> Unit,
    onConfirm: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WalletCardBg,
        border = BorderStroke(1.5.dp, WalletMint),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ScheduleGridEngine.formatHmWindow(block.startMinute, block.endMinute) +
                            if (block.canDragMoveOrResize && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                                " · Kéo thân để dời, kéo mép để chỉnh"
                            } else "",
                        fontSize = 11.sp,
                        color = WalletMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StateMicroPill(block.visualState)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WalletSoftNeutral)
                        .walletClickable(onClickLabel = "Bỏ chọn hoạt động", onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("✕", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionChipButton(
                    label = if (block.isActiveTimer) "Mở đồng hồ" else "Sửa",
                    bg = WalletMintTint,
                    fg = WalletMintDark,
                    onClick = onEdit
                )
                if (block.canDragMoveOrResize && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                    ActionChipButton(label = "▲ Sớm 15p", bg = WalletSoftNeutral, fg = WalletInk, onClick = onNudgeEarlier)
                    ActionChipButton(label = "▼ Muộn 15p", bg = WalletSoftNeutral, fg = WalletInk, onClick = onNudgeLater)
                }
                if (onConfirm != null) {
                    ActionChipButton(label = "✓ Xác nhận", bg = UnconfirmedAmber, fg = Color.White, onClick = onConfirm)
                }
                if (onDelete != null) {
                    ActionChipButton(label = "Xóa", bg = WalletDangerBg, fg = WalletDangerText, onClick = onDelete)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftTransformActionBar(
    draft: ScheduleInteractionState.BlockDraftTransform,
    conflicts: ScheduleConflictCheck,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit
) {
    val hasConflict = conflicts.hasBlockingOrWarningConflict
    val borderColor = if (hasConflict) UnconfirmedAmber else WalletMint
    val bgColor = if (hasConflict) WalletWarningBg else WalletMintTint

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${draft.original.title} (Chưa lưu)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${ScheduleGridEngine.formatHmWindow(draft.original.startMinute, draft.original.endMinute)} → ${ScheduleGridEngine.formatHmWindow(draft.draftStartMin, draft.draftEndMin)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasConflict) WalletWarningText else WalletMintDark
                    )
                }
                StateMicroPill(draft.original.visualState)
            }
            if (hasConflict) {
                Text(
                    text = "⚠ ${conflicts.summaryVi()} — Hãy dời sang giờ trống, xử lý trùng hoặc bấm Hủy.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletWarningText,
                    lineHeight = 15.sp
                )
            } else if (conflicts.actualOverlapNotes.isNotEmpty()) {
                Text(
                    text = "Kế hoạch dự kiến đặt song song với \"${conflicts.actualOverlapNotes.first().title}\" (không tự hoàn thành thực tế).",
                    fontSize = 11.sp,
                    color = WalletInfoText
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionChipButton(label = "Đầu -15p", bg = WalletCardBg, fg = WalletInk) { onNudgeStart(-15) }
                ActionChipButton(label = "Đầu +15p", bg = WalletCardBg, fg = WalletInk) { onNudgeStart(+15) }
                ActionChipButton(label = "Cuối -15p", bg = WalletCardBg, fg = WalletInk) { onNudgeEnd(-15) }
                ActionChipButton(label = "Cuối +15p", bg = WalletCardBg, fg = WalletInk) { onNudgeEnd(+15) }
                ActionChipButton(label = "Hủy", bg = WalletCardBg, fg = WalletDangerText, onClick = onCancel)
                ActionChipButton(
                    label = if (conflicts.hardConflicts.isNotEmpty()) "Xử lý trùng…" else "✓ Lưu thay đổi",
                    bg = if (hasConflict) UnconfirmedAmber else WalletMint,
                    fg = Color.White,
                    onClick = onCommit
                )
            }
        }
    }
}

@Composable
private fun UntimedPlacementActionBar(
    record: LedgerRecord,
    targetDateLocal: String?,
    startMinute: Int?,
    endMinute: Int?,
    conflicts: ScheduleConflictCheck?,
    onCancel: () -> Unit,
    onConfirmPlace: () -> Unit
) {
    val hasRange = targetDateLocal != null && startMinute != null && endMinute != null
    val hasConflict = conflicts?.hasBlockingOrWarningConflict == true
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (hasConflict) WalletWarningBg else WalletInfoBg,
        border = BorderStroke(1.5.dp, if (hasConflict) UnconfirmedAmber else PlannedAccentBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Đặt vào lịch: \"${record.title}\" (${TimeRange.formatDuration(record.durationMinutes)})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        !hasRange -> "Chạm vào khoảng giờ trống trên lịch để chọn điểm bắt đầu."
                        hasConflict -> "⚠ ${conflicts!!.summaryVi()} (${ScheduleGridEngine.formatHmWindow(startMinute!!, endMinute!!)})"
                        else -> "Khung dự kiến đặt: $targetDateLocal · ${ScheduleGridEngine.formatHmWindow(startMinute!!, endMinute!!)}"
                    },
                    fontSize = 11.sp,
                    color = if (hasConflict) WalletWarningText else WalletInfoText
                )
            }
            ActionChipButton(label = "Hủy", bg = WalletCardBg, fg = WalletInk, onClick = onCancel)
            if (hasRange) {
                ActionChipButton(
                    label = if (hasConflict) "Xử lý trùng…" else "✓ Đặt vào giờ này",
                    bg = if (hasConflict) UnconfirmedAmber else WalletMint,
                    fg = Color.White,
                    onClick = onConfirmPlace
                )
            }
        }
    }
}

@Composable
private fun ActionChipButton(
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, WalletStroke.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
            .walletClickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DayTimelineGrid(
    dayModel: DayScheduleModel,
    isToday: Boolean,
    nowMinute: Int,
    interactionState: ScheduleInteractionState,
    modifier: Modifier = Modifier,
    onTapEmptyAtMinute: (Int) -> Unit,
    onDragSelectEmptyRange: (Int, Int) -> Unit,
    onResizeSelectedEmptySlot: (Int, Int) -> Unit,
    onQuickAddSelectedGroup: (SelectedSlotGroup) -> Unit,
    onClearSelection: () -> Unit,
    onTapBlock: (ScheduleGridBlock) -> Unit,
    onConfirmUnconfirmed: (LedgerRecord) -> Unit,
    onDraftBlockChanged: (ScheduleGridBlock, Int, Int) -> Unit
) {
    val hourHeight = 82.dp
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    val minuteHeightPx = hourHeightPx / 60f
    val scrollState = rememberScrollState()

    LaunchedEffect(dayModel.dateLocal) {
        val initialHour = when {
            isToday -> (nowMinute / 60 - 1).coerceIn(0, 17)
            dayModel.timedBlocks.isNotEmpty() -> (dayModel.timedBlocks.minOf { it.startMinute } / 60 - 1).coerceIn(0, 17)
            else -> 7
        }
        scrollState.scrollTo((initialHour * hourHeightPx).roundToInt())
    }

    // Trạng thái kéo live trên lưới
    var liveEmptyDragRange by remember(dayModel.dateLocal) { mutableStateOf<Pair<Int, Int>?>(null) }
    var emptyDragAnchorMin by remember(dayModel.dateLocal) { mutableIntStateOf(0) }
    var emptyDragAccumY by remember(dayModel.dateLocal) { mutableFloatStateOf(0f) }

    var draggingBlockId by remember { mutableStateOf<String?>(null) }
    var dragPreviewStartMin by remember { mutableIntStateOf(0) }
    var dragPreviewEndMin by remember { mutableIntStateOf(0) }

    val latestOnTapEmptyAtMinute by rememberUpdatedState(onTapEmptyAtMinute)
    val latestOnDragSelectEmptyRange by rememberUpdatedState(onDragSelectEmptyRange)
    val latestOnResizeSelectedEmptySlot by rememberUpdatedState(onResizeSelectedEmptySlot)
    val latestOnDraftBlockChanged by rememberUpdatedState(onDraftBlockChanged)

    val selectedEmptyOnDay = remember(interactionState, dayModel.dateLocal) {
        val slots = (interactionState as? ScheduleInteractionState.EmptySlotsSelected)?.slots.orEmpty()
            .filter { it.dateLocal == dayModel.dateLocal }
        ScheduleGridEngine.mergeSelectedEmptySlots(slots)
    }
    val placementPreviewOnDay = remember(interactionState, dayModel.dateLocal) {
        (interactionState as? ScheduleInteractionState.UntimedPlacement)?.takeIf {
            it.targetDateLocal == dayModel.dateLocal && it.startMinute != null && it.endMinute != null
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = WalletCardBg,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.width(54.dp)) {
                for (h in 0 until 24) {
                    Box(
                        modifier = Modifier.height(hourHeight).fillMaxWidth().padding(end = 6.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = "%02d:00".format(h),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = WalletMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * 24)
                    .padding(end = 8.dp)
                    .pointerInput(dayModel.dateLocal) {
                        detectTapGestures(
                            onTap = { offset ->
                                val tappedMinute = ((offset.y / minuteHeightPx).roundToInt()).coerceIn(0, 1439)
                                latestOnTapEmptyAtMinute(tappedMinute)
                            }
                        )
                    }
                    .pointerInput(dayModel.dateLocal) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                val startMin = ((startOffset.y / minuteHeightPx).roundToInt()).coerceIn(0, 1425)
                                emptyDragAnchorMin = startMin
                                emptyDragAccumY = 0f
                                val (s, e) = ScheduleGridEngine.snapEmptyDragRange(startMin, startMin + 30)
                                liveEmptyDragRange = s to e
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                emptyDragAccumY += dragAmount.y
                                val curMin = (emptyDragAnchorMin + (emptyDragAccumY / minuteHeightPx).roundToInt()).coerceIn(0, 1440)
                                liveEmptyDragRange = ScheduleGridEngine.snapEmptyDragRange(emptyDragAnchorMin, curMin)
                            },
                            onDragEnd = {
                                val chosen = liveEmptyDragRange
                                liveEmptyDragRange = null
                                if (chosen != null) {
                                    latestOnDragSelectEmptyRange(chosen.first, chosen.second)
                                }
                            },
                            onDragCancel = {
                                liveEmptyDragRange = null
                            }
                        )
                    }
            ) {
                val canvasWidthDp = maxWidth

                // 1. Nền kẻ giờ 00:00 -> 24:00 (trạng thái rỗi sạch, chạm hoặc nhấn giữ kéo bất kỳ đâu)
                Column(modifier = Modifier.fillMaxSize()) {
                    for (h in 0 until 24) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(hourHeight)
                        ) {
                            HorizontalDivider(color = WalletDivider, thickness = 1.dp)
                            HorizontalDivider(
                                color = WalletDivider.copy(alpha = 0.45f),
                                thickness = 0.75.dp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // 2. Gợi ý khoảng trống nổi bật ban ngày khi chưa chọn
                if (interactionState == ScheduleInteractionState.Idle && liveEmptyDragRange == null) {
                    dayModel.freeGaps
                        .filter { it.durationMinutes >= 60 && it.endMinute > 6 * 60 && it.startMinute < 22 * 60 }
                        .forEach { gap ->
                            val visStart = max(gap.startMinute, 6 * 60)
                            val visEnd = min(gap.endMinute, 22 * 60)
                            if (visEnd - visStart >= 60) {
                                val topDp = (visStart / 60f) * hourHeight.value
                                val heightDp = ((visEnd - visStart) / 60f) * hourHeight.value
                                if (heightDp >= 52f) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 4.dp, y = (topDp + 2f).dp)
                                            .width(canvasWidthDp - 8.dp)
                                            .height(44.dp)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "Trống ${TimeRange.formatHm(visStart)}–${TimeRange.formatHm(visEnd)} · chạm hoặc nhấn giữ kéo chọn",
                                            fontSize = 10.5.sp,
                                            color = WalletMuted.copy(alpha = 0.72f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                }

                // 3. Khối preview kéo khoảng trống trực tiếp (15p snap)
                liveEmptyDragRange?.let { (sMin, eMin) ->
                    val topDp = (sMin / 60f) * hourHeight.value
                    val hDp = max(24f, ((eMin - sMin) / 60f) * hourHeight.value)
                    Box(
                        modifier = Modifier
                            .offset(x = 3.dp, y = topDp.dp)
                            .width(canvasWidthDp - 6.dp)
                            .height(hDp.dp)
                            .zIndex(8f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WalletMintSoft.copy(alpha = 0.92f))
                            .border(2.dp, WalletMint, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Chọn: ${ScheduleGridEngine.formatHmWindow(sMin, eMin)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletMintDark
                        )
                    }
                }

                // 4. Vùng chọn khoảng trống đã chốt + tay nắm giãn trên/dưới + nút "+ Thêm" ngay tại chỗ
                selectedEmptyOnDay.forEachIndexed { idx, group ->
                    val topDp = (group.startMinute / 60f) * hourHeight.value
                    val hDp = max(36f, (group.durationMinutes / 60f) * hourHeight.value)
                    val latestGroup by rememberUpdatedState(group)
                    var slotTopAccumY by remember(dayModel.dateLocal, idx) { mutableFloatStateOf(0f) }
                    var slotBotAccumY by remember(dayModel.dateLocal, idx) { mutableFloatStateOf(0f) }
                    var slotOrigStart by remember(dayModel.dateLocal, idx) { mutableIntStateOf(group.startMinute) }
                    var slotOrigEnd by remember(dayModel.dateLocal, idx) { mutableIntStateOf(group.endMinute) }
                    Box(
                        modifier = Modifier
                            .offset(x = 3.dp, y = topDp.dp)
                            .width(canvasWidthDp - 6.dp)
                            .height(hDp.dp)
                            .zIndex(6f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WalletMintSoft)
                            .border(1.8.dp, WalletMint, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = ScheduleGridEngine.formatHmWindow(group.startMinute, group.endMinute),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletMintDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WalletCardBg)
                                        .border(1.dp, WalletStroke, RoundedCornerShape(8.dp))
                                        .walletClickable(onClickLabel = "Bỏ chọn khung giờ") { onClearSelection() }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WalletMint)
                                        .walletClickable(onClickLabel = "Thêm hoạt động vào khung giờ này") {
                                            onQuickAddSelectedGroup(group)
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Thêm", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                        }
                        // Tay nắm mép trên/dưới cho vùng chọn khoảng trống (giữ nguyên key kéo để không bị hủy khi group recomposes)
                        ResizeEdgeHandle(
                            isTopEdge = true,
                            tint = WalletMint,
                            dragKey = "empty_top_${dayModel.dateLocal}_$idx",
                            modifier = Modifier.align(Alignment.TopCenter),
                            onDragStart = {
                                slotTopAccumY = 0f
                                slotOrigStart = latestGroup.startMinute
                                slotOrigEnd = latestGroup.endMinute
                            },
                            onDragDelta = { dy ->
                                slotTopAccumY += dy
                                val dMin = (slotTopAccumY / minuteHeightPx).roundToInt()
                                val (s, e) = ScheduleGridEngine.snapResizeStartRange(slotOrigStart, slotOrigEnd, dMin)
                                latestOnResizeSelectedEmptySlot(s, e)
                            },
                            onDragEnd = {},
                            onDragCancel = {}
                        )
                        ResizeEdgeHandle(
                            isTopEdge = false,
                            tint = WalletMint,
                            dragKey = "empty_bot_${dayModel.dateLocal}_$idx",
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onDragStart = {
                                slotBotAccumY = 0f
                                slotOrigStart = latestGroup.startMinute
                                slotOrigEnd = latestGroup.endMinute
                            },
                            onDragDelta = { dy ->
                                slotBotAccumY += dy
                                val dMin = (slotBotAccumY / minuteHeightPx).roundToInt()
                                val (s, e) = ScheduleGridEngine.snapResizeRange(slotOrigStart, slotOrigEnd, dMin)
                                latestOnResizeSelectedEmptySlot(s, e)
                            },
                            onDragEnd = {},
                            onDragCancel = {}
                        )
                    }
                }

                // 5. Khối preview đặt hoạt động "Chưa gắn giờ" vào lịch
                placementPreviewOnDay?.let { preview ->
                    val sMin = preview.startMinute!!
                    val eMin = preview.endMinute!!
                    val conflicts = ScheduleGridEngine.findConflictingBlocks(
                        candidateStartMin = sMin,
                        candidateEndMin = eMin,
                        excludeRecordId = preview.record.id,
                        isPlannedCandidate = false,
                        dayBlocks = dayModel.timedBlocks
                    )
                    val topDp = (sMin / 60f) * hourHeight.value
                    val hDp = max(28f, ((eMin - sMin) / 60f) * hourHeight.value)
                    Box(
                        modifier = Modifier
                            .offset(x = 3.dp, y = topDp.dp)
                            .width(canvasWidthDp - 6.dp)
                            .height(hDp.dp)
                            .zIndex(9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (conflicts.hasBlockingOrWarningConflict) WalletWarningBg else WalletInfoBg)
                            .border(
                                2.dp,
                                if (conflicts.hasBlockingOrWarningConflict) UnconfirmedAmber else PlannedAccentBlue,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "${preview.record.title} · ${ScheduleGridEngine.formatHmWindow(sMin, eMin)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (conflicts.hasBlockingOrWarningConflict) WalletWarningText else WalletInfoText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 6. Khối hoạt động (Đã thực hiện, Dự kiến, Đang diễn ra, Chưa xác nhận) + bản nháp Dời/Giãn + kiểm tra xung đột trực tiếp
                val activeDraft = (interactionState as? ScheduleInteractionState.BlockDraftTransform)?.takeIf {
                    it.targetDateLocal == dayModel.dateLocal
                }
                val selectedBlockId = (interactionState as? ScheduleInteractionState.BlockSelected)?.block?.recordId

                dayModel.timedBlocks.forEach { block ->
                    val isDraggingThis = draggingBlockId == block.recordId
                    val isDraftedThis = activeDraft?.original?.recordId == block.recordId
                    val isSelectedThis = selectedBlockId == block.recordId || isDraftedThis || isDraggingThis

                    val effStart = when {
                        isDraggingThis -> dragPreviewStartMin
                        isDraftedThis -> activeDraft!!.draftStartMin
                        else -> block.startMinute
                    }
                    val effEnd = when {
                        isDraggingThis -> dragPreviewEndMin
                        isDraftedThis -> activeDraft!!.draftEndMin
                        else -> block.endMinute
                    }
                    val spanMinutes = (effEnd - effStart).coerceAtLeast(15)

                    val liveConflict = if (isDraggingThis || isDraftedThis) {
                        ScheduleGridEngine.findConflictingBlocks(
                            candidateStartMin = effStart,
                            candidateEndMin = effEnd,
                            excludeRecordId = block.recordId,
                            isPlannedCandidate = block.visualState == ScheduleBlockVisualState.PLANNED,
                            dayBlocks = dayModel.timedBlocks
                        )
                    } else null

                    val laneFraction = 1f / block.laneCount.coerceAtLeast(1)
                    val laneWidthDp = (canvasWidthDp - 4.dp) * laneFraction
                    val laneOffsetDp = laneWidthDp * block.laneIndex

                    // Nếu đang dời/kéo, hiển thị bóng mờ vị trí gốc để người dùng đối chiếu trực quan và Hủy dễ dàng
                    if ((isDraggingThis || isDraftedThis) && (effStart != block.startMinute || effEnd != block.endMinute)) {
                        val origTopDp = (block.startMinute / 60f) * hourHeight.value
                        val origHeightDp = max(20f, ((block.endMinute - block.startMinute) / 60f) * hourHeight.value)
                        Box(
                            modifier = Modifier
                                .offset(x = laneOffsetDp + 2.dp, y = origTopDp.dp)
                                .width((laneWidthDp - 4.dp).coerceAtLeast(48.dp))
                                .height(origHeightDp.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletSoftNeutral.copy(alpha = 0.45f))
                                .border(1.dp, WalletMuted.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        )
                    }

                    val topDp = (effStart / 60f) * hourHeight.value
                    val blockHeightDp = max(22f, (spanMinutes / 60f) * hourHeight.value)

                    val latestBlock by rememberUpdatedState(block)
                    val latestEffRange by rememberUpdatedState(effStart to effEnd)
                    var accumBodyDragY by remember(block.recordId) { mutableFloatStateOf(0f) }
                    var accumTopDragY by remember(block.recordId) { mutableFloatStateOf(0f) }
                    var accumBotDragY by remember(block.recordId) { mutableFloatStateOf(0f) }
                    var dragOrigStartMin by remember(block.recordId) { mutableIntStateOf(block.startMinute) }
                    var dragOrigEndMin by remember(block.recordId) { mutableIntStateOf(block.endMinute) }

                    ScheduleEventCard(
                        block = block,
                        effectiveStartMin = effStart,
                        effectiveEndMin = effEnd,
                        isSelected = isSelectedThis,
                        isDraggingOrDraft = isDraggingThis || isDraftedThis,
                        hasConflictWarning = liveConflict?.hasBlockingOrWarningConflict == true,
                        draggingLabel = if (isDraggingThis || isDraftedThis) {
                            ScheduleGridEngine.formatHmWindow(effStart, effEnd)
                        } else null,
                        blockHeightDp = blockHeightDp.dp,
                        modifier = Modifier
                            .offset(x = if (isDraggingThis || isDraftedThis) 2.dp else laneOffsetDp + 2.dp, y = topDp.dp)
                            .width(
                                if (isDraggingThis || isDraftedThis) (canvasWidthDp - 4.dp).coerceAtLeast(60.dp)
                                else (laneWidthDp - 4.dp).coerceAtLeast(48.dp)
                            )
                            .height(blockHeightDp.dp)
                            .zIndex(if (isSelectedThis) 12f else 1f)
                            .then(
                                if (block.canDragMoveOrResize && block.record != null && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                                    Modifier.pointerInput(block.recordId, block.startMinute, block.endMinute) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                val (baseS, baseE) = latestEffRange
                                                accumBodyDragY = 0f
                                                dragOrigStartMin = baseS
                                                dragOrigEndMin = baseE
                                                draggingBlockId = latestBlock.recordId
                                                dragPreviewStartMin = baseS
                                                dragPreviewEndMin = baseE
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                accumBodyDragY += dragAmount.y
                                                val deltaMin = (accumBodyDragY / minuteHeightPx).roundToInt()
                                                val (s, e) = ScheduleGridEngine.snapMoveRange(dragOrigStartMin, dragOrigEndMin, deltaMin)
                                                dragPreviewStartMin = s
                                                dragPreviewEndMin = e
                                            },
                                            onDragEnd = {
                                                val s = dragPreviewStartMin
                                                val e = dragPreviewEndMin
                                                draggingBlockId = null
                                                if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                                    latestOnDraftBlockChanged(latestBlock, s, e)
                                                }
                                            },
                                            onDragCancel = {
                                                draggingBlockId = null
                                            }
                                        )
                                    }
                                } else Modifier
                            ),
                        onClick = { onTapBlock(block) },
                        onQuickConfirm = block.record?.let { rec ->
                            if (block.visualState == ScheduleBlockVisualState.UNCONFIRMED) {
                                { onConfirmUnconfirmed(rec) }
                            } else null
                        },
                        onTopEdgeDragStart = {
                            val (baseS, baseE) = latestEffRange
                            accumTopDragY = 0f
                            dragOrigStartMin = baseS
                            dragOrigEndMin = baseE
                            draggingBlockId = latestBlock.recordId
                            dragPreviewStartMin = baseS
                            dragPreviewEndMin = baseE
                        },
                        onTopEdgeDragDelta = { deltaY ->
                            accumTopDragY += deltaY
                            val dMin = (accumTopDragY / minuteHeightPx).roundToInt()
                            val (s, e) = ScheduleGridEngine.snapResizeStartRange(dragOrigStartMin, dragOrigEndMin, dMin)
                            dragPreviewStartMin = s
                            dragPreviewEndMin = e
                        },
                        onTopEdgeDragEnd = {
                            val s = dragPreviewStartMin
                            val e = dragPreviewEndMin
                            draggingBlockId = null
                            if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                latestOnDraftBlockChanged(latestBlock, s, e)
                            }
                        },
                        onTopEdgeDragCancel = { draggingBlockId = null },
                        onBottomEdgeDragStart = {
                            val (baseS, baseE) = latestEffRange
                            accumBotDragY = 0f
                            dragOrigStartMin = baseS
                            dragOrigEndMin = baseE
                            draggingBlockId = latestBlock.recordId
                            dragPreviewStartMin = baseS
                            dragPreviewEndMin = baseE
                        },
                        onBottomEdgeDragDelta = { deltaY ->
                            accumBotDragY += deltaY
                            val deltaEndMin = (accumBotDragY / minuteHeightPx).roundToInt()
                            val (s, e) = ScheduleGridEngine.snapResizeRange(dragOrigStartMin, dragOrigEndMin, deltaEndMin)
                            dragPreviewStartMin = s
                            dragPreviewEndMin = e
                        },
                        onBottomEdgeDragEnd = {
                            val s = dragPreviewStartMin
                            val e = dragPreviewEndMin
                            draggingBlockId = null
                            if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                latestOnDraftBlockChanged(latestBlock, s, e)
                            }
                        },
                        onBottomEdgeDragCancel = { draggingBlockId = null }
                    )
                }

                if (isToday) {
                    val nowY = (nowMinute / 60f) * hourHeight.value
                    Row(
                        modifier = Modifier
                            .offset(y = (nowY - 9f).dp)
                            .fillMaxWidth()
                            .zIndex(20f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(NowIndicatorCoral)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NowIndicatorCoral)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = TimeRange.formatHm(nowMinute),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(NowIndicatorCoral)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeEdgeHandle(
    isTopEdge: Boolean,
    tint: Color,
    dragKey: Any = Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .zIndex(2f)
            .pointerInput(dragKey) {
                detectVerticalDragGestures(
                    onDragStart = { latestOnDragStart() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        latestOnDragDelta(dragAmount)
                    },
                    onDragEnd = { latestOnDragEnd() },
                    onDragCancel = { latestOnDragCancel() }
                )
            },
        contentAlignment = if (isTopEdge) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .width(34.dp)
                .height(3.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tint)
        )
    }
}

@Composable
private fun ScheduleEventCard(
    block: ScheduleGridBlock,
    effectiveStartMin: Int,
    effectiveEndMin: Int,
    isSelected: Boolean,
    isDraggingOrDraft: Boolean,
    hasConflictWarning: Boolean,
    draggingLabel: String?,
    blockHeightDp: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onQuickConfirm: (() -> Unit)? = null,
    onTopEdgeDragStart: () -> Unit = {},
    onTopEdgeDragDelta: (Float) -> Unit = {},
    onTopEdgeDragEnd: () -> Unit = {},
    onTopEdgeDragCancel: () -> Unit = {},
    onBottomEdgeDragStart: () -> Unit = {},
    onBottomEdgeDragDelta: (Float) -> Unit = {},
    onBottomEdgeDragEnd: () -> Unit = {},
    onBottomEdgeDragCancel: () -> Unit = {}
) {
    val catColor = Color(block.category.colorHex)
    val (bgColor, accentColor, isDashed) = when (block.visualState) {
        ScheduleBlockVisualState.COMPLETED -> Triple(catColor.copy(alpha = 0.14f), catColor, false)
        ScheduleBlockVisualState.PLANNED -> Triple(WalletInfoBg, WalletInfoText, true)
        ScheduleBlockVisualState.ACTIVE -> Triple(WalletMintSoft, ActiveAccentCyan, false)
        ScheduleBlockVisualState.UNCONFIRMED -> Triple(WalletWarningBg, WalletWarningText, true)
    }

    val effectiveStrokeColor = when {
        hasConflictWarning -> NowIndicatorCoral
        isDraggingOrDraft || isSelected -> WalletMintAccent
        else -> accentColor.copy(alpha = 0.72f)
    }
    val borderStrokePx = with(LocalDensity.current) {
        (if (isDraggingOrDraft || isSelected || hasConflictWarning) 2.2.dp else 1.3.dp).toPx()
    }
    val cornerPx = with(LocalDensity.current) { 10.dp.toPx() }
    val compact = blockHeightDp < 44.dp
    val showQuickAction = blockHeightDp >= 82.dp && block.laneCount == 1
    val canResizeThis = block.canDragMoveOrResize && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext &&
        (isSelected || isDraggingOrDraft || blockHeightDp >= 44.dp)

    Box(
        modifier = modifier
            .clipToBounds()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    hasConflictWarning -> WalletWarningBg.copy(alpha = 0.94f)
                    isDraggingOrDraft -> bgColor.copy(alpha = 0.96f)
                    else -> bgColor
                }
            )
            .drawBehind {
                if (isDashed && !isDraggingOrDraft && !isSelected) {
                    drawRoundRect(
                        color = accentColor,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(
                            width = borderStrokePx,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                        )
                    )
                } else {
                    drawRoundRect(
                        color = effectiveStrokeColor,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = borderStrokePx)
                    )
                }
            }
            .walletClickable(onClickLabel = "Chọn hoặc sửa ${block.title}", onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (hasConflictWarning) NowIndicatorCoral else accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = if (block.laneCount > 1) 6.dp else 7.dp,
                        vertical = if (compact || block.laneCount > 1) 2.dp else 4.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (block.laneCount > 1) 2.dp else 3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    CategoryIcon(
                        category = block.category,
                        size = if (blockHeightDp < 32.dp) 13.dp else 16.dp,
                        tint = accentColor
                    )
                    Text(
                        text = block.title,
                        fontSize = if (compact) 12.sp else 13.5.sp,
                        lineHeight = if (compact) 14.5.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk,
                        maxLines = if (blockHeightDp >= 52.dp) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (block.laneCount == 1) {
                        if (blockHeightDp < 32.dp) {
                            Text(
                                text = "${TimeRange.formatHm(effectiveStartMin)}–${ScheduleGridEngine.formatHmEnd(effectiveEndMin)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else {
                            StateMicroPill(block.visualState)
                        }
                    }
                }

                if (!compact) {
                    val timeWindow = draggingLabel ?: buildString {
                        append(TimeRange.formatHm(effectiveStartMin))
                        append("–")
                        append(ScheduleGridEngine.formatHmEnd(effectiveEndMin))
                        append(" · ")
                        append(TimeRange.formatDuration((effectiveEndMin - effectiveStartMin).coerceAtLeast(1)))
                        if (block.crossesMidnightFromPrev) append(" (từ hôm qua)")
                        if (block.crossesMidnightToNext) append(" (+1 ngày)")
                    }
                    if (block.laneCount > 1) {
                        StateMicroPill(block.visualState)
                        Text(
                            text = if (isDraggingOrDraft) timeWindow else (block.planComparisonText ?: timeWindow),
                            fontSize = 11.sp,
                            lineHeight = 13.5.sp,
                            fontWeight = if (block.planComparisonText != null || isDraggingOrDraft) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasConflictWarning) NowIndicatorCoral else if (isDraggingOrDraft) WalletMintAccent else WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = if (isDraggingOrDraft) timeWindow else (block.planComparisonText ?: timeWindow),
                            fontSize = 11.5.sp,
                            lineHeight = 14.5.sp,
                            fontWeight = if (block.planComparisonText != null || isDraggingOrDraft) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasConflictWarning) NowIndicatorCoral else if (isDraggingOrDraft) WalletMintAccent else WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showQuickAction && onQuickConfirm != null) {
                    Box(
                        modifier = Modifier.walletClickable(
                            onClickLabel = "Xác nhận kết quả ${block.title}",
                            onClick = onQuickConfirm
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(UnconfirmedAmber)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            GlyphIcon(WalletGlyph.CONFIRM, color = Color.White, size = 11.dp)
                            Text(
                                text = "Xác nhận kết quả",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Tay nắm cạnh trên (chỉnh giờ bắt đầu) & cạnh dưới (chỉnh giờ kết thúc)
        if (canResizeThis) {
            ResizeEdgeHandle(
                isTopEdge = true,
                tint = (if (isSelected || isDraggingOrDraft) WalletMintAccent else accentColor).copy(alpha = 0.72f),
                dragKey = "block_top_${block.recordId}",
                modifier = Modifier.align(Alignment.TopCenter),
                onDragStart = onTopEdgeDragStart,
                onDragDelta = onTopEdgeDragDelta,
                onDragEnd = onTopEdgeDragEnd,
                onDragCancel = onTopEdgeDragCancel
            )
            ResizeEdgeHandle(
                isTopEdge = false,
                tint = (if (isSelected || isDraggingOrDraft) WalletMintAccent else accentColor).copy(alpha = 0.72f),
                dragKey = "block_bot_${block.recordId}",
                modifier = Modifier.align(Alignment.BottomCenter),
                onDragStart = onBottomEdgeDragStart,
                onDragDelta = onBottomEdgeDragDelta,
                onDragEnd = onBottomEdgeDragEnd,
                onDragCancel = onBottomEdgeDragCancel
            )
        }
    }
}

@Composable
private fun MultiDayTimelineGrid(
    days: List<DayScheduleModel>,
    todayStr: String,
    nowMinute: Int,
    hourHeight: Dp,
    compactColumns: Boolean = false,
    interactionState: ScheduleInteractionState,
    modifier: Modifier = Modifier,
    onSelectDay: (String) -> Unit,
    onTapCellAtMinute: (DayScheduleModel, Int) -> Unit,
    onDragSelectEmptyRange: (DayScheduleModel, Int, Int) -> Unit,
    onTapBlock: (ScheduleGridBlock) -> Unit,
    onDraftBlockMoved: (ScheduleGridBlock, Int, Int) -> Unit
) {
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val density = LocalDensity.current
    val hourPx = with(density) { hourHeight.toPx() }
    val minutePx = hourPx / 60f

    LaunchedEffect(days.size, compactColumns) {
        val targetHour = (nowMinute / 60 - 2).coerceIn(0, 16)
        vScrollState.scrollTo((targetHour * hourPx).roundToInt())
    }

    var draggingEmptyDate by remember { mutableStateOf<String?>(null) }
    var emptyDragAnchorMin by remember { mutableIntStateOf(0) }
    var emptyDragAccumY by remember { mutableFloatStateOf(0f) }
    var liveEmptyRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var draggingBlockId by remember { mutableStateOf<String?>(null) }
    var draggingBlockDate by remember { mutableStateOf<String?>(null) }
    var dragPreviewStartMin by remember { mutableIntStateOf(0) }
    var dragPreviewEndMin by remember { mutableIntStateOf(0) }

    val latestOnTapCellAtMinute by rememberUpdatedState(onTapCellAtMinute)
    val latestOnDragSelectEmptyRange by rememberUpdatedState(onDragSelectEmptyRange)
    val latestOnDraftBlockMoved by rememberUpdatedState(onDraftBlockMoved)

    val selectedSlots = (interactionState as? ScheduleInteractionState.EmptySlotsSelected)?.slots.orEmpty()
    val selectedBlock = (interactionState as? ScheduleInteractionState.BlockSelected)?.block
    val activeDraft = interactionState as? ScheduleInteractionState.BlockDraftTransform
    val placement = interactionState as? ScheduleInteractionState.UntimedPlacement

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = WalletCardBg,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val axisWidth = 42.dp
            val rightPad = 4.dp
            val gridAvailWidth = (maxWidth - axisWidth - rightPad).coerceAtLeast(180.dp)
            val minColWidth = if (compactColumns) 98.dp else 112.dp
            val needsHorizontalScroll = (minColWidth * days.size) > gridAvailWidth
            val colWidth = if (needsHorizontalScroll) minColWidth else (gridAvailWidth / days.size.coerceAtLeast(1))
            val totalDaysWidth = colWidth * days.size
            val colPx = with(density) { colWidth.toPx() }

            LaunchedEffect(days.firstOrNull()?.dateLocal, days.size, needsHorizontalScroll) {
                if (needsHorizontalScroll) {
                    val todayIdx = days.indexOfFirst { it.dateLocal == todayStr }
                    if (todayIdx > 1) {
                        hScrollState.scrollTo(((todayIdx - 1) * colPx).roundToInt())
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WalletMintTint)
                        .padding(top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(axisWidth))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (needsHorizontalScroll) Modifier.horizontalScroll(hScrollState) else Modifier)
                            .padding(end = rightPad)
                    ) {
                        days.forEach { day ->
                            val d = remember(day.dateLocal) { LocalDate.parse(day.dateLocal) }
                            val isToday = day.dateLocal == todayStr
                            val colSummary = buildString {
                                if (day.actualMinutesTotal > 0) {
                                    append("TT ${TimeRange.formatDuration(day.actualMinutesTotal).replace(" ", "")}")
                                }
                                if (day.plannedMinutesTotal > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("KH ${TimeRange.formatDuration(day.plannedMinutesTotal).replace(" ", "")}")
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .width(colWidth)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isToday) WalletMint else Color.Transparent)
                                    .walletClickable(onClickLabel = "Mở ngày ${day.dateLocal}") {
                                        onSelectDay(day.dateLocal)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = shortWeekdayVi(d.dayOfWeek),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isToday) Color(0xFFD6EFE3) else WalletMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = d.format(DateTimeFormatter.ofPattern("dd/MM")),
                                    fontSize = if (compactColumns) 12.5.sp else 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isToday) Color.White else WalletInk,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (colSummary.isNotEmpty()) {
                                    Text(
                                        text = colSummary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isToday) Color.White else WalletMintAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = WalletStroke)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(vScrollState)
                ) {
                    Column(modifier = Modifier.width(axisWidth)) {
                        for (h in 0 until 24) {
                            Box(
                                modifier = Modifier.height(hourHeight).fillMaxWidth().padding(end = 4.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(
                                    text = "%02d:00".format(h),
                                    fontSize = 9.5.sp,
                                    color = WalletMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (needsHorizontalScroll) Modifier.horizontalScroll(hScrollState) else Modifier)
                            .padding(end = rightPad)
                    ) {
                        Row(
                            modifier = Modifier
                                .width(totalDaysWidth)
                                .height(hourHeight * 24)
                        ) {
                            days.forEach { day ->
                                val isDayToday = day.dateLocal == todayStr
                                val latestDay by rememberUpdatedState(day)
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .width(colWidth)
                                        .fillMaxHeight()
                                        .border(0.5.dp, WalletDivider)
                                        .pointerInput(day.dateLocal) {
                                            detectTapGestures { pos ->
                                                val minute = ((pos.y / minutePx).roundToInt()).coerceIn(0, 1439)
                                                latestOnTapCellAtMinute(latestDay, minute)
                                            }
                                        }
                                        .pointerInput(day.dateLocal) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { pos ->
                                                    val s = ((pos.y / minutePx).roundToInt()).coerceIn(0, 1425)
                                                    draggingEmptyDate = day.dateLocal
                                                    emptyDragAnchorMin = s
                                                    emptyDragAccumY = 0f
                                                    liveEmptyRange = ScheduleGridEngine.snapEmptyDragRange(s, s + 30)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    emptyDragAccumY += dragAmount.y
                                                    val c = (emptyDragAnchorMin + (emptyDragAccumY / minutePx).roundToInt()).coerceIn(0, 1440)
                                                    liveEmptyRange = ScheduleGridEngine.snapEmptyDragRange(emptyDragAnchorMin, c)
                                                },
                                                onDragEnd = {
                                                    val chosen = liveEmptyRange
                                                    draggingEmptyDate = null
                                                    liveEmptyRange = null
                                                    if (chosen != null) {
                                                        latestOnDragSelectEmptyRange(latestDay, chosen.first, chosen.second)
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingEmptyDate = null
                                                    liveEmptyRange = null
                                                }
                                            )
                                        }
                                ) {
                                    val colW = maxWidth
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        for (h in 0 until 24) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(hourHeight)
                                            ) {
                                                HorizontalDivider(color = WalletDivider, thickness = 0.8.dp)
                                            }
                                        }
                                    }

                                    // Hiển thị vùng chọn khoảng trống trên cột ngày này
                                    selectedSlots.filter { it.dateLocal == day.dateLocal }.forEach { slot ->
                                        val topDp = (slot.startMinute / 60f) * hourHeight.value
                                        val hDp = max(20f, ((slot.endMinute - slot.startMinute) / 60f) * hourHeight.value)
                                        Box(
                                            modifier = Modifier
                                                .offset(x = 1.dp, y = topDp.dp)
                                                .width(colW - 2.dp)
                                                .height(hDp.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(WalletMintSoft)
                                                .border(1.5.dp, WalletMint, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✓ ${TimeRange.formatHm(slot.startMinute)}",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = WalletMintDark,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (draggingEmptyDate == day.dateLocal && liveEmptyRange != null) {
                                        val (s, e) = liveEmptyRange!!
                                        val topDp = (s / 60f) * hourHeight.value
                                        val hDp = max(20f, ((e - s) / 60f) * hourHeight.value)
                                        Box(
                                            modifier = Modifier
                                                .offset(x = 1.dp, y = topDp.dp)
                                                .width(colW - 2.dp)
                                                .height(hDp.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(WalletMintSoft.copy(alpha = 0.9f))
                                                .border(1.5.dp, WalletMint, RoundedCornerShape(6.dp))
                                        )
                                    }

                                    if (placement?.targetDateLocal == day.dateLocal && placement.startMinute != null && placement.endMinute != null) {
                                        val s = placement.startMinute
                                        val e = placement.endMinute
                                        val topDp = (s / 60f) * hourHeight.value
                                        val hDp = max(20f, ((e - s) / 60f) * hourHeight.value)
                                        Box(
                                            modifier = Modifier
                                                .offset(x = 1.dp, y = topDp.dp)
                                                .width(colW - 2.dp)
                                                .height(hDp.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(WalletInfoBg)
                                                .border(1.5.dp, PlannedAccentBlue, RoundedCornerShape(6.dp))
                                        )
                                    }

                                    day.timedBlocks.forEach { block ->
                                        val isDraggingThis = draggingBlockId == block.recordId && draggingBlockDate == day.dateLocal
                                        val isDraftedThis = activeDraft?.original?.recordId == block.recordId && activeDraft.targetDateLocal == day.dateLocal
                                        val isSelThis = (selectedBlock?.recordId == block.recordId && selectedBlock.dateLocal == day.dateLocal) || isDraftedThis || isDraggingThis

                                        val effStart = when {
                                            isDraggingThis -> dragPreviewStartMin
                                            isDraftedThis -> activeDraft!!.draftStartMin
                                            else -> block.startMinute
                                        }
                                        val effEnd = when {
                                            isDraggingThis -> dragPreviewEndMin
                                            isDraftedThis -> activeDraft!!.draftEndMin
                                            else -> block.endMinute
                                        }
                                        val topDp = (effStart / 60f) * hourHeight.value
                                        val hDp = max(22f, ((effEnd - effStart) / 60f) * hourHeight.value)
                                        val frac = 1f / block.laneCount.coerceAtLeast(1)
                                        val wDp = (colW - 2.dp) * frac
                                        val xDp = wDp * block.laneIndex + 1.dp
                                        val accentColor = when (block.visualState) {
                                            ScheduleBlockVisualState.PLANNED -> PlannedAccentBlue
                                            ScheduleBlockVisualState.UNCONFIRMED -> UnconfirmedAmber
                                            ScheduleBlockVisualState.ACTIVE -> ActiveAccentCyan
                                            ScheduleBlockVisualState.COMPLETED -> Color(block.category.colorHex)
                                        }
                                        val latestBlock by rememberUpdatedState(block)
                                        val latestEffRange by rememberUpdatedState(effStart to effEnd)
                                        var colAccumDragY by remember(block.recordId) { mutableFloatStateOf(0f) }
                                        var colDragOrigStart by remember(block.recordId) { mutableIntStateOf(block.startMinute) }
                                        var colDragOrigEnd by remember(block.recordId) { mutableIntStateOf(block.endMinute) }

                                        Box(
                                            modifier = Modifier
                                                .offset(x = xDp, y = topDp.dp)
                                                .width((wDp - 1.dp).coerceAtLeast(20.dp))
                                                .height(hDp.dp)
                                                .zIndex(if (isSelThis) 10f else 1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when (block.visualState) {
                                                        ScheduleBlockVisualState.PLANNED -> WalletInfoBg
                                                        ScheduleBlockVisualState.UNCONFIRMED -> WalletWarningBg
                                                        ScheduleBlockVisualState.ACTIVE -> WalletMintSoft
                                                        ScheduleBlockVisualState.COMPLETED -> accentColor.copy(alpha = 0.22f)
                                                    }
                                                )
                                                .border(
                                                    width = if (isSelThis) 2.dp else 1.dp,
                                                    color = if (isSelThis) WalletMintAccent else accentColor.copy(alpha = 0.8f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .then(
                                                    if (block.canDragMoveOrResize && block.record != null && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                                                        Modifier.pointerInput(block.recordId, block.startMinute, block.endMinute) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    val (baseS, baseE) = latestEffRange
                                                                    colAccumDragY = 0f
                                                                    colDragOrigStart = baseS
                                                                    colDragOrigEnd = baseE
                                                                    draggingBlockId = latestBlock.recordId
                                                                    draggingBlockDate = day.dateLocal
                                                                    dragPreviewStartMin = baseS
                                                                    dragPreviewEndMin = baseE
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    colAccumDragY += dragAmount.y
                                                                    val deltaMin = (colAccumDragY / minutePx).roundToInt()
                                                                    val (s, e) = ScheduleGridEngine.snapMoveRange(colDragOrigStart, colDragOrigEnd, deltaMin)
                                                                    dragPreviewStartMin = s
                                                                    dragPreviewEndMin = e
                                                                },
                                                                onDragEnd = {
                                                                    val s = dragPreviewStartMin
                                                                    val e = dragPreviewEndMin
                                                                    draggingBlockId = null
                                                                    draggingBlockDate = null
                                                                    if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                                                        latestOnDraftBlockMoved(latestBlock, s, e)
                                                                    }
                                                                },
                                                                onDragCancel = {
                                                                    draggingBlockId = null
                                                                    draggingBlockDate = null
                                                                }
                                                            )
                                                        }
                                                    } else Modifier
                                                )
                                                .walletClickable(onClickLabel = "Chọn ${block.title}") { onTapBlock(block) }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = block.title,
                                                fontSize = if (compactColumns) 11.sp else 11.5.sp,
                                                lineHeight = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WalletInk,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (isDayToday) {
                                        val nowY = (nowMinute / 60f) * hourHeight.value
                                        Box(
                                            modifier = Modifier
                                                .offset(y = nowY.dp)
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(NowIndicatorCoral)
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

@Composable
private fun MonthOverviewGrid(
    cells: List<MonthDayCell>,
    selectedDateLocal: String,
    selectedDayModel: DayScheduleModel,
    today: LocalDate,
    selectedBlockId: String?,
    modifier: Modifier = Modifier,
    scrollVertically: Boolean = true,
    onSelectDate: (String) -> Unit,
    onOpenDayTimeline: (String) -> Unit,
    onAddOnDate: (String) -> Unit,
    onSelectBlock: (ScheduleGridBlock) -> Unit
) {
    val weekHeaders = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    val scrollModifier = if (scrollVertically) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
        modifier = modifier.then(scrollModifier),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = WalletCardBg,
            border = BorderStroke(1.dp, WalletStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekHeaders.forEach { dLabel ->
                        Text(
                            text = dLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                HorizontalDivider(color = WalletDivider)
                cells.chunked(7).forEach { weekRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        weekRow.forEach { cell ->
                            val isSelected = cell.dateLocal == selectedDateLocal
                            val cellBg = when {
                                isSelected -> WalletMintTint
                                cell.isToday -> WalletMintSoft
                                !cell.inCurrentMonth -> WalletSoftNeutral.copy(alpha = 0.5f)
                                else -> WalletCardBg
                            }
                            val borderColor = when {
                                isSelected -> WalletMint
                                cell.isToday -> WalletMintMid
                                else -> WalletStroke
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cellBg)
                                    .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                                    .walletClickable(onClickLabel = "Chọn ngày ${cell.dateLocal}") {
                                        onSelectDate(cell.dateLocal)
                                    }
                                    .padding(horizontal = 3.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = cell.dayOfMonth.toString(),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (cell.isToday || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (!cell.inCurrentMonth) WalletMuted.copy(alpha = 0.5f) else WalletInk
                                    )
                                    if (cell.unconfirmedCount > 0) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(UnconfirmedAmber))
                                    }
                                }
                                when {
                                    cell.actualMinutes > 0 -> {
                                        Text(
                                            text = TimeRange.formatDuration(cell.actualMinutes),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalletMintAccent,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    cell.plannedMinutes > 0 -> {
                                        Text(
                                            text = "KH ${TimeRange.formatDuration(cell.plannedMinutes).replace(" ", "")}",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalletInfoText,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    cell.inCurrentMonth -> {
                                        Text(
                                            text = "Trống",
                                            fontSize = 8.5.sp,
                                            color = WalletMuted.copy(alpha = 0.55f),
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (cell.topCategories.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        cell.topCategories.forEach { cat ->
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(cat.colorHex))
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

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WalletCardBg,
            border = BorderStroke(1.dp, WalletStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = formatScheduleRangeHeading(LocalDate.parse(selectedDateLocal), today, ScheduleViewMode.DAY),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk
                        )
                        Text(
                            text = "Thực tế ${TimeRange.formatDuration(selectedDayModel.actualMinutesTotal)} · Dự kiến ${TimeRange.formatDuration(selectedDayModel.plannedMinutesTotal)}",
                            fontSize = 13.sp,
                            color = WalletMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletMintTint)
                                .walletClickable(onClickLabel = "Xem dòng giờ ngày $selectedDateLocal") {
                                    onOpenDayTimeline(selectedDateLocal)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Xem Ngày", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = WalletMintDark)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletMint)
                                .walletClickable(onClickLabel = "Ghi mới vào ngày $selectedDateLocal") {
                                    onAddOnDate(selectedDateLocal)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Ghi mới", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                if (selectedDayModel.timedBlocks.isEmpty() && selectedDayModel.untimedRecords.isEmpty()) {
                    Text(
                        text = "Ngày này còn trống — chạm '+ Ghi mới' hoặc 'Xem Ngày' để lên lịch hay ghi nhận hoạt động.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = WalletMuted
                    )
                } else {
                    selectedDayModel.timedBlocks.take(10).forEach { block ->
                        val isSel = selectedBlockId == block.recordId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) WalletMintTint else WalletBg)
                                .border(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) WalletMint else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .walletClickable(onClickLabel = "Chọn ${block.title}") {
                                    onSelectBlock(block)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CategoryIcon(block.category, size = 18.dp, tint = Color(block.category.colorHex))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = block.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletInk,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ScheduleGridEngine.formatHmWindow(block.startMinute, block.endMinute),
                                    fontSize = 12.sp,
                                    color = WalletMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            StateMicroPill(block.visualState)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntimedRecordsSheet(
    records: List<LedgerRecord>,
    onDismiss: () -> Unit,
    onPlaceOnSchedule: (LedgerRecord) -> Unit,
    onEditRecord: (LedgerRecord) -> Unit,
    onQuickLogDuration: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WalletCardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chưa gắn giờ · Chỉ thời lượng (${records.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk
                    )
                    Text(
                        text = "Hoạt động chỉ ghi tổng thời lượng nằm riêng tại đây. Bạn có thể chọn \"Đặt vào lịch\" để gắn khung giờ cụ thể.",
                        fontSize = 12.sp,
                        color = WalletMuted,
                        lineHeight = 16.sp
                    )
                }
                Button(
                    onClick = onQuickLogDuration,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White)
                ) {
                    Text("+ Chỉ thời lượng", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (records.isEmpty()) {
                Text(
                    text = "Chưa có hoạt động nào chỉ lưu thời lượng. Khung giờ mới trên lịch có thể tạo trực tiếp bằng cách chạm hoặc kéo trên khoảng trống.",
                    fontSize = 12.5.sp,
                    color = WalletMuted,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    records.forEach { rec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WalletMintTint)
                                .border(1.dp, WalletStroke, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryIcon(rec.category, size = 16.dp, tint = WalletMintAccent)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${TimeRange.formatDuration(rec.durationMinutes)} · ${rec.date}",
                                    fontSize = 11.sp,
                                    color = WalletMuted
                                )
                            }
                            ActionChipButton(
                                label = "Đặt vào lịch",
                                bg = WalletMint,
                                fg = Color.White,
                                onClick = { onPlaceOnSchedule(rec) }
                            )
                            ActionChipButton(
                                label = "Sửa",
                                bg = WalletCardBg,
                                fg = WalletInk,
                                onClick = { onEditRecord(rec) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateMicroPill(state: ScheduleBlockVisualState) {
    val (bg, fg) = when (state) {
        ScheduleBlockVisualState.COMPLETED -> WalletMintSoft to WalletMintAccent
        ScheduleBlockVisualState.PLANNED -> WalletInfoBg to WalletInfoText
        ScheduleBlockVisualState.ACTIVE -> WalletMintTint to ActiveAccentCyan
        ScheduleBlockVisualState.UNCONFIRMED -> WalletWarningBg to WalletWarningText
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = state.labelVi,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun LegendStateChip(
    color: Color,
    label: String,
    solid: Boolean,
    pulse: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(if (pulse) CircleShape else RoundedCornerShape(3.dp))
                .background(if (solid) color else Color.Transparent)
                .border(1.5.dp, color, if (pulse) CircleShape else RoundedCornerShape(3.dp))
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WalletMuted,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun FilterBadgePill(
    label: String,
    selected: Boolean,
    category: TimeCategory? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) WalletMint else WalletBg)
            .border(1.dp, if (selected) WalletMint else WalletStroke, RoundedCornerShape(100.dp))
            .walletClickable(onClickLabel = "Lọc $label", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        category?.let {
            CategoryIcon(it, size = 13.dp, tint = if (selected) Color.White else WalletInk)
        }
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else WalletInk,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun stepScheduleDate(date: LocalDate, mode: ScheduleViewMode, direction: Int): LocalDate {
    return when (mode) {
        ScheduleViewMode.DAY -> date.plusDays(direction.toLong())
        ScheduleViewMode.THREE_DAYS -> date.plusDays((direction * 3).toLong())
        ScheduleViewMode.WEEK -> date.plusWeeks(direction.toLong())
        ScheduleViewMode.MONTH -> date.plusMonths(direction.toLong())
    }
}

private fun shortWeekdayVi(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "T2"
    DayOfWeek.TUESDAY -> "T3"
    DayOfWeek.WEDNESDAY -> "T4"
    DayOfWeek.THURSDAY -> "T5"
    DayOfWeek.FRIDAY -> "T6"
    DayOfWeek.SATURDAY -> "T7"
    DayOfWeek.SUNDAY -> "CN"
}

private fun formatScheduleRangeHeading(
    selectedDate: LocalDate,
    today: LocalDate,
    mode: ScheduleViewMode
): String {
    val ddMm = DateTimeFormatter.ofPattern("dd/MM")
    return when (mode) {
        ScheduleViewMode.DAY -> when (selectedDate) {
            today -> "Hôm nay · ${selectedDate.format(ddMm)}"
            today.minusDays(1) -> "Hôm qua · ${selectedDate.format(ddMm)}"
            today.plusDays(1) -> "Ngày mai · ${selectedDate.format(ddMm)}"
            else -> "${shortWeekdayVi(selectedDate.dayOfWeek)}, ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
        }
        ScheduleViewMode.THREE_DAYS -> {
            val start = if (selectedDate == today) today.minusDays(1) else selectedDate
            val end = start.plusDays(2)
            "${start.format(ddMm)} – ${end.format(ddMm)}"
        }
        ScheduleViewMode.WEEK -> {
            val mon = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sun = mon.plusDays(6)
            "Tuần ${mon.format(ddMm)}–${sun.format(ddMm)}"
        }
        ScheduleViewMode.MONTH -> {
            selectedDate.format(DateTimeFormatter.ofPattern("'Tháng' MM/yyyy", Locale("vi", "VN")))
        }
    }
}
