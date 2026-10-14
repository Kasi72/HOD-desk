package com.hoddesk.app.reasoning

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

/** Parses only stated values; an absent year or invalid clock is never guessed. */
object TemporalEvidence {
    data class DateMention(val text: String, val value: LocalDate?, val range: IntRange) {
        val key: String get() = value?.toString() ?: text.lowercase(Locale.ROOT).trim()
    }
    data class TimeMention(val text: String, val start: LocalTime, val end: LocalTime?, val range: IntRange) {
        val key: String get() = "$start/${end ?: ""}"
    }

    private const val MONTHS = "Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?"
    private val numericDate = Regex("(?<![\\p{L}\\p{N}])(?:\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4})(?![\\p{L}\\p{N}])")
    private val dayFirstDate = Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+(?:$MONTHS)(?:\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE)
    private val monthFirstDate = Regex("\\b(?:$MONTHS)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE)
    private val relativeDate = Regex("\\b(?:today|tomorrow|this week|next week|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b", RegexOption.IGNORE_CASE)
    private val rangeTime = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?\\s*(?:-|–|—|to)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b", RegexOption.IGNORE_CASE)
    private val singleTime = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b", RegexOption.IGNORE_CASE)
    private val clock24 = Regex("(?i)(?:\\bat\\s+|\\btime\\s*:\\s*|@\\s*|^)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\s*(?:a\\.?m\\.?|p\\.?m\\.?))\\b")

    fun dates(text: String): List<DateMention> {
        val matches = (numericDate.findAll(text).toList() + dayFirstDate.findAll(text).toList() + monthFirstDate.findAll(text).toList())
            .sortedBy { it.range.first }
        val explicit = matches.map { DateMention(it.value, parseDate(it.value), it.range) }
        val anchored = explicit.mapNotNull { it.value }.distinct()
        val resolved = explicit.map { mention ->
            if (mention.value != null) mention else {
                // A calendar invitation can omit the year while the notice body
                // states it. Resolve only when that month/day has one explicit year.
                val possible = anchored.filter { candidate ->
                    parseDate("${mention.text} ${candidate.year}") == candidate
                }
                mention.copy(value = possible.singleOrNull())
            }
        }
        return if (resolved.isNotEmpty()) resolved else relativeDate.findAll(text)
            .map { DateMention(it.value, null, it.range) }.toList()
    }

    fun uniqueDates(text: String): List<DateMention> = dates(text).groupBy { it.key }.values.map { mentions ->
        mentions.firstOrNull { parseDate(it.text) != null } ?: mentions.first()
    }

    fun times(text: String): List<TimeMention> {
        val rangeMatches = rangeTime.findAll(text).toList()
        val ranges = rangeMatches.mapNotNull { match ->
            val start = clock(match.groupValues[1], match.groupValues[2], match.groupValues[3].ifBlank { match.groupValues[6] })
            val end = clock(match.groupValues[4], match.groupValues[5], match.groupValues[6])
            // “11:30–1:30 PM” could mean a noon-crossing range or an
            // overnight range. Only an explicit start meridiem resolves it.
            val ambiguousStart = start != null && end != null && match.groupValues[3].isBlank() && start.isAfter(end)
            if (start == null || end == null || ambiguousStart) null else TimeMention(match.value.trim(), start, end, match.range)
        }.toList()
        // An invalid/ambiguous range must not degrade to a valid-looking end
        // clock (e.g. 1:30 PM), which would silently shift a meeting start.
        val singles = singleTime.findAll(text).filter { match -> rangeMatches.none { match.range.first in it.range } }
            .mapNotNull { match -> clock(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                ?.let { TimeMention(match.value.trim(), it, null, match.range) } }.toList()
        val twentyFourHour = clock24.findAll(text).mapNotNull { match ->
            val group = match.groups[1] ?: return@mapNotNull null
            val first = group.range.first
            if (rangeMatches.any { first in it.range } || singles.any { first in it.range }) null
            else TimeMention("${match.groupValues[1]}:${match.groupValues[2]}", LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()), null, first..match.range.last)
        }.toList()
        return (ranges + singles + twentyFourHour).sortedBy { it.range.first }
    }

    fun uniqueTimes(text: String): List<TimeMention> {
        val all = times(text)
        // A repeated start time beside the range adds no new fact.
        return all.filter { value -> value.end != null || all.none { it.start == value.start && it.end != null } }
            .distinctBy { it.key }
    }

    fun hasTimeConflict(text: String): Boolean {
        val values = times(text)
        return values.map { it.start }.distinct().size > 1 || values.mapNotNull { it.end }.distinct().size > 1
    }

    fun parseDate(value: String): LocalDate? {
        val clean = value.trim().replace(Regex("(?i)(?<=\\d)(st|nd|rd|th)\\b"), "")
        val numeric = Regex("^(\\d{1,4})[./-](\\d{1,2})[./-](\\d{1,4})$").matchEntire(clean)
        if (numeric != null) {
            val first = numeric.groupValues[1]
            val last = numeric.groupValues[3]
            val iso = first.length == 4
            val yearText = if (iso) first else last
            if (yearText.length !in setOf(2, 4)) return null
            val year = yearText.toInt().let { if (yearText.length == 2) 2000 + it else it }
            return runCatching { LocalDate.of(year, numeric.groupValues[2].toInt(), if (iso) last.toInt() else first.toInt()) }.getOrNull()
        }
        return listOf("d MMMM uuuu", "MMMM d uuuu", "MMMM d, uuuu", "d MMM uuuu", "MMM d uuuu", "MMM d, uuuu").firstNotNullOfOrNull { pattern ->
            val formatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
            runCatching { LocalDate.parse(clean, formatter) }.getOrNull()
        }
    }

    fun parseTime(value: String): TimeMention? = times(value).singleOrNull()?.takeIf {
        val only = value.trim().replace(Regex("(?i)^at\\s+"), "")
        it.text.equals(only.trimEnd('.'), ignoreCase = true)
    }

    /** A weekday belongs to the adjacent date, not the first weekday in a document. */
    fun weekdayConflicts(text: String, claimedDate: String?): List<String> {
        val date = claimedDate?.let(::parseDate) ?: return emptyList()
        val dateMentions = dates(text).filter { it.value == date }
        val weekdays = Regex("\\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b", RegexOption.IGNORE_CASE).findAll(text)
        return weekdays.mapNotNull { weekday ->
            val adjacent = dateMentions.any { mention ->
                val start = minOf(weekday.range.last + 1, mention.range.last + 1)
                val end = maxOf(weekday.range.first, mention.range.first)
                if (end < start || end - start > 8) false else text.substring(start, end).all { it.isWhitespace() || it in ",()·:" }
            }
            if (adjacent && !weekday.value.equals(date.dayOfWeek.name, ignoreCase = true))
                "Weekday/date conflict: ${weekday.value} does not match $claimedDate" else null
        }.distinct().toList()
    }

    private fun clock(hourText: String, minuteText: String, meridiem: String): LocalTime? {
        val hour = hourText.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val minute = minuteText.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val pm = meridiem.replace(".", "").equals("pm", ignoreCase = true)
        return LocalTime.of(hour % 12 + if (pm) 12 else 0, minute)
    }
}
