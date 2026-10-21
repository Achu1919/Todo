package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ViewFilterTest {
    private val today = LocalDate.of(2026, 10, 21) // Wednesday
    private val td = today.toEpochDay()
    private fun t(id: Long, title: String, due: Long? = null, minute: Int? = null, done: Boolean = false, list: Long = 1, tags: String = "", prio: Int = 0, rule: String? = null) =
        Task(id = id, title = title, createdAt = id, dueDay = due, dueMinute = minute, done = done, completedAt = if (done) 1000 + id else null, listId = list, tags = tags, priorityCode = prio, repeatRule = rule)

    private val tasks = listOf(
        t(1, "Pay rent", due = td - 2, prio = 2, tags = "bills"),
        t(2, "Standup", due = td, minute = 570, rule = "WD", list = 2),
        t(3, "Buy milk"),
        t(4, "Dentist", due = td + 4, minute = 960), // Sunday
        t(5, "Trip", due = td + 5), // next Monday
        t(6, "Old bill", due = td - 1, done = true, tags = "bills"),
        t(7, "Gym", due = td, prio = 1),
    )
    private fun ids(f: ViewFilter, q: String = "") = filterTasks(tasks, q, f, today).map { it.id }

    @Test fun dueFilters() {
        assertEquals(listOf(1L), ids(ViewFilter(due = DueFilter.Overdue))) // done 6 is not overdue
        assertEquals(listOf(2L, 7L), ids(ViewFilter(due = DueFilter.Today)))
        assertEquals(listOf(2L, 4L, 7L), ids(ViewFilter(due = DueFilter.ThisWeek)))
        assertEquals(listOf(3L), ids(ViewFilter(due = DueFilter.NoDate)))
    }

    @Test fun statusPriorityListReminderRecurring() {
        assertEquals(listOf(6L), ids(ViewFilter(status = StatusFilter.Done)))
        assertEquals(6, ids(ViewFilter(status = StatusFilter.Open)).size)
        assertEquals(listOf(1L), ids(ViewFilter(priority = Priority.High)))
        assertEquals(listOf(2L), ids(ViewFilter(listId = 2)))
        assertEquals(listOf(2L, 4L), ids(ViewFilter(reminder = true)))
        assertEquals(listOf(2L), ids(ViewFilter(recurring = true)))
    }

    @Test fun combinesWithTextAndEachOther() {
        assertEquals(listOf(1L, 6L), ids(ViewFilter(tag = "bills")))
        assertEquals(listOf(1L), ids(ViewFilter(tag = "bills", status = StatusFilter.Open)))
        assertEquals(listOf(6L), ids(ViewFilter(tag = "bills"), "old"))
        assertEquals(listOf(4L), ids(ViewFilter(due = DueFilter.ThisWeek, reminder = true, listId = 1)))
        assertEquals(listOf(1L, 6L), ids(ViewFilter(), "#bills"))
        assertTrue(ids(ViewFilter()).isEmpty())
    }

    @Test fun codecRoundTripsAndIsLenient() {
        val f = ViewFilter(listId = 5, tag = "home", priority = Priority.Low, due = DueFilter.ThisWeek, status = StatusFilter.Open, reminder = true, recurring = true)
        assertEquals("list=5;tag=home;prio=1;due=ThisWeek;status=Open;rem=1;rep=1", f.encode())
        assertEquals(f, ViewFilter.decode(f.encode()))
        assertEquals(ViewFilter(), ViewFilter.decode(""))
        assertEquals(ViewFilter(tag = "x"), ViewFilter.decode("bogus;tag=x;due=Someday;prio=9;list=abc"))
        assertEquals(7, f.count)
    }
}
