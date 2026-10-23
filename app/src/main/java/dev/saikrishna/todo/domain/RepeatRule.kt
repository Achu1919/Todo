package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * How a task repeats. Pure Kotlin (java.time dates only, no zones), so next-occurrence maths has
 * no DST edge cases at all; the time of day is stored separately as minutes and carried over.
 *
 * Stored as a short string (see [encode]/[decode]) in `tasks.repeat_rule`.
 */
@Immutable
sealed interface RepeatRule {
    /** Every [every] days on a fixed schedule (phase kept from the due date). */
    data class Daily(val every: Int = 1) : RepeatRule
    /** Monday to Friday. */
    data object Weekdays : RepeatRule
    /** On [days] of every [every]-th week (weeks counted from the due date's week, Monday-based). */
    data class Weekly(val days: Set<DayOfWeek>, val every: Int = 1) : RepeatRule
    /**
     * On day [day] of every [every]-th month (v0.13: [every], counted from the due date's month),
     * clamped to the month's last day (31 → 30 Apr, 28/29 Feb).
     */
    data class Monthly(val day: Int, val every: Int = 1) : RepeatRule
    /** v0.13: every year on [month]/[day] (29 Feb → 28 Feb in common years). */
    data class Yearly(val month: Int, val day: Int) : RepeatRule
    /** [days] days after the day it was completed (not on a fixed schedule). */
    data class AfterCompletion(val days: Int) : RepeatRule

    fun encode(): String = when (this) {
        is Daily -> "D:$every"
        Weekdays -> "WD"
        is Weekly -> "W:$every:" + days.sorted().joinToString(",") { it.value.toString() }
        is Monthly -> if (every == 1) "M:$day" else "M:$day:$every"
        is Yearly -> "Y:$month:$day"
        is AfterCompletion -> "A:$days"
    }

    companion object {
        /** Lenient: anything unreadable decodes to null (no repeat) instead of crashing. */
        fun decode(s: String?): RepeatRule? {
            if (s.isNullOrBlank()) return null
            val p = s.split(":")
            return runCatching {
                when (p[0]) {
                    "D" -> Daily(p[1].toInt().coerceAtLeast(1))
                    "WD" -> Weekdays
                    "W" -> {
                        val days = p[2].split(",").filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet()
                        if (days.isEmpty()) null else Weekly(days, p[1].toInt().coerceAtLeast(1))
                    }
                    "M" -> Monthly(p[1].toInt().coerceIn(1, 31), p.getOrNull(2)?.toInt()?.coerceAtLeast(1) ?: 1)
                    "Y" -> Yearly(p[1].toInt().coerceIn(1, 12), p[2].toInt().coerceIn(1, 31))
                    "A" -> AfterCompletion(p[1].toInt().coerceAtLeast(1))
                    else -> null
                }
            }.getOrNull()
        }
    }
}

private val ShortDay = mapOf(
    DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu", DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun",
)

fun DayOfWeek.short(): String = ShortDay.getValue(this)

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** Short human label: "Every day", "Every 2 weeks · Mon, Thu", "Monthly · 31st", "3 days after done". */
fun RepeatRule.label(): String = when (this) {
    is RepeatRule.Daily -> if (every == 1) "Every day" else "Every $every days"
    RepeatRule.Weekdays -> "Every weekday"
    is RepeatRule.Weekly -> {
        val d = days.sorted().joinToString(", ") { it.short() }
        if (every == 1) "Every $d" else "Every $every weeks · $d"
    }
    is RepeatRule.Monthly -> if (every == 1) "Monthly · ${ordinal(day)}" else "Every $every months · ${ordinal(day)}"
    is RepeatRule.Yearly -> "Yearly · $day ${java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)}"
    is RepeatRule.AfterCompletion -> if (days == 1) "1 day after done" else "$days days after done"
}

/**
 * The next due date after completing a repeating task.
 *
 * - Scheduled rules (daily, weekdays, weekly, monthly) return the first date of the schedule that is
 *   strictly after BOTH the current due date and the completion day. So completing early keeps the
 *   schedule (due Fri, done Wed → next Fri), and completing late never yields a date in the past
 *   (due 3 days ago, daily → tomorrow).
 * - [RepeatRule.AfterCompletion] counts from the completion day.
 * - A task without a due date uses the completion day as its anchor.
 */
fun nextOccurrence(rule: RepeatRule, due: LocalDate?, completedOn: LocalDate): LocalDate {
    val base = due ?: completedOn
    val after = if (base.isAfter(completedOn)) base else completedOn
    return when (rule) {
        is RepeatRule.Daily -> {
            val n = rule.every.coerceAtLeast(1).toLong()
            // Smallest base + k·n strictly after `after` (k ≥ 1).
            val gap = after.toEpochDay() - base.toEpochDay()
            val k = gap / n + 1
            base.plusDays(k * n)
        }
        RepeatRule.Weekdays -> {
            var d = after.plusDays(1)
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
            d
        }
        is RepeatRule.Weekly -> {
            val days = rule.days.ifEmpty { setOf(base.dayOfWeek) }.sorted()
            val n = rule.every.coerceAtLeast(1).toLong()
            val firstWeek = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            // Skip whole cycles that end before `after`, then scan (bounded).
            val weeksGap = (after.toEpochDay() - firstWeek.toEpochDay()) / 7
            var cycle = (weeksGap / n - 1).coerceAtLeast(0)
            while (true) {
                val ws = firstWeek.plusWeeks(cycle * n)
                for (d in days) {
                    val date = ws.plusDays((d.value - 1).toLong())
                    if (date.isAfter(after)) return date
                }
                cycle++
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        is RepeatRule.Monthly -> {
            val n = rule.every.coerceAtLeast(1).toLong()
            val first = YearMonth.from(base)
            // Months on the schedule are first + k·n; start at the cycle just before `after`'s month.
            val gap = java.time.temporal.ChronoUnit.MONTHS.between(first, YearMonth.from(after))
            var ym = first.plusMonths(((gap / n) - 1).coerceAtLeast(0) * n)
            while (true) {
                val date = ym.atDay(minOf(rule.day, ym.lengthOfMonth()))
                if (date.isAfter(after)) return date
                ym = ym.plusMonths(n)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        is RepeatRule.Yearly -> {
            var y = after.year
            while (true) {
                val date = yearlyDate(rule, y)
                if (date.isAfter(after)) return date
                y++
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        is RepeatRule.AfterCompletion -> completedOn.plusDays(rule.days.coerceAtLeast(1).toLong())
    }
}

/** The first date ON or after [from] that the rule lands on (a new repeating task's first due date). */
fun firstOccurrence(rule: RepeatRule, from: LocalDate): LocalDate = when (rule) {
    is RepeatRule.Daily, is RepeatRule.AfterCompletion -> from
    RepeatRule.Weekdays -> if (from.dayOfWeek.value <= 5) from else from.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    is RepeatRule.Weekly -> rule.days.minOfOrNull { d -> from.with(TemporalAdjusters.nextOrSame(d)) } ?: from
    is RepeatRule.Monthly -> {
        val ym = YearMonth.from(from)
        val here = ym.atDay(minOf(rule.day, ym.lengthOfMonth()))
        if (!here.isBefore(from)) here else ym.plusMonths(1).let { it.atDay(minOf(rule.day, it.lengthOfMonth())) }
    }
    is RepeatRule.Yearly -> yearlyDate(rule, from.year).let { if (!it.isBefore(from)) it else yearlyDate(rule, from.year + 1) }
}

private fun yearlyDate(rule: RepeatRule.Yearly, year: Int): LocalDate {
    val ym = YearMonth.of(year, rule.month)
    return ym.atDay(minOf(rule.day, ym.lengthOfMonth()))
}

/** v0.13 Recurring view: the four groups; custom rules go to the nearest one. */
enum class RepeatGroup(val title: String) { Daily("Daily"), Weekly("Weekly"), Monthly("Monthly"), Yearly("Yearly") }

fun RepeatRule.group(): RepeatGroup = when (this) {
    is RepeatRule.Daily -> daysGroup(every)
    is RepeatRule.AfterCompletion -> daysGroup(days)
    RepeatRule.Weekdays -> RepeatGroup.Daily
    is RepeatRule.Weekly -> when {
        every < 4 -> RepeatGroup.Weekly
        every < 52 -> RepeatGroup.Monthly
        else -> RepeatGroup.Yearly
    }
    is RepeatRule.Monthly -> if (every < 12) RepeatGroup.Monthly else RepeatGroup.Yearly
    is RepeatRule.Yearly -> RepeatGroup.Yearly
}

private fun daysGroup(n: Int): RepeatGroup = when {
    n < 7 -> RepeatGroup.Daily
    n < 28 -> RepeatGroup.Weekly
    n < 365 -> RepeatGroup.Monthly
    else -> RepeatGroup.Yearly
}

/**
 * v0.13 Calendar: the dates a repeating task lands on AFTER [due] (its current due date), within
 * [from]..[to] inclusive, at most [limit]. Completing on the due day each time is assumed, so
 * "after completion" rules project as every N days. [due] itself is not included (it is the task).
 */
fun futureOccurrences(rule: RepeatRule, due: LocalDate, from: LocalDate, to: LocalDate, limit: Int = 400): List<LocalDate> {
    if (to.isBefore(from)) return emptyList()
    val out = ArrayList<LocalDate>()
    var d = due
    var guard = 0
    while (out.size < limit && guard++ < 5000) {
        d = nextOccurrence(rule, d, d)
        if (d.isAfter(to)) break
        if (!d.isBefore(from)) out += d
    }
    return out
}
