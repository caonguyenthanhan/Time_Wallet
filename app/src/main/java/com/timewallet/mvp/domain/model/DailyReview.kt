package com.timewallet.mvp.domain.model

import com.timewallet.mvp.domain.usecase.DayLedgerStats

/**
 * Ghi chú kết quả / phản tư tùy chọn cho từng hoạt động đã hoàn thành trong ngày.
 * Nếu hoạt động thuộc danh mục Học tập (LEARN), hỗ trợ thêm câu trả lời Feynman.
 */
data class ActivityReviewNote(
    val recordId: String,
    val dateLocal: String,
    val outcomeNote: String = "",
    val feynmanExplanation: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Bản lưu Daily Review cục bộ theo ngày (YYYY-MM-DD).
 * Chứa bản tổng kết ngắn, tối đa 2 câu hỏi phản tư từ dữ liệu thật và câu trả lời/ghi chú của người dùng.
 */
data class DailyReviewRecord(
    val dateLocal: String,
    val summaryText: String,
    val question1: String,
    val question2: String = "",
    val answer1: String = "",
    val answer2: String = "",
    val generalNote: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val questions: List<String>
        get() = listOf(question1, question2).filter { it.isNotBlank() }.take(2)
}

/**
 * Mô hình tổng hợp Daily Review cho một ngày cụ thể từ dữ liệu Ledger thật.
 */
data class DailyReviewSnapshot(
    val dateLocal: String,
    val dayStats: DayLedgerStats,
    val budgetProgressList: List<TimeBudgetProgress>,
    val completedActivities: List<LedgerRecord>,
    val plannedOrActiveActivities: List<LedgerRecord>,
    val activityNotesMap: Map<String, ActivityReviewNote>,
    val generatedSummary: String,
    val generatedQuestions: List<String>,
    val savedReview: DailyReviewRecord?
) {
    val learningActivities: List<LedgerRecord>
        get() = completedActivities.filter { it.category == TimeCategory.LEARN }

    val hasLearningActivity: Boolean
        get() = learningActivities.isNotEmpty()

    val effectiveSummary: String
        get() = savedReview?.summaryText?.takeIf { it.isNotBlank() } ?: generatedSummary

    val effectiveQuestions: List<String>
        get() = (savedReview?.questions?.takeIf { it.isNotEmpty() } ?: generatedQuestions).take(2)

    companion object {
        const val FEYNMAN_QUESTION_VI =
            "Bạn sẽ giải thích điều vừa học cho người chưa biết như thế nào?"
    }
}

