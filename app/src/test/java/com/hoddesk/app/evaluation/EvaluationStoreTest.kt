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

    @Test fun reportMeasuresStructuredFieldsAndCitations() {
        val expected = EvaluationExample("1", "text", "Attend at Erode", "Attend", "19 Sep 2026", "2 PM", "Erode", true)
        val actual = expected.copy(sourceQuote = "Attend at Erode on 19 Sep 2026 at 2 PM")
        val report = EvaluationStore.report(listOf(expected), listOf(actual))
        assertEquals(1.0, report.fieldAccuracy, 0.001)
        assertEquals(1.0, report.citationCoverage, 0.001)
    }

    @Test fun baselineIsPrivacySafeAndCoversTamilAndTanglish() {
        assertTrue(EvaluationCorpus.baseline.size >= 10)
        assertTrue(EvaluationCorpus.baseline.any { it.inputType == "audio" })
        assertTrue(EvaluationCorpus.baseline.any { it.source.contains("செப்டம்பர்") })
        assertTrue(EvaluationCorpus.baseline.any { it.source.contains("pannunga") })
        assertTrue(EvaluationCorpus.baseline.none { it.source.contains("patient", ignoreCase = true) })
    }
}
