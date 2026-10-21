package dev.saikrishna.todo.ui.home

import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class PagesTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 20) // Tuesday
    private val day = today.toEpochDay()
    private fun millis(d: LocalDate, hour: Int = 12) = d.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun task(id: Long, due: Long? = null, done: Boolean = false, completed: LocalDate? = null, created: Long = id, notes: String = "", priority: Priority = Priority.None) =
        Task(id = id, title = "t$id", notes = notes, done = done, createdAt = created,
            completedAt = completed?.let { millis(it) }, dueDay = due, priorityCode = priority.code)

    private fun rows(items: List<ListItem>) = items.filterIsInstance<ListItem.Row>().map { it.task.id }
    private fun build(tasks: List<Task>, leaving: Map<Long, LeaveEntry> = emptyMap()) =
        buildPages(tasks, today, zone, leaving, Locale.UK)

    @Test fun classification() {
        assertEquals(Filter.Today, pageOf(task(1), today))                  // no date
        assertEquals(Filter.Today, pageOf(task(2, due = day), today))       // due today
        assertEquals(Filter.Today, pageOf(task(3, due = day - 3), today))   // overdue
        assertEquals(Filter.Upcoming, pageOf(task(4, due = day + 1), today))
        assertEquals(Filter.Done, pageOf(task(5, due = day + 1, done = true, completed = today), today))
        assertEquals(Filter.Done, pageOf(task(6, done = true, completed = today), today))
    }

    @Test fun todayPageIsOpenTodayAndOverdueInCreationOrder() {
        val p = build(listOf(task(3, created = 30), task(1, due = day - 1, created = 10), task(2, due = day + 2), task(4, done = true, completed = today)))
        assertEquals(listOf(1L, 3L), rows(p.today))
        assertEquals(2, p.openToday)
        val overdue = p.today.filterIsInstance<ListItem.Row>().first().meta
        assertTrue(overdue.overdue)
        assertEquals("Overdue · Yesterday", overdue.dueLabel)
    }

    @Test fun upcomingGroupedByDayWithHeaders() {
        val p = build(listOf(task(1, due = day + 8), task(2, due = day + 1), task(3, due = day + 1), task(4, due = day + 3)))
        val shape = p.upcoming.map {
            when (it) {
                is ListItem.DayHeader -> "h:${it.label}"
                is ListItem.Row -> "r:${it.task.id}"
                else -> it.key
            }
        }
        assertEquals(listOf("h:Tomorrow", "r:2", "r:3", "h:Friday", "r:4", "h:Wed, 28 Oct", "r:1"), shape)
        // Upcoming rows don't repeat the date under the header.
        assertTrue(p.upcoming.filterIsInstance<ListItem.Row>().all { it.meta.dueLabel == null })
    }

    @Test fun doneGroupedByCompletionDayNewestFirstWithClearAction() {
        val p = build(listOf(
            task(1, done = true, completed = today.minusDays(1)),
            task(2, done = true, completed = today),
            task(3, done = true, completed = today.minusDays(9)),
            task(4),
        ))
        assertEquals(ListItem.ClearDone(3), p.done.first())
        val headers = p.done.filterIsInstance<ListItem.DayHeader>().map { it.label }
        assertEquals(listOf("Today", "Yesterday", "Sun, 11 Oct"), headers)
        assertEquals(listOf(2L, 1L, 3L), rows(p.done))
        assertEquals(3, p.doneCount)
    }

    @Test fun emptyStates() {
        val none = build(emptyList())
        assertEquals("Nothing yet.", (none.today.single() as ListItem.Empty).title)
        assertEquals("Nothing upcoming.", (none.upcoming.single() as ListItem.Empty).title)
        assertEquals("Nothing done yet.", (none.done.single() as ListItem.Empty).title)

        val allDone = build(listOf(task(1, done = true, completed = today), task(2, done = true, completed = today.minusDays(2))))
        assertEquals(ListItem.AllClear(doneToday = 1), allDone.today.single())
    }

    @Test fun leavingRowsStayOnTheirPageUntilCollapsed() {
        val completed = task(1, done = true, completed = today)
        val dwell = build(listOf(completed), mapOf(1L to LeaveEntry(Filter.Today, LeavePhase.Dwell)))
        assertEquals(listOf(1L), rows(dwell.today))
        assertEquals(0, dwell.doneCount) // not counted until the dwell ends
        assertEquals("Nothing done yet.", (dwell.done.single() as ListItem.Empty).title)

        val collapsing = build(listOf(completed), mapOf(1L to LeaveEntry(Filter.Today, LeavePhase.Collapsing)))
        assertEquals(listOf(1L), rows(collapsing.today))
        assertEquals(1, collapsing.doneCount)

        val reopened = task(2) // un-completed on the Done page
        val back = build(listOf(reopened), mapOf(2L to LeaveEntry(Filter.Done, LeavePhase.Dwell)))
        assertEquals(listOf(2L), rows(back.done))
        assertTrue(rows(back.today).isEmpty())
    }

    @Test fun dayLabels() {
        assertEquals("Today", dayLabel(today, today, Locale.UK))
        assertEquals("Tomorrow", dayLabel(today.plusDays(1), today, Locale.UK))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today, Locale.UK))
        assertEquals("Monday", dayLabel(today.plusDays(6), today, Locale.UK))
        assertEquals("Tue, 27 Oct", dayLabel(today.plusDays(7), today, Locale.UK))
        assertEquals("Fri, 1 Jan 2027", dayLabel(LocalDate.of(2027, 1, 1), today, Locale.UK))
    }

    @Test fun rowMetaSecondaryLine() {
        val m = rowMeta(task(1, due = day + 1, notes = "x", priority = Priority.High), today, showDue = true, Locale.UK)
        assertEquals(RowMeta("Tomorrow", false, Priority.High, true), m)
        assertFalse(m.isEmpty)
        assertTrue(rowMeta(task(2), today, showDue = true).isEmpty)
        // A done task is never "overdue".
        assertFalse(rowMeta(task(3, due = day - 2, done = true, completed = today), today, true, Locale.UK).overdue)
    }

    @Test fun overridesAndHiddenDeletes() {
        val tasks = listOf(task(1), task(2), task(3, done = true, completed = today))
        val out = applyOverrides(tasks, mapOf(1L to DoneOverride(true, 99L), 3L to DoneOverride(true, 5L)), hidden = setOf(2L))
        assertEquals(listOf(1L, 3L), out.map { it.id })
        assertTrue(out[0].done); assertEquals(99L, out[0].completedAt)
        assertEquals(millis(today), out[1].completedAt) // agreeing override leaves the row untouched
    }

    @Test fun dueChips() {
        assertEquals(null, dueDayFor(DueChoice.None, today))
        assertEquals(day, dueDayFor(DueChoice.Today, today))
        assertEquals(day + 1, dueDayFor(DueChoice.Tomorrow, today))
        assertEquals(LocalDate.of(2026, 10, 26).toEpochDay(), dueDayFor(DueChoice.NextWeek, today)) // next Monday
        assertEquals(DueChoice.NextWeek, dueChoiceOf(LocalDate.of(2026, 10, 26).toEpochDay(), today))
        assertEquals(DueChoice.Pick, dueChoiceOf(day + 40, today))
        assertEquals(DueChoice.None, dueChoiceOf(null, today))
    }

    @Test fun entranceTrackerBaselineAndForget() {
        val t = EntranceTracker()
        assertFalse(t.claimEntrance(9)) // before baseline nothing animates
        t.baseline(listOf(1, 2))
        assertFalse(t.claimEntrance(1))
        assertTrue(t.claimEntrance(3))
        assertFalse(t.claimEntrance(3))
        t.forget(listOf(1, 3)) // undo re-inserts: entrance plays again
        assertTrue(t.claimEntrance(1))
        assertTrue(t.claimEntrance(3))
    }
}
