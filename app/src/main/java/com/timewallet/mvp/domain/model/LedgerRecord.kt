package com.timewallet.mvp.domain.model

import kotlin.math.max
import kotlin.math.min

/**
 * Bản ghi sổ cái thời gian (Time Ledger Record).
 * Bảo toàn tương thích dữ liệu cũ và phân biệt rõ:
 * MEASURED, REPORTED_TIMED, REPORTED_UNTIMED, ACTIVE, PLANNED.
 */
data class LedgerRecord(
    val id: String,
    val source: String, // "timer" | "chat" | "manual" | "demo"
    val layer: TimeLayer,
    val createdAt: Long,
    val userConfirmedAt: Long,
    val category: TimeCategory,
    val title: String,
    val date: String, // YYYY-MM-DD local date in record's timezone
    val durationSeconds: Long,
    val startedAt: Long?, // epoch millis (nullable for REPORTED_UNTIMED)
    val endedAt: Long?,   // epoch millis (nullable for REPORTED_UNTIMED)
    val timezone: String,
    val status: String,   // "CONFIRMED" | "ACTIVE" | "PLANNED"
    val idempotencyKey: String
) {
    val durationMinutes: Int
        get() = ((durationSeconds + 30L) / 60L).toInt().coerceAtLeast(1)

    val hasTimeBounds: Boolean
        get() = startedAt != null && endedAt != null && endedAt > startedAt

    /**
     * Kiểm tra xem bản ghi ACTIVE tự khai báo đã qua mốc giờ kết thúc dự kiến (nowEpochMs >= endedAt)
     * nhưng vẫn chưa được người dùng xác nhận kết thúc thực tế hay chưa.
     */
    fun requiresCompletionConfirmationAt(nowEpochMs: Long): Boolean {
        return (layer == TimeLayer.ACTIVE || status == "ACTIVE") &&
            endedAt != null && nowEpochMs >= endedAt
    }

    /**
     * Mốc thời gian thực tế tối đa được phép tính cho bản ghi có mốc giờ tại thời điểm [nowEpochMs]:
     * - Với bản ghi ACTIVE tự khai báo: khi qua giờ kết thúc dự kiến (nowEpochMs >= endedAt) mà chưa có
     *   xác nhận hoàn thành thực tế, KHÔNG tự động cộng đủ đến endedAt; chỉ giữ phần đã xác nhận tại
     *   thời điểm khai báo (userConfirmedAt).
     */
    fun confirmedCutoffEpochMsAt(nowEpochMs: Long): Long {
        val start = startedAt ?: return nowEpochMs
        val end = endedAt ?: return nowEpochMs
        if (layer == TimeLayer.ACTIVE || status == "ACTIVE") {
            if (nowEpochMs >= end) {
                return if (userConfirmedAt in (start + 1) until end) {
                    userConfirmedAt
                } else {
                    start
                }
            }
            return min(end, nowEpochMs)
        }
        return min(end, nowEpochMs)
    }

    /**
     * Xác định trạng thái thực tế của bản ghi tại thời điểm [nowEpochMs]:
     * - Nếu bản ghi là PLANNED hoặc bắt đầu ở tương lai (startedAt > nowEpochMs) -> PLANNED.
     * - Nếu bản ghi là ACTIVE tự khai báo (layer == ACTIVE hoặc status == "ACTIVE"):
     *   KHÔNG mặc nhiên chuyển thành hoàn thành (REPORTED_TIMED) khi qua giờ (nowEpochMs >= endedAt);
     *   vẫn giữ trạng thái ACTIVE chờ người dùng xác nhận kết thúc thực tế.
     * - Nếu đã kết thúc và đã xác nhận (endedAt <= nowEpochMs) -> MEASURED hoặc REPORTED_TIMED.
     * - Nếu không có mốc giờ -> REPORTED_UNTIMED (trừ khi thuộc ngày tương lai thì là PLANNED).
     */
    fun resolveLayerAt(nowEpochMs: Long, todayDateLocal: String? = null): TimeLayer {
        if (layer == TimeLayer.PLANNED || status == "PLANNED") {
            return TimeLayer.PLANNED
        }
        if (todayDateLocal != null && date > todayDateLocal) {
            return TimeLayer.PLANNED
        }
        if (layer == TimeLayer.ACTIVE || status == "ACTIVE") {
            return if (startedAt != null && startedAt > nowEpochMs) {
                TimeLayer.PLANNED
            } else {
                TimeLayer.ACTIVE
            }
        }
        if (startedAt != null && endedAt != null) {
            return when {
                startedAt > nowEpochMs -> TimeLayer.PLANNED
                endedAt > nowEpochMs -> TimeLayer.ACTIVE
                source == "timer" || layer == TimeLayer.MEASURED -> TimeLayer.MEASURED
                else -> TimeLayer.REPORTED_TIMED
            }
        }
        return TimeLayer.REPORTED_UNTIMED
    }

    /**
     * Số giây thực tế đã diễn ra và được phép ghi nhận tính tới [nowEpochMs].
     */
    fun actualElapsedSecondsAt(nowEpochMs: Long, todayDateLocal: String? = null): Long {
        val resolved = resolveLayerAt(nowEpochMs, todayDateLocal)
        if (resolved == TimeLayer.PLANNED) return 0L
        if (startedAt != null && endedAt != null) {
            val effectiveEnd = confirmedCutoffEpochMsAt(nowEpochMs)
            return max(0L, (effectiveEnd - startedAt) / 1000L)
        }
        return durationSeconds
    }

    val isTimedActualOrActive: Boolean
        get() = layer != TimeLayer.PLANNED && layer != TimeLayer.REPORTED_UNTIMED &&
            startedAt != null && endedAt != null
}
