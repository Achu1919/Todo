package dev.saikrishna.todo.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.saikrishna.todo.ui.motion.Depth
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Radii
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Where each task card currently is, so the panel can grow out of it and shrink back into it.
 * Rows register their LayoutCoordinates on placement (plain map: no state, no recomposition);
 * [hidden] is the card currently "lifted" into the panel (read in its graphicsLayer only).
 */
@Stable
class PanelAnchors {
    internal val coords = HashMap<Long, LayoutCoordinates>()
    var hidden by mutableStateOf<Long?>(null)
        internal set
    /**
     * v0.7.1 morph: true on the frame a closing morph lands on its anchor (read in the anchor's
     * graphicsLayer), so the anchor is drawn again in the SAME frame as the landed capsule.
     */
    internal var revealed: () -> Boolean = { false }

    /** Forget a card (e.g. it is being deleted) so closing fades in place instead of flying to it. */
    fun forget(id: Long) { coords.remove(id) }
}

/** Card modifier: registers bounds for [id] and hides the card while it is lifted into the panel. */
fun Modifier.panelAnchor(anchors: PanelAnchors, id: Long): Modifier = this
    .onPlaced { anchors.coords[id] = it }
    .graphicsLayer {
        alpha = if (anchors.hidden == id && !anchors.revealed()) 0f else 1f
        PanelProbe.anchorAlpha[id] = alpha
    }

/**
 * How a panel host moves (v0.7.1).
 * - [Card]: the task panel (unchanged since v0.5): Smooth spring, content mirrors the card and is
 *   visible throughout, fill turns opaque in the first half.
 * - [Menu]: small floating menus that grow out of a glass pill/button: a stiffer [Springs.Morph]
 *   spring, the surface (corner, fill, rim, shadow) interpolates from the anchor's own glass to
 *   the panel's, content fades in after [contentInAt] of the geometry ([contentInMs]) and out at
 *   the start of the close ([contentOutMs]), and the close ends on a glass capsule exactly on the
 *   anchor, which reappears in that same frame.
 */
@androidx.compose.runtime.Immutable
class PanelStyle(
    val spring: dev.saikrishna.todo.ui.motion.SpringToken,
    val morph: Boolean,
    val contentInAt: Float = 0.4f,
    val contentInMs: Int = 120,
    val contentOutMs: Int = 90,
) {
    companion object {
        val Card = PanelStyle(Springs.Smooth, morph = false)
        val Menu = PanelStyle(Springs.Morph, morph = true)
    }
}

/** Frame probe for tests (no cost when unset). */
object PanelProbe {
    /** Called from the draw phase: surface rect in ROOT coordinates, corner px, content alpha, progress. */
    var onDraw: ((rect: Rect, corner: Float, content: Float, p: Float) -> Unit)? = null
    val anchorAlpha = HashMap<Long, Float>()
}

private class Landed : kotlinx.coroutines.CancellationException("landed")

@Stable
private class PanelMotion {
    var drag by mutableFloatStateOf(0f)
    var back by mutableFloatStateOf(0f)
    /** Card rect in host coordinates, or null = no card on screen (scale/fade in place). */
    var from by mutableStateOf<Rect?>(null)
    var host: LayoutCoordinates? = null
    var closing by mutableStateOf(false)
    /** Morph: progress at or below which the capsule is within 0.5 dp of the anchor. */
    var landAt = 0f
    var bare = false
    /** v0.9: the anchor's own shadow level (the lists button carries the composer's Mid shadow). */
    var anchorLevel: dev.saikrishna.todo.ui.components.GlassLevel = dev.saikrishna.todo.ui.components.GlassLevel.Low
    /** Last laid-out panel rect (host coordinates). */
    var target: Rect? = null
    var colors: dev.saikrishna.todo.ui.theme.AppColors? = null
    var ghost: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
}

/**
 * Contextual floating panel (v0.5, replaces the bottom sheet). Technique after the reference's
 * long-press menu: the whole screen behind is blurred and dimmed; the tapped card lifts and
 * expands in place into a rounded glass panel near it.
 *
 * - Backdrop: ONE full-screen Haze blur of the home layer at a FIXED 16 dp radius (never animated);
 *   it fades in through its layer alpha. A separate flat dim layer on top of it. Both are plain
 *   nodes that do not depend on reduced motion or on any background transform (the v0.4 sheet's
 *   6 % scale + 12 % dim sat behind a 92 %-tall sheet and was all but invisible on a phone).
 * - Container transform: one progress p on the Smooth spring. The surface rect, corner radius,
 *   fill and shadow are lerped from the card to the panel in the DRAW phase (one reusable Path);
 *   content is laid out once at panel size and only translated, so its header row (a mirror of
 *   the card's) sits exactly on the card at p = 0.
 * - Placement: under/over the card, clamped below the status bar and above the IME (read in the
 *   layout phase, so it rides the keyboard without recomposition).
 * - Dismiss: tap outside, back (predictive back shrinks it toward the card), swipe down (nested
 *   scroll at the top of the content). Close animates back into the card's CURRENT bounds, or
 *   fades in place if the card is gone.
 * - Reduced motion: crossfade at the final position, no geometry animation.
 */
@Composable
fun TaskPanelHost(
    openId: Long?,
    anchors: PanelAnchors,
    backdrop: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    style: PanelStyle = PanelStyle.Card,
    /** Morph: anchors that are bare icons (no glass of their own): the capsule fades its glass out as it lands. */
    bareAnchors: Set<Long> = emptySet(),
    /** v0.9: anchors whose glass shadow is not Low (the morph starts from exactly that shadow). */
    anchorLevels: Map<Long, dev.saikrishna.todo.ui.components.GlassLevel> = emptyMap(),
    /**
     * Morph: the anchor's own content (e.g. the pill's dot + label + chevron, no surface), drawn
     * inside the capsule as it nears the anchor, so the capsule lands as the pill itself.
     */
    ghost: (@Composable (id: Long) -> Unit)? = null,
    /**
     * v0.9: anchors near the bottom (the lists button) whose panel floats ABOVE them, anchored
     * bottom-left: its bottom 10 dp over the anchor's top, its left on the anchor's left (clamped
     * on screen), at most [AbovePanelMaxWidth] wide and [AbovePanelMaxFraction] of the screen tall.
     */
    above: Set<Long> = emptySet(),
    /** v0.9.1: small dropdowns BELOW their anchor, right-aligned to it, [BelowPanelWidth] wide. */
    below: Set<Long> = emptySet(),
    content: @Composable (id: Long) -> Unit,
) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val progress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }
    val m = remember { PanelMotion() }
    val halfDp = with(density) { 0.5.dp.toPx() }
    anchors.revealed = { m.closing && progress.value <= m.landAt }
    var shown by remember { mutableStateOf<Long?>(null) }
    val flingPx = with(density) { 1000.dp.toPx() }
    val dismissPx = with(density) { 110.dp.toPx() }

    fun rectOf(id: Long?): Rect? {
        val host = m.host ?: return null
        val card = id?.let { anchors.coords[it] } ?: return null
        if (!host.isAttached || !card.isAttached) return null
        val r = host.localBoundingBoxOf(card, clipBounds = false)
        // Must actually be on screen to fly to/from it.
        return if (r.width > 0f && r.overlaps(Rect(Offset.Zero, Size(host.size.width.toFloat(), host.size.height.toFloat())))) r else null
    }

    LaunchedEffect(openId) {
        val spec = if (reduced) Tweens.reduced() else style.spring.float
        if (openId != null) {
            m.closing = false
            if (shown != openId) {
                progress.snapTo(0f)
                m.from = null // invisible (alpha = progress = 0) for the wait below
                shown = openId
                // Let the card settle for one frame (e.g. a swipe snapping back) before reading it.
                withFrameNanos { }
                m.from = rectOf(openId)
            }
            shown = openId
            m.bare = openId in bareAnchors
            m.anchorLevel = anchorLevels[openId] ?: dev.saikrishna.todo.ui.components.GlassLevel.Low
            anchors.hidden = openId
            m.drag = 0f
            m.back = 0f
            if (style.morph && !reduced && m.from != null) {
                if (progress.value <= 0f) contentAlpha.snapTo(0f)
                kotlinx.coroutines.coroutineScope {
                    launch { progress.animateTo(1f, spec) }
                    launch {
                        snapshotFlow { progress.value >= style.contentInAt }.first { it }
                        contentAlpha.animateTo(1f, androidx.compose.animation.core.tween(style.contentInMs, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                    }
                }
            } else {
                contentAlpha.snapTo(1f)
                progress.animateTo(1f, spec)
            }
        } else if (shown != null) {
            m.from = rectOf(shown)
            val from = m.from
            if (style.morph && !reduced && from != null) {
                val t = m.target ?: from
                val delta = maxOf(
                    kotlin.math.abs(t.left - from.left), kotlin.math.abs(t.top - from.top),
                    kotlin.math.abs(t.right - from.right), kotlin.math.abs(t.bottom - from.bottom),
                )
                m.landAt = if (delta <= halfDp) 1f else halfDp / delta
                m.closing = true
                kotlinx.coroutines.coroutineScope {
                    launch { contentAlpha.animateTo(0f, androidx.compose.animation.core.tween(style.contentOutMs, easing = androidx.compose.animation.core.FastOutLinearInEasing)) }
                    launch { if (m.drag != 0f) animate(m.drag, 0f, 0f, spec) { v, _ -> m.drag = v } }
                    // Ends on the landing frame: the capsule sits on the anchor (within 0.5 dp), the
                    // anchor is drawn again in that same frame, and the host goes away on the next.
                    try {
                        progress.animateTo(0f, spec) { if (value <= m.landAt && m.drag == 0f) throw Landed() }
                    } catch (_: Landed) { }
                }
            } else {
                m.closing = true
                progress.animateTo(0f, spec)
            }
            shown = null
            anchors.hidden = null
            // m.closing stays true until the next open: the host's last frame must keep treating
            // itself as landed (paint nothing over the now visible anchor).
            m.drag = 0f
            m.back = 0f
        }
    }

    // Back / predictive back. Composed unconditionally (handler order must not change); enabled only while open.
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = openId != null,
        onBackCancelled = { scope.launch { animate(m.back, 0f, 0f, Springs.Gesture.float) { v, _ -> m.back = v } } },
        onBackCompleted = { dismiss() },
    )
    LaunchedEffect(backState) {
        snapshotFlow { backState.transitionState }.collect { t ->
            if (t is NavigationEventTransitionState.InProgress) m.back = t.latestEvent.progress
        }
    }

    fun settle(velocity: Float) {
        if (m.drag > dismissPx || velocity > flingPx) dismiss()
        else scope.launch { animate(m.drag, 0f, velocity, Springs.Gesture.float) { v, _ -> m.drag = v } }
    }
    val nested = remember(m) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && m.drag > 0f) {
                    val used = maxOf(available.y, -m.drag)
                    m.drag += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    m.drag += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (m.drag > 0f) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }

    /** Visible amount 0..1 for the backdrop: open progress, eased off by drag and back. */
    fun visible(): Float {
        val dragFade = (m.drag / (dismissPx * 3f)).coerceIn(0f, 0.5f)
        return (progress.value * (1f - 0.35f * m.back) * (1f - dragFade)).coerceIn(0f, 1f)
    }

    Box(modifier.fillMaxSize().onPlaced { m.host = it }) {
        val id = shown ?: openId
        if (id != null) {
            val blurStyle = remember(c) {
                HazeBlurStyle {
                    blurEnabled(true)
                    blurRadius(Depth.OverlayBlur)
                    noiseFactor(0f)
                    colorEffects(emptyList())
                    fallbackColorEffect(HazeColorEffect.tint(c.paper.copy(alpha = 0.85f)))
                }
            }
            val input = remember(backdrop) { HazeInput.Sources(backdrop) }
            val dim = if (c.isDark) Depth.OverlayDimDark else Depth.OverlayDim
            // 1) Blur of everything behind (fixed radius; only its layer alpha animates).
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = visible() }
                    .hazeBlur(input, blurStyle, performanceMode = HazePerformanceMode.Performance),
            )
            // 2) Flat dim + tap-outside target.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = dim * visible())) }
                    .pointerInput(Unit) { detectTapGestures { dismiss() } }
                    .semantics { contentDescription = "Close"; onClick { dismiss(); true } },
            )
            // 3) The panel.
            PanelLayout(
                p = { progress.value },
                contentAlpha = { contentAlpha.value },
                morph = style.morph,
                motion = m,
                reduced = reduced,
                nested = nested,
                ghost = if (style.morph && ghost != null) ({ ghost(id) }) else null,
                aboveAnchor = id in above,
                belowAnchor = id in below,
                content = { content(id) },
            )
        }
    }
}

@Composable
private fun PanelLayout(
    p: () -> Float,
    contentAlpha: () -> Float,
    morph: Boolean,
    motion: PanelMotion,
    reduced: Boolean,
    nested: NestedScrollConnection,
    ghost: (@Composable () -> Unit)?,
    aboveAnchor: Boolean = false,
    belowAnchor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val ghostLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    motion.ghost = if (ghost != null) ghostLayer else null
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val status = WindowInsets.statusBars
    val nav = WindowInsets.navigationBars
    // Target rect, written in layout and read in draw (same frame).
    val target = remember { RectHolder() }
    val path = remember { Path() }
    motion.colors = c

    Layout(
        content = {
            Box(
                Modifier
                    .then(if (morph) Modifier.graphicsLayer { alpha = contentAlpha() } else Modifier)
                    .nestedScroll(nested)
                    // Swallow taps on the panel so they never reach the tap-outside layer.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) { content() }
            if (ghost != null) {
                // Recorded (never drawn here); drawMorph draws the recording inside the capsule.
                Box(
                    Modifier
                        .clearAndSetSemantics { }
                        .drawWithContent { ghostLayer.record { this@drawWithContent.drawContent() } },
                ) { ghost() }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val cardR = with(density) { Radii.row.toPx() }
                val panelR = with(density) { PanelRadius.toPx() }
                val rimW = with(density) { 1.dp.toPx() }
                val shadowSpread = with(density) { 26.dp.toPx() }
                val shadowDy = with(density) { 10.dp.toPx() }
                val rings = 10
                onDrawWithContent {
                    val scene = this
                    val t = target.rect ?: return@onDrawWithContent
                    val from = motion.from
                    val raw = p()
                    val back = motion.back
                    if (morph && !reduced && from != null) {
                        drawMorph(scene, t, from, raw, back, motion, contentAlpha(), path, panelR, rimW)
                        return@onDrawWithContent
                    }
                    // Geometry progress: predictive back pulls the panel 40 % of the way to the card.
                    val g = if (reduced || from == null) 1f else (raw * (1f - 0.4f * back))
                    val base = if (from == null || reduced) t else lerp(from, t, g)
                    // No-card / reduced: scale in place from 0.94 (reduced: none) and fade.
                    val s = if (reduced) 1f else if (from == null) 0.94f + 0.06f * raw else 1f - 0.04f * back
                    val w = base.width * s
                    val h = base.height * s
                    val r = Rect(Offset(base.center.x - w / 2f, base.center.y - h / 2f + motion.drag), Size(w, h))
                    val alpha = if (reduced || from == null) raw.coerceIn(0f, 1f) else 1f
                    val corner = if (from == null) panelR else cardR + (panelR - cardR) * g.coerceIn(0f, 1f)
                    val lift = g.coerceIn(0f, 1f)
                    if (alpha > 0f) PanelProbe.onDraw?.invoke(r.translate(motion.host?.localToRoot(Offset.Zero) ?: Offset.Zero), corner, alpha, raw)

                    path.reset()
                    path.addRoundRect(RoundRect(r, CornerRadius(corner)))
                    // Soft shadow outside the surface, strength grows as it lifts.
                    clipPath(path, ClipOp.Difference) {
                        val a = c.glassShadow.alpha * 0.30f / rings * (0.35f + 0.65f * lift) * alpha
                        for (i in 1..rings) {
                            val e = shadowSpread * i / rings * (0.4f + 0.6f * lift)
                            drawRoundRect(
                                c.glassShadow.copy(alpha = a),
                                topLeft = Offset(r.left - e, r.top - e + shadowDy * lift),
                                size = Size(r.width + 2 * e, r.height + 2 * e),
                                cornerRadius = CornerRadius(corner + e),
                            )
                        }
                    }
                    clipPath(path) {
                        // Card fill → panel fill.
                        // Fill turns panel-opaque in the first half, so cards it now covers never ghost through.
                        val fillT = (lift * 2f).coerceAtMost(1f)
                        drawRect(lerp(c.glassCard, c.glassSolid.copy(alpha = PanelFillAlpha), fillT).let { it.copy(alpha = it.alpha * alpha) })
                        // Content laid out at panel size, translated so it starts on the card.
                        translate(r.left - t.left, r.top - t.top) {
                            if (alpha >= 1f) scene.drawContent()
                            else {
                                drawContext.canvas.saveLayer(
                                    androidx.compose.ui.geometry.Rect(t.left, t.top, t.right, t.bottom),
                                    androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha },
                                )
                                scene.drawContent()
                                drawContext.canvas.restore()
                            }
                        }
                    }
                    // 1 dp rim, lighter top / darker bottom.
                    val inset = rimW / 2f
                    drawRoundRect(
                        Brush.verticalGradient(listOf(c.glassRimTop, c.glassRimBottom), startY = r.top, endY = r.bottom),
                        topLeft = Offset(r.left + inset, r.top + inset),
                        size = Size(r.width - rimW, r.height - rimW),
                        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
                        style = Stroke(rimW),
                        alpha = alpha,
                    )
                }
            },
    ) { measurables, constraints ->
        val W = constraints.maxWidth
        val H = constraints.maxHeight
        val side = PanelSide.roundToPx()
        val gap = 12.dp.roundToPx()
        val topLimit = status.getTop(this) + 12.dp.roundToPx()
        val bottomLimit = H - maxOf(ime.getBottom(this), nav.getBottom(this)) - gap
        val from = motion.from
        val ghostPlaceable = measurables.getOrNull(1)?.measure(Constraints(maxWidth = W, maxHeight = H))
        if (belowAnchor) {
            // v0.9.1: a small dropdown under the anchor, its right edge on the anchor's right.
            val panelW = minOf(W - 2 * side, BelowPanelWidth.roundToPx()).coerceAtLeast(0)
            val top = from?.let { (it.bottom + AboveGap.toPx()).roundToInt() } ?: topLimit
            val maxH = (bottomLimit - top).coerceAtLeast(0)
            val placeable = measurables.first().measure(Constraints(minWidth = panelW, maxWidth = panelW, maxHeight = maxH))
            val ph = placeable.height
            val right = from?.right?.roundToInt() ?: (W - side)
            val left = (right - panelW).coerceIn(side, (W - side - panelW).coerceAtLeast(side))
            target.rect = Rect(left.toFloat(), top.toFloat(), (left + panelW).toFloat(), (top + ph).toFloat())
            motion.target = target.rect
            return@Layout layout(W, H) {
                placeable.place(left, top)
                ghostPlaceable?.place(0, 0)
            }
        }
        if (aboveAnchor) {
            // v0.9: above the anchor, bottom-left anchored.
            val panelW = minOf(W - 2 * side, AbovePanelMaxWidth.roundToPx()).coerceAtLeast(0)
            val bottom = from?.let { (it.top - AboveGap.toPx()).roundToInt() } ?: (bottomLimit - 64.dp.roundToPx())
            val maxH = minOf(bottom - topLimit, (H * AbovePanelMaxFraction).roundToInt()).coerceAtLeast(0)
            val placeable = measurables.first().measure(Constraints(minWidth = panelW, maxWidth = panelW, maxHeight = maxH))
            val ph = placeable.height
            val left = (from?.left?.roundToInt() ?: side).coerceIn(side, (W - side - panelW).coerceAtLeast(side))
            val top = (bottom - ph).coerceAtLeast(topLimit)
            target.rect = Rect(left.toFloat(), top.toFloat(), (left + panelW).toFloat(), (top + ph).toFloat())
            motion.target = target.rect
            return@Layout layout(W, H) {
                placeable.place(left, top)
                ghostPlaceable?.place(0, 0)
            }
        }
        val panelW = (W - 2 * side).coerceAtLeast(0)
        val maxH = (bottomLimit - topLimit).coerceAtLeast(0)
        val placeable = measurables.first().measure(Constraints(minWidth = panelW, maxWidth = panelW, maxHeight = maxH))
        val ph = placeable.height
        // Near the card: start at the card's top (it grows downward), but keep fully on screen and above the IME.
        val preferred = from?.top?.roundToInt() ?: ((topLimit + bottomLimit - ph) / 2)
        val top = preferred.coerceAtMost(bottomLimit - ph).coerceAtLeast(topLimit)
        target.rect = Rect(side.toFloat(), top.toFloat(), (side + panelW).toFloat(), (top + ph).toFloat())
        motion.target = target.rect
        layout(W, H) {
            // Placed at the target (hit testing, IME focus scrolling); the draw phase moves the pixels.
            placeable.place(side, top)
            ghostPlaceable?.place(0, 0)
        }
    }
}

@Stable
private class RectHolder { var rect by mutableStateOf<Rect?>(null) }

/** Panel geometry tokens (proportions from the reference's long-press menu). */
val PanelRadius = 24.dp
val PanelSide = 16.dp
/** v0.9 panels above a bottom anchor (lists button). */
val AbovePanelMaxWidth = 360.dp
const val AbovePanelMaxFraction = 0.6f
val AboveGap = 10.dp
/** v0.9.1 dropdowns below an anchor (theme menu). */
val BelowPanelWidth = 200.dp
const val PanelFillAlpha = 0.94f

/**
 * v0.7.1 shared-bounds morph between a glass pill (or bare icon button) and the panel, all in the
 * draw phase. g = geometry progress (0 = exactly the anchor, 1 = the panel):
 * - rect and corner: anchor bounds, fully rounded → panel rect, [PanelRadius];
 * - surface: the anchor's glass (glassControl: glass fill, Low shadow, rim) → the panel's
 *   (opaque glass fill, lifted shadow, rim); the two shadows crossfade so g = 0 is exactly Low;
 * - the anchor's own content (ghost: dot, label, chevron) fades in over the last 30 % of the
 *   geometry at its pill position, so the capsule lands as the pill itself;
 * - panel content: its own time-based alpha (layer on the content child).
 * On the landing frame (within 0.5 dp) only the real pill is painted (no double surface).
 */
private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawMorph(
    scene: androidx.compose.ui.graphics.drawscope.ContentDrawScope,
    t: Rect,
    from: Rect,
    raw: Float,
    back: Float,
    motion: PanelMotion,
    content: Float,
    path: Path,
    panelR: Float,
    rimW: Float,
) {
    val c = motion.colors ?: return
    val g = (raw * (1f - 0.4f * back)).coerceIn(-0.2f, 1.2f)
    val base = lerp(from, t, g)
    val r = base.translate(0f, motion.drag * g.coerceIn(0f, 1f))
    val gc = g.coerceIn(0f, 1f)
    val fromR = from.minDimension / 2f
    val corner = (fromR + (panelR - fromR) * gc).coerceAtMost(r.minDimension / 2f)
    PanelProbe.onDraw?.invoke(r.translate(motion.host?.localToRoot(Offset.Zero) ?: Offset.Zero), corner, content, raw)
    // Landing frame: the anchor is drawn again in this frame, exactly here; paint nothing on top.
    if (motion.closing && raw <= motion.landAt) return

    val bare = motion.bare
    val surface = if (bare) (gc * 3f).coerceAtMost(1f) else 1f // bare icon: glass fades in over the first third
    path.reset()
    path.addRoundRect(RoundRect(r, CornerRadius(corner)))
    clipPath(path, ClipOp.Difference) {
        // Pill shadow (identical to the anchor's glassShadow, Low by default) fading out…
        val low = motion.anchorLevel
        val pillA = if (bare) 0f else c.glassShadow.alpha * low.strength / low.rings * 1.6f * (1f - gc)
        if (pillA > 0f) for (i in 1..low.rings) {
            val e = low.spread.toPx() * i / low.rings
            drawRoundRect(
                c.glassShadow.copy(alpha = pillA),
                topLeft = Offset(r.left - e, r.top - e + low.dy.toPx()),
                size = Size(r.width + 2 * e, r.height + 2 * e),
                cornerRadius = CornerRadius(corner + e),
            )
        }
        // …while the panel's lifted shadow grows in.
        val rings = 10
        val panelA = c.glassShadow.alpha * 0.30f / rings * gc * surface
        if (panelA > 0f) for (i in 1..rings) {
            val e = 26.dp.toPx() * i / rings * (0.4f + 0.6f * gc)
            drawRoundRect(
                c.glassShadow.copy(alpha = panelA),
                topLeft = Offset(r.left - e, r.top - e + 10.dp.toPx() * gc),
                size = Size(r.width + 2 * e, r.height + 2 * e),
                cornerRadius = CornerRadius(corner + e),
            )
        }
    }
    clipPath(path) {
        val fillT = (gc * 1.6f).coerceAtMost(1f)
        val fill = lerp(c.glassFill, c.glassSolid.copy(alpha = PanelFillAlpha), fillT)
        drawRect(fill.copy(alpha = fill.alpha * surface))
        // Panel content (alpha on its own layer); always drawn so the ghost below stays recorded.
        translate(r.left - t.left, r.top - t.top) { scene.drawContent() }
        // The anchor's own content, at its place in the pill (left edge, vertically centred).
        val ghost = motion.ghost
        if (ghost != null) {
            val a = (1f - gc / 0.3f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            if (a > 0f) {
                ghost.alpha = a
                translate(r.left, r.top + (r.height - ghost.size.height) / 2f) { drawLayer(ghost) }
            }
        }
    }
    val inset = rimW / 2f
    drawRoundRect(
        Brush.verticalGradient(listOf(c.glassRimTop, c.glassRimBottom), startY = r.top, endY = r.bottom),
        topLeft = Offset(r.left + inset, r.top + inset),
        size = Size(r.width - rimW, r.height - rimW),
        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(rimW),
        alpha = surface,
    )
}
