package com.hoddesk.app.followup

import com.hoddesk.app.data.CommitmentEntity
import java.util.concurrent.TimeUnit

data class FollowUpCandidate(
    val commitmentId: Long,
    val reason: String,
    val suggestedAt: Long,
    val draft: String,
    val evidence: String,
)

object FollowUpEngine {
    fun candidates(items: List<CommitmentEntity>, now: Long = System.currentTimeMillis()): List<FollowUpCandidate> =
        items.filter { it.status == "open" && it.direction == "OWED_TO_ME" && now - it.updatedAt >= TimeUnit.DAYS.toMillis(2) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }
            .map { item ->
                FollowUpCandidate(
                    commitmentId = item.id,
                    reason = if (item.dueAt != null && item.dueAt < now) "Expected response is overdue" else "No response indexed for two days",
                    suggestedAt = now,
                    draft = "Kindly confirm the status of: " + item.summary + ".",
                    evidence = item.evidence,
                )
            }

    fun edit(candidate: FollowUpCandidate, text: String): FollowUpCandidate =
        candidate.copy(draft = text.trim().ifBlank { candidate.draft })
}
