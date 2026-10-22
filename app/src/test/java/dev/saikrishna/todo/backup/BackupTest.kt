package dev.saikrishna.todo.backup

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.Backup
import dev.saikrishna.todo.data.BackupCodec
import dev.saikrishna.todo.data.BackupException
import dev.saikrishna.todo.data.BackupManager
import dev.saikrishna.todo.data.ImportMode
import dev.saikrishna.todo.data.SavedView
import dev.saikrishna.todo.data.Subtask
import dev.saikrishna.todo.data.Task
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TemplateEntity
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.data.TodoDatabase
import dev.saikrishna.todo.reminders.Reminders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** v0.10 backup: codec, merge/replace, validation, rollback, reminder re-arming. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TodoApp::class)
class BackupTest {
    private val app = ApplicationProvider.getApplicationContext<TodoApp>()
    private lateinit var db: TodoDatabase
    private lateinit var other: TodoDatabase
    private val now = 1_792_000_000_000L // 2026-10-14 IST
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 22).toEpochDay()

    @Before fun open() { db = TodoDatabase.inMemory(app); other = TodoDatabase.inMemory(app) }
    @After fun close() { db.close(); other.close() }

    private fun manager(d: TodoDatabase, after: suspend (List<Task>, List<Task>) -> Unit = { _, _ -> }) =
        BackupManager(d, app.settings, "0.10.0", clock = { now }, afterImport = after)

    private fun task(id: Long, title: String, created: Long = now - id * 1000, list: Long = TaskList.PERSONAL, done: Boolean = false, day: Long? = null, minute: Int? = null) =
        Task(id = id, title = title, createdAt = created, listId = list, done = done, dueDay = day, dueMinute = minute, sortOrder = created, tags = "home errands", priorityCode = 1, notes = "n$id", repeatRule = if (id == 2L) "D1" else null)

    private fun seed(d: TodoDatabase) = runBlocking {
        val dao = d.backupDao()
        dao.putLists(listOf(TaskList(3, "Home", color = 4, emoji = "🏠", position = 2)))
        dao.putTasks(listOf(task(1, "Pay rent", day = today), task(2, "Water plants", list = 3), task(3, "Old thing", done = true)))
        dao.putSubtasks(listOf(Subtask(10, 1, "Transfer", false, 0), Subtask(11, 1, "Receipt", true, 1)))
        dao.putTemplates(listOf(TemplateEntity(5, "Trip", "Pack\nTickets", 0)))
        dao.putViews(listOf(SavedView(6, "Urgent", "!high", "today", 0)))
    }

    private data class Dump(val tasks: List<Task>, val subs: List<Subtask>, val lists: List<TaskList>, val templates: List<TemplateEntity>, val views: List<SavedView>)
    private fun dump(d: TodoDatabase) = runBlocking { d.backupDao().let { Dump(it.tasks(), it.subtasks(), it.lists(), it.templates(), it.views()) } }

    @Test fun roundTripExportImportIsEqual() = runBlocking {
        seed(db)
        app.settings.setTheme(ThemeMode.Dark); app.settings.setHaptics(false)
        val json = manager(db).export()
        val decoded = BackupCodec.decode(json)
        assertEquals(BackupCodec.Schema, decoded.schema)
        assertEquals("3 tasks, 3 lists", decoded.summary.headline)
        // Restore into a different, fresh DB with different settings.
        app.settings.setTheme(ThemeMode.Light); app.settings.setHaptics(true)
        manager(other).import(decoded, ImportMode.Replace)
        assertEquals(dump(db), dump(other))
        val s = app.settings.settings.first()
        assertEquals(ThemeMode.Dark, s.theme); assertFalse(s.haptics)
        // Encoding the restored DB gives the same document (minus nothing): stable format.
        assertEquals(JSONObject(json).getJSONArray("tasks").toString(), JSONObject(manager(other).export()).getJSONArray("tasks").toString())
        app.settings.setTheme(ThemeMode.System); app.settings.setHaptics(true)
    }

    @Test fun mergeDedupesById() = runBlocking {
        seed(db)
        val local = dump(db)
        val b = BackupCodec.decode(manager(db).export())
        // Same file again: nothing duplicated, nothing changed.
        val r0 = manager(db).import(b, ImportMode.Merge)
        assertEquals(local, dump(db)); assertEquals(0, r0.added); assertEquals(3, r0.skipped)
        // Edited on "another phone": same id + createdAt = same task (updated); a new id = added;
        // a different task that happens to reuse id 3 (other createdAt) is added under a new id.
        val edited = b.copy(
            tasks = listOf(
                b.tasks[0].copy(title = "Pay rent (done online)"),
                b.tasks[1],
                b.tasks[2].copy(createdAt = 5, title = "Someone else's 3"),
                task(40, "Brand new"),
            ),
            subtasks = b.subtasks + Subtask(99, 3, "Child of other 3", false, 0),
            lists = b.lists + TaskList(9, "home"), // same name as list 3 (case-insensitive): mapped, not duplicated
        )
        val r = manager(db).import(edited, ImportMode.Merge)
        assertEquals(2, r.added); assertEquals(1, r.updated); assertEquals(1, r.skipped)
        val after = dump(db)
        assertEquals(5, after.tasks.size)
        assertEquals("Pay rent (done online)", after.tasks.first { it.id == 1L }.title)
        assertEquals("Old thing", after.tasks.first { it.id == 3L }.title) // local 3 kept
        val moved = after.tasks.first { it.title == "Someone else's 3" }
        assertTrue(moved.id !in setOf(1L, 2L, 3L, 40L))
        assertEquals(moved.id, after.subs.first { it.title == "Child of other 3" }.taskId) // subtask followed
        assertEquals(local.lists.size, after.lists.size)
        assertEquals(local.templates, after.templates); assertEquals(local.views, after.views)
    }

    @Test fun replaceSwapsEverythingAndKeepsBuiltInLists() = runBlocking {
        seed(db)
        val b = Backup(1, now, "x", listOf(task(7, "Only task", list = 42)), emptyList(), emptyList(), emptyList(), emptyList(), null)
        manager(db).import(b, ImportMode.Replace)
        val d = dump(db)
        assertEquals(listOf("Only task"), d.tasks.map { it.title })
        assertEquals(TaskList.PERSONAL, d.tasks[0].listId) // unknown list → Personal
        assertEquals(setOf(TaskList.PERSONAL, TaskList.WORK), d.lists.map { it.id }.toSet())
        assertTrue(d.subs.isEmpty() && d.templates.isEmpty() && d.views.isEmpty())
    }

    @Test fun corruptFilesAreRejectedWithAMessage() {
        val good = JSONObject(runBlocking { seed(db); manager(db).export() })
        fun bad(text: String, expect: String) {
            try { BackupCodec.decode(text); fail("accepted: $text") } catch (e: BackupException) { assertTrue("${e.message}", e.message!!.contains(expect)) }
        }
        bad("", "not valid JSON")
        bad("{\"tasks\": [", "not valid JSON")
        bad("[1,2,3]", "not valid JSON")
        bad("{\"format\":\"something.else\",\"schema\":1}", "isn't a")
        bad(JSONObject(good.toString()).put("schema", 99).toString(), "newer version")
        bad(JSONObject(good.toString()).put("schema", 0).toString(), "isn't supported")
        bad(JSONObject(good.toString()).put("tasks", "nope").toString(), "damaged")
        bad(JSONObject(good.toString()).apply { getJSONArray("tasks").getJSONObject(0).remove("title") }.toString(), "damaged")
        bad(JSONObject(good.toString()).apply { getJSONArray("tasks").getJSONObject(0).put("id", -4) }.toString(), "bad id")
        bad(JSONObject(good.toString()).apply { getJSONArray("tasks").put(getJSONArray("tasks").getJSONObject(0)) }.toString(), "duplicate task ids")
    }

    @Test fun olderSchemaFileWithMissingSectionsImports() = runBlocking {
        // A minimal schema-1 file: only tasks (sections added later are optional and read as empty).
        val minimal = """{"format":"${BackupCodec.Format}","schema":1,"tasks":[{"id":1,"title":"From an old file","createdAt":5}]}"""
        val b = BackupCodec.decode(minimal)
        assertEquals(1, b.tasks.size); assertTrue(b.lists.isEmpty()); assertEquals(null, b.settings?.length()?.takeIf { it > 0 })
        assertEquals(5L, b.tasks[0].sortOrder) // defaults filled
        manager(db).import(b, ImportMode.Merge)
        assertTrue(dump(db).tasks.any { it.title == "From an old file" })
    }

    @Test fun failedImportRollsBackCompletely() = runBlocking {
        seed(db)
        val before = dump(db)
        val b = BackupCodec.decode(manager(db).export()).let { it.copy(tasks = it.tasks.map { t -> t.copy(title = "changed") }) }
        for (mode in ImportMode.entries) {
            val m = manager(db).apply { faultForTest = { throw IllegalStateException("disk full") } }
            try { m.import(b, mode); fail("no throw") } catch (_: IllegalStateException) { }
            assertEquals("$mode left the DB changed", before, dump(db))
        }
    }

    @Test fun importReArmsRemindersAndCancelsRemovedOnes() = runBlocking {
        val future = LocalDate.now(ZoneId.systemDefault()).plusDays(2).toEpochDay()
        db.backupDao().putTasks(listOf(task(1, "Gone after replace", day = future, minute = 9 * 60)))
        val calls = ArrayList<Pair<List<Long>, List<Long>>>()
        val ctx: Context = app
        val m = manager(db) { before, after ->
            calls += before.map { it.id } to after.map { it.id }
            Reminders.rearm(ctx, before, after, System.currentTimeMillis(), ZoneId.systemDefault())
        }
        Reminders.sync(ctx, db.backupDao().tasks()[0])
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        assertEquals(1, alarms.scheduledAlarms.size)
        val b = Backup(1, now, "x", listOf(task(8, "Dentist", day = future, minute = 10 * 60), task(9, "No time")), emptyList(), emptyList(), emptyList(), emptyList(), null)
        m.import(b, ImportMode.Replace)
        assertEquals(listOf(listOf(1L) to listOf(8L, 9L)), calls)
        // Task 1's alarm was cancelled, task 8's armed; task 9 has no time.
        val scheduled = alarms.scheduledAlarms
        assertEquals(1, scheduled.size)
        val at = LocalDate.ofEpochDay(future).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(at, scheduled[0].triggerAtTime)
    }

    @Test fun markdownSharesOpenTasksAsAChecklist() {
        val lists = listOf(TaskList(1, "Personal"), TaskList(3, "Home"))
        val md = BackupCodec.markdown(
            listOf(task(1, "Pay rent", day = 20_000), task(2, "Water plants", list = 3), task(3, "Done one", done = true)),
            lists, zone,
        )
        assertEquals(
            "## Personal\n- [ ] Pay rent (2024-10-04) #home #errands\n\n## Home\n- [ ] Water plants #home #errands\n",
            md,
        )
        assertEquals("Nothing open.", BackupCodec.markdown(emptyList(), lists, zone))
    }

    @Test fun fileNameAndSummary() {
        assertEquals("todo-backup-2026-10-22.json", BackupCodec.fileName(LocalDate.of(2026, 10, 22)))
        val b = Backup(1, now, "x", listOf(task(1, "a"), task(2, "b", done = true)), emptyList(), listOf(TaskList(1, "P")), emptyList(), emptyList(), null)
        assertEquals("2 tasks, 1 list", b.summary.headline)
        assertEquals("1 open", b.summary.detail)
        assertNotNull(BackupManager.exportedLabel(now, zone))
        assertEquals("14 Oct 2026", BackupManager.exportedLabel(now, zone))
    }
}
