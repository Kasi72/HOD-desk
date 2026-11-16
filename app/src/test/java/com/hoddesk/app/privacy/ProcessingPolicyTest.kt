package com.hoddesk.app.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingPolicyTest {
    @Test fun heavyWorkRequiresCharging() {
        assertFalse(ProcessingPolicy.canRun(80, false, WorkType.HEAVY_INDEX))
        assertTrue(ProcessingPolicy.canRun(80, true, WorkType.HEAVY_INDEX))
    }

    @Test fun lowBatteryAllowsOnlyLightWork() {
        assertFalse(ProcessingPolicy.canRun(19, true, WorkType.AUDIO_TRANSCRIPTION))
        assertTrue(ProcessingPolicy.canRun(19, false, WorkType.SEARCH))
    }
}
