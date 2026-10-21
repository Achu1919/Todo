package dev.saikrishna.todo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens

/**
 * Collapse with list reflow (spec 6.9): height → 0 towards the top on Smooth, alpha out in 150 ms,
 * scale → 0.97. Rows below follow the shrinking height every frame (pure layout), and the data
 * removal ([onRemoved]) happens only after the collapse, so it causes no jump.
 * Put the row gap *inside* [content] so it collapses too.
 */
@Composable
fun CollapsingItem(
    removing: Boolean,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val visible = remember { MutableTransitionState(true) }
    val removed = rememberUpdatedState(onRemoved)
    val isRemoving = rememberUpdatedState(removing)
    LaunchedEffect(removing) { visible.targetState = !removing }

    LaunchedEffect(visible.isIdle, visible.currentState, removing) {
        if (removing && visible.isIdle && !visible.currentState) removed.value()
    }
    // Scrolled away (or otherwise disposed) mid-collapse: finish immediately.
    DisposableEffect(Unit) { onDispose { if (isRemoving.value) removed.value() } }

    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reduced) {
            fadeOut(Tweens.fadeOut()) + shrinkVertically(Tweens.reduced(), shrinkTowards = Alignment.Top)
        } else {
            shrinkVertically(Springs.Smooth.intSize, shrinkTowards = Alignment.Top) +
                fadeOut(Tweens.fadeOut()) +
                scaleOut(Springs.Smooth.float, targetScale = 0.97f)
        },
    ) { content() }
}
