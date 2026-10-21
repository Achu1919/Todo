package dev.saikrishna.todo.org

import dev.saikrishna.todo.domain.Focus
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.domain.FocusSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/** Focus schedule evaluation in IST, at the edges. Work Mon–Fri 09:30–18:30 (start in, end out). */
class FocusTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val work = FocusSchedule(enabled = true) // Mon–Fri 09:30–18:30, Personal outside
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi)
    private fun ms(t: LocalDateTime) = t.atZone(ist).toInstant().toEpochMilli()
    private fun eff(t: LocalDateTime, manual: FocusMode? = null, manualAt: LocalDateTime? = null, s: FocusSchedule = work) =
        Focus.effective(manual, manualAt?.let(::ms), s, ms(t), ist)

    // Wed 21 Oct 2026 is a working day; Sat 24 Oct is not.
    @Test fun windowEdges() {
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 21, 9, 29)))
        assertEquals(FocusMode.Work, eff(at(2026, 10, 21, 9, 30)))
        assertEquals(FocusMode.Work, eff(at(2026, 10, 21, 18, 29)))
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 21, 18, 30)))
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 21, 0, 0)))
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 21, 23, 59)))
    }

    @Test fun weekendAndOutsideOff() {
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 24, 11, 0)))
        assertEquals(FocusMode.Off, eff(at(2026, 10, 24, 11, 0), s = work.copy(outside = FocusMode.Off)))
        assertEquals(FocusMode.Work, eff(at(2026, 10, 23, 18, 0))) // Friday
    }

    @Test fun scheduleOffUsesTheManualPick() {
        val off = work.copy(enabled = false)
        assertEquals(FocusMode.Off, eff(at(2026, 10, 21, 11, 0), s = off))
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 21, 11, 0), FocusMode.Personal, at(2026, 10, 1, 0, 0), off))
    }

    @Test fun manualPickLastsUntilTheNextBoundary() {
        // Picked Personal at 11:00 during work hours: holds until 18:30, then the schedule (Personal anyway).
        val pick = at(2026, 10, 21, 11, 0)
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 21, 18, 29), FocusMode.Personal, pick))
        // Picked Work at 20:00 (outside): holds overnight until 09:30 next day, then the schedule (Work).
        val night = at(2026, 10, 21, 20, 0)
        assertEquals(FocusMode.Work, eff(at(2026, 10, 22, 9, 29), FocusMode.Work, night))
        // Picked Off on Friday evening: lasts the whole weekend, ends Monday 09:30.
        val fri = at(2026, 10, 23, 19, 0)
        assertEquals(FocusMode.Off, eff(at(2026, 10, 25, 22, 0), FocusMode.Off, fri))
        assertEquals(FocusMode.Off, eff(at(2026, 10, 26, 9, 29), FocusMode.Off, fri))
        assertEquals(FocusMode.Work, eff(at(2026, 10, 26, 9, 30), FocusMode.Off, fri))
    }

    @Test fun nextChangeSkipsDaysOff() {
        assertEquals(at(2026, 10, 26, 9, 30), Focus.nextChange(work, at(2026, 10, 23, 18, 30)))
        assertEquals(at(2026, 10, 21, 18, 30), Focus.nextChange(work, at(2026, 10, 21, 9, 30)))
    }

    @Test fun overnightWindow() {
        // Night shift Mon–Fri 22:00–06:00: belongs to the day it starts.
        val night = FocusSchedule(enabled = true, start = 22 * 60, end = 6 * 60, outside = FocusMode.Off)
        assertEquals(FocusMode.Work, eff(at(2026, 10, 23, 23, 0), s = night)) // Fri 23:00
        assertEquals(FocusMode.Work, eff(at(2026, 10, 24, 5, 59), s = night)) // Sat 05:59 (Friday's shift)
        assertEquals(FocusMode.Off, eff(at(2026, 10, 24, 6, 0), s = night))
        assertEquals(FocusMode.Off, eff(at(2026, 10, 24, 23, 0), s = night)) // Saturday is off
        assertEquals(FocusMode.Off, eff(at(2026, 10, 26, 5, 0), s = night)) // Mon 05:00: Sunday had no shift
    }

    @Test fun customDays() {
        val s = work.copy(days = setOf(DayOfWeek.SATURDAY))
        assertEquals(FocusMode.Work, eff(at(2026, 10, 24, 10, 0), s = s))
        assertEquals(FocusMode.Personal, eff(at(2026, 10, 21, 10, 0), s = s))
    }
}
