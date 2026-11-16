package com.hoddesk.app.semantic

import com.hoddesk.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SourceSemanticIndexerReliabilityTest {
    private val source = SourceEntity(1, "DME meeting", "Attend the DME meeting tomorrow", "Screenshot", sensitivity = "T1")
    private val vector = FloatArray(768) { if (it == 0) 1f else 0f }
    private val bytes = ByteBuffer.allocate(768 * 4).order(ByteOrder.LITTLE_ENDIAN).also { vector.forEach(it::putFloat) }.array()
    private fun chunk() = SourceChunkEntity(sourceId = 1, ordinal = 0, modality = "text", text = source.body,
        citation = "DME meeting · paragraph 1", modelVersion = SourceSemanticIndexer.MODEL_VERSION,
        dimension = 768, vector = bytes, sourceFingerprint = SourceFingerprint.of(source), promptVersion = EmbeddingPromptRegistry.VERSION)

    private fun dao(chunks: List<SourceChunkEntity>, onCall: (String) -> Unit = {}) = Proxy.newProxyInstance(
        HodDeskDao::class.java.classLoader, arrayOf(HodDeskDao::class.java)
    ) { _, method, _ ->
        onCall(method.name)
        when (method.name) {
            "allSourceChunks" -> chunks
            "allLearningCorrections", "allSourceRelations", "actionPoints" -> emptyList<Any>()
            "sourcesOnce" -> listOf(source)
            "source" -> source
            "allStagedEmbeddings", "allSemanticEmbeddings" -> listOf(SemanticEmbeddingStagingEntity(
                source.id, SourceSemanticIndexer.MODEL_VERSION, 768, bytes,
                sourceFingerprint = SourceFingerprint.of(source), promptVersion = EmbeddingPromptRegistry.VERSION
            )).let { rows -> if (method.name == "allSemanticEmbeddings") rows.map { e ->
                SemanticEmbeddingEntity(e.sourceId, e.modelVersion, e.dimension, e.vector,
                    sourceFingerprint = e.sourceFingerprint, promptVersion = e.promptVersion)
            } else rows }
            "allStagedChunks", "stagedSourceChunks" -> chunks.map { c -> SourceChunkStagingEntity(c.id, c.sourceId, c.ordinal, c.modality, c.text, c.citation,
                modelVersion = c.modelVersion, dimension = c.dimension, vector = c.vector,
                sourceFingerprint = c.sourceFingerprint, promptVersion = c.promptVersion) }
            else -> Unit
        }
    } as HodDeskDao

    @Test fun nativeFailureStillReturnsKeywordMatchesAndNoClinicalSources() = runTest {
        val indexer = SourceSemanticIndexer(dao(listOf(chunk())), object : EmbeddingGemmaService {
            override suspend fun embed(text: String): FloatArray = throw UnsatisfiedLinkError("Optional JNI absent")
        })
        val clinical = source.copy(id = 2, body = "DME meeting about patient UHID 56789", sensitivity = "official")
        assertEquals(listOf(1L), indexer.search("DME meeting", listOf(source, clinical)).map { it.source.id })
    }

    @Test fun corruptOrStaleRowsDoNotInitializeAiAndFallBackToKeywords() = runTest {
        var embeddingCalls = 0
        val indexer = SourceSemanticIndexer(dao(listOf(chunk().copy(vector = byteArrayOf(1)), chunk().copy(promptVersion = 0))),
            object : EmbeddingGemmaService {
                override suspend fun embed(text: String): FloatArray { embeddingCalls++; return vector }
            })
        assertEquals(1, indexer.search("DME meeting", listOf(source)).size)
        assertEquals(0, embeddingCalls)
    }

    @Test fun searchAndSelfCheckPropagateCancellation() = runTest {
        val cancelled = CancellationException("Worker stopped")
        val embedder = object : EmbeddingGemmaService {
            override suspend fun embed(text: String): FloatArray = throw cancelled
        }
        assertSame(cancelled, runCatching { SourceSemanticIndexer(dao(listOf(chunk())), embedder).search("DME", listOf(source)) }.exceptionOrNull())
        assertSame(cancelled, runCatching { IndexSelfCheck.run(embedder) }.exceptionOrNull())
    }

    @Test fun interruptedBuildReusesValidatedPhysicalStagingWithoutEmbeddingAgain() = runTest {
        val called = mutableListOf<String>()
        val indexer = SourceSemanticIndexer(dao(listOf(chunk())) { called += it }, object : EmbeddingGemmaService {
            override suspend fun embed(text: String): FloatArray = error("Complete staging must be reused")
        })
        indexer.indexAll(listOf(source))
        assertTrue("Must atomically activate the completed staging tables", "promoteStagedSemanticIndex" in called)
        assertFalse("Must preserve process-recovered staging", "clearStagedEmbeddings" in called)
        assertFalse("Must preserve process-recovered chunks", "clearStagedChunks" in called)
    }
}
