package com.hoddesk.app.imports

import java.io.ByteArrayOutputStream

/** PCM phase/carry survives arbitrary decoder boundaries, including partial stereo frames. */
class StreamingPcmResampler(private val sourceRate: Int, private val channels: Int) {
    init {
        require(sourceRate in 8_000..192_000 && channels in 1..2) { "Unsupported recording sample rate/channel count" }
    }
    private val pending = ArrayList<Short>()
    private var carry = ByteArray(0)
    private var phase = 0.0
    private val step = sourceRate.toDouble() / 16_000.0

    fun process(input: ByteArray, length: Int): ByteArray {
        require(length in 0..input.size && length <= AudioFileSafety.MAX_DECODER_BUFFER_BYTES) { "Audio decoder buffer exceeds safe bounds" }
        val combined = ByteArray(carry.size + length)
        carry.copyInto(combined); input.copyInto(combined, carry.size, 0, length)
        val complete = combined.size / (2 * channels) * (2 * channels)
        carry = combined.copyOfRange(complete, combined.size)
        var offset = 0
        while (offset < complete) {
            var mixed = 0
            for (channel in 0 until channels) {
                val i = offset + channel * 2
                mixed += ((combined[i].toInt() and 0xff) or (combined[i + 1].toInt() shl 8)).toShort().toInt()
            }
            pending += (mixed / channels).coerceIn(-32768, 32767).toShort()
            offset += 2 * channels
        }
        val output = ByteArrayOutputStream()
        while (pending.isNotEmpty() && phase < pending.size) {
            val sample = pending[phase.toInt()].toInt()
            output.write(sample and 0xff); output.write((sample shr 8) and 0xff)
            phase += step
        }
        val dropped = phase.toInt().coerceAtMost(pending.size)
        if (dropped > 0) { pending.subList(0, dropped).clear(); phase -= dropped }
        return output.toByteArray()
    }
}
