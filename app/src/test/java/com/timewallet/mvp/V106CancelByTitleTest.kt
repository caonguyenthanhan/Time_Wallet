package com.timewallet.mvp

import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.CancelOutcome
import com.timewallet.mvp.domain.usecase.DraftRef
import com.timewallet.mvp.domain.usecase.ProposalLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WALLY v1.0.6 — K1: các draft tách từ CÙNG một câu dùng chung rawInput. "Hủy đề xuất học" phải đối chiếu theo TIÊU ĐỀ trước;
 * một khớp => hủy đúng draft đó, nhiều khớp => hỏi lại. Chỉ dùng API có từ v1.0.5 nên chạy RED trên bản cũ.
 * Lệnh hủy chỉ tác động draft trong RAM; bản ghi đã lưu không bị đổi (STORED_NOTE).
 */
class V106CancelByTitleTest {

    private val sentence = "Học 45p và đọc sách 30p"
    private val study = DraftRef("a", "Học bài", sentence, 45)
    private val read = DraftRef("b", "Đọc sách", sentence, 30)

    private fun resolve(text: String, drafts: List<DraftRef>): CancelOutcome =
        ProposalLifecycle.resolve(requireNotNull(CancelCommandParser.parse(text)) { "không phải lệnh hủy: $text" }, drafts, false)

    @Test
    fun `cancelling by a word of one title cancels only that draft even though both share the raw sentence`() {
        val o = resolve("Hủy đề xuất học", listOf(study, read))
        assertEquals(setOf("a"), o.cancelIds)
        assertFalse(o.askBack)
        assertTrue(o.message.contains(ProposalLifecycle.STORED_NOTE))
    }

    @Test
    fun `cancelling the other draft by its title`() {
        val o = resolve("Hủy đề xuất đọc sách", listOf(study, read))
        assertEquals(setOf("b"), o.cancelIds)
        assertFalse(o.askBack)
    }

    @Test
    fun `two drafts whose titles both match ask back instead of guessing`() {
        val a = DraftRef("a", "Học bài", sentence, 45)
        val b = DraftRef("b", "Học tiếng Anh", sentence, 30)
        val o = resolve("Hủy đề xuất học", listOf(a, b))
        assertTrue(o.askBack)
        assertTrue(o.cancelIds.isEmpty())
        assertTrue(o.message.contains("2 đề xuất khớp"))
    }

    @Test
    fun `a duration disambiguates between equal titles`() {
        val a = DraftRef("a", "Học", "Học 45p và học 30p", 45)
        val b = DraftRef("b", "Học", "Học 45p và học 30p", 30)
        val o = resolve("Hủy ghi nhận 30 phút học", listOf(a, b))
        assertEquals(setOf("b"), o.cancelIds)
    }

    @Test
    fun `when no title matches the raw input is still consulted (previous behaviour kept)`() {
        val a = DraftRef("a", "Việc 1", "Học 45p", 45)
        val b = DraftRef("b", "Việc 2", "Đọc sách 30p", 30)
        val o = resolve("Hủy đề xuất đọc sách", listOf(a, b))
        assertEquals(setOf("b"), o.cancelIds)
    }

    @Test
    fun `cancel-all and the numbered form are unchanged`() {
        assertEquals(setOf("a", "b"), resolve("Hủy tất cả đề xuất", listOf(study, read)).cancelIds)
        assertEquals(setOf("b"), resolve("Hủy đề xuất số 2", listOf(study, read)).cancelIds)
    }

    @Test
    fun `no draft means nothing to cancel and the saved ledger is explicitly untouched`() {
        val o = resolve("Hủy đề xuất học", emptyList())
        assertTrue(o.cancelIds.isEmpty())
        assertTrue(o.message.contains(ProposalLifecycle.STORED_NOTE))
    }
}
