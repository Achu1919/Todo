package dev.saikrishna.todo.ui.motion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect as ARect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.MainActivity
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.NewTask
import dev.saikrishna.todo.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import dev.saikrishna.todo.ui.motion.LaunchEntrance.Slot

/** v0.10 cold-start entrance: budget, once-only, input never blocked, filmstrip. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = TodoApp::class)
class LaunchEntranceTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<TodoApp>()
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun tearDown() {
        rule.mainClock.autoAdvance = true
        scenario?.close()
        runBlocking { app.settings.setTheme(ThemeMode.System); app.settings.setReduceMotion(false) }
    }

    @Test fun mathFitsTheBudget() {
        // Every slot starts hidden, is settled by 450 ms, and the last row is ~settled before that.
        for (slot in listOf(Slot.Header, Slot.Bottom, Slot.Row(0), Slot.Row(7), Slot.Row(30))) {
            assertEquals(0f, LaunchEntrance.progressAt(0f, slot, false))
            assertEquals(1f, LaunchEntrance.progressAt(LaunchEntrance.TotalMs.toFloat(), slot, false))
        }
        assertTrue(LaunchEntrance.TotalMs < 450 + 1)
        assertTrue(LaunchEntrance.progressAt(440f, Slot.Row(7), false) > 0.985f)
        // Stagger 30-40 ms, capped at 8 rows.
        val step = LaunchEntrance.delayOf(Slot.Row(1)) - LaunchEntrance.delayOf(Slot.Row(0))
        assertTrue(step in 30..40)
        assertEquals(LaunchEntrance.delayOf(Slot.Row(7)), LaunchEntrance.delayOf(Slot.Row(20)))
        // Row 2 trails row 1 mid-flight.
        assertTrue(LaunchEntrance.progressAt(80f, Slot.Row(1), false) > LaunchEntrance.progressAt(80f, Slot.Row(2), false))
        // Reduced motion: one 150 ms fade, the same for every slot.
        assertEquals(0.5f, LaunchEntrance.progressAt(75f, Slot.Row(5), true), 0.001f)
        assertEquals(1f, LaunchEntrance.progressAt(150f, Slot.Header, true))
    }

    private fun seedAndLaunch(reduce: Boolean = false): MainActivity {
        runBlocking {
            app.settings.setTheme(ThemeMode.Light)
            app.settings.setReduceMotion(reduce)
            listOf(
                "Renew passport photos", "Call the bank about the card", "Draft the Q4 plan", "Book the dentist",
                "Water the plants", "Pay the electricity bill", "Send the invoice", "Pick up the dry cleaning",
                "Back up the laptop", "Order printer ink",
            ).forEach { app.repository.add(NewTask(it)) }
            app.settings.settings.first()
        }
        rule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var a: MainActivity
        scenario!!.onActivity { a = it }
        return a
    }

    /** Steps frames until the entrance clock has started (first data frame). */
    private fun awaitStart(a: MainActivity) {
        var n = 0
        while (!started(a) && n++ < 400) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle(); Thread.sleep(2) }
        assertTrue("entrance never started", started(a))
    }
    private fun started(a: MainActivity) = a.entrance.progress(Slot.Header) > 0f || a.entrance.settled

    private fun frame(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    private fun diff(a: Bitmap, b: Bitmap): Int {
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return pa.indices.count { pa[it] != pb[it] }
    }

    @Test fun coldStartFilmstripLandsOnTheSettledFrame() {
        val a = seedAndLaunch()
        awaitStart(a)
        assertFalse(a.entrance.settled)
        val frames = ArrayList<Bitmap>()
        // 0..480 ms every 32 ms.
        repeat(16) { frames += frame(); rule.mainClock.advanceTimeBy(32); rule.waitForIdle() }
        assertTrue(a.entrance.settled)
        val last = frame()
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        val settled = frame()
        assertEquals("last strip frame != settled", 0, diff(last, settled))
        assertTrue("first frame should differ (rows not in yet)", diff(frames[0], settled) > 10_000)
        frames += last
        // Filmstrip: 1/4 size, 6 per row.
        val tw = settled.width / 4; val th = settled.height / 4; val cols = 6
        val rows = (frames.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * (tw + 4), rows * (th + 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(android.graphics.Color.MAGENTA)
        frames.forEachIndexed { i, b ->
            val x = (i % cols) * (tw + 4); val y = (i / cols) * (th + 4)
            c.drawBitmap(b, null, ARect(x, y, x + tw, y + th), null)
        }
        FileOutputStream(File(dir, "launch-filmstrip.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tapsWorkDuringTheEntrance() {
        val a = seedAndLaunch()
        awaitStart(a)
        rule.mainClock.advanceTimeBy(32); rule.waitForIdle()
        assertFalse(a.entrance.settled)
        // The first checkbox, while the rows are still rising: the tap lands (no input gate).
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).onFirst().performClick()
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { runBlocking { app.database.backupDao().tasks().count { it.done } } == 1 }
    }

    @Test fun playsOnlyOnColdStart() {
        val a = seedAndLaunch()
        awaitStart(a)
        rule.mainClock.autoAdvance = true; rule.waitForIdle()
        assertTrue(a.entrance.settled)
        // Configuration change: no replay.
        scenario!!.recreate()
        lateinit var b: MainActivity
        scenario!!.onActivity { b = it }
        assertTrue(b.entrance.settled)
        // Leaving and coming back (a fresh Activity in the same process, e.g. back then relaunch): no replay.
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var c: MainActivity
        scenario!!.onActivity { c = it }
        assertTrue(c.entrance.settled)
    }

    @Test fun reducedMotionIsAShortFade() {
        val a = seedAndLaunch(reduce = true)
        awaitStart(a)
        assertTrue(a.entrance.reduced)
        rule.mainClock.advanceTimeBy(160); rule.waitForIdle()
        assertTrue(a.entrance.settled)
    }
}
