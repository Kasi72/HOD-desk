package com.hoddesk.app.workflow

import com.hoddesk.app.settings.FixedBlock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** "Remind me later" choices for an item in Review. Pure: the caller passes the clock and zone. */
object SnoozePlan {
    data class Option(val label: String, val until: Long)

    /**
     * Later today (3 hours, only before 8 pm), after the next OPD-type block today or tomorrow,
     * tomorrow morning at [morningMinute], and next Monday morning.
     */
    fun options(now: Long, blocks: List<FixedBlock>, morningMinute: Int = 8 * 60, zone: ZoneId = ZoneId.systemDefault()): List<Option> {
        val here = Instant.ofEpochMilli(now).atZone(zone)
        val today = here.toLocalDate()
        fun at(day: LocalDate, minute: Int) = day.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli()
        val morning = morningMinute.coerceIn(0, 24 * 60 - 1)
        return buildList {
            if (here.hour < 20) add(Option("Later today", now + 3 * 60 * 60_000L))
            afterOpd(now, blocks, zone)?.let { add(it) }
            add(Option("Tomorrow morning", at(today.plusDays(1), morning)))
            val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            add(Option("Next week", at(monday, morning)))
        }
    }

    /** End of the next block whose label mentions OPD (today if it has not ended yet, else the next day it runs). */
    fun afterOpd(now: Long, blocks: List<FixedBlock>, zone: ZoneId = ZoneId.systemDefault()): Option? {
        val opd = blocks.filter { it.label.contains("OPD", ignoreCase = true) }
        if (opd.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (offset in 0..7) {
            val day = today.plusDays(offset.toLong())
            val ends = opd.filter { day.dayOfWeek in it.days }.map { b ->
                day.atTime(LocalTime.of(b.endMinute.coerceIn(0, 24 * 60 - 1) / 60, b.endMinute.coerceIn(0, 24 * 60 - 1) % 60)).atZone(zone).toInstant().toEpochMilli()
            }.filter { it > now }.sorted()
            ends.firstOrNull()?.let { end -> return Option(if (offset == 0) "After OPD" else "After OPD · " + day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), end + 15 * 60_000L) }
        }
        return null
    }

    fun isSnoozed(snoozedUntil: Long?, now: Long): Boolean = snoozedUntil != null && snoozedUntil > now
}

/** Short countdown shown next to a due date: "in 3 days", "in 2 h", "overdue 1 day". */
object DueCountdown {
    enum class Tone { OVERDUE, SOON, LATER }
    data class Label(val text: String, val tone: Tone)

    fun of(dueAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Label? {
        val minutes = (dueAt - now) / 60_000L
        if (dueAt < now) {
            val late = (now - dueAt) / 60_000L
            val text = when {
                late < 60 -> "overdue ${late.coerceAtLeast(1)} min"
                late < 24 * 60 -> "overdue ${late / 60} h"
                else -> (late / (24 * 60)).let { d -> "overdue $d day${if (d == 1L) "" else "s"}" }
            }
            return Label(text, Tone.OVERDUE)
        }
        if (minutes < 60) return Label("in ${minutes.coerceAtLeast(1)} min", Tone.SOON)
        val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate())
        return when {
            days == 0L -> Label("in ${minutes / 60} h", Tone.SOON)
            days == 1L -> null // "Tomorrow, 9:00 am" already says it
            days <= 30 -> Label("in $days days", Tone.LATER)
            else -> null
        }
    }
}
