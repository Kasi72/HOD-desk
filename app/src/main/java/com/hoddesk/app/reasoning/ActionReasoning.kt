package com.hoddesk.app.reasoning

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ActionReasoningOutput(
    val action: String,
    val category: String,
    val date: String?,
    val time: String?,
    val location: String?,
    val people: List<String>,
    val owner: String?,
    val sourceQuotes: List<String>,
    val sourceIds: List<Long>,
    val confidence: Float,
    val ambiguities: List<String>,
    val conflicts: List<String>
) {
    /** Backward-compatible name used by existing review and audit surfaces. */
    val citations: List<String> get() = sourceQuotes
}

/** Builds a bounded, citation-first prompt for an approved on-device Gemma instruction model. */
object ActionReasoningPrompt {
    fun build(evidence: String): String = """
        Extract official work actions from the evidence below.
        Return JSON only with keys: action, category, date, time, location, people, owner, source_quotes, source_ids, confidence, ambiguities, conflicts.
        Do not infer missing dates, times, people or locations. Use null when absent.
        Extract only actions that are explicitly requested, directed, scheduled or required.
        If the evidence contains no actionable work, return an empty action and confidence 0.
        Keep action concise and preserve the source wording. Never invent a citation.
        source_quotes must quote exact evidence text and source_ids must contain the numeric IDs printed beside those quotes.
        confidence must be between 0 and 1. conflicts must list any newer-source disagreement explicitly shown in the evidence.
        Evidence:
        ${evidence.take(12000)}
    """.trimIndent()
}

/** Rejects malformed, uncited or overconfident model output before it reaches Room or Calendar. */
object ActionReasoningValidator {
    fun parse(raw: String, evidence: String, expectedSourceIds: Set<Long> = sourceIdsIn(evidence)): ActionReasoningOutput {
        val payload = raw.trim()
        require(payload.startsWith("{") && payload.endsWith("}")) { "Reasoning output must be a JSON object" }
        val required = listOf("action", "category", "date", "time", "location", "people", "owner", "source_quotes", "source_ids", "confidence", "ambiguities", "conflicts")
        require(required.all { hasKey(payload, it) }) { "Reasoning output is missing a required field" }
        val action = jsonString(payload, "action").orEmpty().trim()
        val confidence = jsonNumber(payload, "confidence")?.toFloat() ?: -1f
        require(confidence.isFinite() && confidence in 0f..1f) { "Reasoning confidence is outside 0..1" }
        val citations = jsonArray(payload, "source_quotes")
        val sourceIds = jsonNumberArray(payload, "source_ids")
        require(sourceIds.distinct().size == sourceIds.size) { "source_ids contains duplicates" }
        require(sourceIds.all { it in expectedSourceIds }) { "source_ids contains an unknown evidence source" }
        // A zero-confidence empty result is the only valid no-action response.
        if (action.isBlank()) {
            require(confidence == 0f && citations.isEmpty()) { "Empty action must have zero confidence and no citations" }
            require(sourceIds.isEmpty()) { "Empty action cannot cite sources" }
            return ActionReasoningOutput("", "official_work", null, null, null, emptyList(), null, emptyList(), emptyList(), 0f, emptyList(), emptyList())
        }
        require(citations.isNotEmpty() && citations.size <= 5) { "Reasoning output must contain 1 to 5 citations" }
        citations.forEach { citation -> require(citation.isNotBlank() && containsCitation(evidence, citation)) { "Citation is not present in source evidence" } }
        val citedEvidence = citations.joinToString(" ")
        require(sourceIds.isNotEmpty()) { "Action must identify at least one source_id" }
        require(FieldGrounding.supportsAction(citedEvidence, action)) { "Action is not grounded in its cited evidence" }
        val grounded = FieldGrounding.validate(
            evidence = citedEvidence,
            date = jsonString(payload, "date"),
            time = jsonString(payload, "time"),
            location = jsonString(payload, "location"),
            people = jsonArray(payload, "people"),
            ambiguities = jsonArray(payload, "ambiguities")
        )
        val conflicts = (jsonArray(payload, "conflicts") + grounded.conflicts + conflictHints(evidence)).distinct()
        return ActionReasoningOutput(
            action = action,
            category = jsonString(payload, "category")?.ifBlank { "official_work" } ?: "official_work",
            date = grounded.date,
            time = grounded.time,
            location = grounded.location,
            people = grounded.people,
            owner = jsonString(payload, "owner")?.takeIf { FieldGrounding.supportsField(citedEvidence, it) },
            sourceQuotes = citations,
            sourceIds = sourceIds,
            confidence = confidence,
            ambiguities = grounded.ambiguities,
            conflicts = conflicts
        )
    }

    private fun containsCitation(evidence: String, citation: String): Boolean {
        fun normalize(value: String) = value.lowercase()
            .replace(Regex("\\u00a0|[\\t\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val source = normalize(evidence)
        val quote = normalize(citation)
        require(quote.isNotBlank()) { "Citation cannot be blank" }
        return source.contains(quote)
    }

    private fun hasKey(json: String, key: String): Boolean = Regex("\\\"${Regex.escape(key)}\\\"\\s*:").containsMatchIn(json)
    private fun jsonString(json: String, key: String): String? = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"])*)\\\"|null)").find(json)?.groupValues?.getOrNull(1)?.replace("\\\\\\\"", "\\\"")?.trim()?.ifBlank { null }
    private fun jsonNumber(json: String, key: String): Double? = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    private fun jsonArray(json: String, key: String): List<String> = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\[([^]]*)]").find(json)?.groupValues?.getOrNull(1)?.let { body -> Regex("\\\"((?:\\\\.|[^\\\"])*)\\\"").findAll(body).map { it.groupValues[1].replace("\\\\\\\"", "\\\"").trim() }.filter { it.isNotBlank() }.toList() } ?: emptyList()
    private fun jsonNumberArray(json: String, key: String): List<Long> = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\[([^]]*)]").find(json)?.groupValues?.getOrNull(1)?.let { body -> Regex("-?\\d+").findAll(body).map { it.value.toLong() }.toList() } ?: emptyList()
    private fun sourceIdsIn(evidence: String): Set<Long> = Regex("\\[SOURCE_ID:(\\d+)\\]").findAll(evidence).map { it.groupValues[1].toLong() }.toSet()
    private fun conflictHints(evidence: String): List<String> = evidence.lineSequence().filter { it.startsWith("CONFLICT_HINT:") }.map { it.removePrefix("CONFLICT_HINT:").trim() }.filter { it.isNotBlank() }.toList()
}

class OnDeviceActionReasoner(private val modelFilePath: String?) {
    companion object {
        private const val TAG = "HOD_REASONING"
        private const val MIN_MODEL_BYTES = 100_000_000L
    }

    fun isAvailable(): Boolean = modelFilePath?.let { java.io.File(it).length() > MIN_MODEL_BYTES } == true

    suspend fun reason(evidence: String): ActionReasoningOutput {
        check(isAvailable()) { "An approved instruction-tuned LiteRT-LM model is not installed" }
        require(evidence.isNotBlank()) { "Cannot reason over empty evidence" }
        return withContext(Dispatchers.Default) {
            val modelPath = requireNotNull(modelFilePath)
            val engine = Engine(EngineConfig(modelPath, Backend.CPU()))
            try {
                engine.initialize()
                engine.createSession(SessionConfig()).use { session ->
                    val started = System.nanoTime()
                    val raw = session.generateContent(listOf(InputData.Text(ActionReasoningPrompt.build(evidence))))
                    val parsed = ActionReasoningValidator.parse(extractJson(raw), evidence)
                    Log.i(TAG, "reasoning_ms=${(System.nanoTime() - started) / 1_000_000},confidence=${parsed.confidence}")
                    parsed
                }
            } catch (error: Throwable) {
                Log.e(TAG, "reasoning_failed", error)
                throw IllegalStateException("On-device action reasoning failed", error)
            } finally {
                engine.close()
            }
        }
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "Reasoning model did not return a JSON object" }
        return trimmed.substring(start, end + 1)
    }
}
