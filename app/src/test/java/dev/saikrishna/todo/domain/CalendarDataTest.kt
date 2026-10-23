package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** v0.13 Calendar: repeat projection, day boundaries, overdue, dots, grids and drag reschedule. */
class CalendarDataTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 23)
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun t(id: Long, due: LocalDate?, rule: RepeatRule? = null, done: Boolean = false, list: Long = 1, minute: Int? = null, paused: Boolean = false, completedAt: Long? = null) =
        Task(id = id, title = "t$id", createdAt = id, dueDay = due?.toEpochDay(), repeatRule = rule?.encode(), done = done, listId = list, dueMinute = minute, repeatPaused = paused, completedAt = completedAt)
    private fun build(vararg ts: Task, from: LocalDate = d(10, 19), to: LocalDate = d(11, 1)) = CalendarData.build(ts.toList(), from, to, today, ist)

    @Test fun repeatsProjectFadedOccurrencesAfterTheRealOne() {
        val cal = build(t(1, d(10, 23), RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))))
        assertFalse(cal.getValue(d(10, 23)).entries.single().projected)
        assertTrue(cal.getValue(d(10, 26)).entries.single().projected)
        assertTrue(cal.getValue(d(10, 30)).entries.single().projected)
        assertEquals(setOf(d(10, 23), d(10, 26), d(10, 30)), cal.keys)
        // A window that starts later still projects inside it only.
        val later = build(t(1, d(10, 1), RepeatRule.Daily(1)), from = d(10, 30), to = d(10, 31))
        assertEquals(setOf(d(10, 30), d(10, 31)), later.keys)
        assertTrue(later.values.all { it.entries.single().projected })
    }

    @Test fun pausedAndAfterCompletionDoNotProject() {
        assertEquals(setOf(d(10, 23)), build(t(1, d(10, 23), RepeatRule.Daily(1), paused = true)).keys)
        assertEquals(setOf(d(10, 23)), build(t(2, d(10, 23), RepeatRule.AfterCompletion(3))).keys)
    }

    @Test fun doneTasksSitOnTheirDueDayOrTheirLocalCompletionDay() {
        val lateNight = LocalDateTime.of(2026, 10, 21, 23, 59).atZone(ist).toInstant().toEpochMilli()
        val pastMidnight = LocalDateTime.of(2026, 10, 22, 0, 1).atZone(ist).toInstant().toEpochMilli()
        val cal = build(
            t(1, null, done = true, completedAt = lateNight),
            t(2, null, done = true, completedAt = pastMidnight),
            t(3, d(10, 20), done = true, completedAt = pastMidnight),
        )
        assertEquals(1L, cal.getValue(d(10, 21)).entries.single().task.id)
        assertEquals(2L, cal.getValue(d(10, 22)).entries.single().task.id)
        assertEquals(3L, cal.getValue(d(10, 20)).entries.single().task.id)
        // Done tasks give no dots and never make a day overdue.
        assertTrue(cal.getValue(d(10, 20)).dotLists.isEmpty())
        assertFalse(cal.getValue(d(10, 20)).overdue)
        // Undated open tasks are not on the calendar.
        assertTrue(build(t(4, null)).isEmpty())
    }

    @Test fun overdueDotsAndOrder() {
        val cal = build(
            t(1, d(10, 21), list = 1), t(2, d(10, 21), list = 2), t(3, d(10, 21), list = 3), t(4, d(10, 21), list = 4, minute = 8 * 60),
            t(5, d(10, 23), list = 1), t(6, d(10, 23), done = true, list = 2, minute = 60), t(7, d(10, 23), list = 1, minute = 600),
        )
        val past = cal.getValue(d(10, 21))
        assertTrue(past.overdue)
        assertEquals(listOf(4L, 1L, 2L), past.dotLists) // timed first, max 3 distinct lists
        val now = cal.getValue(d(10, 23))
        assertFalse(now.overdue) // today is never overdue
        assertEquals(listOf(7L, 5L, 6L), now.entries.map { it.task.id }) // open timed, open untimed, done
    }

    @Test fun grids() {
        assertEquals(DayOfWeek.MONDAY, CalendarData.weekStart(today).dayOfWeek)
        assertEquals(d(10, 19), CalendarData.week(today).first())
        assertEquals(d(10, 25), CalendarData.week(today).last())
        val oct = CalendarData.monthGrid(YearMonth.of(2026, 10))
        assertEquals(d(9, 28), oct.first())
        assertEquals(d(11, 1), oct.last())
        assertEquals(35, oct.size)
        assertEquals(28, CalendarData.monthGrid(YearMonth.of(2027, 2)).size) // Feb 2027 starts on a Monday
        assertEquals(42, CalendarData.monthGrid(YearMonth.of(2026, 8)).size) // Aug 2026: 6 rows
    }

    @Test fun dragRescheduleKeepsTheTimeAndReanchorsTheRule() {
        val task = t(1, d(10, 23), minute = 9 * 60)
        assertEquals(d(10, 28).toEpochDay() to 9 * 60, CalendarData.reschedule(task, d(10, 28)))
        assertNull(CalendarData.reschedule(task, d(10, 23)))
        assertNull(CalendarData.reschedule(task.copy(done = true), d(10, 28)))
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.WEDNESDAY)), CalendarData.reanchor(RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY)), d(10, 28)))
        val two = RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        assertEquals(two, CalendarData.reanchor(two, d(10, 28)))
        assertEquals(RepeatRule.Monthly(28, 3), CalendarData.reanchor(RepeatRule.Monthly(23, 3), d(10, 28)))
        assertEquals(RepeatRule.Yearly(10, 28), CalendarData.reanchor(RepeatRule.Yearly(10, 23), d(10, 28)))
        assertEquals(RepeatRule.Daily(2), CalendarData.reanchor(RepeatRule.Daily(2), d(10, 28)))
        assertNull(CalendarData.reanchor(null, d(10, 28)))
    }
}
