package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tổng hợp số liệu của một kỳ, cộng từ LedgerAnalyticsCalculator.computeDayStats theo từng ngày. */
data class PeriodFigures(
    val period: QueryPeriod,
    val timedSeconds: Long,
    val untimedSeconds: Long,
    val plannedSeconds: Long,
    val timedByCategory: Map<TimeCategory, Long>,
    val untimedByCategory: Map<TimeCategory, Long>,
    val plannedByCategory: Map<TimeCategory, Long>
) {
    val hasAnyData: Boolean get() = timedSeconds > 0L || untimedSeconds > 0L || plannedSeconds > 0L
}

data class QueryAnswer(
    val headline: String,
    val lines: List<String>,
    val figures: PeriodFigures,
    val compareFigures: PeriodFigures? = null,
    val notes: List<String> = emptyList()
)

/**
 * AI-01 Slice B — thực thi câu hỏi chỉ-đọc trên danh sách bản ghi đã nằm trong RAM.
 * Không ghi DB, không gọi mạng. Mọi con số đến từ LedgerAnalyticsCalculator.computeDayStats
 * (đã tách timed / untimed / planned và khử trùng khoảng giờ), engine này chỉ cộng theo khoảng ngày.
 */
object LedgerQueryEngine {

    fun execute(
        intent: QueryIntent,
        records: List<LedgerRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis(),
        activeTimer: ActiveTimerSession? = null,
        budgets: List<CategoryTimeBudget> = emptyList()
    ): QueryAnswer {
        val primary = aggregate(intent.period, records, zoneId, nowEpochMs, activeTimer)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()

        // R02: câu hỏi ngân sách đi theo đường riêng (một vòng, không so sánh kỳ, không phụ thuộc "có dữ liệu trong kỳ hay không").
        if (intent.metric == QueryMetric.BUDGET) {
            return budgetAnswer(intent, primary, budgets, records, zoneId, nowEpochMs, activeTimer, today)
        }
        val compare = intent.compareTo?.let { aggregate(it, records, zoneId, nowEpochMs, activeTimer) }

        val notes = mutableListOf<String>()
        if (intent.period.end.isAfter(today) || intent.compareTo?.end?.isAfter(today) == true) {
            notes.add("Chỉ tính phần đã diễn ra tới hiện tại; ngày chưa tới được tính là dự kiến.")
        }
        if (primary.untimedSeconds > 0L && intent.metric != QueryMetric.UNTIMED) {
            notes.add("Đã ghi nhận ${fmt(primary.timedSeconds + primary.untimedSeconds)} gồm ${fmt(primary.timedSeconds)} có mốc giờ và ${fmt(primary.untimedSeconds)} chưa gắn giờ (tách riêng).")
        }

        if (compare != null) return buildComparison(intent, primary, compare, notes)

        if (!primary.hasAnyData) {
            return QueryAnswer(
                headline = "Chưa có dữ liệu trong ${intent.period.label}.",
                lines = listOf("Hãy ghi nhận hoạt động rồi hỏi lại."),
                figures = primary,
                notes = notes
            )
        }

        val cat = intent.category
        return when (intent.metric) {
            QueryMetric.TOTAL -> if (cat == null) totalAnswer(intent, primary, notes) else categoryAnswer(intent, primary, cat, notes)
            QueryMetric.UNTIMED -> untimedAnswer(intent, primary, notes)
            QueryMetric.PLANNED -> plannedAnswer(intent, primary, notes)
            QueryMetric.TOP_CATEGORY -> rankAnswer(intent, primary, top = true, notes = notes)
            QueryMetric.LEAST_CATEGORY -> rankAnswer(intent, primary, top = false, notes = notes)
            QueryMetric.BREAKDOWN -> breakdownAnswer(intent, primary, notes)
            QueryMetric.BUDGET -> error("BUDGET được xử lý ở trên")
        }
    }

    // ------------------------------------------------------------------ ngân sách (R02)

    private fun budgetAnswer(
        intent: QueryIntent,
        primary: PeriodFigures,
        budgets: List<CategoryTimeBudget>,
        records: List<LedgerRecord>,
        zoneId: ZoneId,
        nowEpochMs: Long,
        activeTimer: ActiveTimerSession?,
        today: java.time.LocalDate
    ): QueryAnswer {
        val notes = mutableListOf<String>()
        if (intent.compareTo != null) notes.add("Ngân sách không có so sánh hai kỳ; chỉ hiển thị mức hiện tại.")
        if (budgets.isEmpty()) {
            return QueryAnswer(
                headline = "Bạn chưa đặt ngân sách nào.",
                lines = listOf("Hãy đặt mục tiêu theo danh mục ở tab Insights rồi hỏi lại."),
                figures = primary,
                notes = notes
            )
        }
        // Cùng một hàm với Tổng quan / Insights / Nhật ký ngày: không có bản tính thứ hai.
        val all = LedgerAnalyticsCalculator.computeBudgetProgress(budgets, records, zoneId, nowEpochMs, activeTimer)
        val cat = intent.category
        val shown = if (cat == null) all else all.filter { it.category == cat }
        if (shown.isEmpty()) {
            return QueryAnswer(
                headline = "Bạn chưa đặt ngân sách cho ${cat?.displayName ?: "danh mục này"}.",
                lines = all.take(MAX_BUDGET_LINES).map { budgetLine(it) },
                figures = primary,
                notes = notes
            )
        }
        if (intent.period.start.isAfter(today) || intent.period.end.isBefore(today)) {
            notes.add("Ngân sách luôn tính cho ngày/tuần hiện tại, không theo khoảng ngày bạn hỏi.")
        }
        notes.add("Đã ghi nhận gồm cả thời lượng chưa gắn giờ; hoạt động dự kiến không được tính.")
        val exceeded = shown.count { it.combinedIsExceeded }
        val headline = if (shown.size == 1) {
            val p = shown[0]
            "Ngân sách ${p.category.displayName} (${p.targetFormatted}): đã ghi nhận ${p.combinedUsedFormatted}, ${p.combinedDifferenceLabelVi.lowercase()}."
        } else {
            "Ngân sách hiện tại: ${shown.size} danh mục" + if (exceeded > 0) ", $exceeded đã vượt mục tiêu." else ", chưa danh mục nào vượt mục tiêu."
        }
        return QueryAnswer(
            headline = headline,
            lines = if (shown.size == 1) listOf(budgetLine(shown[0])) else shown.take(MAX_BUDGET_LINES).map { budgetLine(it) },
            figures = primary,
            notes = notes
        )
    }

    private const val MAX_BUDGET_LINES = 6

    private fun budgetLine(p: com.timewallet.mvp.domain.model.TimeBudgetProgress): String =
        "${p.category.emoji} ${p.category.displayName}: ${p.recordedSummaryVi} / ${p.targetFormatted} — ${p.combinedDifferenceLabelVi}"

    fun aggregate(
        period: QueryPeriod,
        records: List<LedgerRecord>,
        zoneId: ZoneId,
        nowEpochMs: Long,
        activeTimer: ActiveTimerSession?
    ): PeriodFigures {
        var timed = 0L
        var untimed = 0L
        var planned = 0L
        val timedBy = mutableMapOf<TimeCategory, Long>()
        val untimedBy = mutableMapOf<TimeCategory, Long>()
        val plannedBy = mutableMapOf<TimeCategory, Long>()

        var d = period.start
        while (!d.isAfter(period.end)) {
            val s = LedgerAnalyticsCalculator.computeDayStats(d.toString(), records, zoneId, nowEpochMs, activeTimer)
            timed += s.timedSeconds
            untimed += s.untimedSeconds
            planned += s.plannedSeconds
            s.categoryBreakdown.forEach { timedBy.merge(it.category, it.durationSeconds, Long::plus) }
            s.untimedCategoryBreakdown.forEach { untimedBy.merge(it.category, it.durationSeconds, Long::plus) }
            s.plannedCategoryBreakdown.forEach { plannedBy.merge(it.category, it.durationSeconds, Long::plus) }
            d = d.plusDays(1)
        }
        return PeriodFigures(period, timed, untimed, planned, timedBy, untimedBy, plannedBy)
    }

    // ------------------------------------------------------------------ đơn kỳ

    private fun totalAnswer(intent: QueryIntent, f: PeriodFigures, notes: List<String>): QueryAnswer {
        val lines = mutableListOf<String>()
        topCategories(f).take(3).forEach { (c, s) -> lines.add("${c.emoji} ${c.displayName}: ${fmt(s)}") }
        if (f.untimedSeconds > 0L) lines.add("Chưa gắn giờ: ${fmt(f.untimedSeconds)} (tách riêng)")
        if (f.plannedSeconds > 0L) lines.add("Dự kiến: ${fmt(f.plannedSeconds)}")
        return QueryAnswer(
            headline = "${cap(intent.period.label)}: ${fmt(f.timedSeconds)} có mốc giờ.",
            lines = lines,
            figures = f,
            notes = notes
        )
    }

    private fun categoryAnswer(intent: QueryIntent, f: PeriodFigures, cat: TimeCategory, notes: List<String>): QueryAnswer {
        val timed = f.timedByCategory[cat] ?: 0L
        val untimed = f.untimedByCategory[cat] ?: 0L
        val planned = f.plannedByCategory[cat] ?: 0L
        val lines = mutableListOf<String>()
        if (untimed > 0L) lines.add("Chưa gắn giờ: ${fmt(untimed)} (tách riêng)")
        if (planned > 0L) lines.add("Dự kiến: ${fmt(planned)}")
        val head = if (timed == 0L && untimed == 0L && planned == 0L) {
            "${cap(intent.period.label)}: chưa có bản ghi ${cat.displayName}."
        } else {
            "${cap(intent.period.label)} — ${cat.displayName}: ${fmt(timed)} có mốc giờ."
        }
        return QueryAnswer(head, lines, f, notes = notes)
    }

    private fun untimedAnswer(intent: QueryIntent, f: PeriodFigures, notes: List<String>): QueryAnswer {
        val cat = intent.category
        val total = if (cat != null) f.untimedByCategory[cat] ?: 0L else f.untimedSeconds
        val lines = f.untimedByCategory.entries.sortedByDescending { it.value }
            .filter { cat == null || it.key == cat }
            .map { "${it.key.emoji} ${it.key.displayName}: ${fmt(it.value)}" }
        val head = if (total == 0L) "${cap(intent.period.label)}: không có thời lượng chưa gắn giờ."
        else "${cap(intent.period.label)}: ${fmt(total)} chưa gắn giờ."
        return QueryAnswer(head, lines, f, notes = notes.filterNot { it.startsWith("Đã ghi nhận ") })
    }

    private fun plannedAnswer(intent: QueryIntent, f: PeriodFigures, notes: List<String>): QueryAnswer {
        val cat = intent.category
        val total = if (cat != null) f.plannedByCategory[cat] ?: 0L else f.plannedSeconds
        val lines = f.plannedByCategory.entries.sortedByDescending { it.value }
            .filter { cat == null || it.key == cat }
            .map { "${it.key.emoji} ${it.key.displayName}: ${fmt(it.value)}" }
        val head = if (total == 0L) "${cap(intent.period.label)}: chưa có hoạt động dự kiến."
        else "${cap(intent.period.label)}: ${fmt(total)} dự kiến (chưa tính vào thời gian đã dùng)."
        return QueryAnswer(head, lines, f, notes = notes)
    }

    private fun rankAnswer(intent: QueryIntent, f: PeriodFigures, top: Boolean, notes: List<String>): QueryAnswer {
        val ranked = topCategories(f)
        if (ranked.isEmpty()) {
            return QueryAnswer("${cap(intent.period.label)}: chưa có thời gian có mốc giờ để xếp hạng.", emptyList(), f, notes = notes)
        }
        val (c, s) = if (top) ranked.first() else ranked.last()
        val word = if (top) "nhiều nhất" else "ít nhất"
        val lines = ranked.take(5).map { "${it.first.emoji} ${it.first.displayName}: ${fmt(it.second)}" }
        return QueryAnswer("${cap(intent.period.label)}: ${c.displayName} $word (${fmt(s)}).", lines, f, notes = notes)
    }

    private fun breakdownAnswer(intent: QueryIntent, f: PeriodFigures, notes: List<String>): QueryAnswer {
        val lines = mutableListOf<String>()
        val denom = f.timedSeconds.coerceAtLeast(1L).toDouble()
        topCategories(f).forEach { (c, s) ->
            lines.add("${c.emoji} ${c.displayName}: ${fmt(s)} (${(s * 100.0 / denom).roundToInt()}%)")
        }
        if (f.untimedSeconds > 0L) lines.add("Chưa gắn giờ: ${fmt(f.untimedSeconds)} (tách riêng)")
        if (f.plannedSeconds > 0L) lines.add("Dự kiến: ${fmt(f.plannedSeconds)}")
        return QueryAnswer("${cap(intent.period.label)}: ${fmt(f.timedSeconds)} có mốc giờ.", lines, f, notes = notes)
    }

    // ------------------------------------------------------------------ so sánh kỳ

    private fun buildComparison(
        intent: QueryIntent,
        a: PeriodFigures,
        b: PeriodFigures,
        notes: List<String>
    ): QueryAnswer {
        if (!a.hasAnyData && !b.hasAnyData) {
            return QueryAnswer(
                "Chưa có dữ liệu trong ${intent.period.label} và ${intent.compareTo!!.label}.",
                emptyList(), a, b, notes
            )
        }
        val cat = intent.category
        val lines = mutableListOf<String>()
        val metricValue: (PeriodFigures) -> Long = { f ->
            when (intent.metric) {
                QueryMetric.UNTIMED -> if (cat != null) f.untimedByCategory[cat] ?: 0L else f.untimedSeconds
                QueryMetric.PLANNED -> if (cat != null) f.plannedByCategory[cat] ?: 0L else f.plannedSeconds
                else -> if (cat != null) f.timedByCategory[cat] ?: 0L else f.timedSeconds
            }
        }
        val x = metricValue(a)
        val y = metricValue(b)
        val what = when (intent.metric) {
            QueryMetric.UNTIMED -> "chưa gắn giờ"
            QueryMetric.PLANNED -> "dự kiến"
            else -> "có mốc giờ"
        } + (cat?.let { " (${it.displayName})" } ?: "")

        lines.add("${cap(intent.period.label)}: ${fmt(x)}")
        lines.add("${cap(intent.compareTo!!.label)}: ${fmt(y)}")
        lines.add(deltaLine(x, y))

        val perCategory = cat == null && (intent.metric == QueryMetric.BREAKDOWN ||
            intent.metric == QueryMetric.TOP_CATEGORY || intent.metric == QueryMetric.LEAST_CATEGORY)
        if (perCategory) {
            val cats = (a.timedByCategory.keys + b.timedByCategory.keys).sortedByDescending {
                (a.timedByCategory[it] ?: 0L) + (b.timedByCategory[it] ?: 0L)
            }
            cats.forEach { c ->
                val ca = a.timedByCategory[c] ?: 0L
                val cb = b.timedByCategory[c] ?: 0L
                lines.add("${c.emoji} ${c.displayName}: ${fmt(ca)} so với ${fmt(cb)} (${signed(ca - cb)})")
            }
        }

        val c = notes.toMutableList()
        if (y == 0L && x > 0L) c.add("${cap(intent.compareTo.label)} chưa có dữ liệu để so sánh.")
        return QueryAnswer(
            headline = "So sánh ${intent.period.label} với ${intent.compareTo.label} — $what",
            lines = lines,
            figures = a,
            compareFigures = b,
            notes = c
        )
    }

    private fun deltaLine(x: Long, y: Long): String {
        val diff = x - y
        if (y == 0L) return "Chênh lệch: ${signed(diff)} (kỳ so sánh chưa có dữ liệu nên không tính phần trăm)"
        val pct = (diff * 100.0 / y).roundToInt()
        val sign = if (pct > 0) "+" else ""
        return "Chênh lệch: ${signed(diff)} ($sign$pct%)"
    }

    // ------------------------------------------------------------------ tiện ích

    private fun topCategories(f: PeriodFigures): List<Pair<TimeCategory, Long>> =
        f.timedByCategory.entries.filter { it.value > 0L }.sortedByDescending { it.value }.map { it.key to it.value }

    private fun signed(seconds: Long): String = when {
        seconds > 0L -> "+${fmt(seconds)}"
        seconds < 0L -> "-${fmt(abs(seconds))}"
        else -> "0p"
    }

    fun fmt(seconds: Long): String =
        if (seconds <= 0L) "0p" else LedgerAnalyticsCalculator.formatCompactDurationSeconds(seconds)

    private fun cap(s: String): String = s.replaceFirstChar { it.uppercase() }
}

/** Thẻ trả lời hiển thị trong tab Chat (chỉ trong RAM, không lưu Room). */
data class ChatAnswer(
    val question: String,
    val headline: String,
    val lines: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val isClarification: Boolean = false,
    val suggestions: List<String> = emptyList(),
    /** Nhãn tiêu đề thẻ; null = dùng nhãn mặc định theo loại. */
    val label: String? = null,
    /** v1.0.8: thẻ nêu trạng thái giao dịch tại một thời điểm (số đề xuất đang chờ...); bị xóa khi danh sách đề xuất đổi. */
    val isTransactionStatus: Boolean = false
) {
    companion object {
        fun fromQuery(question: String, answer: QueryAnswer) =
            ChatAnswer(question, answer.headline, answer.lines, answer.notes)

        fun clarification(question: String, message: String, suggestions: List<String>) =
            ChatAnswer(question, message, isClarification = true, suggestions = suggestions)
    }
}
