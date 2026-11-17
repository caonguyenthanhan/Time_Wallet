package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.timewallet.mvp.domain.usecase.ScheduleGridBlock
import com.timewallet.mvp.domain.usecase.ScheduleGridEngine
import com.timewallet.mvp.domain.usecase.ScheduleViewMode
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintBright
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintMid
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
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
import kotlin.math.roundToInt

private val NowIndicatorCoral = Color(0xFFE53935)
private val PlannedAccentBlue = Color(0xFF3563E9)
private val ActiveAccentCyan = Color(0xFF0E9F6E)
private val UnconfirmedAmber = Color(0xFFD97706)

/**
 * Màn hình Lịch trình & Ghi nhận thống nhất theo chuẩn Google Calendar:
 * - 4 chế độ xem: Ngày / 3 ngày / Tuần / Tháng
 * - Chạm khoảng trống -> thêm hoạt động đúng khung giờ gợi ý
 * - Chạm khối cũ/mới -> mở chỉnh sửa / xác nhận / xóa
 * - Nhấn giữ kéo thân khối để đổi giờ, kéo cạnh đáy để chỉnh thời lượng (±15p)
 * - Phân tách rõ 4 trạng thái (Đã thực hiện, Dự kiến, Đang diễn ra, Chưa xác nhận) + giữ riêng mục Chưa gắn giờ
 */
@OptIn(ExperimentalLayoutApi::class)
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
    embeddedInScrollContainer: Boolean = false
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(todayDateLocal) { runCatching { LocalDate.parse(todayDateLocal) }.getOrDefault(LocalDate.now(zone)) }
    val selectedDate = remember(selectedDateLocal, today) { runCatching { LocalDate.parse(selectedDateLocal) }.getOrDefault(today) }
    val nowMs = System.currentTimeMillis()
    val nowLocalTime = remember(nowMs, zone) { Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime() }
    val nowMinute = nowLocalTime.hour * 60 + nowLocalTime.minute

    var showSearchAndFilter by rememberSaveable { mutableStateOf(false) }
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

    Column(
        modifier = if (embeddedInScrollContainer) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxSize()
        },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header xanh đặc trưng (theo concept Lịch trình xanh của Owner)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (showBackButton) 22.dp else 20.dp))
                .background(Brush.linearGradient(listOf(WalletMintDark, WalletMintMid, WalletMintBright)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showBackButton) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .semantics { contentDescription = "Đóng lịch trình" }
                            .walletClickable(onClickLabel = "Đóng lịch trình", onClick = onBackClick)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(WalletGlyph.BACK, color = Color.White, size = 18.dp)
                    }
                }
                Text(
                    text = "Lịch trình",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (showSearchAndFilter) Color.White else Color.White.copy(alpha = 0.16f))
                        .walletClickable(onClickLabel = "Lọc danh mục và tìm hoạt động") {
                            showSearchAndFilter = !showSearchAndFilter
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!categoryFilter.isNullOrBlank() || searchQuery.isNotBlank()) "Lọc ●" else "Lọc",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showSearchAndFilter) WalletMintDark else Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White)
                        .walletClickable(onClickLabel = "Ghi mới hoạt động") {
                            onAddForSlot(selectedDate.toString(), null, null, selectedDate.isAfter(today))
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GlyphIcon(WalletGlyph.PLUS, color = WalletMintDark, size = 14.dp)
                    Text(
                        text = "Ghi mới",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletMintDark,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Chuyển đổi 4 chế độ xem Google Calendar: Ngày / 3 ngày / Tuần / Tháng
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ScheduleViewMode.entries.forEach { mode ->
                    val isSelected = mode == viewMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .semantics { this.selected = isSelected }
                            .walletClickable(onClickLabel = "Chế độ ${mode.label}", role = Role.Tab) {
                                onSelectViewMode(mode)
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) WalletMintDark else Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Thanh điều hướng ngày + chỉ số tổng Thực tế / Dự kiến / Chưa xác nhận
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .semantics { contentDescription = "Lùi thời gian" }
                        .walletClickable(onClickLabel = "Lùi thời gian") {
                            onSelectDate(stepScheduleDate(selectedDate, viewMode, -1).toString())
                        }
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatScheduleRangeHeading(selectedDate, today, viewMode),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedDate != today) {
                            Box(
                                modifier = Modifier
                                    .walletClickable(onClickLabel = "Về hôm nay") { onSelectDate(today.toString()) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color.White.copy(alpha = 0.22f))
                                        .padding(horizontal = 9.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Hôm nay",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            append("Thực tế: ${TimeRange.formatDuration(headerActualMinutes)}")
                            append(" · Dự kiến: ${TimeRange.formatDuration(headerPlannedMinutes)}")
                            if (headerUnconfirmed > 0) append(" · Chờ xác nhận: $headerUnconfirmed")
                        },
                        fontSize = 11.sp,
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
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Bộ lọc Tag / Danh mục chuyển gọn vào đây thay vì chiếm màn hình chính
        if (showSearchAndFilter) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
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

        // Chú giải 4 trạng thái Kế hoạch vs Thực tế
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendStateChip(color = WalletMint, label = "Đã thực hiện", solid = true)
            LegendStateChip(color = PlannedAccentBlue, label = "Dự kiến", solid = false)
            LegendStateChip(color = ActiveAccentCyan, label = "Đang diễn ra", solid = true, pulse = true)
            LegendStateChip(color = UnconfirmedAmber, label = "Chưa xác nhận", solid = false)
        }

        val gridModifier = if (embeddedInScrollContainer) {
            Modifier.fillMaxWidth().height(540.dp)
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
                    modifier = gridModifier,
                    onTapEmptyMinute = { minute ->
                        val (sMin, eMin) = ScheduleGridEngine.prefillGapRange(minute, dayModel.timedBlocks)
                        onAddForSlot(
                            dayModel.dateLocal,
                            TimeRange.formatHm(sMin),
                            if (eMin >= 1440) "00:00" else TimeRange.formatHm(eMin),
                            selectedDate.isAfter(today)
                        )
                    },
                    onTapBlock = { block ->
                        when {
                            block.isActiveTimer -> onNavigateToTimer()
                            block.record != null -> onEditRecord(block.record)
                        }
                    },
                    onConfirmUnconfirmed = { record -> onConfirmPlannedOrActiveRecord(record) },
                    onMoveOrResize = onMoveOrResizeBlock
                )
            }
            ScheduleViewMode.THREE_DAYS -> {
                MultiDayTimelineGrid(
                    days = multiDayModels,
                    todayStr = today.toString(),
                    nowMinute = nowMinute,
                    hourHeight = 56.dp,
                    modifier = gridModifier,
                    onSelectDay = { dateStr ->
                        onSelectDate(dateStr)
                        onSelectViewMode(ScheduleViewMode.DAY)
                    },
                    onTapCell = { dayModel, minute ->
                        val (sMin, eMin) = ScheduleGridEngine.prefillGapRange(minute, dayModel.timedBlocks)
                        val targetDate = runCatching { LocalDate.parse(dayModel.dateLocal) }.getOrDefault(today)
                        onAddForSlot(
                            dayModel.dateLocal,
                            TimeRange.formatHm(sMin),
                            if (eMin >= 1440) "00:00" else TimeRange.formatHm(eMin),
                            targetDate.isAfter(today)
                        )
                    },
                    onTapBlock = { block ->
                        when {
                            block.isActiveTimer -> onNavigateToTimer()
                            block.record != null -> onEditRecord(block.record)
                        }
                    }
                )
            }
            ScheduleViewMode.WEEK -> {
                MultiDayTimelineGrid(
                    days = multiDayModels,
                    todayStr = today.toString(),
                    nowMinute = nowMinute,
                    hourHeight = 48.dp,
                    compactColumns = true,
                    modifier = gridModifier,
                    onSelectDay = { dateStr ->
                        onSelectDate(dateStr)
                        onSelectViewMode(ScheduleViewMode.DAY)
                    },
                    onTapCell = { dayModel, minute ->
                        val (sMin, eMin) = ScheduleGridEngine.prefillGapRange(minute, dayModel.timedBlocks)
                        val targetDate = runCatching { LocalDate.parse(dayModel.dateLocal) }.getOrDefault(today)
                        onAddForSlot(
                            dayModel.dateLocal,
                            TimeRange.formatHm(sMin),
                            if (eMin >= 1440) "00:00" else TimeRange.formatHm(eMin),
                            targetDate.isAfter(today)
                        )
                    },
                    onTapBlock = { block ->
                        when {
                            block.isActiveTimer -> onNavigateToTimer()
                            block.record != null -> onEditRecord(block.record)
                        }
                    }
                )
            }
            ScheduleViewMode.MONTH -> {
                MonthOverviewGrid(
                    cells = monthCells,
                    selectedDateLocal = selectedDate.toString(),
                    selectedDayModel = selectedDayModel,
                    today = today,
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
                    onEditRecord = onEditRecord
                )
            }
        }

        // Khối riêng bảo toàn bản ghi chỉ có thời lượng (không bao giờ tự gán mốc giờ giả)
        val untimedItems = remember(multiDayModels, selectedDayModel, viewMode) {
            if (viewMode == ScheduleViewMode.MONTH) {
                selectedDayModel.untimedRecords
            } else {
                multiDayModels.flatMap { it.untimedRecords }
            }
        }
        UntimedRecordsTray(
            records = untimedItems,
            onEditRecord = onEditRecord,
            onQuickLogDuration = {
                onAddForSlot(selectedDate.toString(), null, null, selectedDate.isAfter(today))
            }
        )
    }
}

@Composable
private fun DayTimelineGrid(
    dayModel: DayScheduleModel,
    isToday: Boolean,
    nowMinute: Int,
    modifier: Modifier = Modifier,
    onTapEmptyMinute: (Int) -> Unit,
    onTapBlock: (ScheduleGridBlock) -> Unit,
    onConfirmUnconfirmed: (LedgerRecord) -> Unit,
    onMoveOrResize: (recordId: String, dateLocal: String, startHm: String, endHm: String) -> Unit
) {
    val hourHeight = 72.dp
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

    var draggingBlockId by remember { mutableStateOf<String?>(null) }
    var dragPreviewStartMin by remember { mutableStateOf(0) }
    var dragPreviewEndMin by remember { mutableStateOf(0) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            // Trục giờ 00:00 -> 23:00
            Column(modifier = Modifier.width(50.dp)) {
                for (h in 0 until 24) {
                    Box(
                        modifier = Modifier.height(hourHeight).fillMaxWidth().padding(end = 6.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = "%02d:00".format(h),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = WalletMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Vùng sự kiện 24 giờ
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * 24)
                    .padding(end = 8.dp)
            ) {
                val canvasWidthDp = maxWidth

                // 1. Các ô giờ trống có thể chạm trực tiếp để thêm hoạt động
                Column(modifier = Modifier.fillMaxSize()) {
                    for (h in 0 until 24) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(hourHeight)
                                .semantics {
                                    contentDescription = "Chỗ trống lúc %02d:00, chạm để thêm hoạt động".format(h)
                                }
                                .walletClickable(onClickLabel = "Chỗ trống lúc %02d:00".format(h)) {
                                    onTapEmptyMinute(h * 60)
                                }
                        ) {
                            HorizontalDivider(color = Color(0xFFE9F0EC), thickness = 1.dp)
                            HorizontalDivider(
                                color = Color(0xFFF3F7F5),
                                thickness = 0.75.dp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // 2. Gợi ý khoảng trống nổi bật (ban ngày 06:00-22:30, >= 60 phút)
                dayModel.freeGaps
                    .filter { it.durationMinutes >= 60 && it.endMinute > 6 * 60 && it.startMinute < 22 * 60 }
                    .forEach { gap ->
                        val visStart = max(gap.startMinute, 6 * 60)
                        val visEnd = kotlin.math.min(gap.endMinute, 22 * 60)
                        if (visEnd - visStart >= 60) {
                            val topDp = (visStart / 60f) * hourHeight.value
                            val heightDp = ((visEnd - visStart) / 60f) * hourHeight.value
                            if (heightDp >= 52f) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 4.dp, y = (topDp + 2f).dp)
                                        .width(canvasWidthDp - 8.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .walletClickable(
                                            onClickLabel = "Thêm vào chỗ trống ${TimeRange.formatHm(visStart)} đến ${TimeRange.formatHm(visEnd)}"
                                        ) {
                                            val (sMin, _) = ScheduleGridEngine.prefillGapRange(visStart, dayModel.timedBlocks)
                                            onTapEmptyMinute(sMin)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "+ Trống ${TimeRange.formatHm(visStart)}–${TimeRange.formatHm(visEnd)} · chạm để ghi",
                                        fontSize = 10.5.sp,
                                        color = WalletMuted.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                // 3. Khối hoạt động (Đã thực hiện, Dự kiến, Đang diễn ra, Chưa xác nhận)
                dayModel.timedBlocks.forEach { block ->
                    val isDraggingThis = draggingBlockId == block.recordId
                    val effStart = if (isDraggingThis) dragPreviewStartMin else block.startMinute
                    val effEnd = if (isDraggingThis) dragPreviewEndMin else block.endMinute
                    val spanMinutes = (effEnd - effStart).coerceAtLeast(15)

                    val laneFraction = 1f / block.laneCount.coerceAtLeast(1)
                    val laneWidthDp = (canvasWidthDp - 4.dp) * laneFraction
                    val laneOffsetDp = laneWidthDp * block.laneIndex

                    val topDp = (effStart / 60f) * hourHeight.value
                    val blockHeightDp = max(20f, (spanMinutes / 60f) * hourHeight.value)

                    var accumDragYPx by remember(block.recordId) { mutableFloatStateOf(0f) }

                    ScheduleEventCard(
                        block = block,
                        effectiveStartMin = effStart,
                        effectiveEndMin = effEnd,
                        isDragging = isDraggingThis,
                        draggingLabel = if (isDraggingThis) {
                            "${TimeRange.formatHm(effStart)}–${if (effEnd >= 1440) "24:00" else TimeRange.formatHm(effEnd)}"
                        } else null,
                        blockHeightDp = blockHeightDp.dp,
                        modifier = Modifier
                            .offset(x = laneOffsetDp + 2.dp, y = topDp.dp)
                            .width((laneWidthDp - 4.dp).coerceAtLeast(48.dp))
                            .height(blockHeightDp.dp)
                            .zIndex(if (isDraggingThis) 10f else 1f)
                            .then(
                                if (block.canDragMoveOrResize && block.record != null && !block.crossesMidnightFromPrev && !block.crossesMidnightToNext) {
                                    Modifier.pointerInput(block.recordId, block.startMinute, block.endMinute) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                accumDragYPx = 0f
                                                draggingBlockId = block.recordId
                                                dragPreviewStartMin = block.startMinute
                                                dragPreviewEndMin = block.endMinute
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                accumDragYPx += dragAmount.y
                                                val deltaMin = (accumDragYPx / minuteHeightPx).roundToInt()
                                                val (s, e) = ScheduleGridEngine.snapMoveRange(block.startMinute, block.endMinute, deltaMin)
                                                dragPreviewStartMin = s
                                                dragPreviewEndMin = e
                                            },
                                            onDragEnd = {
                                                val targetStart = dragPreviewStartMin
                                                val targetEnd = dragPreviewEndMin
                                                draggingBlockId = null
                                                if (targetStart != block.startMinute || targetEnd != block.endMinute) {
                                                    onMoveOrResize(
                                                        block.recordId,
                                                        block.dateLocal,
                                                        TimeRange.formatHm(targetStart),
                                                        if (targetEnd >= 1440) "00:00" else TimeRange.formatHm(targetEnd)
                                                    )
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
                        onBottomEdgeDragStart = {
                            accumDragYPx = 0f
                            draggingBlockId = block.recordId
                            dragPreviewStartMin = block.startMinute
                            dragPreviewEndMin = block.endMinute
                        },
                        onBottomEdgeDragDelta = { deltaY ->
                            accumDragYPx += deltaY
                            val deltaEndMin = (accumDragYPx / minuteHeightPx).roundToInt()
                            val (s, e) = ScheduleGridEngine.snapResizeRange(block.startMinute, block.endMinute, deltaEndMin)
                            dragPreviewStartMin = s
                            dragPreviewEndMin = e
                        },
                        onBottomEdgeDragEnd = {
                            val targetStart = dragPreviewStartMin
                            val targetEnd = dragPreviewEndMin
                            draggingBlockId = null
                            if (targetEnd != block.endMinute) {
                                onMoveOrResize(
                                    block.recordId,
                                    block.dateLocal,
                                    TimeRange.formatHm(targetStart),
                                    if (targetEnd >= 1440) "00:00" else TimeRange.formatHm(targetEnd)
                                )
                            }
                        },
                        onBottomEdgeDragCancel = {
                            draggingBlockId = null
                        }
                    )
                }

                // 4. Vạch đỏ giờ hiện tại (chuẩn Google Calendar)
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
private fun ScheduleEventCard(
    block: ScheduleGridBlock,
    effectiveStartMin: Int,
    effectiveEndMin: Int,
    isDragging: Boolean,
    draggingLabel: String?,
    blockHeightDp: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onQuickConfirm: (() -> Unit)? = null,
    onBottomEdgeDragStart: () -> Unit = {},
    onBottomEdgeDragDelta: (Float) -> Unit = {},
    onBottomEdgeDragEnd: () -> Unit = {},
    onBottomEdgeDragCancel: () -> Unit = {}
) {
    val catColor = Color(block.category.colorHex)
    val (bgColor, accentColor, isDashed) = when (block.visualState) {
        ScheduleBlockVisualState.COMPLETED -> Triple(catColor.copy(alpha = 0.14f), catColor, false)
        ScheduleBlockVisualState.PLANNED -> Triple(Color(0xFFEEF4FF), PlannedAccentBlue, true)
        ScheduleBlockVisualState.ACTIVE -> Triple(WalletMintSoft, ActiveAccentCyan, false)
        ScheduleBlockVisualState.UNCONFIRMED -> Triple(Color(0xFFFFF4E0), UnconfirmedAmber, true)
    }

    val borderStrokePx = with(LocalDensity.current) { (if (isDragging) 2.dp else 1.3.dp).toPx() }
    val cornerPx = with(LocalDensity.current) { 10.dp.toPx() }
    val compact = blockHeightDp < 44.dp
    val showQuickAction = blockHeightDp >= 82.dp && block.laneCount == 1
    val confirmCallback = onQuickConfirm
    val canShowResizeGrip = block.canDragMoveOrResize && !block.crossesMidnightToNext && blockHeightDp >= 38.dp

    Box(
        modifier = modifier
            .clipToBounds()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDragging) bgColor.copy(alpha = 0.96f) else bgColor)
            .drawBehind {
                if (isDashed && !isDragging) {
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
                        color = if (isDragging) WalletMintDark else accentColor.copy(alpha = 0.72f),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = borderStrokePx)
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .walletClickable(onClickLabel = "Sửa hoặc xem ${block.title}", onClick = onClick)
        ) {
            // Thanh nhấn trái phân biệt danh mục / trạng thái
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
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
                        size = if (blockHeightDp < 32.dp) 12.dp else 14.dp,
                        tint = accentColor
                    )
                    Text(
                        text = block.title,
                        fontSize = if (compact) 11.sp else 12.sp,
                        lineHeight = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (block.laneCount == 1) {
                        if (blockHeightDp < 32.dp) {
                            Text(
                                text = "${TimeRange.formatHm(effectiveStartMin)}–${if (effectiveEndMin >= 1440) "24:00" else TimeRange.formatHm(effectiveEndMin)}",
                                fontSize = 9.5.sp,
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
                        append(if (effectiveEndMin >= 1440) "24:00" else TimeRange.formatHm(effectiveEndMin))
                        append(" · ")
                        append(TimeRange.formatDuration((effectiveEndMin - effectiveStartMin).coerceAtLeast(1)))
                        if (block.crossesMidnightFromPrev) append(" (từ hôm qua)")
                        if (block.crossesMidnightToNext) append(" (+1 ngày)")
                    }
                    if (block.laneCount > 1) {
                        StateMicroPill(block.visualState)
                        Text(
                            text = block.planComparisonText ?: timeWindow,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (block.planComparisonText != null || isDragging) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDragging) WalletMintDark else WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = block.planComparisonText ?: timeWindow,
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (block.planComparisonText != null || isDragging) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDragging) WalletMintDark else WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showQuickAction && confirmCallback != null) {
                    Box(
                        modifier = Modifier.walletClickable(
                            onClickLabel = "Xác nhận kết quả ${block.title}",
                            onClick = confirmCallback
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

        // Tay nắm cạnh dưới để kéo điều chỉnh thời lượng trực tiếp trên lịch
        if (canShowResizeGrip) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(12.dp)
                    .pointerInput(block.recordId, block.endMinute) {
                        detectVerticalDragGestures(
                            onDragStart = { onBottomEdgeDragStart() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onBottomEdgeDragDelta(dragAmount)
                            },
                            onDragEnd = { onBottomEdgeDragEnd() },
                            onDragCancel = { onBottomEdgeDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor.copy(alpha = 0.5f))
                )
            }
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
    modifier: Modifier = Modifier,
    onSelectDay: (String) -> Unit,
    onTapCell: (DayScheduleModel, Int) -> Unit,
    onTapBlock: (ScheduleGridBlock) -> Unit
) {
    val scrollState = rememberScrollState()
    val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
    LaunchedEffect(days.size, compactColumns) {
        val targetHour = (nowMinute / 60 - 2).coerceIn(0, 16)
        scrollState.scrollTo((targetHour * hourPx).roundToInt())
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thanh đầu cột cho 3 ngày / 7 ngày với tóm tắt Thực tế vs Kế hoạch từng ngày
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletMintTint)
                    .padding(start = 42.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            ) {
                days.forEach { day ->
                    val d = remember(day.dateLocal) { LocalDate.parse(day.dateLocal) }
                    val isToday = day.dateLocal == todayStr
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isToday) WalletMint else Color.Transparent)
                            .walletClickable(onClickLabel = "Mở ngày ${day.dateLocal}") {
                                onSelectDay(day.dateLocal)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = shortWeekdayVi(d.dayOfWeek),
                            fontSize = if (compactColumns) 10.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) Color(0xFFD6EFE3) else WalletMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = d.format(DateTimeFormatter.ofPattern("dd/MM")),
                            fontSize = if (compactColumns) 10.5.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isToday) Color.White else WalletInk,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (day.actualMinutesTotal > 0 || day.plannedMinutesTotal > 0) {
                            Text(
                                text = if (compactColumns) {
                                    TimeRange.formatDuration(day.actualMinutesTotal.coerceAtLeast(day.plannedMinutesTotal))
                                } else {
                                    "TT ${TimeRange.formatDuration(day.actualMinutesTotal)}" +
                                        if (day.plannedMinutesTotal > 0) " · KH ${TimeRange.formatDuration(day.plannedMinutesTotal)}" else ""
                                },
                                fontSize = if (compactColumns) 9.sp else 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isToday) Color.White else WalletMint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = WalletStroke)

            // Lưới giờ dùng chung 00:00 -> 24:00
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Column(modifier = Modifier.width(42.dp)) {
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
                        .height(hourHeight * 24)
                        .padding(end = 4.dp)
                ) {
                    days.forEach { day ->
                        val isDayToday = day.dateLocal == todayStr
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, Color(0xFFE8EFEA))
                        ) {
                            val colW = maxWidth
                            Column(modifier = Modifier.fillMaxSize()) {
                                for (h in 0 until 24) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(hourHeight)
                                            .walletClickable(onClickLabel = "Thêm hoạt động ngày ${day.dateLocal} lúc %02d:00".format(h)) {
                                                onTapCell(day, h * 60)
                                            }
                                    ) {
                                        HorizontalDivider(color = Color(0xFFEDF3F0), thickness = 0.8.dp)
                                    }
                                }
                            }
                            day.timedBlocks.forEach { block ->
                                val topDp = (block.startMinute / 60f) * hourHeight.value
                                val hDp = max(20f, ((block.endMinute - block.startMinute) / 60f) * hourHeight.value)
                                val frac = 1f / block.laneCount.coerceAtLeast(1)
                                val wDp = (colW - 2.dp) * frac
                                val xDp = wDp * block.laneIndex + 1.dp
                                val accentColor = when (block.visualState) {
                                    ScheduleBlockVisualState.PLANNED -> PlannedAccentBlue
                                    ScheduleBlockVisualState.UNCONFIRMED -> UnconfirmedAmber
                                    ScheduleBlockVisualState.ACTIVE -> ActiveAccentCyan
                                    ScheduleBlockVisualState.COMPLETED -> Color(block.category.colorHex)
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(x = xDp, y = topDp.dp)
                                        .width((wDp - 1.dp).coerceAtLeast(16.dp))
                                        .height(hDp.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (block.visualState) {
                                                ScheduleBlockVisualState.PLANNED -> Color(0xFFE9F0FF)
                                                ScheduleBlockVisualState.UNCONFIRMED -> Color(0xFFFFF3DC)
                                                ScheduleBlockVisualState.ACTIVE -> WalletMintSoft
                                                ScheduleBlockVisualState.COMPLETED -> accentColor.copy(alpha = 0.22f)
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = accentColor.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .walletClickable(onClickLabel = "Mở ${block.title}") { onTapBlock(block) }
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (compactColumns) block.category.emoji else block.title,
                                        fontSize = if (compactColumns) 9.5.sp else 10.sp,
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

@Composable
private fun MonthOverviewGrid(
    cells: List<MonthDayCell>,
    selectedDateLocal: String,
    selectedDayModel: DayScheduleModel,
    today: LocalDate,
    modifier: Modifier = Modifier,
    scrollVertically: Boolean = true,
    onSelectDate: (String) -> Unit,
    onOpenDayTimeline: (String) -> Unit,
    onAddOnDate: (String) -> Unit,
    onEditRecord: (LedgerRecord) -> Unit
) {
    val weekHeaders = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    val scrollModifier = if (scrollVertically) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
        modifier = modifier.then(scrollModifier),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
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
                HorizontalDivider(color = Color(0xFFEDF3F0))
                cells.chunked(7).forEach { weekRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        weekRow.forEach { cell ->
                            val isSelected = cell.dateLocal == selectedDateLocal
                            val cellBg = when {
                                isSelected -> WalletMintTint
                                cell.isToday -> Color(0xFFE7F5ED)
                                !cell.inCurrentMonth -> Color(0xFFF9FAF9)
                                else -> Color.White
                            }
                            val borderColor = when {
                                isSelected -> WalletMint
                                cell.isToday -> WalletMintMid
                                else -> Color(0xFFE9F0EC)
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
                                            color = WalletMintDark,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    cell.plannedMinutes > 0 -> {
                                        Text(
                                            text = "KH ${TimeRange.formatDuration(cell.plannedMinutes).replace(" ", "")}",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PlannedAccentBlue,
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

        // Thẻ chi tiết nhanh cho ngày đang chọn trong Tháng
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, WalletStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatScheduleRangeHeading(LocalDate.parse(selectedDateLocal), today, ScheduleViewMode.DAY),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletInk
                        )
                        Text(
                            text = "Thực tế ${TimeRange.formatDuration(selectedDayModel.actualMinutesTotal)} · Dự kiến ${TimeRange.formatDuration(selectedDayModel.plannedMinutesTotal)}",
                            fontSize = 11.5.sp,
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
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Xem Ngày", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = WalletMintDark)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletMint)
                                .walletClickable(onClickLabel = "Ghi mới vào ngày $selectedDateLocal") {
                                    onAddOnDate(selectedDateLocal)
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Ghi mới", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                if (selectedDayModel.timedBlocks.isEmpty() && selectedDayModel.untimedRecords.isEmpty()) {
                    Text(
                        text = "Ngày này còn trống — chạm '+ Ghi mới' hoặc 'Xem Ngày' để lên lịch hay ghi nhận hoạt động.",
                        fontSize = 12.sp,
                        color = WalletMuted
                    )
                } else {
                    selectedDayModel.timedBlocks.take(6).forEach { block ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(WalletBg)
                                .walletClickable(onClickLabel = "Sửa ${block.title}") {
                                    block.record?.let(onEditRecord)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryIcon(block.category, size = 16.dp, tint = Color(block.category.colorHex))
                            Text(
                                text = block.title,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${TimeRange.formatHm(block.startMinute)}–${if (block.endMinute >= 1440) "24:00" else TimeRange.formatHm(block.endMinute)}",
                                fontSize = 11.sp,
                                color = WalletMuted,
                                maxLines = 1,
                                softWrap = false
                            )
                            StateMicroPill(block.visualState)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UntimedRecordsTray(
    records: List<LedgerRecord>,
    onEditRecord: (LedgerRecord) -> Unit,
    onQuickLogDuration: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Chưa gắn giờ · Chỉ thời lượng (${records.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk
                )
                Text(
                    text = "+ Ghi nhanh thời lượng",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletMint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .walletClickable(onClickLabel = "Ghi nhanh chỉ thời lượng", onClick = onQuickLogDuration)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (records.isEmpty()) {
                Text(
                    text = "Chạm ô giờ trống trên lịch để lên giờ, hoặc dùng '+ Ghi nhanh thời lượng' cho các việc không nhớ mốc giờ.",
                    fontSize = 11.5.sp,
                    color = WalletMuted,
                    lineHeight = 16.sp
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    records.forEach { rec ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(WalletMintTint)
                                .border(1.dp, WalletStroke, RoundedCornerShape(12.dp))
                                .walletClickable(onClickLabel = "Sửa ${rec.title}") { onEditRecord(rec) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CategoryIcon(rec.category, size = 14.dp, tint = WalletMintDark)
                            Text(
                                text = "${rec.title} · ${TimeRange.formatDuration(rec.durationMinutes)} (${rec.date.substring(5)})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WalletInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
        ScheduleBlockVisualState.COMPLETED -> WalletMintSoft to WalletMintDark
        ScheduleBlockVisualState.PLANNED -> Color(0xFFE1EAFF) to PlannedAccentBlue
        ScheduleBlockVisualState.ACTIVE -> Color(0xFFD5F5EA) to ActiveAccentCyan
        ScheduleBlockVisualState.UNCONFIRMED -> Color(0xFFFFECCC) to WalletWarningText
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
            today -> "Hôm nay · ${shortWeekdayVi(selectedDate.dayOfWeek)}, ${selectedDate.format(ddMm)}"
            today.minusDays(1) -> "Hôm qua · ${shortWeekdayVi(selectedDate.dayOfWeek)}, ${selectedDate.format(ddMm)}"
            today.plusDays(1) -> "Ngày mai · ${shortWeekdayVi(selectedDate.dayOfWeek)}, ${selectedDate.format(ddMm)}"
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
            "Tuần ${mon.format(ddMm)} – ${sun.format(ddMm)}"
        }
        ScheduleViewMode.MONTH -> {
            selectedDate.format(DateTimeFormatter.ofPattern("'Tháng' MM/yyyy", Locale("vi", "VN")))
        }
    }
}
