package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class QuickEntryParseResult(
    val cleanTitle: String,
    val categoryKey: String?,
    val durationMinutes: Int?,
    val startHm: String? = null,
    val endHm: String? = null,
    val tagId: String? = null
)

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
        val title: String,
        val tagId: String? = null,
        /** v1.1.0 (A2/A3): có giờ bắt đầu/kết thúc "HH:mm" khi nhập theo khoảng giờ; null khi chỉ có thời lượng. */
        val startHm: String? = null,
        val endHm: String? = null,
        /** Lưu dạng Dự kiến (PLANNED) khi người dùng chọn trước/ngày tương lai. */
        val saveAsPlanned: Boolean = false
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
            startHm = valid.startHm,
            endHm = valid.endHm,
            idempotencyKey = key,
            convertFutureToPlanned = valid.saveAsPlanned,
            tagId = valid.tagId
        )
    }

    /**
     * Nhập theo KHOẢNG GIỜ: cho phép khoảng đã qua, đang diễn ra (DOING / ACTIVE) và tương lai (PLANNED).
     * "Kết thúc: Bây giờ" dùng được cho hôm nay hoặc hoạt động bắt đầu từ hôm qua kéo sang bây giờ.
     * Ghi vẫn đi qua `LedgerValidator.validateBatchAtomic` (tương lai ⇒ PLANNED, vắt qua hiện tại ⇒ ACTIVE).
     */
    fun validateRange(
        categoryKey: String?,
        tagId: String?,
        tagName: String?,
        startText: String,
        endText: String,
        endIsNow: Boolean,
        dateLocal: String,
        note: String,
        today: LocalDate,
        nowHm: String,
        saveAsPlanned: Boolean = false
    ): ManualEntryValidation {
        val category = TimeCategory.fromKeyOrNull(categoryKey)
            ?: return ManualEntryValidation.Invalid("Hãy chọn hoạt động hoặc một danh mục.")
        val date = try {
            LocalDate.parse(dateLocal.trim())
        } catch (_: DateTimeParseException) {
            return ManualEntryValidation.Invalid("Ngày không hợp lệ.")
        }
        val isToday = date == today
        val isYesterday = date == today.minusDays(1)
        val range = TimeRange.compute(
            startText,
            if (endIsNow) TimeRange.End.Now else TimeRange.End.At(endText),
            nowHm,
            isToday,
            allowEndNowFromYesterday = isYesterday
        )
        val ok = when (range) {
            is TimeRange.Result.Invalid -> return ManualEntryValidation.Invalid(range.message)
            is TimeRange.Result.Ok -> range
        }
        if (ok.minutes > MAX_MINUTES) return ManualEntryValidation.Invalid("Một bản ghi không vượt quá 24 giờ.")
        val title = note.trim().take(MAX_NOTE_LENGTH).ifBlank { tagName?.trim().orEmpty().ifBlank { category.displayName } }
        return ManualEntryValidation.Valid(
            category = category,
            durationMinutes = ok.minutes,
            dateLocal = date.toString(),
            title = title,
            tagId = tagId,
            startHm = ok.startHm,
            endHm = ok.endHm,
            saveAsPlanned = saveAsPlanned || date.isAfter(today)
        )
    }

    fun validate(
        categoryKey: String?,
        hoursText: String,
        minutesText: String,
        dateLocal: String,
        note: String,
        today: LocalDate,
        tagId: String? = null,
        tagName: String? = null,
        saveAsPlanned: Boolean = false
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

        val title = note.trim().take(MAX_NOTE_LENGTH).ifBlank { tagName?.trim().orEmpty().ifBlank { category.displayName } }
        return ManualEntryValidation.Valid(
            category = category,
            durationMinutes = total,
            dateLocal = date.toString(),
            title = title,
            tagId = tagId,
            saveAsPlanned = saveAsPlanned || date.isAfter(today)
        )
    }

    /**
     * Phân tích nhanh chuỗi ghi chú gọn kiểu "Đọc sách 30 phút" hoặc "Họp 14:00-15:00" cho biểu mẫu thủ công:
     * giữ nguyên nguyên tắc chỉ-thời-lượng nếu không nêu mốc giờ (không bao giờ tự bịa giờ bắt đầu/kết thúc).
     */
    fun parseQuickInput(
        raw: String,
        tags: List<Tag> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): QuickEntryParseResult? {
        val clean = raw.trim()
        if (clean.isEmpty()) return null
        val normalized = VietnameseChatParser.restoreUnaccented(clean)
        val parsed = VietnameseChatParser.parse(normalized, today = today).proposals.firstOrNull() ?: return null
        val cleanTitle = parsed.title.trim().take(MAX_NOTE_LENGTH).ifBlank { return null }
        val matchedTag = TagRules.matchTitle(cleanTitle, tags) ?: TagRules.matchTitle(clean, tags)
        val hasBounds = !parsed.startLocal.isNullOrBlank() && !parsed.endLocal.isNullOrBlank() &&
            parsed.timeEvidence == "explicit_range"
        return QuickEntryParseResult(
            cleanTitle = cleanTitle,
            categoryKey = matchedTag?.category?.key ?: parsed.category,
            durationMinutes = parsed.durationMinutes.takeIf { it in 1..MAX_MINUTES },
            startHm = if (hasBounds) parsed.startLocal else null,
            endHm = if (hasBounds) parsed.endLocal else null,
            tagId = matchedTag?.id
        )
    }
}
