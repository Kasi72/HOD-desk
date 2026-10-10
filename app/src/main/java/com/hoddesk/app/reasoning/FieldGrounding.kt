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
            val tokens = tokens(value)
            val evidenceTokens = tokens(evidence)
            val meaningful = tokens.filter { it.length >= 2 }
            if (meaningful.isEmpty() || meaningful.none { it in evidenceTokens }) {
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

    private fun tokens(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase())
        .map { it.value }.toSet()
}
