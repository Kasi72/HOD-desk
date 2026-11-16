package com.hoddesk.app.reasoning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * One-tap corrections for the Review screen. Each chip writes an explicit, parseable value into the
 * edit fields; the reviewer still confirms before approval, and the saved edit feeds local learning.
 */
object CorrectionChips {
    data class DateChip(val label: String, val value: String)

    private val format = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)

    fun dates(today: LocalDate): List<DateChip> = listOf(
        DateChip("Today", today.format(format)),
        DateChip("Tomorrow", today.plusDays(1).format(format)),
        DateChip("Next Monday", today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).format(format)),
        DateChip("In a week", today.plusWeeks(1).format(format)),
    )

    val times: List<String> = listOf("9:00 AM", "11:00 AM", "2:00 PM", "4:00 PM")

    const val ONLINE = "Online"
}
