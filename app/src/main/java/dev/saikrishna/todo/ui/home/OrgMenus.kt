package dev.saikrishna.todo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import dev.saikrishna.todo.ui.components.glassControl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.FocusPrefs
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.Focus
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.domain.Template
import dev.saikrishna.todo.ui.components.SegmentedPill
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Anchor ids for the small floating menus (task ids are > 0). */
object MenuIds {
    const val LIST_PICKER = -1L
    const val ADD_MENU = -2L
}

/** What the lists button says (v0.9; was the list chip under the heading). Pure, built from the current selection and focus. */
@Immutable
data class ListChipState(val label: String, val color: Int?, val focus: FocusMode, val emoji: String? = null)

fun listChipState(lists: List<TaskList>, selected: Long?, focus: FocusMode): ListChipState {
    val sel = selected?.let { id -> lists.firstOrNull { it.id == id } }
    return when {
        // An emoji replaces the dot; the focus area is its own accent segment, so the label stays short.
        sel != null -> ListChipState(sel.name, sel.color.takeIf { sel.emoji == null }, focus, sel.emoji)
        else -> ListChipState("All lists", null, focus)
    }
}

/** v0.8 menu row that goes somewhere (saved view, planning): icon, label, chevron. */
@Composable
internal fun MenuLinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(AppIcons.ChevronRight, null, tint = c.inkTertiary, modifier = Modifier.size(18.dp))
    }
}

fun focusCaption(prefs: FocusPrefs, focus: FocusMode): String {
    val s = prefs.schedule
    val days = daysLabel(s.days)
    return when {
        s.enabled -> "Schedule: Work $days ${Focus.minuteLabel(s.start)}–${Focus.minuteLabel(s.end)}. A pick here lasts until the schedule next changes."
        focus == FocusMode.Off -> "Focus shows only one area's lists on Home. Reminders still arrive from every list."
        else -> "Showing ${focus.name.lowercase()} lists only. Reminders still arrive from every list."
    }
}

fun daysLabel(days: Set<DayOfWeek>): String {
    val sorted = days.sorted()
    if (sorted.isEmpty()) return "no days"
    val consecutive = sorted.zipWithNext().all { (a, b) -> b.value == a.value + 1 }
    fun n(d: DayOfWeek) = d.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return if (consecutive && sorted.size > 2) "${n(sorted.first())}–${n(sorted.last())}" else sorted.joinToString(", ", transform = ::n)
}

@Composable
internal fun MenuTitle(title: String, action: String?, onAction: (() -> Unit)?) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AppTheme.type.title, color = c.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = AppTheme.type.label, color = c.accent,
                modifier = Modifier
                    .clip(Shapes.full)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun PickerRow(label: String, color: Int?, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (color != null) ListDot(c.listColor(color), 10.dp)
            else Icon(AppIcons.Layers, null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (count > 0) Text("$count", style = AppTheme.type.meta, color = c.inkTertiary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(AppIcons.Check, "Selected", tint = c.ink, modifier = Modifier.size(18.dp))
        }
    }
}

/** The composer "+" menu (v0.7): templates to insert, and Manage. */
@Composable
fun AddMenuContent(templates: List<Template>, onUse: (Template) -> Unit, onManage: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
        MenuTitle("Use a template", action = "Manage", onAction = onManage)
        if (templates.isEmpty()) {
            Text(
                "No templates yet. Save one from a task's panel, or make one in Settings.",
                style = AppTheme.type.body, color = c.inkSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        for (t in templates) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = "Use ${t.name}") { onUse(t) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Stack, null, tint = c.inkSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = AppTheme.type.body, color = c.ink, maxLines = 1)
                    Text(
                        t.items.joinToString(" · ") { it.title }.let { if (it.length > 60) it.take(58) + "…" else it },
                        style = AppTheme.type.meta, color = c.inkSecondary, maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(if (t.items.size == 1) "1 task" else "${t.items.size} tasks", style = AppTheme.type.meta, color = c.inkTertiary)
            }
        }
        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

