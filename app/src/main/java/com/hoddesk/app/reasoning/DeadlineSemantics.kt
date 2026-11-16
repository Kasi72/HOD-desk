package com.hoddesk.app.reasoning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Reads the words around a stated date so "by", "before", "after", "from" and "within" are not all
 * treated as "on". The stated date itself is never rewritten: grounding still cites the exact text.
 * Only two things change downstream:
 *  - a date-only "before X" deadline falls due the day before X;
 *  - "after X", "from X" and "within N days" are not deadlines on their own, so they are held for
 *    the reviewer's confirmation instead of being scheduled.
 */
object DeadlineSemantics {
    enum class Kind { ON, BY, BEFORE, AFTER, FROM, WITHIN, NONE }

    data class Reading(val kind: Kind, val phrase: String? = null, val amount: Int? = null, val workingDays: Boolean = false, val weeks: Boolean = false) {
        /** True when the date alone must not be scheduled without the reviewer's confirmation. */
        val needsConfirmation: Boolean get() = kind == Kind.AFTER || kind == Kind.FROM || kind == Kind.WITHIN
    }

    private const val WEEKDAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private val trailingWeekday = Regex("(?i)(?:$WEEKDAYS)\\s*,?\\s*$")
    private val by = Regex("(?i)(?:on\\s+or\\s+before|not\\s+later\\s+than|no\\s+later\\s+than|latest\\s+by|by|till|until|up\\s*to)(?:\\s+the)?\\s*$")
    private val before = Regex("(?i)(?:before|prior\\s+to)(?:\\s+the)?\\s*$")
    private val after = Regex("(?i)(?:after|later\\s+than|beyond)(?:\\s+the)?\\s*$")
    private val from = Regex("(?i)(?:from|w\\.?\\s*e\\.?\\s*f\\.?|with\\s+effect\\s+from|starting|commencing)(?:\\s+the)?\\s*$")
    private val on = Regex("(?i)(?:on|dated|date\\s*:?)(?:\\s+the)?\\s*$")
    private val numberWords = mapOf("a" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "ten" to 10, "fifteen" to 15, "thirty" to 30)
    private val within = Regex("(?i)\\bwithin\\s+(\\d{1,3}|a|one|two|three|four|five|six|seven|ten|fifteen|thirty)\\s+(working\\s+)?(days?|weeks?)\\b")
    private val withinTamil = Regex("(\\d{1,3})\\s*(?:வேலை\\s*)?நாட்களுக்குள்")

    fun read(evidence: String, dateText: String?): Reading {
        within.find(evidence)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull() ?: numberWords[m.groupValues[1].lowercase(Locale.ROOT)] ?: return@let
            return Reading(Kind.WITHIN, m.value.trim(), amount, workingDays = m.groupValues[2].isNotBlank(), weeks = m.groupValues[3].startsWith("week", true))
        }
        withinTamil.find(evidence)?.let { m -> return Reading(Kind.WITHIN, m.value.trim(), m.groupValues[1].toInt(), workingDays = m.value.contains("வேலை")) }
        val date = dateText?.trim()?.takeIf { it.isNotBlank() && !it.contains(',') } ?: return Reading(Kind.NONE)
        val index = evidence.indexOf(date, ignoreCase = true).takeIf { it >= 0 } ?: return Reading(Kind.NONE)
        val prefix = evidence.substring(maxOf(0, index - 40), index).trimEnd().replace(trailingWeekday, "").trimEnd()
        val suffix = evidence.substring(index + date.length, minOf(evidence.length, index + date.length + 20)).trimStart('-', ' ')
        fun phrase(match: MatchResult?) = match?.value?.trim()?.let { "$it $date" }
        // Tamil postpositions follow the date: 15.11.2026க்குள் (by), க்கு முன் (before), க்குப் பிறகு (after), முதல் (from).
        when {
            suffix.startsWith("க்குள்") -> return Reading(Kind.BY, "$date${suffix.substringBefore(' ')}")
            Regex("^(?:க்கு|அன்று)?\\s*முன்").containsMatchIn(suffix) -> return Reading(Kind.BEFORE, "$date ${suffix.take(10).trim()}")
            Regex("^(?:க்குப்|க்கு)?\\s*(?:பிறகு|பின்)").containsMatchIn(suffix) -> return Reading(Kind.AFTER, "$date ${suffix.take(12).trim()}")
            suffix.startsWith("முதல்") -> return Reading(Kind.FROM, "$date முதல்")
        }
        by.find(prefix)?.let { return Reading(Kind.BY, phrase(it)) }
        before.find(prefix)?.let { return Reading(Kind.BEFORE, phrase(it)) }
        after.find(prefix)?.let { return Reading(Kind.AFTER, phrase(it)) }
        from.find(prefix)?.let { return Reading(Kind.FROM, phrase(it)) }
        on.find(prefix)?.let { return Reading(Kind.ON, phrase(it)) }
        return Reading(Kind.NONE)
    }

    /** The calendar day the work is due, given the stated day. Times are kept as stated. */
    fun dueDate(stated: LocalDate, reading: Reading, hasTime: Boolean): LocalDate =
        if (reading.kind == Kind.BEFORE && !hasTime) stated.minusDays(1) else stated

    /** A relative deadline counted from [start]; weekends are skipped for working days. */
    fun relativeDue(start: LocalDate, reading: Reading): LocalDate? {
        val amount = reading.amount?.takeIf { reading.kind == Kind.WITHIN } ?: return null
        if (reading.weeks) return start.plusWeeks(amount.toLong())
        if (!reading.workingDays) return start.plusDays(amount.toLong())
        var day = start; var left = amount
        while (left > 0) { day = day.plusDays(1); if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) left-- }
        return day
    }

    /** Explicitly stated zone wins over the phone's zone; Indian circulars often say IST. */
    fun zone(evidence: String, default: ZoneId = ZoneId.systemDefault()): ZoneId = when {
        Regex("\\bIST\\b").containsMatchIn(evidence) -> ZoneId.of("Asia/Kolkata")
        Regex("\\b(?:UTC|GMT)\\b").containsMatchIn(evidence) -> ZoneId.of("UTC")
        else -> default
    }

    /** Ambiguity text for review; phrased so the scheduling guard holds it until confirmed. */
    fun reviewNote(reading: Reading): String? = when (reading.kind) {
        Kind.AFTER -> "'${reading.phrase}' is a start date, not a deadline; requires confirmation."
        Kind.FROM -> "'${reading.phrase}' is when this starts, not a deadline; requires confirmation."
        Kind.WITHIN -> "Relative deadline '${reading.phrase}'; the start date requires confirmation."
        else -> null
    }

    /** One plain line for the approval preview. */
    fun explain(reading: Reading): String? = when (reading.kind) {
        Kind.BY -> "“${reading.phrase}” · due on that day"
        Kind.BEFORE -> "“${reading.phrase}” · due the day before"
        Kind.AFTER -> "“${reading.phrase}” · a start date, not a deadline"
        Kind.FROM -> "“${reading.phrase}” · a start date, not a deadline"
        Kind.WITHIN -> "“${reading.phrase}” · counted from a start date you confirm"
        Kind.ON -> "“${reading.phrase}” · on that day"
        Kind.NONE -> null
    }
}
