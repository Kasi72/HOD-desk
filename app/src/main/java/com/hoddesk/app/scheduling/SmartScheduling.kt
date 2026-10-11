package com.hoddesk.app.scheduling

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.TaskEntity

data class PlannedBlock(val task: TaskEntity, val start: Long, val end: Long, val rationale: String, val effortMinutes: Long = 30L, val travelMinutes: Long = 0L)
data class CapacityReport(val requestedMinutes: Long, val availableMinutes: Long, val overcommittedMinutes: Long, val overcommitted: Boolean)

/** Deterministic, review-first scheduler. It proposes blocks; the caller must confirm writes. */
object SmartScheduling {
    private const val DEFAULT_TASK_MINUTES = 30L

    fun capacity(tasks: List<TaskEntity>, events: List<EventEntity>, dayStart: Long, dayEnd: Long, travelMinutesByVenue: Map<String, Long> = emptyMap(), completedHistory: List<TaskEntity> = emptyList()): CapacityReport {
        require(dayEnd >= dayStart) { "Day end must be after day start" }
        val requested = tasks.filter { it.status == "open" && it.assignee.isNullOrBlank() }.sumOf { estimateEffortMinutes(it, completedHistory) + (travelMinutesByVenue.entries.firstOrNull { entry -> entry.key.equals(it.project, true) }?.value?.coerceAtLeast(0L) ?: 0L) }
        val busy = mergeBusy(events.map { it.startsAt.coerceAtLeast(dayStart) to it.endsAt.coerceAtMost(dayEnd) }.filter { it.second > it.first })
        val total = (dayEnd - dayStart) / 60_000L
        val occupied = busy.sumOf { (it.second - it.first) / 60_000L }
        val rawAvailable = (total - occupied).coerceAtLeast(0L)
        // Report the portion of free capacity relevant to this queue; spare time
        // beyond the current request is not presented as schedulable demand.
        val available = if (requested == 0L) rawAvailable else minOf(rawAvailable, requested)
        return CapacityReport(requested, available, (requested - rawAvailable).coerceAtLeast(0L), requested > rawAvailable)
    }

    fun propose(tasks: List<TaskEntity>, events: List<EventEntity>, dayStart: Long, dayEnd: Long): List<PlannedBlock> {
        return propose(tasks, events, dayStart, dayEnd, emptyMap())
    }

    /** Proposes work blocks while treating fixed blocks as immutable busy time and
     * reserving configured venue travel before each off-site task. */
    fun propose(tasks: List<TaskEntity>, events: List<EventEntity>, dayStart: Long, dayEnd: Long, travelMinutesByVenue: Map<String, Long>, completedHistory: List<TaskEntity> = emptyList()): List<PlannedBlock> {
        val open = tasks.filter { (it.status == "open" || it.status == "needs_clarification") && it.assignee.isNullOrBlank() && (it.dueAt == null || it.dueAt <= dayEnd) }
            .sortedWith(compareByDescending<TaskEntity> { priority(it.priority) }.thenBy { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.createdAt })
        val protectedStart = dayStart + 4 * 60 * 60 * 1000L
        val protectedEnd = protectedStart + 60 * 60 * 1000L
        val busy = events.map { it.startsAt to it.endsAt }.toMutableList()
        busy += protectedStart to protectedEnd
        val result = mutableListOf<PlannedBlock>()
        var cursor = dayStart
        for (task in open) {
            val effortMinutes = estimateEffortMinutes(task, completedHistory)
            val duration = effortMinutes * 60 * 1000L
            val travelMinutes = travelMinutesByVenue.entries.firstOrNull { it.key.equals(task.project, true) }?.value?.coerceAtLeast(0L) ?: 0L
            val travel = travelMinutes * 60 * 1000L
            var start = nextFree(cursor, duration + travel, busy, dayEnd)
            if (start < 0) break
            val taskStart = start + travel
            val end = taskStart + duration
            if (travel > 0L) busy += start to taskStart
            busy += start to end
            result += PlannedBlock(task, taskStart, end, "${task.priority.replaceFirstChar(Char::uppercase)} priority; ${effortMinutes} min effort; ${travelMinutes} min travel buffer; protected focus preserved", effortMinutes, travelMinutes)
            cursor = end + 10 * 60 * 1000L
        }
        return result
    }

    fun estimateEffortMinutes(task: TaskEntity, completedHistory: List<TaskEntity> = emptyList()): Long {
        val type = taskType(task)
        val matching = completedHistory.filter { it.status == "completed" && taskType(it) == type }
        val observed = matching.mapNotNull { done ->
            done.completedAt?.let { completed -> (completed - done.createdAt).coerceIn(15 * 60_000L, 4 * 60 * 60_000L) / 60_000L }
        }
        if (observed.isNotEmpty()) return observed.average().toLong().coerceIn(15L, 180L)
        return when (task.priority.lowercase()) { "urgent" -> 60L; "high" -> 45L; "low" -> 20L; else -> DEFAULT_TASK_MINUTES }
    }

    /** Stable task categories let completed work teach estimates across projects. */
    fun taskType(task: TaskEntity): String {
        val text = "${task.title} ${task.project}".lowercase()
        return when {
            text.contains("meeting") || text.contains("review") || text.contains("conference") -> "meeting"
            text.contains("report") || text.contains("order") || text.contains("submit") -> "documentation"
            text.contains("call") || text.contains("follow") -> "follow_up"
            text.contains("teach") || text.contains("lecture") -> "teaching"
            else -> "administration"
        }
    }

    private fun mergeBusy(blocks: List<Pair<Long, Long>>): List<Pair<Long, Long>> = blocks.sortedBy { it.first }.fold(mutableListOf()) { merged, block ->
        val previous = merged.lastOrNull()
        if (previous != null && block.first <= previous.second) {
            merged[merged.lastIndex] = previous.first to maxOf(previous.second, block.second)
        } else merged += block
        merged
    }

    private fun nextFree(from: Long, duration: Long, busy: List<Pair<Long, Long>>, limit: Long): Long {
        var candidate = from
        while (candidate + duration <= limit) {
            val clash = busy.firstOrNull { candidate < it.second && candidate + duration > it.first }
            if (clash == null) return candidate
            candidate = clash.second
        }
        return -1L
    }

    private fun priority(value: String): Int = when (value.lowercase()) {
        "urgent" -> 4
        "high" -> 3
        "medium" -> 2
        else -> 1
    }
}
