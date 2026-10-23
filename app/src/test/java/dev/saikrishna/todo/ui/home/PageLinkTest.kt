package dev.saikrishna.todo.ui.home

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.13 page deep links (widgets, notifications): every page round-trips; junk is ignored. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PageLinkTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    @Test fun everyPageRoundTrips() {
        for (f in Filter.entries) assertEquals(f, PageLink.from(PageLink.intent(ctx, f)))
        assertEquals("home", PageLink.name(Filter.Overview))
        assertEquals(Filter.Overview, PageLink.parse("Overview"))
        assertEquals(Filter.Recurring, PageLink.parse("RECURRING"))
    }

    @Test fun uriAloneAndJunk() {
        assertEquals(Filter.Calendar, PageLink.from(Intent(Intent.ACTION_VIEW, "todo://page/calendar".toUri())))
        assertNull(PageLink.from(Intent(Intent.ACTION_VIEW, "todo://task/calendar".toUri())))
        assertNull(PageLink.from(Intent().putExtra(PageLink.EXTRA, "nowhere")))
        assertNull(PageLink.from(null))
        assertNull(PageLink.parse(null))
    }
}
