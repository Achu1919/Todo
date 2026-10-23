package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect as ARect
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
import kotlin.math.hypot

/**
 * v0.9.1: pressing the round checkbox shows no square/ripple. Frame by frame while the finger is
 * down (tap length and long-press length) and just after release, no pixel outside the ring
 * (+2 dp for the fill pop) may change; a press on the row body changes nothing outside the card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class PressFeedbackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val d = 420f / 160f

    @After fun tearDown() { db.close() }

    private fun start(dark: Boolean = false) {
        runBlocking { db.taskDao().insertAll((1..4).map { Task(id = it.toLong(), title = "Task number $it", createdAt = it.toLong()) }) }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun frame(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    private fun checkbox(n: Int): Rect =
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasContentDescription("Task number $n"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
    private fun card(n: Int): Rect =
        rule.onNode(hasText("Task number $n") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNode().boundsInRoot

    /** Pixels differing from [base] where [allowed] is false, ignoring the bottom composer/blur strip. */
    private fun changed(base: Bitmap, f: Bitmap, allowed: (Int, Int) -> Boolean, ignoreBelow: Int): Int {
        var n = 0
        for (y in 0 until minOf(ignoreBelow, base.height)) for (x in 0 until base.width) {
            if (allowed(x, y)) continue
            val p = base.getPixel(x, y); val q = f.getPixel(x, y)
            if (abs((p shr 16 and 255) - (q shr 16 and 255)) + abs((p shr 8 and 255) - (q shr 8 and 255)) + abs((p and 255) - (q and 255)) > 3) n++
        }
        return n
    }

    private fun strip(name: String, frames: List<Bitmap>, crop: Rect) {
        val pad = (40 * d).toInt()
        val src = ARect((crop.left - pad).toInt().coerceAtLeast(0), (crop.top - pad).toInt().coerceAtLeast(0), (crop.right + pad).toInt().coerceAtMost(frames[0].width), (crop.bottom + pad).toInt().coerceAtMost(frames[0].height))
        val cols = 8; val rows = (frames.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * (src.width() + 4), rows * (src.height() + 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(android.graphics.Color.MAGENTA)
        frames.forEachIndexed { i, b ->
            val x = (i % cols) * (src.width() + 4); val y = (i / cols) * (src.height() + 4)
            c.drawBitmap(b, src, ARect(x, y, x + src.width(), y + src.height()), null)
        }
        FileOutputStream(File(dir, "$name.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun checkboxPress(holdMs: Long, name: String, dark: Boolean) {
        start(dark)
        val box = checkbox(2)
        val cx = box.center.x; val cy = box.center.y
        val ring = 12f * d + 2f * d // the 24 dp mark + 2 dp for the fill pop's overshoot
        val inRing = { x: Int, y: Int -> hypot(x + 0.5f - cx, y + 0.5f - cy) <= ring }
        val ignoreBelow = (rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom - 160 * d).toInt()
        val base = frame()
        val frames = ArrayList<Bitmap>(); frames += base
        val fails = ArrayList<String>()
        rule.onRoot().performTouchInput { down(box.center) }
        var t = 0L
        while (t < holdMs) {
            rule.mainClock.advanceTimeByFrame(); t += 16
            val f = frame(); frames += f
            val n = changed(base, f, inRing, ignoreBelow)
            if (n > 0) fails += "$name pressed ${t} ms: $n px changed outside the ring"
        }
        rule.onRoot().performTouchInput { up() }
        // Release: the ring fills (inside), the title strikes and greys (intended, on the text);
        // around the checkbox (its 48 dp box and 24 dp beyond, where a ripple would show) nothing.
        val around = Rect(box.left - 24 * d, box.top - 24 * d, box.right + 24 * d, box.bottom + 24 * d)
        val textStart = box.right + 2 * d
        // v0.12: the row's list accent bar (4 dp, 6 dp in from the card edge) dims with the done
        // state, like the title: a state change on the row, not press feedback.
        val cardLeft = card(2).left
        val onBar = { x: Int, _: Int -> x + 0.5f >= cardLeft + 5 * d && x + 0.5f <= cardLeft + 11 * d }
        val outsideAround = { x: Int, y: Int -> inRing(x, y) || onBar(x, y) || !around.contains(androidx.compose.ui.geometry.Offset(x + 0.5f, y + 0.5f)) || x >= textStart }
        for (i in 1..12) {
            rule.mainClock.advanceTimeByFrame()
            val f = frame(); frames += f
            val n = changed(base, f, outsideAround, ignoreBelow)
            if (n > 0) fails += "$name released +${i * 16} ms: $n px changed around the checkbox"
        }
        strip("checkbox-press-$name-filmstrip", frames.filterIndexed { i, _ -> i % 2 == 0 || i > frames.size - 13 }, box)
        println("PRESS $name: ${frames.size} frames, ${fails.size} failing frames")
        assertTrue(fails.take(10).joinToString("\n"), fails.isEmpty())
    }

    @Test fun checkboxTapShowsNoSquareLight() = checkboxPress(160, "tap-light", false)
    @Test fun checkboxTapShowsNoSquareDark() = checkboxPress(160, "tap-dark", true)
    /** Held until just before the long-press timeout (after it, the row's drag lift starts, as intended). */
    @Test fun checkboxLongPressShowsNoSquare() = checkboxPress(480, "long", false)

    /** v0.9.1 fix: a long press on the checkbox lifts the row for dragging; its release must not complete the task. */
    @Test fun longPressReleaseDoesNotComplete() {
        start()
        val box = checkbox(2)
        rule.onRoot().performTouchInput { down(box.center) }
        repeat(50) { rule.mainClock.advanceTimeByFrame() }
        rule.onRoot().performTouchInput { up() }
        repeat(60) { rule.mainClock.advanceTimeByFrame() }
        val state = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasContentDescription("Task number 2"), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        org.junit.Assert.assertEquals("Not completed", state)
        // A normal tap still completes.
        rule.onRoot().performTouchInput { click(box.center) }
        repeat(10) { rule.mainClock.advanceTimeByFrame() }
        val after = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasContentDescription("Task number 2"), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        org.junit.Assert.assertEquals("Completed", after)
        // Let the completion's Room write finish before the in-memory DB is closed.
        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(3_000); rule.waitForIdle()
        Thread.sleep(300); rule.waitForIdle()
    }

    @Test fun rowPressStaysInsideTheCard() {
        start()
        val card = card(3)
        val pad = 14 * d // the card's own soft shadow scales with it
        val inside = Rect(card.left - pad, card.top - pad, card.right + pad, card.bottom + pad)
        val ignoreBelow = (rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom - 160 * d).toInt()
        val base = frame()
        rule.onRoot().performTouchInput { down(androidx.compose.ui.geometry.Offset(card.center.x + 60 * d, card.center.y)) }
        val fails = ArrayList<String>(); var inCard = 0
        repeat(14) { i ->
            rule.mainClock.advanceTimeByFrame()
            val f = frame()
            val n = changed(base, f, { x, y -> inside.contains(androidx.compose.ui.geometry.Offset(x + 0.5f, y + 0.5f)) }, ignoreBelow)
            if (n > 0) fails += "row pressed +${(i + 1) * 16} ms: $n px changed outside the card"
            inCard = maxOf(inCard, changed(base, f, { _, _ -> false }, ignoreBelow))
        }
        rule.onRoot().performTouchInput { cancel() }
        println("ROW press: up to $inCard px changed (inside the card)")
        assertTrue("press feedback visible on the card", inCard > 0)
        assertTrue(fails.take(10).joinToString("\n"), fails.isEmpty())
    }
}
