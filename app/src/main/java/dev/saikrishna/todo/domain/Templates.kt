package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.NewTask
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.TemplateEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One task of a template (v0.7). Dates are RELATIVE: [dayOffset] days after the day the template
 * is used (+0d = that day), null = no date. [listId] null = the list in view when it is used.
 */
@Immutable
data class TemplateItem(
    val title: String,
    val listId: Long? = null,
    val dayOffset: Int? = null,
    val minute: Int? = null,
    val priority: Priority = Priority.None,
    val tags: List<String> = emptyList(),
    val subtasks: List<String> = emptyList(),
)

@Immutable
data class Template(val id: Long, val name: String, val items: List<TemplateItem>)

object TemplateCodec {
    fun encode(items: List<TemplateItem>): String = JSONArray().apply {
        for (i in items) put(
            JSONObject().apply {
                put("t", i.title)
                i.listId?.let { put("l", it) }
                i.dayOffset?.let { put("d", it) }
                i.minute?.let { put("m", it) }
                if (i.priority != Priority.None) put("p", i.priority.code)
                if (i.tags.isNotEmpty()) put("g", JSONArray(i.tags))
                if (i.subtasks.isNotEmpty()) put("s", JSONArray(i.subtasks))
            },
        )
    }.toString()

    fun decode(body: String): List<TemplateItem> = runCatching {
        val a = JSONArray(body)
        List(a.length()) { k ->
            val o = a.getJSONObject(k)
            fun strings(key: String): List<String> = o.optJSONArray(key)?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
            TemplateItem(
                title = o.getString("t"),
                listId = if (o.has("l")) o.getLong("l") else null,
                dayOffset = if (o.has("d")) o.getInt("d") else null,
                minute = if (o.has("m")) o.getInt("m") else null,
                priority = Priority.fromCode(o.optInt("p", 0)),
                tags = strings("g"),
                subtasks = strings("s"),
            )
        }
    }.getOrDefault(emptyList())

    fun toTemplate(e: TemplateEntity) = Template(e.id, e.name, decode(e.body))
}

object Templates {
    /**
     * Pure: the tasks a template creates when used on [today]. Relative offsets become real days;
     * a list that no longer exists (or none) falls back to [fallbackList].
     */
    fun instantiate(template: Template, today: LocalDate, fallbackList: Long, knownLists: Set<Long>): List<NewTask> =
        template.items.map { i ->
            val day = i.dayOffset?.let { today.plusDays(it.toLong()).toEpochDay() }
            NewTask(
                title = i.title,
                dueDay = day,
                dueMinute = i.minute?.takeIf { day != null },
                priority = i.priority,
                listId = i.listId?.takeIf { it in knownLists } ?: fallbackList,
                tags = i.tags,
                subtasks = i.subtasks,
            )
        }

    /** Pure: a one-task template from an existing task ("Save as template"), dates made relative to [today]. */
    fun fromTask(
        title: String, listId: Long, dueDay: Long?, dueMinute: Int?, priority: Priority,
        tags: List<String>, subtasks: List<String>, today: LocalDate,
    ): TemplateItem = TemplateItem(
        title = title,
        listId = listId,
        dayOffset = dueDay?.let { (it - today.toEpochDay()).toInt().coerceAtLeast(0) },
        minute = dueMinute?.takeIf { dueDay != null },
        priority = priority,
        tags = tags,
        subtasks = subtasks,
    )

    /**
     * Pure: the template editor's text → items. One task per line, written like the composer
     * ("Plan the week tomorrow #work !high +review"); lines starting with "-" are subtasks of the
     * task above. A parsed date becomes an offset from [now]'s day.
     */
    fun parseText(text: String, now: LocalDateTime, lists: List<TaskList>): List<TemplateItem> {
        val out = ArrayList<TemplateItem>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("-") || line.startsWith("•")) {
                val sub = line.trimStart('-', '•', ' ').trim()
                if (sub.isNotEmpty() && out.isNotEmpty()) out[out.lastIndex] = out.last().let { it.copy(subtasks = it.subtasks + sub) }
                continue
            }
            val p = QuickParse.parse(line, now, lists = lists)
            out += TemplateItem(
                title = p.title,
                listId = p.list?.id,
                dayOffset = p.dueDay?.let { (it.toEpochDay() - now.toLocalDate().toEpochDay()).toInt() },
                minute = p.dueMinute,
                priority = p.priority ?: Priority.None,
                tags = p.tags,
            )
        }
        return out
    }

    /** The two examples shipped with v0.7 (the user can delete them). */
    val Seeds: List<Pair<String, List<TemplateItem>>> = listOf(
        "Weekly review" to listOf(
            TemplateItem("Clear the inbox", TaskList.WORK, 0, subtasks = listOf("Email", "Slack", "Paper notes")),
            TemplateItem("Review last week", TaskList.WORK, 0, subtasks = listOf("What went well", "What slipped", "Loose ends")),
            TemplateItem("Plan the top 3 for next week", TaskList.WORK, 0, priority = Priority.High),
            TemplateItem("Look at the calendar for next week", TaskList.WORK, 1),
        ),
        "Trip packing" to listOf(
            TemplateItem(
                "Pack the bag", TaskList.PERSONAL, 0, tags = listOf("travel"),
                subtasks = listOf("Passport / ID", "Phone charger", "Toiletries", "Medicines", "Clothes"),
            ),
            TemplateItem("Check in online", TaskList.PERSONAL, 0, tags = listOf("travel")),
            TemplateItem("Book a cab to the airport", TaskList.PERSONAL, 0, tags = listOf("travel")),
            TemplateItem("Water the plants before leaving", TaskList.PERSONAL, 1),
        ),
    )
}
