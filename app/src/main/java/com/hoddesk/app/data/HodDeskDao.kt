package com.hoddesk.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HodDeskDao {
    @Query("SELECT * FROM sources ORDER BY capturedAt DESC") fun observeSources(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM sources ORDER BY capturedAt DESC") suspend fun sourcesOnce(): List<SourceEntity>
    @Query("SELECT * FROM tasks ORDER BY CASE WHEN status = 'open' THEN 0 ELSE 1 END, dueAt") fun observeTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM events WHERE startsAt < :end AND endsAt > :start ORDER BY startsAt") suspend fun overlappingEvents(start: Long, end: Long): List<EventEntity>
    @Query("SELECT * FROM events WHERE startsAt >= :start AND startsAt < :end ORDER BY startsAt") fun observeEvents(start: Long, end: Long): Flow<List<EventEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSource(source: SourceEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTask(task: TaskEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: EventEntity): Long
    @Update suspend fun updateEvent(event: EventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvidence(evidence: EvidenceEntity): Long
    @Update suspend fun updateSource(source: SourceEntity)
    @Update suspend fun updateTask(task: TaskEntity)
    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt WHERE id = :id") suspend fun setTaskStatus(id: Long, status: String, completedAt: Long?)
    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1") suspend fun source(id: Long): SourceEntity?
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1") suspend fun task(id: Long): TaskEntity?
    @Query("SELECT * FROM events WHERE id = :id LIMIT 1") suspend fun event(id: Long): EventEntity?
    @Query("SELECT * FROM events WHERE taskId = :taskId ORDER BY id") suspend fun eventsForTask(taskId: Long): List<EventEntity>
    @Query("SELECT * FROM tasks WHERE sourceId = :sourceId AND title = :title AND status != 'deleted' ORDER BY id LIMIT 1") suspend fun approvedTask(sourceId: Long, title: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE approvalOperationId = :operationId AND status != 'deleted' LIMIT 1") suspend fun taskByApprovalOperation(operationId: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE dueAt IS NOT NULL AND status NOT IN ('deleted', 'archived', 'completed')") suspend fun tasksWithDueTimes(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE sourceId IS NULL AND project = 'Secretary' AND title = :title AND status NOT IN ('deleted', 'archived', 'completed') ORDER BY id") suspend fun activeSecretaryTasks(title: String): List<TaskEntity>
    @Query("SELECT * FROM evidence WHERE taskId = :taskId ORDER BY capturedAt DESC") fun observeEvidence(taskId: Long): Flow<List<EvidenceEntity>>
    @Query("SELECT * FROM semantic_embeddings WHERE sourceId = :sourceId LIMIT 1") suspend fun semanticEmbedding(sourceId: Long): SemanticEmbeddingEntity?
    @Query("SELECT * FROM semantic_embeddings") suspend fun allSemanticEmbeddings(): List<SemanticEmbeddingEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSemanticEmbedding(embedding: SemanticEmbeddingEntity)
    @Query("DELETE FROM semantic_embeddings_staging") suspend fun clearStagedEmbeddings()
    @Query("DELETE FROM source_chunks_staging") suspend fun clearStagedChunks()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStagedEmbedding(embedding: SemanticEmbeddingStagingEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStagedChunks(chunks: List<SourceChunkStagingEntity>)
    @Transaction
    suspend fun replaceStagedSourceIndex(embedding: SemanticEmbeddingStagingEntity, chunks: List<SourceChunkStagingEntity>) {
        clearStagedSource(embedding.sourceId)
        upsertStagedEmbedding(embedding)
        if (chunks.isNotEmpty()) insertStagedChunks(chunks)
    }
    @Query("DELETE FROM semantic_embeddings_staging WHERE sourceId = :sourceId") suspend fun deleteStagedEmbedding(sourceId: Long)
    @Query("DELETE FROM source_chunks_staging WHERE sourceId = :sourceId") suspend fun deleteStagedChunks(sourceId: Long)
    @Transaction
    suspend fun clearStagedSource(sourceId: Long) { deleteStagedEmbedding(sourceId); deleteStagedChunks(sourceId) }
    @Query("SELECT * FROM semantic_embeddings_staging") suspend fun allStagedEmbeddings(): List<SemanticEmbeddingStagingEntity>
    @Query("SELECT * FROM source_chunks_staging ORDER BY sourceId, ordinal") suspend fun allStagedChunks(): List<SourceChunkStagingEntity>
    @Query("SELECT * FROM source_chunks_staging WHERE sourceId = :sourceId ORDER BY ordinal") suspend fun stagedSourceChunks(sourceId: Long): List<SourceChunkStagingEntity>
    @Transaction
    suspend fun promoteStagedSemanticIndex() {
        val stagedEmbeddings = allStagedEmbeddings()
        val stagedChunks = allStagedChunks()
        deleteAllEmbeddings()
        deleteAllChunks()
        stagedEmbeddings.forEach { e -> upsertSemanticEmbedding(SemanticEmbeddingEntity(e.sourceId, e.modelVersion, e.dimension, e.vector, e.modality, e.evidenceLocator, e.indexedAt, e.sourceFingerprint, e.promptVersion, e.chunkNumber)) }
        if (stagedChunks.isNotEmpty()) insertSourceChunks(stagedChunks.map { c -> SourceChunkEntity(c.id, c.sourceId, c.ordinal, c.modality, c.text, c.citation, c.page, c.paragraphIndex, c.messageIndex, c.startMs, c.endMs, c.modelVersion, c.dimension, c.vector, c.indexedAt, c.sourceFingerprint, c.promptVersion, c.chunkNumber) })
        clearStagedEmbeddings()
        clearStagedChunks()
    }
    @Transaction
    suspend fun promoteStagedSemanticIndex(expectedSources: List<SourceEntity>) {
        val live = sourcesOnce().filter(com.hoddesk.app.semantic.SourceIndexPolicy::searchable).associateBy { it.id }
        check(live.keys == expectedSources.map { it.id }.toSet() && expectedSources.all {
            live[it.id]?.let { current -> com.hoddesk.app.semantic.SourceFingerprint.of(current) == com.hoddesk.app.semantic.SourceFingerprint.of(it) } == true
        }) { "Sources changed during index rebuild; the active index was preserved" }
        promoteStagedSemanticIndex()
    }
    @Query("DELETE FROM source_chunks WHERE sourceId = :sourceId") suspend fun deleteSourceChunks(sourceId: Long)
    @Transaction
    suspend fun replaceSourceIndex(embedding: SemanticEmbeddingEntity, chunks: List<SourceChunkEntity>) {
        deleteSemanticEmbedding(embedding.sourceId)
        deleteSourceChunks(embedding.sourceId)
        upsertSemanticEmbedding(embedding)
        if (chunks.isNotEmpty()) insertSourceChunks(chunks)
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSourceChunks(chunks: List<SourceChunkEntity>)
    @Query("SELECT * FROM source_chunks WHERE sourceId = :sourceId ORDER BY ordinal") suspend fun sourceChunks(sourceId: Long): List<SourceChunkEntity>
    @Query("SELECT * FROM source_chunks WHERE modelVersion = :modelVersion") suspend fun allSourceChunks(modelVersion: String): List<SourceChunkEntity>
    @Query("DELETE FROM source_relations") suspend fun clearSourceRelations()
    @Query("SELECT * FROM source_relations") suspend fun allSourceRelations(): List<SourceRelationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSourceRelations(relations: List<SourceRelationEntity>)
    @Transaction
    suspend fun replaceSourceRelations(relations: List<SourceRelationEntity>, audits: List<AuditEntryEntity>) {
        clearSourceRelations()
        if (relations.isNotEmpty()) insertSourceRelations(relations)
        audits.forEach { insertAudit(it) }
    }
    @Query("SELECT * FROM source_relations WHERE sourceId = :sourceId OR relatedSourceId = :sourceId ORDER BY score DESC") fun observeRelations(sourceId: Long): Flow<List<SourceRelationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertActionPoints(points: List<ActionPointEntity>)
    @Update suspend fun updateActionPoint(point: ActionPointEntity)
    @Query("SELECT * FROM action_points WHERE sourceId = :sourceId ORDER BY confidence DESC") fun observeActionPoints(sourceId: Long): Flow<List<ActionPointEntity>>
    @Query("SELECT * FROM action_points WHERE sourceId = :sourceId ORDER BY confidence DESC") suspend fun actionPoints(sourceId: Long): List<ActionPointEntity>
    @Query("SELECT * FROM action_points WHERE id = :id LIMIT 1") suspend fun actionPoint(id: Long): ActionPointEntity?
    @Query("SELECT * FROM action_points WHERE sourceId = :sourceId AND action = :action LIMIT 1") suspend fun actionPointBySourceAndAction(sourceId: Long, action: String): ActionPointEntity?
    @Query("UPDATE action_points SET status = :status WHERE sourceId = :sourceId AND action = :action") suspend fun setActionPointStatus(sourceId: Long, action: String, status: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReasoningRun(run: ReasoningRunEntity): Long
    @Insert suspend fun insertAudit(entry: AuditEntryEntity): Long
    @Insert suspend fun insertPerformanceMetric(metric: PerformanceMetricEntity): Long
    @Query("SELECT * FROM performance_metrics ORDER BY createdAt DESC LIMIT :limit") suspend fun recentPerformanceMetrics(limit: Int = 500): List<PerformanceMetricEntity>
    @Query("DELETE FROM performance_metrics WHERE createdAt < :before") suspend fun deletePerformanceMetricsBefore(before: Long): Int
    @Query("SELECT * FROM audit_entries WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC") fun observeAudit(entityType: String, entityId: Long): Flow<List<AuditEntryEntity>>
    @Query("DELETE FROM audit_entries WHERE createdAt < :before") suspend fun deleteAuditBefore(before: Long): Int
    @Query("SELECT * FROM audit_entries ORDER BY createdAt DESC LIMIT :limit") fun observeAllAudit(limit: Int): Flow<List<AuditEntryEntity>>
    @Query("SELECT * FROM audit_entries ORDER BY createdAt ASC") suspend fun allAudit(): List<AuditEntryEntity>
    @Query("SELECT COUNT(*) FROM audit_entries WHERE entityType = :type AND entityId = :id AND action = :action") suspend fun auditCount(type: String, id: Long, action: String): Int
    @Query("SELECT detail FROM audit_entries WHERE entityType = 'source' AND entityId = :sourceId AND action = 'extraction_refreshed' ORDER BY id DESC LIMIT 1") suspend fun extractionRevision(sourceId: Long): String?
    @Query("DELETE FROM action_points WHERE id = :id") suspend fun deleteActionPoint(id: Long)
    @Insert suspend fun insertLearningCorrection(correction: LearningCorrectionEntity): Long
    @Query("SELECT * FROM learning_corrections WHERE actionPointId = :actionPointId ORDER BY createdAt DESC") suspend fun learningCorrections(actionPointId: Long): List<LearningCorrectionEntity>
    @Query("SELECT * FROM learning_corrections ORDER BY createdAt DESC") suspend fun allLearningCorrections(): List<LearningCorrectionEntity>
    @Query("SELECT COUNT(*) FROM learning_corrections") fun observeLearningCorrectionCount(): Flow<Int>
    @Query("DELETE FROM learning_corrections") suspend fun deleteAllLearningCorrections()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCommitment(commitment: CommitmentEntity): Long
    @Query("SELECT * FROM commitments WHERE taskId = :taskId LIMIT 1") suspend fun commitmentForTask(taskId: Long): CommitmentEntity?
    @Query("SELECT * FROM commitments WHERE status = 'open' AND direction = 'OWED_BY_ME' ORDER BY dueAt") suspend fun pendingCommitments(): List<CommitmentEntity>
    @Query("SELECT * FROM commitments WHERE status = 'open' AND direction = 'OWED_TO_ME' ORDER BY dueAt") suspend fun waitingCommitments(): List<CommitmentEntity>
    @Query("SELECT * FROM commitments ORDER BY updatedAt DESC") fun observeCommitments(): Flow<List<CommitmentEntity>>
    @Query("SELECT * FROM commitments WHERE status = 'open' AND dueAt IS NOT NULL AND dueAt < :now ORDER BY dueAt") suspend fun overdueCommitments(now: Long): List<CommitmentEntity>
    @Query("UPDATE commitments SET status = :status, updatedAt = :now WHERE taskId = :taskId") suspend fun setCommitmentStatus(taskId: Long, status: String, now: Long = System.currentTimeMillis())
    @Query("DELETE FROM commitments WHERE taskId = :taskId") suspend fun deleteCommitmentForTask(taskId: Long)
    @Query("DELETE FROM commitments WHERE sourceId = :sourceId") suspend fun deleteCommitmentsForSource(sourceId: Long)
    @Query("DELETE FROM commitments") suspend fun deleteAllCommitments()
    @Query("SELECT * FROM events WHERE startsAt >= :start AND startsAt < :end ORDER BY startsAt") suspend fun eventsBetween(start: Long, end: Long): List<EventEntity>
    @Query("SELECT * FROM events WHERE confirmed = 0 ORDER BY id") suspend fun pendingCalendarEvents(): List<EventEntity>
    @Query("SELECT * FROM reasoning_runs WHERE sourceId = :sourceId ORDER BY createdAt DESC") fun observeReasoningRuns(sourceId: Long): Flow<List<ReasoningRunEntity>>
    @Query("DELETE FROM sources WHERE id = :id") suspend fun deleteSourceRow(id: Long)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTaskRow(id: Long)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: Long)
    @Query("DELETE FROM semantic_embeddings WHERE sourceId = :sourceId") suspend fun deleteSemanticEmbedding(sourceId: Long)
    @Query("DELETE FROM source_chunks WHERE sourceId = :sourceId") suspend fun deleteSourceChunksForSource(sourceId: Long)
    @Query("DELETE FROM source_relations WHERE sourceId = :sourceId OR relatedSourceId = :sourceId") suspend fun deleteSourceRelationsForSource(sourceId: Long)
    @Query("DELETE FROM action_points WHERE sourceId = :sourceId") suspend fun deleteActionPointsForSource(sourceId: Long)
    @Query("DELETE FROM reasoning_runs WHERE sourceId = :sourceId") suspend fun deleteReasoningRunsForSource(sourceId: Long)
    @Query("DELETE FROM evidence WHERE taskId IN (SELECT id FROM tasks WHERE sourceId = :sourceId)") suspend fun deleteEvidenceForSource(sourceId: Long)
    @Query("DELETE FROM events WHERE taskId IN (SELECT id FROM tasks WHERE sourceId = :sourceId)") suspend fun deleteEventsForSource(sourceId: Long)
    @Query("DELETE FROM tasks WHERE sourceId = :sourceId") suspend fun deleteTasksForSource(sourceId: Long)
    @Query("DELETE FROM evidence WHERE taskId = :taskId") suspend fun deleteEvidenceForTask(taskId: Long)
    @Query("DELETE FROM events WHERE taskId = :taskId") suspend fun deleteEventsForTask(taskId: Long)
    /** Expired sources with approved tasks remain as durable task evidence. */
    @Query("SELECT id FROM sources WHERE capturedAt < :before AND id NOT IN (SELECT DISTINCT sourceId FROM tasks WHERE sourceId IS NOT NULL)") suspend fun expiredUnapprovedSourceIds(before: Long): List<Long>

    @Transaction
    suspend fun purgeExpiredUnapprovedSources(before: Long): Int {
        val ids = expiredUnapprovedSourceIds(before)
        ids.forEach { deleteSourceCascade(it) }
        return ids.size
    }

    /**
     * Promotes one reviewed action as a single durable state transition.
     * The UI may deliver the same tap twice; returning the existing task makes
     * the operation idempotent instead of creating duplicate official work.
     */
    @Transaction
    suspend fun approveActionAtomic(sourceId: Long, action: String, project: String, dueAt: Long?, assignee: String?): Long {
        val normalized = action.trim()
        require(normalized.isNotBlank()) { "Approved action cannot be blank" }
        val source = requireNotNull(source(sourceId)) { "Source $sourceId does not exist" }
        require(source.sensitivity !in setOf("T3", "T4", "clinical")) { "Clinical content must be reviewed in the Clinical Vault" }
        val candidate = requireNotNull(actionPointBySourceAndAction(sourceId, normalized)) { "No persisted candidate matches this action; edit and confirm a manual action first" }
        require(dueAt == null || candidate.ambiguity.isNullOrBlank()) { "Resolve the candidate's source warnings before scheduling" }
        val operationId = "approve:$sourceId:${normalized.trim().lowercase().replace(Regex("\\s+"), " ")}"
        approvedTask(sourceId, normalized)?.let {
            setActionPointStatus(sourceId, normalized, "approved")
            refreshSourceReviewState(sourceId)
            source(sourceId)?.let { current -> if (SourceLifecycle.parse(current.lifecycleState) != SourceLifecycle.TASK_EVENT) updateSource(current.transitionTo(SourceLifecycle.APPROVED).transitionTo(SourceLifecycle.TASK_EVENT)) }
            syncTaskCommitmentAtomic(it.id)
            return it.id
        }
        val taskId = insertTask(TaskEntity(sourceId = sourceId, title = normalized, project = project, dueAt = dueAt, assignee = assignee?.takeIf { it.isNotBlank() }, approvalOperationId = operationId))
        if (dueAt != null) insertEvent(EventEntity(taskId = taskId, title = normalized, startsAt = dueAt, endsAt = dueAt + 60 * 60 * 1000L, confirmed = true))
        setActionPointStatus(sourceId, normalized, "approved")
        refreshSourceReviewState(sourceId)
        source(sourceId)?.let { current -> if (SourceLifecycle.parse(current.lifecycleState) != SourceLifecycle.TASK_EVENT) updateSource(current.transitionTo(SourceLifecycle.APPROVED).transitionTo(SourceLifecycle.TASK_EVENT)) }
        syncTaskCommitmentAtomic(taskId)
        if (!assignee.isNullOrBlank()) actionPointBySourceAndAction(sourceId, normalized)?.let {
            insertLearningCorrection(LearningCorrectionEntity(actionPointId = it.id, field = "delegation", before = null, after = assignee))
        }
        insertAudit(AuditEntryEntity(entityType = "source", entityId = sourceId, action = "action_approved", detail = normalized))
        insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = "created_from_action", detail = "source=$sourceId"))
        if (!assignee.isNullOrBlank()) insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = "delegated", detail = assignee))
        return taskId
    }

    /** Source completion follows all persisted candidates, never the first tap. */
    @Transaction
    suspend fun refreshSourceReviewState(sourceId: Long) {
        val current = source(sourceId) ?: return
        val remaining = actionPoints(sourceId).any { it.status !in setOf("approved", "rejected", "dismissed") }
        if (!remaining) {
            val state = SourceLifecycle.parse(current.lifecycleState)
            updateSource(if (state == SourceLifecycle.TASK_EVENT) current.copy(reviewed = true) else current.transitionTo(SourceLifecycle.APPROVED).copy(reviewed = true))
        } else updateSource(current.copy(reviewed = false))
    }

    @Transaction
    suspend fun setActionPointStatusAtomic(sourceId: Long, action: String, status: String) {
        require(status in setOf("needs_review", "needs_confirmation", "needs_clarification", "approved", "rejected", "dismissed")) { "Invalid action status" }
        val point = actionPointBySourceAndAction(sourceId, action) ?: return
        if (point.status == status) return
        setActionPointStatus(sourceId, action, status)
        if (status in setOf("rejected", "approved")) insertLearningCorrection(LearningCorrectionEntity(actionPointId = point.id, field = "decision", before = point.status, after = status))
        refreshSourceReviewState(sourceId)
        insertAudit(AuditEntryEntity(entityType = "source", entityId = sourceId, action = "action_status_$status", detail = action))
    }

    /** An edit, its learning record and approval must commit or roll back together. */
    @Transaction
    suspend fun approveEditedActionAtomic(point: ActionPointEntity?, sourceId: Long, project: String, action: String, dueAt: Long?, assignee: String?): Long {
        val normalizedAction = action.trim()
        require(normalizedAction.isNotBlank()) { "Approved action cannot be blank" }
        if (point != null && point.id != 0L) {
            require(point.sourceId == sourceId) { "Edited candidate belongs to a different source" }
            val previous = requireNotNull(actionPoint(point.id)) { "Action candidate no longer exists" }
            require(previous.sourceId == sourceId) { "Edited candidate belongs to a different source" }
            if (previous.status == "approved") {
                return requireNotNull(approvedTask(sourceId, previous.action)) { "Approved candidate has no task" }.id
            }
            require(previous.status !in setOf("rejected", "dismissed")) { "Action candidate has already been dismissed" }
            updateActionPointAtomic(point.copy(action = normalizedAction))
        } else {
            approvedTask(sourceId, normalizedAction)?.let { return it.id }
            val draft = point?.copy(sourceId = sourceId, action = normalizedAction) ?: ActionPointEntity(sourceId = sourceId, action = normalizedAction, evidence = requireNotNull(source(sourceId)).body, confidence = 1f)
            updateActionPointAtomic(draft)
        }
        return approveActionAtomic(sourceId, normalizedAction, project, dueAt, assignee)
    }

    @Transaction
    suspend fun updateActionPointAtomic(point: ActionPointEntity) {
        require(point.action.isNotBlank()) { "Action cannot be blank" }
        require(point.confidence.isFinite() && point.confidence in 0f..1f) { "Action confidence is invalid" }
        requireNotNull(source(point.sourceId)) { "Action source no longer exists" }
        val normalized = point.copy(
            action = point.action.trim(),
            dateText = point.dateText?.trim()?.takeIf(String::isNotBlank),
            timeText = point.timeText?.trim()?.takeIf(String::isNotBlank),
            locationText = point.locationText?.trim()?.takeIf(String::isNotBlank),
            peopleText = point.peopleText?.trim()?.takeIf(String::isNotBlank),
        )
        if (normalized.id == 0L) {
            require(normalized.status in setOf("needs_review", "needs_confirmation", "needs_clarification")) { "A manual draft must be reviewed before approval" }
            require(actionPointBySourceAndAction(normalized.sourceId, normalized.action) == null) { "An action with this wording already exists. Open that action to edit it." }
            insertActionPoints(listOf(normalized))
            refreshSourceReviewState(normalized.sourceId)
            insertAudit(AuditEntryEntity(entityType = "source", entityId = normalized.sourceId, action = "manual_action_drafted", detail = normalized.action))
            return
        }
        val previous = requireNotNull(actionPoint(normalized.id)) { "Action candidate no longer exists" }
        require(previous.sourceId == normalized.sourceId) { "Action candidate belongs to a different source" }
        require(previous.status !in setOf("approved", "rejected", "dismissed")) { "Action candidate has already been handled" }
        updateActionPoint(normalized)
        listOf(
            "action" to (previous.action to normalized.action),
            "date" to (previous.dateText to normalized.dateText),
            "time" to (previous.timeText to normalized.timeText),
            "location" to (previous.locationText to normalized.locationText),
            "people" to (previous.peopleText to normalized.peopleText),
        ).filter { it.second.first != it.second.second }.forEach { (field, values) ->
            insertLearningCorrection(LearningCorrectionEntity(actionPointId = normalized.id, field = field, before = values.first, after = values.second))
        }
        insertAudit(AuditEntryEntity(entityType = "action_point", entityId = normalized.id, action = "edited", detail = normalized.action))
    }

    /** Refresh generated, unhandled candidates after an extractor fix, preserving all user decisions/edits. */
    @Transaction
    suspend fun refreshGeneratedCandidates(sourceId: Long, expectedBody: String, revision: String, proposed: List<ActionPointEntity>) {
        val live = source(sourceId) ?: return
        if (live.body != expectedBody || live.reviewed || extractionRevision(sourceId) == revision) return
        if (auditCount("source", sourceId, "manual_action_drafted") > 0) return
        val existing = actionPoints(sourceId)
        val pending = existing.filter { it.status in setOf("needs_review", "needs_confirmation") }
        // A whole-source refresh must not erase corrected fields or reverse a reviewer decision.
        if (existing.any { it.status !in setOf("needs_review", "needs_confirmation") } ||
            pending.any { learningCorrections(it.id).isNotEmpty() || auditCount("action_point", it.id, "edited") > 0 }) return
        pending.forEach { deleteActionPoint(it.id) }
        insertActionPoints(proposed)
        insertAudit(AuditEntryEntity(entityType = "source", entityId = sourceId, action = "extraction_refreshed", detail = revision))
    }

    /** Task ownership and commitment are part of the same approval transaction. */
    @Transaction
    suspend fun syncTaskCommitmentAtomic(taskId: Long) {
        val current = task(taskId) ?: return
        val existing = commitmentForTask(taskId)
        insertCommitment(CommitmentEntity(
            id = existing?.id ?: 0,
            sourceId = current.sourceId,
            taskId = current.id,
            direction = if (current.assignee.isNullOrBlank()) "OWED_BY_ME" else "OWED_TO_ME",
            owner = current.assignee?.takeIf { it.isNotBlank() } ?: "me",
            counterparty = if (current.assignee.isNullOrBlank()) null else "me",
            summary = current.title,
            dueAt = current.dueAt,
            status = if (current.status in setOf("completed", "deleted", "archived")) current.status else "open",
            evidence = "Task #${current.id} · ${current.project}",
            confidence = 1f,
            createdAt = existing?.createdAt ?: current.createdAt,
        ))
    }

    /**
     * Promotes a source itself when the reviewer chooses "approve source".
     * Keeping this transition in Room makes a repeated tap idempotent and
     * prevents a task being created while the source remains in Review after
     * a process death.
     */
    @Transaction
    suspend fun approveSourceAtomic(sourceId: Long, project: String, dueAt: Long?): Long {
        val source = requireNotNull(source(sourceId)) { "Source $sourceId does not exist" }
        val title = source.title.trim()
        require(title.isNotBlank()) { "Approved source cannot have a blank title" }
        require(source.sensitivity !in setOf("T3", "T4", "clinical")) { "Clinical content must be reviewed in the Clinical Vault" }
        val operationId = "approve-source:$sourceId:${title.lowercase().replace(Regex("\\s+"), " ")}"
        approvedTask(sourceId, title)?.let { syncTaskCommitmentAtomic(it.id); return it.id }
        val taskId = insertTask(TaskEntity(sourceId = sourceId, title = title, project = project, dueAt = dueAt, approvalOperationId = operationId))
        if (dueAt != null) insertEvent(EventEntity(taskId = taskId, title = title, startsAt = dueAt, endsAt = dueAt + 60 * 60 * 1000L, confirmed = true))
        updateSource(source.transitionTo(SourceLifecycle.APPROVED).transitionTo(SourceLifecycle.TASK_EVENT).copy(reviewed = true))
        syncTaskCommitmentAtomic(taskId)
        insertAudit(AuditEntryEntity(entityType = "source", entityId = sourceId, action = "source_approved", detail = title))
        insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = "created_from_source", detail = "source=$sourceId"))
        return taskId
    }

    /** Re-read conflicts inside the write transaction; proposals may be stale. */
    @Transaction
    suspend fun confirmLocalScheduleAtomic(requested: EventEntity): Long {
        require(requested.endsAt > requested.startsAt) { "Event must end after it starts" }
        val owned = requested.taskId?.let { id ->
            val current = requireNotNull(task(id)) { "Task no longer exists" }
            require(current.status !in setOf("completed", "deleted", "archived")) { "Task is no longer active" }
            current.sourceId?.let { sourceId ->
                require(source(sourceId)?.sensitivity !in setOf("T3", "T4", "clinical")) { "Clinical work cannot enter the ordinary calendar" }
            }
            eventsForTask(id)
        }.orEmpty()
        owned.firstOrNull { it.startsAt == requested.startsAt && it.endsAt == requested.endsAt && it.confirmed }?.let { return it.id }
        require(owned.none { it.calendarEventId != null }) { "Use the confirmed calendar reschedule flow for an exported event" }
        val ownedIds = owned.map { it.id }.toSet()
        require(overlappingEvents(requested.startsAt, requested.endsAt).none { it.id !in ownedIds }) { "A calendar conflict appeared before confirmation" }
        val eventId = insertEvent(requested.copy(id = owned.firstOrNull()?.id ?: 0L, confirmed = true))
        owned.filter { it.id != eventId }.forEach { deleteEvent(it.id) }
        insertAudit(AuditEntryEntity(entityType = "event", entityId = eventId, action = "scheduled_confirmed", detail = requested.title))
        return eventId
    }

    @Transaction
    suspend fun beginCalendarWriteAtomic(requested: EventEntity): Long {
        require(requested.endsAt > requested.startsAt) { "Event must end after it starts" }
        val owned = requested.taskId?.let { id ->
            val current = requireNotNull(task(id)) { "Task no longer exists" }
            require(current.status !in setOf("completed", "deleted", "archived")) { "Task is no longer active" }
            current.sourceId?.let { sourceId ->
                require(source(sourceId)?.sensitivity !in setOf("T3", "T4", "clinical")) { "Clinical work cannot enter the ordinary calendar" }
            }
            eventsForTask(id)
        }.orEmpty()
        owned.firstOrNull { !it.confirmed && it.startsAt == requested.startsAt && it.endsAt == requested.endsAt }?.let { return it.id }
        require(owned.none { it.calendarEventId != null && it.startsAt == requested.startsAt && it.endsAt == requested.endsAt }) { "This task is already exported to calendar" }
        val replaceableIds = owned.filter { it.calendarEventId == null && it.confirmed }.map { it.id }.toSet()
        require(overlappingEvents(requested.startsAt, requested.endsAt).none { it.id !in replaceableIds }) { "A calendar conflict appeared before confirmation" }
        return insertEvent(requested.copy(id = 0, confirmed = false, calendarEventId = null))
    }

    @Transaction
    suspend fun finishCalendarWriteAtomic(localId: Long, providerId: Long) {
        require(providerId > 0) { "Provider returned an invalid calendar event id" }
        val pending = requireNotNull(event(localId)) { "Pending calendar event no longer exists" }
        if (pending.confirmed && pending.calendarEventId == providerId) return
        updateEvent(pending.copy(confirmed = true, calendarEventId = providerId))
        // Replace due-only local representations while retaining real provider links.
        pending.taskId?.let { id -> eventsForTask(id).filter { it.id != localId && it.calendarEventId == null && it.confirmed }.forEach { deleteEvent(it.id) } }
        insertAudit(AuditEntryEntity(entityType = "event", entityId = localId, action = "scheduled_confirmed", detail = pending.title))
    }

    @Transaction
    suspend fun setTaskDueAtomic(taskId: Long, dueAt: Long?, providerAlreadyUpdated: Boolean = false) {
        val current = requireNotNull(task(taskId)) { "Task no longer exists" }
        val existingEvents = eventsForTask(taskId)
        if (dueAt != current.dueAt) require(providerAlreadyUpdated || existingEvents.none { it.calendarEventId != null }) { "Confirm the external calendar change before changing this task's due time" }
        updateTask(current.copy(dueAt = dueAt))
        if (dueAt == null) {
            existingEvents.filter { providerAlreadyUpdated || it.calendarEventId == null }.forEach { deleteEvent(it.id) }
        } else if (existingEvents.isEmpty() && current.status !in setOf("completed", "deleted", "archived")) {
            insertEvent(EventEntity(taskId = taskId, title = current.title, startsAt = dueAt, endsAt = dueAt + 60 * 60_000L, confirmed = true))
        } else {
            existingEvents.filter { it.confirmed }.forEach { event ->
                updateEvent(event.copy(startsAt = dueAt, endsAt = dueAt + (event.endsAt - event.startsAt).coerceAtLeast(60_000L)))
            }
        }
        syncTaskCommitmentAtomic(taskId)
        insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = if (dueAt == null) "due_cleared" else "rescheduled", detail = dueAt?.toString()))
    }

    @Transaction
    suspend fun setTaskDoneAtomic(taskId: Long, done: Boolean) {
        requireNotNull(task(taskId)) { "Task no longer exists" }
        setTaskStatus(taskId, if (done) "completed" else "open", if (done) System.currentTimeMillis() else null)
        syncTaskCommitmentAtomic(taskId)
        insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = if (done) "completed" else "reopened"))
    }

    @Transaction
    suspend fun setAssigneeAtomic(taskId: Long, assignee: String?) {
        val current = requireNotNull(task(taskId)) { "Task no longer exists" }
        val normalized = assignee?.trim()?.takeIf { it.isNotBlank() }
        updateTask(current.copy(assignee = normalized))
        syncTaskCommitmentAtomic(taskId)
        insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = if (normalized == null) "taken_back" else "delegated", detail = normalized))
    }

    @Transaction
    suspend fun createSecretaryTaskAtomic(title: String, detail: String): Long {
        val wording = title.trim()
        require(wording.isNotBlank()) { "Task wording is missing" }
        require(com.hoddesk.app.privacy.SensitivityClassifier.classify(wording, "").tier.code !in setOf("T3", "T4", "clinical")) { "Clinical work belongs in the Clinical Vault" }
        activeSecretaryTasks(wording).singleOrNull()?.let { return it.id }
        require(activeSecretaryTasks(wording).isEmpty()) { "Several matching secretary tasks already exist" }
        val id = insertTask(TaskEntity(title = wording, project = "Secretary"))
        syncTaskCommitmentAtomic(id)
        insertAudit(AuditEntryEntity(entityType = "secretary", entityId = id, action = "secretary_task_created", detail = detail))
        return id
    }

    @Transaction
    suspend fun delegateSecretaryTaskAtomic(expected: TaskEntity, assignee: String, detail: String) {
        val current = requireNotNull(task(expected.id)) { "Task no longer exists" }
        require(current == expected) { "Task changed after review. Review the request again." }
        require(current.status !in setOf("completed", "deleted", "archived")) { "Task is no longer active" }
        current.sourceId?.let { require(source(it)?.sensitivity !in setOf("T3", "T4", "clinical") && source(it) != null) { "Task source is unavailable or clinical" } }
        require(assignee.isNotBlank()) { "A configured team member is required" }
        setAssigneeAtomic(current.id, assignee)
        insertAudit(AuditEntryEntity(entityType = "secretary", entityId = current.id, action = "secretary_task_delegated", detail = detail))
    }

    @Transaction
    suspend fun moveSecretaryEventAtomic(expected: EventEntity, startsAt: Long, detail: String): Long {
        val current = requireNotNull(event(expected.id)) { "Meeting no longer exists" }
        require(current == expected) { "Meeting changed after review. Review the request again." }
        require(current.confirmed) { "Calendar write is still pending; wait before changing this meeting" }
        require(current.calendarEventId == null) { "This meeting is exported. Use its Calendar reschedule confirmation to update the phone calendar first." }
        val related = current.taskId?.let { eventsForTask(it) }.orEmpty()
        require(related.size <= 1 && related.none { it.calendarEventId != null }) { "This task has multiple or exported meetings. Use Calendar to review them individually." }
        val ownedTask = current.taskId?.let { requireNotNull(task(it)) { "Task no longer exists" } }
        ownedTask?.let { task ->
            require(task.status !in setOf("completed", "deleted", "archived")) { "Task is no longer active" }
            task.sourceId?.let { require(source(it)?.sensitivity !in setOf("T3", "T4", "clinical") && source(it) != null) { "Task source is unavailable or clinical" } }
        }
        val duration = current.endsAt - current.startsAt
        require(duration > 0) { "Meeting duration is invalid" }
        val endsAt = Math.addExact(startsAt, duration)
        require(overlappingEvents(startsAt, endsAt).none { it.id != current.id }) { "The proposed time conflicts with another calendar event" }
        updateEvent(current.copy(startsAt = startsAt, endsAt = endsAt))
        ownedTask?.let { updateTask(it.copy(dueAt = startsAt)); syncTaskCommitmentAtomic(it.id) }
        insertAudit(AuditEntryEntity(entityType = "secretary", entityId = current.id, action = "secretary_schedule_changed", detail = detail))
        return current.id
    }

    @Transaction
    suspend fun scheduleSecretaryTaskAtomic(expected: TaskEntity, startsAt: Long, detail: String): Long {
        val current = requireNotNull(task(expected.id)) { "Task no longer exists" }
        require(current == expected) { "Task changed after review. Review the request again." }
        require(current.status !in setOf("completed", "deleted", "archived")) { "Task is no longer active" }
        current.sourceId?.let { require(source(it)?.sensitivity !in setOf("T3", "T4", "clinical") && source(it) != null) { "Task source is unavailable or clinical" } }
        val owned = eventsForTask(current.id)
        require(owned.size <= 1 && owned.none { it.calendarEventId != null || !it.confirmed }) { "This task has multiple, exported or pending meetings. Use Calendar to review them individually." }
        val previous = owned.singleOrNull()
        val duration = previous?.let { it.endsAt - it.startsAt } ?: 60 * 60_000L
        require(duration > 0) { "Meeting duration is invalid" }
        val endsAt = Math.addExact(startsAt, duration)
        require(overlappingEvents(startsAt, endsAt).none { it.id != previous?.id }) { "The proposed time conflicts with another calendar event" }
        val eventId = if (previous == null) insertEvent(EventEntity(taskId = current.id, title = current.title, startsAt = startsAt, endsAt = endsAt, confirmed = true)) else {
            updateEvent(previous.copy(startsAt = startsAt, endsAt = endsAt)); previous.id
        }
        updateTask(current.copy(dueAt = startsAt))
        syncTaskCommitmentAtomic(current.id)
        insertAudit(AuditEntryEntity(entityType = "secretary", entityId = eventId, action = "secretary_task_scheduled", detail = detail))
        return eventId
    }

    @Transaction
    suspend fun deleteSourceCascade(sourceId: Long) {
        deleteEvidenceForSource(sourceId)
        deleteEventsForSource(sourceId)
        deleteTasksForSource(sourceId)
        deleteCommitmentsForSource(sourceId)
        deleteSemanticEmbedding(sourceId)
        deleteSourceChunksForSource(sourceId)
        clearStagedSource(sourceId)
        deleteSourceRelationsForSource(sourceId)
        deleteActionPointsForSource(sourceId)
        deleteReasoningRunsForSource(sourceId)
        deleteSourceRow(sourceId)
    }

    @Query("DELETE FROM sources") suspend fun deleteAllSources()
    @Query("DELETE FROM tasks") suspend fun deleteAllTasks()
    @Query("DELETE FROM events") suspend fun deleteAllEvents()
    @Query("DELETE FROM evidence") suspend fun deleteAllEvidence()
    @Query("DELETE FROM semantic_embeddings") suspend fun deleteAllEmbeddings()
    @Query("DELETE FROM source_chunks") suspend fun deleteAllChunks()
    @Transaction
    suspend fun replaceSemanticIndex(embeddings: List<SemanticEmbeddingEntity>, chunks: List<SourceChunkEntity>) {
        deleteAllEmbeddings()
        deleteAllChunks()
        if (embeddings.isNotEmpty()) embeddings.forEach { upsertSemanticEmbedding(it) }
        if (chunks.isNotEmpty()) insertSourceChunks(chunks)
    }
    @Query("DELETE FROM action_points") suspend fun deleteAllActionPoints()
    @Query("DELETE FROM reasoning_runs") suspend fun deleteAllReasoningRuns()

    /** User-confirmed "Delete All Data". The audit trail is append-only and is kept. */
    @Transaction
    suspend fun deleteAllWorkData() {
        deleteAllEvidence(); deleteAllEvents(); deleteAllTasks(); deleteAllCommitments(); deleteAllEmbeddings(); deleteAllChunks()
        clearStagedEmbeddings(); clearStagedChunks(); deleteAllLearningCorrections()
        clearSourceRelations(); deleteAllActionPoints(); deleteAllReasoningRuns(); deleteAllSources()
    }

    @Transaction
    suspend fun deleteTaskCascade(taskId: Long) {
        deleteEvidenceForTask(taskId)
        deleteEventsForTask(taskId)
        deleteTaskRow(taskId)
        deleteCommitmentForTask(taskId)
    }

    /** Removes tasks accidentally created from the audio-failure diagnostic text. */
    @Query("DELETE FROM tasks WHERE status = 'open' AND title LIKE 'Audio is queued for the approved offline transcription runtime%'")
    suspend fun deletePendingAudioPlaceholderTasks(): Int
}
