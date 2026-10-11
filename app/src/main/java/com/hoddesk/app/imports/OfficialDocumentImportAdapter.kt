package com.hoddesk.app.imports

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.zip.ZipInputStream
import com.hoddesk.app.repository.ActionPointDraft

/** Reads official Word, PDF and plain-text records without sending their contents off device. */
class OfficialDocumentImportAdapter(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun import(uri: Uri, preferredType: String? = null): ImportedSource {
        val name = displayName(uri)
        val mime = resolver.getType(uri).orEmpty().lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        val text = resolver.openInputStream(uri)?.use { input ->
            when {
                extension == "docx" || mime.contains("wordprocessingml") -> extractDocx(input)
                extension == "pdf" || mime.contains("pdf") -> extractPdf(input)
                extension == "doc" -> "Legacy .doc format requires conversion to .docx or PDF before import."
                else -> input.bufferedReader(Charsets.UTF_8).readText()
            }
        }.orEmpty().trim()
        val sourceType = preferredType ?: when {
            extension == "doc" || extension == "docx" || mime.contains("word") -> "Official order · Word"
            extension == "pdf" || mime.contains("pdf") -> "Official order · PDF"
            else -> "Official order · Text"
        }
        val insight = ActionPointExtractor().annotate(text)
        return ImportedSource(name.ifBlank { sourceType }, insight, sourceType, uri)
    }

    private fun displayName(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0).orEmpty() else ""
    } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

    private fun extractDocx(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                    return Html.fromHtml(xml.replace(Regex("</w:p>"), "\n").replace(Regex("<w:tab[^>]*/>"), "\t").replace(Regex("<[^>]+>"), ""), Html.FROM_HTML_MODE_LEGACY).toString()
                }
            }
        }
        return ""
    }

    private fun extractPdf(input: InputStream): String {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(input).use { document ->
            val stripper = PDFTextStripper()
            return buildString {
                for (page in 1..document.numberOfPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    append("[Page ").append(page).append("]\n")
                    append(stripper.getText(document).trim())
                    append("\n\n")
                }
            }.trim()
        }
    }
}

/** Conservative extraction: every suggestion remains reviewable before a task/calendar write. */
class ActionPointExtractor {
    private val action = Regex("(?iu)\\b(prepare|submit|send|share|review|approve|forward|attend|join|conduct|organize|organise|complete|verify|constitute|nominate|depute|report|schedule|meet|discuss|ensure|arrange|collect|participate|present|circulate|issue|obtain|coordinate|inform|notify|compile|file|sign|visit|follow[- ]?up|deadline|due)\\b|செய்ய|செல்ல|கலந்து|தயார்|அனுப்ப|சமர்ப்பிக்க|பங்கேற்க|தெரிவிக்க|அறிக்கை")
    private val obligation = Regex("(?iu)\\b(shall|must|required to|requested to|directed to|instructed to|kindly|please|should|need(?:s)? to|is to|are to|will|responsible for|action required)\\b|வேண்டும்|வேண்டிய|உத்தரவு|அறிவுறுத்தல்")
    private val schedule = Regex("(?iu)\\b(meeting|appointment|conference|webinar|zoom|google meet|microsoft teams|video call|hearing|interview|ward round|teaching session|lecture|class|duty|roster|shift|on[- ]?call|timetable|schedule|rotation|exam|inspection|visit|collectorate|DME)\\b|கூட்டம்|சந்திப்பு|பயிற்சி|வகுப்பு")
    private val deadline = Regex("(?iu)\\b(by|before|until|within|on or before|no later than|deadline|due date|at exactly|from .* to|tomorrow|today)\\b|க்குள்|தேதி")
    private val date = Regex("(?iu)\\b(?:today|tomorrow|this week|next week|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|\\d{1,2}(?:st|nd|rd|th)?[ -](?:January|February|March|April|May|June|July|August|September|October|November|December)(?:[ -]\\d{4})?|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b")
    private val time = Regex("(?iu)\\b(?:at\\s*)?([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(AM|PM)\\b")
    private val location = Regex("(?iu)\\b(?:at|in|venue[: ]|location[: ])\\s+([A-Z][A-Za-z .'-]{2,80}(?:college|hospital|office|collectorate|DME|room|campus|centre|center)?)")
    private val owner = Regex("(?iu)\\b(?:owner|responsible(?: officer)?|assigned|delegate(?:d)?|depute(?:d)?)\\s*(?:to|:)?\\s+([A-Z][A-Za-z .'-]{2,60})")
    private val reference = Regex("(?iu)\\b(?:G\\.?O\\.?|GO|PRGI|Ref(?:erence)?|Proceedings|Order)\\s*(?:No\\.?|number|#)?\\s*[:./-]?\\s*([A-Z0-9][A-Z0-9./-]{2,40})")
    private val recurrence = Regex("(?iu)\\b(?:every\\s+(?:day|week|month|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)|daily|weekly|monthly|fortnightly|each\\s+(?:day|week|month))\\b")
    private val processingFailure = Regex("(?iu)^(?:audio is queued|transcription (?:pending|unavailable|failed)|ocr (?:could not|failed)|no readable text|no speech detected|image pending ocr|document pending processing|recording saved; transcription unavailable)")

    private fun units(text: String): List<String> = text.split(Regex("(?<=[.!?])\\s+(?!\\d)|\\r?\\n+"))
        .map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length >= 8 }
    private fun candidateUnits(text: String): List<String> {
        if (text.isBlank() || processingFailure.containsMatchIn(text.trim())) return emptyList()
        return units(text).flatMap { unit ->
            if (!action.containsMatchIn(unit) && !obligation.containsMatchIn(unit) && !schedule.containsMatchIn(unit) && !deadline.containsMatchIn(unit)) emptyList()
            else splitIndependentActions(unit)
        }.distinctBy { it.lowercase() }
    }
    private fun people(text: String): List<String> = Regex("(?iu)\\b(?:with|to|from|for|by|official|officer|chair)\\s+([A-Z][A-Za-z .'-]{2,60})").findAll(text)
        .map { it.groupValues[1].trim().trimEnd('.', ',', ';') }.filter { it.split(' ').size <= 8 }.distinct().take(10).toList()

    private fun splitIndependentActions(unit: String): List<String> {
        val clauses = unit.split(Regex("(?iu)\\s+(?:and then|also|then|and)\\s+|(?<=;)\\s+"))
            .map { it.trim() }.filter { it.length >= 8 }
        return if (clauses.size > 1 && clauses.count { action.containsMatchIn(it) || obligation.containsMatchIn(it) } > 1) clauses else listOf(unit.take(420))
    }

    fun annotate(text: String): String {
        if (text.isBlank()) return "No readable text was extracted. Keep this document for manual review."
        val candidates = candidateUnits(text).take(30)
        val dates = date.findAll(text).map { it.value }.distinct().take(20).toList()
        val times = time.findAll(text).map { it.value.trim() }.distinct().take(20).toList()
        val locations = location.findAll(text).map { it.groupValues[1].trim() }.distinct().take(10).toList()
        val officials = people(text)
        return buildString {
            append(text)
            append("\n\n--- HOD Desk review cues (on-device extraction) ---\n")
            if (candidates.isEmpty()) append("No definite action sentence detected; review the document manually.\\n")
            else candidates.forEach { append("Action candidate: ").append(it).append('\n') }
            if (dates.isNotEmpty()) append("Dates/times to verify: ").append(dates.joinToString(", ")).append('\n')
            if (times.isNotEmpty()) append("Clock times to verify: ").append(times.joinToString(", ")).append('\n')
            if (locations.isNotEmpty()) append("Locations to verify: ").append(locations.joinToString(", ")).append('\n')
            if (officials.isNotEmpty()) append("People/officials to verify: ").append(officials.joinToString(", ")).append('\n')
            append("Nothing is scheduled or approved automatically.")
        }
    }

    fun extract(text: String, relatedContext: List<String> = emptyList()): List<ActionPointDraft> {
        return candidateUnits(text).map { evidence ->
            // Related chunks are consulted only for pronouns and explicit follow-up references.
            // They never broaden ordinary sentence extraction, preventing unrelated dates from leaking in.
            val needsContext = Regex("(?iu)\\b(it|this|that|these|those|same|follow[- ]?up|pending|as discussed|above)\\b").containsMatchIn(evidence)
            val extractionEvidence = if (needsContext && relatedContext.isNotEmpty()) {
                evidence + " " + relatedContext.take(3).joinToString(" ").take(600)
            } else evidence
            val dateText = date.findAll(extractionEvidence).map { it.value }.distinct().joinToString(", ").ifBlank { null }
            val timeText = time.findAll(extractionEvidence).map { it.value.trim().replace(Regex("(?iu)^at\\s+"), "") }.distinct().joinToString(", ").ifBlank { null }
            val locationText = location.findAll(extractionEvidence).map { it.groupValues[1].trim() }.distinct().joinToString(", ").ifBlank { null }
            val peopleText = people(extractionEvidence).joinToString(", ").ifBlank { null }
            val ownerText = owner.find(extractionEvidence)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', ',', ';')
            val referenceNumber = reference.find(extractionEvidence)?.value?.trim()
                ?: Regex("\\b\\d{2,}[A-Z0-9./-]*\\b", RegexOption.IGNORE_CASE).find(extractionEvidence)?.value
            val recurrenceRule = recurrence.find(extractionEvidence)?.value?.trim()
            val category = when {
                Regex("(?iu)\\b(G\\.?O\\.?|government order|proceedings|official order|circular)\\b").containsMatchIn(evidence) -> "government_order"
                Regex("(?iu)\\b(delegate|deputed|assigned to|responsible for|forward to)\\b").containsMatchIn(evidence) -> "delegation"
                Regex("(?iu)\\b(teach|lecture|class|resident|rotation|academic|exam)\\b|கற்பி|வகுப்பு").containsMatchIn(evidence) -> "teaching_or_training"
                Regex("(?iu)\\b(duty|roster|shift|on[- ]?call|ward round)\\b").containsMatchIn(evidence) -> "duty_or_roster"
                Regex("(?iu)\\b(follow[- ]?up|remind|await|pending)\\b").containsMatchIn(evidence) -> "follow_up"
                schedule.containsMatchIn(evidence) -> "meeting_or_appointment"
                deadline.containsMatchIn(evidence) -> "deadline_or_submission"
                else -> "official_work"
            }
            val confidence = when {
                dateText != null && timeText != null && (locationText != null || peopleText != null) -> .97f
                dateText != null && timeText != null -> .93f
                dateText != null || timeText != null || schedule.containsMatchIn(evidence) || deadline.containsMatchIn(evidence) -> .86f
                else -> .74f
            }
            val ambiguity = when {
                date.findAll(extractionEvidence).count() > 1 || time.findAll(extractionEvidence).count() > 1 -> "Multiple date/time values require confirmation."
                dateText == null && timeText == null -> "Date and time were not explicit in this evidence."
                ownerText == null && Regex("(?iu)\\b(delegate|assigned|responsible)\\b").containsMatchIn(evidence) -> "Owner requires confirmation."
                else -> null
            }
            ActionPointDraft(evidence, dateText, timeText, locationText, peopleText, evidence, if (ambiguity != null) minOf(confidence, .68f) else confidence, ambiguity, category, ownerText, referenceNumber, recurrenceRule)
        }.distinctBy { it.action.lowercase().replace(Regex("\\W+"), " ").trim() }
    }

    fun isProcessingFailure(text: String): Boolean = text.trim().let { it.isBlank() || processingFailure.containsMatchIn(it) }
}


