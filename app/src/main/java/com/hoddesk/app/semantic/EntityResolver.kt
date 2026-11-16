package com.hoddesk.app.semantic

import com.hoddesk.app.semantic.TamilTextNormalizer

data class ResolvedEntity(val canonical: String, val aliases: Set<String>, val confidence: Float)

/** Deterministic alias resolution used before semantic ranking; it never stores clinical identifiers. */
object EntityResolver {
    fun normalize(value: String): String = TamilTextNormalizer.normalize(value)
        .lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun resolve(mentions: List<String>): List<ResolvedEntity> {
        val groups = mentions.filter { it.isNotBlank() }.groupBy(::normalize)
        return groups.map { (canonical, values) -> ResolvedEntity(canonical, values.toSet(), if (values.size > 1) .96f else .82f) }
    }

    fun signals(text: String): Set<String> {
        val out = mutableSetOf<String>()
        val aliases = Regex("([A-Za-z][A-Za-z ]{3,})\\s*\\(([A-Za-z]{2,8})\\)").findAll(text)
        aliases.forEach { m ->
            val full = normalize(m.groupValues[1]); val short = normalize(m.groupValues[2])
            if (full.length >= 4) { out += "e:$full"; out += "e:$short" }
        }
        Regex("\\b(?:Dr\\.?|Mr\\.?|Ms\\.?|Director|Secretary|Collector|Officer)\\s+[A-Za-z][A-Za-z .-]{2,40}").findAll(text)
            .forEach { out += "e:${normalize(it.value)}" }
        return out
    }
}
