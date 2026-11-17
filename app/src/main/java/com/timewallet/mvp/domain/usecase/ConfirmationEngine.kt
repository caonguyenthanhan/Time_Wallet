package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

sealed class ConfirmationActionResult {
    data class Applied(
        val updatedRecords: List<LedgerRecord>,
        val messageVi: String
    ) : ConfirmationActionResult()

    data class Overlap(
        val candidate: LedgerRecord,
        val conflicts: List<LedgerRecord>
    ) : ConfirmationActionResult()

    data class Invalid(val messageVi: String) : ConfirmationActionResult()
}

/**
 * Máy trạng thái xác nhận nghiệp vụ:
 * PLANNED / DOING / PENDING_CONFIRMATION -> COMPLETED / ADJUSTED / INTERRUPTED / DOING (tiếp tục) / CANCELLED.
 * Không tự động chuyển PLANNED hoặc DOING thành COMPLETED khi đồng hồ đi qua giờ kết thúc.
 */
object ConfirmationEngine {

    private fun preservedPlanStart(r: LedgerRecord): Long? = r.plannedStartedAt ?: r.startedAt
    private fun preservedPlanEnd(r: LedgerRecord): Long? = r.plannedEndedAt ?: r.endedAt
    private fun preservedPlanDuration(r: LedgerRecord): Long? = r.plannedDurationSeconds ?: r.durationSeconds

    /** Bắt đầu thực hiện một kế hoạch (PLANNED -> DOING) khi đã tới hoặc gần giờ bắt đầu. */
    fun startDoingNow(
        record: LedgerRecord,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): ConfirmationActionResult {
        if (record.isCancelled) return ConfirmationActionResult.Invalid("Hoạt động đã bị hủy.")
        val startMs = record.startedAt ?: nowEpochMs
        val endMs = record.endedAt ?: (nowEpochMs + record.durationSeconds * 1000L)
        if (endMs <= nowEpochMs) {
            return ConfirmationActionResult.Invalid("Giờ kết thúc dự kiến đã qua; hãy xác nhận hoàn thành, điều chỉnh hoặc tiếp tục kéo dài.")
        }
        val effectiveStart = if (startMs > nowEpochMs) nowEpochMs else startMs
        val effectiveEnd = max(effectiveStart + 60_000L, endMs)
        val durSecs = ((effectiveEnd - effectiveStart) / 1000L).coerceIn(60L, 86_400L)
        val todayLocal = Instant.ofEpochMilli(effectiveStart).atZone(zoneId).toLocalDate().toString()
        val candidate = record.copy(
            layer = TimeLayer.ACTIVE,
            status = "ACTIVE",
            date = todayLocal,
            startedAt = effectiveStart,
            endedAt = effectiveEnd,
            durationSeconds = durSecs,
            userConfirmedAt = nowEpochMs,
            confirmationStateRaw = ConfirmationState.DOING.name,
            plannedStartedAt = preservedPlanStart(record),
            plannedEndedAt = preservedPlanEnd(record),
            plannedDurationSeconds = preservedPlanDuration(record),
            overlapResolutionRaw = null,
            overlapWithTitle = null
        )
        val conflicts = ActivityBreakEngine.findAllOverlaps(
            startMs = effectiveStart,
            endMs = effectiveEnd,
            existingRecords = existingRecords,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            excludeRecordIds = setOf(record.id),
            ignoreParallelGroupId = record.relationGroupId
        )
        return if (conflicts.isNotEmpty()) {
            ConfirmationActionResult.Overlap(candidate, conflicts)
        } else {
            ConfirmationActionResult.Applied(listOf(candidate), "Đã chuyển \"${record.title}\" sang Đang diễn ra.")
        }
    }

    /** Xác nhận hoàn thành thực tế (COMPLETED). */
    fun confirmCompleted(
        record: LedgerRecord,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): ConfirmationActionResult {
        if (record.isCancelled) return ConfirmationActionResult.Invalid("Hoạt động đã bị hủy.")
        val todayLocal = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val parsedDate = runCatching { LocalDate.parse(record.date) }.getOrDefault(todayLocal)
        if (parsedDate.isAfter(todayLocal)) {
            return ConfirmationActionResult.Invalid("Hoạt động thuộc ngày tương lai (${record.date}), chưa thể xác nhận hoàn thành.")
        }
        if (record.startedAt != null && record.endedAt != null) {
            if (record.startedAt > nowEpochMs) {
                return ConfirmationActionResult.Invalid("Chưa tới giờ bắt đầu, chưa thể xác nhận hoàn thành.")
            }
            val effectiveEnd = minOf(record.endedAt, nowEpochMs)
            if (effectiveEnd - record.startedAt < 60_000L) {
                return ConfirmationActionResult.Invalid("Thời gian thực tế đã diễn ra chưa đủ 1 phút.")
            }
            val actualSeconds = ((effectiveEnd - record.startedAt) / 1000L).coerceAtLeast(60L)
            val candidate = record.copy(
                layer = if (record.source == "timer") TimeLayer.MEASURED else TimeLayer.REPORTED_TIMED,
                durationSeconds = actualSeconds,
                endedAt = effectiveEnd,
                userConfirmedAt = nowEpochMs,
                status = "CONFIRMED",
                confirmationStateRaw = ConfirmationState.COMPLETED.name,
                plannedStartedAt = preservedPlanStart(record),
                plannedEndedAt = preservedPlanEnd(record),
                plannedDurationSeconds = preservedPlanDuration(record),
                overlapResolutionRaw = if (record.isNeedsReconciliation) null else record.overlapResolutionRaw,
                overlapWithTitle = if (record.isNeedsReconciliation) null else record.overlapWithTitle
            )
            val conflicts = ActivityBreakEngine.findAllOverlaps(
                startMs = record.startedAt,
                endMs = effectiveEnd,
                existingRecords = existingRecords,
                activeTimer = if (record.source == "timer") null else activeTimer,
                nowEpochMs = nowEpochMs,
                excludeRecordIds = setOf(record.id),
                ignoreParallelGroupId = if (candidate.overlapResolution.name == "PARALLEL_CONFIRMED") candidate.relationGroupId else null
            )
            return if (conflicts.isNotEmpty()) {
                ConfirmationActionResult.Overlap(candidate, conflicts)
            } else {
                ConfirmationActionResult.Applied(listOf(candidate), "Đã xác nhận hoàn thành \"${record.title}\".")
            }
        } else {
            val updated = record.copy(
                layer = TimeLayer.REPORTED_UNTIMED,
                userConfirmedAt = nowEpochMs,
                status = "CONFIRMED",
                confirmationStateRaw = ConfirmationState.COMPLETED.name,
                overlapResolutionRaw = if (record.isNeedsReconciliation) null else record.overlapResolutionRaw,
                overlapWithTitle = if (record.isNeedsReconciliation) null else record.overlapWithTitle
            )
            return ConfirmationActionResult.Applied(listOf(updated), "Đã xác nhận hoàn thành \"${record.title}\".")
        }
    }

    /**
     * Điều chỉnh thời gian / thông tin thực tế (ADJUSTED).
     * Bảo toàn kế hoạch gốc (planned*) và không làm mất trạng thái hoàn thành thực tế.
     */
    fun adjustRecord(
        existing: LedgerRecord,
        newTitle: String,
        newCategoryKey: String,
        newDateLocal: String,
        newDurationMinutes: Int?,
        newStartHm: String,
        newEndHm: String,
        allowFutureAsPlanned: Boolean,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): ConfirmationActionResult {
        val st = newStartHm.trim()
        val en = newEndHm.trim()
        if ((st.isNotEmpty() && en.isEmpty()) || (st.isEmpty() && en.isNotEmpty())) {
            return ConfirmationActionResult.Invalid("Hãy nhập cả giờ bắt đầu và giờ kết thúc, hoặc bỏ trống cả hai.")
        }
        var startMs: Long? = null
        var endMs: Long? = null
        if (st.isNotEmpty() && en.isNotEmpty()) {
            val range = LedgerValidator.parseTimeRangeToEpochMillis(newDateLocal, st, en, zoneId)
                ?: return ConfirmationActionResult.Invalid("Giờ bắt đầu/kết thúc không hợp lệ (định dạng HH:mm).")
            startMs = range.first
            endMs = range.second
        }
        val effectiveSource = if (existing.source == "timer" && startMs != null &&
            startMs == existing.startedAt && endMs == existing.endedAt
        ) {
            "timer"
        } else {
            "manual"
        }
        val origPlanStart = preservedPlanStart(existing)
        val origPlanEnd = preservedPlanEnd(existing)
        val origPlanDur = preservedPlanDuration(existing)

        // Dựng bản ghi trước mà chưa chặn overlap nếu cần mở hộp thoại BREAK/PARALLEL
        val validatedNoOverlap = LedgerValidator.validateAndBuildRecord(
            id = existing.id,
            source = effectiveSource,
            rawTitle = newTitle,
            rawCategory = newCategoryKey,
            dateLocal = newDateLocal,
            inputDurationMinutes = if (startMs != null && endMs != null) null else newDurationMinutes,
            startedAt = startMs,
            endedAt = endMs,
            existingRecords = emptyList(),
            activeTimer = null,
            nowEpochMs = nowEpochMs,
            zoneId = zoneId,
            idempotencyKey = existing.idempotencyKey,
            excludeRecordId = existing.id,
            convertFutureToPlanned = allowFutureAsPlanned,
            tagId = existing.tagId,
            plannedStartedAt = origPlanStart,
            plannedEndedAt = origPlanEnd,
            plannedDurationSeconds = origPlanDur
        )
        val built = when (validatedNoOverlap) {
            is ValidationResult.Invalid -> return ConfirmationActionResult.Invalid(validatedNoOverlap.message)
            is ValidationResult.Valid -> validatedNoOverlap.record
        }
        val confState = when (built.layer) {
            TimeLayer.PLANNED -> ConfirmationState.PLANNED.name
            TimeLayer.ACTIVE -> ConfirmationState.DOING.name
            else -> ConfirmationState.ADJUSTED.name
        }
        val candidate = built.copy(
            createdAt = existing.createdAt,
            confirmationStateRaw = confState,
            overlapResolutionRaw = if (existing.isNeedsReconciliation) null else existing.overlapResolutionRaw,
            relationGroupId = if (existing.isNeedsReconciliation) null else existing.relationGroupId,
            overlapWithTitle = null
        )
        if (candidate.layer != TimeLayer.PLANNED && startMs != null && endMs != null) {
            val conflicts = ActivityBreakEngine.findAllOverlaps(
                startMs = startMs,
                endMs = endMs,
                existingRecords = existingRecords,
                activeTimer = if (effectiveSource == "timer") null else activeTimer,
                nowEpochMs = nowEpochMs,
                excludeRecordIds = setOf(existing.id),
                ignoreParallelGroupId = candidate.relationGroupId
            )
            if (conflicts.isNotEmpty()) {
                return ConfirmationActionResult.Overlap(candidate, conflicts)
            }
        }
        return ConfirmationActionResult.Applied(listOf(candidate), "Đã lưu điều chỉnh \"${candidate.title}\".")
    }

    /** Kết thúc sớm / gián đoạn (INTERRUPTED): giữ phần thực tế từ startedAt tới actualEndedAtMs. */
    fun interruptRecord(
        existing: LedgerRecord,
        actualEndedAtMs: Long,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): ConfirmationActionResult {
        if (existing.isCancelled) return ConfirmationActionResult.Invalid("Hoạt động đã bị hủy.")
        val start = existing.startedAt
            ?: return ConfirmationActionResult.Invalid("Chỉ hoạt động có mốc giờ mới ghi nhận điểm gián đoạn theo giờ.")
        if (start > nowEpochMs) {
            return ConfirmationActionResult.Invalid("Hoạt động chưa bắt đầu; hãy dùng Hủy nếu không diễn ra.")
        }
        val cappedEnd = minOf(actualEndedAtMs, nowEpochMs)
        if (cappedEnd - start < 60_000L) {
            return ConfirmationActionResult.Invalid("Thời gian đã diễn ra dưới 1 phút; hãy dùng Hủy kế hoạch nếu không thực hiện.")
        }
        val actualSeconds = ((cappedEnd - start) / 1000L).coerceAtMost(86_400L)
        val effectiveDate = Instant.ofEpochMilli(start).atZone(zoneId).toLocalDate().toString()
        val candidate = existing.copy(
            layer = TimeLayer.REPORTED_TIMED,
            status = "CONFIRMED",
            date = effectiveDate,
            endedAt = cappedEnd,
            durationSeconds = actualSeconds,
            userConfirmedAt = nowEpochMs,
            confirmationStateRaw = ConfirmationState.INTERRUPTED.name,
            plannedStartedAt = preservedPlanStart(existing),
            plannedEndedAt = preservedPlanEnd(existing),
            plannedDurationSeconds = preservedPlanDuration(existing),
            overlapResolutionRaw = if (existing.isNeedsReconciliation) null else existing.overlapResolutionRaw,
            overlapWithTitle = null
        )
        val conflicts = ActivityBreakEngine.findAllOverlaps(
            startMs = start,
            endMs = cappedEnd,
            existingRecords = existingRecords,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            excludeRecordIds = setOf(existing.id),
            ignoreParallelGroupId = candidate.relationGroupId
        )
        return if (conflicts.isNotEmpty()) {
            ConfirmationActionResult.Overlap(candidate, conflicts)
        } else {
            ConfirmationActionResult.Applied(listOf(candidate), "Đã ghi nhận gián đoạn / kết thúc sớm \"${candidate.title}\".")
        }
    }

    /** Tiếp tục hoạt động (kéo dài giờ kết thúc mới > nowEpochMs -> DOING / ACTIVE). */
    fun continueRecord(
        existing: LedgerRecord,
        newEndedAtMs: Long,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): ConfirmationActionResult {
        if (existing.isCancelled) return ConfirmationActionResult.Invalid("Hoạt động đã bị hủy.")
        val start = existing.startedAt ?: nowEpochMs
        if (newEndedAtMs <= nowEpochMs) {
            return ConfirmationActionResult.Invalid("Giờ kết thúc mới phải lớn hơn thời điểm hiện tại.")
        }
        val totalSecs = ((newEndedAtMs - start) / 1000L)
        if (totalSecs < 60L || totalSecs > 86_400L) {
            return ConfirmationActionResult.Invalid("Tổng thời lượng sau khi tiếp tục phải từ 1 phút đến 24 giờ.")
        }
        val effectiveDate = Instant.ofEpochMilli(start).atZone(zoneId).toLocalDate().toString()
        val candidate = existing.copy(
            layer = TimeLayer.ACTIVE,
            status = "ACTIVE",
            date = effectiveDate,
            startedAt = start,
            endedAt = newEndedAtMs,
            durationSeconds = totalSecs,
            userConfirmedAt = nowEpochMs,
            confirmationStateRaw = ConfirmationState.DOING.name,
            plannedStartedAt = preservedPlanStart(existing),
            plannedEndedAt = preservedPlanEnd(existing),
            plannedDurationSeconds = preservedPlanDuration(existing),
            overlapResolutionRaw = if (existing.isNeedsReconciliation) null else existing.overlapResolutionRaw,
            overlapWithTitle = null
        )
        val conflicts = ActivityBreakEngine.findAllOverlaps(
            startMs = start,
            endMs = newEndedAtMs,
            existingRecords = existingRecords,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            excludeRecordIds = setOf(existing.id),
            ignoreParallelGroupId = candidate.relationGroupId
        )
        return if (conflicts.isNotEmpty()) {
            ConfirmationActionResult.Overlap(candidate, conflicts)
        } else {
            ConfirmationActionResult.Applied(listOf(candidate), "Tiếp tục \"${candidate.title}\"; sẽ nhắc xác nhận lại khi tới giờ kết thúc mới.")
        }
    }

    /**
     * Điều chỉnh nhanh thời lượng cho bản ghi Dự kiến ([TimeLayer.PLANNED]) bằng tay cầm ± phút.
     * Tuyệt đối KHÔNG sửa bản ghi đã ghi nhận (MEASURED / REPORTED / ACTIVE) hay bản ghi Đã hủy.
     */
    fun resizePlannedRecord(
        existing: LedgerRecord,
        deltaMinutes: Int,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): ConfirmationActionResult {
        if (existing.isCancelled || existing.resolveLayerAt(nowEpochMs) != TimeLayer.PLANNED) {
            return ConfirmationActionResult.Invalid("Chỉ điều chỉnh nhanh thời lượng cho hoạt động Dự kiến.")
        }
        val currentMinutes = ((existing.durationSeconds + 30L) / 60L).toInt().coerceAtLeast(1)
        val newMinutes = currentMinutes + deltaMinutes
        if (newMinutes < 5) {
            return ConfirmationActionResult.Invalid("Hoạt động dự kiến cần tối thiểu 5 phút.")
        }
        if (newMinutes > 1440) {
            return ConfirmationActionResult.Invalid("Hoạt động dự kiến tối đa 24 giờ trong một thẻ.")
        }
        val hasTimedRange = existing.startedAt != null && existing.endedAt != null
        val newStart = existing.startedAt
        val newEnd = newStart?.plus(newMinutes * 60_000L)
        val built = LedgerValidator.validateAndBuildRecord(
            id = existing.id,
            source = existing.source,
            rawTitle = existing.title,
            rawCategory = existing.category.key,
            dateLocal = existing.date,
            inputDurationMinutes = if (hasTimedRange) null else newMinutes,
            startedAt = newStart,
            endedAt = newEnd,
            existingRecords = emptyList(),
            activeTimer = null,
            nowEpochMs = nowEpochMs,
            zoneId = zoneId,
            idempotencyKey = existing.idempotencyKey,
            excludeRecordId = existing.id,
            convertFutureToPlanned = true,
            tagId = existing.tagId,
            plannedStartedAt = newStart,
            plannedEndedAt = newEnd,
            plannedDurationSeconds = newMinutes * 60L
        )
        return when (built) {
            is ValidationResult.Invalid -> ConfirmationActionResult.Invalid(built.message)
            is ValidationResult.Valid -> {
                val updated = built.record.copy(
                    createdAt = existing.createdAt,
                    confirmationStateRaw = ConfirmationState.PLANNED.name,
                    plannedStartedAt = newStart,
                    plannedEndedAt = newEnd,
                    plannedDurationSeconds = newMinutes * 60L
                )
                ConfirmationActionResult.Applied(
                    updatedRecords = listOf(updated),
                    messageVi = "Đã điều chỉnh dự kiến \"${updated.title}\" thành ${
                        LedgerAnalyticsCalculator.formatCompactDurationSeconds(updated.durationSeconds)
                    }."
                )
            }
        }
    }

    /** Hủy hoạt động (CANCELLED): 0s thực tế, không chiếm overlap, giữ bản ghi cho AI Coach phân tích. */
    fun cancelRecord(
        existing: LedgerRecord,
        nowEpochMs: Long
    ): ConfirmationActionResult {
        val cancelled = existing.copy(
            layer = TimeLayer.PLANNED,
            status = "CANCELLED",
            userConfirmedAt = nowEpochMs,
            confirmationStateRaw = ConfirmationState.CANCELLED.name,
            plannedStartedAt = preservedPlanStart(existing),
            plannedEndedAt = preservedPlanEnd(existing),
            plannedDurationSeconds = preservedPlanDuration(existing),
            overlapResolutionRaw = OverlapResolution.NONE.name,
            overlapWithTitle = null
        )
        return ConfirmationActionResult.Applied(listOf(cancelled), "Đã đánh dấu hủy \"${existing.title}\" (không tính vào thực tế).")
    }
}
