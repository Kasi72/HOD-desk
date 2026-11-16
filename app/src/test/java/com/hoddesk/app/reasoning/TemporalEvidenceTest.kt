package com.hoddesk.app.reasoning

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalEvidenceTest {
    @Test fun ambiguous_range_does_not_become_its_end_time() {
        assertTrue(TemporalEvidence.times("Invitation: 11:30–1:30 PM").isEmpty())
    }

    @Test fun explicit_meridiems_resolve_a_noon_crossing_range() {
        val value = TemporalEvidence.times("Invitation: 11:30 AM–1:30 PM").single()
        assertEquals(LocalTime.of(11, 30), value.start)
        assertEquals(LocalTime.of(13, 30), value.end)
    }

    @Test fun invalid_range_does_not_become_a_valid_part_of_it() {
        assertTrue(TemporalEvidence.times("Invitation: 3 PM–29 PM").isEmpty())
    }
}
