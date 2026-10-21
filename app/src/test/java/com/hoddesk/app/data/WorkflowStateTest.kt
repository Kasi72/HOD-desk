package com.hoddesk.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowStateTest {
    @Test fun validTransitionsArePersisted() {
        val source = SourceEntity(id = 1, title = "Notice", body = "Attend", sourceType = "Document")
        val taskEvent = source.transitionTo(SourceLifecycle.APPROVED).transitionTo(SourceLifecycle.TASK_EVENT)
        assertEquals("task_event", taskEvent.lifecycleState)
        assertEquals("ready", taskEvent.processingStatus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deletedSourceCannotReopen() {
        SourceEntity(id = 1, title = "Notice", body = "", sourceType = "Document")
            .transitionTo(SourceLifecycle.DELETED)
            .transitionTo(SourceLifecycle.REVIEW)
    }
}
