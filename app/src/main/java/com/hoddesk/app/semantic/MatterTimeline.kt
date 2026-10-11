package com.hoddesk.app.semantic

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity

data class MatterTimeline(
    val matterId: Long,
    val sources: List<SourceEntity>,
    val tasks: List<TaskEntity>,
    val events: List<EventEntity>,
    val latestInstruction: SourceEntity?,
    val unresolvedQuestions: List<String>,
    val nextAction: TaskEntity?,
)

object MatterTimelineBuilder {
    fun build(matter: MatterGrouper.Matter, tasks: List<TaskEntity>, events: List<EventEntity>): MatterTimeline {
        val ids = matter.sources.map { it.id }.toSet()
        val relatedTasks = tasks.filter { it.sourceId in ids }.sortedByDescending { it.createdAt }
        val taskIds = relatedTasks.map { it.id }.toSet()
        val relatedEvents = events.filter { it.taskId in taskIds }.sortedBy { it.startsAt }
        val questions = relatedTasks.mapNotNull { it.clarification?.takeIf(String::isNotBlank) } +
            relatedTasks.mapNotNull { it.blocker?.takeIf(String::isNotBlank)?.let { value -> "Blocked: $value" } }
        val superseded = matter.supersededSourceIds
        val orderedSources = matter.sources.sortedWith(
            compareByDescending<SourceEntity> { it.id !in superseded }
                .thenByDescending { it.capturedAt }
        )
        return MatterTimeline(
            matterId = matter.id,
            sources = orderedSources,
            tasks = relatedTasks,
            events = relatedEvents,
            latestInstruction = orderedSources.firstOrNull() ?: matter.latest,
            unresolvedQuestions = questions.distinct(),
            nextAction = relatedTasks.firstOrNull { it.status == "open" || it.status == "needs_clarification" },
        )
    }
}
