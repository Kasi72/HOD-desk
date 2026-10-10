package com.hoddesk.app.imports

import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPointExtractorTest {
    @Test
    fun extractsMeetingDateTimeAndLocationCues() {
        val body = "Attend online Zoom meeting with DME at 2 PM at Erode Collectorate on 19th September 2026."
        val result = ActionPointExtractor().annotate(body)
        assertTrue(result.contains("Action candidate: Attend online Zoom meeting"))
        assertTrue(result.contains("19th September 2026"))
        assertTrue(result.contains("2 PM"))
        assertTrue(result.contains("Erode Collectorate"))
    }

    @Test
    fun extractsContextualObligationAndScheduleWithoutActionVerb() {
        val body = "All HODs are requested to attend the online DME review. Zoom meeting at 2 PM on 19th September 2026. Resident duty roster due by Friday."
        val result = ActionPointExtractor().annotate(body)
        assertTrue(result.contains("Action candidate:"))
        assertTrue(result.contains("Zoom meeting at 2 PM"))
        assertTrue(result.contains("duty roster due by Friday"))
    }
}
