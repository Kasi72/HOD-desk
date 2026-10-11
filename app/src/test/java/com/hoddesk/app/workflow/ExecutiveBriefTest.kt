package com.hoddesk.app.workflow

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.data.CommitmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutiveBriefTest {
    @Test fun deletedAndCompletedItemsNeverReachBrief() {
        val deleted = SourceEntity(id = 1, title = "Deleted", body = "Submit report", sourceType = "WhatsApp", processingStatus = "deleted")
        val live = SourceEntity(id = 2, title = "Live", body = "Attend review", sourceType = "PDF")
        val done = TaskEntity(id = 3, sourceId = 1, title = "Done", project = "work", status = "completed")
        val open = TaskEntity(id = 4, sourceId = 2, title = "Open", project = "work", dueAt = 1L)
        val brief = SecretaryIntelligence.executiveBrief(SecretaryIntelligence.BriefKind.MORNING, listOf(deleted, live), listOf(done, open), now = 0L)
        assertTrue(brief.citations.none { it.sourceId == 1L })
        assertTrue(brief.citations.any { it.taskId == 4L })
    }

    @Test fun morningBriefAlwaysRequiresReview() {
        val source = SourceEntity(id = 1, title = "Order", body = "Attend review", sourceType = "PDF")
        val brief = SecretaryIntelligence.executiveBrief(SecretaryIntelligence.BriefKind.MORNING, listOf(source), emptyList(), now = 0L)
        assertTrue(brief.requiresReview)
        assertEquals(1, brief.citations.size)
    }

    @Test fun allBriefModesCarryAuthorityAndExcludeClinicalCommitments() {
        val official = SourceEntity(id = 7, title = "GO", body = "Attend review", sourceType = "PDF", priorityScore = 90)
        val clinical = SourceEntity(id = 8, title = "Clinical", body = "Patient discussion", sourceType = "PDF", sensitivity = "T3")
        val visibleCommitment = CommitmentEntity(sourceId = 7, direction = "OWED_TO_ME", summary = "Confirm venue", evidence = "Official message")
        val clinicalCommitment = CommitmentEntity(sourceId = 8, direction = "OWED_BY_ME", summary = "Clinical detail", evidence = "Patient note")
        val modes = SecretaryIntelligence.BriefKind.entries.map { kind ->
            SecretaryIntelligence.executiveBrief(kind, listOf(official, clinical), emptyList(), commitments = listOf(visibleCommitment, clinicalCommitment), now = 10_000L)
        }
        assertTrue(modes.all { it.requiresReview })
        assertTrue(modes.flatMap { it.citations }.none { it.sourceId == 8L })
        assertTrue(modes.flatMap { it.citations }.any { it.authority == "High authority" })
    }
}
