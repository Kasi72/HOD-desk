package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceFingerprintTest {
    @Test fun whitespace_and_case_reimports_have_same_identity() {
        val a = SourceEntity(sourceType = "WhatsApp", title = "A", body = "Attend   DME review\n at 2 PM")
        val b = SourceEntity(sourceType = "whatsapp", title = "Different title", body = " attend dme review at 2 pm ")
        assertEquals(SourceFingerprint.of(a), SourceFingerprint.of(b))
    }

    @Test fun different_source_types_do_not_collapse() {
        assertNotEquals(SourceFingerprint.of("WhatsApp", "Attend review"), SourceFingerprint.of("PDF", "Attend review"))
    }
}
