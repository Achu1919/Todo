package dev.saikrishna.todo.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import dev.saikrishna.todo.ui.icons.AppIcons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.ui.components.liftShadow
import dev.saikrishna.todo.ui.motion.Depth
import dev.saikrishna.todo.ui.motion.LaunchEntrance
import dev.saikrishna.todo.ui.motion.LocalLaunchEntrance
import dev.saikrishna.todo.ui.motion.launchEntrance
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.panel.panelAnchor
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Row callbacks for a page, bundled so list rows take one stable parameter. */
@Stable
class RowActions(
    val onToggle: (task: Task, checked: Boolean, page: Filter) -> Unit,
    val onOpen: (Task) -> Unit,
    val onSwipeDelete: (Task) -> Unit,
    val onDeleteCollapsed: (Task) -> Unit,
    val onClearDone: () -> Unit,
    /** Card bounds for the floating panel's container transform. */
    val anchors: dev.saikrishna.todo.ui.panel.PanelAnchors = dev.saikrishna.todo.ui.panel.PanelAnchors(),
    /** Swipe right on an open task (v0.6): open the snooze picker. Null disables the gesture. */
    val onSnooze: ((task: Task, page: Filter) -> Unit)? = null,
    /** v0.7: a finished drag (manual order / new day). Null disables drag to reorder. */
    val onDrop: ((outcome: DropOutcome, movedId: Long) -> Unit)? = null,
    /** v0.7: tag pill tapped. */
    val onTag: ((String) -> Unit)? = null,
    /** v0.7.3: a drag to reorder started (true) / stopped (false); the composer never collapses mid-drag. */
    val onDragging: ((Boolean) -> Unit)? = null,
    /** v0.13 Recurring view: pause / resume / stop a task's repeat. Null hides the controls. */
    val onRepeat: ((task: Task, action: RepeatAction) -> Unit)? = null,
)

/** v0.13 Recurring view row controls. */
enum class RepeatAction { Pause, Resume, Stop }

/**
 * One page of the home list. Stable keys + contentType, every item placed with animateItem
 * (Snappy placement, 220 ms fade-in, 150 ms fade-out). Rows collapse (6.9) when leaving or deleted.
 *
 * v0.7 drag to reorder (sh.calvin.reorderable on this LazyColumn): long-press a row to lift it
 * (scale 1.03 + Mid shadow, tick), drag (tick per slot, auto-scroll near the chrome edges), drop.
 * While dragging, the page shows a local order; on drop [dropOutcome] is applied optimistically
 * by the ViewModel, and the local order is dropped once the data agrees. On Upcoming the day
 * headers are drop targets: a row moved under another day's header is rescheduled to that day.
 */
@Composable
fun TaskListPage(
    page: Filter,
    items: List<ListItem>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    leave: LeaveController,
    deleting: Map<Long, Boolean>,
    tracker: EntranceTracker,
    actions: RowActions,
    heading: String,
    caption: String?,
    modifier: Modifier = Modifier,
    /** v0.7: list selector / focus indicator under the heading, and tapping the heading. */
    headingExtra: @Composable () -> Unit = {},
    /** v0.12: end of the heading's title row (the theme toggle). */
    headingTrailing: @Composable () -> Unit = {},
) {
    val haptics = LocalHaptics.current
    var local by remember { mutableStateOf<List<ListItem>?>(null) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var drops by remember { mutableIntStateOf(0) }
    val latestItems by rememberUpdatedState(items)
    // Keep the dragged order until the data shows the same rows in the same order (or 1.5 s passed).
    LaunchedEffect(items, dragging, local) {
        val l = local ?: return@LaunchedEffect
        if (dragging == null && rowKeys(l) == rowKeys(items)) local = null
    }
    LaunchedEffect(drops) { if (drops > 0) { delay(1_500); if (dragging == null) local = null } }

    val shown = local ?: items
    val reorder = rememberReorderableLazyListState(listState, scrollThresholdPadding = contentPadding) { from, to ->
        val cur = local ?: latestItems
        val f = cur.indexOfFirst { it.key == from.key }
        val t = cur.indexOfFirst { it.key == to.key }
        if (f >= 0 && t >= 0 && f != t) {
            local = cur.moved(f, t)
            haptics.tick() // one tick per slot change
        }
    }
    val canDrag = actions.onDrop != null && page != Filter.Done

    val launch = LocalLaunchEntrance.current
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        // Serif page heading (index 0), then the page items.
        item(key = "heading", contentType = "heading") {
            // v0.10 cold-start entrance: the heading is row 0, page items follow (draw phase only).
            Box(Modifier.launchEntrance(launch, LaunchEntrance.Slot.Row(0))) {
                PageHeading(heading, caption, trailing = headingTrailing, extra = headingExtra)
            }
        }
        itemsIndexed(shown, key = { _, it -> it.key }, contentType = { _, it -> it.contentType }) { index, item ->
            val rise = Modifier.launchEntrance(launch, LaunchEntrance.Slot.Row(index + 1))
            if (canDrag && reorderable(page, item)) {
                ReorderableItem(reorder, key = item.key, animateItemModifier = placedSpec(rise)) { isDragging ->
                    when (item) {
                        is ListItem.Row -> {
                            val handle = Modifier.longPressDraggableHandle(
                                enabled = leave.phase(item.task.id) == null && item.task.id !in deleting,
                                onDragStarted = {
                                    local = latestItems
                                    dragging = item.task.id
                                    actions.onDragging?.invoke(true)
                                    haptics.tick() // pickup
                                },
                                onDragStopped = {
                                    val order = local
                                    dragging = null
                                    actions.onDragging?.invoke(false)
                                    if (order != null) {
                                        actions.onDrop?.invoke(dropOutcome(order, item.task.id, page), item.task.id)
                                        drops++
                                    }
                                },
                            )
                            TaskListRow(item, page, leave, item.task.id in deleting, tracker, actions, handle, isDragging, Modifier)
                        }
                        is ListItem.DayHeader -> DayHeaderRow(item.label)
                        else -> Unit
                    }
                }
            } else {
                when (item) {
                    is ListItem.Row -> TaskListRow(item, page, leave, item.task.id in deleting, tracker, actions, Modifier, false, placedSpec(rise))
                    is ListItem.DayHeader -> DayHeaderRow(item.label, placedSpec(rise))
                    is ListItem.AllClear -> AllClear(item.doneToday, placedSpec(rise))
                    is ListItem.Empty -> EmptyMessage(item.title, item.body, placedSpec(rise))
                    is ListItem.ClearDone -> ClearDoneRow(item.count, actions.onClearDone, placedSpec(rise))
                }
            }
        }
    }
}

private fun rowKeys(items: List<ListItem>): List<String> = items.filterIsInstance<ListItem.Row>().map { it.key }

private fun LazyItemScope.placedSpec(then: Modifier = Modifier): Modifier = Modifier.animateItem(
    fadeInSpec = Tweens.fadeIn(0),
    placementSpec = Springs.Snappy.intOffset,
    fadeOutSpec = Tweens.fadeOut(),
).then(then)

@Composable
private fun TaskListRow(
    item: ListItem.Row,
    page: Filter,
    leave: LeaveController,
    isDeleting: Boolean,
    tracker: EntranceTracker,
    actions: RowActions,
    dragHandle: Modifier,
    isDragging: Boolean,
    placement: Modifier,
) {
    val task = item.task
    val phase = leave.phase(task.id)
    val busy = phase == LeavePhase.Collapsing || isDeleting
    val reduced = LocalReducedMotion.current
    val c = AppTheme.colors
    // Lift: 0 → 1 while dragged (scale 1.03 + shadow), read only in the draw phase.
    val lift = remember { Animatable(0f) }
    LaunchedEffect(isDragging) {
        val target = if (isDragging) 1f else 0f
        if (reduced) lift.snapTo(target) else lift.animateTo(target, Springs.Quick.float)
    }
    CollapsingItem(
        removing = busy,
        onRemoved = { if (isDeleting) actions.onDeleteCollapsed(task) else leave.finish(task.id) },
        modifier = placement,
    ) {
        NewTaskEntrance(task.id, tracker) { entrance ->
            // Row gap lives inside the collapsing item so it collapses with the row.
            Box(Modifier.padding(bottom = Spacing.rowGap).then(entrance)) {
                Box(
                    Modifier
                        .graphicsLayer {
                            val s = 1f + (Depth.LiftScale - 1f) * lift.value
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin.Center
                        }
                        .liftShadow(Shapes.row, c.glassShadow) { lift.value },
                ) {
                    SwipeToDeleteRow(
                        enabled = phase == null && !isDeleting && !isDragging,
                        onDelete = { actions.onSwipeDelete(task) },
                        onSnooze = actions.onSnooze?.takeIf { !task.done }?.let { snooze -> { snooze(task, page) } },
                    ) {
                        TaskRow(
                            task = task,
                            meta = item.meta,
                            onCheckedChange = { checked -> actions.onToggle(task, checked, page) },
                            onClick = { actions.onOpen(task) },
                            enabled = !busy,
                            onTag = actions.onTag,
                            trailing = actions.onRepeat?.takeIf { page == Filter.Recurring && !task.done }?.let { act ->
                                { RepeatControls(paused = task.repeatPaused, title = task.title) { act(task, it) } }
                            },
                            // The handle sits INSIDE the swipe box, so once a drag starts it consumes
                            // the pointer before the horizontal swipe can claim it.
                            modifier = dragHandle.panelAnchor(actions.anchors, task.id),
                        )
                    }
                }
            }
        }
    }
}

/**
 * New-task entrance (spec 6.4): rises 24 dp and scales 0.94 → 1 from bottom-centre on Snappy,
 * while animateItem fades it in. Values are read only in the graphicsLayer (draw phase).
 * v0.7: template rows enter staggered ([EntranceTracker.delayFor]); they stay transparent until
 * their turn.
 */
@Composable
fun NewTaskEntrance(id: Long, tracker: EntranceTracker, content: @Composable (Modifier) -> Unit) {
    val reduced = LocalReducedMotion.current
    val claimed = remember(id) { tracker.claimEntrance(id) }
    val enter = remember(id) { Animatable(if (claimed) 0f else 1f) }
    val wait = remember(id) { if (claimed) tracker.delayFor(id) else 0 }
    LaunchedEffect(id) {
        if (enter.value < 1f) {
            if (wait > 0) delay(wait.toLong())
            if (reduced) enter.snapTo(1f) else enter.animateTo(1f, Springs.Snappy.float)
        }
    }
    val layer = remember(enter) {
        Modifier.graphicsLayer {
            val p = enter.value
            if (p < 1f) {
                translationY = (1f - p) * Depth.NewTaskOffset.toPx()
                val s = Depth.NewTaskScale + (1f - Depth.NewTaskScale) * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
                if (wait > 0) alpha = p.coerceIn(0f, 1f)
            }
        }
    }
    content(layer)
}

/** v0.13: pause/resume + stop, two quiet 36 dp glyph buttons at the end of a Recurring row. */
@Composable
private fun RepeatControls(paused: Boolean, title: String, onAction: (RepeatAction) -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    androidx.compose.foundation.layout.Row {
        for (a in listOf(if (paused) RepeatAction.Resume else RepeatAction.Pause, RepeatAction.Stop)) {
            val label = when (a) { RepeatAction.Pause -> "Pause repeat"; RepeatAction.Resume -> "Resume repeat"; RepeatAction.Stop -> "Stop repeating" }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(Shapes.full)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "$label: $title") { haptics.tick(); onAction(a) }
                    .testTag("repeat-${a.name.lowercase()}"),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    when (a) { RepeatAction.Pause -> AppIcons.Pause; RepeatAction.Resume -> AppIcons.Play; RepeatAction.Stop -> AppIcons.Close },
                    contentDescription = label, tint = if (a == RepeatAction.Stop) c.inkTertiary else c.inkSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
