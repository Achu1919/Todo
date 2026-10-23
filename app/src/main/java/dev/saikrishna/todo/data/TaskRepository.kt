package dev.saikrishna.todo.data

import androidx.room.withTransaction
import dev.saikrishna.todo.domain.RepeatRule
import dev.saikrishna.todo.domain.Template
import dev.saikrishna.todo.domain.TemplateCodec
import dev.saikrishna.todo.domain.TemplateItem
import dev.saikrishna.todo.domain.Templates
import dev.saikrishna.todo.domain.nextOccurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything a new task can be created with (composer parse, templates). */
data class NewTask(
    val title: String,
    val dueDay: Long? = null,
    val dueMinute: Int? = null,
    val priority: Priority = Priority.None,
    val repeat: RepeatRule? = null,
    /** v0.7 */
    val listId: Long = TaskList.PERSONAL,
    val tags: List<String> = emptyList(),
    val subtasks: List<String> = emptyList(),
    /** v0.8: share-sheet capture puts the rest of the text / the link here. */
    val notes: String = "",
    /** v0.13 add-task toolbar: reminder this many minutes before the due time. */
    val remindBefore: Int = 0,
)

/** What completing a task did, so it can be undone exactly. */
data class Completion(
    /** Next occurrence created for a repeating task (deleted again on undo). */
    val spawnedId: Long? = null,
    /** Subtasks as they were before "complete all" (restored on undo). Empty = none were open. */
    val subtasksBefore: List<Subtask> = emptyList(),
)

class TaskRepository(
    private val dao: TaskDao,
    private val org: OrgDao? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneOf: () -> ZoneId = ZoneId::systemDefault,
    /** Runs [block] in one DB transaction (null in tests that use a bare DAO). */
    private val transaction: (suspend (suspend () -> Unit) -> Unit)? = null,
) {
    constructor(db: TodoDatabase, clock: () -> Long = System::currentTimeMillis, zone: () -> ZoneId = ZoneId::systemDefault) :
        this(db.taskDao(), db.orgDao(), clock, zone, { block -> db.withTransaction { block() } })

    val tasks: Flow<List<Task>> = dao.observeAll()
    val lists: Flow<List<TaskList>> = org?.observeLists() ?: flowOf(TaskList.Defaults)
    val subtasks: Flow<List<Subtask>> = org?.observeSubtasks() ?: flowOf(emptyList())
    val views: Flow<List<SavedView>> = org?.observeViews() ?: flowOf(emptyList())
    val templates: Flow<List<Template>> = org?.observeTemplates()?.map { l -> l.map(TemplateCodec::toTemplate) } ?: emptyFlow()

    suspend fun add(title: String, dueDay: Long? = null): Long = add(NewTask(title, dueDay))

    suspend fun add(new: NewTask): Long {
        var id = 0L
        inTransaction { id = insert(new, clock(), clock()) }
        return id
    }

    /** Template use: all tasks in one transaction, in order (consecutive sort keys). */
    suspend fun addAll(news: List<NewTask>): List<Long> {
        val ids = ArrayList<Long>()
        val now = clock()
        inTransaction { news.forEachIndexed { i, n -> if (n.title.isNotBlank()) ids += insert(n, now, now + i) } }
        return ids
    }

    private suspend fun insert(new: NewTask, now: Long, sortKey: Long): Long {
        val clean = new.title.trim()
        require(clean.isNotEmpty()) { "title must not be blank" }
        val id = dao.insert(
            Task(
                title = clean,
                notes = new.notes.trim(),
                createdAt = now,
                dueDay = new.dueDay,
                dueMinute = new.dueMinute?.takeIf { new.dueDay != null },
                priorityCode = new.priority.code,
                repeatRule = new.repeat?.encode(),
                listId = new.listId,
                tags = Tags.join(new.tags),
                sortOrder = sortKey,
                remindBefore = new.remindBefore.coerceIn(0, MaxRemindBefore),
            ),
        )
        val subs = new.subtasks.map { it.trim() }.filter { it.isNotEmpty() }
        if (subs.isNotEmpty()) org?.upsertSubtasks(subs.mapIndexed { i, t -> Subtask(taskId = id, title = t, position = i) })
        return id
    }

    /**
     * Completing a task also completes all its subtasks (v0.7; nothing is asked), and for a
     * repeating task creates the next occurrence (same title, notes, priority, time, rule, list,
     * tags; its subtasks copied UNCHECKED) and clears the rule on the completed copy, so completing
     * it again (after re-opening) never spawns a duplicate.
     */
    suspend fun setDone(id: Long, done: Boolean, at: Long = clock()): Completion {
        if (!done) { dao.markOpen(id); return Completion() }
        var result = Completion()
        inTransaction {
            val task = dao.get(id)
            val subs = org?.subtasksOf(id).orEmpty()
            dao.markDone(id, at)
            val before = if (subs.any { !it.done }) subs else emptyList()
            if (before.isNotEmpty()) org?.completeSubtasks(id)
            var spawned: Long? = null
            val rule = task?.activeRepeat // v0.13: a paused rule spawns nothing
            if (task != null && !task.done && rule != null) {
                val completedOn = Instant.ofEpochMilli(at).atZone(zone()).toLocalDate()
                val next = nextOccurrence(rule, task.dueDay?.let(LocalDate::ofEpochDay), completedOn)
                dao.setRepeat(id, null)
                val now = clock()
                spawned = dao.insert(
                    task.copy(
                        id = 0, done = false, completedAt = null, createdAt = now, sortOrder = now,
                        dueDay = next.toEpochDay(),
                    ),
                )
                if (subs.isNotEmpty()) org?.upsertSubtasks(copySubtasksForRepeat(subs, spawned))
            }
            result = Completion(spawned, before)
        }
        return result
    }

    /** Undo of a completion: re-open, put subtasks back as they were, remove the spawned occurrence. */
    suspend fun undoCompletion(id: Long, completion: Completion, rule: String?) {
        inTransaction {
            dao.markOpen(id)
            if (completion.subtasksBefore.isNotEmpty()) org?.upsertSubtasks(completion.subtasksBefore)
            completion.spawnedId?.let { s ->
                dao.deleteIds(listOf(s))
                org?.deleteSubtasksOf(listOf(s))
                if (rule != null) dao.setRepeat(id, rule)
            }
        }
    }

    private suspend fun inTransaction(block: suspend () -> Unit) {
        val tx = transaction
        if (tx == null) block() else tx(block)
    }

    suspend fun setText(id: Long, title: String, notes: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) dao.setText(id, clean, notes.trim())
    }

    suspend fun get(id: Long): Task? = dao.get(id)
    suspend fun timed(): List<Task> = dao.timed()
    suspend fun setDue(id: Long, dueDay: Long?) = dao.setDue(id, dueDay)
    suspend fun setDueAt(id: Long, dueDay: Long?, dueMinute: Int?) = dao.setDueAt(id, dueDay, dueMinute?.takeIf { dueDay != null })
    suspend fun setRepeat(id: Long, rule: RepeatRule?) = dao.setRepeat(id, rule?.encode())
    /** v0.13 Recurring view: pause / resume (the rule is kept). Stop = [setRepeat] null. */
    suspend fun setRepeatPaused(id: Long, paused: Boolean) = dao.setRepeatPaused(id, paused)
    /** v0.13 Recurring view "Stop repeating": drop the rule and any pause. */
    suspend fun stopRepeat(id: Long) = inTransaction { dao.setRepeat(id, null); dao.setRepeatPaused(id, false) }
    /**
     * v0.13 Calendar drag: move to [day] keeping the time; a repeating task's rule follows the move
     * ([CalendarData.reanchor]). Returns false when nothing changed.
     */
    suspend fun moveToDay(id: Long, day: java.time.LocalDate): Boolean {
        var changed = false
        inTransaction {
            val t = dao.get(id) ?: return@inTransaction
            val (newDay, _) = dev.saikrishna.todo.domain.CalendarData.reschedule(t, day) ?: return@inTransaction
            dao.setDueKeepTime(id, newDay)
            val rule = t.repeat
            val moved = dev.saikrishna.todo.domain.CalendarData.reanchor(rule, day)
            if (moved != rule) dao.setRepeat(id, moved?.encode())
            changed = true
        }
        return changed
    }
    suspend fun setRemindBefore(id: Long, minutes: Int) = dao.setRemindBefore(id, minutes.coerceIn(0, MaxRemindBefore))
    suspend fun setPriority(id: Long, priority: Priority) = dao.setPriority(id, priority.code)
    suspend fun setList(id: Long, listId: Long) = dao.setList(id, listId)
    suspend fun setTags(id: Long, tags: List<String>) = dao.setTags(id, Tags.join(tags))

    /** Deletes tasks and their subtasks. */
    suspend fun delete(ids: List<Long>) = inTransaction {
        dao.deleteIds(ids)
        org?.deleteSubtasksOf(ids)
    }

    suspend fun subtasksOf(ids: List<Long>): List<Subtask> = org?.subtasksOf(ids).orEmpty()

    /** Undo of a delete: tasks with their ids, and their subtasks. */
    suspend fun restore(tasks: List<Task>, subtasks: List<Subtask> = emptyList()) = inTransaction {
        dao.insertAll(tasks)
        if (subtasks.isNotEmpty()) org?.upsertSubtasks(subtasks)
    }

    /** Manual order (and, from Upcoming, a new day keeping the time), in one transaction. */
    suspend fun reorder(keys: Map<Long, Long>, newDays: Map<Long, Long> = emptyMap()) = inTransaction {
        for ((id, key) in keys) dao.setSortOrder(id, key)
        for ((id, day) in newDays) dao.setDueKeepTime(id, day)
    }

    // ---- subtasks ----
    suspend fun addSubtask(taskId: Long, title: String): Long? {
        val t = title.trim()
        val o = org ?: return null
        if (t.isEmpty()) return null
        val next = (o.subtasksOf(taskId).maxOfOrNull { it.position } ?: -1) + 1
        return o.insertSubtask(Subtask(taskId = taskId, title = t, position = next))
    }
    suspend fun setSubtaskDone(id: Long, done: Boolean) { org?.setSubtaskDone(id, done) }
    suspend fun renameSubtask(id: Long, title: String) { title.trim().takeIf { it.isNotEmpty() }?.let { org?.setSubtaskTitle(id, it) } }
    suspend fun deleteSubtask(id: Long) { org?.deleteSubtask(id) }
    suspend fun orderSubtasks(ids: List<Long>) = inTransaction { ids.forEachIndexed { i, id -> org?.setSubtaskPosition(id, i) } }

    // ---- lists ----
    suspend fun createList(name: String, color: Int, emoji: String?, area: ListArea): Long? {
        val o = org ?: return null
        val n = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val pos = (o.lists().maxOfOrNull { it.position } ?: -1) + 1
        return o.insertList(TaskList(name = n, color = color, emoji = emoji?.trim()?.ifEmpty { null }, position = pos, areaCode = area.code))
    }
    suspend fun updateList(list: TaskList) {
        val n = list.name.trim().ifEmpty { return }
        org?.updateList(list.id, n, list.color, list.emoji?.trim()?.ifEmpty { null }, list.areaCode)
    }
    suspend fun orderLists(ids: List<Long>) = inTransaction { ids.forEachIndexed { i, id -> org?.setListPosition(id, i) } }

    /** Deletes a user list after moving its tasks to [moveTo]. Built-in lists cannot be deleted. */
    suspend fun deleteList(id: Long, moveTo: Long) {
        if (id == TaskList.PERSONAL || id == TaskList.WORK || id == moveTo) return
        inTransaction {
            org?.moveTasks(id, moveTo)
            org?.deleteList(id)
        }
    }

    // ---- saved views (v0.8) ----
    suspend fun saveView(name: String, query: String, filter: String): Long? {
        val o = org ?: return null
        val n = name.trim().ifEmpty { return null }
        return o.insertView(SavedView(name = n.take(40), query = query.trim(), filter = filter, position = o.maxViewPosition() + 1))
    }
    suspend fun deleteView(id: Long) { org?.deleteView(id) }

    /** v0.8 planning: one task's new day (time of day kept), or completion. */
    suspend fun setDueKeepTime(id: Long, day: Long) = dao.setDueKeepTime(id, day)

    // ---- templates ----
    suspend fun addTemplate(name: String, items: List<TemplateItem>): Long? {
        val n = name.trim().ifEmpty { return null }
        if (items.isEmpty()) return null
        return org?.insertTemplate(TemplateEntity(name = n, body = TemplateCodec.encode(items), position = Int.MAX_VALUE / 2))
    }
    suspend fun deleteTemplate(id: Long) { org?.deleteTemplate(id) }

    /** Inserts a template's tasks for [today]; returns their ids in order. */
    suspend fun useTemplate(template: Template, today: LocalDate, fallbackList: Long): List<Long> {
        val known = org?.lists()?.map { it.id }?.toSet() ?: TaskList.Defaults.map { it.id }.toSet()
        return addAll(Templates.instantiate(template, today, fallbackList, known))
    }

    fun now(): Long = clock()
    fun zone(): ZoneId = zoneOf()
}

/** v0.13: the longest reminder lead (one week). */
const val MaxRemindBefore = 7 * 24 * 60

/** Pure: a repeating task's subtasks for its next occurrence: same titles and order, all unchecked. */
fun copySubtasksForRepeat(subs: List<Subtask>, newTaskId: Long): List<Subtask> =
    subs.sortedWith(compareBy<Subtask> { it.position }.thenBy { it.id })
        .mapIndexed { i, s -> Subtask(taskId = newTaskId, title = s.title, done = false, position = i) }

/**
 * Pure: manual reorder. [ordered] are the rows of one page/list in their NEW order; the multiset
 * of their current sort keys is handed out again in that order (strictly increasing), so only
 * these rows change and their order relative to rows not shown (other lists) is kept.
 * Returns only the keys that changed.
 */
fun reorderKeys(ordered: List<Task>): Map<Long, Long> {
    val keys = ordered.map { it.sortOrder }.sorted()
    val out = HashMap<Long, Long>()
    var prev = Long.MIN_VALUE
    ordered.forEachIndexed { i, t ->
        val k = if (prev == Long.MIN_VALUE) keys[i] else maxOf(keys[i], prev + 1)
        prev = k
        if (k != t.sortOrder) out[t.id] = k
    }
    return out
}
