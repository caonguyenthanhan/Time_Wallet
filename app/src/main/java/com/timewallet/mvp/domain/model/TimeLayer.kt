package com.timewallet.mvp.domain.model

/**
 * 5 trạng thái / lớp bản ghi trong sổ cái thời gian (MVP Refinement 01):
 * - MEASURED: Đã đo bằng đồng hồ thực tế (có startedAt, endedAt <= now).
 * - REPORTED_TIMED: Tự khai báo có khoảng giờ cụ thể đã qua (startedAt, endedAt <= now).
 * - REPORTED_UNTIMED: Tự khai báo chỉ có thời lượng, chưa gắn mốc giờ trên trục 24h.
 * - ACTIVE: Hoạt động đang diễn ra tại thời điểm hiện tại (startedAt <= now < endedAt);
 *   chỉ phần thời gian đã trôi qua (startedAt -> now) mới được tính vào thực tế đã dùng.
 * - PLANNED: Hoạt động dự kiến trong tương lai (startedAt > now hoặc lưu dạng kế hoạch);
 *   KHÔNG tính vào thời gian đã sử dụng cho tới khi được xác nhận hoàn thành.
 */
enum class TimeLayer(
    val labelVi: String,
    val badgeShortVi: String,
    val countsAsUsedTime: Boolean,
    val hasExactPlacement: Boolean
) {
    MEASURED(
        labelVi = "Thực tế · Đã đo đồng hồ",
        badgeShortVi = "Đã đo",
        countsAsUsedTime = true,
        hasExactPlacement = true
    ),
    REPORTED_TIMED(
        labelVi = "Thực tế · Có mốc giờ",
        badgeShortVi = "Có giờ",
        countsAsUsedTime = true,
        hasExactPlacement = true
    ),
    REPORTED_UNTIMED(
        labelVi = "Thực tế · Chưa gắn giờ",
        badgeShortVi = "Chưa gắn giờ",
        countsAsUsedTime = true,
        hasExactPlacement = false
    ),
    ACTIVE(
        labelVi = "Đang diễn ra",
        badgeShortVi = "Đang diễn ra",
        countsAsUsedTime = true,
        hasExactPlacement = true
    ),
    PLANNED(
        labelVi = "Dự kiến",
        badgeShortVi = "Dự kiến",
        countsAsUsedTime = false,
        hasExactPlacement = false
    );

    companion object {
        /**
         * Đọc an toàn từ chuỗi lưu trong DB, bảo toàn dữ liệu cũ ("REPORTED_UNPLACED" -> REPORTED_UNTIMED).
         */
        fun fromStorageString(raw: String?, startedAt: Long?, endedAt: Long?): TimeLayer {
            return when (raw?.trim()?.uppercase()) {
                "MEASURED" -> MEASURED
                "REPORTED_TIMED" -> REPORTED_TIMED
                "REPORTED_UNTIMED", "REPORTED_UNPLACED" -> REPORTED_UNTIMED
                "ACTIVE" -> ACTIVE
                "PLANNED" -> PLANNED
                else -> if (startedAt != null && endedAt != null) REPORTED_TIMED else REPORTED_UNTIMED
            }
        }
    }
}
