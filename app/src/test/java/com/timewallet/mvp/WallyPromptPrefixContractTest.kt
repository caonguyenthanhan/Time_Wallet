package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

/**
 * WALLY prompt cache — hợp đồng cấu trúc tiền tố. CHỈ dùng API có ở cả v1.0.3 và bản hiện tại (analyzeInput +
 * connectionFactory) để chạy được trên prompt cũ (RED: ngày/giờ nằm trong system prompt nên tiền tố đổi mỗi yêu cầu)
 * và mới (GREEN). Không gọi mạng thật.
 */
class WallyPromptPrefixContractTest {

    private class Conn(url: URL) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() {}
        override fun getOutputStream(): OutputStream = sent
        override fun getResponseCode(): Int = 200
        override fun getInputStream(): InputStream = ByteArrayInputStream(
            JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", """{"reply":"ok"}""")))).toString().toByteArray()
        )
        override fun getErrorStream(): InputStream? = null
    }

    private fun systemMessage(text: String, date: LocalDate, time: LocalTime): String {
        lateinit var conn: Conn
        runBlocking {
            GeminiProposalClient.analyzeInput(
                userText = text, today = date, nowTime = time, optInEnabled = true,
                baseUrl = "https://api.example.invalid/v1", apiKey = "sk-synthetic-prefix-key",
                connectionFactory = { url -> Conn(url).also { conn = it } }, sleeper = { }
            )
        }
        val msgs = JSONObject(conn.sent.toString("UTF-8")).getJSONArray("messages")
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        return msgs.getJSONObject(0).getString("content")
    }

    @Test
    fun `the system message is identical across dates times and user texts so a prefix cache can reuse it`() {
        val a = systemMessage("Học 45p", LocalDate.of(2026, 11, 13), LocalTime.of(8, 5))
        val b = systemMessage("Hôm nay tôi dùng bao nhiêu giờ?", LocalDate.of(2027, 2, 1), LocalTime.of(23, 59))
        assertEquals("tiền tố tĩnh phải giống hệt giữa các yêu cầu", a, b)
        assertFalse("system prompt không được chứa ngày", a.contains("2026-11-13") || b.contains("2027-02-01"))
        assertTrue(a.length > 4000)
    }
}
