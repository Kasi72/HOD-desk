package com.hoddesk.app.semantic

import java.security.MessageDigest

data class IndexSnapshot(
    val id: String,
    val modelVersion: String,
    val promptVersion: Int,
    val sourceCount: Int,
    val chunkCount: Int,
    val checksum: String,
    val createdAt: Long = System.currentTimeMillis(),
)

object IndexSnapshotManager {
    fun checksum(modelVersion: String, promptVersion: Int, sourceIds: List<Long>, chunkCount: Int): String {
        val payload = modelVersion + "|" + promptVersion + "|" + sourceIds.sorted().joinToString(",") + "|" + chunkCount
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun create(modelVersion: String, promptVersion: Int, sourceIds: List<Long>, chunkCount: Int): IndexSnapshot {
        val digest = checksum(modelVersion, promptVersion, sourceIds, chunkCount)
        return IndexSnapshot(digest.take(16), modelVersion, promptVersion, sourceIds.size, chunkCount, digest)
    }

    fun validate(snapshot: IndexSnapshot, modelVersion: String, promptVersion: Int, sourceIds: List<Long>, chunkCount: Int): Boolean =
        snapshot.modelVersion == modelVersion &&
            snapshot.promptVersion == promptVersion &&
            snapshot.sourceCount == sourceIds.size &&
            snapshot.chunkCount == chunkCount &&
            snapshot.checksum == checksum(modelVersion, promptVersion, sourceIds, chunkCount)
}
