package dev.saikrishna.todo.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassChrome
import dev.saikrishna.todo.ui.components.glassRim
import dev.saikrishna.todo.ui.components.glassShadow
import dev.saikrishna.todo.ui.components.pressScale
import dev.saikrishna.todo.ui.components.searchFrost
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.SpringToken
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** v0.13 icon per page (the switcher button shows the current Todo view's). */
fun Filter.icon(): ImageVector = when (this) {
    Filter.Overview -> AppIcons.Home
    Filter.Today -> AppIcons.Sun
    Filter.Upcoming -> AppIcons.Upcoming
    Filter.Recurring -> AppIcons.Repeat
    Filter.Done -> AppIcons.CheckCircle
    Filter.Calendar -> AppIcons.Calendar
}

/**
 * v0.13 bottom-left view switcher state. Pure (no Compose UI), so the open / pick / fold rules
 * are unit-tested: a pick only counts while open, picking the current view just folds, and
 * every fold path (outside tap, swipe down, back, a pick) goes through [close].
 */
@Stable
class SwitcherState {
    var open by mutableStateOf(false)
        private set
    /** The pill that was tapped last (it pops while the others fold). Cleared on the next open. */
    var picked by mutableStateOf<Filter?>(null)
        private set

    fun toggle() { if (open) close() else { picked = null; open = true } }
    fun close() { open = false }

    /** Returns the view to switch to (null = nothing to do). */
    fun pick(view: Filter, current: Filter): Filter? {
        if (!open || view !in TodoViews) return null
        picked = view
        open = false
        return view.takeIf { it != current }
    }

    companion object {
        /** Stagger between pills (ms): opening goes bottom-up, folding top-down and faster. */
        const val OpenStagger = 34L
        const val CloseStagger = 22L
        /** Swipe-down distance that folds the pills. */
        val FoldSwipe = 48.dp
        /** Opening: a lively iOS spring (response 0.42 s, damping 0.72); folding: no bounce. */
        val OpenSpring = SpringToken(0.42f, 0.72f)
        val CloseSpring = SpringToken(0.30f, 1.00f)
        val PickPop = SpringToken(0.28f, 0.55f)

        fun openDelay(index: Int) = index * OpenStagger
        fun closeDelay(index: Int, count: Int) = (count - 1 - index) * CloseStagger
    }
}

/** The switcher button: the composer's 48 dp glass circle with the current view's icon (a cross while open). */
@Composable
fun ViewSwitcherButton(current: Filter, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val press = remember { MutableInteractionSource() }
    val reduced = LocalReducedMotion.current
    val turn = remember { Animatable(0f) }
    LaunchedEffect(open) {
        val t = if (open) 1f else 0f
        if (reduced) turn.snapTo(t) else turn.animateTo(t, SwitcherState.OpenSpring.float)
    }
    Box(
        modifier
            .size(ListsButtonSize)
            .pressScale(press, 0.94f)
            .testTag("view-switcher")
            .glassShadow(CircleShape, GlassLevel.Mid, c.glassShadow)
            .clip(CircleShape)
            .glassChrome()
            .glassRim(CircleShape, c.glassRimTop, c.glassRimBottom)
            .clickable(press, androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClickLabel = if (open) "Close views" else "Switch view", onClick = onClick)
            .semantics { contentDescription = "View: ${current.title}" },
        contentAlignment = Alignment.Center,
    ) {
        // Icon swap: the view icon turns and shrinks away as the cross turns in.
        Icon(
            current.icon(), null, tint = c.ink,
            modifier = Modifier.size(22.dp).graphicsLayer {
                val k = turn.value; alpha = (1f - k * 1.6f).coerceIn(0f, 1f); rotationZ = 90f * k; val s = 1f - 0.4f * k.coerceIn(0f, 1f); scaleX = s; scaleY = s
            },
        )
        Icon(
            AppIcons.Close, null, tint = c.ink,
            modifier = Modifier.size(20.dp).graphicsLayer {
                val k = turn.value; alpha = ((k - 0.3f) / 0.7f).coerceIn(0f, 1f); rotationZ = -90f * (1f - k); val s = 0.6f + 0.4f * k.coerceIn(0f, 1f); scaleX = s; scaleY = s
            },
        )
    }
}

/**
 * The four glass pills that spring up out of the switcher button, one by one. [origin] is the
 * button's bounds in this overlay's coordinates. Behind them: a frost scrim that is also the
 * tap-through shield (any touch outside a pill folds them and never reaches the list), a swipe
 * down folds, back folds. Reduced motion: a plain crossfade, no travel or scale.
 */
@Composable
fun ViewSwitcherOverlay(
    state: SwitcherState,
    current: Filter,
    counts: Map<Filter, Int>,
    origin: Rect,
    backdrop: HazeState?,
    onPick: (Filter) -> Unit,
) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val n = TodoViews.size
    val progress = remember { List(n) { Animatable(0f) } }
    val pop = remember { Animatable(1f) }
    val scrim = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    if (state.open) shown = true

    LaunchedEffect(state.open) {
        coroutineScope {
            if (state.open) {
                pop.snapTo(1f)
                launch { if (reduced) scrim.animateTo(1f, tween(150)) else scrim.animateTo(1f, SwitcherState.CloseSpring.float) }
                progress.forEachIndexed { i, a ->
                    launch {
                        if (reduced) a.animateTo(1f, tween(150))
                        else { delay(SwitcherState.openDelay(i)); a.animateTo(1f, SwitcherState.OpenSpring.float) }
                    }
                }
            } else if (shown) {
                val pickedIdx = TodoViews.indexOf(state.picked)
                if (pickedIdx >= 0 && !reduced) launch { pop.snapTo(1f); pop.animateTo(1.08f, SwitcherState.PickPop.float); pop.animateTo(1f, SwitcherState.CloseSpring.float) }
                launch { scrim.animateTo(0f, if (reduced) tween(150) else SwitcherState.CloseSpring.float) }
                progress.forEachIndexed { i, a ->
                    launch {
                        if (reduced) a.animateTo(0f, tween(150))
                        else {
                            // The picked pill lingers for its pop, then folds last.
                            delay(if (i == pickedIdx) 110L else SwitcherState.closeDelay(i, n))
                            a.animateTo(0f, SwitcherState.CloseSpring.float)
                        }
                    }
                }
            }
        }
        if (!state.open) shown = false
    }
    BackHandler(enabled = state.open) { haptics.tick(); state.close() }
    if (!shown) return

    val foldPx = with(density) { SwitcherState.FoldSwipe.toPx() }
    Box(Modifier.fillMaxSize().testTag("switcher-overlay")) {
        // Frost scrim + shield.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrim.value.coerceIn(0f, 1f) * 0.85f }
                .searchFrost(backdrop)
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var dy = 0f
                        var folded = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull() ?: break
                            dy += ch.positionChange().y
                            ch.consume()
                            if (!folded && dy > foldPx) { folded = true; haptics.tick(); state.close() }
                            if (!ch.pressed) break
                        }
                        if (!folded && state.open) { haptics.tick(); state.close() }
                    }
                }
                .semantics { contentDescription = "Close views" },
        )
        // Pills: stacked upwards from just above the button, left-aligned with it.
        val gap = with(density) { 10.dp.roundToPx() }
        val step = with(density) { (PillHeight + 8.dp).roundToPx() }
        Layout(
            content = {
                TodoViews.forEachIndexed { i, view ->
                    val p = progress[i]
                    SwitcherPill(
                        view = view,
                        count = counts[view],
                        selected = view == current,
                        enabled = state.open,
                        onClick = { onPick(view) },
                        modifier = Modifier.graphicsLayer {
                            val k = p.value
                            transformOrigin = TransformOrigin(0f, 1f)
                            if (reduced) { alpha = k.coerceIn(0f, 1f); return@graphicsLayer }
                            // Out of the button: starts on the button (scaled to its size), rises to its slot.
                            val travel = (i * step + gap + origin.height / 2f)
                            translationY = (1f - k) * travel
                            val s = (0.35f + 0.65f * k) * (if (state.picked == view) pop.value else 1f)
                            scaleX = s; scaleY = s
                            alpha = (k * 2.2f).coerceIn(0f, 1f)
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { ms, cons ->
            val loose = Constraints(maxWidth = cons.maxWidth, maxHeight = cons.maxHeight)
            val ps = ms.map { it.measure(loose) }
            layout(cons.maxWidth, cons.maxHeight) {
                var bottom = origin.top.toInt() - gap
                for (pl in ps) {
                    pl.place(origin.left.toInt(), bottom - pl.height)
                    bottom -= pl.height + (step - pl.height)
                }
            }
        }
    }
}

private val PillHeight = 48.dp

@Composable
private fun SwitcherPill(view: Filter, count: Int?, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    val press = remember { MutableInteractionSource() }
    val ink = if (selected) c.paper else c.ink
    Row(
        modifier
            .height(PillHeight)
            .pressScale(press, 0.95f)
            .testTag("switcher-${view.name.lowercase()}")
            .glassShadow(CircleShape, GlassLevel.Mid, c.glassShadow)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.ink) else Modifier.glassChrome().glassRim(CircleShape, c.glassRimTop, c.glassRimBottom))
            .clickable(press, androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = "${view.title}" + (count?.let { ", $it" } ?: "") }
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(view.icon(), null, tint = ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(view.title, style = AppTheme.type.body, color = ink, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        if (count != null) {
            Box(
                Modifier.clip(CircleShape).background(if (selected) c.paper.copy(alpha = 0.18f) else c.inkFill).padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$count", style = AppTheme.type.meta, color = ink, maxLines = 1) }
        }
    }
}
