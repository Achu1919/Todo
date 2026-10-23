package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.NewTask
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * v0.13 add-task toolbar: what the user picked with the buttons above the keyboard, merged with
 * what [QuickParse] read from the text. Pure, so the two-way sync is unit-tested.
 *
 * Rules:
 * - Typed words light the matching toolbar chip live (source [Source.Typed]).
 * - An explicit pick wins over the text for that field (source [Source.Picked]); the words stay
 *   in the title only if the user also taps the typed chip away, otherwise they are still stripped.
 * - Clearing a picked chip drops the pick (a typed value, if any, shows again); clearing a typed
 *   chip ignores that token (the words go back into the title), as tapping a parse chip always did.
 */
object ComposerDraft {

    enum class Tool(val label: String) { Date("Date"), Time("Time"), Repeat("Repeat"), List("List"), Priority("Priority"), Reminder("Reminder"), Notes("Notes") }

    /** Auto = derived from another field (a repeat's first date, a reminder's default time): no x. */
    enum class Source { Typed, Picked, Auto }

    /** Explicit toolbar picks. null = not picked (the text may still set it). */
    @Immutable
    data class Picks(
        val day: LocalDate? = null,
        val minute: Int? = null,
        val repeat: RepeatRule? = null,
        val listId: Long? = null,
        val priority: Priority? = null,
        /** Minutes before the due time (0 = at the due time). */
        val remindBefore: Int? = null,
        val notes: String? = null,
    ) {
        fun cleared(tool: Tool): Picks = when (tool) {
            Tool.Date -> copy(day = null)
            Tool.Time -> copy(minute = null)
            Tool.Repeat -> copy(repeat = null)
            Tool.List -> copy(listId = null)
            Tool.Priority -> copy(priority = null)
            Tool.Reminder -> copy(remindBefore = null)
            Tool.Notes -> copy(notes = null)
        }
        fun has(tool: Tool): Boolean = when (tool) {
            Tool.Date -> day != null
            Tool.Time -> minute != null
            Tool.Repeat -> repeat != null
            Tool.List -> listId != null
            Tool.Priority -> priority != null
            Tool.Reminder -> remindBefore != null
            Tool.Notes -> !notes.isNullOrBlank()
        }
        val isEmpty: Boolean get() = Tool.entries.none(::has)
    }

    @Immutable
    data class Chip(val tool: Tool, val label: String, val source: Source, /** The parse token behind a typed chip. */ val tokenKey: String? = null)

    @Immutable
    data class Draft(
        val title: String,
        val dueDay: LocalDate?,
        val dueMinute: Int?,
        val priority: Priority,
        val repeat: RepeatRule?,
        /** null = the caller's default list. */
        val listId: Long?,
        val tags: List<String>,
        val remindBefore: Int,
        val notes: String,
        /** One per filled toolbar field, in toolbar order. */
        val chips: List<Chip>,
    ) {
        fun chip(tool: Tool): Chip? = chips.firstOrNull { it.tool == tool }
        fun toNewTask(defaultList: Long, extraNotes: String = ""): NewTask = NewTask(
            title = title,
            dueDay = dueDay?.toEpochDay(),
            dueMinute = dueMinute,
            priority = priority,
            repeat = repeat,
            listId = listId ?: defaultList,
            tags = tags,
            notes = listOf(notes, extraNotes).filter { it.isNotBlank() }.joinToString("\n\n"),
            remindBefore = if (dueMinute == null) 0 else remindBefore,
        )
    }

    /** Toolbar presets (also used by the pickers). */
    val ReminderLeads = listOf(0 to "At due time", 10 to "10 min before", 60 to "1 h before", 24 * 60 to "1 day before")
    fun reminderLabel(minutes: Int): String = ReminderLeads.firstOrNull { it.first == minutes }?.second ?: when {
        minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} days before"
        minutes % 60 == 0 -> "${minutes / 60} h before"
        else -> "$minutes min before"
    }
    /** Morning / Afternoon / Evening / 6 pm. */
    val TimePresets = listOf(9 * 60 to "Morning", 13 * 60 to "Afternoon", 19 * 60 to "Evening", 18 * 60 to "6 pm")

    /** Today / Tomorrow / This weekend (Saturday; today if it is the weekend) / Next week (Monday). */
    fun datePresets(today: LocalDate): List<Pair<String, LocalDate>> {
        val dow = today.dayOfWeek.value // Mon 1 … Sun 7
        val weekend = if (dow >= 6) today else today.plusDays((6 - dow).toLong())
        val nextMonday = today.plusDays((8 - dow).toLong())
        return listOf("Today" to today, "Tomorrow" to today.plusDays(1), "This weekend" to weekend, "Next week" to nextMonday)
    }

    fun merge(parsed: QuickParse.Result, picks: Picks, now: LocalDateTime, lists: List<TaskList> = emptyList()): Draft {
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        fun token(kind: QuickParse.Kind) = parsed.tokens.firstOrNull { it.kind == kind }
        val chips = ArrayList<Chip>()

        // Time first (the date may depend on it).
        val minute = picks.minute ?: parsed.dueMinute
        val repeat = picks.repeat ?: parsed.repeat
        var day: LocalDate? = picks.day ?: run {
            // A picked time or repeat re-derives the day the parser would have chosen.
            val typedDay = token(QuickParse.Kind.Date)?.let { parsed.dueDay }
            when {
                typedDay != null -> typedDay
                picks.repeat != null -> null
                picks.minute != null -> null
                else -> parsed.dueDay
            }
        }
        if (day == null && repeat != null) {
            day = firstOccurrence(repeat, today)
            if (day == today && minute != null && minute <= nowMinute && repeat !is RepeatRule.AfterCompletion) day = nextOccurrence(repeat, today, today)
        }
        if (day == null && minute != null) day = if (minute > nowMinute) today else today.plusDays(1)
        // A reminder needs a time: picking one on an untimed task gives it the morning slot.
        var dueMinute = minute
        if (picks.remindBefore != null && dueMinute == null) {
            dueMinute = 9 * 60
            if (day == null) day = if (dueMinute > nowMinute) today else today.plusDays(1)
        }

        fun src(picked: Boolean) = if (picked) Source.Picked else Source.Typed
        if (day != null) {
            val tk = token(QuickParse.Kind.Date)
            val source = when { picks.day != null -> Source.Picked; tk != null -> Source.Typed; else -> Source.Auto }
            chips += Chip(Tool.Date, QuickParse.dateLabel(day, today), source, if (source == Source.Typed) tk?.key else null)
        }
        if (dueMinute != null) {
            val tk = token(QuickParse.Kind.Time)
            val source = when { picks.minute != null -> Source.Picked; tk != null -> Source.Typed; else -> Source.Auto }
            chips += Chip(Tool.Time, QuickParse.timeLabel(dueMinute), source, if (source == Source.Typed) tk?.key else null)
        }
        if (repeat != null) chips += Chip(Tool.Repeat, repeat.label(), src(picks.repeat != null), if (picks.repeat != null) null else token(QuickParse.Kind.Repeat)?.key)
        val listId = picks.listId ?: parsed.list?.id
        if (listId != null) {
            val name = lists.firstOrNull { it.id == listId }?.name ?: parsed.list?.name ?: "List"
            chips += Chip(Tool.List, name, src(picks.listId != null), if (picks.listId != null) null else token(QuickParse.Kind.List)?.key)
        }
        val priority = picks.priority ?: parsed.priority ?: Priority.None
        if (priority != Priority.None) chips += Chip(Tool.Priority, priority.name, src(picks.priority != null), if (picks.priority != null) null else token(QuickParse.Kind.Priority)?.key)
        val remind = picks.remindBefore ?: 0
        if (picks.remindBefore != null) chips += Chip(Tool.Reminder, reminderLabel(remind), Source.Picked)
        val notes = picks.notes?.trim().orEmpty()
        if (notes.isNotEmpty()) chips += Chip(Tool.Notes, notes.lineSequence().first().take(24), Source.Picked)

        return Draft(parsed.title, day, dueMinute, priority, repeat, listId, parsed.tags, remind, notes, chips)
    }

    /** What the x on a chip does: drop the pick, or ignore the typed token. */
    fun clear(chip: Chip, picks: Picks, ignored: Set<String>): Pair<Picks, Set<String>> = when (chip.source) {
        Source.Picked -> picks.cleared(chip.tool) to ignored
        Source.Typed -> picks to (if (chip.tokenKey != null) ignored + chip.tokenKey else ignored)
        Source.Auto -> picks to ignored
    }
}
