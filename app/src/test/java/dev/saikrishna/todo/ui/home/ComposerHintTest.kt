package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect as ARect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.RepeatRule
import dev.saikrishna.todo.ui.components.skyBackdrop
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** v0.10 rotating composer hint: real syntax only, timing, pauses, a static TalkBack label. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ComposerHintTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0) // Wednesday
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    @org.junit.Before fun rotateOn() { ComposerHints.rotate = true }
    @org.junit.After fun rotateOff() { ComposerHints.rotate = System.getProperty("todo.static.hints") != "true" }

    @Test fun everyExampleUsesSyntaxTheParserSupports() {
        val lists = TaskList.Defaults
        fun p(s: String) = QuickParse.parse(s, now, lists = lists)
        val all = ComposerHints.Examples.map(::p)
        all.forEachIndexed { i, r -> assertFalse("'${ComposerHints.Examples[i]}' parsed as plain text", r.isPlain) }
        all[0].let { assertEquals("Call mom", it.title); assertEquals(now.toLocalDate().plusDays(1), it.dueDay); assertEquals(18 * 60, it.dueMinute) }
        all[1].let { assertEquals("Pay rent", it.title); assertEquals(RepeatRule.Monthly(1), it.repeat) }
        all[2].let { assertEquals("Groceries", it.title); assertEquals(TaskList.PERSONAL, it.list?.id) }
        all[3].let {
            assertEquals("Review deck", it.title); assertEquals(Priority.High, it.priority)
            assertEquals(now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.FRIDAY)), it.dueDay)
        }
        all[4].let { assertEquals("Water plants", it.title); assertEquals(RepeatRule.Daily(3), it.repeat) }
        assertEquals("Add a task", ComposerHints.All.first())
    }

    private var reduced by mutableStateOf(false)
    private var chrome: ComposerChrome? = null

    private fun host(withChrome: Boolean = false) {
        chrome = if (withChrome) ComposerChrome(100f, 100f) else null
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduced) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) {
                        Box(Modifier.fillMaxWidth().skyBackdrop(AppTheme.colors).padding(vertical = 16.dp).testTag("host")) {
                            Composer(state = rememberTextFieldState(), onSubmit = {}, clock = { now }, chrome = chrome)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun index(): Int = rule.onNodeWithTag("composer-hint", true).fetchSemanticsNode().config[HintIndexKey]
    private fun spoken(): String = rule.onNodeWithTag("composer-hint", true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
    private fun advance(ms: Long) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }

    @Test fun cyclesEveryIntervalWhileIdleAndTalkBackStaysStatic() {
        host()
        rule.mainClock.autoAdvance = false
        assertEquals(0, index())
        advance(ComposerHints.IntervalMs - 200)
        assertEquals(0, index())
        advance(400)
        assertEquals(1, index())
        advance(ComposerHints.IntervalMs)
        assertEquals(2, index())
        assertEquals("Add a task", spoken())
        // What TalkBack reads on the field (merged): only the static label, never the example.
        val merged = rule.onNode(hasSetTextAction()).fetchSemanticsNode().config
        val words = merged.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString() +
            merged.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString()
        assertFalse(words, ComposerHints.Examples.any { words.contains(it) })
        // Wraps around back to "Add a task".
        advance(ComposerHints.IntervalMs * (ComposerHints.All.size - 2))
        assertEquals(0, index())
    }

    @Test fun pausesWhileFocusedAndResumesAfter() {
        host()
        rule.mainClock.autoAdvance = false
        advance(ComposerHints.IntervalMs + 100)
        assertEquals(1, index())
        rule.onNode(hasSetTextAction()).performClick() // focus
        advance(ComposerHints.IntervalMs * 3)
        assertEquals(1, index())
        rule.runOnUiThread { rule.activity.currentFocus?.clearFocus() }
        rule.onNode(hasSetTextAction()).fetchSemanticsNode() // still there
        rule.runOnIdle { }
        assertEquals("Add a task", spoken())
    }

    @Test fun pausesWhileCollapsed() {
        host(withChrome = true)
        rule.mainClock.autoAdvance = false
        advance(50)
        rule.runOnUiThread { chrome!!.collapseForTest() }
        assertTrue("busy=${chrome!!.composerBusy} ext=${chrome!!.externalBlock}", chrome!!.collapsed)
        advance(50)
        val held = index()
        advance(ComposerHints.IntervalMs * 3)
        assertEquals(held, index())
        rule.runOnUiThread { chrome!!.expand() }
        advance(50) // recompose first: advanceTimeBy runs due delays before the frame
        advance(ComposerHints.IntervalMs + 100)
        assertEquals((held + 1) % ComposerHints.All.size, index())
    }

    private fun frame(): Bitmap = rule.onNodeWithTag("host").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    /** Frames of one swap ("Add a task" → first example), sampled every 2 frames. */
    private fun swapFrames(): List<Bitmap> {
        val frames = arrayListOf(frame()) // the old phrase, at rest
        val from = index()
        var waited = 0
        while (index() == from && waited < ComposerHints.IntervalMs + 500) { rule.mainClock.advanceTimeBy(16); rule.waitForIdle(); waited += 16 }
        repeat(13) { frames += frame(); rule.mainClock.advanceTimeBy(32); rule.waitForIdle() }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        frames += frame()
        return frames
    }

    @Test fun filmstrip() {
        host()
        rule.mainClock.autoAdvance = false
        val springy = swapFrames()
        // Settled: the last two frames are identical (no lingering motion).
        assertTrue(springy[springy.size - 2].sameAs(springy.last()))
        assertTrue(!springy.first().sameAs(springy.last()))
        reduced = true
        rule.waitForIdle()
        val calm = swapFrames()
        assertNotNull(calm.last())
        // Left 62 % of the bar (where the hint is), 5 frames per row; spring swap first, then reduced.
        val cw = (springy[0].width * 0.62f).toInt(); val ch = springy[0].height
        val tw = (cw * 0.6f).toInt(); val th = (ch * 0.6f).toInt()
        val cols = 5
        val groups = listOf(springy, calm)
        val rowsPer = groups.map { (it.size + cols - 1) / cols }
        val out = Bitmap.createBitmap(cols * (tw + 4), rowsPer.sum() * (th + 4) + 16, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(android.graphics.Color.MAGENTA)
        var y0 = 0
        groups.forEachIndexed { g, frames ->
            frames.forEachIndexed { i, b ->
                val x = (i % cols) * (tw + 4); val y = y0 + (i / cols) * (th + 4)
                c.drawBitmap(b, ARect(0, 0, cw, ch), ARect(x, y, x + tw, y + th), null)
            }
            y0 += rowsPer[g] * (th + 4) + 16
        }
        FileOutputStream(File(dir, "composer-hint-filmstrip.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
