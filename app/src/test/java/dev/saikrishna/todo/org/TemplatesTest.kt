package dev.saikrishna.todo.org

import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.Template
import dev.saikrishna.todo.domain.TemplateItem
import dev.saikrishna.todo.domain.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Template instantiation: relative dates, list fallback, editor text. */
class TemplatesTest {
    private val wed = LocalDate.of(2026, 10, 21)
    private val known = setOf(TaskList.PERSONAL, TaskList.WORK)

    @Test fun relativeOffsetsBecomeRealDays() {
        val t = Template(1, "Review", listOf(
            TemplateItem("A", TaskList.WORK, dayOffset = 0, minute = 9 * 60),
            TemplateItem("B", TaskList.WORK, dayOffset = 1),
            TemplateItem("C", dayOffset = null, minute = 600), // no date: the time is dropped
            TemplateItem("D", TaskList.PERSONAL, dayOffset = 12, priority = Priority.High, tags = listOf("x"), subtasks = listOf("s1", "s2")),
        ))
        val out = Templates.instantiate(t, wed, fallbackList = TaskList.PERSONAL, knownLists = known)
        assertEquals(listOf(wed, wed.plusDays(1), null, LocalDate.of(2026, 11, 2)), out.map { it.dueDay?.let(LocalDate::ofEpochDay) })
        assertEquals(9 * 60, out[0].dueMinute)
        assertNull(out[2].dueMinute)
        assertEquals(TaskList.PERSONAL, out[2].listId) // no list → the one in view
        assertEquals(Priority.High, out[3].priority)
        assertEquals(listOf("s1", "s2"), out[3].subtasks)
        assertEquals(listOf("x"), out[3].tags)
    }

    @Test fun sameTemplateOnAnotherDayShifts() {
        val t = Template(1, "T", listOf(TemplateItem("A", dayOffset = 2)))
        val sat = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 2), LocalDate.ofEpochDay(Templates.instantiate(t, sat, TaskList.PERSONAL, known)[0].dueDay!!))
    }

    @Test fun deletedListFallsBack() {
        val t = Template(1, "T", listOf(TemplateItem("A", listId = 99)))
        assertEquals(TaskList.WORK, Templates.instantiate(t, wed, TaskList.WORK, known)[0].listId)
    }

    @Test fun fromTaskMakesDatesRelative() {
        val item = Templates.fromTask("Pay rent", TaskList.PERSONAL, wed.plusDays(3).toEpochDay(), 600, Priority.Low, listOf("home"), listOf("Transfer"), wed)
        assertEquals(3, item.dayOffset)
        assertEquals(600, item.minute)
        assertEquals(listOf("Transfer"), item.subtasks)
        // A past due date becomes "the day it is used".
        assertEquals(0, Templates.fromTask("Old", 1, wed.minusDays(5).toEpochDay(), null, Priority.None, emptyList(), emptyList(), wed).dayOffset)
    }

    @Test fun editorTextParsesLikeTheComposer() {
        val text = """
            Plan the week tomorrow #work !high +review
            - Inbox
            - Calendar
            Water plants
        """.trimIndent()
        val items = Templates.parseText(text, LocalDateTime.of(2026, 10, 21, 10, 0), TaskList.Defaults)
        assertEquals(2, items.size)
        assertEquals("Plan the week", items[0].title)
        assertEquals(1, items[0].dayOffset)
        assertEquals(TaskList.WORK, items[0].listId)
        assertEquals(Priority.High, items[0].priority)
        assertEquals(listOf("review"), items[0].tags)
        assertEquals(listOf("Inbox", "Calendar"), items[0].subtasks)
        assertNull(items[1].dayOffset)
        assertNull(items[1].listId)
    }

    @Test fun seedsShipTwoExamples() {
        assertEquals(listOf("Weekly review", "Trip packing"), Templates.Seeds.map { it.first })
    }
}
