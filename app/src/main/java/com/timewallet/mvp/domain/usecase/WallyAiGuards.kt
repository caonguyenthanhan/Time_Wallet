package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.DayOfWeek
import java.time.LocalDate
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
        today: LocalDate
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

        val cs = parseDate(compareStart)
        val ce = parseDate(compareEnd)
        val compare = if (cs != null && ce != null) {
            if (ce.isBefore(cs)) return Result.Rejected("Khoảng so sánh bị ngược.")
            if (ChronoUnit.DAYS.between(cs, ce) + 1 > MAX_SPAN_DAYS) return Result.Rejected("Khoảng so sánh quá dài.")
            period(cs, ce, today)
        } else if (cs != null || ce != null) {
            return Result.Rejected("Khoảng so sánh thiếu một đầu.")
        } else null

        return Result.Ok(QueryIntent(m, period(s, e, today), cat, compare))
    }
}

/**
 * Lọc đề xuất của AI bằng các chính sách bắt buộc. Trả về đề xuất còn lại + cảnh báo giải thích vì sao bị bỏ.
 * - Lệnh hủy / câu sửa lại thời lượng: không đề xuất nào được sống sót (hỏi lại).
 * - Mệnh đề phủ định ("không học 2h"): đề xuất tương ứng bị bỏ; nếu không còn mệnh đề hợp lệ nào thì bỏ hết.
 * - Thời lượng AI đưa ra phải khớp thời lượng người dùng nêu (khi câu có thể đọc chắc chắn).
 */
object AiProposalGuard {

    data class Result(val proposals: List<ActivityProposal>, val warnings: List<String>)

    fun apply(userText: String, proposals: List<ActivityProposal>): Result {
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

        val warnings = mutableListOf<String>()
        var kept = proposals

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

        if (!VietnameseChatParser.hasUnnormalizedWordDuration(text)) {
            val stated = VietnameseChatParser.allDurationMinutes(text)
            if (stated.isNotEmpty()) {
                val allowed = stated.toSet() + stated.sum()
                val before = kept.size
                kept = kept.filter { it.durationMinutes in allowed }
                if (kept.size < before) {
                    warnings += "Đã bỏ ${before - kept.size} đề xuất của AI có thời lượng không khớp với câu bạn nhập."
                }
            }
        }
        return Result(kept, warnings)
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
 * Lời đáp tự do của AI (WALLY v1.0.6, F-V105-08 / R03). Lời đáp chỉ để diễn giải, KHÔNG phải nguồn sự thật:
 *  1. AI không được khẳng định đã ghi/lưu/sửa/xóa/hủy (chỉ ứng dụng mới được báo thành công, sau khi commit) và không được
 *     bảo "không cần xác nhận".
 *  2. Số liệu cá nhân (thời gian bằng chữ số HOẶC bằng chữ, phần trăm, số lần/buổi/bản ghi) chỉ đến từ LedgerQueryEngine.
 *     Chỉ một ngoại lệ: thời lượng của chính đề xuất đang chờ xác nhận (hiển thị lại cho khớp thẻ đề xuất).
 *     Số xuất hiện trong câu hỏi của người dùng KHÔNG phải căn cứ (AI lặp lại số trong câu hỏi cũng bị bỏ).
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
    private val verbs =
        "ghi\\s*nhận|ghi\\s*lại|ghi\\s*chép|ghi|lưu\\s*trữ|lưu\\s*giữ|lưu\\s*lại|lưu|thêm|xóa|xoá|sửa|chỉnh\\s*sửa|chỉnh|cập\\s*nhật|hủy|huỷ|hoàn\\s*tất|hoàn\\s*thành|xử\\s*lý\\s+xong|đã\\s+xử\\s+lý|tiếp\\s*nhận|chốt|nạp|đồng\\s*bộ|cất|chép|note|nhập\\s*liệu"
    private val destinations =
        "(?:sổ(?:\\s*cái)?|nhật\\s*ký|danh\\s*sách|lịch\\s*sử|báo\\s*cáo|thống\\s*kê|dữ\\s*liệu|hệ\\s*thống|bộ\\s*nhớ|ví\\s+thời\\s+gian)"
    private val safeNegatedMarker = "(?<!chưa\\s)(?<!không\\s)(?<!chẳng\\s)"
    private val claims = listOf(
        // 1. Khẳng định trong một mệnh đề có (đã/vừa/vừa mới) + động từ ghi/lưu/cập nhật/hoàn tất (cho phép xen từ/số liệu trong cùng mệnh đề, không giới hạn 3 từ).
        Rx.uni(
            """$safeNegatedMarker$B(?:đã|vừa(?:\s+mới)?)\s+(?:(?!(?:chưa|không|chẳng|để|muốn|cần|chờ|trước\s+khi)\b)[^,.;!?\n]){0,70}?(?:$verbs)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        ),
        // 2. Chủ ngữ là hệ thống / sổ cái đã tiếp nhận hoặc giữ giúp người dùng.
        Rx.uni(
            """$B$destinations\s+(?:[^,.;!?\n]{0,35}?)$safeNegatedMarker(?:đã|vừa(?:\s+mới)?)\s+(?:[^,.;!?\n]{0,25}?)(?:nhận|tiếp\s*nhận|ghi|lưu|thêm|cập\s*nhật|giữ)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        ),
        // 3. Trạng thái hoàn thành ngay sau động từ ("ghi xong", "lưu thành công", "xử lý xong").
        Rx.uni("""$B(?:$verbs)(?:\s+lại)?\s+(?:xong|rồi|thành\s*công|hoàn\s*tất|hoàn\s*thành)(?!\p{L})""", RegexOption.IGNORE_CASE),
        // 4. Khẳng định bản ghi đã/đang nằm trong sổ / nhật ký / danh sách / lịch sử / hệ thống.
        Rx.uni(
            """$safeNegatedMarker$B(?:(?:đã|vừa|đang)\s+(?:[^,.;!?\n]{0,45}?)?)?(?:nằm\s+trong|có\s+mặt\s+trong|ở\s+trong|xuất\s+hiện\s+trong|lưu\s+(?:vào|trong)|ghi\s+(?:vào|trong|lên)|thêm\s+(?:vào|lên)|đưa\s+(?:vào|lên)|cập\s+nhật\s+(?:vào|trong|lên)|đã\s+vào|đã\s+lên)\s+$destinations""",
            RegexOption.IGNORE_CASE
        ),
        // 5. Cách nói gián tiếp hoàn tất hoặc giữ giúp người dùng ("xong xuôi", "tất cả xong rồi", "giữ giúp bạn rồi", "tự động hoàn tất").
        Rx.uni(
            """$B$safeNegatedMarker(?:xong\s+xuôi|xong\s+hết|xong\s+cả|tất\s+cả\s+(?:đã\s+)?xong|mọi\s+thứ\s+(?:đã\s+)?xong|xong\s+rồi|hoàn\s*tất\s+rồi|hoàn\s*thành\s+rồi|đã\s+xong|tự\s*động\s+(?:hoàn\s*tất|hoàn\s*thành|ghi|lưu|thêm|xử\s*lý|cập\s*nhật|xác\s*nhận|chốt)|(?:giữ|lưu\s*giữ|cất|chốt|giữ\s+hộ|giữ\s+lại)\s+(?:hộ|giúp|dùm|cho)\s+bạn(?:\s+\p{L}+){0,2}\s*(?:rồi|nhé|nha)?)(?!\p{L})""",
            RegexOption.IGNORE_CASE
        ),
        // 6. Bảo người dùng không cần xác nhận / không cần làm gì thêm.
        Rx.uni(
            """(?<!không\sphải\s)$B(?:(?:không\s+cần|khỏi(?:\s+cần)?|không\s+phải|bỏ\s+qua|miễn)\s+(?:bước\s+)?(?:xác\s*nhận|bấm(?:\s+(?:gì|xác\s*nhận|nữa))?|làm\s+gì(?:\s+thêm)?|thao\s*tác(?:\s+thêm)?|chờ|kiểm\s*tra)|bạn\s+(?:không|chẳng|chưa)\s+cần\s+(?:bấm|xác\s*nhận|làm\s+gì))(?!\p{L})""",
            RegexOption.IGNORE_CASE
        ),
        // 8. "Ghi nhận ... cho bạn rồi nhé", "Hoàn tất cho bạn rồi nhé!", "Xong việc ghi nhận cho bạn rồi đó" (không có đã/vừa, không có số).
        Rx.uni(
            """(?<!sẽ\s)(?<!để\s)(?<!muốn\s)(?<!cần\s)(?<!chưa\s)(?<!không\s)$B(?:$verbs|xong(?:\s+việc)?)\s+(?:(?!(?:sẽ|để|khi|nếu|muốn|cần|chờ)\b)[^,.;!?\n]){0,30}?(?:cho|giúp|hộ|dùm)\s+bạn\s+(?:rồi|nhé|nha|đó|xong)(?!\p{L})""",
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

    private fun nfc(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    private fun minutesOf(value: String, unit: String): Int {
        val v = value.replace(',', '.').toDoubleOrNull() ?: return -1
        val u = unit.lowercase(Locale.ROOT)
        return if (u in setOf("giờ", "tiếng", "h", "hr", "hrs", "hour", "hours")) Math.round(v * 60).toInt() else Math.round(v).toInt()
    }

    private fun hmOf(hm: String?): Int? = hm?.takeIf { Rx.ascii("""\d{1,2}:\d{2}""").matches(it) }?.split(':')?.let { it[0].toInt() * 60 + it[1].toInt() }

    /** True nếu lời đáp khẳng định đã ghi/sửa/xóa (hoặc bỏ qua xác nhận). */
    fun claimsStateChange(text: String): Boolean = nfc(text).let { t -> claims.any { it.containsMatchIn(t) } }

    /**
     * True nếu có số liệu không có căn cứ: phần trăm, số/thời lượng bằng chữ (Việt, Anh), "2h30"/"2:30", hoặc số + đơn vị mà không phải
     * (a) thời lượng hay giờ bắt đầu/kết thúc của chính đề xuất đang chờ xác nhận (chỉ khi [allowProposalDurations]) hay
     * (b) số đề xuất (chỉ khi có ít nhất một đề xuất).
     */
    fun hasUnbackedFigure(rawText: String, proposals: List<ActivityProposal>, allowProposalDurations: Boolean): Boolean {
        val text = nfc(rawText)
        if (percent.containsMatchIn(text) || wordFigure.containsMatchIn(text) || enWordFigure.containsMatchIn(text) || halfWord.containsMatchIn(text)) return true
        val allowed: Set<Int> = if (allowProposalDurations) {
            buildSet {
                proposals.forEach { p ->
                    add(p.durationMinutes)
                    hmOf(p.startLocal)?.let(::add)
                    hmOf(p.endLocal)?.let(::add)
                }
            }
        } else emptySet()

        // "2h30" / "2 giờ 30p": gộp thành một số phút, rồi bỏ khỏi chuỗi để không bị đọc lại như "2h".
        var rest = text
        val combined = hourMinute.findAll(text).toList()
        if (combined.any { (it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()) !in allowed }) return true
        rest = hourMinute.replace(rest, " ")
        val clocks = clock.findAll(rest).toList()
        if (clocks.any { (it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()) !in allowed }) return true
        rest = clock.replace(rest, " ")

        return otherDigitFigure.findAll(rest).any { m ->
            val v = m.value.trim()
            val time = figure.matchEntire(v)
            if (time != null) {
                minutesOf(time.groupValues[1], time.groupValues[2]) !in allowed
            } else {
                val n = digitsOnly.find(v)?.value?.toIntOrNull()
                countFigure.matchEntire(v) == null ||
                    n == null || proposals.isEmpty() || n != proposals.size
            }
        }
    }

    /** @return (lời đáp đã làm sạch hoặc null, ghi chú nếu đã bỏ). */
    fun sanitize(
        reply: String?,
        @Suppress("UNUSED_PARAMETER") userText: String,
        proposals: List<ActivityProposal>,
        hasQueries: Boolean
    ): Pair<String?, String?> {
        val text = reply?.replace(Rx.ascii("""\s+"""), " ")?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }
            ?: return null to null

        if (claimsStateChange(text)) {
            return null to "Đã bỏ lời đáp của AI vì khẳng định đã ghi/sửa/xóa. Chỉ ứng dụng mới báo thành công, sau khi bạn xác nhận và bản ghi được lưu."
        }
        // Có query: mọi số liệu thời gian/phần trăm phải đến từ engine, nên lời đáp của AI không được tự nêu.
        // Không có query: chỉ thời lượng/giờ của đề xuất đang chờ xác nhận mới được nhắc lại.
        if (hasUnbackedFigure(text, proposals, allowProposalDurations = !hasQueries)) {
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
