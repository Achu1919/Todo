package dev.saikrishna.todo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.saikrishna.todo.domain.TemplateCodec
import dev.saikrishna.todo.domain.Templates

@Database(entities = [Task::class, TaskList::class, Subtask::class, TemplateEntity::class, SavedView::class], version = 5, exportSchema = true)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun orgDao(): OrgDao

    companion object {
        /**
         * v0.5 (schema 2) → v0.6 (schema 3): due time + repeat rule. Both nullable, no defaults
         * needed, so existing rows are kept exactly as they were (tested in MigrationTest).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `due_minute` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeat_rule` TEXT")
            }
        }

        /**
         * v0.6 (schema 3) → v0.7 (schema 4): organisation. Every existing task goes to Personal
         * (list_id default 1), has no tags, and keeps its order (sort_order = created_at). New
         * tables: lists (+ the built-in Personal and Work), subtasks, templates (+ two examples).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `list_id` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `tasks` SET `sort_order` = `created_at`")
                createOrgTables(db)
                seed(db)
            }
        }

        /**
         * v0.7 (schema 4) → v0.8 (schema 5): saved search views. A new table only; every
         * existing table and row is untouched (MigrationTest 4 → 5 and 2 → 5).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = createViewsTable(db)
        }

        private fun createViewsTable(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `saved_views` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `query` TEXT NOT NULL DEFAULT '', `filter` TEXT NOT NULL DEFAULT '', `position` INTEGER NOT NULL DEFAULT 0)")
        }

        /** Same SQL as Room generates for schema 4 (checked by Room's validation in MigrationTest). */
        private fun createOrgTables(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL DEFAULT 0, `emoji` TEXT, `position` INTEGER NOT NULL DEFAULT 0, `area` INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `subtasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `task_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `done` INTEGER NOT NULL DEFAULT 0, `position` INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_task_id` ON `subtasks` (`task_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `body` TEXT NOT NULL, `position` INTEGER NOT NULL DEFAULT 0)")
        }

        /** Built-in lists and the example templates (fresh installs and the 3 → 4 migration). */
        fun seed(db: SupportSQLiteDatabase) {
            for (l in TaskList.Defaults) {
                db.insert("lists", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                    put("id", l.id); put("name", l.name); put("color", l.color); put("position", l.position); put("area", l.areaCode)
                })
            }
            Templates.Seeds.forEachIndexed { i, (name, items) ->
                db.insert("templates", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                    put("name", name); put("body", TemplateCodec.encode(items)); put("position", i)
                })
            }
        }

        private val SeedOnCreate = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
        }

        fun build(context: Context): TodoDatabase =
            Room.databaseBuilder(context, TodoDatabase::class.java, "todo.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(SeedOnCreate)
                // Only the pre-release schema 1 (never shipped to Sai) may still be wiped.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()

        /** Tests and previews: an in-memory database with the same seed. */
        fun inMemory(context: Context): TodoDatabase =
            Room.inMemoryDatabaseBuilder(context, TodoDatabase::class.java)
                .addCallback(SeedOnCreate)
                .allowMainThreadQueries()
                .build()
    }
}
