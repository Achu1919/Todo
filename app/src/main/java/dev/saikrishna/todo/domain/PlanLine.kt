package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Task
import java.time.LocalDate

/** v0.9 lists panel: the live one-line summary under "Plan your day" (pure). */
object PlanLine {
    fun of(tasks: List<Task>, today: LocalDate): String {
        val td = today.toEpochDay()
        val overdue = tasks.count { !it.done && it.dueDay != null && it.dueDay < td }
        val dueToday = tasks.count { !it.done && it.dueDay == td }
        return when {
            overdue == 0 && dueToday == 0 -> "Nothing due today"
            overdue == 0 -> "$dueToday today"
            dueToday == 0 -> "$overdue overdue"
            else -> "$overdue overdue · $dueToday today"
        }
    }
}
