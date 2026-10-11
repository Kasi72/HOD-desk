package com.hoddesk.app.scheduling

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.TaskEntity

data class ReplanChange(val taskId: Long, val oldDueAt: Long?, val proposedStart: Long, val proposedEnd: Long, val reason: String)
data class ReplanProposal(val changes: List<ReplanChange>, val displacedTasks: List<Long>, val conflicts: List<String>, val rationale: String)

object ReplanningEngine {
    fun propose(tasks: List<TaskEntity>, events: List<EventEntity>, protectedBlocks: List<Pair<Long, Long>>, travelMinutes: Map<Pair<String, String>, Long> = emptyMap(), now: Long = System.currentTimeMillis()): ReplanProposal {
        val busy = (events.map { it.startsAt to it.endsAt } + protectedBlocks).sortedBy { it.first }.toMutableList()
        val changes = mutableListOf<ReplanChange>()
        val conflicts = mutableListOf<String>()
        tasks.filter { it.status == "open" }.sortedWith(compareByDescending<TaskEntity> { priority(it.priority) }.thenBy { it.dueAt ?: Long.MAX_VALUE }).forEach { task ->
            val duration = 30 * 60 * 1000L
            var candidate = maxOf(now, task.dueAt ?: now)
            val venue = task.project.takeIf { it.isNotBlank() }
            val previous = events.filter { it.endsAt <= candidate && !it.location.isNullOrBlank() }.maxByOrNull { it.endsAt }
            val travel = if (venue != null && previous?.location != null) travelFor(previous.location!!, venue, travelMinutes) else 0L
            if (travel > 0L) candidate += travel
            while (busy.any { candidate < it.second && candidate + duration > it.first }) {
                candidate = busy.first { candidate < it.second && candidate + duration > it.first }.second
            }
            val end = candidate + duration
            if (task.dueAt != null && candidate != task.dueAt) {
                changes += ReplanChange(task.id, task.dueAt, candidate, end, if (travel > 0L) "travel buffer plus higher-priority block or conflict" else "higher-priority block or conflict")
            }
            busy += candidate to end
        }
        if (travelMinutes.isNotEmpty()) conflicts += "Travel buffers applied for configured venue pairs"
        return ReplanProposal(changes, changes.map { it.taskId }, conflicts, "Proposed blocks preserve protected time and priority order; confirmation is required.")
    }

    private fun priority(value: String): Int = when (value.lowercase()) {
        "urgent" -> 4
        "high" -> 3
        "medium" -> 2
        else -> 1
    }

    private fun travelFor(from: String, to: String, map: Map<Pair<String, String>, Long>): Long =
        map.entries.firstOrNull { (pair, _) -> pair.first.equals(from, true) && pair.second.equals(to, true) }?.value?.coerceAtLeast(0L) ?: 0L
}
