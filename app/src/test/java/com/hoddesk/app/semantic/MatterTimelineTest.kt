package com.hoddesk.app.semantic

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatterTimelineTest {
    @Test fun timelineLinksTasksEventsAndQuestions() {
        val source = SourceEntity(id = 1, title = "DME review", body = "Attend", sourceType = "WhatsApp", capturedAt = 2)
        val matter = MatterGrouper.group(listOf(source), emptyList()).single()
        val task = TaskEntity(id = 5, sourceId = 1, title = "Attend DME review", project = "DME", clarification = "Confirm venue", createdAt = 3)
        val event = EventEntity(id = 9, taskId = 5, title = "DME review", startsAt = 10, endsAt = 20)
        val timeline = MatterTimelineBuilder.build(matter, listOf(task), listOf(event))
        assertEquals(1, timeline.tasks.size)
        assertEquals(1, timeline.events.size)
        assertEquals("DME review", timeline.latestInstruction?.title)
        assertTrue(timeline.unresolvedQuestions.contains("Confirm venue"))
    }
    @Test fun strongest_non_superseded_evidence_is_first() {
        val old = SourceEntity(id = 1, title = "Old order", body = "Meeting at 2 PM", sourceType = "PDF", capturedAt = 1)
        val current = SourceEntity(id = 2, title = "Revised order", body = "Meeting rescheduled to 3 PM", sourceType = "PDF", capturedAt = 2)
        val relation = com.hoddesk.app.data.SourceRelationEntity(sourceId = 2, relatedSourceId = 1, relationType = "supersedes", score = .9f, rationale = "updated")
        val matter = MatterGrouper.group(listOf(old, current), listOf(relation)).single()
        val timeline = MatterTimelineBuilder.build(matter, emptyList(), emptyList())
        assertEquals(2L, timeline.sources.first().id)
        assertEquals(2L, timeline.latestInstruction?.id)
    }
}
