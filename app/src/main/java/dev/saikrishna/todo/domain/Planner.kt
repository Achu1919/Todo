package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Task
import java.time.LocalDate

/** v0.8 morning planning. */
enum class PlanSection(val label: String) { Overdue("Overdue"), Today("Due today"), Suggested("Suggested") }

enum class PlanAction(val label: String) { Keep("Keep for today"), Tomorrow("Move to tomorrow"), PickDate("Pick a date"), Done("Done") }

@Immutable
data class PlanItem(val task: Task, val section: PlanSection)

/** What one decision writes: a new due day (time of day kept) and/or completion. */
@Immutable
data class PlanChange(val id: Long, val newDay: Long?, val done: Boolean)

@Immutable
data class PlanSummary(val kept: Int, val tomorrow: Int, val picked: Int, val done: Int, val todayTotal: Int) {
    val decided: Int get() = kept + tomorrow + picked + done
    /** "4 for today · 2 moved · 1 done" (zero parts left out). */
    val line: String get() = listOfNotNull(
        "$todayTotal for today",
        (tomorrow + picked).takeIf { it > 0 }?.let { "$it moved" },
        done.takeIf { it > 0 }?.let { "$it done" },
    ).joinToString(" · ")
}

object Planner {
    /**
     * The planning queue: overdue (oldest due first), due today (timed first, by time, then the
     * manual order), then up to [suggestions] suggestions: open tasks due in the next 7 days
     * (soonest first, at most 2), then undated open tasks (oldest first).
     */
    fun queue(tasks: List<Task>, today: LocalDate, suggestions: Int = 3): List<PlanItem> {
        val td = today.toEpochDay()
        val open = tasks.filter { !it.done }
        val overdue = open.filter { it.dueDay != null && it.dueDay < td }
            .sortedWith(compareBy<Task> { it.dueDay }.thenBy { it.sortOrder }.thenBy { it.id })
        val dueToday = open.filter { it.dueDay == td }
            .sortedWith(compareBy<Task> { it.dueMinute ?: Int.MAX_VALUE }.thenBy { it.sortOrder }.thenBy { it.id })
        val soon = open.filter { it.dueDay != null && it.dueDay in (td + 1)..(td + 7) }
            .sortedWith(compareBy<Task> { it.dueDay }.thenBy { it.sortOrder }).take(2)
        val undated = open.filter { it.dueDay == null }.sortedWith(compareBy<Task> { it.createdAt }.thenBy { it.id })
        val suggested = (soon + undated).take(suggestions.coerceAtLeast(0))
        return overdue.map { PlanItem(it, PlanSection.Overdue) } +
            dueToday.map { PlanItem(it, PlanSection.Today) } +
            suggested.map { PlanItem(it, PlanSection.Suggested) }
    }

    /** The change one decision makes. Keep on a task already due today changes nothing. */
    fun apply(task: Task, action: PlanAction, today: LocalDate, picked: LocalDate? = null): PlanChange {
        val td = today.toEpochDay()
        return when (action) {
            PlanAction.Keep -> PlanChange(task.id, td.takeIf { task.dueDay != td }, false)
            PlanAction.Tomorrow -> PlanChange(task.id, td + 1, false)
            PlanAction.PickDate -> PlanChange(task.id, requireNotNull(picked) { "pick a date" }.toEpochDay().takeIf { it != task.dueDay }, false)
            PlanAction.Done -> PlanChange(task.id, null, true)
        }
    }

    /**
     * Counts per decision. [PlanSummary.todayTotal] = queue items that end up due today: kept ones,
     * ones picked for today, and today's tasks that were skipped (left as they were).
     */
    fun summary(items: List<PlanItem>, decisions: Map<Long, PlanAction>, today: LocalDate, picks: Map<Long, LocalDate> = emptyMap()): PlanSummary {
        var kept = 0; var tomorrow = 0; var picked = 0; var done = 0; var todayTotal = 0
        for (it in items) {
            when (decisions[it.task.id]) {
                PlanAction.Keep -> { kept++; todayTotal++ }
                PlanAction.Tomorrow -> tomorrow++
                PlanAction.PickDate -> { picked++; if (picks[it.task.id] == today) todayTotal++ }
                PlanAction.Done -> done++
                null -> if (it.section == PlanSection.Today) todayTotal++
            }
        }
        return PlanSummary(kept, tomorrow, picked, done, todayTotal)
    }
}
