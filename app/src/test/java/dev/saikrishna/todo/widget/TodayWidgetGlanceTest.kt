package dev.saikrishna.todo.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import dev.saikrishna.todo.data.Task
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** v0.8.1: the widget's Glance tree (runGlanceAppWidgetUnitTest): rows, actions, fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TodayWidgetGlanceTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun rowsAndActions() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 200.dp))
        setContext(ctx)
        val data = WidgetData.build(listOf(
            Task(id = 1, title = "Call the bank", createdAt = 1, dueDay = today.toEpochDay() - 1),
            Task(id = 2, title = "Water the plants", createdAt = 2),
        ), today)
        provideComposable { TodayWidgetContent(ctx, data) }
        onNode(hasText("Today")).assertExists()
        onNode(hasText("Call the bank")).assertExists()
        onNode(hasText("Overdue")).assertExists()
        onNode(hasContentDescription("Complete Water the plants")).assertExists()
        onNode(hasContentDescription("Add a task")).assertExists()
    }

    @Test fun emptyAndFailed() {
        runGlanceAppWidgetUnitTest {
            setContext(ctx)
            provideComposable { TodayWidgetContent(ctx, WidgetData(emptyList(), 0, 0)) }
            onNode(hasText("All clear")).assertExists()
        }
        runGlanceAppWidgetUnitTest {
            setContext(ctx)
            provideComposable { TodayWidgetContent(ctx, null) }
            onNode(hasText("Couldn't load tasks · tap to open Todo")).assertExists()
        }
    }
}
