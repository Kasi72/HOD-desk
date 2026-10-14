package com.hoddesk.app.semantic

import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.SemanticEmbeddingEntity

/** Read-only integrity report used before enabling semantic retrieval. */
data class IndexHealth(
    val modelVersion: String,
    val sourceCount: Int,
    val indexedSourceCount: Int,
    val chunkCount: Int,
    val staleSourceIds: List<Long>,
    val invalidSourceIds: List<Long>
) {
    val healthy: Boolean get() = staleSourceIds.isEmpty() && invalidSourceIds.isEmpty() &&
        (sourceCount == 0 || indexedSourceCount == sourceCount)
}

class IndexHealthChecker(private val dao: HodDeskDao) {
    suspend fun inspect(sources: List<SourceEntity>, modelVersion: String = SourceSemanticIndexer.MODEL_VERSION): IndexHealth {
        val eligible = sources.filter(SourceIndexPolicy::searchable)
        val embeddings = dao.allSemanticEmbeddings().associateBy { it.sourceId }
        val chunks = dao.allSourceChunks(modelVersion)
        val health = IndexHealthEvaluator.evaluate(eligible, embeddings, chunks, modelVersion)
        val changedActions = eligible.filter { source ->
            val expected = dao.actionPoints(source.id).filter { it.status != "rejected" }.map(SourceIndexPolicy::actionText).sorted()
            chunks.filter { it.sourceId == source.id && it.modality == "action" }.map { it.text }.sorted() != expected
        }.map { it.id }
        val stale = (health.staleSourceIds + changedActions).distinct()
        return health.copy(staleSourceIds = stale, indexedSourceCount = eligible.count {
            it.id !in stale && it.id !in health.invalidSourceIds
        })
    }
}

internal object IndexHealthEvaluator {
    /** Compatibility overload for deterministic unit tests that only model the
     * source-level table. Production inspection uses the metadata-aware form. */
    fun evaluate(eligible: List<SourceEntity>, embeddings: Map<Long, SemanticEmbeddingEntity>, chunkCount: Int, modelVersion: String): IndexHealth {
        val stale = eligible.filter { val e = embeddings[it.id]; e == null || e.modelVersion != modelVersion || e.dimension != 768 }.map { it.id }
        val invalid = eligible.filter { it.id !in stale }.filter { SourceIndexPolicy.decode(embeddings.getValue(it.id).vector, embeddings.getValue(it.id).dimension) == null }.map { it.id }
        return IndexHealth(modelVersion, eligible.size, eligible.count { it.id !in stale && it.id !in invalid }, chunkCount, stale, invalid)
    }

    fun evaluate(eligible: List<SourceEntity>, embeddings: Map<Long, SemanticEmbeddingEntity>, chunks: List<com.hoddesk.app.data.SourceChunkEntity>, modelVersion: String): IndexHealth {
        val stale = mutableListOf<Long>()
        val invalid = mutableListOf<Long>()
        eligible.forEach { source ->
            val embedding = embeddings[source.id]
            val fingerprint = SourceFingerprint.of(source)
            val sourceChunks = chunks.filter { it.sourceId == source.id }
            if (embedding == null || embedding.modelVersion != modelVersion || embedding.dimension != 768 ||
                embedding.sourceFingerprint != fingerprint || embedding.promptVersion != EmbeddingPromptRegistry.VERSION ||
                sourceChunks.isEmpty() || sourceChunks.any { it.modelVersion != modelVersion || it.dimension != 768 || it.sourceFingerprint != fingerprint || it.promptVersion != EmbeddingPromptRegistry.VERSION }) {
                stale += source.id
            } else if (SourceIndexPolicy.decode(embedding.vector, embedding.dimension) == null ||
                sourceChunks.any { SourceIndexPolicy.decode(it.vector, it.dimension) == null }) {
                invalid += source.id
            }
        }
        return IndexHealth(modelVersion, eligible.size, eligible.count { embeddings[it.id]?.modelVersion == modelVersion && it.id !in stale && it.id !in invalid },
            chunks.count { chunk -> eligible.any { it.id == chunk.sourceId } }, stale, invalid)
    }
}
