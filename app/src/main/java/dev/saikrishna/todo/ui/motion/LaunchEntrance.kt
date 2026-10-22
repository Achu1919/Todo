package dev.saikrishna.todo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v0.10 cold-start entrance. The sky is already there (window background + splash colour); the
 * header glass drifts down 8 dp, the first [MaxRows] task rows rise 24 dp with a [StaggerMs]
 * stagger, and the bottom row rises from below, all faded in. Everything is a pure function of one
 * clock (elapsed ms), evaluated in the draw phase (graphicsLayer): no recomposition per frame, no
 * layout change, and input is live from the first frame (taps are never delayed).
 *
 * Plays once per Activity creation without saved state ([play]); returning from the background,
 * configuration changes and navigation pops reuse the settled instance and never replay.
 * Reduced motion: one 150 ms fade for everything, no movement.
 */
@Stable
class LaunchEntrance(play: Boolean) {
    /** Elapsed ms since the entrance started; [TotalMs] = settled. */
    private val clock = Animatable(if (play) 0f else TotalMs.toFloat())
    private var started = !play
    var reduced: Boolean = false
        private set

    val settled: Boolean get() = clock.value >= limit()
    private fun limit() = if (reduced) Durations.ReducedCrossfade.toFloat() else TotalMs.toFloat()

    /** Starts once (call when the first data is on screen). */
    suspend fun start(reduced: Boolean) {
        if (started) return
        started = true
        this.reduced = reduced
        val end = limit()
        clock.animateTo(end, tween(end.toInt(), easing = LinearEasing))
    }

    /** Snaps to settled (tests, or when the screen is left mid-entrance). */
    suspend fun finish() { started = true; clock.snapTo(TotalMs.toFloat()) }

    /** 0..1 progress of [slot] at the current clock. */
    fun progress(slot: Slot): Float = progressAt(clock.value, slot, reduced)

    sealed interface Slot {
        data object Header : Slot
        data class Row(val index: Int) : Slot
        data object Bottom : Slot
    }

    companion object {
        /** Total budget; every slot is exactly settled at this point. */
        const val TotalMs = 450
        const val StaggerMs = 32
        const val MaxRows = 8
        const val HeaderDelayMs = 0
        const val BottomDelayMs = 60
        val RowRise: Dp = 24.dp
        val HeaderDrop: Dp = 8.dp
        val BottomRise: Dp = 24.dp
        /** Faster than [Springs.Quick] so 8 staggered rows settle well inside [TotalMs]. */
        val Rise = SpringToken(0.24f, 0.90f)

        fun delayOf(slot: Slot): Int = when (slot) {
            Slot.Header -> HeaderDelayMs
            Slot.Bottom -> BottomDelayMs
            is Slot.Row -> slot.index.coerceIn(0, MaxRows - 1) * StaggerMs
        }

        fun progressAt(elapsedMs: Float, slot: Slot, reduced: Boolean): Float {
            if (reduced) return (elapsedMs / Durations.ReducedCrossfade).coerceIn(0f, 1f)
            if (elapsedMs >= TotalMs) return 1f
            val t = (elapsedMs - delayOf(slot)) / 1000.0
            if (t <= 0) return 0f
            return SpringMath.stepResponse(t, Rise.response.toDouble(), Rise.damping.toDouble()).toFloat().coerceIn(0f, 1.02f)
        }
    }
}

/** The running entrance, or null (nothing animates) e.g. in previews and other screens. */
val LocalLaunchEntrance = staticCompositionLocalOf<LaunchEntrance?> { null }

/** Applies the entrance for [slot]: fade + vertical offset, draw phase only. */
fun Modifier.launchEntrance(entrance: LaunchEntrance?, slot: LaunchEntrance.Slot): Modifier {
    if (entrance == null) return this
    val distance = when (slot) {
        LaunchEntrance.Slot.Header -> -LaunchEntrance.HeaderDrop
        LaunchEntrance.Slot.Bottom -> LaunchEntrance.BottomRise
        is LaunchEntrance.Slot.Row -> LaunchEntrance.RowRise
    }
    return graphicsLayer {
        if (entrance.settled) return@graphicsLayer
        val p = entrance.progress(slot)
        alpha = p.coerceIn(0f, 1f)
        translationY = if (entrance.reduced) 0f else (1f - p) * distance.toPx()
    }
}
