package dev.saikrishna.todo.shots

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.capture.CapturePreset
import dev.saikrishna.todo.ui.capture.CaptureScreen
import dev.saikrishna.todo.ui.capture.VoiceControl
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.plan.PlanScreen
import dev.saikrishna.todo.ui.settings.SettingsScreen
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v0.12.1 polish audit: every main screen at Sai's phone size (412 × 915 dp) in light and dark,
 * at font scale 1.0 and 1.3. Files: out/shots/v0121-<screen>-<light|dark>-<100|130>.png
 * (scripts/v0121_sheets.py builds the audit + before/after sheets).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", application = Application::class)
class V121PolishScreenshotTest(private val dark: Boolean, private val scale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} scale={1}")
        fun params() = listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1.3f), arrayOf<Any>(true, 1.3f))
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()
    private val tag get() = "${if (dark) "dark" else "light"}-${(scale * 100).toInt()}"

    @After fun close() = db.close()

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        FileOutputStream(File(dir, "v0121-$name-$tag.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val errands = TaskList(3, "Errands", color = 4, emoji = "🛒", position = 2)

    private fun seed() = runBlocking {
        db.orgDao().insertList(errands)
        val td = today.toEpochDay()
        var id = 0L
        fun t(title: String, list: Long, due: Long? = td, done: Long? = null, minute: Int? = null, notes: String = "") =
            Task(id = ++id, title = title, createdAt = id, dueDay = due, listId = list, dueMinute = minute,
                done = done != null, completedAt = done, sortOrder = id, notes = notes)
        db.taskDao().insertAll(listOf(
            t("Low yield areas like Punjab are getting low prof incentive", TaskList.WORK, minute = 10 * 60, notes = "Compare with the FY25 numbers"),
            t("High prof targets in Punjab branches", TaskList.PERSONAL),
            t("Team sync notes", TaskList.WORK, minute = 15 * 60 + 30),
            t("Buy oat milk", errands.id),
            t("Staff hired for new branch opening in RO and ZO code", TaskList.PERSONAL),
            t("Branch renovation expense", TaskList.PERSONAL),
            t("AM and staff of branches opened in FY25 and 26 are getting low incentives despite high AUM growth", TaskList.PERSONAL),
            t("Water the plants", TaskList.PERSONAL),
            t("Send overdue invoice", TaskList.WORK, due = td - 1),
            t("Pick up dry cleaning", errands.id, due = td + 1),
            t("Draft Q4 roadmap", TaskList.WORK, due = td + 2),
            t("Book the dentist", TaskList.PERSONAL, due = td + 4),
            t("Renew passport photos", TaskList.PERSONAL, due = null),
            t("Pay the electricity bill", TaskList.PERSONAL, done = at(0, 9)),
            t("Reply to Priya", TaskList.WORK, done = at(0, 8)),
            t("Send invoice", TaskList.WORK, due = td - 1, done = at(1, 17)),
            t("Gym", TaskList.PERSONAL, due = td - 1, done = at(1, 7)),
            t("Return parcel", errands.id, due = td - 2, done = at(2, 12)),
        ))
    }

    private fun themed(content: @Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                ProvideMotion(userPrefReduceMotion = false) {
                    AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { content() } }
                }
            }
        }
    }

    private fun settle(ms: Long = 1_500) {
        rule.waitForIdle()
        repeat((ms / 100).toInt()) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
    }

    private fun home(): TodoViewModel {
        seed()
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        themed { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) }
        rule.waitUntil(30_000) { vm.tasks.value != null }
        rule.mainClock.autoAdvance = false
        settle(2_000)
        return vm
    }

    private fun role(r: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, r)
    private fun tab(name: String) {
        val f = if (name == "Overview" || name == "Home") dev.saikrishna.todo.ui.home.Filter.Overview else dev.saikrishna.todo.ui.home.Filter.valueOf(name)
        dev.saikrishna.todo.ui.home.NavTest.go(rule, f) { settle() }
    }

    @Test fun today() { home(); save("today") }
    @Test fun upcoming() { home(); tab("Upcoming"); save("upcoming") }
    @Test fun done() { home(); tab("Done"); save("done") }
    @Test fun overview() { home(); tab("Overview"); save("overview") }
    @Test fun chipWork() {
        home()
        rule.onNode(hasText("Work") and role(Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick); settle()
        save("chipwork")
    }
    @Test fun search() {
        home()
        rule.onNodeWithContentDescription("Search").performSemanticsAction(SemanticsActions.OnClick); settle()
        rule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Search")).performTextInput("branch")
        settle()
        save("search")
    }
    @Test fun panel() {
        home()
        rule.onNodeWithText("Low yield areas like Punjab are getting low prof incentive").performSemanticsAction(SemanticsActions.OnClick)
        settle(2_000)
        save("panel")
    }
    @Test fun lists() {
        home()
        rule.onNodeWithTag("view-switcher", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick); settle()
        save("lists")
    }
    @Test fun themeReveal() {
        home()
        rule.onNodeWithTag("theme-toggle").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        repeat(4) { rule.mainClock.advanceTimeBy(50); rule.waitForIdle() }
        save("reveal")
    }
    @Test fun plan() {
        seed()
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        themed { PlanScreen(vm, onBack = {}) }
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("plan-card").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.autoAdvance = false
        settle(2_000)
        save("plan")
    }
    @Test fun settings() {
        seed()
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        themed { SettingsScreen(vm, onBack = {}, onOpenLicenses = {}) }
        rule.mainClock.autoAdvance = false
        settle(2_000)
        save("settings")
        rule.mainClock.autoAdvance = true // semantics scroll waits on frames
        rule.onNodeWithText("Share open tasks").performScrollTo()
        rule.mainClock.autoAdvance = false; settle()
        save("settingsdata")
    }

    private class StillVoice : VoiceControl {
        override val listening = false
        override val available = true
        override fun start(onText: (String) -> Unit, onDone: (String?) -> Unit) {}
        override fun stop() {}
        override fun cancel() {}
    }

    @Test fun capture() {
        val lists = TaskList.Defaults + errands
        themed {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                if (dark) listOf(androidx.compose.ui.graphics.Color(0xFF243049), androidx.compose.ui.graphics.Color(0xFF0E1118))
                else listOf(androidx.compose.ui.graphics.Color(0xFF9DB6DF), androidx.compose.ui.graphics.Color(0xFFE6ECF5)),
            ))) {
                CaptureScreen(
                    draft = dev.saikrishna.todo.domain.ShareCapture.parse("", null), lists = lists,
                    defaultList = TaskList.PERSONAL, windowBlur = false, onSave = { _, _, _ -> }, onFinished = {},
                    clock = { LocalDateTime.of(today, java.time.LocalTime.of(9, 0)) }, autoFocus = false,
                    preset = CapturePreset(listId = TaskList.WORK), voiceOverride = StillVoice(),
                )
            }
        }
        rule.mainClock.autoAdvance = false
        settle(1_500)
        save("capture")
    }
}
