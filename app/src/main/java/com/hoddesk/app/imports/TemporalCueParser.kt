package com.hoddesk.app.imports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

data class TemporalCue(val raw: String, val date: LocalDate?, val status: String, val note: String? = null)

/** Indian DD/MM ordering and message-time anchoring for relative date cues. */
object TemporalCueParser {
    private val numeric = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
    private val weekday = mapOf("monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY)

    fun resolve(raw: String, messageTimeMs: Long, nowMs: Long = System.currentTimeMillis()): TemporalCue {
        val anchor = java.time.Instant.ofEpochMilli(messageTimeMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val value = raw.trim().lowercase()
        val date = when {
            value == "today" || value == "இன்று" -> anchor
            value == "tomorrow" || value == "நாளை" -> anchor.plusDays(1)
            value == "day after tomorrow" -> anchor.plusDays(2)
            value.startsWith("next ") && weekday[value.removePrefix("next ")] != null -> anchor.with(TemporalAdjusters.next(weekday.getValue(value.removePrefix("next "))))
            weekday[value] != null -> anchor.with(TemporalAdjusters.nextOrSame(weekday.getValue(value)))
            else -> parseIndian(value, anchor.year)
        }
        return if (date == null) TemporalCue(raw, null, "UNKNOWN", "Ambiguous or unsupported date")
        else TemporalCue(raw, date, if (date.isBefore(java.time.Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate())) "PAST" else "VALID")
    }

    private fun parseIndian(value: String, defaultYear: Int): LocalDate? {
        val match = numeric.find(value) ?: return null
        val day = match.groupValues[1].toInt(); val month = match.groupValues[2].toInt()
        val year = match.groupValues[3].ifBlank { defaultYear.toString() }.let { if (it.length == 2) "20$it" else it }.toInt()
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    fun weekdayMismatch(weekdayText: String?, date: LocalDate?): Boolean = weekdayText?.lowercase()?.let { weekday[it] }?.let { it != date?.dayOfWeek } ?: false
    fun iso(date: LocalDate?): String? = date?.format(DateTimeFormatter.ISO_LOCAL_DATE)
}
