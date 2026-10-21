package dev.saikrishna.todo.shots

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.components.LocalBlurFrozen
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * v0.7.2 composer frost: a long list with rows behind the bottom composer, light + dark.
 * Legibility proxy: luminance standard deviation inside the composer capsule, in the band left
 * of the "+" button and outside the hint text line, with rows behind vs an empty list behind.
 * The difference is structure leaking through the glass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ComposerFrostScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val variant = "after"

    @After fun close() = db.close()

    private val titles = listOf("Ddhdjsbs", "Bbhgg", "Hhhhh", "Buy milk", "Jgghj", "Bghj", "Uhuu", "Ididdj", "Disidis", "Call mom", "Pay rent", "Gym", "Read")

    private fun render(dark: Boolean, rows: Int, frozen: Boolean = false): Pair<Bitmap, Rect> {
        runBlocking { db.taskDao().insertAll(titles.take(rows).mapIndexed { i, t -> Task(id = i + 1L, title = t, createdAt = i + 1L) }) }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = false) {
                        CompositionLocalProvider(LocalBlurFrozen provides frozen) {
                            HomeScreen(vm, onOpenSearch = {}, onOpenSettings = {})
                        }
                    }
                }
            }
        }
        rule.waitUntil(10_000) { vm.tasks.value != null }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        // The composer capsule: the text field's bounds, widened to the capsule row.
        val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        return bmp to field
    }

    private fun lum(p: Int) = 0.2126f * (p shr 16 and 255) + 0.7152f * (p shr 8 and 255) + 0.0722f * (p and 255)

    /**
     * Leak proxy: the same composer captured with the rows behind it and again after the rows are
     * deleted (same composer, same hint). Their difference is only what shows through the glass;
     * we take its local contrast: std dev of the high-passed (pixel minus 9×9 mean) luminance
     * difference over the composer's field. Sharp text edges score high, a soft colour hint ~0.
     */
    private fun leak(a: Bitmap, e: Bitmap, f: Rect): Float {
        val x0 = f.left.toInt() + 8; val x1 = f.right.toInt() - 8
        val y0 = f.top.toInt() + 2; val y1 = f.bottom.toInt() - 2
        val w = x1 - x0 + 9; val h = y1 - y0 + 9
        val d = Array(h) { yy -> DoubleArray(w) { xx -> (lum(a.getPixel(x0 - 4 + xx, y0 - 4 + yy)) - lum(e.getPixel(x0 - 4 + xx, y0 - 4 + yy))).toDouble() } }
        var s = 0.0; var s2 = 0.0; var n = 0
        for (yy in 4 until h - 4) for (xx in 4 until w - 4) {
            var m = 0.0
            for (dy in -4..4) for (dx in -4..4) m += d[yy + dy][xx + dx]
            val hp = d[yy][xx] - m / 81.0
            s += hp; s2 += hp * hp; n++
        }
        val mean = s / n
        return sqrt((s2 / n - mean * mean).coerceAtLeast(0.0)).toFloat()
    }

    private fun save(b: Bitmap, name: String) = FileOutputStream(File(dir, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun run(dark: Boolean, frozen: Boolean = false, name: String? = null): Float {
        val (b, f) = render(dark, titles.size, frozen)
        if (name != null) {
            save(b, name)
            val top = (f.top - 120).toInt().coerceAtLeast(0)
            save(Bitmap.createBitmap(b, 0, top, b.width, b.height - top), "$name-crop")
        }
        runBlocking { db.taskDao().deleteIds(titles.indices.map { it + 1L }) }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        val empty = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        return leak(b, empty, f)
    }

    private fun report(key: String, v: Float) {
        println("FROST $variant $key = ${"%.2f".format(v)}")
        File(dir, "composer-frost-$variant-$key.txt").writeText("%.2f\n".format(v))
    }

    @Test fun light() = report("light", run(false, name = "composer-frost-$variant-light"))
    @Test fun dark() = report("dark", run(true, name = "composer-frost-$variant-dark"))
    /** Tint only (blur off): what the glass hides without any blur, for scale. */
    @Test fun lightTintOnly() = report("light-tint-only", run(false, frozen = true))
}
