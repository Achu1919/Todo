package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Priority
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.LocalDateTime

/** v0.13: Yearly and every-N-months rules, the Recurring groups, and calendar projection. */
class RepeatV13Test {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun newRulesRoundTripAndOldEncodingsStillDecode() {
        for (r in listOf(RepeatRule.Yearly(3, 14), RepeatRule.Monthly(1, 3), RepeatRule.Monthly(31))) {
            assertEquals(r, RepeatRule.decode(r.encode()))
        }
        assertEquals("M:1", RepeatRule.Monthly(1).encode()) // unchanged for every = 1
        assertEquals(RepeatRule.Monthly(15, 1), RepeatRule.decode("M:15"))
        assertEquals("Every 3 months · 1st", RepeatRule.Monthly(1, 3).label())
        assertEquals("Yearly · 14 Mar", RepeatRule.Yearly(3, 14).label())
    }

    @Test fun yearlyNextAndFirst() {
        val r = RepeatRule.Yearly(3, 14)
        assertEquals(d(2027, 3, 14), nextOccurrence(r, d(2026, 3, 14), d(2026, 3, 14)))
        assertEquals(d(2027, 3, 14), nextOccurrence(r, d(2026, 3, 14), d(2026, 10, 23))) // done late
        assertEquals(d(2027, 3, 14), firstOccurrence(r, d(2026, 10, 23)))
        assertEquals(d(2026, 3, 14), firstOccurrence(r, d(2026, 3, 14)))
        // 29 Feb lands on 28 Feb in common years.
        assertEquals(d(2027, 2, 28), nextOccurrence(RepeatRule.Yearly(2, 29), d(2026, 2, 28), d(2026, 2, 28)))
        assertEquals(d(2028, 2, 29), nextOccurrence(RepeatRule.Yearly(2, 29), d(2027, 2, 28), d(2027, 2, 28)))
    }

    @Test fun monthlyEveryNCountsFromTheDueMonth() {
        val r = RepeatRule.Monthly(31, 3)
        assertEquals(d(2026, 4, 30), nextOccurrence(r, d(2026, 1, 31), d(2026, 1, 31)))
        // Completed months late: the next date on the cycle after the completion.
        assertEquals(d(2026, 7, 31), nextOccurrence(r, d(2026, 1, 31), d(2026, 5, 2)))
        assertEquals(d(2026, 2, 28), nextOccurrence(RepeatRule.Monthly(31), d(2026, 1, 31), d(2026, 1, 31)))
    }

    @Test fun groupsPutCustomRulesInTheNearest() {
        assertEquals(RepeatGroup.Daily, RepeatRule.Daily(1).group())
        assertEquals(RepeatGroup.Daily, RepeatRule.Daily(3).group())
        assertEquals(RepeatGroup.Daily, RepeatRule.Weekdays.group())
        assertEquals(RepeatGroup.Weekly, RepeatRule.Daily(14).group())
        assertEquals(RepeatGroup.Weekly, RepeatRule.Weekly(setOf(MONDAY), 2).group())
        assertEquals(RepeatGroup.Monthly, RepeatRule.Weekly(setOf(MONDAY), 4).group())
        assertEquals(RepeatGroup.Monthly, RepeatRule.Daily(30).group())
        assertEquals(RepeatGroup.Monthly, RepeatRule.Monthly(1, 6).group())
        assertEquals(RepeatGroup.Yearly, RepeatRule.Monthly(1, 12).group())
        assertEquals(RepeatGroup.Yearly, RepeatRule.Yearly(1, 1).group())
        assertEquals(RepeatGroup.Weekly, RepeatRule.AfterCompletion(7).group())
    }

    @Test fun futureOccurrencesStayInsideTheWindowAndSkipTheDueDay() {
        val due = d(2026, 10, 23)
        assertEquals(
            listOf(d(2026, 10, 24), d(2026, 10, 25), d(2026, 10, 26)),
            futureOccurrences(RepeatRule.Daily(1), due, d(2026, 10, 20), d(2026, 10, 26)),
        )
        // Window starting later: only dates from `from` on.
        assertEquals(
            listOf(d(2026, 11, 6), d(2026, 11, 20)),
            futureOccurrences(RepeatRule.Daily(14), due, d(2026, 11, 1), d(2026, 11, 30)),
        )
        // Weekdays across a weekend; monthly; empty window.
        assertEquals(listOf(d(2026, 10, 26)), futureOccurrences(RepeatRule.Weekdays, due, d(2026, 10, 24), d(2026, 10, 26)))
        assertEquals(listOf(d(2026, 11, 23), d(2026, 12, 23)), futureOccurrences(RepeatRule.Monthly(23), due, due, d(2026, 12, 31)))
        assertEquals(emptyList<LocalDate>(), futureOccurrences(RepeatRule.Daily(1), due, d(2026, 10, 30), d(2026, 10, 29)))
        assertEquals(3, futureOccurrences(RepeatRule.Daily(1), due, due, d(2027, 12, 31), limit = 3).size)
    }

    @Test fun parserKnowsYearlyAndEveryNMonths() {
        val now = LocalDateTime.of(2026, 10, 23, 9, 0)
        val y = QuickParse.parse("Renew passport yearly on 14 mar", now)
        assertEquals(RepeatRule.Yearly(3, 14), y.repeat)
        assertEquals(d(2027, 3, 14), y.dueDay)
        val m = QuickParse.parse("Water filter every 3 months", now)
        assertEquals(RepeatRule.Monthly(23, 3), m.repeat)
        assertEquals("Water filter", m.title)
        assertEquals(RepeatRule.Yearly(10, 23), QuickParse.parse("Anniversary annually", now).repeat)
    }

    @Test fun mediumPriorityParsesAndSortsBetweenLowAndHigh() {
        val p = QuickParse.parse("Deck !med", LocalDateTime.of(2026, 10, 23, 9, 0))
        assertEquals(Priority.Medium, p.priority)
        assertEquals("Deck", p.title)
        assertEquals(listOf(Priority.None, Priority.Low, Priority.Medium, Priority.High), Priority.entries.sortedBy { it.rank })
        assertEquals(Priority.Medium, Priority.fromCode(3))
        assertEquals(Priority.High, Priority.fromCode(2)) // stored codes unchanged
    }
}
