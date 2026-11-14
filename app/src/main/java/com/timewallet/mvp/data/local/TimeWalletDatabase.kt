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
        ActivityReviewNoteEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TimeWalletDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao
    abstract fun budgetDao(): BudgetDao
    abstract fun dailyReviewDao(): DailyReviewDao

    companion object {
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

        @Volatile
        private var INSTANCE: TimeWalletDatabase? = null

        fun getInstance(context: Context): TimeWalletDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeWalletDatabase::class.java,
                    "time_wallet_ledger.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
