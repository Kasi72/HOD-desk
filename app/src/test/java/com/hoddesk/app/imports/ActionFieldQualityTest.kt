package com.hoddesk.app.imports

import com.hoddesk.app.reasoning.ActionFieldQualityScorer
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionFieldQualityTest {
    @Test fun officialEvidenceScoresHigherThanOcrFailureText() {
        val official = ActionFieldQualityScorer.assess(
            "Attend the scheduled meeting", "13.10.2026", "03:00 PM", "Google Meet",
            listOf("HMIS Nodal Officer"),
            "Official government meeting scheduled on 13.10.2026 at 03:00 PM. Google Meet. HMIS Nodal Officer."
        )
        val failed = ActionFieldQualityScorer.assess("Attend meeting", null, null, null, emptyList(), "OCR failed; transcription unavailable")
        assertTrue(official.sourceReliability > failed.sourceReliability)
        assertTrue(official.overall > failed.overall)
    }
}
