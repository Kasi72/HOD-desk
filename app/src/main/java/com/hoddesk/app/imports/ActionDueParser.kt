package com.hoddesk.app.imports

import com.hoddesk.app.reasoning.TemporalEvidence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Converts one confirmed, valid date/time pair into a calendar-safe instant. */
object ActionDueParser {
    fun parse(dateText: String?, timeText: String?, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Long? {
        val rawDate = dateText?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val anchor = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = parseDate(rawDate, anchor) ?: return null
        if (TemporalEvidence.weekdayConflicts(rawDate, date.toString()).isNotEmpty()) return null
        val rawTime = timeText?.trim()?.takeUnless(String::isBlank)
        // Date-only deadlines can use midnight. Explicit invalid or ambiguous
        // clocks must fail instead of silently becoming midnight.
        val time = if (rawTime == null) LocalTime.MIDNIGHT else parseTime(rawTime) ?: return null
        return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    }

    fun parseIfUnambiguous(dateText: String?, timeText: String?, ambiguity: String?, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Long? =
        if (ambiguity?.let { Regex("(?i)confirmation|conflict|multiple|requires .*check").containsMatchIn(it) } == true) null
        else parse(dateText, timeText, zone, now)

    private fun parseDate(raw: String, anchor: LocalDate): LocalDate? {
        val clean = raw.replace(Regex("(?i)^(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)[, ]+"), "").trim()
        TemporalEvidence.parseDate(clean)?.let { return it }
        when (clean.lowercase(Locale.ROOT)) {
            "today" -> return anchor
            "tomorrow" -> return anchor.plusDays(1)
        }
        DayOfWeek.values().firstOrNull { it.name.equals(clean, true) }?.let {
            return anchor.with(TemporalAdjusters.nextOrSame(it))
        }
        Regex("^(\\d{1,2})[./-](\\d{1,2})$").matchEntire(clean)?.let {
            return runCatching { LocalDate.of(anchor.year, it.groupValues[2].toInt(), it.groupValues[1].toInt()) }.getOrNull()
        }
        // Missing years are interpreted only at the confirmation boundary.
        // Field extraction/grounding never makes this assumption.
        return TemporalEvidence.parseDate("$clean ${anchor.year}")
    }

    private fun parseTime(raw: String): LocalTime? {
        val clean = raw.replace(Regex("(?i)^at\\s+"), "").trim()
        TemporalEvidence.parseTime(clean)?.let { return it.start }
        val clock = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(clean) ?: return null
        return LocalTime.of(clock.groupValues[1].toInt(), clock.groupValues[2].toInt())
    }
}
