package dev.saikrishna.todo.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
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

/** v0.7.4: focus leaves the field on a tap outside; the bar never lets a touch through to a row. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ComposerTouchTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val d = 420f / 160f
    private val chrome get() = HomeTestHooks.chrome!!
    private lateinit var composerState: androidx.compose.foundation.text.input.TextFieldState

    @After fun tearDown() { HomeTestHooks.chrome = null; HomeTestHooks.rowInput = 0; db.close() }

    private fun start() {
        HomeTestHooks.rowInput = 0
        runBlocking { db.taskDao().insertAll((1..30).map { Task(id = it.toLong(), title = "Task number $it", createdAt = it.toLong()) }) }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(1_200); rule.waitForIdle() }
    private fun panelOpen() = rule.onAllNodes(hasContentDescription("Close")).fetchSemanticsNodes().isNotEmpty()
    private fun closePanel() { rule.onNode(hasContentDescription("Close")).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun surface(): Rect = rule.onNode(hasTestTag("composer-surface"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun fieldText() = rule.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun tap(p: Offset) { rule.onRoot().performTouchInput { click(p) }; settle() }
    private fun longPress(p: Offset) { rule.onRoot().performTouchInput { longClick(p) }; settle() }
    private fun scrollDown() { rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 200f, durationMillis = 300) }; settle() }
    private fun rowCentre(n: Int) = rule.onNodeWithText("Task number $n").fetchSemanticsNode().boundsInRoot.center

    @Test fun tapOnARowOnlyDismissesTheFieldThenCollapseWorks() {
        start()
        rule.onNode(hasSetTextAction()).performClick(); settle()
        assertTrue("field focused", chrome.focused)
        tap(rowCentre(3))
        assertFalse("focus cleared", chrome.focused)
        assertEquals("IME hide requested once", 1, chrome.dismissCount)
        assertEquals("the dismissing tap reached no row", 0, HomeTestHooks.rowInput)
        assertFalse("no task panel", panelOpen())
        // The next tap is a normal tap again.
        tap(rowCentre(3))
        assertTrue(panelOpen()); closePanel()
        scrollDown()
        assertTrue("collapses again", chrome.collapsed)
    }

    @Test fun draftTextIsKeptAndTheBarStaysExpanded() {
        start()
        rule.onNode(hasSetTextAction()).performClick(); settle()
        rule.onNode(hasSetTextAction()).performTextInput("Draft idea"); settle()
        tap(rowCentre(4))
        assertFalse(chrome.focused)
        assertEquals("Draft idea", fieldText())
        assertEquals(0, HomeTestHooks.rowInput)
        scrollDown()
        assertFalse("never collapses with text", chrome.collapsed)
    }

    @Test fun scrollingAndPageSwitchEndEditing() {
        start()
        rule.onNode(hasSetTextAction()).performClick(); settle()
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 200f, durationMillis = 300) }; settle()
        assertFalse("scroll cleared focus", chrome.focused)
        assertTrue("and the same scroll collapsed the bar", chrome.collapsed)
        chrome.expand(); settle()
        rule.onNode(hasSetTextAction()).performClick(); settle()
        assertTrue(chrome.focused)
        rule.onNode(androidx.compose.ui.test.hasText("Upcoming") and androidx.compose.ui.test.hasClickAction()).performSemanticsAction(SemanticsActions.OnClick); settle()
        assertFalse("page switch cleared focus", chrome.focused)
    }

    /** Points over the bar and around it; each must never reach a row. */
    private fun barPoints(s: Rect, collapsed: Boolean): List<Pair<String, Offset>> {
        val cy = s.center.y
        val pts = mutableListOf(
            "left rim" to Offset(s.left + 1.5f * d, cy),
            "right rim" to Offset(s.right - 1.5f * d, cy),
            "top rim" to Offset(s.center.x + (if (collapsed) 0f else 40f * d), s.top + 1f * d),
            "bottom rim" to Offset(s.center.x + (if (collapsed) 0f else 40f * d), s.bottom - 1f * d),
            "shadow above" to Offset(s.center.x, s.top - 4f * d),
            "shadow below" to Offset(s.center.x, s.bottom + 6f * d),
            "outside left edge" to Offset(s.left - 5f * d, cy),
            "outside right edge" to Offset(s.right + 5f * d, cy),
            "corner" to Offset(s.left + 3f * d, s.top + 3f * d),
        )
        if (collapsed) {
            pts += "divider" to Offset(s.center.x, cy)
            pts += "beside divider" to Offset(s.center.x + 4f * d, s.top + 4f * d)
        } else {
            pts += "right of mic (2 dp inset)" to Offset(s.right - 1f * d, s.bottom - 3f * d)
            pts += "margin left" to Offset(s.left - 16f * d, cy)
            pts += "margin right" to Offset(s.right + 16f * d, cy)
        }
        return pts
    }

    private fun probe(state: String, s: Rect, collapsed: Boolean, long: Boolean, fails: MutableList<String>, log: StringBuilder, prepare: () -> Unit = {}) {
        for ((name, p) in barPoints(s, collapsed)) {
            prepare()
            val before = HomeTestHooks.rowInput
            if (long) longPress(p) else tap(p)
            val leaked = HomeTestHooks.rowInput != before || panelOpen()
            log.append("[k=${"%.2f".format(chrome.progress.value)} c=${chrome.collapsed}] $state ${if (long) "long" else "tap"} $name (${"%.0f".format(p.x / d)},${"%.0f".format(p.y / d)} dp): ${if (leaked) "LEAK" else "blocked"}\n")
            if (leaked) fails += "$state ${if (long) "long-press" else "tap"} at $name reached a row"
            if (panelOpen()) closePanel()
            if (chrome.focused) chrome.dismiss()
            settle()
        }
    }

    private fun snapProgress(k: Float) {
        rule.runOnUiThread { runBlocking { chrome.progress.snapTo(k) } }
        rule.waitForIdle()
    }

    @Test fun barConsumesEveryTouchButBesideThePillReachesRows() {
        start()
        val fails = ArrayList<String>(); val log = StringBuilder()
        // A row really is behind the bar: the same y beside the collapsed pill reaches it (checked below).
        val full = surface()
        val expanded = { chrome.expand(); settle() }
        probe("expanded", full, false, long = false, fails, log, expanded)
        probe("expanded", full, false, long = true, fails, log, expanded)

        chrome.collapseForTest(); settle()
        val pill = surface()
        assertEquals(120f, pill.width / d, 0.5f)
        val collapsedPrep = { chrome.collapseForTest(); settle() }
        probe("collapsed", pill, true, long = false, fails, log, collapsedPrep)
        probe("collapsed", pill, true, long = true, fails, log, collapsedPrep)
        // Mid-morph geometry (the surface parked at 25 / 50 / 75 %, re-parked before every touch).
        for (k in listOf(0.25f, 0.5f, 0.75f)) {
            val park = { chrome.collapseForTest(); settle(); snapProgress(k) }
            park()
            probe("mid-morph k=$k", surface(), true, long = false, fails, log, park)
        }
        snapProgress(1f)

        // Beside the pill: rows get the tap.
        chrome.collapseForTest(); settle()
        for ((name, p) in listOf("left of pill" to Offset(pill.left - 40f * d, pill.center.y), "right of pill" to Offset(pill.right + 40f * d, pill.center.y))) {
            val before = HomeTestHooks.rowInput
            tap(p)
            val reached = HomeTestHooks.rowInput != before
            log.append("collapsed tap $name: ${if (reached) "reached row" else "NOT reached"}\n")
            if (!reached) fails += "tap $name did not reach the row"
            if (panelOpen()) closePanel()
            chrome.collapseForTest(); settle()
        }
        // In the bottom fade above the expanded bar, rows stay tappable.
        chrome.expand(); settle()
        // (The point must be on a card, not in the 10 dp gap between cards: try a few heights.)
        var fadeOk = false
        for (dy in listOf(14f, 22f, 30f)) {
            val before = HomeTestHooks.rowInput
            tap(Offset(full.left + 60f * d, full.top - dy * d))
            val ok = HomeTestHooks.rowInput != before
            log.append("expanded tap in the bottom fade $dy dp above the bar: ${if (ok) "reached row" else "not on a row"}\n")
            if (panelOpen()) closePanel()
            if (ok) { fadeOk = true; break }
        }
        if (!fadeOk) fails += "row under the bottom fade not tappable"
        if (panelOpen()) closePanel()

        File(dir, "composer-touch-probe-after.txt").writeText(log.toString())
        println(log)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }
}
