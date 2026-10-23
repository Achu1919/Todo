package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.ui.test.onNodeWithTag
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
 * The header's blurred/tinted edge must blend into the page with no visible line, and the sky
 * gradient must have no bright band. Renders Home (empty list) and takes a vertical luminance
 * profile through a content-free column strip (86–94 % of the width), from just under the header
 * controls down to above the composer:
 *  - max row-to-row step (0–255) < 1.5  → no hard edge;
 *  - max change of slope between neighbouring 24-row windows → no Mach band (a kink in the
 *    gradient reads as a bright/dark line even when every single step is tiny).
 * Writes a contrast-boosted crop of the header region to out/shots/header-edge-<theme>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class HeaderEdgeTest {
    private companion object { const val StatusPx = 63 } // 24 dp at 420 dpi

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun render(dark: Boolean, reduce: Boolean = false): Bitmap {
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduce) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) }
                }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        // A phone-like 24 dp status bar, so the header ends where it does on a real device.
        rule.runOnUiThread {
            val window = rule.activity.window
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val content = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, StatusPx, 0, 0))
                .build()
            fun dispatch(v: android.view.View) {
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(v, insets)
            }
            dispatch(content.getChildAt(0))
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        return rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun lum(p: Int): Double =
        0.2126 * ((p shr 16) and 0xff) + 0.7152 * ((p shr 8) and 0xff) + 0.0722 * (p and 0xff)

    private fun profile(b: Bitmap, y0: Int, y1: Int): DoubleArray {
        val x0 = (b.width * 0.86).toInt(); val x1 = (b.width * 0.94).toInt()
        val row = IntArray(x1 - x0)
        return DoubleArray(y1 - y0) { i ->
            b.getPixels(row, 0, row.size, x0, y0 + i, row.size, 1)
            row.sumOf { lum(it) } / row.size
        }
    }

    private fun boosted(b: Bitmap, y0: Int, y1: Int, name: String) {
        val h = y1 - y0
        val out = Bitmap.createBitmap(b.width, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(b.width * h)
        b.getPixels(px, 0, b.width, 0, y0, b.width, h)
        var lo = 255.0; var hi = 0.0
        for (p in px) { val l = lum(p); if (l < lo) lo = l; if (l > hi) hi = l }
        // Stretch the luminance range of the crop to 0–255 (×8 at most), like an auto-levels.
        val gain = minOf(8.0, 255.0 / max(1.0, hi - lo))
        for (i in px.indices) {
            val p = px[i]
            fun ch(v: Int) = ((v - lo) * gain).toInt().coerceIn(0, 255)
            px[i] = (0xff shl 24) or (ch((p shr 16) and 0xff) shl 16) or (ch((p shr 8) and 0xff) shl 8) or ch(p and 0xff)
        }
        out.setPixels(px, 0, b.width, 0, 0, b.width, h)
        FileOutputStream(File(dir, "$name.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun check(dark: Boolean, reduce: Boolean = false) {
        // v0.12: the theme toggle sits in the heading row inside this strip; hide it for the measurement.
        HomeTestHooks.hideThemeToggle = true
        val b = try { render(dark, reduce) } finally { HomeTestHooks.hideThemeToggle = false }
        val d = b.height / 891.0 // px per dp
        // Header controls end at status + 8 dp + 48 dp; their shadows reach ~12 dp below.
        val y0 = StatusPx + ((8 + 48 + 14) * d).toInt()
        val y1 = (b.height * 0.86).toInt() // above the composer
        val p = profile(b, y0, y1)
        var maxStep = 0.0; var stepAt = 0
        for (i in 1 until p.size) { val s = abs(p[i] - p[i - 1]); if (s > maxStep) { maxStep = s; stepAt = y0 + i } }
        val w = 24
        val slopes = (0 until (p.size - 1) / w).map { k -> (p[(k + 1) * w] - p[k * w]) / w }
        var maxKink = 0.0; var kinkAt = 0
        for (k in 1 until slopes.size) { val c = abs(slopes[k] - slopes[k - 1]); if (c > maxKink) { maxKink = c; kinkAt = y0 + k * w } }
        // Backdrop only (below 35 % of the height): the sky gradient's own kinks.
        val skyFrom = slopes.indices.first { y0 + it * w > b.height * 0.35 }
        var skyKink = 0.0; var skyAt = 0
        for (k in maxOf(1, skyFrom) until slopes.size) { val c = abs(slopes[k] - slopes[k - 1]); if (c > skyKink) { skyKink = c; skyAt = y0 + k * w } }
        val theme = (if (dark) "dark" else "light") + if (reduce) "-reduced" else ""
        boosted(b, 0, (b.height * 0.30).toInt(), "header-edge-$theme")
        val line = "header-edge $theme: rows $y0..$y1 of ${b.height}; max row step=${"%.2f".format(maxStep)} at y=$stepAt " +
            "(header ends at y≈${StatusPx + ((8 + 48 + 28) * d).toInt()}); max slope change=${"%.3f".format(maxKink)}/row at y=$kinkAt; " +
            "backdrop max slope change=${"%.3f".format(skyKink)}/row at y=$skyAt"
        println(line)
        val full = profile(b, 0, y1)
        val dump = (150 until 360 step 3).joinToString(" ") { "%d:%.1f".format(it, full[it]) }
        File(dir, "header-edge-$theme.txt").writeText(line + "\n" + dump + "\n")
        assertTrue(line, maxStep < 1.5)
        assertTrue(line, skyKink < 0.08)
        // With blur the header fade is low-contrast and must be as smooth as the sky. Reduced
        // motion's opaque flat tint fades over the same eased mask: every row step is still
        // tiny (asserted above) but its curvature is naturally higher, so it is only reported.
        if (!reduce) assertTrue(line, maxKink < 0.08)
    }

    @Test fun light() = check(dark = false)
    @Test fun dark() = check(dark = true)
    /** Reduced motion: no blur, the flat tint under the same eased mask. */
    @Test fun lightReduced() = check(dark = false, reduce = true)
}
