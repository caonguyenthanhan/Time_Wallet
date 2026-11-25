package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.LocalCategoryRule
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ParsedNaturalTask(
    val title: String,
    val category: TimeCategory,
    val priority: TaskPriority,
    val estimatedMinutes: Int?,
    val dueDateLocal: String?,
    val dueTimeLocal: String?,
    val tagId: String?,
    val matchedRuleKeyword: String? = null
)

enum class TodoRiskKind(val shortLabel: String) {
    OVERDUE("Quá hạn"),
    DUE_TODAY_SOON("Gần hạn"),
    SCHEDULE_CONFLICT("Trùng lịch"),
    REPEATED_POSTPONE("Hoãn nhiều"),
    WORKLOAD_OVERFLOW("Quá tải ngày")
}

data class TodoRiskAlert(
    val taskId: String,
    val taskTitle: String,
    val kind: TodoRiskKind,
    val severityHigh: Boolean,
    val detailVi: String
)

enum class AiTodoProposalKind(val labelVi: String) {
    SCHEDULE_INTO_GAP("Xếp vào khoảng trống"),
    PRIORITIZE_TODAY("Đặt ưu tiên P1"),
    POSTPONE_TO_TOMORROW("Hoãn sang ngày mai"),
    SPLIT_SUBTASKS("Chia thành bước nhỏ")
}

data class AiTodoProposal(
    val id: String,
    val kind: AiTodoProposalKind,
    val taskId: String,
    val taskTitle: String,
    val reasonVi: String,
    val targetDateLocal: String? = null,
    val startHm: String? = null,
    val endHm: String? = null,
    val suggestedPriority: TaskPriority? = null,
    val suggestedSubtasks: List<String> = emptyList(),
    val categoryKey: String = TimeCategory.WORK.key,
    val conflictWarningVi: String? = null,
    val deadlineWarningVi: String? = null,
    val needsConfirmation: Boolean = true,
    val tagId: String? = null
)

data class AiTodoAnalysisReport(
    val generatedAtEpochMs: Long,
    val dateLocal: String,
    val fromCloudAi: Boolean,
    val summaryVi: String,
    val orderedTaskIds: List<String>,
    val risks: List<TodoRiskAlert>,
    val proposals: List<AiTodoProposal>,
    val permissionsFingerprint: String = ""
)

/**
 * Bộ máy AI To-do thuần Kotlin (JVM-testable, ZERO Regex ngoài Rx):
 * - Phân tích câu tự nhiên tiếng Việt thành To-do (deadline, giờ, thời lượng, mức ưu tiên, danh mục/Tag).
 * - Phát hiện rủi ro tất định trên máy: quá hạn, trùng khung giờ lịch trình, hoãn nhiều lần, vượt quỹ trống.
 * - Mọi đề xuất thay đổi (`AiTodoProposal`) đều đặt `needsConfirmation = true`, KHÔNG tự ghi vào DB.
 */
object AiTodoEngine {

    fun parseNaturalTaskInput(
        rawInput: String,
        today: LocalDate,
        tags: List<Tag> = emptyList(),
        rules: List<LocalCategoryRule> = emptyList()
    ): ParsedNaturalTask? {
        val normalized = collapseSpaces(rawInput)
        if (normalized.isEmpty()) return null

        val quick = TaskHabitEngine.parseQuickCaptureInput(
            rawInput = normalized,
            preferredKind = CaptureIntentKind.TASK_INBOX,
            tags = tags,
            rules = rules
        )
        val normAscii = TagRules.nameKey(normalized)

        val priority = when {
            hasWord(normAscii, "p1") || normAscii.contains("khan cap") || normAscii.contains("rat gap") || normAscii.contains("uu tien cao") -> TaskPriority.P1
            hasWord(normAscii, "p3") || normAscii.contains("khong gap") || normAscii.contains("uu tien thap") -> TaskPriority.P3
            else -> quick.priority
        }

        val dueDate = parseVietnameseDateCue(normAscii, today)
        val dueTime = quick.startHm ?: parseExplicitWallTimeHm(normalized)
        val estMinutes = extractEstimatedMinutes(normAscii, hasExplicitWallClock = dueTime != null)
            ?: quick.durationMinutes?.takeIf { !(dueTime != null && it > 480) }

        // Loại bớt các token ngày/ưu tiên dư khỏi tiêu đề nếu có thể nhưng giữ nguyên tối thiểu tiêu đề có nghĩa
        val cleanTitle = cleanTaskTitle(quick.cleanTitle, normalized)
        return ParsedNaturalTask(
            title = cleanTitle.take(110),
            category = quick.category,
            priority = priority,
            estimatedMinutes = estMinutes,
            dueDateLocal = dueDate?.toString() ?: if (dueTime != null) today.toString() else null,
            dueTimeLocal = dueTime,
            tagId = quick.tagId,
            matchedRuleKeyword = quick.matchedRuleKeyword
        )
    }

    fun analyzeWorkloadAndRisks(
        tasks: List<TaskItem>,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        todayLocal: String,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<TodoRiskAlert> {
        val openTasks = tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
        if (openTasks.isEmpty()) return emptyList()

        val todayDate = runCatching { LocalDate.parse(todayLocal) }.getOrElse { LocalDate.now(zoneId) }
        val nowZoned = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val nowMinute = nowZoned.hour * 60 + nowZoned.minute

        val dayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = todayDate,
            records = records,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            zone = zoneId
        )
        val remainingFreeMinutes = dayModel.freeGaps.sumOf { gap ->
            val s = maxOf(gap.startMinute, nowMinute)
            val e = minOf(gap.endMinute, 23 * 60)
            (e - s).coerceAtLeast(0)
        }

        val alerts = mutableListOf<TodoRiskAlert>()
        for (task in openTasks) {
            val dueDate = task.dueDateLocal?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (dueDate != null) {
                if (dueDate.isBefore(todayDate)) {
                    alerts += TodoRiskAlert(
                        taskId = task.id,
                        taskTitle = task.title,
                        kind = TodoRiskKind.OVERDUE,
                        severityHigh = true,
                        detailVi = "Đã quá hạn (${task.dueDateLocal}) — nên làm ngay hoặc dời ngày hợp lý."
                    )
                } else if (dueDate == todayDate) {
                    val dueMin = task.dueTimeLocal?.let { TimeRange.parseMinuteOfDay(it) }
                    if (dueMin != null) {
                        val est = (task.estimatedMinutes ?: 30).coerceIn(15, 240)
                        if (dueMin + est <= nowMinute) {
                            alerts += TodoRiskAlert(
                                taskId = task.id,
                                taskTitle = task.title,
                                kind = TodoRiskKind.OVERDUE,
                                severityHigh = true,
                                detailVi = "Mốc ${task.dueTimeLocal} hôm nay đã trôi qua."
                            )
                        } else if (dueMin - nowMinute in 0..90) {
                            alerts += TodoRiskAlert(
                                taskId = task.id,
                                taskTitle = task.title,
                                kind = TodoRiskKind.DUE_TODAY_SOON,
                                severityHigh = false,
                                detailVi = "Sắp tới mốc ${task.dueTimeLocal} (còn ${dueMin - nowMinute}p)."
                            )
                        }
                        // Kiểm tra trùng khung giờ với lịch trình có sẵn
                        val conflicting = dayModel.timedBlocks.firstOrNull { blk ->
                            blk.record?.taskId != task.id && blk.startMinute < dueMin + est && dueMin < blk.endMinute
                        }
                        if (conflicting != null) {
                            alerts += TodoRiskAlert(
                                taskId = task.id,
                                taskTitle = task.title,
                                kind = TodoRiskKind.SCHEDULE_CONFLICT,
                                severityHigh = true,
                                detailVi = "Mốc ${task.dueTimeLocal} trùng với \"${conflicting.title}\" (${ScheduleGridEngine.formatHmWindow(conflicting.startMinute, conflicting.endMinute)})."
                            )
                        }
                    }
                }
            }
            if (task.postponedCount >= 2) {
                alerts += TodoRiskAlert(
                    taskId = task.id,
                    taskTitle = task.title,
                    kind = TodoRiskKind.REPEATED_POSTPONE,
                    severityHigh = task.postponedCount >= 3,
                    detailVi = "Đã hoãn ${task.postponedCount} lần — cân nhắc chia thành bước 15–25p."
                )
            }
        }

        val dueTodayTasks = openTasks.filter { it.dueDateLocal == todayLocal || it.priority == TaskPriority.P1 }
        val neededMins = dueTodayTasks.sumOf { (it.estimatedMinutes ?: 30).coerceIn(10, 240) }
        if (dueTodayTasks.isNotEmpty() && neededMins > remainingFreeMinutes + 15) {
            alerts += TodoRiskAlert(
                taskId = dueTodayTasks.last().id,
                taskTitle = "Khối lượng hôm nay (${dueTodayTasks.size} việc)",
                kind = TodoRiskKind.WORKLOAD_OVERFLOW,
                severityHigh = neededMins > remainingFreeMinutes + 60,
                detailVi = "Cần ~${TimeRange.formatDuration(neededMins)} cho việc ưu tiên nhưng quỹ trống còn ${TimeRange.formatDuration(remainingFreeMinutes)}."
            )
        }
        return alerts
    }

    /**
     * Xây dựng đề xuất tái cấu trúc/xếp lịch dự kiến (100% ở trạng thái CHỜ XÁC NHẬN, không tự ghi).
     */
    fun buildDeterministicTodoProposals(
        tasks: List<TaskItem>,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        todayLocal: String,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<AiTodoProposal> {
        val openTasks = tasks
            .filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
            .sortedWith(
                compareBy<TaskItem>(
                    { if (it.dueDateLocal != null && it.dueDateLocal < todayLocal) 0 else 1 },
                    { it.priority.sortWeight },
                    { it.dueDateLocal ?: "9999-99-99" },
                    { it.createdAt }
                )
            )
        if (openTasks.isEmpty()) return emptyList()

        val todayDate = runCatching { LocalDate.parse(todayLocal) }.getOrElse { LocalDate.now(zoneId) }
        val tomorrowStr = todayDate.plusDays(1).toString()
        val nowMinute = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).let { it.hour * 60 + it.minute }
        val dayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = todayDate,
            records = records,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            zone = zoneId
        )
        val openGaps = dayModel.freeGaps
            .mapNotNull { g ->
                // Giữ đệm 5–19 phút trước mốc 15p kế tiếp để đề xuất không vừa hiện đã quá hạn khi bấm Áp dụng.
                val s = maxOf(g.startMinute, ((nowMinute + 19) / 15) * 15)
                val e = minOf(g.endMinute, 23 * 60)
                if (e - s >= 15) ScheduleFreeGap(todayLocal, s, e) else null
            }
            .toMutableList()

        val scheduledTaskIds = records
            .filter { !it.isCancelled && it.date == todayLocal && it.taskId != null }
            .mapNotNull { it.taskId }
            .toSet()

        val proposals = mutableListOf<AiTodoProposal>()
        for (task in openTasks) {
            if (proposals.size >= 5) break
            // 1. Việc hoãn nhiều lần hoặc quá lớn chưa có subtasks -> đề xuất chia nhỏ
            if ((task.postponedCount >= 2 || (task.estimatedMinutes ?: 0) >= 90) && task.subtasks.isEmpty()) {
                val est = (task.estimatedMinutes ?: 45).coerceIn(30, 180)
                val step = (est / 3).coerceAtLeast(10)
                proposals += AiTodoProposal(
                    id = "prop_split_${task.id}",
                    kind = AiTodoProposalKind.SPLIT_SUBTASKS,
                    taskId = task.id,
                    taskTitle = task.title,
                    reasonVi = if (task.postponedCount >= 2) "Đã hoãn ${task.postponedCount} lần, chia nhỏ giúp dễ bắt đầu" else "Thời lượng ~${est}p khá dài, nên tách 3 bước",
                    suggestedSubtasks = listOf(
                        "Phác khung & mục tiêu (${step}p)",
                        "Hoàn thành phần lõi (${step}p)",
                        "Rà soát & chốt (${(est - 2 * step).coerceAtLeast(10)}p)"
                    ),
                    categoryKey = task.category.key,
                    tagId = task.tagId,
                    needsConfirmation = true
                )
            }
            // 2. Xếp vào khoảng trống còn lại trong ngày nếu chưa có trên lịch hôm nay
            if (task.id !in scheduledTaskIds && openGaps.isNotEmpty()) {
                val need = (task.estimatedMinutes ?: 30).coerceIn(15, 120)
                val gIdx = openGaps.indexOfFirst { it.durationMinutes >= 15 }
                if (gIdx >= 0) {
                    val gap = openGaps[gIdx]
                    val startMin = gap.startMinute
                    val endMin = minOf(gap.endMinute, startMin + need)
                    if (endMin - startMin >= 15) {
                        val sHm = TimeRange.formatHm(startMin)
                        val eHm = TimeRange.formatHm(endMin)
                        val deadlineWarn = buildScheduleDeadlineNoteVi(
                            task = task,
                            targetDateLocal = todayLocal,
                            startHm = sHm,
                            endHm = eHm,
                            todayLocal = todayLocal,
                            nowEpochMs = nowEpochMs,
                            zoneId = zoneId
                        )
                        val baseReason = "Phù hợp khung trống ${ScheduleGridEngine.formatHmWindow(startMin, endMin)} hôm nay (${task.priority.badgeText})"
                        proposals += AiTodoProposal(
                            id = "prop_sched_${task.id}",
                            kind = AiTodoProposalKind.SCHEDULE_INTO_GAP,
                            taskId = task.id,
                            taskTitle = task.title,
                            reasonVi = if (deadlineWarn != null) "$baseReason · $deadlineWarn" else baseReason,
                            targetDateLocal = todayLocal,
                            startHm = sHm,
                            endHm = eHm,
                            categoryKey = task.category.key,
                            deadlineWarningVi = deadlineWarn,
                            needsConfirmation = true,
                            tagId = task.tagId
                        )
                        if (endMin < gap.endMinute) {
                            openGaps[gIdx] = ScheduleFreeGap(todayLocal, endMin + 5, gap.endMinute)
                        } else {
                            openGaps.removeAt(gIdx)
                        }
                        continue
                    }
                }
            }
            // 3. Nếu hết giờ trống hợp lý cho việc P2/P3 hôm nay -> đề xuất hoãn sang ngày mai
            if (task.dueDateLocal == todayLocal && task.priority != TaskPriority.P1 && openGaps.isEmpty()) {
                val oldDueLabel = buildString {
                    append(formatShortDateVi(task.dueDateLocal))
                    if (!task.dueTimeLocal.isNullOrBlank()) append(" ${task.dueTimeLocal}")
                }
                val newDueLabel = buildString {
                    append(formatShortDateVi(tomorrowStr))
                    if (!task.dueTimeLocal.isNullOrBlank()) append(" ${task.dueTimeLocal}")
                }
                proposals += AiTodoProposal(
                    id = "prop_postpone_${task.id}",
                    kind = AiTodoProposalKind.POSTPONE_TO_TOMORROW,
                    taskId = task.id,
                    taskTitle = task.title,
                    reasonVi = "Hết khoảng trống phù hợp hôm nay — đổi hạn $oldDueLabel → $newDueLabel",
                    targetDateLocal = tomorrowStr,
                    categoryKey = task.category.key,
                    tagId = task.tagId,
                    needsConfirmation = true
                )
            }
        }
        return proposals
    }

    /**
     * Tạo cảnh báo minh bạch khi khung giờ xếp lịch nằm sau hạn công việc hoặc công việc đã quá hạn.
     */
    fun buildScheduleDeadlineNoteVi(
        task: TaskItem,
        targetDateLocal: String,
        startHm: String,
        endHm: String,
        todayLocal: String,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String? {
        val dueDateStr = task.dueDateLocal ?: return null
        val dueDate = runCatching { LocalDate.parse(dueDateStr) }.getOrNull() ?: return null
        val targetDate = runCatching { LocalDate.parse(targetDateLocal) }.getOrNull() ?: return null
        val todayDate = runCatching { LocalDate.parse(todayLocal) }.getOrElse { LocalDate.now(zoneId) }
        val startMin = TimeRange.parseMinuteOfDay(startHm) ?: return null
        val endMin = TimeRange.parseMinuteOfDay(endHm) ?: return null
        val nowMinute = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).let { it.hour * 60 + it.minute }
        val dueMin = task.dueTimeLocal?.let { TimeRange.parseMinuteOfDay(it) }

        val isOverdueNow = dueDate.isBefore(todayDate) ||
            (dueDate == todayDate && dueMin != null && dueMin <= nowMinute)
        val isSlotAfterDeadline = targetDate.isAfter(dueDate) ||
            (targetDate == dueDate && dueMin != null && endMin > dueMin)

        if (!isOverdueNow && !isSlotAfterDeadline) return null

        val dueLabel = buildString {
            append(if (dueDate == todayDate) "hôm nay" else formatShortDateVi(dueDateStr))
            if (!task.dueTimeLocal.isNullOrBlank()) append(" ${task.dueTimeLocal}")
        }
        val overdueStatus = if (isOverdueNow) " (đã quá hạn)" else ""
        val slotRelation = when {
            targetDate.isAfter(dueDate) || (targetDate == dueDate && dueMin != null && startMin >= dueMin) ->
                "khung $startHm–$endHm nằm sau hạn"
            else ->
                "khung $startHm–$endHm kết thúc sau hạn"
        }
        return "Hạn $dueLabel$overdueStatus — $slotRelation (hạn gốc vẫn giữ nguyên)."
    }

    private fun formatShortDateVi(dateLocal: String): String {
        val parsed = runCatching { LocalDate.parse(dateLocal) }.getOrNull() ?: return dateLocal
        return "${parsed.dayOfMonth.toString().padStart(2, '0')}/${parsed.monthValue.toString().padStart(2, '0')}"
    }

    /**
     * Đồng bộ & kiểm tra lại các đề xuất AI To-do khi lịch trình hoặc công việc thực tế thay đổi.
     */
    fun revalidateTodoProposals(
        proposals: List<AiTodoProposal>,
        tasks: List<TaskItem>,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        todayLocal: String,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<AiTodoProposal> {
        val openById = tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED }.associateBy { it.id }
        val todayDate = runCatching { LocalDate.parse(todayLocal) }.getOrElse { LocalDate.now(zoneId) }
        val blocksByDate = mutableMapOf<String, List<ScheduleGridBlock>>()

        return proposals.mapNotNull { prop ->
            val task = openById[prop.taskId] ?: return@mapNotNull null
            if (prop.kind == AiTodoProposalKind.SCHEDULE_INTO_GAP && prop.targetDateLocal != null && prop.startHm != null && prop.endHm != null) {
                val startMin = TimeRange.parseMinuteOfDay(prop.startHm)
                val endMin = TimeRange.parseMinuteOfDay(prop.endHm)
                if (startMin == null || endMin == null || endMin <= startMin) return@mapNotNull null
                val dayBlocks = blocksByDate.getOrPut(prop.targetDateLocal) {
                    val d = runCatching { LocalDate.parse(prop.targetDateLocal) }.getOrDefault(todayDate)
                    ScheduleGridEngine.buildDayScheduleModel(d, records, activeTimer, nowEpochMs, zoneId).timedBlocks
                }
                val conflict = dayBlocks.firstOrNull { it.startMinute < endMin && startMin < it.endMinute }
                val deadlineWarn = buildScheduleDeadlineNoteVi(
                    task = task,
                    targetDateLocal = prop.targetDateLocal,
                    startHm = prop.startHm,
                    endHm = prop.endHm,
                    todayLocal = todayLocal,
                    nowEpochMs = nowEpochMs,
                    zoneId = zoneId
                )
                prop.copy(
                    taskTitle = task.title,
                    conflictWarningVi = conflict?.let { "Vừa phát sinh trùng lịch với \"${it.title}\"" },
                    deadlineWarningVi = deadlineWarn,
                    needsConfirmation = true
                )
            } else {
                prop.copy(taskTitle = task.title, needsConfirmation = true)
            }
        }
    }

    fun serializeReport(report: AiTodoAnalysisReport): String {
        val obj = JSONObject()
            .put("generatedAtEpochMs", report.generatedAtEpochMs)
            .put("dateLocal", report.dateLocal)
            .put("fromCloudAi", report.fromCloudAi)
            .put("summaryVi", report.summaryVi)
            .put("permissionsFingerprint", report.permissionsFingerprint)
            .put("orderedTaskIds", JSONArray(report.orderedTaskIds))

        val risksArr = JSONArray()
        report.risks.forEach { r ->
            risksArr.put(
                JSONObject()
                    .put("taskId", r.taskId)
                    .put("taskTitle", r.taskTitle)
                    .put("kind", r.kind.name)
                    .put("severityHigh", r.severityHigh)
                    .put("detailVi", r.detailVi)
            )
        }
        obj.put("risks", risksArr)

        val propArr = JSONArray()
        report.proposals.forEach { p ->
            propArr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("kind", p.kind.name)
                    .put("taskId", p.taskId)
                    .put("taskTitle", p.taskTitle)
                    .put("reasonVi", p.reasonVi)
                    .put("targetDateLocal", p.targetDateLocal ?: JSONObject.NULL)
                    .put("startHm", p.startHm ?: JSONObject.NULL)
                    .put("endHm", p.endHm ?: JSONObject.NULL)
                    .put("suggestedPriority", p.suggestedPriority?.name ?: JSONObject.NULL)
                    .put("suggestedSubtasks", JSONArray(p.suggestedSubtasks))
                    .put("categoryKey", p.categoryKey)
                    .put("tagId", p.tagId ?: JSONObject.NULL)
                    .put("conflictWarningVi", p.conflictWarningVi ?: JSONObject.NULL)
                    .put("deadlineWarningVi", p.deadlineWarningVi ?: JSONObject.NULL)
            )
        }
        obj.put("proposals", propArr)
        return obj.toString()
    }

    fun deserializeReport(rawJson: String?): AiTodoAnalysisReport? {
        val raw = rawJson?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val ordered = buildList {
                val arr = obj.optJSONArray("orderedTaskIds") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            val risks = buildList {
                val arr = obj.optJSONArray("risks") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    val kind = runCatching { TodoRiskKind.valueOf(r.optString("kind")) }.getOrNull() ?: continue
                    add(
                        TodoRiskAlert(
                            taskId = r.optString("taskId", ""),
                            taskTitle = r.optString("taskTitle", ""),
                            kind = kind,
                            severityHigh = r.optBoolean("severityHigh", false),
                            detailVi = r.optString("detailVi", "")
                        )
                    )
                }
            }
            val props = buildList {
                val arr = obj.optJSONArray("proposals") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val kind = runCatching { AiTodoProposalKind.valueOf(p.optString("kind")) }.getOrNull() ?: continue
                    val subs = buildList {
                        val sa = p.optJSONArray("suggestedSubtasks") ?: JSONArray()
                        for (j in 0 until sa.length()) {
                            sa.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    add(
                        AiTodoProposal(
                            id = p.optString("id", UUID.randomUUID().toString()),
                            kind = kind,
                            taskId = p.optString("taskId", ""),
                            taskTitle = p.optString("taskTitle", ""),
                            reasonVi = p.optString("reasonVi", ""),
                            targetDateLocal = p.optString("targetDateLocal", "").takeIf { it.isNotBlank() && it != "null" },
                            startHm = p.optString("startHm", "").takeIf { it.isNotBlank() && it != "null" },
                            endHm = p.optString("endHm", "").takeIf { it.isNotBlank() && it != "null" },
                            suggestedPriority = p.optString("suggestedPriority", "").takeIf { it.isNotBlank() && it != "null" }?.let(TaskPriority::fromStorage),
                            suggestedSubtasks = subs,
                            categoryKey = p.optString("categoryKey", TimeCategory.WORK.key),
                            conflictWarningVi = p.optString("conflictWarningVi", "").takeIf { it.isNotBlank() && it != "null" },
                            deadlineWarningVi = p.optString("deadlineWarningVi", "").takeIf { it.isNotBlank() && it != "null" },
                            needsConfirmation = true,
                            tagId = p.optString("tagId", "").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
            AiTodoAnalysisReport(
                generatedAtEpochMs = obj.optLong("generatedAtEpochMs", 0L),
                dateLocal = obj.optString("dateLocal", ""),
                fromCloudAi = obj.optBoolean("fromCloudAi", false),
                summaryVi = obj.optString("summaryVi", ""),
                orderedTaskIds = ordered,
                risks = risks,
                proposals = props,
                permissionsFingerprint = obj.optString("permissionsFingerprint", "")
            )
        }.getOrNull()
    }

    private fun parseVietnameseDateCue(normAscii: String, today: LocalDate): LocalDate? {
        if (normAscii.contains("hom nay")) return today
        if (normAscii.contains("ngay mai") || hasWord(normAscii, "mai")) return today.plusDays(1)
        if (normAscii.contains("ngay kia") || hasWord(normAscii, "mot")) return today.plusDays(2)
        if (normAscii.contains("cuoi tuan")) {
            val delta = (DayOfWeek.SATURDAY.value - today.dayOfWeek.value + 7) % 7
            return today.plusDays(if (delta == 0) 0L else delta.toLong())
        }
        val dowMap = listOf(
            listOf("thu 2", "thu hai", "t2") to DayOfWeek.MONDAY,
            listOf("thu 3", "thu ba", "t3") to DayOfWeek.TUESDAY,
            listOf("thu 4", "thu tu", "t4") to DayOfWeek.WEDNESDAY,
            listOf("thu 5", "thu nam", "t5") to DayOfWeek.THURSDAY,
            listOf("thu 6", "thu sau", "t6") to DayOfWeek.FRIDAY,
            listOf("thu 7", "thu bay", "t7") to DayOfWeek.SATURDAY,
            listOf("chu nhat", "cn") to DayOfWeek.SUNDAY
        )
        for ((keys, dow) in dowMap) {
            if (keys.any { k -> hasPhrase(normAscii, k) }) {
                val delta = (dow.value - today.dayOfWeek.value + 7) % 7
                return today.plusDays(delta.toLong())
            }
        }
        // Dạng dd/MM
        val tokens = normAscii.split(' ')
        for (tok in tokens) {
            val slash = tok.indexOf('/')
            if (slash > 0 && slash < tok.lastIndex) {
                val d = tok.substring(0, slash).filter(Char::isDigit).toIntOrNull()
                val m = tok.substring(slash + 1).filter(Char::isDigit).toIntOrNull()
                if (d != null && m != null && d in 1..31 && m in 1..12) {
                    val cand = runCatching { LocalDate.of(today.year, m, d) }.getOrNull()
                    if (cand != null) return cand
                }
            }
        }
        return null
    }

    private fun parseExplicitWallTimeHm(raw: String): String? {
        val tokens = raw.split(' ', ',', ';')
        val cuePrefixes = setOf("luc", "lúc", "tu", "từ", "den", "đến", "truoc", "trước", "han", "hạn", "vao", "vào", "gio", "giờ", "mai", "nay", "kia", "sang", "sáng", "trua", "trưa", "chieu", "chiều", "toi", "tối")
        for (i in tokens.indices) {
            val tok = tokens[i].trim().lowercase()
            if (tok.isEmpty() || tok.endsWith("p") || tok.endsWith("m")) continue
            val prev = tokens.getOrNull(i - 1)?.trim()?.lowercase().orEmpty()
            val next = tokens.getOrNull(i + 1)?.trim()?.lowercase().orEmpty()
            val hIdx = tok.indexOf('h')
            if (tok.contains(':')) {
                val mins = TimeRange.parseMinuteOfDay(tok)
                if (mins != null && mins in 0 until 1440) return TimeRange.formatHm(mins)
            } else if (hIdx > 0) {
                val hPart = tok.substring(0, hIdx)
                val mPart = tok.substring(hIdx + 1)
                if (hPart.all(Char::isDigit) && mPart.all(Char::isDigit)) {
                    val rawH = hPart.toIntOrNull()
                    val m = if (mPart.isEmpty()) 0 else mPart.toIntOrNull()
                    if (rawH != null && rawH in 0..23 && m != null && m in 0..59) {
                        val h = when {
                            rawH in 1..11 && (next == "chiều" || next == "chieu" || next == "tối" || next == "toi" || next == "đêm" || next == "dem") -> rawH + 12
                            rawH == 12 && (next == "sáng" || next == "sang" || next == "đêm" || next == "dem") -> 0
                            else -> rawH
                        }
                        val hasCue = prev in cuePrefixes || h >= 7 || mPart.isNotEmpty() || next in setOf("sáng", "sang", "trưa", "trua", "chiều", "chieu", "tối", "toi", "đêm", "dem")
                        if (hasCue) {
                            return TimeRange.formatHm(h * 60 + m)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractEstimatedMinutes(normAscii: String, hasExplicitWallClock: Boolean): Int? {
        val tokens = normAscii.split(' ', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        var totalMins = 0
        var matchedAny = false
        for (i in tokens.indices) {
            val tok = tokens[i]
            val next = tokens.getOrNull(i + 1).orEmpty()
            // "45p", "30m"
            if ((tok.endsWith("p") || tok.endsWith("m")) && tok.length >= 2) {
                val nStr = tok.dropLast(1)
                if (nStr.all(Char::isDigit)) {
                    val m = nStr.toIntOrNull()
                    if (m != null && m in 1..480) {
                        totalMins += m
                        matchedAny = true
                    }
                }
                continue
            }
            // "45 phut" / "45 min"
            if (tok.all(Char::isDigit) && (next == "phut" || next == "min")) {
                val m = tok.toIntOrNull()
                if (m != null && m in 1..480) {
                    totalMins += m
                    matchedAny = true
                }
                continue
            }
            // "1h30p"
            val hIdx = tok.indexOf('h')
            if (hIdx > 0 && tok.endsWith("p")) {
                val h = tok.substring(0, hIdx).toIntOrNull()
                val m = tok.substring(hIdx + 1, tok.length - 1).toIntOrNull()
                if (h != null && m != null && h in 0..8 && m in 0..59) {
                    totalMins += h * 60 + m
                    matchedAny = true
                }
                continue
            }
            // "2 tieng" / "2 gio" (tránh nhầm "15h" khi đã tách giờ đồng hồ)
            if (tok.all(Char::isDigit) && next == "tieng") {
                val h = tok.toIntOrNull()
                if (h != null && h in 1..8) {
                    totalMins += h * 60
                    matchedAny = true
                }
                continue
            }
            if (!hasExplicitWallClock && tok.endsWith("h") && tok.length in 2..2) {
                val h = tok.dropLast(1).toIntOrNull()
                if (h != null && h in 1..6) {
                    totalMins += h * 60
                    matchedAny = true
                }
            }
        }
        return if (matchedAny && totalMins in 5..480) totalMins else null
    }

    private fun cleanTaskTitle(quickClean: String, original: String): String {
        var cleaned = original.trim().ifBlank { quickClean }
        // Gỡ cụm hạn/thời điểm, ngày, thời lượng, mức ưu tiên để giữ tên việc gọn sạch (vd: "Soạn báo cáo").
        val patterns = listOf(
            Rx.uni("""(?:^|[\s,;])(?:trước|truoc|hạn|han|lúc|luc|vào|vao|từ|tu|đến|den|tới|toi)?\s*\d{1,2}(?::\d{2}|h\d{0,2})(?:\s*(?:giờ\s*|gio\s*)?(?:sáng|sang|trưa|trua|chiều|chieu|tối|toi|đêm|dem))?(?=\s|$|[,.;:!?])""", RegexOption.IGNORE_CASE),
            Rx.uni("""(?:^|[\s,;])(?:cần|can|khoảng|khoang|dự\s*kiến|du\s*kien|tầm|tam|mất|mat)?\s*(?:\d+\s*h\s*\d*\s*p?|\d+\s*(?:phút|phut|p|min|tiếng|tieng|giờ|gio))(?=\s|$|[,.;:!?])""", RegexOption.IGNORE_CASE),
            Rx.uni("""(?:^|[\s,;])(?:ngày\s*hôm\s*nay|ngay\s*hom\s*nay|hôm\s*nay|hom\s*nay|ngày\s*mai|ngay\s*mai|ngày\s*kia|ngay\s*kia|cuối\s*tuần|cuoi\s*tuan|chủ\s*nhật|chu\s*nhat|thứ\s*[2-7]|thu\s*[2-7]|thứ\s*(?:hai|ba|tư|tu|năm|nam|sáu|sau|bảy|bay))(?=\s|$|[,.;:!?])""", RegexOption.IGNORE_CASE),
            Rx.uni("""(?:^|[\s,;])\d{1,2}/\d{1,2}(?=\s|$|[,.;:!?])""", RegexOption.IGNORE_CASE),
            Rx.uni("""(?:^|[\s,;])(?:ưu\s*tiên\s*cao|uu\s*tien\s*cao|ưu\s*tiên\s*thấp|uu\s*tien\s*thap|khẩn\s*cấp|khan\s*cap|rất\s*gấp|rat\s*gap|không\s*gấp|khong\s*gap|p1|p2|p3)(?=\s|$|[,.;:!?])""", RegexOption.IGNORE_CASE),
            Rx.uni("""(?:^|\s)(?:mai|mốt|mot)(?=\s|$|[,.;:!?])""")
        )
        for (rx in patterns) {
            cleaned = rx.replace(cleaned, " ")
        }
        // Dọn từ nối còn sót ở cuối câu sau khi bóc thời hạn/thời lượng
        cleaned = Rx.uni("""(?:[\s,;]+(?:trước|truoc|hạn|han|lúc|luc|vào|vao|cần|can|dự\s*kiến|khoảng|tầm))+$""", RegexOption.IGNORE_CASE).replace(cleaned, "")
        cleaned = collapseSpaces(cleaned.replace(Rx.uni("""\s*[,;]+\s*"""), ", ").trim(' ', ',', ';', '-', '–', ':'))
        return cleaned.ifBlank { quickClean.ifBlank { original.trim() } }
    }

    private fun hasWord(haystack: String, word: String): Boolean =
        haystack.split(' ', ',', ';', '.', '!', '?').any { it == word }

    private fun hasPhrase(haystack: String, phrase: String): Boolean =
        (" $haystack ").contains(" $phrase ")

    private fun collapseSpaces(raw: String): String {
        val sb = StringBuilder(raw.length)
        var prevSpace = true
        for (ch in raw.trim()) {
            if (ch.isWhitespace()) {
                if (!prevSpace) {
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
