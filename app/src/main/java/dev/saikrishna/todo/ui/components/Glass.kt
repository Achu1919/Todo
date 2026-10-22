package dev.saikrishna.todo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.saikrishna.todo.ui.motion.Depth
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.theme.AppColors
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlin.math.max

/** The screen's Haze source (one per screen, spec 8). null = no blur available here. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Optional switch to Haze's flat-tint path. Since v0.6.1 navigation does NOT set it: the
 * blur → tint swap at the start/end of a push/pop was itself a visible tone pop (measured as a
 * one-frame jump at the end of every pop). Scenes now move by translation only, so the live blur
 * keeps its exact geometry relative to its source and stays on throughout.
 */
val LocalBlurFrozen = compositionLocalOf { false }

/** Same signal as a State, for effects that wait for the scene to settle (e.g. Search autofocus). */
val LocalNavMoving = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/**
 * Glass elevation tokens. Shadows are drawn by us as a few stacked, expanding outline rings,
 * clipped OUTSIDE the surface (so translucent fills never show a dark core), offset down a touch.
 * No blur, no RenderEffect, cached per size: cheap enough for every list card.
 */
enum class GlassLevel(val spread: Dp, val dy: Dp, val strength: Float, val rings: Int) {
    /** Cards, pill track, round buttons. */
    Low(spread = 6.dp, dy = 2.dp, strength = 0.10f, rings = 6),
    /** Composer, undo bar. */
    Mid(spread = 14.dp, dy = 5.dp, strength = 0.13f, rings = 9),
    /** Floating task panel. */
    High(spread = 26.dp, dy = 10.dp, strength = 0.18f, rings = 12),
}

/**
 * Static misty-sky backdrop (our own gradient + two soft glows; no shader, no animation, so it
 * costs one cached draw). The bottom deepens to [AppColors.skyDeep] so the light composer glass
 * reads as a distinct floating object.
 */
fun Modifier.skyBackdrop(c: AppColors): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    // Same key colours as before, eased between keys (C¹): the old linear stops had a slope
    // reversal at the bright 80 % stop that read as a light band above the composer. The glow key
    // also moves up a little so the deepening toward the bottom is longer and gentler.
    val base = Brush.verticalGradient(
        *smoothStops(
            listOf(
                0f to c.skyTop,
                0.30f to c.skyMid,
                0.60f to c.skyLow,
                0.76f to c.skyGlow,
                1f to c.skyDeep,
            ),
        ),
    )
    val glowA = Brush.radialGradient(
        listOf(c.skyGlow.copy(alpha = if (c.isDark) 0.22f else 0.55f), Color.Transparent),
        center = Offset(w * 0.82f, h * 0.38f), radius = max(w, 1f) * 0.75f,
    )
    val glowB = Brush.radialGradient(
        listOf(c.skyGlow.copy(alpha = if (c.isDark) 0.16f else 0.40f), Color.Transparent),
        center = Offset(w * 0.12f, h * 0.62f), radius = max(w, 1f) * 0.70f,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(glowA)
        drawRect(glowB)
    }
}

/** Smoothstep 0→1 on [0,1]: C¹ (zero slope at both ends), so a fade has no visible start/end line. */
fun smoothstep(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

/**
 * Colour stops that go through [keys] (position → colour) with a smoothstep ease between each
 * pair, sampled [perSegment] times. A plain linear gradient has a slope jump at every stop, which
 * the eye reads as a bright/dark line (Mach band); this one is C¹.
 */
fun smoothStops(keys: List<Pair<Float, Color>>, perSegment: Int = 10): Array<Pair<Float, Color>> {
    val out = ArrayList<Pair<Float, Color>>()
    for (k in 0 until keys.size - 1) {
        val (p0, c0) = keys[k]; val (p1, c1) = keys[k + 1]
        for (i in 0 until perSegment) {
            val t = i / perSegment.toFloat()
            out += (p0 + (p1 - p0) * t) to androidx.compose.ui.graphics.lerp(c0, c1, smoothstep(t))
        }
    }
    out += keys.last()
    return out.toTypedArray()
}

/**
 * Alpha mask for the header edge as FRACTIONS of the layer height: 1 down to [fadeFrom], an eased
 * (smoothstep) fall to exactly 0 at [fadeTo], then 0 to the bottom. Fractions matter: Haze
 * evaluates the mask in its (half-resolution) blur buffer, so absolute pixel positions would land
 * in the wrong place (v0.6.1 bug: the mask never faded and the layer ended in a hard line).
 */
private fun edgeMask(fadeFrom: Float, fadeTo: Float, color: Color = Color.Black, steps: Int = 12): Brush {
    val stops = ArrayList<Pair<Float, Color>>()
    stops += 0f to color
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        stops += (fadeFrom + (fadeTo - fadeFrom) * t) to color.copy(alpha = color.alpha * (1f - smoothstep(t)))
    }
    stops += 1f to color.copy(alpha = 0f)
    return Brush.verticalGradient(*stops.toTypedArray())
}

/**
 * Top edge of a screen: content under the floating header is blurred (and tinted) at full
 * strength down to [solidHeight] − [fadeAbove], then BOTH blur and tint fade out with an eased
 * mask that reaches exactly 0 at [solidHeight] + [fadeBelow]. The layer itself is [tail] taller
 * than that, so its bottom edge (and the blur's edge sampling) is always fully masked out.
 * One blur pass, half-resolution input, never animated. Reduced motion / no source: the same
 * eased mask over the flat fallback tint.
 *
 * This draws its own layer and takes no layout height: put it first inside the header Box.
 */
@Composable
fun GlassEdgeTop(solidHeight: Dp, fadeBelow: Dp = 30.dp, fadeAbove: Dp = 20.dp, tail: Dp = 12.dp) {
    val c = AppTheme.colors
    val state = LocalHazeState.current
    val reduced = LocalReducedMotion.current
    val frozen = LocalBlurFrozen.current
    val total = solidHeight + fadeBelow + tail
    val from = ((solidHeight - fadeAbove) / total).coerceIn(0f, 1f)
    val to = ((solidHeight + fadeBelow) / total).coerceIn(0f, 1f)
    val mask = remember(from, to) { edgeMask(from, to) }
    val base = androidx.compose.ui.Modifier
        .fillMaxWidth()
        .layout { measurable, constraints ->
            val h = total.roundToPx()
            val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
            layout(p.width, 0) { p.place(0, 0) } // overflows downwards, adds no height
        }
    if (state == null || reduced) {
        // No blur: the flat fallback tint under the same eased mask (no blending, no layer).
        val tint = c.glassChromeFallback.copy(alpha = 0.85f)
        val brush = remember(from, to, tint) { edgeMask(from, to, tint) }
        Box(base.drawWithCache { onDrawBehind { drawRect(brush) } })
        return
    }
    val style = remember(c, mask, frozen) {
        HazeBlurStyle {
            blurEnabled(!frozen)
            blurRadius(Depth.ChromeBlur)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(c.glassChrome)))
            fallbackColorEffect(HazeColorEffect.tint(c.glassChromeFallback))
            mask(mask)
        }
    }
    val input = remember(state) { HazeInput.Sources(state) }
    Box(base.hazeBlur(input, style = style, performanceMode = HazePerformanceMode.Performance))
}

/**
 * Frosted live blur under a glass tint (the composer). Clip to the shape before this.
 * v0.7.2: [Depth.FrostBlur] (26 dp), a small saturation lift (1.15, one colour matrix, applied
 * before the tint) so colours behind stay hinted, then the tint (light: white 72 %, dark: slate
 * 70 %). Frozen/reduced/no source: flat fallback.
 */
@Composable
fun Modifier.glassBlurFill(tint: Color = AppTheme.colors.glassTint, fallback: Color = AppTheme.colors.paperRaised): Modifier {
    val state = LocalHazeState.current
    val reduced = LocalReducedMotion.current
    val frozen = LocalBlurFrozen.current
    if (state == null || reduced) return this.drawWithCache { onDrawBehind { drawRect(fallback) } }
    val style = remember(tint, fallback, frozen) {
        HazeBlurStyle {
            blurEnabled(!frozen)
            blurRadius(Depth.FrostBlur)
            noiseFactor(0f)
            colorEffects(
                listOf(
                    HazeColorEffect.colorFilter(
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(FrostSaturation) },
                        ),
                    ),
                    HazeColorEffect.tint(tint),
                ),
            )
            fallbackColorEffect(HazeColorEffect.tint(fallback.copy(alpha = 0.94f)))
        }
    }
    val input = remember(state) { HazeInput.Sources(state) }
    return this.hazeBlur(input, style = style, performanceMode = HazePerformanceMode.Performance)
}

const val FrostSaturation = 1.15f

/**
 * v0.11 search frost: the heavy full-screen blur behind the search morph. Constant radius
 * ([Depth.SearchBlur]); the caller fades it with graphicsLayer alpha (never animates the radius).
 * Frozen / reduced motion / no source: a flat paper scrim instead.
 */
@Composable
fun Modifier.searchFrost(state: HazeState?): Modifier {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val frozen = LocalBlurFrozen.current
    val scrim = c.paper.copy(alpha = 0.90f)
    if (state == null || reduced) return this.drawWithCache { onDrawBehind { drawRect(scrim) } }
    val tint = c.paper.copy(alpha = if (c.isDark) 0.60f else 0.52f)
    val style = remember(tint, scrim, frozen) {
        HazeBlurStyle {
            blurEnabled(!frozen)
            blurRadius(Depth.SearchBlur)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(scrim))
        }
    }
    val input = remember(state) { HazeInput.Sources(state) }
    return this.hazeBlur(input, style = style, performanceMode = HazePerformanceMode.Performance)
}

/**
 * v0.7.2: the list sinks away above the composer. Content keeps full opacity down to
 * [Depth.BottomFade] above the edge, then an eased (smoothstep, C¹) fall to [floor] exactly at
 * the edge, and [floor] below it. [edgeFromBottom] is read in the DRAW phase (the composer's
 * measured height), so a growing composer or the undo bar only redraws. Alpha mask (DstIn) on an
 * offscreen layer, not a scrim: whatever is behind the list (the sky) shows through.
 */
fun Modifier.bottomFade(
    edgeFromBottom: () -> Float,
    fade: Dp = Depth.BottomFade,
    floor: Float = Depth.BottomFadeFloor,
    /** v0.7.3: composer collapse progress; the fade eases to [collapsedFade]/[collapsedFloor] beside the pill. */
    collapse: () -> Float = { 0f },
    collapsedFade: Dp = 28.dp,
    collapsedFloor: Float = 0.45f,
): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithCache {
        val fadeA = fade.toPx()
        val fadeB = collapsedFade.toPx()
        fun stops(f: Float) = smoothStops(listOf(0f to Color.Black, 1f to Color.Black.copy(alpha = f)), perSegment = 16)
        val expandedKeys = stops(floor)
        onDrawWithContent {
            drawContent()
            val k = collapse().coerceIn(0f, 1f)
            val keys = if (k == 0f) expandedKeys else stops(floor + (collapsedFloor - floor) * k)
            val fadePx = fadeA + (fadeB - fadeA) * k
            val end = size.height - edgeFromBottom()
            val start = end - fadePx
            drawRect(
                Brush.verticalGradient(*keys, startY = start, endY = end),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
    }

/** Kept for call sites from v0.4: the composer glass. */
@Composable
fun Modifier.glassChrome(): Modifier = glassBlurFill()

/**
 * Soft diffuse shadow outside [shape] (see [GlassLevel]). Apply BEFORE clip/fill modifiers.
 */
fun Modifier.glassShadow(shape: Shape, level: GlassLevel, color: Color): Modifier = drawWithCache {
    val spread = level.spread.toPx()
    val dy = level.dy.toPx()
    val inner = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val rings = (1..level.rings).map { i ->
        val e = spread * i / level.rings
        val p = Path().apply {
            addOutline(shape.createOutline(Size(size.width + 2 * e, size.height + 2 * e), layoutDirection, this@drawWithCache))
        }
        p to e
    }
    // Each ring adds a little: the sum falls off smoothly from the edge outwards.
    val a = level.strength / level.rings * 1.6f
    onDrawBehind {
        clipPath(inner, ClipOp.Difference) {
            for ((p, e) in rings) translate(-e, -e + dy) {
                drawPath(p, color.copy(alpha = color.alpha * a))
            }
        }
    }
}

/**
 * Drag lift shadow (v0.7): the Mid glass shadow scaled by [lift] (0..1), read in the draw phase,
 * so a lifted row gains depth without recomposing.
 */
fun Modifier.liftShadow(shape: Shape, color: Color, lift: () -> Float): Modifier = drawWithCache {
    val level = GlassLevel.Mid
    val spread = level.spread.toPx()
    val dy = level.dy.toPx()
    val inner = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val rings = (1..level.rings).map { i ->
        val e = spread * i / level.rings
        Path().apply {
            addOutline(shape.createOutline(Size(size.width + 2 * e, size.height + 2 * e), layoutDirection, this@drawWithCache))
        } to e
    }
    val a = level.strength / level.rings * 1.8f
    onDrawBehind {
        val l = lift()
        if (l <= 0.001f) return@onDrawBehind
        clipPath(inner, ClipOp.Difference) {
            for ((p, e) in rings) translate(-e, -e + dy * l) {
                drawPath(p, color.copy(alpha = color.alpha * a * l))
            }
        }
    }
}

/**
 * 1 dp glass rim: lighter at the top, slightly darker at the bottom. Drawn over the content,
 * inset half a stroke so it stays inside the surface.
 */
fun Modifier.glassRim(shape: Shape, top: Color, bottom: Color, width: Dp = 1.dp): Modifier = drawWithCache {
    val w = width.toPx()
    val path = Path().apply {
        addOutline(shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this@drawWithCache))
    }
    val brush = Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = size.height)
    onDrawWithContent {
        drawContent()
        translate(w / 2f, w / 2f) { drawPath(path, brush, style = Stroke(w)) }
    }
}

/** Flat translucent fill drawn in the draw phase (no clip node needed for a solid fill). */
fun Modifier.glassFill(shape: Shape, color: Color): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    onDrawBehind { drawPath(path, color) }
}

/**
 * Glass surface WITHOUT live blur (cards, pill track, round buttons): translucent fill +
 * gradient rim + soft shadow, all from the shared tokens.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    level: GlassLevel = GlassLevel.Low,
    fill: Color = AppTheme.colors.glassFill,
): Modifier {
    val c = AppTheme.colors
    return this
        .glassShadow(shape, level, c.glassShadow)
        .glassFill(shape, fill)
        .glassRim(shape, c.glassRimTop, c.glassRimBottom)
}

/** Floating control surface (pill track, circle buttons): the shared glass surface. */
@Composable
fun Modifier.glassControl(shape: Shape): Modifier {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    return glassSurface(shape, GlassLevel.Low, if (reduced) c.glassControlFallback else c.glassFill)
}

/**
 * PaddingValues whose top/bottom come from measured chrome heights. LazyColumn reads padding
 * during measure, so a changing chrome height (keyboard, undo bar) only re-runs layout and never
 * recomposes the list.
 */
@Stable
class ChromePadding(
    private val density: Density,
    private val topPx: MutableIntState,
    private val bottomPx: MutableIntState,
    private val horizontal: Dp = dev.saikrishna.todo.ui.theme.Spacing.screen,
    private val extraTop: Dp = 0.dp,
    private val extraBottom: Dp = 8.dp,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = with(density) { topPx.intValue.toDp() } + extraTop
    override fun calculateBottomPadding(): Dp = with(density) { bottomPx.intValue.toDp() } + extraBottom
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = horizontal
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = horizontal
}

@Composable
fun rememberChromePadding(topPx: MutableIntState, bottomPx: MutableIntState, extraBottom: Dp = 8.dp): ChromePadding {
    val density = LocalDensity.current
    return remember(density, topPx, bottomPx, extraBottom) { ChromePadding(density, topPx, bottomPx, extraBottom = extraBottom) }
}
