package com.hoddesk.app.workflow

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretaryIntelligenceTest {
    @Test
    fun briefExcludesClinicalItemsAndCountsOperationalRisk() {
        val source = SourceEntity(title = "Order", body = "Attend review", sourceType = "PDF", triageBucket = "ACT")
        val clinical = SourceEntity(title = "Case", body = "Patient review", sourceType = "PDF", triageBucket = "ACT", sensitivity = "T3")
        val blocked = TaskEntity(title = "Reply to DME", project = "Official", blocker = "Awaiting document", createdAt = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L, assignee = "DME")

        val brief = SecretaryIntelligence.brief(listOf(source, clinical), listOf(blocked))

        assertEquals(1, brief.toReview)
        assertEquals(1, brief.blocked)
        assertEquals(1, brief.followUps)
        assertTrue(brief.detail.contains("to review"))
        assertTrue(brief.detail.contains("blocked"))
    }
}
