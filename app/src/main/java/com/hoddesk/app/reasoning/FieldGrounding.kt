package com.hoddesk.app.reasoning

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
        val groundedDate = date?.takeUnless(String::isBlank)?.takeIf { value ->
            deterministicDate(value, evidence).also { if (!it) issues += "date requires confirmation: '$value' did not pass the deterministic re-check" }
        }
        val groundedTime = time?.takeUnless(String::isBlank)?.takeIf { value ->
            deterministicTime(value, evidence).also { if (!it) issues += "time requires confirmation: '$value' did not pass the deterministic re-check" }
        }
        val groundedLocation = supported("location", location)?.takeIf { value ->
            deterministicLocation(value, evidence).also { if (!it) issues += "location requires deterministic re-check: '$value'" }
        }
        conflicts += TemporalEvidence.weekdayConflicts(evidence, groundedDate)
        if (groundedDate != null && TemporalEvidence.uniqueDates(evidence).size > 1)
            conflicts += "Date conflict: multiple distinct dates in the cited evidence require confirmation"
        if (groundedTime != null && TemporalEvidence.hasTimeConflict(evidence))
            conflicts += "Time conflict: multiple clock values in the cited evidence require confirmation"
        conflicts += ambiguities.filter { it.contains("conflict", ignoreCase = true) }
        issues += conflicts
        val finalDate = if (conflicts.any { it.startsWith("Weekday/date conflict", true) || it.startsWith("Date conflict", true) }) null else groundedDate
        val finalTime = if (conflicts.any { it.startsWith("Time conflict", true) }) null else groundedTime
        return Result(finalDate, finalTime, groundedLocation, groundedPeople, issues.distinct(), issues.isEmpty(), conflicts.distinct())
    }

    /** Prevents a model from attaching a plausible-looking action to an unrelated quote. */
    fun supportsAction(evidence: String, action: String): Boolean {
        val expected = tokens(normalize(action)).filter { it.length >= 2 && it !in actionStopWords }
        if (expected.isEmpty()) return false
        val actual = tokens(normalize(evidence)).toSet()
        val requestedVerbs = actionVerbs.findAll(action).map { it.value.lowercase(Locale.ROOT) }.toSet()
        val evidenceVerbs = actionVerbs.findAll(evidence).map { it.value.lowercase(Locale.ROOT) }.toSet()
        // Matching “DME meeting” cannot support “cancel the DME meeting” or
        // turn an informational meeting announcement into attendance.
        if (requestedVerbs.isEmpty() || !evidenceVerbs.containsAll(requestedVerbs)) return false
        val overlap = expected.count { it in actual }
        return overlap >= kotlin.math.ceil(expected.size * .6).toInt()
    }

    fun supportsField(evidence: String, value: String?): Boolean = !value.isNullOrBlank() && containsPhrase(evidence, value)

    private fun containsPhrase(evidence: String, value: String): Boolean {
        val normalizedEvidence = normalize(evidence)
        val normalizedValue = normalize(value)
        if (normalizedValue.isBlank()) return false
        if (Regex("(?<![\\p{L}\\p{N}])${Regex.escape(normalizedValue)}(?![\\p{L}\\p{N}])").containsMatchIn(normalizedEvidence)) return true
        // Permit punctuation differences such as "2 p.m." versus "2 PM"
        // while preserving phrase order and adjacency.
        val evidenceTokens = tokens(normalizedEvidence)
        val valueTokens = tokens(normalizedValue)
        return valueTokens.isNotEmpty() && evidenceTokens.windowed(valueTokens.size, 1, partialWindows = false)
            .any { it == valueTokens }
    }

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace(Regex("[\\u00A0\\t\\r\\n]+"), " ")
        .replace(Regex("[,;:()\\[\\]{}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokens(text: String): List<String> = Regex("[\\p{L}\\p{N}]+|[.!?]")
        .findAll(text)
        .map { it.value }
        .toList()

    private val actionStopWords = setOf("the", "a", "an", "to", "of", "for", "with", "on", "in", "at", "by", "and", "is", "are", "be", "please", "kindly", "listed", "scheduled", "following")
    private val actionVerbs = Regex("(?i)\\b(?:prepare|submit|send|share|review|approve|forward|attend|join|conduct|organize|organise|complete|verify|constitute|nominate|depute|report|schedule|meet|discuss|ensure|arrange|collect|participate|present|circulate|issue|obtain|coordinate|inform|notify|compile|file|sign|visit|instruct|direct|delegate|follow[- ]?up|cancel|postpone|reschedule|move|delete|create|update)\\b|செய்ய|செல்ல|கலந்து|தயார்|அனுப்ப|சமர்ப்பிக்க|பங்கேற்க|தெரிவிக்க|அறிக்கை")
    private val locationPattern = Regex("(?:\\b(?i:at|in|venue|location)\\b|@|இல்|இடம்)\\s*:?[ ]*([\\p{L}\\p{N}][\\p{L}\\p{N} .'-]{1,80})")

    private fun deterministicDate(value: String, evidence: String): Boolean {
        val parsed = TemporalEvidence.parseDate(value) ?: return false
        return TemporalEvidence.dates(evidence).any { it.value == parsed }
    }
    private fun deterministicTime(value: String, evidence: String): Boolean {
        val parsed = TemporalEvidence.parseTime(value) ?: return false
        return TemporalEvidence.times(evidence).any { it.start == parsed.start && it.end == parsed.end }
    }
    private fun deterministicLocation(value: String, evidence: String): Boolean {
        // Virtual venues are locations too, but do not have an "at/in" prefix.
        // Accept only the explicit platform phrase present in the cited quote.
        val online = Regex("(?i)\\b(Google Meet|Zoom|Microsoft Teams|video call|online meeting)\\b")
        if (online.findAll(evidence).any { containsPhrase(it.groupValues[1], value) }) return true
        return locationPattern.findAll(evidence).any {
            val phrase = it.groupValues[1].trim().trimEnd('.', ',', ';')
            val looksLikeVenue = value.firstOrNull()?.isUpperCase() == true ||
                Regex("(?i)\\b(?:room|hall|office|college|hospital|collectorate|campus|centre|center|auditorium)\\b|அறை|மருத்துவமனை").containsMatchIn(value) ||
                it.value.startsWith("venue", true) || it.value.startsWith("location", true)
            looksLikeVenue && containsPhrase(phrase, value)
        }
    }
}
