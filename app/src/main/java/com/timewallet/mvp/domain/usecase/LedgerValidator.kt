package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

sealed class ValidationResult {
    data class Valid(val record: LedgerRecord) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

object LedgerValidator {

    /**
     * Kiểm tra giao nhau về thời gian giữa khoảng [startEpochMs, endEpochMs)
     * với danh sách bản ghi thực tế/đang diễn ra hiện có (MEASURED, REPORTED_TIMED, ACTIVE)
     * hoặc phiên Timer đang chạy.
     */
    fun findOverlap(
        startEpochMs: Long,
        endEpochMs: Long,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        excludeRecordId: String? = null
    ): String? {
        val hit = existingRecords.firstOrNull { record ->
            record.id != excludeRecordId &&
                record.isTimedActualOrActive &&
                record.startedAt != null &&
                record.endedAt != null &&
                startEpochMs < record.endedAt &&
                endEpochMs > record.startedAt
        }
        if (hit != null) {
            return hit.title
        }

        if (activeTimer != null && startEpochMs < nowEpochMs && endEpochMs > activeTimer.startedAtEpochMs) {
            return "Bộ đếm đang chạy (${activeTimer.title})"
        }

        return null
    }

    /**
     * AI-01 (Finding F): kiểm tra lại giao nhau giữa các bản ghi sắp ghi với phiên Timer đang chạy,
     * dùng trạng thái Timer MỚI NHẤT tại thời điểm commit (không dùng bản chụp lúc validate).
     * @return thông điệp lỗi nếu có xung đột, ngược lại null.
     */
    fun findActiveTimerConflict(
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        nowEpochMs: Long
    ): String? {
        if (activeTimer == null) return null
        for (record in records) {
            if (!record.isTimedActualOrActive || record.status == "PLANNED" || record.layer == TimeLayer.PLANNED) continue
            val start = record.startedAt ?: continue
            val end = record.endedAt ?: continue
            val hit = findOverlap(start, end, emptyList(), activeTimer, nowEpochMs)
            if (hit != null) return "\"${record.title}\" trùng mốc giờ với $hit."
        }
        return null
    }

    /**
     * Phân tích chuỗi giờ HH:mm cùng ngày YYYY-MM-DD sang epoch millis theo múi giờ.
     * Nếu giờ kết thúc < giờ bắt đầu (qua nửa đêm), tự động cộng sang ngày kế tiếp.
     * Giờ kết thúc == giờ bắt đầu là khoảng rỗng, không phải 24 giờ: trả null (v1.0.13).
     */
    fun parseTimeRangeToEpochMillis(
        dateLocal: String,
        startHm: String,
        endHm: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long>? {
        return try {
            val localDate = LocalDate.parse(dateLocal.trim())
            val startTime = parseFlexibleLocalTime(startHm) ?: return null
            val endTime = parseFlexibleLocalTime(endHm) ?: return null
            if (endTime == startTime) return null
            if (endTime == startTime) return null

            val startZoned = ZonedDateTime.of(localDate, startTime, zoneId)
            var endZoned = ZonedDateTime.of(localDate, endTime, zoneId)
            if (!endZoned.isAfter(startZoned)) {
                endZoned = ZonedDateTime.of(localDate.plusDays(1), endTime, zoneId)
            }
            startZoned.toInstant().toEpochMilli() to endZoned.toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFlexibleLocalTime(raw: String): LocalTime? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":", "h", "H")
        if (parts.isEmpty() || parts.size > 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = if (parts.size == 2 && parts[1].isNotBlank()) {
            parts[1].trim().toIntOrNull() ?: return null
        } else {
            0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /**
     * Xác thực toàn bộ bất biến của sổ cái trước khi ghi (MVP Refinement 01):
     * - Phân biệt MEASURED, REPORTED_TIMED, REPORTED_UNTIMED, ACTIVE và PLANNED.
     * - Không tính thời gian tương lai vào thời gian đã sử dụng:
     *   + Nếu ngày > hôm nay hoặc startedAt > nowEpochMs:
     *     * Khi convertFutureToPlanned = true -> lưu làm PLANNED (Dự kiến).
     *     * Khi convertFutureToPlanned = false -> từ chối và yêu cầu xác nhận lưu làm Dự kiến.
     *   + Nếu startedAt <= nowEpochMs < endedAt (ví dụ tại 14:35 nhập hoạt động 13:00–18:00):
     *     * Lưu trạng thái ACTIVE ("Đang diễn ra"). Thống kê chỉ tính phần đã qua (13:00–14:35 = 1h35p),
     *       không tính phần tương lai (14:35–18:00) cho đến khi thời gian trôi qua hoặc người dùng chốt hoàn thành.
     * - Ngăn chồng lấn thời gian giữa các hoạt động thực tế/đang diễn ra.
     */
    fun validateAndBuildRecord(
        id: String = "rec_${UUID.randomUUID()}",
        source: String,
        rawTitle: String,
        rawCategory: String,
        dateLocal: String,
        inputDurationMinutes: Int?,
        startedAt: Long?,
        endedAt: Long?,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        excludeRecordId: String? = null,
        convertFutureToPlanned: Boolean = false
    ): ValidationResult {
        val title = rawTitle.trim()
        if (title.isEmpty()) {
            return ValidationResult.Invalid("Hãy nhập tên hoạt động.")
        }

        val category = TimeCategory.fromKeyOrNull(rawCategory)
            ?: return ValidationResult.Invalid("Danh mục '$rawCategory' không nằm trong danh sách cho phép.")

        val parsedDate = try {
            LocalDate.parse(dateLocal.trim())
        } catch (_: Exception) {
            return ValidationResult.Invalid("Ngày thực hiện không hợp lệ (YYYY-MM-DD).")
        }

        if ((startedAt == null) != (endedAt == null)) {
            return ValidationResult.Invalid("Hãy nhập cả giờ bắt đầu và giờ kết thúc, hoặc bỏ trống cả hai.")
        }

        val todayLocal = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val isFutureDate = parsedDate.isAfter(todayLocal)
        val isFutureStart = startedAt != null && startedAt > nowEpochMs

        val isPlanned = convertFutureToPlanned || isFutureDate || isFutureStart

        val finalDurationSeconds: Long
        if (startedAt != null && endedAt != null) {
            if (endedAt <= startedAt) {
                return ValidationResult.Invalid("Giờ kết thúc phải lớn hơn giờ bắt đầu.")
            }
            val computedSeconds = ((endedAt - startedAt) / 1000L).coerceAtLeast(1L)
            if (computedSeconds > 86_400L) {
                return ValidationResult.Invalid("Một bản ghi đơn lẻ không vượt quá 24 giờ.")
            }

            if (inputDurationMinutes != null && source != "timer") {
                val computedMinutes = computedSeconds / 60.0
                if (abs(computedMinutes - inputDurationMinutes) > 2.0) {
                    return ValidationResult.Invalid(
                        "Mốc giờ tương ứng ${kotlin.math.round(computedMinutes).toInt()} phút, không khớp với $inputDurationMinutes phút đã nhập."
                    )
                }
            }
            finalDurationSeconds = computedSeconds

            if (!isPlanned) {
                val overlappingTitle = findOverlap(
                    startEpochMs = startedAt,
                    endEpochMs = endedAt,
                    existingRecords = existingRecords,
                    activeTimer = if (source == "timer") null else activeTimer,
                    nowEpochMs = nowEpochMs,
                    excludeRecordId = excludeRecordId
                )
                if (overlappingTitle != null) {
                    return ValidationResult.Invalid("Bị trùng mốc giờ thực tế với \"$overlappingTitle\".")
                }
            }
        } else {
            val minutes = inputDurationMinutes ?: 0
            if (minutes < 1 || minutes > 1440) {
                return ValidationResult.Invalid("Thời lượng phải từ 1 đến 1440 phút.")
            }
            finalDurationSeconds = minutes * 60L
        }

        val isSpanningNow = !isPlanned && startedAt != null && endedAt != null &&
            startedAt <= nowEpochMs && endedAt > nowEpochMs

        val layer = when {
            isPlanned -> TimeLayer.PLANNED
            isSpanningNow -> TimeLayer.ACTIVE
            source == "timer" && startedAt != null && endedAt != null -> TimeLayer.MEASURED
            startedAt != null && endedAt != null -> TimeLayer.REPORTED_TIMED
            else -> TimeLayer.REPORTED_UNTIMED
        }

        val status = when (layer) {
            TimeLayer.PLANNED -> "PLANNED"
            TimeLayer.ACTIVE -> "ACTIVE"
            else -> "CONFIRMED"
        }

        val effectiveDate = if (startedAt != null) {
            Instant.ofEpochMilli(startedAt).atZone(zoneId).toLocalDate().toString()
        } else {
            parsedDate.toString()
        }

        return ValidationResult.Valid(
            LedgerRecord(
                id = id,
                source = source,
                layer = layer,
                createdAt = nowEpochMs,
                userConfirmedAt = nowEpochMs,
                category = category,
                title = title,
                date = effectiveDate,
                durationSeconds = finalDurationSeconds,
                startedAt = startedAt,
                endedAt = endedAt,
                timezone = zoneId.id,
                status = status,
                idempotencyKey = idempotencyKey
            )
        )
    }

    /**
     * Xác nhận chuyển một bản ghi Dự kiến (PLANNED) hoặc Đang diễn ra (ACTIVE) sang Thực tế đã hoàn thành:
     * - Chỉ cho phép khi hoạt động đã bắt đầu (startedAt <= nowEpochMs và date <= today).
     * - Nếu kết thúc dự kiến (endedAt) vẫn còn ở tương lai (> nowEpochMs), chốt thời점 kết thúc thực tế tại nowEpochMs.
     * - Kiểm tra không chồng lấn với các bản ghi thực tế khác.
     */
    fun confirmPlannedOrActiveToActual(
        record: LedgerRecord,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        actualEndedAtEpochMs: Long? = null
    ): ValidationResult {
        val todayLocal = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val parsedDate = try {
            LocalDate.parse(record.date)
        } catch (_: Exception) {
            todayLocal
        }

        if (parsedDate.isAfter(todayLocal)) {
            return ValidationResult.Invalid("Hoạt động thuộc ngày tương lai (${record.date}), chưa thể xác nhận thành thực tế.")
        }

        if (record.startedAt != null && record.endedAt != null) {
            if (record.startedAt > nowEpochMs) {
                return ValidationResult.Invalid("Chưa tới giờ bắt đầu của hoạt động dự kiến này, chưa thể xác nhận thực tế.")
            }
            if (actualEndedAtEpochMs != null && actualEndedAtEpochMs > nowEpochMs) {
                return ValidationResult.Invalid("Giờ kết thúc thực tế không được vượt quá thời điểm hiện tại.")
            }
            val effectiveEndMs = actualEndedAtEpochMs ?: min(record.endedAt, nowEpochMs)
            if (effectiveEndMs <= record.startedAt) {
                return ValidationResult.Invalid("Giờ kết thúc thực tế phải lớn hơn giờ bắt đầu.")
            }
            if (effectiveEndMs - record.startedAt < 60_000L) {
                return ValidationResult.Invalid("Thời gian thực tế đã diễn ra chưa đủ 1 phút.")
            }
            val actualSeconds = ((effectiveEndMs - record.startedAt) / 1000L).coerceAtLeast(60L)

            val conflict = findOverlap(
                startEpochMs = record.startedAt,
                endEpochMs = effectiveEndMs,
                existingRecords = existingRecords,
                activeTimer = activeTimer,
                nowEpochMs = nowEpochMs,
                excludeRecordId = record.id
            )
            if (conflict != null) {
                return ValidationResult.Invalid("Bị trùng mốc giờ thực tế với \"$conflict\".")
            }

            return ValidationResult.Valid(
                record.copy(
                    layer = if (record.source == "timer") TimeLayer.MEASURED else TimeLayer.REPORTED_TIMED,
                    durationSeconds = actualSeconds,
                    endedAt = effectiveEndMs,
                    userConfirmedAt = nowEpochMs,
                    status = "CONFIRMED"
                )
            )
        } else {
            return ValidationResult.Valid(
                record.copy(
                    layer = TimeLayer.REPORTED_UNTIMED,
                    userConfirmedAt = nowEpochMs,
                    status = "CONFIRMED"
                )
            )
        }
    }

    /**
     * Kiểm tra tính hợp lệ nguyên tử (All-or-Nothing) cho một lô (batch) hoạt động:
     * - Nếu BẤT KỲ mục nào trong batch không hợp lệ (sai định dạng giờ, thời lượng <= 0,
     *   trùng giờ với bản ghi cũ hoặc trùng giờ giữa 2 mục trong cùng batch), TOÀN BỘ batch
     *   bị từ chối (không có mục nào được phép ghi vào DB).
     */
    fun validateBatchAtomic(
        inputs: List<BatchRecordInput>,
        existingRecords: List<LedgerRecord>,
        activeTimer: ActiveTimerSession? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BatchValidationResult {
        if (inputs.isEmpty()) {
            return BatchValidationResult.Invalid(
                failedIndex = -1,
                failedTitle = "",
                message = "Danh sách đề xuất trống."
            )
        }

        val workingRecords = existingRecords.toMutableList()
        val validatedBatch = mutableListOf<LedgerRecord>()

        for ((index, item) in inputs.withIndex()) {
            val st = item.startHm?.trim().orEmpty()
            val en = item.endHm?.trim().orEmpty()
            if ((st.isNotEmpty() && en.isEmpty()) || (st.isEmpty() && en.isNotEmpty())) {
                return BatchValidationResult.Invalid(
                    failedIndex = index,
                    failedTitle = item.rawTitle,
                    message = "Mục #${index + 1} (\"${item.rawTitle}\"): Hãy nhập cả giờ bắt đầu và kết thúc, hoặc để trống cả hai."
                )
            }

            var startMs: Long? = null
            var endMs: Long? = null
            if (st.isNotEmpty() && en.isNotEmpty()) {
                val range = parseTimeRangeToEpochMillis(item.dateLocal, st, en, zoneId)
                    ?: return BatchValidationResult.Invalid(
                        failedIndex = index,
                        failedTitle = item.rawTitle,
                        message = "Mục #${index + 1} (\"${item.rawTitle}\"): Giờ bắt đầu/kết thúc không hợp lệ."
                    )
                startMs = range.first
                endMs = range.second
            }

            val validation = validateAndBuildRecord(
                id = item.id ?: UUID.randomUUID().toString(),
                source = item.source,
                rawTitle = item.rawTitle,
                rawCategory = item.rawCategory,
                dateLocal = item.dateLocal,
                inputDurationMinutes = item.durationMinutes,
                startedAt = startMs,
                endedAt = endMs,
                existingRecords = workingRecords,
                activeTimer = activeTimer,
                nowEpochMs = nowEpochMs,
                zoneId = zoneId,
                idempotencyKey = item.idempotencyKey ?: UUID.randomUUID().toString(),
                convertFutureToPlanned = item.convertFutureToPlanned
            )

            when (validation) {
                is ValidationResult.Invalid -> {
                    return BatchValidationResult.Invalid(
                        failedIndex = index,
                        failedTitle = item.rawTitle,
                        message = "Mục #${index + 1} (\"${item.rawTitle}\"): ${validation.message}"
                    )
                }
                is ValidationResult.Valid -> {
                    workingRecords.add(validation.record)
                    validatedBatch.add(validation.record)
                }
            }
        }

        return BatchValidationResult.Valid(validatedBatch)
    }
}

data class BatchRecordInput(
    val id: String? = null,
    val source: String = "chat",
    val rawTitle: String,
    val rawCategory: String,
    val dateLocal: String,
    val durationMinutes: Int?,
    val startHm: String? = null,
    val endHm: String? = null,
    val convertFutureToPlanned: Boolean = false,
    val idempotencyKey: String? = null
)

sealed class BatchValidationResult {
    data class Valid(val records: List<LedgerRecord>) : BatchValidationResult()
    data class Invalid(
        val failedIndex: Int,
        val failedTitle: String,
        val message: String
    ) : BatchValidationResult()
}
