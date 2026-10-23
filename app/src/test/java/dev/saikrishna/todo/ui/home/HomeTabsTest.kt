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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * v0.13: every page (Home, the four Todo views behind the switcher, Calendar) shows its own
 * content, reached from every other page, with the test clock under manual control like the
 * screenshot harness. The header pill shows the page's section; the switcher button its view.
 * Chips on Done must not change the page.
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
    /**
     * With autoAdvance off, one big advanceTimeBy() right after a semantics click runs before the
     * click's snapshot change is applied, so the UI lags one action behind. Idle first, then
     * advance in frame-sized steps.
     */
    private fun settle(ms: Long = 1_200) {
        rule.waitForIdle()
        repeat((ms / 100).toInt()) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
    }
    private fun go(f: Filter) = NavTest.go(rule, f) { settle() }
    private fun has(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Content that only that page shows. */
    private fun shows(f: Filter): Boolean = when (f) {
        Filter.Overview -> has("Plan your day")
        Filter.Today -> has("Today personal task") && !has("Later personal task")
        Filter.Upcoming -> has("Later personal task") && !has("Finished personal task")
        Filter.Recurring -> has("Nothing repeats yet.")
        Filter.Done -> has("Finished personal task") && !has("Later personal task")
        Filter.Calendar -> hasTag("calendar-grid")
    }

    private fun assertOn(f: Filter, step: String) {
        for (s in Section.entries) {
            val tab = rule.onNode(NavTest.sectionTab(s))
            if (s == f.section) tab.assertIsSelected() else tab.assertIsNotSelected()
        }
        assert(shows(f)) { "$step: page ${f.title} lacks its content" }
        if (f.isTodoView) {
            val label = rule.onNode(hasTestTag("view-switcher"), useUnmergedTree = true).fetchSemanticsNode()
                .config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString()
            assert(label == "View: ${f.title}") { "$step: switcher shows $label" }
        }
    }

    @Test fun pagesAndSections() {
        assert(Filter.entries.map { it.title } == listOf("Home", "Today", "Upcoming", "Recurring", "Done", "Calendar"))
        assert(Section.entries.map { it.title } == listOf("Home", "Todo", "Calendar"))
        assert(TodoViews == listOf(Filter.Today, Filter.Upcoming, Filter.Recurring, Filter.Done))
    }

    @Test fun everyPageFromEveryPageWithOneSettle() {
        start()
        assertOn(Filter.Today, "launch")
        for (from in Filter.entries) for (to in Filter.entries) {
            if (from == to) continue
            go(from); assertOn(from, "go ${from.title}")
            go(to); assertOn(to, "${from.title} -> ${to.title}")
        }
    }

    @Test fun todoTabReturnsToTheLastView() {
        start()
        go(Filter.Done); assertOn(Filter.Done, "done")
        go(Filter.Calendar); assertOn(Filter.Calendar, "calendar")
        NavTest.section(rule, Section.Todo) { settle() }
        assertOn(Filter.Done, "todo tab brings back Done")
    }

    @Test fun chipThenUpcomingThenDone() {
        start()
        rule.onNode(hasText("Work") and role(Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick); settle()
        assert(has("Today work task"))
        assert(!has("Today personal task")) { "Work chip on Today" }
        go(Filter.Upcoming); assertOn(Filter.Upcoming, "upcoming")
        go(Filter.Done); assertOn(Filter.Done, "done")
        rule.onNode(hasText("Work") and role(Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick); settle()
        rule.onNode(NavTest.sectionTab(Section.Todo)).assertIsSelected()
        assert(!has("Later work task")) { "still on Done" }
        assert(has("Finished work task"))
        assert(!has("Finished personal task")) { "Work chip on Done" }
    }
}
