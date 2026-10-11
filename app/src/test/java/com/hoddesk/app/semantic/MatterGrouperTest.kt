package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.SourceRelationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatterGrouperTest {
    @Test fun related_sources_form_one_matter_and_latest_is_selected() {
        val old = SourceEntity(id = 1, title = "Old order", body = "Attend review", sourceType = "PDF", capturedAt = 1)
        val latest = old.copy(id = 2, title = "Revised order", capturedAt = 2)
        val relation = SourceRelationEntity(sourceId = 2, relatedSourceId = 1, relationType = "supersedes", score = .9f, rationale = "revision", createdAt = 3)
        val matter = MatterGrouper.group(listOf(old, latest), listOf(relation)).single()
        assertEquals(2L, matter.latest.id)
        assertTrue(matter.hasConflict)
        assertEquals(setOf(1L), matter.supersededSourceIds)
    }
}
