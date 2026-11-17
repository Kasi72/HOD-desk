package com.hoddesk.app.workflow

import com.hoddesk.app.commitments.CommitmentLedger
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity

/**
 * The weekly executive review: what got done, what slipped, what is due in the coming week, who owes
 * the HOD and what the HOD owes, and how much is still waiting in Review. Read-only; every row links
 * back to the existing task, Review or Follow-up screens.
 */
object WeeklyReview {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val closed = setOf("completed", "deleted", "archived")

    data class Report(
        val done: List<TaskEntity>,
        val slipped: List<TaskEntity>,
        val dueThisWeek: List<TaskEntity>,
        val undated: List<TaskEntity>,
        val waitingOn: List<CommitmentEntity>,
        val iOwe: List<CommitmentEntity>,
        val overdueCommitments: Int,
        val reviewBacklog: Int,
        val oldestReviewDays: Int?,
        val meetingsAhead: Int,
    ) {
        val isEmpty: Boolean get() = done.isEmpty() && slipped.isEmpty() && dueThisWeek.isEmpty() && undated.isEmpty() &&
            waitingOn.isEmpty() && iOwe.isEmpty() && reviewBacklog == 0 && meetingsAhead == 0

        /** One plain sentence for the top of the page. */
        fun headline(): String = when {
            isEmpty -> "Nothing is open. A quiet week."
            slipped.isEmpty() && reviewBacklog == 0 -> "On track: ${done.size} done, ${dueThisWeek.size} due in the next 7 days."
            else -> buildList {
                if (slipped.isNotEmpty()) add("${slipped.size} slipped")
                if (dueThisWeek.isNotEmpty()) add("${dueThisWeek.size} due this week")
                if (reviewBacklog > 0) add("$reviewBacklog waiting in Review")
            }.joinToString(" · ")
        }
    }

    fun build(
        tasks: List<TaskEntity>,
        commitments: List<CommitmentEntity>,
        sources: List<SourceEntity>,
        events: List<EventEntity>,
        now: Long,
        isReviewable: (SourceEntity) -> Boolean = { true },
    ): Report {
        val open = tasks.filter { it.status !in closed }
        val backlog = sources.filter { s ->
            !s.reviewed && s.body.isNotBlank() && s.processingStatus !in closed &&
                (s.snoozedUntil == null || s.snoozedUntil <= now) && isReviewable(s)
        }
        return Report(
            done = tasks.filter { it.status == "completed" && (it.completedAt ?: 0L) in (now - WEEK_MS)..now }
                .sortedByDescending { it.completedAt },
            slipped = open.filter { it.dueAt != null && it.dueAt < now }.sortedBy { it.dueAt },
            dueThisWeek = open.filter { it.dueAt != null && it.dueAt in now..(now + WEEK_MS) }.sortedBy { it.dueAt },
            undated = open.filter { it.dueAt == null }.sortedBy { it.createdAt },
            waitingOn = CommitmentLedger.waitingOnOthers(commitments),
            iOwe = CommitmentLedger.pendingForMe(commitments),
            overdueCommitments = CommitmentLedger.overdue(commitments, now).size,
            reviewBacklog = backlog.size,
            oldestReviewDays = backlog.minOfOrNull { it.capturedAt }?.let { ((now - it) / DAY_MS).toInt() },
            meetingsAhead = events.count { it.startsAt in now..(now + WEEK_MS) },
        )
    }

    /** The Today nudge appears when no review was done in the last week (never, or 7+ days ago). */
    fun isDue(lastReviewAt: Long?, now: Long): Boolean = lastReviewAt == null || now - lastReviewAt >= WEEK_MS
}
