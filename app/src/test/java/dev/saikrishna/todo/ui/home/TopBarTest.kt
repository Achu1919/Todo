package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.abs

/**
 * v0.13.2 top bar: search circle (left) | Home · Todo · Calendar (centre) | theme toggle (right).
 * On every tab, light + dark, font scale 1.0 + 1.3: the two circles mirror each other (size,
 * vertical centre, edge inset), the pill is centred on the screen with a gap to both circles, no
 * tab label is cut, and there is exactly one theme toggle. Saves out/shots/v0132-topbar-*.png.
 */
abstract class TopBarBase(private val dark: Boolean, private val scale: Float) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    abstract val widthTag: String

    @After fun tearDown() { db.close() }

    private fun settle() { rule.waitForIdle(); repeat(15) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() } }

    private fun bounds(m: androidx.compose.ui.test.SemanticsMatcher): List<Rect> =
        rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun check(where: String) {
        val px = rule.density.density
        val w = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val toggles = bounds(androidx.compose.ui.test.hasTestTag("theme-toggle"))
        assertEquals("$where: exactly one theme toggle", 1, toggles.size)
        val t = toggles.single()
        val s = rule.onNode(hasContentDescription("Search") and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNode().boundsInRoot
        val tabs = Section.entries.map { rule.onNode(NavTest.sectionTab(it)).fetchSemanticsNode().boundsInRoot }
        val pill = Rect(tabs.minOf { it.left }, tabs.minOf { it.top }, tabs.maxOf { it.right }, tabs.maxOf { it.bottom })
        val msg = "$where: search=$s toggle=$t pill=$pill width=$w (px/dp=$px)"
        assertTrue("$msg: same size", abs(s.width - t.width) < 1f && abs(s.height - t.height) < 1f)
        assertTrue("$msg: 44 dp", abs(t.width - 44 * px) < 1.5f)
        assertTrue("$msg: same vertical centre", abs(s.center.y - t.center.y) < 1f)
        assertTrue("$msg: pill on the same centre line", abs(pill.center.y - t.center.y) < 1.5f)
        assertTrue("$msg: same edge inset", abs(s.left - (w - t.right)) < 1.5f)
        assertTrue("$msg: 20 dp inset", abs(s.left - 20 * px) < 1.5f)
        assertTrue("$msg: pill centred on screen", abs(pill.center.x - w / 2f) < 1.5f * px)
        assertTrue("$msg: gap to search", pill.left - s.right >= 8 * px)
        assertTrue("$msg: gap to toggle", t.left - pill.right >= 8 * px)
        // No tab label is cut: one line, no ellipsis, the glyphs inside the text box. (didOverflowWidth
        // is not used: the pill's intrinsic pass leaves a wider paragraph width than the segment.)
        for (sec in Section.entries) {
            val nodes = rule.onAllNodes(hasText(sec.title), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.center.y < s.bottom && it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
            assertTrue("$where: ${sec.title} label found", nodes.isNotEmpty())
            for (n in nodes) {
                val out = mutableListOf<TextLayoutResult>()
                n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
                val r = out.single()
                assertTrue("$where: '${sec.title}' cut: size=${r.size} ell=${r.isLineEllipsized(0)} lines=${r.lineCount} lw=${r.getLineRight(0)} node=${n.boundsInRoot}", r.lineCount == 1 && !r.isLineEllipsized(0) && r.getLineRight(0) <= r.size.width + 0.5f && r.getLineLeft(0) >= -0.5f)
            }
        }
    }

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val tag = "${if (dark) "dark" else "light"}-${(scale * 100).toInt()}$widthTag"
        FileOutputStream(File(dir, "v0132-topbar-$name-$tag.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun mirroredOnEveryTab() {
        val today = LocalDate.now()
        runBlocking {
            db.taskDao().insertAll(listOf(
                Task(id = 1, title = "Water the plants", createdAt = 1, dueDay = today.toEpochDay(), listId = TaskList.PERSONAL),
                Task(id = 2, title = "Draft Q4 roadmap", createdAt = 2, dueDay = today.toEpochDay() + 1, listId = TaskList.WORK),
            ))
        }
        val settings = SettingsRepository(ctx)
        runBlocking { settings.setTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) }
        val vm = TodoViewModel(TaskRepository(db), settings)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                ProvideMotion(userPrefReduceMotion = false) {
                    AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {}) } }
                }
            }
        }
        rule.waitUntil(30_000) { vm.tasks.value != null }
        rule.mainClock.autoAdvance = false
        settle()
        for (sec in listOf(Section.Home, Section.Todo, Section.Calendar)) {
            NavTest.section(rule, sec) { settle() }
            check(sec.name)
            save(sec.name.lowercase())
        }
        // Home's greeting row keeps only the Settings entry (profile circle since v0.13.3).
        NavTest.section(rule, Section.Home) { settle() }
        assertEquals(1, rule.onAllNodes(androidx.compose.ui.test.hasTestTag("home-settings")).fetchSemanticsNodes().size)
        // The toggle still switches the theme (no reveal host here: straight to the setting).
        rule.onNode(androidx.compose.ui.test.hasTestTag("theme-toggle")).performSemanticsAction(SemanticsActions.OnClick)
        val want = if (dark) ThemeMode.Light else ThemeMode.Dark
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end && runBlocking { settings.settings.first().theme } != want) { Thread.sleep(20); rule.waitForIdle() }
        assertEquals(want, runBlocking { settings.settings.first().theme })
        runBlocking { settings.setTheme(ThemeMode.System) }
    }
}

private fun params() = listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1.3f), arrayOf<Any>(true, 1.3f))

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", application = Application::class)
class TopBarTest(dark: Boolean, scale: Float) : TopBarBase(dark, scale) {
    override val widthTag = ""
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} scale={1}")
        fun params() = dev.saikrishna.todo.ui.home.params()
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-420dpi", application = Application::class)
class TopBarNarrowTest(dark: Boolean, scale: Float) : TopBarBase(dark, scale) {
    override val widthTag = "-360"
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} scale={1}")
        fun params() = dev.saikrishna.todo.ui.home.params()
    }
}
