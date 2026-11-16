package com.timewallet.mvp

import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.DraftRef
import com.timewallet.mvp.domain.usecase.ProposalLifecycle
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * WALLY v1.0.13, nhóm G2: các API MỚI của ba lớp lỗi (lệnh hủy theo đề xuất đang chờ, khôi phục dấu, số liệu suy ra từ mốc giờ).
 * Các hành vi nhìn thấy từ bên ngoài được kiểm ở V1013G2ClassFixTest (file đó chạy được trên bản gốc để chứng minh lỗi).
 */
class V1013G2InternalsTest {

    private fun draft(id: String, title: String, minutes: Int, raw: String = "${title.lowercase()} $minutes phút") = DraftRef(id, title, raw, minutes)

    // ------------------------------------------------------------------------------------------------ C1
    @Test
    fun `C1 a duration sentence is still a command when its title and duration match a pending draft (G5 style), and an ambiguity still asks back`() {
        val read = draft("a", "Đọc sách", 30)
        val run = draft("b", "Chạy bộ", 25)
        val drafts = listOf(read, run)

        val cmd = CancelCommandParser.parseDraftReference("Xóa cái đọc sách 30 phút", drafts)
        assertNotNull(cmd)
        assertEquals(setOf("a"), ProposalLifecycle.resolve(cmd!!, drafts, false).cancelIds)
        assertEquals(setOf("b"), ProposalLifecycle.resolve(CancelCommandParser.parseDraftReference("Bỏ hết chạy bộ 25 phút", drafts)!!, drafts, false).cancelIds)

        // tiêu đề khớp nhưng thời lượng khác: không phải draft đó => đi lên cloud
        assertNull(CancelCommandParser.parseDraftReference("Xóa cái đọc sách 45 phút", drafts))
        assertNull(CancelCommandParser.parseDraftReference("Xóa hết bài tập cũ trong cặp 20 phút", drafts))

        // hai draft cùng tiêu đề và thời lượng: vẫn là lệnh, và vẫn hỏi lại thay vì hủy bừa
        val twin = listOf(read, draft("c", "Đọc sách", 30))
        val ask = ProposalLifecycle.resolve(CancelCommandParser.parseDraftReference("Xóa cái đọc sách 30 phút", twin)!!, twin, false)
        assertTrue(ask.askBack); assertTrue(ask.cancelIds.isEmpty()); assertTrue(ask.message.contains("1. Đọc sách (30p)"))
    }

    @Test
    fun `C1 the draft-aware parser never turns a clear command or an unrelated sentence into a match`() {
        val drafts = listOf(draft("a", "Đọc sách", 30), draft("b", "Chạy bộ", 25))
        // lệnh rõ ràng đã do parse(text) xử lý; parseDraftReference chỉ xét câu "đối tượng ngoài ứng dụng + thời lượng"
        listOf("Hủy", "Hủy tất cả đề xuất", "Hủy đề xuất số 2", "Hủy ghi nhận 30 phút đọc sách", "Xóa cái này đi", "Học 45p", "Hủy hợp đồng 2h").forEach {
            assertNull(it, CancelCommandParser.parseDraftReference(it, drafts))
        }
        // không có draft nào: không bao giờ là lệnh
        listOf("Xóa hết ảnh trùng 50 phút", "Bỏ cái bàn học cũ đi 40 phút", "Xóa cái đọc sách 30 phút").forEach {
            assertNull(it, CancelCommandParser.parseDraftReference(it, emptyList()))
        }
        // đối tượng chỉ gồm từ quá ngắn không được khớp mọi draft cùng thời lượng
        assertNull(CancelCommandParser.parseDraftReference("Xóa hết a 30 phút", drafts))
    }

    // ------------------------------------------------------------------------------------------------ C2
    @Test
    fun `C2 accent restoration only touches words of an all-ASCII sentence and leaves accented text byte for byte`() {
        val r = VietnameseChatParser::restoreUnaccented
        assertEquals("Nghi lúc 3h chiều 2 tiếng", r("Nghi luc 3h chieu 2 tieng"))
        assertEquals("hoc bai từ 15h đến giờ", r("hoc bai tu 15h den gio"))
        assertEquals("xem phim từ 19h tới 21h", r("xem phim tu 19h toi 21h"))
        assertEquals("hom nay toi hoc 2 giờ", r("hom nay toi hoc 2 gio"))
        // ngữ cảnh quyết định nghĩa: "tôi" và "tối nay" không bị đụng, "sau" (sáu/sau) không đổi
        assertEquals("toi hoc 2 tiếng", r("toi hoc 2 tieng"))
        assertEquals("Hoc từ 7h tối nay", r("Hoc tu 7h toi nay"))
        assertEquals("sau 2 giờ", r("sau 2 gio"))
        assertEquals("tu giờ den day", r("tu gio den day"))
        assertEquals("Học từ 8h đến 9h", r("Học từ 8h đến 9h"))
        assertEquals("Hoc tu 8h đến 9h", r("Hoc tu 8h đến 9h"))
        for (t in V109Corpus.texts) {
            val out = r(t)
            assertEquals("idempotent: $t", out, r(out))
            if (t.any { it.code > 127 }) assertEquals("câu có dấu giữ nguyên: $t", t, out)
            assertEquals("chỉ chữ cái bị đổi: $t", t.filterNot { it.isLetter() }, out.filterNot { it.isLetter() })
        }
    }

    @Test
    fun `C2 half-hour after h works through the unaccented path too`() {
        assertEquals(listOf(90), VietnameseChatParser.allDurationMinutes(VietnameseChatParser.restoreUnaccented("Chay bo 1h ruoi")))
        assertEquals(listOf(210), VietnameseChatParser.allDurationMinutes(VietnameseChatParser.restoreUnaccented("Doc sach 3 tieng ruoi")))
    }

    // ------------------------------------------------------------------------------------------------ C3
    @Test
    fun `C3 the user's own figures include shared-unit alternatives and clock spans, and nothing else`() {
        fun f(s: String) = ReplyGuard.userFigureMinutes(s)
        assertEquals(setOf(20, 25), f("Đạp xe khoảng 20 hay 25 phút"))
        assertEquals(setOf(60, 120), f("Đọc 1 hoặc 2 tiếng"))
        assertEquals(setOf(540, 690, 150), f("Làm việc từ 9h đến 11h30"))
        assertEquals(setOf(120, 240, 840, 960), f("Dạy kèm từ 2h đến 4h chiều"))
        assertEquals(setOf(420, 1140), f("Xem phim lúc 7h tối"))
        assertEquals(emptySet<Int>(), f("Đạp xe 20 hay 25"))   // không có đơn vị chung: không số nào là số liệu của người dùng
        assertEquals(setOf(540), f("Làm việc từ 9h"))              // một mốc giờ không tạo ra khoảng
    }

    @Test
    fun `C3 ReplyGuard sanitize with the current time behaves like the real parse path and without it like before`() {
        val ask = "Bây giờ mới 4 rưỡi chiều, bạn xem phim từ lúc mấy giờ?"
        assertEquals(ask, ReplyGuard.sanitize(ask, "Xem phim tối nay", emptyList(), false, LocalTime.of(16, 30)).first)
        assertNull(ReplyGuard.sanitize(ask, "Xem phim tối nay", emptyList(), false, LocalTime.of(16, 31)).first)
        assertNull(ReplyGuard.sanitize(ask, "Xem phim tối nay", emptyList(), false, null).first)
        assertNull(ReplyGuard.sanitize(ask, "Xem phim tối nay", emptyList(), false).first)
    }
}
