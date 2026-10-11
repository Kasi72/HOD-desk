package com.hoddesk.app.reasoning

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Field-level evidence gate. A citation can support an action only when its structured fields are also present. */
object FieldGrounding {
    data class Result(
        val date: String?, val time: String?, val location: String?, val people: List<String>,
        val ambiguities: List<String>, val grounded: Boolean, val conflicts: List<String> = emptyList()
    )

    fun validate(evidence: String, date: String?, time: String?, location: String?, people: List<String>, ambiguities: List<String>): Result {
        val issues = ambiguities.toMutableList()
        val conflicts = mutableListOf<String>()
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
        val groundedDate = supported("date", date)?.takeIf { value ->
            deterministicDate(value, evidence).also { if (!it) issues += "date requires deterministic re-check: '$value'" }
        }
        val groundedTime = supported("time", time)?.takeIf { value ->
            deterministicTime(value, evidence).also { if (!it) issues += "time requires deterministic re-check: '$value'" }
        }
        val groundedLocation = supported("location", location)?.takeIf { value ->
            deterministicLocation(value, evidence).also { if (!it) issues += "location requires deterministic re-check: '$value'" }
        }
        val weekday = Regex("\\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b", RegexOption.IGNORE_CASE).find(evidence)?.groupValues?.get(1)
        val parsedDate = groundedDate?.let(::parseDate)
        if (weekday != null && parsedDate != null && weekday.lowercase() != parsedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }) {
            val message = "Weekday/date conflict: $weekday does not match $groundedDate"
            issues += message
            conflicts += message
        }
        val finalDate = if (conflicts.any { it.startsWith("Weekday/date conflict") }) null else groundedDate
        return Result(finalDate, groundedTime, groundedLocation, groundedPeople, issues.distinct(), issues.isEmpty(), conflicts.distinct())
    }

    /** Prevents a model from attaching a plausible-looking action to an unrelated quote. */
    fun supportsAction(evidence: String, action: String): Boolean {
        val expected = tokens(normalize(action)).filter { it.length >= 2 }
        if (expected.isEmpty()) return false
        val actual = tokens(normalize(evidence)).toSet()
        val overlap = expected.count { it in actual }
        return overlap >= if (expected.size <= 2) expected.size else 2
    }

    fun supportsField(evidence: String, value: String?): Boolean = !value.isNullOrBlank() && containsPhrase(evidence, value)

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

    private val timePattern = Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(?:a\\.?m\\.?|p\\.?m\\.?)\\b", RegexOption.IGNORE_CASE)
    private val datePattern = Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}\\b|\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b", RegexOption.IGNORE_CASE)
    private val locationPattern = Regex("(?:\\bat\\b|\\bin\\b|\\bvenue\\b|\\blocation\\b|@|இல்|இடம்)\\s+([\\p{L}\\p{N}][\\p{L}\\p{N} .'-]{1,80})", RegexOption.IGNORE_CASE)

    private fun deterministicDate(value: String, evidence: String): Boolean = datePattern.findAll(evidence).any { containsPhrase(it.value, value) } && parseDate(value) != null
    private fun deterministicTime(value: String, evidence: String): Boolean = timePattern.findAll(evidence).any { containsPhrase(it.value, value) }
    private fun deterministicLocation(value: String, evidence: String): Boolean = locationPattern.findAll(evidence).any { containsPhrase(it.groupValues[1].trim().trimEnd('.', ',', ';'), value) }
    private fun parseDate(value: String): LocalDate? {
        val clean = value.lowercase(Locale.ROOT).replace(Regex("(st|nd|rd|th)\\b"), "").trim()
        val month = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
        return runCatching { LocalDate.parse(clean, month) }.getOrElse {
            val parts = Regex("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?").find(clean) ?: return null
            val year = parts.groupValues[3].ifBlank { "2026" }.let { if (it.length == 2) "20$it" else it }.toInt()
            runCatching { LocalDate.of(year, parts.groupValues[2].toInt(), parts.groupValues[1].toInt()) }.getOrNull()
        }
    }
}
