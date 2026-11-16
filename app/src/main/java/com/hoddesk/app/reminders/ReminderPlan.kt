package com.hoddesk.app.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Pure scheduling rules for local reminders, kept free of Android types so they can be unit-tested. */
object ReminderPlan {
    /** At most this many task alarms are kept at once (Android caps alarms per app). */
    const val MAX_TASK_ALARMS = 50
    /** Task reminders are only set for deadlines inside this window; the rest are set on a later reschedule. */
    const val HORIZON_MS = 14L * 24 * 60 * 60 * 1000

    data class TaskDue(val taskId: Long, val dueAt: Long)
    data class TaskAlarm(val taskId: Long, val dueAt: Long, val fireAt: Long)

    /** The next moment (strictly after [now]) that the daily brief should fire. */
    fun nextBrief(now: Long, minuteOfDay: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate() // API 26-safe (no LocalDate.ofInstant)
        val candidate = today.atTime(time).atZone(zone).toInstant().toEpochMilli()
        return if (candidate > now) candidate else today.plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * One alarm per open task, [leadMinutes] before it is due. A task already inside the lead window
     * but not yet due is reminded right away; overdue tasks are left to the morning brief.
     */
    fun taskAlarms(tasks: List<TaskDue>, now: Long, leadMinutes: Int): List<TaskAlarm> =
        tasks.asSequence()
            .filter { it.dueAt > now && it.dueAt <= now + HORIZON_MS }
            .map { TaskAlarm(it.taskId, it.dueAt, maxOf(now + 1_000, it.dueAt - leadMinutes * 60_000L)) }
            .sortedBy { it.fireAt }
            .take(MAX_TASK_ALARMS)
            .toList()
}
