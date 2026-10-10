package com.hoddesk.app.repository

/** Merges repeated candidates from cross-posted WhatsApp, documents and call transcripts. */
object ActionConsolidator {
    fun merge(actions: List<ActionPointDraft>): List<ActionPointDraft> = actions
        .groupBy { key(it) }
        .values
        .map { group ->
            val best = group.maxBy { it.confidence }
            val evidence = group.map { it.evidence.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
            val ambiguities = group.mapNotNull { it.ambiguity?.trim()?.ifBlank { null } }.distinct().joinToString("; ").ifBlank { null }
            best.copy(evidence = evidence, ambiguity = ambiguities, confidence = group.maxOf { it.confidence })
        }

    private fun key(action: ActionPointDraft): String = listOf(action.action, action.dateText, action.timeText, action.locationText, action.peopleText, action.category)
        .joinToString("|") { it.orEmpty().lowercase().replace(Regex("\\s+"), " ").trim() }
}
