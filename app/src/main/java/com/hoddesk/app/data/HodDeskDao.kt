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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvidence(evidence: EvidenceEntity): Long
    @Update suspend fun updateSource(source: SourceEntity)
    @Update suspend fun updateTask(task: TaskEntity)
    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt WHERE id = :id") suspend fun setTaskStatus(id: Long, status: String, completedAt: Long?)
    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1") suspend fun source(id: Long): SourceEntity?
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1") suspend fun task(id: Long): TaskEntity?
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
    @Query("SELECT * FROM action_points WHERE sourceId = :sourceId ORDER BY confidence DESC") fun observeActionPoints(sourceId: Long): Flow<List<ActionPointEntity>>
    @Query("UPDATE action_points SET status = :status WHERE sourceId = :sourceId AND action = :action") suspend fun setActionPointStatus(sourceId: Long, action: String, status: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReasoningRun(run: ReasoningRunEntity): Long
    @Insert suspend fun insertAudit(entry: AuditEntryEntity): Long
    @Query("SELECT * FROM audit_entries WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC") fun observeAudit(entityType: String, entityId: Long): Flow<List<AuditEntryEntity>>
    @Query("DELETE FROM audit_entries WHERE createdAt < :before") suspend fun deleteAuditBefore(before: Long): Int
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

    @Transaction
    suspend fun deleteSourceCascade(sourceId: Long) {
        deleteEvidenceForSource(sourceId)
        deleteEventsForSource(sourceId)
        deleteTasksForSource(sourceId)
        deleteSemanticEmbedding(sourceId)
        deleteSourceChunksForSource(sourceId)
        deleteSourceRelationsForSource(sourceId)
        deleteActionPointsForSource(sourceId)
        deleteReasoningRunsForSource(sourceId)
        deleteSourceRow(sourceId)
    }

    @Transaction
    suspend fun deleteTaskCascade(taskId: Long) {
        deleteEvidenceForTask(taskId)
        deleteEventsForTask(taskId)
        deleteTaskRow(taskId)
    }
}
