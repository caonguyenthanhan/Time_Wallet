package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * WALLY API-FIRST — guardrail tất định cho đầu ra của Cloud AI.
 * Quy tắc: AI là nơi suy luận; code cục bộ chỉ KIỂM TRA (schema, ngày, thời lượng, ý định bị cấm) và TÍNH SỐ từ sổ cái.
 * Không có hàm nào ở đây tạo câu trả lời thay AI, và không hàm nào ghi DB.
 */

/** Truy vấn số liệu do AI yêu cầu, được kiểm tra rồi mới đổi thành [QueryIntent] để LedgerQueryEngine tính. */
object AiQueryValidator {
    const val MAX_QUERIES = 3
    const val MAX_SPAN_DAYS = 93L

    sealed class Result {
        data class Ok(val intent: QueryIntent) : Result()
        data class Rejected(val reason: String) : Result()
    }

    private fun parseDate(raw: String?): LocalDate? = try {
        raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    fun label(start: LocalDate, end: LocalDate, today: LocalDate): String = when {
        start == end && start == today -> "hôm nay"
        start == end && start == today.minusDays(1) -> "hôm qua"
        start == end -> "ngày ${QueryPeriod.fmt(start)}"
        else -> "${QueryPeriod.fmt(start)}–${QueryPeriod.fmt(end)}"
    }

    private fun period(start: LocalDate, end: LocalDate, today: LocalDate) = QueryPeriod(
        start, end, label(start, end, today),
        if (start == end) QueryPeriodKind.DATE else QueryPeriodKind.ROLLING
    )

    fun validate(
        metric: String?,
        startDate: String?,
        endDate: String?,
        category: String?,
        compareStart: String?,
        compareEnd: String?,
        today: LocalDate,
        tag: String? = null,
        tags: List<Tag> = emptyList(),
        userText: String = ""
    ): Result {
        val m = QueryMetric.entries.firstOrNull { it.name.equals(metric?.trim(), ignoreCase = true) }
            ?: return Result.Rejected("Loại số liệu không hợp lệ.")
        val s = parseDate(startDate) ?: return Result.Rejected("Ngày bắt đầu không hợp lệ.")
        val e = parseDate(endDate) ?: return Result.Rejected("Ngày kết thúc không hợp lệ.")
        if (e.isBefore(s)) return Result.Rejected("Khoảng ngày bị ngược.")
        if (ChronoUnit.DAYS.between(s, e) + 1 > MAX_SPAN_DAYS) return Result.Rejected("Khoảng ngày quá dài (tối đa $MAX_SPAN_DAYS ngày).")

        val cat = category?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.let {
            TimeCategory.fromKeyOrNull(it) ?: return Result.Rejected("Danh mục không hợp lệ.")
        }

        val activeTags = tags.filter { !it.isArchived }
        val cleanTagRaw = tag?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        val explicitByName = cleanTagRaw?.let { name ->
            val key = TagRules.nameKey(name)
            if (key.isEmpty()) null else activeTags.firstOrNull { it.nameKey == key }
        }
        val inferredFromQuestion = if (explicitByName == null && cleanTagRaw == null && userText.isNotBlank() && activeTags.isNotEmpty()) {
            val matches = TagRules.findExplicitMatchesInText(userText, activeTags)
            if (matches.size == 1) matches.first() else null
        } else null
        val resolvedTag = explicitByName ?: inferredFromQuestion

        val cs = parseDate(compareStart)
        val ce = parseDate(compareEnd)
        val compare = if (cs != null && ce != null) {
            if (ce.isBefore(cs)) return Result.Rejected("Khoảng so sánh bị ngược.")
            if (ChronoUnit.DAYS.between(cs, ce) + 1 > MAX_SPAN_DAYS) return Result.Rejected("Khoảng so sánh quá dài.")
            period(cs, ce, today)
        } else if (cs != null || ce != null) {
            return Result.Rejected("Khoảng so sánh thiếu một đầu.")
        } else null

        return Result.Ok(
            QueryIntent(
                metric = m,
                period = period(s, e, today),
                category = cat ?: resolvedTag?.category,
                compareTo = compare,
                tagId = resolvedTag?.id,
                tagName = resolvedTag?.name ?: cleanTagRaw
            )
        )
    }
}

/**
 * Lọc đề xuất của AI bằng các chính sách bắt buộc. Trả về đề xuất còn lại + cảnh báo giải thích vì sao bị bỏ.
 * - Lệnh hủy / câu sửa lại thời lượng: không đề xuất nào được sống sót (hỏi lại).
 * - Mệnh đề phủ định ("không học 2h"): đề xuất tương ứng bị bỏ; nếu không còn mệnh đề hợp lệ nào thì bỏ hết.
 * - Thời lượng AI đưa ra phải khớp thời lượng người dùng nêu (khi câu có thể đọc chắc chắn).
 *   v1.0.13 (C2): câu gõ KHÔNG dấu được khôi phục dấu trước khi đọc (xem VietnameseChatParser.restoreUnaccented) và "1h rưỡi" = 90 phút,
 *   nên đề xuất đúng không bị bỏ nhầm và đề xuất sai thời lượng vẫn bị bắt; câu đã có dấu được xử lý đúng như trước.
 */
object AiProposalGuard {

    data class Result(val proposals: List<ActivityProposal>, val warnings: List<String>)

    /**
     * @param today/nowTime ngày và giờ hiện tại do ứng dụng cung cấp (null = không đối chiếu "đến giờ").
     */
    fun apply(
        userText: String,
        proposals: List<ActivityProposal>,
        today: LocalDate? = null,
        nowTime: LocalTime? = null
    ): Result {
        if (proposals.isEmpty()) return Result(proposals, emptyList())
        val text = userText.trim()

        if (CancelCommandParser.parse(text) != null) {
            return Result(emptyList(), listOf("Đây là lệnh hủy đề xuất, không phải hoạt động mới nên không tạo bản ghi."))
        }
        if (VietnameseChatParser.isCorrectionWithDuration(text)) {
            return Result(
                emptyList(),
                listOf("Câu có vẻ đang sửa lại thời lượng hoặc hoạt động trước đó nên chưa tạo bản ghi nào. Hãy nhập lại một câu rõ ràng, ví dụ \"Học 3h\".")
            )
        }
        VietnameseChatParser.unresolvableNegationReason(text)?.let { reason ->
            return Result(emptyList(), listOf(reason))
        }
        VietnameseChatParser.findAmbiguousCrossDayClock(text)?.let { ambiguous ->
            return Result(
                emptyList(),
                listOf("Mốc giờ \"$ambiguous\" trong khoảng xuyên ngày chưa rõ sáng hay tối nên Wally chưa tự suy đoán. Hãy nói rõ buổi (ví dụ \"20h tối\" hoặc \"8h sáng\").")
            )
        }

        val warnings = mutableListOf<String>()
        var kept = proposals

        val crossDayMatches = text.split(',', ';', '\n').mapNotNull { VietnameseChatParser.extractCrossDayRange(it) }
        if (crossDayMatches.any { it.exceeds24Hours }) {
            return Result(
                emptyList(),
                listOf("Khoảng thời gian xuyên ngày vượt quá 24 giờ nên không thể ghi trong một hoạt động thủ công hoặc chat.")
            )
        }

        val clauses = VietnameseChatParser.durationClauses(text)
        if (clauses.any { it.negated }) {
            val validLocal = VietnameseChatParser.parse(text).proposals
            if (validLocal.isEmpty()) {
                return Result(emptyList(), listOf("Câu có từ phủ định / yêu cầu không ghi nên không tạo bản ghi nào."))
            }
            val screened = screenNegation(clauses, proposals)
            kept = screened.kept
            warnings += screened.warnings
        }

        // v1.0.13 (C2): câu gõ không dấu ("nghi luc 3h chieu 2 tieng") được khôi phục dấu trước khi đọc số liệu; câu có dấu không đổi.
        val figText = VietnameseChatParser.restoreUnaccented(text)
        if (!VietnameseChatParser.hasUnnormalizedWordDuration(figText)) {
            val stated = VietnameseChatParser.allDurationMinutes(figText)
            if (stated.isNotEmpty()) {
                val allowed = stated.toSet() + stated.sum()
                val before = kept.size
                val openStarts = openEndedStartCandidates(text)
                val clockPoints = explicitClockCandidates(figText)
                kept = kept.filter {
                    it.durationMinutes in allowed ||
                        isOpenEndedRange(it, openStarts, today) ||
                        isExplicitClockEndpointRange(it, clockPoints)
                }
                if (kept.size < before) {
                    warnings += "Đã bỏ ${before - kept.size} đề xuất của AI có thời lượng không khớp với câu bạn nhập."
                }
            }
        }
        kept = reconcileOpenEnded(text, kept, today, nowTime, warnings)
        kept = reconcileCrossDayAnchors(crossDayMatches, kept, today, warnings)
        return Result(kept, warnings)
    }

    private fun reconcileCrossDayAnchors(
        matches: List<VietnameseChatParser.CrossDayRangeMatch>,
        proposals: List<ActivityProposal>,
        today: LocalDate?,
        warnings: MutableList<String>
    ): List<ActivityProposal> {
        if (today == null || matches.isEmpty()) return proposals
        val yest = matches.firstOrNull { !it.exceeds24Hours && it.startDayOffset < 0 } ?: return proposals
        val expectedDate = today.plusDays(yest.startDayOffset.toLong()).toString()
        var shifted = false
        val out = proposals.map { p ->
            if (p.timeEvidence == "explicit_range" &&
                p.startLocal == yest.range.startLocal &&
                p.endLocal == yest.range.endLocal &&
                p.dateLocal == today.toString()
            ) {
                shifted = true
                p.copy(dateLocal = expectedDate, isFuturePlanned = false)
            } else {
                p
            }
        }
        if (shifted) {
            warnings += "Đã neo ngày bắt đầu về $expectedDate cho hoạt động xuyên qua nửa đêm."
        }
        return out
    }

    private val OPEN_END_PHRASE = Rx.uni("(?:đến|tới)\\s+(?:bây\\s+giờ|giờ|nay|lúc\\s+này)")
    private val OPEN_END_FILLER = setOf("rồi", "này", "nè", "luôn", "đấy", "hiện", "tại")
    private val CLAUSE_CLOCK = Rx.ascii("(\\d{1,2})\\s*(?:h|:)\\s*(\\d{0,2})")
    private val PUNCT = setOf(',', ';', '.', '!', '?', '…')

    private val OPEN_END_TRAILING_CLOCK = Rx.uni(
        """(?:^|\s)\d{1,2}\s*(?:h|:)\s*\d{0,2}(?:\s*(?:giờ\s*)?(?:sáng|trưa|chiều|tối|đêm|khuya|am|pm))?(?:\s*(?:hôm\s+qua|đêm\s+qua|hôm\s+nay))?\s*$"""
    )
    private val OPEN_END_PERIOD_NOUNS = setOf("trưa", "tối", "chiều", "sáng", "mai", "nay")

    /**
     * Vị trí kết thúc (chỉ số trong [t]) của các cụm "đến giờ / đến bây giờ / đến nay / tới giờ".
     * Hỗ trợ cả dạng đứng cuối mệnh đề ("Code từ 14h đến giờ") lẫn dạng theo sau bởi tên hoạt động
     * ngay sau mốc giờ ("21h đến giờ review code"), nhưng không nhận nhầm cụm phi thời lượng ("đến giờ làm việc").
     */
    private fun openEndedPhraseMatches(t: String): List<MatchResult> = OPEN_END_PHRASE.findAll(t).filter { m ->
        val rest = t.substring(m.range.last + 1).trimStart()
        val words = rest.split(Rx.uni("\\s+")).filter { it.isNotEmpty() }
        if (rest.isEmpty() || rest.first() in PUNCT || words.all { w -> OPEN_END_FILLER.contains(w.trimEnd(',', ';', '.', '!', '?', '…')) }) {
            return@filter true
        }
        val phrase = m.value.lowercase(Locale("vi", "VN"))
        if (!phrase.contains("giờ") && !phrase.contains("lúc")) return@filter false
        val before = t.substring(0, m.range.first)
        val clauseBefore = before.substring(before.lastIndexOfAny(charArrayOf(',', ';', '.', '\n', '!', '?')) + 1)
        if (!OPEN_END_TRAILING_CLOCK.containsMatchIn(clauseBefore)) return@filter false
        val clauseRest = rest.substringBefore(',').substringBefore(';').substringBefore('.').trim()
        val firstWord = clauseRest.split(Rx.uni("\\s+")).firstOrNull()?.lowercase(Locale("vi", "VN")).orEmpty()
        clauseRest.none { it.isDigit() } && firstWord !in OPEN_END_PERIOD_NOUNS
    }.toList()

    internal fun hasOpenEndedToNow(text: String): Boolean =
        openEndedPhraseMatches(VietnameseChatParser.restoreUnaccented(text.lowercase(Locale("vi")))).isNotEmpty()

    /**
     * Giờ bắt đầu (phút trong ngày) có thể có của mệnh đề chứa "đến giờ": số giờ viết ngay trước cụm đó ("14h", "14h30", "14:30", "2h chiều").
     * Chỉ trả giá trị khi đọc được chắc chắn; không có từ chỉ buổi thì chấp nhận cả giờ viết và giờ viết + 12 (vì "2h" có thể là 2h hoặc 14h).
     */
    internal fun openEndedStartCandidates(text: String): Set<Int> {
        val t = VietnameseChatParser.restoreUnaccented(text.lowercase(Locale("vi")))
        val out = mutableSetOf<Int>()
        for (m in openEndedPhraseMatches(t)) {
            val before = t.substring(0, m.range.first)
            val clauseStart = (before.lastIndexOfAny(charArrayOf(',', ';', '.', '\n', '!', '?')) + 1)
            val clause = before.substring(clauseStart)
            val clock = CLAUSE_CLOCK.findAll(clause).lastOrNull() ?: continue
            val h = clock.groupValues[1].toIntOrNull() ?: continue
            val min = clock.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: continue
            if (h !in 0..23 || min !in 0..59) continue
            val tail = clause.substring(clock.range.last + 1)
            val period = listOf("sáng", "trưa", "chiều", "tối", "đêm").firstOrNull { tail.contains(it) }
            when {
                h > 12 -> out += h * 60 + min
                period == "chiều" || period == "tối" -> out += (if (h < 12) h + 12 else h) * 60 + min
                period == "sáng" || period == "đêm" -> out += h * 60 + min
                period == "trưa" -> out += (if (h < 11) h + 12 else h) * 60 + min
                else -> { out += h * 60 + min; if (h in 1..11) out += (h + 12) * 60 + min }
            }
        }
        return out
    }

    private fun parseHm(s: String?): Int? = try {
        s?.let { LocalTime.parse(it) }?.let { it.hour * 60 + it.minute }
    } catch (_: Exception) {
        null
    }

    /**
     * Mọi mốc giờ tường minh xuất hiện trong câu (phút trong ngày 0..1439) để kiểm chứng các đề xuất ghép nối mốc giờ
     * từ hai đầu giờ đã nêu (ví dụ "... đến 8h sáng hôm nay, vệ sinh và ăn sáng đến 9h ...").
     */
    internal fun explicitClockCandidates(text: String): Set<Int> {
        val out = mutableSetOf<Int>()
        val t = text.lowercase(Locale("vi", "VN"))
        val clockWithPeriod = Rx.uni(
            """(\d{1,2})\s*(?:h|:)\s*(\d{0,2})(?:\s*(?:giờ\s*)?(sáng|trưa|chiều|tối|đêm|khuya|am|pm))?""",
            RegexOption.IGNORE_CASE
        )
        for (m in clockWithPeriod.findAll(t)) {
            val idx = m.range.first
            val next = t.substring(m.range.last + 1).trimStart()
            // Bỏ qua "9 giờ" đơn lẻ là thời lượng ("9 tiếng", "2 giờ" không có giờ:) khi đi với "tiếng/phút/p"
            val afterNum = m.value.lowercase(Locale("vi", "VN"))
            if (!afterNum.contains(':') && !afterNum.contains('h')) continue
            if (next.startsWith("tiếng") || next.startsWith("phút")) continue
            val h = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: continue
            if (h !in 0..23 || min !in 0..59) continue
            val period = m.groupValues[3].ifBlank { null }
            when (period) {
                "chiều", "tối", "pm" -> out += (if (h in 1..11) h + 12 else h) * 60 + min
                "trưa" -> out += (if (h in 1..4) h + 12 else h) * 60 + min
                "sáng", "đêm", "khuya", "am" -> out += (if (h == 12) 0 else h) * 60 + min
                else -> {
                    out += h * 60 + min
                    if (h in 1..11 && idx >= 0) out += (h + 12) * 60 + min
                }
            }
        }
        return out
    }

    private fun isExplicitClockEndpointRange(p: ActivityProposal, clocks: Set<Int>): Boolean {
        if (clocks.size < 2 || p.timeEvidence != "explicit_range") return false
        val start = parseHm(p.startLocal) ?: return false
        val end = parseHm(p.endLocal) ?: return false
        return start in clocks && end in clocks
    }

    internal fun hasYesterdayBeforeOpenEnded(text: String): Boolean {
        val t = VietnameseChatParser.restoreUnaccented(text.lowercase(Locale("vi")))
        return openEndedPhraseMatches(t).any { m ->
            val before = t.substring(0, m.range.first)
            val clauseStart = (before.lastIndexOfAny(charArrayOf(',', ';', '.', '\n', '!', '?')) + 1)
            val clause = before.substring(clauseStart)
            clause.contains("hôm qua") || clause.contains("đêm qua")
        }
    }

    /** Đề xuất khoảng giờ của hôm nay (hoặc bắt đầu từ hôm qua khi có "hôm qua/đêm qua") có giờ bắt đầu khớp mệnh đề "đến giờ". */
    private fun isOpenEndedRange(p: ActivityProposal, starts: Set<Int>, today: LocalDate?, allowYesterday: Boolean = false): Boolean {
        val expectedDates: Set<String>? = today?.let { d ->
            buildSet {
                add(d.toString())
                if (allowYesterday) add(d.minusDays(1).toString())
            }
        }
        return starts.isNotEmpty() && p.timeEvidence == "explicit_range" &&
            (expectedDates == null || p.dateLocal in expectedDates) &&
            parseHm(p.startLocal)?.let { it in starts } == true
    }

    /**
     * v1.0.12 + v1.2.0 (UC-15): "Code từ lúc 14h đến giờ" / "Ngủ từ 22h tối hôm qua đến giờ" — giờ kết thúc là giờ hiện tại do ứng dụng cung cấp.
     */
    private fun reconcileOpenEnded(
        text: String,
        proposals: List<ActivityProposal>,
        today: LocalDate?,
        nowTime: LocalTime?,
        warnings: MutableList<String>
    ): List<ActivityProposal> {
        if (today == null || nowTime == null) return proposals
        val starts = openEndedStartCandidates(text)
        if (starts.isEmpty()) return proposals
        val allowYesterday = hasYesterdayBeforeOpenEnded(text)
        val yesterdayStr = today.minusDays(1).toString()
        val nowMin = nowTime.hour * 60 + nowTime.minute
        val nowHm = "%02d:%02d".format(nowTime.hour, nowTime.minute)
        return proposals.map { p ->
            if (!isOpenEndedRange(p, starts, today, allowYesterday)) return@map p
            val end = parseHm(p.endLocal) ?: return@map p
            val startMin = parseHm(p.startLocal) ?: return@map p
            if (allowYesterday && (p.dateLocal == yesterdayStr || (p.dateLocal == today.toString() && startMin > nowMin))) {
                val expectedMins = (1440 - startMin) + nowMin
                if (expectedMins in 1..1440) {
                    if (p.dateLocal == yesterdayStr && kotlin.math.abs(end - nowMin) <= 2 && p.durationMinutes == expectedMins) return@map p
                    warnings += "Giờ kết thúc được đặt theo \"đến giờ\" = $nowHm cho khoảng xuyên đêm từ hôm qua."
                    return@map p.copy(
                        dateLocal = yesterdayStr,
                        endLocal = nowHm,
                        durationSeconds = expectedMins * 60L,
                        isFuturePlanned = false
                    )
                }
                return@map p
            }
            if (kotlin.math.abs(end - nowMin) <= 2) return@map p
            if (startMin >= nowMin) {
                warnings += "Câu có \"đến giờ\" (bây giờ là $nowHm) nhưng mốc bắt đầu ${p.startLocal} chưa tới. Hãy kiểm tra kỹ mốc giờ trước khi xác nhận."
                return@map p
            }
            warnings += "Giờ kết thúc được đặt theo \"đến giờ\" = $nowHm (AI đề xuất ${p.endLocal})."
            p.copy(endLocal = nowHm, durationSeconds = (nowMin - startMin) * 60L, isFuturePlanned = false)
        }
    }

    private class Screened(val kept: List<ActivityProposal>, val warnings: List<String>)

    /**
     * F-V105-03: KHÔNG dựa vào việc danh mục AI == danh mục mệnh đề phủ định (AI có thể gắn nhãn khác, hoặc hai mệnh đề
     * khác nhau cùng danh mục). Với mỗi nhóm thời lượng có mệnh đề phủ định:
     *  - đề xuất giống mệnh đề phủ định hơn mệnh đề khẳng định (theo từ khóa trong câu) => bỏ;
     *  - đề xuất giống mệnh đề khẳng định hơn => giữ (tối đa số mệnh đề khẳng định cùng thời lượng);
     *  - không đủ bằng chứng phân biệt => KHÔNG đoán: bỏ và yêu cầu người dùng nhập rõ hơn.
     */
    private fun screenNegation(clauses: List<VietnameseChatParser.DurationClause>, proposals: List<ActivityProposal>): Screened {
        val negatedMinutes = clauses.filter { it.negated }.map { it.minutes }.toSet()
        val kept = mutableListOf<ActivityProposal>()
        var droppedNegated = 0
        var droppedAmbiguous = 0
        for ((minutes, group) in proposals.groupBy { it.durationMinutes }) {
            if (minutes !in negatedMinutes) { kept += group; continue }
            val neg = clauses.filter { it.negated && it.minutes == minutes }
            val aff = clauses.filter { !it.negated && it.minutes == minutes }
            var affKept = 0
            for (p in group) {
                when (verdict(p, neg, aff)) {
                    Verdict.AFFIRMATIVE -> if (affKept < aff.size) { kept += p; affKept++ } else droppedAmbiguous++
                    Verdict.NEGATED -> droppedNegated++
                    Verdict.UNKNOWN -> droppedAmbiguous++
                }
            }
        }
        val warnings = buildList {
            if (droppedNegated > 0) add("Đã bỏ đề xuất ứng với mệnh đề phủ định (không ghi).")
            if (droppedAmbiguous > 0) add("Chưa đủ căn cứ để biết $droppedAmbiguous đề xuất ứng với việc bạn muốn ghi hay không ghi nên chưa tạo bản ghi. Hãy nhập lại từng hoạt động rõ hơn.")
        }
        // giữ nguyên thứ tự của AI
        return Screened(proposals.filter { it in kept }, warnings)
    }

    private enum class Verdict { AFFIRMATIVE, NEGATED, UNKNOWN }

    private val stopTokens = setOf(
        "tôi", "mình", "hôm", "nay", "qua", "kia", "không", "chưa", "chẳng", "đừng", "chớ", "đã", "vừa", "mới", "và", "rồi",
        "nhưng", "là", "có", "được", "ghi", "nhận", "trong", "khoảng", "buổi", "sáng", "trưa", "chiều", "tối", "đêm", "từ", "đến",
        "giờ", "tiếng", "phút", "hơn", "ngày", "mai", "sẽ", "bị"
    )

    private fun tokens(s: String): Set<String> =
        Rx.ascii("""\p{L}+""").findAll(s.lowercase(Locale.ROOT)).map { it.value }.filter { it.length >= 2 && it !in stopTokens }.toSet()

    private fun overlap(p: ActivityProposal, clauses: List<VietnameseChatParser.DurationClause>): Int {
        val pt = tokens(p.title)
        return clauses.maxOfOrNull { c -> tokens(c.text).count { it in pt } } ?: 0
    }

    private fun verdict(
        p: ActivityProposal,
        neg: List<VietnameseChatParser.DurationClause>,
        aff: List<VietnameseChatParser.DurationClause>
    ): Verdict {
        val n = overlap(p, neg)
        val a = overlap(p, aff)
        if (n > a) return Verdict.NEGATED
        if (a > n) return Verdict.AFFIRMATIVE
        // Hòa theo từ khóa: danh mục chỉ là tín hiệu phụ, và chỉ dùng khi nó phân biệt được hai phía.
        val catNeg = neg.any { VietnameseChatParser.classifyCategory(it.text) == p.category }
        val catAff = aff.any { VietnameseChatParser.classifyCategory(it.text) == p.category }
        return when {
            catAff && !catNeg -> Verdict.AFFIRMATIVE
            catNeg && !catAff -> Verdict.NEGATED
            else -> Verdict.UNKNOWN
        }
    }
}

/**
 * Lời đáp tự do của AI (WALLY v1.0.6, F-V105-08 / R03; v1.0.13 G1). Lời đáp chỉ để diễn giải, KHÔNG phải nguồn sự thật:
 *  1. AI không được khẳng định đã ghi/lưu/sửa/xóa/hủy (chỉ ứng dụng mới báo thành công, sau khi commit) và không được
 *     bảo "không cần xác nhận". Áp dụng cho MỌI câu, kể cả câu hỏi: "Mình đã ghi xong rồi nhé?" vẫn bị chặn.
 *  2. Số liệu cá nhân (thời gian bằng chữ số HOẶC bằng chữ, phần trăm, số lần/buổi/bản ghi) chỉ đến từ LedgerQueryEngine.
 *     Hai ngoại lệ:
 *      (a) thời lượng/giờ của chính đề xuất đang chờ xác nhận (hiển thị lại cho khớp thẻ đề xuất);
 *      (b) v1.0.13: trong một CÂU HỎI (kết thúc bằng "?" hoặc có từ để hỏi: mấy, bao nhiêu, lúc nào, hay, đúng không, phải không...)
 *          AI được nhắc lại số/thời lượng/giờ đồng hồ mà CHÍNH NGƯỜI DÙNG đã viết trong câu của họ (chữ số hoặc số bằng chữ,
 *          có dấu hoặc không dấu, so theo số phút) để hỏi lại cho rõ: "Bạn muốn ghi 2 giờ hay 3 giờ?" khi người dùng nói "2h, à không, 3h".
 *          Câu KHẲNG ĐỊNH vẫn không có ngoại lệ này ("Hôm nay bạn học 3 tiếng" bị bỏ dù người dùng có nói 3 tiếng; số trong câu hỏi
 *          của người dùng không phải căn cứ), và số AI tự nghĩ ra không "rửa" được bằng cách đặt thành câu hỏi ("Hôm nay bạn đã dùng
 *          5 giờ phải không?" khi người dùng không nói 5). Phần trăm, số lần/buổi, số tiếng Anh: không bao giờ có ngoại lệ.
 *          v1.0.13 (G2), cũng CHỈ trong câu hỏi, "số của người dùng" gồm thêm: (i) GIỜ HIỆN TẠI mà ứng dụng đã đưa cho AI ("16:30", "16h30", "4h30 chiều",
 *          "4 rưỡi chiều", "bây giờ mới 4 rưỡi"; phải đúng bằng giờ đó, xem [stripNowClock]); (ii) hai số "hay/hoặc" chung một đơn vị ("30 hay 40 phút");
 *          (iii) số suy ra CHÍNH XÁC từ mốc giờ người dùng đã viết: độ dài và hai đầu (24h) của "từ 8h đến 9h", giờ có buổi ("7h tối" = 19:00).
 *  3. "xong" / "hoàn thành" / "hoàn tất" đứng một mình KHÔNG phải động từ ghi sổ. Ở mệnh đề có từ để hỏi chúng là câu hỏi về việc của
 *     người dùng ("Bạn đã làm xong lúc mấy giờ?", "Bạn đã hoàn thành chưa?") nên không bị coi là báo thành công; ở câu khẳng định
 *     ("Xong xuôi rồi nhé.", "Đã hoàn thành.") vẫn bị chặn như trước. Động từ ghi sổ (ghi, lưu, thêm, xóa, sửa, cập nhật, hủy...) luôn bị chặn.
 *  4. Lời đáp nhiều câu: mỗi câu được xét riêng và CHỈ CẦN MỘT câu vi phạm là bỏ CẢ lời đáp (không cắt bớt: câu hỏi còn lại có thể dựa trên
 *     một tiền đề do AI bịa).
 */
object ReplyGuard {
    private const val MAX_LENGTH = 400
    private const val UNIT_TIME = "giờ|tiếng|phút|giây|h|p|mins?|min|hrs?|hours?|minutes?"
    private const val UNIT_COUNT = "lần|buổi|hoạt động|bản ghi|phiên|mục"
    private const val B = """(?<![\p{L}\p{N}])"""   // đầu từ (không phụ thuộc dấu câu hay khoảng trắng đứng trước)
    private val figure = Rx.ascii("""(\d+(?:[.,]\d+)?)\s*(giờ|tiếng|phút|h|p|mins?|min|hrs?|hours?|minutes?)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val otherDigitFigure = Rx.uni("""\d+(?:[.,]\d+)?\s*(?:$UNIT_TIME|$UNIT_COUNT)(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val countFigure = Rx.uni("""\d+(?:[.,]\d+)?\s*(?:$UNIT_COUNT)""", RegexOption.IGNORE_CASE)
    private val digitsOnly = Rx.ascii("""\d+""")
    private val percent = Rx.uni("""%|phần\s*trăm|percent""", RegexOption.IGNORE_CASE)

    // "2h30", "1g30", "2 giờ 30 (phút)": tổng phút = giờ*60 + phút. "14:30" / "2:30": giờ đồng hồ hoặc thời lượng.
    private val hourMinute = Rx.ascii("""$B(\d{1,2})\s*(?:h|g|giờ|tiếng)\s*(\d{1,2})(?:\s*(?:p|phút))?(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val clock = Rx.ascii("""$B(\d{1,2}):(\d{2})(?![\p{L}\p{N}])""")

    private const val NUMBER_WORD = "một|mốt|hai|ba|bốn|tư|năm|lăm|sáu|bảy|tám|chín|mười|mươi|trăm|rưỡi|nửa|vài|lẻ|linh|chục"
    private const val EN_NUMBER = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty|forty-five|sixty|half"
    private val wordFigure = Rx.uni(
        """$B(?:$NUMBER_WORD)(?:\s+(?:$NUMBER_WORD))*\s+(?:$UNIT_TIME|lần|buổi|bản ghi|phiên)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )
    private val enWordFigure = Rx.ascii("""$B(?:$EN_NUMBER)(?:\s+and\s+a\s+half)?\s+(?:hours?|minutes?|mins?|hrs?)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val halfWord = Rx.uni("""$B(?:rưỡi|nửa\s+(?:giờ|tiếng))(?!\p{L})""", RegexOption.IGNORE_CASE)

    // Khẳng định đã thay đổi sổ cái / bỏ qua bước xác nhận. Cho phép tối đa 3 từ chen giữa ("đã giúp bạn lưu", "đã được Wally ghi lại").
    // v1.0.13: động từ GHI SỔ tách khỏi động từ HOÀN TẤT ("hoàn tất/hoàn thành/xử lý xong" không phải thao tác lên sổ cái).
    private val ledgerVerbs =
        "ghi\\s*nhận|ghi\\s*lại|ghi\\s*chép|ghi|lưu\\s*trữ|lưu\\s*giữ|lưu\\s*lại|lưu|thêm|xóa|xoá|sửa|chỉnh\\s*sửa|chỉnh|cập\\s*nhật|hủy|huỷ|tiếp\\s*nhận|chốt|nạp|đồng\\s*bộ|cất|chép|note|nhập\\s*liệu"
    private val completionVerbs = "hoàn\\s*tất|hoàn\\s*thành|xử\\s*lý\\s+xong|đã\\s+xử\\s+lý"
    private val completionOnly =
        "xong\\s+xuôi|xong\\s+hết|xong\\s+cả|tất\\s+cả\\s+(?:đã\\s+)?xong|mọi\\s+thứ\\s+(?:đã\\s+)?xong|xong\\s+rồi|hoàn\\s*tất\\s+rồi|hoàn\\s*thành\\s+rồi|đã\\s+xong"
    private val destinations =
        "(?:sổ(?:\\s*cái)?|nhật\\s*ký|danh\\s*sách|lịch\\s*sử|báo\\s*cáo|thống\\s*kê|dữ\\s*liệu|hệ\\s*thống|bộ\\s*nhớ|ví\\s+thời\\s+gian)"
    private val safeNegatedMarker = "(?<!chưa\\s)(?<!không\\s)(?<!chẳng\\s)"

    /**
     * Các mẫu có thể trúng chỉ vì "xong/hoàn thành/hoàn tất" (mẫu 1, 3, 5, 8 cũ). [verbs] là tập động từ, [completion] là các cụm hoàn tất đứng một mình
     * (rỗng = bỏ). Bản đầy đủ dùng cho câu khẳng định; bản chỉ-động-từ-ghi-sổ dùng cho mệnh đề có từ để hỏi.
     */
    private fun completionSensitive(verbs: String, completion: String): List<Regex> {
        val co = if (completion.isEmpty()) "" else "$completion|"
        val xo = if (completion.isEmpty()) "" else "|xong(?:\\s+việc)?"
        return listOf(
            // 1. Khẳng định trong một mệnh đề có (đã/vừa/vừa mới) + động từ ghi/lưu/cập nhật/hoàn tất (cho phép xen từ/số liệu trong cùng mệnh đề, không giới hạn 3 từ).
            Rx.uni(
                """$safeNegatedMarker$B(?:đã|vừa(?:\s+mới)?)\s+(?:(?!(?:chưa|không|chẳng|để|muốn|cần|chờ|trước\s+khi)\b)[^,.;!?\n]){0,70}?(?:$verbs)(?!\p{L})""",
                RegexOption.IGNORE_CASE
            ),
            // 3. Trạng thái hoàn thành ngay sau động từ ("ghi xong", "lưu thành công", "xử lý xong").
            Rx.uni("""$B(?:$verbs)(?:\s+lại)?\s+(?:xong|rồi|thành\s*công|hoàn\s*tất|hoàn\s*thành)(?!\p{L})""", RegexOption.IGNORE_CASE),
            // 5. Cách nói gián tiếp hoàn tất hoặc giữ giúp người dùng ("xong xuôi", "tất cả xong rồi", "giữ giúp bạn rồi", "tự động hoàn tất").
            Rx.uni(
                """$B$safeNegatedMarker(?:${co}tự\s*động\s+(?:hoàn\s*tất|hoàn\s*thành|ghi|lưu|thêm|xử\s*lý|cập\s*nhật|xác\s*nhận|chốt)|(?:giữ|lưu\s*giữ|cất|chốt|giữ\s+hộ|giữ\s+lại)\s+(?:hộ|giúp|dùm|cho)\s+bạn(?:\s+\p{L}+){0,2}\s*(?:rồi|nhé|nha)?)(?!\p{L})""",
                RegexOption.IGNORE_CASE
            ),
            // 8. "Ghi nhận ... cho bạn rồi nhé", "Hoàn tất cho bạn rồi nhé!", "Xong việc ghi nhận cho bạn rồi đó" (không có đã/vừa, không có số).
            Rx.uni(
                """(?<!sẽ\s)(?<!để\s)(?<!muốn\s)(?<!cần\s)(?<!chưa\s)(?<!không\s)$B(?:$verbs$xo)\s+(?:(?!(?:sẽ|để|khi|nếu|muốn|cần|chờ)\b)[^,.;!?\n]){0,30}?(?:cho|giúp|hộ|dùm)\s+bạn\s+(?:rồi|nhé|nha|đó|xong)(?!\p{L})""",
                RegexOption.IGNORE_CASE
            )
        )
    }

    private val strictClaims = completionSensitive("$ledgerVerbs|$completionVerbs", completionOnly)
    private val askBackClaims = completionSensitive(ledgerVerbs, "")

    /** Các mẫu không phụ thuộc "xong/hoàn thành": luôn áp dụng cho cả câu, hỏi hay khẳng định. */
    private val alwaysClaims = listOf(
        // 2. Chủ ngữ là hệ thống / sổ cái đã tiếp nhận hoặc giữ giúp người dùng.
        Rx.uni(
            """$B$destinations\s+(?:[^,.;!?\n]{0,35}?)$safeNegatedMarker(?:đã|vừa(?:\s+mới)?)\s+(?:[^,.;!?\n]{0,25}?)(?:nhận|tiếp\s*nhận|ghi|lưu|thêm|cập\s*nhật|giữ)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        ),
        // 4. Khẳng định bản ghi đã/đang nằm trong sổ / nhật ký / danh sách / lịch sử / hệ thống.
        Rx.uni(
            """$safeNegatedMarker$B(?:(?:đã|vừa|đang)\s+(?:[^,.;!?\n]{0,45}?)?)?(?:nằm\s+trong|có\s+mặt\s+trong|ở\s+trong|xuất\s+hiện\s+trong|lưu\s+(?:vào|trong)|ghi\s+(?:vào|trong|lên)|thêm\s+(?:vào|lên)|đưa\s+(?:vào|lên)|cập\s+nhật\s+(?:vào|trong|lên)|đã\s+vào|đã\s+lên)\s+$destinations""",
            RegexOption.IGNORE_CASE
        ),
        // 6. Bảo người dùng không cần xác nhận / không cần làm gì thêm.
        Rx.uni(
            """(?<!không\sphải\s)$B(?:(?:không\s+cần|khỏi(?:\s+cần)?|không\s+phải|bỏ\s+qua|miễn)\s+(?:bước\s+)?(?:xác\s*nhận|bấm(?:\s+(?:gì|xác\s*nhận|nữa))?|làm\s+gì(?:\s+thêm)?|thao\s*tác(?:\s+thêm)?|chờ|kiểm\s*tra)|bạn\s+(?:không|chẳng|chưa)\s+cần\s+(?:bấm|xác\s*nhận|làm\s+gì))(?!\p{L})""",
            RegexOption.IGNORE_CASE
        ),
        // 9. "mọi việc ổn thỏa hết rồi đó", "tất cả xong hết rồi", "yên tâm nhé, ... rồi".
        Rx.uni(
            """$B(?:(?:mọi|tất\s+cả)\s+(?:việc\s+|thứ\s+|chuyện\s+)?(?:đều\s+)?(?:ổn\s*thỏa|ổn|xong|ok|hoàn\s*tất|đâu\s+vào\s+đấy)[^.!?\n]{0,15}?(?:rồi|đó|nhé|nha)|(?:yên\s+tâm|đừng\s+lo|khỏi\s+lo)[^.!?\n]{0,60}?(?:rồi|xong|ổn\s*thỏa|đâu\s+vào\s+đấy))""",
            RegexOption.IGNORE_CASE
        ),
        // 7. "sẵn sàng trong sổ", "bản ghi/hoạt động đã được tạo / đã sẵn có" (khẳng định bản ghi đã tồn tại).
        Rx.uni(
            """$B(?:sẵn\s+sàng\s+(?:trong|ở|tại)\s+$destinations|(?:bản\s*ghi|hoạt\s*động|mục)\s+(?:[^,.;!?\n]{0,25}?)(?:đã|vừa)\s+(?:được\s+tạo|sẵn\s+có|có\s+sẵn))""",
            RegexOption.IGNORE_CASE
        ),
        Rx.ascii(
            """\b(?:saved|recorded|logged|deleted|removed|updated|edited|added|stored|committed|persisted)\b|already\s+in\s+(?:your\s+)?(?:ledger|journal|list)|no\s+(?:confirmation|action)\s+(?:needed|required)""",
            RegexOption.IGNORE_CASE
        )
    )

    // Tách câu sau . ! ? khi đứng trước khoảng trắng (không tách "1.5 giờ", "14:30"); không tách ở "…" (mẫu cũ không dừng ở đó).
    private val sentenceSplit = Rx.uni("""(?<=[.!?])\s+|\n+""")
    private val clauseSplit = Rx.uni("""[,;]\s+|;""")   // dấu phẩy thập phân ("1,5 giờ") không phải ranh giới mệnh đề

    // Từ để hỏi. Cố ý không gồm các trợ từ cuối câu (nhé, nhỉ, à, hả): chúng cũng gắn được vào một câu khẳng định.
    private val askCue = Rx.uni(
        """$B(?:mấy|bao\s+nhiêu|bao\s+lâu|khi\s+nào|lúc\s+nào|nào|hay(?:\s+là)?|có\s+phải|phải\s+không|đúng\s+không|đúng\s+chứ|chưa|gì|sao|thế\s+nào|ở\s+đâu)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    // v1.0.13 (C3a): giờ hiện tại do ứng dụng cung cấp cho AI ("16:30", "16h30", "4h30 chiều", "4 rưỡi chiều", "bây giờ mới 4 rưỡi").
    private const val DAY_PERIOD = "sáng|trưa|chiều|tối|đêm"
    private const val NOW_CUE = """(?:bây\s+giờ|hiện\s+tại|hiện\s+giờ|lúc\s+này|giờ\s+là)(?:\s+(?:là|mới|đã|đang))?\s*"""
    private val nowClockRegex = Rx.uni(
        """$B($NOW_CUE)?(\d{1,2})\s*(?:h|g|giờ|:)\s*(\d{2})?(?:\s*($DAY_PERIOD))?(?![\p{L}\p{N}])(?!\s*(?:phút|p(?![\p{L}])|rưỡi))""",
        RegexOption.IGNORE_CASE
    )
    private val nowHalfRegex = Rx.uni(
        """$B($NOW_CUE)?(\d{1,2})\s*(?:(h|g|giờ)\s+)?rưỡi(?:\s*($DAY_PERIOD))?(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Bỏ khỏi câu HỎI các cách viết GIỜ HIỆN TẠI [now] (giờ đồng hồ, không phải thời lượng) để chúng không bị coi là số liệu không có căn cứ.
     * Giờ 24h ("16:30", "16h30") hoặc có buổi ("4h30 chiều") phải bằng đúng [now]; giờ 12h không buổi ("4h30") chỉ được nhận khi đứng ngay sau
     * "bây giờ / hiện tại / lúc này / giờ là"; "N rưỡi" trần là giờ đồng hồ N:30 và chỉ khớp khi [now] có phút = 30. "4 tiếng rưỡi" là thời lượng, không đụng tới.
     */
    private fun stripNowClock(s: String, now: LocalTime): String {
        val nowMin = now.hour * 60 + now.minute
        val vi = Locale("vi", "VN")
        val a = nowClockRegex.replace(s) { m ->
            val h = m.groupValues[2].toInt()
            val mm = m.groupValues[3].ifEmpty { "0" }.toInt()
            val period = m.groupValues[4].lowercase(vi).ifEmpty { null }
            val cue = m.groupValues[1].isNotEmpty()
            val ok = h <= 23 && mm <= 59 && when {
                period != null -> VietnameseChatParser.shiftHourForPeriod(h, period) * 60 + mm == nowMin
                h > 12 -> h * 60 + mm == nowMin
                else -> h * 60 + mm == nowMin || (cue && (h % 12) * 60 + mm == nowMin % 720)
            }
            if (ok) " " else m.value
        }
        return nowHalfRegex.replace(a) { m ->
            val h = m.groupValues[2].toInt()
            val hasUnit = m.groupValues[3].isNotEmpty()
            val period = m.groupValues[4].lowercase(vi).ifEmpty { null }
            val cue = m.groupValues[1].isNotEmpty()
            val ok = now.minute == 30 && h <= 23 && when {
                period != null -> VietnameseChatParser.shiftHourForPeriod(h, period) == now.hour
                h > 12 || h == 0 -> h == now.hour
                !hasUnit || cue -> h % 12 == now.hour % 12
                else -> false
            }
            if (ok) " " else m.value
        }
    }

    private fun nfc(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    private fun splitSentences(text: String): List<String> = text.split(sentenceSplit).filter { it.isNotBlank() }

    private fun endsWithQuestionMark(sentence: String): Boolean = sentence.trimEnd(' ', '"', '”', ')', '\'').endsWith('?')

    private fun isQuestion(sentence: String): Boolean = endsWithQuestionMark(sentence) || askCue.containsMatchIn(sentence)

    /** Các câu kết thúc bằng "?" trong lời đáp (đã qua [sanitize]); dùng để hiện lời hỏi lại của AI (xem WallyTurnPolicy). Từ để hỏi không đủ: "Hay quá!" không phải câu hỏi. */
    fun questionSentences(text: String): List<String> = splitSentences(nfc(text)).map { it.trim() }.filter(::endsWithQuestionMark)

    private fun minutesOf(value: String, unit: String): Int {
        val v = value.replace(',', '.').toDoubleOrNull() ?: return -1
        val u = unit.lowercase(Locale.ROOT)
        return if (u in setOf("giờ", "tiếng", "h", "hr", "hrs", "hour", "hours")) Math.round(v * 60).toInt() else Math.round(v).toInt()
    }

    private fun hmOf(hm: String?): Int? = hm?.takeIf { Rx.ascii("""\d{1,2}:\d{2}""").matches(it) }?.split(':')?.let { it[0].toInt() * 60 + it[1].toInt() }

    /**
     * True nếu lời đáp khẳng định đã ghi/sửa/xóa (hoặc bỏ qua xác nhận). Xét từng câu, và trong câu xét từng mệnh đề (tách ở , ;):
     * mệnh đề có từ để hỏi chỉ bị chặn vì động từ GHI SỔ, không bị chặn chỉ vì "xong/hoàn thành".
     */
    fun claimsStateChange(text: String): Boolean = splitSentences(nfc(text)).any { sentence ->
        alwaysClaims.any { it.containsMatchIn(sentence) } ||
            sentence.split(clauseSplit).any { clause ->
                (if (askCue.containsMatchIn(clause)) askBackClaims else strictClaims).any { it.containsMatchIn(clause) }
            }
    }

    /** Các số phút / số đếm tìm thấy trong [text]; số đếm null = không bao giờ có căn cứ (không phải số lần/buổi... hoặc đơn vị lạ). */
    private class Figures(val minutes: List<Int>, val counts: List<Int?>)

    private fun figuresIn(text: String): Figures {
        val minutes = mutableListOf<Int>()
        val counts = mutableListOf<Int?>()
        // "2h30" / "2 giờ 30p": gộp thành một số phút, rồi bỏ khỏi chuỗi để không bị đọc lại như "2h".
        hourMinute.findAll(text).forEach { minutes += it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        var rest = hourMinute.replace(text, " ")
        clock.findAll(rest).forEach { minutes += it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        rest = clock.replace(rest, " ")
        otherDigitFigure.findAll(rest).forEach { m ->
            val v = m.value.trim()
            val time = figure.matchEntire(v)
            if (time != null) {
                minutes += minutesOf(time.groupValues[1], time.groupValues[2])
            } else {
                counts += if (countFigure.matchEntire(v) == null) null else digitsOnly.find(v)?.value?.toIntOrNull()
            }
        }
        return Figures(minutes, counts)
    }

    /**
     * Số phút (thời lượng hoặc giờ đồng hồ) mà người dùng đã viết trong câu của họ, bằng chữ số hoặc số bằng chữ, có dấu hoặc không dấu.
     * Đây là tập số được phép nhắc lại trong CÂU HỎI của AI (xem KDoc đầu lớp, mục 2b).
     */
    internal fun userFigureMinutes(userText: String): Set<Int> {
        // Người dùng gõ không dấu ("hoc hai tieng"): khôi phục dấu để dùng chung bộ đọc số bằng chữ của bộ phân tích (xem VietnameseChatParser.restoreUnaccented).
        val t = VietnameseChatParser.restoreUnaccented(nfc(userText))
        // v1.0.13 (C3b) "30 hay 40 phút" => cả 30 và 40 phút; (C3c) "từ 8h đến 9h" => 60 phút và hai đầu 08:00/09:00 theo 24h.
        return (figuresIn(VietnameseChatParser.normalizeSpelledDurations(VietnameseChatParser.expandSharedUnitAlternatives(t))).minutes +
            VietnameseChatParser.userClockMinutes(t)).toSet()
    }

    /**
     * True nếu có số liệu không có căn cứ: phần trăm, số/thời lượng bằng chữ (Việt, Anh), "2h30"/"2:30", hoặc số + đơn vị mà không phải
     * (a) thời lượng hay giờ bắt đầu/kết thúc của chính đề xuất đang chờ xác nhận (chỉ khi [allowProposalDurations]),
     * (b) số phút trong [userMinutes] (số người dùng đã viết; người gọi chỉ truyền cho CÂU HỎI, và đã đổi số bằng chữ sang chữ số), hay
     * (c) số đề xuất (chỉ khi có ít nhất một đề xuất).
     */
    fun hasUnbackedFigure(
        rawText: String,
        proposals: List<ActivityProposal>,
        allowProposalDurations: Boolean,
        userMinutes: Set<Int> = emptySet(),
        contextBackedMinutes: Set<Int> = emptySet()
    ): Boolean {
        val text = nfc(rawText)
        if (percent.containsMatchIn(text) || wordFigure.containsMatchIn(text) || enWordFigure.containsMatchIn(text) || halfWord.containsMatchIn(text)) return true
        val allowed: Set<Int> = userMinutes + contextBackedMinutes + (if (allowProposalDurations) {
            buildSet {
                proposals.forEach { p ->
                    add(p.durationMinutes)
                    hmOf(p.startLocal)?.let(::add)
                    hmOf(p.endLocal)?.let(::add)
                }
            }
        } else emptySet())
        val f = figuresIn(text)
        val invalidCount = f.counts.any { cnt ->
            cnt == null || ((proposals.isEmpty() || cnt != proposals.size) && cnt !in contextBackedMinutes)
        }
        return f.minutes.any { it !in allowed } || invalidCount
    }

    /** @return (lời đáp đã làm sạch hoặc null, ghi chú nếu đã bỏ). */
    fun sanitize(
        reply: String?,
        userText: String,
        proposals: List<ActivityProposal>,
        hasQueries: Boolean,
        nowTime: LocalTime? = null,
        contextBackedMinutes: Set<Int> = emptySet()
    ): Pair<String?, String?> {
        val text = reply?.replace(Rx.ascii("""\s+"""), " ")?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }
            ?: return null to null

        if (claimsStateChange(text)) {
            return null to "Đã bỏ lời đáp của AI vì khẳng định đã ghi/sửa/xóa. Chỉ ứng dụng mới báo thành công, sau khi bạn xác nhận và bản ghi được lưu."
        }
        // Có query: mọi số liệu thời gian/phần trăm phải đến từ engine, nên lời đáp của AI không được tự nêu.
        // Không có query: chỉ thời lượng/giờ của đề xuất đang chờ xác nhận hoặc số liệu xác định từ ContextEngine mới được nhắc lại.
        // Ngoại lệ chung: CÂU HỎI được nhắc lại số của chính người dùng (mục 2b). Chỉ cần một câu có số không căn cứ là bỏ cả lời đáp (mục 4).
        val userMinutes by lazy { userFigureMinutes(userText) }
        val unbacked = splitSentences(nfc(text)).any { s ->
            val question = isQuestion(s)
            // v1.0.13 (C3a): trong CÂU HỎI, AI được nhắc lại giờ hiện tại mà ứng dụng đã đưa cho nó (không phải số của sổ cái).
            val t = if (question && nowTime != null) stripNowClock(s, nowTime) else s
            if (question && userMinutes.isNotEmpty()) {
                hasUnbackedFigure(
                    VietnameseChatParser.normalizeSpelledDurations(t),
                    proposals,
                    allowProposalDurations = !hasQueries,
                    userMinutes = userMinutes,
                    contextBackedMinutes = if (hasQueries) emptySet() else contextBackedMinutes
                )
            } else {
                hasUnbackedFigure(
                    t,
                    proposals,
                    allowProposalDurations = !hasQueries,
                    contextBackedMinutes = if (hasQueries) emptySet() else contextBackedMinutes
                )
            }
        }
        if (unbacked) {
            return null to if (hasQueries) "Đã bỏ phần lời đáp có số liệu của AI; số liệu bên dưới được tính từ sổ cái."
            else "Đã bỏ lời đáp của AI vì có số liệu không có căn cứ trong sổ cái."
        }
        return text to null
    }
}

/** Dựng thẻ trả lời trong tab Wally từ lời đáp của AI + số liệu tính cục bộ. */
object WallyAnswerBuilder {
    const val LABEL_AI = "Wally (AI)"

    fun build(
        question: String,
        reply: String?,
        answers: List<QueryAnswer>,
        notes: List<String>,
        proposalCount: Int,
        leadLines: List<String> = emptyList()
    ): ChatAnswer? {
        val hasReply = !reply.isNullOrBlank()
        // V106-N1: Ở luồng ghi nhận thuần (có đề xuất chờ xác nhận, chưa commit và không có kết quả truy vấn),
        // ứng dụng ưu tiên thông báo proposal/trạng thái giao dịch do app tạo, không dựng thẻ lời đáp AI.
        if (proposalCount > 0 && answers.isEmpty()) return null
        if (!hasReply && answers.isEmpty()) {
            val msg = notes.firstOrNull()
                ?: "Wally chưa nhận ra hoạt động hay câu hỏi nào. Hãy nói rõ thời lượng, ví dụ \"Học 45p\", hoặc hỏi \"Hôm nay tôi dùng bao nhiêu giờ?\"."
            return ChatAnswer.clarification(question, msg, emptyList())
        }
        val headline = if (hasReply) reply!! else answers.first().headline
        val lines = buildList {
            addAll(leadLines)
            answers.forEachIndexed { i, a ->
                if (hasReply || i > 0) add(a.headline)
                addAll(a.lines)
            }
        }
        val allNotes = notes + answers.flatMap { it.notes }
        return ChatAnswer(question, headline, lines, allNotes.distinct(), label = LABEL_AI)
    }
}

/** Thứ trong tuần bằng tiếng Việt để AI quy đổi "tuần này", "thứ Hai tuần trước". */
object VietnameseCalendar {
    fun dayName(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "thứ Hai"
        DayOfWeek.TUESDAY -> "thứ Ba"
        DayOfWeek.WEDNESDAY -> "thứ Tư"
        DayOfWeek.THURSDAY -> "thứ Năm"
        DayOfWeek.FRIDAY -> "thứ Sáu"
        DayOfWeek.SATURDAY -> "thứ Bảy"
        DayOfWeek.SUNDAY -> "Chủ nhật"
    }
}
