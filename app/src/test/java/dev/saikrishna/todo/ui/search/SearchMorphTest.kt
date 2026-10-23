package dev.saikrishna.todo.ui.search

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SavedView
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.HomeTestHooks
import dev.saikrishna.todo.ui.home.SearchFilter
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.time.LocalDate
import kotlin.math.abs

/** v0.11 in-place search morph: state machine, entry points, shield, back, IME docking, motion. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class SearchMorphTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private lateinit var vm: TodoViewModel
    private val morph get() = SearchTestHooks.morph!!

    @After fun tearDown() { SearchTestHooks.ime = null; db.close() }

    private fun start(reduced: Boolean = false) {
        val td = LocalDate.now().toEpochDay()
        runBlocking {
            db.taskDao().insertAll(
                listOf(
                    Task(id = 1, title = "Call the bank about the card", createdAt = 1, dueDay = td, listId = TaskList.WORK, tags = "bills"),
                    Task(id = 2, title = "Buy milk", notes = "oat, for the bank holiday", createdAt = 2, dueDay = td, listId = TaskList.PERSONAL),
                    Task(id = 3, title = "Water the plants", createdAt = 3, dueDay = td),
                ),
            )
            db.orgDao().insertView(SavedView(name = "Errands", query = "milk", filter = "", position = 1))
        }
        vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduced) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) }
                }
            }
        }
        rule.waitUntil(30_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() } // Room loads on real executors; slow under the full suite
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(1_200); rule.waitForIdle() }
    private fun frame() = rule.mainClock.advanceTimeBy(16)
    private fun overlay() = rule.onAllNodes(hasTestTag("search-overlay")).fetchSemanticsNodes().isNotEmpty()
    private fun tapSearch() = rule.onNodeWithContentDescription("Search").performSemanticsAction(SemanticsActions.OnClick)
    private fun field() = rule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Search"))
    private fun focused() = field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
    private fun has(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    // ---- pure state machine ------------------------------------------------------------------
    @Test fun phaseTable() {
        assertEquals(SearchPhase.Closed, searchPhase(false, 0f))
        assertEquals(SearchPhase.Opening, searchPhase(true, 0f))
        assertEquals(SearchPhase.Opening, searchPhase(true, 0.6f))
        assertEquals(SearchPhase.Open, searchPhase(true, 1f))
        assertEquals(SearchPhase.Closing, searchPhase(false, 0.6f))
        assertEquals(SearchPhase.Closing, searchPhase(false, 1f))
    }

    @Test fun openCloseStateMachine() {
        start()
        val chrome = HomeTestHooks.chrome
        assertFalse(overlay())
        tapSearch()
        frame(); frame()
        assertEquals(SearchPhase.Opening, morph.phase)
        assertTrue(overlay())
        settle()
        assertEquals(SearchPhase.Open, morph.phase)
        assertEquals(1, morph.opens)
        assertTrue("field autofocuses", focused())
        assertFalse("open twice is a no-op", rule.runOnIdle { morph.open() })
        rule.onNodeWithTag("search-close").performSemanticsAction(SemanticsActions.OnClick)
        frame(); frame()
        assertEquals(SearchPhase.Closing, morph.phase)
        settle()
        assertEquals(SearchPhase.Closed, morph.phase)
        assertFalse(overlay())
        assertEquals(1, morph.closes)
        assertEquals(SearchFilter(), vm.searchFilter.value)
        // Home was never re-created: same chrome object, controls back.
        assertTrue(chrome === HomeTestHooks.chrome)
        rule.onNodeWithTag("view-switcher", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test fun typingFiltersAndTapOpensTheTask() {
        start()
        tapSearch(); settle()
        assertTrue(has("Search your tasks"))
        field().performTextInput("bank")
        settle()
        // Title and notes both match; the non-matching task is not in the results.
        val results = rule.onAllNodes(hasTestTag("search-results")).fetchSemanticsNodes()
        assertEquals(1, results.size)
        assertTrue(rule.onAllNodes(hasText("Buy milk") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("search-results"))).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(hasText("Water the plants") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("search-results"))).fetchSemanticsNodes().isEmpty())
        rule.onNode(hasText("Call the bank about the card") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("search-results")))
            .performSemanticsAction(SemanticsActions.OnClick)
        settle(); settle()
        assertFalse(overlay())
        assertTrue("task panel opens on Home", has("Mark done"))
    }

    @Test fun rowTagOpensPrefilteredWithoutKeyboard() {
        start()
        rule.onNode(hasText("#bills"), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertTrue(overlay())
        assertEquals("bills", vm.searchFilter.value.filter.tag)
        assertTrue(morph.prefiltered)
        assertFalse("pre-filtered: no autofocus", focused())
        assertTrue(rule.onAllNodes(hasText("Call the bank about the card") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("search-results"))).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun savedViewFromHomeBringsItsQuery() {
        start()
        dev.saikrishna.todo.ui.home.NavTest.section(rule, dev.saikrishna.todo.ui.home.Section.Home) { settle() }
        rule.onNode(hasText("Errands") and androidx.compose.ui.test.hasClickAction()).performSemanticsAction(SemanticsActions.OnClick); settle()
        assertTrue(overlay())
        assertEquals("milk", field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertTrue(rule.onAllNodes(hasText("Buy milk") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("search-results"))).fetchSemanticsNodes().isNotEmpty())
        // The view switcher is not left open behind it.
        assertTrue(rule.onAllNodes(hasTestTag("switcher-today"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun shieldSwallowsTapsAndCloses() {
        start()
        val row = rule.onNode(hasText("Water the plants")).fetchSemanticsNode().boundsInRoot
        tapSearch(); settle()
        val before = HomeTestHooks.rowInput
        // A tap where the row sits (now under the frost, above the results area) never reaches Home.
        rule.onRoot().performTouchInput { click(Offset(row.center.x, row.center.y)) }
        frame(); frame()
        assertEquals("tap must not reach Home's rows", before, HomeTestHooks.rowInput)
        assertFalse(morph.target)
        settle()
        assertFalse(overlay())
        assertFalse("no task panel opened", has("Mark done"))
    }

    @Test fun systemBackCloses() {
        start()
        tapSearch(); settle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertFalse(overlay())
        assertEquals(1, morph.closes)
    }

    @Test fun predictiveBackScrubsCancelsAndCommits() {
        start()
        tapSearch(); settle()
        val d = rule.activity.onBackPressedDispatcher
        fun ev(p: Float) = BackEventCompat(10f, 400f, p, BackEventCompat.EDGE_LEFT)
        rule.runOnUiThread { d.dispatchOnBackStarted(ev(0f)); d.dispatchOnBackProgressed(ev(0.5f)) }
        rule.mainClock.advanceTimeBy(48); rule.waitForIdle()
        assertEquals(1f - SearchMorph.BackScrub * 0.5f, morph.k, 0.02f)
        rule.runOnUiThread { d.dispatchOnBackCancelled() }
        settle()
        assertEquals(SearchPhase.Open, morph.phase)
        rule.runOnUiThread { d.dispatchOnBackStarted(ev(0f)); d.dispatchOnBackProgressed(ev(0.8f)); d.onBackPressed() }
        settle()
        assertFalse(overlay())
    }

    @Test fun notificationRequestClosesSearchThenOpensPanel() {
        start()
        tapSearch(); settle()
        rule.runOnIdle { vm.requestOpen(3) }
        settle(); settle()
        assertFalse(overlay())
        assertTrue(has("Mark done"))
    }

    /** Pill bounds per frame: every edge moves monotonically from the circle to the dock and back. */
    @Test fun noOvershootOpenOrClose() {
        start()
        val circle = rule.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        tapSearch()
        val open = ArrayList<Pair<Float, Rect>>()
        repeat(50) { frame(); open += morph.k to SearchTestHooks.pill }
        val end = open.last().second
        assertEquals(1f, open.last().first, 0.001f)
        assertTrue("first frame starts at the circle", abs(open.first().second.left - circle.left) < 40f)
        checkMonotonic("open", open, up = true)
        assertTrue("pill is full width at rest", end.width > circle.width * 6)
        rule.onNodeWithTag("search-close").performSemanticsAction(SemanticsActions.OnClick)
        val close = ArrayList<Pair<Float, Rect>>()
        repeat(50) { frame(); if (overlay()) close += morph.k to SearchTestHooks.pill }
        checkMonotonic("close", close, up = false)
        assertFalse(overlay())
    }

    private fun checkMonotonic(name: String, s: List<Pair<Float, Rect>>, up: Boolean) {
        for (i in 1 until s.size) {
            val (k0, a) = s[i - 1]; val (k1, b) = s[i]
            assertTrue("$name: k out of range $k1", k1 in 0f..1f)
            if (up) assertTrue("$name: k went back at $i ($k0 → $k1)", k1 >= k0 - 1e-4f)
            else assertTrue("$name: k went up at $i ($k0 → $k1)", k1 <= k0 + 1e-4f)
            // Edges move one way only (no overshoot past either end).
            for ((e0, e1, dir) in listOf(Triple(a.left, b.left, -1), Triple(a.right, b.right, 1), Triple(a.top, b.top, 1), Triple(a.bottom, b.bottom, 1))) {
                val sign = if (up) dir else -dir
                assertTrue("$name: edge reversed at $i ($e0 → $e1)", (e1 - e0) * sign >= -1f)
            }
        }
    }

    @Test fun interruptibleBothWays() {
        start()
        tapSearch()
        repeat(10) { frame() }
        val mid = morph.k
        assertTrue(mid in 0.2f..0.98f)
        rule.runOnIdle { morph.close() }
        val ks = ArrayList<Float>()
        repeat(12) { frame(); ks += morph.k }
        assertTrue("retargeted: heads back without overshooting 1 ($ks)", ks.all { it in 0f..1f } && ks.last() < mid)
        rule.runOnIdle { morph.open() }
        repeat(60) { frame(); ks += morph.k }
        assertTrue(ks.all { it in 0f..1f })
        assertEquals(SearchPhase.Open, morph.phase)
        assertEquals(2, morph.opens)
    }

    /** The pill's bottom sits on the IME's top (+ gap) on every frame of a keyboard animation. */
    @Test fun pillTracksImeEveryFrameWithoutRelayingOutResults() {
        val ime = SearchTestHooks.FakeIme()
        SearchTestHooks.ime = ime
        start()
        tapSearch(); settle()
        field().performTextInput("a"); settle()
        val h = rule.onRoot().fetchSemanticsNode().size.height
        val gap = with(rule.density) { SearchDockGap.toPx() }
        val keyboard = with(rule.density) { 300f * density }.toInt()
        rule.runOnIdle { ime.target.intValue = keyboard }
        frame(); rule.waitForIdle()
        val layouts = SearchTestHooks.resultsLayouts
        val steps = 20
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val live = (keyboard * (1f - (1f - t) * (1f - t) * (1f - t))).toInt()
            rule.runOnIdle { ime.live.intValue = live }
            frame(); rule.waitForIdle()
            val expect = h - live - gap
            assertEquals("frame $i: pill bottom vs IME top", expect, SearchTestHooks.pill.bottom, 1f)
            // The chips row (results region) rides along in the draw phase, docked to the pill.
            val chips = rule.onNodeWithTag("search-chips").fetchSemanticsNode().boundsInRoot
            assertEquals("frame $i: chips docked above the pill", SearchTestHooks.pill.top - with(rule.density) { 8.dp.toPx() }, chips.bottom, 1.5f)
        }
        assertEquals("results region must not relayout per IME frame", layouts, SearchTestHooks.resultsLayouts)
        // And back down.
        rule.runOnIdle { ime.target.intValue = 0 }
        for (i in 0..steps) {
            val live = (keyboard * (1f - i / steps.toFloat())).toInt()
            rule.runOnIdle { ime.live.intValue = live }
            frame(); rule.waitForIdle()
            assertEquals("down frame $i", h - live - gap, SearchTestHooks.pill.bottom, 1f)
        }
    }

    @Test fun reducedMotionCrossfadesInPlace() {
        start(reduced = true)
        tapSearch()
        val tops = ArrayList<Float>(); val ks = ArrayList<Float>()
        repeat(16) { frame(); tops += SearchTestHooks.pill.top; ks += morph.k }
        assertTrue("pill never travels: $tops", tops.max() - tops.min() < 1f)
        assertEquals(1f, ks.last(), 0.001f)
        assertTrue("short tween (≤ 250 ms)", ks.indexOfFirst { it >= 1f } <= 15)
        rule.onNodeWithTag("search-close").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertFalse(overlay())
    }

    private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
}
