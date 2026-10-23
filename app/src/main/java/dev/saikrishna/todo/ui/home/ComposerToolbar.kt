package dev.saikrishna.todo.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.ComposerDraft
import dev.saikrishna.todo.domain.ComposerDraft.Tool
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.RepeatRule
import dev.saikrishna.todo.domain.label
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.SpringToken
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.panel.DayToggles
import dev.saikrishna.todo.ui.panel.Stepper
import dev.saikrishna.todo.ui.panel.TimePickDialog
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** v0.13 add-task toolbar motion: the row slides up with the keyboard; popovers spring from it. */
object ToolbarSpec {
    val RowHeight = 40.dp
    val Slide = SpringToken(0.36f, 0.88f)
    val Pop = SpringToken(0.32f, 0.78f)
}

fun Tool.icon(): ImageVector = when (this) {
    Tool.Date -> AppIcons.Calendar
    Tool.Time -> AppIcons.Clock
    Tool.Repeat -> AppIcons.Repeat
    Tool.List -> AppIcons.Lists
    Tool.Priority -> AppIcons.Flag
    Tool.Reminder -> AppIcons.Bell
    Tool.Notes -> AppIcons.Note
}

/** The tool buttons, above the keyboard. A filled field tints its button. */
@Composable
fun ComposerToolRow(
    visible: Boolean,
    draft: ComposerDraft.Draft,
    active: Tool?,
    onTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    inset: Dp = 24.dp,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(Tweens.reduced()) else fadeIn(Tweens.fadeIn(0)) +
            slideInVertically(ToolbarSpec.Slide.intOffset) { it } + expandVertically(ToolbarSpec.Slide.intSize, expandFrom = Alignment.Top),
        exit = if (reduced) fadeOut(Tweens.fadeOutFast()) else fadeOut(Tweens.fadeOutFast()) +
            slideOutVertically(ToolbarSpec.Slide.intOffset) { it } + shrinkVertically(ToolbarSpec.Slide.intSize, shrinkTowards = Alignment.Top),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("composer-toolbar")
                .horizontalScroll(rememberScrollState())
                .padding(start = inset, end = inset, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (t in Tool.entries) ToolButton(t, filled = draft.chip(t) != null, active = active == t) { onTool(t) }
        }
    }
}

@Composable
private fun ToolButton(tool: Tool, filled: Boolean, active: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val tint = if (filled || active) c.accent else c.ink
    Row(
        Modifier
            .height(ToolbarSpec.RowHeight - 4.dp)
            .then(if (active) Modifier.clip(Shapes.full).background(c.accent.copy(alpha = if (c.isDark) 0.26f else 0.16f)) else Modifier.glassSurface(Shapes.full, GlassLevel.Low))
            .clip(Shapes.full)
            .clickable(role = Role.Button, onClickLabel = tool.label, onClick = onClick)
            .semantics { selected = active; contentDescription = tool.label + if (filled) ", set" else "" }
            .testTag("tool-${tool.name}")
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tool.icon(), null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(tool.label, style = AppTheme.type.meta, color = tint, maxLines = 1)
        if (filled) {
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(5.dp).clip(Shapes.full).background(c.accent))
        }
    }
}

/**
 * Chosen values as chips: tap opens that tool's picker, x clears it (a typed one goes back into the
 * title). Tags (not a toolbar tool) keep the parser chip behaviour.
 */
@Composable
fun DraftChips(
    draft: ComposerDraft.Draft,
    tagTokens: List<QuickParse.Token>,
    lists: List<TaskList>,
    onOpen: (Tool) -> Unit,
    onClear: (ComposerDraft.Chip) -> Unit,
    onRemoveTag: (QuickParse.Token) -> Unit,
    inset: Dp = 28.dp,
) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val any = draft.chips.isNotEmpty() || tagTokens.isNotEmpty()
    AnimatedVisibility(
        visible = any,
        enter = if (reduced) fadeIn(Tweens.reduced()) else fadeIn(Tweens.fadeIn(0)) + expandVertically(Springs.Smooth.intSize, expandFrom = Alignment.Bottom),
        exit = if (reduced) fadeOut(Tweens.fadeOutFast()) else fadeOut(Tweens.fadeOutFast()) + shrinkVertically(Springs.Smooth.intSize, shrinkTowards = Alignment.Bottom),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("draft-chips")
                .horizontalScroll(rememberScrollState())
                .padding(start = inset, end = inset, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (chip in draft.chips) key(chip.tool) {
                val dot = if (chip.tool == Tool.List) lists.firstOrNull { it.id == draft.listId }?.let { c.listColor(it.color) } else null
                ValueChip(chip, dot, onOpen = { onOpen(chip.tool) }, onClear = if (chip.source == ComposerDraft.Source.Auto) null else ({ onClear(chip) }))
            }
            for (t in tagTokens) key(t.key) {
                ValueChip(
                    ComposerDraft.Chip(Tool.Notes, t.label, ComposerDraft.Source.Typed, t.key),
                    dot = null, iconOverride = AppIcons.Hash,
                    onOpen = { onRemoveTag(t) }, onClear = { onRemoveTag(t) },
                )
            }
        }
    }
}

@Composable
private fun ValueChip(
    chip: ComposerDraft.Chip,
    dot: androidx.compose.ui.graphics.Color?,
    onOpen: () -> Unit,
    onClear: (() -> Unit)?,
    iconOverride: ImageVector? = null,
) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, if (reduced) Tweens.reduced() else Springs.Quick.float) }
    val picked = chip.source == ComposerDraft.Source.Picked
    val tint = when {
        chip.tool == Tool.Priority && chip.label == "High" -> c.danger
        picked -> c.accent
        else -> c.ink
    }
    Row(
        Modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                if (!reduced) { val s = 0.9f + 0.1f * appear.value; scaleX = s; scaleY = s }
            }
            .height(30.dp)
            .glassSurface(Shapes.full, GlassLevel.Low)
            .clip(Shapes.full)
            .clickable(role = Role.Button, onClickLabel = "Change ${chip.tool.label.lowercase()}", onClick = onOpen)
            .testTag("chip-${chip.tool.name}")
            .padding(start = 10.dp, end = if (onClear != null) 2.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) ListDot(dot, 8.dp) else Icon(iconOverride ?: chip.tool.icon(), null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(chip.label.removePrefix("#"), style = AppTheme.type.meta, color = tint, maxLines = 1)
        if (onClear != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(Shapes.full)
                    .clickable(role = Role.Button, onClickLabel = "Clear ${chip.tool.label.lowercase()}", onClick = onClear)
                    .testTag("chip-clear-${chip.tool.name}"),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, "Clear ${chip.label}", tint = c.inkTertiary, modifier = Modifier.size(12.dp)) }
        }
    }
}

/** The picker for [tool], a glass popover that springs up from the toolbar. */
@Composable
fun ToolPopover(
    tool: Tool?,
    draft: ComposerDraft.Draft,
    picks: ComposerDraft.Picks,
    today: LocalDate,
    lists: List<TaskList>,
    onPicks: (ComposerDraft.Picks) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = tool,
        modifier = modifier,
        transitionSpec = {
            val origin = TransformOrigin(0.5f, 1f)
            if (reduced) fadeIn(Tweens.reduced()) togetherWith fadeOut(Tweens.fadeOutFast())
            else (fadeIn(Tweens.fadeIn(0)) + scaleIn(ToolbarSpec.Pop.float, initialScale = 0.86f, transformOrigin = origin)) togetherWith
                (fadeOut(Tweens.fadeOutFast()) + scaleOut(Springs.Quick.float, targetScale = 0.92f, transformOrigin = origin)) using
                SizeTransform(clip = false) { _, _ -> Springs.Smooth.intSize }
        },
        label = "tool popover",
    ) { t ->
        if (t == null) { Box(Modifier.fillMaxWidth()); return@AnimatedContent }
        val c = AppTheme.colors
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                .glassSurface(Shapes.lg, GlassLevel.Mid, c.glassSolid)
                .clip(Shapes.lg)
                .testTag("popover-${t.name}")
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(t.icon(), null, tint = c.inkSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.label, style = AppTheme.type.label, color = c.ink, modifier = Modifier.weight(1f))
                Text(
                    "Done", style = AppTheme.type.label, color = c.accent,
                    modifier = Modifier.clip(Shapes.full).clickable(role = Role.Button, onClick = onClose).testTag("popover-done").padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            when (t) {
                Tool.Date -> DatePicker(draft.dueDay, today) { onPicks(picks.copy(day = it)); onClose() }
                Tool.Time -> TimePicker(draft.dueMinute) { onPicks(picks.copy(minute = it)); onClose() }
                Tool.Repeat -> RepeatPicker(draft, today) { onPicks(picks.copy(repeat = it)) }
                Tool.List -> ListPicker(lists, draft.listId) { onPicks(picks.copy(listId = it)); onClose() }
                Tool.Priority -> PriorityPicker(draft.priority) { onPicks(picks.copy(priority = it)); onClose() }
                Tool.Reminder -> ReminderPicker(picks.remindBefore) { onPicks(picks.copy(remindBefore = it)) }
                Tool.Notes -> NotesPicker(picks.notes.orEmpty()) { onPicks(picks.copy(notes = it.ifBlank { null })) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Presets(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun Preset(label: String, on: Boolean, tag: String? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    Text(
        label,
        style = AppTheme.type.meta,
        color = if (on) c.onInk else c.ink,
        modifier = Modifier
            .clip(Shapes.full)
            .then(if (on) Modifier.background(c.ink) else Modifier.border(1.dp, c.inkHairline, Shapes.full))
            .clickable(role = Role.Button) { haptics.tick(); onClick() }
            .semantics { selected = on }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun DatePicker(current: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = AppTheme.colors
    val locale = Locale.getDefault()
    Presets {
        for ((label, d) in ComposerDraft.datePresets(today)) Preset(label, current == d, "preset-$label") { onPick(d) }
    }
    Spacer(Modifier.height(10.dp))
    var month by remember { mutableStateOf(YearMonth.from(current ?: today)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(month.month.getDisplayName(TextStyle.FULL, locale) + " ${month.year}", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.weight(1f))
        MiniArrow(true) { month = month.minusMonths(1) }
        MiniArrow(false) { month = month.plusMonths(1) }
    }
    val grid = remember(month) { dev.saikrishna.todo.domain.CalendarData.monthGrid(month) }
    Row(Modifier.fillMaxWidth()) {
        for (d in grid.take(7)) Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = AppTheme.type.meta, color = c.inkTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    }
    for (r in 0 until grid.size / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (d in grid.subList(r * 7, r * 7 + 7)) {
                val inMonth = YearMonth.from(d) == month
                val on = d == current
                val past = d.isBefore(today)
                Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                    if (inMonth) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(Shapes.full)
                                .then(if (on) Modifier.background(c.ink) else if (d == today) Modifier.border(1.dp, c.accent, Shapes.full) else Modifier)
                                .clickable(enabled = !past, role = Role.Button) { onPick(d) }
                                .testTag("pick-day-$d"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d.dayOfMonth.toString(), style = AppTheme.type.meta,
                                color = when { on -> c.onInk; past -> c.inkTertiary; d == today -> c.accent; else -> c.ink },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniArrow(back: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.size(32.dp).clip(Shapes.full).clickable(role = Role.Button, onClickLabel = if (back) "Previous month" else "Next month", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.ChevronRight, null, tint = c.inkSecondary, modifier = Modifier.size(14.dp).graphicsLayer { if (back) rotationZ = 180f })
    }
}

@Composable
private fun TimePicker(current: Int?, onPick: (Int) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    Presets {
        for ((m, label) in ComposerDraft.TimePresets) Preset("$label · ${QuickParse.timeLabel(m)}".let { if (label == "6 pm") label else it }, current == m, "preset-$label") { onPick(m) }
        Preset(if (current != null && ComposerDraft.TimePresets.none { it.first == current }) QuickParse.timeLabel(current) else "Custom…", false, "preset-custom-time") { custom = true }
    }
    if (custom) TimePickDialog(initialMinute = current ?: (9 * 60), onSet = { custom = false; onPick(it) }, onDismiss = { custom = false })
}

@Composable
private fun RepeatPicker(draft: ComposerDraft.Draft, today: LocalDate, onPick: (RepeatRule) -> Unit) {
    val c = AppTheme.colors
    val anchor = draft.dueDay ?: today
    val current = draft.repeat
    val presets = listOf(
        RepeatRule.Daily(),
        RepeatRule.Weekdays,
        RepeatRule.Weekly(setOf(anchor.dayOfWeek)),
        RepeatRule.Monthly(anchor.dayOfMonth),
        RepeatRule.Yearly(anchor.monthValue, anchor.dayOfMonth),
    )
    Presets { for (r in presets) Preset(r.label(), current == r, "preset-repeat-${presets.indexOf(r)}") { onPick(r) } }
    Spacer(Modifier.height(12.dp))
    // Custom: every N days, or chosen weekdays.
    val every = (current as? RepeatRule.Daily)?.every ?: 2
    Stepper(prefix = "Every", value = every, suffix = if (every == 1) "day" else "days", range = 1..90) { onPick(RepeatRule.Daily(it)) }
    Spacer(Modifier.height(10.dp))
    Text("On days", style = AppTheme.type.meta, color = c.inkSecondary)
    Spacer(Modifier.height(6.dp))
    val days = (current as? RepeatRule.Weekly)?.days ?: emptySet<DayOfWeek>()
    DayToggles(selected = days) { set -> if (set.isNotEmpty()) onPick(RepeatRule.Weekly(set)) }
}

@Composable
private fun ListPicker(lists: List<TaskList>, current: Long?, onPick: (Long) -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    Column {
        for (l in lists) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(Shapes.md)
                    .clickable(role = Role.Button) { haptics.tick(); onPick(l.id) }
                    .semantics { selected = l.id == current }
                    .testTag("pick-list-${l.id}")
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ListDot(c.listColor(l.color), 9.dp)
                Spacer(Modifier.width(10.dp))
                Text(l.name, style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                if (l.id == current) Icon(AppIcons.Check, null, tint = c.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PriorityPicker(current: Priority, onPick: (Priority) -> Unit) {
    Presets {
        for (p in listOf(Priority.High, Priority.Medium, Priority.Low, Priority.None)) Preset(if (p == Priority.None) "No priority" else p.name, current == p, "preset-${p.name}") { onPick(p) }
    }
}

@Composable
private fun ReminderPicker(current: Int?, onPick: (Int) -> Unit) {
    Presets { for ((m, label) in ComposerDraft.ReminderLeads) Preset(label, current == m, "preset-remind-$m") { onPick(m) } }
    Spacer(Modifier.height(12.dp))
    val hours = ((current ?: 120) / 60).coerceIn(1, 72)
    Stepper(prefix = "", value = hours, suffix = "h before", range = 1..72) { onPick(it * 60) }
}

@Composable
private fun NotesPicker(current: String, onChange: (String) -> Unit) {
    val c = AppTheme.colors
    var text by remember { mutableStateOf(current) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(Shapes.md)
            .border(1.dp, c.inkHairline, Shapes.md)
            .padding(10.dp),
    ) {
        if (text.isEmpty()) Text("Add notes", style = AppTheme.type.body, color = c.inkTertiary)
        BasicTextField(
            value = text,
            onValueChange = { text = it; onChange(it) },
            textStyle = AppTheme.type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().testTag("notes-field"),
        )
    }
}
