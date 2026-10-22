package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanLineTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val td = today.toEpochDay()
    private fun t(id: Long, due: Long?, done: Boolean = false) = Task(id = id, title = "T$id", createdAt = id, dueDay = due, done = done)

    @Test fun both() = assertEquals("2 overdue · 3 today", PlanLine.of(listOf(t(1, td - 1), t(2, td - 9), t(3, td), t(4, td), t(5, td), t(6, td + 1), t(7, null)), today))
    @Test fun onlyToday() = assertEquals("1 today", PlanLine.of(listOf(t(1, td), t(2, td + 3)), today))
    @Test fun onlyOverdue() = assertEquals("4 overdue", PlanLine.of((1L..4L).map { t(it, td - it) }, today))
    @Test fun doneTasksDoNotCount() = assertEquals("Nothing due today", PlanLine.of(listOf(t(1, td, done = true), t(2, td - 1, done = true), t(3, null)), today))
    @Test fun empty() = assertEquals("Nothing due today", PlanLine.of(emptyList(), today))
}
