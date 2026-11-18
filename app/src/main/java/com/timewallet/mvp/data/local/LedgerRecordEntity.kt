package com.timewallet.mvp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer

@Entity(
    tableName = "ledger_records",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["date"]),
        Index(value = ["startedAt", "endedAt"]),
        Index(value = ["tagId"]),
        Index(value = ["taskId"]),
        Index(value = ["habitId"])
    ]
)
data class LedgerRecordEntity(
    @PrimaryKey val id: String,
    val source: String,
    val layer: String,
    val createdAt: Long,
    val userConfirmedAt: Long,
    val category: String,
    val title: String,
    val date: String,
    val durationSeconds: Long,
    val startedAt: Long?,
    val endedAt: Long?,
    val timezone: String,
    val status: String,
    val idempotencyKey: String,
    val tagId: String? = null,
    val confirmationState: String? = null,
    val plannedStartedAt: Long? = null,
    val plannedEndedAt: Long? = null,
    val plannedDurationSeconds: Long? = null,
    val overlapResolution: String? = null,
    val relationGroupId: String? = null,
    val overlapWithTitle: String? = null,
    val isMarkedWaste: Boolean = false,
    val taskId: String? = null,
    val habitId: String? = null
) {
    fun toDomain(): LedgerRecord {
        val parsedLayer = TimeLayer.fromStorageString(layer, startedAt, endedAt)
        return LedgerRecord(
            id = id,
            source = source,
            layer = parsedLayer,
            createdAt = createdAt,
            userConfirmedAt = userConfirmedAt,
            category = TimeCategory.fromKeyOrDefault(category),
            title = title,
            date = date,
            durationSeconds = durationSeconds,
            startedAt = startedAt,
            endedAt = endedAt,
            timezone = timezone,
            status = status,
            idempotencyKey = idempotencyKey,
            tagId = tagId,
            confirmationStateRaw = confirmationState,
            plannedStartedAt = plannedStartedAt,
            plannedEndedAt = plannedEndedAt,
            plannedDurationSeconds = plannedDurationSeconds,
            overlapResolutionRaw = overlapResolution,
            relationGroupId = relationGroupId,
            overlapWithTitle = overlapWithTitle,
            isMarkedWaste = isMarkedWaste,
            taskId = taskId,
            habitId = habitId
        )
    }

    companion object {
        fun fromDomain(record: LedgerRecord): LedgerRecordEntity {
            return LedgerRecordEntity(
                id = record.id,
                source = record.source,
                layer = record.layer.name,
                createdAt = record.createdAt,
                userConfirmedAt = record.userConfirmedAt,
                category = record.category.key,
                title = record.title,
                date = record.date,
                durationSeconds = record.durationSeconds,
                startedAt = record.startedAt,
                endedAt = record.endedAt,
                timezone = record.timezone,
                status = record.status,
                idempotencyKey = record.idempotencyKey,
                tagId = record.tagId,
                confirmationState = record.confirmationStateRaw,
                plannedStartedAt = record.plannedStartedAt,
                plannedEndedAt = record.plannedEndedAt,
                plannedDurationSeconds = record.plannedDurationSeconds,
                overlapResolution = record.overlapResolutionRaw,
                relationGroupId = record.relationGroupId,
                overlapWithTitle = record.overlapWithTitle,
                isMarkedWaste = record.isMarkedWaste,
                taskId = record.taskId,
                habitId = record.habitId
            )
        }
    }
}
