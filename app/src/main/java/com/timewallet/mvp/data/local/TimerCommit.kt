package com.timewallet.mvp.data.local

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.TimerSegments
import com.timewallet.mvp.domain.usecase.ValidationResult
import java.time.Instant
import java.time.ZoneId

/** Kết quả chốt một phiên Đồng hồ vào sổ cái. Chỉ [Saved] và [AlreadyCommitted] cho phép xóa phiên khỏi bộ nhớ. */
sealed class TimerCommitResult {
    data class Saved(val segments: Int, val totalSeconds: Long) : TimerCommitResult()

    /** Phiên này đã được ghi từ trước (ví dụ app bị tắt giữa lúc ghi xong và lúc xóa phiên): không ghi lại, chỉ dọn phiên. */
    data object AlreadyCommitted : TimerCommitResult()

    /** Trùng giờ với bản ghi khác: chưa ghi gì, PHIÊN ĐƯỢC GIỮ để người dùng xử lý rồi lưu lại (không buộc phải Hủy). */
    data class Overlap(val message: String) : TimerCommitResult()

    data class Invalid(val message: String) : TimerCommitResult()
}

/**
 * Đường ghi DUY NHẤT của Đồng hồ (R01). ViewModel và test dùng chung hàm này.
 * Một giao dịch (`commitBatchRecordsGuarded`) cho mọi đoạn: hoặc ghi hết hoặc không ghi gì; khóa chống ghi trùng ổn định theo
 * `startedAt` nên thử lại / khởi động lại không bao giờ nhân đôi hay làm mất thời gian.
 */
object TimerCommit {

    suspend fun isCommitted(dao: LedgerDao, startedAtEpochMs: Long): Boolean =
        dao.countByIdempotencyKey(TimerSegments.idempotencyKey(startedAtEpochMs, 0)) > 0

    suspend fun run(
        dao: LedgerDao,
        title: String,
        categoryKey: String,
        startedAtEpochMs: Long,
        endEpochMs: Long,
        zone: ZoneId,
        tagId: String? = null,
        taskId: String? = null,
        habitId: String? = null
    ): TimerCommitResult {
        if (isCommitted(dao, startedAtEpochMs)) return TimerCommitResult.AlreadyCommitted
        val totalSeconds = (endEpochMs - startedAtEpochMs) / 1000L
        if (totalSeconds <= 0L) return TimerCommitResult.Invalid("Đồng hồ cần chạy ít nhất 1 giây.")
        val alignedEndMs = startedAtEpochMs + totalSeconds * 1000L
        val segments = TimerSegments.split(startedAtEpochMs, alignedEndMs)
        if (segments.isEmpty()) return TimerCommitResult.Invalid("Đồng hồ cần chạy ít nhất 1 giây.")

        val existing = dao.getAllRecordsSnapshot().map { it.toDomain() }
        val built = ArrayList<LedgerRecord>(segments.size)
        for (seg in segments) {
            val dateLocal = Instant.ofEpochMilli(seg.startMs).atZone(zone).toLocalDate().toString()
            when (val v = LedgerValidator.validateAndBuildRecord(
                source = "timer",
                rawTitle = title,
                rawCategory = categoryKey,
                dateLocal = dateLocal,
                inputDurationMinutes = null,
                startedAt = seg.startMs,
                endedAt = seg.endMs,
                existingRecords = existing + built,
                activeTimer = null,
                nowEpochMs = endEpochMs,
                zoneId = zone,
                idempotencyKey = TimerSegments.idempotencyKey(startedAtEpochMs, seg.index),
                tagId = tagId,
                taskId = taskId,
                habitId = habitId
            )) {
                is ValidationResult.Invalid -> return if (v.message.contains("trùng", ignoreCase = true)) {
                    if (isCommitted(dao, startedAtEpochMs)) TimerCommitResult.AlreadyCommitted else TimerCommitResult.Overlap(v.message)
                } else {
                    TimerCommitResult.Invalid(v.message)
                }
                is ValidationResult.Valid -> built += v.record
            }
        }

        val outcome = dao.commitBatchRecordsGuarded(built.map { LedgerRecordEntity.fromDomain(it) }) { null }
        // Bấm "Kết thúc & lưu" hai lần nhanh: lượt thứ hai vượt qua isCommitted() rồi va vào CHÍNH các dòng của lượt đầu
        // (DAO kiểm trùng giờ trước khóa chống ghi trùng). Kiểm lại khóa sau khi bị từ chối để báo đúng "đã ghi từ trước".
        if (outcome.abortedAtomically && isCommitted(dao, startedAtEpochMs)) return TimerCommitResult.AlreadyCommitted
        return when {
            outcome.abortedAtomically && outcome.rejectedConflicts.isNotEmpty() ->
                TimerCommitResult.Overlap(outcome.errorMessage ?: "Trùng thời gian với một bản ghi khác.")
            outcome.abortedAtomically -> TimerCommitResult.Invalid(outcome.errorMessage ?: "Dữ liệu không hợp lệ.")
            outcome.savedCount == 0 && outcome.skippedDuplicates > 0 -> TimerCommitResult.AlreadyCommitted
            else -> TimerCommitResult.Saved(outcome.savedCount, built.sumOf { it.durationSeconds })
        }
    }
}
