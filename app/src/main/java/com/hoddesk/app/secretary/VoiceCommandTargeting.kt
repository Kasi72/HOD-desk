package com.hoddesk.app.secretary

import java.util.Locale

/** Mutating voice requests must identify one record; similarity is not authorization. */
object VoiceCommandTargeting {
    data class Match<T>(val record: T?, val error: String? = null, val ambiguous: Boolean = false)
    private val ignored = setOf("move", "update", "change", "reschedule", "delegate", "assign", "schedule", "the", "my", "a", "an", "task", "to", "at", "for", "tomorrow", "today", "am", "pm", "please", "on", "it", "calendar", "put", "no", "go")
    private val generic = setOf("meeting", "review", "report", "work", "document", "appointment", "event", "tasks")
    private fun normalize(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase(Locale.ROOT)).joinToString(" ") { it.value }
    private fun containsPhrase(text: String, phrase: String) = " $text ".contains(" $phrase ")

    fun <T> resolve(request: String, records: List<T>, kind: String, id: (T) -> Long, title: (T) -> String, omit: List<String> = emptyList()): Match<T> {
        val explicitId = Regex("(?i)\\b${Regex.escape(kind)}\\s*#\\s*(\\d+)\\b").find(request)?.groupValues?.get(1)?.toLongOrNull()
        if (explicitId != null) return records.singleOrNull { id(it) == explicitId }?.let { Match(it) }
            ?: Match(null, "The requested $kind no longer exists.")
        var target = request.replace(Regex("(?i)\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b"), " ")
        omit.forEach { target = target.replace(it, " ", ignoreCase = true) }
        val normalizedRequest = normalize(target)
        val exact = records.filter { candidate ->
            val value = normalize(title(candidate))
            value.length >= 4 && value !in generic && containsPhrase(normalizedRequest, value)
        }
        if (exact.size == 1) return Match(exact.single())
        if (exact.size > 1) return Match(null, "Several $kind records match. Use the exact title or $kind #ID.", true)
        val terms = normalizedRequest.split(' ').filter { it.isNotBlank() && it !in ignored }
        val explicitReference = Regex("(?i)\\bG\\.?O\\.?\\s*(?:No\\.?\\s*)?\\d+").containsMatchIn(target)
        if ((terms.size < 2 && !explicitReference) || terms.none { it !in generic })
            return Match(null, "The request is too broad. Use the exact title or $kind #ID.")
        val matching = records.filter { candidate ->
            val words = normalize(title(candidate)).split(' ').toSet()
            terms.all { it in words }
        }
        return when (matching.size) {
            1 -> Match(matching.single())
            0 -> Match(null, "No exact $kind was found; no change was made.")
            else -> Match(null, "Several $kind records match. Use the exact title or $kind #ID.", true)
        }
    }

    fun uniqueAssignee(request: String, names: List<String>): String? {
        val text = normalize(request)
        return names.filter { containsPhrase(text, normalize(it)) }.distinct().singleOrNull()
    }
}
