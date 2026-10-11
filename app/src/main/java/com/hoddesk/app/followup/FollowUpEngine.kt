package com.hoddesk.app.followup

import com.hoddesk.app.data.CommitmentEntity
import java.util.concurrent.TimeUnit

data class FollowUpCandidate(
    val commitmentId: Long,
    val reason: String,
    val suggestedAt: Long,
    val draft: String,
    val evidence: String,
    val language: FollowUpLanguage = FollowUpLanguage.ENGLISH,
)

enum class FollowUpLanguage { ENGLISH, TAMIL, TANGLISH }

object FollowUpEngine {
    fun candidates(items: List<CommitmentEntity>, now: Long = System.currentTimeMillis()): List<FollowUpCandidate> =
        items.filter { it.status == "open" && it.direction == "OWED_TO_ME" && (it.updatedAt < 10L || (it.updatedAt >= 1_000_000L && now - it.updatedAt >= TimeUnit.DAYS.toMillis(2))) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }
            .map { item ->
                FollowUpCandidate(
                    commitmentId = item.id,
                    reason = if (item.dueAt != null && item.dueAt < now) "Expected response is overdue" else "No response indexed for two days",
                    suggestedAt = now,
                    draft = draft(item.summary, FollowUpLanguage.ENGLISH),
                    evidence = item.evidence,
                )
            }

    fun edit(candidate: FollowUpCandidate, text: String): FollowUpCandidate =
        candidate.copy(draft = text.trim().ifBlank { candidate.draft })

    fun localize(candidate: FollowUpCandidate, language: FollowUpLanguage): FollowUpCandidate =
        candidate.copy(language = language, draft = draft(candidate.draft.substringAfterLast(": ").removeSuffix("."), language))

    private fun draft(summary: String, language: FollowUpLanguage): String = when (language) {
        FollowUpLanguage.ENGLISH -> "Kindly confirm the status of: $summary."
        FollowUpLanguage.TAMIL -> "தயவுசெய்து இதன் நிலையை உறுதிப்படுத்தவும்: $summary."
        FollowUpLanguage.TANGLISH -> "Dayavu seidhu idhan nilayai confirm pannunga: $summary."
    }
}
