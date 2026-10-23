package dev.saikrishna.todo.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.saikrishna.todo.data.AppSettings
import dev.saikrishna.todo.data.Completion
import dev.saikrishna.todo.data.FocusPrefs
import dev.saikrishna.todo.data.ListArea
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.tagList
import dev.saikrishna.todo.domain.Focus
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.domain.FocusSchedule
import dev.saikrishna.todo.domain.Template
import dev.saikrishna.todo.domain.TemplateItem
import dev.saikrishna.todo.domain.Templates
import java.time.LocalDate
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.data.NewTask
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.RepeatRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Task deleted · Undo" (spec 6.6). [id] makes every event distinct so timers restart.
 * v0.7: also "Completed with N subtasks · Undo" ([completion] set: re-open instead of restore).
 */
@Immutable
data class UndoEvent(
    val id: Long,
    val message: String,
    val tasks: List<Task>,
    val subtasks: List<Subtask> = emptyList(),
    val completion: Completion? = null,
)

/**
 * Search state shared with Home (v0.7 tag pill, v0.8 saved views from the list menu): the filter
 * set, and a query to put in the field when Search opens (consumed once).
 */
@Immutable
data class SearchFilter(
    val filter: dev.saikrishna.todo.domain.ViewFilter = dev.saikrishna.todo.domain.ViewFilter(),
    val query: String? = null,
) {
    constructor(tag: String) : this(dev.saikrishna.todo.domain.ViewFilter(tag = tag))
}

/** v0.7: an optimistic manual order / day change, shown until Room catches up. */
@Immutable
data class OrderOverride(val key: Long, val day: Long?)

/** Activity-scoped: shared by Home, Search and Settings. */
class TodoViewModel(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val overrides = MutableStateFlow<Map<Long, DoneOverride>>(emptyMap())
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())
    /** Delete and undo-restore must hit the database in order. */
    private val deleteLock = Mutex()

    /**
     * null until the first load completes, so the UI can tell "loading" from "empty".
     * Completion toggles and deletes apply optimistically, on the tap frame, not when Room re-emits.
     */
    private val orderOverrides = MutableStateFlow<Map<Long, OrderOverride>>(emptyMap())

    val tasks: StateFlow<List<Task>?> =
        combine(repository.tasks, overrides, hidden, orderOverrides) { list, ov, hid, ord ->
            applyOrder(applyOverrides(list, ov, hid), ord)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null) // ready on every (re)entry, never a fresh empty value

    /** v0.7 organisation. */
    val lists: StateFlow<List<TaskList>> = repository.lists.stateIn(viewModelScope, SharingStarted.Eagerly, TaskList.Defaults)
    val subtasks: StateFlow<Map<Long, List<Subtask>>> = repository.subtasks
        .map { all -> all.groupBy { it.taskId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val templates: StateFlow<List<Template>> = repository.templates.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** v0.8 saved search views. */
    val views: StateFlow<List<dev.saikrishna.todo.data.SavedView>> = repository.views.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun saveView(name: String, query: String, filter: dev.saikrishna.todo.domain.ViewFilter) =
        viewModelScope.launch { repository.saveView(name, query, filter.encode()) }
    fun deleteView(id: Long) = viewModelScope.launch { repository.deleteView(id) }

    val focusPrefs: StateFlow<FocusPrefs> = settingsRepository.focus.stateIn(viewModelScope, SharingStarted.Eagerly, FocusPrefs())

    /** The list Home shows (null = all lists, within the focus mode). Activity-scoped. */
    private val _selectedList = MutableStateFlow<Long?>(null)
    val selectedList: StateFlow<Long?> = _selectedList.asStateFlow()
    fun selectList(id: Long?) { _selectedList.value = id }

    private val _searchFilter = MutableStateFlow(SearchFilter())
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()
    fun setSearchFilter(f: SearchFilter) { _searchFilter.value = f }

    fun focusAt(nowMillis: Long): FocusMode {
        val p = focusPrefs.value
        return Focus.effective(p.manual, p.manualAt, p.schedule, nowMillis, repository.zone())
    }
    fun setFocus(mode: FocusMode) {
        // Leaving the selected list's area resets the selection to "all lists".
        val sel = _selectedList.value?.let { id -> lists.value.firstOrNull { it.id == id } }
        if (sel != null && mode.area != null && sel.area != mode.area) _selectedList.value = null
        viewModelScope.launch { settingsRepository.setFocus(mode, repository.now()) }
    }
    fun setSchedule(s: FocusSchedule) = viewModelScope.launch { settingsRepository.setSchedule(s) }

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _undo = MutableStateFlow<UndoEvent?>(null)
    val undo: StateFlow<UndoEvent?> = _undo.asStateFlow()
    private var undoSeq = 0L

    /** Search asks Home to open a task's detail sheet. */
    private val _openRequest = MutableStateFlow<Long?>(null)
    val openRequest: StateFlow<Long?> = _openRequest.asStateFlow()

    fun add(title: String, dueDay: Long? = null) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.add(title, dueDay) }
    }

    /** Adds a task from the composer's natural-language parse. */
    fun add(parsed: QuickParse.Result, listHint: Long? = null) {
        if (parsed.title.isBlank()) return
        val hinted = listHint?.takeIf { id -> lists.value.any { it.id == id } }
        viewModelScope.launch {
            repository.add(
                NewTask(
                    title = parsed.title,
                    dueDay = parsed.dueDay?.toEpochDay(),
                    dueMinute = parsed.dueMinute,
                    priority = parsed.priority ?: Priority.None,
                    repeat = parsed.repeat,
                    listId = parsed.list?.id ?: hinted ?: defaultList(),
                    tags = parsed.tags,
                ),
            )
        }
    }

    /** Where a task without "#list" goes: the list in view, else the focus area's built-in, else Personal. */
    fun defaultList(nowMillis: Long = repository.now()): Long =
        _selectedList.value?.takeIf { id -> lists.value.any { it.id == id } }
            ?: when (focusAt(nowMillis)) { FocusMode.Work -> TaskList.WORK; else -> TaskList.PERSONAL }

    // ---- v0.7: lists, tags, subtasks, order, templates -----------------------------------
    fun setList(id: Long, listId: Long) = viewModelScope.launch { repository.setList(id, listId) }
    fun setTags(id: Long, tags: List<String>) = viewModelScope.launch { repository.setTags(id, tags) }
    fun addSubtask(taskId: Long, title: String) = viewModelScope.launch { repository.addSubtask(taskId, title) }
    fun setSubtaskDone(id: Long, done: Boolean) = viewModelScope.launch { repository.setSubtaskDone(id, done) }
    fun deleteSubtask(id: Long) = viewModelScope.launch { repository.deleteSubtask(id) }
    fun orderSubtasks(ids: List<Long>) = viewModelScope.launch { repository.orderSubtasks(ids) }
    fun createList(name: String, color: Int, emoji: String?, area: ListArea) =
        viewModelScope.launch { repository.createList(name, color, emoji, area) }
    fun updateList(list: TaskList) = viewModelScope.launch { repository.updateList(list) }
    fun orderLists(ids: List<Long>) = viewModelScope.launch { repository.orderLists(ids) }
    fun deleteList(id: Long, moveTo: Long) {
        if (_selectedList.value == id) _selectedList.value = null
        viewModelScope.launch { repository.deleteList(id, moveTo) }
    }

    /** Applies a finished drag on the tap frame (optimistic), then writes it. */
    fun applyDrop(outcome: DropOutcome, movedId: Long) {
        if (outcome.isEmpty) return
        val all = tasks.value.orEmpty().associateBy { it.id }
        orderOverrides.update { cur ->
            val next = cur.toMutableMap()
            for ((id, key) in outcome.keys) next[id] = OrderOverride(key, all[id]?.dueDay)
            if (outcome.newDay != null) {
                val key = outcome.keys[movedId] ?: all[movedId]?.sortOrder ?: 0L
                next[movedId] = OrderOverride(key, outcome.newDay)
            }
            next
        }
        val mine = orderOverrides.value.filterKeys { it in outcome.keys || it == movedId }
        viewModelScope.launch {
            repository.reorder(outcome.keys, if (outcome.newDay != null) mapOf(movedId to outcome.newDay) else emptyMap())
            // Drop the overrides once Room shows the same values (never mask later edits).
            repository.tasks.first { list -> orderMatches(list, mine) }
            orderOverrides.update { cur -> cur.filter { (id, o) -> mine[id] != o } }
        }
    }

    fun addTemplate(name: String, items: List<TemplateItem>) = viewModelScope.launch { repository.addTemplate(name, items) }
    fun deleteTemplate(id: Long) = viewModelScope.launch { repository.deleteTemplate(id) }

    /** "Save as template" from the panel: a one-task template named after the task. */
    fun saveAsTemplate(task: Task, today: LocalDate) {
        val subs = subtasks.value[task.id].orEmpty().map { it.title }
        val item = Templates.fromTask(task.title, task.listId, task.dueDay, task.dueMinute, Priority.fromCode(task.priorityCode), task.tagList, subs, today)
        addTemplate(task.title, listOf(item))
    }

    /** Inserts a template's tasks; [onInserted] gets their ids (for the staggered entrance). */
    fun useTemplate(t: Template, today: LocalDate, onInserted: (List<Long>) -> Unit = {}) {
        viewModelScope.launch { onInserted(repository.useTemplate(t, today, defaultList())) }
    }

    fun setDueAt(id: Long, dueDay: Long?, dueMinute: Int?) {
        viewModelScope.launch { repository.setDueAt(id, dueDay, dueMinute) }
    }

    fun setRepeat(id: Long, rule: RepeatRule?) {
        viewModelScope.launch { repository.setRepeat(id, rule) }
    }

    // ---- v0.13 ----
    fun setRepeatPaused(id: Long, paused: Boolean) = viewModelScope.launch { repository.setRepeatPaused(id, paused) }
    fun stopRepeat(id: Long) = viewModelScope.launch { repository.stopRepeat(id) }
    fun moveToDay(id: Long, day: java.time.LocalDate) = viewModelScope.launch { repository.moveToDay(id, day) }
    fun setRemindBefore(id: Long, minutes: Int) = viewModelScope.launch { repository.setRemindBefore(id, minutes) }

    /** Adds a task from the composer with toolbar picks merged in ([ComposerDraft]). */
    fun add(draft: dev.saikrishna.todo.domain.ComposerDraft.Draft, listHint: Long? = null) {
        if (draft.title.isBlank()) return
        val hinted = listHint?.takeIf { id -> lists.value.any { it.id == id } }
        val listOk = draft.listId?.takeIf { id -> lists.value.any { it.id == id } }
        viewModelScope.launch { repository.add(draft.copy(listId = listOk).toNewTask(hinted ?: defaultList())) }
    }

    val askedNotifications: StateFlow<Boolean> = settingsRepository.askedNotifications
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun markAskedNotifications() = viewModelScope.launch { settingsRepository.markAskedNotifications() }

    /**
     * Completing a task with open subtasks completes them all, without asking; the undo bar
     * offers to put everything back (v0.7).
     */
    fun setDone(id: Long, done: Boolean) {
        val at = repository.now()
        overrides.update { it + (id to DoneOverride(done, at)) }
        val task = tasks.value?.firstOrNull { it.id == id }
        val open = if (done) subtasks.value[id].orEmpty().count { !it.done } else 0
        viewModelScope.launch {
            val c = repository.setDone(id, done, at)
            if (open > 0 && task != null) {
                _undo.value = UndoEvent(
                    ++undoSeq,
                    if (open == 1) "Completed with 1 subtask" else "Completed with $open subtasks",
                    listOf(task), completion = c,
                )
            }
        }
    }

    fun setText(id: Long, title: String, notes: String) {
        viewModelScope.launch { repository.setText(id, title, notes) }
    }

    fun setDue(id: Long, dueDay: Long?) {
        viewModelScope.launch { repository.setDue(id, dueDay) }
    }

    fun setPriority(id: Long, priority: Priority) {
        viewModelScope.launch { repository.setPriority(id, priority) }
    }

    fun delete(task: Task) = deleteAll(listOf(task), "Task deleted")

    fun clearDone(done: List<Task>) {
        if (done.isEmpty()) return
        deleteAll(done, if (done.size == 1) "Cleared 1 task" else "Cleared ${done.size} tasks")
    }

    private fun deleteAll(list: List<Task>, message: String) {
        val ids = list.map { it.id }
        hidden.update { it + ids }
        val subs = list.flatMap { subtasks.value[it.id].orEmpty() }
        _undo.value = UndoEvent(++undoSeq, message, list, subtasks = subs)
        viewModelScope.launch { deleteLock.withLock { repository.delete(ids) } }
    }

    /** Re-inserts the event's tasks with their ids. Returns the ids so the UI can re-arm entrances. */
    fun undo(event: UndoEvent): List<Long> {
        val ids = event.tasks.map { it.id }
        if (_undo.value?.id == event.id) _undo.value = null
        val c = event.completion
        if (c != null) {
            val t = event.tasks.first()
            overrides.update { it + (t.id to DoneOverride(false, repository.now())) }
            viewModelScope.launch { repository.undoCompletion(t.id, c, t.repeatRule) }
            return emptyList()
        }
        viewModelScope.launch {
            deleteLock.withLock { repository.restore(event.tasks, event.subtasks) }
            hidden.update { it - ids.toSet() }
        }
        return ids
    }

    fun dismissUndo(event: UndoEvent) {
        if (_undo.value?.id == event.id) _undo.value = null
    }

    /** Whether the pending open request is for the snooze picker rather than the details. */
    var openAsSnooze: Boolean = false
        private set
    fun requestOpen(id: Long, snooze: Boolean = false) { openAsSnooze = snooze; _openRequest.value = id }
    fun consumeOpenRequest() { _openRequest.value = null }

    // ---- v0.8 morning planning ----
    val planPrefs: StateFlow<dev.saikrishna.todo.data.PlanPrefs> = settingsRepository.plan
        .stateIn(viewModelScope, SharingStarted.Eagerly, dev.saikrishna.todo.data.PlanPrefs())
    fun setPlan(prefs: dev.saikrishna.todo.data.PlanPrefs) = viewModelScope.launch { settingsRepository.setPlan(prefs) }
    private val _planRequest = MutableStateFlow(false)
    /** The planning notification (or a widget/shortcut) asks for the planning screen. */
    val planRequest: StateFlow<Boolean> = _planRequest.asStateFlow()
    fun requestPlan() { _planRequest.value = true }
    fun consumePlanRequest() { _planRequest.value = false }

    /** One planning decision: completion goes through [setDone] (subtasks, repeats, undo-safe). */
    fun applyPlan(change: dev.saikrishna.todo.domain.PlanChange) {
        if (change.done) { setDone(change.id, true); return }
        val day = change.newDay ?: return
        viewModelScope.launch { repository.setDueKeepTime(change.id, day) }
    }

    fun setReduceMotion(on: Boolean) = viewModelScope.launch { settingsRepository.setReduceMotion(on) }
    fun setHaptics(on: Boolean) = viewModelScope.launch { settingsRepository.setHaptics(on) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(mode) }
    fun setOpenOn(page: dev.saikrishna.todo.data.StartPage) = viewModelScope.launch { settingsRepository.setOpenOn(page) }
    fun setGreetingName(name: String) = viewModelScope.launch { settingsRepository.setGreetingName(name) }

    companion object {
        fun factory(repository: TaskRepository, settings: SettingsRepository) = viewModelFactory {
            initializer { TodoViewModel(repository, settings) }
        }
    }
}
