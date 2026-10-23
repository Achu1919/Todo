package dev.saikrishna.todo.capture

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.ui.capture.AddTaskTileService
import dev.saikrishna.todo.ui.capture.CaptureActivity
import dev.saikrishna.todo.ui.capture.CapturePreset
import dev.saikrishna.todo.ui.capture.applyDueToday
import dev.saikrishna.todo.reminders.PlanReminder
import dev.saikrishna.todo.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** v0.12 widget presets: URIs, extras round trip, tile/share defaults, distinct PendingIntents, due today. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CapturePresetTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    @Test fun uriCarriesOnlyTheSetParts() {
        assertEquals("todo://capture", CapturePreset().uri().toString())
        assertEquals("todo://capture?list=2", CapturePreset(listId = 2).uri().toString())
        assertEquals("todo://capture?due=today", CapturePreset(dueToday = true).uri().toString())
        assertEquals("todo://capture?voice=1", CapturePreset(voice = true).uri().toString())
        assertEquals("todo://capture?list=7&due=today&voice=1", CapturePreset(7, dueToday = true, voice = true).uri().toString())
    }

    @Test fun roundTripThroughTheCaptureIntent() {
        for (p in listOf(CapturePreset(listId = 2), CapturePreset(dueToday = true), CapturePreset(voice = true), CapturePreset(3, true, true))) {
            val i = AddTaskTileService.captureIntent(ctx, p)
            assertEquals(CaptureActivity::class.java.name, i.component?.className)
            assertEquals(AddTaskTileService.ACTION_CAPTURE, i.action)
            assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertEquals(p.uri(), i.data)
            assertEquals(p, CapturePreset.of(i))
        }
    }

    @Test fun defaultsStayPlain() {
        val tile = AddTaskTileService.captureIntent(ctx)
        assertNull(tile.data)
        assertFalse(tile.hasExtra(CapturePreset.EXTRA_LIST))
        assertTrue(CapturePreset.of(tile).isDefault)
        assertTrue(CapturePreset.of(null).isDefault)
        // A share never takes a preset, even with stray extras.
        val send = Intent(Intent.ACTION_SEND).putExtra(CapturePreset.EXTRA_VOICE, true).putExtra(CapturePreset.EXTRA_LIST, 2L)
        assertTrue(CapturePreset.of(send).isDefault)
        // Bad list ids are ignored.
        assertNull(CapturePreset.of(Intent().putExtra(CapturePreset.EXTRA_LIST, 0L)).listId)
        assertNull(CapturePreset.of(Intent().putExtra(CapturePreset.EXTRA_LIST, -4L)).listId)
    }

    @Test fun eachPresetIsADistinctPendingIntent() {
        fun pi(p: CapturePreset) = PendingIntent.getActivity(ctx, 0, AddTaskTileService.captureIntent(ctx, p), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val presets = listOf(CapturePreset(), CapturePreset(listId = 1), CapturePreset(listId = 2), CapturePreset(dueToday = true), CapturePreset(voice = true))
        val all = presets.map { pi(it) }
        assertEquals(presets.size, all.toSet().size)
        assertEquals(pi(CapturePreset(listId = 2)), pi(CapturePreset(listId = 2)))
        assertNotEquals(pi(CapturePreset(listId = 1)), pi(CapturePreset(listId = 2)))
    }

    @Test fun planIntentOpensTheAppOnPlan() {
        val i = PlanReminder.planActivityIntent(ctx)
        assertEquals(MainActivity::class.java.name, i.component?.className)
        assertTrue(i.getBooleanExtra(PlanReminder.EXTRA_OPEN_PLAN, false))
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun dueTodayUnlessTheTextNamesADay() {
        val now = LocalDateTime.of(2026, 10, 23, 9, 0)
        val today = now.toLocalDate()
        val plain = QuickParse.parse("Call mum", now, lists = TaskList.Defaults)
        assertEquals(today, applyDueToday(plain, dueToday = true, today).dueDay)
        assertNull(applyDueToday(plain, dueToday = false, today).dueDay)
        val named = QuickParse.parse("Call mum tomorrow", now, lists = TaskList.Defaults)
        assertEquals(LocalDate.of(2026, 10, 24), applyDueToday(named, dueToday = true, today).dueDay)
        val timed = QuickParse.parse("Call mum at 6pm", now, lists = TaskList.Defaults)
        val t = applyDueToday(timed, dueToday = true, today)
        assertEquals(today, t.dueDay); assertEquals(18 * 60, t.dueMinute)
    }
}
