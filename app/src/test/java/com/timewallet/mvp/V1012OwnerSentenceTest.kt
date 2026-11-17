package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.domain.usecase.AiProposalGuard
import com.timewallet.mvp.domain.usecase.WallyIntentKind
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.toBatchInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * v1.0.12 — câu thật của Owner "Code từ lúc 14h đến giờ" là regression BẮT BUỘC: ngôn ngữ tự nhiên → (AI giả lập qua kết nối giả, đúng adapter) →
 * đề xuất → xác nhận → sổ cái. Đây KHÔNG phải bằng chứng AI thật (cần khóa của Owner); nó chứng minh phần của ứng dụng: gửi đúng ngữ cảnh,
 * kiểm tra đầu ra, "đến giờ" = giờ hiện tại, chỉ ghi sau xác nhận, không ghi trùng.
 */
class V1012OwnerSentenceTest {

    private val today = LocalDate.of(2026, 11, 15)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val owner = "Code từ lúc 14h đến giờ"
    private val key = "sk-synthetic-key-123"

    private fun run(content: String, now: LocalTime = LocalTime.of(16, 30), text: String = owner) =
        V1012Rig.analyze(text, key, today, now, body = V1012Rig.chatBody(content))

    private fun prop(
        start: String? = "14:00", end: String? = "16:30", seconds: Long = 9000, title: String = "Code", evidence: String = "explicit_range"
    ) = V1012Rig.proposalJson(title = title, durationSeconds = seconds, date = today.toString(), start = start, end = end, evidence = evidence)

    @Test
    fun `owner sentence with a correct AI answer becomes exactly one proposal 14_00-16_30 that still needs confirmation`() {
        val r = run(V1012Rig.answer(prop()))
        assertNull(r.outcome.fault)
        val p = r.outcome.proposals.single()
        assertEquals("Code", p.title)
        assertEquals("14:00", p.startLocal); assertEquals("16:30", p.endLocal)
        assertEquals(9000L, p.durationSeconds)
        assertEquals("explicit_range", p.timeEvidence)
        assertEquals(today.toString(), p.dateLocal)
        assertTrue(p.needsConfirmation); assertFalse(p.isFuturePlanned)
        assertEquals(WallyIntentKind.RECORD, r.outcome.intent)
        assertNull("lời đáp tự do của AI không hiển thị khi đã có đề xuất", r.outcome.reply)
    }

    @Test
    fun `the request carries the current time and date so that the phrase until-now can be resolved`() {
        val r = run(V1012Rig.answer(prop()))
        val sent = r.connections.single().sent.toString(Charsets.UTF_8)
        assertTrue(sent.contains("Giờ hiện tại (currentTime): 16:30"))
        assertTrue(sent.contains("Ngày hiện tại (dateLocal): 2026-11-15"))
        assertTrue(sent.contains("Code từ lúc 14h đến giờ"))
        assertTrue("khóa không nằm trong thân yêu cầu", !sent.contains(key))
    }

    @Test
    fun `when the AI end time disagrees with until-now the end is set to the current time and the duration recomputed`() {
        val r = run(V1012Rig.answer(prop(end = "17:00", seconds = 10800)))
        val p = r.outcome.proposals.single()
        assertEquals("16:30", p.endLocal)
        assertEquals(9000L, p.durationSeconds)
        assertEquals("14:00", p.startLocal)
        assertTrue(r.outcome.warnings.joinToString(), r.outcome.warnings.any { it.contains("đặt theo") && it.contains("16:30") && it.contains("17:00") })
    }

    @Test
    fun `a small clock difference is tolerated but a 3 minute difference is corrected`() {
        val near = run(V1012Rig.answer(prop(end = "16:28", seconds = 8880))).outcome.proposals.single()
        assertEquals("16:28", near.endLocal)
        val far = run(V1012Rig.answer(prop(end = "16:27", seconds = 8820))).outcome.proposals.single()
        assertEquals("16:30", far.endLocal)
        assertEquals(9000L, far.durationSeconds)
    }

    @Test
    fun `until-now with a start that is not before now is left to the user with a warning - nothing is invented`() {
        val r = run(V1012Rig.answer(prop(start = "14:00", end = "16:30")), now = LocalTime.of(13, 0))
        val p = r.outcome.proposals.single()
        assertEquals("16:30", p.endLocal)
        assertTrue(r.outcome.warnings.joinToString(), r.outcome.warnings.any { it.contains("đến giờ") && it.contains("13:00") })
    }

    @Test
    fun `two explicit ranges with until-now are only warned about - the guard never guesses which one to change`() {
        val text = "Code từ lúc 14h đến giờ, đọc sách từ 9h đến 10h"
        val two = V1012Rig.answer(prop(), prop(start = "09:00", end = "10:00", seconds = 3600, title = "Đọc sách"))
        val r = run(two, text = text)
        assertEquals(2, r.outcome.proposals.size)
        assertEquals("10:00", r.outcome.proposals.last().endLocal)
    }

    @Test
    fun `until-now detection only fires on the open-ended phrase`() {
        listOf(
            "Code từ lúc 14h đến giờ", "học từ 9h đến bây giờ", "làm việc từ 8h tới nay", "Code từ 14h đến giờ rồi",
            "Code từ 14h đến giờ, còn trước đó học 1h", "CODE TỪ 14H ĐẾN GIỜ.", "đọc sách từ 8h30 đến lúc này"
        ).forEach { assertTrue(it, AiProposalGuard.hasOpenEndedToNow(it)) }
        listOf(
            "Nhớ đến giờ làm việc thì gọi tôi", "Đến giờ ăn rồi mới nghỉ", "Học đến giờ trưa", "Hôm nay tôi code 2h", "Từ 14h đến 16h30 code"
        ).forEach { assertFalse(it, AiProposalGuard.hasOpenEndedToNow(it)) }
    }

    @Test
    fun `a start without an end is a clarification - the AI is told to ask and no proposal is created`() {
        val ask = """{"reply":"Bạn đã code xong lúc mấy giờ, hay vẫn đang làm tiếp?","proposals":[],"queries":[]}"""
        val r = run(ask, text = "Code từ 14h")
        assertNull(r.outcome.fault)
        assertTrue(r.outcome.proposals.isEmpty())
        assertEquals("Bạn đã code xong lúc mấy giờ, hay vẫn đang làm tiếp?", r.outcome.reply)
        assertEquals(WallyIntentKind.CHAT, r.outcome.intent)
    }

    // ---------------------------------------------------------------- xác nhận -> sổ cái (cùng chuỗi ViewModel.confirmProposalsList)
    private val nowMs = today.atTime(16, 31).atZone(zone).toInstant().toEpochMilli()

    /** ĐÚNG đường ghi mà ViewModel.confirmProposalsList dùng (BatchConfirm.run). */
    private fun confirm(dao: InMemoryLedgerDao, drafts: List<EditableProposalDraft>): ConfirmResult = runBlocking {
        BatchConfirm.run(
            dao = dao, inputs = drafts.map { it.toBatchInput() }, activeTimer = null, readActiveTimer = { null },
            nowEpochMs = nowMs, zone = zone, readNowMs = { nowMs }
        )
    }

    @Test
    fun `owner sentence end to end - nothing is written before confirm, confirm writes the real range once, a second confirm writes nothing`() {
        val dao = InMemoryLedgerDao()
        val r = run(V1012Rig.answer(prop(end = "17:00", seconds = 10800)))
        assertEquals("trước xác nhận: sổ cái trống", 0, runBlocking { dao.getAllRecordsSnapshot().size })
        val drafts = r.outcome.proposals.map { EditableProposalDraft.fromProposal(it) }
        assertEquals(1, (confirm(dao, drafts) as ConfirmResult.Committed).savedCount)
        val row = runBlocking { dao.getAllRecordsSnapshot() }.single()
        assertEquals("Code", row.title)
        assertEquals(9000L, row.durationSeconds)
        assertEquals(today.atTime(14, 0).atZone(zone).toInstant().toEpochMilli(), row.startedAt)
        assertEquals(today.atTime(16, 30).atZone(zone).toInstant().toEpochMilli(), row.endedAt)
        // Xác nhận lần hai của chính đề xuất đó (giao diện đã gỡ đề xuất và chặn bấm lặp; ở đây ép gọi thẳng): KHÔNG ghi thêm, không được báo thành công.
        val again = confirm(dao, drafts)
        assertTrue(again.toString(), again !is ConfirmResult.Committed || again.savedCount == 0)
        assertEquals(1, runBlocking { dao.getAllRecordsSnapshot().size })
    }

    @Test
    fun `the static prompt documents the until-now rule with the owner example and the version moved`() {
        val s = GeminiProposalClient.staticSystemPrompt
        assertTrue(s.contains("đến giờ") && s.contains("Code từ lúc 14h đến giờ") && s.contains("currentTime"))
        assertEquals("wally-prompt-7", GeminiProposalClient.PROMPT_VERSION)
        assertNotNull(GeminiProposalClient.staticPromptHash)
    }
}
