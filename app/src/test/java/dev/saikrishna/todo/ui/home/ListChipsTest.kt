package dev.saikrishna.todo.ui.home

import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.OverviewStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** v0.12 list chips: options + counts, per-page filtering (headers, Clear done, empty states). */
class ListChipsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 23)
    private val td = today.toEpochDay()
    private fun t(id: Long, list: Long, due: Long? = null, done: Boolean = false) =
        Task(id = id, title = "t$id", createdAt = id, listId = list, dueDay = due, done = done,
            completedAt = if (done) today.atTime(9, 0).toInstant(zone).toEpochMilli() + id else null)
    private val lists = listOf(
        TaskList(1, "Personal", color = 2, position = 0),
        TaskList(2, "Work", color = 0, emoji = "💼", position = 1),
        TaskList(3, "Errands", color = 4, position = 2),
    )
    private val tasks = listOf(
        t(1, 1), t(2, 2), t(3, 2, due = td - 1),
        t(4, 1, due = td + 1), t(5, 2, due = td + 1), t(6, 2, due = td + 3),
        t(7, 1, done = true), t(8, 2, done = true), t(9, 2, done = true),
    )
    private val pages = buildPages(tasks, today, zone, locale = Locale.UK)
    private fun rows(items: List<ListItem>) = items.filterIsInstance<ListItem.Row>().map { it.task.id }

    @Test fun optionsAreAllPlusListsWithRowsInOrder() {
        val o = chipOptions(pages.today, lists.reversed(), selected = null)
        assertEquals(listOf(null, 1L, 2L), o.map { it.id })
        assertEquals(listOf(3, 1, 2), o.map { it.count })
        assertEquals("💼 Work", o[2].label)
        assertEquals(0, o[2].color)
        // An empty list stays while it is the choice (so the chip doesn't vanish under the finger).
        assertEquals(listOf(null, 1L, 2L, 3L), chipOptions(pages.today, lists, selected = 3).map { it.id })
    }

    @Test fun countsPerPage() {
        assertEquals(mapOf(1L to 1, 2L to 2), countsByList(pages.today))
        assertEquals(mapOf(1L to 1, 2L to 2), countsByList(pages.upcoming))
        assertEquals(mapOf(1L to 1, 2L to 2), countsByList(pages.done))
    }

    @Test fun allIsThePageUnchanged() {
        assertTrue(filterByList(pages.upcoming, Filter.Upcoming, null) === pages.upcoming)
    }

    @Test fun filteringKeepsOnlyThatListAndItsHeaders() {
        val up = filterByList(pages.upcoming, Filter.Upcoming, 1)
        assertEquals(listOf(4L), rows(up))
        assertEquals(1, up.count { it is ListItem.DayHeader }) // the +3 day header went with its row
        val work = filterByList(pages.upcoming, Filter.Upcoming, 2)
        assertEquals(listOf(5L, 6L), rows(work))
        assertEquals(2, work.count { it is ListItem.DayHeader })
        assertEquals(listOf(2L, 3L).sorted(), rows(filterByList(pages.today, Filter.Today, 2)).sorted())
    }

    @Test fun clearDoneCountsTheFilteredRows() {
        val d = filterByList(pages.done, Filter.Done, 2)
        assertEquals(listOf(9L, 8L).sorted(), rows(d).sorted())
        val clear = d.filterIsInstance<ListItem.ClearDone>().singleOrNull()
        if (pages.done.any { it is ListItem.ClearDone }) assertEquals(2, clear?.count)
    }

    @Test fun emptyResultIsAPageSpecificMessage() {
        val e = filterByList(pages.today, Filter.Today, 3, "Errands").single() as ListItem.Empty
        assertEquals("Nothing in Errands today.", e.title)
        assertEquals("Nothing upcoming in Errands.", (filterByList(pages.upcoming, Filter.Upcoming, 3, "Errands").single() as ListItem.Empty).title)
        assertEquals("Nothing done in this list.", (filterByList(pages.done, Filter.Done, 3).single() as ListItem.Empty).title)
    }

    @Test fun overviewDateLineSaysWhatIsLeft() {
        val now = today.atTime(10, 0).toInstant(zone).toEpochMilli()
        val withOpen = overviewDateLine(today, OverviewStats.of(tasks, lists, now, zone))
        assertTrue(withOpen, withOpen.endsWith(" · 3 to do"))
        val allDone = overviewDateLine(today, OverviewStats.of(listOf(t(7, 1, done = true)), lists, now, zone))
        assertTrue(allDone, allDone.endsWith(" · all done"))
        assertTrue(!overviewDateLine(today, OverviewStats.of(emptyList(), lists, now, zone)).contains("·"))
    }
}
