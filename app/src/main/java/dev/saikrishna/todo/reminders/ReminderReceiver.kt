package dev.saikrishna.todo.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.saikrishna.todo.TodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/** Alarm fired → post the notification; notification actions → Done / Snooze 1h / Tomorrow. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext as TodoApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(app, intent.action, id)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: TodoApp, action: String?, id: Long) {
        val repo = app.repository
        val task = repo.get(id) ?: return
        when (action) {
            Reminders.ACTION_FIRE -> if (!task.done) Reminders.notify(app, task)
            Reminders.ACTION_DONE -> {
                Reminders.dismissNotification(app, id)
                val next = repo.setDone(id, true).spawnedId
                next?.let { repo.get(it) }?.let { Reminders.sync(app, it) }
            }
            Reminders.ACTION_SNOOZE -> {
                Reminders.dismissNotification(app, id)
                val zone = repo.zone()
                val at = Instant.ofEpochMilli(repo.now()).atZone(zone).plusHours(1).withSecond(0).withNano(0)
                repo.setDueAt(id, at.toLocalDate().toEpochDay(), at.hour * 60 + at.minute)
                repo.get(id)?.let { Reminders.sync(app, it) }
            }
            Reminders.ACTION_TOMORROW -> {
                Reminders.dismissNotification(app, id)
                val today = Instant.ofEpochMilli(repo.now()).atZone(repo.zone()).toLocalDate()
                repo.setDueAt(id, today.plusDays(1).toEpochDay(), task.dueMinute ?: 9 * 60)
                repo.get(id)?.let { Reminders.sync(app, it) }
            }
        }
    }
}

/**
 * Re-arms every reminder after events that clear or shift alarms: boot, app update, clock or
 * timezone change, and the exact-alarm permission being granted/revoked.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TodoApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.repository.timed().forEach { Reminders.sync(app, it) }
                PlanReminder.sync(app, app.settings.plan.first())
            } finally {
                pending.finish()
            }
        }
    }
}

