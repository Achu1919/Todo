package dev.saikrishna.todo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.SpringToken
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * v0.9.1: our press feedback, replacing Material's grey ripple app-wide (provided as
 * LocalIndication by AppTheme). A faint ink tint that fades in on press and out on release,
 * drawn strictly INSIDE the node's bounds (and inside any clip before the clickable): a circle
 * for square nodes (round buttons), otherwise a 12 dp rounded rect. No spreading wave, no focus
 * or hover outline.
 */
class SoftPress(private val tint: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = SoftPressNode(interactionSource, tint)
    override fun equals(other: Any?) = other is SoftPress && other.tint == tint
    override fun hashCode() = tint.hashCode()

    companion object {
        /** Peak tint alpha (relative to the ink colour). */
        const val PeakAlpha = 0.07f
    }
}

private class SoftPressNode(private val source: InteractionSource, private val tint: Color) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val target = if (pressed > 0) 1f else 0f
                launch { alpha.animateTo(target, tween(if (target > 0f) 80 else 180)) { invalidateDraw() } }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = alpha.value
        if (a <= 0f) return
        val w = size.width; val h = size.height
        val r = if (abs(w - h) < 1.dp.toPx()) minOf(w, h) / 2f else minOf(12.dp.toPx(), h / 2f)
        drawRoundRect(tint.copy(alpha = tint.alpha * SoftPress.PeakAlpha * a), cornerRadius = CornerRadius(r))
    }
}

private val PressSpring = SpringToken(0.30f, 0.86f) // Quick

/**
 * v0.9.1: a gentle scale while [source] is pressed (spring, layer only). Off with reduced motion.
 * Content stays inside its own bounds (scale < 1).
 */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val s by animateFloatAsState(if (isPressed && !reduced) pressed else 1f, PressSpring.float, label = "press scale")
    return graphicsLayer { scaleX = s; scaleY = s }
}
