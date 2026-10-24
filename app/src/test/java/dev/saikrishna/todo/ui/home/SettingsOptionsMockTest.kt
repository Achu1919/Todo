package dev.saikrishna.todo.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
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
import kotlin.math.roundToInt

/**
 * v0.13.2 Part B — MOCKS ONLY (nothing here ships): where could the Settings entry live?
 * Renders the real Home, then paints a proposal over it with the app's own components:
 *  a) an initial circle ("S", from the Name field) in the greeting, in the gear's place;
 *  b) a "Settings" row at the end of Home (content lifted as if scrolled to the end);
 *  c) Settings in the top bar's left slot, search moved into the composer.
 * Covered originals are hidden with a background patch (one clean background column stretched
 * across: the backdrop is a vertical gradient). Saves out/shots/v0132-settings-option-*.png.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", application = Application::class)
class SettingsOptionsMockTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    @After fun tearDown() { db.close() }

    /** A patch: [dst] is filled with the 1-px column at x = [srcX] of the base capture. */
    /** Optional [dy]: the source row is sampled dy lower at the top edge, easing to 0 at the bottom
     *  (joins a lifted region above to the untouched backdrop below without a seam). */
    private data class Patch(val dst: Rect, val srcX: Float, val dy: Float = 0f)
    /** Content in [top + by, bottom] drawn [by] higher, faded in over [fade] under the top bar. */
    private data class Lift(val top: Float, val bottom: Float, val by: Float, val fade: Float)
    private data class Mock(val patches: List<Patch>, val lift: Lift? = null, val content: @Composable () -> Unit)

    private var base by mutableStateOf<ImageBitmap?>(null)
    private var mock by mutableStateOf<Mock?>(null)

    private fun settle() { rule.waitForIdle(); repeat(15) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() } }
    private fun node(m: androidx.compose.ui.test.SemanticsMatcher) = rule.onNode(m, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun capture(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    private fun save(name: String) {
        val tag = if (dark) "dark" else "light"
        FileOutputStream(File(dir, "v0132-settings-option-$name-$tag.png")).use { capture().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Overlay() {
        val m = mock ?: return
        val b = base ?: return
        Canvas(Modifier.fillMaxSize()) {
            m.lift?.let { l ->
                val top = l.top.roundToInt(); val by = l.by.roundToInt(); val fade = l.fade.roundToInt()
                // The fade zone starts from clean backdrop (a background column), like an edge fade.
                drawImage(b, IntOffset((3 * density).roundToInt(), top), IntSize(1, fade), IntOffset(0, top), IntSize(b.width, fade))
                for (i in 0 until fade) {
                    val y = top + i
                    drawImage(b, IntOffset(0, y + by), IntSize(b.width, 1), IntOffset(0, y), IntSize(b.width, 1), alpha = (i + 0.5f) / fade)
                }
                val h = (l.bottom - l.by).roundToInt() - top - fade
                drawImage(b, IntOffset(0, top + fade + by), IntSize(b.width, h), IntOffset(0, top + fade), IntSize(b.width, h))
            }
            for (p in m.patches) {
                val r = p.dst
                val l = r.left.roundToInt(); val t = r.top.roundToInt(); val pw = r.width.roundToInt(); val ph = r.height.roundToInt()
                if (p.dy == 0f) {
                    drawImage(b, IntOffset(p.srcX.roundToInt(), t), IntSize(1, ph), IntOffset(l, t), IntSize(pw, ph))
                } else for (i in 0 until ph) {
                    val sy = (t + i + p.dy * (1f - i / ph.toFloat())).roundToInt().coerceIn(0, b.height - 1)
                    drawImage(b, IntOffset(p.srcX.roundToInt(), sy), IntSize(1, 1), IntOffset(l, t + i), IntSize(pw, 1))
                }
            }
        }
        m.content()
    }

    @Composable
    private fun At(r: Rect, content: @Composable () -> Unit) {
        Box(Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }) { content() }
    }

    @Test fun renderOptions() {
        val today = LocalDate.now()
        runBlocking {
            db.taskDao().insertAll(listOf(
                Task(id = 1, title = "Water the plants", createdAt = 1, dueDay = today.toEpochDay(), listId = TaskList.PERSONAL),
                Task(id = 2, title = "Draft Q4 roadmap", createdAt = 2, dueDay = today.toEpochDay() + 1, listId = TaskList.WORK),
            ))
        }
        val settings = SettingsRepository(ctx)
        runBlocking { settings.setTheme(if (dark) ThemeMode.Dark else ThemeMode.Light); settings.setGreetingName("Sai") }
        val vm = TodoViewModel(TaskRepository(db), settings)
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = false) {
                        Box(Modifier.fillMaxSize()) {
                            HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {})
                            Overlay()
                        }
                    }
                }
            }
        }
        rule.waitUntil(30_000) { vm.tasks.value != null }
        rule.mainClock.autoAdvance = false
        settle()
        NavTest.section(rule, Section.Home) { settle() }
        settle()
        val px = rule.density.density
        val w = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val gear = node(hasTestTag("home-settings"))
        val search = rule.onNode(hasContentDescription("Search") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val listsEnd = node(hasTestTag("lists-new")).bottom + 6 * px
        val composer = node(hasTestTag("composer-surface"))
        save("0-current")
        base = capture().asImageBitmap()
        val shadow = 9 * px
        val gearPatch = Patch(gear.inflate(shadow), w - 3 * px)
        val searchPatch = Patch(search.inflate(shadow), 3 * px)

        // a) Initial circle in the greeting, where the gear is now.
        mock = Mock(listOf(gearPatch)) {
            At(gear) {
                Box(
                    Modifier.size(44.dp).glassControl(Shapes.full),
                    contentAlignment = Alignment.Center,
                ) { Text("S", style = AppTheme.type.display.copy(fontSize = 21.sp, lineHeight = 21.sp), color = AppTheme.colors.ink) }
            }
        }
        settle(); settle(); save("a-initial")

        // b) A Settings row at the end of Home (everything below the top bar lifted by the row).
        val rowH = 64 * px; val gap = 14 * px; val by = rowH + gap
        val rowTop = listsEnd - rowH
        mock = Mock(
            listOf(Patch(Rect(0f, listsEnd - by, w, listsEnd + 2), 3 * px, dy = by)),
            lift = Lift(search.bottom + 6 * px, listsEnd, by, fade = 40 * px),
        ) {
            val c = AppTheme.colors
            At(Rect(20 * px, rowTop, w - 20 * px, listsEnd)) {
                Row(
                    Modifier.width(with(LocalDensity.current) { (w - 40 * px).toDp() })
                        .glassSurface(Shapes.lg, GlassLevel.Low, c.glassCard)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Gear, null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Settings", style = AppTheme.type.body, color = c.ink)
                        Text("Theme, name, lists, backup", style = AppTheme.type.meta, color = c.inkSecondary)
                    }
                    Icon(AppIcons.ChevronRight, null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        settle(); settle(); save("b-row")

        // c) Settings takes the top bar's left slot; search moves into the composer.
        val searchIcon = Rect(composer.right - 132 * px, composer.center.y - 11 * px, composer.right - 110 * px, composer.center.y + 11 * px)
        mock = Mock(listOf(gearPatch, searchPatch)) {
            At(search) {
                Box(Modifier.size(44.dp).glassControl(Shapes.full), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Gear, null, tint = AppTheme.colors.ink, modifier = Modifier.size(20.dp))
                }
            }
            At(searchIcon) { Icon(AppIcons.Search, null, tint = AppTheme.colors.ink, modifier = Modifier.size(22.dp)) }
        }
        settle(); settle(); save("c-topleft")
        mock = null
        runBlocking { settings.setTheme(ThemeMode.System); settings.setGreetingName("") }
    }
}
