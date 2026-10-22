package dev.saikrishna.todo.ui.search

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.tagList
import dev.saikrishna.todo.domain.DueFilter
import dev.saikrishna.todo.domain.StatusFilter
import dev.saikrishna.todo.domain.ViewFilter
import dev.saikrishna.todo.domain.filterTasks
import dev.saikrishna.todo.ui.components.animateStateColor
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.searchFrost
import dev.saikrishna.todo.ui.components.smoothstep
import dev.saikrishna.todo.ui.home.ListDot
import dev.saikrishna.todo.ui.home.OrgView
import dev.saikrishna.todo.ui.home.SearchFilter
import dev.saikrishna.todo.ui.home.TaskRow
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.home.rememberToday
import dev.saikrishna.todo.ui.home.rowMeta
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.PillSpec
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure: case-insensitive match on title, notes or tags; open tasks first (oldest first), then done
 * (newest first). Kept for v0.7 callers; v0.8 search uses [filterTasks] with a full [ViewFilter].
 */
fun searchTasks(tasks: List<Task>, query: String, tag: String? = null, listId: Long? = null, today: LocalDate = LocalDate.now()): List<Task> =
    filterTasks(tasks, query, ViewFilter(listId = listId, tag = tag), today)

/** Gap between the docked pill and the keyboard / navigation bar. */
val SearchDockGap = 10.dp
/** Gap between the chips/results and the pill. */
private val ChipsGap = 8.dp
private val CloseSize = 34.dp
private val IconInset = 14.dp

/** Ramps: 0 below [from], eased to 1 at 1 (C¹). */
private fun ramp(k: Float, from: Float): Float = smoothstep((k - from) / (1f - from))

/**
 * v0.11 in-place search over Home (replaces the pushed SearchScreen). Composed only while
 * [SearchMorph.active]. Layers, bottom to top: the frost (+ the tap-to-close shield), the
 * results/empty state with the chips just above the pill, and the morphing pill itself.
 *
 * IME: the pill is laid out from the LIVE ime inset every frame (a tiny node). The results region
 * is laid out once from the ime animation TARGET and only translated (draw phase) by the
 * difference, so it stays docked to the pill without re-laying-out the list per keyboard frame.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(vm: TodoViewModel, morph: SearchMorph, hazeState: HazeState?, onOpenTask: (Long) -> Unit) {
    val active by remember(morph) { derivedStateOf { morph.active } }
    if (!active) return
    val c = AppTheme.colors
    val type = AppTheme.type
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val reduced = morph.reduced

    // ---- data (same behaviour as the v0.8 SearchScreen) ----------------------------------------
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val today = rememberToday()
    val query = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val all = tasks.orEmpty()
    val filter by vm.searchFilter.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val views by vm.views.collectAsStateWithLifecycle()
    val f = filter.filter
    val results by remember(all, f, today) { derivedStateOf { filterTasks(all, query.text.toString(), f, today) } }
    // A saved view opened from the lists panel brings its text.
    LaunchedEffect(filter.query) {
        val q = filter.query ?: return@LaunchedEffect
        query.setTextAndPlaceCursorAtEnd(q)
        vm.setSearchFilter(filter.copy(query = null))
    }
    fun setF(n: ViewFilter) { haptics.tick(); vm.setSearchFilter(filter.copy(filter = n)) }
    var naming by remember { mutableStateOf(false) }
    val allTags = remember(all) { all.flatMap { it.tagList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val org = remember(lists, subtasks) { OrgView(lists = lists.associateBy { it.id }, showList = true, subtasks = subtasks) }
    val hasFilter = !f.isEmpty
    // Autofocus as the morph starts (the pill rides the keyboard up); not when opened pre-filtered
    // (tag pill, saved view): then the results are the point, so don't cover them with the IME.
    LaunchedEffect(morph.target) {
        if (morph.target && !morph.prefiltered) { withFrameNanos { }; focus.requestFocus() }
        if (!morph.target) naming = false
    }

    // ---- geometry ---------------------------------------------------------------------------
    val imeLive = WindowInsets.ime
    val imeTarget = WindowInsets.imeAnimationTarget
    val nav = WindowInsets.navigationBars
    fun live(): Int = SearchTestHooks.ime?.live?.intValue ?: imeLive.getBottom(density)
    fun target(): Int = SearchTestHooks.ime?.target?.intValue ?: imeTarget.getBottom(density)
    val gapPx = with(density) { SearchDockGap.roundToPx() }
    fun dock(ime: Int): Int = max(ime, nav.getBottom(density)) + gapPx
    val pillH = with(density) { PillSpec.Height.roundToPx() }
    val sidePx = with(density) { Spacing.screen.roundToPx() }
    val chipsGapPx = with(density) { ChipsGap.roundToPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val box = remember { IntArray(2) } // overlay size, written in layout
    fun geomK(): Float = if (reduced) 1f else morph.k.coerceIn(0f, 1f)
    fun pillRect(w: Int, h: Int): Rect {
        val bottom = (h - dock(live())).toFloat()
        val end = Rect(sidePx.toFloat(), bottom - pillH, (w - sidePx).toFloat(), bottom)
        val start = morph.circle.takeIf { it != Rect.Zero }?.translate(-origin) ?: return end
        return lerp(start, end, geomK())
    }
    // Contents of the pill appear over its second half; the frost and the rest follow progress.
    fun contentsA(): Float = if (reduced) morph.k else ramp(morph.k, 0.45f)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("search-overlay")
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Shield: every touch that reaches here (the frost, empty areas) is ours, never Home's;
            // a tap closes. Children (pill, chips, rows) consume their own taps first.
            .pointerInput(morph) { detectTapGestures(onTap = { morph.close() }) },
    ) {
        val finalW = maxWidth - Spacing.screen * 2
        // 1. Frost over Home (constant blur, fading in with progress).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = morph.k.coerceIn(0f, 1f) }
                .searchFrost(hazeState),
        )
        // 2. Results / empty state + chips, docked above the pill.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .layout { m, cons ->
                    SearchTestHooks.resultsLayouts++
                    val bottom = dock(target()) + pillH + chipsGapPx
                    val hgt = (cons.maxHeight - bottom).coerceAtLeast(0)
                    val p = m.measure(Constraints(0, cons.maxWidth, hgt, hgt))
                    layout(cons.maxWidth, cons.maxHeight) { p.place(0, 0) }
                }
                .graphicsLayer {
                    translationY = (dock(target()) - dock(live())).toFloat()
                    alpha = if (reduced) morph.k else ramp(morph.k, 0.5f)
                },
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val q = query.text
                if (q.isBlank() && !hasFilter) {
                    // Faint centred empty state, rising/scaling in a touch.
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                val e = if (reduced) 1f else ramp(morph.k, 0.5f)
                                val s = 0.94f + 0.06f * e
                                scaleX = s; scaleY = s
                                translationY = (1f - e) * 12.dp.toPx()
                            }
                            .testTag("search-empty"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(AppIcons.Search, null, tint = c.inkTertiary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Search your tasks", style = type.title, color = c.inkSecondary)
                    }
                } else if (results.isEmpty()) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No matches", style = type.title, color = c.ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (q.isBlank()) "Nothing matches this filter." else "Nothing matches “${q.trim()}”.",
                            style = type.meta, color = c.inkSecondary,
                        )
                    }
                } else {
                    // Results fill in upward from the bar (the best match sits right above it).
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("search-results"),
                        reverseLayout = true,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 4.dp),
                    ) {
                        items(results, key = { it.id }, contentType = { "row" }) { task ->
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
                                    onOpenTask(task.id)
                                },
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = Tweens.fadeIn(0),
                                        placementSpec = Springs.Snappy.intOffset,
                                        fadeOutSpec = Tweens.fadeOut(),
                                    )
                                    .padding(top = Spacing.rowGap),
                            )
                        }
                    }
                }
            }
            // Chips + saved views: they wait for the pill to land below them (k ≥ 0.8).
            Column(Modifier.graphicsLayer { alpha = if (reduced) 1f else ramp(morph.k, 0.8f) }) {
            // v0.8 saved views (tap = apply, again = clear) + Save / Delete view.
            val qText = query.text.toString().trim()
            val activeView = views.firstOrNull { it.query == qText && ViewFilter.decode(it.filter) == f && (qText.isNotEmpty() || !f.isEmpty) }
            if (naming) {
                SaveViewRow(
                    suggested = suggestViewName(qText, f, lists),
                    onSave = { name -> vm.saveView(name, qText, f); naming = false },
                    onCancel = { naming = false },
                )
            }
            if (views.isNotEmpty() || qText.isNotEmpty() || hasFilter) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (v in views) {
                        FilterChip(label = v.name, dot = null, selected = activeView?.id == v.id, icon = AppIcons.Bookmark) {
                            haptics.tick()
                            if (activeView?.id == v.id) { query.clearText(); vm.setSearchFilter(SearchFilter()) }
                            else { query.setTextAndPlaceCursorAtEnd(v.query); vm.setSearchFilter(SearchFilter(ViewFilter.decode(v.filter))) }
                        }
                    }
                    if (activeView != null) {
                        ActionChip("Delete view") { haptics.tick(); vm.deleteView(activeView.id); query.clearText(); vm.setSearchFilter(SearchFilter()) }
                    } else if (qText.isNotEmpty() || hasFilter) {
                        ActionChip("Save view") { haptics.tick(); naming = true }
                    }
                }
            }
            // Filters in one scrolling row: lists, tags in use, then status / due / priority /
            // reminder / recurring (all combine).
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(top = 6.dp).testTag("search-chips"),
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
            }
        }

        // 3. The pill: the header circle flying to the bottom and widening (stadium throughout).
        val iconPx = with(density) { 20.dp.roundToPx() }
        val insetPx = with(density) { IconInset.roundToPx() }
        val fieldX = IconInset + 20.dp + 10.dp
        Box(
            Modifier
                .layout { m, cons ->
                    box[0] = cons.maxWidth; box[1] = cons.maxHeight
                    val r = pillRect(cons.maxWidth, cons.maxHeight)
                    val w = r.width.roundToInt().coerceAtLeast(1); val h = r.height.roundToInt().coerceAtLeast(1)
                    val p = m.measure(Constraints.fixed(w, h))
                    layout(cons.maxWidth, cons.maxHeight) {
                        val x = r.left.roundToInt(); val y = r.top.roundToInt()
                        SearchTestHooks.pill = Rect(x + origin.x, y + origin.y, x + origin.x + w, y + origin.y + h)
                        p.place(x, y)
                    }
                }
                .graphicsLayer { alpha = if (reduced) morph.k.coerceIn(0f, 1f) else 1f }
                .glassControl(Shapes.full)
                .clip(Shapes.full)
                .testTag("search-pill")
                .pointerInput(Unit) { detectTapGestures { if (morph.target) focus.requestFocus() } },
        ) {
            // Magnifier: from the circle's centre to the left edge.
            Icon(
                AppIcons.Search, null, tint = c.ink,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val w = pillRect(box[0], box[1]).width
                        val centre = (w - iconPx) / 2f
                        IntOffset((centre + (insetPx - centre) * geomK()).roundToInt(), 0)
                    }
                    .size(20.dp),
            )
            // The field keeps its final width (never reflows mid-flight); the pill's clip reveals it.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = fieldX)
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(finalW - fieldX - CloseSize - 12.dp)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = contentsA() },
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    state = query,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Search" },
                    textStyle = type.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = { keyboard?.hide() },
                    decorator = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.text.isEmpty()) Text("Search", style = type.body, color = c.inkTertiary)
                            inner()
                        }
                    },
                )
            }
            // Round light close button.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 7.dp)
                    .size(CloseSize)
                    .graphicsLayer { alpha = contentsA() }
                    .clip(Shapes.full)
                    .background(if (c.isDark) c.inkFill else c.glassSolid)
                    .clickable(role = Role.Button, onClickLabel = "Close search") { morph.close() }
                    .semantics { contentDescription = "Close search" }
                    .testTag("search-close"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Close, null, tint = c.ink, modifier = Modifier.size(15.dp))
            }
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
    val fill by animateStateColor(selected, c.ink, c.glassFill, Tweens.select(), label = "filter fill")
    val text by animateStateColor(selected, c.onInk, c.ink, Tweens.select(), label = "filter text")
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
