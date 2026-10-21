package com.hoddesk.app.semantic

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.io.MemoryUsageSetting

data class AudioEmbeddingSegment(val startMs: Long, val endMs: Long, val vector: FloatArray)
data class ImageRegionEmbedding(val region: String, val vector: FloatArray)
data class PagePreviewEmbedding(val page: Int, val vector: FloatArray)

/** Real on-device EmbeddingGemma 2 service backed by MediaPipe UniversalEmbedder/LiteRT-LM. */
class EmbeddingGemmaLiteRtRuntime(private val context: Context) : EmbeddingGemmaService, AutoCloseable {
    companion object {
        const val MODEL_URL = "https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/24d962e906c7d332c6428e71c9676855024569e2/embeddinggemma-2-740m.litertlm?download=true"
        private const val MODEL_NAME = "embeddinggemma-2-740m.litertlm"
        private const val MODEL_BYTES = 484_622_336L
        private const val EXPECTED_DIMENSION = 768
        // MainActivity and WorkManager can hold separate runtime instances.
        // They share one model file and must not race downloads/native inference.
        private val sharedInstallMutex = Mutex()
        private val sharedInferenceMutex = Mutex()
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val modelFile get() = File(context.filesDir, MODEL_NAME)
    private val engineMutex = Mutex()
    private val inferenceMutex = sharedInferenceMutex
    private val installMutex = sharedInstallMutex
    private val textCache = BoundedVectorCache(maxEntries = 128, maxBytes = 512L * 1024L)
    private val mediaCache = BoundedVectorCache(maxEntries = 32, maxBytes = 256L * 1024L)
    @Volatile private var embedder: UniversalEmbedder? = null
    @Volatile private var closed = false
    @Volatile private var nativeLoadFailure: Throwable? = null
    @Volatile private var activeDelegate: String = "uninitialized"

    /** The UI and workers can surface which delegate was actually accepted. */
    fun delegateName(): String = activeDelegate
    fun isInstalled(): Boolean = modelFile.length() == MODEL_BYTES

    /** Verifies the model without native initialization; call on an IO dispatcher. */
    fun verifyInstalledModel(): Result<Unit> = runCatching {
        require(isInstalled()) { "EmbeddingGemma model is not installed or incomplete" }
        verifyModelIntegrity(modelFile)
    }

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
        if (isInstalled()) {
                runCatching { verifyModelIntegrity(modelFile) }.onFailure {
                // A failed or interrupted install must not poison every future
                // startup. Remove it and atomically fetch a clean replacement.
                Log.w("HOD_SEMANTIC", "invalid_model_removed", it)
                modelFile.delete()
            }.getOrNull()?.let { return@withContext }
        }
        check(context.filesDir.usableSpace >= MODEL_BYTES + 32L * 1024 * 1024) { "Not enough free storage to install EmbeddingGemma" }
        val temp = File(context.filesDir, ".$MODEL_NAME.part")
        if (temp.exists()) temp.delete()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
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
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        check(copied + read <= MODEL_BYTES) { "EmbeddingGemma download exceeds the pinned model size" }
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100L) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
            check(temp.length() == MODEL_BYTES) { "Downloaded LiteRT-LM bundle is incomplete (${temp.length()} bytes)" }
            verifyModelIntegrity(temp)
            // A failed replacement leaves the previously verified model intact.
            Files.move(temp.toPath(), modelFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            Log.i("HOD_SEMANTIC", "model_installed_bytes=${modelFile.length()}")
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
      }
    }

    private fun verifyModelIntegrity(file: File) {
        val expected = BuildConfig.EMBEDDING_MODEL_SHA256.trim().lowercase()
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
        check(!closed) { "EmbeddingGemma runtime is closed" }
        ensureNativeLibraries()
        embedder ?: withContext(Dispatchers.Default) {
            // Installing a large model is an explicit Settings action. A
            // search or maintenance retry must not start a hidden download.
            check(isInstalled()) { "EmbeddingGemma model is not installed; keyword search remains available" }
            verifyModelIntegrity(modelFile)
            // UniversalEmbedder's LiteRT-LM backend requires a model path for
            // .litertlm bundles; a mapped ByteBuffer is not accepted by all
            // MediaPipe task versions even though it is valid for .tflite files.
            val baseOptions = BaseOptions.builder().setModelAssetPath(modelFile.absolutePath).build()
            val started = System.currentTimeMillis()
            val common = { delegate: Delegate ->
                UniversalEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setTextDelegate(delegate)
                    .setVisionDelegate(delegate)
                    .setAudioDelegate(delegate)
                    .setL2Normalize(true)
                    .setMaxInputLength(8192)
                    .setCacheDir(context.cacheDir.absolutePath)
                    .build()
            }
            try {
                UniversalEmbedder.createFromOptions(context, common(Delegate.GPU)).also { activeDelegate = "GPU" }
            } catch (failure: Throwable) {
                if (failure is CancellationException || failure is VirtualMachineError) throw failure
                    Log.w("HOD_SEMANTIC", "gpu_delegate_unavailable_fallback_cpu", failure)
                    UniversalEmbedder.createFromOptions(context, common(Delegate.CPU)).also { activeDelegate = "CPU" }
            }.also {
                    Log.i("HOD_SEMANTIC", "engine_initialized_ms=${System.currentTimeMillis() - started},delegate=$activeDelegate")
                }
        }.also { created -> embedder = created }
    }

    override suspend fun embed(text: String): FloatArray {
        require(text.isNotBlank()) { "Cannot embed blank text" }
        val key = "text:${text.trim()}"
        textCache.get(key)?.let { return it }
        return withTimeout(45_000L) { inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            val vector = engine().embedText(text).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                ?: error("EmbeddingGemma returned no embedding")
            check(vector.size == EXPECTED_DIMENSION) { "Unexpected EmbeddingGemma dimension ${vector.size}; expected $EXPECTED_DIMENSION" }
            textCache.put(key, vector)
            Log.i("HOD_SEMANTIC", "embedding_ms=${System.currentTimeMillis() - started},dimension=${vector.size}")
            vector
        }
        } }
    }

    /** Embeds the original camera/screenshot pixels into the shared multimodal space. */
    suspend fun embedImage(uri: Uri): FloatArray {
        val key = "image:${uri}"
        mediaCache.get(key)?.let { return it }
        return withTimeout(60_000L) { inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            val bitmap = decodeBoundedImage(uri)
            try {
                val image = BitmapImageBuilder(bitmap).build()
                try {
                (engine().embedImage(image).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                    ?: error("EmbeddingGemma returned no image embedding")).also {
                    check(EmbeddingIntegrity.validate(it).valid) { "Invalid EmbeddingGemma image vector" }
                mediaCache.put(key, it)
                }
                } finally { image.close() }
            } finally {
                bitmap.recycle()
            }
        }
        } }
    }

    /** Embeds a small set of quadrants so text queries can find a relevant image region. */
    suspend fun embedImageRegions(uri: Uri): List<ImageRegionEmbedding> = withContext(Dispatchers.Default) {
        val bitmap = decodeBoundedImage(uri)
        try {
            if (bitmap.width < 2 || bitmap.height < 2) return@withContext emptyList()
            val regions = listOf("top-left" to android.graphics.Rect(0, 0, bitmap.width / 2, bitmap.height / 2), "top-right" to android.graphics.Rect(bitmap.width / 2, 0, bitmap.width, bitmap.height / 2), "bottom-left" to android.graphics.Rect(0, bitmap.height / 2, bitmap.width / 2, bitmap.height), "bottom-right" to android.graphics.Rect(bitmap.width / 2, bitmap.height / 2, bitmap.width, bitmap.height))
            regions.map { (name, rect) ->
                currentCoroutineContext().ensureActive()
                val crop = android.graphics.Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1))
                try {
                    val image = BitmapImageBuilder(crop).build()
                    try {
                        ImageRegionEmbedding(name, inferenceMutex.withLock {
                            engine().embedImage(image).embeddings().first().floatEmbedding().copyOf().also {
                                check(EmbeddingIntegrity.validate(it).valid) { "Invalid image region embedding" }
                            }
                        })
                    } finally { image.close() }
                } finally { crop.recycle() }
            }
        } finally { bitmap.recycle() }
    }

    /** Embeds canonical mono PCM audio; callers must provide 16 kHz float samples. */
    suspend fun embedAudio(samples: FloatArray, sampleRate: Float = 16_000f, channels: Int = 1): FloatArray = withTimeout(60_000L) { inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            require(samples.isNotEmpty()) { "Cannot embed empty audio" }
            require(sampleRate == 16_000f && channels == 1 && samples.size <= 480_000) { "Audio embedding requires at most 30 seconds of 16 kHz mono PCM" }
            require(samples.all { it.isFinite() && it in -1f..1f }) { "Invalid PCM waveform samples" }
            val format = AudioData.AudioDataFormat.builder().setSampleRate(sampleRate).setNumOfChannels(channels).build()
            val audio = AudioData.create(format, samples.size)
            audio.load(samples)
            (engine().embedAudio(audio).embeddings().firstOrNull()?.floatEmbedding()?.copyOf()
                ?: error("EmbeddingGemma returned no audio embedding")).also {
                check(EmbeddingIntegrity.validate(it).valid) { "Invalid EmbeddingGemma audio vector" }
            }
        }
    } }

    /** Summarizes the original waveform windows without allocating the whole recording. */
    suspend fun embedWav(uri: Uri): FloatArray = MultimodalResourcePolicy.summarize(embedWavSegments(uri).map { it.vector })

    /** Embeds overlapping 30-second waveform windows with timestamp locators. */
    suspend fun embedWavSegments(uri: Uri, windowMs: Long = 30_000L, overlapMs: Long = 5_000L): List<AudioEmbeddingSegment> = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to read audio")
        input.use {
            PcmWavWindowReader(it) { coroutine.ensureActive() }.use { reader ->
                val count = MultimodalResourcePolicy.segmentCount(reader.totalSamples, windowMs, overlapMs)
                val window = (16_000L * windowMs / 1000L).toInt()
                val overlap = (16_000L * overlapMs / 1000L).toInt()
                buildList {
                    var carry = FloatArray(0)
                    var start = 0L
                    repeat(count) {
                        coroutine.ensureActive()
                        val samples = carry + reader.readSamples(window - carry.size)
                        val end = start + samples.size
                        require(samples.isNotEmpty()) { "Audio window is empty" }
                        add(AudioEmbeddingSegment(start * 1000L / 16_000, end * 1000L / 16_000, embedAudio(samples)))
                        if (end < reader.totalSamples) {
                            carry = if (overlap == 0) FloatArray(0) else samples.copyOfRange(samples.size - overlap, samples.size)
                            start = end - carry.size
                        }
                    }
                }
            }
        }
    }

    /** Renders PDF pages to previews and embeds them in the same multimodal space. */
    suspend fun embedPdfPagePreviews(uri: Uri): List<PagePreviewEmbedding> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val coroutine = currentCoroutineContext()
        val file = File.createTempFile("hod-embedding-preview-", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    MultimodalResourcePolicy.copyBounded(input, output, MultimodalResourcePolicy.MAX_PDF_BYTES) { coroutine.ensureActive() }
                }
            } ?: error("Unable to read PDF")
            val memory = MemoryUsageSetting.setupMixed(8L * 1024 * 1024, 128L * 1024 * 1024).setTempDir(context.cacheDir)
            PDDocument.load(file, memory).use { document ->
            require(document.numberOfPages in 1..MultimodalResourcePolicy.MAX_PDF_PAGES) { "PDF preview exceeds ${MultimodalResourcePolicy.MAX_PDF_PAGES} pages; text retrieval remains available" }
            val renderer = PDFRenderer(document)
            (0 until document.numberOfPages).map { index ->
                coroutine.ensureActive()
                val page = document.getPage(index).cropBox
                require(page.width.isFinite() && page.height.isFinite() && page.width > 0 && page.height > 0) { "PDF page dimensions are invalid" }
                val scale = minOf(1f, MultimodalResourcePolicy.MAX_IMAGE_EDGE / maxOf(page.width, page.height))
                val bitmap = renderer.renderImage(index, scale)
                try {
                    val image = BitmapImageBuilder(bitmap).build()
                    try {
                        PagePreviewEmbedding(index + 1, inferenceMutex.withLock {
                            engine().embedImage(image).embeddings().first().floatEmbedding().copyOf().also {
                                check(EmbeddingIntegrity.validate(it).valid) { "Invalid PDF page embedding" }
                            }
                        })
                    } finally { image.close() }
                } finally { bitmap.recycle() }
            }
        }
        } finally { file.delete() }
    }

    private suspend fun decodeBoundedImage(uri: Uri): Bitmap {
        currentCoroutineContext().ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = context.contentResolver.openInputStream(uri) ?: error("Unable to read image")
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = MultimodalResourcePolicy.imageSampleSize(bounds.outWidth, bounds.outHeight)
        currentCoroutineContext().ensureActive()
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return (context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Unable to decode image for EmbeddingGemma")).also { bitmap ->
            if (bitmap.width > MultimodalResourcePolicy.MAX_IMAGE_EDGE || bitmap.height > MultimodalResourcePolicy.MAX_IMAGE_EDGE ||
                bitmap.width.toLong() * bitmap.height > MultimodalResourcePolicy.MAX_IMAGE_PIXELS) {
                bitmap.recycle()
                error("Decoded image exceeds safe bounds")
            }
        }
    }

    override fun close() {
        closed = true
        // Native teardown waits for any in-flight inference without blocking
        // Compose or closing an engine still executing on a native thread.
        cleanupScope.launch {
            inferenceMutex.withLock {
                runCatching { embedder?.close() }.onFailure { Log.w("HOD_SEMANTIC", "native_teardown_failed", it) }
                embedder = null
                textCache.clear()
                mediaCache.clear()
            }
        }
    }
}
