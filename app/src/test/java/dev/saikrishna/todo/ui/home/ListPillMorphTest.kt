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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.panel.PanelProbe
import dev.saikrishna.todo.ui.panel.PanelStyle
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * v0.7.1: the list picker morphs out of and back into the glass list pill. Real HomeScreen,
 * paused clock, one 16 ms frame at a time, every frame drawn (captured), with the panel host's
 * draw-phase probe reporting the capsule rect, corner and content alpha.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ListPillMorphTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val density = 420f / 160f
    private val halfDp = 0.5f * density

    @After fun tearDown() {
        PanelProbe.onDraw = null
        PanelProbe.anchorAlpha.clear()
        HomeTestHooks.menuStyle = null
        db.close()
    }

    private class F(val rect: Rect?, val corner: Float, val content: Float, val p: Float, val pill: Float, val bmp: Bitmap)

    private fun start(style: PanelStyle?) {
        HomeTestHooks.menuStyle = style
        runBlocking {
            db.taskDao().insertAll((1..5).map { Task(id = it.toLong(), title = "Task number $it", createdAt = it.toLong()) })
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    private fun pillBounds(): Rect = rule.onNodeWithTag("list-pill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    // Last values the draw phase reported (a layer that did not change is not re-drawn, so keep them).
    private var rect: Rect? = null; private var corner = 0f; private var content = 0f; private var p = 0f
    private fun step(): F {
        PanelProbe.onDraw = { r, c, a, pr -> rect = r; corner = c; content = a; p = pr }
        rule.mainClock.advanceTimeByFrame()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        // The host (scrim "Close" + capsule) is in the tree only while the panel is drawn.
        val hostUp = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Close")).fetchSemanticsNodes().isNotEmpty()
        if (!hostUp) { rect = null; content = 0f; p = 0f }
        return F(rect, corner, content, p, PanelProbe.anchorAlpha[MenuIds.LIST_PICKER] ?: 1f, bmp)
    }

    /** Frames from the trigger until [done] (inclusive) plus [extra] frames. */
    private fun record(trigger: () -> Unit, extra: Int = 3, max: Int = 90, done: (F) -> Boolean): Pair<List<F>, Int> {
        rule.mainClock.autoAdvance = false
        trigger()
        val out = ArrayList<F>()
        var doneAt = -1
        while (out.size < max) {
            val f = step(); out += f
            if (doneAt < 0 && done(f)) doneAt = out.lastIndex
            if (doneAt >= 0 && out.size > doneAt + extra) break
        }
        return out to doneAt
    }

    private fun open() = record({
        rule.onNode(
            androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasAnyChild(androidx.compose.ui.test.hasTestTag("list-pill")),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
    }) { it.rect != null && it.p >= 1f && it.content >= 1f }

    /** Close: the scrim's semantics click (same as a tap outside). Done = the first frame with no capsule. */
    private fun closePanel() = record({ rule.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick) }, extra = 2) { it.rect == null }

    private fun near(a: Rect, b: Rect) = maxOf(abs(a.left - b.left), abs(a.top - b.top), abs(a.right - b.right), abs(a.bottom - b.bottom))

    private fun filmstrip(name: String, frames: List<F>, pill: Rect) {
        val tw = frames[0].bmp.width / 3; val th = frames[0].bmp.height / 3
        val cols = 8; val rows = (frames.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * (tw + 4), rows * (th + 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(android.graphics.Color.MAGENTA)
        frames.forEachIndexed { i, f ->
            val x = (i % cols) * (tw + 4); val y = (i / cols) * (th + 4)
            c.drawBitmap(f.bmp, null, ARect(x, y, x + tw, y + th), null)
        }
        FileOutputStream(File(dir, "$name.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Mean abs RGB difference (0..255) inside [r]. */
    private fun diff(a: Bitmap, b: Bitmap, r: Rect): Float {
        var sum = 0L; var n = 0
        for (y in r.top.toInt().coerceAtLeast(0) until r.bottom.toInt().coerceAtMost(a.height))
            for (x in r.left.toInt().coerceAtLeast(0) until r.right.toInt().coerceAtMost(a.width)) {
                val p = a.getPixel(x, y); val q = b.getPixel(x, y)
                sum += abs((p shr 16 and 255) - (q shr 16 and 255)) + abs((p shr 8 and 255) - (q shr 8 and 255)) + abs((p and 255) - (q and 255))
                n++
            }
        return if (n == 0) 0f else sum / (3f * n)
    }

    private fun ms(frames: Int) = frames * 16

    @Test fun morphOpensFromAndLandsOnThePill() {
        start(null)
        val pill = pillBounds()
        val (opening, openDone) = open()
        filmstrip("list-picker-open-filmstrip", opening.take(openDone + 1), pill)
        rule.mainClock.advanceTimeBy(500)
        val (closing, closeDone) = closePanel()
        filmstrip("list-picker-close-filmstrip", closing.take(closeDone + 1), pill)

        val fails = ArrayList<String>()
        // OPEN: no frame where neither is drawn; first capsule frame sits exactly on the pill; the
        // pill is hidden from that frame on; content waits for 40 % of the geometry.
        opening.forEachIndexed { i, f -> if (f.rect == null && f.pill < 1f) fails += "open frame $i: neither pill nor capsule" }
        val first = opening.indexOfFirst { it.rect != null }
        val f0 = opening[first]
        if (near(f0.rect!!, pill) > halfDp) fails += "open first capsule ${f0.rect} != pill $pill"
        if (abs(f0.corner - pill.height / 2f) > halfDp) fails += "open first corner ${f0.corner} != ${pill.height / 2f}"
        opening.drop(first).forEachIndexed { i, f -> if (f.pill > 0f) fails += "open frame ${first + i}: pill drawn under the panel" }
        opening.forEach { if (it.rect != null && it.p < 0.4f - 0.05f && it.content > 0f) fails += "content visible at p=${it.p}" }

        // CLOSE: content gone early; last capsule frame == pill bounds (±0.5 dp) and the pill drawn
        // in that same frame; no frame where neither is drawn; landing frame == settled pixels.
        closing.forEachIndexed { i, f -> if (f.rect == null && f.pill < 1f) fails += "close frame $i: neither pill nor capsule" }
        val lastCap = closing.indexOfLast { it.rect != null }
        val land = closing[lastCap]
        val landErr = near(land.rect!!, pill)
        if (landErr > halfDp) fails += "landing capsule ${land.rect} vs pill $pill (err ${landErr / density} dp)"
        if (land.pill < 1f) fails += "pill not drawn on the landing frame"
        // The pill only comes back once the capsule is on it (within 0.5 dp).
        closing.forEachIndexed { i, f ->
            if (f.rect != null && f.pill > 0f && near(f.rect, pill) > halfDp) fails += "close frame $i: pill drawn while the capsule is ${near(f.rect, pill) / density} dp away"
        }
        val contentGone = closing.indexOfFirst { it.content <= 0f }
        val settled = closing.last().bmp
        val pillPad = Rect(pill.left - 4, pill.top - 4, pill.right + 4, pill.bottom + 4)
        val landDiff = diff(land.bmp, settled, pillPad)
        val prevDiff = diff(closing[lastCap - 1].bmp, settled, pillPad)
        if (landDiff > 3f) fails += "landing frame differs from the settled pill by $landDiff"

        println("MORPH open: settle ${ms(openDone + 1)} ms (${openDone + 1} frames), first capsule frame $first, content starts frame ${opening.indexOfFirst { it.content > 0f }}")
        println("MORPH close: last capsule frame $lastCap (${ms(lastCap + 1)} ms), host gone frame $closeDone, content 0 at frame $contentGone (${ms(contentGone + 1)} ms), landing err ${"%.3f".format(landErr / density)} dp, landing vs settled diff $landDiff, previous frame diff $prevDiff")
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
        File(dir, "list-picker-morph-after.txt").writeText("open ${ms(openDone + 1)}\nclose ${ms(lastCap + 1)}\n")
    }

    /** Timing of the v0.7.0 picker (task-panel style + Smooth spring), for the before/after comparison. */
    @Test fun beforeTiming() {
        start(PanelStyle.Card)
        val (_, openDone) = open()
        rule.mainClock.advanceTimeBy(500)
        val (closing, closeDone) = closePanel()
        val lastCap = closing.indexOfLast { it.rect != null }
        println("BEFORE open: settle ${ms(openDone + 1)} ms; close: last capsule frame ${ms(lastCap + 1)} ms (host gone frame $closeDone)")
        File(dir, "list-picker-morph-before.txt").writeText("open ${ms(openDone + 1)}\nclose ${ms(lastCap + 1)}\n")
    }
}
