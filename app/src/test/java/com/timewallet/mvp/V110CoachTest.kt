package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.CoachCheck
import com.timewallet.mvp.domain.usecase.CoachDigest
import com.timewallet.mvp.domain.usecase.CoachDigestBuilder
import com.timewallet.mvp.domain.usecase.CoachGuard
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.TagRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v1.1.0 (D, L2): AI Time Coach — ỨNG DỤNG tính số, AI kể chuyện. Kiểm tra khối dữ liệu xác định và các guard: số ngoài dữ liệu, phán xét,
 * nhân quả, "không làm" suy từ thiếu bản ghi, khẳng định đã ghi/đổi dữ liệu, và đề xuất mục tiêu chỉ là gợi ý đã được kiểm.
 */
class V110CoachTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 18)
    private val nowMs = LocalDateTime.of(2026, 11, 18, 20, 0).atZone(zone).toInstant().toEpochMilli()

    private val run = TagRules.newTag("Chạy bộ", TagRules.nameKey("Chạy bộ"), TimeCategory.HEALTH, 1L, "tag_run")
    private val game = TagRules.newTag("Chơi game", TagRules.nameKey("Chơi game"), TimeCategory.FUN, 1L, "tag_game")
    private val read = TagRules.newTag("Đọc sách", TagRules.nameKey("Đọc sách"), TimeCategory.LEARN, 1L, "tag_read")
    private val tags = listOf(run, game, read)

    private fun rec(id: String, date: LocalDate, cat: TimeCategory, minutes: Int, tagId: String?, layer: TimeLayer = TimeLayer.REPORTED_UNTIMED) =
        LedgerRecord(id, "manual", layer, nowMs, nowMs, cat, "x", date.toString(), minutes * 60L, null, null, zone.id, "CONFIRMED", "k_$id", tagId)

    /** Ví dụ của Owner: mục tiêu chạy bộ 30p/ngày; hôm nay game 120p, YouTube(untagged) 60p, đọc sách 30p, chạy bộ 0. */
    private val goal = GoalRules.create(GoalTargetType.TAG, run.id, GoalKind.MIN, GoalPeriod.DAY, 30 * 60L, today.minusDays(6), 1L, "g1", "grp1")
    private val records = listOf(
        rec("1", today, TimeCategory.FUN, 120, game.id),
        rec("2", today, TimeCategory.FUN, 60, null),
        rec("3", today, TimeCategory.LEARN, 30, read.id),
        rec("4", today.minusDays(1), TimeCategory.HEALTH, 35, run.id),
        rec("5", today.minusDays(2), TimeCategory.FUN, 90, game.id)
    )
    private fun digest(days: Int = 7, recs: List<LedgerRecord> = records, goals: List<Goal> = listOf(goal)): CoachDigest =
        CoachDigestBuilder.build(recs, tags, goals, days, nowMs, zone)

    @Test
    fun `digest separates reading from gaming even inside the same category and states the missing running time`() {
        val d = digest()
        assertTrue(d.text.contains("Chơi game (Giải trí)") && d.text.contains("Đọc sách (Học tập)"))
        assertTrue("số phút theo Tag", d.text.contains("Chơi game (Giải trí)=") || d.text.contains("Chơi game (Giải trí): 210"))
        assertTrue(d.text.contains("(chưa gắn Tag)"))
        assertTrue(d.facts.any { it.contains("Chạy bộ hôm nay: chưa ghi nhận") && it.contains("30p") })
        assertTrue(d.allowedMinutes.containsAll(listOf(120, 60, 30, 35, 90, 210)))
        assertEquals(3, d.daysWithData); assertEquals(7, d.totalDays)
    }

    @Test
    fun `the daily goal line carries every day, the met count and today's remaining`() {
        val d = digest()
        assertTrue(d.text.contains("Chạy bộ: tối thiểu 30 phút/ngày"))
        assertTrue(d.text.contains("Đạt 1/7 ngày"))
        assertTrue(d.text.contains("còn thiếu 30 phút"))
        assertTrue(7 in d.allowedCounts && 1 in d.allowedCounts)
    }

    @Test
    fun `thin or empty data is stated and an empty ledger needs no AI`() {
        assertTrue(digest(days = 7, recs = listOf(records[0])).text.contains("dưới 3 ngày"))
        val empty = digest(recs = emptyList())
        assertFalse(empty.hasAnyData)
        assertTrue("mục tiêu vẫn được nhắc, không bịa dữ liệu", empty.facts.any { it.contains("Chạy bộ hôm nay: chưa ghi nhận") } && empty.facts.any { it.contains("0/7") })
        assertTrue(digest(recs = emptyList(), goals = emptyList()).facts.any { it.contains("Chưa có bản ghi") })
        assertTrue(empty.facts.any { it.contains("Chưa có bản ghi") })
    }

    @Test
    fun `planned time is not counted in the digest`() {
        val d = digest(recs = listOf(rec("p", today, TimeCategory.HEALTH, 45, run.id, layer = TimeLayer.PLANNED).copy(status = "PLANNED")))
        assertFalse(d.hasAnyData)
    }

    // ---------- guard ----------
    private fun ok(story: String, d: CoachDigest = digest()) = CoachGuard.check(story, d) is CoachCheck.Ok
    private fun why(story: String, d: CoachDigest = digest()): String = (CoachGuard.check(story, d) as? CoachCheck.Rejected)?.reason ?: "OK"

    @Test
    fun `a faithful story with numbers from the data passes`() {
        assertTrue(ok("Hôm nay bạn dành 2 giờ cho Chơi game và 30 phút đọc sách. Mục tiêu chạy bộ 30 phút hôm nay chưa ghi nhận; nếu phù hợp với lịch, ngày mai có thể dành 30 phút trong khoảng thời gian chơi game cho chạy bộ."))
        assertTrue(ok("Có dữ liệu 3/7 ngày nên mình nhận xét thận trọng: hôm qua chạy bộ 35 phút, đạt mục tiêu."))
    }

    @Test
    fun `numbers outside the data are rejected`() {
        assertEquals("Số liệu thời lượng ngoài dữ liệu.", why("Hôm nay bạn chơi game 3 giờ."))
        assertEquals("Số liệu thời lượng ngoài dữ liệu.", why("Bạn chơi game 45 phút."))
        assertEquals("Số liệu ngoài dữ liệu.", why("Bạn đã làm 5 việc khác nhau."))
        assertTrue(why("Tăng 20%").contains("ngoài dữ liệu") || why("Tăng 20%") != "OK")
        assertEquals("Ngày/phân số ngoài dữ liệu.", why("Vào ngày 03/11 bạn đạt mục tiêu."))
    }

    @Test
    fun `judgement causation and claims of not doing are rejected`() {
        assertEquals("Có lời phán xét.", why("Chơi game 2 giờ là lãng phí thời gian."))
        assertEquals("Có lời phán xét.", why("Bạn hơi lười chạy bộ."))
        assertEquals("Khẳng định quan hệ nhân quả.", why("Chơi game nhiều là nguyên nhân khiến mục tiêu chạy bộ chưa ghi nhận."))
        assertEquals("Khẳng định quan hệ nhân quả.", why("Việc đó dẫn đến chạy bộ thấp."))
        assertEquals("Khẳng định người dùng không làm vì chưa có bản ghi.", why("Hôm nay bạn không chạy bộ."))
    }

    @Test
    fun `guessing the users reason or feeling is rejected even with a hedge`() {
        // Câu có thật Claude kể trên thiết bị (WallyQA, 2026-11-16): lời đoán "thư giãn sau những khối tập trung" phải bị chặn.
        val m = "Đây có vẻ là khoảng thư giãn của bạn sau những khối thời gian tập trung."
        assertEquals("Đoán lý do/cảm xúc của người dùng.", why(m))
        assertEquals("Đoán lý do/cảm xúc của người dùng.", why("Có lẽ bạn đang mệt nên ghi ít hơn."))
        assertEquals("Đoán lý do/cảm xúc của người dùng.", why("Bạn cảm thấy áp lực."))
    }

    @Test
    fun `a story that claims a data change is rejected because the coach never writes`() {
        assertEquals("Khẳng định đã ghi/lưu/đổi dữ liệu.", why("Mình đã ghi thêm 30 phút chạy bộ cho bạn."))
        assertEquals("Khẳng định đã ghi/lưu/đổi dữ liệu.", why("Mình đã lưu mục tiêu mới nhé."))
        assertEquals("Khẳng định đã ghi/lưu/đổi dữ liệu.", why("Wally sẽ tạo mục tiêu giúp bạn."))
        assertEquals("Khẳng định đã ghi/lưu/đổi dữ liệu.", why("Mục tiêu đã tạo cho bạn, không cần xác nhận."))
        // ... nhưng MÔ TẢ dữ liệu người dùng đã ghi nhận là hợp lệ.
        assertTrue(ok("Chơi game đã ghi nhận 2 giờ hôm nay, còn đọc sách đã ghi nhận 30 phút."))
        assertTrue(ok("Mình chỉ kể lại những gì đã được ghi nhận: chạy bộ 35 phút."))
        // Sai-dương-tính đã đo bằng API thật: "13/11 hiện chưa..." không phải 13 giờ; "đã đạt mục tiêu" không phải "đã đặt mục tiêu"; số trần có trong dữ liệu hợp lệ.
        assertTrue(ok("Hai ngày 16/11 và 17/11 hiện chưa ghi nhận hoạt động nào."))
        assertTrue(ok("Hôm qua chạy bộ 35 phút, đã đạt mục tiêu tối thiểu 30 phút/ngày."))
        assertTrue(ok("Các ngày đi từ 30 lên 35 phút."))
        assertEquals("Khẳng định đã ghi/lưu/đổi dữ liệu.", why("Mình đã đặt mục tiêu 30 phút cho bạn."))
        // Khoảng "10–20 phút": cả hai đầu đều phải có trong dữ liệu.
        assertTrue(ok("Nếu hợp lịch, thử một buổi 30–35 phút."))
        assertEquals("Số liệu thời lượng ngoài dữ liệu.", why("Nếu hợp lịch, thử một buổi 10–20 phút."))
        assertTrue(why("").contains("trống"))
        assertTrue(why("a".repeat(1000)).contains("dài"))
    }

    @Test
    fun `suggestions are validated against real tags and categories limits and existing goals and capped at two`() {
        val raw = listOf<Map<String, Any?>>(
            mapOf("targetType" to "tag", "target" to "Chạy bộ", "kind" to "MIN", "period" to "WEEK", "minutes" to 150),   // hợp lệ (khác kỳ với mục tiêu đang có)
            mapOf("targetType" to "tag", "target" to "Chạy bộ", "kind" to "MIN", "period" to "DAY", "minutes" to 30),     // trùng mục tiêu đang chạy
            mapOf("targetType" to "tag", "target" to "Không có", "kind" to "MIN", "period" to "DAY", "minutes" to 30),     // Tag không tồn tại
            mapOf("targetType" to "category", "target" to "fun", "kind" to "MAX", "period" to "DAY", "minutes" to 90),     // hợp lệ
            mapOf("targetType" to "category", "target" to "Học tập", "kind" to "MIN", "period" to "DAY", "minutes" to 20),  // quá hai đề xuất
            mapOf("targetType" to "tag", "target" to "Đọc sách", "kind" to "MIN", "period" to "DAY", "minutes" to 99999)   // vượt giới hạn
        )
        val s = CoachGuard.validSuggestions(raw, tags, listOf(goal))
        assertEquals(2, s.size)
        assertEquals(GoalTargetType.TAG, s[0].targetType); assertEquals(run.id, s[0].targetKey); assertEquals(150 * 60L, s[0].targetSeconds)
        assertEquals(GoalTargetType.CATEGORY, s[1].targetType); assertEquals("fun", s[1].targetKey)
        assertTrue("không tag đã lưu trữ", CoachGuard.validSuggestions(
            listOf(mapOf("targetType" to "tag", "target" to "Chạy bộ", "kind" to "MIN", "period" to "DAY", "minutes" to 30)),
            listOf(TagRules.archive(run, 2)), emptyList()).isEmpty())
    }

    @Test
    fun `the coach prompt keeps the safety rules and is separate from the chat prompt`() {
        val p = GeminiProposalClient.coachSystemPrompt
        listOf("CHƯA GHI NHẬN", "Không phán xét", "Không khẳng định hay đoán nguyên nhân","chỉ nhắc các con số CÓ TRONG khối dữ liệu", "không tự đặt mục tiêu").forEach {
            assertTrue("thiếu quy tắc: $it", p.contains(it, ignoreCase = true))
        }
        assertEquals("wally-coach-4",GeminiProposalClient.COACH_PROMPT_VERSION)
        assertEquals("wally-prompt-8", GeminiProposalClient.PROMPT_VERSION)   // tách biệt với prompt chat
    }
}
