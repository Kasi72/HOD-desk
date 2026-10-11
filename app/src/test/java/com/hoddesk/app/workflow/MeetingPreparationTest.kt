package com.hoddesk.app.workflow

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingPreparationTest {
    @Test fun pack_uses_linked_task_and_source_evidence() {
        val source = SourceEntity(id = 4, title = "DME order", body = "Attend review", sourceType = "PDF")
        val task = TaskEntity(id = 8, sourceId = 4, title = "Prepare review notes", project = "DME")
        val event = EventEntity(taskId = 8, title = "DME review", startsAt = 10, endsAt = 20)
        val pack = MeetingPreparation.pack(event, listOf(task), listOf(source))
        assertEquals(listOf("Prepare review notes"), pack.agenda)
        assertTrue(pack.evidence.single().contains("DME order"))
    }
}
