package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.dueAtMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.ZoneId

class RepeatRuleTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun next(r: RepeatRule, due: LocalDate?, done: LocalDate) = nextOccurrence(r, due, done)

    @Test fun dailyOnTimeEarlyAndLate() {
        val r = RepeatRule.Daily(1)
        assertEquals(d(2026, 10, 22), next(r, d(2026, 10, 21), d(2026, 10, 21)))
        assertEquals(d(2026, 10, 22), next(r, d(2026, 10, 18), d(2026, 10, 21))) // 3 days late → tomorrow
        assertEquals(d(2026, 10, 26), next(r, d(2026, 10, 25), d(2026, 10, 21))) // early: after the due date
    }

    @Test fun everyNDaysKeepsPhase() {
        val r = RepeatRule.Daily(3)
        assertEquals(d(2026, 10, 24), next(r, d(2026, 10, 21), d(2026, 10, 21)))
        // Due 15th, done 21st: schedule 15, 18, 21, 24 → 24.
        assertEquals(d(2026, 10, 24), next(r, d(2026, 10, 15), d(2026, 10, 21)))
        assertEquals(d(2026, 10, 22), next(r, d(2026, 10, 16), d(2026, 10, 21)))
    }

    @Test fun weekdaysSkipWeekend() {
        val r = RepeatRule.Weekdays
        assertEquals(d(2026, 10, 26), next(r, d(2026, 10, 23), d(2026, 10, 23))) // Fri → Mon
        assertEquals(d(2026, 10, 22), next(r, d(2026, 10, 21), d(2026, 10, 21)))
        assertEquals(d(2026, 10, 26), next(r, d(2026, 10, 24), d(2026, 10, 24))) // Sat → Mon
    }

    @Test fun weeklyOnSeveralDays() {
        val r = RepeatRule.Weekly(setOf(MONDAY, THURSDAY))
        assertEquals(d(2026, 10, 22), next(r, d(2026, 10, 19), d(2026, 10, 19))) // Mon → Thu
        assertEquals(d(2026, 10, 26), next(r, d(2026, 10, 22), d(2026, 10, 22))) // Thu → Mon
        assertEquals(d(2026, 11, 2), next(r, d(2026, 10, 22), d(2026, 10, 30))) // late
    }

    @Test fun everyTwoWeeksCountsFromDueWeek() {
        val r = RepeatRule.Weekly(setOf(FRIDAY), 2)
        assertEquals(d(2026, 11, 6), next(r, d(2026, 10, 23), d(2026, 10, 23)))
        // Done very late (3 weeks): next cycle week is 6 Nov, then 20 Nov.
        assertEquals(d(2026, 11, 20), next(r, d(2026, 10, 23), d(2026, 11, 12)))
        val r2 = RepeatRule.Weekly(setOf(MONDAY, WEDNESDAY), 2)
        assertEquals(d(2026, 10, 21), next(r2, d(2026, 10, 19), d(2026, 10, 19))) // same cycle week
        assertEquals(d(2026, 11, 2), next(r2, d(2026, 10, 21), d(2026, 10, 21))) // skip a week
    }

    @Test fun monthlyClampsToMonthEnd() {
        val r = RepeatRule.Monthly(31)
        assertEquals(d(2026, 11, 30), next(r, d(2026, 10, 31), d(2026, 10, 31)))
        assertEquals(d(2026, 12, 31), next(r, d(2026, 11, 30), d(2026, 11, 30))) // back to 31
        assertEquals(d(2027, 2, 28), next(r, d(2027, 1, 31), d(2027, 1, 31)))
        assertEquals(d(2028, 2, 29), next(r, d(2028, 1, 31), d(2028, 1, 31))) // leap year
        assertEquals(d(2027, 1, 31), next(r, d(2026, 12, 31), d(2026, 12, 31))) // year rollover
        val r30 = RepeatRule.Monthly(30)
        assertEquals(d(2027, 3, 30), next(r30, d(2027, 2, 28), d(2027, 2, 28)))
    }

    @Test fun afterCompletionCountsFromDoneDay() {
        val r = RepeatRule.AfterCompletion(10)
        assertEquals(d(2026, 10, 31), next(r, d(2026, 10, 1), d(2026, 10, 21)))
        assertEquals(d(2026, 10, 31), next(r, null, d(2026, 10, 21)))
    }

    @Test fun noDueDateAnchorsOnCompletion() {
        assertEquals(d(2026, 10, 26), next(RepeatRule.Weekly(setOf(MONDAY)), null, d(2026, 10, 21)))
    }

    @Test fun firstOccurrences() {
        val wed = d(2026, 10, 21)
        assertEquals(wed, firstOccurrence(RepeatRule.Daily(1), wed))
        assertEquals(d(2026, 10, 26), firstOccurrence(RepeatRule.Weekly(setOf(MONDAY)), wed))
        assertEquals(wed, firstOccurrence(RepeatRule.Weekly(setOf(MONDAY, WEDNESDAY)), wed))
        assertEquals(d(2026, 10, 26), firstOccurrence(RepeatRule.Weekdays, d(2026, 10, 24)))
        assertEquals(d(2026, 11, 5), firstOccurrence(RepeatRule.Monthly(5), wed))
        assertEquals(d(2026, 10, 31), firstOccurrence(RepeatRule.Monthly(31), wed))
    }

    @Test fun encodeDecodeRoundTrip() {
        val rules = listOf(
            RepeatRule.Daily(1), RepeatRule.Daily(4), RepeatRule.Weekdays,
            RepeatRule.Weekly(setOf(MONDAY, FRIDAY), 2), RepeatRule.Monthly(31), RepeatRule.AfterCompletion(7),
        )
        for (r in rules) assertEquals(r, RepeatRule.decode(r.encode()))
        assertNull(RepeatRule.decode(null))
        assertNull(RepeatRule.decode("garbage"))
        assertNull(RepeatRule.decode("W:1:"))
    }

    @Test fun labels() {
        assertEquals("Every day", RepeatRule.Daily(1).label())
        assertEquals("Every Mon, Thu", RepeatRule.Weekly(setOf(THURSDAY, MONDAY)).label())
        assertEquals("Every 2 weeks · Fri", RepeatRule.Weekly(setOf(FRIDAY), 2).label())
        assertEquals("Monthly · 31st", RepeatRule.Monthly(31).label())
        assertEquals("Monthly · 22nd", RepeatRule.Monthly(22).label())
        assertEquals("3 days after done", RepeatRule.AfterCompletion(3).label())
    }

    /** India has no DST: a 9:00 reminder is 03:30 UTC on every day of the year. */
    @Test fun istDueTimeIsStableAllYear() {
        val ist = ZoneId.of("Asia/Kolkata")
        var day = d(2026, 1, 1)
        while (day.year == 2026) {
            val t = Task(title = "x", createdAt = 0, dueDay = day.toEpochDay(), dueMinute = 9 * 60)
            val millis = t.dueAtMillis(ist)!!
            assertEquals(day.toEpochDay() * 86_400_000L + (3 * 60 + 30) * 60_000L, millis)
            day = day.plusDays(1)
        }
        assertNull(Task(title = "x", createdAt = 0, dueDay = 1).dueAtMillis(ist))
    }
}
