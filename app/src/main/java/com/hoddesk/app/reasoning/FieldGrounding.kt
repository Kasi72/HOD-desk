package com.hoddesk.app.reasoning

/** Field-level evidence gate. A citation can support an action only when its structured fields are also present. */
object FieldGrounding {
    data class Result(
        val date: String?, val time: String?, val location: String?, val people: List<String>,
        val ambiguities: List<String>, val grounded: Boolean
    )

    fun validate(evidence: String, date: String?, time: String?, location: String?, people: List<String>, ambiguities: List<String>): Result {
        val issues = ambiguities.toMutableList()
        fun supported(label: String, value: String?): String? {
            if (value.isNullOrBlank()) return null
            // Require the complete field phrase in the cited evidence. Token
            // subset matching could combine unrelated dates, places or people.
            if (!containsPhrase(evidence, value)) {
                issues += "$label requires confirmation: '$value' is not present in the cited evidence"
                return null
            }
            return value
        }
        val groundedPeople = people.mapNotNull { supported("person", it) }
        val groundedDate = supported("date", date)
        val groundedTime = supported("time", time)
        val groundedLocation = supported("location", location)
        return Result(groundedDate, groundedTime, groundedLocation, groundedPeople, issues, issues.isEmpty())
    }

    private fun containsPhrase(evidence: String, value: String): Boolean {
        val normalizedEvidence = normalize(evidence)
        val normalizedValue = normalize(value)
        if (normalizedValue.isBlank()) return false
        if (normalizedEvidence.contains(normalizedValue)) return true
        // Permit punctuation differences such as "2 p.m." versus "2 PM"
        // while preserving phrase order and adjacency.
        val evidenceTokens = tokens(normalizedEvidence)
        val valueTokens = tokens(normalizedValue)
        return valueTokens.isNotEmpty() && evidenceTokens.windowed(valueTokens.size, 1, partialWindows = false)
            .any { it == valueTokens }
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[\\u00A0\\t\\r\\n]+"), " ")
        .replace(Regex("[,;:()\\[\\]{}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokens(text: String): List<String> = Regex("[\\p{L}\\p{N}]+|[.!?]")
        .findAll(text)
        .map { it.value }
        .toList()
}