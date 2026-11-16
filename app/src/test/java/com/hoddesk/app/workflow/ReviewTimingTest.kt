package com.hoddesk.app.workflow

import com.hoddesk.app.settings.FixedBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class ReviewTimingTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private val opd = FixedBlock("OPD", setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 9 * 60, 13 * 60)

    // 16 Nov 2026 is a Monday.
    @Test fun morningOffersAllChoicesInOrder() {
        val now = at(2026, 11, 16, 10)
        val options = SnoozePlan.options(now, listOf(opd), zone = zone)
        assertEquals(listOf("Later today", "After OPD", "Tomorrow morning", "Next week"), options.map { it.label })
        assertEquals(now + 3 * 3_600_000L, options[0].until)
        assertEquals(at(2026, 11, 16, 13, 15), options[1].until) // 15 minutes after OPD ends
        assertEquals(at(2026, 11, 17, 8), options[2].until)
        assertEquals(at(2026, 11, 23, 8), options[3].until) // next Monday, not today
    }

    @Test fun eveningDropsLaterTodayAndUsesNextOpdDay() {
        val now = at(2026, 11, 16, 21)
        val options = SnoozePlan.options(now, listOf(opd), zone = zone)
        assertFalse(options.any { it.label == "Later today" })
        val after = options.first { it.label.startsWith("After OPD") }
        assertEquals(at(2026, 11, 18, 13, 15), after.until) // Wednesday's OPD
        assertTrue(after.label.contains("·"))
    }

    @Test fun noOpdBlockMeansNoOpdChoice() {
        assertNull(SnoozePlan.afterOpd(at(2026, 11, 16, 10), listOf(FixedBlock("Ward rounds", DayOfWeek.values().toSet(), 8 * 60, 9 * 60)), zone))
    }

    @Test fun snoozeStateFollowsTheClock() {
        assertTrue(SnoozePlan.isSnoozed(2_000, 1_000))
        assertFalse(SnoozePlan.isSnoozed(1_000, 1_000))
        assertFalse(SnoozePlan.isSnoozed(null, 1_000))
    }

    @Test fun countdownLabels() {
        val now = at(2026, 11, 16, 10)
        assertEquals(DueCountdown.Label("in 25 min", DueCountdown.Tone.SOON), DueCountdown.of(now + 25 * 60_000L, now, zone))
        assertEquals(DueCountdown.Label("in 7 h", DueCountdown.Tone.SOON), DueCountdown.of(at(2026, 11, 16, 17), now, zone))
        assertNull(DueCountdown.of(at(2026, 11, 17, 9), now, zone)) // "Tomorrow" already says it
        assertEquals(DueCountdown.Label("in 3 days", DueCountdown.Tone.LATER), DueCountdown.of(at(2026, 11, 19, 9), now, zone))
        assertNull(DueCountdown.of(at(2027, 1, 30, 9), now, zone))
        assertEquals(DueCountdown.Label("overdue 2 h", DueCountdown.Tone.OVERDUE), DueCountdown.of(at(2026, 11, 16, 8), now, zone))
        assertEquals(DueCountdown.Label("overdue 1 day", DueCountdown.Tone.OVERDUE), DueCountdown.of(at(2026, 11, 15, 9), now, zone))
        assertEquals(DueCountdown.Label("overdue 3 days", DueCountdown.Tone.OVERDUE), DueCountdown.of(at(2026, 11, 13, 9), now, zone))
    }
}
