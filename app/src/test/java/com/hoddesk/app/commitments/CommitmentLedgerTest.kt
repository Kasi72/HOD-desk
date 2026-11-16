package com.hoddesk.app.commitments

import com.hoddesk.app.data.CommitmentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CommitmentLedgerTest {
    private fun item(direction: String, status: String = "open", dueAt: Long? = 10L) =
        CommitmentEntity(direction = direction, status = status, summary = "Roster", dueAt = dueAt, evidence = "source")

    @Test fun separatesMineAndWaiting() {
        val items = listOf(item("OWED_BY_ME"), item("OWED_TO_ME"), item("OWED_BY_ME", "completed"))
        assertEquals(1, CommitmentLedger.pendingForMe(items).size)
        assertEquals(1, CommitmentLedger.waitingOnOthers(items).size)
    }

    @Test fun overdueRequiresOpenPastDue() {
        val items = listOf(item("OWED_BY_ME", dueAt = 5L), item("OWED_BY_ME", "completed", 1L), item("OWED_BY_ME", dueAt = 50L))
        assertEquals(1, CommitmentLedger.overdue(items, 10L).size)
    }
}
