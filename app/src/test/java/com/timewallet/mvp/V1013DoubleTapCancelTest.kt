package com.timewallet.mvp

import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.usecase.CancelArming
import com.timewallet.mvp.domain.usecase.ChatPhase
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.ui.TimeWalletUiState
import com.timewallet.mvp.ui.WallyChatRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * v1.0.13 F4 — bấm đúp nhanh nút Gửi: cú chạm thứ hai rơi vào nút Hủy (cùng chỗ) và từng hủy chính yêu cầu vừa gửi.
 * Hành vi thật của [WallyChatRunner] + [CancelArming] với đồng hồ tiêm vào (không có sleep): bấm đúp trong cửa sổ ⇒ MỘT yêu cầu và KHÔNG bị hủy;
 * chạm sau cửa sổ ⇒ Hủy hoạt động ngay; Hủy rồi Gửi lại vẫn được nhận và kết quả muộn của lượt đã hủy không đụng vào lượt mới (F3/F5).
 */
class V1013DoubleTapCancelTest {

    private val today = LocalDate.of(2026, 11, 16)
    private val mainThread = Executors.newSingleThreadExecutor { r -> Thread(r, "wally-test-main") }
    private val computeThread = Executors.newSingleThreadExecutor { r -> Thread(r, "wally-test-compute") }
    private val scope = CoroutineScope(SupervisorJob() + mainThread.asCoroutineDispatcher())
    private val toasts = CopyOnWriteArrayList<String>()
    private val clock = AtomicLong(0)

    @After
    fun tearDown() {
        scope.cancel()
        mainThread.shutdownNow()
        computeThread.shutdownNow()
    }

    private fun runner(state: MutableStateFlow<TimeWalletUiState>) = WallyChatRunner(
        scope = scope, state = state, computeAnswers = { _, _ -> emptyList() }, toast = { toasts += it },
        computeContext = computeThread.asCoroutineDispatcher(),
        cancelArming = CancelArming(CancelArming.DEFAULT_ARM_MS) { clock.get() }
    )

    private fun outcomeWith(title: String) = ParseChatOutcome(
        listOf(
            ActivityProposal(
                id = "p-$title", rawInput = "x", title = title, category = "learn", durationSeconds = 1800L,
                dateLocal = today.toString(), startLocal = null, endLocal = null, timeEvidence = "duration_only",
                missingFields = listOf("startLocal", "endLocal"), needsConfirmation = true, isFuturePlanned = false
            )
        ),
        emptyList(), source = OutcomeSource.CLOUD
    )

    private fun join(job: Job?) = runBlocking { withTimeout(5_000) { job!!.join() } }
    private fun awaitStarted(flag: CompletableDeferred<Unit>) = runBlocking { withTimeout(5_000) { flag.await() } }

    @Test
    fun `a double tap inside the window sends exactly one request and does not cancel it`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "Học 30p"))
        val r = runner(state)
        val gate = CompletableDeferred<ParseChatOutcome>()
        val started = CompletableDeferred<Unit>()
        val analyses = AtomicInteger(0)

        // tap 1: Gửi
        assertTrue(r.submit("Học 30p") { analyses.incrementAndGet(); started.complete(Unit); gate.await() })
        val job = r.currentJob
        awaitStarted(started)
        assertTrue(state.value.isAnalyzingChat)

        // tap 2 trước khi giao diện kịp đổi nút: vẫn là Gửi, cùng nội dung ⇒ bị bỏ, không có yêu cầu thứ hai
        clock.set(60)
        assertFalse(r.submit("Học 30p") { analyses.incrementAndGet(); gate.await() })
        // tap 2 sau khi nút đã đổi thành Hủy (đúng kịch bản lỗi F4): Hủy bị bỏ qua, yêu cầu vẫn chạy
        clock.set(180)
        assertFalse("Hủy trong cửa sổ bấm đúp không được hủy yêu cầu vừa gửi", r.cancel())
        clock.set(CancelArming.DEFAULT_ARM_MS - 1)
        assertFalse("sát cuối cửa sổ vẫn chưa được hủy", r.cancel())

        assertTrue("yêu cầu vẫn đang chạy", state.value.isAnalyzingChat)
        assertEquals(ChatPhase.SENDING, state.value.chatPhase)
        assertTrue("không toast 'Đã hủy' nào", toasts.isEmpty())

        gate.complete(outcomeWith("Học"))
        join(job)
        assertEquals("đúng MỘT lần gọi AI", 1, analyses.get())
        assertEquals(listOf("Học"), state.value.proposals.map { it.title })
        assertFalse(state.value.isAnalyzingChat)
    }

    @Test
    fun `a deliberate cancel after the window works at once`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "Học 30p"))
        val r = runner(state)
        val gate = CompletableDeferred<ParseChatOutcome>()
        val started = CompletableDeferred<Unit>()
        assertTrue(r.submit("Học 30p") { started.complete(Unit); gate.await() })
        awaitStarted(started)

        clock.set(CancelArming.DEFAULT_ARM_MS)
        assertTrue("sau cửa sổ Hủy hoạt động", r.cancel())
        assertFalse(state.value.isAnalyzingChat)
        assertEquals(ChatPhase.CANCELLED, state.value.chatPhase)
        assertEquals("nội dung nhập được giữ lại", "Học 30p", state.value.chatInputText)
        assertFalse("không còn gì để hủy", r.cancel())
    }

    @Test
    fun `cancel then resend is accepted, the late result of the cancelled request is ignored and the new request gets its own window`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "Học 30p"))
        val r = runner(state)
        val gateA = CompletableDeferred<ParseChatOutcome>()
        val gateB = CompletableDeferred<ParseChatOutcome>()
        val startedA = CompletableDeferred<Unit>()

        assertTrue(r.submit("Học 30p") { startedA.complete(Unit); withContext(NonCancellable) { gateA.await() } })
        val jobA = r.currentJob
        awaitStarted(startedA)

        clock.set(800)
        assertTrue(r.cancel())
        clock.set(900) // trong vòng một giây sau Hủy
        assertTrue("Gửi lại ngay sau Hủy phải được nhận", r.submit("Học 30p") { gateB.await() })
        val jobB = r.currentJob

        clock.set(1_000) // 100 ms sau khi B bắt đầu: B có cửa sổ riêng nên chưa hủy được
        assertFalse("cú chạm Hủy sát sau Gửi lại không được hủy B", r.cancel())
        assertTrue(state.value.isAnalyzingChat)

        gateA.complete(outcomeWith("A-muộn")) // kết quả muộn của lượt đã hủy
        join(jobA)
        assertTrue("A không được tắt trạng thái đang chạy của B", state.value.isAnalyzingChat)
        assertTrue("A không được thêm đề xuất", state.value.proposals.isEmpty())
        assertTrue("A không được toast", toasts.isEmpty())

        clock.set(900 + CancelArming.DEFAULT_ARM_MS) // hết cửa sổ của B
        assertTrue(r.cancel())
        join(jobB)
        assertFalse(state.value.isAnalyzingChat)
    }

    @Test
    fun `a rejected duplicate tap does not restart the window of the accepted request`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "x"))
        val r = runner(state)
        val gate = CompletableDeferred<ParseChatOutcome>()
        val started = CompletableDeferred<Unit>()
        assertTrue(r.submit("x") { started.complete(Unit); gate.await() })
        awaitStarted(started)

        clock.set(600)
        assertFalse(r.submit("x") { gate.await() }) // lần bấm trùng bị bỏ
        clock.set(CancelArming.DEFAULT_ARM_MS)
        assertTrue("cửa sổ tính từ lần gửi được nhận (t=0), không từ lần bấm trùng (t=600)", r.cancel())
    }

    @Test
    fun `CancelArming arms after the window, restarts on a new send and is armed before any send`() {
        val c = AtomicLong(1_000)
        val a = CancelArming(CancelArming.DEFAULT_ARM_MS) { c.get() }
        assertTrue("chưa gửi gì thì không có gì khóa", a.isArmed())
        a.onSendStarted()
        assertFalse(a.isArmed())
        c.set(1_000 + CancelArming.DEFAULT_ARM_MS - 1)
        assertFalse(a.isArmed())
        c.set(1_000 + CancelArming.DEFAULT_ARM_MS)
        assertTrue(a.isArmed())
        a.onSendStarted() // lượt mới: cửa sổ mới
        assertFalse(a.isArmed())
        assertTrue("armMs = 0 là Hủy tức thì (mặc định của runner cho các test F3/F5)", CancelArming(0L) { 5L }.also { it.onSendStarted() }.isArmed())
    }

    @Test
    fun `the arming delay is longer than any double tap and shorter than a second`() {
        val ms = CancelArming.DEFAULT_ARM_MS
        assertTrue("dài hơn hai lần ngưỡng bấm đúp 300 ms của Android", ms >= 600)
        assertTrue("ngắn hơn 1 giây để Hủy rồi Gửi lại (F3/F5) không bị cảm nhận là chậm", ms < 1_000)
        assertEquals(700L, ms)
    }
}
