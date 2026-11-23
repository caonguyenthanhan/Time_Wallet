package com.timewallet.mvp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.timewallet.mvp.MainActivity
import com.timewallet.mvp.R
import com.timewallet.mvp.data.local.WallyActionController
import com.timewallet.mvp.domain.usecase.CurrentWorkMode
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.domain.usecase.WallyHubSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget màn hình chính của Wally:
 * - Hỗ trợ 3 kích thước thích ứng: 2x2 gọn (`small`), 4x2 vừa (`medium`), 4x3+ mở rộng (`large`).
 * - Đọc và ghi trực tiếp qua [WallyActionController] (cùng SSOT với app và Floating Assistant).
 * - Hiển thị ưu tiên: Đang làm -> Sắp làm -> Lịch trình & Thời gian trống -> To-do -> Wally AI.
 */
class WallyHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgetsNow(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgetsNow(context.applicationContext, appWidgetManager, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext
        val action = intent.action ?: return
        val handledActions = setOf(
            WallyActionController.ACTION_WIDGET_REFRESH,
            ACTION_PAUSE_RESUME,
            ACTION_STOP_SAVE,
            ACTION_TOGGLE_TODO,
            ACTION_START_TODO,
            ACTION_NEXT_TODO,
            ACTION_TODO_ROW_DISPATCH
        )
        if (action !in handledActions) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val effectiveAction = if (action == ACTION_TODO_ROW_DISPATCH) {
                    intent.getStringExtra(EXTRA_ROW_ACTION) ?: ACTION_OPEN_TASKS
                } else {
                    action
                }

                when (effectiveAction) {
                    ACTION_PAUSE_RESUME -> {
                        val snap = WallyActionController.loadHubSnapshot(appContext)
                        when (snap.current?.mode) {
                            CurrentWorkMode.ACTIVE_TIMER -> WallyActionController.pauseActiveTimer(appContext)
                            CurrentWorkMode.PAUSED_TIMER -> WallyActionController.resumePausedTimer(appContext)
                            CurrentWorkMode.CURRENT_PLANNED -> {
                                val cur = snap.current
                                WallyActionController.startTimerForTaskOrPlan(
                                    context = appContext,
                                    title = cur.title,
                                    category = cur.category,
                                    taskId = cur.taskId,
                                    targetMinutes = cur.targetDurationSeconds?.div(60L)?.toInt()
                                )
                            }
                            else -> {
                                val topTodo = snap.todoItems.firstOrNull { !it.isDone }
                                if (topTodo != null) {
                                    WallyActionController.startTimerForTaskOrPlan(
                                        context = appContext,
                                        title = topTodo.title,
                                        category = topTodo.category,
                                        taskId = topTodo.id,
                                        targetMinutes = topTodo.estimatedMinutes
                                    )
                                } else {
                                    openAppScreen(appContext, tab = "focus", openChat = false)
                                }
                            }
                        }
                    }

                    ACTION_STOP_SAVE -> WallyActionController.stopAndSaveActiveTimer(appContext)

                    ACTION_TOGGLE_TODO -> {
                        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                        if (!taskId.isNullOrBlank()) {
                            WallyActionController.toggleTaskDone(appContext, taskId)
                        }
                    }

                    ACTION_START_TODO -> {
                        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                        val snap = WallyActionController.loadHubSnapshot(appContext)
                        val task = snap.todoItems.firstOrNull { it.id == taskId }
                        if (task != null) {
                            val res = WallyActionController.startTimerForTaskOrPlan(
                                context = appContext,
                                title = task.title,
                                category = task.category,
                                taskId = task.id,
                                targetMinutes = task.estimatedMinutes
                            )
                            if (res is WallyActionController.ActionOutcome.Blocked) {
                                openAppScreen(appContext, tab = "focus", openChat = false)
                            }
                        }
                    }

                    ACTION_NEXT_TODO -> {
                        val prefs = appContext.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                        val nextIdx = (prefs.getInt(KEY_SPOTLIGHT_INDEX, 0) + 1) % 50
                        prefs.edit().putInt(KEY_SPOTLIGHT_INDEX, nextIdx).apply()
                    }

                    ACTION_OPEN_TASKS -> openAppScreen(appContext, tab = "tasks", openChat = false)

                    WallyActionController.ACTION_WIDGET_REFRESH -> Unit
                }

                val mgr = AppWidgetManager.getInstance(appContext)
                val ids = mgr.getAppWidgetIds(ComponentName(appContext, WallyHomeWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    updateWidgetsNow(appContext, mgr, ids)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidgetsNow(
        appContext: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        val snapshot = WallyActionController.loadHubSnapshot(appContext)
        val prefs = appContext.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val spotlightIndex = prefs.getInt(KEY_SPOTLIGHT_INDEX, 0)

        for (widgetId in widgetIds) {
            val options = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
            val views = buildRemoteViews(
                context = appContext,
                widgetId = widgetId,
                snapshot = snapshot,
                spotlightIndex = spotlightIndex,
                minWidthDp = minWidth,
                minHeightDp = minHeight
            )
            manager.updateAppWidget(widgetId, views)
        }
        runCatching {
            manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_todo_list)
        }
    }

    private fun buildRemoteViews(
        context: Context,
        widgetId: Int,
        snapshot: WallyHubSnapshot,
        spotlightIndex: Int,
        minWidthDp: Int,
        minHeightDp: Int
    ): RemoteViews {
        val layoutId = when {
            minWidthDp < 210 || minHeightDp < 145 -> R.layout.wally_widget_small
            minHeightDp >= 220 -> R.layout.wally_widget_large
            else -> R.layout.wally_widget_medium
        }
        val views = RemoteViews(context.packageName, layoutId)

        // Root & Deep links
        views.setOnClickPendingIntent(
            R.id.widget_root,
            openAppPendingIntent(context, requestCode = 1000 + widgetId, tab = "schedule", openChat = false)
        )
        views.setOnClickPendingIntent(
            R.id.widget_current_box,
            openAppPendingIntent(context, requestCode = 1100 + widgetId, tab = "focus", openChat = false)
        )
        views.setOnClickPendingIntent(
            R.id.widget_upcoming_box,
            openAppPendingIntent(context, requestCode = 1200 + widgetId, tab = "schedule", openChat = false)
        )

        // Header meta
        val headerMeta = if (layoutId == R.layout.wally_widget_small) {
            "Trống ${TimeRange.formatDuration(snapshot.freeRemainingMinutes)} · ${snapshot.openTodoCount} việc"
        } else {
            "Thực tế ${TimeRange.formatDuration(snapshot.actualRecordedMinutes)} · Trống ${TimeRange.formatDuration(snapshot.freeRemainingMinutes)}"
        }
        views.setTextViewText(R.id.widget_header_meta, headerMeta)

        // Current work section
        val cur = snapshot.current
        when (cur?.mode) {
            CurrentWorkMode.ACTIVE_TIMER -> {
                views.setTextViewText(R.id.widget_current_badge, "● ĐANG LÀM")
                views.setTextViewText(R.id.widget_current_title, cur.title)
                views.setTextViewText(R.id.widget_current_sub, cur.subtitleVi)
                val base = SystemClock.elapsedRealtime() - (cur.elapsedSeconds.coerceAtLeast(0L) * 1000L)
                views.setChronometer(R.id.widget_current_chronometer, base, null, true)
                views.setViewVisibility(R.id.widget_current_chronometer, View.VISIBLE)
                views.setTextViewText(R.id.widget_btn_primary, "⏸ Tạm dừng")
                views.setViewVisibility(R.id.widget_btn_stop, View.VISIBLE)
            }

            CurrentWorkMode.PAUSED_TIMER -> {
                views.setTextViewText(R.id.widget_current_badge, "⏸ TẠM DỪNG")
                views.setTextViewText(R.id.widget_current_title, cur.title)
                views.setTextViewText(R.id.widget_current_sub, cur.subtitleVi)
                views.setChronometer(R.id.widget_current_chronometer, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.widget_current_chronometer, View.GONE)
                views.setTextViewText(R.id.widget_btn_primary, "▶ Tiếp tục")
                views.setViewVisibility(R.id.widget_btn_stop, View.VISIBLE)
            }

            CurrentWorkMode.CURRENT_PLANNED -> {
                views.setTextViewText(R.id.widget_current_badge, "◷ THEO LỊCH")
                views.setTextViewText(R.id.widget_current_title, cur.title)
                views.setTextViewText(R.id.widget_current_sub, cur.subtitleVi)
                views.setChronometer(R.id.widget_current_chronometer, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.widget_current_chronometer, View.GONE)
                views.setTextViewText(R.id.widget_btn_primary, "▶ Bắt đầu đo")
                views.setViewVisibility(R.id.widget_btn_stop, View.GONE)
            }

            else -> {
                views.setTextViewText(R.id.widget_current_badge, "✓ SẴN SÀNG")
                val topOpen = snapshot.todoItems.firstOrNull { !it.isDone }
                views.setTextViewText(
                    R.id.widget_current_title,
                    topOpen?.title ?: "Chưa có phiên đo"
                )
                views.setTextViewText(
                    R.id.widget_current_sub,
                    snapshot.nextFreeWindowLabel?.let { "Trống kế: $it" }
                        ?: "Chạm để mở hoặc bấm đo"
                )
                views.setChronometer(R.id.widget_current_chronometer, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.widget_current_chronometer, View.GONE)
                views.setTextViewText(
                    R.id.widget_btn_primary,
                    if (topOpen != null) "▶ Đo việc đầu" else "▶ Bắt đầu đo"
                )
                views.setViewVisibility(R.id.widget_btn_stop, View.GONE)
            }
        }

        views.setOnClickPendingIntent(
            R.id.widget_btn_primary,
            broadcastPendingIntent(context, ACTION_PAUSE_RESUME, requestCode = 2000 + widgetId)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_stop,
            broadcastPendingIntent(context, ACTION_STOP_SAVE, requestCode = 2100 + widgetId)
        )

        // Upcoming section
        val up = snapshot.upcoming
        if (up != null) {
            views.setTextViewText(R.id.widget_upcoming_title, "${up.startHm} · ${up.title}")
            views.setTextViewText(
                R.id.widget_upcoming_meta,
                if (up.startsInMinutes >= 60) "sau ${TimeRange.formatDuration(up.startsInMinutes)}" else "còn ${up.startsInMinutes}p"
            )
        } else {
            views.setTextViewText(R.id.widget_upcoming_title, "Không còn lịch cố định")
            views.setTextViewText(
                R.id.widget_upcoming_meta,
                snapshot.nextFreeWindowLabel ?: "Trống ${TimeRange.formatDuration(snapshot.freeRemainingMinutes)}"
            )
        }

        if (layoutId == R.layout.wally_widget_medium || layoutId == R.layout.wally_widget_large) {
            views.setOnClickPendingIntent(
                R.id.widget_btn_chat,
                openAppPendingIntent(context, requestCode = 1300 + widgetId, tab = "schedule", openChat = true)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_refresh,
                broadcastPendingIntent(context, WallyActionController.ACTION_WIDGET_REFRESH, requestCode = 2200 + widgetId)
            )
            val progressPct = (snapshot.timelineProgressFraction * 100f).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_day_progress, 100, progressPct, false)

            views.setOnClickPendingIntent(
                R.id.widget_ai_box,
                openAppPendingIntent(context, requestCode = 1400 + widgetId, tab = "tasks", openChat = false)
            )
            views.setTextViewText(
                R.id.widget_ai_badge,
                if (snapshot.isCloudAiInsight) "✦ Wally AI" else "⚡ Tổng hợp"
            )
            views.setTextViewText(R.id.widget_ai_text, snapshot.wallyInsightText)
        }

        if (layoutId == R.layout.wally_widget_medium) {
            val openTodos = snapshot.todoItems.filter { !it.isDone }
            if (openTodos.isEmpty()) {
                views.setTextViewText(R.id.widget_medium_todo_title, "Không có To-do tồn đọng (${snapshot.doneTodayTodoCount} đã xong)")
                views.setTextViewText(R.id.widget_medium_todo_meta, "Chạm để thêm hoặc xem việc")
                views.setViewVisibility(R.id.widget_btn_toggle_top_todo, View.GONE)
                views.setViewVisibility(R.id.widget_btn_start_top_todo, View.GONE)
                views.setViewVisibility(R.id.widget_btn_next_todo, View.GONE)
            } else {
                val safeIdx = spotlightIndex % openTodos.size
                val item = openTodos[safeIdx]
                views.setTextViewText(
                    R.id.widget_medium_todo_title,
                    "${item.title} (${safeIdx + 1}/${openTodos.size})"
                )
                val metaParts = buildList {
                    item.dueLabel?.let { add(it) }
                    item.estimatedMinutes?.let { add("${it}p") }
                    if (item.hasRiskWarning && !item.riskLabel.isNullOrBlank()) {
                        add("⚠ ${item.riskLabel}")
                    }
                }
                views.setTextViewText(
                    R.id.widget_medium_todo_meta,
                    metaParts.ifEmpty { listOf(item.priority.labelVi) }.joinToString(" · ")
                )
                views.setViewVisibility(R.id.widget_btn_toggle_top_todo, View.VISIBLE)
                views.setViewVisibility(R.id.widget_btn_start_top_todo, View.VISIBLE)
                views.setViewVisibility(
                    R.id.widget_btn_next_todo,
                    if (openTodos.size > 1) View.VISIBLE else View.GONE
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_toggle_top_todo,
                    broadcastPendingIntent(
                        context = context,
                        action = ACTION_TOGGLE_TODO,
                        requestCode = 2300 + widgetId,
                        taskId = item.id
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_start_top_todo,
                    broadcastPendingIntent(
                        context = context,
                        action = ACTION_START_TODO,
                        requestCode = 2400 + widgetId,
                        taskId = item.id
                    )
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_next_todo,
                    broadcastPendingIntent(
                        context = context,
                        action = ACTION_NEXT_TODO,
                        requestCode = 2500 + widgetId
                    )
                )
            }
            views.setOnClickPendingIntent(
                R.id.widget_medium_todo_card,
                openAppPendingIntent(context, requestCode = 1500 + widgetId, tab = "tasks", openChat = false)
            )
        }

        if (layoutId == R.layout.wally_widget_large) {
            views.setOnClickPendingIntent(
                R.id.widget_schedule_box,
                openAppPendingIntent(context, requestCode = 1600 + widgetId, tab = "schedule", openChat = false)
            )
            val freeText = snapshot.nextFreeWindowLabel?.let {
                "Trống ${TimeRange.formatDuration(snapshot.freeRemainingMinutes)} · Kế: $it"
            } ?: "Trống ${TimeRange.formatDuration(snapshot.freeRemainingMinutes)}"
            views.setTextViewText(R.id.widget_free_summary, freeText)

            val timelineSummary = if (snapshot.todayTimeline.isEmpty()) {
                "Chưa có khối thời gian nào hôm nay — chạm để thêm lịch"
            } else {
                snapshot.todayTimeline.take(4).joinToString(" • ") { item ->
                    val prefix = if (item.isPlanned) "◷" else "✓"
                    "$prefix ${item.startHm} ${item.title}"
                }
            }
            views.setTextViewText(R.id.widget_timeline_summary, timelineSummary)

            views.setTextViewText(
                R.id.widget_todo_header_count,
                "To-do hôm nay · ${snapshot.openTodoCount} mở / ${snapshot.doneTodayTodoCount} xong"
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_add_todo,
                openAppPendingIntent(context, requestCode = 1700 + widgetId, tab = "tasks", openChat = false)
            )

            val svcIntent = Intent(context, WallyHomeWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_todo_list, svcIntent)
            views.setEmptyView(R.id.widget_todo_list, R.id.widget_todo_empty)
            views.setPendingIntentTemplate(
                R.id.widget_todo_list,
                rowDispatchPendingIntentTemplate(context, widgetId)
            )
        }

        return views
    }

    private fun openAppScreen(context: Context, tab: String, openChat: Boolean) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WallyActionController.EXTRA_OPEN_TAB, tab)
            putExtra(WallyActionController.EXTRA_OPEN_CHAT, openChat)
        }
        context.startActivity(launchIntent)
    }

    private fun openAppPendingIntent(
        context: Context,
        requestCode: Int,
        tab: String,
        openChat: Boolean
    ): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WallyActionController.EXTRA_OPEN_TAB, tab)
            putExtra(WallyActionController.EXTRA_OPEN_CHAT, openChat)
            data = Uri.parse("timewallet://widget/$tab/$openChat/$requestCode")
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcastPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        taskId: String? = null
    ): PendingIntent {
        val intent = Intent(context, WallyHomeWidgetProvider::class.java).apply {
            this.action = action
            if (taskId != null) putExtra(EXTRA_TASK_ID, taskId)
            data = Uri.parse("timewallet://widget/action/$action/${taskId.orEmpty()}/$requestCode")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rowDispatchPendingIntentTemplate(context: Context, widgetId: Int): PendingIntent {
        val template = Intent(context, WallyHomeWidgetProvider::class.java).apply {
            action = ACTION_TODO_ROW_DISPATCH
        }
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            3000 + widgetId,
            template,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability
        )
    }

    companion object {
        const val ACTION_PAUSE_RESUME = "com.timewallet.mvp.widget.ACTION_PAUSE_RESUME"
        const val ACTION_STOP_SAVE = "com.timewallet.mvp.widget.ACTION_STOP_SAVE"
        const val ACTION_TOGGLE_TODO = "com.timewallet.mvp.widget.ACTION_TOGGLE_TODO"
        const val ACTION_START_TODO = "com.timewallet.mvp.widget.ACTION_START_TODO"
        const val ACTION_NEXT_TODO = "com.timewallet.mvp.widget.ACTION_NEXT_TODO"
        const val ACTION_TODO_ROW_DISPATCH = "com.timewallet.mvp.widget.ACTION_TODO_ROW_DISPATCH"
        const val ACTION_OPEN_TASKS = "com.timewallet.mvp.widget.ACTION_OPEN_TASKS"

        const val EXTRA_TASK_ID = "widget_task_id"
        const val EXTRA_ROW_ACTION = "widget_row_action"

        private const val WIDGET_PREFS = "wally_widget_ui_prefs"
        private const val KEY_SPOTLIGHT_INDEX = "spotlight_idx"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
