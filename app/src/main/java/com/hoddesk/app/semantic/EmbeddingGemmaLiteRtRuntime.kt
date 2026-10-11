package com.hoddesk.app.semantic

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.util.Log
import com.hoddesk.app.BuildConfig
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer

data class AudioEmbeddingSegment(val startMs: Long, val endMs: Long, val vector: FloatArray)
data class ImageRegionEmbedding(val region: String, val vector: FloatArray)
data class PagePreviewEmbedding(val page: Int, val vector: FloatArray)

/** Real on-device EmbeddingGemma 2 service backed by MediaPipe UniversalEmbedder/LiteRT-LM. */
class EmbeddingGemmaLiteRtRuntime(private val context: Context) : EmbeddingGemmaService, AutoCloseable {
    companion object {
        const val MODEL_URL = "https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm?download=true"
        private const val MODEL_NAME = "embeddinggemma-2-740m.litertlm"
        private const val MIN_MODEL_BYTES = 100_000_000L
        private const val EXPECTED_DIMENSION = 768

    }

    private val modelFile get() = File(context.filesDir, MODEL_NAME)
    private val engineMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val installMutex = Mutex()
    @Volatile private var embedder: UniversalEmbedder? = null
    @Volatile private var mappedModel: ByteBuffer? = null
    @Volatile private var nativeLoadFailure: Throwable? = null

    private fun ensureNativeLibraries() {
        nativeLoadFailure?.let { throw IllegalStateException("On-device semantic search is unavailable on this device", it) }
        if (embedder != null) return
        synchronized(this) {
            if (embedder != null) return
            nativeLoadFailure?.let { throw IllegalStateException("On-device semantic search is unavailable on this device", it) }
            runCatching {
                // Keep native loading out of static class initialization. A
                // missing optional runtime must degrade to keyword search, not
                // crash the whole HOD Desk process during composition.
                System.loadLibrary("litertlm_jni")
                System.loadLibrary("mediapipe_tasks_jni")
            }.onFailure { nativeLoadFailure = it }.getOrThrow()
        }
    }

    suspend fun ensureInstalled(onProgress: (Int) -> Unit = {}) = installMutex.withLock {
      withContext(Dispatchers.IO) {
        if (modelFile.length() >= MIN_MODEL_BYTES) {
            runCatching { verifyModelIntegrity(modelFile) }.onFailure {
                // A failed or interrupted install must not poison every future
                // startup. Remove it and atomically fetch a clean replacement.
                Log.w("HOD_SEMANTIC", "invalid_model_removed", it)
                modelFile.delete()
            }.getOrNull()?.let { return@withContext }
        }
        val temp = File(context.cacheDir, "$MODEL_NAME.part")
        if (temp.exists()) temp.delete()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "EmbeddingGemma download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100L) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
            check(temp.length() >= MIN_MODEL_BYTES) { "Downloaded LiteRT-LM bundle is incomplete (${temp.length()} bytes)" }
            verifyModelIntegrity(temp)
            if (modelFile.exists()) modelFile.delete()
            check(temp.renameTo(modelFile)) { "Unable to install EmbeddingGemma model" }
            Log.i("HOD_SEMANTIC", "model_installed_bytes=${modelFile.length()}")
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
      }
    }

    private fun verifyModelIntegrity(file: File) {
        val expected = BuildConfig.EMBEDDING_MODEL_SHA256.trim().lowercase()
        if (expected.isBlank()) {
            Log.w("HOD_SEMANTIC", "model_integrity_unverified; configure hoddeskEmbeddingModelSha256 for production")
            return
        }
        require(expected.matches(Regex("[0-9a-f]{64}"))) {
            "Invalid EmbeddingGemma SHA-256 configuration"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == expected) {
            "EmbeddingGemma model SHA-256 mismatch; expected $expected, got $actual"
        }
        Log.i("HOD_SEMANTIC", "model_integrity_verified_sha256=$actual")
    }

    private suspend fun engine(): UniversalEmbedder = engineMutex.withLock {
        ensureNativeLibraries()
        embedder ?: withContext(Dispatchers.Default) {
            ensureInstalled()
            // UniversalEmbedder's LiteRT-LM backend requires a model path for
            // .litertlm bundles; a mapped ByteBuffer is not accepted by all
            // MediaPipe task versions even though it is valid for .tflite files.
            val baseOptions = BaseOptions.builder().setModelAssetPath(modelFile.absolutePath).build()
            val options = UniversalEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .setTextDelegate(Delegate.CPU)
                .setVisionDelegate(Delegate.CPU)
                .setAudioDelegate(Delegate.CPU)
                .setL2Normalize(true)
                .setMaxInputLength(8192)
                .setCacheDir(context.cacheDir.absolutePath)
                .build()
            val started = System.currentTimeMillis()
            UniversalEmbedder.createFromOptions(context, options).also {
                Log.i("HOD_SEMANTIC", "engine_initialized_ms=${System.currentTimeMillis() - started}")
            }
        }.also { created -> embedder = created }
    }

    override suspend fun embed(text: String): FloatArray = inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            require(text.isNotBlank()) { "Cannot embed blank text" }
            val started = System.currentTimeMillis()
            val vector = engine().embedText(text).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                ?: error("EmbeddingGemma returned no embedding")
            check(vector.size == EXPECTED_DIMENSION) { "Unexpected EmbeddingGemma dimension ${vector.size}; expected $EXPECTED_DIMENSION" }
            Log.i("HOD_SEMANTIC", "embedding_ms=${System.currentTimeMillis() - started},dimension=${vector.size}")
            vector
        }
    }

    /** Embeds the original camera/screenshot pixels into the shared multimodal space. */
    suspend fun embedImage(uri: Uri): FloatArray = inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Unable to decode image for EmbeddingGemma")
            val image = BitmapImageBuilder(bitmap).build()
            try {
                (engine().embedImage(image).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                    ?: error("EmbeddingGemma returned no image embedding")).also {
                    check(it.size == EXPECTED_DIMENSION) { "Unexpected EmbeddingGemma image dimension ${it.size}; expected $EXPECTED_DIMENSION" }
                }
            } finally {
                image.close()
                bitmap.recycle()
            }
        }
    }

    /** Embeds a small set of quadrants so text queries can find a relevant image region. */
    suspend fun embedImageRegions(uri: Uri): List<ImageRegionEmbedding> = withContext(Dispatchers.Default) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Unable to decode image for region embedding")
        try {
            val regions = listOf("top-left" to android.graphics.Rect(0, 0, bitmap.width / 2, bitmap.height / 2), "top-right" to android.graphics.Rect(bitmap.width / 2, 0, bitmap.width, bitmap.height / 2), "bottom-left" to android.graphics.Rect(0, bitmap.height / 2, bitmap.width / 2, bitmap.height), "bottom-right" to android.graphics.Rect(bitmap.width / 2, bitmap.height / 2, bitmap.width, bitmap.height))
            regions.map { (name, rect) ->
                val crop = android.graphics.Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1))
                val image = BitmapImageBuilder(crop).build()
                try { ImageRegionEmbedding(name, inferenceMutex.withLock { engine().embedImage(image).embeddings().first().floatEmbedding().copyOf() }) } finally { image.close(); crop.recycle() }
            }
        } finally { bitmap.recycle() }
    }

    /** Embeds canonical mono PCM audio; callers must provide 16 kHz float samples. */
    suspend fun embedAudio(samples: FloatArray, sampleRate: Float = 16_000f, channels: Int = 1): FloatArray = inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            require(samples.isNotEmpty()) { "Cannot embed empty audio" }
            val format = AudioData.AudioDataFormat.builder().setSampleRate(sampleRate).setNumOfChannels(channels).build()
            val audio = AudioData.create(format, samples.size)
            audio.load(samples)
            (engine().embedAudio(audio).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                ?: error("EmbeddingGemma returned no audio embedding")).also {
                check(it.size == EXPECTED_DIMENSION) { "Unexpected EmbeddingGemma audio dimension ${it.size}; expected $EXPECTED_DIMENSION" }
            }
        }
    }

    /** Embeds a user-selected canonical 16 kHz PCM WAV without using its transcript. */
    suspend fun embedWav(uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read audio for EmbeddingGemma")
        require(bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Audio embedding requires PCM WAV" }
        val channels = u16(bytes, 22)
        val rate = u32(bytes, 24)
        val bits = u16(bytes, 34)
        require(channels == 1 && rate == 16_000 && bits == 16) { "Audio embedding requires 16 kHz mono 16-bit PCM WAV" }
        val data = bytes.copyOfRange(44, bytes.size)
        val samples = FloatArray(data.size / 2) { i -> (((data[i * 2].toInt() and 0xff) or (data[i * 2 + 1].toInt() shl 8)).toShort().toInt() / 32768f) }
        embedAudio(samples, rate.toFloat(), channels)
    }

    /** Embeds overlapping 30-second waveform windows with timestamp locators. */
    suspend fun embedWavSegments(uri: Uri, windowMs: Long = 30_000L, overlapMs: Long = 5_000L): List<AudioEmbeddingSegment> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read audio")
        require(bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE")
        val channels = u16(bytes, 22); val rate = u32(bytes, 24); val bits = u16(bytes, 34)
        require(channels == 1 && rate == 16_000 && bits == 16) { "Audio embedding requires 16 kHz mono 16-bit PCM WAV" }
        val data = bytes.copyOfRange(44, bytes.size)
        val samples = FloatArray(data.size / 2) { i -> (((data[i * 2].toInt() and 0xff) or (data[i * 2 + 1].toInt() shl 8)).toShort().toInt() / 32768f) }
        val window = (rate * windowMs / 1000L).toInt().coerceAtLeast(1); val step = (rate * (windowMs - overlapMs) / 1000L).toInt().coerceAtLeast(1)
        buildList { var start = 0; while (start < samples.size) { val end = (start + window).coerceAtMost(samples.size); add(AudioEmbeddingSegment(start * 1000L / rate, end * 1000L / rate, embedAudio(samples.copyOfRange(start, end), rate.toFloat(), 1))); if (end == samples.size) break; start += step } }
    }

    /** Renders PDF pages to previews and embeds them in the same multimodal space. */
    suspend fun embedPdfPagePreviews(uri: Uri): List<PagePreviewEmbedding> = withContext(Dispatchers.Default) {
        PDFBoxResourceLoader.init(context)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read PDF")
        PDDocument.load(bytes).use { document ->
            val renderer = PDFRenderer(document)
            (0 until document.numberOfPages).map { index ->
                val bitmap = renderer.renderImage(index, 1.0f)
                val image = BitmapImageBuilder(bitmap).build()
                try { PagePreviewEmbedding(index + 1, inferenceMutex.withLock { engine().embedImage(image).embeddings().first().floatEmbedding().copyOf() }) } finally { image.close(); bitmap.recycle() }
            }
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Int = u16(bytes, offset) or (u16(bytes, offset + 2) shl 16)

    override fun close() {
        embedder?.close()
        embedder = null
        mappedModel = null
    }
}
