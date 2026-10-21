package dev.saikrishna.todo.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.saikrishna.todo.MainActivity
import dev.saikrishna.todo.R
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.dueAtMillis
import dev.saikrishna.todo.domain.QuickParse
import java.time.ZoneId

/**
 * Reminders fire at a task's due time (no offset, by design for v0.6).
 *
 * - Exact (`setExactAndAllowWhileIdle`) when the app may schedule exact alarms
 *   (`AlarmManager.canScheduleExactAlarms()`: SCHEDULE_EXACT_ALARM granted by the user in system
 *   settings), else inexact `setAndAllowWhileIdle` (the system may batch it by a few minutes).
 * - One alarm per task, keyed by a `todo://task/<id>` data URI, so re-scheduling replaces it.
 * - Idempotent: [sync] can be called with any task at any time.
 */
object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_FIRE = "dev.saikrishna.todo.REMIND"
    const val ACTION_DONE = "dev.saikrishna.todo.DONE"
    const val ACTION_SNOOZE = "dev.saikrishna.todo.SNOOZE_1H"
    const val ACTION_TOMORROW = "dev.saikrishna.todo.TOMORROW"
    const val EXTRA_ID = "task_id"
    const val EXTRA_OPEN_TASK = "open_task"

    private fun uri(id: Long) = "todo://task/$id".toUri()

    private fun intent(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(action).setData(uri(id)).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun notificationsAllowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Schedules (or cancels) the reminder for [task] according to its current state. */
    fun sync(context: Context, task: Task, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val at = task.dueAtMillis(zone)
        if (task.done || at == null || at <= now) cancel(context, task.id) else schedule(context, task.id, at)
    }

    fun schedule(context: Context, id: Long, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context, ACTION_FIRE, id)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(intent(context, ACTION_FIRE, id))
    }

    fun dismissNotification(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toInt())

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A nudge at a task's due time"
            },
        )
    }

    /** Posts the reminder for [task]. Silently does nothing if notifications are not allowed. */
    fun notify(context: Context, task: Task) {
        if (!notificationsAllowed(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setData(uri(task.id))
                .putExtra(EXTRA_OPEN_TASK, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = task.dueMinute?.let(QuickParse::timeLabel)
        val text = listOfNotNull(time?.let { "Due $it" }, task.notes.takeIf { it.isNotBlank() }).joinToString(" · ")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(task.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Done", intent(context, ACTION_DONE, task.id))
            .addAction(0, "Snooze 1h", intent(context, ACTION_SNOOZE, task.id))
            .addAction(0, "Tomorrow", intent(context, ACTION_TOMORROW, task.id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(task.id.toInt(), n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
