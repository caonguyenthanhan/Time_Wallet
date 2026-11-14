package com.timewallet.mvp.data.local

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.LedgerValidator
import java.time.ZoneId

/** Kết quả xác nhận ghi một lô bản ghi (đề xuất đã được người dùng xác nhận, hoặc nhập thủ công). */
sealed class ConfirmResult {
    /** Không qua kiểm tra nghiệp vụ: chưa ghi gì. */
    data class Invalid(val message: String) : ConfirmResult()

    /** DAO hủy nguyên tử (trùng giờ, Timer mới bật, dữ liệu không hợp lệ): chưa ghi gì. */
    data class Aborted(val message: String) : ConfirmResult()

    data class Committed(val savedCount: Int, val skippedDuplicates: Int) : ConfirmResult()
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
