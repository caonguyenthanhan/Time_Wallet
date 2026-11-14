package com.timewallet.mvp

import com.timewallet.mvp.data.ai.AiHttpException
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.usecase.AiFault
import com.timewallet.mvp.domain.usecase.AiProposalGuard
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.WallyIntentKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * WALLY v1.0.6 — hồi quy cho review v1.0.5:
 *  F-V105-01 (giữ query khi đề xuất bị chặn), F-V105-03 (guard phủ định không dựa vào danh mục),
 *  F-V105-08 + R03 (hợp đồng phản hồi chặt, AI không được báo "đã ghi", số liệu cá nhân chỉ từ engine).
 * Chỉ dùng API công khai đã có từ v1.0.5 để cùng bộ test chạy RED trên bản cũ và GREEN trên bản mới.
 */
class V106ResponseContractTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val now = LocalTime.of(18, 0)

    private fun prop(title: String, cat: String, minutes: Int) = ActivityProposal(
        id = "p-$title-$minutes-$cat", rawInput = "x", title = title, category = cat, durationSeconds = minutes * 60L,
        dateLocal = today.toString(), startLocal = null, endLocal = null, timeEvidence = "duration_only",
        missingFields = listOf("startLocal", "endLocal"), needsConfirmation = true, isFuturePlanned = false
    )

    private fun item(title: String, cat: String, minutes: Int) =
        """{"title":"$title","category":"$cat","durationSeconds":${minutes * 60},"dateLocal":"$today","timeEvidence":"duration_only"}"""

    private fun query(metric: String = "TOTAL") = """{"metric":"$metric","startDate":"$today","endDate":"$today"}"""

    private fun envelope(content: String?): String {
        val message = JSONObject()
        if (content == null) message.put("content", JSONObject.NULL) else message.put("content", content)
        return JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message))).toString()
    }

    private fun parse(content: String, userText: String = "Học 2 tiếng") =
        GeminiProposalClient.parseAndValidateResponseJson(envelope(content), userText, today, now)

    private fun assertMalformed(label: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("$label: phải bị từ chối là MALFORMED nhưng lại được chấp nhận")
        } catch (e: AiHttpException) {
            assertEquals(label, AiFault.MALFORMED, e.fault)
        }
    }

    // =========================================================== F-V105-01
    @Test
    fun `F01 a valid query survives when the proposal is policy-blocked and no orphan reply is kept`() {
        val text = "Hôm nay tôi không học 2 tiếng, hôm nay tôi dùng bao nhiêu giờ?"
        val content = """{"reply":"Mình sẽ ghi 2 giờ học nhé","proposals":[${item("Học", "learn", 120)}],"queries":[${query()}]}"""
        val out = parse(content, text)
        assertTrue("đề xuất bị chặn", out.proposals.isEmpty())
        assertEquals("query hợp lệ phải được giữ", 1, out.queries.size)
        assertEquals(QueryMetric.TOTAL, out.queries[0].metric)
        assertNull("không để lời đáp mồ côi của đề xuất bị chặn", out.reply)
        assertEquals(WallyIntentKind.QUERY, out.intent)
        assertTrue("phải giải thích vì sao đề xuất bị bỏ", out.warnings.isNotEmpty())
    }

    @Test
    fun `F01 with no valid query a blocked proposal still ends as BLOCKED and writes nothing`() {
        val content = """{"reply":"Đã ghi 2 giờ học","proposals":[${item("Học", "learn", 120)}]}"""
        val out = parse(content, "Hôm nay tôi không học 2 tiếng")
        assertTrue(out.proposals.isEmpty())
        assertTrue(out.queries.isEmpty())
        assertNull(out.reply)
        assertEquals(WallyIntentKind.BLOCKED, out.intent)
    }

    // =========================================================== F-V105-03
    private val sameMinutesText = "Hôm nay tôi không học 2 tiếng nhưng làm việc 2 tiếng"

    @Test
    fun `F03 same minutes affirmative plus negated keeps only the affirmative one`() {
        val out = AiProposalGuard.apply(sameMinutesText, listOf(prop("Học", "learn", 120), prop("Làm việc", "work", 120)))
        assertEquals(listOf("Làm việc"), out.proposals.map { it.title })
    }

    @Test
    fun `F03 a relabeled negated proposal is still dropped (category equality is not the gate)`() {
        // AI gắn nhãn sai danh mục cho hoạt động bị phủ định: bản cũ (so category) sẽ giữ lại và ghi nhầm.
        val out = AiProposalGuard.apply(sameMinutesText, listOf(prop("Học", "work", 120)))
        assertTrue("phải bỏ: ${out.proposals.map { it.title to it.category }}", out.proposals.none { it.title == "Học" })
    }

    @Test
    fun `F03 an affirmative proposal sharing the negated category is not dropped`() {
        // Cả hai mệnh đề đều là "học tập": bản cũ bỏ nhầm "Đọc sách" vì cùng danh mục + cùng số phút với mệnh đề phủ định.
        val out = AiProposalGuard.apply("Hôm nay tôi không học 2 tiếng nhưng đọc sách 2 tiếng", listOf(prop("Đọc sách", "learn", 120)))
        assertEquals(listOf("Đọc sách"), out.proposals.map { it.title })
    }

    @Test
    fun `F03 insufficient evidence asks back instead of guessing`() {
        val out = AiProposalGuard.apply(sameMinutesText, listOf(prop("Hoạt động khác", "other", 120)))
        assertTrue(out.proposals.isEmpty())
        assertTrue("phải yêu cầu nhập rõ hơn: ${out.warnings}", out.warnings.any { it.contains("Chưa đủ căn cứ") })
    }

    @Test
    fun `F03 a different duration for the other clause is unaffected`() {
        val out = AiProposalGuard.apply("Không học 2h nhưng code 1h", listOf(prop("Học", "learn", 120), prop("Lập trình", "work", 60)))
        assertEquals(listOf("Lập trình"), out.proposals.map { it.title })
    }

    // =========================================================== F-V105-08 / R03: hợp đồng chặt
    @Test
    fun `R03 JSON null content is rejected`() {
        assertMalformed("content=null") { GeminiProposalClient.parseAndValidateResponseJson(envelope(null), "x", today, now) }
        assertMalformed("content='null'") { parse("null") }
    }

    @Test
    fun `R03 wrong typed fields are rejected`() {
        assertMalformed("proposals là chuỗi") { parse("""{"proposals":"abc"}""") }
        assertMalformed("proposals null") { parse("""{"proposals":null}""") }
        assertMalformed("proposals là object") { parse("""{"proposals":{}}""") }
        assertMalformed("phần tử là số") { parse("""{"proposals":[1,2]}""") }
        assertMalformed("phần tử null") { parse("""{"proposals":[null]}""") }
        assertMalformed("queries không phải mảng") { parse("""{"queries":{"metric":"TOTAL"}}""") }
        assertMalformed("query là chuỗi") { parse("""{"queries":["TOTAL"]}""") }
        assertMalformed("reply là số") { parse("""{"reply":123}""") }
        assertMalformed("reply là mảng") { parse("""{"reply":["a"]}""") }
        assertMalformed("durationSeconds là chuỗi") {
            parse("""{"proposals":[{"title":"Học","category":"learn","durationSeconds":"7200","dateLocal":"$today"}]}""")
        }
        assertMalformed("title là số") {
            parse("""{"proposals":[{"title":5,"category":"learn","durationSeconds":7200,"dateLocal":"$today"}]}""")
        }
        assertMalformed("top-level là mảng") { parse("""[{"title":"Học"}]""") }
        assertMalformed("không có trường nào nhận diện được") { parse("""{"foo":1}""") }
    }

    @Test
    fun `R03 envelope with wrong types is rejected`() {
        listOf(
            """{"choices":"x"}""",
            """{"choices":[]}""",
            """{"choices":[1]}""",
            """{"choices":[{"message":"x"}]}""",
            """{"choices":[{"message":{"content":123}}]}""",
            """{"choices":[{"message":{"content":["a"]}}]}""",
            """{"choices":[{"message":{}}]}"""
        ).forEach { body -> assertMalformed(body) { GeminiProposalClient.parseAndValidateResponseJson(body, "x", today, now) } }
    }

    @Test
    fun `R03 truncated or trailing-garbage JSON is rejected, never partially extracted`() {
        val good = """{"proposals":[${item("Học", "learn", 120)}]}"""
        assertMalformed("bị cắt giữa chừng") { parse(good.dropLast(12)) }
        assertMalformed("cắt trong chuỗi") { parse("""{"reply":"Xin chà""") }
        assertMalformed("thừa dấu } ở cuối") { parse("$good}}") }
        assertMalformed("thừa object thứ hai") { parse("""$good {"proposals":[""") }
        assertMalformed("thừa rác ngay sau object") { parse("$good, rác") }
        assertMalformed("object hợp lệ rồi object hỏng") { parse("""{"reply":"ok"} {"proposals":[{"title":""") }
        assertMalformed("khối rào chưa đóng") { parse("```json\n$good") }
        assertMalformed("ngoặc trong lời dẫn quanh khối rào") { parse("Kết quả {a}:\n```json\n$good\n```") }
        // org.json mặc định chấp nhận phần dư; hợp đồng chặt của ứng dụng thì không.
        assertMalformed("phần dư sau envelope") {
            GeminiProposalClient.parseAndValidateResponseJson(envelope(good) + "}", "Học 2 tiếng", today, now)
        }
    }

    @Test
    fun `R03 well-formed variants still parse`() {
        val good = """{"proposals":[${item("Học", "learn", 120)}]}"""
        assertEquals(1, parse(good).proposals.size)
        assertEquals(1, parse("```json\n$good\n```").proposals.size)
        assertEquals(1, parse("Đây là kết quả:\n```json\n$good\n```\nBạn xác nhận nhé.").proposals.size)
        assertEquals(1, parse("Đây là kết quả: $good Bạn xác nhận nhé.").proposals.size)
        assertEquals(1, GeminiProposalClient.parseAndValidateResponseJson(good, "Học 2 tiếng", today, now).proposals.size)
        val plain = parse("Mình chưa rõ ý bạn, bạn nói lại giúp mình nhé.")
        assertTrue(plain.proposals.isEmpty())
        assertEquals("Mình chưa rõ ý bạn, bạn nói lại giúp mình nhé.", plain.reply)
    }

    @Test
    fun `R03 a reply with braces inside a JSON string does not confuse the object boundary`() {
        val out = parse("""{"reply":"Gõ {học} rồi gửi nhé","proposals":[]}""")
        assertEquals("Gõ {học} rồi gửi nhé", out.reply)
    }

    // =========================================================== R03: lời đáp không được "báo thành công"
    @Test
    fun `R03 an AI reply can never claim it saved edited or deleted anything`() {
        val p = listOf(prop("Học", "learn", 45))
        listOf(
            "Đã ghi 45 phút học.", "Đã lưu xong, không cần xác nhận.", "Mình đã thêm hoạt động rồi nhé", "Đã xóa bản ghi cuối cùng",
            "Đã sửa thành 3 giờ", "Đã cập nhật ngân sách", "Ghi xong rồi!", "Lưu thành công", "Hoàn tất, đã hủy đề xuất",
            "Mình vừa ghi nhận cho bạn", "Bỏ qua bước xác nhận nhé", "Saved!", "Entry recorded successfully"
        ).forEach { reply ->
            assertNull("False Success phải bị bỏ: \"$reply\"", ReplyGuard.sanitize(reply, "Học 45p", p, false).first)
        }
        // Câu chưa xảy ra / nhắc xác nhận thì được giữ.
        assertNotNull(ReplyGuard.sanitize("Bạn xác nhận để mình ghi nhé.", "Học 45p", p, false).first)
        assertNotNull(ReplyGuard.sanitize("Mình chưa ghi gì cả, bạn kiểm tra thẻ đề xuất nhé.", "Học 45p", p, false).first)
    }

    @Test
    fun `R03 the claim is dropped through the full parse and the proposal still needs confirmation`() {
        val out = parse("""{"reply":"Đã lưu xong, không cần xác nhận.","proposals":[${item("Học", "learn", 45)}]}""", "Học 45p")
        assertNull(out.reply)
        assertEquals(1, out.proposals.size)
        assertTrue(out.proposals.all { it.needsConfirmation })
    }

    // =========================================================== R03: số liệu cá nhân chỉ từ engine
    @Test
    fun `R03 spelled-out number words are treated as figures`() {
        listOf(
            "Bạn học hai tiếng hôm nay", "Tuần này bạn dùng một tiếng rưỡi", "Bạn mới ghi nửa tiếng", "Khoảng ba mươi phút thôi",
            "Bạn có năm buổi học", "Hôm nay bạn học hơn một giờ"
        ).forEach { reply ->
            assertNull("số bằng chữ phải bị bỏ: \"$reply\"", ReplyGuard.sanitize(reply, "tổng kết giúp mình", emptyList(), true).first)
            assertNull("số bằng chữ phải bị bỏ (không query): \"$reply\"", ReplyGuard.sanitize(reply, "tổng kết giúp mình", emptyList(), false).first)
        }
    }

    @Test
    fun `R03 percentages are never accepted from the model`() {
        listOf("Bạn dành 12% thời gian cho học", "Chiếm mười hai phần trăm", "Tăng 50 phần trăm so với tuần trước").forEach { reply ->
            assertNull(reply, ReplyGuard.sanitize(reply, "x", emptyList(), false).first)
            assertNull(reply, ReplyGuard.sanitize(reply, "x", emptyList(), true).first)
        }
    }

    @Test
    fun `R03 numbers echoed from the question are not evidence`() {
        // Câu hỏi chứa số; AI lặp lại số đó như thể là số liệu của người dùng.
        val q = "Hôm nay tôi học 5 tiếng đúng không?"
        assertNull(ReplyGuard.sanitize("Đúng rồi, hôm nay bạn học 5 tiếng.", q, emptyList(), false).first)
        assertNull(ReplyGuard.sanitize("Bạn học 5 tiếng, nhiều hơn hôm qua 2 tiếng.", q, emptyList(), true).first)
        assertNull(ReplyGuard.sanitize("Bạn có 3 hoạt động trong ngày", q, emptyList(), false).first)
    }

    @Test
    fun `R03 the duration of the pending proposal may be repeated but nothing else`() {
        val p = listOf(prop("Học", "learn", 45))
        assertNotNull(ReplyGuard.sanitize("Mình thấy 45 phút học, bạn xác nhận nhé.", "Học 45p", p, false).first)
        assertNotNull(ReplyGuard.sanitize("Mình thấy 0,75h học, bạn xác nhận nhé.", "Học 45p", p, false).first)
        assertNull(ReplyGuard.sanitize("Mình thấy 45 phút học, tổng hôm nay 3 giờ.", "Học 45p", p, false).first)
        // Có query thì kể cả thời lượng đề xuất cũng nhường cho engine.
        assertNull(ReplyGuard.sanitize("Mình thấy 45 phút học.", "Học 45p", p, true).first)
    }

    @Test
    fun `R03 figure-free replies pass and words that merely look like numbers do not trigger`() {
        assertEquals("Để mình tính giúp bạn nhé.", ReplyGuard.sanitize("Để mình tính giúp bạn nhé.", "x", emptyList(), true).first)
        assertEquals("Bạn muốn xem hôm nay hay cả tuần?", ReplyGuard.sanitize("Bạn muốn xem hôm nay hay cả tuần?", "x", emptyList(), false).first)
        assertNotNull(ReplyGuard.sanitize("Năm nay bạn làm việc rất chăm chỉ", "x", emptyList(), false).first)
    }
}
