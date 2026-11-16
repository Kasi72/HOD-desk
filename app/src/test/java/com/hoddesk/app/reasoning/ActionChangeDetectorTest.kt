package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionChangeDetectorTest {
    @Test fun revised_date_and_venue_are_reported_as_changes() {
        val old = ActionPointEntity(1, 1, "Attend meeting", dateText = "19 Sep", locationText = "Erode", evidence = "old", confidence = .8f)
        val current = old.copy(dateText = "20 Sep", locationText = "Salem")
        val changes = ActionChangeDetector.compare(old, current)
        assertEquals(setOf("date", "location"), changes.map { it.field }.toSet())
        assertTrue(changes.any { it.previous == "19 Sep" && it.current == "20 Sep" })
    }
}
