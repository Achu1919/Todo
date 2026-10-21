package dev.saikrishna.todo.ui.home

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.reorderKeys

/** What a finished drag writes: new sort keys, and (Upcoming) a new day for the dragged task. */
@Immutable
data class DropOutcome(val keys: Map<Long, Long>, val newDay: Long?) {
    val isEmpty: Boolean get() = keys.isEmpty() && newDay == null
}

/** Pure: moves the item at [from] to [to] (indices into [items]). */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}

/**
 * Pure: the result of dropping [movedId] with the page now laid out as [items].
 * - Rows keep their sort keys as a set; they are handed out again in the new visual order
 *   ([reorderKeys]), so only rows on this page change, relative to other lists nothing moves.
 * - Upcoming: the day of the dragged row becomes the day header it now sits under (time kept);
 *   above the first header (nothing to belong to) it keeps its day.
 */
fun dropOutcome(items: List<ListItem>, movedId: Long, page: Filter): DropOutcome {
    val rows = items.filterIsInstance<ListItem.Row>().map { it.task }
    val moved = rows.firstOrNull { it.id == movedId } ?: return DropOutcome(emptyMap(), null)
    var newDay: Long? = null
    if (page == Filter.Upcoming) {
        val at = items.indexOfFirst { it is ListItem.Row && it.task.id == movedId }
        val header = items.subList(0, at).lastOrNull { it is ListItem.DayHeader } as ListItem.DayHeader?
        if (header != null && header.day != moved.dueDay) newDay = header.day
    }
    // Upcoming sorts by day first: order keys within the final (day, position) layout.
    val ordered: List<Task> = rows.map { if (it.id == movedId && newDay != null) it.copy(dueDay = newDay) else it }
    return DropOutcome(reorderKeys(ordered), newDay)
}

/** Which items can be dragged / dropped on, per page. Done is not reorderable. */
fun reorderable(page: Filter, item: ListItem): Boolean = when (page) {
    Filter.Today -> item is ListItem.Row
    Filter.Upcoming -> item is ListItem.Row || item is ListItem.DayHeader
    Filter.Done -> false
}
