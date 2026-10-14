package com.hoddesk.app.evaluation

import android.content.Context
import org.json.JSONObject

data class EvaluationExample(
    val id: String,
    val inputType: String,
    val source: String,
    val expectedAction: String?,
    val expectedDate: String?,
    val expectedTime: String?,
    val expectedLocation: String?,
    val accepted: Boolean,
    val sourceQuote: String? = null,
)

data class EvaluationRun(
    val baseline: EvaluationReport,
    val current: EvaluationReport,
    val falseVerifiedRate: Double,
    val generatedAt: Long = System.currentTimeMillis(),
    val scope: String = "unverified",
)

data class EvaluationGate(val passed: Boolean, val reasons: List<String>)

object EvaluationStore {
    private const val RUN_FILE = "evaluation-latest.json"
    private const val MAX_FALSE_VERIFIED = 0.02
    private const val MIN_RECALL = 0.90
    private const val MIN_FIELD_ACCURACY = 0.95
    private const val MIN_CITATION_COVERAGE = 0.98
    fun anonymize(source: String): String = source
        .replace(Regex("(?i)\\b[\\w.-]+@[\\w.-]+\\.[a-z]{2,}\\b"), "[EMAIL]")
        .replace(Regex("\\b\\+?\\d[\\d -]{7,}\\b"), "[PHONE]")

    fun report(examples: List<EvaluationExample>, predictions: List<EvaluationExample>): EvaluationReport {
        val byId = predictions.associateBy { it.id }
        require(examples.map { it.id }.distinct().size == examples.size && predictions.map { it.id }.distinct().size == predictions.size) { "Evaluation case IDs must be unique" }
        val comparable = examples.map { expected -> expected to (byId[expected.id] ?: expected.copy(expectedAction = null, expectedDate = null, expectedTime = null, expectedLocation = null, accepted = false, sourceQuote = null)) }
        fun same(a: String?, b: String?): Boolean = when {
            a.isNullOrBlank() && b.isNullOrBlank() -> true
            a.isNullOrBlank() || b.isNullOrBlank() -> false
            else -> a.trim().replace(Regex("\\s+"), " ").equals(b.trim().replace(Regex("\\s+"), " "), ignoreCase = true)
        }
        val positiveCases = comparable.filter { it.first.accepted }
        val recalled = positiveCases.count { (expected, actual) -> actual.accepted && same(expected.expectedAction, actual.expectedAction) }
        val falseVerified = comparable.count { (expected, actual) -> actual.accepted &&
            (!expected.accepted || !same(expected.expectedDate, actual.expectedDate) || !same(expected.expectedTime, actual.expectedTime) || !same(expected.expectedLocation, actual.expectedLocation)) }
        val fields = comparable.flatMap { (expected, actual) ->
            listOf(expected.expectedDate to actual.expectedDate, expected.expectedTime to actual.expectedTime, expected.expectedLocation to actual.expectedLocation)
                .filter { (e, a) -> !e.isNullOrBlank() || !a.isNullOrBlank() }
        }
        val correctFields = fields.count { (expected, actual) -> same(expected, actual) }
        val accepted = comparable.filter { it.second.accepted }
        fun normalized(text: String) = text.replace(Regex("\\s+"), " ").trim()
        val cited = accepted.count { (expected, actual) -> !actual.sourceQuote.isNullOrBlank() && normalized(expected.source).contains(normalized(actual.sourceQuote)) }
        return EvaluationReport(
            caseCount = comparable.size,
            recallAt10 = if (comparable.isEmpty()) 0.0 else if (positiveCases.isEmpty()) 1.0 else recalled.toDouble() / positiveCases.size,
            cer = null, // CER requires a reference transcription, not false-positive task counts.
            wer = null,
            fieldAccuracy = if (fields.isEmpty()) 1.0 else correctFields.toDouble() / fields.size,
            citationCoverage = if (comparable.isEmpty()) 0.0 else if (accepted.isEmpty()) 1.0 else cited.toDouble() / accepted.size,
            falseVerifiedRate = if (comparable.isEmpty()) 1.0 else (falseVerified + predictions.count { it.id !in examples.map(EvaluationExample::id) && it.accepted }).toDouble().div(comparable.size).coerceAtMost(1.0),
        )
    }

    /** Release gate: a candidate must preserve recall and keep unsafe verified fields below 2%. */
    fun gate(baseline: EvaluationReport, current: EvaluationReport, currentFalseVerifiedRate: Double): EvaluationGate {
        val reasons = buildList {
            if (current.caseCount <= 0 || current.caseCount != baseline.caseCount) add("evaluation cases are missing")
            if (listOf(current.recallAt10, current.fieldAccuracy, current.citationCoverage, currentFalseVerifiedRate).any { !it.isFinite() || it !in 0.0..1.0 }) add("invalid evaluation metrics")
            if (current.recallAt10 < MIN_RECALL) add("action recall below ${(MIN_RECALL * 100).toInt()}%")
            if (maxOf(currentFalseVerifiedRate, current.falseVerifiedRate) > MAX_FALSE_VERIFIED) add("false verified rate exceeds ${(MAX_FALSE_VERIFIED * 100).toInt()}%")
            if (current.recallAt10 + 0.001 < baseline.recallAt10) add("recall regressed against baseline")
            if (current.fieldAccuracy < MIN_FIELD_ACCURACY) add("date/time/location field accuracy below ${(MIN_FIELD_ACCURACY * 100).toInt()}%")
            if (current.citationCoverage < MIN_CITATION_COVERAGE) add("accepted actions are missing source citations")
        }
        return EvaluationGate(reasons.isEmpty(), reasons)
    }

    fun saveLatest(context: Context, run: EvaluationRun) {
        val root = JSONObject()
        fun report(value: EvaluationReport) = JSONObject().put("cases", value.caseCount).put("recall", value.recallAt10).put("cer", value.cer).put("wer", value.wer).put("fieldAccuracy", value.fieldAccuracy).put("citationCoverage", value.citationCoverage).put("falseVerifiedRate", value.falseVerifiedRate)
        root.put("schema", 2).put("scope", run.scope)
        root.put("baseline", report(run.baseline)).put("current", report(run.current)).put("falseVerified", run.falseVerifiedRate).put("generatedAt", run.generatedAt)
        context.getFileStreamPath(RUN_FILE).writeText(root.toString())
    }

    fun latest(context: Context): EvaluationRun? = runCatching {
        val root = JSONObject(context.openFileInput(RUN_FILE).bufferedReader().use { it.readText() })
        require(root.optInt("schema") == 2) { "Legacy evaluation metrics require a new run" }
        fun report(value: JSONObject) = EvaluationReport(value.getInt("cases"), value.getDouble("recall"), value.optDouble("cer").takeUnless { it.isNaN() }, value.optDouble("wer").takeUnless { it.isNaN() }, value.optDouble("fieldAccuracy", 0.0), value.optDouble("citationCoverage", 0.0), falseVerifiedRate = value.optDouble("falseVerifiedRate", 1.0))
        EvaluationRun(report(root.getJSONObject("baseline")), report(root.getJSONObject("current")), root.getDouble("falseVerified"), root.getLong("generatedAt"), root.getString("scope"))
    }.getOrNull()
}
