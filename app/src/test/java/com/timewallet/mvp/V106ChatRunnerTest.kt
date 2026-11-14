package com.timewallet.mvp

import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.ChatAnalysisGuard
import com.timewallet.mvp.domain.usecase.ChatPhase
import com.timewallet.mvp.domain.usecase.ChatRequestOwner
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.QueryIntent
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * WALLY v1.0.6 — F-V105-04: quyền sở hữu yêu cầu / epoch. Thứ tự hoàn thành được điều khiển bằng CompletableDeferred;
 * "yêu cầu cũ" dùng NonCancellable để mô phỏng đọc socket không bị ngắt kịp (phản hồi muộn vẫn tới sau khi đã Hủy).
 *
 * ViewModel không dựng được trên JVM (cần Application/Room), nên logic đã được tách thành WallyChatRunner + ChatRequestOwner
 * (ViewModel chỉ ủy quyền) để kiểm thử hành vi thật thay vì đọc mã nguồn.
 */
class V106ChatRunnerTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val mainThread = Executors.newSingleThreadExecutor { r -> Thread(r, "wally-test-main") }
    private val computeThread = Executors.newSingleThreadExecutor { r -> Thread(r, "wally-test-compute") }
    private val mainDispatcher = mainThread.asCoroutineDispatcher()
    private val computeDispatcher = computeThread.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val toasts = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        scope.cancel()
        mainThread.shutdownNow()
        computeThread.shutdownNow()
    }

    private fun prop(title: String, minutes: Int) = ActivityProposal(
        id = "p-$title", rawInput = "x", title = title, category = "learn", durationSeconds = minutes * 60L,
        dateLocal = today.toString(), startLocal = null, endLocal = null, timeEvidence = "duration_only",
        missingFields = listOf("startLocal", "endLocal"), needsConfirmation = true, isFuturePlanned = false
    )

    private fun outcomeWith(title: String) = ParseChatOutcome(listOf(prop(title, 30)), emptyList(), source = OutcomeSource.CLOUD)
    private fun fault(f: AiFault) = ParseChatOutcome(emptyList(), listOf(f.messageVi), source = OutcomeSource.CLOUD, fault = f)

    private fun runner(
        state: MutableStateFlow<TimeWalletUiState>,
        compute: (com.timewallet.mvp.ui.QueryBasis, List<QueryIntent>) -> List<com.timewallet.mvp.domain.usecase.QueryAnswer> = { _, _ -> emptyList() }
    ) = WallyChatRunner(
        scope = scope, state = state, computeAnswers = compute, toast = { toasts += it },
        computeContext = computeDispatcher
    )

    private fun join(job: Job?) = runBlocking { withTimeout(5_000) { job!!.join() } }

    /** Chờ đến khi coroutine đã thật sự chạy vào bên trong analyze (đã "gửi" yêu cầu). */
    private fun awaitStarted(flag: CompletableDeferred<Unit>) = runBlocking { withTimeout(5_000) { flag.await() } }

    // =========================================================== (1) Hủy -> Gửi lại không mất cú chạm
    @Test
    fun `cancel then resend is accepted at once and the late result of the old request changes nothing`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "Học 30p"))
        val r = runner(state)
        val gateA = CompletableDeferred<ParseChatOutcome>()
        val gateB = CompletableDeferred<ParseChatOutcome>()
        val startedA = CompletableDeferred<Unit>()

        assertTrue(r.submit("Học 30p") { startedA.complete(Unit); withContext(NonCancellable) { gateA.await() } })
        val jobA = r.currentJob
        awaitStarted(startedA)

        assertTrue("Hủy phải ngắt lượt đang chạy", r.cancel())
        assertFalse(state.value.isAnalyzingChat)
        assertEquals(ChatPhase.CANCELLED, state.value.chatPhase)

        // Lượt A chưa kịp tháo dỡ (đang NonCancellable) mà người dùng đã bấm Gửi lại: cú chạm KHÔNG được mất.
        assertTrue("Gửi lại ngay sau Hủy phải được nhận", r.submit("Học 30p") { gateB.await() })
        assertTrue(state.value.isAnalyzingChat)
        assertEquals(ChatPhase.SENDING, state.value.chatPhase)
        val jobB = r.currentJob

        // Phản hồi MUỘN của A tới khi B đang chạy.
        gateA.complete(outcomeWith("A-muộn"))
        join(jobA)

        val s = state.value
        assertTrue("A không được tắt trạng thái đang chạy của B", s.isAnalyzingChat)
        assertEquals(ChatPhase.SENDING, s.chatPhase)
        assertTrue("A không được thêm draft", s.proposals.isEmpty())
        assertEquals("A không được xóa/ghi đè input", "Học 30p", s.chatInputText)
        assertNull(s.chatAnswer)
        assertNull(s.chatFault)
        assertTrue("A không được toast", toasts.isEmpty())
        assertFalse("A không được nhả quyền của B: bấm đúp phải vẫn bị chặn", r.submit("C") { error("không được chạy") })

        gateB.complete(outcomeWith("B"))
        join(jobB)
        val done = state.value
        assertFalse(done.isAnalyzingChat)
        assertEquals(listOf("B"), done.proposals.map { it.title })
        assertEquals("", done.chatInputText)
        assertEquals(1, toasts.size)
        assertTrue("sau khi B xong, lượt mới lại được nhận", r.submit("D") { outcomeWith("D") })
    }

    @Test
    fun `a stale failure cannot show an error over the newer request`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "x"))
        val r = runner(state)
        val gateA = CompletableDeferred<ParseChatOutcome>()
        val gateB = CompletableDeferred<ParseChatOutcome>()
        val startedA = CompletableDeferred<Unit>()
        r.submit("x") { startedA.complete(Unit); withContext(NonCancellable) { gateA.await() } }
        val jobA = r.currentJob
        awaitStarted(startedA)
        r.cancel()
        r.submit("x") { gateB.await() }
        val jobB = r.currentJob

        gateA.complete(fault(AiFault.OFFLINE))
        join(jobA)
        assertNull("lỗi muộn của lượt cũ không được hiện lên", state.value.chatFault)
        assertTrue(state.value.isAnalyzingChat)

        gateB.complete(outcomeWith("B"))
        join(jobB)
        assertNull(state.value.chatFault)
        assertEquals(listOf("B"), state.value.proposals.map { it.title })
    }

    @Test
    fun `new request finishing first is not overwritten by the late old result`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "x"))
        val r = runner(state)
        val gateA = CompletableDeferred<ParseChatOutcome>()
        val startedA = CompletableDeferred<Unit>()
        r.submit("x") { startedA.complete(Unit); withContext(NonCancellable) { gateA.await() } }
        val jobA = r.currentJob
        awaitStarted(startedA)
        r.cancel()
        r.submit("x") { outcomeWith("B") }
        join(r.currentJob)
        assertEquals(listOf("B"), state.value.proposals.map { it.title })
        assertFalse(state.value.isAnalyzingChat)

        gateA.complete(outcomeWith("A-muộn"))
        join(jobA)
        assertEquals("kết quả muộn không được chen vào", listOf("B"), state.value.proposals.map { it.title })
        assertFalse(state.value.isAnalyzingChat)
        assertEquals(1, toasts.size)
    }

    @Test
    fun `double tap while a live request runs is ignored and only one request is sent`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "x"))
        val r = runner(state)
        val gate = CompletableDeferred<ParseChatOutcome>()
        val started = CompletableDeferred<Unit>()
        var sent = 0
        assertTrue(r.submit("x") { sent++; started.complete(Unit); gate.await() })
        awaitStarted(started)
        assertFalse(r.submit("x") { sent++; outcomeWith("dup") })
        gate.complete(outcomeWith("A"))
        join(r.currentJob)
        assertEquals(1, sent)
        assertEquals(1, state.value.proposals.size)
    }

    @Test
    fun `invalidating the epoch from outside (cancel-all command) frees ownership and drops the late result`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "x"))
        val r = runner(state)
        val gateA = CompletableDeferred<ParseChatOutcome>()
        val startedA = CompletableDeferred<Unit>()
        r.submit("x") { startedA.complete(Unit); withContext(NonCancellable) { gateA.await() } }
        val jobA = r.currentJob
        awaitStarted(startedA)

        r.epoch.invalidate()      // đúng thứ ProposalLifecycle.applyCancel làm khi "Hủy tất cả đề xuất"
        r.cancelStaleJob()
        assertTrue("sau lệnh hủy, lượt mới phải được nhận", r.submit("y") { outcomeWith("B") })
        join(r.currentJob)
        gateA.complete(outcomeWith("A-muộn"))
        join(jobA)
        assertEquals(listOf("B"), state.value.proposals.map { it.title })
    }

    @Test
    fun `a normal fault shows the error, keeps the input and releases ownership`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "giữ lại"))
        val r = runner(state)
        r.submit("giữ lại") { fault(AiFault.TIMEOUT) }
        join(r.currentJob)
        assertEquals(AiFault.TIMEOUT, state.value.chatFault)
        assertEquals("giữ lại", state.value.chatInputText)
        assertFalse(state.value.isAnalyzingChat)
        assertTrue(r.submit("lại") { outcomeWith("ok") })
    }

    @Test
    fun `ticket owner unit rules`() {
        val o = ChatRequestOwner()
        val a = o.tryBegin()!!
        assertNull(o.tryBegin())
        assertTrue(o.cancel())
        val b = o.tryBegin()!!
        assertNotEquals(a, b)
        assertFalse("vé cũ không nhả được vé mới", o.finish(a))
        assertNull("lượt mới vẫn đang sở hữu", o.tryBegin())
        assertTrue(o.finish(b))
        assertTrue(o.tryBegin() != null)
        assertFalse("không có gì để hủy", ChatRequestOwner().cancel())
    }

    // =========================================================== (2) số liệu tính ngoài luồng chính, kể cả nhánh tính lại
    private fun totalQuery() =
        (AiQueryValidator.validate("TOTAL", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent

    private fun queryOutcome() =
        ParseChatOutcome(emptyList(), emptyList(), source = OutcomeSource.CLOUD, queries = listOf(totalQuery()))

    @Test
    fun `query numbers are computed off the main thread and recomputed off the main thread when the ledger changes`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "x"))
        val threads = CopyOnWriteArrayList<String>()
        val basisRecords = CopyOnWriteArrayList<Any>()
        val firstComputeStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        val r = runner(state) { basis, intents ->
            val n = ++calls
            threads += Thread.currentThread().name
            basisRecords.add(basis.records)
            if (n == 1) { firstComputeStarted.complete(Unit); runBlocking { releaseFirst.await() } }
            intents.map { LedgerQueryEngine.execute(it, basis.records, zone, System.currentTimeMillis(), basis.timer) }
        }
        r.submit("x") { queryOutcome() }
        awaitStarted(firstComputeStarted)
        // Sổ cái đổi trong lúc đang tính lần đầu => phải tính lại trên ảnh chụp mới.
        val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val newRecords = listOf(
            (com.timewallet.mvp.domain.usecase.LedgerValidator.validateAndBuildRecord(
                source = "manual", rawTitle = "Học", rawCategory = "learn", dateLocal = today.toString(), inputDurationMinutes = 90,
                startedAt = null, endedAt = null, existingRecords = emptyList(), nowEpochMs = nowMs, zoneId = zone
            ) as com.timewallet.mvp.domain.usecase.ValidationResult.Valid).record
        )
        state.update { it.copy(records = newRecords) }
        releaseFirst.complete(Unit)
        join(r.currentJob)

        assertEquals("phải tính lại đúng 1 lần", 2, calls)
        assertTrue("mọi lần tính đều ngoài luồng chính: $threads", threads.all { it.startsWith("wally-test-compute") })
        assertTrue("lần 2 dùng sổ cái mới", basisRecords[1] === newRecords)
        assertTrue(threads.none { it.startsWith("wally-test-main") })
        assertEquals(1, state.value.chatAnswer?.lines?.size?.coerceAtLeast(1))
        assertFalse(state.value.isAnalyzingChat)
    }

    @Test
    fun `a request cancelled during the numbers computation applies nothing`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "x"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val r = runner(state) { basis, intents ->
            started.complete(Unit)
            runBlocking { release.await() }
            intents.map { LedgerQueryEngine.execute(it, basis.records, zone, System.currentTimeMillis(), basis.timer) }
        }
        r.submit("x") { queryOutcome() }
        val jobA = r.currentJob
        awaitStarted(started)
        assertTrue(r.cancel())
        release.complete(Unit)
        join(jobA)
        assertNull(state.value.chatAnswer)
        assertTrue(state.value.proposals.isEmpty())
        assertEquals(ChatPhase.CANCELLED, state.value.chatPhase)
    }

    // =========================================================== RECONSTRUCTED_RED: mô hình hành vi v1.0.5
    /**
     * NHÃN TRUNG THỰC: đây KHÔNG phải chạy ViewModel v1.0.5 (không dựng được trên JVM). Đây là bản tái dựng đúng logic
     * `analyzeChatInput`/`cancelAnalysis` của v1.0.5 (ChatAnalysisGuard toàn cục, `finally` luôn tắt loading và nhả guard)
     * để ghi lại lỗi F-V105-04. Test PASS nghĩa là "lỗi v1.0.5 tái hiện được trên mô hình"; hành vi đúng của v1.0.6 nằm ở các test trên.
     */
    @Test
    fun `RECONSTRUCTED v105 model loses the resend tap and lets the old coroutine clear the new request`() {
        val guard = ChatAnalysisGuard()
        val loading = java.util.concurrent.atomic.AtomicBoolean(false)
        var cancelledEpoch = false

        fun v105Submit(job: suspend () -> Unit): Job? {
            if (!guard.tryAcquire()) return null                       // v1.0.5: cú chạm bị nuốt im lặng
            loading.set(true)
            return scope.launch {
                try { job() } finally { loading.set(false); guard.release() }   // v1.0.5: luôn tắt loading + nhả guard
            }
        }
        val gateA = CompletableDeferred<Unit>()
        val startedA = CompletableDeferred<Unit>()
        val jobA = v105Submit { startedA.complete(Unit); withContext(NonCancellable) { gateA.await() } }
        awaitStarted(startedA)
        // v1.0.5 cancelAnalysis(): tăng epoch, job.cancel(), loading=false nhưng KHÔNG nhả guard
        cancelledEpoch = true; jobA!!.cancel(); loading.set(false)
        val resend = v105Submit { }
        assertNull("v1.0.5: Gửi lại sau Hủy bị mất vì guard chưa nhả", resend)

        // Nếu "sửa" bằng cách nhả guard khi Hủy, lượt A tháo dỡ sẽ nhả/tắt của lượt B:
        guard.release()
        val gateB = CompletableDeferred<Unit>()
        val startedB = CompletableDeferred<Unit>()
        val jobB = v105Submit { startedB.complete(Unit); gateB.await() }
        awaitStarted(startedB)
        gateA.complete(Unit)
        join(jobA)
        assertFalse("v1.0.5: lượt A cũ đã tắt loading của lượt B", loading.get())
        assertTrue("v1.0.5: và nhả guard của lượt B (cho phép gửi trùng)", guard.tryAcquire())
        assertTrue(cancelledEpoch)
        jobB!!.cancel()
    }
}
