package com.timewallet.mvp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.timewallet.mvp.domain.model.EisenhowerQuad
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitFrequency
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.Project
import com.timewallet.mvp.domain.model.SubtaskItem
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Project = Project(
        id = id,
        name = name,
        colorHex = colorHex,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(p: Project) = ProjectEntity(
            id = p.id,
            name = p.name,
            colorHex = p.colorHex,
            archivedAt = p.archivedAt,
            createdAt = p.createdAt,
            updatedAt = p.updatedAt
        )
    }
}

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["dueDateLocal"]),
        Index(value = ["projectId"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val projectId: String?,
    val tagId: String?,
    val categoryKey: String,
    val priority: String,
    val estimatedMinutes: Int?,
    val dueDateLocal: String?,
    val dueTimeLocal: String?,
    val status: String,
    val eisenhowerQuad: String?,
    val goalId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val postponedCount: Int = 0
) {
    fun toDomain(subtasks: List<SubtaskItem> = emptyList()): TaskItem = TaskItem(
        id = id,
        title = title,
        notes = notes,
        projectId = projectId,
        tagId = tagId,
        category = TimeCategory.fromKeyOrDefault(categoryKey),
        priority = TaskPriority.fromStorage(priority),
        estimatedMinutes = estimatedMinutes,
        dueDateLocal = dueDateLocal,
        dueTimeLocal = dueTimeLocal,
        status = TaskStatus.fromStorage(status),
        eisenhowerQuad = EisenhowerQuad.fromStorageOrNull(eisenhowerQuad),
        goalId = goalId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        subtasks = subtasks,
        postponedCount = postponedCount
    )

    companion object {
        fun fromDomain(t: TaskItem) = TaskEntity(
            id = t.id,
            title = t.title,
            notes = t.notes,
            projectId = t.projectId,
            tagId = t.tagId,
            categoryKey = t.category.key,
            priority = t.priority.name,
            estimatedMinutes = t.estimatedMinutes,
            dueDateLocal = t.dueDateLocal,
            dueTimeLocal = t.dueTimeLocal,
            status = t.status.name,
            eisenhowerQuad = t.eisenhowerQuad?.name,
            goalId = t.goalId,
            createdAt = t.createdAt,
            updatedAt = t.updatedAt,
            completedAt = t.completedAt,
            postponedCount = t.postponedCount.coerceAtLeast(0)
        )
    }
}

@Entity(
    tableName = "subtasks",
    indices = [Index(value = ["taskId"])]
)
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val isDone: Boolean,
    val sortOrder: Int
) {
    fun toDomain(): SubtaskItem = SubtaskItem(
        id = id,
        taskId = taskId,
        title = title,
        isDone = isDone,
        sortOrder = sortOrder
    )

    companion object {
        fun fromDomain(s: SubtaskItem) = SubtaskEntity(
            id = s.id,
            taskId = s.taskId,
            title = s.title,
            isDone = s.isDone,
            sortOrder = s.sortOrder
        )
    }
}

@Entity(
    tableName = "habits",
    indices = [Index(value = ["tagId"])]
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val tagId: String?,
    val categoryKey: String,
    val frequencyType: String,
    val daysMask: Int,
    val targetPerPeriod: Int,
    val minSessionMinutes: Int,
    val goalId: String?,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Habit = Habit(
        id = id,
        title = title,
        tagId = tagId,
        category = TimeCategory.fromKeyOrDefault(categoryKey),
        frequency = HabitFrequency.fromStorage(frequencyType),
        daysMask = daysMask,
        targetPerPeriod = targetPerPeriod,
        minSessionMinutes = minSessionMinutes,
        goalId = goalId,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(h: Habit) = HabitEntity(
            id = h.id,
            title = h.title,
            tagId = h.tagId,
            categoryKey = h.category.key,
            frequencyType = h.frequency.name,
            daysMask = h.daysMask,
            targetPerPeriod = h.targetPerPeriod,
            minSessionMinutes = h.minSessionMinutes,
            goalId = h.goalId,
            archivedAt = h.archivedAt,
            createdAt = h.createdAt,
            updatedAt = h.updatedAt
        )
    }
}

@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "dateLocal"], unique = true)]
)
data class HabitLogEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val dateLocal: String,
    val completed: Boolean,
    val linkedLedgerId: String?,
    val updatedAt: Long
) {
    fun toDomain(): HabitLog = HabitLog(
        id = id,
        habitId = habitId,
        dateLocal = dateLocal,
        completed = completed,
        linkedLedgerId = linkedLedgerId,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(l: HabitLog) = HabitLogEntity(
            id = l.id,
            habitId = l.habitId,
            dateLocal = l.dateLocal,
            completed = l.completed,
            linkedLedgerId = l.linkedLedgerId,
            updatedAt = l.updatedAt
        )
    }
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY createdAt ASC")
    suspend fun getAllSnapshot(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAllTasksSnapshot(): List<TaskEntity>

    @Query("SELECT * FROM subtasks ORDER BY sortOrder ASC")
    fun observeAllSubtasks(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks ORDER BY sortOrder ASC")
    suspend fun getAllSubtasksSnapshot(): List<SubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubtask(subtask: SubtaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubtasks(subtasks: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasksForTask(taskId: String)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Transaction
    suspend fun deleteTaskWithSubtasks(taskId: String) {
        deleteSubtasksForTask(taskId)
        deleteTaskById(taskId)
    }

    @Query("DELETE FROM subtasks")
    suspend fun deleteAllSubtasks()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun getAllHabitsSnapshot(): List<HabitEntity>

    @Query("SELECT * FROM habit_logs ORDER BY dateLocal DESC")
    fun observeAllLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs ORDER BY dateLocal DESC")
    suspend fun getAllLogsSnapshot(): List<HabitLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: HabitLogEntity)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsForHabit(habitId: String)

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()
}
