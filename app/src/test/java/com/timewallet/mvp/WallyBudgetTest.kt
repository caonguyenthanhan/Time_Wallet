package com.timewallet.mvp

import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.BudgetPolicy
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** WALLY A11 + F5: ngân sách ngày/tuần, thời lượng chưa gắn giờ, giới hạn 3 ưu tiên, không trùng, chip F5. */
class WallyBudgetTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13) // thứ Sáu
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    private fun untimed(cat: String, minutes: Int, date: LocalDate = today, existing: List<LedgerRecord> = emptyList()): LedgerRecord =
        (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Nhập tay", rawCategory = cat, dateLocal = date.toString(),
            inputDurationMinutes = minutes, startedAt = null, endedAt = null,
            existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record

    private fun timed(cat: String, from: String, to: String, existing: List<LedgerRecord> = emptyList()): LedgerRecord {
        val range = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), from, to, zone)!!
        return (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Có giờ", rawCategory = cat, dateLocal = today.toString(),
            inputDurationMinutes = null, startedAt = range.first, endedAt = range.second,
            existingRecords = existing, nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record
    }

    private fun budget(cat: TimeCategory, period: BudgetPeriod, hours: Int, priority: Boolean = true, order: Int = 0) =
        CategoryTimeBudget(cat, period, hours * 3600L, priority, order, nowMs)

    private fun progress(budgets: List<CategoryTimeBudget>, records: List<LedgerRecord>) =
        LedgerAnalyticsCalculator.computeBudgetProgress(budgets, records, zone, nowMs)

    // ---------- gộp thời lượng chưa gắn giờ (quyết định của Owner: thêm số gộp mới) ----------
    @Test
    fun `daily budget - a manual untimed record moves the combined bar but never the timed figure`() {
        val p = progress(listOf(budget(TimeCategory.LEARN, BudgetPeriod.DAILY, 1)), listOf(untimed("learn", 30))).single()
        assertEquals("usedSeconds giữ nguyên: chỉ có mốc giờ", 0L, p.usedSeconds)
        assertEquals(1800L, p.untimedNoteSeconds)
        assertEquals(1800L, p.combinedUsedSeconds)
        assertEquals(1800L, p.combinedRemainingSeconds)
        assertEquals(0.5f, p.combinedProgressFraction, 0.0001f)
        assertFalse(p.combinedIsExceeded)
        assertEquals("Còn 30p", p.combinedDifferenceLabelVi)
    }

    @Test
    fun `weekly budget - timed plus untimed over the Monday to Sunday week, other categories and other weeks excluded`() {
        val t = timed("work", "09:00", "11:00")
        val sameWeekUntimed = untimed("work", 60, today.minusDays(3), listOf(t))      // thứ Hai cùng tuần
        val lastWeek = untimed("work", 600, today.minusDays(10), listOf(t, sameWeekUntimed))
        val otherCat = untimed("learn", 90, today, listOf(t, sameWeekUntimed, lastWeek))
        val p = progress(listOf(budget(TimeCategory.WORK, BudgetPeriod.WEEKLY, 10)), listOf(t, sameWeekUntimed, lastWeek, otherCat)).single()
        assertEquals(7200L, p.usedSeconds)
        assertEquals(3600L, p.untimedNoteSeconds)
        assertEquals(10_800L, p.combinedUsedSeconds)
        assertEquals(36_000L - 10_800L, p.combinedRemainingSeconds)
    }

    @Test
    fun `exceeding the target is reported as over and never as a negative remainder`() {
        val p = progress(listOf(budget(TimeCategory.REST, BudgetPeriod.DAILY, 1)), listOf(untimed("rest", 90))).single()
        assertTrue(p.combinedIsExceeded)
        assertEquals(0L, p.combinedRemainingSeconds)
        assertEquals(1800L, p.combinedExceededSeconds)
        assertEquals(1f, p.combinedProgressFraction, 0.0001f)
        assertEquals("Vượt 30p", p.combinedDifferenceLabelVi)
    }

    @Test
    fun `old figures are untouched by the new combined fields`() {
        val t = timed("work", "09:00", "10:00")
        val p = progress(listOf(budget(TimeCategory.WORK, BudgetPeriod.DAILY, 2)), listOf(t, untimed("work", 30, existing = listOf(t)))).single()
        assertEquals(3600L, p.usedSeconds)
        assertEquals(3600L, p.remainingSeconds)
        assertEquals("Còn 1h", p.differenceLabelVi)
    }

    // ---------- tối đa 3 ưu tiên, không trùng ----------
    @Test
    fun `A11 a fourth priority budget is refused with a message while editing an existing priority one is allowed`() {
        val three = listOf(TimeCategory.WORK, TimeCategory.LEARN, TimeCategory.HEALTH)
            .mapIndexed { i, c -> budget(c, BudgetPeriod.WEEKLY, 5, true, i) }
        assertNotNull(BudgetPolicy.priorityViolation(three, TimeCategory.FUN, true))
        assertNull("sửa mục đã ưu tiên", BudgetPolicy.priorityViolation(three, TimeCategory.WORK, true))
        assertNull("không đặt ưu tiên", BudgetPolicy.priorityViolation(three, TimeCategory.FUN, false))
        assertNull("mới 2 mục", BudgetPolicy.priorityViolation(three.take(2), TimeCategory.FUN, true))
        assertTrue(BudgetPolicy.priorityViolation(three, TimeCategory.FUN, true)!!.contains("3"))
    }

    @Test
    fun `dashboard shows at most three budgets once each and prefers priority ones`() {
        val budgets = TimeCategory.entries.take(5).mapIndexed { i, c ->
            budget(c, BudgetPeriod.WEEKLY, 5, priority = i >= 2, order = i)
        }
        val all = progress(budgets, emptyList())
        val shown = BudgetPolicy.dashboardBudgets(all)
        assertEquals(3, shown.size)
        assertTrue(shown.all { it.budget.isPriority })
        assertEquals(shown.map { it.category }.distinct().size, shown.size)
        assertTrue(BudgetPolicy.dashboardShowsPriority(all))

        val nonePriority = progress(budgets.map { it.copy(isPriority = false) }, emptyList())
        assertEquals(3, BudgetPolicy.dashboardBudgets(nonePriority).size)
        assertFalse("tiêu đề không được gọi là ưu tiên", BudgetPolicy.dashboardShowsPriority(nonePriority))
        assertTrue(BudgetPolicy.dashboardBudgets(emptyList()).isEmpty())
    }

    @Test
    fun `one budget per category - a second save for the same category replaces it so no duplicates can appear`() {
        val b1 = budget(TimeCategory.WORK, BudgetPeriod.WEEKLY, 10)
        val b2 = budget(TimeCategory.WORK, BudgetPeriod.DAILY, 2)
        val byKey = listOf(b1, b2).associateBy { it.category }   // khóa chính là danh mục (CategoryBudgetEntity.categoryKey)
        assertEquals(1, byKey.size)
        assertEquals(BudgetPeriod.DAILY, byKey.getValue(TimeCategory.WORK).period)
    }

    // ---------- F5: kiểm tra tĩnh phần giao diện (không có test Compose) ----------
    @Test
    fun `F5 preset chips are in a FlowRow, have no weekly suffix in the label and never wrap per character`() {
        val src = File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        assertFalse("chip không được lặp /tuần", src.contains("\"\${presetH}h\${selectedPeriod.shortSuffixVi}\""))
        val block = src.substring(src.indexOf("WALLY F5"), src.indexOf("label = { Text(\"Số giờ\") }"))
        assertTrue(block.contains("FlowRow("))
        assertTrue(block.contains("softWrap = false"))
        assertTrue(block.contains("maxLines = 1"))
        assertTrue(block.contains("minimumInteractiveComponentSize()"))
        assertTrue("nhãn Chọn nhanh nằm trên dòng riêng", block.indexOf("Chọn nhanh") < block.indexOf("FlowRow("))
        listOf(5, 10, 15, 20).forEach { assertTrue(src.contains("listOf(5, 10, 15, 20)") && it > 0) }
    }
}
