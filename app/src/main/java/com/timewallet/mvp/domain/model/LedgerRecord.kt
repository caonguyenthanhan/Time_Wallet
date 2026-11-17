package com.timewallet.mvp.domain.model

import kotlin.math.max
import kotlin.math.min

/**
 * Trạng thái xác nhận nghiệp vụ của bản ghi (bổ sung cho TimeLayer).
 * - PLANNED: Hoạt động được lên kế hoạch, chưa bắt đầu.
 * - DOING: Hoạt động đang diễn ra.
 * - PENDING_CONFIRMATION: Đã đến/qua thời điểm kết thúc dự kiến, chờ người dùng xác nhận (KHÔNG tự chuyển sang COMPLETED).
 * - COMPLETED: Người dùng xác nhận hoàn thành thực tế.
 * - ADJUSTED: Người dùng điều chỉnh thời gian hoặc kết quả thực tế (vẫn giữ trạng thái hoàn thành thực tế).
 * - INTERRUPTED: Hoạt động bị gián đoạn hoặc kết thúc sớm (giữ phần thực tế đã diễn ra).
 * - CANCELLED: Hoạt động dự kiến không diễn ra (0s thực tế, không chiếm overlap, lưu lịch sử cho Coach).
 */
enum class ConfirmationState(
    val labelVi: String,
    val badgeShortVi: String,
    val isConfirmedFinalActual: Boolean
) {
    PLANNED("Dự kiến · Chưa bắt đầu", "Dự kiến", false),
    DOING("Đang diễn ra", "Đang làm", false),
    PENDING_CONFIRMATION("Đã tới giờ kết thúc · Chờ xác nhận", "Chờ xác nhận", false),
    COMPLETED("Đã xác nhận hoàn thành", "Hoàn thành", true),
    ADJUSTED("Đã điều chỉnh kết quả thực tế", "Đã điều chỉnh", true),
    INTERRUPTED("Bị gián đoạn / Kết thúc sớm", "Gián đoạn", true),
    CANCELLED("Đã hủy · Không diễn ra", "Đã hủy", false);

    companion object {
        fun fromStorageOrNull(raw: String?): ConfirmationState? =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() }
    }
}

/**
 * Kết quả phân giải chồng lấn / hoạt động xen giữa (Activity Break).
 */
enum class OverlapResolution(
    val labelVi: String,
    val isUnresolvedReconciliation: Boolean
) {
    NONE("Bình thường", false),
    BREAK_CONFIRMED("Khoảng nghỉ xen giữa", false),
    PARALLEL_CONFIRMED("Diễn ra song song", false),
    BREAK_REJECTED("Không phải khoảng nghỉ · Cần làm rõ", true),
    PENDING("Chưa xác nhận · Cần đối soát", true);

    companion object {
        fun fromStorage(raw: String?): OverlapResolution =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: NONE
    }
}

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
    val status: String,   // "CONFIRMED" | "ACTIVE" | "PLANNED" | "CANCELLED"
    val idempotencyKey: String,
    /** v1.1.0: Tag cá nhân của hoạt động (null = chưa gắn Tag, gồm mọi bản ghi cũ). Không bao giờ được suy diễn lại từ tiêu đề. */
    val tagId: String? = null,
    val confirmationStateRaw: String? = null,
    val plannedStartedAt: Long? = null,
    val plannedEndedAt: Long? = null,
    val plannedDurationSeconds: Long? = null,
    val overlapResolutionRaw: String? = null,
    val relationGroupId: String? = null,
    val overlapWithTitle: String? = null
) {
    val overlapResolution: OverlapResolution
        get() = OverlapResolution.fromStorage(overlapResolutionRaw)

    val isNeedsReconciliation: Boolean
        get() = overlapResolution.isUnresolvedReconciliation

    val isCancelled: Boolean
        get() = ConfirmationState.fromStorageOrNull(confirmationStateRaw) == ConfirmationState.CANCELLED ||
            status.equals("CANCELLED", ignoreCase = true)

    fun resolveConfirmationStateAt(nowEpochMs: Long, todayDateLocal: String? = null): ConfirmationState {
        if (isCancelled) return ConfirmationState.CANCELLED
        val explicit = ConfirmationState.fromStorageOrNull(confirmationStateRaw)
        if (explicit == ConfirmationState.COMPLETED ||
            explicit == ConfirmationState.ADJUSTED ||
            explicit == ConfirmationState.INTERRUPTED
        ) {
            return explicit
        }
        val resolvedLayer = resolveLayerAt(nowEpochMs, todayDateLocal)
        return when (resolvedLayer) {
            TimeLayer.PLANNED -> {
                if (endedAt != null && nowEpochMs >= endedAt && !isNeedsReconciliation) {
                    ConfirmationState.PENDING_CONFIRMATION
                } else {
                    ConfirmationState.PLANNED
                }
            }
            TimeLayer.ACTIVE -> {
                if (endedAt != null && nowEpochMs >= endedAt && !isNeedsReconciliation) {
                    ConfirmationState.PENDING_CONFIRMATION
                } else {
                    ConfirmationState.DOING
                }
            }
            TimeLayer.MEASURED, TimeLayer.REPORTED_TIMED, TimeLayer.REPORTED_UNTIMED ->
                explicit ?: ConfirmationState.COMPLETED
        }
    }
    val durationMinutes: Int
        get() = ((durationSeconds + 30L) / 60L).toInt().coerceAtLeast(1)

    val hasTimeBounds: Boolean
        get() = startedAt != null && endedAt != null && endedAt > startedAt

    /**
     * Kiểm tra xem bản ghi ACTIVE tự khai báo đã qua mốc giờ kết thúc dự kiến (nowEpochMs >= endedAt)
     * nhưng vẫn chưa được người dùng xác nhận kết thúc thực tế hay chưa.
     */
    fun requiresCompletionConfirmationAt(nowEpochMs: Long): Boolean {
        if (isCancelled || isNeedsReconciliation) return false
        val explicit = ConfirmationState.fromStorageOrNull(confirmationStateRaw)
        if (explicit == ConfirmationState.COMPLETED ||
            explicit == ConfirmationState.ADJUSTED ||
            explicit == ConfirmationState.INTERRUPTED
        ) {
            return false
        }
        return (layer == TimeLayer.ACTIVE || status == "ACTIVE" ||
            ((layer == TimeLayer.PLANNED || status == "PLANNED") && startedAt != null && endedAt != null)) &&
            endedAt != null && nowEpochMs >= endedAt
    }

    /**
     * Mốc thời gian thực tế tối đa được phép tính cho bản ghi có mốc giờ tại thời điểm [nowEpochMs]:
     * - Với bản ghi ACTIVE tự khai báo: khi qua giờ kết thúc dự kiến (nowEpochMs >= endedAt) mà chưa có
     *   xác nhận hoàn thành thực tế, KHÔNG tự động cộng đủ đến endedAt; chỉ giữ phần đã xác nhận tại
     *   thời điểm khai báo (userConfirmedAt).
     */
    fun confirmedCutoffEpochMsAt(nowEpochMs: Long): Long {
        if (isCancelled || isNeedsReconciliation) return startedAt ?: nowEpochMs
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
     * - Bản ghi đã hủy (CANCELLED) luôn có lớp PLANNED (0s thực tế).
     * - Nếu bản ghi là PLANNED hoặc bắt đầu ở tương lai (startedAt > nowEpochMs) -> PLANNED.
     * - Nếu bản ghi là ACTIVE tự khai báo (layer == ACTIVE hoặc status == "ACTIVE"):
     *   KHÔNG mặc nhiên chuyển thành hoàn thành (REPORTED_TIMED) khi qua giờ (nowEpochMs >= endedAt);
     *   vẫn giữ trạng thái ACTIVE chờ người dùng xác nhận kết thúc thực tế.
     * - Nếu đã kết thúc và đã xác nhận (endedAt <= nowEpochMs) -> MEASURED hoặc REPORTED_TIMED.
     * - Nếu không có mốc giờ -> REPORTED_UNTIMED (trừ khi thuộc ngày tương lai thì là PLANNED).
     */
    fun resolveLayerAt(nowEpochMs: Long, todayDateLocal: String? = null): TimeLayer {
        if (isCancelled) {
            return TimeLayer.PLANNED
        }
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
     * Bản ghi đã hủy (CANCELLED) hoặc đang chờ đối soát chồng lấn (PENDING / BREAK_REJECTED) luôn trả 0L.
     */
    fun actualElapsedSecondsAt(nowEpochMs: Long, todayDateLocal: String? = null): Long {
        if (isCancelled || isNeedsReconciliation) return 0L
        val resolved = resolveLayerAt(nowEpochMs, todayDateLocal)
        if (resolved == TimeLayer.PLANNED) return 0L
        if (startedAt != null && endedAt != null) {
            val effectiveEnd = confirmedCutoffEpochMsAt(nowEpochMs)
            return max(0L, (effectiveEnd - startedAt) / 1000L)
        }
        return durationSeconds
    }

    val isTimedActualOrActive: Boolean
        get() = !isCancelled && !isNeedsReconciliation &&
            layer != TimeLayer.PLANNED && status != "PLANNED" &&
            layer != TimeLayer.REPORTED_UNTIMED &&
            startedAt != null && endedAt != null
}
