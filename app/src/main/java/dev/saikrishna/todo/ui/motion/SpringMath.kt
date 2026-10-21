package dev.saikrishna.todo.ui.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure maths behind SwiftUI-style `spring(response:dampingFraction:)` (unit mass).
 * No Android or Compose dependencies, so it is unit-tested directly.
 */
object SpringMath {
    private const val TWO_PI = 2.0 * PI

    /** Undamped angular frequency ω = 2π / response (rad/s). */
    fun angularFrequency(response: Double): Double {
        require(response > 0) { "response must be > 0" }
        return TWO_PI / response
    }

    /** stiffness = (2π / response)² for a unit mass. */
    fun stiffness(response: Double): Double = angularFrequency(response).pow(2)

    /** Inverse of [stiffness]: the response (undamped period, s) of a spring with stiffness k. */
    fun response(stiffness: Double): Double {
        require(stiffness > 0) { "stiffness must be > 0" }
        return TWO_PI / sqrt(stiffness)
    }

    /** Physical damping coefficient c → damping ratio ζ = c / (2√(k·m)). */
    fun dampingRatio(dampingCoefficient: Double, stiffness: Double, mass: Double = 1.0): Double =
        dampingCoefficient / (2.0 * sqrt(stiffness * mass))

    /** Peak overshoot of a step response, as a fraction of the travel: exp(−ζπ/√(1−ζ²)). 0 for ζ ≥ 1. */
    fun peakOvershoot(dampingRatio: Double): Double {
        require(dampingRatio > 0) { "damping must be > 0" }
        if (dampingRatio >= 1.0) return 0.0
        return exp(-dampingRatio * PI / sqrt(1.0 - dampingRatio * dampingRatio))
    }

    /**
     * Time for the response envelope to fall under [tolerance] of the travel (default 1%).
     * Underdamped: −ln(tol)/(ζω) (≈ 4.6/(ζω)). Critically damped: solve (1+ωt)e^(−ωt) = tol
     * (≈ 6.64/ω at 1%). Overdamped: the slow pole dominates, −ln(tol)/(ω(ζ−√(ζ²−1))).
     */
    fun settleTime(response: Double, dampingRatio: Double, tolerance: Double = 0.01): Double {
        require(tolerance in 0.0..1.0 && tolerance > 0) { "tolerance must be in (0, 1]" }
        val w = angularFrequency(response)
        return when {
            abs(dampingRatio - 1.0) < 1e-9 -> criticalSettleUnits(tolerance) / w
            dampingRatio < 1.0 -> -ln(tolerance) / (dampingRatio * w)
            else -> -ln(tolerance) / (w * (dampingRatio - sqrt(dampingRatio * dampingRatio - 1.0)))
        }
    }

    /** Solves (1+x)e^(−x) = tol for x by Newton's method (x ≈ 6.638 at tol = 0.01). */
    internal fun criticalSettleUnits(tolerance: Double): Double {
        var x = -ln(tolerance) + 1.0
        repeat(50) {
            val f = (1 + x) * exp(-x) - tolerance
            val df = -x * exp(-x)
            val next = x - f / df
            if (abs(next - x) < 1e-12) return next
            x = next
        }
        return x
    }

    /**
     * Closed-form step response x(t) of a unit-mass spring released at rest from 0 towards 1.
     * Used by tests to cross-check overshoot and settle formulas.
     */
    fun stepResponse(t: Double, response: Double, dampingRatio: Double): Double {
        val w = angularFrequency(response)
        val z = dampingRatio
        return when {
            abs(z - 1.0) < 1e-9 -> 1.0 - (1.0 + w * t) * exp(-w * t)
            z < 1.0 -> {
                val wd = w * sqrt(1 - z * z)
                1.0 - exp(-z * w * t) * (kotlin.math.cos(wd * t) + (z * w / wd) * kotlin.math.sin(wd * t))
            }
            else -> {
                val s = sqrt(z * z - 1)
                val r1 = -w * (z - s)
                val r2 = -w * (z + s)
                1.0 - (r2 * exp(r1 * t) - r1 * exp(r2 * t)) / (r2 - r1)
            }
        }
    }
}
