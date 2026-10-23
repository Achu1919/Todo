package dev.saikrishna.todo.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.domain.CalendarData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate
import kotlin.math.abs

/**
 * v0.13.1: the collapsed Calendar week strip must show the selected week's 7 day numbers,
 * laid out in one row inside the grid's clip, with the selected-day circle and the task dots
 * actually drawn. Before the fix the rows below the first were squeezed to 0 dp under the
 * one-row clip, so the strip was blank. Shared by [CalendarWeekStripTest] and the v013 shots.
 */
object WeekStripCheck {
    /** [withDots]: days whose dot row must hold ≥ 1 drawn dot. */
    fun assertDrawn(rule: ComposeTestRule, selected: LocalDate, withDots: Set<LocalDate>) {
        assertFalse("strip must be collapsed", CalendarTestHooks.state!!.expanded)
        val density = rule.density.density
        val grid = rule.onNodeWithTag("calendar-grid").fetchSemanticsNode().boundsInRoot
        val week = CalendarData.week(selected)
        val cells = week.map { d ->
            val nodes = rule.onAllNodesWithTag("cal-$d", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("one cell for $d", 1, nodes.size)
            nodes[0].boundsInRoot
        }
        val top = cells[0].top
        cells.forEachIndexed { i, b ->
            val d = week[i]
            assertTrue("$d cell has height (${b.height} px)", b.height >= 40 * density)
            assertTrue("$d cell inside the strip ($b vs $grid)", b.top >= grid.top - 1 && b.bottom <= grid.bottom + 1)
            assertEquals("$d on the same row", top, b.top, 1f)
            if (i > 0) assertTrue("$d right of ${week[i - 1]}", b.left > cells[i - 1].left + 20 * density)
            val num = rule.onAllNodes(hasText(d.dayOfMonth.toString()) and hasAnyAncestor(hasTestTag("cal-$d")), useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertEquals("day number for $d", 1, num.size)
            val nb = num[0].boundsInRoot
            assertTrue("$d number laid out ($nb)", nb.height > 8 * density && nb.top >= grid.top - 1 && nb.bottom <= grid.bottom + 1)
        }
        // Pixels: every number is drawn, the selected circle is filled, the dots are there.
        val bmp = rule.onNodeWithTag("calendar-grid").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        fun local(r: Rect) = Rect(r.left - grid.left, r.top - grid.top, r.right - grid.left, r.bottom - grid.top)
        cells.forEachIndexed { i, b0 ->
            val d = week[i]
            val b = local(b0)
            val cy = b.center.y
            val ink = contrast(bmp, b.left, cy - 16 * density, b.right, cy + 16 * density)
            assertTrue("$d number drawn ($ink px)", ink >= 25)
            if (d == selected) assertTrue("$d selected circle drawn ($ink px)", ink >= 1500)
            if (d in withDots) {
                val dots = rule.onAllNodesWithTag("cal-dots-$d", useUnmergedTree = true).fetchSemanticsNodes()[0].boundsInRoot.let(::local)
                val px = contrast(bmp, dots.left - 2, dots.top, dots.right + 2, dots.bottom + 2)
                assertTrue("$d dots drawn ($px px)", px >= 4)
            }
        }
    }

    /** Pixels in the box that differ clearly from the box's top-left corner pixel. */
    private fun contrast(bmp: Bitmap, l: Float, t: Float, r: Float, b: Float): Int {
        val x0 = l.toInt().coerceIn(0, bmp.width - 1); val x1 = r.toInt().coerceIn(x0 + 1, bmp.width)
        val y0 = t.toInt().coerceIn(0, bmp.height - 1); val y1 = b.toInt().coerceIn(y0 + 1, bmp.height)
        val ref = bmp.getPixel(x0, y0)
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = bmp.getPixel(x, y)
            val diff = abs(Color.red(p) - Color.red(ref)) + abs(Color.green(p) - Color.green(ref)) + abs(Color.blue(p) - Color.blue(ref))
            if (diff > 90) n++
        }
        return n
    }
}
