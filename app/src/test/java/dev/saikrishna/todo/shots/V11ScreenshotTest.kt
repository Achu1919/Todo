package dev.saikrishna.todo.shots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SavedView
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.search.SearchTestHooks
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
import java.io.FileOutputStream
import java.time.LocalDate

/** v0.11 search morph: open/close filmstrips (light, dark), reduced motion, results docked above a keyboard. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class V11ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    @After fun close() { SearchTestHooks.ime = null; db.close() }

    private fun save(name: String, bmp: Bitmap) =
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    private fun root(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    private fun start(dark: Boolean, reduced: Boolean = false) {
        val td = LocalDate.now().toEpochDay()
        runBlocking {
            db.taskDao().insertAll(
                listOf(
                    Task(id = 1, title = "Call the bank about the card", createdAt = 1, dueDay = td, listId = TaskList.WORK, tags = "bills", dueMinute = 600),
                    Task(id = 2, title = "Buy milk", notes = "oat, for the bank holiday", createdAt = 2, dueDay = td, listId = TaskList.PERSONAL),
                    Task(id = 3, title = "Water the plants", createdAt = 3, dueDay = td),
                    Task(id = 4, title = "Team sync notes", createdAt = 4, dueDay = td, listId = TaskList.WORK, tags = "work"),
                    Task(id = 5, title = "Book dentist", createdAt = 5, dueDay = td),
                    Task(id = 6, title = "Pay the electricity bill", createdAt = 6, dueDay = td, tags = "bills", done = true, completedAt = 10),
                ),
            )
            db.orgDao().insertView(SavedView(name = "Errands", query = "milk", filter = "", position = 1))
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduced) {
                AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
    }

    /** Frames every [stepMs] while [n] frames elapse after [action]. */
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
        save(name, out)
    }

    private fun openClose(dark: Boolean) {
        start(dark)
        val mode = if (dark) "dark" else "light"
        val open = record(15, 40) { rule.onNodeWithContentDescription("Search").performSemanticsAction(SemanticsActions.OnClick) }
        strip("search-open-filmstrip-$mode", open, 40)
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        save("search-open-$mode", root())
        val close = record(15, 40) { rule.onNodeWithTag("search-close").performSemanticsAction(SemanticsActions.OnClick) }
        strip("search-close-filmstrip-$mode", close, 40)
    }

    @Test fun openCloseLight() = openClose(false)
    @Test fun openCloseDark() = openClose(true)

    @Test fun reducedMotion() {
        start(false, reduced = true)
        val open = record(8, 32) { rule.onNodeWithContentDescription("Search").performSemanticsAction(SemanticsActions.OnClick) }
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val close = record(8, 32) { rule.onNodeWithTag("search-close").performSemanticsAction(SemanticsActions.OnClick) }
        strip("search-reduced-filmstrip", open + close, 32)
    }

    /** Typed query with a (simulated) keyboard up: results fill in above the chips + pill. */
    private fun results(dark: Boolean) {
        val ime = SearchTestHooks.FakeIme()
        SearchTestHooks.ime = ime
        start(dark)
        rule.onNodeWithContentDescription("Search").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val kb = (300 * ctx.resources.displayMetrics.density).toInt()
        rule.runOnIdle { ime.target.intValue = kb; ime.live.intValue = kb }
        rule.onNode(hasSetTextAction()).performTextInput("b")
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val shot = root().copy(Bitmap.Config.ARGB_8888, true)
        // Paint a keyboard stand-in where the IME would be, so the docking reads in the image.
        val c = Canvas(shot)
        c.drawRect(0f, (shot.height - kb).toFloat(), shot.width.toFloat(), shot.height.toFloat(),
            Paint().apply { color = if (dark) Color.rgb(30, 32, 38) else Color.rgb(210, 213, 220) })
        c.drawText("keyboard (simulated)", 40f, shot.height - kb + 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 40f })
        save("search-results-${if (dark) "dark" else "light"}", shot)
    }

    @Test fun resultsLight() = results(false)
    @Test fun resultsDark() = results(true)
}
