package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.ai.HttpOpener
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.OutcomeSource
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong

/**
 * v1.0.13: (1) hạn tổng 45 s (trước đây 30 s) và read timeout không vượt phần ngân sách còn lại; (2) quy tắc prompt 7c/7d phải là quy tắc TỔNG QUÁT
 * (không chứa nguyên văn câu của bộ đánh giá) và các lớp bảo vệ phía ứng dụng vẫn chặn phủ định / lệnh hủy thật.
 * Không có kết nối mạng thật; đồng hồ và độ trễ phản hồi là giả.
 */
class V1013PromptDeadlineTest {

    private val today = LocalDate.of(2026, 11, 18)
    private val now = LocalTime.of(16, 30)

    // ---------- hạ tầng giả: kết nối có độ trễ phản hồi, mô phỏng read timeout đúng như socket thật ----------
    private class DelayedConnection(
        url: URL,
        private val delayMs: Long,
        private val status: Int,
        private val body: String,
        private val clock: AtomicLong,
        private val readTimeouts: MutableList<Int>
    ) : HttpURLConnection(url) {
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() {}
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getResponseCode(): Int {
            readTimeouts += readTimeout
            if (delayMs > readTimeout) { // máy chủ chưa trả lời kịp: socket ném lỗi sau đúng readTimeout
                clock.addAndGet(readTimeout.toLong())
                throw SocketTimeoutException("Read timed out")
            }
            clock.addAndGet(delayMs)
            return status
        }
        override fun getHeaderField(name: String?): String? = null
        override fun getErrorStream(): InputStream? = null
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    }

    private class Reply(val delayMs: Long, val status: Int = 200, val body: String = "")

    private class Rig(vararg replies: Reply) {
        val clock = AtomicLong(0)
        val waits = mutableListOf<Long>()
        val readTimeouts = mutableListOf<Int>()
        private val queue = ArrayDeque(replies.toList())
        var opens = 0
        val opener: HttpOpener = { url ->
            opens++
            val r = queue.removeFirstOrNull() ?: error("unexpected extra connection")
            DelayedConnection(url, r.delayMs, r.status, r.body, clock, readTimeouts)
        }
    }

    private fun wrap(inner: String): String =
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()

    private val studyBody = wrap(
        """{"reply":null,"proposals":[{"title":"Học bài","category":"learn","durationSeconds":2700,"dateLocal":"2026-11-18","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","missingFields":["startLocal","endLocal"],"needsConfirmation":true}],"queries":[]}"""
    )

    /** Gọi đúng đường production với hạn MẶC ĐỊNH (không truyền deadlineMs) để kiểm tra chính hằng số của ứng dụng. */
    private fun runDefault(rig: Rig) = runBlocking {
        GeminiProposalClient.analyzeInput(
            userText = "Học bài 45 phút", today = today, nowTime = now,
            optInEnabled = true, baseUrl = "https://api.example.invalid/v1", apiKey = "sk-synthetic-wally-key-123",
            connectionFactory = rig.opener,
            nowMs = { rig.clock.get() }, sleeper = { rig.waits += it; rig.clock.addAndGet(it) }
        )
    }

    // ---------- hạn tổng 45 s ----------
    @Test
    fun `a response that arrives at 40 s is accepted with the default deadline`() {
        val rig = Rig(Reply(40_000L, body = studyBody))
        val out = runDefault(rig)
        assertNull(out.fault)
        assertEquals(OutcomeSource.CLOUD, out.source)
        assertEquals(1, out.proposals.size)
        assertEquals(1, rig.opens)
        assertEquals("read timeout đầu tiên phải bằng cả hạn 45 s (không còn cắt ở 25 s)", listOf(45_000), rig.readTimeouts)
        assertEquals(40_000L, rig.clock.get())
    }

    @Test
    fun `a response that arrives at 46 s times out as TIMEOUT at 45 s and nothing is proposed`() {
        val rig = Rig(Reply(46_000L, body = studyBody))
        val out = runDefault(rig)
        assertEquals(AiFault.TIMEOUT, out.fault)
        assertTrue(out.proposals.isEmpty() && out.reply == null && out.queries.isEmpty())
        assertEquals("không còn chỗ cho lần thử lại sau khi dùng hết 45 s", 1, rig.opens)
        assertTrue(rig.waits.isEmpty())
        assertEquals(45_000L, rig.clock.get())
    }

    @Test
    fun `the retry keeps working inside the remaining budget and its read timeout never exceeds what is left`() {
        val rig = Rig(Reply(10_000L, status = 503), Reply(5_000L, body = studyBody))
        val out = runDefault(rig)
        assertNull(out.fault)
        assertEquals(1, out.proposals.size)
        assertEquals(2, rig.opens)
        assertEquals("tối đa MỘT lần thử lại, chờ 1 s", listOf(1_000L), rig.waits)
        // 45 000 - 10 000 (lần 1) - 1 000 (chờ) = 34 000 ms còn lại cho lần 2
        assertEquals(listOf(45_000, 34_000), rig.readTimeouts)
        assertEquals(1, out.retryCount)
    }

    // ---------- prompt-6/7: quy tắc tổng quát, không chép câu của bộ đánh giá ----------
    @Test
    fun `prompt-10 keeps the rules of prompt-6_7 plus cross-midnight UC-15 and ambiguity guard without copying live catalogue`() {
        assertEquals("wally-prompt-10", GeminiProposalClient.PROMPT_VERSION)
        val s = GeminiProposalClient.staticSystemPrompt
        assertTrue(s.contains("7c. GIỜ BẮT ĐẦU RỒI ĐẾN ĐỘ DÀI") && s.contains("7d. ĐỘNG TỪ NGHE NHƯ LỆNH NHƯNG LÀ TÊN VIỆC"))
        assertTrue("quy tắc 7b cũ còn nguyên", s.contains("Code từ lúc 14h đến giờ"))
        assertTrue("an toàn: phủ định vẫn không tạo proposal", s.contains("Câu phủ định theo quy tắc 8 vẫn không tạo proposal"))
        // v1.0.13 (D7/D8, Live Eval): lời hỏi lại phải là MỘT câu có dấu "?" (ứng dụng chỉ hiển thị câu hỏi) và được nhắc lại số của chính người dùng.
        assertTrue("câu hỏi lại phải kết thúc bằng dấu ?", s.contains("KẾT THÚC BẰNG DẤU \"?\""))
        assertTrue("không dùng câu mệnh lệnh", s.contains("không dùng câu mệnh lệnh"))
        assertTrue("chỉ nhắc lại số người dùng đã viết", s.contains("tuyệt đối không nêu con số nào người dùng chưa viết"))
        assertFalse("quy tắc cũ cấm mọi con số trong câu hỏi lại đã bị thay thế", s.contains("KHÔNG nhắc lại bất kỳ con số hay thời lượng nào"))
        assertTrue("quy tắc 11 có ngoại lệ cho câu hỏi lại", s.contains("ngoại lệ duy nhất: câu hỏi lại ở quy tắc 8"))
        val folded = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('đ', 'd').lowercase()
        listOf("ngu luc 10h toi 8h", "bo ghi chu 20 phut", "huy hop dong 2h", "10h toi 8h").forEach {
            assertFalse("prompt không được chép câu của bộ đánh giá: $it", folded.contains(it))
        }
        // nhiều nhất hai ví dụ trừu tượng cho hai quy tắc mới
        assertEquals(2, Regex("Ví dụ: \"(Xem phim lúc 11h đêm 3 tiếng|Xóa email 30 phút)\"").findAll(s).count())
    }

    @Test
    fun `the ask-back wording suggested by rule 7b is not one that the reply guard drops as a success claim`() {
        // Đo thực tế (v1.0.12): "đã xong lúc mấy giờ" bị ReplyGuard coi là báo thành công nên lời hỏi biến mất. v1.0.13 (G1): "xong" không phải
        // động từ ghi sổ, mệnh đề có từ để hỏi (mấy, chưa, hay...) không bị chặn vì nó. Kỳ vọng cũ (assertTrue) ghi lại lỗi đó nên được đảo lại.
        assertFalse(com.timewallet.mvp.domain.usecase.ReplyGuard.claimsStateChange("Bạn đã xong lúc mấy giờ, hay vẫn đang làm tiếp?"))
        assertFalse(com.timewallet.mvp.domain.usecase.ReplyGuard.claimsStateChange("Bạn làm xong lúc mấy giờ, hay vẫn đang làm tiếp?"))
        val s = GeminiProposalClient.staticSystemPrompt
        assertTrue(s.contains("Bạn làm xong lúc mấy giờ, hay vẫn đang làm tiếp?"))
        assertFalse(s.contains("đã xong lúc mấy giờ"))
    }

    // ---------- phía ứng dụng: khoảng giờ qua nửa đêm và việc "nghe như lệnh" được chấp nhận; phủ định vẫn bị chặn ----------
    private fun parse(text: String, innerJson: String) =
        GeminiProposalClient.parseAndValidateResponseJson(wrap(innerJson), text, today, now)

    @Test
    fun `an explicit range that crosses midnight is accepted by the validator and keeps its start`() {
        val out = parse(
            "Xem phim lúc 11h đêm 3 tiếng",
            """{"reply":null,"proposals":[{"title":"Xem phim","category":"rest","durationSeconds":10800,"dateLocal":"2026-11-18","startLocal":"23:00","endLocal":"02:00","timeEvidence":"explicit_range","missingFields":[],"needsConfirmation":true}],"queries":[]}"""
        )
        assertEquals(1, out.proposals.size)
        val p = out.proposals[0]
        assertEquals("23:00", p.startLocal); assertEquals("02:00", p.endLocal)
        assertEquals(180, p.durationMinutes)
        assertTrue(p.needsConfirmation)
    }

    @Test
    fun `a start plus bare duration sleep sentence with a midnight crossing survives the guards`() {
        val out = parse(
            "Ngủ lúc 10h tối 8h",
            """{"reply":null,"proposals":[{"title":"Ngủ","category":"sleep","durationSeconds":28800,"dateLocal":"2026-11-18","startLocal":"22:00","endLocal":"06:00","timeEvidence":"explicit_range","missingFields":[],"needsConfirmation":true}],"queries":[]}"""
        )
        assertEquals(1, out.proposals.size)
        assertEquals("22:00", out.proposals[0].startLocal)
        assertEquals(480, out.proposals[0].durationMinutes)
    }

    @Test
    fun `an activity-looking command phrase is not swallowed by the local cancel parser and its proposal survives the guards`() {
        listOf("Bỏ ghi chú 20 phút", "Hủy hợp đồng 2h", "Xóa email 30 phút").forEach {
            assertNull("$it phải đi lên cloud như mô tả hoạt động", CancelCommandParser.parse(it))
        }
        val out = parse(
            "Bỏ ghi chú 20 phút",
            """{"reply":null,"proposals":[{"title":"Bỏ ghi chú","category":"other","durationSeconds":1200,"dateLocal":"2026-11-18","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","missingFields":["startLocal","endLocal"],"needsConfirmation":true}],"queries":[]}"""
        )
        assertEquals(1, out.proposals.size)
        assertEquals(20, out.proposals[0].durationMinutes)
        assertTrue(out.proposals[0].needsConfirmation)
    }

    @Test
    fun `real cancel commands stay local and never reach the cloud`() {
        listOf("Hủy tất cả đề xuất", "Xóa bản ghi học 2h", "Hủy đề xuất số 1", "Bỏ đề xuất").forEach {
            assertNotNull("$it phải là lệnh hủy cục bộ", CancelCommandParser.parse(it))
        }
    }

    @Test
    fun `safety unchanged - a negated or retracted time still yields zero proposals even if the model proposes one`() {
        val work20 =
            """{"reply":null,"proposals":[{"title":"Bỏ ghi chú","category":"other","durationSeconds":1200,"dateLocal":"2026-11-18","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","missingFields":["startLocal","endLocal"],"needsConfirmation":true}],"queries":[]}"""
        val work30 =
            """{"reply":null,"proposals":[{"title":"Xóa email","category":"work","durationSeconds":1800,"dateLocal":"2026-11-18","startLocal":null,"endLocal":null,"timeEvidence":"duration_only","missingFields":["startLocal","endLocal"],"needsConfirmation":true}],"queries":[]}"""
        assertTrue(parse("Đừng bỏ ghi chú 20 phút", work20).proposals.isEmpty())
        assertTrue(parse("Hôm nay tôi không xóa email 30 phút", work30).proposals.isEmpty())
        assertTrue(parse("Chưa xóa email 30 phút", work30).proposals.isEmpty())
    }
}
