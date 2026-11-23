package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitFrequency
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.LocalCategoryRule
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class CaptureIntentKind {
    TASK_INBOX,
    TIMED_EVENT,
    DURATION_ONLY
}

data class QuickCaptureParseResult(
    val kind: CaptureIntentKind,
    val cleanTitle: String,
    val category: TimeCategory,
    val tagId: String?,
    val durationMinutes: Int?,
    val startHm: String?,
    val endHm: String?,
    val priority: TaskPriority,
    val markWaste: Boolean,
    val matchedRuleKeyword: String? = null
)

data class PeakFocusWindow(
    val windowLabel: String,
    val goalMinutes: Long,
    val sharePercent: Int
)

data class PlanAccuracySessionEvidence(
    val recordId: String,
    val dateLocal: String,
    val title: String,
    val category: TimeCategory,
    val plannedMinutes: Long,
    val actualMinutes: Long,
    val deltaMinutes: Long,
    val deltaPercent: Int?
)

data class PlanAccuracySummary(
    val sampleCount: Int,
    val avgDeltaPercent: Int,
    val topDriftCategory: TimeCategory?,
    val summaryText: String,
    val usedMinutesDisplayOnly: Boolean = false,
    val plannedMinutesTotal: Long = 0L,
    val actualMinutesTotal: Long = 0L,
    val deltaMinutesTotal: Long = 0L,
    val sessionEvidence: List<PlanAccuracySessionEvidence> = emptyList(),
    val excludedNote: String? = null
)

enum class TaskSmartFilter(val labelVi: String) {
    TODAY("Hôm nay"),
    INBOX("Inbox"),
    HIGH_PRIORITY("Ưu tiên cao"),
    ALL_OPEN("Chưa xong"),
    DONE("Đã xong")
}

/**
 * Engine thuần Kotlin (JVM-testable) cho Wally 2.0:
 * - Liên kết Task <-> Ledger (không nhập trùng).
 * - Đánh giá Habit tự động hoàn thành từ phiên đo/nhật ký cùng Tag hoặc `habitId`.
 * - Phân tích Giờ vàng hiệu suất (`PeakFocusWindow`) và độ sát kế hoạch (`Plan vs Actual`).
 * - Parse thu thập nhanh (`QuickCapture`) + áp quy tắc cục bộ (`LocalCategoryRule`) + lọc quyền AI.
 */
object TaskHabitEngine {

    fun computeTaskLoggedSeconds(taskId: String, records: List<LedgerRecord>): Long {
        if (taskId.isBlank()) return 0L
        return records
            .filter { r ->
                r.taskId == taskId &&
                    !r.isCancelled &&
                    !r.isNeedsReconciliation &&
                    r.layer != TimeLayer.PLANNED &&
                    !r.status.equals("PLANNED", ignoreCase = true)
            }
            .sumOf { it.durationSeconds.coerceAtLeast(0L) }
    }

    fun isHabitDueOnDate(habit: Habit, dateLocal: String): Boolean {
        if (habit.isArchived) return false
        val date = runCatching { LocalDate.parse(dateLocal) }.getOrNull() ?: return true
        val dowBit = 1 shl (date.dayOfWeek.value - 1) // 1=Mon -> bit 0
        return when (habit.frequency) {
            HabitFrequency.DAILY, HabitFrequency.TIMES_PER_WEEK -> true
            HabitFrequency.WEEKDAYS -> date.dayOfWeek.value in 1..5
            HabitFrequency.CUSTOM_DAYS -> (habit.daysMask and dowBit) != 0
        }
    }

    /**
     * Trả về bản ghi `LedgerRecord` thực tế thỏa mãn tự động thói quen [habit] trong ngày [dateLocal] (nếu có).
     */
    fun findAutoFulfillingRecord(
        habit: Habit,
        dateLocal: String,
        records: List<LedgerRecord>
    ): LedgerRecord? {
        val minSeconds = habit.minSessionMinutes.coerceAtLeast(1) * 60L
        return records.firstOrNull { r ->
            if (r.date != dateLocal || r.isCancelled || r.isNeedsReconciliation || r.isMarkedWaste) return@firstOrNull false
            if (r.layer == TimeLayer.PLANNED || r.status.equals("PLANNED", ignoreCase = true)) return@firstOrNull false
            val explicitHabitMatch = r.habitId != null && r.habitId == habit.id
            val linkedTagMatch = habit.tagId != null && r.tagId == habit.tagId && r.durationSeconds >= minSeconds
            explicitHabitMatch || linkedTagMatch
        }
    }

    fun isHabitCompletedOnDate(
        habit: Habit,
        dateLocal: String,
        logs: List<HabitLog>,
        records: List<LedgerRecord>
    ): Boolean {
        val explicitDone = logs.any { it.habitId == habit.id && it.dateLocal == dateLocal && it.completed }
        if (explicitDone) return true
        return findAutoFulfillingRecord(habit, dateLocal, records) != null
    }

    fun habitRecentDots(
        habit: Habit,
        endDateLocal: String,
        daysCount: Int,
        logs: List<HabitLog>,
        records: List<LedgerRecord>
    ): List<Boolean> {
        val end = runCatching { LocalDate.parse(endDateLocal) }.getOrNull() ?: return emptyList()
        val count = daysCount.coerceIn(1, 30)
        return (-(count - 1)..0).map { offset ->
            val d = end.plusDays(offset.toLong()).toString()
            isHabitCompletedOnDate(habit, d, logs, records)
        }
    }

    fun filterTasks(
        tasks: List<TaskItem>,
        filter: TaskSmartFilter,
        todayDateLocal: String
    ): List<TaskItem> {
        val filtered = when (filter) {
            TaskSmartFilter.TODAY -> tasks.filter {
                !it.isDone && it.status != TaskStatus.ARCHIVED &&
                    (it.dueDateLocal == todayDateLocal || it.status == TaskStatus.DOING || it.priority == TaskPriority.P1)
            }
            TaskSmartFilter.INBOX -> tasks.filter { it.status == TaskStatus.INBOX && !it.isDone }
            TaskSmartFilter.HIGH_PRIORITY -> tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED && it.priority == TaskPriority.P1 }
            TaskSmartFilter.ALL_OPEN -> tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
            TaskSmartFilter.DONE -> tasks.filter { it.isDone }
        }
        return filtered.sortedWith(
            compareBy<TaskItem>(
                { it.isDone },
                { it.priority.sortWeight },
                { it.dueDateLocal ?: "9999-99-99" },
                { -it.createdAt }
            )
        )
    }

    /**
     * Phân tích ô Thu thập nhanh (`Universal Capture`) hoàn toàn offline,
     * đồng thời áp quy tắc cá nhân [LocalCategoryRule] nếu khớp từ khóa.
     */
    fun parseQuickCaptureInput(
        rawInput: String,
        preferredKind: CaptureIntentKind? = null,
        tags: List<Tag> = emptyList(),
        rules: List<LocalCategoryRule> = emptyList()
    ): QuickCaptureParseResult {
        val trimmed = collapseAsciiSpaces(rawInput.trim())
        val lower = TagRules.nameKey(trimmed)

        var priority = TaskPriority.P2
        when {
            containsWord(trimmed, "P1") || lower.contains("khẩn") || lower.contains("gấp") -> priority = TaskPriority.P1
            containsWord(trimmed, "P3") -> priority = TaskPriority.P3
        }

        val quickParsed = ManualEntry.parseQuickInput(trimmed, tags)
        val matchedRule = rules.firstOrNull { r ->
            r.enabled && r.keyword.trim().isNotEmpty() && lower.contains(TagRules.nameKey(r.keyword))
        }
        val detectedRange = extractInlineTimeRange(trimmed)
            ?: if (quickParsed?.startHm != null && quickParsed.endHm != null) quickParsed.startHm to quickParsed.endHm else null

        val resolvedKind = preferredKind ?: when {
            detectedRange != null -> CaptureIntentKind.TIMED_EVENT
            quickParsed?.durationMinutes != null && !lower.startsWith("viec ") && !lower.startsWith("task ") ->
                CaptureIntentKind.DURATION_ONLY
            else -> CaptureIntentKind.TASK_INBOX
        }

        val titleCandidate = stripPriorityTokens((quickParsed?.cleanTitle ?: "").ifBlank { trimmed }).ifBlank { trimmed }
        val category = matchedRule?.category
            ?: quickParsed?.categoryKey?.let { TimeCategory.fromKeyOrNull(it) }
            ?: TimeCategory.WORK
        val tagId = matchedRule?.tagId ?: quickParsed?.tagId

        return QuickCaptureParseResult(
            kind = resolvedKind,
            cleanTitle = titleCandidate.take(110),
            category = category,
            tagId = tagId,
            durationMinutes = quickParsed?.durationMinutes,
            startHm = detectedRange?.first,
            endHm = detectedRange?.second,
            priority = priority,
            markWaste = matchedRule?.markWasteByDefault == true,
            matchedRuleKeyword = matchedRule?.keyword
        )
    }

    /**
     * Tính khung 2 giờ tập trung hiệu quả nhất (`Peak Focus Hours`) trong 14 ngày gần nhất.
     */
    fun computePeakFocusWindow(
        records: List<LedgerRecord>,
        endDateLocal: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PeakFocusWindow? {
        val endDate = runCatching { LocalDate.parse(endDateLocal) }.getOrNull() ?: return null
        val startDate = endDate.minusDays(13)
        val hourBuckets = LongArray(24)
        var totalFocusSec = 0L

        for (r in records) {
            if (r.isCancelled || r.isNeedsReconciliation || r.isMarkedWaste) continue
            if (r.layer != TimeLayer.MEASURED && r.layer != TimeLayer.REPORTED_TIMED) continue
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: continue
            if (d.isBefore(startDate) || d.isAfter(endDate)) continue
            if (r.category in setOf(TimeCategory.SLEEP, TimeCategory.REST, TimeCategory.FUN, TimeCategory.LIFE)) continue
            val s = r.startedAt ?: continue
            val e = r.endedAt ?: continue
            if (e <= s) continue

            var cursorMs = s
            val cappedEndMs = minOf(e, s + 8 * 3600_000L)
            while (cursorMs < cappedEndMs) {
                val curZoned = Instant.ofEpochMilli(cursorMs).atZone(zoneId)
                val nextHourZoned = curZoned.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1)
                val segEndMs = minOf(cappedEndMs, nextHourZoned.toInstant().toEpochMilli())
                val segSecs = ((segEndMs - cursorMs) / 1000L).coerceAtLeast(0L)
                if (segSecs > 0L) {
                    val h = curZoned.hour.coerceIn(0, 23)
                    hourBuckets[h] += segSecs
                    totalFocusSec += segSecs
                }
                if (segEndMs <= cursorMs) break
                cursorMs = segEndMs
            }
        }
        if (totalFocusSec <= 0L) return null

        var bestStartHour = 8
        var bestPairSec = -1L
        for (h in 0..22) {
            val pair = hourBuckets[h] + hourBuckets[h + 1]
            if (pair > bestPairSec) {
                bestPairSec = pair
                bestStartHour = h
            }
        }
        if (bestPairSec <= 0L) return null

        val endHour = (bestStartHour + 2).coerceAtMost(24)
        val label = "${bestStartHour.toString().padStart(2, '0')}:00–${endHour.toString().padStart(2, '0')}:00"
        val sharePct = ((bestPairSec.toDouble() / totalFocusSec.toDouble()) * 100.0).roundToInt().coerceIn(1, 100)
        return PeakFocusWindow(windowLabel = label, goalMinutes = bestPairSec / 60L, sharePercent = sharePct)
    }

    /**
     * Tính độ sát kế hoạch giữa Kế hoạch (`plannedDurationSeconds`) và Thực tế (`durationSeconds`).
     * - Loại hoàn toàn `SLEEP` / `REST` khỏi chỉ số tập trung.
     * - Chỉ tính phần trăm khi `plannedDurationSeconds >= 15 phút` và |độ lệch| <= 300%;
     *   khi vượt 300% hoặc toàn bộ kế hoạch < 15p thì hiển thị trực tiếp thời lượng kế hoạch, thực tế và phút chênh.
     * - Khi thiếu cặp đối chiếu tập trung, trả về `sampleCount = 0` để UI ẩn chỉ số.
     */
    fun computePlanVsActualDelta(records: List<LedgerRecord>): PlanAccuracySummary {
        val nowMs = System.currentTimeMillis()
        val eligibleStates = setOf(
            ConfirmationState.COMPLETED,
            ConfirmationState.ADJUSTED,
            ConfirmationState.INTERRUPTED
        )
        val rawPaired = records.filter { r ->
            !r.isCancelled &&
                !r.isNeedsReconciliation &&
                r.layer != TimeLayer.PLANNED &&
                !r.status.equals("PLANNED", ignoreCase = true) &&
                (r.plannedDurationSeconds ?: 0L) > 0L &&
                r.durationSeconds > 0L &&
                r.resolveConfirmationStateAt(nowMs) in eligibleStates
        }
        val excludedSleepRest = rawPaired.count { it.category in setOf(TimeCategory.SLEEP, TimeCategory.REST) }
        val compared = rawPaired.filter { it.category !in setOf(TimeCategory.SLEEP, TimeCategory.REST) }

        val sleepNote = if (excludedSleepRest > 0) {
            "Đã loại $excludedSleepRest phiên Ngủ/nghỉ khỏi chỉ số tập trung."
        } else {
            null
        }

        if (compared.isEmpty()) {
            return PlanAccuracySummary(
                sampleCount = 0,
                avgDeltaPercent = 0,
                topDriftCategory = null,
                summaryText = "",
                excludedNote = sleepNote
            )
        }

        val evidenceList = compared.sortedByDescending { it.startedAt ?: it.createdAt }.map { r ->
            val pSec = r.plannedDurationSeconds!!
            val pMin = ((pSec + 30L) / 60L).coerceAtLeast(1L)
            val aMin = ((r.durationSeconds + 30L) / 60L).coerceAtLeast(1L)
            val pct = if (pSec >= 900L) {
                (((r.durationSeconds - pSec).toDouble() / pSec.toDouble()) * 100.0).roundToInt()
            } else {
                null
            }
            PlanAccuracySessionEvidence(
                recordId = r.id,
                dateLocal = r.date,
                title = r.title,
                category = r.category,
                plannedMinutes = pMin,
                actualMinutes = aMin,
                deltaMinutes = aMin - pMin,
                deltaPercent = pct
            )
        }

        val pctEligible = compared.filter { (it.plannedDurationSeconds ?: 0L) >= 900L }
        val shortPlansCount = compared.size - pctEligible.size

        fun fmtMins(mins: Long): String {
            val h = mins / 60L
            val m = mins % 60L
            return when {
                h > 0L && m > 0L -> "${h}h ${m}p"
                h > 0L -> "${h}h"
                else -> "${mins}p"
            }
        }

        fun signedDiffText(diffMins: Long): String = when {
            diffMins > 0L -> "chênh +${fmtMins(diffMins)}"
            diffMins < 0L -> "chênh -${fmtMins(-diffMins)}"
            else -> "khớp đúng"
        }

        val topCat = compared.groupBy { it.category }
            .maxByOrNull { (_, list) ->
                list.map { kotlin.math.abs(it.durationSeconds - it.plannedDurationSeconds!!) }.average()
            }?.key

        val shortNote = if (shortPlansCount > 0 && pctEligible.isNotEmpty()) {
            "Không tính % cho $shortPlansCount phiên có kế hoạch dưới 15p."
        } else {
            null
        }
        val combinedExcludedNote = listOfNotNull(sleepNote, shortNote).joinToString(" ").ifBlank { null }

        if (pctEligible.isEmpty()) {
            val plannedTotal = evidenceList.sumOf { it.plannedMinutes }
            val actualTotal = evidenceList.sumOf { it.actualMinutes }
            val diffTotal = actualTotal - plannedTotal
            val msg = "Độ sát (${compared.size} phiên kế hoạch < 15p): kế hoạch ${fmtMins(plannedTotal)}, " +
                "thực tế ${fmtMins(actualTotal)} (${signedDiffText(diffTotal)})."
            return PlanAccuracySummary(
                sampleCount = compared.size,
                avgDeltaPercent = 0,
                topDriftCategory = topCat,
                summaryText = msg,
                usedMinutesDisplayOnly = true,
                plannedMinutesTotal = plannedTotal,
                actualMinutesTotal = actualTotal,
                deltaMinutesTotal = diffTotal,
                sessionEvidence = evidenceList,
                excludedNote = combinedExcludedNote
            )
        }

        val pctDeltas = pctEligible.map { r ->
            val p = r.plannedDurationSeconds!!.toDouble()
            ((r.durationSeconds - p) / p * 100.0)
        }
        val avgDelta = pctDeltas.average().roundToInt()
        val eligiblePlannedTotal = pctEligible.sumOf { ((it.plannedDurationSeconds!! + 30L) / 60L).coerceAtLeast(1L) }
        val eligibleActualTotal = pctEligible.sumOf { ((it.durationSeconds + 30L) / 60L).coerceAtLeast(1L) }
        val eligibleDiffTotal = eligibleActualTotal - eligiblePlannedTotal

        if (kotlin.math.abs(avgDelta) > 300) {
            val msg = "Độ lệch vượt 300%: kế hoạch ${fmtMins(eligiblePlannedTotal)}, " +
                "thực tế ${fmtMins(eligibleActualTotal)} (${signedDiffText(eligibleDiffTotal)} · ${pctEligible.size} phiên)."
            return PlanAccuracySummary(
                sampleCount = compared.size,
                avgDeltaPercent = avgDelta,
                topDriftCategory = topCat,
                summaryText = msg,
                usedMinutesDisplayOnly = true,
                plannedMinutesTotal = eligiblePlannedTotal,
                actualMinutesTotal = eligibleActualTotal,
                deltaMinutesTotal = eligibleDiffTotal,
                sessionEvidence = evidenceList,
                excludedNote = combinedExcludedNote
            )
        }

        val sign = if (avgDelta > 0) "+" else ""
        val catLabel = topCat?.displayName ?: "chung"
        val msg = when {
            avgDelta > 10 -> "Thực tế trung bình dài hơn kế hoạch $sign$avgDelta% (lệch nhiều ở $catLabel · ${pctEligible.size} phiên)."
            avgDelta < -10 -> "Thực tế trung bình ngắn hơn kế hoạch $avgDelta% (ở $catLabel · ${pctEligible.size} phiên)."
            else -> "Ước lượng bám sát kế hoạch ($sign$avgDelta% qua ${pctEligible.size} phiên)."
        }
        return PlanAccuracySummary(
            sampleCount = compared.size,
            avgDeltaPercent = avgDelta,
            topDriftCategory = topCat,
            summaryText = msg,
            usedMinutesDisplayOnly = false,
            plannedMinutesTotal = eligiblePlannedTotal,
            actualMinutesTotal = eligibleActualTotal,
            deltaMinutesTotal = eligibleDiffTotal,
            sessionEvidence = evidenceList,
            excludedNote = combinedExcludedNote
        )
    }

    /**
     * Xây dựng ngữ cảnh bổ sung cho Wally AI, TUÂN THỦ NGHIÊM NGẶT [AiReadPermissions]:
     * nhóm bị tắt tuyệt đối không xuất hiện trong chuỗi gửi lên LLM.
     */
    fun buildPermissionAwareContextSuffix(
        perms: AiReadPermissions,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        records: List<LedgerRecord>,
        todayDateLocal: String
    ): String {
        val parts = mutableListOf<String>()
        if (perms.readTasks) {
            val openTasks = tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
            val inboxCount = openTasks.count { it.status == TaskStatus.INBOX }
            parts += "TASKS_OPEN=${openTasks.size} (INBOX=$inboxCount)"
            if (openTasks.isNotEmpty()) {
                val top3 = openTasks.take(3).joinToString("; ") { "${it.title}[${it.priority.name},${it.estimatedMinutes ?: 0}p]" }
                parts += "TOP_TASKS=$top3"
            }
        }
        if (perms.readHabits) {
            val activeHabits = habits.filter { !it.isArchived }
            val doneToday = activeHabits.count { isHabitCompletedOnDate(it, todayDateLocal, habitLogs, records) }
            parts += "HABITS_TODAY=$doneToday/${activeHabits.size}"
        }
        return if (parts.isEmpty()) "" else parts.joinToString(" | ")
    }

    private fun extractInlineTimeRange(raw: String): Pair<String, String>? {
        val tokens = raw.split(' ', ',', ';')
        for (tok in tokens) {
            val dashIdx = tok.indexOfFirst { it == '-' || it == '–' }
            if (dashIdx > 0 && dashIdx < tok.lastIndex) {
                val left = tok.substring(0, dashIdx)
                val right = tok.substring(dashIdx + 1)
                val sm = TimeRange.parseMinuteOfDay(left)
                val em = TimeRange.parseMinuteOfDay(right)
                if (sm != null && em != null && sm != em) {
                    return TimeRange.formatHm(sm) to TimeRange.formatHm(em)
                }
            }
        }
        return null
    }

    private fun containsWord(text: String, word: String): Boolean {
        val parts = text.split(' ', ',', '.', ';', '\n', '\t')
        return parts.any { it.equals(word, ignoreCase = true) }
    }

    private fun stripPriorityTokens(text: String): String {
        return text.split(' ')
            .filterNot { it.equals("P1", ignoreCase = true) || it.equals("P2", ignoreCase = true) || it.equals("P3", ignoreCase = true) }
            .joinToString(" ")
            .trim()
    }

    private fun collapseAsciiSpaces(raw: String): String {
        val sb = StringBuilder(raw.length)
        var prevSpace = true
        for (ch in raw) {
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
}
