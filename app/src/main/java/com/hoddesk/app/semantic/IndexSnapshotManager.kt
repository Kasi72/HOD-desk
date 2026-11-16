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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class IndexSnapshot(
    val id: String,
    val modelVersion: String,
    val promptVersion: Int,
    val sourceCount: Int,
    val chunkCount: Int,
    val checksum: String,
    val createdAt: Long = System.currentTimeMillis(),
    val contentChecksum: String = "",
)

object IndexSnapshotManager {
    private const val FORMAT_VERSION = 4
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
        val vectorChecksum = contentChecksum(embeddings, chunks)
        try {
            DataOutputStream(FileOutputStream(temp)).use { out ->
                out.writeInt(FORMAT_VERSION)
                out.writeUTF(snapshot.modelVersion)
                out.writeInt(snapshot.promptVersion)
                out.writeLong(snapshot.createdAt)
                out.writeUTF(snapshot.checksum)
                out.writeUTF(vectorChecksum)
                out.writeInt(embeddings.size)
                embeddings.forEach { writeEmbedding(out, it) }
                out.writeInt(chunks.size)
                chunks.forEach { writeChunk(out, it) }
                out.flush()
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
            val (model, prompt, embeddings, chunks, storedVectorChecksum) = DataInputStream(FileInputStream(file)).use { input ->
                require(input.readInt() == FORMAT_VERSION) { "Unsupported snapshot format" }
                val model = input.readUTF()
                val prompt = input.readInt()
                input.readLong()
                require(input.readUTF() == snapshot.checksum) { "Snapshot checksum mismatch" }
                val storedVectorChecksum = input.readUTF()
                val embeddingCount = input.readInt().also { require(it in 0..100_000) { "Invalid snapshot embedding count" } }
                val embeddings = List(embeddingCount) { readEmbedding(input) }
                val chunkCount = input.readInt().also { require(it in 0..1_000_000) { "Invalid snapshot chunk count" } }
                val chunks = List(chunkCount) { readChunk(input) }
                require(input.read() == -1) { "Snapshot contains unexpected trailing data" }
                Quad(model, prompt, embeddings, chunks, storedVectorChecksum)
            }
            require(model == snapshot.modelVersion && prompt == snapshot.promptVersion)
            require(embeddings.size == snapshot.sourceCount && chunks.size == snapshot.chunkCount)
            require(storedVectorChecksum == contentChecksum(embeddings, chunks)) { "Snapshot vector checksum mismatch" }
            require(embeddings.all { valid(it.vector, it.dimension) }) { "Invalid embedding in snapshot" }
            require(chunks.all { valid(it.vector, it.dimension) }) { "Invalid chunk embedding in snapshot" }
            dao.replaceSemanticIndex(embeddings, chunks)
            activate(context, snapshot)
            true
        }.getOrDefault(false)
    }

    fun activate(context: Context, snapshot: IndexSnapshot) {
        val target = File(context.filesDir, ACTIVE_FILE)
        val temp = File(context.filesDir, ".${ACTIVE_FILE}.tmp")
        temp.writeText(snapshot.id)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun active(context: Context): String? = File(context.filesDir, ACTIVE_FILE).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotBlank() }

    fun snapshots(context: Context): List<IndexSnapshot> = File(context.filesDir, "index-snapshots")
        .listFiles { file -> file.extension == "bin" }
        .orEmpty().mapNotNull { file -> runCatching { readMetadata(file) }.getOrNull() }.sortedByDescending { it.createdAt }

    private data class Quad(val model: String, val prompt: Int, val embeddings: List<SemanticEmbeddingEntity>, val chunks: List<SourceChunkEntity>, val vectorChecksum: String)
    private fun valid(bytes: ByteArray, dimension: Int): Boolean = runCatching { EmbeddingIntegrity.validate(bytes.decodeSnapshotVector(), dimension).valid }.getOrDefault(false)
    private fun contentChecksum(embeddings: List<SemanticEmbeddingEntity>, chunks: List<SourceChunkEntity>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: String) { digest.update(value.toByteArray(Charsets.UTF_8)); digest.update(0.toByte()) }
        embeddings.sortedBy { it.sourceId }.forEach { e -> add("e|${e.sourceId}|${e.modelVersion}|${e.dimension}|${e.modality}|${e.evidenceLocator}|${e.sourceFingerprint}|${e.promptVersion}|${e.chunkNumber}"); digest.update(e.vector) }
        chunks.sortedWith(compareBy<SourceChunkEntity> { it.sourceId }.thenBy { it.ordinal }.thenBy { it.id }).forEach { c -> add("c|${c.id}|${c.sourceId}|${c.ordinal}|${c.modality}|${c.citation}|${c.sourceFingerprint}|${c.promptVersion}|${c.chunkNumber}"); digest.update(c.vector) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun writeBytes(out: DataOutputStream, bytes: ByteArray) { out.writeInt(bytes.size); out.write(bytes) }
    private fun readBytes(input: DataInputStream): ByteArray = ByteArray(input.readInt().also { require(it in 0..20_000_000) }).also(input::readFully)
    private fun writeEmbedding(out: DataOutputStream, e: SemanticEmbeddingEntity) { out.writeLong(e.sourceId); out.writeUTF(e.modelVersion); out.writeInt(e.dimension); out.writeUTF(e.modality); out.writeBoolean(e.evidenceLocator != null); e.evidenceLocator?.let(out::writeUTF); out.writeLong(e.indexedAt); out.writeUTF(e.sourceFingerprint); out.writeInt(e.promptVersion); out.writeInt(e.chunkNumber); writeBytes(out, e.vector) }
    private fun readEmbedding(input: DataInputStream): SemanticEmbeddingEntity {
        val sourceId = input.readLong()
        val model = input.readUTF()
        val dimension = input.readInt()
        val modality = input.readUTF()
        val locator = if (input.readBoolean()) input.readUTF() else null
        val indexedAt = input.readLong()
        val fingerprint = input.readUTF(); val promptVersion = input.readInt(); val chunkNumber = input.readInt()
        val vector = readBytes(input)
        return SemanticEmbeddingEntity(sourceId, model, dimension, vector, modality, locator, indexedAt, fingerprint, promptVersion, chunkNumber)
    }
    private fun writeChunk(out: DataOutputStream, c: SourceChunkEntity) { out.writeLong(c.id); out.writeLong(c.sourceId); out.writeInt(c.ordinal); out.writeUTF(c.modality); out.writeUTF(c.text); out.writeUTF(c.citation); out.writeBoolean(c.page != null); c.page?.let(out::writeInt); out.writeBoolean(c.paragraphIndex != null); c.paragraphIndex?.let(out::writeInt); out.writeBoolean(c.messageIndex != null); c.messageIndex?.let(out::writeInt); out.writeBoolean(c.startMs != null); c.startMs?.let(out::writeLong); out.writeBoolean(c.endMs != null); c.endMs?.let(out::writeLong); out.writeUTF(c.modelVersion); out.writeInt(c.dimension); out.writeLong(c.indexedAt); out.writeUTF(c.sourceFingerprint); out.writeInt(c.promptVersion); out.writeInt(c.chunkNumber); writeBytes(out, c.vector) }
    private fun readChunk(input: DataInputStream): SourceChunkEntity {
        val id = input.readLong(); val sourceId = input.readLong(); val ordinal = input.readInt()
        val modality = input.readUTF(); val text = input.readUTF(); val citation = input.readUTF()
        val page = if (input.readBoolean()) input.readInt() else null
        val paragraph = if (input.readBoolean()) input.readInt() else null
        val message = if (input.readBoolean()) input.readInt() else null
        val start = if (input.readBoolean()) input.readLong() else null
        val end = if (input.readBoolean()) input.readLong() else null
        val model = input.readUTF(); val dimension = input.readInt(); val indexedAt = input.readLong()
        val fingerprint = input.readUTF(); val prompt = input.readInt(); val chunkNumber = input.readInt()
        val vector = readBytes(input)
        return SourceChunkEntity(id, sourceId, ordinal, modality, text, citation, page, paragraph, message,
            start, end, model, dimension, vector, indexedAt, fingerprint, prompt, chunkNumber)
    }
    private fun readMetadata(file: File): IndexSnapshot = DataInputStream(FileInputStream(file)).use { input -> require(input.readInt() == FORMAT_VERSION); val model = input.readUTF(); val prompt = input.readInt(); val created = input.readLong(); val checksum = input.readUTF(); val vectorChecksum = input.readUTF(); val ec = input.readInt(); repeat(ec) { skipEmbedding(input) }; val cc = input.readInt(); repeat(cc) { skipChunk(input) }; IndexSnapshot(file.nameWithoutExtension, model, prompt, ec, cc, checksum, created, vectorChecksum) }
    private fun skipEmbedding(input: DataInputStream) { input.readLong(); input.readUTF(); input.readInt(); input.readUTF(); if (input.readBoolean()) input.readUTF(); input.readLong(); input.readUTF(); input.readInt(); input.readInt(); input.skipBytes(input.readInt()) }
    private fun skipChunk(input: DataInputStream) { input.readLong(); input.readLong(); input.readInt(); input.readUTF(); input.readUTF(); input.readUTF(); if (input.readBoolean()) input.readInt(); if (input.readBoolean()) input.readInt(); if (input.readBoolean()) input.readInt(); if (input.readBoolean()) input.readLong(); if (input.readBoolean()) input.readLong(); input.readUTF(); input.readInt(); input.readLong(); input.readUTF(); input.readInt(); input.readInt(); input.skipBytes(input.readInt()) }
}






private fun ByteArray.decodeSnapshotVector(): FloatArray {
    require(size % 4 == 0)
    val buffer = java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.float }
}

