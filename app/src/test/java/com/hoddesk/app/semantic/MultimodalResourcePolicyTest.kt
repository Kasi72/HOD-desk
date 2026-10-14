package com.hoddesk.app.semantic

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MultimodalResourcePolicyTest {
    @Test fun largeImagesAreDownsampledBeforeBitmapAllocation() {
        val sample = MultimodalResourcePolicy.imageSampleSize(12_000, 9_000)
        assertTrue(sample >= 8)
        assertTrue((12_000L / sample) * (9_000L / sample) <= MultimodalResourcePolicy.MAX_IMAGE_PIXELS)
        assertEquals(1, MultimodalResourcePolicy.imageSampleSize(720, 1400))
    }

    @Test fun invalidWindowsAndUnboundedRecordingsAreRejectedBeforeInference() {
        assertEquals(3, MultimodalResourcePolicy.segmentCount(16_000L * 80, 30_000, 5_000))
        assertTrue(runCatching { MultimodalResourcePolicy.segmentCount(1000, 30_000, 30_000) }.isFailure)
        assertTrue(runCatching { MultimodalResourcePolicy.segmentCount(16_000L * 24 * 60 * 60, 30_000, 5_000) }.isFailure)
    }

    @Test fun boundedProviderCopyFailsBeforeWritingAboveLimit() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching {
            MultimodalResourcePolicy.copyBounded(ByteArrayInputStream(ByteArray(100)), output, 50)
        }.isFailure)
        assertTrue(output.size() <= 50)
    }

    @Test fun riffMetadataAndTrailingChunksNeverBecomeAudioSamples() {
        PcmWavWindowReader(ByteArrayInputStream(wav(shortArrayOf(-32768, 0, 16384, 32767), metadata = true))).use { reader ->
            assertEquals(4L, reader.totalSamples)
            assertArrayEquals(floatArrayOf(-1f, 0f, .5f), reader.readSamples(3), .0001f)
            assertArrayEquals(floatArrayOf(32767f / 32768f), reader.readSamples(3), .0001f)
            assertEquals(0, reader.readSamples(3).size)
        }
    }

    @Test fun truncatedDeclaredPcmLengthIsRejected() {
        val bytes = wav(shortArrayOf(1, 2))
        PcmWavWindowReader(ByteArrayInputStream(bytes.copyOf(bytes.size - 2))).use { reader ->
            assertTrue(runCatching { reader.readSamples(2) }.isFailure)
        }
    }

    @Test fun pcmReaderChecksCancellationWhileReading() {
        val cancellation = CancellationException("Stopped")
        assertSame(cancellation, runCatching {
            PcmWavWindowReader(ByteArrayInputStream(wav(shortArrayOf(1)))) { throw cancellation }
        }.exceptionOrNull())
    }

    @Test fun summaryNormalizesValidWindowsAndRejectsCollapsedOutput() {
        val first = FloatArray(768) { if (it == 0) 1f else 0f }
        val second = FloatArray(768) { if (it == 1) 1f else 0f }
        assertTrue(EmbeddingIntegrity.validate(MultimodalResourcePolicy.summarize(listOf(first, second))).valid)
        assertTrue(runCatching { MultimodalResourcePolicy.summarize(listOf(first, first.map { -it }.toFloatArray())) }.isFailure)
    }

    private fun wav(samples: ShortArray, metadata: Boolean = false): ByteArray {
        fun chunk(id: String, data: ByteArray): ByteArray = ByteArrayOutputStream().apply {
            write(id.toByteArray()); write(le32(data.size)); write(data)
            if (data.size % 2 == 1) write(0)
        }.toByteArray()
        val format = byteArrayOf(1, 0, 1, 0) + le32(16_000) + le32(32_000) + byteArrayOf(2, 0, 16, 0)
        val pcm = samples.flatMap { listOf(it.toByte(), (it.toInt() shr 8).toByte()) }.toByteArray()
        val body = "WAVE".toByteArray() + chunk("fmt ", format) +
            (if (metadata) chunk("JUNK", byteArrayOf(42, 43, 44)) else byteArrayOf()) + chunk("data", pcm)
        return "RIFF".toByteArray() + le32(body.size) + body
    }

    private fun le32(value: Int) = ByteArray(4) { (value shr (8 * it)).toByte() }
}
