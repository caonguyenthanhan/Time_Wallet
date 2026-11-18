package com.timewallet.mvp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LedgerRecordEntity::class,
        CategoryBudgetEntity::class,
        DailyReviewEntity::class,
        ActivityReviewNoteEntity::class,
        TagEntity::class,
        GoalEntity::class,
        ProjectEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        HabitEntity::class,
        HabitLogEntity::class
    ],
    version = 7,
    exportSchema = true   // v1.1.0+: schema JSON trong app/schemas để test migration đối chiếu DDL với Room thật
)
abstract class TimeWalletDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao
    abstract fun budgetDao(): BudgetDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun tagDao(): TagDao
    abstract fun goalDao(): GoalDao
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao

    companion object {
        // v1.1.0 (3 -> 4). DDL phải KHỚP CHÍNH XÁC cái Room kỳ vọng (đã đối chiếu với schemas/…/4.json bằng test).
        const val SQL_ADD_LEDGER_TAG_ID = "ALTER TABLE `ledger_records` ADD COLUMN `tagId` TEXT"
        const val SQL_CREATE_LEDGER_TAG_ID_INDEX = "CREATE INDEX IF NOT EXISTS `index_ledger_records_tagId` ON `ledger_records` (`tagId`)"

        const val SQL_CREATE_TAGS_TABLE = """
            CREATE TABLE IF NOT EXISTS `tags` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `nameKey` TEXT NOT NULL,
                `categoryKey` TEXT NOT NULL,
                `archivedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """
        const val SQL_CREATE_TAGS_NAMEKEY_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_nameKey` ON `tags` (`nameKey`)"

        const val SQL_CREATE_GOALS_TABLE = """
            CREATE TABLE IF NOT EXISTS `goals` (
                `id` TEXT NOT NULL,
                `groupId` TEXT NOT NULL,
                `targetType` TEXT NOT NULL,
                `targetKey` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `period` TEXT NOT NULL,
                `targetSeconds` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `validFrom` TEXT NOT NULL,
                `validTo` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """
        const val SQL_CREATE_GOALS_GROUP_INDEX = "CREATE INDEX IF NOT EXISTS `index_goals_groupId` ON `goals` (`groupId`)"

        const val SQL_CREATE_CATEGORY_BUDGETS_TABLE = """
            CREATE TABLE IF NOT EXISTS `category_budgets` (
                `categoryKey` TEXT NOT NULL,
                `period` TEXT NOT NULL,
                `targetSeconds` INTEGER NOT NULL,
                `isPriority` INTEGER NOT NULL,
                `priorityOrder` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`categoryKey`)
            )
        """

        const val SQL_MIGRATE_LEGACY_UNPLACED_LAYER = """
            UPDATE `ledger_records`
            SET `layer` = 'REPORTED_UNTIMED'
            WHERE `layer` = 'REPORTED_UNPLACED'
        """

        const val SQL_CREATE_DAILY_REVIEWS_TABLE = """
            CREATE TABLE IF NOT EXISTS `daily_reviews` (
                `dateLocal` TEXT NOT NULL,
                `summaryText` TEXT NOT NULL,
                `question1` TEXT NOT NULL,
                `question2` TEXT NOT NULL,
                `answer1` TEXT NOT NULL,
                `answer2` TEXT NOT NULL,
                `generalNote` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`dateLocal`)
            )
        """

        const val SQL_CREATE_ACTIVITY_REVIEW_NOTES_TABLE = """
            CREATE TABLE IF NOT EXISTS `activity_review_notes` (
                `recordId` TEXT NOT NULL,
                `dateLocal` TEXT NOT NULL,
                `outcomeNote` TEXT NOT NULL,
                `feynmanExplanation` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`recordId`)
            )
        """

        /**
         * Nâng cấp CSDL từ phiên bản 1 lên phiên bản 2:
         * - Tạo bảng `category_budgets` phục vụ tính năng Time Budget.
         * - Chuẩn hóa các dòng `REPORTED_UNPLACED` cũ sang `REPORTED_UNTIMED` mà vẫn giữ nguyên 100% dữ liệu.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_CREATE_CATEGORY_BUDGETS_TABLE.trimIndent())
                db.execSQL(SQL_MIGRATE_LEGACY_UNPLACED_LAYER.trimIndent())
            }
        }

        /**
         * Nâng cấp CSDL từ phiên bản 2 lên phiên bản 3:
         * - Tạo bảng `daily_reviews` và `activity_review_notes` phục vụ tính năng Daily Review.
         * - Bảo toàn 100% dữ liệu trong `ledger_records` và `category_budgets`.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_CREATE_DAILY_REVIEWS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_ACTIVITY_REVIEW_NOTES_TABLE.trimIndent())
            }
        }

        /**
         * Nâng cấp CSDL từ phiên bản 3 lên 4 (v1.1.0), chỉ THÊM, không phá hủy:
         * - `ledger_records.tagId` (NULL cho mọi bản ghi cũ = chưa gắn Tag; không suy diễn lại) + chỉ mục.
         * - Bảng `tags` và `goals`.
         * Bảo toàn 100% `ledger_records`, `category_budgets`, `daily_reviews`, `activity_review_notes`.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_ADD_LEDGER_TAG_ID)
                db.execSQL(SQL_CREATE_LEDGER_TAG_ID_INDEX)
                db.execSQL(SQL_CREATE_TAGS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_TAGS_NAMEKEY_INDEX)
                db.execSQL(SQL_CREATE_GOALS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_GOALS_GROUP_INDEX)
            }
        }

        // v1.2.0 (4 -> 5): trạng thái xác nhận, giữ kế hoạch gốc và phân giải chồng lấn/khoảng nghỉ (chỉ THÊM, không phá hủy).
        const val SQL_ADD_CONFIRMATION_STATE = "ALTER TABLE `ledger_records` ADD COLUMN `confirmationState` TEXT"
        const val SQL_ADD_PLANNED_STARTED_AT = "ALTER TABLE `ledger_records` ADD COLUMN `plannedStartedAt` INTEGER"
        const val SQL_ADD_PLANNED_ENDED_AT = "ALTER TABLE `ledger_records` ADD COLUMN `plannedEndedAt` INTEGER"
        const val SQL_ADD_PLANNED_DURATION_SECONDS = "ALTER TABLE `ledger_records` ADD COLUMN `plannedDurationSeconds` INTEGER"
        const val SQL_ADD_OVERLAP_RESOLUTION = "ALTER TABLE `ledger_records` ADD COLUMN `overlapResolution` TEXT"
        const val SQL_ADD_RELATION_GROUP_ID = "ALTER TABLE `ledger_records` ADD COLUMN `relationGroupId` TEXT"
        const val SQL_ADD_OVERLAP_WITH_TITLE = "ALTER TABLE `ledger_records` ADD COLUMN `overlapWithTitle` TEXT"

        /**
         * Nâng cấp CSDL từ phiên bản 4 lên 5: thêm metadata xác nhận & chồng lấn (nullable, giữ nguyên 100% dữ liệu cũ).
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_ADD_CONFIRMATION_STATE)
                db.execSQL(SQL_ADD_PLANNED_STARTED_AT)
                db.execSQL(SQL_ADD_PLANNED_ENDED_AT)
                db.execSQL(SQL_ADD_PLANNED_DURATION_SECONDS)
                db.execSQL(SQL_ADD_OVERLAP_RESOLUTION)
                db.execSQL(SQL_ADD_RELATION_GROUP_ID)
                db.execSQL(SQL_ADD_OVERLAP_WITH_TITLE)
            }
        }

        // v1.3.0 (5 -> 6): cờ đánh dấu lãng phí theo chính lựa chọn người dùng (NOT NULL DEFAULT 0).
        const val SQL_ADD_IS_MARKED_WASTE =
            "ALTER TABLE `ledger_records` ADD COLUMN `isMarkedWaste` INTEGER NOT NULL DEFAULT 0"

        /**
         * Nâng cấp CSDL từ phiên bản 5 lên 6: tách "Phi mục tiêu" khỏi "Lãng phí" trên biểu đồ hiệu quả 24h.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_ADD_IS_MARKED_WASTE)
            }
        }

        // v2.0 (6 -> 7): liên kết Task/Habit, hợp nhất Goal+Budget và bổ sung 5 bảng lõi mới (không xóa dữ liệu cũ).
        const val SQL_ADD_LEDGER_TASK_ID = "ALTER TABLE `ledger_records` ADD COLUMN `taskId` TEXT"
        const val SQL_ADD_LEDGER_HABIT_ID = "ALTER TABLE `ledger_records` ADD COLUMN `habitId` TEXT"
        const val SQL_CREATE_LEDGER_TASK_ID_INDEX = "CREATE INDEX IF NOT EXISTS `index_ledger_records_taskId` ON `ledger_records` (`taskId`)"
        const val SQL_CREATE_LEDGER_HABIT_ID_INDEX = "CREATE INDEX IF NOT EXISTS `index_ledger_records_habitId` ON `ledger_records` (`habitId`)"
        const val SQL_ADD_GOAL_IS_PRIORITY = "ALTER TABLE `goals` ADD COLUMN `isPriority` INTEGER NOT NULL DEFAULT 0"
        const val SQL_ADD_GOAL_PRIORITY_ORDER = "ALTER TABLE `goals` ADD COLUMN `priorityOrder` INTEGER NOT NULL DEFAULT 0"

        const val SQL_CREATE_PROJECTS_TABLE = """
            CREATE TABLE IF NOT EXISTS `projects` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `colorHex` INTEGER NOT NULL,
                `archivedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """

        const val SQL_CREATE_TASKS_TABLE = """
            CREATE TABLE IF NOT EXISTS `tasks` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `projectId` TEXT,
                `tagId` TEXT,
                `categoryKey` TEXT NOT NULL,
                `priority` TEXT NOT NULL,
                `estimatedMinutes` INTEGER,
                `dueDateLocal` TEXT,
                `dueTimeLocal` TEXT,
                `status` TEXT NOT NULL,
                `eisenhowerQuad` TEXT,
                `goalId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `completedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
        """
        const val SQL_CREATE_TASKS_STATUS_INDEX = "CREATE INDEX IF NOT EXISTS `index_tasks_status` ON `tasks` (`status`)"
        const val SQL_CREATE_TASKS_DUE_INDEX = "CREATE INDEX IF NOT EXISTS `index_tasks_dueDateLocal` ON `tasks` (`dueDateLocal`)"
        const val SQL_CREATE_TASKS_PROJECT_INDEX = "CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)"

        const val SQL_CREATE_SUBTASKS_TABLE = """
            CREATE TABLE IF NOT EXISTS `subtasks` (
                `id` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `isDone` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """
        const val SQL_CREATE_SUBTASKS_TASK_INDEX = "CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)"

        const val SQL_CREATE_HABITS_TABLE = """
            CREATE TABLE IF NOT EXISTS `habits` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `tagId` TEXT,
                `categoryKey` TEXT NOT NULL,
                `frequencyType` TEXT NOT NULL,
                `daysMask` INTEGER NOT NULL,
                `targetPerPeriod` INTEGER NOT NULL,
                `minSessionMinutes` INTEGER NOT NULL,
                `goalId` TEXT,
                `archivedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """
        const val SQL_CREATE_HABITS_TAG_INDEX = "CREATE INDEX IF NOT EXISTS `index_habits_tagId` ON `habits` (`tagId`)"

        const val SQL_CREATE_HABIT_LOGS_TABLE = """
            CREATE TABLE IF NOT EXISTS `habit_logs` (
                `id` TEXT NOT NULL,
                `habitId` TEXT NOT NULL,
                `dateLocal` TEXT NOT NULL,
                `completed` INTEGER NOT NULL,
                `linkedLedgerId` TEXT,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """
        const val SQL_CREATE_HABIT_LOGS_UNIQUE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_logs_habitId_dateLocal` ON `habit_logs` (`habitId`, `dateLocal`)"

        const val SQL_MIGRATE_BUDGETS_TO_UNIFIED_GOALS = """
            INSERT OR IGNORE INTO `goals` (
                `id`, `groupId`, `targetType`, `targetKey`, `kind`, `period`,
                `targetSeconds`, `status`, `validFrom`, `validTo`, `createdAt`, `updatedAt`,
                `isPriority`, `priorityOrder`
            )
            SELECT
                'budget_migrated_' || `categoryKey`,
                'grp_budget_' || `categoryKey`,
                'CATEGORY',
                `categoryKey`,
                'MAX',
                CASE WHEN UPPER(`period`) = 'DAY' THEN 'DAY' ELSE 'WEEK' END,
                CASE WHEN `targetSeconds` < 60 THEN 60 ELSE `targetSeconds` END,
                'ACTIVE',
                '1970-01-01',
                NULL,
                `updatedAt`,
                `updatedAt`,
                CASE WHEN `isPriority` != 0 THEN 1 ELSE 0 END,
                `priorityOrder`
            FROM `category_budgets`
            WHERE NOT EXISTS (
                SELECT 1 FROM `goals` g
                WHERE g.`targetType` = 'CATEGORY'
                  AND g.`targetKey` = `category_budgets`.`categoryKey`
                  AND g.`kind` = 'MAX'
                  AND g.`validTo` IS NULL
            )
        """

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_ADD_LEDGER_TASK_ID)
                db.execSQL(SQL_ADD_LEDGER_HABIT_ID)
                db.execSQL(SQL_CREATE_LEDGER_TASK_ID_INDEX)
                db.execSQL(SQL_CREATE_LEDGER_HABIT_ID_INDEX)
                db.execSQL(SQL_ADD_GOAL_IS_PRIORITY)
                db.execSQL(SQL_ADD_GOAL_PRIORITY_ORDER)
                db.execSQL(SQL_CREATE_PROJECTS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_TASKS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_TASKS_STATUS_INDEX)
                db.execSQL(SQL_CREATE_TASKS_DUE_INDEX)
                db.execSQL(SQL_CREATE_TASKS_PROJECT_INDEX)
                db.execSQL(SQL_CREATE_SUBTASKS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_SUBTASKS_TASK_INDEX)
                db.execSQL(SQL_CREATE_HABITS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_HABITS_TAG_INDEX)
                db.execSQL(SQL_CREATE_HABIT_LOGS_TABLE.trimIndent())
                db.execSQL(SQL_CREATE_HABIT_LOGS_UNIQUE_INDEX)
                db.execSQL(SQL_MIGRATE_BUDGETS_TO_UNIFIED_GOALS.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: TimeWalletDatabase? = null

        fun getInstance(context: Context): TimeWalletDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeWalletDatabase::class.java,
                    "time_wallet_ledger.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
