package com.hoddesk.app.secretary

data class SecretaryIntent(
    val kind: Kind,
    val query: String,
    val parameters: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false,
) {
    enum class Kind { SEARCH, SHOW_WAITING, SHOW_MEETINGS, PLAN_DAY, MOVE_TASKS, CREATE_TASK, UPDATE_TASK, DELEGATE_TASK, SCHEDULE_TASK, UNKNOWN }
}

object VoiceSecretary {
    fun parse(transcript: String): SecretaryIntent {
        val query = transcript.trim()
        val lower = query.lowercase()
        if (query.isBlank()) return SecretaryIntent(SecretaryIntent.Kind.UNKNOWN, query)
        return when {
            lower.contains("what am i waiting") || lower.contains("waiting on") ||
                query.contains("எதற்காக காத்திருக்க") || lower.contains("enna waiting") ->
                SecretaryIntent(SecretaryIntent.Kind.SHOW_WAITING, query)
            lower.contains("what do i have tomorrow") || lower.contains("show meetings") || lower.contains("show my meetings") ||
                query.contains("நாளை என்ன") || lower.contains("meeting tomorrow") ->
                SecretaryIntent(SecretaryIntent.Kind.SHOW_MEETINGS, query)
            lower.startsWith("plan my day") || lower.contains("schedule my day") ||
                query.contains("என் நாளை திட்டமிட") ->
                SecretaryIntent(SecretaryIntent.Kind.PLAN_DAY, query, requiresConfirmation = false)
            lower.startsWith("move ") || lower.contains("reschedule") ||
                query.contains("மாற்று") ->
                SecretaryIntent(SecretaryIntent.Kind.MOVE_TASKS, query, parameters(query), requiresConfirmation = true)
            lower.contains("delegate") || lower.contains("assign") || query.contains("ஒப்படை") ->
                SecretaryIntent(SecretaryIntent.Kind.DELEGATE_TASK, query, parameters(query), requiresConfirmation = true)
            lower.contains("schedule") || lower.contains("put it on my calendar") ->
                SecretaryIntent(SecretaryIntent.Kind.SCHEDULE_TASK, query, parameters(query), requiresConfirmation = true)
            lower.startsWith("create task") || lower.contains("remind me to") ||
                query.contains("பணி உருவாக்க") ->
                SecretaryIntent(SecretaryIntent.Kind.CREATE_TASK, query, parameters(query), requiresConfirmation = true)
            lower.startsWith("update task") || lower.contains("change the meeting") ||
                query.contains("பணியை மாற்று") ->
                SecretaryIntent(SecretaryIntent.Kind.UPDATE_TASK, query, parameters(query), requiresConfirmation = true)
            else -> SecretaryIntent(SecretaryIntent.Kind.SEARCH, query)
        }
    }

    private fun parameters(text: String): Map<String, String> = buildMap {
        time(text)?.let { put("time", "%02d:%02d".format(it.first, it.second)) }
        val target = text.replace(Regex("(?i)\\b(move|update|change|reschedule|create task|remind me to)\\b"), "").trim()
        if (target.isNotBlank()) put("target", target)
    }

    fun time(text: String): Pair<Int, Int>? {
        val match = Regex("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b").find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 1..12 || minute !in 0..59) return null
        if (match.groupValues[3].equals("pm", true) && hour < 12) hour += 12
        if (match.groupValues[3].equals("am", true) && hour == 12) hour = 0
        return hour to minute
    }
}
