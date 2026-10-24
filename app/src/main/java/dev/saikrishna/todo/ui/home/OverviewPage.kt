package dev.saikrishna.todo.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.domain.OverviewStats
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.ui.components.pressScale
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LaunchEntrance
import dev.saikrishna.todo.ui.motion.LocalLaunchEntrance
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.motion.completionDwellMillis
import dev.saikrishna.todo.ui.motion.launchEntrance
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/** Where an Overview tile leads. */
enum class OverviewTarget { Today, Overdue, Upcoming, Week }

/** v0.12 Overview content, everything the page shows (built by HomeScreen from the live data). */
class OverviewModel(
    val stats: OverviewStats,
    val greeting: String,
    val dateLine: String,
    /** Latest version of a task by id (the Next up row keeps showing a just-checked task briefly). */
    val task: (Long) -> Task?,
    val meta: (Task) -> RowMeta,
)

/**
 * v0.12 Overview (mock "Overview A", built for real): greeting + date with the theme toggle,
 * today's progress ring with Plan your day, four count tiles, the week's completions with the
 * streak, the next task, and per-list progress in list colours. Every card leads somewhere.
 */
@Composable
fun OverviewPage(
    model: OverviewModel,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onTarget: (OverviewTarget) -> Unit,
    onList: (Long) -> Unit,
    onPlan: () -> Unit,
    onToggle: (Task, Boolean) -> Unit,
    onOpen: (Task) -> Unit,
    taskAnchor: (Long) -> Modifier,
    modifier: Modifier = Modifier,
    animatorScale: Float = 1f,
    /** v0.13: Settings moved from the old lists panel to a gear next to the greeting. */
    settingsButton: (@Composable () -> Unit)? = null,
    /** v0.13: list management (Settings > Lists) and the saved views, also from the old panel. */
    views: List<dev.saikrishna.todo.data.SavedView> = emptyList(),
    onView: (dev.saikrishna.todo.data.SavedView) -> Unit = {},
    onManageLists: () -> Unit = {},
) {
    val s = model.stats
    val reduced = LocalReducedMotion.current
    val launch = LocalLaunchEntrance.current
    // Ring + bars grow in once when the page appears (snap under reduced motion).
    val grow = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (grow.value < 1f) grow.animateTo(1f, Springs.Smooth.float) }
    val scope = rememberCoroutineScope()
    // A just-checked Next up task stays for the completion dwell, then the next one fades in.
    var held by remember { mutableStateOf<Long?>(null) }
    val shownId = held ?: s.nextUp?.id
    fun slot(i: Int) = Modifier.launchEntrance(launch, LaunchEntrance.Slot.Row(i))

    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "greeting", contentType = "heading") {
            Row(slot(0).fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(model.greeting, style = AppTheme.type.display, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(model.dateLine, style = AppTheme.type.body, color = AppTheme.colors.inkSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                settingsButton?.invoke()
            }
        }
        item(key = "hero", contentType = "hero") {
            HeroCard(s, { grow.value }, onClick = { onTarget(OverviewTarget.Today) }, onPlan = onPlan, modifier = slot(1))
        }
        item(key = "tiles", contentType = "tiles") {
            Row(slot(2).fillMaxWidth().padding(top = Spacing.rowGap), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = AppTheme.colors
                StatTile("${s.todayOpen}", "Today", c.accent, Modifier.weight(1f)) { onTarget(OverviewTarget.Today) }
                StatTile("${s.overdue}", "Overdue", c.danger, Modifier.weight(1f)) { onTarget(OverviewTarget.Overdue) }
                StatTile("${s.upcoming}", "Upcoming", c.listColor(0), Modifier.weight(1f)) { onTarget(OverviewTarget.Upcoming) }
                StatTile("${s.doneThisWeek}", "This week", c.success, Modifier.weight(1f)) { onTarget(OverviewTarget.Week) }
            }
        }
        item(key = "week", contentType = "week") {
            WeekCard(s, { grow.value }, Modifier.padding(top = Spacing.rowGap).then(slot(3))) { onTarget(OverviewTarget.Week) }
        }
        item(key = "next-label", contentType = "label") {
            SectionLabel("Next up", slot(4).padding(top = 18.dp, bottom = 8.dp))
        }
        item(key = "next", contentType = "next") {
            AnimatedContent(
                targetState = shownId,
                transitionSpec = {
                    fadeIn(if (reduced) Tweens.reduced() else Tweens.fadeIn()) togetherWith fadeOut(Tweens.fadeOut())
                },
                label = "next up",
                modifier = slot(5),
            ) { id ->
                val t = id?.let(model.task)
                if (t == null) EmptyNext()
                else TaskRow(
                    task = t,
                    meta = model.meta(t),
                    onCheckedChange = { checked ->
                        if (checked) {
                            held = t.id
                            scope.launch { delay(completionDwellMillis(animatorScale)); if (held == t.id) held = null }
                        } else if (held == t.id) held = null
                        onToggle(t, checked)
                    },
                    onClick = { onOpen(t) },
                    modifier = taskAnchor(t.id),
                )
            }
        }
        item(key = "lists", contentType = "lists") {
            ListsCard(s, { grow.value }, onList, onManageLists, Modifier.padding(top = 18.dp).then(slot(6)))
        }
        if (views.isNotEmpty()) {
            item(key = "views", contentType = "views") {
                ViewsCard(views, onView, Modifier.padding(top = 18.dp).then(slot(7)))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.inkSecondary, modifier = modifier.padding(start = 4.dp).semantics { heading() })
}

/** A glass card that is one tap target (soft press + 0.98 scale, like the rows). */
@Composable
private fun TapCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Shapes.lg,
    pad: Dp = 16.dp,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .pressScale(press, 0.98f)
            .glassSurface(shape, GlassLevel.Low, AppTheme.colors.glassCard)
            .clip(shape)
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
            )
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .padding(pad),
    ) { content() }
}

@Composable
private fun HeroCard(s: OverviewStats, grow: () -> Float, onClick: () -> Unit, onPlan: () -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    val title = when {
        s.todayTotal == 0 -> "A clear day"
        s.todayOpen == 0 -> "All done for today"
        else -> "${s.todayOpen} left today"
    }
    val sub = listOfNotNull(
        s.overdue.takeIf { it > 0 }?.let { "$it overdue" },
        s.nextAtMinute?.let { "next at ${QuickParse.timeLabel(it)}" },
    ).joinToString(" · ").ifEmpty {
        when {
            s.todayTotal == 0 -> "Add a task below, or plan your day"
            s.todayOpen == 0 -> "${s.todayDone} finished. Nice work"
            else -> "Nothing overdue"
        }
    }
    TapCard(onClick, modifier, pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.semantics(mergeDescendants = true) { contentDescription = "${s.todayDone} of ${s.todayTotal} done today" }) {
                ProgressRing(
                    progress = { s.todayProgress * grow() }, size = 96.dp, stroke = 11.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
                        Text("${s.todayDone}/${s.todayTotal}", style = AppTheme.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
                        Text("today", style = AppTheme.type.meta, color = c.inkTertiary)
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = AppTheme.type.title.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
                Text(sub, style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.height(12.dp))
                PlanButton(onPlan)
            }
        }
    }
}

@Composable
private fun PlanButton(onClick: () -> Unit) {
    val c = AppTheme.colors
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(40.dp)
            .pressScale(press, 0.95f)
            .clip(Shapes.full)
            .background(c.ink)
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Sun, contentDescription = null, tint = c.onInk, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text("Plan your day", style = AppTheme.type.label, color = c.onInk, maxLines = 1)
    }
}

/** Today's ring: a faint ink track and the accent arc (a soft sweep, round cap), progress read in draw. */
@Composable
fun ProgressRing(progress: () -> Float, size: Dp, stroke: Dp, center: @Composable () -> Unit = {}) {
    val c = AppTheme.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val tl = Offset(w / 2, w / 2)
            drawArc(c.ink.copy(alpha = 0.08f), 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            val p = progress().coerceIn(0f, 1f)
            if (p > 0f) {
                drawArc(
                    Brush.sweepGradient(listOf(c.accent.copy(alpha = 0.75f), c.accent, c.accent.copy(alpha = 0.75f))),
                    -90f, 360f * p, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        center()
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    TapCard(onClick, modifier, shape = Shapes.md, pad = 12.dp, description = "$label: $value") {
        Column {
            Box(Modifier.width(18.dp).height(3.dp).clip(Shapes.full).background(color))
            Spacer(Modifier.height(10.dp))
            Text(value, style = AppTheme.type.title.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
            // v0.12.1: shrink a little at font scale 1.3 ("Upco…" before), then ellipsize.
            BasicText(
                label, style = AppTheme.type.meta.copy(fontSize = 12.sp, color = c.inkSecondary), maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            )
        }
    }
}

@Composable
private fun WeekCard(s: OverviewStats, grow: () -> Float, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val line = if (s.doneThisWeek == 0) "Nothing finished yet this week"
    else listOfNotNull(
        "${s.doneThisWeek} done",
        s.bestDay?.let { "best day ${it.getDisplayName(TextStyle.FULL, Locale.getDefault())}" },
    ).joinToString(" · ")
    TapCard(onClick, modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = AppTheme.type.title.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.weight(1f))
                if (s.streak > 0) StreakBadge(s.streak)
            }
            Text(line, style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
            WeekBars(s.week, s.todayIndex, grow, Modifier.fillMaxWidth().height(64.dp + 22.dp))
        }
    }
}

@Composable
private fun StreakBadge(days: Int) {
    val c = AppTheme.colors
    Row(
        Modifier.height(26.dp).clip(Shapes.full).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text("$days-day streak", style = AppTheme.type.meta, color = c.ink, maxLines = 1)
    }
}

private val DayInitials = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Monday..Sunday completion bars in one Canvas: 22 dp bars with 7 dp corners, today in an accent
 * gradient, past days in a faint ink gradient, future days a stub. Heights grow with [grow].
 */
@Composable
fun WeekBars(values: List<Int>, todayIndex: Int, grow: () -> Float, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val desc = values.mapIndexed { i, v -> "${java.time.DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())} $v" }
        .joinToString(", ")
    Column(modifier.semantics { contentDescription = "Completed per day: $desc" }) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val bw = 22.dp.toPx()
            val gap = (size.width - bw * 7) / 6
            val r = CornerRadius(7.dp.toPx())
            val g = grow().coerceIn(0f, 1f)
            for (i in 0 until 7) {
                val f = if (i > todayIndex) 0.06f else (values.getOrElse(i) { 0 }.toFloat() / max).coerceAtLeast(0.06f)
                val h = size.height * (0.06f + (f - 0.06f) * g)
                val x = i * (bw + gap)
                val brush = if (i == todayIndex) Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.7f)), size.height - h, size.height)
                else Brush.verticalGradient(listOf(c.ink.copy(alpha = 0.22f), c.ink.copy(alpha = 0.12f)), size.height - h, size.height)
                drawRoundRect(brush, Offset(x, size.height - h), Size(bw, h), r)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            DayInitials.forEachIndexed { i, d ->
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    Text(d, style = AppTheme.type.meta, color = if (i == todayIndex) c.ink else c.inkTertiary)
                }
            }
        }
    }
}

@Composable
private fun ListsCard(s: OverviewStats, grow: () -> Float, onList: (Long) -> Unit, onManage: () -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Lists", Modifier.weight(1f))
            Text(
                "Manage",
                style = AppTheme.type.label,
                color = c.accent,
                modifier = Modifier
                    .clip(Shapes.full)
                    .clickable(role = Role.Button, onClickLabel = "Manage lists", onClick = onManage)
                    .testTag("lists-manage")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        TapCard(null, pad = 6.dp) {
            Column {
                for (p in s.lists) {
                    val col = c.listColor(p.list.color)
                    val press = remember { MutableInteractionSource() }
                    val name = listOfNotNull(p.list.emoji, p.list.name).joinToString(" ")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Shapes.md)
                            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button) { onList(p.list.id) }
                            .semantics(mergeDescendants = true) { contentDescription = "$name, ${p.open} open, ${p.done} done" }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ListDot(col, 9.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(name, style = AppTheme.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                        Spacer(Modifier.width(8.dp))
                        Canvas(Modifier.weight(1f).height(6.dp)) {
                            val r = CornerRadius(size.height / 2)
                            drawRoundRect(col.copy(alpha = 0.16f), cornerRadius = r)
                            val w = size.width * p.fraction * grow().coerceIn(0f, 1f)
                            if (w > 0f) drawRoundRect(col, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${p.open} open", style = AppTheme.type.meta, color = c.inkSecondary, maxLines = 1)
                    }
                }
                // New list / rename / colour / reorder / delete all live in Settings > Lists.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Shapes.md)
                        .clickable(role = Role.Button, onClickLabel = "New list", onClick = onManage)
                        .testTag("lists-new")
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Plus, null, tint = c.inkSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New list", style = AppTheme.type.body, color = c.inkSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ViewsCard(views: List<dev.saikrishna.todo.data.SavedView>, onView: (dev.saikrishna.todo.data.SavedView) -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier) {
        SectionLabel("Saved views", Modifier.padding(bottom = 8.dp))
        TapCard(null, pad = 6.dp) {
            Column {
                for (v in views) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Shapes.md)
                            .clickable(role = Role.Button, onClickLabel = "Open ${v.name}") { onView(v) }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Bookmark, null, tint = c.inkSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(v.name, style = AppTheme.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(AppIcons.ChevronRight, null, tint = c.inkTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNext() {
    val c = AppTheme.colors
    TapCard(null, shape = Shapes.row, pad = 16.dp) {
        Column {
            Text("Nothing up next", style = AppTheme.type.title, color = c.ink)
            Text("Everything for today is done, or not added yet.", style = AppTheme.type.meta, color = c.inkSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

