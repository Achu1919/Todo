package dev.saikrishna.todo.ui.motion

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import kotlin.math.pow

/**
 * The only path to the vibrator (spec 4.1). Our own patterns: a light tick, a firmer thump, and a
 * short rising pulse train. Checks the system touch-feedback setting and an in-app toggle, and uses
 * USAGE_TOUCH so the system touch-intensity setting applies. minSdk 33.
 */
class Haptics(private val view: View, private val userEnabled: () -> Boolean) {
    private val vibrator: Vibrator? =
        view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val touchAttrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    // HAPTIC_FEEDBACK_ENABLED is deprecated in recent SDKs but still the system touch-feedback switch.
    @Suppress("DEPRECATION")
    private fun enabled(): Boolean = userEnabled() && Settings.System.getInt(
        view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1,
    ) != 0

    private fun play(effect: () -> VibrationEffect, fallback: Int) {
        if (!enabled()) return
        val v = vibrator
        if (v != null && v.hasVibrator() && v.hasAmplitudeControl()) v.vibrate(effect(), touchAttrs)
        else view.performHapticFeedback(fallback)
    }

    /** Light: toggles, thresholds, filter change, task added. */
    fun tick() = play({ VibrationEffect.createOneShot(TICK_MS, TICK_AMP) }, HapticFeedbackConstants.CLOCK_TICK)

    /** Firm: task completed, long-press focus. */
    fun thump() = play({ VibrationEffect.createOneShot(THUMP_MS, THUMP_AMP) }, HapticFeedbackConstants.CONFIRM)

    /** Rising 6-pulse train: "all clear". */
    fun rise() = play({ VibrationEffect.createWaveform(riseTimings(), riseAmplitudes(), -1) }, HapticFeedbackConstants.CONFIRM)

    companion object {
        const val TICK_MS = 12L
        const val TICK_AMP = 140
        const val THUMP_MS = 24L
        const val THUMP_AMP = 220

        /** Alternating gap/pulse timings: 38 ms gaps, 9 ms pulses, first gap 0. */
        fun riseTimings(): LongArray = LongArray(12) { if (it % 2 == 0) 38L else 9L }.also { it[0] = 0L }

        /** Pulse amplitudes rise 20% → 100% on a 1.5 power curve; gaps are 0. */
        fun riseAmplitudes(): IntArray = IntArray(12) { i ->
            if (i % 2 == 0) 0 else (255 * (0.2f + 0.8f * ((i / 2) / 5f).pow(1.5f))).toInt()
        }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

@Composable
fun ProvideHaptics(userEnabled: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    val enabled = rememberUpdatedState(userEnabled)
    val haptics = remember(view) { Haptics(view) { enabled.value } }
    CompositionLocalProvider(LocalHaptics provides haptics, content = content)
}
