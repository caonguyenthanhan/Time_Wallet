package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * WALLY API-FIRST — hợp đồng hành vi do Owner quyết định. CHỈ dùng API công khai có ở cả v1.0.2 và bản API-first
 * (analyzeInput + connectionFactory) để chạy được trên hành vi cũ (RED: Local-first) và mới (GREEN).
 * Không gọi mạng thật: kết nối là HttpURLConnection giả.
 */
class WallyApiFirstContractTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val now = LocalTime.of(18, 0)
    private val key = "sk-synthetic-contract-key"

    private class Conn(url: URL, private val behaviour: () -> Pair<Int, String>) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() {}
        override fun getOutputStream(): OutputStream = sent
        private var cached: Pair<Int, String>? = null
        private fun result() = cached ?: behaviour().also { cached = it }
        override fun getResponseCode(): Int = result().first
        override fun getInputStream(): InputStream = ByteArrayInputStream(result().second.toByteArray())
        override fun getErrorStream(): InputStream? = null
    }

    private fun ok(vararg proposals: String): String {
        val inner = """{"proposals":[${proposals.joinToString(",")}]}"""
        return JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()
    }

    private val study45 =
        """{"title":"Học","category":"learn","durationSeconds":2700,"dateLocal":"2026-11-13","timeEvidence":"duration_only","needsConfirmation":true}"""

    private fun call(
        text: String,
        optIn: Boolean = true,
        apiKey: String = key,
        behaviour: () -> Pair<Int, String>,
        opens: AtomicInteger
    ) = runBlocking {
        GeminiProposalClient.analyzeInput(
            userText = text, today = today, nowTime = now, optInEnabled = optIn,
            baseUrl = "https://api.example.invalid/v1", apiKey = apiKey,
            connectionFactory = { url -> opens.incrementAndGet(); Conn(url, behaviour) },
            sleeper = { }
        )
    }

    @Test
    fun `Hoc 45p really calls the API exactly once and the proposal comes from the AI response`() {
        val opens = AtomicInteger()
        val aiOnly = """{"title":"Ôn bài (AI)","category":"work","durationSeconds":2700,"dateLocal":"2026-11-13","timeEvidence":"duration_only"}"""
        val out = call("Học 45p", behaviour = { 200 to ok(aiOnly) }, opens = opens)
        assertEquals("phải có đúng 1 request tới API", 1, opens.get())
        assertEquals("Ôn bài (AI)", out.proposals.single().title)
    }

    @Test
    fun `Wally without a key neither calls the network nor analyses locally`() {
        val opens = AtomicInteger()
        listOf(
            call("Học 45p", apiKey = "", behaviour = { 200 to ok(study45) }, opens = opens),
            call("Học 45p", optIn = false, behaviour = { 200 to ok(study45) }, opens = opens)
        ).forEach { out ->
            assertTrue("không giả vờ AI: không có đề xuất", out.proposals.isEmpty())
            assertNotNull("phải có hướng dẫn cấu hình", out.warnings.firstOrNull { it.contains("khóa API") })
        }
        assertEquals(0, opens.get())
    }

    @Test
    fun `an offline API gives a short error and no silent local fallback`() {
        val opens = AtomicInteger()
        val out = call("Học 45p", behaviour = { throw UnknownHostException("synthetic") }, opens = opens)
        assertTrue("không âm thầm chuyển sang chatbot cục bộ", out.proposals.isEmpty())
        assertNotNull(out.fault)
        assertTrue(out.warnings.isNotEmpty())
    }

    @Test
    fun `401 and 429 and 5xx never produce proposals`() {
        listOf(401, 429, 500).forEach { code ->
            val out = call("Học 45p", behaviour = { code to "" }, opens = AtomicInteger())
            assertTrue("HTTP $code không được tạo đề xuất", out.proposals.isEmpty())
            assertNotNull("HTTP $code phải có lỗi", out.fault)
        }
    }

    @Test
    fun `a negated sentence is still sent to the API and the proposals that contradict it are rejected`() {
        val opens = AtomicInteger()
        val out = call("Hôm nay tôi không học 2 tiếng", behaviour = { 200 to ok(study45) }, opens = opens)
        assertEquals("câu ngôn ngữ tự nhiên nào cũng đi qua API", 1, opens.get())
        assertTrue(out.proposals.isEmpty())
    }
}
