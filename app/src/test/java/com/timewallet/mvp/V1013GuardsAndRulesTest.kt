package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.BatchValidationResult
import com.timewallet.mvp.domain.usecase.DopamineTimeDiceEngine
import com.timewallet.mvp.domain.usecase.JournalDayTotal
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ManualEntry
import com.timewallet.mvp.domain.usecase.ManualEntryValidation
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.domain.usecase.WallyTurnPolicy
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.TimeWalletUiState
import com.timewallet.mvp.ui.WallyChatReducer
import com.timewallet.mvp.ui.toBatchInput
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * WALLY v1.0.13, nhóm G: (G1) ReplyGuard không bỏ nhầm câu hỏi lại hợp lệ, (G2) câu hỏi lại còn sống là nội dung chính của thẻ,
 * (G3) kiểm tra giờ tương lai và trạng thái PLANNED / ACTIVE không được lách quy tắc nghiệp vụ.
 *
 * Câu mẫu lấy từ Live Eval v1.0.12 (D7, D8, H6) và từ các dạng gần đúng của chúng; mọi test chạy mã sản xuất, không mạng, không khóa.
 */
class V1013GuardsAndRulesTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 16)
    private val yesterday = today.minusDays(1)

    private fun ms(date: LocalDate, h: Int, m: Int = 0) = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    // ======================================================================== G1: ReplyGuard
    private fun kept(reply: String, user: String, proposals: List<ActivityProposal> = emptyList(), hasQueries: Boolean = false): String? =
        ReplyGuard.sanitize(reply, user, proposals, hasQueries).first

    private fun assertKept(user: String, vararg replies: String) =
        replies.forEach { assertEquals("phải được giữ (người dùng: \"$user\"): $it", it, kept(it, user)) }

    private fun assertDropped(user: String, vararg replies: String) =
        replies.forEach { assertNull("phải bị bỏ (người dùng: \"$user\"): $it", kept(it, user)) }

    @Test
    fun `G1 the two clarifications observed in the live eval (D7, D8) survive because they repeat the user's own figures`() {
        assertKept("Không phải 2 tiếng, học 3 tiếng", "Bạn muốn ghi 2 giờ hay 3 giờ?", "Bạn muốn ghi học 3 tiếng đúng không?")
        assertKept("Học 2h, à không, 3h", "Bạn muốn ghi 2 giờ hay 3 giờ?", "Bạn muốn ghi học 3 tiếng đúng không?", "Bạn muốn ghi 3h phải không?")
    }

    @Test
    fun `G1 the ask-back of H6 survives although it contains da xong or da hoan thanh`() {
        assertKept(
            "Code từ 14h",
            "Bạn đã làm xong lúc mấy giờ?", "Bạn đã hoàn thành lúc mấy giờ?", "Bạn đã xong lúc mấy giờ, hay vẫn đang làm tiếp?",
            "Bạn đã hoàn thành chưa?", "Bạn code từ 14h đến mấy giờ?"
        )
    }

    @Test
    fun `G1 a figure the user typed may be echoed in a question in digits, in words, without diacritics, as a clock time or as a decimal`() {
        assertKept("Học hai tiếng", "Bạn muốn ghi 2 giờ đúng không?", "Bạn muốn ghi hai tiếng đúng không?")
        assertKept("Hoc hai tieng", "Bạn muốn ghi 2 tiếng phải không?")
        assertKept("hoc 3 tieng", "Bạn muốn ghi 3 tiếng đúng không?")
        assertKept("Học một tiếng rưỡi", "Bạn muốn ghi một tiếng rưỡi phải không?", "Bạn muốn ghi 90 phút phải không?")
        assertKept("Học ba mươi phút", "Bạn muốn ghi 30 phút đúng không?", "Bạn muốn ghi ba mươi phút đúng không?")
        assertKept("Code từ 14h30", "Bạn bắt đầu lúc 14:30 đúng không?", "Bạn bắt đầu lúc 14h30 đúng không?")
        assertKept("Học 1.5 giờ", "Bạn muốn ghi 1,5 giờ đúng không?")
        assertKept("Học 2h30", "Bạn muốn ghi 2 giờ 30 phút đúng không?")
    }

    @Test
    fun `G1 a figure the AI invented cannot be laundered by phrasing it as a question`() {
        assertDropped("Học 3 tiếng", "Hôm nay bạn đã dùng 5 giờ phải không?", "Bạn muốn ghi 7 tiếng hay 3 tiếng?", "Hôm nay bạn học 5h rồi đúng không?")
        assertDropped("Hôm nay tôi làm việc", "Hôm nay bạn đã dùng 5 giờ phải không?", "Bạn có 3 hoạt động hôm nay đúng không?")
        assertDropped("Code từ 14h", "Bạn code đến 17h đúng không?", "Bạn code được 3 tiếng phải không?")
    }

    @Test
    fun `G1 spelled-out numbers the user did not write are blocked, with or without diacritics`() {
        assertDropped("Học 3 tiếng", "Bạn muốn ghi hai tiếng hay ba tiếng?", "Hôm nay bạn đã dùng năm giờ phải không?")
        assertDropped("hoc 3 tieng", "Bạn muốn ghi hai tiếng hay ba tiếng?")
        assertDropped("Học hai tiếng", "Bạn muốn ghi hai tiếng hay ba tiếng?", "Bạn muốn ghi 3 tiếng đúng không?")
        assertDropped("Hôm nay học", "Bạn muốn ghi hai tiếng đúng không?", "Bạn muốn ghi nửa tiếng đúng không?", "Bạn muốn ghi một tiếng rưỡi đúng không?")
        assertDropped("Học một tiếng", "Bạn muốn ghi một tiếng rưỡi phải không?")
    }

    @Test
    fun `G1 percentages, counts and English numbers are never echoed even in a question`() {
        assertDropped("Học 50% bài", "Bạn muốn ghi 50% đúng không?")
        assertDropped("Học 3 lần", "Bạn học 3 lần phải không?", "Bạn học ba lần phải không?")
        assertDropped("Học 2 hours", "Do you mean two hours?")
    }

    @Test
    fun `G1 a statement that repeats the user's figure is still blocked (the numbers of the user are only evidence inside a question)`() {
        assertDropped("Học 3 tiếng", "Bạn học 3 tiếng.", "Hôm nay bạn học 3 tiếng rồi.", "Đúng rồi, bạn học 3 tiếng nhé.")
    }

    @Test
    fun `G1 any claim of a ledger change stays blocked in a question too`() {
        assertDropped(
            "Học 3 tiếng",
            "Mình đã ghi xong rồi nhé?", "Mình đã lưu 3 tiếng rồi đúng không?", "Mình đã thêm hoạt động rồi hay chưa?",
            "Hoạt động đã được xóa chưa?", "Hoạt động đã được sửa rồi phải không?", "Mình vừa cập nhật sổ cái mấy giờ?",
            "Mình ghi giúp bạn rồi nhé, đúng không?", "Mình đã lưu 3 tiếng, đúng không?", "Hoạt động đã nằm trong sổ cái chưa?"
        )
    }

    @Test
    fun `G1 xong and hoan thanh are not ledger verbs, but a statement of completion is still blocked`() {
        assertDropped(
            "Code từ 14h",
            "Xong xuôi rồi nhé.", "Đã hoàn thành.", "Mọi thứ đã xong.", "Hoàn tất cho bạn rồi nhé!", "Xong rồi nhé, bạn cần gì nữa không?",
            "Mình đã hoàn thành rồi nhé?", "Xong rồi nhé?"
        )
        assertKept("Code từ 14h", "Bạn xong chưa?", "Bạn đã hoàn thành code lúc nào?", "Bạn làm xong lúc mấy giờ, hay vẫn đang làm tiếp?")
    }

    @Test
    fun `G1 one invalid sentence drops the whole reply, and valid statements next to a valid question are kept`() {
        // câu khẳng định có số bịa => bỏ cả lời đáp (không cắt bớt: câu hỏi có thể dựa trên tiền đề bịa)
        assertDropped("Học 3 tiếng", "Hôm nay bạn đã dùng 5 giờ. Bạn muốn ghi 3 tiếng đúng không?")
        // câu khẳng định lặp số của người dùng cũng không có ngoại lệ
        assertDropped("Học 3 tiếng", "Bạn nói 3 tiếng. Bạn muốn ghi 3 tiếng đúng không?")
        // câu báo đã ghi + câu hỏi
        assertDropped("Học 3 tiếng", "Đã lưu rồi. Bạn muốn ghi 3 tiếng đúng không?")
        assertKept("Học 3 tiếng", "Mình chưa chắc ý bạn. Bạn muốn ghi 3 tiếng đúng không?")
        assertEquals("Mình chưa chắc ý bạn. Bạn muốn ghi 3 tiếng đúng không?", kept("Mình chưa chắc ý bạn. Bạn muốn ghi 3 tiếng đúng không?", "Học 3 tiếng"))
    }

    @Test
    fun `G1 the echo exception also holds when the engine answers a query, and the other R03 rules still apply there`() {
        assertEquals(
            "Bạn muốn xem 3 tiếng hôm nay đúng không?",
            kept("Bạn muốn xem 3 tiếng hôm nay đúng không?", "Hôm qua học 3 tiếng, hôm nay thì sao?", hasQueries = true)
        )
        assertNull(kept("Hôm nay bạn đã học 5 tiếng.", "Hôm qua học 3 tiếng, hôm nay thì sao?", hasQueries = true))
    }

    @Test
    fun `G1 through the real client the clarification of D8 reaches the outcome and an invented figure is dropped with a note`() {
        val ok = parse("Học 2h, à không, 3h", """{"reply":"Bạn muốn ghi 2 giờ hay 3 giờ?","proposals":[],"queries":[]}""")
        assertEquals("Bạn muốn ghi 2 giờ hay 3 giờ?", ok.reply)
        assertTrue(ok.proposals.isEmpty())
        val bad = parse("Học 3 tiếng", """{"reply":"Hôm nay bạn đã dùng 5 giờ phải không?","proposals":[],"queries":[]}""")
        assertNull(bad.reply)
        assertTrue(bad.warnings.joinToString(), bad.warnings.any { it.contains("số liệu không có căn cứ") })
    }

    // ======================================================================== G2: thẻ trả lời khi 0 đề xuất
    private fun envelope(inner: String) =
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()

    private fun parse(question: String, inner: String, now: LocalTime = LocalTime.of(18, 0)): ParseChatOutcome =
        GeminiProposalClient.parseAndValidateResponseJson(envelope(inner), question, today, now)

    private fun turn(question: String, reply: String?, pending: List<EditableProposalDraft> = emptyList()): TimeWalletUiState {
        val inner = JSONObject().put("reply", reply ?: JSONObject.NULL).put("proposals", JSONArray()).put("queries", JSONArray()).toString()
        val out = parse(question, inner)
        return WallyChatReducer.applyResult(TimeWalletUiState(proposals = pending), question, out, pending, null) { _, _ -> emptyList() }
    }

    private fun pendingDraft(minutes: Int = 45) = EditableProposalDraft.fromProposal(
        ActivityProposal(
            id = "p$minutes", rawInput = "x", title = "Học", category = "learn", durationSeconds = minutes * 60L, dateLocal = today.toString(),
            startLocal = null, endLocal = null, timeEvidence = "duration_only", missingFields = listOf("startLocal", "endLocal"),
            needsConfirmation = true, isFuturePlanned = false
        )
    )

    @Test
    fun `G2 a surviving AI question is the headline of the card and the application states that nothing was saved below it`() {
        for ((q, ask) in listOf(
            "Không phải 2 tiếng, học 3 tiếng" to "Bạn muốn ghi 2 giờ hay 3 giờ?",
            "Học 2h, à không, 3h" to "Bạn muốn ghi học 3 tiếng đúng không?",
            "Học 3 tiếng" to "Bạn muốn ghi môn gì?"
        )) {
            val card = turn(q, ask).chatAnswer!!
            assertEquals(ask, card.headline)
            assertTrue(card.isClarification)
            assertNull("nhãn mặc định 'Cần hỏi lại'", card.label)
            assertTrue("thẻ phải bị xóa khi danh sách đề xuất đổi", card.isTransactionStatus)
            assertTrue(card.lines.first(), card.lines.first().startsWith("Chưa có gì được lưu"))
            assertFalse(card.headline.startsWith("Chưa có gì được lưu"))
        }
        // câu không liên quan ghi nhận (H6): lời hỏi lại vẫn là thẻ chính
        assertEquals("Bạn đã làm xong lúc mấy giờ?", turn("Code từ 14h", "Bạn đã làm xong lúc mấy giờ?").chatAnswer!!.headline)
    }

    @Test
    fun `G2 with pending drafts the question is still primary and the pending count is a line below it`() {
        val card = turn("Học 2h, à không, 3h", "Bạn muốn ghi 2 giờ hay 3 giờ?", listOf(pendingDraft(45), pendingDraft(30))).chatAnswer!!
        assertEquals("Bạn muốn ghi 2 giờ hay 3 giờ?", card.headline)
        assertEquals("Chưa có gì được lưu. Còn 2 đề xuất đang chờ bạn xác nhận.", card.lines.first())
    }

    @Test
    fun `G2 only the question sentences of the AI are shown, an AI statement next to it never is`() {
        val card = turn("Học 3 tiếng", "Cứ yên chí đi, mình lo trọn gói. Bạn muốn ghi môn gì?").chatAnswer!!
        assertEquals("Bạn muốn ghi môn gì?", card.headline)
        assertFalse((listOf(card.headline) + card.lines + card.notes).joinToString().contains("trọn gói"))
        // "hay" trong "Hay quá!" không làm nó thành câu hỏi: không có "?" thì vẫn là thẻ chung của ứng dụng
        assertEquals("Bạn muốn ghi môn gì?", turn("Học 3 tiếng", "Hay quá! Bạn muốn ghi môn gì?").chatAnswer!!.headline)
        assertTrue(turn("Học 3 tiếng", "Hay quá!").chatAnswer!!.headline.startsWith("Chưa có gì được lưu"))
    }

    @Test
    fun `G2 when the guard dropped the AI text the generic application note is still the card`() {
        val card = turn("Học 3 tiếng", "Hôm nay bạn đã dùng 5 giờ phải không?").chatAnswer!!
        assertTrue(card.headline, card.headline.startsWith("Chưa có gì được lưu"))
        assertTrue(card.lines.joinToString().contains("Hãy nêu rõ hoạt động và thời lượng"))
    }

    @Test
    fun `G2 an AI statement is never shown and a status question is always answered by the application`() {
        val statement = turn("Học 45p", "Cứ yên chí đi, phần việc đó mình lo trọn gói rồi nhé.").chatAnswer!!
        assertTrue(statement.headline.startsWith("Chưa có gì được lưu"))
        val status = turn("Ghi chưa?", "Bạn muốn ghi môn gì?").chatAnswer!!
        assertTrue(status.headline.startsWith("Chưa có gì được lưu"))
        assertTrue(status.lines.joinToString().contains("Hiện không có đề xuất nào đang chờ"))
    }

    @Test
    fun `G2 the card composition is pure and the chat screen shows the headline first and labels a clarification as asking back`() {
        val out = ParseChatOutcome(emptyList(), emptyList(), OutcomeSource.CLOUD, reply = "Bạn muốn ghi 2 giờ hay 3 giờ?")
        val card = WallyTurnPolicy.buildCard("Học 2h, à không, 3h", out, emptyList(), 0)!!
        assertEquals("Bạn muốn ghi 2 giờ hay 3 giờ?", card.headline)
        val ui = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        assertTrue(ui.contains("""text = answer.label ?: if (answer.isClarification) "Cần hỏi lại" else "Wally""""))
        assertTrue("headline được vẽ trước các dòng", ui.indexOf("text = answer.headline") in 1 until ui.indexOf("answer.lines.forEach"))
        val policy = File("src/main/java/com/timewallet/mvp/domain/usecase/WallyTurnPolicy.kt").readText()
        assertTrue("AI không bao giờ là nguồn của 'đã lưu': chính sách vẫn không có chuỗi 'Đã lưu'", !Regex("""(?i)\bĐã lưu\b""").containsMatchIn(policy.substringAfter("object WallyTurnPolicy")))
    }

    // ======================================================================== G3: giờ tương lai, PLANNED, ACTIVE
    private fun proposalJson(date: LocalDate, start: String, end: String, seconds: Long) =
        """{"title":"Học","category":"learn","durationSeconds":$seconds,"dateLocal":"$date","startLocal":"$start","endLocal":"$end","timeEvidence":"explicit_range"}"""

    private fun answerWith(vararg p: String) = """{"reply":null,"proposals":[${p.joinToString(",")}],"queries":[]}"""

    private fun confirm(p: ActivityProposal, nowMs: Long, existing: List<LedgerRecord> = emptyList()): LedgerRecord {
        val r = LedgerValidator.validateBatchAtomic(listOf(EditableProposalDraft.fromProposal(p).toBatchInput()), existing, null, nowMs, zone)
        return (r as BatchValidationResult.Valid).records.single()
    }

    @Test
    fun `G3 a range of a PAST date is not flagged as planned just because its clock time is later than the clock now`() {
        // Hôm qua học từ 20h đến 22h, nói lúc 8h sáng: 20:00 "sau" 08:00 trên đồng hồ nhưng là hôm qua.
        val p = parse("Hôm qua tôi học từ 20h đến 22h", answerWith(proposalJson(yesterday, "20:00", "22:00", 7200)), LocalTime.of(8, 0)).proposals.single()
        assertEquals(yesterday.toString(), p.dateLocal)
        assertFalse("ngày đã qua không phải kế hoạch", p.isFuturePlanned)
        val rec = confirm(p, ms(today, 8))
        assertEquals(TimeLayer.REPORTED_TIMED, rec.layer)
        assertEquals("CONFIRMED", rec.status)
        assertEquals(7200L, rec.durationSeconds)
        // qua nửa đêm, nói lúc 21h: "22:00" vẫn đứng sau 21:00 trên đồng hồ
        val sleep = parse("Hôm qua tôi ngủ từ 22h đến 6h", answerWith(proposalJson(yesterday, "22:00", "06:00", 8 * 3600L)), LocalTime.of(21, 0)).proposals.single()
        assertFalse(sleep.isFuturePlanned)
        assertEquals(TimeLayer.REPORTED_TIMED, confirm(sleep, ms(today, 21)).layer)
    }

    @Test
    fun `G3 the same clock time TODAY is still planned and a range that has begun is not (characterization)`() {
        val later = parse("Học từ 20h đến 22h", answerWith(proposalJson(today, "20:00", "22:00", 7200)), LocalTime.of(8, 0)).proposals.single()
        assertTrue(later.isFuturePlanned)
        assertEquals(TimeLayer.PLANNED, confirm(later, ms(today, 8)).layer)
        val begun = parse("Học từ 14h đến 17h", answerWith(proposalJson(today, "14:00", "17:00", 10800)), LocalTime.of(15, 0)).proposals.single()
        assertFalse(begun.isFuturePlanned)
        assertEquals(TimeLayer.ACTIVE, confirm(begun, ms(today, 15)).layer)
        val tomorrow = parse("Ngày mai học từ 9h đến 10h", answerWith(proposalJson(today.plusDays(1), "09:00", "10:00", 3600)), LocalTime.of(15, 0)).proposals.single()
        assertTrue(tomorrow.isFuturePlanned)
    }

    private fun record(
        title: String, cat: String, date: LocalDate, from: Pair<Int, Int>, to: Pair<Int, Int>, nowMs: Long, existing: List<LedgerRecord> = emptyList(),
        planned: Boolean = false, durationMinutes: Int? = null
    ): ValidationResult {
        val range = LedgerValidator.parseTimeRangeToEpochMillis(date.toString(), "%02d:%02d".format(from.first, from.second), "%02d:%02d".format(to.first, to.second), zone)!!
        return LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = title, rawCategory = cat, dateLocal = date.toString(), inputDurationMinutes = durationMinutes,
            startedAt = range.first, endedAt = range.second, existingRecords = existing, nowEpochMs = nowMs, zoneId = zone, convertFutureToPlanned = planned
        )
    }

    private fun valid(v: ValidationResult) = (v as ValidationResult.Valid).record

    @Test
    fun `G3 the dice engine counts only the part of an ACTIVE record that has happened, exactly like the day statistics`() {
        val now = ms(today, 14, 35)
        val active = valid(record("Làm sâu", "work", today, 13 to 0, 18 to 0, now)) // 13:00-18:00 nhập lúc 14:35 => ACTIVE
        assertEquals(TimeLayer.ACTIVE, active.layer)
        val stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(active), zone, now)
        assertEquals(5_700L, stats.timedSeconds)
        val ctx = DopamineTimeDiceEngine.deriveContextFromRecords(today.toString(), listOf(active), now)
        assertEquals("95 phút đã trôi qua, không phải 300 phút đã khai báo", 95, ctx.todayFocusMinutes)
        assertEquals(95, ctx.recentContinuousFocusMinutes)
        // một việc ACTIVE đã quá giờ kết thúc nhưng chưa được xác nhận chỉ giữ phần đã xác nhận lúc khai báo (13:00-14:00)
        val declaredAt = ms(today, 14)
        val expired = valid(record("Đọc", "learn", today, 13 to 0, 15 to 0, declaredAt))
        assertEquals(TimeLayer.ACTIVE, expired.layer)
        val later = ms(today, 16)
        assertEquals(3_600L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(expired), zone, later).timedSeconds)
        assertEquals(60, DopamineTimeDiceEngine.deriveContextFromRecords(today.toString(), listOf(expired), later).todayFocusMinutes)
    }

    @Test
    fun `G3 a PLANNED record counts nowhere, not in statistics, the journal total nor the dice context (characterization)`() {
        val now = ms(today, 14, 35)
        val planned = valid(record("Học tối", "learn", today, 19 to 0, 21 to 0, now))
        assertEquals(TimeLayer.PLANNED, planned.layer)
        assertEquals(0L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(planned), zone, now).timedSeconds)
        assertEquals(0L, JournalDayTotal.seconds(listOf(planned), now))
        assertEquals(0, DopamineTimeDiceEngine.deriveContextFromRecords(today.toString(), listOf(planned), now).todayFocusMinutes)
        val past = valid(record("Học cũ", "learn", today, 8 to 0, 10 to 0, now, planned = true)) // người dùng chọn "Dự kiến" cho giờ đã qua
        assertEquals(TimeLayer.PLANNED, past.layer)
        assertEquals(0L, LedgerAnalyticsCalculator.computeDayStats(today.toString(), listOf(past), zone, now).timedSeconds)
    }

    @Test
    fun `G3 PLANNED skips only the overlap check, never the duration, date or 24-hour rules, and cannot become actual without it (characterization)`() {
        val now = ms(today, 14, 35)
        // trùng một bản ghi thực tế: được phép khi là kế hoạch
        val actual = valid(record("Họp", "work", today, 9 to 0, 10 to 0, now))
        assertEquals(TimeLayer.REPORTED_TIMED, actual.layer)
        val overlappingPlan = valid(record("Kế hoạch trùng", "work", today, 9 to 30, 10 to 30, now, existing = listOf(actual), planned = true))
        assertEquals(TimeLayer.PLANNED, overlappingPlan.layer)
        // nhưng thời lượng sai vẫn bị chặn khi là kế hoạch
        assertTrue(record("Sai", "work", today, 19 to 0, 21 to 0, now, planned = true, durationMinutes = 30) is ValidationResult.Invalid)
        val untimed = LedgerValidator.validateAndBuildRecord(
            source = "chat", rawTitle = "x", rawCategory = "work", dateLocal = today.plusDays(2).toString(), inputDurationMinutes = 0,
            startedAt = null, endedAt = null, existingRecords = emptyList(), nowEpochMs = now, zoneId = zone, convertFutureToPlanned = true
        )
        assertTrue(untimed is ValidationResult.Invalid)
        // không thể thành thực tế khi trùng, chưa tới giờ, hoặc ở ngày tương lai
        val past = valid(record("Kế hoạch giờ đã qua", "work", today, 9 to 30, 10 to 30, now, existing = listOf(actual), planned = true))
        assertTrue(LedgerValidator.confirmPlannedOrActiveToActual(past, listOf(actual), null, now, zone) is ValidationResult.Invalid)
        val futurePlan = valid(record("Tối", "work", today, 19 to 0, 21 to 0, now))
        assertTrue(LedgerValidator.confirmPlannedOrActiveToActual(futurePlan, emptyList(), null, now, zone) is ValidationResult.Invalid)
        val tomorrowPlan = valid(record("Mai", "work", today.plusDays(1), 9 to 0, 10 to 0, now))
        assertTrue(LedgerValidator.confirmPlannedOrActiveToActual(tomorrowPlan, emptyList(), null, now, zone) is ValidationResult.Invalid)
    }

    @Test
    fun `G3 a range that spans now is ACTIVE and still obeys the overlap rule, and a future record never overlaps an actual one (characterization)`() {
        val now = ms(today, 14, 35)
        val actual = valid(record("Đọc", "learn", today, 14 to 0, 14 to 20, now))
        val spanning = record("Làm sâu", "work", today, 13 to 0, 18 to 0, now, existing = listOf(actual))
        assertTrue("ACTIVE vẫn bị chặn khi trùng: $spanning", spanning is ValidationResult.Invalid)
        val free = valid(record("Làm sâu", "work", today, 14 to 20, 18 to 0, now, existing = listOf(actual)))
        assertEquals(TimeLayer.ACTIVE, free.layer)
        assertEquals("ACTIVE", free.status)
    }

    @Test
    fun `G3 a range across midnight is classified from its real start and end, and its second half still takes part in overlap checks (characterization)`() {
        val range = LedgerValidator.parseTimeRangeToEpochMillis(yesterday.toString(), "22:00", "06:00", zone)!!
        assertEquals(8 * 3_600_000L, range.second - range.first)
        // hôm qua 22:00-06:00, lúc 10h sáng: đã xảy ra xong
        val slept = valid(record("Ngủ", "sleep", yesterday, 22 to 0, 6 to 0, ms(today, 10)))
        assertEquals(TimeLayer.REPORTED_TIMED, slept.layer)
        // hôm nay 22:00-06:00: lúc 10h là kế hoạch, lúc 23h30 là đang diễn ra
        assertEquals(TimeLayer.PLANNED, valid(record("Ngủ", "sleep", today, 22 to 0, 6 to 0, ms(today, 10))).layer)
        assertEquals(TimeLayer.ACTIVE, valid(record("Ngủ", "sleep", today, 22 to 0, 6 to 0, ms(today, 23, 30))).layer)
        // nửa sau của giấc ngủ (00:00-06:00 hôm nay) chặn một bản ghi 05:00-06:30 hôm nay
        assertTrue(record("Dậy sớm", "work", today, 5 to 0, 6 to 30, ms(today, 10), existing = listOf(slept)) is ValidationResult.Invalid)
    }

    @Test
    fun `G3 a range whose end equals its start is not read as a full 24 hours`() {
        assertNull(LedgerValidator.parseTimeRangeToEpochMillis(today.toString(), "08:00", "08:00", zone))
        // đường sửa bản ghi / thẻ đề xuất có thể để trống số phút: trước đây 08:00-08:00 thành bản ghi 24 giờ
        val r = LedgerValidator.validateBatchAtomic(
            listOf(BatchRecordInput(rawTitle = "Học", rawCategory = "learn", dateLocal = yesterday.toString(), durationMinutes = null, startHm = "08:00", endHm = "08:00")),
            emptyList(), null, ms(today, 10), zone
        )
        assertTrue("start = end phải bị từ chối: $r", r is BatchValidationResult.Invalid)
        // qua nửa đêm thật vẫn chạy
        val ok = LedgerValidator.validateBatchAtomic(
            listOf(BatchRecordInput(rawTitle = "Ngủ", rawCategory = "sleep", dateLocal = yesterday.toString(), durationMinutes = null, startHm = "22:00", endHm = "06:00")),
            emptyList(), null, ms(today, 10), zone
        )
        assertEquals(8 * 3600L, (ok as BatchValidationResult.Valid).records.single().durationSeconds)
    }

    @Test
    fun `G3 manual entry, chat, and edit accept future entries as PLANNED without ever counting them into actual time (characterization)`() {
        val m = ManualEntry.validate("work", "1", "0", today.plusDays(1).toString(), "x", today)
        assertTrue(m is ManualEntryValidation.Valid && m.saveAsPlanned)
        val future = LedgerValidator.validateAndBuildRecord(
            source = "manual", rawTitle = "x", rawCategory = "work", dateLocal = today.plusDays(3).toString(), inputDurationMinutes = 60,
            startedAt = null, endedAt = null, existingRecords = emptyList(), nowEpochMs = ms(today, 10), zoneId = zone, convertFutureToPlanned = false
        )
        val rec = valid(future)
        assertEquals(TimeLayer.PLANNED, rec.layer)
        assertEquals(0L, LedgerAnalyticsCalculator.computeDayStats(today.plusDays(3).toString(), listOf(rec), zone, ms(today, 10)).untimedSeconds)
    }

    @Test
    fun `G3 over the real commit path (BatchConfirm and the DAO) a future range is stored as PLANNED, an ACTIVE one still blocks an overlapping actual record (characterization)`() {
        val dao = InMemoryLedgerDao()
        val now = ms(today, 14, 35)
        fun input(title: String, from: String, to: String, minutes: Int) = BatchRecordInput(
            rawTitle = title, rawCategory = "work", dateLocal = today.toString(), durationMinutes = minutes, startHm = from, endHm = to
        )
        fun run(vararg items: BatchRecordInput) = runBlocking {
            BatchConfirm.run(dao, items.toList(), null, { null }, now, zone) { now }
        }
        // kế hoạch chiều tối (hai mục chồng nhau, được phép vì chưa xảy ra) + một việc đang diễn ra 13:00-18:00
        val first = run(input("Tối 1", "19:00", "21:00", 120), input("Tối 2", "20:00", "20:30", 30), input("Đang làm", "13:00", "18:00", 300))
        assertEquals(3, (first as ConfirmResult.Committed).savedCount)
        val rows = runBlocking { dao.getAllRecordsSnapshot() }.associateBy { it.title }
        assertEquals("PLANNED", rows.getValue("Tối 1").layer); assertEquals("PLANNED", rows.getValue("Tối 1").status)
        assertEquals("PLANNED", rows.getValue("Tối 2").status)
        assertEquals("ACTIVE", rows.getValue("Đang làm").layer)
        // một bản ghi thực tế trùng phần đã trôi qua của việc đang diễn ra bị chặn, không ghi gì
        val clash = run(input("Họp", "14:00", "14:20", 20))
        assertTrue("$clash", clash is ConfirmResult.Invalid)
        assertEquals(3, runBlocking { dao.getAllRecordsSnapshot() }.size)
        // thống kê: chỉ phần đã qua của việc đang diễn ra (13:00-14:35), không có kế hoạch nào
        val stats = LedgerAnalyticsCalculator.computeDayStats(today.toString(), runBlocking { dao.getAllRecordsSnapshot() }.map { it.toDomain() }, zone, now)
        assertEquals(5_700L, stats.timedSeconds)
    }
}
