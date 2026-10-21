package dev.saikrishna.todo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** One quick snooze target: a day and a minute of that day. */
data class SnoozeOption(val label: String, val day: LocalDate, val minute: Int) {
    fun detail(today: LocalDate): String {
        val time = QuickParse.timeLabel(minute)
        return when (day) {
            today -> time
            today.plusDays(1) -> "Tomorrow, $time"
            else -> "${day.dayOfWeek.short()} ${day.dayOfMonth} ${day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, $time"
        }
    }
}

object Snooze {
    const val LATER_HOURS = 3L
    const val TONIGHT = 20 * 60
    const val MORNING = 9 * 60

    /**
     * The quick options for [now]: Later today (+3 h, rounded up to 5 min; only while it is still
     * today), Tonight 8 pm (only before 7 pm, so it differs from "later"), Tomorrow 9 am,
     * Next week (Monday) 9 am. Listed in time order.
     */
    fun options(now: LocalDateTime): List<SnoozeOption> = buildList {
        val today = now.toLocalDate()
        val later = now.plusHours(LATER_HOURS).let { t ->
            val m = t.hour * 60 + t.minute
            val rounded = (m + 4) / 5 * 5
            t.toLocalDate() to rounded
        }
        if (later.first == today && later.second < 24 * 60) add(SnoozeOption("Later today", today, later.second))
        if (now.hour < 19) add(SnoozeOption("Tonight", today, TONIGHT))
        add(SnoozeOption("Tomorrow", today.plusDays(1), MORNING))
        add(SnoozeOption("Next week", today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), MORNING))
    }.sortedWith(compareBy({ it.day }, { it.minute }))
}
