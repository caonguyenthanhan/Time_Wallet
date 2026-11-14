package com.timewallet.mvp.domain.usecase

/**
 * WALLY v1.0.6 (R01): một phiên Đồng hồ có thể chạy hơn 24 giờ (quên bấm dừng, máy tắt qua đêm...). Sổ cái chỉ nhận mỗi bản ghi
 * tối đa 24 giờ, nên phiên được cắt thành các đoạn liên tiếp dài tối đa 24 giờ theo THỜI GIAN THỰC (epoch), không theo ngày lịch:
 * như vậy ngày đổi giờ mùa hè (23h/25h) không bao giờ tạo ra đoạn > 24h. Tổng các đoạn luôn bằng đúng cả phiên, các đoạn
 * sát nhau (không chồng lấn, không hở). Phiên ≤ 24h giữ nguyên MỘT bản ghi như v1.0.5.
 */
object TimerSegments {
    const val MAX_SEGMENT_MS = 86_400_000L

    data class Segment(val index: Int, val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    /** Khóa chống ghi trùng của đoạn [index]: đoạn 0 trùng khóa v1.0.5 (`timer_<start>`) nên không ghi trùng bản ghi cũ. */
    fun idempotencyKey(startedAtEpochMs: Long, index: Int): String =
        if (index == 0) "timer_$startedAtEpochMs" else "timer_${startedAtEpochMs}_$index"

    fun split(startMs: Long, endMs: Long): List<Segment> {
        if (endMs <= startMs) return emptyList()
        val out = ArrayList<Segment>()
        var cursor = startMs
        var i = 0
        while (cursor < endMs) {
            val next = minOf(endMs, cursor + MAX_SEGMENT_MS)
            out += Segment(i++, cursor, next)
            cursor = next
        }
        return out
    }
}
