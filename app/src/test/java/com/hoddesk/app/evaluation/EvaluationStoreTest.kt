package com.hoddesk.app.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationStoreTest {
    @Test fun anonymizeRemovesContactIdentifiers() {
        val clean = EvaluationStore.anonymize("Call 9876543210 or mail hod@example.org")
        assertTrue(clean.contains("[PHONE]"))
        assertTrue(clean.contains("[EMAIL]"))
    }

    @Test fun reportCountsActionRecall() {
        val expected = EvaluationExample("1", "text", "submit roster", "submit roster", null, null, null, true)
        val actual = expected.copy(expectedAction = "submit roster")
        assertEquals(1, EvaluationStore.report(listOf(expected), listOf(actual)).caseCount)
        assertEquals(1.0, EvaluationStore.report(listOf(expected), listOf(actual)).recallAt10, 0.01)
    }
    @Test fun gateBlocksUnsafeRegression() {
        val baseline = EvaluationReport(10, .95)
        val current = EvaluationReport(10, .91)
        val gate = EvaluationStore.gate(baseline, current, .03)
        assertTrue(!gate.passed)
        assertTrue(gate.reasons.any { it.contains("false verified") })
    }
}
