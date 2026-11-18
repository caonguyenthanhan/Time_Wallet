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

data class PlanAccuracySummary(
    val sampleCount: Int,
    val avgDeltaPercent: Int,
    val topDriftCategory: TimeCategory?,
    val summaryText: String
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

            val startHour = Instant.ofEpochMilli(s).atZone(zoneId).hour.coerceIn(0, 23)
            val mins = ((e - s) / 1000L).coerceAtMost(8 * 3600L)
            hourBuckets[startHour] += mins
            totalFocusSec += mins
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
     * Tính độ chênh lệch trung bình giữa Kế hoạch (`plannedDurationSeconds`) và Thực tế (`durationSeconds`).
     */
    fun computePlanVsActualDelta(records: List<LedgerRecord>): PlanAccuracySummary {
        val compared = records.filter { r ->
            !r.isCancelled &&
                !r.isNeedsReconciliation &&
                r.layer != TimeLayer.PLANNED &&
                (r.plannedDurationSeconds ?: 0L) > 0L &&
                r.durationSeconds > 0L &&
                r.resolveConfirmationStateAt(System.currentTimeMillis()) in setOf(
                    ConfirmationState.COMPLETED,
                    ConfirmationState.ADJUSTED,
                    ConfirmationState.INTERRUPTED
                )
        }
        if (compared.isEmpty()) {
            return PlanAccuracySummary(
                sampleCount = 0,
                avgDeltaPercent = 0,
                topDriftCategory = null,
                summaryText = "Chưa có cặp Kế hoạch vs Thực tế hoàn tất để đối chiếu."
            )
        }
        val pctDeltas = compared.map { r ->
            val p = r.plannedDurationSeconds!!.toDouble()
            ((r.durationSeconds - p) / p * 100.0)
        }
        val avgDelta = pctDeltas.average().roundToInt()
        val topCat = compared.groupBy { it.category }
            .maxByOrNull { (_, list) ->
                list.map { kotlin.math.abs(it.durationSeconds - it.plannedDurationSeconds!!) }.average()
            }?.key
        val sign = if (avgDelta > 0) "+" else ""
        val catLabel = topCat?.displayName ?: "chung"
        val msg = when {
            avgDelta > 10 -> "Thực tế trung bình dài hơn kế hoạch $sign$avgDelta% (lệch nhiều ở $catLabel)."
            avgDelta < -10 -> "Thực tế trung bình ngắn hơn kế hoạch $avgDelta% (ở $catLabel)."
            else -> "Ước lượng bám sát kế hoạch ($sign$avgDelta% qua ${compared.size} phiên)."
        }
        return PlanAccuracySummary(
            sampleCount = compared.size,
            avgDeltaPercent = avgDelta,
            topDriftCategory = topCat,
            summaryText = msg
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
