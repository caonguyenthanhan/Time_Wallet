package com.timewallet.mvp.data.local

import androidx.room.withTransaction
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.DailyReviewRecord
import com.timewallet.mvp.domain.model.EisenhowerQuad
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitFrequency
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.OverlapResolution
import com.timewallet.mvp.domain.model.Project
import com.timewallet.mvp.domain.model.SubtaskItem
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.TagRules
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class BackupRestoreMode(val labelVi: String) {
    MERGE_SAFE("Gộp an toàn"),
    REPLACE_ALL("Khôi phục toàn bộ")
}

data class ParsedBackupPayload(
    val schemaVersion: Int,
    val exportedAt: String,
    val records: List<LedgerRecord>,
    val budgets: List<CategoryTimeBudget>,
    val dailyReviews: List<DailyReviewRecord>,
    val activityNotes: List<ActivityReviewNote>,
    val tags: List<Tag>,
    val goals: List<Goal>,
    val projects: List<Project> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList()
)

sealed class BackupParseResult {
    data class Valid(val payload: ParsedBackupPayload) : BackupParseResult()
    data class Invalid(val reasonVi: String) : BackupParseResult()
}

data class BackupRestoreSummary(
    val mode: BackupRestoreMode,
    val recordsInserted: Int = 0,
    val recordsSkippedDuplicate: Int = 0,
    val recordsSkippedOverlap: Int = 0,
    val tagsInserted: Int = 0,
    val tagsRemapped: Int = 0,
    val goalsRestored: Int = 0,
    val budgetsRestored: Int = 0,
    val dailyReviewsRestored: Int = 0,
    val activityNotesRestored: Int = 0,
    val projectsRestored: Int = 0,
    val tasksRestored: Int = 0,
    val habitsRestored: Int = 0,
    val habitLogsRestored: Int = 0
) {
    fun toUserMessage(): String = buildString {
        append(
            if (mode == BackupRestoreMode.REPLACE_ALL) {
                "Đã khôi phục toàn bộ: "
            } else {
                "Đã gộp sao lưu: "
            }
        )
        append("$recordsInserted bản ghi")
        if (tagsInserted > 0) append(", $tagsInserted tag")
        if (goalsRestored > 0) append(", $goalsRestored mục tiêu")
        if (budgetsRestored > 0) append(", $budgetsRestored ngân sách")
        if (tasksRestored > 0) append(", $tasksRestored việc")
        if (habitsRestored > 0) append(", $habitsRestored thói quen")
        if (dailyReviewsRestored > 0) append(", $dailyReviewsRestored phản tư")
        val skipped = recordsSkippedDuplicate + recordsSkippedOverlap
        if (skipped > 0) {
            append(" (bỏ qua $recordsSkippedDuplicate trùng, $recordsSkippedOverlap chồng lấn)")
        }
        append(".")
    }
}

class BackupRestoreConflictException(message: String) : IllegalArgumentException(message)

interface BackupDatabaseBackend {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
    suspend fun getAllRecords(): List<LedgerRecordEntity>
    suspend fun upsertRecord(entity: LedgerRecordEntity)
    suspend fun deleteAllRecords()
    suspend fun getAllTags(): List<TagEntity>
    suspend fun insertTag(entity: TagEntity)
    suspend fun deleteAllTags()
    suspend fun getAllGoals(): List<GoalEntity>
    suspend fun upsertGoal(entity: GoalEntity)
    suspend fun deleteAllGoals()
    suspend fun getAllBudgets(): List<CategoryBudgetEntity>
    suspend fun upsertBudget(entity: CategoryBudgetEntity)
    suspend fun deleteAllBudgets()
    suspend fun getAllDailyReviews(): List<DailyReviewEntity>
    suspend fun upsertDailyReview(entity: DailyReviewEntity)
    suspend fun deleteAllDailyReviews()
    suspend fun getAllActivityNotes(): List<ActivityReviewNoteEntity>
    suspend fun upsertActivityNotes(entities: List<ActivityReviewNoteEntity>)
    suspend fun deleteAllActivityNotes()
    // v2.0 defaults giữ tương thích mọi mock/test cũ
    suspend fun getAllProjects(): List<ProjectEntity> = emptyList()
    suspend fun upsertProject(entity: ProjectEntity) {}
    suspend fun deleteAllProjects() {}
    suspend fun getAllTasks(): List<TaskEntity> = emptyList()
    suspend fun upsertTask(entity: TaskEntity) {}
    suspend fun deleteAllTasks() {}
    suspend fun upsertSubtasks(entities: List<SubtaskEntity>) {}
    suspend fun deleteAllSubtasks() {}
    suspend fun getAllHabits(): List<HabitEntity> = emptyList()
    suspend fun upsertHabit(entity: HabitEntity) {}
    suspend fun deleteAllHabits() {}
    suspend fun getAllHabitLogs(): List<HabitLogEntity> = emptyList()
    suspend fun upsertHabitLog(entity: HabitLogEntity) {}
    suspend fun deleteAllHabitLogs() {}
}

private class RoomBackupDatabaseBackend(
    private val database: TimeWalletDatabase
) : BackupDatabaseBackend {
    private val ledgerDao = database.ledgerDao()
    private val tagDao = database.tagDao()
    private val goalDao = database.goalDao()
    private val budgetDao = database.budgetDao()
    private val dailyReviewDao = database.dailyReviewDao()
    private val projectDao = database.projectDao()
    private val taskDao = database.taskDao()
    private val habitDao = database.habitDao()

    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }

    override suspend fun getAllRecords(): List<LedgerRecordEntity> = ledgerDao.getAllRecordsSnapshot()
    override suspend fun upsertRecord(entity: LedgerRecordEntity) = ledgerDao.upsertInternal(entity)
    override suspend fun deleteAllRecords() = ledgerDao.deleteAll()
    override suspend fun getAllTags(): List<TagEntity> = tagDao.getAllSnapshot()
    override suspend fun insertTag(entity: TagEntity) = tagDao.insert(entity)
    override suspend fun deleteAllTags() = tagDao.deleteAll()
    override suspend fun getAllGoals(): List<GoalEntity> = goalDao.getAllSnapshot()
    override suspend fun upsertGoal(entity: GoalEntity) = goalDao.upsert(entity)
    override suspend fun deleteAllGoals() = goalDao.deleteAll()
    override suspend fun getAllBudgets(): List<CategoryBudgetEntity> = budgetDao.getAllBudgetsSnapshot()
    override suspend fun upsertBudget(entity: CategoryBudgetEntity) = budgetDao.upsertBudget(entity)
    override suspend fun deleteAllBudgets() = budgetDao.deleteAllBudgets()
    override suspend fun getAllDailyReviews(): List<DailyReviewEntity> = dailyReviewDao.getAllDailyReviewsSnapshot()
    override suspend fun upsertDailyReview(entity: DailyReviewEntity) = dailyReviewDao.upsertDailyReview(entity)
    override suspend fun deleteAllDailyReviews() = dailyReviewDao.deleteAllDailyReviews()
    override suspend fun getAllActivityNotes(): List<ActivityReviewNoteEntity> = dailyReviewDao.getAllActivityNotesSnapshot()
    override suspend fun upsertActivityNotes(entities: List<ActivityReviewNoteEntity>) = dailyReviewDao.upsertActivityNotes(entities)
    override suspend fun deleteAllActivityNotes() = dailyReviewDao.deleteAllActivityNotes()
    override suspend fun getAllProjects(): List<ProjectEntity> = projectDao.getAllSnapshot()
    override suspend fun upsertProject(entity: ProjectEntity) = projectDao.upsert(entity)
    override suspend fun deleteAllProjects() = projectDao.deleteAll()
    override suspend fun getAllTasks(): List<TaskEntity> = taskDao.getAllTasksSnapshot()
    override suspend fun upsertTask(entity: TaskEntity) = taskDao.upsertTask(entity)
    override suspend fun deleteAllTasks() = taskDao.deleteAllTasks()
    override suspend fun upsertSubtasks(entities: List<SubtaskEntity>) = taskDao.upsertSubtasks(entities)
    override suspend fun deleteAllSubtasks() = taskDao.deleteAllSubtasks()
    override suspend fun getAllHabits(): List<HabitEntity> = habitDao.getAllHabitsSnapshot()
    override suspend fun upsertHabit(entity: HabitEntity) = habitDao.upsertHabit(entity)
    override suspend fun deleteAllHabits() = habitDao.deleteAllHabits()
    override suspend fun getAllHabitLogs(): List<HabitLogEntity> = habitDao.getAllLogsSnapshot()
    override suspend fun upsertHabitLog(entity: HabitLogEntity) = habitDao.upsertLog(entity)
    override suspend fun deleteAllHabitLogs() = habitDao.deleteAllLogs()
}

object LedgerJsonBackupParser {
    private val SUPPORTED_STATUSES = setOf("CONFIRMED", "ACTIVE", "PLANNED", "CANCELLED")
    private const val MAX_JSON_BYTES = 16 * 1024 * 1024

    fun parse(rawJson: String, nowEpochMs: Long = System.currentTimeMillis()): BackupParseResult {
        val trimmed = rawJson.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) {
            return BackupParseResult.Invalid("Nội dung sao lưu JSON đang trống.")
        }
        if (trimmed.length > MAX_JSON_BYTES) {
            return BackupParseResult.Invalid("Tệp sao lưu vượt quá giới hạn kích thước an toàn.")
        }

        val root = try {
            val tokener = JSONTokener(trimmed)
            val parsed = tokener.nextValue() as? JSONObject
                ?: return BackupParseResult.Invalid("Tệp sao lưu phải là một đối tượng JSON hợp lệ.")
            // Bảo đảm không có dữ liệu thừa sau dấu '}' kết thúc.
            var nextCh = tokener.next()
            while (nextCh.code != 0 && nextCh.isWhitespace()) {
                nextCh = tokener.next()
            }
            if (nextCh.code != 0) {
                return BackupParseResult.Invalid("Tệp JSON có dữ liệu dư thừa không hợp lệ.")
            }
            parsed
        } catch (e: Exception) {
            return BackupParseResult.Invalid("Không đọc được cấu trúc JSON (${e.message ?: "lỗi cú pháp"}).").also {
                @Suppress("UNUSED_EXPRESSION") e
            }
        }

        val schemaVersion = root.optNullableInt("schemaVersion")
            ?: return BackupParseResult.Invalid("Tệp sao lưu thiếu trường schemaVersion.")
        if (schemaVersion !in 1..2) {
            return BackupParseResult.Invalid("Phiên bản sao lưu schemaVersion=$schemaVersion chưa được hỗ trợ.")
        }

        val itemsArray = root.opt("items") as? JSONArray
            ?: return BackupParseResult.Invalid("Tệp sao lưu thiếu danh sách bản ghi \"items\".")

        val exportedAt = root.optNonBlankString("exportedAt") ?: Instant.ofEpochMilli(nowEpochMs).toString()
        val exportedAtMs = runCatching { Instant.parse(exportedAt).toEpochMilli() }.getOrElse { nowEpochMs }

        val tagsResult = parseTags(root, exportedAtMs)
        if (tagsResult.error != null) return BackupParseResult.Invalid(tagsResult.error)

        val goalsResult = parseGoals(root, exportedAtMs, tagsResult.idRemap)
        if (goalsResult.error != null) return BackupParseResult.Invalid(goalsResult.error)

        val budgetsResult = parseBudgets(root, exportedAtMs)
        if (budgetsResult.error != null) return BackupParseResult.Invalid(budgetsResult.error)

        val reviewsResult = parseDailyReviews(root, exportedAtMs)
        if (reviewsResult.error != null) return BackupParseResult.Invalid(reviewsResult.error)

        val recordsAndNotes = parseRecords(itemsArray, exportedAtMs, tagsResult.idRemap)
        if (recordsAndNotes.error != null) return BackupParseResult.Invalid(recordsAndNotes.error)

        val projects = parseProjects(root, exportedAtMs)
        val tasks = parseTasks(root, exportedAtMs, tagsResult.idRemap)
        val habits = parseHabits(root, exportedAtMs, tagsResult.idRemap)
        val habitLogs = parseHabitLogs(root, exportedAtMs)

        return BackupParseResult.Valid(
            ParsedBackupPayload(
                schemaVersion = schemaVersion,
                exportedAt = exportedAt,
                records = recordsAndNotes.records,
                budgets = budgetsResult.budgets,
                dailyReviews = reviewsResult.reviews,
                activityNotes = recordsAndNotes.notes,
                tags = tagsResult.tags,
                goals = goalsResult.goals,
                projects = projects,
                tasks = tasks,
                habits = habits,
                habitLogs = habitLogs
            )
        )
    }

    private fun parseProjects(root: JSONObject, fallbackNowMs: Long): List<Project> {
        val arr = root.optJSONArray("projects") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optNonBlankString("id") ?: continue
                val name = o.optNonBlankString("name") ?: continue
                add(
                    Project(
                        id = id,
                        name = name,
                        colorHex = o.optNullableLong("colorHex") ?: TimeCategory.WORK.colorHex,
                        archivedAt = o.optNullableLong("archivedAt"),
                        createdAt = o.optNullableLong("createdAt") ?: fallbackNowMs,
                        updatedAt = o.optNullableLong("updatedAt") ?: fallbackNowMs
                    )
                )
            }
        }
    }

    private fun parseTasks(root: JSONObject, fallbackNowMs: Long, tagIdRemap: Map<String, String>): List<TaskItem> {
        val arr = root.optJSONArray("tasks") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optNonBlankString("id") ?: continue
                val title = o.optNonBlankString("title") ?: continue
                val rawTag = o.optNonBlankString("tagId")
                val subsArr = o.optJSONArray("subtasks")
                val subs = if (subsArr != null) {
                    buildList {
                        for (j in 0 until subsArr.length()) {
                            val so = subsArr.optJSONObject(j) ?: continue
                            val sid = so.optNonBlankString("id") ?: continue
                            val stitle = so.optNonBlankString("title") ?: continue
                            add(
                                SubtaskItem(
                                    id = sid,
                                    taskId = id,
                                    title = stitle,
                                    isDone = so.optBoolean("isDone", false),
                                    sortOrder = so.optNullableInt("sortOrder") ?: j
                                )
                            )
                        }
                    }
                } else emptyList()
                add(
                    TaskItem(
                        id = id,
                        title = title,
                        notes = o.optNullableString("notes").orEmpty(),
                        projectId = o.optNonBlankString("projectId"),
                        tagId = rawTag?.let { tagIdRemap[it] ?: it },
                        category = TimeCategory.fromKeyOrDefault(o.optNonBlankString("category")),
                        priority = TaskPriority.fromStorage(o.optNonBlankString("priority")),
                        estimatedMinutes = o.optNullableInt("estimatedMinutes"),
                        dueDateLocal = o.optNonBlankString("dueDateLocal"),
                        dueTimeLocal = o.optNonBlankString("dueTimeLocal"),
                        status = TaskStatus.fromStorage(o.optNonBlankString("status")),
                        eisenhowerQuad = EisenhowerQuad.fromStorageOrNull(o.optNonBlankString("eisenhowerQuad")),
                        goalId = o.optNonBlankString("goalId"),
                        createdAt = o.optNullableLong("createdAt") ?: fallbackNowMs,
                        updatedAt = o.optNullableLong("updatedAt") ?: fallbackNowMs,
                        completedAt = o.optNullableLong("completedAt"),
                        subtasks = subs
                    )
                )
            }
        }
    }

    private fun parseHabits(root: JSONObject, fallbackNowMs: Long, tagIdRemap: Map<String, String>): List<Habit> {
        val arr = root.optJSONArray("habits") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optNonBlankString("id") ?: continue
                val title = o.optNonBlankString("title") ?: continue
                val rawTag = o.optNonBlankString("tagId")
                add(
                    Habit(
                        id = id,
                        title = title,
                        tagId = rawTag?.let { tagIdRemap[it] ?: it },
                        category = TimeCategory.fromKeyOrDefault(o.optNonBlankString("category")),
                        frequency = HabitFrequency.fromStorage(o.optNonBlankString("frequency")),
                        daysMask = o.optNullableInt("daysMask") ?: 0b1111111,
                        targetPerPeriod = o.optNullableInt("targetPerPeriod") ?: 1,
                        minSessionMinutes = o.optNullableInt("minSessionMinutes") ?: 15,
                        goalId = o.optNonBlankString("goalId"),
                        archivedAt = o.optNullableLong("archivedAt"),
                        createdAt = o.optNullableLong("createdAt") ?: fallbackNowMs,
                        updatedAt = o.optNullableLong("updatedAt") ?: fallbackNowMs
                    )
                )
            }
        }
    }

    private fun parseHabitLogs(root: JSONObject, fallbackNowMs: Long): List<HabitLog> {
        val arr = root.optJSONArray("habitLogs") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optNonBlankString("id") ?: continue
                val habitId = o.optNonBlankString("habitId") ?: continue
                val dateLocal = o.optNonBlankString("dateLocal") ?: continue
                add(
                    HabitLog(
                        id = id,
                        habitId = habitId,
                        dateLocal = dateLocal,
                        completed = o.optBoolean("completed", true),
                        linkedLedgerId = o.optNonBlankString("linkedLedgerId"),
                        updatedAt = o.optNullableLong("updatedAt") ?: fallbackNowMs
                    )
                )
            }
        }
    }

    private data class TagParseResult(
        val tags: List<Tag> = emptyList(),
        val idRemap: Map<String, String> = emptyMap(),
        val error: String? = null
    )

    private fun parseTags(root: JSONObject, fallbackNowMs: Long): TagParseResult {
        if (!root.has("tags") || root.isNull("tags")) return TagParseResult()
        val arr = root.opt("tags") as? JSONArray
            ?: return TagParseResult(error = "Trường \"tags\" không đúng định dạng mảng JSON.")
        val tags = mutableListOf<Tag>()
        val byNameKey = mutableMapOf<String, String>()
        val seenIds = mutableSetOf<String>()
        val remap = mutableMapOf<String, String>()
        for (i in 0 until arr.length()) {
            val obj = arr.opt(i) as? JSONObject
                ?: return TagParseResult(error = "Tag #$i không hợp lệ.")
            val id = obj.optNonBlankString("id") ?: return TagParseResult(error = "Tag #$i thiếu id.")
            val rawName = obj.optNonBlankString("name") ?: return TagParseResult(error = "Tag #$i thiếu tên.")
            val cleanName = TagRules.cleanName(rawName).take(TagRules.MAX_NAME_LENGTH)
            if (cleanName.isBlank()) return TagParseResult(error = "Tag #$i có tên trống.")
            val nameKey = obj.optNonBlankString("nameKey")?.let { TagRules.nameKey(it) }.takeUnless { it.isNullOrBlank() }
                ?: TagRules.nameKey(cleanName)
            val category = TimeCategory.fromKeyOrDefault(obj.optNonBlankString("category") ?: obj.optNonBlankString("categoryKey"))
            val archivedAt = obj.optNullableLong("archivedAt")
            val createdAt = obj.optNullableLong("createdAt") ?: fallbackNowMs
            val updatedAt = obj.optNullableLong("updatedAt") ?: createdAt

            val existingId = byNameKey[nameKey]
            if (existingId != null) {
                remap[id] = existingId
                continue
            }
            if (!seenIds.add(id)) {
                return TagParseResult(error = "Tag bị trùng id=$id trong tệp sao lưu.")
            }
            byNameKey[nameKey] = id
            tags += Tag(
                id = id,
                name = cleanName,
                nameKey = nameKey,
                category = category,
                archivedAt = archivedAt,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
        return TagParseResult(tags = tags, idRemap = remap)
    }

    private data class GoalParseResult(
        val goals: List<Goal> = emptyList(),
        val error: String? = null
    )

    private fun parseGoals(
        root: JSONObject,
        fallbackNowMs: Long,
        tagIdRemap: Map<String, String>
    ): GoalParseResult {
        if (!root.has("goals") || root.isNull("goals")) return GoalParseResult()
        val arr = root.opt("goals") as? JSONArray
            ?: return GoalParseResult(error = "Trường \"goals\" không đúng định dạng mảng JSON.")
        val goals = mutableListOf<Goal>()
        val seenIds = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.opt(i) as? JSONObject
                ?: return GoalParseResult(error = "Mục tiêu #$i không hợp lệ.")
            val id = obj.optNonBlankString("id") ?: return GoalParseResult(error = "Mục tiêu #$i thiếu id.")
            if (!seenIds.add(id)) return GoalParseResult(error = "Mục tiêu bị trùng id=$id.")
            val groupId = obj.optNonBlankString("groupId") ?: return GoalParseResult(error = "Mục tiêu #$i thiếu groupId.")
            val targetType = GoalTargetType.fromStorage(obj.optNonBlankString("targetType"))
            val rawTarget = obj.optNonBlankString("target") ?: obj.optNonBlankString("targetKey")
                ?: return GoalParseResult(error = "Mục tiêu #$i thiếu target.")
            val targetKey = if (targetType == GoalTargetType.TAG) tagIdRemap[rawTarget] ?: rawTarget else rawTarget
            val kind = GoalKind.fromStorage(obj.optNonBlankString("kind"))
            val period = GoalPeriod.fromStorage(obj.optNonBlankString("period"))
            val targetSeconds = obj.optNullableLong("targetSeconds")?.takeIf { it >= 60L }
                ?: return GoalParseResult(error = "Mục tiêu #$i có targetSeconds không hợp lệ.")
            val status = GoalVersionStatus.fromStorage(obj.optNonBlankString("status"))
            val validFrom = obj.optNonBlankString("validFrom")?.takeIf(::isValidLocalDate)
                ?: return GoalParseResult(error = "Mục tiêu #$i có validFrom không hợp lệ.")
            val validTo = obj.optNonBlankString("validTo")?.also {
                if (!isValidLocalDate(it)) return GoalParseResult(error = "Mục tiêu #$i có validTo không hợp lệ.")
            }
            val createdAt = obj.optNullableLong("createdAt") ?: fallbackNowMs
            val updatedAt = obj.optNullableLong("updatedAt") ?: createdAt
            goals += Goal(
                id = id,
                groupId = groupId,
                targetType = targetType,
                targetKey = targetKey,
                kind = kind,
                period = period,
                targetSeconds = targetSeconds,
                status = status,
                validFrom = validFrom,
                validTo = validTo,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isPriority = obj.optBoolean("isPriority", false),
                priorityOrder = obj.optNullableInt("priorityOrder") ?: 0
            )
        }
        return GoalParseResult(goals = goals)
    }

    private data class BudgetParseResult(
        val budgets: List<CategoryTimeBudget> = emptyList(),
        val error: String? = null
    )

    private fun parseBudgets(root: JSONObject, fallbackNowMs: Long): BudgetParseResult {
        if (!root.has("budgets") || root.isNull("budgets")) return BudgetParseResult()
        val arr = root.opt("budgets") as? JSONArray
            ?: return BudgetParseResult(error = "Trường \"budgets\" không đúng định dạng mảng JSON.")
        val budgetsByCategory = linkedMapOf<TimeCategory, CategoryTimeBudget>()
        for (i in 0 until arr.length()) {
            val obj = arr.opt(i) as? JSONObject
                ?: return BudgetParseResult(error = "Ngân sách #$i không hợp lệ.")
            val categoryKey = obj.optNonBlankString("category") ?: obj.optNonBlankString("categoryKey")
                ?: return BudgetParseResult(error = "Ngân sách #$i thiếu danh mục.")
            val category = TimeCategory.fromKeyOrDefault(categoryKey)
            val period = BudgetPeriod.fromStorageString(obj.optNonBlankString("period"))
            val targetSeconds = obj.optNullableLong("targetSeconds")?.takeIf { it in 60L..604_800L }
                ?: return BudgetParseResult(error = "Ngân sách #$i có targetSeconds không hợp lệ.")
            val isPriority = obj.optBoolean("isPriority", false)
            val priorityOrder = obj.optNullableInt("priorityOrder") ?: if (isPriority) i + 1 else 0
            val updatedAt = obj.optNullableLong("updatedAt") ?: fallbackNowMs
            budgetsByCategory[category] = CategoryTimeBudget(
                category = category,
                period = period,
                targetSeconds = targetSeconds,
                isPriority = isPriority,
                priorityOrder = priorityOrder,
                updatedAt = updatedAt
            )
        }
        return BudgetParseResult(budgets = budgetsByCategory.values.toList())
    }

    private data class ReviewParseResult(
        val reviews: List<DailyReviewRecord> = emptyList(),
        val error: String? = null
    )

    private fun parseDailyReviews(root: JSONObject, fallbackNowMs: Long): ReviewParseResult {
        if (!root.has("dailyReviews") || root.isNull("dailyReviews")) return ReviewParseResult()
        val arr = root.opt("dailyReviews") as? JSONArray
            ?: return ReviewParseResult(error = "Trường \"dailyReviews\" không đúng định dạng mảng JSON.")
        val reviewsByDate = linkedMapOf<String, DailyReviewRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.opt(i) as? JSONObject
                ?: return ReviewParseResult(error = "Phản tư ngày #$i không hợp lệ.")
            val dateLocal = obj.optNonBlankString("dateLocal")?.takeIf(::isValidLocalDate)
                ?: return ReviewParseResult(error = "Phản tư ngày #$i có dateLocal không hợp lệ.")
            reviewsByDate[dateLocal] = DailyReviewRecord(
                dateLocal = dateLocal,
                summaryText = obj.optNullableString("summaryText").orEmpty(),
                question1 = obj.optNullableString("question1").orEmpty(),
                question2 = obj.optNullableString("question2").orEmpty(),
                answer1 = obj.optNullableString("answer1").orEmpty(),
                answer2 = obj.optNullableString("answer2").orEmpty(),
                generalNote = obj.optNullableString("generalNote").orEmpty(),
                updatedAt = obj.optNullableLong("updatedAt") ?: fallbackNowMs
            )
        }
        return ReviewParseResult(reviews = reviewsByDate.values.toList())
    }

    private data class RecordsParseResult(
        val records: List<LedgerRecord> = emptyList(),
        val notes: List<ActivityReviewNote> = emptyList(),
        val error: String? = null
    )

    private fun parseRecords(
        arr: JSONArray,
        fallbackNowMs: Long,
        tagIdRemap: Map<String, String>
    ): RecordsParseResult {
        val records = mutableListOf<LedgerRecord>()
        val notes = mutableListOf<ActivityReviewNote>()
        val seenIds = mutableSetOf<String>()
        val seenIdemKeys = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.opt(i) as? JSONObject
                ?: return RecordsParseResult(error = "Bản ghi #$i không phải đối tượng JSON.")
            val id = obj.optNonBlankString("id") ?: return RecordsParseResult(error = "Bản ghi #$i thiếu id.")
            if (!seenIds.add(id)) return RecordsParseResult(error = "Bản ghi bị trùng id=$id trong tệp.")
            val title = obj.optNonBlankString("title")?.trim()
                ?: return RecordsParseResult(error = "Bản ghi #$i thiếu tên hoạt động.")
            val date = obj.optNonBlankString("date")?.takeIf(::isValidLocalDate)
                ?: return RecordsParseResult(error = "Bản ghi \"$title\" có ngày không hợp lệ.")
            val durationSeconds = obj.optNullableLong("durationSeconds")?.takeIf { it in 1L..86_400L }
                ?: return RecordsParseResult(error = "Bản ghi \"$title\" có durationSeconds không hợp lệ.")
            val startedAt = obj.optNullableLong("startedAt")
            val endedAt = obj.optNullableLong("endedAt")
            if ((startedAt == null) != (endedAt == null)) {
                return RecordsParseResult(error = "Bản ghi \"$title\" thiếu startedAt hoặc endedAt.")
            }
            if (startedAt != null && endedAt != null && endedAt <= startedAt) {
                return RecordsParseResult(error = "Bản ghi \"$title\" có endedAt <= startedAt.")
            }
            val rawZone = obj.optNonBlankString("timezone") ?: "Asia/Ho_Chi_Minh"
            if (runCatching { ZoneId.of(rawZone) }.isFailure) {
                return RecordsParseResult(error = "Bản ghi \"$title\" có timezone không hợp lệ.")
            }
            val status = (obj.optNonBlankString("status") ?: "CONFIRMED").uppercase()
            if (status !in SUPPORTED_STATUSES) {
                return RecordsParseResult(error = "Bản ghi \"$title\" có trạng thái $status không hợp lệ.")
            }
            val createdAt = obj.optNullableLong("createdAt") ?: startedAt ?: fallbackNowMs
            val userConfirmedAt = obj.optNullableLong("userConfirmedAt") ?: endedAt ?: createdAt
            val idempotencyKey = obj.optNonBlankString("idempotencyKey") ?: "import_$id"
            if (!seenIdemKeys.add(idempotencyKey)) {
                return RecordsParseResult(error = "Bản ghi bị trùng idempotencyKey trong tệp.")
            }

            val rawTagId = obj.optNonBlankString("tagId")
            val mappedTagId = rawTagId?.let { tagIdRemap[it] ?: it }
            val layer = TimeLayer.fromStorageString(obj.optNonBlankString("layer"), startedAt, endedAt)
            val record = LedgerRecord(
                id = id,
                source = obj.optNonBlankString("source") ?: "import",
                layer = layer,
                createdAt = createdAt,
                userConfirmedAt = userConfirmedAt,
                category = TimeCategory.fromKeyOrDefault(obj.optNonBlankString("category")),
                title = title,
                date = date,
                durationSeconds = durationSeconds,
                startedAt = startedAt,
                endedAt = endedAt,
                timezone = rawZone,
                status = status,
                idempotencyKey = idempotencyKey,
                tagId = mappedTagId,
                confirmationStateRaw = obj.optNonBlankString("confirmationState"),
                plannedStartedAt = obj.optNullableLong("plannedStartedAt"),
                plannedEndedAt = obj.optNullableLong("plannedEndedAt"),
                plannedDurationSeconds = obj.optNullableLong("plannedDurationSeconds"),
                overlapResolutionRaw = obj.optNonBlankString("overlapResolution"),
                relationGroupId = obj.optNonBlankString("relationGroupId"),
                overlapWithTitle = obj.optNonBlankString("overlapWithTitle"),
                isMarkedWaste = obj.optBoolean("isMarkedWaste", false),
                taskId = obj.optNonBlankString("taskId"),
                habitId = obj.optNonBlankString("habitId")
            )
            records += record

            val outcomeNote = obj.optNullableString("outcomeNote")
            val feynmanExplanation = obj.optNullableString("feynmanExplanation")
            val noteUpdatedAt = obj.optNullableLong("activityNoteUpdatedAt")
            if (outcomeNote != null || feynmanExplanation != null || noteUpdatedAt != null) {
                notes += ActivityReviewNote(
                    recordId = id,
                    dateLocal = date,
                    outcomeNote = outcomeNote.orEmpty(),
                    feynmanExplanation = feynmanExplanation.orEmpty(),
                    updatedAt = noteUpdatedAt ?: userConfirmedAt.takeIf { it > 0L } ?: fallbackNowMs
                )
            }
        }
        return RecordsParseResult(records = records, notes = notes)
    }

    private fun isValidLocalDate(raw: String): Boolean {
        if (raw.length != 10 || raw[4] != '-' || raw[7] != '-') return false
        if (!raw.indices.all { idx -> idx == 4 || idx == 7 || raw[idx] in '0'..'9' }) return false
        return runCatching { LocalDate.parse(raw) }.isSuccess
    }
}

object LedgerBackupRestorer {
    suspend fun restore(
        database: TimeWalletDatabase,
        payload: ParsedBackupPayload,
        mode: BackupRestoreMode,
        nowEpochMs: Long = System.currentTimeMillis()
    ): BackupRestoreSummary {
        return restoreWithBackend(
            backend = RoomBackupDatabaseBackend(database),
            payload = payload,
            mode = mode,
            nowEpochMs = nowEpochMs
        )
    }

    suspend fun restoreWithBackend(
        backend: BackupDatabaseBackend,
        payload: ParsedBackupPayload,
        mode: BackupRestoreMode,
        nowEpochMs: Long = System.currentTimeMillis()
    ): BackupRestoreSummary = LedgerConcurrencyGate.mutex.withLock {
        @Suppress("UNUSED_VARIABLE") val unusedNow = nowEpochMs
        if (mode == BackupRestoreMode.REPLACE_ALL) {
            validateNoIntraBackupConflicts(payload.records)
        }

        backend.runInTransaction {
            when (mode) {
                BackupRestoreMode.REPLACE_ALL -> applyReplaceAll(backend, payload)
                BackupRestoreMode.MERGE_SAFE -> applyMergeSafe(backend, payload)
            }
        }
    }

    private suspend fun applyReplaceAll(
        backend: BackupDatabaseBackend,
        payload: ParsedBackupPayload
    ): BackupRestoreSummary {
        backend.deleteAllActivityNotes()
        backend.deleteAllDailyReviews()
        backend.deleteAllBudgets()
        backend.deleteAllGoals()
        backend.deleteAllTags()
        backend.deleteAllRecords()
        backend.deleteAllHabitLogs()
        backend.deleteAllHabits()
        backend.deleteAllSubtasks()
        backend.deleteAllTasks()
        backend.deleteAllProjects()

        payload.tags.forEach { backend.insertTag(TagEntity.fromDomain(it)) }
        payload.goals.forEach { backend.upsertGoal(GoalEntity.fromDomain(it)) }
        payload.budgets.forEach { backend.upsertBudget(CategoryBudgetEntity.fromDomain(it)) }
        payload.dailyReviews.forEach { backend.upsertDailyReview(DailyReviewEntity.fromDomain(it)) }
        payload.projects.forEach { backend.upsertProject(ProjectEntity.fromDomain(it)) }
        payload.tasks.forEach { task ->
            backend.upsertTask(TaskEntity.fromDomain(task))
            if (task.subtasks.isNotEmpty()) {
                backend.upsertSubtasks(task.subtasks.map { SubtaskEntity.fromDomain(it) })
            }
        }
        payload.habits.forEach { backend.upsertHabit(HabitEntity.fromDomain(it)) }
        payload.habitLogs.forEach { backend.upsertHabitLog(HabitLogEntity.fromDomain(it)) }
        payload.records.forEach { backend.upsertRecord(LedgerRecordEntity.fromDomain(it)) }
        if (payload.activityNotes.isNotEmpty()) {
            backend.upsertActivityNotes(payload.activityNotes.map { ActivityReviewNoteEntity.fromDomain(it) })
        }

        return BackupRestoreSummary(
            mode = BackupRestoreMode.REPLACE_ALL,
            recordsInserted = payload.records.size,
            tagsInserted = payload.tags.size,
            goalsRestored = payload.goals.size,
            budgetsRestored = payload.budgets.size,
            dailyReviewsRestored = payload.dailyReviews.size,
            activityNotesRestored = payload.activityNotes.size,
            projectsRestored = payload.projects.size,
            tasksRestored = payload.tasks.size,
            habitsRestored = payload.habits.size,
            habitLogsRestored = payload.habitLogs.size
        )
    }

    private suspend fun applyMergeSafe(
        backend: BackupDatabaseBackend,
        payload: ParsedBackupPayload
    ): BackupRestoreSummary {
        val existingTags = backend.getAllTags().map { it.toDomain() }
        val existingTagIds = existingTags.map { it.id }.toMutableSet()
        val existingTagByKey = existingTags.associateBy { it.nameKey }.toMutableMap()
        val tagIdRemap = mutableMapOf<String, String>()
        var tagsInserted = 0
        var tagsRemapped = 0

        for (tag in payload.tags) {
            val matchByKey = existingTagByKey[tag.nameKey]
            when {
                tag.id in existingTagIds -> {
                    tagIdRemap[tag.id] = tag.id
                }
                matchByKey != null -> {
                    tagIdRemap[tag.id] = matchByKey.id
                    tagsRemapped++
                }
                else -> {
                    backend.insertTag(TagEntity.fromDomain(tag))
                    existingTagIds += tag.id
                    existingTagByKey[tag.nameKey] = tag
                    tagsInserted++
                }
            }
        }

        val existingGoalIds = backend.getAllGoals().map { it.id }.toMutableSet()
        var goalsRestored = 0
        for (goal in payload.goals) {
            if (goal.id in existingGoalIds) continue
            val mappedGoal = if (goal.targetType == GoalTargetType.TAG) {
                goal.copy(targetKey = tagIdRemap[goal.targetKey] ?: goal.targetKey)
            } else {
                goal
            }
            backend.upsertGoal(GoalEntity.fromDomain(mappedGoal))
            existingGoalIds += goal.id
            goalsRestored++
        }

        val existingBudgetCategories = backend.getAllBudgets().map { it.categoryKey }.toSet()
        var budgetsRestored = 0
        for (budget in payload.budgets) {
            if (budget.category.key in existingBudgetCategories) continue
            backend.upsertBudget(CategoryBudgetEntity.fromDomain(budget))
            budgetsRestored++
        }

        val existingReviewDates = backend.getAllDailyReviews().map { it.dateLocal }.toSet()
        var reviewsRestored = 0
        for (review in payload.dailyReviews) {
            if (review.dateLocal in existingReviewDates) continue
            backend.upsertDailyReview(DailyReviewEntity.fromDomain(review))
            reviewsRestored++
        }

        val existingRecords = backend.getAllRecords().map { it.toDomain() }.toMutableList()
        val existingRecordIds = existingRecords.map { it.id }.toMutableSet()
        val existingIdemKeys = existingRecords.map { it.idempotencyKey }.toMutableSet()
        val notesByRecordId = payload.activityNotes.associateBy { it.recordId }
        val notesToInsert = mutableListOf<ActivityReviewNoteEntity>()

        var recordsInserted = 0
        var skippedDuplicate = 0
        var skippedOverlap = 0

        for (rawRecord in payload.records) {
            val mappedRecord = rawRecord.copy(
                tagId = rawRecord.tagId?.let { tagIdRemap[it] ?: it }
            )
            if (mappedRecord.id in existingRecordIds || mappedRecord.idempotencyKey in existingIdemKeys) {
                skippedDuplicate++
                continue
            }
            val clash = findOverlapConflict(mappedRecord, existingRecords)
            if (clash != null) {
                skippedOverlap++
                continue
            }
            backend.upsertRecord(LedgerRecordEntity.fromDomain(mappedRecord))
            existingRecords += mappedRecord
            existingRecordIds += mappedRecord.id
            existingIdemKeys += mappedRecord.idempotencyKey
            recordsInserted++
            notesByRecordId[mappedRecord.id]?.let { note ->
                notesToInsert += ActivityReviewNoteEntity.fromDomain(note)
            }
        }

        if (notesToInsert.isNotEmpty()) {
            backend.upsertActivityNotes(notesToInsert)
        }

        val existingProjectIds = backend.getAllProjects().mapTo(HashSet()) { it.id }
        var projectsRestored = 0
        for (p in payload.projects) {
            if (p.id in existingProjectIds) continue
            backend.upsertProject(ProjectEntity.fromDomain(p))
            existingProjectIds += p.id
            projectsRestored++
        }

        val existingTaskIds = backend.getAllTasks().mapTo(HashSet()) { it.id }
        var tasksRestored = 0
        for (t in payload.tasks) {
            if (t.id in existingTaskIds) continue
            val mappedTask = t.copy(tagId = t.tagId?.let { tagIdRemap[it] ?: it })
            backend.upsertTask(TaskEntity.fromDomain(mappedTask))
            if (mappedTask.subtasks.isNotEmpty()) {
                backend.upsertSubtasks(mappedTask.subtasks.map { SubtaskEntity.fromDomain(it) })
            }
            existingTaskIds += mappedTask.id
            tasksRestored++
        }

        val existingHabitIds = backend.getAllHabits().mapTo(HashSet()) { it.id }
        var habitsRestored = 0
        for (h in payload.habits) {
            if (h.id in existingHabitIds) continue
            val mappedHabit = h.copy(tagId = h.tagId?.let { tagIdRemap[it] ?: it })
            backend.upsertHabit(HabitEntity.fromDomain(mappedHabit))
            existingHabitIds += mappedHabit.id
            habitsRestored++
        }

        val existingLogPairs = backend.getAllHabitLogs().mapTo(HashSet()) { "${it.habitId}|${it.dateLocal}" }
        var habitLogsRestored = 0
        for (l in payload.habitLogs) {
            if (!existingLogPairs.add("${l.habitId}|${l.dateLocal}")) continue
            backend.upsertHabitLog(HabitLogEntity.fromDomain(l))
            habitLogsRestored++
        }

        return BackupRestoreSummary(
            mode = BackupRestoreMode.MERGE_SAFE,
            recordsInserted = recordsInserted,
            recordsSkippedDuplicate = skippedDuplicate,
            recordsSkippedOverlap = skippedOverlap,
            tagsInserted = tagsInserted,
            tagsRemapped = tagsRemapped,
            goalsRestored = goalsRestored,
            budgetsRestored = budgetsRestored,
            dailyReviewsRestored = reviewsRestored,
            activityNotesRestored = notesToInsert.size,
            projectsRestored = projectsRestored,
            tasksRestored = tasksRestored,
            habitsRestored = habitsRestored,
            habitLogsRestored = habitLogsRestored
        )
    }

    private fun validateNoIntraBackupConflicts(records: List<LedgerRecord>) {
        val checked = mutableListOf<LedgerRecord>()
        for (record in records) {
            val clash = findOverlapConflict(record, checked)
            if (clash != null) {
                throw BackupRestoreConflictException(
                    "Bản sao lưu chứa hai bản ghi trùng giờ: \"${record.title}\" và \"${clash.title}\"."
                )
            }
            checked += record
        }
    }

    private fun isOverlapSensitiveTimed(record: LedgerRecord): Boolean =
        (record.layer == TimeLayer.MEASURED || record.layer == TimeLayer.REPORTED_TIMED || record.layer == TimeLayer.ACTIVE) &&
            !record.isCancelled &&
            !record.status.equals("PLANNED", ignoreCase = true) &&
            !record.isNeedsReconciliation &&
            record.startedAt != null &&
            record.endedAt != null

    private fun findOverlapConflict(incoming: LedgerRecord, existing: List<LedgerRecord>): LedgerRecord? {
        if (!isOverlapSensitiveTimed(incoming)) return null
        val startMs = incoming.startedAt ?: return null
        val endMs = incoming.endedAt ?: return null
        return existing.firstOrNull { other ->
            if (other.id == incoming.id) return@firstOrNull false
            if (!isOverlapSensitiveTimed(other)) return@firstOrNull false
            val sameParallelGroup = incoming.overlapResolution == OverlapResolution.PARALLEL_CONFIRMED &&
                other.overlapResolution == OverlapResolution.PARALLEL_CONFIRMED &&
                !incoming.relationGroupId.isNullOrBlank() &&
                incoming.relationGroupId == other.relationGroupId
            if (sameParallelGroup) return@firstOrNull false
            startMs < (other.endedAt ?: Long.MIN_VALUE) && endMs > (other.startedAt ?: Long.MAX_VALUE)
        }
    }
}

private fun JSONObject.optNonBlankString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val raw = opt(key) as? String ?: return null
    return raw.takeIf { it.isNotBlank() }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return opt(key) as? String
}

private fun JSONObject.optNullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    return if (value is Number) value.toLong() else null
}

private fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    return if (value is Number) value.toInt() else null
}
