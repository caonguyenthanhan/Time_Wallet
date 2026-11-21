package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActivityProposal
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

data class ParseChatOutcome(
    val proposals: List<ActivityProposal>,
    val warnings: List<String>,
    val source: OutcomeSource = OutcomeSource.LOCAL,
    val fault: AiFault? = null,
    val requestId: String? = null,
    /** Lời đáp ngắn của AI (đã qua ReplyGuard); null nếu không có. */
    val reply: String? = null,
    /** Câu hỏi số liệu AI yêu cầu, đã kiểm tra tất định; app tự tính số từ sổ cái. */
    val queries: List<QueryIntent> = emptyList(),
    /** Số token/nhận trạng thái đệm do nhà cung cấp báo (null nếu không có usage). */
    val usage: AiUsage? = null,
    val retryCount: Int = 0,
    val intent: WallyIntentKind? = null,
    /** true nếu lượt này có đính kèm ngữ cảnh thích ứng từ [WallyContextEngine]. */
    val usedAdaptiveContext: Boolean = false
)

/**
 * Bộ phân tích ngôn ngữ tự nhiên tiếng Việt theo quy tắc (MVP Refinement 01):
 * - Tách tới 12 hoạt động trong một câu để xác nhận hàng loạt (Batch Confirmation).
 * - TUYỆT ĐỐI KHÔNG tự suy diễn giờ bắt đầu / kết thúc khi câu chỉ có thời lượng (ví dụ: "Học 45p, đọc sách 30p...").
 * - Chỉ gắn startLocal / endLocal khi người dùng nêu rõ khoảng giờ ("13:00-18:00", "từ 14h đến 15h30")
 *   hoặc nêu rõ mốc giờ cụ thể kèm thời lượng ("lúc 8h học 45p").
 */
object VietnameseChatParser {

    private val categoryRules: List<Pair<String, Regex>> = listOf(
        "sleep" to Rx.ascii("""ngủ|chợp mắt|\bnap\b""", RegexOption.IGNORE_CASE),
        "health" to Rx.ascii("""tập thể dục|\bgym\b|đi bộ|chạy bộ|yoga|đạp xe|bơi|vận động|thể thao""", RegexOption.IGNORE_CASE),
        "learn" to Rx.ascii("""học|đọc sách|đọc tài liệu|nghiên cứu|feynman|ôn bài|khóa học|python|tiếng anh|luyện đề""", RegexOption.IGNORE_CASE),
        "fun" to Rx.ascii("""youtube|xem phim|lướt|\bgame\b|tiktok|facebook|giải trí|nghe nhạc|netflix|chơi""", RegexOption.IGNORE_CASE),
        "rest" to Rx.ascii("""nghỉ|thư giãn|cà phê|coffee|thiền|uống trà|giải lao""", RegexOption.IGNORE_CASE),
        "social" to Rx.ascii("""gia đình|bạn bè|hẹn hò|gọi điện|trò chuyện|gặp gỡ|ăn tối cùng""", RegexOption.IGNORE_CASE),
        "life" to Rx.ascii("""ăn cơm|ăn sáng|ăn trưa|ăn tối|nấu|dọn dẹp|di chuyển|đi lại|tắm|mua sắm|sinh hoạt|giặt đồ""", RegexOption.IGNORE_CASE),
        "work" to Rx.ascii("""làm việc|dự án|họp|báo cáo|\bcode\b|coding|đi làm|kiểm thử|debug|khách hàng""", RegexOption.IGNORE_CASE)
    )

    private val timeRangeRegex = Rx.uni(
        """(?:từ\s*)?(\d{1,2})\s*(?:h|:)\s*(\d{0,2})(?:\s*(?:giờ\s*)?(sáng|trưa|chiều|tối|đêm|khuya))?\s*(?:đến|tới|-|–)\s*(?:lúc\s*)?(\d{1,2})\s*(?:h|:)\s*(\d{0,2})(?:\s*(?:giờ\s*)?(sáng|trưa|chiều|tối|đêm|khuya))?""",
        RegexOption.IGNORE_CASE
    )

    // "9h làm việc đến 20h", "từ 8h sáng chạy bộ đến 9h", "14h họp team tới 16h30": hoạt động xen giữa hai mốc giờ đầu–cuối.
    private val inlineActivityTimeRangeRegex = Rx.uni(
        """^\s*(?:(?:hôm\s+nay|sáng\s+nay|trưa\s+nay|chiều\s+nay|tối\s+nay|hôm\s+qua|đêm\s+qua|hôm\s+kia|ngày\s+mai|mai)\s+)?(?:từ\s*)?(?:lúc\s*)?(\d{1,2})\s*(?:h|:)\s*(\d{0,2})(?:\s*(?:giờ\s*)?(sáng|trưa|chiều|tối|đêm|khuya))?\s+(?!tiếng\b|phút\b|p\b|min\b|giờ\b|đến\b|tới\b|hôm\s+qua\b|đêm\s+qua\b|hôm\s+kia\b|hôm\s+nay\b|ngày\s+mai\b|mai\b|không\b|chưa\b|chẳng\b|đừng\b|chớ\b)([^\d,.;:\n?!]{2,42}?)\s+(?:đến|tới)\s+(?:lúc\s*)?(\d{1,2})\s*(?:h|:)\s*(\d{0,2})(?:\s*(?:giờ\s*)?(sáng|trưa|chiều|tối|đêm|khuya))?(?=\s|$|[,.;:?!\)])""",
        RegexOption.IGNORE_CASE
    )

    // UC-15: khoảng giờ vắt qua ngày ("từ 20h tối hôm qua đến 7h sáng hôm nay", "từ 8h ngày hôm qua đến 7h sáng hôm nay", "20:00 hôm qua - 07:00 hôm nay").
    private val crossDayRangeRegex = Rx.uni(
        """(?:từ\s*)?(?:lúc\s*)?(\d{1,2})\s*(?:h|:|giờ)\s*(\d{0,2})\s*(phút|p)?\s*(?:(sáng|trưa|chiều|tối|đêm|khuya|am|pm)\s*)?(?:ngày\s*)?(hôm\s+kia|hôm\s+qua|đêm\s+qua|hôm\s+nay|nay)?\s*(?:đến|tới|-|–)\s*(?:lúc\s*)?(\d{1,2})\s*(?:h|:|giờ)\s*(\d{0,2})\s*(phút|p)?\s*(?:(sáng|trưa|chiều|tối|đêm|khuya|am|pm)\s*)?(?:ngày\s*)?(hôm\s+nay|sáng\s+nay|trưa\s+nay|chiều\s+nay|tối\s+nay|đêm\s+nay|nay|hôm\s+qua|ngày\s+mai|mai)?""",
        RegexOption.IGNORE_CASE
    )

    private val explicitStartAtHourRegex = Rx.uni(
        """(?:^|\s)lúc\s*(\d{1,2})\s*(?:h|:)\s*(\d{0,2})""",
        RegexOption.IGNORE_CASE
    )

    /** v1.0.12: "từ 14h30", "đến 16h", "tới 9:15" là mốc giờ, không phải thời lượng 14,5 giờ. */
    private val clockAfterFromRegex = Rx.uni(
        """(?:^|\s)(?:từ|đến|tới)\s*(?:lúc\s*)?(\d{1,2})\s*(?:h|:)\s*(\d{0,2})""",
        RegexOption.IGNORE_CASE
    )

    private val hoursRegex = Rx.ascii(
        """(\d+(?:[.,]\d+)?)\s*(?:h\s*tiếng|giờ|tiếng|h(?=\d|\s|$|[,.;]))""",
        RegexOption.IGNORE_CASE
    )

    private val minutesRegex = Rx.ascii(
        """(\d+)\s*(?:phút|p\b|min\b)""",
        RegexOption.IGNORE_CASE
    )

    // Tách mệnh đề liên kết bằng "và", "rồi", "sau đó", "tiếp theo" trong câu nhiều hoạt động
    private val clauseConnectorRegex = Rx.uni(
        """\s+(?:và|rồi|sau đó|tiếp theo)\s+""",
        RegexOption.IGNORE_CASE
    )

    // AI-01 (Finding D): mệnh đề chốt tổng ("tổng cộng 5h") không phải hoạt động độc lập.
    // Cố ý KHÔNG khớp từ "tổng" đơn lẻ để giữ các hoạt động như "họp tổng kết".
    private val aggregateClauseRegex = Rx.uni(
        """(?:^|\s)(?:tổng cộng|tổng thời gian|tổng số giờ|tổng số thời gian|cộng lại|tính chung|cả ngày hết|tất cả là)(?=\s|$|[,.;:])""",
        RegexOption.IGNORE_CASE
    )

    fun isAggregateClause(text: String): Boolean = aggregateClauseRegex.containsMatchIn(text)

    // AI-01 FINAL FIX (phủ định): "Hôm nay tôi không học 2 tiếng", "Đừng ghi nhận 3 giờ làm việc",
    // "Tôi chưa code 1 tiếng hôm nay" KHÔNG được tạo proposal. Chỉ coi là phủ định khi từ phủ định đứng
    // TRƯỚC thời lượng trong cùng mệnh đề; phủ định đứng sau ("Học 2h không nghỉ", "Học 2h chưa ghi vào sổ")
    // chỉ bổ nghĩa cho phần đuôi nên giữ nguyên. Mệnh đề được tách bởi "nhưng/tuy nhiên/song".
    private val negationCueRegex = Rx.uni(
        """(?:^|\s)(?:không|chưa|chẳng|đừng|chớ)(?=\s|$|[,.;:!?])""",
        RegexOption.IGNORE_CASE
    )

    // "không nghỉ / không ngừng ..." mô tả cách làm liên tục (khẳng định hoạt động), không phải phủ định hoạt động.
    private val nonStopPhraseRegex = Rx.uni(
        """(?:^|\s)(?:không|chẳng)\s+(?:nghỉ|ngừng|ngơi nghỉ|ngơi|ngắt quãng|gián đoạn)(?=\s|$|[,.;:!?])""",
        RegexOption.IGNORE_CASE
    )

    // Từ đệm/mốc ngày đứng trước hoạt động; không tính là "hoạt động" khi xét cụm "không nghỉ".
    private val leadingFillerRegex = Rx.uni(
        """(?:^|\s)(?:tôi|mình|đã|vừa mới|vừa|mới|sẽ|bây giờ|giờ|hôm nay|hôm qua|đêm qua|hôm kia|ngày mai|mai|lúc|buổi|khoảng|trong)(?=\s|$|[,.!?;:])""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Bỏ cụm "không nghỉ/ngừng..." chỉ khi phía trước đã có một hoạt động ("Làm việc không nghỉ 3h").
     * Nếu phía trước chỉ là đại từ/mốc ngày ("Hôm nay tôi không nghỉ 2h") thì "nghỉ" chính là hoạt động bị phủ định.
     */
    private fun stripNonStopPhrase(segment: String): String {
        val m = nonStopPhraseRegex.find(segment) ?: return segment
        val before = leadingFillerRegex.replace(segment.substring(0, m.range.first), " ")
        val hasActivityBefore = before.any { it.isLetter() }
        return if (hasActivityBefore) nonStopPhraseRegex.replace(segment, " ") else segment
    }

    private val contrastClauseRegex = Rx.uni(
        """\s+(?:nhưng mà|nhưng|tuy nhiên|song)\s+""",
        RegexOption.IGNORE_CASE
    )

    // Câu sửa lại thời lượng/hoạt động ("không phải 2 tiếng, 3 tiếng", "sửa lại thành 3h"): mơ hồ => hỏi lại.
    private val correctionRegex = Rx.uni(
        """(?:^|\s)(?:không phải|chứ không|sửa lại|sửa thành|sửa là|cập nhật lại|nhầm|ý tôi là|ý là|à không|thay vì|đúng ra)(?=\s|$|[,.;:!?])""",
        RegexOption.IGNORE_CASE
    )

    private val singleViDigits = mapOf(
        "một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "năm" to 5,
        "sáu" to 6, "bảy" to 7, "bẩy" to 7, "tám" to 8, "chín" to 9
    )
    private val allViUnitDigits = singleViDigits + mapOf(
        "mốt" to 1, "tư" to 4, "lăm" to 5, "nhăm" to 5
    )
    private val ordinalGuardWords = setOf(
        "thứ", "tháng", "quý", "lần", "bước", "mục", "phần", "chương",
        "tập", "tầng", "nhóm", "loại", "câu", "điều", "khoản", "trang", "dòng"
    )
    private val nonDurationAfterTiengWords = setOf(
        "anh", "việt", "pháp", "nhật", "hàn", "trung", "đức", "nga", "tây", "bồ",
        "thái", "hoa", "nước", "mẹ", "lóng", "cười", "khóc", "hét", "kêu", "chuông", "vỗ", "thở"
    )
    private val viWordTokenPattern =
        """(?:một|mốt|hai|ba|bốn|tư|năm|lăm|nhăm|sáu|bảy|bẩy|tám|chín|mười|mươi|trăm|linh|lẻ)"""
    private val halfWordRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))(?:một\s+)?nửa\s+(?:tiếng|giờ)(?:\s+đồng\s+hồ)?(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    private val hourRuoiRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))(?:(\d+(?:[.,]\d+)?|$viWordTokenPattern(?:\s+$viWordTokenPattern){0,4})\s+)?(tiếng|giờ)(?:\s+đồng\s+hồ)?\s+rưỡi(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    // v1.0.13 (C2): "1h rưỡi", "2h rưỡi" = 1,5 / 2,5 giờ (cùng quy tắc với "1 giờ rưỡi").
    private val hourLetterRuoiRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))(\d{1,2})\s?h\s+rưỡi(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    private val dayPeriodNextRegex = Rx.uni("""^\s*(?:sáng|trưa|chiều|tối|đêm)(?=$|[\s,.;:!?()])""", RegexOption.IGNORE_CASE)
    private val decimalWordDurationRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))($viWordTokenPattern(?:\s+$viWordTokenPattern){0,3})\s+phẩy\s+($viWordTokenPattern(?:\s+$viWordTokenPattern){0,2})\s+(tiếng|giờ|phút)(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    private val wordUnitDurationRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))($viWordTokenPattern(?:\s+$viWordTokenPattern){0,5})\s+(tiếng\s+đồng\s+hồ|giờ\s+đồng\s+hồ|tiếng|giờ|phút)(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    private val trailingWordMinutesAfterHourRegex = Rx.uni(
        """(?<=\b\d{1,2}\s?(?:giờ|tiếng|h)\s)((?:hai|ba|bốn|năm)\s+mươi(?:\s+(?:mốt|hai|ba|bốn|tư|lăm|nhăm|sáu|bảy|bẩy|tám|chín))?|mười(?:\s+(?:một|hai|ba|bốn|lăm|nhăm|sáu|bảy|bẩy|tám|chín))?|hai\s+lăm|hai\s+nhăm|ba\s+lăm|ba\s+nhăm|bốn\s+lăm|bốn\s+nhăm|năm\s+lăm)(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    private val vagueNegatedDurationRegex = Rx.uni(
        """(?:^|\s)(?:vài|mấy|đôi|dăm(?:\s+ba)?|một\s+(?:vài|ít|chút)|chút|ít|tẹo|lát|cả\s+buổi|bao\s+nhiêu)\s+(?:tiếng(?:\s+đồng\s+hồ)?|giờ(?:\s+đồng\s+hồ)?|phút|h|p)(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    private val residualWordDurationRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))(?:(?:$viWordTokenPattern|vài|mấy|đôi|dăm|chút|ít|rưỡi|nửa|chục|tá|ngót)\s+(?:tiếng|giờ|phút)|(?:tiếng|giờ)\s+(?:rưỡi|nửa))(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )
    private val coordConnectorRegex = Rx.uni(
        """(?:^|\s)(?:và|hay|hoặc|với|cùng|lẫn|cũng\s+như)(?=\s|$)""",
        RegexOption.IGNORE_CASE
    )
    private val clockPointPrefixRegex = Rx.uni("""(?:từ|đến|tới|lúc|khoảng|vào)\s+$""", RegexOption.IGNORE_CASE)

    private fun prevWordBefore(text: String, start: Int): String =
        text.substring(0, start)
            .trimEnd()
            .substringAfterLast(' ', "")
            .trim(',', '.', ':', ';', '(', ')')
            .lowercase(Locale("vi", "VN"))

    private fun nextWordAfter(text: String, endExclusive: Int): String =
        text.substring(endExclusive)
            .trimStart()
            .substringBefore(' ')
            .trim(',', '.', ':', ';', '!', '?', '(', ')')
            .lowercase(Locale("vi", "VN"))

    private fun parseSubHundred(tokens: List<String>): Int? = when (tokens.size) {
        1 -> when (tokens[0]) {
            "mười" -> 10
            else -> singleViDigits[tokens[0]]
        }
        2 -> {
            val (a, b) = tokens
            val da = singleViDigits[a]
            when {
                a == "mười" -> allViUnitDigits[b]?.takeIf { b !in setOf("mốt", "tư") }?.let { 10 + it }
                b == "mươi" && da != null && da in 2..9 -> da * 10
                da != null && da in 2..9 && b in setOf("mốt", "tư", "lăm", "nhăm") -> da * 10 + allViUnitDigits.getValue(b)
                else -> null
            }
        }
        3 -> {
            val (a, mid, c) = tokens
            val da = singleViDigits[a]
            val dc = allViUnitDigits[c]
            if (mid == "mươi" && da != null && da in 2..9 && dc != null) da * 10 + dc else null
        }
        else -> null
    }

    internal fun parseViNumberPhrase(rawPhrase: String): Int? {
        val tokens = rawPhrase.lowercase(Locale("vi", "VN")).trim().split(Rx.ascii("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        if (tokens.size >= 2 && tokens[1] == "trăm") {
            val h = singleViDigits[tokens[0]] ?: return null
            val rem = tokens.drop(2)
            if (rem.isEmpty()) return h * 100
            if (rem.size == 2 && (rem[0] == "lẻ" || rem[0] == "linh")) {
                val u = singleViDigits[rem[1]] ?: return null
                return h * 100 + u
            }
            val sub = parseSubHundred(rem) ?: return null
            return if (sub in 10..99) h * 100 + sub else null
        }
        return parseSubHundred(tokens)
    }

    internal fun normalizeSpelledDurations(input: String): String {
        if (input.isBlank()) return input
        var text = Normalizer.normalize(input, Normalizer.Form.NFC)

        text = halfWordRegex.replace(text, "30 phút")

        text = hourRuoiRegex.replace(text) { m ->
            val rawNum = m.groupValues[1].trim()
            if (rawNum.isNotEmpty() && prevWordBefore(text, m.range.first) in ordinalGuardWords) {
                return@replace m.value
            }
            val prefix = text.substring(0, m.range.first)
            val isClockPoint = clockPointPrefixRegex.containsMatchIn(prefix)
            if (rawNum.isEmpty()) {
                if (isClockPoint) "1 giờ 30" else "90 phút"
            } else {
                val numDouble = rawNum.replace(',', '.').toDoubleOrNull()
                    ?: parseViNumberPhrase(rawNum)?.toDouble()
                    ?: return@replace m.value
                if (isClockPoint && numDouble == numDouble.toInt().toDouble()) {
                    "${numDouble.toInt()} giờ 30"
                } else {
                    "${(numDouble * 60.0 + 30.0).roundToInt()} phút"
                }
            }
        }

        text = hourLetterRuoiRegex.replace(text) { m ->
            val n = m.groupValues[1].toInt()
            val isClockPoint = clockPointPrefixRegex.containsMatchIn(text.substring(0, m.range.first)) ||
                dayPeriodNextRegex.containsMatchIn(text.substring(m.range.last + 1))
            if (isClockPoint) "${n}h30" else "${n * 60 + 30} phút"
        }

        text = decimalWordDurationRegex.replace(text) { m ->
            if (prevWordBefore(text, m.range.first) in ordinalGuardWords) return@replace m.value
            val whole = parseViNumberPhrase(m.groupValues[1]) ?: return@replace m.value
            val frac = parseViNumberPhrase(m.groupValues[2]) ?: return@replace m.value
            if (frac !in 0..9) return@replace m.value
            val unit = m.groupValues[3].lowercase(Locale("vi", "VN"))
            "$whole.$frac $unit"
        }

        text = wordUnitDurationRegex.replace(text) { m ->
            if (prevWordBefore(text, m.range.first) in ordinalGuardWords) return@replace m.value
            val rawUnit = m.groupValues[2].lowercase(Locale("vi", "VN"))
            if (rawUnit == "tiếng" && nextWordAfter(text, m.range.last + 1) in nonDurationAfterTiengWords) {
                return@replace m.value
            }
            val value = parseViNumberPhrase(m.groupValues[1]) ?: return@replace m.value
            if (value <= 0) return@replace m.value
            val canonicalUnit = if (rawUnit.startsWith("phút")) "phút" else "giờ"
            "$value $canonicalUnit"
        }

        text = trailingWordMinutesAfterHourRegex.replace(text) { m ->
            val mins = parseViNumberPhrase(m.groupValues[1])
            if (mins != null && mins in 10..59) "$mins phút" else m.value
        }

        return text
    }

    // ---- v1.0.13 (C2/C3): người dùng gõ KHÔNG dấu ("nghi luc 3h chieu 2 tieng", "hoc bai tu 15h den gio") ----
    private val plainWord = Rx.uni("""[A-Za-z]+""")
    private val plainFixed = mapOf(
        "mot" to "một", "bon" to "bốn", "nam" to "năm", "tam" to "tám", "chin" to "chín", "muoi" to "mười",
        "tram" to "trăm", "lam" to "lăm", "ruoi" to "rưỡi", "nua" to "nửa", "gio" to "giờ", "tieng" to "tiếng", "phut" to "phút"
    )
    private val plainTensAfterDigit = Rx.uni("""(hai|ba|bốn|năm|sáu|bảy|tám|chín)\s+mười""")
    private val plainClockBefore = Rx.ascii("""(\d{1,2})\s*(?:h|:)\s*\d{0,2}\s*$""", RegexOption.IGNORE_CASE)
    private val plainClockAfter = Rx.ascii("""^\s*(?:luc\s+)?\d{1,2}\s*(?:h|:)""", RegexOption.IGNORE_CASE)
    private val plainEndAfter = Rx.ascii("""^\s*(?:gio|bay\s+gio|nay|luc\s+nay)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val plainNowAfter = Rx.ascii("""^\s*(?:gio|bay\s+gio|luc\s+nay)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val plainDigitOrNayAfter = Rx.ascii("""^\s*(?:\d|nay(?![A-Za-z]))""", RegexOption.IGNORE_CASE)
    private val plainGioAfter = Rx.ascii("""^\s+gio(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val plainGioNayBefore = Rx.ascii("""(?:gio|nay)\s+$""", RegexOption.IGNORE_CASE)
    private val plainLucGioBefore = Rx.ascii("""(?:luc|gio)\s+$""", RegexOption.IGNORE_CASE)
    private val plainFromBefore = Rx.ascii("""(?:^|\s)tu\s+[^,;.!?]*$""", RegexOption.IGNORE_CASE)

    /**
     * Khôi phục dấu cho số, đơn vị giờ và các từ chỉ mốc giờ để dùng chung bộ đọc thời lượng/giờ ở trên. Chỉ khi CẢ câu không có ký tự ngoài ASCII
     * (câu đã có dấu được giữ nguyên nên hành vi cũ không đổi). Từ nhập nhằng ("toi" = tôi/tối/tới, "den", "tu", "sang"...) chỉ được khôi phục
     * khi ngữ cảnh (đứng cạnh một giờ đồng hồ) quyết định rõ nghĩa; "sau" (sáu) và "toi nay" (tối nay) cố ý không đổi: thiếu chứ không thừa.
     */
    internal fun restoreUnaccented(text: String): String {
        if (text.any { it.code > 127 }) return text
        val words = plainWord.replace(text) { m ->
            val key = m.value.lowercase(Locale.ROOT)
            plainFixed[key] ?: run {
                val before = text.substring(0, m.range.first)
                val after = text.substring(m.range.last + 1)
                val clockNear = plainClockBefore.containsMatchIn(before) || plainClockAfter.containsMatchIn(after)
                val hourBefore = plainClockBefore.find(before)?.groupValues?.get(1)?.toIntOrNull()
                when (key) {
                    "luc" -> if (plainDigitOrNayAfter.containsMatchIn(after)) "lúc" else m.value
                    "nay" -> if (plainLucGioBefore.containsMatchIn(before)) "này" else m.value
                    // đuôi của "đến giờ rồi / đến nay luôn" (xem AiProposalGuard.OPEN_END_FILLER)
                    "roi" -> if (plainGioNayBefore.containsMatchIn(before)) "rồi" else m.value
                    "luon" -> if (plainGioNayBefore.containsMatchIn(before)) "luôn" else m.value
                    "bay" -> if (plainGioAfter.containsMatchIn(after)) "bây" else m.value
                    "tu" -> if (plainClockAfter.containsMatchIn(after)) "từ" else m.value
                    "den" -> if (plainClockAfter.containsMatchIn(after) || plainEndAfter.containsMatchIn(after)) "đến" else m.value
                    "toi" -> when {
                        plainNowAfter.containsMatchIn(after) -> "tới"
                        // "từ 19h toi 21h", "19h toi 21h" (giờ 24h đã rõ buổi) là mốc kết thúc; "10h toi 7h" là buổi tối + thời lượng.
                        plainClockAfter.containsMatchIn(after) && (plainFromBefore.containsMatchIn(before) || (hourBefore ?: 0) > 12) -> "tới"
                        hourBefore != null -> "tối"
                        else -> m.value
                    }
                    "sang" -> if (clockNear) "sáng" else m.value
                    "trua" -> if (clockNear) "trưa" else m.value
                    "chieu" -> if (clockNear) "chiều" else m.value
                    "dem" -> if (clockNear) "đêm" else m.value
                    else -> m.value
                }
            }
        }
        return plainTensAfterDigit.replace(words) { it.groupValues[1] + " mươi" }
    }

    // "30 hay 40 phút", "1 hoặc 2 tiếng": hai số dùng chung MỘT đơn vị => viết lại thành "30 phút hay 40 phút" (cả hai đều là số người dùng đã viết).
    private val sharedUnitAlternativesRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))(\d+(?:[.,]\d+)?|$viWordTokenPattern(?:\s+$viWordTokenPattern){0,3})\s+(?:hay|hoặc)(?:\s+là)?\s+(\d+(?:[.,]\d+)?|$viWordTokenPattern(?:\s+$viWordTokenPattern){0,3})\s*(giờ|tiếng|phút|h|p|min|mins)(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )

    internal fun expandSharedUnitAlternatives(text: String): String =
        sharedUnitAlternativesRegex.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[3]} hay ${m.groupValues[2]} ${m.groupValues[3]}" }

    private val clockWithPeriodRegex = Rx.uni(
        """(?:^|(?<=[\s,.;:!?()]))(\d{1,2})\s*(?:h|:)\s*(\d{0,2})\s*(sáng|trưa|chiều|tối|đêm)(?=$|[\s,.;:!?()])""",
        RegexOption.IGNORE_CASE
    )

    private fun hmMinutes(hm: String): Int = hm.substring(0, 2).toInt() * 60 + hm.substring(3, 5).toInt()

    /**
     * Số phút mà các mốc giờ người dùng đã viết suy ra CHÍNH XÁC (không đoán): độ dài và hai đầu (theo 24h, đã tính buổi) của mỗi khoảng
     * "từ 8h đến 9h30" / "2h-4h chiều", và giờ có buổi ("7h tối" = 19:00).
     */
    internal fun userClockMinutes(text: String): Set<Int> {
        val t = normalizeSpelledDurations(restoreUnaccented(text)).trim()
        if (t.isEmpty()) return emptySet()
        val out = mutableSetOf<Int>()
        for (seg in splitSegments(t)) {
            extractCrossDayRange(seg)?.range?.let { r ->
                out += r.durationMinutes; out += hmMinutes(r.startLocal); out += hmMinutes(r.endLocal)
            }
            extractTimeRangeDirect(seg)?.let { r -> out += r.durationMinutes; out += hmMinutes(r.startLocal); out += hmMinutes(r.endLocal) }
            out += extractCrossDayClockCandidateMinutes(seg)
        }
        for (m in clockWithPeriodRegex.findAll(t)) {
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h in 0..23 && min in 0..59) out += shiftHourForPeriod(h, m.groupValues[3].lowercase(Locale("vi", "VN"))) * 60 + min
        }
        return out
    }

    internal fun hasUnnormalizedWordDuration(raw: String): Boolean {
        val norm = normalizeSpelledDurations(raw)
        return residualWordDurationRegex.findAll(norm).any { m ->
            !(m.value.lowercase(Locale("vi", "VN")).contains("tiếng") &&
                nextWordAfter(norm, m.range.last + 1) in nonDurationAfterTiengWords)
        }
    }

    internal fun unresolvableNegationReason(raw: String): String? {
        val norm = normalizeSpelledDurations(raw).trim()
        if (norm.isEmpty()) return null

        for (seg in splitSegments(norm)) {
            val stripped = stripNonStopPhrase(seg)
            for (clause in contrastClauseRegex.split(stripped)) {
                val cues = negationCueRegex.findAll(clause).toList()
                if (cues.isEmpty()) continue
                val vagueNegated = vagueNegatedDurationRegex.findAll(clause).any { vm ->
                    cues.any { cue -> cue.range.first < vm.range.first }
                }
                val residualNegated = if (firstDurationIndex(clause) == null) {
                    residualWordDurationRegex.findAll(clause).any { rm ->
                        val isNonDurationTieng = rm.value.lowercase(Locale("vi", "VN")).contains("tiếng") &&
                            nextWordAfter(clause, rm.range.last + 1) in nonDurationAfterTiengWords
                        !isNonDurationTieng && cues.any { cue -> cue.range.first < rm.range.first }
                    }
                } else {
                    false
                }
                if (vagueNegated || residualNegated) {
                    return "Câu có phủ định với thời lượng chưa rõ ràng nên chưa tạo bản ghi. Hãy nêu rõ thời lượng hoặc từng hoạt động bạn muốn ghi."
                }
            }
        }

        for (sentence in norm.split(Rx.ascii("""[.;!?\n]+"""))) {
            for (contrastChunk in contrastClauseRegex.split(sentence)) {
                for (commaChunk in contrastChunk.split(',')) {
                    val clean = stripNonStopPhrase(commaChunk).trim()
                    if (clean.isEmpty() || !negationCueRegex.containsMatchIn(clean)) continue
                    val connector = coordConnectorRegex.find(clean) ?: continue
                    val before = clean.substring(0, connector.range.first).trim()
                    val after = clean.substring(connector.range.last + 1).trim()
                    val beforeCue = negationCueRegex.find(before)
                    if (beforeCue != null && !hasDurationDirect(before) && hasDurationDirect(after)) {
                        val hasAffirmativeShift = Rx.uni(
                            """(?:^|\s)(?:hôm\s+nay|sáng\s+nay|trưa\s+nay|chiều\s+nay|tối\s+nay|sau\s+đó|rồi\s+thì|riêng|chỉ|vẫn|đã)(?=\s|$)""",
                            RegexOption.IGNORE_CASE
                        ).containsMatchIn(after)
                        val endsWithNegParticle = Rx.uni("""(?:^|\s)(?:đâu|nữa|cả|hết|được)(?=$|\s)""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(after)
                        if (!hasAffirmativeShift || endsWithNegParticle) {
                            return "Câu có từ phủ định nối chung thời lượng bằng \"và\"/\"hay\" nên chưa rõ phần nào được ghi. Hãy tách thành câu riêng hoặc dùng \"nhưng\" để làm rõ."
                        }
                    }
                }
            }
        }
        return null
    }

    private fun hasDurationDirect(text: String): Boolean =
        extractTimeRangeDirect(text) != null || extractDurationMinutesDirect(text) != null

    private fun firstDurationIndex(clause: String): Int? = listOfNotNull(
        timeRangeRegex.find(clause)?.range?.first,
        hoursRegex.find(clause)?.range?.first,
        minutesRegex.find(clause)?.range?.first
    ).minOrNull()

    private fun hasDuration(text: String): Boolean =
        hasDurationDirect(normalizeSpelledDurations(text))

    /** Đoạn có từ phủ định đứng trước thời lượng trong cùng một mệnh đề. */
    internal fun isNegatedDurationSegment(segment: String): Boolean {
        val s = stripNonStopPhrase(normalizeSpelledDurations(segment))
        return contrastClauseRegex.split(s).any { clause ->
            val dIdx = firstDurationIndex(clause)
            val cue = negationCueRegex.find(clause)
            dIdx != null && cue != null && cue.range.first < dIdx
        }
    }

    internal fun isCorrectionWithDuration(text: String): Boolean =
        correctionRegex.containsMatchIn(text) && hasDuration(text)

    /** Mệnh đề phủ định mang thời lượng (category + phút), dùng cho guard đề xuất của AI. */
    data class NegatedClause(val category: String, val minutes: Int)

    internal fun negatedClauses(text: String): List<NegatedClause> =
        durationClauses(text).filter { it.negated }.map { NegatedClause(classifyCategory(it.text), it.minutes) }

    /**
     * WALLY v1.0.6 (F-V105-03): mọi mệnh đề mang thời lượng, kèm cực (phủ định/khẳng định) và nguyên văn mệnh đề,
     * để guard đối chiếu đề xuất của AI theo BẰNG CHỨNG VĂN BẢN chứ không chỉ theo nhãn danh mục.
     */
    data class DurationClause(val minutes: Int, val negated: Boolean, val text: String)

    internal fun durationClauses(text: String): List<DurationClause> {
        val out = mutableListOf<DurationClause>()
        val trimmed = normalizeSpelledDurations(text).trim()
        if (trimmed.isEmpty()) return out
        for (segment in splitSegments(trimmed).flatMap { expandContrastSegment(it) }) {
            val stripped = stripNonStopPhrase(segment)
            for (clause in contrastClauseRegex.split(stripped)) {
                val dIdx = firstDurationIndex(clause) ?: continue
                val minutes = extractTimeRangeDirect(clause)?.durationMinutes ?: extractDurationMinutesDirect(clause) ?: continue
                val cue = negationCueRegex.find(clause)
                out += DurationClause(minutes, negated = cue != null && cue.range.first < dIdx, text = clause.trim())
            }
        }
        return out
    }

    /** Mọi thời lượng (phút) xuất hiện trong câu, theo từng mệnh đề. */
    internal fun allDurationMinutes(text: String): List<Int> {
        val trimmed = normalizeSpelledDurations(text).trim()
        if (trimmed.isEmpty()) return emptyList()
        return splitSegments(trimmed).flatMap { expandContrastSegment(it) }
            .flatMap { contrastClauseRegex.split(it) }
            .mapNotNull { clause ->
                if (findAmbiguousCrossDayClock(clause) != null) return@mapNotNull null
                extractCrossDayRange(clause)?.range?.durationMinutes
                    ?: extractTimeRangeDirect(clause)?.durationMinutes
                    ?: extractDurationMinutesDirect(clause)
            }
    }

    /** Dùng cho đường cloud: câu có phủ định/sửa lại kèm thời lượng thì không tin đề xuất của AI. */
    fun hasNegatedOrCorrectedDuration(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (isCorrectionWithDuration(trimmed)) return true
        if (CancelCommandParser.parse(trimmed) != null) return true
        return splitSegments(trimmed).any { isNegatedDurationSegment(it) }
    }

    /** Đoạn có mệnh đề tương phản ("Không học nhưng code 2h"): chỉ dùng mệnh đề mang thời lượng làm tiêu đề/danh mục. */
    private fun durationClauseOf(raw: String): String {
        val withDuration = contrastClauseRegex.split(raw).filter { hasDuration(it) }
        return if (withDuration.size == 1) withDuration[0].trim() else raw
    }

    /**
     * AI-01 CLOSURE (F4): "Không học 2h nhưng code 1h" có hai mệnh đề đều mang thời lượng; tách ra để mệnh đề
     * phủ định bị bỏ riêng và mệnh đề hợp lệ vẫn được giữ (không im lặng bỏ cả đoạn).
     */
    private fun expandContrastSegment(segment: String): List<String> {
        val withDuration = contrastClauseRegex.split(segment).map { it.trim() }.filter { it.isNotEmpty() && hasDuration(it) }
        return if (withDuration.size >= 2) withDuration else listOf(segment)
    }

    /** Bỏ các cụm thời lượng/giờ khỏi chuỗi (dùng để lấy phần mô tả hoạt động). */
    internal fun stripDurationText(text: String): String {
        var t = timeRangeRegex.replace(normalizeSpelledDurations(text), " ")
        t = explicitStartAtHourRegex.replace(t, " ")
        t = Rx.ascii("""\d+(?:[.,]\d+)?\s*(?:h\s*tiếng|giờ|tiếng|h\d{0,2}\b)""", RegexOption.IGNORE_CASE).replace(t, " ")
        t = minutesRegex.replace(t, " ")
        return t.replace(Rx.ascii("""\s+"""), " ").trim()
    }

    private fun splitSegments(trimmed: String): List<String> = trimmed
        .replace(clauseConnectorRegex, ", ")
        .split(Rx.ascii("""[,;\n]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    // AI-01 (Finding C): dấu ':' kết thúc tiền tố ngữ cảnh — bỏ qua ':' nằm giữa hai chữ số (giờ 13:00).
    private val headerColonRegex = Rx.ascii("""(?<!\d):|:(?!\d)""")

    /**
     * Tiền tố ngữ cảnh ngày của cả câu: phần trước dấu ':' đầu tiên nếu phần đó không chứa dấu tách mệnh đề
     * (ví dụ "Nhật ký ngày hôm qua: ..."); nếu không có tiền tố hợp lệ thì dùng 25 ký tự đầu như cũ
     * (để "Hôm qua học 1h, đọc 30p" vẫn áp dụng cho mọi đoạn).
     */
    internal fun dateContextPrefix(trimmed: String): String {
        val colon = headerColonRegex.find(trimmed)
        if (colon != null) {
            val header = trimmed.substring(0, colon.range.first)
            if (header.isNotBlank() && !header.contains(Rx.ascii("""[,;
]"""))) return header
        }
        return trimmed.take(25)
    }

    // AI-01 (AM/PM): buổi trong ngày đi kèm giờ ("2h chiều", "10h tối"). "đêm qua" là mốc ngày, không phải buổi.
    private val dayPeriodRegex = Rx.uni(
        """(?:^|\s)(sáng|trưa|chiều|tối|đêm)(?!\s+qua)(?=\s|$|[,.;:])""",
        RegexOption.IGNORE_CASE
    )

    private fun detectDayPeriod(segment: String): String? =
        dayPeriodRegex.find(segment)?.groupValues?.get(1)?.lowercase(Locale("vi", "VN"))

    /** Quy đổi giờ 12h theo buổi sang 24h. Chỉ đổi khi giờ nằm trong khoảng mơ hồ hợp lý; còn lại giữ nguyên. */
    internal fun shiftHourForPeriod(hour: Int, period: String?): Int = when (period?.lowercase(Locale("vi", "VN"))) {
        "chiều", "tối", "pm" -> if (hour in 1..11) hour + 12 else hour
        "trưa" -> if (hour in 1..4) hour + 12 else hour
        "đêm", "khuya" -> when (hour) {
            in 6..11 -> hour + 12
            12 -> 0
            else -> hour
        }
        "sáng", "am" -> if (hour == 12) 0 else hour
        else -> hour
    }

    fun classifyCategory(text: String): String {
        val normalized = text.lowercase(Locale("vi", "VN"))
        for ((key, regex) in categoryRules) {
            if (regex.containsMatchIn(normalized)) {
                return key
            }
        }
        return "other"
    }

    data class ExtractedRange(
        val startLocal: String,
        val endLocal: String,
        val durationMinutes: Int
    )

    internal data class CrossDayRangeMatch(
        val range: ExtractedRange,
        val startDayOffset: Int,
        val exceeds24Hours: Boolean
    )

    private data class CrossDayInspect(
        val matchResult: MatchResult,
        val resolved: CrossDayRangeMatch?,
        val ambiguousToken: String?,
        val candidateMinutes: Set<Int>
    )

    private fun dayOffsetOf(raw: String?): Int? {
        val t = raw?.trim()?.lowercase(Locale("vi", "VN"))?.replace(Rx.ascii("""\s+"""), " ") ?: return null
        if (t.isEmpty()) return null
        return when {
            t.contains("hôm kia") -> -2
            t.contains("hôm qua") || t.contains("đêm qua") -> -1
            t.contains("ngày mai") || t == "mai" -> 1
            t.contains("nay") -> 0
            else -> null
        }
    }

    private fun hasLeadingZeroClock(text: String, groupRange: IntRange): Boolean {
        val rawHour = text.substring(groupRange.first, groupRange.last + 1)
        return rawHour.length == 2 && rawHour.startsWith("0")
    }

    private fun inspectCrossDayRange(segment: String): CrossDayInspect? {
        val norm = normalizeSpelledDurations(restoreUnaccented(segment))
        val match = crossDayRangeRegex.find(norm) ?: return null
        val h1 = match.groupValues[1].toIntOrNull() ?: return null
        val m1 = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        val h2 = match.groupValues[6].toIntOrNull() ?: return null
        val m2 = match.groupValues[7].ifBlank { "0" }.toIntOrNull() ?: return null
        if (h1 !in 0..23 || h2 !in 0..23 || m1 !in 0..59 || m2 !in 0..59) return null

        val period1 = match.groupValues[4].lowercase(Locale("vi", "VN")).ifBlank { null }
        val dayCue1 = match.groupValues[5].ifBlank { null }
        val period2 = match.groupValues[9].lowercase(Locale("vi", "VN")).ifBlank { null }
        val dayCue2 = match.groupValues[10].ifBlank { null }

        val offset1 = dayOffsetOf(dayCue1)
        val offset2 = dayOffsetOf(dayCue2)
        val nightPeriods = setOf("tối", "đêm", "khuya")

        val isCrossDay = when {
            offset1 != null && offset2 != null -> offset2 > offset1
            offset1 != null && offset1 < 0 && offset2 == null ->
                (period1 in nightPeriods) || (h1 >= 13) || (period2 == "sáng") || (h2 < h1)
            offset1 == null && offset2 == null ->
                (period1 in nightPeriods) && (period2 == "sáng")
            else -> false
        }
        if (!isCrossDay) return null

        val effectiveStartOffset = offset1 ?: -1
        val effectiveEndOffset = offset2 ?: if (offset1 != null) offset1 + 1 else 0
        val dayDiff = (effectiveEndOffset - effectiveStartOffset).coerceAtLeast(1)

        val g1 = match.groups[1]?.range
        val g6 = match.groups[6]?.range
        val explicit24Start = h1 == 0 || h1 >= 13 || (g1 != null && hasLeadingZeroClock(norm, g1))
        val explicit24End = h2 == 0 || h2 >= 13 || (g6 != null && hasLeadingZeroClock(norm, g6))

        val startAmbiguous = period1 == null && !explicit24Start && h1 in 1..12
        val endAmbiguous = period2 == null && !explicit24End && h2 in 1..12

        val candidates = mutableSetOf<Int>()
        fun addClockCandidates(h: Int, m: Int, p: String?) {
            if (p != null) {
                candidates += shiftHourForPeriod(h, p) * 60 + m
            } else if (h in 1..12) {
                candidates += (h % 12) * 60 + m
                candidates += ((h % 12) + 12) * 60 + m
            } else {
                candidates += h * 60 + m
            }
        }
        addClockCandidates(h1, m1, period1)
        addClockCandidates(h2, m2, period2)

        if (startAmbiguous || endAmbiguous) {
            val rawHourText = if (startAmbiguous) {
                "${h1}h" + if (m1 > 0) m1.toString().padStart(2, '0') else ""
            } else {
                "${h2}h" + if (m2 > 0) m2.toString().padStart(2, '0') else ""
            }
            return CrossDayInspect(match, resolved = null, ambiguousToken = rawHourText, candidateMinutes = candidates)
        }

        val startHour = shiftHourForPeriod(h1, period1)
        val endHour = shiftHourForPeriod(h2, period2)
        val totalMinutes = dayDiff * 1440 + (endHour * 60 + m2) - (startHour * 60 + m1)
        if (totalMinutes <= 0) return null

        val exceeds24 = totalMinutes > 1440
        val startHm = "%02d:%02d".format(startHour, m1)
        val endHm = "%02d:%02d".format(endHour, m2)
        return CrossDayInspect(
            matchResult = match,
            resolved = CrossDayRangeMatch(
                range = ExtractedRange(startHm, endHm, totalMinutes.coerceAtMost(1440)),
                startDayOffset = effectiveStartOffset,
                exceeds24Hours = exceeds24
            ),
            ambiguousToken = null,
            candidateMinutes = candidates + totalMinutes
        )
    }

    internal fun extractCrossDayRange(segment: String): CrossDayRangeMatch? =
        inspectCrossDayRange(segment)?.resolved

    internal fun findAmbiguousCrossDayClock(text: String): String? {
        for (seg in splitSegments(normalizeSpelledDurations(restoreUnaccented(text)))) {
            inspectCrossDayRange(seg)?.ambiguousToken?.let { return it }
        }
        return null
    }

    internal fun extractCrossDayClockCandidateMinutes(segment: String): Set<Int> =
        inspectCrossDayRange(segment)?.candidateMinutes ?: emptySet()

    fun extractTimeRange(segment: String): ExtractedRange? =
        extractCrossDayRange(segment)?.takeUnless { it.exceeds24Hours }?.range
            ?: extractTimeRangeDirect(normalizeSpelledDurations(segment))

    private fun extractTimeRangeDirect(segment: String): ExtractedRange? {
        val match = timeRangeRegex.find(segment) ?: inlineActivityTimeRangeRegex.find(segment) ?: return null
        val isInline = match.groupValues.size >= 8
        val h1 = match.groupValues[1].toIntOrNull() ?: return null
        val m1 = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        val period1 = match.groupValues[3].lowercase(Locale("vi", "VN")).ifBlank { null }
        val h2Idx = if (isInline) 5 else 4
        val h2 = match.groupValues[h2Idx].toIntOrNull() ?: return null
        val m2 = match.groupValues[h2Idx + 1].ifBlank { "0" }.toIntOrNull() ?: return null
        val period2 = match.groupValues[h2Idx + 2].lowercase(Locale("vi", "VN")).ifBlank { null }

        if (h1 !in 0..23 || h2 !in 0..23 || m1 !in 0..59 || m2 !in 0..59) return null
        val fallbackPeriod = if (period1 == null && period2 == null) detectDayPeriod(segment) else null
        val effPeriod2 = period2 ?: fallbackPeriod
        val effPeriod1 = period1 ?: effPeriod2

        val endHour: Int
        val startHour: Int
        if (period1 != null && period2 != null) {
            startHour = shiftHourForPeriod(h1, period1)
            endHour = shiftHourForPeriod(h2, period2)
        } else if (period1 != null) {
            startHour = shiftHourForPeriod(h1, period1)
            endHour = if (h2 in 1..11 && period1 in setOf("chiều", "tối") && h2 + 12 > startHour) {
                h2 + 12
            } else {
                h2
            }
        } else {
            val shiftedEnd = shiftHourForPeriod(h2, effPeriod2)
            val shiftedStart = shiftHourForPeriod(h1, effPeriod1)
            // Chỉ đổi giờ bắt đầu khi giờ kết thúc đã đổi và khoảng vẫn tăng dần ("2h-4h chiều" => 14-16, "10h-2h chiều" => 10-14)
            startHour = if (shiftedEnd != h2 && shiftedStart < shiftedEnd) shiftedStart else h1
            endHour = shiftedEnd
        }

        val startTotal = startHour * 60 + m1
        val endTotal = endHour * 60 + m2
        var diff = endTotal - startTotal
        if (diff <= 0) diff += 1440

        return ExtractedRange(
            startLocal = "${startHour.toString().padStart(2, '0')}:${m1.toString().padStart(2, '0')}",
            endLocal = "${endHour.toString().padStart(2, '0')}:${m2.toString().padStart(2, '0')}",
            durationMinutes = diff
        )
    }

    private fun extractExplicitPointStartRange(segment: String, durationMinutes: Int): ExtractedRange? {
        val norm = normalizeSpelledDurations(segment)
        val match = explicitStartAtHourRegex.find(norm) ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val m1 = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        if (rawHour !in 0..23 || m1 !in 0..59) return null
        val h1 = shiftHourForPeriod(rawHour, detectDayPeriod(norm))

        val startTime = LocalTime.of(h1, m1)
        val endTime = startTime.plusMinutes(durationMinutes.toLong())
        return ExtractedRange(
            startLocal = "${startTime.hour.toString().padStart(2, '0')}:${startTime.minute.toString().padStart(2, '0')}",
            endLocal = "${endTime.hour.toString().padStart(2, '0')}:${endTime.minute.toString().padStart(2, '0')}",
            durationMinutes = durationMinutes
        )
    }

    fun extractDurationMinutes(segment: String): Int? =
        extractDurationMinutesDirect(normalizeSpelledDurations(segment))

    private fun extractDurationMinutesDirect(segment: String): Int? {
        // Nếu cả mệnh đề là "<giờ 1> <hoạt động> đến <giờ 2>" (như "9h làm việc đến 20h"), thời lượng phải đến từ khoảng giờ (11h),
        // tránh cắt "đến 20h" rồi đọc nhầm mốc bắt đầu "9h" thành thời lượng 9 tiếng.
        if (inlineActivityTimeRangeRegex.containsMatchIn(segment)) return null
        val cleaned = clockAfterFromRegex.replace(explicitStartAtHourRegex.replace(segment, " "), " ")
        // v1.0.12: "Ngày mai 9h họp 1 tiếng": "9h" trần là mốc giờ, thời lượng là "1 tiếng". Ưu tiên cụm có đơn vị (giờ/tiếng/h tiếng);
        // chỉ khi không có cụm nào như vậy thì "2h" trần mới là thời lượng ("Học 2h").
        val hourMatches = hoursRegex.findAll(cleaned).toList()
        val hoursMatch = hourMatches.firstOrNull { !it.value.trim().endsWith("h", ignoreCase = true) } ?: hourMatches.firstOrNull()
        val minsMatch = minutesRegex.find(cleaned)
        if (hoursMatch == null && minsMatch == null) return null

        var extraMinutesAfterH = 0
        if (hoursMatch != null && minsMatch == null && hoursMatch.value.trim().endsWith("h", ignoreCase = true)) {
            val afterIndex = hoursMatch.range.last + 1
            if (afterIndex < cleaned.length) {
                val remainder = cleaned.substring(afterIndex)
                val trailingDigits = Rx.ascii("""^(\d{1,2})(?!\d)""").find(remainder)
                if (trailingDigits != null) {
                    extraMinutesAfterH = trailingDigits.groupValues[1].toIntOrNull() ?: 0
                }
            }
        }

        val hoursVal = hoursMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        val minsVal = minsMatch?.groupValues?.get(1)?.toIntOrNull() ?: extraMinutesAfterH

        val total = (hoursVal * 60.0 + minsVal).roundToInt()
        return if (total in 1..1440) total else null
    }

    fun parse(
        inputText: String,
        today: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now()
    ): ParseChatOutcome {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) {
            return ParseChatOutcome(emptyList(), listOf("Hãy nhập hoạt động và thời lượng."))
        }

        // Tách theo dấu phẩy, chấm phẩy, xuống dòng, hoặc liên từ nối giữa các mệnh đề có thời lượng
        val rawSegments = splitSegments(trimmed)

        val warnings = mutableListOf<String>()
        if (rawSegments.size > 15) {
            warnings.add("Tối đa 15 hoạt động mỗi lần phân tích.")
            return ParseChatOutcome(emptyList(), warnings)
        }

        // Kiểm tra ngữ cảnh ngày ở cấp toàn câu (ví dụ "Hôm qua: học 45p, chạy bộ 30p...")
        val contextPrefix = dateContextPrefix(trimmed)
        val globalTomorrow = Rx.uni("""(?:^|\s)(?:ngày mai|mai)(?=\s|$|:|,)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(contextPrefix)
        val globalYesterday = Rx.uni("""(?:^|\s)(?:hôm qua|đêm qua|yesterday)(?=\s|$|:|,)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(contextPrefix)

        val proposals = mutableListOf<ActivityProposal>()

        // AI-01 CLOSURE (F1): lệnh hủy ("Hủy ghi nhận 30 phút đọc sách") không phải hoạt động mới.
        if (CancelCommandParser.parse(trimmed) != null) {
            warnings.add("Đây là lệnh hủy đề xuất, không phải hoạt động mới nên không tạo bản ghi.")
            return ParseChatOutcome(emptyList(), warnings)
        }

        // AI-01 FINAL FIX: câu sửa lại thời lượng => hỏi lại, không tạo proposal nào.
        if (isCorrectionWithDuration(trimmed)) {
            warnings.add(
                "Câu có vẻ đang sửa lại thời lượng hoặc hoạt động trước đó nên chưa tạo bản ghi nào. " +
                    "Hãy nhập lại một câu rõ ràng, ví dụ \"Học 3h\"."
            )
            return ParseChatOutcome(emptyList(), warnings)
        }

        for (segment in rawSegments.flatMap { expandContrastSegment(it) }) {
            if (isNegatedDurationSegment(segment)) {
                warnings.add("Đã bỏ qua mệnh đề phủ định / yêu cầu không ghi (không tạo bản ghi): \"${segment.take(35)}\"")
                continue
            }
            val raw = durationClauseOf(segment)
            if (isAggregateClause(raw)) {
                warnings.add("Đã bỏ qua mệnh đề tổng hợp (không ghi như một hoạt động): \"${raw.take(35)}\"")
                continue
            }
            val ambiguousClock = findAmbiguousCrossDayClock(raw)
            if (ambiguousClock != null) {
                warnings.add("Thời điểm \"$ambiguousClock\" chưa rõ sáng hay tối nên chưa tự suy đoán. Hãy nói rõ buổi (ví dụ 20h tối hoặc 8h sáng).")
                continue
            }
            val crossDay = extractCrossDayRange(raw)
            if (crossDay != null && crossDay.exceeds24Hours) {
                warnings.add("Bỏ qua đoạn có khoảng giờ vượt 24 giờ: \"${raw.take(35)}\"")
                continue
            }
            val explicitRange = crossDay?.range ?: extractTimeRange(raw)
            val explicitMinutes = extractDurationMinutes(raw)
            val durationMinutes = explicitRange?.durationMinutes ?: explicitMinutes

            if (durationMinutes == null || durationMinutes !in 1..1440) {
                warnings.add("Bỏ qua đoạn thiếu thời lượng: \"${raw.take(35)}\"")
                continue
            }

            val isTomorrow = globalTomorrow || (crossDay == null && Rx.uni("""(?:^|\s)(?:ngày mai|mai)(?=\s|$)""", RegexOption.IGNORE_CASE).containsMatchIn(raw))
            val isYesterday = crossDay?.startDayOffset == -1 || globalYesterday || (crossDay == null && Rx.uni("""(?:^|\s)(?:hôm qua|đêm qua|yesterday)(?=\s|$)""", RegexOption.IGNORE_CASE).containsMatchIn(raw))
            val isDayBeforeYesterday = crossDay?.startDayOffset == -2 || Rx.uni("""(?:^|\s)hôm kia(?=\s|$)""", RegexOption.IGNORE_CASE).containsMatchIn(raw)

            val targetDate = when {
                crossDay != null -> today.plusDays(crossDay.startDayOffset.toLong())
                isTomorrow -> today.plusDays(1)
                isYesterday -> today.minusDays(1)
                isDayBeforeYesterday -> today.minusDays(2)
                else -> today
            }

            val resolvedRange = explicitRange ?: extractExplicitPointStartRange(raw, durationMinutes)
            val hasRange = resolvedRange != null

            val isFutureTimeToday = if (!isTomorrow && !isYesterday && !isDayBeforeYesterday && resolvedRange != null) {
                try {
                    LocalTime.parse(resolvedRange.startLocal).isAfter(nowTime)
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }

            val cleanedTitle = cleanTitle(raw)

            proposals.add(
                ActivityProposal(
                    id = "prop_${UUID.randomUUID()}",
                    rawInput = raw,
                    title = cleanedTitle,
                    category = classifyCategory(stripNonStopPhrase(raw)),
                    durationSeconds = durationMinutes * 60L,
                    dateLocal = targetDate.toString(),
                    startLocal = resolvedRange?.startLocal,
                    endLocal = resolvedRange?.endLocal,
                    timeEvidence = if (hasRange) "explicit_range" else "duration_only",
                    missingFields = if (hasRange) emptyList() else listOf("startLocal", "endLocal"),
                    needsConfirmation = true,
                    isFuturePlanned = isTomorrow || isFutureTimeToday
                )
            )
        }

        return ParseChatOutcome(proposals = proposals, warnings = warnings)
    }

    private fun cleanTitle(raw: String): String {
        var text = normalizeSpelledDurations(raw)
        inlineActivityTimeRangeRegex.find(text)?.let { m ->
            val activityOnly = m.groupValues[4].trim()
            if (activityOnly.isNotEmpty()) {
                text = text.replaceRange(m.range, " $activityOnly ")
            }
        }
        text = crossDayRangeRegex.replace(text, " ")
        text = timeRangeRegex.replace(text, " ")
        text = explicitStartAtHourRegex.replace(text, " ")
        text = Rx.ascii("""\d+(?:[.,]\d+)?\s*(?:h\s*tiếng|giờ|tiếng|h\d{0,2}\b)""", RegexOption.IGNORE_CASE).replace(text, " ")
        text = minutesRegex.replace(text, " ")
        text = Rx.uni(
            """(?:^|\s)(?:tôi|mình|đã|vừa mới|vừa|mới|xong|sẽ|bây giờ|ngay bây giờ|giờ|hôm nay|hôm qua|đêm qua|hôm kia|ngày mai|mai|lúc|buổi|khoảng|trong)(?=\s|$|[,.!?;:])""",
            RegexOption.IGNORE_CASE
        ).replace(text, " ")
        text = text.replace(Rx.ascii("""\s+"""), " ").trim()
        text = text.trim(',', '.', ':', '-', '–', ';', ' ')
        if (text.isEmpty()) return "Hoạt động"
        val firstChar = text.substring(0, 1).uppercase(Locale("vi", "VN"))
        return firstChar + text.substring(1)
    }
}
