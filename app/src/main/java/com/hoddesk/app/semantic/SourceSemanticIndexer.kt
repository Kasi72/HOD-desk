package com.hoddesk.app.semantic

import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.SemanticEmbeddingEntity
import com.hoddesk.app.data.SourceChunkEntity
import com.hoddesk.app.data.SourceRelationEntity
import android.net.Uri
import com.hoddesk.app.data.SourceEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import com.hoddesk.app.learning.SecretaryLearning

data class SourceSemanticHit(val source: SourceEntity, val score: Float, val confidence: Float)

/** Persists EmbeddingGemma vectors in the encrypted Room database and searches them locally. */
class SourceSemanticIndexer(
    private val dao: HodDeskDao,
    private val embedder: EmbeddingGemmaService,
    private val minConfidence: Float = .62f
) {
    companion object { const val MODEL_VERSION = "embeddinggemma-2-740m-litertlm-v5-actions-tamil" }

    suspend fun index(source: SourceEntity) {
        if (source.id <= 0L || source.body.isBlank()) return
        // Clinical and special-category sources stay in the encrypted vault;
        // they must never enter semantic retrieval, relations or model calls.
        if (source.sensitivity == "T3" || source.sensitivity == "T4") return
        val existing = dao.semanticEmbedding(source.id)
        if (existing?.modelVersion == MODEL_VERSION && existing.dimension == 768) return
        val directModality: String
        val vector = if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null &&
            (source.sourceType.contains("Screenshot", true) || source.sourceType.contains("Camera", true))) {
            directModality = "image"
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            runCatching { multimodal.embedImage(Uri.parse(source.sourceUri)) }.getOrElse { embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body)) }
        } else if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null && source.sourceType.contains("Audio", true)) {
            directModality = "audio"
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            runCatching { multimodal.embedWav(Uri.parse(source.sourceUri)) }.getOrElse { embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body)) }
        } else {
            directModality = "text"
            embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body))
        }
        val validation = EmbeddingIntegrity.validate(vector)
        check(validation.valid) { "Embedding rejected for source ${source.id}: ${validation.reason}" }
        dao.upsertSemanticEmbedding(SemanticEmbeddingEntity(source.id, MODEL_VERSION, vector.size, vector.toBytes()))
        dao.deleteSourceChunks(source.id)
        val textChunks = chunk(source.body).mapIndexed { index, item ->
            val chunkVector = embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source, item.citation), item.text))
            check(EmbeddingIntegrity.validate(chunkVector).valid) { "Chunk embedding rejected for source ${source.id}" }
            SourceChunkEntity(sourceId = source.id, ordinal = index, modality = source.sourceType, text = item.text, citation = "${source.title} · ${item.citation}", modelVersion = MODEL_VERSION, dimension = chunkVector.size, vector = chunkVector.toBytes())
        }
        // Action candidates get their own vectors.  A query such as “what do
        // I need to submit this week?” should retrieve the extracted action,
        // even when the surrounding document contains many unrelated facts.
        val actionChunks = dao.actionPoints(source.id).mapIndexed { index, point ->
            val actionText = listOfNotNull(
                point.action,
                point.dateText,
                point.timeText,
                point.locationText,
                point.peopleText,
                point.evidence,
            ).joinToString(" · ")
            val actionVector = embedder.embed(EmbeddingPromptRegistry.action(point.action, point.evidence))
            check(EmbeddingIntegrity.validate(actionVector).valid) { "Action embedding rejected for source ${source.id}" }
            SourceChunkEntity(sourceId = source.id, ordinal = 10_000 + index, modality = "action", text = actionText, citation = "${source.title} · action ${index + 1}", modelVersion = MODEL_VERSION, dimension = actionVector.size, vector = actionVector.toBytes())
        }
        // Keep the original modality searchable in the same shared vector space.
        // This enables queries such as “find the screenshot about the DME meeting”.
        val modalityChunk = if (directModality == "image" || directModality == "audio") {
            SourceChunkEntity(sourceId = source.id, ordinal = -1, modality = directModality, text = source.title, citation = "${source.title} · original $directModality", modelVersion = MODEL_VERSION, dimension = vector.size, vector = vector.toBytes())
        } else null
        val chunks = buildList {
            modalityChunk?.let(::add)
            addAll(textChunks)
            addAll(actionChunks)
        }
        if (chunks.isNotEmpty()) dao.insertSourceChunks(chunks)
    }

    suspend fun indexAll(sources: List<SourceEntity>) {
        sources.forEach { index(it) }
        buildRelations(sources)
    }

    /** Links duplicate and related official work after all current vectors are available. */
    private suspend fun buildRelations(sources: List<SourceEntity>) {
        val byId = sources.associateBy { it.id }
        val vectors = dao.allSemanticEmbeddings().associate { it.sourceId to it.vector.toFloatArray() }
        val relations = mutableListOf<SourceRelationEntity>()
        val ids = vectors.keys.sorted()
        for (leftIndex in 0 until ids.size) {
            for (rightIndex in leftIndex + 1 until ids.size) {
                val left = ids[leftIndex]; val right = ids[rightIndex]
                val score = cosine(vectors.getValue(left), vectors.getValue(right))
                if (score < 0.82f || byId[left] == null || byId[right] == null) continue
                val leftSource = byId.getValue(left)
                val rightSource = byId.getValue(right)
                val older = if (leftSource.capturedAt <= rightSource.capturedAt) leftSource else rightSource
                val newer = if (older.id == leftSource.id) rightSource else leftSource
                val decision = SourceSupersession.decide(older, newer, score) ?: continue
                val from = if (decision.relationType == "supersedes") newer.id else left
                val to = if (decision.relationType == "supersedes") older.id else right
                relations += SourceRelationEntity(sourceId = from, relatedSourceId = to, relationType = decision.relationType, score = score, rationale = decision.rationale)
            }
        }
        dao.clearSourceRelations()
        if (relations.isNotEmpty()) dao.insertSourceRelations(relations)
    }

    suspend fun search(query: String, sources: List<SourceEntity>, limit: Int = 5): List<SourceSemanticHit> {
        if (query.isBlank()) return emptyList()
        val queryVector = embedder.embed(EmbeddingPromptRegistry.search(query))
        val byId = sources.associateBy { it.id }
        val chunks = dao.allSourceChunks(MODEL_VERSION)
        val best = chunks.groupBy { it.sourceId }.mapNotNull { (sourceId, sourceChunks) ->
            val source = byId[sourceId] ?: return@mapNotNull null
            val bestChunk = sourceChunks.maxByOrNull { cosine(queryVector, it.vector.toFloatArray()) } ?: return@mapNotNull null
            val score = cosine(queryVector, bestChunk.vector.toFloatArray())
            SourceSemanticHit(source, score, score.coerceIn(0f, 1f))
        }
        val semantic = best.filter { it.confidence >= minConfidence }
        val profile = SecretaryLearning.profile(dao.allLearningCorrections())
        return HybridRetriever.rank(query, sources, semantic, limit)
            .map { SecretaryLearning.rerank(it, profile) }
            .sortedByDescending { it.score }
            .take(limit).map { match ->
            SourceSemanticHit(match.source, match.score, match.score.coerceIn(0f, 1f))
        }
    }

    private data class IndexedChunk(val text: String, val citation: String)

    private fun chunk(text: String, maxChars: Int = 1600): List<IndexedChunk> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return emptyList()
        val result = mutableListOf<IndexedChunk>()
        var start = 0
        while (start < clean.length) {
            var end = (start + maxChars).coerceAtMost(clean.length)
            if (end < clean.length) {
                val boundary = clean.lastIndexOf(' ', end)
                if (boundary > start + maxChars / 2) end = boundary
            }
            val piece = clean.substring(start, end).trim()
            val page = Regex("\\[Page (\\d+)\\]", RegexOption.IGNORE_CASE).find(piece)?.groupValues?.getOrNull(1)
            result += IndexedChunk(piece, page?.let { "page $it" } ?: "document section ${result.size + 1}")
            start = end
        }
        return result
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding dimensions differ" }
        var dot = 0f; var an = 0f; var bn = 0f
        a.indices.forEach { i -> dot += a[i] * b[i]; an += a[i] * a[i]; bn += b[i] * b[i] }
        return if (an == 0f || bn == 0f) 0f else (dot / (sqrt(an) * sqrt(bn))).coerceIn(-1f, 1f)
    }
}

private fun FloatArray.toBytes(): ByteArray = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).also { buffer -> forEach(buffer::putFloat) }.array()

private fun ByteArray.toFloatArray(): FloatArray {
    require(size % 4 == 0) { "Corrupt embedding vector" }
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.float }
}
