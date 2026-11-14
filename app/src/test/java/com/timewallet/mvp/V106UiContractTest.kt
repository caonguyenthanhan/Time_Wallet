package com.timewallet.mvp

import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.BudgetPolicy
import com.timewallet.mvp.domain.usecase.DerivedStatsCalculator
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * WALLY v1.0.6 — nhóm C: hộp thoại cuộn được, vùng chạm >= 48dp + nhãn trợ năng, tính toán nặng không ở luồng chính,
 * thu thập state theo vòng đời, vòng cập nhật đồng hồ không chạy ngầm.
 * Phần tĩnh chỉ kiểm CẤU TRÚC mã nguồn (không chứng minh hiển thị trên thiết bị: vẫn NOT_TESTED, xem checklist thiết bị).
 */
class V106UiContractTest {

    private val srcRoot = File("src/main/java/com/timewallet/mvp")
    private fun text(rel: String) = File(srcRoot, rel).readText()

    // ---------------------------------------------------------------- vùng chạm + nhãn
    @Test
    fun `every custom clickable has a 48dp minimum and a role or label`() {
        val offenders = mutableListOf<String>()
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val lines = f.readLines()
            lines.forEachIndexed { i, l ->
                if (Regex("""\.clickable\b""").containsMatchIn(l) && !l.trim().startsWith("import")) {
                    val window = lines.subList(maxOf(0, i - 12), i + 1).joinToString("\n")
                    val sized = window.contains("minimumInteractiveComponentSize") ||
                        Regex("""heightIn\(min = (4[89]|[5-9]\d)\.dp""").containsMatchIn(window)
                    val labelled = window.contains("contentDescription") || window.contains("Role.") || window.contains("role =")
                    if (!(sized && labelled)) offenders += "${f.name}:${i + 1} (${if (!sized) "size " else ""}${if (!labelled) "label" else ""})"
                }
            }
        }
        assertTrue("vùng chạm nhỏ hoặc thiếu nhãn: $offenders", offenders.isEmpty())
    }

    @Test
    fun `glyph-only controls carry a spoken label and text-bearing containers keep their own text for screen readers`() {
        val chat = text("ui/screens/ChatScreen.kt")
        // Chỉ biểu tượng (×, ✎/▲): contentDescription là cách duy nhất để đọc được.
        listOf("Bỏ đề xuất này", "Chỉnh sửa hoặc thu gọn đề xuất").forEach {
            assertTrue("thiếu nhãn \"$it\"", chat.contains("contentDescription = \"$it\""))
        }
        // Khung có chữ riêng: contentDescription trên nút gộp sẽ THAY chữ đó khi đọc; dùng nhãn hành động (onClickLabel).
        val textBearing = mapOf(
            "ui/screens/ChatScreen.kt" to listOf("Mở cấu hình AI", "Nhập lại khóa API trong cấu hình", "Bỏ hết đề xuất đang chờ", "Đóng câu trả lời"),
            "ui/screens/ComponentsAndDialogs.kt" to listOf("Mở tab Đồng hồ đang chạy", "Sửa bản ghi"),
            "MainActivity.kt" to listOf("Đóng thông báo"),
            "ui/screens/DailyReviewScreen.kt" to listOf("Xem ngày trước", "Xem ngày sau", "Lưu ghi chú kết quả")
        )
        textBearing.forEach { (file, labels) ->
            val src = text(file)
            labels.forEach { label ->
                assertTrue("$file thiếu onClickLabel \"$label\"", src.contains("onClickLabel = \"$label"))
                assertFalse("$file: contentDescription \"$label\" sẽ thay chữ hiển thị khi đọc", src.contains("contentDescription = \"$label"))
            }
        }
    }

    // ---------------------------------------------------------------- hộp thoại
    private fun dialogBody(name: String): String {
        val s = text("ui/screens/ComponentsAndDialogs.kt")
        val from = s.indexOf("fun $name(")
        assertTrue("không thấy $name", from > 0)
        val next = s.indexOf("\n@Composable", from)
        return s.substring(from, if (next > 0) next else s.length)
    }

    @Test
    fun `settings and info dialogs scroll so every field and the action buttons stay reachable`() {
        listOf("AiSettingsDialog", "ProductInfoDialog", "EditRecordDialog", "ExportJsonDialog", "TimeBudgetEditorDialog").forEach {
            assertTrue("$it phải có verticalScroll", dialogBody(it).contains("verticalScroll(rememberScrollState())"))
        }
        // Nút hành động nằm ngoài phần cuộn (confirmButton/dismissButton của AlertDialog), không bị đẩy khỏi màn hình khi mở bàn phím.
        val ai = dialogBody("AiSettingsDialog")
        assertTrue(ai.indexOf("verticalScroll") < ai.indexOf("confirmButton"))
        assertTrue(ai.contains("keyboardType = KeyboardType.Password"))
    }

    // ---------------------------------------------------------------- luồng chính / vòng đời
    @Test
    fun `state is collected with the lifecycle and the ticker only runs while the app is visible`() {
        val main = text("MainActivity.kt")
        assertTrue(main.contains("collectAsStateWithLifecycle()"))
        assertFalse("không còn collectAsState() không gắn vòng đời", Regex("""[^A-Za-z]collectAsState\(\)""").containsMatchIn(main))
        assertTrue(main.contains("Lifecycle.Event.ON_PAUSE") && main.contains("viewModel.onAppPaused()"))
        val vm = text("ui/TimeWalletViewModel.kt")
        val init = vm.substring(vm.indexOf("    init {"), vm.indexOf("private var tickerJob"))
        assertFalse("init không được tự chạy vòng lặp đồng hồ vô hạn", init.contains("refreshTimerTick()"))
        assertTrue(vm.contains("fun onAppPaused()") && vm.contains("tickerJob?.cancel()"))
        assertTrue(vm.contains("startTicker()"))
    }

    @Test
    fun `derived statistics are computed off the main thread with latest-wins and no heavy compute inside the state update`() {
        val vm = text("ui/TimeWalletViewModel.kt")
        val body = vm.substring(vm.indexOf("private fun recomputeStats("), vm.indexOf("fun onAppResumed()"))
        assertTrue(body.contains("withContext(Dispatchers.Default)"))
        assertTrue(body.contains("DerivedStatsCalculator.compute("))
        assertTrue(body.contains("recomputeSeq") && body.contains("recomputeJob?.cancel()"))
        // Trong khối _uiState.update của kết quả dẫn xuất không còn lời gọi tính toán nặng.
        val publish = body.substring(body.indexOf("if (seq != recomputeSeq.get()) return@launch"))
        listOf("computeDayStats", "computeWeeklyInsights", "buildSnapshotForDate", "deriveContextFromRecords").forEach {
            assertFalse("$it không được chạy trong update trạng thái", publish.contains(it))
        }
    }

    @Test
    fun `the chat runner never calls the query engine from the state update lambda`() {
        val runner = text("ui/WallyChatRunner.kt")
        val updateBlock = runner.substring(runner.indexOf("state.update { s ->"), runner.indexOf("when (step)"))
        assertFalse(updateBlock.contains("computeAnswers"))
        assertFalse(updateBlock.contains("LedgerQueryEngine"))
        assertTrue(runner.contains("withContext(computeContext)"))
    }

    // ---------------------------------------------------------------- hàm thuần dẫn xuất: kết quả khớp phép tính trực tiếp
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 13)
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `DerivedStatsCalculator equals the direct computations the ViewModel used to do inline`() {
        val range = LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "09:00", "10:00", zone)!!
        val rec = (LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "Học", rawCategory = "learn", dateLocal = today.toString(), inputDurationMinutes = null,
            startedAt = range.first, endedAt = range.second, existingRecords = emptyList(), nowEpochMs = nowMs, zoneId = zone
        ) as ValidationResult.Valid).record
        val budgets = listOf(CategoryTimeBudget(TimeCategory.LEARN, BudgetPeriod.DAILY, 7200L, true, 0, nowMs))
        val d = DerivedStatsCalculator.compute(
            records = listOf(rec), budgets = budgets, dailyReviews = emptyList(), activityNotes = emptyList(),
            reviewDateOverride = null, selectedReviewDate = "", activeTimer = null,
            preferredFocusCategory = TimeCategory.WORK, consecutiveBreakRolls = 0, zone = zone, nowMs = nowMs
        )
        assertEquals(today.toString(), d.todayDateLocal)
        assertEquals(today.toString(), d.targetReviewDate)
        assertEquals(LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(rec), zone, nowMs), d.dayStats)
        val weekly = LedgerAnalyticsCalculator.computeWeeklyInsights(today.toString(), listOf(rec), zone, nowMs, null, budgets)
        assertEquals(weekly, d.weekly)
        assertEquals(BudgetPolicy.dashboardBudgets(weekly.budgetProgressList), d.priorityBudgets)
        assertEquals(3600L, d.budgetProgress.single().combinedUsedSeconds)
        assertTrue(d.faceProbabilities.isNotEmpty())
    }

    @Test
    fun `an explicit review date wins over the stored selection`() {
        val d = DerivedStatsCalculator.compute(
            records = emptyList(), budgets = emptyList(), dailyReviews = emptyList(), activityNotes = emptyList(),
            reviewDateOverride = "2026-11-10", selectedReviewDate = "2026-11-12", activeTimer = null,
            preferredFocusCategory = TimeCategory.WORK, consecutiveBreakRolls = 0, zone = zone, nowMs = nowMs
        )
        assertEquals("2026-11-10", d.targetReviewDate)
        assertEquals("2026-11-10", d.reviewSnapshot.dateLocal)
    }
}
