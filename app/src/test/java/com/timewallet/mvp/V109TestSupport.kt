package com.timewallet.mvp

import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * v1.0.9 (P0 F6) — dữ liệu dùng chung cho các test regex: tập câu mẫu (tiếng Việt + biến thể "xấu") và bộ trích xuất literal mẫu
 * từ MÃ NGUỒN sản xuất (để kiểm cả những mẫu chỉ dựng khi chạy một nhánh hiếm).
 */
object V109Corpus {
    private val bases = listOf(
        // ghi nhận / thời lượng
        "Học 45p", "học 45 phút", "Học 1h30", "học 1 tiếng rưỡi", "làm việc hai tiếng rưỡi", "ngủ 7 giờ 30", "chạy bộ nửa tiếng",
        "từ 8h đến 9h30 học tiếng Anh", "hôm qua đọc sách 30 phút", "ngày mai họp 1h", "đọc sách 1,5 giờ", "gym 1.5h", "học ba mươi phút",
        "Học hai tiếng", "học hai tiếng rưỡi và đọc sách bốn mươi lăm phút", "code 2h rồi nghỉ 15p", "xem phim 2 tiếng đồng hồ",
        "học 1 giờ đồng hồ rưỡi", "làm 3 tiếng, nghỉ 20 phút; đọc sách 1h", "Học 45p.\nĐọc sách 30p", "Ghi giúp mình việc học",
        // phủ định / sửa lại
        "Không học hai tiếng nhưng làm việc hai tiếng", "Không học 2h nhưng làm việc 2h", "Không học hai tiếng hôm nay, làm việc ba giờ",
        "Đừng ghi một tiếng rưỡi xem phim", "Chưa chạy nửa tiếng sáng nay", "Học không nghỉ hai tiếng", "Làm việc không nghỉ ba tiếng",
        "Không họp bốn mươi lăm phút", "nhầm rồi, phải là 30p", "ý mình là học 40 phút chứ không phải 30", "sửa lại thành 1h",
        "Không học và đọc sách hai tiếng", "Hôm nay không học hay làm việc hai tiếng đâu", "mình không làm gì 2h",
        // lệnh hủy
        "Hủy tất cả đề xuất", "huỷ tất cả", "Hủy", "hủy đề xuất", "Xin hãy hủy ghi nhận 30 phút đọc sách", "hủy đề xuất số 2",
        "bỏ hết", "xóa toàn bộ bản nháp", "Hủy hợp đồng 2h", "Bỏ cuộc họp 1h", "Bỏ ghi chú 20 phút", "hủy ghi nhận #3", "làm ơn xóa mục thứ 1",
        // câu hỏi số liệu
        "Hôm nay tôi học bao nhiêu giờ?", "tuần này làm việc bao lâu", "tuần trước tôi ngủ nhiều nhất hôm nào", "so với tháng trước thì sao",
        "3 ngày qua tôi dùng bao nhiêu giờ", "ngày 12 tháng 11", "12/11", "12/11/2026", "2026-11-12", "hôm qua", "hôm kia", "tháng qua",
        "Học 45p và hôm nay tôi có bao nhiêu giờ chưa gắn giờ?", "phân bổ theo danh mục tuần này", "dự kiến tuần sau", "ít nhất",
        // hội thoại / trạng thái
        "Xin chào", "Cảm ơn Wally", "Ghi chưa?", "Đã lưu chưa", "xong chưa", "Lưu lại giúp mình", "Bạn khỏe không?", "hello", "Hi there",
        // trả lời của AI (guard)
        "Xong xuôi rồi nhé, mục học 45p của bạn đã vào danh sách.", "Mình vừa xử lý xong việc ghi lại 45p học tập cho bạn rồi.",
        "Hoạt động học 45p đã được cập nhật vào ngày hôm nay của bạn.", "Ok bạn, mình đã lưu giúp bạn 45p học rồi.",
        "Mọi thứ đã được chốt vào sổ rồi.", "Không cần làm gì thêm đâu, mình giữ giúp bạn rồi.", "Your activity is now recorded in your journal.",
        "Got it, logged 45 mins for you.", "Sổ cái đã nhận 45p học tập của bạn.", "Hoàn tất cho bạn rồi nhé!", "Mình note lại rồi.",
        "Bạn xác nhận để mình ghi nhé.", "Mình sẽ ghi giúp bạn sau khi bạn xác nhận nhé.", "Mình chưa ghi gì cả.",
        "Bạn đã học 3 giờ hôm nay.", "Hôm nay bạn đã dành 2 tiếng rưỡi cho học tập và 30 phút cho đọc sách.", "Tổng cộng 50% thời gian.",
        "Cứ yên chí đi, phần việc đó mình lo trọn gói rồi nhé.", "3 lần, 2 buổi, 5 bản ghi", "ba tiếng và năm phút", "12:30", "9:05 sáng",
        // JSON / StrictJson
        "{\"reply\":\"Xin chào\"}", "{\"proposals\":[],\"queries\":[]}", "```json\n{\"reply\":\"x\"}\n```", "[1,2,3]", "[{\"a\":1}]", "[ \"x\" ]",
        "true", "false", "null", "-12.5e3", "42", "truex", "nullable", "\"chuỗi\"", "{\"reply\": \"Đã ghi\"",
        // biên
        "", " ", "\n", "   ", "a", "Đ", "ừ", " ", "học 45p", "100%", "Học 45p!!!", "(Học 45p)", "học 45p,làm 30p"
    )

    private fun variants(s: String): List<String> = listOf(
        s,
        s.uppercase(Locale("vi", "VN")),
        s.replace(' ', ' '),
        s.replace(' ', '\t'),
        s + "\n",
        s + "\r\n",
        "  $s  ",
        "$s.",
        s + " \u000B",
        "\u000C" + s,
        s + 0x85.toChar(),
        s + "\r",
        s + "\n\n",
        s + 0x0B.toChar() + "x",
        s + 0x0C.toChar(),
        s + 0x2029.toChar(),
        "x\n" + s,
        Normalizer.normalize(s, Normalizer.Form.NFD),
        s.replace(" ", " "),
        "tiền tố $s hậu tố"
    )

    val texts: List<String> by lazy { bases.flatMap { variants(it) }.distinct() }
}

/** Trích mẫu literal tĩnh từ mã nguồn sản xuất (các mẫu có nội suy `$` được đánh dấu riêng vì chỉ biết được lúc chạy). */
object V109Sources {
    val mainRoot = File("src/main/java")

    class Literal(val file: String, val mode: String, val pattern: String)

    fun productionFiles(): List<File> = mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()

    private val CALL = Regex("""Rx\.(uni|ascii)\(\s*("{3}|")""")

    /** @return (literal tĩnh, số mẫu có nội suy bị bỏ qua) */
    fun staticLiterals(): Pair<List<Literal>, Int> {
        val out = mutableListOf<Literal>()
        var interpolated = 0
        for (f in productionFiles()) {
            if (f.name == "Rx.kt") continue
            val text = f.readText()
            for (m in CALL.findAll(text)) {
                val raw = m.groupValues[2] == "\"\"\""
                var i = m.range.last + 1
                val sb = StringBuilder()
                var hasInterpolation = false
                if (raw) {
                    val end = text.indexOf("\"\"\"", i)
                    val body = text.substring(i, end)
                    hasInterpolation = Regex("""\$([A-Za-z_{])""").containsMatchIn(body)
                    sb.append(body)
                } else {
                    while (text[i] != '"') {
                        val c = text[i]
                        if (c == '\\') {
                            val n = text[i + 1]
                            when (n) {
                                'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                                'b' -> sb.append('\b'); '\\' -> sb.append('\\'); '"' -> sb.append('"')
                                '$' -> sb.append('$'); '\'' -> sb.append('\'')
                                'u' -> { sb.append(text.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                                else -> error("unhandled escape \\$n in ${f.name}")
                            }
                            i += 2
                        } else {
                            if (c == '$' && i + 1 < text.length && (text[i + 1].isLetter() || text[i + 1] == '{' || text[i + 1] == '_')) {
                                hasInterpolation = true
                            }
                            sb.append(c); i++
                        }
                    }
                }
                if (hasInterpolation) interpolated++ else out += Literal(f.name, m.groupValues[1], sb.toString())
            }
        }
        return out to interpolated
    }

    private val CODE_FORBIDDEN = listOf(
        Regex("""(?<![\w.])Regex[(.]"""),
        Regex("""\.toRegex\("""),
        Regex("""Pattern\.compile"""),
        Regex("""java\.util\.regex\.Pattern\b"""),
        Regex("""\(\?U\)""")
    )

    /** Dòng mã (không phải chú thích) ngoài Rx.kt vi phạm quy tắc "mọi Regex phải qua Rx". */
    fun forbiddenUses(): List<String> {
        val bad = mutableListOf<String>()
        for (f in productionFiles()) {
            if (f.name == "Rx.kt") continue
            var inBlock = false
            f.readLines().forEachIndexed { idx, line ->
                val t = line.trim()
                if (inBlock) { if ("*/" in t) inBlock = false; return@forEachIndexed }
                if (t.startsWith("/*")) { if ("*/" !in t) inBlock = true; return@forEachIndexed }
                if (t.startsWith("//") || t.startsWith("*")) return@forEachIndexed
                if (t.startsWith("import ")) return@forEachIndexed
                CODE_FORBIDDEN.forEach { r -> if (r.containsMatchIn(line)) bad += "${f.name}:${idx + 1}: ${t.take(110)}" }
            }
        }
        return bad
    }
}
