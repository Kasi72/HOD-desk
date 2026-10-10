package com.hoddesk.app.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TemporalCueParserTest {
    private val anchor = LocalDate.of(2026, 10, 8).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun tomorrowUsesMessageTimestamp() {
        val cue = TemporalCueParser.resolve("tomorrow", anchor, anchor)
        assertEquals("2026-10-09", TemporalCueParser.iso(cue.date))
    }

    @Test fun numericDatesUseIndianDayMonthOrder() {
        val cue = TemporalCueParser.resolve("05/06/2026", anchor, anchor)
        assertEquals("2026-06-05", TemporalCueParser.iso(cue.date))
    }

    @Test fun weekdayMismatchIsFlagged() {
        assertTrue(TemporalCueParser.weekdayMismatch("Monday", LocalDate.of(2026, 10, 15)))
    }
}
