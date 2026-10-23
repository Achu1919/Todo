package dev.saikrishna.todo.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * v0.7.3 scroll-direction state machine for the bottom composer (pure, unit-tested).
 *
 * - Collapse after [collapseAfter] px of ACCUMULATED downward scroll (content moving up);
 *   expand after [expandAfter] px of accumulated upward scroll. Direction-committed: the
 *   accumulator resets whenever the direction flips, so jitter never toggles it.
 * - Reaching the top or the bottom of the list (cannot scroll further) expands.
 * - Blocked (field focused / text / voice / chips / undo bar / drag) or a list that cannot scroll
 *   at all: expanded, accumulator cleared.
 */
class CollapseMachine(private val collapseAfter: Float, private val expandAfter: Float) {
    var collapsed = false
        private set
    private var acc = 0f

    /**
     * One scroll step. [down] > 0 = the user scrolled down (content moved up), in px, AFTER the
     * list consumed it. Returns the new collapsed state.
     */
    fun onScroll(down: Float, atTop: Boolean, atBottom: Boolean, blocked: Boolean, scrollable: Boolean): Boolean {
        if (blocked || !scrollable) return reset()
        if (atTop || atBottom) return reset()
        if (down == 0f) return collapsed
        if (down > 0f) {
            if (acc < 0f) acc = 0f
            acc += down
            if (!collapsed && acc >= collapseAfter) { collapsed = true; acc = 0f }
        } else {
            if (acc > 0f) acc = 0f
            acc += down
            if (collapsed && -acc >= expandAfter) { collapsed = false; acc = 0f }
        }
        return collapsed
    }

    /** Programmatic collapse (tests). */
    fun collapse(): Boolean { collapsed = true; acc = 0f; return true }

    /** Explicit expand (page switch, back from a pushed screen, tap, blocked). */
    fun expand(): Boolean = reset()

    private fun reset(): Boolean { collapsed = false; acc = 0f; return false }
}

/**
 * The composer's collapsed/expanded state, shared by HomeScreen (scroll, page, navigation,
 * undo, drag) and the Composer (focus, text, voice, chips; it owns the animation).
 * [progress]: 0 = full composer, 1 = centred pill; read only in layout/draw.
 */
@Stable
class ComposerChrome(collapseAfterPx: Float, expandAfterPx: Float) {
    private val machine = CollapseMachine(collapseAfterPx, expandAfterPx)
    /** Target state. */
    var collapsed by mutableStateOf(false)
        private set
    val progress = Animatable(0f)
    /** Reduced motion: fade-through alpha of the one surface. */
    val fade = Animatable(1f)
    /** Set by the Composer: focused, has text, listening or showing chips. */
    var composerBusy by mutableStateOf(false)
    /** Set by HomeScreen: undo bar visible or a drag in progress. */
    var externalBlock by mutableStateOf(false)
    /** v0.7.4: the field has focus (set by the Composer). */
    var focused = false
    /** v0.7.4: drop focus + hide the IME (set by the Composer; no-op when not focused). */
    var dismiss: () -> Unit = {}
    /** Times [dismiss] actually dismissed (tests: "IME hide requested"). */
    var dismissCount = 0
    /** The whole composer (chips + bar) in root coordinates: taps inside it are not "outside". */
    var composerBounds: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero

    /** v0.13: bump to expand + focus the field (Calendar's "add on this day"). */
    var focusTick by mutableStateOf(0)
        private set
    fun focusRequest() { focusTick++ }

    /** The current page's list. */
    var list: LazyListState? = null

    val blocked: Boolean get() = composerBusy || externalBlock

    fun expand() { collapsed = machine.expand() }

    /** Test hook / programmatic. */
    fun collapseForTest() { if (!blocked) collapsed = machine.collapse() }

    private fun step(down: Float) {
        val l = list ?: return
        val atTop = !l.canScrollBackward
        val atBottom = !l.canScrollForward
        collapsed = machine.onScroll(down, atTop, atBottom, blocked, scrollable = l.canScrollBackward || l.canScrollForward)
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // v0.7.4: starting to scroll the list ends editing.
            if (source == NestedScrollSource.UserInput && focused && available.y != 0f) dismiss()
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // consumed.y < 0: the content moved up = the user scrolled down.
            step(-consumed.y)
            return Offset.Zero
        }
    }
}
