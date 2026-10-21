package dev.saikrishna.todo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.components.glassControl

/** Headline + body that rise in on Soft with fadeInSlow (spec 6.14). */
@Composable
private fun RisingMessage(title: String, body: String?, bodySuccess: Boolean, delayMs: Int, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val rise = with(LocalDensity.current) { 16.dp.roundToPx() }
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = if (reduced) fadeIn(Tweens.reduced())
        else fadeIn(Tweens.fadeInSlow(delay = delayMs)) + slideInVertically(Springs.Soft.intOffset) { rise },
        exit = fadeOut(Tweens.fadeOutFast()),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = AppTheme.type.display, color = c.ink)
            if (body != null) {
                Text(
                    body,
                    style = if (bodySuccess) AppTheme.type.meta else AppTheme.type.body,
                    color = if (bodySuccess) c.success else c.inkSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** "All clear." (spec 6.14.2): 150 ms after the last Today row leaves. */
@Composable
fun AllClear(doneToday: Int, modifier: Modifier = Modifier) = RisingMessage(
    "All clear.",
    when (doneToday) { 0 -> null; 1 -> "1 done today"; else -> "$doneToday done today" },
    bodySuccess = true, delayMs = 150, modifier = modifier,
)

@Composable
fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier) =
    RisingMessage(title, body, bodySuccess = false, delayMs = 0, modifier = modifier)

@Composable
fun DayHeaderRow(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = AppTheme.type.label,
        color = AppTheme.colors.inkSecondary,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
    )
}

@Composable
fun ClearDoneRow(count: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (count == 1) "1 completed" else "$count completed",
            style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            "Clear done",
            style = AppTheme.type.label,
            color = c.danger,
            modifier = Modifier
                .glassControl(Shapes.full)
                .clip(Shapes.full)
                .clickable(role = Role.Button, onClick = onClear)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** Page heading: medium serif title + a quiet caption (the serif-headline-over-glass look). */
@Composable
fun PageHeading(title: String, caption: String?, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    val c = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(start = 4.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Text(title, style = AppTheme.type.display, color = c.ink)
        if (caption != null) {
            Text(caption, style = AppTheme.type.body, color = c.inkSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        extra()
    }
}
