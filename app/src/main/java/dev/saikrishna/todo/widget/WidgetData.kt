package dev.saikrishna.todo.widget

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.domain.QuickParse
import java.time.LocalDate

/** v0.8 widget: what the Today widget shows (pure, so it is unit-tested and cheap to diff). */
@Immutable
data class WidgetRow(val id: Long, val title: String, val meta: String?, val overdue: Boolean)

@Immutable
data class WidgetData(val rows: List<WidgetRow>, val more: Int, val openToday: Int) {
    companion object {
        const val MaxRows = 6

        /**
         * The Today page's open tasks (due today, overdue or undated) in its order (manual sort
         * key), at most [max]; [more] counts the rest.
         */
        fun build(tasks: List<Task>, today: LocalDate, max: Int = MaxRows): WidgetData {
            val td = today.toEpochDay()
            val open = tasks.filter { !it.done && (it.dueDay == null || it.dueDay <= td) }
                .sortedWith(compareBy<Task> { it.sortOrder }.thenBy { it.id })
            val rows = open.take(max).map { t ->
                val overdue = t.dueDay != null && t.dueDay < td
                val meta = when {
                    overdue -> "Overdue"
                    t.dueMinute != null && t.dueDay == td -> QuickParse.timeLabel(t.dueMinute)
                    else -> null
                }
                WidgetRow(t.id, t.title, meta, overdue)
            }
            return WidgetData(rows, (open.size - rows.size).coerceAtLeast(0), open.size)
        }
    }
}
