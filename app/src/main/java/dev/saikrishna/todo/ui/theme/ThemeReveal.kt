package dev.saikrishna.todo.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * v0.9.1 theme switch without recreating the Activity: a circular reveal.
 *
 * On a switch we snapshot exactly what is on screen (PixelCopy of the window; including a reveal
 * still in flight; the recorded [GraphicsLayer] is the fallback), switch the theme underneath
 * at once ([overrideDark]) and draw the OLD snapshot on top with a hole punched by an antialiased
 * circle (DstOut) growing from the button's centre to the farthest corner, ease-out over
 * [DurationMs]. Frame 0 = the snapshot = the old theme; the last frame has no overlay = the new
 * theme. A second tap mid-reveal snapshots the current mixed frame and reveals the other theme
 * from the button again (retarget, never queued). Reduced motion: the snapshot fades out over
 * 200 ms. The system-bar icon style flips ([barsDark]) once the circle has covered the whole
 * status bar (the farther top corner), or at half of the crossfade.
 */
@Stable
class ThemeReveal {
    /** The theme shown now while the stored setting catches up (cleared once it matches). */
    var overrideDark by mutableStateOf<Boolean?>(null)
    /** True from a tap until the snapshot is up: the old theme is held regardless of the setting. */
    var holding by mutableStateOf(false)
        private set
    /** Status/navigation bar icon style during a reveal; null = follow the theme. */
    var barsDark by mutableStateOf<Boolean?>(null)
        private set
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
    internal var center by mutableStateOf(Offset.Zero)
    internal val radius = Animatable(0f)
    internal val fade = Animatable(1f)
    internal var reduced = false
    internal var layer: GraphicsLayer? = null
    internal var view: android.view.View? = null
    internal var scope: CoroutineScope? = null
    internal var size = IntSize.Zero
    private var job: Job? = null

    /** Current hole radius in px and its centre (tests). */
    val radiusPx: Float get() = radius.value
    val centerPx: Offset get() = center
    /** True while a reveal is drawing (tests). */
    val running: Boolean get() = snapshot != null
    /** Reveal progress 0..1 (tests). */
    val progress: Float get() = if (reduced) 1f - fade.value else radius.value / maxRadius().coerceAtLeast(1f)

    internal fun maxRadius(): Float {
        val w = size.width.toFloat(); val h = size.height.toFloat()
        return maxOf(hypot(center.x, center.y), hypot(w - center.x, center.y), hypot(center.x, h - center.y), hypot(w - center.x, h - center.y)) + 2f // + 2 px: the antialiased edge clears the far corner pixel
    }

    /**
     * Switch from [fromDark] to [toDark] revealed from [origin] (root px). [persist] stores the
     * choice (DataStore); the theme switches at once regardless of how long that takes.
     */
    fun switch(fromDark: Boolean, toDark: Boolean, origin: Offset, persist: () -> Unit) {
        val scope = scope
        if (scope == null || size == IntSize.Zero) { overrideDark = toDark; persist(); return }
        job?.cancel()
        val barsBefore = barsDark ?: fromDark
        // Hold the old theme on screen until the snapshot exists, and store the choice right away
        // (the hold keeps the stored value from switching the theme early).
        overrideDark = fromDark
        holding = true
        barsDark = barsBefore
        persist()
        job = scope.launch {
            val shot = capture()
            if (shot == null) { overrideDark = toDark; holding = false; barsDark = null; snapshot = null; return@launch }
            // One state batch: the overlay covers everything from the very frame the theme changes.
            snapshot = shot
            center = origin
            radius.snapTo(0f)
            fade.snapTo(1f)
            overrideDark = toDark
            holding = false
            if (reduced) {
                fade.animateTo(0f, tween(200)) { if (value <= 0.5f) barsDark = toDark }
            } else {
                val w = size.width.toFloat()
                val statusCovered = maxOf(hypot(center.x, center.y), hypot(w - center.x, center.y))
                radius.animateTo(maxRadius(), tween(DurationMs, easing = RevealEasing)) {
                    if (value >= statusCovered) barsDark = toDark
                }
            }
            snapshot = null
            barsDark = null
        }
    }

    /**
     * Exactly what is on screen now: a PixelCopy of the window region under the host (the real
     * composited frame, blur included, and a reveal in flight). Falls back to re-rendering the
     * recorded layer if PixelCopy fails.
     */
    private suspend fun capture(): ImageBitmap? {
        val v = view
        if (v != null && v.width > 0 && v.height > 0) {
            val window = generateSequence(v.context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<android.app.Activity>().firstOrNull()?.window
            if (window != null) {
                val loc = IntArray(2); v.getLocationInWindow(loc)
                val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
                val ok = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    android.view.PixelCopy.request(
                        window, android.graphics.Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height), bmp,
                        { r -> if (cont.isActive) cont.resumeWith(Result.success(r == android.view.PixelCopy.SUCCESS)) },
                        android.os.Handler(android.os.Looper.getMainLooper()),
                    )
                }
                if (ok) return bmp.asImageBitmap()
            }
        }
        return runCatching { layer?.toImageBitmap() }.getOrNull()
    }

    companion object {
        const val DurationMs = 500
        /** Ease-out (decelerating, no overshoot): fast start from the button, gentle landing. */
        val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }
}

val LocalThemeReveal = staticCompositionLocalOf<ThemeReveal?> { null }

/** Draws [content] through the reveal's layer and paints the old-theme snapshot on top while revealing. */
@Composable
fun ThemeRevealHost(reveal: ThemeReveal, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    reveal.layer = layer
    reveal.view = androidx.compose.ui.platform.LocalView.current
    reveal.scope = rememberCoroutineScope()
    reveal.reduced = LocalReducedMotion.current
    val paint = androidx.compose.runtime.remember { Paint() }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { reveal.size = it }
            .drawWithContent {
                layer.record {
                    this@drawWithContent.drawContent()
                    drawOverlay(reveal, paint)
                }
                drawLayer(layer)
            },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalThemeReveal provides reveal, content = content)
    }
}

private fun DrawScope.drawOverlay(reveal: ThemeReveal, paint: Paint) {
    val shot = reveal.snapshot ?: return
    if (reveal.reduced) {
        drawImage(shot, alpha = reveal.fade.value)
        return
    }
    val r = reveal.radius.value
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        drawImage(shot)
        // Antialiased hole: the new theme (drawn live underneath) shows through.
        if (r > 0f) drawCircle(Color.Black, r, reveal.center, blendMode = BlendMode.DstOut)
        canvas.restore()
    }
}
