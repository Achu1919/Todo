package dev.saikrishna.todo.shots

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.R
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.domain.DueFilter
import dev.saikrishna.todo.domain.PlanAction
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.ShareCapture
import dev.saikrishna.todo.domain.StatusFilter
import dev.saikrishna.todo.domain.ViewFilter
import dev.saikrishna.todo.ui.capture.CaptureScreen
import dev.saikrishna.todo.ui.home.SearchFilter
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.plan.PlanScreen
import dev.saikrishna.todo.ui.plan.PlanTestHooks
import dev.saikrishna.todo.ui.search.SearchTestHooks
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.widget.TodayWidgetContent
import dev.saikrishna.todo.widget.WidgetData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * v0.8 shots: capture card (light/dark + opening filmstrip + save), planning screen (+ a card
 * action filmstrip + summary), search filters with a saved view, and the Today widget (Glance
 * render and the picker preview layout), light/dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class V08ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val today = LocalDate.now()

    @After fun close() { PlanTestHooks.decide = null; db.close() }

    private fun save(name: String, bmp: Bitmap) =
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun root(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    private fun filmstrip(name: String, frames: List<Bitmap>, every: Int = 2) {
        val picks = frames.filterIndexed { i, _ -> i % every == 0 }.take(20)
        val tw = frames[0].width / 4; val th = frames[0].height / 4
        val cols = minOf(10, picks.size); val rows = (picks.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * (tw + 4), rows * (th + 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.MAGENTA)
        picks.forEachIndexed { i, f ->
            val x = (i % cols) * (tw + 4); val y = (i / cols) * (th + 4)
            c.drawBitmap(f, null, Rect(x, y, x + tw, y + th), null)
        }
        save(name, out)
    }

    private fun themed(dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { content() } }
            }
        }
    }

    // ---- capture card ------------------------------------------------------------------------
    private val sharedText = "Read the new pricing page tomorrow https://example.com/pricing\nCompare with last quarter's numbers"

    /** A stand-in "source app" behind the translucent card. */
    @androidx.compose.runtime.Composable
    private fun FakeSourceApp(dark: Boolean) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(if (dark) listOf(Color(0xFF202634), Color(0xFF0E1118)) else listOf(Color(0xFFF4F1EA), Color(0xFFDCE3EE))))
                .padding(24.dp),
        ) {
            Text("example.com", fontSize = 13.sp, color = if (dark) Color(0x99FFFFFF) else Color(0x99000000))
            Text("Pricing that scales with you", fontSize = 28.sp, color = if (dark) Color.White else Color.Black, modifier = Modifier.padding(top = 12.dp))
            repeat(6) {
                Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(70.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (it % 2 == 0) Color(0xFF5B8DEF) else Color(0xFF42B883)))
            }
        }
    }

    private var saved: Triple<QuickParse.Result, String, Long>? = null
    private var finished = false

    private fun capture(dark: Boolean) {
        val draft = ShareCapture.parse(sharedText, null)
        themed(dark) {
            Box(Modifier.fillMaxSize()) {
                FakeSourceApp(dark)
                CaptureScreen(
                    draft = draft, lists = TaskList.Defaults, defaultList = TaskList.PERSONAL, windowBlur = false,
                    onSave = { p, n, l -> saved = Triple(p, n, l) }, onFinished = { finished = true },
                    clock = { LocalDateTime.of(2026, 10, 21, 9, 0) }, autoFocus = false,
                )
            }
        }
    }

    private fun captureShot(dark: Boolean) {
        capture(dark)
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        rule.onNodeWithTag("capture-card").assertIsDisplayed()
        rule.onNodeWithText("Read the new pricing page tomorrow").assertIsDisplayed()
        save("capture-${if (dark) "dark" else "light"}", root())
    }

    @Test fun captureLight() = captureShot(false)
    @Test fun captureDark() = captureShot(true)

    @Test fun captureOpensAndSaves() {
        rule.mainClock.autoAdvance = false
        capture(false)
        val frames = ArrayList<Bitmap>()
        repeat(36) { rule.mainClock.advanceTimeByFrame(); frames += root() }
        filmstrip("capture-open-filmstrip", frames)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag("capture-save").performClick()
        rule.mainClock.autoAdvance = false // hold the clock inside the 650 ms confirm
        repeat(20) { rule.mainClock.advanceTimeByFrame() }
        assertNotNull(saved)
        val s = saved!!
        assertEquals("Read the new pricing page", s.first.title)
        assertEquals(LocalDate.of(2026, 10, 22), s.first.dueDay)
        assertTrue(s.second.contains("https://example.com/pricing"))
        assertTrue(s.second.contains("Compare with last quarter"))
        assertEquals(TaskList.PERSONAL, s.third)
        rule.onNodeWithTag("capture-confirm").assertIsDisplayed()
        save("capture-confirm", root())
        assertTrue(!finished)
        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        assertTrue(finished)
    }

    // ---- planning ----------------------------------------------------------------------------
    private fun seedPlan(): TodoViewModel {
        val td = today.toEpochDay()
        runBlocking {
            db.taskDao().insertAll(listOf(
                Task(id = 1, title = "Send the invoice to Arjun", createdAt = 1, dueDay = td - 2, sortOrder = 1),
                Task(id = 2, title = "Call the bank about the card", createdAt = 2, dueDay = td - 1, sortOrder = 2),
                Task(id = 3, title = "Team sync notes", createdAt = 3, dueDay = td, dueMinute = 11 * 60 + 30, sortOrder = 3),
                Task(id = 4, title = "Water the plants", createdAt = 4, dueDay = td, sortOrder = 4),
                Task(id = 5, title = "Book the dentist", createdAt = 5, dueDay = td + 3, sortOrder = 5),
                Task(id = 6, title = "Renew passport photos", createdAt = 6, sortOrder = 6),
            ))
        }
        return TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
    }

    private fun planShot(dark: Boolean) {
        val vm = seedPlan()
        themed(dark) { PlanScreen(vm, onBack = {}) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("plan-card").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        rule.onNodeWithText("Send the invoice to Arjun").assertIsDisplayed()
        save("plan-${if (dark) "dark" else "light"}", root())
    }

    @Test fun planLight() = planShot(false)
    @Test fun planDark() = planShot(true)

    @Test fun planCardActionsAndSummary() {
        val vm = seedPlan()
        themed(false) { PlanScreen(vm, onBack = {}) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("plan-card").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val frames = ArrayList<Bitmap>()
        frames += root()
        PlanTestHooks.decide!!(PlanAction.Keep)
        val lefts = ArrayList<Float>()
        repeat(29) {
            rule.mainClock.advanceTimeByFrame(); frames += root()
            lefts += rule.onAllNodesWithTag("plan-card").fetchSemanticsNodes().first().boundsInRoot.left
        }
        // The kept card flies right (monotonic), leaves the screen, then the next card is on top.
        val flight = lefts.takeWhile { it > lefts[0] - 1f && it != lefts.last() }
        assertTrue("card should travel right: $lefts", flight.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("card should leave the screen: $lefts", lefts.max() > rule.onRoot().fetchSemanticsNode().size.width * 0.95f)
        assertTrue("next card should be on top within ~0.35 s: $lefts", lefts.indexOfLast { it > lefts.last() + 1f } < 22)
        filmstrip("plan-keep-filmstrip", frames)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithText("Call the bank about the card").assertIsDisplayed()
        // Overdue task 1 kept for today → due today (written at once).
        rule.waitUntil(5_000) { runBlocking { db.taskDao().get(1)?.dueDay } == today.toEpochDay() }
        PlanTestHooks.decide!!(PlanAction.Tomorrow); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        PlanTestHooks.decide!!(PlanAction.Done); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        while (rule.onAllNodesWithTag("plan-summary").fetchSemanticsNodes().isEmpty()) {
            PlanTestHooks.decide!!(PlanAction.Keep); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        }
        rule.waitUntil(5_000) { runBlocking { db.taskDao().get(2)?.dueDay } == today.toEpochDay() + 1 }
        rule.waitUntil(5_000) { runBlocking { db.taskDao().get(3)?.done } == true }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        save("plan-summary", root())
    }

    // ---- search filters ----------------------------------------------------------------------
    private fun searchShot(dark: Boolean) {
        val vm = seedPlan()
        runBlocking { TaskRepository(db).saveView("Overdue work", "", ViewFilter(due = DueFilter.Overdue).encode()) }
        vm.setSearchFilter(SearchFilter(ViewFilter(due = DueFilter.Today, status = StatusFilter.Open)))
        // v0.11: search is the in-place morph over Home (opened pre-filtered, as from a tag pill).
        themed(dark) { dev.saikrishna.todo.ui.home.HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        rule.runOnIdle { SearchTestHooks.morph!!.prefiltered = true; SearchTestHooks.morph!!.open() }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        rule.onNodeWithText("Overdue work").assertIsDisplayed()
        rule.onNode(androidx.compose.ui.test.hasText("Team sync notes") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("search-results"))).assertIsDisplayed()
        save("search-filters-${if (dark) "dark" else "light"}", root())
    }

    @Test fun searchFiltersLight() = searchShot(false)
    @Test fun searchFiltersDark() = searchShot(true)

    // ---- widget ------------------------------------------------------------------------------
    private fun nightCtx(dark: Boolean) = if (!dark) ctx else ctx.createConfigurationContext(
        Configuration(ctx.resources.configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES },
    )

    private fun drawView(v: View, wDp: Int, hDp: Int, dark: Boolean): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val pad = (16 * d).toInt()
        val out = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(if (dark) 0xFF2B3550.toInt() else 0xFF8FA9D6.toInt()) // wallpaper stand-in
        c.translate(pad.toFloat(), pad.toFloat()); v.draw(c)
        return out
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun widgetShot(dark: Boolean) {
        val td = today.toEpochDay()
        val tasks = listOf(
            Task(id = 1, title = "Call the bank about the card", createdAt = 1, dueDay = td - 1, sortOrder = 1),
            Task(id = 2, title = "Team sync notes", createdAt = 2, dueDay = td, dueMinute = 11 * 60 + 30, sortOrder = 2),
            Task(id = 3, title = "Water the plants", createdAt = 3, dueDay = td, sortOrder = 3),
            Task(id = 4, title = "Renew passport photos", createdAt = 4, sortOrder = 4),
            Task(id = 5, title = "Pick up dry cleaning", createdAt = 5, sortOrder = 5),
            Task(id = 6, title = "Email Priya", createdAt = 6, sortOrder = 6),
            Task(id = 7, title = "Backup photos", createdAt = 7, sortOrder = 7),
            Task(id = 8, title = "Book the dentist", createdAt = 8, dueDay = td + 3, sortOrder = 8),
        )
        val data = WidgetData.build(tasks, today)
        assertEquals(6, data.rows.size); assertEquals(1, data.more)
        val c = nightCtx(dark)
        val size = DpSize(320.dp, 300.dp)
        val rv = runBlocking { GlanceRemoteViews().compose(c, size) { TodayWidgetContent(c, data) } }.remoteViews
        val host = FrameLayout(c)
        val v = rv.apply(c, host)
        save("widget-${if (dark) "dark" else "light"}", drawView(v, 320, 300, dark))
        val preview = LayoutInflater.from(c).inflate(R.layout.widget_today_preview, host, false)
        save("widget-preview-${if (dark) "dark" else "light"}", drawView(preview, 250, 200, dark))
    }

    @Test fun widgetLight() = widgetShot(false)
    @Test fun widgetDark() = widgetShot(true)
}
