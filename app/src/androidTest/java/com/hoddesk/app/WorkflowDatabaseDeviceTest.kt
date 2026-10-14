package com.hoddesk.app

import android.test.InstrumentationTestCase
import androidx.room.Room
import com.hoddesk.app.data.*
import kotlinx.coroutines.runBlocking

/** Tests generated Room transactions on actual Android SQLite, without touching the user's database. */
@Suppress("DEPRECATION")
class WorkflowDatabaseDeviceTest : InstrumentationTestCase() {
    private lateinit var database: HodDeskDatabase
    private lateinit var dao: HodDeskDao

    override fun setUp() {
        super.setUp()
        database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, HodDeskDatabase::class.java).build()
        dao = database.dao()
    }
    override fun tearDown() { database.close(); super.tearDown() }

    fun testApprovalKeepsRemainingActionsAndIsIdempotent() = runBlocking {
        val sourceId = dao.insertSource(SourceEntity(title = "Synthetic notice", body = "Prepare report. Submit report.", sourceType = "Document"))
        dao.insertActionPoints(listOf(candidate(sourceId, "Prepare report"), candidate(sourceId, "Submit report")))
        val first = dao.approveActionAtomic(sourceId, "Prepare report", "Office", null, null)
        assertFalse(dao.source(sourceId)!!.reviewed)
        assertEquals(first, dao.approveActionAtomic(sourceId, "Prepare report", "Office", null, null))
        assertNotNull(dao.commitmentForTask(first))
        assertEquals(1, dao.actionPoints(sourceId).count { it.status == "approved" })
        dao.approveActionAtomic(sourceId, "Submit report", "Office", null, null)
        assertTrue(dao.source(sourceId)!!.reviewed)
    }

    fun testEditedApprovalCommitsFieldsLearningAndTaskTogether() = runBlocking {
        val sourceId = dao.insertSource(SourceEntity(title = "Synthetic meeting", body = "Attend meeting at Erode", sourceType = "Document"))
        dao.insertActionPoints(listOf(candidate(sourceId, "Attend meeting")))
        val point = dao.actionPoints(sourceId).single()
        val id = dao.approveEditedActionAtomic(point.copy(action = "Attend the review meeting", locationText = "Erode"), sourceId, "Office", "Attend the review meeting", null, null)
        assertEquals("Attend the review meeting", dao.task(id)!!.title)
        assertEquals("Erode", dao.actionPoint(point.id)!!.locationText)
        assertTrue(dao.learningCorrections(point.id).any { it.field == "location" })
        assertTrue(dao.allAudit().any { it.action == "edited" })
    }

    fun testClinicalApprovalRollsBackDraftAndTask() = runBlocking {
        val sourceId = dao.insertSource(SourceEntity(title = "Synthetic quarantine", body = "Synthetic clinical material", sourceType = "Text", sensitivity = "T3"))
        try {
            dao.approveEditedActionAtomic(candidate(sourceId, "Send report"), sourceId, "Office", "Send report", null, null)
            fail("Clinical material must not create ordinary tasks")
        } catch (_: IllegalArgumentException) { }
        assertTrue(dao.actionPoints(sourceId).isEmpty())
        assertNull(dao.approvedTask(sourceId, "Send report"))
    }

    fun testChangedSourceCannotReplaceTheActiveIndex() = runBlocking {
        val sourceId = dao.insertSource(SourceEntity(title = "Synthetic source", body = "Submit report", sourceType = "Text"))
        val source = dao.source(sourceId)!!
        val vector = ByteArray(768 * 4)
        dao.upsertSemanticEmbedding(SemanticEmbeddingEntity(sourceId, "active", 768, vector))
        dao.upsertStagedEmbedding(SemanticEmbeddingStagingEntity(sourceId, "staged", 768, vector))
        dao.updateSource(source.copy(body = "Submit revised report"))
        try { dao.promoteStagedSemanticIndex(listOf(source)); fail("Changed source must block activation") }
        catch (_: IllegalStateException) { }
        assertEquals("active", dao.semanticEmbedding(sourceId)!!.modelVersion)
    }

    fun testLegacyGeneratedCandidatesRefreshWithoutOverwritingUserEdits() = runBlocking {
        val sourceId = dao.insertSource(SourceEntity(title = "Synthetic notice", body = "Submit report.", sourceType = "Text"))
        dao.insertActionPoints(listOf(candidate(sourceId, "Synthetic notice title")))
        val source = dao.source(sourceId)!!
        dao.refreshGeneratedCandidates(sourceId, source.body, "test-v1", listOf(candidate(sourceId, "Submit report")))
        assertEquals("Submit report", dao.actionPoints(sourceId).single().action)
        val point = dao.actionPoints(sourceId).single()
        dao.updateActionPointAtomic(point.copy(action = "Submit the corrected report"))
        dao.refreshGeneratedCandidates(sourceId, source.body, "test-v2", listOf(candidate(sourceId, "Wrong replacement")))
        assertEquals("Submit the corrected report", dao.actionPoints(sourceId).single().action)
    }

    private fun candidate(sourceId: Long, action: String) = ActionPointEntity(sourceId = sourceId, action = action, evidence = action, confidence = .7f)
}
