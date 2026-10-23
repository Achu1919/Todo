package dev.saikrishna.todo.data

import android.app.Application
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** v0.13: pausing a repeat, the reminder lead, and both in backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RepeatPauseTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 23, 18, 0).atZone(ist).toInstant().toEpochMilli()
    private val day = LocalDate.of(2026, 10, 23).toEpochDay()

    @Test fun pausedRepeatSpawnsNothingAndKeepsItsRule() = runBlocking {
        val db = TodoDatabase.inMemory(ctx)
        val repo = TaskRepository(db, clock = { now }, zone = { ist })
        val id = repo.add(NewTask("Water plants", dueDay = day, repeat = RepeatRule.Daily(1)))
        repo.setRepeatPaused(id, true)
        assertNull(repo.setDone(id, true).spawnedId)
        val t = repo.get(id)!!
        assertEquals(RepeatRule.Daily(1), t.repeat) // kept for when it is resumed
        assertEquals(1, db.taskDao().observeAll().first().size)
        // Resumed: completing again (re-opened) spawns the next one.
        repo.setDone(id, false)
        repo.setRepeatPaused(id, false)
        val next = repo.setDone(id, true).spawnedId!!
        assertEquals(day + 1, repo.get(next)!!.dueDay)
        assertEquals(false, repo.get(next)!!.repeatPaused)
        db.close()
    }

    @Test fun reminderLeadMovesTheAlarmAndIsCopiedToTheNextOccurrence() = runBlocking {
        val db = TodoDatabase.inMemory(ctx)
        val repo = TaskRepository(db, clock = { now }, zone = { ist })
        val id = repo.add(NewTask("Call mom", dueDay = day + 1, dueMinute = 18 * 60, repeat = RepeatRule.Daily(1), remindBefore = 10))
        val t = repo.get(id)!!
        assertEquals(t.dueAtMillis(ist)!! - 10 * 60_000L, t.remindAtMillis(ist))
        repo.setRemindBefore(id, 60 * 24 * 30) // clamped to a week
        assertEquals(MaxRemindBefore, repo.get(id)!!.remindBefore)
        repo.setRemindBefore(id, 60)
        val next = repo.setDone(id, true).spawnedId!!
        assertEquals(60, repo.get(next)!!.remindBefore)
        assertNull(Task(title = "x", createdAt = 0, dueDay = day).remindAtMillis(ist)) // untimed
        db.close()
    }

    @Test fun backupKeepsPauseAndLeadAndOldFilesDefault() {
        val t = Task(id = 4, title = "Rent", createdAt = 1, dueDay = day, dueMinute = 600, repeatRule = "M:1", repeatPaused = true, remindBefore = 1440)
        val b = Backup(BackupCodec.Schema, 0, "0.13.0", listOf(t), emptyList(), TaskList.Defaults, emptyList(), emptyList(), null)
        assertEquals(listOf(t), BackupCodec.decode(BackupCodec.encode(b)).tasks)
        val old = BackupCodec.encode(b.copy(tasks = listOf(t.copy(repeatPaused = false, remindBefore = 0))))
        assertEquals(false, old.contains("repeatPaused"))
        val d = BackupCodec.decode(old).tasks.single()
        assertEquals(false, d.repeatPaused)
        assertEquals(0, d.remindBefore)
    }
}
