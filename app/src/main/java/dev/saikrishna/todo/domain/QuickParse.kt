package dev.saikrishna.todo.domain

import androidx.compose.runtime.Immutable
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.data.Tags
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Natural-language capture for the composer. Our own small, pure parser (no network, no ML):
 * finds dates, times, priority and repeat phrases in what the user types, reports each as a
 * [Token] (span in the raw text + a chip label), and returns the title with those spans removed.
 *
 * Locale: India. Numeric dates are DAY first ("24/10" = 24 October); times are 12 h with am/pm or
 * 24 h ("16:30"); "at 9" without am/pm means 9:00, and 1–7 mean pm (nobody schedules "at 3" for 3 am).
 *
 * A token whose [Token.key] is in `ignored` is left as plain text: not applied, not stripped, not
 * highlighted. That is how tapping a chip removes an interpretation but keeps the words.
 */
object QuickParse {

    enum class Kind { Date, Time, Priority, Repeat, List, Tag }

    @Immutable
    data class Token(val start: Int, val end: Int, val kind: Kind, val key: String, val label: String)

    @Immutable
    data class Result(
        val title: String,
        /** Applied tokens, in text order. */
        val tokens: List<Token>,
        val dueDay: LocalDate?,
        /** Minutes after midnight, or null for "no time". */
        val dueMinute: Int?,
        val priority: Priority?,
        val repeat: RepeatRule?,
        /** v0.7: "#work" / "#listname" naming a known list. */
        val list: TaskList? = null,
        /** v0.7: "+tag" and unknown "#tag", cleaned ([Tags.clean]), in text order. */
        val tags: List<String> = emptyList(),
    ) {
        val isPlain: Boolean get() = tokens.isEmpty()
    }

    // ---- vocabulary ----------------------------------------------------------------------------

    private const val B = "(?<![\\p{L}\\d])" // word start
    private const val E = "(?![\\p{L}\\d])" // word end

    private const val DOW = "(?:mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)"
    private const val DOW_LIST = "$DOW(?:s)?(?:\\s*(?:,|and|&)\\s*$DOW(?:s)?)*"
    private const val MONTH = "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private const val NUM_WORD = "(?:\\d{1,3}|a|an|one|two|three|four|five|six|seven|eight|nine|ten|other)"
    private const val ORD = "(?:st|nd|rd|th)?"

    private fun dowOf(word: String): DayOfWeek? {
        val w = word.lowercase()
        return when {
            w.startsWith("mon") -> DayOfWeek.MONDAY
            w.startsWith("tue") -> DayOfWeek.TUESDAY
            w.startsWith("wed") -> DayOfWeek.WEDNESDAY
            w.startsWith("thu") -> DayOfWeek.THURSDAY
            w.startsWith("fri") -> DayOfWeek.FRIDAY
            w.startsWith("sat") -> DayOfWeek.SATURDAY
            w.startsWith("sun") -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun daysIn(list: String): Set<DayOfWeek> =
        Regex(DOW, RegexOption.IGNORE_CASE).findAll(list).mapNotNull { dowOf(it.value) }.toSet()

    private fun monthOf(word: String): Int? {
        val w = word.lowercase().take(3)
        val i = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(w)
        return if (i < 0) null else i + 1
    }

    private fun number(word: String): Int? = when (word.lowercase()) {
        "a", "an", "one" -> 1
        "two", "other" -> 2
        "three" -> 3
        "four" -> 4
        "five" -> 5
        "six" -> 6
        "seven" -> 7
        "eight" -> 8
        "nine" -> 9
        "ten" -> 10
        else -> word.toIntOrNull()
    }

    // ---- rules ---------------------------------------------------------------------------------

    /** What a match contributes. Each rule returns null to reject a match (e.g. 31/02). */
    private sealed interface Hit {
        val label: String
        data class Date(val date: LocalDate, override val label: String, val defaultMinute: Int? = null) : Hit
        data class Time(val minute: Int, override val label: String) : Hit
        data class Prio(val priority: Priority, override val label: String) : Hit
        data class Repeat(val rule: RepeatRule, override val label: String, val needsAnchor: Boolean = false) : Hit
    }

    /** Placeholder hit for tokens that carry no schedule (lists, tags). */
    private object NoHit : Hit { override val label = "" }

    private class Rule(val kind: Kind, pattern: String, val build: (MatchResult, LocalDateTime) -> Hit?) {
        val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE))
    }

    private val rules: List<Rule> = listOf(
        // ---- repeat (first: "every monday" must not be read as a date) ----
        Rule(Kind.Repeat, "$B(?:every\\s+($NUM_WORD)\\s+days?\\s+after\\s+(?:completion|completing|done|it'?s\\s+done)|($NUM_WORD)\\s+days?\\s+after\\s+(?:completion|completing|done)|after\\s+completion\\s+every\\s+($NUM_WORD)\\s+days?)$E") { m, _ ->
            val n = number(m.groupValues.drop(1).first { it.isNotEmpty() }) ?: return@Rule null
            if (n !in 1..365) null else RepeatRule.AfterCompletion(n).let { Hit.Repeat(it, it.label()) }
        },
        Rule(Kind.Repeat, "$B(?:every\\s+(?:week\\s*day|working\\s+day)|(?:on\\s+)?weekdays)$E") { _, _ ->
            Hit.Repeat(RepeatRule.Weekdays, RepeatRule.Weekdays.label())
        },
        Rule(Kind.Repeat, "$B(?:every\\s+day|daily|everyday)$E") { _, _ -> Hit.Repeat(RepeatRule.Daily(1), RepeatRule.Daily(1).label()) },
        Rule(Kind.Repeat, "${B}every\\s+($NUM_WORD)\\s+days?$E") { m, _ ->
            val n = number(m.groupValues[1]) ?: return@Rule null
            if (n !in 1..365) null else RepeatRule.Daily(n).let { Hit.Repeat(it, it.label()) }
        },
        // every 2 weeks / every other week [on mon, thu]
        Rule(Kind.Repeat, "${B}every\\s+($NUM_WORD)\\s+weeks?(?:\\s+on\\s+($DOW_LIST))?$E") { m, now ->
            val n = number(m.groupValues[1]) ?: return@Rule null
            if (n !in 1..52) return@Rule null
            val days = daysIn(m.groupValues[2])
            val rule = RepeatRule.Weekly(days.ifEmpty { setOf(now.dayOfWeek) }, n)
            Hit.Repeat(rule, rule.label(), needsAnchor = days.isEmpty())
        },
        // every other monday / every 2nd friday
        Rule(Kind.Repeat, "${B}every\\s+(other|\\d{1,2}(?:nd|rd|th|st)?)\\s+($DOW_LIST)$E") { m, _ ->
            val n = number(m.groupValues[1].trimEnd('s', 't', 'n', 'd', 'r', 'h')) ?: number(m.groupValues[1]) ?: return@Rule null
            val rule = RepeatRule.Weekly(daysIn(m.groupValues[2]), n.coerceIn(1, 52))
            Hit.Repeat(rule, rule.label())
        },
        Rule(Kind.Repeat, "$B(?:every\\s+|each\\s+|on\\s+)($DOW_LIST)$E") { m, _ ->
            val days = daysIn(m.groupValues[1])
            // "on mondays" repeats; "on monday" is a date (handled below).
            if (m.value.lowercase().startsWith("on") && !m.groupValues[1].lowercase().trimEnd().endsWith("s")) return@Rule null
            RepeatRule.Weekly(days, 1).let { Hit.Repeat(it, it.label()) }
        },
        Rule(Kind.Repeat, "$B(?:every\\s+week|weekly)(?:\\s+on\\s+($DOW_LIST))?$E") { m, now ->
            val days = daysIn(m.groupValues[1])
            val rule = RepeatRule.Weekly(days.ifEmpty { setOf(now.dayOfWeek) }, 1)
            Hit.Repeat(rule, rule.label(), needsAnchor = days.isEmpty())
        },
        Rule(Kind.Repeat, "$B(?:every\\s+month|monthly)(?:\\s+on\\s+(?:the\\s+)?(\\d{1,2})$ORD)?$E") { m, now ->
            val d = m.groupValues[1].toIntOrNull()
            if (d != null && d !in 1..31) return@Rule null
            val rule = RepeatRule.Monthly(d ?: now.dayOfMonth)
            Hit.Repeat(rule, rule.label(), needsAnchor = d == null)
        },
        Rule(Kind.Repeat, "${B}every\\s+(\\d{1,2})(?:st|nd|rd|th)(?:\\s+of\\s+(?:the|every)\\s+month)?$E") { m, _ ->
            val d = m.groupValues[1].toInt()
            if (d !in 1..31) null else RepeatRule.Monthly(d).let { Hit.Repeat(it, it.label()) }
        },

        // ---- dates ----
        Rule(Kind.Date, "$B(?:the\\s+)?day\\s+after\\s+tomorrow$E") { _, now -> date(now.toLocalDate().plusDays(2), now) },
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?today$E") { _, now -> date(now.toLocalDate(), now) },
        Rule(Kind.Date, "${B}tonight$E") { _, now -> Hit.Date(now.toLocalDate(), "Tonight", defaultMinute = 20 * 60) },
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?(?:tomorrow|tmrw|tmr|tomo)$E") { _, now -> date(now.toLocalDate().plusDays(1), now) },
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?next\\s+week$E") { _, now ->
            date(now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)), now)
        },
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?next\\s+month$E") { _, now ->
            date(now.toLocalDate().plusMonths(1).withDayOfMonth(1), now)
        },
        Rule(Kind.Date, "${B}in\\s+($NUM_WORD)\\s+(days?|weeks?|months?)$E") { m, now ->
            val n = number(m.groupValues[1]) ?: return@Rule null
            if (n !in 1..999) return@Rule null
            val t = now.toLocalDate()
            val d = when (m.groupValues[2].lowercase().first()) {
                'd' -> t.plusDays(n.toLong())
                'w' -> t.plusWeeks(n.toLong())
                else -> t.plusMonths(n.toLong())
            }
            date(d, now)
        },
        // 24 Oct, 24th October 2026, on 3 jan
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?(\\d{1,2})$ORD\\s+(?:of\\s+)?($MONTH)\\.?(?:,?\\s+(\\d{4}))?$E") { m, now ->
            ymd(m.groupValues[3], monthOf(m.groupValues[2]), m.groupValues[1].toInt(), now)
        },
        // Oct 24, October 24th 2026
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?($MONTH)\\.?\\s+(\\d{1,2})$ORD(?:,?\\s+(\\d{4}))?$E") { m, now ->
            ymd(m.groupValues[3], monthOf(m.groupValues[1]), m.groupValues[2].toInt(), now)
        },
        // 24/10, 24/10/26, 24-10-2026 (day first: India). A dash needs the year ("3-4 reps" is not a date).
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?(\\d{1,2})(?:/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?|-(\\d{1,2})-(\\d{4}|\\d{2}))$E(?![/-])") { m, now ->
            val g = m.groupValues
            val month = (g[2].ifEmpty { g[4] }).toInt()
            val y = g[3].ifEmpty { g[5] }.let { if (it.length == 2) "20$it" else it }
            ymd(y, month, g[1].toInt(), now)
        },
        // (on|this|next) friday
        Rule(Kind.Date, "$B(?:(?:by|on|due)\\s+)?(?:(this|next|coming)\\s+)?($DOW)$E") { m, now ->
            val dow = dowOf(m.groupValues[2]) ?: return@Rule null
            val t = now.toLocalDate()
            val d = when (m.groupValues[1].lowercase()) {
                // "next friday" = Friday of next week (Monday-based weeks)
                "next" -> t.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(dow))
                // plain / this / coming: the next one, today included
                else -> t.with(TemporalAdjusters.nextOrSame(dow))
            }
            date(d, now)
        },

        // ---- times ----
        Rule(Kind.Time, "$B(?:(?:at|@|by)\\s*)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)(?![\\p{L}\\d])") { m, _ ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h !in 1..12 || min !in 0..59) return@Rule null
            val pm = m.groupValues[3].lowercase().startsWith("p")
            if (h == 12) h = 0
            time((h + if (pm) 12 else 0) * 60 + min)
        },
        Rule(Kind.Time, "$B(?:(?:at|@|by)\\s*)?([01]?\\d|2[0-3]):([0-5]\\d)$E") { m, _ ->
            time(m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt())
        },
        Rule(Kind.Time, "$B(?:at\\s+)?(?:noon|midday)$E") { _, _ -> time(12 * 60) },
        // "at 9" (needs "at": a bare number is too ambiguous). 1–7 → pm.
        Rule(Kind.Time, "${B}(?:at|@)\\s*(\\d{1,2})$E(?![:/-])") { m, _ ->
            val h = m.groupValues[1].toInt()
            when (h) {
                in 1..7 -> time((h + 12) * 60)
                in 8..23 -> time(h * 60)
                else -> null
            }
        },

        // ---- priority ----
        Rule(Kind.Priority, "(?<![\\p{L}\\d!])!(?:high|hi|h)$E") { _, _ -> Hit.Prio(Priority.High, "High") },
        Rule(Kind.Priority, "(?<![\\p{L}\\d!])!(?:low|lo|l)$E") { _, _ -> Hit.Prio(Priority.Low, "Low") },
        Rule(Kind.Priority, "(?<!!)!!(?![!\\p{L}\\d])") { _, _ -> Hit.Prio(Priority.High, "High") },
    )

    private fun date(d: LocalDate, now: LocalDateTime) = Hit.Date(d, dateLabel(d, now.toLocalDate()))

    private fun time(minute: Int) = Hit.Time(minute, timeLabel(minute))

    /** Explicit day/month (+ optional year). No year: this year, or next year if already past. */
    private fun ymd(year: String, month: Int?, day: Int, now: LocalDateTime): Hit? {
        if (month == null || month !in 1..12) return null
        val today = now.toLocalDate()
        val y = year.toIntOrNull()
        val d = runCatching { LocalDate.of(y ?: today.year, month, day) }.getOrNull() ?: return null
        val resolved = if (y == null && d.isBefore(today)) runCatching { d.plusYears(1) }.getOrNull() ?: return null else d
        return date(resolved, now)
    }

    // ---- parse ---------------------------------------------------------------------------------

    /** "#word": a list if it names one, else a tag. Starts with a letter ("#1" stays text). */
    private val hashRegex = Regex("(?<![\\p{L}\\d#&/])#(\\p{L}[\\p{L}\\d_-]*)")
    /** "+word": always a tag. Starts with a letter, so "+1d" or "+91 98…" stay text. */
    private val plusRegex = Regex("(?<![\\p{L}\\d+])\\+(\\p{L}[\\p{L}\\d_-]*)")

    fun parse(
        text: String,
        now: LocalDateTime,
        ignored: Set<String> = emptySet(),
        lists: List<TaskList> = emptyList(),
    ): Result {
        val claimed = ArrayList<IntRange>()
        val found = ArrayList<Pair<Token, Hit>>()
        // v0.7 organisation tokens first (so "#today" is a tag, not a date): one list, several tags.
        var list: TaskList? = null
        val tags = ArrayList<Pair<Int, String>>()
        val byHandle = lists.associateBy { it.handle }
        for ((regex, isHash) in listOf(hashRegex to true, plusRegex to false)) {
            for (m in regex.findAll(text)) {
                val r = m.range
                if (claimed.any { it.first <= r.last && r.first <= it.last }) continue
                val word = m.groupValues[1]
                val target = if (isHash) byHandle[TaskList.handleOf(word)] else null
                if (target != null && list == null) {
                    val key = "List:" + target.handle
                    claimed += r
                    if (key in ignored) continue
                    list = target
                    found += Token(r.first, r.last + 1, Kind.List, key, target.name) to NoHit
                } else {
                    val tag = Tags.clean(word)
                    if (tag.isEmpty()) continue
                    val key = "Tag:" + m.value.lowercase()
                    claimed += r
                    if (key in ignored) continue
                    if (tags.none { it.second == tag }) tags += r.first to tag
                    found += Token(r.first, r.last + 1, Kind.Tag, key, "#$tag") to NoHit
                }
            }
        }
        for (rule in rules) {
            for (m in rule.regex.findAll(text)) {
                val r = m.range
                if (claimed.any { it.first <= r.last && r.first <= it.last }) continue
                val hit = rule.build(m, now) ?: continue
                // One interpretation per kind: the first (by rule order, then position) wins.
                if (found.any { it.first.kind == rule.kind }) continue
                val key = rule.kind.name + ":" + m.value.lowercase().replace(Regex("\\s+"), " ").trim()
                claimed += r
                if (key in ignored) continue
                found += Token(r.first, r.last + 1, rule.kind, key, hit.label) to hit
            }
        }
        found.sortBy { it.first.start }

        val dateHit = found.firstNotNullOfOrNull { it.second as? Hit.Date }
        val timeHit = found.firstNotNullOfOrNull { it.second as? Hit.Time }
        val prio = found.firstNotNullOfOrNull { it.second as? Hit.Prio }?.priority
        val repeat = found.firstNotNullOfOrNull { it.second as? Hit.Repeat }
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute

        val minute = timeHit?.minute ?: dateHit?.defaultMinute
        var day: LocalDate? = dateHit?.date
        var rule = repeat?.rule
        if (rule != null && day != null && repeat!!.needsAnchor) {
            // "every week" / "every 2 weeks" / "monthly" + a date: anchor the rule on that date.
            rule = when (rule) {
                is RepeatRule.Weekly -> rule.copy(days = setOf(day.dayOfWeek))
                is RepeatRule.Monthly -> RepeatRule.Monthly(day.dayOfMonth)
                else -> rule
            }
        }
        if (day == null && rule != null) {
            day = firstOccurrence(rule, today)
            if (day == today && minute != null && minute <= nowMinute && rule !is RepeatRule.AfterCompletion) {
                day = nextOccurrence(rule, today, today)
            }
        }
        if (day == null && minute != null) day = if (minute > nowMinute) today else today.plusDays(1)

        // Chip labels can depend on the combination (a re-anchored repeat).
        val tokens = found.map { (t, h) ->
            if (h is Hit.Repeat && rule != null) t.copy(label = rule.label()) else t
        }
        return Result(stripTitle(text, tokens), tokens, day, minute, prio, rule, list, tags.sortedBy { it.first }.map { it.second })
    }

    /** Removes token spans, then tidies spaces and dangling separators. Never returns blank for non-blank input. */
    fun stripTitle(text: String, tokens: List<Token>): String {
        if (tokens.isEmpty()) return text.trim()
        val sb = StringBuilder()
        var i = 0
        for (t in tokens.sortedBy { it.start }) {
            if (t.start > i) sb.append(text, i, t.start)
            sb.append(' ')
            i = maxOf(i, t.end)
        }
        if (i < text.length) sb.append(text, i, text.length)
        val cleaned = sb.toString()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .trim()
            .trim(',', ';', '-', '·', ' ')
            .replace(Regex("(?i)\\s+(?:and|&|on|at|by|due)$"), "")
            .trim()
        return cleaned.ifEmpty { text.trim() }
    }

    // ---- labels --------------------------------------------------------------------------------

    fun timeLabel(minute: Int): String {
        val h = minute / 60
        val m = minute % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ap = if (h < 12) "am" else "pm"
        return if (m == 0) "$h12 $ap" else "$h12:${m.toString().padStart(2, '0')} $ap"
    }

    fun dateLabel(d: LocalDate, today: LocalDate): String {
        val diff = d.toEpochDay() - today.toEpochDay()
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff in 2..6 -> d.dayOfWeek.short()
            else -> "${d.dayOfMonth} " + d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3) +
                (if (d.year != today.year) " ${d.year}" else "")
        }
    }
}
