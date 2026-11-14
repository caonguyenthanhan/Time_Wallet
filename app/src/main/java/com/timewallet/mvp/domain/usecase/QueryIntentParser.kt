package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.TimeCategory
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * AI-01 Slice B — bộ nhận diện câu hỏi số liệu bằng quy tắc (tất định, offline).
 * Thuần Kotlin: KHÔNG import mạng, DAO hay Room. Có test tĩnh kiểm tra điều này.
 *
 * WALLY API-FIRST: ViewModel KHÔNG còn gọi [ChatRouter]/[QueryIntentParser] (Cloud AI hiểu câu hỏi). File này được giữ lại
 * vì (1) các kiểu `QueryIntent/QueryPeriod/QueryMetric` là hợp đồng giữa AI và LedgerQueryEngine, (2) golden 66 và holdout 46
 * vẫn kiểm chứng bộ phân tích như hồi quy. Phần parser có thể xóa ở một đợt dọn dẹp sau khi Owner đồng ý.
 */

/** BUDGET (v1.0.6, R02): tiến độ ngân sách theo danh mục, tính bằng computeBudgetProgress chung với Tổng quan/Insights. */
enum class QueryMetric { TOTAL, UNTIMED, PLANNED, TOP_CATEGORY, LEAST_CATEGORY, BREAKDOWN, BUDGET }

enum class QueryPeriodKind { DAY, WEEK, MONTH, ROLLING, DATE }

data class QueryPeriod(
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
    val kind: QueryPeriodKind
) {
    val dayCount: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    /** Kỳ liền trước có cùng độ dài / cùng loại (tuần -> tuần trước, tháng -> tháng trước...). */
    fun previousEquivalent(): QueryPeriod = when (kind) {
        QueryPeriodKind.DAY -> {
            val d = start.minusDays(1)
            QueryPeriod(d, d, "ngày ${fmt(d)}", QueryPeriodKind.DATE)
        }
        QueryPeriodKind.WEEK -> {
            val s = start.minusWeeks(1)
            QueryPeriod(s, s.plusDays(6), "tuần trước đó (${fmt(s)}–${fmt(s.plusDays(6))})", QueryPeriodKind.WEEK)
        }
        QueryPeriodKind.MONTH -> {
            val s = start.minusMonths(1).withDayOfMonth(1)
            val e = s.withDayOfMonth(s.lengthOfMonth())
            QueryPeriod(s, e, "tháng trước đó (${s.monthValue}/${s.year})", QueryPeriodKind.MONTH)
        }
        QueryPeriodKind.ROLLING, QueryPeriodKind.DATE -> {
            val n = dayCount
            val s = start.minusDays(n)
            val e = start.minusDays(1)
            QueryPeriod(s, e, "$n ngày trước đó (${fmt(s)}–${fmt(e)})", QueryPeriodKind.ROLLING)
        }
    }

    companion object {
        fun fmt(d: LocalDate): String = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }
}

data class QueryIntent(
    val metric: QueryMetric,
    val period: QueryPeriod,
    val category: TimeCategory? = null,
    val compareTo: QueryPeriod? = null
)

/** Ngữ cảnh câu hỏi gần nhất, chỉ giữ trong RAM để hiểu câu hỏi nối tiếp ("còn tuần trước thì sao?"). */
data class QueryContext(
    val metric: QueryMetric,
    val period: QueryPeriod,
    val category: TimeCategory?
)

sealed class ChatRoute {
    /** Không phải câu hỏi: giữ nguyên luồng ghi nhận hiện có. */
    data object Write : ChatRoute()

    data class Query(val intent: QueryIntent) : ChatRoute()

    /** Câu hỏi mơ hồ / hỗn hợp / chưa hỗ trợ: hỏi lại, KHÔNG rơi vào luồng ghi. */
    data class Clarify(val message: String, val suggestions: List<String> = DEFAULT_SUGGESTIONS) : ChatRoute()

    companion object {
        val DEFAULT_SUGGESTIONS = listOf(
            "Hôm nay tôi đã dùng bao nhiêu giờ?",
            "Tuần này tôi học bao nhiêu giờ?",
            "So sánh tuần này với tuần trước"
        )
    }
}

object ChatRouter {

    /** Công tắc tính năng: false => mọi câu đi vào luồng ghi như v1.0.1. */
    const val QUERY_ENABLED: Boolean = true

    fun route(
        text: String,
        today: LocalDate,
        context: QueryContext? = null,
        queryEnabled: Boolean = QUERY_ENABLED
    ): ChatRoute {
        if (!queryEnabled) return ChatRoute.Write
        return QueryIntentParser.parse(text, today, context)
    }
}

object QueryIntentParser {

    private val vi = Locale("vi", "VN")
    private const val MAX_PERIOD_DAYS = 93L

    private val questionMarkerRegex = Rx.ascii(
        """\?|bao nhiêu|bao lâu|mấy\s+(?:giờ|tiếng|phút)|so với|so sánh|nhiều nhất|ít nhất|lâu nhất"""
    )
    private val reportStartRegex = Rx.ascii(
        """^(?:cho (?:tôi|mình) )?(?:xem )?(?:báo cáo|tổng kết|thống kê|tóm tắt)\s+(?:hôm nay|hôm qua|hôm kia|tuần|tháng|ngày)|""" +
            """^(?:cho (?:tôi|mình) )?xem\s+(?:hôm nay|hôm qua|hôm kia|tuần (?:này|trước|rồi)|tháng (?:này|trước|rồi)|\d{1,3} ngày)"""
    )
    private val followUpRegex = Rx.ascii("""^(?:thế |vậy )?còn (?!lại)|thì sao""")
    private val compareWordRegex = Rx.ascii("""so với|so sánh|\bhơn\b|ít hơn|nhiều hơn""")
    private val previousPeriodWordRegex = Rx.ascii("""kỳ trước|kì trước|trước đó|lần trước""")
    private val unsupportedRegex = Rx.ascii(
        """thứ (?:hai|ba|tư|năm|sáu|bảy)|chủ nhật|năm nay|năm ngoái|ngân sách|mục tiêu"""
    )
    private val reportWordsRegex = Rx.ascii("""báo cáo|tổng kết|thống kê|tóm tắt""")

    private data class PeriodHit(val range: IntRange, val period: QueryPeriod)

    fun parse(text: String, today: LocalDate, context: QueryContext? = null): ChatRoute {
        val n = normalize(text)
        if (n.isEmpty()) return ChatRoute.Write

        val isFollowUp = context != null && followUpRegex.containsMatchIn(n)
        val isQuestion = questionMarkerRegex.containsMatchIn(n) || reportStartRegex.containsMatchIn(n) || isFollowUp
        if (!isQuestion) return ChatRoute.Write

        // Câu hỗn hợp: vừa có thời lượng/mốc giờ cần ghi vừa có câu hỏi => không ghi, hỏi lại.
        val stripped = stripPeriodPhrases(n)
        val hasLoggableContent =
            VietnameseChatParser.extractDurationMinutes(stripped) != null ||
                VietnameseChatParser.extractTimeRange(stripped) != null
        if (hasLoggableContent) {
            return ChatRoute.Clarify(
                "Câu này vừa có nội dung ghi nhận vừa có câu hỏi. Hãy gửi riêng: ghi nhận trước, rồi hỏi số liệu sau. Chưa có gì được ghi."
            )
        }

        if (unsupportedRegex.containsMatchIn(n)) {
            return ChatRoute.Clarify(
                "Chat hiện hỗ trợ hỏi theo ngày, tuần, tháng, N ngày qua hoặc một ngày cụ thể (dd/MM). " +
                    "Hỏi theo thứ trong tuần, theo năm hoặc ngân sách thì xem ở tab Phân tích."
            )
        }

        val periodScan = findPeriods(n, today)
        periodScan.error?.let { return ChatRoute.Clarify(it) }
        val periods = periodScan.hits.map { it.period }.distinctBy { it.start to it.end }

        val category = detectCategory(n)
        val explicitMetric = detectExplicitMetric(n)
        val hasCompareWord = compareWordRegex.containsMatchIn(n)
        val hasPreviousWord = previousPeriodWordRegex.containsMatchIn(n)

        // ---- So sánh kỳ ----
        if (hasCompareWord) {
            val metric = explicitMetric ?: context?.metric ?: QueryMetric.TOTAL
            val cat = category ?: context?.category
            return when {
                periods.size >= 2 ->
                    ChatRoute.Query(QueryIntent(metric, periods[0], cat, compareTo = periods[1]))
                periods.size == 1 && context != null && startsWithCompare(n) ->
                    ChatRoute.Query(QueryIntent(metric, context.period, cat, compareTo = periods[0]))
                periods.size == 1 && hasPreviousWord ->
                    ChatRoute.Query(QueryIntent(metric, periods[0], cat, compareTo = periods[0].previousEquivalent()))
                periods.isEmpty() && hasPreviousWord && context != null ->
                    ChatRoute.Query(QueryIntent(metric, context.period, cat, compareTo = context.period.previousEquivalent()))
                else -> ChatRoute.Clarify(
                    "Bạn muốn so sánh khoảng thời gian nào với khoảng nào? Ví dụ: \"So sánh tuần này với tuần trước\"."
                )
            }
        }

        // ---- Câu hỏi nối tiếp ----
        if (isFollowUp && context != null) {
            val period = periods.firstOrNull() ?: context.period
            return ChatRoute.Query(
                QueryIntent(
                    metric = explicitMetric ?: context.metric,
                    period = period,
                    category = category ?: context.category
                )
            )
        }

        // ---- Câu hỏi đơn ----
        if (periods.size >= 2) {
            return ChatRoute.Clarify(
                "Bạn muốn xem riêng từng khoảng hay so sánh? Hãy hỏi một khoảng, hoặc nói rõ \"so sánh A với B\"."
            )
        }
        val period = periods.firstOrNull()
            ?: return ChatRoute.Clarify(
                "Bạn muốn xem khoảng thời gian nào? Ví dụ: hôm nay, hôm qua, tuần này, tuần trước, tháng này, 7 ngày qua."
            )

        val metric = when {
            explicitMetric != null -> explicitMetric
            else -> QueryMetric.TOTAL
        }
        return ChatRoute.Query(QueryIntent(metric, period, category))
    }

    // ------------------------------------------------------------------

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            .lowercase(vi)
            .replace(Rx.ascii("""\s+"""), " ")

    private fun startsWithCompare(n: String): Boolean =
        Rx.ascii("""^(?:thế |vậy )?(?:còn )?(?:so với|so sánh)""").containsMatchIn(n)

    private fun stripPeriodPhrases(n: String): String =
        n.replace(Rx.ascii("""\d{1,3}\s*ngày\s*(?:qua|gần nhất|gần đây|vừa qua|vừa rồi|trở lại đây)"""), " ")
            .replace(Rx.ascii("""\d{1,2}\s*/\s*\d{1,2}(?:\s*/\s*\d{4})?"""), " ")
            .replace(Rx.ascii("""\d{4}-\d{2}-\d{2}"""), " ")
            .replace(Rx.ascii("""ngày \d{1,2} tháng \d{1,2}"""), " ")

    private fun detectExplicitMetric(n: String): QueryMetric? = when {
        Rx.ascii("""chưa gắn giờ|chưa có giờ|không có giờ|không gắn giờ|chưa có mốc giờ|tự khai báo""").containsMatchIn(n) ->
            QueryMetric.UNTIMED
        Rx.ascii("""dự kiến|đã lên lịch|lên kế hoạch|sắp tới""").containsMatchIn(n) -> QueryMetric.PLANNED
        Rx.ascii("""nhiều nhất|lâu nhất""").containsMatchIn(n) -> QueryMetric.TOP_CATEGORY
        Rx.ascii("""ít nhất""").containsMatchIn(n) -> QueryMetric.LEAST_CATEGORY
        reportWordsRegex.containsMatchIn(n) || Rx.ascii("""phân bổ|chia theo|theo danh mục|chi tiết""").containsMatchIn(n) ->
            QueryMetric.BREAKDOWN
        else -> null
    }

    private val explicitCategoryWords: List<Pair<String, TimeCategory>> = listOf(
        "công việc" to TimeCategory.WORK,
        "làm việc" to TimeCategory.WORK,
        "học tập" to TimeCategory.LEARN,
        "vận động" to TimeCategory.HEALTH,
        "sức khỏe" to TimeCategory.HEALTH,
        "sinh hoạt" to TimeCategory.LIFE,
        "nghỉ ngơi" to TimeCategory.REST,
        "kết nối" to TimeCategory.SOCIAL,
        "giải trí" to TimeCategory.FUN
    )

    private fun detectCategory(n: String): TimeCategory? {
        for ((word, cat) in explicitCategoryWords) if (n.contains(word)) return cat
        // Bỏ các từ báo cáo để "báo cáo" không bị hiểu là danh mục Công việc.
        val forClassify = reportWordsRegex.replace(n, " ")
        val key = VietnameseChatParser.classifyCategory(forClassify)
        return if (key == "other") null else TimeCategory.fromKeyOrNull(key)
    }

    // ------------------------------------------------------------------ periods

    private data class PeriodScan(val hits: List<PeriodHit>, val error: String? = null)

    private fun findPeriods(n: String, today: LocalDate): PeriodScan {
        val hits = mutableListOf<PeriodHit>()
        val monday = today.with(DayOfWeek.MONDAY)

        fun add(regex: Regex, build: (MatchResult) -> QueryPeriod) {
            regex.findAll(n).forEach { hits.add(PeriodHit(it.range, build(it))) }
        }

        add(Rx.ascii("""(?<!\p{L})hôm nay(?!\p{L})""")) {
            QueryPeriod(today, today, "hôm nay", QueryPeriodKind.DAY)
        }
        add(Rx.ascii("""(?<!\p{L})hôm qua(?!\p{L})""")) {
            val d = today.minusDays(1); QueryPeriod(d, d, "hôm qua", QueryPeriodKind.DAY)
        }
        add(Rx.ascii("""(?<!\p{L})hôm kia(?!\p{L})""")) {
            val d = today.minusDays(2); QueryPeriod(d, d, "hôm kia", QueryPeriodKind.DAY)
        }
        add(Rx.ascii("""(?<!\p{L})tuần này(?!\p{L})""")) {
            QueryPeriod(monday, monday.plusDays(6), "tuần này (${QueryPeriod.fmt(monday)}–${QueryPeriod.fmt(monday.plusDays(6))})", QueryPeriodKind.WEEK)
        }
        add(Rx.ascii("""(?<!\p{L})tuần (?:trước|rồi|vừa rồi)(?!\p{L})""")) {
            val s = monday.minusWeeks(1)
            QueryPeriod(s, s.plusDays(6), "tuần trước (${QueryPeriod.fmt(s)}–${QueryPeriod.fmt(s.plusDays(6))})", QueryPeriodKind.WEEK)
        }
        add(Rx.ascii("""(?<!\p{L})tháng này(?!\p{L})""")) {
            val s = today.withDayOfMonth(1)
            QueryPeriod(s, s.withDayOfMonth(s.lengthOfMonth()), "tháng này (${s.monthValue}/${s.year})", QueryPeriodKind.MONTH)
        }
        add(Rx.ascii("""(?<!\p{L})tháng (?:trước|rồi|vừa rồi)(?!\p{L})""")) {
            val s = today.minusMonths(1).withDayOfMonth(1)
            QueryPeriod(s, s.withDayOfMonth(s.lengthOfMonth()), "tháng trước (${s.monthValue}/${s.year})", QueryPeriodKind.MONTH)
        }
        add(Rx.ascii("""(?<!\p{L})tuần qua(?!\p{L})""")) {
            QueryPeriod(today.minusDays(6), today, "7 ngày qua", QueryPeriodKind.ROLLING)
        }
        add(Rx.ascii("""(?<!\p{L})tháng qua(?!\p{L})""")) {
            QueryPeriod(today.minusDays(29), today, "30 ngày qua", QueryPeriodKind.ROLLING)
        }

        var error: String? = null
        Rx.ascii("""(?<!\d)(\d{1,3})\s*ngày\s*(?:qua|gần nhất|gần đây|vừa qua|vừa rồi|trở lại đây)""").findAll(n).forEach {
            val days = it.groupValues[1].toLong()
            if (days < 1 || days > MAX_PERIOD_DAYS) {
                error = "Chat hỗ trợ tối đa $MAX_PERIOD_DAYS ngày mỗi lần hỏi."
            } else {
                hits.add(
                    PeriodHit(it.range, QueryPeriod(today.minusDays(days - 1), today, "$days ngày qua", QueryPeriodKind.ROLLING))
                )
            }
        }

        fun dateHit(range: IntRange, y: Int?, m: Int, d: Int) {
            val year = y ?: today.year
            val date = try {
                LocalDate.of(year, m, d)
            } catch (_: Exception) {
                error = "Ngày không hợp lệ: $d/$m. Hãy hỏi lại theo dạng dd/MM."
                return
            }
            hits.add(PeriodHit(range, QueryPeriod(date, date, "ngày ${QueryPeriod.fmt(date)}", QueryPeriodKind.DATE)))
        }
        Rx.ascii("""(\d{4})-(\d{2})-(\d{2})""").findAll(n).forEach {
            dateHit(it.range, it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Rx.ascii("""(?<![\d/])(\d{1,2})\s*/\s*(\d{1,2})(?:\s*/\s*(\d{4}))?(?![\d/])""").findAll(n).forEach {
            dateHit(it.range, it.groupValues[3].toIntOrNull(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }
        Rx.ascii("""ngày (\d{1,2}) tháng (\d{1,2})""").findAll(n).forEach {
            dateHit(it.range, null, it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }

        // Loại các kết quả chồng lấn, giữ theo thứ tự xuất hiện.
        val ordered = hits.sortedBy { it.range.first }
        val kept = mutableListOf<PeriodHit>()
        for (h in ordered) {
            if (kept.none { it.range.first <= h.range.last && h.range.first <= it.range.last }) kept.add(h)
        }
        if (error == null) {
            kept.firstOrNull { it.period.dayCount > MAX_PERIOD_DAYS }?.let {
                error = "Chat hỗ trợ tối đa $MAX_PERIOD_DAYS ngày mỗi lần hỏi."
            }
        }
        return PeriodScan(kept, error)
    }
}
