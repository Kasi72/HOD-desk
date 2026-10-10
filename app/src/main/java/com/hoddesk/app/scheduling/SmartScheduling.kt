package com.hoddesk.app.scheduling

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.TaskEntity

data class PlannedBlock(val task: TaskEntity, val start: Long, val end: Long, val rationale: String)

/** Deterministic, review-first scheduler. It proposes blocks; the caller must confirm writes. */
object SmartScheduling {
    fun propose(tasks: List<TaskEntity>, events: List<EventEntity>, dayStart: Long, dayEnd: Long): List<PlannedBlock> {
        val open = tasks.filter { it.status == "open" && it.dueAt == null || it.status == "open" && (it.dueAt ?: Long.MAX_VALUE) <= dayEnd }
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
