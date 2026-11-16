package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskChangeProposalTest {
    private val source = SourceEntity(id = 4, title = "Updated order", body = "Meeting moved to 3 PM", sourceType = "WhatsApp")

    @Test fun changedTimeCreatesConfirmationProposal() {
        val old = ActionPointEntity(id = 1, sourceId = 2, action = "Attend review", timeText = "2 PM", evidence = "2 PM", confidence = .9f)
        val revised = old.copy(timeText = "3 PM", evidence = "Meeting moved to 3 PM")
        val proposal = TaskChangeProposalBuilder.build(old, revised, source)
        assertTrue(proposal!!.requiresConfirmation)
        assertEquals("3 PM", proposal.changes.single().current)
    }

    @Test fun identicalActionDoesNotCreateProposal() {
        val action = ActionPointEntity(id = 1, sourceId = 2, action = "Attend review", evidence = "Attend review", confidence = .9f)
        assertNull(TaskChangeProposalBuilder.build(action, action.copy(), source))
    }
}
