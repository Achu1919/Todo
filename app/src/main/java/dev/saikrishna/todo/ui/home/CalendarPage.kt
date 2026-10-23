package dev.saikrishna.todo.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.domain.CalendarData
import dev.saikrishna.todo.ui.components.CircleIconButton
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.SpringToken
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** v0.13 Calendar page constants. Our own numbers. */
object CalendarSpec {
    val RowHeight = 46.dp
    val CellDot = 4.dp
    /** Week strip <-> month grid. */
    val PullSpring = SpringToken(0.40f, 0.82f)
    /** Week / month swipe. */
    val PageSpring = SpringToken(0.34f, 1.0f)
    const val ProjectedAlpha = 0.45f
    const val OutsideMonthAlpha = 0.32f
    /** Fraction of the width a horizontal swipe must travel (or a fling) to turn the page. */
    const val SwipeTurn = 0.22f
    const val FlingVelocity = 700f // px/s
}

/** Expand fraction (0 = week strip, 1 = month grid) and page slide, hoisted so tests can drive them. */
@Stable
class CalendarUiState(initialExpanded: Boolean) {
    val expand = Animatable(if (initialExpanded) 1f else 0f)
    val slide = Animatable(0f)
    var dragTask by mutableStateOf<Task?>(null)
    var dragPos by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<LocalDate?>(null)
    internal val cells = HashMap<LocalDate, Rect>()

    val expanded: Boolean get() = expand.targetValue > 0.5f

    fun settle(scope: CoroutineScope, open: Boolean, reduced: Boolean) = scope.launch {
        if (reduced) expand.snapTo(if (open) 1f else 0f) else expand.animateTo(if (open) 1f else 0f, CalendarSpec.PullSpring.float)
    }

    fun dayAt(pos: Offset): LocalDate? = cells.entries.firstOrNull { it.value.contains(pos) }?.key
}

/** Test hook: the live calendar state (filmstrips drive the pull through it). */
object CalendarTestHooks { var state: CalendarUiState? = null }

/** Next/previous page from [selected]: a week when collapsed, a month (day clamped) when expanded. */
fun calendarTurn(selected: LocalDate, expanded: Boolean, dir: Int): LocalDate =
    if (expanded) selected.plusMonths(dir.toLong()) else selected.plusWeeks(dir.toLong())

@Composable
fun CalendarPage(
    tasks: List<Task>,
    org: OrgView,
    today: LocalDate,
    zone: ZoneId,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
    themeButton: @Composable () -> Unit,
    onToggle: (Task, Boolean) -> Unit,
    onOpen: (Task) -> Unit,
    onMove: (Task, LocalDate) -> Unit,
    onAddOn: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    locale: Locale = Locale.getDefault(),
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var savedExpanded by rememberSaveable { mutableStateOf(false) }
    val ui = remember { CalendarUiState(savedExpanded) }
    DisposableEffect(ui) { CalendarTestHooks.state = ui; onDispose { if (CalendarTestHooks.state === ui) CalendarTestHooks.state = null } }
    savedExpanded = ui.expanded

    val month = YearMonth.from(selected)
    val grid = remember(month) { CalendarData.monthGrid(month) }
    val rows = grid.size / 7
    val visibleTasks = remember(tasks, org) { tasks.filter(org::shows) }
    val days = remember(visibleTasks, grid, today, zone) { CalendarData.build(visibleTasks, grid.first(), grid.last(), today, zone) }
    val selRow = (grid.indexOf(selected).coerceAtLeast(0)) / 7
    val density = LocalDensity.current
    val rowPx = with(density) { CalendarSpec.RowHeight.toPx() }

    fun turn(dir: Int) {
        val next = calendarTurn(selected, ui.expanded, dir)
        scope.launch {
            val w = with(density) { 400.dp.toPx() }
            if (reduced) { onSelect(next); ui.slide.snapTo(0f); return@launch }
            ui.slide.animateTo(-dir * w * 0.6f, CalendarSpec.PageSpring.float)
            onSelect(next)
            ui.slide.snapTo(dir * w * 0.6f)
            ui.slide.animateTo(0f, CalendarSpec.PageSpring.float)
        }
        haptics.tick()
    }

    Box(modifier.fillMaxSize()) {
        var rootOffset by remember { mutableStateOf(Offset.Zero) }
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOffset = it.positionInRoot() }
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            // Month title, Today, add, theme.
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, locale) + if (month.year != today.year) " ${month.year}" else "",
                    style = AppTheme.type.title, color = c.ink, modifier = Modifier.weight(1f).testTag("calendar-title"),
                )
                if (selected != today) {
                    Text(
                        "Today",
                        style = AppTheme.type.label, color = c.ink,
                        modifier = Modifier
                            .clip(Shapes.full)
                            .glassSurface(Shapes.full, GlassLevel.Low)
                            .clickable(role = Role.Button, onClickLabel = "Go to today") { haptics.tick(); onSelect(today) }
                            .testTag("calendar-today")
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                CircleIconButton(AppIcons.Plus, "Add a task on ${calDayLabel(selected, today, locale)}", { haptics.tick(); onAddOn(selected) }, Modifier.testTag("calendar-add"))
                Spacer(Modifier.width(8.dp))
                themeButton()
            }
            // Weekday row.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (d in grid.take(7)) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        style = AppTheme.type.meta, color = c.inkTertiary, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    )
                }
            }
            // The grid: one week row when collapsed, every row when expanded.
            val k = ui.expand.value
            val gridHeight = CalendarSpec.RowHeight * (1f + (rows - 1) * k)
            val touchSlop = LocalViewConfiguration.current.touchSlop
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .clipToBounds()
                    .testTag("calendar-grid")
                    .pointerInput(rows, selected) {
                        // Axis-locked drag: vertical pulls the month open/closed, horizontal turns the page.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val vt = VelocityTracker()
                            var axis = 0 // 0 undecided, 1 vertical, 2 horizontal
                            var total = Offset.Zero
                            val range = rowPx * (rows - 1)
                            vt.addPosition(down.uptimeMillis, down.position)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val delta = ch.positionChange()
                                total += delta
                                vt.addPosition(ch.uptimeMillis, ch.position)
                                if (axis == 0 && total.getDistance() > touchSlop) axis = if (abs(total.y) > abs(total.x)) 1 else 2
                                if (axis == 1) {
                                    ch.consume()
                                    val v = (ui.expand.value + delta.y / range).coerceIn(0f, 1f)
                                    scope.launch { ui.expand.snapTo(v) }
                                } else if (axis == 2) {
                                    ch.consume()
                                    scope.launch { ui.slide.snapTo(total.x * 0.9f) }
                                }
                            }
                            val vel = vt.calculateVelocity()
                            when (axis) {
                                1 -> {
                                    val open = when {
                                        vel.y > CalendarSpec.FlingVelocity -> true
                                        vel.y < -CalendarSpec.FlingVelocity -> false
                                        else -> ui.expand.value > 0.5f
                                    }
                                    haptics.tick()
                                    ui.settle(scope, open, reduced)
                                }
                                2 -> {
                                    val turnDir = when {
                                        total.x < -size.width * CalendarSpec.SwipeTurn || vel.x < -CalendarSpec.FlingVelocity * 1.5f -> 1
                                        total.x > size.width * CalendarSpec.SwipeTurn || vel.x > CalendarSpec.FlingVelocity * 1.5f -> -1
                                        else -> 0
                                    }
                                    if (turnDir != 0) turn(turnDir) else scope.launch { ui.slide.animateTo(0f, CalendarSpec.PageSpring.float) }
                                }
                            }
                        }
                    },
            ) {
                val yShift = -(selRow * rowPx * (1f - k))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .graphicsLayer {
                            translationY = yShift
                            translationX = ui.slide.value
                            alpha = 1f - (abs(ui.slide.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.6f)
                        },
                ) {
                    for (r in 0 until rows) {
                        val rowAlpha = if (r == selRow) 1f else k
                        Row(Modifier.fillMaxWidth().height(CalendarSpec.RowHeight).graphicsLayer { alpha = rowAlpha }) {
                            for (d in grid.subList(r * 7, r * 7 + 7)) {
                                val info = days[d]
                                DayCell(
                                    date = d,
                                    info = info,
                                    org = org,
                                    today = today,
                                    selected = d == selected,
                                    hovered = ui.hover == d,
                                    outside = YearMonth.from(d) != month && k > 0.02f,
                                    onTap = {
                                        haptics.tick()
                                        onSelect(d)
                                        // An empty day (today or later) goes straight to adding a task on it.
                                        if ((info == null || info.entries.isEmpty()) && !d.isBefore(today)) onAddOn(d)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { if (r == selRow || k > 0.5f) ui.cells[d] = it.boundsInRoot() else ui.cells.remove(d) },
                                    locale = locale,
                                )
                            }
                        }
                    }
                }
            }
            // Grab handle: tap toggles; it also hints that the strip pulls down.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clickable(role = Role.Button, onClickLabel = if (ui.expanded) "Show week" else "Show month") {
                        haptics.tick(); ui.settle(scope, !ui.expanded, reduced)
                    }
                    .testTag("calendar-handle"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(Shapes.full).background(c.inkHairline))
            }

            // The selected day's list.
            val entries = days[selected]?.entries.orEmpty()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("calendar-day-list"),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 8.dp),
            ) {
                item(key = "day-head", contentType = "head") {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(calDayLabel(selected, today, locale), style = AppTheme.type.label, color = c.inkSecondary, modifier = Modifier.weight(1f))
                        val open = entries.count { !it.task.done && !it.projected }
                        if (open > 0) Text("$open open", style = AppTheme.type.meta, color = c.inkTertiary)
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "day-empty", contentType = "empty") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(Shapes.lg)
                                .glassSurface(Shapes.lg, GlassLevel.Low, c.glassCard)
                                .clickable(role = Role.Button, onClickLabel = "Add a task") { haptics.tick(); onAddOn(selected) }
                                .testTag("calendar-empty")
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Nothing on this day", style = AppTheme.type.body, color = c.inkSecondary, modifier = Modifier.weight(1f))
                            Icon(AppIcons.Plus, null, tint = c.inkSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add", style = AppTheme.type.label, color = c.ink)
                        }
                    }
                }
                items(entries, key = { "${it.task.id}-${it.day}-${it.projected}" }, contentType = { "row" }) { e ->
                    val meta = remember(e.task, today, org) { rowMeta(e.task, today, showDue = false, locale, org) }
                    var rowRoot by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .graphicsLayer { alpha = if (e.projected) CalendarSpec.ProjectedAlpha else if (ui.dragTask?.id == e.task.id) 0.3f else 1f }
                            .onGloballyPositioned { rowRoot = it.positionInRoot() }
                            .then(
                                if (e.projected || e.task.done) Modifier
                                else Modifier.longPressDrag(
                                    onStart = { p -> haptics.thump(); ui.dragTask = e.task; ui.dragPos = rowRoot + p; ui.hover = null },
                                    onMove = { p ->
                                        ui.dragPos = rowRoot + p
                                        val h = ui.dayAt(ui.dragPos)
                                        if (h != ui.hover) { ui.hover = h; if (h != null) haptics.tick() }
                                    },
                                    onEnd = {
                                        val target = ui.hover
                                        val t = ui.dragTask
                                        ui.dragTask = null; ui.hover = null
                                        if (t != null && target != null && CalendarData.reschedule(t, target) != null) {
                                            haptics.rise(); onMove(t, target); onSelect(target)
                                        }
                                    },
                                ),
                            )
                            .semantics { if (e.projected) contentDescription = "${e.task.title}, upcoming repeat" },
                    ) {
                        TaskRow(
                            task = e.task,
                            meta = meta,
                            onCheckedChange = { if (!e.projected) onToggle(e.task, it) },
                            onClick = { onOpen(e.task) },
                            enabled = !e.projected,
                        )
                    }
                }
            }
        }

        // Drag ghost: follows the finger in root coordinates.
        val dragging = ui.dragTask
        if (dragging != null) {
            val p = ui.dragPos - rootOffset
            Box(
                Modifier
                    .offset { IntOffset((p.x - 60.dp.toPx()).roundToInt(), (p.y - 56.dp.toPx()).roundToInt()) }
                    .widthIn(max = 220.dp)
                    .glassSurface(Shapes.full, GlassLevel.High, c.glassSolid)
                    .testTag("calendar-ghost")
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    ui.hover?.let { "${dragging.title} → ${it.format(DateTimeFormatter.ofPattern("EEE d", locale))}" } ?: dragging.title,
                    style = AppTheme.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    info: CalendarData.Day?,
    org: OrgView,
    today: LocalDate,
    selected: Boolean,
    hovered: Boolean,
    outside: Boolean,
    onTap: () -> Unit,
    modifier: Modifier,
    locale: Locale,
) {
    val c = AppTheme.colors
    val isToday = date == today
    Column(
        modifier
            .fillMaxSize()
            .clip(Shapes.md)
            .clickable(role = Role.Button, onClickLabel = "Select") { onTap() }
            .graphicsLayer { alpha = if (outside) CalendarSpec.OutsideMonthAlpha else 1f }
            .semantics {
                this.selected = selected
                contentDescription = buildString {
                    append(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)))
                    if (isToday) append(", today")
                    val n = info?.entries?.count { !it.task.done } ?: 0
                    if (n > 0) append(", $n tasks")
                    if (info?.overdue == true) append(", overdue")
                }
            }
            .testTag("cal-${date}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(Shapes.full)
                    .then(
                        when {
                            selected -> Modifier.background(c.ink)
                            hovered -> Modifier.background(c.accent.copy(alpha = 0.22f))
                            isToday -> Modifier.border(1.5.dp, c.accent, Shapes.full)
                            else -> Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    style = AppTheme.type.body.copy(fontWeight = if (isToday || selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = when {
                        selected -> c.onInk
                        isToday -> c.accent
                        date.isBefore(today) -> c.inkSecondary
                        else -> c.ink
                    },
                )
            }
            if (info?.overdue == true) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-1).dp)
                        .size(7.dp)
                        .clip(Shapes.full)
                        .background(c.danger)
                        .testTag("cal-overdue-${date}"),
                )
            }
        }
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (id in info?.dotLists.orEmpty()) {
                val colour = org.lists[id]?.let { c.listColor(it.color) } ?: c.inkTertiary
                Box(Modifier.size(CalendarSpec.CellDot).clip(Shapes.full).background(colour))
            }
        }
    }
}

private fun calDayLabel(d: LocalDate, today: LocalDate, locale: Locale): String {
    val base = d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    return when (d) {
        today -> "Today · $base"
        today.plusDays(1) -> "Tomorrow · $base"
        today.minusDays(1) -> "Yesterday · $base"
        else -> base
    }
}

/**
 * Long-press then drag, without fighting the row's own tap or the list's scroll: it waits on the
 * Initial pass, and once the long press fires it consumes every change so neither sees the drag.
 */
private fun Modifier.longPressDrag(onStart: (Offset) -> Unit, onMove: (Offset) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val timeout = viewConfiguration.longPressTimeoutMillis
            var moved = false
            val fired = withTimeoutOrNull(timeout) {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                    if (!ch.pressed || ch.isConsumed) return@withTimeoutOrNull false
                    if ((ch.position - down.position).getDistance() > slop) { moved = true; return@withTimeoutOrNull false }
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            if (fired != null || moved) return@awaitEachGesture
            onStart(down.position)
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                if (!ch.pressed) break
                onMove(ch.position)
            }
            onEnd()
        }
    }
