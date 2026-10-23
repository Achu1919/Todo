package dev.saikrishna.todo.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Our own minimal stroke icons on a 24-unit grid (avoids pulling in material-icons-extended). */
object AppIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 2f, pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )

    val Plus: ImageVector by lazy {
        icon("Plus") {
            stroke { moveTo(12f, 5f); lineTo(12f, 19f) }
            stroke { moveTo(5f, 12f); lineTo(19f, 12f) }
        }
    }

    val Minus: ImageVector by lazy {
        icon("Minus") { stroke { moveTo(5f, 12f); lineTo(19f, 12f) } }
    }

    val ArrowUp: ImageVector by lazy {
        icon("ArrowUp") {
            stroke(2.2f) { moveTo(12f, 19f); lineTo(12f, 5.5f) }
            stroke(2.2f) { moveTo(6.5f, 11f); lineTo(12f, 5.5f); lineTo(17.5f, 11f) }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            stroke(2f) { moveTo(6.5f, 9.5f); lineTo(12f, 15f); lineTo(17.5f, 9.5f) }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            stroke(2f) {
                moveTo(17f, 10.5f)
                arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17f, y1 = 10.49f)
                close()
            }
            stroke(2f) { moveTo(15.5f, 15.5f); lineTo(20f, 20f) }
        }
    }

    /** Three slider tracks with knobs: our "settings" mark. */
    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            stroke(1.9f) { moveTo(4f, 7f); lineTo(20f, 7f) }
            stroke(1.9f) { moveTo(4f, 12f); lineTo(20f, 12f) }
            stroke(1.9f) { moveTo(4f, 17f); lineTo(20f, 17f) }
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 7f); arcTo(2.2f, 2.2f, 0f, true, true, 8.99f, 7f); close()
                moveTo(16f, 12f); arcTo(2.2f, 2.2f, 0f, true, true, 15.99f, 12f); close()
                moveTo(11f, 17f); arcTo(2.2f, 2.2f, 0f, true, true, 10.99f, 17f); close()
            }
        }
    }

    val ArrowLeft: ImageVector by lazy {
        icon("ArrowLeft") {
            stroke(2f) { moveTo(19f, 12f); lineTo(5.5f, 12f) }
            stroke(2f) { moveTo(11f, 6.5f); lineTo(5.5f, 12f); lineTo(11f, 17.5f) }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            stroke(2f) { moveTo(9.5f, 6.5f); lineTo(15f, 12f); lineTo(9.5f, 17.5f) }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            stroke(1.9f) { moveTo(4.5f, 7f); lineTo(19.5f, 7f) }
            stroke(1.9f) { moveTo(9.5f, 7f); lineTo(10f, 4.5f); lineTo(14f, 4.5f); lineTo(14.5f, 7f) }
            stroke(1.9f) { moveTo(6.5f, 7f); lineTo(7.5f, 19.5f); lineTo(16.5f, 19.5f); lineTo(17.5f, 7f) }
            stroke(1.9f) { moveTo(10.5f, 11f); lineTo(10.5f, 16f) }
            stroke(1.9f) { moveTo(13.5f, 11f); lineTo(13.5f, 16f) }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            stroke(2.2f) { moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f) }
        }
    }

    /** Two arcs chasing each other (repeat). */
    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            stroke(2f) { moveTo(17f, 3.5f); lineTo(20f, 6.5f); lineTo(17f, 9.5f) }
            stroke(2f) { moveTo(4f, 11.5f); lineTo(4f, 10.5f); arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 8f, y1 = 6.5f); lineTo(20f, 6.5f) }
            stroke(2f) { moveTo(7f, 20.5f); lineTo(4f, 17.5f); lineTo(7f, 14.5f) }
            stroke(2f) { moveTo(20f, 12.5f); lineTo(20f, 13.5f); arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 16f, y1 = 17.5f); lineTo(4f, 17.5f) }
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            stroke(2f) {
                moveTo(6f, 5.5f); lineTo(18f, 5.5f); arcTo(2f, 2f, 0f, false, true, 20f, 7.5f); lineTo(20f, 18f)
                arcTo(2f, 2f, 0f, false, true, 18f, 20f); lineTo(6f, 20f); arcTo(2f, 2f, 0f, false, true, 4f, 18f)
                lineTo(4f, 7.5f); arcTo(2f, 2f, 0f, false, true, 6f, 5.5f); close()
            }
            stroke(2f) { moveTo(4f, 10f); lineTo(20f, 10f) }
            stroke(2f) { moveTo(8.5f, 3.5f); lineTo(8.5f, 7f) }
            stroke(2f) { moveTo(15.5f, 3.5f); lineTo(15.5f, 7f) }
        }
    }

    val Flag: ImageVector by lazy {
        icon("Flag") {
            stroke(2f) { moveTo(6f, 21f); lineTo(6f, 4f) }
            stroke(2f) { moveTo(6f, 4.5f); lineTo(18f, 4.5f); lineTo(15f, 8.75f); lineTo(18f, 13f); lineTo(6f, 13f) }
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke(2f) {
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 11.99f, y1 = 3.5f)
                close()
            }
            stroke(2f) { moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f) }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 7f); lineTo(16f, 7f); arcTo(1f, 1f, 0f, false, true, 17f, 8f); lineTo(17f, 16f)
                arcTo(1f, 1f, 0f, false, true, 16f, 17f); lineTo(8f, 17f); arcTo(1f, 1f, 0f, false, true, 7f, 16f)
                lineTo(7f, 8f); arcTo(1f, 1f, 0f, false, true, 8f, 7f); close()
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            stroke(2f) { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f) }
            stroke(2f) { moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            // capsule body
            stroke(1.8f) {
                moveTo(9f, 6.5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 15f, y1 = 6.5f)
                lineTo(15f, 11.5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 9f, y1 = 11.5f)
                close()
            }
            // cradle + stem
            stroke(1.8f) {
                moveTo(5.5f, 11f)
                arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18.5f, y1 = 11f)
            }
            stroke(1.8f) { moveTo(12f, 17.5f); lineTo(12f, 20.5f) }
        }
    }

    /** "#" for tags (v0.7). */
    val Hash: ImageVector by lazy {
        icon("Hash") {
            stroke(1.9f) { moveTo(9.5f, 4.5f); lineTo(8f, 19.5f) }
            stroke(1.9f) { moveTo(16f, 4.5f); lineTo(14.5f, 19.5f) }
            stroke(1.9f) { moveTo(5f, 9f); lineTo(19.5f, 9f) }
            stroke(1.9f) { moveTo(4.5f, 15f); lineTo(19f, 15f) }
        }
    }

    /** Drag handle: two rows of three dots, drawn as short round strokes. */
    val Grip: ImageVector by lazy {
        icon("Grip") {
            for (y in listOf(9f, 15f)) for (x in listOf(7f, 12f, 17f)) stroke(2.6f) { moveTo(x, y); lineTo(x + 0.01f, y) }
        }
    }

    /** Stacked cards: templates (v0.7). */
    val Stack: ImageVector by lazy {
        icon("Stack") {
            stroke(1.9f) {
                moveTo(6f, 9f); lineTo(18f, 9f); lineTo(18f, 19f); lineTo(6f, 19f); close()
            }
            stroke(1.9f) { moveTo(8f, 6f); lineTo(16f, 6f) }
            stroke(1.9f) { moveTo(10f, 3.5f); lineTo(14f, 3.5f) }
        }
    }

    /** Concentric target: focus mode (v0.7). */
    val Focus: ImageVector by lazy {
        icon("Focus") {
            stroke(1.9f) {
                moveTo(20f, 12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 20f, y1 = 11.99f)
                close()
            }
            stroke(2.6f) { moveTo(12f, 12f); lineTo(12.01f, 12f) }
        }
    }

    /** Three short lines: lists (v0.7). */
    val Lists: ImageVector by lazy {
        icon("Lists") {
            for (y in listOf(7f, 12f, 17f)) {
                stroke(2.4f) { moveTo(5f, y); lineTo(5.01f, y) }
                stroke(1.9f) { moveTo(9f, y); lineTo(19f, y) }
            }
        }
    }

    /** v0.9 lists button: three stacked layers (a diamond and two chevrons under it). */
    val Layers: ImageVector by lazy {
        icon("Layers") {
            stroke(1.9f) { moveTo(12f, 4f); lineTo(20f, 8.5f); lineTo(12f, 13f); lineTo(4f, 8.5f); close() }
            stroke(1.9f) { moveTo(4f, 12.5f); lineTo(12f, 17f); lineTo(20f, 12.5f) }
            stroke(1.9f) { moveTo(4f, 16.5f); lineTo(12f, 21f); lineTo(20f, 16.5f) }
        }
    }

    /** v0.8 planning: a small sun (circle + 8 short rays). */
    val Sun: ImageVector by lazy {
        icon("Sun") {
            stroke(2f) {
                moveTo(16f, 12f); arcTo(4f, 4f, 0f, true, true, 8f, 12f); arcTo(4f, 4f, 0f, true, true, 16f, 12f); close()
            }
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
                stroke(2f) { moveTo(12f + 6.5f * c, 12f + 6.5f * s); lineTo(12f + 8.8f * c, 12f + 8.8f * s) }
            }
        }
    }

    /** v0.8 saved views: a bookmark ribbon. */
    val Bookmark: ImageVector by lazy {
        icon("Bookmark") {
            stroke(2f) {
                moveTo(7f, 4.5f); lineTo(17f, 4.5f); lineTo(17f, 20f); lineTo(12f, 16.2f); lineTo(7f, 20f); close()
            }
        }
    }

    // ---- v0.13 ----

    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy); arcTo(r, r, 0f, true, true, cx - r, cy); arcTo(r, r, 0f, true, true, cx + r, cy); close()
    }

    /** Two bars: pause a repeat. */
    val Pause: ImageVector by lazy {
        icon("Pause") {
            stroke(2.2f) { moveTo(9f, 6.5f); lineTo(9f, 17.5f) }
            stroke(2.2f) { moveTo(15f, 6.5f); lineTo(15f, 17.5f) }
        }
    }

    /** Rounded triangle: resume a repeat. */
    val Play: ImageVector by lazy {
        icon("Play") { stroke(2f) { moveTo(8.5f, 6f); lineTo(18f, 12f); lineTo(8.5f, 18f); close() } }
    }

    /** Settings gear: a ring with six short teeth. */
    val Gear: ImageVector by lazy {
        icon("Gear") {
            stroke(1.9f) { circle(12f, 12f, 3f) }
            stroke(1.9f) { circle(12f, 12f, 6.5f) }
            for (k in 0 until 6) {
                val a = Math.toRadians(k * 60.0 + 30.0)
                val c = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
                stroke(2.6f) { moveTo(12f + 7.2f * c, 12f + 7.2f * sn); lineTo(12f + 9f * c, 12f + 9f * sn) }
            }
        }
    }

    /** Bell: reminders. */
    val Bell: ImageVector by lazy {
        icon("Bell") {
            stroke(1.9f) {
                moveTo(6f, 16.5f); lineTo(6f, 11f)
                curveTo(6f, 7.5f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.5f, 18f, 11f)
                lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
            }
            stroke(1.9f) { moveTo(10.2f, 20.5f); lineTo(13.8f, 20.5f) }
        }
    }

    /** A page with two lines: notes. */
    val Note: ImageVector by lazy {
        icon("Note") {
            stroke(1.9f) { moveTo(6f, 4.5f); lineTo(18f, 4.5f); lineTo(18f, 19.5f); lineTo(6f, 19.5f); close() }
            stroke(1.9f) { moveTo(9f, 9.5f); lineTo(15f, 9.5f) }
            stroke(1.9f) { moveTo(9f, 13.5f); lineTo(13f, 13.5f) }
        }
    }

    /** House outline: the Home page. */
    val Home: ImageVector by lazy {
        icon("Home") {
            stroke(1.9f) { moveTo(4.5f, 11f); lineTo(12f, 4.5f); lineTo(19.5f, 11f) }
            stroke(1.9f) { moveTo(6.5f, 9.5f); lineTo(6.5f, 19.5f); lineTo(17.5f, 19.5f); lineTo(17.5f, 9.5f) }
        }
    }

    /** Circle with a tick: the Done view. */
    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            stroke(1.9f) { circle(12f, 12f, 8f) }
            stroke(2f) { moveTo(8.5f, 12.2f); lineTo(11f, 14.6f); lineTo(15.6f, 9.6f) }
        }
    }

    /** Calendar page with an arrow: the Upcoming view. */
    val Upcoming: ImageVector by lazy {
        icon("Upcoming") {
            stroke(1.9f) { moveTo(4.5f, 6f); lineTo(19.5f, 6f); lineTo(19.5f, 19.5f); lineTo(4.5f, 19.5f); close() }
            stroke(1.9f) { moveTo(8f, 3.5f); lineTo(8f, 7.5f) }
            stroke(1.9f) { moveTo(16f, 3.5f); lineTo(16f, 7.5f) }
            stroke(1.9f) { moveTo(9f, 13f); lineTo(15f, 13f) }
            stroke(1.9f) { moveTo(12.5f, 10.5f); lineTo(15f, 13f); lineTo(12.5f, 15.5f) }
        }
    }
}
