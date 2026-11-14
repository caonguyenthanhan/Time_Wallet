package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.AesGcmKeystoreVault
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.domain.usecase.AiFault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * WALLY — khóa API không được chuyển sang origin khác (origin = scheme + host + cổng), kể cả qua redirect.
 * Redirect được kiểm bằng HttpURLConnection THẬT của JDK nói chuyện với hai máy chủ HTTP cục bộ (hai cổng = hai origin);
 * không gọi mạng ngoài, khóa chỉ là chuỗi tổng hợp.
 */
class WallyOriginBindingTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val now = LocalTime.of(18, 0)
    private val key = "sk-synthetic-origin-key"

    private class Seen(val method: String, val path: String, val authorization: String?, val body: String)

    /** Máy chủ HTTP/1.1 tối thiểu bằng ServerSocket; mỗi yêu cầu được ghi lại rồi trả (status, Location?). */
    private class Origin(private val respond: (Seen) -> Pair<Int, String?>) : AutoCloseable {
        val seen = CopyOnWriteArrayList<Seen>()
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort

        init {
            Thread {
                while (!server.isClosed) {
                    val client = try { server.accept() } catch (_: Exception) { break }
                    try { handle(client) } catch (_: Exception) { } finally { try { client.close() } catch (_: Exception) { } }
                }
            }.apply { isDaemon = true; start() }
        }

        private fun handle(c: Socket) {
            c.soTimeout = 5_000
            val input = c.getInputStream()
            fun line(): String? {
                val sb = StringBuilder()
                while (true) {
                    val b = input.read()
                    if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                    if (b == 10) return sb.toString().trimEnd('\r')
                    sb.append(b.toChar())
                }
            }
            val request = line() ?: return
            val parts = request.split(' ')
            var auth: String? = null
            var length = 0
            while (true) {
                val h = line() ?: break
                if (h.isEmpty()) break
                val i = h.indexOf(':')
                if (i > 0) {
                    val name = h.substring(0, i).trim().lowercase()
                    val value = h.substring(i + 1).trim()
                    if (name == "authorization") auth = value
                    if (name == "content-length") length = value.toIntOrNull() ?: 0
                }
            }
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            val one = Seen(parts[0], parts.getOrElse(1) { "" }, auth, String(body, 0, read, Charsets.UTF_8))
            seen += one
            val (status, location) = respond(one)
            val head = StringBuilder("HTTP/1.1 $status X\r\nContent-Length: 0\r\nConnection: close\r\n")
            if (location != null) head.append("Location: $location\r\n")
            head.append("\r\n")
            c.getOutputStream().apply { write(head.toString().toByteArray()); flush() }
        }

        override fun close() {
            try { server.close() } catch (_: Exception) { }
        }
    }

    private fun idle(): Origin = Origin { 200 to null }
    private fun redirecting(status: Int, to: String): Origin = Origin { status to to }

    /** Opener thật: đổi https://api.example.invalid/... sang http://127.0.0.1:cổng/... rồi mở HttpURLConnection của JDK. */
    private fun openerTo(port: Int): (URL) -> HttpURLConnection = { url ->
        URL("http://127.0.0.1:$port${url.path}").openConnection() as HttpURLConnection
    }

    private fun send(port: Int, text: String = "Học 45p") = runBlocking {
        GeminiProposalClient.analyzeInput(
            userText = text, today = today, nowTime = now, optInEnabled = true,
            baseUrl = "https://api.example.invalid/v1", apiKey = key, trustedHost = "api.example.invalid",
            connectionFactory = openerTo(port), sleeper = { }
        )
    }

    // ---------------- redirect ----------------
    @Test
    fun `a redirect never carries the key to another origin for every redirect status`() {
        listOf(301, 302, 303, 307, 308).forEach { status ->
            idle().use { other ->
                redirecting(status, "http://127.0.0.1:${other.port}/stolen").use { configured ->
                    val out = send(configured.port)
                    assertEquals("máy chủ cấu hình nhận đúng 1 yêu cầu (HTTP $status)", 1, configured.seen.size)
                    assertEquals("Bearer $key", configured.seen[0].authorization)
                    assertEquals("origin KHÁC không được nhận yêu cầu nào (HTTP $status)", 0, other.seen.size)
                    assertTrue("redirect không phải phản hồi hợp lệ", out.proposals.isEmpty() && out.fault != null)
                }
            }
        }
    }

    @Test
    fun `a redirect to a different path on the same origin is not followed either`() {
        redirecting(307, "/v1/elsewhere").use { configured ->
            send(configured.port)
            assertEquals(1, configured.seen.size)
            assertEquals("/v1/chat/completions", configured.seen[0].path)
        }
    }

    @Test
    fun `the connection test path also refuses redirects`() {
        idle().use { other ->
            redirecting(307, "http://127.0.0.1:${other.port}/stolen").use { configured ->
                val r = runBlocking {
                    GeminiProposalClient.testConnection(
                        baseUrl = "https://api.example.invalid/v1", apiKey = key, modelName = "m",
                        trustedHost = "api.example.invalid", connectionFactory = openerTo(configured.port)
                    )
                }
                assertTrue(!r.ok)
                assertEquals(1, configured.seen.size)
                assertEquals(0, other.seen.size)
            }
        }
    }

    @Test
    fun `the request body with the user's text only ever reaches the configured origin`() {
        idle().use { other ->
            redirecting(307, "http://127.0.0.1:${other.port}/x").use { configured ->
                send(configured.port, text = "Học 45p bí mật")
                assertTrue(configured.seen[0].body.contains("Học 45p bí mật"))
                assertEquals(0, other.seen.size)
            }
        }
    }

    // ---------------- origin = scheme + host + cổng ----------------
    private fun analyze(base: String, trusted: String?, opens: AtomicInteger) = runBlocking {
        GeminiProposalClient.analyzeInput(
            userText = "Học 45p", today = today, nowTime = now, optInEnabled = true, baseUrl = base, apiKey = key,
            trustedHost = trusted,
            connectionFactory = { u -> opens.incrementAndGet(); URL("http://127.0.0.1:1${u.path}").openConnection() as HttpURLConnection },
            sleeper = { }
        )
    }

    @Test
    fun `a key bound to one origin is not sent to the same host on another port`() {
        val opens = AtomicInteger()
        val out = analyze("https://api.example.invalid:8443/v1", "api.example.invalid", opens)
        assertEquals(AiFault.CONFIG, out.fault)
        assertEquals("cổng khác = origin khác: không được kết nối", 0, opens.get())
    }

    @Test
    fun `a key bound to a custom port origin is not sent to the default port of the same host`() {
        val opens = AtomicInteger()
        val out = analyze("https://api.example.invalid/v1", "api.example.invalid:8443", opens)
        assertEquals(AiFault.CONFIG, out.fault)
        assertEquals(0, opens.get())
    }

    @Test
    fun `origin string keeps the default port implicit and makes other ports explicit`() {
        fun f(u: String) = AiPreferences.extractValidatedHttpsHost(u)
        assertEquals("api.example.invalid", f("https://api.example.invalid/v1"))
        assertEquals("api.example.invalid", f("https://API.Example.INVALID:443/v1"))
        assertEquals("api.example.invalid", f("https://api.example.invalid./v1"))
        assertEquals("api.example.invalid:8443", f("https://api.example.invalid:8443/v1"))
        assertNull(f("https://api.example.invalid:0/v1"))
        assertNull(f("https://api.example.invalid:70000/v1"))
    }

    @Test
    fun `the origin binding is symmetrical when the key is saved and loaded`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, InMemoryApiKeyVault())
        assertTrue(store.saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = key, baseUrl = "https://x.example.invalid:8443/v1")))
        assertEquals(key, store.loadAiPreferences().apiKey)
        assertEquals("x.example.invalid:8443", store.loadAiPreferences().trustedHost)
        // Đổi cổng (hoặc bỏ cổng) mà không nhập lại khóa: khóa được giữ lại, không trả ra.
        listOf("https://x.example.invalid:9443/v1", "https://x.example.invalid/v1").forEach {
            prefs.edit().putString(TimerAndSettingsStore.KEY_AI_BASE_URL, it).commit()
            val loaded = store.loadAiPreferences()
            assertEquals("origin đổi ($it): không trả khóa", "", loaded.apiKey)
            assertTrue(!loaded.optInEnabled)
        }
    }

    @Test
    fun `the URL actually opened has exactly the validated origin`() {
        val opened = mutableListOf<URL>()
        runBlocking {
            GeminiProposalClient.analyzeInput(
                userText = "Học 45p", today = today, nowTime = now, optInEnabled = true,
                baseUrl = "https://api.example.invalid:8443/v1", apiKey = key, trustedHost = "api.example.invalid:8443",
                connectionFactory = { u -> opened += u; URL("http://127.0.0.1:1${u.path}").openConnection() as HttpURLConnection },
                sleeper = { }
            )
        }
        assertTrue(opened.isNotEmpty())
        assertEquals("api.example.invalid", opened[0].host)
        assertEquals(8443, opened[0].port)
        assertEquals("https", opened[0].protocol)
    }

    @Test
    fun `the URI used to validate and the URL used to connect agree on the origin`() {
        listOf(
            "https://api.example.invalid/v1",
            "https://API.Example.INVALID/v1",
            "https://api.example.invalid./v1",
            "https://api.example.invalid:443/v1",
            "https://api.example.invalid:8443/v1",
            "https://[2001:db8::1]/v1",
            "https://[2001:db8::1]:8443/v1",
            "https://192.0.2.10:9443/v1/chat/completions"
        ).forEach { base ->
            val validated = AiPreferences.extractValidatedHttpsHost(base)
            assertTrue("validate được: $base", validated != null)
            assertEquals("URI và URL phải cùng origin cho $base", validated, AiPreferences.originOf(URL("$base/x")))
        }
        // URL khác scheme hoặc có userinfo thì không có origin hợp lệ
        assertNull(AiPreferences.originOf(URL("http://api.example.invalid/v1")))
        assertNull(AiPreferences.originOf(URL("https://user:pw@api.example.invalid/v1")))
    }

    @Test
    fun `the API key header is set in exactly one place and nothing else in the app opens network connections`() {
        val root = java.io.File("src/main/java")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.filter {
            val t = it.readText()
            t.contains("setRequestProperty(\"Authorization\"") || t.contains(".openConnection(") || t.contains("HttpURLConnection")
        }.map { it.name }.toList()
        assertEquals(listOf("GeminiProposalClient.kt"), offenders)
        val client = java.io.File("src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt").readText()
        assertEquals("một nơi đặt Authorization", 1, Regex("""setRequestProperty\("Authorization"""").findAll(client).count())
        assertTrue("chặn redirect trước khi gửi", client.indexOf("instanceFollowRedirects = false") in 0 until client.indexOf("setRequestProperty(\"Authorization\""))
        assertTrue("kiểm tra origin trước khi mở kết nối", client.indexOf("AiPreferences.originOf(url)") in 0 until client.indexOf("opener(url)"))
    }
}
