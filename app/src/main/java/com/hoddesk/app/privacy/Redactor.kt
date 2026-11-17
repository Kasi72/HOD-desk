package com.hoddesk.app.privacy

/**
 * Identifier masking used before anything is stored. The same rules drive the import pipeline and the
 * "what will be hidden" preview, so what the HOD sees before saving is exactly what gets masked.
 * Patient identifiers are deliberately not masked here: the sensitivity classifier needs them to keep
 * clinical items out of search and Review.
 */
object Redactor {
    enum class Kind(val token: String, val singular: String, val plural: String) {
        EMAIL("[EMAIL]", "email address", "email addresses"),
        AADHAAR("[AADHAAR]", "Aadhaar number", "Aadhaar numbers"),
        PHONE("[PHONE]", "phone number", "phone numbers"),
    }

    data class Hit(val kind: Kind, val original: String, val start: Int, val end: Int)

    data class Preview(val original: String, val redacted: String, val hits: List<Hit>) {
        val hasHits: Boolean get() = hits.isNotEmpty()
        fun count(kind: Kind): Int = hits.count { it.kind == kind }

        /** "2 phone numbers and 1 email address will be hidden", or null when nothing is masked. */
        fun summary(): String? {
            val parts = Kind.values().mapNotNull { k -> count(k).takeIf { it > 0 }?.let { n -> "$n ${if (n == 1) k.singular else k.plural}" } }
            if (parts.isEmpty()) return null
            val joined = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
            return "$joined will be hidden"
        }
    }

    private const val EDGE_BEFORE = "(?<![A-Za-z0-9@._%+-])"
    private const val EDGE_AFTER = "(?![A-Za-z0-9])"

    // Order matters: e-mail first (it may contain digits), then 12-digit Aadhaar, then phone numbers.
    private val patterns = listOf(
        Kind.EMAIL to Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE),
        Kind.AADHAAR to Regex("$EDGE_BEFORE[2-9][0-9]{3}[ -]?[0-9]{4}[ -]?[0-9]{4}$EDGE_AFTER"),
        Kind.PHONE to Regex("$EDGE_BEFORE(?:(?:\\+91|0091)[ -]?|0)?(?:[0-9]{10}|[0-9]{5}[ -][0-9]{5})$EDGE_AFTER"),
    )

    fun preview(text: String): Preview {
        val hits = mutableListOf<Hit>()
        patterns.forEach { (kind, regex) ->
            regex.findAll(text).forEach { m ->
                val overlaps = hits.any { m.range.first < it.end && m.range.last + 1 > it.start }
                if (!overlaps) hits += Hit(kind, m.value, m.range.first, m.range.last + 1)
            }
        }
        hits.sortBy { it.start }
        val out = StringBuilder()
        var at = 0
        hits.forEach { h -> out.append(text, at, h.start).append(h.kind.token); at = h.end }
        out.append(text, at, text.length)
        return Preview(text, out.toString(), hits)
    }

    fun redact(text: String): String = preview(text).redacted
}
