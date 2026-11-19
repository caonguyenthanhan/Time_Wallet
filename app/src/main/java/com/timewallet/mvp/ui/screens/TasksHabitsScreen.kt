package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.domain.model.CustomizationSettings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.timewallet.mvp.domain.model.EisenhowerQuad
import com.timewallet.mvp.domain.model.GoalGroupState
import com.timewallet.mvp.domain.model.GoalKind
import com.timewallet.mvp.domain.model.GoalPeriod
import com.timewallet.mvp.domain.model.Habit
import com.timewallet.mvp.domain.model.HabitFrequency
import com.timewallet.mvp.domain.model.HabitLog
import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.SubtaskItem
import com.timewallet.mvp.domain.model.Tag
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskPriority
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.GoalRules
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.TaskHabitEngine
import com.timewallet.mvp.domain.usecase.TaskSmartFilter
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerBg
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletUntrackedBar
import com.timewallet.mvp.ui.theme.WalletWarningText
import java.time.DayOfWeek
import java.time.LocalDate

private enum class TasksHubSection(val label: String) {
    TASKS("Công việc"),
    HABITS("Thói quen"),
    GOALS("Mục tiêu"),
    KANBAN("Kanban"),
    EISENHOWER("Ma trận")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksHabitsScreen(
    todayDateLocal: String,
    tasks: List<TaskItem>,
    taskFilter: TaskSmartFilter,
    onSelectTaskFilter: (TaskSmartFilter) -> Unit,
    habits: List<Habit>,
    habitLogs: List<HabitLog>,
    records: List<LedgerRecord>,
    tags: List<Tag>,
    unifiedGoals: List<GoalRules.Summary>,
    customization: CustomizationSettings,
    onCreateTask: (title: String, categoryKey: String, priority: TaskPriority, estimatedMinutes: Int?, dueDateLocal: String?, tagId: String?, quad: EisenhowerQuad?, subtasksRaw: List<String>) -> Unit,
    onToggleTaskDone: (TaskItem) -> Unit,
    onUpdateTaskStatus: (TaskItem, TaskStatus) -> Unit,
    onSetTaskEisenhower: (TaskItem, EisenhowerQuad?) -> Unit,
    onAddSubtask: (taskId: String, title: String) -> Unit,
    onToggleSubtaskDone: (SubtaskItem) -> Unit,
    onDeleteTask: (String) -> Unit,
    onStartFocusOnTask: (TaskItem) -> Unit,
    onScheduleTaskOnCalendar: (TaskItem) -> Unit,
    onBreakDownTaskWithWally: (TaskItem) -> Unit,
    onRequestWallyReplanInbox: () -> Unit,
    onCreateHabit: (title: String, categoryKey: String, tagId: String?, frequency: HabitFrequency, minSessionMinutes: Int) -> Unit,
    onToggleHabitForToday: (Habit) -> Unit,
    onOpenGoalEditor: (groupId: String?) -> Unit,
    onPauseGoal: (String) -> Unit,
    onResumeGoal: (String) -> Unit,
    onEndGoal: (String) -> Unit,
    onOpenCustomization: () -> Unit
) {
    val visibleSections = remember(customization.enableHabits, customization.enableKanban, customization.enableEisenhower) {
        buildList {
            add(TasksHubSection.TASKS)
            if (customization.enableHabits) add(TasksHubSection.HABITS)
            add(TasksHubSection.GOALS)
            if (customization.enableKanban) add(TasksHubSection.KANBAN)
            if (customization.enableEisenhower) add(TasksHubSection.EISENHOWER)
        }
    }
    var selectedSection by rememberSaveable { mutableStateOf(TasksHubSection.TASKS) }
    val activeSection = if (selectedSection in visibleSections) selectedSection else TasksHubSection.TASKS

    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var showCreateHabitDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenTitle(
            title = "Việc & Thói quen",
            trailing = {
                WalletPill(
                    onClickLabel = "Thêm mới",
                    containerColor = WalletMintDark,
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                    onClick = {
                        when (activeSection) {
                            TasksHubSection.GOALS -> onOpenGoalEditor(null)
                            TasksHubSection.HABITS -> showCreateHabitDialog = true
                            else -> showCreateTaskDialog = true
                        }
                    }
                ) {
                    Text(
                        text = when (activeSection) {
                            TasksHubSection.GOALS -> "+ Mục tiêu"
                            TasksHubSection.HABITS -> "+ Thói quen"
                            else -> "+ Thêm việc"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        )

        // Thanh chuyển phân hệ tự xuống dòng gọn để Kanban & Ma trận luôn hiện rõ không bị cắt mép.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visibleSections.forEach { sec ->
                val isSel = activeSection == sec
                WalletPill(
                    onClickLabel = sec.label,
                    containerColor = if (isSel) WalletMintDark else WalletCardBg,
                    borderColor = if (isSel) WalletMintDark else WalletStroke,
                    selected = isSel,
                    role = Role.Tab,
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                    onClick = { selectedSection = sec }
                ) {
                    Text(
                        text = sec.label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSel) Color.White else WalletMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        when (activeSection) {
            TasksHubSection.TASKS -> {
                val filteredTasks = remember(tasks, taskFilter, todayDateLocal) {
                    TaskHabitEngine.filterTasks(tasks, taskFilter, todayDateLocal)
                }
                val inboxCount = remember(tasks) { tasks.count { it.status == TaskStatus.INBOX && !it.isDone } }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskSmartFilter.entries.forEach { f ->
                        val isSel = f == taskFilter
                        WalletPill(
                            onClickLabel = "Lọc ${f.labelVi}",
                            containerColor = if (isSel) WalletMintSoft else WalletCardBg,
                            borderColor = if (isSel) WalletMint else WalletStroke,
                            selected = isSel,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                            onClick = { onSelectTaskFilter(f) }
                        ) {
                            Text(
                                text = f.labelVi,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) WalletMintAccent else WalletMuted,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (inboxCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WalletMintTint)
                            .border(1.dp, WalletStroke, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📥 $inboxCount việc Inbox chưa xếp lịch",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        WalletPill(
                            onClickLabel = "Wally gợi ý xếp lịch Inbox",
                            containerColor = WalletMintDark,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                            onClick = onRequestWallyReplanInbox
                        ) {
                            Text(
                                text = "✦ Gợi ý lịch",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (filteredTasks.isEmpty()) {
                    SoftCard {
                        EmptyState(
                            glyph = WalletGlyph.CONFIRM,
                            title = "Chưa có việc trong \"${taskFilter.labelVi}\"",
                            hint = "Bấm '+ Thêm việc' hoặc nút '+ Ghi' ở thanh trên để thu thập nhanh.",
                            actionLabel = "+ Thêm việc",
                            onAction = { showCreateTaskDialog = true }
                        )
                    }
                } else {
                    filteredTasks.forEach { task ->
                        TaskCard(
                            task = task,
                            loggedSeconds = TaskHabitEngine.computeTaskLoggedSeconds(task.id, records),
                            onToggleDone = { onToggleTaskDone(task) },
                            onToggleSubtask = onToggleSubtaskDone,
                            onAddSubtask = { subTitle -> onAddSubtask(task.id, subTitle) },
                            onStartFocus = { onStartFocusOnTask(task) },
                            onSchedule = { onScheduleTaskOnCalendar(task) },
                            onBreakDown = { onBreakDownTaskWithWally(task) },
                            onDelete = { onDeleteTask(task.id) }
                        )
                    }
                }
            }

            TasksHubSection.HABITS -> {
                val todayDate = remember(todayDateLocal) { runCatching { LocalDate.parse(todayDateLocal) }.getOrDefault(LocalDate.now()) }
                val habitWeekdays = remember(todayDate) {
                    (6 downTo 0).map { offset ->
                        val d = todayDate.minusDays(offset.toLong())
                        when (d.dayOfWeek) {
                            DayOfWeek.MONDAY -> "T2"
                            DayOfWeek.TUESDAY -> "T3"
                            DayOfWeek.WEDNESDAY -> "T4"
                            DayOfWeek.THURSDAY -> "T5"
                            DayOfWeek.FRIDAY -> "T6"
                            DayOfWeek.SATURDAY -> "T7"
                            DayOfWeek.SUNDAY -> "CN"
                        }
                    }
                }
                if (habits.isEmpty()) {
                    SoftCard {
                        EmptyState(
                            glyph = WalletGlyph.GOAL,
                            title = "Chưa có thói quen nào",
                            hint = "Gắn Tag để Wally tự đánh dấu hoàn thành mỗi khi bạn đo đồng hồ hoặc ghi Sổ cái!",
                            actionLabel = "+ Tạo thói quen",
                            onAction = { showCreateHabitDialog = true }
                        )
                    }
                } else {
                    val doneTodayCount = habits.count {
                        TaskHabitEngine.isHabitCompletedOnDate(it, todayDateLocal, habitLogs, records)
                    }
                    val totalStreakPoints = habits.sumOf { h ->
                        TaskHabitEngine.habitRecentDots(h, todayDateLocal, 7, habitLogs, records).count { it }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalletMintTint)
                            .border(1.dp, WalletStroke, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Hôm nay: $doneTodayCount/${habits.size} thói quen",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletInk
                            )
                            Text(
                                text = "Tích lũy 7 ngày: $totalStreakPoints lượt hoàn thành",
                                fontSize = 11.sp,
                                color = WalletMuted
                            )
                        }
                        Text(
                            text = if (doneTodayCount == habits.size) "✓ Đạt đủ" else "Đang theo dõi",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent
                        )
                    }

                    habits.forEach { habit ->
                        val autoRec = TaskHabitEngine.findAutoFulfillingRecord(habit, todayDateLocal, records)
                        val doneToday = TaskHabitEngine.isHabitCompletedOnDate(habit, todayDateLocal, habitLogs, records)
                        val dots = TaskHabitEngine.habitRecentDots(habit, todayDateLocal, 7, habitLogs, records)
                        val linkedTag = habit.tagId?.let { tid -> tags.firstOrNull { it.id == tid } }

                        SoftCard(padding = 14.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CategoryIcon(habit.category, size = 18.dp, tint = Color(habit.category.colorHex))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(habit.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                                        val meta = buildString {
                                            append(habit.frequency.labelVi)
                                            append(" · ≥${habit.minSessionMinutes}p")
                                            if (linkedTag != null) append(" · Tag #${linkedTag.name}")
                                        }
                                        Text(meta, fontSize = 11.sp, color = WalletMuted)
                                    }
                                    WalletPill(
                                        onClickLabel = "Đánh dấu ${habit.title}",
                                        containerColor = if (doneToday) WalletMint else WalletMintTint,
                                        borderColor = if (doneToday) WalletMint else WalletStroke,
                                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp),
                                        onClick = { onToggleHabitForToday(habit) }
                                    ) {
                                        Text(
                                            text = if (doneToday) "✓ Đã xong" else "Đánh dấu",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (doneToday) Color.White else WalletMintAccent,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                if (autoRec != null) {
                                    Text(
                                        text = "⚡ Tự khớp từ Sổ cái hôm nay (${autoRec.title})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WalletMintAccent
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(WalletSoftNeutral)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dots.forEachIndexed { idx, ok ->
                                        val isTodayDot = idx == dots.lastIndex
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isTodayDot) 14.dp else 12.dp)
                                                    .clip(CircleShape)
                                                    .background(if (ok) WalletMint else WalletUntrackedBar)
                                                    .then(
                                                        if (isTodayDot) Modifier.border(1.5.dp, WalletMintAccent, CircleShape)
                                                        else Modifier
                                                    )
                                            )
                                            Text(
                                                text = habitWeekdays.getOrElse(idx) { "" },
                                                fontSize = 9.5.sp,
                                                fontWeight = if (isTodayDot) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (isTodayDot) WalletMintAccent else WalletMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (habits.size <= 2) {
                    val habitPresets = listOf(
                        Triple("Ngủ đủ ≥ 7 giờ", TimeCategory.REST.key, 420),
                        Triple("Đọc sách 20 phút", TimeCategory.LEARN.key, 20),
                        Triple("Đi bộ vận động 20p", TimeCategory.HEALTH.key, 20)
                    ).filter { (title, _, _) -> habits.none { h -> h.title.equals(title, ignoreCase = true) } }
                    if (habitPresets.isNotEmpty()) {
                        SoftCard(padding = 12.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Gợi ý thói quen lành mạnh",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WalletInk
                                )
                                Text(
                                    text = "Thêm nhanh để cân đối nhịp nghỉ ngơi, học tập và vận động mỗi ngày.",
                                    fontSize = 11.sp,
                                    color = WalletMuted
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    habitPresets.forEach { (pTitle, pCat, pMins) ->
                                        WalletPill(
                                            onClickLabel = "Thêm nhanh thói quen $pTitle",
                                            containerColor = WalletMintTint,
                                            borderColor = WalletStroke,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            onClick = { onCreateHabit(pTitle, pCat, null, HabitFrequency.DAILY, pMins) }
                                        ) {
                                            Text(
                                                text = "+ $pTitle",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WalletMintAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TasksHubSection.GOALS -> {
                if (unifiedGoals.isEmpty()) {
                    SoftCard {
                        EmptyState(
                            glyph = WalletGlyph.GOAL,
                            title = "Chưa có mục tiêu thời gian",
                            hint = "Đặt mục tiêu tối thiểu (tăng giờ học/tập) hoặc giới hạn tối đa (ngân sách tuần).",
                            actionLabel = "+ Tạo mục tiêu",
                            onAction = { onOpenGoalEditor(null) }
                        )
                    }
                } else {
                    unifiedGoals.forEach { summary ->
                        val goal = summary.latest
                        val isLegacyViewOnly = goal.id.startsWith("budget_view_")
                        val used = summary.progress?.usedSeconds ?: 0L
                        val frac = summary.progress?.fraction ?: 0f
                        val fmt = LedgerAnalyticsCalculator::formatCompactDurationSeconds

                        SoftCard(padding = 14.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = summary.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WalletInk,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${if (goal.kind == GoalKind.MIN) "≥ Tối thiểu" else "≤ Giới hạn"} · ${if (goal.period == GoalPeriod.DAY) "Ngày" else "Tuần"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletMintAccent
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WalletUntrackedBar)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frac.coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (goal.kind == GoalKind.MAX && used > summary.latest.targetSeconds) WalletDangerText else WalletMint
                                            )
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${fmt(used)} / ${fmt(goal.targetSeconds)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletInk
                                    )
                                    if (!isLegacyViewOnly) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "Sửa",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WalletMintAccent,
                                                modifier = Modifier.walletClickable(onClickLabel = "Sửa mục tiêu ${summary.name}") {
                                                    onOpenGoalEditor(summary.groupId)
                                                }
                                            )
                                            if (summary.state == GoalGroupState.ACTIVE) {
                                                Text(
                                                    text = "Tạm dừng",
                                                    fontSize = 11.5.sp,
                                                    color = WalletMuted,
                                                    modifier = Modifier.walletClickable(onClickLabel = "Tạm dừng ${summary.name}") {
                                                        onPauseGoal(summary.groupId)
                                                    }
                                                )
                                            } else if (summary.state == GoalGroupState.PAUSED) {
                                                Text(
                                                    text = "Tiếp tục",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = WalletMintAccent,
                                                    modifier = Modifier.walletClickable(onClickLabel = "Tiếp tục ${summary.name}") {
                                                        onResumeGoal(summary.groupId)
                                                    }
                                                )
                                            }
                                            Text(
                                                text = "Kết thúc",
                                                fontSize = 11.5.sp,
                                                color = WalletDangerText,
                                                modifier = Modifier.walletClickable(onClickLabel = "Kết thúc ${summary.name}") {
                                                    onEndGoal(summary.groupId)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TasksHubSection.KANBAN -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val colWidth = (maxWidth * 0.74f).coerceIn(220.dp, 290.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            TaskStatus.TODO to "Cần làm",
                            TaskStatus.DOING to "Đang làm",
                            TaskStatus.DONE to "Đã xong"
                        ).forEach { (colStatus, colTitle) ->
                            val colTasks = tasks.filter {
                                if (colStatus == TaskStatus.TODO) it.status == TaskStatus.TODO || it.status == TaskStatus.INBOX
                                else it.status == colStatus
                            }
                            Column(
                                modifier = Modifier
                                    .width(colWidth)
                                    .heightIn(min = 140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(WalletCardBg)
                                    .border(1.dp, WalletStroke, RoundedCornerShape(16.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$colTitle (${colTasks.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WalletInk
                                )
                                if (colTasks.isEmpty()) {
                                    Text("Trống", fontSize = 11.5.sp, color = WalletMuted)
                                } else {
                                    colTasks.forEach { task ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(WalletBg)
                                                .border(1.dp, WalletStroke, RoundedCornerShape(12.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = task.title,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WalletInk,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (colStatus != TaskStatus.DOING) {
                                                    Text(
                                                        text = "→ Đang làm",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.walletClickable(onClickLabel = "Chuyển Đang làm") {
                                                            onUpdateTaskStatus(task, TaskStatus.DOING)
                                                        }
                                                    )
                                                }
                                                if (colStatus != TaskStatus.DONE) {
                                                    Text(
                                                        text = "✓ Xong",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.walletClickable(onClickLabel = "Đánh dấu Xong") {
                                                            onToggleTaskDone(task)
                                                        }
                                                    )
                                                }
                                                if (colStatus != TaskStatus.DONE) {
                                                    Text(
                                                        text = "▶ Đo",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = WalletInk,
                                                        modifier = Modifier.walletClickable(onClickLabel = "Tập trung") {
                                                            onStartFocusOnTask(task)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TasksHubSection.EISENHOWER -> {
                val openTasks = tasks.filter { !it.isDone && it.status != TaskStatus.ARCHIVED }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        EisenhowerQuad.Q1 to WalletDangerText,
                        EisenhowerQuad.Q2 to MaterialTheme.colorScheme.primary,
                        EisenhowerQuad.Q3 to WalletWarningText,
                        EisenhowerQuad.Q4 to WalletMuted
                    ).forEach { (quad, accentColor) ->
                        val qTasks = openTasks.filter { it.effectiveQuadrant == quad }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(WalletCardBg)
                                .border(1.2.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${quad.name}: ${quad.labelVi}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                                Text(quad.shortHint, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }
                            if (qTasks.isEmpty()) {
                                Text("Không có việc nào trong ô này.", fontSize = 11.5.sp, color = WalletMuted)
                            } else {
                                qTasks.forEach { t ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(WalletBg)
                                            .border(1.dp, WalletStroke, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = t.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = WalletInk,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (quad != EisenhowerQuad.Q1) {
                                                Text(
                                                    text = "↑Q1",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = WalletDangerText,
                                                    modifier = Modifier.walletClickable(onClickLabel = "Đưa lên Q1") {
                                                        onSetTaskEisenhower(t, EisenhowerQuad.Q1)
                                                    }
                                                )
                                            }
                                            Text(
                                                text = "▶",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.walletClickable(onClickLabel = "Tập trung ${t.title}") {
                                                    onStartFocusOnTask(t)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    if (showCreateTaskDialog) {
        CreateTaskDialog(
            todayDateLocal = todayDateLocal,
            tags = tags,
            showEisenhowerPicker = customization.enableEisenhower,
            onCreate = { title, catKey, prio, estMins, dueDate, tagId, quad, subs ->
                onCreateTask(title, catKey, prio, estMins, dueDate, tagId, quad, subs)
                showCreateTaskDialog = false
            },
            onDismiss = { showCreateTaskDialog = false }
        )
    }

    if (showCreateHabitDialog) {
        CreateHabitDialog(
            tags = tags,
            onCreate = { title, catKey, tagId, freq, minMins ->
                onCreateHabit(title, catKey, tagId, freq, minMins)
                showCreateHabitDialog = false
            },
            onDismiss = { showCreateHabitDialog = false }
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    loggedSeconds: Long,
    onToggleDone: () -> Unit,
    onToggleSubtask: (SubtaskItem) -> Unit,
    onAddSubtask: (String) -> Unit,
    onStartFocus: () -> Unit,
    onSchedule: () -> Unit,
    onBreakDown: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var newSubTitle by remember { mutableStateOf("") }

    SoftCard(padding = 10.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .semantics { contentDescription = "Hoàn thành ${task.title}" }
                        .walletClickable(onClickLabel = "Đánh dấu hoàn thành ${task.title}") { onToggleDone() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (task.isDone) WalletMint else WalletMintTint)
                            .border(1.5.dp, if (task.isDone) WalletMint else WalletMintAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isDone) {
                            Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .walletClickable(onClickLabel = if (expanded) "Thu gọn ${task.title}" else "Xem bước con ${task.title}") {
                            expanded = !expanded
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = task.priority.badgeText,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (task.priority == TaskPriority.P1) WalletDangerText else WalletMintAccent,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (task.priority == TaskPriority.P1) WalletDangerBg else WalletMintTint)
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                        Text(
                            text = task.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (task.isDone) WalletMuted else WalletInk,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val meta = buildList {
                        add(task.category.displayName)
                        task.estimatedMinutes?.let { add("~${it}p") }
                        if (loggedSeconds > 0L) add("Đã đo ${LedgerAnalyticsCalculator.formatCompactDurationSeconds(loggedSeconds)}")
                        if (task.subtasks.isNotEmpty()) {
                            add("${task.subtasks.count { it.isDone }}/${task.subtasks.size} bước")
                        }
                        task.dueDateLocal?.let { add("Hạn ${it.substring(5)}") }
                    }.joinToString(" · ")
                    Text(meta, fontSize = 11.sp, color = WalletMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                if (!task.isDone) {
                    WalletPill(
                        onClickLabel = "Bắt đầu tập trung ${task.title}",
                        containerColor = WalletMintDark,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                        onClick = onStartFocus
                    ) {
                        Text("▶", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Box {
                    WalletPill(
                        onClickLabel = "Thao tác khác cho ${task.title}",
                        containerColor = WalletSoftNeutral,
                        borderColor = WalletStroke,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                        onClick = { menuOpen = true }
                    ) {
                        Text("⋯", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = WalletInk)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = WalletCardBg,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (!task.isDone) {
                            DropdownMenuItem(
                                text = { Text("▶ Bắt đầu tập trung", fontSize = 13.sp, color = WalletInk) },
                                onClick = {
                                    menuOpen = false
                                    onStartFocus()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📅 Xếp lịch lên Lịch trình", fontSize = 13.sp, color = WalletInk) },
                                onClick = {
                                    menuOpen = false
                                    onSchedule()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("✦ Chia nhỏ với Wally AI", fontSize = 13.sp, color = WalletMintAccent) },
                                onClick = {
                                    menuOpen = false
                                    onBreakDown()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (expanded) "Thu gọn bước con" else "Mở bước con & thêm bước", fontSize = 13.sp, color = WalletInk) },
                            onClick = {
                                menuOpen = false
                                expanded = !expanded
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa công việc", fontSize = 13.sp, color = WalletDangerText) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(color = WalletDivider)
                task.subtasks.forEach { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .walletClickable(onClickLabel = "Bước ${sub.title}") { onToggleSubtask(sub) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (sub.isDone) "☑" else "☐",
                            fontSize = 13.sp,
                            color = if (sub.isDone) WalletMintAccent else WalletMuted
                        )
                        Text(
                            text = sub.title,
                            fontSize = 12.sp,
                            color = if (sub.isDone) WalletMuted else WalletInk,
                            textDecoration = if (sub.isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newSubTitle,
                        onValueChange = { newSubTitle = it },
                        placeholder = { Text("Thêm bước con...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val clean = newSubTitle.trim()
                            if (clean.isNotEmpty()) {
                                onAddSubtask(clean)
                                newSubTitle = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WalletMintDark, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateTaskDialog(
    todayDateLocal: String,
    tags: List<Tag>,
    showEisenhowerPicker: Boolean,
    onCreate: (title: String, categoryKey: String, priority: TaskPriority, estimatedMinutes: Int?, dueDateLocal: String?, tagId: String?, quad: EisenhowerQuad?, subtasksRaw: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var categoryKey by remember { mutableStateOf(TimeCategory.WORK.key) }
    var priority by remember { mutableStateOf(TaskPriority.P2) }
    var estMinsText by remember { mutableStateOf("30") }
    var dueToday by remember { mutableStateOf(false) }
    var selectedTagId by remember { mutableStateOf<String?>(null) }
    var selectedQuad by remember { mutableStateOf<EisenhowerQuad?>(null) }
    var subtasksText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletCardBg,
        title = { Text("Thêm công việc", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("VD: Hoàn thành báo cáo tuần") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskPriority.entries.forEach { p ->
                        val isSel = priority == p
                        Text(
                            text = p.badgeText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else WalletInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isSel) WalletMintDark else WalletMintTint)
                                .walletClickable(onClickLabel = p.labelVi) { priority = p }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        text = if (dueToday) "✓ Hôm nay" else "Để Inbox",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dueToday) Color.White else WalletMintAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (dueToday) WalletMint else WalletMintTint)
                            .walletClickable(onClickLabel = "Chuyển Hôm nay hoặc Inbox") { dueToday = !dueToday }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = estMinsText,
                    onValueChange = { s -> estMinsText = s.filter(Char::isDigit).take(3) },
                    label = { Text("Dự kiến (phút)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryChipRow(selectedKey = categoryKey, onSelectCategory = { categoryKey = it })
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.filter { !it.isArchived }.take(6).forEach { t ->
                            ChoiceChip(t.name, selectedTagId == t.id, "Tag ${t.name}", leadingCategory = t.category) {
                                selectedTagId = if (selectedTagId == t.id) null else t.id
                                categoryKey = t.category.key
                            }
                        }
                    }
                }
                if (showEisenhowerPicker) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EisenhowerQuad.entries.forEach { q ->
                            val isSel = selectedQuad == q
                            Text(
                                text = q.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else WalletInk,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(if (isSel) WalletMintDark else WalletMintTint)
                                    .walletClickable(onClickLabel = q.labelVi) {
                                        selectedQuad = if (selectedQuad == q) null else q
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subtasksText,
                    onValueChange = { subtasksText = it },
                    label = { Text("Các bước nhỏ (phân cách bằng dấu phẩy)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val est = estMinsText.toIntOrNull()?.coerceIn(5, 480)
                    val subs = subtasksText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    onCreate(
                        title,
                        categoryKey,
                        priority,
                        est,
                        if (dueToday) todayDateLocal else null,
                        selectedTagId,
                        selectedQuad,
                        subs
                    )
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMintDark, contentColor = Color.White)
            ) {
                Text("Tạo việc", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy", color = WalletMuted) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateHabitDialog(
    tags: List<Tag>,
    onCreate: (title: String, categoryKey: String, tagId: String?, frequency: HabitFrequency, minSessionMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var categoryKey by remember { mutableStateOf(TimeCategory.HEALTH.key) }
    var selectedTagId by remember { mutableStateOf<String?>(null) }
    var freq by remember { mutableStateOf(HabitFrequency.DAILY) }
    var minMinsText by remember { mutableStateOf("15") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalletCardBg,
        title = { Text("Thêm thói quen", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("VD: Đọc sách / Chạy bộ 20p") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(HabitFrequency.DAILY, HabitFrequency.WEEKDAYS, HabitFrequency.TIMES_PER_WEEK).forEach { f ->
                        val isSel = freq == f
                        Text(
                            text = f.labelVi,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else WalletInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isSel) WalletMintDark else WalletMintTint)
                                .walletClickable(onClickLabel = f.labelVi) { freq = f }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = minMinsText,
                    onValueChange = { s -> minMinsText = s.filter(Char::isDigit).take(3) },
                    label = { Text("Số phút tối thiểu để tự đánh dấu") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryChipRow(selectedKey = categoryKey, onSelectCategory = { categoryKey = it })
                if (tags.isNotEmpty()) {
                    Text("Liên kết Tag (tự hoàn thành khi đo/ghi tag này):", fontSize = 11.sp, color = WalletMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.filter { !it.isArchived }.take(6).forEach { t ->
                            ChoiceChip(t.name, selectedTagId == t.id, "Tag ${t.name}", leadingCategory = t.category) {
                                selectedTagId = if (selectedTagId == t.id) null else t.id
                                categoryKey = t.category.key
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minM = minMinsText.toIntOrNull()?.coerceIn(1, 240) ?: 15
                    onCreate(title, categoryKey, selectedTagId, freq, minM)
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WalletMintDark, contentColor = Color.White)
            ) {
                Text("Lưu thói quen", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy", color = WalletMuted) }
        }
    )
}
