package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.imports.ActionDueParser

/** User edits are proposals until the reviewer explicitly resolves the existing warnings. */
object ReviewApprovalPolicy {
    fun edit(point: ActionPointEntity, action: String, date: String, time: String, location: String,
             people: String, owner: String, reference: String, confirmed: Boolean): ActionPointEntity {
        require(action.isNotBlank()) { "Enter an action before saving" }
        fun value(text: String) = text.trim().ifBlank { null }
        if (confirmed && date.isNotBlank()) {
            require(ActionDueParser.parse(date, time.ifBlank { null }) != null) { "Use a valid date and time, or clear them to leave this task unscheduled" }
        }
        if (confirmed && time.isNotBlank() && date.isBlank()) {
            require(TemporalEvidence.parseTime(time) != null) { "Use a valid time or clear it" }
        }
        return point.copy(action = action.trim(), dateText = value(date), timeText = value(time),
            locationText = value(location), peopleText = value(people), ownerText = value(owner), referenceNumber = value(reference),
            ambiguity = if (confirmed) null else point.ambiguity,
            status = if (confirmed || point.ambiguity.isNullOrBlank()) "needs_review" else "needs_confirmation")
    }

    fun dueAt(point: ActionPointEntity?): Long? = point?.let { ActionDueParser.parseIfUnambiguous(it.dateText, it.timeText, it.ambiguity) }
}
