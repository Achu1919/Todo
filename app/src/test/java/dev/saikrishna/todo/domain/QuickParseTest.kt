package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.domain.QuickParse.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class QuickParseTest {
    /** Wednesday 21 Oct 2026, 10:00 IST (wall clock; the parser is zone-free). */
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0)
    private val today = now.toLocalDate()
    private fun p(s: String, at: LocalDateTime = now, ignored: Set<String> = emptySet()) = QuickParse.parse(s, at, ignored)
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test fun plainTextIsUntouched() {
        val r = p("Buy milk")
        assertEquals("Buy milk", r.title)
        assertTrue(r.isPlain)
        assertNull(r.dueDay); assertNull(r.dueMinute); assertNull(r.priority); assertNull(r.repeat)
    }

    @Test fun relativeDays() {
        assertEquals(today, p("Call mum today").dueDay)
        assertEquals(today.plusDays(1), p("Call mum tomorrow").dueDay)
        assertEquals(today.plusDays(1), p("Call mum tmrw").dueDay)
        assertEquals(today.plusDays(2), p("Call mum day after tomorrow").dueDay)
        assertEquals("Call mum", p("Call mum tomorrow").title)
        assertEquals("Call mum", p("Call mum day after tomorrow").title)
    }

    @Test fun tonightIs8pmUnlessTimeGiven() {
        val r = p("Pay rent tonight")
        assertEquals(today, r.dueDay)
        assertEquals(20 * 60, r.dueMinute)
        assertEquals("Pay rent", r.title)
        assertEquals(21 * 60 + 30, p("Pay rent tonight at 9:30pm").dueMinute)
    }

    @Test fun weekdayNames() {
        // Today is Wednesday.
        assertEquals(d(10, 23), p("Gym friday").dueDay)
        assertEquals(d(10, 23), p("Gym on Fri").dueDay)
        assertEquals(today, p("Gym wednesday").dueDay) // today counts
        assertEquals(d(10, 26), p("Gym monday").dueDay)
        assertEquals(d(10, 30), p("Gym next friday").dueDay) // Friday of next week
        assertEquals("Gym", p("Gym on Fri").title)
    }

    @Test fun nextWeekAndInN() {
        assertEquals(d(10, 26), p("Plan trip next week").dueDay)
        assertEquals(today.plusDays(3), p("Plan trip in 3 days").dueDay)
        assertEquals(today.plusWeeks(2), p("Plan trip in two weeks").dueDay)
        assertEquals(today.plusDays(1), p("Plan trip in a day").dueDay)
        assertEquals(d(11, 1), p("Plan trip next month").dueDay)
        assertEquals("Plan trip", p("Plan trip in 3 days").title)
    }

    @Test fun absoluteDatesIndiaDayFirst() {
        assertEquals(d(10, 24), p("Party 24 Oct").dueDay)
        assertEquals(d(10, 24), p("Party 24th October").dueDay)
        assertEquals(d(10, 24), p("Party Oct 24").dueDay)
        assertEquals(d(10, 24), p("Party 24/10").dueDay)
        assertEquals(d(3, 5), p("Party 5/3/2026").dueDay.also { }, )
        assertEquals(d(12, 1, 2027), p("Party 1-12-2027").dueDay)
        assertEquals(d(1, 2, 2027), p("Party 2/1/27").dueDay)
        assertEquals("Party", p("Party on 24/10").title)
    }

    @Test fun pastDateWithoutYearRollsToNextYear() {
        assertEquals(d(1, 5, 2027), p("Taxes 5 Jan").dueDay)
        assertEquals(d(10, 20, 2027), p("Taxes 20/10").dueDay)
    }

    @Test fun invalidDatesAreText() {
        val r = p("Report 31/02")
        assertNull(r.dueDay)
        assertEquals("Report 31/02", r.title)
        assertNull(p("Do 3-4 reps").dueDay) // a dash needs a year
        assertNull(p("Ratio 13/13").dueDay)
    }

    @Test fun times() {
        assertEquals(16 * 60, p("Call at 4pm").dueMinute)
        assertEquals(16 * 60, p("Call 4 PM").dueMinute)
        assertEquals(16 * 60 + 30, p("Call 4:30pm").dueMinute)
        assertEquals(16 * 60 + 30, p("Call 16:30").dueMinute)
        assertEquals(9 * 60, p("Call at 9").dueMinute)
        assertEquals(17 * 60, p("Call at 5").dueMinute) // 1–7 → pm
        assertEquals(0, p("Call 12am").dueMinute)
        assertEquals(12 * 60, p("Call 12pm").dueMinute)
        assertEquals(12 * 60, p("Lunch at noon").dueMinute)
        assertEquals("Call", p("Call at 4pm").title)
    }

    @Test fun timeAloneIsTodayIfAheadElseTomorrow() {
        assertEquals(today, p("Call 4pm").dueDay)
        assertEquals(today.plusDays(1), p("Call 8am").dueDay)
        assertEquals(today.plusDays(1), p("Call at 10:00").dueDay) // exactly now = passed
    }

    @Test fun bareNumbersAreNotTimes() {
        val r = p("Buy 6 eggs")
        assertNull(r.dueMinute)
        assertEquals("Buy 6 eggs", r.title)
    }

    @Test fun dateAndTimeTogether() {
        val r = p("Dentist tomorrow at 4:15pm")
        assertEquals(today.plusDays(1), r.dueDay)
        assertEquals(16 * 60 + 15, r.dueMinute)
        assertEquals("Dentist", r.title)
        assertEquals(listOf(Kind.Date, Kind.Time), r.tokens.map { it.kind })
    }

    @Test fun priority() {
        assertEquals(Priority.High, p("Fix bug !high").priority)
        assertEquals(Priority.High, p("Fix bug !h").priority)
        assertEquals(Priority.High, p("Fix bug!!").priority)
        assertEquals(Priority.Low, p("Fix bug !low").priority)
        assertEquals("Fix bug", p("Fix bug !high").title)
        assertEquals("Fix bug", p("Fix bug!!").title)
        assertNull(p("Wow!").priority)
        assertNull(p("Hello!!!").priority)
    }

    @Test fun repeats() {
        assertEquals(RepeatRule.Daily(1), p("Meds every day").repeat)
        assertEquals(RepeatRule.Daily(1), p("Meds daily").repeat)
        assertEquals(RepeatRule.Daily(3), p("Water plants every 3 days").repeat)
        assertEquals(RepeatRule.Weekdays, p("Standup every weekday").repeat)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)), p("Bins every Monday").repeat)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), p("Gym every mon and thu").repeat)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.WEDNESDAY), 2), p("Payroll every 2 weeks").repeat) // anchored on today (Wed)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY), 2), p("Payroll every other friday").repeat)
        assertEquals(RepeatRule.Monthly(21), p("Rent monthly").repeat)
        assertEquals(RepeatRule.Monthly(5), p("Rent monthly on the 5th").repeat)
        assertEquals(RepeatRule.Monthly(1), p("Rent every 1st").repeat)
        assertEquals(RepeatRule.AfterCompletion(10), p("Haircut every 10 days after done").repeat)
        assertEquals("Bins", p("Bins every Monday").title)
        assertEquals("Haircut", p("Haircut every 10 days after done").title)
    }

    @Test fun repeatSetsFirstDueDate() {
        assertEquals(d(10, 26), p("Bins every Monday").dueDay)
        assertEquals(today, p("Standup every weekday").dueDay)
        assertEquals(d(11, 5), p("Rent monthly on the 5th").dueDay)
        // Time already passed today → next occurrence.
        assertEquals(today.plusDays(1), p("Meds every day at 8am").dueDay)
        assertEquals(today, p("Meds every day at 9pm").dueDay)
    }

    @Test fun repeatAnchorsOnExplicitDate() {
        val r = p("Review every 2 weeks from friday".replace(" from", ""))
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY), 2), r.repeat)
        assertEquals(d(10, 23), r.dueDay)
        assertEquals(RepeatRule.Monthly(24), p("Invoice monthly 24/10").repeat)
    }

    @Test fun ignoringAChipKeepsTheText() {
        val text = "Read Monday Morning Leadership tomorrow"
        val r = p(text)
        // Explicit relative words beat weekday names: "tomorrow" wins, "Monday" stays text.
        assertEquals(today.plusDays(1), r.dueDay)
        assertEquals("Read Monday Morning Leadership", r.title)
        val tomorrow = r.tokens.single { it.kind == Kind.Date }
        // Tapping the chip: "tomorrow" is kept as text, and the next candidate (Monday) applies.
        val r2 = p(text, ignored = setOf(tomorrow.key))
        assertEquals(d(10, 26), r2.dueDay)
        assertEquals("Read Morning Leadership tomorrow", r2.title)
        // Ignore both: plain text again.
        val r3 = p(text, ignored = setOf(tomorrow.key) + r2.tokens.map { it.key })
        assertNull(r3.dueDay)
        assertEquals(text, r3.title)
    }

    @Test fun tokenSpansAreExact() {
        val text = "Dentist tomorrow at 4pm !high"
        val r = p(text)
        assertEquals(listOf("tomorrow", "at 4pm", "!high"), r.tokens.map { text.substring(it.start, it.end) })
        assertEquals(listOf("Tomorrow", "4 pm", "High"), r.tokens.map { it.label })
    }

    @Test fun allTokensKeepsRawTitle() {
        assertEquals("tomorrow", p("tomorrow").title)
        assertEquals(today.plusDays(1), p("tomorrow").dueDay)
    }

    @Test fun titleTidiesSeparators() {
        assertEquals("Email Ravi", p("Email Ravi, tomorrow").title)
        assertEquals("Email Ravi about the deck", p("Email Ravi tomorrow about the deck").title)
    }

    @Test fun labels() {
        assertEquals("4 pm", QuickParse.timeLabel(16 * 60))
        assertEquals("9:05 am", QuickParse.timeLabel(9 * 60 + 5))
        assertEquals("12 am", QuickParse.timeLabel(0))
        assertEquals("Fri", QuickParse.dateLabel(d(10, 23), today))
        assertEquals("24 Nov", QuickParse.dateLabel(d(11, 24), today))
        assertEquals("5 Jan 2027", QuickParse.dateLabel(d(1, 5, 2027), today))
    }
}
