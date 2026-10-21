package dev.saikrishna.todo.ui.panel

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Floating task panel: opens from a card tap, closes on tap outside, deletes. Both motion modes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class TaskPanelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)

    // No explicit db.close(): @After runs before the compose rule tears the activity down, and the
    // VM's write-behind (delete / save-on-dispose) may still touch this in-memory DB then.

    private fun start(reduce: Boolean): TodoViewModel {
        runBlocking {
            db.taskDao().insertAll(listOf(Task(id = 1, title = "Water the plants", createdAt = 1), Task(id = 2, title = "Call mum", createdAt = 2)))
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduce) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) }
                }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.advanceTimeBy(1_000)
        return vm
    }

    private fun openAndTapOutside(reduce: Boolean) {
        start(reduce)
        rule.onNodeWithText("Water the plants").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithText("Mark done").assertIsDisplayed()
        // A real tap outside the panel (left screen edge, beside it).
        rule.onNodeWithContentDescription("Close").performTouchInput { click(Offset(4f, centerY)) }
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTextCount("Mark done"))
    }

    @Test fun opensFromCardAndClosesOnTapOutside() = openAndTapOutside(reduce = false)
    @Test fun reducedMotionStillOpensAndCloses() = openAndTapOutside(reduce = true)

    @Test fun deleteFromPanelRemovesTask() {
        val vm = start(reduce = false)
        rule.onNodeWithText("Call mum").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithText("Delete").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.waitUntil(5_000) { vm.tasks.value?.none { it.id == 2L } == true }
        assertEquals(0, rule.onAllNodesWithTextCount("Mark done"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
