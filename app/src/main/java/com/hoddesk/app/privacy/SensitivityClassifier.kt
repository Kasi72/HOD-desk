package com.hoddesk.app.privacy

enum class SensitivityTier(val code: String) { T0_PUBLIC("T0"), T1_INTERNAL("T1"), T2_PERSONNEL("T2"), T3_CLINICAL("T3"), T4_SPECIAL("T4") }

data class SensitivityDecision(val tier: SensitivityTier, val reasons: List<String>)

/** Fail-closed classifier. It labels content; it does not delete or transmit it. */
object SensitivityClassifier {
    fun classify(title: String, body: String): SensitivityDecision {
        val text = "$title $body".lowercase()
        val special = listOf("suicide", "self-harm", "minor", "child abuse", "mhca", "mental health care act", "legal notice").filter(text::contains)
        if (special.isNotEmpty()) return SensitivityDecision(SensitivityTier.T4_SPECIAL, special.map { "special: $it" })
        // Operational hospital language is common in official schedules and
        // should remain visible in Library/Review. Require patient-specific
        // evidence before quarantining content; generic words such as ward,
        // doctor, ECT, medical, or treatment alone are not enough.
        val clinical = listOf(
            "patient", "diagnosis", "relapse", "admitted", "discharge", "bed no", "psychosis", "depression", "bipolar", "uhid", "mrd no", "op no",
            "நோயாளி", "சிகிச்சை", "மருந்து", "மனநலம்", "வெளியேற்றம்", "நோயறிதல்"
        ).filter(text::contains).toMutableList()
        if (Regex("(?i)\\b(?:uhid|mrd|ip|op|patient)\\s*[-:/#]?\\s*\\d{3,}\\b").containsMatchIn(text)) clinical += "clinical identifier"
        if (clinical.isNotEmpty()) return SensitivityDecision(SensitivityTier.T3_CLINICAL, clinical.distinct().take(3).map { "clinical: $it" })
        val personnel = listOf("leave", "disciplinary", "service book", "salary", "confidential staff").filter(text::contains)
        if (personnel.isNotEmpty()) return SensitivityDecision(SensitivityTier.T2_PERSONNEL, personnel.map { "personnel: $it" })
        val internal = listOf("roster", "duty", "meeting", "internal", "committee", "department").filter(text::contains)
        if (internal.isNotEmpty()) return SensitivityDecision(SensitivityTier.T1_INTERNAL, internal.take(3).map { "internal: $it" })
        return SensitivityDecision(SensitivityTier.T0_PUBLIC, listOf("no sensitive cues detected"))
    }
}
