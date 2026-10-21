package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.ListArea
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Focus mode (v0.7): Home shows only that area's lists. Reminders are NOT filtered. */
enum class FocusMode {
    Off, Work, Personal;

    val area: ListArea? get() = when (this) { Off -> null; Work -> ListArea.Work; Personal -> ListArea.Personal }

    companion object {
        fun parse(name: String?): FocusMode? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Automatic schedule: Work on [days] from [start] (inclusive) to [end] (exclusive), minutes after
 * local midnight; [outside] the rest of the time. An overnight window (start > end) runs past
 * midnight and belongs to the day it starts on.
 */
@Immutable
data class FocusSchedule(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet(),
    val start: Int = 9 * 60 + 30,
    val end: Int = 18 * 60 + 30,
    val outside: FocusMode = FocusMode.Personal,
)

object Focus {
    /** Pure: what the schedule says at local time [t]. */
    fun scheduled(s: FocusSchedule, t: LocalDateTime): FocusMode {
        if (!s.enabled || s.start == s.end) return if (s.enabled) s.outside else FocusMode.Off
        val m = t.hour * 60 + t.minute
        val inWindow = if (s.start < s.end) {
            t.dayOfWeek in s.days && m >= s.start && m < s.end
        } else {
            (t.dayOfWeek in s.days && m >= s.start) || (t.dayOfWeek.minus(1) in s.days && m < s.end)
        }
        return if (inWindow) FocusMode.Work else s.outside
    }

    /** Pure: the first minute after [t] at which [scheduled] changes (null if it never does within 8 days). */
    fun nextChange(s: FocusSchedule, t: LocalDateTime): LocalDateTime? {
        if (!s.enabled) return null
        val now = scheduled(s, t)
        var probe = t.withSecond(0).withNano(0).plusMinutes(1)
        // Boundaries only fall on start/end minutes: check those candidates in order (8 days).
        val candidates = (0L..8L).flatMap { d ->
            val day = t.toLocalDate().plusDays(d)
            listOf(day.atStartOfDay().plusMinutes(s.start.toLong()), day.atStartOfDay().plusMinutes(s.end.toLong()))
        }.filter { it.isAfter(t) }.sorted()
        for (c in candidates) {
            probe = c
            if (scheduled(s, probe) != now) return probe
        }
        return null
    }

    /**
     * Pure: the mode in effect at [nowMillis] in [zone]. A manual pick ([manual] at [manualAt])
     * wins; with the schedule on, it lasts until the schedule next changes after the pick.
     */
    fun effective(manual: FocusMode?, manualAt: Long?, s: FocusSchedule, nowMillis: Long, zone: ZoneId): FocusMode {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        if (!s.enabled) return manual ?: FocusMode.Off
        if (manual != null && manualAt != null) {
            val pickedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(manualAt), zone)
            val until = nextChange(s, pickedAt)
            if (until == null || now.isBefore(until)) return manual
        }
        return scheduled(s, now)
    }

    fun minuteLabel(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
}
