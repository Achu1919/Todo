package dev.saikrishna.todo.org

import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.reorderKeys
import dev.saikrishna.todo.ui.home.Filter
import dev.saikrishna.todo.ui.home.ListItem
import dev.saikrishna.todo.ui.home.OrgView
import dev.saikrishna.todo.ui.home.applyOrder
import dev.saikrishna.todo.ui.home.buildPages
import dev.saikrishna.todo.ui.home.dropOutcome
import dev.saikrishna.todo.ui.home.moved
import dev.saikrishna.todo.ui.home.OrderOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Pure drag logic: manual order keys and drag-to-day in Upcoming. */
class ReorderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 21)
    private val d = today.toEpochDay()
    private fun t(id: Long, created: Long, list: Long = TaskList.PERSONAL, due: Long? = null, minute: Int? = null) =
        Task(id = id, title = "T$id", createdAt = created, listId = list, dueDay = due, dueMinute = minute)

    private fun titles(items: List<ListItem>) = items.filterIsInstance<ListItem.Row>().map { it.task.title }

    @Test fun keysArePermutedAmongTheMovedRowsOnly() {
        val rows = listOf(t(1, 100), t(2, 200), t(3, 300))
        val keys = reorderKeys(listOf(rows[2], rows[0], rows[1])) // drag 3 to the top
        assertEquals(mapOf(3L to 100L, 1L to 200L, 2L to 300L), keys)
        assertTrue(reorderKeys(rows).isEmpty()) // unchanged order writes nothing
    }

    @Test fun duplicateKeysBecomeStrictlyIncreasing() {
        val rows = listOf(t(1, 100), t(2, 100), t(3, 100))
        val keys = reorderKeys(listOf(rows[2], rows[1], rows[0]))
        assertEquals(listOf(100L, 101L, 102L), listOf(keys[3] ?: 100L, keys[2] ?: 100L, keys[1] ?: 100L))
    }

    @Test fun todayDropPersistsThroughPages() {
        val tasks = listOf(t(1, 100), t(2, 200), t(3, 300), t(4, 400))
        val page = buildPages(tasks, today, zone).today
        val dragged = page.moved(page.indexOfFirst { it.key == "r-4" }, page.indexOfFirst { it.key == "r-2" }) // 4 above 2
        val out = dropOutcome(dragged, 4, Filter.Today)
        assertNull(out.newDay)
        val after = applyOrder(tasks, out.keys.mapValues { (id, k) -> OrderOverride(k, tasks.first { it.id == id }.dueDay) })
        assertEquals(listOf("T1", "T4", "T2", "T3"), titles(buildPages(after, today, zone).today))
        // Saved keys survive a rebuild from "the database" (copy the keys into the rows).
        val saved = tasks.map { it.copy(sortOrder = out.keys[it.id] ?: it.sortOrder) }
        assertEquals(listOf("T1", "T4", "T2", "T3"), titles(buildPages(saved, today, zone).today))
        // A new task still lands at the bottom.
        assertEquals("T5", titles(buildPages(saved + t(5, 500), today, zone).today).last())
    }

    @Test fun reorderInOneListKeepsTheOtherListsOrder() {
        val tasks = listOf(t(1, 100), t(2, 200, TaskList.WORK), t(3, 300), t(4, 400, TaskList.WORK), t(5, 500))
        val work = OrgView(visible = setOf(TaskList.WORK))
        val page = buildPages(tasks, today, zone, org = work).today
        assertEquals(listOf("T2", "T4"), titles(page))
        val out = dropOutcome(page.moved(page.indexOfFirst { it.key == "r-4" }, page.indexOfFirst { it.key == "r-2" }), 4, Filter.Today)
        assertEquals(setOf(2L, 4L), out.keys.keys) // only Work rows change
        val saved = tasks.map { it.copy(sortOrder = out.keys[it.id] ?: it.sortOrder) }
        assertEquals(listOf("T4", "T2"), titles(buildPages(saved, today, zone, org = work).today))
        assertEquals(listOf("T1", "T3", "T5"), titles(buildPages(saved, today, zone, org = OrgView(visible = setOf(TaskList.PERSONAL))).today))
    }

    @Test fun upcomingDragUnderAnotherDayHeaderReschedulesKeepingTheTime() {
        val tasks = listOf(
            t(1, 100, due = d + 1, minute = 9 * 60), t(2, 200, due = d + 1),
            t(3, 300, due = d + 3), t(4, 400, due = d + 3, minute = 17 * 60 + 30),
        )
        val page = buildPages(tasks, today, zone).upcoming
        // [H(d+1), T1, T2, H(d+3), T3, T4]: drag T1 down onto the d+3 header, then past T3.
        val i1 = page.indexOfFirst { it.key == "r-1" }
        val step1 = page.moved(i1, page.indexOfFirst { it.key == "h-${d + 3}" })
        val step2 = step1.moved(step1.indexOfFirst { it.key == "r-1" }, step1.indexOfFirst { it.key == "r-3" })
        val out = dropOutcome(step2, 1, Filter.Upcoming)
        assertEquals(d + 3, out.newDay)
        val saved = tasks.map { it.copy(sortOrder = out.keys[it.id] ?: it.sortOrder, dueDay = if (it.id == 1L) out.newDay else it.dueDay) }
        val rebuilt = buildPages(saved, today, zone).upcoming
        assertEquals(listOf("h-${d + 1}", "r-2", "h-${d + 3}", "r-3", "r-1", "r-4"), rebuilt.map { it.key })
        assertEquals(9 * 60, saved.first { it.id == 1L }.dueMinute) // time kept
    }

    @Test fun upcomingMoveUpIntoEarlierDay() {
        val tasks = listOf(t(1, 100, due = d + 1), t(2, 200, due = d + 2))
        val page = buildPages(tasks, today, zone).upcoming // H1, T1, H2, T2
        val moved = page.moved(page.indexOfFirst { it.key == "r-2" }, page.indexOfFirst { it.key == "h-${d + 2}" }) // onto its own header from below
        assertEquals(d + 1, dropOutcome(moved, 2, Filter.Upcoming).newDay)
    }

    @Test fun aboveTheFirstHeaderKeepsTheDay() {
        val tasks = listOf(t(1, 100, due = d + 1), t(2, 200, due = d + 1))
        val page = buildPages(tasks, today, zone).upcoming // H, T1, T2
        val moved = page.moved(page.indexOfFirst { it.key == "r-1" }, 0)
        assertNull(dropOutcome(moved, 1, Filter.Upcoming).newDay)
    }
}
