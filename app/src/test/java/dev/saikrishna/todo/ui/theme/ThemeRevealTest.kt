package dev.saikrishna.todo.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect as ARect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.WindowCompat
import dev.saikrishna.todo.MainActivity
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.NewTask
import dev.saikrishna.todo.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
import kotlin.math.hypot

/**
 * v0.9.1 header theme toggle on the real MainActivity: a circular reveal, frame by frame.
 * Every frame must be made only of settled-old and settled-new pixels (outside the toggle button,
 * whose icon morphs, and a 3 px band on the circle's antialiased edge): no flash, no black, no
 * third state. Frame 0 == the old theme, the landing frame == the settled new theme, the setting
 * persists, the bar icons flip, and the Activity is never recreated.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = TodoApp::class)
class ThemeRevealTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val app get() = rule.activity.application as TodoApp
    private val reveal get() = rule.activity.reveal

    @Before fun seed() {
        runBlocking {
            app.settings.setTheme(ThemeMode.Light)
            app.settings.setReduceMotion(false)
            listOf("Renew passport photos", "Call the bank about the card", "Draft the Q4 plan").forEach { app.repository.add(NewTask(it)) }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("theme-toggle").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
    }

    @After fun reset() {
        rule.mainClock.autoAdvance = true
        runBlocking { app.settings.setTheme(ThemeMode.System); app.settings.setReduceMotion(false) }
    }

    private fun frame(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    private fun px(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    private fun near(p: Int, q: Int, tol: Int = 3) =
        abs((p shr 16 and 255) - (q shr 16 and 255)) <= tol && abs((p shr 8 and 255) - (q shr 8 and 255)) <= tol && abs((p and 255) - (q and 255)) <= tol
    private fun diffCount(a: IntArray, b: IntArray) = a.indices.count { !near(a[it], b[it], 0) }
    private fun toggle(): Rect = rule.onNodeWithTag("theme-toggle").fetchSemanticsNode().boundsInRoot
    private fun lightIcons(): Boolean {
        val a = rule.activity
        return WindowCompat.getInsetsController(a.window, a.window.decorView).isAppearanceLightStatusBars
    }
    private fun stored() = runBlocking { app.settings.settings.first().theme }
    /** DataStore writes run on a real IO thread: wait (real time) for [want]. */
    private fun storedEventually(want: ThemeMode): ThemeMode {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end && stored() != want) { Thread.sleep(20); rule.waitForIdle() }
        return stored()
    }

    private class F(val bmp: Bitmap, val r: Float, val running: Boolean, val lightIcons: Boolean)

    private fun settleIdle() { rule.mainClock.autoAdvance = true; rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle() }

    /** Tap (semantics click) and step frames until the reveal ends (+ [extra]). */
    private fun run(extra: Int = 4, max: Int = 80, trigger: () -> Unit = { click() }): List<F> {
        rule.mainClock.autoAdvance = false
        trigger()
        val out = ArrayList<F>()
        var endAt = -1
        while (out.size < max) {
            rule.mainClock.advanceTimeByFrame()
            out += F(frame(), reveal.radiusPx, reveal.running, lightIcons())
            if (endAt < 0 && out.size > 2 && !reveal.running) endAt = out.lastIndex
            if (endAt >= 0 && out.size > endAt + extra) break
        }
        return out
    }

    private fun click() = rule.onNodeWithTag("theme-toggle").performSemanticsAction(SemanticsActions.OnClick)

    /**
     * Pixels that are neither [a] nor [b] (tolerance 3), outside the button (+6 dp) and outside a
     * 3 px band on the circle edge of radius [r] around [c].
     */
    private fun foreign(f: IntArray, a: IntArray, b: IntArray, w: Int, button: Rect, cx: Float, cy: Float, r: Float?, earlier: List<Float> = emptyList()): Int {
        val pad = 6f * 420f / 160f
        var n = 0
        for (i in f.indices) {
            if (near(f[i], a[i]) || near(f[i], b[i])) continue
            val x = i % w + 0.5f; val y = i / w + 0.5f
            if (x in button.left - pad..button.right + pad && y in button.top - pad..button.bottom + pad) continue
            if (r != null && abs(hypot(x - cx, y - cy) - r) <= 3f) continue
            // Retarget: the previous reveal's antialiased edge is part of the new snapshot.
            if (earlier.any { abs(hypot(x - cx, y - cy) - it) <= 3f }) continue
            n++
        }
        return n
    }

    private fun filmstrip(name: String, groups: List<List<Bitmap>>) {
        val tw = groups[0][0].width / 4; val th = groups[0][0].height / 4
        val cols = 10
        val rowsPer = groups.map { (it.size + cols - 1) / cols }
        val out = Bitmap.createBitmap(cols * (tw + 4), rowsPer.sum() * (th + 4) + (groups.size - 1) * 12, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(android.graphics.Color.MAGENTA)
        var y0 = 0
        groups.forEachIndexed { g, frames ->
            frames.forEachIndexed { i, b ->
                val x = (i % cols) * (tw + 4); val y = y0 + (i / cols) * (th + 4)
                c.drawBitmap(b, null, ARect(x, y, x + tw, y + th), null)
            }
            y0 += rowsPer[g] * (th + 4) + 12
        }
        FileOutputStream(File(dir, "$name.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** One direction: checks + returns frames (incl. the pre-tap frame) for the filmstrip. */
    private fun direction(name: String, toDark: Boolean, fails: MutableList<String>): List<Bitmap> {
        val activity = rule.activity
        val before = frame(); val beforePx = px(before)
        val iconsBefore = lightIcons()
        val button = toggle()
        val frames = run()
        val settled = frames.last().bmp; val settledPx = px(settled)
        val w = before.width
        // Frame 0 (first stepped frame) == the old theme, pixel for pixel.
        val d0 = diffCount(px(frames[0].bmp), beforePx)
        if (d0 != 0) {
            fails += "$name: first frame differs from the old theme in $d0 px"
            FileOutputStream(File(dir, "debug-$name-before.png")).use { before.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileOutputStream(File(dir, "debug-$name-f0.png")).use { frames[0].bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Landing frame: the first frame without the overlay == settled.
        val land = frames.indexOfFirst { !it.running && it !== frames[0] }
        val dl = diffCount(px(frames[land].bmp), settledPx)
        if (dl != 0) fails += "$name: landing frame $land differs from settled in $dl px"
        // Every frame: only old/new pixels (no flash, no black, no third state).
        val cx = button.center.x; val cy = button.center.y
        frames.forEachIndexed { i, f ->
            val n = foreign(px(f.bmp), beforePx, settledPx, w, button, cx, cy, if (f.running) f.r else null)
            if (n > 0) {
                fails += "$name frame $i: $n px neither old nor new theme"
                if (i == 7) {
                    FileOutputStream(File(dir, "debug-$name-f7.png")).use { f.bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileOutputStream(File(dir, "debug-$name-settled.png")).use { settled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        // Radius grows monotonically; duration.
        val rs = frames.filter { it.running }.map { it.r }
        if (rs.zipWithNext().any { (a, b) -> b < a - 0.5f }) fails += "$name: radius not monotonic $rs"
        val ms = (land + 1) * 16
        if (ms !in 420..640) fails += "$name: reveal took $ms ms"
        // Bars: old icons on frame 0, new icons once revealed; flip mid-way.
        if (frames[0].lightIcons != iconsBefore) fails += "$name: bar icons flipped on frame 0"
        if (frames.last().lightIcons == toDark) fails += "$name: bar icons not flipped (lightIcons=${frames.last().lightIcons})"
        val flip = frames.indexOfFirst { it.lightIcons != iconsBefore }
        // Persistence + the same Activity.
        settleIdle()
        val want = if (toDark) ThemeMode.Dark else ThemeMode.Light
        if (storedEventually(want) != want) fails += "$name: stored theme ${stored()} != $want"
        assertSame(activity, rule.activity)
        println("REVEAL $name: ${frames.size} frames, landing frame $land (${ms} ms), bars flip frame $flip, first-frame diff $d0, landing diff $dl, radii ${rs.map { it.toInt() }}")
        return listOf(before) + frames.map { it.bmp }
    }

    @Test fun revealLightToDarkAndBack() {
        val fails = ArrayList<String>()
        assertTrue("starts light", lightIcons())
        val a = direction("light-to-dark", toDark = true, fails)
        val b = direction("dark-to-light", toDark = false, fails)
        filmstrip("theme-reveal-filmstrip", listOf(a, b))
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    @Test fun repeatedTapsRetargetWithoutGlitches() {
        val light = px(frame())
        val button = toggle()
        // Settled dark reference.
        click(); settleIdle(); storedEventually(ThemeMode.Dark); settleIdle()
        val dark = px(frame())
        click(); settleIdle()
        assertEquals(ThemeMode.Light, storedEventually(ThemeMode.Light))
        settleIdle()
        rule.mainClock.autoAdvance = false
        val frames = ArrayList<Bitmap>(); val radii = ArrayList<Float?>(); val edges = ArrayList<List<Float>>()
        val earlier = ArrayList<Float>()
        repeat(3) { k ->
            if (reveal.running) earlier += reveal.radiusPx
            click()
            repeat(if (k < 2) 9 else 45) {
                rule.mainClock.advanceTimeByFrame(); frames += frame()
                radii += if (reveal.running) reveal.radiusPx else null
                edges += if (reveal.running) earlier.toList() else emptyList()
            }
        }
        val w = frames[0].width
        val fails = ArrayList<String>()
        frames.forEachIndexed { i, f ->
            // Only light/dark pixels, the current edge and edges baked into a retargeted snapshot.
            val n = foreign(px(f), light, dark, w, button, button.center.x, button.center.y, radii[i], edges[i])
            if (n > 0) fails += "frame $i: $n px neither theme"
        }
        println("RETARGET: ${frames.size} frames, retargeted at radii $earlier")
        settleIdle()
        assertEquals("three taps from light end dark", ThemeMode.Dark, storedEventually(ThemeMode.Dark))
        settleIdle()
        assertEquals(0, diffCount(px(frame()), dark))
        filmstrip("theme-reveal-retarget-filmstrip", listOf(frames))
        assertTrue(fails.take(10).joinToString("\n"), fails.isEmpty())
    }

    @Test fun reducedMotionIsAShortCrossfade() {
        runBlocking { app.settings.setReduceMotion(true) }
        settleIdle()
        val frames = run()
        val land = frames.indexOfFirst { !it.running && it !== frames[0] }
        println("REDUCED reveal: landing frame $land (${(land + 1) * 16} ms), radius ${frames.map { it.r }.distinct()}")
        assertTrue("crossfade ~200 ms, got ${(land + 1) * 16}", (land + 1) * 16 in 180..260)
        assertTrue("no circle in reduced motion", frames.all { it.r == 0f })
        settleIdle()
        assertEquals(ThemeMode.Dark, storedEventually(ThemeMode.Dark))
    }

    @Test fun longPressMenuSetsSystemLightDark() {
        rule.onNodeWithTag("theme-toggle").performTouchInput { longClick() }
        settleIdle()
        assertTrue("menu open", rule.onAllNodesWithTag("theme-menu", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val menu = rule.onAllNodesWithTag("theme-menu", useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        val b = toggle()
        assertTrue("menu below the button, right-aligned ($menu vs $b)", menu.top > b.bottom && abs(menu.right - b.right) < 2f)
        rule.onNodeWithText("Dark").performSemanticsAction(SemanticsActions.OnClick)
        settleIdle()
        assertEquals(ThemeMode.Dark, storedEventually(ThemeMode.Dark))
        settleIdle()
        assertFalse(lightIcons())
        assertFalse("menu closed", rule.onAllNodesWithTag("theme-menu", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("theme-toggle").performTouchInput { longClick() }
        settleIdle()
        rule.onNodeWithText("System").performSemanticsAction(SemanticsActions.OnClick)
        settleIdle()
        assertEquals(ThemeMode.System, storedEventually(ThemeMode.System))
    }

    @Test fun worksOnUpcomingAndDone() {
        for ((page, want) in listOf("Upcoming" to ThemeMode.Dark, "Done" to ThemeMode.Light)) {
            rule.onNodeWithText(page).performSemanticsAction(SemanticsActions.OnClick)
            settleIdle()
            click(); settleIdle()
            assertEquals(page, want, storedEventually(want))
            settleIdle()
            assertEquals(page, want == ThemeMode.Light, lightIcons())
        }
    }
}
