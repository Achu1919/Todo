package dev.saikrishna.todo.ui.home

import dev.saikrishna.todo.ui.components.pressScale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import dev.saikrishna.todo.ui.motion.Durations
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Unit-radius shapes centred on the origin: an open circle morphing into a soft square
 * ("squircle"). Side 1.7 with corner radius 0.5 keeps the square inside the unit circle, so the
 * filled shape never pokes past the ring it grows out of.
 */
internal object CheckShapes {
    val circle: RoundedPolygon by lazy { RoundedPolygon.circle(numVertices = 8) }
    val squircle: RoundedPolygon by lazy {
        RoundedPolygon.rectangle(width = 1.7f, height = 1.7f, rounding = CornerRounding(radius = 0.5f, smoothing = 0.6f))
    }
    val morph: Morph by lazy { Morph(circle, squircle) }
}

/**
 * Completion checkbox (spec 6.5): a 48 dp touch target drawing a 24 dp mark.
 * Checking: the accent shape grows from the centre on the Pop spring (≈10 % overshoot, the app's
 * only bounce) while morphing circle → squircle; the ring morphs with it; the check draws 40 ms
 * later over 240 ms. Unchecking reverses: check 120 ms, fill on Quick.
 * Everything is read in the draw phase; the Paths are reused across frames.
 */
@Composable
fun CheckCircle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * v0.12: list colour for the ring, a faint wash inside it while open, and the fill once
     * checked. Passed fresh each composition (never tweened), so a theme switch swaps it
     * instantly with the rest of the theme; only the fill/check progress animates.
     */
    tint: Color? = null,
) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val fill = remember { Animatable(if (checked) 1f else 0f) }
    val check = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        if (checked) {
            launch { fill.animateTo(1f, Springs.accent(reduced).float) }
            delay(Durations.CheckDelay)
            check.animateTo(1f, Tweens.select())
        } else {
            launch { check.animateTo(0f, tween(Durations.UncheckReverse)) }
            fill.animateTo(0f, Springs.Quick.float)
        }
    }

    val shapeAndroid = remember { android.graphics.Path() }
    val scaledAndroid = remember { android.graphics.Path() }
    val scaled = remember { scaledAndroid.asComposePath() }
    val matrix = remember { android.graphics.Matrix() }
    val checkPath = remember { Path() }
    val checkSeg = remember { Path() }
    val measure = remember { PathMeasure() }

    // v0.9.1: no ripple (Material's grey wave spilled ~2x past the ring). Press feedback stays
    // inside the ring: the mark scales to 0.9 on a spring while pressed (layer only).
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // A press held past the long-press timeout lifts the row for dragging (the row's own gesture);
    // its release must not also tick the box (v0.9.1 fix: it used to complete the task).
    val longPressMs = androidx.compose.ui.platform.LocalViewConfiguration.current.longPressTimeoutMillis
    // Pointer times (down → up) seen in the Initial pass, without consuming anything.
    val times = remember { longArrayOf(-1L, -1L) }
    val guarded: (Boolean) -> Unit = { v ->
        val (down, up) = times[0] to times[1]
        times[0] = -1L
        if (down < 0 || up - down < longPressMs) onCheckedChange(v)
    }
    Canvas(
        modifier
            .size(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    times[0] = down.uptimeMillis; times[1] = down.uptimeMillis
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        times[1] = e.changes.firstOrNull()?.uptimeMillis ?: times[1]
                        if (e.changes.none { it.pressed }) break
                    }
                }
            }
            .toggleable(
                value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = guarded,
                interactionSource = press, indication = null,
            )
            .pressScale(press, 0.9f)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "Completed" else "Not completed"
            }
            .padding(12.dp),
    ) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val f = fill.value
        val shapeProgress = f.coerceIn(0f, 1f)

        fun shapeAt(radius: Float) {
            CheckShapes.morph.toPath(shapeProgress, shapeAndroid)
            matrix.reset()
            matrix.setScale(radius, radius)
            matrix.postTranslate(cx, cy)
            shapeAndroid.transform(matrix, scaledAndroid)
        }

        // Ring (stroke), morphing with the fill so the closed state is one clean shape.
        val stroke = 1.5.dp.toPx()
        shapeAt(r - stroke / 2f)
        if (tint != null) {
            if (f < 0.999f) drawPath(scaled, tint.copy(alpha = tint.alpha * 0.10f))
            drawPath(scaled, tint, style = Stroke(1.75.dp.toPx()))
        } else drawPath(scaled, c.inkStroke, style = Stroke(stroke))

        // Fill: radius follows the raw spring value, so Pop's overshoot briefly exceeds r.
        if (f > 0.001f) {
            shapeAt(r * f)
            drawPath(scaled, tint ?: c.ink) // ink fill, like the selection capsule (v0.12: or the list colour)
        }

        val k = check.value
        if (k > 0f) {
            checkPath.reset()
            checkPath.moveTo(size.width * 0.28f, size.height * 0.52f)
            checkPath.lineTo(size.width * 0.44f, size.height * 0.67f)
            checkPath.lineTo(size.width * 0.73f, size.height * 0.36f)
            measure.setPath(checkPath, false)
            checkSeg.reset()
            measure.getSegment(0f, measure.length * k, checkSeg, true)
            drawPath(
                checkSeg, if (tint == null) c.onInk else checkOn(tint),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Check mark colour on a list-colour fill: white, or near-black on a light pastel. */
internal fun checkOn(fill: Color): Color =
    if (fill.luminance() > 0.6f) Color(0xE6101418) else Color.White
