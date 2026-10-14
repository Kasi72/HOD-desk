package com.hoddesk.app.semantic

import org.junit.Assert.assertTrue
import org.junit.Test

class EntityResolverTest {
    @Test fun expandsOfficialAliasIntoSharedSignals() {
        val signals = EntityResolver.signals("Directorate of Medical Education (DME) meeting")
        assertTrue(signals.contains("e:directorate of medical education"))
        assertTrue(signals.contains("e:dme"))
    }
}
