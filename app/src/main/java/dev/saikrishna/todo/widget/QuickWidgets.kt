package dev.saikrishna.todo.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.saikrishna.todo.MainActivity
import dev.saikrishna.todo.R
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.reminders.PlanReminder
import dev.saikrishna.todo.ui.capture.AddTaskTileService
import dev.saikrishna.todo.ui.capture.CapturePreset
import dev.saikrishna.todo.ui.theme.DarkAppColors
import dev.saikrishna.todo.ui.theme.LightAppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// v0.12 add-task widgets. All four are launchers for the floating capture card (with presets);
// W3 and W4 also show a little data (list chips, the next task), loaded with the same bounded,
// never-throwing pattern as the Today widget.

/** A W3 chip: Today (listId null → due today) or one of the first lists. */
@Immutable
data class QuickChip(val listId: Long?, val label: String, val colorIndex: Int?) {
    val preset: CapturePreset get() = if (listId == null) CapturePreset(dueToday = true) else CapturePreset(listId = listId)
}

@Immutable
data class QuickData(val next: WidgetRow?, val chips: List<QuickChip>, val dateLabel: String) {
    companion object {
        const val QuickLists = 2

        /** Pure: the next open Today task (the Today widget's first row), Today + the first two lists by position. */
        fun build(tasks: List<Task>, lists: List<TaskList>, today: LocalDate): QuickData {
            val next = WidgetData.build(tasks, today, max = 1).rows.firstOrNull()
            val firstLists = lists.sortedWith(compareBy<TaskList> { it.position }.thenBy { it.id }).take(QuickLists)
            return QuickData(next, chipsFor(firstLists), dateLabel(today))
        }

        fun chipsFor(lists: List<TaskList>): List<QuickChip> =
            listOf(QuickChip(null, "Today", null)) + lists.map { l ->
                QuickChip(l.id, listOfNotNull(l.emoji, l.name).joinToString(" "), l.color)
            }

        /** Used when the data can't load: still a working bar (Today + the built-in lists). */
        val Fallback: QuickData get() = QuickData(null, chipsFor(TaskList.Defaults.take(QuickLists)), "")

        fun dateLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))

        internal suspend fun loadOrNull(context: Context, id: GlanceId? = null): QuickData? = try {
            withTimeout(TodayWidget.LoadTimeoutMs) {
                val app = context.applicationContext as TodoApp
                val today = Instant.ofEpochMilli(app.repository.now()).atZone(app.repository.zone()).toLocalDate()
                build(app.repository.tasks.first(), app.repository.lists.first(), today)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TodayWidget.TAG, "quick widget: load took over ${TodayWidget.LoadTimeoutMs} ms ($id)", e); null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TodayWidget.TAG, "quick widget: load failed ($id)", t); null
        }
    }
}

/** Shared base: exact sizes (layouts adapt to the cell size), error layout, logged failures. */
abstract class QuickWidgetBase(private val needsData: Boolean) : GlanceAppWidget(errorUiLayout = R.layout.widget_quick_error) {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = if (needsData) QuickData.loadOrNull(context, id) ?: QuickData.Fallback else null
        provideContent { Content(context, data) }
    }

    @Composable abstract fun Content(context: Context, data: QuickData?)

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Log.w(TodayWidget.TAG, "${javaClass.simpleName}: composition failed for $appWidgetId", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }
}

class AddPillWidget : QuickWidgetBase(needsData = false) {
    @Composable override fun Content(context: Context, data: QuickData?) = AddPillContent(context)
}
class AddRoundWidget : QuickWidgetBase(needsData = false) {
    @Composable override fun Content(context: Context, data: QuickData?) = AddRoundContent(context)
}
class QuickListsWidget : QuickWidgetBase(needsData = true) {
    @Composable override fun Content(context: Context, data: QuickData?) = QuickListsContent(context, data ?: QuickData.Fallback)
}
class HubWidget : QuickWidgetBase(needsData = true) {
    @Composable override fun Content(context: Context, data: QuickData?) = HubContent(context, data ?: QuickData.Fallback)
}

class AddPillWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AddPillWidget() }
class AddRoundWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AddRoundWidget() }
class QuickListsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickListsWidget() }
class HubWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HubWidget() }

object QuickWidgets {
    /** Redraw the data-showing widgets (W3 lists, W4 next task). No-op when none is placed; never throws. */
    suspend fun refresh(context: Context) {
        refreshIfPlaced(context, QuickListsWidget::class.java) { QuickListsWidget() }
        refreshIfPlaced(context, HubWidget::class.java) { HubWidget() }
    }

    private suspend fun <T : GlanceAppWidget> refreshIfPlaced(context: Context, cls: Class<T>, make: () -> T) {
        try {
            if (GlanceAppWidgetManager(context).getGlanceIds(cls).isEmpty()) return
            make().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TodayWidget.TAG, "refresh ${cls.simpleName} failed", t)
        }
    }

    /** The capture card with a preset (each preset has its own data URI, so its own PendingIntent). */
    fun capture(context: Context, preset: CapturePreset = CapturePreset()): Intent = AddTaskTileService.captureIntent(context, preset)

    fun plan(context: Context): Intent = PlanReminder.planActivityIntent(context)

    fun openApp(context: Context): Intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Our list palette, light / dark (from the app theme). */
internal fun listColorProvider(index: Int) = ColorProvider(day = LightAppColors.listColor(index), night = DarkAppColors.listColor(index))

// ---------------- content ----------------

private val White = ColorProvider(day = androidx.compose.ui.graphics.Color.White, night = androidx.compose.ui.graphics.Color.White)

@Composable
private fun PlusDisc(size: Dp, action: Action?, description: String? = null) {
    var m = GlanceModifier.size(size).cornerRadius(size / 2).background(ImageProvider(R.drawable.widget_accent_disc))
    if (action != null) m = m.clickable(action)
    if (description != null) m = m.semantics { contentDescription = description }
    Box(m, contentAlignment = Alignment.Center) {
        Image(ImageProvider(R.drawable.ic_widget_plus), null, colorFilter = ColorFilter.tint(White), modifier = GlanceModifier.size(size * 0.5f))
    }
}

@Composable
private fun MicDisc(context: Context, size: Dp) {
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(ImageProvider(R.drawable.widget_soft_disc))
            .clickable(actionStartActivity(QuickWidgets.capture(context, CapturePreset(voice = true))))
            .semantics { contentDescription = "Add a task by voice" },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(R.drawable.ic_widget_mic), null, colorFilter = ColorFilter.tint(WidgetColors.inkSecondary), modifier = GlanceModifier.size(18.dp))
    }
}

/** W1 · 2x1: "+ Add a task" pill with a mic (the mic is dropped when the pill is narrow). */
@Composable
fun AddPillContent(context: Context) {
    val size = LocalSize.current
    val h = min(size.height, 64.dp).coerceAtLeast(48.dp)
    val disc = h - 24.dp
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            GlanceModifier.fillMaxWidth().height(h).cornerRadius(h / 2).background(ImageProvider(R.drawable.widget_glass_pill))
                .clickable(actionStartActivity(QuickWidgets.capture(context)))
                .padding(horizontal = 9.dp)
                .semantics { contentDescription = "Add a task" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlusDisc(disc, action = null)
            Spacer(GlanceModifier.width(9.dp))
            Text(
                "Add a task", maxLines = 1,
                style = TextStyle(color = WidgetColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (size.width >= 150.dp) MicDisc(context, disc - 4.dp)
        }
    }
}

/** W2 · 1x1: a round "+" in the icon gradient, as big as the cell allows (≤ 62dp). */
@Composable
fun AddRoundContent(context: Context) {
    val size = LocalSize.current
    val d = min(min(size.width, size.height) - 8.dp, 62.dp).coerceAtLeast(40.dp)
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            GlanceModifier.size(d).cornerRadius(d / 2).background(ImageProvider(R.drawable.widget_round_bg))
                .clickable(actionStartActivity(QuickWidgets.capture(context)))
                .semantics { contentDescription = "Add a task" },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_plus), null, colorFilter = ColorFilter.tint(White), modifier = GlanceModifier.size(d * 0.48f))
        }
    }
}

/** How many chips fit in [width] beside the + and mic (pure, rough text widths). */
internal fun chipsThatFit(chips: List<QuickChip>, width: Dp): Int {
    var left = width.value - 20 - 42 - 8 - 38 - 6
    var n = 0
    for (c in chips) {
        // padding 22 + label (~5.8 dp/char at 13 sp medium, measured on renders) + sun 19 / dot 14 + gap 6.
        val w = 22 + c.label.length * 5.8f + (if (c.listId == null) 19 else if (c.colorIndex != null) 14 else 0) + 6
        if (w > left) break
        left -= w; n++
    }
    return n.coerceAtLeast(1)
}

/** W3 · 4x1: + · Today · first two lists · mic. Each chip opens capture pre-set to it. */
@Composable
fun QuickListsContent(context: Context, data: QuickData) {
    val size = LocalSize.current
    val h = min(size.height, 64.dp).coerceAtLeast(48.dp)
    val shown = data.chips.take(chipsThatFit(data.chips, size.width))
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            GlanceModifier.fillMaxWidth().height(h).cornerRadius(h / 2).background(ImageProvider(R.drawable.widget_glass_pill))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlusDisc(h - 22.dp, actionStartActivity(QuickWidgets.capture(context)), "Add a task")
            Spacer(GlanceModifier.width(8.dp))
            for (chip in shown) {
                QuickChipView(context, chip)
                Spacer(GlanceModifier.width(6.dp))
            }
            Spacer(GlanceModifier.defaultWeight())
            MicDisc(context, h - 26.dp)
        }
    }
}

@Composable
private fun QuickChipView(context: Context, chip: QuickChip) {
    Row(
        GlanceModifier.height(36.dp).cornerRadius(18.dp).background(ImageProvider(R.drawable.widget_soft_chip))
            .clickable(actionStartActivity(QuickWidgets.capture(context, chip.preset)))
            .padding(horizontal = 11.dp)
            .semantics { contentDescription = if (chip.listId == null) "Add a task for today" else "Add a task to ${chip.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip.listId == null) {
            Image(ImageProvider(R.drawable.ic_widget_sun), null, colorFilter = ColorFilter.tint(WidgetColors.accent), modifier = GlanceModifier.size(14.dp))
            Spacer(GlanceModifier.width(5.dp))
        } else if (chip.colorIndex != null) {
            Image(ImageProvider(R.drawable.widget_dot), null, colorFilter = ColorFilter.tint(listColorProvider(chip.colorIndex)), modifier = GlanceModifier.size(8.dp))
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(chip.label, maxLines = 1, style = TextStyle(color = WidgetColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

/** W4 · 2x2 hub: the next task (or "What's next?"), Add, Voice and Plan. */
@Composable
fun HubContent(context: Context, data: QuickData) {
    val size = LocalSize.current
    // 2x2 (~180 dp): header, ONE line of next task / "What's next?", Add, Voice + Plan (mock W4).
    // Taller: a "Next · 10 am" label + two lines. Short (<160 dp): no header.
    val compact = size.height < 160.dp
    val roomy = size.height >= 210.dp
    val next = data.next
    // Without the label line, the next task's time takes the date's place in the header.
    val headerRight = if (!roomy && next?.meta != null) next.meta else data.dateLabel
    val headerRightColor = if (!roomy && next?.overdue == true) WidgetColors.danger else WidgetColors.inkSecondary
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(ImageProvider(R.drawable.widget_glass_hub)).padding(12.dp),
    ) {
        if (!compact) {
            Row(
                GlanceModifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp).clickable(actionStartActivity(QuickWidgets.openApp(context))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.size(16.dp).cornerRadius(8.dp).background(ImageProvider(R.drawable.widget_round_bg)), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_widget_check), null, colorFilter = ColorFilter.tint(White), modifier = GlanceModifier.size(10.dp))
                }
                Spacer(GlanceModifier.width(6.dp))
                Text(context.getString(R.string.app_name), style = TextStyle(color = WidgetColors.inkSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
                Text(headerRight, maxLines = 1, style = TextStyle(color = headerRightColor, fontSize = 12.sp))
            }
        }
        // The next task fills the space above the buttons; tap opens it.
        Column(
            GlanceModifier.fillMaxWidth().defaultWeight().padding(start = 6.dp, end = 4.dp, bottom = 8.dp)
                .clickable(actionStartActivity(if (next != null) openTaskIntent(context, next.id) else QuickWidgets.openApp(context))),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (next != null && roomy) {
                Text(
                    if (next.meta != null) "Next · ${next.meta}" else "Next up",
                    style = TextStyle(color = if (next.overdue) WidgetColors.danger else WidgetColors.inkSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Text(next.title, maxLines = 2, style = TextStyle(color = WidgetColors.ink, fontSize = 18.sp, fontFamily = FontFamily.Serif))
            } else if (next != null) {
                Text(next.title, maxLines = 1, style = TextStyle(color = WidgetColors.ink, fontSize = 19.sp, fontFamily = FontFamily.Serif))
            } else {
                Text("What's next?", maxLines = 1, style = TextStyle(color = WidgetColors.ink, fontSize = 22.sp, fontFamily = FontFamily.Serif))
            }
        }
        Row(
            GlanceModifier.fillMaxWidth().height(48.dp).cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_soft_field))
                .clickable(actionStartActivity(QuickWidgets.capture(context)))
                .padding(horizontal = 6.dp)
                .semantics { contentDescription = "Add a task" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlusDisc(36.dp, action = null)
            Spacer(GlanceModifier.width(8.dp))
            Text("Add a task", maxLines = 1, style = TextStyle(color = WidgetColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            HubTile(R.drawable.ic_widget_mic, "Voice", WidgetColors.inkSecondary, QuickWidgets.capture(context, CapturePreset(voice = true)), "Add a task by voice")
            Spacer(GlanceModifier.width(8.dp))
            HubTile(R.drawable.ic_widget_sun, "Plan", WidgetColors.accent, QuickWidgets.plan(context), "Plan your day")
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.HubTile(icon: Int, label: String, tint: androidx.glance.unit.ColorProvider, intent: Intent, description: String) {
    Row(
        GlanceModifier.defaultWeight().height(48.dp).cornerRadius(18.dp).background(ImageProvider(R.drawable.widget_soft_chip))
            .clickable(actionStartActivity(intent))
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(ImageProvider(icon), null, colorFilter = ColorFilter.tint(tint), modifier = GlanceModifier.size(15.dp))
        Spacer(GlanceModifier.width(5.dp))
        Text(label, maxLines = 1, style = TextStyle(color = WidgetColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}
