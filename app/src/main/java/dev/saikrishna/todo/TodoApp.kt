package dev.saikrishna.todo

import android.app.Application
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.data.dueAtMillis
import dev.saikrishna.todo.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TodoApp : Application() {
    val database: TodoDatabase by lazy { TodoDatabase.build(this) }
    val repository: TaskRepository by lazy { TaskRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
        startReminderSync()
    }

    /**
     * Keeps alarms in step with the database, whatever changed it (composer, panel, snooze,
     * notification actions): every emission is diffed against what was last scheduled, and only
     * changed tasks touch AlarmManager. The first emission re-arms everything.
     */
    private fun startReminderSync() = appScope.launch {
        var last = emptyMap<Long, Long?>() // id → scheduled-at (null = none)
        repository.tasks.collect { list ->
            val zone = repository.zone()
            val now = System.currentTimeMillis()
            val current = HashMap<Long, Long?>(list.size)
            for (t: Task in list) {
                val at = t.dueAtMillis(zone)?.takeIf { !t.done && it > now }
                current[t.id] = at
                if (!last.containsKey(t.id) || last[t.id] != at) Reminders.sync(this@TodoApp, t, now, zone)
            }
            for ((id, at) in last) if (at != null && id !in current) Reminders.cancel(this@TodoApp, id)
            last = current
        }
    }
}
