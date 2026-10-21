@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.saikrishna.todo.ui.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.domain.Snooze
import dev.saikrishna.todo.domain.SnoozeOption
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.home.CheckCircle
import dev.saikrishna.todo.ui.home.MetaLine
import dev.saikrishna.todo.ui.home.rowMeta
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Compact snooze picker shown in the same floating panel as the task details (container
 * transform from the swiped row). The header mirrors the card; below are the quick targets
 * and "Pick…" (date, then time).
 */
@Composable
fun SnoozeContent(
    task: Task,
    today: LocalDate,
    now: LocalDateTime,
    onToggleDone: (Boolean) -> Unit,
    onSnooze: (day: LocalDate, minute: Int) -> Unit,
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val meta = rowMeta(task, today, showDue = true)
    var pickDate by remember { mutableStateOf(false) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = Spacing.rowH), verticalAlignment = Alignment.Top) {
            CheckCircle(checked = task.done, onCheckedChange = onToggleDone, label = task.title, modifier = Modifier.padding(top = 1.dp))
            Column(Modifier.weight(1f).padding(start = 4.dp, top = Spacing.rowV, bottom = 6.dp)) {
                Text(task.title, style = AppTheme.type.title, color = c.ink, maxLines = 2)
                if (!meta.isEmpty) MetaLine(meta, Modifier.padding(top = 2.dp))
            }
        }
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Snooze until",
                style = AppTheme.type.meta,
                color = c.inkSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
            )
            Snooze.options(now).forEach { o ->
                SnoozeRow(iconFor(o), o.label, o.detail(today)) { haptics.tick(); onSnooze(o.day, o.minute) }
            }
            SnoozeRow(AppIcons.Calendar, "Pick…", "Date and time") { haptics.tick(); pickDate = true }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.plusDays(1).toEpochDay() * 86_400_000L,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today.toEpochDay() * 86_400_000L
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    state.selectedDateMillis?.let { pickedDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text("Next", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.inkSecondary) } },
        ) { DatePicker(state = state) }
    }
    pickedDay?.let { day ->
        val start = task.dueMinute ?: Snooze.MORNING
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickedDay = null },
            confirmButton = {
                TextButton(onClick = { pickedDay = null; onSnooze(day, state.hour * 60 + state.minute) }) { Text("Snooze", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { pickedDay = null }) { Text("Cancel", color = c.inkSecondary) } },
            text = { TimePicker(state = state) },
        )
    }
}

private fun iconFor(o: SnoozeOption): ImageVector = when (o.label) {
    "Next week", "Tomorrow" -> AppIcons.Calendar
    else -> AppIcons.Clock
}

@Composable
private fun SnoozeRow(icon: ImageVector, label: String, detail: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .glassControl(Shapes.md)
            .clip(Shapes.md)
            .clickable(role = Role.Button, onClickLabel = "Snooze: $label", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = AppTheme.type.label, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
        Text(detail, style = AppTheme.type.meta, color = c.inkSecondary, maxLines = 1)
    }
}
