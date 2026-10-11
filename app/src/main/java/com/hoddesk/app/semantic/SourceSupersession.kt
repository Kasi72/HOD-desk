package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity

data class SourceRelationDecision(val relationType: String, val rationale: String)

/** Conservative relation policy: only explicit revision language can supersede an older source. */
object SourceSupersession {
    private val revisionLanguage = Regex("\\b(supersed|revis|amend|replace|latest|updated|corrigendum|cancelled|withdrawn)\\w*\\b", RegexOption.IGNORE_CASE)
    private val scheduleChangeLanguage = Regex("\\b(reschedul|postpon|defer|new time|changed venue|changed location|new owner|transfer)\\w*\\b", RegexOption.IGNORE_CASE)
    private val cancellationLanguage = Regex("\\b(cancel|cancelled|withdraw|withdrawn)\\w*\\b", RegexOption.IGNORE_CASE)

    fun decide(older: SourceEntity, newer: SourceEntity, similarity: Float): SourceRelationDecision? {
        // A strong shared reference/date/location signal can rescue OCR or
        // language variation where the embedding score is slightly lower.
        if (similarity < .76f) return null
        val newerText = "${newer.title} ${newer.body}"
        if (similarity >= .96f) return SourceRelationDecision("duplicate", "Very high similarity; review before retaining both imports.")
        if (newer.capturedAt > older.capturedAt && (revisionLanguage.containsMatchIn(newerText) || scheduleChangeLanguage.containsMatchIn(newerText) || cancellationLanguage.containsMatchIn(newerText))) {
            val reason = when {
                cancellationLanguage.containsMatchIn(newerText) -> "Newer source explicitly cancels or withdraws the earlier instruction."
                scheduleChangeLanguage.containsMatchIn(newerText) -> "Newer source explicitly changes timing, venue or ownership."
                else -> "Newer source contains explicit revision, replacement or withdrawal language."
            }
            return SourceRelationDecision("supersedes", reason)
        }
        return SourceRelationDecision("related", "Likely same matter across sources; no explicit supersession language found.")
    }
}
