package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ParseChatOutcome
import com.timewallet.mvp.domain.usecase.ReplyGuard
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
import java.time.LocalDate
import java.time.LocalTime

/**
 * WALLY v1.0.7 vòng 2 (V106-N1, phòng thủ cấu trúc): lời đáp chỉ-AI (không đề xuất, không truy vấn) luôn kèm dòng trạng thái do
 * ỨNG DỤNG tạo, kể cả khi AI dùng cách nói chưa có trong regex; và các cách nói còn lọt ở vòng 1 (kiểm độc lập bằng probe) nay bị chặn.
 */
class V107Round2Test {

    private val today = LocalDate.of(2026, 11, 13)

    private fun prop(minutes: Int = 45) = ActivityProposal(
        id = "p$minutes", rawInput = "Học 45p", title = "Học", category = "learn", durationSeconds = minutes * 60L,
        dateLocal = today.toString(), startLocal = null, endLocal = null, timeEvidence = "duration_only",
        missingFields = listOf("startLocal", "endLocal"), needsConfirmation = true, isFuturePlanned = false
    )

    private fun envelope(inner: String) =
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", inner)))).toString()

    private fun parseChatOnly(reply: String, userText: String) = GeminiProposalClient.parseAndValidateResponseJson(
        envelope(JSONObject().put("reply", reply).put("proposals", JSONArray()).put("queries", JSONArray()).toString()),
        userText, today, LocalTime.of(10, 0)
    )

    // ------------------------------------------------------------------ cách nói còn lọt ở vòng 1 (kiểm độc lập)
    @Test
    fun `paraphrases that slipped past the first round regex are now blocked with and without a proposal`() {
        val slipped = listOf(
            "Ok, mình đã chép vào sổ giúp bạn.", "Mình note lại rồi.", "Mọi thứ đã sẵn sàng trong sổ của bạn.",
            "Hoạt động học đã được tạo.", "Bản ghi đã sẵn có.", "Mình hoàn thành việc nhập liệu rồi."
        )
        slipped.forEach { s ->
            assertNull("(không đề xuất) phải bị chặn: $s", ReplyGuard.sanitize(s, "Học 45p", emptyList(), false).first)
            assertNull("(có đề xuất) phải bị chặn: $s", ReplyGuard.sanitize(s, "Học 45p", listOf(prop()), false).first)
        }
    }

    @Test
    fun `honest wording is still allowed`() {
        listOf(
            "Bạn xác nhận để mình ghi nhé.", "Mình chưa ghi gì cả, bạn kiểm tra thẻ đề xuất nhé.", "Mình sẽ không tự động xác nhận thay bạn.",
            "Bạn muốn xem hôm nay hay cả tuần?", "Chào bạn! Hôm nay bạn muốn làm gì?", "Bạn nhập thời lượng giúp mình nhé."
        ).forEach { assertNotNull("không được chặn nhầm: $it", ReplyGuard.sanitize(it, "x", emptyList(), false).first) }
    }

    // ------------------------------------------------------------------ phòng thủ cấu trúc: dòng trạng thái do ứng dụng tạo
    // Không khớp mẫu regex nào (kể cả các mẫu bổ sung ở v1.0.8): chỉ kiến trúc "trạng thái do ứng dụng phát ngôn" giữ được.
    private val evasive = "Cứ yên chí đi, phần việc đó mình lo trọn gói rồi nhé."

    // v1.0.8 (V107-F1): trạng thái do ỨNG DỤNG tạo là headline của thẻ, không phải chú thích nhỏ bên dưới lời AI; lời AI bị bỏ.
    @Test
    fun `an evasive chat-only reply to a recording sentence is replaced by an application status that nothing was saved`() {
        assertNotNull("mẫu thử phải lọt regex để chứng minh phòng thủ cấu trúc", ReplyGuard.sanitize(evasive, "Học 45p", emptyList(), false).first)
        val out = parseChatOnly(evasive, "Học 45p")
        assertEquals(evasive, out.reply)
        val after = WallyChatReducer.applyResult(TimeWalletUiState(), "Học 45p", out, emptyList(), null) { _, _ -> emptyList() }
        val card = after.chatAnswer!!
        assertTrue(card.headline, card.headline.startsWith("Chưa có gì được lưu") && card.headline.contains("chưa tạo được đề xuất"))
        assertTrue(card.isTransactionStatus)
        assertFalse("lời AI không được hiện ở bất kỳ đâu trên thẻ", (listOf(card.headline) + card.lines + card.notes).any { it.contains("lo trọn gói") })
        assertTrue(after.proposals.isEmpty())
    }

    @Test
    fun `with pending proposals the status headline states how many are still waiting for confirmation`() {
        val pending = listOf(EditableProposalDraft.fromProposal(prop(45)), EditableProposalDraft.fromProposal(prop(30)))
        val state = TimeWalletUiState(proposals = pending)
        val out = parseChatOnly(evasive, "Xong chưa?")
        val after = WallyChatReducer.applyResult(state, "Xong chưa?", out, pending, null) { _, _ -> emptyList() }
        assertEquals("draft đang chờ giữ nguyên", 2, after.proposals.size)
        assertEquals("Chưa có gì được lưu. Còn 2 đề xuất đang chờ bạn xác nhận.", after.chatAnswer!!.headline)
        assertFalse((listOf(after.chatAnswer!!.headline) + after.chatAnswer!!.lines + after.chatAnswer!!.notes).any { it.contains("lo trọn gói") })
    }

    @Test
    fun `an ordinary chat turn without pending drafts or a transaction topic keeps the natural AI reply`() {
        val out = parseChatOnly("Chào bạn! Hôm nay bạn muốn làm gì?", "Xin chào")
        val after = WallyChatReducer.applyResult(TimeWalletUiState(), "Xin chào", out, emptyList(), null) { _, _ -> emptyList() }
        assertEquals("Chào bạn! Hôm nay bạn muốn làm gì?", after.chatAnswer!!.headline)
        assertFalse(after.chatAnswer!!.isTransactionStatus)
    }

    @Test
    fun `turn classification - recording turns build no AI reply card, and a recording sentence with no proposal gets the application status card`() {
        val withProposal = ParseChatOutcome(listOf(prop()), emptyList(), source = OutcomeSource.CLOUD, reply = null)
        assertNull(WallyTurnPolicy.buildCard("Học 45p", withProposal, emptyList(), 1))
        val noReply = ParseChatOutcome(emptyList(), emptyList(), source = OutcomeSource.CLOUD, reply = null)
        val card = WallyTurnPolicy.buildCard("Học 45p", noReply, emptyList(), 0)!!
        assertTrue("không đề xuất cho câu ghi nhận ⇒ chưa lưu + yêu cầu làm rõ: ${card.headline}", card.headline.startsWith("Chưa có gì được lưu"))
        assertTrue(card.lines.any { it.contains("Ghi thủ công") })
    }

    @Test
    fun `a claim is dropped and the user still sees the application notice for the same turn`() {
        val out = parseChatOnly("Ok, mình đã chép vào sổ giúp bạn.", "Học 45p")
        assertNull("khẳng định đã lưu bị bỏ", out.reply)
        assertFalse(out.warnings.isEmpty())
        val after = WallyChatReducer.applyResult(TimeWalletUiState(), "Học 45p", out, emptyList(), null) { _, _ -> emptyList() }
        val headline = after.chatAnswer?.headline.orEmpty()
        assertFalse("thẻ không được mang lời AI bịa: $headline", headline.contains("chép vào sổ"))
    }
}
