package dev.saikrishna.todo.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Radii and the 4 dp spacing grid. */
object Radii {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 20.dp
    val xl = 24.dp
    /** Task rows: between lg and xl so a one-line row reads as a soft card, not a capsule. */
    val row = 22.dp
}

object Shapes {
    val xs = RoundedCornerShape(Radii.xs)
    val sm = RoundedCornerShape(Radii.sm)
    val md = RoundedCornerShape(Radii.md)
    val lg = RoundedCornerShape(Radii.lg) // settings cards, context menu
    val xl = RoundedCornerShape(Radii.xl) // sheet
    val row = RoundedCornerShape(Radii.row)
    val full = CircleShape // pills, chips, composer, undo bar, circle buttons
}

object Spacing {
    val screen = 20.dp
    val rowH = 16.dp
    val rowV = 15.dp
    val rowGap = 10.dp
    val composerInset = 12.dp
}

/**
 * Floating-pill proportions, measured from the reference recordings (px ÷ 1.75 px/dp, see NOTES.md):
 * track ≈ 49 dp tall, indicator inset ≈ 2.3 dp, equal-width segments ≈ 77 dp, 14 sp medium labels,
 * round controls ≈ 42 dp beside it. Rounded to our grid.
 */
object PillSpec {
    val Height = 48.dp
    val Inset = 2.dp
    val MinSegment = 76.dp
    val SegmentPadding = 14.dp
    val Control = 44.dp
    val ChipHeight = 36.dp
    val Rim = 1.dp
}
