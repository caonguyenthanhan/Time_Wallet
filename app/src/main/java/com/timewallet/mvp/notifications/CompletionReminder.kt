package com.timewallet.mvp.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.timewallet.mvp.MainActivity
import com.timewallet.mvp.R
import com.timewallet.mvp.data.local.TimeWalletDatabase
import com.timewallet.mvp.domain.model.ConfirmationState
import com.timewallet.mvp.domain.model.LedgerRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface ReminderScheduler {
    fun syncReminders(records: List<LedgerRecord>, nowEpochMs: Long)
    fun cancelReminder(recordId: String)
}

object CompletionReminderPolicy {
    fun selectTargets(records: List<LedgerRecord>, nowEpochMs: Long): List<LedgerRecord> =
        records.filter { r ->
            if (r.isCancelled || r.isNeedsReconciliation) return@filter false
            val end = r.endedAt ?: return@filter false
            if (end <= nowEpochMs) return@filter false
            val state = r.resolveConfirmationStateAt(nowEpochMs)
            state == ConfirmationState.PLANNED || state == ConfirmationState.DOING
        }

    fun selectMissedRecentPending(
        records: List<LedgerRecord>,
        nowEpochMs: Long,
        lookbackMs: Long = 24L * 3_600_000L
    ): List<LedgerRecord> = records.filter { r ->
        if (r.isCancelled || r.isNeedsReconciliation) return@filter false
        val end = r.endedAt ?: return@filter false
        if (end > nowEpochMs || end < nowEpochMs - lookbackMs) return@filter false
        r.resolveConfirmationStateAt(nowEpochMs) == ConfirmationState.PENDING_CONFIRMATION
    }
}

object NoOpReminderScheduler : ReminderScheduler {
    override fun syncReminders(records: List<LedgerRecord>, nowEpochMs: Long) = Unit
    override fun cancelReminder(recordId: String) = Unit
}

object CompletionNotificationHelper {
    const val CHANNEL_ID = "time_wallet_completion_confirm"
    const val ACTION_CONFIRM_REMINDER = "com.timewallet.mvp.action.CONFIRM_ACTIVITY_END"
    const val EXTRA_CONFIRM_RECORD_ID = "extra_confirm_record_id"
    private const val PREF_SCHEDULED_IDS = "scheduled_record_ids"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Nhắc xác nhận hoạt động",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo khi hoạt động dự kiến hoặc đang diễn ra tới giờ kết thúc."
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showConfirmationNotification(context: Context, record: LedgerRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val zone = runCatching { ZoneId.of(record.timezone) }.getOrDefault(ZoneId.systemDefault())
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val rangeText = if (record.startedAt != null && record.endedAt != null) {
            val s = Instant.ofEpochMilli(record.startedAt).atZone(zone).format(hm)
            val e = Instant.ofEpochMilli(record.endedAt).atZone(zone).format(hm)
            "$s–$e · "
        } else ""

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_CONFIRM_REMINDER
            data = Uri.parse("timewallet://confirm/${Uri.encode(record.id)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONFIRM_RECORD_ID, record.id)
        }
        val tapPi = PendingIntent.getActivity(
            context,
            record.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_wallet)
            .setContentTitle("Đã tới giờ kết thúc: ${record.title}")
            .setContentText("${rangeText}Chạm để xác nhận hoàn thành, điều chỉnh, gián đoạn hoặc hủy.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(record.id.hashCode(), notif)
        } catch (_: SecurityException) {
            // Quyền thông báo có thể bị thu hồi bởi người dùng trên Android 13+.
        }
    }

    fun cancelNotification(context: Context, recordId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(recordId.hashCode())
        }
    }

    internal fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("time_wallet_reminders", Context.MODE_PRIVATE)

    internal fun readScheduledIds(context: Context): Set<String> =
        prefs(context).getStringSet(PREF_SCHEDULED_IDS, emptySet()) ?: emptySet()

    internal fun writeScheduledIds(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(PREF_SCHEDULED_IDS, ids).apply()
    }
}

class AndroidCompletionReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext

    private fun receiverPendingIntent(recordId: String): PendingIntent {
        val intent = Intent(appContext, CompletionReminderReceiver::class.java).apply {
            action = CompletionNotificationHelper.ACTION_CONFIRM_REMINDER
            putExtra(CompletionNotificationHelper.EXTRA_CONFIRM_RECORD_ID, recordId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            recordId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun syncReminders(records: List<LedgerRecord>, nowEpochMs: Long) {
        CompletionNotificationHelper.ensureChannel(appContext)
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prevIds = CompletionNotificationHelper.readScheduledIds(appContext)

        val targets = CompletionReminderPolicy.selectTargets(records, nowEpochMs)
        val nextIds = targets.map { it.id }.toSet()

        for (oldId in prevIds - nextIds) {
            am.cancel(receiverPendingIntent(oldId))
            CompletionNotificationHelper.cancelNotification(appContext, oldId)
        }

        for (record in targets) {
            val triggerAt = record.endedAt ?: continue
            val pi = receiverPendingIntent(record.id)
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
        CompletionNotificationHelper.writeScheduledIds(appContext, nextIds)
    }

    override fun cancelReminder(recordId: String) {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.cancel(receiverPendingIntent(recordId))
        CompletionNotificationHelper.cancelNotification(appContext, recordId)
        val remaining = CompletionNotificationHelper.readScheduledIds(appContext) - recordId
        CompletionNotificationHelper.writeScheduledIds(appContext, remaining)
    }
}

class CompletionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CompletionNotificationHelper.ACTION_CONFIRM_REMINDER) return
        val recordId = intent.getStringExtra(CompletionNotificationHelper.EXTRA_CONFIRM_RECORD_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val dao = TimeWalletDatabase.getInstance(context).ledgerDao()
                val entity = dao.getAllRecordsSnapshot().firstOrNull { it.id == recordId }
                val domain = entity?.toDomain()
                if (domain != null && !domain.isCancelled && !domain.isNeedsReconciliation) {
                    val state = domain.resolveConfirmationStateAt(now)
                    if (state == ConfirmationState.PENDING_CONFIRMATION ||
                        ((state == ConfirmationState.PLANNED || state == ConfirmationState.DOING) && (domain.endedAt ?: Long.MAX_VALUE) <= now + 60_000L)
                    ) {
                        CompletionNotificationHelper.showConfirmationNotification(context, domain)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Khôi phục lịch nhắc xác nhận sau khi thiết bị khởi động lại, cập nhật ứng dụng hoặc đổi giờ/múi giờ.
 */
class CompletionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val records = TimeWalletDatabase.getInstance(appContext)
                    .ledgerDao()
                    .getAllRecordsSnapshot()
                    .map { it.toDomain() }
                AndroidCompletionReminderScheduler(appContext).syncReminders(records, now)
                CompletionReminderPolicy.selectMissedRecentPending(records, now).forEach { overdue ->
                    CompletionNotificationHelper.showConfirmationNotification(appContext, overdue)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED
        )
    }
}
