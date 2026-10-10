package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSupersessionTest {
    private fun source(id: Long, title: String, body: String, time: Long) = SourceEntity(id = id, title = title, body = body, sourceType = "PDF", capturedAt = time)

    @Test fun explicit_revision_on_newer_source_marks_supersedes() {
        val old = source(1, "Duty order", "Resident roster for Monday", 1)
        val newer = source(2, "Revised duty order", "This revised order supersedes the earlier roster", 2)
        assertEquals("supersedes", SourceSupersession.decide(old, newer, .9f)?.relationType)
    }

    @Test fun similar_sources_without_revision_language_stay_related() {
        val old = source(1, "Meeting order", "DME review at 2 PM", 1)
        val newer = source(2, "Meeting note", "DME review at 2 PM", 2)
        assertEquals("related", SourceSupersession.decide(old, newer, .9f)?.relationType)
    }

    @Test fun near_identical_imports_are_duplicates() {
        val a = source(1, "Order", "Attend review", 1)
        val b = source(2, "Order copy", "Attend review", 2)
        assertEquals("duplicate", SourceSupersession.decide(a, b, .97f)?.relationType)
    }
}
