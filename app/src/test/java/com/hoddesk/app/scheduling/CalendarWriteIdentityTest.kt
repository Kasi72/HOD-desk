package com.hoddesk.app.scheduling

import com.hoddesk.app.calendar.CalendarWriteIdentity
import org.junit.Assert.*
import org.junit.Test

class CalendarWriteIdentityTest {
    @Test fun calendar_identity_requires_the_complete_marker_line() {
        assertTrue(CalendarWriteIdentity.matches("Created by HOD Desk\nHODDESK_EVENT:12", 12))
        assertFalse(CalendarWriteIdentity.matches("HODDESK_EVENT:123", 12))
        assertFalse(CalendarWriteIdentity.matches("Another appointment", 12))
        assertFalse(CalendarWriteIdentity.matches(null, 12))
    }
    @Test(expected = IllegalArgumentException::class) fun transient_event_cannot_be_written() {
        CalendarWriteIdentity.marker(0)
    }
}
