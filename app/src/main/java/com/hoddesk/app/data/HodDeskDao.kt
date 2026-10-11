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
    @Query("SELECT * FROM tasks WHERE dueAt IS NOT NULL AND status != 'deleted'") suspend fun tasksWithDueTimes(): List<TaskEntity>
    @Query("SELECT * FROM evidence WHERE taskId = :taskId ORDER BY capturedAt DESC") fun observeEvidence(taskId: Long): Flow<List<EvidenceEntity>>
    @Query("SELECT * FROM semantic_embeddings WHERE sourceId = :sourceId LIMIT 1") suspend fun semanticEmbedding(sourceId: Long): SemanticEmbeddingEntity?
    @Query("SELECT * FROM semantic_embeddings") suspend fun allSemanticEmbeddings(): List<SemanticEmbeddingEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSemanticEmbedding(embedding: SemanticEmbeddingEntity)
    @Query("DELETE FROM source_chunks WHERE sourceId = :sourceId") suspend fun deleteSourceChunks(sourceId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSourceChunks(chunks: List<SourceChunkEntity>)
    @Query("SELECT * FROM source_chunks WHERE sourceId = :sourceId ORDER BY ordinal") suspend fun sourceChunks(sourceId: Long): List<SourceChunkEntity>
    @Query("SELECT * FROM source_chunks WHERE modelVersion = :modelVersion") suspend fun allSourceChunks(modelVersion: String): List<SourceChunkEntity>
    @Query("DELETE FROM source_relations") suspend fun clearSourceRelations()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSourceRelations(relations: List<SourceRelationEntity>)
    @Query("SELECT * FROM source_relations WHERE sourceId = :sourceId OR relatedSourceId = :sourceId ORDER BY score DESC") fun observeRelations(sourceId: Long): Flow<List<SourceRelationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertActionPoints(points: List<ActionPointEntity>)
    @Update suspend fun updateActionPoint(point: ActionPointEntity)
    @Query("SELECT * FROM action_points WHERE sourceId = :sourceId ORDER BY confidence DESC") fun observeActionPoints(sourceId: Long): Flow<List<ActionPointEntity>>
    @Query("SELECT * FROM action_points WHERE sourceId = :sourceId ORDER BY confidence DESC") suspend fun actionPoints(sourceId: Long): List<ActionPointEntity>
    @Query("SELECT * FROM action_points WHERE id = :id LIMIT 1") suspend fun actionPoint(id: Long): ActionPointEntity?
    @Query("UPDATE action_points SET status = :status WHERE sourceId = :sourceId AND action = :action") suspend fun setActionPointStatus(sourceId: Long, action: String, status: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReasoningRun(run: ReasoningRunEntity): Long
    @Insert suspend fun insertAudit(entry: AuditEntryEntity): Long
    @Query("SELECT * FROM audit_entries WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC") fun observeAudit(entityType: String, entityId: Long): Flow<List<AuditEntryEntity>>
    @Query("DELETE FROM audit_entries WHERE createdAt < :before") suspend fun deleteAuditBefore(before: Long): Int
    @Query("SELECT * FROM audit_entries ORDER BY createdAt DESC LIMIT :limit") fun observeAllAudit(limit: Int): Flow<List<AuditEntryEntity>>
    @Query("SELECT * FROM audit_entries ORDER BY createdAt ASC") suspend fun allAudit(): List<AuditEntryEntity>
    @Insert suspend fun insertLearningCorrection(correction: LearningCorrectionEntity): Long
    @Query("SELECT * FROM learning_corrections WHERE actionPointId = :actionPointId ORDER BY createdAt DESC") suspend fun learningCorrections(actionPointId: Long): List<LearningCorrectionEntity>
    @Query("SELECT COUNT(*) FROM learning_corrections") fun observeLearningCorrectionCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCommitment(commitment: CommitmentEntity): Long
    @Query("SELECT * FROM commitments WHERE taskId = :taskId LIMIT 1") suspend fun commitmentForTask(taskId: Long): CommitmentEntity?
    @Query("SELECT * FROM commitments WHERE status = 'open' AND direction = 'OWED_BY_ME' ORDER BY dueAt") suspend fun pendingCommitments(): List<CommitmentEntity>
    @Query("SELECT * FROM commitments WHERE status = 'open' AND direction = 'OWED_TO_ME' ORDER BY dueAt") suspend fun waitingCommitments(): List<CommitmentEntity>
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
        approvedTask(sourceId, normalized)?.let { return it.id }
        val taskId = insertTask(TaskEntity(sourceId = sourceId, title = normalized, project = project, dueAt = dueAt, assignee = assignee?.takeIf { it.isNotBlank() }))
        if (dueAt != null) insertEvent(EventEntity(taskId = taskId, title = normalized, startsAt = dueAt, endsAt = dueAt + 60 * 60 * 1000L, confirmed = true))
        setActionPointStatus(sourceId, normalized, "approved")
        updateSource(source.copy(reviewed = true))
        insertAudit(AuditEntryEntity(entityType = "source", entityId = sourceId, action = "action_approved", detail = normalized))
        insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = "created_from_action", detail = "source=$sourceId"))
        if (!assignee.isNullOrBlank()) insertAudit(AuditEntryEntity(entityType = "task", entityId = taskId, action = "delegated", detail = assignee))
        return taskId
    }

    @Transaction
    suspend fun deleteSourceCascade(sourceId: Long) {
        deleteEvidenceForSource(sourceId)
        deleteEventsForSource(sourceId)
        deleteTasksForSource(sourceId)
        deleteCommitmentsForSource(sourceId)
        deleteSemanticEmbedding(sourceId)
        deleteSourceChunksForSource(sourceId)
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
    @Query("DELETE FROM action_points") suspend fun deleteAllActionPoints()
    @Query("DELETE FROM reasoning_runs") suspend fun deleteAllReasoningRuns()

    /** User-confirmed "Delete All Data". The audit trail is append-only and is kept. */
    @Transaction
    suspend fun deleteAllWorkData() {
        deleteAllEvidence(); deleteAllEvents(); deleteAllTasks(); deleteAllCommitments(); deleteAllEmbeddings(); deleteAllChunks()
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
