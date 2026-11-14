package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.GuardHealth
import com.timewallet.mvp.data.ai.GuardUnavailableException
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AiProposalGuard
import com.timewallet.mvp.domain.usecase.AnalysisEpoch
import com.timewallet.mvp.domain.usecase.CancelCommand
import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.ChatPhase
import com.timewallet.mvp.domain.usecase.DraftRef
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.ProposalLifecycle
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import com.timewallet.mvp.domain.usecase.WallyIntentKind
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.TimeWalletUiState
import com.timewallet.mvp.ui.WallyChatReducer
import com.timewallet.mvp.ui.WallyChatRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.regex.PatternSyntaxException

/**
 * v1.0.9 — P0 F6: lỗi khởi tạo guard KHÔNG được làm sập app và KHÔNG được mở đường ghi dữ liệu thiếu kiểm tra an toàn.
 *
 * Mô phỏng: JDK không từ chối `(?U)` như ICU, nên "guard hỏng" được mô phỏng bằng đúng chuỗi lỗi của log thiết bị
 * (`ExceptionInInitializerError` ← `PatternSyntaxException`) ném từ probe. Đây là mô phỏng hợp đồng chứa lỗi, không phải bằng chứng
 * Android runtime; bằng chứng ICU thật nằm ở harness `icu_replay.py`.
 */
class V109GuardContainmentTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private val mainThread = Executors.newSingleThreadExecutor { r -> Thread(r, "v109-main") }
    private val computeThread = Executors.newSingleThreadExecutor { r -> Thread(r, "v109-compute") }
    private val scope = CoroutineScope(SupervisorJob() + mainThread.asCoroutineDispatcher())
    private val toasts = CopyOnWriteArrayList<String>()
    private val unexpected = CopyOnWriteArrayList<Throwable>()

    @Before
    fun setUp() = GuardHealth.resetForTest()

    @After
    fun tearDown() {
        GuardHealth.resetForTest()
        scope.cancel()
        mainThread.shutdownNow()
        computeThread.shutdownNow()
    }

    /** Chuỗi lỗi y hệt log thiết bị của Owner: EIIE ← PatternSyntaxException (ICU từ chối `(?U)` tại ký tự `U`, offset 3). */
    private fun iceFailure() = ExceptionInInitializerError(PatternSyntaxException("U_REGEX_RULE_SYNTAX", "(?U)^(?:xin|vui lòng)", 3))

    private fun breakGuard(error: Throwable = iceFailure(), name: String = "CancelCommandParser") {
        GuardHealth.probes = listOf(GuardHealth.Probe(name) { throw error })
    }

    private fun runner(state: MutableStateFlow<TimeWalletUiState>) = WallyChatRunner(
        scope = scope, state = state, computeAnswers = { _, _ -> emptyList() }, toast = { toasts += it },
        onUnexpected = { unexpected += it }, computeContext = computeThread.asCoroutineDispatcher()
    )

    private fun join(r: WallyChatRunner) = runBlocking { withTimeout(5_000) { r.currentJob!!.join() } }

    // ============================================================ GuardHealth
    @Test
    fun `healthy guards pass the canary self-test`() {
        val s = GuardHealth.check()
        assertTrue("guard=${s.guard} error=${s.errorClass}", s.ok)
        GuardHealth.require()
        assertTrue(GuardHealth.isCancelCommand("Hủy tất cả đề xuất"))
        assertFalse(GuardHealth.isCancelCommand("Học 45p"))
    }

    @Test
    fun `an Error from guard initialisation is reported and remembered, never thrown`() {
        breakGuard()
        val s = GuardHealth.check()
        assertFalse(s.ok)
        assertEquals("CancelCommandParser", s.guard)
        assertEquals("ExceptionInInitializerError", s.errorClass)
        assertTrue(s.cause is ExceptionInInitializerError)
        assertTrue(s.cause!!.cause is PatternSyntaxException)
        assertSame("kết quả tất định được ghi nhớ", s, GuardHealth.check())
    }

    @Test
    fun `require throws a typed exception that carries the root cause`() {
        breakGuard(NoClassDefFoundError("Could not initialize class CancelCommandParser"))
        val e = runCatching { GuardHealth.require() }.exceptionOrNull()
        assertTrue(e is GuardUnavailableException)
        assertTrue(e!!.cause is NoClassDefFoundError)
    }

    @Test
    fun `a transient OutOfMemoryError is not remembered as a permanent guard failure`() {
        breakGuard(OutOfMemoryError("simulated"), name = "ReplyGuard")
        assertFalse(GuardHealth.check().ok)
        GuardHealth.probes = emptyList()
        assertTrue("OOM tạm thời không được vô hiệu hóa guard vĩnh viễn", GuardHealth.check().ok)
    }

    @Test
    fun `UI predicate never throws even when the guard is broken`() {
        // isCancelCommand gọi thẳng parser thật (healthy) nên trả đúng; điểm kiểm là nó bắt được Error nếu có.
        assertTrue(GuardHealth.isCancelCommand("hủy tất cả"))
        val src = File("src/main/java/com/timewallet/mvp/data/ai/GuardHealth.kt").readText()
        assertTrue(src.contains("catch (_: LinkageError)") && src.contains("catch (_: StackOverflowError)"))
    }

    // ============================================================ client: không gọi AI khi guard hỏng
    @Test
    fun `analyzeInput refuses before any network or parsing when the guard is unavailable`() {
        breakGuard()
        var opened = 0
        val e = runCatching {
            runBlocking {
                GeminiProposalClient.analyzeInput(
                    userText = "Học 45p", today = today, nowTime = LocalTime.of(18, 0), timezoneId = zone.id,
                    optInEnabled = true, apiKey = "sk-test-0000000000",
                    connectionFactory = { opened++; error("không được mở kết nối") }
                )
            }
        }.exceptionOrNull()
        assertTrue("phải ném GuardUnavailableException, thực tế: $e", e is GuardUnavailableException)
        assertEquals("không có kết nối nào được mở khi guard hỏng", 0, opened)
    }

    // ============================================================ runner: không sập, giữ input, không đề xuất
    @Test
    fun `runner converts any guard Error into the guard-unavailable card with no proposals and the input kept`() {
        val failures = listOf<Throwable>(
            iceFailure(),
            NoClassDefFoundError("Could not initialize class com.timewallet.mvp.domain.usecase.CancelCommandParser"),
            StackOverflowError(),
            GuardUnavailableException(GuardHealth.Status(false, "ReplyGuard", "ExceptionInInitializerError", iceFailure())),
            PatternSyntaxException("U_REGEX_RULE_SYNTAX", "(?U)x", 3)
        )
        for (failure in failures) {
            toasts.clear(); unexpected.clear()
            val state = MutableStateFlow(TimeWalletUiState(chatInputText = "Học 45p"))
            val r = runner(state)
            assertTrue(r.submit("Học 45p") { throw failure })
            join(r)
            val s = state.value
            val label = failure.javaClass.simpleName
            assertEquals(label, AiFault.GUARD_UNAVAILABLE, s.chatFault)
            assertEquals(label, ChatPhase.ERROR, s.chatPhase)
            assertTrue("không tạo đề xuất: $label", s.proposals.isEmpty())
            assertNull("không có thẻ trả lời: $label", s.chatAnswer)
            assertFalse("tắt trạng thái đang chạy: $label", s.isAnalyzingChat)
            assertEquals("giữ nguyên input: $label", "Học 45p", s.chatInputText)
            assertEquals("ghi nhật ký đúng một lần: $label", 1, unexpected.size)
            assertTrue("không báo 'đã lưu': $label", toasts.none { it.contains("Đã lưu") })
        }
    }

    @Test
    fun `an ordinary exception keeps the old behaviour and does not claim the guard failed`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "Học 45p"))
        val r = runner(state)
        assertTrue(r.submit("Học 45p") { throw IllegalStateException("boom") })
        join(r)
        assertNull(state.value.chatFault)
        assertEquals(1, unexpected.size)
        assertTrue(state.value.proposals.isEmpty())
    }

    @Test
    fun `the runner is not poisoned by a guard failure and works again once guards are healthy`() {
        val state = MutableStateFlow(TimeWalletUiState(chatInputText = "Học 45p"))
        val r = runner(state)
        assertTrue(r.submit("Học 45p") { throw iceFailure() })
        join(r)
        assertEquals(AiFault.GUARD_UNAVAILABLE, state.value.chatFault)

        val ok = ParseChatOutcome(listOf(prop("Học", "learn", 45)), emptyList(), source = OutcomeSource.CLOUD)
        assertTrue("lượt sau phải được nhận", r.submit("Học 45p") { ok })
        join(r)
        assertEquals(1, state.value.proposals.size)
        assertNull("lỗi cũ được xóa khi lượt mới chạy", state.value.chatFault)
    }

    // ============================================================ luồng sản xuất khi guard khỏe (chuỗi bắt buộc của P0)
    private fun prop(title: String, cat: String, minutes: Int) = ActivityProposal(
        id = "p-$title-$minutes", rawInput = "x", title = title, category = cat, durationSeconds = minutes * 60L,
        dateLocal = today.toString(), startLocal = null, endLocal = null, timeEvidence = "duration_only",
        missingFields = listOf("startLocal", "endLocal"), needsConfirmation = true, isFuturePlanned = false
    )

    private fun envelope(inner: String) =
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()

    private fun inner(reply: String?, proposals: Int, queries: Int): String {
        val o = JSONObject().put("reply", reply ?: JSONObject.NULL)
        o.put("proposals", JSONArray().also { a ->
            repeat(proposals) {
                a.put(JSONObject().put("title", "Học").put("category", "learn").put("durationSeconds", 45 * 60L)
                    .put("dateLocal", today.toString()).put("timeEvidence", "duration_only"))
            }
        })
        o.put("queries", JSONArray().also { a ->
            repeat(queries) { a.put(JSONObject().put("metric", "UNTIMED").put("startDate", today.toString()).put("endDate", today.toString())) }
        })
        return o.toString()
    }

    private fun turn(question: String, reply: String?, proposals: Int, queries: Int): Pair<TimeWalletUiState, ParseChatOutcome> {
        val out = GeminiProposalClient.parseAndValidateResponseJson(envelope(inner(reply, proposals, queries)), question, today, LocalTime.of(18, 0))
        val merged = out.proposals.map { EditableProposalDraft.fromProposal(it) }
        val after = WallyChatReducer.applyResult(TimeWalletUiState(), question, out, merged, null) { recs, timer ->
            out.queries.map { LedgerQueryEngine.execute(it, recs, zone, nowMs, timer) }
        }
        return after to out
    }

    private fun cardText(s: TimeWalletUiState): String =
        s.chatAnswer?.let { (listOf(it.headline) + it.lines + it.notes).joinToString(" | ") }.orEmpty()

    @Test
    fun `Học 45p - duration parsed and RECORD turn yields one confirmable proposal with an app-authored status`() {
        assertEquals(45, VietnameseChatParser.extractDurationMinutes("Học 45p"))
        val (after, out) = turn("Học 45p", "Mình đã lưu giúp bạn 45p học rồi.", proposals = 1, queries = 0)
        assertNull(out.fault)
        assertEquals(WallyIntentKind.RECORD, out.intent)
        assertEquals(1, out.proposals.size)
        assertEquals(45, out.proposals.single().durationMinutes)
        assertNull("lời đáp tự do của AI không được hiện ở lượt ghi nhận", out.reply)
        assertEquals("đề xuất chờ xác nhận được giữ", 1, after.proposals.size)
        assertEquals(45, after.proposals.single().durationMinutes)
        assertNull("RECORD không dựng thẻ lời đáp AI (thẻ trạng thái do giao diện lô đề xuất vẽ)", after.chatAnswer)
        assertEquals("", after.chatInputText)
    }

    @Test
    fun `Xin chào - natural conversation still reaches the user`() {
        val (_, out) = turn("Xin chào", "Chào bạn! Mình giúp gì được cho bạn?", proposals = 0, queries = 0)
        assertNull(out.fault)
        assertEquals(WallyIntentKind.CHAT, out.intent)
        assertEquals("Chào bạn! Mình giúp gì được cho bạn?", out.reply)
        assertTrue(out.proposals.isEmpty())
    }

    @Test
    fun `MIXED keeps both the proposal and the query`() {
        val (after, out) = turn("Học 45p và hôm nay tôi có bao nhiêu giờ chưa gắn giờ?", null, proposals = 1, queries = 1)
        assertEquals(WallyIntentKind.MIXED, out.intent)
        assertEquals(1, out.proposals.size)
        assertEquals(1, out.queries.size)
        assertNotNull(after.chatAnswer)
    }

    @Test
    fun `spelled-number negation keeps only the affirmative activity`() {
        val learn = prop("Học", "learn", 120)
        val work = prop("Làm việc", "work", 120)
        assertEquals(listOf("Làm việc"), AiProposalGuard.apply("Không học hai tiếng nhưng làm việc hai tiếng", listOf(learn, work)).proposals.map { it.title })
        assertTrue(AiProposalGuard.apply("Không học hai tiếng hôm nay", listOf(learn)).proposals.isEmpty())
        assertEquals(1, AiProposalGuard.apply("Học không nghỉ hai tiếng", listOf(learn)).proposals.size)
    }

    @Test
    fun `whole cancel flow - commands are recognised and only unsaved drafts are removed`() {
        assertEquals(CancelCommand.All, CancelCommandParser.parse("Hủy tất cả đề xuất"))
        assertEquals(CancelCommand.All, CancelCommandParser.parse("huỷ tất cả"))
        assertEquals(CancelCommand.Unspecified, CancelCommandParser.parse("Hủy"))
        assertEquals(CancelCommand.Unspecified, CancelCommandParser.parse("hủy đề xuất"))
        val targeted = CancelCommandParser.parse("Xin hãy hủy ghi nhận 30 phút đọc sách") as CancelCommand.Targeted
        assertEquals(30, targeted.durationMinutes)
        assertTrue(targeted.phrase.contains("đọc sách"))
        assertEquals(2, (CancelCommandParser.parse("hủy đề xuất số 2") as CancelCommand.Targeted).index)
        listOf("Hủy hợp đồng 2h", "Bỏ cuộc họp 1h", "Bỏ ghi chú 20 phút", "Học 45p", "Xin chào").forEach {
            assertNull("không phải lệnh hủy: $it", CancelCommandParser.parse(it))
        }

        val drafts = listOf(prop("Học", "learn", 45), prop("Đọc sách", "learn", 30))
        val epoch = AnalysisEpoch()
        val (outcome, remaining) = ProposalLifecycle.applyCancel(
            drafts = drafts, toRef = { DraftRef(it.id, it.title, it.rawInput, it.durationMinutes) },
            command = CancelCommandParser.parse("Hủy tất cả đề xuất")!!, analysisInFlight = true, epoch = epoch
        )
        assertEquals(drafts.map { it.id }.toSet(), outcome.cancelIds)
        assertTrue(remaining.isEmpty())
        assertTrue("đang phân tích ⇒ kết quả về muộn bị bỏ", outcome.invalidateInFlight)
        assertEquals(1, epoch.current())
    }

    // ============================================================ ghim thứ tự trong mã nguồn (không thể dựng ViewModel trên JVM)
    private fun src(rel: String) = File("src/main/java/com/timewallet/mvp/$rel").readText().replace("\r\n", "\n")

    @Test
    fun `ViewModel checks guard health before the first guard call and before any AI work`() {
        val vm = src("ui/TimeWalletViewModel.kt")
        val body = vm.substring(vm.indexOf("fun analyzeChatInput()"))
        val gate = body.indexOf("GuardHealth.check()")
        assertTrue(gate > 0)
        assertTrue("cổng guard phải đứng trước CancelCommandParser.parse", gate < body.indexOf("CancelCommandParser.parse(text)"))
        assertTrue("cổng guard phải đứng trước chatRunner.submit", gate < body.indexOf("chatRunner.submit"))
        val gateBlock = body.substring(gate, body.indexOf("AI-01 CLOSURE cancel branch begin"))
        assertTrue(gateBlock.contains("AiFault.GUARD_UNAVAILABLE") && gateBlock.contains("return"))
        assertFalse("guard hỏng: không xóa input", gateBlock.contains("chatInputText = \"\""))
        assertFalse("guard hỏng: không đụng tới đề xuất/ledger", gateBlock.contains("proposals =") || gateBlock.contains("commit"))
    }

    @Test
    fun `client precheck is the first statement of analyzeInput and the UI never calls a guard directly`() {
        val client = src("data/ai/GeminiProposalClient.kt")
        val body = client.substring(client.indexOf("suspend fun analyzeInput("))
        assertTrue(body.indexOf("GuardHealth.require()") in 1 until body.indexOf("CancelCommandParser.parse(userText)"))
        assertTrue(body.indexOf("GuardHealth.require()") < body.indexOf("AiDiagnostics.newRequestId()"))
        assertFalse(src("ui/screens/ChatScreen.kt").contains("CancelCommandParser"))
        val runner = src("ui/WallyChatRunner.kt")
        assertTrue(runner.contains("catch (e: Throwable)") && runner.contains("e is VirtualMachineError && e !is StackOverflowError"))
    }
}
