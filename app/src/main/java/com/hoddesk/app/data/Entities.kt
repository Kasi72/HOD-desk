package com.hoddesk.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sources", indices = [Index("reviewed"), Index("capturedAt")])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val sourceType: String,
    val sourceUri: String? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val reviewed: Boolean = false,
    val sensitivity: String = "official",
    val processingStatus: String = "ready",
    val triageBucket: String = "FYI",
    val priorityScore: Int = 0,
    val priorityReasons: String? = null
)

@Entity(tableName = "tasks", indices = [Index("status"), Index("dueAt"), Index("sourceId")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long? = null,
    val title: String,
    val project: String,
    val dueAt: Long? = null,
    val priority: String = "medium",
    val status: String = "open",
    val blocker: String? = null,
    val clarification: String? = null,
    val assignee: String? = null,
    val recurrenceRule: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "events", indices = [Index("startsAt"), Index("endsAt")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
    val location: String? = null,
    val confirmed: Boolean = false,
    val calendarEventId: Long? = null
)

@Entity(tableName = "evidence", indices = [Index("taskId")])
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val uri: String,
    val type: String,
    val note: String? = null,
    val capturedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "semantic_embeddings")
data class SemanticEmbeddingEntity(
    @PrimaryKey val sourceId: Long,
    val modelVersion: String,
    val dimension: Int,
    val vector: ByteArray,
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "source_chunks", indices = [Index("sourceId"), Index("modality")])
data class SourceChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val ordinal: Int,
    val modality: String,
    val text: String,
    val citation: String,
    val page: Int? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val modelVersion: String,
    val dimension: Int,
    val vector: ByteArray,
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "source_relations", indices = [Index("sourceId"), Index("relatedSourceId")])
data class SourceRelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val relatedSourceId: Long,
    val relationType: String,
    val score: Float,
    val rationale: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_points", indices = [Index("sourceId"), Index("status"), Index("confidence")])
data class ActionPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val action: String,
    val category: String = "official_work",
    val dateText: String? = null,
    val timeText: String? = null,
    val locationText: String? = null,
    val peopleText: String? = null,
    val evidence: String,
    val confidence: Float,
    val status: String = "needs_review",
    val ambiguity: String? = null,
    val ownerText: String? = null,
    val referenceNumber: String? = null,
    val recurrenceRule: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reasoning_runs", indices = [Index("sourceId"), Index("createdAt")])
data class ReasoningRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val modelVersion: String,
    val action: String,
    val category: String,
    val dateText: String? = null,
    val timeText: String? = null,
    val locationText: String? = null,
    val peopleJson: String,
    val ambiguitiesJson: String,
    val citationsJson: String,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis()
)

/** Append-only record of user-visible workflow transitions. */
@Entity(tableName = "audit_entries", indices = [Index("entityType"), Index("entityId"), Index("createdAt")])
data class AuditEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val action: String,
    val detail: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "learning_corrections", indices = [Index("actionPointId"), Index("createdAt")])
data class LearningCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionPointId: Long,
    val field: String,
    val before: String?,
    val after: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "commitments", indices = [Index("sourceId"), Index("taskId"), Index("direction"), Index("status"), Index("dueAt")])
data class CommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long? = null,
    val taskId: Long? = null,
    val direction: String,
    val owner: String? = null,
    val counterparty: String? = null,
    val summary: String,
    val dueAt: Long? = null,
    val status: String = "open",
    val evidence: String,
    val confidence: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
