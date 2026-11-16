package com.hoddesk.app.workflow

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import java.time.Instant
import java.time.ZoneId

/** Evidence-backed meeting preparation. It only proposes preparation material; it never sends or schedules. */
object MeetingPreparation {
    data class Pack(val event: EventEntity, val agenda: List<String>, val evidence: List<String>, val risks: List<String>)

    fun pack(event: EventEntity, tasks: List<TaskEntity>, sources: List<SourceEntity>): Pack {
        val linkedTasks = tasks.filter { it.id == event.taskId || it.project.equals(event.title, true) }
        val linkedSources = linkedTasks.mapNotNull { task -> task.sourceId?.let { id -> sources.firstOrNull { it.id == id } } }
        val agenda = linkedTasks.map { it.title }.distinct().take(5)
        val evidence = linkedSources.map { "${it.sourceType}: ${it.title}" }.distinct().take(5)
        val risks = buildList {
            if (linkedTasks.any { !it.blocker.isNullOrBlank() }) add("Blocked preparation item")
            if (linkedTasks.any { !it.clarification.isNullOrBlank() }) add("Clarification required")
            if (agenda.isEmpty()) add("No linked action points; review the original invite")
        }
        return Pack(event, agenda, evidence, risks)
    }

    fun next(events: List<EventEntity>, now: Long = System.currentTimeMillis()): EventEntity? = events.filter { it.startsAt >= now }.minByOrNull { it.startsAt }

    fun morning(events: List<EventEntity>, tasks: List<TaskEntity>): String {
        val today = events.count { isToday(it.startsAt) }
        val due = tasks.count { it.dueAt != null && isToday(it.dueAt) && it.status == "open" }
        return if (today == 0 && due == 0) "Your schedule is clear today." else "$today meeting${if (today == 1) "" else "s"} and $due due task${if (due == 1) "" else "s"} today."
    }

    fun evening(events: List<EventEntity>, tasks: List<TaskEntity>): String {
        val completed = tasks.count { it.status == "completed" && it.completedAt?.let(::isToday) == true }
        val unresolved = tasks.count { it.status == "open" && it.dueAt?.let(::isToday) == true }
        return "$completed task${if (completed == 1) "" else "s"} completed; $unresolved due item${if (unresolved == 1) "" else "s"} remain."
    }

    private fun isToday(millis: Long): Boolean = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
}
