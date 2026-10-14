package com.hoddesk.app.imports

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ActionDueParserTest {
    @Test fun invalid_explicit_clock_does_not_become_midnight() {
        listOf("29 PM", "13 PM", "0 AM", "3:99 PM", "soon", "3 PM, 4 PM").forEach { time ->
            assertNull("Invalid clock $time", ActionDueParser.parse("13.10.2026", time))
        }
    }

    @Test fun multiple_dates_are_not_reduced_to_the_first_value() {
        assertNull(ActionDueParser.parse("13.10.2026, 14.10.2026", "3 PM"))
    }

    @Test fun conflicting_schedule_requires_confirmation_before_due_date_writing() {
        assertNull(ActionDueParser.parseIfUnambiguous("13.10.2026", "3 PM", "Time conflict: invitation says 3:30 PM; requires confirmation."))
    }

    @Test fun accepts_iso_and_short_month_dates_and_24_hour_clocks() {
        val zone = ZoneId.of("Asia/Kolkata")
        val expected = ActionDueParser.parse("13.10.2026", "3 PM", zone)
        assertEquals(expected, ActionDueParser.parse("2026-10-13", "15:00", zone))
        assertEquals(expected, ActionDueParser.parse("13 Oct 2026", "3 PM", zone))
    }

    @Test fun valid_date_without_explicit_time_remains_a_date_only_deadline() {
        val zone = ZoneId.of("Asia/Kolkata")
        val due = ActionDueParser.parse("13.10.2026", null, zone)!!
        assertEquals(0, Instant.ofEpochMilli(due).atZone(zone).hour)
    }

    @Test fun time_range_uses_its_stated_start_and_rejects_invalid_range_end() {
        assertEquals(ActionDueParser.parse("13.10.2026", "3:30 PM"), ActionDueParser.parse("13.10.2026", "3:30 – 4:30pm"))
        assertNull(ActionDueParser.parse("13.10.2026", "3:30 – 29:30pm"))
    }

    @Test fun omitted_start_meridiem_across_noon_is_ambiguous() {
        assertNull(ActionDueParser.parse("13.10.2026", "11:30–1:30 PM"))
        assertEquals(ActionDueParser.parse("13.10.2026", "11:30 AM"), ActionDueParser.parse("13.10.2026", "11:30 AM–1:30 PM"))
    }
    @Test fun parsesIndianDottedDateAndAfternoonTime() {
        val zone = ZoneId.of("Asia/Kolkata")
        val due = ActionDueParser.parse("13.10.2026", "03:00 PM", zone)
        assertNotNull(due)
        val local = Instant.ofEpochMilli(due!!).atZone(zone)
        assertEquals(2026, local.year)
        assertEquals(10, local.monthValue)
        assertEquals(13, local.dayOfMonth)
        assertEquals(15, local.hour)
    }
}
