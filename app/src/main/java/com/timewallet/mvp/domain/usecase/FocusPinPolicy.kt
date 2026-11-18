package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class FocusPinKind {
    ACTIVE_TIMER,
    CURRENT_PLANNED,
    SUGGESTION_LAST_WEEK,
    SUGGESTION_YESTERDAY,
    SUGGESTION_RECENT_3_DAYS,
    EMPTY_NO_HISTORY
}

data class FocusPinSuggestion(
    val sourceLabel: String,
    val title: String,
    val category: TimeCategory,
    val tagId: String?,
    val timeRangeLabel: String,
    val frequencyDays: Int = 1
)

data class FocusPinState(
    val kind: FocusPinKind,
    val headline: String,
    val bodyText: String,
    val expandedText: String,
    val suggestedTitle: String?,
    val suggestedCategory: TimeCategory?,
    val suggestedTagId: String?,
    val nextTransitionEpochMs: Long?,
    val secondaryHints: List<FocusPinSuggestion> = emptyList()
)

/**
 * Thuật toán tất định chọn nội dung "Ghim thông báo tập trung" tại thời điểm hiện tại:
 * 1. Đồng hồ đang chạy (`ActiveTimerSession`) -> hiển thị hoạt động đang bấm giờ.
 * 2. Có hoạt động `PLANNED` / `DOING` đang nằm trong khung giờ hiện tại -> hiển thị việc theo kế hoạch.
 * 3. Chưa có kế hoạch giờ này -> gợi ý lần lượt theo: giờ này tuần trước -> hôm qua -> 3 ngày gần đây.
 */
object FocusPinPolicy {

    private const val TOLERANCE_MS = 45L * 60_000L
    private const val FALLBACK_REFRESH_MS = 60L * 60_000L
    private val HM_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private data class ScoredCandidate(
        val record: LedgerRecord,
        val distanceMs: Long,
        val dayDate: LocalDate
    )

    fun resolve(
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FocusPinState {
        val upcomingPlan = findNextUpcomingPlan(records, nowEpochMs)

        if (activeTimer != null) {
            val cat = TimeCategory.fromKeyOrDefault(activeTimer.categoryKey)
            val title = activeTimer.title.ifBlank { cat.displayName }
            val startHm = Instant.ofEpochMilli(activeTimer.startedAtEpochMs).atZone(zoneId).format(HM_FORMATTER)
            val nextSuffix = upcomingPlan?.let { p ->
                val pStart = p.startedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(HM_FORMATTER) }.orEmpty()
                if (pStart.isNotEmpty()) "\nTiếp theo theo lịch: $pStart · ${p.title}" else ""
            }.orEmpty()
            return FocusPinState(
                kind = FocusPinKind.ACTIVE_TIMER,
                headline = "Đang tập trung: $title",
                bodyText = "${cat.displayName} · Bắt đầu lúc $startHm",
                expandedText = "Đang bấm giờ đo thực tế ($startHm · ${cat.displayName}). Chạm để mở lại đồng hồ khi kết thúc.$nextSuffix",
                suggestedTitle = title,
                suggestedCategory = cat,
                suggestedTagId = activeTimer.tagId,
                nextTransitionEpochMs = upcomingPlan?.startedAt
            )
        }

        val activePlan = findCurrentPlannedOrDoing(records, nowEpochMs)
        if (activePlan != null) {
            val startMs = activePlan.startedAt!!
            val endMs = activePlan.endedAt!!
            val startHm = Instant.ofEpochMilli(startMs).atZone(zoneId).format(HM_FORMATTER)
            val endHm = Instant.ofEpochMilli(endMs).atZone(zoneId).format(HM_FORMATTER)
            val remainMin = ((endMs - nowEpochMs + 59_999L) / 60_000L).coerceAtLeast(1L)
            val nextTransition = listOfNotNull(endMs, upcomingPlan?.startedAt).minOrNull()
            return FocusPinState(
                kind = FocusPinKind.CURRENT_PLANNED,
                headline = "Theo kế hoạch: ${activePlan.title}",
                bodyText = "$startHm–$endHm · ${activePlan.category.displayName} · Còn ${formatMinutes(remainMin)}",
                expandedText = "Khung giờ hiện tại ($startHm–$endHm · ${activePlan.category.displayName}) đang có lịch \"${activePlan.title}\". " +
                    "Chạm \"▶ Bấm giờ\" để đo thực tế đúng khoảng này hoặc mở app để đối soát.",
                suggestedTitle = activePlan.title,
                suggestedCategory = activePlan.category,
                suggestedTagId = activePlan.tagId,
                nextTransitionEpochMs = nextTransition
            )
        }

        val nowZoned = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val today = nowZoned.toLocalDate()
        val nowWallTime = nowZoned.toLocalTime()

        val lastWeekHint = findBestOnDate(records, today.minusDays(7), nowWallTime, zoneId, "Giờ này tuần trước")
        val yesterdayHint = findBestOnDate(records, today.minusDays(1), nowWallTime, zoneId, "Giờ này hôm qua")
        val recent3DaysHint = findBestInRecent3Days(records, today, nowWallTime, zoneId)

        val secondaryHints = listOfNotNull(lastWeekHint, yesterdayHint, recent3DaysHint)
        val primary = when {
            lastWeekHint != null -> FocusPinKind.SUGGESTION_LAST_WEEK to lastWeekHint
            yesterdayHint != null -> FocusPinKind.SUGGESTION_YESTERDAY to yesterdayHint
            recent3DaysHint != null -> FocusPinKind.SUGGESTION_RECENT_3_DAYS to recent3DaysHint
            else -> null
        }

        val nextPlanMs = upcomingPlan?.startedAt
        val nextDayStartMs = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val fallbackRefresh = listOfNotNull(
            nextPlanMs,
            nowEpochMs + FALLBACK_REFRESH_MS,
            nextDayStartMs
        ).minOrNull()

        if (primary == null) {
            val nextLine = upcomingPlan?.let { p ->
                val pStart = p.startedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(HM_FORMATTER) }.orEmpty()
                " · Kế hoạch tiếp: $pStart ${p.title}"
            }.orEmpty()
            return FocusPinState(
                kind = FocusPinKind.EMPTY_NO_HISTORY,
                headline = "Chưa có kế hoạch giờ này",
                bodyText = "Chạm để bắt đầu bấm giờ hoặc chọn việc ưu tiên$nextLine",
                expandedText = "Chưa có lịch dự kiến hay dữ liệu trùng giờ từ tuần trước / hôm qua / 3 ngày gần đây.$nextLine",
                suggestedTitle = upcomingPlan?.title,
                suggestedCategory = upcomingPlan?.category,
                suggestedTagId = upcomingPlan?.tagId,
                nextTransitionEpochMs = fallbackRefresh
            )
        }

        val (kind, bestHint) = primary
        val freqNote = if (kind == FocusPinKind.SUGGESTION_RECENT_3_DAYS && bestHint.frequencyDays > 1) {
            " · ${bestHint.frequencyDays}/3 ngày"
        } else ""
        val otherLines = secondaryHints
            .filter { it !== bestHint }
            .joinToString("\n") { "• ${it.sourceLabel}: ${it.title} (${it.timeRangeLabel})" }
        val nextPlanLine = upcomingPlan?.let { p ->
            val pStart = p.startedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(HM_FORMATTER) }.orEmpty()
            "\nKế hoạch sắp tới: $pStart · ${p.title}"
        }.orEmpty()
        val expanded = buildString {
            append("Không có lịch dự kiến giờ này. Gợi ý theo ${bestHint.sourceLabel.lowercase()}: ")
            append("${bestHint.title} (${bestHint.category.displayName}, ${bestHint.timeRangeLabel}$freqNote).")
            if (otherLines.isNotEmpty()) append("\n").append(otherLines)
            if (nextPlanLine.isNotEmpty()) append(nextPlanLine)
        }

        return FocusPinState(
            kind = kind,
            headline = "${bestHint.sourceLabel}: ${bestHint.title}",
            bodyText = "Chưa có kế hoạch · ${bestHint.category.displayName} (${bestHint.timeRangeLabel}$freqNote)",
            expandedText = expanded,
            suggestedTitle = bestHint.title,
            suggestedCategory = bestHint.category,
            suggestedTagId = bestHint.tagId,
            nextTransitionEpochMs = fallbackRefresh,
            secondaryHints = secondaryHints
        )
    }

    private fun findCurrentPlannedOrDoing(records: List<LedgerRecord>, nowEpochMs: Long): LedgerRecord? {
        return records
            .filter { r ->
                if (r.isCancelled || r.isNeedsReconciliation) return@filter false
                val s = r.startedAt ?: return@filter false
                val e = r.endedAt ?: return@filter false
                if (nowEpochMs < s || nowEpochMs >= e) return@filter false
                val state = r.resolveConfirmationStateAt(nowEpochMs)
                state == ConfirmationState.PLANNED || state == ConfirmationState.DOING || r.layer == TimeLayer.ACTIVE
            }
            .sortedWith(
                compareBy<LedgerRecord>(
                    { if (it.layer == TimeLayer.ACTIVE || it.resolveConfirmationStateAt(nowEpochMs) == ConfirmationState.DOING) 0 else 1 },
                    { it.startedAt ?: Long.MAX_VALUE },
                    { -it.createdAt }
                )
            )
            .firstOrNull()
    }

    private fun findNextUpcomingPlan(records: List<LedgerRecord>, nowEpochMs: Long): LedgerRecord? {
        val horizonMs = nowEpochMs + 24L * 3_600_000L
        return records
            .filter { r ->
                if (r.isCancelled || r.isNeedsReconciliation) return@filter false
                val s = r.startedAt ?: return@filter false
                val e = r.endedAt ?: return@filter false
                if (e <= s || s <= nowEpochMs || s > horizonMs) return@filter false
                val state = r.resolveConfirmationStateAt(nowEpochMs)
                state == ConfirmationState.PLANNED || r.layer == TimeLayer.PLANNED
            }
            .minByOrNull { it.startedAt ?: Long.MAX_VALUE }
    }

    private fun findBestOnDate(
        records: List<LedgerRecord>,
        targetDate: LocalDate,
        wallTime: java.time.LocalTime,
        zoneId: ZoneId,
        sourceLabel: String
    ): FocusPinSuggestion? {
        val refMs = targetDate.atTime(wallTime).atZone(zoneId).toInstant().toEpochMilli()
        val best = eligibleHistoryCandidates(records, targetDate, refMs).firstOrNull() ?: return null
        return toSuggestion(best.record, sourceLabel, zoneId, frequencyDays = 1)
    }

    private fun findBestInRecent3Days(
        records: List<LedgerRecord>,
        today: LocalDate,
        wallTime: java.time.LocalTime,
        zoneId: ZoneId
    ): FocusPinSuggestion? {
        val allCandidates = (1L..3L).flatMap { offset ->
            val day = today.minusDays(offset)
            val refMs = day.atTime(wallTime).atZone(zoneId).toInstant().toEpochMilli()
            eligibleHistoryCandidates(records, day, refMs)
        }
        if (allCandidates.isEmpty()) return null

        val grouped = allCandidates.groupBy { c ->
            val normTitle = normalizeTitleKey(c.record.title)
            Triple(c.record.category.key, c.record.tagId.orEmpty(), normTitle)
        }

        val winningGroup = grouped.values.sortedWith(
            compareByDescending<List<ScoredCandidate>> { list -> list.map { it.dayDate }.distinct().size }
                .thenBy { list -> list.minOf { it.distanceMs } }
                .thenByDescending { list -> list.maxOf { it.dayDate } }
        ).first()

        val representative = winningGroup.sortedWith(
            compareBy<ScoredCandidate> { it.distanceMs }
                .thenByDescending { it.dayDate }
                .thenByDescending { it.record.durationSeconds }
        ).first()

        val distinctDays = winningGroup.map { it.dayDate }.distinct().size
        return toSuggestion(
            record = representative.record,
            sourceLabel = "Giờ này 3 ngày gần đây",
            zoneId = zoneId,
            frequencyDays = distinctDays
        )
    }

    private fun eligibleHistoryCandidates(
        records: List<LedgerRecord>,
        day: LocalDate,
        refMs: Long
    ): List<ScoredCandidate> {
        val minWindow = refMs - TOLERANCE_MS
        val maxWindow = refMs + TOLERANCE_MS
        return records.mapNotNull { r ->
            if (r.isCancelled || r.isNeedsReconciliation || r.isMarkedWaste) return@mapNotNull null
            if (r.layer == TimeLayer.PLANNED || r.layer == TimeLayer.REPORTED_UNTIMED) return@mapNotNull null
            val s = r.startedAt ?: return@mapNotNull null
            val e = r.endedAt ?: return@mapNotNull null
            if (e <= s || e < minWindow || s > maxWindow) return@mapNotNull null
            val distance = when {
                refMs in s until e -> 0L
                refMs < s -> abs(s - refMs)
                else -> abs(refMs - e)
            }
            ScoredCandidate(record = r, distanceMs = distance, dayDate = day)
        }.sortedWith(
            compareBy<ScoredCandidate>(
                { it.distanceMs },
                { if (it.record.layer == TimeLayer.MEASURED) 0 else 1 },
                { -it.record.durationSeconds },
                { -(it.record.endedAt ?: 0L) }
            )
        )
    }

    private fun toSuggestion(
        record: LedgerRecord,
        sourceLabel: String,
        zoneId: ZoneId,
        frequencyDays: Int
    ): FocusPinSuggestion {
        val sHm = record.startedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(HM_FORMATTER) }.orEmpty()
        val eHm = record.endedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).format(HM_FORMATTER) }.orEmpty()
        val range = if (sHm.isNotEmpty() && eHm.isNotEmpty()) "$sHm–$eHm" else "${record.durationMinutes}p"
        return FocusPinSuggestion(
            sourceLabel = sourceLabel,
            title = record.title,
            category = record.category,
            tagId = record.tagId,
            timeRangeLabel = range,
            frequencyDays = frequencyDays
        )
    }

    private fun normalizeTitleKey(raw: String): String {
        val sb = StringBuilder(raw.length)
        var prevSpace = true
        for (ch in raw.lowercase()) {
            if (ch.isWhitespace()) {
                if (!prevSpace && sb.isNotEmpty()) {
                    sb.append(' ')
                    prevSpace = true
                }
            } else {
                sb.append(ch)
                prevSpace = false
            }
        }
        return sb.toString().trimEnd()
    }

    private fun formatMinutes(minutes: Long): String {
        val h = minutes / 60L
        val m = minutes % 60L
        return when {
            h > 0L && m > 0L -> "${h}h ${m}p"
            h > 0L -> "${h}h"
            else -> "${m}p"
        }
    }
}
