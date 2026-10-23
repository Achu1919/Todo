package dev.saikrishna.todo.domain

import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.ComposerDraft.Picks
import dev.saikrishna.todo.domain.ComposerDraft.Source
import dev.saikrishna.todo.domain.ComposerDraft.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** v0.13 add-task toolbar <-> parser sync. */
class ComposerDraftTest {
    // Friday 23 Oct 2026, 18:30.
    private val now = LocalDateTime.of(2026, 10, 23, 18, 30)
    private val today = now.toLocalDate()
    private fun parse(s: String, ignored: Set<String> = emptySet()) = QuickParse.parse(s, now, ignored, TaskList.Defaults)
    private fun merge(s: String, p: Picks = Picks(), ignored: Set<String> = emptySet()) = ComposerDraft.merge(parse(s, ignored), p, now, TaskList.Defaults)

    @Test fun typedWordsLightTheirChips() {
        val d = merge("Call mum tomorrow 7pm !high")
        assertEquals("Call mum", d.title)
        assertEquals(today.plusDays(1), d.dueDay)
        assertEquals(19 * 60, d.dueMinute)
        assertEquals(Source.Typed, d.chip(Tool.Date)!!.source)
        assertEquals(Source.Typed, d.chip(Tool.Time)!!.source)
        assertEquals(Source.Typed, d.chip(Tool.Priority)!!.source)
        assertTrue(d.chip(Tool.Date)!!.tokenKey != null)
        assertEquals(listOf(Tool.Date, Tool.Time, Tool.Priority), d.chips.map { it.tool }) // toolbar order
    }

    @Test fun explicitPicksWinOverTheText() {
        val sat = LocalDate.of(2026, 10, 31)
        val d = merge("Report tomorrow 7pm !low", Picks(day = sat, minute = 9 * 60, priority = Priority.High))
        assertEquals("Report", d.title) // typed words still stripped
        assertEquals(sat, d.dueDay)
        assertEquals(9 * 60, d.dueMinute)
        assertEquals(Priority.High, d.priority)
        assertEquals(Source.Picked, d.chip(Tool.Date)!!.source)
        assertNull(d.chip(Tool.Date)!!.tokenKey)
    }

    @Test fun pickedTimeAloneGoesTodayOrTomorrow() {
        assertEquals(today, merge("Gym", Picks(minute = 20 * 60)).dueDay)
        assertEquals(today.plusDays(1), merge("Gym", Picks(minute = 9 * 60)).dueDay) // 9:00 already passed
        assertEquals(Source.Auto, merge("Gym", Picks(minute = 9 * 60)).chip(Tool.Date)!!.source)
        // A typed date is kept with a picked time.
        assertEquals(LocalDate.of(2026, 10, 26), merge("Gym monday", Picks(minute = 9 * 60)).dueDay)
    }

    @Test fun pickedRepeatSetsTheFirstDate() {
        val d = merge("Standup", Picks(repeat = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)), minute = 10 * 60))
        assertEquals(LocalDate.of(2026, 10, 26), d.dueDay)
        assertEquals(Source.Picked, d.chip(Tool.Repeat)!!.source)
        // Daily at a time already passed today starts tomorrow.
        assertEquals(today.plusDays(1), merge("Meds", Picks(repeat = RepeatRule.Daily(1), minute = 8 * 60)).dueDay)
        // Typed repeat words light the chip.
        assertEquals(Source.Typed, merge("Meds every day").chip(Tool.Repeat)!!.source)
    }

    @Test fun reminderNeedsATimeAndDefaultsToMorning() {
        val d = merge("Pay rent", Picks(remindBefore = 60))
        assertEquals(9 * 60, d.dueMinute)
        assertEquals(today.plusDays(1), d.dueDay)
        assertEquals("1 h before", d.chip(Tool.Reminder)!!.label)
        assertEquals(60, d.toNewTask(1).remindBefore)
        // Untimed tasks never carry a lead.
        assertEquals(0, merge("Pay rent friday").toNewTask(1).remindBefore)
    }

    @Test fun listAndNotesPicks() {
        val work = TaskList.Defaults.first { it.id != TaskList.PERSONAL }
        val d = merge("Slides #personal", Picks(listId = work.id, notes = "Use the new template\nand dark mode"))
        assertEquals(work.id, d.listId)
        assertEquals(work.name, d.chip(Tool.List)!!.label)
        assertEquals("Use the new template", d.chip(Tool.Notes)!!.label)
        assertEquals("Use the new template\nand dark mode\n\nshared", d.toNewTask(1, "shared").notes)
        assertNull(merge("Slides").listId)
        assertEquals(TaskList.PERSONAL, merge("Slides").toNewTask(TaskList.PERSONAL).listId)
    }

    @Test fun clearingAChip() {
        // Picked: the pick goes, the typed value shows again.
        val p = Picks(day = LocalDate.of(2026, 11, 2))
        val d = merge("Call tomorrow", p)
        val (p2, ig2) = ComposerDraft.clear(d.chip(Tool.Date)!!, p, emptySet())
        assertTrue(p2.isEmpty)
        assertEquals(today.plusDays(1), merge("Call tomorrow", p2, ig2).dueDay)
        // Typed: the token is ignored, its words return to the title.
        val typed = merge("Call tomorrow")
        val (p3, ig3) = ComposerDraft.clear(typed.chip(Tool.Date)!!, Picks(), emptySet())
        val back = merge("Call tomorrow", p3, ig3)
        assertNull(back.dueDay)
        assertEquals("Call tomorrow", back.title)
        // Auto chips have nothing to clear.
        val auto = merge("Gym", Picks(minute = 20 * 60)).chip(Tool.Date)!!
        assertEquals(Picks(minute = 20 * 60) to emptySet<String>(), ComposerDraft.clear(auto, Picks(minute = 20 * 60), emptySet()))
    }

    @Test fun datePresets() {
        val fri = ComposerDraft.datePresets(today).toMap()
        assertEquals(LocalDate.of(2026, 10, 24), fri["This weekend"])
        assertEquals(LocalDate.of(2026, 10, 26), fri["Next week"])
        val sun = ComposerDraft.datePresets(LocalDate.of(2026, 10, 25)).toMap()
        assertEquals(LocalDate.of(2026, 10, 25), sun["This weekend"])
        assertEquals(LocalDate.of(2026, 10, 26), sun["Next week"])
        val mon = ComposerDraft.datePresets(LocalDate.of(2026, 10, 26)).toMap()
        assertEquals(LocalDate.of(2026, 11, 2), mon["Next week"])
        assertEquals("10 min before", ComposerDraft.reminderLabel(10))
        assertEquals("2 h before", ComposerDraft.reminderLabel(120))
    }
}
