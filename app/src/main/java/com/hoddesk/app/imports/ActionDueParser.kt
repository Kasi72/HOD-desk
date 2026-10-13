package com.hoddesk.app.imports

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

/** Converts an explicitly extracted date/time pair into a calendar-safe instant. */
object ActionDueParser {
    private val numeric = Regex("(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{2,4}))?", RegexOption.IGNORE_CASE)
    private val clock = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)", RegexOption.IGNORE_CASE)
    private val monthDate = DateTimeFormatterBuilder().parseCaseInsensitive()
        .appendPattern("d MMMM uuuu").toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)

    fun parse(dateText: String?, timeText: String?, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Long? {
        val rawDate = dateText?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val date = parseDate(rawDate, now) ?: return null
        val time = timeText?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let(::parseTime) ?: LocalTime.MIDNIGHT
        return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseDate(raw: String, now: Long): LocalDate? {
        val clean = raw.lowercase(Locale.ROOT).replace(Regex("(st|nd|rd|th)\\b"), "").trim()
        numeric.find(clean)?.let { m ->
            val year = m.groupValues[3].ifBlank { java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).year.toString() }
                .let { if (it.length == 2) "20$it" else it }.toInt()
            return runCatching { LocalDate.of(year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return runCatching { LocalDate.parse(clean, monthDate) }.getOrNull()
    }

    private fun parseTime(raw: String): LocalTime? {
        val m = clock.find(raw) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifBlank { "0" }.toInt()
        if (m.groupValues[3].equals("PM", true) && hour < 12) hour += 12
        if (m.groupValues[3].equals("AM", true) && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
