package com.hoddesk.app.semantic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EmbeddingIntegrityTest {
    @Test fun rejectsWrongDimensionAndNan() {
        assertFalse(EmbeddingIntegrity.validate(FloatArray(3)).valid)
        assertFalse(EmbeddingIntegrity.validate(FloatArray(768) { Float.NaN }).valid)
    }

    @Test fun acceptsNormalizedNonCollapsedVector() {
        val raw = FloatArray(768) { if (it == 0) 2f else 1f }
        val norm = sqrt(raw.fold(0.0) { sum, value -> sum + value * value }).toFloat()
        assertTrue(EmbeddingIntegrity.validate(raw.map { it / norm }.toFloatArray()).valid)
    }

    @Test fun fingerprintChangesWhenPromptVersionChangesInputs() {
        assertNotEquals(EmbeddingIntegrity.fingerprint("a", "b", 768), EmbeddingIntegrity.fingerprint("a", "c", 768))
    }
}
