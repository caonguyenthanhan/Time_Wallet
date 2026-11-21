package com.timewallet.mvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.1.0 (L1/L5): hợp đồng nối dây TĨNH của giao diện và ViewModel (cùng kiểu V1011UiPolishContractTest; hành vi thật: V110* test miền + kiểm trên thiết bị).
 * Bảo vệ các bất biến: Coach không tự ghi, mục tiêu chỉ tạo khi người dùng xác nhận, Tag không bao giờ bị ghi đè, ghi nhận vẫn qua đường chung.
 */
class V110UiContractTest {
    private fun src(rel: String) = File("src/main/java/com/timewallet/mvp/$rel").readText()
    private val vm get() = src("ui/TimeWalletViewModel.kt")

    private fun section(from: String, to: String): String {
        val s = vm.indexOf(from); val e = vm.indexOf(to, s + from.length)
        assertTrue("thiếu mốc $from", s >= 0); assertTrue("thiếu mốc $to", e > s)
        return vm.substring(s, e)
    }

    @Test
    fun `main activity wires the tag manager the goals dialogs the personal card and the timer tags`() {
        val m = src("MainActivity.kt")
        listOf("TagManagerDialog(", "GoalsDialog(", "GoalEditorDialog(", "ProfileSettingsScreen(", "viewModel::requestCoach", "viewModel::confirmCoachSuggestion",
            "onSelectTag = viewModel::selectTimerTag", "onSelectTag = viewModel::selectManualTag", "TagRules.suggestions(").forEach {
            assertTrue("MainActivity thiếu $it", m.contains(it))
        }
        val insights = src("ui/screens/InsightsScreen.kt")
        assertTrue("InsightsScreen thiếu mục Công cụ", insights.contains("Wally AI Coach") && insights.contains("Ngân sách thời gian"))
        assertEquals("4 tab chính + Wally Overlay", 4, Regex("""[A-Z]+\("[a-z]+", "[^"]+", "[^"]+"\)""").findAll(vm.substring(vm.indexOf("enum class AppTab"), vm.indexOf("enum class HistoryFilter"))).count())
    }

    @Test
    fun `manual entry offers both ways to remember time and end now and keeps the shared write path`() {
        val sheet = src("ui/screens/ManualEntrySheet.kt")
        listOf("Khoảng giờ", "Thời lượng", "Kết thúc: Bây giờ", "Wally tự tính số phút", "imePadding()", "navigationBarsPadding()", "TimeRange.compute(").forEach { assertTrue("form thiếu $it", sheet.contains(it)) }
        val save = section("fun saveManualEntry()", "// ------------------- v1.1.0: TAG CÁ NHÂN")
        assertTrue(save.contains("ManualEntry.validateRange(") && save.contains("ManualEntry.validate("))
        assertTrue("vẫn ghi qua BatchConfirm (LedgerValidator + commit nguyên tử)", save.contains("BatchConfirm.run("))
        assertFalse(save.contains("ledgerDao.upsert") || save.contains("tagDao") || save.contains("goalDao"))
    }

    @Test
    fun `the coach never writes goals or records by itself`() {
        val coach = section("// ------------------- v1.1.0: AI TIME COACH", "fun clearCrashReport()")
        val request = coach.substring(coach.indexOf("fun requestCoach"), coach.indexOf("fun dismissCoach"))
        assertFalse("requestCoach không được tạo mục tiêu/ghi sổ", request.contains("persistGoalRows") || request.contains("goalDao") || request.contains("ledgerDao") || request.contains("BatchConfirm"))
        assertTrue("câu chuyện chỉ hiện sau CoachGuard.check", request.contains("CoachGuard.check(") && request.contains("CoachCheck.Ok"))
        // Mục tiêu từ đề xuất chỉ được tạo trong confirmCoachSuggestion (người dùng bấm Xác nhận).
        val creators = Regex("""persistGoalRows\(""").findAll(vm).count()
        assertEquals("persistGoalRows: định nghĩa(1) + saveGoalEditor(2) + pause/resume/end(3) + confirmCoachSuggestion(1)", 7, creators)
        assertTrue(coach.substring(coach.indexOf("fun confirmCoachSuggestion")).contains("persistGoalRows("))
    }

    @Test
    fun `tag actions never overwrite another tag and history is never rewritten by tag management`() {
        val dao = src("data/local/TagGoalEntities.kt")
        assertTrue("insert Tag dùng ABORT, không REPLACE", dao.contains("fun insert(tag: TagEntity)") && dao.substringBefore("fun insert(tag: TagEntity)").takeLast(80).contains("OnConflictStrategy.ABORT"))
        val tags = section("// ------------------- v1.1.0: TAG CÁ NHÂN", "// ------------------- v1.1.0: MỤC TIÊU")
        assertFalse("quản lý Tag không đụng sổ cái", tags.contains("ledgerDao") || tags.contains("LedgerRecordEntity"))
        assertTrue(tags.contains("TagRules.archive(") && tags.contains("TagRules.restore(") && tags.contains("TagRules.rename("))
    }

    @Test
    fun `goal changes go through GoalRules so the past is never rewritten`() {
        val goals = section("// ------------------- v1.1.0: MỤC TIÊU", "// ------------------- v1.1.0: AI TIME COACH")
        assertTrue(goals.contains("GoalRules.change(") && goals.contains("GoalRules.end(") && goals.contains("GoalRules.create("))
        assertFalse("không xóa mục tiêu", goals.contains("goalDao.deleteAll") || goals.contains("DELETE"))
    }

    @Test
    fun `the coach prompt is separate and the version is bumped`() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("versionName = \"2.1.0\"") && gradle.contains("versionCode = 19"))
        assertTrue(gradle.contains("room.schemaLocation"))
        assertTrue(File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/4.json").exists())
        assertTrue(File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/5.json").exists())
        assertTrue(File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/6.json").exists())
    }
}
