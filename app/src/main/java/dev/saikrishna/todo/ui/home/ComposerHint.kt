package dev.saikrishna.todo.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import dev.saikrishna.todo.ui.motion.Durations
import dev.saikrishna.todo.ui.motion.Easings
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import kotlinx.coroutines.delay

/**
 * v0.10 rotating composer placeholder: teaches the quick-add syntax by example. Every phrase is
 * parsed by [dev.saikrishna.todo.domain.QuickParse] exactly as shown (ComposerHintTest), so the
 * hint never promises syntax we don't support.
 */
object ComposerHints {
    const val Default = "Add a task"
    val Examples = listOf(
        // v0.12.1: short enough for the pill at 412 dp with font scale 1.3 (≤ 19 characters);
        // a phrase that still does not fit the measured width is skipped, never ellipsized.
        "Call mom 6pm",
        "Rent monthly on 1st",
        "Milk #personal",
        "Deck Friday !high",
        "Water plants daily",
    )
    /** Index 0 is [Default]; 1..n are [Examples]. */
    val All = listOf(Default) + Examples
    const val IntervalMs = 3_500L
    /**
     * Unit tests that compare frames over several seconds run with the hint held still
     * (`todo.static.hints`, set in app/build.gradle.kts); ComposerHintTest turns it back on.
     */
    var rotate: Boolean = System.getProperty("todo.static.hints") != "true"
    val Travel = 12.dp
}

/** Which phrase is showing (tests only; accessibility services ignore custom keys). */
val HintIndexKey = SemanticsPropertyKey<Int>("HintIndex")

/**
 * The placeholder text. Cycles only while [active] (empty, unfocused, expanded, app visible);
 * pausing keeps the current phrase. TalkBack always reads the static "Add a task".
 */
@Composable
fun RotatingHint(active: Boolean, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val available = remember { intArrayOf(0) } // the hint's max width, from the last layout pass
    val currentStyle by rememberUpdatedState(style)
    fun fits(i: Int): Boolean {
        val w = available[0]
        if (i == 0 || w <= 0) return true
        return measurer.measure(ComposerHints.All[i], currentStyle, maxLines = 1, softWrap = false).size.width <= w
    }
    LaunchedEffect(active) {
        if (!active || !ComposerHints.rotate) return@LaunchedEffect
        while (true) {
            delay(ComposerHints.IntervalMs)
            var next = (index + 1) % ComposerHints.All.size
            while (!fits(next)) next = (next + 1) % ComposerHints.All.size
            index = next
        }
    }
    val travel = with(LocalDensity.current) { ComposerHints.Travel.roundToPx() }
    AnimatedContent(
        targetState = index,
        modifier = modifier
            .layout { m, cons ->
                if (cons.hasBoundedWidth) available[0] = cons.maxWidth
                val p = m.measure(cons)
                layout(p.width, p.height) { p.place(0, 0) }
            }
            .testTag("composer-hint")
            .clearAndSetSemantics {
                text = AnnotatedString(ComposerHints.Default)
                this[HintIndexKey] = index
            },
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(Durations.ReducedCrossfade, easing = Easings.Standard)) togetherWith
                    fadeOut(tween(Durations.ReducedCrossfade, easing = Easings.Standard))
            } else {
                // New phrase rises in from 12 dp below while the old one rises away.
                (slideInVertically(Springs.Quick.intOffset) { travel } + fadeIn(Springs.Quick.float)) togetherWith
                    (slideOutVertically(Springs.Quick.intOffset) { -travel } + fadeOut(Springs.Quick.float))
            } using SizeTransform(clip = false)
        },
        label = "composer hint",
    ) { i ->
        Text(ComposerHints.All[i], style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
