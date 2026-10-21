package dev.saikrishna.todo.capture

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.ui.capture.AddTaskTileService
import dev.saikrishna.todo.ui.capture.CaptureActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CaptureIntentTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    @Test fun shareTextResolvesToTheCaptureCard() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(ctx.packageName)
        val hits = ctx.packageManager.queryIntentActivities(send, 0)
        assertEquals(listOf(CaptureActivity::class.java.name), hits.map { it.activityInfo.name })
    }

    @Test fun draftFromSubjectAndLink() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Great article on gardening")
            .putExtra(Intent.EXTRA_TEXT, "https://blog.example.org/soil")
        val d = CaptureActivity.draftOf(i)
        assertEquals("Great article on gardening", d.title)
        assertTrue(d.notes.contains("https://blog.example.org/soil"))
    }

    @Test fun tileIntentIsAnEmptyCardInItsOwnTask() {
        val i = AddTaskTileService.captureIntent(ctx)
        assertEquals("", CaptureActivity.draftOf(i).title)
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(CaptureActivity::class.java.name, i.component?.className)
    }
}
