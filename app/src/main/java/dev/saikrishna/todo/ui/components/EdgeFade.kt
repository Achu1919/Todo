package dev.saikrishna.todo.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v0.13.1: soft fade on the edges of a horizontally scrolling row, only on a side that has more
 * content (so a row that fits is drawn untouched). Apply before `horizontalScroll(state)` so the
 * mask covers the viewport, not the scrolled content.
 */
fun Modifier.horizontalEdgeFade(state: ScrollState, width: Dp = 24.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = width.toPx().coerceAtMost(size.width / 3f)
            if (state.canScrollBackward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                    topLeft = Offset.Zero,
                    size = size.copy(width = w),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (state.canScrollForward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                    topLeft = Offset(size.width - w, 0f),
                    size = size.copy(width = w),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
