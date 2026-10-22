package dev.saikrishna.todo.ui.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import dev.saikrishna.todo.ui.motion.Durations
import dev.saikrishna.todo.ui.motion.Easings
import dev.saikrishna.todo.ui.motion.Springs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** v0.11 in-place search: where the morph is. */
enum class SearchPhase { Closed, Opening, Open, Closing }

/** Pure: the phase for a target (open or not) and the current progress (0 = circle, 1 = docked pill). */
fun searchPhase(target: Boolean, progress: Float): SearchPhase = when {
    target && progress >= 1f -> SearchPhase.Open
    target -> SearchPhase.Opening
    progress <= 0f -> SearchPhase.Closed
    else -> SearchPhase.Closing
}

/**
 * v0.11 search morph state. ONE progress value (0 = the header's glass circle, 1 = the full-width
 * pill docked above the keyboard) drives everything: the pill's bounds, its contents, the frost,
 * the header/bottom-row fades and the list drift. It is read only in layout/draw lambdas, so the
 * flight never recomposes Home.
 *
 * Motion: [Springs.Smooth] (response 0.5 s, damping 1.0 = critically damped, no overshoot).
 * Interruptible: [open]/[close] retarget the same Animatable mid-flight, keeping its velocity; the
 * reversal starts with velocity AWAY from the new target, and a critically damped spring released
 * that way never crosses it, so the bounds never overshoot either way. Reduced motion: the same
 * progress on a short tween, drawn as a crossfade (the pill never travels).
 */
@Stable
class SearchMorph(private val scope: CoroutineScope) {
    val progress = Animatable(0f, visibilityThreshold = 0.0005f)
    /** true = open (or opening). */
    var target by mutableStateOf(false)
        private set
    var reduced = false
    /** Opened with a filter already applied (tag pill, saved view): don't autofocus / raise the IME. */
    var prefiltered = false
    /** The header circle's bounds in root coordinates, captured at rest. */
    var circle by mutableStateOf(Rect.Zero)
    /** Hooks set by Home: haptic + focus/IME handling at the start of each direction. */
    var onOpenStart: () -> Unit = {}
    var onCloseStart: () -> Unit = {}
    /** Fully closed again (query + filter are reset here: a visit's filter is its own). */
    var onClosed: () -> Unit = {}
    /** Tests: how many open / close commands took effect. */
    var opens = 0
        private set
    var closes = 0
        private set
    private var job: Job? = null

    val k: Float get() = progress.value
    val phase: SearchPhase get() = searchPhase(target, progress.value)
    /** The overlay exists (open, or still animating closed). */
    val active: Boolean get() = target || progress.value > 0f

    fun open(): Boolean {
        if (target) return false
        target = true
        opens++
        onOpenStart()
        run(1f)
        return true
    }

    fun close(): Boolean {
        if (!target) return false
        target = false
        closes++
        onCloseStart()
        run(0f)
        return true
    }

    /** Predictive back: the morph follows the gesture a little way back toward the circle. */
    fun backProgress(p: Float) {
        if (!target) return
        job?.cancel()
        job = scope.launch { progress.snapTo(1f - BackScrub * p.coerceIn(0f, 1f)) }
    }

    fun backCancelled() { if (target) run(1f) }

    private fun spec(): AnimationSpec<Float> =
        if (reduced) tween(ReducedMs, easing = Easings.IosEaseInOut) else Springs.Smooth.spec(0.0005f)

    private fun run(to: Float) {
        job?.cancel()
        job = scope.launch {
            progress.animateTo(to, spec())
            if (to == 0f) onClosed()
        }
    }

    companion object {
        /** How far (of the whole morph) the predictive-back gesture scrubs before commit. */
        const val BackScrub = 0.3f
        const val ReducedMs = Durations.ReducedCrossfade + 50
    }
}

/** Test seams (null in the app). */
object SearchTestHooks {
    /** The live morph of the composed Home. */
    var morph: SearchMorph? = null
    /** Replaces the window IME insets (px): live = this frame, target = where the animation ends. */
    var ime: FakeIme? = null
    /** Layout passes of the results region (must not run per frame while the IME animates). */
    var resultsLayouts = 0
    /** The pill's last placed bounds in root px (left, top, right, bottom). */
    var pill: Rect = Rect.Zero

    class FakeIme {
        val live: MutableIntState = mutableIntStateOf(0)
        val target: MutableIntState = mutableIntStateOf(0)
    }
}
