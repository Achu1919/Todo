package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.components.bottomFade
import dev.saikrishna.todo.ui.motion.Depth
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * v0.7.2 bottom fade above the composer: the same row-step / slope-kink measurements as
 * HeaderEdgeTest, on the real mask over the real light/dark card and sky colours, plus a check
 * that the last row can scroll fully clear of the fade.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class BottomFadeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    @After fun close() = db.close()

    private fun lum(p: Int) = 0.2126 * ((p shr 16) and 0xff) + 0.7152 * ((p shr 8) and 0xff) + 0.0722 * (p and 0xff)

    private fun maskProfile(dark: Boolean) {
        val edge = 300f
        rule.setContent {
            AppTheme(darkTheme = dark) {
                val c = AppTheme.colors
                // Worst case: an opaque card-coloured layer over the deepest sky colour.
                Box(Modifier.fillMaxSize().background(c.skyDeep)) {
                    Box(Modifier.fillMaxSize().bottomFade({ edge }).background(if (dark) Color(0xFF2A303D) else Color.White))
                }
            }
        }
        rule.waitForIdle()
        val b = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val fadePx = Depth.BottomFade.value * b.height / 891f
        val end = (b.height - edge).toInt(); val start = (end - fadePx).toInt()
        val y0 = start - 60; val y1 = end + 60
        val x = b.width / 2
        val p = DoubleArray(y1 - y0) { lum(b.getPixel(x, y0 + it)) }
        var maxStep = 0.0
        for (i in 1 until p.size) maxStep = maxOf(maxStep, abs(p[i] - p[i - 1]))
        val w = 24
        val slopes = (0 until (p.size - 1) / w).map { k -> (p[(k + 1) * w] - p[k * w]) / w }
        var maxKink = 0.0
        for (k in 1 until slopes.size) maxKink = maxOf(maxKink, abs(slopes[k] - slopes[k - 1]))
        // C¹ ends: the step right at the fade's start/end is ~0 (no line where the fade begins/ends).
        val atStart = abs(p[start - y0 + 1] - p[start - y0])
        val atEnd = abs(p[end - y0] - p[end - y0 - 1])
        val total = abs(p.first() - p.last())
        val line = "bottom-fade ${if (dark) "dark" else "light"}: total ${"%.1f".format(total)} over ${fadePx.toInt()} rows; " +
            "max row step=${"%.2f".format(maxStep)}; step at start=${"%.2f".format(atStart)}, at end=${"%.2f".format(atEnd)}; max slope change=${"%.3f".format(maxKink)}/row"
        println(line)
        assertTrue(line, maxStep < 1.5)
        assertTrue(line, atStart < 0.5 && atEnd < 0.5)
    }

    @Test fun maskLight() = maskProfile(false)
    @Test fun maskDark() = maskProfile(true)

    /** The list's bottom padding lets the last row come to rest above the fade. */
    @Test fun lastRowClearsTheFade() {
        runBlocking { db.taskDao().insertAll((1..16).map { Task(id = it.toLong(), title = "Row $it", createdAt = it.toLong()) }) }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        rule.onNode(hasScrollAction() and androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(16)
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val density = 420f / 160f
        val fadeStart = field.top - 2f * density - Depth.BottomFade.value * density
        val last = rule.onAllNodesWithText("Row 16").fetchSemanticsNodes().first().boundsInRoot
        // The row text's card ends ~12 dp below its title line; check the card bottom.
        val cardBottom = last.bottom + 12f * density
        val line = "last row card bottom ≈ $cardBottom, fade starts at $fadeStart, composer field top ${field.top}"
        println(line)
        assertTrue(line, cardBottom <= fadeStart + 0.5f * density)
    }
}
