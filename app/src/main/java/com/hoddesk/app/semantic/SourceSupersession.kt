package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity

data class SourceRelationDecision(val relationType: String, val rationale: String)

/** Conservative relation policy: only explicit revision language can supersede an older source. */
object SourceSupersession {
    private val revisionLanguage = Regex("\\b(supersed|revis|amend|replace|latest|updated|corrigendum|cancelled|withdrawn)\\w*\\b", RegexOption.IGNORE_CASE)

    fun decide(older: SourceEntity, newer: SourceEntity, similarity: Float): SourceRelationDecision? {
        if (similarity < .82f) return null
        val newerText = "${newer.title} ${newer.body}"
        if (similarity >= .96f) return SourceRelationDecision("duplicate", "Very high similarity; review before retaining both imports.")
        if (newer.capturedAt > older.capturedAt && revisionLanguage.containsMatchIn(newerText)) {
            return SourceRelationDecision("supersedes", "Newer source contains explicit revision, replacement or withdrawal language.")
        }
        return SourceRelationDecision("related", "Likely same matter across sources; no explicit supersession language found.")
    }
}
