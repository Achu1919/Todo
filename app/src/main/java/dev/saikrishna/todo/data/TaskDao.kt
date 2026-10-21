package dev.saikrishna.todo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /** Oldest first, so new tasks land at the bottom, nearest the composer (spec 6.4). */
    @Query("SELECT * FROM tasks ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    /** Undo: puts deleted rows back with their original ids. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    /** Marks done and stamps the completion time. */
    @Query("UPDATE tasks SET done = 1, completed_at = :at WHERE id = :id")
    suspend fun markDone(id: Long, at: Long)

    /** Re-opens a task. completed_at is kept as "last completed" so the Done list order stays stable. */
    @Query("UPDATE tasks SET done = 0 WHERE id = :id")
    suspend fun markOpen(id: Long)

    @Query("UPDATE tasks SET title = :title, notes = :notes WHERE id = :id")
    suspend fun setText(id: Long, title: String, notes: String)

    /** Date only; clears the time when the date is cleared. */
    @Query("UPDATE tasks SET due_day = :dueDay, due_minute = CASE WHEN :dueDay IS NULL THEN NULL ELSE due_minute END WHERE id = :id")
    suspend fun setDue(id: Long, dueDay: Long?)

    @Query("UPDATE tasks SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int)

    @Query("UPDATE tasks SET due_day = :dueDay, due_minute = :dueMinute WHERE id = :id")
    suspend fun setDueAt(id: Long, dueDay: Long?, dueMinute: Int?)

    @Query("UPDATE tasks SET repeat_rule = :rule WHERE id = :id")
    suspend fun setRepeat(id: Long, rule: String?)

    @Query("UPDATE tasks SET list_id = :listId WHERE id = :id")
    suspend fun setList(id: Long, listId: Long)

    @Query("UPDATE tasks SET tags = :tags WHERE id = :id")
    suspend fun setTags(id: Long, tags: String)

    @Query("UPDATE tasks SET sort_order = :key WHERE id = :id")
    suspend fun setSortOrder(id: Long, key: Long)

    /** Drag to another day in Upcoming: the day changes, the time of day is kept. */
    @Query("UPDATE tasks SET due_day = :day WHERE id = :id")
    suspend fun setDueKeepTime(id: Long, day: Long)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    /** Open tasks with a due time: what the reminder scheduler needs after boot / clock changes. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND due_day IS NOT NULL AND due_minute IS NOT NULL")
    suspend fun timed(): List<Task>

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}
