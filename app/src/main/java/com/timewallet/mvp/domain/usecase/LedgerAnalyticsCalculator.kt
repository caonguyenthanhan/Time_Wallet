package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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

/** 4 tầng hiệu quả của một ngày đủ 24 giờ trên ngăn Thống kê (tham chiếu Time_Wallet_Mo_Rong_UX.html). */
enum class EfficiencyTier(val labelVi: String) {
    GOAL("Mục tiêu"),
    NEUTRAL("Phi mục tiêu"),
    WASTE("Lãng phí"),
    UNKNOWN("Chưa rõ")
}

data class DayEfficiencyBreakdown(
    val dateLocal: String,
    val calendarDaySeconds: Long,
    val goalSeconds: Long,
    val neutralSeconds: Long,
    val wasteSeconds: Long,
    val unknownSeconds: Long,
    val untimedSeconds: Long
) {
    fun secondsFor(filter: EfficiencyTier?): Long = when (filter) {
        EfficiencyTier.GOAL -> goalSeconds
        EfficiencyTier.NEUTRAL -> neutralSeconds
        EfficiencyTier.WASTE -> wasteSeconds
        EfficiencyTier.UNKNOWN -> unknownSeconds
        null -> goalSeconds + neutralSeconds + wasteSeconds
    }
}

enum class ActivityBucket(
    val labelVi: String,
    val shortLabelVi: String,
    val colorHex: Long
) {
    SLEEP_REST("Ngủ, nghỉ", "Ngủ/nghỉ", 0xFF7D95A8L),
    WORK("Công việc", "Công việc", 0xFF287BCBL),
    LEARN("Học tập", "Học tập", 0xFF8962C7L),
    HEALTH_LIFE("Sức khỏe & Sinh hoạt", "Sức khỏe", 0xFF24A890L),
    FUN_SOCIAL("Giải trí & Khác", "Giải trí", 0xFFEC9863L);

    companion object {
        fun fromCategory(category: TimeCategory): ActivityBucket = when (category) {
            TimeCategory.SLEEP, TimeCategory.REST -> SLEEP_REST
            TimeCategory.WORK -> WORK
            TimeCategory.LEARN -> LEARN
            TimeCategory.HEALTH, TimeCategory.LIFE -> HEALTH_LIFE
            TimeCategory.FUN, TimeCategory.SOCIAL, TimeCategory.OTHER -> FUN_SOCIAL
        }
    }
}

data class ActivityItemBreakdown(
    val label: String,
    val tagName: String?,
    val category: TimeCategory,
    val bucket: ActivityBucket,
    val durationSeconds: Long,
    val untimedSeconds: Long = 0L,
    val matchesGoal: Boolean = false,
    val isUntaggedOrOther: Boolean = false
)

data class DayActivityBreakdown(
    val dateLocal: String,
    val calendarDaySeconds: Long,
    val recordedTimedSeconds: Long,
    val unrecordedSeconds: Long,
    val untimedSeconds: Long,
    val uncategorizedOrUntaggedSeconds: Long,
    val bucketSeconds: Map<ActivityBucket, Long>,
    val topItems: List<ActivityItemBreakdown>,
    val prevWeekRecordedTimedSeconds: Long = 0L,
    val prevWeekBucketSeconds: Map<ActivityBucket, Long> = emptyMap()
) {
    fun secondsFor(bucketFilter: ActivityBucket?): Long =
        if (bucketFilter == null) recordedTimedSeconds else (bucketSeconds[bucketFilter] ?: 0L)
}

data class ActivityBucketTotal(
    val bucket: ActivityBucket,
    val durationSeconds: Long,
    val prevWeekDurationSeconds: Long,
    val deltaPercentVsPrevWeek: Int?,
    val goalLinkedSeconds: Long,
    val topItems: List<ActivityItemBreakdown>
)

data class WallySuggestedSlot(
    val dateLocal: String,
    val startMinute: Int,
    val endMinute: Int,
    val durationMinutes: Int,
    val categoryKey: String,
    val tagId: String? = null,
    val title: String,
    val buttonLabelVi: String,
    val slotReasonVi: String
)

sealed class WallyStoryAction {
    data class ScheduleSlot(val slot: WallySuggestedSlot) : WallyStoryAction()
    data class OpenTagAssign(val buttonLabelVi: String, val categoryKey: String) : WallyStoryAction()
    data class OpenConfirmPending(val buttonLabelVi: String, val targetDateLocal: String) : WallyStoryAction()
}

data class WallyEvidenceItem(
    val dateLocal: String,
    val label: String,
    val detailVi: String
)

data class WallyStorySummary(
    val mainFinding: String,
    val findingDetail: String? = null,
    val actionLabel: String? = null,
    val action: WallyStoryAction? = null,
    val uncertaintyNote: String? = null,
    val comparisonPeriodVi: String = "",
    val rationaleVi: String = "",
    val evidenceItems: List<WallyEvidenceItem> = emptyList(),
    val whatHappened: String = mainFinding,
    val notable: String = findingDetail.orEmpty(),
    val goalImpact: String = comparisonPeriodVi,
    val nextAction: String = actionLabel.orEmpty(),
    val goalContextNote: String? = null
)

data class EfficiencyGoalItem(
    val id: String,
    val title: String,
    val progressText: String,
    val fraction: Float,
    val accentHex: Long,
    val met: Boolean,
    val hintVi: String? = null
)

data class WeeklyEfficiencySummary(
    val days: List<DayEfficiencyBreakdown>,
    val totalGoalSeconds: Long,
    val totalNeutralSeconds: Long,
    val totalWasteSeconds: Long,
    val totalUnknownSeconds: Long,
    val totalUntimedSeconds: Long,
    val goalItems: List<EfficiencyGoalItem>,
    val actionableInsight: String,
    val activityDays: List<DayActivityBreakdown> = emptyList(),
    val activityTotals: List<ActivityBucketTotal> = emptyList(),
    val previousWeekTotalTimedSeconds: Long = 0L,
    val weekOverWeekDeltaPercent: Int? = null,
    val totalUncategorizedOrUntaggedSeconds: Long = 0L,
    val activeGoalsCount: Int = 0,
    val wallyStory: WallyStorySummary? = null
)

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

    private data class EfficiencyTaggedInterval(
        val startMs: Long,
        val endMs: Long,
        val tier: EfficiencyTier,
        val category: TimeCategory,
        val bucket: ActivityBucket,
        val itemLabel: String,
        val tagName: String?,
        val matchesGoal: Boolean,
        val isUntaggedOrOther: Boolean
    )

    private data class ItemAccumKey(
        val label: String,
        val tagName: String?,
        val category: TimeCategory,
        val bucket: ActivityBucket,
        val matchesGoal: Boolean,
        val isUntaggedOrOther: Boolean
    )

    private data class ItemAccumVal(
        var timedMs: Long = 0L,
        var untimedSeconds: Long = 0L
    )

    private data class ComputedDaySlice(
        val date: LocalDate,
        val efficiency: DayEfficiencyBreakdown,
        val bucketSeconds: Map<ActivityBucket, Long>,
        val uncategorizedOrUntaggedSeconds: Long,
        val untaggedCategorySeconds: Map<TimeCategory, Long>,
        val itemStats: Map<ItemAccumKey, ItemAccumVal>
    )

    private fun <K : Comparable<K>> conserveSecondsFromMs(
        msMap: Map<K, Long>,
        totalSeconds: Long
    ): Map<K, Long> {
        if (msMap.isEmpty() || totalSeconds <= 0L) return emptyMap()
        val floorMap = msMap.mapValues { (_, ms) -> ms / 1000L }.toMutableMap()
        val deficit = (totalSeconds - floorMap.values.sum()).coerceAtLeast(0L)
        if (deficit > 0L) {
            val ranked = msMap.entries
                .sortedWith(
                    compareByDescending<Map.Entry<K, Long>> { it.value % 1000L }
                        .thenByDescending { it.value }
                        .thenBy { it.key }
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

    private fun computePercentDelta(current: Long, previous: Long, minPrevThresholdSeconds: Long): Int? {
        if (previous < minPrevThresholdSeconds) return null
        return kotlin.math.round(((current - previous).toDouble() / previous.toDouble()) * 100.0).toInt()
    }

    private fun computeDaySlices(
        date: LocalDate,
        records: List<LedgerRecord>,
        goalGroups: Map<String, List<Goal>>,
        budgetCategories: Set<TimeCategory>,
        tagById: Map<String, Tag>,
        zoneId: ZoneId,
        nowEpochMs: Long,
        todayLocal: LocalDate,
        activeTimer: ActiveTimerSession?
    ): ComputedDaySlice {
        val dateStr = date.toString()
        val dayStartZoned = date.atStartOfDay(zoneId)
        val nextDayStartZoned = date.plusDays(1).atStartOfDay(zoneId)
        val dayStartMs = dayStartZoned.toInstant().toEpochMilli()
        val dayEndMs = nextDayStartZoned.toInstant().toEpochMilli()
        val calendarDaySeconds = Duration.between(dayStartZoned, nextDayStartZoned).seconds.coerceAtLeast(3600L)
        val pastCutoffMs = nowEpochMs.coerceIn(dayStartMs, dayEndMs)
        val isFutureDate = date.isAfter(todayLocal)

        val activeGoalTargetsOnDay = goalGroups.values.mapNotNull { versions ->
            GoalRules.versionOn(versions, date)?.takeIf {
                it.status == GoalVersionStatus.ACTIVE && it.kind == GoalKind.MIN
            }
        }

        fun resolveRecordTier(cat: TimeCategory, tagId: String?, isWaste: Boolean): EfficiencyTier {
            if (isWaste) return EfficiencyTier.WASTE
            val matchesGoal = if (activeGoalTargetsOnDay.isNotEmpty()) {
                activeGoalTargetsOnDay.any { g ->
                    when (g.targetType) {
                        GoalTargetType.TAG -> tagId != null && tagId == g.targetKey
                        GoalTargetType.CATEGORY -> cat.key == g.targetKey
                    }
                }
            } else {
                cat in budgetCategories
            }
            return if (matchesGoal) EfficiencyTier.GOAL else EfficiencyTier.NEUTRAL
        }

        fun resolveItemKey(cat: TimeCategory, title: String, tagId: String?, tier: EfficiencyTier): ItemAccumKey {
            val tagName = tagId?.let { tagById[it]?.name }?.takeIf { it.isNotBlank() }
            val cleanTitle = title.trim().ifBlank { cat.displayName }
            val displayLabel = tagName ?: cleanTitle
            val untaggedOrOther = (tagName == null || cat == TimeCategory.OTHER)
            return ItemAccumKey(
                label = displayLabel,
                tagName = tagName,
                category = cat,
                bucket = ActivityBucket.fromCategory(cat),
                matchesGoal = tier == EfficiencyTier.GOAL,
                isUntaggedOrOther = untaggedOrOther
            )
        }

        val intervals = mutableListOf<EfficiencyTaggedInterval>()
        var untimedSeconds = 0L
        val itemAccum = mutableMapOf<ItemAccumKey, ItemAccumVal>()

        for (record in records) {
            if (record.isCancelled || record.isNeedsReconciliation) continue
            val tier = resolveRecordTier(record.category, record.tagId, record.isMarkedWaste)
            if (record.startedAt != null && record.endedAt != null) {
                val start = record.startedAt
                val end = record.endedAt
                if (end <= dayStartMs - 1000L || start >= dayEndMs + 1000L) continue
                if (record.layer == TimeLayer.PLANNED || record.status == "PLANNED" || start >= pastCutoffMs) continue

                val confirmedCutoffMs = min(nowEpochMs, record.confirmedCutoffEpochMsAt(nowEpochMs))
                val rawConfirmedEnd = min(confirmedCutoffMs, end)
                clipIntervalToDay(start, rawConfirmedEnd, dayStartMs, dayEndMs, nowEpochMs)?.let { (aStart, aEnd) ->
                    val itemKey = resolveItemKey(record.category, record.title, record.tagId, tier)
                    intervals += EfficiencyTaggedInterval(
                        startMs = aStart,
                        endMs = aEnd,
                        tier = tier,
                        category = record.category,
                        bucket = itemKey.bucket,
                        itemLabel = itemKey.label,
                        tagName = itemKey.tagName,
                        matchesGoal = itemKey.matchesGoal,
                        isUntaggedOrOther = itemKey.isUntaggedOrOther
                    )
                }
            } else if (!isFutureDate && record.date == dateStr &&
                record.layer != TimeLayer.PLANNED && record.status != "PLANNED"
            ) {
                untimedSeconds += record.durationSeconds
                val key = resolveItemKey(record.category, record.title, record.tagId, tier)
                val acc = itemAccum.getOrPut(key) { ItemAccumVal() }
                acc.untimedSeconds += record.durationSeconds
            }
        }

        if (activeTimer != null && activeTimer.startedAtEpochMs < nowEpochMs && pastCutoffMs > dayStartMs) {
            clipIntervalToDay(activeTimer.startedAtEpochMs, nowEpochMs, dayStartMs, dayEndMs, nowEpochMs)?.let { (tStart, tEnd) ->
                val tCat = TimeCategory.fromKeyOrDefault(activeTimer.categoryKey)
                val tier = resolveRecordTier(tCat, activeTimer.tagId, isWaste = false)
                val itemKey = resolveItemKey(tCat, activeTimer.title, activeTimer.tagId, tier)
                intervals += EfficiencyTaggedInterval(
                    startMs = tStart,
                    endMs = tEnd,
                    tier = tier,
                    category = tCat,
                    bucket = itemKey.bucket,
                    itemLabel = itemKey.label,
                    tagName = itemKey.tagName,
                    matchesGoal = itemKey.matchesGoal,
                    isUntaggedOrOther = itemKey.isUntaggedOrOther
                )
            }
        }

        val tierMsMap = mutableMapOf<EfficiencyTier, Long>()
        val bucketMsMap = mutableMapOf<ActivityBucket, Long>()
        val untaggedCatMs = mutableMapOf<TimeCategory, Long>()
        var untaggedOrOtherMs = 0L
        var totalTimedMs = 0L

        if (intervals.isNotEmpty()) {
            val boundaries = intervals.flatMap { listOf(it.startMs, it.endMs) }.distinct().sorted()
            for (i in 0 until boundaries.size - 1) {
                val s = boundaries[i]
                val e = boundaries[i + 1]
                if (e <= s) continue
                val covering = intervals.filter { it.startMs <= s && it.endMs >= e }
                if (covering.isEmpty()) continue
                val winningTier = when {
                    covering.any { it.tier == EfficiencyTier.WASTE } -> EfficiencyTier.WASTE
                    covering.any { it.tier == EfficiencyTier.GOAL } -> EfficiencyTier.GOAL
                    else -> EfficiencyTier.NEUTRAL
                }
                val primary = covering.first()
                val ms = e - s
                totalTimedMs += ms
                tierMsMap[winningTier] = (tierMsMap[winningTier] ?: 0L) + ms
                bucketMsMap[primary.bucket] = (bucketMsMap[primary.bucket] ?: 0L) + ms
                if (primary.isUntaggedOrOther) {
                    untaggedOrOtherMs += ms
                }
                if (primary.tagName == null) {
                    untaggedCatMs[primary.category] = (untaggedCatMs[primary.category] ?: 0L) + ms
                }
                val pKey = ItemAccumKey(
                    label = primary.itemLabel,
                    tagName = primary.tagName,
                    category = primary.category,
                    bucket = primary.bucket,
                    matchesGoal = primary.matchesGoal,
                    isUntaggedOrOther = primary.isUntaggedOrOther
                )
                itemAccum.getOrPut(pKey) { ItemAccumVal() }.timedMs += ms
            }
        }

        val timedSeconds = totalTimedMs / 1000L
        val tierSecs = conserveSecondsFromMs(tierMsMap, timedSeconds)
        val bucketSecs = conserveSecondsFromMs(bucketMsMap, timedSeconds)
        val gSec = tierSecs[EfficiencyTier.GOAL] ?: 0L
        val nSec = tierSecs[EfficiencyTier.NEUTRAL] ?: 0L
        val wSec = tierSecs[EfficiencyTier.WASTE] ?: 0L
        val unkSec = (calendarDaySeconds - timedSeconds).coerceAtLeast(0L)

        val eff = DayEfficiencyBreakdown(
            dateLocal = dateStr,
            calendarDaySeconds = calendarDaySeconds,
            goalSeconds = gSec,
            neutralSeconds = nSec,
            wasteSeconds = wSec,
            unknownSeconds = unkSec,
            untimedSeconds = untimedSeconds
        )
        return ComputedDaySlice(
            date = date,
            efficiency = eff,
            bucketSeconds = bucketSecs,
            uncategorizedOrUntaggedSeconds = untaggedOrOtherMs / 1000L,
            untaggedCategorySeconds = untaggedCatMs.mapValues { (_, ms) -> ms / 1000L }.filterValues { it > 0L },
            itemStats = itemAccum
        )
    }

    private fun buildTopItems(stats: Map<ItemAccumKey, ItemAccumVal>, limit: Int): List<ActivityItemBreakdown> =
        stats.entries
            .map { (k, v) ->
                ActivityItemBreakdown(
                    label = k.label,
                    tagName = k.tagName,
                    category = k.category,
                    bucket = k.bucket,
                    durationSeconds = v.timedMs / 1000L,
                    untimedSeconds = v.untimedSeconds,
                    matchesGoal = k.matchesGoal,
                    isUntaggedOrOther = k.isUntaggedOrOther
                )
            }
            .filter { it.durationSeconds > 0L || it.untimedSeconds > 0L }
            .sortedWith(
                compareByDescending<ActivityItemBreakdown> { it.durationSeconds + it.untimedSeconds }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
            )
            .take(limit)

    private fun buildWallyDataStory(
        records: List<LedgerRecord>,
        tags: List<Tag>,
        summaries: List<GoalRules.Summary>,
        goalItems: List<EfficiencyGoalItem>,
        activeTimer: ActiveTimerSession?,
        endDate: LocalDate,
        todayLocal: LocalDate,
        zoneId: ZoneId,
        nowEpochMs: Long
    ): WallyStorySummary? = WallyDataStoryEngine.build(
        records = records,
        tags = tags,
        summaries = summaries,
        goalItems = goalItems,
        activeTimer = activeTimer,
        endDate = endDate,
        todayLocal = todayLocal,
        zoneId = zoneId,
        nowEpochMs = nowEpochMs
    )

    /**
     * Tính câu chuyện phân bổ 24 giờ/ngày trong 7 ngày gần nhất thành 4 tầng hiệu quả
     * và 5 nhóm hoạt động thực tế + đối chiếu tuần trước + câu chuyện Wally 4 bước.
     * Bản ghi chưa gắn mốc giờ (`REPORTED_UNTIMED`) luôn tách riêng, không đưa lên cột 24h.
     */
    fun computeWeeklyEfficiency(
        endDateLocal: String,
        records: List<LedgerRecord>,
        goals: List<Goal> = emptyList(),
        budgets: List<CategoryTimeBudget> = emptyList(),
        tags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null,
        weeklyInsights: WeeklyInsightsSummary? = null
    ): WeeklyEfficiencySummary {
        val endDate = LocalDate.parse(endDateLocal)
        val todayLocal = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val goalGroups = goals.groupBy { it.groupId }
        val budgetCategories = budgets.mapTo(HashSet()) { it.category }
        val tagById = tags.associateBy { it.id }

        val curSlices = (-6..0).map { offset ->
            computeDaySlices(
                date = endDate.plusDays(offset.toLong()),
                records = records,
                goalGroups = goalGroups,
                budgetCategories = budgetCategories,
                tagById = tagById,
                zoneId = zoneId,
                nowEpochMs = nowEpochMs,
                todayLocal = todayLocal,
                activeTimer = activeTimer
            )
        }
        val prevSlices = (-13..-7).map { offset ->
            computeDaySlices(
                date = endDate.plusDays(offset.toLong()),
                records = records,
                goalGroups = goalGroups,
                budgetCategories = budgetCategories,
                tagById = tagById,
                zoneId = zoneId,
                nowEpochMs = nowEpochMs,
                todayLocal = todayLocal,
                activeTimer = null
            )
        }

        val days = curSlices.map { it.efficiency }
        val activityDays = curSlices.mapIndexed { idx, slice ->
            val prev = prevSlices.getOrNull(idx)
            val prevRecorded = prev?.let { it.efficiency.calendarDaySeconds - it.efficiency.unknownSeconds } ?: 0L
            val recorded = slice.efficiency.calendarDaySeconds - slice.efficiency.unknownSeconds
            DayActivityBreakdown(
                dateLocal = slice.efficiency.dateLocal,
                calendarDaySeconds = slice.efficiency.calendarDaySeconds,
                recordedTimedSeconds = recorded,
                unrecordedSeconds = slice.efficiency.unknownSeconds,
                untimedSeconds = slice.efficiency.untimedSeconds,
                uncategorizedOrUntaggedSeconds = slice.uncategorizedOrUntaggedSeconds,
                bucketSeconds = slice.bucketSeconds,
                topItems = buildTopItems(slice.itemStats, limit = 8),
                prevWeekRecordedTimedSeconds = prevRecorded,
                prevWeekBucketSeconds = prev?.bucketSeconds ?: emptyMap()
            )
        }

        val weeklyItemsByBucket = mutableMapOf<ActivityBucket, MutableMap<ItemAccumKey, ItemAccumVal>>()
        val goalLinkedSecondsByBucket = mutableMapOf<ActivityBucket, Long>()
        for (slice in curSlices) {
            for ((k, v) in slice.itemStats) {
                val bMap = weeklyItemsByBucket.getOrPut(k.bucket) { mutableMapOf() }
                val acc = bMap.getOrPut(k) { ItemAccumVal() }
                acc.timedMs += v.timedMs
                acc.untimedSeconds += v.untimedSeconds
                if (k.matchesGoal) {
                    goalLinkedSecondsByBucket[k.bucket] = (goalLinkedSecondsByBucket[k.bucket] ?: 0L) + (v.timedMs / 1000L)
                }
            }
        }

        val activityTotals = ActivityBucket.entries.map { bucket ->
            val curSec = activityDays.sumOf { it.bucketSeconds[bucket] ?: 0L }
            val prevSec = prevSlices.sumOf { it.bucketSeconds[bucket] ?: 0L }
            ActivityBucketTotal(
                bucket = bucket,
                durationSeconds = curSec,
                prevWeekDurationSeconds = prevSec,
                deltaPercentVsPrevWeek = computePercentDelta(curSec, prevSec, minPrevThresholdSeconds = 600L),
                goalLinkedSeconds = goalLinkedSecondsByBucket[bucket] ?: 0L,
                topItems = buildTopItems(weeklyItemsByBucket[bucket].orEmpty(), limit = 5)
            )
        }

        val untaggedByCategory = mutableMapOf<TimeCategory, Long>()
        for (slice in curSlices) {
            for ((cat, secs) in slice.untaggedCategorySeconds) {
                untaggedByCategory[cat] = (untaggedByCategory[cat] ?: 0L) + secs
            }
        }

        val summaries = GoalRules.summaries(
            goals = goals,
            records = records,
            tagNameOf = { id -> tagById[id]?.name },
            today = endDate,
            nowEpochMs = nowEpochMs,
            zone = zoneId
        )
        val goalItems = if (summaries.isNotEmpty()) {
            summaries.mapNotNull { s ->
                val p = s.progress ?: return@mapNotNull null
                val tagCat = if (s.latest.targetType == GoalTargetType.TAG) tagById[s.latest.targetKey]?.category else null
                val colorHex = when (s.latest.targetType) {
                    GoalTargetType.TAG -> tagCat?.colorHex ?: 0xFF287BCBL
                    GoalTargetType.CATEGORY -> TimeCategory.fromKeyOrDefault(s.latest.targetKey).colorHex
                }
                val untaggedCatSecs = if (p.usedSeconds == 0L && tagCat != null) (untaggedByCategory[tagCat] ?: 0L) else 0L
                val hint = if (untaggedCatSecs > 0L && tagCat != null) {
                    "Có ${formatCompactDurationSeconds(untaggedCatSecs)} ${tagCat.displayName} chưa gắn Tag \"${s.name}\""
                } else {
                    null
                }
                val math = GoalDisplayMath.from(p.usedSeconds, p.targetSeconds)
                EfficiencyGoalItem(
                    id = s.groupId,
                    title = s.name,
                    progressText = "${math.usedLabel} / ${math.targetLabel}",
                    fraction = p.fraction,
                    accentHex = colorHex,
                    met = p.met,
                    hintVi = hint
                )
            }.take(4)
        } else {
            val budgetList = weeklyInsights?.budgetProgressList
                ?: computeBudgetProgress(budgets, records, zoneId, nowEpochMs, activeTimer)
            budgetList.take(4).map { b ->
                val combinedUsed = b.usedSeconds + b.untimedNoteSeconds
                val usedLabel = if (combinedUsed <= 0L) "0p" else formatCompactDurationSeconds(combinedUsed)
                EfficiencyGoalItem(
                    id = "budget_${b.category.key}",
                    title = b.category.displayName,
                    progressText = "$usedLabel / ${formatCompactDurationSeconds(b.targetSeconds)}",
                    fraction = b.combinedProgressFraction,
                    accentHex = b.category.colorHex,
                    met = combinedUsed >= b.targetSeconds
                )
            }
        }

        val totalGoal = days.sumOf { it.goalSeconds }
        val totalNeutral = days.sumOf { it.neutralSeconds }
        val totalWaste = days.sumOf { it.wasteSeconds }
        val totalUnknown = days.sumOf { it.unknownSeconds }
        val totalUntimed = days.sumOf { it.untimedSeconds }
        val totalTimed = activityDays.sumOf { it.recordedTimedSeconds }
        val prevWeekTotalTimed = prevSlices.sumOf { it.efficiency.calendarDaySeconds - it.efficiency.unknownSeconds }
        val weekOverWeekDelta = computePercentDelta(totalTimed, prevWeekTotalTimed, minPrevThresholdSeconds = 1800L)
        val totalUncategorized = activityDays.sumOf { it.uncategorizedOrUntaggedSeconds }

        val wallyStory = buildWallyDataStory(
            records = records,
            tags = tags,
            summaries = summaries,
            goalItems = goalItems,
            activeTimer = activeTimer,
            endDate = endDate,
            todayLocal = todayLocal,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs
        )

        val topUnmetSummary = summaries.firstOrNull { it.progress != null && !it.progress.met && it.latest.kind == GoalKind.MIN }
        val actionableInsight = when {
            wallyStory != null -> wallyStory.mainFinding
            topUnmetSummary != null && topUnmetSummary.progress != null -> {
                val math = GoalDisplayMath.from(topUnmetSummary.progress.usedSeconds, topUnmetSummary.progress.targetSeconds)
                "Mục tiêu \"${topUnmetSummary.name}\" đang ở ${math.usedLabel}/${math.targetLabel} (còn ${math.remainingLabel})."
            }
            else -> ""
        }

        return WeeklyEfficiencySummary(
            days = days,
            totalGoalSeconds = totalGoal,
            totalNeutralSeconds = totalNeutral,
            totalWasteSeconds = totalWaste,
            totalUnknownSeconds = totalUnknown,
            totalUntimedSeconds = totalUntimed,
            goalItems = goalItems,
            actionableInsight = actionableInsight,
            activityDays = activityDays,
            activityTotals = activityTotals,
            previousWeekTotalTimedSeconds = prevWeekTotalTimed,
            weekOverWeekDeltaPercent = weekOverWeekDelta,
            totalUncategorizedOrUntaggedSeconds = totalUncategorized,
            activeGoalsCount = summaries.size,
            wallyStory = wallyStory
        )
    }

    fun formatHoursMinutesFromSeconds(seconds: Long): String {
        val totalMinutes = max(0L, (seconds + 30L) / 60L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return "${hours}h ${mins.toString().padStart(2, '0')}"
    }

    fun formatCompactDurationSeconds(seconds: Long): String {
        if (seconds <= 0L) return "0p"
        val totalMinutes = (seconds + 30L) / 60L
        if (totalMinutes <= 0L) return "0p"
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}p"
            hours > 0L -> "${hours}h"
            else -> "${mins}p"
        }
    }
}
