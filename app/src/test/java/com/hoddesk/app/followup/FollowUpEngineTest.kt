package com.hoddesk.app.followup

import com.hoddesk.app.data.CommitmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpEngineTest {
    @Test fun onlyStaleWaitingCommitmentsBecomeCandidates() {
        val stale = CommitmentEntity(id = 1, direction = "OWED_TO_ME", summary = "Roster", evidence = "call", updatedAt = 1L)
        val mine = stale.copy(id = 2, direction = "OWED_BY_ME")
        val fresh = stale.copy(id = 3, updatedAt = 99L)
        val result = FollowUpEngine.candidates(listOf(stale, mine, fresh), now = 3 * 86_400_000L)
        assertEquals(1, result.size)
        assertTrue(result.single().draft.contains("Roster"))
    }

    @Test fun editNeverCreatesBlankDraft() {
        val candidate = FollowUpCandidate(1, "stale", 1, "Confirm", "source")
        assertEquals("Confirm", FollowUpEngine.edit(candidate, " ").draft)
    }
}
