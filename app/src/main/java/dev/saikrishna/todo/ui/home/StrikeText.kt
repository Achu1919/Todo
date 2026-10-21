package dev.saikrishna.todo.ui.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Text with a strike-through drawn left→right across all wrapped lines (spec 6.5).
 * [progress] and [color] are read in the draw phase only, so animating them never recomposes.
 */
@Composable
fun StrikeText(
    text: String,
    progress: () -> Float,
    style: TextStyle,
    color: ColorProducer,
    strikeColor: () -> Color,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            val p = progress()
            if (p <= 0f) return@drawWithContent
            var total = 0f
            for (i in 0 until l.lineCount) total += l.getLineRight(i) - l.getLineLeft(i)
            var remaining = total * p
            val stroke = 1.5.dp.toPx()
            val col = strikeColor()
            for (i in 0 until l.lineCount) {
                if (remaining <= 0f) break
                val left = l.getLineLeft(i)
                val w = minOf(remaining, l.getLineRight(i) - left)
                // Optical middle of lowercase: a little below the line's geometric centre.
                val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f + 0.5.dp.toPx()
                drawLine(col, Offset(left, y), Offset(left + w, y), stroke, StrokeCap.Round)
                remaining -= w
            }
        },
    )
}
