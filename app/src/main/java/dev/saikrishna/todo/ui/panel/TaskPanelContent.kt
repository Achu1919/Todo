package dev.saikrishna.todo.ui.panel

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.tagList
import dev.saikrishna.todo.data.priority
import dev.saikrishna.todo.ui.components.PillChip
import dev.saikrishna.todo.ui.components.SegmentedPill
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.home.CheckCircle
import dev.saikrishna.todo.ui.home.DueChoice
import dev.saikrishna.todo.ui.home.MetaLine
import dev.saikrishna.todo.ui.home.dayLabel
import dev.saikrishna.todo.ui.home.dueChoiceOf
import dev.saikrishna.todo.ui.home.dueDayFor
import dev.saikrishna.todo.ui.home.rowMeta
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import dev.saikrishna.todo.domain.RepeatRule
import java.time.ZoneOffset

/**
 * Compact contents of the floating task panel. The first row mirrors the task card exactly
 * (same check circle, paddings and title style), so at the start of the container transform the
 * panel IS the card; the rest is revealed as the surface grows. Title/notes are saved when the
 * panel leaves composition (after the close animation); chips/priority/done save immediately.
 */
@Composable
fun TaskPanelContent(
    task: Task,
    today: LocalDate,
    now: LocalDateTime = LocalDateTime.now(),
    onSaveText: (title: String, notes: String) -> Unit,
    onSetDueAt: (dueDay: Long?, dueMinute: Int?) -> Unit,
    onSetRepeat: (rule: RepeatRule?, firstDay: Long?) -> Unit,
    onSetPriority: (Priority) -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onDelete: () -> Unit,
    // ---- v0.7 organisation ----
    lists: List<TaskList> = emptyList(),
    subtasks: List<Subtask> = emptyList(),
    onSetList: (Long) -> Unit = {},
    onSetTags: (List<String>) -> Unit = {},
    onAddSubtask: (String) -> Unit = {},
    onSubtaskDone: (id: Long, done: Boolean) -> Unit = { _, _ -> },
    onDeleteSubtask: (Long) -> Unit = {},
    onOrderSubtasks: (List<Long>) -> Unit = {},
    onSaveAsTemplate: (() -> Unit)? = null,
    onTag: ((String) -> Unit)? = null,
) {
    val c = AppTheme.colors
    val type = AppTheme.type
    val haptics = LocalHaptics.current
    val title = rememberTextFieldState(task.title)
    val notes = rememberTextFieldState(task.notes)
    val save by rememberUpdatedState(onSaveText)
    DisposableEffect(task.id) {
        onDispose {
            val t = title.text.toString()
            val n = notes.text.toString()
            if (t.isNotBlank() && (t != task.title || n != task.notes)) save(t, n)
        }
    }
    var picking by remember { mutableStateOf(false) }
    // Changing the day keeps the time; clearing the day clears both.
    val onSetDue: (Long?) -> Unit = { day -> onSetDueAt(day, task.dueMinute?.takeIf { day != null }) }
    val meta = rowMeta(task, today, showDue = true)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // Header: the card's own layout (TaskRow), with an editable title.
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = Spacing.rowH), verticalAlignment = Alignment.Top) {
            CheckCircle(
                checked = task.done,
                onCheckedChange = onToggleDone,
                label = task.title,
                modifier = Modifier.padding(top = 1.dp),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp, top = Spacing.rowV, bottom = 6.dp)) {
                BasicTextField(
                    state = title,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = type.title.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (!meta.isEmpty) MetaLine(meta, Modifier.padding(top = 2.dp))
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)) {
            BasicTextField(
                state = notes,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Shapes.md)
                    .background(c.inkFill)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorator = { inner ->
                    Box {
                        if (notes.text.isEmpty()) Text("Notes", style = type.body, color = c.inkTertiary)
                        inner()
                    }
                },
            )

            if (lists.isNotEmpty()) {
                SectionLabel("List")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in lists) {
                        ListPillChip(l, selected = l.id == task.listId, onClick = { haptics.tick(); onSetList(l.id) })
                    }
                }
            }

            SectionLabel("Tags")
            TagEditor(task.tagList, onSetTags, onTag)

            SectionLabel(if (subtasks.isEmpty()) "Subtasks" else "Subtasks · ${subtasks.count { it.done }}/${subtasks.size}")
            SubtaskList(subtasks, onAddSubtask, onSubtaskDone, onDeleteSubtask, onOrderSubtasks)

            SectionLabel("Due")
            val choice = dueChoiceOf(task.dueDay, today)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DueChoice.entries.forEach { option ->
                    val label = when {
                        option == DueChoice.Pick && choice == DueChoice.Pick && task.dueDay != null ->
                            dayLabel(LocalDate.ofEpochDay(task.dueDay), today)
                        else -> option.label
                    }
                    PillChip(label, selected = option == choice, onClick = {
                        haptics.tick()
                        if (option == DueChoice.Pick) picking = true else onSetDue(dueDayFor(option, today))
                    })
                }
            }

            SectionLabel("Time")
            TimeSection(task, now, onSetDueAt)

            SectionLabel("Repeat")
            RepeatSection(task, today, onSetRepeat)

            SectionLabel("Priority")
            SegmentedPill(
                options = Priority.entries.toList(),
                selected = task.priority,
                label = { it.name },
                onSelect = onSetPriority,
            )

            Spacer(Modifier.height(16.dp))
            // v0.12.1: the three actions keep their icons together or drop them together.
            val actionMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            val actionStyle = AppTheme.type.label
            var actionsRowW by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val actionTexts = listOfNotNull(if (task.done) "Mark not done" else "Mark done", if (onSaveAsTemplate != null) "Template" else null, "Delete")
            val actionIcons = actionsRowW == 0 || with(density) {
                val per = (actionsRowW - (actionTexts.size - 1) * 10.dp.toPx()) / actionTexts.size - 2 * 10.dp.toPx()
                actionTexts.all { actionMeasurer.measure(it, actionStyle, maxLines = 1, softWrap = false).size.width + 24.dp.toPx() <= per }
            }
            Row(
                Modifier.fillMaxWidth().onSizeChanged { actionsRowW = it.width },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelAction(
                    text = if (task.done) "Mark not done" else "Mark done",
                    color = c.ink,
                    icon = { Icon(AppIcons.Check, null, tint = c.ink, modifier = Modifier.size(18.dp)) },
                    onClick = { onToggleDone(!task.done) },
                    modifier = Modifier.weight(1f),
                    showIcon = actionIcons,
                )
                if (onSaveAsTemplate != null) {
                    var saved by remember(task.id) { mutableStateOf(false) }
                    PanelAction(
                        text = if (saved) "Saved" else "Template",
                        color = c.ink,
                        icon = { Icon(AppIcons.Stack, null, tint = c.ink, modifier = Modifier.size(18.dp)) },
                        onClick = { if (!saved) { haptics.tick(); onSaveAsTemplate(); saved = true } },
                        modifier = Modifier.weight(1f),
                        label = "Save as template",
                        showIcon = actionIcons,
                    )
                }
                PanelAction(
                    text = "Delete",
                    color = c.danger,
                    icon = { Icon(AppIcons.Trash, null, tint = c.danger, modifier = Modifier.size(18.dp)) },
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    label = "Delete task",
                    showIcon = actionIcons,
                )
            }
        }
    }

    if (picking) {
        val initial = (task.dueDay ?: today.toEpochDay()) * 86_400_000L
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    pickerState.selectedDateMillis?.let { millis ->
                        // DatePicker reports UTC midnight of the chosen day.
                        onSetDue(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                }) { Text("Set", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = c.inkSecondary) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
internal fun PanelAction(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = text,
    showIcon: Boolean? = null,
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .glassControl(Shapes.full)
            .clip(Shapes.full)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // v0.12.1: at large font scales a label that doesn't fit drops its icon first, then steps
        // its size down (to 75 %), then ellipsizes; it never hard-clips ("Mark …", "Templ…").
        val st = AppTheme.type.label
        val gap = 6.dp
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val textFull = remember(text, st) { measurer.measure(text, st, maxLines = 1, softWrap = false).size.width }
        androidx.compose.ui.layout.Layout(
            content = {
                icon()
                androidx.compose.foundation.text.BasicText(
                    text, style = st.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = st.fontSize * 0.75f, maxFontSize = st.fontSize, stepSize = 0.5.sp,
                    ),
                )
            },
        ) { ms, cons ->
            val loose = cons.copy(minWidth = 0)
            val iconP = ms[0].measure(loose)
            val g = gap.roundToPx()
            val withIcon = showIcon ?: (cons.maxWidth == androidx.compose.ui.unit.Constraints.Infinity ||
                iconP.width + g + textFull <= cons.maxWidth)
            val textP = ms[1].measure(loose.copy(maxWidth = if (withIcon) (cons.maxWidth - iconP.width - g).coerceAtLeast(0) else cons.maxWidth))
            val w = if (withIcon) iconP.width + g + textP.width else textP.width
            val h = maxOf(if (withIcon) iconP.height else 0, textP.height)
            layout(w.coerceIn(cons.minWidth, cons.maxWidth), h.coerceIn(cons.minHeight, cons.maxHeight)) {
                var x = 0
                if (withIcon) { iconP.place(0, (h - iconP.height) / 2); x = iconP.width + g }
                textP.place(x, (h - textP.height) / 2)
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = AppTheme.type.meta,
        color = AppTheme.colors.inkSecondary,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 2.dp),
    )
}
