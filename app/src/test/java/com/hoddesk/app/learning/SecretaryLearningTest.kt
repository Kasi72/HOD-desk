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
}
