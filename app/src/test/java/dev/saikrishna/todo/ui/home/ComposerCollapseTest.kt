package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect as ARect
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
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
import kotlin.math.abs

/**
 * v0.7.3: the composer collapses into a centred glass pill. Real HomeScreen, paused clock, one
 * 16 ms frame at a time, every frame drawn (captured).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ComposerCollapseTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val density = 420f / 160f
    private val halfDp = 0.5f * density

    @After fun tearDown() { HomeTestHooks.chrome = null; db.close() }

    private fun start(reduced: Boolean = false) {
        runBlocking {
            db.taskDao().insertAll((1..30).map { Task(id = it.toLong(), title = "Task number $it", createdAt = it.toLong()) })
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduced) {
                AppTheme(darkTheme = false) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    private val chrome get() = HomeTestHooks.chrome!!

    private fun surfaces(): List<Rect> =
        rule.onAllNodes(hasTestTag("composer-surface"), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    private class F(val bounds: List<Rect>, val k: Float, val bmp: Bitmap)

    private fun step(): F {
        rule.mainClock.advanceTimeByFrame()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        return F(surfaces(), chrome.progress.value, bmp)
    }

    private fun record(trigger: () -> Unit, target: Float): List<F> {
        rule.mainClock.autoAdvance = false
        trigger()
        val out = ArrayList<F>()
        var settledAt = -1
        while (out.size < 80) {
            val f = step(); out += f
            if (settledAt < 0 && !chrome.progress.isRunning && f.k == target) settledAt = out.lastIndex
            if (settledAt >= 0 && out.size > settledAt + 3) break
        }
        return out
    }

    private fun filmstrip(name: String, frames: List<F>, crop: Rect) {
        val top = (crop.top - 40 * density).toInt().coerceAtLeast(0)
        val bottom = (crop.bottom + 8 * density).toInt().coerceAtMost(frames[0].bmp.height)
        val w = frames[0].bmp.width / 2; val h = (bottom - top) / 2
        val cols = 4; val rows = (frames.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * (w + 4), rows * (h + 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(android.graphics.Color.MAGENTA)
        frames.forEachIndexed { i, f ->
            val x = (i % cols) * (w + 4); val y = (i / cols) * (h + 4)
            c.drawBitmap(f.bmp, ARect(0, top, f.bmp.width, bottom), ARect(x, y, x + w, y + h), null)
        }
        FileOutputStream(File(dir, "$name.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun diff(a: Bitmap, b: Bitmap): Float {
        var sum = 0L
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            sum += abs((p shr 16 and 255) - (q shr 16 and 255)) + abs((p shr 8 and 255) - (q shr 8 and 255)) + abs((p and 255) - (q and 255))
        }
        return sum / (3f * a.width * a.height)
    }

    private fun check(name: String, frames: List<F>, fails: MutableList<String>): Float {
        val cx0 = frames.first().bounds.single().center.x
        frames.forEachIndexed { i, f ->
            if (f.bounds.size != 1) fails += "$name frame $i: ${f.bounds.size} composer surfaces"
            else if (abs(f.bounds[0].center.x - cx0) > halfDp) fails += "$name frame $i: centreX ${f.bounds[0].center.x} vs $cx0"
            if (f.bounds.size == 1 && abs(f.bounds[0].height - frames.first().bounds[0].height) > halfDp) fails += "$name frame $i: height changed"
        }
        // Last animated frame (progress reached the target) == settled frame, pixel for pixel.
        val landing = frames.indexOfFirst { it.k == frames.last().k }
        val d = diff(frames[landing].bmp, frames.last().bmp)
        if (d != 0f) fails += "$name landing frame $landing differs from settled by $d"
        println("COLLAPSE $name: ${frames.size} frames, landing frame $landing (${(landing + 1) * 16} ms), widths ${frames.map { "%.0f".format(it.bounds.firstOrNull()?.width?.div(density) ?: -1f) }}, landing diff $d")
        return d
    }

    @Test fun collapseAndExpandAreOneSymmetricSurface() {
        start()
        val full = surfaces().single()
        val collapse = record({ chrome.collapseForTest() }, 1f)
        rule.mainClock.advanceTimeBy(500)
        val pill = surfaces().single()
        val expand = record({ chrome.expand() }, 0f)
        val fails = ArrayList<String>()
        check("collapse", collapse, fails)
        check("expand", expand, fails)
        assertEquals("pill width", 120f, pill.width / density, 0.5f)
        assertEquals("pill centre", full.center.x, pill.center.x, halfDp)
        assertEquals("pill height (dp)", 48f, pill.height / density, 0.5f)
        filmstrip("composer-collapse-filmstrip", collapse, full)
        filmstrip("composer-expand-filmstrip", expand, full)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    @Test fun realScrollCollapsesAndUpwardScrollOrTapExpands() {
        start()
        val list = rule.onNode(hasScrollToIndexAction())
        list.performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 200f, durationMillis = 300) }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertTrue("collapsed after scrolling down", chrome.collapsed)
        assertEquals(120f, surfaces().single().width / density, 0.5f)
        list.performTouchInput { swipeDown(startY = centerY - 100f, endY = centerY + 100f, durationMillis = 300) }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertFalse("expanded after scrolling up", chrome.collapsed)
        // Collapse again, then the pill's "Add a task" action expands and focuses the field.
        list.performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 200f, durationMillis = 300) }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertTrue(chrome.collapsed)
        rule.onNode(hasTestTag("composer-surface"), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertFalse(chrome.collapsed)
        assertTrue("field focused", chrome.composerBusy)
        // v0.7.4: starting to scroll ends editing; with a draft in the field it still never collapses.
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Draft")
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        list.performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 200f, durationMillis = 300) }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertFalse("scroll cleared focus", chrome.focused)
        assertFalse("draft keeps it expanded", chrome.collapsed)
    }

    @Test fun pageSwitchExpands() {
        start()
        chrome.collapseForTest(); rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertTrue(chrome.collapsed)
        rule.onNode(androidx.compose.ui.test.hasText("Upcoming") and androidx.compose.ui.test.hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertFalse(chrome.collapsed)
        assertEquals(0f, chrome.progress.value)
    }

    @Test fun reducedMotionIsACrossfadeWithoutBounds() {
        start(reduced = true)
        val full = surfaces().single()
        val frames = record({ chrome.collapseForTest() }, 1f)
        val widths = frames.map { it.bounds.single().width }.distinct()
        // Only the two end states, never an in-between width; done in <= ~150 ms + a frame.
        assertTrue("widths $widths", widths.all { abs(it - full.width) < 1f || abs(it / density - 120f) < 0.5f })
        val done = frames.indexOfFirst { it.k == 1f && !chrome.fade.isRunning && chrome.fade.value == 1f }
        println("REDUCED: widths ${widths.map { it / density }}, frames ${frames.size}")
        assertTrue(frames.size <= 16)
    }
}
