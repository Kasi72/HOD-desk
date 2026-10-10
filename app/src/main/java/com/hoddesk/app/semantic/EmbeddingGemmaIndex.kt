package com.hoddesk.app.semantic

import android.content.Context
import org.json.JSONObject
import kotlin.math.sqrt

data class SemanticDocument(val id: Long, val text: String, val citation: String)
data class SemanticHit(val document: SemanticDocument, val score: Float, val confidence: Float)

data class EmbeddingGemmaConfig(
    val dimension: Int,
    val maxTokens: Int,
    val queryPrefix: String,
    val documentPrefix: String,
    val supportedDimensions: Set<Int>
)

object EmbeddingGemmaConfigLoader {
    fun load(context: Context, asset: String = "embeddinggemma2.manifest.json"): EmbeddingGemmaConfig {
        val json = context.assets.open(asset).bufferedReader().use { JSONObject(it.readText()) }
        return EmbeddingGemmaConfig(
            dimension = json.getInt("native_output_dimension"),
            maxTokens = json.getInt("max_tokens"),
            queryPrefix = json.getString("text_query_prefix"),
            documentPrefix = json.getString("document_prefix"),
            supportedDimensions = json.getJSONArray("supported_truncation_dimensions").let { array -> (0 until array.length()).map { array.getInt(it) }.toSet() }
        )
    }
}

interface EmbeddingGemmaService {
    suspend fun embed(text: String): FloatArray
}

/** Uses EmbeddingGemma to rank extracted candidates by official-work relevance. */
class EmbeddingGemmaActionRanker(private val embedder: EmbeddingGemmaService) {
    suspend fun rank(candidates: List<String>): List<Pair<String, Float>> {
        if (candidates.isEmpty()) return emptyList()
        val intents = listOf(
            "official obligation required action submit report complete",
            "meeting appointment conference attend join schedule",
            "deadline due date submit before within",
            "teaching lecture class resident training duty roster",
            "follow up pending reminder obtain confirmation"
        ).map { embedder.embed(EmbeddingPromptRegistry.classify(it)) }
        return candidates.map { candidate ->
            val vector = embedder.embed(EmbeddingPromptRegistry.classify(candidate))
            val semantic = intents.maxOf { cosine(it, vector) }
            val explicit = listOf("\b(by|before|due|deadline)\b", "\b(at|on|meeting|appointment|zoom|teams)\b", "\b(submit|send|attend|review|approve|follow up|arrange)\b").count { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(candidate) }
            candidate to (semantic + explicit * .025f).coerceIn(-1f, 1f)
        }.sortedByDescending { it.second }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding dimensions differ" }
        var dot = 0f; var an = 0f; var bn = 0f
        a.indices.forEach { i -> dot += a[i] * b[i]; an += a[i] * a[i]; bn += b[i] * b[i] }
        return if (an == 0f || bn == 0f) 0f else (dot / (sqrt(an) * sqrt(bn))).coerceIn(-1f, 1f)
    }
}

/** Loads the quantized on-device EmbeddingGemma asset when it is packaged by the deployment build. */
class TfliteEmbeddingGemmaService(context: Context, modelAsset: String = "embeddinggemma2.tflite") : EmbeddingGemmaService {
    private val modelBytes: ByteArray = context.assets.open(modelAsset).use { it.readBytes() }
    override suspend fun embed(text: String): FloatArray {
        require(text.isNotBlank()) { "Cannot embed blank text" }
        // The model-specific tokenizer/input-output contract is injected at packaging time.
        // This guard prevents silently returning a misleading vector until that contract is configured.
        error("EmbeddingGemma tokenizer and tensor schema must be configured for this packaged model")
    }

    fun close() = modelBytes.fill(0)
}

class SemanticIndex(private val embedder: EmbeddingGemmaService, private val minConfidence: Float = .62f) {
    private val documents = mutableListOf<Pair<SemanticDocument, FloatArray>>()
    suspend fun index(document: SemanticDocument) { documents += document to embedder.embed(EmbeddingPromptRegistry.document(document.citation, document.text)) }
    suspend fun search(query: String, limit: Int = 5): List<SemanticHit> {
        val queryVector = embedder.embed(EmbeddingPromptRegistry.search(query))
        return documents.map { (doc, vector) ->
            val score = cosine(queryVector, vector)
            SemanticHit(doc, score, score.coerceIn(0f, 1f))
        }.filter { it.confidence >= minConfidence }.sortedByDescending { it.score }.take(limit)
    }
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding dimensions differ" }
        var dot = 0f; var an = 0f; var bn = 0f
        a.indices.forEach { i -> dot += a[i] * b[i]; an += a[i] * a[i]; bn += b[i] * b[i] }
        return if (an == 0f || bn == 0f) 0f else (dot / (sqrt(an) * sqrt(bn))).coerceIn(-1f, 1f)
    }
}
