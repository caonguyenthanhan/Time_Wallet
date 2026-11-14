package com.timewallet.mvp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReviewDao {

    @Query("SELECT * FROM daily_reviews ORDER BY dateLocal DESC")
    fun observeAllDailyReviews(): Flow<List<DailyReviewEntity>>

    @Query("SELECT * FROM daily_reviews ORDER BY dateLocal DESC")
    suspend fun getAllDailyReviewsSnapshot(): List<DailyReviewEntity>

    @Query("SELECT * FROM daily_reviews WHERE dateLocal = :dateLocal LIMIT 1")
    suspend fun getDailyReviewByDate(dateLocal: String): DailyReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyReview(entity: DailyReviewEntity)

    @Query("DELETE FROM daily_reviews")
    suspend fun deleteAllDailyReviews()

    @Query("SELECT * FROM activity_review_notes ORDER BY updatedAt DESC")
    fun observeAllActivityNotes(): Flow<List<ActivityReviewNoteEntity>>

    @Query("SELECT * FROM activity_review_notes ORDER BY updatedAt DESC")
    suspend fun getAllActivityNotesSnapshot(): List<ActivityReviewNoteEntity>

    @Query("SELECT * FROM activity_review_notes WHERE recordId = :recordId LIMIT 1")
    suspend fun getActivityNoteByRecordId(recordId: String): ActivityReviewNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivityNote(entity: ActivityReviewNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivityNotes(entities: List<ActivityReviewNoteEntity>)

    @Query("DELETE FROM activity_review_notes WHERE recordId = :recordId")
    suspend fun deleteActivityNoteByRecordId(recordId: String)

    @Query("DELETE FROM activity_review_notes")
    suspend fun deleteAllActivityNotes()
}
