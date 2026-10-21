package dev.saikrishna.todo.ui.nav

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect as CRect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import dev.saikrishna.todo.MainActivity
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.NewTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Frame-accurate push/pop checks on the real MainActivity (real theme/window background, real
 * ViewModel, Room, Haze). The clock is paused and stepped one 16 ms frame at a time; every frame
 * is captured as a bitmap and checked:
 *  (i)   Home is composed, placed and visibly drawn on every frame of a pop;
 *  (ii)  no black band in the top 10 % on any frame of push or pop;
 *  (iii) once the motion has reached its end values, every remaining frame equals the settled
 *        frame (no end snap: blur swap, insets, scale/offset mismatch);
 *  (iv)  Home is never disposed during push + pop.
 * Filmstrips (every 3rd frame) are written to out/shots/transition-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = TodoApp::class)
class TransitionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val report = StringBuilder()

    @Before fun seed() {
        val app = rule.activity.application as TodoApp
        runBlocking {
            listOf("Renew passport photos", "Call the bank about the card", "Draft the Q4 plan", "Pick up dry cleaning", "Water the plants")
                .forEach { app.repository.add(NewTask(it)) }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("screen-Home").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        NavProbe.reset()
    }

    // ---- frame helpers ---------------------------------------------------------------------
    private fun frame(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    private fun bounds(tag: String): CRect? =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun lum(p: Int): Float {
        val r = (p shr 16) and 0xff; val g = (p shr 8) and 0xff; val b = p and 0xff
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }

    /** Rows in the top 10 % where more than half of the pixels are black or not drawn at all. */
    private fun blackRows(b: Bitmap): Int {
        val h = b.height / 10
        val row = IntArray(b.width)
        var bad = 0
        for (y in 0 until h) {
            b.getPixels(row, 0, b.width, 0, y, b.width, 1)
            val dark = row.count { p -> (p ushr 24) < 250 || lum(p) < 0.05f }
            if (dark > b.width / 2) bad++
        }
        return bad
    }

    /** Ink pixels (text, icons) inside [x0, x1): a drawn Home has many, a blank backdrop none. */
    private fun inkPixels(b: Bitmap, x0: Int, x1: Int): Int {
        if (x1 - x0 < 1) return 0
        var n = 0
        val row = IntArray(x1 - x0)
        for (y in b.height / 8 until b.height * 7 / 8 step 2) {
            b.getPixels(row, 0, x1 - x0, x0, y, x1 - x0, 1)
            n += row.count { lum(it) < 0.35f }
        }
        return n
    }

    private var worstAt = ""

    /** Largest mean |Δ| (0–255) over an 8 × 8 grid of regions ([worstAt] names the region). */
    private fun regionDiff(a: Bitmap, b: Bitmap, skipTopRows: Int = 0): Float {
        val gw = a.width / 8; val gh = a.height / 8
        var worst = 0f
        val ra = IntArray(gw); val rb = IntArray(gw)
        for (gy in skipTopRows until 8) for (gx in 0 until 8) {
            var sum = 0L
            for (y in gy * gh until (gy + 1) * gh) {
                a.getPixels(ra, 0, gw, gx * gw, y, gw, 1)
                b.getPixels(rb, 0, gw, gx * gw, y, gw, 1)
                for (i in 0 until gw) {
                    val p = ra[i]; val q = rb[i]
                    sum += abs(((p shr 16) and 0xff) - ((q shr 16) and 0xff)) +
                        abs(((p shr 8) and 0xff) - ((q shr 8) and 0xff)) + abs((p and 0xff) - (q and 0xff))
                }
            }
            val v = sum / (3f * gw * gh)
            if (v > worst) { worst = v; worstAt = "row $gy col $gx" }
        }
        return worst
    }

    private fun filmstrip(name: String, frames: List<Bitmap>) {
        val picks = frames.filterIndexed { i, _ -> i % 3 == 0 }.take(30)
        val tw = frames[0].width / 4; val th = frames[0].height / 4
        val cols = minOf(10, picks.size); val rows = (picks.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * (tw + 4), rows * (th + 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.MAGENTA) // gutters; never confused with app pixels
        picks.forEachIndexed { i, f ->
            val x = (i % cols) * (tw + 4); val y = (i / cols) * (th + 4)
            c.drawBitmap(f, null, Rect(x, y, x + tw, y + th), null)
        }
        FileOutputStream(File(dir, "$name.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Run(val frames: List<Bitmap>, val homeBounds: List<CRect?>, val topBounds: List<CRect?>, val firstFrameMs: Double)

    /** Steps [n] frames after [trigger], capturing each. */
    private fun record(n: Int, topTag: String, trigger: () -> Unit, during: (Int) -> Unit = {}): Run {
        rule.mainClock.autoAdvance = false
        trigger()
        val frames = ArrayList<Bitmap>(); val hb = ArrayList<CRect?>(); val tb = ArrayList<CRect?>()
        var firstMs = 0.0
        for (i in 0 until n) {
            during(i)
            val t0 = System.nanoTime()
            rule.mainClock.advanceTimeByFrame()
            if (i == 0) firstMs = (System.nanoTime() - t0) / 1e6
            frames += frame(); hb += bounds("screen-Home"); tb += bounds(topTag)
        }
        return Run(frames, hb, tb, firstMs)
    }

    private fun settled(): Bitmap {
        rule.mainClock.advanceTimeBy(3_000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        return frame()
    }

    private fun push(screen: String, tag: String): Run {
        val run = record(50, tag, { rule.onNodeWithContentDescription(screen).performSemanticsAction(SemanticsActions.OnClick) })
        settled()
        return run
    }

    private val failures = ArrayList<String>()
    private fun check(ok: Boolean, msg: String) { if (!ok) failures += msg }

    /** Checks (i)–(iii) for a pop and returns a summary line. */
    private fun checkPop(name: String, run: Run, settled: Bitmap): String {
        val w = settled.width
        // (i) Home composed + placed every frame, and visibly drawn wherever it is exposed.
        val missing = run.homeBounds.indices.filter { i -> run.homeBounds[i].let { it == null || it.width <= 0f } }
        check(missing.isEmpty(), "$name: Home not composed/placed at frames $missing")
        var blankFrames = 0
        run.frames.forEachIndexed { i, f ->
            val exposedTo = (run.topBounds[i]?.left ?: w.toFloat()).toInt().coerceIn(0, w)
            if (exposedTo > w / 2 && inkPixels(f, 0, exposedTo) == 0) blankFrames++
        }
        check(blankFrames == 0, "$name: $blankFrames frames where the exposed Home area was blank")
        // (ii) no black band.
        val black = run.frames.map(::blackRows)
        val blackFrames = black.count { it > 0 }
        check(blackFrames == 0, "$name: $blackFrames frames with a black band in the top 10 % (rows per frame $black)")
        // (iii) end snap: from the first frame where Home is at its end bounds and the leaving
        // screen is gone, every frame must equal the settled frame.
        val endIdx = run.homeBounds.indices.indexOfFirst { i ->
            val b = run.homeBounds[i]; val t = run.topBounds[i]
            b != null && abs(b.left) < 0.5f && abs(b.width - w) < 0.5f && (t == null || t.left >= w - 0.5f)
        }.let { if (it < 0) run.frames.lastIndex else it }
        val tail = (endIdx..run.frames.lastIndex).map { regionDiff(run.frames[it], settled) }
        val maxTail = tail.maxOrNull() ?: 0f
        check(maxTail < 1.0f, "$name: end snap, frames after rest differ from settled by ${tail.map { "%.1f".format(it) }}")
        val steps = (1 until endIdx + 1).map { regionDiff(run.frames[it - 1], run.frames[it]) }
        return "$name: frames=${run.frames.size}, at rest from frame $endIdx (${endIdx * 16} ms); " +
            "Home missing frames=${missing.size} ${missing.take(8)}; blank frames=$blankFrames; black-band frames=$blackFrames; " +
            "max diff vs settled after rest=${"%.2f".format(maxTail)}; last motion step=${"%.2f".format(steps.lastOrNull() ?: 0f)}; " +
            "first pop frame ${"%.1f".format(run.firstFrameMs)} ms (Robolectric wall time)"
    }

    /**
     * Search has content that legitimately keeps moving after the slide settles: the field
     * autofocuses (cursor) and the "Search" hint finishes its own rise ~160 ms later. Its top three
     * eighths are therefore excluded from the push end-state comparison (pops are checked in full).
     */
    private fun pushCheck(name: String, run: Run, settled: Bitmap, skipTopRows: Int = 0): String {
        val black = run.frames.map(::blackRows)
        check(black.count { it > 0 } == 0, "$name: frames with a black band in the top 10 % (rows per frame $black)")
        val endIdx = run.topBounds.indexOfFirst { b -> b != null && abs(b.left) < 0.5f }.let { if (it < 0) run.frames.lastIndex else it }
        val tail = (endIdx..run.frames.lastIndex).map { regionDiff(run.frames[it], settled, skipTopRows) }
        val worstTail = tail.indices.maxByOrNull { tail[it] }?.let { regionDiff(run.frames[endIdx + it], settled, skipTopRows); worstAt } ?: ""
        check((tail.maxOrNull() ?: 0f) < 1.0f, "$name: end snap after push: $tail (worst region: $worstTail)")
        return "$name: frames=${run.frames.size}, pushed screen at rest from frame $endIdx (${endIdx * 16} ms), " +
            "max diff vs settled after rest=${"%.2f".format(tail.maxOrNull() ?: 0f)}, black-band frames=${black.count { it > 0 }}, " +
            "first push frame ${"%.1f".format(run.firstFrameMs)} ms"
    }

    private fun cycle(screen: String, viaGesture: Boolean) {
        val tag = "screen-$screen"
        val pushRun = record(50, tag, { rule.onNodeWithContentDescription(screen).performSemanticsAction(SemanticsActions.OnClick) })
        val pushed = settled()
        report.appendLine(pushCheck("push-$screen", pushRun, pushed, skipTopRows = if (screen == "Search") 3 else 0))
        filmstrip("transition-push-${screen.lowercase()}", pushRun.frames)

        val popRun = if (!viaGesture) {
            record(60, tag, { rule.onAllNodesWithTag(tag).fetchSemanticsNodes() ; rule.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick) })
        } else {
            val dispatcher = rule.activity.onBackPressedDispatcher
            // Predictive back: start, drag to 35 % over 12 frames, release (commit).
            record(72, tag, {
                rule.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 900f, 0f, BackEventCompat.EDGE_LEFT)) }
            }) { i ->
                if (i in 1..12) rule.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(10f + i * 30f, 900f, i * 0.35f / 12f, BackEventCompat.EDGE_LEFT)) }
                if (i == 13) rule.runOnUiThread { dispatcher.onBackPressed() }
            }
        }
        val home = settled()
        val name = "pop-$screen-" + if (viaGesture) "gesture" else "arrow"
        filmstrip("transition-${name.lowercase()}", popRun.frames)
        report.appendLine(checkPop(name, if (viaGesture) Run(popRun.frames.drop(13), popRun.homeBounds.drop(13), popRun.topBounds.drop(13), popRun.firstFrameMs) else popRun, home))
        // (iv)
        report.appendLine(
            "$name: Home enters=${NavProbe.enters[Screen.Home] ?: 0} disposals=${NavProbe.disposals[Screen.Home] ?: 0} " +
                "probe recompositions=${NavProbe.recompositions[Screen.Home] ?: 0}",
        )
        check((NavProbe.disposals[Screen.Home] ?: 0) == 0, "$name: Home disposed during push+pop")
        if (failures.isNotEmpty()) report.appendLine("FAILURES:\n" + failures.joinToString("\n"))
        println(report)
        File(dir, "transition-report-$name.txt").writeText(report.toString())
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Three levels deep and back: every screen pops through the same path; Home survives. */
    @Test fun licencesAndBackTwice() {
        rule.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithText("Licences").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_500)
        assertTrue(rule.onAllNodesWithTag("screen-Licenses").fetchSemanticsNodes().isNotEmpty())
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onAllNodesWithContentDescription("Back").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("screen-Settings").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("screen-Licenses").fetchSemanticsNodes().isEmpty())
        assertEquals(0, NavProbe.disposals[Screen.Home] ?: 0)
        assertEquals(1, NavProbe.disposals[Screen.Settings] ?: 0)
        assertEquals(1, NavProbe.disposals[Screen.Licenses] ?: 0)
    }

    /** Reduced motion swaps the slide for a crossfade; Home is still there on every frame. */
    @Test fun reducedMotionArrowPop() {
        // Through the real UI: open Settings and switch Reduce motion on.
        rule.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settled()
        rule.mainClock.autoAdvance = true
        rule.onAllNodes(androidx.compose.ui.test.isToggleable()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.isToggleable()).fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) { null } == androidx.compose.ui.state.ToggleableState.On
        }
        settled()
        val run = record(40, "screen-Settings", { rule.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick) })
        val home = settled()
        filmstrip("transition-pop-settings-arrow-reduced", run.frames)
        report.appendLine(checkPop("pop-Settings-arrow-reduced", run, home))
        check((NavProbe.disposals[Screen.Home] ?: 0) == 0, "reduced: Home disposed")
        File(dir, "transition-report-pop-Settings-arrow-reduced.txt").writeText(report.toString())
        // Settings persist for the whole test process (DataStore is process-wide): switch it back off.
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_000)
        rule.onAllNodes(androidx.compose.ui.test.isToggleable()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.isToggleable()).fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) { null } == androidx.compose.ui.state.ToggleableState.Off
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun settingsArrow() = cycle("Settings", viaGesture = false)
    @Test fun settingsGesture() = cycle("Settings", viaGesture = true)
    @Test fun searchArrow() = cycle("Search", viaGesture = false)
    @Test fun searchGesture() = cycle("Search", viaGesture = true)
}
