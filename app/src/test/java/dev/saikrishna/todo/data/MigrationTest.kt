package dev.saikrishna.todo.data

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.domain.RepeatRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v0.5 → v0.6 keeps Sai's data. Builds a REAL schema-2 database from the exported
 * `schemas/.../2.json` (exact v0.5 CREATE statements + identity hash), fills it, then opens it with
 * the production builder ([TodoDatabase.build]). Room runs MIGRATION_2_3 and validates the result
 * against schema 3, so a wrong migration fails here (and a destructive fallback would lose rows).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MigrationTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val name = "todo.db"

    @Before @After fun clean() { ctx.deleteDatabase(name) }

    private fun createV2(fill: SupportSQLiteDatabase.() -> Unit) = createFromSchema(2, fill)

    /** A REAL database at [version], built from the exported schema JSON (exact CREATE statements). */
    private fun createFromSchema(version: Int, fill: SupportSQLiteDatabase.() -> Unit) {
        val json = JSONObject(File("schemas/dev.saikrishna.todo.data.TodoDatabase/$version.json").readText()).getJSONObject("database")
        val entities = json.getJSONArray("entities")
        val setup = json.getJSONArray("setupQueries")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (i in 0 until entities.length()) {
                            val e = entities.getJSONObject(i)
                            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                        }
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("unexpected")
                },
            ).build(),
        )
        helper.writableDatabase.fill()
        helper.close()
    }

    @Test fun migrate2To3KeepsEveryRowAndColumn() = runBlocking {
        createV2 {
            execSQL("INSERT INTO tasks (id, title, notes, done, created_at, completed_at, due_day, priority) VALUES (1, 'Pay rent', 'HDFC', 0, 100, NULL, 20385, 2)")
            execSQL("INSERT INTO tasks (id, title, notes, done, created_at, completed_at, due_day, priority) VALUES (2, 'Gym', '', 1, 200, 300, NULL, 0)")
            execSQL("INSERT INTO tasks (id, title, notes, done, created_at, completed_at, due_day, priority) VALUES (7, 'Book dentist', 'Dr Rao', 0, 400, 350, 20390, 1)")
        }

        val db = TodoDatabase.build(ctx)
        val rows = db.taskDao().observeAll().first()
        assertEquals(
            listOf(
                Task(id = 1, title = "Pay rent", notes = "HDFC", done = false, createdAt = 100, completedAt = null, dueDay = 20385, priorityCode = 2),
                Task(id = 2, title = "Gym", notes = "", done = true, createdAt = 200, completedAt = 300, dueDay = null, priorityCode = 0),
                Task(id = 7, title = "Book dentist", notes = "Dr Rao", done = false, createdAt = 400, completedAt = 350, dueDay = 20390, priorityCode = 1),
            ),
            rows,
        )
        rows.forEach { assertNull(it.dueMinute); assertNull(it.repeatRule) }

        // New columns are writable, and AUTOINCREMENT continues after the old max id.
        val repo = TaskRepository(db)
        val id = repo.add(NewTask("Standup", dueDay = 20385, dueMinute = 600, repeat = RepeatRule.Weekdays))
        assertEquals(8L, id)
        val t = db.taskDao().get(id)!!
        assertEquals(600, t.dueMinute)
        assertEquals(RepeatRule.Weekdays, t.repeat)
        db.close()
    }

    /**
     * v0.6.2 (schema 3) → v0.7 (schema 4): every task kept exactly, moved to Personal, no tags,
     * order kept (sort_order = created_at); built-in lists and the two example templates exist;
     * new tables work; ids continue.
     */
    @Test fun migrate3To4KeepsEverythingAndAddsOrganisation() = runBlocking {
        createFromSchema(3) {
            execSQL("INSERT INTO tasks (id, title, notes, done, created_at, completed_at, due_day, priority, due_minute, repeat_rule) VALUES (1, 'Pay rent', 'HDFC', 0, 100, NULL, 20385, 2, 600, 'M:1')")
            execSQL("INSERT INTO tasks (id, title, notes, done, created_at, completed_at, due_day, priority, due_minute, repeat_rule) VALUES (4, 'Gym', '', 1, 200, 300, NULL, 0, NULL, NULL)")
            execSQL("INSERT INTO tasks (id, title, notes, done, created_at, completed_at, due_day, priority, due_minute, repeat_rule) VALUES (9, 'Standup', 'Zoom', 0, 50, NULL, 20386, 1, 570, 'WD')")
        }

        val db = TodoDatabase.build(ctx)
        val rows = db.taskDao().observeAll().first()
        assertEquals(
            listOf(
                Task(id = 9, title = "Standup", notes = "Zoom", done = false, createdAt = 50, dueDay = 20386, priorityCode = 1, dueMinute = 570, repeatRule = "WD"),
                Task(id = 1, title = "Pay rent", notes = "HDFC", done = false, createdAt = 100, dueDay = 20385, priorityCode = 2, dueMinute = 600, repeatRule = "M:1"),
                Task(id = 4, title = "Gym", notes = "", done = true, createdAt = 200, completedAt = 300),
            ),
            rows,
        )
        rows.forEach {
            assertEquals(TaskList.PERSONAL, it.listId)
            assertEquals("", it.tags)
            assertEquals(it.createdAt, it.sortOrder)
        }

        val org = db.orgDao()
        assertEquals(listOf("Personal", "Work"), org.lists().map { it.name })
        assertEquals(listOf(ListArea.Personal, ListArea.Work), org.lists().map { it.area })
        val templates = org.observeTemplates().first()
        assertEquals(listOf("Weekly review", "Trip packing"), templates.map { it.name })
        assertEquals(4, dev.saikrishna.todo.domain.TemplateCodec.decode(templates[1].body).size)

        // The new columns and tables are usable, and ids continue after the old max.
        val repo = TaskRepository(db)
        val id = repo.add(NewTask("Ship v0.7", listId = TaskList.WORK, tags = listOf("Release"), subtasks = listOf("Build", "Notes")))
        assertEquals(10L, id)
        val t = db.taskDao().get(id)!!
        assertEquals(TaskList.WORK, t.listId)
        assertEquals("release", t.tags)
        assertEquals(listOf("Build", "Notes"), org.subtasksOf(id).map { it.title })
        val listId = repo.createList("Home", 3, "🏠", ListArea.Personal)!!
        assertEquals(3L, listId)
        db.close()
    }
}
