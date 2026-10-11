package com.hoddesk.app.imports

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import com.googlecode.tesseract.android.TessBaseAPI

data class ImportedSource(val title: String, val body: String, val type: String, val uri: Uri? = null)

enum class TranscriptionStatus { QUEUED, READY, NEEDS_REVIEW, FAILED }

interface AudioTranscriptionProcessor {
    fun status(): TranscriptionStatus
}

class PendingAudioTranscriptionProcessor : AudioTranscriptionProcessor {
    override fun status(): TranscriptionStatus = TranscriptionStatus.QUEUED
}

class WhatsAppExportAdapter {
    private val datedLine = Regex("^(?:\u200e)?(?:\\[)?(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4},? \\d{1,2}:\\d{2}(?::\\d{2})?(?: ?[AP]M)?)(?:\\])?\\s*[-:]\\s*(.*)$", RegexOption.IGNORE_CASE)

    /** Parses WhatsApp's Android/iOS text export, retaining sender and timestamp and joining wrapped lines. */
    fun parse(text: String): List<ImportedSource> {
        val messages = mutableListOf<Pair<String, String>>()
        var currentHeader = ""
        val currentBody = StringBuilder()
        fun flush() {
            if (currentBody.isNotBlank()) messages += currentHeader to currentBody.toString().trim()
            currentBody.clear()
        }
        text.lineSequence().forEach { raw ->
            val line = raw.removePrefix("\u200e").trimEnd()
            val match = datedLine.find(line)
            if (match != null) {
                flush(); currentHeader = match.groupValues[1]; currentBody.append(match.groupValues[2])
            } else if (currentBody.isNotBlank()) {
                currentBody.append('\n').append(line)
            } else if (line.isNotBlank()) {
                currentHeader = "Imported message"; currentBody.append(line)
            }
        }
        flush()
        return messages.mapIndexed { index, (header, body) ->
            val sender = body.substringBefore(": ").takeIf { it.length in 1..80 && !it.contains("http") }
            val message = if (sender != null) body.substringAfter(": ") else body
            val title = buildString { append("WhatsApp"); sender?.let { append(" · ").append(it) }; append(" · ").append(header.ifBlank { "message ${index + 1}" }) }
            ImportedSource(title, message, "WhatsApp", null)
        }
    }

    fun parseZip(input: java.io.InputStream): List<ImportedSource> {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/').startsWith("_chat", ignoreCase = true) && entry.name.endsWith(".txt", ignoreCase = true)) {
                    return parse(zip.bufferedReader(Charsets.UTF_8).readText())
                }
            }
        }
        return emptyList()
    }
}

class ScreenshotImportAdapter {
    fun create(uri: Uri): ImportedSource = ImportedSource("Screenshot capture", "Image pending OCR review", "Screenshot", uri)

    fun recognize(context: Context, uri: Uri): com.google.android.gms.tasks.Task<ImportedSource> {
        val image = runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            requireNotNull(bitmap) { "Screenshot could not be decoded" }
            InputImage.fromBitmap(preprocess(bitmap), 0)
        }.getOrElse { InputImage.fromFilePath(context, uri) }
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).continueWith(Executors.newSingleThreadExecutor()) { task ->
            val text = normalizeOcr(task.result?.text.orEmpty())
            val tamil = if (text.any { it in '\u0B80'..'\u0BFF' }) "" else runCatching { recognizeTamil(context, uri) }.getOrDefault("")
            val merged = listOf(text, tamil).filter { it.isNotBlank() }.distinct().joinToString("\n")
            val title = merged.lineSequence().map { it.trim() }.firstOrNull { it.length >= 8 }?.take(72) ?: "Screenshot capture"
            ImportedSource(title, merged.ifBlank { "OCR found no readable text; Tamil OCR also found no readable text" }, "Screenshot", uri)
        }
    }

    private fun recognizeTamil(context: Context, uri: Uri): String {
        val base = File(context.filesDir, "tessdata")
        if (!base.exists()) base.mkdirs()
        val trained = File(base, "tam.traineddata")
        if (!trained.exists()) context.assets.open("tessdata/tam.traineddata").use { input -> FileOutputStream(trained).use { input.copyTo(it) } }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return ""
        val api = TessBaseAPI()
        return try {
            if (!api.init(context.filesDir.absolutePath, "tam")) return ""
            api.setImage(bitmap)
            api.getUTF8Text().orEmpty()
        } finally { api.end(); bitmap.recycle() }
    }

    private fun preprocess(source: Bitmap): Bitmap {
        val scale = if (source.width < 1600) 1.5f else 1f
        val out = Bitmap.createBitmap((source.width * scale).toInt(), (source.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawBitmap(source, null, android.graphics.Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }) }
        if (out !== source) source.recycle()
        return out
    }

    private fun normalizeOcr(raw: String): String = raw
        .replace('\u00A0', ' ')
        .replace(Regex("(?m)([A-Za-z])[-–]\\s*$"), "$1")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\\n\\n")
        .trim()
}

class AudioTranscriptImportAdapter(private val resolver: ContentResolver) {
    fun importTranscript(uri: Uri): ImportedSource {
        val text = resolver.openInputStream(uri)?.use { input -> BufferedReader(InputStreamReader(input)).readText() }.orEmpty()
        return ImportedSource("Call transcript", text, "Audio transcript", uri)
    }

    fun importAudio(uri: Uri): ImportedSource = ImportedSource(
        "Call recording pending transcription",
        "Audio is queued for the approved offline transcription runtime. Review the recording before creating tasks.",
        "Audio pending transcription",
        uri
    )
}
