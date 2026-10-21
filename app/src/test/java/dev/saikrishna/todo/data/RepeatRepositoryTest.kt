package dev.saikrishna.todo.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.domain.RepeatRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RepeatRepositoryTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val ist = ZoneId.of("Asia/Kolkata")
    /** Wed 21 Oct 2026, 18:00 IST. */
    private val now = LocalDateTime.of(2026, 10, 21, 18, 0).atZone(ist).toInstant().toEpochMilli()

    @Test fun completingARepeatingTaskSpawnsTheNextOne() = runBlocking {
        val db = TodoDatabase.inMemory(ctx)
        val repo = TaskRepository(db, clock = { now }, zone = { ist })
        val id = repo.add(
            NewTask("Bins", dueDay = LocalDate.of(2026, 10, 19).toEpochDay(), dueMinute = 7 * 60,
                priority = Priority.High, repeat = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))),
        )
        repo.setText(id, "Bins", "Blue and green")

        val next = repo.setDone(id, true).spawnedId!!
        val spawned = repo.get(next)!!
        assertEquals(LocalDate.of(2026, 10, 22).toEpochDay(), spawned.dueDay) // done Wed → Thu
        assertEquals(7 * 60, spawned.dueMinute)
        assertEquals("Blue and green", spawned.notes)
        assertEquals(Priority.High.code, spawned.priorityCode)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), spawned.repeat)
        assertEquals(false, spawned.done)

        val old = repo.get(id)!!
        assertEquals(true, old.done)
        assertNull(old.repeatRule) // the rule moved to the new occurrence

        // Re-open + complete again: no duplicate occurrence.
        repo.setDone(id, false)
        assertNull(repo.setDone(id, true).spawnedId)
        assertEquals(2, db.taskDao().observeAll().first().size)
        db.close()
    }

    @Test fun nonRepeatingCompletionSpawnsNothing() = runBlocking {
        val db = TodoDatabase.inMemory(ctx)
        val repo = TaskRepository(db, clock = { now }, zone = { ist })
        val id = repo.add(NewTask("Once"))
        assertNull(repo.setDone(id, true).spawnedId)
        db.close()
    }
}
