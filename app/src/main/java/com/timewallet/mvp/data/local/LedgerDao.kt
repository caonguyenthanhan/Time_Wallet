package com.timewallet.mvp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object LedgerConcurrencyGate {
    val mutex = Mutex()
}

sealed class DbCommitOutcome {
    data object Saved : DbCommitOutcome()
    data object DuplicateIgnored : DbCommitOutcome()
    data class OverlapRejected(val conflictingTitle: String) : DbCommitOutcome()
    data class InvalidEntityRejected(val reason: String) : DbCommitOutcome()
}

data class BatchCommitOutcome(
    val savedCount: Int,
    val skippedDuplicates: Int,
    val rejectedConflicts: List<Pair<String, String>>, // (proposalTitle, conflictingTitle)
    val abortedAtomically: Boolean = false,
    val errorMessage: String? = null
)

@Dao
interface LedgerDao {

    @Query("SELECT * FROM ledger_records ORDER BY COALESCE(startedAt, createdAt) DESC")
    fun observeAllRecords(): Flow<List<LedgerRecordEntity>>

    @Query("SELECT * FROM ledger_records ORDER BY COALESCE(startedAt, createdAt) DESC")
    suspend fun getAllRecordsSnapshot(): List<LedgerRecordEntity>

    @Query("SELECT COUNT(*) FROM ledger_records WHERE idempotencyKey = :idempotencyKey")
    suspend fun countByIdempotencyKey(idempotencyKey: String): Int

    @Query(
        """
        SELECT * FROM ledger_records
        WHERE (layer = 'MEASURED' OR layer = 'REPORTED_TIMED' OR layer = 'ACTIVE')
          AND status != 'PLANNED'
          AND startedAt IS NOT NULL
          AND endedAt IS NOT NULL
          AND id != :excludeId
          AND :startMs < endedAt
          AND :endMs > startedAt
        LIMIT 1
        """
    )
    suspend fun findOverlappingEntity(
        startMs: Long,
        endMs: Long,
        excludeId: String = ""
    ): LedgerRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInternal(entity: LedgerRecordEntity)

    @Query("DELETE FROM ledger_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ledger_records")
    suspend fun deleteAll()

    fun isEntityStructurallyValid(entity: LedgerRecordEntity): String? {
        if (entity.title.isBlank()) {
            return "Tên hoạt động trống."
        }
        if (entity.durationSeconds !in 1L..86_400L) {
            return "Thời lượng bản ghi không hợp lệ (${entity.durationSeconds}s)."
        }
        if ((entity.startedAt == null) != (entity.endedAt == null)) {
            return "Thiếu giờ bắt đầu hoặc giờ kết thúc."
        }
        if (entity.startedAt != null && entity.endedAt != null && entity.endedAt <= entity.startedAt) {
            return "Giờ kết thúc phải lớn hơn giờ bắt đầu."
        }
        return null
    }

    /**
     * Transaction commit chống race condition & lưu trùng.
     */
    @Transaction
    suspend fun commitRecordTransactionally(
        entity: LedgerRecordEntity,
        isUpdateExisting: Boolean = false
    ): DbCommitOutcome = LedgerConcurrencyGate.mutex.withLock {
        commitRecordUnlocked(entity, isUpdateExisting)
    }

    suspend fun commitRecordUnlocked(
        entity: LedgerRecordEntity,
        isUpdateExisting: Boolean = false
    ): DbCommitOutcome {
        val structuralError = isEntityStructurallyValid(entity)
        if (structuralError != null) {
            return DbCommitOutcome.InvalidEntityRejected(structuralError)
        }

        if (!isUpdateExisting && countByIdempotencyKey(entity.idempotencyKey) > 0) {
            return DbCommitOutcome.DuplicateIgnored
        }

        val isTimedActualOrActive =
            (entity.layer == "MEASURED" || entity.layer == "REPORTED_TIMED" || entity.layer == "ACTIVE") &&
                entity.status != "PLANNED" &&
                entity.startedAt != null && entity.endedAt != null

        if (isTimedActualOrActive) {
            val conflict = findOverlappingEntity(
                startMs = entity.startedAt!!,
                endMs = entity.endedAt!!,
                excludeId = entity.id
            )
            if (conflict != null) {
                return DbCommitOutcome.OverlapRejected(conflict.title)
            }
        }

        upsertInternal(entity)
        return DbCommitOutcome.Saved
    }

    /**
     * Xác nhận hàng loạt nguyên tử (All-or-Nothing Batch Confirm) có khóa đồng bộ chống ghi đồng thời:
     * - Khóa bằng LedgerConcurrencyGate.mutex + @Transaction để chống race condition khi nhiều coroutine ghi cùng lúc.
     * - Giai đoạn 1 (Pre-flight): kiểm tra toàn bộ các phần tử trong lô (cấu trúc hợp lệ, không trùng giờ nội bộ lô,
     *   không trùng giờ với bản ghi thực tế trong DB). Nếu BẤT KỲ phần tử nào lỗi hoặc trùng giờ,
     *   hủy toàn bộ giao dịch (savedCount = 0, không ghi bất kỳ dòng nào vào DB).
     * - Giai đoạn 2 (Commit): chỉ khi tất cả đều hợp lệ mới ghi toàn bộ vào DB.
     */
    @Transaction
    suspend fun commitBatchRecordsTransactionally(
        entities: List<LedgerRecordEntity>
    ): BatchCommitOutcome = LedgerConcurrencyGate.mutex.withLock {
        commitBatchUnlocked(entities)
    }

    /**
     * AI-01 (Finding F): như [commitBatchRecordsTransactionally] nhưng chạy [preCommitGuard] BÊN TRONG khóa
     * ngay trước khi ghi, để đối chiếu lại trạng thái ngoài DB (ví dụ Timer vừa bắt đầu sau lúc validate).
     * Guard trả về thông điệp lỗi => hủy nguyên tử, không ghi dòng nào.
     */
    @Transaction
    suspend fun commitBatchRecordsGuarded(
        entities: List<LedgerRecordEntity>,
        preCommitGuard: suspend () -> String?
    ): BatchCommitOutcome = LedgerConcurrencyGate.mutex.withLock {
        val guardError = preCommitGuard()
        if (guardError != null) {
            return BatchCommitOutcome(
                savedCount = 0,
                skippedDuplicates = 0,
                rejectedConflicts = emptyList(),
                abortedAtomically = true,
                errorMessage = guardError
            )
        }
        commitBatchUnlocked(entities)
    }

    suspend fun commitBatchUnlocked(
        entities: List<LedgerRecordEntity>
    ): BatchCommitOutcome {
        if (entities.isEmpty()) {
            return BatchCommitOutcome(
                savedCount = 0,
                skippedDuplicates = 0,
                rejectedConflicts = emptyList(),
                abortedAtomically = true,
                errorMessage = "Danh sách trống."
            )
        }

        val timedInBatch = mutableListOf<LedgerRecordEntity>()

        for ((idx, entity) in entities.withIndex()) {
            val structuralError = isEntityStructurallyValid(entity)
            if (structuralError != null) {
                return BatchCommitOutcome(
                    savedCount = 0,
                    skippedDuplicates = 0,
                    rejectedConflicts = emptyList(),
                    abortedAtomically = true,
                    errorMessage = "Mục #${idx + 1} (\"${entity.title}\") không hợp lệ: $structuralError"
                )
            }

            val isTimedActualOrActive =
                (entity.layer == "MEASURED" || entity.layer == "REPORTED_TIMED" || entity.layer == "ACTIVE") &&
                    entity.status != "PLANNED" &&
                    entity.startedAt != null && entity.endedAt != null

            if (isTimedActualOrActive) {
                val startMs = entity.startedAt!!
                val endMs = entity.endedAt!!

                // Kiểm tra trùng giờ với các mục trước đó ngay trong cùng lô (intra-batch overlap)
                val intraConflict = timedInBatch.firstOrNull { prev ->
                    prev.id != entity.id &&
                        startMs < prev.endedAt!! &&
                        endMs > prev.startedAt!!
                }
                if (intraConflict != null) {
                    return BatchCommitOutcome(
                        savedCount = 0,
                        skippedDuplicates = 0,
                        rejectedConflicts = listOf(entity.title to intraConflict.title),
                        abortedAtomically = true,
                        errorMessage = "\"${entity.title}\" trùng mốc giờ với \"${intraConflict.title}\" trong cùng lô."
                    )
                }

                // Kiểm tra trùng giờ với bản ghi đã có trong DB
                val dbConflict = findOverlappingEntity(
                    startMs = startMs,
                    endMs = endMs,
                    excludeId = entity.id
                )
                if (dbConflict != null) {
                    return BatchCommitOutcome(
                        savedCount = 0,
                        skippedDuplicates = 0,
                        rejectedConflicts = listOf(entity.title to dbConflict.title),
                        abortedAtomically = true,
                        errorMessage = "\"${entity.title}\" trùng mốc giờ với \"${dbConflict.title}\"."
                    )
                }

                timedInBatch.add(entity)
            }
        }

        var saved = 0
        var duplicates = 0
        val seenIdempotencyKeys = mutableSetOf<String>()

        for (entity in entities) {
            if (!seenIdempotencyKeys.add(entity.idempotencyKey) ||
                countByIdempotencyKey(entity.idempotencyKey) > 0
            ) {
                duplicates++
            } else {
                upsertInternal(entity)
                saved++
            }
        }

        return BatchCommitOutcome(
            savedCount = saved,
            skippedDuplicates = duplicates,
            rejectedConflicts = emptyList(),
            abortedAtomically = false,
            errorMessage = null
        )
    }
}
