package com.hoddesk.app.semantic

import com.hoddesk.app.data.SemanticEmbeddingEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.SourceChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexHealthTest {
    @Test fun integrity_rejects_stale_model_and_accepts_valid_index() {
        val source = SourceEntity(id = 1, title = "Order", body = "Attend review", sourceType = "PDF")
        val empty = IndexHealthEvaluator.evaluate(listOf(source), emptyMap(), 0, SourceSemanticIndexer.MODEL_VERSION)
        assertFalse(empty.healthy)
        val embeddings = mapOf(1L to SemanticEmbeddingEntity(1, SourceSemanticIndexer.MODEL_VERSION, 768, normalizedVector().toBytesForTest()))
        assertTrue(IndexHealthEvaluator.evaluate(listOf(source), embeddings, 1, SourceSemanticIndexer.MODEL_VERSION).healthy)
    }

    @Test fun corruptPassageVectorsFailHealthEvenWhenSourceVectorIsValid() {
        val source = SourceEntity(id = 1, title = "Order", body = "Attend review", sourceType = "PDF")
        val fingerprint = SourceFingerprint.of(source)
        val embedding = SemanticEmbeddingEntity(1, SourceSemanticIndexer.MODEL_VERSION, 768, normalizedVector().toBytesForTest(),
            sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION)
        val chunk = SourceChunkEntity(sourceId = 1, ordinal = 0, modality = "text", text = source.body, citation = "page 1",
            modelVersion = SourceSemanticIndexer.MODEL_VERSION, dimension = 768, vector = byteArrayOf(1, 2, 3),
            sourceFingerprint = fingerprint, promptVersion = EmbeddingPromptRegistry.VERSION)
        val health = IndexHealthEvaluator.evaluate(listOf(source), mapOf(1L to embedding), listOf(chunk), SourceSemanticIndexer.MODEL_VERSION)
        assertFalse(health.healthy)
        assertEquals(listOf(1L), health.invalidSourceIds)
        assertEquals(0, health.indexedSourceCount)
    }

    private fun normalizedVector(): FloatArray {
        val raw = FloatArray(768) { kotlin.math.sin(it.toDouble()).toFloat() }
        val norm = kotlin.math.sqrt(raw.sumOf { (it * it).toDouble() }).toFloat()
        return raw.map { it / norm }.toFloatArray()
    }
}

private fun FloatArray.toBytesForTest(): ByteArray = java.nio.ByteBuffer.allocate(size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).also { b -> forEach(b::putFloat) }.array()
