package com.timewallet.mvp

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import com.timewallet.mvp.domain.usecase.ChatRoute
import com.timewallet.mvp.domain.usecase.ChatRouter
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.LedgerQueryEngine
import com.timewallet.mvp.domain.usecase.QueryAnswer
import com.timewallet.mvp.domain.usecase.QueryContext
import com.timewallet.mvp.domain.usecase.QueryIntent
import com.timewallet.mvp.domain.usecase.QueryIntentParser
import com.timewallet.mvp.domain.usecase.QueryMetric
import com.timewallet.mvp.domain.usecase.QueryPeriod
import com.timewallet.mvp.domain.usecase.QueryPeriodKind
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
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
 * AI-01 Slice B — Conversational Analytics.
 * Ngày cố định: Thứ Năm 2026-11-12, giờ hiện tại 18:00 (+07).
 * Số liệu kỳ vọng được TÍNH TAY từ fixture bên dưới (không lấy từ chính engine).
 */
class Ai01ConversationalAnalyticsTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 12)
    private fun ms(date: String, hm: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(hm)).atZone(zone).toInstant().toEpochMilli()

    private val nowMs = ms("2026-11-12", "18:00")

    private var seq = 0
    private fun timed(date: String, from: String, to: String, cat: TimeCategory, title: String = cat.key): LedgerRecord {
        val s = ms(date, from)
        val e = ms(date, to)
        return LedgerRecord(
            id = "r${++seq}", source = "chat", layer = TimeLayer.REPORTED_TIMED, createdAt = s, userConfirmedAt = e,
            category = cat, title = title, date = date, durationSeconds = (e - s) / 1000, startedAt = s, endedAt = e,
            timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k$seq"
        )
    }

    private fun untimed(date: String, minutes: Int, cat: TimeCategory): LedgerRecord = LedgerRecord(
        id = "r${++seq}", source = "chat", layer = TimeLayer.REPORTED_UNTIMED, createdAt = nowMs, userConfirmedAt = nowMs,
        category = cat, title = cat.key, date = date, durationSeconds = minutes * 60L, startedAt = null, endedAt = null,
        timezone = zone.id, status = "CONFIRMED", idempotencyKey = "k$seq"
    )

    private fun planned(date: String, from: String, to: String, cat: TimeCategory): LedgerRecord {
        val s = ms(date, from)
        val e = ms(date, to)
        return LedgerRecord(
            id = "r${++seq}", source = "chat", layer = TimeLayer.PLANNED, createdAt = nowMs, userConfirmedAt = nowMs,
            category = cat, title = cat.key, date = date, durationSeconds = (e - s) / 1000, startedAt = s, endedAt = e,
            timezone = zone.id, status = "PLANNED", idempotencyKey = "k$seq"
        )
    }

    private val W = TimeCategory.WORK
    private val L = TimeCategory.LEARN
    private val H = TimeCategory.HEALTH
    private val F = TimeCategory.FUN

    /*
     * Fixture (giây tính tay):
     *  10-20: learn 1h, work 2h                                  -> tháng trước: 10800 (learn 3600, work 7200)
     *  11-02 (T2): work 4h              | tuần trước (11-02..08)
     *  11-04 (T4): learn 2h             |   timed: work 14400, learn 7200, health 1800 = 23400
     *  11-06 (T6): health 30m, untimed fun 45m  |   untimed: 2700 (fun)
     *  11-09 (T2): work 3h              | tuần này (11-09..15)
     *  11-10 (T3): learn 1.5h, work 2h  |   timed: work 25200, learn 9000, health 1800 = 36000
     *  11-11 (T4): learn 1h, untimed learn 30m  |  untimed: 1800 (learn)
     *  11-12 (T5): work 2h, health 30m, PLANNED learn 19-20 | planned: 3600 (11-12) + 3600 (11-14) = 7200
     *  11-14 (T7): PLANNED work 09-10
     */
    private val records: List<LedgerRecord> = listOf(
        timed("2026-10-20", "09:00", "10:00", L), timed("2026-10-20", "10:00", "12:00", W),
        timed("2026-11-02", "08:00", "12:00", W),
        timed("2026-11-04", "09:00", "11:00", L),
        timed("2026-11-06", "06:00", "06:30", H), untimed("2026-11-06", 45, F),
        timed("2026-11-09", "08:00", "11:00", W),
        timed("2026-11-10", "09:00", "10:30", L), timed("2026-11-10", "13:00", "15:00", W),
        timed("2026-11-11", "20:00", "21:00", L), untimed("2026-11-11", 30, L),
        timed("2026-11-12", "08:00", "10:00", W), timed("2026-11-12", "17:00", "17:30", H),
        planned("2026-11-12", "19:00", "20:00", L),
        planned("2026-11-14", "09:00", "10:00", W)
    )

    // ---------------------------------------------------------------- periods
    private fun d(s: String) = LocalDate.parse(s)
    private val TODAY = d("2026-11-12") to d("2026-11-12")
    private val YESTERDAY = d("2026-11-11") to d("2026-11-11")
    private val WEEK = d("2026-11-09") to d("2026-11-15")
    private val LAST_WEEK = d("2026-11-02") to d("2026-11-08")
    private val MONTH = d("2026-11-01") to d("2026-11-30")
    private val LAST_MONTH = d("2026-10-01") to d("2026-10-31")
    private val D7 = d("2026-11-06") to d("2026-11-12")
    private val D14 = d("2026-10-30") to d("2026-11-12")

    private fun ctx(metric: QueryMetric, p: Pair<LocalDate, LocalDate>, cat: TimeCategory?) =
        QueryContext(metric, QueryPeriod(p.first, p.second, "ctx", QueryPeriodKind.ROLLING), cat)

    // ---------------------------------------------------------------- golden
    private sealed class Exp {
        data object Write : Exp()
        data object Clarify : Exp()
        class Query(
            val metric: QueryMetric,
            val period: Pair<LocalDate, LocalDate>,
            val cat: TimeCategory? = null,
            val cmp: Pair<LocalDate, LocalDate>? = null,
            val check: (QueryAnswer) -> Boolean
        ) : Exp()
    }

    private class Case(val input: String, val exp: Exp, val ctx: QueryContext? = null)

    private fun q(
        metric: QueryMetric, period: Pair<LocalDate, LocalDate>, cat: TimeCategory? = null,
        cmp: Pair<LocalDate, LocalDate>? = null, check: (QueryAnswer) -> Boolean
    ) = Exp.Query(metric, period, cat, cmp, check)

    private val T = QueryMetric.TOTAL

    private fun golden(): List<Case> = listOf(
        // ---- câu hỏi tổng / theo kỳ
        Case("Hôm nay tôi đã dùng bao nhiêu giờ?", q(T, TODAY) { it.figures.timedSeconds == 9000L && it.figures.plannedSeconds == 3600L }),
        Case("hôm qua tôi dùng bao nhiêu thời gian?", q(T, YESTERDAY) { it.figures.timedSeconds == 3600L && it.figures.untimedSeconds == 1800L }),
        Case("Tuần này tôi học bao nhiêu giờ?", q(T, WEEK, L) { (it.figures.timedByCategory[L] ?: 0) == 9000L }),
        Case("Tuần này tôi làm việc bao nhiêu?", q(T, WEEK, W) { (it.figures.timedByCategory[W] ?: 0) == 25200L }),
        Case("Tuần trước tôi làm việc bao nhiêu giờ?", q(T, LAST_WEEK, W) { (it.figures.timedByCategory[W] ?: 0) == 14400L }),
        Case("Tháng này tôi đã dùng bao nhiêu giờ?", q(T, MONTH) { it.figures.timedSeconds == 59400L }),
        Case("Tháng trước tôi học bao nhiêu?", q(T, LAST_MONTH, L) { (it.figures.timedByCategory[L] ?: 0) == 3600L }),
        Case("7 ngày qua tôi dùng bao nhiêu giờ?", q(T, D7) { it.figures.timedSeconds == 37800L && it.figures.untimedSeconds == 4500L }),
        Case("Tuần này tôi tập thể dục bao nhiêu?", q(T, WEEK, H) { (it.figures.timedByCategory[H] ?: 0) == 1800L }),
        Case("ngày 10/11 tôi học bao nhiêu?", q(T, d("2026-11-10").let { it to it }, L) { (it.figures.timedByCategory[L] ?: 0) == 5400L }),
        Case("Hôm kia tôi làm việc bao nhiêu?", q(T, d("2026-11-10").let { it to it }, W) { (it.figures.timedByCategory[W] ?: 0) == 7200L }),
        Case("14 ngày qua tôi học bao nhiêu?", q(T, D14, L) { (it.figures.timedByCategory[L] ?: 0) == 16200L }),
        Case("12/11 tôi làm việc bao nhiêu?", q(T, TODAY, W) { (it.figures.timedByCategory[W] ?: 0) == 7200L }),
        Case("2026-11-10 tôi học bao nhiêu?", q(T, d("2026-11-10").let { it to it }, L) { (it.figures.timedByCategory[L] ?: 0) == 5400L }),
        Case("Tuần này tôi nghỉ ngơi bao nhiêu?", q(T, WEEK, TimeCategory.REST) {
            (it.figures.timedByCategory[TimeCategory.REST] ?: 0) == 0L && it.headline.contains("chưa có bản ghi")
        }),
        Case("Hôm nay tôi ngủ bao nhiêu?", q(T, TODAY, TimeCategory.SLEEP) { (it.figures.timedByCategory[TimeCategory.SLEEP] ?: 0) == 0L }),
        Case("Giải trí hôm qua bao nhiêu giờ?", q(T, YESTERDAY, F) { (it.figures.timedByCategory[F] ?: 0) == 0L && (it.figures.untimedByCategory[F] ?: 0) == 0L }),
        Case("Tuần trước tôi giải trí bao nhiêu?", q(T, LAST_WEEK, F) {
            (it.figures.timedByCategory[F] ?: 0) == 0L && (it.figures.untimedByCategory[F] ?: 0) == 2700L
        }),
        Case("Tuần này tôi dùng bao nhiêu giờ để học tập?", q(T, WEEK, L) { (it.figures.timedByCategory[L] ?: 0) == 9000L }),

        // ---- chỉ số khác
        Case("Hoạt động nào nhiều nhất tuần này?", q(QueryMetric.TOP_CATEGORY, WEEK) { it.headline.contains("Công việc nhiều nhất") }),
        Case("Tuần trước tôi dành thời gian cho việc gì ít nhất?", q(QueryMetric.LEAST_CATEGORY, LAST_WEEK) { it.headline.contains("Vận động ít nhất") }),
        Case("Tuần này có bao nhiêu giờ chưa gắn giờ?", q(QueryMetric.UNTIMED, WEEK) { it.figures.untimedSeconds == 1800L && it.headline.contains("30p") }),
        Case("Tuần này tôi học bao nhiêu giờ chưa gắn giờ?", q(QueryMetric.UNTIMED, WEEK, L) { (it.figures.untimedByCategory[L] ?: 0) == 1800L }),
        Case("Hôm nay tôi dự kiến làm gì bao nhiêu giờ?", q(QueryMetric.PLANNED, TODAY) { it.figures.plannedSeconds == 3600L && it.figures.timedSeconds == 9000L }),
        Case("Tổng kết tuần này", q(QueryMetric.BREAKDOWN, WEEK) { it.figures.timedSeconds == 36000L && it.lines.any { l -> l.contains("Công việc: 7h") } }),
        Case("Cho tôi xem báo cáo tháng này", q(QueryMetric.BREAKDOWN, MONTH) { it.figures.timedSeconds == 59400L }),
        Case("Thống kê hôm nay", q(QueryMetric.BREAKDOWN, TODAY) { it.figures.timedSeconds == 9000L }),
        Case("Cho mình xem tuần này", q(T, WEEK) { it.figures.timedSeconds == 36000L }),
        Case("xem hôm qua", q(T, YESTERDAY) { it.figures.timedSeconds == 3600L }),

        // ---- so sánh kỳ
        Case("So sánh tuần này với tuần trước", q(T, WEEK, null, LAST_WEEK) {
            it.figures.timedSeconds == 36000L && it.compareFigures!!.timedSeconds == 23400L && it.lines.any { l -> l.contains("+54%") }
        }),
        Case("Tuần này so với tuần trước tôi học nhiều hơn hay ít hơn?", q(T, WEEK, L, LAST_WEEK) {
            (it.figures.timedByCategory[L] ?: 0) == 9000L && (it.compareFigures!!.timedByCategory[L] ?: 0) == 7200L
        }),
        Case("Hôm nay so với hôm qua tôi dùng bao nhiêu giờ?", q(T, TODAY, null, YESTERDAY) {
            it.figures.timedSeconds == 9000L && it.compareFigures!!.timedSeconds == 3600L
        }),
        Case("Tháng này so với tháng trước thế nào?", q(T, MONTH, null, LAST_MONTH) {
            it.figures.timedSeconds == 59400L && it.compareFigures!!.timedSeconds == 10800L
        }),
        Case("tháng trước so với tháng này làm việc bao nhiêu", q(T, LAST_MONTH, W, MONTH) {
            (it.figures.timedByCategory[W] ?: 0) == 7200L && (it.compareFigures!!.timedByCategory[W] ?: 0) == 39600L
        }),

        // ---- câu hỏi nối tiếp (lastQueryContext)
        Case("Còn tuần trước thì sao?", q(T, LAST_WEEK, L) { (it.figures.timedByCategory[L] ?: 0) == 7200L }, ctx(T, WEEK, L)),
        Case("Còn học tập?", q(T, WEEK, L) { (it.figures.timedByCategory[L] ?: 0) == 9000L }, ctx(T, WEEK, null)),
        Case("So với tuần trước?", q(T, WEEK, W, LAST_WEEK) {
            (it.figures.timedByCategory[W] ?: 0) == 25200L && (it.compareFigures!!.timedByCategory[W] ?: 0) == 14400L
        }, ctx(T, WEEK, W)),
        Case("Thế còn hôm qua?", q(T, YESTERDAY) { it.figures.timedSeconds == 3600L }, ctx(T, TODAY, null)),
        Case("Còn tuần trước thì sao?", q(T, LAST_WEEK) { it.figures.timedSeconds == 23400L }, null),

        // ---- mơ hồ / hỗn hợp / chưa hỗ trợ => hỏi lại, không ghi
        Case("Tôi học bao nhiêu giờ?", Exp.Clarify),
        Case("Bao nhiêu?", Exp.Clarify),
        Case("Học 45 phút, hôm nay tôi học bao nhiêu?", Exp.Clarify),
        Case("Hôm nay làm việc 2h, tuần này tôi làm việc bao nhiêu giờ?", Exp.Clarify),
        Case("So sánh với tuần trước", Exp.Clarify),
        Case("So sánh", Exp.Clarify),
        Case("Tuần này và tuần trước tôi học bao nhiêu?", Exp.Clarify),
        Case("Tôi dùng bao nhiêu giờ thứ hai tuần này?", Exp.Clarify),
        Case("Ngân sách học tập còn lại bao nhiêu?", Exp.Clarify),
        Case("Tôi đã học bao nhiêu giờ năm nay?", Exp.Clarify),
        Case("Hôm nay tôi dùng bao nhiêu giờ trong 200 ngày qua?", Exp.Clarify),
        Case("Ngày 31/02 tôi học bao nhiêu?", Exp.Clarify),

        // ---- phủ định / câu ghi nhận: PHẢI đi vào luồng ghi, không bị chặn
        Case("Họp báo cáo 1h", Exp.Write),
        Case("Tổng kết dự án 2h", Exp.Write),
        Case("Tổng kết 2h dự án", Exp.Write),
        Case("Hôm nay tôi không học", Exp.Write),
        Case("Học 45p, đọc sách 30p", Exp.Write),
        Case("Hôm qua làm việc 3h", Exp.Write),
        Case("Từ 13:00 đến 18:00 làm việc tập trung", Exp.Write),
        Case("Tuần này tôi sẽ học nhiều hơn", Exp.Write),
        Case("Ăn tối với gia đình 1h", Exp.Write),
        Case("Đã dùng hết 2h cho dự án", Exp.Write),
        Case("Nghỉ ngơi 20 phút rồi học 1 tiếng", Exp.Write),
        Case("Ngủ lúc 10h tối 8h", Exp.Write),
        Case("Tổng cộng hôm nay 5h", Exp.Write),
        Case("Từ 8h đến 9h30 làm dự án, đọc sách 45 phút, chạy bộ 30 phút, ăn trưa 30 phút, nghỉ ngơi 20 phút", Exp.Write),
        Case("Hôm nay học kỹ thuật Feynman 45 phút", Exp.Write)
    )

    private fun periodOf(i: QueryIntent) = i.period.start to i.period.end

    /** @return null nếu khớp; ngược lại mô tả sai lệch. */
    private fun evaluate(c: Case): Pair<Boolean, String> {
        val route = ChatRouter.route(c.input, today, c.ctx)
        return when (val e = c.exp) {
            Exp.Write -> (route is ChatRoute.Write) to "route=$route"
            Exp.Clarify -> (route is ChatRoute.Clarify) to "route=$route"
            is Exp.Query -> {
                if (route !is ChatRoute.Query) return false to "route=$route"
                val i = route.intent
                val okIntent = i.metric == e.metric && periodOf(i) == e.period && i.category == e.cat &&
                    (i.compareTo?.let { it.start to it.end }) == e.cmp
                okIntent to "intent=${i.metric}/${periodOf(i)}/${i.category}/cmp=${i.compareTo?.let { it.start to it.end }}"
            }
        }
    }

    @Test
    fun `golden - at least 40 sentences and intent accuracy is at least 95 percent`() {
        val cases = golden()
        assertTrue("golden size=${cases.size}", cases.size >= 40)
        val failures = mutableListOf<String>()
        cases.forEach { c ->
            val (ok, detail) = evaluate(c)
            if (!ok) failures.add("\"${c.input}\" -> $detail")
        }
        val accuracy = (cases.size - failures.size) * 100.0 / cases.size
        println("GOLDEN_INTENT total=${cases.size} correct=${cases.size - failures.size} accuracy=%.1f%%".format(accuracy))
        failures.forEach { println("GOLDEN_MISS $it") }
        assertTrue("accuracy=$accuracy failures=$failures", accuracy >= 95.0)
    }

    @Test
    fun `golden - every accepted query has exact period category and numbers (100 percent)`() {
        var accepted = 0
        val wrong = mutableListOf<String>()
        golden().forEach { c ->
            val route = ChatRouter.route(c.input, today, c.ctx)
            if (route is ChatRoute.Query) {
                accepted++
                val e = c.exp
                if (e !is Exp.Query) {
                    wrong.add("\"${c.input}\" lẽ ra không được chấp nhận làm câu hỏi")
                    return@forEach
                }
                val (okIntent, detail) = evaluate(c)
                if (!okIntent) { wrong.add("\"${c.input}\" intent sai: $detail"); return@forEach }
                val ans = LedgerQueryEngine.execute(route.intent, records, zone, nowMs, null)
                if (!e.check(ans)) wrong.add("\"${c.input}\" số liệu sai: ${ans.headline} | ${ans.lines}")
            }
        }
        println("GOLDEN_ACCEPTED accepted=$accepted wrong=${wrong.size}")
        wrong.forEach { println("GOLDEN_WRONG $it") }
        assertTrue(accepted >= 30)
        assertTrue(wrong.toString(), wrong.isEmpty())
    }

    // ---------------------------------------------------------------- đối soát với Analytics
    @Test
    fun `engine totals equal the sum of computeDayStats day by day and never merge untimed or planned into timed`() {
        val route = ChatRouter.route("Tuần này tôi đã dùng bao nhiêu giờ?", today) as ChatRoute.Query
        val ans = LedgerQueryEngine.execute(route.intent, records, zone, nowMs, null)
        var expectedTimed = 0L
        var day = d("2026-11-09")
        while (!day.isAfter(d("2026-11-15"))) {
            expectedTimed += LedgerAnalyticsCalculator.computeDayStats(day.toString(), records, zone, nowMs, null).timedSeconds
            day = day.plusDays(1)
        }
        assertEquals(expectedTimed, ans.figures.timedSeconds)
        assertEquals(36000L, ans.figures.timedSeconds)
        assertEquals(1800L, ans.figures.untimedSeconds)
        assertEquals(7200L, ans.figures.plannedSeconds)
        assertTrue(ans.notes.any { it.contains("chưa gắn giờ") })
    }

    @Test
    fun `comparison with empty previous period reports missing data and does not compute a percentage`() {
        val route = ChatRouter.route("So sánh tuần này với tuần trước", d("2026-12-01")) as ChatRoute.Query
        // fixture: tuần 11-30..12-06 không có gì; tuần trước 11-23..29 cũng không có
        val ans = LedgerQueryEngine.execute(route.intent, records, zone, nowMs, null)
        assertTrue(ans.headline.contains("Chưa có dữ liệu"))

        val only = LedgerQueryEngine.execute(
            QueryIntent(
                QueryMetric.TOTAL,
                QueryPeriod(d("2026-11-09"), d("2026-11-15"), "tuần này", QueryPeriodKind.WEEK),
                null,
                QueryPeriod(d("2026-10-26"), d("2026-11-01"), "tuần xa", QueryPeriodKind.WEEK)
            ),
            records, zone, nowMs, null
        )
        assertEquals(0L, only.compareFigures!!.timedSeconds)
        assertTrue(only.lines.any { it.contains("không tính phần trăm") })
        assertTrue(only.notes.any { it.contains("chưa có dữ liệu để so sánh") })
        assertFalse(only.lines.any { it.contains("%)") })
    }

    @Test
    fun `no data period answers with a clear message instead of zeros`() {
        val route = ChatRouter.route("Hôm nay tôi dùng bao nhiêu giờ?", today) as ChatRoute.Query
        val ans = LedgerQueryEngine.execute(route.intent, emptyList(), zone, nowMs, null)
        assertTrue(ans.headline.startsWith("Chưa có dữ liệu"))
        assertFalse(ans.figures.hasAnyData)
    }

    @Test
    fun `follow up context is a plain RAM value and only supplies missing fields`() {
        val c = ctx(T, WEEK, L)
        val r = ChatRouter.route("Còn tuần trước thì sao?", today, c) as ChatRoute.Query
        assertEquals(L, r.intent.category)
        assertEquals(LAST_WEEK, periodOf(r.intent))
        // câu hỏi đầy đủ không bị ctx ghi đè
        val r2 = ChatRouter.route("Hôm nay tôi làm việc bao nhiêu?", today, c) as ChatRoute.Query
        assertEquals(W, r2.intent.category)
        assertEquals(TODAY, periodOf(r2.intent))
        // không có ctx => "còn học tập?" không tự đoán kỳ
        assertTrue(ChatRouter.route("Còn học tập?", today, null) is ChatRoute.Clarify)
    }

    // ---------------------------------------------------------------- an toàn: không ghi, không mạng
    @Test
    fun `routing keeps write path byte-for-byte for non-question sentences`() {
        val logs = listOf(
            "Học 45p, đọc sách 30p", "Từ 13:00 đến 18:00 làm việc tập trung", "Hôm qua làm việc 3h",
            "Sáng học 2h, chiều làm việc 3h, tổng cộng hôm nay được 5h", "Họp lúc 2h chiều 1h", "ngủ 8 tiếng"
        )
        logs.forEach { text ->
            assertTrue("\"$text\" phải đi vào luồng ghi", ChatRouter.route(text, today) is ChatRoute.Write)
            assertTrue(VietnameseChatParser.parse(text, today, LocalTime.of(14, 0)).proposals.isNotEmpty())
        }
    }

    @Test
    fun `QUERY_ENABLED switch disables the feature and every sentence goes to the write path`() {
        assertTrue(ChatRouter.QUERY_ENABLED)
        assertTrue(ChatRouter.route("Tuần này tôi học bao nhiêu giờ?", today, null, queryEnabled = false) is ChatRoute.Write)
        assertTrue(ChatRouter.route("Tuần này tôi học bao nhiêu giờ?", today, null, queryEnabled = true) is ChatRoute.Query)
    }

    @Test
    fun `mixed sentence never produces a write route or a query`() {
        val r = ChatRouter.route("Học 45 phút, hôm nay tôi học bao nhiêu?", today)
        assertTrue(r is ChatRoute.Clarify)
        assertTrue((r as ChatRoute.Clarify).message.contains("Chưa có gì được ghi"))
        assertNull((r as? ChatRoute.Query))
    }

    @Test
    fun `query code has zero network or database dependencies (static source check)`() {
        val base = File("src/main/java/com/timewallet/mvp/domain/usecase")
        val files = listOf("QueryIntentParser.kt", "LedgerQueryEngine.kt").map { File(base, it) }
        val forbidden = listOf(
            "java.net", "HttpURLConnection", "okhttp", "GeminiProposalClient", "com.timewallet.mvp.data",
            "androidx.room", "LedgerDao", "SharedPreferences", "upsert", "commitRecord", "commitBatch", "deleteById", "deleteAll"
        )
        files.forEach { f ->
            assertTrue("không tìm thấy ${f.absolutePath}", f.exists())
            val text = f.readText()
            forbidden.forEach { bad -> assertFalse("${f.name} chứa '$bad'", text.contains(bad)) }
        }
        // WALLY API-FIRST (thay đổi có chủ đích): ViewModel không còn định tuyến cục bộ bằng ChatRouter. Tin nhắn đi qua
        // Cloud AI; số liệu vẫn do LedgerQueryEngine (hai file đã quét ở trên: không mạng, không DB) tính từ sổ cái.
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        assertFalse(vm.contains("ChatRouter.route("))
        assertTrue(vm.contains("LedgerQueryEngine.execute("))
        // v1.0.6: engine được ViewModel truyền vào WallyChatRunner dưới dạng computeAnswers; runner chỉ gọi nó SAU analyze().
        val runner = File("src/main/java/com/timewallet/mvp/ui/WallyChatRunner.kt").readText()
        val aiCall = runner.indexOf("analyze()")
        val engineCall = runner.indexOf("computeAnswers(basis")
        assertTrue("engine chỉ chạy sau khi AI trả kết quả", aiCall in 0 until engineCall)
        assertTrue(vm.contains("GeminiProposalClient.analyzeInput("))
    }

    @Test
    fun `parser exposes period labels with dd_MM and previousEquivalent keeps the same kind`() {
        val week = (ChatRouter.route("Tuần này tôi dùng bao nhiêu giờ?", today) as ChatRoute.Query).intent.period
        assertEquals("tuần này (09/11–15/11)", week.label)
        val prev = week.previousEquivalent()
        assertEquals(LAST_WEEK, prev.start to prev.end)
        val month = (ChatRouter.route("Tháng này tôi dùng bao nhiêu giờ?", today) as ChatRoute.Query).intent.period
        assertEquals(LAST_MONTH, month.previousEquivalent().let { it.start to it.end })
        assertNotNull(QueryIntentParser.parse("Hôm nay tôi dùng bao nhiêu giờ?", today))
    }
}
