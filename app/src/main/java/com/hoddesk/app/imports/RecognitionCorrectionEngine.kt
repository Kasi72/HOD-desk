package com.hoddesk.app.imports

data class RecognitionCandidate(val text: String, val confidence: Float, val reason: String)

/** Produces reviewable alternatives from the cited OCR/transcript; it never invents text. */
object RecognitionCorrectionEngine {
    fun candidates(raw: String, evidence: String, limit: Int = 3): List<RecognitionCandidate> {
        val source = evidence.lines().map(String::trim).filter { it.length >= 12 }
            .filter { Regex("(?i)\\b(send|submit|attend|follow|share|instruct|join|வரவும்|கலந்து|அனுப்ப)").containsMatchIn(it) }
        return (listOf(raw.trim()) + source).filter { it.isNotBlank() }.distinct().take(limit)
            .mapIndexed { i, text -> RecognitionCandidate(text, if (i == 0) .72f else .88f, if (i == 0) "Current extraction" else "Cited source sentence") }
    }
}
