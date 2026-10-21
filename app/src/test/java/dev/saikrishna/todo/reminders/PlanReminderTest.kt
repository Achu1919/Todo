package dev.saikrishna.todo.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PlanReminderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test fun laterTodayOrTomorrow() {
        assertEquals(at("2026-10-21T08:00"), PlanReminder.nextAt(at("2026-10-21T07:15"), 480, zone))
        assertEquals(at("2026-10-22T08:00"), PlanReminder.nextAt(at("2026-10-21T08:00"), 480, zone))
        assertEquals(at("2026-10-22T08:00"), PlanReminder.nextAt(at("2026-10-21T21:00"), 480, zone))
        assertEquals(at("2026-10-21T21:45"), PlanReminder.nextAt(at("2026-10-21T21:00"), 21 * 60 + 45, zone))
    }
}
