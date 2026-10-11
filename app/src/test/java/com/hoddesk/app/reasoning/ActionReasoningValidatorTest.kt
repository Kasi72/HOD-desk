package com.hoddesk.app.reasoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionReasoningValidatorTest {
    @Test
    fun buildsCitationFirstPrompt() {
        val evidence = "Attend online Zoom meeting with DME at 2 PM at Erode Collectorate."
        val prompt = ActionReasoningPrompt.build(evidence)
        assertEquals(true, prompt.contains("source_quotes"))
        assertEquals(true, prompt.contains(evidence))
    }

    @Test
    fun accepts_whitespace_normalized_exact_citation() {
        val result = ActionReasoningValidator.parse(
            """{"action":"Attend meeting","category":"meeting","date":null,"time":"2 PM","location":null,"people":[],"owner":null,"source_quotes":["Attend meeting at 2 PM"],"source_ids":[1],"confidence":0.9,"ambiguities":[],"conflicts":[]}""",
            "[SOURCE_ID:1] Attend meeting\nat 2 PM"
        )
        assertEquals("Attend meeting", result.action)
        assertEquals("2 PM", result.time)
    }

    @Test
    fun rejects_empty_action_with_nonzero_confidence() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionReasoningValidator.parse("""{"action":"","category":"official_work","date":null,"time":null,"location":null,"people":[],"owner":null,"source_quotes":[],"source_ids":[],"confidence":0.4,"ambiguities":[],"conflicts":[]}""", "No action")
        }
    }

    @Test
    fun rejects_action_not_supported_by_its_citation() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionReasoningValidator.parse(
                """{"action":"Approve budget","category":"official_work","date":null,"time":"2 PM","location":null,"people":[],"owner":null,"source_quotes":["Attend meeting at 2 PM"],"source_ids":[1],"confidence":0.9,"ambiguities":[],"conflicts":[]}""",
                "[SOURCE_ID:1] Attend meeting at 2 PM"
            )
        }
    }

    @Test fun rejects_missing_required_schema_fields() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ActionReasoningValidator.parse("""{"action":"Attend","confidence":0.9}""", "[SOURCE_ID:1] Attend")
        }
    }

    @Test fun weekday_conflict_is_returned_for_review() {
        val result = ActionReasoningValidator.parse(
            """{"action":"Attend review","category":"meeting","date":"15 October 2026","time":"2 PM","location":null,"people":[],"owner":null,"source_quotes":["Attend review on Monday, 15 October 2026 at 2 PM"],"source_ids":[1],"confidence":0.9,"ambiguities":[],"conflicts":[]}""",
            "[SOURCE_ID:1] Attend review on Monday, 15 October 2026 at 2 PM"
        )
        assertEquals(true, result.conflicts.isNotEmpty())
        assertEquals(null, result.date)
    }
}
