package dev.saikrishna.todo.ui.search

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
 * (newest first). v0.7: optionally filtered by a tag and/or a list; with a filter, an empty query
 * lists everything that passes it.
 */
fun searchTasks(tasks: List<Task>, query: String, tag: String? = null, listId: Long? = null): List<Task> {
    val q = query.trim().removePrefix("#")
    if (q.isEmpty() && tag == null && listId == null) return emptyList()
    val hits = tasks.filter { t ->
        (tag == null || tag in t.tagList) &&
            (listId == null || t.listId == listId) &&
            (q.isEmpty() || t.title.contains(q, ignoreCase = true) || t.notes.contains(q, ignoreCase = true) ||
                t.tagList.any { it.contains(q, ignoreCase = true) })
    }
    val (done, open) = hits.partition { it.done }
    return open.sortedWith(compareBy<Task> { it.createdAt }.thenBy { it.id }) +
        done.sortedByDescending { it.completedAt ?: it.createdAt }
}

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
    val results by remember(all, filter) { derivedStateOf { searchTasks(all, query.text.toString(), filter.tag, filter.listId) } }
    val allTags = remember(all) { all.flatMap { it.tagList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val org = remember(lists, subtasks) { OrgView(lists = lists.associateBy { it.id }, showList = true, subtasks = subtasks) }
    // The pre-filter belongs to this visit only.
    DisposableEffect(Unit) { onDispose { vm.setSearchFilter(SearchFilter()) } }
    val hasFilter = filter.tag != null || filter.listId != null
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

        // v0.7 filters: lists, then tags in use. Tap to toggle.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (l in lists) {
                FilterChip(
                    label = listOfNotNull(l.emoji, l.name).joinToString(" "),
                    dot = c.listColor(l.color),
                    selected = filter.listId == l.id,
                ) { haptics.tick(); vm.setSearchFilter(filter.copy(listId = if (filter.listId == l.id) null else l.id)) }
            }
            for (t in allTags) {
                FilterChip(label = "#$t", dot = null, selected = filter.tag == t) {
                    haptics.tick(); vm.setSearchFilter(filter.copy(tag = if (filter.tag == t) null else t))
                }
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
                        onTag = { t -> haptics.tick(); vm.setSearchFilter(filter.copy(tag = t)) },
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

/** Search filter chip: ink when selected; lists carry their colour dot. */
@Composable
private fun FilterChip(label: String, dot: androidx.compose.ui.graphics.Color?, selected: Boolean, onClick: () -> Unit) {
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
        BasicText(label, style = AppTheme.type.label, color = { text }, maxLines = 1)
    }
}
