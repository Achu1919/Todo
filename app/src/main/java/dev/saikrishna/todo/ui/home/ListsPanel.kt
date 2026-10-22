package dev.saikrishna.todo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.FocusPrefs
import dev.saikrishna.todo.data.SavedView
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.SegmentedPill
import dev.saikrishna.todo.ui.components.glassChrome
import dev.saikrishna.todo.ui.components.glassRim
import dev.saikrishna.todo.ui.components.glassShadow
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.theme.AppTheme

/** v0.9: the lists button is the composer's height (48 dp single-line capsule). */
val ListsButtonSize = 48.dp
/** v0.9: gap between the lists button and the composer, expanded and collapsed. */
val ListsButtonGap = 8.dp

/** Accessible name of the lists button. */
fun listsButtonLabel(state: ListChipState): String =
    "Lists, current: ${state.label}" + if (state.focus != FocusMode.Off) ", ${state.focus.name} focus on" else ""

/**
 * v0.9 bottom-left lists button: a 48 dp glass circle with the composer's glass (Mid shadow,
 * frosted fill, 1 dp rim), showing the layers icon for "All lists" or the selected list's colour
 * dot (emoji if it has one). [anchor] registers it for the panel morph (it is hidden while lifted).
 */
@Composable
fun ListsButton(state: ListChipState, onClick: () -> Unit, modifier: Modifier = Modifier, anchor: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(
        modifier
            .then(anchor)
            .size(ListsButtonSize)
            .testTag("lists-button")
            .glassShadow(CircleShape, GlassLevel.Mid, c.glassShadow)
            .clip(CircleShape)
            .glassChrome()
            .glassRim(CircleShape, c.glassRimTop, c.glassRimBottom)
            .clickable(role = Role.Button, onClickLabel = "Open lists", onClick = onClick)
            .semantics { contentDescription = listsButtonLabel(state) },
        contentAlignment = Alignment.Center,
    ) { ListsButtonContent(state) }
}

/** What is inside the lists button (no surface); also the morph's ghost, so the capsule lands as the button. */
@Composable
fun ListsButtonContent(state: ListChipState, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(modifier.size(ListsButtonSize), contentAlignment = Alignment.Center) {
        when {
            state.emoji != null -> Text(state.emoji, style = AppTheme.type.body)
            state.color != null -> {
                val col = c.listColor(state.color)
                // Dot + a subtle ring in the list's colour.
                Box(Modifier.size(20.dp).border(1.5.dp, col.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(col))
                }
            }
            else -> Icon(AppIcons.Layers, null, tint = c.ink, modifier = Modifier.size(22.dp))
        }
        if (state.focus != FocusMode.Off) {
            // Focus mode on: a small accent dot at the top right.
            Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(7.dp).clip(CircleShape).background(c.accent))
        }
    }
}

/**
 * v0.9 lists panel (grows out of the lists button): "Plan your day" with a live summary, the
 * lists (focus switch, All lists + each list with its open count, New list), saved views, and
 * Settings (the header no longer has a Settings button).
 */
@Composable
fun ListsPanelContent(
    planLine: String,
    lists: List<TaskList>,
    openCounts: Map<Long, Int>,
    selected: Long?,
    focus: FocusMode,
    prefs: FocusPrefs,
    views: List<SavedView>,
    onPlan: () -> Unit,
    onSelect: (Long?) -> Unit,
    onFocus: (FocusMode) -> Unit,
    onNewList: () -> Unit,
    onView: (SavedView) -> Unit,
    onSettings: () -> Unit,
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val shown = lists.filter { focus.area == null || it.area == focus.area }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp).testTag("lists-panel")) {
        // a. Plan your day.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClickLabel = "Plan your day") { haptics.tick(); onPlan() }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent.copy(alpha = if (c.isDark) 0.22f else 0.14f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Sun, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Plan your day", style = AppTheme.type.body, color = c.ink, maxLines = 1)
                Text(planLine, style = AppTheme.type.meta, color = c.inkSecondary, maxLines = 1)
            }
            Icon(AppIcons.ChevronRight, null, tint = c.inkTertiary, modifier = Modifier.size(18.dp))
        }
        Hairline()
        // b. Lists.
        MenuTitle("Lists", action = null, onAction = null)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Focus", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp, start = 2.dp))
            SegmentedPill(options = FocusMode.entries.toList(), selected = focus, label = { it.name }, onSelect = onFocus)
            if (prefs.schedule.enabled || focus != FocusMode.Off) {
                Text(focusCaption(prefs, focus), style = AppTheme.type.meta, color = c.inkTertiary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        PickerRow(
            label = if (focus == FocusMode.Off) "All lists" else "All ${focus.name.lowercase()} lists",
            color = null,
            count = shown.sumOf { openCounts[it.id] ?: 0 },
            selected = selected == null,
        ) { haptics.tick(); onSelect(null) }
        for (l in shown) {
            PickerRow(
                label = listOfNotNull(l.emoji, l.name).joinToString(" "),
                color = l.color,
                count = openCounts[l.id] ?: 0,
                selected = selected == l.id,
            ) { haptics.tick(); onSelect(l.id) }
        }
        MenuLinkRow(AppIcons.Plus, "New list") { haptics.tick(); onNewList() }
        // c. Saved views.
        if (views.isNotEmpty()) {
            Hairline()
            Text("Saved views", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp))
            for (v in views) MenuLinkRow(AppIcons.Bookmark, v.name) { haptics.tick(); onView(v) }
        }
        Hairline()
        MenuLinkRow(AppIcons.Sliders, "Settings") { haptics.tick(); onSettings() }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.inkHairline))
}
