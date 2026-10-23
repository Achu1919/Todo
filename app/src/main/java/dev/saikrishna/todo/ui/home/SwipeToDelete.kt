package dev.saikrishna.todo.ui.home

import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassShadow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Radii
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.PillSpec
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Swipe end→start to delete (spec 6.6) and start→end to snooze (v0.6), on M3 1.4
 * SwipeToDismissBox (`onDismiss` callback). Each side's capsule is drawn only over the revealed
 * area, from the live offset, in the draw phase: danger red for delete, a quiet glass capsule
 * with a clock for snooze. Icons scale 0.7 ↔ 1 on Quick as the 35 % threshold is crossed, with one
 * `tick` per crossing. Delete slides off and the caller collapses the row (6.9); snooze snaps the
 * row back and hands over to the floating snooze picker.
 */
@Composable
fun SwipeToDeleteRow(
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onSnooze: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val delete by rememberUpdatedState(onDelete)
    val snooze by rememberUpdatedState(onSnooze)
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f })
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }
            .distinctUntilChanged().drop(1).collect { haptics.tick() }
    }
    val armedDelete = state.targetValue == SwipeToDismissBoxValue.EndToStart
    val armedSnooze = state.targetValue == SwipeToDismissBoxValue.StartToEnd
    val trashScale by animateFloatAsState(if (armedDelete) 1f else 0.7f, Springs.Quick.float, label = "trash")
    val clockScale by animateFloatAsState(if (armedSnooze) 1f else 0.7f, Springs.Quick.float, label = "clock")
    val canSnooze = enabled && onSnooze != null

    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = buildList {
                if (onSnooze != null) add(CustomAccessibilityAction("Snooze") { snooze?.invoke(); true })
                add(CustomAccessibilityAction("Delete") { delete(); true })
            }
        },
        enableDismissFromStartToEnd = canSnooze,
        enableDismissFromEndToStart = enabled,
        gesturesEnabled = enabled,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> delete()
                SwipeToDismissBoxValue.StartToEnd -> scope.launch {
                    // Back in place first, so the picker's container transform starts from the card.
                    state.snapTo(SwipeToDismissBoxValue.Settled)
                    snooze?.invoke()
                }
                else -> Unit
            }
        },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val off = state.offsetOrZero()
                        val r = CornerRadius(Radii.lg.toPx())
                        if (off < 0f) {
                            drawRoundRect(c.danger, topLeft = Offset(size.width + off, 0f), size = Size(-off, size.height), cornerRadius = r)
                        } else if (off > 0f) {
                            drawRoundRect(c.glassFill, size = Size(off, size.height), cornerRadius = r)
                            val w = 1.dp.toPx()
                            drawRoundRect(
                                Brush.verticalGradient(listOf(c.glassRimTop, c.glassRimBottom)),
                                topLeft = Offset(w / 2, w / 2),
                                size = Size((off - w).coerceAtLeast(0f), size.height - w),
                                cornerRadius = r,
                                style = Stroke(w),
                            )
                        }
                    },
            ) {
                Icon(
                    AppIcons.Clock, null, tint = c.ink,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                        .size(22.dp)
                        .graphicsLayer {
                            val off = state.offsetOrZero()
                            alpha = (off / (56.dp.toPx())).coerceIn(0f, 1f)
                            scaleX = clockScale
                            scaleY = clockScale
                        },
                )
                Icon(
                    AppIcons.Trash, null, tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .size(22.dp)
                        .graphicsLayer {
                            val off = state.offsetOrZero()
                            alpha = ((-off) / (56.dp.toPx())).coerceIn(0f, 1f)
                            scaleX = trashScale
                            scaleY = trashScale
                        },
                )
            }
        },
    ) { content() }
}

/**
 * Undo bar (spec 6.6): rises from below the composer (translationY 100 % → 0 on Smooth + fade),
 * auto-hides after 4 s of wall-clock time, polite live region.
 */
@Composable
fun UndoBar(event: UndoEvent?, onUndo: (UndoEvent) -> Unit, onTimeout: (UndoEvent) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    LaunchedEffect(event) {
        if (event != null) { kotlinx.coroutines.delay(4_000); onTimeout(event) }
    }
    // Keep showing the last event while the exit animation runs.
    val shown = rememberLast(event)
    AnimatedVisibility(
        visible = event != null,
        modifier = modifier,
        enter = if (reduced) fadeIn(Tweens.reduced())
        else slideInVertically(Springs.Smooth.intOffset) { it } + fadeIn(Tweens.fadeIn(0)),
        exit = if (reduced) fadeOut(Tweens.fadeOutFast())
        else slideOutVertically(Springs.Smooth.intOffset) { it } + fadeOut(Tweens.fadeOut()),
    ) {
        val e = shown ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 4.dp)
                .heightIn(min = PillSpec.Height)
                .glassShadow(Shapes.full, GlassLevel.Mid, c.glassShadow)
                .clip(Shapes.full)
                .background(c.ink) // v0.12.1: solid ink, no inner rim line
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(e.message, color = c.onInk, style = AppTheme.type.label, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
            Text(
                "Undo",
                color = c.accentOnInk,
                style = AppTheme.type.label,
                modifier = Modifier
                    .clip(Shapes.full)
                    .clickable(role = Role.Button) { onUndo(e) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
    }
}

/** Offset without allocating (requireOffset throws before the first layout). */
private fun androidx.compose.material3.SwipeToDismissBoxState.offsetOrZero(): Float =
    try { requireOffset() } catch (_: IllegalStateException) { 0f }

@Composable
private fun <T : Any> rememberLast(value: T?): T? {
    val holder = androidx.compose.runtime.remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}
