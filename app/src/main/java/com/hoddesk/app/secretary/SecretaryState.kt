package com.hoddesk.app.secretary

import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.workflow.SecretaryIntelligence

data class SecretaryStateSnapshot(
    val action: Int,
    val meetings: Int,
    val deadlines: Int,
    val delegations: Int,
    val waiting: Int,
    val duplicates: Int,
    val conflicts: Int,
    val sensitive: Int,
    val informationOnly: Int,
    val overdue: Int,
    val dueSoon: Int,
)

object SecretaryStateBuilder {
    fun build(
        sources: List<SourceEntity>,
        tasks: List<TaskEntity>,
        events: List<EventEntity>,
        commitments: List<CommitmentEntity>,
        now: Long = System.currentTimeMillis(),
    ): SecretaryStateSnapshot {
        val visibleSources = sources.filter { it.sensitivity != "T3" && it.sensitivity != "T4" && it.processingStatus != "deleted" }
        val activeTasks = tasks.filter { it.status != "completed" && it.status != "deleted" && it.status != "archived" }
        val openCommitments = commitments.filter { it.status == "open" }
        val end = now + 48 * 60 * 60 * 1000L
        val urgentSources = visibleSources.count { !it.reviewed && it.triageBucket == "ACT" }
        val action = urgentSources + activeTasks.count { it.assignee.isNullOrBlank() }
        val meetings = events.count { it.startsAt >= now && it.confirmed }
        val deadlines = activeTasks.count { it.dueAt != null }
        val delegations = activeTasks.count { !it.assignee.isNullOrBlank() }
        val waiting = openCommitments.count { it.direction == "OWED_TO_ME" }
        val duplicates = sources.count { it.processingStatus == "duplicate" }
        val conflicts = SecretaryIntelligence.conflicts(activeTasks).size
        val overdue = openCommitments.count { it.dueAt != null && it.dueAt < now }
        val dueSoon = openCommitments.count { it.dueAt != null && it.dueAt in now..end }
        val sensitive = sources.count { it.sensitivity == "T3" || it.sensitivity == "T4" }
        val informationOnly = visibleSources.count { it.reviewed && it.triageBucket == "FYI" }
        return SecretaryStateSnapshot(action, meetings, deadlines, delegations, waiting, duplicates, conflicts, sensitive, informationOnly, overdue, dueSoon)
    }
}
