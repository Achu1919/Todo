package dev.saikrishna.todo.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.saikrishna.todo.domain.RepeatRule
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stored codes are stable (0/1/2 since v0.5); v0.13 Medium is code 3 so older data keeps its
 * meaning. Enum order is the display / sort order (None < Low < Medium < High); see [rank].
 */
enum class Priority(val code: Int) {
    None(0), Low(1), Medium(3), High(2);

    val rank: Int get() = ordinal

    companion object {
        fun fromCode(code: Int): Priority = entries.firstOrNull { it.code == code } ?: None
    }
}

@Immutable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val done: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Last time the task was completed; kept when re-opened (orders the Done list). */
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    /** Due date as a LocalDate epoch day (no time of day), or null for "no date". */
    @ColumnInfo(name = "due_day") val dueDay: Long? = null,
    @ColumnInfo(name = "priority", defaultValue = "0") val priorityCode: Int = 0,
    /** v0.6: due time as minutes after local midnight (needs [dueDay]); null = all day. */
    @ColumnInfo(name = "due_minute") val dueMinute: Int? = null,
    /** v0.6: repeat rule ([RepeatRule.encode]), null = does not repeat. */
    @ColumnInfo(name = "repeat_rule") val repeatRule: String? = null,
    /** v0.7: owning list ([TaskList.id]); existing tasks migrate to Personal. */
    @ColumnInfo(name = "list_id", defaultValue = "1") val listId: Long = TaskList.PERSONAL,
    /** v0.7: tags, lowercase, space-separated ("" = none). See [Tags]. */
    @ColumnInfo(name = "tags", defaultValue = "") val tags: String = "",
    /**
     * v0.7: manual order key (ascending). New tasks get their creation time, so by default the
     * order is creation order; dragging permutes the keys of the rows involved ([reorderKeys]).
     */
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Long = createdAt,
    /**
     * v0.13: a repeating task on pause keeps its rule but completing it spawns nothing and the
     * Calendar shows no future occurrences, until it is resumed.
     */
    @ColumnInfo(name = "repeat_paused", defaultValue = "0") val repeatPaused: Boolean = false,
    /** v0.13: the reminder fires this many minutes before the due time (0 = at the due time). */
    @ColumnInfo(name = "remind_before", defaultValue = "0") val remindBefore: Int = 0,
)

/** v0.13: the rule that still produces occurrences (null when there is none or it is paused). */
val Task.activeRepeat: RepeatRule? get() = if (repeatPaused) null else repeat

val Task.tagList: List<String> get() = Tags.split(tags)

/** Tag normalisation: lowercase, letters/digits/_/- only, stored space-separated, order kept. */
object Tags {
    private val bad = Regex("[^\\p{L}\\d_-]")
    fun clean(raw: String): String = raw.trim().trimStart('#', '+').lowercase().replace(bad, "").take(32)
    fun split(stored: String): List<String> = if (stored.isBlank()) emptyList() else stored.split(' ').filter { it.isNotEmpty() }
    fun join(tags: Collection<String>): String = tags.map(::clean).filter { it.isNotEmpty() }.distinct().joinToString(" ")
}

val Task.priority: Priority get() = Priority.fromCode(priorityCode)

val Task.repeat: RepeatRule? get() = RepeatRule.decode(repeatRule)

/** When the reminder should fire (epoch millis in [zone]), or null if the task has no due time. */
/** v0.13: when the reminder fires: the due time minus [Task.remindBefore] (null when untimed). */
fun Task.remindAtMillis(zone: ZoneId): Long? = dueAtMillis(zone)?.minus(remindBefore * 60_000L)

fun Task.dueAtMillis(zone: ZoneId): Long? {
    val day = dueDay ?: return null
    val minute = dueMinute ?: return null
    return LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
}
