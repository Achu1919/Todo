package dev.saikrishna.todo.shots

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.TodoViewModel
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
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** v0.7 organisation shots: list picker, tags + progress rows, subtasks panel, drag lift, templates menu, focus. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class OrgScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    @After fun close() = db.close()

    private fun seed() = runBlocking {
        val d = LocalDate.now().toEpochDay()
        val w = TaskList.WORK; val p = TaskList.PERSONAL
        db.taskDao().insertAll(
            listOf(
                Task(id = 1, title = "Ship the v0.7 release", createdAt = 1, dueDay = d, priorityCode = Priority.High.code, listId = w, tags = "release android", sortOrder = 1),
                Task(id = 2, title = "Call the bank about the card", createdAt = 2, dueDay = d, listId = p, tags = "money", sortOrder = 2),
                Task(id = 3, title = "Draft the Q4 plan", notes = "Outline + budget", createdAt = 3, listId = w, tags = "planning q4 finance", sortOrder = 3),
                Task(id = 4, title = "Pack for Goa", createdAt = 4, listId = p, tags = "travel", sortOrder = 4),
                Task(id = 5, title = "Review Ananya's PR", createdAt = 5, listId = w, sortOrder = 5),
                Task(id = 6, title = "Water the plants", createdAt = 6, listId = p, sortOrder = 6),
                Task(id = 7, title = "Book dentist", createdAt = 7, dueDay = d + 2, listId = p, sortOrder = 7),
            ),
        )
        val org = db.orgDao()
        org.upsertSubtasks(
            listOf(
                Subtask(0, 1, "Bump version to 0.7.0", true, 0),
                Subtask(0, 1, "Run screenshot tests", true, 1),
                Subtask(0, 1, "Write release notes", false, 2),
                Subtask(0, 1, "Upload the APK", false, 3),
                Subtask(0, 1, "Tell the testers", false, 4),
                Subtask(0, 4, "Sunscreen", false, 0),
                Subtask(0, 4, "Charger", true, 1),
            ),
        )
        // v0.9 lists panel: a saved view section and an overdue task for the plan summary.
        org.insertView(dev.saikrishna.todo.data.SavedView(name = "Work this week", query = "", filter = "", position = 1))
        db.taskDao().insertAll(listOf(Task(id = 8, title = "Renew the passport", createdAt = 8, dueDay = d - 1, listId = p, sortOrder = 8)))
    }

    private lateinit var vm: TodoViewModel

    private fun start(dark: Boolean, focus: FocusMode = FocusMode.Off) {
        seed()
        val settings = SettingsRepository(ctx)
        runBlocking { settings.setFocus(focus, System.currentTimeMillis()) }
        vm = TodoViewModel(TaskRepository(db), settings)
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = false) {
                        HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {})
                    }
                }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null && vm.lists.value.isNotEmpty() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    private fun settle(ms: Long = 1_500) {
        var t = 0L
        while (t < ms) { rule.mainClock.advanceTimeByFrame(); t += 16 }
    }

    private fun capture(name: String) = rule.onRoot().captureRoboImage(File(dir, "$name.png").absolutePath)

    private fun rows(name: String, dark: Boolean) { start(dark); capture(name) }

    /** v0.9 lists panel, grown out of the bottom-left lists button. */
    private fun picker(name: String, dark: Boolean) {
        start(dark)
        rule.onNodeWithTag("lists-button").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        capture(name)
    }

    private fun panel(name: String, dark: Boolean) {
        start(dark)
        rule.onNodeWithText("Ship the v0.7 release").performClick()
        settle()
        capture(name)
    }

    private fun templates(name: String, dark: Boolean) {
        start(dark)
        rule.onNodeWithContentDescription("Add options").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        capture(name)
    }

    private fun focus(name: String, dark: Boolean) { start(dark, FocusMode.Work); capture(name) }

    /** Long-press, then move without releasing: the row is lifted (1.03 + shadow) mid-drag. */
    private fun drag(name: String, dark: Boolean) {
        start(dark)
        val node = rule.onNodeWithText("Pack for Goa")
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        node.performTouchInput { moveBy(Offset(0f, -40f)) }
        rule.mainClock.advanceTimeBy(100)
        node.performTouchInput { moveBy(Offset(0f, -150f)) }
        settle(600)
        capture(name)
        // Release and wait for the drop to be written (else it lands after the database closes).
        node.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { runBlocking { db.taskDao().get(4)!!.sortOrder != 4L } }
    }

    private fun crop(name: String, fromDp: Int?, heightDp: Int) {
        val file = File(dir, "$name.png")
        rule.onRoot().captureRoboImage(file.absolutePath)
        val img = javax.imageio.ImageIO.read(file)
        val h = (heightDp * 420 / 160).coerceAtMost(img.height)
        val y = if (fromDp == null) img.height - h else fromDp * 420 / 160
        javax.imageio.ImageIO.write(img.getSubimage(0, y, img.width, h), "png", file)
    }

    /** v0.9 header: search + the centred page pill only (cropped to the top). */
    private fun header(name: String, dark: Boolean) { start(dark); crop(name, 0, 240) }

    /** v0.9 bottom row: lists button + composer (cropped to the bottom); optionally a list selected. */
    private fun bottomRow(name: String, dark: Boolean, focus: FocusMode = FocusMode.Off, list: Long? = null) {
        start(dark, focus)
        if (list != null) { rule.runOnUiThread { vm.selectList(list) }; settle() }
        crop(name, null, 200)
    }

    /** v0.9 collapsed pair (lists button + 120 dp add pill), cropped to the bottom. */
    private fun collapsedPair(name: String, dark: Boolean) {
        start(dark)
        rule.runOnUiThread { dev.saikrishna.todo.ui.home.HomeTestHooks.chrome!!.collapseForTest() }
        settle()
        crop(name, null, 200)
    }

    /** v0.9.1 theme dropdown (long-press on the header theme toggle), cropped to the top. */
    private fun themeMenu(name: String, dark: Boolean) {
        start(dark)
        rule.onNodeWithTag("theme-toggle").performTouchInput { longClick() }
        settle()
        crop(name, 0, 320)
    }

    @Test fun themeMenuLight() = themeMenu("theme-menu-light", false)
    @Test fun themeMenuDark() = themeMenu("theme-menu-dark", true)
    @Test fun homeHeaderLight() = header("home-header-light", false)
    @Test fun homeHeaderDark() = header("home-header-dark", true)
    @Test fun homeBottomRowLight() = bottomRow("home-bottom-row-light", false)
    @Test fun homeBottomRowDark() = bottomRow("home-bottom-row-dark", true)
    @Test fun homeBottomRowListLight() = bottomRow("home-bottom-row-list-light", false, list = TaskList.WORK)
    @Test fun homeBottomRowListDark() = bottomRow("home-bottom-row-list-dark", true, list = TaskList.WORK)
    @Test fun homeBottomRowFocusLight() = bottomRow("home-bottom-row-focus-light", false, FocusMode.Work)
    @Test fun homeBottomRowFocusDark() = bottomRow("home-bottom-row-focus-dark", true, FocusMode.Work)
    @Test fun homePairCollapsedLight() = collapsedPair("home-pair-collapsed-light", false)
    @Test fun homePairCollapsedDark() = collapsedPair("home-pair-collapsed-dark", true)
    @Test fun homeV09Light() = rows("home-v09-light", false)
    @Test fun homeV09Dark() = rows("home-v09-dark", true)
    @Test fun listsPanelLight() = picker("lists-panel-light", false)
    @Test fun listsPanelDark() = picker("lists-panel-dark", true)
    @Test fun rowsTagsProgressLight() = rows("rows-tags-progress-light", false)
    @Test fun rowsTagsProgressDark() = rows("rows-tags-progress-dark", true)
    @Test fun panelSubtasksTagsLight() = panel("panel-subtasks-tags-light", false)
    @Test fun panelSubtasksTagsDark() = panel("panel-subtasks-tags-dark", true)
    @Test fun dragLiftedLight() = drag("drag-lifted-light", false)
    @Test fun dragLiftedDark() = drag("drag-lifted-dark", true)
    @Test fun templatesMenuLight() = templates("templates-menu-light", false)
    @Test fun templatesMenuDark() = templates("templates-menu-dark", true)
    @Test fun focusIndicatorLight() = focus("focus-indicator-light", false)
    @Test fun focusIndicatorDark() = focus("focus-indicator-dark", true)
}
