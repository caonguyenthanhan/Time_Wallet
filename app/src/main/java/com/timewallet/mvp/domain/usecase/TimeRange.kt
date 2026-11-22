package com.timewallet.mvp.domain.usecase

/**
 * v1.1.0 (A2/A3): nhập theo KHOẢNG GIỜ. Người dùng nhập hai mốc giờ (hoặc giờ bắt đầu + "Bây giờ"); ứng dụng tự tính số phút.
 * Hàm thuần, không đụng sổ cái: việc ghi vẫn đi qua `LedgerValidator.validateBatchAtomic` (giờ bắt đầu/kết thúc) như đường chat.
 *
 * Quy ước: cả hai mốc cùng một ngày; giờ kết thúc nhỏ hơn giờ bắt đầu nghĩa là qua nửa đêm (ví dụ 23:30 → 00:15 = 45 phút);
 * hai mốc bằng nhau KHÔNG phải 24 giờ mà là không hợp lệ.
 */
object TimeRange {

    sealed class End {
        data class At(val hm: String) : End()
        /** Giờ kết thúc = đồng hồ hiện tại tại lúc tính; chỉ dùng cho ngày hôm nay. */
        object Now : End()
    }

    sealed class Result {
        data class Ok(val startHm: String, val endHm: String, val minutes: Int, val crossesMidnight: Boolean) : Result()
        data class Invalid(val message: String) : Result()
    }

    /**
     * "14:00", "9:05", "14h30", "14h" → số phút từ 00:00; null nếu không hợp lệ.
     * Dạng chỉ-chữ-số (ô giờ chỉ giữ chữ số, dấu ":" chỉ để hiển thị): "14" = 14:00; "930" = 9:30 (số đầu >= 3 là giờ một chữ số);
     * "1400" = 14:00; "141" (giờ hai chữ số thiếu phút) và "2460" không hợp lệ.
     */
    fun parseMinuteOfDay(raw: String?): Int? {
        val t = raw?.trim()?.lowercase().orEmpty()
        if (t.isEmpty()) return null
        if (t.all { it.isDigit() }) {
            val (h, m) = when (t.length) {
                1, 2 -> t.toInt() to 0
                3 -> if (t[0] >= '3') t.substring(0, 1).toInt() to t.substring(1).toInt() else return null
                4 -> t.substring(0, 2).toInt() to t.substring(2).toInt()
                else -> return null
            }
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }
        val parts = when {
            ':' in t -> t.split(':')
            'h' in t -> t.split('h')
            else -> return null
        }
        if (parts.size != 2) return null
        val h = parts[0].trim().takeIf { it.isNotEmpty() && it.length <= 2 && it.all(Char::isDigit) }?.toInt() ?: return null
        val mText = parts[1].trim()
        val m = if (mText.isEmpty()) 0 else (mText.takeIf { it.length <= 2 && it.all(Char::isDigit) }?.toInt() ?: return null)
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun formatHm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** Giờ kết thúc chấp nhận thêm mốc cuối ngày `"24:00"` / `"2400"` (= 1440 phút), trong khi giờ bắt đầu giữ strictly 00:00..23:59. */
    fun parseEndMinuteOfDay(raw: String?): Int? {
        val t = raw?.trim()?.lowercase().orEmpty()
        if (t in setOf("24:00", "2400", "24h", "24h00", "24:0")) return 1440
        return parseMinuteOfDay(raw)
    }

    /**
     * @param nowHm đồng hồ hiện tại "HH:mm" (bắt buộc khi [end] là [End.Now]).
     * @param isToday ngày ghi nhận có phải hôm nay không.
     * @param allowEndNowFromYesterday cho phép chọn "Bây giờ" khi ngày bắt đầu là hôm qua (vd ngủ từ tối hôm qua tới sáng nay).
     */
    fun compute(
        startHm: String?,
        end: End,
        nowHm: String?,
        isToday: Boolean,
        allowEndNowFromYesterday: Boolean = false
    ): Result {
        val start = parseMinuteOfDay(startHm)
            ?: return Result.Invalid("Hãy nhập giờ bắt đầu, ví dụ 14:00.")
        val endMinute: Int = when (end) {
            is End.At -> parseEndMinuteOfDay(end.hm) ?: return Result.Invalid("Hãy nhập giờ kết thúc, ví dụ 15:12.")
            End.Now -> {
                if (!isToday && !allowEndNowFromYesterday) {
                    return Result.Invalid("\"Bây giờ\" chỉ dùng cho hôm nay hoặc bắt đầu từ hôm qua. Hãy nhập giờ kết thúc.")
                }
                val now = parseMinuteOfDay(nowHm) ?: return Result.Invalid("Không đọc được giờ hiện tại.")
                if (isToday && start > now) return Result.Invalid("Giờ bắt đầu chưa tới. Chọn giờ bắt đầu trước bây giờ.")
                now
            }
        }
        if (endMinute == start) {
            return Result.Invalid(
                if (end === End.Now) "Chưa đủ 1 phút kể từ giờ bắt đầu." else "Giờ kết thúc phải khác giờ bắt đầu."
            )
        }
        if (endMinute == 1440) {
            val minutes = 1440 - start
            return Result.Ok(formatHm(start), "24:00", minutes, crossesMidnight = false)
        }
        val crosses = endMinute < start
        if (crosses && end === End.Now && isToday) return Result.Invalid("Giờ bắt đầu chưa tới. Chọn giờ bắt đầu trước bây giờ.")
        val minutes = if (crosses) (24 * 60 - start) + endMinute else endMinute - start
        return Result.Ok(formatHm(start), formatHm(endMinute), minutes, crosses)
    }

    /** "1h 12p", "45p", "2h" — dùng để hiển thị kết quả tự tính TRƯỚC khi lưu. */
    fun formatDuration(minutes: Int): String = when {
        minutes < 60 -> "${minutes}p"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}p"
    }
}
