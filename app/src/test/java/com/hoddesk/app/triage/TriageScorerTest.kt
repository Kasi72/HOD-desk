package com.hoddesk.app.triage

import com.hoddesk.app.data.SourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageScorerTest {
    @Test fun urgentDmeDirectiveIsAct() {
        val decision = TriageScorer.score(SourceEntity(title = "DME reminder", body = "HOD shall submit report by 5 PM without fail", sourceType = "WhatsApp"))
        assertEquals(TriageBucket.ACT, decision.bucket)
        assertTrue(decision.reasons.isNotEmpty())
    }

    @Test fun promiseBecomesWaiting() {
        assertEquals(TriageBucket.WAITING, TriageScorer.score(SourceEntity(title = "Roster", body = "Dr A will send the roster tomorrow", sourceType = "WhatsApp")).bucket)
    }

    @Test fun addressedToAnotherDepartmentIsNotMine() {
        assertEquals(TriageBucket.NOT_MINE, TriageScorer.score(SourceEntity(title = "Circular", body = "HOD Surgery to submit the list", sourceType = "PDF")).bucket)
    }
}
