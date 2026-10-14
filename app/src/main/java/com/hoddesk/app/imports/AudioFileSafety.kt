package com.hoddesk.app.imports

import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/** Successful trust decisions are reused only while the exact file metadata and pin agree. */
class CachedModelVerifier {
    private data class Key(val path: String, val bytes: Long, val modifiedAt: Long, val sha256: String, val revision: String)
    private var verified: Key? = null

    @Synchronized
    fun verify(file: File, expectedBytes: Long, sha256: String, revision: String, checkActive: () -> Unit = {}) {
        require(expectedBytes > 0 && sha256.matches(Regex("[0-9a-f]{64}")) && revision.isNotBlank()) { "Invalid model trust pin" }
        checkActive()
        require(file.isFile && file.length() == expectedBytes) { "Offline speech model is missing or incomplete" }
        val before = Key(file.canonicalPath, file.length(), file.lastModified(), sha256, revision)
        if (verified == before) return
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        check(file.length() == before.bytes && file.lastModified() == before.modifiedAt) { "Speech model changed during verification" }
        check(digest.digest().joinToString("") { "%02x".format(it) } == sha256) { "Offline speech model checksum mismatch" }
        verified = before
    }

    /** Same-directory atomic rename keeps the previous model intact on a failed install. */
    fun activate(download: File, target: File, expectedBytes: Long, sha256: String, revision: String, checkActive: () -> Unit = {}) {
        verify(download, expectedBytes, sha256, revision, checkActive)
        checkActive()
        Files.move(download.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        // The hash was verified before the atomic move. Cache the target identity;
        // callers recheck size/mtime/pin immediately before every native load.
        verified = Key(target.canonicalPath, target.length(), target.lastModified(), sha256, revision)
    }
}

data class PcmWaveLayout(val sampleRate: Int, val channels: Int, val dataOffset: Long, val dataBytes: Long) {
    val frames: Long get() = dataBytes / (channels * 2L)
}

object AudioFileSafety {
    const val MAX_SOURCE_BYTES = 512L * 1024 * 1024
    const val MAX_PCM_BYTES = 32_000L * 60 * 60 // One hour of canonical mono PCM.
    const val MAX_DECODER_BUFFER_BYTES = 1024 * 1024

    fun inspectWave(file: File, checkActive: () -> Unit = {}): PcmWaveLayout = RandomAccessFile(file, "r").use { input ->
        require(file.length() in 44..MAX_SOURCE_BYTES) { "WAV is empty, incomplete or exceeds the recording size limit" }
        val header = ByteArray(12).also(input::readFully)
        require(ascii(header, 0, 4) == "RIFF" && ascii(header, 8, 4) == "WAVE") { "WAV header is invalid" }
        var format: Pair<Int, Int>? = null
        var chunks = 0
        while (input.filePointer + 8 <= input.length()) {
            checkActive()
            require(++chunks <= 1024) { "WAV metadata exceeds the chunk limit" }
            val chunk = ByteArray(8).also(input::readFully)
            val size = u32(chunk, 4)
            val offset = input.filePointer
            require(size <= input.length() - offset) { "WAV chunk length exceeds the actual file" }
            when (ascii(chunk, 0, 4)) {
                "fmt " -> {
                    require(size >= 16) { "WAV format chunk is incomplete" }
                    val fmt = ByteArray(16).also(input::readFully)
                    val channels = u16(fmt, 2)
                    val rate = u32(fmt, 4).toInt()
                    require(u16(fmt, 0) == 1 && channels in 1..2 && rate in 8_000..192_000 &&
                        u16(fmt, 12) == channels * 2 && u16(fmt, 14) == 16) { "WAV must contain supported 16-bit PCM audio" }
                    format = rate to channels
                }
                "data" -> {
                    val (rate, channels) = requireNotNull(format) { "WAV format chunk must precede audio data" }
                    require(size > 0 && size % (channels * 2) == 0L) { "WAV contains incomplete PCM frames" }
                    require(size / (channels * 2L) * 32_000L / rate <= MAX_PCM_BYTES) { "Recording exceeds the one-hour transcription limit" }
                    return@use PcmWaveLayout(rate, channels, offset, size)
                }
            }
            input.seek(offset + size + size % 2)
        }
        error("WAV data chunk is missing")
    }

    fun writeWaveHeader(file: File, dataBytes: Long) {
        require(dataBytes in 1..MAX_PCM_BYTES && dataBytes % 2L == 0L) { "Decoded recording is empty or exceeds the one-hour limit" }
        val bytes = ByteArray(44)
        fun text(offset: Int, value: String) = value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
        fun number(offset: Int, value: Long, count: Int = 4) { repeat(count) { bytes[offset + it] = (value shr (8 * it)).toByte() } }
        text(0, "RIFF"); number(4, dataBytes + 36); text(8, "WAVE"); text(12, "fmt "); number(16, 16)
        number(20, 1, 2); number(22, 1, 2); number(24, 16_000); number(28, 32_000); number(32, 2, 2); number(34, 16, 2)
        text(36, "data"); number(40, dataBytes)
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(bytes) }
    }

    /** Two bounded passes retain DC removal and gain without whole-recording arrays. */
    fun normalizeCanonicalWave(file: File, checkActive: () -> Unit = {}) {
        val layout = inspectWave(file, checkActive)
        require(layout.sampleRate == 16_000 && layout.channels == 1) { "Normalization requires canonical PCM WAV" }
        RandomAccessFile(file, "rw").use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0.0
            var squareTotal = 0.0
            var lowest = Int.MAX_VALUE
            var highest = Int.MIN_VALUE
            var remaining = layout.dataBytes
            input.seek(layout.dataOffset)
            while (remaining > 0) {
                checkActive()
                val count = minOf(remaining, buffer.size.toLong()).toInt()
                input.readFully(buffer, 0, count)
                for (i in 0 until count step 2) {
                    val sample = sample(buffer, i)
                    total += sample
                    squareTotal += sample.toDouble() * sample
                    lowest = minOf(lowest, sample); highest = maxOf(highest, sample)
                }
                remaining -= count
            }
            val mean = total / layout.frames
            val rms = sqrt((squareTotal / layout.frames - mean * mean).coerceAtLeast(0.0))
            val peak = maxOf(abs(lowest - mean), abs(highest - mean))
            val gain = if (rms < 1 || peak == 0.0) 1.0 else
                (0.12 * Short.MAX_VALUE / rms).coerceIn(.75, 2.5).coerceAtMost(.95 * Short.MAX_VALUE / peak)
            remaining = layout.dataBytes
            input.seek(layout.dataOffset)
            while (remaining > 0) {
                checkActive()
                val offset = input.filePointer
                val count = minOf(remaining, buffer.size.toLong()).toInt()
                input.readFully(buffer, 0, count)
                for (i in 0 until count step 2) {
                    val normalized = ((sample(buffer, i) - mean) * gain).toInt().coerceIn(-32768, 32767)
                    buffer[i] = normalized.toByte(); buffer[i + 1] = (normalized shr 8).toByte()
                }
                input.seek(offset); input.write(buffer, 0, count)
                remaining -= count
            }
            input.fd.sync()
        }
    }

    private fun sample(bytes: ByteArray, offset: Int) = ((bytes[offset].toInt() and 0xff) or (bytes[offset + 1].toInt() shl 8)).toShort().toInt()
    private fun ascii(bytes: ByteArray, start: Int, count: Int) = String(bytes, start, count, Charsets.US_ASCII)
    private fun u16(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    private fun u32(bytes: ByteArray, offset: Int) = (0..3).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 0xff) shl (i * 8)) }
}
