package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.Goal
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.GoalVersionStatus
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class GoalDeficitSummary(
    val goalLabel: String,
    val categoryKey: String,
    val kind: GoalKind,
    val period: GoalPeriod,
    val targetMinutes: Int,
    val usedMinutes: Int,
    val confirmedMinutes: Int,
    val remainingMinutes: Int,
    val overMinutes: Int
)

data class FeasibleSlot(
    val dateLocal: String,
    val startMinute: Int,
    val endMinute: Int,
    val startHm: String,
    val endHm: String,
    val durationMinutes: Int
)

/**
 * Bản nháp đề xuất đang chờ làm rõ / chỉnh sửa nối tiếp qua hội thoại (chỉ nằm trong RAM).
 */
data class PendingProposalContext(
    val draftId: String? = null,
    val title: String,
    val categoryKey: String,
    val tagId: String? = null,
    val tagName: String? = null,
    val dateLocal: String,
    val startLocal: String? = null,
    val endLocal: String? = null,
    val durationMinutes: Int? = null,
    val isFuturePlanned: Boolean = true,
    val taskId: String? = null,
    val awaitingFields: Set<String> = emptySet(),
    val explicitTagCleared: Boolean = false
)

/**
 * Trạng thái hội thoại giới hạn có TTL và gắn chặt với dấu vân tay quyền [AiReadPermissions].
 * Chỉ giữ tóm tắt cấu trúc tối thiểu trong RAM (không lưu toàn bộ lịch sử hay sổ cái).
 */
data class ConversationState(
    val isAdvisoryTopic: Boolean = false,
    val topicLabel: String? = null,
    val focusCategory: TimeCategory? = null,
    val targetDate: LocalDate? = null,
    val dayWindow: DayTimeWindow? = null,
    val lastQuery: QueryContext? = null,
    val lastCoachSummary: String? = null,
    val lastRecommendationHeadline: String? = null,
    val candidateSlots: List<FeasibleSlot> = emptyList(),
    val updatedAtEpochMs: Long = 0L,
    val ttlMs: Long = DEFAULT_TTL_MS,
    val permissionsFingerprint: String = "",
    val focusTag: Tag? = null,
    val pendingProposal: PendingProposalContext? = null
) {
    val hasAdvisoryTopic: Boolean
        get() = pendingProposal != null || (isAdvisoryTopic && (!topicLabel.isNullOrBlank() ||
            focusCategory != null ||
            focusTag != null ||
            !lastRecommendationHeadline.isNullOrBlank() ||
            candidateSlots.isNotEmpty()))

    fun isValidFor(nowEpochMs: Long, perms: AiReadPermissions): Boolean {
        if (updatedAtEpochMs <= 0L) return false
        if (nowEpochMs - updatedAtEpochMs > ttlMs) return false
        return permissionsFingerprint == perms.fingerprint()
    }

    companion object {
        const val DEFAULT_TTL_MS = 30L * 60_000L
        val EMPTY = ConversationState()
    }
}

/** Bản sao tuần tự hóa của trạng thái hội thoại dùng chung giữa Chat nổi và Chat trong app. */
data class PersistedChatConversation(
    val pendingProposal: PendingProposalContext? = null,
    val lastQuery: QueryContext? = null,
    val focusTagId: String? = null,
    val topicLabel: String? = null,
    val focusCategoryKey: String? = null,
    val targetDateLocal: String? = null,
    val isAdvisoryTopic: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val permissionsFingerprint: String = "",
    val clarificationQuestion: String? = null,
    val clarificationSuggestions: List<String> = emptyList()
)

fun AiReadPermissions.fingerprint(): String =
    "L${if (readLedger) 1 else 0}T${if (readTasks) 1 else 0}H${if (readHabits) 1 else 0}G${if (readGoals) 1 else 0}"

data class AdaptivePromptContext(
    val formattedBlock: String,
    val backedMinutes: Set<Int>,
    val goalDeficits: List<GoalDeficitSummary>,
    val feasibleSlots: List<FeasibleSlot>,
    val targetDate: LocalDate,
    val dayWindow: DayTimeWindow,
    val contextInstruction: String = DEFAULT_ADAPTIVE_INSTRUCTION
) {
    companion object {
        const val DEFAULT_ADAPTIVE_INSTRUCTION =
            "[HƯỚNG DẪN TƯ VẤN NGỮ CẢNH]\n" +
                "- Với câu hỏi tư vấn/lập kế hoạch này, bạn ĐƯỢC PHÉP nêu trực tiếp số phút mục tiêu còn thiếu và các khung giờ trống dạng HH:mm-HH:mm lấy từ [NGỮ CẢNH THÔNG MINH WALLY] bên trên vào \"reply\" (tối đa 3 câu ngắn, không dùng phần trăm, không viết số bằng chữ).\n" +
                "- Nếu có khung giờ trống phù hợp và người dùng hỏi nên làm gì/xếp lúc nào, hãy tạo tối đa 1 proposal dự kiến (startLocal/endLocal nằm gọn trong một khung trống ở trên, timeEvidence=\"explicit_range\", needsConfirmation=true). Tuyệt đối không nói đã ghi/lưu."
    }
}

/**
 * Bộ máy Ngữ cảnh & Ra quyết định thống nhất cho Wally (Context Engine + Decision Engine).
 *
 * Nguyên tắc:
 * - Zero-leak: nhóm quyền nào tắt tuyệt đối không đọc/tính/nhúng dữ liệu của nhóm đó.
 * - Tất định: mọi phép tính thiếu hụt mục tiêu, slot trống không chồng lấn (`ScheduleGridEngine`) và kiểm tra xung đột chạy trên máy.
 * - Bounded: giới hạn cứng độ dài khối ngữ cảnh để giữ payload Chat nhanh và tiết kiệm token.
 */
object WallyContextEngine {

    const val MAX_CONTEXT_CHARS = 1_100
    private val dmFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
    private val vi = Locale("vi", "VN")

    private fun mins(seconds: Long): Int = ((seconds + 30L) / 60L).toInt()

    fun buildFilteredCoachDigest(
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        goals: List<Goal>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        days: Int,
        nowMs: Long,
        zone: ZoneId
    ): CoachDigest {
        val base = CoachDigestBuilder.build(
            records = if (perms.readLedger) records else emptyList(),
            tags = tags,
            goals = if (perms.readGoals) goals else emptyList(),
            days = days,
            nowEpochMs = nowMs,
            zone = zone
        )
        val todayLocal = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
        val extra = TaskHabitEngine.buildPermissionAwareContextSuffix(
            perms = perms,
            tasks = tasks,
            habits = habits,
            habitLogs = habitLogs,
            records = if (perms.readLedger) records else emptyList(),
            todayDateLocal = todayLocal
        )
        return if (extra.isBlank()) base else base.copy(text = "${base.text}\n$extra")
    }

    fun buildAdaptiveChatContext(
        route: ContextRouteDecision,
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        tags: List<Tag>,
        goals: List<Goal>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        activeTimer: ActiveTimerSession?,
        conversationState: ConversationState?,
        today: LocalDate,
        nowEpochMs: Long,
        zone: ZoneId
    ): AdaptivePromptContext {
        val targetDate = route.targetDate ?: today
        val tagById = tags.associateBy { it.id }
        val safeRecords = if (perms.readLedger) records else emptyList()
        val backedMinutes = LinkedHashSet<Int>()

        val deficits = if (route.includeGoals && perms.readGoals) {
            computeGoalDeficits(
                goals = goals,
                records = safeRecords,
                tagById = tagById,
                today = today,
                focusCategory = route.focusCategory,
                nowEpochMs = nowEpochMs,
                zone = zone,
                activeTimer = activeTimer
            )
        } else emptyList()

        deficits.forEach { g ->
            backedMinutes += g.targetMinutes
            backedMinutes += g.usedMinutes
            backedMinutes += g.confirmedMinutes
            backedMinutes += g.remainingMinutes
            if (g.overMinutes > 0) backedMinutes += g.overMinutes
        }

        val slots = if (route.includeScheduleGaps && perms.readLedger) {
            computeFeasibleSlots(
                targetDate = targetDate,
                dayWindow = route.dayWindow,
                records = safeRecords,
                activeTimer = activeTimer,
                today = today,
                nowEpochMs = nowEpochMs,
                zone = zone
            )
        } else emptyList()

        slots.forEach { s ->
            backedMinutes += s.durationMinutes
            backedMinutes += s.startMinute
            backedMinutes += s.endMinute
            if (s.endMinute >= 1440) backedMinutes += 0
        }

        val openTasks = if (route.includeTasks && perms.readTasks) {
            selectPriorityTasks(tasks, route.focusCategory)
        } else emptyList()
        openTasks.forEach { t -> t.estimatedMinutes?.let { backedMinutes += it } }

        val incompleteHabits = if (route.includeHabits && perms.readHabits) {
            selectPendingHabits(habits, habitLogs, safeRecords, targetDate.toString())
        } else emptyList()
        incompleteHabits.forEach { h -> backedMinutes += h.minSessionMinutes }

        val validContinuity = conversationState?.takeIf { it.isValidFor(nowEpochMs, perms) }
        val pendingDraft = validContinuity?.pendingProposal
        pendingDraft?.durationMinutes?.let { backedMinutes += it }
        pendingDraft?.startLocal?.let { hm ->
            TimeRange.parseMinuteOfDay(hm)?.let { backedMinutes += it }
        }

        val anchorClockMinutes = if (route.includeScheduleGaps && slots.isNotEmpty()) {
            listOf(9 * 60, 14 * 60, 19 * 60, 20 * 60, 21 * 60).filter { m ->
                slots.any { sl -> sl.startMinute <= m && sl.endMinute >= m + 30 }
            }.also { anchors -> anchors.forEach { backedMinutes += it } }
        } else emptyList()

        val sb = StringBuilder()
        sb.append("[NGỮ CẢNH THÔNG MINH WALLY]\n")
        sb.append("- Ngày xét: ${targetDate} (${targetDate.format(dmFmt)}) · Khung: ${route.dayWindow.labelVi}\n")

        val activeFocusTag = route.focusTag ?: validContinuity?.focusTag
        if (activeFocusTag != null) {
            sb.append("- Tag người dùng nêu: ${activeFocusTag.name} (nhóm=${activeFocusTag.category.displayName})\n")
        }

        if (pendingDraft != null) {
            sb.append("- Đề xuất đang mở cần làm rõ/cập nhật: tiêu đề=\"${pendingDraft.title}\", ngày=${pendingDraft.dateLocal}")
            sb.append(", nhóm=${pendingDraft.categoryKey}")
            if (!pendingDraft.tagName.isNullOrBlank()) sb.append(", tag=\"${pendingDraft.tagName}\"")
            pendingDraft.startLocal?.let { sb.append(", giờ bắt đầu=$it") }
            pendingDraft.durationMinutes?.let { sb.append(", thời lượng=${it}p") }
            if (pendingDraft.awaitingFields.isNotEmpty()) {
                sb.append(", còn thiếu=${pendingDraft.awaitingFields.joinToString("/")}")
            }
            sb.append('\n')
        }

        if (validContinuity != null && route.includeContinuity && validContinuity.hasAdvisoryTopic) {
            val parts = buildList {
                validContinuity.topicLabel?.let { add("chủ đề=$it") }
                validContinuity.focusCategory?.let { add("nhóm=${it.displayName}") }
                validContinuity.targetDate?.let { add("ngày trước=${it.format(dmFmt)}") }
            }
            if (parts.isNotEmpty()) {
                sb.append("- Hội thoại đang nối tiếp: ${parts.joinToString(", ")}\n")
            }
        }

        if (validContinuity != null && route.includeCoachSummary && !validContinuity.lastCoachSummary.isNullOrBlank()) {
            sb.append("- Coach gần nhất: ${validContinuity.lastCoachSummary.take(150)}\n")
        }

        if (!perms.readGoals) {
            sb.append("- Mục tiêu: chưa bật quyền đọc.\n")
        } else if (deficits.isNotEmpty()) {
            sb.append("- Mục tiêu cần chú ý:\n")
            deficits.take(3).forEach { g ->
                val periodVi = if (g.period == GoalPeriod.DAY) "hôm nay" else "tuần này"
                val statusVi = if (g.kind == GoalKind.MIN) {
                    if (g.remainingMinutes > 0) "đã ${g.usedMinutes}p/${g.targetMinutes}p, còn thiếu ${g.remainingMinutes}p"
                    else "đã đủ ${g.usedMinutes}p/${g.targetMinutes}p"
                } else {
                    if (g.overMinutes > 0) "đã ${g.usedMinutes}p, vượt ${g.overMinutes}p"
                    else "đã ${g.usedMinutes}p/${g.targetMinutes}p, còn ${g.remainingMinutes}p"
                }
                sb.append("  * ${g.goalLabel} ($periodVi): $statusVi\n")
            }
        } else if (route.includeGoals) {
            sb.append("- Mục tiêu: chưa có mục tiêu tối thiểu cần bù thêm.\n")
        }

        if (!perms.readLedger) {
            sb.append("- Lịch trống: chưa bật quyền đọc Sổ cái.\n")
        } else if (slots.isNotEmpty()) {
            sb.append("- Khung giờ trống khả thi ngày ${targetDate.format(dmFmt)} (không trùng lịch):\n")
            slots.take(4).forEach { sl ->
                sb.append("  * ${sl.startHm}-${sl.endHm} (${sl.durationMinutes}p)\n")
            }
            if (anchorClockMinutes.isNotEmpty()) {
                val anchorLabels = anchorClockMinutes.take(3).joinToString(", ") { TimeRange.formatHm(it) }
                sb.append("- Mốc giờ gợi ý trong khoảng trống: $anchorLabels\n")
            }
        } else if (route.includeScheduleGaps) {
            sb.append("- Khung giờ trống ngày ${targetDate.format(dmFmt)}: không còn slot trống >= 15p trong khung này.\n")
        }

        if (perms.readTasks && openTasks.isNotEmpty()) {
            sb.append("- Việc mở ưu tiên: ")
            sb.append(
                openTasks.joinToString("; ") { t ->
                    val est = t.estimatedMinutes?.let { ",${it}p" } ?: ""
                    "${t.title}[${t.priority.name}$est]"
                }
            ).append('\n')
        }

        if (perms.readHabits && incompleteHabits.isNotEmpty()) {
            sb.append("- Thói quen chưa xong: ")
            sb.append(incompleteHabits.joinToString("; ") { "${it.title}(${it.minSessionMinutes}p)" })
                .append('\n')
        }

        if (route.includeTagAssignmentCandidates && perms.readLedger) {
            val candidates = WallyTagAssignmentEngine.findCandidateRecordsForContext(
                userText = "",
                records = safeRecords,
                today = today,
                zone = zone,
                limit = 6
            )
            if (candidates.isNotEmpty()) {
                sb.append("- Các phiên đã lưu gần đây (chỉ để đối chiếu gắn Tag khi người dùng yêu cầu):\n")
                candidates.forEach { rec ->
                    val hm = rec.startedAt?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    } ?: "chưa gắn giờ"
                    val tagLabel = rec.tagId?.let { id -> tagById[id]?.name } ?: "chưa gắn"
                    val durMin = ((rec.durationSeconds + 30L) / 60L).coerceAtLeast(1L)
                    sb.append("  * \"${rec.title.take(40)}\" | ${rec.date} | $hm | ${durMin}p | tag=$tagLabel\n")
                }
            }
        }

        val cappedText = capContextBlock(sb.toString().trimEnd())
        return AdaptivePromptContext(
            formattedBlock = cappedText,
            backedMinutes = backedMinutes,
            goalDeficits = deficits,
            feasibleSlots = slots,
            targetDate = targetDate,
            dayWindow = route.dayWindow
        )
    }

    internal fun computeGoalDeficits(
        goals: List<Goal>,
        records: List<LedgerRecord>,
        tagById: Map<String, Tag>,
        today: LocalDate,
        focusCategory: TimeCategory?,
        nowEpochMs: Long,
        zone: ZoneId,
        activeTimer: ActiveTimerSession? = null
    ): List<GoalDeficitSummary> {
        val grouped = goals.groupBy { it.groupId }
        val result = mutableListOf<GoalDeficitSummary>()
        for ((_, versions) in grouped) {
            if (GoalRules.groupState(versions) != GoalGroupState.ACTIVE) continue
            val activeGoal = GoalRules.latest(versions) ?: continue
            if (activeGoal.status != GoalVersionStatus.ACTIVE) continue
            val label = GoalRules.displayName(activeGoal) { id -> tagById[id]?.name }
            val categoryKey = when (activeGoal.targetType) {
                com.timewallet.mvp.domain.model.GoalTargetType.TAG ->
                    tagById[activeGoal.targetKey]?.category?.key ?: TimeCategory.OTHER.key
                com.timewallet.mvp.domain.model.GoalTargetType.CATEGORY -> activeGoal.targetKey
            }
            val progress = GoalRules.progress(activeGoal, records, today, nowEpochMs, zone, activeTimer = activeTimer)
            val (pStart, pEnd) = GoalRules.periodBounds(activeGoal.period, today)
            val confSec = GoalRules.confirmedUsedSeconds(
                activeGoal.targetType, activeGoal.targetKey, records, pStart, pEnd, nowEpochMs, zone
            )
            result += GoalDeficitSummary(
                goalLabel = label,
                categoryKey = categoryKey,
                kind = activeGoal.kind,
                period = activeGoal.period,
                targetMinutes = mins(activeGoal.targetSeconds),
                usedMinutes = mins(progress.usedSeconds),
                confirmedMinutes = mins(confSec),
                remainingMinutes = mins(progress.remainingSeconds),
                overMinutes = mins(progress.overSeconds)
            )
        }
        return result.sortedWith(
            compareByDescending<GoalDeficitSummary> {
                focusCategory != null && it.categoryKey == focusCategory.key
            }.thenByDescending {
                it.kind == GoalKind.MIN && it.remainingMinutes > 0
            }.thenByDescending {
                it.remainingMinutes
            }.thenBy {
                it.goalLabel.lowercase(vi)
            }
        )
    }

    internal fun computeFeasibleSlots(
        targetDate: LocalDate,
        dayWindow: DayTimeWindow,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        today: LocalDate,
        nowEpochMs: Long,
        zone: ZoneId
    ): List<FeasibleSlot> {
        if (targetDate.isBefore(today)) return emptyList()
        val dayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = targetDate,
            records = records,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            zone = zone
        )
        val nowMinute = Instant.ofEpochMilli(nowEpochMs).atZone(zone).let { it.hour * 60 + it.minute }
        val minAllowedStart = if (targetDate == today) ((nowMinute + 14) / 15) * 15 else 0

        fun clipGaps(winStart: Int, winEnd: Int): List<FeasibleSlot> = dayModel.freeGaps.mapNotNull { gap ->
            val s = maxOf(gap.startMinute, minAllowedStart, winStart)
            val e = minOf(gap.endMinute, winEnd)
            val dur = e - s
            if (dur < 15) return@mapNotNull null
            FeasibleSlot(
                dateLocal = targetDate.toString(),
                startMinute = s,
                endMinute = e,
                startHm = TimeRange.formatHm(s.coerceIn(0, 1439)),
                endHm = if (e >= 1440) "00:00" else TimeRange.formatHm(e.coerceIn(1, 1439)),
                durationMinutes = dur
            )
        }

        val windowed = clipGaps(dayWindow.startMinute, dayWindow.endMinute)
        val chosen = if (windowed.isNotEmpty() || dayWindow == DayTimeWindow.ANY) {
            windowed
        } else {
            // Nếu khung yêu cầu (vd buổi tối) đã kín lịch, trả về các slot còn lại trong ngày để người dùng có phương án thay thế.
            clipGaps(minAllowedStart, 24 * 60)
        }
        return chosen.sortedBy { it.startMinute }.take(4)
    }

    private fun selectPriorityTasks(tasks: List<TaskItem>, focusCategory: TimeCategory?): List<TaskItem> =
        tasks.asSequence()
            .filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
            .sortedWith(
                compareByDescending<TaskItem> { focusCategory != null && it.category == focusCategory }
                    .thenBy { it.priority.sortWeight }
                    .thenBy { it.estimatedMinutes ?: 999 }
            )
            .take(3)
            .toList()

    private fun selectPendingHabits(
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        records: List<LedgerRecord>,
        dateLocal: String
    ): List<Habit> =
        habits.asSequence()
            .filter { !it.isArchived }
            .filterNot { TaskHabitEngine.isHabitCompletedOnDate(it, dateLocal, habitLogs, records) }
            .take(3)
            .toList()

    /**
     * Kiểm tra xung đột lịch xác định trên thiết bị cho các proposal có giờ cụ thể trước khi hiển thị cho người dùng.
     * Không tự động xóa hoặc tự ghi dữ liệu; chỉ gắn cảnh báo nếu trùng giờ.
     */
    fun detectScheduleConflictWarnings(
        proposals: List<ActivityProposal>,
        perms: AiReadPermissions,
        records: List<LedgerRecord>,
        activeTimer: ActiveTimerSession?,
        nowEpochMs: Long,
        zone: ZoneId
    ): List<String> {
        if (!perms.readLedger || proposals.isEmpty()) return emptyList()
        val warnings = mutableListOf<String>()
        for (p in proposals) {
            val s = p.startLocal ?: continue
            val e = p.endLocal ?: continue
            val range = LedgerValidator.parseTimeRangeToEpochMillis(p.dateLocal, s, e, zone) ?: continue
            val pStart = range.first
            val pEnd = range.second
            val conflictingTitles = buildList {
                for (r in records) {
                    if (r.isCancelled) continue
                    val rStart = r.startedAt ?: continue
                    val rEnd = r.endedAt ?: continue
                    if (rEnd > rStart && maxOf(pStart, rStart) < minOf(pEnd, rEnd)) {
                        add(r.title.ifBlank { r.category.displayName })
                    }
                }
                if (activeTimer != null) {
                    val tStart = activeTimer.startedAtEpochMs
                    val tEnd = maxOf(nowEpochMs, tStart + 60_000L)
                    if (maxOf(pStart, tStart) < minOf(pEnd, tEnd)) {
                        add(activeTimer.title.ifBlank { TimeCategory.fromKeyOrDefault(activeTimer.categoryKey).displayName })
                    }
                }
            }
            if (conflictingTitles.isNotEmpty()) {
                val firstTitle = conflictingTitles.first()
                warnings += "Lưu ý: khung giờ ${s}–${e} (${p.dateLocal}) đang giao với \"$firstTitle\" trên lịch."
            }
        }
        return warnings
    }

    private fun capContextBlock(text: String): String {
        if (text.length <= MAX_CONTEXT_CHARS) return text
        val truncated = text.take(MAX_CONTEXT_CHARS - 20)
        val lastNewline = truncated.lastIndexOf('\n')
        return (if (lastNewline > 200) truncated.substring(0, lastNewline) else truncated) + "\n- (đã rút gọn theo giới hạn)"
    }
}
