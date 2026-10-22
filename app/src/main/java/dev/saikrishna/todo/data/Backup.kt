package dev.saikrishna.todo.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.10 backup file: one JSON document.
 *
 * ```
 * { "format": "dev.saikrishna.todo.backup", "schema": 1, "exportedAt": <epoch ms>, "app": "0.10.0",
 *   "tasks": [...], "subtasks": [...], "lists": [...], "templates": [...], "views": [...],
 *   "settings": { <DataStore key>: value, ... } }
 * ```
 * Tags live on the tasks (space-separated, as in the DB). Every field name is stable; new
 * fields may be added in later schemas, never renamed. Readers accept [MinSchema]..[Schema];
 * a file from a newer app is refused (it may hold data we would silently drop).
 */
data class Backup(
    val schema: Int,
    val exportedAt: Long,
    val appVersion: String,
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val lists: List<TaskList>,
    val templates: List<TemplateEntity>,
    val views: List<SavedView>,
    val settings: JSONObject?,
) {
    val summary: BackupSummary get() = BackupSummary(
        tasks = tasks.size, open = tasks.count { !it.done }, lists = lists.size,
        subtasks = subtasks.size, templates = templates.size, views = views.size, exportedAt = exportedAt,
    )
}

data class BackupSummary(
    val tasks: Int, val open: Int, val lists: Int, val subtasks: Int, val templates: Int, val views: Int, val exportedAt: Long,
) {
    /** "42 tasks, 3 lists" (+ the smaller collections when present). */
    val headline: String get() = "${plural(tasks, "task")}, ${plural(lists, "list")}"
    val detail: String get() = listOfNotNull(
        "$open open",
        subtasks.takeIf { it > 0 }?.let { plural(it, "subtask") },
        templates.takeIf { it > 0 }?.let { plural(it, "template") },
        views.takeIf { it > 0 }?.let { plural(it, "saved view") },
    ).joinToString(" · ")

    companion object {
        fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"
    }
}

class BackupException(message: String) : Exception(message)

enum class ImportMode { Merge, Replace }

object BackupCodec {
    const val Format = "dev.saikrishna.todo.backup"
    const val Schema = 1
    const val MinSchema = 1
    /** Refuse absurd files before parsing (a backup of 10 000 tasks is ~3 MB). */
    const val MaxBytes = 20 * 1024 * 1024

    fun fileName(today: LocalDate): String = "todo-backup-$today.json"

    fun encode(b: Backup): String = JSONObject().apply {
        put("format", Format)
        put("schema", Schema)
        put("exportedAt", b.exportedAt)
        put("app", b.appVersion)
        put("tasks", JSONArray(b.tasks.map(::task)))
        put("subtasks", JSONArray(b.subtasks.map { JSONObject().put("id", it.id).put("taskId", it.taskId).put("title", it.title).put("done", it.done).put("position", it.position) }))
        put("lists", JSONArray(b.lists.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).putOpt("emoji", it.emoji).put("position", it.position).put("area", it.areaCode) }))
        put("templates", JSONArray(b.templates.map { JSONObject().put("id", it.id).put("name", it.name).put("body", it.body).put("position", it.position) }))
        put("views", JSONArray(b.views.map { JSONObject().put("id", it.id).put("name", it.name).put("query", it.query).put("filter", it.filter).put("position", it.position) }))
        put("settings", b.settings ?: JSONObject())
    }.toString(1)

    private fun task(t: Task) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("notes", t.notes); put("done", t.done)
        put("createdAt", t.createdAt); putOpt("completedAt", t.completedAt); putOpt("dueDay", t.dueDay)
        put("priority", t.priorityCode); putOpt("dueMinute", t.dueMinute); putOpt("repeat", t.repeatRule)
        put("listId", t.listId); put("tags", t.tags); put("sortOrder", t.sortOrder)
    }

    /** Parses and validates; throws [BackupException] with a user-facing message. */
    fun decode(text: String): Backup {
        if (text.length > MaxBytes) throw BackupException("This file is too large to be a backup.")
        val o = runCatching { JSONObject(text) }.getOrElse { throw BackupException("This file isn't a backup (not valid JSON).") }
        if (o.optString("format") != Format) throw BackupException("This file isn't a ${dev.saikrishna.todo.BuildConfig.APP_NAME} backup.")
        val schema = o.optInt("schema", -1)
        if (schema > Schema) throw BackupException("This backup was made by a newer version of the app. Update the app, then import it.")
        if (schema < MinSchema) throw BackupException("This backup's version ($schema) isn't supported.")
        try {
            val lists = objects(o, "lists").map {
                TaskList(
                    id = it.getLong("id").also(::positive), name = it.getString("name").trim().take(60).ifEmpty { "List" },
                    color = it.optInt("color", 0), emoji = it.optStringOrNull("emoji")?.take(8),
                    position = it.optInt("position", 0), areaCode = it.optInt("area", ListArea.Personal.code),
                )
            }
            val tasks = objects(o, "tasks").map {
                val created = it.getLong("createdAt")
                Task(
                    id = it.getLong("id").also(::positive),
                    title = it.getString("title").trim().ifEmpty { throw BackupException("A task has no title.") }.take(500),
                    notes = it.optString("notes", ""), done = it.optBoolean("done", false), createdAt = created,
                    completedAt = it.optLongOrNull("completedAt"), dueDay = it.optLongOrNull("dueDay"),
                    priorityCode = it.optInt("priority", 0).coerceIn(0, 2),
                    dueMinute = it.optIntOrNull("dueMinute")?.takeIf { m -> m in 0 until 24 * 60 },
                    repeatRule = it.optStringOrNull("repeat"), listId = it.optLong("listId", TaskList.PERSONAL),
                    tags = Tags.join(Tags.split(it.optString("tags", ""))), sortOrder = it.optLong("sortOrder", created),
                )
            }
            val subtasks = objects(o, "subtasks").map {
                Subtask(it.getLong("id").also(::positive), it.getLong("taskId"), it.getString("title").take(300), it.optBoolean("done", false), it.optInt("position", 0))
            }
            val templates = objects(o, "templates").map {
                TemplateEntity(it.getLong("id").also(::positive), it.getString("name").take(60), it.getString("body"), it.optInt("position", 0))
            }
            val views = objects(o, "views").map {
                SavedView(it.getLong("id").also(::positive), it.getString("name").take(40), it.optString("query", ""), it.optString("filter", ""), it.optInt("position", 0))
            }
            for ((name, ids) in listOf("task" to tasks.map { it.id }, "list" to lists.map { it.id }, "subtask" to subtasks.map { it.id }))
                if (ids.size != ids.toSet().size) throw BackupException("This backup is damaged (duplicate $name ids).")
            return Backup(
                schema = schema, exportedAt = o.optLong("exportedAt", 0L), appVersion = o.optString("app", "?"),
                tasks = tasks, subtasks = subtasks, lists = lists, templates = templates, views = views,
                settings = o.optJSONObject("settings"),
            )
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("This backup is damaged and can't be read.")
        }
    }

    private fun positive(id: Long) { if (id <= 0) throw BackupException("This backup is damaged (bad id).") }
    /** A missing section reads as empty (sections may be added in later schemas). */
    private fun objects(o: JSONObject, key: String): List<JSONObject> {
        if (!o.has(key) || o.isNull(key)) return emptyList()
        val a = o.optJSONArray(key) ?: throw BackupException("This backup is damaged and can't be read.")
        return (0 until a.length()).map { a.getJSONObject(it) }
    }
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else optString(k)
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)

    /** v0.10 "Share open tasks": a Markdown checklist grouped by list. */
    fun markdown(tasks: List<Task>, lists: List<TaskList>, zone: ZoneId = ZoneId.systemDefault()): String {
        val open = tasks.filter { !it.done }
        val byList = open.groupBy { it.listId }
        val ordered = lists.filter { byList.containsKey(it.id) } + byList.keys.filter { id -> lists.none { it.id == id } }.map { TaskList(it, "Other") }
        return buildString {
            for (l in ordered) {
                if (isNotEmpty()) append('\n')
                append("## ").append(l.name).append('\n')
                for (t in byList.getValue(l.id).sortedWith(compareBy<Task> { it.dueDay ?: Long.MAX_VALUE }.thenBy { it.sortOrder })) {
                    append("- [ ] ").append(t.title.replace('\n', ' '))
                    t.dueDay?.let { append(" (").append(LocalDate.ofEpochDay(it)).append(')') }
                    if (t.tags.isNotBlank()) append(' ').append(Tags.split(t.tags).joinToString(" ") { "#$it" })
                    append('\n')
                }
            }
        }.ifEmpty { "Nothing open." }
    }
}

/**
 * Export / import against the database. Import runs in ONE Room transaction: any failure rolls
 * the whole thing back, so the DB is never half-imported. Settings are written after the DB
 * commit (DataStore is separate), and only on Replace. [afterImport] re-arms reminders and
 * refreshes the widget (TodoApp), given the tasks before and after.
 */
class BackupManager(
    private val db: TodoDatabase,
    private val settings: SettingsRepository?,
    private val appVersion: String = "?",
    private val clock: () -> Long = System::currentTimeMillis,
    private val afterImport: suspend (before: List<Task>, after: List<Task>) -> Unit = { _, _ -> },
) {
    private val dao get() = db.backupDao()
    /** Tests: throws inside the import transaction (after the first writes) to prove the rollback. */
    internal var faultForTest: (() -> Unit)? = null

    suspend fun snapshot(): Backup = db.withTransaction {
        Backup(BackupCodec.Schema, clock(), appVersion, dao.tasks(), dao.subtasks(), dao.lists(), dao.templates(), dao.views(), null)
    }.let { it.copy(settings = settings?.exportPrefs()) }

    suspend fun export(): String = BackupCodec.encode(snapshot())

    data class Result(val added: Int, val updated: Int, val skipped: Int)

    suspend fun import(b: Backup, mode: ImportMode): Result {
        val before = dao.tasks()
        val r = db.withTransaction { if (mode == ImportMode.Replace) replace(b) else merge(b) }
        if (mode == ImportMode.Replace) b.settings?.let { settings?.importPrefs(it) }
        afterImport(before, dao.tasks())
        return r
    }

    private suspend fun replace(b: Backup): Result {
        dao.clearSubtasks(); dao.clearTasks(); dao.clearLists(); dao.clearTemplates(); dao.clearViews()
        dao.putLists(b.lists)
        faultForTest?.invoke()
        // The built-in lists always exist (the app assumes them).
        db.orgDao().insertListsIfMissing(TaskList.Defaults)
        val listIds = dao.lists().map { it.id }.toSet()
        dao.putTasks(b.tasks.map { if (it.listId in listIds) it else it.copy(listId = TaskList.PERSONAL) })
        val taskIds = b.tasks.map { it.id }.toSet()
        dao.putSubtasks(b.subtasks.filter { it.taskId in taskIds })
        dao.putTemplates(b.templates)
        dao.putViews(b.views)
        return Result(added = b.tasks.size, updated = 0, skipped = 0)
    }

    /**
     * Merge, dedupe by id: a row with the same id that is the same thing (task: same createdAt;
     * list: same name, case-insensitive; template/view: same name) is updated from the backup;
     * a different thing that happens to share the id gets a new id (its subtasks follow). Rows
     * that are equal are skipped. Nothing local is deleted; settings are left as they are.
     */
    private suspend fun merge(b: Backup): Result {
        var added = 0; var updated = 0; var skipped = 0
        // Lists: map backup id → local id.
        val localLists = dao.lists().associateBy { it.id }.toMutableMap()
        val listMap = HashMap<Long, Long>()
        for (l in b.lists) {
            val same = localLists[l.id]
            val byName = localLists.values.firstOrNull { it.name.equals(l.name, ignoreCase = true) }
            when {
                same != null && same.name.equals(l.name, ignoreCase = true) -> {
                    listMap[l.id] = l.id
                    if (same != l) { dao.putLists(listOf(l)); localLists[l.id] = l }
                }
                byName != null -> listMap[l.id] = byName.id
                same == null -> { dao.putLists(listOf(l)); localLists[l.id] = l; listMap[l.id] = l.id }
                else -> { val id = dao.addList(l.copy(id = 0)); localLists[id] = l.copy(id = id); listMap[l.id] = id }
            }
        }
        fun listOf(id: Long) = listMap[id] ?: id.takeIf { localLists.containsKey(it) } ?: TaskList.PERSONAL
        faultForTest?.invoke()
        // Tasks.
        val localTasks = dao.tasks().associateBy { it.id }
        val taskMap = HashMap<Long, Long>()
        for (t0 in b.tasks) {
            val t = t0.copy(listId = listOf(t0.listId))
            val same = localTasks[t.id]
            when {
                same == null -> { dao.putTasks(kotlin.collections.listOf(t)); taskMap[t.id] = t.id; added++ }
                same.createdAt == t.createdAt -> {
                    taskMap[t.id] = t.id
                    if (same == t) skipped++ else { dao.putTasks(kotlin.collections.listOf(t)); updated++ }
                }
                else -> { taskMap[t.id] = dao.addTask(t.copy(id = 0)); added++ }
            }
        }
        // Subtasks follow their task; same id on the same task = same subtask.
        val localSubs = dao.subtasks().associateBy { it.id }
        for (s0 in b.subtasks) {
            val task = taskMap[s0.taskId] ?: continue
            val s = s0.copy(taskId = task)
            val same = localSubs[s.id]
            when {
                same == null -> dao.putSubtasks(kotlin.collections.listOf(s))
                same.taskId == s.taskId -> if (same != s) dao.putSubtasks(kotlin.collections.listOf(s))
                else -> dao.addSubtask(s.copy(id = 0))
            }
        }
        val localTemplates = dao.templates()
        for (t in b.templates) {
            val sameName = localTemplates.firstOrNull { it.name.equals(t.name, ignoreCase = true) }
            when {
                sameName != null -> if (sameName.body != t.body) dao.putTemplates(kotlin.collections.listOf(t.copy(id = sameName.id)))
                localTemplates.any { it.id == t.id } -> dao.addTemplate(t.copy(id = 0))
                else -> dao.putTemplates(kotlin.collections.listOf(t))
            }
        }
        val localViews = dao.views()
        for (v in b.views) {
            val sameName = localViews.firstOrNull { it.name.equals(v.name, ignoreCase = true) }
            when {
                sameName != null -> if (sameName.query != v.query || sameName.filter != v.filter) dao.putViews(kotlin.collections.listOf(v.copy(id = sameName.id)))
                localViews.any { it.id == v.id } -> dao.addView(v.copy(id = 0))
                else -> dao.putViews(kotlin.collections.listOf(v))
            }
        }
        return Result(added, updated, skipped)
    }

    companion object {
        fun exportedLabel(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            if (at <= 0) "unknown date" else Instant.ofEpochMilli(at).atZone(zone).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
