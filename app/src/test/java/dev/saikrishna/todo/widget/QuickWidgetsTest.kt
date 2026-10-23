package dev.saikrishna.todo.widget

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.ui.capture.CapturePreset
import dev.saikrishna.todo.ui.capture.CaptureActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/** v0.12 add-task widgets: data, chip presets/fit, Glance trees, manifest + provider info. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class QuickWidgetsTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 23)
    private val td = today.toEpochDay()
    private val lists = listOf(
        TaskList(3, "Errands", color = 4, emoji = "🛒", position = 2),
        TaskList(1, "Personal", color = 2, position = 1),
        TaskList(2, "Work", color = 0, position = 0),
    )

    @Test fun dataIsNextTaskTodayAndFirstTwoListsByOrder() {
        val d = QuickData.build(listOf(
            Task(id = 1, title = "Later", createdAt = 1, dueDay = td + 2),
            Task(id = 2, title = "Pay rent", createdAt = 2, dueDay = td - 1),
            Task(id = 3, title = "Done one", createdAt = 3, done = true, completedAt = 5),
        ), lists, today)
        assertEquals("Pay rent", d.next?.title)
        assertEquals(listOf(null, 2L, 1L), d.chips.map { it.listId })
        assertEquals(listOf("Today", "Work", "Personal"), d.chips.map { it.label })
        assertEquals(listOf(null, 0, 2), d.chips.map { it.colorIndex })
        assertEquals(QuickData.dateLabel(today), d.dateLabel)
        assertNull(QuickData.build(emptyList(), lists, today).next)
    }

    @Test fun chipsFollowTheListsWhenTheyChange() {
        val renamed = lists.map { if (it.id == 2L) it.copy(name = "Office", emoji = "💼", position = 5) else it }
        val d = QuickData.build(emptyList(), renamed, today)
        assertEquals(listOf("Today", "Personal", "🛒 Errands"), d.chips.map { it.label })
        assertEquals(listOf("Today"), QuickData.build(emptyList(), emptyList(), today).chips.map { it.label })
        assertTrue(QuickData.build(emptyList(), lists, today) != d) // distinctUntilChanged sees a change
    }

    @Test fun chipPresets() {
        assertEquals(CapturePreset(dueToday = true), QuickChip(null, "Today", null).preset)
        assertEquals(CapturePreset(listId = 2), QuickChip(2, "Work", 0).preset)
        assertEquals(listOf("Today", "Personal", "Work"), QuickData.Fallback.chips.map { it.label })
    }

    @Test fun chipsThatFitDropFromTheEnd() {
        val chips = QuickData.build(emptyList(), lists, today).chips
        assertEquals(3, chipsThatFit(chips, 400.dp))
        assertTrue(chipsThatFit(chips, 250.dp) in 1..3)
        assertEquals(1, chipsThatFit(chips, 120.dp)) // always at least Today
        assertTrue(chipsThatFit(chips, 250.dp) <= chipsThatFit(chips, 320.dp))
    }

    @Test fun dateLabel() {
        Locale.setDefault(Locale.UK)
        assertEquals("Fri 23", QuickData.dateLabel(today))
    }

    @Test fun pillTree() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 56.dp)); setContext(ctx)
        provideComposable { AddPillContent(ctx) }
        onNode(hasText("Add a task")).assertExists()
        onNode(hasContentDescription("Add a task")).assertExists()
        onNode(hasContentDescription("Add a task by voice")).assertExists()
    }

    @Test fun narrowPillDropsTheMic() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(120.dp, 56.dp)); setContext(ctx)
        provideComposable { AddPillContent(ctx) }
        onNode(hasText("Add a task")).assertExists()
        onNode(hasContentDescription("Add a task by voice")).assertDoesNotExist()
    }

    @Test fun roundTree() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(64.dp, 64.dp)); setContext(ctx)
        provideComposable { AddRoundContent(ctx) }
        onNode(hasContentDescription("Add a task")).assertExists()
    }

    @Test fun quickListsTree() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(400.dp, 64.dp)); setContext(ctx)
        val d = QuickData.build(emptyList(), lists, today)
        provideComposable { QuickListsContent(ctx, d) }
        onNode(hasContentDescription("Add a task")).assertExists()
        onNode(hasContentDescription("Add a task for today")).assertExists()
        onNode(hasContentDescription("Add a task to Work")).assertExists()
        onNode(hasContentDescription("Add a task to Personal")).assertExists()
        onNode(hasContentDescription("Add a task by voice")).assertExists()
        onNode(hasText("Today")).assertExists()
    }

    @Test fun hubTreeWithNextTask() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(200.dp, 200.dp)); setContext(ctx)
        val d = QuickData.build(listOf(Task(id = 1, title = "Call the bank", createdAt = 1, dueDay = td, dueMinute = 600)), lists, today)
        provideComposable { HubContent(ctx, d) }
        onNode(hasText("Call the bank")).assertExists()
        onNode(hasText("Add a task")).assertExists()
        onNode(hasText("Voice")).assertExists()
        onNode(hasText("Plan")).assertExists()
        onNode(hasContentDescription("Plan your day")).assertExists()
        onNode(hasContentDescription("Add a task by voice")).assertExists()
        onNode(hasText("What's next?")).assertDoesNotExist()
    }

    @Test fun hubEmptyAsksWhatsNext() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(160.dp, 160.dp)); setContext(ctx)
        provideComposable { HubContent(ctx, QuickData.build(emptyList(), lists, today)) }
        onNode(hasText("What's next?")).assertExists()
        onNode(hasText("Add a task")).assertExists()
    }

    @Test fun intents() {
        val add = QuickWidgets.capture(ctx)
        assertEquals(CaptureActivity::class.java.name, add.component?.className)
        assertTrue(CapturePreset.of(add).isDefault)
        assertTrue(CapturePreset.of(QuickWidgets.capture(ctx, CapturePreset(voice = true))).voice)
        assertTrue(QuickWidgets.plan(ctx).getBooleanExtra(dev.saikrishna.todo.reminders.PlanReminder.EXTRA_OPEN_PLAN, false))
        assertTrue(QuickWidgets.openApp(ctx).flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun receiversAreRegisteredWidgetProviders() {
        val pm = ctx.packageManager
        val hits = pm.queryBroadcastReceivers(Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(ctx.packageName), android.content.pm.PackageManager.GET_META_DATA)
            .map { it.activityInfo.name }.toSet()
        for (r in listOf(TodayWidgetReceiver::class, AddPillWidgetReceiver::class, AddRoundWidgetReceiver::class, QuickListsWidgetReceiver::class, HubWidgetReceiver::class)) {
            assertTrue("${r.java.name} not in $hits", r.java.name in hits)
            val info = pm.getReceiverInfo(ComponentName(ctx, r.java), android.content.pm.PackageManager.GET_META_DATA)
            assertTrue(info.exported)
            assertTrue(info.metaData.getInt(AppWidgetManagerMeta) != 0)
        }
        // The receivers hand out the right widgets.
        assertTrue(AddPillWidgetReceiver().glanceAppWidget is AddPillWidget)
        assertTrue(AddRoundWidgetReceiver().glanceAppWidget is AddRoundWidget)
        assertTrue(QuickListsWidgetReceiver().glanceAppWidget is QuickListsWidget)
        assertTrue(HubWidgetReceiver().glanceAppWidget is HubWidget)
    }

    @Test fun providerInfoHasPreviewsAndSizes() {
        fun info(xml: Int): Map<String, String> {
            val p = ctx.resources.getXml(xml)
            while (p.next() != org.xmlpull.v1.XmlPullParser.START_TAG) Unit
            return (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
        }
        for (x in listOf(dev.saikrishna.todo.R.xml.add_pill_widget_info, dev.saikrishna.todo.R.xml.add_round_widget_info,
            dev.saikrishna.todo.R.xml.quick_lists_widget_info, dev.saikrishna.todo.R.xml.hub_widget_info)) {
            val a = info(x)
            assertTrue("$x $a", a.containsKey("previewLayout") && a.containsKey("previewImage"))
            assertTrue(a.containsKey("initialLayout") && a.containsKey("description"))
        }
        fun int(x: Int, k: String) = info(x)[k]?.let { Integer.decode(it) }
        assertEquals(0, int(dev.saikrishna.todo.R.xml.add_round_widget_info, "resizeMode"))
        assertEquals(1, int(dev.saikrishna.todo.R.xml.add_pill_widget_info, "targetCellHeight"))
        assertEquals(4, int(dev.saikrishna.todo.R.xml.quick_lists_widget_info, "targetCellWidth"))
        assertEquals(AppWidgetProviderInfo.RESIZE_HORIZONTAL, int(dev.saikrishna.todo.R.xml.quick_lists_widget_info, "resizeMode"))
        assertEquals(AppWidgetProviderInfo.RESIZE_BOTH, int(dev.saikrishna.todo.R.xml.hub_widget_info, "resizeMode"))
    }

    private companion object { const val AppWidgetManagerMeta = "android.appwidget.provider" }
}
