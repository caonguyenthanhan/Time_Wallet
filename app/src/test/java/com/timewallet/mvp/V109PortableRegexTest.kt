package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GuardHealth
import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.QueryIntentParser
import com.timewallet.mvp.domain.usecase.Rx
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * v1.0.9 — P0 F6 (crash thiết bị thật): `(?U)` không phải cờ ICU nên `Regex` khởi tạo thất bại trên Android.
 *
 * LƯU Ý TRUNG THỰC: các test này chạy trên JDK. Chúng KHÔNG chứng minh Android runtime PASS. Cái chúng chứng minh được:
 *  (1) mọi Regex sản xuất đi qua `Rx` và chuỗi mẫu CUỐI CÙNG chỉ chứa cấu trúc nằm trong tập con di động (không `(?U)`, không `\s \d \b`,
 *      không cờ nội tuyến...), tức là cùng một chuỗi mà ICU4C nhận được;
 *  (2) chuỗi dịch hành xử GIỐNG HỆT mẫu gốc `(?U)...` trên JDK cho cả tập câu mẫu + biến thể xấu.
 * Bằng chứng ICU4C thật nằm ở `out/wally_evidence_v109/icu_*` (xem `V109IcuDumpTest` sinh dữ liệu và `icu_replay.py` chạy lại trên ICU).
 */
class V109PortableRegexTest {

    // ---------------------------------------------------------------- A. hợp đồng của bộ dịch
    private fun rejects(src: String, unicode: Boolean = true): String {
        try {
            Rx.translate(src, unicode)
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        fail("phải bị từ chối: /$src/")
        return ""
    }

    @Test
    fun `inline unicode flag (?U) is rejected and can never reach the engine`() {
        assertTrue(rejects("(?U)^abc").contains("inline flags"))
        assertTrue(rejects("(?U)^abc", unicode = false).contains("inline flags"))
    }

    @Test
    fun `constructs outside the portable subset are rejected instead of behaving differently per engine`() {
        listOf(
            "(?i)abc", "(?<name>a)", "(?>a)", "(?s:a)",
            "\\w", "\\W", "\\D", "\\B", "\\S+", "\\h", "\\R", "\\z", "\\1",
            "\\p{IsAlphabetic}", "\\p{Alpha}", "\\p{javaLowerCase}", "\\pL",
            "[a&&b]", "[a[b]]", "[abc", "[\\b]", "a{", "a}", "a{x}", "a{,3}", "a{2", "abc\\", "[{]", "[}]"
        ).forEach { rejects(it) }
    }

    @Test
    fun `explicit classes replace s d b so the result does not depend on any engine flag`() {
        val ws = "\\t\\n\\x{B}\\f\\r\\x{85}\\p{Z}"
        assertEquals("[$ws]+", Rx.translate("\\s+", true))
        assertEquals("[ \\t\\n\\x{B}\\f\\r]+", Rx.translate("\\s+", false))
        // `$` và `.` theo đúng ngữ nghĩa JDK, không phụ thuộc định nghĩa "kết thúc dòng" của ICU (thêm U+000B/U+000C).
        assertEquals("a(?=(?:\\r\\n|(?<!\\r)\\n|[\\r\\x{85}\\x{2028}\\x{2029}])?\\z)", Rx.translate("a$", false))
        assertEquals("a[^\\n\\r\\x{85}\\x{2028}\\x{2029}]*", Rx.translate("a.*", false))
        assertEquals("\\.\\$[.$]", Rx.translate("\\.\\$[.$]", false))
        assertEquals("[\\p{Nd}]{1,2}", Rx.translate("\\d{1,2}", true))
        assertEquals("[0-9]{1,2}", Rx.translate("\\d{1,2}", false))
        assertEquals("[0-9/]", Rx.translate("[\\d/]", false))
        assertEquals("a{2}b{1,3}c{0,}", Rx.translate("a{2}b{1,3}c{0,}", false))
        assertEquals("[$ws,.;:!?()]", Rx.translate("[\\s,.;:!?()]", true))
        assertEquals("[\\x{0}-\\x{10FFFF}]*?", Rx.translate("[\\s\\S]*?", false))
        val b = Rx.translate("a\\b", false)
        assertFalse(b.contains("\\b"))
        assertTrue(b.contains("(?<=[A-Za-z0-9_])(?![A-Za-z0-9_])"))
        val ub = Rx.translate("\\bgiờ\\b", true)
        assertTrue(ub.contains("\\p{L}\\p{M}\\p{Nd}\\p{Nl}\\p{Pc}"))
        assertTrue(Rx.translate("\\p{L}(?<!\\p{N})\\.\\(\\[", true).contains("\\p{L}(?<!\\p{N})\\.\\(\\["))
    }

    // ---------------------------------------------------------------- B. quét mã nguồn sản xuất
    @Test
    fun `no production file builds a Regex or Pattern outside Rx`() {
        val bad = V109Sources.forbiddenUses()
        assertTrue("Regex/Pattern/(?U) ngoài Rx:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `Rx itself has no inline-flag text in code so the shipped binary contains none`() {
        // Chú thích (KDoc) có thể nhắc tới cờ; chuỗi trong mã thì không, để chuỗi này không xuất hiện trong dex.
        val code = File("src/main/java/com/timewallet/mvp/domain/usecase/Rx.kt").readLines()
            .filterNot { it.trim().startsWith("*") || it.trim().startsWith("/*") || it.trim().startsWith("//") }
        val flag = "(?" + "U)"
        assertTrue(code.none { it.contains(flag) })
    }

    @Test
    fun `every static pattern literal in production translates and uses only portable constructs`() {
        val (literals, interpolated) = V109Sources.staticLiterals()
        assertTrue("mong đợi ≥ 55 literal tĩnh, thực tế ${literals.size}", literals.size >= 55)
        assertTrue("các mẫu có nội suy phải được phủ bởi registry lúc chạy", interpolated > 0)
        val problems = mutableListOf<String>()
        for (l in literals) {
            try {
                val out = Rx.translate(l.pattern, l.mode == "uni")
                problems += forbiddenIn(out).map { "${l.file}: $it in /${l.pattern.take(60)}/" }
            } catch (e: IllegalArgumentException) {
                problems += "${l.file}: ${e.message}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // ---------------------------------------------------------------- C. registry lúc chạy
    private fun touchEverything() {
        GuardHealth.resetForTest()
        assertTrue(GuardHealth.check().ok)
        val today = LocalDate.of(2026, 11, 13)
        V109Corpus.texts.forEach {
            CancelCommandParser.parse(it)
            QueryIntentParser.parse(it, today)
            VietnameseChatParser.parse(it)
            VietnameseChatParser.stripDurationText(it)
            VietnameseChatParser.hasNegatedOrCorrectedDuration(it)
            VietnameseChatParser.classifyCategory(it)
        }
    }

    /** Cấu trúc KHÔNG được phép xuất hiện trong chuỗi mẫu CUỐI CÙNG (kiểm độc lập với bộ dịch). */
    private fun forbiddenIn(portable: String): List<String> {
        val allowedProps = setOf("L", "N", "Nd", "Nl", "M", "Lu", "Ll", "Z", "Pc")
        val found = mutableListOf<String>()
        var i = 0
        var cls = false
        while (i < portable.length) {
            val c = portable[i]
            if (c == '\\') {
                val n = portable.getOrNull(i + 1) ?: break
                if (n in "sdbwSDBWhHvVRXGZAk" || n.isDigit()) found += "\\$n"
                if (n == 'p' || n == 'P') {
                    val end = portable.indexOf('}', i)
                    val name = portable.substring(i + 3, end)
                    if (name !in allowedProps) found += "\\$n{$name}"
                    i = end + 1
                    continue
                }
                i += 2
                continue
            }
            if (cls) {
                if (c == ']') cls = false else if (c == '[') found += "nested-class" else if (c == '&') found += "&"
            } else if (c == '[') {
                cls = true
            } else if (c == '$' || c == '.') {
                found += "unescaped $c outside a class (line-terminator semantics differ between JDK and ICU)"
            } else if (c == '(' && portable.startsWith("(?", i)) {
                val ok = portable.startsWith("(?:", i) || portable.startsWith("(?=", i) || portable.startsWith("(?!", i) ||
                    portable.startsWith("(?<=", i) || portable.startsWith("(?<!", i)
                if (!ok) found += portable.substring(i, minOf(portable.length, i + 4))
            }
            i++
        }
        return found
    }

    @Test
    fun `registry after running the guards holds only portable patterns and covers the production inventory`() {
        touchEverything()
        val entries = Rx.entries()
        assertTrue("mong đợi ≥ 100 mẫu, thực tế ${entries.size}", entries.size >= 100)
        val problems = entries.flatMap { e -> forbiddenIn(e.portable).map { "$it in /${e.source.take(70)}/" } }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("mong đợi ≥ 40 mẫu Unicode (trước đây là (?U))", entries.count { it.unicode } >= 40)
        assertTrue(entries.any { !it.unicode })
        assertTrue(entries.none { it.portable.contains("(?U)") })
    }

    // ---------------------------------------------------------------- D. đối chiếu với mẫu gốc `(?U)` trên JDK
    private fun spans(r: Regex, text: String): List<List<Int>> = r.findAll(text).map { m ->
        buildList {
            for (g in m.groups.indices) { val gr = m.groups[g]; add(gr?.range?.first ?: -1); add(gr?.range?.last ?: -1) }
        }
    }.toList()

    @Test
    fun `translated pattern behaves exactly like the original (?U) or default pattern on the JDK for the whole corpus`() {
        touchEverything()
        val entries = Rx.entries()
        val corpus = V109Corpus.texts
        val mismatches = mutableListOf<String>()
        var comparisons = 0
        for (e in entries) {
            val reference = if (e.ignoreCase) Regex(if (e.unicode) "(?U)" + e.source else e.source, RegexOption.IGNORE_CASE)
            else Regex(if (e.unicode) "(?U)" + e.source else e.source)
            for (t in corpus) {
                comparisons++
                val a = spans(reference, t)
                val b = spans(e.regex, t)
                if (a != b && mismatches.size < 12) {
                    mismatches += "pattern=/${e.source.take(80)}/ uni=${e.unicode} text=${t.replace("\n", "\\n").take(60)} ref=$a got=$b"
                }
            }
        }
        assertTrue("so sánh $comparisons cặp; lệch:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
        assertTrue(comparisons > 100_000)
    }

    // ---------------------------------------------------------------- D2. mẫu mở rộng (\b, $, \s tường minh) không chậm/không tràn ngăn xếp
    @Test
    fun `explicit patterns stay fast and do not overflow the stack on long inputs`() {
        GuardHealth.resetForTest()
        assertTrue(GuardHealth.check().ok)
        val unit = "Mình đã ghi lại 45 phút học và hai tiếng rưỡi làm việc, không nghỉ ba giờ nhưng chưa lưu xong. "
        val long = unit.repeat(80) // ≈ 7.5k ký tự
        val zone = LocalDate.of(2026, 11, 13)
        val t0 = System.nanoTime()
        com.timewallet.mvp.domain.usecase.ReplyGuard.sanitize(long, "Học 45p", emptyList(), false)
        com.timewallet.mvp.domain.usecase.AiProposalGuard.apply(long, emptyList())
        CancelCommandParser.parse(long)
        QueryIntentParser.parse(long, zone)
        VietnameseChatParser.parse(long)
        VietnameseChatParser.hasNegatedOrCorrectedDuration(long)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("các guard mất ${ms}ms cho 7.5k ký tự (ngưỡng 4000ms)", ms < 4_000)
    }

    // ---------------------------------------------------------------- E. xuất dữ liệu để chạy lại trên ICU4C thật (Python/ctypes)
    private fun esc(s: String): String = buildString {
        for (ch in s) when {
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch.code < 0x20 || ch.code == 0x85 || ch.code == 0x2028 || ch.code == 0x2029 -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
    }

    @Test
    fun `dump portable patterns and JVM match results for the ICU replay harness`() {
        touchEverything()
        val dir = File("build/wally_icu").apply { mkdirs() }
        val corpus = V109Corpus.texts
        val (literals, _) = V109Sources.staticLiterals()
        val runtime = Rx.entries().map { Triple(it.portable, it.unicode, it.ignoreCase) to "runtime" }
        val staticOnes = literals.map {
            Triple(Rx.translate(it.pattern, it.mode == "uni"), it.mode == "uni", false) to "static:${it.file}"
        }
        val all = (runtime + staticOnes).distinctBy { it.first.first + "|" + it.first.third }
        File(dir, "corpus.txt").writeText(corpus.joinToString("\n") { esc(it) }, Charsets.UTF_8)
        File(dir, "patterns.tsv").writeText(
            all.mapIndexed { i, (p, tag) -> "$i\t${if (p.third) 1 else 0}\t$tag\t${esc(p.first)}" }.joinToString("\n"), Charsets.UTF_8
        )
        val sb = StringBuilder()
        all.forEachIndexed { pi, (p, _) ->
            val r = if (p.third) Regex(p.first, RegexOption.IGNORE_CASE) else Regex(p.first)
            corpus.forEachIndexed { ti, t ->
                val s = spans(r, t)
                if (s.isNotEmpty()) sb.append(pi).append('\t').append(ti).append('\t').append(s.joinToString(";") { it.joinToString(",") }).append('\n')
            }
        }
        File(dir, "jvm_matches.tsv").writeText(sb.toString(), Charsets.UTF_8)
        assertTrue(all.size >= 100)
        assertTrue(File(dir, "jvm_matches.tsv").length() > 0)
    }
}
