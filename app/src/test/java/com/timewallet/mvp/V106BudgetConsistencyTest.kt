package com.timewallet.mvp

import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.DailyReviewAssistant
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * WALLY v1.0.6 — F-V105-02: "Ngân sách = thời gian thực tế ĐÃ GHI NHẬN (có mốc giờ + chưa gắn giờ, không PLANNED)" phải giống
 * nhau ở thẻ Tổng quan/Insights, câu phản tư Insights và bản tổng kết/câu hỏi của Nhật ký ngày.
 * Chỉ dùng API công khai có từ v1.0.5 để chạy RED trên bản cũ (lời văn phản tư/tổng kết chỉ nêu phần có mốc giờ).
 */
class V106BudgetConsistencyTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13)
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun timed(cat: String, from: String, to: String, existing: List<LedgerRecord> = emptyList()): LedgerRecord {
        val range = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), from, to, zone)!!
        return (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Có giờ", rawCategory = cat, dateLocal = today.toString(),
            inputDurationMinutes = null, startedAt = range.first, endedAt = range.second,
            existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record
    }

    private fun untimed(cat: String, minutes: Int, existing: List<LedgerRecord>): LedgerRecord =
        (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Nhập tay", rawCategory = cat, dateLocal = today.toString(),
            inputDurationMinutes = minutes, startedAt = null, endedAt = null,
            existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record

    private val budgets = listOf(CategoryTimeBudget(TimeCategory.LEARN, BudgetPeriod.DAILY, 2 * 3600L, true, 0, nowMs))

    // 1h có mốc giờ + 30p chưa gắn giờ => đã ghi nhận 1h30, còn 30p.
    private val t = timed("learn", "09:00", "10:00")
    private val records = listOf(t, untimed("learn", 30, listOf(t)))

    private val progress get() = LedgerAnalyticsCalculator.computeBudgetProgress(budgets, records, zone, nowMs).single()

    @Test
    fun `the card figure is timed plus untimed`() {
        assertEquals(5400L, progress.combinedUsedSeconds)
        assertEquals("Còn 30p", progress.combinedDifferenceLabelVi)
    }

    @Test
    fun `the Insights reflection states the same recorded figure and remainder as the card`() {
        val text = LedgerAnalyticsCalculator.buildNeutralBudgetReflection(listOf(progress), 3600L, 1800L)
        assertTrue("phải nêu 1h 30p: $text", text.contains("1h 30p"))
        assertTrue("phải nêu còn 30p: $text", text.lowercase().contains("còn 30p"))
        assertFalse("không được nói còn 1h (mức chỉ tính có mốc giờ): $text", text.lowercase().contains("còn 1h,") || text.lowercase().contains("còn 1h "))
    }

    @Test
    fun `the Daily Review summary and question state the same recorded figure and remainder as the card`() {
        val snap = DailyReviewAssistant.buildSnapshotForDate(
            dateLocal = today.toString(), records = records, budgets = budgets, activityNotes = emptyList(),
            savedReview = null, zoneId = zone, nowEpochMs = nowMs
        )
        val budgetText = (listOf(snap.generatedSummary) + snap.generatedQuestions).joinToString(" ").lowercase()
        assertTrue("phải nêu 1h 30p: $budgetText", budgetText.contains("1h 30p"))
        assertTrue("phải nêu còn 30p: $budgetText", budgetText.contains("còn 30p"))
        assertFalse("không được nêu còn 1h: $budgetText", budgetText.contains("còn 1h)") || budgetText.contains("còn 1h "))
    }

    @Test
    fun `PLANNED time never enters the recorded budget figure`() {
        val future = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "19:00", "21:00", zone)!!
        val planned = (LedgerValidator.validateAndBuildRecord(
            source = "chat", rawTitle = "Học tối", rawCategory = "learn", dateLocal = today.toString(),
            inputDurationMinutes = null, startedAt = future.first, endedAt = future.second,
            existingRecords = records, nowEpochMs = nowMs, zoneId = zone, convertFutureToPlanned = true
        ) as ValidationResult.Valid).record
        val p = LedgerAnalyticsCalculator.computeBudgetProgress(budgets, records + planned, zone, nowMs).single()
        assertEquals(5400L, p.combinedUsedSeconds)
        assertEquals(7200L, p.plannedSeconds)
    }

    @Test
    fun `UI source states one definition and no longer calls the budget used-only-timed`() {
        val insights = java.io.File("src/main/java/com/timewallet/mvp/ui/screens/InsightsScreen.kt").readText()
        assertFalse(insights.contains("Chỉ thời gian thực tế hợp lệ có mốc giờ mới được tính vào mức đã sử dụng"))
        val card = java.io.File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        assertTrue(card.contains("Đã ghi nhận: \${progress.combinedUsedFormatted}"))
    }

    // ---------------------------------------------------------------- R02: câu hỏi ngân sách do AI yêu cầu
    @Test
    fun `R02 the validator accepts a BUDGET query for today`() {
        val r = AiQueryValidator.validate("BUDGET", today.toString(), today.toString(), "learn", null, null, today)
        assertTrue("v1.0.5 từ chối metric BUDGET: $r", r is AiQueryValidator.Result.Ok)
    }
}
