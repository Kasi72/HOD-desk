package com.hoddesk.app.reasoning

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Installs an approved LiteRT-LM instruction model atomically in app-private storage.
 * The URL and SHA-256 are supplied by the deployment configuration; the app never
 * accepts an unverified model as a reasoning engine.
 */
class InstructionModelStore(
    context: Context,
    private val downloadUrl: String?,
    private val expectedSha256: String?
) {
    companion object {
        private const val TAG = "HOD_REASONING"
        private const val MIN_BYTES = 100_000_000L
    }

    private val target = File(context.filesDir, "hoddesk-instruction.litertlm")

    fun installedPath(): String? = target.takeIf { isValid(it) }?.absolutePath

    suspend fun install(onProgress: (Int) -> Unit = {}): String = withContext(Dispatchers.IO) {
        require(!downloadUrl.isNullOrBlank()) { "No approved instruction model URL configured" }
        require(!expectedSha256.isNullOrBlank()) { "No instruction model SHA-256 configured" }
        val part = File(target.parentFile, "${target.name}.part")
        if (part.exists()) part.delete()
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Instruction model download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
            check(isValid(part)) { "Downloaded instruction model is incomplete" }
            check(sha256(part).equals(expectedSha256, ignoreCase = true)) { "Instruction model SHA-256 mismatch" }
            // ModelInfo performs LiteRT-LM's native container validation before install.
            ModelInfo.from(part.absolutePath).use { info ->
                check(info.modelType::class.java.simpleName.contains("Llm", ignoreCase = true)) {
                    "Configured model is not an instruction LLM"
                }
            }
            if (target.exists()) target.delete()
            check(part.renameTo(target)) { "Unable to activate instruction model" }
            Log.i(TAG, "instruction_model_installed_bytes=${target.length()}")
            target.absolutePath
        } finally {
            connection.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun isValid(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_BYTES) return false
        return expectedSha256.isNullOrBlank() || sha256(file).equals(expectedSha256, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
