package com.timewallet.mvp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.timewallet.mvp.domain.model.ActivityReviewNote
import com.timewallet.mvp.domain.model.DailyReviewRecord

@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    @PrimaryKey val dateLocal: String,
    val summaryText: String,
    val question1: String,
    val question2: String,
    val answer1: String,
    val answer2: String,
    val generalNote: String,
    val updatedAt: Long
) {
    fun toDomain(): DailyReviewRecord {
        return DailyReviewRecord(
            dateLocal = dateLocal,
            summaryText = summaryText,
            question1 = question1,
            question2 = question2,
            answer1 = answer1,
            answer2 = answer2,
            generalNote = generalNote,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(record: DailyReviewRecord): DailyReviewEntity {
            return DailyReviewEntity(
                dateLocal = record.dateLocal,
                summaryText = record.summaryText,
                question1 = record.question1,
                question2 = record.question2,
                answer1 = record.answer1,
                answer2 = record.answer2,
                generalNote = record.generalNote,
                updatedAt = record.updatedAt
            )
        }
    }
}

@Entity(tableName = "activity_review_notes")
data class ActivityReviewNoteEntity(
    @PrimaryKey val recordId: String,
    val dateLocal: String,
    val outcomeNote: String,
    val feynmanExplanation: String,
    val updatedAt: Long
) {
    fun toDomain(): ActivityReviewNote {
        return ActivityReviewNote(
            recordId = recordId,
            dateLocal = dateLocal,
            outcomeNote = outcomeNote,
            feynmanExplanation = feynmanExplanation,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(note: ActivityReviewNote): ActivityReviewNoteEntity {
            return ActivityReviewNoteEntity(
                recordId = note.recordId,
                dateLocal = note.dateLocal,
                outcomeNote = note.outcomeNote,
                feynmanExplanation = note.feynmanExplanation,
                updatedAt = note.updatedAt
            )
        }
    }
}

