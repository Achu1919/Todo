package dev.saikrishna.todo.ui.search

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.domain.DueFilter
import dev.saikrishna.todo.domain.StatusFilter
import dev.saikrishna.todo.domain.ViewFilter
import dev.saikrishna.todo.domain.filterTasks
import java.time.LocalDate

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import dev.saikrishna.todo.ui.components.LocalNavMoving
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.ui.components.CircleIconButton
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.skyBackdrop
import dev.saikrishna.todo.ui.theme.PillSpec
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import dev.saikrishna.todo.ui.home.EmptyMessage
import dev.saikrishna.todo.ui.home.TaskRow
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.home.SearchFilter
import dev.saikrishna.todo.ui.home.OrgView
import dev.saikrishna.todo.ui.home.ListDot
import dev.saikrishna.todo.data.tagList
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.text.BasicText
import dev.saikrishna.todo.ui.home.rememberToday
import dev.saikrishna.todo.ui.home.rowMeta
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing

/**
 * Pure: case-insensitive match on title, notes or tags; open tasks first (oldest first), then done
 * (newest first). Kept for v0.7 callers; v0.8 search uses [filterTasks] with a full [ViewFilter].
 */
fun searchTasks(tasks: List<Task>, query: String, tag: String? = null, listId: Long? = null, today: LocalDate = LocalDate.now()): List<Task> =
    filterTasks(tasks, query, ViewFilter(listId = listId, tag = tag), today)

/** Search (spec 6.12): pushed screen, autofocused field, live results that animate in/out/move. */
@Composable
fun SearchScreen(vm: TodoViewModel, onBack: () -> Unit) {
    val c = AppTheme.colors
    val type = AppTheme.type
    val haptics = LocalHaptics.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val today = rememberToday()
    val query = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val all = tasks.orEmpty()
    val filter by vm.searchFilter.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val views by vm.views.collectAsStateWithLifecycle()
    val f = filter.filter
    val results by remember(all, f, today) { derivedStateOf { filterTasks(all, query.text.toString(), f, today) } }
    // A saved view opened from the list menu brings its text.
    LaunchedEffect(filter.query) {
        val q = filter.query ?: return@LaunchedEffect
        query.setTextAndPlaceCursorAtEnd(q)
        vm.setSearchFilter(filter.copy(query = null))
    }
    fun setF(n: ViewFilter) { haptics.tick(); vm.setSearchFilter(filter.copy(filter = n)) }
    var naming by remember { mutableStateOf(false) }
    val allTags = remember(all) { all.flatMap { it.tagList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val org = remember(lists, subtasks) { OrgView(lists = lists.associateBy { it.id }, showList = true, subtasks = subtasks) }
    // The pre-filter belongs to this visit only.
    DisposableEffect(Unit) { onDispose { vm.setSearchFilter(SearchFilter()) } }
    val hasFilter = !f.isEmpty
    // Autofocus only once the push has settled: opening the IME mid-transition re-lays-out the
    // whole screen every frame of the keyboard animation, on top of the slide.
    val moving = LocalNavMoving.current
    val prefiltered = remember { vm.searchFilter.value != SearchFilter() }
    LaunchedEffect(Unit) {
        snapshotFlow { moving.value }.first { !it }
        // Opened from a tag pill: show the results, don't cover them with the keyboard.
        if (!prefiltered) focus.requestFocus()
    }

    Column(Modifier.fillMaxSize().background(c.paper).skyBackdrop(c).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(AppIcons.ArrowLeft, "Back", onBack, glass = true)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                state = query,
                modifier = Modifier
                    .weight(1f)
                    .height(PillSpec.Height)
                    .glassControl(Shapes.full)
                    .padding(horizontal = 18.dp)
                    .focusRequester(focus),
                textStyle = type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = { keyboard?.hide() },
                decorator = { inner ->
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (query.text.isEmpty()) Text("Search tasks and notes", style = type.body, color = c.inkTertiary)
                            inner()
                        }
                    }
                },
            )
            if (query.text.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                CircleIconButton(AppIcons.Close, "Clear search", { query.clearText() }, glass = true)
            }
        }

        // v0.8: saved views (tap = apply, tap again = clear), then "Save view" when something is set.
        val qText = query.text.toString().trim()
        val active = views.firstOrNull { it.query == qText && ViewFilter.decode(it.filter) == f && (qText.isNotEmpty() || !f.isEmpty) }
        if (views.isNotEmpty() || qText.isNotEmpty() || hasFilter) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (v in views) {
                    FilterChip(label = v.name, dot = null, selected = active?.id == v.id, icon = AppIcons.Bookmark) {
                        haptics.tick()
                        if (active?.id == v.id) { query.clearText(); vm.setSearchFilter(SearchFilter()) }
                        else { query.setTextAndPlaceCursorAtEnd(v.query); vm.setSearchFilter(SearchFilter(ViewFilter.decode(v.filter))) }
                    }
                }
                if (active != null) {
                    ActionChip("Delete view") { haptics.tick(); vm.deleteView(active.id); query.clearText(); vm.setSearchFilter(SearchFilter()) }
                } else if (qText.isNotEmpty() || hasFilter) {
                    ActionChip("Save view") { haptics.tick(); naming = true }
                }
            }
        }
        if (naming) {
            SaveViewRow(
                suggested = suggestViewName(qText, f, lists),
                onSave = { name -> vm.saveView(name, qText, f); naming = false },
                onCancel = { naming = false },
            )
        }
        // v0.8 filters: status, due, priority, reminder, recurring. Combine freely (all must hold).
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (st in listOf(StatusFilter.Open, StatusFilter.Done)) {
                FilterChip(st.label, null, f.status == st) { setF(f.copy(status = if (f.status == st) StatusFilter.Any else st)) }
            }
            for (d in DueFilter.entries) {
                FilterChip(d.label, null, f.due == d) { setF(f.copy(due = if (f.due == d) null else d)) }
            }
            FilterChip("High priority", null, f.priority == Priority.High) { setF(f.copy(priority = if (f.priority == Priority.High) null else Priority.High)) }
            FilterChip("Low priority", null, f.priority == Priority.Low) { setF(f.copy(priority = if (f.priority == Priority.Low) null else Priority.Low)) }
            FilterChip("Reminder", null, f.reminder, icon = AppIcons.Clock) { setF(f.copy(reminder = !f.reminder)) }
            FilterChip("Recurring", null, f.recurring, icon = AppIcons.Repeat) { setF(f.copy(recurring = !f.recurring)) }
        }
        // v0.7 filters: lists, then tags in use. Tap to toggle.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (l in lists) {
                FilterChip(
                    label = listOfNotNull(l.emoji, l.name).joinToString(" "),
                    dot = c.listColor(l.color),
                    selected = f.listId == l.id,
                ) { setF(f.copy(listId = if (f.listId == l.id) null else l.id)) }
            }
            for (t in allTags) {
                FilterChip(label = "#$t", dot = null, selected = f.tag == t) { setF(f.copy(tag = if (f.tag == t) null else t)) }
            }
        }

        val q = query.text
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 24.dp,
            ),
        ) {
            when {
                q.isBlank() && !hasFilter -> item(key = "hint") {
                    EmptyMessage("Search", "Find any task by its title, notes or tag.", Modifier.animateItem())
                }
                results.isEmpty() -> item(key = "none") {
                    EmptyMessage("No matches", if (q.isBlank()) "Nothing matches this filter." else "Nothing matches “${q.trim()}”.", Modifier.animateItem())
                }
                else -> items(results, key = { it.id }, contentType = { "row" }) { task ->
                    TaskRow(
                        task = task,
                        meta = remember(task, today, org) { rowMeta(task, today, showDue = true, org = org) },
                        onTag = { t -> haptics.tick(); vm.setSearchFilter(filter.copy(filter = f.copy(tag = t))) },
                        onCheckedChange = { checked ->
                            if (checked) haptics.thump() else haptics.tick()
                            vm.setDone(task.id, checked)
                        },
                        onClick = {
                            keyboard?.hide()
                            vm.requestOpen(task.id)
                            onBack()
                        },
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = Tweens.fadeIn(0),
                                placementSpec = Springs.Snappy.intOffset,
                                fadeOutSpec = Tweens.fadeOut(),
                            )
                            .padding(bottom = Spacing.rowGap),
                    )
                }
            }
            item(key = "nav") { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/** v0.8: a suggested name for a new saved view, from what is set ("Overdue · #bills · rent"). */
fun suggestViewName(query: String, f: ViewFilter, lists: List<dev.saikrishna.todo.data.TaskList>): String = listOfNotNull(
    f.listId?.let { id -> lists.firstOrNull { it.id == id }?.name },
    f.due?.label,
    f.status.takeIf { it != StatusFilter.Any }?.label,
    f.priority?.let { "${it.name} priority" },
    f.tag?.let { "#$it" },
    "Reminders".takeIf { f.reminder },
    "Recurring".takeIf { f.recurring },
    query.takeIf { it.isNotBlank() }?.let { "“$it”" },
).joinToString(" · ").take(40).ifEmpty { "My view" }

/** Glass action chip ("Save view" / "Delete view"). */
@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .heightIn(min = 34.dp)
            .glassControl(Shapes.full)
            .clip(Shapes.full)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = AppTheme.type.label, color = c.accent, maxLines = 1) }
}

/** Inline "name this view" row: glass field + Save / Cancel. */
@Composable
private fun SaveViewRow(suggested: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val c = AppTheme.colors
    val name = rememberTextFieldState(suggested)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = name,
            modifier = Modifier.weight(1f).height(40.dp).glassControl(Shapes.full).padding(horizontal = 14.dp).focusRequester(focus)
                .semantics { contentDescription = "View name" },
            textStyle = AppTheme.type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { if (name.text.isNotBlank()) onSave(name.text.toString()) },
            decorator = { inner -> Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) { inner() } },
        )
        Spacer(Modifier.width(6.dp))
        ActionChip("Save") { if (name.text.isNotBlank()) onSave(name.text.toString()) }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.heightIn(min = 34.dp).clip(Shapes.full).clickable(role = Role.Button, onClick = onCancel).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Cancel", style = AppTheme.type.label, color = c.inkSecondary) }
    }
}

/** Search filter chip: ink when selected; lists carry their colour dot. */
@Composable
private fun FilterChip(
    label: String,
    dot: androidx.compose.ui.graphics.Color?,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val fill by animateColorAsState(if (selected) c.ink else c.glassFill, Tweens.select(), label = "filter fill")
    val text by animateColorAsState(if (selected) c.onInk else c.ink, Tweens.select(), label = "filter text")
    Row(
        Modifier
            .heightIn(min = 34.dp)
            .clip(Shapes.full)
            .drawBehind { drawRect(fill) }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { ListDot(dot, 8.dp); Spacer(Modifier.width(6.dp)) }
        if (icon != null) {
            androidx.compose.material3.Icon(icon, null, tint = text, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        BasicText(label, style = AppTheme.type.label, color = { text }, maxLines = 1)
    }
}
