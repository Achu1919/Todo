package dev.saikrishna.todo.ui.home

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.tagList
import dev.saikrishna.todo.data.priority
import dev.saikrishna.todo.domain.QuickParse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The pages behind the filter pill (spec 6.2; v0.12 adds Overview first). Order = slide direction. */
enum class Filter(val title: String) { Overview("Overview"), Today("Today"), Upcoming("Upcoming"), Done("Done") }

/** A row that changed state stays on its old page for a dwell, then collapses out (6.5, 6.9). */
enum class LeavePhase { Dwell, Collapsing }

@Immutable
data class LeaveEntry(val from: Filter, val phase: LeavePhase)

/** Secondary line of a row. Precomputed so rows only render it. */
@Immutable
data class RowMeta(
    val dueLabel: String?,
    val overdue: Boolean,
    val priority: Priority,
    val hasNotes: Boolean,
    /** v0.6: shows the small repeat glyph. */
    val repeats: Boolean = false,
    /** v0.7: tags (rows show 2 + "+N"), subtask progress "2/5", and the list when viewing all lists. */
    val tags: List<String> = emptyList(),
    val progress: String? = null,
    val listName: String? = null,
    val listColor: Int = 0,
    /** v0.12: list colour index for the row's accent bar and checkbox ring (null = one list only). */
    val accent: Int? = null,
) {
    val isEmpty: Boolean get() = dueLabel == null && priority == Priority.None && !hasNotes && !repeats &&
        tags.isEmpty() && progress == null && listName == null
}

/**
 * v0.7 organisation context for building pages: which lists are shown (null = all), list
 * metadata for the row badge, and subtasks per task for the progress label.
 */
@Immutable
data class OrgView(
    val lists: Map<Long, TaskList> = emptyMap(),
    val visible: Set<Long>? = null,
    val showList: Boolean = false,
    val subtasks: Map<Long, List<Subtask>> = emptyMap(),
) {
    fun shows(t: Task): Boolean = visible == null || t.listId in visible
}

/** One LazyColumn item. Keys are stable and unique within a page. */
@Immutable
sealed interface ListItem {
    val key: String
    val contentType: String

    @Immutable
    data class Row(val task: Task, val meta: RowMeta) : ListItem {
        override val key get() = rowKey(task.id)
        override val contentType get() = "task"
    }

    @Immutable
    data class DayHeader(val day: Long, val label: String) : ListItem {
        override val key get() = "h-$day"
        override val contentType get() = "header"
    }

    @Immutable
    data class AllClear(val doneToday: Int) : ListItem {
        override val key get() = "all-clear"
        override val contentType get() = "all-clear"
    }

    @Immutable
    data class Empty(val title: String, val body: String) : ListItem {
        override val key get() = "empty"
        override val contentType get() = "empty"
    }

    @Immutable
    data class ClearDone(val count: Int) : ListItem {
        override val key get() = "clear-done"
        override val contentType get() = "clear-done"
    }
}

fun rowKey(id: Long) = "r-$id"

@Immutable
data class Pages(
    val today: List<ListItem>,
    val upcoming: List<ListItem>,
    val done: List<ListItem>,
    val openToday: Int,
    val doneCount: Int,
) {
    operator fun get(filter: Filter): List<ListItem> = when (filter) {
        Filter.Overview -> emptyList()
        Filter.Today -> today
        Filter.Upcoming -> upcoming
        Filter.Done -> done
    }
}

/** Natural page of a task: Done if completed; Upcoming if open and due after today; else Today. */
fun pageOf(task: Task, today: LocalDate): Filter = when {
    task.done -> Filter.Done
    task.dueDay != null && task.dueDay > today.toEpochDay() -> Filter.Upcoming
    else -> Filter.Today
}

private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** "Today", "Tomorrow", "Yesterday", a weekday within the coming week, else "Thu, 22 Oct" (+ year if not this year). */
fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val diff = day.toEpochDay() - today.toEpochDay()
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff == -1L -> "Yesterday"
        diff in 2..6 -> day.format(DateTimeFormatter.ofPattern("EEEE", locale))
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
        else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))
    }
}

fun rowMeta(task: Task, today: LocalDate, showDue: Boolean, locale: Locale = Locale.getDefault(), org: OrgView? = null): RowMeta {
    val due = task.dueDay?.let(LocalDate::ofEpochDay)
    val overdue = !task.done && due != null && due.isBefore(today)
    val time = task.dueMinute?.takeIf { due != null }?.let(QuickParse::timeLabel)
    val day = if (!showDue || due == null) null
    else if (overdue) "Overdue · ${dayLabel(due, today, locale)}" else dayLabel(due, today, locale)
    val label = listOfNotNull(day, time).joinToString(" · ").ifEmpty { null }
    val subs = org?.subtasks?.get(task.id).orEmpty()
    val list = if (org?.showList == true) org.lists[task.listId] else null
    return RowMeta(
        label, overdue, task.priority, task.notes.isNotBlank(), repeats = task.repeatRule != null,
        tags = task.tagList,
        progress = if (subs.isEmpty()) null else "${subs.count { it.done }}/${subs.size}",
        listName = list?.let { l -> listOfNotNull(l.emoji, l.name).joinToString(" ") },
        listColor = list?.color ?: 0,
        accent = if ((org?.lists?.size ?: 0) > 1) org!!.lists[task.listId]?.color else null,
    )
}

/**
 * Pure: builds the three pages.
 * - Today: open, due today / overdue / no date, in creation order (new tasks at the bottom).
 * - Upcoming: open with a future due date, grouped by due day (ascending).
 * - Done: completed, grouped by completion day (newest first), with a "Clear done" action.
 * Rows mid-leave ([leaving]) stay on the page they are leaving from until they finish collapsing.
 */
fun buildPages(
    tasks: List<Task>,
    today: LocalDate,
    zone: ZoneId,
    leaving: Map<Long, LeaveEntry> = emptyMap(),
    locale: Locale = Locale.getDefault(),
    org: OrgView = OrgView(),
): Pages {
    fun page(t: Task): Filter = leaving[t.id]?.from ?: pageOf(t, today)
    val allTasks = tasks
    @Suppress("NAME_SHADOWING")
    val tasks = if (org.visible == null) tasks else tasks.filter(org::shows)

    val todayTasks = ArrayList<Task>()
    val upcomingTasks = ArrayList<Task>()
    val doneTasks = ArrayList<Task>()
    for (t in tasks) when (page(t)) {
        Filter.Overview -> Unit // never a task's page
        Filter.Today -> todayTasks += t
        Filter.Upcoming -> upcomingTasks += t
        Filter.Done -> doneTasks += t
    }

    val todayItems = ArrayList<ListItem>()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    if (todayTasks.isEmpty()) {
        if (tasks.isEmpty()) {
            todayItems += if (allTasks.isEmpty()) ListItem.Empty("Nothing yet.", "Add your first task below.")
            else ListItem.Empty("Nothing here.", "No tasks in this list yet.")
        }
        else todayItems += ListItem.AllClear(tasks.count { it.done && (it.completedAt ?: 0L) >= startOfToday })
    }
    // Manual order (v0.7): sort_order defaults to the creation time, so new tasks still land last.
    todayTasks.sortedWith(compareBy<Task> { it.sortOrder }.thenBy { it.id })
        .mapTo(todayItems) { ListItem.Row(it, rowMeta(it, today, showDue = true, locale, org)) }

    val upcomingItems = ArrayList<ListItem>()
    upcomingTasks
        .sortedWith(compareBy<Task> { it.dueDay ?: Long.MAX_VALUE }.thenBy { it.sortOrder }.thenBy { it.id })
        .groupBy { it.dueDay ?: today.toEpochDay() }
        .forEach { (day, group) ->
            upcomingItems += ListItem.DayHeader(day, dayLabel(LocalDate.ofEpochDay(day), today, locale))
            group.mapTo(upcomingItems) { ListItem.Row(it, rowMeta(it, today, showDue = false, locale, org)) }
        }
    if (upcomingItems.isEmpty()) {
        upcomingItems += ListItem.Empty("Nothing upcoming.", "Give a task a future date and it waits here.")
    }

    val doneItems = ArrayList<ListItem>()
    if (doneTasks.isNotEmpty()) doneItems += ListItem.ClearDone(doneTasks.count { it.done })
    doneTasks
        .sortedWith(compareByDescending<Task> { it.completedAt ?: it.createdAt }.thenByDescending { it.id })
        .groupBy { (it.completedAt ?: it.createdAt).toLocalDate(zone).toEpochDay() }
        .forEach { (day, group) ->
            doneItems += ListItem.DayHeader(day, dayLabel(LocalDate.ofEpochDay(day), today, locale))
            group.mapTo(doneItems) { ListItem.Row(it, rowMeta(it, today, showDue = true, locale, org)) }
        }
    if (doneItems.isEmpty()) doneItems += ListItem.Empty("Nothing done yet.", "Completed tasks collect here.")

    return Pages(
        today = todayItems,
        upcoming = upcomingItems,
        done = doneItems,
        openToday = tasks.count { !it.done && pageOf(it, today) == Filter.Today },
        doneCount = tasks.count { it.done && leaving[it.id]?.phase != LeavePhase.Dwell },
    )
}

/**
 * v0.12 list chips: one page narrowed to one list. Rows of other lists go (so the existing item
 * animations fade them out), day headers left without rows go, "Clear done" counts what is left,
 * and an empty result gets a page-specific empty message. [listId] null = the page unchanged.
 */
fun filterByList(items: List<ListItem>, page: Filter, listId: Long?, listName: String = ""): List<ListItem> {
    if (listId == null) return items
    val rows = items.filter { it is ListItem.Row && it.task.listId == listId }
    if (rows.isEmpty()) {
        val name = listName.ifBlank { "this list" }
        return listOf(
            when (page) {
                Filter.Upcoming -> ListItem.Empty("Nothing upcoming in $name.", "Tasks with a future date in this list wait here.")
                Filter.Done -> ListItem.Empty("Nothing done in $name.", "Completed tasks from this list collect here.")
                else -> ListItem.Empty("Nothing in $name today.", "All clear here. Tap All to see every list.")
            },
        )
    }
    val out = ArrayList<ListItem>(items.size)
    var header: ListItem.DayHeader? = null
    for (item in items) when (item) {
        is ListItem.DayHeader -> header = item
        is ListItem.Row -> if (item.task.listId == listId) {
            header?.let { out += it; header = null }
            out += item
        }
        is ListItem.ClearDone -> out += ListItem.ClearDone(rows.count { (it as ListItem.Row).task.done })
        is ListItem.AllClear, is ListItem.Empty -> Unit
    }
    return out
}

/** v0.12 list chips: rows per list on one page (open rows; Done counts its completed rows). */
fun countsByList(items: List<ListItem>): Map<Long, Int> =
    items.filterIsInstance<ListItem.Row>().groupingBy { it.task.listId }.eachCount()

/** Optimistic completion state applied over the database rows until Room catches up. */
@Immutable
data class DoneOverride(val done: Boolean, val at: Long)

/** Pure: applies overrides that still disagree with the database, and hides pending deletes. */
fun applyOverrides(tasks: List<Task>, overrides: Map<Long, DoneOverride>, hidden: Set<Long> = emptySet()): List<Task> {
    if (overrides.isEmpty() && hidden.isEmpty()) return tasks
    val out = ArrayList<Task>(tasks.size)
    for (t in tasks) {
        if (t.id in hidden) continue
        val o = overrides[t.id]
        out += if (o == null || o.done == t.done) t
        else t.copy(done = o.done, completedAt = if (o.done) o.at else t.completedAt)
    }
    return out
}

/** Quick due-date chips in the detail sheet. */
enum class DueChoice(val label: String) { None("No date"), Today("Today"), Tomorrow("Tomorrow"), NextWeek("Next week"), Pick("Pick…") }

/** Pure: epoch day for a chip ("Next week" = next Monday). null for None; Pick is handled by the picker. */
fun dueDayFor(choice: DueChoice, today: LocalDate): Long? = when (choice) {
    DueChoice.None -> null
    DueChoice.Today -> today.toEpochDay()
    DueChoice.Tomorrow -> today.plusDays(1).toEpochDay()
    DueChoice.NextWeek -> today.plusDays((8 - today.dayOfWeek.value).toLong()).toEpochDay()
    DueChoice.Pick -> null
}

/** Pure: which chip a stored due day corresponds to. */
fun dueChoiceOf(dueDay: Long?, today: LocalDate): DueChoice = when (dueDay) {
    null -> DueChoice.None
    dueDayFor(DueChoice.Today, today) -> DueChoice.Today
    dueDayFor(DueChoice.Tomorrow, today) -> DueChoice.Tomorrow
    dueDayFor(DueChoice.NextWeek, today) -> DueChoice.NextWeek
    else -> DueChoice.Pick
}

/** Pure: applies optimistic manual-order / day overrides (v0.7). */
fun applyOrder(tasks: List<Task>, order: Map<Long, OrderOverride>): List<Task> {
    if (order.isEmpty()) return tasks
    return tasks.map { t ->
        val o = order[t.id] ?: return@map t
        if (t.sortOrder == o.key && t.dueDay == o.day) t else t.copy(sortOrder = o.key, dueDay = o.day)
    }
}

/** Pure: whether the database rows already carry these overrides. */
fun orderMatches(tasks: List<Task>, order: Map<Long, OrderOverride>): Boolean {
    val byId = tasks.associateBy { it.id }
    return order.all { (id, o) -> byId[id]?.let { it.sortOrder == o.key && it.dueDay == o.day } ?: true }
}
