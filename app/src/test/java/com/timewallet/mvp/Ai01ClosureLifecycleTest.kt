package com.timewallet.mvp

import com.timewallet.mvp.domain.usecase.AnalysisEpoch
import com.timewallet.mvp.domain.usecase.CancelCommand
import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.DraftRef
import com.timewallet.mvp.domain.usecase.ProposalLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AI-01 CLOSURE (F2 + bảo vệ state): vòng đời draft — 0/1/nhiều draft, hủy tất cả, hủy mơ hồ, hủy trong lúc phân tích.
 *
 * Các hàm thuần [ProposalLifecycle] là đúng đoạn mã mà ViewModel gọi trong `_uiState.update { ... }`; test chạy chúng
 * trên MutableStateFlow theo cùng thứ tự. Phần nối ViewModel/UI chỉ được kiểm bằng biên dịch + kiểm tra tĩnh.
 */
class Ai01ClosureLifecycleTest {

    private fun d(id: String, title: String, minutes: Int, raw: String = title) = DraftRef(id, title, raw, minutes)
    private val read30 = d("a", "Đọc sách", 30, "đọc sách 30 phút")
    private val learn30 = d("b", "Học", 30, "học 30 phút")
    private val read45 = d("c", "Đọc sách", 45, "đọc sách 45 phút")

    private fun resolve(text: String, drafts: List<DraftRef>, inFlight: Boolean = false) =
        ProposalLifecycle.resolve(CancelCommandParser.parse(text)!!, drafts, inFlight)

    // ---------- phân biệt lệnh hủy / mô tả hoạt động ----------
    @Test
    fun `parser - recognises cancel commands and extracts the reference`() {
        assertEquals(CancelCommand.All, CancelCommandParser.parse("Hủy tất cả đề xuất"))
        assertEquals(CancelCommand.All, CancelCommandParser.parse("xóa hết đề xuất"))
        assertEquals(CancelCommand.Unspecified, CancelCommandParser.parse("Hủy đề xuất"))
        assertEquals(CancelCommand.Unspecified, CancelCommandParser.parse("Hủy"))
        val t = CancelCommandParser.parse("Hủy ghi nhận 30 phút đọc sách") as CancelCommand.Targeted
        assertEquals(30, t.durationMinutes); assertEquals("đọc sách", t.phrase); assertNull(t.index)
        val i = CancelCommandParser.parse("Hủy đề xuất số 2") as CancelCommand.Targeted
        assertEquals(2, i.index)
    }

    @Test
    fun `parser - activity descriptions are not cancel commands`() {
        listOf(
            "Hủy hợp đồng 2h", "Bỏ cuộc họp 1h", "Bỏ ghi chú 20 phút", "Họp hủy đơn hàng 1h",
            "Học 45p", "Hôm nay tôi không học", "Tổng kết dự án 2h", "Hủy cả buổi họp 1h"
        ).forEach { assertNull("không phải lệnh hủy: $it", CancelCommandParser.parse(it)) }
    }

    // ---------- 0 draft ----------
    @Test
    fun `zero drafts - every cancel form is a harmless no-op with a clear message`() {
        listOf("Hủy tất cả đề xuất", "Hủy đề xuất", "Hủy ghi nhận 30 phút đọc sách", "Hủy đề xuất số 1").forEach { s ->
            val o = resolve(s, emptyList())
            assertTrue(s, o.cancelIds.isEmpty())
            assertFalse(s, o.askBack)
            assertFalse(s, o.invalidateInFlight)
            assertTrue(s, o.message.contains(ProposalLifecycle.STORED_NOTE))
        }
    }

    // ---------- 1 draft ----------
    @Test
    fun `one draft - unspecified all and matching target cancel it, a non-matching target does not`() {
        assertEquals(setOf("a"), resolve("Hủy đề xuất", listOf(read30)).cancelIds)
        assertEquals(setOf("a"), resolve("Hủy tất cả đề xuất", listOf(read30)).cancelIds)
        assertEquals(setOf("a"), resolve("Hủy ghi nhận 30 phút đọc sách", listOf(read30)).cancelIds)
        val wrong = resolve("Hủy ghi nhận 30 phút học", listOf(read30))
        assertTrue("không khớp thì không hủy nhầm", wrong.cancelIds.isEmpty()); assertTrue(wrong.askBack)
        val wrongDuration = resolve("Hủy ghi nhận 60 phút đọc sách", listOf(read30))
        assertTrue(wrongDuration.cancelIds.isEmpty()); assertTrue(wrongDuration.askBack)
    }

    // ---------- nhiều draft ----------
    @Test
    fun `many drafts - a clear reference cancels exactly that draft`() {
        val all = listOf(read30, learn30, read45)
        assertEquals(setOf("a"), resolve("Hủy ghi nhận 30 phút đọc sách", all).cancelIds)
        assertEquals(setOf("c"), resolve("Hủy ghi nhận 45p đọc sách", all).cancelIds)
        assertEquals(setOf("b"), resolve("Hủy đề xuất học", all).cancelIds)
        assertEquals(setOf("b"), resolve("Hủy đề xuất số 2", all).cancelIds)
    }

    @Test
    fun `many drafts - ambiguous or unknown references ask back and cancel nothing`() {
        val all = listOf(read30, learn30, read45)
        listOf("Hủy đề xuất", "Hủy đề xuất đọc sách", "Hủy đề xuất số 9", "Hủy ghi nhận 99 phút nấu ăn").forEach { s ->
            val o = resolve(s, all)
            assertTrue("phải hỏi lại: $s", o.askBack)
            assertTrue("không hủy gì: $s", o.cancelIds.isEmpty())
            assertFalse(o.invalidateInFlight)
        }
        assertTrue(resolve("Hủy đề xuất", all).message.contains("1. Đọc sách (30p)"))
    }

    @Test
    fun `cancel all removes only drafts - the stored note is always present`() {
        val all = listOf(read30, learn30, read45)
        val o = resolve("Hủy tất cả đề xuất", all)
        assertEquals(setOf("a", "b", "c"), o.cancelIds)
        assertTrue(o.message.contains(ProposalLifecycle.STORED_NOTE))
    }

    // ---------- hủy trong lúc phân tích ----------
    @Test
    fun `cancel during analysis - the late result cannot recreate a cancelled draft`() = runBlocking {
        val epoch = AnalysisEpoch()
        val state = MutableStateFlow(emptyList<DraftRef>())
        val captured = epoch.current()
        val analysis = async {
            delay(80) // phân tích (cloud) đang chạy
            var dropped = false
            state.update { cur ->
                val merged = ProposalLifecycle.mergeIfCurrent(cur, listOf(read30), captured, epoch)
                dropped = merged == null
                merged ?: cur
            }
            dropped
        }
        delay(20)
        state.update { cur ->
            ProposalLifecycle.applyCancel(cur, { it }, CancelCommand.All, analysisInFlight = true, epoch = epoch).second
        }
        assertTrue("kết quả muộn phải bị bỏ", analysis.await())
        assertTrue("không có draft cũ sau hủy", state.value.isEmpty())
    }

    @Test
    fun `cancel during analysis with an existing draft cancels it and drops the incoming result`() = runBlocking {
        val epoch = AnalysisEpoch()
        val state = MutableStateFlow(listOf(read30))
        val captured = epoch.current()
        val analysis = async {
            delay(80)
            state.update { cur -> ProposalLifecycle.mergeIfCurrent(cur, listOf(learn30), captured, epoch) ?: cur }
        }
        delay(20)
        state.update { cur ->
            ProposalLifecycle.applyCancel(cur, { it }, CancelCommand.Unspecified, analysisInFlight = true, epoch = epoch).second
        }
        analysis.await()
        assertTrue("không draft nào được phép tồn tại sau hủy xác định", state.value.isEmpty())
    }

    @Test
    fun `ambiguous cancel during analysis does not discard the running analysis`() = runBlocking {
        val epoch = AnalysisEpoch()
        val state = MutableStateFlow(listOf(read30, read45))
        val captured = epoch.current()
        val analysis = async {
            delay(60)
            state.update { cur -> ProposalLifecycle.mergeIfCurrent(cur, listOf(learn30), captured, epoch) ?: cur }
        }
        delay(10)
        val (outcome, remaining) = ProposalLifecycle.applyCancel(
            state.value, { it }, CancelCommand.Unspecified, analysisInFlight = true, epoch = epoch
        )
        assertTrue(outcome.askBack); assertEquals(2, remaining.size); assertFalse(outcome.invalidateInFlight)
        analysis.await()
        assertEquals(3, state.value.size)
    }

    @Test
    fun `unmatched targeted cancel during analysis asks and keeps the analysis, cancel-all drops it`() {
        val epoch = AnalysisEpoch(); val c = epoch.current()
        val o = ProposalLifecycle.resolve(CancelCommand.Targeted("đọc sách", 30, null), emptyList(), analysisInFlight = true)
        assertTrue(o.askBack); assertFalse(o.invalidateInFlight)
        assertEquals(listOf(read30), ProposalLifecycle.mergeIfCurrent(emptyList(), listOf(read30), c, epoch))
        ProposalLifecycle.applyCancel(emptyList<DraftRef>(), { it }, CancelCommand.All, analysisInFlight = true, epoch = epoch)
        assertNull(ProposalLifecycle.mergeIfCurrent(emptyList(), listOf(read30), c, epoch))
    }

    @Test
    fun `without a cancel the analysis result merges as before and the 15 draft cap holds`() {
        val epoch = AnalysisEpoch()
        val many = (1..20).map { d("x$it", "Việc $it", 10) }
        assertEquals(15, ProposalLifecycle.mergeIfCurrent(emptyList(), many, epoch.current(), epoch)!!.size)
    }

    // ---------- bảo vệ state / không tác động Ledger (kiểm tra tĩnh) ----------
    private val vmText by lazy { File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText() }

    @Test
    fun `query and clarify routing never touch the pending drafts`() {
        // WALLY API-FIRST (thay đổi có chủ đích): không còn nhánh định tuyến cục bộ. Bất biến giữ nguyên ở dạng mới:
        // câu trả lời/câu hỏi số liệu của AI không bao giờ xóa draft đang chờ; chỉ GỘP đề xuất mới qua mergeIfCurrent.
        val gateBegin = vmText.indexOf("WALLY API-FIRST begin")
        val gateEnd = vmText.indexOf("WALLY API-FIRST end")
        assertTrue(gateBegin > 0 && gateEnd > gateBegin)
        assertFalse("nhánh chưa cấu hình không được đụng proposals", vmText.substring(gateBegin, gateEnd).contains("proposals"))
        // v1.0.6: logic áp kết quả chuyển sang WallyChatRunner (có test hành vi trong V106ChatRunnerTest); bất biến giữ nguyên.
        val runnerText = File("src/main/java/com/timewallet/mvp/ui/WallyChatRunner.kt").readText()
        assertTrue(runnerText.contains("ProposalLifecycle.mergeIfCurrent(s.proposals, newDrafts, ticket, epoch)"))
        val resultBlock = runnerText.substring(runnerText.indexOf("val merged = ProposalLifecycle.mergeIfCurrent"), runnerText.indexOf("Step.STALE -> return"))
        assertFalse("không có đường nào xóa draft khi có câu trả lời", resultBlock.contains("proposals = emptyList()"))
    }

    @Test
    fun `cancel branch has no ledger or database access and lifecycle code has none either`() {
        val begin = vmText.indexOf("AI-01 CLOSURE cancel branch begin")
        val end = vmText.indexOf("AI-01 CLOSURE cancel branch end")
        assertTrue(begin > 0 && end > begin)
        val region = vmText.substring(begin, end)
        listOf("ledgerDao", "commit", "upsert", "delete", "settingsStore", "budgetDao", "GeminiProposalClient").forEach {
            assertFalse("nhánh hủy không được chứa $it", region.contains(it, ignoreCase = true))
        }
        val life = File("src/main/java/com/timewallet/mvp/domain/usecase/ProposalLifecycle.kt").readText()
        listOf("data.local", "androidx.room", "LedgerDao", "java.net", "commit", "upsert").forEach {
            assertFalse("ProposalLifecycle không được chứa $it", life.contains(it))
        }
    }

    @Test
    fun `cancelling during analysis is reachable from the UI`() {
        val screen = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        assertTrue(screen.contains("enabled = !isAnalyzing || GuardHealth.isCancelCommand(chatInputText)"))
        // v1.0.9: giao diện không được gọi guard trực tiếp lúc compose (lỗi khởi tạo guard là Error và sẽ giết tiến trình).
        assertFalse(screen.contains("CancelCommandParser"))
    }
}
