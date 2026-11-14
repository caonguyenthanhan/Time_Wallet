package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.TimeCategory
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * WALLY P1: nhập giờ thủ công. Chỉ kiểm tra đầu vào của form; việc ghi đi qua đúng đường chung
 * `LedgerValidator.validateBatchAtomic` + `LedgerDao.commitBatchRecordsGuarded` (không có nghiệp vụ riêng).
 * Không bao giờ tự gán giờ bắt đầu/kết thúc khi chỉ có thời lượng.
 */
sealed class ManualEntryValidation {
    data class Valid(
        val category: TimeCategory,
        val durationMinutes: Int,
        val dateLocal: String,
        val title: String
    ) : ManualEntryValidation()

    data class Invalid(val message: String) : ManualEntryValidation()
}

object ManualEntry {
    const val MAX_NOTE_LENGTH = 110
    const val MAX_MINUTES = 1440

    /** Các mốc chọn nhanh (phút) theo SPEC: 15m / 30m / 1h / 2h. */
    val PRESET_MINUTES: List<Int> = listOf(15, 30, 60, 120)

    fun presetLabel(minutes: Int): String = when {
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes}p"
    }

    /** Chuẩn hóa số phút tổng thành cặp (giờ, phút) để điền vào form. */
    fun split(minutes: Int): Pair<Int, Int> = minutes / 60 to minutes % 60

    private fun parseCount(text: String, label: String): Pair<Int?, String?> {
        val t = text.trim()
        if (t.isEmpty()) return 0 to null
        if (!t.all { it.isDigit() } || t.length > 4) return null to "$label phải là số nguyên không âm."
        return t.toInt() to null
    }

    /** Một id + idempotencyKey cho mỗi lần mở form: bấm Lưu lặp chỉ ghi một bản ghi. Không có start/end. */
    fun toBatchInput(valid: ManualEntryValidation.Valid, sessionId: String): BatchRecordInput {
        val key = "manual_$sessionId"
        return BatchRecordInput(
            id = key,
            source = "manual",
            rawTitle = valid.title,
            rawCategory = valid.category.key,
            dateLocal = valid.dateLocal,
            durationMinutes = valid.durationMinutes,
            startHm = null,
            endHm = null,
            idempotencyKey = key
        )
    }

    fun validate(
        categoryKey: String?,
        hoursText: String,
        minutesText: String,
        dateLocal: String,
        note: String,
        today: LocalDate
    ): ManualEntryValidation {
        val category = TimeCategory.fromKeyOrNull(categoryKey)
            ?: return ManualEntryValidation.Invalid("Hãy chọn một danh mục.")

        val (hours, hErr) = parseCount(hoursText, "Giờ")
        if (hErr != null) return ManualEntryValidation.Invalid(hErr)
        val (minutes, mErr) = parseCount(minutesText, "Phút")
        if (mErr != null) return ManualEntryValidation.Invalid(mErr)
        if (minutes!! !in 0..59) return ManualEntryValidation.Invalid("Phút phải từ 0 đến 59.")

        val total = hours!! * 60 + minutes
        if (total <= 0) return ManualEntryValidation.Invalid("Hãy nhập thời lượng lớn hơn 0.")
        if (total > MAX_MINUTES) return ManualEntryValidation.Invalid("Một bản ghi không vượt quá 24 giờ.")

        val date = try {
            LocalDate.parse(dateLocal.trim())
        } catch (_: DateTimeParseException) {
            return ManualEntryValidation.Invalid("Ngày không hợp lệ.")
        }
        if (date.isAfter(today)) {
            return ManualEntryValidation.Invalid("Chỉ ghi giờ đã dùng, không chọn ngày trong tương lai. Hãy dùng Đồng hồ hoặc Chat để lên kế hoạch.")
        }

        val title = note.trim().take(MAX_NOTE_LENGTH).ifBlank { category.displayName }
        return ManualEntryValidation.Valid(category, total, date.toString(), title)
    }
}
