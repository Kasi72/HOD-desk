package com.hoddesk.app.semantic

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.sqrt

/** Explicit resource ceilings; an unsupported input falls back to its text evidence. */
object MultimodalResourcePolicy {
    const val MAX_IMAGE_EDGE = 2048
    const val MAX_IMAGE_PIXELS = 4_194_304L
    const val MAX_PDF_BYTES = 32L * 1024 * 1024
    const val MAX_PDF_PAGES = 128
    const val MAX_AUDIO_SEGMENTS = 512
    const val SAMPLE_RATE = 16_000
    const val MAX_AUDIO_WINDOW_MS = 30_000L

    fun imageSampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "Image dimensions are invalid" }
        var sample = 1
        while (true) {
            val w = (width.toLong() + sample - 1) / sample
            val h = (height.toLong() + sample - 1) / sample
            if (w <= MAX_IMAGE_EDGE && h <= MAX_IMAGE_EDGE && w * h <= MAX_IMAGE_PIXELS) return sample
            require(sample <= (1 shl 28)) { "Image dimensions exceed decoder limits" }
            sample *= 2
        }
    }

    fun segmentCount(totalSamples: Long, windowMs: Long, overlapMs: Long): Int {
        require(totalSamples > 0) { "Recording has no PCM samples" }
        require(windowMs in 1..MAX_AUDIO_WINDOW_MS && overlapMs in 0 until windowMs) { "Audio window/overlap is invalid" }
        val window = SAMPLE_RATE * windowMs / 1000
        val step = SAMPLE_RATE * (windowMs - overlapMs) / 1000
        require(window > 0 && step > 0)
        val count = if (totalSamples <= window) 1L else 1L + (totalSamples - window + step - 1) / step
        require(count <= MAX_AUDIO_SEGMENTS) { "Recording exceeds $MAX_AUDIO_SEGMENTS waveform windows; transcript retrieval remains available" }
        return count.toInt()
    }

    fun summarize(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty() && vectors.all { EmbeddingIntegrity.validate(it).valid }) { "Invalid audio segment embeddings" }
        val sum = FloatArray(768)
        vectors.forEach { vector -> sum.indices.forEach { sum[it] += vector[it] } }
        val norm = sqrt(sum.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        require(norm > 0f && norm.isFinite()) { "Audio summary embedding collapsed" }
        return FloatArray(sum.size) { sum[it] / norm }.also {
            require(EmbeddingIntegrity.validate(it).valid) { "Audio summary embedding is invalid" }
        }
    }

    /** Never allocates based on an untrusted provider's declared content length. */
    fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long, checkActive: () -> Unit = {}): Long {
        require(maxBytes > 0)
        val buffer = ByteArray(32 * 1024)
        var copied = 0L
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count == -1) return copied
            if (count == 0) {
                val byte = input.read()
                if (byte == -1) return copied
                check(copied < maxBytes) { "Media file exceeds $maxBytes bytes" }
                output.write(byte); copied++
                continue
            }
            check(copied + count <= maxBytes) { "Media file exceeds $maxBytes bytes" }
            output.write(buffer, 0, count)
            copied += count
        }
    }
}

/** Reads RIFF chunks and PCM windows without retaining the full recording. */
class PcmWavWindowReader(private val input: InputStream, private val checkActive: () -> Unit = {}) : AutoCloseable {
    val totalSamples: Long
    private var remainingBytes: Long

    init {
        val header = readExactly(12)
        require(ascii(header, 0, 4) == "RIFF" && ascii(header, 8, 4) == "WAVE") { "Audio embedding requires PCM WAV" }
        var formatSeen = false
        var metadataBytes = 0L
        var dataBytes: Long? = null
        var chunkCount = 0
        while (dataBytes == null) {
            checkActive()
            require(++chunkCount <= 1024) { "WAV contains too many metadata chunks" }
            val chunkHeader = readExactly(8)
            val id = ascii(chunkHeader, 0, 4)
            val size = u32(chunkHeader, 4)
            if (id == "data") {
                require(formatSeen) { "WAV format chunk must precede PCM data" }
                require(size > 0 && size % 2L == 0L) { "WAV contains incomplete PCM samples" }
                dataBytes = size
            } else {
                metadataBytes += size + (size % 2)
                require(metadataBytes <= 4L * 1024 * 1024) { "WAV metadata exceeds safe limits" }
                if (id == "fmt ") {
                    require(size >= 16) { "WAV format chunk is incomplete" }
                    val format = readExactly(16)
                    require(u16(format, 0) == 1 && u16(format, 2) == 1 && u32(format, 4) == 16_000L &&
                        u16(format, 12) == 2 && u16(format, 14) == 16) { "Audio embedding requires 16 kHz mono 16-bit PCM WAV" }
                    skipExactly(size - 16)
                    formatSeen = true
                } else skipExactly(size)
                if (size % 2L != 0L) skipExactly(1)
            }
        }
        remainingBytes = requireNotNull(dataBytes)
        totalSamples = remainingBytes / 2
    }

    fun readSamples(maxSamples: Int): FloatArray {
        require(maxSamples in 1..480_000) { "Requested audio window exceeds 30 seconds" }
        val count = minOf(maxSamples.toLong(), remainingBytes / 2).toInt()
        if (count == 0) return FloatArray(0)
        val bytes = readExactly(count * 2)
        remainingBytes -= bytes.size
        return FloatArray(count) { i ->
            (((bytes[i * 2].toInt() and 0xff) or (bytes[i * 2 + 1].toInt() shl 8)).toShort().toInt() / 32768f)
        }
    }

    private fun readExactly(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            checkActive()
            val count = input.read(bytes, offset, size - offset)
            check(count >= 0) { "WAV ended before its declared PCM/metadata length" }
            if (count == 0) {
                val byte = input.read()
                check(byte >= 0) { "WAV ended before its declared length" }
                bytes[offset++] = byte.toByte()
            } else offset += count
        }
        return bytes
    }

    private fun skipExactly(size: Long) {
        val buffer = ByteArray(8192)
        var remaining = size
        while (remaining > 0) {
            checkActive()
            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            check(count > 0) { "WAV metadata is truncated" }
            remaining -= count
        }
    }

    override fun close() = input.close()
    private fun ascii(bytes: ByteArray, start: Int, count: Int) = String(bytes, start, count, Charsets.US_ASCII)
    private fun u16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 0xffL) shl (i * 8)) }
}
