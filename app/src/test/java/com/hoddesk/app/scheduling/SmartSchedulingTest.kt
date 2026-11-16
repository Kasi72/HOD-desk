package com.hoddesk.app.scheduling

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSchedulingTest {
    @Test fun capacity_merges_overlapping_events_and_reports_overload() {
        val task = TaskEntity(status = "open", title = "Prepare order", project = "Official")
        val events = listOf(
            EventEntity(title = "Meeting", startsAt = 30 * 60_000L, endsAt = 90 * 60_000L),
            EventEntity(title = "Overlap", startsAt = 60 * 60_000L, endsAt = 120 * 60_000L)
        )
        val report = SmartScheduling.capacity(listOf(task, task.copy(id = 2)), events, 0, 180 * 60_000L)
        assertEquals(90, report.availableMinutes)
        assertEquals(0, report.overcommittedMinutes)
        assertTrue(!report.overcommitted)
    }

    @Test fun fixed_blocks_are_never_overwritten_and_travel_is_visible() {
        val task = TaskEntity(id = 4, title = "Collector meeting", project = "Erode Collectorate", priority = "medium")
        val fixed = EventEntity(title = "OPD", startsAt = 0L, endsAt = 60 * 60_000L)
        val proposal = SmartScheduling.propose(listOf(task), listOf(fixed), 0L, 3 * 60 * 60_000L, mapOf("Erode Collectorate" to 15L)).single()
        assertTrue(proposal.start >= 75 * 60_000L)
        assertEquals(15L, proposal.travelMinutes)
        assertTrue(proposal.rationale.contains("travel buffer"))
    }

    @Test fun effort_estimate_and_capacity_detect_overload() {
        val tasks = (1L..4L).map { TaskEntity(id = it, title = "Work $it", project = "Office", priority = "high") }
        assertEquals(45L, SmartScheduling.estimateEffortMinutes(tasks.first()))
        val report = SmartScheduling.capacity(tasks, emptyList(), 0L, 2 * 60 * 60_000L)
        assertTrue(report.overcommitted)
        assertTrue(report.overcommittedMinutes > 0)
    }

    @Test fun a_long_task_that_cannot_fit_does_not_hide_a_short_task() {
        val long = TaskEntity(id = 1, title = "Prepare order", project = "Office", priority = "urgent")
        val short = TaskEntity(id = 2, title = "Return call", project = "Office", priority = "low")
        val proposals = SmartScheduling.propose(listOf(long, short), emptyList(), 0, 25 * 60_000L)
        assertEquals(listOf(2L), proposals.map { it.task.id })
    }

    @Test fun planned_and_unresolved_tasks_are_not_scheduled_again() {
        val task = TaskEntity(id = 1, title = "Meeting", project = "Office")
        val unresolved = task.copy(id = 2, status = "needs_clarification")
        val event = EventEntity(id = 3, taskId = 1, title = "Meeting", startsAt = 60_000L, endsAt = 120_000L, confirmed = true)
        assertTrue(SmartScheduling.propose(listOf(task, unresolved), listOf(event), 0, 8 * 60 * 60_000L).isEmpty())
    }

    @Test fun proposals_do_not_run_after_an_explicit_deadline() {
        val task = TaskEntity(id = 1, title = "Submit report", project = "Office", dueAt = 20 * 60_000L)
        assertTrue(SmartScheduling.propose(listOf(task), emptyList(), 0, 8 * 60 * 60_000L).isEmpty())
    }

    @Test fun queue_age_is_not_used_as_completed_task_effort() {
        val task = TaskEntity(id = 1, title = "Submit report", project = "Office")
        val old = task.copy(id = 2, status = "completed", createdAt = 0, completedAt = 7 * 24 * 60 * 60_000L)
        assertEquals(SmartScheduling.estimateEffortMinutes(task), SmartScheduling.estimateEffortMinutes(task, listOf(old)))
    }
}
