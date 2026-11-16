package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SourceEntity

/**
 * Pre-approval simulation: exactly what one tap on Approve or Delegate will create, which fields the
 * reviewer changed from the extraction, and what still deserves a second look. It only describes; the
 * DAO transaction remains the single place that writes.
 */
data class ApprovalPreview(
    val effects: List<Effect>,
    val edits: List<ActionChangeDetector.Change>,
    val warnings: List<String>,
    val evidence: String?,
) {
    data class Effect(val label: String, val value: String)

    /** A confirmation step is shown only when something deserves a second look. */
    val needsConfirmation: Boolean get() = warnings.isNotEmpty()
}

object ApprovalPreviewBuilder {
    /**
     * @param original the candidate as extracted, before any edit in this session.
     * @param candidate what will actually be approved.
     * @param dueAt the instant the approval will schedule, or null when it stays unscheduled.
     * @param uncertainFields labels of fields that are unconfirmed or conflicting.
     * @param newerSources titles of newer related sources containing revision language.
     */
    fun build(
        original: ActionPointEntity?,
        candidate: ActionPointEntity,
        source: SourceEntity,
        assignee: String?,
        dueAt: Long?,
        dueLabel: (Long) -> String,
        uncertainFields: List<String> = emptyList(),
        newerSources: List<String> = emptyList(),
    ): ApprovalPreview {
        val owner = assignee?.trim()?.takeIf { it.isNotBlank() }
        val effects = buildList {
            add(ApprovalPreview.Effect("New task", candidate.action.trim()))
            add(ApprovalPreview.Effect("Owner", owner ?: "You"))
            add(ApprovalPreview.Effect("Due", dueAt?.let(dueLabel) ?: "No date · stays unscheduled"))
            add(ApprovalPreview.Effect("Calendar", if (dueAt != null) "Adds a 1-hour event" else "No change"))
            add(ApprovalPreview.Effect("Tracked as", if (owner != null) "Waiting on $owner" else "Something you owe"))
            candidate.locationText?.takeIf { it.isNotBlank() }?.let { add(ApprovalPreview.Effect("Where", it)) }
        }
        val edits = original?.let { TaskChangeProposalBuilder.build(it, candidate, source)?.changes }.orEmpty()
        val warnings = buildList {
            if (uncertainFields.isNotEmpty()) add("Not confirmed: " + uncertainFields.joinToString(", ") { it.lowercase() })
            if (!candidate.dateText.isNullOrBlank() && dueAt == null) add("The date could not be confirmed, so nothing goes on the calendar")
            newerSources.forEach { add("A newer source may change this: $it") }
        }
        val quote = candidate.evidence.trim().lineSequence().map(String::trim).firstOrNull { it.isNotBlank() }?.take(180)
        return ApprovalPreview(effects, edits, warnings, quote)
    }
}
