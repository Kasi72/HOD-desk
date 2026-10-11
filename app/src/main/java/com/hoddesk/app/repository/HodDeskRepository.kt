package com.hoddesk.app.repository

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.data.EvidenceEntity
import com.hoddesk.app.data.ActionPointEntity
import kotlinx.coroutines.flow.Flow
import com.hoddesk.app.reasoning.ActionReasoningOutput
import org.json.JSONArray
import com.hoddesk.app.data.AuditEntryEntity
import com.hoddesk.app.data.LearningCorrectionEntity
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.triage.TriageScorer
import com.hoddesk.app.privacy.SensitivityClassifier

data class ScheduleProposal(val event: EventEntity, val conflicts: List<EventEntity>) {
    val requiresConfirmation: Boolean get() = conflicts.isNotEmpty()
}

class HodDeskRepository(private val dao: HodDeskDao) {
    companion object {
        const val OWED_BY_ME = "OWED_BY_ME"
        const val OWED_TO_ME = "OWED_TO_ME"
    }
    val sources: Flow<List<SourceEntity>> = dao.observeSources()
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()

    suspend fun ingest(source: SourceEntity): Long {
        val decision = TriageScorer.score(source)
        val sensitivity = SensitivityClassifier.classify(source.title, source.body)
        return dao.insertSource(source.copy(triageBucket = decision.bucket.name, priorityScore = decision.score, priorityReasons = decision.reasons.joinToString(" · "), sensitivity = sensitivity.tier.code))
    }

    suspend fun ingestWithActions(source: SourceEntity, actions: List<ActionPointDraft>): Long {
        val decision = TriageScorer.score(source)
        val sensitivity = SensitivityClassifier.classify(source.title, source.body)
        val sourceId = dao.insertSource(source.copy(triageBucket = decision.bucket.name, priorityScore = decision.score, priorityReasons = decision.reasons.joinToString(" · "), sensitivity = sensitivity.tier.code))
        val consolidated = ActionConsolidator.merge(actions)
        if (consolidated.isNotEmpty()) dao.insertActionPoints(consolidated.map { it.toEntity(sourceId) })
        return sourceId
    }

    suspend fun approveSource(sourceId: Long, project: String, dueAt: Long? = null): Long {
        val taskId = dao.approveSourceAtomic(sourceId, project, dueAt)
        syncCommitment(taskId)
        return taskId
    }

    /** Promotes the exact user-approved action candidate, never the generic source title. */
    private suspend fun audit(type: String, id: Long, action: String, detail: String? = null) = dao.insertAudit(AuditEntryEntity(entityType = type, entityId = id, action = action, detail = detail))

    suspend fun approveAction(sourceId: Long, action: String, project: String, dueAt: Long? = null, assignee: String? = null): Long {
        val taskId = dao.approveActionAtomic(sourceId, action, project, dueAt, assignee)
        syncCommitment(taskId)
        return taskId
    }

    /** Marks a source handled without creating a task ("Not mine" / "Not an action"). Nothing is deleted. */
    suspend fun dismissSource(sourceId: Long, reason: String) {
        val source = dao.source(sourceId) ?: return
        dao.updateSource(source.copy(reviewed = true, triageBucket = if (reason == "not_mine") "NOT_MINE" else source.triageBucket))
        audit("source", sourceId, "dismissed_$reason")
    }

    suspend fun setTaskDue(taskId: Long, dueAt: Long?) {
        val task = dao.task(taskId) ?: return
        dao.updateTask(task.copy(dueAt = dueAt))
        if (dueAt == null) dao.deleteEventsForTask(taskId) else ensureLocalTaskEvent(taskId, task.title, dueAt)
        syncCommitment(taskId)
        audit("task", taskId, if (dueAt == null) "due_cleared" else "rescheduled", dueAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) })
    }

    suspend fun setAssignee(taskId: Long, assignee: String?) {
        val task = dao.task(taskId) ?: return
        dao.updateTask(task.copy(assignee = assignee?.takeIf { it.isNotBlank() }))
        syncCommitment(taskId)
        audit("task", taskId, if (assignee.isNullOrBlank()) "taken_back" else "delegated", assignee)
    }

    fun observeAllAudit(limit: Int = 500): Flow<List<AuditEntryEntity>> = dao.observeAllAudit(limit)

    suspend fun deleteAllWorkData() { dao.deleteAllWorkData(); audit("app", 0, "all_data_deleted") }
    suspend fun setActionPointStatus(sourceId: Long, action: String, status: String) { dao.setActionPointStatus(sourceId, action, status); audit("source", sourceId, "action_status_$status", action) }
    suspend fun updateActionPoint(point: ActionPointEntity) {
        val previous = dao.actionPoint(point.id)
        val normalized = point.copy(
            action = point.action.trim(),
            dateText = point.dateText?.trim()?.takeIf { it.isNotBlank() },
            timeText = point.timeText?.trim()?.takeIf { it.isNotBlank() },
            locationText = point.locationText?.trim()?.takeIf { it.isNotBlank() },
            peopleText = point.peopleText?.trim()?.takeIf { it.isNotBlank() },
        )
        dao.updateActionPoint(normalized)
        if (previous != null) {
            val fields = listOf(
                "action" to (previous.action to normalized.action),
                "date" to (previous.dateText to normalized.dateText),
                "time" to (previous.timeText to normalized.timeText),
                "location" to (previous.locationText to normalized.locationText),
                "people" to (previous.peopleText to normalized.peopleText),
            )
            fields.filter { it.second.first != it.second.second }.forEach { (field, value) ->
                dao.insertLearningCorrection(LearningCorrectionEntity(actionPointId = point.id, field = field, before = value.first, after = value.second))
            }
        }
        audit("action_point", point.id, "edited", normalized.action)
    }

    suspend fun resetLearningProfile() {
        dao.deleteAllLearningCorrections()
        audit("app", 0, "learning_profile_reset")
    }

    suspend fun proposeSchedule(title: String, startsAt: Long, endsAt: Long, taskId: Long? = null): ScheduleProposal {
        require(endsAt > startsAt) { "Event must end after it starts" }
        val event = EventEntity(taskId = taskId, title = title, startsAt = startsAt, endsAt = endsAt)
        return ScheduleProposal(event, dao.overlappingEvents(startsAt, endsAt))
    }

    suspend fun confirmSchedule(proposal: ScheduleProposal): Long {
        require(proposal.conflicts.isEmpty()) { "Conflicting events require explicit rescheduling before confirmation" }
        val eventId = dao.insertEvent(proposal.event.copy(confirmed = true))
        audit("event", eventId, "scheduled_confirmed", proposal.event.title)
        return eventId
    }

    suspend fun returnSourceToInbox(sourceId: Long) {
        val source = dao.source(sourceId) ?: return
        dao.updateSource(source.copy(reviewed = false, processingStatus = "needs_review"))
        audit("source", sourceId, "returned_to_inbox")
    }

    /** Creates a durable local pending record before an external calendar write. */
    suspend fun beginCalendarWrite(event: EventEntity): Long = dao.insertEvent(event.copy(confirmed = false, calendarEventId = null))

    suspend fun finishCalendarWrite(localEventId: Long, providerEventId: Long) {
        val event = requireNotNull(dao.event(localEventId)) { "Pending calendar event $localEventId does not exist" }
        dao.updateEvent(event.copy(confirmed = true, calendarEventId = providerEventId))
        audit("event", localEventId, "scheduled_confirmed", event.title)
    }

    suspend fun cancelCalendarWrite(localEventId: Long) { dao.deleteEvent(localEventId) }

    suspend fun reconcilePendingCalendarEvents(findProviderId: suspend (EventEntity) -> Long?) {
        dao.pendingCalendarEvents().forEach { pending ->
            val providerId = findProviderId(pending) ?: return@forEach
            finishCalendarWrite(pending.id, providerId)
        }
    }
    suspend fun schedulePlannedTask(taskId: Long, title: String, startsAt: Long, endsAt: Long): Long {
        require(endsAt > startsAt) { "Event must end after it starts" }
        require(dao.overlappingEvents(startsAt, endsAt).isEmpty()) { "A local calendar conflict appeared before confirmation" }
        val eventId = dao.insertEvent(EventEntity(taskId = taskId, title = title, startsAt = startsAt, endsAt = endsAt, confirmed = true))
        audit("event", eventId, "scheduled_confirmed", title)
        return eventId
    }

    suspend fun completeTask(taskId: Long) {
        val task = requireNotNull(dao.task(taskId)) { "Task $taskId does not exist" }
        dao.updateTask(task.copy(status = "completed", completedAt = System.currentTimeMillis()))
        dao.setCommitmentStatus(taskId, "completed")
        audit("task", taskId, "completed")
    }

    suspend fun setTaskDone(taskId: Long, done: Boolean) {
        requireNotNull(dao.task(taskId)) { "Task $taskId does not exist" }
        dao.setTaskStatus(taskId, if (done) "completed" else "open", if (done) System.currentTimeMillis() else null)
        if (done) dao.setCommitmentStatus(taskId, "completed") else syncCommitment(taskId)
        audit("task", taskId, if (done) "completed" else "reopened")
    }

    suspend fun addEvidence(taskId: Long, evidenceUri: String, type: String, note: String? = null): Long = dao.insertEvidence(EvidenceEntity(taskId = taskId, uri = evidenceUri, type = type, note = note))

    suspend fun setBlocker(taskId: Long, blocker: String?) {
        val task = requireNotNull(dao.task(taskId)) { "Task $taskId does not exist" }
        dao.updateTask(task.copy(blocker = blocker)); audit("task", taskId, if (blocker.isNullOrBlank()) "blocker_cleared" else "blocker_added", blocker)
    }
    suspend fun requestClarification(taskId: Long, clarification: String) {
        val task = requireNotNull(dao.task(taskId)) { "Task $taskId does not exist" }
        dao.updateTask(task.copy(clarification = clarification, status = "needs_clarification")); audit("task", taskId, "clarification_requested", clarification)
    }

    suspend fun saveReasoningRun(sourceId: Long, output: ActionReasoningOutput, modelVersion: String = "local-gemma-litertlm") {
        dao.insertReasoningRun(com.hoddesk.app.data.ReasoningRunEntity(
            sourceId = sourceId,
            modelVersion = modelVersion,
            action = output.action,
            category = output.category,
            dateText = output.date,
            timeText = output.time,
            locationText = output.location,
            peopleJson = JSONArray(output.people).toString(),
            ambiguitiesJson = JSONArray(output.ambiguities).toString(),
            citationsJson = JSONArray(output.citations).toString(),
            confidence = output.confidence
        ))
    }
    suspend fun deleteSource(id: Long) { audit("source", id, "deleted"); dao.deleteSourceCascade(id) }
    suspend fun deleteTask(id: Long) { audit("task", id, "deleted"); dao.deleteTaskCascade(id) }
    suspend fun deleteEvent(id: Long) { audit("event", id, "deleted"); dao.deleteEvent(id) }
    suspend fun purgeAuditBefore(before: Long): Int = dao.deleteAuditBefore(before)
    suspend fun purgeExpiredSources(before: Long): Int {
        val count = dao.purgeExpiredUnapprovedSources(before)
        if (count > 0) audit("app", 0, "retention_purge", "sources=$count")
        return count
    }

    suspend fun resolveChangeProposal(taskId: Long, decision: String) {
        val task = requireNotNull(dao.task(taskId)) { "Task $taskId does not exist" }
        require(decision == "accepted" || decision == "kept_existing")
        dao.updateTask(task.copy(clarification = null, status = if (task.status == "needs_clarification") "open" else task.status))
        audit("task", taskId, "change_$decision", task.title)
    }

    /**
     * Keeps the in-app itinerary in sync with task due times. This is local and
     * idempotent; writing to an Android provider calendar still requires the
     * separate explicit confirmation flow.
     */
    private suspend fun ensureLocalTaskEvent(taskId: Long, title: String, dueAt: Long?) {
        if (dueAt == null) return
        if (dao.eventsForTask(taskId).any { it.startsAt == dueAt && it.endsAt > dueAt }) return
        dao.deleteEventsForTask(taskId)
        dao.insertEvent(EventEntity(taskId = taskId, title = title, startsAt = dueAt, endsAt = dueAt + 60 * 60 * 1000L, confirmed = true))
    }

    /** Keeps the commitment ledger in lockstep with the task's current owner and due state. */
    private suspend fun syncCommitment(taskId: Long) {
        val task = dao.task(taskId) ?: return
        val existing = dao.commitmentForTask(taskId)
        val status = when (task.status) {
            "completed" -> "completed"
            "deleted", "archived" -> task.status
            else -> "open"
        }
        dao.insertCommitment(
            CommitmentEntity(
                id = existing?.id ?: 0,
                sourceId = task.sourceId,
                taskId = task.id,
                direction = if (task.assignee.isNullOrBlank()) OWED_BY_ME else OWED_TO_ME,
                owner = if (task.assignee.isNullOrBlank()) "me" else task.assignee,
                counterparty = if (task.assignee.isNullOrBlank()) null else "me",
                summary = task.title,
                dueAt = task.dueAt,
                status = status,
                evidence = "Task #" + task.id + " · " + task.project,
                confidence = 1f,
                createdAt = existing?.createdAt ?: task.createdAt,
            )
        )
    }

    suspend fun removePendingAudioPlaceholders(): Int = dao.deletePendingAudioPlaceholderTasks()

    suspend fun backfillTaskEvents() {
        dao.tasksWithDueTimes().forEach { task -> ensureLocalTaskEvent(task.id, task.title, task.dueAt) }
    }
}

data class ActionPointDraft(
    val action: String,
    val dateText: String? = null,
    val timeText: String? = null,
    val locationText: String? = null,
    val peopleText: String? = null,
    val evidence: String,
    val confidence: Float,
    val ambiguity: String? = null,
    val category: String = "official_work"
) {
    fun toEntity(sourceId: Long) = ActionPointEntity(sourceId = sourceId, action = action, category = category, dateText = dateText, timeText = timeText, locationText = locationText, peopleText = peopleText, evidence = evidence, confidence = confidence, ambiguity = ambiguity)
}
