package dev.saikrishna.todo.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Which focus mode a list belongs to (v0.7). */
enum class ListArea(val code: Int) {
    Personal(0), Work(1);

    companion object {
        fun fromCode(code: Int): ListArea = entries.firstOrNull { it.code == code } ?: Personal
    }
}

/**
 * A list ("area") of tasks (v0.7). Built-ins Personal (id 1) and Work (id 2) always exist and
 * cannot be deleted. [color] indexes our list palette ([dev.saikrishna.todo.ui.theme.ListPalette]).
 */
@Immutable
@Entity(tableName = "lists")
data class TaskList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0") val color: Int = 0,
    val emoji: String? = null,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    @ColumnInfo(name = "area", defaultValue = "0") val areaCode: Int = ListArea.Personal.code,
) {
    val area: ListArea get() = ListArea.fromCode(areaCode)
    val builtIn: Boolean get() = id == PERSONAL || id == WORK

    /** "#work" handle used by the composer parser: lowercase, letters and digits only. */
    val handle: String get() = handleOf(name)

    companion object {
        const val PERSONAL = 1L
        const val WORK = 2L
        fun handleOf(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
        val Defaults = listOf(
            TaskList(PERSONAL, "Personal", color = 2, position = 0, areaCode = ListArea.Personal.code),
            TaskList(WORK, "Work", color = 0, position = 1, areaCode = ListArea.Work.code),
        )
    }
}

/** A checklist item inside a task (v0.7). Deleted with its task by the repository. */
@Immutable
@Entity(tableName = "subtasks", indices = [Index("task_id")])
data class Subtask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    val title: String,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

/** A saved set of tasks (v0.7). [body] is [TemplateCodec] JSON. */
@Immutable
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

/**
 * v0.8 saved search view: a name, the search text and a [dev.saikrishna.todo.domain.ViewFilter]
 * encoded with [dev.saikrishna.todo.domain.ViewFilter.encode].
 */
@Immutable
@Entity(tableName = "saved_views")
data class SavedView(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "") val query: String = "",
    @ColumnInfo(defaultValue = "") val filter: String = "",
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Dao
interface OrgDao {
    // ---- saved views (v0.8) ----
    @Query("SELECT * FROM saved_views ORDER BY position ASC, id ASC")
    fun observeViews(): Flow<List<SavedView>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM saved_views")
    suspend fun maxViewPosition(): Int

    @Insert
    suspend fun insertView(v: SavedView): Long

    @Query("DELETE FROM saved_views WHERE id = :id")
    suspend fun deleteView(id: Long)

    // ---- lists ----
    @Query("SELECT * FROM lists ORDER BY position ASC, id ASC")
    fun observeLists(): Flow<List<TaskList>>

    @Query("SELECT * FROM lists ORDER BY position ASC, id ASC")
    suspend fun lists(): List<TaskList>

    @Insert
    suspend fun insertList(list: TaskList): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertListsIfMissing(lists: List<TaskList>)

    @Query("UPDATE lists SET name = :name, color = :color, emoji = :emoji, area = :area WHERE id = :id")
    suspend fun updateList(id: Long, name: String, color: Int, emoji: String?, area: Int)

    @Query("UPDATE lists SET position = :position WHERE id = :id")
    suspend fun setListPosition(id: Long, position: Int)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("UPDATE tasks SET list_id = :to WHERE list_id = :from")
    suspend fun moveTasks(from: Long, to: Long): Int

    // ---- subtasks ----
    @Query("SELECT * FROM subtasks ORDER BY task_id ASC, position ASC, id ASC")
    fun observeSubtasks(): Flow<List<Subtask>>

    @Query("SELECT * FROM subtasks WHERE task_id = :taskId ORDER BY position ASC, id ASC")
    suspend fun subtasksOf(taskId: Long): List<Subtask>

    @Query("SELECT * FROM subtasks WHERE task_id IN (:taskIds) ORDER BY task_id ASC, position ASC, id ASC")
    suspend fun subtasksOf(taskIds: List<Long>): List<Subtask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubtasks(items: List<Subtask>)

    @Insert
    suspend fun insertSubtask(item: Subtask): Long

    @Query("UPDATE subtasks SET done = :done WHERE id = :id")
    suspend fun setSubtaskDone(id: Long, done: Boolean)

    @Query("UPDATE subtasks SET done = 1 WHERE task_id = :taskId")
    suspend fun completeSubtasks(taskId: Long)

    @Query("UPDATE subtasks SET title = :title WHERE id = :id")
    suspend fun setSubtaskTitle(id: Long, title: String)

    @Query("UPDATE subtasks SET position = :position WHERE id = :id")
    suspend fun setSubtaskPosition(id: Long, position: Int)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long)

    @Query("DELETE FROM subtasks WHERE task_id IN (:taskIds)")
    suspend fun deleteSubtasksOf(taskIds: List<Long>)

    // ---- templates ----
    @Query("SELECT * FROM templates ORDER BY position ASC, id ASC")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Insert
    suspend fun insertTemplate(t: TemplateEntity): Long

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)
}
