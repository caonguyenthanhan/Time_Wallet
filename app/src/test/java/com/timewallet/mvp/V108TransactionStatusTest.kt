package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.usecase.AiQueryValidator
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.LedgerValidator
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.ValidationResult
import com.timewallet.mvp.domain.usecase.WallyTurnPolicy
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.TimeWalletUiState
import com.timewallet.mvp.ui.WallyChatReducer
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
 * WALLY v1.0.8 — V106-N1 đóng theo KIẾN TRÚC (V107-F1/F2): trạng thái giao dịch do ứng dụng phát ngôn.
 *
 * Mọi test chạy PIPELINE SẢN XUẤT: JSON phản hồi giả -> GeminiProposalClient.parseAndValidateResponseJson -> WallyChatReducer
 * (-> WallyTurnPolicy) trên các loại lượt RECORD, MIXED, CHAT (liên quan / không liên quan ghi nhận), CLARIFY và khi đã có
 * đề xuất chờ. Nguồn gốc các bộ mẫu được ghi rõ từng chỗ (không gộp mẫu tái dựng vào mẫu của Reviewer).
 */
class V108TransactionStatusTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val nowMs = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    // ------------------------------------------------------------------ bộ mẫu
    /** 14 mẫu ADV_REPLYGUARD GỐC do Reviewer khôi phục nguyên văn, REVIEW_WALLY_V107.md mục 3.2 (không phải mẫu tái dựng). */
    private val reviewerOriginal14 = listOf(
        "Xong xuôi rồi nhé, mục học 45p của bạn đã vào danh sách.",
        "Mình vừa xử lý xong việc ghi lại 45p học tập cho bạn rồi.",
        "Hoạt động học 45p đã được cập nhật vào ngày hôm nay của bạn.",
        "Tất cả xong rồi bạn nhé, 45p đã có mặt trong Nhật ký.",
        "Ok bạn, mình đã lưu giúp bạn 45p học rồi.",
        "Yên tâm, hoạt động học đã tự động hoàn tất rồi nhé!",
        "Mọi thứ đã được chốt vào sổ rồi.",
        "Mình đã cập nhật hoạt động vào lịch hôm nay.",
        "Hoạt động của bạn đã được lưu trữ xong xuôi.",
        "Không cần làm gì thêm đâu, mình giữ giúp bạn rồi.",
        "Your activity is now recorded in your journal.",
        "Got it, logged 45 mins for you.",
        "Đề xuất của bạn đã hoàn tất lưu vào nhật ký.",
        "Sổ cái đã nhận 45p học tập của bạn."
    )

    /** 6 mẫu vòng 2 của v1.0.7 (do Master phát hiện bằng probe độc lập; xem V107Round2Test). */
    private val round2Six = listOf(
        "Ok, mình đã chép vào sổ giúp bạn.", "Mình note lại rồi.", "Mọi thứ đã sẵn sàng trong sổ của bạn.",
        "Hoạt động học đã được tạo.", "Bản ghi đã sẵn có.", "Mình hoàn thành việc nhập liệu rồi."
    )

    /** 4 mẫu mới của V107-F1/F2 (REVIEW_WALLY_V107.md mục 3.2): không có số, không có "đã/vừa". */
    private val v107New4 = listOf(
        "Ghi nhận hoạt động học cho bạn rồi nhé.",
        "Hoàn tất cho bạn rồi nhé!",
        "Xong việc ghi nhận cho bạn rồi đó.",
        "Yên tâm nhé, mọi việc ổn thỏa hết rồi đó bạn."
    )

    /** Câu lách mà KHÔNG regex nào bắt được: chỉ kiến trúc mới giữ được. */
    private val evasive = "Cứ yên chí đi, phần việc đó mình lo trọn gói rồi nhé."

    private val all24 = reviewerOriginal14 + round2Six + v107New4

    // ------------------------------------------------------------------ helpers sản xuất
    private fun prop(minutes: Int = 45, title: String = "Học") = ActivityProposal(
        id = "p-$title-$minutes", rawInput = "Học 45p", title = title, category = "learn", durationSeconds = minutes * 60L,
        dateLocal = today.toString(), startLocal = null, endLocal = null, timeEvidence = "duration_only",
        missingFields = listOf("startLocal", "endLocal"), needsConfirmation = true, isFuturePlanned = false
    )

    private fun proposalJson(minutes: Int = 45) =
        """{"title":"Học","category":"learn","durationSeconds":${minutes * 60},"dateLocal":"$today","timeEvidence":"duration_only"}"""

    private fun queryJson() = """{"metric":"UNTIMED","startDate":"$today","endDate":"$today"}"""

    private fun envelope(inner: String) =
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()

    private fun inner(reply: String?, proposals: Int, queries: Int): String {
        val o = JSONObject()
        if (reply != null) o.put("reply", reply) else o.put("reply", JSONObject.NULL)
        o.put("proposals", JSONArray().also { a -> repeat(proposals) { a.put(JSONObject(proposalJson())) } })
        o.put("queries", JSONArray().also { a -> repeat(queries) { a.put(JSONObject(queryJson())) } })
        return o.toString()
    }

    private fun ledgerRecord(minutes: Int): LedgerRecord = (LedgerValidator.validateAndBuildRecord(
        source = "manual", rawTitle = "Đã lưu", rawCategory = "learn", dateLocal = today.toString(), inputDurationMinutes = minutes,
        startedAt = null, endedAt = null, existingRecords = emptyList(), nowEpochMs = nowMs, zoneId = zone
    ) as ValidationResult.Valid).record

    /** Chạy hết đường sản xuất của một lượt và trả (state sau lượt, outcome). */
    private fun turn(
        question: String, reply: String?, proposals: Int, queries: Int,
        pendingBefore: List<EditableProposalDraft> = emptyList(), records: List<LedgerRecord> = emptyList()
    ): Pair<TimeWalletUiState, com.timewallet.mvp.domain.usecase.ParseChatOutcome> {
        val out = GeminiProposalClient.parseAndValidateResponseJson(envelope(inner(reply, proposals, queries)), question, today, LocalTime.of(18, 0))
        val before = TimeWalletUiState(proposals = pendingBefore, records = records)
        val merged = pendingBefore + out.proposals.map { EditableProposalDraft.fromProposal(it) }
        val after = WallyChatReducer.applyResult(before, question, out, merged, null) { recs, timer ->
            out.queries.map { LedgerQueryEngine.execute(it, recs, zone, nowMs, timer) }
        }
        return after to out
    }

    private fun cardText(s: TimeWalletUiState): String =
        s.chatAnswer?.let { (listOf(it.headline) + it.lines + it.notes).joinToString(" | ") }.orEmpty()

    private fun distinctive(adv: String) = adv.take(18)

    // =========================================================================== ReplyGuard trực tiếp (tầng regex, heuristic)
    @Test
    fun `ReplyGuard blocks all 14 original reviewer samples, the 6 round-2 samples and the 4 new V107 samples`() {
        all24.forEach { adv ->
            assertNull("phải bị ReplyGuard chặn: $adv", ReplyGuard.sanitize(adv, "Học 45p", emptyList(), false).first)
            assertNull("(có đề xuất) phải bị chặn: $adv", ReplyGuard.sanitize(adv, "Học 45p", listOf(prop()), false).first)
        }
        assertEquals(14, reviewerOriginal14.size); assertEquals(6, round2Six.size); assertEquals(4, v107New4.size)
    }

    @Test
    fun `honest replies are not blocked by the new patterns`() {
        listOf(
            "Bạn xác nhận để mình ghi nhé.", "Mình sẽ ghi giúp bạn sau khi bạn xác nhận nhé.", "Mình chưa ghi gì cả.",
            "Mình sẽ không tự động xác nhận thay bạn.", "Bạn đã thử dùng đồng hồ bấm giờ chưa?", "Xin chào, mình giúp gì được cho bạn?",
            "Cảm ơn bạn nhé!", "Bạn muốn xem hôm nay hay cả tuần?"
        ).forEach { assertNotNull("không được chặn nhầm: $it", ReplyGuard.sanitize(it, "x", emptyList(), false).first) }
    }

    // =========================================================================== RECORD
    @Test
    fun `RECORD - no AI sentence reaches the user for any of the 24 samples and the correct proposal is kept`() {
        all24.forEach { adv ->
            val (after, out) = turn("Học 45p", adv, proposals = 1, queries = 0)
            assertNull("RECORD: reply phải null: $adv", out.reply)
            assertEquals("đề xuất đúng được giữ", 1, after.proposals.size)
            assertEquals(45, after.proposals.single().durationMinutes)
            assertNull("RECORD không dựng thẻ lời đáp AI", after.chatAnswer)
            assertEquals("", after.chatInputText)
        }
    }

    // =========================================================================== MIXED (V107-F2)
    @Test
    fun `MIXED - the AI text is gone, the valid query is kept and answered by the engine with an application lead line`() {
        val ledger = listOf(ledgerRecord(30))
        all24.forEach { adv ->
            val (after, out) = turn("Học 45p, tổng hôm nay?", adv, proposals = 1, queries = 1, records = ledger)
            assertNull("MIXED: reply phải null: $adv", out.reply)
            assertEquals(1, out.proposals.size); assertEquals(1, out.queries.size)
            assertEquals(1, after.proposals.size)
            val card = after.chatAnswer!!
            assertFalse("thẻ MIXED không được mang lời AI: ${cardText(after)}", cardText(after).contains(distinctive(adv)))
            assertEquals(WallyTurnPolicy.MIXED_LEAD_LINE, card.lines.first())
            assertFalse("MIXED không phải thẻ trạng thái cũ đi (không có số đề xuất)", card.isTransactionStatus)
        }
    }

    @Test
    fun `MIXED - query numbers include only saved records, never the uncommitted drafts`() {
        val ledger = listOf(ledgerRecord(30)) // 30 phút chưa gắn giờ đã lưu
        val pending = listOf(EditableProposalDraft.fromProposal(prop(90, "Đang chờ")))
        val (after, _) = turn("Học 45p, chưa gắn giờ hôm nay?", null, proposals = 1, queries = 1, pendingBefore = pending, records = ledger)
        assertEquals("2 đề xuất chờ (1 cũ + 1 mới), chưa commit", 2, after.proposals.size)
        val answer = LedgerQueryEngine.execute(
            (AiQueryValidator.validate("UNTIMED", today.toString(), today.toString(), null, null, null, today) as AiQueryValidator.Result.Ok).intent,
            ledger, zone, nowMs
        )
        assertEquals("số liệu = đúng bản ghi đã lưu (30p), không cộng 45p/90p của draft", 1800L, answer.figures.untimedSeconds)
        assertTrue(after.chatAnswer!!.headline.contains("30p"))
        assertFalse(cardText(after).contains("2h 45p") || cardText(after).contains("2h 15p") || cardText(after).contains("1h 15p"))
    }

    // =========================================================================== CHAT liên quan ghi nhận (V107-F1)
    @Test
    fun `CHAT about recording - the application status is the headline and none of the 24 samples nor the evasive reply is shown`() {
        (all24 + evasive).forEach { adv ->
            val (after, _) = turn("Học 45p", adv, proposals = 0, queries = 0)
            val card = after.chatAnswer!!
            assertTrue("headline phải là trạng thái của ứng dụng: ${card.headline}", card.headline.startsWith("Chưa có gì được lưu"))
            assertFalse("lời AI không được hiện ở bất kỳ đâu: ${cardText(after)}", cardText(after).contains(distinctive(adv)))
            assertTrue(card.isTransactionStatus)
            assertTrue(after.proposals.isEmpty())
        }
    }

    @Test
    fun `CHAT status questions are answered by the application, with and without pending drafts`() {
        listOf("Ghi chưa?", "Đã lưu chưa?", "Xong chưa bạn?", "Lưu rồi à?").forEach { q ->
            val (none, _) = turn(q, evasive, 0, 0)
            assertTrue(none.chatAnswer!!.headline, none.chatAnswer!!.headline.startsWith("Chưa có gì được lưu"))
            assertTrue(none.chatAnswer!!.lines.joinToString().contains("Hiện không có đề xuất nào đang chờ"))
            val pending = listOf(EditableProposalDraft.fromProposal(prop(45)), EditableProposalDraft.fromProposal(prop(30)))
            val (two, _) = turn(q, evasive, 0, 0, pendingBefore = pending)
            assertEquals("Chưa có gì được lưu. Còn 2 đề xuất đang chờ bạn xác nhận.", two.chatAnswer!!.headline)
            assertEquals(2, two.proposals.size)
            assertFalse(cardText(two).contains("lo trọn gói"))
        }
    }

    @Test
    fun `CHAT with pending drafts - even an unrelated sentence shows the application status instead of an AI reassurance`() {
        val pending = listOf(EditableProposalDraft.fromProposal(prop(45)))
        listOf("Cảm ơn nhé", "Ok", "Hôm nay trời đẹp").forEach { q ->
            val (after, _) = turn(q, "Yên tâm nhé, mọi việc ổn thỏa hết rồi đó bạn.", 0, 0, pendingBefore = pending)
            assertEquals("Chưa có gì được lưu. Còn 1 đề xuất đang chờ bạn xác nhận.", after.chatAnswer!!.headline)
            assertEquals(1, after.proposals.size)
        }
    }

    // =========================================================================== CLARIFY
    @Test
    fun `CLARIFY - a recording request for which the AI created no proposal says nothing was saved and asks to clarify`() {
        val (after, out) = turn("Học 45p", null, 0, 0)
        assertNull(out.reply)
        val card = after.chatAnswer!!
        assertTrue(card.headline, card.headline.startsWith("Chưa có gì được lưu"))
        assertTrue(card.lines.joinToString(), card.lines.joinToString().contains("Học 45p") && card.lines.joinToString().contains("Ghi thủ công"))
        assertTrue(card.isClarification)
    }

    @Test
    fun `CLARIFY - a non-recording sentence keeps the ordinary clarification and does not claim anything about saving`() {
        val (after, _) = turn("Hôm nay trời đẹp quá", null, 0, 0)
        val text = cardText(after)
        assertFalse(text, text.startsWith("Chưa có gì được lưu"))
        assertNotNull(after.chatAnswer)
    }

    // =========================================================================== CHAT không liên quan giao dịch giữ tự nhiên
    @Test
    fun `CHAT unrelated to transactions keeps the natural AI reply`() {
        listOf("Xin chào" to "Chào bạn! Hôm nay bạn muốn làm gì?", "Cảm ơn Wally" to "Không có gì nhé, mình luôn sẵn sàng giúp bạn.",
            "Bạn đã thử dùng đồng hồ bấm giờ chưa?" to "Mình là trợ lý nên chưa thử được, bạn dùng thử nhé!",
            "3 giờ chiều tôi nên làm gì?" to "Bạn có thể nghỉ ngơi một chút rồi quay lại việc chính.").forEach { (q, reply) ->
            val (after, _) = turn(q, reply, 0, 0)
            assertEquals("câu hỏi không liên quan giao dịch: $q", reply, after.chatAnswer!!.headline)
            assertFalse(after.chatAnswer!!.isTransactionStatus)
        }
    }

    @Test
    fun `turn classification and transaction relatedness`() {
        assertTrue(listOf("Học 45p", "Ghi chưa?", "Đã lưu chưa", "xong chưa", "Ghi giúp mình việc học", "Học hai tiếng", "Lưu lại giúp mình").all { WallyTurnPolicy.isTransactionRelated(it) })
        assertFalse(listOf("Xin chào", "Cảm ơn Wally", "Bạn đã thử dùng đồng hồ bấm giờ chưa?", "3 giờ chiều tôi nên làm gì?", "Hôm nay tôi dùng bao nhiêu giờ?", "Ghi chú là gì?").any { WallyTurnPolicy.isTransactionRelated(it) })
        assertEquals(WallyTurnPolicy.Kind.RECORD, WallyTurnPolicy.classify("x", 1, 0, 1))
        assertEquals(WallyTurnPolicy.Kind.MIXED, WallyTurnPolicy.classify("x", 1, 1, 1))
        assertEquals(WallyTurnPolicy.Kind.QUERY, WallyTurnPolicy.classify("x", 0, 1, 0))
        assertEquals(WallyTurnPolicy.Kind.TRANSACTION_CHAT, WallyTurnPolicy.classify("x", 0, 0, 2))
        assertEquals(WallyTurnPolicy.Kind.PLAIN_CHAT, WallyTurnPolicy.classify("Xin chào", 0, 0, 0))
    }

    // =========================================================================== QUERY giữ nguyên
    @Test
    fun `QUERY - the engine answer is kept and an AI completion claim is not shown while drafts are pending`() {
        val pending = listOf(EditableProposalDraft.fromProposal(prop(45)))
        val (after, out) = turn("Hôm nay chưa gắn giờ bao nhiêu?", "Ổn thỏa nhé, đây là số liệu.", 0, 1, pendingBefore = pending, records = listOf(ledgerRecord(30)))
        assertEquals(1, out.queries.size)
        val headline = after.chatAnswer!!.headline
        assertFalse("không được hiện lời AI kiểu hoàn tất khi còn đề xuất chờ: $headline", headline.contains("Ổn thỏa"))
        assertEquals(1, after.proposals.size)
        val (clean, _) = turn("Hôm nay chưa gắn giờ bao nhiêu?", "Để mình tính giúp bạn nhé.", 0, 1, records = listOf(ledgerRecord(30)))
        assertEquals("Để mình tính giúp bạn nhé.", clean.chatAnswer!!.headline)
    }

    // =========================================================================== thẻ trạng thái không được cũ đi sau khi commit
    @Test
    fun `a transaction status card is cleared when the drafts change so it can never go stale`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val discard = vm.substring(vm.indexOf("fun discardProposal("), vm.indexOf("fun confirmSingleProposal("))
        assertEquals("discard + clearAll đều phải xóa thẻ trạng thái", 2, Regex("isTransactionStatus").findAll(discard).count())
        val confirm = vm.substring(vm.indexOf("private fun confirmProposalsList("), vm.indexOf("LUỒNG C: HISTORY"))
        assertTrue(confirm.contains("takeUnless { it.isTransactionStatus }"))
        // Chỉ toast do ứng dụng báo "Đã lưu", và chỉ sau commit thành công.
        assertTrue(confirm.indexOf("is ConfirmResult.Committed") < confirm.indexOf("ConfirmFeedback.message"))
        val policy = File("src/main/java/com/timewallet/mvp/domain/usecase/WallyTurnPolicy.kt").readText()
        assertFalse("không có đường nào của chính sách lượt tự báo 'đã lưu'", Regex("""(?i)\bĐã lưu\b""").containsMatchIn(policy.substringAfter("object WallyTurnPolicy")))
    }

    @Test
    fun `the proposal batch card states pending status itself`() {
        val ui = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        assertTrue(ui.contains("hoạt động đề xuất · chờ bạn xác nhận"))
        assertTrue(ui.contains("Chưa lưu vào sổ cho tới khi bạn bấm Xác nhận."))
    }

    @Test
    fun `the client never returns AI free text together with a proposal`() {
        val src = File("src/main/java/com/timewallet/mvp/data/ai/GeminiProposalClient.kt").readText()
        assertTrue(src.contains("val finalReply = if (policyBlocked || hasProposals) null else cleanReply"))
    }
}
