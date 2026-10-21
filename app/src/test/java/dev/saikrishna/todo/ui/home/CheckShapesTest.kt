package dev.saikrishna.todo.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** The checkbox morph must stay inside the ring it grows out of. */
class CheckShapesTest {
    private fun radii(progress: Float): List<Double> = CheckShapes.morph.asCubics(progress).flatMap { c ->
        (0..20).map { i ->
            val t = i / 20.0
            val u = 1 - t
            val x = u * u * u * c.anchor0X + 3 * u * u * t * c.control0X + 3 * u * t * t * c.control1X + t * t * t * c.anchor1X
            val y = u * u * u * c.anchor0Y + 3 * u * u * t * c.control0Y + 3 * u * t * t * c.control1Y + t * t * t * c.anchor1Y
            hypot(x, y)
        }
    }

    @Test
    fun startIsTheUnitCircle() {
        val r = radii(0f)
        assertTrue("min ${r.min()}", r.min() > 0.99)
        assertTrue("max ${r.max()}", r.max() < 1.01)
    }

    @Test
    fun everyMorphStepFitsInsideTheUnitCircle() {
        for (i in 0..10) {
            val r = radii(i / 10f)
            assertTrue("progress ${i / 10f}: max ${r.max()}", r.max() <= 1.005)
        }
    }

    @Test
    fun endIsASquircleNotACircle() {
        val r = radii(1f)
        // Flat sides are at 0.85; rounded corners reach ~1.
        assertTrue("min ${r.min()}", r.min() < 0.86)
        assertTrue("max ${r.max()}", r.max() > 0.95)
    }
}
