package com.timewallet.mvp.data.local

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.ActivityBreakEngine
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import java.time.ZoneId
import java.util.UUID

/** Kết quả xác nhận ghi một lô bản ghi (đề xuất đã được người dùng xác nhận, hoặc nhập thủ công). */
sealed class ConfirmResult {
    /** Không qua kiểm tra nghiệp vụ: chưa ghi gì. */
    data class Invalid(val message: String) : ConfirmResult()

    /** DAO hủy nguyên tử (trùng giờ, Timer mới bật, dữ liệu không hợp lệ): chưa ghi gì. */
    data class Aborted(val message: String) : ConfirmResult()

    data class Committed(val savedCount: Int, val skippedDuplicates: Int) : ConfirmResult()
}

/** Kế hoạch tách tiền tố an toàn vs mục đầu tiên chồng lấn cho lô nhiều đề xuất Chat. */
sealed class BatchOverlapStep {
    data class Invalid(val message: String) : BatchOverlapStep()
    data class ReadyAll(
        val inputs: List<BatchRecordInput>,
        val skippedDuplicates: Int
    ) : BatchOverlapStep()

    data class ConflictAt(
        val safePrefix: List<BatchRecordInput>,
        val conflictCandidate: LedgerRecord,
        val conflicts: List<LedgerRecord>,
        val conflictInputId: String,
        val remainingInputIds: List<String>,
        val skippedDuplicates: Int
    ) : BatchOverlapStep()
}

object BatchProposalPlanner {
    fun plan(
        inputs: List<BatchRecordInput>,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        nowEpochMs: Long,
        zone: ZoneId
    ): BatchOverlapStep {
        if (inputs.isEmpty()) return BatchOverlapStep.ReadyAll(emptyList(), 0)
        val existingKeys = existingRecords.mapTo(HashSet()) { it.idempotencyKey }
        val seenInBatch = HashSet<String>()
        var skippedDuplicates = 0

        val uniqueBuilt = mutableListOf<Pair<BatchRecordInput, LedgerRecord>>()
        for (input in inputs) {
            val effectiveId = input.id ?: "rec_${UUID.randomUUID()}"
            val effectiveKey = input.idempotencyKey?.takeIf { it.isNotBlank() } ?: effectiveId
            if (effectiveKey in existingKeys || !seenInBatch.add(effectiveKey)) {
                skippedDuplicates++
                continue
            }
            val parsedRange = if (!input.startHm.isNullOrBlank() && !input.endHm.isNullOrBlank()) {
                LedgerValidator.parseTimeRangeToEpochMillis(input.dateLocal, input.startHm, input.endHm, zone)
                    ?: return BatchOverlapStep.Invalid("Khoảng giờ của \"${input.rawTitle}\" không hợp lệ (HH:mm).")
            } else {
                null
            }
            when (val validated = LedgerValidator.validateAndBuildRecord(
                id = effectiveId,
                source = input.source,
                rawTitle = input.rawTitle,
                rawCategory = input.rawCategory,
                dateLocal = input.dateLocal,
                inputDurationMinutes = input.durationMinutes,
                startedAt = parsedRange?.first,
                endedAt = parsedRange?.second,
                existingRecords = emptyList(),
                activeTimer = null,
                nowEpochMs = nowEpochMs,
                zoneId = zone,
                idempotencyKey = effectiveKey,
                convertFutureToPlanned = input.convertFutureToPlanned,
                tagId = input.tagId,
                isMarkedWaste = input.isMarkedWaste,
                taskId = input.taskId,
                habitId = input.habitId
            )) {
                is ValidationResult.Invalid -> return BatchOverlapStep.Invalid(validated.message)
                is ValidationResult.Valid -> uniqueBuilt += (input.copy(id = effectiveId, idempotencyKey = effectiveKey) to validated.record)
            }
        }

        if (uniqueBuilt.isEmpty()) {
            return BatchOverlapStep.ReadyAll(emptyList(), skippedDuplicates)
        }

        val projectedExisting = existingRecords.toMutableList()
        val safePrefix = mutableListOf<BatchRecordInput>()
        for (idx in uniqueBuilt.indices) {
            val (input, record) = uniqueBuilt[idx]
            if (record.isTimedActualOrActive && record.startedAt != null && record.endedAt != null) {
                val conflicts = ActivityBreakEngine.findAllOverlaps(
                    startMs = record.startedAt,
                    endMs = record.endedAt,
                    existingRecords = projectedExisting,
                    activeTimer = activeTimer,
                    nowEpochMs = nowEpochMs
                )
                if (conflicts.isNotEmpty()) {
                    val remainingIds = uniqueBuilt.drop(idx + 1).map { it.first.id!! }
                    return BatchOverlapStep.ConflictAt(
                        safePrefix = safePrefix.toList(),
                        conflictCandidate = record,
                        conflicts = conflicts,
                        conflictInputId = input.id!!,
                        remainingInputIds = remainingIds,
                        skippedDuplicates = skippedDuplicates
                    )
                }
            }
            safePrefix += input
            projectedExisting += record
        }
        return BatchOverlapStep.ReadyAll(safePrefix, skippedDuplicates)
    }
}

/**
 * Đường GHI DUY NHẤT từ đề xuất/nhập thủ công xuống sổ cái: validateBatchAtomic -> commitBatchRecordsGuarded.
 * ViewModel và test dùng chung hàm này (không có bản sao), nên test tích hợp chạy đúng mã thật.
 * `idempotencyKey` của từng đầu vào bảo đảm bấm lặp không ghi trùng; hai hoạt động giống nhau nhưng khác key vẫn ghi riêng.
 */
object BatchConfirm {
    suspend fun run(
        dao: LedgerDao,
        inputs: List<BatchRecordInput>,
        activeTimer: ActiveTimerSession?,
        readActiveTimer: () -> ActiveTimerSession?,
        nowEpochMs: Long,
        zone: ZoneId,
        readNowMs: () -> Long = { System.currentTimeMillis() }
    ): ConfirmResult {
        val existing = dao.getAllRecordsSnapshot().map { it.toDomain() }
        return when (val batch = LedgerValidator.validateBatchAtomic(
            inputs = inputs,
            existingRecords = existing,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            zoneId = zone
        )) {
            is BatchValidationResult.Invalid -> ConfirmResult.Invalid(batch.message)
            is BatchValidationResult.Valid -> {
                val outcome = dao.commitBatchRecordsGuarded(batch.records.map { LedgerRecordEntity.fromDomain(it) }) {
                    // Đối chiếu lại Timer mới nhất bên trong khóa, ngay trước khi ghi (AI-01 Finding F).
                    LedgerValidator.findActiveTimerConflict(
                        records = batch.records,
                        activeTimer = readActiveTimer(),
                        nowEpochMs = readNowMs()
                    )
                }
                if (outcome.abortedAtomically) {
                    ConfirmResult.Aborted(outcome.errorMessage ?: "dữ liệu không hợp lệ hoặc trùng giờ.")
                } else {
                    ConfirmResult.Committed(outcome.savedCount, outcome.skippedDuplicates)
                }
            }
        }
    }
}
