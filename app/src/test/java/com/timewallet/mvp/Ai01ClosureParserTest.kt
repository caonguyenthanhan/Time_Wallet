package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * AI-01 CLOSURE — F1 (lệnh hủy không phải hoạt động), F3 (không nghỉ != rest), F4 (mệnh đề tương phản).
 * Chỉ dùng API công khai có từ trước để chạy được cả trên hành vi cũ (RED) và hành vi mới (GREEN).
 */
class Ai01ClosureParserTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val now = LocalTime.of(18, 0)
    private fun parse(t: String) = VietnameseChatParser.parse(t, today, now)

    // ---------------- F1 ----------------
    @Test
    fun `F1 - cancel commands never create an activity proposal`() {
        listOf(
            "Hủy ghi nhận 30 phút đọc sách",
            "Xóa đề xuất đọc sách 30p",
            "Bỏ ghi 45 phút học",
            "Hãy hủy bản nháp học 1 tiếng",
            "Hủy tất cả đề xuất",
            "Hủy đề xuất số 2"
        ).forEach { s ->
            val out = parse(s)
            assertTrue("lệnh hủy không được tạo proposal: $s -> ${out.proposals.map { it.title }}", out.proposals.isEmpty())
        }
        val warn = parse("Hủy ghi nhận 30 phút đọc sách").warnings
        assertTrue("phải giải thích vì sao không tạo bản ghi", warn.isNotEmpty())
    }

    @Test
    fun `F1 - activity descriptions that merely contain a cancel word are still recorded`() {
        val cases = mapOf(
            "Hủy hợp đồng 2h" to 120L,
            "Bỏ cuộc họp 1h" to 60L,
            "Bỏ ghi chú 20 phút" to 20L,
            "Họp hủy đơn hàng 1h" to 60L,
            "Xóa dữ liệu khách hàng 45p" to 45L
        )
        cases.forEach { (s, minutes) ->
            val out = parse(s)
            assertEquals("phải vẫn tạo 1 proposal: $s", 1, out.proposals.size)
            assertEquals(minutes * 60, out.proposals[0].durationSeconds)
        }
    }

    private val cloudBody = """{"proposals":[
      {"title":"Đọc sách","category":"learn","durationSeconds":1800,"dateLocal":"2026-11-12","timeEvidence":"duration_only"}]}"""

    @Test
    fun `F1 - mocked cloud proposal for a cancel command is discarded and fallbacks keep the guard`() = runBlocking {
        val s = "Hủy ghi nhận 30 phút đọc sách"
        val viaJson = GeminiProposalClient.parseAndValidateResponseJson(cloudBody, s, today, now)
        assertTrue(viaJson.proposals.isEmpty())

        val mismatched = GeminiProposalClient.analyzeInput(
            userText = s, today = today, nowTime = now,
            optInEnabled = true, baseUrl = "https://example.invalid/v1", apiKey = "k", trustedHost = "other.invalid"
        )
        assertTrue(mismatched.proposals.isEmpty())

        val direct = GeminiProposalClient.analyzeInput(
            userText = s, today = today, nowTime = now,
            optInEnabled = true, baseUrl = "https://example.invalid/v1", apiKey = "k"
        )
        assertTrue(direct.proposals.isEmpty())
        assertFalse("không được gửi lên cloud", direct.warnings.contains(GeminiProposalClient.SAFE_FALLBACK_WARNING_VI))
    }

    // ---------------- F3 ----------------
    @Test
    fun `F3 - working without a break is work not rest`() {
        listOf(
            "Làm việc không nghỉ 3 tiếng" to "work",
            "Làm việc 3 tiếng không nghỉ" to "work",
            "Code không nghỉ 2h" to "work",
            "Học không nghỉ 2h" to "learn"
        ).forEach { (s, cat) ->
            val out = parse(s)
            assertEquals("1 proposal: $s", 1, out.proposals.size)
            assertEquals("category của: $s", cat, out.proposals[0].category)
        }
        assertEquals(180 * 60L, parse("Làm việc không nghỉ 3 tiếng").proposals[0].durationSeconds)
    }

    @Test
    fun `F3 - genuine rest sentences stay rest`() {
        listOf("Nghỉ ngơi 20 phút", "Thư giãn 30p", "Uống cà phê 15 phút", "Nghỉ 1 tiếng").forEach { s ->
            val out = parse(s)
            assertEquals("1 proposal: $s", 1, out.proposals.size)
            assertEquals("rest", out.proposals[0].category)
        }
        val mixed = parse("Nghỉ ngơi 20 phút rồi học 1 tiếng")
        assertEquals(listOf("rest", "learn"), mixed.proposals.map { it.category })
        // Vẫn là phủ định thật, không tạo proposal rest.
        assertTrue(parse("Hôm nay tôi không nghỉ 2h").proposals.isEmpty())
    }

    // ---------------- F4 ----------------
    @Test
    fun `F4 - negated clause is dropped and the valid contrast clause is kept`() {
        val out = parse("Không học 2h nhưng code 1h")
        assertEquals(1, out.proposals.size)
        assertEquals("work", out.proposals[0].category)
        assertEquals(60 * 60L, out.proposals[0].durationSeconds)
        assertTrue("phải nói rõ vế phủ định đã bị bỏ", out.warnings.any { it.contains("phủ định") })
    }

    @Test
    fun `F4 - two positive clauses with durations are both kept`() {
        val out = parse("Học 2h nhưng code 1h")
        assertEquals(listOf(120 * 60L, 60 * 60L), out.proposals.map { it.durationSeconds })
        assertEquals(listOf("learn", "work"), out.proposals.map { it.category })
    }

    @Test
    fun `F4 - contrast without a second duration keeps previous behaviour`() {
        assertEquals(1, parse("Học 2h nhưng hơi mệt").proposals.size)
        assertEquals(1, parse("Không học nhưng code 2h").proposals.size)
        assertEquals("work", parse("Không học nhưng code 2h").proposals[0].category)
    }

    @Test
    fun `F4 - when the valid clause has no duration nothing is silently recorded and the user is told`() {
        val out = parse("Không học 2h nhưng code")
        assertTrue(out.proposals.isEmpty())
        assertTrue(out.warnings.isNotEmpty())
    }
}
