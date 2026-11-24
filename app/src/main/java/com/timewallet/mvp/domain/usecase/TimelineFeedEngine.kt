package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * Bộ lọc chuyên biệt cho chế độ Dòng thời gian (Timeline).
 * Tách khỏi tầng UI để unit test trực tiếp logic phân tầng & lọc.
 */
enum class TimelineFeedFilter {
    ALL,
    ACTUAL_TIMED,
    UNTIMED,
    UPCOMING_OR_ACTIVE
}

/**
 * Ba chân trời thời gian bất biến lấy [nowEpochMs] làm mốc:
 * - UPCOMING: Dự kiến trong tương lai (sau [nowEpochMs] hôm nay hoặc các ngày sau).
 * - CURRENT: Đang diễn ra (`ACTIVE` / timer chạy) hoặc cần người dùng xác nhận/đối soát (`PENDING_CONFIRMATION` / `isNeedsReconciliation`).
 * - PAST: Hoạt động thực tế đã diễn ra (sắp theo ngày mới nhất -> cũ nhất, trong ngày mới nhất lên đầu).
 */
enum class TimelineHorizon {
    UPCOMING,
    CURRENT,
    PAST
}

enum class TimelineConnectorKind {
    CONTINUOUS,
    TRANSITION,
    UNTRACKED_GAP,
    FREE_SLOT,
    PARALLEL_OVERLAP
}

data class TimelineConnector(
    val kind: TimelineConnectorKind,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val minutes: Int,
    val labelVi: String,
    val isActionableGap: Boolean
)

data class TimelineGapSlot(
    val dateLocal: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val minutes: Int,
    val labelVi: String
)

data class TimelineFeedItem(
    val record: LedgerRecord,
    val horizon: TimelineHorizon,
    val resolvedLayer: TimeLayer,
    val confirmationState: ConfirmationState,
    val isRunningNow: Boolean,
    val needsUserAction: Boolean,
    val relativeHintVi: String? = null
)

data class TimelineUpcomingDayGroup(
    val dateLocal: String,
    val totalPlannedSeconds: Long,
    val timedItems: List<TimelineFeedItem>,
    val untimedItems: List<TimelineFeedItem>,
    val connectorsAfterTimedItem: Map<String, TimelineConnector>
)

data class TimelinePastDayGroup(
    val dateLocal: String,
    val totalRecordedSeconds: Long,
    val inDayRecordedSeconds: Long,
    val overnightCarryoverSeconds: Long,
    val untimedRecordedSeconds: Long,
    val timedItems: List<TimelineFeedItem>,
    val untimedItems: List<TimelineFeedItem>,
    val connectorsAfterTimedItem: Map<String, TimelineConnector>,
    val trailingGapToNow: TimelineGapSlot?
)

data class TimelineCurrentSection(
    val items: List<TimelineFeedItem>,
    val hasActiveTimer: Boolean,
    val pendingActionCount: Int
)

data class TimelineFeedSnapshot(
    val nowEpochMs: Long,
    val todayDateLocal: String,
    val nowTimeLabel: String,
    val upcomingDays: List<TimelineUpcomingDayGroup>,
    val upcomingTotalPlannedSeconds: Long,
    val upcomingCount: Int,
    val current: TimelineCurrentSection,
    val pastDays: List<TimelinePastDayGroup>,
    val todayRecordedTotalSeconds: Long,
    val todayInDayRecordedSeconds: Long,
    val todayOvernightCarryoverSeconds: Long,
    val todayPlannedSeconds: Long,
    val totalVisibleRecords: Int
)

/**
 * Bộ máy sắp xếp & liên kết Dòng thời gian (Domain pure logic):
 * 1. Neo tại thời điểm hiện tại (`nowEpochMs`): tách bạch Sắp diễn ra -> Hiện tại -> Đã diễn ra.
 * 2. Không trộn hoạt động dự kiến tương lai vào giữa Hôm nay và Hôm qua.
 * 3. `REPORTED_UNTIMED` giữ nguyên trạng thái "Chưa gắn giờ", không bịa mốc đồng hồ và không chèn vào trục khoảng cách giờ.
 * 4. Tính khoảng cách giữa các mục có mốc giờ liên tiếp (liền mạch, chưa ghi/trống >= 15p có thể bấm ghi/xếp, hoặc song song/trùng).
 */
object TimelineFeedEngine {

    private val HH_MM_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val MIN_ACTIONABLE_GAP_MINUTES = 15
    private const val CONTINUOUS_MAX_MINUTES = 4

    fun build(
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        todayStats: DayLedgerStats? = null,
        filter: TimelineFeedFilter = TimelineFeedFilter.ALL,
        todayDateLocal: String,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): TimelineFeedSnapshot {
        val nonCancelled = records.filterNot { it.isCancelled }
        val nowZdt = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val nowTimeLabel = HH_MM_FORMATTER.format(nowZdt)

        val todayRecordedTotal = JournalDayTotal.secondsForDate(
            dateLocal = todayDateLocal,
            allRecords = nonCancelled,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer
        )
        val todayOvernightCarryover = JournalDayTotal.overnightCarryoverSeconds(
            dateLocal = todayDateLocal,
            allRecords = nonCancelled,
            zoneId = zoneId,
            nowEpochMs = nowEpochMs
        )
        val todayInDayRecorded = (todayRecordedTotal - todayOvernightCarryover).coerceAtLeast(0L)
        val todayPlannedSeconds = todayStats?.plannedSeconds
            ?: computePlannedSecondsForDate(nonCancelled, todayDateLocal, nowEpochMs)

        val filtered = nonCancelled.filter { record ->
            matchesFilter(record, filter, nowEpochMs, todayDateLocal)
        }

        val upcomingRaw = mutableListOf<TimelineFeedItem>()
        val currentRaw = mutableListOf<TimelineFeedItem>()
        val pastRaw = mutableListOf<TimelineFeedItem>()

        for (record in filtered) {
            val resolvedLayer = record.resolveLayerAt(nowEpochMs, todayDateLocal)
            val confState = record.resolveConfirmationStateAt(nowEpochMs, todayDateLocal)
            val needsAction = record.isNeedsReconciliation ||
                record.requiresCompletionConfirmationAt(nowEpochMs) ||
                confState == ConfirmationState.PENDING_CONFIRMATION

            val startMs = record.startedAt
            val endMs = record.endedAt
            val isFutureDate = record.date > todayDateLocal
            val isPastDate = record.date < todayDateLocal
            val isPlannedInWindowNow = resolvedLayer == TimeLayer.PLANNED &&
                record.hasTimeBounds &&
                startMs != null &&
                endMs != null &&
                startMs <= nowEpochMs &&
                endMs > nowEpochMs
            val isActiveRunning = resolvedLayer == TimeLayer.ACTIVE && !needsAction

            when {
                // Cần đối soát / quá giờ chờ xác nhận / đang chạy thuộc CURRENT
                needsAction || isActiveRunning || isPlannedInWindowNow -> {
                    val hint = when {
                        isActiveRunning -> formatEndRemainingHint(endMs, nowEpochMs)
                        isPlannedInWindowNow -> "Đã tới giờ bắt đầu"
                        record.isNeedsReconciliation -> "Cần đối soát"
                        else -> "Chờ xác nhận"
                    }
                    currentRaw += TimelineFeedItem(
                        record = record,
                        horizon = TimelineHorizon.CURRENT,
                        resolvedLayer = resolvedLayer,
                        confirmationState = confState,
                        isRunningNow = isActiveRunning,
                        needsUserAction = needsAction || isPlannedInWindowNow,
                        relativeHintVi = hint
                    )
                }

                // Tương lai rõ ràng (ngày sau hôm nay hoặc dự kiến hôm nay có giờ bắt đầu sau hiện tại / chưa đặt giờ)
                resolvedLayer == TimeLayer.PLANNED &&
                    (isFutureDate || (!isPastDate && (startMs == null || startMs > nowEpochMs))) -> {
                    val hint = formatUpcomingRelativeHint(startMs, record.date, todayDateLocal, nowEpochMs)
                    upcomingRaw += TimelineFeedItem(
                        record = record,
                        horizon = TimelineHorizon.UPCOMING,
                        resolvedLayer = resolvedLayer,
                        confirmationState = confState,
                        isRunningNow = false,
                        needsUserAction = false,
                        relativeHintVi = hint
                    )
                }

                // Bản ghi PLANNED cũ bị bỏ lỡ ở ngày trước mà chưa có endMs -> đưa vào CURRENT để xử lý thay vì trộn Tương lai
                resolvedLayer == TimeLayer.PLANNED && isPastDate -> {
                    currentRaw += TimelineFeedItem(
                        record = record,
                        horizon = TimelineHorizon.CURRENT,
                        resolvedLayer = resolvedLayer,
                        confirmationState = confState,
                        isRunningNow = false,
                        needsUserAction = true,
                        relativeHintVi = "Quá ngày dự kiến"
                    )
                }

                // Đã diễn ra (MEASURED, REPORTED_TIMED, REPORTED_UNTIMED)
                else -> {
                    pastRaw += TimelineFeedItem(
                        record = record,
                        horizon = TimelineHorizon.PAST,
                        resolvedLayer = resolvedLayer,
                        confirmationState = confState,
                        isRunningNow = false,
                        needsUserAction = false,
                        relativeHintVi = null
                    )
                }
            }
        }

        // Sắp xếp CURRENT: đang diễn ra -> cần đối soát/xác nhận -> dự kiến vừa chạm giờ bắt đầu
        val currentSorted = currentRaw.sortedWith(
            compareBy<TimelineFeedItem>(
                {
                    when {
                        it.isRunningNow -> 0
                        it.needsUserAction && it.confirmationState == ConfirmationState.PENDING_CONFIRMATION -> 1
                        it.needsUserAction -> 2
                        else -> 3
                    }
                },
                { -(it.record.endedAt ?: it.record.startedAt ?: it.record.createdAt) }
            )
        )

        // Nhóm UPCOMING theo ngày tăng dần, trong ngày tăng dần theo giờ bắt đầu (sắp diễn ra nhất trước)
        val upcomingGroups = upcomingRaw
            .groupBy { it.record.date }
            .entries
            .sortedBy { it.key }
            .map { (dateLocal, dayItems) ->
                val timed = dayItems
                    .filter { it.record.hasTimeBounds }
                    .sortedWith(
                        compareBy<TimelineFeedItem>(
                            { it.record.startedAt ?: Long.MAX_VALUE },
                            { it.record.endedAt ?: Long.MAX_VALUE },
                            { it.record.createdAt }
                        )
                    )
                val untimed = dayItems
                    .filterNot { it.record.hasTimeBounds }
                    .sortedBy { it.record.createdAt }
                val connectors = buildUpcomingConnectors(timed, zoneId)
                TimelineUpcomingDayGroup(
                    dateLocal = dateLocal,
                    totalPlannedSeconds = dayItems.sumOf { it.record.durationSeconds },
                    timedItems = timed,
                    untimedItems = untimed,
                    connectorsAfterTimedItem = connectors
                )
            }

        // Nhóm PAST theo ngày giảm dần (Hôm nay -> Hôm qua -> trước đó)
        // Trong mỗi ngày: mới nhất lên đầu (`endedAt ?: startedAt ?: createdAt` giảm dần)
        val pastDayDates = pastRaw.map { it.record.date }.distinct().sortedDescending()
        val showTrailingGapToday = filter == TimelineFeedFilter.ALL || filter == TimelineFeedFilter.ACTUAL_TIMED
        val pastGroups = pastDayDates.map { dateLocal ->
            val dayItems = pastRaw.filter { it.record.date == dateLocal }
            val timedDesc = dayItems
                .filter { it.record.hasTimeBounds && it.resolvedLayer != TimeLayer.REPORTED_UNTIMED }
                .sortedWith(
                    compareByDescending<TimelineFeedItem> { it.record.endedAt ?: it.record.startedAt ?: it.record.createdAt }
                        .thenByDescending { it.record.startedAt ?: it.record.createdAt }
                        .thenByDescending { it.record.createdAt }
                )
            val untimedDesc = dayItems
                .filter { !it.record.hasTimeBounds || it.resolvedLayer == TimeLayer.REPORTED_UNTIMED }
                .sortedByDescending { it.record.createdAt }

            val dayTotal = JournalDayTotal.secondsForDate(
                dateLocal = dateLocal,
                allRecords = nonCancelled,
                zoneId = zoneId,
                nowEpochMs = nowEpochMs,
                activeTimer = activeTimer
            )
            val dayCarryover = JournalDayTotal.overnightCarryoverSeconds(
                dateLocal = dateLocal,
                allRecords = nonCancelled,
                zoneId = zoneId,
                nowEpochMs = nowEpochMs
            )
            val dayInDay = (dayTotal - dayCarryover).coerceAtLeast(0L)
            val dayUntimed = nonCancelled
                .filter {
                    it.date == dateLocal &&
                        !it.isNeedsReconciliation &&
                        it.resolveLayerAt(nowEpochMs, todayDateLocal) == TimeLayer.REPORTED_UNTIMED
                }
                .sumOf { it.durationSeconds }

            val trailingGap = if (
                showTrailingGapToday &&
                dateLocal == todayDateLocal &&
                activeTimer == null &&
                currentSorted.none { it.isRunningNow }
            ) {
                computeTrailingGapToNow(
                    dateLocal = dateLocal,
                    latestTimedToday = timedDesc.firstOrNull(),
                    nowEpochMs = nowEpochMs,
                    zoneId = zoneId
                )
            } else {
                null
            }

            TimelinePastDayGroup(
                dateLocal = dateLocal,
                totalRecordedSeconds = dayTotal,
                inDayRecordedSeconds = dayInDay,
                overnightCarryoverSeconds = dayCarryover,
                untimedRecordedSeconds = dayUntimed,
                timedItems = timedDesc,
                untimedItems = untimedDesc,
                connectorsAfterTimedItem = buildPastConnectors(timedDesc, zoneId),
                trailingGapToNow = trailingGap
            )
        }

        val showActiveTimer = activeTimer != null &&
            (filter == TimelineFeedFilter.ALL || filter == TimelineFeedFilter.UPCOMING_OR_ACTIVE)

        return TimelineFeedSnapshot(
            nowEpochMs = nowEpochMs,
            todayDateLocal = todayDateLocal,
            nowTimeLabel = nowTimeLabel,
            upcomingDays = upcomingGroups,
            upcomingTotalPlannedSeconds = upcomingRaw.sumOf { it.record.durationSeconds },
            upcomingCount = upcomingRaw.size,
            current = TimelineCurrentSection(
                items = currentSorted,
                hasActiveTimer = showActiveTimer,
                pendingActionCount = currentSorted.count { it.needsUserAction }
            ),
            pastDays = pastGroups,
            todayRecordedTotalSeconds = todayRecordedTotal,
            todayInDayRecordedSeconds = todayInDayRecorded,
            todayOvernightCarryoverSeconds = todayOvernightCarryover,
            todayPlannedSeconds = todayPlannedSeconds,
            totalVisibleRecords = upcomingRaw.size + currentSorted.size + pastRaw.size
        )
    }

    private fun matchesFilter(
        record: LedgerRecord,
        filter: TimelineFeedFilter,
        nowEpochMs: Long,
        todayDateLocal: String
    ): Boolean {
        val resolved = record.resolveLayerAt(nowEpochMs, todayDateLocal)
        return when (filter) {
            TimelineFeedFilter.ALL -> true
            TimelineFeedFilter.ACTUAL_TIMED ->
                !record.isNeedsReconciliation &&
                    (resolved == TimeLayer.MEASURED || resolved == TimeLayer.REPORTED_TIMED)
            TimelineFeedFilter.UNTIMED ->
                resolved == TimeLayer.REPORTED_UNTIMED
            TimelineFeedFilter.UPCOMING_OR_ACTIVE ->
                resolved == TimeLayer.ACTIVE ||
                    resolved == TimeLayer.PLANNED ||
                    record.isNeedsReconciliation ||
                    record.requiresCompletionConfirmationAt(nowEpochMs)
        }
    }

    private fun computePlannedSecondsForDate(
        nonCancelled: List<LedgerRecord>,
        dateLocal: String,
        nowEpochMs: Long
    ): Long = nonCancelled
        .filter { it.date == dateLocal && !it.isNeedsReconciliation }
        .sumOf { record ->
            when (record.resolveLayerAt(nowEpochMs, dateLocal)) {
                TimeLayer.PLANNED -> record.durationSeconds
                TimeLayer.ACTIVE -> {
                    val s = record.startedAt
                    val e = record.endedAt
                    if (s != null && e != null && e > s) {
                        ((e - max(s, nowEpochMs)).coerceAtLeast(0L)) / 1000L
                    } else {
                        0L
                    }
                }
                else -> 0L
            }
        }

    /**
     * Vì trong mỗi ngày PAST các mục có giờ được sắp mới nhất -> cũ nhất (DESC),
     * `laterItem = timedDesc[i]` diễn ra sau và `earlierItem = timedDesc[i + 1]` diễn ra trước.
     */
    private fun buildPastConnectors(
        timedDesc: List<TimelineFeedItem>,
        zoneId: ZoneId
    ): Map<String, TimelineConnector> {
        if (timedDesc.size < 2) return emptyMap()
        val result = LinkedHashMap<String, TimelineConnector>(timedDesc.size - 1)
        for (i in 0 until timedDesc.lastIndex) {
            val later = timedDesc[i]
            val earlier = timedDesc[i + 1]
            val connector = buildIntervalConnector(
                earlier = earlier.record,
                later = later.record,
                zoneId = zoneId,
                isFutureHorizon = false
            ) ?: continue
            result[later.record.id] = connector
        }
        return result
    }

    /**
     * Trong ngày UPCOMING các mục sắp tăng dần (ASC): `earlierItem = timedAsc[i]`, `laterItem = timedAsc[i + 1]`.
     */
    private fun buildUpcomingConnectors(
        timedAsc: List<TimelineFeedItem>,
        zoneId: ZoneId
    ): Map<String, TimelineConnector> {
        if (timedAsc.size < 2) return emptyMap()
        val result = LinkedHashMap<String, TimelineConnector>(timedAsc.size - 1)
        for (i in 0 until timedAsc.lastIndex) {
            val earlier = timedAsc[i]
            val later = timedAsc[i + 1]
            val connector = buildIntervalConnector(
                earlier = earlier.record,
                later = later.record,
                zoneId = zoneId,
                isFutureHorizon = true
            ) ?: continue
            result[earlier.record.id] = connector
        }
        return result
    }

    private fun buildIntervalConnector(
        earlier: LedgerRecord,
        later: LedgerRecord,
        zoneId: ZoneId,
        isFutureHorizon: Boolean
    ): TimelineConnector? {
        val earlierEnd = earlier.endedAt ?: return null
        val laterStart = later.startedAt ?: return null
        val diffMs = laterStart - earlierEnd
        if (diffMs < 0L) {
            val overlapMinutes = ((-diffMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
            return TimelineConnector(
                kind = TimelineConnectorKind.PARALLEL_OVERLAP,
                startEpochMs = laterStart,
                endEpochMs = earlierEnd,
                minutes = overlapMinutes,
                labelVi = "Song song · trùng ${formatMinutesCompact(overlapMinutes)}",
                isActionableGap = false
            )
        }
        val gapMinutes = ((diffMs + 30_000L) / 60_000L).toInt()
        val startHm = HH_MM_FORMATTER.format(Instant.ofEpochMilli(earlierEnd).atZone(zoneId))
        val endHm = HH_MM_FORMATTER.format(Instant.ofEpochMilli(laterStart).atZone(zoneId))
        return when {
            gapMinutes <= CONTINUOUS_MAX_MINUTES -> TimelineConnector(
                kind = TimelineConnectorKind.CONTINUOUS,
                startEpochMs = earlierEnd,
                endEpochMs = laterStart,
                minutes = gapMinutes,
                labelVi = "Liền mạch",
                isActionableGap = false
            )
            gapMinutes < MIN_ACTIONABLE_GAP_MINUTES -> TimelineConnector(
                kind = TimelineConnectorKind.TRANSITION,
                startEpochMs = earlierEnd,
                endEpochMs = laterStart,
                minutes = gapMinutes,
                labelVi = "Chuyển tiếp ${gapMinutes}p",
                isActionableGap = false
            )
            isFutureHorizon -> TimelineConnector(
                kind = TimelineConnectorKind.FREE_SLOT,
                startEpochMs = earlierEnd,
                endEpochMs = laterStart,
                minutes = gapMinutes,
                labelVi = "Trống ${formatMinutesCompact(gapMinutes)} ($startHm–$endHm)",
                isActionableGap = true
            )
            else -> TimelineConnector(
                kind = TimelineConnectorKind.UNTRACKED_GAP,
                startEpochMs = earlierEnd,
                endEpochMs = laterStart,
                minutes = gapMinutes,
                labelVi = "Chưa ghi ${formatMinutesCompact(gapMinutes)} ($startHm–$endHm)",
                isActionableGap = true
            )
        }
    }

    private fun computeTrailingGapToNow(
        dateLocal: String,
        latestTimedToday: TimelineFeedItem?,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): TimelineGapSlot? {
        val latestEnd = latestTimedToday?.record?.endedAt ?: return null
        if (latestEnd >= nowEpochMs) return null
        val diffMinutes = ((nowEpochMs - latestEnd) / 60_000L).toInt()
        if (diffMinutes < MIN_ACTIONABLE_GAP_MINUTES) return null
        val startHm = HH_MM_FORMATTER.format(Instant.ofEpochMilli(latestEnd).atZone(zoneId))
        val endHm = HH_MM_FORMATTER.format(Instant.ofEpochMilli(nowEpochMs).atZone(zoneId))
        return TimelineGapSlot(
            dateLocal = dateLocal,
            startEpochMs = latestEnd,
            endEpochMs = nowEpochMs,
            minutes = diffMinutes,
            labelVi = "Chưa ghi ${formatMinutesCompact(diffMinutes)} ($startHm–$endHm)"
        )
    }

    private fun formatUpcomingRelativeHint(
        startMs: Long?,
        recordDateLocal: String,
        todayDateLocal: String,
        nowEpochMs: Long
    ): String {
        if (recordDateLocal == todayDateLocal) {
            if (startMs == null) return "Dự kiến hôm nay"
            val diffMins = ((startMs - nowEpochMs) / 60_000L).toInt().coerceAtLeast(1)
            return "Còn ${formatMinutesCompact(diffMins)}"
        }
        val today = runCatching { LocalDate.parse(todayDateLocal) }.getOrNull()
        val target = runCatching { LocalDate.parse(recordDateLocal) }.getOrNull()
        if (today != null && target != null) {
            val daysAhead = java.time.temporal.ChronoUnit.DAYS.between(today, target).toInt()
            if (daysAhead == 1) return "Ngày mai"
            if (daysAhead in 2..7) return "Sau $daysAhead ngày"
        }
        return "Sắp tới"
    }

    private fun formatEndRemainingHint(endMs: Long?, nowEpochMs: Long): String {
        if (endMs == null) return "Đang diễn ra"
        val remainMins = ((endMs - nowEpochMs) / 60_000L).toInt()
        return if (remainMins > 0) "Còn ${formatMinutesCompact(remainMins)}" else "Vừa tới hạn"
    }

    private fun formatMinutesCompact(minutes: Int): String {
        val safe = max(1, minutes)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}p"
            hours > 0 -> "${hours}h"
            else -> "${mins}p"
        }
    }
}
