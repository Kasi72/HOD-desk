package com.hoddesk.app.semantic

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.util.Log
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** Real on-device EmbeddingGemma 2 service backed by MediaPipe UniversalEmbedder/LiteRT-LM. */
class EmbeddingGemmaLiteRtRuntime(private val context: Context) : EmbeddingGemmaService, AutoCloseable {
    companion object {
        const val MODEL_URL = "https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm?download=true"
        private const val MODEL_NAME = "embeddinggemma-2-740m.litertlm"
        private const val MIN_MODEL_BYTES = 100_000_000L
        private const val EXPECTED_DIMENSION = 768

        init {
            // The retrieval AAR exposes these JNI libraries but does not load them
            // on every Android/MediaPipe combination before ByteBuffer mapping.
            // Load the LiteRT-LM dependency first, then the MediaPipe task bridge.
            System.loadLibrary("litertlm_jni")
            System.loadLibrary("mediapipe_tasks_jni")
        }
    }

    private val modelFile get() = File(context.filesDir, MODEL_NAME)
    private val engineMutex = Mutex()
    private val inferenceMutex = Mutex()
    @Volatile private var embedder: UniversalEmbedder? = null
    @Volatile private var mappedModel: ByteBuffer? = null

    suspend fun ensureInstalled(onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (modelFile.length() >= MIN_MODEL_BYTES) return@withContext
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
            if (modelFile.exists()) modelFile.delete()
            check(temp.renameTo(modelFile)) { "Unable to install EmbeddingGemma model" }
            Log.i("HOD_SEMANTIC", "model_installed_bytes=${modelFile.length()}")
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    private suspend fun engine(): UniversalEmbedder = engineMutex.withLock {
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
                engine().embedImage(image).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                    ?: error("EmbeddingGemma returned no image embedding")
            } finally {
                image.close()
                bitmap.recycle()
            }
        }
    }

    /** Embeds canonical mono PCM audio; callers must provide 16 kHz float samples. */
    suspend fun embedAudio(samples: FloatArray, sampleRate: Float = 16_000f, channels: Int = 1): FloatArray = inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            require(samples.isNotEmpty()) { "Cannot embed empty audio" }
            val format = AudioData.AudioDataFormat.builder().setSampleRate(sampleRate).setNumOfChannels(channels).build()
            val audio = AudioData.create(format, samples.size)
            audio.load(samples)
            engine().embedAudio(audio).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                ?: error("EmbeddingGemma returned no audio embedding")
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

    private fun u16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Int = u16(bytes, offset) or (u16(bytes, offset + 2) shl 16)

    override fun close() {
        embedder?.close()
        embedder = null
        mappedModel = null
    }
}
