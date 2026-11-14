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
        Index(value = ["startedAt", "endedAt"])
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
    val idempotencyKey: String
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
            idempotencyKey = idempotencyKey
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
                idempotencyKey = record.idempotencyKey
            )
        }
    }
}
