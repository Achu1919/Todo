package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** v0.12 Overview numbers: counts, week chart, streak, next up, per-list progress, day boundaries. */
class OverviewStatsTest {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 23) // Friday
    private val td = today.toEpochDay()
    private fun at(d: LocalDate, h: Int, m: Int = 0, z: ZoneId = zone) = d.atTime(h, m).atZone(z).toInstant().toEpochMilli()
    private val now = at(today, 10, 30)

    private fun open(id: Long, due: Long? = null, minute: Int? = null, list: Long = 1, prio: Priority = Priority.None) =
        Task(id = id, title = "t$id", createdAt = id, dueDay = due, dueMinute = minute, listId = list, priorityCode = prio.code)
    private fun done(id: Long, on: LocalDate, h: Int = 12, list: Long = 1) =
        Task(id = id, title = "d$id", createdAt = id, done = true, completedAt = at(on, h), listId = list)

    private val lists = listOf(
        TaskList(2, "Work", color = 0, position = 1),
        TaskList(1, "Personal", color = 2, position = 0),
        TaskList(3, "Errands", color = 4, position = 2),
    )

    @Test fun counts() {
        val s = OverviewStats.of(listOf(
            open(1), open(2, due = td), open(3, due = td - 2), open(4, due = td + 1), open(5, due = td + 9),
            done(6, today), done(7, today.minusDays(1)),
        ), lists, now, zone)
        assertEquals(1, s.todayDone)
        assertEquals(3, s.todayOpen)
        assertEquals(1, s.overdue)
        assertEquals(2, s.upcoming)
        assertEquals(4, s.todayTotal)
        assertEquals(0.25f, s.todayProgress, 0.0001f)
    }

    @Test fun emptyIsZeroes() {
        val s = OverviewStats.of(emptyList(), lists, now, zone)
        assertEquals(0f, s.todayProgress)
        assertEquals(List(7) { 0 }, s.week)
        assertEquals(0, s.streak); assertNull(s.bestDay); assertNull(s.nextUp); assertEquals(emptyList<ListProgress>(), s.lists)
        assertEquals(4, s.todayIndex) // Friday
    }

    @Test fun weekChartIsMondayToSundayOfThisWeek() {
        val monday = LocalDate.of(2026, 10, 19)
        val s = OverviewStats.of(listOf(
            done(1, monday), done(2, monday), done(3, monday.plusDays(2)), done(4, today),
            done(5, monday.minusDays(1)), // last Sunday: not this week
        ), lists, now, zone)
        assertEquals(listOf(2, 0, 1, 0, 1, 0, 0), s.week)
        assertEquals(4, s.doneThisWeek)
        assertEquals(DayOfWeek.MONDAY, s.bestDay)
    }

    @Test fun bestDayTieIsEarliest() {
        val s = OverviewStats.of(listOf(done(1, today.minusDays(1)), done(2, today)), lists, now, zone)
        assertEquals(DayOfWeek.THURSDAY, s.bestDay)
    }

    @Test fun streakEndsTodayOrYesterday() {
        val days = (0L..3L).map { today.minusDays(it) }
        assertEquals(4, OverviewStats.of(days.mapIndexed { i, d -> done(i + 1L, d) }, lists, now, zone).streak)
        // Nothing yet today: the streak through yesterday still stands.
        assertEquals(3, OverviewStats.of(days.drop(1).mapIndexed { i, d -> done(i + 1L, d) }, lists, now, zone).streak)
        // A gap breaks it.
        assertEquals(1, OverviewStats.of(listOf(done(1, today), done(2, today.minusDays(2))), lists, now, zone).streak)
        // Two days ago only: no streak.
        assertEquals(0, OverviewStats.of(listOf(done(1, today.minusDays(2))), lists, now, zone).streak)
        // Several completions on one day count once.
        assertEquals(2, OverviewStats.of(listOf(done(1, today), done(2, today, 9), done(3, today.minusDays(1))), lists, now, zone).streak)
    }

    @Test fun completionsCountOnTheirLocalDay() {
        // 23:45 IST on Thursday is 18:15 UTC Thursday; 00:15 IST Friday is 18:45 UTC Thursday.
        val lateThu = at(today.minusDays(1), 23, 45)
        val earlyFri = at(today, 0, 15)
        val tasks = listOf(
            Task(id = 1, title = "a", createdAt = 1, done = true, completedAt = lateThu),
            Task(id = 2, title = "b", createdAt = 2, done = true, completedAt = earlyFri),
        )
        val ist = OverviewStats.of(tasks, lists, now, zone)
        assertEquals(1, ist.todayDone)
        assertEquals(listOf(0, 0, 0, 1, 1, 0, 0), ist.week)
        assertEquals(2, ist.streak)
        // The same instants seen from UTC are both Thursday, and "now" (05:00 UTC Fri) makes that yesterday.
        val utc = OverviewStats.of(tasks, lists, now, ZoneOffset.UTC)
        assertEquals(0, utc.todayDone)
        assertEquals(listOf(0, 0, 0, 2, 0, 0, 0), utc.week)
        assertEquals(1, utc.streak)
    }

    @Test fun todayFollowsTheZoneAtMidnight() {
        // 23:59 vs 00:01 local: a task due "today" flips to overdue across midnight.
        val task = open(1, due = td)
        assertEquals(0, OverviewStats.of(listOf(task), lists, at(today, 23, 59), zone).overdue)
        assertEquals(1, OverviewStats.of(listOf(task), lists, at(today.plusDays(1), 0, 1), zone).overdue)
        // And an upcoming (tomorrow) task becomes a Today task.
        val tomorrow = open(2, due = td + 1)
        assertEquals(1, OverviewStats.of(listOf(tomorrow), lists, at(today, 23, 59), zone).upcoming)
        assertEquals(1, OverviewStats.of(listOf(tomorrow), lists, at(today.plusDays(1), 0, 1), zone).todayOpen)
    }

    @Test fun weekStartsOnMondayAcrossTheWeekBoundary() {
        val sunday = LocalDate.of(2026, 10, 25)
        val monday = sunday.plusDays(1)
        val tasks = listOf(done(1, sunday, 23))
        assertEquals(6, OverviewStats.of(tasks, lists, at(sunday, 23, 50), zone).week.indexOfFirst { it > 0 })
        val next = OverviewStats.of(tasks, lists, at(monday, 0, 10), zone)
        assertEquals(List(7) { 0 }, next.week) // a new week
        assertEquals(0, next.todayIndex)
        assertEquals(1, next.streak) // yesterday still counts
    }

    @Test fun nextUpPrefersTimedThenOverdueThenHighThenOrder() {
        val timedPast = open(1, due = td, minute = 9 * 60)       // 09:00, already passed at 10:30
        val timedSoon = open(2, due = td, minute = 11 * 60)
        val timedLater = open(3, due = td, minute = 15 * 60)
        val overdueOld = open(4, due = td - 3)
        val overdueNew = open(5, due = td - 1)
        val high = open(6, prio = Priority.High)
        val plain = open(7)
        val future = open(8, due = td + 1, minute = 600)
        var s = OverviewStats.of(listOf(plain, high, overdueNew, overdueOld, timedLater, timedSoon, timedPast, future), lists, now, zone)
        assertEquals(2L, s.nextUp?.id); assertEquals(660, s.nextAtMinute)
        s = OverviewStats.of(listOf(plain, high, overdueNew, overdueOld, timedPast, future), lists, now, zone)
        assertEquals(4L, s.nextUp?.id); assertNull(s.nextAtMinute)
        s = OverviewStats.of(listOf(plain, high, timedPast), lists, now, zone)
        assertEquals(6L, s.nextUp?.id)
        s = OverviewStats.of(listOf(plain, timedPast), lists, now, zone)
        assertEquals(1L, s.nextUp?.id) // manual order: id 1 sorts first
        assertNull(OverviewStats.of(listOf(future), lists, now, zone).nextUp) // upcoming never "next up"
    }

    @Test fun perListProgressInListOrderSkippingEmptyLists() {
        val s = OverviewStats.of(listOf(
            open(1, list = 2), done(2, today, list = 2), done(3, today, list = 2),
            open(4, list = 1), open(5, due = td + 3, list = 1),
        ), lists, now, zone)
        assertEquals(listOf(1L, 2L), s.lists.map { it.list.id }) // Personal (pos 0), Work; Errands empty
        assertEquals(ListProgress(lists[1], open = 2, done = 0), s.lists[0])
        assertEquals(2f / 3, s.lists[1].fraction, 0.0001f)
        assertEquals(3, s.lists[1].total)
    }

    @Test fun greetings() {
        assertEquals("Hello", OverviewStats.greeting(4))
        assertEquals("Good morning", OverviewStats.greeting(5))
        assertEquals("Good morning", OverviewStats.greeting(11))
        assertEquals("Good afternoon", OverviewStats.greeting(12))
        assertEquals("Good evening", OverviewStats.greeting(21))
        assertEquals("Hello", OverviewStats.greeting(22))
    }
}
