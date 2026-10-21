package dev.saikrishna.todo.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.7.3: scroll-direction state machine of the collapsing composer (28 / 16 px thresholds here). */
class CollapseMachineTest {
    private fun m() = CollapseMachine(collapseAfter = 28f, expandAfter = 16f)
    private fun CollapseMachine.down(px: Float, atTop: Boolean = false, atBottom: Boolean = false, blocked: Boolean = false, scrollable: Boolean = true) =
        onScroll(px, atTop, atBottom, blocked, scrollable)

    @Test fun collapsesOnlyAfterAccumulatedDownwardScroll() {
        val s = m()
        assertFalse(s.down(10f)); assertFalse(s.down(10f)); assertFalse(s.down(7.9f))
        assertTrue(s.down(0.2f)) // 28.1
    }

    @Test fun directionFlipResetsTheAccumulator() {
        val s = m()
        s.down(20f); s.down(-1f) // flip: start over
        assertFalse(s.down(20f))
        assertTrue(s.down(8f))
    }

    @Test fun expandsAfterUpwardScrollPastThreshold() {
        val s = m()
        s.down(30f)
        assertTrue(s.down(-10f)); assertTrue(s.down(-5.9f))
        assertFalse(s.down(-0.2f))
    }

    @Test fun jitterDoesNotToggle() {
        val s = m()
        s.down(30f)
        repeat(50) { assertTrue(s.down(if (it % 2 == 0) -8f else 8f)) }
    }

    @Test fun zeroDeltaKeepsState() {
        val s = m(); s.down(30f)
        assertTrue(s.down(0f))
    }

    @Test fun reachingTopOrBottomExpands() {
        val s = m(); s.down(30f)
        assertFalse(s.down(5f, atBottom = true))
        s.down(30f)
        assertFalse(s.down(-2f, atTop = true))
    }

    @Test fun blockedOrNotScrollableNeverCollapses() {
        val s = m()
        assertFalse(s.down(100f, blocked = true))
        assertFalse(s.down(100f, scrollable = false))
        // ...and the accumulator was cleared: 27 more px is still not enough.
        assertFalse(s.down(27f))
        s.down(30f)
        assertFalse(s.down(1f, blocked = true))
    }

    @Test fun explicitExpandClears() {
        val s = m(); s.down(30f)
        assertFalse(s.expand())
        assertFalse(s.down(27f))
        assertTrue(s.down(1f))
    }
}
