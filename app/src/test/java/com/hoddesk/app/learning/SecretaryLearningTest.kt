package com.hoddesk.app.learning

import com.hoddesk.app.data.LearningCorrectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretaryLearningTest {
    @Test fun profileRequiresTwoSamplesBeforeRanking() {
        val one = LearningCorrectionEntity(actionPointId = 1, field = "location", before = "Office", after = "Collectorate")
        assertEquals(0, SecretaryLearning.profile(listOf(one)).phraseWeights.size)
    }

    @Test fun repeatedCorrectionCreatesBoundedPreference() {
        val corrections = (1..3).map { LearningCorrectionEntity(actionPointId = it.toLong(), field = "location", before = "Office", after = "Collectorate") }
        val profile = SecretaryLearning.profile(corrections)
        assertTrue(profile.phraseWeights["collectorate"]!! <= .25f)
        assertEquals(3, profile.sampleCount)
    }
    @Test fun sensitiveCorrectionsNeverEnterProfile() {
        val corrections = (1..3).map { LearningCorrectionEntity(actionPointId = it.toLong(), field = "people", before = "patient", after = "Patient 3") }
        assertEquals(0, SecretaryLearning.profile(corrections).sampleCount)
    }

    @Test fun decisionsAreInspectableButDoNotBecomeSearchPhrases() {
        val corrections = (1..3).map { LearningCorrectionEntity(actionPointId = it.toLong(), field = "decision", before = null, after = "rejected") }
        val profile = SecretaryLearning.profile(corrections)
        assertEquals(3, profile.sampleCount)
        assertTrue(profile.phraseWeights.isEmpty())
        assertTrue(SecretaryLearning.exportLines(corrections).all { it.contains("decision") })
    }
}
