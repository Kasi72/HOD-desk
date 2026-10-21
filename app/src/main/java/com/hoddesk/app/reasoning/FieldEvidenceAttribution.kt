package com.hoddesk.app.reasoning

data class FieldEvidence(
    val confidence: Float,
    val quote: String?,
    val state: String,
)

/** Produces explainable per-field evidence without allowing document-wide leakage. */
object FieldEvidenceAttribution {
    private val fields = listOf("action", "date", "time", "location", "people", "owner", "reference")

    fun assess(action: String, date: String?, time: String?, location: String?, people: List<String>, owner: String?, reference: String?, evidence: String): Map<String, FieldEvidence> {
        val grounded = FieldGrounding.validate(evidence, date, time, location, people, emptyList())
        fun quote(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let { needle ->
            evidence.split(Regex("(?<=[.!?。！？])\\s+|\\n+")).firstOrNull { sentence ->
                FieldGrounding.supportsField(sentence, needle) || (needle == action && FieldGrounding.supportsAction(sentence, action))
            }?.trim()?.takeIf { it.isNotBlank() }
        }
        fun field(value: String?, valid: Boolean, actionField: Boolean = false): FieldEvidence {
            if (value.isNullOrBlank()) return FieldEvidence(0f, null, "missing")
            val q = quote(value)
            val supported = valid && q != null
            return FieldEvidence(if (supported) .86f else .28f, q, if (supported) "verified" else "ambiguous")
        }
        val result = linkedMapOf<String, FieldEvidence>()
        result["action"] = field(action, action.isNotBlank() && FieldGrounding.supportsAction(evidence, action), true)
        result["date"] = field(date, grounded.date != null)
        result["time"] = field(time, grounded.time != null)
        result["location"] = field(location, grounded.location != null)
        result["people"] = field(people.joinToString("; "), people.isNotEmpty() && grounded.people.size == people.size)
        result["owner"] = field(owner, owner != null && FieldGrounding.supportsField(evidence, owner))
        result["reference"] = field(reference, reference != null && FieldGrounding.supportsField(evidence, reference))
        return result
    }

    // Keep these small metadata objects independent of Android's org.json implementation.
    // The latter is deliberately unavailable in local JVM tests and can throw "not mocked".
    fun confidenceJson(values: Map<String, FieldEvidence>): String = values.entries.joinToString(",", "{", "}") { (key, value) ->
        "\"${escape(key)}\":${value.confidence.coerceIn(0f, 1f)}"
    }

    fun citationsJson(values: Map<String, FieldEvidence>): String = values.entries
        .filter { !it.value.quote.isNullOrBlank() }
        .joinToString(",", "{", "}") { (key, value) -> "\"${escape(key)}\":\"${escape(value.quote!!)}\"" }

    fun stringMapJson(values: Map<String, String>): String = values.entries
        .joinToString(",", "{", "}") { (key, value) -> "\"${escape(key)}\":\"${escape(value)}\"" }

    fun readConfidences(json: String?): Map<String, Float> = fields.associateWith { key ->
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            .find(json.orEmpty())?.groupValues?.getOrNull(1)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
    }

    fun readCitations(json: String?): Map<String, String> = fields.mapNotNull { key ->
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
            .find(json.orEmpty())?.groupValues?.getOrNull(1)?.let { key to unescape(it) }
    }.toMap()

    private fun escape(value: String): String = buildString(value.length + 8) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = value.replace("\\\\", "\\")
        .replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
}
