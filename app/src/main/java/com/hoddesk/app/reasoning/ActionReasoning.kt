package com.hoddesk.app.reasoning

import org.json.JSONArray
import org.json.JSONObject
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
    val confidence: Float,
    val ambiguities: List<String>,
    val citations: List<String>
)

/** Builds a bounded, citation-first prompt for an approved on-device Gemma instruction model. */
object ActionReasoningPrompt {
    fun build(evidence: String): String = """
        Extract official work actions from the evidence below.
        Return JSON only with keys: action, category, date, time, location, people, confidence, ambiguities, citations.
        Do not infer missing dates, times, people or locations. Use null when absent.
        Extract only actions that are explicitly requested, directed, scheduled or required.
        If the evidence contains no actionable work, return an empty action and confidence 0.
        Keep action concise and preserve the source wording. Never invent a citation.
        citations must quote exact evidence text. confidence must be between 0 and 1.
        Evidence:
        ${evidence.take(12000)}
    """.trimIndent()
}

/** Rejects malformed, uncited or overconfident model output before it reaches Room or Calendar. */
object ActionReasoningValidator {
    fun parse(raw: String, evidence: String): ActionReasoningOutput {
        val payload = raw.trim()
        require(payload.startsWith("{") && payload.endsWith("}")) { "Reasoning output must be a JSON object" }
        val json = JSONObject(payload)
        val action = json.optString("action").trim()
        val confidence = json.optDouble("confidence", -1.0).toFloat()
        require(confidence.isFinite() && confidence in 0f..1f) { "Reasoning confidence is outside 0..1" }
        val citations = json.optJSONArray("citations").toStrings()
        // A zero-confidence empty result is the only valid no-action response.
        if (action.isBlank()) {
            require(confidence == 0f && citations.isEmpty()) { "Empty action must have zero confidence and no citations" }
            return ActionReasoningOutput("", "official_work", null, null, null, emptyList(), 0f, emptyList(), emptyList())
        }
        require(citations.isNotEmpty() && citations.size <= 5) { "Reasoning output must contain 1 to 5 citations" }
        citations.forEach { citation -> require(citation.isNotBlank() && containsCitation(evidence, citation)) { "Citation is not present in source evidence" } }
        val grounded = FieldGrounding.validate(
            evidence = evidence,
            date = json.optNullable("date"),
            time = json.optNullable("time"),
            location = json.optNullable("location"),
            people = json.optJSONArray("people").toStrings(),
            ambiguities = json.optJSONArray("ambiguities").toStrings()
        )
        return ActionReasoningOutput(
            action = action,
            category = json.optString("category", "official_work"),
            date = grounded.date,
            time = grounded.time,
            location = grounded.location,
            people = grounded.people,
            confidence = confidence,
            ambiguities = grounded.ambiguities,
            citations = citations
        )
    }

    private fun JSONObject.optNullable(key: String): String? = optString(key, "").trim().ifBlank { null }
    private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().ifBlank { null } }

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
