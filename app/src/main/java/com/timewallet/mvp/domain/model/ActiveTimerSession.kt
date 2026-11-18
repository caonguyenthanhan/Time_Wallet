package com.timewallet.mvp.domain.model

/**
 * Phiên đồng hồ đang chạy nền.
 * Sử dụng kết hợp:
 * - Epoch timestamp (`startedAtEpochMs`)
 * - Monotonic clock (`startedElapsedRealtimeMs`)
 * - Định danh lần khởi động thiết bị (`startedBootCount` từ `Settings.Global.BOOT_COUNT`)
 *
 * Bảo đảm (Patch v1.0.1 — F3):
 * 1. Cùng một chu kỳ boot (`startedBootCount == currentBootCount` và `nowElapsedRealtimeMs >= startedElapsedRealtimeMs`):
 *    Ưu tiên đối chiếu với monotonic clock để phát hiện và chống chỉnh giờ hệ thống (`clockSkewDetected`).
 * 2. Sau khi thiết bị reboot (`startedBootCount != currentBootCount` hoặc `nowElapsedRealtimeMs < startedElapsedRealtimeMs`):
 *    Bỏ qua hoàn toàn `startedElapsedRealtimeMs` (kể cả khi uptime mới lớn hơn uptime lúc start),
 *    fallback an toàn sang `wallDeltaMs = nowEpochMs - startedAtEpochMs` để không bao giờ bị tính thiếu giờ (undercount).
 * 3. Phiên cũ nâng cấp từ bản chưa có `startedBootCount` (`startedBootCount == null` nhưng `currentBootCount != null`):
 *    Nếu đồng hồ tường tiến bình thường (`wallDeltaMs >= 0`), dùng `wallDeltaMs` để tránh rủi ro undercount sau reboot;
 *    nếu đồng hồ tường bị lùi về trước lúc bắt đầu (`wallDeltaMs < 0`), gắn cờ `clockSkewDetected = true` và fallback sang monotonic delta (nếu hợp lệ).
 */
data class ActiveTimerSession(
    val title: String,
    val categoryKey: String,
    val startedAtEpochMs: Long,
    val startedElapsedRealtimeMs: Long?,
    val timezone: String,
    val targetDurationSeconds: Long? = null,
    val startedBootCount: Int? = null,
    /** v1.1.0: Tag chọn khi bắt đầu phiên đo (null = không gắn). */
    val tagId: String? = null,
    /** v2.0: Liên kết tùy chọn tới Task hoặc Habit đang tập trung. */
    val taskId: String? = null,
    val habitId: String? = null
) {
    /**
     * Tính số giây đã trôi qua, phân biệt same-boot với reboot, và kiểm tra lệch đồng hồ hệ thống.
     */
    fun computeElapsedSeconds(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long?,
        currentBootCount: Int? = null
    ): TimerElapsedSnapshot {
        val wallDeltaMs = nowEpochMs - startedAtEpochMs

        val rawMonotonicDeltaMs = if (
            startedElapsedRealtimeMs != null &&
            nowElapsedRealtimeMs != null &&
            nowElapsedRealtimeMs >= startedElapsedRealtimeMs
        ) {
            nowElapsedRealtimeMs - startedElapsedRealtimeMs
        } else {
            null
        }

        val rebootDetectedByBootId = startedBootCount != null &&
            currentBootCount != null &&
            startedBootCount != currentBootCount

        val rebootDetectedByMonotonicReset = startedElapsedRealtimeMs != null &&
            nowElapsedRealtimeMs != null &&
            nowElapsedRealtimeMs < startedElapsedRealtimeMs

        val isLegacyMissingBootIdOnDevice = startedBootCount == null && currentBootCount != null

        val trustedMonotonicDeltaMs: Long? = when {
            rebootDetectedByBootId || rebootDetectedByMonotonicReset -> null
            isLegacyMissingBootIdOnDevice -> {
                // Phiên cũ nâng cấp lên chưa có bootId: chỉ dùng monotonic khi đồng hồ tường bị chỉnh lùi âm (< 0)
                if (wallDeltaMs < 0L) rawMonotonicDeltaMs else null
            }
            else -> rawMonotonicDeltaMs
        }

        val clockSkewDetected = wallDeltaMs < 0L ||
            (trustedMonotonicDeltaMs != null && kotlin.math.abs(wallDeltaMs - trustedMonotonicDeltaMs) > 120_000L)

        val effectiveDeltaMs = when {
            clockSkewDetected && trustedMonotonicDeltaMs != null -> trustedMonotonicDeltaMs
            else -> wallDeltaMs.coerceAtLeast(0L)
        }

        return TimerElapsedSnapshot(
            elapsedSeconds = (effectiveDeltaMs / 1000L).coerceAtLeast(0L),
            effectiveEndEpochMs = startedAtEpochMs + effectiveDeltaMs,
            clockSkewDetected = clockSkewDetected,
            rebootFallbackUsed = rebootDetectedByBootId || rebootDetectedByMonotonicReset
        )
    }
}

data class TimerElapsedSnapshot(
    val elapsedSeconds: Long,
    val effectiveEndEpochMs: Long,
    val clockSkewDetected: Boolean,
    val rebootFallbackUsed: Boolean = false
)
