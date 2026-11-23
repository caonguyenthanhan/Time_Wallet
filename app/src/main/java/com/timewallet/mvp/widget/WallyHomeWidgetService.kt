package com.timewallet.mvp.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.timewallet.mvp.R
import com.timewallet.mvp.data.local.WallyActionController
import com.timewallet.mvp.domain.usecase.TodoItemSnapshot
import kotlinx.coroutines.runBlocking

class WallyHomeWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WallyTodoRemoteViewsFactory(applicationContext)
    }
}

private class WallyTodoRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<TodoItemSnapshot> = emptyList()

    override fun onCreate() {
        loadItems()
    }

    override fun onDataSetChanged() {
        loadItems()
    }

    private fun loadItems() {
        items = runCatching {
            runBlocking {
                WallyActionController.loadHubSnapshot(context).todoItems.take(12)
            }
        }.getOrDefault(emptyList())
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in items.indices) return null
        val item = items[position]
        val row = RemoteViews(context.packageName, R.layout.wally_widget_todo_item)
        row.setTextViewText(
            R.id.widget_todo_row_title,
            if (item.isDone) "✓ ${item.title}" else item.title
        )
        val metaParts = buildList {
            add(item.priority.badgeText)
            item.dueLabel?.let { add(it) }
            item.estimatedMinutes?.let { add("${it}p") }
            if (item.hasRiskWarning && !item.riskLabel.isNullOrBlank()) {
                add("⚠ ${item.riskLabel}")
            }
        }
        row.setTextViewText(R.id.widget_todo_row_meta, metaParts.joinToString(" · "))

        row.setOnClickFillInIntent(
            R.id.widget_todo_row_check,
            Intent().apply {
                putExtra(WallyHomeWidgetProvider.EXTRA_ROW_ACTION, WallyHomeWidgetProvider.ACTION_TOGGLE_TODO)
                putExtra(WallyHomeWidgetProvider.EXTRA_TASK_ID, item.id)
            }
        )
        row.setOnClickFillInIntent(
            R.id.widget_todo_row_start,
            Intent().apply {
                putExtra(WallyHomeWidgetProvider.EXTRA_ROW_ACTION, WallyHomeWidgetProvider.ACTION_START_TODO)
                putExtra(WallyHomeWidgetProvider.EXTRA_TASK_ID, item.id)
            }
        )
        row.setOnClickFillInIntent(
            R.id.widget_todo_row_body,
            Intent().apply {
                putExtra(WallyHomeWidgetProvider.EXTRA_ROW_ACTION, WallyHomeWidgetProvider.ACTION_OPEN_TASKS)
                putExtra(WallyHomeWidgetProvider.EXTRA_TASK_ID, item.id)
            }
        )
        return row
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
