package com.hoddesk.app.repository

import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.data.EvidenceEntity
import com.hoddesk.app.data.ActionPointEntity
import kotlinx.coroutines.flow.Flow
import com.hoddesk.app.reasoning.ActionReasoningOutput
import com.hoddesk.app.reasoning.FieldEvidenceAttribution
import org.json.JSONArray
import com.hoddesk.app.data.AuditEntryEntity
import com.hoddesk.app.data.LearningCorrectionEntity
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.transitionTo
import com.hoddesk.app.triage.TriageScorer
import com.hoddesk.app.privacy.SensitivityClassifier
import com.hoddesk.app.semantic.SourceFingerprint
import com.hoddesk.app.workflow.CaptureIntentClassifier

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
        findDuplicateSource(source)?.let { existing ->
            audit("source", existing.id, "duplicate_import_ignored", SourceFingerprint.of(source))
            return existing.id
        }
        val decision = TriageScorer.score(source)
        val intent = CaptureIntentClassifier.classify(source.title, source.body, source.sourceType)
        val sensitivity = SensitivityClassifier.classify(source.title, source.body)
        return dao.insertSource(source.copy(triageBucket = decision.bucket.name, priorityScore = decision.score, priorityReasons = (decision.reasons + intent.reasons).distinct().joinToString(" · "), sensitivity = sensitivity.tier.code, intentKind = intent.kind.wire))
    }

    suspend fun ingestWithActions(source: SourceEntity, actions: List<ActionPointDraft>): Long {
        findDuplicateSource(source)?.let { existing ->
            audit("source", existing.id, "duplicate_import_ignored", SourceFingerprint.of(source))
            return existing.id
        }
        val decision = TriageScorer.score(source)
        val intent = CaptureIntentClassifier.classify(source.title, source.body, source.sourceType)
        val sensitivity = SensitivityClassifier.classify(source.title, source.body)
        val sourceId = dao.insertSource(source.copy(triageBucket = decision.bucket.name, priorityScore = decision.score, priorityReasons = (decision.reasons + intent.reasons).distinct().joinToString(" · "), sensitivity = sensitivity.tier.code, intentKind = intent.kind.wire))
        val consolidated = ActionConsolidator.merge(actions)
        if (consolidated.isNotEmpty()) dao.insertActionPoints(consolidated.map { it.toEntity(sourceId) })
        return sourceId
    }

    private suspend fun findDuplicateSource(source: SourceEntity): SourceEntity? =
        dao.sourcesOnce().firstOrNull { it.id != source.id && SourceFingerprint.of(it) == SourceFingerprint.of(source) }

    suspend fun approveSource(sourceId: Long, project: String, dueAt: Long? = null): Long {
        return dao.approveSourceAtomic(sourceId, project, dueAt)
    }

    /** Promotes the exact user-approved action candidate, never the generic source title. */
    private suspend fun audit(type: String, id: Long, action: String, detail: String? = null) = dao.insertAudit(AuditEntryEntity(entityType = type, entityId = id, action = action, detail = detail))

    suspend fun approveAction(sourceId: Long, action: String, project: String, dueAt: Long? = null, assignee: String? = null): Long {
        return dao.approveActionAtomic(sourceId, action, project, dueAt, assignee)
    }

    suspend fun approveEditedAction(point: ActionPointEntity?, sourceId: Long, project: String, action: String, dueAt: Long? = null, assignee: String? = null): Long =
        dao.approveEditedActionAtomic(point, sourceId, project, action, dueAt, assignee)

    /** Marks a source handled without creating a task ("Not mine" / "Not an action"). Nothing is deleted. */
    suspend fun dismissSource(sourceId: Long, reason: String) {
        val source = dao.source(sourceId) ?: return
        dao.updateSource(source.transitionTo(com.hoddesk.app.data.SourceLifecycle.COMPLETED).copy(reviewed = true, triageBucket = if (reason == "not_mine") "NOT_MINE" else source.triageBucket))
        audit("source", sourceId, "dismissed_$reason")
    }

    suspend fun setTaskDue(taskId: Long, dueAt: Long?, providerAlreadyUpdated: Boolean = false) {
        dao.setTaskDueAtomic(taskId, dueAt, providerAlreadyUpdated)
    }

    suspend fun setAssignee(taskId: Long, assignee: String?) {
        dao.setAssigneeAtomic(taskId, assignee)
    }

    suspend fun createSecretaryTask(title: String, detail: String): Long = dao.createSecretaryTaskAtomic(title, detail)
    suspend fun delegateSecretaryTask(expected: TaskEntity, assignee: String, detail: String) = dao.delegateSecretaryTaskAtomic(expected, assignee, detail)
    suspend fun moveSecretaryEvent(expected: EventEntity, startsAt: Long, detail: String): Long = dao.moveSecretaryEventAtomic(expected, startsAt, detail)
    suspend fun scheduleSecretaryTask(expected: TaskEntity, startsAt: Long, detail: String): Long = dao.scheduleSecretaryTaskAtomic(expected, startsAt, detail)

    fun observeAllAudit(limit: Int = 500): Flow<List<AuditEntryEntity>> = dao.observeAllAudit(limit)

    suspend fun deleteAllWorkData() { dao.deleteAllWorkData(); audit("app", 0, "all_data_deleted") }
    suspend fun setActionPointStatus(sourceId: Long, action: String, status: String) {
        dao.setActionPointStatusAtomic(sourceId, action, status)
    }
    suspend fun updateActionPoint(point: ActionPointEntity) {
        dao.updateActionPointAtomic(point)
    }
    suspend fun refreshGeneratedCandidates(source: SourceEntity) {
        if (source.reviewed || !com.hoddesk.app.semantic.SourceIndexPolicy.searchable(source)) return
        val evidence = com.hoddesk.app.reasoning.EvidenceBundleBuilder.originalText(source.body)
        val revision = "conservative-extraction-v3:" + com.hoddesk.app.semantic.SourceFingerprint.of(source)
        if (dao.extractionRevision(source.id) == revision) return
        val proposed = com.hoddesk.app.imports.ActionPointExtractor().extract(evidence).map { it.toEntity(source.id) }
        dao.refreshGeneratedCandidates(source.id, source.body, revision, proposed)
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
        return dao.confirmLocalScheduleAtomic(proposal.event)
    }

    suspend fun returnSourceToInbox(sourceId: Long) {
        val source = dao.source(sourceId) ?: return
        dao.updateSource(source.transitionTo(com.hoddesk.app.data.SourceLifecycle.REVIEW).copy(reviewed = false))
        audit("source", sourceId, "returned_to_inbox")
    }

    /** Creates a durable local pending record before an external calendar write. */
    suspend fun beginCalendarWrite(event: EventEntity): Long = dao.beginCalendarWriteAtomic(event)

    suspend fun finishCalendarWrite(localEventId: Long, providerEventId: Long) {
        dao.finishCalendarWriteAtomic(localEventId, providerEventId)
    }

    suspend fun cancelCalendarWrite(localEventId: Long) { dao.deleteEvent(localEventId) }

    suspend fun reconcilePendingCalendarEvents(findProviderId: suspend (EventEntity) -> Long?) {
        dao.pendingCalendarEvents().forEach { pending ->
            val providerId = findProviderId(pending) ?: return@forEach
            finishCalendarWrite(pending.id, providerId)
        }
    }
    suspend fun schedulePlannedTask(taskId: Long, title: String, startsAt: Long, endsAt: Long): Long {
        return dao.confirmLocalScheduleAtomic(EventEntity(taskId = taskId, title = title, startsAt = startsAt, endsAt = endsAt))
    }

    suspend fun completeTask(taskId: Long) {
        dao.setTaskDoneAtomic(taskId, true)
    }

    suspend fun setTaskDone(taskId: Long, done: Boolean) {
        dao.setTaskDoneAtomic(taskId, done)
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
            ambiguitiesJson = JSONArray((output.ambiguities + output.conflicts.map { "CONFLICT: $it" }).distinct()).toString(),
            citationsJson = JSONArray(output.citations).toString(),
            confidence = output.confidence,
            fieldConfidenceJson = com.hoddesk.app.reasoning.FieldEvidenceAttribution.confidenceJson(output.fieldConfidence.mapValues { com.hoddesk.app.reasoning.FieldEvidence(it.value, output.fieldCitations[it.key], if (it.value >= .8f) "verified" else "ambiguous") }),
            fieldCitationsJson = FieldEvidenceAttribution.stringMapJson(output.fieldCitations),
            intentKind = output.intentKind
        ))
        audit("source", sourceId, "gemma_reasoning_recorded", "model=$modelVersion;sourceIds=${output.sourceIds.joinToString(",")};citations=${output.citations.size};conflicts=${output.conflicts.size};confidence=${output.confidence}")
    }
    suspend fun deleteSource(id: Long) { audit("source", id, "deleted"); dao.source(id)?.let { dao.updateSource(it.transitionTo(com.hoddesk.app.data.SourceLifecycle.DELETED)) }; dao.deleteSourceCascade(id) }
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

    suspend fun recordMatterDecision(sourceId: Long, decision: String) {
        require(decision in setOf("create_new", "update_existing", "keep_separate"))
        audit("source", sourceId, "matter_$decision")
    }

    /**
     * Keeps the in-app itinerary in sync with task due times. This is local and
     * idempotent; writing to an Android provider calendar still requires the
     * separate explicit confirmation flow.
     */
    private suspend fun ensureLocalTaskEvent(taskId: Long, title: String, dueAt: Long?) {
        if (dueAt == null) return
        // Backfill repairs a missing representation only. It must never move an
        // explicitly planned event or erase its external calendar identity.
        if (dao.eventsForTask(taskId).isNotEmpty()) return
        dao.insertEvent(EventEntity(taskId = taskId, title = title, startsAt = dueAt, endsAt = dueAt + 60 * 60 * 1000L, confirmed = true))
    }

    suspend fun removePendingAudioPlaceholders(): Int = dao.deletePendingAudioPlaceholderTasks()

    suspend fun backfillTaskEvents() {
        dao.tasksWithDueTimes().forEach { task -> ensureLocalTaskEvent(task.id, task.title, task.dueAt) }
    }
}

/** Canonical extraction contract shared by document, OCR, audio and message adapters. */
data class ActionExtractionResult(
    val action: String,
    val dateText: String? = null,
    val timeText: String? = null,
    val locationText: String? = null,
    val peopleText: String? = null,
    val evidence: String,
    val confidence: Float,
    val ambiguity: String? = null,
    val category: String = "official_work",
    val ownerText: String? = null,
    val referenceNumber: String? = null,
    val recurrenceRule: String? = null,
    val intentKind: String = "task",
    val fieldConfidenceJson: String = "{}",
    val fieldCitationsJson: String = "{}",
    val conflictJson: String = "{}"
) {
    fun toEntity(sourceId: Long) = ActionPointEntity(sourceId = sourceId, action = action, category = category, dateText = dateText, timeText = timeText, locationText = locationText, peopleText = peopleText, evidence = evidence, confidence = confidence, status = if (ambiguity.isNullOrBlank()) "needs_review" else "needs_confirmation", ambiguity = ambiguity, ownerText = ownerText, referenceNumber = referenceNumber, recurrenceRule = recurrenceRule, intentKind = intentKind, fieldConfidenceJson = fieldConfidenceJson, fieldCitationsJson = fieldCitationsJson, conflictJson = conflictJson)
}

/** Compatibility name for existing adapter and importer call sites. */
typealias ActionPointDraft = ActionExtractionResult
