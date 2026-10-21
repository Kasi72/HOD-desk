package com.hoddesk.app.imports

import android.content.Context
import android.net.Uri
import android.util.Log
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.hoddesk.app.semantic.MultimodalResourcePolicy
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
        // Keep the selected multilingual medium model; trust its exact published
        // artifact independently of the transcription quality evaluation.
        private const val WHISPER_REVISION = "5359861c739e955e79d9a303bcbc70fb988958b1"
        private const val WHISPER_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/$WHISPER_REVISION/ggml-medium.bin?download=true"
        private const val WHISPER_MODEL_NAME = "ggml-medium.bin"
        private const val WHISPER_MODEL_BYTES = 1_533_763_059L
        private const val WHISPER_MODEL_SHA256 = "6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val MODEL_DIR = "vosk-model-small-en-us-0.15"
        private val sharedWhisperInstallMutex = Mutex()
        private val sharedVoskInstallMutex = Mutex()
        private val sharedInferenceMutex = Mutex()
        private val modelVerifier = CachedModelVerifier()
    }

    private val whisperModelFile get() = File(context.filesDir, WHISPER_MODEL_NAME)
    private val modelDirectory get() = File(context.filesDir, MODEL_DIR)
    private val whisperInstallMutex = sharedWhisperInstallMutex
    private val voskInstallMutex = sharedVoskInstallMutex

    /** Downloads and verifies the pinned multilingual medium model. */
    suspend fun ensureTamilModel(onProgress: (Int) -> Unit = {}) = whisperInstallMutex.withLock { withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        if (verifiedWhisperInstalled { coroutine.ensureActive() }) return@withContext
        check(context.filesDir.usableSpace >= WHISPER_MODEL_BYTES + 64L * 1024 * 1024) { "Not enough free storage to install the verified offline speech model" }
        // The old model remains intact until a same-directory atomic activation.
        val temp = File.createTempFile("$WHISPER_MODEL_NAME-", ".part", context.filesDir)
        val connection = (URL(WHISPER_MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Tamil transcription model download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            check(total < 0 || total == WHISPER_MODEL_BYTES) { "Speech model download does not match the pinned artifact size" }
            connection.inputStream.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024); var copied = 0L
                while (true) {
                    coroutine.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    check(copied + read <= WHISPER_MODEL_BYTES) { "Speech model download exceeds its trusted size" }
                    output.write(buffer, 0, read); copied += read
                    if (total > 0) onProgress((copied * 100L / total).toInt().coerceIn(0, 100))
                }
                output.fd.sync()
            } }
            modelVerifier.activate(temp, whisperModelFile, WHISPER_MODEL_BYTES, WHISPER_MODEL_SHA256, WHISPER_REVISION) { coroutine.ensureActive() }
            Log.i("HOD_AUDIO", "tamil_model=medium,bytes=${whisperModelFile.length()}")
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    } }

    private fun verifiedWhisperInstalled(checkActive: () -> Unit): Boolean = try {
        modelVerifier.verify(whisperModelFile, WHISPER_MODEL_BYTES, WHISPER_MODEL_SHA256, WHISPER_REVISION, checkActive)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: java.io.IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    /** Reports readiness only after checking the exact pinned file, off the UI thread. */
    suspend fun tamilModelReady(): Boolean = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        verifiedWhisperInstalled { coroutine.ensureActive() }
    }

    /** Tamil-first multilingual transcription. English remains available through [transcribeEnglish]. */
    suspend fun transcribeWav(uri: Uri): String = transcribeTamil(uri)

    suspend fun transcribeTamil(uri: Uri): String = withTimeout(5 * 60_000L) { sharedInferenceMutex.withLock { withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
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
                source.outputStream().use { output ->
                    MultimodalResourcePolicy.copyBounded(input, output, AudioFileSafety.MAX_SOURCE_BYTES) { coroutine.ensureActive() }
                }
            }
            require(source.length() > 44L) { "Selected recording is empty or incomplete" }
            // Some Android document providers report recordings as
            // application/octet-stream (and omit the extension).  MIME-only
            // routing then sends an M4A/AAC file to the WAV parser, which is
            // why audio could appear to import successfully but never
            // produce a transcript.  Inspect the copied bytes as a second
            // source of truth before choosing the decoder.
            val compressed = isCompressedAudio(source, mime)
            Log.i("HOD_AUDIO", "audio_input_format=${if (compressed) "compressed" else "wav"}")
            if (compressed) {
                decodeCompressedToWav(source, audio) { coroutine.ensureActive() }
                Log.i("HOD_AUDIO", "compressed_decoded_to_wav_bytes=${audio.length()}")
            } else {
                canonicalizeWav(source, audio) { coroutine.ensureActive() }
            }
            require(audio.length() > 44L) { "Decoded recording is empty or incomplete" }
            normalizeWavInPlace(audio) { coroutine.ensureActive() }
            Log.i("HOD_AUDIO", "audio_preprocessed=16khz_mono_normalized")
            Log.i("HOD_AUDIO", "whisper_input_mime=$mime,bytes=${audio.length()},suffix=.wav")
            // Recheck the cached file identity/pin immediately before native load.
            modelVerifier.verify(whisperModelFile, WHISPER_MODEL_BYTES, WHISPER_MODEL_SHA256, WHISPER_REVISION) { coroutine.ensureActive() }
            val model = Whisper.loadModel(context, whisperModelFile.absolutePath)
            try {
                // The medium checkpoint is intentionally retained for Tamil
                // accuracy, but four threads leaves the S26 Ultra largely
                // idle.  Use the device's available cores (bounded to avoid
                // starving Compose/Room or causing runaway thread creation).
                val inferenceThreads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
                Log.i("HOD_AUDIO", "whisper_inference_threads=$inferenceThreads")
                val result = Whisper.transcribe(
                    model,
                    audio.absolutePath,
                    WhisperConfig(language = "ta", translate = false, threads = inferenceThreads, maxSegmentLength = 0, printTimestamps = false)
                )
                coroutine.ensureActive()
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
    } } }

    /** Detect compressed containers when a picker supplies no useful MIME. */
    private fun isCompressedAudio(source: File, mime: String): Boolean {
        if (mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") ||
            mime.contains("mpeg") || mime.contains("mp3") || mime.contains("flac") ||
            mime.contains("ogg") || mime.contains("opus") || mime.contains("amr")) return true
        val header = ByteArray(12)
        RandomAccessFile(source, "r").use { file ->
            val count = file.read(header)
            if (count < 4) return false
            val first = String(header, 0, 4, Charsets.US_ASCII)
            if (first == "RIFF" && count >= 12 && String(header, 8, 4, Charsets.US_ASCII) == "WAVE") return false
            if (first == "fLaC" || first == "ID3") return true
            // ISO-BMFF audio (M4A/AAC) stores an `ftyp` box at byte 4.
            if (count >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") return true
            // ADTS AAC and MPEG audio commonly begin with a sync word.
            if ((header[0].toInt() and 0xff) == 0xff &&
                ((header[1].toInt() and 0xe0) == 0xe0)) return true
        }
        return false
    }

    private fun decodeCompressedToWav(sourceFile: File, outputFile: File, checkActive: () -> Unit) {
        val extractor = MediaExtractor().apply { setDataSource(sourceFile.absolutePath) }
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
        var codecStarted = false
        try {
            codec.configure(inputFormat, null, null, 0); codec.start(); codecStarted = true
            outputFile.outputStream().use { out ->
                out.write(ByteArray(44))
                val resampler = StreamingPcmResampler(sourceRate, sourceChannels)
                var inputEnded = false; var outputEnded = false
                var stalledPolls = 0
                while (!outputEnded) {
                    checkActive()
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
                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
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
                                check(pcmBytes + pcm.size <= AudioFileSafety.MAX_PCM_BYTES) { "Recording exceeds the one-hour transcription limit" }
                                out.write(pcm); pcmBytes += pcm.size
                            }
                            codec.releaseOutputBuffer(index, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                        }
                    }
                }
            }
            AudioFileSafety.writeWaveHeader(outputFile, pcmBytes)
        } finally {
            if (codecStarted) runCatching { codec.stop() }
            codec.release(); extractor.release()
        }
    }

    /** Converts a user-provided PCM WAV (any common rate, one/two channels) to Whisper's contract. */
    private fun canonicalizeWav(inputFile: File, outputFile: File, checkActive: () -> Unit) {
        val layout = AudioFileSafety.inspectWave(inputFile, checkActive)
        val resampler = StreamingPcmResampler(layout.sampleRate, layout.channels)
        var written = 0L
        RandomAccessFile(inputFile, "r").use { input ->
            input.seek(layout.dataOffset)
            outputFile.outputStream().use { output ->
                output.write(ByteArray(44))
                val buffer = ByteArray(64 * 1024)
                var remaining = layout.dataBytes
                while (remaining > 0) {
                    checkActive()
                    val count = minOf(remaining, buffer.size.toLong()).toInt()
                    input.readFully(buffer, 0, count)
                    val converted = resampler.process(buffer, count)
                    check(written + converted.size <= AudioFileSafety.MAX_PCM_BYTES) { "Recording exceeds the one-hour transcription limit" }
                    output.write(converted); written += converted.size; remaining -= count
                }
            }
        }
        AudioFileSafety.writeWaveHeader(outputFile, written)
    }

    /** Removes DC offset and applies bounded gain so quiet Tamil speech is not lost by the decoder. */
    private fun normalizeWavInPlace(file: File, checkActive: () -> Unit) =
        AudioFileSafety.normalizeCanonicalWave(file, checkActive)

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
        val temp = File.createTempFile("hod-vosk-wave-", ".wav", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open recording" }
                temp.outputStream().use { MultimodalResourcePolicy.copyBounded(input, it, AudioFileSafety.MAX_SOURCE_BYTES) }
            }
            val layout = AudioFileSafety.inspectWave(temp)
            RandomAccessFile(temp, "r").use { input ->
                input.seek(layout.dataOffset)
                var remaining = layout.dataBytes
                val resampler = StreamingPcmResampler(layout.sampleRate, layout.channels)
                val buffer = ByteArray(8192)
                while (remaining > 0) {
                    val count = minOf(remaining, buffer.size.toLong()).toInt()
                    input.readFully(buffer, 0, count)
                    val pcm = resampler.process(buffer, count)
                    recognizer.acceptWaveForm(pcm, pcm.size)
                    remaining -= count
                }
            }
        } finally {
            temp.delete()
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
            val resampler = StreamingPcmResampler(sourceRate, channels)
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
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
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

}
