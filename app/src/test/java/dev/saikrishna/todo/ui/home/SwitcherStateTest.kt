package dev.saikrishna.todo.ui.home

import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.domain.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** v0.13: the bottom-left view switcher, the header sections and the Recurring view. */
class SwitcherStateTest {
    @Test fun openPickFold() {
        val s = SwitcherState()
        assertNull(s.pick(Filter.Upcoming, Filter.Today)) // closed: ignored
        s.toggle()
        assertTrue(s.open)
        assertEquals(Filter.Upcoming, s.pick(Filter.Upcoming, Filter.Today))
        assertFalse(s.open)
        assertEquals(Filter.Upcoming, s.picked)
        s.toggle()
        assertNull(s.picked) // a new open forgets the last pick
        assertNull(s.pick(Filter.Today, Filter.Today)) // the current view just folds
        assertFalse(s.open)
        s.toggle(); s.toggle()
        assertFalse(s.open)
        s.toggle()
        assertNull(s.pick(Filter.Calendar, Filter.Today)) // not a Todo view
        assertTrue(s.open)
        s.close()
        assertFalse(s.open)
    }

    @Test fun staggerOpensBottomUpAndFoldsTopDown() {
        assertEquals(listOf(0L, 34L, 68L, 102L), (0..3).map(SwitcherState::openDelay))
        assertEquals(listOf(66L, 44L, 22L, 0L), (0..3).map { SwitcherState.closeDelay(it, 4) })
        assertTrue(SwitcherState.OpenStagger in 30..40)
    }

    @Test fun headerSections() {
        assertEquals(listOf("Home", "Todo", "Calendar"), Section.entries.map { it.title })
        assertEquals(Filter.Overview, Section.Home.target(Filter.Done))
        assertEquals(Filter.Calendar, Section.Calendar.target(Filter.Done))
        assertEquals(Filter.Recurring, Section.Todo.target(Filter.Recurring))
        assertEquals(Filter.Today, Section.Todo.target(Filter.Calendar))
        assertEquals(Section.Todo, Filter.Upcoming.section)
        assertEquals(Section.Home, Filter.Overview.section)
        assertEquals("Home", Filter.Overview.title)
        assertEquals(listOf(Filter.Today, Filter.Upcoming, Filter.Recurring, Filter.Done), TodoViews)
    }

    private val today = LocalDate.of(2026, 10, 23)
    private fun t(id: Long, rule: RepeatRule?, due: LocalDate? = today, paused: Boolean = false, done: Boolean = false) =
        Task(id = id, title = "t$id", createdAt = id, dueDay = due?.toEpochDay(), repeatRule = rule?.encode(), repeatPaused = paused, done = done)

    @Test fun recurringGroupsRowsAndLabels() {
        val items = buildRecurring(
            listOf(
                t(1, RepeatRule.Yearly(3, 14), due = LocalDate.of(2027, 3, 14)),
                t(2, RepeatRule.Daily(1), due = today.plusDays(1)),
                t(3, RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)), due = LocalDate.of(2026, 10, 26), paused = true),
                t(4, RepeatRule.Daily(2), due = today.minusDays(1)),
                t(5, null),
                t(6, RepeatRule.Daily(1), done = true),
                t(7, RepeatRule.Monthly(1, 3), due = LocalDate.of(2026, 11, 1)),
            ),
            today, java.util.Locale.UK,
        )
        val shape = items.map { if (it is ListItem.DayHeader) "#${it.label}" else (it as ListItem.Row).task.id.toString() }
        assertEquals(listOf("#Daily", "4", "2", "#Weekly", "3", "#Monthly", "7", "#Yearly", "1"), shape)
        val rows = items.filterIsInstance<ListItem.Row>().associateBy { it.task.id }
        assertEquals("Next · Tomorrow", rows.getValue(2).meta.dueLabel)
        assertEquals("Every day", rows.getValue(2).meta.repeatLabel)
        assertTrue(rows.getValue(4).meta.overdue)
        assertEquals("Paused", rows.getValue(3).meta.dueLabel)
        assertTrue(rows.getValue(3).meta.paused)
        // Group headers have negative keys, so they never clash with a real day header.
        assertTrue(items.filterIsInstance<ListItem.DayHeader>().all { it.day < 0 })
        assertTrue(buildRecurring(emptyList(), today).single() is ListItem.Empty)
        assertFalse(reorderable(Filter.Recurring, items[1]))
    }
}
