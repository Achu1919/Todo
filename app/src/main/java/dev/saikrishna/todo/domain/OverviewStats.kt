package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.priority
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** v0.12 Overview: one list's share of open and completed tasks (in list order). */
@Immutable
data class ListProgress(val list: TaskList, val open: Int, val done: Int) {
    val total: Int get() = open + done
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * v0.12 Overview numbers, computed purely from the task rows (no history table: a cleared Done
 * task no longer counts towards the week or the streak).
 *
 * Days are local days in [zone]; a completion counts on the local day of its `completedAt`.
 */
@Immutable
data class OverviewStats(
    /** Completed today (local day of completedAt == today). */
    val todayDone: Int,
    /** Open on the Today page: due today, overdue or undated. */
    val todayOpen: Int,
    val overdue: Int,
    /** Open with a due day after today. */
    val upcoming: Int,
    /** Completions Monday..Sunday of the current week (index 0 = Monday). */
    val week: List<Int>,
    /** Index of today in [week] (0 = Monday). */
    val todayIndex: Int,
    /** Consecutive days with at least one completion, ending today (or yesterday if today has none yet). */
    val streak: Int,
    /** The weekday with the most completions this week (earliest on a tie); null when none. */
    val bestDay: DayOfWeek?,
    /** The single most useful open task to show ("Next up"). */
    val nextUp: Task?,
    /** Minute of day of the next timed task due today at or after now; null when none. */
    val nextAtMinute: Int?,
    val lists: List<ListProgress>,
) {
    val todayTotal: Int get() = todayDone + todayOpen
    val todayProgress: Float get() = if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal
    val doneThisWeek: Int get() = week.sum()

    companion object {
        fun localDay(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        /**
         * @param nowMillis wall-clock time (gives today and the current minute in [zone]).
         * @param lists every list, any order (shown by position).
         */
        fun of(tasks: List<Task>, lists: List<TaskList>, nowMillis: Long, zone: ZoneId): OverviewStats {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val today = now.toLocalDate()
            val td = today.toEpochDay()
            val nowMinute = now.hour * 60 + now.minute

            var todayDone = 0; var todayOpen = 0; var overdue = 0; var upcoming = 0
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val week = IntArray(7)
            val doneDays = HashSet<Long>()
            for (t in tasks) {
                if (t.done) {
                    val at = t.completedAt ?: continue
                    val day = localDay(at, zone)
                    doneDays += day.toEpochDay()
                    if (day == today) todayDone++
                    val i = (day.toEpochDay() - monday.toEpochDay()).toInt()
                    if (i in 0..6) week[i]++
                } else {
                    val due = t.dueDay
                    when {
                        due != null && due > td -> upcoming++
                        else -> {
                            todayOpen++
                            if (due != null && due < td) overdue++
                        }
                    }
                }
            }

            var streak = 0
            var d = if (td in doneDays) td else td - 1
            while (d in doneDays) { streak++; d-- }

            val max = week.maxOrNull() ?: 0
            val bestDay = if (max == 0) null else DayOfWeek.of(week.indexOfFirst { it == max } + 1)

            val open = tasks.filter { !it.done && (it.dueDay == null || it.dueDay <= td) }
            val timedAhead = open.filter { it.dueDay == td && it.dueMinute != null && it.dueMinute >= nowMinute }
                .sortedWith(compareBy<Task> { it.dueMinute }.thenBy { it.sortOrder })
            val nextUp = nextUp(open, timedAhead, td)

            val counts = HashMap<Long, IntArray>()
            for (t in tasks) counts.getOrPut(t.listId) { IntArray(2) }[if (t.done) 1 else 0]++
            val progress = lists.sortedWith(compareBy<TaskList> { it.position }.thenBy { it.id })
                .mapNotNull { l -> counts[l.id]?.let { ListProgress(l, it[0], it[1]) }?.takeIf { it.total > 0 } }

            return OverviewStats(
                todayDone = todayDone, todayOpen = todayOpen, overdue = overdue, upcoming = upcoming,
                week = week.toList(), todayIndex = today.dayOfWeek.value - 1, streak = streak, bestDay = bestDay,
                nextUp = nextUp, nextAtMinute = timedAhead.firstOrNull()?.dueMinute, lists = progress,
            )
        }

        /**
         * Next up: the next timed task today; else the oldest overdue; else the first high-priority
         * task; else the first task in Today's manual order.
         */
        private fun nextUp(open: List<Task>, timedAhead: List<Task>, td: Long): Task? {
            timedAhead.firstOrNull()?.let { return it }
            open.filter { it.dueDay != null && it.dueDay < td }
                .minWithOrNull(compareBy<Task> { it.dueDay }.thenBy { it.dueMinute ?: Int.MAX_VALUE }.thenBy { it.sortOrder })
                ?.let { return it }
            val ordered = open.sortedWith(compareBy<Task> { it.sortOrder }.thenBy { it.id })
            return ordered.firstOrNull { it.priority == Priority.High } ?: ordered.firstOrNull()
        }

        /** "Good morning" 5–11, "Good afternoon" 12–16, "Good evening" 17–21, else "Hello". */
        fun greeting(hour: Int): String = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
    }
}
