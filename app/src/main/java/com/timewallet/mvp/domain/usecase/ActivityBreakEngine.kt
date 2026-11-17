package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class BreakDecision {
    BREAK_CONFIRMED,
    PARALLEL_CONFIRMED,
    TIME_ADJUSTED,
    BREAK_REJECTED,
    PENDING
}

data class OverlapResolutionPlan(
    val updatedRecords: List<LedgerRecord>,
    val deletedIds: List<String> = emptyList(),
    val summaryVi: String
)

/**
 * Xử lý chồng lấn thời gian & Activity Break (BREAK_CONFIRMED, PARALLEL_CONFIRMED, TIME_ADJUSTED, BREAK_REJECTED, PENDING).
 * Không bao giờ tự tính trùng thời gian vào quỹ ngày và không xóa ngầm dữ liệu người dùng.
 */
object ActivityBreakEngine {

    const val ACTIVE_TIMER_CONFLICT_ID = "__active_timer__"

    fun findAllOverlaps(
        startMs: Long,
        endMs: Long,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        excludeRecordIds: Set<String> = emptySet(),
        ignoreParallelGroupId: String? = null
    ): List<LedgerRecord> {
        val hits = existingRecords.filter { record ->
            val sameParallelGroup = !ignoreParallelGroupId.isNullOrBlank() &&
                record.overlapResolution == OverlapResolution.PARALLEL_CONFIRMED &&
                record.relationGroupId == ignoreParallelGroupId
            record.id !in excludeRecordIds &&
                !sameParallelGroup &&
                record.isTimedActualOrActive &&
                record.startedAt != null &&
                record.endedAt != null &&
                startMs < record.endedAt &&
                endMs > record.startedAt
        }.toMutableList()

        if (activeTimer != null && startMs < nowEpochMs && endMs > activeTimer.startedAtEpochMs) {
            val today = Instant.ofEpochMilli(activeTimer.startedAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            hits += LedgerRecord(
                id = ACTIVE_TIMER_CONFLICT_ID,
                source = "timer",
                layer = TimeLayer.ACTIVE,
                createdAt = activeTimer.startedAtEpochMs,
                userConfirmedAt = nowEpochMs,
                category = com.timewallet.mvp.domain.model.TimeCategory.fromKeyOrDefault(activeTimer.categoryKey),
                title = "Bộ đếm đang chạy (${activeTimer.title})",
                date = today,
                durationSeconds = ((nowEpochMs - activeTimer.startedAtEpochMs) / 1000L).coerceAtLeast(1L),
                startedAt = activeTimer.startedAtEpochMs,
                endedAt = nowEpochMs,
                timezone = activeTimer.timezone,
                status = "ACTIVE",
                idempotencyKey = ACTIVE_TIMER_CONFLICT_ID
            )
        }
        return hits
    }

    fun resolveBreak(
        incoming: LedgerRecord,
        conflicts: List<LedgerRecord>,
        decision: BreakDecision,
        nowEpochMs: Long,
        zoneId: ZoneId,
        groupIdSeed: String? = null
    ): Result<OverlapResolutionPlan> {
        val inStart = incoming.startedAt
            ?: return Result.failure(IllegalArgumentException("Bản ghi mới không có mốc giờ bắt đầu."))
        val inEnd = incoming.endedAt
            ?: return Result.failure(IllegalArgumentException("Bản ghi mới không có mốc giờ kết thúc."))
        val realConflicts = conflicts.filterNot { it.id == ACTIVE_TIMER_CONFLICT_ID }
        if (conflicts.any { it.id == ACTIVE_TIMER_CONFLICT_ID } &&
            (decision == BreakDecision.BREAK_CONFIRMED || decision == BreakDecision.PARALLEL_CONFIRMED)
        ) {
            return Result.failure(IllegalStateException("Hãy dừng và lưu Bộ đếm đang chạy trước khi tách khoảng nghỉ hoặc ghép song song với nó."))
        }

        return when (decision) {
            BreakDecision.BREAK_CONFIRMED -> {
                val relGroup = groupIdSeed ?: ("brk_${UUID.randomUUID()}")
                val out = mutableListOf<LedgerRecord>()
                for (base in realConflicts) {
                    val bStart = base.startedAt ?: continue
                    val bEnd = base.endedAt ?: continue
                    val origPlanStart = base.plannedStartedAt ?: bStart
                    val origPlanEnd = base.plannedEndedAt ?: bEnd
                    val origPlanDur = base.plannedDurationSeconds ?: base.durationSeconds

                    val leftDur = (inStart - bStart).coerceAtLeast(0L)
                    val rightDur = (bEnd - inEnd).coerceAtLeast(0L)
                    val hasLeft = leftDur >= 60_000L
                    val hasRight = rightDur >= 60_000L

                    if (!hasLeft && !hasRight) {
                        out += base.copy(
                            layer = TimeLayer.PLANNED,
                            status = "CANCELLED",
                            confirmationStateRaw = ConfirmationState.CANCELLED.name,
                            plannedStartedAt = origPlanStart,
                            plannedEndedAt = origPlanEnd,
                            plannedDurationSeconds = origPlanDur,
                            overlapResolutionRaw = OverlapResolution.BREAK_CONFIRMED.name,
                            relationGroupId = relGroup
                        )
                    } else {
                        var usedPrimaryId = false
                        if (hasLeft) {
                            val leftSeg = buildSplitSegment(
                                base = base,
                                segId = base.id,
                                segKey = base.idempotencyKey,
                                segStart = bStart,
                                segEnd = inStart,
                                origPlanStart = origPlanStart,
                                origPlanEnd = origPlanEnd,
                                origPlanDur = origPlanDur,
                                relGroup = relGroup,
                                nowEpochMs = nowEpochMs,
                                zoneId = zoneId
                            )
                            out += leftSeg
                            usedPrimaryId = true
                        }
                        if (hasRight) {
                            val rightSeg = buildSplitSegment(
                                base = base,
                                segId = if (!usedPrimaryId) base.id else "${base.id}_part_${inEnd}",
                                segKey = if (!usedPrimaryId) base.idempotencyKey else "${base.idempotencyKey}_part_${inEnd}",
                                segStart = inEnd,
                                segEnd = bEnd,
                                origPlanStart = origPlanStart,
                                origPlanEnd = origPlanEnd,
                                origPlanDur = origPlanDur,
                                relGroup = relGroup,
                                nowEpochMs = nowEpochMs,
                                zoneId = zoneId
                            )
                            out += rightSeg
                        }
                    }
                }
                val finalIncoming = incoming.copy(
                    overlapResolutionRaw = OverlapResolution.BREAK_CONFIRMED.name,
                    relationGroupId = relGroup,
                    overlapWithTitle = null
                )
                out += finalIncoming
                val msg = previewBreakSummaryVi(incoming, realConflicts)
                Result.success(OverlapResolutionPlan(updatedRecords = out, summaryVi = msg))
            }

            BreakDecision.PARALLEL_CONFIRMED -> {
                val existingGroup = realConflicts.mapNotNull { it.relationGroupId }.firstOrNull()
                    ?: incoming.relationGroupId
                    ?: "par_${UUID.randomUUID()}"
                val out = mutableListOf<LedgerRecord>()
                for (c in realConflicts) {
                    out += c.copy(
                        overlapResolutionRaw = OverlapResolution.PARALLEL_CONFIRMED.name,
                        relationGroupId = existingGroup
                    )
                }
                out += incoming.copy(
                    overlapResolutionRaw = OverlapResolution.PARALLEL_CONFIRMED.name,
                    relationGroupId = existingGroup,
                    overlapWithTitle = null
                )
                Result.success(
                    OverlapResolutionPlan(
                        updatedRecords = out,
                        summaryVi = "Đã xác nhận song song (mỗi hoạt động giữ nguyên thời lượng riêng, không cộng trùng quỹ ngày)."
                    )
                )
            }

            BreakDecision.BREAK_REJECTED -> {
                val firstTitle = conflicts.firstOrNull()?.title ?: "hoạt động khác"
                val flagged = incoming.copy(
                    overlapResolutionRaw = OverlapResolution.BREAK_REJECTED.name,
                    overlapWithTitle = firstTitle
                )
                Result.success(
                    OverlapResolutionPlan(
                        updatedRecords = listOf(flagged),
                        summaryVi = "Đã đánh dấu \"${incoming.title}\" không phải khoảng nghỉ — chờ làm rõ chồng lấn với \"$firstTitle\" (chưa tính vào thực tế)."
                    )
                )
            }

            BreakDecision.PENDING -> {
                val firstTitle = conflicts.firstOrNull()?.title ?: "hoạt động khác"
                val pending = incoming.copy(
                    overlapResolutionRaw = OverlapResolution.PENDING.name,
                    overlapWithTitle = firstTitle
                )
                Result.success(
                    OverlapResolutionPlan(
                        updatedRecords = listOf(pending),
                        summaryVi = "Đã giữ \"${incoming.title}\" ở trạng thái chờ đối soát với \"$firstTitle\" (chưa tính vào thực tế)."
                    )
                )
            }

            BreakDecision.TIME_ADJUSTED -> {
                Result.failure(IllegalArgumentException("TIME_ADJUSTED yêu cầu thời gian mới từ form chỉnh sửa."))
            }
        }
    }

    private fun buildSplitSegment(
        base: LedgerRecord,
        segId: String,
        segKey: String,
        segStart: Long,
        segEnd: Long,
        origPlanStart: Long,
        origPlanEnd: Long,
        origPlanDur: Long,
        relGroup: String,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): LedgerRecord {
        val segSeconds = ((segEnd - segStart) / 1000L).coerceAtLeast(60L)
        val segDate = Instant.ofEpochMilli(segStart).atZone(zoneId).toLocalDate().toString()
        val isOngoing = segStart <= nowEpochMs && segEnd > nowEpochMs
        val isFuture = segStart > nowEpochMs
        val baseConf = base.resolveConfirmationStateAt(nowEpochMs, segDate)
        val keepPendingConfirm = !isFuture && !isOngoing && baseConf == ConfirmationState.PENDING_CONFIRMATION
        val segLayer = when {
            isFuture -> TimeLayer.PLANNED
            isOngoing || keepPendingConfirm -> TimeLayer.ACTIVE
            base.source == "timer" -> TimeLayer.MEASURED
            else -> TimeLayer.REPORTED_TIMED
        }
        val segStatus = when (segLayer) {
            TimeLayer.PLANNED -> "PLANNED"
            TimeLayer.ACTIVE -> "ACTIVE"
            else -> "CONFIRMED"
        }
        val segConf = when {
            isFuture -> ConfirmationState.PLANNED
            isOngoing -> ConfirmationState.DOING
            keepPendingConfirm -> ConfirmationState.PENDING_CONFIRMATION
            baseConf == ConfirmationState.COMPLETED || baseConf == ConfirmationState.DOING -> ConfirmationState.COMPLETED
            baseConf == ConfirmationState.INTERRUPTED -> ConfirmationState.INTERRUPTED
            else -> ConfirmationState.ADJUSTED
        }
        val cutoffConfirmAt = when {
            isFuture -> base.userConfirmedAt
            keepPendingConfirm -> base.userConfirmedAt.coerceIn(segStart, segEnd)
            isOngoing -> maxOf(base.userConfirmedAt, nowEpochMs).coerceAtMost(segEnd - 1L)
            else -> nowEpochMs
        }
        return base.copy(
            id = segId,
            idempotencyKey = segKey,
            date = segDate,
            startedAt = segStart,
            endedAt = segEnd,
            durationSeconds = segSeconds,
            layer = segLayer,
            status = segStatus,
            userConfirmedAt = cutoffConfirmAt,
            confirmationStateRaw = segConf.name,
            plannedStartedAt = origPlanStart,
            plannedEndedAt = origPlanEnd,
            plannedDurationSeconds = origPlanDur,
            overlapResolutionRaw = OverlapResolution.BREAK_CONFIRMED.name,
            relationGroupId = relGroup,
            overlapWithTitle = null
        )
    }

    fun previewBreakSummaryVi(incoming: LedgerRecord, conflicts: List<LedgerRecord>): String {
        val inStart = incoming.startedAt ?: return "Đã tách khoảng nghỉ xen giữa."
        val inEnd = incoming.endedAt ?: return "Đã tách khoảng nghỉ xen giữa."
        val inSecs = ((inEnd - inStart) / 1000L).coerceAtLeast(0L)
        val base = conflicts.firstOrNull { it.id != ACTIVE_TIMER_CONFLICT_ID }
            ?: return "Đã lưu khoảng nghỉ \"${incoming.title}\"."
        val bStart = base.startedAt ?: return "Đã tách khoảng nghỉ."
        val bEnd = base.endedAt ?: return "Đã tách khoảng nghỉ."
        val remSecs = (((inStart - bStart).coerceAtLeast(0L) + (bEnd - inEnd).coerceAtLeast(0L)) / 1000L)
        val baseFmt = LedgerAnalyticsCalculator.formatCompactDurationSeconds(remSecs)
        val inFmt = LedgerAnalyticsCalculator.formatCompactDurationSeconds(inSecs)
        val totalFmt = LedgerAnalyticsCalculator.formatCompactDurationSeconds(remSecs + inSecs)
        return "Tạm dừng \"${base.title}\" khi \"${incoming.title}\" xen giữa → ${base.title} $baseFmt + ${incoming.title} $inFmt = $totalFmt thực tế."
    }
}
