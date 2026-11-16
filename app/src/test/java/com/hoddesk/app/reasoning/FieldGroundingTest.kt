package com.hoddesk.app.reasoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldGroundingTest {
    @Test fun correct_weekday_does_not_erase_a_valid_date() {
        val result = FieldGrounding.validate("Attend on Tuesday, 13 October 2026 at 3 PM", "13 October 2026", "3 PM", null, emptyList(), emptyList())
        assertEquals("13 October 2026", result.date)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun wrong_weekday_clears_date_and_marks_conflict() {
        val result = FieldGrounding.validate("Attend on Monday, 13 October 2026", "13 October 2026", null, null, emptyList(), emptyList())
        assertEquals(null, result.date)
        assertTrue(result.conflicts.isNotEmpty())
    }

    @Test fun unrelated_weekday_does_not_invalidate_the_cited_date() {
        val result = FieldGrounding.validate("Submit the budget Friday. Attend review on Tuesday, 13 October 2026", "13 October 2026", null, null, emptyList(), emptyList())
        assertEquals("13 October 2026", result.date)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun structured_clock_validation_rejects_invalid_hours_and_minutes() {
        val result = FieldGrounding.validate("Attend at 29 PM on 13 October 2026", "13 October 2026", "29 PM", null, emptyList(), emptyList())
        assertEquals(null, result.time)
        assertFalse(result.grounded)
    }

    @Test fun field_phrase_requires_word_boundaries() {
        assertFalse(FieldGrounding.supportsField("Attend XDME review", "DME"))
    }

    @Test fun matching_subject_nouns_does_not_ground_a_different_action() {
        assertFalse(FieldGrounding.supportsAction("Attend the DME meeting", "Cancel the DME meeting"))
        assertFalse(FieldGrounding.supportsAction("A DME meeting has been scheduled", "Attend the DME meeting"))
    }

    @Test fun partial_date_cannot_be_given_an_implicit_hardcoded_year() {
        val result = FieldGrounding.validate("Review on 13/10", "13/10", null, null, emptyList(), emptyList())
        assertEquals(null, result.date)
        assertFalse(result.grounded)
    }
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
