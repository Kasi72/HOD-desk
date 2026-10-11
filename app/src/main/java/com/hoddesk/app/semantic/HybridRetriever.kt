package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity

data class QueryPlan(val raw: String, val referenceNumbers: Set<String> = emptySet(), val dates: Set<String> = emptySet(), val times: Set<String> = emptySet(), val places: Set<String> = emptySet(), val officials: Set<String> = emptySet(), val departments: Set<String> = emptySet(), val isTamil: Boolean = false, val isTanglish: Boolean = false)

object QueryPlanner {
    private val date = Regex("(?iu)\\b(?:today|tomorrow|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|\\d{1,2}(?:st|nd|rd|th)?[ -](?:January|February|March|April|May|June|July|August|September|October|November|December)(?:[ -]\\d{4})?|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b")
    private val time = Regex("(?iu)\\b(?:at\\s*)?(?:[01]?\\d|2[0-3])(?::[0-5]\\d)?\\s*(?:AM|PM)\\b")
    private val reference = Regex("(?iu)\\b(?:G\\.?O\\.?|GO|PRGI|Ref(?:erence)?|Proceedings|Order)\\s*(?:No\\.?|number|#)?\\s*[:./-]?\\s*[A-Z0-9][A-Z0-9./-]{2,40}\\b|\\b\\d{2,}[A-Z0-9./-]*\\b")
    private val place = Regex("(?iu)\\b(?:at|in|venue|location)\\s+([A-Z][A-Za-z .'-]{2,70})")
    private val official = Regex("(?iu)\\b(?:with|from|by|to|official|officer|chair(?:ed by)?)\\s+([A-Z][A-Za-z .'-]{2,60})")
    private val departments = setOf("dme", "dean", "collector", "collectorate", "health", "medical", "psychiatry", "resident", "teaching", "academic", "secretary", "hod")
    private val tanglish = setOf("kootam", "koottam", "santhippu", "santippu", "neram", "naal", "thethi", "idam", "anuppu", "samarppikka", "kalantu", "varavendum")
    fun plan(query: String): QueryPlan {
        val normalized = TamilTextNormalizer.normalize(query)
        val tokens = Regex("[\\p{L}\\p{N}]+(?:[./-][\\p{L}\\p{N}]+)*").findAll(normalized).map { it.value }.toList()
        return QueryPlan(query, reference.findAll(query).map { it.value.trim().lowercase() }.toSet(), date.findAll(query).map { it.value.trim().lowercase() }.toSet(), time.findAll(query).map { it.value.trim().replace(Regex("(?iu)^at\\s+"), "").lowercase() }.toSet(), place.findAll(query).map { it.groupValues[1].trim().lowercase() }.toSet(), official.findAll(query).map { it.groupValues[1].trim().lowercase() }.toSet(), tokens.filter { it in departments }.toSet(), query.any { it in '\u0B80'..'\u0BFF' }, tokens.any { it in tanglish })
    }
}

data class HybridWeights(val semantic: Float = .62f, val lexical: Float = .28f, val exact: Float = .10f)
data class HybridMatch(val source: SourceEntity, val score: Float, val semanticScore: Float, val keywordScore: Float, val reasons: List<String>, val evidenceCitation: String? = null, val evidenceModality: String? = null)

/** Exact official-work matching fused with EmbeddingGemma meaning search. */
object HybridRetriever {
    fun rank(query: String, sources: List<SourceEntity>, semanticHits: List<SourceSemanticHit>, limit: Int = 5, weights: HybridWeights = HybridWeights(), allowedSourceTypes: Set<String>? = null): List<HybridMatch> {
        if (query.isBlank()) return emptyList()
        val plan = QueryPlanner.plan(query)
        val semanticById = semanticHits.associateBy { it.source.id }
        return sources.mapNotNull { source ->
            if (source.sensitivity == "T3" || source.sensitivity == "T4") return@mapNotNull null
            if (allowedSourceTypes != null && source.sourceType.lowercase() !in allowedSourceTypes.map { it.lowercase() }) return@mapNotNull null
            val semantic = semanticById[source.id]?.score?.coerceIn(0f, 1f) ?: 0f
            val lexical = lexicalScore(query, source)
            val exact = exactScore(plan, source)
            if (semantic == 0f && lexical == 0f && exact == 0f) return@mapNotNull null
            val text = normalizedSource(source)
            val exactReference = plan.referenceNumbers.any { it in text }
            val reasons = buildList {
                if (semantic > 0f) add("semantic evidence")
                val exactTokens = exactTokens(query, source.title + " " + source.body)
                if (exactTokens.isNotEmpty()) add("keyword: ${exactTokens.take(3).joinToString()}")
                if (plan.referenceNumbers.any { it in text }) add("exact reference number")
                if (plan.dates.any { it in text }) add("exact date")
                if (plan.times.any { it in text }) add("exact time")
                if (plan.officials.any { it in text }) add("named official")
                if (plan.places.any { it in text }) add("named place")
                if (plan.departments.any { it in text }) add("department cue")
                if (lexical > .08f && exactTokens.isEmpty()) add("fuzzy text")
                semanticById[source.id]?.citation?.let { add("evidence: $it") }
            }
            val fusedBase = semantic * weights.semantic + lexical * weights.lexical + exact * weights.exact
            // A verified document/reference number is a deterministic identity
            // signal and must outrank a broad semantic similarity hit.
            val fused = (if (exactReference) maxOf(fusedBase, .90f) else fusedBase).coerceIn(0f, 1f)
            HybridMatch(source, fused, semantic, lexical, reasons, semanticById[source.id]?.citation, semanticById[source.id]?.modality)
        }.sortedByDescending { it.score }.take(limit)
    }
    private fun normalizedSource(source: SourceEntity) = TamilTextNormalizer.expandTanglish("${source.title} ${source.body}").lowercase()
    private fun exactScore(plan: QueryPlan, source: SourceEntity): Float {
        val text = normalizedSource(source); var score = 0f
        if (plan.referenceNumbers.any { it in text }) score += .55f
        if (plan.dates.any { it in text }) score += .20f
        if (plan.times.any { it in text }) score += .12f
        if (plan.officials.any { it in text }) score += .10f
        if (plan.places.any { it in text }) score += .10f
        if (plan.departments.any { it in text }) score += .05f
        return score.coerceIn(0f, 1f)
    }
    private fun lexicalScore(query: String, source: SourceEntity): Float {
        val text = normalizedSource(source); val q = TamilTextNormalizer.expandTanglish(query)
        if (q.isBlank()) return 0f
        var score = 0f
        if (text.contains(q)) score += .65f
        val tokens = exactTokens(q, text); score += tokens.size.toFloat() / qTokens(q).size.coerceAtLeast(1) * .25f
        val qgrams = characterNgrams(q); if (qgrams.isNotEmpty()) score += qgrams.count { it in characterNgrams(text) }.toFloat() / qgrams.size * .10f
        return score.coerceIn(0f, 1f)
    }
    private fun exactTokens(query: String, text: String): List<String> = qTokens(query).filter { token -> token.length >= 2 && (text.contains(token) || text.contains(TamilTextNormalizer.tamilStem(token)) || text.split(Regex("[^\\p{L}\\p{N}]+" )).any { TamilTextNormalizer.tamilStem(it) == TamilTextNormalizer.tamilStem(token) }) }
    private fun qTokens(value: String): List<String> = Regex("[\\p{L}\\p{N}]+").findAll(value.lowercase()).map { it.value }.toList()
    private fun characterNgrams(value: String): Set<String> = value.filter { it.isLetterOrDigit() }.windowed(3, 1, partialWindows = false).toSet()
}
