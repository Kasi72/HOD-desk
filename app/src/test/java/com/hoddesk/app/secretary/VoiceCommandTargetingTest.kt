package com.hoddesk.app.secretary

import org.junit.Assert.*
import org.junit.Test

class VoiceCommandTargetingTest {
    private data class Record(val id: Long, val title: String)
    private val records = listOf(Record(1, "DME drug return meeting"), Record(2, "DME monthly review meeting"))

    @Test fun broadOrTiedMutationNeverChoosesAnArbitraryRecord() {
        assertNull(resolve("move meeting to 3 PM").record)
        assertNull(resolve("move DME meeting to 3 PM").record)
        assertTrue(resolve("move DME meeting to 3 PM").ambiguous)
    }

    @Test fun exactTitleOrExplicitIdSelectsOneRecord() {
        assertEquals(1L, resolve("move DME drug return meeting to 3 PM").record?.id)
        assertEquals(2L, resolve("move event #2 to 3 PM").record?.id)
    }

    @Test fun assigneeCanBeRemovedWithoutWeakeningTaskIdentity() {
        val result = VoiceCommandTargeting.resolve("delegate DME drug return meeting to Dr Ravi", records,
            "task", Record::id, Record::title, omit = listOf("Dr Ravi"))
        assertEquals(1L, result.record?.id)
        assertEquals("Dr Ravi", VoiceCommandTargeting.uniqueAssignee("delegate it to Dr Ravi", listOf("Dr Ravi", "Dr Raj")))
    }

    private fun resolve(query: String) = VoiceCommandTargeting.resolve(query, records, "event", Record::id, Record::title)
}
