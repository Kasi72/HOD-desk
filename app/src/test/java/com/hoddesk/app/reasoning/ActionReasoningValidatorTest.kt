package com.hoddesk.app.reasoning

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionReasoningValidatorTest {
    @Test
    fun buildsCitationFirstPrompt() {
        val evidence = "Attend online Zoom meeting with DME at 2 PM at Erode Collectorate."
        val prompt = ActionReasoningPrompt.build(evidence)
        assertEquals(true, prompt.contains("citations"))
        assertEquals(true, prompt.contains(evidence))
    }
}
