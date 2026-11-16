package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SourceEntity

data class TaskChangeProposal(
    val existingAction: ActionPointEntity,
    val revisedAction: ActionPointEntity,
    val source: SourceEntity,
    val changes: List<ActionChangeDetector.Change>,
    val evidence: String,
    val requiresConfirmation: Boolean = changes.isNotEmpty(),
)

object TaskChangeProposalBuilder {
    fun build(existing: ActionPointEntity, revised: ActionPointEntity, source: SourceEntity): TaskChangeProposal? {
        val changes = ActionChangeDetector.compare(existing, revised)
        if (changes.isEmpty()) return null
        return TaskChangeProposal(existing, revised, source, changes, revised.evidence)
    }
}
