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
    private fun hostOpen() = rule.onAllNodes(hasContentDescription("Close")).fetchSemanticsNodes().isNotEmpty()
    private fun listsPanelOpen() = rule.onAllNodes(hasTestTag("switcher-overlay"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    /** A task panel (a row was opened); the v0.9 lists panel shares the host but is not a leak. */
    private fun panelOpen() = hostOpen() && !listsPanelOpen()
    private fun lists(): Rect = rule.onNode(hasTestTag("view-switcher"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun closePanel() { rule.onNode(hasContentDescription("Close")).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun closeSwitcher() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle() }
    private fun closeAny() { if (listsPanelOpen()) closeSwitcher(); if (hostOpen()) closePanel() }
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
        NavTest.section(rule, Section.Calendar) { settle() }
        assertFalse("page switch cleared focus", chrome.focused)
    }

    /**
     * Points over the bar, the v0.9 lists button and around both; none may reach a row. Points on
     * the lists button open the lists panel (expected, [onButton]).
     */
    private fun barPoints(s: Rect, l: Rect, collapsed: Boolean): List<Triple<String, Offset, Boolean>> {
        val cy = s.center.y
        val pts = mutableListOf(
            Triple("left rim", Offset(s.left + 1.5f * d, cy), false),
            Triple("right rim", Offset(s.right - 1.5f * d, cy), false),
            Triple("top rim", Offset(s.center.x + (if (collapsed) 0f else 40f * d), s.top + 1f * d), false),
            Triple("bottom rim", Offset(s.center.x + (if (collapsed) 0f else 40f * d), s.bottom - 1f * d), false),
            Triple("shadow above", Offset(s.center.x, s.top - 4f * d), false),
            Triple("shadow below", Offset(s.center.x, s.bottom + 6f * d), false),
            Triple("outside left edge (gap)", Offset(s.left - 5f * d, cy), false),
            Triple("outside right edge", Offset(s.right + 5f * d, cy), false),
            Triple("corner", Offset(s.left + 3f * d, s.top + 3f * d), false),
            // v0.9 lists button and the 8 dp gap between it and the capsule.
            Triple("lists button centre", l.center, true),
            Triple("lists button left rim", Offset(l.left + 1.5f * d, l.center.y), true),
            Triple("gap top", Offset((l.right + s.left) / 2f, s.top + 2f * d), false),
            Triple("gap bottom", Offset((l.right + s.left) / 2f, s.bottom - 2f * d), false),
            Triple("left of button (5 dp)", Offset(l.left - 5f * d, l.center.y), false),
            Triple("button shadow below", Offset(l.center.x, l.bottom + 6f * d), false),
        )
        if (collapsed) {
            pts += Triple("divider", Offset(s.center.x, cy), false)
            pts += Triple("beside divider", Offset(s.center.x + 4f * d, s.top + 4f * d), false)
        } else {
            pts += Triple("right of mic (2 dp inset)", Offset(s.right - 1f * d, s.bottom - 3f * d), false)
            pts += Triple("margin left", Offset(l.left - 16f * d, cy), false)
            pts += Triple("margin right", Offset(s.right + 16f * d, cy), false)
        }
        return pts
    }

    private fun probe(state: String, s: Rect, l: Rect, collapsed: Boolean, long: Boolean, fails: MutableList<String>, log: StringBuilder, prepare: () -> Unit = {}) {
        for ((name, p, onButton) in barPoints(s, l, collapsed)) {
            prepare()
            val before = HomeTestHooks.rowInput
            if (long) longPress(p) else tap(p)
            val leaked = HomeTestHooks.rowInput != before || panelOpen()
            val listsUp = listsPanelOpen()
            log.append("[k=${"%.2f".format(chrome.progress.value)} c=${chrome.collapsed}] $state ${if (long) "long" else "tap"} $name (${"%.0f".format(p.x / d)},${"%.0f".format(p.y / d)} dp): ${if (leaked) "LEAK" else if (listsUp) "lists panel" else "blocked"}\n")
            if (leaked) fails += "$state ${if (long) "long-press" else "tap"} at $name reached a row"
            if (!long && onButton && !listsUp) fails += "$state tap at $name did not open the view switcher"
            if (!onButton && listsUp) fails += "$state ${if (long) "long-press" else "tap"} at $name opened the view switcher"
            closeAny()
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
        val fullLists = lists()
        probe("expanded", full, fullLists, false, long = false, fails, log, expanded)
        probe("expanded", full, fullLists, false, long = true, fails, log, expanded)

        chrome.collapseForTest(); settle()
        val pill = surface()
        assertEquals(120f, pill.width / d, 0.5f)
        val collapsedPrep = { chrome.collapseForTest(); settle() }
        val pairLists = lists()
        probe("collapsed", pill, pairLists, true, long = false, fails, log, collapsedPrep)
        probe("collapsed", pill, pairLists, true, long = true, fails, log, collapsedPrep)
        // Mid-morph geometry (the surface parked at 25 / 50 / 75 %, re-parked before every touch).
        for (k in listOf(0.25f, 0.5f, 0.75f)) {
            val park = { chrome.collapseForTest(); settle(); snapProgress(k) }
            park()
            probe("mid-morph k=$k", surface(), lists(), true, long = false, fails, log, park)
        }
        snapProgress(1f)

        // Beside the pair (lists button + pill): rows get the tap.
        chrome.collapseForTest(); settle()
        for ((name, p) in listOf("left of pair" to Offset(pairLists.left - 40f * d, pill.center.y), "right of pair" to Offset(pill.right + 40f * d, pill.center.y))) {
            val before = HomeTestHooks.rowInput
            tap(p)
            val reached = HomeTestHooks.rowInput != before
            log.append("collapsed tap $name: ${if (reached) "reached row" else "NOT reached"}\n")
            if (!reached) fails += "tap $name did not reach the row"
            closeAny()
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
            closeAny()
            if (ok) { fadeOk = true; break }
        }
        if (!fadeOk) fails += "row under the bottom fade not tappable"
        closeAny()

        File(dir, "composer-touch-probe-after.txt").writeText(log.toString())
        println(log)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    /** v0.9: tapping the lists button while typing ends editing first, then opens the panel. */
    @Test fun listsButtonWhileTypingDismissesThenOpens() {
        start()
        rule.onNode(hasSetTextAction()).performClick(); settle()
        rule.onNode(hasSetTextAction()).performTextInput("Half a thought"); settle()
        assertTrue(chrome.focused)
        val before = chrome.dismissCount
        tap(lists().center)
        assertFalse("editing ended", chrome.focused)
        assertEquals("IME hide requested once", before + 1, chrome.dismissCount)
        assertTrue("view switcher open", listsPanelOpen())
        assertEquals("draft kept", "Half a thought", fieldText())
        assertEquals(0, HomeTestHooks.rowInput)
        closeSwitcher()
        assertFalse(listsPanelOpen())
        assertFalse("the button did not take focus", chrome.focused)
    }
}
