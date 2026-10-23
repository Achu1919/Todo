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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TodoApp : Application() {
    val database: TodoDatabase by lazy { TodoDatabase.build(this) }
    val repository: TaskRepository by lazy { TaskRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    /** v0.10: export / import. After an import every alarm is re-armed and the widget redrawn. */
    val backup: dev.saikrishna.todo.data.BackupManager by lazy {
        dev.saikrishna.todo.data.BackupManager(database, settings, BuildConfig.VERSION_NAME) { before, after ->
            Reminders.rearm(this, before, after, System.currentTimeMillis(), repository.zone())
            runCatching { dev.saikrishna.todo.widget.TodayWidget.refresh(this) }
            runCatching { dev.saikrishna.todo.widget.QuickWidgets.refresh(this) }
        }
    }
    /** v0.10: the launch entrance has played in this process (warm starts don't replay it). */
    var entranceShown = false
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
        startReminderSync()
        // v0.8: the morning planning alarm follows its setting (and is re-armed at every start).
        appScope.launch { settings.plan.distinctUntilChanged().collect { dev.saikrishna.todo.reminders.PlanReminder.sync(this@TodoApp, it) } }
        startWidgetSync()
        startQuickWidgetSync()
    }

    /** v0.8: the Today widget redraws whenever what it shows changes (not on every write). */
    private fun startWidgetSync() = appScope.launch {
        repository.tasks
            .map { tasks ->
                val today = java.time.Instant.ofEpochMilli(repository.now()).atZone(repository.zone()).toLocalDate()
                dev.saikrishna.todo.widget.WidgetData.build(tasks, today)
            }
            .distinctUntilChanged()
            .collect { runCatching { dev.saikrishna.todo.widget.TodayWidget.refresh(this@TodoApp) } }
    }

    /** v0.12: the quick-lists and hub widgets redraw when their chips or next task change. */
    private fun startQuickWidgetSync() = appScope.launch {
        kotlinx.coroutines.flow.combine(repository.tasks, repository.lists) { tasks, lists ->
            val today = java.time.Instant.ofEpochMilli(repository.now()).atZone(repository.zone()).toLocalDate()
            dev.saikrishna.todo.widget.QuickData.build(tasks, lists, today)
        }
            .distinctUntilChanged()
            .collect { runCatching { dev.saikrishna.todo.widget.QuickWidgets.refresh(this@TodoApp) } }
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
