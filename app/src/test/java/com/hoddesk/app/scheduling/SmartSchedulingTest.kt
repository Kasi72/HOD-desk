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
        assertEquals(60, report.availableMinutes)
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
}
