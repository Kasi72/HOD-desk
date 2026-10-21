package com.hoddesk.app.workflow

/** A stable workflow destination shared by every capture modality. */
enum class CaptureIntentKind(val wire: String) {
    TASK("task"), MEETING("meeting"), DEADLINE("deadline"), FYI("fyi"),
    DELEGATION("delegation"), FOLLOW_UP("follow_up"), REFERENCE("reference");

    companion object {
        fun fromWire(value: String?): CaptureIntentKind = entries.firstOrNull { it.wire == value } ?: REFERENCE
    }
}

data class CaptureIntent(val kind: CaptureIntentKind, val confidence: Float, val reasons: List<String>)

/** Deterministic first pass. Gemma may refine fields later, but never chooses an unsafe destination silently. */
object CaptureIntentClassifier {
    fun classify(title: String, body: String, sourceType: String): CaptureIntent {
        val text = "$title $body".lowercase()
        fun hit(vararg terms: String) = terms.any { text.contains(it) }
        return when {
            hit("delegate", "depute", "assigned to", "responsible for", "forward to", "ஒப்படைக்க", "பொறுப்பு") ->
                CaptureIntent(CaptureIntentKind.DELEGATION, .91f, listOf("delegation language"))
            hit("follow up", "follow-up", "remind", "pending", "awaiting", "will confirm", "தொடர்ந்து", "நினைவூட்ட") ->
                CaptureIntent(CaptureIntentKind.FOLLOW_UP, .88f, listOf("follow-up language"))
            hit("meeting", "conference", "appointment", "webinar", "கூட்டம்", "சந்திப்பு") &&
                (hit("scheduled", "at ", "on ", "tomorrow", "today", "நாளை", "இன்று") || sourceType.contains("calendar", true)) ->
                CaptureIntent(CaptureIntentKind.MEETING, .94f, listOf("meeting and schedule cues"))
            hit("submit", "before", "deadline", "due", "by ", "சமர்ப்பிக்க", "கடைசி தேதி") ->
                CaptureIntent(CaptureIntentKind.DEADLINE, .89f, listOf("deadline language"))
            hit("attend", "join", "prepare", "send", "share", "review", "report", "arrange", "complete", "கலந்து", "அனுப்ப", "தயார்") ->
                CaptureIntent(CaptureIntentKind.TASK, .82f, listOf("action language"))
            hit("for record", "fyi", "information only", "for information", "தகவலுக்கு") ->
                CaptureIntent(CaptureIntentKind.FYI, .93f, listOf("information-only language"))
            else -> CaptureIntent(CaptureIntentKind.REFERENCE, .70f, listOf("no explicit action language"))
        }
    }
}
