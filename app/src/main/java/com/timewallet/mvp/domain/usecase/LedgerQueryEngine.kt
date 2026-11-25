package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalTargetType
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TagAssignmentUndoToken
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
        budgets: List<CategoryTimeBudget> = emptyList(),
        tags: List<Tag> = emptyList(),
        goals: List<Goal> = emptyList()
    ): QueryAnswer {
        val primary = aggregate(intent.period, records, zoneId, nowEpochMs, activeTimer)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val resolvedTag = resolveTargetTag(intent, tags)

        // R02: câu hỏi ngân sách / mục tiêu đi theo đường riêng (một vòng, dùng chung dữ liệu với Tổng quan/Thống kê).
        if (intent.metric == QueryMetric.BUDGET) {
            return budgetAnswer(intent, primary, budgets, goals, tags, resolvedTag, records, zoneId, nowEpochMs, activeTimer, today)
        }
        val compare = intent.compareTo?.let { aggregate(it, records, zoneId, nowEpochMs, activeTimer) }

        val notes = mutableListOf<String>()
        if (intent.period.end.isAfter(today) || intent.compareTo?.end?.isAfter(today) == true) {
            notes.add("Chỉ tính phần đã diễn ra tới hiện tại; ngày chưa tới được tính là dự kiến.")
        }
        if (primary.untimedSeconds > 0L && intent.metric != QueryMetric.UNTIMED) {
            notes.add("Đã ghi nhận ${fmt(primary.timedSeconds + primary.untimedSeconds)} gồm ${fmt(primary.timedSeconds)} có mốc giờ và ${fmt(primary.untimedSeconds)} chưa gắn giờ (tách riêng).")
        }

        if (intent.metric == QueryMetric.TYPICAL_WINDOW) {
            return typicalWindowAnswer(intent, primary, records, resolvedTag, zoneId, nowEpochMs, notes)
        }

        if (resolvedTag != null || !intent.tagName.isNullOrBlank()) {
            return tagAnswer(intent, primary, compare, records, resolvedTag, tags, zoneId, nowEpochMs, activeTimer, notes)
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
            QueryMetric.TOTAL -> if (cat == null) totalAnswer(intent, primary, notes) else categoryAnswer(intent, primary, cat, records, tags, zoneId, nowEpochMs, activeTimer, notes)
            QueryMetric.UNTIMED -> untimedAnswer(intent, primary, notes)
            QueryMetric.PLANNED -> plannedAnswer(intent, primary, notes)
            QueryMetric.TOP_CATEGORY -> rankAnswer(intent, primary, top = true, notes = notes)
            QueryMetric.LEAST_CATEGORY -> rankAnswer(intent, primary, top = false, notes = notes)
            QueryMetric.BREAKDOWN -> breakdownAnswer(intent, primary, notes)
            QueryMetric.BUDGET -> error("BUDGET được xử lý ở trên")
            QueryMetric.TYPICAL_WINDOW -> error("TYPICAL_WINDOW được xử lý ở trên")
        }
    }

    // ------------------------------------------------------------------ ngân sách (R02)

    private fun budgetAnswer(
        intent: QueryIntent,
        primary: PeriodFigures,
        budgets: List<CategoryTimeBudget>,
        goals: List<Goal>,
        tags: List<Tag>,
        resolvedTag: Tag?,
        records: List<LedgerRecord>,
        zoneId: ZoneId,
        nowEpochMs: Long,
        activeTimer: ActiveTimerSession?,
        today: java.time.LocalDate
    ): QueryAnswer {
        val notes = mutableListOf<String>()
        if (intent.compareTo != null) notes.add("Ngân sách không có so sánh hai kỳ; chỉ hiển thị mức hiện tại.")

        val activeTags = tags.filter { !it.isArchived }
        val tagMap = activeTags.associateBy { it.id }
        val activeGoals = GoalRules.summaries(
            goals = goals,
            records = records,
            tagNameOf = { id -> tagMap[id]?.name },
            today = today,
            nowEpochMs = nowEpochMs,
            zone = zoneId,
            activeTimer = activeTimer
        ).filter { it.progress != null }

        if (budgets.isEmpty() && activeGoals.isEmpty()) {
            return QueryAnswer(
                headline = "Bạn chưa đặt ngân sách nào.",
                lines = listOf("Hãy đặt mục tiêu hoặc ngân sách theo danh mục/Tag ở tab Thống kê rồi hỏi lại."),
                figures = primary,
                notes = notes
            )
        }

        // Bảo toàn nguyên vẹn hành vi cũ khi chỉ có CategoryTimeBudget (R02 & V106BudgetQueryTest)
        if (activeGoals.isEmpty()) {
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

        // Thống nhất cả Goal mới và CategoryTimeBudget cũ chưa trùng trên cùng một phép tính với Thống kê/Mục tiêu.
        val allBudgets = LedgerAnalyticsCalculator.computeBudgetProgress(budgets, records, zoneId, nowEpochMs, activeTimer)
        val coveredWeeklyMaxCategories = activeGoals
            .filter {
                it.latest.targetType == GoalTargetType.CATEGORY &&
                    it.latest.kind == GoalKind.MAX &&
                    it.latest.period == GoalPeriod.WEEK
            }
            .mapTo(HashSet()) { it.latest.targetKey }
        val uncoveredBudgets = allBudgets.filter {
            it.period != com.timewallet.mvp.domain.model.BudgetPeriod.WEEKLY || it.category.key !in coveredWeeklyMaxCategories
        }

        val cat = intent.category
        val matchedGoals = activeGoals.filter { s ->
            when {
                resolvedTag != null -> s.latest.targetType == GoalTargetType.TAG && s.latest.targetKey == resolvedTag.id
                cat != null -> (s.latest.targetType == GoalTargetType.CATEGORY && s.latest.targetKey == cat.key) ||
                    (s.latest.targetType == GoalTargetType.TAG && tagMap[s.latest.targetKey]?.category == cat)
                else -> true
            }
        }
        val matchedBudgets = if (resolvedTag != null) {
            emptyList()
        } else {
            uncoveredBudgets.filter { cat == null || it.category == cat }
        }

        if (matchedGoals.isEmpty() && matchedBudgets.isEmpty()) {
            val targetLabel = resolvedTag?.name ?: cat?.displayName ?: "mục này"
            val fallbackLines = (activeGoals.map { goalSummaryLine(it, tagMap) } +
                uncoveredBudgets.map { budgetLine(it) }).take(MAX_BUDGET_LINES)
            return QueryAnswer(
                headline = "Bạn chưa đặt mục tiêu hay ngân sách cho $targetLabel.",
                lines = fallbackLines,
                figures = primary,
                notes = notes
            )
        }

        if (intent.period.start.isAfter(today) || intent.period.end.isBefore(today)) {
            notes.add("Mục tiêu và ngân sách luôn tính cho ngày/tuần hiện tại, không theo khoảng ngày bạn hỏi.")
        }
        notes.add("Đã ghi nhận gồm cả thời lượng có mốc giờ, chưa gắn giờ và đồng hồ đang chạy; hoạt động dự kiến không được tính.")

        val combinedLines = (matchedGoals.map { goalSummaryLine(it, tagMap) } +
            matchedBudgets.map { budgetLine(it) }).take(MAX_BUDGET_LINES)

        val headline = when {
            matchedGoals.size == 1 && matchedBudgets.isEmpty() -> {
                val s = matchedGoals[0]
                val p = s.progress!!
                val math = GoalDisplayMath.from(p.usedSeconds, p.targetSeconds)
                val periodVi = if (s.latest.period == GoalPeriod.DAY) "ngày" else "tuần"
                val statusVi = when {
                    s.latest.kind == GoalKind.MIN && p.met -> "đã đạt mục tiêu"
                    s.latest.kind == GoalKind.MIN && math.remainingMinutes > 0L -> "còn thiếu ${math.remainingLabel}"
                    s.latest.kind == GoalKind.MIN -> "còn thiếu < 1p"
                    p.overSeconds > 0L -> if (math.overMinutes > 0L) "vượt ${math.overLabel}" else "vượt < 1p"
                    else -> "còn lại ${math.remainingLabel}"
                }
                "Mục tiêu ${s.name} (${math.targetLabel}/$periodVi): đã ghi nhận ${math.usedLabel}, $statusVi."
            }
            matchedBudgets.size == 1 && matchedGoals.isEmpty() -> {
                val p = matchedBudgets[0]
                "Ngân sách ${p.category.displayName} (${p.targetFormatted}): đã ghi nhận ${p.combinedUsedFormatted}, ${p.combinedDifferenceLabelVi.lowercase()}."
            }
            else -> {
                val totalCount = matchedGoals.size + matchedBudgets.size
                val alertCount = matchedGoals.count { s ->
                    val p = s.progress!!
                    s.latest.kind == GoalKind.MAX && p.overSeconds > 0L
                } + matchedBudgets.count { it.combinedIsExceeded }
                val metMinCount = matchedGoals.count { it.latest.kind == GoalKind.MIN && it.progress!!.met }
                when {
                    alertCount > 0 -> "Mục tiêu & ngân sách hiện tại: $totalCount mục, $alertCount đang vượt giới hạn."
                    metMinCount > 0 -> "Mục tiêu & ngân sách hiện tại: $totalCount mục ($metMinCount mục tiêu tối thiểu đã đạt)."
                    else -> "Mục tiêu & ngân sách hiện tại: $totalCount mục đang theo dõi."
                }
            }
        }
        return QueryAnswer(
            headline = headline,
            lines = combinedLines,
            figures = primary,
            notes = notes
        )
    }

    private fun goalSummaryLine(s: GoalRules.Summary, tagMap: Map<String, Tag>): String {
        val p = s.progress!!
        val math = GoalDisplayMath.from(p.usedSeconds, p.targetSeconds)
        val cat = if (s.latest.targetType == GoalTargetType.TAG) {
            tagMap[s.latest.targetKey]?.category ?: TimeCategory.OTHER
        } else {
            TimeCategory.fromKeyOrDefault(s.latest.targetKey)
        }
        val periodSuffix = if (s.latest.period == GoalPeriod.DAY) "/ngày" else "/tuần"
        val sign = if (s.latest.kind == GoalKind.MIN) "≥ " else "≤ "
        val status = when {
            s.latest.kind == GoalKind.MIN && p.met -> "Đạt mục tiêu"
            s.latest.kind == GoalKind.MIN && math.remainingMinutes > 0L -> "Còn thiếu ${math.remainingLabel}"
            s.latest.kind == GoalKind.MIN -> "Còn thiếu < 1p"
            p.overSeconds > 0L -> if (math.overMinutes > 0L) "Vượt ${math.overLabel}" else "Vượt < 1p"
            else -> "Còn ${math.remainingLabel}"
        }
        return "${cat.emoji} ${s.name}: ${math.usedLabel} / $sign${math.targetLabel}$periodSuffix — $status"
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

    private fun categoryAnswer(
        intent: QueryIntent,
        f: PeriodFigures,
        cat: TimeCategory,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        zoneId: ZoneId,
        nowEpochMs: Long,
        activeTimer: ActiveTimerSession?,
        notes: List<String>
    ): QueryAnswer {
        val timed = f.timedByCategory[cat] ?: 0L
        val untimed = f.untimedByCategory[cat] ?: 0L
        val planned = f.plannedByCategory[cat] ?: 0L
        val totalActual = timed + untimed
        val lines = mutableListOf<String>()
        if (untimed > 0L) lines.add("Chưa gắn giờ: ${fmt(untimed)} (tách riêng)")
        if (planned > 0L) lines.add("Dự kiến: ${fmt(planned)} (chưa tính vào thực tế)")

        val activeTags = tags.filter { !it.isArchived }
        if (activeTags.isNotEmpty() && totalActual > 0L) {
            val byTag = computeTagTotalsInPeriod(
                period = intent.period,
                records = records,
                tags = activeTags,
                categoryFilter = cat,
                nowEpochMs = nowEpochMs,
                activeTimer = activeTimer,
                zoneId = zoneId
            )
            if (byTag.taggedActualSeconds.isNotEmpty()) {
                val tagParts = byTag.taggedActualSeconds.entries
                    .sortedByDescending { it.value }
                    .take(4)
                    .mapNotNull { (tid, sec) ->
                        val tName = activeTags.firstOrNull { it.id == tid }?.name ?: return@mapNotNull null
                        "$tName ${fmt(sec)}"
                    }
                val parts = tagParts.toMutableList()
                if (byTag.untaggedActualSeconds > 0L) {
                    parts += "chưa gắn tag ${fmt(byTag.untaggedActualSeconds)}"
                }
                if (parts.isNotEmpty()) {
                    lines.add("Trong ${cat.displayName} (${fmt(totalActual)} thực tế): ${parts.joinToString(" · ")}")
                }
            }
        }

        val head = if (timed == 0L && untimed == 0L && planned == 0L) {
            "${cap(intent.period.label)}: chưa có bản ghi ${cat.displayName}."
        } else {
            "${cap(intent.period.label)} — ${cat.displayName}: ${fmt(timed)} có mốc giờ."
        }
        return QueryAnswer(head, lines, f, notes = notes)
    }

    private data class PeriodTagBreakdown(
        val taggedActualSeconds: Map<String, Long>,
        val taggedTimedSeconds: Map<String, Long>,
        val taggedUntimedSeconds: Map<String, Long>,
        val taggedPlannedSeconds: Map<String, Long>,
        val untaggedActualSeconds: Long
    )

    private fun resolveTargetTag(intent: QueryIntent, tags: List<Tag>): Tag? {
        val active = tags.filter { !it.isArchived }
        intent.tagId?.let { id -> active.firstOrNull { it.id == id }?.let { return it } }
        intent.tagName?.let { name ->
            val key = TagRules.nameKey(name)
            if (key.isNotEmpty()) {
                active.firstOrNull { it.nameKey == key }?.let { return it }
            }
        }
        return null
    }

    private fun computeTagTotalsInPeriod(
        period: QueryPeriod,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        categoryFilter: TimeCategory?,
        nowEpochMs: Long,
        activeTimer: ActiveTimerSession?,
        zoneId: ZoneId
    ): PeriodTagBreakdown {
        val taggedTimed = mutableMapOf<String, Long>()
        val taggedUntimed = mutableMapOf<String, Long>()
        val taggedPlanned = mutableMapOf<String, Long>()
        var untaggedTimed = 0L
        var untaggedUntimed = 0L

        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        var day = period.start
        while (!day.isAfter(period.end)) {
            val dayStr = day.toString()
            val isFuture = day.isAfter(today)
            val dayStartMs = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEndMs = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val pastCutoffMs = if (isFuture) dayStartMs else min(nowEpochMs, dayEndMs)

            // Theo tagId: khử trùng lặp khoảng thời gian trên từng tag nếu có hai phiên giao nhau cùng tag.
            val timedIntervalsByTag = mutableMapOf<String?, MutableList<Pair<Long, Long>>>()

            for (r in records) {
                if (r.isCancelled || r.isNeedsReconciliation) continue
                if (categoryFilter != null && r.category != categoryFilter) continue
                val tid = r.tagId?.takeIf { id -> tags.any { it.id == id } }
                if (r.startedAt != null && r.endedAt != null) {
                    val s = r.startedAt
                    val e = r.endedAt
                    if (e <= dayStartMs - 1000L || s >= dayEndMs + 1000L) continue
                    val isPlan = r.layer == TimeLayer.PLANNED || r.status.equals("PLANNED", ignoreCase = true) || s >= pastCutoffMs
                    if (isPlan) {
                        val clippedStart = max(s, dayStartMs)
                        val clippedEnd = min(e, dayEndMs)
                        if (clippedEnd > clippedStart && tid != null) {
                            taggedPlanned[tid] = (taggedPlanned[tid] ?: 0L) + ((clippedEnd - clippedStart) / 1000L)
                        }
                    } else {
                        val cutoffMs = min(nowEpochMs, r.confirmedCutoffEpochMsAt(nowEpochMs))
                        val confEnd = min(cutoffMs, e)
                        val cs = max(s, dayStartMs)
                        val ce = min(min(confEnd, dayEndMs), nowEpochMs)
                        if (ce > cs) {
                            timedIntervalsByTag.getOrPut(tid) { mutableListOf() }.add(cs to ce)
                        }
                    }
                } else if (!isFuture && r.date == dayStr) {
                    val isPlan = r.layer == TimeLayer.PLANNED || r.status.equals("PLANNED", ignoreCase = true)
                    if (isPlan) {
                        if (tid != null) taggedPlanned[tid] = (taggedPlanned[tid] ?: 0L) + r.durationSeconds
                    } else {
                        if (tid != null) taggedUntimed[tid] = (taggedUntimed[tid] ?: 0L) + r.durationSeconds
                        else untaggedUntimed += r.durationSeconds
                    }
                }
            }

            if (activeTimer != null && activeTimer.startedAtEpochMs < nowEpochMs && pastCutoffMs > dayStartMs) {
                val tCat = TimeCategory.fromKeyOrDefault(activeTimer.categoryKey)
                if (categoryFilter == null || tCat == categoryFilter) {
                    val tid = activeTimer.tagId?.takeIf { id -> tags.any { it.id == id } }
                    val cs = max(activeTimer.startedAtEpochMs, dayStartMs)
                    val ce = min(nowEpochMs, dayEndMs)
                    if (ce > cs) {
                        timedIntervalsByTag.getOrPut(tid) { mutableListOf() }.add(cs to ce)
                    }
                }
            }

            for ((tid, spans) in timedIntervalsByTag) {
                val secs = unionIntervalSeconds(spans)
                if (secs <= 0L) continue
                if (tid != null) {
                    taggedTimed[tid] = (taggedTimed[tid] ?: 0L) + secs
                } else {
                    untaggedTimed += secs
                }
            }
            day = day.plusDays(1)
        }

        val taggedActual = mutableMapOf<String, Long>()
        for (tid in taggedTimed.keys + taggedUntimed.keys) {
            taggedActual[tid] = (taggedTimed[tid] ?: 0L) + (taggedUntimed[tid] ?: 0L)
        }
        return PeriodTagBreakdown(
            taggedActualSeconds = taggedActual,
            taggedTimedSeconds = taggedTimed,
            taggedUntimedSeconds = taggedUntimed,
            taggedPlannedSeconds = taggedPlanned,
            untaggedActualSeconds = untaggedTimed + untaggedUntimed
        )
    }

    private fun unionIntervalSeconds(spans: List<Pair<Long, Long>>): Long {
        if (spans.isEmpty()) return 0L
        val sorted = spans.sortedBy { it.first }
        var totalMs = 0L
        var curS = sorted[0].first
        var curE = sorted[0].second
        for (i in 1 until sorted.size) {
            val (ns, ne) = sorted[i]
            if (ns <= curE) {
                if (ne > curE) curE = ne
            } else {
                totalMs += (curE - curS).coerceAtLeast(0L)
                curS = ns
                curE = ne
            }
        }
        totalMs += (curE - curS).coerceAtLeast(0L)
        return totalMs / 1000L
    }

    private fun tagAnswer(
        intent: QueryIntent,
        primary: PeriodFigures,
        compare: PeriodFigures?,
        records: List<LedgerRecord>,
        resolvedTag: Tag?,
        tags: List<Tag>,
        zoneId: ZoneId,
        nowEpochMs: Long,
        activeTimer: ActiveTimerSession?,
        notes: List<String>
    ): QueryAnswer {
        if (resolvedTag == null) {
            val label = intent.tagName.orEmpty().trim()
            return QueryAnswer(
                headline = "Chưa tìm thấy Tag \"$label\" đang hoạt động.",
                lines = listOf("Bạn có thể tạo Tag trong Cài đặt › Quản lý Tag hoặc hỏi tổng thời gian theo danh mục."),
                figures = primary,
                compareFigures = compare,
                notes = notes
            )
        }
        val activeTags = tags.filter { !it.isArchived }
        val cat = resolvedTag.category
        val periodStats = computeTagTotalsInPeriod(
            period = intent.period,
            records = records,
            tags = activeTags,
            categoryFilter = null,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer,
            zoneId = zoneId
        )
        val catPeriodStats = computeTagTotalsInPeriod(
            period = intent.period,
            records = records,
            tags = activeTags,
            categoryFilter = cat,
            nowEpochMs = nowEpochMs,
            activeTimer = activeTimer,
            zoneId = zoneId
        )

        val tagTimed = periodStats.taggedTimedSeconds[resolvedTag.id] ?: 0L
        val tagUntimed = periodStats.taggedUntimedSeconds[resolvedTag.id] ?: 0L
        val tagActual = tagTimed + tagUntimed
        val tagPlanned = periodStats.taggedPlannedSeconds[resolvedTag.id] ?: 0L

        val catTimed = primary.timedByCategory[cat] ?: 0L
        val catUntimed = primary.untimedByCategory[cat] ?: 0L
        val catActual = catTimed + catUntimed
        val untaggedInCat = catPeriodStats.untaggedActualSeconds
        val otherTaggedInCat = (catActual - (catPeriodStats.taggedActualSeconds[resolvedTag.id] ?: 0L) - untaggedInCat).coerceAtLeast(0L)

        val lines = mutableListOf<String>()
        if (tagTimed > 0L) lines += "${resolvedTag.name} (có mốc giờ): ${fmt(tagTimed)}"
        if (tagUntimed > 0L) lines += "${resolvedTag.name} (chưa gắn giờ): ${fmt(tagUntimed)}"
        if (tagPlanned > 0L) lines += "${resolvedTag.name} dự kiến: ${fmt(tagPlanned)} (chưa tính vào thực tế)"

        if (catActual > 0L) {
            val parts = buildList {
                add("${fmt(tagActual)} mang tag ${resolvedTag.name}")
                if (untaggedInCat > 0L) add("${fmt(untaggedInCat)} chưa phân loại / chưa gắn tag")
                if (otherTaggedInCat > 0L) add("${fmt(otherTaggedInCat)} mang tag khác")
            }
            lines += "Tổng ${cat.displayName}: ${fmt(catActual)} (${parts.joinToString(", ")})"
        }

        if (compare != null) {
            val prevStats = computeTagTotalsInPeriod(
                period = compare.period,
                records = records,
                tags = activeTags,
                categoryFilter = null,
                nowEpochMs = nowEpochMs,
                activeTimer = activeTimer,
                zoneId = zoneId
            )
            val prevActual = prevStats.taggedActualSeconds[resolvedTag.id] ?: 0L
            lines += "${cap(compare.period.label)}: ${fmt(prevActual)}"
            lines += deltaLine(tagActual, prevActual)
        }

        val head = if (tagActual > 0L) {
            "${cap(intent.period.label)} — Tag ${resolvedTag.name}: ${fmt(tagActual)} thực tế."
        } else {
            "${cap(intent.period.label)}: chưa ghi nhận thực tế mang tag ${resolvedTag.name}."
        }
        val cleanNotes = notes.filterNot { it.startsWith("Đã ghi nhận ") }
        return QueryAnswer(
            headline = head,
            lines = lines.ifEmpty { listOf("Chưa có phiên thực tế hay dự kiến nào cho Tag ${resolvedTag.name}.") },
            figures = primary,
            compareFigures = compare,
            notes = cleanNotes
        )
    }

    private fun typicalWindowAnswer(
        intent: QueryIntent,
        primary: PeriodFigures,
        records: List<LedgerRecord>,
        resolvedTag: Tag?,
        zoneId: ZoneId,
        nowEpochMs: Long,
        notes: List<String>
    ): QueryAnswer {
        val filterCat = intent.category ?: resolvedTag?.category
        val subjectLabel = when {
            resolvedTag != null -> "Tag ${resolvedTag.name}"
            filterCat != null -> filterCat.displayName
            else -> "các hoạt động"
        }
        val startMs = intent.period.start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = min(nowEpochMs, intent.period.end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli())

        val hourSeconds = LongArray(24)
        var sessionCount = 0
        var totalTimedSeconds = 0L

        for (r in records) {
            if (r.isCancelled || r.isNeedsReconciliation) continue
            if (r.layer != TimeLayer.MEASURED && r.layer != TimeLayer.REPORTED_TIMED) continue
            if (resolvedTag != null) {
                if (r.tagId != resolvedTag.id) continue
            } else if (filterCat != null) {
                if (r.category != filterCat) continue
            }
            val rs = r.startedAt ?: continue
            val re = r.endedAt ?: continue
            val cs = max(rs, startMs)
            val ce = min(re, endMs)
            if (ce <= cs) continue
            sessionCount++
            totalTimedSeconds += (ce - cs) / 1000L

            var cursor = cs
            while (cursor < ce) {
                val z = Instant.ofEpochMilli(cursor).atZone(zoneId)
                val nextHourMs = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val sliceEnd = min(ce, nextHourMs)
                val sec = ((sliceEnd - cursor) / 1000L).coerceAtLeast(0L)
                hourSeconds[z.hour] += sec
                cursor = sliceEnd
            }
        }

        if (sessionCount == 0 || totalTimedSeconds <= 0L) {
            return QueryAnswer(
                headline = "${cap(intent.period.label)}: chưa đủ phiên có mốc giờ cho $subjectLabel.",
                lines = listOf("Hãy ghi nhận hoặc bấm giờ vài phiên để Wally nhận diện khung giờ thường gặp."),
                figures = primary,
                notes = notes
            )
        }

        var bestHour = 0
        var bestTwoHourSec = -1L
        for (h in 0..22) {
            val pairSec = hourSeconds[h] + hourSeconds[h + 1]
            if (pairSec > bestTwoHourSec) {
                bestTwoHourSec = pairSec
                bestHour = h
            }
        }
        val windowLabel = "${"%02d:00".format(bestHour)}–${"%02d:00".format(bestHour + 2)}"
        val bandTotals = listOf(
            "Buổi sáng (06:00–12:00)" to (6 until 12).sumOf { hourSeconds[it] },
            "Buổi chiều (12:00–18:00)" to (12 until 18).sumOf { hourSeconds[it] },
            "Buổi tối (18:00–22:00)" to (18 until 22).sumOf { hourSeconds[it] },
            "Đêm/sớm (22:00–06:00)" to ((22..23).sumOf { hourSeconds[it] } + (0 until 6).sumOf { hourSeconds[it] })
        ).filter { it.second > 0L }.sortedByDescending { it.second }

        val lines = mutableListOf(
            "Tổng $subjectLabel có mốc giờ: ${fmt(totalTimedSeconds)} ($sessionCount phiên trong ${intent.period.label})."
        )
        bandTotals.take(3).forEach { (band, secs) ->
            lines += "$band: ${fmt(secs)}"
        }
        return QueryAnswer(
            headline = "Khung giờ thường $subjectLabel: $windowLabel.",
            lines = lines,
            figures = primary,
            notes = notes
        )
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
    val isTransactionStatus: Boolean = false,
    /** v2.2.3: token hoàn tác khi vừa duyệt gắn/đổi/bỏ Tag cho một phiên cũ ngay trong Chat. */
    val undoToken: TagAssignmentUndoToken? = null
) {
    companion object {
        fun fromQuery(question: String, answer: QueryAnswer) =
            ChatAnswer(question, answer.headline, answer.lines, answer.notes)

        fun clarification(question: String, message: String, suggestions: List<String>) =
            ChatAnswer(question, message, isClarification = true, suggestions = suggestions)
    }
}

/**
 * Một mục hội thoại đã hoàn tất trong lịch sử trò chuyện với Wally (chỉ hiển thị & lưu cục bộ ngoài sổ cái, không tự ghi bản ghi).
 */
data class ChatHistoryEntry(
    val id: String,
    val isUser: Boolean,
    val headline: String,
    val lines: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val timeText: String = "",
    val isClarification: Boolean = false,
    val label: String? = null
)
