package com.hoddesk.app.scheduling

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.TaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test fun overrun_is_protected_and_schedule_diff_is_explicit() {
        val task = TaskEntity(id = 5, title = "Submit order", project = "Office", dueAt = 1_000L)
        val event = EventEntity(id = 6, title = "Ward round", startsAt = 1_000L, endsAt = 4_000L)
        val result = ReplanningEngine.proposeForOverrun(listOf(task), listOf(event), emptyList(), 6L, 8_000L, now = 1_000L)
        assertTrue(result.conflicts.any { it.contains("overrun") })
        val diff = ReplanningEngine.scheduleDiff(listOf(task), result.changes)
        assertTrue(diff.moved.isNotEmpty())
    }

    @Test fun a_task_does_not_conflict_with_its_own_existing_event() {
        val task = TaskEntity(id = 1, title = "Submit report", project = "Office", dueAt = 60_000L)
        val own = EventEntity(id = 2, taskId = 1, title = task.title, startsAt = 60_000L, endsAt = 31 * 60_000L, confirmed = true)
        assertTrue(ReplanningEngine.propose(listOf(task), listOf(own), emptyList(), now = 0).changes.isEmpty())
    }

    @Test fun delegated_tasks_do_not_reserve_the_hods_time() {
        val delegated = TaskEntity(id = 1, title = "Submit report", project = "Office", dueAt = 60_000L, assignee = "Secretary")
        val busy = EventEntity(id = 2, title = "Ward round", startsAt = 0, endsAt = 60 * 60_000L)
        assertTrue(ReplanningEngine.propose(listOf(delegated), listOf(busy), emptyList(), now = 0).changes.isEmpty())
    }

    @Test fun configured_travel_values_are_minutes() {
        val task = TaskEntity(id = 1, title = "Collector meeting", project = "Collectorate", dueAt = 60 * 60_000L)
        val earlier = EventEntity(id = 2, title = "Ward round", location = "Ward", startsAt = 0, endsAt = 60 * 60_000L)
        val proposal = ReplanningEngine.propose(listOf(task), listOf(earlier), emptyList(), mapOf(("Ward" to "Collectorate") to 15L), now = 0)
        assertEquals(75 * 60_000L, proposal.changes.single().proposedStart)
    }
}
