package dev.saikrishna.todo.shots

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.HomeTestHooks
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** v0.7.3: the collapsed composer pill over a scrolled list, light + dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ComposerPillScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    @After fun close() { HomeTestHooks.chrome = null; db.close() }

    private val titles = listOf("Buy milk", "Call mom", "Pay rent", "Gym", "Read", "Book flights", "Water plants", "Email Priya",
        "Renew passport", "Dentist", "Groceries", "Laundry", "Fix the tap", "Plan the weekend", "Backup photos")

    private fun shot(dark: Boolean) {
        runBlocking { db.taskDao().insertAll(titles.mapIndexed { i, t -> Task(id = i + 1L, title = t, createdAt = i + 1L) }) }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        HomeTestHooks.chrome!!.collapseForTest()
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        val pill = rule.onNode(hasTestTag("composer-surface"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(120f, pill.width / 2.625f, 0.5f)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val name = "composer-pill-${if (dark) "dark" else "light"}"
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val top = (pill.top - 120 * 2.625f).toInt().coerceAtLeast(0)
        val crop = Bitmap.createBitmap(bmp, 0, top, bmp.width, bmp.height - top)
        FileOutputStream(File(dir, "$name-crop.png")).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun light() = shot(false)
    @Test fun dark() = shot(true)
}
