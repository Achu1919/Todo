package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v0.13.3 Settings entry: the profile circle in Home's greeting row. Light + dark, font scale 1.0 +
 * 1.3, a name and no name, 412 + 360 dp: exactly one "home-settings" node described "Settings,
 * profile"; the Name's initial (or the person glyph when empty); 44 dp, right-aligned with the theme
 * toggle above, clear of the greeting text; its disc visibly unlike the toggle's glass; a tap opens
 * Settings. Saves out/shots/v0133-settings-entry-*.png.
 */
abstract class ProfileEntryBase(private val dark: Boolean, private val scale: Float, private val name: String) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    abstract val widthTag: String

    @After fun tearDown() { db.close() }

    private fun settle() { rule.waitForIdle(); repeat(15) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() } }

    /** Mean colour on a ring of radius [rDp] around [c] (clear of the mark inside). */
    private fun ringColour(bmp: Bitmap, c: Rect, rDp: Float): FloatArray {
        val r = rDp * rule.density.density
        val acc = FloatArray(3); var n = 0
        for (k in 0 until 72) {
            val a = k * Math.PI * 2 / 72
            val x = (c.center.x + r * cos(a)).toInt(); val y = (c.center.y + r * sin(a)).toInt()
            val p = bmp.getPixel(x, y)
            acc[0] += (p shr 16 and 255); acc[1] += (p shr 8 and 255); acc[2] += (p and 255); n++
        }
        return floatArrayOf(acc[0] / n, acc[1] / n, acc[2] / n)
    }

    private fun slug() = when {
        name.isBlank() -> "-empty"
        name.first().isLetter() && name.first().code < 0x250 -> ""
        name.first().isLetter() -> "-devanagari"
        else -> "-emoji"
    }

    @Test fun profileCircle() {
        val settings = SettingsRepository(ctx)
        runBlocking { settings.setTheme(if (dark) ThemeMode.Dark else ThemeMode.Light); settings.setGreetingName(name) }
        val vm = TodoViewModel(TaskRepository(db), settings)
        var opened = 0
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                ProvideMotion(userPrefReduceMotion = false) {
                    AppTheme(darkTheme = dark) { ProvideHaptics(userEnabled = false) { HomeScreen(vm, onOpenSearch = {}, onOpenSettings = { opened++ }) } }
                }
            }
        }
        rule.waitUntil(30_000) { vm.tasks.value != null && vm.settings.value?.name == name }
        rule.mainClock.autoAdvance = false
        settle()
        NavTest.section(rule, Section.Home) { settle() }

        val px = rule.density.density
        val w = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val nodes = rule.onAllNodes(hasTestTag("home-settings")).fetchSemanticsNodes()
        assertEquals("one Settings entry", 1, nodes.size)
        val node = nodes.single()
        assertEquals(listOf("Settings, profile"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue("clickable", node.config.getOrNull(SemanticsActions.OnClick) != null)
        val c = node.boundsInRoot
        assertTrue("44 dp: $c", abs(c.width - 44 * px) < 1.5f && abs(c.height - 44 * px) < 1.5f)
        val t = rule.onNode(hasTestTag("theme-toggle")).fetchSemanticsNode().boundsInRoot
        assertTrue("right edge lines up with the theme toggle: circle=$c toggle=$t", abs(c.right - t.right) < 1.5f)
        assertTrue("on screen", c.left >= 0f && c.right <= w)

        val initials = rule.onAllNodes(hasTestTag("profile-initial"), useUnmergedTree = true).fetchSemanticsNodes()
        val glyphs = rule.onAllNodes(hasTestTag("profile-glyph"), useUnmergedTree = true).fetchSemanticsNodes()
        if (name.isBlank()) {
            assertEquals("no letter when the name is empty", 0, initials.size)
            assertEquals("person glyph when the name is empty", 1, glyphs.size)
        } else {
            assertEquals("no glyph with a name", 0, glyphs.size)
            val text = initials.single().config[SemanticsProperties.Text].joinToString("") { it.text }
            assertEquals(profileInitial(name), text)
            val mark = initials.single().boundsInRoot
            assertTrue("initial centred in the circle: $mark in $c", abs(mark.center.x - c.center.x) < 2 * px && abs(mark.center.y - c.center.y) < 3 * px)
            assertTrue("initial inside the circle", mark.width < c.width && mark.height < c.height)
            // The greeting (which carries the name) stays clear of the circle.
            val g = rule.onAllNodes(hasText(name, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.top < c.bottom + 40 * px && it.boundsInRoot.bottom > c.top - 40 * px }
            assertTrue("greeting found", g.isNotEmpty())
            for (n in g) assertTrue("greeting ${n.boundsInRoot} clear of circle $c", n.boundsInRoot.right <= c.left + 0.5f)
        }

        // Tinted disc vs the theme toggle's glass: clearly different colour.
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val a = ringColour(bmp, c, 15f); val b = ringColour(bmp, t, 15f)
        val dist = sqrt((0..2).sumOf { ((a[it] - b[it]) * (a[it] - b[it])).toDouble() })
        println("v0133 ring circle=${a.toList()} toggle=${b.toList()} dist=$dist")
        assertTrue("disc distinct from toggle (dist=$dist)", dist > 18)

        val tag = "${if (dark) "dark" else "light"}-${(scale * 100).toInt()}${slug()}$widthTag"
        FileOutputStream(File(dir, "v0133-settings-entry-$tag.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        rule.onNode(hasTestTag("home-settings")).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals("tap opens Settings", 1, opened)
    }
}

private fun entryParams() = listOf(
    arrayOf<Any>(false, 1f, "Sai"), arrayOf<Any>(true, 1f, "Sai"),
    arrayOf<Any>(false, 1f, ""), arrayOf<Any>(true, 1f, ""),
    arrayOf<Any>(false, 1.3f, "Sai Krishna"), arrayOf<Any>(true, 1.3f, "Sai Krishna"),
    arrayOf<Any>(false, 1.3f, ""),
    arrayOf<Any>(false, 1f, "👩‍💻 Dev"), arrayOf<Any>(false, 1f, "सुमन"),
)

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", application = Application::class)
class ProfileEntryTest(dark: Boolean, scale: Float, name: String) : ProfileEntryBase(dark, scale, name) {
    override val widthTag = ""
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} scale={1} name={2}")
        fun params() = entryParams()
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-420dpi", application = Application::class)
class ProfileEntryNarrowTest(dark: Boolean, scale: Float, name: String) : ProfileEntryBase(dark, scale, name) {
    override val widthTag = "-360"
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} scale={1} name={2}")
        fun params() = listOf(
            arrayOf<Any>(false, 1.3f, "Sai Krishna"), arrayOf<Any>(true, 1.3f, "Sai Krishna"),
            arrayOf<Any>(false, 1.3f, ""), arrayOf<Any>(true, 1f, "Sai"),
        )
    }
}
