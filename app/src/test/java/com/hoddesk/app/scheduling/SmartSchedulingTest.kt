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
}
