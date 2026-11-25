package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.PausedTimerContinuation
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.model.TimeLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CurrentWorkMode {
    ACTIVE_TIMER,
    PAUSED_TIMER,
    CURRENT_PLANNED,
    IDLE
}

data class ActiveActivitySnapshot(
    val mode: CurrentWorkMode,
    val title: String,
    val category: TimeCategory,
    val startedAtEpochMs: Long,
    val elapsedSeconds: Long,
    val startedElapsedRealtimeMs: Long? = null,
    val targetDurationSeconds: Long? = null,
    val taskId: String? = null,
    val subtitleVi: String,
    val tagId: String? = null
)

data class UpcomingActivitySnapshot(
    val title: String,
    val startHm: String,
    val endHm: String?,
    val category: TimeCategory,
    val startsInMinutes: Int,
    val isPlannedBlock: Boolean,
    val taskId: String? = null,
    val tagId: String? = null
)

data class TimelineItemSnapshot(
    val id: String,
    val title: String,
    val startHm: String,
    val endHm: String,
    val category: TimeCategory,
    val isPlanned: Boolean,
    val isCurrent: Boolean,
    val isDone: Boolean
)

data class TodoItemSnapshot(
    val id: String,
    val title: String,
    val category: TimeCategory,
    val priority: TaskPriority,
    val estimatedMinutes: Int?,
    val dueLabel: String?,
    val subtaskSummary: String?,
    val hasRiskWarning: Boolean,
    val riskLabel: String?,
    val isDone: Boolean,
    val postponedCount: Int,
    val tagId: String? = null
)

data class WallyHubSnapshot(
    val dateLocal: String,
    val nowEpochMs: Long,
    val current: ActiveActivitySnapshot?,
    val upcoming: UpcomingActivitySnapshot?,
    val actualRecordedMinutes: Int,
    val plannedRemainingMinutes: Int,
    val freeRemainingMinutes: Int,
    val nextFreeWindowLabel: String?,
    val timelineProgressFraction: Float,
    val todayTimeline: List<TimelineItemSnapshot>,
    val openTodoCount: Int,
    val doneTodayTodoCount: Int,
    val todoItems: List<TodoItemSnapshot>,
    val wallyInsightText: String,
    val isCloudAiInsight: Boolean
)

/**
 * Nguồn chân lý duy nhất (SSOT) cho dữ liệu hiển thị đồng thời trên App chính, Home Widget và Floating Assistant.
 * - Tuyệt đối không tính PLANNED vào actualRecordedMinutes.
 * - Không hiển thị nhận xét AI giả lập: chỉ đặt `isCloudAiInsight = true` khi có báo cáo thật từ Cloud AI còn hiệu lực.
 */
object WallyHubSnapshotEngine {

    private val HM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        records: List<LedgerRecord>,
        tasks: List<TaskItem>,
        activeTimer: ActiveTimerSession?,
        pausedTimer: PausedTimerContinuation?,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        currentBootCount: Int? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        aiReadPermissions: AiReadPermissions = AiReadPermissions(),
        cachedAiReport: AiTodoAnalysisReport? = null,
        aiEnabled: Boolean = false
    ): WallyHubSnapshot {
        val nowZoned = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val today = nowZoned.toLocalDate()
        val todayLocal = today.toString()
        val nowMinute = nowZoned.hour * 60 + nowZoned.minute

        val dayModel = ScheduleGridEngine.buildDayScheduleModel(
            date = today,
            records = records,
            activeTimer = activeTimer,
            nowEpochMs = nowEpochMs,
            zone = zoneId
        )

        val closedTaskIds = tasks
            .asSequence()
            .filter { it.isDone || it.status == TaskStatus.ARCHIVED }
            .mapTo(HashSet()) { it.id }
        val isLinkedTaskClosed: (String?) -> Boolean = { taskId -> taskId != null && taskId in closedTaskIds }
        val activeTimerSnap = activeTimer?.computeElapsedSeconds(nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        val effectiveNowEpochMs = activeTimerSnap?.effectiveEndEpochMs ?: nowEpochMs

        // 1. Đang làm (Priority 1)
        val currentSnapshot: ActiveActivitySnapshot? = when {
            activeTimer != null -> {
                val cat = TimeCategory.fromKeyOrDefault(activeTimer.categoryKey)
                val snap = activeTimerSnap!!
                val startHm = Instant.ofEpochMilli(activeTimer.startedAtEpochMs).atZone(zoneId).format(HM_FMT)
                ActiveActivitySnapshot(
                    mode = CurrentWorkMode.ACTIVE_TIMER,
                    title = activeTimer.title.ifBlank { cat.displayName },
                    category = cat,
                    startedAtEpochMs = activeTimer.startedAtEpochMs,
                    elapsedSeconds = snap.elapsedSeconds,
                    startedElapsedRealtimeMs = activeTimer.startedElapsedRealtimeMs,
                    targetDurationSeconds = activeTimer.targetDurationSeconds,
                    taskId = activeTimer.taskId,
                    subtitleVi = "Đang đo thực tế · từ $startHm",
                    tagId = activeTimer.tagId
                )
            }
            pausedTimer != null -> {
                val cat = TimeCategory.fromKeyOrDefault(pausedTimer.categoryKey)
                val savedStr = TimeRange.formatDuration(((pausedTimer.accumulatedSeconds + 30L) / 60L).toInt().coerceAtLeast(0))
                ActiveActivitySnapshot(
                    mode = CurrentWorkMode.PAUSED_TIMER,
                    title = pausedTimer.title,
                    category = cat,
                    startedAtEpochMs = pausedTimer.pausedAtEpochMs,
                    elapsedSeconds = pausedTimer.accumulatedSeconds,
                    targetDurationSeconds = pausedTimer.targetDurationSeconds,
                    taskId = pausedTimer.taskId,
                    subtitleVi = if (pausedTimer.accumulatedSeconds >= 60L) "Đã tạm dừng · đã ghi $savedStr" else "Đang tạm dừng · chạm Tiếp tục",
                    tagId = pausedTimer.tagId
                )
            }
            else -> {
                val activePlanBlock = dayModel.timedBlocks.firstOrNull { blk ->
                    blk.visualState == ScheduleBlockVisualState.PLANNED &&
                        blk.startMinute <= nowMinute &&
                        nowMinute < blk.endMinute &&
                        !isLinkedTaskClosed(blk.record?.taskId)
                }
                activePlanBlock?.let { blk ->
                    val remain = (blk.endMinute - nowMinute).coerceAtLeast(1)
                    ActiveActivitySnapshot(
                        mode = CurrentWorkMode.CURRENT_PLANNED,
                        title = blk.title,
                        category = blk.category,
                        startedAtEpochMs = blk.record?.startedAt ?: nowEpochMs,
                        elapsedSeconds = ((nowMinute - blk.startMinute).coerceAtLeast(0)) * 60L,
                        targetDurationSeconds = ((blk.endMinute - blk.startMinute).coerceAtLeast(1)) * 60L,
                        taskId = blk.record?.taskId,
                        subtitleVi = "Theo kế hoạch ${ScheduleGridEngine.formatHmWindow(blk.startMinute, blk.endMinute)} · còn ${remain}p",
                        tagId = blk.record?.tagId
                    )
                }
            }
        }

        // 2. Sắp làm (Priority 2)
        val nextPlanBlock = dayModel.timedBlocks
            .filter { blk ->
                blk.startMinute > nowMinute &&
                    (blk.visualState == ScheduleBlockVisualState.PLANNED || (blk.record?.startedAt ?: 0L) > nowEpochMs) &&
                    !isLinkedTaskClosed(blk.record?.taskId)
            }
            .minByOrNull { blk -> blk.startMinute }
        val nextDueTaskToday = tasks
            .filter { !it.isDone && it.status != TaskStatus.ARCHIVED && it.dueDateLocal == todayLocal && it.dueTimeLocal != null }
            .mapNotNull { t -> TimeRange.parseMinuteOfDay(t.dueTimeLocal!!)?.let { m -> if (m > nowMinute) t to m else null } }
            .minByOrNull { it.second }

        val upcomingSnapshot: UpcomingActivitySnapshot? = when {
            nextPlanBlock != null && (nextDueTaskToday == null || nextPlanBlock.startMinute <= nextDueTaskToday.second) -> {
                UpcomingActivitySnapshot(
                    title = nextPlanBlock.title,
                    startHm = TimeRange.formatHm(nextPlanBlock.startMinute),
                    endHm = TimeRange.formatHm(nextPlanBlock.endMinute),
                    category = nextPlanBlock.category,
                    startsInMinutes = (nextPlanBlock.startMinute - nowMinute).coerceAtLeast(1),
                    isPlannedBlock = true,
                    taskId = nextPlanBlock.record?.taskId,
                    tagId = nextPlanBlock.record?.tagId
                )
            }
            nextDueTaskToday != null -> {
                val (task, m) = nextDueTaskToday
                UpcomingActivitySnapshot(
                    title = task.title,
                    startHm = TimeRange.formatHm(m),
                    endHm = task.estimatedMinutes?.let { TimeRange.formatHm((m + it).coerceAtMost(1439)) },
                    category = task.category,
                    startsInMinutes = (m - nowMinute).coerceAtLeast(1),
                    isPlannedBlock = false,
                    taskId = task.id,
                    tagId = task.tagId
                )
            }
            else -> null
        }

        // 3. Thời gian thực tế vs Kế hoạch vs Thời gian trống hôm nay (không cộng PLANNED vào thực tế!)
        // Dùng chung LedgerAnalyticsCalculator.computeDayStats + DashboardSummary để khớp Ví 24h theo từng ngày
        // (bao gồm tách phiên qua đêm 23:00–00:30 và ActiveTimer đang chạy tại cùng thời điểm).
        val dayStats = LedgerAnalyticsCalculator.computeDayStats(
            dateLocal = todayLocal,
            records = records,
            zoneId = zoneId,
            nowEpochMs = effectiveNowEpochMs,
            activeTimer = activeTimer
        )
        val actualRecordedMinutes = ((DashboardSummary.from(dayStats).recordedSeconds + 30L) / 60L)
            .toInt()
            .coerceAtLeast(0)
        val plannedRemainingMinutes = dayModel.timedBlocks
            .filter { blk ->
                blk.visualState == ScheduleBlockVisualState.PLANNED &&
                    blk.endMinute > nowMinute &&
                    !isLinkedTaskClosed(blk.record?.taskId)
            }
            .sumOf { blk -> (blk.endMinute - maxOf(blk.startMinute, nowMinute)).coerceAtLeast(0) }

        val remainingFreeGaps = dayModel.freeGaps.mapNotNull { gap ->
            val s = maxOf(gap.startMinute, nowMinute)
            val e = minOf(gap.endMinute, 23 * 60)
            if (e - s >= 10) s to e else null
        }
        val freeRemainingMinutes = remainingFreeGaps.sumOf { it.second - it.first }
        val nextGap = remainingFreeGaps.firstOrNull { (it.second - it.first) >= 15 }
        val nextFreeWindowLabel = nextGap?.let { (s, e) ->
            ScheduleGridEngine.formatHmWindow(s, e)
        }

        val dayHorizon = (23 * 60 - 6 * 60).toFloat().coerceAtLeast(1f)
        val elapsedDayFraction = ((nowMinute - 6 * 60).toFloat() / dayHorizon).coerceIn(0f, 1f)

        val todayTimeline = dayModel.timedBlocks
            .filter { blk -> !blk.isActiveTimer }
            .sortedBy { blk -> blk.startMinute }
            .map { blk ->
                val blkPlanned = blk.visualState == ScheduleBlockVisualState.PLANNED
                val isCurrent = blk.startMinute <= nowMinute && nowMinute < blk.endMinute
                val isPastDone = (blk.endMinute <= nowMinute && !blkPlanned) || isLinkedTaskClosed(blk.record?.taskId)
                TimelineItemSnapshot(
                    id = blk.recordId,
                    title = blk.title,
                    startHm = ScheduleGridEngine.formatHmWindow(blk.startMinute, blk.endMinute).substringBefore("–"),
                    endHm = ScheduleGridEngine.formatHmWindow(blk.startMinute, blk.endMinute).substringAfter("–"),
                    category = blk.category,
                    isPlanned = blkPlanned,
                    isCurrent = isCurrent,
                    isDone = isPastDone
                )
            }

        // 4. To-do hôm nay & rủi ro
        val risks = AiTodoEngine.analyzeWorkloadAndRisks(tasks, records, activeTimer, todayLocal, nowEpochMs, zoneId)
        val risksByTaskId = risks.groupBy { it.taskId }

        val relevantOpen = tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
        val aiOrderIndex = cachedAiReport?.orderedTaskIds
            ?.takeIf { cachedAiReport.dateLocal == todayLocal && aiReadPermissions.readTasks }
            ?.withIndex()
            ?.associate { it.value to it.index }

        val sortedOpen = relevantOpen.sortedWith(
            compareBy<TaskItem>(
                { if (it.dueDateLocal != null && it.dueDateLocal < todayLocal) 0 else 1 },
                { aiOrderIndex?.get(it.id) ?: Int.MAX_VALUE },
                { it.priority.sortWeight },
                { if (it.dueDateLocal == todayLocal) 0 else 1 },
                { it.dueDateLocal ?: "9999-99-99" },
                { -it.createdAt }
            )
        )
        val doneTodayCount = tasks.count { t ->
            t.isDone && t.completedAt != null &&
                Instant.ofEpochMilli(t.completedAt).atZone(zoneId).toLocalDate() == today
        }

        val todoItems = sortedOpen.take(8).map { t ->
            val tRisks = risksByTaskId[t.id].orEmpty()
            val primaryRisk = tRisks.maxByOrNull { it.severityHigh }
            val dueText = when {
                t.dueDateLocal != null && t.dueDateLocal < todayLocal -> "Quá hạn ${t.dueDateLocal.substring(5)}"
                t.dueDateLocal == todayLocal && t.dueTimeLocal != null -> "Hôm nay ${t.dueTimeLocal}"
                t.dueDateLocal == todayLocal -> "Hôm nay"
                t.dueDateLocal != null -> t.dueDateLocal.substring(5)
                else -> null
            }
            val subText = if (t.subtasks.isNotEmpty()) "${t.subtasks.count { it.isDone }}/${t.subtasks.size} bước" else null
            TodoItemSnapshot(
                id = t.id,
                title = t.title,
                category = t.category,
                priority = t.priority,
                estimatedMinutes = t.estimatedMinutes,
                dueLabel = dueText,
                subtaskSummary = subText,
                hasRiskWarning = primaryRisk != null,
                riskLabel = primaryRisk?.kind?.shortLabel,
                isDone = t.isDone,
                postponedCount = t.postponedCount,
                tagId = t.tagId
            )
        }

        // 5. Nhận xét trung thực (chỉ gắn nhãn Wally AI khi có phân tích Cloud AI thật còn hợp lệ)
        val permsFp = aiReadPermissions.fingerprint()
        val hasFreshCloudReport = aiEnabled &&
            aiReadPermissions.readTasks &&
            cachedAiReport != null &&
            cachedAiReport.fromCloudAi &&
            cachedAiReport.dateLocal == todayLocal &&
            cachedAiReport.permissionsFingerprint == permsFp &&
            cachedAiReport.summaryVi.isNotBlank()

        val insightText = if (hasFreshCloudReport) {
            cachedAiReport!!.summaryVi
        } else {
            val highRisks = risks.count { it.severityHigh }
            val p1Count = relevantOpen.count { it.priority == TaskPriority.P1 }
            buildString {
                if (relevantOpen.isEmpty() && nextPlanBlock == null) {
                    append("Lịch trống ${TimeRange.formatDuration(freeRemainingMinutes)} · chưa có việc cần làm.")
                } else {
                    append("Còn ${relevantOpen.size} việc")
                    if (p1Count > 0) append(" ($p1Count P1)")
                    if (highRisks > 0) append(" · ⚠ $highRisks lưu ý")
                    if (nextFreeWindowLabel != null) append(" · trống $nextFreeWindowLabel")
                }
            }
        }

        return WallyHubSnapshot(
            dateLocal = todayLocal,
            nowEpochMs = nowEpochMs,
            current = currentSnapshot,
            upcoming = upcomingSnapshot,
            actualRecordedMinutes = actualRecordedMinutes,
            plannedRemainingMinutes = plannedRemainingMinutes,
            freeRemainingMinutes = freeRemainingMinutes,
            nextFreeWindowLabel = nextFreeWindowLabel,
            timelineProgressFraction = elapsedDayFraction,
            todayTimeline = todayTimeline,
            openTodoCount = relevantOpen.size,
            doneTodayTodoCount = doneTodayCount,
            todoItems = todoItems,
            wallyInsightText = insightText,
            isCloudAiInsight = hasFreshCloudReport
        )
    }
}
