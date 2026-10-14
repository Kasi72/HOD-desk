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
import com.hoddesk.app.reasoning.ActionFieldQualityScorer
import com.hoddesk.app.reasoning.TemporalEvidence

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
        // Keep the persisted source faithful to the original document. The
        // ingest pipeline adds review cues once, after redaction; returning an
        // already annotated body here caused those cues to be re-parsed as
        // independent actions and produced duplicate/title tasks.
        return ImportedSource(name.ifBlank { sourceType }, text, sourceType, uri)
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
    private val action = Regex("(?iu)\\b(prepare|submit|send|share|review|approve|forward|attend|join|conduct|organize|organise|complete|verify|constitute|nominate|depute|report|schedule|meet|discuss|ensure|arrange|collect|participate|present|circulate|issue|obtain|coordinate|inform|notify|compile|file|sign|visit|instruct|direct|delegate|follow[- ]?up)\\b|செய்ய|செல்ல|கலந்து|தயார்|அனுப்ப|சமர்ப்பிக்க|பங்கேற்க|தெரிவிக்க|அறிக்கை")
    private val obligation = Regex("(?iu)\\b(shall|must|required to|requested to|directed to|instructed to|kindly|please|should|need(?:s)? to|is to|are to|will|responsible for|action required)\\b|வேண்டும்|வேண்டிய|உத்தரவு|அறிவுறுத்தல்")
    private val schedule = Regex("(?iu)\\b(meeting|appointment|conference|webinar|zoom|google meet|microsoft teams|video call|hearing|interview|ward round|teaching session|lecture|class|duty|roster|shift|on[- ]?call|timetable|schedule|rotation|exam|inspection|visit|collectorate|DME)\\b|கூட்டம்|சந்திப்பு|பயிற்சி|வகுப்பு")
    private val deadline = Regex("(?iu)\\b(by|before|until|within|on or before|no later than|deadline|due date|at exactly|from .* to|tomorrow|today)\\b|க்குள்|தேதி")
    // Official emails frequently use dotted Indian dates (13.10.2026) in
    // screenshots and PDF text. Keep slash, hyphen and dotted forms together
    // so the same value is grounded and can become a task due date.
    private val date = Regex("(?iu)\\b(?:today|tomorrow|this week|next week|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|\\d{1,2}(?:st|nd|rd|th)?[ -](?:January|February|March|April|May|June|July|August|September|October|November|December)(?:[ -]\\d{4})?|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4})\\b")
    private val time = Regex("(?iu)\\b(?:at\\s*)?([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(AM|PM)\\b")
    private val timeRange = Regex("(?iu)\\b(\\d{1,2}(?::[0-5]\\d)?)\\s*(?:-|–|—|to)\\s*(\\d{1,2}(?::[0-5]\\d)?)\\s*(AM|PM)\\b")
    private val location = Regex("\\b(?i:at|in|venue|location)\\s*[: ]\\s*([A-Z][A-Za-z0-9 .'-]{2,80})")
    private val onlineVenue = Regex("(?iu)\\b(Google Meet|Zoom|Microsoft Teams|video call|online meeting)\\b")
    private val owner = Regex("\\b(?i:owner|responsible(?: officer)?|assigned|delegate(?:d)?|depute(?:d)?)\\s*(?i:to|:)?\\s*((?:(?i:Dr|Mr|Ms|Mrs)\\.?\\s+)?[A-Z][A-Za-z.'-]*(?:\\s+[A-Z][A-Za-z.'-]*){0,4})")
    private val reference = Regex("(?iu)\\b(?:G\\.?O\\.?|GO|PRGI|Ref(?:erence)?|Proceedings|Order)\\s*(?:No\\.?|number|#)?\\s*[:./-]?\\s*([A-Z0-9][A-Z0-9./-]{2,40})")
    private val recurrence = Regex("(?iu)\\b(?:every\\s+(?:day|week|month|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)|daily|weekly|monthly|fortnightly|each\\s+(?:day|week|month))\\b")
    private val processingFailure = Regex("(?iu)^(?:audio is queued|transcription (?:pending|unavailable|failed)|ocr (?:could not|failed)|no readable text|no speech detected|image pending ocr|document pending processing|recording saved; transcription unavailable|legacy \\.doc format requires)")
    private val metadataOnly = Regex("(?iu)\\b(joining info|time zone|video call link|contact|thank(?:ing)? you|national health mission|help desk)\\b")

    private fun units(text: String): List<String> {
        // Keep numbered continuations attached to the instruction that
        // introduces them ("instruct the following officials:\n1. ...").
        // Splitting those lines into isolated units loses the people field.
        // Protect the dot in numbered prefixes ("1. Officer") from the
        // sentence splitter, then restore it after units are assembled.
        val protected = text.replace(Regex("(?m)(^|\\s)(\\d+)[.)](?=\\s)")) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}§"
        }.replace(Regex("(?i)\\b(Dr|Mr|Mrs|Ms|No|Ref)\\.")) { "${it.groupValues[1]}§" }
        val raw = protected.split(Regex("(?<=[.!?])\\s+(?!\\d)|\\r?\\n+"))
            .map { it.replace('§', '.').replace(Regex("\\s+"), " ").trim() }.filter { it.isNotBlank() }
        val merged = mutableListOf<String>()
        raw.forEach { line ->
            if (line.matches(Regex("^\\d+[.)]\\s+.+")) && merged.isNotEmpty() &&
                (merged.last().contains(':') || Regex("\\s\\d+[.)]\\s").containsMatchIn(merged.last()))) {
                merged[merged.lastIndex] = "${merged.last()} $line"
            } else merged += line
        }
        return merged.filter { it.length >= 8 }
    }
    private fun candidateUnits(text: String): List<String> {
        if (text.isBlank() || processingFailure.containsMatchIn(text.trim())) return emptyList()
        return units(text).flatMap { unit ->
            if (!isActionable(unit)) emptyList() else splitIndependentActions(unit)
        }.distinctBy { it.lowercase() }
    }

    private fun isActionable(unit: String): Boolean {
        if (processingFailure.containsMatchIn(unit) || Regex("(?i)^(?:from|to|cc|bcc|subject|date)\\b|government email services").containsMatchIn(unit)) return false
        if (metadataOnly.containsMatchIn(unit) && !obligation.containsMatchIn(unit)) return false
        // A subject, a date-bearing meeting announcement and a recipient name
        // are context, not instructions to the HOD. An imperative or an explicit
        // obligation must exist before a candidate is emitted.
        val start = unit.replace(Regex("(?i)^(?:hence|therefore)[, :]*\\s*"), "")
            .replace(Regex("(?i)^(?:please|kindly)\\s+"), "")
        val imperative = action.find(start)?.range?.first == 0
        val explicitObligation = obligation.containsMatchIn(unit) && action.containsMatchIn(unit)
        val statedDeadline = Regex("(?i)\\b(?:due|deadline|must be completed|to be submitted)\\b").containsMatchIn(unit)
        val tamilObligation = Regex("வேண்டும்|வேண்டிய|உத்தரவு|அறிவுறுத்தல்").containsMatchIn(unit) && action.containsMatchIn(unit)
        return imperative || explicitObligation || statedDeadline || tamilObligation
    }
    private fun people(text: String): List<String> {
        // Only accept explicit person/title forms here. Broad preposition
        // matching incorrectly labelled objects such as "Drug Return" or
        // "the programme" as people.
        val titled = Regex("\\b(?i:Dr|Mr|Ms|Mrs)\\.?\\s*[A-Z][A-Za-z.'-]*(?:\\s+[A-Z][A-Za-z.'-]*){0,4}").findAll(text)
            .map { it.value.trim().trimEnd('.', ',', ';') }.toList()
        val acronym = Regex("(?<![A-Za-z])(?:with|to|from|for|by|chair)\\s+([A-Z][A-Z0-9&/-]{1,20})(?![A-Za-z])").findAll(text)
            .map { it.groupValues[1].trim() }.toList()
        // Numbered official lists are common in government notices and are
        // otherwise invisible to the old preposition-based extractor.
        val listed = Regex("(?m)^\\s*\\d+[.)]\\s+(.+?)\\s*$").findAll(text)
            .map { it.groupValues[1].trim().trimEnd('.', ',', ';') }.toList() +
            // Candidate evidence is normalized to one line, so retain list
            // members when the source line breaks are no longer present.
            Regex("(?:^|\\s)\\d+[.)]\\s+(.+?)(?=\\s+\\d+[.)]\\s+|$)").findAll(text)
                .map { it.groupValues[1].trim().trimEnd('.', ',', ';') }.toList()
        val official = Regex("(?i)\\b(?:officers?|coordinators?|directors?|deans?|HODs?|secretar(?:y|ies)|collector|superintendent|residents?|faculty|professor|staff|Dr|Mr|Ms|Mrs)\\b")
        return (listed.filter { official.containsMatchIn(it) } + titled + acronym)
            .filter { it.length >= 3 }.distinct().take(20)
    }

    private fun canonicalAction(evidence: String, scheduleContext: String?): String = when {
        Regex("(?iu)\\b(?:requested|directed|instructed|kindly)\\b.*\\b(?:instruct|direct|depute)\\b.*\\bofficials?\\b.*\\battend\\b").containsMatchIn(evidence) ->
            "Instruct the listed officials to attend " + (scheduleContext?.let { context ->
                Regex("(?i)\\bto discuss\\s+(.+?)(?=\\s+(?:in HMIS|on \\d|at \\d)|[.!]|$)").find(context)?.groupValues?.get(1)?.trim()
                    ?.let { "the meeting on $it" }
            } ?: "the scheduled meeting")
        else -> evidence.trim().take(420)
    }

    private fun times(text: String): List<String> {
        return TemporalEvidence.uniqueTimes(text).map { it.text }
    }

    private fun splitIndependentActions(unit: String): List<String> {
        val clauses = unit.split(Regex("(?iu)\\s+(?:and then|also|then|and)\\s+|(?<=;)\\s+"))
            .map { it.trim() }.filter { it.length >= 8 }
        // “Drug Condemnation and Drug Return” is one object, not two actions.
        return if (clauses.size > 1 && clauses.all(::isActionable)) clauses else listOf(unit.take(1600))
    }

    fun annotate(text: String): String {
        if (text.isBlank()) return "No readable text was extracted. Keep this document for manual review."
        val candidates = candidateUnits(text).take(30)
        val dates = date.findAll(text).map { it.value }.distinct().take(20).toList()
        val times = times(text).take(20)
        val locations = (location.findAll(text).map { it.groupValues[1].trim() } +
            onlineVenue.findAll(text).map { it.groupValues[1].trim() }).distinct().take(10).toList()
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
        // Stored sources from older builds may already contain the generated
        // review-cue appendix. Never treat that machine-generated appendix as
        // source evidence or it will create title/duplicate actions.
        val sourceText = text.substringBefore("--- HOD Desk review cues")
        val sourceUnits = units(sourceText)
        val scheduleUnits = sourceUnits.withIndex().filter { (_, unit) -> isScheduleContext(unit) }
        val candidates = sourceUnits.withIndex().flatMap { (index, unit) ->
            if (!isActionable(unit)) emptyList() else splitIndependentActions(unit).map { index to it }
        }
        return candidates.map { (index, evidence) ->
            // Link only the adjacent, explicitly referred-to event. The previous
            // global “first meeting” fallback attached its date to every later
            // attendance/instruction, including unrelated meetings.
            val refersToMeeting = Regex("(?i)\\b(?:attend|join|instruct|depute)\\b").containsMatchIn(evidence) &&
                !Regex("(?i)\\b(?:another|different|separate|other)\\s+(?:meeting|conference|appointment)\\b").containsMatchIn(evidence)
            val adjacent = scheduleUnits.filter { it.index != index && it.index in (index - 2)..(index + 1) }
                .minByOrNull { kotlin.math.abs(it.index - index) }
                ?.takeIf { refersToMeeting && TemporalEvidence.times(evidence).isEmpty() }
            val scheduleContext = adjacent?.value
            val linked = mutableListOf(evidence)
            if (scheduleContext != null) linked += scheduleContext
            val eventIndex = adjacent?.index ?: index
            if (refersToMeeting && (scheduleContext != null || isScheduleContext(evidence))) {
                // The invitation block and platform belong to this event only
                // when there is one event in the document or they are adjacent.
                sourceUnits.withIndex().filter { (position, unit) ->
                    (scheduleUnits.size <= 1 || kotlin.math.abs(position - eventIndex) <= 3) &&
                        (onlineVenue.containsMatchIn(unit) || isCalendarInvite(unit))
                }.forEach { linked += it.value }
            }
            // Related passages resolve terse references only. They are deliberately
            // excluded from date/time/people extraction: similarity is not proof
            // that a previous message's deadline governs this action.
            val extractionEvidence = linked.distinct().joinToString("\n")
            val dates = TemporalEvidence.uniqueDates(extractionEvidence)
            val dateText = dates.map { it.text }.joinToString(", ").ifBlank { null }
            val timeText = times(extractionEvidence).joinToString(", ").ifBlank { null }
            val explicitLocations = locations(extractionEvidence)
            val onlineLocations = onlineVenue.findAll(extractionEvidence).map { it.groupValues[1].trim() }
                .filterNot { it.equals("video call", true) || it.equals("online meeting", true) }
            val locationText = (explicitLocations + onlineLocations).distinct().joinToString(", ").ifBlank { null }
            // Officials are mentioned in the actual instruction, not inherited
            // from an email To/Cc block or the meeting's other participants.
            val peopleValues = people(evidence)
            val peopleText = peopleValues.joinToString("; ").ifBlank { null }
            val ownerText = owner.find(extractionEvidence)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', ',', ';')
            val referenceNumber = reference.find(extractionEvidence)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { value -> value.any(Char::isDigit) && TemporalEvidence.parseDate(value) == null }
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
            val ambiguity = buildList {
                if (dates.size > 1) add("Date conflict: multiple distinct dates require confirmation.")
                if (TemporalEvidence.hasTimeConflict(extractionEvidence)) add("Time conflict: ${times(extractionEvidence).joinToString(" versus ")} requires confirmation.")
                addAll(TemporalEvidence.weekdayConflicts(extractionEvidence, dateText))
                if (dateText == null && timeText == null) add("Date and time were not explicit in this evidence.")
                if (scheduleContext != null && Regex("(?i)\\b(?:instruct|depute|direct)\\b.*\\battend\\b").containsMatchIn(evidence))
                    add("Meeting schedule is context; instruction deadline requires confirmation.")
                if (ownerText == null && Regex("(?iu)\\b(delegate|assigned|responsible)\\b").containsMatchIn(evidence)) add("Owner requires confirmation.")
            }.joinToString("; ").ifBlank { null }
            val finalAction = canonicalAction(evidence, scheduleContext)
            val quality = ActionFieldQualityScorer.assess(finalAction, dateText, timeText, locationText, peopleValues, extractionEvidence)
            val qualityIssues = quality.issues.filter { it.contains("requires confirmation") }.take(3)
            val finalAmbiguity = listOfNotNull(ambiguity, qualityIssues.joinToString("; ").ifBlank { null }).distinct().joinToString("; ").ifBlank { null }
            ActionPointDraft(finalAction, dateText, timeText, locationText, peopleText, extractionEvidence, minOf(confidence, quality.overall.coerceAtLeast(.25f)), finalAmbiguity, category, ownerText, referenceNumber, recurrenceRule)
        }.distinctBy { listOf(it.action.lowercase().replace(Regex("\\W+"), " ").trim(), it.dateText, it.timeText, it.peopleText) }
    }

    private fun isScheduleContext(unit: String): Boolean =
        Regex("(?i)\\b(?:meeting|conference|appointment|webinar)\\b").containsMatchIn(unit) &&
            (Regex("(?i)\\b(?:scheduled|fixed|arranged|will be held|is on)\\b").containsMatchIn(unit) ||
                Regex("(?i)^(?:Zoom|Google Meet|online)\\s+meeting\\b").containsMatchIn(unit)) &&
            (TemporalEvidence.dates(unit).isNotEmpty() || TemporalEvidence.times(unit).isNotEmpty())

    private fun isCalendarInvite(unit: String): Boolean =
        Regex("(?i)\\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b").containsMatchIn(unit) &&
            TemporalEvidence.dates(unit).isNotEmpty() && TemporalEvidence.times(unit).isNotEmpty()

    private fun locations(text: String): List<String> = location.findAll(text).mapNotNull { match ->
        val value = match.groupValues[1].split(Regex("(?i)\\s+(?:on|at|by|before|to discuss|with)\\s+")).first()
            .trim().trimEnd('.', ',', ';')
        // A capitalized software acronym (“in HMIS 3.0”) is not a venue.
        if (Regex("(?i)\\b(?:college|hospital|office|collectorate|room|campus|hall|centre|center|department|auditorium|DME)\\b").containsMatchIn(value) ||
            match.value.startsWith("venue", true) || match.value.startsWith("location", true)) value else null
    }.toList()

    fun isProcessingFailure(text: String): Boolean = text.trim().let { it.isBlank() || processingFailure.containsMatchIn(it) }
}


