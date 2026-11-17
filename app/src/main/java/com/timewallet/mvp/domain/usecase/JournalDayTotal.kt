package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tổng thời lượng thực tế đã dùng trong Nhật ký:
 * - Không gồm PLANNED, CANCELLED, bản ghi chờ đối soát chồng lấn hoặc phần chưa tới/chưa chốt của hoạt động dự kiến.
 * - Khử trùng khoảng thời gian song song (`PARALLEL_CONFIRMED`) bằng hợp khoảng `[startedAt, endedAt]`.
 * - [secondsForDate] dùng chung bộ tính theo ngày lịch với Tổng quan và Phân tích (kể cả phần cắt qua nửa đêm `UC-15`).
 */
object JournalDayTotal {
    fun seconds(dayRecords: List<LedgerRecord>, nowEpochMs: Long): Long {
        val timedIntervals = mutableListOf<Pair<Long, Long>>()
        var untimedSeconds = 0L
        var fallbackTimedSeconds = 0L

        for (r in dayRecords) {
            if (r.isCancelled || r.isNeedsReconciliation) continue
            when (r.resolveLayerAt(nowEpochMs)) {
                TimeLayer.MEASURED, TimeLayer.REPORTED_TIMED -> {
                    val s = r.startedAt
                    val e = r.endedAt
                    if (s != null && e != null && e > s) {
                        timedIntervals += s to e
                    } else {
                        fallbackTimedSeconds += r.durationSeconds
                    }
                }
                TimeLayer.REPORTED_UNTIMED -> untimedSeconds += r.durationSeconds
                TimeLayer.ACTIVE, TimeLayer.PLANNED -> Unit
            }
        }
        return unionSeconds(timedIntervals) + fallbackTimedSeconds + untimedSeconds
    }

    /** Tổng đã ghi nhận trên đúng ngày lịch [dateLocal], đồng bộ hoàn toàn với Tổng quan và Phân tích (gồm ca qua đêm và DOING đã trôi qua). */
    fun secondsForDate(
        dateLocal: String,
        allRecords: List<LedgerRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null
    ): Long {
        val stats = LedgerAnalyticsCalculator.computeDayStats(dateLocal, allRecords, zoneId, nowEpochMs, activeTimer)
        return stats.timedSeconds + stats.untimedSeconds
    }

    /** Phần giây trên [dateLocal] đến từ bản ghi có ngày bắt đầu sớm hơn [dateLocal] (ví dụ ngủ từ 20:00 hôm qua sang 07:00 hôm nay). */
    fun overnightCarryoverSeconds(
        dateLocal: String,
        allRecords: List<LedgerRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): Long {
        val target = runCatching { LocalDate.parse(dateLocal) }.getOrNull() ?: return 0L
        val priorTimed = allRecords.filter { r ->
            r.startedAt != null && r.endedAt != null && r.date < dateLocal
        }
        if (priorTimed.isEmpty()) return 0L
        return LedgerAnalyticsCalculator.computeDayStats(target.toString(), priorTimed, zoneId, nowEpochMs).timedSeconds
    }

    private fun unionSeconds(intervals: List<Pair<Long, Long>>): Long {
        if (intervals.isEmpty()) return 0L
        val sorted = intervals.sortedBy { it.first }
        var curStart = sorted[0].first
        var curEnd = sorted[0].second
        var totalMs = 0L
        for (i in 1 until sorted.size) {
            val (s, e) = sorted[i]
            if (s <= curEnd) {
                if (e > curEnd) curEnd = e
            } else {
                totalMs += (curEnd - curStart)
                curStart = s
                curEnd = e
            }
        }
        totalMs += (curEnd - curStart)
        return (totalMs / 1000L).coerceAtLeast(0L)
    }
}
