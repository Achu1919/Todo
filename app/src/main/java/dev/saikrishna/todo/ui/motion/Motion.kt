package dev.saikrishna.todo.ui.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.pow

/** Stiffness of a SwiftUI-style spring whose undamped period is [response] seconds (unit mass). */
fun stiffnessFor(response: Float): Float = (2f * PI.toFloat() / response).pow(2)

/** SwiftUI `spring(response:dampingFraction:)` as a Compose [SpringSpec]. */
fun <T> iosSpring(
    response: Float,
    damping: Float = 1f,
    visibilityThreshold: T? = null,
): SpringSpec<T> = spring(
    dampingRatio = damping,
    stiffness = stiffnessFor(response),
    visibilityThreshold = visibilityThreshold,
)

@Immutable
data class SpringToken(val response: Float, val damping: Float) {
    init {
        require(response > 0f) { "response must be > 0" }
        require(damping > 0f) { "damping must be > 0" }
    }

    val stiffness: Float get() = stiffnessFor(response)

    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        iosSpring(response, damping, visibilityThreshold)

    val float: SpringSpec<Float> get() = spec()
    val dp: SpringSpec<Dp> get() = spec(Dp.VisibilityThreshold)
    val intOffset: SpringSpec<IntOffset> get() = spec(IntOffset.VisibilityThreshold)
    val intSize: SpringSpec<IntSize> get() = spec(IntSize.VisibilityThreshold)
    val offset: SpringSpec<Offset> get() = spec(Offset.VisibilityThreshold)
    val size: SpringSpec<Size> get() = spec(Size.VisibilityThreshold)
    val rect: SpringSpec<Rect> get() = spec(Rect.VisibilityThreshold)
}

/** Spec 3.1. Near-critical damping by default; [Pop] is the only visible bounce (accent only). */
object Springs {
    val Smooth = SpringToken(0.50f, 1.00f)
    val Glide = SpringToken(0.40f, 0.86f)
    val Snappy = SpringToken(0.50f, 0.80f)
    val Quick = SpringToken(0.30f, 0.86f)
    val Sheet = SpringToken(0.55f, 1.00f)
    val Gesture = SpringToken(0.44f, 0.92f)
    val Soft = SpringToken(0.80f, 0.90f)
    val Pop = SpringToken(0.35f, 0.58f) // accent only
    /** v0.7.1: small floating menus morphing out of / into their pill (list picker, "+" menu). */
    val Morph = SpringToken(0.38f, 0.90f)

    /** Pop, or Quick under reduced motion (spec 7). */
    fun accent(reduced: Boolean): SpringToken = if (reduced) Quick else Pop

    val all: Map<String, SpringToken> = linkedMapOf(
        "Smooth" to Smooth, "Glide" to Glide, "Snappy" to Snappy, "Quick" to Quick,
        "Sheet" to Sheet, "Gesture" to Gesture, "Soft" to Soft, "Pop" to Pop, "Morph" to Morph,
    )
}

object Easings {
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val Emphasized = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
    val IosEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val IosEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val IosEaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
}

object Durations {
    const val FadeOutFast = 120
    const val FadeOut = 150
    const val FadeIn = 220
    const val EnterDelay = 50
    const val Select = 240
    const val Strike = 280
    const val FadeInSlow = 320
    const val Emphasized = 400
    const val ReducedCrossfade = 150
    /** Completed row stays in place this long before it collapses out (spec 6.5). */
    const val CompleteDwell = 700
    const val UncheckReverse = 120
    const val CheckDelay = 40L
    const val RiseAfterThump = 250L
}

/**
 * Wall-clock dwell before a completed row leaves. Coroutine delays are not scaled by the system
 * animator scale; we stretch (never shrink) the dwell by it so slowed-down recordings stay in sync,
 * and keep the full dwell when animations are off so the undo window still exists.
 */
fun completionDwellMillis(animatorScale: Float): Long =
    (Durations.CompleteDwell * maxOf(1f, animatorScale)).toLong()

/** Spec 3.2. Opacity and colour use tweens, never springs. */
object Tweens {
    fun <T> fadeOutFast(): TweenSpec<T> = tween(Durations.FadeOutFast, easing = Easings.Standard)
    fun <T> fadeOut(): TweenSpec<T> = tween(Durations.FadeOut, easing = Easings.Standard)
    fun <T> fadeIn(delay: Int = Durations.EnterDelay): TweenSpec<T> =
        tween(Durations.FadeIn, delayMillis = delay, easing = Easings.Standard)
    fun <T> fadeInSlow(delay: Int = 0): TweenSpec<T> =
        tween(Durations.FadeInSlow, delayMillis = delay, easing = Easings.IosEaseOut)
    fun <T> select(): TweenSpec<T> = tween(Durations.Select, easing = Easings.Emphasized)
    fun <T> strike(delay: Int = 60): TweenSpec<T> =
        tween(Durations.Strike, delayMillis = delay, easing = Easings.Emphasized)
    fun <T> crossfade(): TweenSpec<T> = tween(Durations.FadeIn, easing = Easings.IosEaseInOut)
    fun <T> emphasized(): TweenSpec<T> = tween(Durations.Emphasized, easing = Easings.Emphasized)
    /** Reduced-motion replacement for spatial moves. */
    fun <T> reduced(): TweenSpec<T> = tween(Durations.ReducedCrossfade, easing = Easings.IosEaseInOut)
}

/** Spec 2.4 depth tokens. */
object Depth {
    /** Live blur radii are capped at 16 dp and never animated (v0.5 perf pass). */
    val ChromeBlur = 16.dp
    /** v0.7.2: the composer's frosted glass (stronger than the header edge). */
    val FrostBlur = 26.dp
    /** v0.11: the heavy frost behind the search morph (constant radius; only its alpha animates). */
    val SearchBlur = 30.dp
    /** v0.7.2: list content fades over this height above the composer top. */
    val BottomFade = 36.dp
    /** …down to this opacity behind the composer (rows still hinted through the frost). */
    const val BottomFadeFloor = 0.25f
    /** Backdrop behind the floating task panel: fixed radius, faded in via layer alpha. */
    val OverlayBlur = 16.dp
    const val OverlayDim = 0.18f
    const val OverlayDimDark = 0.40f
    val ExpandBlur = 16.dp
    val FocusBlur = 24.dp
    const val FocusDim = 0.30f
    const val ExpandDim = 0.16f
    const val LiftScale = 1.03f
    const val ExpandLiftScale = 1.015f
    val EnterOffset = 12.dp
    const val StackScale = 0.92f

    /** New task entrance (6.4). */
    val NewTaskOffset = 24.dp
    const val NewTaskScale = 0.94f
}

// ---- Reduced motion ----------------------------------------------------------

/**
 * true when animations are off system-wide, or the in-app "Reduce motion" toggle is on.
 *
 * Contract: this only selects *variants* (crossfades instead of slides/scales, Quick instead of
 * Pop). It must never gate state changes, callbacks or data writes. Compose already applies the
 * system animator scale to every animation (at 0 they jump to their end value on the next frame),
 * so nothing here re-implements or blocks on that.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Current system "Animator duration scale" (1 by default, 0 = Remove animations). */
val LocalAnimatorScale = staticCompositionLocalOf { 1f }

private fun animatorScale(c: Context): Float =
    Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/** Observes Settings.Global.ANIMATOR_DURATION_SCALE. */
@Composable
fun rememberAnimatorDurationScale(): Float {
    val context = LocalContext.current
    var scale by remember(context) { mutableStateOf(animatorScale(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = animatorScale(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return scale
}

/** true when the system animator scale is 0 ("Remove animations"). */
@Composable
fun rememberSystemAnimationsOff(): Boolean = rememberAnimatorDurationScale() == 0f

/** Wrap the app: `ProvideMotion(userPrefReduceMotion) { App() }`. */
@Composable
fun ProvideMotion(userPrefReduceMotion: Boolean, content: @Composable () -> Unit) {
    val scale = rememberAnimatorDurationScale()
    val reduced = userPrefReduceMotion || scale == 0f
    CompositionLocalProvider(
        LocalReducedMotion provides reduced,
        LocalAnimatorScale provides scale,
        content = content,
    )
}
