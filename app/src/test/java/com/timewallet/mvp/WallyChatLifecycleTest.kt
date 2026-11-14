package com.timewallet.mvp

import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.ChatEvent
import com.timewallet.mvp.domain.usecase.ChatPhase
import com.timewallet.mvp.domain.usecase.ChatPhaseMachine
import com.timewallet.mvp.domain.usecase.ConfirmFeedback
import com.timewallet.mvp.domain.usecase.WallyGreeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** WALLY: máy trạng thái chat, lời chào cục bộ, thông báo xác nhận và kiểm tra tĩnh phần nối ViewModel. */
class WallyChatLifecycleTest {

    private fun next(p: ChatPhase, e: ChatEvent) = ChatPhaseMachine.next(p, e)

    @Test
    fun `every phase has an exit and the main flows reach the expected states`() {
        assertEquals(ChatPhase.SENDING, next(ChatPhase.IDLE, ChatEvent.Submit))
        assertEquals(ChatPhase.PROPOSAL_READY, next(ChatPhase.SENDING, ChatEvent.Result(2, null)))
        assertEquals(ChatPhase.IDLE, next(ChatPhase.SENDING, ChatEvent.Result(0, null)))
        assertEquals(ChatPhase.ERROR, next(ChatPhase.SENDING, ChatEvent.Result(1, AiFault.TIMEOUT)))
        assertEquals(ChatPhase.CANCELLED, next(ChatPhase.SENDING, ChatEvent.Cancelled))
        // thoát khỏi mọi trạng thái cuối
        assertEquals(ChatPhase.SENDING, next(ChatPhase.ERROR, ChatEvent.Submit))          // thử lại
        assertEquals(ChatPhase.SENDING, next(ChatPhase.CANCELLED, ChatEvent.Submit))
        assertEquals(ChatPhase.IDLE, next(ChatPhase.ERROR, ChatEvent.Dismissed))
        assertEquals(ChatPhase.IDLE, next(ChatPhase.CANCELLED, ChatEvent.Dismissed))
        assertEquals(ChatPhase.IDLE, next(ChatPhase.ANSWER_READY, ChatEvent.Dismissed))
        assertEquals(ChatPhase.IDLE, next(ChatPhase.PROPOSAL_READY, ChatEvent.DraftsChanged(0)))
        assertEquals(ChatPhase.PROPOSAL_READY, next(ChatPhase.PROPOSAL_READY, ChatEvent.DraftsChanged(1)))
    }

    @Test
    fun `a late result after cancel does not change the state and a local answer does not disturb a running request`() {
        assertEquals(ChatPhase.CANCELLED, next(ChatPhase.CANCELLED, ChatEvent.Result(3, null)))
        assertEquals(ChatPhase.SENDING, next(ChatPhase.SENDING, ChatEvent.Answered))
        assertEquals(ChatPhase.ANSWER_READY, next(ChatPhase.IDLE, ChatEvent.Answered))
        assertEquals(ChatPhase.IDLE, next(ChatPhase.IDLE, ChatEvent.Cancelled))
    }

    @Test
    fun `only the decorative dashboard greeting remains local, the Wally tab no longer answers greetings`() {
        assertTrue(WallyGreeting.dashboardLine(8).contains("buổi sáng"))
        assertTrue(WallyGreeting.dashboardLine(15).contains("buổi chiều"))
        assertTrue(WallyGreeting.dashboardLine(21).contains("buổi tối"))
        // API-FIRST: không còn hàm trả lời lời chào cục bộ trong tab Wally.
        val src = File("src/main/java/com/timewallet/mvp/domain/usecase/WallyGreeting.kt").readText()
        assertFalse(src.contains("fun isGreeting") || src.contains("fun reply"))
        assertFalse(vm.contains("WallyGreeting"))
    }

    @Test
    fun `confirm feedback tells saved from already-present`() {
        assertTrue(ConfirmFeedback.message(2, 0).contains("lưu nguyên tử 2"))
        assertTrue(ConfirmFeedback.message(0, 1).contains("không ghi trùng"))
        assertTrue(ConfirmFeedback.message(1, 1).contains("1 hoạt động đã có sẵn"))
        assertTrue(ConfirmFeedback.message(0, 0).contains("Không có"))
    }

    // ---- kiểm tra tĩnh phần nối ViewModel (không có test ViewModel/Compose) ----
    private val vm by lazy { File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText() }

    @Test
    fun `every coroutine in the ViewModel uses the safe handler`() {
        val launches = Regex("""viewModelScope\.launch\(([^)]*)\)""").findAll(vm).toList()
        assertTrue(launches.size >= 20)
        assertTrue("mọi launch phải có safeHandler", launches.all { it.groupValues[1] == "safeHandler" })
        assertFalse("không còn launch trần", vm.contains("viewModelScope.launch {"))
    }

    @Test
    fun `API-FIRST the ViewModel has no local routing and no local chatbot for natural language`() {
        listOf("ChatRouter", "ChatRoute", "QueryIntentParser", "VietnameseChatParser.parse", "WallyGreeting").forEach {
            assertFalse("ViewModel không được dùng $it cho tin nhắn ngôn ngữ tự nhiên", vm.contains(it))
        }
        val begin = vm.indexOf("WALLY API-FIRST begin")
        val end = vm.indexOf("WALLY API-FIRST end")
        assertTrue(begin > 0 && end > begin)
        val gate = vm.substring(begin, end)
        assertTrue("thiếu khóa => báo cấu hình, không gọi analyzeInput", gate.contains("AiFault.NOT_CONFIGURED") && !gate.contains("analyzeInput"))
        assertTrue("không tự điền input khi lỗi", !gate.contains("chatInputText = \"\""))
        // Ghi/lỗi giữ input: chỉ xóa input khi đã có đề xuất hoặc thẻ trả lời
        assertTrue(File("src/main/java/com/timewallet/mvp/ui/WallyChatReducer.kt").readText()
            .contains("chatInputText = if (outcome.proposals.isNotEmpty() || card != null) \"\" else state.chatInputText"))
        // Số liệu do engine cục bộ tính từ sổ cái, không lấy số AI tự nêu
        assertTrue(vm.contains("LedgerQueryEngine.execute("))
    }

    @Test
    fun `confirm and manual save are guarded against double submit and cancel button exists`() {
        assertTrue(vm.contains("confirmGuard.tryAcquire()"))
        assertTrue(vm.contains("manualSaveGuard.tryAcquire()"))
        assertTrue(vm.contains("fun cancelAnalysis()"))
        val screen = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        assertTrue(screen.contains("onClick = onCancelAnalysis"))
        assertTrue(screen.contains("Thử lại"))
        assertTrue(screen.contains("Ghi thủ công"))
    }

    @Test
    fun `no logging of keys or text anywhere in main sources`() {
        val offenders = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
            .filter { Regex("""\bLog\.[dviwe]\(|println\(""").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue("không dùng Log/println trong main: $offenders", offenders.isEmpty())
    }
}
