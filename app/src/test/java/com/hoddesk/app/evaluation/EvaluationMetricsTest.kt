package com.hoddesk.app.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationMetricsTest {
    @Test fun perfectTranscriptionHasZeroError() {
        assertEquals(0.0, EvaluationMetrics.cer("கூட்டம் 3 PM", "கூட்டம் 3 PM"), 0.0001)
        assertEquals(0.0, EvaluationMetrics.wer("Attend DME review", "Attend DME review"), 0.0001)
    }

    @Test fun tamilCharacterErrorIgnoresWhitespace() {
        assertEquals(0.0, EvaluationMetrics.cer("கூட்டம்  3 PM", "கூட்டம் 3 PM"), 0.0001)
        assertTrue(EvaluationMetrics.cer("Erode Collectorate", "Erode Collectrate") > 0.0)
    }

    @Test fun recallCountsRelevantResultsWithinCutoff() {
        val score = EvaluationMetrics.recallAtK(setOf("a", "c"), listOf("x", "a", "c"), 3)
        assertEquals(1.0, score, 0.0001)
        assertEquals(0.5, EvaluationMetrics.recallAtK(setOf("a", "c"), listOf("a", "x"), 2), 0.0001)
    }
}
