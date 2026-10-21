package dev.saikrishna.todo.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.ui.motion.Durations
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing

/**
 * Glass task row (radius 20, bevel) with the completion checkbox (spec 6.5).
 * Completing: title → ink.tertiary and the secondary line → 50 % alpha on `select` (240 ms);
 * strike draws from 60 ms over 280 ms. Un-completing reverses in 120 ms.
 * All animated values are read in the draw phase (ColorProducer / graphicsLayer).
 */
@Composable
fun TaskRow(
    task: Task,
    meta: RowMeta,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** v0.7: tapping a tag pill (opens Search filtered by it). */
    onTag: ((String) -> Unit)? = null,
) {
    val c = AppTheme.colors
    val checked = task.done
    val strike = remember { Animatable(if (checked) 1f else 0f) }
    val metaAlpha = remember { Animatable(if (checked) 0.5f else 1f) }
    LaunchedEffect(checked) {
        if (checked) {
            kotlinx.coroutines.coroutineScope {
                launch { metaAlpha.animateTo(0.5f, Tweens.select()) }
                strike.animateTo(1f, Tweens.strike())
            }
        } else {
            kotlinx.coroutines.coroutineScope {
                launch { metaAlpha.animateTo(1f, tween(Durations.UncheckReverse)) }
                strike.animateTo(0f, tween(Durations.UncheckReverse))
            }
        }
    }
    val titleColor by animateColorAsState(
        if (checked) c.inkTertiary else c.ink, Tweens.select(), label = "title colour",
    )

    Row(
        modifier
            .fillMaxWidth()
            // Shared glass tokens, no live blur on cards: translucent fill + gradient rim + soft shadow.
            .glassSurface(Shapes.row, GlassLevel.Low, c.glassCard)
            .clip(Shapes.row)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = "Open details", onClick = onClick)
                else Modifier,
            )
            // 4 dp start + the checkbox's 12 dp inner padding = the 16 dp row inset.
            .padding(start = 4.dp, end = Spacing.rowH),
        verticalAlignment = Alignment.Top,
    ) {
        CheckCircle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            label = task.title,
            enabled = enabled,
            modifier = Modifier.padding(top = 1.dp), // centre of the 24 dp mark on the first text line
        )
        Column(Modifier.weight(1f).padding(start = 4.dp, top = Spacing.rowV, bottom = Spacing.rowV)) {
            StrikeText(
                text = task.title,
                progress = { strike.value },
                style = AppTheme.type.title,
                color = { titleColor },
                strikeColor = { c.inkTertiary },
            )
            if (!meta.isEmpty) {
                MetaLine(meta, Modifier.padding(top = 2.dp).graphicsLayer { alpha = metaAlpha.value }, onTag = onTag)
            }
        }
    }
}

@Composable
internal fun MetaLine(meta: RowMeta, modifier: Modifier = Modifier, onTag: ((String) -> Unit)? = null) {
    val c = AppTheme.colors
    val style = AppTheme.type.meta
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        var needsSep = false
        if (meta.listName != null) {
            ListDot(c.listColor(meta.listColor))
            Spacer(Modifier.width(5.dp))
            BasicText(meta.listName, style = style, color = { c.inkSecondary }, maxLines = 1)
            needsSep = true
        }
        if (meta.dueLabel != null) {
            if (needsSep) BasicText(" · ", style = style, color = { c.inkTertiary })
            if (meta.overdue) {
                Box(Modifier.size(6.dp).clip(Shapes.full).background(c.warning))
                Spacer(Modifier.width(6.dp))
            }
            BasicText(meta.dueLabel, style = style, color = { if (meta.overdue) c.warning else c.inkSecondary })
            needsSep = true
        }
        if (meta.priority != Priority.None) {
            if (needsSep) BasicText(" · ", style = style, color = { c.inkTertiary })
            val high = meta.priority == Priority.High
            BasicText(if (high) "High" else "Low", style = style, color = { if (high) c.danger else c.inkSecondary })
            needsSep = true
        }
        if (meta.hasNotes) {
            if (needsSep) BasicText(" · ", style = style, color = { c.inkTertiary })
            BasicText("Notes", style = style, color = { c.inkSecondary })
            needsSep = true
        }
        if (meta.repeats) {
            if (needsSep) Spacer(Modifier.width(6.dp))
            // Small repeat glyph (v0.6): ink 50 %, sized to the meta x-height.
            androidx.compose.material3.Icon(
                dev.saikrishna.todo.ui.icons.AppIcons.Repeat, contentDescription = "Repeats",
                tint = c.inkSecondary, modifier = Modifier.size(13.dp),
            )
            needsSep = true
        }
        if (meta.progress != null) {
            if (needsSep) Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                dev.saikrishna.todo.ui.icons.AppIcons.Check, contentDescription = null,
                tint = c.inkSecondary, modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(3.dp))
            BasicText(
                meta.progress, style = style, color = { c.inkSecondary },
                modifier = Modifier.semantics { contentDescription = "Subtasks ${meta.progress.replace("/", " of ")} done" },
            )
            needsSep = true
        }
        if (meta.tags.isNotEmpty()) {
            if (needsSep) Spacer(Modifier.width(8.dp))
            TagPills(meta.tags, onTag)
        }
    }
}

/** Small list colour dot. */
@Composable
fun ListDot(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(Modifier.size(size).clip(Shapes.full).background(color))
}

/** Up to 2 tag pills + "+N" (v0.7). Tapping one calls [onTag]. */
@Composable
fun TagPills(tags: List<String>, onTag: ((String) -> Unit)?, max: Int = 2) {
    val c = AppTheme.colors
    val style = AppTheme.type.meta
    val shown = tags.take(max)
    // Pills that do not fit the rest of the meta line fold into the "+N" count instead of being
    // clipped mid-word: measure every pill and every possible count, keep the most that fit.
    androidx.compose.ui.layout.Layout(
        content = {
            for (t in shown) {
                BasicText(
                    "#$t", style = style, color = { c.inkSecondary }, maxLines = 1, softWrap = false,
                    modifier = Modifier
                        .clip(Shapes.full)
                        .background(c.inkFill)
                        .then(if (onTag != null) Modifier.clickable(role = Role.Button, onClickLabel = "Search #$t") { onTag(t) } else Modifier)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            for (k in 0..shown.size) BasicText("+${tags.size - k}", style = style, color = { c.inkTertiary }, maxLines = 1, softWrap = false)
        },
    ) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
        val pills = measurables.take(shown.size).map { it.measure(loose) }
        val counts = measurables.drop(shown.size).map { it.measure(loose) }
        val limit = constraints.maxWidth
        fun width(k: Int): Int {
            var w = 0
            for (i in 0 until k) w += pills[i].width + (if (i > 0) gap else 0)
            if (tags.size > k) w += counts[k].width + (if (k > 0) gap else 0)
            return w
        }
        val k = (shown.size downTo 0).firstOrNull { width(it) <= limit } ?: -1
        val placed = if (k < 0) emptyList() else pills.take(k) + listOfNotNull(counts.getOrNull(k)?.takeIf { tags.size > k })
        val h = placed.maxOfOrNull { it.height } ?: 0
        layout(placed.sumOf { it.width } + gap * (placed.size - 1).coerceAtLeast(0), h) {
            var x = 0
            for (p in placed) { p.place(x, (h - p.height) / 2); x += p.width + gap }
        }
    }
}
