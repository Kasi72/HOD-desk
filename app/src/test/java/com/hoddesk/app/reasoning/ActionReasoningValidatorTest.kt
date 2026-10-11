package com.hoddesk.app.reasoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionReasoningValidatorTest {
    @Test
    fun buildsCitationFirstPrompt() {
        val evidence = "Attend online Zoom meeting with DME at 2 PM at Erode Collectorate."
        val prompt = ActionReasoningPrompt.build(evidence)
        assertEquals(true, prompt.contains("citations"))
        assertEquals(true, prompt.contains(evidence))
    }

    @Test
    fun accepts_whitespace_normalized_exact_citation() {
        val result = ActionReasoningValidator.parse(
            """{"action":"Attend meeting","category":"meeting","confidence":0.9,"citations":["Attend meeting at 2 PM"],"ambiguities":[],"people":[],"date":null,"time":"2 PM","location":null}""",
            "Attend meeting\nat 2 PM"
        )
        assertEquals("Attend meeting", result.action)
        assertEquals("2 PM", result.time)
    }

    @Test
    fun rejects_empty_action_with_nonzero_confidence() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionReasoningValidator.parse("""{"action":"","confidence":0.4,"citations":[]}""", "No action")
        }
    }

    @Test
    fun rejects_action_not_supported_by_its_citation() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionReasoningValidator.parse(
                """{"action":"Approve budget","category":"official_work","confidence":0.9,"citations":["Attend meeting at 2 PM"],"ambiguities":[],"people":[],"date":null,"time":"2 PM","location":null}""",
                "Attend meeting at 2 PM"
            )
        }
    }
}
