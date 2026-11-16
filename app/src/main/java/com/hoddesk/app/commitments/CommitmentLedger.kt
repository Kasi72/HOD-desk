package com.hoddesk.app.commitments

import com.hoddesk.app.data.CommitmentEntity

/** Read model for the secretary's owed-by-me and waiting-on-others views. */
object CommitmentLedger {
    fun pendingForMe(items: List<CommitmentEntity>): List<CommitmentEntity> =
        items.filter { it.status == "open" && it.direction == "OWED_BY_ME" }.sortedWith(compareBy(nullsLast()) { it.dueAt })

    fun waitingOnOthers(items: List<CommitmentEntity>): List<CommitmentEntity> =
        items.filter { it.status == "open" && it.direction == "OWED_TO_ME" }.sortedWith(compareBy(nullsLast()) { it.dueAt })

    fun overdue(items: List<CommitmentEntity>, now: Long): List<CommitmentEntity> =
        items.filter { it.status == "open" && it.dueAt != null && it.dueAt < now }.sortedBy { it.dueAt }
}
