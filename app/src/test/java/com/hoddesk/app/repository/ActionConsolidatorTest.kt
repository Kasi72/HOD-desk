package com.hoddesk.app.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionConsolidatorTest {
    @Test fun repeated_cross_source_candidates_become_one_evidence_backed_action() {
        val first = ActionPointDraft("Attend DME review", "19 September 2026", "2 PM", "Erode", evidence = "WhatsApp message", confidence = .72f)
        val second = first.copy(evidence = "PDF order", confidence = .91f, ambiguity = "Confirm room")
        val merged = ActionConsolidator.merge(listOf(first, second))
        assertEquals(1, merged.size)
        assertEquals(.91f, merged.single().confidence)
        assertTrue(merged.single().evidence.contains("WhatsApp"))
        assertTrue(merged.single().evidence.contains("PDF"))
        assertEquals("Confirm room", merged.single().ambiguity)
    }
}
