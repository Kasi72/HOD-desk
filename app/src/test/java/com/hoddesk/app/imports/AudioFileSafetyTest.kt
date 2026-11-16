package com.hoddesk.app.imports

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioFileSafetyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun riffParserFindsPcmAfterOptionalMetadataAndNormalizationKeepsTheLayout() {
        val file = folder.newFile("metadata.wav")
        val pcm = byteArrayOf(0, 0, 100, 0, (-100).toByte(), (-1).toByte(), 50, 0)
        file.writeBytes(waveWithJunk(pcm))
        val layout = AudioFileSafety.inspectWave(file)
        assertEquals(16_000, layout.sampleRate)
        assertEquals(1, layout.channels)
        assertEquals(pcm.size.toLong(), layout.dataBytes)
        assertTrue(layout.dataOffset > 44)
        AudioFileSafety.normalizeCanonicalWave(file)
        assertEquals(layout, AudioFileSafety.inspectWave(file))
    }

    @Test fun truncatedOrOversizedChunkIsRejected() {
        val file = folder.newFile("truncated.wav")
        file.writeBytes(waveWithJunk(byteArrayOf(1, 0)).copyOf(48))
        assertThrows(IllegalStateException::class.java) { AudioFileSafety.inspectWave(file) }
    }

    @Test fun streamingResamplerIsIndependentOfDecoderBufferBoundaries() {
        val pcm = ByteArray(480 * 2 * 2)
        for (frame in 0 until 480) for (channel in 0..1) {
            val value = (frame * 31 + channel * 7).toShort().toInt()
            val offset = (frame * 2 + channel) * 2
            pcm[offset] = value.toByte(); pcm[offset + 1] = (value shr 8).toByte()
        }
        val once = StreamingPcmResampler(48_000, 2).process(pcm, pcm.size)
        val split = StreamingPcmResampler(48_000, 2)
        val pieces = listOf(1, 7, 103, 511, pcm.size).fold(0 to mutableListOf<Byte>()) { state, endHint ->
            val start = state.first
            val end = endHint.coerceIn(start, pcm.size)
            split.process(pcm.copyOfRange(start, end), end - start).forEach(state.second::add)
            end to state.second
        }.second.toByteArray()
        assertArrayEquals(once, pieces)
    }

    @Test fun modelVerifierRejectsWrongHashAndAtomicallyActivatesTrustedBytes() {
        val target = folder.newFile("model.bin").apply { writeText("old") }
        val candidate = folder.newFile("candidate.bin").apply { writeText("trusted") }
        val digest = MessageDigest.getInstance("SHA-256").digest(candidate.readBytes()).joinToString("") { "%02x".format(it) }
        val verifier = CachedModelVerifier()
        assertThrows(IllegalStateException::class.java) { verifier.verify(candidate, candidate.length(), "0".repeat(64), "rev") }
        verifier.activate(candidate, target, 7, digest, "rev")
        assertEquals("trusted", target.readText())
        verifier.verify(target, 7, digest, "rev")
    }

    private fun waveWithJunk(pcm: ByteArray): ByteArray {
        val junk = byteArrayOf(1, 2, 3, 4)
        val total = 12 + 8 + 16 + 8 + junk.size + 8 + pcm.size
        val bytes = ByteArray(total)
        fun text(at: Int, value: String) = value.toByteArray(Charsets.US_ASCII).copyInto(bytes, at)
        fun n(at: Int, value: Int, count: Int = 4) { repeat(count) { bytes[at + it] = (value shr (8 * it)).toByte() } }
        text(0, "RIFF"); n(4, total - 8); text(8, "WAVE")
        text(12, "fmt "); n(16, 16); n(20, 1, 2); n(22, 1, 2); n(24, 16_000); n(28, 32_000); n(32, 2, 2); n(34, 16, 2)
        text(36, "JUNK"); n(40, junk.size); junk.copyInto(bytes, 44)
        val data = 44 + junk.size; text(data, "data"); n(data + 4, pcm.size); pcm.copyInto(bytes, data + 8)
        return bytes
    }
}
