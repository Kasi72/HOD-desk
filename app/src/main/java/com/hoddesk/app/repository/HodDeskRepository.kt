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
import com.hoddesk.app.triage.TriageScorer
import com.hoddesk.app.privacy.SensitivityClassifier

data class ScheduleProposal(val event: EventEntity, val conflicts: List<EventEntity>) {
    val requiresConfirmation: Boolean get() = conflicts.isNotEmpty()
}

class HodDeskRepository(private val dao: HodDeskDao) {
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
        val source = requireNotNull(dao.source(sourceId)) { "Source $sourceId does not exist" }
        val taskId = dao.insertTask(TaskEntity(sourceId = source.id, title = source.title, project = project, dueAt = dueAt))
        dao.updateSource(source.copy(reviewed = true))
        return taskId
    }

    /** Promotes the exact user-approved action candidate, never the generic source title. */
    private suspend fun audit(type: String, id: Long, action: String, detail: String? = null) = dao.insertAudit(AuditEntryEntity(entityType = type, entityId = id, action = action, detail = detail))

    suspend fun approveAction(sourceId: Long, action: String, project: String, dueAt: Long? = null): Long {
        require(action.isNotBlank()) { "Approved action cannot be blank" }
        val source = requireNotNull(dao.source(sourceId)) { "Source $sourceId does not exist" }
        val taskId = dao.insertTask(TaskEntity(sourceId = source.id, title = action.trim(), project = project, dueAt = dueAt))
        dao.setActionPointStatus(source.id, action.trim(), "approved")
        dao.updateSource(source.copy(reviewed = true))
        audit("source", source.id, "action_approved", action.trim())
        audit("task", taskId, "created_from_action", "source=$sourceId")
        return taskId
    }
    suspend fun setActionPointStatus(sourceId: Long, action: String, status: String) { dao.setActionPointStatus(sourceId, action, status); audit("source", sourceId, "action_status_$status", action) }

    suspend fun proposeSchedule(title: String, startsAt: Long, endsAt: Long, taskId: Long? = null): ScheduleProposal {
        require(endsAt > startsAt) { "Event must end after it starts" }
        val event = EventEntity(taskId = taskId, title = title, startsAt = startsAt, endsAt = endsAt)
        return ScheduleProposal(event, dao.overlappingEvents(startsAt, endsAt))
    }

    suspend fun confirmSchedule(proposal: ScheduleProposal): Long {
        val eventId = dao.insertEvent(proposal.event.copy(confirmed = true))
        audit("event", eventId, "scheduled_confirmed", proposal.event.title)
        return eventId
    }
    suspend fun schedulePlannedTask(taskId: Long, title: String, startsAt: Long, endsAt: Long): Long {
        val eventId = dao.insertEvent(EventEntity(taskId = taskId, title = title, startsAt = startsAt, endsAt = endsAt, confirmed = true))
        audit("event", eventId, "scheduled_confirmed", title)
        return eventId
    }

    suspend fun completeTask(taskId: Long) {
        val task = requireNotNull(dao.task(taskId)) { "Task $taskId does not exist" }
        dao.updateTask(task.copy(status = "completed", completedAt = System.currentTimeMillis()))
        audit("task", taskId, "completed")
    }

    suspend fun setTaskDone(taskId: Long, done: Boolean) {
        requireNotNull(dao.task(taskId)) { "Task $taskId does not exist" }
        dao.setTaskStatus(taskId, if (done) "completed" else "open", if (done) System.currentTimeMillis() else null); audit("task", taskId, if (done) "completed" else "reopened")
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
