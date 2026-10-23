package dev.saikrishna.todo.shots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.capture.CaptureScreen
import dev.saikrishna.todo.ui.capture.VoiceControl
import dev.saikrishna.todo.ui.home.Filter
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.HomeTestHooks
import dev.saikrishna.todo.ui.home.NavTest
import dev.saikrishna.todo.ui.home.Section
import dev.saikrishna.todo.ui.home.TodoViewModel
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
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v0.13 at Sai's phone size (412 × 915 dp), light and dark: the view switcher, Recurring, Home with
 * the gear, the Calendar (week strip, month grid, the pull) and the add-task toolbar in the
 * composer and the capture card. Files: out/shots/v013-<name>-<light|dark>.png; filmstrips are
 * v013-<name>-filmstrip-<mode>.png (scripts/v013_sheets.py builds the three contact sheets).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", application = Application::class)
class V13ScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()
    private val mode get() = if (dark) "dark" else "light"

    @After fun close() = db.close()

    private fun root(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    private fun write(name: String, bmp: Bitmap) =
        FileOutputStream(File(dir, "v013-$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    private fun save(name: String) = write("$name-$mode", root())
    private fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val errands = TaskList(3, "Errands", color = 4, emoji = "🛒", position = 2)

    private fun seed() = runBlocking {
        db.orgDao().insertList(errands)
        val td = today.toEpochDay()
        var id = 0L
        fun t(title: String, list: Long, due: Long? = td, done: Long? = null, minute: Int? = null, repeat: String? = null, prio: Int = 0) =
            Task(id = ++id, title = title, createdAt = id, dueDay = due, listId = list, dueMinute = minute, repeatRule = repeat,
                priorityCode = prio, done = done != null, completedAt = done, sortOrder = id)
        val dow = today.dayOfWeek.value
        db.taskDao().insertAll(listOf(
            t("Review the branch incentive numbers", TaskList.WORK, minute = 10 * 60, prio = 2),
            t("Standup", TaskList.WORK, minute = 9 * 60 + 30, repeat = "WD"),
            t("Water the plants", TaskList.PERSONAL, repeat = "D:2"),
            t("Buy oat milk", errands.id),
            t("Send overdue invoice", TaskList.WORK, due = td - 1),
            t("Weekly review", TaskList.WORK, due = td + (7 - dow).toLong(), minute = 17 * 60, repeat = "W:1:7"),
            t("Pay rent", TaskList.PERSONAL, due = today.plusMonths(1).withDayOfMonth(1).toEpochDay(), repeat = "M:1", prio = 1),
            t("Mum's birthday", TaskList.PERSONAL, due = td + 9, repeat = "Y:${today.plusDays(9).monthValue}:${today.plusDays(9).dayOfMonth}"),
            t("Pick up dry cleaning", errands.id, due = td + 1),
            t("Draft Q4 roadmap", TaskList.WORK, due = td + 2),
            t("Book the dentist", TaskList.PERSONAL, due = td + 4, minute = 11 * 60),
            t("Car service", TaskList.PERSONAL, due = td + 12),
            t("Pay the electricity bill", TaskList.PERSONAL, done = at(0, 9)),
            t("Reply to Priya", TaskList.WORK, done = at(0, 8)),
            t("Gym", TaskList.PERSONAL, due = td - 1, done = at(1, 7)),
        ))
    }

    private fun themed(content: @Composable () -> Unit) {
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { content() } }
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

    private fun click(tag: String) { rule.onNode(hasTestTag(tag), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick) }

    private fun record(n: Int, stepMs: Long, action: () -> Unit): List<Bitmap> {
        val out = ArrayList<Bitmap>()
        out += root()
        action()
        repeat(n) { rule.mainClock.advanceTimeBy(stepMs); out += root() }
        return out
    }

    private fun strip(name: String, frames: List<Bitmap>, stepMs: Long) {
        val tw = frames[0].width / 4; val th = frames[0].height / 4
        val cols = minOf(8, frames.size); val rows = (frames.size + cols - 1) / cols
        val label = 34
        val out = Bitmap.createBitmap(cols * (tw + 6), rows * (th + 6 + label), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(40, 40, 46))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
        frames.forEachIndexed { i, f ->
            val x = (i % cols) * (tw + 6); val y = (i / cols) * (th + 6 + label)
            c.drawText("${i * stepMs} ms", x + 6f, y + 26f, p)
            c.drawBitmap(f, null, Rect(x, y + label, x + tw, y + label + th), null)
        }
        write("$name-filmstrip-$mode", out)
    }

    private fun go(f: Filter) = NavTest.go(rule, f) { settle() }

    // ---- view switcher -------------------------------------------------------------------------
    @Test fun switcher() {
        home()
        save("today")
        strip("switcher", record(15, 24) { click("view-switcher") }, 24)
        settle()
        save("switcher-open")
        click("switcher-recurring"); settle()
        save("recurring")
        go(Filter.Upcoming); save("upcoming")
        go(Filter.Done); save("done")
    }

    @Test fun homePage() {
        home()
        NavTest.section(rule, Section.Home) { settle() }
        save("home")
    }

    // ---- calendar ------------------------------------------------------------------------------
    @Test fun calendar() {
        home()
        NavTest.section(rule, Section.Calendar) { settle() }
        save("calendar-week")
        strip("calendar-pull", record(15, 32) { click("calendar-handle") }, 32)
        settle()
        save("calendar-month")
        click("cal-${today.plusDays(2)}"); settle()
        // Robolectric draws the translucent projected-repeat layers only from the second capture on.
        root(); settle(500)
        save("calendar-day")
    }

    // ---- add-task toolbar ----------------------------------------------------------------------
    @Test fun toolbar() {
        home()
        strip("toolbar", record(12, 24) { HomeTestHooks.chrome!!.focusRequest() }, 24)
        settle()
        save("composer-tools")
        rule.onNode(hasSetTextAction()).performTextInput("Call the plumber"); settle()
        click("tool-Date"); settle()
        save("composer-date")
        click("preset-Tomorrow"); settle()
        click("tool-Time"); settle()
        click("preset-Evening"); settle()
        click("tool-Repeat"); settle()
        save("composer-repeat")
        click("preset-repeat-0"); settle()
        click("tool-Priority"); settle(); click("preset-High"); settle()
        click("tool-List"); settle()
        save("composer-list")
        click("pick-list-${TaskList.WORK}"); settle()
        save("composer-chips")
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
                    voiceOverride = StillVoice(),
                )
            }
        }
        rule.mainClock.autoAdvance = false
        settle()
        save("capture")
        rule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Task title")).performTextInput("Renew the car insurance")
        settle()
        click("tool-Date"); settle(); click("preset-Next week"); settle()
        click("tool-Reminder"); settle()
        save("capture-reminder")
        click("preset-remind-${24 * 60}"); settle()
        click("tool-Priority"); settle(); click("preset-Medium"); settle()
        save("capture-chips")
    }
}
