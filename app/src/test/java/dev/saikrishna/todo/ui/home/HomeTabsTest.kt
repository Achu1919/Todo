package dev.saikrishna.todo.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * v0.12.1: every tab of the header pill (Overview was inserted at index 0) shows its own page,
 * from every other tab, with the test clock under manual control like the screenshot harness.
 * Pages are identified by content only they show; chips on Done must not change the page.
 * Tab state is the Filter enum (no int indices are stored or passed in intents).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class HomeTabsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    @After fun close() = db.close()

    private fun start() {
        val td = LocalDate.now().toEpochDay()
        runBlocking {
            db.taskDao().insertAll(listOf(
                Task(id = 1, title = "Today work task", createdAt = 1, dueDay = td, listId = TaskList.WORK, sortOrder = 1),
                Task(id = 2, title = "Today personal task", createdAt = 2, dueDay = td, listId = TaskList.PERSONAL, sortOrder = 2),
                Task(id = 3, title = "Later work task", createdAt = 3, dueDay = td + 3, listId = TaskList.WORK, sortOrder = 3),
                Task(id = 4, title = "Later personal task", createdAt = 4, dueDay = td + 4, listId = TaskList.PERSONAL, sortOrder = 4),
                Task(id = 5, title = "Finished work task", createdAt = 5, dueDay = td, listId = TaskList.WORK, done = true, completedAt = System.currentTimeMillis(), sortOrder = 5),
                Task(id = 6, title = "Finished personal task", createdAt = 6, dueDay = td, listId = TaskList.PERSONAL, done = true, completedAt = System.currentTimeMillis(), sortOrder = 6),
            ))
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.autoAdvance = false
        settle()
    }

    private fun role(r: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, r)
    private fun tabNode(f: Filter) = rule.onNode(hasText(f.title) and role(Role.Tab))
    /**
     * With autoAdvance off, one big advanceTimeBy() right after a semantics click runs before the
     * click's snapshot change is applied, so the UI lags one action behind (the v0.12.0
     * categories sheet showed Upcoming for Done). Idle first, then advance in frame-sized steps.
     */
    private fun settle(ms: Long = 1_200) {
        rule.waitForIdle()
        repeat((ms / 100).toInt()) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
    }

    /** Text that only that page shows. */
    private val marker = mapOf(
        Filter.Overview to "Plan your day",
        Filter.Today to "Today personal task",
        Filter.Upcoming to "Later personal task",
        Filter.Done to "Finished personal task",
    )

    private fun assertOn(f: Filter, step: String) {
        for (o in Filter.entries) {
            if (o == f) tabNode(o).assertIsSelected() else tabNode(o).assertIsNotSelected()
            val n = rule.onAllNodes(hasText(marker.getValue(o))).fetchSemanticsNodes().size
            if (o == f) assert(n > 0) { "$step: page ${f.title} lacks '${marker[o]}'" }
            else if (o != Filter.Overview && f != Filter.Overview) assert(n == 0) { "$step: page ${f.title} shows ${o.title}'s '${marker[o]}'" }
        }
    }

    @Test fun tabIndicesAfterOverview() {
        assert(Filter.entries.map { it.title } == listOf("Overview", "Today", "Upcoming", "Done"))
    }

    @Test fun everyTabFromEveryTabWithOneSettle() {
        start()
        assertOn(Filter.Today, "launch")
        for (from in Filter.entries) for (to in Filter.entries) {
            if (from == to) continue
            tabNode(from).performSemanticsAction(SemanticsActions.OnClick); settle()
            assertOn(from, "go ${from.title}")
            tabNode(to).performSemanticsAction(SemanticsActions.OnClick); settle()
            assertOn(to, "${from.title} -> ${to.title}")
        }
    }

    @Test fun chipThenUpcomingThenDone() {
        // The exact sequence of the v012 categories shot.
        start()
        rule.onNode(hasText("Work") and role(Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick); settle()
        tabNode(Filter.Today).assertIsSelected()
        assert(rule.onAllNodes(hasText("Today work task")).fetchSemanticsNodes().isNotEmpty())
        assert(rule.onAllNodes(hasText("Today personal task")).fetchSemanticsNodes().isEmpty()) { "Work chip on Today" }
        tabNode(Filter.Upcoming).performSemanticsAction(SemanticsActions.OnClick); settle()
        assertOn(Filter.Upcoming, "upcoming")
        tabNode(Filter.Done).performSemanticsAction(SemanticsActions.OnClick); settle()
        assertOn(Filter.Done, "done")
        rule.onNode(hasText("Work") and role(Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick); settle()
        tabNode(Filter.Done).assertIsSelected()
        assert(rule.onAllNodes(hasText("Finished work task")).fetchSemanticsNodes().isNotEmpty())
        assert(rule.onAllNodes(hasText("Finished personal task")).fetchSemanticsNodes().isEmpty()) { "Work chip on Done" }
    }
}
