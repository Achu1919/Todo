package dev.saikrishna.todo.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.saikrishna.todo.ui.panel.SnoozeContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import dev.saikrishna.todo.ui.panel.panelAnchor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saikrishna.todo.ui.motion.LaunchEntrance
import dev.saikrishna.todo.ui.motion.LocalLaunchEntrance
import dev.saikrishna.todo.ui.motion.launchEntrance
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.ui.components.CircleIconButton
import dev.saikrishna.todo.ui.components.LocalHazeState
import androidx.compose.ui.platform.LocalDensity
import dev.saikrishna.todo.ui.motion.Depth
import dev.saikrishna.todo.ui.components.bottomFade
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.SideEffect
import dev.saikrishna.todo.ui.components.SegmentedPill
import dev.saikrishna.todo.ui.components.PillStyle
import dev.saikrishna.todo.ui.components.GlassEdgeTop
import dev.saikrishna.todo.ui.components.skyBackdrop
import dev.saikrishna.todo.ui.theme.PillSpec
import dev.saikrishna.todo.ui.theme.Spacing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import dev.saikrishna.todo.ui.components.rememberChromePadding
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.Durations
import dev.saikrishna.todo.ui.motion.LocalAnimatorScale
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.motion.completionDwellMillis
import dev.saikrishna.todo.ui.panel.PanelAnchors
import dev.saikrishna.todo.ui.panel.TaskPanelContent
import dev.saikrishna.todo.ui.panel.TaskPanelHost
import dev.saikrishna.todo.ui.panel.PanelStyle
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import dev.saikrishna.todo.ui.theme.AppTheme
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import dev.saikrishna.todo.domain.PlanLine
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.ui.theme.LocalThemeReveal
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.collectAsState
import dev.saikrishna.todo.ui.components.GlassLevel
import androidx.compose.foundation.layout.ime
import java.time.LocalDateTime
import java.time.ZoneId

/** Today's date; refreshed on resume and at midnight. */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    LaunchedEffect(today) {
        val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay()).toMillis()
        delay(untilMidnight.coerceAtLeast(0L) + 1_000L)
        today = LocalDate.now()
    }
    return today
}

/** What the floating panel shows: the task details, or the snooze picker after a swipe right. */
enum class PanelMode { Details, Snooze }

@Composable
fun HomeScreen(
    vm: TodoViewModel,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    /** v0.7: Settings > Lists / Templates, reached from the floating menus. */
    onOpenLists: () -> Unit = onOpenSettings,
    onOpenTemplates: () -> Unit = onOpenSettings,
    /**
     * Whether Home is the top of the stack (not covered by a pushed screen). An open request
     * (reminder tap) waits until it is, so the panel opens on a visible Home after the pop.
     */
    isTop: () -> Boolean = { true },
    /** v0.8 morning planning (list menu entry). */
    onOpenPlan: () -> Unit = {},
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val focusPrefs by vm.focusPrefs.collectAsStateWithLifecycle()
    val selectedList by vm.selectedList.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val views by vm.views.collectAsStateWithLifecycle()
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()

    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val today = rememberToday()
    val zone = remember { ZoneId.systemDefault() }

    var filter by rememberSaveable { mutableStateOf(Filter.Today) }
    var panelTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var panelMode by rememberSaveable { mutableStateOf(PanelMode.Details) }
    val anchors = remember { PanelAnchors() }
    val listStates = Filter.entries.associateWith { rememberLazyListState() }
    val composerState = rememberTextFieldState()
    val tracker = remember { EntranceTracker() }
    val deleting = remember { mutableStateMapOf<Long, Boolean>() }
    var pendingScroll by remember { mutableStateOf(false) }
    val topPx = remember { mutableIntStateOf(0) }
    val bottomPx = remember { mutableIntStateOf(0) }
    // v0.7.2: the last row comes to rest above the bottom fade (fade + 4 dp), not inside it.
    val padding = rememberChromePadding(topPx, bottomPx, extraBottom = Depth.BottomFade + 4.dp)
    val hazeState = rememberHazeState()

    val currentFilter by rememberUpdatedState(filter)
    val animatorScale by rememberUpdatedState(LocalAnimatorScale.current)
    fun onScreen(id: Long, page: Filter): Boolean =
        page == currentFilter && listStates.getValue(page).layoutInfo.visibleItemsInfo.any { it.key == rowKey(id) }
    val leave = remember {
        LeaveController(scope, { completionDwellMillis(animatorScale) }) { id, page -> onScreen(id, page) }
    }

    val all = tasks.orEmpty()
    if (tasks != null) tracker.baseline(all.map { it.id })
    // v0.10 cold-start entrance: starts with the first data frame (the splash holds until then).
    val launch = LocalLaunchEntrance.current
    val launchReduced = LocalReducedMotion.current
    LaunchedEffect(launch, tasks != null) { if (tasks != null) launch?.start(launchReduced) }
    // Latest values for callbacks remembered once (RowActions) so they never see stale data.
    val latestAll by rememberUpdatedState(all)
    val latestToday by rememberUpdatedState(today)
    // ---- v0.7 organisation: focus (re-evaluated every minute), selected list, visible lists ----
    val nowMinute by produceState(System.currentTimeMillis() / 60_000L) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
            value = System.currentTimeMillis() / 60_000L
        }
    }
    val focus = remember(focusPrefs, nowMinute) { vm.focusAt(System.currentTimeMillis()) }
    // A selected list outside the focus area falls back to "all lists of that area".
    val selected = selectedList?.takeIf { id -> lists.any { it.id == id && (focus.area == null || it.area == focus.area) } }
    val org = remember(lists, subtasks, selected, focus) {
        OrgView(
            lists = lists.associateBy { it.id },
            visible = when {
                selected != null -> setOf(selected)
                focus.area != null -> lists.filter { it.area == focus.area }.map { it.id }.toSet()
                else -> null
            },
            showList = selected == null && lists.size > 1,
            subtasks = subtasks,
        )
    }
    val chip = listChipState(lists, selected, focus)
    val planSummary = remember(all, today) { PlanLine.of(all, today) }
    val pages by remember(all, today, org) { derivedStateOf { buildPages(all, today, zone, leave.entries, org = org) } }
    val openCounts = remember(all) { all.filter { !it.done }.groupingBy { it.listId }.eachCount() }
    val menuAnchors = remember { PanelAnchors() }
    var menu by rememberSaveable { mutableStateOf<Long?>(null) }

    val homeOnTop by rememberUpdatedState(isTop)
    LaunchedEffect(openRequest) {
        val id = openRequest ?: return@LaunchedEffect
        // A reminder tapped while Settings/Search is open: AppNav pops back first (same pop as the
        // back arrow); the panel opens once Home is the top screen again.
        snapshotFlow { homeOnTop() }.first { it }
        menu = null
        // A task in a list hidden by the current selection/focus: show all lists so its card exists.
        val t = vm.tasks.value?.firstOrNull { it.id == id }
        if (t != null && !org.shows(t)) vm.selectList(null)
        panelMode = if (vm.openAsSnooze) PanelMode.Snooze else PanelMode.Details
        panelTaskId = id
        vm.consumeOpenRequest()
    }

    // Auto-scroll only if the newly added row lands off-screen (spec 6.4). It is the last Today row.
    val lastTodayRow = pages.today.lastOrNull() as? ListItem.Row
    LaunchedEffect(lastTodayRow?.task?.id) {
        val id = lastTodayRow?.task?.id ?: return@LaunchedEffect
        if (!pendingScroll) return@LaunchedEffect
        pendingScroll = false
        revealItem(listStates.getValue(Filter.Today), rowKey(id), pages.today.lastIndex + 1) // +1: heading item
    }

    // ---- notifications: asked in context, the first time a task gets a time (never at launch) ----
    val context = LocalContext.current
    val askedNotifications by vm.askedNotifications.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotificationsIfNeeded() {
        if (askedNotifications) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        vm.markAskedNotifications()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ---- intents ---------------------------------------------------------------------------
    fun onToggle(task: Task, checked: Boolean, page: Filter) {
        val entry = leave.entries[task.id]
        if (entry?.phase == LeavePhase.Collapsing) return
        if (entry != null) { // tapped again during the dwell: cancel and stay (6.5)
            vm.setDone(task.id, checked)
            leave.cancel(task.id)
            haptics.tick()
            return
        }
        val othersOpenToday = latestAll.count { !it.done && it.id != task.id && pageOf(it, latestToday) == Filter.Today }
        vm.setDone(task.id, checked)
        leave.start(task.id, page)
        if (checked) {
            haptics.thump()
            if (page == Filter.Today && othersOpenToday == 0) {
                scope.launch { delay(Durations.RiseAfterThump); haptics.rise() }
            }
        } else {
            haptics.tick()
        }
    }

    fun deleteWithCollapse(task: Task) {
        val page = leave.entries[task.id]?.from ?: pageOf(task, latestToday)
        if (onScreen(task.id, page)) deleting[task.id] = true else vm.delete(task)
    }

    // ---- v0.7.3: composer collapses to a centred pill while scrolling down ---------------------
    val density = LocalDensity.current
    val chrome = remember { with(density) { ComposerChrome(28.dp.toPx(), 16.dp.toPx()) } }
    // v0.9 lists button. While typing, a tap first dismisses editing (keyboard down, composer
    // unfocused), waits for the keyboard to finish leaving (max 600 ms) and one frame, then opens
    // the panel, so it grows from the button's resting place and never fights the IME animation.
    // The button itself is not focusable for text, so it never steals focus from the field.
    val imeInsets = WindowInsets.ime
    val densityNow = LocalDensity.current
    val openListsPanel: () -> Unit = {
        haptics.tick()
        val typing = chrome.focused || imeInsets.getBottom(densityNow) > 0
        if (!typing) menu = MenuIds.LIST_PICKER
        else {
            chrome.dismiss()
            scope.launch {
                kotlinx.coroutines.withTimeoutOrNull(600) { snapshotFlow { imeInsets.getBottom(densityNow) }.first { it == 0 } }
                androidx.compose.runtime.withFrameNanos { }
                menu = MenuIds.LIST_PICKER
            }
        }
    }
    HomeTestHooks.chrome = chrome
    // v0.9.1 theme toggle: tap = light <-> dark (overrides System), stored in the theme setting;
    // the switch is a circular reveal from the button (ThemeReveal), retargeted if tapped again.
    val darkNow = AppTheme.colors.isDark
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val reveal = LocalThemeReveal.current
    var themeCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val storedTheme = vm.settings.collectAsState().value?.theme ?: ThemeMode.System
    val applyTheme: (ThemeMode) -> Unit = { mode ->
        haptics.tick()
        val to = mode.isDark(systemDark)
        if (reveal == null || to == darkNow) vm.setTheme(mode)
        else reveal.switch(darkNow, to, themeCenter) { vm.setTheme(mode) }
    }
    var draggingRow by remember { mutableStateOf(false) }
    chrome.list = listStates.getValue(filter)
    SideEffect { chrome.externalBlock = undo != null || draggingRow }
    // Blocked (focus, text, voice, chips, undo bar, drag) always shows the full composer.
    LaunchedEffect(chrome) { snapshotFlow { chrome.blocked }.collect { if (it) chrome.expand() } }
    // Page switch.
    LaunchedEffect(filter) { chrome.expand() }
    // Back from a pushed screen (Home on top again).
    LaunchedEffect(chrome) { snapshotFlow { homeOnTop() }.collect { if (it) chrome.expand() else chrome.dismiss() } }

    val actions = remember {
        RowActions(
            onToggle = { t, checked, page -> HomeTestHooks.rowInput++; onToggle(t, checked, page) },
            onOpen = { t -> HomeTestHooks.rowInput++; panelMode = PanelMode.Details; panelTaskId = t.id },
            onSnooze = { t, _ -> panelMode = PanelMode.Snooze; panelTaskId = t.id },
            onSwipeDelete = { t -> deleting[t.id] = true },
            onDeleteCollapsed = { t ->
                deleting.remove(t.id)
                leave.finish(t.id)
                vm.delete(t)
            },
            onClearDone = {
                haptics.tick()
                // Only what the Done page shows: not rows still dwelling on the page they are leaving.
                vm.clearDone(latestAll.filter { it.done && leave.entries[it.id] == null })
            },
            anchors = anchors,
            onDrop = { outcome, id -> vm.applyDrop(outcome, id) },
            onTag = { tag -> vm.setSearchFilter(SearchFilter(tag)); onOpenSearch() },
            onDragging = { if (it) HomeTestHooks.rowInput++; draggingRow = it },
        )
    }

    // ---- layout ------------------------------------------------------------------------------
    // Two Haze states: `hazeState` = the scrolling content (header + composer glass blur it);
    // `overlayHaze` = the whole home layer incl. header and composer (the floating panel's backdrop).
    val overlayHaze = rememberHazeState()
    // Keep the last seen task so a panel closing over a just-deleted task still has content.
    val panelTask = panelTaskId?.let { id -> all.firstOrNull { it.id == id } }
    val lastPanelTask = remember { mutableStateOf<Task?>(null) }
    if (panelTask != null) lastPanelTask.value = panelTask

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.paper)
                    .hazeSource(overlayHaze)
                    // v0.7.4: while the field has focus, the first tap anywhere outside the composer
                    // (rows, empty space, header) only ends editing: focus + IME go, a draft stays,
                    // and the press is consumed here (Initial pass, before the children see it) so it
                    // cannot open a task, tick a checkbox or switch a page by accident. A drag that
                    // starts there still scrolls (scrolling ignores a consumed down).
                    .pointerInput(chrome) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (chrome.focused && !chrome.composerBounds.contains(down.position)) {
                                down.consume()
                                chrome.dismiss()
                            }
                        }
                    },
            ) {
                // The sky is part of the haze source so the glass picks up its tint. ORDER MATTERS
                // (v0.7.2 root cause): hazeSource records what is drawn AFTER it in the chain plus
                // the children. With `.skyBackdrop().hazeSource()` the opaque sky was outside the
                // recording, the source was just the rows on transparent, and the blurred copy
                // could not cover the sharp rows drawn underneath: text read straight through the
                // composer and the header edge.
                Box(Modifier.fillMaxSize().hazeSource(hazeState).skyBackdrop(c).nestedScroll(chrome.connection)) {
                    val composerEdge = with(LocalDensity.current) { 6.dp.toPx() }
                    AnimatedContent(
                        // Rows fade out over 36 dp above the composer (or undo bar) top.
                        modifier = Modifier.bottomFade({ bottomPx.intValue - composerEdge }, collapse = { chrome.progress.value }),
                        targetState = filter,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            // Spatial part only; the fade is applied inside each page as a
                            // ModulateAlpha layer (no full-screen offscreen buffer per page).
                            if (reduced) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                slideInHorizontally(Springs.Smooth.intOffset) { dir * it / 6 } togetherWith
                                    slideOutHorizontally(Springs.Smooth.intOffset) { -dir * it / 6 }
                            } using SizeTransform(clip = false)
                        },
                        label = "filter content",
                    ) { page ->
                        val fade by transition.animateFloat(
                            transitionSpec = {
                                if (targetState == EnterExitState.Visible) Tweens.fadeIn(if (reduced) 0 else Durations.EnterDelay)
                                else Tweens.fadeOut()
                            },
                            label = "page fade",
                        ) { if (it == EnterExitState.Visible) 1f else 0f }
                        TaskListPage(
                            page = page,
                            items = pages[page],
                            listState = listStates.getValue(page),
                            contentPadding = padding,
                            leave = leave,
                            deleting = deleting,
                            tracker = tracker,
                            actions = actions,
                            heading = headingFor(page, today),
                            caption = captionFor(page, pages),
                            modifier = Modifier.graphicsLayer {
                                alpha = fade
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                        )
                    }
                }

                HomeHeader(
                    filter = filter,
                    onFilter = { chrome.dismiss(); filter = it },
                    onOpenSearch = onOpenSearch,
                    themeButton = {
                        ThemeToggleButton(
                            dark = darkNow,
                            onToggle = { applyTheme(if (darkNow) ThemeMode.Light else ThemeMode.Dark) },
                            onMenu = { haptics.tick(); chrome.dismiss(); menu = MenuIds.THEME_MENU },
                            modifier = Modifier.onGloballyPositioned { themeCenter = it.boundsInRoot().center },
                            anchor = Modifier.panelAnchor(menuAnchors, MenuIds.THEME_MENU),
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { topPx.intValue = it.height }
                        .launchEntrance(launch, LaunchEntrance.Slot.Header),
                )

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomPx.intValue = it.height }
                        .launchEntrance(launch, LaunchEntrance.Slot.Bottom),
                ) {
                    UndoBar(
                        event = undo,
                        onUndo = { e ->
                            haptics.tick()
                            tracker.forget(vm.undo(e))
                        },
                        onTimeout = vm::dismissUndo,
                    )
                    Composer(
                        state = composerState,
                        lists = lists,
                        onOptions = { menu = MenuIds.ADD_MENU },
                        optionsAnchor = Modifier.panelAnchor(menuAnchors, MenuIds.ADD_MENU),
                        chrome = chrome,
                        // v0.9: the lists button, left of the composer (pairs with the pill when collapsed).
                        leading = {
                            ListsButton(
                                state = chip,
                                onClick = openListsPanel,
                                anchor = Modifier.panelAnchor(menuAnchors, MenuIds.LIST_PICKER),
                            )
                        },
                        onSubmit = { parsed ->
                            // Show the page the new task lands on.
                            val future = parsed.dueDay != null && parsed.dueDay > today
                            val target = if (future) Filter.Upcoming else Filter.Today
                            if (filter != target) filter = target
                            // "#list" naming a list that is not in view: show it so the new row is seen.
                            val to = parsed.list
                            // (A list hidden by the focus mode stays hidden: that was the user's choice.)
                            if (to != null && selected != null && to.id != selected) vm.selectList(to.id)
                            pendingScroll = !future
                            vm.add(parsed)
                            if (parsed.dueMinute != null) askNotificationsIfNeeded()
                        },
                    )
                }
            }
        }

        TaskPanelHost(
            openId = panelTask?.id,
            anchors = anchors,
            backdrop = overlayHaze,
            onDismiss = { panelTaskId = null },
        ) { id ->
            val task = all.firstOrNull { it.id == id } ?: lastPanelTask.value?.takeIf { it.id == id }
            if (task != null) {
                androidx.compose.runtime.key(task.id, panelMode) {
                    if (panelMode == PanelMode.Snooze) {
                        SnoozeContent(
                            task = task,
                            today = today,
                            now = LocalDateTime.now(),
                            onToggleDone = { checked ->
                                onToggle(task, checked, leave.entries[task.id]?.from ?: pageOf(task, today))
                            },
                            onSnooze = { day, minute ->
                                val from = leave.entries[task.id]?.from ?: pageOf(task, today)
                                val to = pageOf(task.copy(dueDay = day.toEpochDay(), dueMinute = minute), today)
                                vm.setDueAt(task.id, day.toEpochDay(), minute)
                                if (to != from) leave.start(task.id, from) // dwell, then the same collapse
                                panelTaskId = null
                                askNotificationsIfNeeded()
                            },
                        )
                    } else TaskPanelContent(
                        task = task,
                        today = today,
                        lists = lists,
                        subtasks = subtasks[task.id].orEmpty(),
                        onSetList = { vm.setList(task.id, it) },
                        onSetTags = { vm.setTags(task.id, it) },
                        onAddSubtask = { vm.addSubtask(task.id, it) },
                        onSubtaskDone = { sid, done -> vm.setSubtaskDone(sid, done) },
                        onDeleteSubtask = { vm.deleteSubtask(it) },
                        onOrderSubtasks = { vm.orderSubtasks(it) },
                        onSaveAsTemplate = { vm.saveAsTemplate(task, today) },
                        onTag = { tag ->
                            panelTaskId = null
                            vm.setSearchFilter(SearchFilter(tag))
                            onOpenSearch()
                        },
                        onSaveText = { t, n -> vm.setText(task.id, t, n) },
                        onSetDueAt = { day, minute ->
                            vm.setDueAt(task.id, day, minute)
                            if (minute != null) askNotificationsIfNeeded()
                        },
                        onSetRepeat = { rule, firstDay ->
                            vm.setRepeat(task.id, rule)
                            if (firstDay != null) vm.setDueAt(task.id, firstDay, task.dueMinute)
                        },
                        onSetPriority = { vm.setPriority(task.id, it) },
                        onToggleDone = { checked ->
                            onToggle(task, checked, leave.entries[task.id]?.from ?: pageOf(task, today))
                        },
                        onDelete = {
                            haptics.tick()
                            anchors.forget(task.id) // close in place, the card is going away
                            panelTaskId = null
                            deleteWithCollapse(task)
                        },
                    )
                }
            }
        }

        // v0.7 floating menus: the "+" menu grows out of the bare "+" icon; v0.9: the lists panel
        // grows out of the lists button and floats ABOVE it, anchored bottom-left.
        TaskPanelHost(
            openId = menu,
            anchors = menuAnchors,
            backdrop = overlayHaze,
            onDismiss = { menu = null },
            // v0.7.1: shared-bounds morph, Morph spring.
            style = HomeTestHooks.menuStyle ?: PanelStyle.Menu,
            bareAnchors = setOf(MenuIds.ADD_MENU),
            ghost = { id ->
                if (id == MenuIds.LIST_PICKER) ListsButtonContent(chip)
                if (id == MenuIds.THEME_MENU) ThemeToggleContent(darkNow)
            },
            above = setOf(MenuIds.LIST_PICKER),
            below = setOf(MenuIds.THEME_MENU),
            anchorLevels = mapOf(MenuIds.LIST_PICKER to GlassLevel.Mid),
        ) { id ->
            when (id) {
                MenuIds.LIST_PICKER -> ListsPanelContent(
                    planLine = planSummary,
                    lists = lists,
                    openCounts = openCounts,
                    selected = selected,
                    focus = focus,
                    prefs = focusPrefs,
                    views = views,
                    onPlan = { menu = null; onOpenPlan() },
                    onSelect = { vm.selectList(it); menu = null },
                    onFocus = { vm.setFocus(it) },
                    onNewList = { menu = null; onOpenLists() },
                    onView = { v ->
                        menu = null
                        vm.setSearchFilter(SearchFilter(dev.saikrishna.todo.domain.ViewFilter.decode(v.filter), query = v.query))
                        onOpenSearch()
                    },
                    onSettings = { menu = null; onOpenSettings() },
                )
                MenuIds.THEME_MENU -> ThemeMenuContent(
                    current = storedTheme,
                    onSelect = { m -> menu = null; applyTheme(m) },
                )
                MenuIds.ADD_MENU -> AddMenuContent(
                    templates = templates,
                    onUse = { t ->
                        haptics.tick()
                        menu = null
                        val onToday = t.items.count { (it.dayOffset ?: 0) <= 0 }
                        if (filter != Filter.Today) filter = Filter.Today
                        tracker.armStagger(onToday)
                        vm.useTemplate(t, today)
                    },
                    onManage = { menu = null; onOpenTemplates() },
                )
            }
        }
    }
}

/**
 * Floating header (reference study): no bar, just a glass search circle and the centred pill over the
 * content. Content scrolling underneath is blurred at full strength behind the controls and the
 * blur fades out over the 28 dp below them (one masked blur pass for the whole header).
 */
@Composable
private fun HomeHeader(
    filter: Filter,
    onFilter: (Filter) -> Unit,
    onOpenSearch: () -> Unit,
    themeButton: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth()) {
        // Blurred, tinted edge behind the controls, fading out (eased) by 30 dp below them.
        GlassEdgeTop(solidHeight = statusTop + HeaderTop + PillSpec.Height)
        HeaderControls(filter, onFilter, onOpenSearch, themeButton)
    }
}

@Composable
private fun HeaderControls(
    filter: Filter,
    onFilter: (Filter) -> Unit,
    onOpenSearch: () -> Unit,
    themeButton: @Composable BoxScope.() -> Unit,
) {
    // v0.9.1: the theme toggle mirrors search on the right (same 44 dp glass circle).
    // v0.9: search (left) + the centred Today/Upcoming/Done pill. Settings and the lists moved to
    // the bottom-left lists button; the pill keeps symmetric insets so it stays screen-centred.
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Spacing.screen, end = Spacing.screen, top = HeaderTop, bottom = 28.dp),
    ) {
        CircleIconButton(AppIcons.Search, "Search", onOpenSearch, Modifier.align(Alignment.CenterStart), glass = true)
        SegmentedPill(
            options = Filter.entries.toList(),
            selected = filter,
            label = { it.title },
            onSelect = onFilter,
            style = PillStyle.Floating,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = PillSpec.Control + 10.dp),
        )
        Box(Modifier.align(Alignment.CenterEnd)) { themeButton() }
    }
}

private val HeaderTop = 8.dp

private val HeadingDate = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")

private fun headingFor(page: Filter, today: LocalDate): String = when (page) {
    Filter.Today -> today.format(HeadingDate.withLocale(java.util.Locale.getDefault()))
    Filter.Upcoming -> "Coming up"
    Filter.Done -> "Done"
}

private fun captionFor(page: Filter, pages: Pages): String? = when (page) {
    Filter.Today -> when (pages.openToday) { 0 -> null; 1 -> "1 thing to do"; else -> "${pages.openToday} things to do" }
    Filter.Upcoming -> pages.upcoming.count { it is ListItem.Row }.let { if (it == 0) null else if (it == 1) "1 planned" else "$it planned" }
    Filter.Done -> null // the "Clear done" row already shows the count
}

private suspend fun revealItem(listState: LazyListState, key: String, index: Int) {
    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > index }
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key }
    if (item == null) {
        listState.animateScrollToItem(index) // off-screen: bring it in (clamps at the list end)
        return
    }
    val visibleEnd = info.viewportEndOffset - info.afterContentPadding
    val overflow = (item.offset + item.size) - visibleEnd
    if (overflow > 0) listState.animateScrollBy(overflow.toFloat(), Springs.Smooth.float)
}

/** Test-only override (before/after timing comparisons). Null in the app. */
@androidx.annotation.VisibleForTesting
object HomeTestHooks {
    /** v0.7.4: row taps / toggles / drag starts that reached a row (leak tests). */
    var rowInput = 0

    /** v0.7.3: the live composer chrome (frame-step tests drive collapse/expand through it). */
    var chrome: ComposerChrome? = null

    var menuStyle: PanelStyle? = null
}
