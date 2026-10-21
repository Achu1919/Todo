package dev.saikrishna.todo.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import dev.saikrishna.todo.reminders.Reminders
import dev.saikrishna.todo.ui.capture.AddTaskTileService
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Our widget palette (light / dark), from the app's paper-raised, ink and accent tokens. */
internal object WidgetColors {
    val background = ColorProvider(day = Color(0xF5E6EBF3), night = Color(0xF51B202B))
    val ink = ColorProvider(day = Color(0xFF1B1C20), night = Color(0xFFF1F2F5))
    val inkSecondary = ColorProvider(day = Color(0x801B1C20), night = Color(0x8CF1F2F5))
    val ring = ColorProvider(day = Color(0x4D1B1C20), night = Color(0x52F1F2F5))
    val accent = ColorProvider(day = Color(0xFFE9562B), night = Color(0xFFFF7247))
    val danger = ColorProvider(day = Color(0xFFD63A40), night = Color(0xFFEC5F63))
    val plusFill = ColorProvider(day = Color(0xFF1B1C20), night = Color(0xFFF1F2F5))
    val onInk = ColorProvider(day = Color(0xFFF7F8FA), night = Color(0xFF15161A))
}

/**
 * v0.8 home-screen widget: Today's open tasks (up to 6). Tick a ring to complete the task (in
 * the background, via [CompleteTaskAction]); tap a row to open the app at that task's panel;
 * "+" opens the floating capture card. Refreshed whenever the tasks change ([TodoApp]).
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TodoApp
        val today = Instant.ofEpochMilli(app.repository.now()).atZone(app.repository.zone()).toLocalDate()
        val data = WidgetData.build(app.repository.tasks.first(), today)
        provideContent { TodayWidgetContent(context, data) }
    }

    companion object {
        /** Refresh every placed Today widget (no-op when none is placed). */
        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).isEmpty()) return
            TodayWidget().updateAll(context)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** The ring tapped: complete the task (subtasks + repeat handled by the repository). */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val app = context.applicationContext as TodoApp
        app.repository.setDone(id, true) // alarms (incl. a repeat's next instance) follow via TodoApp
        Reminders.dismissNotification(app, id)
        TodayWidget().update(context, glanceId)
    }
}

internal val TaskIdKey = ActionParameters.Key<Long>("task_id")

internal fun openTaskIntent(context: Context, id: Long): Intent =
    Intent(context, MainActivity::class.java)
        .setData("todo://task/$id".toUri())
        .putExtra(Reminders.EXTRA_OPEN_TASK, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
fun TodayWidgetContent(context: Context, data: WidgetData) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(WidgetColors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Today",
                style = TextStyle(color = WidgetColors.ink, fontSize = 20.sp, fontFamily = FontFamily.Serif),
                modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
            )
            Spacer(GlanceModifier.width(8.dp))
            if (data.openToday > 0) {
                Text("${data.openToday}", style = TextStyle(color = WidgetColors.inkSecondary, fontSize = 13.sp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Box(
                GlanceModifier
                    .size(34.dp)
                    .cornerRadius(17.dp)
                    .background(WidgetColors.plusFill)
                    .clickable(actionStartActivity(AddTaskTileService.captureIntent(context)))
                    .semantics { contentDescription = "Add a task" },
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_widget_plus), contentDescription = null, colorFilter = ColorFilter.tint(WidgetColors.onInk), modifier = GlanceModifier.size(18.dp))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        if (data.rows.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("All clear", style = TextStyle(color = WidgetColors.inkSecondary, fontSize = 14.sp))
            }
        } else {
            for (row in data.rows) TaskRowWidget(context, row)
            if (data.more > 0) {
                Text(
                    "+${data.more} more",
                    style = TextStyle(color = WidgetColors.inkSecondary, fontSize = 12.sp),
                    modifier = GlanceModifier.padding(start = 34.dp, top = 2.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
                )
            }
        }
    }
}

@Composable
private fun TaskRowWidget(context: Context, row: WidgetRow) {
    Row(
        GlanceModifier.fillMaxWidth().height(36.dp).clickable(actionStartActivity(openTaskIntent(context, row.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier
                .size(30.dp)
                .cornerRadius(15.dp)
                .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to row.id)))
                .semantics { contentDescription = "Complete ${row.title}" },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_ring), contentDescription = null, colorFilter = ColorFilter.tint(WidgetColors.ring), modifier = GlanceModifier.size(20.dp))
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            row.title,
            style = TextStyle(color = WidgetColors.ink, fontSize = 14.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (row.meta != null) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                row.meta,
                style = TextStyle(color = if (row.overdue) WidgetColors.danger else WidgetColors.inkSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

