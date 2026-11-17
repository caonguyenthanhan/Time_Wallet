package com.timewallet.mvp.domain.usecase

/**
 * v1.0.12 (P1-AI): vệ sinh khóa API trước khi đưa vào header xác thực của yêu cầu AI.
 *
 * SỰ THẬT ĐÃ ĐO (v1.0.13, lớp HTTP chuẩn java.net của Android 9 và Android 16): `setRequestProperty` chỉ ném IllegalArgumentException với KÝ TỰ ĐIỀU KHIỂN
 * (U+0000–0008, U+000A–001F gồm LF/CR, và U+007F). Tab (U+0009) và mọi ký tự ngoài ASCII đã đo (dấu gạch dài "–", khoảng trắng không ngắt, ký tự
 * rộng-bằng-không, chữ có dấu/tiếng Việt, CJK...) đều được lớp mạng NHẬN và sẽ được gửi tới nhà cung cấp (khóa sai ⇒ nhà cung cấp trả HTTP 401 dạng JSON).
 * Lớp mạng KHÔNG từ chối ký tự ngoài ASCII. Lỗi cục bộ "Cloud MALFORMED 5/5" (vài mili giây, không có mã HTTP, từng bị gán nhãn MALFORMED
 * "AI trả về dữ liệu không hợp lệ") nếu đến từ header thì ký tự gây ra chỉ có thể là ký tự điều khiển (điển hình xuống dòng/CR dính theo khi dán khóa).
 *
 * QUY TẮC SẢN PHẨM (không phải hạn chế của nền tảng): khóa của nhà cung cấp chỉ gồm ASCII in được, không có khoảng trắng. Vì vậy:
 *  - [normalize] bỏ phần chắc chắn là rác khi dán (khoảng trắng/xuống dòng/ký tự vô hình, ngoặc bao quanh, tiền tố "Bearer ")
 *    và đổi các loại dấu gạch Unicode về '-';
 *  - [check] báo ký tự đầu tiên còn lại không phải ASCII in được (điều khiển, khoảng trắng, ngoài ASCII) là khóa không hợp lệ TRƯỚC khi mở kết nối,
 *    để người dùng được báo ngay thay vì gửi đi một khóa chắc chắn sai (không bao giờ trả về nội dung khóa).
 */
object ApiKeyHygiene {

    data class Problem(val position: Int, val kindVi: String)

    private val DASHES = setOf('‐', '‑', '‒', '–', '—', '―', '−', '﹘', '﹣', '－')
    private val QUOTES = setOf('"', '\'', '`', '“', '”', '‘', '’', '«', '»')

    private fun isInvisible(c: Char): Boolean =
        c in '\u200B'..'\u200F' || c in '\u202A'..'\u202E' || c in '\u2060'..'\u2064' || c == '\uFEFF' || c == '\u00AD'

    private fun isBlankLike(c: Char): Boolean = c.isWhitespace() || Character.isSpaceChar(c) || isInvisible(c)

    fun normalize(raw: String): String {
        var s = raw.trim { isBlankLike(it) || it in QUOTES }
        if (s.length > 7 && s.regionMatches(0, "Bearer", 0, 6, ignoreCase = true) && isBlankLike(s[6])) {
            s = s.substring(7).trim { isBlankLike(it) || it in QUOTES }
        }
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                isBlankLike(c) -> Unit
                c in DASHES -> sb.append('-')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** null = khóa hợp lệ theo quy tắc sản phẩm (ASCII in được, không khoảng trắng); đây không phải điều kiện của lớp mạng. Vị trí tính từ 1. */
    fun check(key: String): Problem? {
        for ((i, c) in key.withIndex()) {
            when {
                c.code < 0x20 || c.code == 0x7F -> return Problem(i + 1, "ký tự điều khiển")
                c.code > 0x7E -> return Problem(i + 1, "ký tự ngoài ASCII (chữ có dấu hoặc dấu câu đặc biệt)")
                c.code == 0x20 -> return Problem(i + 1, "khoảng trắng")
            }
        }
        return null
    }
}
