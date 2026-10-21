package dev.saikrishna.todo.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class SpringMathTest {

    @Test
    fun stiffnessIsTwoPiOverResponseSquared() {
        assertEquals((2 * PI / 0.5) * (2 * PI / 0.5), SpringMath.stiffness(0.5), 1e-9)
        assertEquals(157.91, SpringMath.stiffness(0.50), 0.01)
    }

    /** The stiffness column of spec table 3.1. */
    @Test
    fun specTableStiffnesses() {
        val expected = mapOf(
            0.50 to 157.9, 0.40 to 246.7, 0.30 to 438.6, 0.55 to 130.5,
            0.44 to 203.9, 0.80 to 61.7, 0.35 to 322.3,
        )
        expected.forEach { (response, k) ->
            assertEquals("response $response", k, SpringMath.stiffness(response), 0.05)
        }
    }

    @Test
    fun responseRoundTrips() {
        listOf(0.25, 0.3, 0.35, 0.42, 0.5, 0.65, 0.8, 1.2).forEach { r ->
            assertEquals(r, SpringMath.response(SpringMath.stiffness(r)), 1e-12)
        }
    }

    @Test
    fun floatHelperMatchesDoubleMath() {
        Springs.all.values.forEach { t ->
            assertEquals(SpringMath.stiffness(t.response.toDouble()), stiffnessFor(t.response).toDouble(), 1e-3)
        }
    }

    @Test
    fun dampingCoefficientToRatio() {
        // c / (2 sqrt(k m)): k = 168, c = 23 -> 0.887
        assertEquals(0.8873, SpringMath.dampingRatio(23.0, 168.0), 1e-3)
        // Critical damping: c = 2 sqrt(k)
        assertEquals(1.0, SpringMath.dampingRatio(2 * kotlin.math.sqrt(157.9), 157.9), 1e-12)
    }

    /** Spec section 1 overshoot table. */
    @Test
    fun peakOvershootTable() {
        val table = mapOf(
            1.00 to 0.0, 0.92 to 0.0006, 0.90 to 0.0015, 0.86 to 0.0050,
            0.82 to 0.0110, 0.80 to 0.0152, 0.78 to 0.0198, 0.72 to 0.0384, 0.58 to 0.1068,
        )
        table.forEach { (z, os) -> assertEquals("zeta $z", os, SpringMath.peakOvershoot(z), 0.0006) }
        assertEquals(0.0, SpringMath.peakOvershoot(1.2), 0.0)
    }

    @Test
    fun overshootFormulaMatchesClosedFormStepResponse() {
        listOf(0.58, 0.72, 0.8, 0.86, 0.92).forEach { z ->
            var peak = 0.0
            var t = 0.0
            while (t < 3.0) { peak = maxOf(peak, SpringMath.stepResponse(t, 0.5, z)); t += 1e-4 }
            assertEquals("zeta $z", SpringMath.peakOvershoot(z), peak - 1.0, 1e-5)
        }
    }

    @Test
    fun criticalSettleConstant() {
        assertEquals(6.638, SpringMath.criticalSettleUnits(0.01), 1e-3)
    }

    /** The settle column of spec table 3.1. */
    @Test
    fun specTableSettleTimes() {
        val expected = mapOf(
            Springs.Smooth to 0.53, Springs.Glide to 0.34, Springs.Snappy to 0.46, Springs.Quick to 0.26,
            Springs.Sheet to 0.58, Springs.Gesture to 0.35, Springs.Soft to 0.65, Springs.Pop to 0.44,
        )
        expected.forEach { (t, s) ->
            assertEquals("$t", s, SpringMath.settleTime(t.response.toDouble(), t.damping.toDouble()), 0.01)
        }
    }

    @Test
    fun settledStepResponseIsWithinTolerance() {
        Springs.all.values.forEach { t ->
            val r = t.response.toDouble(); val z = t.damping.toDouble()
            val ts = SpringMath.settleTime(r, z)
            // From the settle time on, the response stays inside the 1% envelope (the underdamped
            // envelope carries a 1/sqrt(1 - z^2) amplitude factor).
            val bound = if (z < 1.0) 0.01 / kotlin.math.sqrt(1 - z * z) + 1e-4 else 0.0101
            var time = ts
            while (time < ts + 2.0) {
                assertTrue("$t at $time", abs(SpringMath.stepResponse(time, r, z) - 1.0) <= bound)
                time += 1e-3
            }
        }
    }

    @Test
    fun overdampedSettlesSlowerThanCritical() {
        assertTrue(SpringMath.settleTime(0.5, 1.3) > SpringMath.settleTime(0.5, 1.0))
    }

    @Test
    fun nothingSpatialVisiblyBouncesExceptPop() {
        // Spec rule: zeta >= 0.80 for everything except the Pop accent.
        Springs.all.filterKeys { it != "Pop" }.values.forEach { t ->
            assertTrue("$t", SpringMath.peakOvershoot(t.damping.toDouble()) <= 0.016)
        }
        assertTrue(SpringMath.peakOvershoot(Springs.Pop.damping.toDouble()) > 0.10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveResponse() {
        SpringMath.stiffness(0.0)
    }
}
