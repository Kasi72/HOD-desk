package com.hoddesk.app.secretary

import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SecretaryStateTest {
    @Test fun quarantinedSourcesDoNotInflateActionCounts() {
        val sources = listOf(
            SourceEntity(title = "Act", body = "submit", sourceType = "PDF", triageBucket = "ACT"),
            SourceEntity(title = "Clinical", body = "submit", sourceType = "PDF", sensitivity = "T3", triageBucket = "ACT"),
        )
        val state = SecretaryStateBuilder.build(sources, emptyList(), emptyList(), emptyList(), now = 100L)
        assertEquals(1, state.action)
        assertEquals(1, state.sensitive)
    }

    @Test fun waitingAndOverdueComeFromOpenCommitments() {
        val waiting = CommitmentEntity(direction = "OWED_TO_ME", summary = "Roster", evidence = "msg")
        val overdue = CommitmentEntity(direction = "OWED_BY_ME", summary = "Report", evidence = "msg", dueAt = 1L)
        val state = SecretaryStateBuilder.build(emptyList(), emptyList(), emptyList(), listOf(waiting, overdue), now = 10L)
        assertEquals(1, state.waiting)
        assertEquals(1, state.overdue)
    }
}
