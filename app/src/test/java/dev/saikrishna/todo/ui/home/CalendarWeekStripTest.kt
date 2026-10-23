package dev.saikrishna.todo.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.domain.CalendarData
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** v0.13.1: the collapsed week strip shows the 7 day numbers, the selected circle and dots (light + dark). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", application = Application::class)
class CalendarWeekStripTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val today = LocalDate.now()

    @After fun tearDown() { db.close() }

    private fun settle() { rule.waitForIdle(); repeat(15) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() } }

    @Test fun collapsedWeekShowsAllSevenDays() {
        val week = CalendarData.week(today)
        runBlocking {
            db.taskDao().insertAll(week.mapIndexed { i, d ->
                Task(id = i + 1L, title = "Task $i", createdAt = i + 1L, dueDay = d.toEpochDay(), listId = if (i % 2 == 0) TaskList.PERSONAL else TaskList.WORK)
            })
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(30_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        rule.mainClock.autoAdvance = false
        settle()
        NavTest.section(rule, Section.Calendar) { settle() }
        WeekStripCheck.assertDrawn(rule, today, withDots = week.toSet())
        // Next week (turned via the hooks' selection path), then back to today: still a full strip.
        val next = calendarTurn(today, expanded = false, dir = 1)
        rule.onNode(androidx.compose.ui.test.hasTestTag("calendar-grid")).performTouchInput { swipeLeft(durationMillis = 200) }
        settle()
        WeekStripCheck.assertDrawn(rule, next, withDots = emptySet())
        rule.onNode(androidx.compose.ui.test.hasTestTag("calendar-today"), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        settle()
        WeekStripCheck.assertDrawn(rule, today, withDots = week.toSet())
    }
}
