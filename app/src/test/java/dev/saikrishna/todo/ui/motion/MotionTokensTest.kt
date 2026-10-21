package dev.saikrishna.todo.ui.motion

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the Compose SpringSpecs we build behave like the SwiftUI springs they model. */
class MotionTokensTest {

    @Test
    fun tokenSpecCarriesStiffnessAndDamping() {
        Springs.all.values.forEach { t ->
            val spec = t.float
            assertEquals(t.damping, spec.dampingRatio, 0f)
            assertEquals(stiffnessFor(t.response), spec.stiffness, 1e-3f)
        }
        assertEquals(157.91f, Springs.Smooth.float.stiffness, 0.01f)
        assertEquals(1f, Springs.Smooth.float.dampingRatio, 0f)
    }

    /** Runs Compose's own spring integrator and compares its peak overshoot with the formula. */
    @Test
    fun composeSpringOvershootMatchesFormula() {
        Springs.all.values.forEach { t ->
            val v = t.float.vectorize(Float.VectorConverter)
            val from = AnimationVector1D(0f)
            val to = AnimationVector1D(1f)
            val vel = AnimationVector1D(0f)
            var peak = 0f
            var nanos = 0L
            while (nanos < 2_000_000_000L) {
                peak = maxOf(peak, v.getValueFromNanos(nanos, from, to, vel).value)
                nanos += 1_000_000L
            }
            val expected = SpringMath.peakOvershoot(t.damping.toDouble()).toFloat()
            assertEquals("$t", expected, peak - 1f, 2e-3f)
        }
    }

    @Test
    fun composeSpringDurationTracksResponse() {
        // A slower response must take longer for Compose to settle.
        val fast = Springs.Quick.float.vectorize(Float.VectorConverter)
            .getDurationNanos(AnimationVector1D(0f), AnimationVector1D(100f), AnimationVector1D(0f))
        val slow = Springs.Soft.float.vectorize(Float.VectorConverter)
            .getDurationNanos(AnimationVector1D(0f), AnimationVector1D(100f), AnimationVector1D(0f))
        assertTrue("fast=$fast slow=$slow", slow > fast)
    }

    @Test
    fun hapticRiseIsMonotonicAndBounded() {
        val amps = Haptics.riseAmplitudes()
        val timings = Haptics.riseTimings()
        assertEquals(12, amps.size); assertEquals(12, timings.size)
        assertEquals(0L, timings[0])
        val pulses = amps.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(6, pulses.size)
        assertTrue(pulses.zipWithNext().all { (a, b) -> b > a })
        assertEquals(255, pulses.last())
        assertTrue(pulses.first() in 40..60)
        assertTrue(amps.filterIndexed { i, _ -> i % 2 == 0 }.all { it == 0 })
    }
}
