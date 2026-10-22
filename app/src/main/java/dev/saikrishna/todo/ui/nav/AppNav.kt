package dev.saikrishna.todo.ui.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.saikrishna.todo.ui.components.LocalNavMoving
import dev.saikrishna.todo.ui.components.skyBackdrop
import dev.saikrishna.todo.ui.home.HomeScreen
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.settings.LicensesScreen
import dev.saikrishna.todo.ui.settings.ListsScreen
import dev.saikrishna.todo.ui.settings.TemplatesScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import dev.saikrishna.todo.ui.settings.SettingsScreen
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Plain enum keys: saved by name. */
/** v0.11: Search is no longer a screen (in-place morph on Home). */
enum class Screen { Home, Settings, Licenses, Lists, Templates, Plan }

private val BackStackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack -> stack.map { it.name } },
    // Names from an older build (v0.10 "Search") are skipped; Home always stays at the root.
    restore = { names ->
        names.mapNotNull { n -> Screen.entries.firstOrNull { it.name == n } }
            .ifEmpty { listOf(Screen.Home) }.toMutableStateList()
    },
)

/** Parallax of the screen underneath a push: it moves a quarter of the width, no scale. */
private const val UnderShift = 0.25f

/**
 * v0.6.1 stack navigator (replaces Navigation 3's NavDisplay).
 *
 * Why: with NavDisplay, a screen that is covered is DISPOSED once the push settles, so a pop had
 * to compose Home from scratch on the pop's first frame (Home was missing for the first frames and
 * popped in late on device), and the button pop and the predictive pop took different paths.
 * Here every screen on the stack stays composed (covered ones are simply not drawn and are hidden
 * from input/semantics), so Home is always ready, and ONE `pop()` serves the back arrow, the
 * predictive gesture's commit and system back.
 *
 * Motion is a single [Animatable] `x` (the top screen's offset as a fraction of the width,
 * 0 = in place, 1 = off to the right) read only in graphicsLayer blocks (draw phase):
 * top at `x·w`, the screen under it at `−¼·w·(1−x)`. No scale, so nothing behind the scene is
 * ever exposed (the old 0.96 scale exposed the window at the edges), and both end states are
 * exact (x snaps to exactly 0/1), so the last animated frame equals the settled frame.
 * Reduced motion: the same `x` drives a crossfade instead of slides.
 */
@Stable
private class NavState(
    val stack: SnapshotStateList<Screen>,
    private val holder: SaveableStateHolder,
    private val scope: CoroutineScope,
) {
    /** Top screen offset, fraction of the width (0 = in place, 1 = off to the right). Set synchronously. */
    var x by mutableFloatStateOf(0f)
    /** A popped screen still sliding out (drawn above the stack). */
    var exiting by mutableStateOf<Screen?>(null)
    /** True while x is animating or following a back gesture. */
    var moving by mutableStateOf(false)
    var reduced = false
    var keyboard: SoftwareKeyboardController? = null
    var focus: FocusManager? = null
    private var job: Job? = null

    val top: Screen get() = exiting ?: stack.last()
    /** The screen drawn under the moving one (null when settled). */
    val under: Screen? get() = when {
        exiting != null -> stack.last()
        moving && stack.size > 1 -> stack[stack.lastIndex - 1]
        else -> null
    }

    private fun dismissIme() {
        // Same frame as the pop/push: never wait for the keyboard before moving.
        focus?.clearFocus(force = true)
        keyboard?.hide()
    }

    private fun spec(): AnimationSpec<Float> = if (reduced) Tweens.reduced() else Springs.Smooth.float

    /** Animates x to [target]; on completion x is exactly [target] and [then] runs. */
    private fun animateX(target: Float, then: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            animate(x, target, 0f, spec()) { v, _ -> x = v }
            x = target
            then()
        }
    }

    fun push(s: Screen) {
        if (exiting == null && stack.last() == s) return
        finishExit()
        dismissIme()
        stack.add(s)
        x = 1f // same frame as the stack change: the new screen starts off to the right
        moving = true
        animateX(0f) { moving = false }
    }

    /** THE pop: the back arrow, system back and the predictive gesture's commit all end here. */
    fun pop() {
        finishExit()
        if (stack.size <= 1) return
        dismissIme()
        exiting = stack.removeAt(stack.lastIndex)
        moving = true
        animateX(1f) { finishExit() }
    }

    /**
     * v0.7: back to Home from any depth with the SAME pop (reminder tapped while Settings,
     * Settings or Licences is open). Screens between Home and the top are dropped without
     * animation (they are covered and not drawn), then the top slides out over Home.
     */
    fun popToRoot() {
        finishExit()
        if (stack.size <= 1) return
        while (stack.size > 2) {
            val gone = stack.removeAt(stack.lastIndex - 1)
            holder.removeState(gone.name)
        }
        pop()
    }

    /** Predictive back: the top screen follows the gesture; the screen under is already drawn. */
    fun preview(progress: Float) {
        if (exiting != null || stack.size <= 1) return
        job?.cancel()
        moving = true
        x = progress.coerceIn(0f, 1f)
    }

    fun cancelPreview() {
        if (exiting != null) return
        animateX(0f) { moving = false }
    }

    private fun finishExit() {
        val gone = exiting ?: return
        job?.cancel()
        exiting = null
        moving = false
        x = 0f
        if (gone !in stack) holder.removeState(gone.name)
    }
}

@Composable
fun AppNav(vm: TodoViewModel, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val stack = rememberSaveable(saver = BackStackSaver) { mutableListOf(Screen.Home).toMutableStateList() }
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val nav = remember { NavState(stack, holder, scope) }
    nav.reduced = LocalReducedMotion.current
    nav.keyboard = LocalSoftwareKeyboardController.current
    nav.focus = LocalFocusManager.current
    val movingState: State<Boolean> = remember { derivedStateOf { nav.moving } }

    // A reminder tapped while another screen is on top: pop back to Home (the same pop path);
    // Home opens the task's panel once it is the top screen again.
    val openRequest by vm.openRequest.collectAsState()
    LaunchedEffect(openRequest) {
        if (openRequest != null && stack.size > 1) nav.popToRoot()
    }

    // v0.8: the planning notification opens the planning screen (over Home).
    val planRequest by vm.planRequest.collectAsState()
    LaunchedEffect(planRequest) {
        if (!planRequest) return@LaunchedEffect
        vm.consumePlanRequest()
        if (stack.last() != Screen.Plan) {
            if (stack.size > 1) nav.popToRoot()
            nav.push(Screen.Plan)
        }
    }

    PredictiveBackHandler(enabled = stack.size > 1) { progress ->
        try {
            progress.collect { e -> nav.preview(e.progress) }
            nav.pop() // committed: the same pop as the back arrow
        } catch (e: CancellationException) {
            nav.cancelPreview()
            throw e
        }
    }

    // The window behind every scene is our own backdrop (never the platform window colour).
    BoxWithConstraints(modifier.fillMaxSize().background(c.paper).skyBackdrop(c)) {
        val layers = remember(stack.toList(), nav.exiting) { stack.toList() + listOfNotNull(nav.exiting) }
        for (s in layers) {
            key(s) {
                val role by remember(s) {
                    derivedStateOf {
                        when (s) {
                            nav.top -> Role.Top
                            nav.under -> Role.Under
                            else -> Role.Hidden
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val x = nav.x
                            when (role) {
                                Role.Top -> if (nav.reduced) {
                                    alpha = if (nav.moving) 1f - x else 1f
                                    translationX = 0f
                                } else {
                                    alpha = 1f
                                    translationX = if (nav.moving) x * size.width else 0f
                                }
                                Role.Under -> {
                                    alpha = 1f
                                    translationX = if (nav.reduced) 0f else -UnderShift * size.width * (1f - x)
                                }
                                Role.Hidden -> { alpha = 0f; translationX = 0f } // covered: not drawn at all
                            }
                        }
                        // Covered screens take no touches and are invisible to accessibility.
                        .then(if (role == Role.Hidden) Modifier.clearAndSetSemantics { } else Modifier)
                        // Each scene is opaque to touches: taps never fall through to a covered screen.
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                ) {
                    holder.SaveableStateProvider(s.name) {
                        CompositionLocalProvider(LocalNavMoving provides movingState) {
                            ScreenProbe(s) {
                                when (s) {
                                    Screen.Home -> HomeScreen(
                                        vm,
                                        onOpenSearch = {},
                                        onOpenSettings = { nav.push(Screen.Settings) },
                                        onOpenLists = { nav.push(Screen.Lists) },
                                        onOpenTemplates = { nav.push(Screen.Templates) },
                                        isTop = { stack.last() == Screen.Home && nav.exiting == null && !nav.moving },
                                        onOpenPlan = { nav.push(Screen.Plan) },
                                    )
                                    Screen.Settings -> SettingsScreen(
                                        vm, onBack = nav::pop,
                                        onOpenLicenses = { nav.push(Screen.Licenses) },
                                        onOpenLists = { nav.push(Screen.Lists) },
                                        onOpenTemplates = { nav.push(Screen.Templates) },
                                    )
                                    Screen.Licenses -> LicensesScreen(onBack = nav::pop)
                                    Screen.Lists -> ListsScreen(vm, onBack = nav::pop)
                                    Screen.Templates -> TemplatesScreen(vm, onBack = nav::pop)
                                    Screen.Plan -> dev.saikrishna.todo.ui.plan.PlanScreen(vm, onBack = nav::pop)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Role { Top, Under, Hidden }

/** Counters for transition tests: how often each screen entered / left composition. */
object NavProbe {
    val enters = HashMap<Screen, Int>()
    val disposals = HashMap<Screen, Int>()
    val recompositions = HashMap<Screen, Int>()
    fun reset() { enters.clear(); disposals.clear(); recompositions.clear() }
}

@Composable
private fun ScreenProbe(key: Screen, content: @Composable () -> Unit) {
    DisposableEffect(key) {
        NavProbe.enters.merge(key, 1, Int::plus)
        onDispose { NavProbe.disposals.merge(key, 1, Int::plus) }
    }
    SideEffect { NavProbe.recompositions.merge(key, 1, Int::plus) }
    Box(Modifier.testTag("screen-${key.name}")) { content() }
}
