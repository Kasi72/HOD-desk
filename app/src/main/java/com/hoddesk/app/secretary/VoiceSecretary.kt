package com.hoddesk.app.secretary

data class SecretaryIntent(
    val kind: Kind,
    val query: String,
    val parameters: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false,
) {
    enum class Kind { SEARCH, SHOW_WAITING, SHOW_MEETINGS, PLAN_DAY, MOVE_TASKS, CREATE_TASK, UPDATE_TASK, UNKNOWN }
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
            lower.contains("what do i have tomorrow") || lower.contains("show meetings") ||
                query.contains("நாளை என்ன") || lower.contains("meeting tomorrow") ->
                SecretaryIntent(SecretaryIntent.Kind.SHOW_MEETINGS, query)
            lower.startsWith("plan my day") || lower.contains("schedule my day") ||
                query.contains("என் நாளை திட்டமிட") ->
                SecretaryIntent(SecretaryIntent.Kind.PLAN_DAY, query, requiresConfirmation = false)
            lower.startsWith("move ") || lower.contains("reschedule") ||
                query.contains("மாற்று") ->
                SecretaryIntent(SecretaryIntent.Kind.MOVE_TASKS, query, requiresConfirmation = true)
            lower.startsWith("create task") || lower.contains("remind me to") ||
                query.contains("பணி உருவாக்க") ->
                SecretaryIntent(SecretaryIntent.Kind.CREATE_TASK, query, requiresConfirmation = true)
            lower.startsWith("update task") || lower.contains("change the meeting") ||
                query.contains("பணியை மாற்று") ->
                SecretaryIntent(SecretaryIntent.Kind.UPDATE_TASK, query, requiresConfirmation = true)
            else -> SecretaryIntent(SecretaryIntent.Kind.SEARCH, query)
        }
    }
}
