package dev.saikrishna.todo.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.saikrishna.todo.MainActivity
import dev.saikrishna.todo.R
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.PlanPrefs
import dev.saikrishna.todo.domain.PlanSection
import dev.saikrishna.todo.domain.Planner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

import java.time.ZoneId

/**
 * v0.8 "Plan your day": one inexact daily alarm (a few minutes of batching is fine for a morning
 * nudge, so no exact-alarm permission is involved) at the chosen time, re-armed after each fire,
 * on settings changes, at app start and after boot / clock changes.
 */
object PlanReminder {
    const val CHANNEL = "planning"
    const val ACTION_PLAN = "dev.saikrishna.todo.PLAN"
    const val EXTRA_OPEN_PLAN = "open_plan"
    private const val NOTIFICATION_ID = -100

    /** Pure: the next time [minute] (after local midnight) strictly after [now]. */
    fun nextAt(now: Long, minute: Int, zone: ZoneId): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        var at = local.toLocalDate().atTime(minute / 60, minute % 60).atZone(zone)
        if (!at.toInstant().isAfter(Instant.ofEpochMilli(now))) at = local.toLocalDate().plusDays(1).atTime(minute / 60, minute % 60).atZone(zone)
        return at.toInstant().toEpochMilli()
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, PlanReceiver::class.java).setAction(ACTION_PLAN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun sync(context: Context, prefs: PlanPrefs, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (!prefs.enabled) { am.cancel(alarm(context)); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(now, prefs.minute, zone), alarm(context))
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Plan your day", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A morning nudge to sort today's tasks"
            },
        )
    }

    /** "2 overdue · 3 due today", or null when there is nothing to plan. */
    fun summaryText(overdue: Int, today: Int, suggested: Int): String? = when {
        overdue + today == 0 && suggested == 0 -> null
        overdue + today == 0 -> "Nothing due. Pick something to do today."
        else -> listOfNotNull(overdue.takeIf { it > 0 }?.let { "$it overdue" }, today.takeIf { it > 0 }?.let { "$it due today" }).joinToString(" · ")
    }

    fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, MainActivity::class.java).setAction(ACTION_PLAN).putExtra(EXTRA_OPEN_PLAN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notify(context: Context, text: String) {
        if (!Reminders.notificationsAllowed(context)) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle("Plan your day")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context))
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) } catch (_: SecurityException) { }
    }
}

/** The daily alarm: post the nudge (if there is anything to plan) and arm tomorrow's. */
class PlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TodoApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = app.settings.plan.first()
                if (prefs.enabled) {
                    val zone = app.repository.zone()
                    val today = Instant.ofEpochMilli(app.repository.now()).atZone(zone).toLocalDate()
                    val q = Planner.queue(app.repository.tasks.first(), today)
                    PlanReminder.summaryText(
                        q.count { it.section == PlanSection.Overdue }, q.count { it.section == PlanSection.Today },
                        q.count { it.section == PlanSection.Suggested },
                    )?.let { PlanReminder.notify(app, it) }
                }
                PlanReminder.sync(app, prefs)
            } finally {
                pending.finish()
            }
        }
    }
}

