package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    onPlaceUntimedOnSlot: (recordId: String, dateLocal: String, startHm: String, endHm: String) -> Unit = onMoveOrResizeBlock,
    recordedSummaryBadge: String? = null,
    onRecordedSummaryClick: (() -> Unit)? = null,
    onSwitchToTimelineMode: (() -> Unit)? = null,
    nowEpochMs: Long = System.currentTimeMillis()
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(todayDateLocal) { runCatching { LocalDate.parse(todayDateLocal) }.getOrDefault(LocalDate.now(zone)) }
    val selectedDate = remember(selectedDateLocal, today) { runCatching { LocalDate.parse(selectedDateLocal) }.getOrDefault(today) }
    val nowMs = nowEpochMs
    val nowLocalTime = remember(nowMs, zone) { Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime() }
    val nowMinute = nowLocalTime.hour * 60 + nowLocalTime.minute

    var showSearchAndFilter by rememberSaveable { mutableStateOf(false) }
    var showLegend by rememberSaveable { mutableStateOf(false) }
    var showCalendarMenu by remember { mutableStateOf(false) }
    var showUntimedSheet by remember { mutableStateOf(false) }
    var blockPendingDelete by remember { mutableStateOf<LedgerRecord?>(null) }
    var interactionState by remember { mutableStateOf<ScheduleInteractionState>(ScheduleInteractionState.Idle) }
    var timelineScrollPx by remember(viewMode, selectedDateLocal) { mutableFloatStateOf(0f) }
    var timelineViewportHeightPx by remember { mutableFloatStateOf(0f) }
    var activeDragViewportY by remember { mutableFloatStateOf(-1f) }
    var quickJumpNonce by remember { mutableIntStateOf(0) }
    var quickJumpMinute by remember { mutableIntStateOf(nowMinute) }
    val quickScrollRequest: Pair<Int, Int>? = if (quickJumpNonce > 0) quickJumpMinute to quickJumpNonce else null

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
        // Chạm để chọn khối và thao tác trực tiếp trên timeline; chỉ mở bottom sheet chi tiết khi người dùng bấm "Sửa".
        interactionState = ScheduleInteractionState.BlockSelected(block)
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Panel điều khiển hợp nhất tối giản trên surface sạch (xanh chỉ dùng cho trạng thái chọn & nút chính "+"):
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = WalletCardBg,
                shadowElevation = 1.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val showTopIdentityRow = showBackButton || recordedSummaryBadge != null || onSwitchToTimelineMode != null
                    if (showTopIdentityRow) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stackModeSwitcher = maxWidth < 330.dp &&
                                onSwitchToTimelineMode != null &&
                                (showBackButton || recordedSummaryBadge != null)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = if (stackModeSwitcher) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (showBackButton) {
                                                Box(
                                                    modifier = Modifier
                                                        .semantics { contentDescription = "Đóng lịch trình" }
                                                        .walletClickable(onClickLabel = "Đóng lịch trình", onClick = onBackClick),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(WalletSoftNeutral),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        GlyphIcon(WalletGlyph.BACK, color = WalletInk, size = 14.dp)
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "Lịch trình",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = WalletInk,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                        if (recordedSummaryBadge != null) {
                                            val badgeModifier = if (onRecordedSummaryClick != null) {
                                                Modifier.walletClickable(onClickLabel = "Xem nhanh thống kê ví 24 giờ hôm nay") {
                                                    onRecordedSummaryClick()
                                                }
                                            } else {
                                                Modifier
                                            }
                                            Box(
                                                modifier = badgeModifier,
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(100.dp))
                                                        .background(WalletSoftNeutral)
                                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = recordedSummaryBadge,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = WalletMintAccent,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (onSwitchToTimelineMode != null && !stackModeSwitcher) {
                                        ScheduleSurfaceModeSwitcher(
                                            fillWidth = false,
                                            onSwitchToTimelineMode = onSwitchToTimelineMode
                                        )
                                    }
                                }

                                if (onSwitchToTimelineMode != null && stackModeSwitcher) {
                                    ScheduleSurfaceModeSwitcher(
                                        fillWidth = true,
                                        onSwitchToTimelineMode = onSwitchToTimelineMode
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .semantics { contentDescription = "Lùi thời gian" }
                                .walletClickable(onClickLabel = "Lùi thời gian") {
                                    onSelectDate(stepScheduleDate(selectedDate, viewMode, -1).toString())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(WalletSoftNeutral),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("‹", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                            }
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = formatScheduleRangeHeading(selectedDate, today, viewMode),
                                fontSize = if (viewMode == ScheduleViewMode.WEEK) 12.5.sp else 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (selectedDate != today) {
                                Box(
                                    modifier = Modifier.walletClickable(onClickLabel = "Về hôm nay") {
                                        onSelectDate(today.toString())
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(WalletMintSoft)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Hôm nay",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = WalletMintAccent,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .semantics { contentDescription = "Tiến thời gian" }
                                .walletClickable(onClickLabel = "Tiến thời gian") {
                                    onSelectDate(stepScheduleDate(selectedDate, viewMode, +1).toString())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(WalletSoftNeutral),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("›", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                            }
                        }

                        Box {
                            val hasActiveFilter = showSearchAndFilter || !categoryFilter.isNullOrBlank() || searchQuery.isNotBlank() || showLegend
                            Box(
                                modifier = Modifier
                                    .semantics { contentDescription = "Tùy chọn lịch trình" }
                                    .walletClickable(onClickLabel = "Tùy chọn lịch trình") {
                                        showCalendarMenu = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (hasActiveFilter) WalletMintSoft else WalletSoftNeutral),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (!categoryFilter.isNullOrBlank() || searchQuery.isNotBlank()) "⋮●" else "⋮",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (hasActiveFilter) WalletMintAccent else WalletInk,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
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
                                            text = "TT ${TimeRange.formatDuration(headerActualMinutes)} · KH ${TimeRange.formatDuration(headerPlannedMinutes)}" +
                                                if (headerUnconfirmed > 0) " · Chờ $headerUnconfirmed" else "",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = WalletMuted
                                        )
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                                HorizontalDivider(color = WalletDivider)
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletSoftNeutral)
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ScheduleViewMode.entries.forEach { mode ->
                                val isSelected = mode == viewMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { this.selected = isSelected }
                                        .walletClickable(onClickLabel = "Chế độ ${mode.label}", role = Role.Tab) {
                                            onSelectViewMode(mode)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) WalletCardBg else Color.Transparent)
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.label,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) WalletInk else WalletMuted,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                        // Khay "Chưa gắn giờ" + "+" điểm nhấn chính duy nhất màu xanh thương hiệu
                        PinnedScheduleUtilityStrip(
                            untimedCount = untimedItems.size,
                            isPlacingUntimed = interactionState is ScheduleInteractionState.UntimedPlacement,
                            onOpenUntimedTray = { showUntimedSheet = true },
                            onQuickCreateNew = {
                                onAddForSlot(selectedDate.toString(), null, null, selectedDate.isAfter(today))
                            }
                        )
                    }
                }
            }
        }

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

        // 2. Timeline là trung tâm — thích ứng theo chiều cao màn hình để không tạo dải trống đáy ở 390dp hay tràn lồng cuộn ở 320dp
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val embeddedGridHeight = (screenHeightDp - 246).coerceIn(320, 620).dp
        val gridModifier = if (embeddedInScrollContainer) {
            Modifier.fillMaxWidth().height(embeddedGridHeight)
        } else {
            Modifier.fillMaxWidth().weight(1f)
        }
        val boxContainerModifier = if (viewMode == ScheduleViewMode.MONTH && embeddedInScrollContainer) {
            Modifier.fillMaxWidth()
        } else {
            gridModifier
        }

        val activeHourHeightPx = with(LocalDensity.current) {
            when (viewMode) {
                ScheduleViewMode.DAY -> 72.dp.toPx()
                ScheduleViewMode.THREE_DAYS -> 56.dp.toPx()
                ScheduleViewMode.WEEK -> 40.dp.toPx()
                ScheduleViewMode.MONTH -> 40.dp.toPx()
            }
        }
        val activeTargetViewportY: Float? = when {
            activeDragViewportY >= 0f -> activeDragViewportY
            else -> when (val s = interactionState) {
                is ScheduleInteractionState.BlockDraftTransform ->
                    (((s.draftStartMin + s.draftEndMin) / 2f) / 60f) * activeHourHeightPx - timelineScrollPx
                is ScheduleInteractionState.BlockSelected ->
                    (((s.block.startMinute + s.block.endMinute) / 2f) / 60f) * activeHourHeightPx - timelineScrollPx
                is ScheduleInteractionState.EmptySlotsSelected ->
                    s.slots.firstOrNull()?.let { slot ->
                        (((slot.startMinute + slot.endMinute) / 2f) / 60f) * activeHourHeightPx - timelineScrollPx
                    }
                is ScheduleInteractionState.UntimedPlacement ->
                    if (s.startMinute != null && s.endMinute != null) {
                        (((s.startMinute + s.endMinute) / 2f) / 60f) * activeHourHeightPx - timelineScrollPx
                    } else null
                else -> null
            }
        }
        val dockToolbarAtTop = viewMode != ScheduleViewMode.MONTH &&
            activeTargetViewportY != null &&
            timelineViewportHeightPx > 80f &&
            activeTargetViewportY > timelineViewportHeightPx * 0.48f

        val nextJumpBlock = remember(multiDayModels, selectedDayModel, selectedDate, today, nowMinute) {
            if (selectedDate == today) {
                val todayModel = multiDayModels.firstOrNull { it.dateLocal == today.toString() } ?: selectedDayModel
                todayModel.timedBlocks.firstOrNull { it.startMinute > nowMinute }
                    ?: todayModel.timedBlocks.firstOrNull { it.endMinute > nowMinute }
            } else {
                selectedDayModel.timedBlocks.firstOrNull()
            }
        }

        Box(modifier = boxContainerModifier) {
            when (viewMode) {
                ScheduleViewMode.DAY -> {
                    val dayModel = multiDayModels.first()
                    DayTimelineGrid(
                        dayModel = dayModel,
                        isToday = selectedDate == today,
                        nowMinute = nowMinute,
                        quickScrollRequest = quickScrollRequest,
                        interactionState = interactionState,
                        modifier = Modifier.fillMaxSize(),
                        onViewportMetricsChanged = { scrollPx, vpH ->
                            timelineScrollPx = scrollPx
                            timelineViewportHeightPx = vpH
                        },
                        onDragViewportYChanged = { yVp -> activeDragViewportY = yVp },
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
                        hourHeight = 56.dp,
                        quickScrollRequest = quickScrollRequest,
                        interactionState = interactionState,
                        modifier = Modifier.fillMaxSize(),
                        onViewportMetricsChanged = { scrollPx, vpH ->
                            timelineScrollPx = scrollPx
                            timelineViewportHeightPx = vpH
                        },
                        onDragViewportYChanged = { yVp -> activeDragViewportY = yVp },
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
                        hourHeight = 40.dp,
                        compactColumns = true,
                        quickScrollRequest = quickScrollRequest,
                        interactionState = interactionState,
                        modifier = Modifier.fillMaxSize(),
                        onViewportMetricsChanged = { scrollPx, vpH ->
                            timelineScrollPx = scrollPx
                            timelineViewportHeightPx = vpH
                        },
                        onDragViewportYChanged = { yVp -> activeDragViewportY = yVp },
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
                        modifier = Modifier.fillMaxWidth(),
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

            // Nút nhảy nhanh "Giờ hiện tại" & "Hoạt động kế tiếp": chỉ hiện "● HH:mm" khi khác hôm nay hoặc đường giờ hiện tại đã cuộn ra ngoài khung nhìn
            val nowY = (nowMinute / 60f) * activeHourHeightPx
            val isNowVisibleInViewport = selectedDate == today &&
                timelineViewportHeightPx > 40f &&
                nowY >= (timelineScrollPx + 14f) &&
                nowY <= (timelineScrollPx + timelineViewportHeightPx - 14f)
            val showNowJumpChip = !isNowVisibleInViewport
            if (viewMode != ScheduleViewMode.MONTH && !(dockToolbarAtTop && interactionState != ScheduleInteractionState.Idle) && (showNowJumpChip || nextJumpBlock != null)) {
                val quickTopPad = if (viewMode == ScheduleViewMode.DAY) 6.dp else 46.dp
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = quickTopPad, end = 8.dp)
                            .zIndex(24f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showNowJumpChip) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = WalletCardBg.copy(alpha = 0.94f),
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, WalletStroke)
                            ) {
                                Text(
                                    text = "● ${TimeRange.formatHm(nowMinute)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NowIndicatorCoral,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .walletClickable(onClickLabel = "Về giờ hiện tại") {
                                            if (selectedDate != today) onSelectDate(today.toString())
                                            quickJumpMinute = nowMinute
                                            quickJumpNonce++
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        nextJumpBlock?.let { blk ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = WalletCardBg.copy(alpha = 0.94f),
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, WalletMintAccent.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "⏭ Kế ${TimeRange.formatHm(blk.startMinute)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMintAccent,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .walletClickable(onClickLabel = "Tới hoạt động kế tiếp ${blk.title}") {
                                            quickJumpMinute = blk.startMinute
                                            quickJumpNonce++
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Thanh công cụ nổi nhỏ gọn neo đối diện khối đang chọn/kéo (không đẩy hay che timeline!)
            val overlayTopPad = if (viewMode == ScheduleViewMode.THREE_DAYS || viewMode == ScheduleViewMode.WEEK) 56.dp else 6.dp
            val overlayPad = if (dockToolbarAtTop) {
                Modifier.align(Alignment.TopCenter).padding(start = 8.dp, end = 8.dp, top = overlayTopPad, bottom = 6.dp)
            } else {
                Modifier.align(Alignment.BottomCenter).padding(
                    start = 8.dp,
                    end = 56.dp,
                    bottom = 6.dp
                )
            }
            Box(modifier = overlayPad.zIndex(30f)) {
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
                            onEditDetails = {
                                val rec = active.original.record
                                interactionState = ScheduleInteractionState.Idle
                                if (active.original.isActiveTimer) onNavigateToTimer()
                                else rec?.let(onEditRecord)
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
private fun ScheduleSurfaceModeSwitcher(
    fillWidth: Boolean,
    onSwitchToTimelineMode: () -> Unit
) {
    Row(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(100.dp))
            .background(WalletSoftNeutral)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(true to "Lịch trình", false to "Dòng thời gian").forEach { (isCal, label) ->
            val selected = isCal
            Box(
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .semantics {
                        this.selected = selected
                        this.contentDescription = "Chế độ $label"
                    }
                    .walletClickable(
                        onClickLabel = "Chế độ $label",
                        role = Role.Tab
                    ) {
                        if (!isCal) onSwitchToTimelineMode()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) WalletMint else Color.Transparent)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
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

@Composable
private fun PinnedScheduleUtilityStrip(
    untimedCount: Int,
    isPlacingUntimed: Boolean,
    onOpenUntimedTray: () -> Unit,
    onQuickCreateNew: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.walletClickable(onClickLabel = "Mở khay hoạt động chưa gắn giờ") { onOpenUntimedTray() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (isPlacingUntimed) WalletMintSoft else WalletSoftNeutral)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (untimedCount > 0) WalletMint else WalletMuted.copy(alpha = 0.45f))
                )
                Text(
                    text = "Chưa giờ ($untimedCount)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlacingUntimed) WalletMintAccent else WalletInk,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Box(
            modifier = Modifier
                .semantics { contentDescription = "Ghi mới hoạt động" }
                .walletClickable(onClickLabel = "Ghi mới hoạt động vào ngày đang xem", onClick = onQuickCreateNew),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(WalletMint),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
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
        shape = RoundedCornerShape(12.dp),
        color = WalletCardBg.copy(alpha = 0.97f),
        border = BorderStroke(1.5.dp, WalletMint),
        shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 34.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val cleanSummary = summaryText.removePrefix("1 sự kiện · ")
                Text(
                    text = if (slotCount <= 1) cleanSummary else "($slotCount) $cleanSummary",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WalletSoftNeutral)
                        .border(1.dp, WalletStroke, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = "Bỏ chọn khung giờ" }
                        .walletClickable(onClickLabel = "Bỏ chọn khung giờ", onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WalletMint)
                        .semantics { contentDescription = "Thêm hoạt động vào khung giờ" }
                        .walletClickable(onClickLabel = "Thêm hoạt động vào khung giờ", onClick = onCreate),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

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
        shape = RoundedCornerShape(12.dp),
        color = WalletCardBg.copy(alpha = 0.97f),
        border = BorderStroke(1.5.dp, WalletMint),
        shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 34.dp) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "${block.title} · ${ScheduleGridEngine.formatHmWindow(block.startMinute, block.endMinute)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(WalletSoftNeutral)
                            .semantics { contentDescription = "Bỏ chọn hoạt động" }
                            .walletClickable(onClickLabel = "Bỏ chọn hoạt động", onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionChipButton(
                        label = if (block.isActiveTimer) "Đồng hồ" else "Sửa",
                        bg = WalletMintTint,
                        fg = WalletMintAccent,
                        onClick = onEdit
                    )
                    if (block.canDragMoveOrResize && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                        ActionChipButton(label = "−15p", bg = WalletSoftNeutral, fg = WalletInk, onClick = onNudgeEarlier)
                        ActionChipButton(label = "+15p", bg = WalletSoftNeutral, fg = WalletInk, onClick = onNudgeLater)
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
}

@Composable
private fun DraftTransformActionBar(
    draft: ScheduleInteractionState.BlockDraftTransform,
    conflicts: ScheduleConflictCheck,
    onEditDetails: () -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit
) {
    val hasConflict = conflicts.hasBlockingOrWarningConflict
    val borderColor = if (hasConflict) UnconfirmedAmber else WalletMint
    val bgColor = if (hasConflict) WalletWarningBg else WalletCardBg

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor.copy(alpha = 0.98f),
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 34.dp) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "${draft.original.title}: ${TimeRange.formatHm(draft.original.startMinute)}–${ScheduleGridEngine.formatHmEnd(draft.original.endMinute)} → ${ScheduleGridEngine.formatHmWindow(draft.draftStartMin, draft.draftEndMin)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hasConflict) WalletWarningText else WalletMintAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (hasConflict) {
                    Text(
                        text = "⚠ ${conflicts.summaryVi()}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletWarningText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionChipButton(label = "Sửa", bg = WalletSoftNeutral, fg = WalletInk, onClick = onEditDetails)
                    Spacer(modifier = Modifier.weight(1f))
                    ActionChipButton(label = "Hủy", bg = WalletSoftNeutral, fg = WalletDangerText, onClick = onCancel)
                    ActionChipButton(
                        label = if (conflicts.hardConflicts.isNotEmpty()) "Trùng…" else "✓ Lưu",
                        bg = if (hasConflict) UnconfirmedAmber else WalletMint,
                        fg = Color.White,
                        onClick = onCommit
                    )
                }
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
        shape = RoundedCornerShape(12.dp),
        color = (if (hasConflict) WalletWarningBg else WalletInfoBg).copy(alpha = 0.98f),
        border = BorderStroke(1.5.dp, if (hasConflict) UnconfirmedAmber else PlannedAccentBlue),
        shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 34.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "Đặt \"${record.title}\" (${TimeRange.formatDuration(record.durationMinutes)})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            !hasRange -> "Chạm ô giờ trống để đặt vị trí."
                            hasConflict -> "⚠ ${conflicts!!.summaryVi()} (${ScheduleGridEngine.formatHmWindow(startMinute!!, endMinute!!)})"
                            else -> "$targetDateLocal · ${ScheduleGridEngine.formatHmWindow(startMinute!!, endMinute!!)}"
                        },
                        fontSize = 10.5.sp,
                        color = if (hasConflict) WalletWarningText else WalletInfoText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ActionChipButton(label = "Hủy", bg = WalletCardBg, fg = WalletInk, onClick = onCancel)
                if (hasRange) {
                    ActionChipButton(
                        label = if (hasConflict) "Trùng…" else "✓ Đặt",
                        bg = if (hasConflict) UnconfirmedAmber else WalletMint,
                        fg = Color.White,
                        onClick = onConfirmPlace
                    )
                }
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
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, WalletStroke, RoundedCornerShape(8.dp))
            .walletClickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
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
    quickScrollRequest: Pair<Int, Int>? = null,
    interactionState: ScheduleInteractionState,
    modifier: Modifier = Modifier,
    onViewportMetricsChanged: (Float, Float) -> Unit = { _, _ -> },
    onDragViewportYChanged: (Float) -> Unit = {},
    onTapEmptyAtMinute: (Int) -> Unit,
    onDragSelectEmptyRange: (Int, Int) -> Unit,
    onResizeSelectedEmptySlot: (Int, Int) -> Unit,
    onQuickAddSelectedGroup: (SelectedSlotGroup) -> Unit,
    onClearSelection: () -> Unit,
    onTapBlock: (ScheduleGridBlock) -> Unit,
    onConfirmUnconfirmed: (LedgerRecord) -> Unit,
    onDraftBlockChanged: (ScheduleGridBlock, Int, Int) -> Unit
) {
    val hourHeight = 72.dp
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    val minuteHeightPx = hourHeightPx / 60f
    val scrollState = rememberScrollState()

    var vpCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var activeDragKind by remember { mutableStateOf<String?>(null) }
    var localDragViewportY by remember { mutableFloatStateOf(-1f) }
    var activeAutoScrollTick by remember { mutableStateOf<(() -> Unit)?>(null) }

    val latestOnViewportMetricsChanged by rememberUpdatedState(onViewportMetricsChanged)
    val latestOnDragViewportYChanged by rememberUpdatedState(onDragViewportYChanged)

    LaunchedEffect(scrollState.value, viewportHeightPx) {
        if (viewportHeightPx > 0f) {
            latestOnViewportMetricsChanged(scrollState.value.toFloat(), viewportHeightPx)
        }
    }
    LaunchedEffect(localDragViewportY) {
        latestOnDragViewportYChanged(localDragViewportY)
    }

    LaunchedEffect(activeDragKind) {
        if (activeDragKind != null) {
            val edgeZonePx = with(density) { 52.dp.toPx() }
            val maxStepPx = with(density) { 12.dp.toPx() }
            while (isActive) {
                val yVp = localDragViewportY
                val vpH = viewportHeightPx
                if (vpH > edgeZonePx * 2f && yVp >= 0f) {
                    val delta = when {
                        yVp < edgeZonePx && scrollState.value > 0 -> {
                            val ratio = ((edgeZonePx - yVp) / edgeZonePx).coerceIn(0.15f, 1f)
                            -(ratio * maxStepPx)
                        }
                        yVp > vpH - edgeZonePx && scrollState.value < scrollState.maxValue -> {
                            val ratio = ((yVp - (vpH - edgeZonePx)) / edgeZonePx).coerceIn(0.15f, 1f)
                            ratio * maxStepPx
                        }
                        else -> 0f
                    }
                    if (delta != 0f) {
                        scrollState.scrollBy(delta)
                        activeAutoScrollTick?.invoke()
                    }
                }
                delay(16L)
            }
        }
    }

    LaunchedEffect(dayModel.dateLocal) {
        val initialHour = when {
            isToday -> (nowMinute / 60 - 2).coerceIn(0, 16)
            dayModel.timedBlocks.isNotEmpty() -> (dayModel.timedBlocks.minOf { it.startMinute } / 60 - 1).coerceIn(0, 16)
            else -> 7
        }
        scrollState.scrollTo((initialHour * hourHeightPx).roundToInt())
    }
    LaunchedEffect(quickScrollRequest) {
        val (targetMin, _) = quickScrollRequest ?: return@LaunchedEffect
        val targetPx = (((targetMin / 60f) - 1.2f).coerceIn(0f, 18f) * hourHeightPx).roundToInt()
        scrollState.animateScrollTo(targetPx)
    }

    // Trạng thái kéo live trên lưới
    var liveEmptyDragRange by remember(dayModel.dateLocal) { mutableStateOf<Pair<Int, Int>?>(null) }
    var emptyDragAnchorMin by remember(dayModel.dateLocal) { mutableIntStateOf(0) }

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
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { vpCoords = it }
                .onSizeChanged { viewportHeightPx = it.height.toFloat() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = 4.dp, bottom = 10.dp)
            ) {
                Box(modifier = Modifier.width(50.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (h in 0 until 24) {
                            Box(
                                modifier = Modifier.height(hourHeight).fillMaxWidth().padding(end = 5.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(
                                    text = "%02d:00".format(h),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = WalletMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    if (isToday) {
                        val nowYAxis = (nowMinute / 60f) * hourHeight.value
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = (nowYAxis - 9f).dp)
                                .padding(end = 4.dp)
                                .zIndex(21f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NowIndicatorCoral)
                                .padding(horizontal = 4.5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = TimeRange.formatHm(nowMinute),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                var gridCoords by remember(dayModel.dateLocal) { mutableStateOf<LayoutCoordinates?>(null) }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(hourHeight * 24)
                        .padding(end = 6.dp)
                        .onGloballyPositioned { gridCoords = it }
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
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val startMin = ((startOffset.y / minuteHeightPx).roundToInt()).coerceIn(0, 1425)
                                    emptyDragAnchorMin = startMin
                                    val vc = vpCoords
                                    val gc = gridCoords
                                    localDragViewportY = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                        vc.localPositionOf(gc, startOffset).y
                                    } else {
                                        startOffset.y - scrollState.value
                                    }
                                    val (s, e) = ScheduleGridEngine.snapEmptyDragRange(startMin, startMin + 30)
                                    liveEmptyDragRange = s to e
                                    activeAutoScrollTick = {
                                        val gcTopInVp = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                            vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                        } else {
                                            -scrollState.value.toFloat()
                                        }
                                        val docY = localDragViewportY - gcTopInVp
                                        val curMin = (docY / minuteHeightPx).roundToInt().coerceIn(0, 1440)
                                        liveEmptyDragRange = ScheduleGridEngine.snapEmptyDragRange(emptyDragAnchorMin, curMin)
                                    }
                                    activeDragKind = "empty_range"
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val vc = vpCoords
                                    val gc = gridCoords
                                    localDragViewportY = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                        vc.localPositionOf(gc, change.position).y
                                    } else {
                                        change.position.y - scrollState.value
                                    }
                                    activeAutoScrollTick?.invoke()
                                },
                                onDragEnd = {
                                    val chosen = liveEmptyDragRange
                                    liveEmptyDragRange = null
                                    activeDragKind = null
                                    localDragViewportY = -1f
                                    activeAutoScrollTick = null
                                    if (chosen != null) {
                                        latestOnDragSelectEmptyRange(chosen.first, chosen.second)
                                    }
                                },
                                onDragCancel = {
                                    liveEmptyDragRange = null
                                    activeDragKind = null
                                    localDragViewportY = -1f
                                    activeAutoScrollTick = null
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
                                            text = "Trống ${TimeRange.formatHm(visStart)}–${TimeRange.formatHm(visEnd)}",
                                            fontSize = 10.5.sp,
                                            color = WalletMuted,
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
                            color = WalletMintAccent
                        )
                    }
                }

                // 4. Vùng chọn khoảng trống đã chốt + tay nắm giãn trên/dưới + nút "+ Thêm" ngay tại chỗ
                selectedEmptyOnDay.forEachIndexed { idx, group ->
                    val topDp = (group.startMinute / 60f) * hourHeight.value
                    val hDp = max(36f, (group.durationMinutes / 60f) * hourHeight.value)
                    val latestGroup by rememberUpdatedState(group)
                    var slotTopStartDocY by remember(dayModel.dateLocal, idx) { mutableFloatStateOf(0f) }
                    var slotBotStartDocY by remember(dayModel.dateLocal, idx) { mutableFloatStateOf(0f) }
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = ScheduleGridEngine.formatHmWindow(group.startMinute, group.endMinute),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletMintAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Tay nắm mép trên/dưới cho vùng chọn khoảng trống (tự cuộn khi kéo sát mép, giữ nguyên 1 khối liền)
                        val emptyCompactHandles = hDp < 48f
                        ResizeEdgeHandle(
                            isTopEdge = true,
                            tint = WalletMint,
                            dragKey = "empty_top_${dayModel.dateLocal}_$idx",
                            viewportCoords = vpCoords,
                            staticGridCoords = gridCoords,
                            compactHandle = emptyCompactHandles,
                            modifier = Modifier.align(Alignment.TopCenter),
                            onDragStartAtViewportY = { vpY, gridY ->
                                slotOrigStart = latestGroup.startMinute
                                slotOrigEnd = latestGroup.endMinute
                                slotTopStartDocY = if (gridY >= 0f) gridY else (slotOrigStart / 60f) * hourHeightPx
                                localDragViewportY = if (vpY >= 0f) vpY else -1f
                                activeAutoScrollTick = {
                                    val vc = vpCoords
                                    val gc = gridCoords
                                    val gcTopInVp = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                        vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                    } else {
                                        -scrollState.value.toFloat()
                                    }
                                    val curDocY = localDragViewportY - gcTopInVp
                                    val dMin = ((curDocY - slotTopStartDocY) / minuteHeightPx).roundToInt()
                                    val (s, e) = ScheduleGridEngine.snapResizeStartRange(slotOrigStart, slotOrigEnd, dMin)
                                    latestOnResizeSelectedEmptySlot(s, e)
                                }
                                activeDragKind = "empty_top_${dayModel.dateLocal}_$idx"
                            },
                            onDragAtViewportY = { vpY, gridY ->
                                val vc = vpCoords
                                val gc = gridCoords
                                localDragViewportY = if (gridY >= 0f && vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                    gridY + vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                } else vpY
                                activeAutoScrollTick?.invoke()
                            },
                            onDragEnd = {
                                activeDragKind = null
                                localDragViewportY = -1f
                                activeAutoScrollTick = null
                            },
                            onDragCancel = {
                                activeDragKind = null
                                localDragViewportY = -1f
                                activeAutoScrollTick = null
                            }
                        )
                        ResizeEdgeHandle(
                            isTopEdge = false,
                            tint = WalletMint,
                            dragKey = "empty_bot_${dayModel.dateLocal}_$idx",
                            viewportCoords = vpCoords,
                            staticGridCoords = gridCoords,
                            compactHandle = emptyCompactHandles,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onDragStartAtViewportY = { vpY, gridY ->
                                slotOrigStart = latestGroup.startMinute
                                slotOrigEnd = latestGroup.endMinute
                                slotBotStartDocY = if (gridY >= 0f) gridY else (slotOrigEnd / 60f) * hourHeightPx
                                localDragViewportY = if (vpY >= 0f) vpY else -1f
                                activeAutoScrollTick = {
                                    val vc = vpCoords
                                    val gc = gridCoords
                                    val gcTopInVp = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                        vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                    } else {
                                        -scrollState.value.toFloat()
                                    }
                                    val curDocY = localDragViewportY - gcTopInVp
                                    val dMin = ((curDocY - slotBotStartDocY) / minuteHeightPx).roundToInt()
                                    val (s, e) = ScheduleGridEngine.snapResizeRange(slotOrigStart, slotOrigEnd, dMin)
                                    latestOnResizeSelectedEmptySlot(s, e)
                                }
                                activeDragKind = "empty_bot_${dayModel.dateLocal}_$idx"
                            },
                            onDragAtViewportY = { vpY, gridY ->
                                val vc = vpCoords
                                val gc = gridCoords
                                localDragViewportY = if (gridY >= 0f && vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                    gridY + vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                } else vpY
                                activeAutoScrollTick?.invoke()
                            },
                            onDragEnd = {
                                activeDragKind = null
                                localDragViewportY = -1f
                                activeAutoScrollTick = null
                            },
                            onDragCancel = {
                                activeDragKind = null
                                localDragViewportY = -1f
                                activeAutoScrollTick = null
                            }
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
                                .border(1.dp, WalletStroke, RoundedCornerShape(10.dp))
                        )
                    }

                    val topDp = (effStart / 60f) * hourHeight.value
                    val blockHeightDp = max(28f, (spanMinutes / 60f) * hourHeight.value)

                    val latestBlock by rememberUpdatedState(block)
                    val latestEffRange by rememberUpdatedState(effStart to effEnd)
                    val latestIsSelected by rememberUpdatedState(isSelectedThis)
                    var cardCoords by remember(block.recordId) { mutableStateOf<LayoutCoordinates?>(null) }
                    var dragStartDocY by remember(block.recordId) { mutableFloatStateOf(0f) }
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
                        viewportCoords = vpCoords,
                        staticGridCoords = gridCoords,
                        modifier = Modifier
                            .offset(x = if (isDraggingThis || isDraftedThis) 2.dp else laneOffsetDp + 2.dp, y = topDp.dp)
                            .width(
                                if (isDraggingThis || isDraftedThis) (canvasWidthDp - 4.dp).coerceAtLeast(60.dp)
                                else (laneWidthDp - 4.dp).coerceAtLeast(48.dp)
                            )
                            .height(blockHeightDp.dp)
                            .zIndex(if (isSelectedThis) 12f else 1f)
                            .onGloballyPositioned { cardCoords = it }
                            .then(
                                if (block.canDragMoveOrResize && block.record != null && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                                    Modifier.pointerInput(block.recordId, block.startMinute, block.endMinute) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            // Luôn cần nhấn giữ mới nhấc thẻ để cuộn dọc trên thẻ (kể cả thẻ đang chọn như Ngủ) không bị cướp thao tác
                                            val dragTrigger = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                            val vc = vpCoords
                                            val gc = gridCoords
                                            val cc = cardCoords
                                            val startGridY = if (gc != null && cc != null && gc.isAttached && cc.isAttached) {
                                                gc.localPositionOf(cc, dragTrigger.position).y
                                            } else -1f
                                            val startVpY = if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                vc.localPositionOf(cc, dragTrigger.position).y
                                            } else -1f
                                            val (baseS, baseE) = latestEffRange
                                            val baseDocY = if (startGridY >= 0f) startGridY else (baseS / 60f) * hourHeightPx
                                            dragOrigStartMin = baseS
                                            dragOrigEndMin = baseE
                                            dragStartDocY = baseDocY
                                            draggingBlockId = latestBlock.recordId
                                            dragPreviewStartMin = baseS
                                            dragPreviewEndMin = baseE
                                            localDragViewportY = startVpY
                                            activeAutoScrollTick = {
                                                val gcTopInVp = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                                    vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                                } else {
                                                    -scrollState.value.toFloat()
                                                }
                                                val curDocY = localDragViewportY - gcTopInVp
                                                val deltaMin = ((curDocY - dragStartDocY) / minuteHeightPx).roundToInt()
                                                val (s, e) = ScheduleGridEngine.snapMoveRange(dragOrigStartMin, dragOrigEndMin, deltaMin)
                                                dragPreviewStartMin = s
                                                dragPreviewEndMin = e
                                            }
                                            activeDragKind = "block_body_${latestBlock.recordId}"

                                            val completed = drag(dragTrigger.id) { change ->
                                                change.consume()
                                                if (vc != null && gc != null && cc != null && vc.isAttached && gc.isAttached && cc.isAttached) {
                                                    val curG = gc.localPositionOf(cc, change.position).y
                                                    localDragViewportY = curG + vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                                } else if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                    localDragViewportY = vc.localPositionOf(cc, change.position).y
                                                }
                                                activeAutoScrollTick?.invoke()
                                            }
                                            val s = dragPreviewStartMin
                                            val e = dragPreviewEndMin
                                            draggingBlockId = null
                                            activeDragKind = null
                                            localDragViewportY = -1f
                                            activeAutoScrollTick = null
                                            if (completed && (s != latestBlock.startMinute || e != latestBlock.endMinute)) {
                                                latestOnDraftBlockChanged(latestBlock, s, e)
                                            }
                                        }
                                    }
                                } else Modifier
                            ),
                        onClick = { onTapBlock(block) },
                        onQuickConfirm = block.record?.let { rec ->
                            if (block.visualState == ScheduleBlockVisualState.UNCONFIRMED) {
                                { onConfirmUnconfirmed(rec) }
                            } else null
                        },
                        onTopEdgeDragStartAtVpY = { vpY, gridY ->
                            val (baseS, baseE) = latestEffRange
                            dragOrigStartMin = baseS
                            dragOrigEndMin = baseE
                            draggingBlockId = latestBlock.recordId
                            dragPreviewStartMin = baseS
                            dragPreviewEndMin = baseE
                            dragStartDocY = if (gridY >= 0f) gridY else (baseS / 60f) * hourHeightPx
                            localDragViewportY = vpY
                            activeAutoScrollTick = {
                                val vc = vpCoords
                                val gc = gridCoords
                                val gcTopInVp = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                    vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                } else {
                                    -scrollState.value.toFloat()
                                }
                                val curDocY = localDragViewportY - gcTopInVp
                                val dMin = ((curDocY - dragStartDocY) / minuteHeightPx).roundToInt()
                                val (s, e) = ScheduleGridEngine.snapResizeStartRange(dragOrigStartMin, dragOrigEndMin, dMin)
                                dragPreviewStartMin = s
                                dragPreviewEndMin = e
                            }
                            activeDragKind = "block_top_${latestBlock.recordId}"
                        },
                        onTopEdgeDragAtVpY = { vpY, gridY ->
                            val vc = vpCoords
                            val gc = gridCoords
                            localDragViewportY = if (gridY >= 0f && vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                gridY + vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                            } else vpY
                            activeAutoScrollTick?.invoke()
                        },
                        onTopEdgeDragEnd = {
                            val s = dragPreviewStartMin
                            val e = dragPreviewEndMin
                            draggingBlockId = null
                            activeDragKind = null
                            localDragViewportY = -1f
                            activeAutoScrollTick = null
                            if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                latestOnDraftBlockChanged(latestBlock, s, e)
                            }
                        },
                        onTopEdgeDragCancel = {
                            draggingBlockId = null
                            activeDragKind = null
                            localDragViewportY = -1f
                            activeAutoScrollTick = null
                        },
                        onBottomEdgeDragStartAtVpY = { vpY, gridY ->
                            val (baseS, baseE) = latestEffRange
                            dragOrigStartMin = baseS
                            dragOrigEndMin = baseE
                            draggingBlockId = latestBlock.recordId
                            dragPreviewStartMin = baseS
                            dragPreviewEndMin = baseE
                            dragStartDocY = if (gridY >= 0f) gridY else (baseE / 60f) * hourHeightPx
                            localDragViewportY = vpY
                            activeAutoScrollTick = {
                                val vc = vpCoords
                                val gc = gridCoords
                                val gcTopInVp = if (vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                    vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                                } else {
                                    -scrollState.value.toFloat()
                                }
                                val curDocY = localDragViewportY - gcTopInVp
                                val deltaEndMin = ((curDocY - dragStartDocY) / minuteHeightPx).roundToInt()
                                val (s, e) = ScheduleGridEngine.snapResizeRange(dragOrigStartMin, dragOrigEndMin, deltaEndMin)
                                dragPreviewStartMin = s
                                dragPreviewEndMin = e
                            }
                            activeDragKind = "block_bot_${latestBlock.recordId}"
                        },
                        onBottomEdgeDragAtVpY = { vpY, gridY ->
                            val vc = vpCoords
                            val gc = gridCoords
                            localDragViewportY = if (gridY >= 0f && vc != null && gc != null && vc.isAttached && gc.isAttached) {
                                gridY + vc.localPositionOf(gc, androidx.compose.ui.geometry.Offset.Zero).y
                            } else vpY
                            activeAutoScrollTick?.invoke()
                        },
                        onBottomEdgeDragEnd = {
                            val s = dragPreviewStartMin
                            val e = dragPreviewEndMin
                            draggingBlockId = null
                            activeDragKind = null
                            localDragViewportY = -1f
                            activeAutoScrollTick = null
                            if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                latestOnDraftBlockChanged(latestBlock, s, e)
                            }
                        },
                        onBottomEdgeDragCancel = {
                            draggingBlockId = null
                            activeDragKind = null
                            localDragViewportY = -1f
                            activeAutoScrollTick = null
                        }
                    )
                }

                if (isToday) {
                    val nowY = (nowMinute / 60f) * hourHeight.value
                    // Vẽ đường giờ hiện tại phía dưới lớp thẻ (zIndex 0.5f) để KHÔNG bao giờ che tên phiên ngắn;
                    // nhãn giờ "HH:mm" đã được ghim gọn trong cột giờ bên trái.
                    Row(
                        modifier = Modifier
                            .offset(y = (nowY - 3f).dp)
                            .fillMaxWidth()
                            .zIndex(0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NowIndicatorCoral)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.5.dp)
                                .background(NowIndicatorCoral.copy(alpha = 0.8f))
                        )
                    }
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
    viewportCoords: LayoutCoordinates? = null,
    staticGridCoords: LayoutCoordinates? = null,
    compactHandle: Boolean = false,
    handleWidth: Dp = 112.dp,
    modifier: Modifier = Modifier,
    onDragStartAtViewportY: (Float, Float) -> Unit = { _, _ -> },
    onDragAtViewportY: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestVpCoords by rememberUpdatedState(viewportCoords)
    val latestGridCoords by rememberUpdatedState(staticGridCoords)
    val latestOnDragStartAtVpY by rememberUpdatedState(onDragStartAtViewportY)
    val latestOnDragAtVpY by rememberUpdatedState(onDragAtViewportY)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

    // Tay nắm rộng theo mép thẻ (tránh hụt khi chạm lệch tâm), chiều cao co gọn khi thẻ thấp để 2 cạnh không đè nhau
    Box(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .widthIn(min = 48.dp, max = handleWidth)
            .height(if (compactHandle) 13.dp else 22.dp)
            .zIndex(2f)
            .onGloballyPositioned { handleCoords = it }
            .pointerInput(dragKey) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val vc = latestVpCoords
                        val sgc = latestGridCoords
                        val hc = handleCoords
                        val gridY = if (sgc != null && hc != null && sgc.isAttached && hc.isAttached) {
                            sgc.localPositionOf(hc, offset).y
                        } else -1f
                        val vpY = if (vc != null && hc != null && vc.isAttached && hc.isAttached) {
                            vc.localPositionOf(hc, offset).y
                        } else -1f
                        latestOnDragStartAtVpY(vpY, gridY)
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val vc = latestVpCoords
                        val sgc = latestGridCoords
                        val hc = handleCoords
                        val gridY = if (sgc != null && hc != null && sgc.isAttached && hc.isAttached) {
                            sgc.localPositionOf(hc, change.position).y
                        } else -1f
                        val vpY = if (vc != null && hc != null && vc.isAttached && hc.isAttached) {
                            vc.localPositionOf(hc, change.position).y
                        } else -1f
                        if (vpY >= -2000f || gridY >= -2000f) {
                            latestOnDragAtVpY(vpY, gridY)
                        }
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
                .width(38.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tint)
                .border(0.75.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
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
    viewportCoords: LayoutCoordinates? = null,
    staticGridCoords: LayoutCoordinates? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onQuickConfirm: (() -> Unit)? = null,
    onTopEdgeDragStartAtVpY: (Float, Float) -> Unit = { _, _ -> },
    onTopEdgeDragAtVpY: (Float, Float) -> Unit = { _, _ -> },
    onTopEdgeDragEnd: () -> Unit = {},
    onTopEdgeDragCancel: () -> Unit = {},
    onBottomEdgeDragStartAtVpY: (Float, Float) -> Unit = { _, _ -> },
    onBottomEdgeDragAtVpY: (Float, Float) -> Unit = { _, _ -> },
    onBottomEdgeDragEnd: () -> Unit = {},
    onBottomEdgeDragCancel: () -> Unit = {}
) {
    val catColor = Color(block.category.colorHex)
    val (bgColor, accentColor, isDashed) = when (block.visualState) {
        ScheduleBlockVisualState.COMPLETED -> Triple(catColor.copy(alpha = 0.18f), catColor, false)
        ScheduleBlockVisualState.PLANNED -> Triple(WalletInfoBg, PlannedAccentBlue, true)
        ScheduleBlockVisualState.ACTIVE -> Triple(WalletMintSoft, ActiveAccentCyan, false)
        ScheduleBlockVisualState.UNCONFIRMED -> Triple(WalletWarningBg, WalletWarningText, true)
    }

    val effectiveStrokeColor = when {
        hasConflictWarning -> NowIndicatorCoral
        isDraggingOrDraft || isSelected -> WalletMintAccent
        else -> accentColor.copy(alpha = 0.55f)
    }
    val borderStrokePx = with(LocalDensity.current) {
        (if (isDraggingOrDraft || isSelected || hasConflictWarning) 2.2.dp else 1.3.dp).toPx()
    }
    val cornerPx = with(LocalDensity.current) { 10.dp.toPx() }
    val compact = blockHeightDp < 44.dp
    val showQuickAction = blockHeightDp >= 82.dp && block.laneCount == 1
    val canResizeThis = block.canDragMoveOrResize && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext &&
        (isSelected || isDraggingOrDraft)

    Box(
        modifier = modifier
            .clipToBounds()
            .clip(RoundedCornerShape(10.dp))
            .background(WalletCardBg)
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
                val effectiveTimeLabel = if (isDraggingOrDraft) {
                    "${TimeRange.formatHm(effectiveStartMin)}–${ScheduleGridEngine.formatHmEnd(effectiveEndMin)}"
                } else {
                    "${block.displayStartHm}–${block.displayEndHm}"
                }
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
                        fontSize = if (compact) 11.5.sp else 13.5.sp,
                        lineHeight = if (compact) 13.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk,
                        maxLines = if (blockHeightDp >= 52.dp) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (block.laneCount == 1) {
                        if (compact) {
                            Text(
                                text = effectiveTimeLabel,
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

                if (compact && block.laneCount > 1) {
                    Text(
                        text = draggingLabel ?: effectiveTimeLabel,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasConflictWarning) NowIndicatorCoral else accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!compact) {
                    val timeWindow = draggingLabel ?: buildString {
                        append(effectiveTimeLabel)
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
            val compactResizeHandle = blockHeightDp < 48.dp
            ResizeEdgeHandle(
                isTopEdge = true,
                tint = (if (isSelected || isDraggingOrDraft) WalletMintAccent else accentColor).copy(alpha = 0.78f),
                dragKey = "block_top_${block.recordId}",
                viewportCoords = viewportCoords,
                staticGridCoords = staticGridCoords,
                compactHandle = compactResizeHandle,
                modifier = Modifier.align(Alignment.TopCenter),
                onDragStartAtViewportY = onTopEdgeDragStartAtVpY,
                onDragAtViewportY = onTopEdgeDragAtVpY,
                onDragEnd = onTopEdgeDragEnd,
                onDragCancel = onTopEdgeDragCancel
            )
            ResizeEdgeHandle(
                isTopEdge = false,
                tint = (if (isSelected || isDraggingOrDraft) WalletMintAccent else accentColor).copy(alpha = 0.78f),
                dragKey = "block_bot_${block.recordId}",
                viewportCoords = viewportCoords,
                staticGridCoords = staticGridCoords,
                compactHandle = compactResizeHandle,
                modifier = Modifier.align(Alignment.BottomCenter),
                onDragStartAtViewportY = onBottomEdgeDragStartAtVpY,
                onDragAtViewportY = onBottomEdgeDragAtVpY,
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
    quickScrollRequest: Pair<Int, Int>? = null,
    interactionState: ScheduleInteractionState,
    modifier: Modifier = Modifier,
    onViewportMetricsChanged: (Float, Float) -> Unit = { _, _ -> },
    onDragViewportYChanged: (Float) -> Unit = {},
    onSelectDay: (String) -> Unit,
    onTapCellAtMinute: (DayScheduleModel, Int) -> Unit,
    onDragSelectEmptyRange: (DayScheduleModel, Int, Int) -> Unit,
    onTapBlock: (ScheduleGridBlock) -> Unit,
    onDraftBlockMoved: (ScheduleGridBlock, Int, Int) -> Unit
) {
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hourPx = with(density) { hourHeight.toPx() }
    val minutePx = hourPx / 60f

    var vpCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var activeDragKind by remember { mutableStateOf<String?>(null) }
    var localDragViewportY by remember { mutableFloatStateOf(-1f) }
    var activeAutoScrollTick by remember { mutableStateOf<(() -> Unit)?>(null) }

    val latestOnViewportMetricsChanged by rememberUpdatedState(onViewportMetricsChanged)
    val latestOnDragViewportYChanged by rememberUpdatedState(onDragViewportYChanged)

    LaunchedEffect(vScrollState.value, viewportHeightPx) {
        if (viewportHeightPx > 0f) {
            latestOnViewportMetricsChanged(vScrollState.value.toFloat(), viewportHeightPx)
        }
    }
    LaunchedEffect(localDragViewportY) {
        latestOnDragViewportYChanged(localDragViewportY)
    }

    LaunchedEffect(activeDragKind) {
        if (activeDragKind != null) {
            val edgeZonePx = with(density) { 48.dp.toPx() }
            val maxStepPx = with(density) { 12.dp.toPx() }
            while (isActive) {
                val yVp = localDragViewportY
                val vpH = viewportHeightPx
                if (vpH > edgeZonePx * 2f && yVp >= 0f) {
                    val delta = when {
                        yVp < edgeZonePx && vScrollState.value > 0 -> {
                            val ratio = ((edgeZonePx - yVp) / edgeZonePx).coerceIn(0.15f, 1f)
                            -(ratio * maxStepPx)
                        }
                        yVp > vpH - edgeZonePx && vScrollState.value < vScrollState.maxValue -> {
                            val ratio = ((yVp - (vpH - edgeZonePx)) / edgeZonePx).coerceIn(0.15f, 1f)
                            ratio * maxStepPx
                        }
                        else -> 0f
                    }
                    if (delta != 0f) {
                        vScrollState.scrollBy(delta)
                        activeAutoScrollTick?.invoke()
                    }
                }
                delay(16L)
            }
        }
    }

    LaunchedEffect(days.size, compactColumns) {
        val dayBlocks = days.flatMap { it.timedBlocks }.filter { it.endMinute > 360 }
        val earliestDayHour = dayBlocks.minOfOrNull { it.startMinute / 60 } ?: 8
        val targetHour = if (compactColumns) {
            minOf((nowMinute / 60 - 9).coerceIn(6, 12), (earliestDayHour - 1).coerceIn(6, 12))
        } else {
            minOf((nowMinute / 60 - 4).coerceIn(6, 15), (earliestDayHour - 1).coerceIn(6, 15))
        }
        vScrollState.scrollTo((targetHour * hourPx).roundToInt())
    }
    LaunchedEffect(quickScrollRequest) {
        val (targetMin, _) = quickScrollRequest ?: return@LaunchedEffect
        val targetPx = (((targetMin / 60f) - 1.2f).coerceIn(0f, 18f) * hourPx).roundToInt()
        vScrollState.animateScrollTo(targetPx)
    }

    var draggingEmptyDate by remember { mutableStateOf<String?>(null) }
    var emptyDragAnchorMin by remember { mutableIntStateOf(0) }
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
            val needsHorizontalScroll = if (days.size <= 3) false else (minColWidth * days.size) > gridAvailWidth
            val colWidth = if (needsHorizontalScroll) {
                val visibleCols = (gridAvailWidth / minColWidth).toInt().coerceAtLeast(1)
                (gridAvailWidth / visibleCols.toFloat()).coerceAtLeast(minColWidth)
            } else {
                gridAvailWidth / days.size.coerceAtLeast(1)
            }
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
                        .padding(vertical = 2.dp),
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
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isToday) WalletMint else Color.Transparent)
                                    .walletClickable(onClickLabel = "Mở ngày ${day.dateLocal}") {
                                        onSelectDay(day.dateLocal)
                                    }
                                    .padding(vertical = 3.dp, horizontal = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${shortWeekdayVi(d.dayOfWeek)} · ${d.format(DateTimeFormatter.ofPattern("dd/MM"))}",
                                    fontSize = if (compactColumns || colWidth < 96.dp) 10.sp else 12.sp,
                                    lineHeight = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isToday) Color.White else WalletInk,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (colSummary.isNotEmpty()) {
                                    Text(
                                        text = colSummary,
                                        fontSize = if (compactColumns || colWidth < 96.dp) 8.sp else 9.5.sp,
                                        lineHeight = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isToday) Color(0xFFD6EFE3) else WalletMintAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = WalletStroke)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { vpCoords = it }
                        .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScrollState)
                        .padding(top = 2.dp, bottom = 10.dp)
                ) {
                    Box(modifier = Modifier.width(axisWidth)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                        if (days.any { it.dateLocal == todayStr }) {
                            val nowAxisY = (nowMinute / 60f) * hourHeight.value
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(y = (nowAxisY - 8f).dp)
                                    .padding(end = 2.dp)
                                    .zIndex(21f)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(NowIndicatorCoral)
                                    .padding(horizontal = 3.5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = TimeRange.formatHm(nowMinute),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
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
                                var colCoords by remember(day.dateLocal) { mutableStateOf<LayoutCoordinates?>(null) }
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .width(colWidth)
                                        .fillMaxHeight()
                                        .border(0.5.dp, WalletDivider)
                                        .onGloballyPositioned { colCoords = it }
                                        .pointerInput(day.dateLocal) {
                                            detectTapGestures { pos ->
                                                val minute = ((pos.y / minutePx).roundToInt()).coerceIn(0, 1439)
                                                latestOnTapCellAtMinute(latestDay, minute)
                                            }
                                        }
                                        .pointerInput(day.dateLocal) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { pos ->
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val s = ((pos.y / minutePx).roundToInt()).coerceIn(0, 1425)
                                                    draggingEmptyDate = day.dateLocal
                                                    emptyDragAnchorMin = s
                                                    val vc = vpCoords
                                                    val cc = colCoords
                                                    localDragViewportY = if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                        vc.localPositionOf(cc, pos).y
                                                    } else pos.y - vScrollState.value
                                                    liveEmptyRange = ScheduleGridEngine.snapEmptyDragRange(s, s + 30)
                                                    activeAutoScrollTick = {
                                                        val docY = localDragViewportY + vScrollState.value
                                                        val c = (docY / minutePx).roundToInt().coerceIn(0, 1440)
                                                        liveEmptyRange = ScheduleGridEngine.snapEmptyDragRange(emptyDragAnchorMin, c)
                                                    }
                                                    activeDragKind = "m_empty_${day.dateLocal}"
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val vc = vpCoords
                                                    val cc = colCoords
                                                    if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                        localDragViewportY = vc.localPositionOf(cc, change.position).y
                                                    } else {
                                                        localDragViewportY = change.position.y - vScrollState.value
                                                    }
                                                    activeAutoScrollTick?.invoke()
                                                },
                                                onDragEnd = {
                                                    val chosen = liveEmptyRange
                                                    draggingEmptyDate = null
                                                    liveEmptyRange = null
                                                    activeDragKind = null
                                                    localDragViewportY = -1f
                                                    activeAutoScrollTick = null
                                                    if (chosen != null) {
                                                        latestOnDragSelectEmptyRange(latestDay, chosen.first, chosen.second)
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingEmptyDate = null
                                                    liveEmptyRange = null
                                                    activeDragKind = null
                                                    localDragViewportY = -1f
                                                    activeAutoScrollTick = null
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
                                    val minSlotDp = if (compactColumns) 12f else 18f
                                    selectedSlots.filter { it.dateLocal == day.dateLocal }.forEach { slot ->
                                        val topDp = (slot.startMinute / 60f) * hourHeight.value
                                        val hDp = max(minSlotDp, ((slot.endMinute - slot.startMinute) / 60f) * hourHeight.value)
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
                                        val hDp = max(minSlotDp, ((e - s) / 60f) * hourHeight.value)
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
                                        val hDp = max(minSlotDp, ((e - s) / 60f) * hourHeight.value)
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
                                        val latestIsSelected by rememberUpdatedState(isSelThis)
                                        var mCardCoords by remember(block.recordId, day.dateLocal) { mutableStateOf<LayoutCoordinates?>(null) }
                                        var colStartDocY by remember(block.recordId, day.dateLocal) { mutableFloatStateOf(0f) }
                                        var colDragOrigStart by remember(block.recordId, day.dateLocal) { mutableIntStateOf(block.startMinute) }
                                        var colDragOrigEnd by remember(block.recordId, day.dateLocal) { mutableIntStateOf(block.endMinute) }

                                        Box(
                                            modifier = Modifier
                                                .offset(x = xDp, y = topDp.dp)
                                                .width((wDp - 1.dp).coerceAtLeast(20.dp))
                                                .height(hDp.dp)
                                                .zIndex(if (isSelThis) 10f else 1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(WalletCardBg)
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
                                                .onGloballyPositioned { mCardCoords = it }
                                                .then(
                                                    if (block.canDragMoveOrResize && block.record != null && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                                                        Modifier.pointerInput(block.recordId, block.startMinute, block.endMinute) {
                                                            awaitEachGesture {
                                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                                // Luôn nhấn giữ mới kéo đổi giờ trên thẻ đa ngày để vuốt cuộn không bị nhạy
                                                                val trigger = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                                val vc = vpCoords
                                                                val cc = mCardCoords
                                                                val startVpY = if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                                    vc.localPositionOf(cc, trigger.position).y
                                                                } else -1f
                                                                val (baseS, baseE) = latestEffRange
                                                                colDragOrigStart = baseS
                                                                colDragOrigEnd = baseE
                                                                colStartDocY = if (startVpY >= 0f) startVpY + vScrollState.value else (baseS / 60f) * hourPx
                                                                draggingBlockId = latestBlock.recordId
                                                                draggingBlockDate = day.dateLocal
                                                                dragPreviewStartMin = baseS
                                                                dragPreviewEndMin = baseE
                                                                localDragViewportY = startVpY
                                                                activeAutoScrollTick = {
                                                                    val curDocY = localDragViewportY + vScrollState.value
                                                                    val deltaMin = ((curDocY - colStartDocY) / minutePx).roundToInt()
                                                                    val (s, e) = ScheduleGridEngine.snapMoveRange(colDragOrigStart, colDragOrigEnd, deltaMin)
                                                                    dragPreviewStartMin = s
                                                                    dragPreviewEndMin = e
                                                                }
                                                                activeDragKind = "m_block_${latestBlock.recordId}"

                                                                val completed = drag(trigger.id) { change ->
                                                                    change.consume()
                                                                    if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                                        localDragViewportY = vc.localPositionOf(cc, change.position).y
                                                                    }
                                                                    activeAutoScrollTick?.invoke()
                                                                }
                                                                val s = dragPreviewStartMin
                                                                val e = dragPreviewEndMin
                                                                draggingBlockId = null
                                                                draggingBlockDate = null
                                                                activeDragKind = null
                                                                localDragViewportY = -1f
                                                                activeAutoScrollTick = null
                                                                if (completed && (s != latestBlock.startMinute || e != latestBlock.endMinute)) {
                                                                    latestOnDraftBlockMoved(latestBlock, s, e)
                                                                }
                                                            }
                                                        }
                                                    } else Modifier
                                                )
                                                .walletClickable(onClickLabel = "Chọn ${block.title}") { onTapBlock(block) }
                                        ) {
                                            Text(
                                                text = block.title,
                                                fontSize = if (compactColumns) 11.sp else 11.5.sp,
                                                lineHeight = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WalletInk,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                            if (isSelThis && block.canDragMoveOrResize && block.record != null && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                                                val mCompactHandle = hDp < 48f
                                                ResizeEdgeHandle(
                                                    isTopEdge = true,
                                                    tint = WalletMintAccent.copy(alpha = 0.85f),
                                                    dragKey = "m_top_${block.recordId}_${day.dateLocal}",
                                                    viewportCoords = vpCoords,
                                                    staticGridCoords = colCoords,
                                                    compactHandle = mCompactHandle,
                                                    handleWidth = 56.dp,
                                                    modifier = Modifier.align(Alignment.TopCenter),
                                                    onDragStartAtViewportY = { vpY, gridY ->
                                                        val (baseS, baseE) = latestEffRange
                                                        colDragOrigStart = baseS
                                                        colDragOrigEnd = baseE
                                                        colStartDocY = if (gridY >= 0f) gridY else (baseS / 60f) * hourPx
                                                        draggingBlockId = latestBlock.recordId
                                                        draggingBlockDate = day.dateLocal
                                                        dragPreviewStartMin = baseS
                                                        dragPreviewEndMin = baseE
                                                        localDragViewportY = vpY
                                                        activeAutoScrollTick = {
                                                            val vc = vpCoords
                                                            val cc = colCoords
                                                            val colTopInVp = if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                                vc.localPositionOf(cc, androidx.compose.ui.geometry.Offset.Zero).y
                                                            } else {
                                                                -vScrollState.value.toFloat()
                                                            }
                                                            val curDocY = localDragViewportY - colTopInVp
                                                            val dMin = ((curDocY - colStartDocY) / minutePx).roundToInt()
                                                            val (s, e) = ScheduleGridEngine.snapResizeStartRange(colDragOrigStart, colDragOrigEnd, dMin)
                                                            dragPreviewStartMin = s
                                                            dragPreviewEndMin = e
                                                        }
                                                        activeDragKind = "m_top_${latestBlock.recordId}"
                                                    },
                                                    onDragAtViewportY = { vpY, gridY ->
                                                        val vc = vpCoords
                                                        val cc = colCoords
                                                        localDragViewportY = if (gridY >= 0f && vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                            gridY + vc.localPositionOf(cc, androidx.compose.ui.geometry.Offset.Zero).y
                                                        } else vpY
                                                        activeAutoScrollTick?.invoke()
                                                    },
                                                    onDragEnd = {
                                                        val s = dragPreviewStartMin
                                                        val e = dragPreviewEndMin
                                                        draggingBlockId = null
                                                        draggingBlockDate = null
                                                        activeDragKind = null
                                                        localDragViewportY = -1f
                                                        activeAutoScrollTick = null
                                                        if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                                            latestOnDraftBlockMoved(latestBlock, s, e)
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggingBlockId = null
                                                        draggingBlockDate = null
                                                        activeDragKind = null
                                                        localDragViewportY = -1f
                                                        activeAutoScrollTick = null
                                                    }
                                                )
                                                ResizeEdgeHandle(
                                                    isTopEdge = false,
                                                    tint = WalletMintAccent.copy(alpha = 0.85f),
                                                    dragKey = "m_bot_${block.recordId}_${day.dateLocal}",
                                                    viewportCoords = vpCoords,
                                                    staticGridCoords = colCoords,
                                                    compactHandle = mCompactHandle,
                                                    handleWidth = 56.dp,
                                                    modifier = Modifier.align(Alignment.BottomCenter),
                                                    onDragStartAtViewportY = { vpY, gridY ->
                                                        val (baseS, baseE) = latestEffRange
                                                        colDragOrigStart = baseS
                                                        colDragOrigEnd = baseE
                                                        colStartDocY = if (gridY >= 0f) gridY else (baseE / 60f) * hourPx
                                                        draggingBlockId = latestBlock.recordId
                                                        draggingBlockDate = day.dateLocal
                                                        dragPreviewStartMin = baseS
                                                        dragPreviewEndMin = baseE
                                                        localDragViewportY = vpY
                                                        activeAutoScrollTick = {
                                                            val vc = vpCoords
                                                            val cc = colCoords
                                                            val colTopInVp = if (vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                                vc.localPositionOf(cc, androidx.compose.ui.geometry.Offset.Zero).y
                                                            } else {
                                                                -vScrollState.value.toFloat()
                                                            }
                                                            val curDocY = localDragViewportY - colTopInVp
                                                            val dMin = ((curDocY - colStartDocY) / minutePx).roundToInt()
                                                            val (s, e) = ScheduleGridEngine.snapResizeRange(colDragOrigStart, colDragOrigEnd, dMin)
                                                            dragPreviewStartMin = s
                                                            dragPreviewEndMin = e
                                                        }
                                                        activeDragKind = "m_bot_${latestBlock.recordId}"
                                                    },
                                                    onDragAtViewportY = { vpY, gridY ->
                                                        val vc = vpCoords
                                                        val cc = colCoords
                                                        localDragViewportY = if (gridY >= 0f && vc != null && cc != null && vc.isAttached && cc.isAttached) {
                                                            gridY + vc.localPositionOf(cc, androidx.compose.ui.geometry.Offset.Zero).y
                                                        } else vpY
                                                        activeAutoScrollTick?.invoke()
                                                    },
                                                    onDragEnd = {
                                                        val s = dragPreviewStartMin
                                                        val e = dragPreviewEndMin
                                                        draggingBlockId = null
                                                        draggingBlockDate = null
                                                        activeDragKind = null
                                                        localDragViewportY = -1f
                                                        activeAutoScrollTick = null
                                                        if (s != latestBlock.startMinute || e != latestBlock.endMinute) {
                                                            latestOnDraftBlockMoved(latestBlock, s, e)
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggingBlockId = null
                                                        draggingBlockDate = null
                                                        activeDragKind = null
                                                        localDragViewportY = -1f
                                                        activeAutoScrollTick = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (isDayToday) {
                                        val nowY = (nowMinute / 60f) * hourHeight.value
                                        Box(
                                            modifier = Modifier
                                                .offset(y = nowY.dp)
                                                .fillMaxWidth()
                                                .height(1.5.dp)
                                                .zIndex(0.5f)
                                                .background(NowIndicatorCoral.copy(alpha = 0.8f))
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
                                    // Ngày không có dữ liệu giữ khoảng trắng sạch thay vì lặp chữ "Trống" mọi ô.
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
