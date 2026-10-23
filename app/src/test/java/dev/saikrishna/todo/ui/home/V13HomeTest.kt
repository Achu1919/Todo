package dev.saikrishna.todo.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.flow.first

/** v0.13 on Home: the add-task toolbar, the Calendar pull + turn, the Recurring view. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class V13HomeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private lateinit var vm: TodoViewModel
    private val today = LocalDate.now()

    @After fun tearDown() { db.close() }

    private fun start() {
        val td = today.toEpochDay()
        runBlocking {
            db.taskDao().insertAll(
                listOf(
                    Task(id = 1, title = "Plain today task", createdAt = 1, dueDay = td, listId = TaskList.PERSONAL, sortOrder = 1),
                    Task(id = 2, title = "Weekly review", createdAt = 2, dueDay = td + 2, listId = TaskList.WORK, repeatRule = "W:1:1", sortOrder = 2),
                    Task(id = 3, title = "Rent every month", createdAt = 3, dueDay = td + 5, listId = TaskList.PERSONAL, repeatRule = "M:1", sortOrder = 3),
                ),
            )
        }
        vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) }
                }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(1_200); rule.waitForIdle() }
    private fun tagged(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun shown(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNode(hasTestTag(tag), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun switcherLabel() = rule.onNode(hasTestTag("view-switcher"), useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription].joinToString()

    @Test fun toolbarSlidesUpOnFocusAndItsPicksLandOnTheTask() {
        start()
        assertFalse("no tools until the composer is focused", tagged("composer-toolbar"))
        rule.onNode(hasSetTextAction()).performClick(); settle()
        assertTrue("tools slide up with the keyboard", tagged("composer-toolbar"))
        for (t in listOf("Date", "Time", "Repeat", "List", "Priority", "Reminder", "Notes")) assertTrue(t, tagged("tool-$t"))
        rule.onNode(hasSetTextAction()).performTextInput("Pay the plumber"); settle()

        click("tool-Date")
        assertTrue(tagged("popover-Date"))
        click("preset-Tomorrow")
        assertTrue("date chip", tagged("chip-Date"))
        click("tool-Priority"); click("preset-High")
        assertTrue("priority chip", tagged("chip-Priority"))
        click("chip-clear-Priority")
        assertFalse("x clears the chip", tagged("chip-Priority"))
        click("tool-Notes")
        rule.onNode(hasTestTag("notes-field")).performTextInput("Kitchen sink"); settle()
        click("popover-done")
        assertFalse("done folds the picker", tagged("popover-Notes"))
        assertTrue(tagged("chip-Notes"))

        rule.onNode(hasSetTextAction() and !hasTestTag("notes-field")).performImeAction(); settle(); settle()
        val added = runBlocking { db.taskDao().observeAll().first() }.first { it.title == "Pay the plumber" }
        assertEquals(today.plusDays(1).toEpochDay(), added.dueDay)
        assertEquals(0, added.priorityCode)
        assertEquals("Kitchen sink", added.notes)
        assertFalse("picks reset after adding", tagged("chip-Date"))
        assertEquals("a future task lands on Upcoming", "View: Upcoming", switcherLabel())
    }

    @Test fun recurringShowsOnlyRepeatingTasks() {
        start()
        NavTest.go(rule, Filter.Recurring) { settle() }
        assertEquals("View: Recurring", switcherLabel())
        assertTrue(shown("Weekly review"))
        assertTrue(shown("Rent every month"))
        assertFalse(shown("Plain today task"))
    }

    @Test fun calendarPullsIntoAMonthAndTurnsPages() {
        start()
        NavTest.section(rule, Section.Calendar) { settle() }
        assertTrue(tagged("calendar-grid"))
        val st = CalendarTestHooks.state!!
        assertFalse("starts as a week strip", st.expanded)
        assertTrue("today's cell", tagged("cal-$today"))
        rule.onNodeWithTag("calendar-grid").performTouchInput { swipeDown(startY = centerY, endY = centerY + 900f, durationMillis = 400) }
        settle()
        assertTrue("pulled into the month grid", st.expanded)
        assertTrue("the whole month is laid out", tagged("cal-${today.withDayOfMonth(today.lengthOfMonth())}"))
        rule.onNodeWithTag("calendar-grid").performTouchInput { swipeLeft(durationMillis = 200) }
        settle()
        val next = today.plusMonths(1).withDayOfMonth(15)
        assertTrue("swiped to next month", tagged("cal-$next"))
        click("calendar-today")
        assertTrue(tagged("cal-$today"))
        click("calendar-handle") // the handle toggles back
        assertFalse("tapped back to the week strip", st.expanded)
        click("calendar-handle"); assertTrue(st.expanded)
        rule.onNodeWithTag("calendar-grid").performTouchInput { swipeUp(startY = bottom - 10f, endY = top - 600f, durationMillis = 400) }
        settle()
        assertFalse("pushed back to the week strip", st.expanded)
    }
}
