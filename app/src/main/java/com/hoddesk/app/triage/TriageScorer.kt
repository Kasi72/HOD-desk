package com.hoddesk.app.triage

import com.hoddesk.app.data.SourceEntity

enum class TriageBucket { ACT, DELEGATE, WAITING, FYI, NOT_MINE }

data class TriageDecision(val bucket: TriageBucket, val score: Int, val reasons: List<String>)

/** Explainable PA-style triage. It ranks urgency; it never approves a task. */
object TriageScorer {
    fun score(source: SourceEntity): TriageDecision {
        val text = "${source.title} ${source.body}".lowercase()
        val reasons = mutableListOf<String>()
        var score = 0
        when {
            listOf("collector", "dme", "director", "dean", "secretary", "superintendent").any(text::contains) -> { score += 4; reasons += "high-authority sender or office" }
            text.contains("hod") || text.contains("head of department") -> { score += 3; reasons += "addressed to HOD" }
        }
        if (listOf("reminder", "urgent", "most urgent", "without fail", "explanation called for", "immediately", "முக்கியம்", "அவசரம்").any(text::contains)) { score += 3; reasons += "escalation wording" }
        if (listOf("attend", "submit", "send", "report", "arrange", "review", "collect", "join", "share", "follow up", "வாருங்கள்", "சமர்ப்பிக்க", "அனுப்ப", "கலந்து", "தொடர்ந்து").any(text::contains)) { score += 2; reasons += "action language" }
        if (Regex("\\b\\d{1,2}([:/.-]\\d{1,2})?\\b").containsMatchIn(text) || listOf("today", "tomorrow", "tonight", "this week", "நாளை", "இன்று", "தேதி", "by ").any(text::contains)) { score += 2; reasons += "date or time cue" }
        val notMine = listOf("hod surgery", "hod medicine", "hod orthopaedics", "for surgery department").any(text::contains)
        val waiting = listOf("will send", "promised", "awaiting", "waiting for", "pending from", "will confirm", "பின்னர் அனுப்ப").any(text::contains)
        val delegate = listOf("ask dr", "assign", "delegate", "team to", "staff to", "ஒப்படைக்க", "பொறுப்பு").any(text::contains)
        val bucket = when {
            notMine -> TriageBucket.NOT_MINE
            waiting -> TriageBucket.WAITING
            delegate -> TriageBucket.DELEGATE
            score >= 6 -> TriageBucket.ACT
            else -> TriageBucket.FYI
        }
        return TriageDecision(bucket, score, reasons.take(2))
    }
}
