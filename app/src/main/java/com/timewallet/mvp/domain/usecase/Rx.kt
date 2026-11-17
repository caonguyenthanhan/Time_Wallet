package com.timewallet.mvp.domain.usecase

import java.util.concurrent.ConcurrentHashMap

/**
 * v1.0.9 — P0 F6 (crash trên thiết bị thật, 2026-11-14): `ExceptionInInitializerError` ← `PatternSyntaxException`
 * ← `CancelCommandParser.<clinit>` (ProposalLifecycle.kt:29).
 *
 * NGUYÊN NHÂN: Android KHÔNG dùng bộ regex của JDK. `java.util.regex.Pattern` trên Android chuyển nguyên chuỗi mẫu cho ICU4C
 * (`PatternNative.create(pattern, flags)`) và bỏ trình phân tích của JDK. Cờ nội tuyến `(?U)` (UNICODE_CHARACTER_CLASS của JDK)
 * KHÔNG phải cờ ICU (ICU chỉ có i d m s w x) nên ICU từ chối với `U_REGEX_RULE_SYNTAX` ngay tại ký tự `U`. 49 mẫu sản xuất mở đầu
 * bằng `(?U)`. Test JVM luôn qua vì JDK hiểu `(?U)`.
 *
 * QUY TẮC: mọi Regex sản xuất PHẢI được tạo qua `Rx.uni` / `Rx.ascii` (một test quét mã nguồn cấm `Regex(`, `toRegex`,
 * `Pattern.compile` ở nơi khác). Mẫu nguồn viết bằng một "phương ngữ" nhỏ; `Rx` dịch nó thành MỘT chuỗi mẫu duy nhất chỉ dùng cấu
 * trúc có nghĩa GIỐNG HỆT trên JDK và ICU, nên cùng một chuỗi chạy trên cả hai bộ máy:
 *  - `uni`  = ngữ nghĩa Unicode (trước đây là tiền tố `(?U)`);   `ascii` = ngữ nghĩa ASCII mặc định của JDK (mẫu không có `(?U)`).
 *  - `\s` `\d` `\b` `$` `.` được thay bằng lớp ký tự/khẳng định tường minh (không còn phụ thuộc cờ hay định nghĩa "kết thúc dòng" của
 *    bộ máy: ICU coi thêm U+000B/U+000C là kết thúc dòng, JDK thì không).
 *  - Cấu trúc ngoài tập con (cờ nội tuyến, nhóm đặt tên, `\w`, `&&`, `\p{Is..}`, lớp lồng nhau, `{` chưa thoát...) bị TỪ CHỐI với
 *    `IllegalArgumentException` thay vì chạy khác nhau giữa hai bộ máy.
 *
 * Ký tự không in được trong hằng số bên dưới được viết dạng `\x{hhhh}` (cả JDK lẫn ICU đều hiểu) để mã nguồn không chứa ký tự vô hình.
 */
internal object Rx {

    /** Một mẫu đã dựng: dùng cho test đối chiếu JDK-vs-ICU và cho kiểm toán. */
    class Entry(val source: String, val unicode: Boolean, val ignoreCase: Boolean, val portable: String, val regex: Regex)

    private data class Key(val source: String, val unicode: Boolean, val ignoreCase: Boolean)

    private val cache = ConcurrentHashMap<Key, Entry>()

    /** Ngữ nghĩa Unicode: `\s` = White_Space, `\d` = Nd, `\b` theo ký tự từ Unicode (như `(?U)` của JDK). */
    fun uni(pattern: String, vararg options: RegexOption): Regex = build(pattern, true, options)

    /** Ngữ nghĩa ASCII: `\s` = [ \t\n\x0B\f\r], `\d` = [0-9], `\b` theo [A-Za-z0-9_] (mặc định của JDK khi không có `(?U)`). */
    fun ascii(pattern: String, vararg options: RegexOption): Regex = build(pattern, false, options)

    /** Ảnh chụp mọi mẫu đã dựng trong tiến trình (dùng cho test/kiểm toán). */
    internal fun entries(): List<Entry> = cache.values.toList()

    private fun build(pattern: String, unicode: Boolean, options: Array<out RegexOption>): Regex {
        var ignoreCase = false
        for (o in options) {
            if (o == RegexOption.IGNORE_CASE) ignoreCase = true
            else throw IllegalArgumentException("Rx: only IGNORE_CASE is supported, got $o")
        }
        val key = Key(pattern, unicode, ignoreCase)
        cache[key]?.let { return it.regex }
        val portable = translate(pattern, unicode)
        val regex = if (ignoreCase) Regex(portable, RegexOption.IGNORE_CASE) else Regex(portable)
        return cache.getOrPut(key) { Entry(pattern, unicode, ignoreCase, portable, regex) }.regex
    }

    // White_Space của Unicode = \t \n VT \f \r NEL + \p{Z}; ASCII mặc định của JDK = [ \t\n\x0B\f\r].
    private const val WS_UNI = """\t\n\x{B}\f\r\x{85}\p{Z}"""
    private const val WS_ASCII = """ \t\n\x{B}\f\r"""

    // Ký tự từ Unicode ≈ Alphabetic | M | Nd | Pc | Join_Control (ZWNJ, ZWJ); ASCII = [A-Za-z0-9_].
    private const val WORD_UNI = """\p{L}\p{M}\p{Nd}\p{Nl}\p{Pc}\x{200C}\x{200D}"""
    private const val WORD_ASCII = "A-Za-z0-9_"

    // `$` (không MULTILINE) của JDK: khớp ở cuối chuỗi hoặc ngay trước dấu xuống dòng CUỐI (\r\n tính là một; không khớp giữa \r và \n).
    private const val LINE_END = """(?=(?:\r\n|(?<!\r)\n|[\r\x{85}\x{2028}\x{2029}])?\z)"""

    // `.` (không DOTALL, không UNIX_LINES) của JDK: mọi ký tự trừ \n \r NEL LS PS.
    private const val ANY_BUT_EOL = """[^\n\r\x{85}\x{2028}\x{2029}]"""

    private val PROPERTIES = setOf("L", "N", "Nd", "Nl", "M", "Lu", "Ll", "Z", "Pc")

    /** Độ dài của bộ lượng từ `{n}` / `{n,}` / `{n,m}` bắt đầu tại [i]; -1 nếu không phải. (Không dùng Regex để phân tích Regex.) */
    private fun quantifierLength(s: String, i: Int): Int {
        var j = i + 1
        val firstDigit = j
        while (j < s.length && s[j] in '0'..'9') j++
        if (j == firstDigit) return -1
        if (j < s.length && s[j] == ',') {
            j++
            while (j < s.length && s[j] in '0'..'9') j++
        }
        return if (j < s.length && s[j] == '}') j - i + 1 else -1
    }

    /** Phép dịch thuần (không đụng bộ máy regex nào ngoài kiểm tra bộ lượng từ); công khai trong module để test. */
    internal fun translate(src: String, unicode: Boolean): String {
        val ws = if (unicode) WS_UNI else WS_ASCII
        val digit = if (unicode) """\p{Nd}""" else "0-9"
        val word = if (unicode) WORD_UNI else WORD_ASCII
        val sb = StringBuilder(src.length + 64)
        var i = 0
        var inClass = false
        fun bad(why: String): Nothing = throw IllegalArgumentException("Rx: $why at $i in /$src/")

        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' -> {
                    if (i + 1 >= src.length) bad("dangling backslash")
                    val n = src[i + 1]
                    when {
                        n == 's' -> { sb.append(if (inClass) ws else "[$ws]"); i += 2 }
                        n == 'd' -> { sb.append(if (inClass) digit else "[$digit]"); i += 2 }
                        n == 'b' -> {
                            if (inClass) bad("\\b inside a character class")
                            sb.append("(?:(?<=[$word])(?![$word])|(?<![$word])(?=[$word]))"); i += 2
                        }
                        n == 'p' || n == 'P' -> {
                            val end = src.indexOf('}', i)
                            if (i + 2 >= src.length || src[i + 2] != '{' || end < 0) bad("malformed property escape")
                            val name = src.substring(i + 3, end)
                            if (name !in PROPERTIES) bad("property \\$n{$name} is outside the portable subset")
                            sb.append(src, i, end + 1); i = end + 1
                        }
                        n == 'n' || n == 't' || n == 'r' || n == 'f' -> { sb.append(c).append(n); i += 2 }
                        n == 'u' -> {
                            val ok = i + 6 <= src.length && src.substring(i + 2, i + 6).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                            if (!ok) bad("malformed \\u escape")
                            sb.append(src, i, i + 6); i += 6
                        }
                        n.isLetterOrDigit() -> bad("escape \\$n is outside the portable subset")
                        else -> { sb.append(c).append(n); i += 2 } // dấu câu đã thoát: \. \( \[ \{ \" \- \$
                    }
                }
                // [\s\S] = "mọi ký tự" bất kể định nghĩa khoảng trắng; viết lại không phụ thuộc \s/\S.
                !inClass && src.startsWith("[\\s\\S]", i) -> { sb.append("[\\x{0}-\\x{10FFFF}]"); i += 6 }
                inClass -> {
                    when (c) {
                        ']' -> inClass = false
                        '[' -> bad("nested character class")
                        '&', '{', '}' -> bad("'$c' inside a character class is not portable")
                    }
                    sb.append(c); i++
                }
                c == '[' -> { inClass = true; sb.append(c); i++ }
                c == '(' && src.startsWith("(?", i) -> {
                    val ok = src.startsWith("(?:", i) || src.startsWith("(?=", i) || src.startsWith("(?!", i) ||
                        src.startsWith("(?<=", i) || src.startsWith("(?<!", i)
                    if (!ok) bad("group/flag syntax outside the portable subset (inline flags are not portable: ICU accepts only i d m s w x)")
                    sb.append(c); i++
                }
                c == '{' -> {
                    val len = quantifierLength(src, i)
                    if (len < 0) bad("unescaped '{' that is not a quantifier")
                    sb.append(src, i, i + len); i += len
                }
                c == '}' -> bad("unescaped '}'")
                c == '$' -> { sb.append(LINE_END); i++ }
                c == '.' -> { sb.append(ANY_BUT_EOL); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        if (inClass) bad("unterminated character class")
        return sb.toString()
    }
}
