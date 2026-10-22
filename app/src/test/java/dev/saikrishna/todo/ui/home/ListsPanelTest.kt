package dev.saikrishna.todo.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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

/** v0.9 lists panel: its rows do what they say and close the panel; the header has no Settings button. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ListsPanelTest {
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
    private fun open() { rule.onNodeWithTag("lists-button").performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun isOpen() = rule.onAllNodes(hasTestTag("lists-panel"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(text: String) { rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun clickRow(text: String) {
        val radio = androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton)
        rule.onNode(androidx.compose.ui.test.hasText(text) and radio).performSemanticsAction(SemanticsActions.OnClick); settle()
    }
    private fun buttonLabel() = rule.onNodeWithTag("lists-button").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test fun headerHasNoSettingsOrListButton() {
        start()
        assertTrue(rule.onAllNodes(hasContentDescription("Settings")).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasTestTag("list-pill"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasContentDescription("Search")).fetchSemanticsNodes().isNotEmpty())
        assertEquals("Lists, current: All lists", buttonLabel())
    }

    @Test fun planRowShowsTheLiveSummaryAndPushesPlanning() {
        start()
        open()
        assertTrue(isOpen())
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasText("2 overdue · 1 today")).fetchSemanticsNodes().isNotEmpty())
        click("Plan your day")
        assertFalse(isOpen())
        assertEquals(listOf("plan"), opened)
    }

    @Test fun selectingAListClosesAndSwitches() {
        start()
        open()
        clickRow("Work")
        assertFalse(isOpen())
        assertEquals(TaskList.WORK, vm.selectedList.value)
        assertEquals("Lists, current: Work", buttonLabel())
        open()
        clickRow("All lists")
        assertEquals(null, vm.selectedList.value)
    }

    @Test fun newListSavedViewSettingsAndBack() {
        start()
        open(); click("New list"); assertFalse(isOpen())
        open(); click("Errands"); assertFalse(isOpen())
        // v0.11: a saved view opens the in-place search morph (Home stays); back closes it.
        assertTrue(rule.onAllNodes(hasTestTag("search-overlay")).fetchSemanticsNodes().isNotEmpty())
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertTrue(rule.onAllNodes(hasTestTag("search-overlay")).fetchSemanticsNodes().isEmpty())
        open(); click("Settings"); assertFalse(isOpen())
        assertEquals(listOf("lists", "search", "settings"), opened)
        open()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertFalse("back closes the panel", isOpen())
    }
}
