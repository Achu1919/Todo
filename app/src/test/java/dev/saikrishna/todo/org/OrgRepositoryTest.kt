package dev.saikrishna.todo.org

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.NewTask
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TaskRepository
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.data.copySubtasksForRepeat
import dev.saikrishna.todo.domain.RepeatRule
import dev.saikrishna.todo.domain.TemplateCodec
import dev.saikrishna.todo.domain.TemplateItem
import dev.saikrishna.todo.data.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Repository behaviour behind v0.7: subtasks on repeat, complete-all + undo, lists, order, templates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OrgRepositoryTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val wed = LocalDate.of(2026, 10, 21)
    private val now = LocalDateTime.of(2026, 10, 21, 18, 0).atZone(ist).toInstant().toEpochMilli()
    private val db = TodoDatabase.inMemory(ctx)
    private val repo = TaskRepository(db, clock = { now }, zone = { ist })
    private val org = db.orgDao()

    @After fun close() = db.close()

    @Test fun subtasksAreCopiedUncheckedToTheNextOccurrence() = runBlocking {
        val id = repo.add(NewTask("Bins", dueDay = wed.toEpochDay(), repeat = RepeatRule.Daily(1), listId = TaskList.WORK, tags = listOf("home"),
            subtasks = listOf("Blue", "Green", "Black")))
        val subs = org.subtasksOf(id)
        repo.setSubtaskDone(subs[1].id, true)
        repo.orderSubtasks(listOf(subs[2].id, subs[0].id, subs[1].id)) // Black, Blue, Green

        val c = repo.setDone(id, true)
        val next = c.spawnedId!!
        val copied = org.subtasksOf(next)
        assertEquals(listOf("Black", "Blue", "Green"), copied.map { it.title })
        assertTrue(copied.none { it.done })
        val spawned = repo.get(next)!!
        assertEquals(wed.plusDays(1).toEpochDay(), spawned.dueDay)
        assertEquals(TaskList.WORK, spawned.listId)
        assertEquals("home", spawned.tags)
        // The completed one has all its subtasks done.
        assertTrue(org.subtasksOf(id).all { it.done })
    }

    @Test fun pureCopyKeepsOrderAndUnchecks() {
        val subs = listOf(Subtask(5, 1, "b", true, 1), Subtask(4, 1, "a", false, 0))
        assertEquals(listOf(Subtask(0, 9, "a", false, 0), Subtask(0, 9, "b", false, 1)), copySubtasksForRepeat(subs, 9))
    }

    @Test fun completingCompletesAllAndUndoPutsThemBack() = runBlocking {
        val id = repo.add(NewTask("Pack", subtasks = listOf("Passport", "Charger", "Meds")))
        val subs = org.subtasksOf(id)
        repo.setSubtaskDone(subs[0].id, true)
        val c = repo.setDone(id, true)
        assertEquals(3, c.subtasksBefore.size)
        assertTrue(org.subtasksOf(id).all { it.done })
        repo.undoCompletion(id, c, null)
        assertEquals(listOf(true, false, false), org.subtasksOf(id).map { it.done })
        assertEquals(false, repo.get(id)!!.done)
    }

    @Test fun undoOfARepeatingCompletionRemovesTheSpawnedCopy() = runBlocking {
        val id = repo.add(NewTask("Water plants", dueDay = wed.toEpochDay(), repeat = RepeatRule.Daily(2), subtasks = listOf("Balcony")))
        val c = repo.setDone(id, true)
        repo.undoCompletion(id, c, "D:2")
        assertNull(repo.get(c.spawnedId!!))
        assertTrue(org.subtasksOf(c.spawnedId!!).isEmpty())
        assertEquals("D:2", repo.get(id)!!.repeatRule)
    }

    @Test fun deleteAndRestoreBringBackSubtasks() = runBlocking {
        val id = repo.add(NewTask("Trip", subtasks = listOf("Bag")))
        val task = repo.get(id)!!
        val subs = org.subtasksOf(id)
        repo.delete(listOf(id))
        assertTrue(org.subtasksOf(id).isEmpty())
        repo.restore(listOf(task), subs)
        assertEquals(listOf("Bag"), org.subtasksOf(id).map { it.title })
    }

    @Test fun deleteListMovesItsTasks() = runBlocking {
        val home = repo.createList("Home", 3, null, dev.saikrishna.todo.data.ListArea.Personal)!!
        val a = repo.add(NewTask("Fix tap", listId = home))
        repo.deleteList(home, TaskList.WORK)
        assertEquals(TaskList.WORK, repo.get(a)!!.listId)
        assertEquals(listOf("Personal", "Work"), org.lists().map { it.name })
        // Built-ins cannot be deleted.
        repo.deleteList(TaskList.WORK, TaskList.PERSONAL)
        assertEquals(2, org.lists().size)
    }

    @Test fun reorderAndDragToDayArePersisted() = runBlocking {
        val a = repo.add(NewTask("A", dueDay = wed.plusDays(1).toEpochDay(), dueMinute = 9 * 60))
        repo.reorder(mapOf(a to 5L), mapOf(a to wed.plusDays(4).toEpochDay()))
        val t = repo.get(a)!!
        assertEquals(5L, t.sortOrder)
        assertEquals(wed.plusDays(4).toEpochDay(), t.dueDay)
        assertEquals(9 * 60, t.dueMinute)
    }

    @Test fun usingATemplateInsertsInOrderWithSubtasks() = runBlocking {
        val tpl = repo.templates.first().first { it.name == "Trip packing" }
        val ids = repo.useTemplate(tpl, wed, TaskList.PERSONAL)
        assertEquals(4, ids.size)
        val tasks = ids.map { repo.get(it)!! }
        assertEquals(listOf("Pack the bag", "Check in online", "Book a cab to the airport", "Water the plants before leaving"), tasks.map { it.title })
        assertEquals(tasks.map { it.sortOrder }.sorted(), tasks.map { it.sortOrder }) // consecutive keys keep the order
        assertEquals(wed.plusDays(1).toEpochDay(), tasks[3].dueDay)
        assertEquals(5, org.subtasksOf(ids[0]).size)
        assertEquals("travel", tasks[0].tags)
    }

    @Test fun codecRoundTrip() {
        val items = listOf(
            TemplateItem("A", TaskList.WORK, 1, 600, Priority.High, listOf("x", "y"), listOf("s")),
            TemplateItem("B"),
        )
        assertEquals(items, TemplateCodec.decode(TemplateCodec.encode(items)))
        assertTrue(TemplateCodec.decode("not json").isEmpty())
    }
}
