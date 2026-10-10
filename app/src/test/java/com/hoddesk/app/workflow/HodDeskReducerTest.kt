package com.hoddesk.app.workflow

import org.junit.Assert.assertEquals
import org.junit.Test

class HodDeskReducerTest {
    @Test fun ingestReviewApproveScheduleComplete() {
        var state = WorkflowState()
        state = reduce(state, WorkflowAction.Ingest(10))
        assertEquals(setOf(10L), state.sources)
        state = reduce(state, WorkflowAction.Approve("Send dean the SOP"))
        val taskId = state.tasks.single().id
        state = reduce(state, WorkflowAction.Schedule(taskId, 55))
        assertEquals(Status.SCHEDULED, state.tasks.single().status)
        state = reduce(state, WorkflowAction.Complete(taskId, evidenceCount = 1))
        assertEquals(Status.COMPLETED, state.tasks.single().status)
        assertEquals(1, state.tasks.single().evidenceCount)
    }

    @Test fun completionCannotChangeUnknownTask() {
        val state = WorkflowState(tasks = listOf(WorkflowTask(1, "Known")))
        assertEquals(state, reduce(state, WorkflowAction.Complete(99)))
    }
}
