package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.tagList
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** v0.8 search filters. */
enum class DueFilter(val label: String) { Overdue("Overdue"), Today("Today"), ThisWeek("This week"), NoDate("No date") }

enum class StatusFilter(val label: String) { Any("Any"), Open("Open"), Done("Done") }

/**
 * A combinable filter set (all set conditions must hold). Saved views store it with [encode].
 * - due: Overdue = open and due before today; Today = due today; This week = due from today
 *   through Sunday (ISO week); No date = no due date.
 * - reminder = has a due time (that is when reminders fire); recurring = has a repeat rule.
 */
@Immutable
data class ViewFilter(
    val listId: Long? = null,
    val tag: String? = null,
    val priority: Priority? = null,
    val due: DueFilter? = null,
    val status: StatusFilter = StatusFilter.Any,
    val reminder: Boolean = false,
    val recurring: Boolean = false,
) {
    val isEmpty: Boolean get() = this == ViewFilter()
    val count: Int get() = listOfNotNull(listId, tag, priority, due).size + (if (status != StatusFilter.Any) 1 else 0) +
        (if (reminder) 1 else 0) + (if (recurring) 1 else 0)

    fun matches(t: Task, today: LocalDate): Boolean {
        if (listId != null && t.listId != listId) return false
        if (tag != null && tag !in t.tagList) return false
        if (priority != null && t.priorityCode != priority.code) return false
        when (status) {
            StatusFilter.Any -> Unit
            StatusFilter.Open -> if (t.done) return false
            StatusFilter.Done -> if (!t.done) return false
        }
        if (reminder && (t.dueDay == null || t.dueMinute == null)) return false
        if (recurring && t.repeatRule == null) return false
        val d = t.dueDay
        val td = today.toEpochDay()
        when (due) {
            null -> Unit
            DueFilter.Overdue -> if (t.done || d == null || d >= td) return false
            DueFilter.Today -> if (d != td) return false
            DueFilter.ThisWeek -> {
                val end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toEpochDay()
                if (d == null || d < td || d > end) return false
            }
            DueFilter.NoDate -> if (d != null) return false
        }
        return true
    }

    /** "list=3;tag=home;prio=2;due=Today;status=Open;rem=1;rep=1" (only what is set). */
    fun encode(): String = buildList {
        listId?.let { add("list=$it") }
        tag?.let { add("tag=$it") }
        priority?.let { add("prio=${it.code}") }
        due?.let { add("due=${it.name}") }
        if (status != StatusFilter.Any) add("status=${status.name}")
        if (reminder) add("rem=1")
        if (recurring) add("rep=1")
    }.joinToString(";")

    companion object {
        /** Lenient: unknown keys / bad values are ignored, so an old or damaged row still opens. */
        fun decode(s: String): ViewFilter {
            var f = ViewFilter()
            for (part in s.split(';')) {
                val k = part.substringBefore('=', "").trim()
                val v = part.substringAfter('=', "").trim()
                f = when (k) {
                    "list" -> v.toLongOrNull()?.let { f.copy(listId = it) } ?: f
                    "tag" -> v.takeIf { it.isNotEmpty() }?.let { f.copy(tag = it) } ?: f
                    "prio" -> v.toIntOrNull()?.let { c -> Priority.entries.firstOrNull { it.code == c && it != Priority.None } }?.let { f.copy(priority = it) } ?: f
                    "due" -> DueFilter.entries.firstOrNull { it.name == v }?.let { f.copy(due = it) } ?: f
                    "status" -> StatusFilter.entries.firstOrNull { it.name == v }?.let { f.copy(status = it) } ?: f
                    "rem" -> f.copy(reminder = v == "1")
                    "rep" -> f.copy(recurring = v == "1")
                    else -> f
                }
            }
            return f
        }
    }
}

/**
 * Pure search: text (title, notes or tags, case-insensitive, a leading "#" ignored) combined with
 * [filter]. Open tasks first (oldest first), then done (newest first). An empty query with an
 * empty filter returns nothing (the hint shows instead).
 */
fun filterTasks(tasks: List<Task>, query: String, filter: ViewFilter, today: LocalDate): List<Task> {
    val q = query.trim().removePrefix("#")
    if (q.isEmpty() && filter.isEmpty) return emptyList()
    val hits = tasks.filter { t ->
        filter.matches(t, today) &&
            (q.isEmpty() || t.title.contains(q, ignoreCase = true) || t.notes.contains(q, ignoreCase = true) ||
                t.tagList.any { it.contains(q, ignoreCase = true) })
    }
    val (done, open) = hits.partition { it.done }
    return open.sortedWith(compareBy<Task> { it.createdAt }.thenBy { it.id }) +
        done.sortedByDescending { it.completedAt ?: it.createdAt }
}
