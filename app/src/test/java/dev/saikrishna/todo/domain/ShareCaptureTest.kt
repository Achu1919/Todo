package dev.saikrishna.todo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCaptureTest {
    @Test fun plainOneLine() {
        val d = ShareCapture.parse("Buy milk tomorrow", null)
        assertEquals("Buy milk tomorrow", d.title); assertEquals("", d.notes)
    }

    @Test fun firstLineTitleRestNotes() {
        val d = ShareCapture.parse("\n  Plan the offsite\nVenue: Coorg\nBudget 2L\n", null)
        assertEquals("Plan the offsite", d.title)
        assertEquals("Venue: Coorg\nBudget 2L", d.notes)
    }

    @Test fun linkOnlyGetsReadHostTitleAndKeepsLink() {
        val d = ShareCapture.parse("https://www.example.com/articles/42?ref=x", null)
        assertEquals("Read example.com", d.title)
        assertEquals("https://www.example.com/articles/42?ref=x", d.notes)
    }

    @Test fun subjectWithLinkFromABrowser() {
        val d = ShareCapture.parse("https://blog.dev/compose-tips", "10 Compose tips")
        assertEquals("10 Compose tips", d.title)
        assertEquals("https://blog.dev/compose-tips", d.notes)
        assertEquals(listOf("https://blog.dev/compose-tips"), d.urls)
    }

    @Test fun urlInsideTextMovesToNotesOnce() {
        val d = ShareCapture.parse("Check this out https://a.io/x. Really good\nhttps://a.io/x", null)
        assertEquals("Check this out. Really good", d.title)
        assertEquals("https://a.io/x", d.notes)
    }

    @Test fun subjectAndBodyLikeAnEmail() {
        val d = ShareCapture.parse("Hi,\nPlease send the invoice by Friday.\nThanks", "Invoice for October")
        assertEquals("Invoice for October", d.title)
        assertEquals("Hi,\nPlease send the invoice by Friday.\nThanks", d.notes)
    }

    @Test fun longFirstLineIsCutAtAWordAndKeptInFull() {
        val long = "word ".repeat(40).trim() // 199 chars
        val d = ShareCapture.parse(long + "\nsecond", null)
        assertTrue(d.title.length <= ShareCapture.MaxTitle + 1)
        assertTrue(d.title.endsWith("…"))
        assertTrue(!d.title.contains("wor…"))
        assertEquals("$long\n\nsecond", d.notes)
    }

    @Test fun emptyShare() {
        val d = ShareCapture.parse("   ", "  ")
        assertEquals("", d.title); assertEquals("", d.notes)
    }

    @Test fun trailingPunctuationNotPartOfUrl() {
        assertEquals(listOf("https://x.dev/a"), ShareCapture.urls("see (https://x.dev/a)."))
    }
}
