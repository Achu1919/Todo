package dev.saikrishna.todo.widget

import dev.saikrishna.todo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetDataTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val td = today.toEpochDay()

    @Test fun showsTodayPageOpenTasksInManualOrderCappedAtSix() {
        val tasks = (1..9).map { Task(id = it.toLong(), title = "T$it", createdAt = it.toLong(), sortOrder = (10 - it).toLong()) } +
            Task(id = 20, title = "future", createdAt = 20, dueDay = td + 1) +
            Task(id = 21, title = "done", createdAt = 21, done = true)
        val d = WidgetData.build(tasks, today)
        assertEquals(listOf(9L, 8, 7, 6, 5, 4), d.rows.map { it.id })
        assertEquals(3, d.more)
        assertEquals(9, d.openToday)
    }

    @Test fun metaShowsOverdueAndTimes() {
        val d = WidgetData.build(listOf(
            Task(id = 1, title = "late", createdAt = 1, dueDay = td - 1, dueMinute = 600),
            Task(id = 2, title = "timed", createdAt = 2, dueDay = td, dueMinute = 18 * 60),
            Task(id = 3, title = "allday", createdAt = 3, dueDay = td),
        ), today)
        assertEquals("Overdue", d.rows[0].meta); assertTrue(d.rows[0].overdue)
        assertTrue(d.rows[1].meta!!.isNotBlank())
        assertEquals(null, d.rows[2].meta)
    }

    @Test fun emptyAndEqualityForDiffing() {
        assertEquals(WidgetData(emptyList(), 0, 0), WidgetData.build(emptyList(), today))
        val a = listOf(Task(id = 1, title = "x", createdAt = 1))
        assertEquals(WidgetData.build(a, today), WidgetData.build(a.map { it.copy(notes = "changed") }, today))
    }
}
