package com.hoddesk.app.semantic

import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.SemanticEmbeddingEntity
import com.hoddesk.app.data.SourceChunkEntity
import com.hoddesk.app.data.SemanticEmbeddingStagingEntity
import com.hoddesk.app.data.SourceChunkStagingEntity
import com.hoddesk.app.data.SourceRelationEntity
import com.hoddesk.app.data.AuditEntryEntity
import android.net.Uri
import com.hoddesk.app.data.SourceEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import com.hoddesk.app.learning.SecretaryLearning
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    companion object {
        const val MODEL_VERSION = "embeddinggemma-2-740m-litertlm-v6-validated-evidence"
        // UI imports, a manual rebuild and WorkManager must not overwrite one another.
        private val indexWriteMutex = Mutex()
    }

    suspend fun index(source: SourceEntity) = indexWriteMutex.withLock { index(source, staged = false) }

    private suspend fun index(source: SourceEntity, staged: Boolean) {
        currentCoroutineContext().ensureActive()
        if (!SourceIndexPolicy.searchable(source)) return
        // Clinical and special-category sources stay in the encrypted vault;
        // they must never enter semantic retrieval, relations or model calls.
        val fingerprint = SourceFingerprint.of(source)
        val actionPoints = dao.actionPoints(source.id).filter { it.status != "rejected" }
        val existing = if (staged) dao.allStagedEmbeddings().firstOrNull { it.sourceId == source.id }?.let {
            SemanticEmbeddingEntity(it.sourceId, it.modelVersion, it.dimension, it.vector, it.modality, it.evidenceLocator, it.indexedAt, it.sourceFingerprint, it.promptVersion, it.chunkNumber)
        } else dao.semanticEmbedding(source.id)
        // An earlier process can die after writing the document vector but
        // before writing chunks. Also, edited action candidates must become
        // searchable. Only skip when the complete, current chunk set exists.
        if (existing != null) {
            val chunks = if (staged) dao.stagedSourceChunks(source.id).map { c ->
                SourceChunkEntity(c.id, c.sourceId, c.ordinal, c.modality, c.text, c.citation, c.page, c.paragraphIndex, c.messageIndex, c.startMs, c.endMs, c.modelVersion, c.dimension, c.vector, c.indexedAt, c.sourceFingerprint, c.promptVersion, c.chunkNumber)
            } else dao.sourceChunks(source.id)
            if (SourceIndexPolicy.current(source, existing, chunks, actionPoints)) return
        }
        var directModality = "text"
        var directEmbedding = false
        var cachedAudioSegments: List<AudioEmbeddingSegment>? = null
        val vector = if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null &&
            (source.sourceType.contains("Screenshot", true) || source.sourceType.contains("Camera", true))) {
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            recoverable { multimodal.embedImage(Uri.parse(source.sourceUri)).also { directEmbedding = true; directModality = "image" } }.getOrElse {
                Log.w("HOD_SEMANTIC", "direct_image_embedding_failed source=${source.id}; using OCR text", it)
                embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body))
            }
        } else if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null &&
            (source.sourceType.contains("Audio", true) || source.sourceType.contains("Call", true))) {
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            recoverable {
                val segments = multimodal.embedWavSegments(Uri.parse(source.sourceUri))
                MultimodalResourcePolicy.summarize(segments.map { it.vector }).also {
                    cachedAudioSegments = segments; directEmbedding = true; directModality = "audio"
                }
            }.getOrElse {
                Log.w("HOD_SEMANTIC", "direct_audio_embedding_failed source=${source.id}; using transcript text", it)
                embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body))
            }
        } else {
            embedder.embed(EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source), source.body))
        }
        Log.i("HOD_SEMANTIC", "source_modality source=${source.id},modality=$directModality,direct=$directEmbedding")
        val validation = EmbeddingIntegrity.validate(vector)
        check(validation.valid) { "Embedding rejected for source ${source.id}: ${validation.reason}" }
        val embedding = SemanticEmbeddingEntity(source.id, MODEL_VERSION, vector.size, vector.toBytes(), directModality, source.sourceUri, sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION, chunkNumber = 0)
        val textItems = chunk(source.body, source.sourceType)
        val textVectors = embedder.embedBatch(textItems.map { item -> EmbeddingPromptRegistry.document(EmbeddingPromptRegistry.header(source, item.citation), item.text) })
        val textChunks = textItems.mapIndexed { index, item ->
            val chunkVector = textVectors[index]
            check(EmbeddingIntegrity.validate(chunkVector).valid) { "Chunk embedding rejected for source ${source.id}" }
            SourceChunkEntity(sourceId = source.id, ordinal = index, modality = source.sourceType, text = item.text, citation = "${source.title} · ${item.citation}", page = item.page, paragraphIndex = item.paragraphIndex, messageIndex = item.messageIndex, startMs = item.startMs, endMs = item.endMs, modelVersion = MODEL_VERSION, dimension = chunkVector.size, vector = chunkVector.toBytes(), sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION, chunkNumber = index)
        }
        // Action candidates get their own vectors.  A query such as “what do
        // I need to submit this week?” should retrieve the extracted action,
        // even when the surrounding document contains many unrelated facts.
        val actionVectors = embedder.embedBatch(actionPoints.map { point -> EmbeddingPromptRegistry.action(point.action, point.evidence) })
        val actionChunks = actionPoints.mapIndexed { index, point ->
            val actionText = SourceIndexPolicy.actionText(point)
            val actionVector = actionVectors[index]
            check(EmbeddingIntegrity.validate(actionVector).valid) { "Action embedding rejected for source ${source.id}" }
            SourceChunkEntity(sourceId = source.id, ordinal = 10_000 + index, modality = "action", text = actionText, citation = "${source.title} · action ${index + 1}", modelVersion = MODEL_VERSION, dimension = actionVector.size, vector = actionVector.toBytes(), sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION, chunkNumber = 10_000 + index)
        }
        // Keep the original modality searchable in the same shared vector space.
        // This enables queries such as “find the screenshot about the DME meeting”.
        val modalityChunk = if (directModality == "image" || directModality == "audio") {
            SourceChunkEntity(sourceId = source.id, ordinal = -1, modality = directModality, text = source.title, citation = "${source.title} · original $directModality", modelVersion = MODEL_VERSION, dimension = vector.size, vector = vector.toBytes(), sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION, chunkNumber = -1)
        } else null
        val mediaChunks = if (embedder is EmbeddingGemmaLiteRtRuntime && source.sourceUri != null) {
            val multimodal = embedder as EmbeddingGemmaLiteRtRuntime
            when {
                directModality == "audio" -> (cachedAudioSegments ?: recoverable { multimodal.embedWavSegments(Uri.parse(source.sourceUri)) }.getOrDefault(emptyList())).mapIndexed { index, segment ->
                    SourceChunkEntity(sourceId = source.id, ordinal = -10_000 - index, modality = "audio_segment", text = source.title, citation = "${source.title} · ${segment.startMs / 1000}s–${segment.endMs / 1000}s", startMs = segment.startMs, endMs = segment.endMs, modelVersion = MODEL_VERSION, dimension = segment.vector.size, vector = segment.vector.toBytes(), sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION, chunkNumber = -10_000 - index)
                }
                directModality == "image" -> recoverable { multimodal.embedImageRegions(Uri.parse(source.sourceUri)) }.getOrDefault(emptyList()).mapIndexed { index, region ->
                    SourceChunkEntity(sourceId = source.id, ordinal = -20_000 - index, modality = "image_region", text = source.title, citation = "${source.title} · image region ${region.region}", modelVersion = MODEL_VERSION, dimension = region.vector.size, vector = region.vector.toBytes(), sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION, chunkNumber = -20_000 - index)
                }
                source.sourceType.contains("PDF", true) -> recoverable { multimodal.embedPdfPagePreviews(Uri.parse(source.sourceUri)) }.getOrDefault(emptyList()).map { page ->
                    SourceChunkEntity(sourceId = source.id, ordinal = -30_000 - page.page, modality = "document_page_preview", text = source.title, citation = "${source.title} · page ${page.page} preview", page = page.page, modelVersion = MODEL_VERSION, dimension = page.vector.size, vector = page.vector.toBytes(), sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION, chunkNumber = -30_000 - page.page)
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
        check(chunks.all { SourceIndexPolicy.decode(it.vector, it.dimension) != null }) { "Media chunk integrity check failed" }
        currentCoroutineContext().ensureActive()
        // Source deletion/edits during native inference must not resurrect old evidence.
        val liveSource = dao.source(source.id) ?: return
        if (!SourceIndexPolicy.searchable(liveSource) || SourceFingerprint.of(liveSource) != fingerprint) return
        if (SourceIndexPolicy.eligibleActionTexts(dao.actionPoints(source.id)) !=
            SourceIndexPolicy.eligibleActionTexts(actionPoints)) return
        if (staged) {
            dao.replaceStagedSourceIndex(
                SemanticEmbeddingStagingEntity(embedding.sourceId, embedding.modelVersion, embedding.dimension, embedding.vector, embedding.modality, embedding.evidenceLocator, embedding.indexedAt, embedding.sourceFingerprint, embedding.promptVersion, embedding.chunkNumber),
                chunks.map { c -> SourceChunkStagingEntity(c.id, c.sourceId, c.ordinal, c.modality, c.text, c.citation, c.page, c.paragraphIndex, c.messageIndex, c.startMs, c.endMs, c.modelVersion, c.dimension, c.vector, c.indexedAt, c.sourceFingerprint, c.promptVersion, c.chunkNumber) }
            )
        } else {
            dao.replaceSourceIndex(embedding, chunks)
        }
    }

    suspend fun indexAll(sources: List<SourceEntity>) = indexWriteMutex.withLock {
        // Staging survives a cancellation/process kill. A restarted build reuses
        // only complete, current records; the active index is untouched until promotion.
        val eligible = sources.filter(SourceIndexPolicy::searchable)
        val expectedIds = eligible.map { it.id }.toSet()
        try {
            (dao.allStagedEmbeddings().map { it.sourceId } + dao.allStagedChunks().map { it.sourceId })
                .toSet().filter { it !in expectedIds }.forEach { dao.clearStagedSource(it) }
            eligible.forEach { source -> index(source, staged = true) }
            val stagedEmbeddings = dao.allStagedEmbeddings()
            val stagedChunks = dao.allStagedChunks()
            check(stagedEmbeddings.map { it.sourceId }.toSet() == expectedIds) { "Staged source set is incomplete" }
            check(stagedEmbeddings.all { SourceIndexPolicy.decode(it.vector, it.dimension) != null }) { "Staged embedding integrity check failed" }
            check(stagedChunks.all { SourceIndexPolicy.decode(it.vector, it.dimension) != null }) { "Staged chunk integrity check failed" }
            val live = dao.sourcesOnce().filter(SourceIndexPolicy::searchable).associateBy { it.id }
            check(live.keys == expectedIds && eligible.all { source ->
                live[source.id]?.let { SourceFingerprint.of(it) == SourceFingerprint.of(source) } == true
            }) { "Sources changed during rebuild; retry to include current evidence" }
            currentCoroutineContext().ensureActive()
            dao.promoteStagedSemanticIndex(eligible)
        } catch (failure: Throwable) {
            // Retain complete staging rows for retry. Never mutate the active
            // index while handling cancellation or an interrupted build.
            throw failure
        }
        buildRelations(eligible)
    }

    /** Links duplicate and related official work after all current vectors are available. */
    private suspend fun buildRelations(sources: List<SourceEntity>) {
        val byId = sources.filter(SourceIndexPolicy::searchable).associateBy { it.id }
        val vectors = dao.allSemanticEmbeddings().mapNotNull { row ->
            val source = byId[row.sourceId] ?: return@mapNotNull null
            if (row.modelVersion != MODEL_VERSION || row.promptVersion != EmbeddingPromptRegistry.VERSION ||
                row.sourceFingerprint != SourceFingerprint.of(source)) return@mapNotNull null
            SourceIndexPolicy.decode(row.vector, row.dimension)?.let { row.sourceId to it }
        }.toMap()
        val relations = mutableListOf<SourceRelationEntity>()
        val ids = vectors.keys.sorted()
        for (leftIndex in 0 until ids.size) {
            currentCoroutineContext().ensureActive()
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
        // One Room transaction preserves the previous relations on cancellation
        // and ensures that proposed links and their audit records agree.
        val priorKeys = dao.allSourceRelations().map { Triple(it.sourceId, it.relatedSourceId, it.relationType) }.toSet()
        dao.replaceSourceRelations(relations, relations.filter {
            Triple(it.sourceId, it.relatedSourceId, it.relationType) !in priorKeys
        }.map { relation ->
            AuditEntryEntity(
                entityType = "source_relation", entityId = relation.sourceId,
                action = "relation_proposal_pending",
                detail = "related=${relation.relatedSourceId};type=${relation.relationType};score=${relation.score};${relation.rationale}"
            )
        })
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
        return (numbers + dates + cueWords).toSet() + EntityResolver.signals(text)
    }

    suspend fun search(query: String, sources: List<SourceEntity>, limit: Int = 5): List<SourceSemanticHit> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val allowed = sources.filter(SourceIndexPolicy::searchable)
        val byId = allowed.associateBy { it.id }
        val liveActions = allowed.associate { it.id to SourceIndexPolicy.eligibleActionTexts(dao.actionPoints(it.id)) }
        val chunks = dao.allSourceChunks(MODEL_VERSION).mapNotNull { chunk ->
            val source = byId[chunk.sourceId] ?: return@mapNotNull null
            SourceIndexPolicy.validChunk(chunk, source, liveActions[chunk.sourceId].orEmpty())?.let { chunk to it }
        }
        // Keyword retrieval must remain usable with no model, failed native
        // initialization, corrupt vectors, or a first-run index still being built.
        val queryVector = if (chunks.isEmpty()) null else recoverable {
            embedder.embed(EmbeddingPromptRegistry.search(query.take(4000))).also {
                check(EmbeddingIntegrity.validate(it).valid) { "Invalid query embedding" }
            }
        }.getOrNull()
        val best = if (queryVector == null) emptyList() else chunks.groupBy { it.first.sourceId }.mapNotNull { (sourceId, sourceChunks) ->
            val source = byId[sourceId] ?: return@mapNotNull null
            val bestChunk = sourceChunks.maxByOrNull { cosine(queryVector, it.second) } ?: return@mapNotNull null
            val score = cosine(queryVector, bestChunk.second)
            SourceSemanticHit(source, score, score.coerceIn(0f, 1f), bestChunk.first.citation, bestChunk.first.modality)
        }
        val semantic = best.filter { it.confidence >= minConfidence }
        val profile = SecretaryLearning.profile(dao.allLearningCorrections())
        return HybridRetriever.rank(query, allowed, semantic, allowed.size)
            .map { SecretaryLearning.rerank(it, profile) }
            .sortedByDescending { it.score }
            .take(limit.coerceAtMost(100)).map { match ->
                SourceSemanticHit(match.source, match.score, match.score.coerceIn(0f, 1f), match.evidenceCitation, match.evidenceModality)
        }
    }

    /** Cross-modal image query: a camera/screenshot can retrieve text and audio sources. */
    suspend fun searchImage(uri: Uri, sources: List<SourceEntity>, limit: Int = 5): List<SourceSemanticHit> {
        val multimodal = embedder as? EmbeddingGemmaLiteRtRuntime ?: return emptyList()
        return recoverable { searchVector(multimodal.embedImage(uri), sources, limit) }.getOrDefault(emptyList())
    }

    /** Cross-modal audio query: each query waveform window is matched against indexed chunks. */
    suspend fun searchAudio(uri: Uri, sources: List<SourceEntity>, limit: Int = 5): List<SourceSemanticHit> {
        val multimodal = embedder as? EmbeddingGemmaLiteRtRuntime ?: return emptyList()
        val hits = recoverable { multimodal.embedWavSegments(uri).flatMap { searchVector(it.vector, sources, limit) } }.getOrDefault(emptyList())
        return hits.groupBy { it.source.id }.map { (_, values) -> values.maxBy { it.score } }.sortedByDescending { it.score }.take(limit.coerceIn(0, 100))
    }

    private suspend fun searchVector(queryVector: FloatArray, sources: List<SourceEntity>, limit: Int): List<SourceSemanticHit> {
        if (limit <= 0 || !EmbeddingIntegrity.validate(queryVector).valid) return emptyList()
        val byId = sources.filter(SourceIndexPolicy::searchable).associateBy { it.id }
        val liveActions = byId.keys.associateWith { SourceIndexPolicy.eligibleActionTexts(dao.actionPoints(it)) }
        return dao.allSourceChunks(MODEL_VERSION).groupBy { it.sourceId }.mapNotNull { (sourceId, sourceChunks) ->
            val source = byId[sourceId] ?: return@mapNotNull null
            val best = sourceChunks.mapNotNull { chunk -> SourceIndexPolicy.validChunk(chunk, source, liveActions[sourceId].orEmpty())?.let { chunk to it } }
                .maxByOrNull { cosine(queryVector, it.second) } ?: return@mapNotNull null
            val score = cosine(queryVector, best.second)
            SourceSemanticHit(source, score, score.coerceIn(0f, 1f), best.first.citation, best.first.modality)
        }.filter { it.confidence >= minConfidence }.sortedByDescending { it.score }.take(limit.coerceAtMost(100))
    }

    private suspend fun <T> recoverable(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    } catch (missingRuntime: LinkageError) {
        Result.failure(missingRuntime)
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
