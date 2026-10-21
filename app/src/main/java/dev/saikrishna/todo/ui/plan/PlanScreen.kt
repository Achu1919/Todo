package dev.saikrishna.todo.ui.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.domain.PlanAction
import dev.saikrishna.todo.domain.PlanItem
import dev.saikrishna.todo.domain.PlanSection
import dev.saikrishna.todo.domain.Planner
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.ui.components.CircleIconButton
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.ui.components.skyBackdrop
import dev.saikrishna.todo.ui.home.ListDot
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.home.dayLabel
import dev.saikrishna.todo.ui.home.rememberToday
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One planning pass over a fixed queue (taken when the screen opens, so writes never reshuffle it). */
@Stable
class PlanSession(val items: List<PlanItem>) {
    var index by mutableIntStateOf(0)
    val decisions = mutableStateMapOf<Long, PlanAction>()
    val picks = mutableStateMapOf<Long, LocalDate>()
    val finished: Boolean get() = index >= items.size
    val current: PlanItem? get() = items.getOrNull(index)
}

/** Where a decided card leaves to: Keep → right, Tomorrow → left, Pick date → down, Done → up. */
private fun exitOf(a: PlanAction?): Pair<Float, Float> = when (a) {
    PlanAction.Keep -> 1f to 0f
    PlanAction.Tomorrow -> -1f to 0f
    PlanAction.PickDate -> 0f to 1f
    PlanAction.Done -> 0f to -1f
    null -> 0f to 1f // skip
}

/** Test hook: the live session (frame tests drive decisions through [PlanTestHooks.decide]). */
object PlanTestHooks {
    var decide: ((PlanAction?) -> Unit)? = null
}

/**
 * v0.8 morning planning: a stack of glass cards (overdue, due today, a few suggestions). Swipe
 * right = keep for today, left = tomorrow; buttons for all four; ends with a summary. Every
 * decision is written at once. Motion: the top card leaves on [Springs.Smooth] toward its
 * action, the cards behind rise into place on [Springs.Glide]; reduced motion: 150 ms fades.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: TodoViewModel, onBack: () -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val today = rememberToday()
    val loaded = tasks
    val session = remember(loaded == null) { loaded?.let { PlanSession(Planner.queue(it, today)) } }
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) } // top card offset, fraction of the width
    val dy = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    fun decide(action: PlanAction?, picked: LocalDate? = null) {
        val s = session ?: return
        val item = s.current ?: return
        if (busy) return
        busy = true
        if (action != null) {
            if (action == PlanAction.Done) haptics.thump() else haptics.tick()
            s.decisions[item.task.id] = action
            if (picked != null) s.picks[item.task.id] = picked
            vm.applyPlan(Planner.apply(item.task, action, today, picked))
        }
        scope.launch {
            val (ex, ey) = exitOf(action)
            if (reduced) {
                alpha.animateTo(0f, tween(75))
            } else {
                // The card is flung toward a point well past the edge on Smooth and released the
                // moment it is fully off-screen (still moving, no settle tail), so the next card is
                // live after ~0.3 s.
                val (anim, target, gone) = if (ex != 0f) Triple(dx, ex * 1.6f, 1.2f) else Triple(dy, ey * 1.4f, 1.0f)
                val flight = launch { anim.animateTo(target, Springs.Smooth.float) }
                snapshotFlow { kotlin.math.abs(anim.value) >= gone || !flight.isActive }.first { it }
                flight.cancel()
            }
            s.index++
            dx.snapTo(0f); dy.snapTo(0f)
            if (reduced) alpha.animateTo(1f, tween(75)) else alpha.snapTo(1f)
            busy = false
        }
    }
    PlanTestHooks.decide = { a -> decide(a, if (a == PlanAction.PickDate) today.plusDays(3) else null) }

    Column(Modifier.fillMaxSize().background(c.paper).skyBackdrop(c).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(AppIcons.ArrowLeft, "Back", onBack, glass = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Plan your day", style = AppTheme.type.display, color = c.ink)
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = AppTheme.type.meta, color = c.inkSecondary)
            }
        }
        val s = session ?: return@Column
        val total = s.items.size
        // Progress: "3 of 8" and a thin bar (draw-phase width).
        if (total > 0 && !s.finished) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen + 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.index + 1} of $total", style = AppTheme.type.label, color = c.inkSecondary)
                Spacer(Modifier.width(12.dp))
                val p by animateFloatAsState(s.index.toFloat() / total, if (reduced) Tweens.reduced() else Springs.Smooth.float, label = "plan progress")
                Box(Modifier.weight(1f).height(4.dp).clip(Shapes.full).background(c.inkFill)) {
                    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = p.coerceIn(0.001f, 1f); transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }.background(c.ink))
                }
            }
        }

        AnimatedContent(
            targetState = s.finished,
            transitionSpec = {
                if (reduced) fadeIn(Tweens.reduced()) togetherWith fadeOut(Tweens.reduced())
                else (fadeIn(Tweens.fadeIn(0)) + scaleIn(Springs.Glide.float, initialScale = 0.94f)) togetherWith fadeOut(Tweens.fadeOutFast())
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "plan stage",
        ) { finished ->
            if (finished) {
                Summary(s, today, onDone = onBack)
            } else {
                Column(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen), contentAlignment = Alignment.Center) {
                        val widthPx = constraints.maxWidth.toFloat()
                        val heightPx = constraints.maxHeight.toFloat()
                        val step = with(LocalDensity.current) { 14.dp.toPx() }
                        // Back to front: two cards behind the top one.
                        for (depth in 2 downTo 0) {
                            val i = s.index + depth
                            val item = s.items.getOrNull(i) ?: continue
                            androidx.compose.runtime.key(item.task.id) {
                                val scale by animateFloatAsState(1f - 0.05f * depth, if (reduced) Tweens.reduced() else Springs.Glide.float, label = "card scale")
                                val lift by animateFloatAsState(depth * step, if (reduced) Tweens.reduced() else Springs.Glide.float, label = "card lift")
                                val top = depth == 0
                                PlanCard(
                                    item = item,
                                    today = today,
                                    listName = lists.firstOrNull { it.id == item.task.listId }?.let { listOfNotNull(it.emoji, it.name).joinToString(" ") },
                                    listColor = lists.firstOrNull { it.id == item.task.listId }?.color,
                                    hint = { if (top) dx.value else 0f },
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = scale; scaleY = scale
                                            translationY = lift + if (top) dy.value * heightPx else 0f
                                            if (top) {
                                                translationX = dx.value * widthPx
                                                rotationZ = dx.value * 7f
                                                this.alpha = alpha.value
                                            } else this.alpha = if (depth == 2) 0.7f else 1f
                                        }
                                        .then(if (top) Modifier.testTag("plan-card") else Modifier)
                                        .then(
                                            if (top && !reduced) Modifier.pointerInput(item.task.id) {
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        val v = dx.value
                                                        when {
                                                            v > 0.28f -> decide(PlanAction.Keep)
                                                            v < -0.28f -> decide(PlanAction.Tomorrow)
                                                            else -> scope.launch { dx.animateTo(0f, Springs.Snappy.float) }
                                                        }
                                                    },
                                                    onDragCancel = { scope.launch { dx.animateTo(0f, Springs.Snappy.float) } },
                                                ) { change, amount ->
                                                    change.consume()
                                                    if (!busy) scope.launch { dx.snapTo(dx.value + amount / size.width) }
                                                }
                                            } else Modifier,
                                        )
                                        .then(
                                            if (top) Modifier.semantics {
                                                customActions = PlanAction.entries.map { a ->
                                                    CustomAccessibilityAction(a.label) { if (a == PlanAction.PickDate) picking = true else decide(a); true }
                                                }
                                            } else Modifier,
                                        ),
                                )
                            }
                        }
                    }
                    ActionBar(
                        onKeep = { decide(PlanAction.Keep) },
                        onTomorrow = { decide(PlanAction.Tomorrow) },
                        onPick = { picking = true },
                        onDone = { decide(PlanAction.Done) },
                        onSkip = { decide(null) },
                    )
                }
            }
        }
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    state.selectedDateMillis?.let { ms -> decide(PlanAction.PickDate, LocalDate.ofEpochDay(ms / 86_400_000L)) }
                }) { Text("Move", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = c.inkSecondary) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun PlanCard(
    item: PlanItem,
    today: LocalDate,
    listName: String?,
    listColor: Int?,
    hint: () -> Float,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t: Task = item.task
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            // Opaque base under the glass tint: the cards stacked behind never show through.
            .background(c.paperRaised, Shapes.xl)
            .glassSurface(Shapes.xl, GlassLevel.Mid, c.glassCard)
            .clip(Shapes.xl),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 52.dp)) {
            val sectionColor = when (item.section) {
                PlanSection.Overdue -> c.danger
                PlanSection.Today -> c.accent
                PlanSection.Suggested -> c.inkSecondary
            }
            val due = t.dueDay?.let { LocalDate.ofEpochDay(it) }
            val sectionText = when (item.section) {
                PlanSection.Overdue -> "Overdue · ${dayLabel(due!!, today)}"
                PlanSection.Today -> "Due today" + (t.dueMinute?.let { " · " + QuickParse.timeLabel(it) } ?: "")
                PlanSection.Suggested -> if (due != null) "Suggested · ${dayLabel(due, today)}" else "Suggested · no date"
            }
            Text(sectionText.uppercase(), style = AppTheme.type.meta, color = sectionColor)
            Spacer(Modifier.height(10.dp))
            Text(t.title, style = AppTheme.type.display, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (t.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(t.notes, style = AppTheme.type.body, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            if (listName != null && listColor != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ListDot(c.listColor(listColor), 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(listName, style = AppTheme.type.meta, color = c.inkSecondary)
                }
            }
        }
        // Footer: a quiet swipe hint at rest; while dragging it gives way to "Today" (bottom left,
        // card moving right) or "Tomorrow" (bottom right, card moving left).
        if (!LocalReducedMotion.current) {
            Text(
                "Swipe right for today · left for tomorrow", style = AppTheme.type.meta, color = c.inkTertiary,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                    .graphicsLayer { alpha = 1f - (kotlin.math.abs(hint()) / 0.14f).coerceIn(0f, 1f) },
            )
        }
        Text(
            "TODAY", style = AppTheme.type.label, color = c.success,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 18.dp).graphicsLayer { alpha = (hint() / 0.28f).coerceIn(0f, 1f) },
        )
        Text(
            "TOMORROW", style = AppTheme.type.label, color = c.accent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 18.dp).graphicsLayer { alpha = (-hint() / 0.28f).coerceIn(0f, 1f) },
        )
    }
}

@Composable
private fun ActionBar(onKeep: () -> Unit, onTomorrow: () -> Unit, onPick: () -> Unit, onDone: () -> Unit, onSkip: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = 16.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(AppIcons.Sun, "Today", "Keep for today", Modifier.weight(1f), onKeep)
            ActionButton(AppIcons.ChevronRight, "Tomorrow", "Move to tomorrow", Modifier.weight(1f), onTomorrow)
            ActionButton(AppIcons.Calendar, "Pick date", "Pick a date", Modifier.weight(1f), onPick)
            ActionButton(AppIcons.Check, "Done", "Mark done", Modifier.weight(1f), onDone)
        }
        Text(
            "Skip", style = AppTheme.type.label, color = c.inkSecondary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
                .clip(Shapes.full)
                .clickable(role = Role.Button, onClickLabel = "Skip, leave as is", onClick = onSkip)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier
            .glassControl(Shapes.lg)
            .clip(Shapes.lg)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = AppTheme.type.meta, color = c.ink, maxLines = 1)
    }
}

@Composable
private fun Summary(s: PlanSession, today: LocalDate, onDone: () -> Unit) {
    val c = AppTheme.colors
    val sum = remember(s.index) { Planner.summary(s.items, s.decisions.toMap(), today, s.picks.toMap()) }
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen).padding(top = 12.dp), verticalArrangement = Arrangement.Top) {
        Column(
            Modifier.fillMaxWidth().glassSurface(Shapes.xl, GlassLevel.Mid, c.glassCard).clip(Shapes.xl).padding(22.dp).testTag("plan-summary"),
        ) {
            Text(if (s.items.isEmpty()) "Nothing to plan" else "You're set", style = AppTheme.type.display, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                if (s.items.isEmpty()) "No overdue or due tasks, and nothing waiting without a date." else sum.line,
                style = AppTheme.type.body, color = c.inkSecondary,
            )
            if (s.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SummaryRow("Kept for today", sum.kept)
                SummaryRow("Moved to tomorrow", sum.tomorrow)
                SummaryRow("Moved to a date", sum.picked)
                SummaryRow("Done", sum.done)
                SummaryRow("Skipped", s.items.size - sum.decided)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(Shapes.full)
                .background(c.ink)
                .clickable(role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) { Text("Go to Today", style = AppTheme.type.label, color = c.onInk) }
    }
}

@Composable
private fun SummaryRow(label: String, n: Int) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
        Text("$n", style = AppTheme.type.body.copy(fontFeatureSettings = "tnum"), color = if (n > 0) c.ink else c.inkTertiary)
    }
}
