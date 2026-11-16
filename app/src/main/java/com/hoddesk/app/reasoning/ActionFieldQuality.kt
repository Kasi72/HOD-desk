package com.hoddesk.app.reasoning

/** Field-level quality used to keep uncertain extraction reviewable. */
data class ActionFieldQuality(
    val fields: Map<String, Float>,
    val overall: Float,
    val sourceReliability: Float,
    val issues: List<String>
)

object ActionFieldQualityScorer {
    fun assess(action: String, date: String?, time: String?, location: String?, people: List<String>, evidence: String): ActionFieldQuality {
        val grounded = FieldGrounding.validate(evidence, date, time, location, people, emptyList())
        val reliability = sourceReliability(evidence)
        val fields = linkedMapOf<String, Float>()
        fields["action"] = if (action.isNotBlank() && FieldGrounding.supportsAction(evidence, action)) reliability else 0f
        if (date != null) fields["date"] = if (grounded.date != null) reliability else 0.25f
        if (time != null) fields["time"] = if (grounded.time != null) reliability else 0.25f
        if (location != null) fields["location"] = if (grounded.location != null) reliability else 0.25f
        if (people.isNotEmpty()) fields["people"] = if (grounded.people.size == people.size) reliability else 0.35f
        val overall = if (fields.isEmpty()) 0f else fields.values.average().toFloat().coerceIn(0f, 1f)
        return ActionFieldQuality(fields, overall, reliability, grounded.ambiguities.distinct())
    }

    private fun sourceReliability(evidence: String): Float {
        // This is a rule-based ranking weight, never a calibrated accuracy or
        // sender-authenticity score. Official vocabulary cannot make OCR more reliable.
        return if (Regex("(?i)\\b(?:ocr|transcription)\\s+(?:failed|unavailable|pending)\\b").containsMatchIn(evidence)) .25f else .75f
    }
}
