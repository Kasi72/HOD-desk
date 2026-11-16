package com.hoddesk.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlanTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun briefLaterTodayFiresToday() {
        assertEquals(at(2026, 11, 16, 8, 0), ReminderPlan.nextBrief(at(2026, 11, 16, 6, 30), 8 * 60, zone))
    }

    @Test fun briefAlreadyPassedFiresTomorrow() {
        assertEquals(at(2026, 11, 17, 8, 0), ReminderPlan.nextBrief(at(2026, 11, 16, 8, 0), 8 * 60, zone))
        assertEquals(at(2026, 11, 17, 8, 0), ReminderPlan.nextBrief(at(2026, 11, 16, 21, 0), 8 * 60, zone))
    }

    @Test fun taskReminderFiresLeadMinutesBefore() {
        val now = at(2026, 11, 16, 9, 0)
        val alarms = ReminderPlan.taskAlarms(listOf(ReminderPlan.TaskDue(7, at(2026, 11, 16, 17, 0))), now, 60)
        assertEquals(at(2026, 11, 16, 16, 0), alarms.single().fireAt)
    }

    @Test fun taskInsideLeadWindowFiresNowAndOverdueIsSkipped() {
        val now = at(2026, 11, 16, 16, 30)
        val alarms = ReminderPlan.taskAlarms(listOf(
            ReminderPlan.TaskDue(1, at(2026, 11, 16, 17, 0)),
            ReminderPlan.TaskDue(2, at(2026, 11, 16, 15, 0)),
        ), now, 60)
        assertEquals(listOf(1L), alarms.map { it.taskId })
        assertTrue(alarms.single().fireAt in now..now + 2_000)
    }

    @Test fun farFutureTasksAreNotScheduledAndCountIsCapped() {
        val now = at(2026, 11, 16, 9, 0)
        val tasks = (1L..80L).map { ReminderPlan.TaskDue(it, now + it * 3_600_000L) } + ReminderPlan.TaskDue(999, now + 30L * 24 * 3_600_000L)
        val alarms = ReminderPlan.taskAlarms(tasks, now, 60)
        assertEquals(ReminderPlan.MAX_TASK_ALARMS, alarms.size)
        assertTrue(alarms.none { it.taskId == 999L })
    }
}
