package dev.saikrishna.todo.ui.panel

import dev.saikrishna.todo.ui.components.animateStateColor
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.Tags
import dev.saikrishna.todo.ui.components.liftShadow
import dev.saikrishna.todo.ui.home.ListDot
import dev.saikrishna.todo.ui.home.moved
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.Depth
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.PillSpec
import dev.saikrishna.todo.ui.theme.Shapes
import sh.calvin.reorderable.ReorderableColumn

/** A list choice in the panel: dot + name, ink capsule when selected (like [PillChip]). */
@Composable
fun ListPillChip(list: TaskList, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val fill by animateStateColor(selected, c.ink, c.inkFill, Tweens.select(), label = "list chip fill")
    val text by animateStateColor(selected, c.onInk, c.ink, Tweens.select(), label = "list chip text")
    Row(
        Modifier
            .height(PillSpec.ChipHeight)
            .clip(Shapes.full)
            .drawBehind { drawRect(fill) }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListDot(c.listColor(list.color), 8.dp)
        Spacer(Modifier.width(7.dp))
        BasicText(listOfNotNull(list.emoji, list.name).joinToString(" "), style = AppTheme.type.label, color = { text }, maxLines = 1)
    }
}

/** Tags as removable chips + a small "Add tag" field (enter adds). Tapping a tag opens Search. */
@Composable
fun TagEditor(tags: List<String>, onSet: (List<String>) -> Unit, onTag: ((String) -> Unit)?) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val field = rememberTextFieldState()
    fun commit() {
        val t = Tags.clean(field.text.toString())
        if (t.isNotEmpty() && t !in tags) { haptics.tick(); onSet(tags + t) }
        field.clearText()
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (t in tags) key(t) {
            Row(
                Modifier
                    .height(32.dp)
                    .clip(Shapes.full)
                    .background(c.inkFill)
                    .then(if (onTag != null) Modifier.clickable(role = Role.Button, onClickLabel = "Search #$t") { onTag(t) } else Modifier)
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#$t", style = AppTheme.type.meta, color = c.ink, maxLines = 1)
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(Shapes.full)
                        .clickable(role = Role.Button, onClickLabel = "Remove #$t") { haptics.tick(); onSet(tags - t) },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, "Remove #$t", tint = c.inkTertiary, modifier = Modifier.size(12.dp)) }
            }
        }
        BasicTextField(
            state = field,
            modifier = Modifier
                .widthIn(min = 96.dp)
                .height(32.dp)
                .clip(Shapes.full)
                .background(c.inkFill)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            textStyle = AppTheme.type.meta.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.None),
            onKeyboardAction = { commit() },
            decorator = { inner ->
                Box {
                    if (field.text.isEmpty()) Text("Add tag", style = AppTheme.type.meta, color = c.inkTertiary)
                    inner()
                }
            },
        )
    }
}

/**
 * Subtask checklist (v0.7): check, delete, add, and drag the grip to reorder (lift 1.03 +
 * shadow, tick on pickup and on every slot change; ReorderableColumn, since the panel is a plain
 * scrolling Column). Order is written once, on drop.
 */
@Composable
fun SubtaskList(
    subtasks: List<Subtask>,
    onAdd: (String) -> Unit,
    onDone: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onOrder: (List<Long>) -> Unit,
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    var order by remember(subtasks) { mutableStateOf(subtasks) }
    Column(Modifier.fillMaxWidth().clip(Shapes.md).background(c.inkFill)) {
        ReorderableColumn(
            list = order,
            onSettle = { from, to ->
                val next = order.moved(from, to)
                order = next
                onOrder(next.map { it.id })
            },
            onMove = { haptics.tick() },
        ) { _, item, isDragging ->
            key(item.id) {
                ReorderableItem {
                    val lift = remember { Animatable(0f) }
                    LaunchedEffect(isDragging) {
                        val t = if (isDragging) 1f else 0f
                        if (reduced) lift.snapTo(t) else lift.animateTo(t, Springs.Quick.float)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val s = 1f + (Depth.LiftScale - 1f) * lift.value
                                scaleX = s; scaleY = s
                            }
                            .liftShadow(Shapes.sm, c.glassShadow) { lift.value }
                            .drawBehind { if (lift.value > 0f) drawRect(c.glassSolid.copy(alpha = lift.value)) }
                            .heightIn(min = 44.dp)
                            .padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(Shapes.full)
                                .clickable(role = Role.Checkbox, onClickLabel = if (item.done) "Mark not done" else "Mark done") {
                                    haptics.tick(); onDone(item.id, !item.done)
                                }
                                .semantics { contentDescription = item.title + if (item.done) ", done" else "" },
                            contentAlignment = Alignment.Center,
                        ) { MiniCheck(item.done) }
                        Text(
                            item.title,
                            style = AppTheme.type.body.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
                            color = if (item.done) c.inkTertiary else c.ink,
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                        )
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(Shapes.full)
                                .clickable(role = Role.Button, onClickLabel = "Delete subtask") { haptics.tick(); onDelete(item.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Close, "Delete ${item.title}", tint = c.inkTertiary, modifier = Modifier.size(14.dp)) }
                        Box(
                            Modifier
                                .size(36.dp)
                                .draggableHandle(onDragStarted = { haptics.tick() })
                                .semantics { contentDescription = "Drag to reorder ${item.title}" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Grip, null, tint = c.inkTertiary, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        val field = rememberTextFieldState()
        fun add() {
            val t = field.text.toString().trim()
            if (t.isNotEmpty()) { haptics.tick(); onAdd(t); field.clearText() }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Plus, null, tint = c.inkTertiary, modifier = Modifier.size(16.dp))
            }
            BasicTextField(
                state = field,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textStyle = AppTheme.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                onKeyboardAction = { add() },
                decorator = { inner ->
                    Box {
                        if (field.text.isEmpty()) Text("Add subtask", style = AppTheme.type.body, color = c.inkTertiary)
                        inner()
                    }
                },
            )
        }
    }
}

/** Small round check for subtasks: ink ring, filled ink + paper tick when done. */
@Composable
private fun MiniCheck(done: Boolean) {
    val c = AppTheme.colors
    val fill by animateStateColor(done, c.ink, c.ink.copy(alpha = 0f), Tweens.select(), label = "mini check")
    Box(
        Modifier
            .size(20.dp)
            .clip(Shapes.full)
            .drawBehind {
                drawRect(fill)
                if (!done) drawCircle(c.inkStroke, radius = size.minDimension / 2f - 0.75.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(AppIcons.Check, null, tint = c.onInk, modifier = Modifier.size(13.dp))
    }
}
