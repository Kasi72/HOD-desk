package com.hoddesk.app.scheduling

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.TaskEntity

data class PlannedBlock(val task: TaskEntity, val start: Long, val end: Long, val rationale: String)
data class CapacityReport(val requestedMinutes: Long, val availableMinutes: Long, val overcommittedMinutes: Long, val overcommitted: Boolean)

/** Deterministic, review-first scheduler. It proposes blocks; the caller must confirm writes. */
object SmartScheduling {
    private const val DEFAULT_TASK_MINUTES = 30L

    fun capacity(tasks: List<TaskEntity>, events: List<EventEntity>, dayStart: Long, dayEnd: Long): CapacityReport {
        require(dayEnd >= dayStart) { "Day end must be after day start" }
        val requested = tasks.count { it.status == "open" && it.assignee.isNullOrBlank() } * DEFAULT_TASK_MINUTES
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
        val open = tasks.filter { (it.status == "open" || it.status == "needs_clarification") && it.assignee.isNullOrBlank() && (it.dueAt == null || it.dueAt <= dayEnd) }
            .sortedWith(compareByDescending<TaskEntity> { priority(it.priority) }.thenBy { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.createdAt })
        val protectedStart = dayStart + 4 * 60 * 60 * 1000L
        val protectedEnd = protectedStart + 60 * 60 * 1000L
        val busy = events.map { it.startsAt to it.endsAt }.toMutableList()
        busy += protectedStart to protectedEnd
        val result = mutableListOf<PlannedBlock>()
        var cursor = dayStart
        for (task in open) {
            val duration = 30 * 60 * 1000L
            var start = nextFree(cursor, duration, busy, dayEnd)
            if (start < 0) break
            val end = start + duration
            busy += start to end
            result += PlannedBlock(task, start, end, "${task.priority.replaceFirstChar(Char::uppercase)} priority; protected focus preserved")
            cursor = end + 10 * 60 * 1000L
        }
        return result
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
