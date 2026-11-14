package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.ValidationResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** WALLY v1.0.6 — R02: câu hỏi ngân sách (metric BUDGET) dùng computeBudgetProgress chung, một vòng AI, không gửi sổ cái. */
class V106BudgetQueryTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13)
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun intent(category: String? = null, metric: String = "BUDGET", compare: Pair<String, String>? = null) =
        (AiQueryValidator.validate(metric, today.toString(), today.toString(), category, compare?.first, compare?.second, today)
            as AiQueryValidator.Result.Ok).intent

    private fun rec(cat: String, minutes: Int, existing: List<LedgerRecord> = emptyList()): LedgerRecord =
        (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "x", rawCategory = cat, dateLocal = today.toString(),
            inputDurationMinutes = minutes, startedAt = null, endedAt = null,
            existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record

    private fun b(cat: TimeCategory, period: BudgetPeriod, hours: Int, order: Int = 0) =
        CategoryTimeBudget(cat, period, hours * 3600L, true, order, nowMs)

    @Test
    fun `BUDGET answer reports the same recorded figure as computeBudgetProgress`() {
        val budgets = listOf(b(TimeCategory.LEARN, BudgetPeriod.DAILY, 2))
        val records = listOf(rec("learn", 90))
        val a = LedgerQueryEngine.execute(intent("learn"), records, zone, nowMs, null, budgets)
        val shared = LedgerAnalyticsCalculator.computeBudgetProgress(budgets, records, zone, nowMs).single()
        assertTrue(a.headline, a.headline.contains(shared.combinedUsedFormatted) && a.headline.contains("1h 30p"))
        assertTrue(a.headline.lowercase().contains("còn 30p"))
        assertEquals(1, a.lines.size)
        assertTrue(a.lines[0].contains(shared.recordedSummaryVi))
    }

    @Test
    fun `BUDGET with no budgets set says so and does not invent numbers`() {
        val a = LedgerQueryEngine.execute(intent(), listOf(rec("learn", 90)), zone, nowMs, null, emptyList())
        assertEquals("Bạn chưa đặt ngân sách nào.", a.headline)
        assertFalse(a.lines.joinToString(" ").contains("1h 30p"))
    }

    @Test
    fun `BUDGET for a category without a budget names the gap and lists existing budgets`() {
        val budgets = listOf(b(TimeCategory.WORK, BudgetPeriod.WEEKLY, 10))
        val a = LedgerQueryEngine.execute(intent("learn"), emptyList(), zone, nowMs, null, budgets)
        assertTrue(a.headline, a.headline.contains("chưa đặt ngân sách cho"))
        assertEquals(1, a.lines.size)
    }

    @Test
    fun `BUDGET with no records at all still answers (empty ledger is not a no-data dead end)`() {
        val budgets = listOf(b(TimeCategory.LEARN, BudgetPeriod.DAILY, 2))
        val a = LedgerQueryEngine.execute(intent("learn"), emptyList(), zone, nowMs, null, budgets)
        assertTrue(a.headline, a.headline.contains("đã ghi nhận 0p") && a.headline.contains("còn 2h"))
    }

    @Test
    fun `BUDGET exceeded and overall summary`() {
        val r1 = rec("learn", 150)
        val budgets = listOf(b(TimeCategory.LEARN, BudgetPeriod.DAILY, 2), b(TimeCategory.WORK, BudgetPeriod.WEEKLY, 10, 1))
        val a = LedgerQueryEngine.execute(intent(), listOf(r1), zone, nowMs, null, budgets)
        assertTrue(a.headline, a.headline.contains("2 danh mục") && a.headline.contains("1 đã vượt"))
        assertEquals(2, a.lines.size)
        assertTrue(a.lines.any { it.contains("Vượt 30p") })
    }

    @Test
    fun `BUDGET ignores a comparison period (no second computation) and says so`() {
        val budgets = listOf(b(TimeCategory.LEARN, BudgetPeriod.DAILY, 2))
        val a = LedgerQueryEngine.execute(intent("learn", compare = "2026-11-12" to "2026-11-12"), emptyList(), zone, nowMs, null, budgets)
        assertTrue(a.compareFigures == null)
        assertTrue(a.notes.any { it.contains("so sánh") })
    }

    @Test
    fun `BUDGET for a past period explains that budgets are always current`() {
        val yesterday = today.minusDays(1)
        val i = (AiQueryValidator.validate("BUDGET", yesterday.toString(), yesterday.toString(), "learn", null, null, today) as AiQueryValidator.Result.Ok).intent
        val a = LedgerQueryEngine.execute(i, emptyList(), zone, nowMs, null, listOf(b(TimeCategory.LEARN, BudgetPeriod.DAILY, 2)))
        assertTrue(a.notes.any { it.contains("hiện tại") })
    }

    @Test
    fun `the AI response with a BUDGET query parses and produces exactly one query and no second round is needed`() {
        val content = """{"reply":null,"proposals":[],"queries":[{"metric":"BUDGET","startDate":"$today","endDate":"$today","category":"learn"}]}"""
        val body = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()
        val out = GeminiProposalClient.parseAndValidateResponseJson(body, "Ngân sách học tập còn bao nhiêu?", today, LocalTime.of(18, 0))
        assertEquals(1, out.queries.size)
        assertEquals(QueryMetric.BUDGET, out.queries[0].metric)
    }

    @Test
    fun `the prompt documents BUDGET and the ledger and budget never go to the cloud`() {
        assertTrue(GeminiProposalClient.staticSystemPrompt.contains("BUDGET"))
        val client = File("src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt").readText()
        listOf("CategoryTimeBudget", "budgets", "LedgerRecord", "records").forEach {
            assertFalse("client không được đụng $it (không gửi sổ cái/ngân sách lên cloud)", client.contains(it))
        }
    }
}
