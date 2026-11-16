package com.timewallet.mvp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import kotlinx.coroutines.flow.Flow

/** v1.1.0 — Tag cá nhân. `nameKey` duy nhất (kể cả Tag đã lưu trữ ⇒ gợi ý khôi phục thay vì tạo trùng). */
@Entity(tableName = "tags", indices = [Index(value = ["nameKey"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val categoryKey: String,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Tag = Tag(
        id = id,
        name = name,
        nameKey = nameKey,
        category = TimeCategory.fromKeyOrDefault(categoryKey),
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(t: Tag) = TagEntity(t.id, t.name, t.nameKey, t.category.key, t.archivedAt, t.createdAt, t.updatedAt)
    }
}

/** v1.1.0 — một PHIÊN BẢN của mục tiêu (xem [Goal]). Không bao giờ sửa dòng quá khứ: chỉ đóng `validTo` và thêm dòng mới. */
@Entity(tableName = "goals", indices = [Index(value = ["groupId"])])
data class GoalEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val targetType: String,
    val targetKey: String,
    val kind: String,
    val period: String,
    val targetSeconds: Long,
    val status: String,
    val validFrom: String,
    val validTo: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Goal = Goal(
        id = id,
        groupId = groupId,
        targetType = GoalTargetType.fromStorage(targetType),
        targetKey = targetKey,
        kind = GoalKind.fromStorage(kind),
        period = GoalPeriod.fromStorage(period),
        targetSeconds = targetSeconds.coerceAtLeast(60L),
        status = GoalVersionStatus.fromStorage(status),
        validFrom = validFrom,
        validTo = validTo,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(g: Goal) = GoalEntity(
            g.id, g.groupId, g.targetType.storageKey, g.targetKey, g.kind.storageKey, g.period.storageKey,
            g.targetSeconds, g.status.storageKey, g.validFrom, g.validTo, g.createdAt, g.updatedAt
        )
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    suspend fun getAllSnapshot(): List<TagEntity>

    /** ABORT (không REPLACE): một Tag trùng `nameKey` không bao giờ được ghi đè lên Tag khác. Người gọi kiểm trước bằng `TagRules`. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity)

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt ASC, validFrom ASC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY createdAt ASC, validFrom ASC")
    suspend fun getAllSnapshot(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}
