package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

enum class ScheduleViewMode(val label: String) {
    DAY("Ngày"),
    THREE_DAYS("3 ngày"),
    WEEK("Tuần"),
    MONTH("Tháng")
}

/**
 * 4 trạng thái hiển thị trực quan phân biệt rõ Kế hoạch vs Thực tế trên lịch:
 * - COMPLETED: Đã thực hiện (MEASURED / REPORTED_TIMED)
 * - PLANNED: Dự kiến (lên lịch tương lai hoặc chưa tới hạn)
 * - ACTIVE: Đang diễn ra (timer đang chạy hoặc hoạt động ACTIVE)
 * - UNCONFIRMED: Chưa xác nhận (đã qua giờ dự kiến nhưng chưa xác nhận thực tế / cần đối soát)
 */
enum class ScheduleBlockVisualState(val labelVi: String) {
    COMPLETED("Đã thực hiện"),
    PLANNED("Dự kiến"),
    ACTIVE("Đang diễn ra"),
    UNCONFIRMED("Chưa xác nhận")
}

data class ScheduleGridBlock(
    val recordId: String,
    val record: LedgerRecord?,
    val isActiveTimer: Boolean,
    val dateLocal: String,
    val startMinute: Int,
    val endMinute: Int,
    val totalDurationMinutes: Int,
    val title: String,
    val category: TimeCategory,
    val visualState: ScheduleBlockVisualState,
    val laneIndex: Int = 0,
    val laneCount: Int = 1,
    val planComparisonText: String? = null,
    val crossesMidnightFromPrev: Boolean = false,
    val crossesMidnightToNext: Boolean = false,
    val tagId: String? = null,
    val canDragMoveOrResize: Boolean = false,
    val displayStartHm: String = TimeRange.formatHm(startMinute.coerceIn(0, 1439)),
    val displayEndHm: String = ScheduleGridEngine.formatHmEnd(endMinute)
)

data class ScheduleFreeGap(
    val dateLocal: String,
    val startMinute: Int,
    val endMinute: Int
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
}

/** Khung giờ trống người dùng chọn trên lưới Lịch trình. */
data class SelectedTimeSlot(
    val dateLocal: String,
    val startMinute: Int,
    val endMinute: Int
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
}

/** Khoảng giờ sau khi gộp các ô trống liền kề cùng ngày (ô rời rạc hoặc khác ngày luôn tách riêng). */
data class SelectedSlotGroup(
    val dateLocal: String,
    val startMinute: Int,
    val endMinute: Int
) {
    val startHm: String get() = TimeRange.formatHm(startMinute.coerceIn(0, 1439))
    val endHm: String get() = if (endMinute >= 1440) "24:00" else TimeRange.formatHm(endMinute.coerceIn(1, 1439))
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(1)
}

/** Kết quả phát hiện xung đột khi dời / kéo cạnh / đặt hoạt động vào khoảng giờ mới trên lịch. */
data class ScheduleConflictCheck(
    val hardConflicts: List<ScheduleGridBlock>,
    val planConflicts: List<ScheduleGridBlock>,
    val actualOverlapNotes: List<ScheduleGridBlock> = emptyList()
) {
    val hasBlockingOrWarningConflict: Boolean
        get() = hardConflicts.isNotEmpty() || planConflicts.isNotEmpty()

    val primaryConflict: ScheduleGridBlock?
        get() = hardConflicts.firstOrNull() ?: planConflicts.firstOrNull()

    fun summaryVi(): String {
        val hit = primaryConflict ?: return ""
        val endLabel = if (hit.endMinute >= 1440) "24:00" else TimeRange.formatHm(hit.endMinute)
        val kind = if (hardConflicts.isNotEmpty()) "Trùng hoạt động thực tế" else "Trùng kế hoạch dự kiến"
        return "$kind với \"${hit.title}\" (${TimeRange.formatHm(hit.startMinute)}–$endLabel)"
    }
}

data class DayScheduleModel(
    val dateLocal: String,
    val timedBlocks: List<ScheduleGridBlock>,
    val freeGaps: List<ScheduleFreeGap>,
    val untimedRecords: List<LedgerRecord>,
    val actualMinutesTotal: Int,
    val plannedMinutesTotal: Int,
    val unconfirmedCount: Int,
    val hasActiveTimer: Boolean
)

data class MonthDayCell(
    val dateLocal: String,
    val dayOfMonth: Int,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val actualMinutes: Int,
    val plannedMinutes: Int,
    val unconfirmedCount: Int,
    val untimedCount: Int,
    val activityCount: Int,
    val isFreeDay: Boolean,
    val topCategories: List<TimeCategory>
)

/**
 * Bộ tính bố cục Lịch trình & Ghi nhận kiểu Google Calendar:
 * - Xử lý chính xác cắt lát qua nửa đêm (UC-15)
 * - Bố trí song song cho hoạt động trùng giờ hoặc Kế hoạch vs Thực tế (không tự ẩn / không gộp sai)
 * - Tách riêng bản ghi chỉ có thời lượng (`REPORTED_UNTIMED`) ra khỏi lưới 24h
 * - Tính khoảng trống (Free Gaps) và phép snap kéo/giãn 15 phút tất định
 */
object ScheduleGridEngine {

    fun resolveVisualState(
        record: LedgerRecord,
        nowEpochMs: Long,
        todayDateLocal: String
    ): ScheduleBlockVisualState {
        if (record.isNeedsReconciliation) return ScheduleBlockVisualState.UNCONFIRMED
        val confState = record.resolveConfirmationStateAt(nowEpochMs, todayDateLocal)
        if (confState == ConfirmationState.PENDING_CONFIRMATION) {
            return ScheduleBlockVisualState.UNCONFIRMED
        }
        val layer = record.resolveLayerAt(nowEpochMs, todayDateLocal)
        return when (layer) {
            TimeLayer.ACTIVE -> ScheduleBlockVisualState.ACTIVE
            TimeLayer.PLANNED -> ScheduleBlockVisualState.PLANNED
            TimeLayer.MEASURED, TimeLayer.REPORTED_TIMED, TimeLayer.REPORTED_UNTIMED ->
                ScheduleBlockVisualState.COMPLETED
        }
    }

    fun buildDayScheduleModel(
        date: LocalDate,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        categoryFilter: String? = null,
        searchQuery: String = ""
    ): DayScheduleModel {
        val dateStr = date.toString()
        val todayStr = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate().toString()
        val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val normalizedQuery = searchQuery.trim().lowercase()

        fun matchesFilter(title: String, category: TimeCategory): Boolean {
            if (!categoryFilter.isNullOrBlank() && category.key != categoryFilter) return false
            if (normalizedQuery.isNotEmpty() &&
                !title.lowercase().contains(normalizedQuery) &&
                !category.displayName.lowercase().contains(normalizedQuery)
            ) {
                return false
            }
            return true
        }

        val rawBlocks = mutableListOf<ScheduleGridBlock>()
        val untimed = mutableListOf<LedgerRecord>()
        var actualSecondsSum = 0L
        var plannedSecondsSum = 0L
        var unconfirmedCount = 0
        var hasActive = false

        for (record in records) {
            if (record.isCancelled) continue
            if (!matchesFilter(record.title, record.category)) continue

            val visualState = resolveVisualState(record, nowEpochMs, todayStr)
            val start = record.startedAt
            val end = record.endedAt

            if (start != null && end != null && end > start) {
                if (start < dayEndMs && end > dayStartMs) {
                    val clipStart = max(start, dayStartMs)
                    val clipEnd = min(end, dayEndMs)
                    // Dùng cùng quy tắc cắt phút lịch (HH:mm) với Dòng thời gian để 2 màn không bao giờ lệch 1–2 phút.
                    val startMin = ((clipStart - dayStartMs) / 60_000L).toInt().coerceIn(0, 1439)
                    val flooredEndMin = ((clipEnd - dayStartMs) / 60_000L).toInt()
                    val endMin = max(startMin + 1, flooredEndMin).coerceIn(startMin + 1, 1440)
                    val crossPrev = start < dayStartMs
                    val crossNext = end > dayEndMs

                    val recordZone = runCatching { ZoneId.of(record.timezone) }.getOrDefault(zone)
                    val hmFmt = DateTimeFormatter.ofPattern("HH:mm")
                    val exactStartHm = if (crossPrev) "00:00" else Instant.ofEpochMilli(start).atZone(recordZone).format(hmFmt)
                    val exactEndHm = when {
                        crossNext || clipEnd >= dayEndMs -> "24:00"
                        else -> Instant.ofEpochMilli(end).atZone(recordZone).format(hmFmt)
                    }

                    val comparison = buildPlanComparisonText(record, visualState)
                    val totalMins = ((end - start + 30_000L) / 60_000L).toInt().coerceAtLeast(1)

                    rawBlocks += ScheduleGridBlock(
                        recordId = record.id,
                        record = record,
                        isActiveTimer = false,
                        dateLocal = dateStr,
                        startMinute = startMin,
                        endMinute = endMin,
                        totalDurationMinutes = totalMins,
                        title = record.title,
                        category = record.category,
                        visualState = visualState,
                        planComparisonText = comparison,
                        crossesMidnightFromPrev = crossPrev,
                        crossesMidnightToNext = crossNext,
                        tagId = record.tagId,
                        canDragMoveOrResize = true,
                        displayStartHm = exactStartHm,
                        displayEndHm = exactEndHm
                    )

                    val sliceSecs = ((clipEnd - clipStart) / 1000L).coerceAtLeast(0L)
                    when (visualState) {
                        ScheduleBlockVisualState.COMPLETED -> {
                            val effectiveEnd = min(clipEnd, record.confirmedCutoffEpochMsAt(nowEpochMs))
                            actualSecondsSum += max(0L, (effectiveEnd - clipStart) / 1000L)
                        }
                        ScheduleBlockVisualState.ACTIVE -> {
                            hasActive = true
                            val effectiveEnd = min(clipEnd, record.confirmedCutoffEpochMsAt(nowEpochMs))
                            actualSecondsSum += max(0L, (effectiveEnd - clipStart) / 1000L)
                        }
                        ScheduleBlockVisualState.PLANNED -> {
                            plannedSecondsSum += sliceSecs
                        }
                        ScheduleBlockVisualState.UNCONFIRMED -> {
                            unconfirmedCount++
                            plannedSecondsSum += sliceSecs
                        }
                    }
                }
            } else if (record.date == dateStr) {
                // Tuyệt đối KHÔNG gán mốc giờ giả cho bản ghi chỉ có thời lượng.
                untimed += record
                if (visualState == ScheduleBlockVisualState.PLANNED) {
                    plannedSecondsSum += record.durationSeconds
                } else if (visualState == ScheduleBlockVisualState.UNCONFIRMED) {
                    unconfirmedCount++
                } else {
                    actualSecondsSum += record.durationSeconds
                }
            }
        }

        if (activeTimer != null) {
            val tStart = activeTimer.startedAtEpochMs
            val tEnd = max(nowEpochMs, tStart + 60_000L)
            val tCategory = TimeCategory.fromKeyOrDefault(activeTimer.categoryKey)
            val tTitle = activeTimer.title.ifBlank { tCategory.displayName }
            if (matchesFilter(tTitle, tCategory) && tStart < dayEndMs && tEnd > dayStartMs) {
                val clipStart = max(tStart, dayStartMs)
                val clipEnd = min(tEnd, dayEndMs)
                val startMin = ((clipStart - dayStartMs) / 60_000L).toInt().coerceIn(0, 1439)
                val endMin = max(startMin + 1, ((clipEnd - dayStartMs) / 60_000L).toInt()).coerceIn(startMin + 1, 1440)
                val totalMins = ((tEnd - tStart) / 60_000L).toInt().coerceAtLeast(1)
                hasActive = true
                actualSecondsSum += max(0L, (min(clipEnd, nowEpochMs) - clipStart) / 1000L)

                rawBlocks += ScheduleGridBlock(
                    recordId = ActivityBreakEngine.ACTIVE_TIMER_CONFLICT_ID,
                    record = null,
                    isActiveTimer = true,
                    dateLocal = dateStr,
                    startMinute = startMin,
                    endMinute = endMin,
                    totalDurationMinutes = totalMins,
                    title = tTitle,
                    category = tCategory,
                    visualState = ScheduleBlockVisualState.ACTIVE,
                    crossesMidnightFromPrev = tStart < dayStartMs,
                    crossesMidnightToNext = tEnd > dayEndMs,
                    tagId = activeTimer.tagId,
                    canDragMoveOrResize = false
                )
            }
        }

        val layoutBlocks = assignParallelLanes(rawBlocks, minVisualSpanMinutes = 14)
        val gaps = computeFreeGaps(dateStr, layoutBlocks)

        return DayScheduleModel(
            dateLocal = dateStr,
            timedBlocks = layoutBlocks,
            freeGaps = gaps,
            untimedRecords = untimed.sortedBy { it.createdAt },
            actualMinutesTotal = ((actualSecondsSum + 30L) / 60L).toInt(),
            plannedMinutesTotal = ((plannedSecondsSum + 30L) / 60L).toInt(),
            unconfirmedCount = unconfirmedCount,
            hasActiveTimer = hasActive
        )
    }

    fun buildMultiDayScheduleModels(
        startDate: LocalDate,
        dayCount: Int,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        categoryFilter: String? = null,
        searchQuery: String = ""
    ): List<DayScheduleModel> = (0 until dayCount.coerceAtLeast(1)).map { offset ->
        buildDayScheduleModel(
            date = startDate.plusDays(offset.toLong()),
            records = records,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            zone = zone,
            categoryFilter = categoryFilter,
            searchQuery = searchQuery
        )
    }

    fun buildMonthGrid(
        yearMonth: YearMonth,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        categoryFilter: String? = null,
        searchQuery: String = ""
    ): List<MonthDayCell> {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val firstOfMonth = yearMonth.atDay(1)
        val leadingDays = (firstOfMonth.dayOfWeek.value - 1).coerceAtLeast(0)
        val gridStart = firstOfMonth.minusDays(leadingDays.toLong())

        return (0 until 42).map { idx ->
            val d = gridStart.plusDays(idx.toLong())
            val dayModel = buildDayScheduleModel(
                date = d,
                records = records,
                activeTimer = activeTimer,
                nowEpochMs = nowEpochMs,
                zone = zone,
                categoryFilter = categoryFilter,
                searchQuery = searchQuery
            )
            val cats = (dayModel.timedBlocks.map { it.category } + dayModel.untimedRecords.map { it.category })
                .distinct()
                .take(3)
            val count = dayModel.timedBlocks.size + dayModel.untimedRecords.size
            MonthDayCell(
                dateLocal = d.toString(),
                dayOfMonth = d.dayOfMonth,
                inCurrentMonth = d.month == yearMonth.month && d.year == yearMonth.year,
                isToday = d == today,
                actualMinutes = dayModel.actualMinutesTotal,
                plannedMinutes = dayModel.plannedMinutesTotal,
                unconfirmedCount = dayModel.unconfirmedCount,
                untimedCount = dayModel.untimedRecords.size,
                activityCount = count,
                isFreeDay = count == 0 && dayModel.actualMinutesTotal == 0 && dayModel.plannedMinutesTotal == 0,
                topCategories = cats
            )
        }
    }

    /**
     * Thuật toán xếp cột song song theo cluster chồng lấn:
     * Hai khối trùng giờ (ví dụ Kế hoạch 14:00–15:00 và Thực tế 14:00–15:12) được đặt cạnh nhau
     * trong cùng nhóm để người dùng nhìn thấy cả hai cùng lúc.
     */
    internal fun assignParallelLanes(
        raw: List<ScheduleGridBlock>,
        minVisualSpanMinutes: Int = 0
    ): List<ScheduleGridBlock> {
        if (raw.isEmpty()) return emptyList()
        val sorted = raw.sortedWith(
            compareBy<ScheduleGridBlock> { it.startMinute }
                .thenBy { stateOrder(it.visualState) }
                .thenByDescending { it.endMinute }
        )
        fun visualEnd(b: ScheduleGridBlock): Int =
            if (minVisualSpanMinutes > 0) max(b.endMinute, (b.startMinute + minVisualSpanMinutes).coerceAtMost(1440))
            else b.endMinute

        val clusters = mutableListOf<MutableList<ScheduleGridBlock>>()
        var currentCluster = mutableListOf<ScheduleGridBlock>()
        var clusterMaxEnd = -1
        for (block in sorted) {
            val vEnd = visualEnd(block)
            if (currentCluster.isEmpty() || block.startMinute < clusterMaxEnd) {
                currentCluster += block
                clusterMaxEnd = max(clusterMaxEnd, vEnd)
            } else {
                clusters += currentCluster
                currentCluster = mutableListOf(block)
                clusterMaxEnd = vEnd
            }
        }
        if (currentCluster.isNotEmpty()) clusters += currentCluster

        val result = ArrayList<ScheduleGridBlock>(sorted.size)
        for (cluster in clusters) {
            val laneEndMinutes = mutableListOf<Int>()
            val assignedIndices = IntArray(cluster.size)
            for (i in cluster.indices) {
                val b = cluster[i]
                val vEnd = visualEnd(b)
                var chosen = -1
                for (laneIdx in laneEndMinutes.indices) {
                    if (laneEndMinutes[laneIdx] <= b.startMinute) {
                        chosen = laneIdx
                        laneEndMinutes[laneIdx] = vEnd
                        break
                    }
                }
                if (chosen < 0) {
                    chosen = laneEndMinutes.size
                    laneEndMinutes += vEnd
                }
                assignedIndices[i] = chosen
            }
            val totalLanes = laneEndMinutes.size.coerceAtLeast(1)
            for (i in cluster.indices) {
                result += cluster[i].copy(laneIndex = assignedIndices[i], laneCount = totalLanes)
            }
        }
        return result
    }

    private fun computeFreeGaps(
        dateLocal: String,
        blocks: List<ScheduleGridBlock>,
        minGapMinutes: Int = 15
    ): List<ScheduleFreeGap> {
        if (blocks.isEmpty()) {
            return listOf(ScheduleFreeGap(dateLocal, 0, 1440))
        }
        val merged = mutableListOf<IntRange>()
        for (b in blocks.sortedBy { it.startMinute }) {
            if (merged.isEmpty() || b.startMinute > merged.last().last) {
                merged += b.startMinute..b.endMinute
            } else {
                val prev = merged.removeAt(merged.lastIndex)
                merged += prev.first..max(prev.last, b.endMinute)
            }
        }
        val gaps = mutableListOf<ScheduleFreeGap>()
        var cursor = 0
        for (occ in merged) {
            if (occ.first - cursor >= minGapMinutes) {
                gaps += ScheduleFreeGap(dateLocal, cursor, occ.first)
            }
            cursor = max(cursor, occ.last)
        }
        if (1440 - cursor >= minGapMinutes) {
            gaps += ScheduleFreeGap(dateLocal, cursor, 1440)
        }
        return gaps
    }

    fun prefillGapRange(
        tappedMinute: Int,
        occupiedBlocks: List<ScheduleGridBlock>,
        defaultDurationMin: Int = 60
    ): Pair<Int, Int> {
        val snappedStart = ((tappedMinute.coerceIn(0, 1425)) / 15) * 15
        val gaps = computeFreeGaps("", occupiedBlocks, minGapMinutes = 15)
        val containing = gaps.firstOrNull { tappedMinute in it.startMinute..it.endMinute }
        if (containing != null) {
            val s = snappedStart.coerceIn(containing.startMinute, (containing.endMinute - 15).coerceAtLeast(containing.startMinute))
            val e = min(containing.endMinute, s + defaultDurationMin).coerceAtLeast(min(containing.endMinute, s + 15))
            if (e > s) return s to e
        }
        val fallbackEnd = min(1440, snappedStart + defaultDurationMin)
        val fallbackStart = min(snappedStart, 1425)
        return fallbackStart to max(fallbackStart + 15, fallbackEnd).coerceAtMost(1440)
    }

    fun snapMoveRange(
        origStartMin: Int,
        origEndMin: Int,
        deltaMinutes: Int,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val duration = (origEndMin - origStartMin).coerceIn(snapMinutes, 1440)
        val half = snapMinutes / 2
        val snappedDelta = if (deltaMinutes >= 0) {
            ((deltaMinutes + half) / snapMinutes) * snapMinutes
        } else {
            ((deltaMinutes - half) / snapMinutes) * snapMinutes
        }
        val newStart = (origStartMin + snappedDelta).coerceIn(0, (1440 - duration).coerceAtLeast(0))
        return newStart to (newStart + duration).coerceAtMost(1440)
    }

    fun snapResizeRange(
        origStartMin: Int,
        origEndMin: Int,
        deltaEndMinutes: Int,
        minMinutes: Int = 15,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val safeStart = origStartMin.coerceIn(0, 1440 - minMinutes)
        val rawEnd = origEndMin + deltaEndMinutes
        val snappedEnd = ((rawEnd + snapMinutes / 2) / snapMinutes) * snapMinutes
        val clampedEnd = snappedEnd.coerceIn(safeStart + minMinutes, 1440)
        return safeStart to clampedEnd
    }

    /** Kéo mép trên của khối hoạt động để cập nhật giờ bắt đầu (giữ nguyên giờ kết thúc). */
    fun snapResizeStartRange(
        origStartMin: Int,
        origEndMin: Int,
        deltaStartMinutes: Int,
        minMinutes: Int = 15,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val safeEnd = origEndMin.coerceIn(minMinutes, 1440)
        val rawStart = origStartMin + deltaStartMinutes
        val half = snapMinutes / 2
        val snappedStart = if (rawStart >= 0) {
            ((rawStart + half) / snapMinutes) * snapMinutes
        } else {
            0
        }
        val clampedStart = snappedStart.coerceIn(0, safeEnd - minMinutes)
        return clampedStart to safeEnd
    }

    /** Kéo liên tục trên khoảng trống để chọn một dải giờ nối liền với bước snap 15 phút. */
    fun snapEmptyDragRange(
        anchorMinute: Int,
        currentMinute: Int,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val rawMin = min(anchorMinute, currentMinute).coerceIn(0, 1440 - snapMinutes)
        val rawMax = max(anchorMinute, currentMinute).coerceIn(rawMin + snapMinutes, 1440)
        val snappedStart = (rawMin / snapMinutes) * snapMinutes
        val snappedEnd = (((rawMax + snapMinutes - 1) / snapMinutes) * snapMinutes).coerceIn(snappedStart + snapMinutes, 1440)
        return snappedStart to snappedEnd
    }

    private fun containingFreeGapForRange(
        startMinute: Int,
        endMinute: Int,
        occupiedBlocks: List<ScheduleGridBlock>
    ): ScheduleFreeGap? {
        if (occupiedBlocks.isEmpty()) return ScheduleFreeGap("", 0, 1440)
        val gaps = computeFreeGaps("", occupiedBlocks, minGapMinutes = 1)
        val mid = ((startMinute + endMinute) / 2).coerceIn(0, 1439)
        return gaps.firstOrNull { startMinute >= it.startMinute && endMinute <= it.endMinute }
            ?: gaps.firstOrNull { mid in it.startMinute until it.endMinute }
            ?: gaps.firstOrNull { startMinute in it.startMinute..it.endMinute }
    }

    /** Kéo chọn trên khoảng trống nhưng luôn chặn trong ranh giới khoảng trống hiện hữu (không trùm lên phiên đã ghi). */
    fun snapEmptyDragRangeInGaps(
        anchorMinute: Int,
        currentMinute: Int,
        occupiedBlocks: List<ScheduleGridBlock>,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val anchor = anchorMinute.coerceIn(0, 1439)
        val gaps = computeFreeGaps("", occupiedBlocks, minGapMinutes = 1)
        val gap = gaps.firstOrNull { anchor in it.startMinute until it.endMinute }
            ?: gaps.firstOrNull { anchor in it.startMinute..it.endMinute }
            ?: if (occupiedBlocks.isEmpty()) ScheduleFreeGap("", 0, 1440)
            else gaps.minByOrNull { g ->
                when {
                    anchor < g.startMinute -> g.startMinute - anchor
                    anchor > g.endMinute -> anchor - g.endMinute
                    else -> 0
                }
            }
            ?: return snapEmptyDragRange(anchorMinute, currentMinute, snapMinutes)
        val minBound = gap.startMinute
        val maxBound = gap.endMinute
        if (maxBound - minBound < snapMinutes) return minBound to maxBound
        val clampedCur = currentMinute.coerceIn(minBound, maxBound)
        val rawMin = min(anchor, clampedCur).coerceIn(minBound, maxBound - snapMinutes)
        val rawMax = max(anchor, clampedCur).coerceIn(rawMin + snapMinutes, maxBound)
        val snappedStart = ((rawMin / snapMinutes) * snapMinutes).coerceAtLeast(minBound)
        val rawSnappedEnd = ((rawMax + snapMinutes - 1) / snapMinutes) * snapMinutes
        val snappedEnd = min(rawSnappedEnd, maxBound).coerceIn((snappedStart + 1).coerceAtMost(maxBound), maxBound)
        return snappedStart to snappedEnd
    }

    /** Mặc định "Dự kiến" khi tạo từ ô trống còn ở tương lai; ô trống đã qua trong hôm nay giữ "Có giờ" thực tế. */
    fun shouldDefaultEmptySlotAsPlanned(
        dateLocal: String,
        startMinute: Int,
        todayLocal: String,
        nowMinute: Int
    ): Boolean = dateLocal > todayLocal || (dateLocal == todayLocal && startMinute >= nowMinute)

    /** Kéo mép TRÊN của ô trống đã chọn, chặn tại ranh giới khối đã có ngay phía trên để không đè lên dữ liệu thực tế. */
    fun snapEmptyResizeStartInGap(
        origStartMin: Int,
        origEndMin: Int,
        deltaStartMinutes: Int,
        occupiedBlocks: List<ScheduleGridBlock>,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val gap = containingFreeGapForRange(origStartMin, origEndMin, occupiedBlocks)
        val minBound = gap?.startMinute ?: 0
        val minSpan = min(snapMinutes, (origEndMin - origStartMin).coerceAtLeast(1))
        val raw = origStartMin + deltaStartMinutes
        val half = snapMinutes / 2
        val snapped = if (raw <= minBound) minBound else ((raw + half) / snapMinutes) * snapMinutes
        val maxAllowedStart = (origEndMin - minSpan).coerceAtLeast(minBound)
        return snapped.coerceIn(minBound, maxAllowedStart) to origEndMin
    }

    /** Kéo mép DƯỚI của ô trống đã chọn, chặn tại ranh giới khối đã có ngay phía dưới để không trùm qua phiên đã đo. */
    fun snapEmptyResizeEndInGap(
        origStartMin: Int,
        origEndMin: Int,
        deltaEndMinutes: Int,
        occupiedBlocks: List<ScheduleGridBlock>,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val gap = containingFreeGapForRange(origStartMin, origEndMin, occupiedBlocks)
        val maxBound = gap?.endMinute ?: 1440
        val minSpan = min(snapMinutes, (origEndMin - origStartMin).coerceAtLeast(1))
        val raw = origEndMin + deltaEndMinutes
        val snapped = if (raw >= maxBound) maxBound else ((raw + snapMinutes / 2) / snapMinutes) * snapMinutes
        val minAllowedEnd = (origStartMin + minSpan).coerceAtMost(maxBound)
        return origStartMin to snapped.coerceIn(minAllowedEnd, maxBound)
    }

    /** Định vị một hoạt động chưa gắn giờ vào khung giờ đã chọn nhưng giữ nguyên thời lượng (nằm gọn trong ngày). */
    fun buildUntimedPlacementWindow(
        tappedMinute: Int,
        durationMinutes: Int,
        snapMinutes: Int = 15
    ): Pair<Int, Int> {
        val dur = durationMinutes.coerceIn(snapMinutes, 1440)
        val snappedStart = ((tappedMinute.coerceIn(0, 1440 - dur)) / snapMinutes) * snapMinutes
        val clampedStart = snappedStart.coerceIn(0, (1440 - dur).coerceAtLeast(0))
        return clampedStart to (clampedStart + dur).coerceAtMost(1440)
    }

    /** Kiểm tra xung đột trên lưới ngày trước khi lưu thao tác dời/giãn/đặt lịch. */
    fun findConflictingBlocks(
        candidateStartMin: Int,
        candidateEndMin: Int,
        excludeRecordId: String?,
        isPlannedCandidate: Boolean,
        dayBlocks: List<ScheduleGridBlock>
    ): ScheduleConflictCheck {
        val overlapping = dayBlocks.filter { b ->
            b.recordId != excludeRecordId &&
                candidateStartMin < b.endMinute &&
                candidateEndMin > b.startMinute
        }
        return if (isPlannedCandidate) {
            val planHits = overlapping.filter { it.visualState == ScheduleBlockVisualState.PLANNED || it.visualState == ScheduleBlockVisualState.UNCONFIRMED }
            val actualHits = overlapping.filter { it.visualState == ScheduleBlockVisualState.COMPLETED || it.visualState == ScheduleBlockVisualState.ACTIVE }
            ScheduleConflictCheck(hardConflicts = emptyList(), planConflicts = planHits, actualOverlapNotes = actualHits)
        } else {
            val actualHits = overlapping.filter { it.visualState == ScheduleBlockVisualState.COMPLETED || it.visualState == ScheduleBlockVisualState.ACTIVE || it.visualState == ScheduleBlockVisualState.UNCONFIRMED }
            ScheduleConflictCheck(hardConflicts = actualHits, planConflicts = emptyList())
        }
    }

    fun formatHmEnd(endMinute: Int): String =
        if (endMinute >= 1440) "24:00" else TimeRange.formatHm(endMinute.coerceIn(1, 1439))

    fun formatHmWindow(startMinute: Int, endMinute: Int): String {
        val dur = (endMinute - startMinute).coerceAtLeast(1)
        return "${TimeRange.formatHm(startMinute.coerceIn(0, 1439))}–${formatHmEnd(endMinute)} · ${TimeRange.formatDuration(dur)}"
    }

    /**
     * Tìm phần giờ trống lớn nhất (>= 15 phút) bên trong ô 1 giờ `[hour*60, (hour+1)*60]`.
     * Trả về null nếu ô giờ đó đã bị phủ gần kín.
     */
    fun computeFreeSlotInHour(
        dateLocal: String,
        hour: Int,
        occupiedBlocks: List<ScheduleGridBlock>,
        minSlotMinutes: Int = 15
    ): SelectedTimeSlot? {
        val cellStart = (hour.coerceIn(0, 23)) * 60
        val cellEnd = cellStart + 60
        val gaps = computeFreeGaps(dateLocal, occupiedBlocks, minGapMinutes = minSlotMinutes)
        val best = gaps.mapNotNull { gap ->
            val s = max(cellStart, gap.startMinute)
            val e = min(cellEnd, gap.endMinute)
            if (e - s >= minSlotMinutes) SelectedTimeSlot(dateLocal, s, e) else null
        }.maxByOrNull { it.durationMinutes }
        return best
    }

    fun toggleSelectedSlot(
        current: List<SelectedTimeSlot>,
        candidate: SelectedTimeSlot
    ): List<SelectedTimeSlot> {
        val exists = current.any {
            it.dateLocal == candidate.dateLocal &&
                it.startMinute == candidate.startMinute &&
                it.endMinute == candidate.endMinute
        }
        return if (exists) {
            current.filterNot {
                it.dateLocal == candidate.dateLocal &&
                    it.startMinute == candidate.startMinute &&
                    it.endMinute == candidate.endMinute
            }
        } else {
            current + candidate
        }
    }

    /**
     * Gộp các khối giờ trống liền kề/giao nhau TRONG CÙNG MỘT NGÀY thành 1 khoảng;
     * các khối rời rạc hoặc khác ngày giữ thành nhiều [SelectedSlotGroup] độc lập.
     */
    fun mergeSelectedEmptySlots(slots: List<SelectedTimeSlot>): List<SelectedSlotGroup> {
        if (slots.isEmpty()) return emptyList()
        val valid = slots.filter { it.endMinute > it.startMinute && it.startMinute in 0 until 1440 && it.endMinute <= 1440 }
        if (valid.isEmpty()) return emptyList()
        val groupedByDate = valid.groupBy { it.dateLocal }.toSortedMap()
        val result = mutableListOf<SelectedSlotGroup>()
        for ((date, daySlots) in groupedByDate) {
            val sorted = daySlots.sortedBy { it.startMinute }
            var curStart = sorted.first().startMinute
            var curEnd = sorted.first().endMinute
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.startMinute <= curEnd) {
                    curEnd = max(curEnd, next.endMinute)
                } else {
                    result += SelectedSlotGroup(date, curStart, curEnd)
                    curStart = next.startMinute
                    curEnd = next.endMinute
                }
            }
            result += SelectedSlotGroup(date, curStart, curEnd)
        }
        return result
    }

    fun summarizeSelectedSlotGroups(groups: List<SelectedSlotGroup>): String {
        if (groups.isEmpty()) return ""
        val totalMinutes = groups.sumOf { it.durationMinutes }
        val preview = groups.take(2).joinToString(", ") { "${it.startHm}–${if (it.endMinute >= 1440) "24:00" else TimeRange.formatHm(it.endMinute)}" }
        val extra = if (groups.size > 2) " +${groups.size - 2}" else ""
        val kindLabel = if (groups.size == 1) "1 sự kiện" else "${groups.size} khoảng rời"
        return "$kindLabel · $preview$extra (${TimeRange.formatDuration(totalMinutes)})"
    }

    private fun stateOrder(state: ScheduleBlockVisualState): Int = when (state) {
        ScheduleBlockVisualState.PLANNED -> 0
        ScheduleBlockVisualState.UNCONFIRMED -> 1
        ScheduleBlockVisualState.ACTIVE -> 2
        ScheduleBlockVisualState.COMPLETED -> 3
    }

    private fun buildPlanComparisonText(
        record: LedgerRecord,
        visualState: ScheduleBlockVisualState
    ): String? {
        if (visualState != ScheduleBlockVisualState.COMPLETED && visualState != ScheduleBlockVisualState.ACTIVE) return null
        val planSecs = record.plannedDurationSeconds ?: return null
        val planMins = ((planSecs + 30L) / 60L).toInt().coerceAtLeast(1)
        val actualMins = record.durationMinutes
        val diff = actualMins - planMins
        if (kotlin.math.abs(diff) < 1) return null
        val sign = if (diff > 0) "+" else ""
        return "KH ${TimeRange.formatDuration(planMins)} → TT ${TimeRange.formatDuration(actualMins)} ($sign${diff}p)"
    }
}
