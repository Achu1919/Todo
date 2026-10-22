package dev.saikrishna.todo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** v0.10 backup: whole-table reads and writes (always used inside one transaction). */
@Dao
interface BackupDao {
    @Query("SELECT * FROM tasks ORDER BY id") suspend fun tasks(): List<Task>
    @Query("SELECT * FROM subtasks ORDER BY id") suspend fun subtasks(): List<Subtask>
    @Query("SELECT * FROM lists ORDER BY id") suspend fun lists(): List<TaskList>
    @Query("SELECT * FROM templates ORDER BY id") suspend fun templates(): List<TemplateEntity>
    @Query("SELECT * FROM saved_views ORDER BY id") suspend fun views(): List<SavedView>

    @Query("DELETE FROM subtasks") suspend fun clearSubtasks()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM lists") suspend fun clearLists()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("DELETE FROM saved_views") suspend fun clearViews()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasks(items: List<Task>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSubtasks(items: List<Subtask>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLists(items: List<TaskList>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTemplates(items: List<TemplateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putViews(items: List<SavedView>)

    @Insert suspend fun addTask(t: Task): Long
    @Insert suspend fun addList(l: TaskList): Long
    @Insert suspend fun addSubtask(s: Subtask): Long
    @Insert suspend fun addTemplate(t: TemplateEntity): Long
    @Insert suspend fun addView(v: SavedView): Long
}
