package com.hoddesk.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

class SensitivityClassifierTest {
    @Test fun clinicalContentFailsClosed() = assertEquals(SensitivityTier.T3_CLINICAL, SensitivityClassifier.classify("Ward note", "Patient admitted with relapse; bed no 12").tier)
    @Test fun specialContentGetsHighestTier() = assertEquals(SensitivityTier.T4_SPECIAL, SensitivityClassifier.classify("Case", "MHCA legal notice involving suicide risk").tier)
    @Test fun ordinaryCircularIsPublic() = assertEquals(SensitivityTier.T0_PUBLIC, SensitivityClassifier.classify("GO", "Government circular about procurement").tier)
}
