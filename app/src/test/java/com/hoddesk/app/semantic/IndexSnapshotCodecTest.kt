package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceChunkEntity
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class IndexSnapshotCodecTest {
    @Test fun chunkRoundTripPreservesVectorAndEveryEvidenceLocator() {
        val original = SourceChunkEntity(id = 42, sourceId = 7, ordinal = 3, modality = "audio_segment",
            text = "Submit report", citation = "Recording 12–30 seconds", page = 2, paragraphIndex = 4,
            messageIndex = 9, startMs = 12000, endMs = 30000, modelVersion = "model-v6", dimension = 768,
            vector = ByteArray(3072) { (it % 127).toByte() }, indexedAt = 123456789,
            sourceFingerprint = "fingerprint", promptVersion = 6, chunkNumber = 3)
        val writer = IndexSnapshotManager::class.java.getDeclaredMethod("writeChunk", DataOutputStream::class.java, SourceChunkEntity::class.java).apply { isAccessible = true }
        val reader = IndexSnapshotManager::class.java.getDeclaredMethod("readChunk", DataInputStream::class.java).apply { isAccessible = true }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { writer.invoke(IndexSnapshotManager, it, original) }
        DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
            val restored = reader.invoke(IndexSnapshotManager, input) as SourceChunkEntity
            assertEquals(original.copy(vector = restored.vector), restored)
            assertArrayEquals(original.vector, restored.vector)
            assertEquals(-1, input.read())
        }
    }
}
