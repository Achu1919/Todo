package dev.saikrishna.todo.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saikrishna.todo.data.ListArea
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.Focus
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.domain.Template
import dev.saikrishna.todo.domain.Templates
import dev.saikrishna.todo.ui.components.MotionSwitch
import dev.saikrishna.todo.ui.components.PillChip
import dev.saikrishna.todo.ui.components.SegmentedPill
import dev.saikrishna.todo.ui.components.liftShadow
import dev.saikrishna.todo.ui.home.ListDot
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.home.moved
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.Depth
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.panel.DayToggles
import dev.saikrishna.todo.ui.panel.PanelAction
import dev.saikrishna.todo.ui.panel.TimePickDialog
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.ListPalette
import dev.saikrishna.todo.ui.theme.Shapes
import sh.calvin.reorderable.ReorderableColumn
import java.time.LocalDateTime

// ---- Focus schedule (Settings) -------------------------------------------------------------

@Composable
fun FocusScheduleCard(vm: TodoViewModel) {
    val c = AppTheme.colors
    val prefs by vm.focusPrefs.collectAsStateWithLifecycle()
    val s = prefs.schedule
    var editing by remember { mutableStateOf<Int?>(null) } // 0 = start, 1 = end
    Card {
        SwitchRow(
            title = "Automatic focus",
            subtitle = if (s.enabled) "Work lists during work hours, ${s.outside.name.lowercase()} lists outside them. Reminders still arrive from every list."
            else "Switch to Work focus on a schedule. You can also pick a focus from the list chip on Home.",
            checked = s.enabled,
            onChange = { vm.setSchedule(s.copy(enabled = it)) },
        )
        AnimatedVisibility(
            visible = s.enabled,
            enter = fadeIn(Tweens.fadeIn(0)) + expandVertically(Springs.Smooth.intSize),
            exit = fadeOut(Tweens.fadeOut()) + shrinkVertically(Springs.Smooth.intSize),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text("Work days", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(bottom = 8.dp))
                DayToggles(s.days) { vm.setSchedule(s.copy(days = it)) }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("From", style = AppTheme.type.body, color = c.inkSecondary)
                    PillChip(Focus.minuteLabel(s.start), selected = true, onClick = { editing = 0 })
                    Text("to", style = AppTheme.type.body, color = c.inkSecondary)
                    PillChip(Focus.minuteLabel(s.end), selected = true, onClick = { editing = 1 })
                }
                Text("Outside work hours", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                SegmentedPill(
                    options = listOf(FocusMode.Personal, FocusMode.Off),
                    selected = s.outside,
                    label = { if (it == FocusMode.Off) "All lists" else "Personal" },
                    onSelect = { vm.setSchedule(s.copy(outside = it)) },
                )
            }
        }
    }
    val e = editing
    if (e != null) {
        TimePickDialog(
            initialMinute = if (e == 0) s.start else s.end,
            onSet = { m -> editing = null; vm.setSchedule(if (e == 0) s.copy(start = m) else s.copy(end = m)) },
            onDismiss = { editing = null },
        )
    }
}

// ---- Lists -----------------------------------------------------------------------------------

/**
 * Settings > Lists (v0.7): reorder (drag the grip), tap to edit name / emoji / colour / focus
 * area; user lists can be deleted after choosing where their tasks go. New list at the bottom.
 */
@Composable
fun ListsScreen(vm: TodoViewModel, onBack: () -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val lists by vm.lists.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val counts = remember(tasks) { tasks.orEmpty().groupingBy { it.listId }.eachCount() }
    var open by remember { mutableStateOf<Long?>(null) }
    var order by remember(lists) { mutableStateOf(lists) }

    ScreenScaffold(title = "Lists", onBack = onBack) {
        GroupLabel("Your lists")
        Card {
            ReorderableColumn(
                list = order,
                onSettle = { from, to ->
                    val next = order.moved(from, to)
                    order = next
                    vm.orderLists(next.map { it.id })
                },
                onMove = { haptics.tick() },
            ) { index, list, isDragging ->
                key(list.id) {
                    ReorderableItem {
                        val lift = remember { Animatable(0f) }
                        LaunchedEffect(isDragging) {
                            val t = if (isDragging) 1f else 0f
                            if (reduced) lift.snapTo(t) else lift.animateTo(t, Springs.Quick.float)
                        }
                        Column(
                            Modifier
                                .graphicsLayer { val sc = 1f + (Depth.LiftScale - 1f) * lift.value; scaleX = sc; scaleY = sc }
                                .liftShadow(Shapes.lg, c.glassShadow) { lift.value }
                                .drawBehind { if (lift.value > 0f) drawRect(c.glassSolid.copy(alpha = lift.value)) },
                        ) {
                            if (index > 0) Divider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = "Edit ${list.name}") {
                                        haptics.tick(); open = if (open == list.id) null else list.id
                                    }
                                    .padding(start = 16.dp, end = 4.dp)
                                    .heightIn(min = 56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ListDot(c.listColor(list.color), 10.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(listOfNotNull(list.emoji, list.name).joinToString(" "), style = AppTheme.type.title, color = c.ink)
                                    Text(
                                        "${list.area.name} focus · ${counts[list.id] ?: 0} tasks" + if (list.builtIn) " · built in" else "",
                                        style = AppTheme.type.meta, color = c.inkSecondary,
                                    )
                                }
                                Box(
                                    Modifier.size(44.dp).draggableHandle(onDragStarted = { haptics.tick() })
                                        .semantics { contentDescription = "Drag to reorder ${list.name}" },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(AppIcons.Grip, null, tint = c.inkTertiary, modifier = Modifier.size(18.dp)) }
                            }
                            AnimatedVisibility(
                                visible = open == list.id,
                                enter = fadeIn(Tweens.fadeIn(0)) + expandVertically(Springs.Smooth.intSize),
                                exit = fadeOut(Tweens.fadeOut()) + shrinkVertically(Springs.Smooth.intSize),
                            ) {
                                ListEditor(
                                    list = list,
                                    others = lists.filter { it.id != list.id },
                                    taskCount = counts[list.id] ?: 0,
                                    onSave = { vm.updateList(it) },
                                    onDelete = { moveTo -> open = null; vm.deleteList(list.id, moveTo) },
                                )
                            }
                        }
                    }
                }
            }
        }

        GroupLabel("New list")
        Card { NewListRow(onCreate = { name -> vm.createList(name, (lists.size + 3) % ListPalette.size, null, ListArea.Personal) }) }

        // v0.13: Focus and saved views moved here from the old lists panel.
        val prefs by vm.focusPrefs.collectAsStateWithLifecycle()
        val focus = remember(prefs) { vm.focusAt(System.currentTimeMillis()) }
        GroupLabel("Focus")
        Card {
            Column(Modifier.padding(16.dp)) {
                dev.saikrishna.todo.ui.components.SegmentedPill(
                    options = FocusMode.entries.toList(),
                    selected = focus,
                    label = { it.name },
                    onSelect = { haptics.tick(); vm.setFocus(it) },
                )
                Text(
                    dev.saikrishna.todo.ui.home.focusCaption(prefs, focus).ifEmpty { "Work or Personal shows only that area's lists." },
                    style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                )
            }
        }
        val views by vm.views.collectAsStateWithLifecycle()
        GroupLabel("Saved views")
        Card {
            if (views.isEmpty()) {
                Text(
                    "Save a search with its filters to keep it here and on Home.",
                    style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(16.dp),
                )
            }
            views.forEachIndexed { i, v ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Bookmark, null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(v.name, style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(Shapes.full)
                            .clickable(role = Role.Button, onClickLabel = "Delete ${v.name}") { haptics.tick(); vm.deleteView(v.id) }
                            .semantics { contentDescription = "Delete ${v.name}" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Trash, null, tint = c.inkTertiary, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ListEditor(
    list: TaskList,
    others: List<TaskList>,
    taskCount: Int,
    onSave: (TaskList) -> Unit,
    onDelete: (moveTo: Long) -> Unit,
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val name = rememberTextFieldState(list.name)
    val emoji = rememberTextFieldState(list.emoji.orEmpty())
    var deleting by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf(TaskList.PERSONAL.takeIf { it != list.id } ?: TaskList.WORK) }
    fun save(l: TaskList = list) = onSave(l.copy(name = name.text.toString().ifBlank { list.name }, emoji = emoji.text.toString().trim().take(4).ifEmpty { null }))

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(emoji, "Emoji", Modifier.width(72.dp)) { save() }
            Field(name, "Name", Modifier.weight(1f)) { save() }
        }
        Text("Colour", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until ListPalette.size) {
                val on = list.color == i
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(Shapes.full)
                        .drawBehind {
                            if (on) drawCircle(c.ink, radius = size.minDimension / 2f)
                            drawCircle(c.listColor(i), radius = size.minDimension / 2f - if (on) 3.dp.toPx() else 4.dp.toPx())
                        }
                        .clickable(role = Role.RadioButton, onClickLabel = ListPalette.names[i]) { haptics.tick(); save(list.copy(color = i)) }
                        .semantics { selected = on; contentDescription = ListPalette.names[i] },
                )
            }
        }
        Text("Shown in focus", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        SegmentedPill(
            options = ListArea.entries.toList(),
            selected = list.area,
            label = { it.name },
            onSelect = { save(list.copy(areaCode = it.code)) },
        )
        if (!list.builtIn) {
            Spacer(Modifier.height(14.dp))
            if (!deleting) {
                PanelAction(
                    text = "Delete list", color = c.danger,
                    icon = { Icon(AppIcons.Trash, null, tint = c.danger, modifier = Modifier.size(18.dp)) },
                    onClick = { haptics.tick(); deleting = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    if (taskCount == 0) "This list is empty." else "Move its $taskCount task${if (taskCount == 1) "" else "s"} to:",
                    style = AppTheme.type.body, color = c.ink, modifier = Modifier.padding(bottom = 8.dp),
                )
                if (taskCount > 0) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (o in others) PillChip(o.name, selected = moveTo == o.id, onClick = { haptics.tick(); moveTo = o.id })
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelAction("Cancel", c.ink, icon = {}, onClick = { deleting = false }, modifier = Modifier.weight(1f))
                    PanelAction(
                        "Delete", c.danger,
                        icon = { Icon(AppIcons.Trash, null, tint = c.danger, modifier = Modifier.size(18.dp)) },
                        onClick = { haptics.thump(); onDelete(moveTo) },
                        modifier = Modifier.weight(1f), label = "Delete list",
                    )
                }
            }
        }
    }
}

@Composable
private fun NewListRow(onCreate: (String) -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val name = rememberTextFieldState()
    fun create() {
        val n = name.text.toString().trim()
        if (n.isNotEmpty()) { haptics.tick(); onCreate(n); name.clearText() }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(name, "List name, e.g. Home", Modifier.weight(1f)) { create() }
        Spacer(Modifier.width(10.dp))
        PanelAction("Add", c.ink, icon = { Icon(AppIcons.Plus, null, tint = c.ink, modifier = Modifier.size(18.dp)) }, onClick = ::create, label = "Add list")
    }
}

@Composable
internal fun Field(state: TextFieldState, hint: String, modifier: Modifier = Modifier, lines: Int = 1, onDone: () -> Unit) {
    val c = AppTheme.colors
    BasicTextField(
        state = state,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(Shapes.md)
            .background(c.inkFill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        textStyle = AppTheme.type.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        lineLimits = if (lines == 1) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = lines, maxHeightInLines = 12),
        keyboardOptions = KeyboardOptions(
            imeAction = if (lines == 1) ImeAction.Done else ImeAction.Default,
            capitalization = KeyboardCapitalization.Sentences,
        ),
        onKeyboardAction = { onDone() },
        decorator = { inner ->
            Box {
                if (state.text.isEmpty()) Text(hint, style = AppTheme.type.body, color = c.inkTertiary)
                inner()
            }
        },
    )
}

// ---- Templates -------------------------------------------------------------------------------

/**
 * Settings > Templates (v0.7): the saved templates (tap to see their tasks, delete), and a new
 * template written like the composer, one task per line, "-" for subtasks.
 */
@Composable
fun TemplatesScreen(vm: TodoViewModel, onBack: () -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val templates by vm.templates.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Long?>(null) }

    ScreenScaffold(title = "Templates", onBack = onBack) {
        GroupLabel("Saved")
        Card {
            if (templates.isEmpty()) {
                Text("No templates. Make one below, or use “Template” in a task's panel.", style = AppTheme.type.body, color = c.inkSecondary, modifier = Modifier.padding(16.dp))
            }
            templates.forEachIndexed { i, t ->
                key(t.id) {
                    if (i > 0) Divider()
                    TemplateRow(t, lists, open == t.id, onToggle = { haptics.tick(); open = if (open == t.id) null else t.id }, onDelete = { haptics.tick(); vm.deleteTemplate(t.id) })
                }
            }
        }

        GroupLabel("New template")
        Card {
            val name = rememberTextFieldState()
            val body = rememberTextFieldState()
            val preview = remember(body.text, lists) { Templates.parseText(body.text.toString(), LocalDateTime.now(), lists) }
            Column(Modifier.padding(16.dp)) {
                Field(name, "Name, e.g. Monday setup", Modifier.fillMaxWidth()) {}
                Spacer(Modifier.height(8.dp))
                Field(body, "One task per line, like the composer:\nPlan the week tomorrow #work !high\n- Inbox\n- Calendar", Modifier.fillMaxWidth(), lines = 4) {}
                Text(
                    if (preview.isEmpty()) "Dates are saved relative to the day you use the template."
                    else "${preview.size} task${if (preview.size == 1) "" else "s"}: " + preview.joinToString(" · ") { it.title + offsetLabel(it.dayOffset) },
                    style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )
                PanelAction(
                    "Save template", c.ink,
                    icon = { Icon(AppIcons.Stack, null, tint = c.ink, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        val n = name.text.toString().trim()
                        if (n.isNotEmpty() && preview.isNotEmpty()) {
                            haptics.tick()
                            vm.addTemplate(n, preview)
                            name.clearText(); body.clearText()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun offsetLabel(d: Int?): String = when (d) { null -> ""; 0 -> " (+0d)"; else -> " (+${d}d)" }

@Composable
private fun TemplateRow(t: Template, lists: List<TaskList>, open: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(start = 16.dp, end = 4.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Stack, null, tint = c.inkSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = AppTheme.type.title, color = c.ink)
                Text(if (t.items.size == 1) "1 task" else "${t.items.size} tasks", style = AppTheme.type.meta, color = c.inkSecondary)
            }
            Box(
                Modifier.size(44.dp).clip(Shapes.full).clickable(role = Role.Button, onClickLabel = "Delete ${t.name}", onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Trash, "Delete ${t.name}", tint = c.inkTertiary, modifier = Modifier.size(18.dp)) }
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(Tweens.fadeIn(0)) + expandVertically(Springs.Smooth.intSize),
            exit = fadeOut(Tweens.fadeOut()) + shrinkVertically(Springs.Smooth.intSize),
        ) {
            Column(Modifier.padding(start = 48.dp, end = 16.dp, bottom = 14.dp)) {
                for (i in t.items) {
                    val list = lists.firstOrNull { it.id == i.listId }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        if (list != null) { ListDot(c.listColor(list.color)); Spacer(Modifier.width(8.dp)) }
                        Text(i.title + offsetLabel(i.dayOffset), style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    }
                    for (s in i.subtasks) Text("– $s", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
