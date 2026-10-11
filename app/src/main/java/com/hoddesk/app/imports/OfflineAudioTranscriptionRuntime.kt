package com.hoddesk.app.imports

import android.content.Context
import android.net.Uri
import android.util.Log
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Tamil-first multilingual offline transcription with Whisper, with an English Vosk fallback. */
class OfflineAudioTranscriptionRuntime(private val context: Context) {
    companion object {
        // The small checkpoint produced visibly unreliable Tamil on mixed
        // clinical calls. Medium is the minimum Whisper checkpoint we ship for
        // dependable Tamil names, numbers and action phrases on an S26 Ultra.
        private const val WHISPER_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin?download=true"
        private const val WHISPER_MODEL_NAME = "ggml-medium.bin"
        private const val MIN_WHISPER_MODEL_BYTES = 1_400_000_000L
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val MODEL_DIR = "vosk-model-small-en-us-0.15"
    }

    private val whisperModelFile get() = File(context.filesDir, WHISPER_MODEL_NAME)
    private val modelDirectory get() = File(context.filesDir, MODEL_DIR)
    private val whisperInstallMutex = Mutex()
    private val voskInstallMutex = Mutex()

    /** Downloads the multilingual Whisper base model used for Tamil-first transcription. */
    suspend fun ensureTamilModel(onProgress: (Int) -> Unit = {}) = whisperInstallMutex.withLock { withContext(Dispatchers.IO) {
        if (whisperModelFile.length() >= MIN_WHISPER_MODEL_BYTES) return@withContext
        val temp = File(context.cacheDir, "$WHISPER_MODEL_NAME.part")
        if (temp.exists()) temp.delete()
        val connection = (URL(WHISPER_MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Tamil transcription model download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024); var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read); copied += read
                    if (total > 0) onProgress((copied * 100L / total).toInt().coerceIn(0, 100))
                }
                output.fd.sync()
            } }
            check(temp.length() >= MIN_WHISPER_MODEL_BYTES) { "Tamil transcription model is incomplete" }
            if (whisperModelFile.exists()) whisperModelFile.delete()
            check(temp.renameTo(whisperModelFile)) { "Unable to install Tamil transcription model" }
            Log.i("HOD_AUDIO", "tamil_model=medium,bytes=${whisperModelFile.length()}")
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    } }

    /** Tamil-first multilingual transcription. English remains available through [transcribeEnglish]. */
    suspend fun transcribeWav(uri: Uri): String = transcribeTamil(uri)

    suspend fun transcribeTamil(uri: Uri): String = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        ensureTamilModel()
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val suffix = when {
            mime.contains("wav") || mime.contains("wave") -> ".wav"
            mime.contains("m4a") || mime.contains("mp4") -> ".m4a"
            mime.contains("aac") -> ".aac"
            mime.contains("flac") -> ".flac"
            mime.contains("mpeg") || mime.contains("mp3") -> ".mp3"
            else -> ".wav"
        }
        val source = File.createTempFile("hod-audio-source-", suffix, context.cacheDir)
        val audio = File.createTempFile("hod-audio-", ".wav", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open recording" }
                source.outputStream().use { output -> input.copyTo(output) }
            }
            require(source.length() > 44L) { "Selected recording is empty or incomplete" }
            if (mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac")) {
                decodeCompressedToWav(uri, audio)
                Log.i("HOD_AUDIO", "m4a_decoded_to_wav_bytes=${audio.length()}")
            } else {
                canonicalizeWav(source, audio)
            }
            require(audio.length() > 44L) { "Decoded recording is empty or incomplete" }
            normalizeWavInPlace(audio)
            Log.i("HOD_AUDIO", "audio_preprocessed=16khz_mono_normalized")
            Log.i("HOD_AUDIO", "whisper_input_mime=$mime,bytes=${audio.length()},suffix=.wav")
            val model = Whisper.loadModel(context, whisperModelFile.absolutePath)
            try {
                val result = Whisper.transcribe(
                    model,
                    audio.absolutePath,
                    WhisperConfig(language = "ta", translate = false, threads = 4, maxSegmentLength = 0, printTimestamps = false)
                )
                val text = result.text.trim()
                Log.i("HOD_AUDIO", "language=ta,transcription_ms=${System.currentTimeMillis() - started},chars=${text.length}")
                text
            } finally {
                Whisper.releaseModel(model)
            }
        } finally {
            source.delete()
            audio.delete()
        }
    }

    private fun decodeCompressedToWav(uri: Uri, outputFile: File) {
        val extractor = MediaExtractor().apply { setDataSource(context, uri, null) }
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("Recording has no decodable audio track")
        extractor.selectTrack(track)
        val inputFormat = extractor.getTrackFormat(track)
        val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
        val sourceRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(mime)
        val info = MediaCodec.BufferInfo()
        var pcmBytes = 0L
        val targetRate = 16_000
        val targetChannels = 1
        try {
            codec.configure(inputFormat, null, null, 0); codec.start()
            outputFile.outputStream().use { out ->
                out.write(ByteArray(44))
                val resampler = PcmResampler(sourceRate, sourceChannels)
                var inputEnded = false; var outputEnded = false
                var stalledPolls = 0
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val input = codec.getInputBuffer(inputIndex)
                                ?: error("Audio decoder returned no input buffer")
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) { codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                            else { codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0); extractor.advance() }
                        }
                    }
                    when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // A decoder may have delayed frames after EOS. Do not
                            // truncate the tail merely because one poll is empty.
                            stalledPolls++
                            check(stalledPolls < 120) { "Audio decoder stalled before end of stream" }
                        }
                        else -> {
                            stalledPolls = 0
                            if (info.size > 0) {
                                val buffer = codec.getOutputBuffer(index)
                                    ?: error("Audio decoder returned no output buffer")
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size); buffer.get(bytes)
                                val pcm = resampler.process(bytes, bytes.size)
                                out.write(pcm); pcmBytes += pcm.size
                            }
                            codec.releaseOutputBuffer(index, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                        }
                    }
                }
            }
            writeWavHeader(outputFile, pcmBytes, targetRate, targetChannels)
        } finally { codec.stop(); codec.release(); extractor.release() }
    }

    private fun writeWavHeader(file: File, dataSize: Long, sampleRate: Int, channels: Int) {
        val header = ByteArray(44)
        fun putAscii(offset: Int, value: String) { value.toByteArray(Charsets.US_ASCII).copyInto(header, offset) }
        fun putInt(offset: Int, value: Long) { for (i in 0..3) header[offset + i] = (value shr (8 * i)).toByte() }
        fun putShort(offset: Int, value: Int) { header[offset] = value.toByte(); header[offset + 1] = (value shr 8).toByte() }
        putAscii(0, "RIFF"); putInt(4, 36 + dataSize); putAscii(8, "WAVE"); putAscii(12, "fmt "); putInt(16, 16); putShort(20, 1); putShort(22, channels); putInt(24, sampleRate.toLong()); putInt(28, (sampleRate * channels * 2).toLong()); putShort(32, channels * 2); putShort(34, 16); putAscii(36, "data"); putInt(40, dataSize)
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(header) }
    }

    /** Converts a user-provided PCM WAV (any common rate, one/two channels) to Whisper's contract. */
    private fun canonicalizeWav(inputFile: File, outputFile: File) {
        val bytes = inputFile.readBytes()
        require(bytes.size >= 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "WAV header is invalid" }
        val channels = littleU16(bytes, 22)
        val sampleRate = littleU32(bytes, 24).toInt()
        val bits = littleU16(bytes, 34)
        val dataOffset = findWavDataOffset(bytes)
        require(channels in 1..2 && sampleRate > 0 && bits == 16) { "WAV must contain 16-bit PCM audio" }
        val pcmLength = bytes.size - dataOffset
        require(pcmLength > 0 && pcmLength % (2 * channels) == 0) { "WAV contains no complete PCM frames" }
        val pcm = bytes.copyOfRange(dataOffset, bytes.size)
        val converted = resamplePcm(pcm, pcm.size, sampleRate, channels)
        outputFile.outputStream().use { it.write(ByteArray(44)); it.write(converted) }
        writeWavHeader(outputFile, converted.size.toLong(), 16_000, 1)
    }

    /** Removes DC offset and applies bounded gain so quiet Tamil speech is not lost by the decoder. */
    private fun normalizeWavInPlace(file: File) {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return
        val pcm = bytes.copyOfRange(44, bytes.size)
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return
        var mean = 0.0
        var sumSquares = 0.0
        var peak = 0
        for (i in 0 until sampleCount) {
            val value = (pcm[i * 2].toInt() and 0xff) or (pcm[i * 2 + 1].toInt() shl 8)
            mean += value.toShort().toInt()
        }
        mean /= sampleCount
        for (i in 0 until sampleCount) {
            val value = (pcm[i * 2].toInt() and 0xff) or (pcm[i * 2 + 1].toInt() shl 8)
            val centered = (value.toShort().toInt() - mean)
            sumSquares += centered * centered
            peak = maxOf(peak, kotlin.math.abs(centered).toInt())
        }
        val rms = kotlin.math.sqrt(sumSquares / sampleCount)
        val gain = if (rms < 1.0 || peak == 0) 1.0 else (0.12 * Short.MAX_VALUE / rms).coerceIn(0.75, 2.5).coerceAtMost(0.95 * Short.MAX_VALUE / peak)
        for (i in 0 until sampleCount) {
            val value = (pcm[i * 2].toInt() and 0xff) or (pcm[i * 2 + 1].toInt() shl 8)
            val normalized = ((value.toShort().toInt() - mean) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[i * 2] = normalized.toByte()
            pcm[i * 2 + 1] = (normalized shr 8).toByte()
        }
        file.outputStream().use { it.write(bytes, 0, 44); it.write(pcm) }
    }

    private fun findWavDataOffset(bytes: ByteArray): Int {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val size = littleU32(bytes, offset + 4).toInt()
            if (String(bytes, offset, 4, Charsets.US_ASCII) == "data") return offset + 8
            offset += 8 + size + (size and 1)
        }
        require(bytes.size >= 44) { "WAV data chunk is missing" }
        return 44
    }

    private fun littleU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun littleU32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or ((bytes[offset + 3].toLong() and 0xff) shl 24)

    suspend fun transcribeEnglish(uri: Uri): String = transcribeVosk(uri)

    suspend fun ensureModel(onProgress: (Int) -> Unit = {}) = voskInstallMutex.withLock { withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (File(modelDirectory, "am/final.mdl").exists()) return@withContext
        val zip = File(context.cacheDir, "$MODEL_DIR.zip")
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.connect()
        check(connection.responseCode in 200..299) { "Transcription model download failed: HTTP ${connection.responseCode}" }
        val total = connection.contentLengthLong
        connection.inputStream.use { input -> zip.outputStream().use { output ->
            val buffer = ByteArray(1024 * 1024); var copied = 0L
            while (true) { val read = input.read(buffer); if (read <= 0) break; output.write(buffer, 0, read); copied += read; if (total > 0) onProgress((copied * 100 / total).toInt()) }
        } }
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = File(context.filesDir, entry.name).canonicalFile
                check(target.path.startsWith(context.filesDir.canonicalPath + File.separator)) { "Unsafe model archive entry" }
                if (entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); target.outputStream().use { input.copyTo(it) } }
            }
        }
        zip.delete()
        check(File(modelDirectory, "am/final.mdl").exists()) { "Transcription model is incomplete" }
        Log.i("HOD_AUDIO", "model_ready_ms=${System.currentTimeMillis() - started}")
    } }

    private suspend fun transcribeVosk(uri: Uri): String = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        ensureModel()
        val model = Model(modelDirectory.absolutePath)
        val recognizer = Recognizer(model, 16_000f)
        try {
            val header = context.contentResolver.openInputStream(uri)?.use { input -> ByteArray(12).also { input.read(it) } }
            if (header != null && header.size == 12 && String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") {
                feedWav(uri, recognizer)
            } else {
                feedCompressed(uri, recognizer)
            }
            val text = Regex("\\\"text\\\"\\s*:\\s*\\\"([^\"]*)\\\"").find(recognizer.finalResult)?.groupValues?.get(1).orEmpty()
            Log.i("HOD_AUDIO", "transcription_ms=${System.currentTimeMillis() - started},chars=${text.length}")
            text
        } finally { recognizer.close(); model.close() }
    }

    private fun feedWav(uri: Uri, recognizer: Recognizer) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open recording" }
            val header = ByteArray(44)
            require(input.read(header) == 44) { "WAV header is incomplete" }
            val channels = (header[22].toInt() and 0xff) or ((header[23].toInt() and 0xff) shl 8)
            val sampleRate = (header[24].toInt() and 0xff) or ((header[25].toInt() and 0xff) shl 8) or ((header[26].toInt() and 0xff) shl 16) or ((header[27].toInt() and 0xff) shl 24)
            val bits = (header[34].toInt() and 0xff) or ((header[35].toInt() and 0xff) shl 8)
            require(channels in 1..2 && bits == 16 && sampleRate > 0) { "WAV must be 16-bit PCM with one or two channels" }
            val buffer = ByteArray(8192)
            val resampler = PcmResampler(sampleRate, channels)
            while (true) { val read = input.read(buffer); if (read <= 0) break; val pcm = resampler.process(buffer, read); recognizer.acceptWaveForm(pcm, pcm.size) }
        }
    }

    private fun feedCompressed(uri: Uri, recognizer: Recognizer) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: error("Recording has no decodable audio track")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(mime)
        val info = MediaCodec.BufferInfo()
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var inputEnded = false
            var outputEnded = false
            var stalledPolls = 0
            val resampler = PcmResampler(sourceRate, channels)
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                            ?: error("Audio decoder returned no input buffer")
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) { codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                        else { codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        stalledPolls++
                        check(stalledPolls < 120) { "Audio decoder stalled before end of stream" }
                    }
                    else -> {
                        stalledPolls = 0
                        if (info.size > 0) {
                            val output = codec.getOutputBuffer(outputIndex)
                                ?: error("Audio decoder returned no output buffer")
                            output.position(info.offset); output.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size); output.get(bytes)
                            val pcm = resampler.process(bytes, bytes.size)
                            recognizer.acceptWaveForm(pcm, pcm.size)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                    }
                }
            }
        } finally { codec.stop(); codec.release(); extractor.release() }
    }

    private fun resamplePcm(input: ByteArray, length: Int, sourceRate: Int, channels: Int): ByteArray {
        val sourceFrames = length / 2 / channels
        if (sourceFrames <= 0 || sourceRate <= 0 || channels <= 0) return ByteArray(0)
        val targetFrames = (sourceFrames.toLong() * 16_000L / sourceRate).toInt().coerceAtLeast(0)
        val output = ByteArray(targetFrames * 2)
        for (target in 0 until targetFrames) {
            val source = ((target.toLong() * sourceRate) / 16_000L).toInt().coerceAtMost(sourceFrames - 1)
            var mixed = 0
            for (channel in 0 until channels) {
                val index = (source * channels + channel) * 2
                mixed += (input[index].toInt() and 0xff) or (input[index + 1].toInt() shl 8)
            }
            val sample = (mixed / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[target * 2] = sample.toByte(); output[target * 2 + 1] = (sample shr 8).toByte()
        }
        return output
    }

    /** Stateful nearest-neighbour resampler. Decoder output buffers are arbitrary
     * chunks; keeping phase and carry prevents clicks, drift and lost Tamil syllables
     * at every MediaCodec boundary. */
    private class PcmResampler(private val sourceRate: Int, private val channels: Int) {
        private val pending = ArrayList<Short>()
        private var carry = ByteArray(0)
        private var phase = 0.0
        private val step = if (sourceRate > 0) sourceRate.toDouble() / 16_000.0 else 1.0

        fun process(input: ByteArray, length: Int): ByteArray {
            require(channels > 0) { "Audio channel count must be positive" }
            val safeLength = length.coerceIn(0, input.size)
            val combined = ByteArray(carry.size + safeLength)
            carry.copyInto(combined)
            input.copyInto(combined, carry.size, 0, safeLength)
            val complete = (combined.size / (2 * channels)) * (2 * channels)
            carry = combined.copyOfRange(complete, combined.size)
            var offset = 0
            while (offset < complete) {
                var mixed = 0
                for (channel in 0 until channels) {
                    val i = offset + channel * 2
                    mixed += ((combined[i].toInt() and 0xff) or (combined[i + 1].toInt() shl 8)).toShort().toInt()
                }
                pending += (mixed / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                offset += 2 * channels
            }
            val result = java.io.ByteArrayOutputStream()
            while (pending.isNotEmpty() && phase < pending.size) {
                val sample = pending[phase.toInt()]
                result.write(sample.toInt() and 0xff); result.write((sample.toInt() shr 8) and 0xff)
                phase += step
            }
            val drop = phase.toInt().coerceAtMost(pending.size)
            if (drop > 0) { pending.subList(0, drop).clear(); phase -= drop }
            return result.toByteArray()
        }
    }
}
