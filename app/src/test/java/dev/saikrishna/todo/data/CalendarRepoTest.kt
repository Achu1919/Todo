package dev.saikrishna.todo.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.domain.RepeatRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** v0.13: Calendar drag reschedule and the Recurring view's stop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CalendarRepoTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 23, 18, 0).atZone(ist).toInstant().toEpochMilli()
    private val fri = LocalDate.of(2026, 10, 23)

    @Test fun moveToDayKeepsTimeAndMovesTheSeries() = runBlocking {
        val repo = TaskRepository(TodoDatabase.inMemory(ctx), clock = { now }, zone = { ist })
        val id = repo.add(NewTask("Gym", dueDay = fri.toEpochDay(), dueMinute = 7 * 60, repeat = RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY))))
        assertTrue(repo.moveToDay(id, LocalDate.of(2026, 10, 28)))
        val t = repo.get(id)!!
        assertEquals(LocalDate.of(2026, 10, 28).toEpochDay(), t.dueDay)
        assertEquals(7 * 60, t.dueMinute)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.WEDNESDAY)), t.repeat)
        assertFalse(repo.moveToDay(id, LocalDate.of(2026, 10, 28))) // same day: nothing
        val plain = repo.add(NewTask("Call", dueDay = fri.toEpochDay()))
        assertTrue(repo.moveToDay(plain, fri.plusDays(1)))
        assertNull(repo.get(plain)!!.dueMinute)
        assertNull(repo.get(plain)!!.repeat)
    }

    @Test fun stopRepeatClearsRuleAndPause() = runBlocking {
        val repo = TaskRepository(TodoDatabase.inMemory(ctx), clock = { now }, zone = { ist })
        val id = repo.add(NewTask("Water plants", dueDay = fri.toEpochDay(), repeat = RepeatRule.Daily(1)))
        repo.setRepeatPaused(id, true)
        repo.stopRepeat(id)
        val t = repo.get(id)!!
        assertNull(t.repeat)
        assertFalse(t.repeatPaused)
        assertEquals(fri.toEpochDay(), t.dueDay) // the task itself stays
        assertNull(repo.setDone(id, true).spawnedId)
    }
}
