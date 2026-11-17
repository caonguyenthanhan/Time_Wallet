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
    val canDragMoveOrResize: Boolean = false
)

data class ScheduleFreeGap(
    val dateLocal: String,
    val startMinute: Int,
    val endMinute: Int
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
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
                    val startMin = ((clipStart - dayStartMs) / 60_000L).toInt().coerceIn(0, 1439)
                    val rawEndMin = ((clipEnd - dayStartMs + 30_000L) / 60_000L).toInt()
                    val endMin = max(startMin + 1, rawEndMin).coerceIn(startMin + 1, 1440)
                    val crossPrev = start < dayStartMs
                    val crossNext = end > dayEndMs

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
                        canDragMoveOrResize = true
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
                val endMin = max(startMin + 1, ((clipEnd - dayStartMs + 30_000L) / 60_000L).toInt()).coerceIn(startMin + 1, 1440)
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

        val layoutBlocks = assignParallelLanes(rawBlocks)
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
    internal fun assignParallelLanes(raw: List<ScheduleGridBlock>): List<ScheduleGridBlock> {
        if (raw.isEmpty()) return emptyList()
        val sorted = raw.sortedWith(
            compareBy<ScheduleGridBlock> { it.startMinute }
                .thenBy { stateOrder(it.visualState) }
                .thenByDescending { it.endMinute }
        )

        val clusters = mutableListOf<MutableList<ScheduleGridBlock>>()
        var currentCluster = mutableListOf<ScheduleGridBlock>()
        var clusterMaxEnd = -1
        for (block in sorted) {
            if (currentCluster.isEmpty() || block.startMinute < clusterMaxEnd) {
                currentCluster += block
                clusterMaxEnd = max(clusterMaxEnd, block.endMinute)
            } else {
                clusters += currentCluster
                currentCluster = mutableListOf(block)
                clusterMaxEnd = block.endMinute
            }
        }
        if (currentCluster.isNotEmpty()) clusters += currentCluster

        val result = ArrayList<ScheduleGridBlock>(sorted.size)
        for (cluster in clusters) {
            val laneEndMinutes = mutableListOf<Int>()
            val assignedIndices = IntArray(cluster.size)
            for (i in cluster.indices) {
                val b = cluster[i]
                var chosen = -1
                for (laneIdx in laneEndMinutes.indices) {
                    if (laneEndMinutes[laneIdx] <= b.startMinute) {
                        chosen = laneIdx
                        laneEndMinutes[laneIdx] = b.endMinute
                        break
                    }
                }
                if (chosen < 0) {
                    chosen = laneEndMinutes.size
                    laneEndMinutes += b.endMinute
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
