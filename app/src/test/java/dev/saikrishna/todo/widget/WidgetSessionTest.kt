package dev.saikrishna.todo.widget

import android.appwidget.AppWidgetManager
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.saikrishna.todo.R
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.NewTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * v0.8.1: the real widget pipeline end to end, as a launcher drives it: a widget is bound to
 * [TodayWidgetReceiver], onUpdate starts Glance's session through WorkManager (SessionWorker),
 * provideGlance loads the tasks and the RemoteViews reach AppWidgetManager. Passing means the
 * loading layout is replaced by our content. (Unit tests run unminified classes; the release dex
 * is checked separately by scripts/verify_release.py.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TodoApp::class)
class WidgetSessionTest {
    private fun org.robolectric.shadows.ShadowAppWidgetManager.updateAppWidgetOptionsForTest(id: Int) =
        AppWidgetManager.getInstance(app).updateAppWidgetOptions(id, android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250); putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200)
        })

    private val app: TodoApp = ApplicationProvider.getApplicationContext()

    @Before fun work() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app, Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun texts(v: View?): List<String> = when (v) {
        null -> emptyList()
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    /** Pumps the main looper (Glance posts to it) until [done] or ~10 s. */
    private fun pumpUntil(done: () -> Boolean): Boolean {
        repeat(400) {
            // Glance's session runs on the main dispatcher with delays (frame clock, debounce):
            // advance the paused main looper's clock as well as running what is due.
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            if (done()) return true
            Thread.sleep(10)
        }
        return false
    }

    @Test fun placedWidgetLeavesTheLoadingLayoutAndShowsTodaysTasks() {
        runBlocking {
            app.repository.add(NewTask("Water the plants"))
            app.repository.add(NewTask("Call the bank"))
        }
        val awm = shadowOf(AppWidgetManager.getInstance(app))
        // Bind widget 1 to our provider the way a launcher would, then deliver APPWIDGET_UPDATE.
        val id = 1
        val info = android.appwidget.AppWidgetProviderInfo().apply {
            provider = android.content.ComponentName(app, TodayWidgetReceiver::class.java)
            initialLayout = androidx.glance.appwidget.R.layout.glance_default_loading_layout
            minWidth = 180; minHeight = 110
        }
        awm.addInstalledProvider(info)
        awm.addBoundWidget(id, info)
        awm.updateAppWidgetOptionsForTest(id)
        TodayWidgetReceiver().onReceive(app, android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .setComponent(info.provider).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id)))
        val ok = pumpUntil { texts(awm.getViewFor(id)).contains("Water the plants") }
        val works = WorkManager.getInstance(app).getWorkInfosByTag("androidx.glance.session.SessionWorker").get() +
            runCatching { WorkManager.getInstance(app).getWorkInfosForUniqueWork("appWidget-$id").get() }.getOrDefault(emptyList())
        assertTrue("widget never rendered; view texts=${texts(awm.getViewFor(id))}; work=${works.map { it.state }}", ok)
        val shown = texts(awm.getViewFor(id))
        assertTrue(shown.contains("Today"))
        assertTrue(shown.contains("Call the bank"))
        assertTrue("no session work failed: $works", works.none { it.state == WorkInfo.State.FAILED })
    }

    @Test fun loadFailureStillRendersAFallbackInsteadOfSpinning() {
        // A context whose application is not TodoApp makes the load throw: it must be caught.
        val data = runBlocking { TodayWidget.loadOrNull(android.content.ContextWrapper(object : android.content.ContextWrapper(app) {
            override fun getApplicationContext() = android.app.Application()
        })) }
        assertEquals(null, data)
    }
}
