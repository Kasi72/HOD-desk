package com.hoddesk.app.reasoning

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.semantic.SourceSemanticHit
import org.junit.Assert.*
import org.junit.Test

class EvidenceBundleTest {
    @org.junit.Test fun drops_real_annotation_marker_with_version_suffix() {
        org.junit.Assert.assertEquals("Original", EvidenceBundleBuilder.originalText("Original\n--- HOD Desk review cues (on-device extraction) ---\nAction candidate: invented"))
    }
    @Test fun excludes_generated_annotations_and_clinical_related_sources() {
        val source = SourceEntity(id = 1, title = "Roster", body = "Please submit roster.\n--- HOD Desk review cues ---\nAction candidate: INVENTED", sourceType = "Text")
        val clinical = SourceEntity(id = 2, title = "Patient", body = "SECRET", sourceType = "Text", sensitivity = "T3")
        val bundle = EvidenceBundleBuilder.build(source, listOf("Submit roster"), listOf(SourceSemanticHit(clinical, 1f, 1f)))
        assertFalse(bundle.contains("INVENTED"))
        assertFalse(bundle.contains("SECRET"))
        assertTrue(bundle.contains("Please submit roster."))
    }
}
