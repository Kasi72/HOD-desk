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
import android.util.Log

data class SourceSemanticHit(
    val source: SourceEntity,
    val score: Float,
    val confidence: Float,
    val citation: String? = null,
    val modality: String? = null
)

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
        // An earlier process can die after writing the document vector but
        // before writing chunks. Also, edited action candidates must become
        // searchable. Only skip when the complete, current chunk set exists.
        if (existing?.modelVersion == MODEL_VERSION && existing.dimension == 768) {
            val chunks = dao.sourceChunks(source.id)
            val expectedActions = dao.actionPoints(source.id).size
            val indexedActions = chunks.count { it.modality == "action" }
            if (chunks.isNotEmpty() && indexedActions == expectedActions) return
        }
        val directModality: String
        var directEmbedding = false
        val vector = if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null &&
            (source.sourceType.contains("Screenshot", true) || source.sourceType.contains("Camera", true))) {
            directModality = "image"
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            runCatching { multimodal.embedImage(Uri.parse(source.sourceUri)).also { directEmbedding = true } }.getOrElse {
                Log.w("HOD_SEMANTIC", "direct_image_embedding_failed source=${source.id}; using OCR text", it)
                embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body))
            }
        } else if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null && source.sourceType.contains("Audio", true)) {
            directModality = "audio"
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            runCatching { multimodal.embedWav(Uri.parse(source.sourceUri)).also { directEmbedding = true } }.getOrElse {
                Log.w("HOD_SEMANTIC", "direct_audio_embedding_failed source=${source.id}; using transcript text", it)
                embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body))
            }
        } else {
            directModality = "text"
            embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body))
        }
        Log.i("HOD_SEMANTIC", "source_modality source=${source.id},modality=$directModality,direct=$directEmbedding")
        val validation = EmbeddingIntegrity.validate(vector)
        check(validation.valid) { "Embedding rejected for source ${source.id}: ${validation.reason}" }
        dao.upsertSemanticEmbedding(SemanticEmbeddingEntity(source.id, MODEL_VERSION, vector.size, vector.toBytes(), directModality, source.sourceUri))
        dao.deleteSourceChunks(source.id)
        val textChunks = chunk(source.body, source.sourceType).mapIndexed { index, item ->
            val chunkVector = embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source, item.citation), item.text))
            check(EmbeddingIntegrity.validate(chunkVector).valid) { "Chunk embedding rejected for source ${source.id}" }
            SourceChunkEntity(sourceId = source.id, ordinal = index, modality = source.sourceType, text = item.text, citation = "${source.title} · ${item.citation}", page = item.page, paragraphIndex = item.paragraphIndex, messageIndex = item.messageIndex, startMs = item.startMs, endMs = item.endMs, modelVersion = MODEL_VERSION, dimension = chunkVector.size, vector = chunkVector.toBytes())
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
        val mediaChunks = if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null) {
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            when {
                directModality == "audio" -> runCatching { multimodal.embedWavSegments(Uri.parse(source.sourceUri)) }.getOrDefault(emptyList()).mapIndexed { index, segment ->
                    SourceChunkEntity(sourceId = source.id, ordinal = -10_000 - index, modality = "audio_segment", text = source.title, citation = "${source.title} · ${segment.startMs / 1000}s–${segment.endMs / 1000}s", startMs = segment.startMs, endMs = segment.endMs, modelVersion = MODEL_VERSION, dimension = segment.vector.size, vector = segment.vector.toBytes())
                }
                directModality == "image" -> runCatching { multimodal.embedImageRegions(Uri.parse(source.sourceUri)) }.getOrDefault(emptyList()).mapIndexed { index, region ->
                    SourceChunkEntity(sourceId = source.id, ordinal = -20_000 - index, modality = "image_region", text = source.title, citation = "${source.title} · image region ${region.region}", modelVersion = MODEL_VERSION, dimension = region.vector.size, vector = region.vector.toBytes())
                }
                source.sourceType.contains("PDF", true) -> runCatching { multimodal.embedPdfPagePreviews(Uri.parse(source.sourceUri)) }.getOrDefault(emptyList()).map { page ->
                    SourceChunkEntity(sourceId = source.id, ordinal = -30_000 - page.page, modality = "document_page_preview", text = source.title, citation = "${source.title} · page ${page.page} preview", page = page.page, modelVersion = MODEL_VERSION, dimension = page.vector.size, vector = page.vector.toBytes())
                }
                else -> emptyList()
            }
        } else emptyList()
        val chunks = buildList {
            modalityChunk?.let(::add)
            addAll(mediaChunks)
            addAll(textChunks)
            addAll(actionChunks)
        }
        if (chunks.isNotEmpty()) dao.insertSourceChunks(chunks)
    }

    suspend fun indexAll(sources: List<SourceEntity>) {
        sources.forEach { source ->
            runCatching { index(source) }
                .onFailure { Log.e("HOD_SEMANTIC", "source_index_failed id=${source.id}", it) }
        }
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
                if (byId[left] == null || byId[right] == null) continue
                val leftSource = byId.getValue(left)
                val rightSource = byId.getValue(right)
                val signal = sharedMatterSignals(leftSource, rightSource)
                // Keep semantic linking conservative, while allowing a
                // matching order/reference number, date, or venue cue to
                // survive OCR and Tamil/Tanglish wording differences.
                if (score < 0.82f && signal < 0.15f) continue
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

    private fun sharedMatterSignals(a: SourceEntity, b: SourceEntity): Float {
        val left = matterSignals(a.title + " " + a.body)
        val right = matterSignals(b.title + " " + b.body)
        if (left.isEmpty() || right.isEmpty()) return 0f
        return left.intersect(right).size.toFloat() / minOf(left.size, right.size).toFloat()
    }

    private fun matterSignals(text: String): Set<String> {
        val normalized = TamilTextNormalizer.expandTanglish(text).lowercase()
        val numbers = Regex("\\b\\d{2,}\\b").findAll(normalized).map { "n:${it.value}" }
        val dates = Regex("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b|\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\w*\\b").findAll(normalized).map { "d:${it.value}" }
        val cueWords = Regex("(?:at|in|venue|location|collectorate|hospital|office|இல்|இடம்)\\s+([\\p{L}][\\p{L}.-]{2,})", RegexOption.IGNORE_CASE)
            .findAll(normalized).map { "l:${it.groupValues[1]}" }
        return (numbers + dates + cueWords).toSet()
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
            SourceSemanticHit(source, score, score.coerceIn(0f, 1f), bestChunk.citation, bestChunk.modality)
        }
        val semantic = best.filter { it.confidence >= minConfidence }
        val profile = SecretaryLearning.profile(dao.allLearningCorrections())
        return HybridRetriever.rank(query, sources, semantic, limit)
            .map { SecretaryLearning.rerank(it, profile) }
            .sortedByDescending { it.score }
            .take(limit).map { match ->
                SourceSemanticHit(match.source, match.score, match.score.coerceIn(0f, 1f), match.evidenceCitation, match.evidenceModality)
        }
    }

    /** Cross-modal image query: a camera/screenshot can retrieve text and audio sources. */
    suspend fun searchImage(uri: Uri, sources: List<SourceEntity>, limit: Int = 5): List<SourceSemanticHit> {
        val multimodal = embedder as? EmbeddingGemmaLiteRtRuntime ?: return emptyList()
        return searchVector(multimodal.embedImage(uri), sources, limit)
    }

    /** Cross-modal audio query: each query waveform window is matched against indexed chunks. */
    suspend fun searchAudio(uri: Uri, sources: List<SourceEntity>, limit: Int = 5): List<SourceSemanticHit> {
        val multimodal = embedder as? EmbeddingGemmaLiteRtRuntime ?: return emptyList()
        val hits = multimodal.embedWavSegments(uri).flatMap { searchVector(it.vector, sources, limit) }
        return hits.groupBy { it.source.id }.map { (_, values) -> values.maxBy { it.score } }.sortedByDescending { it.score }.take(limit)
    }

    private suspend fun searchVector(queryVector: FloatArray, sources: List<SourceEntity>, limit: Int): List<SourceSemanticHit> {
        val byId = sources.filter { it.sensitivity != "T3" && it.sensitivity != "T4" }.associateBy { it.id }
        return dao.allSourceChunks(MODEL_VERSION).groupBy { it.sourceId }.mapNotNull { (sourceId, sourceChunks) ->
            val source = byId[sourceId] ?: return@mapNotNull null
            val best = sourceChunks.maxByOrNull { cosine(queryVector, it.vector.toFloatArray()) } ?: return@mapNotNull null
            val score = cosine(queryVector, best.vector.toFloatArray())
            SourceSemanticHit(source, score, score.coerceIn(0f, 1f), best.citation, best.modality)
        }.filter { it.confidence >= minConfidence }.sortedByDescending { it.score }.take(limit)
    }

    private data class IndexedChunk(val text: String, val citation: String, val page: Int? = null, val paragraphIndex: Int? = null, val messageIndex: Int? = null, val startMs: Long? = null, val endMs: Long? = null)

    private fun chunk(text: String, sourceType: String, maxChars: Int = 1600, overlap: Int = 240): List<IndexedChunk> {
        // Keep line boundaries: PDF page markers and audio timestamps are
        // evidence anchors, so flattening all whitespace would lose them.
        val clean = text.replace("\r\n", "\n").replace(Regex("[ \\t]+"), " ").trim()
        if (clean.isBlank()) return emptyList()
        val result = mutableListOf<IndexedChunk>()
        var start = 0
        var paragraph = 0
        while (start < clean.length) {
            var end = (start + maxChars).coerceAtMost(clean.length)
            if (end < clean.length) {
                val boundary = maxOf(clean.lastIndexOf('\n', end), clean.lastIndexOf(' ', end))
                if (boundary > start + maxChars / 2) end = boundary
            }
            val piece = clean.substring(start, end).trim()
            val page = Regex("\\[Page (\\d+)\\]", RegexOption.IGNORE_CASE).find(piece)?.groupValues?.getOrNull(1)
                ?.toIntOrNull()
            val paragraphIndex = paragraph + piece.substringBefore("\n").let { if (it.isBlank()) 0 else 1 }
            paragraph += piece.count { it == '\n' }
            val messageIndex = if (sourceType.contains("WhatsApp", true)) {
                when {
                    piece.contains("[Previous message]", true) -> 0
                    piece.contains("[Reply]", true) -> 1
                    else -> result.size
                }
            } else null
            val time = Regex("(?:\\[)?(\\d{1,2}):(\\d{2})(?:\\s*[-–]\\s*(\\d{1,2}):(\\d{2}))?(?:\\])?").find(piece)
            fun millis(groupMinute: Int, groupSecond: Int): Long = (groupMinute * 60L + groupSecond) * 1000L
            val startMs = time?.let { millis(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            val endMs = time?.groupValues?.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { millis(it.toInt(), time.groupValues[4].toInt()) }
            val citation = buildList {
                page?.let { add("page $it") }
                paragraphIndex.let { add("paragraph $it") }
                messageIndex?.let { add("message $it") }
                startMs?.let { add(if (endMs != null) "${it / 1000}s–${endMs / 1000}s" else "${it / 1000}s") }
                if (isEmpty()) add("document section ${result.size + 1}")
            }.joinToString(" · ")
            result += IndexedChunk(piece, citation, page, paragraphIndex, messageIndex, startMs, endMs)
            val next = end - overlap
            start = if (next > start) next else end
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
