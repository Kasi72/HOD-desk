package com.hoddesk.app.imports

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test fun fields_are_sentence_local_and_extended_fields_are_captured() {
        val drafts = ActionPointExtractor().extract("Attend the DME review at 2 PM in Erode Collectorate. Submit GO No. 214/2026 weekly; owner: Dr Raman.")
        assertEquals(2, drafts.size)
        assertTrue(drafts[0].timeText == "2 PM")
        assertTrue(drafts[0].locationText?.contains("Erode Collectorate") == true)
        assertTrue(drafts.any { it.referenceNumber?.contains("214/2026") == true })
        assertTrue(drafts.any { it.recurrenceRule?.contains("weekly", ignoreCase = true) == true })
    }

    @Test fun processing_error_text_never_becomes_an_action() {
        assertTrue(ActionPointExtractor().extract("OCR could not read this image; manual review required.").isEmpty())
        assertTrue(ActionPointExtractor().extract("Audio is queued for the approved offline transcription runtime.").isEmpty())
    }

    @Test fun independent_actions_split_on_explicit_clause_boundary() {
        val drafts = ActionPointExtractor().extract("Attend the review and then submit the report by Friday.")
        assertEquals(2, drafts.size)
    }
}
