package dev.saikrishna.todo.org

import dev.saikrishna.todo.data.ListArea
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.QuickParse.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** v0.7 composer additions: "#list", "+tag", unknown "#tag". */
class QuickParseOrgTest {
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0)
    private val lists = TaskList.Defaults + TaskList(id = 7, name = "Side Project", color = 4, areaCode = ListArea.Work.code)
    private fun p(s: String, ignored: Set<String> = emptySet()) = QuickParse.parse(s, now, ignored, lists)

    @Test fun hashWorkAssignsTheWorkList() {
        val r = p("Send the deck #work")
        assertEquals(TaskList.WORK, r.list?.id)
        assertEquals("Send the deck", r.title)
        assertEquals(listOf(Kind.List), r.tokens.map { it.kind })
        assertEquals("Work", r.tokens.single().label)
        assertTrue(r.tags.isEmpty())
    }

    @Test fun listNamesMatchIgnoringCaseAndSpaces() {
        assertEquals(7L, p("Fix the build #SideProject").list?.id)
        assertEquals(7L, p("Fix the build #sideproject").list?.id)
        assertEquals(TaskList.PERSONAL, p("#Personal call mum").list?.id)
    }

    @Test fun unknownHashBecomesATag() {
        val r = p("Buy lights #diwali")
        assertNull(r.list)
        assertEquals(listOf("diwali"), r.tags)
        assertEquals("Buy lights", r.title)
        assertEquals(Kind.Tag, r.tokens.single().kind)
        assertEquals("#diwali", r.tokens.single().label)
    }

    @Test fun plusTagsAndMixWithOtherTokens() {
        val r = p("Plan offsite tomorrow at 4pm #work +Q4 +offsite !high")
        assertEquals(TaskList.WORK, r.list?.id)
        assertEquals(listOf("q4", "offsite"), r.tags)
        assertEquals(now.toLocalDate().plusDays(1), r.dueDay)
        assertEquals(16 * 60, r.dueMinute)
        assertEquals(Priority.High, r.priority)
        assertEquals("Plan offsite", r.title)
    }

    @Test fun notTags() {
        // "+1d", phone numbers, C#, and "#1" stay text.
        for (s in listOf("Remind +1d", "Call +91 98450 12345", "Learn C# basics", "Fix issue #1")) {
            val r = p(s)
            assertTrue(s, r.tags.isEmpty())
            assertNull(s, r.list)
        }
    }

    @Test fun oneListPerTaskTheSecondIsATag() {
        val r = p("Expense report #work #personal")
        assertEquals(TaskList.WORK, r.list?.id)
        assertEquals(listOf("personal"), r.tags)
    }

    @Test fun hashTodayIsATagNotADate() {
        val r = p("Ideas #today")
        assertNull(r.dueDay)
        assertEquals(listOf("today"), r.tags)
    }

    @Test fun duplicateTagsCollapse() {
        assertEquals(listOf("home"), p("Fix tap +home #home").tags)
    }

    @Test fun tappingTheChipKeepsTheWords() {
        val key = p("Send the deck #work").tokens.single().key
        val r = p("Send the deck #work", ignored = setOf(key))
        assertNull(r.list)
        assertEquals("Send the deck #work", r.title)
        val tagKey = p("Buy lights #diwali").tokens.single().key
        assertEquals("Buy lights #diwali", p("Buy lights #diwali", ignored = setOf(tagKey)).title)
    }

    @Test fun withoutKnownListsEveryHashIsATag() {
        val r = QuickParse.parse("Send the deck #work", now)
        assertNull(r.list)
        assertEquals(listOf("work"), r.tags)
    }
}
