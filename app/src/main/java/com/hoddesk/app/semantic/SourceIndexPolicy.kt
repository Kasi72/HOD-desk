package com.hoddesk.app.semantic

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SemanticEmbeddingEntity
import com.hoddesk.app.data.SourceChunkEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.privacy.SensitivityClassifier
import com.hoddesk.app.privacy.SensitivityTier
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Checks the live source at every retrieval/index boundary, including legacy records. */
object SourceIndexPolicy {
    private val quarantined = setOf(SensitivityTier.T3_CLINICAL, SensitivityTier.T4_SPECIAL)

    fun searchable(source: SourceEntity): Boolean = source.id > 0 && source.body.isNotBlank() &&
        source.sensitivity !in setOf("T3", "T4", "T3_CLINICAL", "T4_SPECIAL") &&
        source.processingStatus in setOf("ready", "needs_review") &&
        !source.sourceType.contains("pending transcription", ignoreCase = true) &&
        SensitivityClassifier.classify(source.title, source.body).tier !in quarantined

    fun actionText(point: ActionPointEntity): String = listOfNotNull(
        point.action, point.dateText, point.timeText, point.locationText, point.peopleText,
        point.ownerText, point.referenceNumber, point.recurrenceRule, point.evidence
    ).joinToString(" · ")

    fun eligibleActionTexts(actions: List<ActionPointEntity>): Set<String> = actions
        .filter { it.status !in setOf("rejected", "dismissed") }.map(::actionText).toSet()

    fun current(source: SourceEntity, embedding: SemanticEmbeddingEntity?, chunks: List<SourceChunkEntity>, actions: List<ActionPointEntity>): Boolean {
        if (!searchable(source) || embedding == null || chunks.isEmpty()) return false
        val fingerprint = SourceFingerprint.of(source)
        if (embedding.modelVersion != SourceSemanticIndexer.MODEL_VERSION ||
            embedding.promptVersion != EmbeddingPromptRegistry.VERSION ||
            embedding.sourceFingerprint != fingerprint || decode(embedding.vector, embedding.dimension) == null) return false
        if (chunks.any { it.modelVersion != SourceSemanticIndexer.MODEL_VERSION ||
                it.promptVersion != EmbeddingPromptRegistry.VERSION || it.sourceFingerprint != fingerprint ||
                decode(it.vector, it.dimension) == null }) return false
        val expected = eligibleActionTexts(actions).sorted()
        return chunks.filter { it.modality == "action" }.map { it.text }.sorted() == expected
    }

    /** Corruption and obsolete dimensions are a reason to use keyword search, never a crash. */
    fun decode(bytes: ByteArray, dimension: Int): FloatArray? {
        if (dimension != 768 || bytes.size != dimension * 4) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val vector = FloatArray(dimension) { buffer.float }
        return vector.takeIf { EmbeddingIntegrity.validate(it, dimension).valid }
    }

    fun validChunk(chunk: SourceChunkEntity, source: SourceEntity): FloatArray? =
        if (chunk.modelVersion == SourceSemanticIndexer.MODEL_VERSION &&
            chunk.promptVersion == EmbeddingPromptRegistry.VERSION &&
            chunk.sourceFingerprint == SourceFingerprint.of(source)) decode(chunk.vector, chunk.dimension) else null

    fun validChunk(chunk: SourceChunkEntity, source: SourceEntity, currentActionTexts: Set<String>): FloatArray? =
        if (chunk.modality == "action" && chunk.text !in currentActionTexts) null else validChunk(chunk, source)
}
