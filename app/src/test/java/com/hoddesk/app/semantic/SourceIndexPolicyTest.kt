package com.hoddesk.app.semantic

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SemanticEmbeddingEntity
import com.hoddesk.app.data.SourceChunkEntity
import com.hoddesk.app.data.SourceEntity
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SourceIndexPolicyTest {
    private val source = SourceEntity(1, "DME meeting", "Attend the DME meeting on 13 October", "Screenshot", sensitivity = "T1")
    private val vector = FloatArray(768) { if (it == 0) 1f else 0f }
    private val bytes = ByteBuffer.allocate(768 * 4).order(ByteOrder.LITTLE_ENDIAN).also { vector.forEach(it::putFloat) }.array()
    private val embedding = SemanticEmbeddingEntity(1, SourceSemanticIndexer.MODEL_VERSION, 768, bytes,
        sourceFingerprint = SourceFingerprint.of(source), promptVersion = EmbeddingPromptRegistry.VERSION)
    private fun chunk(text: String = source.body, modality: String = "text") = SourceChunkEntity(
        sourceId = 1, ordinal = 0, modality = modality, text = text, citation = "paragraph 1",
        modelVersion = SourceSemanticIndexer.MODEL_VERSION, dimension = 768, vector = bytes,
        sourceFingerprint = SourceFingerprint.of(source), promptVersion = EmbeddingPromptRegistry.VERSION)

    @Test fun editedActionFieldsInvalidateIndexEvenWhenCountIsUnchanged() {
        val action = ActionPointEntity(sourceId = 1, action = "Attend meeting", evidence = source.body,
            timeText = "3 PM", ownerText = "Me", confidence = .9f)
        val chunks = listOf(chunk(), chunk(SourceIndexPolicy.actionText(action), "action"))
        assertTrue(SourceIndexPolicy.current(source, embedding, chunks, listOf(action)))
        assertFalse(SourceIndexPolicy.current(source, embedding, chunks, listOf(action.copy(timeText = "4 PM"))))
        assertFalse(SourceIndexPolicy.current(source, embedding, chunks, listOf(action.copy(ownerText = "HMIS officer"))))
    }

    @Test fun rejectedActionsInvalidatePreviouslyIndexedCandidates() {
        val action = ActionPointEntity(sourceId = 1, action = "Attend meeting", evidence = source.body, confidence = .9f)
        assertFalse(SourceIndexPolicy.current(source, embedding,
            listOf(chunk(), chunk(SourceIndexPolicy.actionText(action), "action")), listOf(action.copy(status = "rejected"))))
    }

    @Test fun obsoleteActionChunksAreExcludedBeforeTheNextRebuild() {
        val action = ActionPointEntity(sourceId = 1, action = "Attend meeting", evidence = source.body, confidence = .9f)
        val oldChunk = chunk(SourceIndexPolicy.actionText(action), "action")
        assertNotNull(SourceIndexPolicy.validChunk(oldChunk, source, SourceIndexPolicy.eligibleActionTexts(listOf(action))))
        assertNull(SourceIndexPolicy.validChunk(oldChunk, source, SourceIndexPolicy.eligibleActionTexts(listOf(action.copy(status = "rejected")))))
        assertNull(SourceIndexPolicy.validChunk(oldChunk, source, SourceIndexPolicy.eligibleActionTexts(listOf(action.copy(timeText = "4 PM")))))
        assertNotNull(SourceIndexPolicy.validChunk(chunk(), source, emptySet()))
    }

    @Test fun corruptChunksCannotBeReportedCurrentOrUsedForSearch() {
        val corrupt = chunk().copy(vector = byteArrayOf(1, 2, 3))
        assertFalse(SourceIndexPolicy.current(source, embedding, listOf(corrupt), emptyList()))
        assertNull(SourceIndexPolicy.validChunk(corrupt, source))
        assertNull(SourceIndexPolicy.decode(bytes, 384))
        assertNull(SourceIndexPolicy.decode(ByteArray(768 * 4), 768))
    }

    @Test fun legacyClinicalDataIsExcludedEvenWhenOldSensitivityIsOfficial() {
        assertFalse(SourceIndexPolicy.searchable(source.copy(sensitivity = "official", body = "Patient UHID 91234 admitted with relapse")))
        assertFalse(SourceIndexPolicy.searchable(source.copy(sensitivity = "T3_CLINICAL")))
        assertTrue(SourceIndexPolicy.searchable(source))
    }

    @Test fun pendingAudioAndFailedJobsCannotEnterRetrieval() {
        assertFalse(SourceIndexPolicy.searchable(source.copy(sourceType = "Audio pending transcription")))
        assertFalse(SourceIndexPolicy.searchable(source.copy(processingStatus = "failed")))
    }

    @Test fun stalePromptAndChangedBodyAreNotSemanticEvidence() {
        assertNull(SourceIndexPolicy.validChunk(chunk().copy(promptVersion = 0), source))
        assertNull(SourceIndexPolicy.validChunk(chunk(), source.copy(body = "Meeting rescheduled to tomorrow")))
    }
}
