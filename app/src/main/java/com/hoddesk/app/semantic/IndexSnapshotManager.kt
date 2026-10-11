package com.hoddesk.app.semantic

import android.content.Context
import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.SemanticEmbeddingEntity
import com.hoddesk.app.data.SourceChunkEntity
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    private const val FORMAT_VERSION = 1
    private const val ACTIVE_FILE = "active-snapshot"
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

    /** Writes vectors into app-private storage; callers should only expose this through the encrypted app UI. */
    suspend fun save(context: Context, dao: HodDeskDao, snapshot: IndexSnapshot): File {
        val dir = File(context.filesDir, "index-snapshots").apply { mkdirs() }
        val file = File(dir, "${snapshot.id}.bin")
        val temp = File(dir, ".${snapshot.id}.tmp")
        val embeddings = dao.allSemanticEmbeddings()
        val chunks = dao.allSourceChunks(snapshot.modelVersion)
        try {
            DataOutputStream(FileOutputStream(temp)).use { out ->
                out.writeInt(FORMAT_VERSION)
                out.writeUTF(snapshot.modelVersion)
                out.writeInt(snapshot.promptVersion)
                out.writeLong(snapshot.createdAt)
                out.writeUTF(snapshot.checksum)
                out.writeInt(embeddings.size)
                embeddings.forEach { writeEmbedding(out, it) }
                out.writeInt(chunks.size)
                chunks.forEach { writeChunk(out, it) }
                out.flush()
            }
            check(temp.renameTo(file)) { "Unable to atomically publish index snapshot" }
        } finally {
            if (temp.exists()) temp.delete()
        }
        return file
    }

    /** Restores a complete vector set in one Room transaction. Invalid/truncated files are rejected. */
    suspend fun restore(context: Context, dao: HodDeskDao, snapshot: IndexSnapshot): Boolean {
        val file = File(File(context.filesDir, "index-snapshots"), "${snapshot.id}.bin")
        if (!file.isFile) return false
        return runCatching {
            val (model, prompt, embeddings, chunks) = DataInputStream(FileInputStream(file)).use { input ->
                require(input.readInt() == FORMAT_VERSION) { "Unsupported snapshot format" }
                val model = input.readUTF()
                val prompt = input.readInt()
                input.readLong()
                require(input.readUTF() == snapshot.checksum) { "Snapshot checksum mismatch" }
                val embeddingCount = input.readInt().coerceIn(0, 100_000)
                val embeddings = List(embeddingCount) { readEmbedding(input) }
                val chunkCount = input.readInt().coerceIn(0, 1_000_000)
                val chunks = List(chunkCount) { readChunk(input) }
                Quad(model, prompt, embeddings, chunks)
            }
            require(model == snapshot.modelVersion && prompt == snapshot.promptVersion)
            require(embeddings.size == snapshot.sourceCount && chunks.size == snapshot.chunkCount)
            dao.replaceSemanticIndex(embeddings, chunks)
            activate(context, snapshot)
            true
        }.getOrDefault(false)
    }

    fun activate(context: Context, snapshot: IndexSnapshot) {
        File(context.filesDir, ACTIVE_FILE).writeText(snapshot.id)
    }

    fun active(context: Context): String? = File(context.filesDir, ACTIVE_FILE).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotBlank() }

    fun snapshots(context: Context): List<IndexSnapshot> = File(context.filesDir, "index-snapshots")
        .listFiles { file -> file.extension == "bin" }
        .orEmpty().mapNotNull { file -> runCatching { readMetadata(file) }.getOrNull() }.sortedByDescending { it.createdAt }

    private data class Quad(val model: String, val prompt: Int, val embeddings: List<SemanticEmbeddingEntity>, val chunks: List<SourceChunkEntity>)
    private fun writeBytes(out: DataOutputStream, bytes: ByteArray) { out.writeInt(bytes.size); out.write(bytes) }
    private fun readBytes(input: DataInputStream): ByteArray = ByteArray(input.readInt().also { require(it in 0..20_000_000) }).also(input::readFully)
    private fun writeEmbedding(out: DataOutputStream, e: SemanticEmbeddingEntity) { out.writeLong(e.sourceId); out.writeUTF(e.modelVersion); out.writeInt(e.dimension); out.writeLong(e.indexedAt); writeBytes(out, e.vector) }
    private fun readEmbedding(input: DataInputStream) = SemanticEmbeddingEntity(input.readLong(), input.readUTF(), input.readInt(), readBytes(input), input.readLong())
    private fun writeChunk(out: DataOutputStream, c: SourceChunkEntity) { out.writeLong(c.id); out.writeLong(c.sourceId); out.writeInt(c.ordinal); out.writeUTF(c.modality); out.writeUTF(c.text); out.writeUTF(c.citation); out.writeBoolean(c.page != null); c.page?.let(out::writeInt); out.writeBoolean(c.startMs != null); c.startMs?.let(out::writeLong); out.writeBoolean(c.endMs != null); c.endMs?.let(out::writeLong); out.writeUTF(c.modelVersion); out.writeInt(c.dimension); out.writeLong(c.indexedAt); writeBytes(out, c.vector) }
    private fun readChunk(input: DataInputStream) = SourceChunkEntity(input.readLong(), input.readLong(), input.readInt(), input.readUTF(), input.readUTF(), input.readUTF(), if (input.readBoolean()) input.readInt() else null, if (input.readBoolean()) input.readLong() else null, if (input.readBoolean()) input.readLong() else null, input.readUTF(), input.readInt(), readBytes(input), input.readLong())
    private fun readMetadata(file: File): IndexSnapshot = DataInputStream(FileInputStream(file)).use { input -> require(input.readInt() == FORMAT_VERSION); val model = input.readUTF(); val prompt = input.readInt(); val created = input.readLong(); val checksum = input.readUTF(); val ec = input.readInt(); repeat(ec) { skipEmbedding(input) }; val cc = input.readInt(); repeat(cc) { skipChunk(input) }; IndexSnapshot(file.nameWithoutExtension, model, prompt, ec, cc, checksum, created) }
    private fun skipEmbedding(input: DataInputStream) { input.readLong(); input.readUTF(); input.readInt(); input.readLong(); input.skipBytes(input.readInt()) }
    private fun skipChunk(input: DataInputStream) { input.readLong(); input.readLong(); input.readInt(); input.readUTF(); input.readUTF(); input.readUTF(); if (input.readBoolean()) input.readInt(); if (input.readBoolean()) input.readLong(); if (input.readBoolean()) input.readLong(); input.readUTF(); input.readInt(); input.readLong(); input.skipBytes(input.readInt()) }
}
