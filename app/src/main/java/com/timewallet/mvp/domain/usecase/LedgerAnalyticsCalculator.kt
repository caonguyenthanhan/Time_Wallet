package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CategoryDurationStat(
    val category: TimeCategory,
    val durationSeconds: Long,
    val fractionOfDayOrTotal: Float
) {
    val durationMinutes: Int
        get() = (durationSeconds / 60.0).roundToInt()
}

/**
 * Thống kê đầy đủ cho 1 ngày lịch (Refinement 01 & 02):
 * - Tách biệt rõ:
 *   1. remainingDaySeconds: Quỹ thời gian còn lại trong ngày (tương lai từ now -> 24h)
 *   2. elapsedDaySeconds: Thời gian đã trôi qua trong ngày (00:00 -> now)
 *   3. timedSeconds: Thời gian thực tế có mốc giờ đã ghi nhận hợp lệ (<= cutoff, đã khử trùng lặp)
 *   4. untimedSeconds: Thời lượng tự khai báo chưa gắn mốc giờ (REPORTED_UNTIMED), TÁCH RIÊNG khỏi timedSeconds
 *   5. untrackedPastSeconds: Thời gian chưa ghi nhận trong quá khứ (= elapsedDaySeconds - timedSeconds)
 *   6. plannedSeconds: Thời gian dự kiến (PLANNED + phần chưa xác nhận của ACTIVE)
 */
data class DayLedgerStats(
    val dateLocal: String,
    val calendarDaySeconds: Long,
    val elapsedDaySeconds: Long,
    val remainingDaySeconds: Long,
    val timedSeconds: Long,
    val untimedSeconds: Long,
    val untrackedPastSeconds: Long,
    val plannedSeconds: Long,
    val activeRecordsCount: Int,
    val categoryBreakdown: List<CategoryDurationStat>,
    val untimedCategoryBreakdown: List<CategoryDurationStat> = emptyList(),
    val plannedCategoryBreakdown: List<CategoryDurationStat> = emptyList(),
    val confirmedCompletedSeconds: Long = 0L,
    val doingElapsedSeconds: Long = 0L,
    val parallelOverlapSeconds: Long = 0L,
    val standaloneCategorySeconds: Map<TimeCategory, Long> = emptyMap(),
    val pendingConfirmationCount: Int = 0,
    val reconciliationPendingCount: Int = 0
) {
    // Tương thích ngược với các thuộc tính cũ (luôn tách riêng, không bao giờ gộp timed + untimed)
    val unplacedSeconds: Long
        get() = untimedSeconds

    val untrackedSeconds: Long
        get() = untrackedPastSeconds

    val timedMinutes: Int
        get() = (timedSeconds / 60.0).roundToInt()

    val untimedMinutes: Int
        get() = (untimedSeconds / 60.0).roundToInt()

    val untrackedPastMinutes: Int
        get() = (untrackedPastSeconds / 60.0).roundToInt()

    val remainingDayMinutes: Int
        get() = (remainingDaySeconds / 60.0).roundToInt()

    val elapsedDayMinutes: Int
        get() = (elapsedDaySeconds / 60.0).roundToInt()
}

data class WeeklyInsightsSummary(
    val days: List<DayLedgerStats>,
    val totalTimedSeconds: Long,
    val totalUntimedSeconds: Long,
    val categoryTotals: List<CategoryDurationStat>,
    val untimedCategoryTotals: List<CategoryDurationStat> = emptyList(),
    val budgetProgressList: List<TimeBudgetProgress> = emptyList(),
    val currentWeekStartLocal: String = "",
    val currentWeekEndLocal: String = "",
    val reflectionPrompt: String
) {
    val totalUnplacedSeconds: Long
        get() = totalUntimedSeconds
}

object LedgerAnalyticsCalculator {

    private data class TaggedInterval(
        val startMs: Long,
        val endMs: Long,
        val category: TimeCategory
    )

    /**
     * Khi một bản ghi dài >= 1 giây có `startedAt % 1000 != 0` chạy vắt qua nửa đêm [boundaryMs], mỗi giây
     * `[start + i*1000, start + (i+1)*1000)` được gán về ngày chứa điểm giữa của giây đó, bảo đảm tổng
     * thời lượng cộng across các ngày đúng bằng `totalSeconds` (không mất 1 giây do làm tròn xuống ở hai phía).
     */
    private fun midnightGridBoundary(boundaryMs: Long, phaseOffsetMs: Long): Long {
        if (phaseOffsetMs == 0L) return boundaryMs
        return if (phaseOffsetMs < 500L) boundaryMs + phaseOffsetMs else boundaryMs - 1000L + phaseOffsetMs
    }

    private fun clipIntervalToDay(
        rawStart: Long,
        rawEnd: Long,
        dayStartMs: Long,
        dayEndMs: Long,
        nowCutoffMs: Long
    ): Pair<Long, Long>? {
        if (rawEnd <= rawStart) return null
        val spansDayStart = rawStart < dayStartMs && rawEnd > dayStartMs
        val spansDayEnd = rawStart < dayEndMs && rawEnd > dayEndMs
        val effEnd = if (spansDayStart || spansDayEnd) {
            val wholeSecs = (rawEnd - rawStart) / 1000L
            if (wholeSecs <= 0L) return null
            rawStart + wholeSecs * 1000L
        } else {
            rawEnd
        }
        val phase = ((rawStart % 1000L) + 1000L) % 1000L
        val lower = if (spansDayStart) midnightGridBoundary(dayStartMs, phase) else max(dayStartMs, rawStart)
        val upperDay = if (spansDayEnd && nowCutoffMs >= dayEndMs) {
            min(nowCutoffMs, midnightGridBoundary(dayEndMs, phase))
        } else {
            min(dayEndMs, nowCutoffMs)
        }
        val s = max(rawStart, lower)
        val e = min(effEnd, upperDay)
        return if (e > s) s to e else null
    }

    /**
     * Số giây thực tế đã trôi qua của [record] nằm trong khoảng ngày lịch `from..to` theo [zoneId].
     * Bản ghi có mốc giờ xuyên nửa đêm (`20:00 hôm qua → 07:00 hôm nay`) được cắt chính xác theo ranh giới `00:00`
     * để Tổng quan, Nhật ký, Phân tích, Mục tiêu và AI Coach luôn thống nhất cùng một nguồn tính.
     */
    internal fun recordActualSecondsInDateRange(
        record: LedgerRecord,
        from: LocalDate,
        to: LocalDate,
        nowEpochMs: Long,
        zoneId: ZoneId,
        confirmedFinalOnly: Boolean = false
    ): Long {
        if (to.isBefore(from) || record.isCancelled || record.isNeedsReconciliation) return 0L
        val todayStr = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate().toString()
        val conf = record.resolveConfirmationStateAt(nowEpochMs, todayStr)
        if (confirmedFinalOnly && !conf.isConfirmedFinalActual) return 0L
        if (record.resolveLayerAt(nowEpochMs, todayStr) == TimeLayer.PLANNED) return 0L

        val start = record.startedAt
        val end = record.endedAt
        if (start != null && end != null) {
            val winStartMs = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val winEndMs = to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val cutoffEnd = min(record.confirmedCutoffEpochMsAt(nowEpochMs), end)
            val clipped = clipIntervalToDay(start, cutoffEnd, winStartMs, winEndMs, nowEpochMs) ?: return 0L
            return ((clipped.second - clipped.first) / 1000L).coerceAtLeast(0L)
        }

        val d = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return 0L
        return if (!d.isBefore(from) && !d.isAfter(to)) {
            record.actualElapsedSecondsAt(nowEpochMs, todayStr)
        } else {
            0L
        }
    }

    /**
     * Chuyển bảng mili-giây theo danh mục thành số giây nguyên sao cho tổng theo danh mục khớp [totalSeconds]
     * (Largest Remainder / Hamilton method khi các mảnh < 1s thuộc nhiều danh mục gộp lại thành giây nguyên).
     */
    private fun conserveCategorySecondsFromMs(
        msMap: Map<TimeCategory, Long>,
        totalSeconds: Long
    ): Map<TimeCategory, Long> {
        if (msMap.isEmpty() || totalSeconds <= 0L) return emptyMap()
        val floorMap = msMap.mapValues { (_, ms) -> ms / 1000L }.toMutableMap()
        val deficit = (totalSeconds - floorMap.values.sum()).coerceAtLeast(0L)
        if (deficit > 0L) {
            val ranked = msMap.entries
                .sortedWith(
                    compareByDescending<Map.Entry<TimeCategory, Long>> { it.value % 1000L }
                        .thenByDescending { it.value }
                        .thenBy { it.key.ordinal }
                )
            var rem = deficit
            for (entry in ranked) {
                if (rem <= 0L) break
                floorMap[entry.key] = (floorMap[entry.key] ?: 0L) + 1L
                rem--
            }
        }
        return floorMap.filterValues { it > 0L }
    }

    /**
     * Tính thống kê ngày lịch theo múi giờ người dùng và mốc thời gian hiện tại [nowEpochMs]:
     * 1. Không tính thời gian tương lai (> nowEpochMs) vào thời gian đã sử dụng.
     * 2. Bản ghi ACTIVE tự khai báo: khi qua giờ kết thúc dự kiến (nowEpochMs >= endedAt) mà chưa có
     *    xác nhận kết thúc thực tế, KHÔNG mặc nhiên chuyển thành hoàn thành và KHÔNG cộng phần chưa xác nhận
     *    vào timedSeconds.
     * 3. Bản ghi PLANNED hoàn toàn không ảnh hưởng thống kê đã sử dụng.
     * 4. Ngăn tính trùng khoảng thời gian thực tế bằng thuật toán chia lát (interval slicing / union).
     * 5. Tách rõ thời lượng chưa gắn giờ (untimedSeconds / untimedCategoryBreakdown) khỏi tổng có mốc giờ
     *    (timedSeconds / categoryBreakdown); tuyệt đối không tạo tổng cộng gộp gây hiểu nhầm.
     */
    fun computeDayStats(
        dateLocal: String,
        records: List<LedgerRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null
    ): DayLedgerStats {
        val localDate = LocalDate.parse(dateLocal)
        val dayStartZoned = localDate.atStartOfDay(zoneId)
        val nextDayStartZoned = localDate.plusDays(1).atStartOfDay(zoneId)

        val dayStartMs = dayStartZoned.toInstant().toEpochMilli()
        val dayEndMs = nextDayStartZoned.toInstant().toEpochMilli()
        val calendarDaySeconds = Duration.between(dayStartZoned, nextDayStartZoned).seconds.coerceAtLeast(3600L)

        // Ranh giới quá khứ - tương lai trong ngày đang xét
        val pastCutoffMs = nowEpochMs.coerceIn(dayStartMs, dayEndMs)
        val elapsedDaySeconds = ((pastCutoffMs - dayStartMs) / 1000L).coerceAtLeast(0L)
        val remainingDaySeconds = ((dayEndMs - pastCutoffMs) / 1000L).coerceAtLeast(0L)

        val todayLocal = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val isFutureDate = localDate.isAfter(todayLocal)

        val rawActualIntervals = mutableListOf<TaggedInterval>()
        val rawConfirmedIntervals = mutableListOf<TaggedInterval>()
        val rawDoingIntervals = mutableListOf<TaggedInterval>()
        val standaloneCategoryMsMap = mutableMapOf<TimeCategory, Long>()
        var standaloneActualMsTotal = 0L
        var confirmedUntimedSecondsTotal = 0L
        var pendingConfirmCount = 0
        var reconcilePendingCount = 0
        val plannedTimedMsMap = mutableMapOf<TimeCategory, Long>()
        var plannedTimedMsTotal = 0L
        val plannedUntimedCategoryMap = mutableMapOf<TimeCategory, Long>()
        var plannedUntimedSecondsTotal = 0L
        var activeCount = 0

        for (record in records) {
            if (record.isCancelled) continue

            val confState = record.resolveConfirmationStateAt(nowEpochMs, todayLocal.toString())
            val resolvedLayer = record.resolveLayerAt(nowEpochMs, todayLocal.toString())

            val touchesDay = if (record.startedAt != null && record.endedAt != null) {
                record.endedAt > dayStartMs - 1000L && record.startedAt < dayEndMs + 1000L
            } else {
                record.date == dateLocal
            }
            if (touchesDay) {
                if (confState == com.timewallet.mvp.domain.model.ConfirmationState.PENDING_CONFIRMATION) pendingConfirmCount++
                if (record.isNeedsReconciliation) reconcilePendingCount++
            }
            if (record.isNeedsReconciliation) continue

            if (resolvedLayer == TimeLayer.ACTIVE && record.date == dateLocal) {
                activeCount++
            }

            if (record.startedAt != null && record.endedAt != null) {
                val start = record.startedAt
                val end = record.endedAt
                if (end <= dayStartMs - 1000L || start >= dayEndMs + 1000L) continue

                if (record.layer == TimeLayer.PLANNED || record.status == "PLANNED" || start >= pastCutoffMs) {
                    clipIntervalToDay(start, end, dayStartMs, dayEndMs, dayEndMs)?.let { (pStart, pEnd) ->
                        val pMs = pEnd - pStart
                        plannedTimedMsTotal += pMs
                        plannedTimedMsMap[record.category] = (plannedTimedMsMap[record.category] ?: 0L) + pMs
                    }
                } else {
                    val confirmedCutoffMs = min(nowEpochMs, record.confirmedCutoffEpochMsAt(nowEpochMs))
                    val rawConfirmedEnd = min(confirmedCutoffMs, end)
                    clipIntervalToDay(start, rawConfirmedEnd, dayStartMs, dayEndMs, nowEpochMs)?.let { (aStart, aEnd) ->
                        val interval = TaggedInterval(
                            startMs = aStart,
                            endMs = aEnd,
                            category = record.category
                        )
                        rawActualIntervals.add(interval)
                        val durMs = aEnd - aStart
                        standaloneActualMsTotal += durMs
                        standaloneCategoryMsMap[record.category] = (standaloneCategoryMsMap[record.category] ?: 0L) + durMs
                        if (confState.isConfirmedFinalActual) {
                            rawConfirmedIntervals.add(interval)
                        } else if (confState == com.timewallet.mvp.domain.model.ConfirmationState.DOING) {
                            rawDoingIntervals.add(interval)
                        }
                    }
                    // Phần chưa xác nhận hoặc thuộc tương lai -> đưa vào plannedSeconds, KHÔNG tính vào đã sử dụng
                    val unconfirmedStart = max(confirmedCutoffMs, start)
                    clipIntervalToDay(unconfirmedStart, end, dayStartMs, dayEndMs, dayEndMs)?.let { (uStart, uEnd) ->
                        val uMs = uEnd - uStart
                        plannedTimedMsTotal += uMs
                        plannedTimedMsMap[record.category] = (plannedTimedMsMap[record.category] ?: 0L) + uMs
                    }
                }
            } else {
                // Bản ghi không có mốc giờ
                if (record.date == dateLocal) {
                    if (record.layer == TimeLayer.PLANNED || record.status == "PLANNED" || isFutureDate) {
                        plannedUntimedSecondsTotal += record.durationSeconds
                        plannedUntimedCategoryMap[record.category] =
                            (plannedUntimedCategoryMap[record.category] ?: 0L) + record.durationSeconds
                    } else if (confState.isConfirmedFinalActual) {
                        confirmedUntimedSecondsTotal += record.durationSeconds
                    }
                }
            }
        }

        // Nếu có phiên đồng hồ đang chạy thực tế, đưa phần đã chạy [startedAt, nowEpochMs] vào union
        if (activeTimer != null && activeTimer.startedAtEpochMs < nowEpochMs && pastCutoffMs > dayStartMs) {
            clipIntervalToDay(activeTimer.startedAtEpochMs, nowEpochMs, dayStartMs, dayEndMs, nowEpochMs)?.let { (tStart, tEnd) ->
                activeCount++
                rawActualIntervals.add(
                    TaggedInterval(
                        startMs = tStart,
                        endMs = tEnd,
                        category = TimeCategory.fromKeyOrDefault(activeTimer.categoryKey)
                    )
                )
            }
        }

        val plannedTimedSeconds = plannedTimedMsTotal / 1000L
        val plannedSecondsTotal = plannedUntimedSecondsTotal + plannedTimedSeconds
        val plannedCategoryMap = mutableMapOf<TimeCategory, Long>()
        conserveCategorySecondsFromMs(plannedTimedMsMap, plannedTimedSeconds).forEach { (cat, secs) ->
            plannedCategoryMap[cat] = secs
        }
        plannedUntimedCategoryMap.forEach { (cat, secs) ->
            plannedCategoryMap[cat] = (plannedCategoryMap[cat] ?: 0L) + secs
        }

        // Khử trùng lặp khoảng thời gian thực tế bằng Sweep-Line Elementary Intervals trên mili-giây trước khi quy đổi giây
        val categoryMsMap = mutableMapOf<TimeCategory, Long>()
        var timedMsTotal = 0L

        if (rawActualIntervals.isNotEmpty()) {
            val boundaries = rawActualIntervals
                .flatMap { listOf(it.startMs, it.endMs) }
                .distinct()
                .sorted()

            for (i in 0 until boundaries.size - 1) {
                val segStart = boundaries[i]
                val segEnd = boundaries[i + 1]
                if (segEnd <= segStart) continue

                val covering = rawActualIntervals.firstOrNull { it.startMs <= segStart && it.endMs >= segEnd }
                if (covering != null) {
                    val segMs = segEnd - segStart
                    if (segMs > 0L) {
                        timedMsTotal += segMs
                        categoryMsMap[covering.category] = (categoryMsMap[covering.category] ?: 0L) + segMs
                    }
                }
            }
        }
        val timedSecondsTotal = unionMsOf(rawActualIntervals) / 1000L
        val confirmedTimedSeconds = unionMsOf(rawConfirmedIntervals) / 1000L
        val doingElapsedSeconds = unionMsOf(rawDoingIntervals) / 1000L
        val parallelOverlapSeconds = ((standaloneActualMsTotal - timedMsTotal).coerceAtLeast(0L)) / 1000L
        val standaloneCategorySeconds = standaloneCategoryMsMap.mapValues { (_, ms) -> ms / 1000L }.filterValues { it > 0L }
        val categorySecondsMap = conserveCategorySecondsFromMs(categoryMsMap, timedSecondsTotal)

        // Thời lượng chưa gắn giờ (REPORTED_UNTIMED) — tách riêng theo danh mục, không cộng vào timedSecondsTotal
        val untimedCategoryMap = mutableMapOf<TimeCategory, Long>()
        var untimedSecondsTotal = 0L
        if (!isFutureDate) {
            records
                .filter {
                    !it.isCancelled &&
                        !it.isNeedsReconciliation &&
                        it.date == dateLocal &&
                        it.startedAt == null &&
                        it.endedAt == null &&
                        it.layer != TimeLayer.PLANNED &&
                        it.status != "PLANNED"
                }
                .forEach { rec ->
                    untimedSecondsTotal += rec.durationSeconds
                    untimedCategoryMap[rec.category] =
                        (untimedCategoryMap[rec.category] ?: 0L) + rec.durationSeconds
                }
        }

        val untrackedPastSeconds = max(0L, elapsedDaySeconds - timedSecondsTotal)

        val breakdown = categorySecondsMap.entries
            .sortedByDescending { it.value }
            .map { (cat, secs) ->
                CategoryDurationStat(
                    category = cat,
                    durationSeconds = secs,
                    fractionOfDayOrTotal = (secs.toFloat() / calendarDaySeconds.toFloat()).coerceIn(0f, 1f)
                )
            }

        val untimedDenom = untimedSecondsTotal.coerceAtLeast(1L).toFloat()
        val untimedBreakdown = untimedCategoryMap.entries
            .sortedByDescending { it.value }
            .map { (cat, secs) ->
                CategoryDurationStat(
                    category = cat,
                    durationSeconds = secs,
                    fractionOfDayOrTotal = (secs.toFloat() / untimedDenom).coerceIn(0f, 1f)
                )
            }

        val plannedDenom = plannedSecondsTotal.coerceAtLeast(1L).toFloat()
        val plannedBreakdown = plannedCategoryMap.entries
            .sortedByDescending { it.value }
            .map { (cat, secs) ->
                CategoryDurationStat(
                    category = cat,
                    durationSeconds = secs,
                    fractionOfDayOrTotal = (secs.toFloat() / plannedDenom).coerceIn(0f, 1f)
                )
            }

        return DayLedgerStats(
            dateLocal = dateLocal,
            calendarDaySeconds = calendarDaySeconds,
            elapsedDaySeconds = elapsedDaySeconds,
            remainingDaySeconds = remainingDaySeconds,
            timedSeconds = timedSecondsTotal,
            untimedSeconds = untimedSecondsTotal,
            untrackedPastSeconds = untrackedPastSeconds,
            plannedSeconds = plannedSecondsTotal,
            activeRecordsCount = activeCount,
            categoryBreakdown = breakdown,
            untimedCategoryBreakdown = untimedBreakdown,
            plannedCategoryBreakdown = plannedBreakdown,
            confirmedCompletedSeconds = confirmedTimedSeconds + confirmedUntimedSecondsTotal,
            doingElapsedSeconds = doingElapsedSeconds,
            parallelOverlapSeconds = parallelOverlapSeconds,
            standaloneCategorySeconds = standaloneCategorySeconds,
            pendingConfirmationCount = pendingConfirmCount,
            reconciliationPendingCount = reconcilePendingCount
        )
    }

    private fun unionMsOf(intervals: List<TaggedInterval>): Long {
        if (intervals.isEmpty()) return 0L
        val boundaries = intervals.flatMap { listOf(it.startMs, it.endMs) }.distinct().sorted()
        var sum = 0L
        for (i in 0 until boundaries.size - 1) {
            val s = boundaries[i]
            val e = boundaries[i + 1]
            if (e > s && intervals.any { it.startMs <= s && it.endMs >= e }) {
                sum += (e - s)
            }
        }
        return sum
    }

    /**
     * Xác định ngày Thứ Hai (đầu tuần) và Chủ Nhật (cuối tuần) theo đúng múi giờ thiết bị [zoneId]
     * tại thời điểm [nowEpochMs].
     */
    fun resolveCurrentWeekBounds(
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): Pair<LocalDate, LocalDate> {
        val todayLocal = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val monday = todayLocal.with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        return monday to sunday
    }

    /**
     * Tính tiến độ Ngân sách Thời gian (Time Budget) theo danh mục cho ngày hiện tại hoặc tuần hiện tại
     * (Thứ Hai -> Chủ Nhật theo đúng múi giờ thiết bị [zoneId]).
     * - usedSeconds chỉ gồm phần có mốc giờ (giữ cho trục giờ); "Đã ghi nhận" = usedSeconds + untimedNoteSeconds (xem TimeBudgetProgress).
     * - PLANNED không được tính là đã sử dụng.
     * - untimedNoteSeconds luôn được tách riêng để hiển thị phần cấu thành.
     */
    fun computeBudgetProgress(
        budgets: List<CategoryTimeBudget>,
        records: List<LedgerRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null
    ): List<TimeBudgetProgress> {
        if (budgets.isEmpty()) return emptyList()

        val todayLocal = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val (weekMonday, weekSunday) = resolveCurrentWeekBounds(zoneId, nowEpochMs)

        val todayStats = computeDayStats(
            dateLocal = todayLocal.toString(),
            records = records,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer
        )

        val currentWeekStats = (0..6).map { offset ->
            val dateStr = weekMonday.plusDays(offset.toLong()).toString()
            if (dateStr == todayLocal.toString()) {
                todayStats
            } else {
                computeDayStats(
                    dateLocal = dateStr,
                    records = records,
                    zoneId = zoneId,
                    nowEpochMs = nowEpochMs,
                    activeTimer = activeTimer
                )
            }
        }

        return budgets
            .sortedWith(
                compareByDescending<CategoryTimeBudget> { it.isPriority }
                    .thenBy { it.priorityOrder }
                    .thenBy { it.category.ordinal }
            )
            .map { budget ->
                val cat = budget.category
                when (budget.period) {
                    BudgetPeriod.DAILY -> {
                        val usedTimed = todayStats.categoryBreakdown
                            .firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        val untimed = todayStats.untimedCategoryBreakdown
                            .firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        val planned = todayStats.plannedCategoryBreakdown
                            .firstOrNull { it.category == cat }?.durationSeconds ?: 0L

                        val remaining = max(0L, budget.targetSeconds - usedTimed)
                        val exceeded = max(0L, usedTimed - budget.targetSeconds)
                        val delta = budget.targetSeconds - usedTimed

                        TimeBudgetProgress(
                            budget = budget,
                            periodStartDateLocal = todayLocal.toString(),
                            periodEndDateLocal = todayLocal.toString(),
                            usedSeconds = usedTimed,
                            targetSeconds = budget.targetSeconds,
                            remainingSeconds = remaining,
                            exceededSeconds = exceeded,
                            deltaSeconds = delta,
                            untimedNoteSeconds = untimed,
                            plannedSeconds = planned
                        )
                    }

                    BudgetPeriod.WEEKLY -> {
                        val usedTimed = currentWeekStats.sumOf { day ->
                            day.categoryBreakdown.firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        }
                        val untimed = currentWeekStats.sumOf { day ->
                            day.untimedCategoryBreakdown.firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        }
                        val planned = currentWeekStats.sumOf { day ->
                            day.plannedCategoryBreakdown.firstOrNull { it.category == cat }?.durationSeconds ?: 0L
                        }

                        val remaining = max(0L, budget.targetSeconds - usedTimed)
                        val exceeded = max(0L, usedTimed - budget.targetSeconds)
                        val delta = budget.targetSeconds - usedTimed

                        TimeBudgetProgress(
                            budget = budget,
                            periodStartDateLocal = weekMonday.toString(),
                            periodEndDateLocal = weekSunday.toString(),
                            usedSeconds = usedTimed,
                            targetSeconds = budget.targetSeconds,
                            remainingSeconds = remaining,
                            exceededSeconds = exceeded,
                            deltaSeconds = delta,
                            untimedNoteSeconds = untimed,
                            plannedSeconds = planned
                        )
                    }
                }
            }
    }

    /**
     * Tạo gợi ý phản tư trung lập dựa trên ngân sách thật của người dùng:
     * - Không phán xét đạo đức hay cảm xúc.
     * - Không tự động thay đổi mục tiêu của người dùng.
     */
    fun buildNeutralBudgetReflection(
        budgetProgressList: List<TimeBudgetProgress>,
        totalTimedSeconds: Long,
        totalUntimedSeconds: Long
    ): String {
        if (budgetProgressList.isNotEmpty()) {
            val topSummaries = budgetProgressList.take(3).joinToString(" · ") { item ->
                "${item.category.displayName} (${item.targetFormatted}): đã ghi nhận ${item.recordedSummaryVi}, ${item.combinedDifferenceLabelVi.lowercase()}"
            }
            return "Đối chiếu ngân sách thực tế của bạn: $topSummaries. Gợi ý phản tư: Mức phân bổ hiện tại đã sát với ưu tiên thực tế của bạn chưa, hay bạn muốn tự điều chỉnh lại lịch trình / mục tiêu cho phù hợp hơn?"
        }

        return if (totalTimedSeconds > 0L || totalUntimedSeconds > 0L) {
            "Trong 7 ngày qua, bạn đã ghi nhận ${formatHoursMinutesFromSeconds(totalTimedSeconds)} thực tế có mốc giờ và ${formatHoursMinutesFromSeconds(totalUntimedSeconds)} chưa gắn giờ (tách riêng). Bạn có thể tự đặt Ngân sách thời gian theo danh mục nếu muốn đối chiếu mục tiêu cá nhân."
        } else {
            "Bạn muốn tuần này dành thời gian cho danh mục nào? Hãy tự đặt ngân sách khi cần theo dõi, hệ thống không tự đặt hay thay đổi mục tiêu thay bạn."
        }
    }

    /**
     * Tính toán thống kê 7 ngày gần nhất kết thúc tại endDateLocal, kèm tiến độ Ngân sách Thời gian.
     */
    fun computeWeeklyInsights(
        endDateLocal: String,
        records: List<LedgerRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null,
        budgets: List<CategoryTimeBudget> = emptyList()
    ): WeeklyInsightsSummary {
        val endDate = LocalDate.parse(endDateLocal)
        val dayStatsList = (-6..0).map { offset ->
            val dateStr = endDate.plusDays(offset.toLong()).toString()
            computeDayStats(dateStr, records, zoneId, nowEpochMs, activeTimer)
        }

        val totalTimedSeconds = dayStatsList.sumOf { it.timedSeconds }
        val totalUntimedSeconds = dayStatsList.sumOf { it.untimedSeconds }

        val categorySeconds = mutableMapOf<TimeCategory, Long>()
        val untimedCategorySeconds = mutableMapOf<TimeCategory, Long>()
        for (day in dayStatsList) {
            for (catStat in day.categoryBreakdown) {
                categorySeconds[catStat.category] =
                    (categorySeconds[catStat.category] ?: 0L) + catStat.durationSeconds
            }
            for (untimedStat in day.untimedCategoryBreakdown) {
                untimedCategorySeconds[untimedStat.category] =
                    (untimedCategorySeconds[untimedStat.category] ?: 0L) + untimedStat.durationSeconds
            }
        }

        val denom = totalTimedSeconds.coerceAtLeast(1L).toFloat()
        val categoryTotals = categorySeconds.entries
            .sortedByDescending { it.value }
            .map { (cat, secs) ->
                CategoryDurationStat(
                    category = cat,
                    durationSeconds = secs,
                    fractionOfDayOrTotal = (secs.toFloat() / denom).coerceIn(0f, 1f)
                )
            }

        val untimedDenom = totalUntimedSeconds.coerceAtLeast(1L).toFloat()
        val untimedCategoryTotals = untimedCategorySeconds.entries
            .sortedByDescending { it.value }
            .map { (cat, secs) ->
                CategoryDurationStat(
                    category = cat,
                    durationSeconds = secs,
                    fractionOfDayOrTotal = (secs.toFloat() / untimedDenom).coerceIn(0f, 1f)
                )
            }

        val (weekMonday, weekSunday) = resolveCurrentWeekBounds(zoneId, nowEpochMs)
        val budgetProgressList = computeBudgetProgress(
            budgets = budgets,
            records = records,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer
        )

        val reflectionPrompt = buildNeutralBudgetReflection(
            budgetProgressList = budgetProgressList,
            totalTimedSeconds = totalTimedSeconds,
            totalUntimedSeconds = totalUntimedSeconds
        )

        return WeeklyInsightsSummary(
            days = dayStatsList,
            totalTimedSeconds = totalTimedSeconds,
            totalUntimedSeconds = totalUntimedSeconds,
            categoryTotals = categoryTotals,
            untimedCategoryTotals = untimedCategoryTotals,
            budgetProgressList = budgetProgressList,
            currentWeekStartLocal = weekMonday.toString(),
            currentWeekEndLocal = weekSunday.toString(),
            reflectionPrompt = reflectionPrompt
        )
    }

    fun formatHoursMinutesFromSeconds(seconds: Long): String {
        val totalMinutes = max(0L, (seconds + 30L) / 60L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return "${hours}h ${mins.toString().padStart(2, '0')}"
    }

    fun formatCompactDurationSeconds(seconds: Long): String {
        val totalMinutes = max(1L, (seconds + 30L) / 60L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}p"
            hours > 0L -> "${hours}h"
            else -> "${mins}p"
        }
    }
}
