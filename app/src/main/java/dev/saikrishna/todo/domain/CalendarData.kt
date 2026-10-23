package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.activeRepeat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * v0.13 Calendar page data. Pure: tasks + a date window in, per-day entries out.
 *
 * - An open dated task sits on its due day. Done tasks sit on their due day, or (undated) on the
 *   local day they were completed ([zone] decides where midnight falls).
 * - A repeating open task also projects its future occurrences ([CalEntry.projected], drawn faded)
 *   through the window. A paused repeat projects nothing.
 * - A day is overdue when it is before today and still has an open, real task.
 */
object CalendarData {

    @Immutable
    data class CalEntry(val task: Task, val day: LocalDate, val projected: Boolean)

    @Immutable
    data class Day(
        val date: LocalDate,
        val entries: List<CalEntry>,
        /** Distinct list ids of the day's open entries, in entry order, at most [MaxDots]. */
        val dotLists: List<Long>,
        val overdue: Boolean,
    )

    const val MaxDots = 3

    fun build(tasks: List<Task>, from: LocalDate, to: LocalDate, today: LocalDate, zone: ZoneId): Map<LocalDate, Day> {
        if (to.isBefore(from)) return emptyMap()
        val byDay = HashMap<LocalDate, MutableList<CalEntry>>()
        fun put(e: CalEntry) { if (!e.day.isBefore(from) && !e.day.isAfter(to)) byDay.getOrPut(e.day) { ArrayList() } += e }
        for (t in tasks) {
            val due = t.dueDay?.let(LocalDate::ofEpochDay)
            if (t.done) {
                val d = due ?: t.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: continue
                put(CalEntry(t, d, projected = false))
                continue
            }
            if (due == null) continue
            put(CalEntry(t, due, projected = false))
            val rule = t.activeRepeat ?: continue
            if (rule is RepeatRule.AfterCompletion) continue // no fixed schedule to project
            val start = maxOf(from, due.plusDays(1))
            for (d in futureOccurrences(rule, due, start, to)) put(CalEntry(t, d, projected = true))
        }
        return byDay.mapValues { (d, list) ->
            val sorted = list.sortedWith(entryOrder)
            val open = sorted.filter { !it.task.done }
            Day(
                date = d,
                entries = sorted,
                dotLists = open.map { it.task.listId }.distinct().take(MaxDots),
                overdue = d.isBefore(today) && open.any { !it.projected },
            )
        }
    }

    /** Open first, then timed by time, then untimed, then done; ties by sort order. */
    private val entryOrder = compareBy<CalEntry>({ it.task.done }, { it.task.dueMinute ?: Int.MAX_VALUE }, { it.task.sortOrder }, { it.task.id })

    /** Monday of [d]'s week. */
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun week(d: LocalDate): List<LocalDate> = weekStart(d).let { s -> (0L..6L).map(s::plusDays) }

    /** The month grid: whole Monday-based weeks covering [month] (5 or 6 rows; 4 for a Feb starting Monday). */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val first = weekStart(month.atDay(1))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val n = (last.toEpochDay() - first.toEpochDay() + 1).toInt()
        return (0 until n).map { first.plusDays(it.toLong()) }
    }

    /**
     * Drag a task onto [target]: the new (dueDay, dueMinute). The time of day is kept; a projected
     * occurrence moves the real task (the series shifts with it); dropping on its own day is a no-op (null).
     */
    fun reschedule(task: Task, target: LocalDate): Pair<Long, Int?>? {
        if (task.done) return null
        if (task.dueDay == target.toEpochDay()) return null
        return target.toEpochDay() to task.dueMinute
    }

    /**
     * The repeat rule after a drag to [target], so the series follows the move: a single-day weekly
     * rule takes the new weekday, monthly the new day of month, yearly the new date. Others unchanged.
     */
    fun reanchor(rule: RepeatRule?, target: LocalDate): RepeatRule? = when (rule) {
        is RepeatRule.Weekly -> if (rule.days.size == 1) rule.copy(days = setOf(target.dayOfWeek)) else rule
        is RepeatRule.Monthly -> rule.copy(day = target.dayOfMonth)
        is RepeatRule.Yearly -> RepeatRule.Yearly(target.monthValue, target.dayOfMonth)
        else -> rule
    }
}
