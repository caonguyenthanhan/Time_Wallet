package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeLayer

/**
 * WALLY: tổng thời lượng của một ngày trong Nhật ký = các bản ghi ĐÃ dùng (đã ghi nhận), không gồm Dự kiến và phần
 * chưa tới của bản ghi đang diễn ra. Cộng theo thời lượng khai báo, kể cả bản ghi chưa gắn giờ (đây là tổng theo ngày,
 * không phải trục giờ). Bản ghi có mốc giờ chồng nhau đã bị chặn từ lúc lưu nên không cần khử trùng ở đây.
 */
object JournalDayTotal {
    fun seconds(dayRecords: List<LedgerRecord>, nowEpochMs: Long): Long = dayRecords.sumOf { r ->
        when (r.resolveLayerAt(nowEpochMs)) {
            TimeLayer.MEASURED, TimeLayer.REPORTED_TIMED, TimeLayer.REPORTED_UNTIMED -> r.durationSeconds
            TimeLayer.ACTIVE, TimeLayer.PLANNED -> 0L
        }
    }
}
