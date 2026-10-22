package dev.saikrishna.todo.shots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.R
import dev.saikrishna.todo.data.Backup
import dev.saikrishna.todo.data.SettingsRepository
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.settings.ImportSheet
import dev.saikrishna.todo.ui.settings.ImportStep
import dev.saikrishna.todo.ui.settings.SettingsScreen
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** v0.10 shots: launcher icon variants + launcher mock, Settings → Data, import preview. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class V10ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val db = TodoDatabase.inMemory(ctx)
    private val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    @After fun close() = db.close()
    private fun save(b: Bitmap, name: String) = FileOutputStream(File(dir, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    // ---- icons -----------------------------------------------------------------------------

    private data class Variant(val bg: Int, val fg: Int, val mono: Int)
    private val a = Variant(R.drawable.icon_a_background, R.drawable.icon_a_foreground, R.drawable.icon_a_monochrome)
    private val b = Variant(R.drawable.icon_b_background, R.drawable.icon_b_foreground, R.drawable.icon_b_monochrome)

    /** Draws an adaptive icon at [size] px (the 72/108 visible part) clipped by [mask]. */
    private fun drawIcon(c: Canvas, v: Variant, x: Float, y: Float, size: Float, mask: Path.() -> Unit, themed: Pair<Int, Int>? = null) {
        val full = size * 108f / 72f
        val off = (full - size) / 2f
        c.save()
        c.translate(x, y)
        val clip = Path().apply(mask)
        c.clipPath(clip)
        if (themed == null) {
            ctx.getDrawable(v.bg)!!.apply { setBounds((-off).toInt(), (-off).toInt(), (size + off).toInt(), (size + off).toInt()); draw(c) }
            ctx.getDrawable(v.fg)!!.apply { setBounds((-off).toInt(), (-off).toInt(), (size + off).toInt(), (size + off).toInt()); draw(c) }
        } else {
            c.drawColor(themed.first)
            ctx.getDrawable(v.mono)!!.mutate().apply {
                colorFilter = PorterDuffColorFilter(themed.second, PorterDuff.Mode.SRC_IN)
                setBounds((-off).toInt(), (-off).toInt(), (size + off).toInt(), (size + off).toInt()); draw(c)
            }
        }
        c.restore()
    }
    private fun circle(s: Float): Path.() -> Unit = { addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }
    private fun squircle(s: Float): Path.() -> Unit = { addRoundRect(RectF(0f, 0f, s, s), s * 0.3f, s * 0.3f, Path.Direction.CW) }

    @Test fun iconVariants() {
        for ((name, v) in listOf("icon-a" to a, "icon-b" to b)) {
            val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            drawIcon(Canvas(bmp), v, 0f, 0f, 512f, circle(512f))
            save(bmp, name)
            // Sanity: the centre is the mark, the corners are transparent (masked).
            assertTrue(Color.alpha(bmp.getPixel(2, 2)) == 0 && Color.alpha(bmp.getPixel(256, 256)) == 255)
        }
        launcherMock()
    }

    /** A launcher home screen mock: icons at 48 dp (126 px at 420 dpi), light + dark + themed. */
    private fun launcherMock() {
        val w = 1080; val h = 1500
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val ghost = Paint(Paint.ANTI_ALIAS_FLAG)
        val icon = 126f
        val cols = 4
        val cellW = w / cols.toFloat()
        // Three bands: light wallpaper (A, B), dark wallpaper (A, B), themed icons (A, B mono).
        val bands = listOf(
            Triple("Light wallpaper", intArrayOf(0xFFB9C6E4.toInt(), 0xFFEEF1F8.toInt()), null),
            Triple("Dark wallpaper", intArrayOf(0xFF0B0F1A.toInt(), 0xFF27304A.toInt()), null),
            Triple("Themed icons (Android 13+)", intArrayOf(0xFFE6E9F2.toInt(), 0xFFD5DBEA.toInt()), 0xFFD6DEF7.toInt() to 0xFF26315F.toInt()),
        )
        val bandH = h / bands.size
        bands.forEachIndexed { i, (title, colors, themed) ->
            val top = i * bandH.toFloat()
            val paint = Paint().apply { shader = LinearGradient(0f, top, 0f, top + bandH, colors[0], colors[1], Shader.TileMode.CLAMP) }
            c.drawRect(0f, top, w.toFloat(), top + bandH, paint)
            val dark = i == 1
            head.color = if (dark) 0xFFE8ECF5.toInt() else 0xFF1C2233.toInt()
            label.color = head.color
            c.drawText(title, 40f, top + 70f, head)
            for (row in 0 until 2) for (col in 0 until cols) {
                val cx = col * cellW + cellW / 2
                val y = top + 120f + row * 200f
                val x = cx - icon / 2
                val mine = row == 0 && col <= 1
                if (mine) {
                    val v = if (col == 0) a else b
                    drawIcon(c, v, x, y, icon, circle(icon), themed)
                    c.drawText(if (col == 0) "Todo (A)" else "Todo (B)", cx, y + icon + 44f, label)
                } else {
                    // Neutral placeholders (not other apps' icons).
                    ghost.color = when {
                        themed != null -> themed.first
                        dark -> 0x33FFFFFF
                        else -> 0x66FFFFFF
                    }
                    c.drawCircle(cx, y + icon / 2, icon / 2, ghost)
                    ghost.color = if (dark) 0x22FFFFFF else 0x22000000
                    c.drawRoundRect(RectF(cx - 70f, y + icon + 22f, cx + 70f, y + icon + 40f), 9f, 9f, ghost)
                }
            }
        }
        // Square-ish mask sample (some launchers): A and B at 48 dp in the last band's spare cells.
        val y = 2 * bandH + 320f
        drawIcon(c, a, 2 * cellW + cellW / 2 - icon / 2, y, icon, squircle(icon))
        drawIcon(c, b, 3 * cellW + cellW / 2 - icon / 2, y, icon, squircle(icon))
        save(bmp, "icon-preview")
    }

    /**
     * v0.10.1: icon A old (pure-white disc, debug-only copy of the v0.10.0 drawable) vs new
     * (off-white #E6E8EE → #DCE0E8) on light and dark launchers, and on the splash backgrounds
     * (the splash icon is the same background + foreground layers).
     */
    @Test fun iconADullPreview() {
        val old = a.copy(fg = R.drawable.icon_a_foreground_v0100)
        val w = 1080; val h = 1560
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; textAlign = Paint.Align.CENTER }
        val bands = listOf(
            Triple("Light launcher", intArrayOf(0xFFB9C6E4.toInt(), 0xFFEEF1F8.toInt()), false),
            Triple("Dark launcher", intArrayOf(0xFF0B0F1A.toInt(), 0xFF27304A.toInt()), true),
            Triple("Splash: light | dark", intArrayOf(0xFFCDD6E5.toInt(), 0xFFCDD6E5.toInt()), false),
        )
        val bandH = h / 3
        bands.forEachIndexed { i, (title, colors, dark) ->
            val top = i * bandH.toFloat()
            val p = Paint().apply { shader = LinearGradient(0f, top, 0f, top + bandH, colors[0], colors[1], Shader.TileMode.CLAMP) }
            c.drawRect(0f, top, w.toFloat(), top + bandH, p)
            if (i == 2) c.drawRect(w / 2f, top, w.toFloat(), top + bandH, Paint().apply { color = 0xFF1B2231.toInt() })
            head.color = if (dark) 0xFFE8ECF5.toInt() else 0xFF1C2233.toInt()
            c.drawText(title, 40f, top + 64f, head)
            if (i < 2) {
                label.color = head.color
                // 256 px pair, then the 48 dp (126 px) pair.
                val big = 256f
                drawIcon(c, old, 60f, top + 110f, big, circle(big))
                drawIcon(c, a, 360f, top + 110f, big, circle(big))
                c.drawText("old v0.10.0", 60f + big / 2, top + 110f + big + 44f, label)
                c.drawText("new v0.10.1", 360f + big / 2, top + 110f + big + 44f, label)
                val small = 126f
                drawIcon(c, old, 700f, top + 175f, small, circle(small))
                drawIcon(c, a, 880f, top + 175f, small, circle(small))
                c.drawText("old · 48dp", 700f + small / 2, top + 175f + small + 44f, label)
                c.drawText("new · 48dp", 880f + small / 2, top + 175f + small + 44f, label)
            } else {
                // Splash: the icon masked to a circle (2/3 of the 240 dp icon area ≈ 160 dp → 280 px here).
                val s = 220f
                for ((k, x0) in listOf(0f, w / 2f).withIndex()) {
                    label.color = if (k == 0) 0xFF1C2233.toInt() else 0xFFE8ECF5.toInt()
                    drawIcon(c, old, x0 + 20f, top + 120f, s, circle(s))
                    drawIcon(c, a, x0 + 290f, top + 120f, s, circle(s))
                    c.drawText("old", x0 + 20f + s / 2, top + 120f + s + 44f, label)
                    c.drawText("new", x0 + 290f + s / 2, top + 120f + s + 44f, label)
                }
            }
        }
        save(bmp, "icon-a-dull")
        // The disc centre-top is no longer pure white.
        val probe = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        drawIcon(Canvas(probe), a, 0f, 0f, 512f, circle(512f))
        val px = probe.getPixel(256, 160)
        assertTrue("disc still white: ${Integer.toHexString(px)}", Color.red(px) < 240 && Color.blue(px) < 245)
        save(probe, "icon-a")
    }

    // ---- Settings → Data and the import sheet ----------------------------------------------

    private var step by mutableStateOf<ImportStep?>(null)

    private fun settings(dark: Boolean) {
        runBlocking {
            db.taskDao().insertAll((1..17L).map { Task(id = it, title = "Task $it", createdAt = it, sortOrder = it) })
        }
        val vm = TodoViewModel(TaskRepository(db), SettingsRepository(ctx))
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = false) {
                        Box(Modifier.fillMaxSize()) {
                            SettingsScreen(vm, onBack = {}, onOpenLicenses = {})
                            ImportSheet(step, onMerge = {}, onReplace = { _, _ -> }, onConfirmReplace = {}, onDismiss = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Share open tasks").performScrollTo()
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
    }

    private fun backup(): Backup {
        val now = 1_792_000_000_000L
        val tasks = (1..42L).map { Task(id = it, title = "T$it", createdAt = it, done = it % 4 == 0L, sortOrder = it) }
        return Backup(1, now, "0.10.0", tasks, (1..12L).map { Subtask(it, 1, "s$it", false, it.toInt()) },
            listOf(TaskList(1, "Personal"), TaskList(2, "Work"), TaskList(3, "Home")), emptyList(), emptyList(), null)
    }

    private fun root(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap()

    @Test fun dataSectionAndImportPreviewLight() = sheetShots(false)
    @Test fun dataSectionAndImportPreviewDark() = sheetShots(true)

    private fun sheetShots(dark: Boolean) {
        val suffix = if (dark) "dark" else "light"
        settings(dark)
        save(root(), "settings-data-$suffix")
        step = ImportStep.Preview(backup(), 17)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        save(root(), "import-preview-$suffix")
        step = ImportStep.ConfirmReplace(backup(), 17)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        save(root(), "import-replace-$suffix")
        step = ImportStep.Failed("This backup was made by a newer version of the app. Update the app, then import it.")
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        save(root(), "import-error-$suffix")
    }
}
