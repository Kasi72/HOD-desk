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
        // SensitivityClassifier stores tier codes ("T3", "T4"); clinical tiers are never indexed.
        val eligible = sources.filter { it.body.isNotBlank() && it.sensitivity !in setOf("T3", "T4", "T3_CLINICAL", "T4_SPECIAL") }
        val embeddings = dao.allSemanticEmbeddings().associateBy { it.sourceId }
        return IndexHealthEvaluator.evaluate(eligible, embeddings, dao.allSourceChunks(modelVersion).size, modelVersion)
    }
}

internal object IndexHealthEvaluator {
    fun evaluate(eligible: List<SourceEntity>, embeddings: Map<Long, SemanticEmbeddingEntity>, chunkCount: Int, modelVersion: String): IndexHealth {
        val stale = mutableListOf<Long>()
        val invalid = mutableListOf<Long>()
        eligible.forEach { source ->
            val embedding = embeddings[source.id]
            if (embedding == null || embedding.modelVersion != modelVersion || embedding.dimension != 768) {
                stale += source.id
            } else if (!EmbeddingIntegrity.validate(embedding.vector.decodeVector()).valid) {
                invalid += source.id
            }
        }
        return IndexHealth(modelVersion, eligible.size, eligible.count { embeddings[it.id]?.modelVersion == modelVersion }, chunkCount, stale, invalid)
    }
}

private fun ByteArray.decodeVector(): FloatArray {
    require(size % 4 == 0) { "Corrupt embedding vector" }
    val buffer = java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.float }
}
