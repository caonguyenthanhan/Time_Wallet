package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.HttpOpener
import com.timewallet.mvp.data.diagnostics.AiDiagnostics
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.OutcomeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLHandshakeException
import com.timewallet.mvp.WallyAiFaultInjectionTest.Step

/**
 * v1.0.13 U2 — "Kiểm tra kết nối" phải phản ánh ĐÚNG điều đã xảy ra: lớp lỗi thật, mã HTTP thật, lời giải thích khớp, thành công chỉ khi có phản hồi
 * chat-completions thật, và sự kiện chẩn đoán phân biệt được với yêu cầu chat. Chạy ĐÚNG `GeminiProposalClient.testConnection` qua HttpOpener giả
 * (không có kết nối mạng thật; khóa chỉ là dữ liệu tổng hợp).
 */
class V1013ConnectionTestDiagnosticsTest {

    private val key = "sk-synthetic-key-123"
    private val base = "https://api.example.invalid/v1"

    @Before
    fun reset() = AiDiagnostics.clear()

    private class Run(val result: GeminiProposalClient.ConnectionTestResult, val opens: Int)

    private fun test(
        step: Step, apiKey: String = key, baseUrl: String = base, trusted: String? = null
    ): Run {
        val clock = AtomicLong(0)
        val opens = AtomicInteger(0)
        val opener: HttpOpener = { url ->
            opens.incrementAndGet()
            WallyAiFaultInjectionTest.FakeConnection(url, step, clock, CountDownLatch(1))
        }
        val r = runBlocking {
            GeminiProposalClient.testConnection(baseUrl, apiKey, "m", trusted, opener, nowMs = { clock.get() })
        }
        return Run(r, opens.get())
    }

    private fun reply(status: Int, body: String = "", headers: Map<String, String> = emptyMap()) = Step.Reply(status, body, headers)
    private fun event() = AiDiagnostics.snapshot().single()
    private val genuine = """{"choices":[{"message":{"role":"assistant","content":"pong"}}],"usage":{"prompt_tokens":8,"completion_tokens":1}}"""

    // ------------------------------------------------------------------ thành công chỉ khi là phản hồi chat-completions thật

    @Test
    fun `a genuine chat-completions reply is a success and the event carries the real status and provider-reported tokens`() {
        val run = test(reply(200, genuine))
        assertTrue(run.result.ok)
        assertNull(run.result.fault)
        assertEquals(200, run.result.httpStatus)
        assertTrue(run.result.detailVi.contains("Kết nối thành công"))
        val e = event()
        assertEquals(OutcomeSource.CLOUD, e.source)
        assertNull(e.fault)
        assertEquals(200, e.httpStatus)
        val line = AiDiagnostics.asText()
        assertTrue(line, line.contains("intent=CONNECTION_TEST"))
        assertTrue("token do nhà cung cấp báo được ghi lại", line.contains("tokens=8/1"))
    }

    @Test
    fun `a reply with empty or null content but a real message object is still a genuine reply (max_tokens is 1)`() {
        assertTrue(test(reply(200, """{"choices":[{"finish_reason":"length","message":{"role":"assistant","content":null}}]}""")).result.ok)
        assertTrue(test(reply(200, """{"choices":[{"message":{"content":""}}]}""")).result.ok)
    }

    @Test
    fun `HTTP 200 that is not a genuine chat-completions reply is never reported as success`() {
        val notGenuine = listOf(
            """{"choices":[]}""", """{"choices":5}""", """{"choices":null}""", """{"choices":[{}]}""", """{"choices":[{"message":"x"}]}""",
            """{"choices":[5]}""", "{}", "[]", "", "<html><body>captive portal</body></html>", """{"choices":[{"message":{"content":"x"}}]} trailing"""
        )
        for (body in notGenuine) {
            AiDiagnostics.clear()
            val r = test(reply(200, body)).result
            assertFalse("không phải thành công: $body", r.ok)
            assertEquals("MALFORMED: $body", AiFault.MALFORMED, r.fault)
            assertEquals(200, r.httpStatus)
            assertNotNull("tên lớp lỗi được ghi: $body", event().errorClass)
            assertTrue("lời giải thích nói về nội dung phản hồi, không nói về đề xuất: $body", r.detailVi.contains("chat-completions") && !r.detailVi.contains("đề xuất"))
        }
    }

    // ------------------------------------------------------------------ HTTP 200 kèm lỗi của nhà cung cấp

    @Test
    fun `an error object inside an HTTP 200 body is classified by its nature, not called malformed`() {
        val cases = mapOf(
            """{"error":{"code":"invalid_api_key","message":"Incorrect API key provided"}}""" to AiFault.AUTH,
            """{"error":{"type":"rate_limit_error","message":"slow down"}}""" to AiFault.RATE_LIMIT,
            """{"error":{"message":"The model does not exist"}}""" to AiFault.NOT_FOUND,
            """{"error":{"message":"something else"}}""" to AiFault.UNKNOWN
        )
        for ((body, fault) in cases) {
            AiDiagnostics.clear()
            val r = test(reply(200, body)).result
            assertFalse(r.ok)
            assertEquals(body, fault, r.fault)
            assertEquals(200, r.httpStatus)
            assertEquals("ProviderErrorBody", event().errorClass)
        }
    }

    // ------------------------------------------------------------------ mã HTTP lỗi

    @Test
    fun `401 and 403 are AUTH with the real status in the result and the event`() {
        for (status in listOf(401, 403)) {
            AiDiagnostics.clear()
            val r = test(reply(status)).result
            assertFalse(r.ok); assertEquals(AiFault.AUTH, r.fault); assertEquals(status, r.httpStatus)
            assertTrue(r.detailVi.contains("401/403"))
            assertEquals(AiFault.AUTH, event().fault); assertEquals(status, event().httpStatus)
        }
    }

    @Test
    fun `404 is NOT_FOUND and 5xx is SERVER`() {
        val nf = test(reply(404)).result
        assertEquals(AiFault.NOT_FOUND, nf.fault); assertEquals(404, nf.httpStatus)
        for (status in listOf(500, 502, 503)) {
            val r = test(reply(status)).result
            assertFalse(r.ok); assertEquals(AiFault.SERVER, r.fault); assertEquals(status, r.httpStatus)
        }
    }

    @Test
    fun `429 reports the wait from Retry-After (or the default) in the message`() {
        val withHeader = test(reply(429, headers = mapOf("Retry-After" to "7"))).result
        assertEquals(AiFault.RATE_LIMIT, withHeader.fault); assertEquals(429, withHeader.httpStatus)
        assertTrue(withHeader.detailVi, withHeader.detailVi.contains("7 giây"))
        val without = test(reply(429)).result
        assertEquals(AiFault.RATE_LIMIT, without.fault)
        assertTrue(without.detailVi, without.detailVi.contains("giây"))
    }

    @Test
    fun `a redirect is not followed and is explained as a redirect rather than as no connection`() {
        for (status in listOf(301, 302, 307, 308)) {
            AiDiagnostics.clear()
            val run = test(reply(status, headers = mapOf("Location" to "https://elsewhere.invalid/x")))
            assertEquals("chỉ một kết nối được mở, không theo chuyển hướng", 1, run.opens)
            assertFalse(run.result.ok)
            assertEquals(status, run.result.httpStatus)
            assertTrue(run.result.detailVi, run.result.detailVi.contains("chuyển hướng"))
            assertFalse("máy chủ ĐÃ phản hồi nên không được nói là không kết nối được", run.result.detailVi.contains("Không thể kết nối"))
            assertEquals(status, event().httpStatus)
        }
    }

    @Test
    fun `other HTTP errors say the server answered and refused, with the real status`() {
        for (status in listOf(400, 402, 422)) {
            val r = test(reply(status)).result
            assertFalse(r.ok); assertEquals(AiFault.UNKNOWN, r.fault); assertEquals(status, r.httpStatus)
            assertTrue(r.detailVi, r.detailVi.contains("đã phản hồi") && !r.detailVi.contains("Không thể kết nối"))
        }
    }

    // ------------------------------------------------------------------ lỗi trước khi có phản hồi

    @Test
    fun `timeout offline and TLS keep their class, have no status and record the exception class`() {
        val cases = listOf<Triple<Exception, AiFault, String>>(
            Triple(SocketTimeoutException("t"), AiFault.TIMEOUT, "SocketTimeoutException"),
            Triple(UnknownHostException("h"), AiFault.OFFLINE, "UnknownHostException"),
            Triple(ConnectException("c"), AiFault.OFFLINE, "ConnectException"),
            Triple(SSLHandshakeException("s"), AiFault.TLS, "SSLHandshakeException")
        )
        for ((error, fault, cls) in cases) {
            AiDiagnostics.clear()
            val r = test(Step.Fail(error)).result
            assertFalse(r.ok); assertEquals(fault, r.fault); assertNull(r.httpStatus)
            assertEquals(fault.messageVi, r.detailVi)
            val e = event()
            assertEquals(fault, e.fault); assertNull(e.httpStatus); assertEquals(cls, e.errorClass)
            assertEquals(OutcomeSource.CLOUD, e.source)
        }
    }

    // ------------------------------------------------------------------ lỗi trước khi mở kết nối

    @Test
    fun `a bad key is reported as KEY_INVALID before any connection and is recorded as a local failure`() {
        val run = test(reply(200, genuine), apiKey = "sk-synthetic-khóa-123")
        assertEquals(0, run.opens)
        assertFalse(run.result.ok); assertEquals(AiFault.KEY_INVALID, run.result.fault); assertNull(run.result.httpStatus)
        assertTrue(run.result.detailVi.contains("vị trí 16"))
        assertFalse("không lộ khóa", run.result.detailVi.contains("synthetic"))
        val e = event()
        assertEquals(OutcomeSource.NOT_CONFIGURED, e.source)
        assertEquals(AiFault.KEY_INVALID, e.fault)
        assertEquals("KeyHeaderChars", e.errorClass)
        assertTrue(AiDiagnostics.summaryText().contains("kiểm tra kết nối=0"))
    }

    @Test
    fun `an invalid base url a foreign host and a blank key carry their real class and open no connection`() {
        val badUrl = test(reply(200, genuine), baseUrl = "http://insecure.invalid")
        val foreign = test(reply(200, genuine), trusted = "other.example.invalid")
        val blank = test(reply(200, genuine), apiKey = "  ")
        assertEquals(0, badUrl.opens + foreign.opens + blank.opens)
        assertEquals(AiFault.CONFIG, badUrl.result.fault)
        assertEquals(AiFault.CONFIG, foreign.result.fault)
        assertEquals(AiFault.NOT_CONFIGURED, blank.result.fault)
        listOf(badUrl, foreign, blank).forEach { assertFalse(it.result.ok); assertNull(it.result.httpStatus) }
        val events = AiDiagnostics.snapshot()
        assertEquals("mỗi lần bấm ghi đúng một sự kiện", 3, events.size)
        assertEquals(listOf(AiFault.CONFIG, AiFault.CONFIG, AiFault.NOT_CONFIGURED), events.map { it.fault })
        assertTrue(events.all { it.source == OutcomeSource.NOT_CONFIGURED })
        assertEquals("không có yêu cầu cloud nào", 0, AiDiagnostics.summary().cloudRequests)
    }

    // ------------------------------------------------------------------ phân biệt với chat, số liệu chat không bị lẫn

    @Test
    fun `a connection test is distinguishable from chat requests and never inflates the chat counters`() {
        val chat = V1012Rig.analyze(
            "Code từ lúc 14h đến giờ", key, LocalDate.of(2026, 11, 16), LocalTime.of(16, 30),
            body = V1012Rig.chatBody(V1012Rig.answer(V1012Rig.proposalJson(date = "2026-11-16")))
        )
        assertEquals(1, chat.connections.size)
        val before = AiDiagnostics.summary()
        assertEquals(1, before.cloudRequests)

        test(reply(200, genuine))
        test(reply(401))
        val after = AiDiagnostics.summary()
        assertEquals("kiểm tra kết nối không phải yêu cầu chat", 1, after.cloudRequests)
        assertEquals(before.cloudFailures, after.cloudFailures)
        assertEquals(before.failuresByFault, after.failuresByFault)
        assertEquals(before.totalLatencyMs, after.totalLatencyMs)
        assertEquals(before.promptTokens, after.promptTokens)
        assertEquals(before.byIntent, after.byIntent)

        val summary = AiDiagnostics.summaryText()
        assertTrue(summary, summary.contains("yêu cầu cloud=1") && summary.contains("kiểm tra kết nối=2 (lỗi=1)"))

        val lines = AiDiagnostics.asText().lines()
        assertEquals(3, lines.size)
        val testLines = lines.filter { it.contains("intent=CONNECTION_TEST") }
        assertEquals("hai dòng kiểm tra kết nối", 2, testLines.size)
        assertTrue("dòng chat vẫn mang ý định của nó", lines.single { !it.contains("CONNECTION_TEST") }.contains("intent=RECORD"))
        assertTrue("kiểm tra kết nối không dùng prompt của Wally", testLines.none { it.contains("prompt=" + GeminiProposalClient.PROMPT_VERSION) })
        assertTrue(testLines.any { it.contains("fault=AUTH") && it.contains("http=401") })
    }
}
