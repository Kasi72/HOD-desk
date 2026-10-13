package com.hoddesk.app.imports

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ActionDueParserTest {
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
