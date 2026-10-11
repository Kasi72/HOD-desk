package com.hoddesk.app.scheduling

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.TaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplanningEngineTest {
    @Test fun conflictsProduceProposalWithoutWritingCalendar() {
        val task = TaskEntity(id = 1, title = "Submit report", project = "Work", dueAt = 1_000L, priority = "high")
        val event = EventEntity(id = 2, title = "Ward round", startsAt = 1_000L, endsAt = 4_000L)
        val proposal = ReplanningEngine.propose(listOf(task), listOf(event), emptyList(), now = 1_000L)
        assertFalse(proposal.changes.isEmpty())
        assertTrue(proposal.rationale.contains("confirmation"))
    }

    @Test fun configuredVenueTravelAddsBufferToProposal() {
        val task = TaskEntity(id = 3, title = "Collector meeting", project = "Erode Collectorate", dueAt = 10_000L)
        val event = EventEntity(id = 4, title = "Ward round", startsAt = 1_000L, endsAt = 9_000L, location = "Psychiatry ward")
        val proposal = ReplanningEngine.propose(listOf(task), listOf(event), emptyList(), mapOf(("Psychiatry ward" to "Erode Collectorate") to 20_000L), now = 10_000L)
        assertTrue(proposal.changes.first().reason.contains("travel"))
    }
}
