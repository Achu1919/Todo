package dev.saikrishna.todo.domain

/**
 * v0.8 share-sheet capture: turns ACTION_SEND text (+ optional EXTRA_SUBJECT) into a task draft.
 *
 * - A subject (a web page's title, an email subject) becomes the title; the text goes to notes.
 * - Otherwise the first non-blank line is the title and the rest is notes.
 * - URLs never stay in the title: they are removed from it and kept in notes (once each). A share
 *   that is only a link gets "Read <host>" as its title.
 * - A title longer than [MaxTitle] is cut at a word boundary with "…"; the full line goes to notes.
 */
object ShareCapture {
    const val MaxTitle = 120
    private val urlRegex = Regex("""(?i)\bhttps?://[^\s<>"]+""")

    data class Draft(val title: String, val notes: String, val urls: List<String>)

    private fun trimUrl(u: String) = u.trimEnd('.', ',', ')', ';', '!', '?')
    fun urls(text: String): List<String> = urlRegex.findAll(text).map { trimUrl(it.value) }.distinct().toList()

    fun parse(text: String?, subject: String?): Draft {
        val body = text.orEmpty().replace("\r\n", "\n").trim()
        val subj = subject.orEmpty().trim().replace(Regex("\\s+"), " ")
        val urls = urls(body + "\n" + subj)
        // The link goes, punctuation that followed it stays ("see https://x.io/a." -> "see.").
        val withoutUrls: (String) -> String = { s ->
            urlRegex.replace(s) { m -> m.value.substring(trimUrl(m.value).length) }
                .replace(Regex("[ \\t]+"), " ").replace(Regex(" +([.,;:!?])"), "$1")
                .trim().trim('-', '–', '—', '|', ':').trim()
        }

        val clean: (String) -> String = { s -> s.lines().map(withoutUrls).joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim() }
        var title: String
        val notes = ArrayList<String>()
        if (withoutUrls(subj).isNotEmpty()) {
            title = withoutUrls(subj)
            val rest = clean(body)
            if (rest.isNotEmpty() && rest != title) notes += rest
        } else {
            val lines = body.lines()
            val first = lines.indexOfFirst { withoutUrls(it).isNotEmpty() }
            if (first < 0) {
                title = urls.firstOrNull()?.let { "Read " + host(it) } ?: ""
            } else {
                title = withoutUrls(lines[first])
                val rest = clean(lines.drop(first + 1).joinToString("\n"))
                if (rest.isNotEmpty()) notes += rest
            }
        }
        if (title.length > MaxTitle) {
            val full = title
            val cut = title.take(MaxTitle).let { t -> t.substring(0, t.lastIndexOf(' ').takeIf { it > MaxTitle / 2 } ?: t.length) }
            title = cut.trimEnd(',', ';', ':', ' ') + "…"
            notes.add(0, full)
        }
        val n = (notes.filter { it.isNotBlank() } + urls).joinToString("\n\n")
        return Draft(title, n, urls)
    }

    fun host(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.").lowercase()
}
