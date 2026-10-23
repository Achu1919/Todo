@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.saikrishna.todo.ui.panel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.repeat
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.RepeatRule
import dev.saikrishna.todo.domain.firstOccurrence
import dev.saikrishna.todo.domain.label
import dev.saikrishna.todo.domain.ordinal
import dev.saikrishna.todo.ui.components.PillChip
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

private val QuickTimes = listOf(9 * 60, 13 * 60, 18 * 60)

/** The day a time lands on when the task has no date: today if still ahead, else tomorrow. */
internal fun dayForTime(minute: Int, now: LocalDateTime): LocalDate {
    val nowMinute = now.hour * 60 + now.minute
    return if (minute > nowMinute) now.toLocalDate() else now.toLocalDate().plusDays(1)
}

/** Time row: No time · 9 am · 1 pm · 6 pm · custom (M3 time picker). */
@Composable
internal fun TimeSection(task: Task, now: LocalDateTime, onSetDueAt: (dueDay: Long?, dueMinute: Int?) -> Unit) {
    val haptics = LocalHaptics.current
    var picking by remember { mutableStateOf(false) }
    val current = task.dueMinute
    fun set(minute: Int?) {
        haptics.tick()
        if (minute == null) onSetDueAt(task.dueDay, null)
        else onSetDueAt(task.dueDay ?: dayForTime(minute, now).toEpochDay(), minute)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillChip("No time", selected = current == null, onClick = { set(null) })
        // A custom time sits up front (visible without scrolling); "Pick…" stays at the end.
        if (current != null && current !in QuickTimes) {
            PillChip(QuickParse.timeLabel(current), selected = true, onClick = { haptics.tick(); picking = true })
        }
        QuickTimes.forEach { m -> PillChip(QuickParse.timeLabel(m), selected = current == m, onClick = { set(m) }) }
        PillChip("Pick…", selected = false, onClick = { haptics.tick(); picking = true })
    }
    if (picking) {
        val c = AppTheme.colors
        val start = current ?: (now.hour + 1).coerceAtMost(23) * 60
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = { picking = false; set(state.hour * 60 + state.minute) }) { Text("Set", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = c.inkSecondary) } },
            text = { TimePicker(state = state) },
        )
    }
}

private enum class RepeatKind(val label: String) { Never("Never"), Daily("Daily"), Weekdays("Weekdays"), Weekly("Weekly"), Monthly("Monthly"), Yearly("Yearly"), After("After done") }

private fun RepeatRule?.kind(): RepeatKind = when (this) {
    null -> RepeatKind.Never
    is RepeatRule.Daily -> RepeatKind.Daily
    RepeatRule.Weekdays -> RepeatKind.Weekdays
    is RepeatRule.Weekly -> RepeatKind.Weekly
    is RepeatRule.Monthly -> RepeatKind.Monthly
    is RepeatRule.Yearly -> RepeatKind.Yearly
    is RepeatRule.AfterCompletion -> RepeatKind.After
}

/**
 * Simple repeat picker: one row of kinds, plus a detail line for the chosen kind
 * (interval stepper; weekday toggles for Weekly; day-of-month stepper for Monthly).
 * Choosing a rule on an undated task also gives it its first date.
 */
@Composable
internal fun RepeatSection(task: Task, today: LocalDate, onSetRepeat: (rule: RepeatRule?, firstDay: Long?) -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val rule = task.repeat
    val anchor = task.dueDay?.let(LocalDate::ofEpochDay) ?: today
    fun apply(r: RepeatRule?) {
        haptics.tick()
        val first = if (r != null && task.dueDay == null) firstOccurrence(r, today).toEpochDay() else null
        onSetRepeat(r, first)
    }
    fun defaultFor(k: RepeatKind): RepeatRule? = when (k) {
        RepeatKind.Never -> null
        RepeatKind.Daily -> RepeatRule.Daily(1)
        RepeatKind.Weekdays -> RepeatRule.Weekdays
        RepeatKind.Weekly -> RepeatRule.Weekly(setOf(anchor.dayOfWeek), 1)
        RepeatKind.Monthly -> RepeatRule.Monthly(anchor.dayOfMonth)
        RepeatKind.Yearly -> RepeatRule.Yearly(anchor.monthValue, anchor.dayOfMonth)
        RepeatKind.After -> RepeatRule.AfterCompletion(7)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RepeatKind.entries.forEach { k ->
            PillChip(k.label, selected = rule.kind() == k, onClick = { if (rule.kind() != k) apply(defaultFor(k)) })
        }
    }
    AnimatedContent(
        targetState = rule.kind(),
        transitionSpec = {
            (if (reduced) fadeIn(Tweens.reduced()) else fadeIn(Tweens.fadeIn(0))) togetherWith fadeOut(Tweens.fadeOutFast()) using
                SizeTransform(clip = false) { _, _ -> Springs.Smooth.intSize }
        },
        label = "repeat detail",
    ) { kind ->
        Column(Modifier.fillMaxWidth().padding(top = if (kind == RepeatKind.Never) 0.dp else 10.dp)) {
            when (val r = rule) {
                is RepeatRule.Daily -> Stepper("Every", r.every, if (r.every == 1) "day" else "days", 1..30) { apply(RepeatRule.Daily(it)) }
                is RepeatRule.Weekly -> {
                    DayToggles(r.days) { days -> if (days.isNotEmpty()) apply(r.copy(days = days)) }
                    Spacer(Modifier.height(8.dp))
                    Stepper("Every", r.every, if (r.every == 1) "week" else "weeks", 1..8) { apply(r.copy(every = it)) }
                }
                is RepeatRule.Monthly -> {
                    Stepper("On the", r.day, null, 1..31, valueText = { ordinal(it) }) { apply(r.copy(day = it)) }
                    Spacer(Modifier.height(8.dp))
                    Stepper("Every", r.every, if (r.every == 1) "month" else "months", 1..12) { apply(r.copy(every = it)) }
                }
                is RepeatRule.AfterCompletion -> Stepper("", r.days, if (r.days == 1) "day after done" else "days after done", 1..90) { apply(RepeatRule.AfterCompletion(it)) }
                else -> Unit
            }
            if (rule != null) {
                Text(rule.label(), style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            }
        }
    }
}

@Composable
internal fun DayToggles(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val on = d in selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(Shapes.full)
                    .drawBehind { drawRect(if (on) c.ink else c.inkFill) }
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onChange(if (it) selected + d else selected - d) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.name.take(1),
                    style = AppTheme.type.label,
                    color = if (on) c.onInk else c.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    prefix: String,
    value: Int,
    suffix: String?,
    range: IntRange,
    valueText: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isNotEmpty()) {
            Text(prefix, style = AppTheme.type.body, color = c.ink)
            Spacer(Modifier.width(10.dp))
        }
        StepButton(AppIcons.Minus, "Fewer", enabled = value > range.first) { onChange(value - 1) }
        Text(
            valueText(value),
            style = AppTheme.type.label,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        StepButton(AppIcons.Plus, "More", enabled = value < range.last) { onChange(value + 1) }
        if (suffix != null) {
            Spacer(Modifier.width(10.dp))
            Text(suffix, style = AppTheme.type.body, color = c.ink)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(36.dp)
            .glassControl(Shapes.full)
            .clip(Shapes.full)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) c.ink else c.inkTertiary, modifier = Modifier.size(16.dp))
    }
}

/** v0.7: a plain time dialog (Settings focus schedule). */
@Composable
fun TimePickDialog(initialMinute: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSet(state.hour * 60 + state.minute) }) { Text("Set", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.inkSecondary) } },
        text = { TimePicker(state = state) },
    )
}
