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

    @Test fun reviewerCanCloseAnItemWithoutATask() {
        listOf(SourceLifecycle.REVIEW, SourceLifecycle.IMPORTED).forEach { start ->
            val closed = SourceEntity(id = 1, title = "OCR pending", body = "x", sourceType = "Screenshot", lifecycleState = start.wire)
                .transitionTo(SourceLifecycle.COMPLETED)
            assertEquals("completed", closed.lifecycleState)
            assertEquals("completed", closed.processingStatus)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun processingItemCannotBeClosedMidway() {
        SourceEntity(id = 1, title = "Audio", body = "x", sourceType = "Call", lifecycleState = SourceLifecycle.PROCESSING.wire)
            .transitionTo(SourceLifecycle.COMPLETED)
    }
}
