package dev.saikrishna.todo.shots

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.R
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
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.widget.AddPillContent
import dev.saikrishna.todo.widget.AddRoundContent
import dev.saikrishna.todo.widget.HubContent
import dev.saikrishna.todo.widget.QuickData
import dev.saikrishna.todo.widget.QuickListsContent
import dev.saikrishna.todo.widget.TodayWidgetContent
import dev.saikrishna.todo.widget.WidgetData
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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v0.12 shots (real renders): Overview (light/dark, reduced), the tab switch into Overview and the
 * list-chip filtering (filmstrips), list accents + chips on Today/Upcoming/Done, the capture card
 * opened with each widget preset, and the W1–W4 widgets (Glance → RemoteViews) + their previews.
 * The contact sheets out/shots/v012-*.png are assembled from these files by scripts/v012_sheets.py.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class V12ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    @After fun close() = db.close()

    private fun save(name: String, bmp: Bitmap) =
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    private fun root(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    private fun mode(dark: Boolean) = if (dark) "dark" else "light"

    /** Epoch millis at [hour] on the day [daysAgo] before today. */
    private fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val errands = TaskList(3, "Errands", color = 4, emoji = "🛒", position = 2)

    private fun seed() = runBlocking {
        db.orgDao().insertList(errands)
        val td = today.toEpochDay()
        var id = 0L
        fun t(title: String, list: Long, due: Long? = td, done: Long? = null, minute: Int? = null) =
            Task(id = ++id, title = title, createdAt = id, dueDay = due, listId = list, dueMinute = minute,
                done = done != null, completedAt = done, sortOrder = id)
        db.taskDao().insertAll(
            listOf(
                t("Call the bank about the card", TaskList.WORK, minute = 10 * 60),
                t("Team sync notes", TaskList.WORK, minute = 15 * 60 + 30),
                t("Buy oat milk", errands.id),
                t("Water the plants", TaskList.PERSONAL),
                t("Pick up dry cleaning", errands.id, due = td + 1),
                t("Draft Q4 roadmap", TaskList.WORK, due = td + 2),
                t("Book the dentist", TaskList.PERSONAL, due = td + 4),
                t("Renew passport photos", TaskList.PERSONAL, due = null),
                // history: done today + the last days (streak + week chart)
                t("Pay the electricity bill", TaskList.PERSONAL, done = at(0, 9)),
                t("Reply to Priya", TaskList.WORK, done = at(0, 8)),
                t("Send invoice", TaskList.WORK, due = td - 1, done = at(1, 17)),
                t("Gym", TaskList.PERSONAL, due = td - 1, done = at(1, 7)),
                t("Return parcel", errands.id, due = td - 2, done = at(2, 12)),
                t("Weekly review", TaskList.WORK, due = td - 3, done = at(3, 18)),
                t("Clean the desk", TaskList.PERSONAL, due = td - 3, done = at(3, 11)),
                t("Laundry", TaskList.PERSONAL, due = td - 3, done = at(3, 10)),
            ),
        )
    }

    private fun start(dark: Boolean, reduced: Boolean = false, autoAdvance: Boolean = false): TodoViewModel {
        seed()
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = reduced) {
                AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.autoAdvance = autoAdvance
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        return vm
    }

    private fun role(r: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, r)
    /** v0.13: Home/Calendar are header tabs, the Todo views sit behind the switcher. */
    private fun tab(name: String) {
        val f = if (name == "Overview" || name == "Home") dev.saikrishna.todo.ui.home.Filter.Overview else dev.saikrishna.todo.ui.home.Filter.valueOf(name)
        if (f == dev.saikrishna.todo.ui.home.Filter.Overview) dev.saikrishna.todo.ui.home.NavTest.section(rule, dev.saikrishna.todo.ui.home.Section.Home) {}
        else dev.saikrishna.todo.ui.home.NavTest.go(rule, f) { settle() }
    }
    private fun chip(name: String) = rule.onNode(hasText(name) and role(Role.RadioButton)).performSemanticsAction(SemanticsActions.OnClick)
    // Idle first, then frame-sized steps: one big advanceTimeBy() right after a semantics click
    // ran before the click was applied, so v0.12.0's "Done" shot showed Upcoming (see HomeTabsTest).
    private fun settle(ms: Long = 1_200) {
        rule.waitForIdle()
        repeat((ms / 100).toInt()) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
    }
    private fun onTab(name: String) {
        if (name == "Overview") rule.onNode(dev.saikrishna.todo.ui.home.NavTest.sectionTab(dev.saikrishna.todo.ui.home.Section.Home)).assertIsSelected()
        else org.junit.Assert.assertEquals("View: $name", rule.onNode(androidx.compose.ui.test.hasTestTag("view-switcher"), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
    }

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

    // ---- Overview ------------------------------------------------------------------------------
    private fun overview(dark: Boolean) {
        start(dark)
        save("v012-today-${mode(dark)}", root())
        val frames = record(14, 32) { tab("Overview") }
        strip("v012-tab-overview-filmstrip-${mode(dark)}", frames, 32)
        settle(); onTab("Overview")
        save("v012-overview-${mode(dark)}", root())
    }

    @Test fun overviewLight() = overview(false)
    @Test fun overviewDark() = overview(true)

    @Test fun overviewReducedMotion() {
        start(false, reduced = true)
        val frames = record(10, 32) { tab("Overview") }
        strip("v012-tab-overview-filmstrip-reduced", frames, 32)
    }

    // ---- categories: accents + chips -----------------------------------------------------------
    private fun categories(dark: Boolean) {
        start(dark)
        save("v012-chips-today-${mode(dark)}", root())
        val frames = record(14, 32) { chip("Work") }
        strip("v012-chip-filter-filmstrip-${mode(dark)}", frames, 32)
        settle()
        save("v012-chips-work-${mode(dark)}", root())
        onTab("Today")
        tab("Upcoming"); settle(); onTab("Upcoming")
        save("v012-chips-upcoming-${mode(dark)}", root())
        tab("Done"); settle(); onTab("Done")
        save("v012-chips-done-${mode(dark)}", root())
    }

    @Test fun categoriesLight() = categories(false)
    @Test fun categoriesDark() = categories(true)

    // ---- capture card with the widget presets -------------------------------------------------
    private class StillVoice(listening: Boolean) : VoiceControl {
        override val listening = listening
        override val available = true
        override fun start(onText: (String) -> Unit, onDone: (String?) -> Unit) {}
        override fun stop() {}
        override fun cancel() {}
    }

    private fun captureShot(name: String, dark: Boolean, preset: CapturePreset) {
        val lists = TaskList.Defaults + errands
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = false) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                            if (dark) listOf(androidx.compose.ui.graphics.Color(0xFF243049), androidx.compose.ui.graphics.Color(0xFF0E1118))
                            else listOf(androidx.compose.ui.graphics.Color(0xFF9DB6DF), androidx.compose.ui.graphics.Color(0xFFE6ECF5)),
                        ))) {
                            CaptureScreen(
                                draft = dev.saikrishna.todo.domain.ShareCapture.parse("", null), lists = lists,
                                defaultList = TaskList.PERSONAL, windowBlur = false, onSave = { _, _, _ -> }, onFinished = {},
                                clock = { LocalDateTime.of(today, java.time.LocalTime.of(9, 0)) }, autoFocus = false,
                                preset = preset, voiceOverride = StillVoice(preset.voice),
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle()
        save("v012-capture-$name-${mode(dark)}", root())
    }

    @Test fun capturePresetWork() = captureShot("work", false, CapturePreset(listId = TaskList.WORK))
    @Test fun capturePresetWorkDark() = captureShot("work", true, CapturePreset(listId = TaskList.WORK))
    @Test fun capturePresetToday() { captureShot("today", false, CapturePreset(dueToday = true)) }
    @Test fun capturePresetTodayDark() { captureShot("today", true, CapturePreset(dueToday = true)) }
    @Test fun capturePresetVoice() { captureShot("voice", false, CapturePreset(voice = true)) }
    @Test fun capturePresetVoiceDark() { captureShot("voice", true, CapturePreset(voice = true)) }

    // ---- widgets ------------------------------------------------------------------------------
    private fun nightCtx(dark: Boolean) = if (!dark) ctx else ctx.createConfigurationContext(
        Configuration(ctx.resources.configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES },
    )

    private fun drawView(v: View, wDp: Int, hDp: Int, dark: Boolean, pad: Int = 16): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val p = (pad * d).toInt()
        val out = Bitmap.createBitmap(w + 2 * p, h + 2 * p, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(if (dark) 0xFF2B3550.toInt() else 0xFF8FA9D6.toInt()) // wallpaper stand-in
        c.translate(p.toFloat(), p.toFloat()); v.draw(c)
        return out
    }

    /** Transparent render (no wallpaper) for the picker preview images. */
    private fun drawBare(v: View, wDp: Int, hDp: Int, scale: Float = 1f): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val out = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.scale(scale, scale); v.draw(c)
        return out
    }

    private fun quickData(): QuickData {
        val td = today.toEpochDay()
        val tasks = listOf(
            Task(id = 1, title = "Call the bank about the card", createdAt = 1, dueDay = td, dueMinute = 10 * 60, listId = TaskList.WORK),
            Task(id = 2, title = "Buy oat milk", createdAt = 2, dueDay = td, listId = TaskList.PERSONAL),
        )
        return QuickData.build(tasks, TaskList.Defaults + errands, today)
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun glance(dark: Boolean, wDp: Int, hDp: Int, content: @androidx.compose.runtime.Composable (android.content.Context) -> Unit): View {
        val c = nightCtx(dark)
        val rv = runBlocking { GlanceRemoteViews().compose(c, DpSize(wDp.dp, hDp.dp)) { content(c) } }.remoteViews
        return rv.apply(c, FrameLayout(c))
    }

    private fun widgets(dark: Boolean) {
        val m = mode(dark)
        val data = quickData()
        val empty = QuickData.build(emptyList(), TaskList.Defaults, today)
        save("v012-w1-pill-$m", drawView(glance(dark, 180, 56) { AddPillContent(it) }, 180, 56, dark))
        save("v012-w1-pill-narrow-$m", drawView(glance(dark, 120, 56) { AddPillContent(it) }, 120, 56, dark))
        save("v012-w2-round-$m", drawView(glance(dark, 64, 64) { AddRoundContent(it) }, 64, 64, dark))
        save("v012-w3-quick-$m", drawView(glance(dark, 370, 56) { QuickListsContent(it, data) }, 370, 56, dark))
        save("v012-w3-quick-narrow-$m", drawView(glance(dark, 200, 56) { QuickListsContent(it, data) }, 200, 56, dark))
        save("v012-w4-hub-$m", drawView(glance(dark, 182, 182) { HubContent(it, data) }, 182, 182, dark))
        save("v012-w4-hub-empty-$m", drawView(glance(dark, 182, 182) { HubContent(it, empty) }, 182, 182, dark))
        save("v012-w4-hub-wide-$m", drawView(glance(dark, 260, 230) { HubContent(it, data) }, 260, 230, dark))
        // The existing Today widget, unchanged, still renders.
        val td = today.toEpochDay()
        val wd = WidgetData.build(listOf(Task(id = 1, title = "Water the plants", createdAt = 1, dueDay = td)), today)
        save("v012-today-widget-$m", drawView(glance(dark, 250, 170) { TodayWidgetContent(it, wd) }, 250, 170, dark))
        // Picker previews (previewLayout, API 31+).
        val c = nightCtx(dark); val host = FrameLayout(c)
        val inf = LayoutInflater.from(c)
        save("v012-preview-pill-$m", drawView(inf.inflate(R.layout.widget_add_pill_preview, host, false), 180, 56, dark))
        save("v012-preview-round-$m", drawView(inf.inflate(R.layout.widget_add_round_preview, host, false), 64, 64, dark))
        save("v012-preview-quick-$m", drawView(inf.inflate(R.layout.widget_quick_lists_preview, host, false), 370, 56, dark))
        save("v012-preview-hub-$m", drawView(inf.inflate(R.layout.widget_hub_preview, host, false), 182, 182, dark))
        if (!dark) {
            // previewImage (pre-31 pickers / fallback): bare light renders, written as res candidates.
            save("v012-previewimage-pill", drawBare(glance(false, 180, 56) { AddPillContent(it) }, 180, 56, 0.75f))
            save("v012-previewimage-round", drawBare(glance(false, 64, 64) { AddRoundContent(it) }, 64, 64, 0.75f))
            save("v012-previewimage-quick", drawBare(glance(false, 370, 56) { QuickListsContent(it, data) }, 370, 56, 0.75f))
            save("v012-previewimage-hub", drawBare(glance(false, 182, 182) { HubContent(it, data) }, 182, 182, 0.75f))
        }
    }

    @Test fun widgetsLight() = widgets(false)
    @Test fun widgetsDark() = widgets(true)
}
