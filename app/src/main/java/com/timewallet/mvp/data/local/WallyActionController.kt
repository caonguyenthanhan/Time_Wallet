package com.timewallet.mvp.data.local

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.timewallet.mvp.domain.model.ActiveTimerSession
import com.timewallet.mvp.domain.model.PausedTimerContinuation
import com.timewallet.mvp.domain.model.TaskItem
import com.timewallet.mvp.domain.model.TaskStatus
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AiTodoEngine
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.WallyHubSnapshot
import com.timewallet.mvp.domain.usecase.WallyHubSnapshotEngine
import com.timewallet.mvp.notifications.FocusPinNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Cầu nối ghi/đọc dùng chung (SSOT) giữa App chính, Home Widget và Floating Assistant.
 * Mọi thay đổi đều đi qua `TimerCommit`, `TaskDao` và `TimerAndSettingsStore` với một chuẩn nhất quán:
 * - Tạm dừng luôn chốt phần thời gian đã đo thực tế trước đó qua [TimerCommit], tuyệt đối không bịa thời gian nối dài.
 * - Hoàn thành To-do chỉ đổi trạng thái Task, không tự ghi thời lượng giả lập vào Sổ cái.
 */
object WallyActionController {

    const val ACTION_WIDGET_REFRESH = "com.timewallet.mvp.widget.ACTION_REFRESH"
    const val WIDGET_PROVIDER_CLASS = "com.timewallet.mvp.widget.WallyHomeWidgetProvider"
    const val EXTRA_OPEN_TAB = "wally_open_tab"
    const val EXTRA_OPEN_CHAT = "wally_open_chat"

    private val mutex = Mutex()
    private val _hubStateVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val hubStateVersion: kotlinx.coroutines.flow.StateFlow<Long> = _hubStateVersion

    fun notifyAllWidgets(context: Context) {
        _hubStateVersion.value += 1L
        val appContext = context.applicationContext
        val intent = Intent(ACTION_WIDGET_REFRESH).apply {
            component = ComponentName(appContext, WIDGET_PROVIDER_CLASS)
        }
        runCatching { appContext.sendBroadcast(intent) }
        runCatching {
            val mgr = AppWidgetManager.getInstance(appContext)
            val ids = mgr.getAppWidgetIds(ComponentName(appContext, WIDGET_PROVIDER_CLASS))
            if (ids.isNotEmpty()) {
                val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = ComponentName(appContext, WIDGET_PROVIDER_CLASS)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                appContext.sendBroadcast(updateIntent)
            }
        }
    }

    suspend fun loadHubSnapshot(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis()
    ): WallyHubSnapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val db = TimeWalletDatabase.getInstance(appContext)
        val store = TimerAndSettingsStore(appContext)

        val records = db.ledgerDao().getAllRecordsSnapshot().map { it.toDomain() }
        val taskEntities = db.taskDao().getAllTasksSnapshot()
        val subtaskEntities = db.taskDao().getAllSubtasksSnapshot()
        val byTask = subtaskEntities.map { it.toDomain() }.groupBy { it.taskId }
        val tasks = taskEntities.map { it.toDomain(byTask[it.id].orEmpty().sortedBy { s -> s.sortOrder }) }

        val activeTimer = store.loadActiveTimer()
        val pausedTimer = store.loadPausedTimer()
        val perms = store.loadAiReadPermissions()
        val aiPrefs = store.loadAiPreferences()
        val cachedReport = AiTodoEngine.deserializeReport(store.loadTodoAiAnalysisJson())

        WallyHubSnapshotEngine.build(
            records = records,
            tasks = tasks,
            activeTimer = activeTimer,
            pausedTimer = pausedTimer,
            nowEpochMs = nowEpochMs,
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            currentBootCount = TimerAndSettingsStore.readDeviceBootCount(appContext),
            zoneId = ZoneId.systemDefault(),
            aiReadPermissions = perms,
            cachedAiReport = cachedReport,
            aiEnabled = aiPrefs.optInEnabled && aiPrefs.apiKey.isNotBlank()
        )
    }

    sealed class ActionOutcome(val messageVi: String) {
        class Ok(msg: String) : ActionOutcome(msg)
        class Blocked(msg: String) : ActionOutcome(msg)
    }

    suspend fun pauseActiveTimer(context: Context): ActionOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val store = TimerAndSettingsStore(appContext)
            val active = store.loadActiveTimer() ?: return@withContext ActionOutcome.Blocked("Không có phiên đang chạy.")
            val nowMs = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val bootCount = TimerAndSettingsStore.readDeviceBootCount(appContext)
            val snap = active.computeElapsedSeconds(nowMs, nowElapsed, bootCount)
            val zone = runCatching { ZoneId.of(active.timezone) }.getOrDefault(ZoneId.systemDefault())
            val prevAccumulated = store.loadPausedTimer()?.accumulatedSeconds ?: 0L

            if (snap.elapsedSeconds >= 1L) {
                val res = TimerCommit.run(
                    dao = TimeWalletDatabase.getInstance(appContext).ledgerDao(),
                    title = active.title,
                    categoryKey = active.categoryKey,
                    startedAtEpochMs = active.startedAtEpochMs,
                    endEpochMs = snap.effectiveEndEpochMs,
                    zone = zone,
                    tagId = active.tagId,
                    taskId = active.taskId,
                    habitId = active.habitId
                )
                if (res is TimerCommitResult.Overlap) {
                    return@withContext ActionOutcome.Blocked("Phiên vừa chạy trùng lịch trên Sổ cái — mở app để đối soát trước khi tạm dừng.")
                }
                if (res is TimerCommitResult.Invalid) {
                    return@withContext ActionOutcome.Blocked(res.message)
                }
            }

            val totalAccumulated = prevAccumulated + snap.elapsedSeconds
            store.saveActiveTimer(null)
            store.savePausedTimer(
                PausedTimerContinuation(
                    title = active.title,
                    categoryKey = active.categoryKey,
                    targetDurationSeconds = active.targetDurationSeconds,
                    pausedAtEpochMs = nowMs,
                    accumulatedSeconds = totalAccumulated,
                    tagId = active.tagId,
                    taskId = active.taskId,
                    habitId = active.habitId
                ),
                immediate = true
            )
            syncFocusPinFromStore(appContext)
            notifyAllWidgets(appContext)
            ActionOutcome.Ok("Đã tạm dừng \"${active.title}\" và lưu đoạn đã đo.")
        }
    }

    suspend fun resumePausedTimer(context: Context): ActionOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val store = TimerAndSettingsStore(appContext)
            if (store.loadActiveTimer() != null) {
                return@withContext ActionOutcome.Blocked("Đồng hồ đang chạy.")
            }
            val paused = store.loadPausedTimer() ?: return@withContext ActionOutcome.Blocked("Không có phiên đang tạm dừng.")
            val session = ActiveTimerSession(
                title = paused.title,
                categoryKey = paused.categoryKey,
                startedAtEpochMs = System.currentTimeMillis(),
                startedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                timezone = ZoneId.systemDefault().id,
                targetDurationSeconds = paused.targetDurationSeconds,
                startedBootCount = TimerAndSettingsStore.readDeviceBootCount(appContext),
                tagId = paused.tagId,
                taskId = paused.taskId,
                habitId = paused.habitId
            )
            store.savePausedTimer(null, immediate = true)
            store.saveActiveTimer(session)
            syncFocusPinFromStore(appContext)
            notifyAllWidgets(appContext)
            ActionOutcome.Ok("Tiếp tục đo \"${paused.title}\".")
        }
    }

    suspend fun stopAndSaveActiveTimer(context: Context): ActionOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val store = TimerAndSettingsStore(appContext)
            val active = store.loadActiveTimer() ?: run {
                val paused = store.loadPausedTimer()
                if (paused != null) {
                    store.savePausedTimer(null, immediate = true)
                    notifyAllWidgets(appContext)
                    return@withContext ActionOutcome.Ok("Đã hoàn tất phiên \"${paused.title}\".")
                }
                return@withContext ActionOutcome.Blocked("Không có phiên đang chạy.")
            }
            val nowMs = System.currentTimeMillis()
            val snap = active.computeElapsedSeconds(nowMs, SystemClock.elapsedRealtime(), TimerAndSettingsStore.readDeviceBootCount(appContext))
            if (snap.elapsedSeconds < 1L) {
                return@withContext ActionOutcome.Blocked("Đồng hồ cần chạy ít nhất 1 giây.")
            }
            val zone = runCatching { ZoneId.of(active.timezone) }.getOrDefault(ZoneId.systemDefault())
            val res = TimerCommit.run(
                dao = TimeWalletDatabase.getInstance(appContext).ledgerDao(),
                title = active.title,
                categoryKey = active.categoryKey,
                startedAtEpochMs = active.startedAtEpochMs,
                endEpochMs = snap.effectiveEndEpochMs,
                zone = zone,
                tagId = active.tagId,
                taskId = active.taskId,
                habitId = active.habitId
            )
            when (res) {
                is TimerCommitResult.Saved, TimerCommitResult.AlreadyCommitted -> {
                    store.saveActiveTimer(null)
                    store.savePausedTimer(null, immediate = true)
                    syncFocusPinFromStore(appContext)
                    notifyAllWidgets(appContext)
                    ActionOutcome.Ok("Đã lưu \"${active.title}\" vào Sổ cái.")
                }
                is TimerCommitResult.Overlap -> ActionOutcome.Blocked("Bị trùng lịch trên Sổ cái — mở app để đối soát.")
                is TimerCommitResult.Invalid -> ActionOutcome.Blocked(res.message)
            }
        }
    }

    suspend fun startTimerForTaskOrPlan(
        context: Context,
        title: String,
        category: TimeCategory,
        tagId: String? = null,
        taskId: String? = null,
        targetMinutes: Int? = null
    ): ActionOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val store = TimerAndSettingsStore(appContext)
            if (store.loadActiveTimer() != null) {
                return@withContext ActionOutcome.Blocked("Đang có phiên chạy — hãy tạm dừng hoặc lưu trước.")
            }
            val cleanTitle = title.trim().ifEmpty { category.displayName }
            val session = ActiveTimerSession(
                title = cleanTitle,
                categoryKey = category.key,
                startedAtEpochMs = System.currentTimeMillis(),
                startedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                timezone = ZoneId.systemDefault().id,
                targetDurationSeconds = targetMinutes?.coerceIn(5, 240)?.times(60L),
                startedBootCount = TimerAndSettingsStore.readDeviceBootCount(appContext),
                tagId = tagId,
                taskId = taskId
            )
            store.savePausedTimer(null, immediate = true)
            store.saveActiveTimer(session)
            syncFocusPinFromStore(appContext)
            notifyAllWidgets(appContext)
            ActionOutcome.Ok("▶ Đang đo: $cleanTitle")
        }
    }

    suspend fun toggleTaskDone(context: Context, taskId: String): ActionOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val dao = TimeWalletDatabase.getInstance(appContext).taskDao()
            val entity = dao.getAllTasksSnapshot().firstOrNull { it.id == taskId }
                ?: return@withContext ActionOutcome.Blocked("Không tìm thấy công việc.")
            val subs = dao.getAllSubtasksSnapshot().filter { it.taskId == taskId }.map { it.toDomain() }
            val domain: TaskItem = entity.toDomain(subs)
            val becomingDone = !domain.isDone
            val nowMs = System.currentTimeMillis()
            val updated = domain.copy(
                status = if (becomingDone) TaskStatus.DONE else TaskStatus.TODO,
                completedAt = if (becomingDone) nowMs else null,
                updatedAt = nowMs
            )
            dao.upsertTask(TaskEntity.fromDomain(updated))
            notifyAllWidgets(appContext)
            ActionOutcome.Ok(if (becomingDone) "✓ Đã xong \"${updated.title}\"." else "Đã mở lại \"${updated.title}\".")
        }
    }

    suspend fun quickAddNaturalTask(context: Context, rawInput: String): ActionOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) {
                return@withContext ActionOutcome.Blocked("Hãy nhập nội dung công việc trước.")
            }
            val appContext = context.applicationContext
            val db = TimeWalletDatabase.getInstance(appContext)
            val store = TimerAndSettingsStore(appContext)
            val tags = db.tagDao().getAllSnapshot().map { it.toDomain() }
            val rules = store.loadLocalRules()
            val today = LocalDate.now()
            val parsed = AiTodoEngine.parseNaturalTaskInput(
                rawInput = trimmed,
                today = today,
                tags = tags,
                rules = rules
            ) ?: return@withContext ActionOutcome.Blocked("Không đọc được tiêu đề công việc.")
            val matchedTag = parsed.tagId ?: TagRules.matchTitle(parsed.title, tags)?.id
            val nowMs = Instant.now().toEpochMilli()
            val newTask = TaskItem(
                id = UUID.randomUUID().toString(),
                title = parsed.title,
                category = parsed.category,
                tagId = matchedTag,
                priority = parsed.priority,
                estimatedMinutes = parsed.estimatedMinutes,
                dueDateLocal = parsed.dueDateLocal,
                dueTimeLocal = parsed.dueTimeLocal,
                status = if (parsed.dueDateLocal != null) TaskStatus.TODO else TaskStatus.INBOX,
                createdAt = nowMs,
                updatedAt = nowMs
            )
            db.taskDao().upsertTask(TaskEntity.fromDomain(newTask))
            notifyAllWidgets(appContext)
            ActionOutcome.Ok("Đã thêm To-do: \"${newTask.title}\".")
        }
    }

    private suspend fun syncFocusPinFromStore(appContext: Context) {
        if (!TimerAndSettingsStore.isFocusPinEnabled(appContext)) return
        val records = TimeWalletDatabase.getInstance(appContext).ledgerDao().getAllRecordsSnapshot().map { it.toDomain() }
        val active = TimerAndSettingsStore(appContext).loadActiveTimer()
        FocusPinNotificationManager.syncPinnedFocus(
            context = appContext,
            enabled = true,
            records = records,
            activeTimer = active,
            nowEpochMs = System.currentTimeMillis()
        )
    }
}
