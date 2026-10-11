package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity

data class HybridMatch(val source: SourceEntity, val score: Float, val semanticScore: Float, val keywordScore: Float, val reasons: List<String>)

/** Deterministic lexical backstop for OCR, Tamil suffixes, document numbers and Tanglish. */
object HybridRetriever {
    fun rank(query: String, sources: List<SourceEntity>, semanticHits: List<SourceSemanticHit>, limit: Int = 5): List<HybridMatch> {
        if (query.isBlank()) return emptyList()
        val semanticById = semanticHits.associateBy { it.source.id }
        return sources.mapNotNull { source ->
            val semantic = semanticById[source.id]?.score?.coerceIn(0f, 1f) ?: 0f
            val lexical = lexicalScore(query, source)
            if (semantic == 0f && lexical == 0f) return@mapNotNull null
            val reasons = buildList {
                if (semantic > 0f) add("semantic evidence")
                val exact = exactTokens(query, source.title + " " + source.body)
                if (exact.isNotEmpty()) add("keyword: ${exact.take(3).joinToString()}")
                if (containsNumber(query) && Regex("\\d+").findAll(source.body).any { it.value in query }) add("document number")
            }
            HybridMatch(source, (semantic * .72f + lexical * .28f).coerceIn(0f, 1f), semantic, lexical, reasons)
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun lexicalScore(query: String, source: SourceEntity): Float {
        val text = TamilTextNormalizer.expandTanglish("${source.title} ${source.body}")
        val q = TamilTextNormalizer.expandTanglish(query)
        if (q.isBlank()) return 0f
        var score = 0f
        if (text.contains(q)) score += .65f
        val tokens = exactTokens(q, text)
        score += (tokens.size.toFloat() / qTokens(q).size.coerceAtLeast(1) * .25f)
        val qgrams = tamilNgrams(q)
        if (qgrams.isNotEmpty()) score += (qgrams.count { it in tamilNgrams(text) }.toFloat() / qgrams.size * .10f)
        return score.coerceIn(0f, 1f)
    }

    private fun exactTokens(query: String, text: String): List<String> = qTokens(query).filter { token ->
        token.length >= 2 && (text.contains(token) || text.contains(TamilTextNormalizer.tamilStem(token)) ||
            text.split(Regex("[^\\p{L}\\p{N}]+" )).any { TamilTextNormalizer.tamilStem(it) == TamilTextNormalizer.tamilStem(token) })
    }

    private fun qTokens(value: String): List<String> = Regex("[\\p{L}\\p{N}]+").findAll(value.lowercase()).map { it.value }.toList()
    private fun tamilNgrams(value: String): Set<String> = value.filter { it.code >= 0x0B80 && it.code <= 0x0BFF }.windowed(3, 1, partialWindows = false).toSet()
    private fun containsNumber(value: String) = value.any { it.isDigit() }
}
