package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.usecase.ChatAnalysisFeedback
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * AI-01 FINAL FIX — câu phủ định / yêu cầu không ghi / sửa lại thời lượng KHÔNG được tạo proposal.
 * Chỉ dùng API công khai đã có từ trước (parse, parseAndValidateResponseJson, analyzeInput) để cùng một
 * test chạy được trên code chưa sửa (RED) và đã sửa (GREEN). Không gọi API thật.
 */
class Ai01NegationFinalFixTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val now = LocalTime.of(18, 0)

    private fun parse(text: String) = VietnameseChatParser.parse(text, today, now)

    // ---------- 3 câu Reviewer phát hiện ----------
    @Test
    fun `NEG - three reviewer sentences create no proposal and explain why`() {
        listOf(
            "Hôm nay tôi không học 2 tiếng",
            "Đừng ghi nhận 3 giờ làm việc",
            "Tôi chưa code 1 tiếng hôm nay"
        ).forEach { s ->
            val out = parse(s)
            assertTrue("không được tạo proposal cho: $s -> ${out.proposals.map { it.title }}", out.proposals.isEmpty())
            assertTrue("phải có cảnh báo để toast không báo tách thành công: $s", out.warnings.isNotEmpty())
            val toast = ChatAnalysisFeedback.toastMessage(out.proposals.size, out.warnings)
            assertFalse("toast không được báo 'Đã tách': $s", toast.startsWith("Đã tách"))
        }
    }

    @Test
    fun `NEG - more negated or do-not-record forms create no proposal`() {
        listOf(
            "Không học 2 tiếng",
            "Hôm qua tôi chưa học 30 phút",
            "Đừng ghi 2h học",
            "Chẳng làm gì 1h",
            "Hôm nay không đọc sách 45p",
            "Không học từ 8h đến 10h",
            "Hôm nay tôi không nghỉ 2h"
        ).forEach { s ->
            assertTrue("không được tạo proposal cho: $s", parse(s).proposals.isEmpty())
        }
    }

    // ---------- không chặn từ khóa phủ định toàn câu ----------
    @Test
    fun `KEEP - negation after the duration or non-stop phrases still create the proposal`() {
        val a = parse("Học 2h không nghỉ")
        assertEquals(1, a.proposals.size); assertEquals(120 * 60L, a.proposals[0].durationSeconds)
        assertEquals("learn", a.proposals[0].category)

        val b = parse("Học 2h chưa ghi vào sổ")
        assertEquals(1, b.proposals.size); assertEquals(120 * 60L, b.proposals[0].durationSeconds)

        val c = parse("Làm việc không nghỉ 3h")
        assertEquals(1, c.proposals.size); assertEquals(180 * 60L, c.proposals[0].durationSeconds)

        val e = parse("Hôm nay tôi học không nghỉ 2h")
        assertEquals(1, e.proposals.size); assertEquals("learn", e.proposals[0].category)

        val d = parse("Họp 2h không ghi biên bản")
        assertEquals(1, d.proposals.size); assertEquals("work", d.proposals[0].category)
    }

    @Test
    fun `KEEP - not studying but coding 2h keeps only the coding proposal`() {
        val out = parse("Không học nhưng code 2h")
        assertEquals(1, out.proposals.size)
        val p = out.proposals[0]
        assertEquals(120 * 60L, p.durationSeconds)
        assertEquals("work", p.category)
        assertFalse("tiêu đề không được mang vế phủ định: ${p.title}", p.title.contains("không", ignoreCase = true))
    }

    @Test
    fun `MIX - a negated clause is dropped while the valid clause is kept`() {
        val out = parse("Không học 2h, code 1h")
        assertEquals(1, out.proposals.size)
        assertEquals(60 * 60L, out.proposals[0].durationSeconds)
        assertEquals("work", out.proposals[0].category)
        assertTrue(out.warnings.isNotEmpty())
    }

    // ---------- sửa lại thời lượng / mơ hồ => hỏi lại ----------
    @Test
    fun `CORRECT - duration corrections ask back and create nothing`() {
        listOf(
            "Không phải 2 tiếng, học 3 tiếng",
            "Học 2h, à không, 3h",
            "Sửa lại thành học 3h",
            "Học 2h chứ không phải 3h"
        ).forEach { s ->
            val out = parse(s)
            assertTrue("không được tạo proposal cho câu sửa lại: $s", out.proposals.isEmpty())
            assertTrue("phải hỏi lại: $s", out.warnings.isNotEmpty())
        }
    }

    // ---------- đường cloud (mock, không gọi API thật) ----------
    private val cloudBodyForStudy = """{"proposals":[
      {"title":"Học","category":"learn","durationSeconds":7200,"dateLocal":"2026-11-12","timeEvidence":"duration_only"}]}"""

    @Test
    fun `CLOUD - a mocked AI proposal for a negated sentence is discarded`() {
        listOf(
            "Hôm nay tôi không học 2 tiếng",
            "Đừng ghi nhận 3 giờ làm việc",
            "Tôi chưa code 1 tiếng hôm nay"
        ).forEach { s ->
            val out = GeminiProposalClient.parseAndValidateResponseJson(cloudBodyForStudy, s, today, now)
            assertTrue("cloud không được tạo proposal cho: $s -> ${out.proposals.map { it.title }}", out.proposals.isEmpty())
        }
    }

    @Test
    fun `CLOUD - a valid sentence still passes through the mocked AI response`() {
        val out = GeminiProposalClient.parseAndValidateResponseJson(cloudBodyForStudy, "Học 2h không nghỉ", today, now)
        assertEquals(1, out.proposals.size)
        assertEquals(7200L, out.proposals[0].durationSeconds)
    }

    @Test
    fun `CLOUD - fallback paths do not bypass the negation guard`() = runBlocking {
        // Host không khớp khóa => nhánh fallback offline (không có kết nối mạng nào được mở).
        val mismatched = GeminiProposalClient.analyzeInput(
            userText = "Hôm nay tôi không học 2 tiếng",
            today = today, nowTime = now,
            optInEnabled = true, baseUrl = "https://example.invalid/v1", apiKey = "test-key",
            trustedHost = "other.invalid"
        )
        assertTrue(mismatched.proposals.isEmpty())

        // URL không phải HTTPS => nhánh fallback offline khác.
        val insecure = GeminiProposalClient.analyzeInput(
            userText = "Đừng ghi nhận 3 giờ làm việc",
            today = today, nowTime = now,
            optInEnabled = true, baseUrl = "http://example.invalid/v1", apiKey = "test-key"
        )
        assertTrue(insecure.proposals.isEmpty())

        // Opt-in tắt => bộ phân tích offline.
        val offline = GeminiProposalClient.analyzeInput(
            userText = "Tôi chưa code 1 tiếng hôm nay",
            today = today, nowTime = now, optInEnabled = false, apiKey = ""
        )
        assertTrue(offline.proposals.isEmpty())
    }

    @Test
    fun `CLOUD - negated sentence never reaches the network client`() = runBlocking {
        // Nếu câu bị gửi đi, host .invalid sẽ lỗi và nhánh fallback gắn SAFE_FALLBACK_WARNING_VI.
        val out = GeminiProposalClient.analyzeInput(
            userText = "Hôm nay tôi không học 2 tiếng",
            today = today, nowTime = now,
            optInEnabled = true, baseUrl = "https://example.invalid/v1", apiKey = "test-key"
        )
        assertTrue(out.proposals.isEmpty())
        assertFalse(out.warnings.contains(GeminiProposalClient.SAFE_FALLBACK_WARNING_VI))
    }
}
