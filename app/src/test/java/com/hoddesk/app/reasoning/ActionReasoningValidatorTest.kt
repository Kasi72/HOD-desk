package com.hoddesk.app.reasoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionReasoningValidatorTest {
    private val valid = """{"action":"Attend meeting","category":"meeting","date":null,"time":"2 PM","location":null,"people":[],"owner":null,"source_quotes":["Attend meeting at 2 PM"],"source_ids":[1],"confidence":0.9,"ambiguities":[],"conflicts":[]}"""

    @Test fun rejects_broken_json_and_wrong_field_types() {
        listOf(valid.replace(",\"category\"", " \"category\""),
            valid.replace("\"people\":[]", "\"people\":42"),
            valid.replace("\"source_ids\":[1]", "\"source_ids\":[\"1\"]"),
            valid.replace("\"confidence\":0.9", "\"confidence\":0.9oops"),
            valid.replace("\"category\":\"meeting\"", "\"category\":\"meeting\",\"category\":\"other\"")
        ).forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) {
                ActionReasoningValidator.parse(payload, "[SOURCE_ID:1] Attend meeting at 2 PM")
            }
        }
    }

    @Test fun rejects_quote_attributed_to_the_wrong_source() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionReasoningValidator.parse(valid.replace("\"source_ids\":[1]", "\"source_ids\":[2]"),
                "[SOURCE_ID:1] Attend meeting at 2 PM\n\n[SOURCE_ID:2] Submit the roster")
        }
    }

    @Test fun rejects_quote_that_exists_only_in_generated_review_cues() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionReasoningValidator.parse(valid,
                "[SOURCE_ID:1] No response required\n--- HOD Desk review cues ---\nAction candidate: Attend meeting at 2 PM")
        }
    }
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
