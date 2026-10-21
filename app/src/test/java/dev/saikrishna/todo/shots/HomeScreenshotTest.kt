package dev.saikrishna.todo.shots

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.settings.SettingsScreen
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
import javax.imageio.ImageIO
import java.time.LocalDate
import java.time.ZoneId

/**
 * Visual check without a device: renders the real Home screen (real ViewModel + in-memory Room)
 * on a 411 × 891 dp, 420 dpi canvas and writes PNGs to out/shots/. Opt-in: -Pshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class HomeScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    @After fun close() = db.close()

    private fun seed() = runBlocking {
        val today = LocalDate.now()
        val d = today.toEpochDay()
        val noonToday = today.atTime(9, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.taskDao().insertAll(
            listOf(
                Task(id = 1, title = "Renew passport photos", createdAt = 1, dueDay = d - 1),
                Task(id = 2, title = "Call the bank about the card", createdAt = 2, dueDay = d, priorityCode = Priority.High.code),
                Task(id = 3, title = "Draft the Q4 plan", notes = "Outline + budget", createdAt = 3),
                Task(id = 4, title = "Pick up dry cleaning", createdAt = 4),
                Task(id = 5, title = "Water the plants", createdAt = 5),
                Task(id = 6, title = "Morning run", createdAt = 6, done = true, completedAt = noonToday),
                Task(id = 7, title = "Book dentist", createdAt = 7, dueDay = d + 2),
            ),
        )
    }

    private fun shoot(
        name: String,
        dark: Boolean,
        settings: Boolean = false,
        page: Int = 0,
        tapCard: String? = null,
        reduce: Boolean = false,
        cropBottomDp: Int? = null,
        tapSettleMs: Long = 1_500,
        type: String? = null,
        snoozeId: Long? = null,
        scheduled: Boolean = false,
    ) {
        seed()
        if (scheduled) runBlocking {
            // Task 3 as a timed, repeating task: today 4:30 pm, every Mon & Thu.
            val dao = db.taskDao()
            dao.setDueAt(3, LocalDate.now().toEpochDay(), 16 * 60 + 30)
            dao.setRepeat(3, "W:1:1,4")
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduce) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = false) {
                        if (settings) SettingsScreen(vm, onBack = {}, onOpenLicenses = {})
                        else HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {})
                    }
                }
            }
        }
        rule.waitUntil(10_000) { if (settings) vm.settings.value != null else vm.tasks.value != null }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        repeat(page) {
            rule.onNodeWithText(if (it == 0) "Upcoming" else "Done").performClick()
            rule.mainClock.advanceTimeBy(1_500)
        }
        if (type != null) {
            rule.onNode(hasSetTextAction()).performTextInput(type)
            rule.mainClock.advanceTimeBy(1_500)
        }
        if (snoozeId != null) {
            vm.requestOpen(snoozeId, snooze = true)
            rule.mainClock.advanceTimeBy(1_500)
        }
        if (tapCard != null) {
            // The real path: tap the card, the panel grows out of it.
            if (tapSettleMs >= 1_000) {
                rule.onNodeWithText(tapCard).performClick()
            } else {
                // Mid-animation frame: input injection is not delivered with a paused clock, so open
                // the same card through the app's open request (the same anchor/transform path).
                rule.mainClock.autoAdvance = false
                vm.requestOpen(3)
            }
            // Step frame by frame so recomposition, effects and the spring all run.
            var t = 0L
            while (t < tapSettleMs) { rule.mainClock.advanceTimeByFrame(); t += 16 }
        }
        if (tapCard == null) rule.waitForIdle()
        val file = File(dir, "$name.png")
        rule.onRoot().captureRoboImage(file.absolutePath)
        if (cropBottomDp != null) {
            val img = ImageIO.read(file)
            val h = (cropBottomDp * 420 / 160).coerceAtMost(img.height)
            ImageIO.write(img.getSubimage(0, img.height - h, img.width, h), "png", file)
        }
    }

    @Test fun homeLight() = shoot("home-light", dark = false)
    @Test fun homeDark() = shoot("home-dark", dark = true)
    @Test fun settingsLight() = shoot("settings-light", dark = false, settings = true)
    @Test fun upcomingLight() = shoot("upcoming-light", dark = false, page = 1)
    @Test fun panelLight() = shoot("panel-light", dark = false, tapCard = "Draft the Q4 plan")
    @Test fun panelDark() = shoot("panel-dark", dark = true, tapCard = "Draft the Q4 plan")
    /** Mid container transform (≈ 130 ms in): the card growing into the panel. */
    @Test fun panelOpeningLight() = shoot("panel-opening-light", dark = false, tapCard = "Draft the Q4 plan", tapSettleMs = 260)
    @Test fun composerLight() = shoot("composer-light", dark = false, cropBottomDp = 190)
    @Test fun composerChipsLight() = shoot("composer-chips-light", dark = false, cropBottomDp = 260, type = "Dentist tomorrow at 4pm !high every week")
    @Test fun composerChipsDark() = shoot("composer-chips-dark", dark = true, cropBottomDp = 260, type = "Dentist tomorrow at 4pm !high every week")
    @Test fun snoozePickerLight() = shoot("snooze-picker-light", dark = false, snoozeId = 4)
    @Test fun panelRepeatTimeLight() = shoot("panel-repeat-time-light", dark = false, tapCard = "Draft the Q4 plan", scheduled = true)
    @Test fun homeLightReduced() = shoot("home-light-reduced-motion", dark = false, reduce = true)
}
