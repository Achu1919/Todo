package dev.saikrishna.todo.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SnoozeTest {
    private fun labels(now: LocalDateTime) = Snooze.options(now).map { it.label }

    @Test fun morningHasAllFour() {
        val now = LocalDateTime.of(2026, 10, 21, 10, 2) // Wednesday
        val o = Snooze.options(now)
        assertEquals(listOf("Later today", "Tonight", "Tomorrow", "Next week"), o.map { it.label })
        assertEquals(13 * 60 + 5, o[0].minute) // 13:02 rounded up to 13:05
        assertEquals(LocalDate.of(2026, 10, 22), o[2].day)
        assertEquals(LocalDate.of(2026, 10, 26), o[3].day) // next Monday
        assertEquals(9 * 60, o[3].minute)
    }

    @Test fun eveningDropsTonight() {
        assertEquals(listOf("Later today", "Tomorrow", "Next week"), labels(LocalDateTime.of(2026, 10, 21, 19, 30)))
    }

    @Test fun afternoonListsTonightBeforeLater() {
        // 18:00 + 3 h = 21:00, after 8 pm: options stay in time order.
        assertEquals(listOf("Tonight", "Later today", "Tomorrow", "Next week"), labels(LocalDateTime.of(2026, 10, 21, 18, 0)))
    }

    @Test fun lateNightDropsLaterToday() {
        assertEquals(listOf("Tomorrow", "Next week"), labels(LocalDateTime.of(2026, 10, 21, 21, 30)))
    }

    @Test fun mondayNextWeekIsSevenDaysOut() {
        val o = Snooze.options(LocalDateTime.of(2026, 10, 26, 8, 0))
        assertEquals(LocalDate.of(2026, 11, 2), o.last().day)
    }

    @Test fun detailLabels() {
        val today = LocalDate.of(2026, 10, 21)
        assertEquals("4 pm", SnoozeOption("x", today, 16 * 60).detail(today))
        assertEquals("Tomorrow, 9 am", SnoozeOption("x", today.plusDays(1), 9 * 60).detail(today))
        assertEquals("Mon 26 Oct, 9 am", SnoozeOption("x", LocalDate.of(2026, 10, 26), 9 * 60).detail(today))
    }
}
