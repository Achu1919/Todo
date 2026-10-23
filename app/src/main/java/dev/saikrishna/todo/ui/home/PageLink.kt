package dev.saikrishna.todo.ui.home

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.saikrishna.todo.MainActivity

/**
 * v0.13 deep links into a page: `todo://page/<name>` with [EXTRA] = name. Names are the [Filter]
 * names lower-cased ("overview" also answers to "home"), so widgets and notifications can open
 * Home, any Todo view or Calendar.
 */
object PageLink {
    const val EXTRA = "open_page"

    fun name(f: Filter): String = if (f == Filter.Overview) "home" else f.name.lowercase()

    fun parse(name: String?): Filter? = when (name?.lowercase()) {
        null -> null
        "home", "overview" -> Filter.Overview
        else -> Filter.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun intent(context: Context, page: Filter): Intent =
        Intent(context, MainActivity::class.java)
            .setData("todo://page/${name(page)}".toUri())
            .putExtra(EXTRA, name(page))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The page an intent asks for: the extra, else a `todo://page/<name>` URI. */
    fun from(intent: Intent?): Filter? {
        intent ?: return null
        parse(intent.getStringExtra(EXTRA))?.let { return it }
        val d = intent.data ?: return null
        return if (d.scheme == "todo" && d.host == "page") parse(d.lastPathSegment) else null
    }
}
