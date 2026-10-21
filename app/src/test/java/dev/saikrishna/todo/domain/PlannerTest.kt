package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val td = today.toEpochDay()
    private fun t(id: Long, due: Long? = null, minute: Int? = null, done: Boolean = false, created: Long = id, sort: Long = id) =
        Task(id = id, title = "T$id", createdAt = created, dueDay = due, dueMinute = minute, done = done, sortOrder = sort)

    private val tasks = listOf(
        t(1, due = td - 1), t(2, due = td - 5), t(3, due = td, minute = 900), t(4, due = td),
        t(5, due = td, minute = 540), t(6, due = td + 1), t(7, due = td + 3), t(8, due = td + 2),
        t(9), t(10, created = 1), t(11, due = td - 3, done = true), t(12, due = td + 30),
    )

    @Test fun queueOrderAndSections() {
        val q = Planner.queue(tasks, today)
        assertEquals(listOf(2L, 1L, 5L, 3L, 4L, 6L, 8L, 10L), q.map { it.task.id })
        assertEquals(listOf(PlanSection.Overdue, PlanSection.Overdue, PlanSection.Today, PlanSection.Today, PlanSection.Today, PlanSection.Suggested, PlanSection.Suggested, PlanSection.Suggested), q.map { it.section })
        assertEquals(5, Planner.queue(tasks, today, suggestions = 0).size)
        assertEquals(emptyList<PlanItem>(), Planner.queue(emptyList(), today))
    }

    @Test fun actions() {
        val overdue = tasks[0]; val due = tasks[3]; val undated = tasks[8]
        assertEquals(PlanChange(1, td, false), Planner.apply(overdue, PlanAction.Keep, today))
        assertEquals(PlanChange(4, null, false), Planner.apply(due, PlanAction.Keep, today)) // already today: no write
        assertEquals(PlanChange(9, td, false), Planner.apply(undated, PlanAction.Keep, today))
        assertEquals(PlanChange(4, td + 1, false), Planner.apply(due, PlanAction.Tomorrow, today))
        assertEquals(PlanChange(1, td + 10, false), Planner.apply(overdue, PlanAction.PickDate, today, today.plusDays(10)))
        assertEquals(PlanChange(9, null, true), Planner.apply(undated, PlanAction.Done, today))
    }

    @Test fun summary() {
        val q = Planner.queue(tasks, today)
        val s = Planner.summary(
            q,
            mapOf(2L to PlanAction.Keep, 1L to PlanAction.Tomorrow, 5L to PlanAction.Done, 6L to PlanAction.Keep, 8L to PlanAction.PickDate, 10L to PlanAction.PickDate),
            today,
            picks = mapOf(8L to today.plusDays(5), 10L to today),
        )
        // today: kept 2, 6; picked-for-today 10; untouched today items 3, 4 -> 5
        assertEquals(PlanSummary(kept = 2, tomorrow = 1, picked = 2, done = 1, todayTotal = 5), s)
        assertEquals("5 for today · 3 moved · 1 done", s.line)
    }
}
