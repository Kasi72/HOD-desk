package com.hoddesk.app.semantic

/** Lightweight offline normalization for Tamil inflections and Tanglish queries. */
object TamilTextNormalizer {
    private val tanglish = mapOf(
        "kootam" to "கூட்டம்", "koottam" to "கூட்டம்", "meeting" to "கூட்டம்",
        "santhippu" to "சந்திப்பு", "santippu" to "சந்திப்பு", "neram" to "நேரம்",
        "naal" to "நாள்", "thethi" to "தேதி", "idam" to "இடம்", "collectorate" to "கலெக்டரேட்",
        "anuppu" to "அனுப்பு", "anuppu" to "அனுப்பு", "samarppikka" to "சமர்ப்பிக்க",
        "kalantu" to "கலந்து", "kalando" to "கலந்து", "varavendum" to "வரவேண்டும்",
        "seyyavendum" to "செய்யவேண்டும்", "followup" to "தொடர்ந்து பார்க்க"
    )

    fun normalize(text: String): String = text.lowercase()
        .replace('\u00a0', ' ')
        .replace(Regex("[\u200B-\u200D\uFEFF]"), "")
        .replace(Regex("[\u2018\u2019]"), "'")
        .replace(Regex("[\u201C\u201D]"), "\"")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun expandTanglish(text: String): String {
        val normalized = normalize(text)
        val mapped = normalized.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
            .map { tanglish[it] ?: it }
        return (listOf(normalized) + mapped).joinToString(" ")
    }

    fun tamilStem(token: String): String {
        var value = normalize(token)
        listOf("களில்", "ங்களில்", "த்தில்", "த்துக்கு", "த்திற்கும்", "களுக்கு", "க்கு", "இல்", "ஆக", "ஐ", "ம்")
            .forEach { suffix -> if (value.length > suffix.length + 2) value = value.removeSuffix(suffix) }
        return value
    }
}
