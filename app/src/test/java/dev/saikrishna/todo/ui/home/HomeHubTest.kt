package dev.saikrishna.todo.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SavedView
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
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
import java.time.LocalDate

/**
 * v0.13: the lists panel is gone. The bottom-left button is the view switcher (four pills,
 * back/scrim fold them), and Home carries Settings, Plan your day, list management and saved views.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class HomeHubTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private lateinit var vm: TodoViewModel
    private val opened = ArrayList<String>()

    @After fun tearDown() { db.close() }

    private fun start() {
        val td = LocalDate.now().toEpochDay()
        runBlocking {
            db.taskDao().insertAll(
                listOf(
                    Task(id = 1, title = "Late one", createdAt = 1, dueDay = td - 2, listId = TaskList.WORK),
                    Task(id = 2, title = "Late two", createdAt = 2, dueDay = td - 1, listId = TaskList.PERSONAL),
                    Task(id = 3, title = "Today one", createdAt = 3, dueDay = td, listId = TaskList.WORK),
                ),
            )
            db.orgDao().insertView(SavedView(name = "Errands", query = "bank", filter = "", position = 1))
        }
        vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) {
                        HomeScreen(
                            vm,
                            onOpenSearch = { opened += "search" },
                            onOpenSettings = { opened += "settings" },
                            onOpenLists = { opened += "lists" },
                            onOpenPlan = { opened += "plan" },
                        )
                    }
                }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(1_200); rule.waitForIdle() }
    private fun tagged(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun switcherOpen() = tagged("switcher-today")
    private fun tapSwitcher() { rule.onNode(hasTestTag("view-switcher"), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun switcherLabel() = rule.onNode(hasTestTag("view-switcher"), useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle() }
    private fun clickTag(tag: String) { rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick); settle() }

    @Test fun headerHasSectionsAndTheSwitcherShowsTheView() {
        start()
        for (s in Section.entries) assertTrue(s.title, rule.onAllNodes(NavTest.sectionTab(s)).fetchSemanticsNodes().isNotEmpty())
        assertFalse("no view pills until opened", switcherOpen())
        assertEquals("View: Today", switcherLabel())
        assertTrue(rule.onAllNodes(hasContentDescription("Search")).fetchSemanticsNodes().isNotEmpty())
        assertFalse("Settings lives on Home", tagged("home-settings"))
    }

    @Test fun switcherOpensPicksAndFolds() {
        start()
        tapSwitcher()
        assertTrue(switcherOpen())
        for (v in TodoViews) assertTrue(v.name, tagged("switcher-${v.name.lowercase()}"))
        rule.onNodeWithTag("switcher-today").assertIsSelected()
        back()
        assertFalse("back folds the pills", switcherOpen())
        tapSwitcher(); clickTag("switcher-upcoming")
        assertFalse("a pick folds the pills", switcherOpen())
        assertEquals("View: Upcoming", switcherLabel())
        tapSwitcher()
        rule.onNode(hasContentDescription("Close views")).performSemanticsAction(SemanticsActions.OnClick); settle()
        assertFalse("the scrim folds the pills", switcherOpen())
        assertEquals("View: Upcoming", switcherLabel())
    }

    @Test fun homeCarriesSettingsPlanListsAndViews() {
        start()
        NavTest.section(rule, Section.Home) { settle() }
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasText("Plan your day")).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Plan your day").performSemanticsAction(SemanticsActions.OnClick); settle()
        clickTag("home-settings")
        clickTag("lists-manage")
        clickTag("lists-new")
        assertEquals(listOf("plan", "settings", "lists", "lists"), opened)
        // A saved view opens the in-place search morph (Home stays); back closes it.
        rule.onNode(androidx.compose.ui.test.hasText("Errands") and androidx.compose.ui.test.hasClickAction()).performSemanticsAction(SemanticsActions.OnClick); settle()
        assertTrue(tagged("search-overlay"))
        back()
        assertFalse(tagged("search-overlay"))
    }
}
