package com.hoddesk.app.reasoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldGroundingTest {
    @Test fun fields_present_in_evidence_are_retained() {
        val result = FieldGrounding.validate("Attend DME review at 2 PM in Erode Collectorate", "", "2 PM", "Erode Collectorate", listOf("DME"), emptyList())
        assertEquals("2 PM", result.time)
        assertEquals("Erode Collectorate", result.location)
        assertEquals(listOf("DME"), result.people)
        assertTrue(result.grounded)
    }

    @Test fun unsupported_field_is_cleared_and_requires_confirmation() {
        val result = FieldGrounding.validate("Attend review at 2 PM", "19 September 2026", "2 PM", "Erode", emptyList(), emptyList())
        assertEquals(null, result.date)
        assertEquals(null, result.location)
        assertFalse(result.grounded)
        assertTrue(result.ambiguities.any { it.contains("date") })
    }

    @Test fun fields_are_not_reconstructed_from_unrelated_sentences() {
        val result = FieldGrounding.validate(
            "Review is at 2 PM. Erode has a separate circular. Collectorate follow-up is pending.",
            null, "2 PM Erode", "Erode Collectorate", emptyList(), emptyList()
        )
        assertEquals(null, result.time)
        assertEquals(null, result.location)
        assertFalse(result.grounded)
    }
}